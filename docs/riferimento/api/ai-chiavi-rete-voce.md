<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/ai-chiavi-rete-voce.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-ai: chiavi, rete, voce

_Generato sull'engine 3.0.6 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ai/ai/keys/AiDataStores.kt`

- `object AiDataStores` — I file DataStore dell'assistente (3.0.0). Servono a una cosa: **un file corrotto si butta**.
  - `fun corruptionHandler(): ReplaceFileCorruptionHandler<Preferences>` — Un file illeggibile riparte da vuoto: le preferenze tornano ai default, le chiavi vanno reinserite.
  - `fun open(file: File, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): DataStore<Preferences>` — Apre [file] (che deve finire in `.preferences_pb`) come [DataStore] di preferenze, col gestore della corruzione. [scope] e' quello in cui gira l'I/O: di serie uno proprio, su `Dispatchers.IO`.

## `engine-ai/ai/keys/AiKeyStore.kt`

- `data class KeyState(val present: Boolean, val verifiedAtMillis: Long?)` — Cosa si sa di una chiave senza leggerla: c'e', e quando e' stata verificata l'ultima volta.
  - `val verified: Boolean get()`
- `class AiKeyStore(private val store: DataStore<Preferences>, private val cipher: SecretCipher)` — Le chiavi dei provider IA, cifrate col Keystore e in un file DataStore tutto loro (come `provider_keys`: l'export dei dati puo' escluderlo e basta). La chiave in chiaro non esce mai da un `Flow`: si legge con [key] nel momento in cui serve, e si dimentica.
  - `companion object`
    - `fun open(file: File, cipher: SecretCipher = KeystoreCipher(), scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): AiKeyStore` — Le chiavi in un file scelto dall'app (3.0.0), che deve finire in `.preferences_pb`: stesso file di prima per chi l'aveva gia' ([AiDataStores.open]), con la corruzione gestita. Una volta sola per file e per processo.
  - `val states: Flow<Map<ProviderId, KeyState>>`
  - `val anyVerified: Flow<Boolean>` — Vero se almeno un provider ha una chiave verificata: la condizione per accendere l'assistente.
  - `suspend fun currentStates(): Map<ProviderId, KeyState>`
  - `suspend fun key(provider: ProviderId): String?` — La chiave in chiaro, adesso. Null se manca o se il Keystore non la legge piu' (e allora la toglie).
  - `suspend fun set(provider: ProviderId, key: String?)` — Salva (cifrata) o rimuove; una chiave nuova parte non verificata.
  - `suspend fun markVerified(provider: ProviderId, atMillis: Long)`

## `engine-ai/ai/keys/AiKeyVerifier.kt`

- `sealed interface VerifyResult` — L'esito di una verifica: quanti modelli, e per OpenRouter anche i crediti e il default scelto.
  - `data class Ok(val catalogue: ModelCatalogue, val keyInfo: OpenRouterKeyInfo?, val chosenDefault: String?, val chosenIsFree: Boolean) : VerifyResult`
  - `data object Invalid : VerifyResult`
  - `data class Failed(val error: AiError?) : VerifyResult`
- `class ModelCatalogStore(private val directory: File)` — I cataloghi dei modelli su disco (`cacheDir/ai/models-<provider>.json`) e in memoria: la pagina delle impostazioni li mostra subito, e li rinfresca alla verifica, alla richiesta, e al massimo una volta al giorno. Codifica a mano con JsonElement, come tutto il resto.
  - `val catalogues: StateFlow<Map<ProviderId, ModelCatalogue>>`
  - `suspend fun load(provider: ProviderId): ModelCatalogue?`
  - `suspend fun save(provider: ProviderId, catalogue: ModelCatalogue)`
  - `companion object`
    - `fun encode(catalogue: ModelCatalogue): JsonElement`
    - `fun decode(json: JsonElement): ModelCatalogue?`
