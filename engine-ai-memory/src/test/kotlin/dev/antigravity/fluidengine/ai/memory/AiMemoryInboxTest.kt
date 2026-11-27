package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeForgotten
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeInvalid
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeLimitReached
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeNotAllowed
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeNotFound
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeRemembered
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeUndone
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeUpdated
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F9.3 · I ricordi che nascono in un'altra app passano da PampAI: il formato del ponte (in engine-ai) e l'aggancio per il
 * provider dell'assistenza di F9.1 ([AiMemoryInbox]). Chi chiama arriva gia' verificato dal cancello di F9.1; qui si
 * prova cosa decide la memoria: la tabella, gli argomenti di ogni app, l'utente che blocca, i tetti, l'annulla e la
 * revoca dell'app stessa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryInboxTest {
  private val clock = TestClock()
  private val classeviva = AiMemoryCaller("dev.antigravity.classevivaexpressive", "ClasseViva")
  private val mail = AiMemoryCaller("dev.pampa.mail", "Pampa Mail")
  private val stranger = AiMemoryCaller("dev.pampa.fluidweather", "Fluid Weather")

  private fun setup(devBuild: Boolean = false, limits: AiMemoryLimits = AiMemoryLimits()) = runBlocking {
    val book = AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig(limits = limits, zone = MemoryTestTime.Zone, autoSave = true), clock)
    book to AiMemoryInbox(book, AiMemoryAppPolicy(devBuild = devBuild))
  }

  private suspend fun AiMemoryInbox.propose(caller: AiMemoryCaller, p: AiMemoryProposal): AiMemoryProposalResult =
    AiMemoryProposalResult.fromJson(handle(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(p), caller))

  @Test fun `ClasseViva propone la classe - PampAI la ricorda, lo dice, e l'app puo' annullare`() = runBlocking {
    val (book, inbox) = setup()
    val r = inbox.propose(classeviva, AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola", "classe")))
    assertEquals(OutcomeRemembered, r.outcome)
    assertNotNull(r.noticeId)
    val m = book.memories.value.single()
    assertEquals(AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva"), m.source)
    // PampAI ha il suo avviso (la chat «Dalle app» e la pagina), l'app il suo dall'esito.
    assertEquals(r.noticeId, book.notices.value.single().id)
    val appNotice = r.notice(AiMemorySource.app(classeviva.packageName, classeviva.label), 0L)!!
    assertTrue(appNotice.canUndo)

    // Un'altra app non annulla quello di ClasseViva; ClasseViva si'.
    val undo = AiMemoryProtocol.undoRequest(r.noticeId!!)
    assertEquals(OutcomeNotFound, AiMemoryProposalResult.fromJson(inbox.handle(AiMemoryProtocol.MethodUndo, undo, mail)).outcome)
    assertEquals(OutcomeUndone, AiMemoryProposalResult.fromJson(inbox.handle(AiMemoryProtocol.MethodUndo, undo, classeviva)).outcome)
    assertTrue(book.memories.value.isEmpty())
  }

  @Test fun `ogni app solo sui suoi argomenti, e solo le app della tabella`() = runBlocking {
    val (book, inbox) = setup()
    assertEquals(OutcomeNotAllowed, inbox.propose(classeviva, AiMemoryProposal("è vegetariana", "cibo.dieta")).outcome)
    assertEquals(OutcomeNotAllowed, inbox.propose(classeviva, AiMemoryProposal("è in 4F")).outcome)
    assertEquals(OutcomeNotAllowed, inbox.propose(stranger, AiMemoryProposal("abita a Firenze", "casa.citta")).outcome)
    assertEquals(OutcomeRemembered, inbox.propose(mail, AiMemoryProposal("firma le mail con «Giulia»", "scrittura.firma")).outcome)
    // Un'app non decide «per ogni domanda»: lo decide l'utente.
    assertEquals(OutcomeRemembered, inbox.propose(classeviva, AiMemoryProposal("è in 4F", "scuola.classe", always = true)).outcome)
    assertFalse(book.memories.value.first { it.key == "scuola.classe" }.always)
    // Le varianti di sviluppo valgono solo con un PampAI di sviluppo.
    val debugCv = AiMemoryCaller("dev.antigravity.classevivaexpressive.debug", "ClasseViva")
    assertEquals(OutcomeNotAllowed, inbox.propose(debugCv, AiMemoryProposal("è in 4F", "scuola.classe")).outcome)
    val (_, devInbox) = setup(devBuild = true)
    assertEquals(OutcomeRemembered, devInbox.propose(debugCv, AiMemoryProposal("è in 4F", "scuola.classe")).outcome)
  }

  @Test fun `l'utente blocca l'app, il tetto del giorno, le richieste rotte`() = runBlocking {
    val (book, inbox) = setup(limits = AiMemoryLimits(maxPerAppPerDay = 2))
    assertEquals(OutcomeRemembered, inbox.propose(classeviva, AiMemoryProposal("è in 4F", "scuola.classe")).outcome)
    assertEquals(OutcomeRemembered, inbox.propose(classeviva, AiMemoryProposal("ha storia il martedì", "scuola.orario")).outcome)
    assertEquals(OutcomeLimitReached, inbox.propose(classeviva, AiMemoryProposal("la prof di inglese è la Bianchi", "scuola.docenti")).outcome)
    // Un aggiornamento non e' un ricordo in piu': passa anche col tetto pieno.
    assertEquals(OutcomeUpdated, inbox.propose(classeviva, AiMemoryProposal("è in 5F", "scuola.classe")).outcome)
    book.setAppBlocked(classeviva.packageName, true)
    assertEquals(OutcomeNotAllowed, inbox.propose(classeviva, AiMemoryProposal("è in 5G", "scuola.classe")).outcome)
    for (bad in listOf("{", "{\"memory\":2,\"proposal\":{\"text\":\"x\",\"key\":\"scuola.x\"}}", "{\"memory\":1}", "[]")) {
      assertEquals(OutcomeInvalid, AiMemoryProposalResult.fromJson(inbox.handle(AiMemoryProtocol.MethodPropose, bad, classeviva)).outcome)
    }
    assertEquals(OutcomeInvalid, AiMemoryProposalResult.fromJson(inbox.handle("memory_list", "{\"memory\":1}", classeviva)).outcome)
  }

  @Test fun `l'app dimentica quello che ha proposto lei, non il resto`() = runBlocking {
    val (book, inbox) = setup()
    inbox.propose(classeviva, AiMemoryProposal("è in 4F", "scuola.classe"))
    inbox.propose(classeviva, AiMemoryProposal("ha storia il martedì", "scuola.orario"))
    book.remember(AiMemoryProposal("vuole ripassare storia", "studio.storia"), AiMemorySource.chat(3, classeviva.packageName, classeviva.label))
    book.remember(AiMemoryProposal("è vegetariana", "cibo.dieta"), AiMemorySource.chat(1))
    val r = AiMemoryProposalResult.fromJson(inbox.handle(AiMemoryProtocol.MethodForgetMine, AiMemoryProtocol.forgetMineRequest(), classeviva))
    assertEquals(OutcomeForgotten, r.outcome)
    assertEquals(2, r.count)
    assertEquals(setOf("vuole ripassare storia", "è vegetariana"), book.memories.value.map { it.text }.toSet())
  }
}
