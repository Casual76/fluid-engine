/*
 * I grafici da toccare (4.0, F8.6): lo scrubber dei grafici cartesiani dell'engine, `FluidLineChart` e `FluidBarChart`.
 * Codice del Fluid Engine.
 *
 * Il riferimento e' Swift Charts con `chartXSelection` e le annotazioni, il gesto di Borsa e di Salute: il dito sul
 * grafico sceglie il punto piu' vicino, compaiono la linea verticale, il punto e una capsula col valore; con due dita si
 * sceglie un intervallo e la capsula dice la differenza. Con due dita (4.0, F8.6b, scelta di Alessio del 24 novembre) il
 * grafico fa come Borsa: fuori dall'intervallo linea e area sbiadiscono, e la differenza, le due linee e i due punti sono
 * verdi se si sale e rossi se si scende ([FluidChartRangeHighlight.Signed]); la fascia d'accento resta per i grafici in
 * cui salire non e' ne' bene ne' male ([FluidChartRangeHighlight.Band]). Al rilascio tutto torna nel punto da cui era
 * nato.
 *
 * **Cio' che muove il dito resta nitido** (regola di Alessio, 24 novembre): la linea, il punto, la capsula e lo sbiadito
 * seguono il dito senza alcuna sfocatura di movimento. Si sfoca solo cio' che si muove da solo.
 *
 * Il costo e' tenuto fuori dal grafico: lo stato dello scrubber ([FluidChartScrubState]) e' letto **solo** nel disegno
 * di un livello suo (la linea, la fascia e il punto) e nella capsula. Il piano del grafico, i tracciati e le etichette
 * non si ricompongono e non si ridisegnano mentre il dito scorre; a dito fermo nessuno scrive niente.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Come si legge l'intervallo scelto con due dita (4.0, F8.6b).
 */
enum class FluidChartRangeHighlight {
  /**
   * Come Borsa: fuori dall'intervallo linea e area sbiadiscono, e la differenza, le due linee e i due punti sono verdi se
   * si sale e rossi se si scende. Per i grafici in cui il segno dice «meglio» o «peggio»: i voti, un prezzo, i minuti di
   * ascolto. E' il modo di serie.
   */
  Signed,

  /**
   * Una fascia d'accento fra le due linee, senza il colore del segno e senza sbiadire niente: per i grafici in cui salire
   * non e' ne' bene ne' male (lo spazio occupato, i download).
   */
  Band,
}

/** Le misure e i tempi dello scrubber dei grafici (F8.6). */
object FluidChartTouchDefaults {
  /**
   * Quanto il dito deve restare fermo sul grafico perche' lo scrubber parta senza che il dito si muova: **300 ms**.
   * Borsa dice «tocca e tieni premuto il grafico»; il trascinamento di lato lo fa partire subito, come `chartXSelection`.
   * A occhio (24 novembre 2026): sotto i 400–500 ms del tocco lungo di Android, sopra un tocco normale (~100 ms).
   */
  const val HoldMillis: Long = 300L

  /**
   * Lo spessore della linea verticale: **1 dp**. E' il `lineWidth` di serie di `RuleMark` in Swift Charts (1 pt; fiducia
   * alta: il valore di serie di `StrokeStyle`).
   */
  val RuleWidth: Dp = 1.dp

  /**
   * L'inchiostro della linea verticale con piu' serie e sulle barre, sul testo del tema: **0,45** (a occhio, 24 novembre
   * 2026). Con una serie sola la linea e' del colore della serie, come in Borsa (immagine della guida di Borsa per Mac).
   */
  const val RuleAlpha: Float = 0.45f

  /**
   * Il raggio del punto scelto: **5 dp**, con un alone di **1,5 dp** del colore della superficie: 13 dp in tutto, il
   * punto di Borsa (~12,5 pt col suo bordo chiaro, misura sull'immagine @2x della guida di Borsa per Mac, fiducia media,
   * 24 novembre 2026; `sessioni/f8-6/riferimenti/fonti.md`).
   */
  val DotRadius: Dp = 5.dp

  /** L'alone del punto scelto, del colore della superficie: lo stacca dalla linea che gli passa sotto. */
  val DotHalo: Dp = 1.5.dp

