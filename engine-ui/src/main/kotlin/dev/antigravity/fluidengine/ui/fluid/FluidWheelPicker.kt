/*
 * Il selettore a ruota (4.0, F8.2): la ruota di iOS, `Picker` con `.pickerStyle(.wheel)`, `UIPickerView`, `UIDatePicker`
 * a ruota e la durata del timer di Orologio. Codice del Fluid Engine.
 *
 * La costruzione e' quella di `UIPickerView`, che disegna la ruota in tre strati: le voci fuori dalla fascia (piu'
 * tenui, curve sul cilindro), la fascia di selezione, e le voci dentro la fascia (piene). Qui la fascia e' **vetro**
 * dell'engine (`fluidGlass`) e resta ferma: si muove solo il contenuto, cosi' la lente non costa a ogni fotogramma piu'
 * di quanto costa ridisegnare le voci, che sono una registrazione piccola quanto la ruota (mai lo schermo intero).
 *
 *  1. le voci fuori dalla fascia stanno in una sorgente di vetro propria della ruota (`glassBackdropSource`): la fascia
 *     le piega al suo bordo, come una lente vera piega cio' che ha accanto;
 *  2. la fascia, ferma, campiona la pagina e quella sorgente;
 *  3. le voci dentro la fascia si disegnano **sopra** il vetro, nitide e appena ingrandite: e' il valore scelto.
 *
 * Il moto e' quello di UIKit (`FluidDetentInertia.kt`): il lancio decelera come una lista di iOS e finisce esattamente
 * su una voce, il dito che tocca una ruota che gira la ferma, un tocco su una voce la porta al centro. Un tic aptico per
 * voce, con la rete dei 55 ms dei gesti continui.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Una colonna di un [FluidWheelPicker]: [count] voci, quella scelta, e come si chiama ognuna.
 *
 * Il valore e' del chiamante, come in `FluidSlider`: la ruota mostra [selected] e chiede di cambiarlo con
 * [onSelectedChange] **quando si ferma** su una voce (come `pickerView(_:didSelectRow:inComponent:)`, non a ogni voce
 * che passa). Un chiamante che rifiuta (non aggiorna [selected]) vede la ruota tornare dov'era.
 *
 * @param count quante voci. Le lunghissime (gli anni) vanno bene: si disegnano e si misurano solo quelle in vista.
 * @param selected la voce scelta, da 0 a `count − 1`.
 * @param onSelectedChange la ruota si e' fermata su un'altra voce.
 * @param label il testo della voce: una funzione pura dell'indice. Se cambia (un'altra lingua), passane una nuova.
 * @param cyclic la ruota gira all'infinito, come le ore e i minuti di iOS: dopo l'ultima voce torna la prima.
 * @param unit una parola ferma accanto al valore nella fascia, come «ore» e «min» nel timer di Orologio.
 * @param contentDescription il nome della colonna per TalkBack («Ore»).
 * @param stateDescription cosa legge TalkBack come valore; di serie il testo della voce e [unit].
 * @param weight quanta larghezza prende, rispetto alle altre colonne.
 * @param textAlign dove sta il testo nella colonna; di serie al centro, o a destra del centro con [unit].
 * @param isEnabled le voci che si possono scegliere (un minimo e un massimo, come `UIDatePicker`): le altre sono piu'
 *   tenui, e una ruota che si ferma su una di loro scivola alla piu' vicina che si puo'. Null: tutte.
 */
@Immutable
class FluidWheelColumn(
  val count: Int,
  val selected: Int,
  val onSelectedChange: (Int) -> Unit,
  val label: (Int) -> String,
  val cyclic: Boolean = false,
  val unit: String? = null,
  val contentDescription: String? = null,
  val stateDescription: ((Int) -> String)? = null,
  val weight: Float = 1f,
  val textAlign: TextAlign = if (unit != null) TextAlign.End else TextAlign.Center,
  val isEnabled: ((Int) -> Boolean)? = null,
)

/** Le misure di serie della ruota, per chi le deve riservare. */
object FluidWheelDefaults {
  /** 216 dp: l'altezza di `UIPickerView` e di `UIDatePicker` a ruota (Apple, fiducia alta). */
  val Height: Dp = 216.dp

  /**
   * L'altezza di una voce: 32 dp, quella di `UIPickerView` (fiducia media: non documentata da Apple, misurata dalle
   * schermate; con il testo grande cresce con la scala del carattere, fino a 1,6).
   */
  val RowHeight: Dp = 32.dp
}

/**
 * L'aspetto della ruota (F8.2), con la fonte di ogni numero. Le misure in dp sono quelle in punti di iOS.
 *
 * Le schermate misurate (`artifacts/liquid-glass/sessioni/f8-2/riferimenti/`, 23 novembre 2026) sono il timer di
 * Orologio di iOS 27 e 26 (guida dell'iPhone, ~1,15 px a punto) e la ruota dell'ora delle HIG «Pickers» (@2x). Il
 * confronto con il codice di Flutter che imita iOS (`cupertino/picker.dart`, `date_picker.dart`, misure prese da Apple
 * con «Debug View Hierarchy») e' nel rapporto di F8.2.
 */
