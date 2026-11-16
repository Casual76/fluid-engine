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
 *    tutte le superfici; il palco parte un fotogramma dopo il cambio, dalla geometria che il riposo stava mostrando;
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
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
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
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurfaceInked
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneMotion
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneNeckMs
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
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

  /** Nessun viaggio: i vetri vanno al posto nuovo nel fotogramma dopo il cambio. Anche con il movimento ridotto. */
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
 * Limiti dichiarati: una forma che il campo non sa rendere (un `Outline` generico) resta un vetro a sé, fuori dal piano;
 * un contenuto che scorre **dentro** il contenitore è un cambio a ogni fotogramma (il contenitore va intorno alla barra,
 * non alla lista). Sotto Android 13 e con il vetro di prima (`FluidGlassModel.Classic`) ogni pezzo disegna il proprio
 * vetro di sempre, senza viaggi.
 *
 * @param spacing fin dove due vetri sono un grappolo e un vetro nuovo nasce dal vicino: [FluidGlassContainerDefaults.Spacing].
 * @param animation come si animano i cambi: [FluidGlassAnimation.Default].
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

  /** I viaggi partiti (un cambio a riposo), in tutto il processo. */
  val totalJourneys: Long get() = journeys.get()

  /** I viaggi ripartiti a metà (uno stato cambiato mentre il vetro viaggiava). */
  val totalRetargets: Long get() = retargets.get()

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
    orderWarnings.set(0)
    last = emptyList()
    rest = emptyList()
    restLarge = false
    contents = emptyList()
  }

  internal fun onJourney(records: List<FluidGlassFlightRecord>, retarget: Boolean) {
    if (retarget) retargets.incrementAndGet() else journeys.incrementAndGet()
    last = records
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
) {
  override fun toString(): String =
    "FluidGlassPieceRecord($id, $bounds, $cornerRadii, cluster=$clusterSize" +
      "${unionId?.let { ", union=$it" } ?: ""}${if (exports) ", exports" else ""})"
}

// ================================================================================================================
// L'ordine dei modificatori (contratto)
// ================================================================================================================

/** Un `id` qualificato dal suo spazio dei nomi: è questo che il piano confronta. */
internal data class FluidGlassQualifiedId(val namespace: FluidGlassNamespace, val id: Any)

