/*
 * I grafici da toccare (4.0, F8.6): lo scrubber dei grafici cartesiani dell'engine, `FluidLineChart` e `FluidBarChart`.
 * Codice del Fluid Engine.
 *
 * Il riferimento e' Swift Charts con `chartXSelection` e le annotazioni, il gesto di Borsa e di Salute: il dito sul
 * grafico sceglie il punto piu' vicino, compaiono la linea verticale, il punto e una capsula col valore; con due dita si
 * sceglie un intervallo e la capsula dice la differenza, su una fascia fra le due linee. Al rilascio tutto torna nel
 * punto da cui era nato.
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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

  /** La fascia fra due punti scelti con due dita: l'accento a **0,14** (a occhio, 24 novembre 2026, come Borsa). */
  const val BandAlpha: Float = 0.14f

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
    val range = fluidChartScrubRange(first, second) ?: return "" to ""
    if (range.first == range.last) {
      val i = range.first
      val line = joinSeries { s -> values[s].getOrNull(i)?.let(format) ?: noValue }
      return line to xLabels.getOrElse(i) { "" }
    }
    val from = range.first
    val to = range.last
    val line = joinSeries { s ->
      val a = values[s].getOrNull(from)
      val b = values[s].getOrNull(to)
      if (a == null || b == null) noValue else delta?.invoke(a, b) ?: fluidChartDeltaText(a, b, format, locale, labels)
    }
    return line to labels.chartSpan(xLabels.getOrElse(from) { "" }, xLabels.getOrElse(to) { "" })
  }

  private fun joinSeries(value: (Int) -> String): String =
    if (values.size <= 1) {
      if (values.isEmpty()) "" else value(0)
    } else {
      values.indices.joinToString(" · ") { s -> "${seriesLabels.getOrElse(s) { "" }} ${value(s)}".trim() }
    }
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
 * Il livello dello scrubber, sopra il piano: la fascia fra due punti, le linee verticali e i punti. Ha un layer suo
 * (`graphicsLayer`): quando il dito cambia punto si ridisegna lui, il piano no.
 */
@Composable
internal fun FluidChartScrubLayer(
  state: FluidChartScrubState,
  plan: FluidCartesianPlan,
  model: FluidChartScrubModel,
  modifier: Modifier = Modifier,
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
        val ruleColor = if (model.dots && model.values.size == 1) model.colors.getOrElse(0) { rule } else rule
        val xa = plan.centerX(a)
        if (b >= 0 && b != a) {
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
              drawCircle(model.colors.getOrElse(s) { scheme.primary }, radius, Offset(x, y))
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
  val lastText = remember(model) { arrayOf("" to "") }
  val texts by remember(model) {
    derivedStateOf {
      if (state.first >= 0) model.text(state.first, state.second).also { lastText[0] = it } else lastText[0]
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
    val (value, label) = texts
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
