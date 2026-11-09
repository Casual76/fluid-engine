package dev.antigravity.fluidengine.ui.fluid

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

/** Il sigillo del contenitore web: nessun `<link`, `<meta` o `<iframe` scritto dal documento arriva al renderer.
 *
 * Questi test provano la proprieta' sul testo. Che Chromium legga quel testo come ci si aspetta lo provano
 * i test strumentati della galleria (socket, DNS e DOM vero), non questi.
 */
class FluidWebSealTest {
  private val origin = "https://tracker.invalid"

  /** Controllo scritto in modo diverso da [FluidWebSeal.isSealed]: espressioni regolari sulle sole forme canoniche. */
  private val trigger = Regex("<(link|meta|/?iframe)", RegexOption.IGNORE_CASE)
  private val canonicalLink = Regex("""<link rel="stylesheet" href="[^"<>]*"( media="[^"<>]*")?( title="[^"<>]*")?>""")
  private val canonicalMeta = Regex("""<meta name="(viewport|color-scheme)" content="[^"<>]*">""")

  private fun assertClosed(source: String): String {
    val sealed = FluidWebSeal.seal(source)
    for (match in trigger.findAll(sealed)) {
      val canonical = when (match.groupValues[1]) {
        "link" -> canonicalLink.matchAt(sealed, match.range.first)
        "meta" -> canonicalMeta.matchAt(sealed, match.range.first)
        else -> null
      }
      assertNotNull("Resta ${match.value} non canonico in: ${show(sealed)}\nda: ${show(source)}", canonical)
    }
    assertTrue("isSealed rifiuta: ${show(sealed)}", FluidWebSeal.isSealed(sealed))
    return sealed
  }

  private fun show(text: String) = text.take(400).replace("\u0000", "\\0").replace("\u000C", "\\f")

  private fun assertNoLink(source: String) {
    val sealed = assertClosed(source)
    assertFalse("Un link e' sopravvissuto in: ${show(sealed)}\nda: ${show(source)}", sealed.contains("<link", ignoreCase = true))
  }

  @Test fun documentWithoutTriggersIsTheSameInstance() {
    for (html in listOf(
      "",
      "<h1>Titolo</h1><p>Testo con &lt;link rel=preconnect&gt; scritto come testo.</p>",
      "<a href='https://example.org/link'>link</a><img src='cid:meta'><li nk><me ta><ifram e>",
      "<h1>Documento lungo</h1>" + "<div><b>Testo non chiuso<p>Contenuto leggibile. ".repeat(2000),
      "< link rel=preconnect><\u0000link><l\u0000ink><\uFEFFlink>",
    )) assertSame(html, FluidWebSeal.seal(html))
  }

  @Test fun networkHintsLoseTheirElementAndKeepTheirText() {
    assertEquals("<basefont rel=preconnect href=$origin><p>a</p>", FluidWebSeal.seal("<link rel=preconnect href=$origin><p>a</p>"))
    assertEquals("<basefont rel='dns-prefetch' href='//tracker.invalid'>", FluidWebSeal.seal("<LINK rel='dns-prefetch' href='//tracker.invalid'>"))
    for (rel in listOf("preconnect", "dns-prefetch", "preload", "modulepreload", "prefetch", "prerender", "icon",
      "apple-touch-icon", "manifest", "search", "alternate", "canonical", "pingback", "next", "expect", "compression-dictionary")) {
      assertNoLink("<link rel=\"$rel\" href=\"$origin/x\" as=\"image\">")
    }
  }

