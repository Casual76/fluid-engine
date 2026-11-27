/*
 * La ghiera a righello (4.0, F8.2): il righello di «Raddrizza» in Foto e la ghiera dello zoom in Fotocamera. Apple non
 * ha un'API: il riferimento sono le app, misurate sulle schermate della guida dell'iPhone (iOS 26 e 18,
 * `artifacts/liquid-glass/sessioni/f8-2/riferimenti/`). Codice del Fluid Engine.
 *
 * Un righello di tacche fitte scorre di lato sotto un indice fermo. Il valore segue il dito 1:1, continua con l'inerzia
 * di UIKit quando lo si lancia (`FluidDetentInertia.kt`) e si ferma su una tacca, con uno scatto sulle principali e sul
 * valore neutro. Un tic per tacca, piu' deciso sulle principali, e un colpo sul neutro e alle estremita'.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * L'aspetto della ghiera (F8.2). Misure dalle schermate di Foto in iOS 26 (Ritaglia, ~1,15 px a punto) e iOS 18.
 */
internal object FluidRulerLook {
  /** La strada fra due tacche: 9 pt (10,1 px nella schermata di iOS 26, uguale in iOS 18). Fiducia media. */
  val TickSpacing: Dp = 9.dp

  /** L'altezza di una tacca: 14 pt in iOS 26 (16 px), 10 pt in iOS 18; tutte uguali, le principali piu' scure. */
  val TickHeight: Dp = 14.dp

  /**
   * Lo spessore di una tacca: 1,8 pt. Dal profilo d'inchiostro delle tacche nella schermata di iOS 26 (la principale
   * copre ~2,05 px a 1,15 px a punto con l'inchiostro pieno 0,8). Fiducia media: la schermata e' ridotta. Corretto il 24
   * novembre 2026 dopo la tavola contro Foto: a 1 dp le tacche erano la meta' di quelle di Apple.
   */
  val TickWidth: Dp = 1.8.dp

  /**
   * L'inchiostro di una tacca: 0,49 le altre, 0,8 le principali (iOS 26, dal profilo: l'inchiostro totale diviso per la
   * larghezza; il picco della schermata ridotta, 0,43, lo sottostima). Fiducia alta sulle principali, media sulle altre.
   */
  const val MinorInk: Float = 0.49f
  const val MajorInk: Float = 0.8f

  /** L'indice: 28 pt di altezza (32 px), 2 pt di spessore, nel colore d'accento (il giallo di Foto). */
  val NeedleHeight: Dp = 28.dp
  val NeedleWidth: Dp = 2.dp

  /** La dissolvenza ai lati: le tacche spariscono negli ultimi 30 pt (iOS 26: da 0,14 a 0,45 in ~35 px). Fiducia media. */
  val EdgeFade: Dp = 30.dp

  /** Il punto sopra il valore neutro (iOS 18: il pallino sulla posizione originale), 2,5 dp di raggio, a 4 dp dalle tacche. */
  val NeutralDot: Dp = 2.5.dp
  val NeutralDotGap: Dp = 4.dp

  /** Lo spazio fra il valore scritto e le tacche. */
  val LabelGap: Dp = 4.dp

  /**
   * Il magnete del valore neutro mentre si trascina, in tacche: il valore resta sul neutro entro mezza tacca, come lo
   * zero di «Raddrizza» che si sente sotto il dito. A occhio, 23 novembre 2026.
   */
  const val NeutralMagnetTicks: Float = 0.5f

  /**
   * Fin dove una tacca principale attira la fine di un lancio, in frazione dell'intervallo fra due principali: un
   * quarto (con una principale ogni 10 tacche, 2,5 tacche). A occhio, 23 novembre 2026.
   */
  const val MajorSnapFraction: Float = 0.25f

  /**
   * F7.24b · Sotto questa velocita', in pixel al secondo, un lancio che decade passa la mano alla molla dell'assestamento
   * (che lo posa esatto sulla tacca). Col decadimento di iOS (k = 2 al secondo) e la mano passata solo a un pixel
   * ([FluidDetentMotion.HandoffPx]), un rilascio a 12–13 tacche al secondo (il gesto di agent-device sul Tab S9, 26 novembre
   * 2026) strisciava ~2,4 s sotto la tacca prima di fermarsi, e la pagina disegnava a 120 fotogrammi al secondo per tutto
   * quel tempo. A 30 px/s il lancio dura ~1,4 s, come uno scorrimento di iOS con la stessa velocita'; la tacca d'arrivo
   * e' la stessa. Solo la ghiera: la ruota ([FluidWheelPicker]) resta com'e'.
   */
  const val FlingHandoffSpeedPx: Float = 30f

