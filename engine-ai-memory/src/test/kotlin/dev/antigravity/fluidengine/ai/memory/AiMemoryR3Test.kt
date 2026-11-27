package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomePending
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeRemembered
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeUpdated
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R3-S1 · I difetti 1, 3 e 6 della review R3 (`sol-ai.md`): cio' che la memoria lascia uscire verso un'app o verso il
 * prompt dopo che l'utente l'ha tolto. Ogni prova cerca il dato privato con un'espressione isolata (la parola intera, con
 * lettere e cifre escluse ai due lati): un id o un orario non la compongono per caso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryR3Test {
  private val clock = TestClock()
  private val mail = AiMemoryCaller("dev.pampa.mail", "Pampa Mail")
  private val classeviva = "dev.antigravity.classevivaexpressive"
  private val fluidify = "dev.pampa.fluidify"

  private fun book(autoSave: Boolean, config: AiMemoryConfig = AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = autoSave)) =
    runBlocking { AiMemoryBook.open(AiMemoryStore.inMemory(), config, clock) }

  private fun word(w: String) = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(w)}(?![\\p{L}\\p{N}])")

  private suspend fun AiMemoryInbox.call(method: String, json: String) = handle(method, json, mail)

  // ── 1 · una proposta non rivela all'app i ricordi privati ─────────────────────────────────────────────────────

  @Test fun `1 - la risposta del ponte non porta mai il ricordo privato con la stessa chiave`() = runBlocking {
    // Il ricordo scritto a mano dall'utente, sulla chiave che Pampa Mail puo' proporre.
    val secret = word("Pratesi")
    val firma = AiMemoryProposal("firma le mail come «avv. Ornella Pratesi, studio di via Maggio»", "scrittura.firma")
    val proposed = AiMemoryProposal("firma le mail con «Saluti, Ale»", "scrittura.firma")

    // Di serie si chiede: la domanda torna all'app.
    val asking = book(autoSave = false)
    asking.remember(firma, AiMemorySource.Manual)
    val inbox = AiMemoryInbox(asking)
    val pending = inbox.call(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(proposed))
    assertEquals(OutcomePending, AiMemoryProposalResult.fromJson(pending).outcome)
    assertFalse("la domanda porta all'app il ricordo privato: $pending", secret.containsMatchIn(pending))
    // Il «al posto di…» resta per la pagina di PampAI.
    assertTrue(secret.containsMatchIn(asking.pending.value.single().previousText.orEmpty()))
    // L'utente tocca Ricorda nella card dell'app: la conferma non lo porta neanche lei.
    val id = AiMemoryProposalResult.fromJson(pending).noticeId!!
    val answered = inbox.call(AiMemoryProtocol.MethodAnswer, AiMemoryProtocol.answerRequest(id, accept = true))
    assertEquals(OutcomeUpdated, AiMemoryProposalResult.fromJson(answered).outcome)
    assertFalse("la conferma porta all'app il ricordo privato: $answered", secret.containsMatchIn(answered))
    assertEquals("firma le mail con «Saluti, Ale»", AiMemoryProposalResult.fromJson(answered).text)

    // Il ricordo dell'utente e' piu' recente: resta, e l'app non lo legge nell'esito.
    val kept = book(autoSave = false)
    kept.remember(firma, AiMemorySource.Manual)
    val older = proposed.copy(observedAtMillis = MemoryTestTime.Now - MemoryTestTime.Day)
    val keptJson = AiMemoryInbox(kept).call(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(older))
    assertEquals(AiMemoryProposalResult.OutcomeKept, AiMemoryProposalResult.fromJson(keptJson).outcome)
    assertFalse("«kept» porta all'app il ricordo privato: $keptJson", secret.containsMatchIn(keptJson))
    // L'app non mostra una card per «kept»: il conflitto lo vede l'utente in PampAI.
    assertNull(AiMemoryProposalResult.fromJson(keptJson).notice(AiMemorySource.app(mail.packageName, mail.label), 0L))
    assertTrue(secret.containsMatchIn(kept.notices.value.first { it.kind == AiMemoryNotice.KindKept }.text))

    // Col salvataggio automatico l'aggiornamento entra subito: neanche li'.
    val auto = book(autoSave = true)
    auto.remember(firma, AiMemorySource.Manual)
    val updated = AiMemoryInbox(auto).call(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(proposed))
    assertEquals(OutcomeUpdated, AiMemoryProposalResult.fromJson(updated).outcome)
    assertFalse("l'aggiornamento porta all'app il ricordo privato: $updated", secret.containsMatchIn(updated))
  }

  // ── D2 (risposta di Alessio, R3-S1b) · lo stesso testo di un ricordo non suo non lo cambia ───────────────────

  @Test fun `D2 - un'app che propone il testo di un ricordo dell'utente non gli cambia scadenza ne' chiave`() = runBlocking {
    val b = book(autoSave = true)
    val diet = word("vegetariana")
    val mine = b.remember(AiMemoryProposal("è vegetariana", topics = listOf("cibo", "cena")), AiMemorySource.Manual).memory!!
    val inbox = AiMemoryInbox(b)
    val cv = AiMemoryCaller(classeviva, "ClasseViva")
    // ClasseViva indovina il testo, con una chiave sua e una scadenza fra un minuto.
    val guess = AiMemoryProposal("è vegetariana", "scuola.mensa", listOf("mensa"), expiresAtMillis = clock.now + 60_000)
    val json = inbox.handle(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(guess), cv)
    assertEquals("l'app sa solo che era un doppione", AiMemoryProposalResult.OutcomeDuplicate, AiMemoryProposalResult.fromJson(json).outcome)
    val after = b.memories.value.single { it.id == mine.id }
    assertNull("la scadenza dell'app e' finita sul ricordo dell'utente: ${after.expiresAtMillis}", after.expiresAtMillis)
    assertNull("la chiave dell'app e' finita sul ricordo dell'utente: ${after.key}", after.key)
    assertEquals(mine.topics, after.topics)
    assertEquals(mine.source, after.source)
    // Passato il minuto il ricordo dell'utente c'e' ancora nella domanda.
    clock.advance(2 * 60_000)
    assertTrue("il ricordo dell'utente e' scaduto per mano dell'app", diet.containsMatchIn(b.prompt("cosa mangio a cena?").text))

    // Un ricordo proposto dall'app stessa si aggiorna come prima (la scadenza nuova vale).
    val own = AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola"))
    assertEquals(AiMemoryProposalResult.OutcomeRemembered, AiMemoryProposalResult.fromJson(inbox.handle(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(own), cv)).outcome)
    val until = clock.now + MemoryTestTime.Day
    assertEquals(AiMemoryProposalResult.OutcomeDuplicate, AiMemoryProposalResult.fromJson(inbox.handle(AiMemoryProtocol.MethodPropose,
      AiMemoryProtocol.proposeRequest(own.copy(expiresAtMillis = until)), cv)).outcome)
    assertEquals(until, b.memories.value.single { it.key == "scuola.classe" }.expiresAtMillis)
  }

  // ── 3 · un ricordo revocato non torna con Ricorda o Annulla ───────────────────────────────────────────────────

  private val coldplay = AiMemoryProposal(
    "ascolta spesso i Coldplay", "musica.artista_preferito", listOf("musica", "concerti"),
    origin = AiMemory.OriginInference,
    evidence = listOf(AiMemoryEvidence("e1", grant = "g-fluidify", app = fluidify, record = "track-9")),
  )
  private val research = AiMemorySource.research(fluidify, "Fluidify")
  private val band = word("Coldplay")

  @Test fun `3 - una domanda dalla ricerca, la fonte revocata, poi Ricorda - non entra`() = runBlocking {
    val b = book(autoSave = false)
    val ask = b.remember(coldplay, research)
    assertEquals(OutcomePending, ask.result)
    b.forgetFromGrant("g-fluidify")
    val yes = b.answer(ask.notice!!.id, accept = true)
    assertFalse("la proposta della fonte revocata e' entrata: ${yes.result}", b.memories.value.any { band.containsMatchIn(it.text) })
    assertFalse(band.containsMatchIn(b.prompt("che musica mi consigli per il concerto?").text))
    assertTrue("la domanda sparisce con la revoca", b.pending.value.isEmpty())
  }

  @Test fun `3 - Annulla non rimette la versione di prima nata da una fonte revocata`() = runBlocking {
    val b = book(autoSave = true)
    assertEquals(OutcomeRemembered, b.remember(coldplay, research).result)
    // L'utente dice il suo gruppo: vince sul dedotto, e l'annulla tiene la versione di prima.
    val said = b.remember(AiMemoryProposal("il suo gruppo preferito sono i Verdena", "musica.artista_preferito", listOf("musica")), AiMemorySource.chat(1))
    assertEquals(OutcomeUpdated, said.result)
    // La revoca: il fatto detto resta (non viene da li'), la versione di prima no.
    assertEquals(0, b.forgetFromGrant("g-fluidify"))
    val undone = b.undo(said.notice!!.id)
    assertFalse("Annulla ha rimesso il fatto della fonte revocata (undo=$undone)", b.memories.value.any { band.containsMatchIn(it.text) })
    assertFalse(band.containsMatchIn(b.prompt("che musica mi consigli per il concerto?").text))
  }

  @Test fun `3 - la domanda di un'app poi bloccata non entra con Ricorda`() = runBlocking {
    val b = book(autoSave = false)
    val school = b.remember(AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola")), AiMemorySource.app(classeviva, "ClasseViva"))
    assertEquals(OutcomePending, school.result)
    b.setAppBlocked(classeviva, true)
    b.answer(school.notice!!.id, accept = true)
    assertFalse("la proposta dell'app bloccata e' entrata", b.memories.value.any { word("Galilei").containsMatchIn(it.text) })
  }

  @Test fun `3 - la domanda su un argomento poi escluso non entra con Ricorda`() = runBlocking {
    val b = book(autoSave = false)
    val diet = b.remember(AiMemoryProposal("è vegetariana", "cibo.dieta", listOf("cibo", "cena")), AiMemorySource.chat(1), automatic = true)
    assertEquals(OutcomePending, diet.result)
    b.exclude(AiMemoryExclusion(key = "cibo.dieta"))
    b.answer(diet.notice!!.id, accept = true)
    assertFalse("la proposta dell'argomento escluso e' entrata", b.memories.value.any { word("vegetariana").containsMatchIn(it.text) })
  }

  @Test fun `3 - una domanda gia' scaduta non entra con Ricorda`() = runBlocking {
    val b = book(autoSave = false)
    val test = b.remember(AiMemoryProposal("ha la verifica di storia domani", "scuola.verifica.storia", listOf("verifica"),
      expiresAtMillis = MemoryTestTime.Now + MemoryTestTime.Hour), AiMemorySource.chat(1), automatic = true)
    assertEquals(OutcomePending, test.result)
    clock.advance(2 * MemoryTestTime.Hour)
    b.answer(test.notice!!.id, accept = true)
    assertFalse("la proposta scaduta e' entrata", b.memories.value.any { word("storia").containsMatchIn(it.text) })
  }

  // ── 6 · una domanda in attesa del modello del telefono non usa un ricordo tolto nel frattempo ──────────────────

  @Test fun `6 - il ricordo tolto o scaduto mentre AICore allarga la domanda non entra nel prompt`() = runBlocking {
    var entered = CompletableDeferred<Unit>()
    var release = CompletableDeferred(Unit)
    val device = AiMemoryOnDeviceModel { _, _, _ -> entered.complete(Unit); release.await(); "cena, cibo, casa" }
    val b = book(autoSave = true, config = AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true, onDevice = device, onDeviceTimeoutMillis = 30_000))
    val street = word("Serragli")
    val home = b.remember(AiMemoryProposal("abita in via dei Serragli", "casa.indirizzo", listOf("casa")), AiMemorySource.Manual).memory!!
    b.setPinned(home.id, true)
    val diet = b.remember(AiMemoryProposal("è vegetariana", "cibo.dieta", listOf("cibo", "cena")), AiMemorySource.chat(1)).memory!!
    val lunch = b.remember(AiMemoryProposal("pranza alla mensa di San Marco", "cibo.pranzo", listOf("cibo", "pranzo"),
      expiresAtMillis = MemoryTestTime.Now + MemoryTestTime.Hour), AiMemorySource.chat(1)).memory!!
    assertTrue(word("vegetariana").containsMatchIn(b.prompt("cosa cucino stasera?").text))

    entered = CompletableDeferred(); release = CompletableDeferred()
    val prompt = async { b.prompt("cosa cucino stasera?") }
    entered.await()
    // Mentre il modello del telefono pensa: l'utente toglie l'indirizzo fissato e la dieta, e il pranzo scade.
    b.forget(home.id)
    b.forget(diet.id)
    clock.advance(2 * MemoryTestTime.Hour)
    release.complete(Unit)
    val text = prompt.await().text
    assertFalse("il ricordo fissato tolto e' nel prompt: $text", street.containsMatchIn(text))
    assertFalse("il ricordo pertinente tolto e' nel prompt: $text", word("vegetariana").containsMatchIn(text))
    assertFalse("il ricordo scaduto e' nel prompt: $text", word("Marco").containsMatchIn(text))
    assertTrue(b.memories.value.any { it.id == lunch.id })

    // Lo stesso per la ricerca che da' il contesto all'estrattore dopo il turno: un ricordo che scade durante l'attesa.
    b.remember(AiMemoryProposal("è vegana", "cibo.dieta", listOf("cibo", "cena"), expiresAtMillis = clock.now + MemoryTestTime.Hour), AiMemorySource.chat(1))
    entered = CompletableDeferred(); release = CompletableDeferred()
    val found = async { b.relevant("cosa cucino stasera?") }
    entered.await()
    clock.advance(2 * MemoryTestTime.Hour)
    release.complete(Unit)
    val known = found.await().map { it.memory.text }
    assertFalse("l'estrattore riceverebbe il ricordo scaduto: $known", known.any { word("vegana").containsMatchIn(it) })
  }
}