/** Ciò che i modificatori a sinistra di un vetro dicono di lui. */
@Immutable
internal data class FluidGlassAttributes(
  val id: FluidGlassQualifiedId? = null,
  val unionId: FluidGlassQualifiedId? = null,
  val transition: FluidGlassTransition = FluidGlassTransition.MatchedGeometry,
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

  /** Il carattere del viaggio di adesso: vedi il parametro `character`. */
  val journeyCharacter: GlassLiquidJourney get() = character()

  /**
   * Il livello della **luce** del carattere del viaggio adesso, 0..1 (`GlassLiquidJourney.light` alla [phase]): il velo
   * verso la luce, l'alone e la smerigliatura che la ricetta delle superfici fisiche applicherà con
   * `glassJourneyRecipe`. Zero a riposo. **Oggi il palco non lo usa**: l'aggancio alla ricetta è del pacchetto dopo
   * F3.8a, e `FluidGlassContainerState.driveStage` dice dove passarlo. Il livello e la sua entrata sono già qui, provati.
   */
  val journeyLight: Float
    get() {
      val running = journey ?: return 0f
      return character().light(phase, journeyLightEntry, running.settleMs.toFloat())
    }

  /** Il livello del **rigonfiamento** del carattere adesso, 0..1 (`GlassLiquidJourney.swell` alla [phase]): zero a riposo. */
  val journeySwell: Float
    get() = if (journey == null) 0f else character().swell(phase, journeySwellEntry)

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
   */
  fun step(nanos: Long, measured: GlassPlaneState?, motion: GlassPlaneMotion?, durationScale: Float = 1f): Change {
    if (measured == null) return Change.Nothing
    val current = rest
    val running = journey
    val instant = motion == null || !(durationScale > 0f)
    if (running == null) {
      if (current != null && planesAlike(current, measured)) return Change.Nothing
      if (current == null || instant || current.pieces.isEmpty() && measured.pieces.isEmpty()) {
        rest = measured
        return Change.Rest
      }
      origin = current
      rest = measured
      val started = GlassPlaneJourney(current, measured, motion!!)
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
      neck = PhysicsPlaneNeck(-1f, physicsPlaneNeckMs(started.settleMs))
      startUnion(started, from = null)
      return Change.Started
    }
    advance(nanos)
    val elapsed = clockNanos / 1_000_000.0 / durationScale.toDouble()
    if (current != null && !planesAlike(current, measured)) {
      if (instant) {
        finish()
        rest = measured
        return Change.Finished
      }
      // Il collo di adesso, prima di cambiare viaggio: da lì ripartono l'orologio del collo e quello delle unioni.
      elapsedMs = elapsed
      val unionNow = if (unionKind != GlassPlaneUnionChange.None) neckNow(unionKind) else null
      // La luce e il rigonfiamento di adesso, prima di cambiare viaggio: da lì riparte il carattere di quello nuovo.
      val lightNow = journeyLight
      val swellNow = journeySwell
      val restarted = running.retarget(elapsed, measured)
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
      startUnion(restarted, from = unionNow)
      return Change.Retargeted
    }
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
    unionKind = GlassPlaneUnionChange.None
    unionDissolve = null
  }
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

private fun rectsAlike(a: Rect, b: Rect): Boolean =
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
  /** Di quanto esce ogni lato di una lastra col lato corto [shortPx], in pixel. */
  fun growPx(shortPx: Float): Float {
    if (!(level > 0f) || !(density > 0f)) return 0f
    return min(glassJourneySwellDp(shortPx / density, level, character) * density, maxPx)
  }

  /** [slab] rigonfiata; a livello zero è [slab], la stessa istanza. */
  fun of(slab: FluidForm.Slab): FluidForm.Slab = glassJourneySwollen(slab, growPx(slab.frame.minDimension))

  companion object {
    /** Nessun rigonfiamento: a riposo, e nei campi che compaiono o scompaiono per lente. */
    val None = FluidGlassSwell(GlassLiquidJourneys.Off, 0f, 0f, 1f)
  }
}

/**
 * La forma di un campo del palco in un fotogramma (F3.8a): le lastre di [flights] al fotogramma [frame], rigonfiate di
 * [swell], col collo [neck], le unioni stabili, la comparsa del campo e il peso del vetro in viaggio dalla velocità del
 * lato più veloce (`physicsPlaneMotion`). È ciò che il contenitore dà alla superficie fisica di ogni campo a ogni
 * fotogramma, e ciò che le tracce del banco rendono. Pura.
 *
 * [journeyLight] è la luce del carattere del viaggio (F3.9), che la forma porta alla ricetta: di serie zero, perché
 * l'aggancio non c'è ancora (lo fa il pacchetto dopo F3.8a).
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
): PhysicsPlaneShape {
  val shape = fluidGlassFieldSlabs(frame, flights, origin, smoothing)
  // Il lato più veloce del campo, in dp al secondo.
  val speedPx = shape.members.maxOfOrNull { p ->
    val v = p.velocity.bounds
    maxOf(maxOf(abs(v.left), abs(v.right)), maxOf(abs(v.top), abs(v.bottom)))
  } ?: 0f
  val slabs = shape.slabs.take(FluidForm.Group.MaxPieces)
  return PhysicsPlaneShape(
    slabs = if (swell.level > 0f) slabs.map { swell.of(it) } else slabs,
    nominalBlend = spacingPx,
    neck = neck,
    unions = fluidGlassStableUnionIndices(shape.members),
    materialize = shape.materialize,
    motion = physicsPlaneMotion(speedPx / density, movingMs),
    journeyLight = journeyLight,
  )
}

/** Come arriva il vetro di un pezzo, per il suo contenuto: vedi [glassPieceContentFor]. Pura. */
internal fun fluidGlassArrivalOf(change: GlassPlaneChange): GlassPieceArrival = when (change) {
  GlassPlaneChange.BornFromNeighbour -> GlassPieceArrival.BornFromNeighbour
  GlassPlaneChange.Materialize -> GlassPieceArrival.ThroughLens
  GlassPlaneChange.Identity -> GlassPieceArrival.Instant
  else -> GlassPieceArrival.Stays
}