  /** Sopra questa velocita' (tacche al secondo) le tacche suonano il `DragTick` leggero; sotto il `Tick`. */
  const val FastTicksPerSecond: Float = 10f
}

/**
 * La ghiera a righello: un righello di tacche fitte che scorre di lato sotto un indice fermo, per regolare un valore
 * fine (i gradi per raddrizzare una foto, lo zoom, la velocita', una calibrazione). A differenza del cursore non ha un
 * inizio e una fine in vista, e il dito non copre il valore: il valore e' scritto sopra l'indice.
 *
 * **Il gesto.** Un trascinamento di lato porta il righello col dito (1:1: [step] ogni 9 dp); oltre le estremita' cede
 * con l'elastico di UIKit. Lasciato con velocita' continua con l'inerzia di una lista di iOS e si ferma su una tacca: se
 * finisce vicino a una principale, sulla principale. Un dito sulla ghiera che scorre la ferma. Il **doppio tocco** torna
 * al valore neutro. La ghiera si prende il dito a mezzo slop di lato prima che in verticale: dentro un pager scorre lei,
 * e una pagina che scorre in verticale resta della pagina.
 *
 * **Il valore neutro** ([neutralValue], di serie zero) e' un magnete: trascinando, entro mezza tacca il valore e'
 * esattamente il neutro, con un colpo aptico deciso all'arrivo. Un pallino sopra le tacche lo segna (come in Foto).
 *
 * **L'aptica**: un `DragTick` per tacca a dito veloce, un `Tick` per tacca lenta e per ogni principale, un `Threshold`
 * sul neutro e alle estremita'; con la rete dei 55 ms dei gesti continui, cosi' un lancio veloce non e' un ronzio.
 *
 * **Il valore e' del chiamante** come in `FluidSlider`: [onValueChange] arriva durante il gesto e l'inerzia, gia'
 * portato al passo ([step]) e dentro [valueRange]; [onValueChangeFinished] quando la ghiera si ferma. Un valore cambiato
 * da fuori porta il righello con una molla (col movimento ridotto si posa).
 *
 * **Il movimento ridotto** toglie l'inerzia lunga (al piu' una tacca oltre il dito); lo scatto resta.
 *
 * **TalkBack**: un nodo regolabile con il valore scritto, «aumenta» e «diminuisci» di un passo.
 *
 * @param valueRange l'intervallo; di serie ±45, quello di «Raddrizza».
 * @param step il valore fra due tacche (un grado, 0,1 dB…).
 * @param majorEvery ogni quante tacche una principale (10, come in Foto), contando dal valore neutro se c'e'.
 * @param neutralValue il valore neutro (lo zero), o null se non c'e'.
 * @param valueText come si scrive il valore sopra l'indice (e per TalkBack); null non lo scrive.
 * @param contentDescription il nome della ghiera per TalkBack.
 */
@Composable
fun FluidRulerDial(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = -45f..45f,
  step: Float = 1f,
  majorEvery: Int = 10,
  neutralValue: Float? = 0f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  valueText: ((Float) -> String)? = { fluidRulerDefaultText(it) },
  contentDescription: String? = null,
) {
  // F8.15c: il numero passa in uno stato e la ghiera lo legge dove serve (effetti, disegno, semantica). Questo adattatore
  // si ricompone quando il chiamante passa un valore nuovo; il corpo della ghiera no.
  val current = rememberUpdatedState(value)
  val provider = remember(current) { { current.value } }
  FluidRulerDial(provider, onValueChange, modifier, valueRange, step, majorEvery, neutralValue, enabled, onValueChangeFinished,
    valueText, contentDescription)
}

