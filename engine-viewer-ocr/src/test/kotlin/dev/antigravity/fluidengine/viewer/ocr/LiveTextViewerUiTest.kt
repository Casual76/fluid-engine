package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
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
import kotlin.math.abs

/**
 * F8.17 · «Leggi testo» nel visore vero (FluidFileViewer): le righe stanno sopra le righe dell'immagine a scala 1, dopo
 * lo zoom col doppio tocco e con lo schermo in orizzontale; tenere premuto su una parola sceglie quella parola anche da
 * ingranditi; TalkBack legge le righe in ordine e il tasto è un interruttore; col movimento ridotto i comandi compaiono
 * subito. Il testo qui è quello vero della schermata di prova (i riquadri del generatore), per provare l'allineamento
 * senza dipendere dal riconoscimento.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveTextViewerUiTest {
  @get:Rule val compose = createComposeRule()

  private val entry = JSONObject(javaClass.classLoader!!.getResourceAsStream("ocr/righe.json")!!.readBytes().toString(Charsets.UTF_8))
    .getJSONArray("images").let { a -> (0 until a.length()).map(a::getJSONObject).first { it.getString("name") == "schermata.png" } }
  private val width = entry.getInt("width").toFloat()
  private val height = entry.getInt("height").toFloat()

  /** Il testo «giusto»: una parola per riquadro di parola stimato dividendo la riga in parti proporzionali alle lettere. */
  private val known: FluidRecognizedText = run {
    val rows = entry.getJSONArray("lines")
    val lines = (0 until rows.length()).map { i ->
      val row = rows.getJSONObject(i)
      val b = row.getJSONArray("box")
      val r = Rect(b.getInt(0) / width, b.getInt(1) / height, b.getInt(2) / width, b.getInt(3) / height)
      val words = row.getString("text").split(' ')
      val total = words.sumOf { it.length + 1 }.toFloat()
      var x = r.left
      val recognized = words.map { w ->
        val span = r.width * (w.length + 1) / total
        FluidRecognizedWord(w, FluidTextQuad.of(Rect(x, r.top, x + span * w.length / (w.length + 1), r.bottom)), 0.95f).also { x += span }
      }
      FluidRecognizedLine(recognized, FluidTextQuad.of(r))
    }
    FluidRecognizedText(listOf(FluidRecognizedBlock(lines, FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), width.toInt(), height.toInt())
  }

  private lateinit var state: FluidLiveTextState
  private var landscape by mutableStateOf(false)

  private fun show(reduced: Boolean = false, ready: Boolean = true) {
    val bitmap = javaClass.classLoader!!.getResourceAsStream("ocr/schermata.png").use { BitmapFactory.decodeStream(it) }!!.asImageBitmap()
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          Box(if (landscape) Modifier.size(891.dp, 411.dp) else Modifier.fillMaxSize()) {
            FluidFileViewer(count = 1, onBack = {}, content = {
              FluidViewerContent.Image(width / height) {
                Image(bitmap, null, Modifier.fillMaxSize())
                val s = remember { FluidLiveTextState(width / height).also { if (ready) it.show(known) } }
                state = s
                FluidLiveTextOverlay(s)
                FluidViewerPageAccessory { FluidLiveTextControls(s) }
              }
            })
          }
        }
      }
    }
    compose.waitForIdle()
  }

  /** La trasformazione dalla foto (0..1) allo schermo (dp), ricavata dalle righe in vista: x = ox + kx · u, y = oy + ky · v. */
  private class Fit(val kx: Float, val ky: Float, val ox: Float, val oy: Float)

  /**
   * Ogni riga in vista sta dove la sua riga vera sta nella foto. Dalle righe intere in altezza si ricavano scala e
   * spostamento verticali; la scala orizzontale è quella di uno zoom uniforme (le proporzioni della foto) e lo
   * spostamento orizzontale viene da un inizio di riga in vista; poi ogni spigolo in vista (alto, basso, e sinistro e
   * destro quando non sono tagliati dallo schermo) deve cadere al suo posto entro 1,2 dp.
   */
  private fun assertLinesAligned(where: String, minWidth: Float): Fit {
    val root = compose.onRoot().getBoundsInRoot()
    val lines = known.lines.mapIndexed { i, line ->
      Triple(i, line.quad.bounds, compose.onNodeWithTag(FluidLiveTextTags.line(i), useUnmergedTree = true).getBoundsInRoot())
    }.filter { (_, _, b) -> b.right > b.left && b.top > root.top + 0.5.dp && b.bottom < root.bottom - 0.5.dp }
    assertTrue("$where: almeno due righe intere in altezza (${lines.size})", lines.size >= 2)
    val a = lines.minBy { it.second.top }
    val c = lines.maxBy { it.second.top }
    val ky = (c.third.top - a.third.top).value / (c.second.top - a.second.top)
    val oy = a.third.top.value - ky * a.second.top
    val kx = ky * width / height
    assertTrue("$where: la foto è larga $kx dp, attesi almeno $minWidth", kx >= minWidth)
    val start = lines.firstOrNull { it.third.left > root.left + 0.5.dp }
    assertTrue("$where: almeno un inizio di riga in vista", start != null)
    val ox = start!!.third.left.value - kx * start.second.left
    for ((i, r, b) in lines) {
      val checks = mutableListOf(b.top.value to oy + ky * r.top, b.bottom.value to oy + ky * r.bottom)
      if (b.left > root.left + 0.5.dp) checks += b.left.value to ox + kx * r.left
      if (b.right < root.right - 0.5.dp) checks += b.right.value to ox + kx * r.right
      for ((actual, expected) in checks) assertTrue("$where, riga $i: $actual invece di $expected", abs(actual - expected) <= 1.2f)
    }
    return Fit(kx, ky, ox, oy)
  }

  private fun zoomedTo(percent: Int) =
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom $percent%"), useUnmergedTree = true).assertExists()

  /** Il doppio tocco del visore vicino al bordo sinistro, fuori dalle bolle: lo zoom a 2,5× tiene in vista gli inizi delle righe. */
  private fun zoomNearLeftEdge() {
    compose.onRoot().performTouchInput { doubleClick(Offset(width * 0.03f, height * 0.6f)) }
    compose.mainClock.advanceTimeBy(1500)
    compose.waitForIdle()
    zoomedTo(250)
  }

  @Test fun leRigheSeguonoLoZoomELOrizzontale() {
    show()
    compose.onNodeWithTag(FluidLiveTextTags.Button).performClick()
    compose.waitForIdle()
    assertLinesAligned("scala 1", minWidth = 400f)
    // Il doppio tocco ingrandisce a 2,5×: le righe vanno con la foto (il tocco cade fuori dalle parole: con la modalità
    // attiva un tocco su una parola la sceglie, come in Foto).
    zoomNearLeftEdge()
    assertLinesAligned("ingrandita", minWidth = 411f * 2.4f)
    // Di nuovo a 100% col doppio tocco, poi lo schermo in orizzontale: la foto si rifà più piccola, le righe con lei.
    compose.onRoot().performTouchInput { doubleClick(Offset(width * 0.03f, height * 0.6f)) }
    compose.mainClock.advanceTimeBy(1500)
    compose.waitForIdle()
    zoomedTo(100)
    landscape = true
    compose.mainClock.advanceTimeBy(1500)
    compose.waitForIdle()
    assertLinesAligned("in orizzontale", minWidth = 150f)
  }

  @Test fun tenerePremutoSceglieLaParolaAncheIngrandita() {
    show()
    // La modalità accesa dà gli elementi delle righe, da cui si legge dove sono le parole dopo lo zoom.
    compose.onNodeWithTag(FluidLiveTextTags.Button).performClick()
    compose.waitForIdle()
    zoomNearLeftEdge()
    val fit = assertLinesAligned("ingrandita", minWidth = 411f * 2.4f)
    val root = compose.onRoot().getBoundsInRoot()
    // La prima parola in vista di una riga in vista, dove sta adesso sullo schermo.
    val target = known.words.indices.first { w ->
      val c = known.words[w].quad.bounds.center
      val x = fit.ox + fit.kx * c.x
      val y = fit.oy + fit.ky * c.y
      x > root.left.value + 20f && x < root.right.value - 20f && y > root.top.value + 120f && y < root.bottom.value - 140f
    }
    val c = known.words[target].quad.bounds.center
    val density = 2.625f
    // Il dito tenuto fermo oltre la pressione lunga, col tempo del test che avanza davvero.
    compose.onRoot().performTouchInput { down(Offset((fit.ox + fit.kx * c.x) * density, (fit.oy + fit.ky * c.y) * density)) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    compose.onRoot().performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(known.words[target].text, state.selectedText)
    compose.onNodeWithTag(FluidLiveTextTags.Menu).assertIsDisplayed()
    compose.onNodeWithTag(FluidLiveTextTags.MenuSelectAll).performClick()
    compose.waitForIdle()
    assertEquals(known.words.size - 1, state.selection!!.last)
  }

  @Test fun talkBackEMovimentoRidotto() {
    show(reduced = true, ready = false)
    // Prima della lettura il tasto non c'è (come il tasto di Testo attivo, che compare solo se c'è testo).
    assertEquals(0, compose.onAllNodesWithTagCount(FluidLiveTextTags.Button))
    compose.runOnIdle { state.show(known) }
    compose.mainClock.advanceTimeBy(16)
    compose.waitForIdle()
    // Col movimento ridotto compare nello stesso fotogramma, senza la comparsa sfocata.
    compose.onNodeWithTag(FluidLiveTextTags.Button).assertIsDisplayed()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "spento"))
      .performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(FluidLiveTextTags.Button).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "attivo"))
    // Ogni riga è un elemento con il suo testo, nell'ordine della lettura, con «Copia la riga».
    known.lines.forEachIndexed { i, line ->
      compose.onNodeWithTag(FluidLiveTextTags.line(i), useUnmergedTree = true)
        .assert(SemanticsMatcher("testo") { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString() == line.text })
    }
  }

  private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag), useUnmergedTree = true).fetchSemanticsNodes().size
}
