package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeDuplicate
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeKept
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeLimitReached
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeNotAllowed
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeRefused
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeRemembered
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeUpdated
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F9.3 · La memoria: niente senza avviso, Annulla, le contraddizioni (vince il piu' recente, e lo si dice), i doppioni, i
 * «ricordati» nei turni di cui fidarsi poco, i tetti, il dopo-turno con l'estrattore, le scadenze, e il riavvio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryBookTest {
  private val clock = TestClock()
  private val you = AiMemorySource.chat(1L)
  private val classeviva = AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva")

  private fun book(store: AiMemoryStore = AiMemoryStore.inMemory(), limits: AiMemoryLimits = AiMemoryLimits()) = runBlocking {
    AiMemoryBook.open(store, AiMemoryConfig(limits = limits, zone = MemoryTestTime.Zone, autoSave = true), clock)
  }

  @Test fun `ogni ricordo nuovo ha il suo avviso, e Annulla lo toglie`() = runBlocking {
    val b = book()
    val out = b.remember(AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola")), you)
    assertEquals(OutcomeRemembered, out.result)
    val notice = out.notice!!
    assertEquals(AiMemoryNotice.KindRemembered, notice.kind)
    assertEquals(listOf(notice), b.notices.value)
    assertEquals(listOf("è in 4F al liceo Galilei"), b.memories.value.map { it.text })
    assertEquals(you, b.memories.value.single().source)
    assertEquals(MemoryTestTime.Now, b.memories.value.single().createdAtMillis)

    assertTrue(b.undo(notice.id))
    assertTrue(b.memories.value.isEmpty())
    assertTrue(b.notices.value.single().undone)
    assertFalse(b.notices.value.single().canUndo)
    // Un annulla vale una volta.
    assertFalse(b.undo(notice.id))
  }

  @Test fun `due ricordi che si contraddicono - vince il piu' recente, e si dice cosa c'era prima`() = runBlocking {
    val b = book()
    b.remember(AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe"), you)
    clock.advance(3 * MemoryTestTime.Day)
    val out = b.remember(AiMemoryProposal("è in 5F al liceo Galilei", "scuola.classe"), you)
    assertEquals(OutcomeUpdated, out.result)
    val m = b.memories.value.single()
    assertEquals("è in 5F al liceo Galilei", m.text)
    assertEquals("è in 4F al liceo Galilei", m.previousText)
    assertEquals(MemoryTestTime.Now, m.previousSinceMillis)
    assertEquals(AiMemoryNotice.KindUpdated, out.notice!!.kind)
    assertEquals("è in 4F al liceo Galilei", out.notice!!.previousText)
    // La card di prima («Ho ricordato: è in 4F») non offre piu' Annulla: il fatto e' cambiato dopo di lei.
    val first = b.notices.value.first { it.kind == AiMemoryNotice.KindRemembered }
    assertTrue(first.stale)
    assertFalse(first.canUndo)
    // Annulla rimette quello di prima, intero.
    assertTrue(b.undo(out.notice!!.id))
    assertEquals("è in 4F al liceo Galilei", b.memories.value.single().text)
    assertNull(b.memories.value.single().previousText)
  }

  @Test fun `un fatto detto prima che arriva dopo non vince, e l'avviso lo dice`() = runBlocking {
    val b = book()
    b.remember(AiMemoryProposal("è in 5F al liceo Galilei", "scuola.classe"), you)
    // ClasseViva manda un dato di settembre: piu' vecchio di quello detto oggi in chat.
    val september = MemoryTestTime.Now - 70 * MemoryTestTime.Day
    val out = b.remember(AiMemoryProposal("è in 4F", "scuola.classe", observedAtMillis = september), classeviva)
    assertEquals(OutcomeKept, out.result)
    assertEquals("è in 5F al liceo Galilei", b.memories.value.single().text)
    assertEquals(AiMemoryNotice.KindKept, out.notice!!.kind)
    assertEquals("è in 4F", out.notice!!.previousText)
    assertFalse(out.notice!!.canUndo)
  }

  @Test fun `lo stesso fatto detto due volte resta uno, con le parole di tutti e due e nessun avviso`() = runBlocking {
    val b = book()
    b.remember(AiMemoryProposal("È vegetariana.", "cibo.dieta", listOf("cibo")), you)
    clock.advance(MemoryTestTime.Hour)
    val again = b.remember(AiMemoryProposal("è vegetariana", null, listOf("cena")), you)
    assertEquals(OutcomeDuplicate, again.result)
    assertNull(again.notice)
    assertEquals(1, b.notices.value.size)
    val m = b.memories.value.single()
    assertEquals(listOf("cibo", "cena"), m.topics)
    assertEquals(MemoryTestTime.Now + MemoryTestTime.Hour, m.observedAtMillis)
  }

  @Test fun `correggere, fissare, scadere, e un annulla vecchio non vale piu' dopo una correzione`() = runBlocking {
    val b = book()
    val n = b.remember(AiMemoryProposal("abita a Firenzi", "casa.citta"), you).notice!!
    val id = b.memories.value.single().id
    clock.advance(1_000)
    val fixed = b.correct(id, "abita a Firenze")!!
    assertTrue(fixed.edited)
    assertFalse(b.undo(n.id))
    assertEquals("abita a Firenze", b.memories.value.single().text)
    assertTrue(b.setPinned(id, true))
    assertTrue(b.memories.value.single().pinned)
    assertTrue(b.setExpiry(id, MemoryTestTime.Now + MemoryTestTime.Day))
    assertNull(b.correct(id, "   "))
    assertNull(b.correct(id, "il pin del portone è 1234"))
  }

  @Test fun `ricordati in un turno di cui fidarsi poco chiede prima, nella chat temporanea no`() = runBlocking {
    val b = book()
    val read = turn("ricordati che il ristorante preferito è Da Mario").copy(readExternal = true)
    val ask = b.remember(AiMemoryProposal("il suo ristorante preferito è Da Mario", "cibo.ristorante"), you, turn = read)
    assertEquals(AiMemoryOutcome.NeedsConfirmation, ask.result)
    assertTrue(b.memories.value.isEmpty())
    val yes = b.remember(AiMemoryProposal("il suo ristorante preferito è Da Mario", "cibo.ristorante"), you, turn = read, confirmed = true)
    assertEquals(OutcomeRemembered, yes.result)
    val temp = b.remember(AiMemoryProposal("è in 4F"), you, turn = turn("ricordati che sono in 4F").copy(temporary = true), confirmed = true)
    assertEquals(OutcomeNotAllowed, temp.result)
    val locked = b.remember(AiMemoryProposal("è in 4F"), you, turn = turn("ricordati che sono in 4F").copy(locked = true))
    assertEquals(OutcomeNotAllowed, locked.result)
    assertEquals(OutcomeRefused, b.remember(AiMemoryProposal("la password della mail è Rondine99"), you).result)
  }

  @Test fun `i tetti - la memoria piena, i ricordi automatici del giorno, quelli di un'app`() = runBlocking {
    val b = book(limits = AiMemoryLimits(maxMemories = 3, maxAutomaticPerDay = 2, maxPerAppPerDay = 1))
    assertEquals(OutcomeRemembered, b.remember(AiMemoryProposal("fatto uno"), you, automatic = true).result)
    assertEquals(OutcomeRemembered, b.remember(AiMemoryProposal("fatto due"), you, automatic = true).result)
    assertEquals(OutcomeLimitReached, b.remember(AiMemoryProposal("fatto tre"), you, automatic = true).result)
    // Un «ricordati» esplicito non conta nel tetto dei ricordi automatici.
    assertEquals(OutcomeRemembered, b.remember(AiMemoryProposal("fatto tre"), you).result)
    assertEquals(OutcomeLimitReached, b.remember(AiMemoryProposal("fatto quattro"), AiMemorySource.Manual).result)
    val other = book(limits = AiMemoryLimits(maxPerAppPerDay = 1))
    assertEquals(OutcomeRemembered, other.remember(AiMemoryProposal("è in 4F", "scuola.classe"), classeviva).result)
    assertEquals(OutcomeLimitReached, other.remember(AiMemoryProposal("la verifica è giovedì", "scuola.verifica"), classeviva).result)
    clock.advance(MemoryTestTime.Day + 1)
    assertEquals(OutcomeRemembered, other.remember(AiMemoryProposal("la verifica è giovedì", "scuola.verifica"), classeviva).result)
  }

  @Test fun `il dopo-turno - l'estrattore propone, la memoria ricorda e lo dice`() = runBlocking {
    val b = book()
    val model = FakeMemoryModel.scripted()
    val extractor = AiMemoryExtractor(model, "piccolo", zone = MemoryTestTime.Zone)
    val first = b.afterTurn(turn("Sono in 4F al Galilei, che compiti ho?"), extractor)
    assertEquals(listOf(AiMemoryNotice.KindRemembered), first.notices.map { it.kind })
    clock.advance(4 * MemoryTestTime.Day)
    val second = b.afterTurn(turn("Da lunedì sono in 5F, ho cambiato sezione", at = clock.now), extractor)
    assertEquals(listOf(AiMemoryNotice.KindUpdated), second.notices.map { it.kind })
    assertEquals("è in 5F al liceo Galilei", b.memories.value.single().text)
    // L'estrattore ha visto la chiave gia' usata (e l'ha riusata): nessuna parola del messaggio nomina il ricordo di prima.
    assertTrue(model.requests.last().messages.last().toString().contains("scuola.classe"))
    // La chat temporanea, e un messaggio che non dice niente di se': nessuna chiamata.
    val calls = model.requests.size
    assertTrue(b.afterTurn(turn("sono vegetariana").copy(temporary = true), extractor).notices.isEmpty())
    assertEquals(AiMemoryPolicy.SkipNoSignal, b.afterTurn(turn("che tempo fa?"), extractor).extraction.skipped)
    assertEquals(calls, model.requests.size)
  }

  @Test fun `uno scaduto non entra piu' nelle domande e dopo una settimana sparisce`() = runBlocking {
    val b = book()
    b.remember(AiMemoryProposal("ha la verifica di matematica venerdì", "scuola.verifica", listOf("verifica", "matematica"),
      expiresAtMillis = MemoryTestTime.Now + 2 * MemoryTestTime.Day), you)
    assertEquals(1, b.relevant("quando ho la verifica di matematica?").size)
    clock.advance(2 * MemoryTestTime.Day)
    assertTrue(b.relevant("quando ho la verifica di matematica?").isEmpty())
    assertTrue(b.prompt("quando ho la verifica di matematica?").isEmpty)
    assertEquals(1, b.memories.value.size)
    assertEquals(0, b.maintain())
    clock.advance(8 * MemoryTestTime.Day)
    assertEquals(1, b.maintain())
    assertTrue(b.memories.value.isEmpty())
  }

  @Test fun `dopo un riavvio la memoria e gli annulla ci sono ancora`() = runBlocking {
    val store = AiMemoryStore.inMemory()
    val b = book(store)
    val n = b.remember(AiMemoryProposal("abita a Firenze", "casa.citta", listOf("casa", "meteo")), you).notice!!
    b.setAppBlocked("dev.pampa.mail", true)
    b.close()
    val again = book(store)
    assertEquals(listOf("abita a Firenze"), again.memories.value.map { it.text })
    assertEquals(setOf("dev.pampa.mail"), again.blockedApps.value)
    assertEquals(1, again.relevant("che tempo fa a casa?").size)
    assertTrue(again.undo(n.id))
    assertTrue(again.memories.value.isEmpty())
  }

  @Test fun `un file che non si legge non azzera la memoria`() {
    val store = AiMemoryStore.inMemory()
    runBlocking { store.write("non e' la memoria".toByteArray()) }
    val error = runCatching { book(store) }.exceptionOrNull()
    assertNotNull(error)
    assertEquals("non e' la memoria", String(runBlocking { store.read() }!!))
  }
}
