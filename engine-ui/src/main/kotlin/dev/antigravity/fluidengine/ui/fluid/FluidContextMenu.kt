package dev.antigravity.fluidengine.ui.fluid

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
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.util.WeakHashMap

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
