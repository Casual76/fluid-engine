<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/ai-bridge.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-ai-bridge

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

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

## `engine-ai-bridge/ai/bridge/assist/AiAssistClient.kt`

- `data class AiAssistClientConfig(val hostPackage: String = PampaiPackage, val devHostPackage: String? = PampaiDevPackage, val devBuild: Boolean? = null, val pollIntervalMillis: Long = 0L, val graceMillis: Long = 5_000L, val storePackage: String = StorePackage, val devStorePackage: String? = StoreDevPackage)` — Dove l'app cerca PampAI.
  - `companion object`
    - `const val PampaiPackage`
    - `const val PampaiDevPackage`
    - `const val StorePackage`
    - `const val StoreDevPackage`
    - `const val StoreActionOpen` — Come si apre Pampa Store alla pagina di un'app: l'azione dell'Account Pampa (`dev.pampa.account.OPEN`) con la sezione `app` e il pacchetto in `app`. E' il contratto di F9.1b per lo Store (che lo impara col passaggio all'engine 4.0); uno Store che non lo conosce si apre sulla sua prima pagina.
    - `const val StoreExtraSection`
    - `const val StoreSectionApp`
    - `const val StoreExtraApp`
- `data class AiAssistHostInfo(val packageName: String, val authority: String, val uid: Int)` — PampAI trovata: il pacchetto, l'autorita' dell'ospite dell'assistenza, l'uid (per riconoscere i suoi invii).
- `class AiAssistClient internal constructor(val config: AiAssistClientConfig, private val locate: () -> Located, private val connector: AiAssistConnector, private val io: CoroutineDispatcher, private val install: () -> Intent? = { null }, private val launch: (String) -> Intent? = { null })`
  - `fun host(): AiAssistHostInfo?` — PampAI, se c'e' e ha la firma giusta.
  - `suspend fun check(): AiAssistStatus` — Cosa l'app puo' fare adesso: se mostrare il tasto «Chiedi a PampAI», quali compiti, il sì per la pagina.
  - `fun ask(request: AiAssistRequest): Flow<AiAssistProgress>` — Chiede a PampAI. Il primo stato e' quello vuoto (la domanda e' partita, o sta per), poi uno a ogni novita'; l'ultimo e' finito ([AiAssistProgress.finished]).
  - `suspend fun run(request: AiAssistRequest): AiAssistProgress` — Chiede e aspetta la fine: l'ultimo stato.
  - `suspend fun answer(question: String, page: AiAssistPage? = null): AiAssistProgress` — Una domanda libera, con la pagina se c'e'.
  - `fun consentIntent(page: Boolean = false): Intent?` — L'Activity di PampAI che chiede il sì: la prima volta per usare PampAI da quest'app, e per la pagina se [page] (**un foglio solo** per le due domande, F9.1b). Da aprire con `ActivityResultContracts.StartActivityForResult` (solo cosi' PampAI sa dal sistema chi chiede); il risultato si legge con [AiAssistConsent.resultOf]. Null se PampAI non c'e'.
  - `fun installIntent(): Intent?` — «Installa PampAI» (F9.1b): Pampa Store alla pagina di PampAI, se lo Store c'e' ed e' firmato come l'app; uno Store che non conosce ancora quella pagina si apre sulla sua prima pagina. Null se lo Store non c'e': l'app mostra [AiAssistTexts.installHint]. Va usato solo per [AiAssistEntry.ActionInstall] e [AiAssistEntry.ActionUpdate], cioe' quando PampAI non c'e' o e' vecchia: mai per un'app non autorizzata.
  - `fun openIntent(): Intent?` — «Apri PampAI» (per aggiungere una chiave, [AiAssistEntry.ActionOpen]); null se PampAI non c'e'.

## `engine-ai-bridge/ai/bridge/assist/AiAssistConsent.kt`

- `interface AiAssistConsentStore` — Il registro delle scelte dell'utente per le app che chiedono a PampAI: il sì per **usare PampAI** da un'app (F9.1b: la prima volta PampAI chiede) e il sì per **la pagina**. Sta in PampAI (l'ospite), mai nell'app: se lo tenesse l'app, un'app potrebbe rispondersi da sola.
  - `fun appConsent(packageName: String): AiAssistConsentState` — Il sì per usare PampAI da [packageName]. [AiAssistConsentState.Denied] e' anche «bloccata».
  - `fun setAppConsent(packageName: String, consent: AiAssistConsentState)`
  - `fun pageConsent(packageName: String): AiAssistConsentState`
  - `fun setPageConsent(packageName: String, consent: AiAssistConsentState)`
  - `fun forget(packageName: String)` — Dimentica l'app: si torna a «non chiesto» per tutte e due le scelte.
  - `fun known(): Set<String>` — Le app di cui c'e' una scelta, per la pagina «App che usano PampAI».
- `class InMemoryAiAssistConsentStore : AiAssistConsentStore` — Il registro in memoria: per le prove e per un ospite che non deve ricordare.
- `class SharedPreferencesAiAssistConsentStore(context: Context, name: String = "fluid-ai-assist-consents") : AiAssistConsentStore` — Il registro nelle `SharedPreferences` private dell'ospite (un file, [name]). Letture dalla copia in memoria, scritture con `apply`. Un processo solo: l'Activity del consenso e il provider devono stare nello stesso processo dell'ospite.
- `data class AiAssistConsentAnswer(val app: AiAssistConsentState, val page: AiAssistConsentState)` — Cio' che l'utente ha scelto nel foglio di PampAI, come lo legge l'app ([AiAssistConsent.resultOf]).
- `class AiAssistConsent(private val host: AiAssistHost)` — Le domande del consenso, nell'Activity di PampAI (come i luoghi dell'Account Pampa, F10.8). La domanda e' di PampAI, come un permesso di Android e' del sistema. Sono due, e stanno **in un foglio solo** quando servono tutte e due (F9.1b, scelta di Alessio):
  - `data class Choice(val label: String, val allowApp: Boolean, val allowPage: Boolean)` — Un tasto del foglio: cosa vuol dire per l'app e per la pagina.
  - `sealed interface Step`
    - `data class Ask(val packageName: String, val appName: String, val askApp: Boolean, val askPage: Boolean) : Step` — Da chiedere all'utente con [question] e i tasti [choices]; poi [answer].
    - `data class Decided(val app: AiAssistConsentState, val page: AiAssistConsentState) : Step` — Gia' deciso: niente da chiedere.
    - `data object Refused : Step` — Non si chiede: chi chiama non si sa o non e' in tabella.
  - `fun begin(callingPackage: String?, withPage: Boolean = false): Step` — Cosa fare per una richiesta di [callingPackage] (dal sistema) che vuole anche la pagina se [withPage]. Un'app gia' rifiutata non viene richiesta ([Step.Decided] col «no»).
  - `fun answer(ask: Step.Ask, choice: Choice): Step.Decided` — La risposta dell'utente a [ask] con [choice]. Con «Non consentire» la prima volta, l'app e' rifiutata e la pagina resta da chiedere (non serve piu'); con «Senza la pagina» l'app puo' chiedere e la pagina e' negata.
  - `companion object`
    - `const val ACTION` — L'azione dell'Activity del consenso di PampAI.
    - `const val EXTRA_WITH_PAGE` — L'extra della richiesta: l'app vuole anche mandare la pagina (un foglio solo per le due domande).
    - `const val EXTRA_CONSENT` — L'extra del risultato per la pagina: `allowed`, `denied` o `not_asked`.
    - `const val EXTRA_APP_CONSENT` — L'extra del risultato per l'app (F9.1b).
    - `fun isRequest(intent: Intent?): Boolean` — E' una richiesta di consenso?
    - `fun wantsPage(intent: Intent?): Boolean` — La richiesta vuole anche la pagina?
    - `fun callerOf(activity: Activity): String?` — Chi chiede, dal sistema, se ha la stessa firma dell'ospite; altrimenti null. Senza `startActivityForResult` il sistema non lo dice, e la risposta e' null.
    - `fun resultIntent(decided: Step.Decided): Intent` — Il risultato da restituire all'app ([Activity.setResult] con `RESULT_OK`).
    - `fun resultOf(resultCode: Int, intent: Intent?): AiAssistConsentAnswer?` — La scelta letta dall'app, dal risultato dell'Activity di PampAI (aperta con `AiAssistClient.consentIntent()` e `ActivityResultContracts.StartActivityForResult`): null se PampAI non c'e' o l'utente e' uscito senza scegliere.

