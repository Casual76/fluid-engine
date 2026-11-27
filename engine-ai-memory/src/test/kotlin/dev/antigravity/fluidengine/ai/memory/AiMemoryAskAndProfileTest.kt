package dev.antigravity.fluidengine.ai.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeDeclined
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeExcluded
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeNotAllowed
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeNotFound
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomePending
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult.Companion.OutcomeRemembered
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F9.3b · Le risposte di Alessio del 26/11/2026: di serie la memoria **chiede** («Vuoi che lo ricordi?», Ricorda e No) e
 * con «Ricorda da solo» salva e lo dice; le app che propongono da sole sono Pampa Mail, MyCloud Gallery e ClasseViva, le
 * altre passano solo dallo strumento del modello; il profilo della ricerca entra nella memoria (da un database Room v7
 * finto, con prove, autorizzazioni ed esclusioni); AICore (finto) allarga la domanda ed estrae sul telefono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryAskAndProfileTest {
  @get:Rule val tmp = TemporaryFolder()
  private val clock = TestClock()
  private val you = AiMemorySource.chat(1L)

  private fun book(store: AiMemoryStore = AiMemoryStore.inMemory(), config: AiMemoryConfig = AiMemoryConfig(zone = MemoryTestTime.Zone)) =
    runBlocking { AiMemoryBook.open(store, config, clock) }

  private fun extractor(model: FakeMemoryModel = FakeMemoryModel.scripted()) =
    AiMemoryExtractor(model, "llama-3.1-8b-instant", zone = MemoryTestTime.Zone)

  @Test fun `di serie chiede - niente entra prima del si', e Ricorda tiene lo stesso avviso`() = runBlocking {
    val b = book()
    val after = b.afterTurn(turn("sono in 4F al Galilei, che compiti ho?"), extractor())
    val ask = after.notices.single()
    assertEquals(AiMemoryNotice.KindAsk, ask.kind)
    assertTrue(ask.asking && !ask.canUndo)
    assertEquals(OutcomePending, after.outcomes.single().result)
    assertTrue(b.memories.value.isEmpty())
    assertEquals(listOf(ask.id), b.pending.value.map { it.id })
    val yes = b.answer(ask.id, accept = true)
    assertEquals(OutcomeRemembered, yes.result)
    assertEquals(ask.id, yes.notice!!.id)
    assertTrue(yes.notice!!.canUndo)
    assertEquals(listOf("è in 4F al liceo Galilei"), b.memories.value.map { it.text })
    assertTrue(b.pending.value.isEmpty())
    // Una seconda risposta non trova piu' niente.
    assertEquals(OutcomeNotFound, b.answer(ask.id, accept = true).result)
  }

  @Test fun `No con l'esclusione non chiede piu' di quell'argomento, ma un ricordati passa lo stesso`() = runBlocking {
    val b = book()
    val ask = b.afterTurn(turn("sono vegetariana, consigliami una cena"), extractor()).notices.single()
    val no = b.answer(ask.id, accept = false, exclude = true)
    assertEquals(OutcomeDeclined, no.result)
    assertTrue(no.notice!!.declined && !no.notice!!.asking)
    assertTrue(b.memories.value.isEmpty() && b.pending.value.isEmpty())
    assertEquals(listOf(AiMemoryExclusion(key = "cibo.dieta")), b.exclusions.value)
    // Lo stesso fatto trovato di nuovo: escluso, senza domanda.
    val again = b.afterTurn(turn("sono vegetariana, cosa ordino?"), extractor())
    assertEquals(OutcomeExcluded, again.outcomes.single().result)
    assertTrue(again.notices.isEmpty())
    // Chiesto dall'utente entra: l'esclusione vale per cio' che la memoria impara da sola.
    val asked = b.afterTurn(turn("ricordati che sono vegetariana"), extractor())
    assertEquals(OutcomeRemembered, asked.outcomes.single().result)
  }

  @Test fun `un ricordati entra subito anche quando di serie si chiede`() = runBlocking {
    val b = book()
    val after = b.afterTurn(turn("ricordati che mi chiamo Giulia"), extractor())
    assertEquals(OutcomeRemembered, after.outcomes.single().result)
    assertTrue(after.notices.single().canUndo)
    assertTrue(b.pending.value.isEmpty())
    assertEquals(listOf("si chiama Giulia"), b.memories.value.map { it.text })
  }

  @Test fun `Ricorda da solo resta acceso alla riapertura, e le domande aperte restano`() = runBlocking {
    val store = AiMemoryStore.inMemory()
    val b = book(store)
    assertFalse(b.autoSave.value)
    val ask = b.afterTurn(turn("sono in 4F al Galilei"), extractor()).notices.single()
    b.setAutoSave(true)
    assertTrue(b.autoSave.value)
    assertEquals(OutcomeRemembered, b.afterTurn(turn("sono vegetariana"), extractor()).outcomes.single().result)
    b.close()
    val again = book(store)
    assertTrue(again.autoSave.value)
    assertEquals(listOf(ask.id), again.pending.value.map { it.id })
    assertEquals(listOf("è vegetariana"), again.memories.value.map { it.text })
    // La domanda di prima si risponde anche dopo la riapertura.
    assertEquals(OutcomeRemembered, again.answer(ask.id, accept = true).result)
    assertEquals(2, again.memories.value.size)
  }

  @Test fun `le app della tabella - MyCloud Gallery propone e risponde col ponte, Pampa Notes no`() = runBlocking {
    val b = book()
    val inbox = AiMemoryInbox(b)
    val notes = AiMemoryCaller("dev.pampa.pampanotes", "Pampa Notes")
    val gallery = AiMemoryCaller("dev.pampa.mycloudgallery", "MyCloud Gallery")
    val mail = AiMemoryCaller("dev.pampa.mail", "Pampa Mail")
    val p = AiMemoryProposal("va spesso al mare a Cecina", "luoghi.mare", listOf("mare"))
    suspend fun call(method: String, json: String, c: AiMemoryCaller) = AiMemoryProposalResult.fromJson(inbox.handle(method, json, c))
    assertEquals(OutcomeNotAllowed, call(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(p.copy(key = "studio.chimica")), notes).outcome)
    // Un argomento che non e' suo: no anche per MyCloud Gallery.
    assertEquals(OutcomeNotAllowed, call(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(p.copy(key = "cibo.dieta")), gallery).outcome)
    val asked = call(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(p), gallery)
    assertEquals(OutcomePending, asked.outcome)
    assertEquals("va spesso al mare a Cecina", asked.text)
    val id = asked.noticeId!!
    // Un'altra app non risponde per lei.
    assertEquals(OutcomeNotFound, call(AiMemoryProtocol.MethodAnswer, AiMemoryProtocol.answerRequest(id, true), mail).outcome)
    assertEquals(OutcomeRemembered, call(AiMemoryProtocol.MethodAnswer, AiMemoryProtocol.answerRequest(id, true), gallery).outcome)
    assertEquals(AiMemorySource.app(gallery.packageName, gallery.label), b.memories.value.single().source)
  }

  @Test fun `una domanda da un'app fuori tabella non lascia ricordi automatici, e lo strumento del modello chiede`() = runBlocking {
    val b = book()
    val model = FakeMemoryModel.scripted()
    val fromNotes = turn("sono in 4F al Galilei, riassumi gli appunti").copy(app = "dev.pampa.pampanotes", appLabel = "Pampa Notes")
    val skipped = b.afterTurn(fromNotes, extractor(model))
    assertEquals(AiMemoryPolicy.SkipApp, skipped.extraction.skipped)
    assertEquals(0, model.requests.size)
    val fromMail = turn("sono in 4F al Galilei").copy(app = "dev.pampa.mail", appLabel = "Pampa Mail")
    assertEquals(OutcomePending, b.afterTurn(fromMail, extractor(model)).outcomes.single().result)
    // Lo strumento della memoria, chiamato dal modello coi dati di Notes: la strada per le altre app. Di serie chiede.
    val tool = b.remember(
      AiMemoryProposal("studia chimica con gli appunti", "studio.chimica", listOf("chimica")),
      AiMemorySource.tool(1L, "dev.pampa.pampanotes", "Pampa Notes"),
    )
    assertEquals(OutcomePending, tool.result)
    assertEquals(AiMemorySource.KindTool, b.pending.value.single { it.id == tool.notice!!.id }.source.kind)
  }

  @Test fun `i fatti imparati dalla ricerca seguono l'interruttore - spento chiedono uno per uno, acceso entrano da soli`() = runBlocking {
    // La risposta 2 di Alessio alle domande di F9.3b (C, 26/11/2026).
    val b = book()
    val gallery = AiMemorySource.research("dev.pampa.mycloudgallery", "MyCloud Gallery")
    fun learned(text: String, key: String) = AiMemoryProposal(
      text, key, listOf("vacanze"), origin = AiMemory.OriginInference, limitations = "solo dalle foto degli ultimi due anni",
      evidence = listOf(AiMemoryEvidence("e-$key", grant = "g-gallery", app = "dev.pampa.mycloudgallery", field = "foto.luoghi")),
    )
    val first = b.remember(learned("passa le vacanze d'estate al mare a Cecina", "luoghi.estate"), gallery)
    val second = b.remember(learned("va in montagna a Natale", "luoghi.inverno"), gallery)
    assertEquals(listOf(OutcomePending, OutcomePending), listOf(first.result, second.result))
    assertTrue(b.memories.value.isEmpty())
    assertEquals(2, b.pending.value.size)
    assertTrue(b.pending.value.all { it.kind == AiMemoryNotice.KindAsk && it.source.kind == AiMemorySource.KindResearch })
    // Una per una: rispondere a una non tocca l'altra.
    assertEquals(OutcomeRemembered, b.answer(first.notice!!.id, accept = true).result)
    assertEquals(listOf(second.notice!!.id), b.pending.value.map { it.id })
    // Acceso: entrano da soli, coi loro limiti e la loro fonte (la pagina li segna «Imparato dai dati di …»).
    b.setAutoSave(true)
    val third = b.remember(learned("fa foto ai tramonti in spiaggia", "foto.soggetti"), gallery)
    assertEquals(OutcomeRemembered, third.result)
    val m = b.memories.value.single { it.key == "foto.soggetti" }
    assertEquals(AiMemory.OriginInference, m.origin)
    assertEquals(gallery, m.source)
    assertEquals("solo dalle foto degli ultimi due anni", m.limitations)
    assertEquals(1, b.pending.value.size)
  }

  /** Il database di PampAI alla versione 7, con le sole tabelle del profilo (le colonne di `PampaiDatabase/7.json`). */
  private fun fakeProfileDb(): File {
    val file = tmp.newFile("pampai-profilo.db")
    file.delete()
    val now = MemoryTestTime.Now
    val day = MemoryTestTime.Day
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL("CREATE TABLE aria_grants (id TEXT NOT NULL, app TEXT NOT NULL, authority TEXT NOT NULL, account TEXT NOT NULL, student TEXT, area TEXT NOT NULL, year TEXT, version INTEGER NOT NULL, enabled INTEGER NOT NULL, generationProof TEXT, proofExpiresAt INTEGER, PRIMARY KEY(id))")
      db.execSQL("CREATE TABLE aria_evidence (id TEXT NOT NULL, jobId TEXT NOT NULL, grantId TEXT NOT NULL, recordKey TEXT NOT NULL, periodKey TEXT NOT NULL, field TEXT NOT NULL, value TEXT NOT NULL, unit TEXT, periodStart INTEGER, periodEnd INTEGER, timeZone TEXT NOT NULL, observedAt INTEGER, syncedAt INTEGER, coverage TEXT NOT NULL, freshness TEXT NOT NULL, provenance TEXT NOT NULL, contentHash TEXT NOT NULL, valid INTEGER NOT NULL, valueType TEXT NOT NULL, PRIMARY KEY(id))")
      db.execSQL("CREATE TABLE aria_profile (id TEXT NOT NULL, field TEXT NOT NULL, value TEXT NOT NULL, kind TEXT NOT NULL, jobId TEXT, revision INTEGER NOT NULL, status TEXT NOT NULL, generatedProvider TEXT, generatedModel TEXT, derivation TEXT, limitations TEXT, valueType TEXT NOT NULL, PRIMARY KEY(id))")
      db.execSQL("CREATE TABLE aria_dependencies (itemId TEXT NOT NULL, evidenceId TEXT NOT NULL, PRIMARY KEY(itemId, evidenceId))")
      db.execSQL("CREATE TABLE aria_exclusions (key TEXT NOT NULL, field TEXT, grantId TEXT, recordKey TEXT, unmapped INTEGER NOT NULL, PRIMARY KEY(key))")
      fun grant(id: String, app: String) = db.insert("aria_grants", null, ContentValues().apply {
        put("id", id); put("app", app); put("authority", "$app.research"); put("account", "giulia"); put("area", "all")
        put("version", 1); put("enabled", 1)
      })
      grant("g-fluidify", "dev.pampa.fluidify")
      grant("g-gallery", "dev.pampa.mycloudgallery")
      fun evidence(id: String, grant: String, record: String, field: String, observed: Long, valid: Boolean = true) =
        db.insert("aria_evidence", null, ContentValues().apply {
          put("id", id); put("jobId", "j1"); put("grantId", grant); put("recordKey", record); put("periodKey", "p")
          put("field", field); put("value", "x"); put("timeZone", "Europe/Rome"); put("observedAt", observed)
          put("coverage", "full"); put("freshness", "fresh"); put("provenance", "app"); put("contentHash", "h-$id")
          put("valid", if (valid) 1 else 0); put("valueType", "text")
        })
      evidence("e1", "g-fluidify", "track-1", "fluidify.history_records", now - 2 * day)
      evidence("e2", "g-gallery", "album-1", "foto.luoghi", now - 10 * day)
      evidence("e3", "g-gallery", "album-2", "foto.luoghi", now - day, valid = false)
      fun item(id: String, field: String, value: String, kind: String, status: String = "ACTIVE", limitations: String? = null, model: String? = null) =
        db.insert("aria_profile", null, ContentValues().apply {
          put("id", id); put("field", field); put("value", value); put("kind", kind); put("jobId", "j1"); put("revision", 1)
          put("status", status); put("valueType", "text")
          model?.let { put("generatedProvider", "groq"); put("generatedModel", it) }
          limitations?.let { put("limitations", it) }
        })
      item("p1", "musica.artista_preferito", "Coldplay", "INFERENCE", limitations = "solo dagli ultimi 30 giorni di ascolti", model = "llama-3.3-70b")
      item("p2", "luoghi.vacanze", "al mare a Cecina", "OBSERVATION")
      item("p3", "nome", "Giulia", "USER_STATEMENT")
      item("p4", "musica.genere", "rock", "INFERENCE", status = "SUPERSEDED")
      item("p5", "salute.allergie", "noci", "INFERENCE")
      listOf("p1" to "e1", "p2" to "e2", "p2" to "e3", "p5" to "e2").forEach { (i, e) ->
        db.insert("aria_dependencies", null, ContentValues().apply { put("itemId", i); put("evidenceId", e) })
      }
      fun exclusion(key: String, field: String?, grant: String?, record: String?, unmapped: Boolean = false) =
        db.insert("aria_exclusions", null, ContentValues().apply {
          put("key", key); put("field", field); put("grantId", grant); put("recordKey", record); put("unmapped", if (unmapped) 1 else 0)
        })
      exclusion("x1", "salute", null, null)
      exclusion("x2", null, "g-fluidify", "track-9")
      exclusion("x3", "unmapped.preference", null, null, unmapped = true)
    }
    return file
  }

  @Test fun `il profilo di PampAI v7 entra nella memoria - prove, fonti, esclusioni, e la revoca di una fonte`() = runBlocking {
    val labels = mapOf("dev.pampa.fluidify" to "Fluidify", "dev.pampa.mycloudgallery" to "MyCloud Gallery")
    val export = AiMemoryLegacyImport.readPampaiProfile(fakeProfileDb()) { labels[it] }
    assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), export.rows.map { it.id })
    // La prova non valida non passa; l'app viene dall'autorizzazione.
    assertEquals(listOf("e2"), export.rows.single { it.id == "p2" }.evidence.map { it.id })
    assertEquals("dev.pampa.fluidify", export.rows.single { it.id == "p1" }.app)
    assertEquals("groq/llama-3.3-70b", export.rows.single { it.id == "p1" }.derivedBy)
    assertEquals(1, export.unmapped)
    assertEquals(
      listOf(AiMemoryExclusion(key = "salute"), AiMemoryExclusion(app = "dev.pampa.fluidify", grant = "g-fluidify", record = "track-9")),
      export.exclusions,
    )

    val b = book()
    val result = b.importProfile(export.rows, export.exclusions)
    assertEquals(3, result.imported)
    assertEquals(1, result.inactive)
    assertEquals(1, result.excluded)
    assertEquals(2, result.exclusions)
    val coldplay = b.memories.value.single { it.key == "musica.artista_preferito" }
    assertEquals(AiMemory.OriginInference, coldplay.origin)
    assertEquals(AiMemorySource.research("dev.pampa.fluidify", "Fluidify"), coldplay.source)
    assertEquals(MemoryTestTime.Now - 2 * MemoryTestTime.Day, coldplay.observedAtMillis)
    // Nel prompt un fatto dedotto lo dice, coi suoi limiti.
    val line = AiMemoryPrompt.line(coldplay, zone = MemoryTestTime.Zone)
    assertTrue(line, line.contains("da Fluidify") && line.contains("dedotto, non detto: solo dagli ultimi 30 giorni di ascolti"))
    assertEquals(AiMemory.OriginStatement, b.memories.value.single { it.key == "nome" }.origin)
    // Rifarlo non duplica.
    assertEquals(5, b.importProfile(export.rows, export.exclusions).alreadyImported)
    // La revoca della fonte di Fluidify toglie cio' che era dedotto da lei.
    assertEquals(1, b.forgetFromGrant("g-fluidify"))
    assertTrue(b.memories.value.none { it.key == "musica.artista_preferito" })
    // Un'esclusione sul tipo di dato delle foto toglie l'osservazione che ne viene, non le cose dette dall'utente.
    assertEquals(1, b.exclude(AiMemoryExclusion(key = "foto.luoghi")))
    assertEquals(listOf("nome"), b.memories.value.map { it.key })
  }

  @Test fun `AICore allarga la domanda con le parole dell'argomento, e se tarda la ricerca non aspetta`() = runBlocking {
    val vegetarian = AiMemoryProposal("è vegetariana", "cibo.dieta", listOf("cibo", "cena", "ricette"))
    val device = AiMemoryOnDeviceModel { _, input, _ -> if ("pizza" in input) "cena, ristorante, cibo" else "" }
    val plain = book(config = AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true))
    plain.remember(vegetarian, you)
    assertTrue(plain.relevant("dove prendo una pizza stasera?").isEmpty())
    val withDevice = book(config = AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true, onDevice = device))
    withDevice.remember(vegetarian, you)
    assertEquals(listOf("è vegetariana"), withDevice.relevant("dove prendo una pizza stasera?").map { it.memory.text })
    // Un modello che non risponde in tempo: niente parole in piu', e nessuna attesa oltre il tetto.
    val slow = AiMemoryOnDeviceModel { _, _, _ -> delay(5_000); "cena" }
    val started = System.nanoTime()
    assertTrue(AiMemoryQueryExpansion.expand(slow, "dove prendo una pizza?", timeoutMillis = 50).isEmpty())
    assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000)
    assertEquals(listOf("cena", "ristorante", "cibo"), AiMemoryQueryExpansion.parse("Cena, ristorante,\n- cibo."))
  }

  @Test fun `l'estrattore sul telefono segue le stesse regole e non ha consumi da contare`() = runBlocking {
    var calls = 0
    val device = AiMemoryOnDeviceModel { _, input, _ ->
      calls++
      if ("4F" in input) FakeMemoryModel.facts(FakeMemoryModel.fact("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola"))) else FakeMemoryModel.facts()
    }
    val ex = AiMemoryExtractor.onDevice(device, zone = MemoryTestTime.Zone)
    assertTrue(ex.onDevice)
    val out = ex.extract(turn("sono in 4F al Galilei"))
    assertEquals(listOf("è in 4F al liceo Galilei"), out.proposals.map { it.text })
    assertNull(out.usage)
    // Il filtro locale vale anche qui: il meteo non chiama nessuno.
    assertEquals(AiMemoryPolicy.SkipNoSignal, ex.extract(turn("che tempo fa domani?")).skipped)
    assertEquals(1, calls)
  }
}