  /**
   * La fascia fra due punti scelti con due dita, nel modo [FluidChartRangeHighlight.Band]: l'accento a **0,14** (a occhio,
   * 24 novembre 2026).
   */
  const val BandAlpha: Float = 0.14f

  /**
   * Quanto sbiadisce il grafico fuori dall'intervallo nel modo [FluidChartRangeHighlight.Signed]: se ne toglie lo **0,6**,
   * ne resta il 40%. Misura sull'immagine @2x della guida di Borsa per Mac (il tratto e l'area fuori dall'intervallo hanno
   * circa il 40% dell'inchiostro di quelli dentro; fiducia media, 24 novembre 2026; `sessioni/f8-6/riferimenti/fonti.md`).
   */
  const val RangeFadeAlpha: Float = 0.6f

  /**
   * Il verde di chi sale e il rosso di chi scende: `systemGreen` e `systemRed` delle HIG «Color». In chiaro le varianti
   * ad alto contrasto (#248A3D, #D70015), in scuro quelle di serie (#30D158, #FF453A): fiducia alta, sono i valori della
   * tabella. Si usano anche come testo, e sono portati a 4,5:1 sulle superfici dei grafici ([fluidChartSignColors]).
   */
  val RiseLight: Color = Color(0xFF248A3D)
  val RiseDark: Color = Color(0xFF30D158)
  val FallLight: Color = Color(0xFFD70015)
  val FallDark: Color = Color(0xFFFF453A)

  /** Lo spazio fra la capsula e il punto, quando la capsula deve scendere sotto il punto per non coprirlo. */
  val CapsuleGap: Dp = 6.dp

  /** L'imbottitura della capsula, di lato e in alto e in basso: una pastiglia come la bolla dello scorrimento rapido. */
  val CapsulePaddingHorizontal: Dp = 12.dp
  val CapsulePaddingVertical: Dp = 6.dp

  /**
   * Da quanto e' piccola la capsula quando nasce dal punto (e quando ci torna): **0,6**, con la molla `snappy` dei
   * controlli. A occhio (24 novembre 2026): il viaggio d'oro di F1B, che nasce dal punto e non dal nulla.
   */
  const val CapsuleBirthScale: Float = 0.6f
}

/**
 * Gli interruttori e i contatori delle prove dei grafici (la galleria, le sessioni sul tablet): **non sono API per le
 * app**, come `FluidPageBarDiagnostics`. I contatori contano sempre (un intero in piu' per disegno): le prove li azzerano
 * con [resetForTesting] e guardano quanto crescono.
 */
object FluidChartDiagnostics {
  /** Il tag di prova della capsula dello scrubber (c'e' solo mentre la capsula c'e'). */
  const val CapsuleTag: String = "fluid-chart-scrub-capsule"

  /** Le composizioni del piano di un grafico cartesiano (assi, tracciati, etichette): non devono crescere sotto il dito. */
  var plotCompositions: Int = 0
    internal set

  /** I disegni del piano di un grafico cartesiano (guide, barre, tracciati). */
  var plotDraws: Int = 0
    internal set

  /** I disegni del livello dello scrubber (linea, fascia, punto): uno per punto toccato, non per pixel. */
  var scrubDraws: Int = 0
    internal set

  /** Le composizioni della capsula: una per punto toccato, non per pixel. */
  var capsuleCompositions: Int = 0
    internal set

  fun resetForTesting() {
    plotCompositions = 0
    plotDraws = 0
    scrubDraws = 0
    capsuleCompositions = 0
  }
}

/**
 * Dove sta lo scrubber: il punto sotto il primo dito ([first]) e quello sotto il secondo ([second]), -1 se non c'e'.
 * Letto solo nel disegno e nella capsula.
 */
@Stable
internal class FluidChartScrubState {
  var first by mutableIntStateOf(-1)
    private set
  var second by mutableIntStateOf(-1)
    private set

  val active: Boolean get() = first >= 0

  fun set(a: Int, b: Int) {
    if (first != a) first = a
    if (second != b) second = b
  }
}

/**
 * Il gesto dello scrubber, fuori dal disegno: chi suona, chi avvisa l'app, quale punto e' sotto quale dito. Le tacche
 * aptiche sono quelle dello scrubber e dello slider dell'engine ([FluidSliderNotchGate]): un `Tick` quando il dito
 * si posa su un punto nuovo, con la rete dei 55 ms dei gesti continui, uno per dito.
 */
