package dev.antigravity.fluidengine.search

import dev.antigravity.fluidengine.ai.bridge.BridgeCalls
import dev.antigravity.fluidengine.ai.bridge.RemotePart
import dev.antigravity.fluidengine.ai.bridge.RemoteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R3-M3 · Le API nuove di R3 nella ricerca: l'errore dell'indice nello stato della pagina ([FluidSearchState.failure],
 * Muse 18) e la lingua di «Cerca in Pampa» ([FluidPampaSearch.language], Muse 26).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class R3SearchStateTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  private fun state(index: FluidSearchIndex) = FluidSearchState(index, scope, limit = 50, typos = true)

  @Test
  fun unIndiceChiusoEUnErroreNonUnVuoto() = runBlocking {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    index.put(FluidSearchSamples.of("notes"))
    val state = state(index)
    state.run(SearchInput("riunione", emptySet(), 0))
    assertTrue(state.results.hits.isNotEmpty())
    assertNull(state.failure)
    // L'app chiude l'indice (lo ricrea) mentre la pagina e' aperta.
    index.close()
    state.run(SearchInput("verbale", emptySet(), 0))
    assertNotNull("l'errore dell'indice non si vede", state.failure)
    assertEquals("verbale", state.results.query)
    assertTrue("restano i risultati di prima", state.results.hits.isEmpty())
    assertTrue(state.completions.isEmpty())
    // Il campo vuotato toglie anche l'errore.
    state.run(SearchInput("", emptySet(), 0))
    assertNull(state.failure)
  }

  @Test
  fun unModelloCheNonParteNonEUnErroreDellIndice() = runBlocking {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    index.put(FluidSearchSamples.of("mail"))
    FluidSemanticIndexer(index, FixtureEmbedder()).update()
    val state = state(index)
    state.embedder = object : FluidTextEmbedder {
      override val space: FluidEmbeddingSpace = FluidEmbeddingSpace.EmbeddingGemma2
      override suspend fun embed(texts: List<String>): List<FloatArray> = error("il modello non parte")
    }
    state.run(SearchInput("appuntamento", emptySet(), 0))
    assertNull(state.failure)
    assertTrue("le parole restano", state.results.hits.isNotEmpty())
    index.close()
  }

  /** Il ponte finto: ricorda la lingua di ogni chiamata e risponde che l'app non c'e'. */
  private class Calls : BridgeCalls {
    val languages = ArrayList<String>()
    override suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>? = null
    override suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult {
      synchronized(languages) { languages += language }
      return RemoteResult.Error("no")
    }
    override suspend fun status(authority: String, jobId: String): RemoteResult = RemoteResult.Error("no")
    override suspend fun cancel(authority: String, jobId: String) = Unit
    override fun readPart(part: RemotePart): ByteArray? = null
  }

  @Test
  fun laLinguaDiChiCercaArrivaAlleApp() = runBlocking {
    val apps = listOf(FluidPampaApp("a", "a.ai.tools", "A"), FluidPampaApp("b", "b.ai.tools", "B"))
    val english = Calls()
    FluidPampaSearch(english, discover = { apps }, language = "en").search("meeting").toList()
    assertEquals(listOf("en", "en"), english.languages)
    // Di serie resta l'italiano, come le altre parole dell'engine.
    val italian = Calls()
    FluidPampaSearch(italian, discover = { apps }).search("riunione").toList()
    assertEquals(listOf("it", "it"), italian.languages)
  }
}
