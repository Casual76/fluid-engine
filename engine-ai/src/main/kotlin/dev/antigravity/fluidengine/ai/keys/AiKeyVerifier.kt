package dev.antigravity.fluidengine.ai.keys

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelKind
import dev.antigravity.fluidengine.ai.provider.OpenRouterCatalog
import dev.antigravity.fluidengine.ai.provider.OpenRouterKeyInfo
import dev.antigravity.fluidengine.ai.provider.OpenRouterProvider
import dev.antigravity.fluidengine.ai.provider.ProviderFactory
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.TierDefaults
import dev.antigravity.fluidengine.ai.net.asArray
import dev.antigravity.fluidengine.ai.net.at
import dev.antigravity.fluidengine.ai.net.double
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** L'esito di una verifica: quanti modelli, e per OpenRouter anche i crediti e il default scelto. */
sealed interface VerifyResult {
  data class Ok(val catalogue: ModelCatalogue, val keyInfo: OpenRouterKeyInfo?, val chosenDefault: String?, val chosenIsFree: Boolean) : VerifyResult
  data object Invalid : VerifyResult
  data class Failed(val error: AiError?) : VerifyResult
}

/**
 * I cataloghi dei modelli su disco (`cacheDir/ai/models-<provider>.json`) e in memoria: la
 * pagina delle impostazioni li mostra subito, e li rinfresca alla verifica, alla richiesta,
 * e al massimo una volta al giorno. Codifica a mano con JsonElement, come tutto il resto.
 */
class ModelCatalogStore(private val directory: File) {

  private val memory = MutableStateFlow<Map<ProviderId, ModelCatalogue>>(emptyMap())
  val catalogues: StateFlow<Map<ProviderId, ModelCatalogue>> = memory

  suspend fun load(provider: ProviderId): ModelCatalogue? {
    memory.value[provider]?.let { return it }
    return withContext(Dispatchers.IO) {
      val file = file(provider)
      if (!file.exists()) return@withContext null
      runCatching { decode(Json.parseToJsonElement(file.readText())) }.getOrNull()?.also { put(provider, it) }
    }
  }

  suspend fun save(provider: ProviderId, catalogue: ModelCatalogue) {
    put(provider, catalogue)
    withContext(Dispatchers.IO) {
      directory.mkdirs()
      runCatching { file(provider).writeText(encode(catalogue).toString()) }
    }
  }

  private fun put(provider: ProviderId, catalogue: ModelCatalogue) {
    memory.value = memory.value + (provider to catalogue)
  }

  private fun file(provider: ProviderId) = File(directory, "models-${provider.id}.json")

