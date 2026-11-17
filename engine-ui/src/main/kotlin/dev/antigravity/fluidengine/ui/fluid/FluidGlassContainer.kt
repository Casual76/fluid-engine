/*
 * Il piano flottante (4.0, piano «iOS in Android», F3.2): `GlassEffectContainer` di SwiftUI e i suoi modificatori.
 *
 * Chi scrive l'app dice dove sta un vetro, che identità ha e come vuole comparire; il contenitore decide chi si fonde,
 * chi nasce da chi, chi compare per lente, e anima ogni cambio da solo. La logica è quella pura di `gf/GlassPlane.kt`
 * (F3.1); qui c'è il passaggio a Compose: il registro dei pezzi, le catture, il palco e la sonda unica.
 *
 * Come disegna, in breve (il perché nei KDoc):
 *  - **il vetro dei pezzi lo disegna il contenitore**, mai il pezzo: dietro il contenuto, una superficie per grappolo a
 *    riposo («ogni grappolo è un campo con la sua cattura», 3.7) e una per campo del viaggio durante un cambio (il
 *    palco). I pezzi disegnano solo il loro contenuto;
 *  - **riposo e palco non si sovrappongono mai**: chi porta il vetro lo decide la composizione, nella stessa passata per
 *    tutte le superfici; il palco parte al primo fotogramma dopo quello che compone il cambio (il pezzo nuovo è misurato
 *    lì), dalla geometria che il riposo stava mostrando, compresa la pressione del tasto toccato. A schermo fermo sono due
 *    periodi del pannello (a 30 Hz, 33–67 ms) più il lavoro di quei fotogrammi: lo misura
 *    `FluidGlassContainerDiagnostics.lastStart`;
 *  - **una sonda sola** sul grappolo più grande decide lato e inchiostro per tutti (adattamento uniforme, F3-g).
 */
package dev.antigravity.fluidengine.ui.fluid

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusion
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDefault
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDp
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneNeck
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneShape
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneTouch
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurfaceInked
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneMotion
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneNeckMs
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.field.GlassJourneyFingerMin
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourneys
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneCluster
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneClusterId
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneField
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFlightId
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFrame
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePieceFrame
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneRegistry
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneUnionChange
import dev.antigravity.fluidengine.ui.glass.field.GlassTouchLight
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmFamily
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmUp
import dev.antigravity.fluidengine.ui.glass.field.glassFieldRenders
import dev.antigravity.fluidengine.ui.glass.field.glassJourneySwellDp
import dev.antigravity.fluidengine.ui.glass.field.glassJourneySwollen
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import dev.antigravity.fluidengine.ui.glass.field.glassProbe
import dev.antigravity.fluidengine.ui.glass.field.glassProbeAvailable
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp
import dev.antigravity.fluidengine.ui.glass.field.rememberGlassProbeState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.channels.Channel

// ================================================================================================================
// L'API pubblica
// ================================================================================================================

/**
 * Come il contenitore anima i cambi dei suoi vetri. In SwiftUI è l'animazione della transazione (`withAnimation`); qui
 * non si chiama niente: **il contenitore anima da solo** ogni cambio, e questo dice come.
 */
@Immutable
sealed class FluidGlassAnimation {

  /**
   * Di serie: la molla [FluidMotion.standard] stirata nei bilanci del piano, [FluidMotion.PlaneContextMillis] per un
   * cambio di contesto, [FluidMotion.PlaneAppearMillis] per una comparsa e [FluidMotion.PlaneDisappearMillis] per una
   * scomparsa. Un cambio a metà viaggio riparte da dove si trova, con la velocità che ha.
   */
  data object Default : FluidGlassAnimation()

  /**
   * Nessun viaggio: i vetri vanno al posto nuovo nel fotogramma dopo il cambio, e il contenuto che si è spostato o
   * cresciuto con loro (lo stesso fotogramma per vetro e contenuto). Anche con il movimento ridotto. Se arriva mentre un
   * vetro viaggia, il viaggio finisce lì, al posto nuovo.
   */
  data object None : FluidGlassAnimation()

  /**
   * La spec dell'app per ogni lato e raggio di ogni vetro, col suo tempo (i bilanci di serie non contano). Una molla
   * conserva la velocità di un viaggio interrotto; una curva a durata no, e alla ripartenza riparte da ferma.
   */
  class Custom(val spec: FiniteAnimationSpec<Float>) : FluidGlassAnimation() {
    override fun equals(other: Any?): Boolean = this === other || other is Custom && other.spec == spec
    override fun hashCode(): Int = spec.hashCode()
    override fun toString(): String = "FluidGlassAnimation.Custom($spec)"
  }
}

/** I valori di serie di [FluidGlassContainer]. */
object FluidGlassContainerDefaults {
  /**
   * Fin dove due vetri dello stesso contenitore sono **un grappolo** (una cattura sola, un campo fuso in viaggio), fin
   * dove un vetro nuovo **nasce dal vicino** invece di comparire per lente, e il raggio di fusione del loro collo.
   *
   * **Scelta di Alessio, 2026-11-16** (tavola «distanza di fusione» di F3.3: stretta 8 / media 16 / larga 24): la media,
   * 16 dp. Copre i due vetri di un gruppo (8 dp, [FluidMetrics.GlassSpacing]) e due gruppi vicini (12 dp,
   * [FluidMetrics.GroupSpacing]) con un margine, perché l'arrotondamento al pixel non decida chi si fonde. A riposo due
   * vetri distinti restano separati del tutto; per un blocco di tasti che è **una cosa sola**, [fluidGlassUnion]. Apple
   * non la documenta (i suoi esempi usano 20 e 40 pt).
   */
  val Spacing: Dp = PhysicsPlaneFusionDp.dp
}

/**
 * Lo spazio dei nomi delle identità dei vetri: `@Namespace` di SwiftUI. Due `id` uguali in due spazi diversi sono due
 * vetri diversi; lo stesso `id` nello stesso spazio, prima e dopo un cambio, è **lo stesso vetro** che si sposta.
 */
@Stable
class FluidGlassNamespace internal constructor() {
  override fun toString(): String = "FluidGlassNamespace@${Integer.toHexString(System.identityHashCode(this))}"
}

/** Uno spazio dei nomi che vive quanto il punto della composizione che lo chiede. */
@Composable
fun rememberFluidGlassNamespace(): FluidGlassNamespace = remember { FluidGlassNamespace() }

/** Come un vetro che arriva o se ne va entra nel cambio: `glassEffectTransition` di SwiftUI. */
enum class FluidGlassTransition {
  /**
   * Di serie: un vetro nuovo vicino a un altro (entro lo `spacing` del contenitore) **nasce da lui** e se ne stacca; uno
   * che se ne va vicino a un altro **vi confluisce**; lontano compare o scompare per lente.
   */
  MatchedGeometry,

  /** Compare e scompare sempre per lente, al suo posto: la rifrazione sale, il contorno si stringe da poco più largo. */
  Materialize,

  /** Nessuna transizione del vetro: c'è o non c'è. */
  Identity,

  /**
   * Senza predecessore compare e scompare per lente anche accanto a un altro vetro. Con lo stesso `id` resta invece
   * lo stesso vetro: l'indietro fra due pagine non deve morire e rinascere a ogni cambio della barra (R7, riga 21).
   */
  MaterializeWhenAlone,
}

/**
 * L'identità del vetro che **segue** questo modificatore nella stessa catena (`glassEffectID` di SwiftUI).
 *
 * **L'ordine è contratto**: `fluidGlassId`, [fluidGlassUnion] e [fluidGlassTransition] valgono per il primo
 * [fluidGlass] alla loro destra, e per nessun altro (né i figli, né un vetro più a sinistra). Scritti dopo il vetro non
 * hanno effetto, e in una build di debug lo dice un avviso nel log (`FluidGlass`):
 *
 * ```
 * Modifier.fluidGlassId("play", ns).fluidGlass()   // giusto: il vetro è "play"
 * Modifier.fluidGlass().fluidGlassId("play", ns)   // l'id non conta, avviso in debug
 * ```
 *
 * Contano dentro un [FluidGlassContainer]; fuori non c'è niente da accoppiare.
 */
fun Modifier.fluidGlassId(id: Any, namespace: FluidGlassNamespace): Modifier =
  this then FluidGlassIdElement(id, namespace)

/**
 * Unisce il vetro che segue in **una sagoma sola** con gli altri della stessa unione (`glassEffectUnion` di SwiftUI):
 * fusi a riposo in un campo, anche oltre lo `spacing` del contenitore (mai oltre otto pezzi). Stesso ordine di
 * [fluidGlassId]: vale per il [fluidGlass] alla sua destra.
 */
fun Modifier.fluidGlassUnion(id: Any, namespace: FluidGlassNamespace): Modifier =
  this then FluidGlassUnionElement(id, namespace)

/**
 * Come il vetro che segue compare e scompare (`glassEffectTransition` di SwiftUI). Stesso ordine di [fluidGlassId]:
 * vale per il [fluidGlass] alla sua destra.
 */
fun Modifier.fluidGlassTransition(transition: FluidGlassTransition): Modifier =
  this then FluidGlassTransitionElement(transition)

/**
 * Il punto del pezzo che porta il contenuto mentre il vetro cambia larghezza. Il centro conserva il volo del piano;
 * un bordo tiene ferme le icone di una capsula che cresce attorno a loro, senza stirarle (R7: chi resta resta nitido).
 * I bordi seguono la direzione del layout; in verticale resta il centro. Il rimbalzo del bordo resta nel contenuto.
 */
enum class FluidGlassContentAnchor { Center, Start, End }

/**
 * Ancora il contenuto al bordo del vetro che segue, invece che al suo centro. Stesso ordine di [fluidGlassId];
 * fuori da un [FluidGlassContainer] non cambia il disegno. Di serie [FluidGlassContentAnchor.Center].
 */
fun Modifier.fluidGlassContentAnchor(anchor: FluidGlassContentAnchor): Modifier =
  this then FluidGlassContentAnchorElement(anchor)

/**
 * Dichiara ciò che porta il vetro che segue (F4A.5b): quando la chiave cambia e il vetro resta della sua misura, il piano lo
 * fa **respirare** come Apple fa col tondo di una barra che cambia simbolo (`FluidMotion.PlaneContentBreath`). Stesso
 * ordine di [fluidGlassId]. Interno: lo usa la barra dichiarata, che sa quali facce porta ogni vetro.
 */
internal fun Modifier.fluidGlassContentKey(key: Any?): Modifier =
  if (key == null) this else this then FluidGlassContentKeyElement(key)

/**
 * Il contenitore del piano flottante: `GlassEffectContainer(spacing:)` di SwiftUI.
 *
 * Dentro, ogni [fluidGlass] (e ogni `glassControlSurface` dell'engine, i tasti di vetro compresi) è un **pezzo**: il
 * contenitore ne disegna il vetro dietro al contenuto, fonde in una cattura sola i pezzi vicini, e quando lo stato
 * cambia porta i vetri di prima su quelli di adesso da solo. L'app scrive solo lo stato, con un `if`:
 *
 * ```
 * val ns = rememberFluidGlassNamespace()
 * FluidGlassContainer {
 *   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
 *     Icon(…, Modifier.fluidGlassId("edit", ns).fluidGlass())
 *     if (editing) Icon(…, Modifier.fluidGlassId("done", ns).fluidGlass())   // nasce da "edit"
 *   }
 * }
 * ```
 *
 * **Chi nasce da chi.** Lo stesso `id` prima e dopo è lo stesso vetro che si sposta e cambia forma; un `id` nuovo entro
 * [spacing] da un vetro di prima nasce da lui; più lontano compare per lente; senza `id` i vetri si accoppiano per lato e
 * per posizione, e può sbagliare: date gli `id`.
 *
 * **Il contenuto è già al suo posto d'arrivo** e non si stira mai, ma **si vede solo dentro il suo vetro**: quello di un
 * vetro che nasce o compare arriva sfocato quando il vetro l'ha raggiunto e si fa nitido per ultimo, quello di un vetro
 * che se ne va si sfoca e svanisce dov'era mentre il bordo lo copre, quello di un vetro che resta viaggia nitido con lui.
 *
 * **Il lato lo decide il contenitore** (adattamento uniforme): una sonda sola, sotto il grappolo più grande, sceglie
 * chiaro o scuro per tutti i vetri, che girano insieme nello stesso fotogramma. Ed è il **patto dell'inchiostro**: il
 * contenuto dentro il contenitore riceve l'inchiostro del lato (`LocalContentColor`, lo schema di colori del lato e
 * [LocalFluidGlassAppearance]) e, al giro, svanisce e risale con lui. Un `fluidGlass` fuori da un contenitore non gira:
 * il vetro non sa di che colore è ciò che gli sta sopra. Per un vetro solo che gira, un contenitore con un pezzo solo.
 *
 * Il contenuto è posato come in un `Box` (in alto all'inizio, il contenitore grande quanto il più grande): di solito è
 * una `Row` o una `Column`. Di serie il contenuto non riceve i vincoli minimi del contenitore, come in un `Box`; con
 * [propagateMinConstraints] li riceve, ed è ciò che serve a **un pezzo che sta da solo** nel suo contenitore (F3.2e).
 *
 * **I pannelli** (F3.2e). Un vetro che porta del contenuto, con il ruolo, la tinta e l'ottica di un `glassSurface` (una
 * card, un dock, una barra di scrittura), è un pezzo [fluidGlassPanel]. Un vetro dentro il contenuto di un altro pezzo
 * è suo **fratello** nel piano, e si fonde con lui; dentro un [FluidGlassNested] (di solito il contenuto di un pannello)
 * è invece **vetro annidato**: non è un pezzo, campiona il materiale del pannello che lo porta e ne prende il lato.
 *
 * **Il viaggio esce dal contenitore** (giro di correzione di F3). Il superamento di chi nasce e la spinta di chi accoglie
 * sono quelli di Apple anche in un contenitore che fascia la sua riga (come qui sopra, o in una barra): il palco disegna
 * oltre il riquadro del contenitore, fino al bordo della **finestra**, e non oltre. Non serve dare al contenitore un
 * riquadro più grande del suo contenuto; serve che un genitore non lo ritagli (un `clip`, una lista che ritaglia il suo
 * riquadro): lì il rimbalzo si taglia col resto.
 *
 * **Quando cambia il riquadro e non lo stato** (giro di correzione di F3): se il contenitore riceve vincoli nuovi dal
 * genitore (una rotazione senza ricreazione, lo schermo diviso, la tastiera, una riga che si allarga attorno a lui) o una
 * densità nuova, e i pezzi sono gli stessi, i vetri **seguono il riquadro senza viaggio e senza luce**, nel fotogramma
 * dopo, col loro contenuto. Un vetro in viaggio arriva lì. Un pezzo che entra o esce nello stesso fotogramma viaggia come
 * sempre.
 *
 * **Il tocco**: il tasto premuto che fa partire un viaggio lo comincia premuto, e si rilascia in volo con la molla della
 * pressione. Un vetro che nasce è già al suo posto d'arrivo nel layout: si tocca e c'è nella semantica anche nei ~230 ms
 * in cui il suo contenuto non si vede ancora (come ogni animazione di layout di Compose; da rivedere coi controlli, F6).
 *
 * Limiti dichiarati: una forma che il campo non sa rendere (un `Outline` generico) resta un vetro a sé, fuori dal piano;
 * un contenuto che scorre **dentro** il contenitore è un cambio a ogni fotogramma (il contenitore va intorno alla barra,
 * non alla lista). Sotto Android 13 e con il vetro di prima (`FluidGlassModel.Classic`) ogni pezzo disegna il proprio
 * vetro di sempre, senza viaggi.
 *
 * @param spacing fin dove due vetri sono un grappolo e un vetro nuovo nasce dal vicino: [FluidGlassContainerDefaults.Spacing].
 * @param animation come si animano i cambi: [FluidGlassAnimation.Default]. Con [FluidGlassAnimation.None], il movimento
 *   ridotto del tema o la scala delle animazioni di sistema a zero non c'è viaggio, anche se arrivano a viaggio avviato.
 * @param propagateMinConstraints come in `Box`: vero, il contenuto riceve i vincoli minimi che il contenitore riceve dal
 *   genitore. Serve a un pezzo solo nel suo contenitore, che riceve il `modifier` dell'app sul contenitore: senza, un
 *   `Modifier.weight` o una misura imposta dal genitore allargherebbero il contenitore e non il vetro. Falso di serie,
 *   perché in un contenitore con più pezzi cambierebbe il layout di ciò che ci sta dentro (una `Row` costretta alla
 *   larghezza del contenitore sposta i pezzi di un `Arrangement.Center`).
 */
@Composable
fun FluidGlassContainer(
  modifier: Modifier = Modifier,
  spacing: Dp = FluidGlassContainerDefaults.Spacing,
  animation: FluidGlassAnimation = FluidGlassAnimation.Default,
  propagateMinConstraints: Boolean = false,
  content: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val state = remember { FluidGlassContainerState() }
  // La diagnostica del «giro» (spenta di serie: `log.tag.FluidGlassFrame`, e gli esperimenti della frequenza alta al cambio
  // di stato `FluidGlassHighRate` / `FluidGlassWindowRate`): per misurare la partenza sul tablet.
  GlassFrameDiagnostics.composed(LocalView.current, state)
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // La manopola delle prove (S3): una delle tre distanze della tavola di F3.3 al posto di quella dell'app.
  val preview = FluidGlassContainerDiagnostics.fusionPreview?.fusion
  val fusion = preview ?: PhysicsPlaneFusionDefault
  state.configure(
    density = density,
    layoutDirection = layoutDirection,
    spacingPx = with(density) { (if (preview != null) preview.dp.dp else spacing).coerceAtLeast(0.dp).toPx() },
    motion = if (reducedMotion) null else animation.planeMotion(joinStill = fusion.restFused),
    restFused = fusion.restFused,
  )
  // Il contenuto dei pezzi si registra in layer che il contenitore tiene anche dopo che un pezzo se n'è andato.
  state.graphics = LocalGraphicsContext.current

  // Il contenitore disegna i vetri solo dove il materiale nuovo può esistere (Android 13, sorgenti sane, nessun antenato
  // che chieda il vetro di prima): altrove ogni pezzo resta il vetro di sempre. Si chiede per la forma di serie.
  val probeOptics = remember { GlassDefaults.optics(GlassRole.Floating).sanitized() }
  val renders = isRenderEffectSupported() &&
    rememberFluidGlassModel(FluidCapsuleShape, GlassFalloff.Uniform, probeOptics) == FluidGlassModel.Liquid
  state.renders = renders
  if (renders) {
    // I programmi dei campi a più pezzi si scaldano quando il contenitore entra in scena, non al primo viaggio (F3.8a):
    // un contenitore annuncia che i suoi vetri viaggeranno.
    val view = LocalView.current
    remember(view) { GlassWarmUp.request(GlassWarmFamily.Group, view) }
  }

  // ---- L'adattamento uniforme (F3-g) ----
  val darkSide = GlassDefaults.isDarkSurface()
  val outerHost = LocalGlassAdaptiveHost.current
  val outerShared = LocalGlassSharedHost.current
  // Dentro un vetro adattivo (un foglio, un pop-up) il lato è già deciso da lui: il contenitore non misura.
  val probes = renders && outerHost == null && glassProbeAvailable()
  val large = state.large
  val probe = rememberGlassProbeState(initialDark = darkSide)
  val adaptive = rememberGlassAdaptiveState(
    probe = probe,
    role = GlassRole.Floating,
    darkSide = darkSide,
    inkFollows = probes,
    flipAllowed = true,
    large = large,
  )
  val ink = rememberGlassInk(large)
  if (probes) ink.bind(adaptive, darkSide) else ink.unbind()
  state.adaptive = if (probes) adaptive else null
  val shared = remember(adaptive) { GlassSharedHost { adaptive.inkAlpha() } }
  val backdrop = currentGlassBackdrop()

  LaunchedEffect(state) { state.run() }
  DisposableEffect(state) { onDispose { state.dispose() } }

  CompositionLocalProvider(LocalFluidGlassContainer provides state) {
    Layout(
      content = {
        // Le superfici del vetro, dietro al contenuto. Prendono lato e misura dalla decisione del contenitore.
        CompositionLocalProvider(
          LocalGlassAdaptiveHost provides if (probes) adaptive else outerHost,
          LocalGlassSharedHost provides if (probes) shared else outerShared,
        ) {
          if (renders) FluidGlassFields(state)
        }
        GlassInkContent(ink, LocalContentColor.current, content)
      },
      modifier = modifier
        .onPlaced {
          state.coordinates = it
          state.invalidate()
        }
        // Il contenuto di chi se n'è andato, sopra tutto il resto, finché il suo vetro non l'ha coperto.
        .drawWithContent {
          drawContent()
          with(state) { drawDeparted() }
        }
        // La sonda legge ciò che rifrangono i pezzi del grappolo più grande; finché non c'è, la sorgente di questo punto.
        .glassProbe((state.probeBackdrop ?: backdrop).backdrop, probe, enabled = probes, region = state::probeRegion),
    ) { measurables, constraints ->
      // Vincoli nuovi dal genitore: il contenitore cambia misura (D R5), e i pezzi che il layout sposta lo seguono.
      state.noteConstraints(constraints)
      val loose = fluidGlassContentConstraints(constraints, propagateMinConstraints)
      val fields = ArrayList<Pair<FluidGlassFieldSlot, androidx.compose.ui.layout.Placeable>>()
      val contents = ArrayList<androidx.compose.ui.layout.Placeable>()
      for (measurable in measurables) {
        val slot = measurable.layoutId as? FluidGlassFieldSlot
        if (slot != null) {
          val w = max(1, kotlin.math.round(slot.bounds.width).toInt())
          val h = max(1, kotlin.math.round(slot.bounds.height).toInt())
          fields += slot to measurable.measure(Constraints.fixed(w, h))
        } else {
          contents += measurable.measure(loose)
        }
      }
      val width = constraints.constrainWidth(contents.maxOfOrNull { it.width } ?: 0)
      val height = constraints.constrainHeight(contents.maxOfOrNull { it.height } ?: 0)
      layout(width, height) {
        for ((slot, placeable) in fields) {
          placeable.place(kotlin.math.round(slot.bounds.left).toInt(), kotlin.math.round(slot.bounds.top).toInt())
        }
        for (placeable in contents) placeable.place(0, 0)
      }
    }
  }
}