## `engine-ai-bridge/ai/bridge/assist/AiAssistHost.kt`

- `data class AiAssistLimits(val maxInputChars: Int = 24_000, val maxOutputChars: Int = 32_000, val maxPerApp: Int = 2, val maxTotal: Int = 4, val startsPerMinute: Int = 30, val minTimeoutMillis: Long = 1_000L, val maxTimeoutMillis: Long = 120_000L, val flushMillis: Long = 50L, val keepMillis: Long = 60_000L)` — I numeri dell'ospite dell'assistenza. Quelli di serie sono «a occhio» (26/11/2026), coi motivi nel contratto di F9.1 (§ 6 e § 8): stanno lontani dal buffer di Binder (1 MB per processo, meta' per le transazioni a senso unico) e dal contesto dei tre fornitori di PampAI.
- `class AiAssistHost(private val executor: AiAssistExecutor, val consents: AiAssistConsentStore, val policy: AiAssistPolicy = AiAssistPolicy(), val limits: AiAssistLimits = AiAssistLimits(), private val ready: () -> ReadyState = { ReadyState(true) }, private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.De…, private val clock: () -> Long = { System.nanoTime() / 1_000_000L }, private val history: AiAssistHistory? = null, private val wallClock: () -> Long = { System.currentTimeMillis() })` — L'ospite dell'assistenza, **senza Android**: il pezzo che PampAI tiene per tutta la vita del processo (nel suo grafo) e che `AiAssistHostProvider` usa per ogni chiamata. Riceve chiamanti gia' verificati dal sistema ([AiAssistCaller]) e decide, nell'ordine:
  - `fun setAppConsent(packageName: String, consent: AiAssistConsentState)` — Il sì per usare PampAI da un'app (F9.1b). Il «no» ([AiAssistConsentState.Denied]) e' anche il blocco: da subito `not_authorized`, e le sue domande in corso si fermano.
  - `fun setBlocked(packageName: String, blocked: Boolean)` — Blocca (il «no» di [setAppConsent]) o sblocca (il sì) un'app.
  - `fun setPageConsent(packageName: String, consent: AiAssistConsentState)` — Il sì per la pagina di un'app. Toglierlo ferma le sue domande in corso che hanno una pagina.
  - `fun forget(packageName: String)` — Dimentica un'app: tutte e due le scelte tornano «da chiedere»; le sue domande in corso si fermano.
  - `fun inFlight(packageName: String): Int` — Le domande in corso di [packageName].
  - `fun cancelAll()` — Ferma tutte le domande in corso (PampAI chiude, l'utente spegne l'assistenza).

## `engine-ai-bridge/ai/bridge/assist/AiAssistHostProvider.kt`

- `abstract class AiAssistHostProvider : ContentProvider()` — L'ospite dell'assistenza, per PampAI: un `ContentProvider` «solo `call`» con autorita' `<applicationId>.ai.assist`, `exported="true"` **senza `android:permission`** e il meta-data `dev.antigravity.fluidengine.ai.ASSIST_HOST` con valore `3`.
  - `abstract fun host(): AiAssistHost` — L'ospite del processo: lo stesso oggetto per ogni chiamata (le domande in corso vivono li').
  - `companion object`
    - `const val MemoryTimeoutMillis: Long` — Quanto aspetta al piu' la memoria per un metodo dei ricordi: 10 s, a occhio (27/11/2026; una scrittura cifrata).