internal class FluidChartScrubber(val state: FluidChartScrubState) {
  var haptics: FluidHaptics? = null
  var onScrub: ((IntRange?) -> Unit)? = null
  private val firstGate = FluidSliderNotchGate()
  private val secondGate = FluidSliderNotchGate()

  fun begin() {
    firstGate.restart(-1)
    secondGate.restart(-1)
  }

  /** Il primo dito sta su [a], il secondo su [b] (-1: un dito solo). */
  fun move(a: Int, b: Int, nowMillis: Long) {
    val wasA = state.first
    val wasB = state.second
    if (a == wasA && b == wasB) return
    val tickA = a != wasA && firstGate.land(a, nowMillis)
    val tickB = b != wasB && b >= 0 && secondGate.land(b, nowMillis)
    if (b < 0) secondGate.restart(-1)
    if (tickA || tickB) haptics?.play(FluidHapticEvent.Tick)
    state.set(a, b)
    onScrub?.invoke(fluidChartScrubRange(a, b))
  }

  fun end() {
    if (!state.active) return
    state.set(-1, -1)
    onScrub?.invoke(null)
  }
}

/**
 * Il verde e il rosso del segno come si leggono: quelli di [FluidChartTouchDefaults] per il lato del tema, portati verso
 * `onSurface` del minimo che serve perche' si leggano a 4,5:1 su tutte le superfici dei grafici (sono anche testo).
 */
internal fun fluidChartSignColors(scheme: ColorScheme, glassFilm: Color, dark: Boolean): Pair<Color, Color> {
  val surfaces = fluidChartSurfaces(scheme, glassFilm)
  fun legible(start: Color): Color {
    fun worst(c: Color) = surfaces.minOf { fluidContrastRatio(c, it) }
    if (worst(start) >= 4.5f) return start
    var low = 0f
    var high = 1f
    repeat(12) {
      val mid = (low + high) / 2f
      if (worst(androidx.compose.ui.graphics.lerp(start, scheme.onSurface, mid)) >= 4.5f) high = mid else low = mid
    }
    return androidx.compose.ui.graphics.lerp(start, scheme.onSurface, high)
  }
  val rise = if (dark) FluidChartTouchDefaults.RiseDark else FluidChartTouchDefaults.RiseLight
  val fall = if (dark) FluidChartTouchDefaults.FallDark else FluidChartTouchDefaults.FallLight
  return legible(rise) to legible(fall)
}

@Composable
internal fun rememberFluidChartSignColors(): Pair<Color, Color> {
  val scheme = MaterialTheme.colorScheme
  val film = GlassDefaults.glassFilm()
  val dark = GlassDefaults.isDarkSurface()
  return remember(scheme, film, dark) { fluidChartSignColors(scheme, film, dark) }
}

/** Il segno di una differenza: +1 se si sale, -1 se si scende, 0 se resta uguale o non si sa. */
internal fun fluidChartSign(from: Float?, to: Float?): Int = when {
  from == null || to == null || !(to - from).isFinite() -> 0
  to > from -> 1
  to < from -> -1
  else -> 0
}

/** L'intervallo scelto: un punto solo con un dito, dal piu' piccolo al piu' grande con due. */
internal fun fluidChartScrubRange(a: Int, b: Int): IntRange? = when {
  a < 0 -> null
  b < 0 || b == a -> a..a
  else -> min(a, b)..max(a, b)
}

/**
 * Cio' che la capsula scrive e dove stanno i punti: i valori `values[serie][punto]` (null: un buco), le etichette e i
 * colori delle serie, il formato del valore e quello della differenza.
 */
