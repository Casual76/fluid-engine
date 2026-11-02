package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il parser e' il pezzo che le app avevano scritto ognuna per conto suo e che nessuna provava:
 * qui si prova tutto sul computer, comprese le letture del testo **a meta'** che uno stream produce.
 */
class FluidMarkdownParserTest {

  private fun parse(text: String, options: FluidMarkdownOptions = FluidMarkdownOptions()) = FluidMarkdownParser.parse(text, options)

  private fun inline(text: String) = FluidMarkdownParser.inlines(text)

  private fun t(text: String) = FluidMdInline.Text(text)

  @Test
  fun `un paragrafo di piu righe e' uno solo, unito da uno spazio`() {
    val blocks = parse("Prima riga\nseconda riga\n\nTerzo")
    assertEquals(2, blocks.size)
    assertEquals(listOf(t("Prima riga seconda riga")), (blocks[0] as FluidMdBlock.Paragraph).inlines)
    assertEquals(listOf(t("Terzo")), (blocks[1] as FluidMdBlock.Paragraph).inlines)
  }

  @Test
  fun `chi vuole gli a capo del modello li tiene`() {
    val blocks = parse("uno\ndue", FluidMarkdownOptions(softBreak = "\n"))
    assertEquals(listOf(t("uno\ndue")), (blocks.single() as FluidMdBlock.Paragraph).inlines)
  }

  @Test
  fun `i titoli tengono il livello e perdono i cancelletti di chiusura`() {
    val h = parse("## Estate ##").single() as FluidMdBlock.Heading
    assertEquals(2, h.level)
    assertEquals(listOf(t("Estate")), h.inlines)
    assertEquals(6, (parse("###### sei").single() as FluidMdBlock.Heading).level)
    // Sette cancelletti non sono un titolo.
    assertTrue(parse("####### sette").single() is FluidMdBlock.Paragraph)
    // E senza lo spazio nemmeno: "#hashtag" e' un hashtag.
    assertTrue(parse("#hashtag").single() is FluidMdBlock.Paragraph)
  }

  @Test
  fun `un cancelletto che e' parte del titolo non si toglie`() {
    val h = parse("## Imparare C#").single() as FluidMdBlock.Heading
    assertEquals(listOf(t("Imparare C#")), h.inlines)
    // Staccato da uno spazio e' la chiusura, e va via.
    assertEquals(listOf(t("Imparare C#")), (parse("## Imparare C# ##").single() as FluidMdBlock.Heading).inlines)
  }

  @Test
  fun `grassetto corsivo barrato e codice in linea`() {
    assertEquals(listOf(t("a "), FluidMdInline.Bold(listOf(t("b"))), t(" c")), inline("a **b** c"))
    assertEquals(listOf(FluidMdInline.Italic(listOf(t("x")))), inline("*x*"))
    assertEquals(listOf(FluidMdInline.Italic(listOf(t("x")))), inline("_x_"))
    assertEquals(listOf(FluidMdInline.Strike(listOf(t("no")))), inline("~~no~~"))
    assertEquals(listOf(t("usa "), FluidMdInline.Code("map()"), t(" qui")), inline("usa `map()` qui"))
  }

  @Test
  fun `il corsivo dentro il grassetto e il grassetto dentro il corsivo`() {
    assertEquals(
      listOf(FluidMdInline.Bold(listOf(t("a "), FluidMdInline.Italic(listOf(t("b")))))),
      inline("**a *b***"),
    )
    assertEquals(
      listOf(FluidMdInline.Italic(listOf(t("a "), FluidMdInline.Bold(listOf(t("b"))), t(" c")))),
      inline("*a **b** c*"),
    )
    assertEquals(listOf(FluidMdInline.Bold(listOf(FluidMdInline.Italic(listOf(t("tutto")))))), inline("***tutto***"))
  }