/**
 * La ghiera col valore letto da [value] solo dove serve (F8.15c): chi la usa non legge il valore in composizione, e
 * mentre il dito la muove **non si ricompone niente**, ne' il chiamante ne' la ghiera. Cambiano il disegno delle tacche,
 * la scritta sopra l'indice (che si ricompone da sola, quando cambia la scritta) e la semantica (quando cambia la tacca).
 * E' la forma da usare quando il valore cambia a ogni fotogramma (l'esposizione di una foto mentre il righello gira).
 * Gli altri parametri sono quelli dell'altra forma.
 */
@Composable
fun FluidRulerDial(
  value: () -> Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = -45f..45f,
  step: Float = 1f,
  majorEvery: Int = 10,
  neutralValue: Float? = 0f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  valueText: ((Float) -> String)? = { fluidRulerDefaultText(it) },
  contentDescription: String? = null,
) {
  // Tutto quello che il chiamante puo' ricreare a ogni ricomposizione passa in stati fissi: il corpo salta.
  val valueState = rememberUpdatedState(value)
  val changeState = rememberUpdatedState(onValueChange)
  val finishedState = rememberUpdatedState(onValueChangeFinished)
  val textState = rememberUpdatedState(valueText)
  val scale = remember(valueRange.start, valueRange.endInclusive, step, majorEvery, neutralValue) {
    FluidRulerScale(valueRange, step, majorEvery, neutralValue)
  }
  RulerDialBody(valueState, changeState, finishedState, textState, valueText != null, scale, modifier, enabled,
    contentDescription)
}

@Composable
private fun RulerDialBody(
  value: State<() -> Float>,
  onValueChange: State<(Float) -> Unit>,
  onFinished: State<(() -> Unit)?>,
  valueText: State<((Float) -> String)?>,
  hasText: Boolean,
  scale: FluidRulerScale,
  modifier: Modifier,
  enabled: Boolean,
  contentDescription: String?,
) {
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val haptics = LocalFluidHaptics.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val labels = LocalFluidLabels.current.dateTime
  val increase = labels.increase
  val decrease = labels.decrease
  val description = contentDescription
  val scope = rememberCoroutineScope()
  val spacingPx = with(density) { FluidRulerLook.TickSpacing.toPx() }
  // Il valore di partenza si legge senza farsi osservare: una lettura qui, anche dentro `remember`, ricomporrebbe la
  // ghiera a ogni valore.
  val driver = remember(scope) {
    FluidRulerDriver(scope, scale.stepsOf(Snapshot.withoutReadObservation { value.value() }))
  }
  SideEffect {
    driver.update(scale, value, onValueChange, onFinished, spacingPx, reducedMotion, haptics, isLtr)
    FluidRulerDialDiagnostics.compositions++
  }
  // Un valore cambiato da fuori porta il righello (con una molla): si segue con un flusso, senza ricomporre.
  LaunchedEffect(driver, scale) { snapshotFlow { value.value() }.collect { driver.follow(it) } }

  val ink = scheme.onSurface
  val accent = scheme.primary
  val tickW = with(density) { FluidRulerLook.TickWidth.toPx() }
  val tickH = with(density) { FluidRulerLook.TickHeight.toPx() }
  val needleW = with(density) { FluidRulerLook.NeedleWidth.toPx() }
  val needleH = with(density) { FluidRulerLook.NeedleHeight.toPx() }
  val fadePx = with(density) { FluidRulerLook.EdgeFade.toPx() }
  val dotR = with(density) { FluidRulerLook.NeutralDot.toPx() }
  val dotGap = with(density) { FluidRulerLook.NeutralDotGap.toPx() }

  Column(
    modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      // TalkBack legge il valore scritto, la posizione nell'intervallo, «imposta», «aumenta» e «diminuisci». La semantica
      // si legge qui (non ricompone) e, mentre il dito muove il righello, aspetta la fine del gesto (F7.24).
      .fluidSettledSemantics {
        val s = scale
        val v = value.value()
        description?.let { this.contentDescription = it }
        valueText.value?.let { stateDescription = it(s.quantize(v)) }
        progressBarRangeInfo = ProgressBarRangeInfo(
          current = v.coerceIn(s.start, s.end),
          range = s.start..s.end,
          steps = if (s.ticks in 2..1000) s.ticks - 1 else 0,
        )
        if (enabled) {
          setProgress { target -> driver.set(target) }
          customActions = listOf(
            CustomAccessibilityAction(increase) { driver.nudge(1) },
            CustomAccessibilityAction(decrease) { driver.nudge(-1) },
          )
        } else {
          disabled()
        }
      }
      .then(if (enabled) Modifier.rulerGesture(driver) else Modifier),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(FluidRulerLook.LabelGap),
  ) {
    if (hasText) {
      RulerLabel(driver, scale, valueText, accent, scheme.onSurfaceVariant)
    }
    Box(Modifier.fillMaxWidth().height(FluidRulerLook.NeedleHeight)) {
      // Le tacche restano nitide anche durante l'inerzia: la ghiera la muove il dito (F8.2b). La posizione si legge qui,
      // nel disegno: muovere il righello ridisegna le tacche e basta.
      Canvas(Modifier.matchParentSize()) {
        FluidRulerDialDiagnostics.tickDraws++
        val position = driver.position
        val w = size.width
        val cx = w / 2f
        val bottom = size.height
        val top = bottom - tickH
        val reach = ceil(cx / spacingPx).toInt() + 1
        val base = floor(position).toInt()
        val dir = if (isLtr) 1f else -1f
        for (i in base - reach..base + reach + 1) {
          if (i < 0 || i > scale.ticks) continue
          val x = cx + (i - position) * spacingPx * dir
          if (x < -tickW || x > w + tickW) continue
          val edge = fluidRulerEdgeFade(x, w, fadePx)
          if (edge <= 0f) continue
          val major = scale.isMajor(i)
          val alpha = (if (major) FluidRulerLook.MajorInk else FluidRulerLook.MinorInk) * edge
          drawLine(ink, Offset(x, top), Offset(x, bottom), strokeWidth = tickW, alpha = alpha)
          if (scale.isNeutral(i)) {
            drawCircle(ink, radius = dotR, center = Offset(x, top - dotGap - dotR), alpha = FluidRulerLook.MajorInk * edge)
          }
        }
      }
      // L'indice, fermo e nitido: sopra le tacche, alto quanto la ghiera.
      Canvas(Modifier.matchParentSize()) {
        val cx = size.width / 2f
        val bottom = size.height
        drawLine(accent, Offset(cx, bottom - needleH + needleW / 2f), Offset(cx, bottom - needleW / 2f), strokeWidth = needleW, cap = StrokeCap.Round)
      }
    }
  }
}