internal class FluidChartScrubModel(
  val xLabels: List<String>,
  val seriesLabels: List<String>,
  val values: List<List<Float?>>,
  val colors: List<Color>,
  val format: (Float) -> String,
  val delta: ((from: Float, to: Float) -> String)?,
  val noValue: String,
  val labels: FluidDataLabels,
  val locale: Locale,
  /** Vero per un andamento (il punto si disegna), falso per le barre (si evidenzia la casella). */
  val dots: Boolean,
  /** Vero nel modo di Borsa ([FluidChartRangeHighlight.Signed]): con due dita il segno colora e il fuori sbiadisce. */
  val signed: Boolean = false,
) {
  val count: Int get() = xLabels.size

  fun pointY(plan: FluidCartesianPlan, series: Int, index: Int): Float? =
    values.getOrNull(series)?.getOrNull(index)?.let { plan.yOf(it.toDouble()) }

  /** Il punto piu' alto della colonna [index] (il bordo alto dell'area, se la colonna e' vuota). */
  fun top(plan: FluidCartesianPlan, index: Int): Float {
    var best = Float.NaN
    for (s in values.indices) {
      val y = pointY(plan, s, index) ?: continue
      if (best.isNaN() || y < best) best = y
    }
    return if (best.isNaN()) plan.bottom else best
  }

  /** Le due righe della capsula: in alto il valore (o la differenza), sotto l'etichetta (o l'intervallo). */
  fun text(first: Int, second: Int): Pair<String, String> {
    val (parts, label) = parts(first, second)
    return parts.joinToString(PartSeparator) { it.text } to label
  }

  /**
   * La prima riga della capsula a pezzi, uno per serie, ognuno col suo segno (+1 sale, -1 scende, 0 niente: con un dito,
   * per un buco, per una differenza nulla), e la seconda riga. Con piu' serie ogni pezzo porta il nome della sua.
   */
  fun parts(first: Int, second: Int): Pair<List<FluidChartScrubPart>, String> {
    val range = fluidChartScrubRange(first, second) ?: return emptyList<FluidChartScrubPart>() to ""
    if (range.first == range.last) {
      val i = range.first
      val parts = eachSeries { s -> FluidChartScrubPart(values[s].getOrNull(i)?.let(format) ?: noValue, 0) }
      return parts to xLabels.getOrElse(i) { "" }
    }
    val from = range.first
    val to = range.last
    val parts = eachSeries { s ->
      val a = values[s].getOrNull(from)
      val b = values[s].getOrNull(to)
      if (a == null || b == null) {
        FluidChartScrubPart(noValue, 0)
      } else {
        FluidChartScrubPart(delta?.invoke(a, b) ?: fluidChartDeltaText(a, b, format, locale, labels), fluidChartSign(a, b))
      }
    }
    return parts to labels.chartSpan(xLabels.getOrElse(from) { "" }, xLabels.getOrElse(to) { "" })
  }

  /** Il segno di tutto l'intervallo (per le due linee): quello delle serie se va per tutte nello stesso verso, se no 0. */
  fun rangeSign(first: Int, second: Int): Int {
    val range = fluidChartScrubRange(first, second) ?: return 0
    if (range.first == range.last) return 0
    val signs = values.indices.map { s -> fluidChartSign(values[s].getOrNull(range.first), values[s].getOrNull(range.last)) }
      .filter { it != 0 }
    return if (signs.isNotEmpty() && signs.all { it == signs[0] }) signs[0] else 0
  }

  /** Il segno della serie [series] nell'intervallo (per il suo punto). */
  fun seriesSign(series: Int, first: Int, second: Int): Int {
    val range = fluidChartScrubRange(first, second) ?: return 0
    if (range.first == range.last) return 0
    return fluidChartSign(values.getOrNull(series)?.getOrNull(range.first), values.getOrNull(series)?.getOrNull(range.last))
  }

  private fun eachSeries(part: (Int) -> FluidChartScrubPart): List<FluidChartScrubPart> =
    if (values.size <= 1) {
      if (values.isEmpty()) emptyList() else listOf(part(0))
    } else {
      values.indices.map { s ->
        val p = part(s)
        FluidChartScrubPart("${seriesLabels.getOrElse(s) { "" }} ${p.text}".trim(), p.sign)
      }
    }

  private companion object {
    const val PartSeparator = " · "
  }
}

/** Un pezzo della prima riga della capsula: il testo e il segno (+1 sale, -1 scende, 0 niente). */
internal class FluidChartScrubPart(val text: String, val sign: Int) {
  override fun equals(other: Any?): Boolean = other is FluidChartScrubPart && other.text == text && other.sign == sign
  override fun hashCode(): Int = text.hashCode() * 31 + sign
}

