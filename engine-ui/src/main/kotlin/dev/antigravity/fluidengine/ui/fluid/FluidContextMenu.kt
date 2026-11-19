package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidDropWarp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Il menu contestuale dal lato di chi lo apre: il controller, l'ancora che registra la riga e il
// modificatore che unisce le due cose. Il pannello lo disegna `FluidMenuLayer`, la card sollevata
// `FluidLiftedPreview`. Spezzato da `FluidGlassModal.kt` in F5A.0; sollevamento e anteprima in F5A.4.

// ---------------------------------------------------------------------------------------------
// Context menu
// ---------------------------------------------------------------------------------------------

/**
 * A context menu that something else decides when to open.
 *
 * Two pieces instead of one, and the split is not tidiness — it is the only arrangement that works.
 * A `detectTapGestures { onLongPress }` sitting next to a row's own `combinedClickable` never fires:
 * the click handler consumes the gesture first and the long press is simply eaten, silently, which
 * is exactly what happened the first time this shipped. Anything that already handles clicks has to
 * raise the menu from **its own** long-press callback, and that is what [open] is for.
 *
 * [Modifier.fluidContextMenu] is the convenience form for elements that do *not* handle clicks: it
 * pairs this with a gesture of its own.
 */
@Stable
class FluidContextMenuController internal constructor(
  private val host: FluidGlassModalHostState?,
  internal val entry: FluidGlassModalEntry,
  internal val layer: GraphicsLayer,
) {
  internal var actionsProvider: () -> List<FluidContextAction> = { emptyList() }

  /** Whether the element is currently painting itself into [layer] for the overlay to replay. */
  internal var recording by mutableStateOf(false)

  /**
   * Il ponte del dito (3.0): [fluidContextMenuAnchor] lo tiene aggiornato col relè, e [open] gli
   * passa il dito che ha tenuto premuto — il menu si sceglie trascinando e rilasciando su una voce.
   */
  internal val handOff = FluidMenuHandOff()

  /** Cio' che la card sollevata deve sapere oltre all'`entry` (F5A.4): vedi [FluidContextLift]. */
  internal val lift = FluidContextLift()

  val isOpen: Boolean get() = entry.visible

  /** Starts capturing the element's picture. Called on touch-down, long before a menu can open. */
  internal fun arm(pressed: Boolean) {
    recording = pressed || entry.visible
    if (recording) entry.preview = layer
  }

  /**
   * Raises the menu. Returns false when there is nothing to show, so the caller can fall through.
   *
   * Dalla 3.0, se c'e' ancora un dito giu' sull'elemento (il click lungo che apre), il menu se lo
   * prende: trascinando su una voce e rilasciando la si sceglie; rilasciando senza muoversi, o fuori,
   * il menu resta aperto. Il colpo (`Open`) e' di chi chiama, e solo se questa restituisce vero.
   */
  fun open(): Boolean {
    val resolvedHost = host ?: return false
    if (entry.visible) return false
    val list = actionsProvider()
    if (list.isEmpty()) return false
    entry.actions = list
    entry.preview = layer
    entry.openedAt = resolvedHost.nextSequence()
    entry.visible = true
    entry.lifted = true
    recording = true
    entry.selection.cancel()
    handOff.attach(entry.selection)
    return true
  }

  fun dismiss() {
    entry.visible = false
    recording = false
  }
}

/**
 * Il sollevamento di un menu contestuale, per la card che lo disegna (F5A.4).
 *
 * Tre cose che l'`entry` dell'host non porta: la **scala viva** dell'elemento sotto il dito, perche' la card
 * parta esattamente da dove l'elemento si era gonfiato (prima partiva dalla riga a riposo, e al click lungo la
 * riga rimpiccioliva del 2% in un fotogramma prima di sollevarsi); l'**anteprima personalizzata** e la **forma**
 * del ritaglio, i due parametri di `contextMenu(menuItems:preview:)` e `contentShape(.contextMenuPreview, _)`.
 *
 * Vive accanto all'`entry` in [FluidContextLifts] e non dentro di lei perche' `FluidGlassModalEntry` sta
 * nell'host (`FluidGlassModalHost.kt`), che e' di un altro pacchetto: e' il posto giusto, e ci si sposta quando
 * l'host si riapre (F5B.1).
 */
@Stable
internal class FluidContextLift {
  /**
   * La scala dell'elemento in questo istante: 1 a riposo, fino a `1 + FluidContextAnchorSwell` sotto il dito.
   * Letta in disegno (legge la pressione), quindi la card la segue senza ricomposizioni: alla partenza vale il
   * gonfiore del dito, e quando il dito si alza torna a 1 sotto la card gia' sollevata, dove non si vede.
   */
  var anchorScale: () -> Float = { 1f }

  /** La forma della card e del ritaglio dell'anteprima; null = il raggio dei pannelli ([FluidPopoverRadius]). */
  var previewShape: Shape? by mutableStateOf(null)

  /** L'anteprima personalizzata: al posto dell'elemento registrato, la card diventa questa. */
  var preview: (@Composable () -> Unit)? by mutableStateOf(null)

  /**
   * La taglia dell'anteprima personalizzata, misurata dall'host: decide dove si posa la card e quindi dove si
   * apre il menu. Null finche' non e' misurata.
   */
  var previewSize: IntSize? by mutableStateOf(null)
}

/**
 * Il registro dei sollevamenti, per `entry`. Debole, e svuotato quando il controller esce dalla composizione:
 * un'ancora sparita non tiene in vita niente.
 */
internal object FluidContextLifts {
  private val byEntry = WeakHashMap<FluidGlassModalEntry, FluidContextLift>()

  fun of(entry: FluidGlassModalEntry): FluidContextLift? = byEntry[entry]

  internal fun attach(entry: FluidGlassModalEntry, lift: FluidContextLift) {
    byEntry[entry] = lift
  }

  internal fun detach(entry: FluidGlassModalEntry) {
    byEntry.remove(entry)
  }
}

/**
 * The state behind a context menu, so a component that owns its own gestures can raise one.
 *
 * Pair it with [Modifier.fluidContextMenuAnchor] on the element the menu belongs to, and call
 * [FluidContextMenuController.open] from that element's long-press.
 */
