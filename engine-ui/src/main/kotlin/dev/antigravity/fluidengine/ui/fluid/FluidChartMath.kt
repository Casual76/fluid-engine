package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// La matematica dei grafici (`FluidCharts.kt`), fuori da ogni composizione: scale e tacche «belle», ripartizione
// in fette, percentuali che sommano 100, angoli e bersagli di tocco, barre, linee con buchi, numeri nella lingua.
//
// **Perche' e' un file a se'.** Un grafico sbaglia nei bordi: tutto zero (una divisione per zero), un valore
// enorme che schiaccia gli altri, un buco nei dati, tre percentuali arrotondate che fanno 99, una fetta dello
// 0,3% che non si vede e non si tocca. Sono tutti conti, e un conto si prova con un test JVM in un millisecondo,
// senza uno schermo. I composable restano sottili: prendono questi risultati e li disegnano; e i calcoli vivono
// qui fuori, quindi non si rifanno a ogni disegno ne' a ogni ricomposizione (i chiamanti li tengono in `remember`).

// --- Fette (ciambella) ---------------------------------------------------------------------------------------

/**
 * Una voce di una ripartizione: un'etichetta, un valore e (se serve) il colore.
 *
 * Il valore e' un **peso non negativo**, non una percentuale: la ciambella calcola da se' la quota di ognuno sulla
 * somma. Se i valori sono gia' percentuali (62, 21, 17) funziona lo stesso, e [FluidDonutChart] puo' farsi
 * dichiarare il totale atteso (`whole = 100f`) per rifiutare dati che non tornano.
 *
 * @param color `Color.Unspecified`: lo sceglie il grafico dalla palette d'identita' ([FluidIdentityColors]),
 *   per posizione. Un colore dato (di un'etichetta, di un account) vale cosi' com'e', **portato del minimo che
 *   serve** a staccarsi a 3:1 dallo sfondo (`legibleFill`): il colore che si vede puo' essere un poco piu'
 *   scuro o chiaro di quello passato.
 */
@Immutable
data class FluidChartEntry(
  val label: String,
  val value: Float,
  val color: Color = Color.Unspecified,
)

/**
 * Una fetta cosi' come la disegna la ciambella, dopo il raggruppamento in «Altro» ([fluidDonutLayout]).
 *
 * @property fraction la quota sulla somma, 0..1: la somma delle quote e' 1 per costruzione.
 * @property sourceIndices le posizioni delle voci di partenza che questa fetta contiene: una sola per una fetta
 *   normale, piu' di una per «Altro». E' il modo di risalire dai dati a cio' che e' stato toccato.
 * @property isOther vera per la fetta «Altro» (nata dal raggruppamento): ha un colore neutro e non un colore
 *   della palette, perche' non e' una categoria ma un avanzo.
 */
@Immutable
data class FluidDonutSlice(
  val label: String,
  val value: Float,
  val fraction: Float,
  val color: Color,
  val sourceIndices: List<Int>,
  val isOther: Boolean,
)

/** Perche' una ripartizione non si disegna. */
enum class FluidDonutProblem {
  /** Un valore e' `NaN` o infinito. */
  NotFinite,

  /** Un valore e' negativo: una fetta non puo' avere meno di zero. */
  Negative,

  /** I valori non sommano al totale dichiarato (`whole`) e la regola e' [FluidChartSumPolicy.Strict]. */
  SumMismatch,
}

/**
 * Cosa fare quando i valori di una ripartizione non sommano al totale che il chiamante ha dichiarato.
 *
 * Una torta e' una promessa: «queste parti fanno il tutto». Se i dati non la mantengono, o si dice, o si
 * dichiara che si sta riportando alla somma. Mai una terza strada silenziosa.
 */
enum class FluidChartSumPolicy {
  /** Non disegna: mostra «dati non validi» col totale dichiarato e la somma vera. Di serie. */
  Strict,

