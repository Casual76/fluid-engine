package dev.antigravity.fluidengine.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidRichTextHtmlTest {
  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)
  private val numbered = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)
  private val quote = FluidRichTextParagraphStyle(quote = true)

  @Test fun writesOnlyTheRestrictedDialect() {
    val doc = buildFluidRichText {
      paragraph { append("Ciao "); bold("Mario"); append(" & "); italic("<amici>") }
      paragraph { }
      paragraph(bullet) { append("uno") }
      paragraph(bullet) { underline("due") }
      paragraph(numbered) { link("sito", "https://esempio.it/?a=1&b=2") }
      paragraph(quote) { append("citato") }
      paragraph(FluidRichTextParagraphStyle(FluidRichTextListKind.Numbered, quote = true)) { append("voce citata") }
    }
    assertEquals(
      "<div>Ciao <b>Mario</b> &amp; <i>&lt;amici&gt;</i></div>" +
        "<div><br></div>" +
        "<ul><li>uno</li><li><u>due</u></li></ul>" +
        "<ol><li><a href=\"https://esempio.it/?a=1&amp;b=2\">sito</a></li></ol>" +
        "<blockquote><div>citato</div><ol><li>voce citata</li></ol></blockquote>",
      FluidRichTextHtml.write(doc),
    )
  }

  @Test fun nestingStaysWellFormedWithOverlappingStyles() {
    val doc = FluidRichTextDocument.fromPlainText("abcdef")
      .mapInline(0, 4) { it.copy(bold = true) }
      .mapInline(2, 6) { it.copy(italic = true) }
    assertEquals("<div><b>ab<i>cd</i></b><i>ef</i></div>", FluidRichTextHtml.write(doc))
  }

  @Test fun spacesThatHtmlWouldCollapseAreKept() {
    val doc = FluidRichTextDocument.fromPlainText(" a  b\tc ")
    val html = FluidRichTextHtml.write(doc)
    assertEquals("<div>&nbsp;a &nbsp;b&#9;c&nbsp;</div>", html)
    assertEquals(doc, FluidRichTextHtml.read(html))
  }

  @Test fun readsTypicalMailHtml() {
    val html = """
      <div dir="ltr">Ciao,<div><br></div><div>ecco <b>la lista</b>:</div>
      <ul><li>primo</li><li>secondo <a href="https://esempio.it">link</a></li></ul>
      <div class="gmail_quote"><blockquote class="gmail_quote" style="margin:0 0 0 .8ex">vecchio<br>testo</blockquote></div></div>
    """.trimIndent()
    val doc = FluidRichTextHtml.read(html)
    assertEquals("Ciao,\n\necco la lista:\nprimo\nsecondo link\nvecchio\ntesto", doc.text)
    assertEquals(
      listOf(FluidRichTextParagraphStyle.Plain, FluidRichTextParagraphStyle.Plain, FluidRichTextParagraphStyle.Plain, bullet, bullet, quote, quote),
      doc.paragraphStyles,
    )
    assertTrue(doc.formatAt(doc.text.indexOf("la lista")).bold)
    assertEquals("https://esempio.it", doc.formatAt(doc.text.indexOf("link")).link)
  }

  @Test fun readsGoogleDocsStyleSpans() {
    // Google Documenti avvolge tutto in un <b> con font-weight:normal, e mette gli stili negli span.
    val html = "<meta charset='utf-8'><b style=\"font-weight:normal;\" id=\"docs-internal-guid-1\">" +
      "<p dir=\"ltr\"><span style=\"font-weight:700;\">Grassetto</span><span style=\"font-weight:400;\"> normale </span>" +
      "<span style=\"font-style:italic;text-decoration:underline;\">corsivo</span></p></b>"
    val doc = FluidRichTextHtml.read(html)
    assertEquals("Grassetto normale corsivo", doc.text)
    assertTrue(doc.formatAt(0).bold)
    assertFalse(doc.formatAt(10).bold)
    val corsivo = doc.formatAt(doc.text.indexOf("corsivo"))
    assertTrue(corsivo.italic && corsivo.underline && !corsivo.bold)
  }

  @Test fun hostileHtmlIsNeverExecutedAndLinksAreNeutralized() {
    val html = """
      <script>alert('x')</script><style>body{color:red}</style>
      <p onclick="steal()">Testo <a href="javascript:alert(1)">cattivo</a>
      <a href=" JaVa&#10;ScRiPt:alert(1)">cattivo2</a>
      <a href="data:text/html;base64,PHNjcmlwdD4=">dati</a>
      <a href="/relativo">relativo</a>
      <a href="vbscript:x">vb</a>
      <a href="https://buono.it">buono</a></p>
      <img src="https://tracker.example/pixel.gif" onerror="x()">
      <iframe src="https://evil.example"></iframe><svg><text>svg</text></svg>
      <object data="x.swf">oggetto</object><template><b>tpl</b></template>
      <textarea>area</textarea><noscript>nos</noscript>
    """.trimIndent()
    val doc = FluidRichTextHtml.read(html)
    assertEquals("Testo cattivo cattivo2 dati relativo vb buono", doc.text)
    val links = doc.spans.mapNotNull { it.format.link }.distinct()
    assertEquals(listOf("https://buono.it"), links)
    assertFalse(doc.text.contains("alert"))
  }

  @Test fun brokenMarkupDoesNotThrowAndKeepsText() {
    val inputs = listOf(
      "<b>non chiuso <i>per niente",
      "</b></i>chiusure senza apertura</div></ul>",
      "<<<>>> < b > <a href='x",
      "<!-- commento non chiuso <b>x</b>",
      "<scr<script>ipt>alert(1)</script>dopo",
      "&notanentity; &#xZZ; &#99999999999; &#0; &amp",
      "<li>voce fuori da un elenco</li>",
      "<ul><li>a<ul><li>annidata</li></ul></li><li>b</li></ul>",
      "<div".repeat(1000),
      "<b>".repeat(5000) + "profondo" + "</b>".repeat(5000),
      "\u0000\u0001testo\u0007",
    )
    for (input in inputs) {
      val doc = FluidRichTextHtml.read(input)
      assertEquals(doc.paragraphCount, doc.paragraphStyles.size)
      assertFalse(doc.text.any { it < ' ' && it != '\n' && it != '\t' })
    }
    // Come in un browser, `<scr<script>` e' un tag sconosciuto: quello che segue e' testo, mai codice.
    assertEquals("ipt>alert(1)dopo", FluidRichTextHtml.read("<scr<script>ipt>alert(1)</script>dopo").text)
    val nested = FluidRichTextHtml.read("<ul><li>a<ul><li>annidata</li></ul></li><li>b</li></ul>")
    assertEquals("a\nannidata\nb", nested.text)
    assertTrue(nested.paragraphStyles.all { it.list == FluidRichTextListKind.Bullet })
    assertEquals(bullet, FluidRichTextHtml.read("<li>voce fuori da un elenco</li>").paragraphStyles.single())
    assertTrue(FluidRichTextHtml.read("<b>".repeat(5000) + "profondo" + "</b>".repeat(5000)).formatAt(0).bold)
    // Le entita' sconosciute restano letterali; i codici impossibili diventano il carattere sostitutivo.
    assertEquals(
      "&notanentity; &#xZZ; � � &amp",
      FluidRichTextHtml.read("&notanentity; &#xZZ; &#99999999999; &#0; &amp").text,
    )
  }

  @Test fun whitespaceCollapsesLikeABrowser() {
    val doc = FluidRichTextHtml.read("<p>\n   uno\n   due  </p>  <p> <b>tre</b> <i>quattro</i> </p>")
    assertEquals("uno due\ntre quattro", doc.text)
    assertFalse(doc.formatAt(3).bold)
  }

  @Test fun breaksMakeParagraphs() {
    assertEquals(listOf("a", "b"), FluidRichTextHtml.read("a<br>b").text.split('\n'))
    assertEquals(listOf("a", "", "b"), FluidRichTextHtml.read("a<br><br>b").text.split('\n'))
    assertEquals(listOf("a"), FluidRichTextHtml.read("<div>a<br></div>").text.split('\n'))
    assertEquals(listOf("a", "", "b"), FluidRichTextHtml.read("<div>a</div><div><br></div><div>b</div>").text.split('\n'))
    assertEquals(FluidRichTextDocument.Empty, FluidRichTextHtml.read(""))
    assertEquals(FluidRichTextDocument.Empty, FluidRichTextHtml.read("<div></div>"))
  }

  @Test fun headWithoutClosingTagDoesNotSwallowTheBody() {
    val doc = FluidRichTextHtml.read("<html><head><title>T</title><style>x{}</style><body><p>Corpo <b>vero</b></p></body></html>")
    assertEquals("Corpo vero", doc.text)
    assertTrue(doc.formatAt(6).bold)
    assertEquals("Testo", FluidRichTextHtml.read("<head><meta charset=utf-8></head><body>Testo</body>").text)
  }

  @Test fun preKeepsSpacesAndLines() {
    val doc = FluidRichTextHtml.read("<pre>a  b\nc</pre>")
    assertEquals("a  b\nc", doc.text)
  }

  @Test fun tableCellsAreSeparatedBySpaces() {
    val doc = FluidRichTextHtml.read("<table><tr><td>Nome</td><td>Mario</td></tr><tr><th>X</th><td>Y</td></tr></table>")
    assertEquals("Nome Mario\nX Y", doc.text)
    assertTrue(doc.formatAt(doc.text.indexOf('X')).bold)
  }

  @Test fun entitiesDecode() {
    val doc = FluidRichTextHtml.read("caff&egrave; &euro;5 &#8364; &#x1F600; &quot;x&quot; a&nbsp;&nbsp;b")
    assertEquals("caffè €5 € 😀 \"x\" a  b", doc.text)
  }

  @Test fun roundTripIsExactForEveryDocument() {
    val random = java.util.Random(7)
    repeat(400) {
      val doc = randomDocument(random)
      val html = FluidRichTextHtml.write(doc)
      assertEquals("HTML: $html", doc, FluidRichTextHtml.read(html))
      // Il dialetto scritto e' stabile: riscriverlo da' lo stesso HTML.
      assertEquals(html, FluidRichTextHtml.write(FluidRichTextHtml.read(html)))
    }
  }

  @Test fun outputNeverContainsUnsafeOrUnknownMarkup() {
    val random = java.util.Random(11)
    val allowed = Regex("</?(div|br|ul|ol|li|blockquote|b|i|u|a)( href=\"[^\"]*\")?>")
    repeat(200) {
      val html = FluidRichTextHtml.write(randomDocument(random))
      val tags = Regex("<[^>]*>").findAll(html).map { it.value }.toList()
      assertTrue(tags.joinToString(), tags.all { allowed.matches(it) })
      assertNull(Regex("(?i)javascript:|on\\w+=").find(html))
    }
  }
}

