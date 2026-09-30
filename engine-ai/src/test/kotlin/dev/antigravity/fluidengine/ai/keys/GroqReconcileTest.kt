package dev.antigravity.fluidengine.ai.keys

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelKind
import dev.antigravity.fluidengine.ai.provider.ProviderFactory
import dev.antigravity.fluidengine.ai.provider.ProviderId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il Keystore non c'e' sul computer: la cifratura e' un involucro trasparente. */
private class ReconcileCipher : SecretCipher {
  override fun encrypt(plain: String): String = "plain:$plain"
  override fun decrypt(blob: String): String? = blob.removePrefix("plain:").takeIf { blob.startsWith("plain:") }
}

/**
 * Il riallineamento di Groq (2.8.0), che prima non c'era: un modello di Groq ritirato restava la
 * chat o il router finche' l'utente non lo cambiava a mano, e ogni domanda finiva in un 400. E i
 * modelli che una domanda ha trovato spariti ([AiKeyVerifier.markUnavailable]).
 */
class GroqReconcileTest {

  private fun model(id: String, ctx: Int = 131_072, reasoning: Boolean = false) =
    ModelInfo(id, id, ModelKind.CHAT, contextWindow = ctx, supportsTools = true, supportsReasoning = reasoning)

  private data class Fixture(val verifier: AiKeyVerifier, val settings: AiSettingsStore, val catalogs: ModelCatalogStore)

