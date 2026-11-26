package dev.antigravity.fluidengine.viewer.ocr

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerExtensions
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * F8.17 · Il percorso intero, come lo vede un'app: l'estensione trovata da sola, il file aperto con
 * l'estensione che decora una foto del visore, la foto ridecodificata per la lettura dal suo indirizzo, Tesseract vero,
 * il tasto di Testo attivo che compare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveTextExtensionTest {
  @get:Rule val compose = createComposeRule()

  @Test fun laSchermataApertaNelVisoreHaLeggiTesto() {
    assertTrue("l'estensione si trova da sola", FluidViewerExtensions.extensions.any { it.id == "ocr" })
    FluidLiveText.clearCache()
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val file = File(context.cacheDir, "schermata.png").apply {
      this@LiveTextExtensionTest.javaClass.classLoader!!.getResourceAsStream("ocr/schermata.png")!!.use { input -> outputStream().use { input.copyTo(it) } }
    }
    val viewerFile = FluidViewerFile(Uri.fromFile(file), file.name, "image/png", file.length())
    compose.mainClock.autoAdvance = false
    // Il contenuto di serie di una foto (quello che darebbe engine-viewer sul telefono: in Robolectric la decodifica da
    // file:// del visore non c'è), poi le aggiunte delle estensioni trovate da sole.
    val bitmap = android.graphics.BitmapFactory.decodeFile(file.path).asImageBitmap()
    val base = FluidViewerContent.Image(bitmap.width.toFloat() / bitmap.height) { Image(bitmap, null, Modifier.fillMaxSize()) }
    val decorated = FluidViewerExtensions.decorate(context, viewerFile, base)
    assertTrue("decorata", decorated !== base)
    compose.setContent {
      FluidTheme(EngineSettings()) {
        FluidFileViewer(count = 1, content = { decorated }, onBack = {})
      }
    }
    var shown = false
    val deadline = System.currentTimeMillis() + 60_000
    while (!shown && System.currentTimeMillis() < deadline) {
      compose.mainClock.advanceTimeBy(100)
      Thread.sleep(40)
      shadowOf(Looper.getMainLooper()).idle()
      compose.waitForIdle()
      shown = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, FluidLiveTextTags.Button), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue("il tasto compare dopo la lettura (errore: ${FluidLiveText.lastError}, in memoria: " +
      "${FluidLiveText.cached("${viewerFile.uri}|${viewerFile.sizeBytes}|${viewerFile.modifiedAtMillis}")?.words?.size})", shown)
  }
}