  /**
   * Disegna le quote sulla **somma** dei valori (che e' quindi il 100%), invece che sul totale dichiarato.
   * Lo sceglie chi sa che i dati hanno un resto (arrotondamenti a monte): la scelta e' esplicita, nel codice
   * di chi chiama, e non una conseguenza di un dato sbagliato.
   */
  Normalize,
}

/** Il risultato di [fluidDonutLayout]. */
sealed interface FluidDonutLayout {
  /** Fette da disegnare, con le quote che sommano 1. */
  data class Ready(
    val slices: List<FluidDonutSlice>,
    val total: Float,
    /** Vero se i valori non sommavano al totale dichiarato e si e' scelto [FluidChartSumPolicy.Normalize]. */
    val normalized: Boolean,
  ) : FluidDonutLayout

  /** Nessuna voce, o tutte a zero: «nessun dato nel periodo». Non e' un errore. */
  data object Empty : FluidDonutLayout

  /** Dati che non si possono disegnare senza mentire. */
  data class Invalid(
    val problem: FluidDonutProblem,
    /** La posizione della voce colpevole, o -1 se non e' una voce sola. */
    val index: Int = -1,
    val declared: Float = Float.NaN,
    val actual: Float = Float.NaN,
  ) : FluidDonutLayout
}

/** La tolleranza con cui una somma «torna» al totale dichiarato: mezzo punto percentuale. */
internal const val FluidSumTolerance = 0.005

/**
 * Ripartisce [entries] in fette: valida i dati, calcola le quote e raggruppa le fette minuscole in «Altro».
 *
 * **Regole, in ordine.**
 * 1. Un valore non finito o negativo e' [FluidDonutLayout.Invalid]; la posizione e' nel risultato.
 * 2. Nessuna voce, o somma zero: [FluidDonutLayout.Empty]. Non si divide per zero.
 * 3. Con [whole] dichiarato, la somma deve tornare entro [FluidSumTolerance] (0,5%): altrimenti
 *    [FluidChartSumPolicy.Strict] da' [FluidDonutLayout.Invalid] (`SumMismatch`, con totale e somma), e
 *    [FluidChartSumPolicy.Normalize] disegna sulla somma e lo segnala in `normalized`.
 * 4. Oltre [maxSlices] fette, le piu' piccole confluiscono in «Altro» (restano le `maxSlices - 1` maggiori).
 * 5. Le fette sotto [groupBelow] (quota 0..1) confluiscono in «Altro», **se sono almeno due** (o se «Altro»
 *    esiste gia'): una fetta minuscola sola resta se' stessa, perche' «Altro» per una voce sola nasconde il nome
 *    senza risparmiare niente.
 *
 * L'ordine dei dati e' quello di chi chiama (di solito per valore decrescente); «Altro» e' sempre ultimo.
 * Pura: la stessa cosa che fa la ciambella, provabile senza uno schermo.
 */