/**
 * I vincoli del contenuto di un contenitore: quelli del genitore senza i minimi (come un `Box`), o tutti con
 * [propagateMinConstraints] (come `Box(propagateMinConstraints = true)`). Pura.
 */
internal fun fluidGlassContentConstraints(constraints: Constraints, propagateMinConstraints: Boolean): Constraints =
  if (propagateMinConstraints) constraints else constraints.copy(minWidth = 0, minHeight = 0)

/**
 * Il contenuto di un pezzo che **ospita vetri annidati** (F3.2e), di solito un [fluidGlassPanel]:
 *
 * ```
 * FluidGlassContainer(propagateMinConstraints = true) {
 *   Column(Modifier.fluidGlassPanel(backdrop, GlassDefaults.modalTint(), shape, GlassRole.Modal)) {
 *     FluidGlassNested {
 *       Text(…)
 *       FluidGlassIconButton(…)   // vetro annidato del pannello, non un pezzo
 *     }
 *   }
 * }
 * ```
 *
 * Qui dentro un vetro (`fluidGlass`, `glassControlSurface`, i tasti di vetro dell'engine) **non è un pezzo** del
 * contenitore: è un vetro come fuori dal contenitore, che campiona il materiale del pannello su cui sta (il pannello lo
 * esporta, vedi [fluidGlassPanel]) e ne prende il lato, anche mentre il contenitore gira o scurisce. Senza, starebbe nel
 * piano accanto al pannello e si fonderebbe con lui come un fratello.
 *
 * Non aggiunge nodi di layout: il contenuto si posa come se non ci fosse, e lo scope della `Column` o della `Row` che lo
 * contiene resta in vista. Fuori da un contenitore non cambia niente.
 */
@Composable
fun FluidGlassNested(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalFluidGlassContainer provides null, content = content)
}

/**
 * Il contenitore visto da fuori, per le prove (il test del piano nel modulo della galleria, che non vede gli `internal`
 * dell'engine) e per le sessioni sul dispositivo. Non è un'API per le app.
 */
object FluidGlassContainerDiagnostics {
  private val journeys = AtomicLong()
  private val retargets = AtomicLong()
  private val arrivals = AtomicLong()
  private val orderWarnings = AtomicLong()
  private val live = AtomicInteger()

  @Volatile
  private var last: List<FluidGlassFlightRecord> = emptyList()

  @Volatile
  private var rest: List<FluidGlassPieceRecord> = emptyList()

  @Volatile
  private var restLarge: Boolean = false

  @Volatile
  private var contents: List<FluidGlassContentRecord> = emptyList()

  @Volatile
  private var finger: Float = 0f

  @Volatile
  private var start: FluidGlassStartRecord? = null

  /** I viaggi partiti (un cambio a riposo), in tutto il processo. */
  val totalJourneys: Long get() = journeys.get()

  /** I viaggi ripartiti a metà (uno stato cambiato mentre il vetro viaggiava). */
  val totalRetargets: Long get() = retargets.get()

  /**
   * I viaggi arrivati: il conducente ha finito e il palco ha lasciato il vetro al riposo (f3-corpo). È la fine vera di un
   * viaggio; [lastRest] non lo è più, perché il riposo d'arrivo si pubblica al secondo fotogramma del palco (giro di
   * correzione di F3), molto prima dell'arrivo.
   */
  val totalArrivals: Long get() = arrivals.get()

  /**
   * Quante volte un `fluidGlassId`, `fluidGlassUnion` o `fluidGlassTransition` è stato scritto **dopo** il suo vetro
   * (o senza vetro alla sua destra): non ha effetto, e in debug il log lo dice.
   */
  val totalOrderWarnings: Long get() = orderWarnings.get()

  /** I contenitori in scena adesso. */
  val liveContainers: Int get() = live.get()

  /** Chi è andato dove nell'ultimo viaggio partito o ripartito: un record per vetro in volo. */
  val lastJourney: List<FluidGlassFlightRecord> get() = last

  /** I pezzi dell'ultimo riposo stabilito, con i raggi già risolti (le forme concentriche comprese). */
  val lastRest: List<FluidGlassPieceRecord> get() = rest

  /**
   * Che parte fa il contenuto di ogni vetro nell'ultimo viaggio partito o ripartito (F3.8a): chi nasce col suo vetro, chi
   * si condensa, chi se ne va, chi resta.
   */
  val lastContents: List<FluidGlassContentRecord> get() = contents

  /**
   * Se, nell'ultimo riposo stabilito, il contenitore è un **grande** che scurisce in continuo invece di girare (F3.2e):
   * lo dice il pannello che l'ha dichiarato ([fluidGlassPanel], `large` o il suo ruolo), altrimenti la taglia del
   * grappolo più grande.
   */
  val lastRestLarge: Boolean get() = restLarge

  /**
   * La **luce del dito** dell'ultimo viaggio partito o ripartito (F3.10): il bagliore da cui comincia il campo del vetro
   * toccato, 0..1 (nelle unità della pressione). Zero se il viaggio non l'ha fatto partire un tocco su un vetro del
   * contenitore: allora parte dal livello del carattere.
   */
  val lastFinger: Float get() = finger

  /**
   * Com'è partito l'ultimo viaggio da fermo (giro di correzione di F3): quanto ha aspettato dal momento in cui il
   * contenitore ha saputo del cambio, e quanto lavoro ha fatto il fotogramma della partenza sul filo dell'interfaccia.
   * Null finché nessun viaggio è partito. Per i log della galleria e le sessioni sul tablet.
   */
  val lastStart: FluidGlassStartRecord? get() = start

  /**
   * Le tre distanze di fusione della tavola di F3.3, da riprendere sul dispositivo (S3): quella scelta da Alessio è la
   * media, di serie; le altre due servono a confrontarle dal vivo.
   */
  enum class FusionPreview(internal val fusion: PhysicsPlaneFusion) {
    /** 8 dp: si fondono quasi solo quando si toccano. */
    Narrow(PhysicsPlaneFusion.Narrow),

    /** 16 dp: la scelta di serie. */
    Medium(PhysicsPlaneFusion.Medium),

    /** 24 dp, e due vetri distinti entro la distanza restano fusi anche a riposo. */
    Wide(PhysicsPlaneFusion.Wide),
  }

  /**
   * La manopola delle prove: con un valore, **ogni** contenitore usa quella distanza di fusione al posto del suo
   * `spacing` (e la «larga» fonde anche a riposo). Null, di serie: lo `spacing` di ogni contenitore. Uno stato: i
   * contenitori in scena si ricompongono quando cambia. Non è un'API per le app (la galleria la legge dall'extra
   * `plane.fusion`).
   */
  var fusionPreview: FusionPreview? by mutableStateOf(null)

  /** Azzera i contatori e i record, per i test. */
  fun resetForTesting() {
    journeys.set(0)
    retargets.set(0)
    arrivals.set(0)
    orderWarnings.set(0)
    last = emptyList()
    rest = emptyList()
    restLarge = false
    contents = emptyList()
    finger = 0f
    start = null
  }

  internal fun onStart(record: FluidGlassStartRecord) {
    start = record
  }

  internal fun onJourney(records: List<FluidGlassFlightRecord>, retarget: Boolean) {
    if (retarget) retargets.incrementAndGet() else journeys.incrementAndGet()
    last = records
  }

  internal fun onArrival() {
    arrivals.incrementAndGet()
  }

  internal fun onFinger(entry: Float) {
    finger = entry
  }

  internal fun onContents(records: List<FluidGlassContentRecord>) {
    contents = records
  }

  internal fun onRest(records: List<FluidGlassPieceRecord>, large: Boolean = false) {
    rest = records
    restLarge = large
  }

  internal fun onOrderWarning() {
    orderWarnings.incrementAndGet()
  }

  internal fun onContainer(delta: Int) {
    live.addAndGet(delta)
  }
}

/**
 * Un vetro in volo nell'ultimo viaggio: da quale `id` a quale (null per un vetro senza `id`), che cambio è
 * (`Matched`, `BornFromNeighbour`, `MergeIntoNeighbour`, `Materialize`, `Dematerialize`, `Identity`) e, per chi nasce o
 * confluisce, l'`id` del vicino.
 */
@Immutable
class FluidGlassFlightRecord internal constructor(
  val fromId: Any?,
  val toId: Any?,
  val change: String,
  val bornFromId: Any?,
  val mergesIntoId: Any?,
) {
  override fun toString(): String =
    "FluidGlassFlightRecord($fromId -> $toId, $change, bornFrom=$bornFromId, mergesInto=$mergesIntoId)"
}

/**
 * La partenza di un viaggio da fermo (giro di correzione di F3, A 8 e C R5): [waitMs] dal momento in cui il contenitore a
 * riposo ha saputo del cambio (il pezzo nuovo si iscrive o si posa: la composizione e il layout del fotogramma del
 * cambio) all'istante del fotogramma in cui il palco parte, che è sempre il fotogramma dopo (il pezzo nuovo è misurato
 * lì); [workMs] il lavoro di quel fotogramma sul filo dell'interfaccia (misura, viaggio, palco), senza la composizione e
 * il disegno che vengono dopo. A schermo fermo l'attesa è un periodo del pannello (a 30 Hz, fino a 33 ms), e prima ce
 * n'è un altro, quello in cui l'app compone il cambio: il contenitore non lo vede.
 */
@Immutable
class FluidGlassStartRecord internal constructor(val waitMs: Double, val workMs: Double) {
  override fun toString(): String =
    "FluidGlassStartRecord(attesa=${"%.1f".format(java.util.Locale.ROOT, waitMs)} ms, " +
      "lavoro=${"%.2f".format(java.util.Locale.ROOT, workMs)} ms)"
}

/**
 * Il contenuto di un vetro nell'ultimo viaggio partito o ripartito: l'`id` del vetro (o null), la parte che il contenuto
 * fa (`Rest`: resta nitido e viaggia col vetro; `Born`: nasce col vetro; `Lens`: si condensa nella comparsa per lente;
 * `Recover`: torna nitido da dove si trovava; `Exit`: se ne va; `Hidden`: non si vede) e se è il contenuto di un vetro che
 * non c'è più, disegnato dal contenitore finché non è sparito.
 */
@Immutable
class FluidGlassContentRecord internal constructor(
  val id: Any?,
  val role: String,
  val departed: Boolean,
) {
  override fun toString(): String = "FluidGlassContentRecord($id, $role${if (departed) ", departed" else ""})"
}

/** Un vetro a riposo: il suo `id` (o null), il riquadro nel contenitore in pixel e i quattro raggi risolti. */
@Immutable
class FluidGlassPieceRecord internal constructor(
  val id: Any?,
  val bounds: Rect,
  val cornerRadii: FluidCornerRadii,
  /** Quanti pezzi ha il suo grappolo: uno = una cattura sua, di più = una cattura per tutti. */
  val clusterSize: Int,
  /**
   * L'unione del vetro (l'`id` di [fluidGlassUnion]), o null. Due vetri dello stesso grappolo con la stessa unione sono
   * una sagoma sola anche a riposo; distinti, separati del tutto.
   */
  val unionId: Any? = null,
  /**
   * Se il vetro esporta il suo materiale ai vetri annidati nel suo contenuto (F3.2e): un [fluidGlassPanel], che ne ha
   * sempre uno (quello dell'app, o uno suo).
   */
  val exports: Boolean = false,
  /**
   * La pellicola con cui il contenitore disegna **questo** vetro a riposo (F4A.2): la sua, anche in un grappolo di più pezzi
   * con tinte diverse. Fino a F4A.1 un grappolo si disegnava con la tinta del primo pezzo (review di F3, D R4).
   */
  val drawnTint: GlassTint? = null,
) {
  override fun toString(): String =
    "FluidGlassPieceRecord($id, $bounds, $cornerRadii, cluster=$clusterSize" +
      "${unionId?.let { ", union=$it" } ?: ""}${if (exports) ", exports" else ""})"
}

// ================================================================================================================
// L'ordine dei modificatori (contratto)
// ================================================================================================================

/**
 * [block] dentro una sezione di `android.os.Trace` col nome [name] (giro di correzione di F3): nelle tracce di Perfetto del
 * tablet il passo del contenitore e la partenza del palco hanno il loro nome, separati dalla composizione e dal disegno.
 */
internal inline fun <T> fluidGlassTraced(name: String, block: () -> T): T {
  android.os.Trace.beginSection(name)
  try {
    return block()
  } finally {
    android.os.Trace.endSection()
  }
}

/** Un `id` qualificato dal suo spazio dei nomi: è questo che il piano confronta. */
internal data class FluidGlassQualifiedId(val namespace: FluidGlassNamespace, val id: Any)

/** Ciò che i modificatori a sinistra di un vetro dicono di lui. */
@Immutable
internal data class FluidGlassAttributes(
  val id: FluidGlassQualifiedId? = null,
  val unionId: FluidGlassQualifiedId? = null,
  val transition: FluidGlassTransition = FluidGlassTransition.MatchedGeometry,
  val contentAnchor: FluidGlassContentAnchor = FluidGlassContentAnchor.Center,
  /** Ciò che il vetro porta, se chi lo disegna lo dichiara ([fluidGlassContentKey], F4A.5b): `GlassPlanePiece.contentKey`. */
  val contentKey: Any? = null,
) {
  companion object {
    val None = FluidGlassAttributes()
  }
}

/**
 * Gli attributi del vetro che chiude [chain]: gli elementi dopo l'ultimo vetro (un [FluidGlassMarkerElement]) e prima
 * della fine, il più a destra vince. Pura sulla catena; segna come **consumati** gli elementi che usa, perché il loro nodo
 * non avvisi. Il vetro la chiama sulla catena alla sua sinistra (`this`).
 */
internal fun fluidGlassAttributesOf(chain: Modifier): FluidGlassAttributes {
  val pending = ArrayList<FluidGlassAttributeElement>()
  chain.foldIn(Unit) { _, element ->
    if (element is FluidGlassMarkerElement) {
      pending.clear()
    } else if (element is FluidGlassAttributeElement) {
      pending.add(element)
    }
    Unit
  }
  var out = FluidGlassAttributes.None
  for (element in pending) {
    element.consumed = true
    out = when (element) {
      is FluidGlassIdElement -> out.copy(id = FluidGlassQualifiedId(element.namespace, element.id))
      is FluidGlassUnionElement -> out.copy(unionId = FluidGlassQualifiedId(element.namespace, element.id))
      is FluidGlassTransitionElement -> out.copy(transition = element.transition)
      is FluidGlassContentAnchorElement -> out.copy(contentAnchor = element.anchor)
      is FluidGlassContentKeyElement -> out.copy(contentKey = element.key)
    }
  }
  return out
}

/** Gli elementi che valgono per il vetro alla loro destra. `consumed` non entra nell'uguaglianza. */
internal sealed class FluidGlassAttributeElement : ModifierNodeElement<FluidGlassAttributeNode>() {
  /** Vero quando un vetro alla destra l'ha letto, nella stessa composizione. */
  @Volatile
  var consumed: Boolean = false

  abstract val label: String

  override fun create(): FluidGlassAttributeNode = FluidGlassAttributeNode(consumed, label)

  override fun update(node: FluidGlassAttributeNode) {
    node.update(consumed, label)
  }
}

internal class FluidGlassIdElement(val id: Any, val namespace: FluidGlassNamespace) : FluidGlassAttributeElement() {
  override val label: String get() = "fluidGlassId($id)"
  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassIdElement && other.id == id && other.namespace === namespace
  override fun hashCode(): Int = 31 * id.hashCode() + System.identityHashCode(namespace)
  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassId"
    properties["id"] = id
    properties["namespace"] = namespace
  }
}

internal class FluidGlassUnionElement(val id: Any, val namespace: FluidGlassNamespace) : FluidGlassAttributeElement() {
  override val label: String get() = "fluidGlassUnion($id)"
  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassUnionElement && other.id == id && other.namespace === namespace
  override fun hashCode(): Int = 17 * id.hashCode() + System.identityHashCode(namespace)
  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassUnion"
    properties["id"] = id
    properties["namespace"] = namespace
  }
}

internal class FluidGlassTransitionElement(val transition: FluidGlassTransition) : FluidGlassAttributeElement() {
  override val label: String get() = "fluidGlassTransition($transition)"
  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassTransitionElement && other.transition == transition
  override fun hashCode(): Int = transition.hashCode()
  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassTransition"
    properties["transition"] = transition
  }
}

internal class FluidGlassContentAnchorElement(val anchor: FluidGlassContentAnchor) : FluidGlassAttributeElement() {
  override val label: String get() = "fluidGlassContentAnchor($anchor)"
  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassContentAnchorElement && other.anchor == anchor
  override fun hashCode(): Int = anchor.hashCode()
  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassContentAnchor"
    properties["anchor"] = anchor
  }
}

internal class FluidGlassContentKeyElement(val key: Any) : FluidGlassAttributeElement() {
  override val label: String get() = "fluidGlassContentKey($key)"
  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassContentKeyElement && other.key == key
  override fun hashCode(): Int = key.hashCode()
  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassContentKey"
    properties["key"] = key
  }
}

/**
 * Il nodo di un attributo non fa niente: c'è per avvisare, in una build di debug, quando nessun vetro alla sua destra
 * l'ha letto (scritto dopo il vetro, o senza vetro). Una volta per nodo.
 */
internal class FluidGlassAttributeNode(
  private var consumed: Boolean,
  private var label: String,
) : Modifier.Node(), CompositionLocalConsumerModifierNode {
  private var warned = false

  fun update(consumed: Boolean, label: String) {
    this.consumed = consumed
    this.label = label
    warnIfUnused()
  }

  override fun onAttach() {
    warnIfUnused()
  }

  private fun warnIfUnused() {
    if (consumed || warned || !isAttached) return
    warned = true
    FluidGlassContainerDiagnostics.onOrderWarning()
    val debuggable = (currentValueOf(LocalContext).applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (debuggable) {
      Log.w(
        FluidGlassLogTag,
        "$label non ha effetto: va scritto prima del fluidGlass a cui si riferisce, nella stessa catena " +
          "(Modifier.$label.fluidGlass()).",
      )
    }
  }
}

/** Il segno di un vetro nella catena: gli attributi a sinistra sono suoi, quelli dopo del vetro che segue. */
internal data object FluidGlassMarkerElement : ModifierNodeElement<FluidGlassMarkerNode>() {
  override fun create(): FluidGlassMarkerNode = FluidGlassMarkerNode()
  override fun update(node: FluidGlassMarkerNode) = Unit
  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlass"
  }
}

internal class FluidGlassMarkerNode : Modifier.Node()

internal const val FluidGlassLogTag = "FluidGlass"

// ================================================================================================================
// Il conducente: riposo e palco, logica pura
// ================================================================================================================

/**
 * Chi porta il vetro del contenitore, fotogramma per fotogramma: logica pura, senza Compose, provata da sola
 * (`FluidGlassContainerDriverTest`).
 *
 * Due fasi. **A riposo** [rest] è lo stato che le superfici dei grappoli mostrano. **In viaggio** [journey] porta i vetri
 * da [origin] a [rest] (che è già l'arrivo: le superfici dei grappoli d'arrivo sono montate, spente, pronte a riprendere
 * il vetro). Un cambio a riposo apre un viaggio che parte **esattamente** dalla geometria che il riposo mostrava; un
 * cambio in viaggio lo fa ripartire da dove si trova ([GlassPlaneJourney.retarget]); il viaggio finito restituisce il
 * vetro al riposo d'arrivo, che è la sua ultima geometria.
 */