## `engine-ai-bridge/ai/bridge/assist/AiAssistModel.kt`

- `data class AiAssistTask(val kind: String, val text: String, val style: String? = null)` — Cosa l'app chiede a PampAI: il tipo ([kind]), il testo (la domanda, o il testo da riscrivere o riassumere) e, per riscrivere e riassumere, lo stile. I valori sono testi e non un enum: un compito nuovo si aggiunge senza rompere chi c'era, e un ospite che non lo conosce risponde `needs_update`.
  - `companion object`
    - `const val KindAnswer` — Una domanda libera. Ambito [AiAssistPolicy.ScopeAnswer].
    - `const val KindRewrite` — Riscrivere un testo dell'utente con uno stile `Style*`. Ambito [AiAssistPolicy.ScopeRewrite].
    - `const val KindSummarize` — Riassumere un testo in una forma `Form*`. Ambito [AiAssistPolicy.ScopeSummarize].
    - `const val StyleProofread` — Correggi: ortografia e grammatica, senza cambiare il tono.
    - `const val StyleRewrite` — Riscrivi: lo stesso significato, parole nuove.
    - `const val StyleFriendly` — Cordiale.
    - `const val StyleProfessional` — Professionale.
    - `const val StyleConcise` — Breve.
    - `const val FormSummary` — Un riassunto in un paragrafo.
    - `const val FormKeyPoints` — I punti chiave, uno per riga.
    - `const val FormList` — Un elenco.
    - `const val FormTable` — Una tabella.
    - `val Kinds: Set<String>` — I compiti che questa versione conosce.
    - `val RewriteStyles: List<String>` — Gli stili di [KindRewrite], nell'ordine di Writing Tools.
    - `val SummaryForms: List<String>` — Le forme di [KindSummarize], nell'ordine di Writing Tools.
    - `fun answer(question: String): AiAssistTask`
    - `fun rewrite(text: String, style: String = StyleRewrite): AiAssistTask`
    - `fun summarize(text: String, form: String = FormSummary): AiAssistTask`
