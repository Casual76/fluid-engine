package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A modal sheet.
 *
 * Material's sheet is kept underneath for the drag gesture, the predictive-back handling and the
 * accessibility wiring — all of which are genuinely hard to redo — but nothing it draws survives:
 * the corners are continuous and much larger, the grabber is a short capsule rather than Material's
 * wide bar, and the scrim is darker so the sheet reads as being in front of the app rather than
 * merely on top of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  title: String? = null,
  showGrabber: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  val sheetShape = ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    sheetState = sheetState,
    shape = sheetShape,
    // ModalBottomSheet owns a separate platform window, so it cannot sample a FluidScreen's
    // GraphicsLayer. Keep the window translucent and add the same optical rim draw-only below;
    // the live-backdrop path remains reserved for in-root overlays.
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    scrimColor = Color.Black.copy(alpha = 0.42f),
    tonalElevation = 0.dp,
    dragHandle = if (showGrabber) {
      { FluidGrabber() }
    } else {
      null
    },
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fluidStaticGlassSurface(shape = sheetShape),
    ) {
      if (title != null) {
        Text(
          text = title,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.Center,
        )
      }
      content()
    }
  }
}

/**
 * The grabber.
 *
 * 36 x 5, centred, at 18% of the text colour. Material's default is nearly twice as wide and sits in
 * a much taller strip, which is the first thing that gives a sheet away.
 */
@Composable
fun FluidGrabber(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(width = 36.dp, height = 5.dp)
        .clip(FluidCapsuleShape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    )
  }
}

/** One button in a [FluidAlert]. */
data class FluidAlertAction(
  val label: String,
  val onClick: () -> Unit,
  val emphasis: Emphasis = Emphasis.Normal,
  val enabled: Boolean = true,
) {
  enum class Emphasis { Normal, Preferred, Destructive }
}

/**
 * An alert.
 *
 * Centred, narrow, and built out of hairlines rather than padding — the buttons are cells in a grid
 * that reaches the edges of the card, not floating pills in a corner. Material puts its buttons at
 * the bottom right; that single difference is enough to place a dialog on the wrong platform.
 *
 * Two actions sit side by side, three or more stack, exactly as UIKit does it, because at three the
 * labels stop fitting on one line.
 */
@Composable
fun FluidAlert(
  onDismissRequest: () -> Unit,
  title: String,
  modifier: Modifier = Modifier,
  message: String? = null,
  actions: List<FluidAlertAction>,
  content: (@Composable ColumnScope.() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { visible = true }
  // The card comes in slightly oversized and settles. iOS scales *down* into place from 1.12; the
  // effect reads as the alert arriving from in front of the screen rather than growing out of it.
  val scale by animateFloatAsState(
    targetValue = if (visible) 1f else 1.12f,
    animationSpec = FluidMotion.snappy(),
    label = "alert scale",
  )
  val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = FluidMotion.fadeIn(140),
    label = "alert alpha",
  )

  // The buttons are the only way out of an alert, so they are the one part that may never be
  // pushed off the screen. An unbounded content column did exactly that: a long enough message —
  // an app changelog, say — grew the card past the display and left the actions unreachable, which
  // turns an alert into a trap. The card is now bounded to the window, the message scrolls inside
  // whatever is left over, and the actions are measured first so they always have their place.
  val windowHeight = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.height.toDp()
  }
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    val alertShape = ContinuousCornerShape(FluidRadius.Card)
    Column(
      modifier = modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          this.alpha = alpha
        }
        .widthIn(max = 280.dp)
        .heightIn(max = windowHeight * AlertMaxHeightFraction)
        .clip(alertShape)
        // Dialog is another platform window: use the optical fallback rather than pretending the
        // screen's live sample can cross that boundary.
        .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
        .fluidStaticGlassSurface(shape = alertShape),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          // `fill = false` is what keeps a short alert the size of its content: it takes the space
          // the actions left over only if it needs it, rather than always stretching to the cap.
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .padding(top = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = scheme.onSurface,
        )
        message?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
          )
        }
        content?.invoke(this)
      }

      val hairline = scheme.onSurface.copy(alpha = 0.12f)
      val onAction: (FluidAlertAction) -> Unit = { action -> action.onClick() }

      if (actions.size == 2) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(hairline))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
          AlertButton(actions[0], Modifier.weight(1f)) { onAction(actions[0]) }
          Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(hairline))
          AlertButton(actions[1], Modifier.weight(1f)) { onAction(actions[1]) }
        }
      } else {
        actions.forEach { action ->
          Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(hairline))
          AlertButton(action, Modifier.fillMaxWidth()) { onAction(action) }
        }
      }
    }
  }
}

/** How much of the window an alert may occupy before its message starts scrolling instead. */
private const val AlertMaxHeightFraction = 0.86f

@Composable
private fun AlertButton(
  action: FluidAlertAction,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val color = when (action.emphasis) {
    FluidAlertAction.Emphasis.Destructive -> scheme.error
    else -> scheme.primary
  }
  Box(
    modifier = modifier
      .fluidRowPressable(onClick = onClick, enabled = action.enabled)
      .alpha(if (action.enabled) 1f else 0.35f)
      .heightIn(min = 44.dp)
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = action.label,
      style = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = if (action.emphasis == FluidAlertAction.Emphasis.Preferred) {
          FontWeight.SemiBold
        } else {
          FontWeight.Normal
        },
      ),
      color = color,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}
