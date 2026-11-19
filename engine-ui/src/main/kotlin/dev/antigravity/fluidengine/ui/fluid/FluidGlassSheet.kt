package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import kotlin.math.abs
import kotlin.math.min

// Il foglio di vetro con i detent (4.0, piano «iOS in Android», F5B.2): l'API che ricalca `sheet` +
// `presentationDetents` di SwiftUI, e la logica pura del suo moto. Il disegno sta in `FluidGlassModalSheet.kt`,
// che l'host chiama per ogni foglio: questo file dichiara, quello disegna.

/**
 * Un'altezza di riposo di un [FluidGlassSheet] (in SwiftUI `PresentationDetent`).
 *
 * Le altezze si risolvono sulla finestra: [Large] arriva fin sotto la barra di stato, [Medium] è circa metà dello
 * schermo, [Fraction] è una frazione di [Large], [Height] un'altezza fissa. Un'altezza oltre [Large] vale [Large].
 */
@Immutable
sealed class FluidSheetDetent {
  /** Circa metà dello schermo: il 52% della finestra, come il foglio di Apple misurato (WWDC25 323, f12203). */
  data object Medium : FluidSheetDetent()

  /** Tutta l'altezza: il foglio diventa opaco e si attacca ai bordi. È il detent di serie, come in SwiftUI. */
  data object Large : FluidSheetDetent()

  /** Una frazione dell'altezza di [Large], fra 0 e 1. */
  data class Fraction(val fraction: Float) : FluidSheetDetent()

  /** Un'altezza fissa del foglio, dal suo bordo alto al fondo dello schermo. */
  data class Height(val height: Dp) : FluidSheetDetent()
}

/**
 * Come un [FluidGlassSheet] entra e se ne va.
 *
 * [Slide] sale dal basso con la molla del foglio. [CrossFade] compare in dissolvenza sopra il contenuto, al suo posto
 * (iOS 27, `navigationTransition(.crossFade)`): Apple lo documenta ma non lo mostra in nessun video, quindi tempi e
 * curva sono quelli della molla del foglio letti come opacità, a occhio (F5B.2, 2026-11-18).
 */
enum class FluidSheetTransition { Slide, CrossFade }

