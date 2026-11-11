package dev.antigravity.fluidengine.sample.measure

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * I colori della pagina di prova. Non sono token del tema, di proposito: sono quelli delle scene del
 * banco (`tools/glassbench/scenes`), perché un numero preso qui si confronti con un'immagine resa là.
 */
internal class MeasurePalette(val page: Color, val ink: Color, val accent: Color)

private val LightPalette = MeasurePalette(page = Color(0xFFE3E3E5), ink = Color(0xFF0A0A0A), accent = Color(0xFFD23C3C))
private val DarkPalette = MeasurePalette(page = Color(0xFF1C1C1E), ink = Color(0xFFF2F2F7), accent = Color(0xFFFF6B5E))

@Composable
internal fun measurePalette(): MeasurePalette = if (GlassDefaults.isDarkSurface()) DarkPalette else LightPalette

/**
 * Le due registrazioni del fondale: quella che campiona il prototipo (`drawBackdrop` vuole un
 * `Backdrop`, e quello di un `GlassBackdropState` è interno all'engine) e quella che campiona il vetro
 * di oggi. Registrano lo stesso contenuto, quindi ciò che i due vetri hanno dietro è identico.
 */
@Stable
internal class MeasureSources(val layer: LayerBackdrop, val glass: GlassBackdropState)

@Composable
internal fun rememberMeasureSources(): MeasureSources {
  val layer = rememberLayerBackdrop()
  val glass = rememberGlassBackdrop()
  return remember(layer, glass) { MeasureSources(layer, glass) }
}

/**
 * Il palco di una scena: il fondale registrato come sorgente, sopra [content], e a fondale fermo il
 * battito che tiene lo schermo in ridisegno.
 */
@Composable
internal fun MeasureStage(
  config: MeasureConfig,
  clock: MeasureClock,
  sources: MeasureSources,
  modifier: Modifier = Modifier,
  /**
   * Disegnato sopra il fondale e **dentro** la sua registrazione: ciò che legge qui uno stato fa
   * ri-registrare la sorgente quando lo stato cambia, come una pagina che cambia sotto un vetro fermo.
   */
  backdropOverlay: (DrawScope.() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    MeasureBackdrop(
      config = config,
      clock = clock,
      modifier = Modifier
        .fillMaxSize()
        // Come `glassBackdropSource`: la registrazione, e sotto di lei il fondale rasterizzato una
        // volta sola fuori schermo, così ogni vetro campiona un quadrilatero e non rigira la pagina.
        .layerBackdrop(sources.layer)
        .glassBackdropSource(sources.glass)
        .then(
          if (backdropOverlay != null) {
            Modifier.drawWithContent {
              drawContent()
              backdropOverlay()
            }
          } else {
            Modifier
          },
        ),
    )
    content()
    if (config.motion == MeasureMotion.Still) MeasureHeartbeat(clock)
  }
}

/**
 * Il fondale: opaco, noto, e — quando scorre — in moto a velocità costante.
 *
 * Lo scorrimento è la traslazione di **un layer**, non un nuovo layout a ogni fotogramma: il filo
 * principale non ridisegna niente, e ciò che resta nel fotogramma è il lavoro del RenderThread sui
 * vetri che campionano il fondale, cioè la cosa da misurare. Il disegno è periodico e il layer è alto
 * uno schermo più un periodo, quindi il moto riparte senza cucitura.
 */
