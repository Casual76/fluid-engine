package dev.antigravity.fluidengine.audio

import android.content.Context
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.foundation.audio.FluidAudioLevel
import dev.antigravity.fluidengine.foundation.audio.FluidAudioSilence
import dev.antigravity.fluidengine.foundation.audio.FluidGrowingAudioEnvelope
import dev.antigravity.fluidengine.foundation.audio.FluidRecordingQuality
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Come si registra con [FluidAudioRecorder].
 *
 * @param sampleRate 48 kHz di serie, quella dei microfoni dei telefoni di oggi: niente ricampionamento.
 * @param format AAC in M4A di serie (la «Compressa» di Memo Vocali), o WAV.
 * @param aacBitRate 96 kbit/s di serie, mono ([FluidRecordingQuality.Standard]): la via di mezzo scelta da Alessio il 25
 *   novembre 2026, circa 44 MB all'ora. Le quattro preimpostazioni (voce per l'IA, standard, alta, senza perdita) si
 *   mettono con [with]; la qualità di una registrazione sola, con `FluidAudioRecorder.start(file, quality)`.
 * @param trimSilence a [FluidAudioRecorder.stop], toglie il silenzio in testa e in coda ([FluidAudioSilence.trimBounds]).
 *   Spento di serie: un memo che comincia con una pausa la tiene, come in Memo Vocali.
 * @param saveEnvelope salva l'onda accanto al file ([FluidAudioEnvelopes.sidecar]): riaprendo la registrazione l'onda
 *   c'è subito, senza decodificare.
 * @param frameMillis ogni quanto il thread della cattura legge il microfono: 20 ms, un valore dell'onda per lettura.
 */
data class FluidRecorderConfig(
  val sampleRate: Int = 48_000,
  val format: FluidRecordingFormat = FluidRecordingFormat.Aac,
  val aacBitRate: Int = FluidRecordingQuality.Standard.bitRate,
  val trimSilence: Boolean = false,
  val saveEnvelope: Boolean = true,
  val frameMillis: Int = FluidAudioEnvelope.DefaultBucketMillis,
  val microphone: FluidMicrophoneConfig = FluidMicrophoneConfig(),
) {
  /** La preimpostazione di questa configurazione, o null se il formato non è una delle quattro. */
  val quality: FluidRecordingQuality?
    get() = FluidRecordingQuality.of(format == FluidRecordingFormat.Wav, aacBitRate, sampleRate)

  /** La stessa configurazione con la qualità [quality]: il formato, i bit al secondo e la frequenza. */
  fun with(quality: FluidRecordingQuality): FluidRecorderConfig = copy(
    format = FluidRecordingFormat.of(quality),
    aacBitRate = if (quality.lossless) aacBitRate else quality.bitRate,
    sampleRate = quality.sampleRate,
  )
}

/** Una registrazione finita: il file, quanto dura, la sua onda. */
data class FluidRecording(
  val file: File,
  val durationMillis: Long,
  val envelope: FluidAudioEnvelope,
  val format: FluidRecordingFormat,
  /** Vero se a [FluidAudioRecorder.stop] si sono tolti silenzi in testa o in coda. */
  val trimmed: Boolean = false,
)

/** Dove è un [FluidAudioRecorder]. */
sealed interface FluidRecorderStatus {
  /** Niente in corso. */
  data object Idle : FluidRecorderStatus

  /** Registra in [file]. */
  data class Recording(val file: File) : FluidRecorderStatus

  /** In pausa. [interrupted] vero se l'ha decisa il telefono (una chiamata, un'altra app col suono o col microfono). */
  data class Paused(val file: File, val interrupted: Boolean) : FluidRecorderStatus

  /** Finita: la registrazione è pronta. */
  data class Finished(val recording: FluidRecording) : FluidRecorderStatus

  /** Il microfono non ha collaborato, o il file non si è potuto scrivere. */
  data class Failed(val reason: FluidMicrophoneFailure, val cause: Throwable? = null) : FluidRecorderStatus
}