@Composable
fun rememberFluidContextMenu(
  actions: () -> List<FluidContextAction>,
  /**
   * Il colore della cosa che ha alzato il menu, se ne ha uno.
   *
   * Un menu che nasce da una superficie colorata e arriva grigio si stacca da cio' che l'ha
   * chiamato: il vetro tinto lo tiene attaccato all'oggetto, che e' l'unico motivo per cui quel
   * menu e' li'.
   */
  tint: Color? = null,
  /**
   * La forma della card sollevata e del ritaglio dell'anteprima, come `contentShape(.contextMenuPreview, _)`:
   * la sagoma del vetro e il ritaglio dell'immagine coincidono, cosi' gli angoli non cambiano durante il
   * sollevamento. Null: il raggio dei pannelli.
   */
  previewShape: Shape? = null,
  /**
   * Un'anteprima diversa dall'elemento, come `contextMenu(menuItems:preview:)`: l'elemento si solleva e la card
   * diventa questa, alla sua taglia, centrata su di lui; il menu si apre accanto all'anteprima. Null: la card e'
   * l'elemento stesso, registrato.
   */
  preview: (@Composable () -> Unit)? = null,
): FluidContextMenuController {
  val host = LocalFluidGlassModalHostState.current
  val entry = remember { FluidGlassModalEntry() }
  val layer = rememberGraphicsLayer()
  val controller = remember(host, entry, layer) {
    FluidContextMenuController(host, entry, layer)
  }

  SideEffect {
    controller.actionsProvider = actions
    entry.presentation = FluidGlassModalPresentation.ContextMenu
    entry.paneTint = tint
    entry.onDismissRequest = controller::dismiss
    entry.menuHandOff = controller.handOff
    controller.lift.previewShape = previewShape
    if (controller.lift.preview !== preview) {
      controller.lift.preview = preview
      // Un'anteprima nuova si rimisura: la taglia vecchia posterebbe la card e il menu nel posto sbagliato.
      controller.lift.previewSize = null
    }
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    FluidContextLifts.attach(entry, controller.lift)
    onDispose {
      // The recording belongs to this node and dies with it. Clearing the host's reference first is
      // what stops the overlay from replaying a layer the runtime has already released.
      entry.preview = null
      entry.visible = false
      FluidContextLifts.detach(entry)
      host?.unregister(entry)
    }
  }

  return controller
}

/**
 * Marks the element a [FluidContextMenuController] belongs to.
 *
 * Reports where it is, and paints it into a `GraphicsLayer` while a finger is on it so the overlay
 * has a finished picture to lift. The press is observed on the **initial** pass and nothing is
 * consumed, so this can sit under a row that handles its own clicks without taking anything away
 * from it — which is the entire reason the capture is separate from the gesture.
 *
 * Recording only runs between touch-down and touch-up, so a list of these costs nothing at rest.
 */
@Composable
fun Modifier.fluidContextMenuAnchor(controller: FluidContextMenuController): Modifier {
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // The same specular hotspot the glass controls carry, for the same reason: a row that is about to
  // become a pane of glass has to answer the finger like one *before* it does. Without it a long
  // press is a blank half-second and then a whole overlay, with nothing in between to say the press
  // was even received — and half a second of nothing is how long it takes to decide a control
  // is broken and lift your finger off it.
  val onDark = GlassDefaults.isDarkSurface()
  val highlight = remember(scope, reducedMotion, onDark) {
    GlassTouchHighlight(
      animationScope = scope,
      strength = { if (reducedMotion) 0f else 1f },
      onDarkSurface = onDark,
    )
  }
  // La scala viva per la card (F5A.4): la stessa formula del `graphicsLayer` qui sotto, letta dall'host.
  val anchorScale = remember(highlight, reducedMotion) {
    { if (reducedMotion) 1f else fluidContextAnchorScale(highlight.pressProgress) }
  }
  SideEffect { controller.lift.anchorScale = anchorScale }
  return this
    .onGloballyPositioned {
      val rect = it.boundsInRoot()
      controller.entry.previewSize = rect
      controller.entry.origin = { rect }
    }
    // Il relè del dito, prima di qualunque gesto dell'elemento (3.0): e' sul percorso che Compose ha
    // scelto al down, l'unico che vede i movimenti dopo che il menu e' comparso sopra. Mentre il menu
    // ha il dito, consuma: la lista attorno resta ferma.
    .fluidMenuPointerRelay(controller.handOff)
    .pointerInput(controller) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          controller.arm(event.changes.any { it.pressed })
        }
      }
    }
    .graphicsLayer {
      // Swells under the finger, the way the navigation pill does, and settles back when it is let
      // go. Rows normally must not scale — shrinking one row of a stack breaks the group's
      // silhouette — but this one is on its way *out* of the stack, and growing says so. Attorno al
      // centro: la card sollevata cresce attorno allo stesso punto e parte da questa scala (F5A.4).
      val swell = anchorScale()
      scaleX = swell
      scaleY = swell
    }
    // Ahead of the capture, not inside it. The trail belongs to the finger and the capture is a
    // still: record the glow too and the lifted pane arrives with a bright smear frozen onto it,
    // sitting wherever the finger happened to be on the frame the press was recognised.
    .then(highlight.modifier)
    .then(highlight.gestureModifier)
    .drawWithContent {
      // Lifted: the row is *in the overlay now*, and drawing it here as well is what made the
      // menu look broken. The copy in front is a pane of glass, so the row still sitting in the
      // page was being refracted through it and arriving a second time, blurred and a few pixels
      // off — every line of it doubled behind itself. The space stays reserved, because this is a
      // draw and not a layout, so nothing below it moves while the menu is up.
      if (controller.entry.lifted) return@drawWithContent
      if (controller.recording) {
        controller.layer.record { this@drawWithContent.drawContent() }
        drawLayer(controller.layer)
      } else {
        drawContent()
      }
    }
}

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a darkened, lightly frosted scrim so the menu has
 * somewhere to be, the element itself is redrawn *in its own place* and lifted, and the actions grow
 * out of the edge nearest it.
 *
 * The lifted element is not the same instance — that one is still in the list, under the scrim. It
 * is a **recording**, replayed one level up. Recording costs nothing while nobody is touching the
 * element, because it only starts on the finger going down and stops when it comes back up.
 *
 * Use this only on something that does **not** already handle clicks. On anything that does — a
 * list row, a card with an `onClick` — the click handler consumes the gesture and this never fires;
 * use [rememberFluidContextMenu] with [fluidContextMenuAnchor] and open the menu from that
 * element's own long-press instead. [dev.antigravity.fluidengine.ui.theme.FluidListRow] does exactly
 * that behind its `contextActions` parameter.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 *
 * @param previewShape la forma della card sollevata e del ritaglio (`contentShape(.contextMenuPreview, _)`).
 * @param preview un'anteprima al posto dell'elemento (`contextMenu(menuItems:preview:)`): la card la mostra
 *   alla sua taglia, centrata sull'elemento, e il menu si apre accanto a lei.
 */
@Composable
fun Modifier.fluidContextMenu(
  actions: () -> List<FluidContextAction>,
  enabled: Boolean = true,
  previewShape: Shape? = null,
  preview: (@Composable () -> Unit)? = null,
): Modifier {
  val controller = rememberFluidContextMenu(actions, previewShape = previewShape, preview = preview)
  val haptics = LocalFluidHaptics.current
  if (!enabled) return this
  return this
    .fluidContextMenuAnchor(controller)
    .pointerInput(controller) {
      detectTapGestures(
        onLongPress = {
          if (controller.open()) haptics.play(FluidHapticEvent.Open)
        },
      )
    }
}

// ---------------------------------------------------------------------------------------------
// La geometria del sollevamento (F5A.4): funzioni pure, provate da `FluidContextLiftTest`.
// ---------------------------------------------------------------------------------------------

/** How much a row swells while it is being held. A few percent: it is a row, not a card yet. */
internal const val FluidContextAnchorSwell = 0.02f

