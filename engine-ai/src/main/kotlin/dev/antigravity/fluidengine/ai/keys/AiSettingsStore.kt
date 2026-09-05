package dev.antigravity.fluidengine.ai.keys

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Quanto il modello deve pensare prima di rispondere: si mappa sui parametri di ogni provider. */
enum class ThinkingLevel { LOW, MEDIUM, HIGH }

/**
 * I modelli con cui l'assistente parte, prima che l'utente li cambi. Groq e Gemini sono scelte
 * di prodotto (2026-09-02); su OpenRouter il modello di chat lo sceglie un'euristica sul
 * catalogo al momento della verifica della chiave, e qui c'e' solo il ripiego. Il livello
 * profondo non ha un default scritto: lo sceglie [dev.antigravity.fluidengine.ai.provider.TierDefaults]
 * dal catalogo vivo alla verifica della chiave.
 */
object AiDefaults {
  const val GROQ_CHAT = "qwen/qwen3.8-27b"
  const val GROQ_STT = "whisper-large-v3"

  /** Lo stadio 1 su Groq gira su un modello piccolo: i limiti di Groq sono per modello. */
  const val GROQ_CLASSIFIER = "llama-3.1-8b-instant"
  const val GEMINI_CHAT = "gemini-3.6-flash"
  const val GEMINI_STT = "gemini-3.5-transcribe"
  const val GEMINI_CLASSIFIER = "gemini-3.5-flash-lite"
  const val OPENROUTER_STT = "openai/whisper-large-v3"

  /** Se nessun modello gratuito con tool esiste nel catalogo: un flash a pagamento, con avviso. */
  const val OPENROUTER_CHAT_FALLBACK = "google/gemini-3.6-flash"

  fun chatModel(provider: ProviderId): String? = when (provider) {
    ProviderId.GROQ -> GROQ_CHAT
    ProviderId.GEMINI -> GEMINI_CHAT
    ProviderId.OPENROUTER -> null
  }

  fun sttModel(provider: ProviderId): String = when (provider) {
    ProviderId.GROQ -> GROQ_STT
    ProviderId.GEMINI -> GEMINI_STT
    ProviderId.OPENROUTER -> OPENROUTER_STT
  }

  fun classifierModel(provider: ProviderId): String? = when (provider) {
    ProviderId.GROQ -> GROQ_CLASSIFIER
    ProviderId.GEMINI -> GEMINI_CLASSIFIER
    ProviderId.OPENROUTER -> null
  }
}

/** Le impostazioni dell'assistente, gia' risolte coi default: chi le legge non ragiona sui null. */
data class AiSettings(
  val enabled: Boolean = false,
  val chatOrder: List<ProviderId> = ProviderId.defaultOrder,
  val sttOrder: List<ProviderId> = ProviderId.defaultOrder,
  val chatModels: Map<ProviderId, String> = emptyMap(),
  val sttModels: Map<ProviderId, String> = emptyMap(),
  val classifierModels: Map<ProviderId, String> = emptyMap(),
  val deepModels: Map<ProviderId, String> = emptyMap(),
  /** Fino a due modelli di riserva che OpenRouter prova da solo se il primario fallisce. */
  val openRouterFallbacks: List<String> = emptyList(),
  val openRouterAllowDataCollection: Boolean = false,
  val thinking: ThinkingLevel = ThinkingLevel.MEDIUM,
  val speakReplies: Boolean = false,
  val actionsEnabled: Boolean = false,
  /** Quando l'utente ha letto e accettato cosa parte verso i provider; null se mai. */
  val consentAcceptedAtMillis: Long? = null,
  val modelsRefreshedAt: Map<ProviderId, Long> = emptyMap(),
) {
  fun chatModel(provider: ProviderId): String? = chatModels[provider] ?: AiDefaults.chatModel(provider)
  fun sttModel(provider: ProviderId): String = sttModels[provider] ?: AiDefaults.sttModel(provider)
  fun classifierModel(provider: ProviderId): String? =
    classifierModels[provider] ?: AiDefaults.classifierModel(provider)
  fun deepModel(provider: ProviderId): String? = deepModels[provider]

  /** Il modello scelto per un livello, o null se e' la chat a fare anche quello. */
  fun model(provider: ProviderId, tier: ModelTier): String? = when (tier) {
    ModelTier.ROUTER -> classifierModel(provider)
    ModelTier.CHAT -> chatModel(provider)
    ModelTier.DEEP -> deepModel(provider)
  }

  val consentAccepted: Boolean get() = consentAcceptedAtMillis != null
}

private val Context.aiSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "ai")

/**
 * Le preferenze dell'assistente, tutte in un file DataStore `ai`. Le chiavi stanno altrove
 * ([AiKeyStore]): qui non c'e' nulla di segreto, e l'export dei dati puo' includerlo.
 */
class AiSettingsStore(private val store: DataStore<Preferences>) {

  constructor(context: Context) : this(context.aiSettingsStore)

  val settings: Flow<AiSettings> = store.data.map { it.toSettings() }

  suspend fun current(): AiSettings = settings.first()