/**
 * Un foglio di vetro con detent, maniglia e chiusura col dito (in SwiftUI `sheet` + `presentationDetents` +
 * `presentationDragIndicator`).
 *
 * **Vetro a mezza altezza, opaco a tutta altezza.** A un detent parziale il foglio è una lastra di vetro staccata dai
 * bordi (pochi dp di rientro) con gli angoli bassi concentrici a quelli dello schermo: la pagina si intravede attorno.
 * Salendo verso [FluidSheetDetent.Large] il rientro si chiude e un velo del colore della pagina cresce fino a opaco, in
 * continuo con l'altezza (non a scatto) e con un piccolo ritardo nel tempo, così il bianco arriva subito dopo il foglio:
 * a tutta altezza il foglio è una pagina attaccata ai bordi. È ciò che fa il foglio di iOS 26 (WWDC25 323, 6:47: vetro a
 * metà e per tutta la salita, bianco e a filo in cima).
 *
 * **I detent li decide il dito.** Il foglio segue il dito al pixel, anche fra un detent e l'altro; al rilascio va al
 * detent più vicino a dove lo porterebbe lo slancio, con una molla che supera appena (R4 B8). Sotto il detent più basso
 * il foglio scende intero e si chiude oltre una soglia o con un colpo verso il basso. Uno scorrimento verso l'alto del
 * contenuto, finché il foglio non è al detent più alto, alza il foglio invece di scorrere la lista; verso il basso,
 * quando la lista è in cima, lo abbassa. Toccare la maniglia passa al detent successivo. Il dito può riprendere il
 * foglio mentre si muove: la molla si ferma dov'è e il dito continua da lì.
 *
 * **La pagina sotto non si muove.** Il foglio di iOS 26 non fa rientrare la pagina che lo presenta (323 f12225, 284
 * f25440: a tutta altezza la pagina resta dov'era, scurita): qui la scurisce lo scrim dell'host, come per ogni modale.
 *
 * Sotto c'è [FluidGlassModalPortal] con [FluidGlassModalPresentation.Sheet]: serve un `FluidGlassModalHost` alla radice,
 * senza il quale il foglio non si apre e non va in crash. Il contenuto è una colonna alta quanto il foglio al detent in
 * cui sta, già scostata dalla barra di navigazione: una `LazyColumn` con `Modifier.weight(1f)` ci scorre dentro.
 *
 * @param detents le altezze di riposo; vuoto vale `Large`. Il foglio si apre al più basso, come in SwiftUI.
 * @param selectedDetent il detent da tenere, se l'app lo vuole decidere: cambiarlo porta il foglio lì con la molla.
 *   Null: lo decide il dito.
 * @param onSelectedDetentChange il detent in cui il foglio si è fermato dopo un gesto o un tocco sulla maniglia.
 * @param dragIndicator la maniglia in cima (36 × 5 dp). Il foglio si trascina comunque da ogni punto.
 * @param transition vedi [FluidSheetTransition].
 * @param source il tasto (o l'elemento) da cui il foglio nasce con lo zoom (4.0, F5B.3; in SwiftUI
 *   `matchedTransitionSource` sul tasto e `navigationTransition(.zoom)` sul foglio). Il vetro del foglio cresce dal
 *   riquadro della sorgente fino al suo detent sulla molla di sempre; il contenuto è dentro dal primo fotogramma, alla sua
 *   misura e sfocato, e diventa nitido a ≈300 ms; la pagina dietro si scurisce. Mentre il foglio è aperto la sorgente è
 *   «consumata» (non si disegna) e alla chiusura il vetro ci rientra. Per un tasto della barra:
 *   `rememberFluidToolbarTransitionSource(id)`; per un'altra vista: `FluidTransitionSource(id, namespace)` col
 *   `Modifier.fluidTransitionSource` sulla vista. Null (di serie), con [FluidSheetTransition.CrossFade], con le
 *   animazioni ridotte o se la sorgente all'apertura non si trova: il foglio sale dal basso come sempre.
 */
@Composable
fun FluidGlassSheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  detents: Set<FluidSheetDetent> = setOf(FluidSheetDetent.Large),
  selectedDetent: FluidSheetDetent? = null,
  onSelectedDetentChange: (FluidSheetDetent) -> Unit = {},
  dragIndicator: Boolean = true,
  transition: FluidSheetTransition = FluidSheetTransition.Slide,
  paneTitle: String? = null,
  paneTint: Color? = null,
  source: FluidTransitionSource? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val spec = FluidSheetSpec(detents.toList(), selectedDetent, dragIndicator, transition, source)
  FluidGlassModalPortal(
    visible = visible,
    onDismissRequest = onDismissRequest,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = paneTitle,
    paneTint = paneTint,
  ) {
    FluidSheetDeclaration(spec, onSelectedDetentChange)
    FluidSheetBody(content)
  }
}

/**
 * [FluidGlassSheet] che presenta **un dato** e resta intero mentre esce: la forma a `item` del portale
 * ([FluidGlassModalPortal]), con le stesse ragioni. Aperto quando [item] non è nullo.
 */
@Composable
fun <T : Any> FluidGlassSheet(
  item: T?,
  onDismissRequest: () -> Unit,
  detents: Set<FluidSheetDetent> = setOf(FluidSheetDetent.Large),
  selectedDetent: FluidSheetDetent? = null,
  onSelectedDetentChange: (FluidSheetDetent) -> Unit = {},
  dragIndicator: Boolean = true,
  transition: FluidSheetTransition = FluidSheetTransition.Slide,
  paneTitle: String? = null,
  paneTint: Color? = null,
  source: FluidTransitionSource? = null,
  content: @Composable ColumnScope.(T) -> Unit,
) {
  val spec = FluidSheetSpec(detents.toList(), selectedDetent, dragIndicator, transition, source)
  FluidGlassModalPortal(
    item = item,
    onDismissRequest = onDismissRequest,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = paneTitle,
    paneTint = paneTint,
  ) { shown ->
    FluidSheetDeclaration(spec, onSelectedDetentChange)
    FluidSheetBody { content(shown) }
  }
}