/**
 * Il **registratore** di un memo (F8.13, idea `registratore-vocale`): registra in un file solo, con **pausa e ripresa**
 * (la ripresa scrive in coda, senza buchi), l'**onda viva** ([envelope], che cresce mentre registra), il **livello che non
 * satura** ([FluidAudioLevel]) e l'avviso quando il suono taglia ([clipping]), le **interruzioni** del telefono (va in
 * pausa da solo, [FluidRecorderStatus.Paused] con `interrupted`), e il **taglio dei silenzi** in testa e in coda a fine
 * registrazione, se l'app lo chiede ([FluidRecorderConfig.trimSilence]).
 *
 * È la cattura di `engine-ai` resa comune: lo stesso `AudioRecord` in frame da 20 ms, ma per minuti e ore invece che per
 * una domanda, e senza la decisione di fermarsi da sola.
 *
 * **Thread.** I comandi ([start], [pause], [resume], [stop], [discard]) si chiamano da un thread qualunque (di solito il
 * principale) e tornano subito, tranne [stop], che sospende finché il file non è chiuso. La lettura del microfono ha un
 * thread suo, alla priorità audio, che scrive il file e l'onda. [status] e [clipping] sono `StateFlow`; il tempo è un
 * orologio ([elapsedMillis]) che l'interfaccia legge a ogni fotogramma, non uno stato.
 *
 * **Cosa resta all'app**: il permesso del microfono (chiesto al tasto), il servizio in primo piano di tipo `microphone`
 * per registrare a schermo spento o fuori dall'app (Android zittisce il microfono di un'app in sottofondo senza), dove
 * salvare, e il nome del memo.
 */