fun fluidDonutLayout(
  entries: List<FluidChartEntry>,
  whole: Float? = null,
  sumPolicy: FluidChartSumPolicy = FluidChartSumPolicy.Strict,
  groupBelow: Float = FluidChartDefaults.GroupBelow,
  maxSlices: Int = FluidChartDefaults.MaxSlices,
  otherLabel: String = "Altro",
): FluidDonutLayout {
  entries.forEachIndexed { index, entry ->
    if (!entry.value.isFinite()) return FluidDonutLayout.Invalid(FluidDonutProblem.NotFinite, index)
    if (entry.value < 0f) return FluidDonutLayout.Invalid(FluidDonutProblem.Negative, index)
  }
  if (entries.isEmpty()) return FluidDonutLayout.Empty
  val sum = entries.sumOf { it.value.toDouble() }
  if (sum <= 0.0) return FluidDonutLayout.Empty

  var normalized = false
  if (whole != null) {
    if (!whole.isFinite() || whole <= 0f) {
      return FluidDonutLayout.Invalid(FluidDonutProblem.SumMismatch, declared = whole, actual = sum.toFloat())
    }
    val off = abs(sum - whole) > whole * FluidSumTolerance
    if (off && sumPolicy == FluidChartSumPolicy.Strict) {
      return FluidDonutLayout.Invalid(FluidDonutProblem.SumMismatch, declared = whole, actual = sum.toFloat())
    }
    normalized = off
  }

  var slices = entries.mapIndexed { index, entry ->
    FluidDonutSlice(entry.label, entry.value, (entry.value / sum).toFloat(), entry.color, listOf(index), isOther = false)
  }

  fun mergeInto(list: List<FluidDonutSlice>, members: List<FluidDonutSlice>): List<FluidDonutSlice> {
    val keep = list.filter { slice -> members.none { it === slice } }
    val other = FluidDonutSlice(
      label = otherLabel,
      value = members.sumOf { it.value.toDouble() }.toFloat(),
      fraction = members.sumOf { it.fraction.toDouble() }.toFloat(),
      color = Color.Unspecified,
      sourceIndices = members.flatMap { it.sourceIndices }.sorted(),
      isOther = true,
    )
    return keep + other
  }

  if (maxSlices >= 2 && slices.size > maxSlices) {
    val keepCount = maxSlices - 1
    val kept = slices.sortedByDescending { it.value }.take(keepCount)
    slices = mergeInto(slices, slices.filter { slice -> kept.none { it === slice } })
  }
  if (groupBelow > 0f) {
    val tiny = slices.filter { !it.isOther && it.fraction < groupBelow }
    val hasOther = slices.any { it.isOther }
    if (tiny.size >= 2 || (tiny.isNotEmpty() && hasOther)) {
      val members = tiny + slices.filter { it.isOther }
      slices = mergeInto(slices, members)
    }
  }
  return FluidDonutLayout.Ready(slices, sum.toFloat(), normalized)
}

// --- Percentuali che sommano 100 ----------------------------------------------------------------------------

/**
 * Le [fractions] (che sommano ~1) come percentuali con [decimals] cifre decimali, **che sommano esattamente
 * 100** (metodo del resto piu' grande): tre terzi diventano 34, 33, 33 e non 33, 33, 33.
 *
 * Perche': scrivere accanto a una torta «33% 33% 33%» e' una torta bugiarda. L'arrotondamento naive sbaglia
 * di un punto quando le quote hanno la stessa parte decimale; qui si assegna il punto mancante alle fette con
 * il resto maggiore (a parita', la prima). Una somma di quote zero da' tutti zeri.
 */
internal fun fluidRoundedPercents(fractions: List<Float>, decimals: Int = 0): List<Double> {
  if (fractions.isEmpty()) return emptyList()
  val scale = 10.0.pow(decimals.coerceIn(0, 4)) * 100.0
  val total = fractions.sumOf { it.toDouble() }
  if (total <= 0.0) return fractions.map { 0.0 }
  val target = scale.roundToInt()
  val exact = fractions.map { it / total * scale }
  val floors = exact.map { floor(it).toInt() }.toMutableList()
  var missing = target - floors.sum()
  val order = exact.indices.sortedWith(compareByDescending<Int> { exact[it] - floors[it] }.thenBy { it })
  var cursor = 0
  while (missing > 0 && order.isNotEmpty()) {
    floors[order[cursor % order.size]]++
    missing--
    cursor++
  }
  return floors.map { it / (scale / 100.0) }
}

/**
 * Le percentuali di [fractions] come testo nella lingua di [locale] («62%»), con la somma a 100
 * ([fluidRoundedPercents]). Una quota maggiore di zero che arrotonda a zero si scrive «<1%», non «0%»: «0%» direbbe
 * che non c'e'.
 */
internal fun fluidPercentTexts(fractions: List<Float>, locale: Locale, decimals: Int = 0): List<String> {
  val rounded = fluidRoundedPercents(fractions, decimals)
  val format = NumberFormat.getPercentInstance(locale).apply {
    minimumFractionDigits = decimals
    maximumFractionDigits = decimals
  }
  val smallest = 10.0.pow(-decimals.coerceIn(0, 4))
  return fractions.mapIndexed { i, fraction ->
    if (fraction > 0f && rounded[i] == 0.0) "<" + format.format(smallest / 100.0) else format.format(rounded[i] / 100.0)
  }
}

