package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerBackground
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextOverlay
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextState
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedBlock
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedLine
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedText
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedWord
import dev.antigravity.fluidengine.viewer.ocr.FluidTextMatch
import dev.antigravity.fluidengine.viewer.ocr.FluidTextQuad
import java.io.File
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.floor
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
 * F7.33 · Il vetro del tema scuro sopra un contenuto chiaro (il difetto di R1-B sul Tab S9, `tablet-zoom-sinistra-scuro.png`):
 * sopra la carta bianca di una foto il menu della selezione di «Leggi testo» aveva le scritte bianche su un grigio
 * lattiginoso (188 su 255, 1,7:1), e il titolo e i tasti della barra del visore restavano scuri con l'inchiostro chiaro
 * (il titolo a 2,4:1). Apple (WWDC25 219, HIG «Liquid Glass color»): i vetri piccoli, barre e controlli, passano fra chiaro e scuro
 * secondo il contenuto sotto, e simboli e scritte li seguono, più scuri sopra il chiaro.
 *
 * Il vetro vero (`accept = true`) e un lettore della sonda finto che vede lo sfondo che la prova disegna ([under]): la
 * scena è bianca dappertutto, quindi la lettura dice il vero. Si misura il contrasto di ogni scritta e di ogni simbolo
 * contro il suo fondo **vero** nei pixel (la mediana del riquadro è il fondo, l'1% più lontano l'inchiostro): 4,5:1 per
 * le scritte, 3:1 per i simboli, come WCAG AA e la guida dell'accessibilità di Apple. I PNG e le misure vanno in
 * `build/reports/f7-33/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class F7p33LightContentTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f7-33")
  /** Lo sfondo sotto i vetri, per il lettore finto della sonda: quello che la scena disegna. */
  @Volatile private var under: Color = Color.White
  private var diagnosticsWere = false
  private var warmUpWas = true

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    warmUpWas = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidGlassProbeDiagnostics.readerForTesting = { under }
    compose.mainClock.autoAdvance = false
    out.mkdirs()
  }

  @After fun stop() {
    FluidGlassDiagnostics.warmUpEnabled = warmUpWas
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val view = window.decorView
    // Due PixelCopy: la prima può essere il fotogramma in cui il vetro aspetta la sua cattura.
    var image: Bitmap? = null
    repeat(2) {
      image?.recycle()
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      var result = -1
      PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
      image = bitmap
      advance(48)
    }
    val shot = image!!
    File(out, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return shot
  }

  /** Il contrasto misurato di un elemento: [ink] e [ground] sono luminanze relative (0..1). */
  private data class Reading(val name: String, val ratio: Float, val ink: Float, val ground: Float, val needed: Float) {
    val passes get() = ratio >= needed
    val lightInk get() = ink > ground
    override fun toString() = "$name ${"%.2f".format(ratio)}:1 (inchiostro ${"%.3f".format(ink)}, fondo " +
      "${"%.3f".format(ground)}, serve ${needed}:1)"
  }

  /**
   * Il contrasto nei pixel del riquadro [bounds] (pixel della finestra), ristretto di [inset] per lato (la parte che sta
   * sul vetro, non gli angoli fuori dalla capsula): la mediana è il fondo, il percentile 1 o 99 più lontano l'inchiostro.
   */
  private fun reading(shot: Bitmap, name: String, bounds: Rect, needed: Float, inset: Float = 0f): Reading {
    val dx = bounds.width * inset
    val dy = bounds.height * inset
    val left = ceil(bounds.left + dx).toInt().coerceAtLeast(0)
    val right = floor(bounds.right - dx).toInt().coerceAtMost(shot.width)
    val top = ceil(bounds.top + dy).toInt().coerceAtLeast(0)
    val bottom = floor(bounds.bottom - dy).toInt().coerceAtMost(shot.height)
    require(right > left && bottom > top) { "$name fuori dalla finestra: $bounds" }
    val pixels = IntArray((right - left) * (bottom - top))
    shot.getPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
    val values = FloatArray(pixels.size) { Color(pixels[it]).luminance() }.apply { sort() }
    val ground = values[values.size / 2]
    val low = values[values.size / 100]
    val high = values[values.lastIndex - values.size / 100]
    val light = (high + .05f) / (ground + .05f)
    val dark = (ground + .05f) / (low + .05f)
    return if (light > dark) Reading(name, light, high, ground, needed) else Reading(name, dark, low, ground, needed)
  }

  private fun SemanticsNodeInteraction.window(): Rect = fetchSemanticsNode().boundsInWindow

  /** Una parola riconosciuta a metà della foto, scelta: sopra compare il menu della selezione. */
  private fun selectedWord(): FluidLiveTextState {
    val rect = Rect(0.3f, 0.48f, 0.7f, 0.52f)
    val text = FluidRecognizedText(
      listOf(FluidRecognizedBlock(listOf(FluidRecognizedLine(listOf(FluidRecognizedWord("Cordiali", FluidTextQuad.of(rect), 0.95f)),
        FluidTextQuad.of(rect))), FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), 100, 100,
    )
    return FluidLiveTextState(411f / 891f).also {
      it.show(text)
      it.selection = FluidTextMatch(0, 0)
    }
  }

  /**
   * Il visore su un contenuto bianco che riempie lo schermo: una foto ([photo], col fondo nero e la chrome del lato
   * scuro dei visori) con la selezione di «Leggi testo», o una pagina del tema (la carta bianca di un documento).
   */
  private fun viewer(dark: Boolean, photo: Boolean, name: String): Pair<Bitmap, List<Reading>> {
    under = Color.White
    val live = if (photo) selectedWord() else null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val content = remember {
          if (photo) FluidViewerContent.Image(411f / 891f) {
            Box(Modifier.fillMaxSize().background(Color.White)) { FluidLiveTextOverlay(live!!) }
          } else FluidViewerContent.Custom(FluidViewerBackground.Theme) {
            Box(Modifier.fillMaxSize().background(Color.White))
          }
        }
        val info = remember { FluidViewerInfo(FluidFileInfo(if (photo) "foto-lettera.jpg" else "lettera.pdf")) }
        FluidViewerInfoProvider({ info }) {
          FluidFileViewer(1, content = { content }, onBack = {})
        }
      }
    }
    // La prima lettura, il giro (700 ms coi tempi di Apple, F7.33b) e il filtro: due secondi bastano.
    advance(2_000)
    val shot = capture(name)
    val readings = mutableListOf<Reading>()
    readings += reading(shot, "indietro", compose.onNodeWithContentDescription("Indietro", useUnmergedTree = true).window(), 3f, 0.22f)
    readings += reading(shot, "informazioni", compose.onNodeWithContentDescription("Informazioni", useUnmergedTree = true).window(), 3f, 0.22f)
    readings += reading(shot, "titolo", compose.onNodeWithText(if (photo) "foto-lettera" else "lettera", useUnmergedTree = true).window(), 4.5f)
    if (photo) {
      for (label in listOf("Copia", "Seleziona tutto", "Condividi")) {
        readings += reading(shot, "menu $label", compose.onNodeWithText(label, useUnmergedTree = true).window(), 4.5f)
      }
    }
    File(out, "$name.txt").writeText(readings.joinToString("\n"))
    return shot to readings
  }

  private fun assertReadable(name: String, readings: List<Reading>) {
    val failing = readings.filterNot { it.passes }
    assertTrue("$name: ${failing.joinToString("; ")} — tutte: ${readings.joinToString("; ")}", failing.isEmpty())
  }

  @Test fun fotoChiaraInScuro() {
    val (_, readings) = viewer(dark = true, photo = true, name = "foto-scuro")
    assertReadable("foto chiara, tema scuro", readings)
    // Sopra il chiaro l'inchiostro è scuro (il vetro è passato al chiaro), come in Apple.
    for (r in readings) assertTrue("${r.name}: l'inchiostro deve essere scuro sopra la carta, è $r", !r.lightInk)
  }

  @Test fun paginaBiancaInScuro() {
    val (_, readings) = viewer(dark = true, photo = false, name = "pagina-scuro")
    assertReadable("pagina bianca, tema scuro", readings)
    for (r in readings) assertTrue("${r.name}: l'inchiostro deve essere scuro sopra la carta, è $r", !r.lightInk)
  }

  /**
   * La ripresa di R1-B: la foto della lettera, più bassa dello schermo, ha il nero del visore sopra e sotto. La barra
   * legge il nero e resta scura; poi si ingrandisce, la carta passa sotto la barra, e la barra deve rileggere e girare.
   * Lo zoom cambia solo lo strato della foto: senza un avviso la sorgente non si registra di nuovo e la sonda non rilegge.
   */
  @Test fun fotoIngranditaSottoLaBarra() {
    under = Color.Black
    var zoom: dev.antigravity.fluidengine.ui.fluid.FluidZoomState? = null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK, dynamicColorEnabled = false) }) {
        val content = remember {
          FluidViewerContent.Image(0.75f) {
            zoom = dev.antigravity.fluidengine.ui.fluid.viewer.currentFluidViewerZoom()
            Box(Modifier.fillMaxSize().background(Color.White))
          }
        }
        val info = remember { FluidViewerInfo(FluidFileInfo("foto-lettera.jpg")) }
        FluidViewerInfoProvider({ info }) { FluidFileViewer(1, content = { content }, onBack = {}) }
      }
    }
    advance(2_000)
    val before = capture("zoom-prima")
    val back = compose.onNodeWithContentDescription("Indietro", useUnmergedTree = true).window()
    val resting = reading(before, "indietro a riposo", back, 3f, 0.22f)
    assertTrue("sopra il nero l'inchiostro è chiaro: $resting", resting.lightInk && resting.passes)
    val requests = FluidGlassProbeDiagnostics.totalRequests
    // Ingrandita 3×: la carta copre lo schermo, anche sotto la barra.
    under = Color.White
    compose.runOnIdle { checkNotNull(zoom) { "lo zoom della pagina" }.zoomTo(3f) }
    advance(2_500)
    val after = capture("zoom-dopo")
    val zoomed = reading(after, "indietro ingrandita", back, 3f, 0.22f)
    val title = reading(after, "titolo ingrandita", compose.onNodeWithText("foto-lettera", useUnmergedTree = true).window(), 4.5f)
    File(out, "zoom.txt").writeText("$resting\n$zoomed\n$title\nletture ${FluidGlassProbeDiagnostics.totalRequests - requests}")
    assertTrue("la sonda rilegge dopo lo zoom", FluidGlassProbeDiagnostics.totalRequests > requests)
    assertTrue("sopra la carta l'inchiostro è scuro: $zoomed; $title", !zoomed.lightInk && zoomed.passes && !title.lightInk && title.passes)
  }

  /**
   * Il giro della barra fotogramma per fotogramma, per la tavola contro Apple (WWDC25 219, B6 di R4): dallo zoom che porta
   * la carta sotto la barra, ogni 64 ms per 1,1 s (18 PixelCopy: col PC carico una costa fino a 6 s), il corpo del tasto
   * indietro, il suo simbolo e il titolo. Le immagini e `giro.csv` vanno in `build/reports/f7-33/giro/`. Si controlla solo
   * che l'inchiostro passi di lato **una volta** (dal chiaro allo scuro, mai indietro: niente lampi) e che alla fine sia
   * scuro; i tempi si giudicano sulla tavola. Ogni 32 ms (F7.33, prima consegna) il simbolo cambiava fra 192 e 224 ms:
   * ogni 64 ms cade fra due campioni in cui il lato si legge (192 e 256 ms).
   */
  @Test fun giroDellaBarraFotogrammi() {
    under = Color.Black
    var zoom: dev.antigravity.fluidengine.ui.fluid.FluidZoomState? = null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK, dynamicColorEnabled = false) }) {
        val content = remember {
          FluidViewerContent.Image(0.75f) {
            zoom = dev.antigravity.fluidengine.ui.fluid.viewer.currentFluidViewerZoom()
            Box(Modifier.fillMaxSize().background(Color.White))
          }
        }
        val info = remember { FluidViewerInfo(FluidFileInfo("foto-lettera.jpg")) }
        FluidViewerInfoProvider({ info }) { FluidFileViewer(1, content = { content }, onBack = {}) }
      }
    }
    advance(2_000)
    capture("giro-prima").recycle()
    val back = compose.onNodeWithContentDescription("Indietro", useUnmergedTree = true).window()
    val titleBox = compose.onNodeWithText("foto-lettera", useUnmergedTree = true).window()
    val dir = File(out, "giro").apply { mkdirs() }
    val rows = mutableListOf("ms,corpo,simbolo,rapporto_simbolo,titolo_inchiostro,titolo_fondo,rapporto_titolo,simbolo_chiaro")
    val sides = mutableListOf<Boolean>()
    under = Color.White
    compose.runOnIdle { checkNotNull(zoom) { "lo zoom della pagina" }.zoomTo(3f) }
    val window = compose.activity.window
    val view = window.decorView
    var ms = 0
    while (ms <= 1_088) {
      val shot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      var result = -1
      PixelCopy.request(window, shot, { result = it }, Handler(Looper.getMainLooper()))
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
      val icon = reading(shot, "indietro", back, 3f, 0.22f)
      val title = reading(shot, "titolo", titleBox, 4.5f)
      rows += "$ms,${icon.ground},${icon.ink},${icon.ratio},${title.ink},${title.ground},${title.ratio},${icon.lightInk}"
      // Nel fotogramma a contrasto quasi zero (il simbolo svanito nel corpo) il lato non si legge: si conta dove c'è.
      if (icon.ratio >= 2f) sides += icon.lightInk
      File(dir, "f%04d.png".format(ms)).outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
      shot.recycle()
      advance(64)
      ms += 64
    }
    File(dir, "giro.csv").writeText(rows.joinToString("\n"))
    val changes = sides.zipWithNext().count { (a, b) -> a != b }
    assertEquals("l'inchiostro passa di lato una volta sola: ${rows.joinToString(" | ")}", 1, changes)
    assertTrue("alla fine l'inchiostro è scuro", !sides.last())
  }

  @Test fun fotoChiaraInChiaro() {
    val (_, readings) = viewer(dark = false, photo = true, name = "foto-chiaro")
    assertReadable("foto chiara, tema chiaro", readings)
  }

  @Test fun paginaBiancaInChiaro() {
    val (_, readings) = viewer(dark = false, photo = false, name = "pagina-chiaro")
    assertReadable("pagina bianca, tema chiaro", readings)
  }
}
