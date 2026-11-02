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
import dev.antigravity.fluidengine.ai.provider.OpenRouterDataPolicy
import dev.antigravity.fluidengine.ai.provider.ProviderId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

  /**
   * I gratuiti di OpenRouter che si preferiscono, in ordine, quando ci sono nel catalogo
   * (2026-09-07, rivisti nella 1.29.0). Il tetto giornaliero del gratuito e' lo stesso per
   * qualunque modello, quindi conviene il piu' capace: per la chat uno svelto ma grosso, per il
   * profondo il piu' capace che veda immagini e legga documenti, per il router uno leggero ma non
   * stupido. Quando nessuno c'e' piu', decide l'euristica di
   * [dev.antigravity.fluidengine.ai.provider.OpenRouterCatalog].
   */
  val OPENROUTER_CHAT_PREFERRED = listOf("minimax/minimax-m3:free", "minimax/minimax-m2.7:free", "nvidia/nemotron-3.5-lightning:free")
  val OPENROUTER_DEEP_PREFERRED = listOf("minimax/minimax-m3:free", "nvidia/nemotron-3-ultra-550b-a55b:free")
  val OPENROUTER_CLASSIFIER_PREFERRED = listOf("google/gemma-4-26b-a4b-it:free", "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free")

  /**
   * I modelli di OpenRouter che non si propongono mai, e che una scelta salvata non tiene in vita
   * (1.29.0). Inkling (Thinking Machines) risponde solo dentro un harness agentico: a una domanda
   * di chat si rifiuta, e un telefono che lo aveva come profondo — era il primo dei preferiti
   * fino alla 1.28.0 — rispondeva "non posso" a ogni domanda complessa. Cambiare la lista dei
   * preferiti non bastava: [AiKeyVerifier] conserva una scelta finche' esiste nel catalogo, e
   * Inkling nel catalogo c'e'. Quindi chi sceglie fra i modelli
   * ([dev.antigravity.fluidengine.ai.provider.TierDefaults],
   * [dev.antigravity.fluidengine.ai.provider.OpenRouterCatalog], [AiKeyVerifier]) li tratta come
   * assenti: mai candidati, e una scelta salvata su di loro vale come nessuna scelta.
   */
  val OPENROUTER_AVOID: Set<String> = setOf("thinkingmachines/inkling:free", "thinkingmachines/inkling-small:free")

  /** Vero se [model] e' fra quelli da non usare mai ([OPENROUTER_AVOID]); null non lo e'. */
  fun avoided(model: String?): Boolean = model != null && model in OPENROUTER_AVOID

  /**
   * L'ultimo modello di una famiglia Gemini nel catalogo: `gemini-flash-latest` se Google espone
   * l'alias, altrimenti quello con la versione piu' alta (`gemini-3.6-flash` > `gemini-2.5-flash`),
   * a parita' di versione quello senza "preview". Null se nel catalogo non c'e' nessuno.
   *
   * [family] e' "flash", "pro" o "flash-lite"; le altre varianti ("flash-lite" quando si cerca
   * "flash", "flash-image", "tts") non contano.
   */
  fun latestGemini(ids: List<String>, family: String): String? {
    val alias = "gemini-$family-latest"
    if (ids.any { it == alias }) return alias
    val pattern = Regex("^gemini-(\\d+(?:\\.\\d+)?)-$family(-preview)?(?:-\\d+)?$")
    return ids.mapNotNull { id -> pattern.matchEntire(id)?.let { m -> Triple(m.groupValues[1].toDoubleOrNull() ?: 0.0, m.groupValues[2].isEmpty(), id) } }
      .maxWithOrNull(compareBy<Triple<Double, Boolean, String>> { it.first }.thenBy { it.second })
      ?.third
  }

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
  /** Cosa dire a OpenRouter sull'addestramento: niente (vale l'account) o `deny`. */
  val openRouterDataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT,
  val thinking: ThinkingLevel = ThinkingLevel.MEDIUM,
  val speakReplies: Boolean = false,
  val actionsEnabled: Boolean = false,
  /** Quando l'utente ha letto e accettato cosa parte verso i provider; null se mai. */
  val consentAcceptedAtMillis: Long? = null,
  val modelsRefreshedAt: Map<ProviderId, Long> = emptyMap(),
  /**
   * Vero se l'utente ha mai toccato l'interruttore (3.0.0): [enabled] e' gia' risolto, e "spento
   * perche' l'ha spento" non si distingue da "spento perche' non ha mai scelto". Chi accende
   * l'assistente dopo una prima chiave verificata (`AiSettingsStore.enableIfNeverChosen`) guarda
   * questo, non [enabled].
   */
  val enabledChosen: Boolean = false,
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

/**
 * I valori che le impostazioni hanno finche' l'utente non sceglie (3.0.0). Ogni app aveva i suoi
 * ("azioni nell'app accese: mostra subito, agisci con conferma"; "a OpenRouter sempre `deny`") e,
 * dato che l'engine risolveva "mai scelto" in un altro modo, li imponeva riscrivendo il file con una
 * migrazione. Qui sono un parametro: un valore **assente** dal file vale quello di
 * [AiSettingsDefaults], uno **scritto** vale quello che l'utente ha scelto, e il file non si tocca.
 *
 * I default di serie sono quelli di prima (tutto spento, ordine Groq, Gemini, OpenRouter, pensiero
 * medio, la politica sui dati dell'account).
 */
data class AiSettingsDefaults(
  val enabled: Boolean = false,
  val actionsEnabled: Boolean = false,
  val speakReplies: Boolean = false,
  val thinking: ThinkingLevel = ThinkingLevel.MEDIUM,
  val openRouterDataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT,
  val chatOrder: List<ProviderId> = ProviderId.defaultOrder,
  val sttOrder: List<ProviderId> = ProviderId.defaultOrder,
)

