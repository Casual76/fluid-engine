package dev.antigravity.fluidengine.ui.fluid.output

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius

/**
 * I colori del materiale piatto «stampa» (4.0, F8.10): il vetro di un'uscita, dove il vetro non ha niente da piegare.
 *
 * Sono i tre segni con cui si riconosce il vetro dell'engine, fermi: il film del modale (la superficie, appena tinta
 * dall'accento), la luce del bordo in alto a sinistra e il riflesso morbido in cima. Nessuna sfocatura e nessuna lente:
 * su una bitmap o su un PDF sarebbero un'immagine sbagliata di qualcosa che non c'e'. Numeri a occhio (24 novembre
 * 2026), sulle prove del materiale `modalTint()` di [dev.antigravity.fluidengine.ui.fluid.GlassDefaults]: il film pieno
 * e' la superficie del ripiego (0,97), qui opaca.
 */
class FluidPrintColors(
  val fill: Color,
  val rim: Color,
  val sheen: Color,
  val shadow: Color,
  val content: Color,
  val secondaryContent: Color,
) {
  companion object {
    /** Il materiale per [tint] (di serie l'accento), sul chiaro o sullo scuro del tema. */
    @Composable
    fun of(tint: Color = MaterialTheme.colorScheme.primary): FluidPrintColors {
      val scheme = MaterialTheme.colorScheme
      val dark = scheme.surface.luminance() < 0.5f
      return FluidPrintColors(
        fill = lerp(scheme.surface, tint, if (dark) 0.10f else 0.05f),
        rim = Color.White.copy(alpha = if (dark) 0.20f else 0.70f),
        sheen = Color.White.copy(alpha = if (dark) 0.06f else 0.40f),
        shadow = Color.Black.copy(alpha = if (dark) 0.40f else 0.16f),
        content = scheme.onSurface,
        secondaryContent = scheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Un pannello di materiale piatto «stampa»: il posto del vetro in un'immagine da condividere o in una pagina stampata
 * ([LocalFluidPrinting]). Dentro il testo prende `onSurface`, come su un pannello di vetro.
 *
 * L'ombra e' una sfocatura di maschera (`BlurMaskFilter`), che la tela software di un'uscita sa disegnare; il resto sono
 * riempimenti e sfumature.
 */
@Composable
fun FluidPrintPanel(
  modifier: Modifier = Modifier,
  shape: Shape = ContinuousCornerShape(FluidRadius.Sheet),
  colors: FluidPrintColors = FluidPrintColors.of(),
  contentPadding: PaddingValues = PaddingValues(24.dp),
  shadowRadius: Dp = 28.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier
      .drawBehind {
        if (shadowRadius > 0.dp) {
          val outline = shape.createOutline(size, layoutDirection, this)
          val radius = shadowRadius.toPx()
          val path = Path().apply { addOutline(outline) }.asAndroidPath()
          drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
              color = colors.shadow.toArgb()
              maskFilter = BlurMaskFilter(radius / 2f, BlurMaskFilter.Blur.NORMAL)
            }
            val native = canvas.nativeCanvas
            native.save()
            native.translate(0f, radius / 3f)
            native.drawPath(path, paint)
            native.restore()
          }
        }
      }
      .clip(shape)
      .background(colors.fill)
      .drawBehind {
        // Il riflesso in cima: il chiaro del vetro che prende luce dall'alto.
        drawRect(Brush.verticalGradient(0f to colors.sheen, 0.45f to Color.Transparent), size = size)
      }
      .border(
        width = 1.dp,
        brush = Brush.linearGradient(
          0f to colors.rim,
          0.5f to colors.rim.copy(alpha = colors.rim.alpha * 0.25f),
          1f to colors.rim.copy(alpha = colors.rim.alpha * 0.6f),
          start = Offset.Zero,
          end = Offset.Infinite,
        ),
        shape = shape,
      )
      .padding(contentPadding),
  ) {
    CompositionLocalProvider(LocalContentColor provides colors.content) { content() }
  }
}

/**
 * La cornice di un'immagine da condividere ([FluidShareImageRenderer]): il fondo dell'accento, il pannello «stampa» al
 * centro col contenuto e, in fondo, la firma (il nome dell'app). Come le card che Fitness o Musica fanno per le storie:
 * il contenuto e' dell'app, la cornice dell'engine.
 *
 * Il formato si legge dallo spazio che c'e': in 9:16 il pannello sta al centro con piu' aria, in quadrato riempie di
 * piu'. Riempie tutto il riquadro che riceve (l'intera tela del formato).
 *
 * @param accent il colore del fondo; di serie l'accento dell'app.
 * @param signature la riga in fondo («ClasseViva», «Fluid Glass»); null per nessuna.
 */
@Composable
fun FluidShareImageFrame(
  modifier: Modifier = Modifier,
  accent: Color = MaterialTheme.colorScheme.primary,
  signature: String? = null,
  panelColors: FluidPrintColors = FluidPrintColors.of(accent),
  content: @Composable ColumnScope.() -> Unit,
) {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  // Sullo scuro l'accento del tema e' un pastello: il fondo lo porta verso il nero, come le card scure delle storie,
  // perche' il pannello scuro stia su un fondo scuro. Sul chiaro va dal chiaro al pieno. A occhio, 24 novembre 2026.
  val light = if (dark) lerp(accent, Color.Black, 0.35f) else lerp(accent, Color.White, 0.35f)
  val middle = if (dark) lerp(accent, Color.Black, 0.55f) else accent
  val deep = lerp(accent, Color.Black, if (dark) 0.75f else 0.25f)
  val onAccent = if (lerp(light, deep, 0.5f).luminance() > 0.55f) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.9f)
  Box(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(light, middle, deep), start = Offset.Zero, end = Offset.Infinite)),
  ) {
    FrameGlow(dark)
    Column(
      Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      FluidPrintPanel(Modifier.fillMaxWidth().widthIn(max = 420.dp), colors = panelColors, content = content)
      if (signature != null) {
        Spacer(Modifier.height(20.dp))
        Text(
          signature,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = onAccent,
        )
      }
    }
  }
}

/** Due chiarori morbidi sul fondo, come la luce sotto una lastra: fermi, disegnati una volta. */
@Composable
private fun BoxScope.FrameGlow(dark: Boolean) {
  Box(
    Modifier.matchParentSize().drawBehind {
      val a = if (dark) 0.10f else 0.22f
      drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.1f), radius = size.minDimension * 0.7f),
        radius = size.minDimension * 0.7f, center = Offset(size.width * 0.15f, size.height * 0.1f),
      )
      drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = a * 0.6f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.95f), radius = size.minDimension * 0.6f),
        radius = size.minDimension * 0.6f, center = Offset(size.width * 0.9f, size.height * 0.95f),
      )
    },
  )
}
