package dev.antigravity.fluidengine.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * L'unico punto Android della cattura: un microfono che dà campioni PCM 16 bit mono. Il registratore lo usa da un thread
 * suo; un microfono finto (le prove, una sorgente diversa) implementa questa interfaccia.
 *
 * È la forma comune della cattura di `engine-ai` (`PcmSource`), allungata a una registrazione vera: c'è [failure] come
 * là, e in più l'avviso delle **interruzioni** ([onInterrupted]), che per un memo contano (una chiamata, un'altra app che
 * comincia a suonare: Memo Vocali in quel caso smette di registrare).
 */
interface FluidPcmSource {
  /** Vero se il microfono è partito a [sampleRate] Hz. */
  fun start(sampleRate: Int): Boolean

  /** Bloccante: quanti campioni ha scritto in [frame]; < 0 è un errore, 0 è «niente per ora». */
  fun read(frame: ShortArray): Int

  fun stop()

  /** Perché non si è potuto registrare, quando [start] o [read] falliscono. */
  val failure: FluidMicrophoneFailure get() = FluidMicrophoneFailure.Unknown

  /** Chiamata (da un thread qualunque) quando il telefono si riprende il microfono o il suono: il registratore va in pausa. */
  var onInterrupted: (() -> Unit)?
}

/** Cosa dire all'utente quando il microfono non collabora. Nuovo nella 4.0 (F8.13). */
enum class FluidMicrophoneFailure {
  /** Il telefono non ha un microfono usabile, o il permesso manca. */
  Unavailable,

  /** Lo tiene un'altra app (una chiamata, un altro registratore). */
  Busy,

  /** Si è perso a metà (cuffie staccate, il servizio audio riavviato). */
  Lost,

  Unknown,
}

/**
 * Come si apre il microfono di [FluidMicrophone].
 *
 * Di serie è quello di un **memo**: la sorgente `MIC` (il microfono come lo tara il telefono per registrare, non per il
 * riconoscimento vocale), senza soppressore di rumore e senza controllo del guadagno: il controllo automatico «pompa» la
 * stanza nelle pause e i memo di Memo Vocali non lo hanno (il miglioramento è una scelta all'ascolto). Per una domanda a
 * voce (un assistente) servono quelli di `engine-ai`: [speech].
 *
 * @param requestFocus chiede il fuoco audio mentre registra: la musica di un'altra app si ferma invece di finire nel
 *   memo, e se poi un'altra app lo riprende (una chiamata, un video) il registratore va in pausa.
 */
data class FluidMicrophoneConfig(
  val audioSource: Int = MediaRecorder.AudioSource.MIC,
  val noiseSuppression: Boolean = false,
  val gainControl: Boolean = false,
  val requestFocus: Boolean = true,
) {
  companion object {
    /** Il microfono di un assistente: la sorgente del riconoscimento, col soppressore e il guadagno (come `engine-ai`). */
    val speech: FluidMicrophoneConfig = FluidMicrophoneConfig(
      audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
      noiseSuppression = true,
      gainControl = true,
    )
  }
}

/**
 * Il microfono vero: `AudioRecord` a 16 bit mono, il thread di lettura alla priorità audio (rimessa com'era a fine
 * lettura), e le due interruzioni che Android sa dire:
 *  - il **fuoco audio** perso (un'altra app comincia a suonare, una chiamata): [onInterrupted];
 *  - da Android 10, il **microfono zittito** dal sistema (`AudioRecordingConfiguration.isClientSilenced`, un'altra app con
 *    priorità più alta, come una chiamata): [onInterrupted].
 *
 * Il permesso RECORD_AUDIO lo controlla chi chiama: qui si presume concesso (senza, [start] è falso con
 * [FluidMicrophoneFailure.Unavailable]).
 */