/**
 * Il valore scritto sopra l'indice: **disegnato**, non composto (F7.24). Si legge la posizione nel disegno e si misura la
 * scritta solo quando cambia (la cache del misuratore): niente ricomposizione, niente nuova misura della pagina e,
 * con l'accessibilità accesa, nessun controllo della semantica a ogni tacca (la scritta non ha semantica: il valore lo
 * dice la ghiera). Ha uno strato suo: il ridisegno a ogni fotogramma resta qui. Le posizioni sono quelle del `Text`
 * centrato di prima, al pixel.
 */
@Composable
private fun RulerLabel(
  driver: FluidRulerDriver,
  scale: FluidRulerScale,
  valueText: State<((Float) -> String)?>,
  accent: Color,
  neutralInk: Color,
) {
  val measurer = rememberTextMeasurer(cacheSize = 8)
  val density = LocalDensity.current
  val style = FluidTextStyles.numeric
  // Una riga dello stile numerico (interlinea fissa): l'altezza di ogni scritta.
  val heightPx = remember(measurer, density) { measurer.measure("0", style, maxLines = 1).size.height }
  SideEffect { FluidRulerDialDiagnostics.labelCompositions++ }
  Spacer(
    Modifier
      .fillMaxWidth()
      .height(with(density) { heightPx.toDp() })
      .clearAndSetSemantics {}
      .graphicsLayer()
      .drawBehind {
        FluidRulerDialDiagnostics.labelDraws++
        val tick = fluidDetentRound(driver.position)
        val text = valueText.value?.invoke(scale.valueOf(tick)).orEmpty()
        val ink = if (scale.isNeutral(tick.roundToInt())) neutralInk else accent
        val layout = measurer.measure(
          text, style.copy(color = ink, textAlign = TextAlign.Center), softWrap = false, maxLines = 1,
          constraints = Constraints(maxWidth = size.width.toInt().coerceAtLeast(0)), layoutDirection = layoutDirection,
          density = this,
        )
        // Come `Alignment.Center` del Box di prima: arrotondato al pixel.
        val x = ((size.width - layout.size.width) / 2f).roundToInt().toFloat()
        val y = ((size.height - layout.size.height) / 2f).roundToInt().toFloat()
        drawText(layout, topLeft = Offset(x, y))
      },
  )
}