- `class AiAssistPage(title: String, lines: List<String> = emptyList(), selection: String? = null, kind: String? = null)` — La pagina che l'utente ha davanti, scritta dall'app (`assistente-vede-la-pagina`): un titolo, un tipo facoltativo, le righe visibili e la selezione. **Nient'altro**: il formato non ha posto per identificativi, indirizzi o dati nascosti. Corta per costruzione: ogni campo ha un tetto e il totale non supera [MaxTotalChars] caratteri (circa 600 gettoni);
  - `val title: String`
  - `val lines: List<String>`
  - `val selection: String?`
  - `val kind: String?`
  - `val truncated: Boolean` — Vero se qualcosa e' stato tagliato per stare nei tetti.
  - `val length: Int get()` — Quanti caratteri passano in tutto.
  - `fun preview(): String` — Il testo che passa a PampAI, come lo legge una persona: il titolo, le righe e la selezione. Lo mostra il chip «Questa pagina» quando si chiede cosa passa.
  - `companion object`
    - `const val MaxTitleChars`
    - `const val MaxKindChars`
    - `const val MaxLines`
    - `const val MaxLineChars`
    - `const val MaxSelectionChars`
    - `const val MaxTotalChars`
- `data class AiAssistRequest(val task: AiAssistTask, val page: AiAssistPage? = null, val tools: String = ToolsCaller, val language: String = "it", val timeoutMillis: Long = DefaultTimeoutMillis)` — Una domanda a PampAI.
  - `companion object`
    - `const val ToolsCaller`
    - `const val ToolsCallerOnly`
    - `const val ToolsNone`
    - `const val DefaultTimeoutMillis` — 60 s: a occhio (26/11/2026), una risposta con due giri di tool. Il tetto dell'ospite e' 120 s.
