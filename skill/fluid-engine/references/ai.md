# L'assistente IA con `engine-ai`

`engine-ai` e' il trasporto e l'orchestrazione di un assistente con strumenti, **senza dominio**:
provider a chiave dell'utente (Groq, Gemini, OpenRouter), chiavi cifrate col Keystore, stream SSE,
failover, catalogo dei modelli su tre livelli, router dei gruppi, orchestratore generico, voce.
La guida completa e' `docs/07-ai.md` nel repo dell'engine; qui c'e' quello che serve mentre si
scrive codice in un'app.

## La divisione del lavoro

| nell'engine (`dev.antigravity.fluidengine.ai`) | nell'app |
|---|---|
| `AiHttp`, `SseParser`, `AiError`, `AiErrorMapper` | — |
| `ChatProvider` + Groq / Gemini / OpenRouter, `ProviderFactory`, `ReadyProvider` | il referer e il titolo per OpenRouter |
| `Message`, `ContentPart`, `ChatRequest`, `ToolSpec`, `ModelTier`, `ModelCapabilities` | — |
| `AiKeyStore`, `AiSettingsStore`, `AiKeyVerifier`, `ModelCatalogStore` | la UI delle chiavi e dei picker |
| `AiToolGroup`, `AiTool<C>`, `ToolOutput`, `ToolRegistry<C>`, `Schema`, `Args`, `ToolText` | i gruppi (enum), il contesto `C`, i tool |
| `AiRouter`, `AiOrchestrator<C>`, `AiOrchestratorConfig`, `FailoverPolicy`, `HistoryCompactor` | il pre-router locale (facoltativo), il prompt, i chip |
| `AssistantState`, `AiConfirmationGate`, `AiDiagnosticsLog` | la UI che li osserva, le azioni vere |
| `SpeechCapture`, `Transcriber` | il TTS di sistema, il permesso microfono |

Regola 4 dell'engine, applicata: **un tool che sa cos'e' un voto non entra nell'engine.**

## Scrivere un tool

```kotlin
enum class RegistroGroup(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  VOTI("voti", "grades", "voti, medie, obiettivi per materia"),
  APP("app", "app", "azioni nell'app: aprire pagine, cambiare impostazioni"),
}

class VotiMediaTool : AiTool<AssistantToolContext> {
  override val name = "voti_media"
  override val group = RegistroGroup.VOTI
  override val description = "La media dei voti, per materia o in tutto, in un periodo"
  override val parameters = Schema.obj(mapOf("materia" to Schema.str("nome della materia, vuoto per tutte")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput = ToolText.output {
    val subject = args.str("materia")
    line("materia", subject ?: "tutte")
    line("media", ctx.gradeMath.average(subject))
  }
}
```

- Nomi e parametri **nella lingua dei tool** (italiano nelle app Pampa), brevi: ogni parola dello
  schema costa token a ogni giro.
- Il risultato e' testo `chiave: valore`, mai JSON verboso, entro `ToolText.MAX_CHARS`; il tool
  taglia le sue liste prima, `ToolText.limit` e' l'ultimo argine.
- Un allegato si restituisce come `ToolOutput(text, parts = listOf(ContentPart.Document(...)))`:
  l'orchestratore lo passa al modello se lo regge, o lo fa tradurre in testo dall'app
  (`AskInput.attachmentFallback`).
- Le scritture chiamano `gate.ask(titolo, dettaglio)` e riportano l'esito **come testo del tool**
  ("fatto", "l'utente ha annullato", "nessuna conferma"): il prompt dice al modello di non chiedere
  conferma a parole e di non fermarsi ad aspettare.
- `Args.str/int/bool/list` leggono gli argomenti in modo tollerante: il modello scrive numeri come
  stringhe e viceversa.
- Un tool che porta roba da ragionarci sopra puo' chiedere il modello piu' capace per il resto della
  domanda: `ToolOutput(text, escalate = true)`. E' la stessa strada del tool built-in
  `modello_avanzato`, che l'orchestratore offre da se' al modello finche' si lavora col livello
  della chat e il provider ne ha davvero uno migliore. Indietro non si torna, e il lavoro gia' fatto
  resta: cambia solo chi risponde dal giro dopo.

