package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A saturated gradient and the one content colour readable on it.
 *
 * The pair is a unit for the same reason every accent role in the theme is a pair: a background
 * detached from its content colour is how white text ends up on saturated orange. [from] exists so
 * callers never pick the content by eye — it is chosen against the *worse* end of the gradient,
 * because a card is only as readable as its least readable corner.
 */
@Immutable
data class FluidVividColors(
  val start: Color,
  val end: Color,
  val content: Color,
) {
  companion object {
    private val DarkContent = Color(0xFF121214)
    private val LightContent = Color(0xFFFDFDFF)

    fun from(start: Color, end: Color = start): FluidVividColors {
      fun ratio(foreground: Color, background: Color): Float {
        val high = maxOf(foreground.luminance(), background.luminance())
        val low = minOf(foreground.luminance(), background.luminance())
        return (high + 0.05f) / (low + 0.05f)
      }

      val darkWorst = minOf(ratio(DarkContent, start), ratio(DarkContent, end))
      val lightWorst = minOf(ratio(LightContent, start), ratio(LightContent, end))
      val content = if (darkWorst >= lightWorst) DarkContent else LightContent
      return FluidVividColors(start = start, end = end, content = content)
    }
  }
}

enum class FluidVividEffect {
  None,

  /**
   * A faint diagonal band of light that sweeps the surface every few seconds. For the one card in a
   * page that has earned looking precious — on every card it is wallpaper.
   */
  Sheen,
}

/**
 * A surface that is entirely its own colour: saturated gradient, on-colour content.
 *
 * This is the vocabulary of *separated* elements, and the counterpart of the grouped-list rule:
 * inside a [dev.antigravity.fluidengine.ui.theme.FluidListGroup] the tone stays on the icon tile,
 * because tinted rows turn a tidy group into a patchwork — but an element that stands alone in the
 * page can carry its colour on the whole surface, and behind glass chrome a field of real colour is
 * what makes the refraction visible at all. The sample gallery's loud cards are this component's
 * ancestor; this is the production version, with the content colour resolved for contrast instead
 * of assumed white.
 *
 * The sheen's tween lives here and not in a screen deliberately: it is periodic, so a spring has
 * nothing to grab onto, and keeping it in the engine keeps it consistent and policy-gated. It runs
 * only while the card is actually in the viewport ([onVisibilityChanged]) and only while the system
 * animation scale allows decorative motion; with animations off the band is painted once, standing
 * still, so the card keeps its distinction without moving.
 */
@Composable
fun FluidVividCard(
  colors: FluidVividColors,
  modifier: Modifier = Modifier,
  effect: FluidVividEffect = FluidVividEffect.None,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  contextActions: (() -> List<FluidContextAction>)? = null,
  contentPadding: PaddingValues = PaddingValues(18.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Group)
  // Same split as FluidListRow: the menu must be raised from the card's own long-press, because a
  // separate detector next to a clickable never fires.
  // Il menu prende il colore della card: nasce da lei, e un pannello grigio sopra una superficie
  // satura si legge come una cosa arrivata da un'altra parte.
  val contextMenu = contextActions?.let { rememberFluidContextMenu(it, tint = colors.start) }

  var inViewport by remember { mutableStateOf(false) }
  val wantsSheen = effect == FluidVividEffect.Sheen
  val allowMotion = LocalFluidMotionPolicy.current.allowDecorativeMotion

  Column(
    modifier = modifier
      .then(
        if (wantsSheen) {
          Modifier.onVisibilityChanged(minFractionVisible = 0.3f) { visible ->
            inViewport = visible
          }
        } else {
          Modifier
        },
      )
      .clip(shape)
      .then(
        if (contextMenu != null) Modifier.fluidContextMenuAnchor(contextMenu) else Modifier,
      )
      .fluidPressable(
        onClick = onClick,
        onLongClick = if (contextMenu != null) {
          { if (!contextMenu.open()) onLongClick?.invoke() }
        } else {
          onLongClick
        },
        pressedScale = 0.97f,
        role = if (onClick != null) Role.Button else null,
      )
      .background(
        Brush.linearGradient(
          colors = listOf(colors.start, colors.end),
          start = Offset.Zero,
          end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        ),
      )
      .then(
        when {
          wantsSheen && allowMotion && inViewport -> Modifier.animatedSheen(colors.content)
          wantsSheen -> Modifier.staticSheen(colors.content)
          else -> Modifier
        },
      )
      .padding(contentPadding),
  ) {
    CompositionLocalProvider(LocalContentColor provides colors.content) {
      content()
    }
  }
}

/** How far past both corners the band starts and ends, as a fraction of the diagonal. */
private const val SheenOverflow = 0.35f
private const val SheenPeakAlpha = 0.13f
private const val SheenTravelMillis = 2600
private const val SheenRestMillis = 1900

/** Where the standing band sits when motion is off: high on the surface, clear of the text. */
private const val SheenStaticPosition = 0.30f

private fun Modifier.animatedSheen(content: Color): Modifier = composed {
  val transition = rememberInfiniteTransition(label = "fluidVividSheen")
  val progress by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = SheenTravelMillis,
        delayMillis = SheenRestMillis,
        easing = FluidMotion.EaseInOut,
      ),
      repeatMode = RepeatMode.Restart,
    ),
    label = "fluidVividSheenProgress",
  )
  drawSheen(content) { progress }
}

private fun Modifier.staticSheen(content: Color): Modifier =
  drawSheen(content) { SheenStaticPosition }

/**
 * The band is drawn over the content, not under it: a light sweep that skipped the text would read
 * as a reflection *behind* the ink, which is not where a surface's gloss lives. At thirteen percent
 * peak alpha the text stays comfortably readable while the light passes.
 */
private fun Modifier.drawSheen(content: Color, progress: () -> Float): Modifier =
  drawWithContent {
    drawContent()
    val diagonal = size.width + size.height
    if (diagonal <= 0f) return@drawWithContent
    val band = diagonal * 0.18f
    val travel = diagonal * (1f + 2f * SheenOverflow)
    val centre = -diagonal * SheenOverflow + travel * progress()
    // The gradient axis runs along the (1, 1) diagonal; positions project onto it.
    val axis = 0.70710677f // 1 / sqrt(2)
    val startT = centre - band
    val endT = centre + band
    drawRect(
      brush = Brush.linearGradient(
        colors = listOf(
          Color.Transparent,
          content.copy(alpha = SheenPeakAlpha),
          Color.Transparent,
        ),
        start = Offset(startT * axis, startT * axis),
        end = Offset(endT * axis, endT * axis),
      ),
    )
  }