- `class AiKeyVerifier(private val keys: AiKeyStore, private val settings: AiSettingsStore, private val providers: ProviderFactory, private val catalogs: ModelCatalogStore, private val clock: () -> Long = System::currentTimeMillis)` — La verifica di una chiave e' una chiamata vera (`GET /models`), come in KeyVoice: se risponde, la chiave si segna verificata e il catalogo si salva. Per OpenRouter si leggono anche i crediti e, la prima volta, si sceglie il modello gratuito con tool secondo l'euristica.
  - `val keyInfo: StateFlow<Map<ProviderId, OpenRouterKeyInfo>>` — L'ultimo `GET /key` di OpenRouter, per la scheda delle impostazioni.
  - `val unavailable: StateFlow<Map<ProviderId, Set<String>>>` — I modelli che un provider ha detto non disponibili in questo processo (2.8.0, da [markUnavailable]): [reconcile] li tratta come assenti dal catalogo.
  - `suspend fun markUnavailable(provider: ProviderId, model: String)` — Un provider ha risposto che [model] non c'e' (2.8.0): e' il gancio per `AskInput.onModelUnavailable`. Il modello si segna come assente e le scelte si riallineano subito sul catalogo salvato, cosi' la domanda dopo non ci ribatte contro.
  - `suspend fun verify(provider: ProviderId): VerifyResult`
  - `suspend fun reconcile(provider: ProviderId, catalogue: ModelCatalogue)` — Riallinea i tre livelli di [provider] al [catalogue] (1.29.0, prima era dentro [verify]). Una scelta vale finche' esiste nel catalogo e non e' da evitare; se non vale piu' si riassegna il default di quel livello: per la chat di OpenRouter i gratuiti preferiti, poi l'euristica, poi il flash a pagamento; per Gemini l'ultimo flash e flash-lite; per il profondo [TierDefaults.pickDeep].
  - `suspend fun refreshIfStale(provider: ProviderId, force: Boolean = false): ModelCatalogue?` — Rinfresca un catalogo se e' vecchio piu' di un giorno (o subito, se [force]), e riallinea i modelli al catalogo nuovo: un modello sparito o sconsigliato si sostituisce qui, non solo alla verifica della chiave.
  - `companion object`
    - `const val DAY_MILLIS`

## `engine-ai/ai/keys/AiSettingsStore.kt`

- `enum class ThinkingLevel` — Quanto il modello deve pensare prima di rispondere: si mappa sui parametri di ogni provider.
- `object AiDefaults` — I modelli con cui l'assistente parte, prima che l'utente li cambi. Groq e Gemini sono scelte di prodotto (2026-09-02); su OpenRouter il modello di chat lo sceglie un'euristica sul catalogo al momento della verifica della chiave, e qui c'e' solo il ripiego. Il livello profondo non ha un default scritto: lo sceglie [dev.antigravity.fluidengine.ai.provider.TierDefaults] dal catalogo vivo alla verifica della chiave.
  - `const val GROQ_CHAT`
  - `const val GROQ_STT`
  - `const val GROQ_CLASSIFIER` — Lo stadio 1 su Groq gira su un modello piccolo: i limiti di Groq sono per modello.
  - `const val GEMINI_CHAT`
  - `const val GEMINI_STT`
  - `const val GEMINI_CLASSIFIER`
  - `const val OPENROUTER_STT`
  - `const val OPENROUTER_CHAT_FALLBACK` — Se nessun modello gratuito con tool esiste nel catalogo: un flash a pagamento, con avviso.
  - `val OPENROUTER_CHAT_PREFERRED` — I gratuiti di OpenRouter che si preferiscono, in ordine, quando ci sono nel catalogo (2026-09-07, rivisti nella 1.29.0). Il tetto giornaliero del gratuito e' lo stesso per qualunque modello, quindi conviene il piu' capace: per la chat uno svelto ma grosso, per il profondo il piu' capace che veda immagini e legga documenti, per il router uno leggero ma non stupido.
  - `val OPENROUTER_DEEP_PREFERRED`
  - `val OPENROUTER_CLASSIFIER_PREFERRED`
  - `val OPENROUTER_AVOID: Set<String>` — I modelli di OpenRouter che non si propongono mai, e che una scelta salvata non tiene in vita (1.29.0). Inkling (Thinking Machines) risponde solo dentro un harness agentico: a una domanda di chat si rifiuta, e un telefono che lo aveva come profondo — era il primo dei preferiti fino alla 1.28.0 — rispondeva "non posso" a ogni domanda complessa.
  - `fun avoided(model: String?): Boolean` — Vero se [model] e' fra quelli da non usare mai ([OPENROUTER_AVOID]); null non lo e'.
  - `fun latestGemini(ids: List<String>, family: String): String?` — L'ultimo modello di una famiglia Gemini nel catalogo: `gemini-flash-latest` se Google espone l'alias, altrimenti quello con la versione piu' alta (`gemini-3.6-flash` > `gemini-2.5-flash`), a parita' di versione quello senza "preview". Null se nel catalogo non c'e' nessuno.
  - `fun chatModel(provider: ProviderId): String?`
  - `fun sttModel(provider: ProviderId): String`
  - `fun classifierModel(provider: ProviderId): String?`