- `data class AiAssistStatusLine(val line: String, val app: String? = null)` — La riga semplice di cosa fa PampAI («Controllo i voti») e l'app di cui usa i tool, per l'icona (F9.4).
- `data class AiAssistReceipt(val line: String, val app: String? = null, val outcome: String = OutcomeDone)` — La ricevuta di un'azione fatta durante la domanda: una riga, l'app, l'esito.
  - `companion object`
    - `const val OutcomeDone`
    - `const val OutcomeUncertain` — L'effetto non e' certo (una risposta persa): da verificare nell'app, mai da ripetere da soli.
    - `const val OutcomeFailed`
- `data class AiAssistError(val code: String, val retryAfterMillis: Long? = null, val reason: String? = null, val needs: Set<String> = emptySet())` — Un errore dell'assistenza: un **codice fisso**, mai il testo di un'eccezione o di un fornitore (che potrebbe portare una chiave). Le parole le mette l'engine ([message]).
  - `fun message(language: String = "it"): String` — Le parole per l'utente, in italiano o in inglese.
  - `val retryable: Boolean` — Se ha senso offrire «Riprova» con la stessa domanda (F9.1d). Sì quando la stessa domanda puo' andare dopo: [Timeout], [Interrupted], [Failed] (e ogni codice sconosciuto), [Busy] e [LimitReached] (dopo [retryAfterMillis]), [ConsentRequired] (riprovare riapre il foglio del sì).
  - `companion object`
    - `const val NotInstalled`
    - `const val NeedsUpdate`
    - `const val NotAuthorized`
    - `const val ConsentRequired`
    - `const val NotReady`
    - `const val LimitReached`
    - `const val Busy`
    - `const val TooLarge`
    - `const val Cancelled`
    - `const val Timeout`
    - `const val Interrupted`
    - `const val Failed`
    - `val Codes: Set<String>` — I codici che questa versione conosce; un codice sconosciuto si legge come [Failed].
    - `const val NeedsApp` — Manca il sì per usare PampAI da quest'app (la prima volta, F9.1b).
    - `const val NeedsPage` — Manca il sì per la pagina.
- `data class AiAssistProgress(val text: String = "", val status: AiAssistStatusLine? = null, val receipts: List<AiAssistReceipt> = emptyList(), val done: Boolean = false, val error: AiAssistError? = null)` — Lo stato di una domanda, **intero** e immutabile: il testo arrivato finora, la riga di stato, le ricevute, se e' finita e come. `AiAssistClient.ask` ne emette uno a ogni novita'; in Compose basta mostrare l'ultimo. L'ultimo elemento del flusso ha [done] vero o un [error].
  - `val finished: Boolean get()`
  - `val retryable: Boolean` — Se offrire «Riprova» per questa domanda: come [AiAssistError.retryable], ma **non dopo una scrittura** (R3-S1). Una ricevuta fatta o da verificare vuol dire che un tool ha gia' scritto qualcosa (un evento, un promemoria): rifare la stessa domanda lo rifarebbe. Allora decide l'utente, guardando le ricevute.