// --- Numeri nella lingua ----------------------------------------------------------------------------------

private val CompactSuffixes = listOf("", "k", "M", "G", "T")

/**
 * Un numero come testo nella lingua di [locale]: separatori e virgola decimale della lingua, poche cifre
 * (nessuna decimale da 100 in su), e **compatto** da [compactFrom] in poi (`12k`, `3,4M`).
 *
 * Gli assi compattano da 10.000 (non c'e' posto per `1.240.000` sotto una tacca); i valori letti da una
 * persona (legenda, descrizione) da un milione. Oltre mille miliardi passa alla notazione scientifica: un
 * valore enorme non rompe il testo. Non finito → «—».
 */
internal fun fluidFormatNumber(value: Double, locale: Locale, compactFrom: Double = 1_000_000.0): String {
  if (!value.isFinite()) return "—"
  val v = if (value == 0.0) 0.0 else value
  if (abs(v) >= compactFrom) {
    var scaled = v
    var step = 0
    while (abs(scaled) >= 1000.0 && step < CompactSuffixes.lastIndex) {
      scaled /= 1000.0
      step++
    }
    if (abs(scaled) >= 1000.0) {
      return DecimalFormat("0.##E0", DecimalFormatSymbols(locale)).format(v)
    }
    val format = NumberFormat.getNumberInstance(locale).apply {
      maximumFractionDigits = if (abs(scaled) < 10.0) 1 else 0
      minimumFractionDigits = 0
    }
    return format.format(scaled) + CompactSuffixes[step]
  }
  val format = NumberFormat.getNumberInstance(locale).apply {
    maximumFractionDigits = when {
      abs(v) >= 100.0 -> 0
      abs(v) >= 10.0 -> 1
      else -> 2
    }
    minimumFractionDigits = 0
  }
  return format.format(v)
}

// --- Scale e tacche ---------------------------------------------------------------------------------------

/** Una scala dell'asse dei valori: dove comincia, dove finisce e le tacche (con i loro valori). */
internal class FluidNiceScale(val min: Double, val max: Double, val step: Double, val ticks: List<Double>) {
  /** La posizione di [value] sull'asse, 0 (in basso/a sinistra) .. 1, fuori scala tagliata ai bordi. */
  fun fraction(value: Double): Float =
    if (max > min) ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() else 0f

  override fun toString(): String = "FluidNiceScale($min..$max step $step ticks=$ticks)"
}

/**
 * La scala «bella» per dati che vanno da [dataMin] a [dataMax]: estremi e passo su numeri tondi (1, 2, 5 per
 * una potenza di dieci), circa [targetTicks] tacche, **mai meno del dato**.
 *
 * - [includeZero] (di serie): lo zero e' sempre sull'asse. E' l'unica scala onesta per le barre (la loro
 *   lunghezza e' il valore) e la scelta prudente per una linea; chi vuole che una linea occupi tutta l'altezza
 *   la spegne.
 * - Tutto zero, o dati degeneri (`NaN`, infiniti): una scala da 0 a 1, mai una divisione per zero.
 * - Una linea piatta senza zero (tutti 7,0): si apre del 10% da una parte e dall'altra.
 * - Valori enormi (`1e30`): il conto e' in `Double`, il passo resta una potenza di dieci.
 */
