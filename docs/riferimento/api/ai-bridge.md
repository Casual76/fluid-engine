<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/ai-bridge.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-ai-bridge

_Generato sull'engine 3.1.0 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ai-bridge/ai/bridge/AiToolClient.kt`

- `enum class RemoteAvailability` — Cosa il cliente puo' dire di un'app ospite. [NO_PERMISSION] vuol dire che l'ospite ha rifiutato la chiamata (`SecurityException`): una firma diversa — una build di debug di Android Studio accanto a quella dello store — oppure, per un ospite della 2.x, il permesso `AI_TOOLS` che il cliente non chiede piu'.
- `class AiToolClient(private val context: Context) : BridgeCalls` — Il cliente: trova le app che espongono tool ([discover]) e le chiama. Ogni chiamata passa da un `ContentProviderClient` *unstable*, cosi' un ospite che muore a meta' non uccide il cliente: si riceve un'eccezione e si risponde con un errore. Il cliente dichiara nel manifest un `<queries>` con `QUERY_ALL_PACKAGES` o con le autorita' note, altrimenti Android nasconde i provider delle altre app.
  - `fun negotiation(authority: String): BridgeNegotiation` — Features agreed during catalog discovery; absent/old hosts have no extended capabilities.
  - `fun discover(): List<RemoteHost>` — Le app installate che espongono i tool: quelle con il meta-data sul provider.
  - `fun availability(host: RemoteHost): RemoteAvailability`
  - `suspend fun catalog(host: RemoteHost): RemoteCatalog?` — Il catalogo di un ospite; null se non risponde, non ha il permesso, o parla un protocollo piu' nuovo.
- `data class RemoteHost(val packageName: String, val authority: String, val label: String)` — Un'app ospite trovata sul telefono.

## `engine-ai-bridge/ai/bridge/AiToolHostProvider.kt`

- `data class RemoteCall(val callingPackage: String?, val language: String, val confirmed: Boolean, val requestId: String)` — Chi sta chiamando e come: il pacchetto, la lingua, se la conferma e' gia' stata data.
- `data class ReadyState(val ready: Boolean, val reason: String? = null)` — L'ospite e' pronto a rispondere? (loggato, con dati, con il motore avviato).
- `abstract class AiToolHostProvider<C> : ContentProvider()` — L'ospite: un `ContentProvider` che espone i tool dell'app a un assistente esterno con lo stesso firmatario. L'app dichiara il provider nel manifest con l'autorita' `<applicationId>.ai.tools`, `exported="true"` **senza `android:permission`** e il meta-data [BridgeProtocol.META_TOOL_HOST], e in una sottoclasse da' il registry, il contesto dei tool e le parole di se stessa.
  - `abstract fun registry(): ToolRegistry<C>` — Il registry dell'app: lo stesso del suo assistente interno.
  - `abstract suspend fun context(call: RemoteCall): C` — Il contesto con cui girano i tool per questa chiamata, con il gate gia' confermato.
  - `abstract fun domain(): String` — Il prefisso dei nomi nel registry del cliente (`cv`, `meteo`, `bus`, `convert`, `store`).
  - `open fun appLabel(): String`
  - `open fun routerHint(): String?` — Una riga per il router del cliente: cosa sa fare quest'app, in parole.
  - `open fun vocabulary(): List<String>` — Parole da riconoscere a voce (nomi di fermate, materie, luoghi salvati).
  - `open fun ready(): ReadyState`
  - `open val callTimeoutMillis: Long` — Quanto una singola `call` puo' durare prima di rispondere "in corso" o "timeout".
  - `open fun partsAuthority(): String?` — L'autorita' del `FileProvider` con cui consegnare le parti (screenshot, PDF) al cliente; null = le parti si scartano e resta il testo. La cartella e' `cacheDir/ai-bridge/`: va dichiarata nei `file_paths.xml` del FileProvider (`<cache-path name="ai-bridge" path="ai-bridge/"/>`).
  - `open fun isCallerTrusted(callingUid: Int): Boolean` — Chi puo' chiamare: le app firmate come questa. [callingUid] e' quello di `Binder`; una chiamata dentro lo stesso processo (nessuna transazione in corso) ha il nostro uid e passa. Una sottoclasse puo' restringere (un elenco di pacchetti) chiamando `super`; allargare non conviene mai: `ContentProvider.call` e' l'unica porta, e questo l'unico cancello.
