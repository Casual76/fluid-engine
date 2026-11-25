package dev.antigravity.fluidengine.foundation.text

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.21 · Il confronto dei testi: i pezzi ridanno il testo, l'allineamento e' una sottosequenza comune massima, il tetto tiene. */
class FluidTextDiffTest {

  @Test fun `i pezzi ridanno il testo esatto`() {
    val text = "Ciao, Luca!  Domani alle 8:30\nin via Roma 12 — porta l'acqua.\n\nFine"
    assertEquals(text, FluidTextDiff.words(text).joinToString(""))
    assertEquals(text, FluidTextDiff.lines(text).joinToString(""))
    assertEquals(listOf("Ciao", ",", " ", "Luca", "!"), FluidTextDiff.words("Ciao, Luca!"))
    assertEquals(listOf("a\n", "b\n", "c"), FluidTextDiff.lines("a\nb\nc"))
    assertTrue(FluidTextDiff.words("").isEmpty())
  }

  @Test fun `un esempio - i tratti diversi`() {
    val a = FluidTextDiff.words("il gatto nero dorme")
    val b = FluidTextDiff.words("il cane nero dorme sempre")
    val diff = FluidTextDiff.diff(a, b)!!
    assertEquals(listOf(FluidDiffHunk(2, 3, 2, 3), FluidDiffHunk(7, 7, 7, 9)), diff.hunks)
    assertEquals(4, diff.edits)
  }

  @Test fun `su testi a caso l'allineamento e' una sottosequenza comune di lunghezza massima`() {
    val random = Random(21)
    repeat(300) {
      val a = List(random.nextInt(0, 30)) { "abcde"[random.nextInt(5)].toString() }
      val b = List(random.nextInt(0, 30)) { "abcde"[random.nextInt(5)].toString() }
      val diff = FluidTextDiff.diff(a, b, maxEdits = 100)!!
      var last = -1
      var matched = 0
      for (i in a.indices) {
        val j = diff.matchInB(i)
        if (j < 0) continue
        assertTrue("in avanti", j > last)
        assertEquals(a[i], b[j])
        assertEquals(i, diff.matchInA(j))
        last = j
        matched++
      }
      assertEquals("lunghezza massima per $a / $b", lcs(a, b), matched)
    }
  }

  @Test fun `oltre il tetto risponde null`() {
    val a = List(200) { "a$it" }
    val b = List(200) { "b$it" }
    assertNull(FluidTextDiff.diff(a, b, maxEdits = 100))
    assertNotNull(FluidTextDiff.diff(a, b, maxEdits = 400))
    // L'inizio e la fine comuni non contano: un testo lungo con una parola cambiata costa poco.
    val long = List(50_000) { "w$it" }
    val changed = long.toMutableList().also { it[25_000] = "nuova" }
    assertEquals(2, FluidTextDiff.diff(long, changed, maxEdits = 4)!!.edits)
  }

  private fun lcs(a: List<String>, b: List<String>): Int {
    val dp = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
      dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    }
    return dp[0][0]
  }
}
