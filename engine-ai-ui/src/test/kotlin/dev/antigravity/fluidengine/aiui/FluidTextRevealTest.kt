package dev.antigravity.fluidengine.aiui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.antigravity.fluidengine.aiui.markdown.FluidMarkdownParser
import dev.antigravity.fluidengine.aiui.markdown.FluidMarkdownText
import dev.antigravity.fluidengine.aiui.markdown.RevealCommitted
import dev.antigravity.fluidengine.aiui.markdown.RevealPending
import dev.antigravity.fluidengine.aiui.markdown.RevealTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La rivelazione parola per parola e' un po' di contabilita' (quali parole sono nuove, quali sono
 * state riscritte) e un po' di disegno. La contabilita' si prova qui; il disegno si guarda in mano.
 */
class FluidTextRevealTest {

  private val reveal = 180_000_000L
  private val rewrite = 120_000_000L

  private fun RevealTracker.ranges(text: String) = revealing.map { text.substring(it.start, it.end) }

  @Test
  fun `il primo testo mai visto e' gia' dentro, a meno che non nasca adesso`() {
    val settled = RevealTracker()
    settled.update("una risposta vecchia", reveal, rewrite, animateFirst = false)
    assertFalse(settled.hasWork)

    val born = RevealTracker()
    born.update("una risposta nuova", reveal, rewrite, animateFirst = true)
    assertEquals(listOf("una", "risposta", "nuova"), born.ranges("una risposta nuova"))
    assertTrue(born.revealing.all { it.born == RevealPending })
  }

  @Test
  fun `le parole che arrivano dopo sfumano, quelle di prima restano ferme`() {
    val tracker = RevealTracker()
    tracker.update("Ciao a", reveal, rewrite, animateFirst = false)
    tracker.update("Ciao a tutti voi", reveal, rewrite, animateFirst = false)
    assertEquals(listOf("tutti", "voi"), tracker.ranges("Ciao a tutti voi"))
  }

  @Test
  fun `una parola tagliata a meta' tiene la testa e fa nascere solo la coda`() {
    val tracker = RevealTracker()
    tracker.update("La Prima", reveal, rewrite, animateFirst = false)
    tracker.update("La Primavera", reveal, rewrite, animateFirst = false)
    // "Prima" era gia' dentro: "vera" e' la sola cosa nuova, e non e' una riscrittura.
    val tokens = tracker.revealing
    assertEquals(1, tokens.size)
    assertEquals("vera", "La Primavera".substring(tokens[0].start, tokens[0].end))
    assertEquals(reveal, tokens[0].durationNanos)
  }

  @Test
  fun `una riscrittura rinasce dalla divergenza, piu' veloce`() {
    val tracker = RevealTracker()
    tracker.update("Il cielo e' blu", reveal, rewrite, animateFirst = false)
    tracker.update("Il cielo e' grigio scuro", reveal, rewrite, animateFirst = false)
    assertEquals(listOf("grigio", "scuro"), tracker.ranges("Il cielo e' grigio scuro"))
    assertTrue(tracker.revealing.all { it.durationNanos == rewrite })
  }

  @Test
  fun `il testo che non cambia non fa niente`() {
    val tracker = RevealTracker()
    tracker.update("uno due", reveal, rewrite, animateFirst = true)
    tracker.stamp(1_000)
    val before = tracker.revealing.map { it.born }
    tracker.update("uno due", reveal, rewrite, animateFirst = true)
    assertEquals(before, tracker.revealing.map { it.born })
  }

  @Test
  fun `la maschera parte nascosta, scende con l'orologio e finisce a zero`() {
    val tracker = RevealTracker()
    tracker.update("a b", reveal, rewrite, animateFirst = true)
    val token = tracker.revealing.first()
    // Nato ma non ancora timbrato: nascosto. E' il fotogramma che non deve mostrare la parola intera.
    assertEquals(1f, token.mask(500L), 0f)
    tracker.stamp(1_000L)
    assertEquals(1f, token.mask(1_000L), 0.0001f)
    val mid = token.mask(1_000L + reveal / 2)
    assertTrue("a meta' tempo e' fra le due: $mid", mid > 0f && mid < 1f)
    assertEquals(0f, token.mask(1_000L + reveal), 0.0001f)
  }

  @Test
  fun `a durata zero (movimento ridotto) tutto compare com'e'`() {
    val tracker = RevealTracker()
    tracker.update("uno due", 0L, 0L, animateFirst = true)
    assertFalse(tracker.hasWork)
    tracker.update("uno due tre", 0L, 0L, animateFirst = true)
    assertFalse(tracker.hasWork)
  }

  @Test
  fun `settle mette dentro i token finiti e dice quando non c'e' piu' niente`() {
    val tracker = RevealTracker()
    tracker.update("a b", reveal, rewrite, animateFirst = true)
    tracker.stamp(0L)
    assertFalse(tracker.settle(reveal / 2))
    assertTrue(tracker.settle(reveal + 1))
    assertTrue(tracker.all.all { it.born == RevealCommitted })
  }

  // ---- dal testo in linea all'AnnotatedString ----------------------------------------------------

  private val code = SpanStyle(background = Color.Gray)

  @Test
  fun `l'AnnotatedString ha il testo senza segni e gli stili negli intervalli giusti`() {
    val inlines = FluidMarkdownParser.inlines("a **b** *c* ~~d~~ `e`")
    val annotated = FluidMarkdownText.annotate(inlines, linkColor = Color.Blue, code = code)
    assertEquals("a b c d e", annotated.text)
    val styles = annotated.spanStyles
    val bold = styles.first { it.item.fontWeight == FontWeight.Bold }
    assertEquals(2 to 3, bold.start to bold.end)
    val italic = styles.first { it.item.fontStyle == FontStyle.Italic }
    assertEquals(4 to 5, italic.start to italic.end)
    val strike = styles.first { it.item.textDecoration == TextDecoration.LineThrough }
    assertEquals(6 to 7, strike.start to strike.end)
    val inlineCode = styles.first { it.item.background == Color.Gray }
    assertEquals(8 to 9, inlineCode.start to inlineCode.end)
  }

  @Test
  fun `un link diventa una annotazione con il suo indirizzo, e il callback e' quello dell'app`() {
    var opened: String? = null
    val inlines = FluidMarkdownParser.inlines("vai su [OpenRouter](https://openrouter.ai)")
    val annotated = FluidMarkdownText.annotate(inlines, linkColor = Color.Blue, code = code, onLinkClick = { opened = it })
    assertEquals("vai su OpenRouter", annotated.text)
    val links = annotated.getLinkAnnotations(0, annotated.length)
    assertEquals(1, links.size)
    assertEquals(7 to 17, links[0].start to links[0].end)
    assertNotNull(links[0].item)
    assertTrue(opened == null)
  }
}