/** La scala dell'elemento tenuto premuto, alla corsa [pressProgress] della pressione (0..1). */
internal fun fluidContextAnchorScale(pressProgress: Float): Float =
  1f + FluidContextAnchorSwell * pressProgress.coerceIn(0f, 1f)

/** How much the lifted row grows. Any more and it stops being the row and becomes a card. */
internal const val FluidLiftedPreviewScale = 1.03f

/**
 * La card sollevata a riposo, senza anteprima personalizzata: l'elemento gonfiato di [scale] **attorno al suo
 * centro**, lo stesso punto attorno a cui si gonfia sotto il dito ([fluidContextMenuAnchor]). Fino alla 4.0 la
 * card cresceva dal bordo basso e partiva dalla riga a riposo: la riga gonfiata del 2% sotto il dito tornava
 * indietro in un fotogramma prima di sollevarsi.
 */
internal fun fluidLiftedFrame(source: Rect, scale: Float = FluidLiftedPreviewScale): Rect {
  val c = source.center
  val hw = source.width * scale / 2f
  val hh = source.height * scale / 2f
  return Rect(c.x - hw, c.y - hh, c.x + hw, c.y + hh)
}

/**
 * Dove si posa un'anteprima personalizzata di taglia [size]: centrata sull'elemento [source], dentro lo schermo
 * [host] a [marginPx] dai bordi. Piu' grande dello schermo meno i margini non diventa: la taglia la limita gia'
 * chi la misura ([fluidContextPreviewMaxSize]).
 */
internal fun fluidContextPreviewFrame(source: Rect, size: Size, host: Size, marginPx: Float): Rect {
  val w = size.width.coerceIn(1f, (host.width - marginPx * 2f).coerceAtLeast(1f))
  val h = size.height.coerceIn(1f, (host.height - marginPx * 2f).coerceAtLeast(1f))
  val left = (source.center.x - w / 2f).coerceIn(marginPx, (host.width - marginPx - w).coerceAtLeast(marginPx))
  val top = (source.center.y - h / 2f).coerceIn(marginPx, (host.height - marginPx - h).coerceAtLeast(marginPx))
  return Rect(left, top, left + w, top + h)
}

/**
 * Il tetto della taglia di un'anteprima personalizzata: larga quanto lo schermo meno i margini, alta al piu'
 * [FluidContextPreviewMaxHeightFraction] dello schermo, perche' il menu deve stare accanto a lei.
 */
internal fun fluidContextPreviewMaxSize(host: Size, marginPx: Float): Size = Size(
  (host.width - marginPx * 2f).coerceAtLeast(1f),
  ((host.height - marginPx * 2f) * FluidContextPreviewMaxHeightFraction).coerceAtLeast(1f),
)

/** Quanta altezza dello schermo puo' prendere un'anteprima personalizzata. Il resto e' del menu. */
internal const val FluidContextPreviewMaxHeightFraction = 0.55f

/**
 * Dove si posa la card: l'anteprima personalizzata se c'e' ed e' misurata, se no l'elemento sollevato.
 */
internal fun fluidContextLiftTarget(source: Rect, previewSize: Size?, host: Size, marginPx: Float): Rect =
  if (previewSize == null) fluidLiftedFrame(source) else fluidContextPreviewFrame(source, previewSize, host, marginPx)

/**
 * Da che punto del viaggio fra [source] e [target] parte la card, perche' alla partenza sia larga quanto
 * l'elemento gonfiato sotto il dito ([anchorScale]): la frazione di strada che quel gonfiore ha gia' fatto.
 *
 * Esatta senza anteprima personalizzata (la card e' una scala dell'elemento attorno allo stesso centro, e il
 * gonfiore e' gia' un pezzo di quella scala); con un'anteprima di un'altra taglia e' esatta sulla larghezza, e
 * l'altezza e' quella del viaggio. Zero se il viaggio non allarga (un'anteprima piu' stretta dell'elemento): li'
 * la card parte dall'elemento a riposo.
 */
internal fun fluidLiftStartFraction(source: Rect, target: Rect, anchorScale: Float): Float {
  val grow = target.width - source.width
  if (grow <= 0.5f || anchorScale <= 1f) return 0f
  return ((anchorScale - 1f) * source.width / grow).coerceIn(0f, 1f)
}

/**
 * Il progresso della card al progresso [p] del menu, partendo da [start] ([fluidLiftStartFraction]): da [start]
 * a 1 con la stessa molla, oltrepasso compreso. All'andata [start] e' il gonfiore del dito; al ritorno il dito
 * e' alzato, [start] e' zero e la card finisce esattamente sull'elemento a riposo.
 */
internal fun fluidLiftProgress(p: Float, start: Float): Float = start + (1f - start) * p

/**
 * I raggi della card per [shape] su un rettangolo di taglia [size]: quelli della forma se e' una forma ad
 * angoli (`RoundedCornerShape`, `CircleShape`, [ContinuousCornerShape]) o un rettangolo; per una forma libera
 * il raggio [fallbackPx], e il ritaglio dell'immagine resta la forma vera. Mai oltre mezzo lato corto.
 */
internal fun fluidContextPreviewRadii(
  shape: Shape?,
  size: Size,
  density: Density,
  layoutDirection: LayoutDirection,
  fallbackPx: Float,
): FluidCornerRadii {
  val cap = (minOf(size.width, size.height) / 2f).coerceAtLeast(0f)
  fun c(v: Float) = v.coerceIn(0f, cap)
  if (shape == null || size.width <= 0f || size.height <= 0f) return FluidCornerRadii.all(c(fallbackPx))
  if (shape is CornerBasedShape) {
    val ts = shape.topStart.toPx(size, density)
    val te = shape.topEnd.toPx(size, density)
    val be = shape.bottomEnd.toPx(size, density)
    val bs = shape.bottomStart.toPx(size, density)
    val ltr = layoutDirection == LayoutDirection.Ltr
    return FluidCornerRadii(
      topLeft = c(if (ltr) ts else te),
      topRight = c(if (ltr) te else ts),
      bottomRight = c(if (ltr) be else bs),
      bottomLeft = c(if (ltr) bs else be),
    )
  }
  return when (val outline = shape.createOutline(size, layoutDirection, density)) {
    is Outline.Rectangle -> FluidCornerRadii.Zero
    is Outline.Rounded -> FluidCornerRadii(
      topLeft = c(outline.roundRect.topLeftCornerRadius.x),
      topRight = c(outline.roundRect.topRightCornerRadius.x),
      bottomRight = c(outline.roundRect.bottomRightCornerRadius.x),
      bottomLeft = c(outline.roundRect.bottomLeftCornerRadius.x),
    )
    is Outline.Generic -> FluidCornerRadii.all(c(fallbackPx))
  }
}

// ---------------------------------------------------------------------------------------------
// La nascita del menu contestuale (sessione «contestuale», 2026-11-19): B1 (b) di Apple, WWDC25 219 a 7:15, il tasto
// graffetta che diventa menu sulla foto, letto a mano fotogramma per fotogramma sul 4K (f13046 -> f13072, 29,97 fps,
// `riferimento-apple.md`). Funzioni pure, provate da `FluidContextBirthTest`.
// ---------------------------------------------------------------------------------------------

