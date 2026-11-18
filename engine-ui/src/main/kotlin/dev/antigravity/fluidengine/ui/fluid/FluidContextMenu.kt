package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight

// Il menu contestuale dal lato di chi lo apre: il controller, l'ancora che registra la riga e il
// modificatore che unisce le due cose. Il pannello lo disegna `FluidMenuLayer`. Spezzato da
// `FluidGlassModal.kt` in F5A.0.

// ---------------------------------------------------------------------------------------------
// Context menu
// ---------------------------------------------------------------------------------------------

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a lightly frosted scrim so the menu has somewhere to
 * be, the row itself is redrawn *in its own place* and lifted, and the actions grow out of the
 * corner nearest it.
 *
 * The lifted row is not the same instance — that one is still in the list, under the scrim. It is a
 * **recording**: the element paints itself into a `GraphicsLayer` and the host replays it. Recording
 * costs nothing while nobody is touching the row, because it only starts on the finger going down
 * and stops when it comes back up.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 */
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
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      // The recording belongs to this node and dies with it. Clearing the host's reference first is
      // what stops the overlay from replaying a layer the runtime has already released.
      entry.preview = null
      entry.visible = false
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
      if (reducedMotion) return@graphicsLayer
      // Swells under the finger, the way the navigation pill does, and settles back when it is let
      // go. Rows normally must not scale — shrinking one row of a stack breaks the group's
      // silhouette — but this one is on its way *out* of the stack, and growing says so.
      val swell = 1f + FluidContextAnchorSwell * highlight.pressProgress
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

/** How much a row swells while it is being held. A few percent: it is a row, not a card yet. */
private const val FluidContextAnchorSwell = 0.02f

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a lightly frosted scrim so the menu has somewhere to
 * be, the row itself is redrawn *in its own place* and lifted, and the actions grow out of the
 * corner nearest it.
 *
 * The lifted element is not the same instance — that one is still in the list, under the scrim. It
 * is a **recording**, replayed one level up.
 *
 * Use this only on something that does **not** already handle clicks. On anything that does — a
 * list row, a card with an `onClick` — the click handler consumes the gesture and this never fires;
 * use [rememberFluidContextMenu] with [fluidContextMenuAnchor] and open the menu from that
 * element's own long-press instead. [dev.antigravity.fluidengine.ui.theme.FluidListRow] does exactly
 * that behind its `contextActions` parameter.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 */
@Composable
fun Modifier.fluidContextMenu(
  actions: () -> List<FluidContextAction>,
  enabled: Boolean = true,
): Modifier {
  val controller = rememberFluidContextMenu(actions)
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