- `data class AiSettings(val enabled: Boolean = false, val chatOrder: List<ProviderId> = ProviderId.defaultOrder, val sttOrder: List<ProviderId> = ProviderId.defaultOrder, val chatModels: Map<ProviderId, String> = emptyMap(), val sttModels: Map<ProviderId, String> = emptyMap(), val classifierModels: Map<ProviderId, String> = emptyMap(), val deepModels: Map<ProviderId, String> = emptyMap(), val openRouterFallbacks: List<String> = emptyList(), val openRouterDataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT, val thinking: ThinkingLevel = ThinkingLevel.MEDIUM, val speakReplies: Boolean = false, val actionsEnabled: Boolean = false, val consentAcceptedAtMillis: Long? = null, val modelsRefreshedAt: Map<ProviderId, Long> = emptyMap(), val enabledChosen: Boolean = false)` — Le impostazioni dell'assistente, gia' risolte coi default: chi le legge non ragiona sui null.
  - `fun chatModel(provider: ProviderId): String?`
  - `fun sttModel(provider: ProviderId): String`
  - `fun classifierModel(provider: ProviderId): String?`
  - `fun deepModel(provider: ProviderId): String?`
  - `fun model(provider: ProviderId, tier: ModelTier): String?` — Il modello scelto per un livello, o null se e' la chat a fare anche quello.
  - `val consentAccepted: Boolean get()`
- `data class AiSettingsDefaults(val enabled: Boolean = false, val actionsEnabled: Boolean = false, val speakReplies: Boolean = false, val thinking: ThinkingLevel = ThinkingLevel.MEDIUM, val openRouterDataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT, val chatOrder: List<ProviderId> = ProviderId.defaultOrder, val sttOrder: List<ProviderId> = ProviderId.defaultOrder)` — I valori che le impostazioni hanno finche' l'utente non sceglie (3.0.0). Ogni app aveva i suoi ("azioni nell'app accese: mostra subito, agisci con conferma"; "a OpenRouter sempre `deny`") e, dato che l'engine risolveva "mai scelto" in un altro modo, li imponeva riscrivendo il file con una migrazione.
- `class AiSettingsStore(private val store: DataStore<Preferences>, private val defaults: AiSettingsDefaults = AiSettingsDefaults())` — Le preferenze dell'assistente, tutte in un file DataStore `ai`. Le chiavi stanno altrove ([AiKeyStore]): qui non c'e' nulla di segreto, e l'export dei dati puo' includerlo.
  - `val settings: Flow<AiSettings>`
  - `suspend fun current(): AiSettings`
  - `suspend fun setEnabled(enabled: Boolean)`
  - `suspend fun enableIfNeverChosen(): Boolean` — Accende l'assistente solo se l'utente non ha mai scelto (3.0.0); torna vero se l'ha acceso. Dopo una chiave verificata: chi incollava la chiave vedeva "ok" e tornava all'app senza sapere che mancava l'interruttore. Chi l'ha spento una volta lo trova spento, e [setEnabled] a `false` e' una scelta come un'altra.
  - `suspend fun setChatOrder(order: List<ProviderId>)`
  - `suspend fun setSttOrder(order: List<ProviderId>)`
  - `suspend fun setChatModel(provider: ProviderId, model: String?)`
  - `suspend fun setSttModel(provider: ProviderId, model: String?)`
  - `suspend fun setClassifierModel(provider: ProviderId, model: String?)`
  - `suspend fun setDeepModel(provider: ProviderId, model: String?)`
  - `suspend fun setModel(provider: ProviderId, tier: ModelTier, model: String?)`
  - `suspend fun setOpenRouterFallbacks(models: List<String>)`
  - `suspend fun setOpenRouterDataPolicy(policy: OpenRouterDataPolicy)`
  - `suspend fun setThinking(level: ThinkingLevel)`
  - `suspend fun setSpeakReplies(speak: Boolean)`
  - `suspend fun setActionsEnabled(enabled: Boolean)`
  - `suspend fun setConsentAccepted(atMillis: Long?)` — Il consenso e' un istante, non un interruttore: si registra quando viene dato, si toglie con null.
  - `suspend fun markModelsRefreshed(provider: ProviderId, atMillis: Long)`
  - `companion object`
    - `fun parseOrder(raw: String?): List<ProviderId>` — Un ordine salvato che non nomina un provider lo accoda: una versione nuova non lo perde.
    - `fun open(file: File, defaults: AiSettingsDefaults = AiSettingsDefaults(), scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): AiSettingsStore` — Le preferenze in un file scelto dall'app (3.0.0), che deve finire in `.preferences_pb`: per chi non usa il file `ai` di serie, o vuole il proprio [DataStore] ([AiDataStores.open]) per metterci anche altro. Un file corrotto riparte da vuoto. Una volta sola per file e per processo.