- `enum class AiAssistConsentState` — Una scelta dell'utente nel registro di PampAI: il sì per usare PampAI da un'app (la prima volta, F9.1b) e il sì per la pagina. «No» si ricorda: PampAI non chiede piu', e si cambia da «App che usano PampAI».
- `data class AiAssistStatus(val ready: Boolean, val tasks: Set<String> = emptySet(), val tools: Boolean = false, val page: Boolean = false, val pageConsent: AiAssistConsentState = AiAssistConsentState.NotAsked, val error: AiAssistError? = null, val hostPackage: String? = null, val appConsent: AiAssistConsentState = AiAssistConsentState.NotAsked)` — Cosa l'app puo' fare con PampAI adesso (`AiAssistClient.check`): se e' pronta, quali compiti puo' chiedere, se puo' usare i suoi tool e mandare la pagina, il sì per la pagina e (F9.1b) il sì per usare PampAI. Con [error] il tasto «Chiedi a PampAI» si vede spento ([AiAssistEntry.of]). Un'app che l'utente ha rifiutato o bloccato riceve solo `not_authorized`, senza nient'altro.
- `data class AiAssistEntry(val enabled: Boolean, val note: String? = null, val action: String? = null, val actionLabel: String? = null)` — Come si mostra il tasto «Chiedi a PampAI» (F9.1b, scelta di Alessio del 26/11/2026: **spento, non nascosto**, quando PampAI non c'e'). [enabled] dice se si puo' toccare; [note] e' la riga sotto («PampAI non c'è»);
  - `companion object`
    - `const val ActionInstall`
    - `const val ActionUpdate`
    - `const val ActionOpen`
    - `fun of(status: AiAssistStatus?, language: String = "it"): AiAssistEntry` — Il tasto per [status] nella lingua [language]; null (non ancora controllato) e' spento, senza parole.
- `data class AiAssistCaller(val packageName: String, val label: String, val toolsAuthority: String? = null)` — Chi chiama, come lo vede l'ospite: **verificato dal sistema** (uid di Binder, firma, un pacchetto solo), mai scritto dall'app. [toolsAuthority] e' l'ospite dei tool dell'app chiamante, trovato da PampAI nel suo pacchetto (il provider col meta-data `TOOL_HOST`), null se l'app non ne ha.
- `class AiAssistCall(val requestId: String, val caller: AiAssistCaller, val task: AiAssistTask, val page: AiAssistPage?, val tools: String, val language: String, val timeoutMillis: Long)` — Una domanda accettata, come arriva all'esecutore di PampAI: chi chiama, il compito, la pagina **solo se** l'utente ha detto sì per quell'app e l'app l'ha mandata, quali tool, la lingua, la scadenza (in millisecondi dell'orologio dell'ospite).
- `interface AiAssistSink` — Dove l'esecutore scrive cosa sta facendo. Non sospende: l'ospite raccoglie e manda all'app ogni 50 ms, a parole intere e ripulito ([AiAssistRedaction]). Si puo' chiamare da qualunque filo.
  - `fun status(line: String, app: String? = null)` — La riga semplice («Controllo i voti»), con l'app di cui si usano i tool.
  - `fun text(delta: String)` — Un pezzo di testo della risposta, da aggiungere a quello di prima.
  - `fun receipt(receipt: AiAssistReceipt)` — La ricevuta di un'azione.
- `fun interface AiAssistExecutor` — L'esecutore che PampAI fornisce: fa la domanda al modello scelto dall'utente, con le chiavi che stanno solo li', e scrive nel [AiAssistSink]. Finisce normalmente (risposta completa), lancia [AiAssistException] per un errore con il suo codice, o viene cancellato (l'app ha annullato, e' morta, o e' scaduto il tempo): deve smettere di lavorare e di spendere.
  - `suspend fun execute(call: AiAssistCall, sink: AiAssistSink)`
- `class AiAssistException(val error: AiAssistError) : Exception(error.code)` — Un errore con il suo codice, dall'esecutore (un 429 diventa [AiAssistError.LimitReached]).
- `data class AiAssistHistoryEntry(val requestId: String, val app: String, val appName: String, val atMillis: Long, val kind: String, val style: String?, val question: String, val inputChars: Int, val pageTitle: String?, val answer: String, val receipts: List<AiAssistReceipt>, val outcome: String)` — Una domanda di un'app come la ricorda PampAI nella chat «Dalle app» (F9.1b e F9.1c, scelte di Alessio del 26/11/2026). Si salva **tutto quello che serve a rileggere**: l'app, quando, il compito con lo stile, la domanda **intera** (per riscrivere e riassumere, **il testo di partenza intero**), il titolo della pagina, la risposta **intera**, le ricevute e l'esito.
  - `companion object`
    - `const val OutcomeDone`
- `fun interface AiAssistHistory` — Dove l'ospite consegna le domande finite per la chat «Dalle app». PampAI la scrive nel suo database; l'ospite la chiama una volta per domanda, a domanda finita (anche fermata o fallita), mai per una domanda rifiutata prima di partire. Non deve bloccare: si chiama dal filo che chiude la domanda.
  - `fun record(entry: AiAssistHistoryEntry)`
