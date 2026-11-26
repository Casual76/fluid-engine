package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.scan.FluidScanPoint
import dev.antigravity.fluidengine.scan.FluidScanQuad
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.17d · Nel visore vero, la foto storta della lettera con le righe lette dal foglio raddrizzato e riportate sulla
 * foto: tenere premuto su una parola sceglie **quella** parola, in alto, in mezzo e in fondo al foglio, dove la
 * prospettiva storce di più. Il testo qui è quello vero della lettera (i riquadri del generatore), portato sulla foto
 * con la prospettiva vera: si prova il disegno e il tocco, senza dipendere dal riconoscimento.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetReadingViewerUiTest {
  @get:Rule val compose = createComposeRule()

  private val photo = javaClass.classLoader!!.getResourceAsStream("ocr/foto-lettera.jpg").use { BitmapFactory.decodeStream(it) }!!

  /** Il foglio vero della foto (come in SheetReadingTest). */
  private val sheet = FluidScanQuad(FluidScanPoint(0.25f, 0.20625f), FluidScanPoint(0.75833f, 0.1875f),
    FluidScanPoint(0.925f, 0.89375f), FluidScanPoint(0.07917f, 0.92188f))

  /** Le righe vere della lettera, una parola per pezzo proporzionale alle lettere, portate sulla foto. */
  private val known: FluidRecognizedText = run {
    val entry = JSONObject(javaClass.classLoader!!.getResourceAsStream("ocr/righe.json")!!.readBytes().toString(Charsets.UTF_8))
      .getJSONArray("images").let { a -> (0 until a.length()).map(a::getJSONObject).first { it.getString("name") == "lettera.png" } }
    val w = entry.getInt("width").toFloat()
    val h = entry.getInt("height").toFloat()
    val rows = entry.getJSONArray("lines")
    val lines = (0 until rows.length()).map { i ->
      val b = rows.getJSONObject(i).getJSONArray("box")
      val r = Rect(b.getInt(0) / w, b.getInt(1) / h, b.getInt(2) / w, b.getInt(3) / h)
      val words = rows.getJSONObject(i).getString("text").replace(Regex("_{2,}"), " ").trim().split(Regex("\\s+"))
      val total = words.sumOf { it.length + 1 }.toFloat()
      var x = r.left
      FluidRecognizedLine(words.map { word ->
        val span = r.width * (word.length + 1) / total
        FluidRecognizedWord(word, FluidTextQuad.of(Rect(x, r.top, x + span * word.length / (word.length + 1), r.bottom)), 0.95f).also { x += span }
      }, FluidTextQuad.of(r))
    }
    FluidRecognizedText(listOf(FluidRecognizedBlock(lines, FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), w.toInt(), h.toInt())
      .onPhoto(sheet.pageMap()!!, photo.width, photo.height)
  }

  private lateinit var state: FluidLiveTextState

  @Test fun tenerePremutoSceglieLaParolaStorta() {
    compose.setContent {
      FluidTheme(EngineSettings()) {
        FluidFileViewer(count = 1, onBack = {}, content = {
          FluidViewerContent.Image(photo.width.toFloat() / photo.height) {
            Image(remember { photo.asImageBitmap() }, null, Modifier.fillMaxSize())
            val s = remember { FluidLiveTextState(photo.width.toFloat() / photo.height).also { it.show(known) } }
            state = s
            FluidLiveTextOverlay(s)
            FluidViewerPageAccessory { FluidLiveTextControls(s) }
          }
        })
      }
    }
    // Il visore finisce di entrare prima del primo tocco.
    compose.mainClock.advanceTimeBy(1000)
    compose.waitForIdle()
    assertTrue("le righe sono storte", known.lines.none { it.quad.isUpright })
    // In alto, in mezzo e in fondo al foglio: «Studio», «contratto», «Rossi»; con la modalità spenta (come in Foto,
    // tenere premuto sceglie anche senza il tasto) e poi accesa. Il dito va sul centro del quadrilatero storto della
    // parola, dato rispetto al disegno (che riempie il riquadro della foto).
    for ((round, word) in listOf("Studio", "contratto", "Rossi", "Studio", "contratto", "Rossi").withIndex()) {
      if (round == 3) {
        compose.onNodeWithTag(FluidLiveTextTags.Button).performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertTrue("accesa", state.active)
      }
      val index = known.words.indexOfLast { it.text.trim(',', '.') == word }
      val q = known.words[index].quad
      val c = (q.topLeft + q.topRight + q.bottomRight + q.bottomLeft) / 4f
      compose.onNodeWithTag(FluidLiveTextTags.Overlay).performTouchInput { down(Offset(c.x * width, c.y * height)) }
      compose.mainClock.advanceTimeBy(900)
      compose.waitForIdle()
      compose.onNodeWithTag(FluidLiveTextTags.Overlay).performTouchInput { up() }
      compose.waitForIdle()
      assertEquals("$word (giro $round)", FluidTextMatch(index, index), state.selection)
      assertEquals(known.words[index].text, state.selectedText)
      compose.runOnIdle { state.selection = null }
      // Fra un dito e l'altro più del doppio tocco del visore, che se no ingrandirebbe la foto: la foto resta al 100%.
      compose.mainClock.advanceTimeBy(1000)
      compose.waitForIdle()
      compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"), useUnmergedTree = true).assertExists()
    }
  }
}