## `engine-ai/ai/keys/SecretCipher.kt`

- `interface SecretCipher` — Cifra e decifra un segreto. Esiste come interfaccia perche' il Keystore non c'e' sul computer: i test lo sostituiscono con una versione trasparente, e il resto del codice non se ne accorge.
  - `fun encrypt(plain: String): String`
  - `fun decrypt(blob: String): String?` — Null quando il blob non si puo' leggere: chiave del Keystore cambiata, dato corrotto.
- `class KeystoreCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher` — AES-256-GCM con la chiave dentro l'Android Keystore: e' quello che `EncryptedSharedPreferences` faceva prima di essere deprecata (security-crypto 1.1.0-alpha07, aprile 2025: "usate direttamente il Keystore"). Ottanta righe, nessuna dipendenza, stesso livello di protezione.
  - `companion object`
    - `const val DEFAULT_ALIAS` — L'alias della chiave nel Keystore. Un'app che aveva gia' il proprio (FluidWeather usava `fluidweather.ai`) lo passa al costruttore, altrimenti le chiavi salvate non si leggono piu'.

## `engine-ai/ai/net/AiError.kt`

- `data class RateLimitInfo(val limitRequests: Int? = null, val remainingRequests: Int? = null, val limitTokens: Int? = null, val remainingTokens: Int? = null, val resetRequestsSec: Double? = null, val resetTokensSec: Double? = null)` — Cosa gli header di un provider dicono sui limiti: tutto opzionale, Gemini non ne manda nessuno.
  - `companion object`
    - `val EMPTY`
- `enum class BadReason` — Perche' un provider ha rifiutato una richiesta (2.8.0). Un 400 non e' una cosa sola: "il modello ha scritto male una tool call" si risolve riprovando, "il modello non c'e' piu'" cambiando modello, "non ci sta" accorciando la storia. Prima erano tutti "il servizio ha risposto con un errore" e la domanda finiva li'.
- `sealed class AiError(message: String, cause: Throwable? = null, val httpCode: Int? = null, val providerMessage: String? = null) : Exception(message, cause)` — Gli errori di un provider IA, ridotti a quelli su cui l'orchestratore decide qualcosa: aspettare, passare a un altro provider, riprovare, o dirlo all'utente. Il messaggio grezzo resta per la diagnostica; la frase per l'utente la sceglie la UI in base al tipo.
  - `class Unauthorized(message: String, httpCode: Int? = null, providerMessage: String? = null) : AiError(message, null, httpCode, providerMessage)` — 401/403, o il 400 di Gemini "API key not valid": una chiave sbagliata non si maschera con un ripiego.
  - `class RateLimited(val retryAfterSec: Double?, val rateLimit: RateLimitInfo, val freeModelCap: Boolean = false, message: String, httpCode: Int? = 429, providerMessage: String? = AiErrorMapper.shorten(message)) : AiError(message, null, httpCode, providerMessage)`
  - `class Server(val code: Int, message: String, providerMessage: String? = AiErrorMapper.shorten(message)) : AiError(message, null, code, providerMessage)`
  - `class BadRequest(val code: Int, message: String, val reason: BadReason = if (message.startsWith(BLOCKED_PREFIX)) BadReas…, providerMessage: String? = AiErrorMapper.shorten(message)) : AiError(message, null, code, providerMessage)` — Un rifiuto della richiesta. [reason] dice quale (2.8.0); il default tiene in vita la convenzione di prima, un messaggio che comincia con "bloccato" vale [BadReason.BLOCKED].
  - `class Network(message: String, cause: Throwable? = null) : AiError(message, cause)`
  - `class Timeout(message: String, cause: Throwable? = null) : AiError(message, cause)`
  - `class Parse(message: String, cause: Throwable? = null) : AiError(message, cause)` — Un 2xx che non si capisce: JSON rotto, struttura inattesa.
  - `companion object`
    - `const val BLOCKED_PREFIX` — L'inizio del messaggio di un contenuto bloccato: la convenzione di prima della 2.8.0.