internal fun fluidNiceScale(
  dataMin: Double,
  dataMax: Double,
  targetTicks: Int = 5,
  includeZero: Boolean = true,
): FluidNiceScale {
  var lo = if (dataMin.isFinite()) dataMin else 0.0
  var hi = if (dataMax.isFinite()) dataMax else 1.0
  if (lo > hi) lo = hi.also { hi = lo }
  if (includeZero) {
    lo = min(lo, 0.0)
    hi = max(hi, 0.0)
  }
  if (hi - lo <= 0.0) {
    if (hi == 0.0) return fluidScaleOf(0.0, 1.0, 0.25)
    val pad = abs(hi) * 0.1
    lo -= pad
    hi += pad
  }
  val intervals = max(1, targetTicks - 1)
  val rawStep = (hi - lo) / intervals
  val magnitude = 10.0.pow(floor(log10(rawStep)))
  val residual = rawStep / magnitude
  val nice = when {
    residual <= 1.0 -> 1.0
    residual <= 2.0 -> 2.0
    residual <= 5.0 -> 5.0
    else -> 10.0
  }
  val step = nice * magnitude
  val first = floor(lo / step + 1e-9)
  val last = ceil(hi / step - 1e-9)
  return fluidScaleOf(first * step, last * step, step)
}

private fun fluidScaleOf(min: Double, max: Double, step: Double): FluidNiceScale {
  val count = ((max - min) / step).roundToInt().coerceAtLeast(1)
  return FluidNiceScale(min, max, step, List(count + 1) { min + it * step })
}

/**
 * Una scala che finisce **esattamente** su [max] (un denominatore dichiarato: «su 1.240 mail»): le tacche sono
 * tonde, e l'ultima e' [max] stesso (l'ultima tonda troppo vicina cede il posto).
 */
internal fun fluidFixedScale(min: Double, max: Double, targetTicks: Int = 5): FluidNiceScale {
  if (!(max > min) || !max.isFinite() || !min.isFinite()) return fluidScaleOf(0.0, 1.0, 0.25)
  val natural = fluidNiceScale(min, max, targetTicks, includeZero = false)
  val step = natural.step
  val ticks = natural.ticks.filter { it >= min - 1e-9 * step && it <= max + 1e-9 * step }.toMutableList()
  if (ticks.isEmpty() || ticks.first() > min + 1e-9 * step) ticks.add(0, min)
  if (ticks.last() < max - 1e-9 * step) {
    if (max - ticks.last() < step * 0.4 && ticks.size > 1) ticks.removeAt(ticks.lastIndex)
    ticks.add(max)
  }
  return FluidNiceScale(min, max, step, ticks)
}

/** Quante tacche stanno su [lengthPx] di asse, con [labelPx] di altezza di una etichetta: 2..6, mai fitte. */
internal fun fluidTickBudget(lengthPx: Float, labelPx: Float): Int {
  if (labelPx <= 0f) return 5
  return (lengthPx / (labelPx * 2.4f)).toInt().coerceIn(2, 6)
}

// --- Etichette dell'asse che si diradano ---------------------------------------------------------------------

/**
 * Ogni quante etichette se ne scrive una: la prima distanza [stride] da 1 a [count] per cui [fits] e' vero
 * (tutte le etichette scritte stanno nello spazio che hanno, [stride] caselle ciascuna). Se nemmeno scriverne una
 * sola ci sta, [count] (resta la prima).
 *
 * Perche' un predicato: «sta» dipende da come il testo va a capo, da quanto e' grande (200%) e dalla lingua, e
 * lo sa chi misura il testo; la regola di scegliere il minimo che non si sovrappone e' qui, e si prova con un
 * predicato finto.
 */
internal fun fluidLabelStride(count: Int, fits: (stride: Int) -> Boolean): Int {
  if (count <= 1) return 1
  for (stride in 1 until count) if (fits(stride)) return stride
  return count
}

// --- Barre ------------------------------------------------------------------------------------------------

/** Una barra lungo l'asse delle categorie: da [start] a [end] in pixel dentro la sua casella. */
internal class FluidBarSpan(val start: Float, val end: Float) {
  val size: Float get() = end - start
}

/**
 * Dove stanno le barre di una casella larga [slotExtent] pixel: una per serie affiancate (grouped), o una sola
 * larga come un'intera pila (stacked, e allora ogni serie riceve lo stesso intervallo).
 *
 * La barra non supera [maxThickness] (un grafico con due voci non ha due lastre larghe una pagina), lascia
 * [slotPadFraction] di casella per parte e [innerGap] pixel fra le barre di una stessa casella: **la
 * separazione e' fatta di spazio, non solo di colore**. Tutte centrate nella casella.
 */