@Composable
private fun MeasureBackdrop(config: MeasureConfig, clock: MeasureClock, modifier: Modifier = Modifier) {
  val palette = measurePalette()
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val baseStyle = MaterialTheme.typography.bodyLarge
  BoxWithConstraints(modifier = modifier.background(palette.page)) {
    // Senza una taglia finita non c'e' un palco da riempire (succede solo in un'anteprima).
    if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
      val widthPx = constraints.maxWidth
      val heightPx = constraints.maxHeight
      val pattern: MeasurePattern = remember(config.backdrop, widthPx, density, palette, baseStyle) {
        when (config.backdrop) {
          MeasureBackdropKind.Flat -> FlatPattern(palette.page, with(density) { 480.dp.roundToPx() })
          MeasureBackdropKind.Checker -> CheckerPattern(palette, with(density) { 24.dp.toPx() })
          MeasureBackdropKind.Photo -> PhotoPattern(generateMeasurePhoto(), with(density) { 512.dp.roundToPx() })
          MeasureBackdropKind.Text -> {
            val padding = with(density) { 14.dp.roundToPx() }
            val layout = measurer.measure(
              text = MeasureText,
              // Corpo e interlinea in dp (non in sp): la scala del testo del dispositivo non deve cambiare il fondale.
              style = baseStyle.copy(
                color = palette.ink,
                fontSize = with(density) { 22.dp.toSp() },
                lineHeight = with(density) { 31.dp.toSp() },
              ),
              overflow = TextOverflow.Clip,
              softWrap = true,
              maxLines = MeasureTextLines,
              constraints = Constraints(maxWidth = (widthPx - 2 * padding).coerceAtLeast(1)),
            )
            TextPattern(layout, padding)
          }
        }
      }
      val period = pattern.periodPx.coerceAtLeast(1)
      val speedPx = with(density) { config.speedDp.dp.toPx() }
      val scrolls = config.motion == MeasureMotion.Scroll
      Box(
        modifier = Modifier
          .layout { measurable, _ ->
            val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx + period))
            layout(widthPx, heightPx) { placeable.place(0, 0) }
          }
          .graphicsLayer {
            translationY = if (scrolls) -((clock.seconds * speedPx) % period) else 0f
          }
          .drawBehind { with(pattern) { draw() } },
      )
    }
  }
}

/**
 * A fondale fermo niente chiederebbe un fotogramma, e una scena senza fotogrammi non si misura.
 *
 * Due punti da due pixel agli angoli opposti, in un layer loro, che cambiano di un livello a ogni
 * fotogramma: il layer è grande quanto il palco, quindi il sistema ricompone tutto il palco, ma i
 * vetri sotto non cambiano. È il fotogramma «a riposo»: il corpo esce dalla cache, la luce (che non è
 * un layer) si riesegue.
 */
@Composable
private fun MeasureHeartbeat(clock: MeasureClock) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer { compositingStrategy = CompositingStrategy.Auto }
      .drawBehind {
        val color = if (clock.frame % 2 == 0) Color(0xFF808080) else Color(0xFF7F7F7F)
        val dot = Size(2f, 2f)
        drawRect(color = color, topLeft = Offset.Zero, size = dot)
        drawRect(color = color, topLeft = Offset(size.width - 2f, size.height - 2f), size = dot)
      },
  )
}

/** Un disegno che si ripete uguale ogni [periodPx] in verticale. */
private interface MeasurePattern {
  val periodPx: Int

  /** Disegna su tutta la taglia dello scope (uno schermo più un periodo). */
  fun DrawScope.draw()
}

/** Un colore solo. Si disegna lo stesso: un layer vuoto che trasla non sporca niente, e niente si rifarebbe. */
private class FlatPattern(private val color: Color, override val periodPx: Int) : MeasurePattern {
  override fun DrawScope.draw() {
    drawRect(color)
  }
}

/** Scacchi chiaro/scuro: il caso peggiore per il bordo di una lente, e quello che fa sfarfallare l'adattività. */
private class CheckerPattern(private val palette: MeasurePalette, private val stepPx: Float) : MeasurePattern {
  // Venti righe, cioè dieci coppie: la riga pari e la dispari sono sfasate di una casella.
  override val periodPx: Int = (stepPx * 20f).roundToInt()

  override fun DrawScope.draw() {
    val rows = ceil(size.height / stepPx).toInt()
    for (row in 0 until rows) {
      drawLine(
        color = palette.ink,
        start = Offset(0f, (row + 0.5f) * stepPx),
        end = Offset(size.width, (row + 0.5f) * stepPx),
        strokeWidth = stepPx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stepPx, stepPx), if (row % 2 == 0) 0f else stepPx),
      )
    }
  }
}

/** Un blocco di testo impaginato una volta e ripetuto: il periodo è la sua altezza vera. */
private class TextPattern(private val layout: TextLayoutResult, private val paddingPx: Int) : MeasurePattern {
  override val periodPx: Int = layout.size.height.coerceAtLeast(1)

  override fun DrawScope.draw() {
    val blocks = ceil(size.height / periodPx).toInt()
    for (block in 0 until blocks) {
      drawText(layout, topLeft = Offset(paddingPx.toFloat(), (block * periodPx).toFloat()))
    }
  }
}

/** La «fotografia»: una piastrella generata in codice, ripetibile in ogni direzione, larga [periodPx]. */
private class PhotoPattern(image: ImageBitmap, override val periodPx: Int) : MeasurePattern {
  private val brush: Brush = ShaderBrush(
    ImageShader(image, TileMode.Repeated, TileMode.Repeated).apply {
      val scale = periodPx / image.width.toFloat()
      setLocalMatrix(Matrix().apply { setScale(scale, scale) })
    },
  )

