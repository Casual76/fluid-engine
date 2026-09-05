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