internal object FluidWheelLook {
  /** La fascia: 34 pt su voci da 32 (Flutter da iOS; 39 px ≈ 34 pt nel timer di iOS 27). Fiducia alta. */
  val BandExtra: Dp = 2.dp

  /** Il margine della fascia dai bordi della ruota: 9 pt (Flutter, da iOS 14–18). Fiducia media. */
  val BandInset: Dp = 9.dp

  /**
   * Il raggio del cilindro, come frazione dell'altezza della ruota: 0,39, cioe' 84 pt su 216. Dalle schermate: le voci
   * stanno a `R·sin(n·a)` dal centro con a ≈ 0,38 rad a voce (32 pt di arco) e R ≈ 84 pt (timer di iOS 27: 30,9, 57,8,
   * 76,5, 86 pt per le voci 1–4; HIG: 29,8, 55, 73, 82 pt a scala @2x). Tre voci piene per lato e una quarta schiacciata,
   * come iOS. Fiducia alta sulla forma, media sul valore (±4 pt).
   */
  const val RadiusFraction: Float = 0.39f

  /**
   * Quanto la fascia ingrandisce il valore scelto: 1,12. iOS ingrandisce la voce nella fascia: Flutter 2,35/2,1 = 1,12
   * per la ruota delle date, 25/21 pt per iOS 13, 34/32 per il timer; nelle HIG le altezze delle voci danno ~1,16.
   * Fiducia media (fra 1,06 e 1,19).
   */
  const val Magnification: Float = 1.12f

  /**
   * L'inchiostro delle voci fuori dalla fascia, accanto ad essa: 0,447 (Flutter, «off-center opacity»; le HIG,
   * estrapolate a zero gradi, danno 0,44). Uguale in chiaro e in scuro, come nelle HIG. Fiducia alta.
   */
  const val OuterInk: Float = 0.447f

  /**
   * La dissolvenza verso i bordi: l'inchiostro fuori dalla fascia cala come `exp(−FadePerRadian·|angolo|)`. Le HIG
   * danno 1,2 (0,28, 0,18, 0,11, 0,07 alle voci 1–4), la schermata del timer, piu' piccola e ricampionata, ~0,5. 0,9 sta
   * fra le due. Fiducia media.
   */
  const val FadePerRadian: Float = 0.9f

  /** L'inchiostro di una voce che non si puo' scegliere, sopra quello che avrebbe: la `tertiaryLabel` di iOS. */
  const val DisabledInk: Float = 0.35f

  /** Lo spazio fra il numero e la sua unita' («5 ore»): ~6 pt nel timer di Orologio di iOS 27. Fiducia media. */
  val UnitGap: Dp = 6.dp

  /** Sopra questa velocita' (voci al secondo) un tic per voce e' il `DragTick` leggero, sotto il `Tick` di un aggancio. */
  const val FastTickRowsPerSecond: Float = 8f

  /** Fino a che scala del carattere crescono le voci (oltre, la ruota mostrerebbe tre voci). */
  const val MaxRowScale: Float = 1.6f
}

/**
 * Un selettore a ruota di una sola colonna. Vedi l'altra forma di [FluidWheelPicker] per il comportamento.
 */
@Composable
fun FluidWheelPicker(
  count: Int,
  selected: Int,
  onSelectedChange: (Int) -> Unit,
  label: (Int) -> String,
  modifier: Modifier = Modifier,
  cyclic: Boolean = false,
  unit: String? = null,
  contentDescription: String? = null,
  stateDescription: ((Int) -> String)? = null,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  FluidWheelPicker(
    columns = listOf(
      FluidWheelColumn(
        count = count,
        selected = selected,
        onSelectedChange = onSelectedChange,
        label = label,
        cyclic = cyclic,
        unit = unit,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
      ),
    ),
    modifier = modifier,
    enabled = enabled,
    backdrop = backdrop,
  )
}

