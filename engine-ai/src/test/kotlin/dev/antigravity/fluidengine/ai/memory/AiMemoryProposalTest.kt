package dev.antigravity.fluidengine.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F9.3 · Il formato con cui un'app (o l'estrattore) propone un ricordo: i limiti, la pulizia, il JSON del ponte. */
class AiMemoryProposalTest {
  @Test fun `una proposta si ripulisce nei limiti`() {
    val p = AiMemoryProposal(
      text = "  «è in 4F\u0000 al   liceo\nGalilei»  ",
      key = " Scuola Classe!! ",
      topics = listOf("Scuola", "scuola", "x", "compiti", "verifiche", "orario", "classe", "materie", "troppe"),
      expiresAtMillis = -5,
    ).sanitized()!!
    assertEquals("è in 4F al liceo Galilei", p.text)
    assertEquals("scuola.classe", p.key)
    assertEquals(listOf("scuola", "compiti", "verifiche", "orario", "classe", "materie"), p.topics)
    assertNull(p.expiresAtMillis)
  }

  @Test fun `le virgolette che chiudono tutto il fatto cadono, quelle dentro restano`() {
    assertEquals("firma le mail con «Giulia»", AiMemoryProposal("firma le mail con «Giulia»").sanitized()!!.text)
    assertEquals("è vegetariana", AiMemoryProposal("\"è vegetariana\"").sanitized()!!.text)
    assertEquals("«a» e «b»", AiMemoryProposal("«a» e «b»").sanitized()!!.text)
  }

  @Test fun `un testo vuoto non e' una proposta, uno lungo si taglia a una parola`() {
    assertNull(AiMemoryProposal("   \u0007  ").sanitized())
    val long = ("parola ".repeat(80)).trim()
    val cut = AiMemoryProposal(long).sanitized()!!.text
    assertTrue(cut.length <= AiMemoryProposal.MaxTextChars)
    assertTrue(cut.endsWith("parola"))
  }

  @Test fun `il JSON va e torna uguale, senza campi in piu'`() {
    val p = AiMemoryProposal("la verifica di matematica è venerdì 28", "scuola.verifica.matematica", listOf("verifica", "matematica"),
      expiresAtMillis = 1_796_000_000_000L, observedAtMillis = 1_795_000_000_000L)
    val json = p.toJson()
    assertEquals(p, AiMemoryProposal.fromJson(json))
    assertFalse(json.contains("always"))
    assertNull(AiMemoryProposal.fromJson("{\"testo\":\"senza il campo giusto\"}"))
    assertNull(AiMemoryProposal.fromJson("non e' json"))
  }

  @Test fun `le richieste del ponte portano la versione e niente altro`() {
    val propose = AiMemoryProtocol.proposeRequest(AiMemoryProposal("è in 4F", "scuola.classe"))
    assertEquals("{\"memory\":1,\"proposal\":{\"text\":\"è in 4F\",\"key\":\"scuola.classe\"}}", propose)
    assertEquals("{\"memory\":1,\"noticeId\":\"n1\"}", AiMemoryProtocol.undoRequest("n1"))
    assertEquals("{\"memory\":1}", AiMemoryProtocol.forgetMineRequest())
    assertEquals(setOf("memory_propose", "memory_answer", "memory_undo", "memory_forget_mine"), AiMemoryProtocol.Methods)
  }

  @Test fun `l'esito torna come avviso per la card dell'app`() {
    val r = AiMemoryProposalResult(AiMemoryProposalResult.OutcomeUpdated, noticeId = "n7", text = "è in 5F", previousText = "è in 4F")
    val back = AiMemoryProposalResult.fromJson(r.toJson())
    assertEquals(r, back)
    assertTrue(back.remembered)
    val source = AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva")
    val notice = back.notice(source, 10L)!!
    assertEquals(AiMemoryNotice.KindUpdated, notice.kind)
    assertEquals("è in 4F", notice.previousText)
    assertTrue(notice.canUndo)
    assertNull(AiMemoryProposalResult(AiMemoryProposalResult.OutcomeDuplicate).notice(source, 0L))
    assertEquals(AiMemoryProposalResult.OutcomeInvalid, AiMemoryProposalResult.fromJson("{").outcome)
  }

  @Test fun `le chiavi si normalizzano`() {
    assertEquals("cibo.dieta", AiMemoryProposal.normalizeKey("Cibo.Dieta"))
    assertEquals("casa.citta", AiMemoryProposal.normalizeKey("casa citta"))
    assertNull(AiMemoryProposal.normalizeKey(" .. "))
    assertNull(AiMemoryProposal.normalizeKey(null))
  }

  @Test fun `un ricordo scade alla sua ora, un avviso annullato non si annulla di nuovo`() {
    val m = AiMemory("a", "x", source = AiMemorySource.Manual, createdAtMillis = 0, expiresAtMillis = 100)
    assertFalse(m.expired(99))
    assertTrue(m.expired(100))
    val n = AiMemoryNotice("n", AiMemoryNotice.KindRemembered, "a", "x", source = AiMemorySource.Manual, atMillis = 0)
    assertTrue(n.canUndo)
    assertFalse(n.copy(undone = true).canUndo)
    assertFalse(n.copy(kind = AiMemoryNotice.KindKept).canUndo)
  }
}