## `engine-ai/ai/net/AiErrorMapper.kt`

- `object AiErrorMapper` — Da codice HTTP + header + corpo a [AiError]. Puro: si prova sul computer con i corpi veri dei tre provider (fixture nei test).
  - `fun map(code: Int, rawHeaders: Map<String?, List<String>>, body: String, nowMillis: Long = System.currentTimeMillis()): AiError`
  - `fun inBandError(error: JsonElement?, fallbackMessage: String): AiError` — Un errore arrivato **dentro** una risposta 200 (2.8.0): l'oggetto `error` di un pezzo di stream (Groq manda cosi' il suo `tool_use_failed`, a stream gia' aperto) o di una risposta intera. Quelli che si sanno leggere diventano un [AiError.BadRequest] con il loro [BadReason]; gli altri restano un [AiError.Server] come prima, cioe' una riprova.
  - `fun badReason(code: Int, json: JsonElement?, message: String): BadReason` — Il perche' di un 4xx, dal codice e dal corpo (2.8.0). I tre provider lo dicono in modi diversi e nessuno lo promette, quindi si guarda tutto: `error.code` (Groq: una parola; Gemini e OpenRouter: un numero), `error.type`, `error.status` (Gemini), il messaggio, e `error.metadata.raw` di OpenRouter, dove finisce l'errore del fornitore a valle.
  - `fun providerDetails(json: JsonElement?, message: String): String?` — La frase per un foglio "Dettagli": il codice o lo stato del provider se c'e' (`tool_use_failed`, `NOT_FOUND`), il messaggio, e l'errore a valle di OpenRouter se dice qualcosa di piu'. Corta e senza niente che somigli a una chiave.
  - `fun shorten(text: String?): String?` — Un testo del provider pronto da mostrare: una riga sola, al massimo [PROVIDER_MESSAGE_CHARS] caratteri, e senza chiavi. Un messaggio d'errore non dovrebbe mai contenerne, ma qualche servizio ripete meta' della chiave sbagliata, e questo testo finisce in uno screenshot.
  - `fun wrap(t: Throwable): Throwable` — Un'eccezione di rete/IO nella sua categoria; le cancellazioni passano oltre intatte.
  - `fun errorMessage(json: JsonElement?): String?` — `error.message` e' dove Groq, Gemini e OpenRouter mettono tutti la frase; il resto e' rumore.
  - `fun normalize(rawHeaders: Map<String?, List<String>>): Map<String, String>`
  - `fun parseRetryAfter(headers: Map<String, String>, json: JsonElement?, message: String, nowMillis: Long = System.currentTimeMillis()): Double?` — In ordine di fiducia: header `retry-after` (secondi o data HTTP), `RetryInfo.retryDelay` nei dettagli di Gemini ("37s"), la frase di Groq "try again in 7.66s".
  - `fun parseDuration(raw: String): Double?` — "1h2m3.5s", "7.66s", "250ms", "2m59.56s" -> secondi. Null se non e' una durata.
  - `fun parseRateLimit(headers: Map<String, String>, nowMillis: Long = System.currentTimeMillis()): RateLimitInfo`
  - `const val PROVIDER_MESSAGE_CHARS` — Quanto e' lungo, al massimo, [AiError.providerMessage].

## `engine-ai/ai/net/AiHttp.kt`