internal class FluidGlassPlaneDriver(
  /**
   * Di quanto avanza al più l'orologio in ognuno dei primi [firstSteps] fotogrammi di un viaggio partito da fermo, in
   * nanosecondi veri; zero, nessun tetto (i test contano il tempo a mano). Vedi [FluidMotion.PlaneFirstStepMillis].
   */
  private val firstStepCapNanos: Long = 0L,
  private val firstSteps: Int = FluidMotion.PlaneFirstSteps,
  /**
   * Il **carattere del viaggio** (F3.9, [GlassLiquidJourney]): la luce e il rigonfiamento del vetro lungo la fase. Di
   * serie quello del profilo con cui si disegna, letto ogni volta: le manopole di `gallery.tune` lo cambiano dal vivo.
   */
  private val character: () -> GlassLiquidJourney = { GlassLiquidProfile.Default.journey },
) {
  /** Lo stato a riposo, o l'arrivo del viaggio in corso. Null finché il contenitore non ha misurato niente. */
  var rest: GlassPlaneState? = null
    private set

  /** Da dove è partito il viaggio in corso. */
  var origin: GlassPlaneState? = null
    private set

  var journey: GlassPlaneJourney? = null
    private set

  /** Il fotogramma del viaggio di adesso, o null a riposo. */
  var frame: GlassPlaneFrame? = null
    private set

  /** Cresce a ogni viaggio nuovo o ripartito: chi disegna il palco sa che i campi sono cambiati. */
  var journeyRevision: Int = 0
    private set

  /**
   * L'orologio del viaggio (F3.8a): il tempo dall'ultima partenza o ripartenza e quello dalla partenza da fermi, sommati
   * fotogramma per fotogramma. Non è la differenza fra due istanti: i primi passi di un viaggio hanno un tetto
   * ([firstStepCapNanos]), perché un buco di fotogrammi alla partenza sia tempo perso e non moto saltato.
   */
  private var lastNanos = 0L
  private var clockNanos = 0L
  private var movingNanos = 0L
  private var steps = 0

  /** Quanto del viaggio è passato, 0..1: all'uno la superficie fisica è ferma. */
  var progress: Float = 1f
    private set

  /**
   * L'orologio del collo del viaggio in corso (F3.3, [PhysicsPlaneNeck]): parte da -1 a ogni viaggio da fermi, riparte
   * dal collo di adesso a ogni interruzione (invariante 16), dura il doppio del riposo a vista del viaggio
   * (`physicsPlaneNeckMs`). Null a riposo.
   */
  var neck: PhysicsPlaneNeck? = null
    private set

  /** Il tempo del viaggio in corso dall'ultima partenza o ripartenza, in millisecondi (scala di sistema compresa). */
  var elapsedMs: Double = 0.0
    private set

  /**
   * Da quanto il vetro si muove, in millisecondi: dalla partenza da fermi, e un'interruzione **non** lo azzera. È il
   * tempo di `physicsPlaneMotion`, che accende il vetro in viaggio in 40 ms e non deve riaccenderlo a ogni ripartenza.
   */
  var movingMs: Double = 0.0
    private set

  /** La coordinata del collo di adesso: -1 a riposo. */
  fun neckNow(): Float = neck?.at(elapsedMs) ?: -1f

  /**
   * La **fase** del viaggio in corso (F3.8a): il tempo dalla partenza o dalla ripartenza diviso per il riposo a vista, 0
   * alla partenza e 1 dal riposo in poi. Uno a riposo. È ciò che legge il carattere del viaggio ([journeyLight],
   * [journeySwell]): lo stesso numero di `glassJourneyPhase(elapsedMs, settleMs)`.
   */
  val phase: Float
    get() {
      val settle = journey?.settleMs ?: return 1f
      return if (settle > 0.0) (elapsedMs / settle).toFloat().fastCoerceIn(0f, 1f) else 1f
    }

  /**
   * I livelli con cui il viaggio in corso **comincia** (F3.9, l'aggancio di `sessioni/f3-9/aggancio.md`): zero per un
   * viaggio partito da fermo, la luce e il rigonfiamento che aveva il viaggio interrotto per uno che riparte. Senza, un
   * cambio di stato a metà spegnerebbe luce e rigonfiamento in un fotogramma (invariante 16).
   */
  var journeyLightEntry: Float = 0f
    private set

  /** Vedi [journeyLightEntry]. */
  var journeySwellEntry: Float = 0f
    private set

  /** Vedi [journeyLightEntry]: il livello dell'alone (F3.12c) con cui il viaggio comincia. */
  var journeyGlowEntry: Float = 0f
    private set

  /** Il carattere del viaggio di adesso: vedi il parametro `character`. */
  val journeyCharacter: GlassLiquidJourney get() = character()

  /**
   * Il livello della **luce** del carattere del viaggio adesso, 0..1 (`GlassLiquidJourney.light` alla [phase]): il velo
   * verso la luce, l'alone e la smerigliatura che la ricetta delle superfici fisiche applica con `glassJourneyRecipe`
   * (F3.10: `FluidGlassContainerState.driveStage` lo mette nella forma di ogni campo che viaggia). Zero a riposo.
   */
  val journeyLight: Float
    get() {
      val running = journey ?: return 0f
      val c = character()
      // Un carattere senza luce (`journey=off`) non ha livello: il palco dice zero, non l'inviluppo della curva.
      if (!c.lights) return 0f
      return c.light(phase, journeyLightEntry, running.settleMs.toFloat())
    }

  /**
   * Il livello dell'**alone** del carattere del viaggio adesso, 0..1 (F3.12c, `GlassLiquidJourney.glow` alla [phase]):
   * sale come la luce, resta pieno fino a ~0,31 del viaggio e si spegne a ~0,69, prima della luce del corpo, come nel
   * render di Apple (F3.12b, `sessioni/f3-12b/misure.md`). Lo porta la forma del palco (`PhysicsPlaneShape.journeyGlow`) a
   * `glassJourneyRecipe`, che lo usa per l'alone al posto della luce. Zero a riposo e senza luce (`journey=off`).
   */
  val journeyGlow: Float
    get() {
      val running = journey ?: return 0f
      val c = character()
      if (!c.lights) return 0f
      return c.glowLevel(phase, journeyGlowEntry, running.settleMs.toFloat())
    }

  /**
   * Il livello del **rigonfiamento** del carattere adesso, 0..1 (`GlassLiquidJourney.swell` alla [phase]): zero a riposo, e
   * zero con un carattere senza rigonfiamento (`journey=off`).
   */
  val journeySwell: Float
    get() {
      if (journey == null) return 0f
      val c = character()
      return if (c.swell > 0f) c.swell(phase, journeySwellEntry) else 0f
    }

  /**
   * La **luce del dito** del viaggio in corso (F3.10): il vetro toccato che l'ha fatto partire, dove stava il dito, e
   * da che bagliore comincia. Null senza tocco (un cambio dall'app, da un timer, da un vetro fuori dal contenitore): il
   * viaggio ha la luce del carattere e basta. La dà il contenitore con [touched], subito dopo la partenza; un viaggio che
   * riparte se la porta dietro dal bagliore che aveva (invariante 16).
   */
  var finger: FluidGlassFinger? = null
    private set

  /** Il bagliore e il riempimento del dito del viaggio interrotto, da cui riparte quello nuovo: vedi [touched]. */
  private var fingerGlowCarried = 0f
  private var fingerFillCarried = 0f

  /** Il bagliore del dito adesso, 0..1 (`GlassLiquidJourney.fingerGlow` alla [phase]): zero senza tocco e a riposo. */
  val fingerGlow: Float
    get() {
      val touched = finger ?: return 0f
      val running = journey ?: return 0f
      return character().fingerGlow(phase, touched.entry, running.settleMs.toFloat())
    }

  /** Quanto il bagliore del dito ha riempito il vetro adesso, 0..1 (`GlassLiquidJourney.fingerFill`): zero senza tocco. */
  val fingerFill: Float
    get() {
      val touched = finger ?: return 0f
      if (journey == null) return 0f
      return character().fingerFill(phase, touched.fillEntry)
    }

  /**
   * Il viaggio appena partito o ripartito l'ha fatto partire un **tocco** sul pezzo [key] (F3.10): il suo bagliore lo
   * accompagna. [amount] è la pressione che il vetro ha adesso (`GlassTouchLight.amount`), [u] e [v] dove sta il dito
   * in frazione del suo riquadro. [shown] dice se il bagliore si vedeva sul vetro fermo (un pezzo solo a riposo: i
   * grappoli non lo mostrano ancora, F6.1): allora il viaggio comincia da quel bagliore, esatto, e il passaggio riposo →
   * palco non salta; altrimenti sale da quello che c'era. Con la luce del dito spenta (`GlassLiquidJourney.finger`
   * zero, `journey=off`) non fa niente.
   */
  fun touched(key: Any, amount: Float, u: Float, v: Float, shown: Boolean) {
    if (journey == null || !(character().finger > 0f)) return
    val pressed = if (shown && amount.isFinite()) amount.fastCoerceIn(0f, 1f) else 0f
    finger = FluidGlassFinger(
      key = key,
      entry = max(fingerGlowCarried, pressed),
      fillEntry = fingerFillCarried,
      u = if (u.isFinite()) u.fastCoerceIn(0f, 1f) else 0.5f,
      v = if (v.isFinite()) v.fastCoerceIn(0f, 1f) else 0.5f,
    )
  }

  /**
   * Il collo delle unioni che **cambiano** in questo viaggio (F3.8a): «Unisci» e «Separa» sono un viaggio, non un
   * fotogramma. Un'unione che si scioglie parte dal collo pieno e si strozza e si rompe con la legge di ogni divisione
   * (`PhysicsPlaneNeck` da +1); una che nasce fa la stessa strada al contrario sul riposo a vista (il ponte nasce a un
   * quarto del viaggio, è pieno a metà) e pieno resta, perché a riposo l'unione è una sagoma sola. Riparte da dove si
   * trova a ogni interruzione.
   */
  private var unionKind: GlassPlaneUnionChange = GlassPlaneUnionChange.None
  private var unionDissolve: PhysicsPlaneNeck? = null
  private var unionFormOffsetMs = 0.0

  /** La coordinata del collo di un campo: quella dell'orologio, o quella dell'unione che in quel campo nasce o si scioglie. */
  fun neckNow(union: GlassPlaneUnionChange): Float = when {
    journey == null -> -1f
    union == GlassPlaneUnionChange.None || union != unionKind -> neckNow()
    union == GlassPlaneUnionChange.Dissolving -> unionDissolve?.at(elapsedMs) ?: -1f
    else -> fluidGlassUnionFormingNeck(elapsedMs + unionFormOffsetMs, journey?.settleMs ?: 0.0)
  }

  /** Prepara il collo delle unioni del viaggio [started]: da [from], il collo che quei pezzi hanno adesso. */
  private fun startUnion(started: GlassPlaneJourney, from: Float?) {
    val kind = started.fields.firstOrNull { it.union != GlassPlaneUnionChange.None }?.union ?: GlassPlaneUnionChange.None
    unionKind = kind
    unionDissolve = null
    unionFormOffsetMs = 0.0
    when (kind) {
      GlassPlaneUnionChange.None -> Unit
      GlassPlaneUnionChange.Dissolving ->
        unionDissolve = PhysicsPlaneNeck(from ?: 1f, physicsPlaneNeckMs(started.settleMs))
      GlassPlaneUnionChange.Forming ->
        unionFormOffsetMs = fluidGlassUnionFormingOffsetMs(from ?: -1f, started.settleMs)
    }
  }

  /** Che cosa è cambiato in un passo. */
  enum class Change { Nothing, Rest, Started, Retargeted, Moved, Finished }

  /**
   * Un fotogramma all'istante [nanos], con lo stato misurato adesso ([measured], null se il contenitore non è posato).
   * [motion] null = nessun viaggio (`FluidGlassAnimation.None`, movimento ridotto); [durationScale] la scala delle
   * animazioni di sistema (zero = nessun viaggio).
   *
   * **L'immediatezza vale anche a viaggio avviato** (giro di correzione di F3, R1 di C): se `None`, il movimento ridotto
   * o la scala a zero arrivano mentre un vetro viaggia, il viaggio finisce in questo fotogramma al posto misurato, anche
   * se l'arrivo non è cambiato. Si decide **prima** di dividere il tempo per la scala: con la scala a zero l'orologio
   * valeva infinito, e il fotogramma del piano si rifiutava (un'eccezione dall'effetto del contenitore).
   *
   * [resized] dice che il contenitore ha ricevuto **vincoli nuovi** dal genitore dall'ultimo passo (una rotazione, lo
   * schermo diviso, una riga che si allarga attorno al contenitore) o una densità nuova: se i pezzi sono gli stessi
   * ([samePieces]), quello che si è mosso l'ha mosso il layout, non lo stato dell'app, e i vetri **seguono il riquadro
   * senza viaggio e senza luce** (D R5). Un cambio dei pezzi nello stesso fotogramma viaggia come sempre.
   */
  fun step(
    nanos: Long,
    measured: GlassPlaneState?,
    motion: GlassPlaneMotion?,
    durationScale: Float = 1f,
    resized: Boolean = false,
  ): Change {
    if (measured == null) return Change.Nothing
    val current = rest
    val running = journey
    val follows = resized && current != null && samePieces(current, measured)
    val instant = motion == null || !(durationScale > 0f) || follows
    if (running == null) {
      if (current != null && planesAlike(current, measured)) {
        rest = planeWithContentOf(current, measured)
        return Change.Nothing
      }
      if (current == null || instant || current.pieces.isEmpty() && measured.pieces.isEmpty()) {
        rest = measured
        return Change.Rest
      }
      origin = current
      rest = measured
      val started = GlassFrameDiagnostics.span("journey", this) { GlassPlaneJourney(current, measured, motion!!) }
      journey = started
      lastNanos = nanos
      clockNanos = 0L
      movingNanos = 0L
      steps = 0
      journeyRevision++
      frame = started.frame(0.0)
      progress = 0f
      elapsedMs = 0.0
      movingMs = 0.0
      journeyLightEntry = 0f
      journeySwellEntry = 0f
      journeyGlowEntry = 0f
      finger = null
      fingerGlowCarried = 0f
      fingerFillCarried = 0f
      neck = PhysicsPlaneNeck(-1f, physicsPlaneNeckMs(started.settleMs))
      startUnion(started, from = null)
      return Change.Started
    }
    // Viaggio avviato e immediatezza arrivata adesso (la scala a zero, `None`, il movimento ridotto, il contenitore che
    // cambia misura): si arriva in questo fotogramma, prima di toccare l'orologio.
    if (instant) {
      finish()
      rest = measured
      return Change.Finished
    }
    advance(nanos)
    val elapsed = clockNanos / 1_000_000.0 / durationScale.toDouble()
    if (current != null && !planesAlike(current, measured)) {
      // Il collo di adesso, prima di cambiare viaggio: da lì ripartono l'orologio del collo e quello delle unioni.
      elapsedMs = elapsed
      val unionNow = if (unionKind != GlassPlaneUnionChange.None) neckNow(unionKind) else null
      // La luce e il rigonfiamento di adesso, prima di cambiare viaggio: da lì riparte il carattere di quello nuovo.
      val lightNow = journeyLight
      val swellNow = journeySwell
      val glowNow = journeyGlow
      // E il bagliore del dito: il viaggio nuovo lo porta da dov'è (F3.10).
      val fingerNow = fingerGlow
      val fillNow = fingerFill
      // Il rigonfiamento comune di adesso passa al piano: chi prendeva una parte diversa del comune se la porta (F3.12c).
      val restarted = running.retarget(elapsed, measured, swellNow)
      // Il collo riparte da dove si trova, sul riposo a vista del viaggio nuovo.
      val neckMs = physicsPlaneNeckMs(restarted.settleMs)
      neck = neck?.retarget(elapsed, neckMs) ?: PhysicsPlaneNeck(-1f, neckMs)
      journey = restarted
      origin = restarted.from
      rest = measured
      clockNanos = 0L
      journeyRevision++
      frame = restarted.frame(0.0)
      progress = 0f
      elapsedMs = 0.0
      movingMs = movingNanos / 1_000_000.0 / durationScale.toDouble()
      journeyLightEntry = lightNow
      journeySwellEntry = swellNow
      journeyGlowEntry = glowNow
      fingerGlowCarried = fingerNow
      fingerFillCarried = fillNow
      finger = finger?.let { FluidGlassFinger(it.key, fingerNow, fillNow, it.u, it.v) }
      startUnion(restarted, from = unionNow)
      return Change.Retargeted
    }
    // Un cambio solo di contenuto a viaggio avviato non lo fa ripartire: l'arrivo lo sa, per il viaggio dopo (F4A.5b).
    if (current != null) rest = planeWithContentOf(current, measured)
    val now = running.frame(elapsed)
    if (now.finished) {
      finish()
      return Change.Finished
    }
    frame = now
    elapsedMs = elapsed
    movingMs = movingNanos / 1_000_000.0 / durationScale.toDouble()
    progress = if (running.durationMs > 0.0) (elapsed / running.durationMs).toFloat().fastCoerceIn(0f, FluidGlassTravelProgressCap) else 0f
    return Change.Moved
  }

  /** Un fotogramma in più sull'orologio del viaggio, col tetto dei primi passi. */
  private fun advance(nanos: Long) {
    var step = max(0L, nanos - lastNanos)
    if (firstStepCapNanos > 0L && steps < firstSteps && step > firstStepCapNanos) step = firstStepCapNanos
    lastNanos = nanos
    clockNanos += step
    movingNanos += step
    steps++
  }

  private fun finish() {
    journey = null
    origin = null
    frame = null
    progress = 1f
    neck = null
    elapsedMs = 0.0
    movingMs = 0.0
    journeyLightEntry = 0f
    journeySwellEntry = 0f
    journeyGlowEntry = 0f
    finger = null
    fingerGlowCarried = 0f
    fingerFillCarried = 0f
    unionKind = GlassPlaneUnionChange.None
    unionDissolve = null
  }
}

/**
 * Il dito che ha fatto partire un viaggio del piano (F3.10): il pezzo toccato [key], il bagliore [entry] e il
 * riempimento [fillEntry] da cui il viaggio comincia, e dove stava il dito nel pezzo ([u], [v], in frazione del suo
 * riquadro: segue il pezzo in volo).
 */
@Immutable
internal class FluidGlassFinger(val key: Any, val entry: Float, val fillEntry: Float, val u: Float, val v: Float)

/**
 * La luce del dito di un campo del palco in un fotogramma (F3.10): il bagliore [glow] e il riempimento [fill] del dito
 * [finger], centrato dove stava il dito sul pezzo toccato **in volo** (nelle coordinate del nodo del campo, che
 * comincia in [origin]). Null se il dito non c'è, se il bagliore è spento o se il pezzo toccato non viaggia in questo
 * campo: gli altri campi hanno la luce del carattere e basta. Pura.
 */
internal fun fluidGlassFingerTouch(
  frame: GlassPlaneFrame,
  flights: List<GlassPlaneFlightId>,
  origin: Offset,
  finger: FluidGlassFinger?,
  glow: Float,
  fill: Float,
): PhysicsPlaneTouch? {
  if (finger == null || !(glow > 0f)) return null
  if (flights.none { it.sourceKey == finger.key || it.targetKey == finger.key }) return null
  val flying = frame.pieces.firstOrNull {
    it.flightId in flights && (it.flightId.sourceKey == finger.key || it.flightId.targetKey == finger.key)
  }
  // Un pezzo che non è più nel fotogramma (confluito del tutto) lascia il bagliore al centro del campo.
  val b = flying?.bounds
  val x = if (b != null && b.width > 0f) b.left + b.width * finger.u - origin.x else Float.NaN
  val y = if (b != null && b.height > 0f) b.top + b.height * finger.v - origin.y else Float.NaN
  return PhysicsPlaneTouch(glow.fastCoerceIn(0f, 1f), x, y, fill.fastCoerceIn(0f, 1f))
}

/**
 * Il collo di un'unione che **nasce** a [tMs] dall'inizio della sua salita, su un viaggio fermo a vista a [settleMs]: la
 * legge della divisione (`PhysicsPlaneNeck` da +1, la stessa strozzatura e la stessa rottura) letta al contrario sul
 * riposo a vista. Vale -1 a zero, zero (il ponte nasce) a circa un quarto, uno (pieno) a circa metà, e uno resta. Pura.
 */
internal fun fluidGlassUnionFormingNeck(tMs: Double, settleMs: Double): Float {
  if (!(settleMs > 0.0) || !tMs.isFinite()) return 1f
  return PhysicsPlaneNeck(1f, physicsPlaneNeckMs(settleMs)).at((settleMs - tMs).coerceAtLeast(0.0))
}

/**
 * Da che punto della salita di [fluidGlassUnionFormingNeck] il collo vale [neck]: è da lì che riparte un'unione che nasce
 * mentre il collo c'è già (un «Separa» ripensato a metà). Zero da fermi (-1). Per bisezione: la salita è monotona. Pura.
 */
internal fun fluidGlassUnionFormingOffsetMs(neck: Float, settleMs: Double): Double {
  if (!(settleMs > 0.0) || !neck.isFinite() || neck <= -1f) return 0.0
  if (neck >= 1f) return settleMs
  var lo = 0.0
  var hi = settleMs
  repeat(28) {
    val mid = (lo + hi) / 2.0
    if (fluidGlassUnionFormingNeck(mid, settleMs) < neck) lo = mid else hi = mid
  }
  return (lo + hi) / 2.0
}

/**
 * Il progresso più alto che il palco dichiara a Fluid-physics prima della fine: all'uno la superficie fisica si
 * considera ferma, e il palco non è mai fermo finché viaggia.
 */
internal const val FluidGlassTravelProgressCap: Float = 0.999f

/** Sotto mezzo pixel due riquadri o due raggi sono uguali: la stessa regola dell'engine per il fermo. */
internal const val FluidGlassPlaneTolerancePx: Float = 0.5f

/**
 * Vero se [a] e [b] chiedono gli stessi vetri: stessi pezzi, stesse identità, unioni, transizioni e varianti, riquadri e
 * raggi entro mezzo pixel. Pura. Un contenitore che si sposta sullo schermo non cambia niente (le coordinate sono le
 * sue).
 */
internal fun planesAlike(a: GlassPlaneState, b: GlassPlaneState): Boolean {
  if (a.pieces.size != b.pieces.size) return false
  if (abs(a.spacing - b.spacing) > FluidGlassPlaneTolerancePx) return false
  // Per chiave, non per posizione nella lista: il registro rimette in fondo chi è stato tolto e rimesso.
  val others = b.pieces.associateBy { it.key }
  for (x in a.pieces) {
    val y = others[x.key] ?: return false
    if (x.id != y.id || x.unionId != y.unionId || x.transition != y.transition ||
      x.variant != y.variant
    ) {
      return false
    }
    if (!rectsAlike(x.bounds, y.bounds) || !radiiAlike(x.radii, y.radii)) return false
  }
  return true
}

/**
 * Vero se [a] e [b] hanno **gli stessi pezzi** (stesse chiavi, identità, unioni, transizioni e varianti), dovunque
 * stiano: è il caso del contenitore che cambia misura (D R5), in cui il layout sposta i pezzi senza che lo stato dell'app
 * cambi. Pura.
 */
internal fun samePieces(a: GlassPlaneState, b: GlassPlaneState): Boolean {
  if (a.pieces.size != b.pieces.size) return false
  val others = b.pieces.associateBy { it.key }
  for (x in a.pieces) {
    val y = others[x.key] ?: return false
    if (x.id != y.id || x.unionId != y.unionId || x.transition != y.transition || x.variant != y.variant) return false
  }
  return true
}

