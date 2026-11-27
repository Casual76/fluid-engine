package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.transcript.FluidTranscript
import dev.antigravity.fluidengine.transcript.FluidTranscriptParagraph
import dev.antigravity.fluidengine.transcript.FluidTranscriptSpan
import dev.antigravity.fluidengine.transcript.FluidTranscriptView
import dev.antigravity.fluidengine.ui.fluid.FluidSpokenWord
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R3 (review del 27/11/2026, difetto 20) · Il tocco sul testo della trascrizione, col testo impaginato davvero. La vista
 * chiedeva `getOffsetForPosition`, che dà il punto di inserimento più vicino: la metà destra della «i» di «Oggi» e la
 * metà sinistra dello spazio dopo davano lo stesso 4. Con la regola «fino alla fine compresa» lo spazio saltava alla
 * parola prima; con la sola fine esclusa (la correzione del revisore) la metà destra della «i» sarebbe saltata a quella
 * dopo. Qui si tocca ogni metà.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class R3m1TranscriptTest {

  @get:Rule
  val compose = createComposeRule()

  private val text = "Oggi parliamo del ciclo."
  private val words = listOf(
    FluidSpokenWord(0, 4, 1_000, 1_400),
    FluidSpokenWord(5, 13, 1_400, 1_800),
    FluidSpokenWord(14, 17, 1_800, 2_200),
    FluidSpokenWord(18, 23, 2_200, 2_600),
  )
  private val seeks = mutableListOf<Long>()

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun show() {
    val paragraph = FluidTranscriptParagraph(text, startMillis = 900, words = words, segments = listOf(FluidTranscriptSpan(0, 24, 1_000, 2_600)))
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidTranscriptView(
          transcript = FluidTranscript(listOf(paragraph)),
          position = { 0L },
          onSeek = { seeks += it },
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    compose.waitForIdle()
  }

  private fun node() = compose.onNode(hasText(text), useUnmergedTree = true)

  private fun layout(): TextLayoutResult {
    val out = mutableListOf<TextLayoutResult>()
    node().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
    return out.first()
  }

  /** Tocca il carattere [char] a [fraction] della sua larghezza e dice dove si salta. */
  private fun tap(char: Int, fraction: Float): Long {
    val box = layout().getBoundingBox(char)
    val at = Offset(box.left + box.width * fraction, box.center.y)
    seeks.clear()
    node().performTouchInput { click(at) }
    compose.waitForIdle()
    assertEquals("un tocco, un salto", 1, seeks.size)
    return seeks.single()
  }

  @Test
  fun `il tocco sullo spazio dopo una parola va alla parola dopo, l'ultima lettera resta sua`() {
    show()
    assertEquals("il testo sta su una riga", 1, layout().lineCount)
    assertEquals(' ', text[4])
    assertEquals("la metà destra della «i» di «Oggi» è ancora «Oggi»", 1_000L, tap(3, 0.8f))
    assertEquals("la metà sinistra dello spazio va a «parliamo»", 1_400L, tap(4, 0.2f))
    assertEquals("la metà destra dello spazio va a «parliamo»", 1_400L, tap(4, 0.8f))
    assertEquals("dentro «parliamo»", 1_400L, tap(8, 0.5f))
    assertEquals("la metà destra della «l» di «del» è ancora «del»", 1_800L, tap(16, 0.8f))
    assertEquals("il punto in fondo resta su «ciclo»", 2_200L, tap(23, 0.5f))
  }
}
