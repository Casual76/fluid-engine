package dev.antigravity.fluidengine.sample.fluid.viewer

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.navigation.*
import java.io.File
import org.robolectric.Shadows.shadowOf
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6C.2 sul runtime Android: i preset immagine, testo e sconosciuto dentro il telaio, e FluidImageViewer di prima con il
 * suo pager e la sua chiusura. Il dito vero (un puntatore, due), il dispatcher del back, la semantica.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FluidViewerPresetsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun themed(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        if (reduced) CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f), content = content)
        else content()
      }
    }
    compose.waitForIdle()
  }

  private fun back() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private fun zoomPercent(): Int = compose.onNode(SemanticsMatcher("ha uno stato di zoom") {
    it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Zoom ") == true
  }).fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix("%").toInt()

  /** Un trascinamento lento verso il basso di [dp] (mdpi: un pixel per dp), senza spinta. */
  private fun TouchInjectionScope.slowPullDown(dp: Float) {
    down(center)
    val steps = (dp / 10f).toInt()
    repeat(steps) { moveBy(Offset(0f, 10f), delayMillis = 40) }
    advanceEventTime(160)
    up()
  }

  // --- FluidImageViewer di oggi, sulle pagine del preset ------------------------------------------------------------

  @Test fun todaysImageViewerClosesOver110DpOnlyAtScaleOneAndPagesAfterAZoom() {
    var dismissed = 0
    val pages = mutableListOf<Int>()
    themed {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          FluidImageViewer(visible = true, onDismissRequest = { dismissed++ }, pageCount = 3,
            onPageChange = { pages += it }) { index -> Box(Modifier.fillMaxSize().testTag("page-$index")) }
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
    // Ingrandita, il dito verticale sposta l'immagine e non chiude, nemmeno oltre i 110 dp.
    compose.onNodeWithTag("page-0").performTouchInput { doubleClick() }
    compose.waitForIdle()
    assertEquals(250, zoomPercent())
    compose.onNodeWithTag("page-0").performTouchInput { slowPullDown(200f) }
    compose.waitForIdle()
    assertEquals(0, dismissed)
    // Di nuovo a scala 1 il pager riprende il dito laterale, e la pagina lasciata torna a 100%.
    compose.onNodeWithTag("page-0").performTouchInput { doubleClick() }
    compose.waitForIdle()
    compose.onNodeWithTag("page-0").performTouchInput { swipeLeft() }
    compose.waitForIdle()
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    assertTrue(1 in pages)
    assertEquals(100, zoomPercent())
    // Sotto i 110 dp torna al suo posto; oltre, chiede di chiudere.
    compose.onNodeWithTag("page-1").performTouchInput { slowPullDown(60f) }
    compose.waitForIdle()
    assertEquals(0, dismissed)
    compose.onNodeWithTag("page-1").performTouchInput { slowPullDown(160f) }
    compose.waitForIdle()
    assertEquals(1, dismissed)
  }

  @Test fun theImagePageTellsItsContentTheZoomAndWhetherItIsAtRest() {
    themed {
      FluidFileViewer(2, content = { i ->
        FluidViewerContent.Image(1f) {
          val zoom = currentFluidViewerZoom()
          val active = currentFluidViewerPageActive()
          Box(Modifier.fillMaxSize().testTag("image-$i")) {
            Text("scala ${zoom?.scale} attiva $active")
          }
        }
      }, onBack = {})
    }
    compose.onNodeWithText("scala 1.0 attiva true").assertExists()
    compose.onNodeWithTag("image-0").performTouchInput { doubleClick() }
    compose.waitForIdle()
    compose.onNodeWithText("scala 2.5 attiva true").assertExists()
  }

  // --- Le immagini animate ------------------------------------------------------------------------------------------

  private class Gif : ColorDrawable(0xFF336699.toInt()), Animatable {
    var running = false
    var starts = 0
    override fun start() {
      running = true
      starts++
    }
    override fun stop() {
      running = false
    }
    override fun isRunning() = running
  }

  @Test fun anAnimatedImagePlaysOnTheSettledPageAndStopsWhenLeft() {
    val gifs = listOf(Gif(), Gif())
    themed {
      FluidFileViewer(2, content = { i -> fluidAnimatedImage(gifs[i], description = "Animazione $i") }, onBack = {})
    }
    compose.runOnIdle {
      assertTrue(gifs[0].running)
      assertFalse(gifs[1].running)
    }
    compose.onNodeWithContentDescription("Animazione 0").performTouchInput { swipeLeft() }
    compose.waitForIdle()
    compose.runOnIdle {
      assertFalse(gifs[0].running)
      assertTrue(gifs[1].running)
    }
  }

  @Test fun withReducedMotionAnAnimationWaitsForTalkBack() {
    val gif = Gif()
    themed(reduced = true) {
      FluidFileViewer(1, content = { fluidAnimatedImage(gif, description = "Animazione") }, onBack = {})
    }
    compose.runOnIdle { assertEquals(0, gif.starts) }
    val node = compose.onNodeWithContentDescription("Animazione")
    val play = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Riproduci animazione" }
    compose.runOnUiThread { play.action() }
    compose.waitForIdle()
    compose.runOnIdle { assertTrue(gif.running) }
    val pause = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Metti in pausa l'animazione" }
    compose.runOnUiThread { pause.action() }
    compose.waitForIdle()
    compose.runOnIdle { assertFalse(gif.running) }
  }

  // --- Testo e codice -----------------------------------------------------------------------------------------------

  /** Un file di [total] righe generate, che conta le righe chieste. */
  private class Lines(val total: Int, val line: (Int) -> String = { "riga ${it + 1}: testo di prova" }) : FluidTextSource {
    var requested = 0
    var reads = 0
    override suspend fun readLines(start: Int, count: Int): List<String> {
      reads++
      requested += count
      return (start until minOf(total, start + count)).map(line)
    }
  }

  @Test fun aBigTextFileShowsAtOnceAndIsReadInChunksWhileScrolling() {
    val source = Lines(500_000)
    themed { FluidFileViewer(1, content = { FluidViewerContent.Text(source) }, onBack = {}) }
    compose.onNodeWithText("riga 1: testo di prova").assertIsDisplayed()
    val first = source.requested
    assertTrue("lette $first righe per la prima schermata", first in 1..2 * FluidTextPresetDefaults.ChunkLines)
    compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(first - 1)
    compose.waitForIdle()
    assertTrue(source.requested > first)
    assertTrue("lette ${source.requested} righe di 500.000", source.requested <= 4 * FluidTextPresetDefaults.ChunkLines)
    // I numeri di riga non si leggono con TalkBack: il testo sì.
    compose.onAllNodesWithText("1", useUnmergedTree = true).assertCountEquals(0)
  }

  private val Lake = Lines(3_000) { i ->
    when (i) {
      9 -> "Il lago Tahoe sta fra la California e il Nevada"
      1_499 -> "D'inverno il LAGO è freddo"
      2_899 -> "e d'estate il làgo è tiepido"
      else -> "riga ${i + 1}"
    }
  }

  /** Le occorrenze si calcolano fuori dal thread principale: si aspetta il contatore che le dice. */
  private fun waitForDescription(text: String) {
    compose.waitUntil(timeoutMillis = 10_000) {
      compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
    }
    compose.waitForIdle()
  }

  @Test fun findCountsWalksToTheResultAndBackClosesItFirst() {
    themed { FluidFileViewer(1, content = { FluidViewerContent.Text(Lake) }, onBack = {}) }
    compose.onNodeWithContentDescription("Trova").performClick()
    compose.waitForIdle()
    val field = compose.onNode(hasSetTextAction())
    field.performTextInput("lago")
    waitForDescription("1 di 3")
    compose.onNodeWithText("Il lago Tahoe sta fra la California e il Nevada").assertIsDisplayed()
    // L'invio passa alla successiva, che il preset porta in vista dopo averla letta.
    field.performImeAction()
    waitForDescription("2 di 3")
    compose.onNodeWithText("D'inverno il LAGO è freddo").assertIsDisplayed()
    val current = compose.onNodeWithText("D'inverno il LAGO è freddo").fetchSemanticsNode()
      .config[SemanticsProperties.Text].single()
    assertTrue(current.spanStyles.any { it.item.background == FluidFindDefaults.CurrentHighlight && it.start == 13 && it.end == 17 })
    // Nessun risultato.
    field.performTextClearance()
    field.performTextInput("zzz")
    waitForDescription("Nessun risultato")
    // Indietro chiude prima «trova»; il visore resta.
    back()
    back()
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
    compose.onNodeWithContentDescription("Trova").assertExists()
  }

  @Test fun wrapTurnsALongLineIntoSeveralLinesAndBack() {
    val long = "x".repeat(400)
    themed {
      FluidFileViewer(1, content = { FluidViewerContent.Text(Lines(3) { if (it == 1) long else "breve" }) }, onBack = {})
    }
    fun height() = compose.onNodeWithText(long).fetchSemanticsNode().boundsInRoot.height
    // Un testo senza lingua va a capo di serie; spento, la riga lunga è una riga sola che scorre di lato.
    val wrapped = height()
    compose.onNodeWithContentDescription("A capo").performClick()
    compose.waitForIdle()
    val single = height()
    assertTrue("a capo $wrapped, senza $single", wrapped > single * 3)
    compose.onNodeWithContentDescription("A capo").performClick()
    compose.waitForIdle()
    assertEquals(wrapped, height(), 0.5f)
  }

  @Test fun codeDoesNotWrapByDefault() {
    val long = "y".repeat(400)
    val code = object : FluidTextSource {
      override val language = "kotlin"
      override suspend fun readLines(start: Int, count: Int) = if (start == 0) listOf("val a = 1", long) else emptyList()
    }
    themed { FluidFileViewer(1, content = { FluidViewerContent.Text(code) }, onBack = {}) }
    val one = compose.onNodeWithText("val a = 1").fetchSemanticsNode().boundsInRoot.height
    assertEquals(one, compose.onNodeWithText(long).fetchSemanticsNode().boundsInRoot.height, 0.5f)
  }

  @Test fun anEmptyFileSaysSo() {
    themed { FluidFileViewer(1, content = { FluidViewerContent.Text(Lines(0)) }, onBack = {}) }
    compose.onNodeWithText("Il file è vuoto").assertIsDisplayed()
  }

  @Test fun aFailedReadSaysSoAndRetryReadsAgain() {
    var fail = true
    val flaky = object : FluidTextSource {
      override suspend fun readLines(start: Int, count: Int): List<String> {
        if (fail) error("disco staccato")
        return if (start == 0) listOf("ritrovato") else emptyList()
      }
    }
    themed { FluidFileViewer(1, content = { FluidViewerContent.Text(flaky) }, onBack = {}) }
    compose.onNodeWithText("Impossibile leggere il file").assertIsDisplayed()
    fail = false
    compose.onNodeWithText("Riprova").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("ritrovato").assertIsDisplayed()
  }

  // --- Lo sconosciuto -----------------------------------------------------------------------------------------------

  @Test fun anExecutableWarnsKeepsItsExtensionAndAFailedOpenIsNotADeadEnd() {
    var shared = 0
    var backs = 0
    val name = "aggiornamento-di-sistema-importantissimo-da-installare-subito-versione-finale-davvero.apk"
    themed {
      FluidFileViewer(1, content = {
        FluidViewerContent.Unknown(FluidFileInfo(name, "application/octet-stream", 25_300_000),
          openWith = { throw ActivityNotFoundException("nessuna app") }, share = { shared++ })
      }, onBack = { backs++ })
    }
    compose.onNodeWithText("È un'app o un programma: aprilo solo se ti fidi di chi te l'ha mandato.").assertIsDisplayed()
    val shown = compose.onNode(SemanticsMatcher("il nome") {
      it.config.getOrNull(SemanticsProperties.Heading) != null
    }).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    assertTrue(shown, shown.endsWith(".apk"))
    assertTrue(shown, shown.length < name.length && "…" in shown)
    compose.onNodeWithText("Eseguibile o pacchetto, attenzione", substring = true).assertIsDisplayed()
    compose.onNodeWithText("MB", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Apri con…").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Nessuna app può aprire questo file").assertIsDisplayed()
    compose.onNodeWithText("Condividi").performClick()
    assertEquals(1, shared)
    // C'è sempre un'uscita.
    compose.onNodeWithContentDescription("Indietro").performClick()
    assertEquals(1, backs)
    // Il dump esadecimale non è di serie.
    compose.onNodeWithText("Mostra i primi byte").assertDoesNotExist()
  }

  @Test fun withoutAppActionsTheCardStillSaysWhatTheFileIsAndCanShowItsBytes() {
    themed {
      FluidFileViewer(1, content = { FluidViewerContent.Unknown(FluidFileInfo("dati.bin")) }, onBack = {},
        renderContent = { file, _ ->
          FluidUnknownPreset(file as FluidViewerContent.Unknown, firstBytes = { "PK\u0003\u0004ciao".toByteArray(Charsets.ISO_8859_1) })
        })
    }
    compose.onNodeWithText("dati.bin").assertIsDisplayed()
    compose.onNodeWithText("Dimensione sconosciuta", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Nessuna anteprima per questo tipo di file").assertIsDisplayed()
    compose.onNodeWithText("Mostra i primi byte").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("00000000  50 4B 03 04 63 69 61 6F  PK..ciao").assertIsDisplayed()
  }

  // --- Dentro la rotta: la barra dell'host disegna «trova» --------------------------------------------------------

  private lateinit var nav: NavHostController

  /** Una lista con un file per tipo e la rotta del visore, come la userebbe un'app (F6C.1). */
  @Composable
  private fun Files(dark: Boolean = false, files: List<FluidViewerContent>, tile: @Composable (Int, Modifier) -> Unit) {
    FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
      nav = rememberFluidNavController()
      val ns = rememberFluidGlassNamespace()
      val modal = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides modal) {
        Box(Modifier.fillMaxSize()) {
          FluidNavHost(nav, "files") {
            composable("files") {
              Column(Modifier.fillMaxSize().padding(top = 60.dp, start = 16.dp, end = 16.dp)) {
                Text("File", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(20.dp))
                files.indices.forEach { i ->
                  tile(i, Modifier.testTag("tile-$i").fluidViewerThumbnail(FluidViewerThumbnail(i, ns),
                    ContinuousCornerShape(FluidRadius.Card)))
                  Spacer(Modifier.height(12.dp))
                }
              }
            }
            fluidFileViewerRoute("file/{index}", listOf(navArgument("index") { type = NavType.IntType }),
              initial = { it.arguments!!.getInt("index") }, thumbnail = { _, i -> FluidViewerThumbnail(i, ns) }) {
              FluidFileViewer(files.size, content = { files[it] }, onBack = { nav.popBackStack() })
            }
          }
          FluidGlassModalHost(modal, backdrop)
        }
      }
    }
  }

  @Test fun findWorksInsideTheRouteWhereTheHostDrawsTheBar() {
    compose.setContent {
      Files(files = listOf(FluidViewerContent.Text(Lake))) { i, modifier ->
        FluidFileTile("lago.txt", onClick = { nav.navigate("file/$i") }, modifier = modifier, mimeType = "text/plain")
      }
    }
    compose.onNodeWithTag("tile-0").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("1 di 1").assertIsDisplayed()
    compose.onNodeWithContentDescription("Trova").performClick()
    compose.waitForIdle()
    compose.onNode(hasSetTextAction()).performTextInput("lago")
    waitForDescription("1 di 3")
    // Indietro chiude prima la tastiera (se il campo la tiene) e poi «trova»: il visore resta.
    back()
    if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) back()
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
    assertEquals("file/{index}", nav.currentDestination?.route)
    back()
    assertEquals("files", nav.currentDestination?.route)
  }

  // --- I fotogrammi per Alessio: il componente vero, fotogramma per fotogramma a 60 Hz ---------------------------

  private val stills = System.getenv("GLASS_F6C2_FRAMES")?.let(::File)
  private var still = 0

  private fun shot(folder: File?) {
    if (folder == null) return
    folder.mkdirs()
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
      var result = PixelCopy.ERROR_UNKNOWN
      compose.runOnIdle {
        PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap, { result = it },
          Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PixelCopy.SUCCESS, result)
      File(folder, "%04d.png".format(still++)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
      bitmap.recycle()
    }
  }

  /** [count] fotogrammi da 16 ms, ognuno catturato se la variabile lo chiede. */
  private fun film(folder: File?, count: Int) = repeat(count) {
    compose.mainClock.advanceTimeByFrame()
    shot(folder)
  }

  private val plate by lazy { System.getenv("GLASS_F6C2_PHOTO")?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }

  @Composable private fun Photo(modifier: Modifier) {
    val picture = plate
    if (picture != null) Image(picture, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Color(0xFF8A6A48)))
  }

  @Test fun framesPhotoZoomAndClose() {
    val folder = stills?.let { File(it, "foto") }
    compose.setContent {
      Files(dark = true, files = listOf(FluidViewerContent.Image(1.5f) { Photo(Modifier.fillMaxSize().testTag("foto")) })) { i, modifier ->
        Photo(modifier.size(150.dp, 100.dp).clickable { nav.navigate("file/$i") })
      }
    }
    compose.mainClock.autoAdvance = false
    film(folder, 12)
    compose.onNodeWithTag("tile-0").performClick()
    film(folder, 50)
    compose.onNodeWithTag("foto").performTouchInput { doubleClick(center + Offset(60f, -30f)) }
    film(folder, 45)
    compose.onNodeWithTag("foto").performTouchInput { doubleClick(center) }
    film(folder, 45)
    back()
    film(folder, 50)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("files", nav.currentDestination?.route)
  }

  @Test fun framesTextFind() {
    val folder = stills?.let { File(it, "testo") }
    compose.setContent {
      Files(files = listOf(FluidViewerContent.Text(Lake))) { i, modifier ->
        FluidFileTile("lago.txt", onClick = { nav.navigate("file/$i") }, modifier = modifier, mimeType = "text/plain",
          sizeBytes = 48_200)
      }
    }
    compose.mainClock.autoAdvance = false
    film(folder, 12)
    compose.onNodeWithTag("tile-0").performClick()
    film(folder, 45)
    compose.onNodeWithContentDescription("Trova").performClick()
    film(folder, 30)
    compose.onNode(hasSetTextAction()).performTextInput("lago")
    // Le occorrenze arrivano da un altro thread: l'attesa lascia correre l'orologio, poi si riprende a filmare.
    compose.mainClock.autoAdvance = true
    compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("1 di 3").fetchSemanticsNodes().isNotEmpty() }
    compose.mainClock.autoAdvance = false
    film(folder, 45)
    compose.onNode(hasSetTextAction()).performImeAction()
    film(folder, 50)
    compose.onNode(hasSetTextAction()).performImeAction()
    film(folder, 50)
    back()
    if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) back()
    film(folder, 30)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
    assertEquals("file/{index}", nav.currentDestination?.route)
  }

  @Test fun framesUnknownFile() {
    val folder = stills?.let { File(it, "sconosciuto") }
    val info = FluidFileInfo("aggiornamento-sistema.apk", "application/octet-stream", 25_300_000)
    compose.setContent {
      Files(files = listOf(FluidViewerContent.Unknown(info, openWith = { throw ActivityNotFoundException() }, share = {}))) { i, modifier ->
        FluidFileTile(info.name, onClick = { nav.navigate("file/$i") }, modifier = modifier, mimeType = info.mimeType,
          sizeBytes = info.sizeBytes)
      }
    }
    compose.mainClock.autoAdvance = false
    film(folder, 12)
    compose.onNodeWithTag("tile-0").performClick()
    film(folder, 50)
    compose.onNodeWithText("Apri con…").performClick()
    film(folder, 45)
    back()
    film(folder, 50)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("files", nav.currentDestination?.route)
  }
}
