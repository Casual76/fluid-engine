<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/audio.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-audio

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-audio/audio/FluidAudioEnvelopes.kt`

- `object FluidAudioEnvelopes` — L'**onda di un file** (F8.13): l'inviluppo che disegnano `FluidAudioWaveform` e `FluidAudioTimeline`, per un audio che non è stato registrato adesso.
  - `fun sidecar(audio: File): File` — Il file dell'onda salvata accanto a [audio]: lo stesso nome con `.fluidwave` in coda.
  - `fun save(audio: File, envelope: FluidAudioEnvelope)` — Salva [envelope] accanto ad [audio].
  - `suspend fun load(audio: File, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope?` — L'onda di [audio]: quella salvata accanto se c'è e non è più vecchia del file, se no decodificata (e salvata, se si può scrivere nella cartella). Null se il file non si legge.
  - `suspend fun decode(context: Context, uri: Uri, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope?` — L'onda di un audio qualunque per [uri] (`content://`, `file://`, `android.resource://`), decodificato.
  - `fun fromWav(input: InputStream, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope` — L'onda di un WAV letto da [input]: PCM intero a 8, 16, 24 o 32 bit, uno o più canali (mescolati). Un WAV d'altro tipo (in virgola mobile, compresso) è un'[IOException]: lo si passa a [decode].
  - `fun slice(envelope: FluidAudioEnvelope, fromMillis: Long, toMillis: Long): FluidAudioEnvelope` — Il tratto [fromMillis]..[toMillis] di [envelope], ripartendo da zero: l'onda di un file tagliato.
- `object FluidAudioTrim` — Il **taglio** di un file registrato fra due tempi (F8.13): quello che il registratore fa ai silenzi in testa e in coda. Il WAV si taglia copiando i byte; l'AAC in M4A copiando i pacchetti con `MediaExtractor` e `MediaMuxer`, senza ricodificare (un pacchetto AAC è 21 ms a 48 kHz: il taglio cade al pacchetto più vicino). Il file si sostituisce solo a taglio riuscito.
  - `fun trim(file: File, format: FluidRecordingFormat, fromMillis: Long, toMillis: Long): Boolean` — Taglia [file] a [fromMillis]..[toMillis]. Vero se il file ora è tagliato; falso se non si è potuto (resta com'era).

## `engine-audio/audio/FluidAudioRecorder.kt`

- `data class FluidRecorderConfig(val sampleRate: Int = 48_000, val format: FluidRecordingFormat = FluidRecordingFormat.Aac, val aacBitRate: Int = FluidRecordingQuality.Standard.bitRate, val trimSilence: Boolean = false, val saveEnvelope: Boolean = true, val frameMillis: Int = FluidAudioEnvelope.DefaultBucketMillis, val microphone: FluidMicrophoneConfig = FluidMicrophoneConfig())` — Come si registra con [FluidAudioRecorder].
  - `val quality: FluidRecordingQuality?` — La preimpostazione di questa configurazione, o null se il formato non è una delle quattro.
  - `fun with(quality: FluidRecordingQuality): FluidRecorderConfig` — La stessa configurazione con la qualità [quality]: il formato, i bit al secondo e la frequenza.
- `data class FluidRecording(val file: File, val durationMillis: Long, val envelope: FluidAudioEnvelope, val format: FluidRecordingFormat, val trimmed: Boolean = false)` — Una registrazione finita: il file, quanto dura, la sua onda.
- `sealed interface FluidRecorderStatus` — Dove è un [FluidAudioRecorder].
  - `data object Idle : FluidRecorderStatus` — Niente in corso.
  - `data class Recording(val file: File) : FluidRecorderStatus` — Registra in [file].
  - `data class Paused(val file: File, val interrupted: Boolean) : FluidRecorderStatus` — In pausa. [interrupted] vero se l'ha decisa il telefono (una chiamata, un'altra app col suono o col microfono).
  - `data class Finished(val recording: FluidRecording) : FluidRecorderStatus` — Finita: la registrazione è pronta.
  - `data class Failed(val reason: FluidMicrophoneFailure, val cause: Throwable? = null) : FluidRecorderStatus` — Il microfono non ha collaborato, o il file non si è potuto scrivere.
- `class FluidAudioRecorder(private val source: FluidPcmSource, val config: FluidRecorderConfig = FluidRecorderConfig(), private val clock: () -> Long = System::nanoTime)` — Il **registratore** di un memo (F8.13, idea `registratore-vocale`): registra in un file solo, con **pausa e ripresa** (la ripresa scrive in coda, senza buchi), l'**onda viva** ([envelope], che cresce mentre registra), il **livello che non satura** ([FluidAudioLevel]) e l'avviso quando il suono taglia ([clipping]), le **interruzioni** del telefono (va in pausa da solo, [FluidRecorderStatus.Paused] con `interrupted`),…
  - `val status: StateFlow<FluidRecorderStatus>` — Dove è il registratore.
  - `val clipping: StateFlow<Boolean>` — Vero quando nell'ultimo secondo un campione ha toccato il fondo scala: il suono è troppo forte o troppo vicino.
  - `val envelope: FluidGrowingAudioEnvelope` — L'onda della registrazione in corso: un valore ogni [FluidRecorderConfig.frameMillis], già nella scala che non satura.
  - `val recordingConfig: FluidRecorderConfig get()` — La configurazione della registrazione in corso (o dell'ultima): [config] con la qualità scelta a [start].
  - `fun elapsedMillis(): Long` — Il tempo registrato, in millisecondi: un orologio che scorre liscio mentre registra (non a scatti di 20 ms come i campioni) e si ferma in pausa. A fine registrazione è la durata del file.
  - `fun start(target: File, quality: FluidRecordingQuality? = null): Boolean` — Comincia una registrazione in [target] (la cartella si crea). Falso se c'è già una registrazione in corso, se il file non si apre o se il microfono non parte ([status] dice perché).
  - `fun pause()` — Mette in pausa: il microfono si spegne (e con lui l'indicatore della privacy), il file resta aperto.
  - `fun resume(): Boolean` — Riprende dopo una pausa, scrivendo in coda allo stesso file. Falso se il microfono non riparte.
  - `suspend fun stop(): FluidRecording?` — Finisce: chiude il file, toglie i silenzi se richiesto, salva l'onda accanto. Torna la registrazione, o null se non ce n'era una o il file non si è chiuso ([status] lo dice). Sospende sul thread di IO.
  - `fun discard()` — Butta la registrazione in corso: il file si cancella, il registratore torna a riposo.
  - `fun reset()` — Dopo una registrazione finita o fallita, torna a riposo (l'onda resta finché non se ne comincia un'altra). Dopo una cattura fallita da sola, il file scritto fin lì si butta: per tenerlo, [stop] prima.

## `engine-audio/audio/FluidAudioWriters.kt`

- `enum class FluidRecordingFormat(val extension: String, val mimeType: String)` — In che file finisce una registrazione. Nuovo nella 4.0 (F8.13).
  - `companion object`
    - `fun of(quality: FluidRecordingQuality): FluidRecordingFormat` — Il formato di una preimpostazione: WAV per quella senza perdita, AAC per le altre.

## `engine-audio/audio/FluidMicrophone.kt`

- `interface FluidPcmSource` — L'unico punto Android della cattura: un microfono che dà campioni PCM 16 bit mono. Il registratore lo usa da un thread suo; un microfono finto (le prove, una sorgente diversa) implementa questa interfaccia.
  - `fun start(sampleRate: Int): Boolean` — Vero se il microfono è partito a [sampleRate] Hz.
  - `fun read(frame: ShortArray): Int` — Bloccante: quanti campioni ha scritto in [frame]; < 0 è un errore, 0 è «niente per ora».
  - `fun stop()`
  - `val failure: FluidMicrophoneFailure get()` — Perché non si è potuto registrare, quando [start] o [read] falliscono.
  - `var onInterrupted: (() -> Unit)?` — Chiamata (da un thread qualunque) quando il telefono si riprende il microfono o il suono: il registratore va in pausa.
- `enum class FluidMicrophoneFailure` — Cosa dire all'utente quando il microfono non collabora. Nuovo nella 4.0 (F8.13).
- `data class FluidMicrophoneConfig(val audioSource: Int = MediaRecorder.AudioSource.MIC, val noiseSuppression: Boolean = false, val gainControl: Boolean = false, val requestFocus: Boolean = true)` — Come si apre il microfono di [FluidMicrophone].
  - `companion object`
    - `val speech: FluidMicrophoneConfig` — Il microfono di un assistente: la sorgente del riconoscimento, col soppressore e il guadagno (come `engine-ai`).
- `class FluidMicrophone(context: Context, private val config: FluidMicrophoneConfig = FluidMicrophoneConfig()) : FluidPcmSource` — Il microfono vero: `AudioRecord` a 16 bit mono, il thread di lettura alla priorità audio (rimessa com'era a fine lettura), e le due interruzioni che Android sa dire: - il **fuoco audio** perso (un'altra app comincia a suonare, una chiamata): [onInterrupted];