/**
 * [rest] con ciò che portano i pezzi di [measured] (`GlassPlanePiece.contentKey`, F4A.5b), dove la chiave è la stessa;
 * [rest] stesso se niente è cambiato. Un cambio **solo di contenuto** non è un viaggio ([planesAlike] non lo guarda: due
 * tasti che cambiano solo icona restano vetri fermi, la scelta di F4A.3), ma il riposo deve saperlo: il vetro respira
 * solo se cambia contenuto nello stesso viaggio di un altro (la barra che cambia contesto), mai per un cambio vecchio.
 * Pura.
 */
internal fun planeWithContentOf(rest: GlassPlaneState, measured: GlassPlaneState): GlassPlaneState {
  val now = measured.pieces.associateBy { it.key }
  if (rest.pieces.all { piece -> now[piece.key]?.let { it.contentKey == piece.contentKey } != false }) return rest
  return GlassPlaneState(
    rest.pieces.map { piece -> now[piece.key]?.let { piece.copy(contentKey = it.contentKey) } ?: piece },
    rest.spacing, rest.bounds, rest.preferredPieces, rest.room,
  )
}

internal fun rectsAlike(a: Rect, b: Rect): Boolean =
  abs(a.left - b.left) <= FluidGlassPlaneTolerancePx && abs(a.top - b.top) <= FluidGlassPlaneTolerancePx &&
    abs(a.right - b.right) <= FluidGlassPlaneTolerancePx && abs(a.bottom - b.bottom) <= FluidGlassPlaneTolerancePx

private fun radiiAlike(a: FluidCornerRadii, b: FluidCornerRadii): Boolean =
  abs(a.topLeft - b.topLeft) <= FluidGlassPlaneTolerancePx && abs(a.topRight - b.topRight) <= FluidGlassPlaneTolerancePx &&
    abs(a.bottomRight - b.bottomRight) <= FluidGlassPlaneTolerancePx &&
    abs(a.bottomLeft - b.bottomLeft) <= FluidGlassPlaneTolerancePx

/**
 * Le lastre di un campo del palco in questo fotogramma, nelle coordinate del suo nodo (che comincia in [origin]), quanto
 * del campo c'è (`materialize`) e i pezzi che le danno, nello stesso ordine. Pura.
 *
 * Chi compare o scompare per lente è sempre un campo a sé (`GlassPlaneJourney.fields`, F3.2c): il suo `materialize` è
 * quello del campo, esatto, e la superficie lo applica con `glassMaterializeRecipe` (contorno, poi lente). Un campo di
 * vetri che viaggiano è pieno; un vetro che se ne va senza transizione (`Identity`) non c'è più dal fotogramma dopo.
 */
internal fun fluidGlassFieldSlabs(
  frame: GlassPlaneFrame,
  flights: List<GlassPlaneFlightId>,
  origin: androidx.compose.ui.geometry.Offset,
  smoothing: (GlassPlanePiece) -> Float,
): FluidGlassFieldShape {
  val members = frame.pieces.filter { it.flightId in flights }
  val lens = members.isNotEmpty() &&
    members.all { it.change == GlassPlaneChange.Materialize || it.change == GlassPlaneChange.Dematerialize }
  val amount = if (lens) members.maxOf { it.materialize } else 1f
  val drawn = (if (lens) members else members.filter { it.materialize > 0f }).unionsFirst { it.piece }
  val slabs = drawn.map {
    FluidForm.Slab(it.bounds.translate(-origin.x, -origin.y), it.radii, smoothing(it.piece))
  }
  return FluidGlassFieldShape(slabs, amount.fastCoerceIn(0f, 1f), drawn)
}

/**
 * Le unioni **stabili** dei vetri di un campo come indici (-1 per chi non ne ha), nell'ordine delle lastre: quelle che
 * il vetro ha prima e dopo il cambio, o con cui nasce o confluisce. Un'unione che in questo viaggio nasce o si scioglie
 * non è qui: il suo collo lo porta l'orologio dell'unione ([FluidGlassPlaneDriver.neckNow]), non è pieno. Null se
 * nessuno ha un'unione stabile. Pura.
 */
internal fun fluidGlassStableUnionIndices(members: List<GlassPlanePieceFrame>): IntArray? {
  fun unionOf(member: GlassPlanePieceFrame): Any? {
    val changing = member.flightId.sourceKey != null && member.flightId.targetKey != null &&
      member.unionFrom != member.unionTo
    return if (changing) null else member.piece.unionId
  }
  if (members.none { unionOf(it) != null }) return null
  val seen = HashMap<Any, Int>()
  return IntArray(members.size) { i -> unionOf(members[i])?.let { seen.getOrPut(it) { seen.size } } ?: -1 }
}

/**
 * Il **rigonfiamento** del viaggio in un fotogramma (punto 8 di F3.8a, col numero e la curva di F3.9): il livello
 * [level] (`GlassLiquidJourney.swell` alla fase, 0..1) del carattere [character], alla densità [density]. Ogni lato di un
 * pezzo esce di `glassJourneySwellDp` del suo lato corto, raggi compresi (`glassJourneySwollen`: una capsula resta una
 * capsula).
 *
 * [maxPx] è il tetto per lato ([FluidMotion.PlaneSwellMaxDp]): il rigonfiamento di Apple è misurato su tasti e barre
 * (15% del lato corto, cioè 3,6 dp per lato su un tasto da 48), e un pannello grande del piano (la card dell'assistente)
 * con la stessa frazione uscirebbe di decine di dp e toccherebbe i vicini. Sotto il tetto il numero è quello di F3.9.
 */
internal class FluidGlassSwell(
  val character: GlassLiquidJourney,
  val level: Float,
  val maxPx: Float,
  val density: Float,
) {
  /**
   * Di quanto esce ogni lato di una lastra col lato corto [shortPx], in pixel; negativo, di quanto rientra. [extra] è il
   * rigonfiamento **proprio** di quel pezzo (F3.12a, F3.12c: `GlassPlanePieceFrame.swell`, anche negativo), nelle stesse
   * unità del livello, e [share] la parte del livello comune che il pezzo prende (`GlassPlanePieceFrame.swellShare`): la
   * somma `share · level + extra`, col tetto [maxPx] da tutte e due le parti.
   */
  fun growPx(shortPx: Float, extra: Float = 0f, share: Float = 1f): Float {
    if (!(density > 0f)) return 0f
    val own = if (extra.isFinite()) extra else 0f
    val common = if (share.isFinite()) level * share.fastCoerceIn(0f, 1f) else level
    if (!(common > 0f) && own == 0f) return 0f
    val short = shortPx / density
    // Più chiamate: `glassJourneySwellDp` ferma il suo livello fra zero e uno, e la somma può superarlo o scendere sotto.
    var grow = glassJourneySwellDp(short, common, character)
    val sign = if (own < 0f) -1f else 1f
    var more = abs(own)
    while (more > 0f) {
      grow += sign * glassJourneySwellDp(short, min(more, 1f), character)
      more -= 1f
    }
    return (grow * density).fastCoerceIn(-maxPx, maxPx)
  }

  /**
   * [slab] rigonfiata, col rigonfiamento proprio [extra] del suo pezzo e la sua parte [share] del comune; più piccola se
   * la somma è negativa. A livello zero è [slab], la stessa istanza.
   */
  fun of(slab: FluidForm.Slab, extra: Float = 0f, share: Float = 1f): FluidForm.Slab {
    val grow = growPx(slab.frame.minDimension, extra, share)
    return if (grow >= 0f) glassJourneySwollen(slab, grow) else fluidGlassShrunk(slab, -grow)
  }

  companion object {
    /** Nessun rigonfiamento: a riposo, e nei campi che compaiono o scompaiono per lente. */
    val None = FluidGlassSwell(GlassLiquidJourneys.Off, 0f, 0f, 1f)
  }
}

/** Nessun pezzo premuto: il valore di serie di `fluidGlassStagePlane`. */
internal val NoPieceFramePress: (GlassPlanePieceFrame) -> FluidPressTransform? = { null }

/**
 * [slab] come la mostra la **pressione** [press] (giro di correzione di F3, R2 di C): scalata attorno al suo centro di
 * `scaleX`, `scaleY` e spostata della deriva, come il blocco del layer della pressione (`fluidPressTransform`) sposta e
 * scala la lastra ferma di un pezzo solo, attorno al centro del suo nodo. I raggi crescono col lato corto e mai oltre il
 * mezzo lato nuovo: una capsula premuta resta una capsula. Con [press] identità restituisce [slab]. Pura.
 */
internal fun fluidGlassPressedSlab(slab: FluidForm.Slab, press: FluidPressTransform): FluidForm.Slab {
  if (press.isIdentity) return slab
  val sx = if (press.scaleX.isFinite() && press.scaleX > 0f) press.scaleX else 1f
  val sy = if (press.scaleY.isFinite() && press.scaleY > 0f) press.scaleY else 1f
  val tx = if (press.translationX.isFinite()) press.translationX else 0f
  val ty = if (press.translationY.isFinite()) press.translationY else 0f
  val f = slab.frame
  val c = f.center
  val frame = Rect(
    c.x - f.width * sx / 2f + tx, c.y - f.height * sy / 2f + ty,
    c.x + f.width * sx / 2f + tx, c.y + f.height * sy / 2f + ty,
  )
  val half = frame.minDimension / 2f
  // I raggi seguono il lato corto: una capsula resta una capsula anche quando la pressione la allunga di più di traverso.
  val k = if (f.minDimension > 0f) frame.minDimension / f.minDimension else 1f
  val r = slab.cornerRadii
  return FluidForm.Slab(
    frame = frame,
    cornerRadii = FluidCornerRadii(
      topLeft = (r.topLeft * k).coerceIn(0f, half),
      topRight = (r.topRight * k).coerceIn(0f, half),
      bottomRight = (r.bottomRight * k).coerceIn(0f, half),
      bottomLeft = (r.bottomLeft * k).coerceIn(0f, half),
    ),
    smoothing = slab.smoothing,
  )
}

/**
 * La pressione di un pezzo del contenitore vista dal palco (giro di correzione di F3): la stessa trasformazione che il
 * blocco del layer della pressione ([fluidGlassPressLayer], il `pressLayer` dei tasti di vetro) applica alla lastra ferma e
 * al contenuto, letta quando serve con la taglia del pezzo. Il palco la porta sulla lastra del pezzo in volo e la lascia
 * andare con la sua molla, quella del rilascio della pressione: il tasto premuto non torna alla sua misura in un
 * fotogramma quando il viaggio parte (S4 di F3, `prove/tocco/rilascio.jpg`).
 *
 * [reducedMotion] è quello con cui il layer la disegna; null, quello che la pressione ha ricevuto dal tema.
 */
@Stable
internal class FluidGlassPressGeometry(private val press: FluidPressState, private val reducedMotion: Boolean? = null) {
  private val reduced: Boolean get() = reducedMotion ?: press.reducedMotion

  /** La trasformazione di adesso per un pezzo largo [width] e alto [height] (pixel, [density] pixel per dp). */
  fun at(width: Float, height: Float, density: Float): FluidPressTransform =
    fluidPressTransform(press.feedback, press.progress, press.lean, width, height, density, reduced)

  /**
   * Di quanto al più la pressione porta un lato fuori dal posto del pezzo, in pixel: a pressione piena, col dito dov'è.
   * È il margine che il nodo del palco aggiunge per un pezzo premuto: la pressione può ancora crescere mentre viaggia.
   */
  fun reachPx(width: Float, height: Float, density: Float): Float {
    val t = fluidPressTransform(press.feedback, max(1f, press.progress), press.lean, width, height, density, reduced)
    return max((t.scaleX - 1f) * width / 2f + abs(t.translationX), (t.scaleY - 1f) * height / 2f + abs(t.translationY))
      .coerceAtLeast(0f)
  }
}

/**
 * [slab] più piccola di [insetPx] su ogni lato, raggi compresi e mai oltre il mezzo lato nuovo: il contrario di
 * `glassJourneySwollen` (F3.12c, un pezzo che dopo il suo colmo va un poco sotto la sua misura). Mai sotto un pixel di
 * lato. Con [insetPx] zero restituisce [slab] stessa. Pura.
 */
internal fun fluidGlassShrunk(slab: FluidForm.Slab, insetPx: Float): FluidForm.Slab {
  if (!(insetPx > 0f) || !insetPx.isFinite()) return slab
  val f = slab.frame
  val inset = min(insetPx, (f.minDimension - 1f) / 2f).coerceAtLeast(0f)
  if (!(inset > 0f)) return slab
  val frame = Rect(f.left + inset, f.top + inset, f.right - inset, f.bottom - inset)
  val half = frame.minDimension / 2f
  val r = slab.cornerRadii
  return FluidForm.Slab(
    frame = frame,
    cornerRadii = FluidCornerRadii(
      topLeft = (r.topLeft - inset).coerceIn(0f, half),
      topRight = (r.topRight - inset).coerceIn(0f, half),
      bottomRight = (r.bottomRight - inset).coerceIn(0f, half),
      bottomLeft = (r.bottomLeft - inset).coerceIn(0f, half),
    ),
    smoothing = slab.smoothing,
  )
}

/**
 * La forma di un campo del palco in un fotogramma (F3.8a): le lastre di [flights] al fotogramma [frame], rigonfiate di
 * [swell], col collo [neck], le unioni stabili, la comparsa del campo e il peso del vetro in viaggio dalla velocità del
 * lato più veloce (`physicsPlaneMotion`). È ciò che il contenitore dà alla superficie fisica di ogni campo a ogni
 * fotogramma, e ciò che le tracce del banco rendono. Pura.
 *
 * [journeyLight] è la luce del carattere del viaggio (F3.9), che la forma porta alla ricetta (`glassJourneyRecipe`,
 * agganciata da F3.10); [touch] la luce del dito del campo ([fluidGlassFingerTouch]); [journeyGlow] il livello
 * dell'**alone** (F3.12c, `GlassLiquidJourney.glow`: sale con la luce e si spegne prima). Di serie zero, null e l'alone
 * sulla luce: la forma di prima, al bit.
 *
 * Giro di correzione di F3: la **vita della goccia** (`GlassPlanePieceFrame.birthWaist`, la più stretta dei pezzi del
 * campo) stringe la sola parte positiva del collo, `neck · vita²` (la vita del collo è la radice della coordinata): lo zero,
 * cioè la rottura, resta dov'è. E [pressed] dà la **pressione** di un pezzo in volo (`FluidPressTransform`, la stessa del
 * blocco del layer del vetro fermo), che la sua lastra porta sopra il rigonfiamento: il palco parte dal tasto premuto e lo
 * rilascia con la molla della pressione (R2 di C, rilievo 1 di A). Di serie nessuna.
 */
internal fun fluidGlassStagePlane(
  frame: GlassPlaneFrame,
  flights: List<GlassPlaneFlightId>,
  origin: Offset,
  smoothing: (GlassPlanePiece) -> Float,
  spacingPx: Float,
  neck: Float,
  swell: FluidGlassSwell,
  density: Float,
  movingMs: Float,
  journeyLight: Float = 0f,
  touch: PhysicsPlaneTouch? = null,
  journeyGlow: Float = journeyLight,
  pressed: (GlassPlanePieceFrame) -> FluidPressTransform? = NoPieceFramePress,
): PhysicsPlaneShape {
  val shape = fluidGlassFieldSlabs(frame, flights, origin, smoothing)
  // Il lato più veloce del campo, in dp al secondo.
  val speedPx = shape.members.maxOfOrNull { p ->
    val v = p.velocity.bounds
    maxOf(maxOf(abs(v.left), abs(v.right)), maxOf(abs(v.top), abs(v.bottom)))
  } ?: 0f
  val slabs = shape.slabs.take(FluidForm.Group.MaxPieces)
  // Il rigonfiamento comune del viaggio, nella parte che ogni pezzo prende, più il suo proprio (F3.12a, F3.12c: chi nasce,
  // chi confluisce, il donatore e chi accoglie hanno il loro).
  val swollen = swell.level > 0f || shape.members.any { it.swell != 0f }
  // La pressione di chi è premuto (R2 di C): null per tutti, quasi sempre.
  var presses: Array<FluidPressTransform?>? = null
  for (index in slabs.indices) {
    val press = pressed(shape.members[index])?.takeUnless { it.isIdentity } ?: continue
    val out = presses ?: arrayOfNulls<FluidPressTransform>(slabs.size)
    out[index] = press
    presses = out
  }
  // La vita della goccia: la più stretta del campo, sulla sola parte positiva del collo.
  val waist = shape.members.minOfOrNull { it.birthWaist }?.takeIf { it.isFinite() }?.fastCoerceIn(0f, 1f) ?: 1f
  val shapedNeck = if (neck > 0f && waist < 1f) neck * waist * waist else neck
  val pressing = presses
  return PhysicsPlaneShape(
    slabs = if (swollen || pressing != null) slabs.mapIndexed { index, slab ->
      val member = shape.members[index]
      val grown = if (swollen) swell.of(slab, member.swell, member.swellShare) else slab
      pressing?.get(index)?.let { fluidGlassPressedSlab(grown, it) } ?: grown
    } else slabs,
    nominalBlend = spacingPx,
    neck = shapedNeck,
    unions = fluidGlassStableUnionIndices(shape.members),
    materialize = shape.materialize,
    motion = physicsPlaneMotion(speedPx / density, movingMs),
    journeyLight = journeyLight,
    touch = touch,
    journeyGlow = journeyGlow,
  )
}

/**
 * Come arriva il vetro di un pezzo, per il suo contenuto: vedi [glassPieceContentFor]. [relay] è
 * `GlassPlanePieceFrame.relay`: il vetro che confluisce in chi nasce da lui (la staffetta) è lo stesso vetro che cambia
 * identità, e il suo contenuto ha i tempi di un cambio di contesto; chi confluisce in un vicino vero ha i suoi (F3.12c).
 * Pura.
 */
internal fun fluidGlassArrivalOf(change: GlassPlaneChange, relay: Boolean = false): GlassPieceArrival = when (change) {
  GlassPlaneChange.BornFromNeighbour -> GlassPieceArrival.BornFromNeighbour
  // Per chi se ne va (F3.10): il simbolo di chi scompare per lente ha i tempi della scomparsa, non del cambio di contesto.
  GlassPlaneChange.Materialize, GlassPlaneChange.Dematerialize -> GlassPieceArrival.ThroughLens
  GlassPlaneChange.Identity -> GlassPieceArrival.Instant
  GlassPlaneChange.MergeIntoNeighbour -> if (relay) GlassPieceArrival.Stays else GlassPieceArrival.MergesIntoNeighbour
  else -> GlassPieceArrival.Stays
}

/**
 * Come si disegna in questo fotogramma il contenuto di un pezzo che sta in [place] (nel contenitore), col vetro [flying],
 * la parte [role] e l'aspetto [look] (F3.8a). Null per un contenuto fermo e nitido: è quello di riposo, senza ritaglio.
 *
 * **Ogni contenuto viaggia col suo vetro** (F3.12a): è spostato di quanto l'ancora della lastra è lontana dal posto
 * in cui il contenuto è stato composto. Fino a F3.11 valeva solo per chi resta (R7 A3: «nitido, si sposta col suo
 * vetro»), e il contenuto di un pezzo che nasce stava fermo al posto d'arrivo mentre il vetro gli rimbalzava attorno;
 * in Apple i simboli stanno dentro il vetro e fanno il suo rimbalzo (`sessioni/f3-12a/misure.md`: superano il loro
 * posto di 0,15–0,20 D e tornano, e quelli di chi confluisce entrano nel vicino col loro vetro mentre svaniscono), e
 * **non cambiano misura** (la larghezza di un simbolo resta entro l'1%): il contenuto trasla, non si stira (invariante
 * 23). Chi compare o scompare per lente non si sposta: la sua lastra cresce attorno al centro. Tutti sono ritagliati
 * sulla lastra in volo, rigonfiata di [swell] (col rigonfiamento in più del pezzo) come la disegna il palco: mai un
 * contenuto fuori dal suo vetro. Di serie [anchor] è il centro, identico a F3.12a; un bordo (F4A.2b) conserva invece
 * il posto d'arrivo delle icone di un gruppo che cambia larghezza, salvo il rimbalzo di quel bordo. Pura.
 */
internal fun fluidGlassContentFlightOf(
  flying: GlassPlanePieceFrame,
  role: GlassPieceContent,
  look: GlassContentLook,
  place: Rect,
  swell: FluidGlassSwell,
  /**
   * La pressione del pezzo (giro di correzione di F3): il ritaglio è la lastra premuta, come la disegna il palco. Lo
   * spostamento no: la scala e la deriva della pressione il contenuto le ha già nel suo layer.
   */
  press: FluidPressTransform? = null,
  anchor: FluidGlassContentAnchor = FluidGlassContentAnchor.Center,
  layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): FluidGlassContentFlight? {
  if (flying.still && look == GlassContentLook.Sharp) return null
  val lens = flying.change == GlassPlaneChange.Materialize || flying.change == GlassPlaneChange.Dematerialize
  val slab = FluidForm.Slab(flying.bounds, flying.radii)
  val grown = if (lens) slab else swell.of(slab, flying.swell, flying.swellShare)
  val drawn = if (press != null && !press.isIdentity) fluidGlassPressedSlab(grown, press) else grown
  // Un contenuto che non si vede non ha un posto: niente spostamento da ricordare per quando comparirà.
  val rides = !lens && role.role != GlassPieceContentRole.Hidden
  val offset = if (!rides) Offset.Zero else when (anchor) {
    FluidGlassContentAnchor.Center -> flying.bounds.center - place.center
    else -> {
      val left = (anchor == FluidGlassContentAnchor.Start) == (layoutDirection == LayoutDirection.Ltr)
      Offset(
        if (left) flying.bounds.left - place.left else flying.bounds.right - place.right,
        flying.bounds.center.y - place.center.y,
      )
    }
  }
  return FluidGlassContentFlight(
    look = look,
    offset = offset,
    clip = drawn.frame.translate(-place.left, -place.top),
    radii = drawn.cornerRadii,
  )
}