/**
 * I contatori delle prove della ghiera (F8.15c): **non sono API per le app**, come `FluidChartDiagnostics`. Contano
 * sempre (un intero in piu' per composizione o disegno); le prove li azzerano con [resetForTesting] e guardano quanto
 * crescono mentre il dito muove il righello.
 */
object FluidRulerDialDiagnostics {
  /** Le composizioni del corpo della ghiera: non devono crescere a ogni valore. */
  var compositions: Int = 0
    internal set

  /** Le composizioni della scritta sopra l'indice: da F7.24 la scritta si disegna, e non si ricompone più. */
  var labelCompositions: Int = 0
    internal set

  /** I disegni della scritta sopra l'indice (F7.24): uno per fotogramma in cui il righello si muove. */
  var labelDraws: Int = 0
    internal set

  /** I disegni delle tacche: uno per fotogramma in cui il righello si muove. */
  var tickDraws: Int = 0
    internal set

  fun resetForTesting() {
    compositions = 0
    labelCompositions = 0
    labelDraws = 0
    tickDraws = 0
  }
}

/** La scritta di serie: il valore senza decimali inutili e col segno meno tipografico («−12», «0», «2,5»). */
internal fun fluidRulerDefaultText(value: Float): String {
  val rounded = (value * 10f).roundToLong() / 10.0
  val whole = rounded == floor(rounded)
  val body = if (whole) abs(rounded).toLong().toString() else abs(rounded).toString().replace('.', ',')
  return if (rounded < 0) "−$body" else body
}

/**
 * Quanto una tacca a [x] e' visibile su una ghiera larga [width]: sparisce linearmente negli ultimi [fadePx] a ogni lato.
 */
internal fun fluidRulerEdgeFade(x: Float, width: Float, fadePx: Float): Float {
  if (fadePx <= 0f) return if (x in 0f..width) 1f else 0f
  val fromEdge = minOf(x, width - x)
  return (fromEdge / fadePx).coerceIn(0f, 1f)
}

/**
 * La scala della ghiera: i valori come tacche. La tacca `0` e' l'inizio dell'intervallo, l'ultima ([ticks]) la fine;
 * le principali e il neutro si contano dal neutro (o dall'inizio), cosi' lo zero e' sempre una principale.
 */
@Stable
internal class FluidRulerScale(
  range: ClosedFloatingPointRange<Float>,
  step: Float,
  majorEvery: Int,
  neutral: Float?,
) {
  val start: Float = minOf(range.start, range.endInclusive)
  val end: Float = maxOf(range.start, range.endInclusive)
  val step: Float = if (step > 0f && step.isFinite()) step else 1f
  val ticks: Int = ((end - start) / this.step + 1e-3f).toInt().coerceAtLeast(0)
  val majorEvery: Int = majorEvery.coerceAtLeast(1)
  val neutralTick: Int = neutral?.let { ((it.coerceIn(start, end) - start) / this.step).roundToInt() } ?: -1
  private val majorOrigin: Int = if (neutralTick >= 0) neutralTick else 0

  /** Il valore della tacca (anche frazionaria) [steps]. */
  fun valueOf(steps: Float): Float = (start + steps.coerceIn(0f, ticks.toFloat()) * step).coerceIn(start, end)

  /** Dove sta [value] in tacche. */
  fun stepsOf(value: Float): Float = ((value.coerceIn(start, end) - start) / step).coerceIn(0f, ticks.toFloat())

  /** [value] portato alla tacca piu' vicina. */
  fun quantize(value: Float): Float = valueOf(fluidDetentRound(stepsOf(value)))

  fun isMajor(tick: Int): Boolean = Math.floorMod(tick - majorOrigin, majorEvery) == 0

  fun isNeutral(tick: Int): Boolean = neutralTick >= 0 && tick == neutralTick

  /** Dove si ferma un lancio che finirebbe in [projected] (tacche): sulla principale se e' vicina, se no sulla tacca. */
  fun landing(projected: Float): Float {
    val nearest = fluidDetentRound(projected).coerceIn(0f, ticks.toFloat())
    val reach = majorEvery * FluidRulerLook.MajorSnapFraction
    val k = ((projected - majorOrigin) / majorEvery).roundToInt()
    val major = (majorOrigin + k * majorEvery).toFloat()
    return if (major in 0f..ticks.toFloat() && abs(projected - major) <= reach) major else nearest
  }
}

