package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le iniziali di un nome, per grafemi: i casi che un `name[0]` sbaglia.
 *
 * Gli emoji sono scritti con gli escape `\u` perche' un test che dipende dalla codifica del file
 * non prova niente il giorno in cui qualcuno apre il file con un altro editor.
 */
class FluidMonogramTest {

  private val family = "👨‍👩‍👧" // 👨‍👩‍👧
  private val flagItaly = "🇮🇹" // 🇮🇹
  private val flagFrance = "🇫🇷"
  private val smile = "🙂"
  private val wave = "👋"
  private val waveSkin = "👋🏽" // 👋🏽
  private val keycap = "1️⃣"

  // --- Latino --------------------------------------------------------------------------------------

  @Test
  fun `first and last initial of a normal name`() {
    assertEquals("AR", fluidMonogram("Anna Rossi"))
    assertEquals("MR", fluidMonogram("mario rossi"))
  }

  @Test
  fun `a single word gives a single letter`() {
    assertEquals("A", fluidMonogram("Anna"))
    assertEquals("P", fluidMonogram("pampa"))
  }

  @Test
  fun `three words give first and last not first and second`() {
    assertEquals("AR", fluidMonogram("Anna Maria Rossi"))
    assertEquals("GV", fluidMonogram("Giuseppe Maria Antonio Verdi"))
  }

  @Test
  fun `the letters are capped by maxLetters`() {
    assertEquals("A", fluidMonogram("Anna Maria Rossi", maxLetters = 1))
    assertEquals("AMR", fluidMonogram("Anna Maria Rossi", maxLetters = 3))
    assertEquals("AMB", fluidMonogram("Anna Maria Rossi Bianchi", maxLetters = 3))
    assertEquals("A", fluidMonogram("Anna", maxLetters = 3))
    assertEquals("A", fluidMonogram("Anna Rossi", maxLetters = 0))
  }

  @Test
  fun `hyphens and apostrophes do not split a word`() {
    assertEquals("JD", fluidMonogram("Jean-Pierre Dupont"))
    assertEquals("D", fluidMonogram("D'Angelo"))
    assertEquals("OB", fluidMonogram("O'Brien Brown"))
  }

  @Test
  fun `extra spaces tabs and newlines are only separators`() {
    assertEquals("AR", fluidMonogram("  Anna \t\n  Rossi  "))
    assertEquals("AR", fluidMonogram("Anna Rossi"))
    assertEquals("AR", fluidMonogram("Anna　Rossi"))
  }

  @Test
  fun `quotes and parentheses around a word are skipped`() {
    assertEquals("AR", fluidMonogram("\"Anna\" Rossi"))
    assertEquals("UR", fluidMonogram("(Ufficio) Rossi"))
    assertEquals("A", fluidMonogram("-Anna"))
  }

  @Test
  fun `a comma separated surname first is read in the order written`() {
    assertEquals("RA", fluidMonogram("Rossi, Anna"))
  }

  // --- Accenti -------------------------------------------------------------------------------------

  @Test
  fun `a precomposed accent stays`() {
    assertEquals("ÉB", fluidMonogram("Émile Bernard"))
    assertEquals("ÖÅ", fluidMonogram("österberg Åkesson"))
  }

  @Test
  fun `a decomposed accent is one letter - base and combining mark kept together`() {
    val decomposed = "Émile Bernard" // E + accento acuto combinante
    val m = fluidMonogram(decomposed)
    assertEquals("ÉB", m)
    assertEquals(2, graphemes(m).size)
  }

  @Test
  fun `the sharp s upper-cases to a single letter`() {
    assertEquals("S", fluidMonogram("ßonne"))
  }

  @Test
  fun `a lower-case initial is upper-cased including non ASCII`() {
    assertEquals("ÀÈ", fluidMonogram("àgata èva"))
  }

  // --- Nomi di un carattere, numeri, indirizzi -----------------------------------------------------

  @Test
  fun `one character names`() {
    assertEquals("A", fluidMonogram("A"))
    assertEquals("A", fluidMonogram("a"))
    assertEquals("7", fluidMonogram("7"))
    assertEquals("AB", fluidMonogram("A B"))
  }

  @Test
  fun `digits count as initials`() {
    assertEquals("1", fluidMonogram("123"))
    assertEquals("A1", fluidMonogram("Agent 1"))
  }

  @Test
  fun `an address gives its local part split on dots and underscores`() {
    assertEquals("AR", fluidMonogram("anna.rossi@example.com"))
    assertEquals("AR", fluidMonogram("anna_rossi@example.com"))
    assertEquals("I", fluidMonogram("info@example.com"))
    assertEquals("AR", fluidMonogram("anna.rossi+lavoro@example.com"))
  }

  @Test
  fun `a handle and an address without local part`() {
    assertEquals("M", fluidMonogram("@mano"))
    // Senza parte locale vale il dominio, diviso come ogni altro nome.
    assertEquals("EC", fluidMonogram("@example.com"))
  }

  // --- Emoji ---------------------------------------------------------------------------------------

  @Test
  fun `an emoji is one grapheme not half a surrogate pair`() {
    assertEquals(smile, fluidMonogram(smile))
    assertEquals(smile + "A", fluidMonogram("$smile Anna"))
    assertEquals(1, graphemes(fluidMonogram(smile)).size)
    // Mai una meta' di coppia surrogata.
    assertTrue(fluidMonogram(smile).codePointCount(0, fluidMonogram(smile).length) == 1)
  }