/**
 * La differenza fra due valori come la scrive la capsula di serie: col segno (`+`, o il meno tipografico) e il valore
 * nel formato del grafico, poi la variazione in percentuale se il valore di partenza non e' zero: «+1,5 (+20 %)», come
 * Borsa con due dita.
 */
internal fun fluidChartDeltaText(
  from: Float,
  to: Float,
  format: (Float) -> String,
  locale: Locale,
  labels: FluidDataLabels,
): String {
  val difference = to - from
  val magnitude = fluidSigned(difference, format(abs(difference)))
  if (from == 0f || !difference.isFinite()) return magnitude
  val change = difference / abs(from)
  val percent = NumberFormat.getPercentInstance(locale).apply {
    maximumFractionDigits = if (abs(change) < 0.1f) 1 else 0
    minimumFractionDigits = 0
  }.format(abs(change).toDouble())
  return labels.chartDelta(magnitude, fluidSigned(change, percent))
}

private fun fluidSigned(value: Float, text: String): String = when {
  value > 0f -> "+$text"
  value < 0f -> "−$text"
  else -> text
}

/** La colonna sotto [x]: fuori dall'area vale la prima o l'ultima, cosi' il dito che sborda resta sull'estremo. */
internal fun fluidChartScrubIndex(x: Float, plan: FluidCartesianPlan): Int {
  if (plan.count <= 0) return -1
  return fluidSlotAt(x.coerceIn(plan.left, plan.right), plan.count, plan.left, plan.width)
}

private enum class ChartGrip { Undecided, Scrub, Page }

/**
 * Il gesto del grafico: un tocco sceglie una colonna (com'era), il dito che scorre di lato **o** che resta fermo per
 * [FluidChartTouchDefaults.HoldMillis] prende lo scrubber, il dito che scorre in verticale lascia scorrere la pagina
 * (l'arbitraggio dello slider dell'engine: [fluidSliderClaim]). Un secondo dito, prima o dopo, sceglie l'intervallo.
 * Preso il gesto, il grafico consuma tutto fino al rilascio: la pagina sotto non si muove.
 */
internal suspend fun PointerInputScope.fluidChartGesture(
  plan: () -> FluidCartesianPlan,
  scrubber: FluidChartScrubber?,
  onTap: (Int?) -> Unit,
) {
  awaitEachGesture {
    val down = awaitFirstDown()
    var grip = ChartGrip.Undecided
    var travel = Offset.Zero
    // «Tieni premuto» vale finche' il dito non si e' mosso oltre meta' dello slop; il tempo si conta dal primo tocco.
    var holdable = scrubber != null
    val holdAt = down.uptimeMillis + FluidChartTouchDefaults.HoldMillis
    var now = down.uptimeMillis
    // L'evento che ha deciso (il secondo dito, il dito di lato): lo scrubber parte da li', senza aspettarne un altro.
    var trigger: androidx.compose.ui.input.pointer.PointerEvent? = null
    while (grip == ChartGrip.Undecided) {
      val event = if (holdable) {
        withTimeoutOrNull((holdAt - now).coerceAtLeast(1L)) { awaitPointerEvent() }
      } else {
        awaitPointerEvent()
      }
      if (event == null) {
        // Il dito e' rimasto fermo: «tocca e tieni premuto», come Borsa.
        grip = ChartGrip.Scrub
        break
      }
      val pressedCount = event.changes.count { it.pressed }
      if (scrubber != null && pressedCount >= 2) {
        grip = ChartGrip.Scrub
        trigger = event
        break
      }
      val change = event.changes.fastFirstOrNull { it.id == down.id } ?: return@awaitEachGesture
      if (change.isConsumed) return@awaitEachGesture
      if (!change.pressed) {
        change.consume()
        val p = plan()
        val inPlot = down.position.y >= p.top && down.position.y <= p.bottom
        val index = if (inPlot) fluidSlotAt(down.position.x, p.count, p.left, p.width) else -1
        onTap(if (index < 0) null else index)
        return@awaitEachGesture
      }
      travel += change.positionChange()
      now = change.uptimeMillis
      when (fluidSliderClaim(travel, viewConfiguration.touchSlop)) {
        FluidSliderClaim.Slider -> {
          grip = if (scrubber != null) ChartGrip.Scrub else ChartGrip.Page
          trigger = event
        }
        FluidSliderClaim.Page -> grip = ChartGrip.Page
        FluidSliderClaim.Undecided -> Unit
      }
      if (abs(travel.x) + abs(travel.y) > viewConfiguration.touchSlop / 2f) holdable = false
    }
    if (grip != ChartGrip.Scrub || scrubber == null) return@awaitEachGesture

    var primary: PointerId = down.id
    var secondary: PointerId? = null
    var lastX = down.position.x
    scrubber.begin()
    try {
      // Il primo punto: dove il dito e' rimasto fermo (o, piu' sotto, l'evento che ha deciso).
      if (trigger == null) scrubber.move(fluidChartScrubIndex(lastX, plan()), -1, down.uptimeMillis)
      var pending = trigger
      while (true) {
        val event = pending ?: awaitPointerEvent(PointerEventPass.Main)
        pending = null
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) {
          event.changes.forEach(PointerInputChange::consume)
          break
        }
        val primaryChange = pressed.fastFirstOrNull { it.id == primary }
          ?: pressed.first().also { primary = it.id; if (secondary == it.id) secondary = null }
        if (secondary != null && pressed.none { it.id == secondary }) secondary = null
        if (secondary == null) secondary = pressed.fastFirstOrNull { it.id != primary }?.id
        val secondChange = secondary?.let { id -> pressed.fastFirstOrNull { it.id == id } }
        lastX = primaryChange.position.x
        val p = plan()
        val a = fluidChartScrubIndex(lastX, p)
        val b = secondChange?.let { fluidChartScrubIndex(it.position.x, p) } ?: -1
        scrubber.move(a, b, primaryChange.uptimeMillis)
        // I due assi, ogni cambiamento, fino al rilascio: la pagina sotto non scorre.
        event.changes.forEach(PointerInputChange::consume)
      }
    } finally {
      scrubber.end()
    }
  }
}