/**
 * I grappoli che in un viaggio da [origin] a [target] **restano al vetro di riposo** (F3.8a): quelli che c'erano uguali
 * prima (gli stessi pezzi) e i cui pezzi sono tutti fermi ([still], `GlassPlaneJourney.stillKeys`). Il palco non li
 * prende: nessun passaggio di consegne per chi non cambia, e nessun collo fra due pezzi fermi solo perché un terzo, più
 * in là, compare. Pura.
 */
internal fun fluidGlassRestingClusters(
  origin: GlassPlaneState,
  target: GlassPlaneState,
  still: Set<Any>,
): List<GlassPlaneCluster> {
  val before = origin.clusters.mapTo(HashSet()) { it.id }
  return target.clusters.filter { cluster -> cluster.id in before && cluster.pieces.all { it.key in still } }
}

/** La chiave della superficie di riposo di [cluster]: il pezzo, se è solo, altrimenti l'identità del grappolo. */
internal fun fluidGlassRestKey(cluster: GlassPlaneCluster): Any =
  if (cluster.pieces.size == 1) cluster.pieces.first().key else cluster.id

/** Le lastre di un campo, la sua comparsa e i pezzi che le danno: vedi [fluidGlassFieldSlabs]. */
internal class FluidGlassFieldShape(
  val slabs: List<FluidForm.Slab>,
  val materialize: Float,
  val members: List<GlassPlanePieceFrame>,
)

/**
 * [this] con i pezzi di un'unione in testa, nell'ordine di prima (un ordinamento stabile). Il campo fonde ogni pezzo con
 * tutto ciò che lo precede con un raggio solo: con le unioni davanti, il loro collo pieno non incontra mai un pezzo
 * estraneo già fuso, e non deve fermarsi per non fargli ponte (`physicsPlaneNeckBlend`). Le unioni non cambiano durante un
 * viaggio, quindi l'ordine è lo stesso in ogni fotogramma. Pura.
 */
internal inline fun <T> List<T>.unionsFirst(crossinline piece: (T) -> GlassPlanePiece): List<T> =
  if (none { piece(it).unionId != null }) this else sortedBy { if (piece(it).unionId != null) 0 else 1 }

/**
 * Le unioni di [pieces] come indici (-1 per chi non ne ha), nell'ordine delle lastre: ciò che `PhysicsPlaneShape.unions`
 * vuole. Null se nessuno ha un'unione. Pura.
 */
internal fun fluidGlassUnionIndices(pieces: List<GlassPlanePiece>): IntArray? {
  if (pieces.none { it.unionId != null }) return null
  val seen = HashMap<Any, Int>()
  return IntArray(pieces.size) { i -> pieces[i].unionId?.let { seen.getOrPut(it) { seen.size } } ?: -1 }
}

/** La forma di Fluid-physics di un insieme di lastre: una lastra da sola, un gruppo se sono di più. */
internal fun fluidGlassFormOf(slabs: List<FluidForm.Slab>, blendRadius: Float): FluidForm? = when {
  slabs.isEmpty() -> null
  slabs.size == 1 -> slabs.single()
  else -> FluidForm.Group(slabs.take(FluidForm.Group.MaxPieces), blendRadius)
}

// ================================================================================================================
// Lo stato del contenitore
// ================================================================================================================

/** Il contenitore in cui ci si trova, o null. Lo fornisce [FluidGlassContainer]. */
internal val LocalFluidGlassContainer = staticCompositionLocalOf<FluidGlassContainerState?> { null }

/**
 * Come si disegna il vetro di un pezzo: lo scrive il pezzo in composizione, lo usano le superfici del contenitore.
 * Le lambda e gli stati sono quelli del pezzo, ricordati: un aspetto uguale non ricompone niente.
 */
@Immutable
internal data class FluidGlassLook(
  val backdrop: GlassBackdropState,
  val tint: GlassTint,
  val role: GlassRole,
  /** Già passata da `sanitized()`. */
  val optics: GlassOptics,
  val variant: GlassLiquidVariant,
  /** La forma come l'ha data l'app: per una lastra ferma non concentrica la si disegna così com'è. */
  val shape: Shape,
  val press: FluidPressState? = null,
  val touch: GlassTouchLight? = null,
  val layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
  /**
   * La pressione come la vede il palco (giro di correzione di F3): la stessa trasformazione di [layerBlock], che il palco
   * porta sulla lastra del pezzo in volo. Null per chi non reagisce al dito.
   */
  val pressGeometry: FluidGlassPressGeometry? = null,
  /**
   * Dove la superficie del pezzo pubblica il proprio materiale, per i vetri annidati nel suo contenuto (F3.2e, un
   * [fluidGlassPanel]); null per gli altri pezzi, come prima.
   */
  val exports: GlassBackdropState? = null,
  /** Ogni quanto, al massimo, la superficie rifà la cattura: vedi `glassSurface`. Zero, «quando serve», di serie. */
  val resampleIntervalMillis: Long = 0L,
  /**
   * Se il pezzo dichiara di essere un **grande** che scurisce in continuo (vero) o un piccolo che gira (falso), F3.2e: un
   * [fluidGlassPanel] lo dice sempre (il suo `large`, o il suo ruolo). Null per gli altri pezzi: decide la taglia, come
   * prima ([fluidGlassContainerLarge]).
   */
  val large: Boolean? = null,
)

/**
 * Se il contenitore è un grande che scurisce in continuo: ciò che ha dichiarato un pezzo del grappolo più grande
 * ([declared], un pannello), altrimenti la taglia del suo lato corto più grande ([shortSideDp]: dalla metà della rampa
 * dei grandi della ricetta in su). Pura.
 */
internal fun fluidGlassContainerLarge(declared: Boolean?, shortSideDp: Float): Boolean =
  declared ?: (glassLiquidLargeVeilGain(shortSideDp) >= 0.5f)

/**
 * Un pezzo del contenitore: un `fluidGlass`, un `fluidGlassPanel` o un `glassControlSurface`. È la chiave del registro, e porta ciò che il
 * piano non sa (come si disegna) e ciò che il layout gli dice (dove sta, i raggi concentrici).
 */
@Stable
internal class FluidGlassPiece(val container: FluidGlassContainerState) {
  var look: FluidGlassLook? = null
    set(value) {
      if (field != value) {
        field = value
        container.onLookChanged()
      }
    }

  var attributes: FluidGlassAttributes = FluidGlassAttributes.None
    set(value) {
      if (field != value) {
        field = value
        container.invalidate()
      }
    }

  /** Il nodo del pezzo, dal posizionamento. */
  var coordinates: LayoutCoordinates? = null

  /** I raggi concentrici del nodo, se la forma è una [FluidConcentricShape]: li scrive il nodo al layout. */
  var concentricRadii: FluidCornerRadii? = null

  /**
   * Il **contenuto** del pezzo nel viaggio del suo vetro (F3.8a): la sua registrazione e l'aspetto con cui si disegna.
   * Vedi [FluidGlassPieceContent].
   */
  val content = FluidGlassPieceContent()

  override fun toString(): String = "FluidGlassPiece(${attributes.id?.id ?: Integer.toHexString(hashCode())})"
}

/**
 * Come si disegna il contenuto di un pezzo in un fotogramma del viaggio: l'aspetto (opacità, scala, sfocatura), di quanto
 * è spostato dal suo posto (chi resta viaggia col suo vetro), e la sagoma del vetro in volo su cui è ritagliato, nelle
 * coordinate del pezzo (null: nessun ritaglio).
 */
@Immutable
internal class FluidGlassContentFlight(
  val look: GlassContentLook,
  val offset: Offset = Offset.Zero,
  val clip: Rect? = null,
  val radii: FluidCornerRadii = FluidCornerRadii.Zero,
) {
  companion object {
    /** Il contenuto di un pezzo che non si vede ancora. */
    val Hidden = FluidGlassContentFlight(GlassContentInvisible)
  }
}

/**
 * Il contenuto di un pezzo (F3.8a): **si vede solo dentro il suo vetro**, ed entra ed esce con la grammatica del
 * contenuto (`glassPieceContentLook`), sulla fase del volo del suo vetro.
 *
 * Il pezzo registra il contenuto in un layer ([layer]) a ogni disegno, e lo disegna da lì: a riposo com'è, in viaggio con
 * l'aspetto di [flight] e ritagliato sulla sagoma del vetro in volo. Il layer è del contenitore, non del nodo: quando il
 * pezzo esce dalla composizione il suo contenuto resta disegnabile, e il contenitore lo fa svanire dov'era mentre il
 * vetro se ne va (R7 A5: «chi esce resta dov'era e viene coperto dal bordo che si ritira»). Una registrazione tiene per
 * riferimento i layer dei figli, anche dopo che i loro nodi sono stati rilasciati.
 */
@Stable
internal class FluidGlassPieceContent {
  /** La registrazione del contenuto, più larga del pezzo di [marginPx] per lato (una sfocatura non si taglia al bordo). */
  var layer: GraphicsLayer? = null
    private set
  private var graphics: GraphicsContext? = null

  /**
   * La registrazione di riserva (giro di correzione di F3, D 6): quando il pezzo ha già la misura o il posto nuovi e il
   * vetro li prenderà al fotogramma dopo, si mostra la registrazione di prima e la nuova si scrive qui. Vedi [hold].
   */
  private var spare: GraphicsLayer? = null

  /** Vero finché il nodo del pezzo c'è: una registrazione rieseguita dopo (un canvas senza accelerazione) non disegna. */
  var attached: Boolean = false

  /** Dove sta il pezzo nel contenitore, all'ultima misura: è lì che il suo contenuto esce quando il pezzo se ne va. */
  var place: Rect = Rect.Zero

  /** La parte che il contenuto fa nel viaggio in corso, e l'ultimo aspetto calcolato. */
  var role: GlassPieceContent = GlassPieceContent.Rest
  var look: GlassContentLook = GlassContentLook.Sharp

  /** L'aspetto di adesso, letto in disegno: null a riposo (nitido, al suo posto, senza ritaglio). Si scrive con [show]. */
  val flight = mutableStateOf<FluidGlassContentFlight?>(null)

  /**
   * Vero se il contenuto adesso non si vede ([fluidGlassContentHidden]): chi nasce, finché il suo contenuto non compare.
   * Il palco non gli porta una pressione (giro di correzione di F3).
   */
  var hidden: Boolean = false
    private set

  /** Scrive l'aspetto di adesso, e [hidden]. */
  fun show(next: FluidGlassContentFlight?) {
    if (flight.value != next) flight.value = next
    hidden = fluidGlassContentHidden(next)
  }

  /** Il layer, creato alla prima registrazione col contesto grafico di chi disegna. */
  fun obtain(context: GraphicsContext): GraphicsLayer =
    layer ?: context.createGraphicsLayer().also {
      layer = it
      graphics = context
    }

  /** Vero se c'è una registrazione da disegnare dopo che il pezzo se n'è andato. */
  val recorded: Boolean get() = layer != null

  /** Riposo: nitido, al suo posto. */
  fun rest() {
    role = GlassPieceContent.Rest
    look = GlassContentLook.Sharp
    show(null)
  }

  /** Non si vede, finché il piano non decide come entra. */
  fun hide() {
    role = GlassPieceContent.Hidden
    look = GlassContentInvisible
    show(FluidGlassContentFlight.Hidden)
  }

  /**
   * Il fotogramma in cui il pezzo ha la misura o il posto nuovi ma il vetro mostra ancora quelli di prima (lo segue al
   * passo dopo): la registrazione **di prima** resta quella da mostrare, e quella di adesso si scrive nella riserva, che
   * diventa la più recente. Restituisce la registrazione da mostrare. Così contenuto e vetro cambiano nello stesso
   * fotogramma, un fotogramma dopo il layout, invece del contenuto un fotogramma prima del vetro (il «Chiedi qualcosa…»
   * del dock dell'assistente fuori dal vetro per ~41 ms, S4 di F3).
   */
  fun hold(context: GraphicsContext, record: (GraphicsLayer) -> Unit): GraphicsLayer {
    val shown = layer ?: return obtain(context).also(record)
    val fresh = spare ?: context.createGraphicsLayer()
    record(fresh)
    layer = fresh
    spare = shown
    return shown
  }

  /** Rilascia la registrazione: il pezzo non c'è più e il suo contenuto non si vede più. */
  fun release() {
    val other = spare
    spare = null
    if (other != null) graphics?.releaseGraphicsLayer(other)
    val current = layer ?: return
    layer = null
    graphics?.releaseGraphicsLayer(current)
    graphics = null
  }
}

/** Un posto per una superficie del contenitore: il suo riquadro nel contenitore, in pixel. */
@Immutable
internal data class FluidGlassFieldSlot(val bounds: Rect)

/** Un grappolo a riposo e come si disegna. */
@Immutable
internal class FluidGlassRestField(
  val key: Any,
  /** Il riquadro del nodo della superficie, nel contenitore. */
  val node: Rect,
  val look: FluidGlassLook,
  /** Le lastre, nelle coordinate del nodo. */
  val slabs: List<FluidForm.Slab>,
  /** Per un grappolo di un pezzo: la forma da disegnare (quella dell'app, o la concentrica risolta). */
  val shape: Shape?,
  /** Per un grappolo di più pezzi: la forma del piano ferma, con le unioni (una sagoma sola) e il collo a riposo. */
  val plane: PhysicsPlaneShape? = null,
  /** Dove il grappolo pubblica il suo materiale: quello del primo suo pezzo che esporta (F3.2e), o null. */
  val exports: GlassBackdropState? = look.exports,
  /**
   * Le tinte di un grappolo di più pezzi che non hanno tutti la stessa pellicola (F4A.2, review di F3 D R4): una
   * superficie per tinta, ognuna sulla sua striscia ([fluidGlassTintStrips]). Null, il caso di sempre: una tinta sola.
   */
  val tints: FluidGlassTintSplit? = null,
)

/**
 * Le tinte di un grappolo, o di un campo del palco, i cui pezzi **non hanno tutti la stessa pellicola** (F4A.2, review
 * di F3 D R4). Fino a F4A.1 un grappolo si disegnava con la tinta del primo pezzo: la selezione di un tasto di un
 * blocco unito non si vedeva (o tingeva tutto il blocco), e l'azione principale tinta accanto a un gruppo, a 12 dp, cioè
 * nello stesso grappolo, perdeva la sua tinta o la dava al gruppo. Ora ogni tinta ha la sua superficie, ritagliata sulla
 * striscia dei suoi pezzi: la sagoma resta una (stesse lastre, stesso collo, stessa unione), cambia solo la pellicola, e
 * fra due pezzi distinti il taglio cade nel varco, dove non c'è vetro.
 *
 * [looks] una per tinta, nell'ordine in cui compaiono fra i pezzi (la prima è quella del primo); [slabTints] la tinta di
 * ogni lastra, nell'ordine delle lastre. Il confronto è sulla sola **tinta** ([GlassTint]): ruolo e ottica di pezzi con la
 * stessa tinta restano quelli del primo, com'erano, così i grappoli di oggi (un `fluidGlass` accanto a un tasto, stessa
 * pellicola di serie) restano al bit.
 */
@Immutable
internal class FluidGlassTintSplit(val looks: List<FluidGlassLook>, val slabTints: IntArray) {
  override fun equals(other: Any?): Boolean =
    other is FluidGlassTintSplit && looks == other.looks && slabTints.contentEquals(other.slabTints)

  override fun hashCode(): Int = 31 * looks.hashCode() + slabTints.contentHashCode()
}

/**
 * Le tinte di [looks] (una per lastra, nell'ordine delle lastre), o null se la tinta è una sola: il grappolo si disegna
 * com'era. Pura.
 */
internal fun fluidGlassTintSplit(looks: List<FluidGlassLook>): FluidGlassTintSplit? {
  if (looks.size < 2) return null
  val first = looks.first().tint
  if (looks.all { it.tint == first }) return null
  val distinct = ArrayList<FluidGlassLook>()
  val indices = IntArray(looks.size) { index ->
    val at = distinct.indexOfFirst { it.tint == looks[index].tint }
    if (at >= 0) {
      at
    } else {
      distinct += looks[index]
      distinct.size - 1
    }
  }
  return FluidGlassTintSplit(distinct, indices)
}

/**
 * La tinta è contenuto (R7 A4): chi nasce la prende dal donatore, chi confluisce la cede a chi lo accoglie, nella
 * finestra della **sua** sfocatura. Le lastre possono così coincidere senza tagliare una capsula in due colori.
 * Conserva il colore già mostrato quando un viaggio viene interrotto, come fa il conducente con la geometria.
 */
internal class FluidGlassTintJourney {
  private var starts: Map<GlassPlaneFlightId, GlassTint> = emptyMap()
  private var ends: Map<GlassPlaneFlightId, GlassTint> = emptyMap()
  private val shown = HashMap<Any, GlassTint>()

  fun rest(tints: Map<Any, GlassTint>) {
    shown.clear()
    shown.putAll(tints)
  }

  fun start(frame: GlassPlaneFrame, targets: Map<Any, GlassTint>) {
    fun before(key: Any?): GlassTint? = shown[key] ?: targets[key]
    starts = frame.pieces.mapNotNull { flying ->
      val id = flying.flightId
      val tint = before(id.sourceKey) ?: before(flying.bornFrom) ?: targets[id.targetKey]
      tint?.let { id to it }
    }.toMap()
    ends = frame.pieces.mapNotNull { flying ->
      val id = flying.flightId
      val tint = targets[id.targetKey] ?: targets[flying.mergesInto] ?: starts[id]
      tint?.let { id to it }
    }.toMap()
  }

  fun frame(frame: GlassPlaneFrame): Map<GlassPlaneFlightId, GlassTint> = frame.pieces.mapNotNull { flying ->
    val id = flying.flightId
    val from = starts[id] ?: return@mapNotNull null
    val to = ends[id] ?: from
    val u = if (flying.relay) flying.phase / GlassPieceContentDefaults.ExitSpan else when (flying.change) {
      GlassPlaneChange.BornFromNeighbour -> glassPieceBornU(flying.contentPhase)
      GlassPlaneChange.MergeIntoNeighbour -> flying.contentPhase / if (flying.relay)
        GlassPieceContentDefaults.ExitSpan else GlassPieceContentDefaults.MergeExitSpan
      else -> flying.contentPhase
    }
    val tint = fluidGlassMixTint(from, to, GlassContentCurves.tint(u))
    (id.targetKey ?: id.sourceKey)?.let { shown[it] = tint }
    id to tint
  }.toMap()
}

/** Il neutro non ha pellicola nel materiale nuovo: la tinta colorata si spegne in alfa sopra lo stesso velo. */
internal fun fluidGlassMixTint(from: GlassTint, to: GlassTint, amount: Float): GlassTint {
  if (amount <= 0f || from == to) return from
  if (amount >= 1f) return to
  val aNeutral = glassTintLiquidMode(from) == GlassTintMode.Neutral
  val bNeutral = glassTintLiquidMode(to) == GlassTintMode.Neutral
  val a = if (aNeutral) to.overlay.copy(alpha = 0f) else from.overlay
  val b = if (bNeutral) from.overlay.copy(alpha = 0f) else to.overlay
  return GlassTint(lerp(a, b, amount), lerp(from.fallback, to.fallback, amount),
    lerp(from.hairline, to.hairline, amount),
    if (aNeutral || bNeutral) GlassTintMode.Colored else glassTintLiquidMode(to))
}

/** Fin dove si estende una striscia di tinta fuori dalle lastre: il ritaglio non deve mai mordere ombra o alone. */
private const val FluidGlassTintStripFar = 100_000f

/**
 * Dove si disegna la tinta [tint] di un campo di lastre [slabs] (nelle coordinate della superficie) con le tinte
 * [slabTints]: le strisce dei suoi pezzi lungo l'asse della riga (quello in cui i centri sono più sparsi), tagliate a metà
 * fra il bordo di un pezzo e quello del vicino di un'altra tinta, e senza fine sull'altro asse e oltre i pezzi estremi.
 *
 * Fra due pezzi distinti a riposo il taglio cade nel varco, dove non c'è vetro: ogni pezzo ha la sua pellicola, intera.
 * In un'unione, o in volo col collo, cade nel collo: la tinta cambia lì, come la selezione dentro una capsula di Apple.
 * Chi nasce dentro il donatore porta la sua tinta da metà della parte in cui si sovrappongono. Pura.
 */
internal fun fluidGlassTintStrips(slabs: List<Rect>, slabTints: IntArray, tint: Int): List<Rect> {
  val count = minOf(slabs.size, slabTints.size)
  if (count == 0) return emptyList()
  var minX = Float.POSITIVE_INFINITY
  var maxX = Float.NEGATIVE_INFINITY
  var minY = Float.POSITIVE_INFINITY
  var maxY = Float.NEGATIVE_INFINITY
  for (index in 0 until count) {
    val center = slabs[index].center
    minX = minOf(minX, center.x)
    maxX = maxOf(maxX, center.x)
    minY = minOf(minY, center.y)
    maxY = maxOf(maxY, center.y)
  }
  val horizontal = maxX - minX >= maxY - minY
  fun center(rect: Rect) = if (horizontal) rect.center.x else rect.center.y
  fun start(rect: Rect) = if (horizontal) rect.left else rect.top
  fun end(rect: Rect) = if (horizontal) rect.right else rect.bottom
  val order = (0 until count).sortedBy { center(slabs[it]) }
  val strips = ArrayList<Rect>()
  var from = -FluidGlassTintStripFar
  for (position in order.indices) {
    val here = order[position]
    val next = order.getOrNull(position + 1)
    // Una striscia finisce dove comincia un vicino di un'altra tinta; fra due della stessa tinta non c'è taglio.
    if (next != null && slabTints[next] == slabTints[here]) continue
    val to = if (next == null) FluidGlassTintStripFar else (end(slabs[here]) + start(slabs[next])) / 2f
    if (slabTints[here] == tint && to > from) {
      strips += if (horizontal) {
        Rect(from, -FluidGlassTintStripFar, to, FluidGlassTintStripFar)
      } else {
        Rect(-FluidGlassTintStripFar, from, FluidGlassTintStripFar, to)
      }
    }
    from = maxOf(from, to)
  }
  return strips
}