internal fun fluidBarSpans(
  slotExtent: Float,
  seriesCount: Int,
  stacked: Boolean,
  maxThickness: Float,
  innerGap: Float,
  slotPadFraction: Float = 0.16f,
): List<FluidBarSpan> {
  val series = max(1, seriesCount)
  val usable = slotExtent * (1f - 2f * slotPadFraction)
  if (stacked || series == 1) {
    val thickness = min(usable, maxThickness).coerceAtLeast(0f)
    val start = (slotExtent - thickness) / 2f
    return List(series) { FluidBarSpan(start, start + thickness) }
  }
  val thickness = ((usable - innerGap * (series - 1)) / series).coerceIn(0f, maxThickness)
  val total = thickness * series + innerGap * (series - 1)
  val first = (slotExtent - total) / 2f
  return List(series) { i ->
    val s = first + i * (thickness + innerGap)
    FluidBarSpan(s, s + thickness)
  }
}

/** Gli estremi (in valore) di ogni segmento di una pila: cumulati da zero, `from` e `to`. */
internal fun fluidStackEdges(values: List<Float>): List<Pair<Double, Double>> {
  var acc = 0.0
  return values.map { v ->
    val from = acc
    acc += v.toDouble()
    from to acc
  }
}

/** Il problema di un dato di barre/linee, o `null` se va bene: non finito, o negativo dove non e' ammesso. */
internal enum class FluidSeriesProblem { NotFinite, Negative, ExceedsScale, Ragged }

/**
 * La scala delle barre e il suo controllo: la pila somma per casella, le barre affiancate hanno la loro
 * estensione, e un [maxValue] dichiarato fissa la fine della scala.
 *
 * Restituisce la scala, oppure il problema: un valore non finito, un negativo in una pila (le pile sono
 * solo per serie disgiunte e non negative), un valore che **supera** la scala dichiarata (una barra tagliata
 * mente), o righe di lunghezza diversa dalle serie.
 */
internal fun fluidBarScale(
  values: List<List<Float>>,
  seriesCount: Int,
  stacked: Boolean,
  maxValue: Float?,
  targetTicks: Int,
): Pair<FluidNiceScale?, FluidSeriesProblem?> {
  if (values.any { it.size != seriesCount }) return null to FluidSeriesProblem.Ragged
  for (row in values) for (v in row) {
    if (!v.isFinite()) return null to FluidSeriesProblem.NotFinite
    if (stacked && v < 0f) return null to FluidSeriesProblem.Negative
  }
  val high = if (stacked) values.maxOfOrNull { row -> row.sumOf { it.toDouble() } } else values.maxOfOrNull { row -> row.maxOrNull()?.toDouble() ?: 0.0 }
  val low = if (stacked) 0.0 else values.minOfOrNull { row -> row.minOrNull()?.toDouble() ?: 0.0 }
  val dataMax = high ?: 0.0
  val dataMin = min(low ?: 0.0, 0.0)
  if (maxValue != null) {
    if (!maxValue.isFinite() || maxValue <= 0f) return null to FluidSeriesProblem.ExceedsScale
    if (dataMax > maxValue * (1.0 + FluidSumTolerance)) return null to FluidSeriesProblem.ExceedsScale
    return fluidFixedScale(dataMin, maxValue.toDouble(), targetTicks) to null
  }
  return fluidNiceScale(dataMin, dataMax, targetTicks, includeZero = true) to null
}

// --- Linee ------------------------------------------------------------------------------------------------

/**
 * I tratti continui di una serie con buchi: gruppi di posizioni consecutive con un valore. `[1,2,null,4]` da' due
 * tratti, `[0,1]` e `[3]`: **un buco spezza la linea**, non la unisce con un segmento che direbbe un valore mai
 * misurato. Un tratto di una sola posizione e' un punto isolato.
 */