/**
 * Lo sbiadito di Borsa fuori dall'intervallo (4.0, F8.6b), sul piano del grafico: una maschera `DstOut` sopra il piano
 * gia' registrato. Il piano sta nel suo layer (quello della sorgente di vetro) e non si ridisegna: quando le dita
 * cambiano punto si rifa' solo questo involucro, che riusa il layer del piano. Fuori dai due punti resta il 40%
 * dell'inchiostro ([FluidChartTouchDefaults.RangeFadeAlpha]). Il layer si compone fuori schermo solo mentre l'intervallo
 * c'e': a riposo il piano si compone come prima.
 */
internal fun Modifier.fluidChartRangeFade(state: FluidChartScrubState, plan: FluidCartesianPlan, model: FluidChartScrubModel?): Modifier {
  if (model == null || !model.signed) return this
  return this
    .graphicsLayer {
      val a = state.first
      val b = state.second
      compositingStrategy = if (a >= 0 && b >= 0 && a != b) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
      drawContent()
      val a = state.first
      val b = state.second
      if (a < 0 || b < 0 || a == b) return@drawWithContent
      val lo = min(plan.centerX(a), plan.centerX(b))
      val hi = max(plan.centerX(a), plan.centerX(b))
      val veil = Color.Black.copy(alpha = FluidChartTouchDefaults.RangeFadeAlpha)
      if (lo > 0f) drawRect(veil, Offset.Zero, Size(lo, size.height), blendMode = BlendMode.DstOut)
      if (hi < size.width) drawRect(veil, Offset(hi, 0f), Size(size.width - hi, size.height), blendMode = BlendMode.DstOut)
    }
}

/**
 * Il livello dello scrubber, sopra il piano: le linee verticali e i punti, e la fascia fra due punti nel modo
 * [FluidChartRangeHighlight.Band]. Ha un layer suo (`graphicsLayer`): quando il dito cambia punto si ridisegna lui, il
 * piano no. Nel modo di Borsa le due linee e i due punti prendono il colore del segno ([rise], [fall]).
 */