  private fun fixture(): Fixture {
    val dir = Files.createTempDirectory("ai-groq").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = AiSettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "ai.preferences_pb") }))
    val keys = AiKeyStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "keys.preferences_pb") }), ReconcileCipher())
    val catalogs = ModelCatalogStore(File(dir, "models"))
    val factory = ProviderFactory(AiHttp("test"), keys, settings, "https://test.invalid", "test", catalogs)
    return Fixture(AiKeyVerifier(keys, settings, factory, catalogs), settings, catalogs)
  }

  private val versatile = "llama-3.3-70b-versatile"
  private val gptOss = "openai/gpt-oss-120b"
  private val instant = AiDefaults.GROQ_CLASSIFIER

  @Test
  fun `con i default nel catalogo non si salva niente, restano quelli del codice`() = runBlocking {
    val (verifier, settings) = fixture()
    val catalogue = ModelCatalogue(chat = listOf(model(AiDefaults.GROQ_CHAT, reasoning = true), model(instant), model(versatile)), stt = emptyList())
    verifier.reconcile(ProviderId.GROQ, catalogue)
    val after = settings.current()
    assertNull(after.chatModels[ProviderId.GROQ])
    assertNull(after.classifierModels[ProviderId.GROQ])
    assertEquals(AiDefaults.GROQ_CHAT, after.chatModel(ProviderId.GROQ))
  }

  @Test
  fun `il default della chat ritirato si sostituisce col migliore del catalogo, il router resta`() = runBlocking {
    val (verifier, settings) = fixture()
    val catalogue = ModelCatalogue(chat = listOf(model(instant, ctx = 131_072), model(versatile), model(gptOss, reasoning = true)), stt = emptyList())
    verifier.reconcile(ProviderId.GROQ, catalogue)
    val after = settings.current()
    // Non piccolo, ragiona, contesto grande: il gpt-oss, non l'instant ne' il versatile.
    assertEquals(gptOss, after.chatModel(ProviderId.GROQ))
    assertEquals(instant, after.classifierModel(ProviderId.GROQ))
    assertNull(after.classifierModels[ProviderId.GROQ])
  }

  @Test
  fun `una scelta sparita torna al default togliendo la scelta, un router sparito prende un piccolo`() = runBlocking {
    val (verifier, settings) = fixture()
    settings.setChatModel(ProviderId.GROQ, "llama3-70b-8192")
    settings.setClassifierModel(ProviderId.GROQ, "gemma2-9b-it")
    val catalogue = ModelCatalogue(chat = listOf(model(AiDefaults.GROQ_CHAT), model(versatile), model("llama-4-scout-mini")), stt = emptyList())
    verifier.reconcile(ProviderId.GROQ, catalogue)
    val after = settings.current()
    assertNull(after.chatModels[ProviderId.GROQ])
    assertEquals(AiDefaults.GROQ_CHAT, after.chatModel(ProviderId.GROQ))
    // Il default del router non c'e': il primo dei piccoli.
    assertEquals("llama-4-scout-mini", after.classifierModel(ProviderId.GROQ))
    // Una scelta valida non si tocca al giro dopo.
    verifier.reconcile(ProviderId.GROQ, catalogue)
    assertEquals(after, settings.current())
  }

  @Test
  fun `un modello segnato non disponibile vale come assente anche se il catalogo lo elenca ancora`() = runBlocking {
    val (verifier, settings, catalogs) = fixture()
    val catalogue = ModelCatalogue(chat = listOf(model(AiDefaults.GROQ_CHAT, reasoning = true), model(instant), model(gptOss, reasoning = true)), stt = emptyList())
    catalogs.save(ProviderId.GROQ, catalogue)
    verifier.reconcile(ProviderId.GROQ, catalogue)
    assertNull(settings.current().chatModels[ProviderId.GROQ])

    // Una domanda ha trovato il default sparito: AskInput.onModelUnavailable -> markUnavailable.
    verifier.markUnavailable(ProviderId.GROQ, AiDefaults.GROQ_CHAT)

    assertTrue(AiDefaults.GROQ_CHAT in verifier.unavailable.value[ProviderId.GROQ].orEmpty())
    assertEquals(gptOss, settings.current().chatModel(ProviderId.GROQ))
    // Il rinfresco dal catalogo in cache non lo rimette: la memoria vale finche' vive il processo.
    verifier.reconcile(ProviderId.GROQ, catalogue)
    assertEquals(gptOss, settings.current().chatModel(ProviderId.GROQ))
  }

  /**
   * Il catalogo che Groq dava davvero a una chiave il 2026-09-30: il router di default
   * (`llama-3.1-8b-instant`) non c'e' piu', e nessun modello ha un nome da "piccolo". Prima nessuna
   * scelta veniva salvata, il default sparito restava in uso, e ogni domanda cominciava con un 404
   * del router.
   */
  @Test
  fun `il router di default sparito senza piccoli nel catalogo prende gpt-oss-20b`() = runBlocking {
    val (verifier, settings) = fixture()
    val catalogue = ModelCatalogue(
      chat = listOf(model(AiDefaults.GROQ_CHAT, reasoning = true), model(gptOss, reasoning = true), model("openai/gpt-oss-20b", reasoning = true), model("allam-2-7b", ctx = 4_096)),
      stt = emptyList(),
    )
    verifier.reconcile(ProviderId.GROQ, catalogue)
    assertEquals("openai/gpt-oss-20b", settings.current().classifierModel(ProviderId.GROQ))
    // Stabile: al giro dopo non cambia.
    verifier.reconcile(ProviderId.GROQ, catalogue)
    assertEquals("openai/gpt-oss-20b", settings.current().classifierModel(ProviderId.GROQ))
  }

  @Test
  fun `senza nessun candidato il router lo fa la chat, non il default sparito`() = runBlocking {
    val (verifier, settings) = fixture()
    val catalogue = ModelCatalogue(chat = listOf(model(AiDefaults.GROQ_CHAT, reasoning = true)), stt = emptyList())
    verifier.reconcile(ProviderId.GROQ, catalogue)
    assertEquals(AiDefaults.GROQ_CHAT, settings.current().classifierModel(ProviderId.GROQ))
  }

  @Test
  fun `un profondo segnato non disponibile se ne va anche senza un sostituto`() = runBlocking {
    val (verifier, settings, catalogs) = fixture()
    val gemini = ModelCatalogue(chat = listOf(model("gemini-3.6-flash"), model("gemini-3.5-pro")), stt = emptyList())
    catalogs.save(ProviderId.GEMINI, gemini)
    settings.setDeepModel(ProviderId.GEMINI, "gemini-3.5-pro")
    verifier.markUnavailable(ProviderId.GEMINI, "gemini-3.5-pro")
    val deep = settings.current().deepModel(ProviderId.GEMINI)
    assertTrue("il profondo e' ancora $deep", deep != "gemini-3.5-pro")
  }
}
