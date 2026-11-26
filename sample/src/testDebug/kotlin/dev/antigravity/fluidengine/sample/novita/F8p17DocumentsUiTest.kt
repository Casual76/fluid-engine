package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.scan.FluidScanTags
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerExtensions
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveText
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextTags
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedBlock
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedLine
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedText
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedWord
import dev.antigravity.fluidengine.viewer.ocr.FluidTextQuad
import dev.antigravity.fluidengine.viewer.ocr.FluidTextRecognizer
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
 * F8.17 · Le scene di «Documenti» nella galleria: lo scanner con la fotocamera finta e le foto degli asset (il foglio
 * scattato da solo arriva nella pila), e «Leggi testo» che la galleria riceve da engine-viewer-all senza codice (l'estensione
 * trovata con ServiceLoader). Il riconoscitore qui è finto: le librerie native di Tesseract per il computer stanno solo
 * nelle prove di engine-viewer-ocr.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p17DocumentsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val original = FluidLiveText.recognizer

  @Before fun fake() {
    FluidLiveText.clearCache()
    FluidLiveText.recognizer = {
      object : FluidTextRecognizer {
        override val available = true
        override suspend fun recognize(image: Bitmap): FluidRecognizedText {
          val word = FluidRecognizedWord("Messaggi", FluidTextQuad.of(Rect(0.05f, 0.05f, 0.4f, 0.08f)), 0.9f)
          val line = FluidRecognizedLine(listOf(word), word.quad)
          return FluidRecognizedText(listOf(FluidRecognizedBlock(listOf(line), word.quad)), image.width, image.height)
        }
      }
    }
  }

  @After fun restore() {
    FluidLiveText.recognizer = original
    FluidLiveText.clearCache()
  }

  private fun pump(ms: Long = 66) {
    compose.mainClock.advanceTimeBy(ms)
    Thread.sleep(4)
    shadowOf(Looper.getMainLooper()).idle()
    compose.waitForIdle()
  }

  private fun count(tag: String): Int =
    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag), useUnmergedTree = true).fetchSemanticsNodes().size

  @Test fun laSezioneHaLeQuattroScene() {
    val section = NewElementSections.single { it.pack == "F8.17" }
    assertEquals(listOf("scanner", "scanner-fila", "leggi-testo", "leggi-testo-pdf"), section.scenes.map { it.id })
    assertTrue("le voci da provare", F8p17TryItems.size == 4)
    assertTrue("«Leggi testo» si trova da solo", FluidViewerExtensions.extensions.any { it.id == "ocr" })
  }

  @Test fun loScannerScattaIlFoglioDegliAsset() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { NewElementsPageAt(onBack = {}, initial = "scanner") }
    }
    var done = false
    repeat(300) {
      if (!done) {
        pump()
        done = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 pagina"), useUnmergedTree = true)
          .fetchSemanticsNodes().isNotEmpty()
      }
    }
    assertTrue("la pila dice «1 pagina»", done)
    assertEquals(1, count(FluidScanTags.Thumbnail))
  }

  /**
   * La scena «Leggi testo» apre il visore coi file della galleria e con l'estensione «ocr» attiva. La lettura vera (la
   * foto ridecodificata, Tesseract, il tasto) la provano LiveTextExtensionTest e LiveTextViewerUiTest di engine-viewer-ocr:
   * in Robolectric il visore non decodifica le foto da file://, e la scena le mostrerebbe come «sconosciuto».
   */
  @Test fun leggiTestoApreIlVisoreConLEstensione() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { NewElementsPageAt(onBack = {}, initial = "leggi-testo") }
    }
    var shown = false
    val deadline = System.currentTimeMillis() + 30_000
    while (!shown && System.currentTimeMillis() < deadline) {
      pump(100)
      Thread.sleep(20)
      shown = count(F8p17Tags.Result) > 0
    }
    assertTrue("il visore della scena", shown)
    assertTrue("«Leggi testo» c'è", FluidViewerExtensions.extensions.any { it.id == "ocr" })
  }
}
