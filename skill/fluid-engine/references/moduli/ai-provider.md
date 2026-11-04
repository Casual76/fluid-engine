# AI: provider, modelli e livelli

Questa scheda racconta la parte di `engine-ai` che **parla con i servizi**: i tre provider a chiave
dell'utente (Groq, Gemini, OpenRouter), il modello neutro dei messaggi che l'orchestratore vede, i
cataloghi dei modelli e i **tre livelli** (router, chat, profondo) con cui un provider si usa. Sta
sotto l'orchestratore ([ai-orchestrator.md](ai-orchestrator.md)) e sopra la rete ([ai-chiavi-rete-voce.md](ai-chiavi-rete-voce.md)).
Le firme complete stanno in [`../api/ai-provider.md`](../api/ai-provider.md); la guida generale in [`../ai.md`](../ai.md).

Modello mentale in due frasi. Un `ChatProvider` e' un adattatore **stateless** fra il modello neutro
(`Message`, `ContentPart`, `ToolSpec`, `ChatRequest`, `ChatDelta`) e il dialetto di un servizio: costruisce il JSON,
legge la risposta o lo stream, e traduce ogni guasto in un `AiError`. Un `ReadyProvider` e' quel client
piu' i **tre modelli** con cui lo si vuole usare adesso: e' quello che l'app passa a `AskInput.providers`, nell'ordine di preferenza.

```
   chiavi (AiKeyStore) + impostazioni (AiSettingsStore) + cataloghi (ModelCatalogStore)
                              |
                     ProviderFactory.ordered(CHAT)            un ReadyProvider per provider con chiave VERIFICATA,
                              |                               nell'ordine scelto dall'utente
        +---------------------+----------------------+
   GroqProvider           GeminiProvider        OpenRouterProvider     tutti dietro l'interfaccia ChatProvider
   (OpenAiCompat*)        (GeminiCodec)         (OpenAiCompat*)
        |                       |                      |
   AiHttp: POST JSON / POST SSE / multipart / GET   ->   AiError (AiErrorMapper)
```

## Scegliere in fretta

| ho bisogno di... | uso |
|---|---|
| i provider pronti per una domanda, nell'ordine dell'utente | `ProviderFactory.ordered(ProviderFactory.Kind.CHAT)` -> `AskInput.providers` |
| i provider per la voce (stesso meccanismo, altro ordine) | `ProviderFactory.ordered(ProviderFactory.Kind.STT)` -> `Transcriber` (vedi [ai-chiavi-rete-voce.md](ai-chiavi-rete-voce.md)) |
| un client anche senza chiave verificata, per verificarla | `ProviderFactory.forVerification(provider)` (lo usa `AiKeyVerifier`) |
| sapere che modello usa un livello | `ReadyProvider.model(ModelTier.ROUTER / CHAT / DEEP)` |
| sapere se un modello vede immagini / legge PDF | `ReadyProvider.capabilities(model)` (catalogo se c'e', altrimenti `ProviderCapabilities.guess`) |
| una chiamata diretta, senza orchestratore (un riassunto, una classificazione) | `ready.provider.complete(ChatRequest(...))` |
| una risposta a pezzi | `ready.provider.stream(request)` + `ToolCallAssembler` |
| una risposta strutturata (JSON) | `ChatRequest(jsonSchema = ...)` (e' cosi' che lavora il router) |
| cercare sul web | `ChatRequest(webSearch = true)` -> `ChatTurn.citations`; su Groq **senza tool**, in una chiamata a parte |
| l'elenco dei modelli per un picker | `ChatProvider.listModels()` -> `ModelCatalogue`; per OpenRouter `OpenRouterCatalog.recommended` |
| un involucro per iniettare guasti in debug | `ProviderFactory(decorate = { Faulty(it) })` |
| un client di prova nei test | implementare `ChatProvider` a mano (il copione dei test dell'orchestratore e' il modello da copiare) |
| una scelta di modello che il catalogo non riesce a fare | **non** nel codice dell'app: `AiKeyVerifier.reconcile` (vedi [ai-chiavi-rete-voce.md](ai-chiavi-rete-voce.md)) |

Non cosi': non costruire `GroqProvider`/`GeminiProvider`/`OpenRouterProvider` a mano in un'app. La fabbrica rilegge la chiave
**a ogni domanda** (una chiave cambiata nelle impostazioni vale alla domanda dopo, senza riavvii) e applica ordine, modelli e
politica dati scelti dall'utente. Un client costruito a mano salta tutto questo.

## I tre livelli (`ModelTier`)

**Cos'e'.** Un provider non e' un modello: e' tre, scelti per il compito. `ROUTER` e' il modello piccolo e veloce che sceglie i gruppi
di strumenti (stadio 1); `CHAT` risponde e chiama i tool; `DEEP` entra quando servono documenti, immagini o un contesto grande.
Perche': i limiti dei free tier sono **per modello** (Groq: 30 richieste e 8K token al minuto per modello), quindi spostare il
router su un modello piccolo libera la chat; e il modello grosso si paga (in tempo, in quota) solo dove serve.

