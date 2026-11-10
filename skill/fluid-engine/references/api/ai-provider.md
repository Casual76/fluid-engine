# API · engine-ai: provider e modelli

_Generato sull'engine 3.1.1 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ai/ai/provider/Capabilities.kt`

- `object ProviderCapabilities` — Cosa un modello sa prendere in ingresso, quando il catalogo non lo dice. Gemini e' multimodale per intero; OpenRouter legge i documenti di chiunque col suo parser e vede le immagini dove il catalogo lo dichiara (qui si indovina dal nome); Groq vede solo coi modelli che lo dicono nel nome e non legge documenti mai.
  - `fun guess(provider: ProviderId, model: String): ModelCapabilities`
  - `fun of(provider: ProviderId, model: String, catalogue: ModelCatalogue?): ModelCapabilities` — Il modello del catalogo se c'e', l'euristica altrimenti.
- `object TierDefaults` — Quale modello del catalogo fa da livello profondo, se l'utente non ne ha scelto uno: quello che legge documenti e vede immagini, col contesto piu' grande, e che ragiona; sui gratuiti prima dove i gratuiti esistono. Null se il catalogo e' vuoto: allora il livello profondo e' la chat. I modelli di [AiDefaults.OPENROUTER_AVOID] non sono mai candidati, nemmeno come ripiego.
  - `fun pickDeep(provider: ProviderId, catalogue: ModelCatalogue, chatModel: String?): ModelInfo?`
  - `fun score(provider: ProviderId, model: ModelInfo): Int`

## `engine-ai/ai/provider/GeminiProvider.kt`

- `class GeminiProvider(private val http: AiHttp, private val apiKey: String, private val baseUrl: String = BASE_URL) : ChatProvider` — Gemini via `generateContent` (stabile; l'Interactions API e' ancora beta). Le parti del modello si conservano grezze in [Message.Assistant.raw] e si rimandano identiche: Gemini 3 pretende di rivedere le sue thought signature sulle `functionCall`, e una parte ricostruita a mano senza firma e' un 400. Per le chiamate nate altrove (dopo un cambio di provider) c'e' il valore di bypass documentato.
  - `companion object`
    - `const val BASE_URL`
    - `const val FALLBACK_TRANSCRIBE_MODEL`
- `object GeminiCodec` — Il dialetto `contents/parts` di Gemini, puro e testabile.
  - `const val SKIP_SIGNATURE` — Il valore di bypass documentato per le functionCall che Gemini non ha firmato lui.
  - `fun systemInstruction(messages: List<Message>): JsonObject?`
  - `fun contents(messages: List<Message>): JsonArray`
  - `fun userParts(parts: List<ContentPart>): JsonArray` — Le parti di un messaggio dell'utente: testo com'e', immagini e documenti come `inlineData`.
  - `fun declarations(tools: List<ToolSpec>): JsonArray`
  - `fun callingConfig(choice: ToolChoice): JsonObject`
  - `fun thinkingLevel(level: ReasoningLevel): String`
  - `fun usage(json: JsonElement?): Usage?`
  - `fun finishReason(raw: String?, hasCalls: Boolean): FinishReason`
  - `fun parseResponse(body: JsonElement?, rateLimit: RateLimitInfo): ChatTurn`
  - `fun citations(candidate: JsonElement?): List<Citation>` — Le fonti della ricerca di Google: i `groundingChunks` del candidato che vengono dal web.
  - `class StreamState`
    - `val parts`
    - `var callIndex`
    - `var finish: FinishReason?`
    - `var usage: Usage?`
    - `val citations`
    - `fun raw(): JsonArray?`
  - `fun parseStreamChunk(payload: String, state: StreamState): List<ChatDelta>`
- `object GeminiSchema` — Gemini accetta un sottoinsieme di JSON Schema e risponde 400 a `additionalProperties`, `$schema`, `default`, `examples`: si tengono solo le chiavi che capisce, ricorsivamente.
  - `fun sanitize(schema: JsonObject): JsonObject`

## `engine-ai/ai/provider/GroqProvider.kt`

- `open class GroqProvider(http: AiHttp, apiKey: String) : OpenAiCompatProvider(http, BASE_URL, apiKey)` — Groq: chat completions OpenAI-compatible con `reasoning_effort` e `reasoning_format`, Whisper in multipart, catalogo in `GET /models`. Free tier: 30 richieste e 8K token al minuto per modello — il motivo di meta' delle scelte dell'orchestratore.
  - `companion object`
    - `const val BASE_URL`
    - `const val SEARCH_MODEL` — Il sistema compound di Groq che cerca sul web da solo; il piccolo basta per cinque fonti.
    - `const val PROMPT_MAX_CHARS` — Il prompt di Whisper vale al massimo 224 token: 800 caratteri di nomi di posti ci stanno.