internal fun fluidLineRuns(values: List<Float?>): List<List<Int>> {
  val runs = mutableListOf<List<Int>>()
  var current = mutableListOf<Int>()
  values.forEachIndexed { i, v ->
    if (v != null && v.isFinite()) {
      current.add(i)
    } else if (current.isNotEmpty()) {
      runs.add(current)
      current = mutableListOf()
    }
  }
  if (current.isNotEmpty()) runs.add(current)
  return runs
}

/**
 * Le tangenti di una curva monotona (Fritsch–Carlson) per i punti ([xs], [ys]): la curva che li unisce **non
 * supera mai** i valori misurati. Una spline qualunque «rimbalza» oltre il massimo fra due punti e disegna un
 * picco che i dati non hanno; qui fra due punti la curva sta fra i loro due valori.
 */
internal fun fluidMonotoneTangents(xs: FloatArray, ys: FloatArray): FloatArray {
  val n = xs.size
  if (n < 2) return FloatArray(n)
  val dx = FloatArray(n - 1) { xs[it + 1] - xs[it] }
  val slope = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (ys[it + 1] - ys[it]) / dx[it] }
  val t = FloatArray(n)
  t[0] = slope[0]
  t[n - 1] = slope[n - 2]
  for (i in 1 until n - 1) t[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
  for (i in 0 until n - 1) {
    if (slope[i] == 0f) {
      t[i] = 0f
      t[i + 1] = 0f
    } else {
      val a = t[i] / slope[i]
      val b = t[i + 1] / slope[i]
      val s = a * a + b * b
      if (s > 9f) {
        val k = 3f / kotlin.math.sqrt(s)
        t[i] = k * a * slope[i]
        t[i + 1] = k * b * slope[i]
      }
    }
  }
  return t
}

/** La casella dell'indice [index] fra [count], in una fascia larga [width] che comincia a [left]: il suo centro. */
internal fun fluidSlotCenter(index: Int, count: Int, left: Float, width: Float): Float =
  if (count <= 0) left + width / 2f else left + (index + 0.5f) * (width / count)

/** La casella sotto una posizione [x] (in pixel) di una fascia di [count] caselle, o -1 fuori dalla fascia. */
internal fun fluidSlotAt(x: Float, count: Int, left: Float, width: Float): Int {
  if (count <= 0 || width <= 0f || x < left || x > left + width) return -1
  return ((x - left) / (width / count)).toInt().coerceIn(0, count - 1)
}

// --- Ciambella: angoli e bersagli --------------------------------------------------------------------------

/**
 * Gli angoli di una ciambella: per ogni quota in [fractions], l'inizio e l'ampiezza in gradi, in senso orario da
 * ore 12 (0°), con [gapDegrees] di spazio fra una fetta e la successiva.
 *
 * Con una fetta sola (o una sola non nulla) l'anello e' pieno e senza taglio. Una fetta piu' stretta dello
 * spazio non sparisce: tiene almeno meta' del suo angolo.
 */
internal fun fluidDonutAngles(fractions: List<Float>, gapDegrees: Float): List<Pair<Float, Float>> {
  val visible = fractions.count { it > 0f }
  val gap = if (visible > 1) gapDegrees else 0f
  var cursor = 0f
  return fractions.map { f ->
    val span = f * 360f
    val sweep = if (f <= 0f) 0f else max(span - gap, span * 0.5f)
    val start = cursor + (span - sweep) / 2f
    cursor += span
    start to sweep
  }
}

/**
 * Quale fetta sta sotto il punto ([dx], [dy]) misurato dal centro, in pixel: -1 fuori dall'anello (con [slop]
 * pixel di tolleranza oltre i bordi) o sul foro.
 *
 * Gli spazi fra le fette appartengono alla fetta vicina (si divide per le quote, non per gli angoli disegnati):
 * non c'e' un punto morto in cui il dito tocca l'anello e non succede niente. Una fetta a quota zero non si
 * trova mai.
 */
