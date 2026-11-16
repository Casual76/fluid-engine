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
import android.util.Log
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
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
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurfaceInked
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneCluster
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneField
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFlightId
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFrame
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneRegistry
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.GlassTouchLight
import dev.antigravity.fluidengine.ui.glass.field.glassFieldRenders
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import dev.antigravity.fluidengine.ui.glass.field.glassProbe
import dev.antigravity.fluidengine.ui.glass.field.glassProbeAvailable
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp
import dev.antigravity.fluidengine.ui.glass.field.rememberGlassProbeState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
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
   * Fin dove due vetri dello stesso contenitore sono **un grappolo** (una cattura sola, un campo fuso in viaggio) e fin
   * dove un vetro nuovo **nasce dal vicino** invece di comparire per lente.
   *
   * Scelta di gusto aperta (piano, F3: stretta / media / larga; Apple non la documenta, i suoi esempi usano 20 e 40 pt).
   * 16 dp è la media: copre i due vetri di un gruppo (8 dp, [FluidMetrics.GlassSpacing]) e due gruppi vicini (12 dp,
   * [FluidMetrics.GroupSpacing]) con un margine, perché l'arrotondamento al pixel non decida chi si fonde.
   */
  val Spacing: Dp = 16.dp
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
 * **Il contenuto è già al suo posto d'arrivo**: viaggia solo il vetro, il contenuto non si stira mai.
 *
 * **Il lato lo decide il contenitore** (adattamento uniforme): una sonda sola, sotto il grappolo più grande, sceglie
 * chiaro o scuro per tutti i vetri, che girano insieme nello stesso fotogramma. Ed è il **patto dell'inchiostro**: il
 * contenuto dentro il contenitore riceve l'inchiostro del lato (`LocalContentColor`, lo schema di colori del lato e
 * [LocalFluidGlassAppearance]) e, al giro, svanisce e risale con lui. Un `fluidGlass` fuori da un contenitore non gira:
 * il vetro non sa di che colore è ciò che gli sta sopra. Per un vetro solo che gira, un contenitore con un pezzo solo.
 *
 * Il contenuto è posato come in un `Box` (in alto all'inizio, il contenitore grande quanto il più grande): di solito è
 * una `Row` o una `Column`.
 *
 * Limiti dichiarati: i vetri di uno stesso contenitore sono fratelli (un vetro dentro un altro pezzo dello stesso
 * contenitore non ne campiona il materiale); una forma che il campo non sa rendere (un `Outline` generico) resta un vetro
 * a sé, fuori dal piano; un contenuto che scorre **dentro** il contenitore è un cambio a ogni fotogramma (il contenitore
 * va intorno alla barra, non alla lista). Sotto Android 13 e con il vetro di prima (`FluidGlassModel.Classic`) ogni pezzo
 * disegna il proprio vetro di sempre, senza viaggi.
 *
 * @param spacing fin dove due vetri sono un grappolo e un vetro nuovo nasce dal vicino: [FluidGlassContainerDefaults.Spacing].
 * @param animation come si animano i cambi: [FluidGlassAnimation.Default].
 */
@Composable
fun FluidGlassContainer(
  modifier: Modifier = Modifier,
  spacing: Dp = FluidGlassContainerDefaults.Spacing,
  animation: FluidGlassAnimation = FluidGlassAnimation.Default,
  content: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val state = remember { FluidGlassContainerState() }
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  state.configure(
    density = density,
    layoutDirection = layoutDirection,
    spacingPx = with(density) { spacing.coerceAtLeast(0.dp).toPx() },
    motion = if (reducedMotion) null else animation.planeMotion(),
  )

  // Il contenitore disegna i vetri solo dove il materiale nuovo può esistere (Android 13, sorgenti sane, nessun antenato
  // che chieda il vetro di prima): altrove ogni pezzo resta il vetro di sempre. Si chiede per la forma di serie.
  val probeOptics = remember { GlassDefaults.optics(GlassRole.Floating).sanitized() }
  val renders = isRenderEffectSupported() &&
    rememberFluidGlassModel(FluidCapsuleShape, GlassFalloff.Uniform, probeOptics) == FluidGlassModel.Liquid
  state.renders = renders

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
        // La sonda legge ciò che rifrangono i pezzi del grappolo più grande; finché non c'è, la sorgente di questo punto.
        .glassProbe((state.probeBackdrop ?: backdrop).backdrop, probe, enabled = probes, region = state::probeRegion),
    ) { measurables, constraints ->
      val loose = constraints.copy(minWidth = 0, minHeight = 0)
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

  /** Azzera i contatori e i record, per i test. */
  fun resetForTesting() {
    journeys.set(0)
    retargets.set(0)
    orderWarnings.set(0)
    last = emptyList()
    rest = emptyList()
  }

  internal fun onJourney(records: List<FluidGlassFlightRecord>, retarget: Boolean) {
    if (retarget) retargets.incrementAndGet() else journeys.incrementAndGet()
    last = records
  }

  internal fun onRest(records: List<FluidGlassPieceRecord>) {
    rest = records
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

/** Un vetro a riposo: il suo `id` (o null), il riquadro nel contenitore in pixel e i quattro raggi risolti. */
@Immutable
class FluidGlassPieceRecord internal constructor(
  val id: Any?,
  val bounds: Rect,
  val cornerRadii: FluidCornerRadii,
  /** Quanti pezzi ha il suo grappolo: uno = una cattura sua, di più = una cattura per tutti. */
  val clusterSize: Int,
) {
  override fun toString(): String = "FluidGlassPieceRecord($id, $bounds, $cornerRadii, cluster=$clusterSize)"
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
internal class FluidGlassPlaneDriver {
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

  private var startNanos = 0L

  /** Quanto del viaggio è passato, 0..1, per il collo «nel progresso» di Fluid-physics. */
  var progress: Float = 1f
    private set

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
      journey = GlassPlaneJourney(current, measured, motion!!)
      startNanos = nanos
      journeyRevision++
      frame = journey!!.frame(0.0)
      progress = 0f
      return Change.Started
    }
    val elapsed = elapsedMs(nanos, durationScale)
    if (current != null && !planesAlike(current, measured)) {
      if (instant) {
        finish()
        rest = measured
        return Change.Finished
      }
      journey = running.retarget(elapsed, measured)
      origin = measured
      rest = measured
      startNanos = nanos
      journeyRevision++
      frame = journey!!.frame(0.0)
      progress = 0f
      return Change.Retargeted
    }
    val now = running.frame(elapsed)
    if (now.finished) {
      finish()
      return Change.Finished
    }
    frame = now
    progress = if (running.durationMs > 0.0) (elapsed / running.durationMs).toFloat().fastCoerceIn(0f, FluidGlassTravelProgressCap) else 0f
    return Change.Moved
  }

  private fun elapsedMs(nanos: Long, durationScale: Float): Double =
    max(0L, nanos - startNanos) / 1_000_000.0 / durationScale.toDouble()

  private fun finish() {
    journey = null
    origin = null
    frame = null
    progress = 1f
  }
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
 * Le lastre di un campo del palco in questo fotogramma, nelle coordinate del suo nodo (che comincia in [origin]), e
 * quanto del campo c'è (`materialize`). Pura.
 *
 * Un campo fatto **solo** di vetri che compaiono o scompaiono porta la loro rifrazione nell'intensità della superficie,
 * esatta (sono lontani da tutto, quindi di solito uno). In un campo misto la superficie è una sola e l'intensità è del
 * campo: un vetro che compare accanto ad altri si vede dal contorno che si stringe, e si toglie solo sotto
 * [FluidGlassAppearFloor] perché non nasca a piena rifrazione dal nulla.
 */
internal fun fluidGlassFieldSlabs(
  frame: GlassPlaneFrame,
  flights: List<GlassPlaneFlightId>,
  origin: androidx.compose.ui.geometry.Offset,
  smoothing: (GlassPlanePiece) -> Float,
): FluidGlassFieldShape {
  val members = frame.pieces.filter { it.flightId in flights }
  val lens = members.all { it.change == GlassPlaneChange.Materialize || it.change == GlassPlaneChange.Dematerialize }
  val intensity = if (lens) members.maxOfOrNull { it.materialize } ?: 0f else 1f
  val slabs = members
    .filter { lens || it.materialize >= FluidGlassAppearFloor }
    .map {
      FluidForm.Slab(it.bounds.translate(-origin.x, -origin.y), it.radii, smoothing(it.piece))
    }
  return FluidGlassFieldShape(slabs, intensity.fastCoerceIn(0f, 1f))
}

/** Le lastre di un campo e la sua intensità: vedi [fluidGlassFieldSlabs]. */
internal class FluidGlassFieldShape(val slabs: List<FluidForm.Slab>, val intensity: Float)

/** Sotto questa rifrazione un vetro che compare in un campo misto non si disegna ancora: vedi [fluidGlassFieldSlabs]. */
internal const val FluidGlassAppearFloor: Float = 0.05f

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
)

/**
 * Un pezzo del contenitore: un `fluidGlass` o un `glassControlSurface`. È la chiave del registro, e porta ciò che il
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

  override fun toString(): String = "FluidGlassPiece(${attributes.id?.id ?: Integer.toHexString(hashCode())})"
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
)

/** Un campo del palco. */
@Immutable
internal class FluidGlassStageField(
  val index: Int,
  val node: Rect,
  val look: FluidGlassLook,
  val flights: List<GlassPlaneFlightId>,
)

/** Il palco di un viaggio: cambia quando il viaggio parte o riparte, non a ogni fotogramma. */
@Immutable
internal class FluidGlassStage(val revision: Int, val fields: List<FluidGlassStageField>)

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
  private val driver = FluidGlassPlaneDriver()
  private val wake = Channel<Unit>(Channel.CONFLATED)

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

  /** L'intensità di ogni campo del palco, scritta a ogni fotogramma e letta in disegno. */
  private val stageIntensity = HashMap<Int, androidx.compose.runtime.MutableFloatState>()

  /** Il progresso del viaggio per il collo di Fluid-physics, letto in disegno. */
  private val travelProgress = mutableFloatStateOf(1f)

  /** Il riquadro sotto cui legge la sonda unica: il grappolo più grande, nel contenitore. */
  private var probeRect by mutableStateOf<Rect?>(null)

  /** Ciò che rifrangono i pezzi del grappolo più grande: è lì che la sonda unica legge. Letto in composizione. */
  var probeBackdrop: GlassBackdropState? by mutableStateOf(null)
    private set

  fun configure(density: Density, layoutDirection: LayoutDirection, spacingPx: Float, motion: GlassPlaneMotion?) {
    val changed = this.density != density || this.layoutDirection != layoutDirection || this.spacingPx != spacingPx
    this.density = density
    this.layoutDirection = layoutDirection
    this.spacingPx = spacingPx
    this.motion = motion
    if (changed) invalidate()
  }

  fun attach(piece: FluidGlassPiece) {
    pieces += piece
    invalidate()
  }

  fun detach(piece: FluidGlassPiece) {
    pieces -= piece
    registry.remove(piece)
    piece.coordinates = null
    invalidate()
  }

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
    stagePhysics.clear()
  }

  /** Il riquadro della sonda unica (F3-g), in pixel del nodo del contenitore; letto dalla sonda sotto osservazione. */
  fun probeRegion(): Rect? = probeRect

  /** La dissolvenza dell'inchiostro dei pezzi al giro, in disegno: uno senza la decisione del contenitore. */
  fun contentFade(): Float = adaptive?.inkAlpha() ?: 1f

  /** Il progresso del viaggio per Fluid-physics: letto in disegno dalla guida esterna. */
  val progressNow: () -> Float = { travelProgress.floatValue }

  /** L'intensità di un campo del palco, in disegno. */
  fun stageIntensityOf(index: Int): () -> Float {
    val holder = stageIntensity.getOrPut(index) { mutableFloatStateOf(0f) }
    return { holder.floatValue }
  }

  /**
   * Un campo del palco si è composto con [physics]: la guida al fotogramma di adesso **subito**, in composizione, perché
   * il suo primo disegno sia già quello giusto.
   */
  fun bindStage(index: Int, physics: FluidPhysicsState) {
    stagePhysics[index] = physics
    driveStage()
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
    stageIntensity.clear()
    travelProgress.floatValue = 1f
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
        FluidGlassRestField(
          key = cluster.id,
          node = node,
          look = look,
          slabs = cluster.pieces.map {
            FluidForm.Slab(it.bounds.translate(-node.left, -node.top), it.radii, smoothingOf(it))
          },
          shape = null,
        )
      }
    }
    restFields = fields
    val biggest = state.clusters.maxByOrNull { it.bounds.width * it.bounds.height }
    probeRect = biggest?.bounds
    probeBackdrop = biggest?.let { looks[it.pieces.first().key]?.backdrop }
    large = biggest != null &&
      glassLiquidLargeVeilGain(with(density) { biggest.pieces.maxOf { it.bounds.minDimension }.toDp().value }) >= 0.5f
    FluidGlassContainerDiagnostics.onRest(
      state.clusters.flatMap { cluster ->
        cluster.pieces.map { FluidGlassPieceRecord(it.id.appId(), it.bounds, it.radii, cluster.pieces.size) }
      },
    )
  }

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
    stage = FluidGlassStage(
      revision = driver.journeyRevision,
      fields = journey.fields.mapIndexed { index, field -> stageFieldOf(index, field, margin) },
    )
    stageIntensity.keys.retainAll((0 until journey.fields.size).toSet())
    driveStage()
    FluidGlassContainerDiagnostics.onJourney(records(driver.frame), retarget)
  }

  private fun stageFieldOf(index: Int, field: GlassPlaneField, margin: Float): FluidGlassStageField {
    val look = field.flights.firstNotNullOfOrNull { flight ->
      flight.targetKey?.let(looks::get) ?: flight.sourceKey?.let(looks::get)
    } ?: looks.values.first()
    return FluidGlassStageField(index, field.bounds.inflate(margin).snapped(), look, field.flights)
  }

  /** Porta il fotogramma di adesso sulle superfici del palco. */
  private fun driveStage() {
    val frame = driver.frame ?: return
    val stage = stage ?: return
    travelProgress.floatValue = driver.progress
    for (field in stage.fields) {
      val shape = fluidGlassFieldSlabs(frame, field.flights, field.node.topLeft) { smoothingOf(it) }
      stageIntensity.getOrPut(field.index) { mutableFloatStateOf(0f) }.floatValue = shape.intensity
      val physics = stagePhysics[field.index] ?: continue
      val form = fluidGlassFormOf(shape.slabs, spacingPx) ?: continue
      // La guida esterna sulla geometria di questo fotogramma (il piano la calcola da sé, con l'interruzione): la
      // superficie fisica ne fa la finestra, col collo nel progresso del viaggio.
      physics.driveExternally(form, form, progressNow)
    }
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

private fun FluidGlassAnimation.planeMotion(): GlassPlaneMotion? = when (this) {
  FluidGlassAnimation.Default -> GlassPlaneMotion()
  FluidGlassAnimation.None -> null
  is FluidGlassAnimation.Custom -> GlassPlaneMotion(spec = spec)
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
 * ancorata (`FluidBarJourney.active`), generalizzato.
 */
@Composable
private fun FluidGlassFields(state: FluidGlassContainerState) {
  val stage = state.stage
  val resting = stage == null
  for (field in state.restFields) {
    key(field.key) { FluidGlassRestSurface(field, carries = resting) }
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
        exports = null,
        layerBlock = look.layerBlock,
        sampleOnce = false,
        resampleIntervalMillis = 0L,
        frozen = NeverHeld,
        heldWhileMoving = NeverHeld,
        nestInContainer = true,
        touch = look.touch,
        ink = null,
      ),
    )
  } else {
    // Più pezzi: una cattura sola per tutto il grappolo, ogni pezzo una lastra del campo (a riposo separate del tutto).
    val form = remember(field) { fluidGlassFormOf(field.slabs, 0f)!! }
    val physics = rememberFluidPhysicsState(form)
    SideEffect { if (physics.form != form) physics.snapTo(form) }
    val ink = remember { GlassInk() }
    Box(
      gate.fluidPhysicsSurfaceInked(
        state = physics,
        backdrop = look.backdrop,
        tint = look.tint,
        role = look.role,
        optics = look.optics,
        ink = ink,
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
  val intensity = remember(field.index) { state.stageIntensityOf(field.index) }
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
        intensity = intensity,
        ink = ink,
      ),
  )
}

private val FullIntensity: () -> Float = { 1f }
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
    .fluidGlassContentFade(container::contentFade)
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

/**
 * Il contenuto di un pezzo segue la dissolvenza dell'inchiostro del contenitore (in disegno, un fotogramma per volta):
 * un layer solo mentre è a metà strada.
 */
private fun Modifier.fluidGlassContentFade(fade: () -> Float): Modifier = this.drawWithCache {
  val paint = Paint()
  val bounds = Rect(0f, 0f, size.width, size.height)
  onDrawWithContent {
    val amount = fade().fastCoerceIn(0f, 1f)
    when {
      amount <= 0.001f -> Unit
      amount >= 0.999f -> drawContent()
      else -> {
        paint.alpha = amount
        drawContext.canvas.saveLayer(bounds, paint)
        drawContent()
        drawContext.canvas.restore()
      }
    }
  }
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

/** L'ottica di serie di un `fluidGlass`: un tasto se reagisce al dito, una superficie flottante altrimenti. */
internal fun fluidGlassOptics(glass: FluidGlass): GlassOptics {
  val base = if (glass.isInteractive) GlassDefaults.buttonOptics() else GlassDefaults.optics(GlassRole.Floating)
  return if (glass.isClear) base.copy(blurScale = 0f) else base
}

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
