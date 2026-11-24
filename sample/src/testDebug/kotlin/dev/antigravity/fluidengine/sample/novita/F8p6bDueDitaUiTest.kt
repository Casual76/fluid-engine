package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidChartDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidChartRangeHighlight
import dev.antigravity.fluidengine.ui.fluid.FluidChartSeries
import dev.antigravity.fluidengine.ui.fluid.FluidLineChart
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import java.util.Locale
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.6b · Due dita alla maniera di Borsa (scelta di Alessio del 24 novembre), col dito vero e coi pixel veri (PixelCopy):
 * fuori dall'intervallo linea e area sbiadiscono, la differenza e le due linee sono verdi se si sale e rosse se si
 * scende, e il piano del grafico non si ridisegna mentre le dita si muovono. Con [FluidChartRangeHighlight.Band] resta la
 * fascia di prima, senza sbiadire.
 *
 * I fermi in chiaro e in scuro si salvano in `build/f8-6b/` (densita' xxhdpi, 3 px a dp), per la tavola contro Borsa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class F8p6bDueDitaUiTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private val grades = listOf(5.5f, 6f, 6.5f, 5f, 6f, 7f, 7.5f, 6.5f, 7f, 8f, 7.5f, 8.5f)
  private val dates = listOf("12 set", "26 set", "10 ott", "24 ott", "7 nov", "21 nov", "5 dic", "19 dic", "16 gen", "30 gen", "13 feb", "27 feb")

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidChartDiagnostics.resetForTesting()
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun fmt(v: Float): String = String.format(Locale.ITALY, if (v % 1f == 0f) "%.0f" else "%.1f", v)

  private fun show(dark: Boolean = false, highlight: FluidChartRangeHighlight = FluidChartRangeHighlight.Signed) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 1f)) {
          // Il fondo del tema: senza, in scuro il grafico starebbe sul bianco della finestra.
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.padding(top = 80.dp)) { Grades(highlight) }
          }
        }
      }
    }
    advance(900)
    finger = MaterialFinger(view)
  }

  @Composable
  private fun Grades(highlight: FluidChartRangeHighlight) {
    val series = remember { listOf(FluidChartSeries("Matematica")) }
    val points = remember { listOf<List<Float?>>(grades) }
    FluidLineChart(
      series = series,
      points = points,
      xLabels = dates,
      startAtZero = false,
      valueText = ::fmt,
      rangeHighlight = highlight,
      modifier = Modifier.padding(16.dp).testTag("grafico"),
    )
  }

  private fun slot(index: Int): Rect = compose
    .onNode(hasContentDescription("${dates[index]}: ${fmt(grades[index])}"), useUnmergedTree = true)
    .fetchSemanticsNode().boundsInRoot

  private fun chart(): Rect = compose.onNodeWithTag("grafico", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /** Il testo della capsula con i suoi colori, o null. */
  private fun capsuleText(text: String): AnnotatedString? = compose
    .onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(FluidChartDiagnostics.CapsuleTag)), useUnmergedTree = true)
    .fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()

  private fun snapshot(): Bitmap {
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()
    val window = activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    return image
  }

  /** Quanto inchiostro c'e' in [rect]: la distanza di ogni pixel dal fondo del grafico, sommata. */
  private fun ink(image: Bitmap, rect: Rect, background: Int): Long {
    var total = 0L
    for (y in rect.top.toInt() until rect.bottom.toInt()) {
      for (x in rect.left.toInt() until rect.right.toInt()) {
        val p = image.getPixel(x, y)
        total += abs(android.graphics.Color.red(p) - android.graphics.Color.red(background)) +
          abs(android.graphics.Color.green(p) - android.graphics.Color.green(background)) +
          abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(background))
      }
    }
    return total
  }

  private fun save(name: String, image: Bitmap) {
    val c = chart()
    val crop = Bitmap.createBitmap(image, c.left.toInt(), (c.top - 80f).toInt().coerceAtLeast(0), c.width.toInt(), (c.height + 80f).toInt())
    val dir = File("build/f8-6b").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  /** Le due dita su [a] e [b], tenute; il fermo si prende con le dita giu'. */
  private fun hold(a: Int, b: Int, block: () -> Unit) {
    val pa = slot(a).center
    val pb = slot(b).center
    finger.downAll(listOf(pa, pb))
    advance(400)
    block()
    finger.upAll(listOf(pa, pb))
    advance(500)
  }

  @Test
  fun `due dita come Borsa - fuori dall'intervallo il grafico sbiadisce, dentro no`() {
    show()
    val before = snapshot()
    val plot = slot(0)
    val lower = plot.top + plot.height / 2f
    val background = before.getPixel((chart().left + 4f).toInt(), (chart().top + 4f).toInt())
    val outside = Rect(slot(0).left, lower, slot(2).center.x - 8f, plot.bottom)
    val inside = Rect(slot(2).center.x + 8f, lower, slot(6).center.x - 8f, plot.bottom)
    hold(2, 6) {
      val during = snapshot()
      val outBefore = ink(before, outside, background)
      val outDuring = ink(during, outside, background)
      val inBefore = ink(before, inside, background)
      val inDuring = ink(during, inside, background)
      assertTrue("fuori sbiadisce: $outDuring su $outBefore", outDuring < outBefore * 0.7)
      assertTrue("dentro resta: $inDuring su $inBefore", inDuring > inBefore * 0.85 && inDuring < inBefore * 1.2)
      save("due-dita-sale-chiaro", during)
    }
  }

  @Test
  fun `la differenza e le due linee sono verdi se si sale e rosse se si scende`() {
    show()
    // Dal 10 ott (6,5) al 5 dic (7,5): +1, cioe' +15%.
    hold(2, 6) {
      val text = capsuleText("+1 (+15%)")
      assertTrue("la capsula dice la differenza", text != null)
      val color = text!!.spanStyles.single().item.color.toArgb()
      assertTrue("verde: ${Integer.toHexString(color)}", android.graphics.Color.green(color) > android.graphics.Color.red(color) + 40)
      // La linea del 10 ott, sotto la capsula: verde anche lei.
      val image = snapshot()
      val plot = slot(2)
      val x = plot.center.x.toInt()
      val greens = (plot.top.toInt() + (plot.height * 0.55f).toInt() until plot.bottom.toInt() - 4).count { y ->
        val p = image.getPixel(x, y)
        android.graphics.Color.green(p) > android.graphics.Color.red(p) + 40 && android.graphics.Color.green(p) > android.graphics.Color.blue(p) + 20
      }
      assertTrue("la linea verticale e' verde: $greens pixel", greens > 10)
    }
    // Dal 10 ott (6,5) al 24 ott (5): -1,5, cioe' -23%.
    hold(2, 3) {
      val text = capsuleText("−1,5 (−23%)")
      assertTrue("la capsula dice la discesa", text != null)
      val color = text!!.spanStyles.single().item.color.toArgb()
      assertTrue("rosso: ${Integer.toHexString(color)}", android.graphics.Color.red(color) > android.graphics.Color.green(color) + 40)
      save("due-dita-scende-chiaro", snapshot())
    }
  }

  @Test
  fun `il piano del grafico non si ridisegna mentre le dita si muovono, si rifa' solo la maschera`() {
    show()
    val a = slot(2).center
    val b = slot(6).center
    finger.downAll(listOf(a, b))
    advance(400)
    snapshot()
    FluidChartDiagnostics.resetForTesting()
    // Le due dita si allargano di un punto per parte, due volte.
    finger.moveAll(listOf(slot(1).center, slot(7).center))
    advance(100)
    snapshot()
    finger.moveAll(listOf(slot(0).center, slot(8).center))
    advance(100)
    snapshot()
    assertEquals("il piano non si ricompone", 0, FluidChartDiagnostics.plotCompositions)
    assertEquals("il piano non si ridisegna", 0, FluidChartDiagnostics.plotDraws)
    assertTrue("il livello dello scrubber si', una volta per punto: ${FluidChartDiagnostics.scrubDraws}", FluidChartDiagnostics.scrubDraws in 1..6)
    finger.upAll(listOf(slot(0).center, slot(8).center))
    advance(500)
  }

  @Test
  fun `con Band resta la fascia, senza sbiadire e senza colore del segno`() {
    show(highlight = FluidChartRangeHighlight.Band)
    val before = snapshot()
    val plot = slot(0)
    val lower = plot.top + plot.height / 2f
    val background = before.getPixel((chart().left + 4f).toInt(), (chart().top + 4f).toInt())
    val outside = Rect(slot(0).left, lower, slot(2).center.x - 8f, plot.bottom)
    val inside = Rect(slot(2).center.x + 8f, lower, slot(6).center.x - 8f, plot.bottom)
    hold(2, 6) {
      val during = snapshot()
      val outBefore = ink(before, outside, background)
      val outDuring = ink(during, outside, background)
      assertTrue("fuori non sbiadisce: $outDuring su $outBefore", outDuring > outBefore * 0.9)
      assertTrue("dentro c'e' la fascia", ink(during, inside, background) > ink(before, inside, background) * 1.1)
      val text = capsuleText("+1 (+15%)")
      assertTrue(text != null && text.spanStyles.isEmpty())
      save("due-dita-fascia-chiaro", during)
    }
  }

  @Test
  fun `i fermi in scuro, per la tavola`() {
    show(dark = true)
    hold(1, 10) { save("due-dita-sale-scuro", snapshot()) }
    val one = slot(5).center
    finger.down(one)
    advance(500)
    save("un-dito-scuro", snapshot())
    finger.up(one)
    advance(500)
  }
}