/**
 * Una curva misurata: punti (ms, valore) uniti da una cubica monotona (Fritsch-Carlson).
 *
 * Perche' non una molla: la sagoma di Apple non e' una molla sola. Il bordo lontano corre, il bordo vicino si stacca dalla
 * sorgente e ci torna, la larghezza parte in ritardo e supera piu' tardi dell'altezza; tre molle tarate a occhio darebbero
 * tre errori. I punti sono le misure, e fra due punti la cubica monotona non inventa rimbalzi che i fotogrammi non hanno
 * (una spline qualunque oscillerebbe fra 400 e 534 ms, dove Apple e' quasi ferma). Fuori dai capi vale il capo.
 */
internal class FluidMeasuredCurve(private val ms: FloatArray, private val values: FloatArray) {
  private val slopes = FloatArray(ms.size)

  init {
    require(ms.size == values.size && ms.size >= 2) { "una curva vuole almeno due punti, tanti tempi quanti valori" }
    val n = ms.size
    val d = FloatArray(n - 1) { (values[it + 1] - values[it]) / (ms[it + 1] - ms[it]) }
    slopes[0] = d[0]
    slopes[n - 1] = d[n - 2]
    for (i in 1 until n - 1) slopes[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    // Fritsch-Carlson: dove la tangente sfora, si accorcia; cosi' fra due punti monotoni la curva resta monotona.
    for (i in 0 until n - 1) {
      if (d[i] == 0f) {
        slopes[i] = 0f
        slopes[i + 1] = 0f
        continue
      }
      val a = slopes[i] / d[i]
      val b = slopes[i + 1] / d[i]
      val s = a * a + b * b
      if (s > 9f) {
        val k = 3f / kotlin.math.sqrt(s)
        slopes[i] = k * a * d[i]
        slopes[i + 1] = k * b * d[i]
      }
    }
  }

  fun at(t: Float): Float {
    if (t <= ms[0]) return values[0]
    val last = ms.size - 1
    if (t >= ms[last]) return values[last]
    var i = 0
    while (t > ms[i + 1]) i++
    val h = ms[i + 1] - ms[i]
    val x = (t - ms[i]) / h
    val x2 = x * x
    val x3 = x2 * x
    return (2f * x3 - 3f * x2 + 1f) * values[i] + (x3 - 2f * x2 + x) * h * slopes[i] +
      (-2f * x3 + 3f * x2) * values[i + 1] + (x3 - x2) * h * slopes[i + 1]
  }
}

/**
 * Le misure di B1 (b), nel sistema del pannello a riposo: `u` lungo l'asse della crescita (0 = il bordo vicino alla
 * sorgente a riposo, 1 = il bordo lontano, in frazioni dell'altezza del pannello), `v` di traverso (0 = il lato della
 * sorgente, 1 = l'altro, in frazioni della larghezza). Tempi dal primo fotogramma che si muove (f13046, t = 0).
 *
 * Il menu di Apple e' alto 720 px e largo 495 px sul ritaglio a mezza risoluzione del 4K; ogni valore e' letto a ±3 px
 * (±0,4% dell'altezza). Cosa si vede: a 0-33 ms la capsula di tre tasti si stringe verso la graffetta; a 67 ms e' una goccia
 * inclinata (33% x 21% del menu) col punto di luce; a 100 ms un fagiolo con le voci minuscole dentro; fino a 234 ms il corpo
 * cresce verso l'alto e a destra **staccandosi dalla barra** (il bordo vicino sale del 15%); poi il bordo vicino torna giu',
 * quello lontano supera del 3% fra 367 e 467 ms, la larghezza del 3% fra 400 e 534 ms, e il menu e' fermo a ~770 ms.
 */
internal object FluidContextBirthProfile {
  /** I tempi dei fotogrammi letti, in ms: f13046 + k, k = 0..12, 14, 16, 18, 20, 23, 26 (29,97 fps). */
  private val Ms = floatArrayOf(
    0f, 33.4f, 66.7f, 100.1f, 133.5f, 166.8f, 200.2f, 233.6f, 266.9f, 300.3f, 333.7f, 367.0f, 400.4f,
    467.1f, 533.9f, 600.6f, 667.3f, 767.4f, 867.5f,
  )

