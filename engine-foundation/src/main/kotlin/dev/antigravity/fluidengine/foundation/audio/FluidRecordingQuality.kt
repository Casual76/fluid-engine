package dev.antigravity.fluidengine.foundation.audio

/**
 * La **qualità di una registrazione** (F8.13b): quattro preimpostazioni chiare, che il registratore di `engine-audio`
 * usa (`FluidRecorderConfig.with(quality)`) e che la riga delle impostazioni di `engine-ui`
 * (`FluidRecordingQualityRow`) mostra all'utente con lo spazio che occupa un'ora.
 *
 * Scelta di Alessio del 25 novembre 2026: di serie la via di mezzo, **AAC a 96 kbit/s**; modificabile dall'app e, se
 * l'app vuole, dall'utente. Per dare l'audio all'IA bastano 64 kbit/s; in Pampa Notes l'utente può volere il WAV senza
 * perdita.
 *
 * Non è un `enum`: una preimpostazione nuova non deve rompere i `when` delle app. Si confronta per [id].
 *
 * @param id il nome stabile, da salvare nelle impostazioni dell'app ([byId] lo rilegge).
 * @param bitRate i bit al secondo dell'AAC; 0 per [Lossless] (PCM, senza compressione).
 * @param sampleRate i campioni al secondo, mono.
 */
class FluidRecordingQuality private constructor(
  val id: String,
  val bitRate: Int,
  val sampleRate: Int,
) {
  /** Vero per la registrazione senza perdita (WAV PCM 16 bit). */
  val lossless: Boolean get() = bitRate == 0

  /**
   * Quanti byte occupa un'ora: l'AAC col suo contenitore MP4 (circa l'1% in più dei soli dati, misurato sui file del
   * codificatore di Android), il WAV coi suoi campioni a 16 bit.
   */
  val bytesPerHour: Long
    get() = if (lossless) sampleRate.toLong() * 2L * 3_600L else (bitRate.toLong() / 8L * 3_600L * 101L) / 100L

  override fun equals(other: Any?): Boolean = other is FluidRecordingQuality && other.id == id

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = "FluidRecordingQuality($id)"

  companion object {
    /**
     * **Voce per l'IA**: AAC a 64 kbit/s, 48 kHz mono. I riconoscitori lavorano sul parlato a 16 kHz, e 64 kbit/s di AAC
     * a 48 kHz tengono tutta la banda della voce con margine (a occhio, 25 novembre 2026; è la qualità che Alessio ha
     * indicato per l'IA). Un'ora: circa 29 MB.
     */
    val Voice: FluidRecordingQuality = FluidRecordingQuality("voce", 64_000, 48_000)

    /** **Standard**, di serie: AAC a 96 kbit/s, 48 kHz mono, la via di mezzo scelta da Alessio. Un'ora: circa 44 MB. */
    val Standard: FluidRecordingQuality = FluidRecordingQuality("standard", 96_000, 48_000)

    /**
     * **Alta**: AAC a 128 kbit/s, 48 kHz mono, cioè quanto un canale di iTunes Plus (AAC a 256 kbit/s stereo, la qualità
     * della musica di Apple): fiducia media come soglia oltre la quale l'AAC mono non si distingue dall'originale. Un'ora:
     * circa 58 MB.
     */
    val High: FluidRecordingQuality = FluidRecordingQuality("alta", 128_000, 48_000)

    /**
     * **Senza perdita**: WAV PCM a 16 bit, 48 kHz mono, come la qualità «Senza perdita» di Memo Vocali (che usa ALAC;
     * Android non ha un codificatore ALAC, e il WAV si legge ovunque). Un'ora: circa 346 MB.
     */
    val Lossless: FluidRecordingQuality = FluidRecordingQuality("senza-perdita", 0, 48_000)

    /** Le quattro, dalla più leggera alla più pesante: l'ordine in cui la riga le propone. */
    val All: List<FluidRecordingQuality> = listOf(Voice, Standard, High, Lossless)

    /** La preimpostazione con [id], o null (un valore salvato da una versione che ne aveva un'altra). */
    fun byId(id: String?): FluidRecordingQuality? = All.firstOrNull { it.id == id }

    /** La preimpostazione con questo formato, o null se non è una delle quattro. */
    fun of(lossless: Boolean, bitRate: Int, sampleRate: Int): FluidRecordingQuality? =
      All.firstOrNull { it.lossless == lossless && (lossless || it.bitRate == bitRate) && it.sampleRate == sampleRate }
  }
}
