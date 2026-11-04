<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/ai-bridge.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-ai-bridge

I **tool federati**: un'app espone i **propri** tool (quelli che il suo assistente interno gia' usa) a un assistente esterno firmato con la stessa chiave, che li scopre a runtime, li fonde nel suo catalogo e li esegue **nel processo dell'app ospite**,
dove stanno i dati e la sessione. E' una chiamata Binder fra due app dello stesso firmatario, non un servizio in rete. Pacchetto `dev.antigravity.fluidengine.ai.bridge`, quattro file (`BridgeProtocol.kt`, `AiToolHostProvider.kt`, `AiToolClient.kt`, `RemoteTools.kt`).
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
| `run` | `{name, args, confirmed, requestId, lang}` | `{text, escalate, parts:[{kind, uri, mime, name}]}` **oppure** `{jobId}` |
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

**Versione.** Il modulo e' dalla 1.27.0 (metadati dei tool dalla 1.26.0). **3.0.0:** `AI_TOOLS` rimosso, cancello di firma in `isCallerTrusted`/`compareSignatures`, `BridgeProtocol.PERMISSION` deprecata, avviso in `attachInfo`.

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
- **`RemoteTool.run`** nell'ordine: (1) un tool `isAction` con le azioni spente risponde `host.actionsOffText` **senza chiamare l'ospite** (un `ToolOutput` normale, non un errore); (2) se `needsConfirmation`, chiede le parole all'ospite (`describe`; se mancano usa «<App>: <nome con spazi>?»), poi `host.confirm(...)`:
  un risultato non nullo **interrompe** e torna al modello (rifiutato, scaduto); (3) chiama `run` con `confirmed = info.needsConfirmation` (quindi `true` solo per i tool che lo chiedono); (4) mappa il risultato: `Done` → output con le parti lette (`readPart`), `Error` → `ToolOutput.error`, `Job` → `follow`.
- **`follow`**: chiede `status` **ogni secondo**; `done` → output; `failed`/`unknown` → errore; oltre `longRunningTimeoutMillis` (**180 s**) chiama `cancel` e risponde «il lavoro in <App> non e' finito in tempo: continua nell'app».
- **Parti**: lette con `contentResolver.openInputStream` sull'URI con grant temporaneo e rese `ContentPart.Image` / `ContentPart.Document`; una parte illeggibile viene saltata.
- Gli errori del cliente sono messaggi in italiano («l'app rifiuta la chiamata (firma diversa: e' una build di sviluppo?)», «l'app non espone piu' i tool (disinstallata o da aggiornare)», «l'app non risponde: aprila e riprova (...)», «risposta illeggibile dall'app»).

**Trappole.**
- **Visibilita' dei pacchetti (Android 11+).** Senza un `<queries>` (con le autorita' note) o `QUERY_ALL_PACKAGES`, `discover()` restituisce una lista vuota e `availability` dice **`NOT_INSTALLED` anche per un ospite installato** (`getPackageInfo` fallisce). Per scoprire ospiti **nuovi** senza ricompilare serve `QUERY_ALL_PACKAGES`
  (con la sua policy di pubblicazione); con le autorita' note basta `<queries><provider android:authorities="..."/></queries>`.
- **`NO_PERMISSION` non e' un permesso da chiedere**: e' una firma diversa. Il cliente non ha niente da dichiarare (dalla 3.0.0); un `<uses-permission>` in piu' per `AI_TOOLS` e' innocuo e serve solo a parlare con ospiti della 2.x non ancora aggiornati.
- **La conferma e' tua.** Se il tuo `RemoteToolHost.confirm` restituisce `null` senza aver chiesto davvero all'utente, l'ospite eseguira' un'azione con `confirmed = true`: il cancello che l'ospite non puo' sostituire e' questo.
- `RemoteTool.output` calcola `escalate || (parti non vuote && testo < 200 caratteri && escalate)`: l'espressione e' sempre uguale a `escalate` (la terza condizione implica la prima), quindi le parti **non** alzano mai l'escalation da sole.

**Non usarlo quando.** Chiami un tuo servizio in rete (e' un altro canale: `engine-ai` ha i provider); o hai bisogno di piu' di una chiamata sincrona per volta senza `longRunning`.

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
- `RemoteToolTest` (7): la conferma la chiede il chiamante e passa all'ospite; una conferma rifiutata non raggiunge l'ospite; azioni spente fermano la chiamata prima che esca dall'app; un lavoro lungo si segue fino alla fine; un lavoro fallito e' un errore; le parti tornano come contenuto per il modello; un errore dell'ospite resta un errore.
- `ManifestTest` (4): il manifest del modulo **non dichiara ne' chiede permessi** (un `<permission>` farebbe fallire l'installazione di due app con firme diverse); la costante `PERMISSION` esiste ma e' deprecata.
- `AiToolHostProviderGuardTest` (5, **nel sample** con Robolectric, perche' un'app che include il modulo non deve scaricare Robolectric): una chiamata dello stesso processo passa; un chiamante con un'altra firma riceve `SecurityException` **su ogni metodo**; il cancello sceglie per uid; la regola di serie si fida solo di `SIGNATURE_MATCH`; senza contesto non si fida di nessuno.
  Il `PackageManager` di Robolectric risponde sempre «firme uguali», quindi il confronto vero e' isolato in `compareSignatures`, `protected open`, per poter provare la regola.
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