  /** Il bordo vicino (quello della sorgente), in `u`: si stacca fino a 0,15 a 234 ms e torna a 0 a ~600 ms. */
  val Near = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.007f, 0.014f, 0.019f, 0.039f, 0.069f, 0.100f, 0.128f, 0.153f, 0.139f, 0.121f, 0.093f, 0.072f, 0.053f, 0.024f, 0.006f, 0f, 0f, 0f, 0f),
  )

  /** Il bordo lontano, in `u`: 0,146 e' la capsula; supera 1 a 334 ms, picco 1,031 a 467, fermo a 767. */
  val Far = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.146f, 0.142f, 0.225f, 0.385f, 0.542f, 0.683f, 0.794f, 0.878f, 0.938f, 0.975f, 1.003f, 1.028f, 1.028f, 1.031f, 1.019f, 1.010f, 1.003f, 1f, 1f),
  )

  /** La larghezza, in frazioni del pannello: la capsula 0,55 si raccoglie a 0,32 (100 ms), poi cresce e supera del 3%. */
  val Width = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.546f, 0.404f, 0.332f, 0.322f, 0.469f, 0.610f, 0.737f, 0.838f, 0.909f, 0.964f, 1.004f, 1.026f, 1.030f, 1.032f, 1.030f, 1.024f, 1.016f, 1.004f, 1f),
  )

  /** Dove si legge la sorgente di Apple a t = 0 (la capsula): il nostro seme la sostituisce in [BlendMillis]. */
  val SourceNear: Float get() = Near.at(0f)
  val SourceFar: Float get() = Far.at(0f)
  val SourceWidth: Float get() = Width.at(0f)

  /**
   * In quanti ms la sagoma passa dal **nostro** seme (la goccia sotto il dito, appesa alla card) al profilo di Apple.
   * La sorgente di Apple e' una capsula larga mezzo menu; la nostra e' una goccia di 28 dp: per i primi 100 ms il profilo
   * parte dal seme, dai 100 ms in poi e' Apple tal quale (a 67 ms la goccia di Apple e' gia' la capsula raccolta).
   */
  const val BlendMillis = 100f

  /** Fine della nascita: il menu di Apple e' fermo (l'ultimo punto letto, f13072). */
  const val EndMillis = 867.5f

  /**
   * L'anticipo della sagoma chiesta sulla sagoma che si vede, in ms, per ogni bordo: la goccia (`FluidPhysicsLiquid`)
   * insegue la sagoma a riposo con molle morbide lontano dall'ancora: senza anticipo il bordo lontano arrivava in ritardo
   * di 18 ms a 100 ms e di 51 ms a 334 ms, la larghezza di 15 e 41 ms (misurati sull'anello delle tracce del banco della
   * sessione, «ritardo_goccia.py»); col primo anticipo restava indietro di 7-18 ms fra 100 e 300 ms, ed e' questo il
   * secondo. Si chiede quindi la sagoma di Apple **di qualche ms dopo**, perche' quella che si vede sia di Apple
   * all'istante giusto. Zero all'inizio: la nascita parte dal seme.
   */
  private val LeadMs = floatArrayOf(0f, 50f, 100.1f, 133.5f, 166.8f, 200.2f, 233.6f, 266.9f, 300.3f, 333.7f, 400.4f, 867.5f)
  val FarLead = FluidMeasuredCurve(LeadMs, floatArrayOf(0f, 12f, 25f, 28f, 38f, 49f, 56f, 62f, 58f, 52f, 51f, 51f))
  val WidthLead = FluidMeasuredCurve(LeadMs, floatArrayOf(0f, 6f, 14f, 25f, 35f, 40f, 47f, 46f, 47f, 45f, 41f, 41f))
  val NearLead = FluidMeasuredCurve(LeadMs, floatArrayOf(0f, 0f, 4f, 8f, 10f, 12f, 12f, 12f, 12f, 12f, 12f, 12f))

  /**
   * Gli angoli in viaggio non arrivano mai a mezzo lato: al 78%. Il ritaglio delle voci sulla goccia
   * (`dropInnerClip`) prende ogni lato dai punti dell'anello che stanno sui **tratti dritti** della sagoma a riposo; uno
   * stadio (raggio = mezzo lato) non ne ha su due lati, e li' il ritaglio restava il bordo a riposo mentre la goccia era
   * ancora indietro: 8-14 px di voci fuori dal vetro a 183-283 ms nella prima prova, 1 px all'82%. Il seme parte tondo
   * (fino a 30 ms) e a 80 ms e' gia' al 78%: a occhio la goccia, che arrotonda da se', non cambia.
   */
  const val RoundnessInTravel = 0.78f

  /**
   * Quanto dell'oltrepasso di Apple si chiede alla sagoma a riposo: la goccia ci aggiunge il suo (~0,5% sui due assi,
   * misurato come sopra), e chiedendolo tutto il vetro supererebbe del 3,6% invece che del 3,1-3,2%.
   */
  const val OvershootShare = 0.85f

  /**
   * Gli angoli: lontani al raggio del pannello fra 200 e 367 ms, vicini (la coda verso la sorgente) fra 267 e 434 ms. In
   * Apple sono quasi quelli finali a 334 ms; qui si chiedono un fotogramma di Apple piu' tardi, perche' un angolo che si
   * stringe in fretta corre piu' della goccia che lo insegue, e il ritaglio delle voci (sul raggio chiesto) sporgeva di un
   * pixel dalla goccia a 217 ms. La goccia, che arriva in ritardo, li mostra comunque attorno a 367 ms.
   */
  const val FarCornersFrom = 200.2f
  const val FarCornersTo = 367.0f
  const val NearCornersFrom = 266.9f
  const val NearCornersTo = 434.0f

  /** Fin dove sale il progresso prima di arrivare al pannello: la fine della salita del bordo lontano (334 ms). */
  const val RiseEndMillis = 333.7f

  // --- Il contorno (giro di correzione del 2026-11-19): la sagoma di B1 (b), non un rettangolo che cambia misura -----

  /*
   * Letti su B1 (b) fotogramma per fotogramma (`riferimento-apple.md`, «Il contorno»), nel sistema dell'asse che va dalla
   * sorgente (la coda) al lato lontano (la testa). Diventano una [FluidDropWarp] sul contorno a riposo della goccia:
   *  - 67 ms: la goccia sulla sorgente, grassa li' e piu' stretta verso dove crescera' (la punta), e tutta lente;
   *  - 100 ms: il fagiolo, con la vita;
   *  - 133–200 ms: l'uovo con la testa lontana e la coda verso la sorgente;
   *  - 234–300 ms: il corpo panciuto, a botte;
   *  - 334 ms: il rettangolo; da 400 ms nessuna deformazione (a menu aperto il contorno e' la lastra).
   */
  private val WarpMs = floatArrayOf(0f, 33.4f, 66.7f, 100.1f, 133.5f, 166.8f, 200.2f, 233.6f, 266.9f, 300.3f, 333.7f, 367.0f, 400.4f)

  /** Quanto si stringe la coda verso la sorgente: la larghezza al capo della coda e' `1 − TailTaper` del corpo. */
  val TailTaper = FluidMeasuredCurve(WarpMs, floatArrayOf(0f, 0f, 0f, 0f, 0.50f, 0.55f, 0.42f, 0.22f, 0.10f, 0.04f, 0f, 0f, 0f))

  /** Fin dove arriva la coda, in frazioni dell'asse dalla sorgente. */
  val TailReach = FluidMeasuredCurve(WarpMs, floatArrayOf(0.7f, 0.7f, 0.7f, 0.7f, 0.75f, 0.65f, 0.5f, 0.4f, 0.35f, 0.3f, 0.3f, 0.3f, 0.3f))

  /** La punta verso dove la goccia crescera': a 67 ms la goccia di Apple e' grassa sulla sorgente e piu' stretta in avanti. */
  val HeadTaper = FluidMeasuredCurve(WarpMs, floatArrayOf(0f, 0.10f, 0.35f, 0.08f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))

  /** La vita del fagiolo a 100 ms. */
  val Waist = FluidMeasuredCurve(WarpMs, floatArrayOf(0f, 0f, 0f, 0.15f, 0.03f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))

  /** La pancia: i capi piu' stretti del mezzo, il corpo a botte di 234–300 ms. */
  val Belly = FluidMeasuredCurve(WarpMs, floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.01f, 0.03f, 0.05f, 0.06f, 0.04f, 0.02f, 0.01f, 0f))

  /**
   * La lente della goccia (giro di correzione, B1 b a 67–117 ms): la goccia piccola e' una lente intera, limpida, che
   * ingrandisce cio' che ha sotto; da 133 ms torna verso il vetro del profilo, che raggiunge a ~200 ms. [Lens] e' la
   * soglia della lente in moto, [Magnify] l'ingrandimento del corpo (il profilo in viaggio ne ha al piu' 0,035), [Clarity]
   * quanto la goccia e' limpida in piu'.
   */
  val Lens = FluidMeasuredCurve(WarpMs, floatArrayOf(0.3f, 1f, 1f, 1f, 0.6f, 0.3f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f))
  val Magnify = FluidMeasuredCurve(WarpMs, floatArrayOf(0.05f, 0.18f, 0.25f, 0.22f, 0.12f, 0.06f, 0.02f, 0f, 0f, 0f, 0f, 0f, 0f))
  val Clarity = FluidMeasuredCurve(WarpMs, floatArrayOf(0.2f, 0.6f, 0.7f, 0.6f, 0.35f, 0.15f, 0.04f, 0f, 0f, 0f, 0f, 0f, 0f))

  /** Da qui in poi il contorno e' quello della lastra: nessuna deformazione. */
  const val WarpEndMillis = 400.4f

  /**
   * Le proporzioni della goccia di Apple (larghezza / altezza, in pixel, B1 b): la goccia piccola e' tonda a 67 ms e alta a
   * 100–234 ms, qualunque sia il menu che diventera'. In frazioni del pannello un menu largo come il nostro (quattro voci,
   * 256 × 212 dp) dava una pillola larga il doppio che alta, non una goccia: fino a [AspectFrom] la larghezza e' quella delle
   * proporzioni di Apple, da li' a [AspectTo] torna alla frazione del pannello (il riquadro di Apple, [Width]).
   */
  private val AspectMs = floatArrayOf(66.7f, 100.1f, 133.5f, 166.8f, 200.2f, 233.6f, 266.9f, 300.3f)
  val Aspect = FluidMeasuredCurve(AspectMs, floatArrayOf(1.10f, 0.64f, 0.68f, 0.72f, 0.76f, 0.80f, 0.78f, 0.78f))
  const val AspectFrom = 133.5f
  const val AspectTo = 300.3f

  /** Le voci entrano nella goccia fra 67 ms (in Apple la goccia e' ancora solo lente) e 117 ms (voci minuscole dentro). */
  const val InkInFrom = 66.7f
  const val InkInTo = 116.8f
}