## `engine-ai/ai/provider/Model.kt`

- `sealed interface ContentPart` — Un pezzo di un messaggio dell'utente: testo, un'immagine, un documento. Le immagini e i documenti viaggiano in base64 dentro la richiesta (nessun provider dei tre offre un upload a parte che valga la pena): chi li mette qui ha gia' deciso che sono abbastanza piccoli.
  - `data class Text(val text: String) : ContentPart`
  - `class Image(val bytes: ByteArray, val mime: String) : ContentPart` — Un'immagine (JPEG o PNG) per un modello che vede.
  - `class Document(val bytes: ByteArray, val mime: String, val name: String) : ContentPart` — Un documento (di regola un PDF) per un modello, o un provider, che legge i documenti.
  - `val isText: Boolean get()`
- `val ContentPart.displayName: String?` — Il nome con cui parlare di una parte all'utente: quello del documento, o cos'e'.
- `sealed interface Message` — Il modello neutro della conversazione: quello che l'orchestratore vede, qualunque provider ci sia sotto. Gli adapter lo traducono nel dialetto di ciascuno (OpenAI per Groq e OpenRouter, `contents/parts` per Gemini) e ne conservano in [Message.Assistant.raw] cio' che il provider pretende di rivedere identico al turno dopo (le thought signature di Gemini, i `reasoning_details` di OpenRouter).
  - `data class System(val text: String) : Message`
  - `data class User(val parts: List<ContentPart>) : Message` — Il messaggio dell'utente: una lista di parti, che nel caso comune e' un testo solo.
    - `val text: String get()` — Il solo testo, per chi non sa che fare di immagini e documenti (storia, stime di token).
    - `val hasBinaryParts: Boolean get()`
  - `data class Assistant(val text: String?, val toolCalls: List<ToolCall> = emptyList(), val raw: JsonElement? = null, val rawProvider: ProviderId? = null) : Message`
  - `data class ToolResult(val callId: String, val name: String, val content: String) : Message`
- `data class ToolCall(val id: String, val name: String, val arguments: JsonObject, val thoughtSignature: String? = null)`
- `data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)` — Una funzione offerta al modello: nome, cosa fa, e lo schema JSON dei parametri.
- `sealed interface ToolChoice`
  - `data object Auto : ToolChoice`
  - `data object None : ToolChoice`
  - `data object Required : ToolChoice`
  - `data class Named(val name: String) : ToolChoice`
- `enum class ReasoningLevel` — Quanto pensare in questa chiamata; ogni adapter lo mappa sui suoi parametri.
- `enum class ModelTier` — I tre livelli di modello di un provider, scelti in base al compito e non una volta per tutte: [ROUTER] e' il modello piccolo e veloce che sceglie i gruppi di strumenti; [CHAT] risponde e chiama i tool; [DEEP] entra quando servono documenti, immagini o un contesto grande.
- `data class ModelCapabilities(val vision: Boolean, val documents: Boolean)` — Cosa un modello sa prendere in ingresso oltre al testo.
  - `fun accepts(part: ContentPart): Boolean`
  - `companion object`
    - `val TEXT_ONLY`
- `data class Citation(val url: String, val title: String? = null, val snippet: String? = null)` — Una fonte dal web citata da una risposta con ricerca (1.26.0): Gemini la chiama grounding chunk, OpenRouter url citation, Groq search result. Qui e' un indirizzo con, se ci sono, titolo e frammento.
- `data class ChatRequest(val model: String, val messages: List<Message>, val tools: List<ToolSpec> = emptyList(), val toolChoice: ToolChoice = ToolChoice.Auto, val parallelToolCalls: Boolean = true, val reasoning: ReasoningLevel = ReasoningLevel.MEDIUM, val jsonSchema: JsonObject? = null, val maxOutputTokens: Int? = null, val temperature: Double? = null, val webSearch: Boolean = false, val webSearchMaxResults: Int = 5, val readTimeoutMillis: Int? = null, val beforeDispatch: suspend (ProviderDispatch) -> Unit = {}, val lifecycle: ProviderAttemptLifecycle = ProviderAttemptLifecycle.None)`
  - `val hasDocuments: Boolean get()` — Vero se qualche messaggio porta un documento: OpenRouter allora vuole il plugin che li legge.
- `enum class FinishReason`
- `data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int, val costUsd: Double? = null)`
  - `operator fun plus(other: Usage): Usage`