**Come si sceglie il modello di un livello.**

| livello | impostazione | default | se manca |
|---|---|---|---|
| `ROUTER` | `AiSettings.classifierModel(provider)` | Groq `AiDefaults.GROQ_CLASSIFIER`, Gemini `AiDefaults.GEMINI_CLASSIFIER` (poi riallineati al catalogo), OpenRouter il primo di `AiDefaults.OPENROUTER_CLASSIFIER_PREFERRED` presente nel catalogo (altrimenti nessuno) | `ReadyProvider.classifierModel == null` -> **la chat fa anche da router** |
| `CHAT` | `AiSettings.chatModel(provider)` | Groq `AiDefaults.GROQ_CHAT`, Gemini `AiDefaults.GEMINI_CHAT`, OpenRouter scelto dall'euristica alla verifica | OpenRouter senza scelta: `AiDefaults.OPENROUTER_CHAT_FALLBACK` (a pagamento); Groq/Gemini non mancano mai |
| `DEEP` | `AiSettings.deepModel(provider)` | **nessun default scritto**: lo sceglie `TierDefaults.pickDeep` dal catalogo vivo alla verifica della chiave | `ReadyProvider.deepModel == null` -> **la chat fa anche da profondo** |

`ReadyProvider.model(tier)` implementa esattamente queste due regole di ripiego (`ROUTER -> classifierModel ?: chatModel`,
`DEEP -> deepModel ?: chatModel`). Conseguenza che l'orchestratore sfrutta: `model(DEEP) == model(CHAT)` significa «niente da guadagnare», e
`modello_avanzato` non si offre al modello.

**Uso.**

```kotlin
val ready = factory.ordered(ProviderFactory.Kind.CHAT).first()
val chat = ready.model(ModelTier.CHAT)
val deep = ready.model(ModelTier.DEEP)          // == chat se l'utente non ha un profondo diverso
val sees = ready.capabilities(deep).vision      // il catalogo se c'e', il nome altrimenti
```

**Dove scegliere i default** (`TierDefaults.pickDeep`, il profondo). Prima le scelte di prodotto quando il catalogo le ha: su
Gemini l'ultimo `pro` (`AiDefaults.latestGemini(ids, "pro")`: l'alias `gemini-pro-latest` se esiste, altrimenti la versione piu'
alta); su OpenRouter i gratuiti di `AiDefaults.OPENROUTER_DEEP_PREFERRED` in ordine; su Groq nessuna. Altrimenti punteggio
(`TierDefaults.score`): documenti +4, visione +3, ragionamento +2, contesto >= 128k +2 (>= 64k +1, >= 500k un altro +1), «pro» non
«preview» +2, nomi da piccolo (`lite`, `mini`, `nano`, `8b`, `instant`) -3, `preview`/`exp` -1; su OpenRouter gratuito +3, oltre 5 dollari/M in
ingresso -2. **Se il migliore non batte la chat il profondo e' la chat** (un livello in piu' che non aggiunge niente e' solo un modello in piu'
da spiegare). I modelli di `AiDefaults.OPENROUTER_AVOID` non sono mai candidati.

**Trappole.**
- Il profondo **cambia chi risponde dal giro dopo**, con la stessa storia: i pezzi grezzi (`Message.Assistant.raw`) scritti dall'altro modello si
  buttano. Un'app che rimonta i messaggi a mano deve farlo (l'orchestratore lo fa da solo).
- Con `AiOrchestratorConfig.deepTierEnabled = false` i provider **non si ritoccano**: l'orchestratore li vede senza `deepModel`. Non fare
  `copy(deepModel = null)` per spegnere il profondo: la verifica della chiave continuerebbe a sceglierne uno e salvarlo.
- Il router di Groq (`AiDefaults.GROQ_CLASSIFIER`) e' un default scritto nel codice che il provider puo' ritirare: `AiKeyVerifier.reconcile` lo sostituisce
  quando il catalogo non lo ha piu' (`openai/gpt-oss-20b`, poi la chat). Se un'app non verifica mai le chiavi ne' rinfresca i cataloghi, un 404 del
  router finisce nel ripiego, ma costa una chiamata a ogni domanda.

## `ProviderId` e l'ordine

**Cos'e'.** I tre servizi: `ProviderId.GROQ`, `ProviderId.GEMINI`, `ProviderId.OPENROUTER`. L'ordine di dichiarazione e' l'ordine di default con cui
rispondono (`ProviderId.defaultOrder`): Groq perche' e' il piu' veloce e gratuito, Gemini perche' e' gratuito e largo, OpenRouter ultimo perche' i suoi modelli migliori costano.

**Comportamento.** `ProviderId.fromId(id)` legge l'id salvato (`"groq"`, `"gemini"`, `"openrouter"`); `AiSettingsStore.parseOrder` accoda i provider che un ordine salvato
non nomina, cosi' una versione futura con un quarto servizio non lo perde. **Aggiungere un valore a questo enum rompe ogni `when` esaustivo
dell'app** (e va fatto in coda: i cataloghi su disco e le preferenze usano gli id, non gli ordinali).

