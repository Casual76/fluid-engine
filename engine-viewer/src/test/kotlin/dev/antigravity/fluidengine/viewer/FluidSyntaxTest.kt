package dev.antigravity.fluidengine.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** I colori di sintassi: le lingue, i ruoli, gli stati fra le righe e il ripiego. */
class FluidSyntaxTest {

  /** I pezzi di [line] come testo → ruolo, per leggere i test. */
  private fun roles(language: String, vararg lines: String): List<List<Pair<String, String>>> {
    val spans = FluidSyntax.highlight(lines.toList(), language)
    return lines.indices.map { i -> spans[i].map { lines[i].substring(it.start, it.end) to it.role.toString() } }
  }

  private fun flat(language: String, vararg lines: String) = roles(language, *lines).flatten()

  @Test
  fun `the language comes from the extension then from the mime`() {
    assertEquals(FluidSyntax.Kotlin, FluidSyntax.languageFor("Main.kt"))
    assertEquals(FluidSyntax.Kotlin, FluidSyntax.languageFor("build.gradle.kts"))
    assertEquals(FluidSyntax.Groovy, FluidSyntax.languageFor("build.gradle"))
    assertEquals(FluidSyntax.Json, FluidSyntax.languageFor("package.JSON"))
    assertEquals(FluidSyntax.Markdown, FluidSyntax.languageFor("README.md"))
    assertEquals(FluidSyntax.Html, FluidSyntax.languageFor("index.htm"))
    assertEquals(FluidSyntax.Ini, FluidSyntax.languageFor(".editorconfig"))
    assertEquals(FluidSyntax.Ini, FluidSyntax.languageFor("gradle.properties"))
    assertEquals(FluidSyntax.Json, FluidSyntax.languageFor("risposta", "application/json; charset=utf-8"))
    assertEquals(FluidSyntax.Json, FluidSyntax.languageFor("risposta", "application/problem+json"))
    assertEquals(FluidSyntax.Xml, FluidSyntax.languageFor("feed", "application/atom+xml"))
    assertNull(FluidSyntax.languageFor("nota.txt"))
    assertNull(FluidSyntax.languageFor("dati", "application/octet-stream"))
    assertTrue(FluidSyntax.languages.containsAll(listOf("kotlin", "json", "xml", "markdown")))
  }

  @Test
  fun `an unknown language falls back to plain text`() {
    assertNull(FluidSyntax.highlighter(null))
    assertNull(FluidSyntax.highlighter("cobol"))
    assertEquals(listOf(emptyList<FluidSyntaxSpan>(), emptyList()), FluidSyntax.highlight(listOf("a", "b"), "cobol"))
  }

  @Test
  fun `kotlin keywords strings numbers comments annotations and types`() {
    val spans = flat(FluidSyntax.Kotlin, "@Composable fun Saluta(nome: String, n: Int = 0x1F) = \"ciao \$nome\" // fine")
    assertEquals(
      listOf(
        "@Composable" to "Attribute", "fun" to "Keyword", "Saluta" to "Type", "String" to "Type", "Int" to "Type",
        "0x1F" to "Number", "\"ciao \$nome\"" to "String", "// fine" to "Comment",
      ),
      spans,
    )
  }

  @Test
  fun `a block comment carries over lines`() {
    val lines = roles(FluidSyntax.Kotlin, "val a = 1 /* inizio", "tutto commento", "fine */ val b = 2")
    assertEquals(listOf("val" to "Keyword", "1" to "Number", "/* inizio" to "Comment"), lines[0])
    assertEquals(listOf("tutto commento" to "Comment"), lines[1])
    assertEquals(listOf("fine */" to "Comment", "val" to "Keyword", "2" to "Number"), lines[2])
  }

  @Test
  fun `a raw string carries over lines in kotlin and a template in javascript`() {
    val kotlin = roles(FluidSyntax.Kotlin, "val s = \"\"\"prima", "seconda", "terza\"\"\" + 1")
    assertEquals("\"\"\"prima" to "String", kotlin[0].last())
    assertEquals(listOf("seconda" to "String"), kotlin[1])
    assertEquals(listOf("terza\"\"\"" to "String", "1" to "Number"), kotlin[2])
    val js = roles(FluidSyntax.JavaScript, "const t = `a", "b` + x")
    assertEquals("`a" to "String", js[0].last())
    assertEquals("b`" to "String", js[1].first())
  }

  @Test
  fun `the state is the one of the previous line`() {
    val highlighter = FluidSyntax.highlighter(FluidSyntax.Kotlin)!!
    val first = highlighter.line("/* aperto")
    assertTrue(first.endState != 0)
    assertEquals(listOf(FluidSyntaxSpan(0, 6, FluidSyntaxRole.Comment)), highlighter.line("ancora", first.endState).spans)
    assertEquals(0, highlighter.line("chiuso */", first.endState).endState)
  }

  @Test
  fun `json keys values numbers and literals`() {
    assertEquals(
      listOf("\"nome\"" to "Attribute", "\"Anna\"" to "String", "\"anni\"" to "Attribute", "-12.5e3" to "Number", "true" to "Keyword", "null" to "Keyword"),
      flat(FluidSyntax.Json, "{\"nome\": \"Anna\", \"anni\" : -12.5e3, \"ok\": [true, null]}").filterNot { it.first == "\"ok\"" },
    )
  }