/** Un documento a caso: testo con caratteri speciali, stili sovrapposti, link, elenchi e citazioni. */
internal fun randomDocument(random: java.util.Random): FluidRichTextDocument {
  val alphabet = "abcdeèfgh  xyz<>&\"'*_[]#-+1.2)\\`\tà€😀"
  val urls = listOf("https://a.it", "https://b.example/x?y=1&z=2", "mailto:a@b.it", "tel:+390551234")
  val styles = listOf(
    FluidRichTextParagraphStyle.Plain,
    FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet),
    FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered),
    FluidRichTextParagraphStyle(quote = true),
    FluidRichTextParagraphStyle(FluidRichTextListKind.Bullet, quote = true),
  )
  return buildFluidRichText {
    repeat(1 + random.nextInt(5)) {
      paragraph(styles[random.nextInt(styles.size)]) {
        repeat(random.nextInt(5)) {
          val length = 1 + random.nextInt(6)
          val text = buildString {
            repeat(length) {
              val index = random.nextInt(alphabet.length)
              val char = alphabet[index]
              if (Character.isHighSurrogate(char)) append(alphabet, index, index + 2)
              else if (!Character.isLowSurrogate(char)) append(char)
            }
          }
          val format = FluidRichTextInlineFormat(
            bold = random.nextBoolean(),
            italic = random.nextInt(3) == 0,
            underline = random.nextInt(4) == 0,
            link = if (random.nextInt(5) == 0) urls[random.nextInt(urls.size)] else null,
          )
          append(text, format)
        }
      }
    }
  }
}
