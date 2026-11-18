package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal val LocalBackgroundExtensionStart = compositionLocalOf<() -> Dp> { { 0.dp } }

/**
 * Continua un'immagine sotto la navigazione laterale mantenendo nitida e intera la sua parte visibile.
 * Applicarlo al fondale, prima del suo disegno. Lo spazio di [FluidAdaptiveNavigation] segue il vetro;
 * [start] e [end] permettono di servire anche un pannello o un ispettore disposti dall'app.
 */
fun Modifier.fluidBackgroundExtension(start: Dp? = null, end: Dp = 0.dp): Modifier = composed {
  val automatic = LocalBackgroundExtensionStart.current
  val original = rememberGraphicsLayer()
  val mirror = rememberGraphicsLayer()
  drawWithCache {
    val radius = FluidSidebarDefaults.Margin.toPx()
    mirror.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
    onDrawWithContent {
      val leading = (start ?: automatic()).toPx().coerceAtLeast(0f)
      val trailing = end.toPx().coerceAtLeast(0f)
      if (size.width <= 0f || (leading == 0f && trailing == 0f)) {
        drawContent()
      } else {
        original.record { this@onDrawWithContent.drawContent() }
        mirror.record { drawLayer(original) }
        val left = if (layoutDirection == LayoutDirection.Ltr) leading else trailing
        val right = if (layoutDirection == LayoutDirection.Ltr) trailing else leading
        fun extend(distance: Float, before: Boolean) {
          if (distance <= 0f) return
          val edge = if (before) 0f else size.width
          clipRect(left = if (before) -distance else edge, right = if (before) edge else edge + distance) {
            var covered = 0f
            var reflected = true
            while (covered < distance) {
              val x = if (before) -covered - size.width else edge + covered
              translate(left = x) {
                scale(if (reflected) -1f else 1f, 1f, pivot = Offset(size.width / 2, 0f)) { drawLayer(mirror) }
              }
              covered += size.width
              reflected = !reflected
            }
          }
        }
        extend(left, true)
        extend(right, false)
        drawLayer(original)
      }
    }
  }
}