/**
 * Lo stato vivo della ghiera: la posizione in tacche (disegnata), il gesto, l'inerzia, cosa suona. Come la ruota
 * ([FluidWheelDriver]), ma il valore si comunica mentre si muove.
 */
@Stable
internal class FluidRulerDriver(private val scope: CoroutineScope, initial: Float) {
  var position by mutableFloatStateOf(initial)
    private set

  private var scale: FluidRulerScale? = null
  private var valueState: State<() -> Float>? = null
  private var changeState: State<(Float) -> Unit>? = null
  private var finishedState: State<(() -> Unit)?>? = null
  /** Il valore del chiamante adesso (letto fuori dalla composizione: nel gesto, nell'inerzia, nella semantica). */
  private val value: Float get() = valueState?.value?.invoke() ?: 0f
  private fun onValueChange(v: Float) = changeState?.value?.invoke(v)
  private val onFinished: (() -> Unit)? get() = finishedState?.value
  var spacingPx: Float = 0f
    private set
  private var reducedMotion = false
  private var haptics: FluidHaptics? = null
  var isLtr: Boolean = true
    private set

  private val inertia = FluidDetentInertia().apply { snapTo(initial) }
  private val notches = FluidSliderNotchGate()
  private var job: Job? = null
  private val frame: (Long) -> Long = { it }
  private var lastReported = Float.NaN
  private var atEdge = false
  private var atNeutral = false
  private var lastMillis = 0L
  var dragging: Boolean = false
    private set
  val moving: Boolean get() = job?.isActive == true
  var widthPx: Float = 0f

  fun update(
    scale: FluidRulerScale,
    value: State<() -> Float>,
    onValueChange: State<(Float) -> Unit>,
    onFinished: State<(() -> Unit)?>,
    spacingPx: Float,
    reducedMotion: Boolean,
    haptics: FluidHaptics,
    isLtr: Boolean,
  ) {
    this.scale = scale
    this.valueState = value
    this.changeState = onValueChange
    this.finishedState = onFinished
    this.spacingPx = spacingPx
    this.reducedMotion = reducedMotion
    this.haptics = haptics
    this.isLtr = isLtr
  }

  private val ticks: Float get() = (scale?.ticks ?: 0).toFloat()

  fun grab(): Boolean {
    val was = moving
    job?.cancel()
    job = null
    inertia.snapTo(position)
    restartHaptics()
    return was
  }

  private fun restartHaptics() {
    val s = scale ?: return
    val tick = fluidDetentRound(position).roundToInt()
    notches.restart(tick)
    atEdge = position <= 0f || position >= ticks
    atNeutral = s.isNeutral(tick) && abs(position - tick) < 1e-3f
  }

  fun startDrag() {
    dragging = true
    lastReported = value
  }

  fun rawPosition(): Float {
    val max = ticks
    val dimension = widthPx
    if (spacingPx <= 0f) return position
    return when {
      position < 0f -> -inverseRubberBandTranslation(-position * spacingPx, dimension) / spacingPx
      position > max -> max + inverseRubberBandTranslation((position - max) * spacingPx, dimension) / spacingPx
      else -> position
    }
  }