/** Da [a] a [b] per [f], esatto ai capi (i raggi a riposo sono quelli del pannello, non un loro arrotondamento). */
private fun birthLerp(a: Float, b: Float, f: Float): Float = when {
  f <= 0f -> a
  f >= 1f -> b
  else -> a + (b - a) * f
}

/** Smoothstep fra [from] e [to]: 0 prima, 1 dopo. */
private fun birthStep(t: Float, from: Float, to: Float): Float {
  val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
  return x * x * (3f - 2f * x)
}

/**
 * La sagoma del menu contestuale a [ms] dalla sua nascita, verso il pannello [panel] a raggio [panelRadius], dal seme
 * [seed] (la goccia appesa alla card, [fluidMenuDropSeed]). Null dalla fine in poi: e' il pannello fermo.
 *
 * Il profilo e' quello di Apple ([FluidContextBirthProfile]) nel sistema del pannello: il bordo lontano e quello vicino
 * lungo l'asse che va dalla card al pannello (verso il basso o verso l'alto), la larghezza di traverso dai bordi del
 * seme a quelli del pannello, cosi' che un seme sul bordo sinistro cresca verso destra (come la graffetta di Apple) e
 * uno al centro su tutti e due i lati. Il bordo vicino **si stacca** dalla card e ci torna: e' il «prima sale, poi rilascia» del
 * baricentro (memoria `viaggi-contro-apple`). Gli angoli sono la goccia (al piu' il 78% di mezzo lato corto,
 * [FluidContextBirthProfile.RoundnessInTravel]) fino a 200 ms e poi si stringono al raggio del pannello, i lontani prima dei
 * vicini: in viaggio il corpo e' un uovo con la coda verso la card.
 *
 * Nei primi [FluidContextBirthProfile.BlendMillis] la sagoma parte dal seme e non dalla capsula di Apple: a 0 ms e'
 * esattamente il seme, a 100 ms e' Apple. L'oltrepasso di Apple (3% dell'altezza sul bordo lontano, 3% della larghezza)
 * resta in frazioni del pannello, non della corsa: con un seme piu' piccolo della capsula sarebbe stato piu' grande.
 */
internal fun fluidContextBirthSlab(
  seed: Rect,
  panel: Rect,
  panelRadius: Float,
  ms: Float,
  /**
   * Con l'anticipo della goccia ([FluidContextBirthProfile.FarLead]): la sagoma da chiedere alla superficie liquida perche'
   * quella che si vede sia Apple. Senza: la sagoma di Apple stessa (per le prove e per chi non ha la goccia).
   */
  leadForDrop: Boolean = true,
): FluidForm.Slab? {
  if (ms >= FluidContextBirthProfile.EndMillis) return null
  if (panel.width <= 0f || panel.height <= 0f) return null
  val t = ms.coerceAtLeast(0f)
  val p = FluidContextBirthProfile
  val e = birthStep(t, 0f, p.BlendMillis)
  fun over(v: Float) = if (leadForDrop && v > 1f) 1f + (v - 1f) * p.OvershootShare else v
  val tNear = if (leadForDrop) t + p.NearLead.at(t) else t
  val tFar = if (leadForDrop) t + p.FarLead.at(t) else t
  val tWidth = if (leadForDrop) t + p.WidthLead.at(t) else t
  val down = panel.center.y >= seed.center.y
  val h = panel.height
  // Il seme nel sistema del pannello: dove stanno i suoi due bordi lungo l'asse della crescita.
  val nearRest = if (down) panel.top else panel.bottom
  fun u(y: Float) = if (down) (y - nearRest) / h else (nearRest - y) / h
  fun y(u: Float) = if (down) nearRest + u * h else nearRest - u * h
  val seedNear = u(if (down) seed.top else seed.bottom)
  val seedFar = u(if (down) seed.bottom else seed.top)
  val near = p.Near.at(tNear) + (seedNear - p.SourceNear) * (1f - e)
  val far = over(p.Far.at(tFar)) + (seedFar - p.SourceFar) * (1f - e)
  // Di traverso: ogni lato va dal bordo del seme al bordo del pannello con la stessa frazione, scelta perche' la larghezza
  // sia quella di Apple. Un lato che non ha strada da fare (il seme sul bordo, come la graffetta) resta fermo, e
  // l'oltrepasso va tutto dalla parte lontana.
  val seedW = seed.width.coerceAtMost(panel.width - 0.5f)
  val travelW = panel.width - seedW
  val a = y(near)
  val b = y(far)
  // La larghezza: quella delle proporzioni della goccia di Apple finche' e' piccola, poi la frazione del pannello.
  val fractionW = over(p.Width.at(tWidth)) * panel.width
  val dropW = abs(b - a) * p.Aspect.at(tWidth)
  val widthNow = if (dropW < fractionW) dropW + (fractionW - dropW) * birthStep(tWidth, p.AspectFrom, p.AspectTo) else fractionW
  val g = ((widthNow - seedW) / travelW).coerceAtLeast(0f) * e
  val left = seed.left + (panel.left - seed.left) * g
  val right = seed.right + (panel.right - seed.right) * g
  val frame = Rect(left, minOf(a, b), right, maxOf(a, b))
  val cap = frame.minDimension / 2f * birthLerp(1f, p.RoundnessInTravel, birthStep(t, 30f, 80f))
  val r = panelRadius.coerceAtMost(cap)
  val farR = birthLerp(cap, r, birthStep(t, p.FarCornersFrom, p.FarCornersTo))
  val nearR = birthLerp(cap, r, birthStep(t, p.NearCornersFrom, p.NearCornersTo))
  val radii = if (down) {
    FluidCornerRadii(topLeft = nearR, topRight = nearR, bottomRight = farR, bottomLeft = farR)
  } else {
    FluidCornerRadii(topLeft = farR, topRight = farR, bottomRight = nearR, bottomLeft = nearR)
  }
  return FluidForm.Slab(frame, radii)
}

