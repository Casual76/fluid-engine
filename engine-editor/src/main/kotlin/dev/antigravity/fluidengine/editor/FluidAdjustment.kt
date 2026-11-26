package dev.antigravity.fluidengine.editor

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Una regolazione dell'editor: «Esposizione», «Contrasto», «Calore»… Le voci e il loro ordine sono quelli di «Regola» in
 * Foto di iOS 26 (la fila di tasti tondi sotto la foto), senza «Riduzione del rumore», che con un filtro serio costa
 * troppo per un'anteprima a 120 Hz (F8.15: domanda per Alessio).
 *
 * **Non e' un enum** apposta: una regolazione nuova in una versione futura non rompe il `when` di un'app (la regola 3.11
 * dell'engine non vuole valori nuovi negli enum pubblici). Le regolazioni di serie sono le costanti del companion e
 * stanno in [All] nell'ordine di Foto.
 *
 * @property id il nome stabile, scritto nella ricetta ([FluidImageEdit.encode]); non si traduce.
 * @property range i valori del righello, come in Foto: −100…100, e 0…100 per nitidezza e definizione (che non hanno un
 *   verso negativo). Il neutro e' sempre 0.
 */
class FluidAdjustment private constructor(
  val id: String,
  val range: ClosedFloatingPointRange<Float>,
  internal val index: Int,
) {
  /** Vero per le regolazioni che vanno solo da 0 in su (nitidezza, definizione). */
  val positiveOnly: Boolean get() = range.start >= 0f

  /** Porta [value] dentro [range], al passo intero del righello di Foto. */
  fun clamp(value: Float): Float = value.coerceIn(range.start, range.endInclusive).roundToInt().toFloat()

  override fun toString(): String = "FluidAdjustment($id)"

  companion object {
    private val Full = -100f..100f
    private val Positive = 0f..100f

    /** La luce, in stop: la moltiplicazione nella luce lineare di `CIExposureAdjust` (±100 = ±1,5 stop, a occhio). */
    val Exposure = FluidAdjustment("exposure", Full, 0)
    /** «Brillantezza»: schiarisce le ombre, recupera le luci e aggiunge un po' di contrasto, insieme. */
    val Brilliance = FluidAdjustment("brilliance", Full, 1)
    /** «Luci»: la parte alta della curva. In negativo recupera i cieli bruciati. */
    val Highlights = FluidAdjustment("highlights", Full, 2)
    /** «Ombre»: la parte bassa della curva. In positivo apre i neri chiusi. */
    val Shadows = FluidAdjustment("shadows", Full, 3)
    /** «Contrasto»: la curva a S attorno al grigio medio. */
    val Contrast = FluidAdjustment("contrast", Full, 4)
    /** «Luminosità»: i toni medi, lasciando fermi il nero e il bianco. */
    val Brightness = FluidAdjustment("brightness", Full, 5)
    /** «Punto di nero»: in positivo i grigi scuri diventano neri, in negativo il nero si alza. */
    val BlackPoint = FluidAdjustment("blackPoint", Full, 6)
    /** «Saturazione»: tutti i colori; −100 e' il bianco e nero. */
    val Saturation = FluidAdjustment("saturation", Full, 7)
    /** «Vividezza»: i colori spenti piu' di quelli gia' vivi (`CIVibrance`). */
    val Vibrance = FluidAdjustment("vibrance", Full, 8)
    /** «Calore»: il bilanciamento del bianco fra blu e arancio, a luminosita' costante. */
    val Warmth = FluidAdjustment("warmth", Full, 9)
    /** «Tinta»: il bilanciamento fra verde (negativo) e magenta (positivo). */
    val Tint = FluidAdjustment("tint", Full, 10)
    /** «Nitidezza»: la maschera di contrasto a raggio piccolo, sulla luminanza. */
    val Sharpness = FluidAdjustment("sharpness", Positive, 11)
    /** «Definizione»: il contrasto locale a raggio grande (2% del lato corto), sulla luminanza. */
    val Definition = FluidAdjustment("definition", Positive, 12)
    /** «Vignettatura»: in positivo scurisce i bordi, in negativo li schiarisce. */
    val Vignette = FluidAdjustment("vignette", Full, 13)

    /** Tutte, nell'ordine della fila di Foto. */
    val All: List<FluidAdjustment> = listOf(
      Exposure, Brilliance, Highlights, Shadows, Contrast, Brightness, BlackPoint,
      Saturation, Vibrance, Warmth, Tint, Sharpness, Definition, Vignette,
    )

    /** La regolazione col nome [id], o null (una ricetta scritta da una versione futura). */
    fun byId(id: String): FluidAdjustment? = All.firstOrNull { it.id == id }
  }
}

/**
 * I valori di tutte le regolazioni, immutabili: una foto appena aperta ha [Neutral]. Si legge con `values[Exposure]`, si
 * cambia con [with] (che da' un oggetto nuovo). Due oggetti coi valori uguali sono uguali.
 */
class FluidAdjustments private constructor(private val values: FloatArray) {

  operator fun get(adjustment: FluidAdjustment): Float = values[adjustment.index]

  /** Gli stessi valori con [adjustment] a [value] (portato dentro l'intervallo e al passo intero). */
  fun with(adjustment: FluidAdjustment, value: Float): FluidAdjustments {
    val clamped = adjustment.clamp(value)
    if (values[adjustment.index] == clamped) return this
    return FluidAdjustments(values.copyOf().also { it[adjustment.index] = clamped })
  }

  /** Vero se nessuna regolazione si discosta dal neutro: la foto esce coi colori di partenza. */
  val isNeutral: Boolean get() = values.all { it == 0f }

  /** Le regolazioni non neutre, nell'ordine di [FluidAdjustment.All]. */
  val changed: List<FluidAdjustment> get() = FluidAdjustment.All.filter { values[it.index] != 0f }

  /** Una copia dei valori nell'ordine di [FluidAdjustment.All], per chi disegna (gli uniform dello shader). */
  internal fun toArray(): FloatArray = values.copyOf()

  override fun equals(other: Any?): Boolean = other is FluidAdjustments && values.contentEquals(other.values)
  override fun hashCode(): Int = values.contentHashCode()
  override fun toString(): String =
    changed.joinToString(prefix = "FluidAdjustments(", postfix = ")") { "${it.id}=${values[it.index].roundToInt()}" }

  companion object {
    /** Tutte a zero. */
    val Neutral = FluidAdjustments(FloatArray(FluidAdjustment.All.size))

    /** Dai valori per nome; i nomi sconosciuti si ignorano. */
    fun of(vararg pairs: Pair<FluidAdjustment, Float>): FluidAdjustments =
      pairs.fold(Neutral) { acc, (a, v) -> acc.with(a, v) }

    internal fun near(a: FluidAdjustments, b: FluidAdjustments, tolerance: Float = 0.5f): Boolean =
      FluidAdjustment.All.all { abs(a[it] - b[it]) <= tolerance }
  }
}