@Composable
internal fun FluidChartScrubLayer(
  state: FluidChartScrubState,
  plan: FluidCartesianPlan,
  model: FluidChartScrubModel,
  modifier: Modifier = Modifier,
  rise: Color = Color.Unspecified,
  fall: Color = Color.Unspecified,
) {
  val scheme = MaterialTheme.colorScheme
  val rule = scheme.onSurface.copy(alpha = FluidChartTouchDefaults.RuleAlpha)
  val band = scheme.primary.copy(alpha = FluidChartTouchDefaults.BandAlpha)
  val plate = scheme.onSurface.copy(alpha = 0.08f)
  val halo = scheme.surface
  Box(
    modifier
      .graphicsLayer()
      .drawBehind {
        FluidChartDiagnostics.scrubDraws++
        val a = state.first
        if (a < 0) return@drawBehind
        val b = state.second
        val ruleWidth = FluidChartTouchDefaults.RuleWidth.toPx()
        // Con una serie sola la linea e' del colore della serie, come in Borsa; con piu' serie e per le barre, neutra.
        // Con due dita nel modo di Borsa e' del colore del segno.
        val range = b >= 0 && b != a
        fun signColor(sign: Int, otherwise: Color): Color = when {
          !model.signed || !range -> otherwise
          sign > 0 && rise != Color.Unspecified -> rise
          sign < 0 && fall != Color.Unspecified -> fall
          else -> otherwise
        }
        val plain = if (model.dots && model.values.size == 1) model.colors.getOrElse(0) { rule } else rule
        val ruleColor = signColor(model.rangeSign(a, b), plain)
        val xa = plan.centerX(a)
        if (range && !model.signed) {
          val xb = plan.centerX(b)
          drawRect(band, Offset(min(xa, xb), plan.top), Size(abs(xb - xa), plan.height))
        }
        val columns = if (b >= 0 && b != a) intArrayOf(a, b) else intArrayOf(a)
        for (index in columns) {
          val x = plan.centerX(index)
          if (!model.dots) {
            drawRect(plate, Offset(plan.left + index * plan.slot, plan.top), Size(plan.slot, plan.height))
          }
          drawLine(ruleColor, Offset(x, plan.top), Offset(x, plan.bottom), strokeWidth = ruleWidth)
          if (model.dots) {
            val radius = FluidChartTouchDefaults.DotRadius.toPx()
            val haloPx = FluidChartTouchDefaults.DotHalo.toPx()
            for (s in model.values.indices) {
              val y = model.pointY(plan, s, index) ?: continue
              drawCircle(halo, radius + haloPx, Offset(x, y))
              drawCircle(signColor(model.seriesSign(s, a, b), model.colors.getOrElse(s) { scheme.primary }), radius, Offset(x, y))
            }
          }
        }
      },
  )
}

/**
 * La capsula di vetro col valore: in cima al grafico, centrata sulla linea e tenuta dentro i bordi del grafico; scende
 * sotto il punto se il punto starebbe sotto di lei. Nasce dal punto (piccola e trasparente) e ci torna al rilascio;
 * mentre il dito scorre scivola da un punto all'altro con la molla dei controlli. Il testo si ricompone solo quando
 * cambia il punto.
 *
 * Il vetro e' quello dell'engine ([fluidGlass]): campiona il fondo dello schermo e il piano del grafico ([plotSource]),
 * che si registra solo mentre la capsula c'e'.
 */