/**
 * Il contorno della nascita a [ms] ([FluidDropWarp]) sulla sagoma [slab] di quell'istante ([fluidContextBirthSlab]): la
 * coda verso il seme [seed] (il lato della card), la testa verso il lato lontano del pannello [panel]. L'asse va dal seme,
 * sul bordo vicino, al centro del bordo lontano: un dito sul bordo della riga lo inclina, e l'uovo si piega verso il
 * dito come quello di Apple verso la graffetta. Null da [FluidContextBirthProfile.WarpEndMillis]: il contorno e' la lastra.
 */
internal fun fluidContextBirthWarp(seed: Rect, panel: Rect, slab: FluidForm.Slab, ms: Float): FluidDropWarp? {
  if (ms >= FluidContextBirthProfile.WarpEndMillis) return null
  val p = FluidContextBirthProfile
  val t = ms.coerceAtLeast(0f)
  val f = slab.frame
  val down = panel.center.y >= seed.center.y
  return FluidDropWarp(
    tailX = seed.center.x.coerceIn(f.left, f.right),
    tailY = if (down) f.top else f.bottom,
    headX = f.center.x,
    headY = if (down) f.bottom else f.top,
    tailTaper = p.TailTaper.at(t).coerceAtLeast(0f),
    tailReach = p.TailReach.at(t),
    headTaper = p.HeadTaper.at(t).coerceAtLeast(0f),
    waist = p.Waist.at(t).coerceAtLeast(0f),
    belly = p.Belly.at(t).coerceAtLeast(0f),
    lens = p.Lens.at(t).coerceIn(0f, 1f),
    magnify = p.Magnify.at(t).coerceAtLeast(0f),
    clarity = p.Clarity.at(t).coerceIn(0f, 1f),
  )
}

/** Quanto delle voci c'e' nella goccia a [ms] dalla nascita: niente fino a 67 ms, tutte a 117 ms. */
internal fun fluidContextBirthInk(ms: Float): Float =
  birthStep(ms, FluidContextBirthProfile.InkInFrom, FluidContextBirthProfile.InkInTo)

/**
 * Il progresso del menu contestuale a [ms] dalla nascita: la corsa del bordo lontano di Apple dalla capsula (0) al menu a
 * riposo (1), col suo oltrepasso (1,036 a 467 ms). E' il numero che leggono gli altri pezzi del menu (la lente, il
 * ritaglio, il punto di luce, il verso della chiusura); non dipende dalla geometria, cosi' un pannello che si sposta a
 * meta' nascita (un'anteprima misurata dopo) non cambia l'orologio. Vale 1 **esatto** dalla fine in poi: il vetro si dice
 * fermo solo li'.
 */
internal fun fluidContextBirthProgress(ms: Float): Float {
  if (ms >= FluidContextBirthProfile.EndMillis) return 1f
  if (ms <= 0f) return 0f
  val p = FluidContextBirthProfile
  // Nei primi 33 ms la capsula di Apple si stringe e il suo bordo lontano scende di un filo: qui e' zero, non negativo.
  return ((p.Far.at(ms) - p.SourceFar) / (1f - p.SourceFar) * birthStep(ms, 0f, p.BlendMillis)).coerceAtLeast(0f)
}

/**
 * Quanto della goccia del contestuale si vede a [ms] dalla nascita: tutta a 50 ms. Nel fotogramma di Apple a 33 ms la
 * capsula e' ancora quella, a 67 ms e' gia' una goccia piena: la nostra esce dalla card in quei due fotogrammi.
 */
internal fun fluidContextBirthPresence(ms: Float): Float = birthStep(ms, 0f, FluidContextBirthPresenceMillis)

internal const val FluidContextBirthPresenceMillis = 50f

/**
 * La messa a fuoco delle voci nella nascita: nitide a [FluidContextFocusMillis] (Apple: leggibili a 234-267 ms, nitide a
 * ~334). L'andamento e' 1 − √(1 − f): la sfocatura della lente va col quadrato di quanto manca
 * (`fluidMenuLensBlurDp`), e cosi' cala **dritta** nel tempo, come nei fotogrammi di Apple (a 234 ms le voci sono ancora
 * morbide; con la rampa lineare dei menu dal tasto sarebbero gia' nitide).
 */
internal const val FluidContextFocusMillis = 334

internal val FluidContextFocusEasing = Easing { f ->
  1f - kotlin.math.sqrt((1f - f).coerceIn(0f, 1f))
}

/**
 * L'istante della nascita a cui corrisponde [progress], per una nascita ripresa a meta' (un menu riaperto mentre si
 * chiudeva): il primo istante della salita in cui il progresso vale [progress]. Bisezione sulla parte che sale (fino a
 * 334 ms, dove il bordo lontano arriva al pannello).
 */
internal fun fluidContextBirthMillisAt(progress: Float): Float {
  if (progress <= 0f) return 0f
  var lo = 0f
  var hi = FluidContextBirthProfile.RiseEndMillis
  if (fluidContextBirthProgress(hi) <= progress) return hi
  repeat(24) {
    val mid = (lo + hi) / 2f
    if (fluidContextBirthProgress(mid) < progress) lo = mid else hi = mid
  }
  return hi
}

/**
 * La nascita di UN menu contestuale col vetro liquido: l'orologio in millisecondi che guida la sagoma
 * ([fluidContextBirthSlab]), la molla della card sollevata e la sagoma fermata al congedo.
 *
 * Perche' un orologio a parte e non il progresso del viaggio dei menu ([FluidMenuJourney]): quel progresso e' una molla
 * tarata sul menu dal tasto del telefono (R4 B1 a, quasi pieno a ~130 ms), e il contestuale ha come modello la nascita
 * sulla foto (B1 b), che ha tempi suoi su ogni bordo. Il viaggio resta la fonte del progresso per tutti gli altri pezzi
 * (lente, ritaglio, punto di luce, chiusura): qui lo si fa correre sul profilo di Apple, nello stesso fotogramma
 * dell'orologio, e la chiusura resta quella del viaggio. La card ha la sua molla, quella di prima (`dropOpen`): il suo
 * sollevamento non rallenta con il menu.
 *
 * Gli `Animatable` si **ricreano** a un'apertura nuova ([prepare], in composizione) invece di uno `snapTo`: lo `snapTo` e'
 * sospeso, e il primo fotogramma arriva prima di qualunque coroutine (lo stesso motivo di `anchorRelax`).
 */
@Stable
internal class FluidContextBirth {
  var clock = Animatable(FluidContextBirthProfile.EndMillis, 0.5f)
    private set
  var card = Animatable(0f, 0.001f)
    private set

  /** La sagoma al congedo, se la nascita non era finita: la chiusura torna alla goccia da li'. Null: dal pannello. */
  var frozen: FluidForm.Slab? = null
    private set
  var frozenProgress: Float = 1f
    private set

  /** Il raggio del pannello in pixel: lo scrive la composizione del menu, lo leggono la sagoma e il congedo. */
  var radius: Float = 0f

  /** Un'apertura nuova da fermo: orologio e card ripartono da zero, gia' al primo fotogramma. */
  fun prepare(fresh: Boolean) {
    if (!fresh) return
    clock = Animatable(0f, 0.5f)
    card = Animatable(0f, 0.001f)
    frozen = null
    frozenProgress = 1f
  }