/**
 * Il selettore a ruota di iOS: una o piu' colonne di voci che girano col dito su un cilindro e si fermano a scatti sotto
 * una fascia di vetro, che ingrandisce appena il valore scelto. Piu' colonne affiancate fanno una durata, un'ora, un
 * valore con la sua unita' (le forme pronte: [FluidDurationWheel], [FluidTimeWheel]).
 *
 * **Le misure sono quelle di `UIPickerView`**, prese dalle schermate di iOS 26 e 27: alta 216 dp, voci da 32 dp su un
 * cilindro di 84 dp di raggio (tre voci piene per lato e una quarta schiacciata, accorciate col coseno dell'angolo), la
 * fascia a capsula alta 34 dp con 9 dp di margine, la voce scelta ingrandita di 1,12, le voci fuori dalla fascia al 45%
 * dell'inchiostro e piu' tenui verso i bordi. Vedi [FluidWheelLook].
 *
 * **Il gesto.**
 *  - Un trascinamento in verticale la gira, 1:1 col dito; oltre la prima e l'ultima voce cede con l'elastico di UIKit.
 *  - Lasciata con velocita' **decelera come una lista di iOS** (0,998 a millisecondo) e si ferma esattamente su una voce:
 *    il punto proiettato si porta sulla voce piu' vicina e il moto si ritocca per arrivarci, senza rimbalzo.
 *  - Un dito su una ruota che gira la **ferma**; sollevato senza muoversi, la posa sulla voce piu' vicina.
 *  - Un **tocco** su una voce sopra o sotto la fascia la porta al centro.
 *  - La ruota si prende il dito quando va in verticale per mezzo slop prima che di lato; se va prima di lato e' di chi
 *    sta sotto (un pager). Da li' consuma il gesto: dentro un foglio che si trascina, gira la ruota e non il foglio.
 *  - Un tic aptico per voce che passa sotto la fascia, con la rete dei 55 ms dei gesti continui: a un lancio veloce se ne
 *    sentono alcuni, non un ronzio, e sono i `DragTick` leggeri.
 *
 * **Il movimento ridotto** toglie l'inerzia lunga: un lancio porta al piu' una voce oltre quella sotto il dito, con un
 * assestamento di 0,20 s; lo scatto resta. Un valore cambiato da fuori si posa senza viaggio.
 *
 * **TalkBack**: ogni colonna e' un nodo regolabile (il nome, il valore detto, l'intervallo, «aumenta» e «diminuisci»
 * con le parole di `FluidLabels`, che su una ruota che gira all'infinito passano dall'ultima alla prima).
 *
 * **A riposo non ridisegna niente**; mentre gira si ridisegnano solo le voci (in disegno, nessuna ricomposizione) e il
 * moto non alloca per fotogramma. Le voci si misurano una volta.
 *
 * @param columns le colonne, da sinistra (da destra a sinistra si specchiano).
 * @param enabled spenta, la ruota e' a meta' opacita' e non risponde.
 * @param backdrop cio' che la fascia di vetro rifrange sotto le voci: di serie la superficie in scope.
 */
