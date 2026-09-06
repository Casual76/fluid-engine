# I tool federati: `engine-ai-bridge`

Un'app Pampa ha i suoi tool (`AiTool<C>` in un `ToolRegistry`) per il proprio assistente. Con
`engine-ai-bridge` li **espone** a un assistente esterno con la stessa firma — PampAI/Aria — che li
scopre a runtime, li fonde nel suo catalogo e li esegue **nel processo dell'app ospite**, dove ci
sono i dati e la sessione. Nessun dato viaggia altrove: e' una chiamata Binder fra due app firmate
con la stessa chiave.

Package `dev.antigravity.fluidengine.ai.bridge`, dipende da `engine-ai`, senza Compose. Il manifest
del modulo dichiara il permesso `dev.antigravity.fluidengine.permission.AI_TOOLS`
(`protectionLevel="signature"`) e lo chiede: chi include il modulo lo ha nel manifest fuso.

## Le due parti

| ruolo | classe | chi la usa |
|---|---|---|
| **ospite** | `AiToolHostProvider<C>` — un `ContentProvider` astratto | ogni app che espone i tool (ClasseViva, Weather, Transit, Store, Convert) |
| **cliente** | `AiToolClient`, `RemoteToolSet`, `RemoteTool<C>`, `RemoteGroup`, `RemoteCategory` | l'assistente che li chiama (PampAI) |
| **protocollo** | `BridgeProtocol` (JSON in `Bundle`, puro Kotlin, con test) | entrambi |

## L'ospite, in quattro passi

1. **Il provider** nel manifest:

```xml
<provider
    android:name=".pampai.RegistroToolHostProvider"
    android:authorities="${applicationId}.ai.tools"
    android:exported="true"
    android:permission="dev.antigravity.fluidengine.permission.AI_TOOLS"
    android:grantUriPermissions="true">
  <meta-data android:name="dev.antigravity.fluidengine.ai.TOOL_HOST" android:value="1" />
</provider>
```

2. **La sottoclasse**: il registry (lo stesso dell'assistente interno), il contesto dei tool per
   una chiamata, il dominio (il prefisso dei nomi nel cliente: `cv`, `meteo`, `bus`), e le parole
   di se stessa.

```kotlin
class RegistroToolHostProvider : AiToolHostProvider<AssistantToolContext>() {
  override fun registry() = graph().registry
  override fun domain() = "cv"
  override fun routerHint() = "il registro elettronico: voti, compiti, orario, bacheca, assenze"
  override fun vocabulary() = graph().subjects.map { it.name }
  override fun ready() = if (graph().session.loggedIn) ReadyState(true) else ReadyState(false, "l'utente non e' loggato: apri ClasseViva")
  override fun partsAuthority() = "${context!!.packageName}.fileprovider"
  override suspend fun context(call: RemoteCall) = graph().contextFactory.create(actions = true, gate = PreConfirmedGate)
}
```

   Il **gate del contesto e' pre-confermato**: la conferma la chiede il cliente, prima di chiamare.
   Il provider, per difesa in profondita', rifiuta un tool con `needsConfirmation` se la chiamata
   non porta `confirmed = true`.

3. **I metadati dei tool** (engine 1.26.0): `needsConfirmation`, `describe(args, ctx)` (le parole
   della conferma, con i dati veri: "Segnare come letta 'Gita a Roma'?"), `longRunning` per cio' che
   dura piu' di una chiamata (un'installazione, una conversione), `isAction`.

4. **Le parti** (screenshot, PDF) escono come file in `cacheDir/ai-bridge/<requestId>/` tramite il
   `FileProvider` dell'app (`partsAuthority()`), con il grant di lettura al chiamante. Senza
   `partsAuthority()` restano solo i testi.

I tool con `longRunning` partono in `BridgeJobs` e rispondono subito con un `jobId`: il cliente
chiede `status` ogni secondo (`running` / `done` / `failed`). Un tool che vuole raccontare a che
punto e' chiama `BridgeJobs.report(jobId, detail, progress)`.

## Il cliente

```kotlin
val client = AiToolClient(context)
val hosts = client.discover()                                  // le app con il meta-data
val catalog = client.catalog(host) ?: return                    // null: non risponde o protocollo piu' nuovo
val set = RemoteToolSet.of(client, catalog, host = myRemoteHost) // gruppi e tool con il prefisso `cv_`
registry = ToolRegistry(localTools + set.tools, localGroups + set.groups)
```

`RemoteToolHost<C>` e' cio' che il cliente mette del suo: come chiedere la conferma con il proprio
cancello (e le proprie azioni fidate), in che lingua, se le azioni sono spente, quanto aspettare un
lavoro lungo. `RemoteTool.run` chiede la conferma **prima** (con `describe` dell'ospite) e poi
chiama con `confirmed = true`; le parti tornano come `ContentPart` nel `ToolOutput`.

`availability(host)` distingue `INSTALLED_OK`, `NEEDS_UPDATE` (installata ma senza provider),
`NOT_INSTALLED`, `NO_PERMISSION` (firma diversa: una build di debug di Android Studio accanto a
quella dello store), `NOT_READY` (l'app dice di no: non loggata, senza dati).

Il cliente ha bisogno di vedere i provider delle altre app: `QUERY_ALL_PACKAGES`, o un `<queries>`
con le autorita' note.

## Il protocollo

`ContentProvider.call(method, null, Bundle("json" -> stringa))` → `Bundle("json" -> stringa)`.

| metodo | richiesta | risposta |
|---|---|---|
| `catalog` | — | `{protocol, appId, domain, appLabel, appVersion, hint, vocabulary, categories, groups, tools}` |
| `ready` | — | `{ready, reason}` |
| `describe` | `{name, args, lang}` | `{title, detail}` |
| `run` | `{name, args, confirmed, requestId, lang}` | `{text, escalate, parts:[{kind, uri, mime, name}]}` oppure `{jobId}` |
| `status` | `{jobId}` | `{state: running/done/failed/unknown, detail, progress, text, escalate, parts}` |
| `cancel` | `{jobId}` | `{state: failed}` |

Ogni errore e' `{error: "..."}` e diventa un `errore:` per il modello. `BridgeProtocol.PROTOCOL` sale
solo aggiungendo campi: un cliente che riceve un protocollo piu' nuovo del suo ignora l'app.

## Le trappole

- **La firma.** `signature` vuol dire la stessa chiave: l'assistente e le app dallo store si', una
  build di debug di Android Studio no (`NO_PERMISSION`). PampAI firma anche il debug con la chiave
  dello store per questo.
- **`call` blocca un thread Binder** finche' il tool non risponde: `callTimeoutMillis` (25 s) e'
  il tetto; oltre, il cliente riceve un errore e l'ospite continua a lavorare per niente. Cio' che
  puo' durare va in `longRunning`.
- **Il processo dell'ospite puo' morire** a meta' (`DeadObjectException`): il cliente usa un
  `ContentProviderClient` *unstable*, quindi non muore con lui; il tool risponde "l'app non
  risponde: aprila e riprova".
- **Le parti sono file temporanei**: la cartella `ai-bridge/` va dichiarata nei `file_paths.xml`
  del FileProvider (`<cache-path name="ai-bridge" path="ai-bridge/"/>`), e l'ospite puo' svuotarla
  quando vuole.