  /** Il dito porta il righello a [raw] tacche (senza elastico ne' magnete). */
  fun dragTo(raw: Float, uptimeMillis: Long, speed: Float) {
    val s = scale ?: return
    val max = ticks
    val dimension = widthPx
    var next = when {
      spacingPx <= 0f -> raw
      raw < 0f -> -rubberBandTranslation(-raw * spacingPx, dimension) / spacingPx
      raw > max -> max + rubberBandTranslation((raw - max) * spacingPx, dimension) / spacingPx
      else -> raw
    }
    // Il magnete del neutro: entro mezza tacca il valore e' il neutro.
    if (s.neutralTick >= 0 && abs(next - s.neutralTick) <= FluidRulerLook.NeutralMagnetTicks) next = s.neutralTick.toFloat()
    position = next
    report(uptimeMillis, speed)
  }

  /**
   * Il dito si e' alzato con [speed] tacche al secondo. Un tocco ([finishIfStill] falso) su una ghiera ferma non e' un
   * gesto finito: niente `onValueChangeFinished`.
   */
  fun release(speed: Float, finishIfStill: Boolean = true) {
    val wasDragging = dragging
    dragging = false
    val max = ticks
    if (!finishIfStill && !wasDragging && speed == 0f && position == fluidDetentRound(position) && position in 0f..max) return
    if (position < 0f || position > max) {
      run(position.coerceIn(0f, max), speed, settle = true)
      return
    }
    val s = scale ?: return
    val target = if (reducedMotion) {
      fluidDetentLanding(position, speed, true, 0f, max)
    } else {
      s.landing(position + speed * FluidDetentMotion.ProjectionSeconds)
    }
    if (reducedMotion) run(target, 0f, settle = true, response = FluidDetentMotion.ReducedSettleResponseSeconds)
    else run(target, speed, settle = false)
  }

  /** Il doppio tocco: al neutro. */
  fun toNeutral() {
    val s = scale ?: return
    if (s.neutralTick < 0) return
    val target = s.neutralTick.toFloat()
    if (position == target) return
    if (reducedMotion) {
      inertia.snapTo(target)
      position = target
      report(lastMillis, 0f)
      finish()
      return
    }
    run(target, 0f, settle = true)
  }

  fun follow(newValue: Float) {
    val s = scale ?: return
    if (dragging || moving) return
    val target = s.stepsOf(newValue)
    if (abs(target - position) < 1e-4f) return
    if (reducedMotion || spacingPx <= 0f) {
      inertia.snapTo(target)
      position = target
      restartHaptics()
      return
    }
    run(target, 0f, settle = true, response = FluidDetentMotion.FollowResponseSeconds, live = false)
  }

  /** TalkBack, «imposta». */
  fun set(target: Float): Boolean {
    val s = scale ?: return false
    val next = s.quantize(target)
    if (next != value) onValueChange(next)
    onFinished?.invoke()
    return true
  }

  /** TalkBack, «aumenta» e «diminuisci»: un passo. */
  fun nudge(delta: Int): Boolean {
    val s = scale ?: return false
    val next = s.valueOf(fluidDetentRound(s.stepsOf(value)) + delta)
    if (next == s.quantize(value)) return false
    onValueChange(next)
    onFinished?.invoke()
    return true
  }

  private fun run(
    target: Float,
    speed: Float,
    settle: Boolean,
    response: Double = FluidDetentMotion.SettleResponseSeconds,
    live: Boolean = true,
  ) {
    job?.cancel()
    if (settle) {
      inertia.springTo(position, speed, target, spacingPx, response)
    } else {
      inertia.flingTo(position, speed, target, spacingPx, handoffSpeedPx = FluidRulerLook.FlingHandoffSpeedPx)
    }
    job = scope.launch {
      val glide: suspend () -> Unit = {
        var last = -1L
        while (inertia.moving) {
          val now = withFrameNanos(frame)
          val dt = if (last < 0L) 0f else (now - last) / 1e9f
          last = now
          inertia.step(dt)
          position = inertia.position
          if (live) report(now / 1_000_000L, inertia.velocity) else restartHaptics()
        }
        position = inertia.position
        if (live) {
          report(lastMillis, 0f)
          finish()
        } else {
          restartHaptics()
        }
      }
      // F8.1b: l'inerzia di un lancio (e il ritorno al neutro col doppio tocco) conta come il dito: i numeri che mostrano
      // il valore cambiano di colpo, nitidi, fino alla posa. Quella che segue un valore dell'app no.
      if (live) fluidFingerMotion(glide) else glide()
    }
  }

  private fun finish() {
    onFinished?.invoke()
  }