  suspend fun setEnabled(enabled: Boolean) = edit { it[Enabled] = enabled }

  suspend fun setChatOrder(order: List<ProviderId>) =
    edit { it[ChatOrder] = order.joinToString(",") { p -> p.id } }

  suspend fun setSttOrder(order: List<ProviderId>) =
    edit { it[SttOrder] = order.joinToString(",") { p -> p.id } }

  suspend fun setChatModel(provider: ProviderId, model: String?) = setModel("chat_model_", provider, model)

  suspend fun setSttModel(provider: ProviderId, model: String?) = setModel("stt_model_", provider, model)

  suspend fun setClassifierModel(provider: ProviderId, model: String?) = setModel("classifier_model_", provider, model)

  suspend fun setDeepModel(provider: ProviderId, model: String?) = setModel("deep_model_", provider, model)

  suspend fun setModel(provider: ProviderId, tier: ModelTier, model: String?) = when (tier) {
    ModelTier.ROUTER -> setClassifierModel(provider, model)
    ModelTier.CHAT -> setChatModel(provider, model)
    ModelTier.DEEP -> setDeepModel(provider, model)
  }

  private suspend fun setModel(prefix: String, provider: ProviderId, model: String?) = edit { prefs ->
    val key = stringPreferencesKey("$prefix${provider.id}")
    if (model.isNullOrBlank()) prefs -= key else prefs[key] = model
  }

  suspend fun setOpenRouterFallbacks(models: List<String>) =
    edit { it[OpenRouterFallbacks] = models.take(2).joinToString("\n") }

  suspend fun setOpenRouterAllowDataCollection(allow: Boolean) = edit { it[OpenRouterDataCollection] = allow }
  suspend fun setThinking(level: ThinkingLevel) = edit { it[Thinking] = level.name }
  suspend fun setSpeakReplies(speak: Boolean) = edit { it[SpeakReplies] = speak }
  suspend fun setActionsEnabled(enabled: Boolean) = edit { it[ActionsEnabled] = enabled }

  /** Il consenso e' un istante, non un interruttore: si registra quando viene dato, si toglie con null. */
  suspend fun setConsentAccepted(atMillis: Long?) = edit { prefs ->
    if (atMillis == null) prefs -= ConsentAt else prefs[ConsentAt] = atMillis
  }

  suspend fun markModelsRefreshed(provider: ProviderId, atMillis: Long) =
    edit { it[longPreferencesKey("models_refreshed_${provider.id}")] = atMillis }

  private suspend fun edit(transform: (MutablePreferences) -> Unit) {
    store.edit { transform(it) }
  }

  private fun Preferences.toSettings(): AiSettings = AiSettings(
    enabled = this[Enabled] ?: false,
    chatOrder = parseOrder(this[ChatOrder]),
    sttOrder = parseOrder(this[SttOrder]),
    chatModels = modelsFor("chat_model_"),
    sttModels = modelsFor("stt_model_"),
    classifierModels = modelsFor("classifier_model_"),
    deepModels = modelsFor("deep_model_"),
    openRouterFallbacks = this[OpenRouterFallbacks]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
    openRouterAllowDataCollection = this[OpenRouterDataCollection] ?: false,
    thinking = this[Thinking]?.let { name -> ThinkingLevel.entries.firstOrNull { it.name == name } }
      ?: ThinkingLevel.MEDIUM,
    speakReplies = this[SpeakReplies] ?: false,
    actionsEnabled = this[ActionsEnabled] ?: false,
    consentAcceptedAtMillis = this[ConsentAt],
    modelsRefreshedAt = ProviderId.entries.mapNotNull { p ->
      this[longPreferencesKey("models_refreshed_${p.id}")]?.let { p to it }
    }.toMap(),
  )

  private fun Preferences.modelsFor(prefix: String): Map<ProviderId, String> =
    ProviderId.entries.mapNotNull { p ->
      this[stringPreferencesKey("$prefix${p.id}")]?.takeIf { it.isNotBlank() }?.let { p to it }
    }.toMap()

  companion object {
    private val Enabled = booleanPreferencesKey("enabled")
    private val ChatOrder = stringPreferencesKey("chat_order")
    private val SttOrder = stringPreferencesKey("stt_order")
    private val OpenRouterFallbacks = stringPreferencesKey("openrouter_fallbacks")
    private val OpenRouterDataCollection = booleanPreferencesKey("openrouter_data_collection")
    private val Thinking = stringPreferencesKey("thinking")
    private val SpeakReplies = booleanPreferencesKey("speak_replies")
    private val ActionsEnabled = booleanPreferencesKey("actions_enabled")
    private val ConsentAt = longPreferencesKey("consent_at")

    /** Un ordine salvato che non nomina un provider lo accoda: una versione nuova non lo perde. */
    fun parseOrder(raw: String?): List<ProviderId> {
      val listed = raw?.split(",")?.mapNotNull { ProviderId.fromId(it.trim()) }?.distinct() ?: emptyList()
      return listed + ProviderId.defaultOrder.filterNot { it in listed }
    }
  }
}