  @Test
  fun `il codice in linea non si guarda dentro`() {
    assertEquals(listOf(FluidMdInline.Code("**no**")), inline("`**no**`"))
    assertEquals(listOf(FluidMdInline.Code("a`b")), inline("``a`b``"))
  }

  @Test
  fun `un segno aperto e mai chiuso resta testo, come uno stream a meta'`() {
    assertEquals(listOf(t("**ciao")), inline("**ciao"))
    assertEquals(listOf(t("*ciao")), inline("*ciao"))
    assertEquals(listOf(t("`ciao")), inline("`ciao"))
    assertEquals(listOf(t("~~ciao")), inline("~~ciao"))
    assertEquals(listOf(t("[testo](http")), inline("[testo](http"))
    // E il testo che c'e' prima del segno aperto si disegna comunque.
    assertEquals(listOf(t("a "), FluidMdInline.Bold(listOf(t("b"))), t(" **c")), inline("a **b** **c"))
  }

  @Test
  fun `un asterisco circondato da spazi e' una moltiplicazione, non un corsivo`() {
    assertEquals(listOf(t("2 * 3 * 4")), inline("2 * 3 * 4"))
  }

  @Test
  fun `il trattino basso dentro una parola e' un nome, non un corsivo`() {
    assertEquals(listOf(t("snake_case_name")), inline("snake_case_name"))
    assertEquals(listOf(t("il "), FluidMdInline.Bold(listOf(t("init"))), t(" qui")), inline("il __init__ qui"))
  }

  @Test
  fun `i link con testo, gli indirizzi nudi e il punto che chiude la frase`() {
    assertEquals(
      listOf(t("vai su "), FluidMdInline.Link(listOf(t("OpenRouter")), "https://openrouter.ai"), t(" ora")),
      inline("vai su [OpenRouter](https://openrouter.ai) ora"),
    )
    val bare = inline("Guarda https://example.com/a?b=1.")
    assertEquals(t("Guarda "), bare[0])
    assertEquals(FluidMdInline.Link(listOf(t("https://example.com/a?b=1")), "https://example.com/a?b=1"), bare[1])
    assertEquals(t("."), bare[2])
    // Un'immagine non si disegna: resta il suo testo alternativo, come un link.
    assertEquals(listOf(FluidMdInline.Link(listOf(t("logo")), "https://x.it/l.png")), inline("![logo](https://x.it/l.png)"))
  }

  @Test
  fun `la barra rovescia toglie il significato al segno`() {
    assertEquals(listOf(t("*non corsivo*")), inline("\\*non corsivo\\*"))
  }

  @Test
  fun `i marcatori dei chip spariscono quando l'assistente lo chiede`() {
    val chips = FluidMarkdownOptions(hideChipMarkers = true)
    assertEquals(listOf(t("Apri la pagina  ora")), FluidMarkdownParser.inlines("Apri la pagina [[page:voti]] ora", chips))
    assertEquals(listOf(t("x ")), FluidMarkdownParser.inlines("x [[a]]", chips))
  }

  @Test
  fun `di serie le parentesi doppie restano testo, come in una nota`() {
    // Un collegamento fra pagine di una nota ("[[Idee]]") non e' un chip: un renderer generico non lo mangia.
    assertEquals(listOf(t("vedi [[Idee]] e [[page:voti]]")), inline("vedi [[Idee]] e [[page:voti]]"))
    assertFalse(FluidMarkdownOptions().hideChipMarkers)
  }

  @Test
  fun `il testo senza segni ha le stesse opzioni del parser`() {
    val md = "Pioverà alle 18. [[radar]]"
    assertEquals("Pioverà alle 18. [[radar]]", FluidMarkdownParser.plainText(md))
    assertEquals("Pioverà alle 18.", FluidMarkdownParser.plainText(md, options = FluidMarkdownOptions(hideChipMarkers = true)).trim())
  }