/**
 * Il contenuto di un foglio con detent: alto quanto il foglio, sopra la barra di navigazione, con i tasti alla taglia,
 * allo stile e alla larghezza di serie (F6.4: in iOS 27 un foglio non eredita `controlSize` e `buttonSizing` della barra
 * che lo apre). L'host li riporta gia' per ogni presentazione; il foglio lo dice anche da se', per il suo contenuto.
 */
@Composable
private fun ColumnScope.FluidSheetBody(content: @Composable ColumnScope.() -> Unit) {
  CompositionLocalProvider(*fluidGlassButtonDefaults()) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
      content = content,
    )
  }
}

// --- Dichiarazione: dal contenuto al foglio che lo disegna ---------------------------------------------------------

/** Ciò che un [FluidGlassSheet] chiede al foglio dell'host. Uguaglianza per valore: ricrearlo non invalida niente. */
@Immutable
internal data class FluidSheetSpec(
  val detents: List<FluidSheetDetent>,
  val selected: FluidSheetDetent?,
  val dragIndicator: Boolean,
  val transition: FluidSheetTransition,
  /** La sorgente dello zoom (F5B.3), o null. Va ricordata da chi la crea: è parte dell'uguaglianza della dichiarazione. */
  val source: FluidTransitionSource? = null,
)

/**
 * Il canale fra il contenuto di un foglio e il foglio che lo disegna (`FluidGlassModalSheet`).
 *
 * Esiste perché il portale consegna all'host solo il contenuto: un [FluidGlassSheet] non ha altro modo di dire al foglio
 * quali detent vuole senza toccare host e portale. Il foglio lo mette a disposizione del suo contenuto
 * ([LocalFluidSheetSink]); il contenuto ci scrive la sua [spec] mentre si compone, e il foglio la legge **solo** al
 * layout, nel disegno e nei gesti, mai in composizione: così la scrittura arriva prima di ogni lettura dello stesso
 * fotogramma e non c'è una scrittura all'indietro. Senza [spec] (un portale `Sheet` di oggi, `FluidConfirmSheet`) il
 * foglio è alto quanto il suo contenuto, come prima.
 */
@Stable
internal class FluidSheetSink {
  var spec: FluidSheetSpec? by mutableStateOf(null)

  /** Un campo semplice: una lambda cambia identità a ogni ricomposizione e non deve invalidare il layout. */
  var onDetentChange: (FluidSheetDetent) -> Unit = {}
}

/** Vedi [FluidSheetSink]. Null fuori da un foglio dell'host. */
internal val LocalFluidSheetSink: ProvidableCompositionLocal<FluidSheetSink?> = staticCompositionLocalOf { null }

@Composable
private fun FluidSheetDeclaration(spec: FluidSheetSpec, onDetentChange: (FluidSheetDetent) -> Unit) {
  val sink = LocalFluidSheetSink.current ?: return
  sink.onDetentChange = onDetentChange
  // Scritto senza leggerlo: uno stato con uguaglianza strutturale ignora un valore uguale.
  sink.spec = spec
}

// --- Numeri ---------------------------------------------------------------------------------------------------------

/**
 * Il rientro del foglio a un detent parziale, dai lati e dal fondo: ≈4,5 pt nel foglio di Apple (WWDC25 323 f12203,
 * 4–5 px su uno schermo largo 432 px per 402 pt; media). Scelta di gusto aperta (piano, F5B: «rientro del foglio a
 * mezza altezza»).
 */
internal val FluidSheetFloatingInset: Dp = 5.dp

/**
 * Lo spazio fra la barra di stato e il foglio a tutta altezza: ≈3 pt in iOS 26 (323 f12225: il bordo alto del foglio a
 * 71 px dal bordo dello schermo, 64 pt contro i 62 dell'area sicura; media). Qui 6 dp, a occhio (2026-11-18): le icone
 * della barra di stato di Android stanno più in basso, nel centro di una barra più corta.
 */