- `class BridgeJobs` — I lavori lunghi di un ospite (un'installazione, una conversione): partono in un proprio scope, il cliente li segue con `status`. Vivono quanto il processo dell'ospite; un lavoro finito resta consultabile dieci minuti, poi si dimentica.
  - `class Entry(val job: Job)`
  - `fun start(block: suspend () -> ToolOutput): String`
  - `operator fun get(id: String): Entry?`
  - `fun cancel(id: String)`
  - `fun report(id: String, detail: String?, progress: Double?)` — Un tool che conosce il proprio jobId puo' raccontare a che punto e'.

## `engine-ai-bridge/ai/bridge/BridgeProtocol.kt`

- `object BridgeProtocol` — Il protocollo fra un'app che espone i suoi tool (l'**ospite**, un `ContentProvider`) e l'app che li chiama (il **cliente**, un assistente come PampAI): JSON in stringhe dentro i `Bundle` di `ContentProvider.call`. Puro Kotlin, cosi' si prova sulla JVM. Tutto cio' che attraversa il confine e' qui: [PROTOCOL] resta legacy, le estensioni si negoziano fino a [MAX_PROTOCOL].
  - `const val PROTOCOL`
  - `const val MAX_PROTOCOL`
  - `const val CAP_TYPED_RESULTS`
  - `const val CAP_OPERATION_ID`
  - `val CAPABILITIES: Set<String>`
  - `const val METHOD_NEGOTIATE`
  - `fun negotiationRequest(): JsonObject`
  - `fun negotiate(request: JsonObject?): JsonObject`
  - `fun agreementOf(response: JsonObject?): BridgeNegotiation` — Old hosts return unknown-method errors; the safe adapter remains protocol 1.
  - `fun typedRequested(request: JsonObject?): Boolean`
  - `fun withResult(envelope: JsonObject, output: dev.antigravity.fluidengine.ai.tools.ToolOutput, request: JsonObject?): JsonObject`
  - `const val META_TOOL_HOST` — Il meta-data sul `<provider>` dell'ospite che lo fa trovare al cliente.
  - `const val PERMISSION` — **DEPRECATO: Il permesso non esiste piu': il provider controlla la firma di chi chiama e il manifest non porta android:permission.** — Il permesso `signature` che stava sul provider fino alla 3.0.0. Non esiste piu': il modulo non lo dichiara e il provider non lo chiede, perche' due app con firme diverse non potevano installarlo insieme. L'ospite controlla la firma di chi chiama ([AiToolHostProvider.isCallerTrusted]) e il `<provider>` non porta `android:permission`.
  - `const val AUTHORITY_SUFFIX` — Il suffisso dell'autorita': `<applicationId>.ai.tools`.
  - `const val METHOD_CATALOG`
  - `const val METHOD_DESCRIBE`
  - `const val METHOD_RUN`
  - `const val METHOD_STATUS`
  - `const val METHOD_CANCEL`
  - `const val METHOD_READY`
  - `const val KEY_JSON` — La chiave del JSON nei Bundle di richiesta e risposta.
  - `const val KEY_PENDING_INTENT` — La chiave del `PendingIntent` (Parcelable, non JSON) in una risposta di `status`.
  - `const val STATE_RUNNING`
  - `const val STATE_DONE`
  - `const val STATE_FAILED`
  - `const val STATE_UNKNOWN`
  - `val json: Json`
  - `fun parse(text: String?): JsonObject?`
  - `fun <C> catalogJson(registry: ToolRegistry<C>, appId: String, domain: String, appLabel: String, appVersion: String, hint: String?, vocabulary: List<String>): JsonObject` — Il catalogo di un'app: dal suo registry, nel formato che il cliente ricostruisce in [RemoteCatalog].
  - `fun catalogOf(json: JsonObject?, authority: String, packageName: String): RemoteCatalog?`
  - `fun describeRequest(name: String, args: JsonObject, language: String): JsonObject`
  - `fun describeResponse(title: String?, detail: String?): JsonObject`
  - `fun runRequest(name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): JsonObject`
  - `fun resultResponse(text: String, escalate: Boolean, parts: List<RemotePart>): JsonObject` — Una risposta finita: testo, escalation, parti (file consegnati come URI).
  - `fun jobResponse(jobId: String): JsonObject` — Un lavoro lungo avviato: il cliente lo segue con `status`.
  - `fun statusRequest(jobId: String): JsonObject`
  - `fun statusResponse(state: String, detail: String?, progress: Double?, text: String?, escalate: Boolean, parts: List<RemotePart>): JsonObject`
  - `fun readyResponse(ready: Boolean, reason: String?): JsonObject`
  - `fun errorResponse(message: String): JsonObject`
  - `fun resultOf(json: JsonObject?): RemoteResult?` — Un risultato di `run` o `status` letto dal cliente.