  @Test fun disguisedHintsAreNeutralizedInEveryForm() {
    for (html in listOf(
      "<LiNk ReL=PrEcOnNeCt HrEf=$origin>",
      "<link/rel=preconnect/href=$origin>",
      "<link\trel=preconnect\thref=$origin>",
      "<link\u000Crel=preconnect\u000Chref=$origin>",
      "<link\nrel=preconnect\r\nhref=$origin\n>",
      "<link rel=\"&#112;reconnect\" href=$origin>",
      "<link rel=\"&#x70;reconnect\" href=$origin>",
      "<link rel=\"pre&#99;onnect\" href=\"https&#58;//tracker.invalid\">",
      "<link rel=stylesheet rel=preconnect href=$origin>",
      "<link rel=preconnect rel=stylesheet href=$origin>",
      "<link rel=\"stylesheet preconnect\" href=$origin>",
      "<link rel=\"preconnect stylesheet\" href=$origin>",
      "<link rel=\"dns-prefetch preconnect prefetch\" href=$origin>",
      "<link rel=\" \tpreconnect\n\" href=$origin>",
      "<link rel=preconnect href=$origin", // tag non chiuso a fine documento
      "<link rel=preconnect href=\"$origin", // virgolette non chiuse
      "<link rel=preconnect\u0000 href=$origin>",
      "<link \u0000rel=preconnect href=$origin>",
      "<link x=\"\u0000\" rel=preconnect href=$origin>",
      "<link a=\"b\"rel=preconnect href=$origin>",
      "<link =x rel=preconnect href=$origin>",
      "<link rel=preconnect href=$origin crossorigin>",
      "<svg><link rel=preconnect href=$origin></svg>",
      "<math><mi><link rel=preconnect href=$origin></mi></math>",
      "<svg><title><link rel=preconnect href=$origin></title></svg>",
      "<svg><foreignObject><link rel=preconnect href=$origin></foreignObject></svg>",
      "<!-- <link rel=preconnect href=$origin>",
      "<!--> <link rel=preconnect href=$origin>",
      "<!--!> <link rel=preconnect href=$origin> -->",
      "<![CDATA[ <link rel=preconnect href=$origin>",
      "<svg><![CDATA[ ]]><link rel=preconnect href=$origin></svg>",
      "<body><p>Testo</p><link rel=preconnect href=$origin></body>",
      "<table><tr><link rel=preconnect href=$origin><td>x</td></tr></table>",
      "<select><link rel=preconnect href=$origin></select>",
      "<template><link rel=preconnect href=$origin></template>",
      "<div><template shadowrootmode=open><link rel=preconnect href=$origin></template></div>",
      "<noscript><link rel=preconnect href=$origin></noscript>",
      "<head><noscript><link rel=preconnect href=$origin></noscript></head>",
      "<title><link rel=preconnect href=$origin></title>",
      "<textarea><link rel=preconnect href=$origin></textarea>",
      "<style><link rel=preconnect href=$origin></style>",
      "<xmp><link rel=preconnect href=$origin></xmp>",
      "<plaintext><link rel=preconnect href=$origin>",
      "<p title=\"<link rel=preconnect href=$origin>\">x</p>",
      "<<link rel=preconnect href=$origin>",
      "<link<link rel=preconnect href=$origin>",
      "<linkrel=preconnect href=$origin>",
      "</link rel=preconnect><link rel=preconnect href=$origin>",
      "<a href=\"$origin\"><link rel=preconnect href=$origin></a>",
    )) assertNoLink(html)
  }

  /** Le regole non guardano il contesto: dentro un attributo il testo cambia, ma resta nella forma che il controllo accetta. */
  @Test fun stylesheetTextInsideAnAttributeStaysCanonical() {
    assertEquals("<p title='<link rel=\"stylesheet\" href=\"$origin/a.css\"><basefont rel=stylesheet href=$origin/a.css>'>x</p>",
      assertClosed("<p title='<link rel=stylesheet href=$origin/a.css>'>x</p>"))
    assertClosed("<p title=\"<link rel=stylesheet href=$origin/a.css>\" x>y</p>")
    assertClosed("<!-- <link rel=stylesheet href=$origin/a.css> --><style><link rel=stylesheet href=$origin/a.css></style>")
  }

