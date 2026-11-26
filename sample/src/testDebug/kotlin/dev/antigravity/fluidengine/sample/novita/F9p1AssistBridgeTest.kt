package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsent
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistEntry
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsentAnswer
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistError
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHost
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHostProvider
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsentState
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistProgress
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistRequest
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistTask
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

/**
 * F9.1 · Il ponte dell'assistenza con Android vero (Robolectric): il cancello dell'ospite (la firma, il pacchetto dal
 * sistema, la tabella), e la galleria che chiede al suo PampAI finto attraverso `ContentResolver`, il `Binder` del canale
 * di ritorno e il provider del manifest di debug. Sta nel sample come le altre prove del ponte: un'app che include il
 * modulo non deve scaricare Robolectric.
 *
 * Il `PackageManager` di Robolectric risponde sempre «firme uguali»: il confronto e i pacchetti dell'uid stanno in
 * [GatedHost], come in `AiToolHostProviderGuardTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F9p1AssistBridgeTest {

  /** L'ospite finto con un cancello comandato dalla prova. */
  class GatedHost : AiAssistHostProvider() {
    var comparison: Int = PackageManager.SIGNATURE_MATCH
    var packages: Array<String>? = null
    val compared = ArrayList<Pair<Int, Int>>()

    override fun host(): AiAssistHost = F9p1FakePampai.host(context!!)

    override fun compareSignatures(callingUid: Int, ownUid: Int): Int {
      compared += callingUid to ownUid
      return comparison
    }

    override fun packagesForUid(uid: Int): Array<String>? = packages ?: super.packagesForUid(uid)
  }

  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val pkg: String get() = context.packageName

  @Before fun clean() {
    F9p1FakePampai.host(context).forget(pkg)
    F9p1FakePampai.history.forget()
    // La galleria ha gia' detto sì a PampAI finto: il primo uso ha le sue prove, sotto.
    F9p1FakePampai.host(context).setAppConsent(pkg, AiAssistConsentState.Allowed)
  }

  @After fun stop() {
    F9p1FakePampai.host(context).cancelAll()
    ShadowBinder.reset()
  }

  private fun gated(): GatedHost {
    val info = ProviderInfo().apply {
      authority = "$pkg.ai.assist.prova"
      name = GatedHost::class.java.name
      packageName = pkg
      exported = true
    }
    return Robolectric.buildContentProvider(GatedHost::class.java).create(info).get()
  }

  private fun call(provider: GatedHost, method: String, json: String = "{}"): JsonObject {
    val out = provider.call(method, null, Bundle().apply { putString("json", json) })
    return Json.parseToJsonElement(out.getString("json")!!).jsonObject
  }

  private fun JsonObject.error(): String? = this["error"]?.toString()?.trim('"')

  private val ask = """{"protocol":3,"task":{"kind":"answer","text":"Ciao"},"tools":"caller","lang":"it","timeoutMillis":5000}"""

  @Test fun `le scene sono nell'indice`() {
    assertEquals(listOf("pampai-chiedi", "pampai-assente", "pampai-errori", "pampai-misure"), F9p1Section.scenes.map { it.id })
    assertTrue(NewElementSections.contains(F9p1Section))
    assertEquals(6, F9p1TryItems.size)
  }

  @Test fun `una firma diversa riceve SecurityException su ogni metodo, anche sconosciuto`() {
    val provider = gated().apply { comparison = PackageManager.SIGNATURE_NO_MATCH }
    ShadowBinder.setCallingUid(40_001)
    listOf("negotiate", "assist_scope", "assist_ask", "assist_poll", "assist_cancel", "metodo-che-non-esiste").forEach { method ->
      assertThrows("$method deve essere negato", SecurityException::class.java) { call(provider, method, ask) }
    }
    assertTrue("il cancello confronta chi chiama con l'ospite", provider.compared.all { it.first == 40_001 })
    assertEquals(0, F9p1FakePampai.host(context).inFlight(pkg))
  }

  @Test fun `un uid condiviso da due pacchetti non passa`() {
    val provider = gated().apply { packages = arrayOf(pkg, "dev.prova.compagna") }
    assertThrows(SecurityException::class.java) { call(provider, "assist_scope") }
  }

  @Test fun `il pacchetto lo dice il sistema - un'app fuori tabella non passa, qualunque cosa scriva`() {
    val provider = gated().apply { packages = arrayOf("dev.prova.altra") }
    assertEquals(AiAssistError.NotAuthorized, call(provider, "assist_scope").error())
    // Una richiesta che si dichiara la galleria non cambia niente: il formato non ha un campo per chi chiama.
    val forged = ask.replace("\"protocol\":3", "\"protocol\":3,\"package\":\"$pkg\",\"caller\":\"$pkg\"")
    assertEquals(AiAssistError.NotAuthorized, call(provider, "assist_ask", forged).error())
    assertEquals(0, F9p1FakePampai.host(context).inFlight("dev.prova.altra"))
  }

  @Test fun `la galleria passa, con l'ambito della tabella del PampAI finto`() {
    val provider = gated().apply { packages = arrayOf(pkg) }
    val scope = call(provider, "assist_scope")
    assertNull(scope.error())
    assertEquals("""["answer","summarize"]""", scope["tasks"].toString())
    assertEquals("true", scope["page"].toString())
    assertEquals("\"not_asked\"", scope["consent"].toString())
    assertEquals("\"allowed\"", scope["app"].toString())
    assertEquals("""{"protocol":3,"capabilities":["assist","assist_stream"]}""", call(provider, "negotiate", """{"versions":[3],"capabilities":["assist","assist_stream"]}""").toString())
    assertNotNull("un ospite dei tool v1/v2 che chiedesse qui non trova la sua versione", call(provider, "negotiate", """{"versions":[1,2]}""").error())
  }

  @Test fun `una domanda attraversa il ponte vero - la riga di stato con l'app, i pezzi, la fine`() = runBlocking {
    val states = F9p1FakePampai.client(context).ask(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"))).toList()
    val last = states.last()
    assertTrue("${last.error}", last.done)
    assertTrue(last.text.startsWith("Non vedo la pagina"))
    assertTrue(states.any { it.status?.line == "Controllo i voti" && it.status?.app == pkg })
    assertTrue("arriva a pezzi: ${states.size} stati", states.count { it.text.isNotEmpty() && !it.finished } >= 3)
    states.zipWithNext().forEach { (a, b) -> assertTrue(b.text.startsWith(a.text)) }
  }

  @Test fun `la pagina - niente prima del sì, poi passa, la revoca vale dalla domanda dopo`() = runBlocking {
    val client = F9p1FakePampai.client(context)
    val withPage = AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = F9p1Page)
    assertEquals(AiAssistError.ConsentRequired, client.run(withPage).error?.code)
    assertEquals(AiAssistConsentState.NotAsked, client.check().pageConsent)
    F9p1FakePampai.host(context).setPageConsent(pkg, AiAssistConsentState.Allowed)
    val answer = client.run(withPage)
    assertTrue("${answer.error}", answer.done)
    assertTrue("il modello finto ha letto la pagina", answer.text.contains(F9p1Page.title))
    F9p1FakePampai.host(context).setPageConsent(pkg, AiAssistConsentState.Denied)
    assertEquals(AiAssistError.ConsentRequired, client.run(withPage).error?.code)
    assertEquals(AiAssistConsentState.Denied, client.check().pageConsent)
  }

  @Test fun `bloccare la galleria da PampAI ferma la domanda in corso e le prossime`() = runBlocking {
    val client = F9p1FakePampai.client(context)
    var last: AiAssistProgress? = null
    val job = launch(kotlinx.coroutines.Dispatchers.Default) { client.ask(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskLong))).collect { last = it } }
    waitUntil { last?.text?.isNotEmpty() == true }
    F9p1FakePampai.host(context).setBlocked(pkg, true)
    job.join()
    assertEquals(AiAssistError.NotAuthorized, last?.error?.code)
    assertEquals(AiAssistError.NotAuthorized, client.check().error?.code)
    F9p1FakePampai.host(context).setBlocked(pkg, false)
    assertTrue(client.check().ready)
  }

  @Test fun `PampAI che non c'e'`() = runBlocking {
    val absent = F9p1FakePampai.absentClient(context)
    assertEquals(AiAssistError.NotInstalled, absent.run(AiAssistRequest(AiAssistTask.answer("Ciao"))).error?.code)
    assertEquals(AiAssistError.NotInstalled, absent.check().error?.code)
    assertNull(absent.consentIntent())
  }

  @Test fun `annullare ferma il lavoro nel PampAI finto`() = runBlocking {
    var last: AiAssistProgress? = null
    val job = launch(kotlinx.coroutines.Dispatchers.Default) { F9p1FakePampai.client(context).ask(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskLong))).collect { last = it } }
    waitUntil { last?.text?.isNotEmpty() == true }
    assertEquals(1, F9p1FakePampai.host(context).inFlight(pkg))
    job.cancel()
    waitUntil { F9p1FakePampai.host(context).inFlight(pkg) == 0 }
  }

  @Test fun `il tempo massimo, il limite e l'ambito arrivano come codici, con le parole dell'engine`() = runBlocking {
    val client = F9p1FakePampai.client(context)
    val slow = client.run(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskSlow), timeoutMillis = 1_000))
    assertEquals(AiAssistError.Timeout, slow.error?.code)
    val limit = client.run(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskLimit)))
    assertEquals(AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 120_000), limit.error)
    assertEquals("Limite raggiunto: riprova fra 2 minuti", limit.error?.message())
    val rewrite = client.run(AiAssistRequest(AiAssistTask.rewrite("ciao prof", AiAssistTask.StyleProfessional)))
    assertEquals("PampAI non ha autorizzato quest'app", rewrite.error?.message())
  }

  @Test fun `la pagina del PampAI finto aperta senza startActivityForResult non chiede niente`() {
    val intent = Intent(AiAssistConsent.ACTION).setPackage(pkg)
    val activity = Robolectric.buildActivity(F9p1FakePampaiActivity::class.java, intent).create().get()
    assertTrue(activity.isFinishing)
    assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
  }

  @Test fun `col sì gia' dato, la pagina del consenso risponde subito all'app che la apre`() {
    F9p1FakePampai.host(context).setPageConsent(pkg, AiAssistConsentState.Allowed)
    val controller = Robolectric.buildActivity(F9p1FakePampaiActivity::class.java, Intent(AiAssistConsent.ACTION).setPackage(pkg).putExtra(AiAssistConsent.EXTRA_WITH_PAGE, true))
    shadowOf(controller.get()).setCallingPackage(pkg)
    val activity = controller.create().get()
    assertTrue(activity.isFinishing)
    assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    assertEquals(AiAssistConsentAnswer(AiAssistConsentState.Allowed, AiAssistConsentState.Allowed), AiAssistConsent.resultOf(shadowOf(activity).resultCode, shadowOf(activity).resultIntent))
  }

  @Test fun `la domanda del sì ha le parole giuste, e la risposta scrive nel registro di PampAI`() {
    val consent = AiAssistConsent(F9p1FakePampai.host(context))
    val step = consent.begin(pkg, withPage = true) as AiAssistConsent.Step.Ask
    assertFalse("la galleria ha gia' il sì per l'app", step.askApp)
    assertEquals("Fluid Glass vuole mandare a PampAI la pagina che hai davanti quando le chiedi qualcosa. Il testo va al tuo fornitore di IA. Consenti?", step.question)
    assertEquals(AiAssistConsentState.Denied, consent.answer(step, step.choices.last()).page)
    assertEquals(AiAssistConsent.Step.Decided(AiAssistConsentState.Allowed, AiAssistConsentState.Denied), consent.begin(pkg, withPage = true))
    assertEquals(AiAssistConsent.Step.Refused, consent.begin("dev.prova.altra"))
    assertEquals(AiAssistConsent.Step.Refused, consent.begin(null))
  }

  @Test fun `la prima volta la domanda chiede il sì, e la pagina del PampAI finto chiede tutte e due le cose in un foglio solo`() = runBlocking {
    F9p1FakePampai.host(context).forget(pkg)
    val client = F9p1FakePampai.client(context)
    assertEquals(AiAssistConsentState.NotAsked, client.check().appConsent)
    val first = client.run(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = F9p1Page))
    assertEquals(AiAssistError.ConsentRequired, first.error?.code)
    assertEquals(setOf(AiAssistError.NeedsApp, AiAssistError.NeedsPage), first.error?.needs)
    // Il foglio: aperto dalla galleria con la pagina, resta aperto e chiede le due cose.
    val intent = client.consentIntent(page = true)!!
    assertTrue(AiAssistConsent.wantsPage(intent))
    val controller = Robolectric.buildActivity(F9p1FakePampaiActivity::class.java, intent)
    shadowOf(controller.get()).setCallingPackage(pkg)
    val activity = controller.create().get()
    assertFalse("il foglio aspetta la risposta", activity.isFinishing)
    val step = AiAssistConsent(F9p1FakePampai.host(context)).begin(pkg, withPage = true) as AiAssistConsent.Step.Ask
    assertTrue(step.askApp && step.askPage)
    assertEquals(listOf("Consenti", "Senza la pagina", "Non consentire"), step.choices.map { it.label })
    // «Consenti»: tutte e due, e la domanda passa con la pagina.
    AiAssistConsent(F9p1FakePampai.host(context)).answer(step, step.choices.first())
    val answer = client.run(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = F9p1Page))
    assertTrue("${answer.error}", answer.done)
    assertTrue(answer.text.contains(F9p1Page.title))
    activity.finish()
  }

  @Test fun `il no della prima volta si ricorda - not_authorized, nessuna domanda di nuovo, e si rimette da PampAI`() = runBlocking {
    F9p1FakePampai.host(context).forget(pkg)
    val consent = AiAssistConsent(F9p1FakePampai.host(context))
    val step = consent.begin(pkg) as AiAssistConsent.Step.Ask
    consent.answer(step, step.choices.last())
    val client = F9p1FakePampai.client(context)
    assertEquals(AiAssistError.NotAuthorized, client.check().error?.code)
    assertEquals(AiAssistError.NotAuthorized, client.run(AiAssistRequest(AiAssistTask.answer("Ciao"))).error?.code)
    // Il foglio si chiude da solo col «no».
    val controller = Robolectric.buildActivity(F9p1FakePampaiActivity::class.java, client.consentIntent()!!)
    shadowOf(controller.get()).setCallingPackage(pkg)
    val activity = controller.create().get()
    assertTrue(activity.isFinishing)
    assertEquals(AiAssistConsentState.Denied, AiAssistConsent.resultOf(shadowOf(activity).resultCode, shadowOf(activity).resultIntent)?.app)
    F9p1FakePampai.host(context).setAppConsent(pkg, AiAssistConsentState.Allowed)
    assertTrue(client.check().ready)
  }

  @Test fun `senza PampAI il tasto e' spento con Installa, e senza lo Store non c'e' un indirizzo`() = runBlocking {
    val absent = F9p1FakePampai.absentClient(context)
    val entry = AiAssistEntry.of(absent.check())
    assertFalse(entry.enabled)
    assertEquals(AiAssistEntry.ActionInstall, entry.action)
    assertEquals("Installa PampAI", entry.actionLabel)
    assertNull("lo Store inventato non c'e': la spiegazione breve", absent.installIntent())
    assertNull(absent.openIntent())
    // Il PampAI finto c'e' ma la galleria e' bloccata: nessun «Installa».
    F9p1FakePampai.host(context).setAppConsent(pkg, AiAssistConsentState.Denied)
    val blocked = AiAssistEntry.of(F9p1FakePampai.client(context).check())
    assertEquals(AiAssistEntry(false, "PampAI non ha autorizzato quest'app"), blocked)
  }

  @Test fun `le domande finiscono in Dalle app, senza le righe della pagina`() = runBlocking {
    F9p1FakePampai.host(context).setPageConsent(pkg, AiAssistConsentState.Allowed)
    F9p1FakePampai.client(context).run(AiAssistRequest(AiAssistTask.answer("Aggiungi la verifica di recupero al calendario"), page = F9p1Page))
    waitUntil { F9p1FakePampai.history.entries().isNotEmpty() }
    val e = F9p1FakePampai.history.entries().first()
    assertEquals("Fluid Glass", e.appName)
    assertEquals(F9p1Page.title, e.pageTitle)
    assertEquals("Verifica di recupero aggiunta al calendario: mercoledì 3 dicembre", e.receipts.single().line)
    assertTrue(e.answer.startsWith("Fatto"))
    F9p1Page.lines.forEach { assertFalse("«$it» e' nella cronologia", e.toString().contains(it)) }
    // F9.1c: di un testo da riassumere si salva tutto, e una domanda si toglie da sola, le altre restano.
    val text = "Circolare 42. " + "Le lezioni di venerdì finiscono alle 12 per l'assemblea di istituto. ".repeat(20)
    F9p1FakePampai.client(context).run(AiAssistRequest(AiAssistTask.summarize(text, AiAssistTask.FormKeyPoints)))
    waitUntil { F9p1FakePampai.history.entries().size == 2 }
    val summary = F9p1FakePampai.history.entries().first()
    assertEquals(text.trim(), summary.question)
    assertTrue(summary.answer.isNotBlank())
    assertTrue(F9p1FakePampai.history.remove(summary.requestId))
    assertEquals(listOf(e.requestId), F9p1FakePampai.history.entries().map { it.requestId })
  }

  @Test fun `la misura della memoria sta nel limite - aspetta quanto dice PampAI e rifa' la domanda`() = runBlocking {
    // F9.1d: sul tablet la misura faceva 50 domande di fila e dalla trentesima PampAI rispondeva «limite raggiunto».
    var calls = 0
    val limit = AiAssistProgress(error = AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 150))
    val done = AiAssistProgress(text = "pss=1234", done = true)
    val start = System.nanoTime()
    assertEquals(done, f9p1WithinLimit { calls++; if (calls == 1) limit else done })
    val waited = (System.nanoTime() - start) / 1_000_000
    assertEquals(2, calls)
    assertTrue("ha aspettato quanto diceva PampAI: $waited ms", waited >= 140)
    // Un altro errore torna subito, senza rifare la domanda.
    calls = 0
    val timeout = AiAssistProgress(error = AiAssistError(AiAssistError.Timeout))
    assertEquals(timeout, f9p1WithinLimit { calls++; timeout })
    assertEquals(1, calls)
    // Un limite che non passa: tre attese, poi l'ultima risposta com'e'.
    calls = 0
    val stuck = AiAssistProgress(error = AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 1))
    assertEquals(stuck, f9p1WithinLimit { calls++; stuck })
    assertEquals(4, calls)
    assertTrue(F9p1Measures(LongArray(0), true, DoubleArray(0), 0, DoubleArray(0), 0, 100, 101, 1, 48, 61_400).summary()
      .contains("dopo 48 domande (in 61 s, col limite di 30 al minuto) 101 kB"))
  }

  private suspend fun waitUntil(condition: () -> Boolean) {
    repeat(500) {
      if (condition()) return
      kotlinx.coroutines.delay(20)
    }
    assertFalse("non e' successo in tempo", true)
  }
}