- `class AiResponse(val code: Int, val body: JsonElement?, val headers: Map<String, String>, val rateLimit: RateLimitInfo)` — Una risposta 2xx gia' letta: il JSON, gli header (minuscoli) e cio' che dicono sui limiti.
- `class AiHttp(private val userAgent: String, private val connectTimeoutMillis: Int = 10_000, private val readTimeoutMillis: Int = 60_000, private val streamChunkTimeoutMillis: Int = 45_000, private val io: CoroutineDispatcher = Dispatchers.IO)` — Il client HTTP dei provider IA: POST con corpo JSON, POST in streaming (SSE), POST multipart per l'audio, GET per i cataloghi. Su `HttpURLConnection` come `EngineHttp`, perche' l'app non ha e non vuole una libreria HTTP: quello che serve in piu' rispetto all'engine (un corpo, un flusso di eventi) sta tutto qui, in un file.
  - `suspend fun postJson(url: String, headers: Map<String, String>, body: JsonObject, readTimeoutMillis: Int? = null, onResponse: (Int, Map<String, String>) -> Unit = { _, _ -> }): AiResponse` — [readTimeoutMillis]: l'attesa massima della risposta per questa richiesta; null = il default del client.
  - `suspend fun getJson(url: String, headers: Map<String, String>): AiResponse`
  - `suspend fun postMultipart(url: String, headers: Map<String, String>, fields: Map<String, String>, file: File, fileMime: String, fileName: String, fileFieldName: String = "file"): AiResponse`
  - `fun postJsonStream(url: String, headers: Map<String, String>, body: JsonObject, chunkTimeoutMillis: Int? = null, onHeaders: (Map<String, String>) -> Unit = {}): Flow<String>` — Overload storico: conserva anche la lambda finale per gli header 2xx.
  - `fun postJsonStream(url: String, headers: Map<String, String>, body: JsonObject, chunkTimeoutMillis: Int? = null, onHeaders: (Map<String, String>) -> Unit = {}, onResponse: (Int, Map<String, String>) -> Unit): Flow<String>`

## `engine-ai/ai/net/Json.kt`

- `operator fun JsonElement?.get(key: String): JsonElement?` — Navigazione JSON senza modelli: ogni campo mancante e' una decisione esplicita, non una eccezione.
- `fun JsonElement?.at(index: Int): JsonElement?`
- `fun JsonElement?.asArray(): List<JsonElement>`
- `fun JsonElement?.double(): Double?`
- `fun JsonElement?.string(): String?`

## `engine-ai/ai/net/MultipartBody.kt`

- `object MultipartBody` — Il corpo `multipart/form-data` a mano, come gia' fa Fluid transit col microfono: i campi di testo e l'intestazione del file in un preludio, la chiusura in un epilogo, il file nel mezzo copiato a flusso. Cosi' `Content-Length` e' esatto senza tenere l'audio in memoria due volte.
  - `class Parts(val prelude: ByteArray, val epilogue: ByteArray, val contentType: String)`
    - `fun contentLength(fileBytes: Long): Long`
  - `fun build(fields: Map<String, String>, fileFieldName: String, fileName: String, fileMime: String, boundary: String = "----FluidEngine${System.nanoTime()}"): Parts`

## `engine-ai/ai/net/SseParser.kt`

- `class SseParser` — Un parser di Server-Sent Events riga per riga, come dice la specifica: le righe `data:` di un evento si accumulano, una riga vuota lo chiude, i commenti (`: OPENROUTER PROCESSING`, i keep-alive di Groq) e i campi `event:`/`id:`/`retry:` non ci interessano. Una istanza per stream; [flush] recupera un ultimo evento non terminato a fine connessione.
  - `fun feed(rawLine: String): String?` — Ritorna il payload di un evento appena completo, altrimenti null.
  - `fun flush(): String?`

## `engine-ai/ai/speech/AndroidPcmSource.kt`