  @Test
  fun `un elenco puntato`() {
    val list = parse("- uno\n- due\n* tre").single() as FluidMdBlock.ListBlock
    assertFalse(list.ordered)
    assertEquals(3, list.items.size)
    assertEquals(listOf(t("tre")), list.items[2].inlines)
  }

  @Test
  fun `un elenco numerato che comincia da tre resta da tre`() {
    val list = parse("3. c\n4. d").single() as FluidMdBlock.ListBlock
    assertTrue(list.ordered)
    assertEquals(3, list.start)
    assertEquals(2, list.items.size)
  }

  @Test
  fun `un numero davanti a una frase non apre un elenco in mezzo a un paragrafo`() {
    // "2024." in testa a una riga che continua un paragrafo e' una data, non la voce 2024.
    val blocks = parse("Nel\n2024. un anno")
    assertEquals(1, blocks.size)
    assertTrue(blocks.single() is FluidMdBlock.Paragraph)
    // Ma "1." interrompe: un elenco che segue due punti, senza riga vuota, e' un elenco.
    val withList = parse("Ecco:\n1. a\n2. b")
    assertEquals(2, withList.size)
    assertTrue(withList[1] is FluidMdBlock.ListBlock)
  }

  @Test
  fun `le voci piu rientrate sono figlie della voce sopra`() {
    val list = parse("- a\n  - b\n  - c\n- d").single() as FluidMdBlock.ListBlock
    assertEquals(2, list.items.size)
    val children = list.items[0].children.single() as FluidMdBlock.ListBlock
    assertEquals(2, children.items.size)
    assertEquals(listOf(t("c")), children.items[1].inlines)
    assertTrue(list.items[1].children.isEmpty())
  }

  @Test
  fun `una riga vuota fra due voci non chiude l'elenco`() {
    val list = parse("- a\n\n- b").single() as FluidMdBlock.ListBlock
    assertEquals(2, list.items.size)
  }

  @Test
  fun `un blocco di codice conserva le righe e la lingua`() {
    val code = parse("```kotlin\nval a = 1\n\n  val b = 2\n```").single() as FluidMdBlock.CodeBlock
    assertEquals("kotlin", code.language)
    assertEquals("val a = 1\n\n  val b = 2", code.code)
    assertTrue(code.closed)
  }

  @Test
  fun `un blocco di codice senza chiusura e' gia' un blocco, non tre apici nudi`() {
    val blocks = parse("Ecco:\n\n```js\nlet x")
    assertEquals(2, blocks.size)
    val code = blocks[1] as FluidMdBlock.CodeBlock
    assertEquals("js", code.language)
    assertEquals("let x", code.code)
    assertFalse(code.closed)
  }

  @Test
  fun `dentro il codice niente e' markdown`() {
    val code = parse("```\n# non un titolo\n- non un elenco\n**no**\n```").single() as FluidMdBlock.CodeBlock
    assertEquals("# non un titolo\n- non un elenco\n**no**", code.code)
  }

  @Test
  fun `una citazione contiene blocchi`() {
    val quote = parse("> uno\n> - a\n> - b").single() as FluidMdBlock.Quote
    assertEquals(2, quote.blocks.size)
    assertTrue(quote.blocks[0] is FluidMdBlock.Paragraph)
    assertTrue(quote.blocks[1] is FluidMdBlock.ListBlock)
  }

  @Test
  fun `una tabella con allineamenti e celle con segni`() {
    val md = "| Nome | Voto | Media |\n|:---|:---:|---:|\n| **Ada** | 8 | 7,5 |\n| Bo | 6 | 6 |"
    val table = parse(md).single() as FluidMdBlock.Table
    assertEquals(3, table.columns)
    assertEquals(listOf(FluidMdAlign.Start, FluidMdAlign.Center, FluidMdAlign.End), table.aligns)
    assertEquals(listOf(t("Nome")), table.header[0])
    assertEquals(2, table.rows.size)
    assertEquals(listOf(FluidMdInline.Bold(listOf(t("Ada")))), table.rows[0][0])
  }

