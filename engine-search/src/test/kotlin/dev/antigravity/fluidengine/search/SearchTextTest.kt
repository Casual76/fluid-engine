package dev.antigravity.fluidengine.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'italiano dell'indice, il piano della domanda e i pezzi evidenziati: puri, sulla JVM. */
class SearchTextTest {

  private fun forms(text: String) = SearchAnalyzer.tokens(text).filter { !it.stop }.map { it.form }
  private fun stems(text: String) = SearchAnalyzer.tokens(text).filter { !it.stop }.map { it.stem }

  @Test
  fun accentiEMaiuscoleSonoLaStessaParola() {
    assertEquals(listOf("perche", "citta"), forms("Perché città?"))
    assertEquals(forms("perche' citta'"), forms("PERCHÉ CITTÀ"))
    // Un accento scritto a parte (e + accento combinante) e' come quello composto.
    assertEquals(listOf("caffe"), forms("caffé"))
    assertEquals(listOf("strasse"), forms("Straße"))
  }

  @Test
  fun gliApostrofiDividonoLeParole() {
    assertEquals(listOf("indirizzo"), forms("l'indirizzo"))
    assertEquals(listOf("apparecchio"), forms("dell’apparecchio"))
    assertEquals(listOf("ospedale"), forms("all`ospedale"))
    // La parte elisa e' una parola vuota.
    val all = SearchAnalyzer.tokens("un'altra")
    assertEquals(listOf("un", "altra"), all.map { it.form })
    assertTrue(all[0].stop)
  }

  @Test
  fun pluraliEGeneriHannoLaStessaRadice() {
    assertEquals(stems("riunione"), stems("riunioni"))
    assertEquals(stems("verifica"), stems("verifiche"))
    assertEquals(stems("amico"), stems("amiche"))
    assertEquals(stems("amici"), stems("amica"))
    assertEquals(stems("farmacia"), stems("farmacie"))
    assertEquals(stems("luogo"), stems("luoghi"))
    assertEquals(stems("voto"), stems("voti"))
    assertEquals(stems("casa"), stems("case"))
    assertEquals(stems("dentista"), stems("dentisti"))
    // Le parole con cifre e quelle corte non cambiano.
    assertEquals(listOf("14c"), stems("14C"))
    assertEquals(listOf("tre"), stems("tre"))
  }

  @Test
  fun lePosizioniSonoQuelleDelTestoOriginale() {
    val text = "Città: l'indirizzo è giusto"
    val t = SearchAnalyzer.tokens(text)
    val word = t.first { it.form == "indirizzo" }
    assertEquals("indirizzo", text.substring(word.start, word.end))
    assertEquals("città", t.first().display)
  }

  @Test
  fun laDistanzaContaScambiInserimentiEMancanze() {
    assertEquals(1, SearchAnalyzer.distance("riunone", "riunione", 2))
    assertEquals(1, SearchAnalyzer.distance("riunoine", "riunione", 2))
    assertEquals(2, SearchAnalyzer.distance("rinone", "riunione", 2))
    assertEquals(3, SearchAnalyzer.distance("abc", "riunione", 2))
    assertEquals(0, SearchAnalyzer.typoBudget(3))
    assertEquals(1, SearchAnalyzer.typoBudget(6))
    assertEquals(2, SearchAnalyzer.typoBudget(7))
  }

  private class Vocab(words: List<String>) : SearchVocabulary {
    private val entries = words.map { w -> VocabularyEntry(SearchAnalyzer.fold(w), SearchAnalyzer.stem(SearchAnalyzer.fold(w)), w, 1) }
    private val memory = MemoryVocabulary(entries.sortedBy { it.form })
    override fun hasStem(stem: String) = memory.hasStem(stem)
    override fun hasPrefix(prefix: String) = memory.hasPrefix(prefix)
    override fun near(form: String, maxDistance: Int, limit: Int) = memory.near(form, maxDistance, limit)
  }

  @Test
  fun unaParolaSbagliataCercaLeVicineEDiceLaCorrezione() {
    val vocab = Vocab(listOf("riunione", "riunioni", "giugno", "lunedì"))
    val plan = SearchPlanner.plan("riunone ", vocab, blind = null, typos = true)
    assertEquals("riunione", plan.corrected)
    assertTrue(plan.phrases.contains("riunion"))
    // Una parola che c'e' non si corregge: «rossi» resta «rossi» anche se c'e' «rosso».
    val known = SearchPlanner.plan("rossi ", Vocab(listOf("rossi", "rosso")), blind = null, typos = true)
    assertNull(known.corrected)
    assertFalse(known.phrases.any { it.startsWith("h") })
  }