  @Test fun plainStylesheetIsWrittenAgainInCanonicalForm() {
    fun canonical(source: String, expected: String) {
      val sealed = assertClosed(source)
      assertTrue("Atteso $expected all'inizio di: $sealed", sealed.startsWith(expected + "<basefont"))
      assertEquals("Uno e un solo link", 1, Regex("<link", RegexOption.IGNORE_CASE).findAll(sealed).count())
    }
    canonical("<link rel=\"stylesheet\" href=\"$origin/a.css\">", "<link rel=\"stylesheet\" href=\"$origin/a.css\">")
    canonical("<LINK REL='StyleSheet' HREF='$origin/a.css' TYPE='Text/CSS'/>", "<link rel=\"stylesheet\" href=\"$origin/a.css\">")
    canonical("<link href=$origin/a.css rel=stylesheet>", "<link rel=\"stylesheet\" href=\"$origin/a.css\">")
    canonical("<link\nrel = \"stylesheet\"\n\thref = \"$origin/a.css\"\n>", "<link rel=\"stylesheet\" href=\"$origin/a.css\">")
    canonical("<link rel=\" stylesheet\t\" href=\"$origin/a.css\" id=x class=y hreflang=it>", "<link rel=\"stylesheet\" href=\"$origin/a.css\">")
    canonical("<link rel=\"&#115;tylesheet\" href=\"$origin/a.css\">", "<link rel=\"stylesheet\" href=\"$origin/a.css\">")
    // Le entita' certe si decodificano e si riscrivono; `&nome=` in un attributo non e' mai un'entita'.
    canonical("<link rel=stylesheet href=\"$origin/css?family=Inter&amp;display=swap\">",
      "<link rel=\"stylesheet\" href=\"$origin/css?family=Inter&amp;display=swap\">")
    canonical("<link rel=stylesheet href=\"$origin/css?family=Inter&display=swap&copy=1\">",
      "<link rel=\"stylesheet\" href=\"$origin/css?family=Inter&amp;display=swap&amp;copy=1\">")
    canonical("<link rel=stylesheet href=\"$origin/a&#46;css?q=&quot;x&quot;&lt;&gt;\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?q=&quot;x&quot;&lt;&gt;\">")
    canonical("<link rel=stylesheet href='$origin/a.css' media='screen and (max-width:600px)' title=\"Chiaro\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" media=\"screen and (max-width:600px)\" title=\"Chiaro\">")
    canonical("<link rel=stylesheet href='$origin/a\"b>c.css'>", "<link rel=\"stylesheet\" href=\"$origin/a&quot;b&gt;c.css\">")
    canonical("<link rel=stylesheet href=$origin/a.css/>", "<link rel=\"stylesheet\" href=\"$origin/a.css/\">")
  }

  @Test fun doubtfulStylesheetIsDroppedNotGuessed() {
    for (html in listOf(
      "<link rel=\"alternate stylesheet\" href=\"$origin/a.css\">",
      "<link rel=\"stylesheet\" rel=\"stylesheet\" href=\"$origin/a.css\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" href=\"$origin/b.css\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" integrity=\"sha384-x\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" crossorigin>",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" disabled>",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" type=\"text/plain\">",
      "<link rel=\"stylesheet\" href=\"\">",
      "<link rel=\"stylesheet\" href=\" \n\">",
      "<link rel=\"stylesheet\">",
      "<link href=\"$origin/a.css\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&copy;\">", // entita' con nome fuori dalle cinque certe
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x&copy\">", // senza `;`: decodificata o no secondo la tabella completa
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#0;\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#x80;\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#xD800;\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#1114112;\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#65\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#;\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css?x=&#000000065;\">",
      "<link rel=\"style&shy;sheet\" href=\"$origin/a.css\">",
      "<link rel=\"stylesheet\u00A0\" href=\"$origin/a.css\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\"media=print>",
      "<link rel=\"stylesheet\" href=\"$origin/a.css",
      "<link rel=\"stylesheet\" href=$origin/a.css",
      "<link rel=\"stylesheet\" href=`$origin/a.css`>",
      "<link rel=\"stylesheet\" href=$origin/a.css?a=b>",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" x=\"\u0000\">",
      "<link rel=\"stylesheet\" href=\"$origin/a.css\" " + "x ".repeat(FluidWebSeal.MaxTagCharacters) + ">",
      "<linkx rel=\"stylesheet\" href=\"$origin/a.css\">",
      "<link\u0000 rel=\"stylesheet\" href=\"$origin/a.css\">",
    )) assertNoLink(html)
  }

