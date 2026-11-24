package dev.antigravity.fluidengine.widget

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import dev.antigravity.fluidengine.ui.fluid.fluidGaugeReading
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.6 · Il quadrante sul widget: Glance lo compone (il numero, l'etichetta, la frase per TalkBack) e le maschere si
 * disegnano davvero (grafica nativa di Robolectric): la pista intera, il valore fino alla sua frazione, un segno per
 * soglia. Le maschere della prova si salvano in `build/f8-6-widget/` per guardarle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EngineWidgetGaugeTest {

  private val absences = listOf(FluidGaugeThreshold(25f, "Limite"))

  private fun palette() = engineWidgetPalette(RuntimeEnvironment.getApplication(), EngineSettings())

  private fun layout() = resolveEngineWidgetLayout(DpSize(180.dp, 180.dp))

  @Test
  fun `Glance compone l'anello col numero e la frase per TalkBack`() = runGlanceAppWidgetUnitTest {
    setContext(RuntimeEnvironment.getApplication())
    setAppWidgetSize(DpSize(180.dp, 180.dp))
    provideComposable {
      EngineWidgetGauge(
        value = 18f,
        range = 0f..100f,
        palette = palette(),
        layout = layout(),
        valueText = "18 %",
        label = "Assenze",
        minText = "0",
        maxText = "100 %",
        thresholds = absences,
        thresholdTexts = listOf("25 %"),
      )
    }
    onNode(hasText("18 %")).assertExists()
    onNode(hasContentDescription("Assenze. 18 %, da 0 a 100 %. Limite 25 %.")).assertExists()
  }

  @Test
  fun `l'arco grande scrive anche l'etichetta, la soglia superata si dice`() = runGlanceAppWidgetUnitTest {
    setContext(RuntimeEnvironment.getApplication())
    setAppWidgetSize(DpSize(180.dp, 180.dp))
    provideComposable {
      EngineWidgetGauge(
        value = 27f,
        range = 0f..100f,
        palette = palette(),
        layout = layout(),
        valueText = "27 %",
        style = FluidGaugeStyle.Arc,
        label = "Assenze",
        thresholds = absences,
        thresholdTexts = listOf("25 %"),
      )
    }
    onNode(hasText("27 %")).assertExists()
    onNode(hasText("Assenze")).assertExists()
    onNode(hasContentDescription("oltre: Limite 25 %", true)).assertExists()
  }

  @Test
  fun `la barra a capacita' ha l'etichetta, il valore e gli estremi`() = runGlanceAppWidgetUnitTest {
    setContext(RuntimeEnvironment.getApplication())
    setAppWidgetSize(DpSize(250.dp, 110.dp))
    provideComposable {
      EngineWidgetGauge(
        value = 41.5f,
        range = 0f..64f,
        palette = palette(),
        layout = resolveEngineWidgetLayout(DpSize(250.dp, 110.dp)),
        valueText = "41,5 GB",
        style = FluidGaugeStyle.Capacity,
        label = "Spazio",
        minText = "0",
        maxText = "64 GB",
      )
    }
    onNode(hasText("Spazio")).assertExists()
    onNode(hasText("41,5 GB")).assertExists()
    onNode(hasText("64 GB")).assertExists()
  }

  @Test
  fun `le maschere dell'anello sono bianche, la pista intera e il valore fino a un quarto`() {
    val reading = fluidGaugeReading(25f, 0f..100f, absences)
    val masks = engineWidgetGaugeMasks(FluidGaugeStyle.Ring, reading, 40f, 40f, 3f)
    save("anello-traccia", masks.track)
    save("anello-valore", masks.value!!)
    assertEquals(120, masks.track.width)
    // In alto al centro l'anello comincia: pista e valore ci sono.
    val topX = 60
    val topY = (0..30).first { android.graphics.Color.alpha(masks.track.getPixel(topX, it)) > 0 }
    val y = topY + 3
    assertTrue("la pista in alto", android.graphics.Color.alpha(masks.track.getPixel(topX, y)) in 30..70)
    assertTrue("il valore in alto", android.graphics.Color.alpha(masks.value.getPixel(topX + 4, y)) > 200)
    // Un quarto: il valore arriva a ore 3 e non a ore 6.
    val bottomX = 60
    val bottomY = 119 - topY - 3
    assertTrue("la pista in basso c'e'", android.graphics.Color.alpha(masks.track.getPixel(bottomX, bottomY)) > 0)
    assertEquals("il valore in basso non c'e'", 0, android.graphics.Color.alpha(masks.value.getPixel(bottomX, bottomY)))
    assertEquals("un segno per soglia", 1, masks.marks.size)
    // Bianche: il colore lo da' il tinteggio dell'host.
    val pixel = masks.value.getPixel(topX + 4, y)
    assertEquals(255, android.graphics.Color.red(pixel))
    assertEquals(255, android.graphics.Color.blue(pixel))
  }

  @Test
  fun `l'arco e' aperto in basso e a zero non c'e' la maschera del valore`() {
    val reading = fluidGaugeReading(0f, 0f..100f)
    val masks = engineWidgetGaugeMasks(FluidGaugeStyle.Arc, reading, 120f, 120f, 2f)
    save("arco-traccia", masks.track)
    assertNull(masks.value)
    val w = masks.track.width
    val bottom = (w - 1 downTo w / 2).firstOrNull { android.graphics.Color.alpha(masks.track.getPixel(w / 2, it)) > 0 }
    assertNull("in basso al centro l'arco e' aperto", bottom)
    val top = (0 until w / 2).firstOrNull { android.graphics.Color.alpha(masks.track.getPixel(w / 2, it)) > 0 }
    assertTrue("in alto l'arco c'e'", top != null)
  }

  @Test
  fun `una soglia senza colore prende il tono del pericolo, una con colore il suo`() {
    val palette = palette()
    val two = listOf(FluidGaugeThreshold(20f), FluidGaugeThreshold(25f), FluidGaugeThreshold(5f, color = Color(0xFF00897B)))
    assertEquals(palette.warningTone.content, engineWidgetThresholdColor(0, two, palette))
    assertEquals(palette.dangerTone.content, engineWidgetThresholdColor(1, two, palette))
    assertEquals("25", engineWidgetPlainNumber(25f))
    assertEquals("2.5", engineWidgetPlainNumber(2.5f))
  }

  /**
   * Un'anteprima per gli occhi (non una prova): le maschere tinte coi colori veri della palette di serie, sulla card del
   * widget, in chiaro e in scuro, col testo scritto dove lo scrive Glance. Non e' il launcher: e' cio' che l'host compone.
   */
  @Test
  fun `anteprima del widget in chiaro e in scuro`() {
    for (night in listOf(false, true)) {
      val base = RuntimeEnvironment.getApplication()
      val config = android.content.res.Configuration(base.resources.configuration).apply {
        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
          (if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO)
      }
      val context = base.createConfigurationContext(config)
      val palette = engineWidgetPalette(context, EngineSettings())
      val scale = 3f
      val sheet = Bitmap.createBitmap((340 * scale).toInt(), (170 * scale).toInt(), Bitmap.Config.ARGB_8888)
      val canvas = android.graphics.Canvas(sheet)
      canvas.drawColor(palette.background.getColor(context).toArgbInt())
      val card = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = palette.card.getColor(context).toArgbInt() }
      canvas.drawRoundRect(8f * scale, 8f * scale, 332f * scale, 162f * scale, 20f * scale, 20f * scale, card)
      fun tinted(mask: Bitmap, color: Int, x: Float, y: Float) {
        val paint = android.graphics.Paint().apply { colorFilter = android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN) }
        canvas.drawBitmap(mask, x * scale, y * scale, paint)
      }
      fun text(value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
          this.color = color
          textSize = size * scale
          textAlign = android.graphics.Paint.Align.CENTER
          typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
        canvas.drawText(value, x * scale, y * scale, paint)
      }
      val onSurface = palette.onSurface.getColor(context).toArgbInt()
      val muted = palette.onSurfaceVariant.getColor(context).toArgbInt()
      // L'arco delle assenze, oltre il limite.
      val arc = fluidGaugeReading(27f, 0f..100f, absences)
      val arcMasks = engineWidgetGaugeMasks(FluidGaugeStyle.Arc, arc, 120f, 120f, scale)
      val danger = engineWidgetThresholdColor(0, absences, palette).getColor(context).toArgbInt()
      tinted(arcMasks.track, danger, 20f, 20f)
      tinted(arcMasks.value!!, danger, 20f, 20f)
      tinted(arcMasks.marks[0], danger, 20f, 20f)
      text("27 %", 80f, 84f, 24f, onSurface, bold = true)
      text("Assenze", 80f, 100f, 11f, muted)
      // Due anelli e la barra dello spazio.
      val accent = palette.accent.getColor(context).toArgbInt()
      listOf(6.9f to 200f, 8.2f to 260f).forEach { (mean, x) ->
        val ring = engineWidgetGaugeMasks(FluidGaugeStyle.Ring, fluidGaugeReading(mean, 0f..10f), 40f, 40f, scale)
        tinted(ring.track, accent, x, 24f)
        tinted(ring.value!!, accent, x, 24f)
        text(String.format(java.util.Locale.ITALY, "%.1f", mean), x + 20f, 48f, 11f, onSurface, bold = true)
      }
      val bar = engineWidgetGaugeMasks(FluidGaugeStyle.Capacity, fluidGaugeReading(41.5f, 0f..64f), 120f, 8f, scale)
      text("Spazio · 41,5 GB", 260f, 112f, 12f, onSurface, bold = true)
      tinted(bar.track, accent, 200f, 120f)
      tinted(bar.value!!, accent, 200f, 120f)
      text("0", 204f, 142f, 10f, muted)
      text("64 GB", 304f, 142f, 10f, muted)
      save(if (night) "anteprima-scuro" else "anteprima-chiaro", sheet)
    }
  }

  private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
  )

  private fun save(name: String, bitmap: Bitmap) {
    val dir = File("build/f8-6-widget").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