class FluidMicrophone(
  context: Context,
  private val config: FluidMicrophoneConfig = FluidMicrophoneConfig(),
) : FluidPcmSource {

  private val context = context.applicationContext
  private var record: AudioRecord? = null
  private var suppressor: NoiseSuppressor? = null
  private var gain: AutomaticGainControl? = null
  private var focus: AudioFocusRequest? = null
  private var recordingCallback: AudioManager.AudioRecordingCallback? = null
  private var previousPriority: Int? = null
  private val main = Handler(Looper.getMainLooper())

  override var failure: FluidMicrophoneFailure = FluidMicrophoneFailure.Unknown
    private set

  @Volatile override var onInterrupted: (() -> Unit)? = null

  @SuppressLint("MissingPermission")
  override fun start(sampleRate: Int): Boolean {
    failure = FluidMicrophoneFailure.Unknown
    val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuffer <= 0) {
      failure = FluidMicrophoneFailure.Unavailable
      return false
    }
    // Mezzo secondo di riserva: un thread che tarda un attimo non perde campioni.
    val buffer = maxOf(minBuffer, sampleRate)
    val recorder = runCatching {
      AudioRecord(config.audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer)
    }.getOrNull()
    if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
      recorder?.release()
      failure = FluidMicrophoneFailure.Unavailable
      return false
    }
    if (config.requestFocus) requestFocus()
    previousPriority = runCatching { Process.getThreadPriority(Process.myTid()) }.getOrNull()
    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
    if (config.noiseSuppression && NoiseSuppressor.isAvailable()) {
      suppressor = runCatching { NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
    }
    if (config.gainControl && AutomaticGainControl.isAvailable()) {
      gain = runCatching { AutomaticGainControl.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
    }
    runCatching { recorder.startRecording() }
    if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      // Il microfono c'è ma non parte: quasi sempre lo tiene un'altra app.
      recorder.release()
      release()
      failure = FluidMicrophoneFailure.Busy
      return false
    }
    record = recorder
    watchSilencing(recorder)
    return true
  }

  override fun read(frame: ShortArray): Int {
    val n = record?.read(frame, 0, frame.size) ?: return -1
    if (n >= 0) return n
    failure = when (n) {
      AudioRecord.ERROR_DEAD_OBJECT -> FluidMicrophoneFailure.Lost
      AudioRecord.ERROR_INVALID_OPERATION -> FluidMicrophoneFailure.Busy
      else -> FluidMicrophoneFailure.Unknown
    }
    return -1
  }

  override fun stop() {
    runCatching { record?.stop() }
    runCatching { record?.release() }
    record = null
    release()
  }

  private fun release() {
    runCatching { suppressor?.release() }
    runCatching { gain?.release() }
    suppressor = null
    gain = null
    abandonFocus()
    unwatchSilencing()
    val previous = previousPriority
    if (previous != null) runCatching { Process.setThreadPriority(previous) }
    previousPriority = null
  }

  private fun requestFocus() {
    val manager = context.getSystemService(AudioManager::class.java) ?: return
    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(attributes)
      .setOnAudioFocusChangeListener({ change ->
        // Un'altra app suona (o squilla): Memo Vocali smette di registrare; qui si va in pausa e si può riprendere.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onInterrupted?.invoke()
      }, main)
      .build()
    runCatching { manager.requestAudioFocus(request) }
    focus = request
  }

  private fun abandonFocus() {
    val request = focus ?: return
    runCatching { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
    focus = null
  }

  private fun watchSilencing(recorder: AudioRecord) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val manager = context.getSystemService(AudioManager::class.java) ?: return
    val session = recorder.audioSessionId
    val callback = object : AudioManager.AudioRecordingCallback() {
      override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
        val mine = configs.firstOrNull { it.clientAudioSessionId == session } ?: return
        if (mine.isClientSilenced) onInterrupted?.invoke()
      }
    }
    runCatching { manager.registerAudioRecordingCallback(callback, main) }
    recordingCallback = callback
  }

  private fun unwatchSilencing() {
    val callback = recordingCallback ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching { context.getSystemService(AudioManager::class.java)?.unregisterAudioRecordingCallback(callback) }
    }
    recordingCallback = null
  }
}