  @Test fun metaKeepsOnlyViewportAndColorScheme() {
    assertEquals("<meta name=\"viewport\" content=\"width=device-width\"><basefont name='viewport' content='width=device-width'>",
      assertClosed("<meta name='viewport' content='width=device-width'>"))
    assertEquals("<meta name=\"color-scheme\" content=\"dark light\"><basefont NAME=Color-Scheme CONTENT=\"dark light\">",
      assertClosed("<META NAME=Color-Scheme CONTENT=\"dark light\">"))
    assertEquals("<meta name=\"viewport\" content=\"a&amp;b&quot;&lt;\"><basefont content=\"a&amp;b&quot;&#60;\" name=\"viewport\"/>",
      assertClosed("<meta content=\"a&amp;b&quot;&#60;\" name=\"viewport\"/>"))
    for (html in listOf(
      "<meta http-equiv=\"x-dns-prefetch-control\" content=\"on\">",
      "<meta http-equiv='refresh' content='0;url=$origin/refresh'>",
      "<meta http-equiv=\"Link\" content=\"<$origin>; rel=preconnect\">",
      "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src *\">",
      "<meta http-equiv=\"origin-trial\" content=\"token\">",
      "<meta http-equiv=\"accept-ch\" content=\"sec-ch-ua-model\">",
      "<meta http-equiv=\"set-cookie\" content=\"a=b\">",
      "<META HTTP-EQUIV=X-DNS-Prefetch-Control CONTENT=on>",
      "<meta/http-equiv=x-dns-prefetch-control/content=on>",
      "<meta http-equiv=\"x-dns-prefetch-&#99;ontrol\" content=\"on\">",
      "<meta name=\"viewport\" http-equiv=\"refresh\" content=\"0;url=$origin\">",
      "<meta name=\"viewport\" content=\"a\" content=\"b\">",
      "<meta name=\"viewport \" content=\"width=1\">",
      "<meta name=\"referrer\" content=\"unsafe-url\">",
      "<meta charset=\"utf-7\">",
      "<meta name=viewport content=x",
      "<svg><meta http-equiv=x-dns-prefetch-control content=on></svg>",
      "<!-- <meta http-equiv=x-dns-prefetch-control content=on>",
    )) {
      val sealed = assertClosed(html)
      assertFalse("Un meta e' sopravvissuto in: $sealed", sealed.contains("<meta", ignoreCase = true))
    }
  }

  @Test fun nestedDocumentsAreNeverCreated() {
    assertEquals("<noframes srcdoc=\"&lt;link rel=preconnect href=$origin&gt;\">ripiego</noframes>",
      assertClosed("<iframe srcdoc=\"&lt;link rel=preconnect href=$origin&gt;\">ripiego</iframe>"))
    assertEquals("<noframes src=$origin></noframes><p>dopo</p>", assertClosed("<IFRAME src=$origin></IfRaMe><p>dopo</p>"))
    for (html in listOf(
      "<iframe/srcdoc='&#60;link rel=preconnect href=$origin&#62;'>",
      "<iframe srcdoc=\"&lt;iframe srcdoc=&quot;&amp;lt;link rel=preconnect href=$origin&amp;gt;&quot;&gt;\">",
      "<svg><iframe srcdoc='x'></svg>",
      "<iframe srcdoc=x",
      "<iframe><link rel=preconnect href=$origin></iframe>",
      "<noframes><iframe srcdoc=x></noframes></iframe>",
    )) {
      val sealed = assertClosed(html)
      assertFalse(sealed, Regex("</?iframe", RegexOption.IGNORE_CASE).containsMatchIn(sealed))
    }
  }