  @Test
  fun `l'intestazione di una tabella senza la riga dei trattini e' ancora un paragrafo`() {
    // Lo stream e' arrivato fin li': diventa tabella quando la riga arriva.
    val partial = parse("| a | b |")
    assertTrue(partial.single() is FluidMdBlock.Paragraph)
    val whole = parse("| a | b |\n|---|---|")
    assertTrue(whole.single() is FluidMdBlock.Table)
  }

  @Test
  fun `una pipe in un paragrafo con una riga di trattini non e' una tabella a un numero sbagliato di colonne`() {
    val blocks = parse("a | b\n---")
    assertTrue(blocks[0] is FluidMdBlock.Paragraph)
    assertEquals(FluidMdBlock.Rule, blocks[1])
  }

  @Test
  fun `le righe orizzontali`() {
    assertEquals(listOf(FluidMdBlock.Rule), parse("---"))
    assertEquals(listOf(FluidMdBlock.Rule), parse("* * *"))
    // Un solo trattino non e' una riga ne' un elenco senza testo.
    assertTrue(parse("-").single() is FluidMdBlock.Paragraph)
  }

  @Test
  fun `il testo senza segni per la voce salta il codice e tiene una voce per riga`() {
    val md = "## Titolo\n\nUn **testo** con `codice`.\n\n- uno\n- due\n\n```\nlet x\n```\n\n[sito](https://a.it)"
    assertEquals("Titolo\nUn testo con codice.\nuno\ndue\nsito", FluidMarkdownParser.plainText(md))
    assertEquals("Titolo\nUn testo con codice.\n- uno\n- due\nlet x\nsito", FluidMarkdownParser.plainText(md, includeCode = true, bullets = true))
  }

  @Test
  fun `un testo vuoto o fatto di soli spazi non ha blocchi`() {
    assertTrue(parse("").isEmpty())
    assertTrue(parse("  \n \n").isEmpty())
  }

  @Test
  fun `gli a capo di Windows valgono come gli altri`() {
    val blocks = parse("a\r\n\r\nb")
    assertEquals(2, blocks.size)
  }

  // ---- le misure dei titoli ------------------------------------------------------------------------

  private fun style(headings: List<TextStyle> = emptyList()) = FluidMarkdownStyle(
    body = TextStyle(fontSize = 16.sp),
    heading = TextStyle(fontSize = 20.sp),
    subheading = TextStyle(fontSize = 17.sp),
    code = TextStyle(fontSize = 13.sp),
    textColor = Color.Black,
    mutedColor = Color.Gray,
    linkColor = Color.Blue,
    markerColor = Color.Blue,
    codeBackground = Color.LightGray,
    quoteBar = Color.Gray,
    headings = headings,
  )

  @Test
  fun `di serie i titoli hanno due misure sole`() {
    val style = style()
    assertEquals(style.heading, style.headingFor(1))
    assertEquals(style.heading, style.headingFor(2))
    assertEquals(style.subheading, style.headingFor(3))
    assertEquals(style.subheading, style.headingFor(6))
  }

  @Test
  fun `con le misure per livello ogni titolo ha la sua e l'ultima vale per i piu' profondi`() {
    val levels = listOf(TextStyle(fontSize = 28.sp), TextStyle(fontSize = 22.sp), TextStyle(fontSize = 18.sp))
    val style = style(levels)
    assertEquals(levels[0], style.headingFor(1))
    assertEquals(levels[1], style.headingFor(2))
    assertEquals(levels[2], style.headingFor(3))
    assertEquals(levels[2], style.headingFor(4))
    assertEquals(levels[2], style.headingFor(6))
    // Un livello fuori scala (0, o oltre il sesto) non fa uscire dalla lista.
    assertEquals(levels[0], style.headingFor(0))
    assertEquals(levels[2], style.headingFor(9))
  }
}