## `ProviderFactory` e `ReadyProvider`

**Firma essenziale.**

```kotlin
class ProviderFactory(
  http: AiHttp, keys: AiKeyStore, settings: AiSettingsStore,
  referer: String, appTitle: String,                 // vanno a OpenRouter (HTTP-Referer, X-Title)
  catalogs: ModelCatalogStore? = null,               // per sapere cosa vede/legge ogni modello
  decorate: (ChatProvider) -> ChatProvider = { it }, // involucro per iniettare guasti in debug
) {
  enum class Kind { CHAT, STT }
  suspend fun ordered(kind: Kind): List<ReadyProvider>
  suspend fun build(provider: ProviderId, settings: AiSettings): ReadyProvider?
  suspend fun forVerification(provider: ProviderId): ChatProvider?
}
```

Tutti i parametri: [`../api/ai-provider.md`](../api/ai-provider.md).

**Uso.**

```kotlin
val http = AiHttp(userAgent = "MiaApp/1.0")
val keys = AiKeyStore(context)
val settings = AiSettingsStore(context)
val catalogs = ModelCatalogStore(File(context.cacheDir, "ai"))
val factory = ProviderFactory(http, keys, settings, referer = "https://example.com/miaapp", appTitle = "MiaApp", catalogs = catalogs)

val providers = factory.ordered(ProviderFactory.Kind.CHAT)     // vuoto = nessuna chiave verificata = FailureKind.NO_KEYS
```

**Comportamento.**
- `ordered` legge **a ogni chiamata** impostazioni e stati delle chiavi: ordine di `chatOrder` o `sttOrder`, filtrato su `KeyState.verified` (chiave presente **e** verificata),
  poi `build` per ognuno. Costa letture di DataStore, non rete: si puo' chiamare a ogni domanda.
- `build` torna `null` se la chiave non si legge (manca, o il Keystore non la decifra piu' e `AiKeyStore.key` l'ha tolta).
- `ReadyProvider.catalogue` viene da `ModelCatalogStore.load`: senza catalogo salvato (o senza `catalogs`) le capacita' si indovinano dal nome.
- OpenRouter riceve `settings.openRouterFallbacks` (fino a due modelli di riserva che prova **lui**, lato server) e `settings.openRouterDataPolicy`.
- `decorate` si applica a **ogni** client costruito qui, anche a quello di `forVerification`. L'involucro nasconde il tipo concreto: `AiKeyVerifier` fa
  `client as? OpenRouterProvider`, quindi con un involucro attivo i crediti di OpenRouter non si leggono (`keyInfo` resta vuoto). Va bene per una build di prova.

**Trappole.**
- Un `ordered` vuoto non e' un errore della fabbrica: l'orchestratore lo traduce in `AssistantFailure(FailureKind.NO_KEYS)` alla prima domanda. L'app decide prima se mostrare il tasto dell'assistente (`AiKeyStore.anyVerified`).
- `referer` e `appTitle` sono **obbligatori** anche se l'utente non usera' mai OpenRouter: la fabbrica li passa al client solo se serve.
- L'ordine **non e' un failover cieco**: `AskInput.pinProvider = true` toglie il «prossimo» dal giro (vedi [ai-orchestrator.md](ai-orchestrator.md)).

## `ChatProvider` e il modello neutro

**Cos'e'.** L'interfaccia che i tre adattatori implementano, e il vocabolario con cui l'orchestratore la usa. Esiste perche' i tre servizi parlano due dialetti
(OpenAI-compatibile per Groq e OpenRouter, `contents/parts` per Gemini) e l'orchestratore non deve saperlo.

**Firma essenziale.**

```kotlin
interface ChatProvider {
  val id: ProviderId
  suspend fun complete(request: ChatRequest): ChatTurn            // risposta intera
  fun stream(request: ChatRequest): Flow<ChatDelta>               // a pezzi, flusso freddo
  suspend fun listModels(): ModelCatalogue
  suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript
}

sealed interface Message {                     // System, User(parts), Assistant(text, toolCalls, raw, rawProvider), ToolResult(callId, name, content)
}
sealed interface ContentPart {                 // Text, Image(bytes, mime), Document(bytes, mime, name)
}
data class ChatRequest(
  val model: String, val messages: List<Message>,
  val tools: List<ToolSpec> = emptyList(), val toolChoice: ToolChoice = ToolChoice.Auto,
  val parallelToolCalls: Boolean = true, val reasoning: ReasoningLevel = ReasoningLevel.MEDIUM,
  val jsonSchema: JsonObject? = null, val maxOutputTokens: Int? = null, val temperature: Double? = null,
  val webSearch: Boolean = false, val webSearchMaxResults: Int = 5,
  val readTimeoutMillis: Int? = null,
)
```

**Uso (una chiamata diretta e un flusso).**