internal val FluidSheetLargeTopGap: Dp = 6.dp

/** [FluidSheetDetent.Medium] come frazione della finestra: 52% (323 f12203, 496 px su 946; media). */
internal const val FluidSheetMediumFraction: Float = 0.52f

/** Il foglio più basso che un detent può chiedere: sotto, la maniglia e il bordo non lasciano spazio a niente. */
internal val FluidSheetMinDetentHeight: Dp = 64.dp

/**
 * La fascia, in frazione del tratto fra il detent parziale più alto e [FluidSheetDetent.Large], in cui il rientro si
 * chiude e il foglio si attacca ai bordi: Apple apre i lati dalla metà del tratto in su (323 f12210–f12214). Metà del
 * tratto, con una curva morbida ai due capi.
 */
internal const val FluidSheetInsetBand: Float = 0.5f

/**
 * La fascia in cui il velo opaco cresce: l'ultimo decimo del tratto. Il foglio di Apple resta vetro per tutta la salita
 * e diventa bianco dopo essere arrivato (323, con t = 0 a f12203: vetro fino a 400 ms, 53% a 434, 80% a 467, 92% a 500,
 * pieno a 534; in cima a 367). Con questa fascia e il ritardo di [FluidSheetVeilLagMillis] il velo del banco, sul dito di
 * Apple, fa 4% a 367 ms, 62% a 434, 88% a 500, 93% a 534 (`sessioni/f5b-2`, scelta fra le fasce 0,1–0,5 e i ritardi
 * 0–110 ms). Resta continuo con l'altezza: un dito fermo nell'ultimo decimo lascia il velo a metà.
 */
internal const val FluidSheetOpaqueBand: Float = 0.1f

/**
 * Il ritardo con cui il velo insegue l'altezza, come costante di tempo di un inseguimento esponenziale: è ciò che porta
 * il bianco dopo l'arrivo come in Apple, e che rende morbido un velo che, a dito veloce, attraverserebbe la sua fascia in
 * due fotogrammi. Vedi [FluidSheetOpaqueBand].
 */
internal const val FluidSheetVeilLagMillis: Float = 60f

/**
 * Quanto si può tirare un foglio oltre il suo detent più alto, come asintoto dell'elastico di UIKit
 * ([rubberBandTranslation]), in frazione della finestra. A occhio (2026-11-18).
 */
internal const val FluidSheetStretchFraction: Float = 0.06f

/** Lo slancio che il rilascio proietta in avanti per scegliere il detent: 0,15 s di velocità. A occhio (2026-11-18). */
internal const val FluidSheetProjectionSeconds: Float = 0.15f

/** Quanto va spinto in giù il foglio, sotto il detent più basso, perché il rilascio lo chiuda invece di riportarlo su. */
internal val FluidSheetDismissTravel: Dp = 110.dp

/** O quanto in fretta, in dp al secondo: un colpo verso il basso vicino al detent più basso lo chiude comunque. */
internal val FluidSheetDismissVelocity: Dp = 900.dp

/**
 * La molla di un foglio che va a un detent: smorzamento 0,8 e rigidezza 290, cioè 95% a 200 ms, ≈1,5% di superamento
 * con la punta a ≈310 ms, entro mezzo punto da ≈420. Tarata sul foglio di Apple che sale dal tocco (WWDC25 219,
 * f7362–f7374: 60% a 100 ms, 88% a 167, 95% a 200, superamento appena visibile, fermo a ≈400 ms; R4 B8 dice ≈330 ms a
 * occhio; fiducia media, la clip è un render). Apple parte più svelto (23% a 33 ms contro 12): il tocco gli dà una
 * spinta che una molla da ferma non ha. È quasi la
 * molla con cui l'host porta in scena il foglio (`FluidMotion.PopoverOpenY`, 0,74 e 260): entrare e cambiare detent si
 * muovono allo stesso modo. Da spostare in `FluidMotion` quando quel file si potrà toccare (F5B.2 non lo ha).
 */
internal val FluidSheetDetentSpring: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 290f, visibilityThreshold = 0.5f)