  /** I millisecondi della nascita, in disegno. */
  fun millis(): Float = clock.value

  /**
   * Quanto della goccia si vede: in apertura col tempo della nascita ([fluidContextBirthPresence]), in chiusura col
   * progresso del viaggio come prima (la goccia rientra nella card).
   */
  fun presence(journey: FluidMenuJourney): Float =
    if (!journey.closing) fluidContextBirthPresence(clock.value) else fluidMenuDropPresence(journey.progress.value)

  /**
   * La sagoma di questo istante, per `driveExternally(travelSlab = ...)`: la nascita in apertura, il ritorno del menu dal
   * tasto (R4 B1 a, [fluidMenuReturnSlab]) in chiusura, dal pannello o dalla sagoma fermata al congedo. Null a riposo.
   */
  fun travel(journey: FluidMenuJourney): FluidForm.Slab? {
    val seed = journey.seed ?: return null
    val panel = journey.to
    if (!journey.closing) return fluidContextBirthSlab(seed, panel, radius, clock.value)
    val source = FluidForm.Slab(seed, FluidCornerRadii.all(seed.minDimension / 2f))
    val home = frozen ?: FluidForm.Slab(panel, FluidCornerRadii.all(radius))
    val q = (journey.progress.value / frozenProgress.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    return fluidMenuReturnSlab(source, home, q)
  }

  /**
   * Il contorno di questo istante, per `driveExternally(travelWarp = ...)`: la goccia, il fagiolo e l'uovo della nascita
   * ([fluidContextBirthWarp]) in apertura; nessuno in chiusura (il ritorno e' quello dei menu) ne' a riposo.
   */
  fun warp(journey: FluidMenuJourney): FluidDropWarp? {
    if (journey.closing) return null
    val seed = journey.seed ?: return null
    val slab = fluidContextBirthSlab(seed, journey.to, radius, clock.value) ?: return null
    return fluidContextBirthWarp(seed, journey.to, slab, clock.value)
  }

  /** Quanto delle voci si vede: in apertura col tempo della nascita ([fluidContextBirthInk]), in chiusura tutte. */
  fun inkPresence(journey: FluidMenuJourney): Float = if (journey.closing) 1f else fluidContextBirthInk(clock.value)

  /**
   * L'apertura: l'orologio fino alla fine, il progresso del viaggio sul profilo di Apple, la messa a fuoco in
   * [FluidContextFocusMillis], la card con la sua molla. Da dove si trova: un menu riaperto a meta' chiusura riprende la
   * nascita dall'istante del suo progresso.
   */
  suspend fun open(journey: FluidMenuJourney): Unit = coroutineScope {
    val from = journey.progress.value
    val start = if (from <= 0.001f) clock.value.coerceAtMost(FluidContextBirthProfile.EndMillis) else fluidContextBirthMillisAt(from)
    clock.snapTo(start)
    frozen = null
    frozenProgress = 1f
    val remaining = (FluidContextBirthProfile.EndMillis - start).coerceAtLeast(1f)
    val millis = ceil(remaining).toInt()
    launch { clock.animateTo(FluidContextBirthProfile.EndMillis, tween(millis, easing = LinearEasing)) }
    launch {
      val focus = journey.focus
      val left = (FluidContextFocusMillis * (1f - focus.value)).toInt().coerceAtLeast(1)
      focus.animateTo(1f, tween(left, easing = FluidContextFocusEasing))
    }
    launch { journey.contentVisibility.snapTo(1f) }
    launch { card.animateTo(1f, FluidMotion.dropOpen(), initialVelocity = card.velocity) }
    val span = 1f - from
    if (span > 0.0001f) {
      journey.progress.animateTo(
        1f,
        tween(millis, easing = Easing { f -> (fluidContextBirthProgress(start + f * remaining) - from) / span }),
      )
    } else {
      journey.progress.snapTo(1f)
    }
  }

  /**
   * Il congedo: la sagoma si ferma dov'e' (se la nascita non era finita) e il viaggio la riporta alla goccia col ritorno
   * dei menu (`FluidMenuJourney.run`). Le voci si sfocano subito come nei menu dal tasto, ma l'inchiostro e' quello di
   * B1 (a) ([fluidContextExitInkSpec]): resta dentro il vetro che crolla fino a ~67 ms e se ne va entro ~167. Per questo
   * il viaggio corre senza la sua lente e fuoco e inchiostro si animano qui. La card torna con le stesse molle con cui
   * ci tornava col progresso del viaggio.
   */
  suspend fun close(journey: FluidMenuJourney, reducedMotion: Boolean): Unit = coroutineScope {
    val seed = journey.seed
    val t = clock.value
    if (seed != null && t < FluidContextBirthProfile.EndMillis) {
      frozen = fluidContextBirthSlab(seed, journey.to, radius, t)
      frozenProgress = journey.progress.value.coerceAtLeast(0.001f)
    } else {
      frozen = null
      frozenProgress = 1f
    }
    clock.stop()
    launch {
      if (reducedMotion) {
        card.snapTo(0f)
      } else {
        val spec: AnimationSpec<Float> = if (journey.controlledReturn) {
          fluidMenuReturnSpec()
        } else {
          spring(FluidMotion.DropJourneyDamping, FluidMotion.MenuLiquidCloseStiffness)
        }
        card.animateTo(0f, spec, initialVelocity = card.velocity)
      }
    }
    if (!reducedMotion) {
      launch { journey.focus.animateTo(0f, fluidMenuReturnFocusSpec(journey.focus.value)) }
      launch {
        val visibility = journey.contentVisibility
        visibility.animateTo(0f, fluidContextExitInkSpec(visibility.value))
      }
    }
    // Con le animazioni ridotte `run` mette a zero fuoco e inchiostro da se'.
    journey.run(reducedMotion, withLens = false)
  }
}

/**
 * Quanto resta pieno l'inchiostro delle voci al congedo del contestuale, e quando se n'e' andato (B1 a: a 67 ms le voci
 * sono sfocate e piene, a 133 ms ne resta un'ombra, a 200 ms niente). Scelta di Alessio del 2026-11-19 per il
 * contestuale; i menu dal tasto e dal gruppo tengono i ~90 ms di F5A-corr (`fluidMenuReturnInkSpec`).
 */
internal const val FluidContextExitInkHoldMillis = 67f
internal const val FluidContextExitInkMillis = 167f

/**
 * L'inchiostro del congedo del contestuale da [from] a 0: fermo fino a [FluidContextExitInkHoldMillis], poi via con una
 * curva morbida entro [FluidContextExitInkMillis]. Un congedo con l'inchiostro gia' a meta' dura in proporzione.
 */
internal fun fluidContextExitInkSpec(from: Float): AnimationSpec<Float> {
  val total = (FluidContextExitInkMillis * from.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(1)
  val hold = FluidContextExitInkHoldMillis / FluidContextExitInkMillis
  return tween(total, easing = Easing { f ->
    val x = ((f - hold) / (1f - hold)).coerceIn(0f, 1f)
    x * x * (3f - 2f * x)
  })
}
