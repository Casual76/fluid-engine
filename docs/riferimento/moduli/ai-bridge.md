<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/ai-bridge.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-ai-bridge

I **tool federati**: un'app espone i **propri** tool (quelli che il suo assistente interno gia' usa) a un assistente esterno firmato con la stessa chiave, che li scopre a runtime, li fonde nel suo catalogo e li esegue **nel processo dell'app ospite**,
dove stanno i dati e la sessione. E' una chiamata Binder fra due app dello stesso firmatario, non un servizio in rete. Pacchetto `dev.antigravity.fluidengine.ai.bridge`, quattro file (`BridgeProtocol.kt`, `AiToolHostProvider.kt`, `AiToolClient.kt`, `RemoteTools.kt`). Dalla 4.0 (F9.1) anche il **percorso inverso**, un'app che chiede a PampAI: il pacchetto `...ai.bridge.assist` (sezione «L'assistenza»).
**Senza Compose, senza permessi, senza `Context` d'interfaccia**: dipende da `engine-ai` (i tipi dei tool) e basta.

Due ruoli, un protocollo:

| ruolo | chi e' | classi |
|---|---|---|
| **ospite** | l'app che espone i tool | `AiToolHostProvider<C>` (un `ContentProvider` astratto), `RemoteCall`, `ReadyState`, `BridgeJobs` |
| **cliente** | l'assistente che li chiama | `AiToolClient`, `RemoteHost`, `RemoteToolSet`, `RemoteTool<C>`, `RemoteToolHost<C>`, `BridgeCalls` |
| **protocollo** | entrambi | `BridgeProtocol` (JSON in stringhe dentro i `Bundle` di `ContentProvider.call`; Kotlin puro, con test) |

Modello mentale: **il cliente decide e conferma, l'ospite esegue e si difende**. I tool dell'ospite sono gli stessi `AiTool<C>` e lo stesso `ToolRegistry<C>` dell'assistente interno ([`../ai.md`](../ai.md), catalogo dei tipi in
[`../api/ai-orchestrator.md`](../api/ai-orchestrator.md)); questo modulo aggiunge soltanto la porta. Catalogo delle firme del modulo: [`../api/ai-bridge.md`](../api/ai-bridge.md). Ricetta lunga: `docs/08-ai-bridge.md`.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| far usare i tool della **mia** app da un assistente esterno | una sottoclasse di `AiToolHostProvider<C>` + il `<provider>` nel manifest |
| essere l'**assistente** che usa i tool di altre app | `AiToolClient(context)` → `discover()` → `catalog(host)` → `RemoteToolSet.of(...)` |
| trovare le app che espongono tool | `AiToolClient.discover()` (cerca il meta-data `dev.antigravity.fluidengine.ai.TOOL_HOST` sui provider) |
| sapere se un ospite e' utilizzabile (installato, firma giusta, pronto) | `AiToolClient.availability(host)` → `RemoteAvailability` |
| dire all'assistente «non sono pronta» (non loggato, senza dati) | `AiToolHostProvider.ready()` → `ReadyState(false, "motivo")` |
| un tool che chiede conferma all'utente | `needsConfirmation = true` sul tool + `describe(args, ctx)` per le parole; la conferma la chiede il **cliente** |
| un tool che dura piu' di una chiamata | `longRunning = true` sul tool: parte in `BridgeJobs`, il cliente lo segue con `status` |
| consegnare uno screenshot/un PDF | `ToolOutput(parts = ...)` + `partsAuthority()` + un `FileProvider` |
| restringere chi puo' chiamare a pochi pacchetti | sovrascrivere `isCallerTrusted` **chiamando `super`**; mai allargarlo |
| provare un tool remoto senza un telefono | un `BridgeCalls` finto (e' un'interfaccia: i test del modulo lo fanno) |
| far **chiedere a PampAI** dalla mia app (rispondere, riscrivere, riassumere, coi miei tool) | `AiAssistClient(context).ask(AiAssistRequest(...))` (sezione «L'assistenza») |
| mandare a PampAI la pagina che l'utente ha davanti | `AiAssistPage` nella richiesta, il chip `FluidComposerPagePill`, il sì con `consentIntent()` |
| essere **PampAI** per le altre app | una sottoclasse di `AiAssistHostProvider` con un `AiAssistHost` e il tuo `AiAssistExecutor` |
| un permesso `AI_TOOLS` | non esiste piu' (dalla 3.0.0): vedi sicurezza |

## Sicurezza e fiducia (la regola trasversale)

1. **Chi puo' chiamare: solo chi ha la stessa firma.** `AiToolHostProvider.call` — l'unica porta del provider — controlla, **prima di fare qualunque altra cosa e su ogni metodo** (anche uno sconosciuto), `isCallerTrusted(Binder.getCallingUid())`:
   `checkSignatures(callingUid, myUid) == SIGNATURE_MATCH`. Altrimenti lancia una `SecurityException` che **arriva al cliente cosi' com'e'** (non diventa un `{error}` che sembrerebbe un ospite malato) e che il cliente legge come `RemoteAvailability.NO_PERMISSION`.
   Una chiamata dentro lo stesso processo (nessuna transazione) ha il nostro uid e passa. Solo `SIGNATURE_MATCH` fa fidare: un pacchetto sconosciuto no.
2. **Non c'e' un permesso, dalla 3.0.0 (BREAKING).** Fino alla 2.x il modulo dichiarava il permesso `signature` `AI_TOOLS` in ogni app, e due app con **firme diverse** non si installavano insieme (`INSTALL_FAILED_DUPLICATE_PERMISSION`: un permesso e' di chi lo dichiara per primo).
   Un permesso per app non risolve (l'assistente scopre gli ospiti **a runtime** e non puo' dichiarare in anticipo un `uses-permission` per nomi che non conosce); una sola app che lo dichiara per tutte lega ogni ospite all'ordine di installazione.
   La firma si legge quando serve. Di conseguenza il `<provider>` e' `exported="true"` **senza `android:permission`**, e il cliente non chiede **nessun** permesso.
3. **Se il `<provider>` porta ancora `android:permission="...AI_TOOLS"` (la ricetta della 2.x) nessun cliente puo' piu' chiamarlo**: il permesso non e' dichiarato da nessuno, il sistema nega a tutti, e l'ospite sembra «non pronto». Il provider lo segnala con un `Log.w` (tag `AiToolHost`) in `attachInfo`: toglilo dal manifest.
   `BridgeProtocol.PERMISSION` resta come costante `@Deprecated`, solo per chi la nomina nel codice.
4. **Allargare non conviene mai.** `isCallerTrusted` e' `open` per restringere (un elenco di pacchetti, chiamando `super`); `ContentProvider.call` e' l'unica porta e questo l'unico cancello.
5. **La conferma e' del cliente, la difesa e' dell'ospite.** Un tool con `needsConfirmation` gira sull'ospite **solo** se la richiesta `run` porta `confirmed = true`, qualunque cosa dica il cliente («conferma richiesta: il chiamante deve chiedere all'utente prima di eseguire ...»).
   Il contesto `C` che l'app costruisce in `context(call)` deve avere il **cancello gia' confermato** (risponde si' senza chiedere): la domanda all'utente l'ha gia' fatta il cliente, e chiederla due volte da un processo senza schermata non porterebbe da nessuna parte.
6. **Cio' che un tool restituisce esce dal processo dell'ospite**: e' testo «per il modello» e il cliente lo passa al proprio. Cosa esporre e' una scelta dell'ospite: tool che leggono dati sensibili o che scrivono (`isAction`, `needsConfirmation`) vanno pensati come cio' che un assistente potra' fare con la tua app.
7. **Il debug non ha la chiave dello store.** Una build di debug di Android Studio accanto a quella dello store ha un'altra firma: `NO_PERMISSION`. (PampAI firma anche il debug con la chiave dello store proprio per questo.)

## `BridgeProtocol` (il formato)

**Cos'e'.** Tutto cio' che attraversa il confine fra le due app, in un posto solo: `ContentProvider.call(method, null, Bundle("json" -> stringa))` → `Bundle("json" -> stringa)`. Chi cambia il formato alza `PROTOCOL` e **aggiunge, mai toglie**: un cliente che riceve un protocollo
piu' nuovo del suo **ignora** l'app (`AiToolClient.catalog` restituisce `null` se `catalog.protocol > BridgeProtocol.PROTOCOL`).

| metodo (`BridgeProtocol.METHOD_*`) | richiesta | risposta |
|---|---|---|
| `catalog` | — | `{protocol, appId, domain, appLabel, appVersion, hint, vocabulary, categories, groups, tools}` |
| `ready` | — | `{ready, reason}` |
| `describe` | `{name, args, lang}` | `{title, detail}` (vuoto se il tool non ha parole sue) |
| `run` | `{name, args, confirmed, requestId, lang}` | `{text, escalate, parts:[{kind, uri, mime, name}]}` **oppure** `{jobId}`; per un tool `action`/`needsConfirmation` senza `requestId` l'ospite risponde `{error: "operation identity required for writes"}` (3.0.6) |
| `status` | `{jobId}` | `{state: running/done/failed/unknown, detail, progress, text, escalate, parts}` |
| `cancel` | `{jobId}` | `{state: failed, detail: annullato}` |

Ogni errore e' `{error: "..."}` e diventa un errore per il modello. Costanti: `PROTOCOL = 1`, `KEY_JSON = "json"`, `META_TOOL_HOST`, `STATE_RUNNING`/`STATE_DONE`/`STATE_FAILED`/`STATE_UNKNOWN`, `RemotePart.KIND_IMAGE`/`KIND_DOCUMENT`.

**Comportamento.** Le funzioni `*Request`/`*Response` costruiscono i JSON e `catalogOf`/`resultOf` li rileggono in modo **tollerante** (`ignoreUnknownKeys`, `isLenient`; un campo mancante prende un default, `parse` di un testo non JSON restituisce `null`).
`catalogOf` senza il campo `protocol` restituisce `null`. `resultOf` riconosce le forme in quest'ordine: `error` → `RemoteResult.Error`, `jobId` → `RemoteResult.Job`, `state` → `RemoteResult.Status`, `text` → `RemoteResult.Done`; altro → `null`. Il catalogo porta per ogni tool `name`, `group`, `description`,
`parameters` (lo schema JSON), `needsConfirmation`, `longRunning`, `action`; per ogni gruppo `id`, `statusKey`, `hint`, `category`, `parent`, `default` (`loadsWithCategory`), `action`.

**Trappole.** `BridgeProtocol.KEY_PENDING_INTENT` e `AUTHORITY_SUFFIX` sono dichiarate ma **nessun codice le usa** (restano per compatibilita'/uso futuro: non cercare un `PendingIntent` nelle risposte). Un tool senza `parameters` object diventa `{"type":"object"}` lato cliente.

## L'ospite: `AiToolHostProvider<C>`

**Cos'e'.** Un `ContentProvider` astratto «solo `call`» (`query`/`insert`/`delete`/`update`/`getType` sono `final` e inerti) che espone un `ToolRegistry<C>`. Fa il lavoro noioso e difficile: protocollo, lavori lunghi, parti consegnate come file, timeout, il controllo di chi chiama. La sottoclasse da' soltanto
il registry, il contesto dei tool e le parole di se stessa.

**Firma essenziale.**

```kotlin
abstract class AiToolHostProvider<C> : ContentProvider() {
  abstract fun registry(): ToolRegistry<C>                      // lo stesso dell'assistente interno
  abstract suspend fun context(call: RemoteCall): C             // contesto dei tool per questa chiamata, gate GIA' confermato
  abstract fun domain(): String                                 // prefisso dei nomi nel cliente: "agenda" → agenda_<tool>
  open fun appLabel(): String                                   // di serie l'etichetta dell'app
  open fun routerHint(): String?                                // una riga per il router del cliente: cosa sa fare l'app
  open fun vocabulary(): List<String>                           // parole da riconoscere a voce (nomi di luoghi, voci salvate)
  open fun ready(): ReadyState                                  // di serie ReadyState(true)
  open val callTimeoutMillis: Long                              // 25_000
  open fun partsAuthority(): String?                            // il FileProvider per le parti; null = le parti si scartano
  open fun isCallerTrusted(callingUid: Int): Boolean            // di serie: stessa firma
}
data class RemoteCall(val callingPackage: String?, val language: String, val confirmed: Boolean, val requestId: String)
data class ReadyState(val ready: Boolean, val reason: String? = null)
```

**Uso.**

```kotlin
class AgendaToolHostProvider : AiToolHostProvider<AgendaToolContext>() {      // AgendaToolContext: il contesto dei tuoi tool (segnaposto)
  override fun registry() = graph().registry
  override fun domain() = "agenda"
  override fun routerHint() = "l'agenda dell'utente: eventi, promemoria, scadenze"
  override fun ready() = if (graph().session.isOpen) ReadyState(true) else ReadyState(false, "l'agenda e' chiusa: apri l'app")
  override fun partsAuthority() = "${context!!.packageName}.fileprovider"
  override suspend fun context(call: RemoteCall) = graph().contextFactory.create(gate = PreConfirmedGate)
}
```

```xml
<provider android:name=".bridge.AgendaToolHostProvider"
    android:authorities="${applicationId}.ai.tools"
    android:exported="true">                     <!-- niente android:permission -->
  <meta-data android:name="dev.antigravity.fluidengine.ai.TOOL_HOST" android:value="1" />
</provider>

<!-- Solo se consegni file: il FileProvider di partsAuthority(), con il grant. -->
<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider"
    android:exported="false" android:grantUriPermissions="true">
  <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
<!-- res/xml/file_paths.xml:  <cache-path name="ai-bridge" path="ai-bridge/" /> -->
```

**Comportamento — cosa fa `call`.**
- **Cancello**, poi dispatch su `method`; qualunque eccezione dentro il dispatch (non quella del cancello) diventa `{error: "<appLabel> non ha risposto: <messaggio>"}`; un metodo sconosciuto e' `{error: "metodo sconosciuto: ..."}`.
- **`call` blocca un thread Binder.** `describe` e `run` usano `runBlocking(Dispatchers.IO)` con `withTimeoutOrNull(callTimeoutMillis)`: **25 s** di serie, poi il cliente riceve `{error: "<tool> non ha risposto entro N secondi"}` (non «in corso»: non esiste uno stato intermedio per i tool non lunghi). Per un'attesa piu' lunga si **sovrascrive `callTimeoutMillis`**
  oppure — meglio — si marca il tool `longRunning`. Il timeout cancella la coroutine, ma un tool che non e' cooperativo (I/O bloccante) continuera' a lavorare «per niente».
- **`run`**: tool sconosciuto → errore; `needsConfirmation && !confirmed` → errore (difesa in profondita'); **`ready()` non pronto → errore con il motivo** (o «`<app>` non e' pronta: apri l'app e riprova»); poi `longRunning` → `jobs.start { ... }` e risposta immediata `{jobId}`; altrimenti esecuzione con timeout e `resultResponse`.
  Un'eccezione dentro il tool diventa `ToolOutput.error("<tool> ha fallito: <messaggio>")`: il modello riceve un errore leggibile, non un crash.
- **`describe`** costruisce il contesto con `confirmed = false` e chiede al tool le parole della conferma (`tool.describe(args, ctx)`); se il tool non ne ha o scade, la risposta e' vuota e il cliente ne inventa di generiche. **Non controlla `ready()`.**
- **`status`**: lavoro sconosciuto → `state: unknown` («lavoro sconosciuto (l'app e' stata riavviata?)»); fallito → `failed`; finito → `done` con testo e parti (progress `1.0`); altrimenti `running` con `detail`/`progress`. **`cancel`** risponde sempre `failed`/«annullato», anche per un jobId che non esiste.
- **`catalog`** include `appVersion` letta dal `PackageManager` (la `versionName` dell'ospite) e ricostruisce il JSON dal `registry()` a ogni chiamata.
- **Parti** (`ToolOutput.parts`): ogni immagine/documento diventa un file in `cacheDir/ai-bridge/<requestId>/` (nome ripulito dai caratteri fuori da `[A-Za-z0-9._-]`), un URI del `FileProvider` di `partsAuthority()`, con **grant di lettura al `callingPackage`**.
  Senza `partsAuthority()` restano solo i testi; un file che non si scrive o un URI che non si ottiene viene **saltato in silenzio**.
- **Lavori lunghi** (`BridgeJobs`): partono in uno scope proprio (`SupervisorJob` su `Dispatchers.IO`), vivono quanto il **processo** dell'ospite, e un lavoro finito resta consultabile **10 minuti**, poi si dimentica (pulizia al `start` successivo). Il cliente chiede `status` ogni secondo.

**Trappole.**
- **Un'app che ospita non ha un'`Activity` viva**: `context(call)` gira in background; non dare per scontati schermo, `Activity` o un utente che risponda. Il gate pre-confermato e `ready()` servono a questo.
- **`ready()` e' sincrono** e viene chiamato a ogni `run`: tienilo economico (leggi un campo, non fare I/O).
- **`BridgeJobs.report(jobId, detail, progress)` non e' raggiungibile da un tool**: il `jobs` del provider e' `private`, il jobId e' generato da `BridgeJobs.start` e `AiTool.run(args, ctx)` non lo riceve, e nessun codice del modulo chiama `report`. Gli `detail`/`progress` di un lavoro `running` restano quindi sempre vuoti
  (la descrizione in `docs/08-ai-bridge.md` che un tool «puo' raccontare a che punto e'» non e' realizzabile con le API di oggi).
- **Il `FileProvider` deve avere `android:grantUriPermissions="true"`** e la cartella `ai-bridge/` nei suoi `file_paths.xml` (`<cache-path name="ai-bridge" path="ai-bridge/"/>`). Il grant lo fa `Context.grantUriPermission` sull'URI del **FileProvider**, non sul provider dei tool (la ricetta in `docs/08` mette l'attributo sul provider dei tool).
  L'ospite puo' svuotare la cache quando vuole: le parti sono file temporanei.
- **Il processo dell'ospite puo' morire a meta'** (`DeadObjectException`): il cliente non muore con lui (client *unstable*) e il tool risponde «l'app non risponde: aprila e riprova»; i lavori in `BridgeJobs` muoiono con il processo e il cliente li vedra' `unknown`.
- La lingua (`lang`) e' una stringa dal cliente (`"it"` se manca): le parole di `describe` e dei testi sono dell'ospite.

**Non usarlo quando.** L'app non ha un assistente interno con tool (non c'e' niente da esporre), o i tool dipendono da una `Activity`/da un'interazione sullo schermo.

**Versione.** **4.0.0 (F9.1):** l'assistenza, protocollo 3, nel pacchetto `assist`; i tool non cambiano. Il modulo e' dalla 1.27.0 (metadati dei tool dalla 1.26.0). **3.0.0:** `AI_TOOLS` rimosso, cancello di firma in `isCallerTrusted`/`compareSignatures`, `BridgeProtocol.PERMISSION` deprecata, avviso in `attachInfo`. **3.0.6:** identita' dell'operazione obbligatoria per le scritture (`requestId`), `negotiate` (protocollo 2 con risultati tipizzati; il catalogo resta `protocol = 1`), argomenti validati, l'orchestratore che passa l'identita'.

## Il cliente: `AiToolClient`, `RemoteToolSet`, `RemoteTool`, `RemoteToolHost`

**Cos'e'.** `AiToolClient` trova gli ospiti e li chiama; `RemoteToolSet.of(...)` traduce il catalogo di un ospite in **gruppi e tool del registry del cliente** (`RemoteGroup`, `RemoteTool<C>`), che si fondono nel proprio `ToolRegistry`.
Ogni chiamata passa da un `ContentProviderClient` **unstable**: un ospite che muore a meta' non uccide il cliente (si riceve un'eccezione e si risponde con un errore).

**Firma essenziale.**

```kotlin
class AiToolClient(private val context: Context) : BridgeCalls {
  fun discover(): List<RemoteHost>
  fun availability(host: RemoteHost): RemoteAvailability       // INSTALLED_OK, NEEDS_UPDATE, NOT_INSTALLED, NO_PERMISSION, NOT_READY
  suspend fun catalog(host: RemoteHost): RemoteCatalog?        // null: non risponde, rifiutato, o protocollo piu' nuovo
  // describe/run/status/cancel/readPart: l'implementazione di BridgeCalls
}
data class RemoteHost(val packageName: String, val authority: String, val label: String)

interface RemoteToolHost<C> {                                   // la parte del cliente
  suspend fun confirm(ctx: C, toolName: String, text: ConfirmationText): ToolOutput?   // null = procedi; altrimenti il testo da dare al modello
  fun language(ctx: C): String = "it"
  fun actionsEnabled(ctx: C): Boolean = true
  val actionsOffText: String
  val longRunningTimeoutMillis: Long                            // 180_000
}

class RemoteToolSet<C>(val catalog: RemoteCatalog, val categories: List<RemoteCategory>, val groups: List<RemoteGroup>, val tools: List<RemoteTool<C>>) {
  companion object { fun <C> of(client: BridgeCalls, catalog: RemoteCatalog, host: RemoteToolHost<C>): RemoteToolSet<C> }
}
```

**Uso.**

```kotlin
val client = AiToolClient(context)
val tools = client.discover().mapNotNull { host ->
  val catalog = client.catalog(host) ?: return@mapNotNull null           // non risponde / firma diversa / protocollo piu' nuovo
  RemoteToolSet.of(client, catalog, miaRemoteToolHost)                   // miaRemoteToolHost: la tua RemoteToolHost<C> (segnaposto)
}
val registry = ToolRegistry(localTools + tools.flatMap { it.tools }, localGroups + tools.flatMap { it.groups })
```

**Comportamento.**
- **`discover()`** elenca i provider con il meta-data `TOOL_HOST` valorizzato a `1` (uno per autorita'); se `queryContentProviders` fallisce restituisce lista vuota. **`availability`** controlla prima che il pacchetto sia installato (`NOT_INSTALLED`), poi chiama `ready`:
  `SecurityException` → `NO_PERMISSION`; provider assente → `NEEDS_UPDATE` (app installata ma senza provider: una versione vecchia); altro errore → `NOT_READY`; `ready: false` → `NOT_READY`.
- **Nomi e gerarchia.** `RemoteToolSet.of` prefissa con il dominio: tool `agenda_<nome>`, gruppo `agenda_<id>` (con `remoteId` = l'id originale); le **categorie** dell'ospite restano quelle (non prefissate); un gruppo senza categoria, o con una che non c'e', va sotto la prima, e un ospite senza categorie ne riceve una costruita dal dominio
  (cosi' nel router ogni app e' una porta). La descrizione di ogni tool diventa «descrizione (Etichetta app)». I gruppi padre si risolvono dopo averli creati tutti; un tool il cui gruppo non c'e' viene scartato.
- **Identita' dell'operazione (3.0.6).** Il `requestId` di una chiamata e' l'`operationId` della `ToolExecution` che riceve `RemoteTool.run(args, ctx, execution)`: lo stesso durante la conferma e in ogni ripetizione con la stessa identita', e l'ospite lo passa al suo tool (`ToolExecution(requestId)`). L'orchestratore la da' a ogni chiamata (`AskInput.prepareToolExecution`, [ai-orchestrator.md](ai-orchestrator.md)).
  L'overload **senza** identita', `run(args, ctx)`, va bene per una lettura (ne inventa una), ma per una scrittura (`action` o `needsConfirmation`) risponde `errore: operation identity required for writes` **prima** della conferma e della chiamata: e' voluto, perche' un'identita' inventata li' non riconoscerebbe mai una ripetizione.
  L'identita' da sola non deduplica: nessun ospite dell'engine ricorda le identita' gia' viste, e un lavoro di `BridgeJobs` muore col processo. Riconoscere una ripetizione e' compito di chi conserva l'identita' (il registro dell'assistente) e, se vuole, dell'ospite.
- **`RemoteTool.run`** nell'ordine: (0) argomenti validati sullo schema (`ToolArgumentValidator`) e, nell'overload senza identita', il rifiuto delle scritture; (1) un tool `isAction` con le azioni spente risponde `host.actionsOffText` **senza chiamare l'ospite** (un `ToolOutput` normale, non un errore); (2) se `needsConfirmation`, chiede le parole all'ospite (`describe`; se mancano usa «<App>: <nome con spazi>?»), poi `host.confirm(...)`:
  un risultato non nullo **interrompe** e torna al modello (rifiutato, scaduto); (3) chiama `run` con `confirmed = info.needsConfirmation` (quindi `true` solo per i tool che lo chiedono); (4) mappa il risultato: `Done` → output con le parti lette (`readPart`), `Error` → `ToolOutput.error`, `Job` → `follow`.
- **`follow`**: chiede `status` **ogni secondo**; `done` → output; `failed`/`unknown` → errore; oltre `longRunningTimeoutMillis` (**180 s**) chiama `cancel` e risponde «il lavoro in <App> non e' finito in tempo: continua nell'app».
- **Parti**: lette con `contentResolver.openInputStream` sull'URI con grant temporaneo e rese `ContentPart.Image` / `ContentPart.Document`; una parte illeggibile viene saltata.
- Gli errori del cliente sono messaggi in italiano («l'app rifiuta la chiamata (firma diversa: e' una build di sviluppo?)», «l'app non espone piu' i tool (disinstallata o da aggiornare)», «l'app non risponde: aprila e riprova (...)», «risposta illeggibile dall'app»).

**Trappole.**
- **Chi chiama un tool remoto fuori dall'orchestratore** (un test, un'azione rapida dell'app) usa `run(args, ctx, ToolExecution(id))` con un id suo per le scritture: con `run(args, ctx)` una scrittura e' rifiutata (nella prima candidata 3.0.6 ci cadeva anche l'orchestratore, e le azioni remote chieste dall'assistente non partivano).
- **Visibilita' dei pacchetti (Android 11+).** Senza un `<queries>` (con le autorita' note) o `QUERY_ALL_PACKAGES`, `discover()` restituisce una lista vuota e `availability` dice **`NOT_INSTALLED` anche per un ospite installato** (`getPackageInfo` fallisce). Per scoprire ospiti **nuovi** senza ricompilare serve `QUERY_ALL_PACKAGES`
  (con la sua policy di pubblicazione); con le autorita' note basta `<queries><provider android:authorities="..."/></queries>`.
- **`NO_PERMISSION` non e' un permesso da chiedere**: e' una firma diversa. Il cliente non ha niente da dichiarare (dalla 3.0.0); un `<uses-permission>` in piu' per `AI_TOOLS` e' innocuo e serve solo a parlare con ospiti della 2.x non ancora aggiornati.
- **La conferma e' tua.** Se il tuo `RemoteToolHost.confirm` restituisce `null` senza aver chiesto davvero all'utente, l'ospite eseguira' un'azione con `confirmed = true`: il cancello che l'ospite non puo' sostituire e' questo.
- `RemoteTool.output` calcola `escalate || (parti non vuote && testo < 200 caratteri && escalate)`: l'espressione e' sempre uguale a `escalate` (la terza condizione implica la prima), quindi le parti **non** alzano mai l'escalation da sole.

**Non usarlo quando.** Chiami un tuo servizio in rete (e' un altro canale: `engine-ai` ha i provider); o hai bisogno di piu' di una chiamata sincrona per volta senza `longRunning`.

## L'assistenza: un'app chiede a PampAI (F9.1, protocollo 3)

**Cos'e'.** Il percorso inverso dei tool: un'app Pampa (ClasseViva, Fluid Transit, Fluid Weather, Pampa Notes, Pampa Mail, MyCloud Gallery, Universal Converter) manda una domanda a **PampAI** e riceve la risposta, anche a pezzi, con le righe semplici di cosa fa PampAI e le ricevute delle azioni. Le chiavi e i modelli **restano in PampAI**: l'app non configura niente e non riceve mai una chiave, un fornitore o un modello. Pacchetto `dev.antigravity.fluidengine.ai.bridge.assist`. Il contratto per intero, con le scelte e i numeri, e' `artifacts/liquid-glass/sessioni/f9-1/contratto.md` (fuori da git).

| ruolo | chi e' | classi |
|---|---|---|
| **app** | chi chiede | `AiAssistClient`, `AiAssistClientConfig`, `AiAssistRequest`, `AiAssistTask`, `AiAssistPage`, `AiAssistProgress`, `AiAssistError`, `AiAssistStatus`, `AiAssistEntry` (il tasto, F9.1b), `AiAssistConsentAnswer`, `AiAssistTexts` |
| **PampAI** | l'ospite dell'assistenza | `AiAssistHostProvider` (il `ContentProvider`), `AiAssistHost` (il cuore, senza Android), `AiAssistExecutor` + `AiAssistSink` (il lavoro, di PampAI), `AiAssistPolicy` (la tabella), `AiAssistConsentStore` + `AiAssistConsent` (il sì per l'app e per la pagina, in un foglio solo), `AiAssistHistory` + `AiAssistHistoryEntry` + `InMemoryAiAssistHistory` (la chat «Dalle app», F9.1b), `AiAssistLimits` |
| **il chip** | nel composer | `FluidComposerPagePill` di `engine-ai-ui` ([ai-ui-chat.md](ai-ui-chat.md)) |

**Uso, lato app.**

```kotlin
val pampai = AiAssistClient(context)                                      // trova PampAI (in sviluppo prima PampAI di sviluppo)
val status = pampai.check()                                               // ready, tasks, page, pageConsent, error
val page = AiAssistPage("Matematica · verifica del 12 novembre", listOf("Voto 5½, peso 100%", "Media 6,8 (prima 7,1)"))
pampai.ask(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = page)).collect { p ->
  riga = p.status?.line; testo = p.text; errore = p.error?.message()      // ogni elemento e' lo stato intero
}
val riassunto = pampai.run(AiAssistRequest(AiAssistTask.summarize(circolare, AiAssistTask.FormKeyPoints)))
// Il sì per la pagina: l'Activity di PampAI, con StartActivityForResult (solo cosi' PampAI sa dal sistema chi chiede).
launcher.launch(pampai.consentIntent()); /* nel risultato: */ AiAssistConsent.resultOf(resultCode, data)
```

**Uso, lato PampAI.**

```kotlin
val host = AiAssistHost(executor = PampaiAssistExecutor(...), consents = SharedPreferencesAiAssistConsentStore(context),
  policy = AiAssistPolicy(devBuild = isDebuggable), ready = { if (keys.any()) ReadyState(true) else ReadyState(false, "aggiungi una chiave") })
class PampaiAssistHost : AiAssistHostProvider() { override fun host() = graph().assistHost }   // lo stesso oggetto per tutto il processo
```

```xml
<provider android:name=".assist.PampaiAssistHost" android:authorities="${applicationId}.ai.assist" android:exported="true">
  <meta-data android:name="dev.antigravity.fluidengine.ai.ASSIST_HOST" android:value="3" />   <!-- niente android:permission -->
</provider>
```

**Chi chiama: l'identita' dal sistema.** `AiAssistHostProvider.call`, l'unica porta, **prima di leggere la richiesta e su ogni metodo**: (1) `checkSignatures(callingUid, myUid) == SIGNATURE_MATCH`, altrimenti `SecurityException` (l'app vede `not_authorized`); (2) `getPackagesForUid(uid)` deve dare **un pacchetto solo** (niente uid condivisi), e il nome scritto dall'app non conta: il formato non ha un campo per chi chiama; (3) la tabella (`AiAssistPolicy`), il blocco dell'utente e il sì per la pagina, in `AiAssistHost`. Le varianti `.debug`/`.dev` contano solo se PampAI e' una build di sviluppo. **Il controllo inverso**: `AiAssistClient` parla solo con il pacchetto atteso, col provider `<pacchetto>.ai.assist` e il meta-data, e **la stessa firma dell'app**; una PampAI firmata da altri «non c'e'». Il canale di ritorno accetta solo l'uid di PampAI (o il proprio processo).

**L'ambito.** `AiAssistPolicy.Scope*`: `answer` (una domanda), `rewrite` (gli stili di Writing Tools: `StyleProofread`, `StyleRewrite`, `StyleFriendly`, `StyleProfessional`, `StyleConcise`), `summarize` (`FormSummary`, `FormKeyPoints`, `FormList`, `FormTable`), `tools` (PampAI usa i tool **dell'app che chiama**: l'ospite dei tool del suo pacchetto, trovato da PampAI, arriva all'esecutore in `AiAssistCall.caller.toolsAuthority`), `page`. La tabella di serie: ClasseViva, Pampa Notes e Pampa Mail hanno tutto; Fluid Transit, Fluid Weather, MyCloud Gallery e Universal Converter `answer`, `tools`, `page`. `AiAssistRequest.tools`: `ToolsCaller` (di serie: i tool dell'app sono la base, le altre app raggiungibili ma non caricate), `ToolsCallerOnly`, `ToolsNone`; riscrivere e riassumere non usano mai tool. Un compito o uno stile sconosciuti: `needs_update`.

**La pagina** (`AiAssistPage`): titolo (120), tipo (40), fino a 8 righe da 160, selezione (2000), **in tutto 2400 caratteri**; nient'altro (niente identificativi o dati nascosti: il formato non li ha); cio' che sfora si taglia e `truncated` lo dice; `preview()` e' il testo che passa e che il chip mostra. Parte **solo dentro una domanda**. **Il sì** lo chiede PampAI la prima volta (`AiAssistConsent.begin(callerOf(activity))` → `Step.Ask.question` → `answer`), mai l'app; prima del sì una domanda con la pagina riceve `consent_required` e **all'esecutore non arriva niente**. Dalle impostazioni di PampAI: `host.setPageConsent(pkg, Denied)` (ferma le domande in corso con la pagina), `host.setBlocked(pkg, true)` (ferma tutte le sue domande), `host.forget(pkg)`.

**Il risultato.** `Flow<AiAssistProgress>`: `text` (a parole intere), `status` (`line` e `app` per l'icona: «Controllo il meteo», F9.4), `receipts` (`line`, `app`, `outcome` `done`/`uncertain`/`failed`), `done`, `error`. Gli errori sono **codici fissi** (`AiAssistError.*`) con le parole dell'engine (`message()`, italiano e inglese): `not_installed` «PampAI non c'è», `needs_update`, `not_authorized`, `consent_required`, `not_ready` (con la riga di PampAI), `limit_reached` (con `retryAfterMillis`: «riprova fra 2 minuti»), `busy`, `too_large`, `cancelled` «Annullata», `timeout`, `interrupted` «PampAI si è fermata», `failed`. Un'eccezione dell'esecutore esce come `failed` **senza il suo messaggio**; l'esecutore da' un codice con `AiAssistException`.

**Comportamento: i tempi e l'annullamento.** `assist_ask` risponde subito; la domanda ha un tempo massimo (di serie 60 s, tenuto fra 1 e 120 s) allo scadere del quale l'ospite chiude con `timeout` **all'ora giusta anche se l'esecutore non si ferma** (e lo cancella). L'app ha la sua guardia: tempo + `graceMillis` (5 s). Cancellare chi raccoglie manda `assist_cancel` (al massimo 2 s di attesa); se l'app muore il canale muore e l'ospite annulla; se PampAI muore il gettone muore e l'app chiude con `interrupted`. Se PampAI tace per 2 s l'app controlla con `assist_poll` (un tool lungo, o un invio perso: anche la fine persa arriva cosi').

**Il trasporto.** `ContentResolver.call` (la porta di sempre, un cancello solo) e un **canale di ritorno**: l'app mette nel `Bundle` di `assist_ask` un suo `Binder`, l'ospite ci manda gli invii come transazioni a senso unico (`linkToDeath` in tutti e due i sensi). L'app tiene aperto il `ContentProviderClient` per tutta la domanda: il processo di PampAI resta alla sua priorita'. Gli invii partono al massimo ogni 50 ms (la fine subito), il testo a parole intere e **ripulito** (`AiAssistRedaction`: `gsk_…`, `AIza…`, `sk-or-…`, `sk-…`, `Bearer …`, JWT, `api_key=…` diventano «[chiave nascosta]»; a parole intere una chiave non si spezza mai fra due invii). Riserva: `AiAssistClientConfig(pollIntervalMillis = …)` fa chiedere i pezzi con `assist_poll`.

**F9.1b: la prima volta, il tasto spento, «Dalle app»** (le scelte di Alessio del 26/11/2026).
- **La prima volta che un'app usa PampAI, PampAI chiede.** Il registro ha due scelte per app (`AiAssistConsentState`: `NotAsked`, `Allowed`, `Denied`): `appConsent` e `pageConsent`. Con `appConsent` da chiedere la domanda riceve `consent_required` con `needs = [app]` (con la pagina `[app, page]`) e all'esecutore non arriva niente; `AiAssistStatus.appConsent` lo dice prima, e il tasto resta acceso. L'app apre `consentIntent(page = conPagina)`: **un foglio solo** (`AiAssistConsent.begin(caller, withPage)` → `Step.Ask(askApp, askPage)` con `question` e `choices`: «Consenti», «Senza la pagina», «Non consentire»; `answer(ask, choice)` → `Step.Decided(app, page)`; `resultIntent(decided)`; l'app legge `AiAssistConsent.resultOf` → `AiAssistConsentAnswer(app, page)`). **Il «no» si ricorda**: `not_authorized`, nessun foglio di nuovo (`begin` → `Decided` col no), si cambia da «App che usano PampAI» con `host.setAppConsent` (`setBlocked` resta come scorciatoia). A un'app rifiutata `assist_scope` risponde solo `{error}`.
- **Il tasto «Chiedi a PampAI» si vede spento, non sparisce**: `AiAssistEntry.of(status)` → `enabled`, `note`, `action`, `actionLabel`. `not_installed` → «Installa PampAI», `needs_update` → «Aggiorna PampAI», `not_ready` → «Apri PampAI» (`openIntent()`), `not_authorized` → **nessuna azione** (non si rivela niente di PampAI). `installIntent()` apre Pampa Store (`com.pampa.store`, in sviluppo prima `.debug`, con la firma dell'app) con `dev.pampa.account.OPEN`, sezione `app`, extra `app = dev.pampa.pampai` (lo Store lo impara col passaggio al 4.0; uno Store vecchio si apre sulla prima pagina); null senza lo Store, e l'app mostra `AiAssistTexts.installHint()`. Il modulo dichiara i `<queries>` dei due Store.
- **La chat «Dalle app»** (F9.1c: le scelte 8 B e 9 B di Alessio): `AiAssistHost(history = …)` riceve un `AiAssistHistoryEntry` per ogni domanda finita (anche fermata o fallita; mai una rifiutata prima di partire): app, nome, ora (`wallClock`), compito e stile, **la domanda intera** (per riscrivere e riassumere **il testo di partenza intero**, e la sua lunghezza), **della pagina solo il titolo**, **la risposta intera**, le ricevute, l'esito. Niente righe o selezione della pagina, niente righe di stato, niente fornitore o modello; tutto passa dalla pulizia delle chiavi. **Le voci restano finche' l'utente non le toglie**, una per una o tutte (`InMemoryAiAssistHistory.remove`, `forget`): l'engine non ne cancella nessuna da solo. Il database e la pagina sono di PampAI.

**I metodi** (`Bundle("json" → testo)`, piu' `callback` e `token` come `IBinder`):

| metodo | richiesta | risposta |
|---|---|---|
| `negotiate` | `{versions:[3], capabilities:["assist","assist_stream"]}` | `{protocol:3, capabilities}`; un'altra versione → `{error}` |
| `assist_scope` | `{protocol:3}` | `{protocol, tasks, tools, page, consent, app, ready, reason?}` o `{error:codice}` (senza `app`, un ospite di F9.1: vale `allowed`) |
| `assist_ask` | `{protocol, task:{kind, text, style?}, page?:{title, kind?, lines, selection?}, tools, lang, timeoutMillis}` + `callback` | `{requestId}` + `token`, o `{error, retryAfterMillis?, reason?, needs?}` |
| `assist_poll` | `{requestId, after}` | `{events, finished}` |
| `assist_cancel` | `{requestId}` | `{cancelled}` |

Gli eventi (sul canale e in `assist_poll`) hanno `seq` (da 1, senza buchi) e `type`: `status {line, app?}`, `text {delta}`, `receipt {line, app?, outcome}`, `done {text}`, `failed {code, retryAfterMillis?, reason?, needs?}`.

**Le misure** (`AiAssistLimits`, «a occhio» col perche' nel KDoc): compito fino a 24 000 caratteri, risposta fino a 32 000 (poi si chiude con quello che c'e'), 2 domande in corso per app e 4 in tutto (`busy`), 30 al minuto per app (`limit_reached`), invii ogni 50 ms, una domanda finita leggibile 60 s.

**Compatibilita'.** La 3 e' solo dell'ospite dell'assistenza, che sta su un provider a parte: **i tool restano alla 1 e alla 2** (`BridgeProtocol` non cambia, `MAX_PROTOCOL = 2`). Un'app che chiamasse per sbaglio un ospite dei tool riceve «nessun protocollo comune» → `needs_update`.

**Trappole.**
- **L'`AiAssistHost` e' uno per processo**: il provider lo chiede a ogni chiamata (`host()`), e le domande in corso vivono li'. Le impostazioni di PampAI passano da lui (non dal registro), se no una revoca non ferma le domande in corso.
- **Le prove e i PampAI finti devono dare il sì all'app** (`setAppConsent(pkg, Allowed)`), se no ogni domanda e' la prima volta e riceve `consent_required`.
- **L'Activity del consenso deve stare nel processo del provider** se il registro e' `SharedPreferencesAiAssistConsentStore` (un file, un processo). Va aperta con `startActivityForResult`: senza, `callerOf` e' null e la pagina non chiede niente (`Step.Refused`).
- **I nomi dei modelli nelle righe di stato li scrive l'esecutore**: la pulizia toglie le chiavi, non i nomi. Le righe sono quelle semplici di F9.4 («Controllo il meteo»).
- **Il modulo dichiara `<queries>` per `dev.pampa.pampai` e `dev.pampa.pampai.debug`** (Android 11+): un'app che cerca un PampAI con un altro pacchetto (la galleria cerca se stessa) deve vederlo da se'.
- `AiAssistSink` non sospende: l'ospite raccoglie; un pezzo dopo la fine si scarta.

## Token, costanti, Defaults

| nome | valore |
|---|---|
| `BridgeProtocol.PROTOCOL` | `1` |
| `BridgeProtocol.META_TOOL_HOST` | `dev.antigravity.fluidengine.ai.TOOL_HOST` (meta-data con valore `1` sul `<provider>` dell'ospite) |
| `BridgeProtocol.KEY_JSON` | `"json"` |
| autorita' dell'ospite | `<applicationId>.ai.tools` (la costante `AUTHORITY_SUFFIX` = `".ai.tools"` non e' usata dal codice) |
| `AiToolHostProvider.callTimeoutMillis` | 25 000 ms (sovrascrivibile) |
| `RemoteToolHost.longRunningTimeoutMillis` | 180 000 ms; sondaggio di `status` ogni 1 000 ms |
| `BridgeJobs` (tempo di conservazione di un lavoro finito) | 10 minuti (privato) |
| cartella delle parti | `cacheDir/ai-bridge/<requestId>/` |
| `BridgeProtocol.PERMISSION` | `@Deprecated`, non usata |

## Cosa e' verificato dai test

- `BridgeProtocolTest` (4): il catalogo fa il giro per JSON (categorie, gruppi, tool, flag di conferma/azione/lungo); `resultOf` legge ogni forma (`Done`, `Job`, `Status`, `Error`, e `null` per `{}` o testo non JSON); un catalogo senza `protocol` e' `null`; `RemoteToolSet` prefissa nomi e mantiene la gerarchia.
- `RemoteToolOrchestrationTest` (6, 3.0.6): una scrittura remota con conferma eseguita **dall'orchestratore** arriva all'ospite una volta, con `confirmed = true` e l'identita' preparata (prima della correzione non partiva); senza gancio dell'app l'identita' c'e' comunque; una conferma rifiutata non raggiunge l'ospite; dopo un errore di rete la ripetizione porta la stessa identita' che l'app ha conservato; chiamate diverse (in parallelo o in giri diversi con lo stesso `call_0`) hanno identita' diverse; `run(args, ctx)` su una scrittura resta rifiutato.
- `RemoteToolTest` (12): la conferma la chiede il chiamante e passa all'ospite; una conferma rifiutata non raggiunge l'ospite; azioni spente fermano la chiamata prima che esca dall'app; un lavoro lungo si segue fino alla fine; un lavoro fallito e' un errore; le parti tornano come contenuto per il modello; un errore dell'ospite resta un errore.
- `ManifestTest` (4): il manifest del modulo **non dichiara ne' chiede permessi** (un `<permission>` farebbe fallire l'installazione di due app con firme diverse); la costante `PERMISSION` esiste ma e' deprecata.
- `AiToolHostProviderGuardTest` (5, **nel sample** con Robolectric, perche' un'app che include il modulo non deve scaricare Robolectric): una chiamata dello stesso processo passa; un chiamante con un'altra firma riceve `SecurityException` **su ogni metodo**; il cancello sceglie per uid; la regola di serie si fida solo di `SIGNATURE_MATCH`; senza contesto non si fida di nessuno.
  Il `PackageManager` di Robolectric risponde sempre «firme uguali», quindi il confronto vero e' isolato in `compareSignatures`, `protected open`, per poter provare la regola.
- **L'assistenza (F9.1).** `AiAssistHostTest` (17, JVM col tempo virtuale): pezzi a parole intere al massimo ogni 50 ms e la fine col testo intero; un'app fuori tabella o bloccata, un compito non concesso (`not_authorized`) o sconosciuto (`needs_update`) senza che l'esecutore parta; la pagina senza il sì (`consent_required`, all'esecutore non arriva niente) e la revoca che ferma la domanda in corso; annullare, il tempo massimo all'ora giusta anche con un esecutore che non si ferma, il canale morto; `busy`, `limit_reached` con l'attesa, `too_large`; `not_ready` ripulito; un'eccezione con una chiave che esce come `failed`; chiavi spezzate fra due pezzi ripulite; il tetto della risposta; `assist_poll` solo a chi ha chiesto. `AiAssistClientTest` (13): PampAI assente o vecchia, i pezzi in ordine, `assist_poll`, l'annullamento che arriva all'esecutore, PampAI che tace (tempo + grazia), che muore (`interrupted`), invii persi recuperati (anche l'ultimo), `check`, un ospite dei tool chiamato per sbaglio, niente chiavi in tutto quello che passa. `AiAssistPageTest` (6): i tetti, i caratteri di controllo, solo i quattro campi sul filo. `AiAssistCostTest` (2): il costo del JSON e la negoziazione dei tool che non cambia. `AiAssistFirstUseTest` (7, F9.1b): la prima volta (`needs` app e app+pagina, niente all'esecutore), il foglio unico coi tre tasti, il «no» ricordato e rimesso, dimenticare che ferma le domande, il tasto spento per ogni stato, «Dalle app» senza le righe della pagina, l'inizio del testo da riscrivere, le fermate salvate e le rifiutate no, `needs` sul filo. Nel sample, con Robolectric: `F9p1AssistBridgeTest` (14: il cancello per firma, uid condiviso e tabella; il ponte vero con la galleria e il suo PampAI finto: pezzi, pagina e sì, blocco, assenza, annullamento, tempi, l'Activity del consenso) e `F9p1AssistUiTest` (3: il chip per TalkBack, la risposta che arriva sfocata, «Riduci movimento»). F9.1b aggiunge il primo uso col foglio vero del PampAI finto, il «no» ricordato, il tasto spento senza PampAI e con l'app bloccata, «Dalle app» (`F9p1AssistBridgeTest`), il tocco che apre il foglio con la pagina e la scena «Senza PampAI» per TalkBack (`F9p1AssistUiTest`).
- **Non verificato:** `AiToolClient` (Binder, `discover`, `availability`), `BridgeJobs`, l'esportazione delle parti, i timeout, `describe` e `ready` dell'ospite. Servono due app sullo stesso dispositivo.

## Dove guardare

- `engine-ai-bridge/src/main/kotlin/dev/antigravity/fluidengine/ai/bridge/AiToolHostProvider.kt` — l'ospite: cancello, `call`, `run`/`describe`/`status`/`cancel`, parti, `BridgeJobs`.
- `.../BridgeProtocol.kt` — formato, costanti, `RemoteCatalog`, `RemotePart`, `RemoteResult`.
- `.../AiToolClient.kt` — scoperta, disponibilita', le chiamate Binder, `RemoteAvailability`, `RemoteHost`.
- `.../RemoteTools.kt` — `RemoteToolHost`, `BridgeCalls`, `RemoteTool` (conferma, azioni, lavori lunghi), `RemoteToolSet`.
- `engine-ai-bridge/src/main/AndroidManifest.xml` — vuoto di proposito, con il perche'.
- `sample/src/testDebug/.../AiToolHostProviderGuardTest.kt` — l'unico esempio eseguibile di una sottoclasse (il sample non espone tool veri).

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api project(':engine-ai')` (che porta `engine-foundation`) e `implementation androidx.core:core-ktx` (per `FileProvider`). Nessun permesso, nessun Compose. Lo script `tools/engine-install.ps1` lo include **di serie** (senza `-Modules` entrano tutti i moduli tranne `engine-wear` e `engine-ai-ui`, che sono opt-in); per includerlo da solo:
`tools/engine-install.ps1 -AppRoot . -Modules engine-ai-bridge` (porta `engine-ai` e `engine-foundation`). Nel `build.gradle`:

```groovy
implementation project(':engine-ai-bridge')   // tool federati: esporre i tool a un assistente esterno, o chiamarli
```

**Configurazione minima — ospite.** (1) una sottoclasse con `registry()`, `domain()`, `context(call)`; (2) il `<provider>` con autorita' `<applicationId>.ai.tools`, `exported="true"`, il meta-data `TOOL_HOST`, **nessun `android:permission`**;
(3) `ready()` se l'app puo' non essere in grado di rispondere; (4) se consegni file, `partsAuthority()` e un `FileProvider` con grant e `<cache-path name="ai-bridge" path="ai-bridge/"/>`; (5) tool con `needsConfirmation`/`describe`/`longRunning` dove serve.
**Configurazione minima — cliente.** (1) un `<queries>` con le autorita' note o `QUERY_ALL_PACKAGES`; (2) un `AiToolClient(context)`; (3) una `RemoteToolHost<C>` con il **tuo** cancello delle conferme; (4) i tool di `RemoteToolSet` nel proprio `ToolRegistry`.
Entrambi i lati devono essere **firmati con la stessa chiave**.