- `class InMemoryAiAssistHistory(private val capacity: Int = Int.MAX_VALUE) : AiAssistHistory` — La cronologia in memoria, per le prove e per un PampAI finto: dura quanto il processo. Come quella vera non toglie niente da sola; [capacity] e' solo un tetto di memoria per chi lo vuole (di serie nessuno).
  - `fun remove(requestId: String): Boolean` — Toglie una domanda (la scelta dell'utente, una per una). Vero se c'era.
  - `fun entries(): List<AiAssistHistoryEntry>` — Le domande, la piu' nuova per prima (una copia).
  - `fun forget(app: String? = null)` — Toglie le domande di un'app, o tutte.

## `engine-ai-bridge/ai/bridge/assist/AiAssistPolicy.kt`

- `class AiAssistPolicy(val apps: Map<String, Entry> = DEFAULT, val devBuild: Boolean = false)` — La tabella delle app che possono chiedere a PampAI, con cosa possono chiedere (l'**ambito**). Sta nel codice, rivista in git, come quella dell'Account Pampa (`AppPolicy` di `engine-account-host`). Il pacchetto di chi chiama lo dice il sistema (l'uid di Binder), mai l'app. Le varianti di sviluppo (`.debug`, `.dev`) contano solo se l'ospite stesso e' una build di sviluppo ([devBuild]).
  - `data class Entry(val packageName: String, val name: String, val scopes: Set<String>)` — Un'app della tabella: il pacchetto, il nome per le domande all'utente, gli ambiti `Scope*`.
  - `fun entryFor(packageName: String): Entry?` — La voce di [packageName], con le varianti di sviluppo solo se [devBuild].
  - `fun allows(packageName: String, scope: String): Boolean` — [packageName] puo' usare [scope]?
  - `companion object`
    - `const val ScopeAnswer` — Una domanda libera.
    - `const val ScopeRewrite` — Riscrivere un testo dell'utente.
    - `const val ScopeSummarize` — Riassumere un testo.
    - `const val ScopeTools` — PampAI usa i tool dell'app che chiama.
    - `const val ScopePage` — L'app puo' mandare la pagina (serve anche il sì dell'utente).
    - `val AllScopes: Set<String>` — Tutti gli ambiti.
    - `fun scopeFor(kind: String): String?` — L'ambito che serve per un compito; null se il compito non e' conosciuto.
    - `val DEV_SUFFIXES`
    - `val DEFAULT: Map<String, Entry>` — La tabella di partenza (stessi pacchetti dell'Account Pampa).

## `engine-ai-bridge/ai/bridge/assist/AiAssistRedaction.kt`

- `object AiAssistRedaction` — La pulizia del testo che esce da PampAI verso l'app che chiama: cio' che somiglia a una chiave diventa [Hidden]. E' difesa in profondita': il formato non ha campi per chiavi o modelli e gli errori passano come codici, ma un modello o un tool potrebbero scrivere una chiave nel testo (un errore copiato, un file di configurazione letto).
  - `const val Hidden`
  - `fun scrub(text: String): String` — [text] senza niente che somigli a una chiave.

## `engine-ai-bridge/ai/bridge/assist/AiAssistTexts.kt`

- `object AiAssistTexts` — Le parole degli errori dell'assistenza, in italiano e in inglese. Sono dell'engine e non di PampAI: l'ospite manda solo il codice ([AiAssistError.code]), cosi' nessun testo di un fornitore (che potrebbe portare una chiave) arriva all'app. Un'app che vuole parole sue le scrive a partire dal codice.
  - `fun message(error: AiAssistError, language: String = "it"): String` — Le parole per [error] nella lingua [language] (`it` di serie; `en` per l'inglese, ogni altra lingua cade sull'italiano).
  - `fun action(id: String, language: String = "it"): String` — L'etichetta di un'azione del tasto spento ([AiAssistEntry.action]).
  - `fun installHint(language: String = "it"): String` — La spiegazione breve quando PampAI non c'e' e nemmeno Pampa Store (F9.1b): `AiAssistClient.installIntent()` e' null.