/** Disegna il contenuto solo dentro [strips] (vedi [fluidGlassTintStrips]); null o vuote: niente ritaglio. */
private fun ContentDrawScope.drawInStrips(strips: List<Rect>?) {
  when {
    strips == null -> drawContent()
    strips.isEmpty() -> Unit
    strips.size == 1 -> {
      val strip = strips.single()
      clipRect(strip.left, strip.top, strip.right, strip.bottom) { this@drawInStrips.drawContent() }
    }
    else -> {
      val path = Path().apply { strips.forEach { addRect(it) } }
      clipPath(path) { this@drawInStrips.drawContent() }
    }
  }
}

/** Un campo del palco. */
@Stable
internal class FluidGlassStageField(
  val index: Int,
  val node: Rect,
  val look: FluidGlassLook,
  val flights: List<GlassPlaneFlightId>,
  /** Dove il campo pubblica il suo materiale mentre porta il vetro (F3.2e): vedi [FluidGlassRestField.exports]. */
  val exports: GlassBackdropState? = look.exports,
  /** Se nel campo un'unione nasce o si scioglie: il suo collo è quello dell'unione (F3.8a). */
  val union: GlassPlaneUnionChange = GlassPlaneUnionChange.None,
  /** Vero per un campo di vetri che compaiono o scompaiono per lente: non si gonfia. */
  val lens: Boolean = false,
  /**
   * Le tinte dei pezzi del campo, se non sono una sola (F4A.2): una superficie per tinta, ritagliata a ogni fotogramma
   * sulle strisce dei suoi pezzi in volo. La tinta di ogni lastra la scrive il passo ([FluidGlassContainerState.stageTintStrips]).
   */
  val tints: List<FluidGlassLook>? = null,
) {
  /** Gli aspetti del fotogramma: le sole pellicole cambiano con la sfocatura, la geometria resta quella del palco. */
  var liveTints: List<FluidGlassLook> by mutableStateOf(tints ?: listOf(look))
}

/**
 * Il palco di un viaggio: cambia quando il viaggio parte o riparte, non a ogni fotogramma. [resting] sono le chiavi delle
 * superfici di riposo che **tengono il vetro** per tutto il viaggio (F3.8a): i grappoli che non cambiano.
 */
@Immutable
internal class FluidGlassStage(
  val revision: Int,
  val fields: List<FluidGlassStageField>,
  val resting: Set<Any> = emptySet(),
)

/**
 * Lo stato di un [FluidGlassContainer]: il registro dei pezzi, il conducente ([FluidGlassPlaneDriver]) e ciò che le
 * superfici leggono in composizione ([restFields], [stage]). Gira su un orologio solo, quello dei fotogrammi: un
 * pezzo che cambia avvisa ([invalidate]), il passo del fotogramma dopo misura e decide.
 */
@Stable
internal class FluidGlassContainerState {
  private val pieces = LinkedHashSet<FluidGlassPiece>()

  /**
   * Il registro del piano (F3.1): ogni pezzo posato ci sta con la sua chiave, riquadro, raggi, variante, identità,
   * unione e transizione; lo stato del piano è la sua fotografia. Chi non è ancora posato non c'è.
   */
  private val registry = GlassPlaneRegistry()
  private val driver = FluidGlassPlaneDriver(firstStepCapNanos = FluidMotion.PlaneFirstStepMillis * 1_000_000L)
  private val wake = Channel<Unit>(Channel.CONFLATED)

  /** Il contesto grafico in cui si registra il contenuto dei pezzi: lo dà il contenitore in composizione. */
  var graphics: GraphicsContext? = null

  /**
   * I pezzi usciti dalla composizione il cui contenuto si vede ancora (F3.8a): il contenitore lo disegna dov'era, dal
   * fotogramma in cui il pezzo se ne va (allora com'è: il vetro di riposo c'è ancora) e poi mentre svanisce col suo
   * vetro. Letto in disegno.
   */
  private var departed by mutableStateOf<List<FluidGlassPiece>>(emptyList())
  private val departedPath = Path()

  /** Gli aspetti dei pezzi per chiave del registro, compresi quelli che se ne sono andati e viaggiano ancora. */
  private val looks = HashMap<Any, FluidGlassLook>()

  /** Le superfici fisiche dei campi del palco, per indice: il passo del fotogramma le guida. */
  private val stagePhysics = HashMap<Int, FluidPhysicsState>()

  private var density: Density = Density(1f)
  private var layoutDirection: LayoutDirection = LayoutDirection.Ltr
  private var spacingPx: Float = 0f
  private var motion: GlassPlaneMotion? = GlassPlaneMotion()

  /** Il nodo del contenitore. */
  var coordinates: LayoutCoordinates? = null

  /** Se il contenitore disegna il vetro dei pezzi (il materiale nuovo). Letto in composizione dai pezzi. */
  var renders: Boolean by mutableStateOf(false)

  /** Lo stato adattivo del contenitore, se misura: i tasti dell'engine che ci stanno dentro ci legano il loro inchiostro. */
  var adaptive: GlassAdaptiveState? = null

  /** Se il grappolo più grande è un grande (scurisce in continuo invece di girare). */
  var large: Boolean by mutableStateOf(false)
    private set

  /** I grappoli a riposo (o d'arrivo, durante un viaggio). Letto in composizione. */
  var restFields: List<FluidGlassRestField> by mutableStateOf(emptyList())
    private set

  /** Il palco del viaggio in corso, o null. Letto in composizione: chi porta il vetro si decide qui. */
  var stage: FluidGlassStage? by mutableStateOf(null)
    private set

  /**
   * La forma di ogni campo del palco (F3.2c): scritta a ogni fotogramma dal passo del contenitore, letta in disegno dalla
   * superficie fisica del campo ([FluidPhysicsState.driveByFrame]). Una forma nuova solo quando qualcosa cambia.
   */
  private val stageShapes = HashMap<Int, MutableState<PhysicsPlaneShape>>()

  /**
   * La tinta di ogni lastra dei campi del palco con più tinte (F4A.2), nell'ordine delle lastre della forma di adesso:
   * scritta dal passo insieme alla forma, prima di lei, così chi la legge in disegno (dopo la forma) la trova allineata.
   */
  private val stageTints = HashMap<Int, IntArray>()
  private val tintJourney = FluidGlassTintJourney()

  /** Il progresso del viaggio, letto in disegno: all'uno la superficie fisica è ferma. */
  private val travelProgress = mutableFloatStateOf(1f)

  /** Se due vetri distinti di un grappolo restano fusi anche a riposo: solo la distanza «larga» della tavola di F3.3. */
  private var restFused = PhysicsPlaneFusionDefault.restFused

  /** Il riquadro sotto cui legge la sonda unica: il grappolo più grande, nel contenitore. */
  private var probeRect by mutableStateOf<Rect?>(null)

  /** Ciò che rifrangono i pezzi del grappolo più grande: è lì che la sonda unica legge. Letto in composizione. */
  var probeBackdrop: GlassBackdropState? by mutableStateOf(null)
    private set

  fun configure(
    density: Density,
    layoutDirection: LayoutDirection,
    spacingPx: Float,
    motion: GlassPlaneMotion?,
    restFused: Boolean = PhysicsPlaneFusionDefault.restFused,
  ) {
    val changed = this.density != density || this.layoutDirection != layoutDirection || this.spacingPx != spacingPx
    if (this.restFused != restFused) {
      this.restFused = restFused
      // Il riposo si rifà con le stesse lastre e il collo nuovo.
      looksDirty = true
      invalidate()
    }
    // Una densità o un verso nuovi spostano i pezzi senza che lo stato cambi: come vincoli nuovi (D R5).
    if (this.density != density || this.layoutDirection != layoutDirection) resized = true
    this.density = density
    this.layoutDirection = layoutDirection
    this.spacingPx = spacingPx
    this.motion = motion
    if (changed) invalidate()
  }

  /** I vincoli dell'ultima misura del contenitore, e se sono cambiati dall'ultimo passo dell'orologio: vedi [noteConstraints]. */
  private var lastConstraints: Constraints? = null
  private var resized = false

  /**
   * Il contenitore è stato misurato con [constraints]: se non sono quelli di prima, il genitore gli ha dato un posto
   * nuovo (una rotazione senza ricreazione, lo schermo diviso, una riga che si allarga attorno a lui). Il prossimo passo
   * lo dice al conducente (`resized`), che lascia seguire i pezzi senza viaggio se sono gli stessi. Fase di misura.
   */
  fun noteConstraints(constraints: Constraints) {
    if (lastConstraints != constraints) {
      if (lastConstraints != null) resized = true
      lastConstraints = constraints
    }
  }

  fun attach(piece: FluidGlassPiece) {
    pieces += piece
    piece.content.attached = true
    // In un contenitore già a riposo il pezzo nuovo nasce da un vicino o compare per lente: il suo contenuto aspetta il
    // fotogramma che lo decide, e poi entra col suo vetro (vedi [FluidGlassPieceContent]). Senza, si vedeva pieno, senza
    // vetro, per il fotogramma in cui il pezzo è stato composto. Alla prima composizione del contenitore (F3.10, decisione
    // aperta 5 di F3.8a) aspetta lo stesso: il vetro di riposo si pubblica al primo passo dell'orologio, e la composizione
    // che lo monta è quella del fotogramma dopo; il contenuto torna a vista nello stesso passo (`onFrame`, `rest()`), e
    // vetro e contenuto compaiono nello stesso fotogramma invece del contenuto un fotogramma prima.
    if (renders) piece.content.hide()
    invalidate()
  }

  fun detach(piece: FluidGlassPiece) {
    pieces -= piece
    registry.remove(piece)
    piece.coordinates = null
    val content = piece.content
    content.attached = false
    // Il contenuto di chi se ne va resta a vista da questo fotogramma (il vetro di riposo c'è ancora), e il viaggio che
    // parte al prossimo lo fa svanire. Chi non si vedeva ancora non lascia niente.
    if (!disposed && renders && content.recorded && content.look.alpha > 0f) {
      departed = departed + piece
    } else {
      content.release()
    }
    invalidate()
  }

  /** Vero da quando il contenitore è uscito dalla composizione: da lì nessun contenuto resta a vista. */
  private var disposed = false

  /**
   * Il grappolo a riposo di [piece] (o d'arrivo, durante un viaggio): la sua identità strutturale, i membri e il
   * riquadro nel contenitore. È la domanda di F4A (i gruppi della barra) e di F5A (il gruppo del tasto che apre un menu):
   * leggono il piano, non ci scrivono.
   */
  fun clusterOf(piece: FluidGlassPiece): GlassPlaneCluster? = driver.rest?.clusterOf(piece)

  /** Qualcosa è cambiato: il fotogramma dopo misura e decide. Da qualunque fase, senza lavoro. */
  fun invalidate() {
    GlassFrameDiagnostics.event("invalidate", this)
    // L'istante in cui il contenitore a riposo ha saputo di un cambio: da qui si misura quanto aspetta la partenza.
    if (driver.journey == null && wokenAtNanos == 0L) wokenAtNanos = System.nanoTime()
    measurePending = true
    wake.trySend(Unit)
  }

  /**
   * Vero dal primo avviso ([invalidate]) al passo che misura: il contenitore guarderà i pezzi al prossimo fotogramma. È
   * la condizione con cui il contenuto di un pezzo spostato aspetta il suo vetro ([FluidGlassPieceContent.hold]): il
   * vetro lo raggiunge davvero al fotogramma dopo, quindi l'attesa è di un fotogramma e mai di più.
   */
  var measurePending: Boolean = false
    private set

  /**
   * Da quando il contenitore a riposo sa di un cambio ([invalidate]): la partenza di un viaggio lo scrive in
   * [FluidGlassContainerDiagnostics.lastStart] (giro di correzione di F3, A 8, C R5).
   */
  private var wokenAtNanos = 0L

  /** Un aspetto è cambiato (una tinta, una selezione): le superfici a riposo si rifanno, il piano no. */
  private var looksDirty = false

  fun onLookChanged() {
    looksDirty = true
    invalidate()
  }

  fun dispose() {
    disposed = true
    stagePhysics.clear()
    dropDeparted { true }
    for (piece in pieces) piece.content.release()
  }

  /** Toglie dai contenuti in uscita quelli che [gone] dice finiti, e rilascia la loro registrazione. */
  private inline fun dropDeparted(gone: (FluidGlassPiece) -> Boolean) {
    val current = departed
    if (current.isEmpty()) return
    val kept = current.filter { piece ->
      val drop = gone(piece)
      if (drop) piece.content.release()
      !drop
    }
    if (kept.size != current.size) departed = kept
  }

  /** Il contenuto di chi se n'è andato, nelle coordinate del contenitore: vedi [departed]. Fase di disegno. */
  fun DrawScope.drawDeparted() {
    val leaving = departed
    if (leaving.isEmpty()) return
    val fade = contentFade().fastCoerceIn(0f, 1f)
    val reference = GlassContentDefaults.BlurReference.toPx()
    for (piece in leaving) {
      val content = piece.content
      translate(content.place.left, content.place.top) {
        drawGlassPieceContent(content, content.layer, fade, departedPath, reference)
      }
    }
  }

  /** Il riquadro della sonda unica (F3-g), in pixel del nodo del contenitore; letto dalla sonda sotto osservazione. */
  fun probeRegion(): Rect? = probeRect

  /** La dissolvenza dell'inchiostro dei pezzi al giro, in disegno: uno senza la decisione del contenitore. */
  fun contentFade(): Float = adaptive?.inkAlpha() ?: 1f

  /** Il progresso del viaggio per Fluid-physics: letto in disegno dalla guida esterna. */
  val progressNow: () -> Float = { travelProgress.floatValue }

  /** Se il campo [index] del palco ha qualcosa da disegnare, in disegno: un campo vuoto non disegna niente. */
  fun stagePresenceOf(index: Int): () -> Float = {
    if (stageShapes[index]?.value?.slabs.isNullOrEmpty()) 0f else 1f
  }

  /**
   * Un campo del palco si è composto con [physics]: la guida **una volta** «a fotogramma» sulla forma del campo
   * ([FluidPhysicsState.driveByFrame]), al fotogramma di adesso subito, in composizione, perché il suo primo disegno sia
   * già quello giusto. Da lì il passo del contenitore cambia solo la forma.
   */
  fun bindStage(index: Int, physics: FluidPhysicsState) {
    stagePhysics[index] = physics
    driveStage()
    val holder = stageShapes[index] ?: return
    physics.driveByFrame({ holder.value }, progressNow)
  }

  fun unbindStage(index: Int, physics: FluidPhysicsState) {
    if (stagePhysics[index] === physics) stagePhysics.remove(index)
  }

  /**
   * Le strisce della tinta [tint] del campo [index] del palco in questo fotogramma (F4A.2), in disegno: legge la forma di
   * adesso (quindi ridisegna con lei) e la tinta di ogni sua lastra. Null finché il campo non ha una forma.
   */
  fun stageTintStrips(index: Int, tint: Int): List<Rect>? {
    val plane = stageShapes[index]?.value ?: return null
    val tints = stageTints[index] ?: return null
    return fluidGlassTintStrips(plane.slabs.map { it.frame }, tints, tint)
  }

  /** L'orologio del contenitore: dorme finché niente cambia, gira a ogni fotogramma mentre il vetro viaggia. */
  suspend fun run() {
    FluidGlassContainerDiagnostics.onContainer(1)
    try {
      while (true) {
        if (driver.journey == null) wake.receive()
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        androidx.compose.runtime.withFrameNanos { onFrame(it, scale) }
      }
    } finally {
      FluidGlassContainerDiagnostics.onContainer(-1)
    }
  }

  /** Un fotogramma: misura i pezzi, chiede al conducente, aggiorna ciò che le superfici leggono. */
  fun onFrame(nanos: Long, durationScale: Float) {
    if (!renders) return
    val began = System.nanoTime()
    GlassFrameDiagnostics.event("frame", this) { "frame=$nanos" }
    measurePending = false
    val measured = GlassFrameDiagnostics.span("measure", this) { measure() } ?: return
    val resizedNow = resized
    resized = false
    val change = fluidGlassTraced("FluidGlass.step") {
      GlassFrameDiagnostics.span("step", this) { driver.step(nanos, measured, motion, durationScale, resizedNow) }
    }
    when (change) {
      FluidGlassPlaneDriver.Change.Nothing -> refreshLooks(measured)
      FluidGlassPlaneDriver.Change.Rest -> settle(measured)
      FluidGlassPlaneDriver.Change.Started -> fluidGlassTraced("FluidGlass.start") {
        GlassFrameDiagnostics.span("start", this) { start(retarget = false) }
      }
      FluidGlassPlaneDriver.Change.Retargeted -> fluidGlassTraced("FluidGlass.restart") { start(retarget = true) }
      FluidGlassPlaneDriver.Change.Moved -> driveStage(publishPending = true)
      FluidGlassPlaneDriver.Change.Finished -> {
        settle(driver.rest ?: measured)
        FluidGlassContainerDiagnostics.onArrival()
      }
    }
    if (change == FluidGlassPlaneDriver.Change.Started) {
      // Dove va il tempo fra il cambio e il primo fotogramma del palco (A 8, B 3, C R5): l'attesa del fotogramma (che a
      // schermo fermo è quella del pannello a 30 Hz) e il lavoro di questo fotogramma.
      val woken = wokenAtNanos
      FluidGlassContainerDiagnostics.onStart(
        FluidGlassStartRecord(
          waitMs = if (woken != 0L) (nanos - woken) / 1_000_000.0 else Double.NaN,
          workMs = (System.nanoTime() - began) / 1_000_000.0,
        ),
      )
    }
    wokenAtNanos = 0L
    // A riposo ogni contenuto è pieno e al suo posto: anche quello di un pezzo che il piano non ha misurato (senza
    // taglia), che non entra con un vetro perché non ne ha. E niente resta di chi se n'è andato.
    if (driver.journey == null) {
      for (piece in pieces) piece.content.rest()
      dropDeparted { true }
    }
  }

  // ---- La misura ----

  /**
   * Lo stato del piano adesso, dai nodi: un pezzo non ancora posato (appena composto) non c'è ancora, e il viaggio che lo
   * aspetta parte (o riparte) quando arriva. Un `id` ripetuto nello stesso contenitore vale per il primo pezzo e
   * basta: il piano non sa accoppiare due vetri con la stessa identità.
   */
  private fun measure(): GlassPlaneState? {
    val box = coordinates?.takeIf { it.isAttached } ?: return null
    val seenIds = HashSet<Any>()
    for (piece in pieces) {
      val look = piece.look
      val node = piece.coordinates?.takeIf { it.isAttached }
      val rect = if (look != null && node != null) box.localBoundingBoxOf(node, clipBounds = false) else null
      if (look == null || rect == null || !(rect.width > 0f) || !(rect.height > 0f) || !rect.isFinite) {
        registry.remove(piece)
        continue
      }
      val attributes = piece.attributes
      val id = attributes.id?.takeIf { seenIds.add(it) }
      looks[piece] = look
      piece.content.place = rect
      registry.put(GlassPlanePiece(
        key = piece,
        bounds = rect,
        radii = radiiOf(piece, look, rect),
        containerId = this,
        variant = look.variant,
        id = id,
        unionId = attributes.unionId,
        transition = when (attributes.transition) {
          FluidGlassTransition.MatchedGeometry -> GlassPlaneTransition.MatchedGeometry
          FluidGlassTransition.Materialize -> GlassPlaneTransition.Materialize
          FluidGlassTransition.Identity -> GlassPlaneTransition.Identity
          FluidGlassTransition.MaterializeWhenAlone -> GlassPlaneTransition.MaterializeWhenAlone
        },
        contentKey = attributes.contentKey,
      ))
    }
    val bounds = Rect(0f, 0f, box.size.width.toFloat(), box.size.height.toFloat())
    return registry.snapshot(spacingPx, bounds, room = stageRoom(box, bounds))
  }

  /**
   * Il **posto del palco** (`GlassPlaneState.room`, giro di correzione di F3): la finestra (il riquadro della radice della
   * composizione) nelle coordinate del contenitore. Il superamento di chi nasce e la spinta di chi accoglie possono uscire
   * dal contenitore fin lì, come in Apple; non oltre il bordo della finestra, dove nessuno li vedrebbe e il vetro si
   * fermerebbe di colpo. Il riquadro del contenitore se la radice non si sa.
   */
  private fun stageRoom(box: LayoutCoordinates, bounds: Rect): Rect {
    val root = box.findRootCoordinates()
    if (root === box || !root.isAttached) return bounds
    val window = box.localBoundingBoxOf(root, clipBounds = false)
    return if (window.isFinite && window.width > 0f && window.height > 0f) window else bounds
  }

  private fun radiiOf(piece: FluidGlassPiece, look: FluidGlassLook, rect: Rect): FluidCornerRadii {
    val half = rect.minDimension / 2f
    val raw = if (look.shape is FluidConcentricShape) {
      piece.concentricRadii ?: cornerRadiiOf(look.shape, rect.size, density, layoutDirection)
    } else {
      cornerRadiiOf(look.shape, rect.size, density, layoutDirection)
    }
    return FluidCornerRadii(
      raw.topLeft.fastCoerceIn(0f, half),
      raw.topRight.fastCoerceIn(0f, half),
      raw.bottomRight.fastCoerceIn(0f, half),
      raw.bottomLeft.fastCoerceIn(0f, half),
    )
  }

  // ---- Il riposo ----

  private fun settle(state: GlassPlaneState) {
    pendingRest = null
    looksDirty = false
    stage = null
    stagePhysics.clear()
    stageShapes.clear()
    stageTints.clear()
    travelProgress.floatValue = 1f
    swell = FluidGlassSwell.None
    tintJourney.rest(state.pieces.mapNotNull { piece -> looks[piece.key]?.let { piece.key to it.tint } }.toMap())
    publishRest(state)
    // Gli aspetti di chi se n'è andato non servono più: il viaggio che li portava è finito.
    looks.keys.retainAll(state.pieces.mapTo(HashSet()) { it.key })
  }