@Composable
fun FluidWheelPicker(
  columns: List<FluidWheelColumn>,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val haptics = LocalFluidHaptics.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val labels = LocalFluidLabels.current.dateTime
  val scope = rememberCoroutineScope()
  val rowScale = density.fontScale.coerceIn(1f, FluidWheelLook.MaxRowScale)
  val rowPx = with(density) { FluidWheelDefaults.RowHeight.toPx() } * rowScale
  val bandPx = rowPx + with(density) { FluidWheelLook.BandExtra.toPx() }

  val drivers = remember(columns.size, scope) {
    List(columns.size) { i -> FluidWheelDriver(scope, columns[i]) }
  }
  SideEffect {
    for (i in drivers.indices) drivers[i].update(columns[i], rowPx, reducedMotion, haptics, enabled)
  }

  val rowStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")
  val unitStyle = MaterialTheme.typography.titleMedium
  val measurer = rememberTextMeasurer(cacheSize = 0)
  val ink = scheme.onSurface
  val wheelSource = rememberGlassBackdrop()
  val bandBackdrop = rememberCombinedGlassBackdrop(backdrop, wheelSource)
  val unitGapPx = with(density) { FluidWheelLook.UnitGap.toPx() }

  Box(
    modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .height(FluidWheelDefaults.Height),
  ) {
    // 1. Le voci fuori dalla fascia, registrate: la fascia le piega al suo bordo.
    Row(Modifier.matchParentSize().glassBackdropSource(wheelSource)) {
      columns.forEachIndexed { i, column ->
        key(i) {
          val texts = rememberWheelLabels(measurer, rowStyle, unitStyle, column)
          WheelCanvas(drivers[i], texts, column, bandPx, inner = false, ink, isLtr, unitGapPx)
        }
      }
    }
    // 2. La fascia di vetro, ferma.
    Box(
      Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(horizontal = FluidWheelLook.BandInset)
        .height(with(density) { bandPx.toDp() })
        // Una capsula: la fascia di iOS 26 e 27 (timer di Orologio, HIG); fino a iOS 18 era un rettangolo a raggio 8.
        .fluidGlass(FluidGlass.Regular, FluidCapsuleShape, bandBackdrop),
    )
    // 3. Le voci dentro la fascia, sopra il vetro; i gesti e i nodi per TalkBack, una colonna per nodo.
    Row(Modifier.matchParentSize()) {
      columns.forEachIndexed { i, column ->
        key(i) {
          val driver = drivers[i]
          val texts = rememberWheelLabels(measurer, rowStyle, unitStyle, column)
          LaunchedEffect(driver, column.selected, column.count, column.cyclic) { driver.follow(column.selected) }
          Box(
            Modifier
              .weight(column.weight.coerceAtLeast(0.01f))
              .fillMaxHeight()
              .wheelSemantics(driver, column, enabled, labels.increase, labels.decrease)
              .then(if (enabled) Modifier.wheelGesture(driver) else Modifier),
          ) {
            WheelCanvasBox(driver, texts, column, bandPx, ink, isLtr, unitGapPx)
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Il disegno
// ---------------------------------------------------------------------------------------------

@Composable
private fun RowScope.WheelCanvas(
  driver: FluidWheelDriver,
  texts: WheelLabels,
  column: FluidWheelColumn,
  bandPx: Float,
  inner: Boolean,
  ink: Color,
  isLtr: Boolean,
  unitGapPx: Float,
) {
  Canvas(Modifier.weight(column.weight.coerceAtLeast(0.01f)).fillMaxHeight().wheelMotionBlur(driver)) {
    drawWheel(driver, texts, column, bandPx, inner, ink, isLtr, unitGapPx)
  }
}

@Composable
private fun WheelCanvasBox(
  driver: FluidWheelDriver,
  texts: WheelLabels,
  column: FluidWheelColumn,
  bandPx: Float,
  ink: Color,
  isLtr: Boolean,
  unitGapPx: Float,
) {
  Canvas(Modifier.fillMaxSize().wheelMotionBlur(driver)) {
    drawWheel(driver, texts, column, bandPx, inner = true, ink, isLtr, unitGapPx)
  }
}

/**
 * La sfocatura del movimento delle voci ([FluidDetentBlur]): solo mentre la ruota corre da sola, lungo il moto. Il blocco
 * del layer legge la posizione, quindi si rifa' a ogni fotogramma del moto e a riposo no.
 */
private fun Modifier.wheelMotionBlur(driver: FluidWheelDriver): Modifier = graphicsLayer {
  @Suppress("UNUSED_VARIABLE")
  val moved = driver.position
  val radius = if (driver.reduced) 0f else FluidDetentBlur.radiusPx(
    driver.motionSpeed, FluidDetentBlur.WheelFrom, FluidDetentBlur.WheelFull, FluidDetentBlur.MaxDp * density,
  )
  renderEffect = FluidDetentBlur.effect(radius, verticalMotion = true)
}

/**
 * Le voci di una colonna in vista, sul cilindro. Fuori dalla fascia ([inner] falso) si ritaglia via la fascia, dentro si
 * ritaglia la fascia e si ingrandisce attorno al centro. Legge la posizione qui, in disegno: girare la ruota ridisegna e
 * basta.
 */
private fun DrawScope.drawWheel(
  driver: FluidWheelDriver,
  texts: WheelLabels,
  column: FluidWheelColumn,
  bandPx: Float,
  inner: Boolean,
  ink: Color,
  isLtr: Boolean,
  unitGapPx: Float,
) {
  val position = driver.position
  val rowPx = driver.rowPx
  if (rowPx <= 0f || column.count <= 0) return
  val w = size.width
  val h = size.height
  val center = h / 2f
  val radius = fluidWheelRadius(h)
  val bandTop = center - bandPx / 2f
  val bandBottom = center + bandPx / 2f
  val anchor = texts.anchorX(w, column, isLtr, unitGapPx)
  if (inner) {
    clipRect(0f, bandTop, w, bandBottom) {
      withTransform({ scale(FluidWheelLook.Magnification, FluidWheelLook.Magnification, Offset(anchor.pivot, center)) }) {
        drawRows(driver, texts, column, position, rowPx, radius, center, anchor, inner = true, ink)
      }
      // L'unita' sta ferma nella fascia: e' della colonna, non della voce.
      val unit = texts.unit
      if (unit != null) {
        val x = if (isLtr) anchor.number + unitGapPx else anchor.number - unitGapPx - unit.size.width
        drawText(unit, color = ink, topLeft = Offset(x, center - unit.size.height / 2f))
      }
    }
  } else {
    clipRect(0f, bandTop, w, bandBottom, clipOp = ClipOp.Difference) {
      drawRows(driver, texts, column, position, rowPx, radius, center, anchor, inner = false, ink)
    }
  }
}

private fun DrawScope.drawRows(
  driver: FluidWheelDriver,
  texts: WheelLabels,
  column: FluidWheelColumn,
  position: Float,
  rowPx: Float,
  radius: Float,
  center: Float,
  anchor: WheelAnchor,
  inner: Boolean,
  ink: Color,
) {
  val count = column.count
  val reach = ceil((PI / 2.0) * radius / rowPx).toInt() + 1
  val base = floor(position).toInt()
  val enabled = column.isEnabled
  for (row in base - reach..base + reach + 1) {
    val index = if (column.cyclic) fluidDetentWrap(row, count) else row
    if (index < 0 || index >= count) continue
    val angle = fluidWheelAngle(row - position, rowPx, radius)
    if (abs(angle) >= HalfPi) continue
    // Sul cilindro: a `R·sin` dal centro, accorciata del coseno (le schermate di iOS non mostrano altra prospettiva).
    val y = center + radius * sin(angle)
    val scaleY = cos(angle)
    var alpha = if (inner) 1f else FluidWheelLook.OuterInk * fluidWheelFade(angle)
    if (enabled != null && !enabled(index)) alpha *= FluidWheelLook.DisabledInk
    if (alpha <= 0.004f) continue
    val layout = texts.row(index)
    val left = anchor.left(layout.size.width.toFloat())
    withTransform({
      scale(1f, scaleY, Offset(anchor.pivot, y))
    }) {
      drawText(layout, color = ink, topLeft = Offset(left, y - layout.size.height / 2f), alpha = alpha.coerceIn(0f, 1f))
    }
  }
  // Per le prove di riposo: quante volte una colonna e' stata disegnata (un campo, non uno stato: non ricompone).
  driver.drawnFrames++
}

/** L'angolo sul cilindro di una voce che dista [offsetRows] voci dal centro, su una ruota di raggio [radiusPx]. */
internal fun fluidWheelAngle(offsetRows: Float, rowPx: Float, radiusPx: Float): Float =
  if (radiusPx <= 0f) 0f else offsetRows * rowPx / radiusPx

/** Il raggio del cilindro di una ruota alta [heightPx]: [FluidWheelLook.RadiusFraction] dell'altezza. */
internal fun fluidWheelRadius(heightPx: Float): Float = heightPx * FluidWheelLook.RadiusFraction

/** La dissolvenza delle voci fuori dalla fascia all'[angle] dal centro: `exp(−FadePerRadian·|angolo|)`. */
internal fun fluidWheelFade(angle: Float): Float = exp(-FluidWheelLook.FadePerRadian * abs(angle))

/**
 * Quale voce sta sotto un tocco a [dyPx] dal centro (in giu' positivo), su una ruota a [position]: l'inverso del cilindro.
 * Un tocco oltre il bordo prende la voce di bordo.
 */
internal fun fluidWheelRowAt(dyPx: Float, position: Float, rowPx: Float, radiusPx: Float): Float {
  if (rowPx <= 0f || radiusPx <= 0f) return fluidDetentRound(position)
  val s = (dyPx / radiusPx).coerceIn(-1f, 1f)
  // La prospettiva sposta appena le voci verso il centro: l'inverso esatto non serve a un dito largo 40 dp.
  val angle = asin(s)
  return fluidDetentRound(position + angle * radiusPx / rowPx)
}

private const val HalfPi: Float = (PI / 2.0).toFloat()

/** Dove si allinea il testo di una colonna: il bordo sinistro di una voce larga `width`, e il perno delle scale. */
private class WheelAnchor {
  var align: Int = AlignCenter
  var x: Float = 0f
  var pivot: Float = 0f
  var number: Float = 0f

  fun left(width: Float): Float = when (align) {
    AlignStart -> x
    AlignEnd -> x - width
    else -> x - width / 2f
  }

  companion object {
    const val AlignStart = 0
    const val AlignCenter = 1
    const val AlignEnd = 2
  }
}

/**
 * Le voci misurate di una colonna, una volta sola: le piccole (fino a [DirectCapacity] voci, i minuti) in un elenco
 * diretto, le lunghe in un anello delle ultime [RingCapacity]. Misurare a ogni fotogramma allocherebbe un testo per voce.
 */
private class WheelLabels(
  private val measurer: TextMeasurer,
  private val style: TextStyle,
  private val column: FluidWheelColumn,
  unitStyle: TextStyle,
) {
  private val direct: Array<TextLayoutResult?>? = if (column.count in 1..DirectCapacity) arrayOfNulls(column.count) else null
  private val ringKeys = IntArray(RingCapacity) { -1 }
  private val ringValues = arrayOfNulls<TextLayoutResult>(RingCapacity)
  private var ringNext = 0
  private val anchor = WheelAnchor()
  private var widest = -1f

  val unit: TextLayoutResult? = column.unit?.let { measurer.measure(it, unitStyle, maxLines = 1, softWrap = false) }

  fun row(index: Int): TextLayoutResult {
    val table = direct
    if (table != null) {
      return table[index] ?: measure(index).also { table[index] = it }
    }
    for (i in 0 until RingCapacity) if (ringKeys[i] == index) return ringValues[i]!!
    val layout = measure(index)
    ringKeys[ringNext] = index
    ringValues[ringNext] = layout
    ringNext = (ringNext + 1) % RingCapacity
    return layout
  }

  private fun measure(index: Int): TextLayoutResult =
    measurer.measure(column.label(index), style, maxLines = 1, softWrap = false)

  /** Il punto d'allineamento in una colonna larga [width]: calcolato una volta per larghezza. */
  fun anchorX(width: Float, column: FluidWheelColumn, isLtr: Boolean, unitGapPx: Float): WheelAnchor {
    if (widest < 0f) {
      // La voce piu' larga fra la prima e l'ultima: con le cifre tabulari, quella con piu' cifre.
      widest = max(row(0).size.width, row(column.count - 1).size.width).toFloat()
    }
    val unitWidth = unit?.size?.width?.toFloat() ?: 0f
    val align = column.textAlign
    val start = if (isLtr) WheelAnchor.AlignStart else WheelAnchor.AlignEnd
    val end = if (isLtr) WheelAnchor.AlignEnd else WheelAnchor.AlignStart
    if (unit != null) {
      // Il numero e la sua unita' come un blocco centrato nella colonna; il numero allineato al suo bordo verso l'unita'.
      val block = widest + unitGapPx + unitWidth
      val blockStart = (width - block) / 2f
      anchor.align = end
      anchor.x = if (isLtr) blockStart + widest else width - blockStart - widest
      anchor.number = anchor.x
      anchor.pivot = anchor.x
      return anchor
    }
    when (align) {
      // A un lato, staccate dal bordo della colonna di due spazi d'unita': le colonne di un'ora si stringono al centro
      // della ruota (ore a destra, minuti a sinistra), come in iOS.
      TextAlign.Start, TextAlign.Left -> {
        anchor.align = start
        anchor.x = if (isLtr) unitGapPx * 2f else width - unitGapPx * 2f
      }
      TextAlign.End, TextAlign.Right -> {
        anchor.align = end
        anchor.x = if (isLtr) width - unitGapPx * 2f else unitGapPx * 2f
      }
      else -> {
        anchor.align = WheelAnchor.AlignCenter
        anchor.x = width / 2f
      }
    }
    anchor.number = anchor.x
    anchor.pivot = if (anchor.align == WheelAnchor.AlignCenter) width / 2f else anchor.x
    return anchor
  }

  companion object {
    const val DirectCapacity = 400
    const val RingCapacity = 48
  }
}

@Composable
private fun rememberWheelLabels(
  measurer: TextMeasurer,
  style: TextStyle,
  unitStyle: TextStyle,
  column: FluidWheelColumn,
): WheelLabels = remember(measurer, style, unitStyle, column.label, column.count, column.unit, column.textAlign) {
  WheelLabels(measurer, style, column, unitStyle)
}

// ---------------------------------------------------------------------------------------------
// Il moto
// ---------------------------------------------------------------------------------------------

/**
 * Lo stato vivo di una colonna: dove sta la ruota, chi la muove, cosa suona. La posizione e' in voci (la voce `i` al
 * centro a `i`), e su una ruota che gira all'infinito non ha limiti: l'indice e' quello modulo [count].
 */
@Stable
internal class FluidWheelDriver(
  private val scope: CoroutineScope,
  column: FluidWheelColumn,
) {
  /** La posizione disegnata, in voci. Letta solo in disegno. */
  var position by mutableFloatStateOf(column.selected.toFloat())
    private set

  /** Quante volte una colonna e' stata disegnata (per le prove di riposo): non uno stato, non ricompone. */
  var drawnFrames: Int = 0

  var rowPx: Float = 0f
    private set
  var count: Int = column.count
    private set
  var cyclic: Boolean = column.cyclic
    private set
  private var selected: Int = column.selected
  private var onSelectedChange: (Int) -> Unit = column.onSelectedChange
  private var isEnabled: ((Int) -> Boolean)? = column.isEnabled
  private var reducedMotion = false
  private var haptics: FluidHaptics? = null
  private var enabled = true

  /** Il movimento ridotto, per il disegno (niente sfocatura). */
  val reduced: Boolean get() = reducedMotion

  /** Quanto corre la ruota da sola, in voci al secondo (zero sotto il dito e a riposo): per la sfocatura del moto. */
  var motionSpeed: Float = 0f
    private set

  private val inertia = FluidDetentInertia().apply { snapTo(column.selected.toFloat()) }
  private val notches = FluidSliderNotchGate()
  private var job: Job? = null
  private val frame: (Long) -> Long = { it }

  /** Vero mentre il dito la tiene. */
  var dragging: Boolean = false
    private set

  /** Vero mentre si muove da sola (lancio, assestamento, tocco). */
  val moving: Boolean get() = job?.isActive == true

  /** L'altezza del cilindro, per l'elastico e per il tocco: la scrive il gesto. */
  var heightPx: Float = 0f

  fun update(column: FluidWheelColumn, rowPx: Float, reducedMotion: Boolean, haptics: FluidHaptics, enabled: Boolean) {
    this.rowPx = rowPx
    this.count = column.count
    this.cyclic = column.cyclic
    this.selected = column.selected
    this.onSelectedChange = column.onSelectedChange
    this.isEnabled = column.isEnabled
    this.reducedMotion = reducedMotion
    this.haptics = haptics
    this.enabled = enabled
  }

  /** L'indice della voce al centro adesso. */
  val centred: Int get() = indexOf(fluidDetentRound(position))

  private fun indexOf(row: Float): Int {
    val r = row.roundToInt()
    return if (cyclic) fluidDetentWrap(r, count) else r.coerceIn(0, max(0, count - 1))
  }

  private val lastIndex: Float get() = max(0, count - 1).toFloat()

  // --- Il dito ----------------------------------------------------------------------------------

  /** Il dito e' sceso: la ruota si ferma dov'e'. Vero se si stava muovendo. */
  fun grab(): Boolean {
    val wasMoving = moving
    job?.cancel()
    job = null
    motionSpeed = 0f
    inertia.snapTo(position)
    notches.restart(centred)
    return wasMoving
  }

  fun startDrag() {
    dragging = true
    notches.restart(centred)
  }

  /** La posizione «grezza», senza elastico, da cui si misura il trascinamento. */
  fun rawPosition(): Float {
    if (cyclic || rowPx <= 0f) return position
    val dimension = heightPx
    return when {
      position < 0f -> -inverseRubberBandTranslation(-position * rowPx, dimension) / rowPx
      position > lastIndex -> lastIndex + inverseRubberBandTranslation((position - lastIndex) * rowPx, dimension) / rowPx
      else -> position
    }
  }

  /** Il dito porta la ruota a [raw] (in voci, senza elastico). */
  fun dragTo(raw: Float, uptimeMillis: Long, speed: Float) {
    position = if (cyclic || rowPx <= 0f) raw else {
      val dimension = heightPx
      when {
        raw < 0f -> -rubberBandTranslation(-raw * rowPx, dimension) / rowPx
        raw > lastIndex -> lastIndex + rubberBandTranslation((raw - lastIndex) * rowPx, dimension) / rowPx
        else -> raw
      }
    }
    tick(uptimeMillis, speed)
  }

  /** Il dito si e' alzato con [speed] voci al secondo. */
  fun release(speed: Float) {
    dragging = false
    val lower = if (cyclic) null else 0f
    val upper = if (cyclic) null else lastIndex
    val outside = !cyclic && (position < 0f || position > lastIndex)
    if (outside) {
      run(position.coerceIn(0f, lastIndex), speed, settle = true)
      return
    }
    val target = fluidDetentLanding(position, speed, reducedMotion, lower, upper)
    if (reducedMotion) {
      run(target, 0f, settle = true, response = FluidDetentMotion.ReducedSettleResponseSeconds)
    } else {
      run(target, speed, settle = false)
    }
  }

  /** Un tocco a [dyPx] dal centro: la voce che sta li' viene al centro. */
  fun tapAt(dyPx: Float) {
    var target = fluidWheelRowAt(dyPx, position, rowPx, fluidWheelRadius(heightPx))
    if (!cyclic) target = target.coerceIn(0f, lastIndex)
    if (target == fluidDetentRound(position) && position == target) return
    run(target, 0f, settle = true, response = if (reducedMotion) FluidDetentMotion.ReducedSettleResponseSeconds else FluidDetentMotion.SettleResponseSeconds)
  }

  // --- Da fuori ---------------------------------------------------------------------------------

  /** Il chiamante dice che la voce scelta e' [index]: la ruota ci va, se non c'e' gia' e nessuno la sta muovendo. */
  fun follow(index: Int) {
    if (dragging || moving || count <= 0) return
    val wanted = index.coerceIn(0, count - 1)
    if (centred == wanted && position == fluidDetentRound(position)) return
    val target = fluidDetentNearest(position, wanted, count, cyclic)
    if (reducedMotion || rowPx <= 0f) {
      inertia.snapTo(target)
      position = target
      notches.restart(wanted)
      return
    }
    run(target, 0f, settle = true, response = FluidDetentMotion.FollowResponseSeconds, report = false)
  }

  /** TalkBack, un'azione: la voce [index] e' chiesta subito al chiamante; la ruota la segue quando arriva. */
  fun select(index: Int): Boolean {
    if (!enabled || count <= 0) return false
    val wanted = if (cyclic) fluidDetentWrap(index, count) else index.coerceIn(0, count - 1)
    val allowed = isEnabled
    if (allowed != null && !allowed(wanted)) return false
    if (wanted != selected) onSelectedChange(wanted)
    return true
  }

  fun step(delta: Int): Boolean {
    if (count <= 0) return false
    val next = selected + delta
    if (!cyclic && (next < 0 || next >= count)) return false
    return select(next)
  }

  // --- Il motore --------------------------------------------------------------------------------

  private fun run(
    target: Float,
    speed: Float,
    settle: Boolean,
    response: Double = FluidDetentMotion.SettleResponseSeconds,
    report: Boolean = true,
  ) {
    job?.cancel()
    if (settle) inertia.springTo(position, speed, target, rowPx, response) else inertia.flingTo(position, speed, target, rowPx)
    job = scope.launch {
      var last = -1L
      while (inertia.moving) {
        val now = withFrameNanos(frame)
        val dt = if (last < 0L) 0f else (now - last) / 1e9f
        last = now
        inertia.step(dt)
        // Prima della posizione: il layer che la legge trova la velocita' di questo fotogramma.
        motionSpeed = inertia.velocity
        position = inertia.position
        // Un valore cambiato da fuori (l'app, TalkBack) non vibra: il dito non ha fatto niente, come nello scrubber.
        if (report) tick(now / 1_000_000L, inertia.velocity)
      }
      motionSpeed = 0f
      position = inertia.position
      if (report) land() else notches.restart(centred)
    }
  }

  /** La ruota si e' fermata: una voce che non si puo' scegliere scivola alla piu' vicina; poi lo si dice al chiamante. */
  private suspend fun land() {
    val index = centred
    val allowed = isEnabled
    if (allowed != null && !allowed(index)) {
      val alternative = fluidWheelNearestEnabled(index, count, cyclic, allowed)
      if (alternative >= 0 && alternative != index) {
        val target = fluidDetentNearest(position, alternative, count, true).let { if (cyclic) it else alternative.toFloat() }
        inertia.springTo(position, 0f, target, rowPx, FluidDetentMotion.SettleResponseSeconds)
        var last = -1L
        while (inertia.moving) {
          val now = withFrameNanos(frame)
          val dt = if (last < 0L) 0f else (now - last) / 1e9f
          last = now
          inertia.step(dt)
          position = inertia.position
          tick(now / 1_000_000L, 0f)
        }
        position = inertia.position
      }
    }
    val landed = centred
    if (landed != selected) {
      onSelectedChange(landed)
      // Il chiamante puo' rifiutare: due fotogrammi dopo (la ricomposizione e' passata) la ruota torna alla sua voce.
      withFrameNanos(frame)
      withFrameNanos(frame)
      if (!dragging && selected != landed) {
        job = null
        follow(selected)
      }
    }
  }

  /** Un tic se la voce al centro e' cambiata, con la rete dei 55 ms. */
  private fun tick(nowMillis: Long, speed: Float) {
    if (count <= 0) return
    if (notches.land(centred, nowMillis)) {
      val event = if (abs(speed) > FluidWheelLook.FastTickRowsPerSecond) FluidHapticEvent.DragTick else FluidHapticEvent.Tick
      haptics?.play(event)
    }
  }
}

/** La voce piu' vicina a [index] che si puo' scegliere, o −1 se nessuna. A pari distanza vince quella prima. */
internal fun fluidWheelNearestEnabled(index: Int, count: Int, cyclic: Boolean, allowed: (Int) -> Boolean): Int {
  if (count <= 0) return -1
  if (allowed(index)) return index
  for (d in 1 until count) {
    val before = index - d
    val after = index + d
    val b = if (cyclic) fluidDetentWrap(before, count) else before
    val a = if (cyclic) fluidDetentWrap(after, count) else after
    if (b in 0 until count && allowed(b)) return b
    if (a in 0 until count && allowed(a)) return a
  }
  return -1
}

// ---------------------------------------------------------------------------------------------
// Il gesto e TalkBack
// ---------------------------------------------------------------------------------------------

private fun Modifier.wheelGesture(driver: FluidWheelDriver): Modifier = pointerInput(driver) {
  val tracker = VelocityTracker()
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    driver.heightPx = size.height.toFloat()
    val wasMoving = driver.grab()
    tracker.resetTracking()
    tracker.addPosition(down.uptimeMillis, down.position)
    var claimed = wasMoving
    if (claimed) down.consume()
    var travel = Offset.Zero
    var anchorY = down.position.y
    var anchorRaw = driver.rawPosition()
    var pointer = down.id
    var released = false
    try {
      while (!claimed) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return@awaitEachGesture
        if (change.isConsumed) return@awaitEachGesture
        if (!change.pressed) {
          change.consume()
          released = true
          driver.tapAt(down.position.y - size.height / 2f)
          return@awaitEachGesture
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        travel += change.positionChange()
        // Lo stesso arbitro dello slider, con gli assi scambiati: la ruota a mezzo slop in verticale, la pagina a uno
        // slop di lato.
        when (fluidSliderClaim(Offset(travel.y, travel.x), viewConfiguration.touchSlop)) {
          FluidSliderClaim.Slider -> {
            change.consume()
            claimed = true
            anchorY = change.position.y
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
            anchorY = it.position.y
            anchorRaw = driver.rawPosition()
          }
          ?: break
        if (!change.pressed) {
          tracker.addPosition(change.uptimeMillis, change.position)
          change.consume()
          break
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        val rowPx = driver.rowPx
        if (rowPx > 0f) {
          val speed = -tracker.calculateVelocity().y / rowPx
          driver.dragTo(anchorRaw + (anchorY - change.position.y) / rowPx, change.uptimeMillis, speed)
        }
        change.consume()
      }
      released = true
      val maxFling = viewConfiguration.maximumFlingVelocity
      val vy = tracker.calculateVelocity().y.coerceIn(-maxFling, maxFling)
      val rowPx = driver.rowPx
      driver.release(if (rowPx > 0f) -vy / rowPx else 0f)
    } finally {
      // Annullato a meta' (la pagina che se ne va, un altro gesto): la ruota si posa sulla voce piu' vicina.
      if (!released) driver.release(0f)
    }
  }
}

private fun Modifier.wheelSemantics(
  driver: FluidWheelDriver,
  column: FluidWheelColumn,
  enabled: Boolean,
  increaseLabel: String,
  decreaseLabel: String,
): Modifier = semantics {
  val count = column.count
  val selected = column.selected.coerceIn(0, max(0, count - 1))
  column.contentDescription?.let { contentDescription = it }
  stateDescription = column.stateDescription?.invoke(selected)
    ?: if (count > 0) column.label(selected) + (column.unit?.let { " $it" } ?: "") else ""
  progressBarRangeInfo = ProgressBarRangeInfo(
    current = selected.toFloat(),
    range = 0f..max(0, count - 1).toFloat(),
    steps = max(0, count - 2),
  )
  if (enabled && count > 0) {
    setProgress { target -> driver.select(target.roundToInt()) }
    customActions = listOf(
      CustomAccessibilityAction(increaseLabel) { driver.step(1) },
      CustomAccessibilityAction(decreaseLabel) { driver.step(-1) },
    )
  } else {
    disabled()
  }
}

/** Le colonne non piu' larghe del necessario non esistono: ma una ruota di zero voci non deve rompersi. */
internal fun fluidWheelSafeIndex(index: Int, count: Int): Int = if (count <= 0) 0 else min(max(index, 0), count - 1)
