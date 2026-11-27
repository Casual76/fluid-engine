package dev.antigravity.fluidengine.search

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.ai.bridge.AiToolClient
import dev.antigravity.fluidengine.ai.bridge.RemoteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R3-M3 · Le correzioni della review R3 nella ricerca (Muse 10, 19, 26): il tool dei vettori che impone il suo tetto,
 * l'indicizzatore che si ferma su una sorgente che non va avanti, e quello che un'app mostra di serie alle altre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class R3SearchTest {

  /** Un modello finto che conta quanti testi riceve per chiamata, e quanto sono lunghi. */
  private class CountingEmbedder : FluidTextEmbedder {
    override val space: FluidEmbeddingSpace = FluidEmbeddingSpace("prova", 4)
    val calls = ArrayList<List<String>>()
    override suspend fun embed(texts: List<String>): List<FloatArray> {
      calls += texts
      return texts.map { floatArrayOf(1f, 0f, 0f, 0f) }
    }
  }

  private fun texts(count: Int, text: (Int) -> String = { "testo numero $it" }): JsonObject =
    buildJsonObject { putJsonArray("texts") { repeat(count) { add(JsonPrimitive(text(it))) } } }

  // --- Muse 10 · il tool dei vettori --------------------------------------------------------------------------------

  @Test
  fun ilToolDeiVettoriNonCalcolaPiuDiTrentaduTesti() = runBlocking {
    // Chiamato dentro l'app (l'orchestratore di PampAI, che di serie non controlla lo schema): 500 testi in una volta.
    val model = CountingEmbedder()
    val out = FluidSearchBridge.embedTool<Unit>(model).run(texts(500), Unit)
    val largest = model.calls.maxOfOrNull { it.size } ?: 0
    assertTrue("il modello ha ricevuto $largest testi in una chiamata", largest <= FluidSearchBridge.MaxEmbedTexts)
    assertTrue("la risposta non dice che sono troppi: ${out.text}", out.text.startsWith("errore"))
    // Al tetto si risponde come sempre.
    val ok = FluidSearchBridge.embedTool<Unit>(model).run(texts(FluidSearchBridge.MaxEmbedTexts), Unit)
    assertEquals("vettori: ${FluidSearchBridge.MaxEmbedTexts}", ok.text)
  }

  @Test
  fun unTestoLunghissimoArrivaAlModelloTagliato() = runBlocking {
    val model = CountingEmbedder()
    FluidSearchBridge.embedTool<Unit>(model).run(texts(2) { "parola ".repeat(150_000) }, Unit)
    val longest = model.calls.flatten().maxOf { it.length }
    assertTrue("al modello arrivano $longest caratteri in un testo", longest <= 4_096)
  }

  @Test
  fun dalPonteITroppiTestiSiFermanoGiaAllaPorta() = runBlocking {
    // La strada fra due app: l'ospite controlla gli argomenti con lo schema (maxItems) prima di chiamare il tool.
    val authority = "dev.pampa.pampai.r3.ai.tools"
    Robolectric.buildContentProvider(FluidPampaSearchTest.ModelApp::class.java).create(authority)
    val client = AiToolClient(ApplicationProvider.getApplicationContext())
    val result = client.run(authority, FluidSearchBridge.EmbedToolName, texts(40), confirmed = false, requestId = "r3", language = "it")
    assertTrue("risposta: $result", result is RemoteResult.Error)
  }

  // --- Muse 19 · l'indicizzatore -----------------------------------------------------------------------------------

  /** Una sorgente sbagliata: dice sempre «ce n'è ancora» senza dare niente e senza far avanzare il segno. */
  private class Stuck : FluidSearchSource {
    override val id: String = "ferma"
    var calls = 0
    override suspend fun changes(cursor: String?, limit: Int): FluidSearchChanges {
      calls++
      return FluidSearchChanges(cursor = "pagina-1", hasMore = true)
    }
  }

  @Test
  fun unaSorgenteCheNonVaAvantiFermaIlGiro() = runBlocking {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    val source = Stuck()
    val indexer = FluidSearchIndexer(index, source)
    // In primo piano non c'e' una scadenza: prima il giro non finiva mai.
    val light = withTimeoutOrNull(5_000) { indexer.update() }
    assertNotNull("il giro in primo piano non è finito (${source.calls} chiamate alla sorgente)", light)
    assertNotNull("l'errore non è detto", light!!.error)
    assertFalse("resta «da fare»: il lavoro in carica la richiamerebbe subito", light.hasMore)
    assertTrue("la sorgente chiamata ${source.calls} volte", source.calls <= 3)
    // In carica: niente giro fino alla scadenza, e niente «ancora da fare» (il lavoro si rimetterebbe in coda).
    source.calls = 0
    val started = SystemClock.elapsedRealtime()
    val more = indexer.runHeavy(SystemClock.elapsedRealtime() + 3_000) { false }
    assertFalse(more)
    assertTrue("il giro in carica è durato ${SystemClock.elapsedRealtime() - started} ms", SystemClock.elapsedRealtime() - started < 1_000)
    assertTrue(source.calls <= 2)
    index.close()
  }

  @Test
  fun unaPaginaVuotaColSegnoNuovoNonEUnErrore() = runBlocking {
    // Una sorgente che salta i documenti che non vuole indicizzare: pagina vuota, ma il segno avanza.
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    val docs = FluidSearchSamples.generated(30)
    val source = object : FluidSearchSource {
      override val id: String = "salta"
      override suspend fun changes(cursor: String?, limit: Int): FluidSearchChanges {
        val page = cursor?.toInt() ?: 0
        val upserts = if (page % 2 == 1) emptyList() else docs.drop(page / 2 * 10).take(10)
        return FluidSearchChanges(upserts = upserts, cursor = (page + 1).toString(), hasMore = page < 5)
      }
    }
    val done = FluidSearchIndexer(index, source).update(heavy = true)
    assertEquals(null, done.error)
    assertEquals(30, index.count())
    index.close()
  }

  // --- Sospetto 7 di R1 · close() durante un'operazione ------------------------------------------------------------

  /** Una cassaforte che, come quella vera (`VaultSection.withKey`, su `FluidVault.io`), cifra su un altro filo e torna. */
  private class OtherThreadCipher : FluidSearchCipher {
    override suspend fun seal(plain: ByteArray, associated: ByteArray): ByteArray = withContext(Dispatchers.Default) {
      Thread.sleep(2)
      plain.reversedArray()
    }

    override suspend fun open(sealed: ByteArray, associated: ByteArray): ByteArray =
      withContext(Dispatchers.Default) { sealed.reversedArray() }
  }

  @Test
  fun chiudereLIndiceDuranteUnaScritturaNonLoLasciaBloccato() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val name = "r3-chiusura-${System.nanoTime()}"
    val cipher = OtherThreadCipher()
    val first = FluidSearchIndex.open(context, FluidSearchSamples.schema, cipher, name)
    // Una scrittura lunga (400 note, la cassaforte per ognuna), e l'app che chiude l'indice a meta'.
    val writing = async(Dispatchers.Default) { runCatching { first.put(FluidSearchSamples.generated(400)) } }
    delay(150)
    first.close()
    val outcome = withTimeoutOrNull(15_000) { writing.await() }
    assertNotNull("la scrittura in corso non è più finita", outcome)
    // L'app riapre l'indice (lo ricrea): si deve poter scrivere e cercare.
    val again = withTimeoutOrNull(30_000) {
      runCatching {
        val idx = FluidSearchIndex.open(context, FluidSearchSamples.schema, cipher, name)
        idx.put(FluidSearchSamples.generated(5))
        idx
      }
    }
    assertNotNull("l'indice riaperto non risponde", again)
    val index = again!!.getOrElse { throw AssertionError("l'indice riaperto non si scrive: $it", it) }
    assertTrue(index.count() >= 5)
    index.destroy()
  }

  // --- Muse 26 · cosa un'app mostra alle altre -------------------------------------------------------------------

  private fun query(text: String) = buildJsonObject { put("query", text) }

  private fun total(out: dev.antigravity.fluidengine.ai.tools.ToolOutput): Int =
    out.result!!.data!!["total"]!!.jsonPrimitive.content.toInt()

  @Test
  fun diSerieIlToolNonMostraNessunTipo() = runBlocking {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    index.put(FluidSearchSamples.of("notes"))
    // L'app aggiunge il tool in una riga, senza scegliere niente: non mostra niente alle altre app.
    val bare = FluidSearchBridge.searchTool<Unit>(index)
    assertEquals(0, total(bare.run(query("riunione"), Unit)))
    // Coi tipi elencati, solo quelli.
    val notes = FluidSearchBridge.searchTool<Unit>(index, FluidSearchExposure(kinds = setOf("nota")))
    val out = notes.run(query("riunione"), Unit)
    assertTrue(total(out) > 0)
    assertTrue(out.result!!.data.toString(), out.result!!.data.toString().contains("\"kind\":\"nota\""))
    assertFalse(out.result!!.data.toString().contains("\"kind\":\"trascrizione\""))
    // Tutti, se l'app lo dice (null).
    val all = FluidSearchBridge.searchTool<Unit>(index, FluidSearchExposure(kinds = null)).run(query("riunione"), Unit)
    assertTrue(all.result!!.data.toString().contains("\"kind\":\"trascrizione\""))
    index.close()
  }

  @Test
  fun unTettoDeiRisultatiFuoriMisuraSiFermaAllaNascita() {
    try {
      FluidSearchExposure(maxHits = FluidSearchQuery.MaxLimit + 1)
      fail("un'esposizione che fa fallire ogni ricerca è nata")
    } catch (expected: IllegalArgumentException) {
    }
    try {
      FluidSearchExposure(maxHits = 0)
      fail("un'esposizione che fa fallire ogni ricerca è nata")
    } catch (expected: IllegalArgumentException) {
    }
  }
}
