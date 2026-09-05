package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.keys.AiDefaults
import dev.antigravity.fluidengine.ai.keys.AiKeyStore
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.AiSettingsStore
import dev.antigravity.fluidengine.ai.keys.ModelCatalogStore
import dev.antigravity.fluidengine.ai.net.AiHttp

/**
 * Un provider pronto a rispondere, coi modelli con cui lo si vuole usare adesso, uno per livello:
 * [chatModel] risponde, [classifierModel] fa da router (null = la chat fa anche quello),
 * [deepModel] legge documenti e testi lunghi (null = la chat fa anche quello). Il [catalogue], se
 * c'e', dice cosa ogni modello sa prendere in ingresso.
 */
data class ReadyProvider(
  val provider: ChatProvider,
  val chatModel: String,
  val sttModel: String,
  val classifierModel: String?,
  val deepModel: String? = null,
  val catalogue: ModelCatalogue? = null,
) {
  fun model(tier: ModelTier): String = when (tier) {
    ModelTier.ROUTER -> classifierModel ?: chatModel
    ModelTier.CHAT -> chatModel
    ModelTier.DEEP -> deepModel ?: chatModel
  }

  fun capabilities(model: String): ModelCapabilities = ProviderCapabilities.of(provider.id, model, catalogue)
}

/**
 * Costruisce i provider per ogni domanda leggendo le chiavi dal loro deposito: una chiave cambiata
 * nelle impostazioni vale alla domanda dopo, senza riavvii. L'ordine e' quello dell'utente,
 * filtrato sui provider che hanno una chiave verificata.
 */
class ProviderFactory(
  private val http: AiHttp,
  private val keys: AiKeyStore,
  private val settings: AiSettingsStore,
  private val referer: String,
  private val appTitle: String,
  /** I cataloghi salvati, per sapere cosa vede e cosa legge ogni modello; opzionale. */
  private val catalogs: ModelCatalogStore? = null,
) {

  enum class Kind { CHAT, STT }

  suspend fun build(provider: ProviderId, settings: AiSettings): ReadyProvider? {
    val key = keys.key(provider) ?: return null
    val chat = settings.chatModel(provider) ?: if (provider == ProviderId.OPENROUTER) AiDefaults.OPENROUTER_CHAT_FALLBACK else return null
    val client: ChatProvider = when (provider) {
      ProviderId.GROQ -> GroqProvider(http, key)
      ProviderId.GEMINI -> GeminiProvider(http, key)
      ProviderId.OPENROUTER -> OpenRouterProvider(
        http = http,
        apiKey = key,
        referer = referer,
        title = appTitle,
        fallbackModels = settings.openRouterFallbacks,
        allowDataCollection = settings.openRouterAllowDataCollection,
      )
    }
    return ReadyProvider(
      provider = client,
      chatModel = chat,
      sttModel = settings.sttModel(provider),
      classifierModel = settings.classifierModel(provider),
      deepModel = settings.deepModel(provider),
      catalogue = catalogs?.load(provider),
    )
  }

  /** I provider con chiave verificata, nell'ordine scelto per [kind]. */
  suspend fun ordered(kind: Kind): List<ReadyProvider> {
    val current = settings.current()
    val states = keys.currentStates()
    val order = if (kind == Kind.CHAT) current.chatOrder else current.sttOrder
    return order.filter { states[it]?.verified == true }.mapNotNull { build(it, current) }
  }

  /** Un provider con chiave anche se non ancora verificata: per verificarla. */
  suspend fun forVerification(provider: ProviderId): ChatProvider? = build(provider, settings.current())?.provider
}