/**
 * La molla di un foglio che scende a un detent più basso: la stessa rigidezza, smorzamento critico. Il foglio di Apple che
 * si richiude non rimbalza sotto il detent, ci arriva da sopra (WWDC25 219, f7422–f7434: 35% a 100 ms, 70% a 167, 88% a
 * 233, 97% a 300, fermo a ≈400); questa fa 51%, 78%, 91%, 96%, 99% a 400 ms. Con la molla della salita il foglio
 * scendeva più svelto e superava il detent dell'1,5%. Fiducia media (un render).
 */
internal val FluidSheetDetentSpringDown: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 290f, visibilityThreshold = 0.5f)

/**
 * Il chiarore del dito su un foglio premuto, al massimo: un velo bianco al 9% sul chiaro (al 6% sullo scuro), largo
 * quanto il foglio attorno al dito. Il foglio di Apple si schiarisce sotto il dito prima di muoversi (323 f12200–f12204:
 * +21 di luminanza in 130 ms; 219 f7357–f7362). È un sostituto: la luce del dito vera del materiale
 * (`GlassTouchLight`) non arriva a `glassSurfaceInked`. A occhio (2026-11-18).
 */
internal const val FluidSheetPressGlowLight: Float = 0.09f
internal const val FluidSheetPressGlowDark: Float = 0.06f

// --- Logica pura ----------------------------------------------------------------------------------------------------

/** L'altezza di [FluidSheetDetent.Large]: la finestra meno la barra di stato e [FluidSheetLargeTopGap]. */
internal fun fluidSheetLargeHeightPx(windowHeightPx: Float, topInsetPx: Float, density: Float): Float =
  (windowHeightPx - topInsetPx - FluidSheetLargeTopGap.value * density).coerceAtLeast(0f)

/** L'altezza di un detent, in pixel, fra [FluidSheetMinDetentHeight] e [largePx]. */
internal fun fluidSheetDetentHeightPx(
  detent: FluidSheetDetent,
  windowHeightPx: Float,
  largePx: Float,
  density: Float,
): Float {
  val raw = when (detent) {
    FluidSheetDetent.Large -> largePx
    FluidSheetDetent.Medium -> windowHeightPx * FluidSheetMediumFraction
    is FluidSheetDetent.Fraction -> largePx * detent.fraction.finiteOr(1f).coerceIn(0f, 1f)
    is FluidSheetDetent.Height -> detent.height.value.finiteOr(0f) * density
  }
  val floor = min(FluidSheetMinDetentHeight.value * density, largePx)
  return raw.coerceIn(floor, largePx)
}

/**
 * I detent di un foglio, risolti in pixel e messi in ordine dal più basso. Due detent che cadono alla stessa altezza
 * (entro un pixel) sono uno solo: il primo della lista che li chiedeva.
 */
@Immutable
internal class FluidSheetStops(val detents: List<FluidSheetDetent>, val heights: FloatArray) {
  val lowest: Float get() = heights.first()
  val highest: Float get() = heights.last()

  /** L'indice di [detent]; -1 se non c'è (o se un altro detent alla stessa altezza ha preso il suo posto). */
  fun indexOf(detent: FluidSheetDetent?): Int = if (detent == null) -1 else detents.indexOf(detent)
}

internal fun fluidSheetStops(
  detents: Collection<FluidSheetDetent>,
  windowHeightPx: Float,
  topInsetPx: Float,
  density: Float,
): FluidSheetStops {
  val asked = detents.ifEmpty { listOf(FluidSheetDetent.Large) }
  val large = fluidSheetLargeHeightPx(windowHeightPx, topInsetPx, density)
  val resolved = asked.map { it to fluidSheetDetentHeightPx(it, windowHeightPx, large, density) }.sortedBy { it.second }
  val kept = ArrayList<Pair<FluidSheetDetent, Float>>(resolved.size)
  for (pair in resolved) {
    if (kept.isEmpty() || abs(kept.last().second - pair.second) > 1f) kept += pair
  }
  return FluidSheetStops(kept.map { it.first }, FloatArray(kept.size) { kept[it].second })
}

