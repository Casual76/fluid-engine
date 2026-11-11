package dev.antigravity.fluidengine.sample.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import android.graphics.Matrix
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.measure.MeasureClock
import dev.antigravity.fluidengine.sample.measure.generateMeasurePhoto
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import kotlin.math.ceil

/** La pagina chiara e la pagina scura dell'engine (piano 4.6): i colori di base di `FluidTheme`. */
internal val MaterialPageLight = Color(0xFFF2F2F7)
internal val MaterialPageDark = Color(0xFF0D0D0F)

/**
 * Il palco: il fondo scelto, registrato come ciò che il vetro della scena rifrange, e sopra la scena. Il fondo è
 * anche quello che `LocalGlassBackdrop` e `LocalFluidCanvasBackdrop` danno ai componenti dentro: un tasto, una
 * capsula e una card della scena rifrangono la stessa cosa.
 *
 * Con `motion scroll` il fondo scorre come nella pagina «Misure»: la traslazione di un layer alto uno schermo più un
 * periodo, quindi il filo principale non ridisegna niente e ogni lastra rifà la sua cattura a ogni fotogramma.
 */
@Composable
internal fun MaterialStage(
  config: MaterialConfig,
  clock: MeasureClock,
  stage: GlassBackdropState,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    MaterialBackdropLayer(config, clock, Modifier.fillMaxSize().glassBackdropSource(stage))
    CompositionLocalProvider(LocalGlassBackdrop provides stage, LocalFluidCanvasBackdrop provides stage) {
      content()
    }
  }
}

/** Un disegno che si ripete uguale ogni `period` pixel in verticale. */
private class MaterialPattern(val period: Int, val draw: DrawScope.() -> Unit)

@Composable
private fun MaterialBackdropLayer(config: MaterialConfig, clock: MeasureClock, modifier: Modifier) {
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.bodyLarge
  val base = when (config.backdrop) {
    MaterialBackdrop.Page, MaterialBackdrop.Text -> scheme.background
    MaterialBackdrop.PageLight -> MaterialPageLight
    MaterialBackdrop.PageDark -> MaterialPageDark
    MaterialBackdrop.White -> Color.White
    MaterialBackdrop.Black -> Color.Black
    MaterialBackdrop.Photo -> Color(0xFF6B5A44)
  }
  val ink = scheme.onBackground
  BoxWithConstraints(modifier = modifier.background(base)) {
    // Senza una taglia finita non c'è un palco da riempire (solo in un'anteprima).
    if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
      val widthPx = constraints.maxWidth
      val heightPx = constraints.maxHeight
      val pattern = remember(config.backdrop, widthPx, density, base, ink, style) {
        when (config.backdrop) {
          MaterialBackdrop.Photo -> {
            val period = with(density) { 512.dp.roundToPx() }
            val image = generateMeasurePhoto()
            val brush: Brush = ShaderBrush(
              ImageShader(image, TileMode.Repeated, TileMode.Repeated).apply {
                val scale = period / image.width.toFloat()
                setLocalMatrix(Matrix().apply { setScale(scale, scale) })
              },
            )
            MaterialPattern(period) { drawRect(brush) }
          }
          MaterialBackdrop.Text -> {
            val padding = with(density) { 18.dp.roundToPx() }
            val layout = measurer.measure(
              text = MaterialText,
              // In dp e non in sp: la scala del testo del dispositivo non deve cambiare il fondo.
              style = style.copy(
                color = ink,
                fontSize = with(density) { 22.dp.toSp() },
                lineHeight = with(density) { 31.dp.toSp() },
              ),
              overflow = TextOverflow.Clip,
              softWrap = true,
              maxLines = MaterialTextLines,
              constraints = Constraints(maxWidth = (widthPx - 2 * padding).coerceAtLeast(1)),
            )
            MaterialPattern(layout.size.height.coerceAtLeast(1)) {
              val blocks = ceil(size.height / layout.size.height.coerceAtLeast(1)).toInt()
              for (block in 0 until blocks) {
                drawText(layout, topLeft = Offset(padding.toFloat(), (block * layout.size.height).toFloat()))
              }
            }
          }
          // Un colore solo: si disegna lo stesso, così un fondo che scorre fa rifare la cattura come gli altri.
          else -> MaterialPattern(with(density) { 480.dp.roundToPx() }) { drawRect(base) }
        }
      }
      val period = pattern.period.coerceAtLeast(1)
      val speedPx = with(density) { config.speedDp.dp.toPx() }
      val scrolls = config.motion == MaterialMotion.Scroll
      Box(
        modifier = Modifier
          .layout { measurable, _ ->
            val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx + period))
            layout(widthPx, heightPx) { placeable.place(0, 0) }
          }
          .graphicsLayer {
            translationY = if (scrolls) -((clock.seconds * speedPx) % period) else 0f
          }
          .drawBehind { pattern.draw(this) },
      )
    }
  }
}

private const val MaterialTextLines = 16

/** La frase delle scene di testo del banco e della pagina «Misure»: lo stesso testo, così si confrontano. */
private val MaterialText: String = List(18) {
  "Signs are made using neon lights. Light is used to define color, tone, and shape. " +
    "They are irreplaceable, and depend on the people who bend them all over the world."
}.joinToString(" ")