```kotlin
val turn = ready.provider.complete(
  ChatRequest(
    model = ready.chatModel,
    messages = listOf(Message.System("Rispondi in una frase."), Message.User("Cos'e' un elemento?")),
    reasoning = ReasoningLevel.NONE, maxOutputTokens = 200, temperature = 0.2,
  ),
)
val text = turn.message.text           // null se il modello ha chiuso senza una parola (vedi Trappole)

val assembler = ToolCallAssembler()
val out = StringBuilder()
ready.provider.stream(request).collect { delta ->
  when (delta) {
    is ChatDelta.Text -> out.append(delta.text)
    is ChatDelta.ToolCallPart -> assembler.add(delta)
    is ChatDelta.Raw -> Unit              // da conservare in Message.Assistant.raw se si continua il giro
    is ChatDelta.Finish -> Unit           // reason, usage, rateLimit, citations
  }
}
val calls = assembler.build()             // List<ToolCall>, con id anche per Gemini
```

**Comportamento.**
- `Message.User(text)` e' un costruttore di comodo per una sola parte di testo. Con parti binarie (`Image`, `Document`) il messaggio diventa una lista: `ContentPart` viaggia in
  **base64 dentro la richiesta** (nessun provider dei tre offre un upload a parte che valga la pena): chi mette una parte ha gia' deciso che e' abbastanza piccola.
- `Message.Assistant.raw` / `rawProvider`: cio' che **quel provider pretende di rivedere identico** al giro dopo — le thought signature di Gemini sulle `functionCall`, i
  `reasoning_details` di OpenRouter. Un adattatore li rimanda solo se `rawProvider` e' il suo; altrimenti li ignora e ricostruisce (Gemini: con `GeminiCodec.SKIP_SIGNATURE`).
