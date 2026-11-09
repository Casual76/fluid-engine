package dev.antigravity.fluidengine.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidRichTextMarkdownTest {
  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)
  private val numbered = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)
  private val quote = FluidRichTextParagraphStyle(quote = true)

  @Test fun writesTheSubset() {
    val doc = buildFluidRichText {
      paragraph { append("Ciao "); bold("Mario"); append(", "); italic("come va"); append("? "); underline("bene") }
      paragraph { }
      paragraph(bullet) { append("uno") }
      paragraph(numbered) { link("sito", "https://esempio.it/a_(b)") }
      paragraph(numbered) { append("due") }
      paragraph(quote) { append("citato") }
    }
    assertEquals(
      "Ciao **Mario**, *come va*? <u>bene</u>\n\n- uno\n1. [sito](https://esempio.it/a_\\(b\\))\n2. due\n> citato",
      FluidRichTextMarkdown.write(doc),
    )
  }

  @Test fun spacesStayOutsideTheMarkers() {
    val doc = buildFluidRichText { paragraph { append("a"); bold(" grassetto "); append("b") } }
    assertEquals("a **grassetto** b", FluidRichTextMarkdown.write(doc))
  }

  @Test fun literalMarkersAreEscaped() {
    val doc = buildFluidRichText {
      paragraph { append("2 * 3 = 6 e a_b [x] `c` <tag>") }
      paragraph { append("# non titolo") }
      paragraph { append("- non voce") }
      paragraph { append("2024. Un anno") }
      paragraph { append("> non citazione") }
    }
    val markdown = FluidRichTextMarkdown.write(doc)
    assertEquals(
      "2 \\* 3 = 6 e a\\_b \\[x\\] \\`c\\` \\<tag>\n\\# non titolo\n\\- non voce\n2024\\. Un anno\n\\> non citazione",
      markdown,
    )
    assertEquals(doc, FluidRichTextMarkdown.read(markdown))
  }

  @Test fun readsCommonMarkdown() {
    val doc = FluidRichTextMarkdown.read(
      """
      # Titolo
      Testo con __grassetto__, _corsivo_, ***tutti e due*** e `codice *non* stile`.
      * voce a
      + voce b
      3) terza
      > > citazione annidata
      > - voce citata
      Link <https://esempio.it> e [qui](javascript:alert(1)) e [là](https://là.it "titolo")
      ---
      2 * 3 = 6, snake_case_name
      ```
      **non formattato**
      ```
      """.trimIndent(),
    )
    val lines = doc.text.split('\n')
    assertEquals("Titolo", lines[0])
    assertTrue(doc.formatAt(0).bold)
    assertEquals("Testo con grassetto, corsivo, tutti e due e codice *non* stile.", lines[1])
    val line1 = doc.paragraphStart(1)
    assertTrue(doc.formatAt(line1 + lines[1].indexOf("grassetto")).bold)
    assertTrue(doc.formatAt(line1 + lines[1].indexOf("corsivo")).italic)
    val both = doc.formatAt(line1 + lines[1].indexOf("tutti"))
    assertTrue(both.bold && both.italic)
    assertFalse(doc.formatAt(line1 + lines[1].indexOf("non")).italic)
    assertEquals(listOf(bullet, bullet, numbered), doc.paragraphStyles.subList(2, 5))
    // Le citazioni annidate restano annidate (prima dell'annidamento si appiattivano).
    assertEquals(FluidRichTextParagraphStyle.of(FluidRichTextBlock.Quote, FluidRichTextBlock.Quote), doc.paragraphStyles[5])
    assertTrue(doc.paragraphStyles[5].quote)
    assertEquals("citazione annidata", lines[5])
    assertEquals(FluidRichTextParagraphStyle(FluidRichTextListKind.Bullet, quote = true), doc.paragraphStyles[6])
    assertEquals("Link https://esempio.it e qui e là", lines[7])
    val line7 = doc.paragraphStart(7)
    assertEquals("https://esempio.it", doc.formatAt(line7 + lines[7].indexOf("esempio")).link)
    assertNull(doc.formatAt(line7 + lines[7].indexOf("qui")).link)
    assertEquals("https://là.it", doc.formatAt(line7 + lines[7].indexOf("là")).link)
    assertEquals("", lines[8])
    assertEquals("2 * 3 = 6, snake_case_name", lines[9])
    assertTrue(doc.spans.none { it.start >= doc.paragraphStart(9) && it.start < doc.paragraphEnd(9) })
    assertEquals("**non formattato**", lines[10])
  }

  @Test fun roundTripKeepsTextStructureAndStylesOfVisibleCharacters() {
    val random = java.util.Random(3)
    repeat(400) {
      val doc = randomDocument(random)
      val markdown = FluidRichTextMarkdown.write(doc)
      val back = FluidRichTextMarkdown.read(markdown)
      assertEquals("MD: $markdown", doc.text, back.text)
      assertEquals("MD: $markdown", doc.paragraphStyles, back.paragraphStyles)
      // Il Markdown non sa dare uno stile a uno spazio da solo: si confrontano i caratteri visibili.
      for (index in doc.text.indices) {
        if (doc.text[index].isWhitespace()) continue
        assertEquals("MD: $markdown @ $index", doc.formatAt(index), back.formatAt(index))
      }
    }
  }

  @Test fun htmlToMarkdownToHtml() {
    val html = "<div><b>Ciao</b> <i>mondo</i></div><ul><li><a href=\"https://a.it\">uno</a></li></ul><blockquote><div>q</div></blockquote>"
    val doc = FluidRichTextHtml.read(html)
    assertEquals(html, FluidRichTextHtml.write(FluidRichTextMarkdown.read(FluidRichTextMarkdown.write(doc))))
  }
}
