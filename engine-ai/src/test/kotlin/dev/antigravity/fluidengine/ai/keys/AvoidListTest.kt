package dev.antigravity.fluidengine.ai.keys

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelKind
import dev.antigravity.fluidengine.ai.provider.OpenRouterCatalog
import dev.antigravity.fluidengine.ai.provider.ProviderFactory
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.TierDefaults
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il Keystore non c'e' sul computer: la cifratura e' un involucro trasparente. */
private class NoopCipher : SecretCipher {
  override fun encrypt(plain: String): String = "plain:$plain"
  override fun decrypt(blob: String): String? = blob.removePrefix("plain:").takeIf { blob.startsWith("plain:") }
}

/**
 * La lista di cio' che non si propone mai ([AiDefaults.OPENROUTER_AVOID], 1.29.0): Inkling
 * risponde solo in un harness agentico, e un telefono che lo aveva come profondo diceva "non
 * posso" a ogni domanda complessa. Qui si verifica che nessuno lo scelga piu', e che una scelta
 * salvata su di lui si ripari da sola al riallineamento.
 */
class AvoidListTest {

  private val inkling = "thinkingmachines/inkling:free"
  private val inklingSmall = "thinkingmachines/inkling-small:free"

  private fun free(id: String, ctx: Int = 200_000, tools: Boolean = true) =
    ModelInfo(id, id, ModelKind.CHAT, contextWindow = ctx, supportsTools = tools, supportsReasoning = true, supportsVision = true, supportsDocuments = true, free = true)

  @Test
  fun `la lista da evitare ha Inkling, e null non e' da evitare`() {
    assertTrue(AiDefaults.avoided(inkling))
    assertTrue(AiDefaults.avoided(inklingSmall))
    assertFalse(AiDefaults.avoided("minimax/minimax-m3:free"))
    assertFalse(AiDefaults.avoided(null))
    assertTrue(AiDefaults.OPENROUTER_CHAT_PREFERRED.none { AiDefaults.avoided(it) })
    assertTrue(AiDefaults.OPENROUTER_DEEP_PREFERRED.none { AiDefaults.avoided(it) })
    assertTrue(AiDefaults.OPENROUTER_CLASSIFIER_PREFERRED.none { AiDefaults.avoided(it) })
  }

  @Test
  fun `il profondo e il gratuito predefinito non sono mai un modello da evitare, per quanto punteggi`() {
    // Inkling punteggerebbe come il migliore (ragiona, contesto enorme): non conta.
    val catalogue = ModelCatalogue(chat = listOf(free(inkling, ctx = 1_000_000), free("qwen/qwen3-235b:free", ctx = 131_072)), stt = emptyList())
    assertEquals("qwen/qwen3-235b:free", TierDefaults.pickDeep(ProviderId.OPENROUTER, catalogue, null)?.id)
    assertEquals("qwen/qwen3-235b:free", OpenRouterCatalog.pickDefaultFree(catalogue)?.id)
    assertTrue(OpenRouterCatalog.recommended(catalogue).none { AiDefaults.avoided(it.id) })
    assertEquals(Int.MIN_VALUE, OpenRouterCatalog.score(free(inkling)))
    // Solo modelli da evitare: nessun profondo, nessun gratuito predefinito, nemmeno come ripiego.
    val only = ModelCatalogue(chat = listOf(free(inkling), free(inklingSmall)), stt = emptyList())
    assertNull(TierDefaults.pickDeep(ProviderId.OPENROUTER, only, inkling))
    assertNull(OpenRouterCatalog.pickDefaultFree(only))
    // Con i preferiti in catalogo vincono loro, nel loro ordine.
    val preferred = ModelCatalogue(chat = listOf(free(inkling), free("nvidia/nemotron-3-ultra-550b-a55b:free"), free("minimax/minimax-m3:free")), stt = emptyList())
    assertEquals("minimax/minimax-m3:free", TierDefaults.pickDeep(ProviderId.OPENROUTER, preferred, "nvidia/nemotron-3.5-lightning:free")?.id)
  }

  /** Un verificatore su file temporanei: senza chiavi, quindi senza nessuna chiamata di rete. */
  private data class Fixture(val verifier: AiKeyVerifier, val settings: AiSettingsStore, val catalogs: ModelCatalogStore)