- `ReasoningLevel` (`NONE`, `LOW`, `MEDIUM`, `HIGH`) si traduce per provider: Groq `reasoning_effort` (+ `reasoning_format: hidden` se non `NONE`, perche' con i tool Groq accetta solo `parsed` o `hidden`),
  Gemini `thinkingConfig.thinkingLevel` (`NONE` -> `minimal`), OpenRouter `reasoning.effort` con `exclude: true` (il testo del ragionamento non serve, solo i dettagli da rimandare).
- `ChatRequest.readTimeoutMillis`: in stream il **silenzio massimo fra due pezzi**, senza stream l'attesa della risposta intera; null = i default di `AiHttp` (45 s stream, 60 s). Un modello che pensa tace anche piu' di mezzo minuto.
- `Usage` somma con `+` (anche il costo); `costUsd` lo dice solo OpenRouter.
- `Citation(url, title, snippet)`: le fonti di una risposta con `webSearch`, deduplicate per indirizzo.
- `ModelCapabilities.accepts(part)`: testo sempre; immagine se `vision`; documento se `documents`. E' la funzione con cui l'orchestratore decide se una parte entra come tale, passa dal fallback dell'app o diventa una riga.

**Trappole.**
- `ChatTurn.message.text == null` e' una risposta **senza parole**: succede con `FinishReason.LENGTH` (Gemini conta il thinking nel tetto di uscita) e con la chiamata malformata di Gemini (`MALFORMED_FUNCTION_CALL` -> messaggio vuoto, `FinishReason.OTHER`).
  L'orchestratore la riprova e non la tratta mai come successo; chi chiama `complete` direttamente deve fare lo stesso controllo.
- Gli errori escono **come `AiError`** (vedi [ai-chiavi-rete-voce.md](ai-chiavi-rete-voce.md)), anche quelli arrivati dentro un 200 (`error` nel corpo o in un pezzo di stream, `blockReason` di Gemini). Un'eccezione non-`AiError` e' un bug del provider o dell'involucro.
- `stream` e' **freddo** e ogni raccolta e' una richiesta nuova: non raccoglierlo due volte.
- `ToolCall.arguments` puo' essere `{"_raw": "..."}` se il modello ha scritto JSON rotto (vedi `ToolCallAssembler`): il tool deve rispondere con un errore leggibile invece di lanciare.
- `ChatRequest.jsonSchema` su Groq/OpenRouter parte come `response_format: json_schema` **strict**; su Gemini come `responseJsonSchema`. Uno schema che Gemini rifiuta (400 con «schema») riparte una volta con `GeminiSchema.sanitize`.

## `ToolCallAssembler`

**Cos'e'.** Ricompone le tool call di uno stream. Groq e OpenRouter mandano il nome nel primo pezzo e gli argomenti a **frammenti di stringa JSON**; Gemini una `functionCall` intera per parte. Si accumula per `index` e si parsa **una volta, alla fine**.

**Comportamento.**
- Un pezzo senza nome non produce una chiamata (`build` scarta le voci senza nome); un `id` mancante o vuoto riceve `ToolCallAssembler.localCallId()` («`call_`» + 24 caratteri, stile OpenAI, cosi' Groq lo accetta anche se la chiamata e' nata su Gemini).
- Argomenti vuoti -> `{}`; argomenti non-JSON -> `{"_raw": "<testo>"}`. Non lancia mai: un giro non cade per un JSON rotto.
- `thoughtSignature` (Gemini 3) passa dal pezzo alla `ToolCall` e va rimandata verbatim.

**Trappole.** L'`index` e' la chiave: due chiamate parallele con lo stesso indice si fondono (i provider OpenAI-compatibili lo danno sempre; Gemini lo assegna l'adattatore, in ordine di arrivo).

## `OpenAiCompatProvider`, `GroqProvider`, `OpenRouterProvider`

**Cos'e'.** `OpenAiCompatProvider` e' la base astratta che Groq e OpenRouter condividono: costruzione del corpo, **giro di auto-riparazione**, stream col suo stato. Le sottoclassi
aggiungono base URL, header e campi extra (`providerFields`) e, se serve, riscrivono la richiesta (`effectiveRequest`). `GroqProvider` e `OpenRouterProvider` sono `open` (dalla 1.26.0) per poterli sottoclassare.

**Comportamento comune.**
- **Auto-riparazione.** Un 400 il cui messaggio nomina un campo opzionale si ritenta **senza** quel campo, uno alla volta, in ordine di sospetto: `reasoning_effort`, `reasoning_format`, `reasoning`, `response_format`,
  `parallel_tool_calls`, `stream_options`, `temperature`, `top_p`, `tool_choice`, `max_completion_tokens` (gli ultimi quattro dalla 1.29.0: i modelli di solo ragionamento rifiutano la temperatura, qualche gratuito di
  OpenRouter il `tool_choice` esplicito o il nome nuovo del tetto di uscita). **Mai** per `BadReason.TOOL_USE_FAILED` o `BLOCKED` (il messaggio puo' citare per caso un parametro dello schema di un tool, e togliere quel campo non
  aggiusta niente), e in stream **mai dopo il primo pezzo**.
- Il corpo mette `tools` + `tool_choice` + `parallel_tool_calls` solo se ci sono tool; in stream chiede `stream_options.include_usage`.
- I messaggi `Message.ToolResult` partono come ruolo `tool` con `tool_call_id` e `name`; gli argomenti delle chiamate come **stringa** JSON.
- Uno stream con `error` aperto a meta' (Groq) o un choice con `finish_reason: "error"` (OpenRouter) lancia un `AiError` (un `AiError.BadRequest` col suo perche' se si sa leggere); il `tool_use_failed` di Groq arriva proprio cosi'.

**`GroqProvider`.**
- Catalogo in `GET /models`, Whisper in multipart, base `GroqProvider.BASE_URL`. I documenti **non** si leggono mai: `OpenAiCompatCodec.messages(..., fileParts = false)` trasforma un `ContentPart.Document` nella riga «[allegato nome: questo modello non legge i documenti]» (meglio di un 400); le immagini passano come `image_url` data-URL solo con modelli che il nome dichiara di visione.
- **Ricerca web** (`webSearch = true`): la richiesta si riscrive sul sistema compound (`GroqProvider.SEARCH_MODEL`), **senza tool e senza ragionamento**. Il compound non accetta strumenti: la ricerca va fatta in una chiamata a parte, di solito da un tool dell'app.
- `GroqProvider.PROMPT_MAX_CHARS`: il prompt di Whisper vale al massimo 224 token; 800 caratteri.

**`OpenRouterProvider`.**
- Aggiunge `HTTP-Referer` e `X-Title`; `models` con i modelli di riserva (il primario piu' fino a due, senza doppioni); `provider.sort = "price"`; `usage.include = true` (per il costo); il `reasoning` unificato.
- Documenti: parte `file` (`fileParts = true`) + plugin `file-parser` con motore `pdf-text` (gratuito; per le scansioni ci pensa l'app con le immagini). Ricerca web: plugin `web` con `webSearchMaxResults`. I due plugin possono stare insieme.
- `OpenRouterProvider.keyInfo()` -> `OpenRouterKeyInfo` (etichetta, limite e residuo in dollari, uso totale e giornaliero, `isFreeTier`): per la scheda delle impostazioni.
- Le trascrizioni viaggiano in **JSON base64** su `/audio/transcriptions`; il suggerimento di vocabolario (`TranscribeOptions.prompt`) **non viene mandato**.

**`OpenRouterDataPolicy`.** `ACCOUNT` (default): non si dice niente a OpenRouter, vale la politica scelta dall'utente su openrouter.ai. `DENY`: si manda `provider.data_collection = "deny"`.
Perche' non `DENY` di serie: un'app che lo manda a ogni richiesta **sovrascrive l'account con la piu' stretta**, e su un modello i cui endpoint gratuiti registrano i prompt il conto e' zero endpoint — «No endpoints found matching your data policy»,
che `AiErrorMapper` legge come `MODEL_UNAVAILABLE`. Il modello smette di funzionare senza che nessuno abbia cambiato niente (1.30.0).

**Trappole.**
- Un 400 che nomina un campo opzionale **non** e' mai la fine della domanda, ma ogni auto-riparazione e' una richiesta in piu' sul tetto giornaliero (su OpenRouter gratuito e' lo stesso per qualunque modello).
- Sottoclassare `OpenAiCompatProvider` per un quarto servizio compatibile OpenAI funziona (`providerFields`, `extraHeaders`, `supportsFileParts`, `optionalFields`), ma `ProviderId` ha tre valori: l'integrazione in `ProviderFactory` e' dell'engine.
- Su Groq la ricerca web **non** convive con i tool: se la domanda li ha, la ricerca fatta nella stessa richiesta li azzera.

## `GeminiProvider`, `GeminiCodec`, `GeminiSchema`

**Cos'e'.** Gemini via `generateContent` / `streamGenerateContent?alt=sse` (stabile; l'Interactions API e' ancora beta). Autenticazione con l'header `x-goog-api-key`. Il dialetto vive in `GeminiCodec` (puro, testabile).

**Comportamento.**
- **Le parti del modello si conservano grezze** in `Message.Assistant.raw` (`rawProvider = GEMINI`) e si rimandano identiche: Gemini 3 pretende di rivedere le sue **thought signature** sulle `functionCall`, e una parte ricostruita a mano senza firma e' un 400. Per le chiamate nate altrove
  (dopo un cambio di provider) si usa il valore di bypass `GeminiCodec.SKIP_SIGNATURE`. Le parti marcate `thought` non si mostrano e non si conservano.
- I risultati dei tool consecutivi si **fondono in un solo turno `user`** di `functionResponse`; il system prompt va in `systemInstruction`.
- Ricerca web: `google_search` nello stesso elenco `tools` delle funzioni; le fonti vengono dai `groundingChunks` del candidato.
- `GeminiSchema.sanitize`: Gemini accetta un sottoinsieme di JSON Schema e risponde 400 a `additionalProperties`, `$schema`, `default`, `examples`; si tengono solo le chiavi che capisce (`type`, `description`, `enum`, `properties`, `required`, `items`, `nullable`, `format`, `minimum`, `maximum`, `anyOf`, `title`), ricorsivamente. Vale per ogni tool, sempre.
- **Degradazioni su un 400 generico** (solo `BadReason.GENERIC`; un modello sparito il cui nome contiene «thinking», o un contesto troppo lungo, non si aggiustano cosi'): se il messaggio parla di «thinking» prima `thinkingLevel = low`, poi via tutto il `thinkingConfig`; se parla di «schema» il `jsonSchema` passa da `responseJsonSchema` a `responseSchema` ripulito.
- Uso: `promptTokenCount` come ingresso, **uscita = candidati + pensieri** (il thinking si paga e conta nel tetto di uscita).
- `finishReason`: `MAX_TOKENS` -> `LENGTH`; `SAFETY`, `RECITATION`, `PROHIBITED_CONTENT`, `BLOCKLIST`, `SPII` -> `BLOCKED`; un `promptFeedback.blockReason` e' un `AiError.BadRequest` «bloccato: ...» (`BadReason.BLOCKED`).
- Trascrizione: l'audio va in linea (`inlineData`) con un prompt «Transcribe the speech ... verbatim» e, se c'e', `Context: <hint>`; se il modello scelto dà un 400 si riprova con `GeminiProvider.FALLBACK_TRANSCRIBE_MODEL`.
- `listModels` scorre le pagine (200 per pagina, fino a 2000 modelli).

**Trappole.**
- Una `functionCall` **senza** thought signature e senza bypass e' un 400 su Gemini 3: non costruire a mano `Message.Assistant` con `toolCalls` per Gemini senza `raw` o senza `ToolCall.thoughtSignature` (il bypass lo mette il codec, ma e' un valore documentato, non una garanzia eterna).
- Gemini non manda **nessun header di limiti**: `RateLimitInfo` resta `EMPTY` e il `retry-after` si legge da `RetryInfo.retryDelay` nei dettagli dell'errore.
- `GeminiProvider.FALLBACK_TRANSCRIBE_MODEL` e' un nome scritto nel codice: se Google lo ritira, il ripiego smette di funzionare (il default della trascrizione, `AiDefaults.GEMINI_STT`, ha lo stesso rischio e viene dal catalogo solo se l'utente lo cambia).

## `ModelCatalog` e `ModelCatalogue`

**Cos'e'.** Il passaggio dai cataloghi grezzi dei tre provider agli elenchi che l'utente sfoglia: **chat** da una parte, **trascrizione** dall'altra, tutto il resto (embedding, immagini, voci, guardie) fuori. Le regole sono per
**sottostringa** perche' e' l'unica cosa che i cataloghi promettono; i test le fissano su elenchi veri salvati. `ModelCatalogue(chat, stt)` e' il risultato; `ModelInfo` descrive un modello (contesto, tool, ragionamento, visione, documenti, gratuito, prezzi per milione di token).

**Firma essenziale.**

```kotlin
ModelCatalog.groq(body)        // GET /models di Groq     -> ModelCatalogue
ModelCatalog.gemini(models)    // elenco `models` di Gemini
ModelCatalog.openRouter(body)  // GET /models di OpenRouter
ModelCatalog.preferredFirst(models, preferred)   // il preferito in testa, poi contesto decrescente e nome
```

**Comportamento (cosa entra nella chat).**

| provider | fuori dalla chat | note |
|---|---|---|
| Groq | `whisper` -> trascrizione; poi `tts`, `orpheus`, `playai`, `guard`, `safeguard`, `embed`, `compound`, `moderation`; `active: false` | `supportsTools = true` per tutti; ragionamento se il nome ha `qwen`, `gpt-oss`, `deepseek`, `r1`; visione solo da `ProviderCapabilities.guess`; documenti mai |
| Gemini | senza `generateContent`; `embedding`, `imagen`, `image`, `tts`, `veo`, `robotics`, `computer-use`, `gemma` (niente tool), `transcribe`, `live`, `native-audio`, `aqa` | tutti con visione + documenti + audio in linea; i `transcribe` (non `live`) vanno nella lista STT; i **flash non-lite** si aggiungono in coda alla lista STT come «(audio inline)» di ripiego |
| OpenRouter | uscita senza `text`; nomi con `embedding`, `imagen`, `tts`, `veo`, `guard`, `moderation`, `rerank`, `whisper`, `transcribe`, `-stt`; **modelli senza `tools` fra i parametri supportati** | gratuito se finisce in `:free` **o** prezzi a zero; visione dalle `input_modalities`; documenti **sempre** (il parser di OpenRouter legge i PDF per chiunque); la lista chat ha i gratuiti in testa, poi per nome |

**Trappole.**
- Una regola per sottostringa **sbaglia sui nomi nuovi**: un modello di chat con «image» nel nome sparisce dalla lista di Gemini. E' il compromesso voluto; si corregge nelle liste di `ModelCatalog`, non nell'app.
- `ModelCatalogue.chat(id)` e' una ricerca lineare per id esatto: l'id di Gemini non porta il prefisso `models/` (si toglie nel catalogo).
- OpenRouter: il catalogo e' pubblico e grande, e i `:free` cambiano ogni settimana. **Non si scrivono liste di modelli nel codice dell'app**: si usano le euristiche di `OpenRouterCatalog`.

## `ProviderCapabilities`

**Cos'e'.** Cosa un modello sa prendere in ingresso **quando il catalogo non lo dice**: `ProviderCapabilities.of(provider, model, catalogue)` torna `ModelInfo.capabilities` se il modello e' nel catalogo, altrimenti `ProviderCapabilities.guess`.
Gemini e' multimodale per intero; OpenRouter legge i documenti di chiunque col suo parser e vede le immagini dove il nome lo suggerisce; Groq vede solo coi modelli che lo dicono nel nome e non legge documenti mai.

**Comportamento.** Il nome e' di visione se contiene uno di: `vision`, `llama-4`, `scout`, `maverick`, `gemini`, `gpt-4o`, `gpt-5`, `claude`, `pixtral`, `qwen-vl`, `qwen3-vl`, `vl-`.

**Trappole.** Un catalogo vecchio di un modello rinominato fa cadere nell'euristica: un PDF puo' arrivare a un modello che non lo legge (OpenRouter: lo legge il parser, quindi va bene; Groq: `attachmentFallback` o riga «non leggibile»).

## `OpenRouterCatalog`

**Cos'e'.** Le **euristiche** sul catalogo di OpenRouter: quale gratuito con tool diventa il predefinito alla verifica della chiave (`pickDefaultFree`) e quali otto stanno sotto «Consigliati» (`recommended`). Sono euristiche, non liste: una lista nel codice sarebbe vecchia alla prima release.
L'unica lista e' `AiDefaults.OPENROUTER_AVOID`.

**Comportamento.** `score`: ragionamento +3, contesto >= 64k +2 (>= 128k un altro +1), famiglia nota con function calling (`qwen`, `nemotron`, `glm`, `deepseek`, `llama`, `mistral`, `kimi`, `minimax`, `gemini`, `claude`, `gpt`, `grok`, `hermes`; non Gemma: niente tool) +2, `preview`/`exp` -1;
`Int.MIN_VALUE` se il modello non ha tool o e' da evitare. `recommended(catalogue, limit = 8)`: solo punteggio >= 2, gratuiti in testa, poi punteggio, prezzo crescente, contesto.

**Trappole.** `AiDefaults.OPENROUTER_AVOID` (Inkling di Thinking Machines: risponde solo dentro un harness agentico e a una domanda di chat si rifiuta) vale in **tutti** i punti di scelta; una scelta salvata su un modello da evitare conta come nessuna scelta (1.29.0).

## Token, costanti, Defaults

| costante | valore | dove |
|---|---|---|
| `GroqProvider.BASE_URL` | `https://api.groq.com/openai/v1` | |
| `GeminiProvider.BASE_URL` | `https://generativelanguage.googleapis.com/v1beta` | |
| `OpenRouterProvider.BASE_URL` | `https://openrouter.ai/api/v1` | |
| `GroqProvider.SEARCH_MODEL` | `groq/compound-mini` | ricerca web su Groq |
| `GroqProvider.PROMPT_MAX_CHARS` | 800 | prompt di Whisper |
| `GeminiProvider.FALLBACK_TRANSCRIBE_MODEL` | `gemini-3.6-flash` | ripiego della trascrizione |
| `GeminiCodec.SKIP_SIGNATURE` | `skip_thought_signature_validator` | bypass delle thought signature |
| `AiDefaults.GROQ_CHAT` / `GROQ_CLASSIFIER` / `GROQ_STT` | `qwen/qwen3.8-27b` / `llama-3.1-8b-instant` / `whisper-large-v3` | |
| `AiDefaults.GEMINI_CHAT` / `GEMINI_CLASSIFIER` / `GEMINI_STT` | `gemini-3.6-flash` / `gemini-3.5-flash-lite` / `gemini-3.5-transcribe` | poi riallineati al catalogo (`latestGemini`) |
| `AiDefaults.OPENROUTER_STT` / `OPENROUTER_CHAT_FALLBACK` | `openai/whisper-large-v3` / `google/gemini-3.6-flash` | il secondo e' a pagamento |
| `AiDefaults.OPENROUTER_CHAT_PREFERRED` / `OPENROUTER_DEEP_PREFERRED` / `OPENROUTER_CLASSIFIER_PREFERRED` | liste di id `:free` in ordine | usate se presenti nel catalogo |
| `AiDefaults.OPENROUTER_AVOID` | due id di Inkling | mai proposti |

Questi sono default di **prodotto**, rivisti a ogni release dell'engine: un'app non li copia, li legge (`AiDefaults`) o, meglio, lascia fare a `AiKeyVerifier`.

## Cosa e' verificato dai test

- `CodecTest`: forma esatta del JSON OpenAI (ruoli, `tool_calls` con argomenti stringa, `tool_call_id`), due tool call parallele, stream a frammenti con uso e ragione, argomenti malformati che non fanno cadere il giro, dialetto Gemini (`systemInstruction`, `functionCall` con firma, `functionResponse` fusi), parti grezze rimandate identiche, `GeminiSchema.sanitize`, cataloghi Groq e OpenRouter su elenchi veri.
- `ContentPartsTest`: utente di solo testo = stringa, con parti = lista; un documento e' parte `file` solo dove il provider la accetta; `inlineData` su Gemini; capacita' da catalogo o da nome; scelta del profondo (documenti, visione, contesto, non un modello piccolo); catalogo su disco con visione e documenti.
- `FailureCodecTest`: la chiamata malformata di Gemini e' una risposta vuota con fine `OTHER`; un blocco porta la sua ragione; un errore a stream aperto si classifica.
- `OpenRouterDataPolicyTest`: senza scelta non si manda niente, `DENY` manda `data_collection`.
- `WebSearchCodecTest`: plugin `web` solo con la ricerca (accanto al parser PDF), Groq riscritto sul compound senza tool, fonti da `annotations` / `search_results` / `groundingChunks` senza doppioni, accumulo nello stream.
- `StreamTimeoutTest` (in `net`): il timeout della richiesta arriva alla socket, in stream e senza; un `tool_use_failed` non passa dall'auto-riparazione anche se nomina un campo.
- **Non coperto**: le chiamate di rete vere ai tre servizi (i test usano un server locale e fixture); il comportamento di Gemini con le thought signature reali.

## Dove guardare

Radice: `engine-ai/src/main/kotlin/dev/antigravity/fluidengine/ai/provider/`.

| file | cosa c'e' dentro |
|---|---|
| `Model.kt` | il modello neutro (`Message`, `ContentPart`, `ToolCall`, `ToolSpec`, `ChatRequest`, `ChatTurn`, `ChatDelta`, `Usage`, `Citation`), `ModelTier`, `ModelInfo`, `ModelCatalogue`, `ChatProvider` |
| `ProviderId.kt`, `ProviderFactory.kt` | i tre servizi e la fabbrica (`ReadyProvider`) |
| `OpenAiCompatCodec.kt`, `OpenAiCompatProvider.kt` | dialetto OpenAI e giro di auto-riparazione |
| `GroqProvider.kt`, `OpenRouterProvider.kt` | le due sottoclassi, `OpenRouterKeyInfo`, `OpenRouterDataPolicy` |
| `GeminiProvider.kt` | `GeminiProvider`, `GeminiCodec`, `GeminiSchema` |
| `ModelCatalog.kt`, `OpenRouterCatalog.kt`, `Capabilities.kt` | cataloghi, euristiche, `ProviderCapabilities`, `TierDefaults` |
| `ToolCallAssembler.kt` | ricomposizione delle tool call di uno stream |

Altre schede: il giro dei tool e il failover in [ai-orchestrator.md](ai-orchestrator.md); chiavi, impostazioni e rete in [ai-chiavi-rete-voce.md](ai-chiavi-rete-voce.md); l'interfaccia in [ai-ui.md](ai-ui.md); i tool federati in [ai-bridge.md](ai-bridge.md); le regole trasversali in [`../regole.md`](../regole.md).