  override fun DrawScope.draw() {
    drawRect(brush)
  }
}

private const val MeasureTextLines = 16

/** La frase delle scene di testo del banco, ripetuta quanto basta a riempire sedici righe su un tablet. */
private val MeasureText: String = List(18) {
  "Signs are made using neon lights. Light is used to define color, tone, and shape. " +
    "They are irreplaceable, and depend on the people who bend them all over the world."
}.joinToString(" ")

/**
 * Una piastrella che somiglia a una fotografia senza esserlo: dune (sfumature larghe, dove si vedono
 * le bande), grana a tre ottave, una fascia di cielo saturo e una macchia rossa (i colori che un
 * pannello a gamma larga rende diversi). Nessun numero casuale: il rumore è un hash di coordinate,
 * quindi l'immagine è la stessa a ogni lancio e su ogni dispositivo. Si ripete senza cuciture.
 */
internal fun generateMeasurePhoto(side: Int = 512): ImageBitmap {
  val pixels = IntArray(side * side)
  val twoPi = (2.0 * PI).toFloat()
  for (y in 0 until side) {
    val v = y / side.toFloat()
    for (x in 0 until side) {
      val u = x / side.toFloat()
      val dune = 0.5f + 0.5f * sin(twoPi * (2f * v + 0.35f * sin(twoPi * u)))
      val grain = 0.55f * tileNoise(x, y, 64, side) + 0.30f * tileNoise(x, y, 16, side) + 0.15f * tileNoise(x, y, 4, side)
      val light = (0.62f * dune + 0.38f * grain).coerceIn(0f, 1f)

      // Sabbia: dall'ombra calda alla cresta chiara.
      var r = lerp(0.72f, 0.97f, light)
      var g = lerp(0.47f, 0.87f, light)
      var b = lerp(0.24f, 0.66f, light)

      // Una fascia di cielo saturo, ondulata.
      val band = 0.5f + 0.5f * cos(twoPi * (v + 0.08f * sin(twoPi * 2f * u)))
      val sky = smooth(0.80f, 0.97f, band)
      r = lerp(r, 0.10f + 0.10f * grain, sky)
      g = lerp(g, 0.42f + 0.12f * grain, sky)
      b = lerp(b, 0.88f, sky)

      // Una macchia rossa, con la distanza che gira attorno ai bordi della piastrella.
      val dx = wrapDistance(u - 0.30f)
      val dy = wrapDistance(v - 0.72f)
      val spot = exp(-(dx * dx + dy * dy) / 0.012f)
      r = lerp(r, 0.93f, spot)
      g = lerp(g, 0.22f, spot)
      b = lerp(b, 0.16f, spot)

      pixels[y * side + x] = (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
  }
  return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Rumore a reticolo, liscio, che si ripete ogni [side] pixel: i nodi si leggono modulo il lato. */
private fun tileNoise(x: Int, y: Int, cell: Int, side: Int): Float {
  val nodes = side / cell
  val fx = x / cell.toFloat()
  val fy = y / cell.toFloat()
  val x0 = floor(fx).toInt()
  val y0 = floor(fy).toInt()
  val tx = fade(fx - x0)
  val ty = fade(fy - y0)
  val a = lattice(x0 % nodes, y0 % nodes, cell)
  val b = lattice((x0 + 1) % nodes, y0 % nodes, cell)
  val c = lattice(x0 % nodes, (y0 + 1) % nodes, cell)
  val d = lattice((x0 + 1) % nodes, (y0 + 1) % nodes, cell)
  return lerp(lerp(a, b, tx), lerp(c, d, tx), ty)
}

/** Un valore fra 0 e 1 per un nodo del reticolo: un hash intero, lo stesso ovunque. */
private fun lattice(x: Int, y: Int, seed: Int): Float {
  var h = x * 374761393 + y * 668265263 + seed * 1442695041
  h = (h xor (h ushr 13)) * 1274126177
  h = h xor (h ushr 16)
  return (h and 0xFFFF) / 65535f
}

private fun fade(t: Float): Float = t * t * (3f - 2f * t)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smooth(from: Float, to: Float, value: Float): Float = fade(((value - from) / (to - from)).coerceIn(0f, 1f))

/** La distanza fra due coordinate 0..1 su una piastrella che si ripete: mai più di mezzo lato. */
private fun wrapDistance(delta: Float): Float {
  val d = delta - floor(delta)
  return if (d > 0.5f) 1f - d else d
}

private fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