  private fun refreshLooks(state: GlassPlaneState) {
    // Un aspetto cambiato senza che il piano cambi: a riposo le superfici si rifanno; in viaggio aspetta l'arrivo.
    if (looksDirty && driver.journey == null) {
      looksDirty = false
      tintJourney.rest(state.pieces.mapNotNull { piece -> looks[piece.key]?.let { piece.key to it.tint } }.toMap())
      publishRest(state)
    }
  }

  private fun publishRest(state: GlassPlaneState) {
    val margin = shadowMarginPx()
    val fields = state.clusters.map { cluster ->
      val first = cluster.pieces.first()
      val look = looks[first.key] ?: (first.key as FluidGlassPiece).look!!
      if (cluster.pieces.size == 1) {
        val node = first.bounds.snapped()
        FluidGlassRestField(
          key = first.key,
          node = node,
          look = look,
          slabs = listOf(FluidForm.Slab(first.bounds.translate(-node.left, -node.top), first.radii, smoothingOf(first))),
          shape = restShapeOf(look, first),
        )
      } else {
        val node = cluster.bounds.inflate(margin).snapped()
        val ordered = cluster.pieces.unionsFirst { it }
        val slabs = ordered.map {
          FluidForm.Slab(it.bounds.translate(-node.left, -node.top), it.radii, smoothingOf(it))
        }
        FluidGlassRestField(
          key = cluster.id,
          node = node,
          look = look,
          exports = exportsOf(cluster.pieces.map { it.key }),
          slabs = slabs,
          shape = null,
          // F4A.2 (D R4): pezzi con tinte diverse, una superficie per tinta. Una tinta sola: null, com'era.
          tints = fluidGlassTintSplit(ordered.map { looks[it.key] ?: (it.key as FluidGlassPiece).look!! }),
          // A riposo due vetri distinti sono separati del tutto (collo a -1, decisione dell'11 novembre); quelli della
          // stessa unione una sagoma sola (F3.2c). La «larga» della tavola li fonde tutti.
          plane = PhysicsPlaneShape(
            slabs = slabs,
            nominalBlend = spacingPx,
            neck = if (restFused) 1f else -1f,
            unions = fluidGlassUnionIndices(ordered),
            resting = true,
          ),
        )
      }
    }
    restFields = fields
    // La pellicola con cui ogni pezzo si disegna davvero (F4A.2): quella della sua striscia, o quella del grappolo.
    val drawnTints = HashMap<Any, GlassTint>()
    state.clusters.forEachIndexed { index, cluster ->
      val field = fields[index]
      val split = field.tints
      cluster.pieces.unionsFirst { it }.forEachIndexed { slab, piece ->
        drawnTints[piece.key] = if (split == null) field.look.tint else split.looks[split.slabTints[slab]].tint
      }
    }
    val biggest = state.clusters.maxByOrNull { it.bounds.width * it.bounds.height }
    probeRect = biggest?.bounds
    probeBackdrop = biggest?.let { looks[it.pieces.first().key]?.backdrop }
    // Un pannello dice da sé se è un grande (F3.2e: la card e il dock scuriscono, la capsula larga del composer gira);
    // gli altri pezzi lo lasciano alla taglia, come prima.
    large = biggest != null && fluidGlassContainerLarge(
      declared = biggest.pieces.firstNotNullOfOrNull { looks[it.key]?.large },
      shortSideDp = with(density) { biggest.pieces.maxOf { it.bounds.minDimension }.toDp().value },
    )
    FluidGlassContainerDiagnostics.onRest(
      state.clusters.flatMap { cluster ->
        cluster.pieces.map {
          FluidGlassPieceRecord(
            id = it.id.appId(),
            bounds = it.bounds,
            cornerRadii = it.radii,
            clusterSize = cluster.pieces.size,
            unionId = it.unionId.appId(),
            exports = looks[it.key]?.exports != null,
            drawnTint = drawnTints[it.key],
          )
        }
      },
      large = large,
    )
  }

  /** Il materiale che un grappolo o un campo pubblica: quello del primo dei suoi pezzi che ne ha uno (F3.2e). */
  private fun exportsOf(keys: List<Any?>): GlassBackdropState? =
    keys.firstNotNullOfOrNull { key -> key?.let { looks[it]?.exports } }

  /** La forma di un grappolo di un pezzo: quella dell'app, salvo una concentrica, che si disegna risolta. */
  private fun restShapeOf(look: FluidGlassLook, piece: GlassPlanePiece): Shape {
    val shape = look.shape
    return if (shape is FluidConcentricShape) shape.resolvedTo(piece.radii, layoutDirection) else shape
  }

  private fun smoothingOf(piece: GlassPlanePiece): Float {
    val shape = looks[piece.key]?.shape ?: return ContinuousCornerShape.IosSmoothing
    return (shape as? ContinuousCornerShape)?.smoothing ?: 0f
  }

  /**
   * Di quanto il nodo di una superficie fisica si allarga attorno alla sagoma: fin dove arriva l'ombra in corpo. Per un
   * campo del palco ([journey], F3.10) fin dove arriva l'alone del carattere del viaggio, che prende la maschera
   * dell'ombra (con l'ombra B di serie la stessa portata); i grappoli fermi restano quelli di sempre, al bit.
   */
  private fun shadowMarginPx(journey: GlassLiquidJourney? = null): Float = with(density) {
    glassTravelShadowReachDp(GlassDefaults.buttonOptics().sanitized(), gain = 0f, journey = journey).dp.toPx()
  }

  // ---- Il palco ----

  private fun start(retarget: Boolean) {
    val journey = driver.journey ?: return
    val target = driver.rest ?: return
    driver.frame?.let { tintJourney.start(it, looks.mapValues { entry -> entry.value.tint }) }
    // Il riposo d'arrivo si monta spento e riprenderà il vetro nella composizione in cui il palco lo lascia; ma **non in
    // questo fotogramma** (giro di correzione di F3, C R5): il primo fotogramma del palco compone già le superfici del
    // palco, e il riposo d'arrivo (una superficie nuova per ogni grappolo che cambia) serve solo alla fine. Si pubblica al
    // passo dopo ([driveStage]); fino ad allora i grappoli di prima restano montati e spenti, e quelli che non cambiano
    // tengono il vetro come sempre (le loro chiavi sono le stesse prima e dopo).
    pendingRest = target
    // Le lastre del palco nel log, a richiesta (`adb shell setprop log.tag.FluidGlass VERBOSE`): la «testa rettangolare»
    // di «Fatto» al colmo (A 3) si vedeva solo sul tablet. Letto una volta per viaggio.
    stageLog = Log.isLoggable(FluidGlassLogTag, Log.VERBOSE)
    val margin = shadowMarginPx(driver.journeyCharacter)
    // Chi non cambia resta al suo vetro di riposo (F3.8a): il palco prende solo i pezzi che viaggiano.
    val origin = driver.origin
    val resting = if (origin == null) emptyList() else fluidGlassRestingClusters(origin, target, journey.stillKeys)
    val restingKeys = resting.flatMapTo(HashSet()) { cluster -> cluster.pieces.map { it.key } }
    stage = FluidGlassStage(
      revision = driver.journeyRevision,
      fields = journey.fields.mapIndexedNotNull { index, field ->
        val flights = field.flights.filter { it.targetKey !in restingKeys }
        if (flights.isEmpty()) null else stageFieldOf(index, field, flights, margin)
      },
      resting = resting.mapTo(HashSet()) { fluidGlassRestKey(it) },
    )
    stageShapes.keys.retainAll(stage!!.fields.mapTo(HashSet()) { it.index })
    stageTints.keys.retainAll(stage!!.fields.mapTo(HashSet()) { it.index })
    noteFinger(retarget)
    startContents()
    driveStage()
    FluidGlassContainerDiagnostics.onJourney(records(driver.frame), retarget)
  }

  /**
   * La luce del dito (F3.10): se il viaggio che parte l'ha fatto partire un tocco su un vetro interattivo del
   * contenitore (un `fluidGlass().interactive()`, un tasto di vetro dell'engine), lo dice al conducente: il pezzo più
   * premuto adesso, oltre la soglia [GlassJourneyFingerMin]. Il click arriva al rilascio, quindi la pressione sta già
   * tornando: è il bagliore che il vetro fermo mostra in questo fotogramma, ed è da lì che il palco comincia.
   */
  private fun noteFinger(retarget: Boolean) {
    findFinger(retarget)
    FluidGlassContainerDiagnostics.onFinger(driver.finger?.entry ?: 0f)
  }

  private fun findFinger(retarget: Boolean) {
    val origin = driver.origin ?: return
    // Solo un vetro che viaggia: uno premuto che resta fermo (un grappolo che non cambia) tiene il suo bagliore lì.
    val travelling = stage?.fields?.flatMapTo(HashSet()) { field -> field.flights.mapNotNull { it.sourceKey } } ?: return
    var pressed: GlassPlanePiece? = null
    var amount = GlassJourneyFingerMin
    for (piece in origin.pieces) {
      if (piece.key !in travelling) continue
      val touch = looks[piece.key]?.touch ?: continue
      val now = touch.amount()
      if (now > amount) {
        amount = now
        pressed = piece
      }
    }
    val piece = pressed ?: return
    val at = looks[piece.key]?.touch?.position?.invoke() ?: Offset.Unspecified
    val b = piece.bounds
    val u = if (at.isSpecified && b.width > 0f) at.x / b.width else 0.5f
    val v = if (at.isSpecified && b.height > 0f) at.y / b.height else 0.5f
    // Il bagliore si vedeva solo sul vetro fermo di un pezzo solo (i grappoli non lo mostrano ancora: F6.1).
    val shown = !retarget && origin.clusters.any { it.pieces.size == 1 && it.pieces.first().key == piece.key }
    driver.touched(piece.key, amount, u, v, shown)
  }

  private fun stageFieldOf(
    index: Int,
    field: GlassPlaneField,
    flights: List<GlassPlaneFlightId>,
    margin: Float,
  ): FluidGlassStageField {
    val look = flights.firstNotNullOfOrNull { flight ->
      flight.targetKey?.let(looks::get) ?: flight.sourceKey?.let(looks::get)
    } ?: looks.values.first()
    val exports = exportsOf(flights.flatMap { listOf(it.targetKey, it.sourceKey) })
    val lens = flights.all { it.change == GlassPlaneChange.Materialize || it.change == GlassPlaneChange.Dematerialize }
    // Il nodo contiene il rigonfiamento al colmo (F3.9): ogni pezzo sta nel riquadro del campo, quindi il lato corto
    // del campo è un tetto al suo. Chi compare o scompare per lente non si gonfia. Dove un vetro nasce da un vicino il
    // suo rigonfiamento proprio arriva a [FluidMotion.PlaneReleaseSwell] (F3.12c: al posto del comune, non sopra).
    val born = flights.any { it.change == GlassPlaneChange.BornFromNeighbour }
    val grow = if (lens) 0f else {
      FluidGlassSwell(driver.journeyCharacter, 1f, FluidMotion.PlaneSwellMaxDp * density.density, density.density)
        .growPx(field.bounds.minDimension, if (born) max(0f, FluidMotion.PlaneReleaseSwell - 1f) else 0f)
    }
    // Un pezzo premuto parte dalla misura premuta e la rilascia in volo (giro di correzione di F3): il nodo la contiene.
    val pressed = if (lens) 0f else pressReachPx(flights)
    // F4A.2 (D R4): i pezzi del campo con tinte diverse hanno una superficie per tinta; una tinta sola, null com'era.
    val tints = fluidGlassTintSplit(flights.mapNotNull(::lookOf))?.looks
    return FluidGlassStageField(
      index, field.bounds.inflate(margin + grow + pressed).snapped(), look, flights, exports, union = field.union, lens = lens,
      tints = tints,
    )
  }

  /** L'aspetto di un volo: quello del pezzo d'arrivo, o di quello di partenza per chi se ne va. */
  private fun lookOf(flight: GlassPlaneFlightId): FluidGlassLook? =
    flight.targetKey?.let(looks::get) ?: flight.sourceKey?.let(looks::get)

  /**
   * La tinta di ogni lastra del campo [field] in [frame] (F4A.2), nell'ordine delle lastre della sua forma: lo stesso
   * filtro e lo stesso ordine di [fluidGlassFieldSlabs], fino al tetto delle lastre del campo.
   */
  private fun updateStageTints(frame: GlassPlaneFrame, field: FluidGlassStageField, tints: Map<GlassPlaneFlightId, GlassTint>) {
    val members = fluidGlassFieldSlabs(frame, field.flights, field.node.topLeft, { 0f }).members
    val shown = members.take(FluidForm.Group.MaxPieces).map { member ->
      val look = lookOf(member.flightId) ?: field.look
      look.copy(tint = tints[member.flightId] ?: look.tint)
    }
    if (shown.isEmpty()) return
    val split = fluidGlassTintSplit(shown)
    stageTints[field.index] = split?.slabTints ?: IntArray(shown.size)
    field.liveTints = split?.looks ?: listOf(shown.first())
  }

  /**
   * La parte del contenuto di ogni pezzo nel viaggio che parte o riparte adesso (F3.8a): dall'aspetto che ha, perché un
   * cambio a metà non faccia saltare nessuna scritta (invariante 16). Chi se n'è andato esce, se il piano lo porta via
   * con un vetro; se il suo vetro è passato a un altro nodo con la stessa identità, il contenuto lo disegna già quello.
   */
  private fun startContents() {
    val frame = driver.frame ?: return
    val records = ArrayList<FluidGlassContentRecord>()
    val leaving = HashSet<FluidGlassPiece>()
    for (flying in frame.pieces) {
      val target = flying.flightId.targetKey as? FluidGlassPiece
      val source = flying.flightId.sourceKey as? FluidGlassPiece
      val arrival = fluidGlassArrivalOf(flying.change, flying.relay)
      if (target != null) {
        val content = target.content
        // Un nodo nuovo con l'identità di uno che se n'è appena andato continua il suo contenuto, dall'aspetto che aveva.
        val inherited = source?.takeIf { it !== target }?.content
        val now = when {
          content.role.role != GlassPieceContentRole.Hidden -> content.look
          inherited != null && inherited.look.alpha > 0f -> inherited.look
          else -> null
        }
        content.role = glassPieceContentFor(arrival, now, departed = false)
        records += FluidGlassContentRecord(target.attributes.id?.id, content.role.role.name, departed = false)
      } else if (source != null && !source.content.attached) {
        val content = source.content
        content.role = glassPieceContentFor(arrival, content.look, departed = true)
        if (content.role.role == GlassPieceContentRole.Exit) leaving += source
        records += FluidGlassContentRecord(source.attributes.id?.id, content.role.role.name, departed = true)
      }
    }
    dropDeparted { it !in leaving }
    FluidGlassContainerDiagnostics.onContents(records)
  }

  /** Il riposo d'arrivo del viaggio appena partito, da pubblicare al passo dopo: vedi [start]. */
  private var pendingRest: GlassPlaneState? = null

  /** Se le lastre del palco vanno nel log in questo viaggio: vedi [start]. */
  private var stageLog = false

  /**
   * La pressione di un pezzo in volo adesso (giro di correzione di F3): quella del pezzo che il riposo mostrava premuto
   * (chi parte), o del pezzo d'arrivo; null se non è premuto. Nelle coordinate del suo posto, con la sua taglia.
   */
  private fun pressOf(member: GlassPlanePieceFrame): FluidPressTransform? {
    fun of(key: Any?): FluidPressTransform? {
      val piece = key as? FluidGlassPiece ?: return null
      val geometry = looks[piece]?.pressGeometry ?: return null
      // Un pezzo che non si vede ancora (nato da poco) non è premuto da nessuno: il tocco non gli arriva (D, minore).
      if (piece.content.hidden) return null
      val place = piece.content.place
      if (!(place.width > 0f) || !(place.height > 0f)) return null
      return geometry.at(place.width, place.height, density.density).takeUnless { it.isIdentity }
    }
    return of(member.flightId.sourceKey) ?: of(member.flightId.targetKey)
  }

  /** Di quanto la pressione può portare fuori dal suo posto un pezzo di [flights], al più: vedi `FluidGlassPressGeometry`. */
  private fun pressReachPx(flights: List<GlassPlaneFlightId>): Float {
    var reach = 0f
    for (flight in flights) {
      for (key in listOf(flight.sourceKey, flight.targetKey)) {
        val piece = key as? FluidGlassPiece ?: continue
        val geometry = looks[piece]?.pressGeometry ?: continue
        val place = piece.content.place
        if (place.width > 0f && place.height > 0f) reach = max(reach, geometry.reachPx(place.width, place.height, density.density))
      }
    }
    return reach
  }

  /**
   * Porta il fotogramma di adesso sulle superfici del palco: per ogni campo una forma del piano ([PhysicsPlaneShape]) col
   * collo nel tempo ([FluidGlassPlaneDriver.neckNow], F3.3), le unioni, la comparsa del campo e il peso del vetro in
   * viaggio dalla velocità dei suoi lati (`physicsPlaneMotion`), rigonfiata del rigonfiamento del viaggio. E l'aspetto
   * del contenuto di ogni pezzo ([driveContents]).
   */
  private fun driveStage(publishPending: Boolean = false) {
    val frame = driver.frame ?: return
    val stage = stage ?: return
    // Il riposo d'arrivo rimandato dal primo fotogramma del palco ([start]): al primo passo del palco dopo la partenza.
    if (publishPending) {
      pendingRest?.let { target ->
        pendingRest = null
        publishRest(target)
      }
    }
    travelProgress.floatValue = driver.progress
    val movingMs = driver.movingMs.toFloat()
    // Il rigonfiamento del viaggio (punto 8 di F3.8a), con la curva e il numero del carattere di F3.9: la sagoma di chi
    // viaggia esce di poco da ogni lato alla fase di adesso, dal livello con cui il viaggio è cominciato, e rientra
    // prima del riposo. Non per chi compare o scompare per lente, che ha già il suo contorno più largo.
    swell = FluidGlassSwell(
      driver.journeyCharacter, driver.journeySwell, FluidMotion.PlaneSwellMaxDp * density.density, density.density,
    )
    // La luce del carattere (F3.9) e quella del dito (F3.10), agganciate da F3.10: la forma le porta alla ricetta delle
    // superfici del palco (`physicsLiquidRecipe`, `physicsLiquidGlow`). Chi compare o scompare per lente ha la sua.
    val light = driver.journeyLight
    // L'alone ha la sua curva (F3.12c): sale con la luce e si spegne prima.
    val halo = driver.journeyGlow
    val finger = driver.finger
    val glow = driver.fingerGlow
    val fill = driver.fingerFill
    val tints = tintJourney.frame(frame)
    for (field in stage.fields) {
      val plane = fluidGlassStagePlane(
        frame = frame,
        flights = field.flights,
        origin = field.node.topLeft,
        smoothing = { smoothingOf(it) },
        spacingPx = spacingPx,
        // Il collo dell'orologio, o quello dell'unione che in questo campo nasce o si scioglie.
        neck = if (restFused) 1f else driver.neckNow(field.union),
        swell = if (field.lens) FluidGlassSwell.None else swell,
        density = density.density,
        movingMs = movingMs,
        journeyLight = if (field.lens) 0f else light,
        touch = if (field.lens) null else fluidGlassFingerTouch(frame, field.flights, field.node.topLeft, finger, glow, fill),
        journeyGlow = if (field.lens) 0f else halo,
        // Il tasto premuto che ha fatto partire il viaggio resta premuto e si rilascia con la sua molla (R2 di C).
        pressed = if (field.lens) NoPieceFramePress else pressFrames,
      )
      // Le tinte delle lastre prima della forma: chi le legge in disegno le trova allineate con lei.
      updateStageTints(frame, field, tints)
      val holder = stageShapes[field.index]
      if (holder == null) stageShapes[field.index] = mutableStateOf(plane) else holder.value = plane
      if (stageLog) {
        Log.v(
          FluidGlassLogTag,
          "PALCO t=${"%.1f".format(java.util.Locale.ROOT, driver.elapsedMs)} campo=${field.index} nodo=${field.node} " +
            "collo=${"%.3f".format(java.util.Locale.ROOT, plane.neck)} " +
            plane.slabs.joinToString(" ") { "[${it.frame.left},${it.frame.top},${it.frame.right},${it.frame.bottom} r=${it.cornerRadii}]" },
        )
      }
    }
    driveContents(frame)
  }

  /** Il rigonfiamento di adesso: vedi [FluidGlassSwell]. */
  private var swell = FluidGlassSwell.None

  /** La pressione di ogni pezzo in volo, per `fluidGlassStagePlane`: vedi [pressOf]. */
  private val pressFrames: (GlassPlanePieceFrame) -> FluidPressTransform? = { pressOf(it) }

  /**
   * L'aspetto del contenuto di ogni pezzo in questo fotogramma (F3.8a): dalla sua parte e dalla fase del volo del suo
   * vetro, ritagliato sulla lastra in volo. Ogni contenuto viaggia con la sua lastra (F3.12a: il centro di serie,
   * F4A.2b: l'ancora scelta dal pezzo), anche quello di chi nasce e di chi confluisce: l'icona rimbalza col vetro.
   */
  private fun driveContents(frame: GlassPlaneFrame) {
    val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var finished: HashSet<FluidGlassPiece>? = null
    for (flying in frame.pieces) {
      val target = flying.flightId.targetKey as? FluidGlassPiece
      val piece = target ?: (flying.flightId.sourceKey as? FluidGlassPiece)?.takeIf { !it.content.attached } ?: continue
      val content = piece.content
      val role = content.role
      // La fase del contenuto (F3.12a): quella del volo, più avanti per chi nasce quando il primo rilascio viene presto.
      val look = glassPieceContentLook(role, flying.contentPhase, blur)
      content.look = look
      if (target == null && glassPieceContentDone(role, flying.contentPhase)) {
        (finished ?: HashSet<FluidGlassPiece>().also { finished = it }) += piece
        continue
      }
      val flight = fluidGlassContentFlightOf(
        flying, role, look, content.place, swell, pressOf(flying), piece.attributes.contentAnchor, layoutDirection,
      )
      if (flight != null || content.flight.value != null) content.show(flight)
    }
    finished?.let { done -> dropDeparted { it in done } }
  }