/**
 * L'altezza visibile del foglio per una posizione del dito [positionPx] (l'altezza che il dito chiede): uguale fra il
 * detent più basso e il più alto; oltre il più alto si allunga con l'elastico di UIKit; sotto il più basso resta al più
 * basso, perché lì il foglio scende intero ([fluidSheetSinkPx]).
 */
internal fun fluidSheetVisibleHeight(positionPx: Float, stops: FluidSheetStops, windowHeightPx: Float): Float = when {
  positionPx > stops.highest ->
    stops.highest + rubberBandTranslation(positionPx - stops.highest, windowHeightPx * FluidSheetStretchFraction)
  positionPx < stops.lowest -> stops.lowest
  else -> positionPx
}

/** Quanto il foglio è sceso intero sotto il detent più basso (in pixel, verso il basso). */
internal fun fluidSheetSinkPx(positionPx: Float, stops: FluidSheetStops): Float = (stops.lowest - positionPx).coerceAtLeast(0f)

/** Il detent a cui andare al rilascio, o [FluidSheetDismiss] per chiudere. */
internal const val FluidSheetDismiss: Int = -1

/**
 * Dove va il foglio quando il dito lo lascia in [positionPx] con [velocityUpPxPerSecond] (positiva verso l'alto).
 *
 * Si chiude se è sceso oltre [FluidSheetDismissTravel] sotto il detent più basso, o con un colpo verso il basso più
 * veloce di [FluidSheetDismissVelocity] partito dalla metà bassa del primo tratto (un colpo dalla cima di un foglio a
 * due detent lo porta al detent più basso, non fuori). Altrimenti va al detent più vicino a dove lo slancio lo porterebbe
 * in [FluidSheetProjectionSeconds].
 */
internal fun fluidSheetSettleTarget(
  stops: FluidSheetStops,
  positionPx: Float,
  velocityUpPxPerSecond: Float,
  density: Float,
): Int {
  val heights = stops.heights
  val velocity = velocityUpPxPerSecond.finiteOr(0f)
  val travel = FluidSheetDismissTravel.value * density
  val fling = FluidSheetDismissVelocity.value * density
  if (positionPx < stops.lowest - travel) return FluidSheetDismiss
  val firstSpan = if (heights.size > 1) heights[1] - heights[0] else travel * 2f
  if (velocity <= -fling && positionPx <= stops.lowest + firstSpan / 2f) return FluidSheetDismiss
  val projected = positionPx + velocity * FluidSheetProjectionSeconds
  var best = 0
  var bestDistance = Float.MAX_VALUE
  for (i in heights.indices) {
    val distance = abs(heights[i] - projected)
    if (distance < bestDistance) {
      bestDistance = distance
      best = i
    }
  }
  return best
}

/**
 * Quanto il foglio è «grande» alla sua altezza visibile, da 0 (vetro rientrato) a 1 (in cima), nella fascia alta [band]
 * del tratto che va dal detent parziale più alto fino a [FluidSheetDetent.Large], con una curva morbida ai due capi: in
 * continuo con l'altezza, come Alessio chiede, e non a scatto all'arrivo. Il rientro usa [FluidSheetInsetBand], il velo
 * [FluidSheetOpaqueBand] (e poi il suo ritardo, [fluidSheetVeilStep]). Un foglio senza detent parziali conta da metà di
 * [largePx]. Un foglio che non arriva a [largePx] resta vetro (o quasi) a ogni altezza.
 */