internal fun fluidDonutHit(
  dx: Float,
  dy: Float,
  innerRadius: Float,
  outerRadius: Float,
  slop: Float,
  fractions: List<Float>,
): Int {
  val radius = hypot(dx, dy)
  if (radius < innerRadius - slop || radius > outerRadius + slop) return -1
  var degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
  if (degrees < 0f) degrees += 360f
  val total = fractions.sumOf { it.toDouble() }.toFloat()
  if (total <= 0f) return -1
  var edge = 0f
  var last = -1
  fractions.forEachIndexed { i, f ->
    if (f > 0f) {
      last = i
      edge += f / total * 360f
      if (degrees < edge) return i
    }
  }
  return last
}

/**
 * Il rettangolo che contiene un arco di anello (centro [cx],[cy], raggi [outer] e [inner], da [startDegrees] per
 * [sweepDegrees], da ore 12 in senso orario): `[left, top, right, bottom]`. Serve a dare a ogni fetta il suo
 * nodo per TalkBack, che ha bisogno di un rettangolo.
 */
internal fun fluidArcBounds(
  cx: Float,
  cy: Float,
  outer: Float,
  inner: Float,
  startDegrees: Float,
  sweepDegrees: Float,
): FloatArray {
  var left = Float.MAX_VALUE
  var top = Float.MAX_VALUE
  var right = -Float.MAX_VALUE
  var bottom = -Float.MAX_VALUE
  fun add(degrees: Float, radius: Float) {
    val rad = Math.toRadians(degrees.toDouble())
    val x = cx + (radius * sin(rad)).toFloat()
    val y = cy - (radius * cos(rad)).toFloat()
    left = min(left, x)
    right = max(right, x)
    top = min(top, y)
    bottom = max(bottom, y)
  }
  val end = startDegrees + sweepDegrees
  val angles = mutableListOf(startDegrees, end)
  var cardinal = ceil(startDegrees / 90f) * 90f
  while (cardinal < end) {
    angles.add(cardinal)
    cardinal += 90f
  }
  angles.forEach { add(it, outer); add(it, inner) }
  return floatArrayOf(left, top, right, bottom)
}

/**
 * Lo spessore dell'anello come frazione del diametro: 0,22 col testo normale, che scende fino a 0,15 al 200%.
 * Il foro e' dove stanno il valore e l'etichetta del centro, che crescono con il testo di sistema: con l'anello
 * fisso il numero uscirebbe dal foro; stringendo l'anello il foro cresce e il numero ci sta.
 */
internal fun fluidDonutRingFraction(fontScale: Float): Float {
  val t = ((fontScale - 1f) / 1f).coerceIn(0f, 1f)
  return 0.22f - 0.07f * t
}

// --- Colori di serie --------------------------------------------------------------------------------------

/**
 * L'indice nella palette d'identita' ([FluidIdentityColors.palette]) della serie [position] fra [count]: parte dal
 * blu (7) e distribuisce le serie a passo uguale lungo il cerchio cromatico, cosi' due serie sono blu e
 * arancio, tre blu viola e verde. Da 12 serie in poi la palette gira (le tinte si ripetono: raggruppa in «Altro»).
 *
 * **Non e' un contratto di stabilita'**, a differenza di [FluidIdentityColors.indexFor]: dice solo «tinte lontane
 * fra loro». Chi vuole che «Promozioni» sia sempre dello stesso colore passa il colore.
 */
internal fun fluidSeriesPaletteIndex(position: Int, count: Int): Int {
  val size = FluidIdentityColors.palette.size
  val n = max(1, count)
  val spread = if (n >= size) position else position * size / n
  return Math.floorMod(7 + spread, size)
}

/** Un testo con segnaposto (`%1$s`) nella lingua di [locale]: i numeri si scrivono con le sue cifre. */
internal fun fluidChartFormat(locale: Locale, template: String, vararg args: Any): String =
  String.format(locale, template, *args)