## Il giro, e dove l'app lo orienta

`AskInput` porta i ganci senza dominio:

- `preselectedGroups`: i gruppi decisi da un pre-router locale (una tabella di parole). Se non e'
  null, lo stadio 1 non parte e si risparmia una chiamata.
- `routerHint`: i gruppi probabili, suggeriti allo stadio 1 senza vincolarlo.
- `deepRequested`: l'app sa gia' che servira' il livello profondo.
- `chipFilter`: quali `[[id]]` e `[[id:valore]]` l'app riconosce; gli altri spariscono dal testo.
- `attachmentFallback`: come tradurre in testo una parte che il modello non regge.
- `forceFinalPrompt`: la frase dell'ultimo giro (default `AiPrompts.forceFinal(lingua)`).

`AiOrchestratorConfig` e' il posto dei numeri: giri, tool in parallelo, timeout, budget totale e
riserva finale, soglia di escalation. Una card a schermo vive con 90 secondi; un foreground service
con quattro minuti.

## Dalla 1.26.0

- `AskInput.attachments`: le parti (screenshot, foto, PDF) messe dall'utente nella domanda. Il
  livello di partenza si adegua (profondo se solo lui le vede), il resto passa da
  `attachmentFallback`. L'ultimo scambio le ricorda per il "e a destra?".
- Catalogo gerarchico: `AiToolGroup.category` (`AiToolCategory`), `parent`, `loadsWithCategory`;
  `AiRouter(..., categories = ...)`. Lo stadio 1 sceglie una categoria (o "nessuna") e le sue
  sottocategorie; il modello apre il resto con `apri_categoria`/`apri_sottocategoria`; cio' che e'
  aperto resta in `Conversation.loadedGroups` (salvarne gli id insieme alla conversazione e
  ridarli con `registry.group(id)`). Tetto `AiOrchestratorConfig.maxLoadedTools`, aperture
  `maxOpens`. Senza categorie tutto e' come prima.
- `ChatRequest.webSearch` → `ChatTurn.citations`: Google Search su Gemini, plugin `web` su
  OpenRouter, `groq/compound-mini` su Groq (senza tool: farla in una chiamata a parte).
- `AiOrchestrator(usageSink = AiUsageSink { … })`: un `AiUsageEvent` per ogni chiamata, router e
  fallimenti compresi, per il tracker dei consumi dell'app.
- `AiTool.needsConfirmation/longRunning/isAction/describe`: metadati per chi esegue il tool da
  fuori dell'app; dentro l'app non cambiano niente.

## Le trappole

- **L'alias del Keystore.** `KeystoreCipher()` usa `fluidengine.ai`. Un'app che aveva gia' le
  chiavi sotto un altro alias (FluidWeather: `fluidweather.ai`) lo passa al costruttore, o le
  chiavi salvate non si decifrano piu' e l'utente le vede "da reinserire".
- **Le parti grezze appartengono al modello.** Cambiando modello (livello o provider) le
  `raw` dei messaggi si buttano: l'orchestratore lo fa da solo, un'app che rimonta i messaggi a
  mano deve farlo anche lei.
- **Un `ToolResult` orfano e' un 400.** `HistoryCompactor` tiene sempre insieme l'`Assistant` con le
  tool call e i suoi risultati; se si costruisce la storia a mano, stessa regola.
- **`Conversation` e' in memoria.** La cronologia su disco e' dell'app; bastano gli `Exchange`.
- **Groq non legge documenti.** `ProviderCapabilities` lo sa; un PDF su Groq passa dal
  `attachmentFallback`, o non passa.
- **`RECORD_AUDIO` arriva col modulo.** Chi include `engine-ai` lo ha nel manifest fuso: il
  permesso a runtime si chiede solo dall'onboarding o dalle impostazioni, mai a sorpresa.

## Dalla 1.27.0: i tool federati (`engine-ai-bridge`)

