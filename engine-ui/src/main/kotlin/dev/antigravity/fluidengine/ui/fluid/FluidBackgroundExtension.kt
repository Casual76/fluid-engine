package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported

internal val LocalBackgroundExtensionStart = compositionLocalOf<() -> Dp> { { 0.dp } }

/**
 * Continua un'immagine sotto la navigazione laterale mantenendo nitida e intera la sua parte visibile.
 * Applicarlo al fondale, prima del suo disegno. Lo spazio di [FluidAdaptiveNavigation] segue il vetro;
 * [start] e [end] permettono di servire anche un pannello o un ispettore disposti dall'app.
 * Sotto la colonna di [FluidAdaptiveNavigation] c'è di serie lo sfondo del tema: l'estensione si disegna sopra e lo copre.
 */
fun Modifier.fluidBackgroundExtension(start: Dp? = null, end: Dp = 0.dp): Modifier = composed {
  val automatic = LocalBackgroundExtensionStart.current
  val original = rememberGraphicsLayer()
  val mirror = rememberGraphicsLayer()
  val blur = isRenderEffectSupported()
  drawWithCache {
    val radius = FluidSidebarDefaults.Margin.toPx()
    mirror.renderEffect = if (blur) BlurEffect(radius, radius, TileMode.Clamp) else null
    val seam = minOf(radius, size.width)
    val leadingSeam = BackgroundExtensionSeam(0f, -seam)
    val trailingSeam = BackgroundExtensionSeam(size.width, size.width + seam)
    val layerPaint = Paint()
    val addPaint = Paint().apply { blendMode = BlendMode.Plus }
    onDrawWithContent {
      val leading = (start ?: automatic()).toPx().coerceAtLeast(0f)
      val trailing = end.toPx().coerceAtLeast(0f)
      if (size.width <= 0f || (leading == 0f && trailing == 0f)) {
        drawContent()
      } else {
        // Un GraphicsLayer può rigiocare operazioni oltre la sua misura. Il grande arco di Maree
        // esce dalla pagina e, nell'ultimo drawLayer, si sovrapporrebbe all'estensione già disegnata.
        // Ritaglia la registrazione comune una volta, prima di specchiarla e sfocarla.
        original.record { clipRect { this@onDrawWithContent.drawContent() } }
        if (blur) mirror.record { drawLayer(original) }
        val extension = if (blur) mirror else original
        val left = if (layoutDirection == LayoutDirection.Ltr) leading else trailing
        val right = if (layoutDirection == LayoutDirection.Ltr) trailing else leading
        fun extend(distance: Float, before: Boolean, join: BackgroundExtensionSeam) {
          if (distance <= 0f) return
          val edge = if (before) 0f else size.width
          val joinWidth = if (blur) minOf(distance, seam) else 0f
          val left = if (before) -distance else edge
          val right = if (before) edge else edge + distance
          clipRect(left = if (before) left else left + joinWidth, right = if (before) right - joinWidth else right) {
            var covered = 0f
            var reflected = true
            while (covered < distance) {
              val x = if (before) -covered - size.width else edge + covered
              translate(left = x) {
                scale(if (reflected) -1f else 1f, 1f, pivot = Offset(size.width / 2, 0f)) { drawLayer(extension) }
              }
              covered += size.width
              reflected = !reflected
            }
          }
          if (joinWidth > 0f) {
            val bounds = Rect(if (before) -joinWidth else edge, 0f, if (before) edge else edge + joinWidth, size.height)
            join.draw(this, bounds, original, mirror, layerPaint, addPaint)
          }
        }
        extend(left, true, leadingSeam)
        extend(right, false, trailingSeam)
        drawLayer(original)
      }
    }
  }
}

/** Lo specchio entra nitido al bordo e riceve gradualmente la sfocatura solo al di fuori della pagina. */
private class BackgroundExtensionSeam(private val edge: Float, end: Float) {
  private val sharpMask = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), edge, end)
  private val blurredMask = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), edge, end)

  fun draw(scope: DrawScope, bounds: Rect, original: GraphicsLayer, blurred: GraphicsLayer, paint: Paint, add: Paint) =
    with(scope) {
      fun drawMasked(layer: GraphicsLayer, mask: Brush) {
        scale(-1f, 1f, pivot = Offset(edge, 0f)) { drawLayer(layer) }
        drawRect(mask, bounds.topLeft, Size(bounds.width, bounds.height), blendMode = BlendMode.DstIn)
      }
      // La somma delle due copie pesate conserva anche l'alfa di un fondale traslucido.
      drawContext.canvas.withSaveLayer(bounds, paint) {
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
          drawMasked(blurred, blurredMask)
          drawContext.canvas.withSaveLayer(bounds, add) { drawMasked(original, sharpMask) }
        }
      }
    }
}