  private fun fixture(): Fixture {
    val dir = Files.createTempDirectory("ai-avoid").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = AiSettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "ai.preferences_pb") }))
    val keys = AiKeyStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "keys.preferences_pb") }), NoopCipher())
    val catalogs = ModelCatalogStore(File(dir, "models"))
    val factory = ProviderFactory(AiHttp("test"), keys, settings, "https://test.invalid", "test", catalogs)
    return Fixture(AiKeyVerifier(keys, settings, factory, catalogs), settings, catalogs)
  }

  private fun verifier(): Pair<AiKeyVerifier, AiSettingsStore> = fixture().let { it.verifier to it.settings }

  @Test
  fun `una scelta salvata su Inkling si sostituisce al riallineamento, una valida resta`() = runBlocking {
    val (verifier, settings) = verifier()
    val catalogue = ModelCatalogue(
      chat = listOf(
        free(inkling), free(inklingSmall), free("minimax/minimax-m3:free"), free("nvidia/nemotron-3.5-lightning:free"),
        free("google/gemma-4-26b-a4b-it:free", tools = false), free("qwen/qwen3-235b:free"),
      ),
      stt = emptyList(),
    )
    settings.setChatModel(ProviderId.OPENROUTER, "qwen/qwen3-235b:free")
    settings.setDeepModel(ProviderId.OPENROUTER, inkling)
    settings.setClassifierModel(ProviderId.OPENROUTER, inklingSmall)
    verifier.reconcile(ProviderId.OPENROUTER, catalogue)
    val after = settings.current()
    // La chat era una scelta valida: non si tocca. Profondo e router erano su Inkling: i preferiti.
    assertEquals("qwen/qwen3-235b:free", after.chatModel(ProviderId.OPENROUTER))
    assertEquals("minimax/minimax-m3:free", after.deepModel(ProviderId.OPENROUTER))
    assertEquals("google/gemma-4-26b-a4b-it:free", after.classifierModel(ProviderId.OPENROUTER))
    // Un secondo riallineamento non cambia niente: le scelte adesso valgono.
    verifier.reconcile(ProviderId.OPENROUTER, catalogue)
    assertEquals(after, settings.current())
  }

  /**
   * Il buco vero della 1.29.0: [AiKeyVerifier.reconcile] era giusto, ma nessuno lo chiamava.
   *
   * L'app invoca solo [AiKeyVerifier.refreshIfStale] (all'apertura delle impostazioni), e quello
   * tornava il catalogo dalla cache senza riallineare finche' era fresco di meno di un giorno.
   * Un telefono che aveva verificato la chiave ieri teneva Inkling come profondo per sempre —
   * la correzione arrivava con l'aggiornamento e non cambiava niente. Qui si passa dalla porta
   * da cui entra l'app, non da [AiKeyVerifier.reconcile] direttamente.
   */
  @Test
  fun `il riallineamento avviene anche quando il catalogo e' ancora fresco e non si scarica niente`() = runBlocking {
    val (verifier, settings, catalogs) = fixture()
    val catalogue = ModelCatalogue(
      chat = listOf(free(inkling), free(inklingSmall), free("minimax/minimax-m3:free"), free("google/gemma-4-26b-a4b-it:free")),
      stt = emptyList(),
    )
    catalogs.save(ProviderId.OPENROUTER, catalogue)
    // Fresco di un minuto: nessuna rete, e nessuna chiave con cui farla comunque.
    settings.markModelsRefreshed(ProviderId.OPENROUTER, System.currentTimeMillis() - 60_000L)
    settings.setChatModel(ProviderId.OPENROUTER, inklingSmall)
    settings.setDeepModel(ProviderId.OPENROUTER, inkling)

    assertEquals(catalogue, verifier.refreshIfStale(ProviderId.OPENROUTER))

    val after = settings.current()
    assertFalse(AiDefaults.avoided(after.chatModel(ProviderId.OPENROUTER)))
    assertFalse(AiDefaults.avoided(after.deepModel(ProviderId.OPENROUTER)))
    assertEquals("minimax/minimax-m3:free", after.chatModel(ProviderId.OPENROUTER))
  }

  /** Offline, o senza chiave: si riallinea lo stesso, sul catalogo che si ha su disco. */
  @Test
  fun `senza client il riallineamento usa comunque il catalogo salvato`() = runBlocking {
    val (verifier, settings, catalogs) = fixture()
    catalogs.save(ProviderId.OPENROUTER, ModelCatalogue(chat = listOf(free(inkling), free("minimax/minimax-m3:free")), stt = emptyList()))
    // Vecchio di due giorni: si proverebbe la rete, ma senza chiave non c'e' nessun client.
    settings.markModelsRefreshed(ProviderId.OPENROUTER, System.currentTimeMillis() - 2 * AiKeyVerifier.DAY_MILLIS)
    settings.setDeepModel(ProviderId.OPENROUTER, inkling)

    verifier.refreshIfStale(ProviderId.OPENROUTER)

    assertEquals("minimax/minimax-m3:free", settings.current().deepModel(ProviderId.OPENROUTER))
  }

  @Test
  fun `una scelta da evitare se ne va anche quando non c'e' niente di meglio, una solo sparita resta`() = runBlocking {
    val (verifier, settings) = verifier()
    settings.setChatModel(ProviderId.OPENROUTER, "qwen/qwen3-235b:free")
    settings.setDeepModel(ProviderId.OPENROUTER, inkling)
    verifier.reconcile(ProviderId.OPENROUTER, ModelCatalogue(chat = listOf(free(inkling)), stt = emptyList()))
    val after = settings.current()
    // Nessun profondo: il livello torna "nessuno", cioe' la chat.
    assertNull(after.deepModel(ProviderId.OPENROUTER))
    // La chat e' solo sparita dal catalogo e non c'e' niente di meglio: resta.
    assertEquals("qwen/qwen3-235b:free", after.chatModel(ProviderId.OPENROUTER))
  }
}