- `class AndroidPcmSource(private val context: Context? = null) : PcmSource` — `AudioRecord` con la sorgente per il riconoscimento vocale (l'OEM la tara per il parlato), il soppressore di rumore e il controllo di guadagno dove ci sono, e il thread di lettura alla priorita' audio. Il permesso RECORD_AUDIO lo controlla chi chiama: qui si presume concesso.

## `engine-ai/ai/speech/SpeechCapture.kt`

- `interface PcmSource` — L'unico punto Android della cattura: un microfono che da' campioni PCM 16 bit.
  - `fun start(sampleRate: Int): Boolean` — Vero se il microfono e' partito.
  - `fun read(frame: ShortArray): Int` — Bloccante: quanti campioni ha scritto in [frame]; < 0 = errore, 0 = niente per ora.
  - `fun stop()`
  - `val failure: MicrophoneFailure get()` — Perche' non si e' potuto registrare, quando [start] o [read] falliscono.
- `enum class MicrophoneFailure` — Cosa dire all'utente quando il microfono non collabora: sono casi diversi con rimedi diversi.
- `class MicrophoneException(val failure: MicrophoneFailure, message: String) : IllegalStateException(message)` — Il microfono non ha registrato, e questo e' il motivo.
- `class SpeechCapture(private val source: PcmSource, private val config: VadConfig = VadConfig(), private val clock: () -> Long = System::currentTimeMillis)` — La cattura vocale con rilevamento del silenzio, su `AudioRecord` (non `MediaRecorder`, che da' solo un picco senza campioni e non si puo' rifilare). PCM 16 kHz mono in frame da 20 ms: dagli stessi frame escono il livello per l'aureola e la decisione di fermarsi. Il WAV si scrive una volta alla fine, gia' tagliato all'ultima parola piu' un margine.
  - `data class VadConfig(val sampleRate: Int = 16_000, val frameMillis: Int = 20, val calibrationMillis: Int = 300, val floorMultiplier: Double = 2.5, val speechFloorRms: Double = 180.0, val minFloor: Double = 60.0, val maxFloor: Double = 2_000.0, val maxCalibratedFloor: Double = 300.0, val releaseRatio: Double = 0.6, val startFrames: Int = 3, val endSilenceMillis: Long = 1_200, val maxDurationMillis: Long = 30_000, val minSpeechMillis: Long = 300, val minTotalMillis: Long = 600, val tailKeepMillis: Long = 300)`
    - `val frameSamples: Int get()`
  - `enum class EndReason`
  - `enum class EmptyReason`
  - `sealed interface Event`
    - `data class Level(val level: Float, val speaking: Boolean, val elapsedMillis: Long) : Event`
    - `data object SpeechStarted : Event`
    - `data class Finished(val file: File, val durationMillis: Long, val reason: EndReason) : Event`
    - `data class Empty(val reason: EmptyReason) : Event`
    - `data class Failed(val cause: Throwable) : Event`
  - `fun stopNow()` — Tocco: chiude la cattura al prossimo frame.
  - `fun record(target: File): Flow<Event>` — Freddo: parte all'iscrizione, e la cancellazione ferma il microfono e cancella il file.
- `object WavWriter` — WAV PCM 16 bit mono, intestazione di 44 byte little-endian (mai `DataOutputStream`, che e' big-endian).
  - `fun header(dataSize: Int, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray`
  - `fun write(target: File, pcm: ShortArray, samples: Int, sampleRate: Int)`

## `engine-ai/ai/speech/Transcriber.kt`

- `data class Transcription(val text: String, val provider: ProviderId, val model: String)` — Chi ha trascritto e cosa.
- `class Transcriber(private val providers: suspend () -> List<ReadyProvider>)` — La trascrizione segue l'ordine dei provider scelto per la voce: il primo con chiave prova, i successivi sono riserva su 429, 5xx e rete. La lingua e' quella dell'app (it/en), forzata: piu' precisa dell'auto-rilevamento sulle frasi corte.
  - `suspend fun transcribe(audio: File, language: String, hint: String?): Transcription`
  - `companion object`
    - `const val HINT_MAX_CHARS` — Il prompt di Whisper vale al massimo 224 token: 800 caratteri ci stanno.
    - `const val MAX_HINT_WORDS` — Quanti nomi propri si danno a Whisper, di serie (3.0.0: e' un parametro di [hint]; FluidWeather ne dava 12).
    - `fun hint(domainHint: String, vocabulary: List<String>, maxWords: Int = MAX_HINT_WORDS): String` — Il suggerimento di vocabolario per Whisper: una frase sul dominio dell'app ("Domande sul registro elettronico: voti, compiti, ...") e i nomi propri che l'utente potrebbe dire.