  /** Il valore alla posizione di adesso al chiamante, se e' cambiato; e cosa si sente. */
  private fun report(nowMillis: Long, speed: Float) {
    lastMillis = nowMillis
    val s = scale ?: return
    val tick = fluidDetentRound(position).roundToInt()
    val edge = position <= 0f || position >= ticks
    val neutral = s.isNeutral(tick) && abs(position - tick) < 1e-3f
    val crossed = notches.land(tick.coerceIn(0, s.ticks), nowMillis)
    when {
      edge && !atEdge -> haptics?.play(FluidHapticEvent.Threshold)
      neutral && !atNeutral -> haptics?.play(FluidHapticEvent.Threshold)
      crossed -> haptics?.play(
        if (s.isMajor(tick) || abs(speed) <= FluidRulerLook.FastTicksPerSecond) FluidHapticEvent.Tick else FluidHapticEvent.DragTick,
      )
    }
    atEdge = edge
    atNeutral = neutral
    val next = s.valueOf(fluidDetentRound(position.coerceIn(0f, ticks)))
    if (next != lastReported) {
      lastReported = next
      if (next != value) onValueChange(next)
    }
  }
}

// F8.1b: il dito muove un valore (un numero che lo mostra resta nitido, `FluidFingerDrive`).
private fun Modifier.rulerGesture(driver: FluidRulerDriver): Modifier = fluidFingerDrivesValue().pointerInput(driver) {
  val tracker = VelocityTracker()
  var lastTapUp = Long.MIN_VALUE / 2
  var lastTapX = 0f
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    driver.widthPx = size.width.toFloat()
    val wasMoving = driver.grab()
    tracker.resetTracking()
    tracker.addPosition(down.uptimeMillis, down.position)
    var claimed = wasMoving
    if (claimed) down.consume()
    var travel = Offset.Zero
    var anchorX = down.position.x
    var anchorRaw = driver.rawPosition()
    var pointer = down.id
    var released = false
    fun dir() = if (driver.isLtr) 1f else -1f
    try {
      while (!claimed) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return@awaitEachGesture
        if (change.isConsumed) return@awaitEachGesture
        if (!change.pressed) {
          change.consume()
          released = true
          // Il secondo tocco vicino al primo, entro il tempo del doppio tocco: al neutro.
          val double = change.uptimeMillis - lastTapUp <= viewConfiguration.doubleTapTimeoutMillis &&
            abs(change.position.x - lastTapX) <= viewConfiguration.touchSlop * 2f
          if (double) {
            lastTapUp = Long.MIN_VALUE / 2
            driver.toNeutral()
          } else {
            lastTapUp = change.uptimeMillis
            lastTapX = change.position.x
            driver.release(0f, finishIfStill = false)
          }
          return@awaitEachGesture
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        travel += change.positionChange()
        when (fluidSliderClaim(travel, viewConfiguration.touchSlop)) {
          FluidSliderClaim.Slider -> {
            change.consume()
            claimed = true
            anchorX = change.position.x
            anchorRaw = driver.rawPosition()
          }
          FluidSliderClaim.Page -> return@awaitEachGesture
          FluidSliderClaim.Undecided -> Unit
        }
      }
      driver.startDrag()
      while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer }
          ?: event.changes.fastFirstOrNull { it.pressed }?.also {
            pointer = it.id
            anchorX = it.position.x
            anchorRaw = driver.rawPosition()
          }
          ?: break
        tracker.addPosition(change.uptimeMillis, change.position)
        if (!change.pressed) {
          change.consume()
          break
        }
        val spacing = driver.spacingPx
        if (spacing > 0f) {
          val speed = -tracker.calculateVelocity().x / spacing * dir()
          driver.dragTo(anchorRaw - (change.position.x - anchorX) / spacing * dir(), change.uptimeMillis, speed)
        }
        change.consume()
      }
      released = true
      val maxFling = viewConfiguration.maximumFlingVelocity
      val vx = tracker.calculateVelocity().x.coerceIn(-maxFling, maxFling)
      val spacing = driver.spacingPx
      driver.release(if (spacing > 0f) -vx / spacing * dir() else 0f)
    } finally {
      if (!released) driver.release(0f, finishIfStill = false)
    }
  }
}