- `data class ChatTurn(val message: Message.Assistant, val finishReason: FinishReason, val usage: Usage?, val rateLimit: RateLimitInfo, val citations: List<Citation> = emptyList())`
- `sealed interface ChatDelta` — I pezzi di uno stream, nell'ordine in cui arrivano.
  - `data class Text(val text: String) : ChatDelta`
  - `data class ToolCallPart(val index: Int, val id: String?, val name: String?, val argumentsFragment: String?, val thoughtSignature: String? = null) : ChatDelta` — Un frammento di tool call; l'indice tiene insieme i pezzi della stessa chiamata.
  - `data class Raw(val raw: JsonElement) : ChatDelta` — Parti grezze del provider da conservare (thought signature, reasoning_details).
  - `data class Finish(val reason: FinishReason, val usage: Usage?, val rateLimit: RateLimitInfo, val citations: List<Citation> = emptyList()) : ChatDelta`
- `data class Transcript(val text: String, val language: String?)`
- `data class TranscribeOptions(val model: String, val language: String?, val prompt: String?)`
- `enum class ModelKind`
- `data class ModelInfo(val id: String, val displayName: String, val kind: ModelKind, val contextWindow: Int? = null, val maxOutputTokens: Int? = null, val supportsTools: Boolean = true, val supportsReasoning: Boolean = false, val audioInput: Boolean = false, val supportsVision: Boolean = false, val supportsDocuments: Boolean = false, val free: Boolean = false, val pricePromptPerM: Double? = null, val priceCompletionPerM: Double? = null, val visionVerified: Boolean = false)` — Quello che serve per scegliere un modello in un elenco; i prezzi solo dove esistono.
  - `val capabilities: ModelCapabilities get()`
- `data class ModelCatalogue(val chat: List<ModelInfo>, val stt: List<ModelInfo>)`
  - `fun chat(id: String): ModelInfo?`
- `interface ChatProvider`
  - `val id: ProviderId`
  - `suspend fun complete(request: ChatRequest): ChatTurn`
  - `fun stream(request: ChatRequest): Flow<ChatDelta>`
  - `suspend fun listModels(): ModelCatalogue`
  - `suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript`
- `data class ProviderDispatch(val provider: ProviderId, val model: String, val resolved: ReasoningLevel, val effective: String?, val support: String = "UNKNOWN", val adaptation: String? = null)` — Effective means encoded on the wire, never a claim that a provider honored the field.

## `engine-ai/ai/provider/ModelCatalog.kt`

- `object ModelCatalog` — Dai cataloghi grezzi dei tre provider agli elenchi che l'utente sfoglia: chat da una parte, trascrizione dall'altra, tutto il resto (embedding, immagini, voci, guardie) fuori. Le regole sono per sottostringa perche' e' l'unica cosa che i cataloghi promettono; i test le fissano su elenchi veri salvati.
  - `fun groq(body: JsonElement?): ModelCatalogue`
  - `fun gemini(models: JsonElement?): ModelCatalogue`
  - `fun openRouter(body: JsonElement?, includeModelsWithoutTools: Boolean = false): ModelCatalogue`
  - `fun preferredFirst(models: List<ModelInfo>, preferred: String): List<ModelInfo>` — Il preferito in testa, poi per contesto decrescente e nome: l'ordine di un elenco da scegliere.

## `engine-ai/ai/provider/OpenAiCompatCodec.kt`

- `object OpenAiCompatCodec` — Il dialetto OpenAI delle chat completions, che Groq e OpenRouter parlano entrambi: messaggi, tool, scelte, e le due letture (risposta intera, pezzo di stream). Puro, cosi' i test fissano la forma esatta del JSON che parte e leggono i pezzi registrati che arrivano.
  - `fun messages(messages: List<Message>, fileParts: Boolean = false): JsonArray` — [fileParts]: il provider accetta la parte `file` (OpenRouter, col suo parser). Dove non la accetta, un documento diventa una riga di testo che dice che c'era: meglio di un 400.
  - `fun dataUrl(mime: String, bytes: ByteArray): String`
  - `fun tools(tools: List<ToolSpec>): JsonArray`
  - `fun toolChoice(choice: ToolChoice): JsonElement`
  - `fun responseFormat(schema: JsonObject): JsonObject`
  - `fun finishReason(raw: String?, hasToolCalls: Boolean): FinishReason`
  - `fun usage(json: JsonElement?): Usage?`
  - `fun parseCompletion(body: JsonElement?, rateLimit: RateLimitInfo, provider: ProviderId): ChatTurn` — Una risposta intera: il primo `choice`, il suo messaggio, la ragione, l'uso.
  - `fun citations(message: JsonElement?): List<Citation>` — Le fonti di una risposta con ricerca web, nei due dialetti: le `annotations` di tipo `url_citation` di OpenRouter, e i `search_results` dentro `executed_tools` di Groq compound. Deduplicate per indirizzo, nell'ordine in cui compaiono.
  - `class StreamState` — Lo stato che uno stream accumula fra un pezzo e l'altro: la ragione, l'uso, i dettagli grezzi, le fonti.
    - `var finish: String?`
    - `var usage: Usage?`
    - `val reasoningDetails`
    - `val citations`
  - `fun parseStreamChunk(payload: String, state: StreamState): List<ChatDelta>` — Un pezzo di stream -> i delta che contiene. Un `error` a stream gia' aperto (Groq lo fa) e un choice con `finish_reason: "error"` (OpenRouter) sono errori, non testo: del server in generale, un [AiError.BadRequest] col suo perche' quando si sa leggere (2.8.0) — il `tool_use_failed` di Groq arriva proprio cosi', a stream aperto.
  - `fun rawFromStream(state: StreamState): JsonObject?` — Le parti grezze da conservare per OpenRouter: i reasoning_details raccolti lungo lo stream.