internal fun fluidSheetLargeness(
  visibleHeightPx: Float,
  stops: FluidSheetStops,
  largePx: Float,
  band: Float = FluidSheetInsetBand,
): Float {
  if (largePx <= 0f) return 0f
  var lower = -1f
  for (h in stops.heights) if (h < largePx - 1f && h > lower) lower = h
  if (lower < 0f) lower = largePx * 0.5f
  val span = (largePx - lower) * band.coerceIn(0f, 1f)
  if (span <= 0.5f) return if (visibleHeightPx >= largePx - 0.5f) 1f else 0f
  val t = ((visibleHeightPx - (largePx - span)) / span).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * Un passo del velo che insegue il suo bersaglio [target] (la «grandezza» nella fascia del velo) dopo [dtMillis]: un
 * inseguimento esponenziale con costante [FluidSheetVeilLagMillis]. Non supera mai il bersaglio e non torna indietro.
 */
internal fun fluidSheetVeilStep(current: Float, target: Float, dtMillis: Float): Float {
  if (!current.isFinite() || dtMillis <= 0f) return if (current.isFinite()) current else target
  val k = 1f - kotlin.math.exp(-dtMillis / FluidSheetVeilLagMillis)
  return current + (target - current) * k
}

/** Il rientro dai lati e dal fondo a «grandezza» [largeness]: pieno a 0, a filo a 1. */
internal fun fluidSheetInsetPx(largeness: Float, density: Float): Float =
  FluidSheetFloatingInset.value * density * (1f - largeness.coerceIn(0f, 1f))

/**
 * Il raggio degli angoli bassi del foglio staccato, con la regola concentrica di F3.5 ([concentricCornerRadii]): lo
 * schermo è il contenitore, i suoi angoli quelli del display ([displayCornerPx], 0 dove non ce ne sono), il foglio sta a
 * [insetPx] dal fondo e dai lati. Un foglio più stretto della finestra (un tablet: sta in mezzo) è lontano dagli angoli
 * dello schermo e prende il raggio di sopra, [topRadiusPx]; così anche uno schermo ad angoli vivi.
 *
 * Il raggio non segue la «grandezza» del foglio: a filo dei bordi gli angoli dello schermo coprono quelli del foglio, che
 * sono più stretti, e un raggio che cambiasse a ogni fotogramma rifarebbe il vetro a ogni fotogramma.
 */
internal fun fluidSheetBottomRadiusPx(
  windowSize: Size,
  sheetWidthPx: Float,
  insetPx: Float,
  displayCornerPx: Float,
  topRadiusPx: Float,
  minimumPx: Float,
): Float {
  val spansWidth = sheetWidthPx >= windowSize.width - 2f * insetPx - 1f
  if (!spansWidth || displayCornerPx <= 0f) return topRadiusPx
  val piece = Rect(insetPx, 0f, windowSize.width - insetPx, windowSize.height - insetPx)
  val container = FluidCornerRadii(topLeft = 0f, topRight = 0f, bottomRight = displayCornerPx, bottomLeft = displayCornerPx)
  return concentricCornerRadii(container, windowSize, piece, minimumPx).bottomLeft
}

// --- Lo zoom dalla sorgente (F5B.3) ---------------------------------------------------------------------------------

/**
 * La sfocatura del contenuto di un foglio che nasce dal tasto, al massimo (σ): il contenuto è dentro dal primo fotogramma,
 * illeggibile (284 f25411–f25414: le righe della lista sono macchie). A occhio sulla clip, 2026-11-18.
 */
internal val FluidSheetZoomBlur: Dp = 6.dp

/** Quando il contenuto di un foglio nato dal tasto è nitido: ≈300 ms dall'apertura (284 f25417, 14:08,08; R7 32). */
internal const val FluidSheetZoomSharpMillis: Float = 300f

/**
 * La molla con cui il vetro di un foglio nato dal tasto cresce fino al suo detent: smorzamento critico, risposta 0,374 s
 * (rigidezza 282). Misurata sul foglio di Apple (WWDC25 284, f25409–f25420, `sessioni/f5b-3/strumenti/misura_284.py`):
 * la cima e i lati percorrono 33% a 67 ms, 49% a 100, 65% a 133, 78% a 167, 92% a 234, 97% a 300 e arrivano senza
 * superare; questa fa 31, 50, 65, 77, 90, 96. Apple parte più svelto nel primo fotogramma (22% a 33 ms contro 11: il
 * tocco gli dà una spinta). La molla con cui l'host porta in scena il foglio (`FluidMotion.PopoverOpenY`) supera del 3%
 * ed è più avanti a metà strada (74% a 133 ms): per lo zoom il foglio usa questa in apertura, e quella dell'host in
 * chiusura (quando il layer se ne va la decide l'host).
 */
internal val FluidSheetZoomSpring: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 282f, visibilityThreshold = 0.001f)