/**
 * Come si disegna in questo fotogramma il contenuto di un pezzo che sta in [place] (nel contenitore), col vetro [flying],
 * la parte [role] e l'aspetto [look] (F3.8a). Null per un contenuto fermo e nitido: è quello di riposo, senza ritaglio.
 *
 * Chi resta ([GlassPieceContentRole.Rest], [GlassPieceContentRole.Recover]) **viaggia col suo vetro**: è spostato di
 * quanto il centro della lastra è lontano dal posto d'arrivo (R7 A3: «nitido, si sposta col suo vetro»). Chi nasce,
 * compare o se ne va sta fermo al suo posto (R7 A5). Tutti sono ritagliati sulla lastra in volo, rigonfiata di [swell]
 * come la disegna il palco: mai un contenuto fuori dal suo vetro. Pura.
 */
internal fun fluidGlassContentFlightOf(
  flying: GlassPlanePieceFrame,
  role: GlassPieceContent,
  look: GlassContentLook,
  place: Rect,
  swell: FluidGlassSwell,
): FluidGlassContentFlight? {
  if (flying.still && look == GlassContentLook.Sharp) return null
  val rides = role.role == GlassPieceContentRole.Rest || role.role == GlassPieceContentRole.Recover
  val lens = flying.change == GlassPlaneChange.Materialize || flying.change == GlassPlaneChange.Dematerialize
  val slab = FluidForm.Slab(flying.bounds, flying.radii)
  val drawn = if (lens) slab else swell.of(slab)
  return FluidGlassContentFlight(
    look = look,
    offset = if (rides) flying.bounds.center - place.center else Offset.Zero,
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

  /** Vero finché il nodo del pezzo c'è: una registrazione rieseguita dopo (un canvas senza accelerazione) non disegna. */
  var attached: Boolean = false

  /** Dove sta il pezzo nel contenitore, all'ultima misura: è lì che il suo contenuto esce quando il pezzo se ne va. */
  var place: Rect = Rect.Zero

  /** La parte che il contenuto fa nel viaggio in corso, e l'ultimo aspetto calcolato. */
  var role: GlassPieceContent = GlassPieceContent.Rest
  var look: GlassContentLook = GlassContentLook.Sharp

  /** L'aspetto di adesso, letto in disegno: null a riposo (nitido, al suo posto, senza ritaglio). */
  val flight = mutableStateOf<FluidGlassContentFlight?>(null)

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
    if (flight.value != null) flight.value = null
  }

  /** Non si vede, finché il piano non decide come entra. */
  fun hide() {
    role = GlassPieceContent.Hidden
    look = GlassContentInvisible
    flight.value = FluidGlassContentFlight.Hidden
  }

  /** Rilascia la registrazione: il pezzo non c'è più e il suo contenuto non si vede più. */
  fun release() {
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
)

/** Un campo del palco. */
@Immutable
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
)

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
    this.density = density
    this.layoutDirection = layoutDirection
    this.spacingPx = spacingPx
    this.motion = motion
    if (changed) invalidate()
  }

  fun attach(piece: FluidGlassPiece) {
    pieces += piece
    piece.content.attached = true
    // In un contenitore già a riposo il pezzo nuovo nasce da un vicino o compare per lente: il suo contenuto aspetta il
    // fotogramma che lo decide, e poi entra col suo vetro (vedi [FluidGlassPieceContent]). Senza, si vedeva pieno, senza
    // vetro, per il fotogramma in cui il pezzo è stato composto. Alla prima composizione del contenitore non c'è niente
    // da aspettare.
    if (renders && driver.rest != null) piece.content.hide()
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
    wake.trySend(Unit)
  }

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
        drawGlassPieceContent(content, fade, departedPath, reference)
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
    val measured = measure() ?: return
    when (driver.step(nanos, measured, motion, durationScale)) {
      FluidGlassPlaneDriver.Change.Nothing -> refreshLooks(measured)
      FluidGlassPlaneDriver.Change.Rest -> settle(measured)
      FluidGlassPlaneDriver.Change.Started -> start(retarget = false)
      FluidGlassPlaneDriver.Change.Retargeted -> start(retarget = true)
      FluidGlassPlaneDriver.Change.Moved -> driveStage()
      FluidGlassPlaneDriver.Change.Finished -> settle(driver.rest ?: measured)
    }
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
        },
      ))
    }
    return registry.snapshot(spacingPx, Rect(0f, 0f, box.size.width.toFloat(), box.size.height.toFloat()))
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
    looksDirty = false
    stage = null
    stagePhysics.clear()
    stageShapes.clear()
    travelProgress.floatValue = 1f
    swell = FluidGlassSwell.None
    publishRest(state)
    // Gli aspetti di chi se n'è andato non servono più: il viaggio che li portava è finito.
    looks.keys.retainAll(state.pieces.mapTo(HashSet()) { it.key })
  }

  private fun refreshLooks(state: GlassPlaneState) {
    // Un aspetto cambiato senza che il piano cambi: a riposo le superfici si rifanno; in viaggio aspetta l'arrivo.
    if (looksDirty && driver.journey == null) {
      looksDirty = false
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

  /** Di quanto il nodo di una superficie fisica si allarga attorno alla sagoma: fin dove arriva l'ombra in corpo. */
  private fun shadowMarginPx(): Float =
    with(density) { glassTravelShadowReachDp(GlassDefaults.buttonOptics().sanitized(), gain = 0f).dp.toPx() }

  // ---- Il palco ----

  private fun start(retarget: Boolean) {
    val journey = driver.journey ?: return
    val target = driver.rest ?: return
    // Il riposo d'arrivo si monta subito, spento: riprenderà il vetro nella composizione in cui il palco lo lascia.
    publishRest(target)
    val margin = shadowMarginPx()
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
    startContents()
    driveStage()
    FluidGlassContainerDiagnostics.onJourney(records(driver.frame), retarget)
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
    // del campo è un tetto al suo. Chi compare o scompare per lente non si gonfia.
    val grow = if (lens) 0f else {
      FluidGlassSwell(driver.journeyCharacter, 1f, FluidMotion.PlaneSwellMaxDp * density.density, density.density)
        .growPx(field.bounds.minDimension)
    }
    return FluidGlassStageField(
      index, field.bounds.inflate(margin + grow).snapped(), look, flights, exports, union = field.union, lens = lens,
    )
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
      val arrival = fluidGlassArrivalOf(flying.change)
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

  /**
   * Porta il fotogramma di adesso sulle superfici del palco: per ogni campo una forma del piano ([PhysicsPlaneShape]) col
   * collo nel tempo ([FluidGlassPlaneDriver.neckNow], F3.3), le unioni, la comparsa del campo e il peso del vetro in
   * viaggio dalla velocità dei suoi lati (`physicsPlaneMotion`), rigonfiata del rigonfiamento del viaggio. E l'aspetto
   * del contenuto di ogni pezzo ([driveContents]).
   */
  private fun driveStage() {
    val frame = driver.frame ?: return
    val stage = stage ?: return
    travelProgress.floatValue = driver.progress
    val movingMs = driver.movingMs.toFloat()
    // Il rigonfiamento del viaggio (punto 8 di F3.8a), con la curva e il numero del carattere di F3.9: la sagoma di chi
    // viaggia esce di poco da ogni lato alla fase di adesso, dal livello con cui il viaggio è cominciato, e rientra
    // prima del riposo. Non per chi compare o scompare per lente, che ha già il suo contorno più largo.
    swell = FluidGlassSwell(
      driver.journeyCharacter, driver.journeySwell, FluidMotion.PlaneSwellMaxDp * density.density, density.density,
    )
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
        // La **luce** del carattere del viaggio (F3.9) è spenta: l'aggancio alla ricetta è del pacchetto dopo F3.8a
        // (`sessioni/f3-9/aggancio.md`). Si accende passando qui
        // `journeyLight = if (field.lens) 0f else driver.journeyLight`, col margine dell'alone in `shadowMarginPx`
        // (`glassTravelShadowReachDp(journey = …)`) e la ricetta in `physicsLiquidRecipe`.
        density = density.density,
        movingMs = movingMs,
      )
      val holder = stageShapes[field.index]
      if (holder == null) stageShapes[field.index] = mutableStateOf(plane) else holder.value = plane
    }
    driveContents(frame)
  }

  /** Il rigonfiamento di adesso: vedi [FluidGlassSwell]. */
  private var swell = FluidGlassSwell.None

  /**
   * L'aspetto del contenuto di ogni pezzo in questo fotogramma (F3.8a): dalla sua parte e dalla fase del volo del suo
   * vetro, ritagliato sulla lastra in volo. Chi resta viaggia con la sua lastra (di quanto il centro si è spostato dal
   * posto d'arrivo); chi nasce, compare o se ne va sta fermo al suo posto, e il vetro lo scopre o lo copre.
   */
  private fun driveContents(frame: GlassPlaneFrame) {
    val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var finished: HashSet<FluidGlassPiece>? = null
    for (flying in frame.pieces) {
      val target = flying.flightId.targetKey as? FluidGlassPiece
      val piece = target ?: (flying.flightId.sourceKey as? FluidGlassPiece)?.takeIf { !it.content.attached } ?: continue
      val content = piece.content
      val role = content.role
      val look = glassPieceContentLook(role, flying.phase, blur)
      content.look = look
      if (target == null && glassPieceContentDone(role, flying.phase)) {
        (finished ?: HashSet<FluidGlassPiece>().also { finished = it }) += piece
        continue
      }
      val flight = fluidGlassContentFlightOf(flying, role, look, content.place, swell)
      if (flight != null || content.flight.value != null) content.flight.value = flight
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
    val physics = rememberFluidPhysicsState(plane.form())
    remember(physics) {
      physics.driveByFrame({ current.value }, AtRest)
      Unit
    }
    val ink = remember { GlassInk() }
    Box(
      gate.fluidPhysicsSurfaceInked(
        state = physics,
        backdrop = look.backdrop,
        tint = look.tint,
        role = look.role,
        optics = look.optics,
        exports = field.exports,
        ink = ink,
        variant = look.variant,
      ),
    )
  }
}

/** Un campo del palco: una superficie fisica guidata dal piano, per tutto il viaggio. */
@Composable
private fun FluidGlassStageSurface(state: FluidGlassContainerState, field: FluidGlassStageField) {
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
  val look = field.look
  Box(
    Modifier
      .layoutId(FluidGlassFieldSlot(field.node))
      .fluidPhysicsSurfaceInked(
        state = physics,
        backdrop = look.backdrop,
        tint = look.tint,
        role = look.role,
        optics = look.optics,
        intensity = presence,
        exports = field.exports,
        ink = ink,
        variant = look.variant,
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
    val layer = content.obtain(requireGraphicsContext())
    layer.topLeft = IntOffset(-margin, -margin)
    val recorded = IntSize(ceil(size.width).toInt() + 2 * margin, ceil(size.height).toInt() + 2 * margin)
    recordLayer(this@FluidGlassPieceContentNode, layer, recorded) {
      // Rieseguita dopo che il pezzo se n'è andato (un canvas senza accelerazione rifà il blocco a ogni disegno), non ha
      // più un contenuto da disegnare.
      if (content.attached) translate(margin.toFloat(), margin.toFloat()) { this@draw.drawContent() }
    }
    drawGlassPieceContent(content, piece.container.contentFade().fastCoerceIn(0f, 1f), path, reference)
  }
}

/**
 * Disegna la registrazione di [content] nell'origine del suo pezzo: a riposo com'è; in viaggio con l'opacità, la scala
 * (attorno al centro) e la sfocatura del suo aspetto, spostata di quanto il suo vetro è lontano dal posto d'arrivo e
 * **ritagliata sulla sagoma del vetro in volo**. [fade] è la dissolvenza dell'inchiostro del contenitore.
 */
private fun DrawScope.drawGlassPieceContent(content: FluidGlassPieceContent, fade: Float, path: Path, referencePx: Float) {
  val layer = content.layer ?: return
  val flight = content.flight.value
  val look = flight?.look ?: GlassContentLook.Sharp
  val alpha = (look.alpha * fade).fastCoerceIn(0f, 1f)
  if (alpha <= 0.001f) return
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
