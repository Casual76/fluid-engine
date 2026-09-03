package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A button that unfolds into its own menu, and folds back once something is chosen.
 *
 * Tapped it performs [onClick], the way any button does. **Held**, it does not open a pop-up beside
 * itself — it *becomes* one: the menu starts at exactly this button's rectangle, made of the same
 * glass, and grows out of it on the two springs the navigation pill uses. The button steps aside
 * while that happens, so what the eye follows is one object changing size rather than a second
 * object arriving on top of the first. Choosing an action reverses it back into the button.
 *
 * That difference is the whole component. A menu that appears *next to* its button is a menu; a
 * button that turns into its menu is the thing Apple's controls do, and the reason it reads as
 * physical is that at no point are there two of it.
 *
 * The unfolded pane is drawn by [FluidGlassModalHost] at the root of the app, so it clears the tab
 * bar and anything else the page is sitting under. With no host in scope the long press does
 * nothing and the button is an ordinary button — which is the right degradation, not a crash.
 */
@Composable
fun FluidGlassMenuButton(
  actions: () -> List<FluidContextAction>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  contentDescription: String? = null,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  shape: Shape = FluidCapsuleShape,
) {
  val host = LocalFluidGlassModalHostState.current
  val entry = remember { FluidGlassModalEntry() }
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  var open by remember { mutableStateOf(false) }

  SideEffect {
    entry.presentation = FluidGlassModalPresentation.Expand
    entry.paneTitle = text ?: contentDescription
    entry.onDismissRequest = { open = false }
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      entry.visible = false
      host?.unregister(entry)
    }
  }

  LaunchedEffect(open) {
    entry.actions = if (open) actions() else entry.actions
    if (open && host != null) entry.openedAt = host.nextSequence()
    entry.visible = open && host != null
  }

  // The button gives up its pixels while the menu is out, and keeps its *space*. Removing it from
  // the layout instead would reflow the row it sits in at the exact moment the menu is growing out
  // of where it used to be, which is the one frame that has to be still.
  val handover = remember { Animatable(1f) }
  LaunchedEffect(open, reducedMotion) {
    val target = if (open) 0f else 1f
    if (reducedMotion) {
      handover.snapTo(target)
    } else {
      handover.animateTo(target, spring(1f, if (open) 900f else 340f, 0.001f))
    }
  }

  Box(
    modifier = modifier
      .onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        entry.previewSize = rect
        entry.origin = { rect }
      }
      .graphicsLayer { alpha = handover.value }
      .height(FluidGlassMenuButtonHeight)
      .defaultMinSize(minWidth = FluidGlassMenuButtonHeight)
      // The full control treatment, not a plain pane: it leans toward the finger, swells while it
      // is held and lights up where it was touched. A button that is about to unfold into a menu is
      // exactly the button that has to answer the half-second before it does.
      .glassControlSurface(
        backdrop = backdrop,
        shape = shape,
        interactive = enabled,
      )
      .semantics { this.role = Role.Button }
      .pointerInput(enabled, host) {
        detectTapGestures(
          onTap = { if (enabled) onClick() },
          onLongPress = {
            if (!enabled || host == null) return@detectTapGestures
            if (actions().isEmpty()) return@detectTapGestures
            haptics.play(FluidHapticEvent.Open)
            open = true
          },
        )
      }
      .padding(horizontal = if (text != null) 18.dp else 0.dp),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        icon?.let {
          Icon(
            imageVector = it,
            contentDescription = if (text == null) contentDescription else null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
        text?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** 48 dp: the smallest a control may be and still be reliably hit. */
private val FluidGlassMenuButtonHeight = 48.dp