## `engine-ai/ai/provider/OpenAiCompatProvider.kt`

- `abstract class OpenAiCompatProvider(protected val http: AiHttp, protected val baseUrl: String, private val apiKey: String) : ChatProvider` — Quello che Groq e OpenRouter hanno in comune oltre al dialetto: la costruzione della richiesta, il giro di auto-riparazione (un 400 che nomina un parametro opzionale -> si ritenta senza), lo stream con il suo stato. Le differenze (base URL, header, campi extra) stanno nelle due sottoclassi, in un metodo ciascuna.

## `engine-ai/ai/provider/OpenRouterCatalog.kt`

- `object OpenRouterCatalog` — Le euristiche sul catalogo di OpenRouter: quale modello gratuito con tool diventa il predefinito alla verifica della chiave, e quali otto vanno sotto "Consigliati". Sono euristiche, non liste: i modelli `:free` cambiano ogni settimana e una lista nel codice sarebbe vecchia alla prima release.
  - `fun family(id: String): String?`
  - `fun score(model: ModelInfo): Int`
  - `fun pickDefaultFree(catalogue: ModelCatalogue): ModelInfo?` — Il gratuito migliore secondo [score]; null se nessun `:free` sa usare i tool (o e' da evitare).
  - `fun recommended(catalogue: ModelCatalogue, limit: Int = 8): List<ModelInfo>` — Fino a otto: i gratuiti idonei in testa, poi per punteggio e prezzo crescente.

## `engine-ai/ai/provider/OpenRouterProvider.kt`

- `data class OpenRouterKeyInfo(val label: String?, val limitUsd: Double?, val limitRemainingUsd: Double?, val usageUsd: Double?, val usageDailyUsd: Double?, val isFreeTier: Boolean)` — Cosa `GET /key` dice della chiave: crediti, uso, se e' un account senza acquisti.
- `enum class OpenRouterDataPolicy` — Cosa dire a OpenRouter sull'addestramento, quando si manda una domanda.
- `open class OpenRouterProvider(http: AiHttp, apiKey: String, private val referer: String, private val title: String, private val fallbackModels: List<String> = emptyList(), private val dataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT) : OpenAiCompatProvider(http, BASE_URL, apiKey)` — OpenRouter: lo stesso dialetto di Groq piu' il routing (`models` di riserva, `provider` con la privacy), il `reasoning` unificato, l'uso col costo. Le trascrizioni viaggiano in JSON base64 su `/audio/transcriptions`. Il catalogo e' pubblico e grande: lo legge [ModelCatalog].
  - `suspend fun keyInfo(): OpenRouterKeyInfo`
  - `companion object`
    - `const val BASE_URL`

## `engine-ai/ai/provider/ProviderAttemptLifecycle.kt`

- `interface ProviderAttemptLifecycle` — Atomic app-owned admission and durable settlement; no pricing or domain policy in the engine.
  - `suspend fun beforeAttempt(attempt: ProviderAttempt)`
  - `suspend fun afterAttempt(settlement: ProviderSettlement)`
  - `object None : ProviderAttemptLifecycle`
- `class ProviderExecutionContext(val executionId: String, val purpose: String, val lifecycle: ProviderAttemptLifecycle, val beforeDispatch: suspend (ProviderDispatch) -> Unit = {}) : AbstractCoroutineContextElement(Key)` — Inherited by all chat calls in a coroutine, including internally constructed helper/router requests.
  - `companion object Key : CoroutineContext.Key<ProviderExecutionContext>`
- `class ProviderAdmissionDenied(val reason: String) : CancellationException(reason)` — Control signals deliberately cross existing cancellation barriers without failover or replay.
- `class ProviderObserverFailure(val attemptId: String, cause: Throwable) : CancellationException("Provider observer failed for $attemptId")`
- `data class ProviderAttempt(val attemptId: String, val executionId: String?, val purpose: String?, val dispatch: ProviderDispatch, val streaming: Boolean, val payloadBytes: Int, val maxOutputTokens: Int?, val toolCount: Int, val binaryInputBytes: Long, val webSearch: Boolean, val webSearchMaxResults: Int?, val documents: Boolean, val structuredOutput: Boolean, val modelCandidates: List<String> = listOf(dispatch.model), val inputTokenUpperBound: Int? = null)`
- `enum class ProviderAttemptOutcome`
- `enum class ProviderDispatchCertainty`
- `data class ProviderSettlement(val attempt: ProviderAttempt, val outcome: ProviderAttemptOutcome, val certainty: ProviderDispatchCertainty, val usage: Usage?, val rateLimit: RateLimitInfo, val httpStatus: Int?, val retryAfterSec: Double?, val errorType: String?, val partialResponse: Boolean)`

## `engine-ai/ai/provider/ProviderFactory.kt`

- `data class ReadyProvider(val provider: ChatProvider, val chatModel: String, val sttModel: String, val classifierModel: String?, val deepModel: String? = null, val catalogue: ModelCatalogue? = null)` — Un provider pronto a rispondere, coi modelli con cui lo si vuole usare adesso, uno per livello: [chatModel] risponde, [classifierModel] fa da router (null = la chat fa anche quello), [deepModel] legge documenti e testi lunghi (null = la chat fa anche quello). Il [catalogue], se c'e', dice cosa ogni modello sa prendere in ingresso.
  - `fun model(tier: ModelTier): String`
  - `fun capabilities(model: String): ModelCapabilities`
- `class ProviderFactory(private val http: AiHttp, private val keys: AiKeyStore, private val settings: AiSettingsStore, private val referer: String, private val appTitle: String, private val catalogs: ModelCatalogStore? = null, private val decorate: (ChatProvider) -> ChatProvider = { it })` — Costruisce i provider per ogni domanda leggendo le chiavi dal loro deposito: una chiave cambiata nelle impostazioni vale alla domanda dopo, senza riavvii. L'ordine e' quello dell'utente, filtrato sui provider che hanno una chiave verificata.
  - `enum class Kind`
  - `suspend fun build(provider: ProviderId, settings: AiSettings): ReadyProvider?`
  - `suspend fun ordered(kind: Kind): List<ReadyProvider>` — I provider con chiave verificata, nell'ordine scelto per [kind].
  - `suspend fun forVerification(provider: ProviderId): ChatProvider?` — Un provider con chiave anche se non ancora verificata: per verificarla.

## `engine-ai/ai/provider/ProviderId.kt`

- `enum class ProviderId(val id: String, val label: String)` — I tre servizi che l'assistente sa usare, ognuno con la chiave dell'utente. L'ordine di dichiarazione e' anche l'ordine di default con cui rispondono (Groq -> Gemini -> OpenRouter): Groq perche' e' il piu' veloce e gratuito, Gemini perche' e' gratuito e largo, OpenRouter per ultimo perche' i suoi modelli migliori costano.
  - `companion object`
    - `fun fromId(id: String?): ProviderId?`
    - `val defaultOrder: List<ProviderId>` — L'ordine con cui l'app parte, prima che l'utente lo tocchi.

## `engine-ai/ai/provider/ToolCallAssembler.kt`

- `class ToolCallAssembler(private val idFactory: () -> String = { localCallId() })` — Ricompone le tool call di uno stream. Groq e OpenRouter mandano il nome nel primo pezzo e gli argomenti a frammenti di stringa JSON; Gemini ogni `functionCall` intera in una parte. Qui si accumula per indice e alla fine si parsa una volta: argomenti malformati diventano `{"_raw": "..."}` e il tool rispondera' con un errore leggibile, invece di far cadere il giro.
  - `fun add(part: ChatDelta.ToolCallPart)`
  - `val isEmpty: Boolean get()`
  - `fun build(): List<ToolCall>`
  - `companion object`
    - `fun parseArguments(raw: String): JsonObject`
    - `fun localCallId(): String` — Un id locale per i provider che non ne danno (Gemini): stile OpenAI, cosi' Groq lo accetta.
