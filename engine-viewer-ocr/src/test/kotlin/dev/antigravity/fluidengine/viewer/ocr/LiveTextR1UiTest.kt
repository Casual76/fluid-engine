package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import org.junit.After
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * R1, D3 · Il disegno di «Leggi testo» ingrandito resta nitido: la selezione e le maniglie (le muove il dito) si
 * disegnano alla scala dello schermo, non in uno strato a 1× poi ingrandito. R2, n. 11 · Un file senza peso né data
 * riaperto dopo che è cambiato si rilegge, invece di mostrare il testo della foto di prima.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveTextR1UiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val previous = FluidLiveText.recognizer

  @After fun restore() {
    FluidLiveText.recognizer = previous
    FluidLiveText.clearCache()
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

  /** Il riquadro della parola che legge il riconoscitore finto. */
  private val word = Rect(0.2f, 0.3f, 0.8f, 0.7f)

  /**
   * Quanti pixel servono per passare dal bianco al colore pieno della maniglia, lungo la riga a metà della parola: uno o
   * due se il bordo è disegnato alla scala dello schermo, quattro o più se lo strato a 1× è stato ingrandito 4 volte.
   */
  private fun edgeWidth(shot: Bitmap, y: Int, x0: Int, x1: Int): Int {
    val red = (x0 until x1).map { x -> android.graphics.Color.red(shot.getPixel(x, y)) }
    val white = red.max()
    val bar = red.min()
    val step = white - bar
    assertTrue("la maniglia c'è (bianco $white, maniglia $bar)", step > 120)
    val first = red.indexOfFirst { it < white - step * 0.1f }
    val full = red.indexOfFirst { it < bar + step * 0.1f }
    return full - first
  }

  private fun oneWord(rect: Rect) = FluidRecognizedText(
    listOf(FluidRecognizedBlock(listOf(FluidRecognizedLine(listOf(FluidRecognizedWord("Fluid", FluidTextQuad.of(rect), 0.95f)),
      FluidTextQuad.of(rect))), FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), 100, 100,
  )

  @Test fun ingranditaLaSelezioneELeManiglieRestanoNitide() {
    // Una foto larga 400 dp ingrandita 4 volte: sullo schermo se ne vede l'angolo in alto a sinistra, con l'inizio
    // della parola (al 5% della larghezza) e la maniglia d'inizio.
    val state = FluidLiveTextState(1f).also { it.show(oneWord(Rect(0.05f, 0.3f, 0.95f, 0.7f))); it.selection = FluidTextMatch(0, 0) }
    compose.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          // Lo zoom del visore: uno strato che ingrandisce 4 volte la foto e il disegno sopra.
          Box(Modifier.size(400.dp).graphicsLayer { scaleX = 4f; scaleY = 4f; transformOrigin = TransformOrigin(0f, 0f) }
            .background(Color.White)) {
            FluidLiveTextOverlay(state)
          }
        }
      }
    }
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    val shot = capture()
    val density = compose.activity.resources.displayMetrics.density
    val bar = 0.05f * 400f * 4f * density
    val y = (0.5f * 400f * 4f * density).toInt()
    // Dal bianco a sinistra fino a dentro la sbarra della maniglia d'inizio (larga 8 dp sullo schermo).
    val width = edgeWidth(shot, y, (bar - 60f).toInt(), (bar + 6f).toInt())
    assertTrue("il bordo della maniglia ingrandita è largo $width pixel", width <= 2)
  }

  @Test fun ilVeloScurisceFuoriDalleRigheELeLasciaChiare() {
    // Il velo senza lo strato fuori schermo (un ritaglio al posto del BlendMode.Clear): lo stesso aspetto di prima.
    val state = FluidLiveTextState(1f).also { it.show(oneWord(Rect(0.2f, 0.3f, 0.8f, 0.7f))); it.active = true }
    compose.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(Modifier.size(300.dp).background(Color.White)) { FluidLiveTextOverlay(state) }
        }
      }
    }
    compose.mainClock.advanceTimeBy(1000)
    compose.waitForIdle()
    val shot = capture()
    val side = 300f * compose.activity.resources.displayMetrics.density
    val outside = android.graphics.Color.red(shot.getPixel((side * 0.1f).toInt(), (side * 0.5f).toInt()))
    val inside = android.graphics.Color.red(shot.getPixel((side * 0.5f).toInt(), (side * 0.5f).toInt()))
    // Fuori: il bianco sotto il nero al 45% (circa 140); dentro la riga: il bianco, appena schiarito.
    assertTrue("fuori dalle righe $outside", outside in 125..155)
    assertTrue("dentro la riga $inside", inside >= 250)
  }

  @Test fun ilMenuDellaSelezioneRestaOttoDpDentroLoSchermoAiDueBordi() {
    // Dal tablet (F8.17d, foto della lettera nel formato telefono): l'immagine larga quanto lo schermo e la parola vicino
    // al bordo; il menu arrivava a 3 px dal bordo. Come iOS resta almeno 8 dp dentro, e si sposta invece di tagliarsi.
    val left = FluidLiveTextState(1f).also { it.show(oneWord(Rect(0.01f, 0.5f, 0.09f, 0.54f))); it.selection = FluidTextMatch(0, 0) }
    val right = FluidLiveTextState(1f).also { it.show(oneWord(Rect(0.91f, 0.5f, 0.99f, 0.54f))); it.selection = FluidTextMatch(0, 0) }
    var which by mutableIntStateOf(0)
    compose.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(Modifier.fillMaxWidth().height(400.dp).background(Color.White)) {
            androidx.compose.runtime.key(which) { FluidLiveTextOverlay(if (which == 0) left else right) }
          }
        }
      }
    }
    val density = compose.activity.resources.displayMetrics.density
    for (side in 0..1) {
      compose.runOnIdle { which = side }
      compose.mainClock.advanceTimeBy(600)
      compose.waitForIdle()
      val screen = compose.activity.window.decorView.width.toFloat()
      val menu = compose.onNodeWithTag(FluidLiveTextTags.Menu).fetchSemanticsNode().boundsInRoot
      val margin = 8f * density - 1f
      if (side == 0) assertTrue("a sinistra il menu sta a ${menu.left / density} dp dal bordo", menu.left >= margin)
      else assertTrue("a destra il menu sta a ${(screen - menu.right) / density} dp dal bordo", screen - menu.right >= margin)
    }
  }

  @Test fun inUnImmagineStrettaIlMenuDellaSelezioneNonFaCadere() {
    // Uno scontrino lungo sul telefono: l'immagine è più stretta del menu «Copia, Seleziona tutto, Condividi».
    val state = FluidLiveTextState(0.25f).also { it.show(oneWord(Rect(0.1f, 0.4f, 0.9f, 0.45f))); it.selection = FluidTextMatch(0, 0) }
    compose.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(Modifier.size(80.dp, 320.dp).background(Color.White)) { FluidLiveTextOverlay(state) }
        }
      }
    }
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    compose.onNodeWithTag(FluidLiveTextTags.Menu).assertExists()
  }

  /** Chi legge per finta: conta le letture e dà una parola diversa per ogni foto (il colore del primo pixel). */
  private inner class CountingRecognizer(private val calls: AtomicInteger) : FluidTextRecognizer {
    override val available: Boolean = true
    override suspend fun recognize(image: Bitmap): FluidRecognizedText {
      calls.incrementAndGet()
      val name = if (android.graphics.Color.red(image.getPixel(0, 0)) > 128) "rosso" else "blu"
      return FluidRecognizedText(listOf(FluidRecognizedBlock(listOf(FluidRecognizedLine(
        listOf(FluidRecognizedWord(name, FluidTextQuad.of(word), 0.9f)), FluidTextQuad.of(word))), FluidTextQuad.of(word))), image.width, image.height)
    }
  }

  @Test fun unFileSenzaPesoNeDataRiapertoSiRilegge() {
    FluidLiveText.clearCache()
    val calls = AtomicInteger()
    FluidLiveText.recognizer = { CountingRecognizer(calls) }
    val context = compose.activity
    val png = File(context.cacheDir, "r2-foto.png")
    fun write(color: Int) = png.outputStream().use {
      Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    // L'app passa solo l'indirizzo e il nome: né peso né data.
    val file = FluidViewerFile(Uri.fromFile(png), "r2-foto.png", "image/png")
    val extension = FluidLiveTextExtension()
    var opened by mutableIntStateOf(0)
    var contents = listOf<FluidViewerContent.Image>()
    compose.setContent {
      FluidTheme(EngineSettings()) {
        // Ogni apertura è un contenuto nuovo, decorato dall'estensione come fa il visore.
        val content = contents.getOrNull(opened - 1)
        if (content != null) androidx.compose.runtime.key(opened) { Box(Modifier.fillMaxSize()) { content.draw() } }
      }
    }
    fun open() {
      val plain = FluidViewerContent.Image(1f) {}
      contents = contents + (extension.decorate(context, file, plain) as FluidViewerContent.Image)
      opened++
      val before = calls.get()
      repeat(200) {
        if (calls.get() > before) return
        compose.mainClock.advanceTimeBy(100)
        Thread.sleep(10)
        compose.waitForIdle()
      }
    }
    write(android.graphics.Color.RED)
    open()
    assertEquals("la prima apertura legge", 1, calls.get())
    // L'app riscrive la foto allo stesso indirizzo e la riapre.
    write(android.graphics.Color.BLUE)
    open()
    assertEquals("la foto nuova si rilegge, non si mostra il testo di prima", 2, calls.get())
  }
}