  @Test fun realisticHeadKeepsItsUsefulParts() {
    val html = """
      <!doctype html><html><head><meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <meta http-equiv="X-UA-Compatible" content="IE=edge"><title>Notizie</title>
      <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
      <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;700&display=swap" rel="stylesheet">
      <style>body{margin:0}</style></head><body><p>Ciao</p></body></html>
    """.trimIndent()
    val sealed = assertClosed(html)
    assertTrue(sealed.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"))
    assertTrue(sealed.contains("<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;700&amp;display=swap\">"))
    assertEquals(1, Regex("<link").findAll(sealed).count())
    assertEquals(1, Regex("<meta").findAll(sealed).count())
    assertTrue(sealed.contains("<title>Notizie</title>") && sealed.contains("<style>body{margin:0}</style>") && sealed.contains("<p>Ciao</p>"))
  }

  @Test fun isSealedAcceptsOnlyTheCanonicalForms() {
    for (html in listOf(
      "", "<p>testo</p>", "<basefont rel=preconnect>", "<noframes srcdoc=x></noframes>",
      "<link rel=\"stylesheet\" href=\"a.css\">",
      "<link rel=\"stylesheet\" href=\"a.css\" media=\"print\">",
      "<link rel=\"stylesheet\" href=\"a.css\" title=\"T\">",
      "<link rel=\"stylesheet\" href=\"a.css\" media=\"print\" title=\"T\">x<link rel=\"stylesheet\" href=\"\">",
      "<meta name=\"viewport\" content=\"width=device-width\"><meta name=\"color-scheme\" content=\"\">",
    )) assertTrue(html, FluidWebSeal.isSealed(html))
    for (html in listOf(
      "<link>", "<link rel=preconnect>", "<LINK rel=\"stylesheet\" href=\"a.css\">",
      "<link rel=\"stylesheet\" href=\"a.css\" rel=\"preconnect\">",
      "<link rel=\"stylesheet\" href=\"a.css\" title=\"T\" media=\"print\">",
      "<link rel=\"stylesheet\" href=\"a>b\">", "<link rel=\"stylesheet\" href=\"a<b\">",
      "<link rel=\"stylesheet\" href=\"a\u0000b\">",
      "<link rel=\"stylesheet\" href=\"a.css\"", "<link rel=\"stylesheet\" href=\"a.css", "<link rel=\"stylesheet\"  href=\"a.css\">",
      "<link rel=\"stylesheet preconnect\" href=\"a.css\">", "<link", "x<link ",
      "<meta>", "<meta http-equiv=\"refresh\" content=\"0\">", "<meta name=\"referrer\" content=\"x\">",
      "<meta name=\"viewport\" content=\"a\" http-equiv=\"refresh\">", "<META name=\"viewport\" content=\"a\">",
      "<meta name=\"viewport-x\" content=\"a\">",
      "<iframe>", "</iframe>", "<IFRAME srcdoc=x>", "a</IfRaMe",
    )) assertFalse(html, FluidWebSeal.isSealed(html))
  }

  /** Prodotto di nomi, separatori, modi di scrivere `rel`, chiusure e contesti: circa centomila documenti. */
  @Test fun systematicDisguisesNeverLeaveARawElement() {
    val names = listOf("link", "LINK", "LiNk", "lInK")
    val separators = listOf(" ", "\t", "\n", "\u000C", "\r", "/", " / ", "\u0000", "", " \u0000")
    val rels = listOf("rel=preconnect", "rel=\"preconnect\"", "rel='preconnect'", "REL=PRECONNECT", "rel = preconnect",
      "rel=\"&#112;reconnect\"", "rel=\"dns-prefetch preconnect\"", "rel=\"stylesheet preconnect\"",
      "rel=stylesheet rel=preconnect", "rel=preconnect rel=stylesheet", "rel=\"\tpreconnect\n\"", "rel=stylesheet",
      "rel=\"stylesheet\"", "rel=preload as=image")
    val hrefs = listOf("href=$origin/x", "href=\"$origin/x\"", "href='//tracker.invalid'")
    val closings = listOf(">", "/>", "", " x=\"", " x='", "\u0000>")
    val contexts = listOf("" to "", "<svg>" to "</svg>", "<math><mi>" to "</mi></math>", "<!--" to "", "<![CDATA[" to "",
      "<template>" to "</template>", "<noscript>" to "</noscript>", "<body><p>a" to "</p>", "<title>" to "</title>",
      "<style>" to "</style>", "<textarea>" to "", "<table><tr>" to "</tr></table>", "<div title=\"" to "\">",
      "<div title='" to "'>", "<iframe srcdoc=\"" to "\"></iframe>", "<p " to ">")
    var count = 0
    for (name in names) for (separator in separators) for (rel in rels) for (href in hrefs) for (closing in closings) {
      val tag = "<$name$separator$rel $href$closing"
      for ((before, after) in contexts) {
        assertClosed(before + tag + after)
        count++
      }
    }
    assertTrue(count > 100_000)
  }

  /** Frammenti accostati a caso, con seme fisso: cerca le combinazioni che un elenco scritto a mano non prevede. */
  @Test fun randomFragmentsNeverLeaveARawElement() {
    val fragments = listOf("<", ">", "</", "/", " ", "\t", "\n", "\u000C", "\u0000", "\"", "'", "=", "&", ";", "`", "-", "!",
      "link", "LINK", "meta", "MeTa", "iframe", "IFRAME", "basefont", "noframes", "rel", "href", "http-equiv", "name",
      "content", "srcdoc", "media", "title", "type", "preconnect", "stylesheet", "dns-prefetch", "viewport", "refresh",
      "x-dns-prefetch-control", "on", "$origin/a.css", "//tracker.invalid", "&#112;", "&amp;", "&lt;", "&gt;", "&quot;", "&copy",
      "<!--", "-->", "<![CDATA[", "]]>", "<svg>", "</svg>", "<style>", "</style>", "<template>", "<p>", "text/css",
      "<link rel=\"stylesheet\" href=\"", "<meta name=\"viewport\" content=\"", "<link ", "<meta ", "<iframe ", "</iframe>")
    val random = Random(20261109)
    repeat(40_000) {
      val html = buildString { repeat(1 + random.nextInt(40)) { append(fragments[random.nextInt(fragments.size)]) } }
      assertClosed(html)
    }
  }

  /** Un documento al limite, costruito per far rileggere piu' volte lo stesso testo, resta una passata. */
  @Test(timeout = 120_000) fun adversarialDocumentAtTheSizeLimitStaysLinear() {
    val limit = FluidWebContentDefaults.MaxHtmlCharacters
    fun fill(unit: String) = buildString(limit) { while (length + unit.length <= limit) append(unit) }
    for (html in listOf(
      fill("<link a=\"<link b='"),
      fill("<link rel=stylesheet href=x "),
      fill("<meta name=viewport content="),
      "<link a=\"" + fill("<link b='<meta c "),
      fill("<link rel=stylesheet href=\"$origin/a.css\">"),
      fill("<iframe srcdoc=x></iframe>"),
      fill("<"),
    )) {
      val sealed = FluidWebSeal.seal(html)
      assertTrue(FluidWebSeal.isSealed(sealed))
      assertTrue("La crescita resta proporzionale", sealed.length <= html.length * 3L + 1024)
    }
  }
}