private val Context.aiSettingsStore: DataStore<Preferences> by preferencesDataStore(
  name = "ai",
  corruptionHandler = AiDataStores.corruptionHandler(),
)

/**
 * Le preferenze dell'assistente, tutte in un file DataStore `ai`. Le chiavi stanno altrove
 * ([AiKeyStore]): qui non c'e' nulla di segreto, e l'export dei dati puo' includerlo.
 *
 * Dalla 3.0.0 l'app non ha piu' bisogno di aprirsi il file da se' per tre cose che l'engine non
 * faceva:
 *
 *  - **i valori di partenza** sono un parametro ([AiSettingsDefaults]);
 *  - **"mai scelto" non e' "spento"**: [enableIfNeverChosen] accende solo se l'utente non ha mai
 *    toccato l'interruttore, e [AiSettings.enabledChosen] lo dice;
 *  - **un file corrotto si butta**: il costruttore a `Context` e [open] ripartono da un file vuoto
 *    ([AiDataStores]), invece di fallire ogni lettura e ogni scrittura per sempre.
 *
 * Un file si apre una volta sola per processo: chi passa il suo [DataStore] non deve poi usare anche
 * il costruttore a `Context` (DataStore si rifiuta di avere due istanze sullo stesso file).
 */
class AiSettingsStore(
  private val store: DataStore<Preferences>,
  private val defaults: AiSettingsDefaults = AiSettingsDefaults(),
) {

  constructor(context: Context, defaults: AiSettingsDefaults = AiSettingsDefaults()) : this(context.aiSettingsStore, defaults)

  val settings: Flow<AiSettings> = store.data.map { it.toSettings() }

  suspend fun current(): AiSettings = settings.first()

  suspend fun setEnabled(enabled: Boolean) = edit { it[Enabled] = enabled }

  /**
   * Accende l'assistente solo se l'utente non ha mai scelto (3.0.0); torna vero se l'ha acceso.
   * Dopo una chiave verificata: chi incollava la chiave vedeva "ok" e tornava all'app senza sapere
   * che mancava l'interruttore. Chi l'ha spento una volta lo trova spento, e [setEnabled] a `false`
   * e' una scelta come un'altra.
   */
  suspend fun enableIfNeverChosen(): Boolean {
    var changed = false
    store.edit { prefs ->
      if (Enabled !in prefs) {
        prefs[Enabled] = true
        changed = true
      }
    }
    return changed
  }

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

  suspend fun setOpenRouterDataPolicy(policy: OpenRouterDataPolicy) = edit { it[OpenRouterDataPolicyKey] = policy.name }
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
    enabled = this[Enabled] ?: defaults.enabled,
    chatOrder = this[ChatOrder]?.let(::parseOrder) ?: completeOrder(defaults.chatOrder),
    sttOrder = this[SttOrder]?.let(::parseOrder) ?: completeOrder(defaults.sttOrder),
    chatModels = modelsFor("chat_model_"),
    sttModels = modelsFor("stt_model_"),
    classifierModels = modelsFor("classifier_model_"),
    deepModels = modelsFor("deep_model_"),
    openRouterFallbacks = this[OpenRouterFallbacks]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
    openRouterDataPolicy = this[OpenRouterDataPolicyKey]?.let { name -> OpenRouterDataPolicy.entries.firstOrNull { it.name == name } }
      ?: defaults.openRouterDataPolicy,
    thinking = this[Thinking]?.let { name -> ThinkingLevel.entries.firstOrNull { it.name == name } }
      ?: defaults.thinking,
    speakReplies = this[SpeakReplies] ?: defaults.speakReplies,
    actionsEnabled = this[ActionsEnabled] ?: defaults.actionsEnabled,
    consentAcceptedAtMillis = this[ConsentAt],
    modelsRefreshedAt = ProviderId.entries.mapNotNull { p ->
      this[longPreferencesKey("models_refreshed_${p.id}")]?.let { p to it }
    }.toMap(),
    enabledChosen = Enabled in this,
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
    private val OpenRouterDataPolicyKey = stringPreferencesKey("openrouter_data_policy")
    private val Thinking = stringPreferencesKey("thinking")
    private val SpeakReplies = booleanPreferencesKey("speak_replies")
    private val ActionsEnabled = booleanPreferencesKey("actions_enabled")
    private val ConsentAt = longPreferencesKey("consent_at")

    /** Un ordine salvato che non nomina un provider lo accoda: una versione nuova non lo perde. */
    fun parseOrder(raw: String?): List<ProviderId> {
      val listed = raw?.split(",")?.mapNotNull { ProviderId.fromId(it.trim()) }?.distinct() ?: emptyList()
      return listed + ProviderId.defaultOrder.filterNot { it in listed }
    }

    /** Lo stesso per un ordine di default dato dall'app: senza doppioni, con in coda i provider che non nomina. */
    private fun completeOrder(order: List<ProviderId>): List<ProviderId> {
      val listed = order.distinct()
      return listed + ProviderId.defaultOrder.filterNot { it in listed }
    }

    /**
     * Le preferenze in un file scelto dall'app (3.0.0), che deve finire in `.preferences_pb`: per chi
     * non usa il file `ai` di serie, o vuole il proprio [DataStore] ([AiDataStores.open]) per
     * metterci anche altro. Un file corrotto riparte da vuoto. Una volta sola per file e per processo.
     */
    fun open(
      file: File,
      defaults: AiSettingsDefaults = AiSettingsDefaults(),
      scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): AiSettingsStore = AiSettingsStore(AiDataStores.open(file, scope), defaults)
  }
}