  private fun records(frame: GlassPlaneFrame?): List<FluidGlassFlightRecord> {
    frame ?: return emptyList()
    fun idOf(key: Any?): Any? = (key as? FluidGlassPiece)?.attributes?.id?.id
    return frame.pieces.map {
      FluidGlassFlightRecord(
        fromId = idOf(it.flightId.sourceKey),
        toId = idOf(it.flightId.targetKey),
        change = it.change.name,
        bornFromId = idOf(it.bornFrom),
        mergesIntoId = idOf(it.mergesInto),
      )
    }
  }
}

private fun Any?.appId(): Any? = (this as? FluidGlassQualifiedId)?.id

private fun FluidGlassAnimation.planeMotion(joinStill: Boolean): GlassPlaneMotion? = when (this) {
  FluidGlassAnimation.Default -> GlassPlaneMotion(joinStill = joinStill)
  FluidGlassAnimation.None -> null
  is FluidGlassAnimation.Custom -> GlassPlaneMotion(spec = spec, joinStill = joinStill)
}

// ================================================================================================================
// Le superfici
// ================================================================================================================

/**
 * Le superfici del vetro del contenitore: i grappoli a riposo e, durante un viaggio, i campi del palco.
 *
 * **Chi porta il vetro** si legge qui, in composizione, una volta per tutte le superfici: con un palco i grappoli (già
 * quelli d'arrivo) restano montati e spenti, senza disegno né cattura; senza, lo portano loro. Le due letture stanno
 * nella stessa passata, quindi in nessun fotogramma il vetro c'è due volte o manca (F3-b). È il meccanismo della barra
 * ancorata (`FluidBarJourney.active`), generalizzato. Un grappolo che nel viaggio non cambia non passa dal palco: tiene
 * il suo vetro di riposo dall'inizio alla fine (F3.8a, [FluidGlassStage.resting]).
 */
@Composable
private fun FluidGlassFields(state: FluidGlassContainerState) {
  GlassFrameDiagnostics.event("fieldsCompose", state)
  val stage = state.stage
  for (field in state.restFields) {
    key(field.key) { FluidGlassRestSurface(field, carries = stage == null || field.key in stage.resting) }
  }
  if (stage != null) {
    for (field in stage.fields) {
      key(field.index) { FluidGlassStageSurface(state, field) }
    }
  }
}

/** Un grappolo a riposo: una lastra di vetro di sempre se è un pezzo solo, una superficie fisica ferma se sono di più. */
@Composable
private fun FluidGlassRestSurface(field: FluidGlassRestField, carries: Boolean) {
  val carrying by rememberUpdatedState(carries)
  val look = field.look
  val gate = Modifier
    .layoutId(FluidGlassFieldSlot(field.node))
    // Spenta, non disegna niente, nemmeno la cattura.
    .drawWithContent { if (carrying) drawContent() }
  val shape = field.shape
  if (shape != null) {
    // Un pezzo solo: la stessa lastra di un vetro fuori dal contenitore, con la sua ombra a maschere e la luce a parte.
    val press = look.press
    Box(
      gate.glassLiquidSurface(
        state = look.backdrop,
        tint = look.tint,
        shape = shape,
        edge = GlassEdge.None,
        intensity = FullIntensity,
        role = look.role,
        optics = look.optics,
        opticalDepth = FullIntensity,
        pressed = if (press != null) {
          { press.progress.fastCoerceIn(0f, 1f) }
        } else {
          NoPress
        },
        exports = field.exports,
        layerBlock = look.layerBlock,
        sampleOnce = false,
        resampleIntervalMillis = look.resampleIntervalMillis,
        frozen = NeverHeld,
        heldWhileMoving = NeverHeld,
        nestInContainer = true,
        touch = look.touch,
        ink = null,
        variant = look.variant,
      ),
    )
  } else {
    // Più pezzi: una cattura sola per tutto il grappolo, ogni pezzo una lastra del campo: separate del tutto se sono
    // distinte, una sagoma sola se sono della stessa unione (F3.2c). La stessa guida «a fotogramma» del palco, ferma.
    val plane = field.plane ?: PhysicsPlaneShape(field.slabs, nominalBlend = 0f, resting = true)
    val current = rememberUpdatedState(plane)
    // F4A.2 (D R4): con pezzi di tinte diverse, una superficie per tinta, sulla striscia dei suoi pezzi. La prima sta
    // sempre nello stesso posto della composizione, con la stessa catena: un tasto che si seleziona non rifà la superficie
    // del grappolo, ne aggiunge una. Con una tinta sola nessun ritaglio, com'era.
    val split = field.tints
    val drawn = split?.looks ?: listOf(look)
    for (tint in drawn.indices) {
      key(tint) {
        val strips = if (split == null) {
          null
        } else {
          remember(field.slabs, split, tint) { fluidGlassTintStrips(field.slabs.map { it.frame }, split.slabTints, tint) }
        }
        FluidGlassRestPlane(
          gate.drawWithContent { drawInStrips(strips) },
          current,
          drawn[tint],
          if (tint == 0) field.exports else null,
        )
      }
    }
  }
}

/** Un grappolo fermo di più pezzi con l'aspetto [look]: la superficie fisica della sua forma, guidata «a fotogramma». */
@Composable
private fun FluidGlassRestPlane(
  modifier: Modifier,
  current: androidx.compose.runtime.State<PhysicsPlaneShape>,
  look: FluidGlassLook,
  exports: GlassBackdropState?,
) {
  val physics = rememberFluidPhysicsState(current.value.form())
  remember(physics) {
    physics.driveByFrame({ current.value }, AtRest)
    Unit
  }
  val ink = remember { GlassInk() }
  Box(
    modifier.fluidPhysicsSurfaceInked(
      state = physics,
      backdrop = look.backdrop,
      tint = look.tint,
      role = look.role,
      optics = look.optics,
      exports = exports,
      ink = ink,
      variant = look.variant,
    ),
  )
}

/** Un campo del palco: una superficie fisica guidata dal piano, per tutto il viaggio; una per tinta se ne ha più d'una. */
@Composable
private fun FluidGlassStageSurface(state: FluidGlassContainerState, field: FluidGlassStageField) {
  val tints = field.liveTints
  for (tint in tints.indices) key(tint) { FluidGlassStageTint(state, field, tints[tint], tint) }
}

/**
 * Una superficie di un campo del palco con l'aspetto [look]; [tint] è l'indice della sua tinta fra quelle del campo, o
 * -1 per il campo di una tinta sola (nessun ritaglio, com'era).
 */
@Composable
private fun FluidGlassStageTint(state: FluidGlassContainerState, field: FluidGlassStageField, look: FluidGlassLook, tint: Int) {
  val physics = rememberFluidPhysicsState(FluidForm.Slab(Rect(0f, 0f, 1f, 1f)))
  // Guidata subito al fotogramma di adesso: il suo primo disegno è già quello giusto.
  remember(physics, field.index) {
    state.bindStage(field.index, physics)
    Unit
  }
  DisposableEffect(physics, field.index) {
    onDispose { state.unbindStage(field.index, physics) }
  }
  val presence = remember(field.index) { state.stagePresenceOf(field.index) }
  val ink = remember { GlassInk() }
  Box(
    Modifier
      .layoutId(FluidGlassFieldSlot(field.node))
      .then(if (tint < 0) Modifier else Modifier.drawWithContent { drawInStrips(state.stageTintStrips(field.index, tint)) })
      // Giro di correzione di F3 (A 8, C R5): finché il palco c'è, chiede al sistema la frequenza alta. A schermo fermo il
      // pannello del tablet gira a 30 Hz e un cambio senza tocco non lo sveglia: i primi fotogrammi del viaggio arrivavano
      // a 30 Hz. Dall'API 35 (frequenza adattiva); prima non fa niente. Il palco esce col viaggio, e il voto con lui.
      .preferredFrameRate(FrameRateCategory.High)
      .fluidPhysicsSurfaceInked(
        state = physics,
        backdrop = look.backdrop,
        tint = look.tint,
        role = look.role,
        optics = look.optics,
        intensity = presence,
        exports = if (tint <= 0) field.exports else null,
        ink = ink,
        variant = look.variant,
        // Il carattere del viaggio e la luce del dito (F3.10), col margine della cattura del carattere al colmo.
        planeJourney = true,
      ),
  )
}

private val FullIntensity: () -> Float = { 1f }
private val AtRest: () -> Float = { 1f }
private val NoPress: () -> Float = { 0f }
private val NeverHeld: () -> Boolean = { false }

// ================================================================================================================
// Il pezzo
// ================================================================================================================

/**
 * Iscrive al contenitore [container] il nodo che porta questo modificatore, come pezzo di aspetto [look]: il vetro lo
 * disegna il contenitore, il nodo disegna solo il suo contenuto (che al giro del lato svanisce e risale con
 * l'inchiostro). Usato da `fluidGlass` e da `glassControlSurface`.
 */
@Composable
internal fun Modifier.fluidGlassPiece(
  container: FluidGlassContainerState,
  look: FluidGlassLook,
  attributes: FluidGlassAttributes,
): Modifier {
  val piece = remember(container) { FluidGlassPiece(container) }
  piece.look = look
  piece.attributes = attributes
  DisposableEffect(container, piece) {
    container.attach(piece)
    onDispose { container.detach(piece) }
  }
  val minimum = (look.shape as? FluidConcentricShape)?.minimum
  return this
    .then(FluidGlassPieceElement(piece, minimum))
    .then(FluidGlassPieceContentElement(piece))
}

private class FluidGlassPieceElement(
  val piece: FluidGlassPiece,
  val concentricMinimum: Dp?,
) : ModifierNodeElement<FluidGlassPieceNode>() {
  override fun create(): FluidGlassPieceNode = FluidGlassPieceNode(piece, concentricMinimum)

  override fun update(node: FluidGlassPieceNode) {
    node.update(piece, concentricMinimum)
  }

  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassPieceElement && other.piece === piece && other.concentricMinimum == concentricMinimum

  override fun hashCode(): Int = 31 * System.identityHashCode(piece) + concentricMinimum.hashCode()

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassPiece"
  }
}

/**
 * Il nodo di un pezzo: dice al contenitore dove sta, e per una forma concentrica delega la risoluzione dei raggi al
 * nodo di F3.5 ([FluidConcentricRadiiNode]), che legge il contenitore di forma più vicino.
 *
 * È **solo** un nodo che delega: il posizionamento lo riceve un delegato suo ([FluidGlassPiecePositionNode]). Compose,
 * quando un nodo è lui stesso del tipo cercato, non visita i suoi delegati per quel tipo: un nodo che fosse insieme
 * `GlobalPositionAwareModifierNode` e delegante toglierebbe il posizionamento al nodo concentrico, che resterebbe al
 * minimo (è il difetto che il test della galleria ha trovato).
 */
private class FluidGlassPieceNode(
  piece: FluidGlassPiece,
  concentricMinimum: Dp?,
) : DelegatingNode() {
  private val position = delegate(FluidGlassPiecePositionNode(piece))
  private var concentric: FluidConcentricRadiiNode? = null

  init {
    install(concentricMinimum)
  }

  fun update(piece: FluidGlassPiece, concentricMinimum: Dp?) {
    position.piece = piece
    val current = concentric
    if (concentricMinimum == null && current != null) {
      undelegate(current)
      concentric = null
    } else if (concentricMinimum != null && current == null) {
      install(concentricMinimum)
    } else if (concentricMinimum != null && current != null && current.minimum != concentricMinimum) {
      current.minimum = concentricMinimum
      current.resolve()
    }
  }

  private fun install(minimum: Dp?) {
    if (minimum == null) return
    concentric = delegate(
      FluidConcentricRadiiNode(minimum) { radii ->
        val piece = position.piece
        if (piece.concentricRadii != radii) {
          piece.concentricRadii = radii
          piece.container.invalidate()
        }
      },
    )
  }
}

/** Dove sta il pezzo: lo dice al contenitore a ogni posizionamento. */
private class FluidGlassPiecePositionNode(var piece: FluidGlassPiece) : Modifier.Node(), GlobalPositionAwareModifierNode {
  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    piece.coordinates = coordinates
    piece.container.invalidate()
  }

  override fun onDetach() {
    piece.coordinates = null
  }
}

private class FluidGlassPieceContentElement(val piece: FluidGlassPiece) : ModifierNodeElement<FluidGlassPieceContentNode>() {
  override fun create(): FluidGlassPieceContentNode = FluidGlassPieceContentNode(piece)

  override fun update(node: FluidGlassPieceContentNode) {
    node.piece = piece
  }

  override fun equals(other: Any?): Boolean =
    this === other || other is FluidGlassPieceContentElement && other.piece === piece

  override fun hashCode(): Int = System.identityHashCode(piece)

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidGlassPieceContent"
  }
}

/**
 * Il disegno del contenuto di un pezzo (F3.8a): lo registra nel layer del pezzo ([FluidGlassPieceContent.layer], che
 * resta al contenitore quando il pezzo se ne va) e lo disegna da lì, con la dissolvenza dell'inchiostro del contenitore
 * e, in viaggio, con l'aspetto e il ritaglio di [FluidGlassPieceContent.flight]. Vale per ogni contenuto del pezzo, anche
 * una [FluidGlassContentTransition]. A riposo è il contenuto com'è, in un layer senza effetti.
 */
private class FluidGlassPieceContentNode(var piece: FluidGlassPiece) : Modifier.Node(), DrawModifierNode {
  private val path = Path()

  override fun ContentDrawScope.draw() {
    val content = piece.content
    val reference = GlassContentDefaults.BlurReference.toPx()
    // Il margine del layer: tre σ della sfocatura più larga, perché una gaussiana tagliata al bordo sarebbe una macchia
    // squadrata. Fisso per la vita del pezzo: la taglia del layer non cambia quando la sfocatura arriva.
    val margin = ceil(3f * glassContentSigmaPx(GlassContentDefaults.PeakBlur, reference)).toInt()
    val recorded = IntSize(ceil(size.width).toInt() + 2 * margin, ceil(size.height).toInt() + 2 * margin)
    val record: (GraphicsLayer) -> Unit = { layer ->
      layer.topLeft = IntOffset(-margin, -margin)
      recordLayer(this@FluidGlassPieceContentNode, layer, recorded) {
        // Rieseguita dopo che il pezzo se n'è andato (un canvas senza accelerazione rifà il blocco a ogni disegno), non ha
        // più un contenuto da disegnare.
        if (content.attached) translate(margin.toFloat(), margin.toFloat()) { this@draw.drawContent() }
      }
    }
    val context = requireGraphicsContext()
    // Giro di correzione di F3 (D 6): se il pezzo è già alla misura o al posto nuovi e il vetro li prende al fotogramma
    // dopo, si mostra il contenuto di prima dov'era, e i due cambiano insieme.
    val held = heldOffset()
    val shown = if (held == null) content.obtain(context).also(record) else content.hold(context, record)
    val fade = piece.container.contentFade().fastCoerceIn(0f, 1f)
    if (held == null) {
      drawGlassPieceContent(content, shown, fade, path, reference)
    } else {
      translate(held.x, held.y) { drawGlassPieceContent(content, shown, fade, path, reference) }
    }
  }

  /**
   * Di quanto spostare il contenuto di prima perché stia dove il vetro lo mostra ancora, o null se contenuto e vetro
   * sono già d'accordo: a riposo (in viaggio ci pensa il volo), con una misura del contenitore in arrivo al fotogramma
   * dopo ([FluidGlassContainerState.measurePending]), e col pezzo più in là del mezzo pixel dal posto che il vetro
   * mostra ([FluidGlassPieceContent.place]). Fase di disegno.
   */
  private fun heldOffset(): Offset? {
    val content = piece.content
    if (content.flight.value != null || content.layer == null) return null
    val container = piece.container
    if (!container.renders || !container.measurePending) return null
    val box = container.coordinates?.takeIf { it.isAttached } ?: return null
    val node = piece.coordinates?.takeIf { it.isAttached } ?: return null
    val now = box.localBoundingBoxOf(node, clipBounds = false)
    return fluidGlassContentHold(content.place, now)
  }
}

/**
 * Lo spostamento con cui il contenuto registrato quando il pezzo stava in [shown] (il posto che il vetro mostra) si
 * disegna nel nodo che adesso sta in [now], o null se i due coincidono entro mezzo pixel (o [shown] non c'è ancora):
 * vedi `FluidGlassPieceContentNode`. Pura.
 */
internal fun fluidGlassContentHold(shown: Rect, now: Rect): Offset? {
  if (!shown.isFinite || !now.isFinite || !(shown.width > 0f) || !(shown.height > 0f)) return null
  if (rectsAlike(shown, now)) return null
  return Offset(shown.left - now.left, shown.top - now.top)
}

/** Vero se il contenuto di un pezzo, con l'aspetto [flight], non si vede: nascosto, o nato e non ancora comparso. Pura. */
internal fun fluidGlassContentHidden(flight: FluidGlassContentFlight?): Boolean =
  flight != null && flight.look.alpha <= FluidGlassContentInvisibleAlpha

/** Sotto quest'opacità il contenuto di un pezzo non si disegna (`drawGlassPieceContent`), e il pezzo non si tocca. */
internal const val FluidGlassContentInvisibleAlpha: Float = 0.001f

/**
 * Disegna la registrazione di [content] nell'origine del suo pezzo: a riposo com'è; in viaggio con l'opacità, la scala
 * (attorno al centro) e la sfocatura del suo aspetto, spostata di quanto il suo vetro è lontano dal posto d'arrivo e
 * **ritagliata sulla sagoma del vetro in volo**. [fade] è la dissolvenza dell'inchiostro del contenitore.
 */
private fun DrawScope.drawGlassPieceContent(
  content: FluidGlassPieceContent,
  layer: GraphicsLayer?,
  fade: Float,
  path: Path,
  referencePx: Float,
) {
  layer ?: return
  val flight = content.flight.value
  val look = flight?.look ?: GlassContentLook.Sharp
  val alpha = (look.alpha * fade).fastCoerceIn(0f, 1f)
  if (alpha <= FluidGlassContentInvisibleAlpha) return
  layer.alpha = alpha
  layer.scaleX = look.scale
  layer.scaleY = look.scale
  val sigma = glassContentSigmaPx(look.blur, referencePx)
  layer.renderEffect = if (sigma > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val radius = glassContentBlurRadiusPx(sigma)
    BlurEffect(radius, radius, TileMode.Decal)
  } else {
    null
  }
  val offset = flight?.offset ?: Offset.Zero
  val clip = flight?.clip
  if (clip == null) {
    translate(offset.x, offset.y) { drawLayer(layer) }
    return
  }
  val radii = flight.radii
  path.rewind()
  path.addRoundRect(
    RoundRect(
      rect = clip,
      topLeft = CornerRadius(radii.topLeft),
      topRight = CornerRadius(radii.topRight),
      bottomRight = CornerRadius(radii.bottomRight),
      bottomLeft = CornerRadius(radii.bottomLeft),
    ),
  )
  clipPath(path) { translate(offset.x, offset.y) { drawLayer(layer) } }
}

/** Il contenitore che disegnerà il vetro di questo punto, o null (nessun contenitore, o un contenitore che non disegna). */
@Composable
internal fun currentFluidGlassContainer(shape: Shape): FluidGlassContainerState? {
  val container = LocalFluidGlassContainer.current ?: return null
  return container.takeIf { it.renders && glassFieldRenders(shape) }
}

/** L'aspetto di un `fluidGlass` per [glass]: vedi [fluidGlassTint] e [fluidGlassOptics]. */
internal fun fluidGlassLook(
  glass: FluidGlass,
  shape: Shape,
  backdrop: GlassBackdropState,
  baseTint: GlassTint,
  press: FluidPressState?,
  touch: GlassTouchLight?,
  layerBlock: (GraphicsLayerScope.() -> Unit)?,
): FluidGlassLook {
  val role = if (glass.isInteractive) GlassRole.Interactive else GlassRole.Floating
  return FluidGlassLook(
    backdrop = backdrop,
    tint = fluidGlassTint(glass, baseTint),
    role = role,
    optics = fluidGlassOptics(glass).sanitized(),
    variant = glass.liquidVariant,
    shape = shape,
    press = press,
    touch = touch,
    layerBlock = layerBlock,
    // La stessa trasformazione del blocco del layer, per il palco (giro di correzione di F3).
    pressGeometry = if (layerBlock != null) press?.let { FluidGlassPressGeometry(it) } else null,
  )
}

/**
 * L'ottica di serie di un `fluidGlass`: un tasto se reagisce al dito, una superficie flottante altrimenti. La stessa per
 * il limpido (F3.2c): il raggio di sfocatura resta, e la variante della ricetta porta la mescola a nitido (piano 3.6).
 */
internal fun fluidGlassOptics(glass: FluidGlass): GlassOptics =
  if (glass.isInteractive) GlassDefaults.buttonOptics() else GlassDefaults.optics(GlassRole.Floating)

/**
 * Il riquadro di un nodo di superficie sui pixel interi che lo contengono: il nodo si posa a un pixel intero, e le lastre
 * si misurano dal suo angolo vero (mai uno sfasamento di un pixel fra la sagoma e il nodo, nemmeno a coordinate negative).
 */
internal fun Rect.snapped(): Rect = Rect(
  kotlin.math.floor(left),
  kotlin.math.floor(top),
  kotlin.math.ceil(right),
  kotlin.math.ceil(bottom),
)