  companion object {
    fun encode(catalogue: ModelCatalogue): JsonElement = buildJsonObject {
      put("v", 1)
      put("chat", buildJsonArray { catalogue.chat.forEach { add(encode(it)) } })
      put("stt", buildJsonArray { catalogue.stt.forEach { add(encode(it)) } })
    }

    private fun encode(model: ModelInfo): JsonElement = buildJsonObject {
      put("id", model.id)
      put("name", model.displayName)
      put("kind", model.kind.name)
      model.contextWindow?.let { put("ctx", it) }
      model.maxOutputTokens?.let { put("out", it) }
      put("tools", model.supportsTools)
      put("reasoning", model.supportsReasoning)
      put("audio", model.audioInput)
      put("vision", model.supportsVision)
      put("docs", model.supportsDocuments)
      put("free", model.free)
      model.pricePromptPerM?.let { put("pin", it) }
      model.priceCompletionPerM?.let { put("pout", it) }
    }

    fun decode(json: JsonElement): ModelCatalogue? {
      if (json["v"].double()?.toInt() != 1) return null
      fun model(e: JsonElement): ModelInfo? {
        val id = e["id"].string() ?: return null
        return ModelInfo(
          id = id,
          displayName = e["name"].string() ?: id,
          kind = ModelKind.entries.firstOrNull { it.name == e["kind"].string() } ?: ModelKind.CHAT,
          contextWindow = e["ctx"].double()?.toInt(),
          maxOutputTokens = e["out"].double()?.toInt(),
          supportsTools = (e["tools"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true,
          supportsReasoning = (e["reasoning"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
          audioInput = (e["audio"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
          supportsVision = (e["vision"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
          supportsDocuments = (e["docs"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
          free = (e["free"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
          pricePromptPerM = e["pin"].double(),
          priceCompletionPerM = e["pout"].double(),
        )
      }
      return ModelCatalogue(
        chat = json["chat"].asArray().mapNotNull { model(it) },
        stt = json["stt"].asArray().mapNotNull { model(it) },
      )
    }
  }
}

/**
 * La verifica di una chiave e' una chiamata vera (`GET /models`), come in KeyVoice: se risponde,
 * la chiave si segna verificata e il catalogo si salva. Per OpenRouter si leggono anche i
 * crediti e, la prima volta, si sceglie il modello gratuito con tool secondo l'euristica.
 *
 * I modelli dei tre livelli si riallineano al catalogo ([reconcile]) sia alla verifica sia al
 * rinfresco quotidiano: una scelta dell'utente vale finche' esiste e non e' fra quelli da
 * evitare ([AiDefaults.OPENROUTER_AVOID]); altrimenti si riassegna il default.
 */
class AiKeyVerifier(
  private val keys: AiKeyStore,
  private val settings: AiSettingsStore,
  private val providers: ProviderFactory,
  private val catalogs: ModelCatalogStore,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  private val info = MutableStateFlow<Map<ProviderId, OpenRouterKeyInfo>>(emptyMap())

  /** L'ultimo `GET /key` di OpenRouter, per la scheda delle impostazioni. */
  val keyInfo: StateFlow<Map<ProviderId, OpenRouterKeyInfo>> = info

  suspend fun verify(provider: ProviderId): VerifyResult {
    val client = providers.forVerification(provider) ?: return VerifyResult.Invalid
    return try {
      val catalogue = client.listModels()
      if (catalogue.chat.isEmpty() && catalogue.stt.isEmpty()) return VerifyResult.Failed(null)
      catalogs.save(provider, catalogue)
      settings.markModelsRefreshed(provider, clock())
      keys.markVerified(provider, clock())
      var keyInfo: OpenRouterKeyInfo? = null
      if (provider == ProviderId.OPENROUTER) {
        keyInfo = runCatching { (client as? OpenRouterProvider)?.keyInfo() }.getOrNull()
        keyInfo?.let { info.value = info.value + (provider to it) }
      }
      reconcile(provider, catalogue)
      var chosen: String? = null
      var chosenIsFree = false
      if (provider == ProviderId.OPENROUTER) {
        chosen = settings.current().chatModels[ProviderId.OPENROUTER]
        chosenIsFree = catalogue.chat.firstOrNull { it.id == chosen }?.free == true
      }
      VerifyResult.Ok(catalogue, keyInfo, chosen, chosenIsFree)
    } catch (e: CancellationException) {
      throw e
    } catch (e: AiError.Unauthorized) {
      VerifyResult.Invalid
    } catch (e: AiError) {
      VerifyResult.Failed(e)
    } catch (e: Throwable) {
      VerifyResult.Failed(null)
    }
  }

  /**
   * Riallinea i tre livelli di [provider] al [catalogue] (1.29.0, prima era dentro [verify]). Una
   * scelta vale finche' esiste nel catalogo e non e' da evitare; se non vale piu' si riassegna il
   * default di quel livello: per la chat di OpenRouter i gratuiti preferiti, poi l'euristica, poi
   * il flash a pagamento; per Gemini l'ultimo flash e flash-lite; per il profondo
   * [TierDefaults.pickDeep]. Lo chiama anche [refreshIfStale]: cosi' un telefono con un modello
   * ritirato o sconsigliato si ripara al primo rinfresco, senza reinserire la chiave.
   */
  suspend fun reconcile(provider: ProviderId, catalogue: ModelCatalogue) {
    if (provider == ProviderId.OPENROUTER) {
      ensureChat(ProviderId.OPENROUTER, catalogue) { _ ->
        val preferred = AiDefaults.OPENROUTER_CHAT_PREFERRED.firstNotNullOfOrNull { id -> catalogue.chat.firstOrNull { it.id == id && it.free && it.supportsTools } }
        (preferred ?: OpenRouterCatalog.pickDefaultFree(catalogue))?.id
          ?: catalogue.chat.firstOrNull { it.id == AiDefaults.OPENROUTER_CHAT_FALLBACK }?.id
          ?: catalogue.chat.firstOrNull { !AiDefaults.avoided(it.id) }?.id
      }
      ensureClassifier(ProviderId.OPENROUTER, catalogue) { ids -> AiDefaults.OPENROUTER_CLASSIFIER_PREFERRED.firstOrNull { it in ids } }
    }
    if (provider == ProviderId.GEMINI) {
      ensureChat(ProviderId.GEMINI, catalogue) { ids -> AiDefaults.latestGemini(ids, "flash") }
      ensureClassifier(ProviderId.GEMINI, catalogue) { ids -> AiDefaults.latestGemini(ids, "flash-lite") }
    }
    ensureDeepModel(provider, catalogue)
  }

  /**
   * Una scelta salvata vale se e' nel catalogo e non e' da evitare. Una scelta da evitare si
   * sostituisce sempre, anche con "nessuna" (null: il livello ricade sul suo ripiego); una che e'
   * solo sparita dal catalogo resta se non c'e' niente di meglio da mettere al suo posto.
   */
  private fun valid(model: String?, catalogue: ModelCatalogue): Boolean =
    model != null && !AiDefaults.avoided(model) && catalogue.chat.any { it.id == model }

  private fun mustReplace(model: String?, picked: String?): Boolean = picked != null || AiDefaults.avoided(model)

  /**
   * La chat, se l'utente non l'ha scelta (o la sua scelta non vale piu'), segue [pick]: su Gemini
   * l'ultimo flash. Una scelta ancora valida non si tocca.
   */
  private suspend fun ensureChat(provider: ProviderId, catalogue: ModelCatalogue, pick: (List<String>) -> String?) {
    val current = settings.current().chatModels[provider]
    if (valid(current, catalogue)) return
    val picked = pick(catalogue.chat.map { it.id })
    if (mustReplace(current, picked)) settings.setChatModel(provider, picked)
  }

  /** Il router come la chat: la scelta dell'utente vale finche' esiste, poi decide [pick]. */
  private suspend fun ensureClassifier(provider: ProviderId, catalogue: ModelCatalogue, pick: (List<String>) -> String?) {
    val current = settings.current().classifierModels[provider]
    if (valid(current, catalogue)) return
    val picked = pick(catalogue.chat.map { it.id })
    if (mustReplace(current, picked)) settings.setModel(provider, ModelTier.ROUTER, picked)
  }

  /**
   * Il livello profondo non ha un default scritto nel codice: la prima volta, o se il modello
   * scelto non vale piu', lo sceglie l'euristica. Una scelta dell'utente ancora valida non si
   * tocca; una da evitare se ne va anche se l'euristica non trova niente (la chat fa da profondo).
   */
  private suspend fun ensureDeepModel(provider: ProviderId, catalogue: ModelCatalogue) {
    val current = settings.current()
    val chosen = current.deepModels[provider]
    if (valid(chosen, catalogue)) return
    val picked = TierDefaults.pickDeep(provider, catalogue, current.chatModel(provider))?.id
    if (mustReplace(chosen, picked)) settings.setDeepModel(provider, picked)
  }

  /**
   * Rinfresca un catalogo se e' vecchio piu' di un giorno (o subito, se [force]), e riallinea i
   * modelli al catalogo nuovo: un modello sparito o sconsigliato si sostituisce qui, non solo alla
   * verifica della chiave.
   */
  suspend fun refreshIfStale(provider: ProviderId, force: Boolean = false): ModelCatalogue? {
    val current = settings.current()
    val refreshedAt = current.modelsRefreshedAt[provider] ?: 0L
    val cached = catalogs.load(provider)
    if (!force && cached != null && clock() - refreshedAt < DAY_MILLIS) return cached
    val client = providers.forVerification(provider) ?: return cached
    return try {
      val catalogue = client.listModels()
      catalogs.save(provider, catalogue)
      settings.markModelsRefreshed(provider, clock())
      if (provider == ProviderId.OPENROUTER) {
        runCatching { (client as? OpenRouterProvider)?.keyInfo() }.getOrNull()?.let { info.value = info.value + (provider to it) }
      }
      reconcile(provider, catalogue)
      catalogue
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      cached
    }
  }

  companion object {
    const val DAY_MILLIS = 24 * 3_600_000L
  }
}