  @Test
  fun lUltimaParolaValeAncheComeInizio() {
    val plan = SearchPlanner.plan("riunione lun", SearchVocabulary.Empty, blind = null, typos = false)
    assertEquals(2, plan.groups)
    assertTrue(plan.phrases.contains("lun*"))
    assertTrue(plan.phrases.contains("riunion"))
    // «verifich» si sta scrivendo: la radice di «verifiche» e' «verific».
    val typing = SearchPlanner.plan("verifich", SearchVocabulary.Empty, blind = null, typos = false)
    assertTrue(typing.phrases.contains("verific"))
    // Le parole vuote si tolgono, tranne l'ultima che si sta scrivendo.
    assertEquals(1, SearchPlanner.plan("la mail ", SearchVocabulary.Empty, null, false).groups)
    assertTrue(SearchPlanner.plan("la", SearchVocabulary.Empty, null, false).phrases.contains("la*"))
  }

  @Test
  fun iCampiRiservatiSiCercanoPerImpronta() {
    val blind = BlindTerms(ByteArray(32) { it.toByte() })
    val plan = SearchPlanner.plan("girasole", SearchVocabulary.Empty, blind, typos = false)
    assertTrue(plan.phrases.contains(blind.term("girasol")))
    assertTrue(plan.phrases.contains(blind.prefix("girasole")))
    // Le impronte sono parole buone per FTS4: lettere e cifre.
    assertTrue(blind.term("girasol").all { it.isLetterOrDigit() })
    assertEquals(17, blind.term("girasol").length)
  }

  @Test
  fun ilPezzoEvidenziaLeParoleETaglia() {
    val plan = SearchPlanner.plan("riunione ", SearchVocabulary.Empty, null, false)
    val text = "Il verbale della riunione con le decisioni sul calendario delle uscite didattiche e sulla gita di primavera, " +
      "poi le riunioni dei genitori, e alla fine molte altre cose che non c'entrano niente con il resto del discorso fatto prima."
    val s = SearchSnippets.best(text, plan, maxChars = 80)!!
    assertTrue(s.text.length <= 82)
    assertTrue(s.highlighted.isNotEmpty())
    assertTrue(s.highlighted.all { it.startsWith("riunion") })
    assertTrue(s.text.endsWith("…"))
    // L'inizio di parola si evidenzia solo per la parte scritta.
    val prefix = SearchPlanner.plan("riun", SearchVocabulary.Empty, null, false)
    val whole = SearchSnippets.whole("Riunione di lunedì", prefix)
    assertEquals(listOf("Riun"), whole.highlighted)
  }

  @Test
  fun ilPezzoSenzaParoleEdLInizioDelTesto() {
    val plan = SearchPlanner.plan("zebra ", SearchVocabulary.Empty, null, false)
    val s = SearchSnippets.best("Una riga\n\ncon   spazi.", plan)!!
    assertEquals("Una riga con spazi.", s.text)
    assertTrue(s.highlights.isEmpty())
  }

  @Test
  fun ilTitoloPesaPiuDelCorpo() {
    // Una frase, due colonne (titolo peso 3, corpo 1), 10 righe; la stessa parola una volta.
    val plan = QueryPlan(listOf("riunion"), listOf(PlannedVariant(0, 0, 1f, TermMatch.Stem("riunion"))), 1, null)
    fun info(titleHits: Int, bodyHits: Int) = MatchInfo.of(
      intArrayOf(1, 2, 10, 4, 20, 4, 20, titleHits, 5, 3, bodyHits, 9, 6),
    )
    val weights = floatArrayOf(3f, 1f)
    val inTitle = SearchScoring.score(plan, info(1, 0), weights).score
    val inBody = SearchScoring.score(plan, info(0, 1), weights).score
    assertTrue("titolo $inTitle corpo $inBody", inTitle > inBody)
    assertEquals(0, SearchScoring.score(plan, info(0, 0), weights).matchedGroups)
  }

  @Test
  fun iDocumentiEIPezziPerIlModello() {
    assertEquals(listOf("Titolo. corpo"), FluidSearchIndex.passages("Titolo", "corpo", 700, 6))
    val long = (1..60).joinToString(" ") { "Frase numero $it." }
    val parts = FluidSearchIndex.passages("T", long, 200, 6)
    assertTrue(parts.size in 2..6)
    assertTrue(parts.all { it.startsWith("T. ") && it.length <= 200 })
    val back = FluidSearchIndex.deserialize(FluidSearchIndex.serialize(mapOf("a" to "uno", "b" to "è due")))
    assertEquals(mapOf("a" to "uno", "b" to "è due"), back)
  }
}