/**
 * La fascia del viaggio dello zoom in cui il velo di un foglio nato dal tasto cresce: dal 45% all'85%. Il foglio di Apple
 * diventa bianco **mentre cresce**, non dopo: vetro fino a ≈100 ms (284 f25411, viaggio ≈50%), metà a 133 (f25412, 65%),
 * quasi pieno a 167 (f25413, 78%). La fascia del foglio trascinato (l'ultimo decimo dell'altezza, [FluidSheetOpaqueBand],
 * tarata dove Apple resta vetro fino all'arrivo) qui lo farebbe bianco solo a ≈400 ms.
 */
internal const val FluidSheetZoomVeilFrom: Float = 0.45f
internal const val FluidSheetZoomVeilTo: Float = 0.85f

/**
 * Il velo di un foglio che nasce dal tasto a viaggio [travel] dello zoom: una S nella fascia [FluidSheetZoomVeilFrom]–
 * [FluidSheetZoomVeilTo], fino a [rest], il velo che il foglio ha al suo detent (pieno a `Large`, zero a un detent
 * parziale, che resta vetro). A viaggio finito vale [rest]: nessun salto quando il foglio torna a quello di sempre.
 */
internal fun fluidSheetZoomVeil(travel: Float, rest: Float): Float {
  val t = ((travel.finiteOr(1f) - FluidSheetZoomVeilFrom) / (FluidSheetZoomVeilTo - FluidSheetZoomVeilFrom)).coerceIn(0f, 1f)
  return rest.finiteOr(0f).coerceIn(0f, 1f) * t * t * (3f - 2f * t)
}

/**
 * Il riquadro del vetro di un foglio che nasce dalla sorgente [source] e arriva in [sheet], a viaggio [slide] della molla
 * con cui l'host porta in scena il foglio. In larghezza non va mai oltre il foglio (la molla supera di qualche per cento, e
 * il vetro uscirebbe dai lati); in altezza supera in cima come il foglio che sale dal basso, mai sotto il suo fondo.
 */
internal fun fluidSheetZoomBounds(source: Rect, sheet: Rect, slide: Float): Rect {
  val s = slide.finiteOr(1f).coerceAtLeast(0f)
  val across = s.coerceAtMost(1f)
  fun l(a: Float, b: Float, f: Float) = a + (b - a) * f
  val left = l(source.left, sheet.left, across)
  val right = l(source.right, sheet.right, across)
  val top = l(source.top, sheet.top, s)
  val bottom = l(source.bottom, sheet.bottom, across)
  return Rect(left, top, kotlin.math.max(left, right), kotlin.math.max(top, bottom))
}

/**
 * Quanto è sfocato il contenuto di un foglio che nasce dal tasto, da 0 (nitido) a 1 ([FluidSheetZoomBlur]): il più
 * sfocato fra l'orologio dell'apertura (pieno al primo fotogramma, nitido a [FluidSheetZoomSharpMillis]) e la sagoma
 * (nitido quando il vetro è arrivato, sfocato man mano che rientra nella sorgente alla chiusura).
 */
internal fun fluidSheetZoomBlurFraction(elapsedMillis: Float, slide: Float): Float {
  fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
  }
  val byTime = 1f - smooth(elapsedMillis.finiteOr(FluidSheetZoomSharpMillis) / FluidSheetZoomSharpMillis)
  val byShape = 1f - smooth((slide.finiteOr(1f) - 0.4f) / 0.6f)
  return kotlin.math.max(byTime, byShape).coerceIn(0f, 1f)
}

/** L'opacità di un foglio che entra in dissolvenza ([FluidSheetTransition.CrossFade]) a viaggio [slide] della molla. */
internal fun fluidSheetCrossFadeAlpha(slide: Float): Float = slide.finiteOr(0f).coerceIn(0f, 1f)

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