class FluidAudioRecorder(
  private val source: FluidPcmSource,
  val config: FluidRecorderConfig = FluidRecorderConfig(),
  private val clock: () -> Long = System::nanoTime,
) {

  /** Il registratore col microfono vero del telefono, aperto con [FluidRecorderConfig.microphone]. */
  constructor(context: Context, config: FluidRecorderConfig = FluidRecorderConfig()) :
    this(FluidMicrophone(context, config.microphone), config)

  private val _status = MutableStateFlow<FluidRecorderStatus>(FluidRecorderStatus.Idle)

  /** Dove è il registratore. */
  val status: StateFlow<FluidRecorderStatus> = _status.asStateFlow()

  private val _clipping = MutableStateFlow(false)

  /** Vero quando nell'ultimo secondo un campione ha toccato il fondo scala: il suono è troppo forte o troppo vicino. */
  val clipping: StateFlow<Boolean> = _clipping.asStateFlow()

  /** L'onda della registrazione in corso: un valore ogni [FluidRecorderConfig.frameMillis], già nella scala che non satura. */
  val envelope: FluidGrowingAudioEnvelope = FluidGrowingAudioEnvelope(config.frameMillis)

  private val lock = Any()

  /** La configurazione della registrazione in corso: [config], con la qualità passata a [start] se c'era. */
  @Volatile private var active: FluidRecorderConfig = config

  /** La configurazione della registrazione in corso (o dell'ultima): [config] con la qualità scelta a [start]. */
  val recordingConfig: FluidRecorderConfig get() = active
  private var file: File? = null
  private var writer: FluidAudioWriter? = null
  private var thread: Thread? = null
  @Volatile private var running = false

  /** I campioni scritti nel file finora. */
  @Volatile private var written = 0L

  /** Il tempo registrato prima dell'ultima ripresa, e quando è ripartito l'orologio. */
  @Volatile private var accumulatedNanos = 0L
  @Volatile private var resumedAtNanos = 0L
  @Volatile private var lastClipNanos = Long.MIN_VALUE

  init {
    source.onInterrupted = { interrupt() }
  }

  /**
   * Il tempo registrato, in millisecondi: un orologio che scorre liscio mentre registra (non a scatti di 20 ms come i
   * campioni) e si ferma in pausa. A fine registrazione è la durata del file.
   */
  fun elapsedMillis(): Long {
    val base = accumulatedNanos
    val started = resumedAtNanos
    val live = if (running && started != 0L) clock() - started else 0L
    val clockMs = (base + live) / 1_000_000L
    // L'orologio non scappa avanti ai campioni piu' di un quarto di secondo (un microfono che si e' fermato).
    val samplesMs = written * 1000L / active.sampleRate
    return clockMs.coerceIn(samplesMs - 250L, samplesMs + 250L).coerceAtLeast(0L)
  }

  /**
   * Comincia una registrazione in [target] (la cartella si crea). Falso se c'è già una registrazione in corso, se il
   * file non si apre o se il microfono non parte ([status] dice perché).
   */
  fun start(target: File, quality: FluidRecordingQuality? = null): Boolean = synchronized(lock) {
    if (_status.value is FluidRecorderStatus.Recording || _status.value is FluidRecorderStatus.Paused) return false
    active = if (quality != null) config.with(quality) else config
    target.parentFile?.mkdirs()
    val opened = try {
      openWriter(target)
    } catch (error: Exception) {
      _status.value = FluidRecorderStatus.Failed(FluidMicrophoneFailure.Unknown, error)
      return false
    }
    file = target
    writer = opened
    envelope.clear()
    written = 0L
    accumulatedNanos = 0L
    resumedAtNanos = 0L
    _clipping.value = false
    if (!launch()) {
      opened.abandon()
      writer = null
      file = null
      return false
    }
    _status.value = FluidRecorderStatus.Recording(target)
    true
  }

  /** Mette in pausa: il microfono si spegne (e con lui l'indicatore della privacy), il file resta aperto. */
  fun pause() {
    pauseInternal(interrupted = false)
  }

  /** Riprende dopo una pausa, scrivendo in coda allo stesso file. Falso se il microfono non riparte. */
  fun resume(): Boolean = synchronized(lock) {
    val paused = _status.value as? FluidRecorderStatus.Paused ?: return false
    if (!launch()) return false
    _status.value = FluidRecorderStatus.Recording(paused.file)
    true
  }

  /**
   * Finisce: chiude il file, toglie i silenzi se richiesto, salva l'onda accanto. Torna la registrazione, o null se non
   * ce n'era una o il file non si è chiuso ([status] lo dice). Sospende sul thread di IO.
   */
  suspend fun stop(): FluidRecording? = withContext(Dispatchers.IO) {
    val (target, output) = synchronized(lock) {
      val target = file ?: return@withContext null
      val output = writer ?: return@withContext null
      halt()
      file = null
      writer = null
      target to output
    }
    try {
      output.finish()
    } catch (error: IOException) {
      output.abandon()
      _status.value = FluidRecorderStatus.Failed(FluidMicrophoneFailure.Unknown, error)
      return@withContext null
    }
    var shape: FluidAudioEnvelope = envelope.snapshot()
    var duration = written * 1000L / active.sampleRate
    var trimmed = false
    if (config.trimSilence) {
      val bounds = FluidAudioSilence.trimBounds(shape)
      if (bounds != null && (bounds.first > 0L || bounds.last + 1 < shape.durationMillis)) {
        val cut = runCatching { FluidAudioTrim.trim(target, active.format, bounds.first, bounds.last + 1) }.getOrDefault(false)
        if (cut) {
          shape = FluidAudioEnvelopes.slice(shape, bounds.first, bounds.last + 1)
          duration = bounds.last + 1 - bounds.first
          trimmed = true
        }
      }
    }
    if (config.saveEnvelope) runCatching { FluidAudioEnvelopes.save(target, shape) }
    accumulatedNanos = duration * 1_000_000L
    val recording = FluidRecording(target, duration, shape, active.format, trimmed)
    _status.value = FluidRecorderStatus.Finished(recording)
    recording
  }

  /** Butta la registrazione in corso: il file si cancella, il registratore torna a riposo. */
  fun discard() {
    synchronized(lock) {
      halt()
      writer?.abandon()
      writer = null
      file?.let { FluidAudioEnvelopes.sidecar(it).delete() }
      file = null
      envelope.clear()
      written = 0L
      accumulatedNanos = 0L
      _status.value = FluidRecorderStatus.Idle
    }
  }

  /** Dopo una registrazione finita o fallita, torna a riposo (l'onda resta finché non se ne comincia un'altra). */
  fun reset() {
    synchronized(lock) {
      if (_status.value is FluidRecorderStatus.Finished || _status.value is FluidRecorderStatus.Failed) {
        _status.value = FluidRecorderStatus.Idle
      }
    }
  }

  private fun interrupt() {
    pauseInternal(interrupted = true)
  }

  private fun pauseInternal(interrupted: Boolean) = synchronized(lock) {
    val recording = _status.value as? FluidRecorderStatus.Recording ?: return@synchronized
    halt()
    _status.value = FluidRecorderStatus.Paused(recording.file, interrupted)
  }

  private fun openWriter(target: File): FluidAudioWriter = when (active.format) {
    FluidRecordingFormat.Wav -> FluidWavWriter(target, active.sampleRate)
    FluidRecordingFormat.Aac -> FluidAacWriter(target, active.sampleRate, active.aacBitRate)
  }

  /** Fa partire il thread della cattura; il microfono lo apre lui. Vero se il microfono è partito. */
  private fun launch(): Boolean {
    val ready = java.util.concurrent.CountDownLatch(1)
    var started = false
    running = true
    val capture = Thread({
      started = source.start(active.sampleRate)
      ready.countDown()
      if (!started) {
        running = false
        return@Thread
      }
      try {
        capture()
      } finally {
        source.stop()
      }
    }, "FluidAudioRecorder")
    capture.start()
    ready.await()
    if (!started) {
      capture.join()
      _status.value = FluidRecorderStatus.Failed(source.failure)
      return false
    }
    thread = capture
    resumedAtNanos = clock()
    return true
  }

  /** Ferma il thread della cattura e aspetta che abbia scritto l'ultimo blocco. */
  private fun halt() {
    val capture = thread ?: return
    running = false
    if (capture !== Thread.currentThread()) capture.join()
    thread = null
    resumedAtNanos = 0L
    // L'orologio si allinea ai campioni: in pausa il tempo mostrato e' esattamente quello del file.
    accumulatedNanos = written * 1_000_000_000L / active.sampleRate
  }

  private fun capture() {
    val frameSamples = (active.sampleRate * config.frameMillis / 1000).coerceAtLeast(1)
    val frame = ShortArray(frameSamples)
    // Un valore dell'onda ogni `frameSamples` campioni, anche se il microfono ne da' di piu' o di meno per lettura.
    var bucketFill = 0
    var bucketSum = 0.0
    var silentReads = 0
    while (running) {
      val n = source.read(frame)
      if (n < 0) {
        failAsync(source.failure)
        return
      }
      if (n == 0) {
        if (++silentReads >= EmptyReadsLimit) {
          failAsync(FluidMicrophoneFailure.Lost)
          return
        }
        continue
      }
      silentReads = 0
      try {
        writer?.write(frame, n)
      } catch (error: IOException) {
        failAsync(FluidMicrophoneFailure.Unknown, error)
        return
      }
      written += n
      if (FluidAudioLevel.isClipping(FluidAudioLevel.peak(frame, 0, n))) lastClipNanos = clock()
      var i = 0
      while (i < n) {
        val take = minOf(n - i, frameSamples - bucketFill)
        for (k in i until i + take) {
          val s = frame[k].toDouble()
          bucketSum += s * s
        }
        bucketFill += take
        i += take
        if (bucketFill == frameSamples) {
          val rms = (kotlin.math.sqrt(bucketSum / frameSamples) / 32768.0).toFloat()
          envelope.append(FluidAudioLevel.fromRms(rms))
          bucketFill = 0
          bucketSum = 0.0
        }
      }
      val clipped = clock() - lastClipNanos < ClipHoldNanos && lastClipNanos != Long.MIN_VALUE
      if (clipped != _clipping.value) _clipping.value = clipped
    }
  }

  /**
   * Un errore dal thread della cattura: la pausa (il file scritto finora resta, si puo' riprendere o finire) o lo stato
   * che lo dice. Senza prendere il lucchetto: chi l'ha preso puo' star aspettando proprio questo thread.
   */
  private fun failAsync(reason: FluidMicrophoneFailure, cause: Throwable? = null) {
    running = false
    resumedAtNanos = 0L
    accumulatedNanos = written * 1_000_000_000L / active.sampleRate
    val current = _status.value
    if (current is FluidRecorderStatus.Recording) {
      val next = if (reason == FluidMicrophoneFailure.Busy || reason == FluidMicrophoneFailure.Lost) {
        FluidRecorderStatus.Paused(current.file, interrupted = true)
      } else {
        FluidRecorderStatus.Failed(reason, cause)
      }
      _status.compareAndSet(current, next)
    }
  }

  private companion object {
    /** Letture vuote di fila prima di dire perso il microfono: mezzo secondo a 20 ms. */
    const val EmptyReadsLimit = 25

    /** Quanto resta acceso l'avviso del suono che taglia dopo l'ultimo campione tagliato. */
    const val ClipHoldNanos = 1_000_000_000L
  }
}