@Composable
internal fun FluidChartScrubCapsule(
  state: FluidChartScrubState,
  plan: FluidCartesianPlan,
  model: FluidChartScrubModel,
  backdrop: GlassBackdropState,
  widthPx: Float,
  rise: Color = Color.Unspecified,
  fall: Color = Color.Unspecified,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val density = androidx.compose.ui.platform.LocalDensity.current
  val gapPx = with(density) { FluidChartTouchDefaults.CapsuleGap.toPx() }
  val appear = remember { Animatable(0f) }
  val anchor = remember { Animatable(0f) }
  // Il punto a cui la capsula e' legata (nasce li' e ci torna), e il punto piu' alto sotto di lei.
  val target by remember(plan, model) {
    derivedStateOf {
      val a = state.first
      if (a < 0) {
        null
      } else {
        val b = state.second
        val x = if (b >= 0 && b != a) (plan.centerX(a) + plan.centerX(b)) / 2f else plan.centerX(a)
        val top = if (b >= 0 && b != a) min(model.top(plan, a), model.top(plan, b)) else model.top(plan, a)
        x to top
      }
    }
  }
  var lastTop by remember { mutableIntStateOf(0) }
  // La capsula c'e' dal primo punto alla fine del suo ritorno: due composizioni per gesto, poi niente vetro attaccato.
  var present by remember { mutableStateOf(false) }
  LaunchedEffect(reduced) {
    snapshotFlow { target }.collectLatest { t ->
      if (t == null) {
        if (reduced) appear.snapTo(0f) else appear.animateTo(0f, FluidMotion.snappy())
        present = false
      } else {
        lastTop = t.second.roundToInt()
        present = true
        if (appear.value <= 0.01f || reduced) {
          anchor.snapTo(t.first)
        }
        if (reduced) appear.snapTo(1f) else appear.animateTo(1f, FluidMotion.snappy())
      }
    }
  }
  LaunchedEffect(reduced) {
    snapshotFlow { target?.first }.collectLatest { x ->
      if (x == null) return@collectLatest
      if (reduced || appear.value <= 0.01f) anchor.snapTo(x) else anchor.animateTo(x, FluidMotion.instant())
    }
  }
  // Al rilascio la capsula torna nel punto col suo ultimo testo: il testo si tiene finche' non ce n'e' uno nuovo.
  val lastText = remember(model) { arrayOf(emptyList<FluidChartScrubPart>() to "") }
  val texts by remember(model) {
    derivedStateOf {
      if (state.first >= 0) model.parts(state.first, state.second).also { lastText[0] = it } else lastText[0]
    }
  }
  val scheme = MaterialTheme.colorScheme
  val muted = rememberFluidChartMuted()

  Box(
    Modifier
      .layout { measurable, constraints ->
        val placeable = measurable.measure(
          Constraints(maxWidth = (widthPx.roundToInt()).coerceAtLeast(1), maxHeight = constraints.maxHeight),
        )
        layout(0, 0) {
          val p = appear.value
          if (p <= 0f) return@layout
          val w = placeable.width.toFloat()
          val h = placeable.height.toFloat()
          val x = (anchor.value - w / 2f).coerceIn(0f, max(0f, widthPx - w))
          // In cima; sotto il punto se il punto starebbe sotto la capsula.
          val pointTop = lastTop.toFloat() - FluidChartTouchDefaults.DotRadius.toPx() - gapPx
          val y = if (pointTop >= h) 0f else (lastTop + FluidChartTouchDefaults.DotRadius.toPx() + gapPx).coerceAtMost(plan.bottom - h)
          placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
            alpha = p
            val scale = lerp(FluidChartTouchDefaults.CapsuleBirthScale, 1f, p)
            scaleX = scale
            scaleY = scale
            // Nasce dal punto: l'origine della scala e' dove la linea tocca la capsula.
            val pivotX = if (w > 0f) ((anchor.value - x) / w).coerceIn(0f, 1f) else 0.5f
            transformOrigin = TransformOrigin(pivotX, if (y <= 0f) 1f else 0f)
          }
        }
      }
      .semantics { hideFromAccessibility() },
  ) {
    SideEffect { FluidChartDiagnostics.capsuleCompositions++ }
    val (parts, label) = texts
    // Nel modo di Borsa ogni differenza ha il colore del suo segno; il resto e' il testo del tema.
    val value = buildAnnotatedString {
      parts.forEachIndexed { i, part ->
        if (i > 0) append(" · ")
        val color = when {
          !model.signed || part.sign == 0 -> Color.Unspecified
          part.sign > 0 -> rise
          else -> fall
        }
        if (color == Color.Unspecified) append(part.text) else withStyle(SpanStyle(color = color)) { append(part.text) }
      }
    }
    if (present && (value.isNotEmpty() || label.isNotEmpty())) {
      Column(
        Modifier
          .testTag(FluidChartDiagnostics.CapsuleTag)
          .fluidGlass(FluidGlass.Regular, FluidCapsuleShape, backdrop)
          .padding(
            horizontal = FluidChartTouchDefaults.CapsulePaddingHorizontal,
            vertical = FluidChartTouchDefaults.CapsulePaddingVertical,
          ),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = value,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
          color = scheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
        if (label.isNotEmpty()) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}