Un'app espone i suoi tool a un assistente esterno con la stessa firma (PampAI/Aria) e li esegue nel
proprio processo. Modulo a parte, `engine-install.ps1 -Modules engine-ai-bridge` (porta con se'
`engine-ai`). La ricetta intera e' in `docs/08-ai-bridge.md`; il riassunto:

- **Ospite**: un `<provider>` con `android:authorities="${applicationId}.ai.tools"`, il permesso
  `dev.antigravity.fluidengine.permission.AI_TOOLS` (`signature`), `grantUriPermissions="true"` e il
  meta-data `dev.antigravity.fluidengine.ai.TOOL_HOST=1`; una sottoclasse di
  `AiToolHostProvider<C>` che da' `registry()`, `context(call)` **con il gate pre-confermato**,
  `domain()` (il prefisso: `cv`, `meteo`, `bus`), `routerHint()`, `vocabulary()`, `ready()`,
  `partsAuthority()` (il FileProvider per screenshot e PDF, cartella `cacheDir/ai-bridge/`).
- **Cliente**: `AiToolClient(context).discover()` → `catalog(host)` → `RemoteToolSet.of(client,
  catalog, host)`: gruppi `RemoteGroup` e tool `RemoteTool<C>` con il prefisso, da fondere nel
  proprio `ToolRegistry`. `RemoteToolHost<C>` e' la parte del cliente: la conferma con il suo
  cancello, la lingua, le azioni spente, il timeout dei lavori lunghi.
- **Conferme**: le chiede il cliente prima (`describe` dell'ospite), poi chiama con
  `confirmed = true`; l'ospite rifiuta comunque un tool con `needsConfirmation` senza `confirmed`.
- **Lavori lunghi** (`longRunning`): `run` torna `{jobId}`, il cliente segue con `status` ogni
  secondo; l'ospite li tiene in `BridgeJobs`.
- **Trappole**: la firma (una build di debug di Studio e' `NO_PERMISSION`); `call` occupa un thread
  Binder fino a `callTimeoutMillis`; il processo ospite puo' morire (client *unstable*, errore
  pulito); il cliente deve vedere i provider (`QUERY_ALL_PACKAGES` o `<queries>`).

## Dalla 1.29.0: il servizio fissato, la lista da evitare

- `AskInput.pinProvider = true`: **nessun cambio di provider, mai**. 429 -> attesa del `retry-after`
  (due volte, entro il budget) poi `RATE_LIMITED` con `retryAfterSec`; 5xx/rete -> una riprova poi
  il fallimento; lo stadio 1 che fallisce non fa fallire la domanda (si prosegue con `routerHint` e i
  gruppi di prima). Un'app lo lega a un interruttore "riserva automatica" e lo forza a vero quando
  l'utente ha scelto un servizio per quella domanda. `FailoverPolicy.decide(..., pinned = true)` e'
  la stessa tabella.
- Quando il provider **cambia** e il livello era profondo, la riserva **riparte dalla chat**: le
  escalation si rivalutano li'. Resta profondo solo con `deepRequested` o con allegati che sulla
  riserva legge solo lui. Prima un 429 sul profondo di Gemini finiva sul profondo di OpenRouter,
  qualunque cosa avesse scelto l'utente.
- `AiDefaults.OPENROUTER_AVOID` / `AiDefaults.avoided(id)`: i modelli che non si propongono mai e
  che una scelta salvata non tiene in vita (Inkling: risponde solo in un harness agentico). Vale in
  `TierDefaults.pickDeep`, `OpenRouterCatalog.*` e in `AiKeyVerifier`, che riallinea le scelte al
  catalogo anche al rinfresco quotidiano (`reconcile`, pubblico). Un'app non deve fare niente: i
  telefoni si riparano da soli.
- `AiRequestLog.modelsUsed: List<ModelUse>`: i modelli che hanno risposto, in ordine, con provider
  e livello. `models` resta com'era.
- `OpenAiCompatProvider.optionalFields` toglie anche `temperature`, `top_p`, `tool_choice`,
  `max_completion_tokens` dopo un 400.