- `data class RemoteCategoryInfo(val id: String, val label: String, val hint: String)`
- `data class RemoteGroupInfo(val id: String, val statusKey: String, val hint: String, val category: String?, val parent: String?, val loadsWithCategory: Boolean, val action: Boolean)`
- `data class RemoteToolInfo(val name: String, val group: String, val description: String, val parameters: JsonObject, val needsConfirmation: Boolean, val longRunning: Boolean, val action: Boolean, val timeoutMillis: Long? = null)`
- `data class RemoteCatalog(val protocol: Int, val authority: String, val packageName: String, val appId: String, val domain: String, val appLabel: String, val appVersion: String, val hint: String?, val vocabulary: List<String>, val categories: List<RemoteCategoryInfo>, val groups: List<RemoteGroupInfo>, val tools: List<RemoteToolInfo>)` — Il catalogo di un'app ospite, come lo vede il cliente.
- `data class RemotePart(val kind: String, val uri: String, val mime: String, val name: String?)` — Un file consegnato da un tool ospite: un URI leggibile dal chiamante (grant temporaneo).
  - `fun toJson(): JsonObject`
  - `companion object`
    - `const val KIND_IMAGE`
    - `const val KIND_DOCUMENT`
    - `fun of(json: JsonElement?): RemotePart?`
- `sealed interface RemoteResult`
  - `data class Done(val text: String, val escalate: Boolean, val parts: List<RemotePart>, val result: dev.antigravity.fluidengine.ai.tools.ToolResultDetails? = null) : RemoteResult`
  - `data class Job(val jobId: String) : RemoteResult`
  - `data class Status(val state: String, val detail: String?, val progress: Double?, val text: String?, val escalate: Boolean, val parts: List<RemotePart>, val result: dev.antigravity.fluidengine.ai.tools.ToolResultDetails? = null) : RemoteResult`
  - `data class Error(val message: String) : RemoteResult`
- `fun JsonArray?.strings(): List<String>` — Per i test e per chi vuole leggere un `parameters` senza il registry.
- `data class BridgeNegotiation(val protocol: Int = 1, val capabilities: Set<String> = emptySet())` — Negotiated transport features, available to app capability gates. No durable dedup capability exists.
  - `fun toJson(): JsonObject`

## `engine-ai-bridge/ai/bridge/RemoteTools.kt`

- `data class RemoteCategory(override val id: String, override val label: String, override val hint: String) : AiToolCategory` — Una categoria di un'app ospite, nel registry del cliente.
- `class RemoteGroup(override val id: String, override val statusKey: String, override val hint: String, override val category: AiToolCategory?, override val loadsWithCategory: Boolean, var parentGroup: RemoteGroup? = null, val remoteId: String) : AiToolGroup` — Un gruppo di un'app ospite, con l'id prefissato dal dominio (`cv_voti`).
- `interface RemoteToolHost<C>` — Cosa il cliente deve saper fare per un tool remoto: chiedere la conferma con il proprio cancello (e le proprie azioni fidate), dire in che lingua, e — per i lavori lunghi — quanto aspettare.
  - `suspend fun confirm(ctx: C, toolName: String, text: ConfirmationText): ToolOutput?` — Torna null se si puo' procedere, o il testo da restituire al modello (rifiutato, scaduto, azioni spente).
  - `fun language(ctx: C): String`
  - `fun actionsEnabled(ctx: C): Boolean` — Vero se l'utente ha spento le azioni: i tool azione rispondono senza chiamare l'ospite.
  - `val actionsOffText: String get()`
  - `val longRunningTimeoutMillis: Long get()`
- `interface BridgeCalls` — Le chiamate che un tool remoto fa attraverso il confine: [AiToolClient] le implementa, i test le fingono.
  - `suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>?`
  - `suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult`
  - `suspend fun status(authority: String, jobId: String): RemoteResult`
  - `suspend fun cancel(authority: String, jobId: String)`
  - `fun readPart(part: RemotePart): ByteArray?`
- `class RemoteTool<C>(private val client: BridgeCalls, val catalog: RemoteCatalog, val info: RemoteToolInfo, override val group: AiToolGroup, private val host: RemoteToolHost<C>) : AiTool<C>` — Un tool di un'app ospite visto dal cliente: stesso nome prefissato dal dominio, stessa descrizione con l'app fra parentesi, e il `run` che attraversa il confine. La conferma la chiede il cliente **prima** (con `describe` dell'ospite, o il titolo generico), poi chiama `run` con `confirmed = true`; un lavoro lungo si segue con `status` ogni secondo.
- `class RemoteToolSet<C>(val catalog: RemoteCatalog, val categories: List<RemoteCategory>, val groups: List<RemoteGroup>, val tools: List<RemoteTool<C>>)` — Il catalogo di un ospite tradotto in gruppi e tool del registry del cliente.
  - `companion object`
    - `fun <C> of(client: BridgeCalls, catalog: RemoteCatalog, host: RemoteToolHost<C>): RemoteToolSet<C>` — Costruisce gruppi e tool dal catalogo. Le categorie dell'ospite restano quelle (`classeviva`, `meteo`), i gruppi e i tool prendono il prefisso del dominio; un gruppo senza categoria va sotto una categoria costruita dal dominio, cosi' nel router ogni app e' una porta.
