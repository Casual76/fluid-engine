package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPageAccessory
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * F8.17b · I comandi di «Leggi testo» su un telefono stretto (360 dp, come i telefoni Android più piccoli che contano):
 * - la riga non esce dallo schermo e il tasto di Testo attivo resta intero, anche col testo al 200%; sul tablet, in
 *   formato telefono, la riga con la mail della circolare era più larga dello schermo e il tasto era una striscia;
 * - il vetro dei comandi sfoca la foto sotto: la foto qui è a righe sottili, e sotto il vetro le righe devono sparire.
 *   Il confronto è col vetro senza fondale (com'era prima: il tasto prendeva il fondale d'ambiente o nessuno), che le
 *   lascia vedere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveTextControlsPhoneTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Una riga con una mail lunga (l'azione rapida più larga che capita: quella della circolare) e due righe normali. */
  private val text: FluidRecognizedText = run {
    fun line(words: List<String>, top: Float): FluidRecognizedLine {
      var x = 0.08f
      val recognized = words.map { w ->
        val width = 0.012f * w.length
        FluidRecognizedWord(w, FluidTextQuad.of(Rect(x, top, x + width, top + 0.02f)), 0.95f).also { x += width + 0.01f }
      }
      return FluidRecognizedLine(recognized, FluidTextQuad.of(Rect(0.08f, top, x, top + 0.02f)))
    }
    val lines = listOf(
      line("Istituto Comprensivo «Galileo Galilei»".split(' '), 0.1f),
      line("Per informazioni scrivere a segreteria@icgalilei.example.it oppure telefonare".split(' '), 0.14f),
      line("Circolare n. 87 del 18 novembre 2026".split(' '), 0.18f),
    )
    FluidRecognizedText(listOf(FluidRecognizedBlock(lines, FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), 720, 1560)
  }

  /** Righe verticali bianche e nere di 2 pixel: tanta energia nelle differenze fra pixel vicini, che il vetro toglie. */
  private val stripes: Bitmap = Bitmap.createBitmap(720, 1560, Bitmap.Config.ARGB_8888).apply {
    val row = IntArray(width) { x -> if ((x / 2) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
    for (y in 0 until height) setPixels(row, 0, width, 0, y, width, 1)
  }

  /** La schermata di prova e le sue righe vere (i riquadri del generatore), per la foto della tavola. */
  private val screenshot: Pair<Bitmap, FluidRecognizedText> by lazy {
    val entry = JSONObject(javaClass.classLoader!!.getResourceAsStream("ocr/righe.json")!!.readBytes().toString(Charsets.UTF_8))
      .getJSONArray("images").let { a -> (0 until a.length()).map(a::getJSONObject).first { it.getString("name") == "schermata.png" } }
    val w = entry.getInt("width").toFloat()
    val h = entry.getInt("height").toFloat()
    val rows = entry.getJSONArray("lines")
    val lines = (0 until rows.length()).map { i ->
      val b = rows.getJSONObject(i).getJSONArray("box")
      val r = Rect(b.getInt(0) / w, b.getInt(1) / h, b.getInt(2) / w, b.getInt(3) / h)
      val words = rows.getJSONObject(i).getString("text").split(' ')
      val total = words.sumOf { it.length + 1 }.toFloat()
      var x = r.left
      FluidRecognizedLine(words.map { word ->
        val span = r.width * (word.length + 1) / total
        FluidRecognizedWord(word, FluidTextQuad.of(Rect(x, r.top, x + span * word.length / (word.length + 1), r.bottom)), 0.95f).also { x += span }
      }, FluidTextQuad.of(r))
    }
    val bitmap = javaClass.classLoader!!.getResourceAsStream("ocr/schermata.png").use { BitmapFactory.decodeStream(it) }!!
    bitmap to FluidRecognizedText(listOf(FluidRecognizedBlock(lines, FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), w.toInt(), h.toInt())
  }

  /**
   * Come si mostra la riga: acceso o spento, la scala del testo, il vetro col fondale del visore o senza, e (per la
   * foto della tavola) la schermata vera al posto delle righe sottili, nel tema chiaro o scuro.
   */
  private data class Setup(
    val active: Boolean,
    val fontScale: Float,
    val plainGlass: Boolean,
    val screenshot: Boolean = false,
    val dark: Boolean = false,
  )

  private var setup by mutableStateOf<Setup?>(null)

  /** La prima volta compone il visore; poi cambia solo [setup] (setContent si chiama una volta per prova). */
  private fun show(active: Boolean, fontScale: Float = 1f, plainGlass: Boolean = false, screenshot: Boolean = false, dark: Boolean = false) {
    val first = setup == null
    setup = Setup(active, fontScale, plainGlass, screenshot, dark)
    if (first) compose.setContent {
      val s = setup ?: return@setContent
      val density = LocalDensity.current
      val (picture, recognized) = if (s.screenshot) this@LiveTextControlsPhoneTest.screenshot else stripes to text
      CompositionLocalProvider(LocalDensity provides Density(density.density, s.fontScale)) {
        FluidTheme(EngineSettings(themeMode = if (s.dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
          FluidFileViewer(count = 1, onBack = {}, content = {
            FluidViewerContent.Image(picture.width.toFloat() / picture.height) {
              Image(remember(picture) { picture.asImageBitmap() }, null, Modifier.fillMaxSize())
              val state = remember(s) {
                FluidLiveTextState(picture.width.toFloat() / picture.height).also { it.show(recognized); it.active = s.active }
              }
              FluidLiveTextOverlay(state)
              FluidViewerPageAccessory {
                // Il vetro di prima della correzione: nessun fondale (il controllo che la misura distingue).
                if (s.plainGlass) CompositionLocalProvider(LocalGlassBackdrop provides null, LocalFluidCanvasBackdrop provides null) {
                  FluidLiveTextControls(state)
                } else FluidLiveTextControls(state)
              }
            }
          })
        }
      }
    }
    compose.mainClock.advanceTimeBy(1000)
    compose.waitForIdle()
  }

  private fun bounds(tag: String): Rect? = runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow }.getOrNull()

  private fun assertRowInside(where: String) {
    val screen = compose.activity.window.decorView.width.toFloat()
    val px = compose.activity.resources.displayMetrics.density
    val tags = listOf(FluidLiveTextTags.QuickAction, FluidLiveTextTags.ShowText, FluidLiveTextTags.CopyAll, FluidLiveTextTags.Button)
    val shown = tags.mapNotNull { tag -> bounds(tag)?.let { tag to it } }
    for ((tag, b) in shown) {
      assertTrue("$where: $tag esce a sinistra ($b)", b.left >= -0.5f)
      assertTrue("$where: $tag esce a destra ($b, schermo $screen)", b.right <= screen + 0.5f)
    }
    val button = shown.first { it.first == FluidLiveTextTags.Button }.second
    // Il tasto è intero: tondo, largo quanto è alto e almeno 40 dp.
    assertTrue("$where: il tasto è schiacciato ($button)", button.width >= 40f * px && abs(button.width - button.height) < 2f)
    // Nessun comando sopra un altro, e in fila da sinistra a destra.
    for (i in 1 until shown.size) {
      assertTrue("$where: ${shown[i - 1].first} e ${shown[i].first} si sovrappongono",
        shown[i - 1].second.right <= shown[i].second.left + 0.5f)
    }
  }

  @Test fun aTrecentosessantaDpLaRigaStaNelloSchermo() {
    show(active = true)
    assertRowInside("360 dp")
    // Con la mail, a 360 dp ci stanno l'azione rapida (coi puntini), «Testo» e il tasto; «Copia tutto» è nel foglio.
    assertTrue("l'azione rapida c'è", bounds(FluidLiveTextTags.QuickAction) != null)
    assertTrue("«Testo» c'è", bounds(FluidLiveTextTags.ShowText) != null)
    assertEquals("«Copia tutto» non c'è nella riga", null, bounds(FluidLiveTextTags.CopyAll))
    compose.onNodeWithTag(FluidLiveTextTags.ShowText).performClick()
    compose.mainClock.advanceTimeBy(1000)
    compose.onNodeWithTag(FluidLiveTextTags.Sheet).fetchSemanticsNode()
  }

  @Test fun colTestoAlDoppioIlTastoRestaIntero() {
    show(active = true, fontScale = 2f)
    assertRowInside("testo al 200%")
    show(active = false, fontScale = 2f)
    assertRowInside("testo al 200%, spento")
  }

  @Test fun ilVetroDelTastoSfocaLaFotoSotto() {
    show(active = false)
    val glass = stripeContrast()
    show(active = false, plainGlass = true)
    val plain = stripeContrast()
    println("F8.17b vetro: sotto il tasto ${"%.3f".format(glass.first)} della foto accanto (${"%.1f".format(glass.second)}); " +
      "senza fondale ${"%.3f".format(plain.first)} (${"%.1f".format(plain.second)})")
    // Misurato il 26/11/2026: 0,003 col fondale del visore, 0,542 senza (la foto accanto 88). Le soglie stanno lontane
    // da tutti e due.
    assertTrue("la foto accanto al tasto ha le righe (${plain.second})", plain.second > 40f)
    assertTrue("senza fondale le righe si vedono sotto il tasto (${plain.first})", plain.first > 0.3f)
    assertTrue("col fondale del visore le righe sotto il tasto spariscono (${glass.first})", glass.first < 0.1f)
  }

  /**
   * Non è una prova: con `FLUID_F8_17B_EVIDENCE=<cartella>` salva la schermata col tasto acceso a 360 dp, chiara e
   * scura, per la tavola accanto ad Apple. Senza la variabile non fa niente.
   */
  @Test fun fotoPerLaTavola() {
    val out = System.getenv("FLUID_F8_17B_EVIDENCE") ?: return
    for (dark in listOf(false, true)) {
      show(active = true, screenshot = true, dark = dark)
      val shot = capture()
      try {
        File(out, "leggi-testo-360dp-${if (dark) "scuro" else "chiaro"}.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
      } finally { shot.recycle() }
    }
  }

  /**
   * Il contrasto delle righe (la media delle differenze fra pixel vicini in orizzontale) in una striscia dentro il tasto,
   * a sinistra dell'icona, diviso per quello della stessa striscia un tasto più in alto, dove c'è solo la foto.
   */
  private fun stripeContrast(): Pair<Float, Float> {
    val b = bounds(FluidLiveTextTags.Button)!!
    val shot = capture()
    try {
      val x0 = (b.left + b.width * 0.12f).toInt()
      val x1 = (b.left + b.width * 0.22f).toInt()
      val yc = b.center.y.toInt()
      val half = (b.height * 0.12f).toInt()
      val under = contrast(shot, x0, x1, yc - half, yc + half)
      val above = contrast(shot, x0, x1, yc - half - (b.height * 1.5f).toInt(), yc + half - (b.height * 1.5f).toInt())
      return (under / above.coerceAtLeast(1e-3f)) to above
    } finally { shot.recycle() }
  }

  private fun contrast(bitmap: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int): Float {
    var sum = 0.0
    var n = 0
    for (y in y0 until y1) for (x in x0 until x1) {
      fun luma(c: Int) = 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)
      sum += abs(luma(bitmap.getPixel(x + 1, y)) - luma(bitmap.getPixel(x, y)))
      n++
    }
    return (sum / n.coerceAtLeast(1)).toFloat()
  }

  private fun capture(): Bitmap {
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var status = PixelCopy.ERROR_UNKNOWN
    compose.runOnIdle {
      PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap, { status = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(PixelCopy.SUCCESS, status)
    return bitmap
  }
}
