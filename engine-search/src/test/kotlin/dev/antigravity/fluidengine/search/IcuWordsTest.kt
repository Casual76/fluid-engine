package dev.antigravity.fluidengine.search

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Perche' l'analisi dell'italiano e' dell'engine e non quella di ICU (che usa AppSearch): le parole come le taglia ICU
 * (UAX #29) e come le taglia l'indice, sulla stessa frase. Il risultato di ICU si scrive nell'uscita della prova (lo cita
 * la scheda del modulo); quello dell'indice si controlla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IcuWordsTest {
  private val sentence = "L'indirizzo dell'ospedale è all'ultimo piano: c'è un'altra entrata."

  private fun icuWords(text: String): List<String> {
    val it = BreakIterator.getWordInstance(ULocale.ITALIAN)
    it.setText(text)
    val out = ArrayList<String>()
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
      val w = text.substring(start, end)
      if (w.any { c -> c.isLetterOrDigit() }) out += w
      start = end
      end = it.next()
    }
    return out
  }

  @Test
  fun leParoleDiIcuEQuelleDellIndice() {
    val icu = icuWords(sentence)
    println("F8.22 ICU: $icu")
    val ours = SearchAnalyzer.tokens(sentence).filter { !it.stop }.map { it.form }
    println("F8.22 indice: $ours")
    assertEquals(listOf("indirizzo", "ospedale", "ultimo", "piano", "altra", "entrata"), ours)
  }
}