  @Test
  fun `xml tags attributes values comments and entities`() {
    val lines = roles(FluidSyntax.Xml, "<?xml version=\"1.0\"?>", "<a href=\"x\">uno &amp; due</a> <!-- nota", "fine -->")
    assertEquals(listOf("<?xml" to "Keyword", "version" to "Attribute", "\"1.0\"" to "String", "?>" to "Tag"), lines[0])
    assertEquals(
      listOf("<a" to "Tag", "href" to "Attribute", "\"x\"" to "String", ">" to "Tag", "&amp;" to "Keyword", "</a" to "Tag", ">" to "Tag", "<!--" to "Comment", " nota" to "Comment"),
      lines[1],
    )
    assertEquals(listOf("fine -->" to "Comment"), lines[2])
  }

  @Test
  fun `a tag that spans lines keeps its attributes`() {
    val lines = roles(FluidSyntax.Xml, "<View", "  android:id=\"@+id/a\"", "  />")
    assertEquals(listOf("<View" to "Tag"), lines[0])
    assertEquals(listOf("android:id" to "Attribute", "\"@+id/a\"" to "String"), lines[1])
    assertEquals(listOf("/>" to "Tag"), lines[2])
  }

  @Test
  fun `markdown headings fences inline code emphasis links and lists`() {
    val lines = roles(
      FluidSyntax.Markdown,
      "# Titolo", "- voce con `codice` e **forte** e *corsivo*", "1. vedi [qui](https://x.it)", "```kotlin", "val # non è un titolo", "```", "> citazione",
    )
    assertEquals(listOf("# Titolo" to "Heading"), lines[0])
    assertEquals(listOf("-" to "Keyword", "`codice`" to "Code", "**forte**" to "Emphasis", "*corsivo*" to "Emphasis"), lines[1])
    assertEquals(listOf("1." to "Keyword", "[qui](https://x.it)" to "Link"), lines[2])
    assertEquals(listOf("```kotlin" to "Code"), lines[3])
    assertEquals(listOf("val # non è un titolo" to "Code"), lines[4])
    assertEquals(listOf("```" to "Code"), lines[5])
    assertEquals(listOf("> citazione" to "Comment"), lines[6])
  }

  @Test
  fun `yaml keys comments literals and numbers`() {
    val lines = roles(FluidSyntax.Yaml, "---", "nome: Anna # commento", "- attivo: true", "  porta: 8080", "  url: \"http://x\"")
    assertEquals(listOf("---" to "Keyword"), lines[0])
    assertEquals(listOf("nome" to "Attribute", "# commento" to "Comment"), lines[1])
    assertEquals(listOf("-" to "Keyword", "attivo" to "Attribute", "true" to "Keyword"), lines[2])
    assertEquals(listOf("porta" to "Attribute", "8080" to "Number"), lines[3])
    assertEquals(listOf("url" to "Attribute", "\"http://x\"" to "String"), lines[4])
  }

  @Test
  fun `ini sections keys and comments`() {
    val lines = roles(FluidSyntax.Ini, "[server]", "porta = 8080", "; commento", "nome = \"prova\"", "attivo=true")
    assertEquals(listOf("[server]" to "Tag"), lines[0])
    assertEquals(listOf("porta" to "Attribute", "8080" to "Number"), lines[1])
    assertEquals(listOf("; commento" to "Comment"), lines[2])
    assertEquals(listOf("nome" to "Attribute", "\"prova\"" to "String"), lines[3])
    assertEquals(listOf("attivo" to "Attribute", "true" to "Keyword"), lines[4])
  }

  @Test
  fun `python docstrings decorators and comments`() {
    val lines = roles(FluidSyntax.Python, "@dataclass", "def f(x):", "    \"\"\"Una", "    docstring\"\"\"", "    return r'\\d' # fine")
    assertEquals(listOf("@dataclass" to "Attribute"), lines[0])
    assertEquals(listOf("def" to "Keyword"), lines[1])
    assertEquals(listOf("\"\"\"Una" to "String"), lines[2])
    assertEquals(listOf("    docstring\"\"\"" to "String"), lines[3])
    assertEquals(listOf("return" to "Keyword", "r'\\d'" to "String", "# fine" to "Comment"), lines[4])
  }

  @Test
  fun `c preprocessor and rust lifetimes`() {
    assertEquals(listOf("#include <stdio.h>" to "Attribute"), flat(FluidSyntax.C, "#include <stdio.h>"))
    val rust = flat(FluidSyntax.Rust, "fn f<'a>(s: &'a str) -> u8 { 0 }")
    assertTrue(rust.contains("fn" to "Keyword"))
    assertTrue(rust.none { it.second == "String" })
  }

  @Test
  fun `no language ever throws or produces bad spans`() {
    val random = Random(20261122)
    val alphabet = "abcXYZ019 \t\"'`/*#<>!-=:[](){}@&;\\.,_\$è😀\n"
    for (language in FluidSyntax.languages) {
      val highlighter = FluidSyntax.highlighter(language)!!
      var state = 0
      repeat(400) {
        val line = buildString { repeat(random.nextInt(0, 60)) { append(alphabet[random.nextInt(alphabet.length)]) } }
          .replace("\n", "")
        val result = highlighter.line(line, state)
        var last = 0
        for (span in result.spans) {
          assertTrue("$language: $span fuori da «$line»", span.start >= 0 && span.end <= line.length && span.start < span.end)
          assertTrue("$language: pezzi sovrapposti in «$line»: ${result.spans}", span.start >= last)
          last = span.end
        }
        state = result.endState
      }
    }
  }
}