  @Test
  fun `a ZWJ family is one grapheme`() {
    val m = fluidMonogram("$family Famiglia")
    assertEquals(family + "F", m)
    assertEquals(2, graphemes(m).size)
  }

  @Test
  fun `a flag is one grapheme made of two regional indicators`() {
    assertEquals(flagItaly, fluidMonogram(flagItaly))
    assertEquals(flagItaly + "I", fluidMonogram("$flagItaly Italia"))
    // Due bandiere attaccate sono due grafemi, non uno solo da quattro.
    assertEquals(listOf(flagItaly, flagFrance), graphemes(flagItaly + flagFrance))
  }

  @Test
  fun `a skin tone modifier stays with its emoji`() {
    assertEquals(waveSkin, fluidMonogram(waveSkin))
    assertEquals(1, graphemes(waveSkin).size)
    assertEquals(listOf(wave, wave), graphemes(wave + wave))
  }

  @Test
  fun `a keycap sequence is one grapheme`() {
    assertEquals(keycap, fluidMonogram(keycap))
  }

  // --- Scritture non latine, RTL ---------------------------------------------------------------------

  @Test
  fun `cyrillic greek and their case`() {
    assertEquals("ИП", fluidMonogram("иван петров"))
    assertEquals("ΑΠ", fluidMonogram("αννα παπαδοπουλου"))
  }

  @Test
  fun `arabic and hebrew have no case and keep their logical order`() {
    assertEquals("مع", fluidMonogram("محمد علي"))
    assertEquals("דכ", fluidMonogram("דוד כהן"))
  }

  @Test
  fun `a name without spaces in a script without spaces gives its first character`() {
    assertEquals("田", fluidMonogram("田中太郎"))
    assertEquals("한", fluidMonogram("한국어"))
  }

  @Test
  fun `devanagari keeps a consonant with its vowel sign`() {
    val name = "कि" // क + ि
    assertEquals(1, graphemes(name).size)
    assertEquals(name, fluidMonogram(name))
  }

  @Test
  fun `bidi controls and zero width characters are removed`() {
    val rlo = "‮"
    val lrm = "‎"
    val zwsp = "​"
    assertEquals("AR", fluidMonogram("${rlo}Anna$lrm ${zwsp}Rossi"))
    assertEquals("A", fluidMonogram("⁧Anna⁩"))
    // Solo controlli: niente da scrivere.
    assertEquals("", fluidMonogram("$rlo$lrm$zwsp"))
  }

  @Test
  fun `control characters are dropped`() {
    assertEquals("A", fluidMonogram("\u0000A\u0007"))
  }

  // --- Vuoto e simboli -------------------------------------------------------------------------------

  @Test
  fun `empty blank and symbol-only names give nothing so the avatar shows the person glyph`() {
    assertEquals("", fluidMonogram(""))
    assertEquals("", fluidMonogram("   "))
    assertEquals("", fluidMonogram("---"))
    assertEquals("", fluidMonogram("& + ( ) !!!"))
    assertEquals("", fluidMonogram("@"))
  }

  @Test
  fun `a symbol-only word is skipped but the others count`() {
    assertEquals("AR", fluidMonogram("Anna & Rossi"))
    assertEquals("AR", fluidMonogram("Anna - Rossi"))
  }

  @Test
  fun `a very long name does not hang and still gives its initials`() {
    val long = "Anna " + "x".repeat(100_000) + " Rossi"
    // Oltre i primi 512 caratteri non si guarda: «Rossi» e' fuori, l'ultima parola vista e' la x.
    assertEquals("AX", fluidMonogram(long))
  }

  // --- Mosaico ------------------------------------------------------------------------------------

  @Test
  fun `stack cells - 2 3 and 4 people fill 2 3 and 4 cells with no overflow cell`() {
    assertEquals(FluidAvatarStackLayout(cells = 2, visible = 2, overflowCell = -1), fluidAvatarStackCells(2, 4))
    assertEquals(FluidAvatarStackLayout(cells = 3, visible = 3, overflowCell = -1), fluidAvatarStackCells(3, 4))
    assertEquals(FluidAvatarStackLayout(cells = 4, visible = 4, overflowCell = -1), fluidAvatarStackCells(4, 4))
  }

  @Test
  fun `stack cells - past the cap the last cell says how many are missing`() {
    assertEquals(FluidAvatarStackLayout(cells = 4, visible = 3, overflowCell = 3), fluidAvatarStackCells(7, 4))
    assertEquals(FluidAvatarStackLayout(cells = 4, visible = 3, overflowCell = 3), fluidAvatarStackCells(5, 4))
    assertEquals(FluidAvatarStackLayout(cells = 3, visible = 2, overflowCell = 2), fluidAvatarStackCells(9, 3))
  }

  @Test
  fun `stack cap is kept between 2 and 4`() {
    assertEquals(2, fluidAvatarStackCells(10, 0).cells)
    assertEquals(4, fluidAvatarStackCells(10, 99).cells)
  }

  @Test
  fun `stack description names the visible people and says the rest in the app's words`() {
    val names = listOf("Anna", "Bruno", "Carla", "Dario", "Elena", "Fabio")
    assertEquals("Anna, Bruno, Carla e altri 3", fluidAvatarStackDescription(names))
    assertEquals("Anna, Bruno", fluidAvatarStackDescription(names.take(2)))
    assertEquals(
      "Anna, Bruno, Carla and 3 more",
      fluidAvatarStackDescription(names, labels = FluidLabels.english()),
    )
    assertEquals("", fluidAvatarStackDescription(emptyList()))
  }
}
