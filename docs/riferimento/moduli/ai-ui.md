<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/ai-ui.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-ai-ui: l'interfaccia dell'assistente (opt-in)

`engine-ai-ui` e' la UI che manca a `engine-ai`: quest'ultimo e' trasporto e orchestrazione **senza una riga di Compose** (un servizio in primo piano lo usa senza schermo), `engine-ui` e' il design
system **senza una riga di assistente**. Questo modulo e' l'unico posto che li conosce entrambi. Nasce nella 3.0.0 come **unione** delle quattro interfacce che quattro app si erano scritte ognuna
sopra `engine-ai`: quello che un'app aveva e le altre no diventa un parametro o uno slot, **mai il minimo comune**. **Niente dominio dentro**: prompt, strumenti, nomi, pagine, Room restano all'app; i
testi arrivano da oggetti `Fluid*Strings` con i default italiani (e, dalla 3.0.4, la versione `english()`).

Modello mentale in due frasi: i componenti ricevono **valori** (`AssistantState`, `MicLevel`, `PendingConfirmation`, `FluidAiSettingsState`) e rispondono con **callback** (`FluidAssistantActions`,
`FluidAiSettingsActions`) — non c'e' un ViewModel ne' un DataStore dentro; tutto cio' che e' dominio entra da **slot** e da **`statusFor`**. Le decisioni che sbagliano in silenzio (il fondo di una
lista, il tasto del composer, l'ordine dei servizi, la frase di un errore, l'aptica «una volta sola») sono **funzioni pure** e provate sul computer; il vetro e il moto si guardano in mano.

Questa scheda e' il punto d'ingresso. Il resto e' diviso per argomento:

| file | contenuto |
|---|---|
| [ai-ui-overlay.md](ai-ui-overlay.md) | overlay, card, pillola, **pannello ancorato**, aureola e campo di luce, aptica di stato, voce (lettura), foglio «Dettagli» |
| [ai-ui-chat.md](ai-ui-chat.md) | il kit della conversazione: «in fondo», bolle, coda, composer di vetro, alone in ascolto, riga della voce, azioni |
| [ai-ui-impostazioni.md](ai-ui-impostazioni.md) | `fluidAiSettingsSection`: chiavi, ordine, modelli, preferenze, OpenRouter, diagnostica, opzioni, slot |
| [ai-ui-stringhe.md](ai-ui-stringhe.md) | le tre `Fluid*Strings`, `english()`, `FluidAssistantTexts`, **errori e limiti di richieste** |

Cosa sta altrove: l'orchestratore, gli stati, i provider, le chiavi e le impostazioni salvate sono in [ai-orchestrator.md](ai-orchestrator.md) e [ai-provider.md](ai-provider.md); la panoramica di
`engine-ai` e' [../ai.md](../ai.md). Il Markdown (`FluidMarkdown`, `FluidMarkdownParser`, `FluidRevealingText`) e' di `engine-ui`; la sezione «L'interfaccia dell'assistente» di
[../design-system.md](../design-system.md) e' il riassunto. Il catalogo delle firme, una riga per dichiarazione, e' [../api/ai-ui.md](../api/ai-ui.md).

## Scegliere in fretta

| ho bisogno di… | uso | scheda |
|---|---|---|
| l'assistente sopra una pagina (aureola, barra, card), non modale | `FluidAssistantOverlay` + `FluidAssistantOverlayState` | [overlay](ai-ui-overlay.md) |
| l'assistente come pannello fisso in basso (c'e' una mappa/vista viva sopra) | `FluidAssistantDock` | [overlay](ai-ui-overlay.md) |
| una conversazione a pagina intera | `FluidFollowBottom`, bolle, `FluidComposer` | [chat](ai-ui-chat.md) |
| la pagina «Impostazioni → Assistente» | `fluidAiSettingsSection` | [impostazioni](ai-ui-impostazioni.md) |
| tradurre / dare il nome / le frasi dei miei strumenti | `FluidAssistantStrings`, `FluidAiSettingsStrings`, `FluidChatStrings` | [stringhe](ai-ui-stringhe.md) |
| cosa dire di un errore o di un limite di richieste | `FluidAssistantTexts.failure`, `FluidFailureReport` | [stringhe](ai-ui-stringhe.md), [overlay](ai-ui-overlay.md) |
| leggere le risposte ad alta voce | `rememberFluidTtsSpeaker` / `FluidSpeaker` | [overlay](ai-ui-overlay.md) |
| una pagina di prova con dati finti, per guardare tutto in mano | `fluidAiUiDemoItems()` | sotto |
| Markdown per una nota/novita' che **non** e' una risposta d'assistente | `FluidMarkdown` di `engine-ui` | [../design-system.md](../design-system.md) |
| trasporto, provider, chiavi, failover, strumenti | `engine-ai` | [ai-orchestrator.md](ai-orchestrator.md), [ai-provider.md](ai-provider.md) |

**Non cosi'**: non scrivere una UI dell'assistente dentro l'app (e' esattamente il lavoro che questo modulo ha tolto da quattro app); non mettere un nome di dominio in un componente
(«Guardo i tuoi elementi…») — va in `FluidAssistantStrings.statusFor`; non richiamare `engine-ai-ui` da un servizio senza schermo (non serve, usa `engine-ai`).

## Il modulo

- **Opt-in.** `tools/engine-install.ps1 -Modules engine-ai-ui` (porta con se' `engine-ui`, `engine-ai` ed `engine-foundation`); le app che non lo chiedono non cambiano.
- **Dipendenze** (`engine-ai-ui/build.gradle`): `api project(':engine-ui')` e `api project(':engine-ai')`. Sono `api` perche' le firme pubbliche parlano `AssistantState`, `ProviderId`,
  `GlassBackdropState`: un'app che include il modulo le usa senza ridichiararle.
- **Nessun permesso proprio** (il manifest e' vuoto): `RECORD_AUDIO` arriva con `engine-ai`, e la UI lo chiede a runtime **solo** quando l'utente tocca il microfono.
- **Richiede**, intorno: un `FluidTheme`, un `GlassBackdropState` della pagina, e un **host dei modali alla radice** (`FluidGlassModalHost`): il picker dei modelli e il foglio «Dettagli» ci passano e,
  senza, **non si aprono e non danno errore**.
- **Struttura** (`dev.antigravity.fluidengine.aiui`): `overlay/`, `halo/`, `chat/`, `markdown/` (solo `FluidAssistantMarkdownOptions`), `settings/`, `strings/`, piu' `FluidAiUiDemo`.

## Montare l'assistente in un'app

Cosa fa l'engine e cosa resta all'app:

| l'engine (`engine-ai-ui`) | l'app |
|---|---|
| overlay/dock, card, aureola, barra, pillole, foglio «Dettagli» | dove vive lo stato (ViewModel, servizio, sessione) e come parte una domanda |
| aptica di stato, lettura ad alta voce, permesso del microfono | il tasto che invoca l'assistente (tocco = voce, pressione lunga = testo) |
| la sezione impostazioni con chiavi, ordine, modelli | cosa e' un chip e dove porta; la navigazione dei tool |
| bolle, composer, «in fondo» | le frasi dei suoi gruppi di strumenti (`statusFor`), il nome, cosa parte verso il servizio |
| le parole italiane/inglesi e le guide dei tre servizi | i messaggi su disco, gli allegati, le versioni, la coda come concetto |

**1. Lo stato** (nell'app, non nell'engine). Un `MutableStateFlow<AssistantState>` e un `MutableStateFlow<MicLevel>` (il livello del microfono sta **a parte**: cambia a ~50 Hz). L'orchestratore
scrive lo stato: `AiOrchestrator.ask(input, state)`. Il cancello delle conferme (`AiConfirmationGate`) si lega allo stesso flusso con `bindState(state)` e scrive da solo `AwaitingConfirmation`.

```kotlin
class AssistantModel(config: AiOrchestratorConfig /* … orchestratore, chiavi, impostazioni … */) {
  val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
  val micLevel = MutableStateFlow(MicLevel())
  val gate = AiConfirmationGate(config).also { it.bindState(state) }
  // ask(question, mode) -> orchestrator.ask(AskInput(...), state)
  // l'ascolto: SpeechCapture.record(...) emette Event.Level(level, speaking, elapsedMillis):
  //   micLevel.value = MicLevel(e.level, e.speaking); state.value = AssistantState.Listening(e.elapsedMillis)
}
```

**2. Il collegamento**, valore per valore:

| `engine-ai-ui` vuole | viene da |
|---|---|
| `state: AssistantState` | il tuo `MutableStateFlow` passato all'orchestratore |
| `micLevel: MicLevel` | il flusso del microfono (`SpeechCapture`) mappato su `MicLevel(level, speaking)` |
| `pending: PendingConfirmation?` | `AiConfirmationGate.current` (un `StateFlow`) |
| `actions.onConfirm(id, yes)` | `AiConfirmationGate.resolve(id, yes)` |
| `actions.onCancel` | il tuo cancellare la richiesta (e `AiConfirmationGate.cancel()` per quelle in fila) |
| `actions.onAskText` / `onAskVoice` | `ask(question, AskMode.TEXT)` / far partire l'ascolto |
| `lastMode: AskMode?` | l'ultimo `AskMode` usato (`Done.mode` ce l'ha solo a fine risposta: durante lo stream lo tieni tu) |
| `FluidAiSettingsState.keys` / `catalogues` / `keyInfo` / `recent` / `settings` | `AiKeyStore.states` / `ModelCatalogStore.catalogues` / `AiKeyVerifier.keyInfo` / `AiDiagnosticsLog.entries` / `AiSettingsStore.settings` |

**3. La radice** (una volta). Il vetro, l'host dei modali, la pagina e, **fratello sopra la pagina**, l'overlay:

```kotlin
FluidTheme {
  val backdrop = rememberGlassBackdrop()
  val overlay = remember { FluidAssistantOverlayState() }
  Box(Modifier.fillMaxSize()) {
    AppPages(backdrop, overlay)                     // il corpo ha Modifier.glassBackdropSource(backdrop)
    AssistantLayer(model, overlay, backdrop)        // raccoglie state/micLevel/pending e chiama FluidAssistantOverlay(...)
  }
  FluidGlassModalHost(/* … */)                       // alla radice: picker dei modelli e «Dettagli» ci passano
}
```

`AssistantLayer` e' un composable **piccolo**: raccoglie `state`, `micLevel` e `pending` e contiene solo l'overlay. Se raccogli il livello del microfono nella pagina, la pagina ricompone a 50 Hz
([ai-ui-overlay.md](ai-ui-overlay.md)).

**4. Il tasto.** Nella pagina un tasto dell'assistente: tocco -> `overlay.openVoice()`, pressione lunga -> `overlay.openText()`, e `overlay.originBounds = rect` (il rettangolo del tasto in
coordinate di radice) perche' la card **cresca da li'**. Pagina toccata o scorsa -> `overlay.onPageInteraction()`. Un chip o un tool che apre un'altra pagina -> `overlay.collapseForNavigation()` e poi la
tua navigazione.

**5. Le impostazioni.** Nella tua pagina di impostazioni: `fluidAiSettingsSection(...)` dentro la `LazyColumn`, e un `FluidAiCatalogueRefresh(verified, refresh)` una volta
([ai-ui-impostazioni.md](ai-ui-impostazioni.md)).

**6. Le parole.** `FluidAssistantStrings(statusFor = …)` con le frasi dei **tuoi** gruppi di strumenti, il nome, la nota sulla privacy ([ai-ui-stringhe.md](ai-ui-stringhe.md)).

**Trappole del montaggio.**

- `FluidAssistantOverlay` e' un'estensione di `BoxScope`: deve stare in un `Box` a schermo intero, **sopra** la pagina. Dentro una `Column` non compila.
- Senza `originBounds` l'overlay compare con una sfumatura e una salita: funziona, ma non e' «il tasto che diventa la card».
- `FluidWeather` e `Fluid-transit` (due delle app di origine) avevano stati propri: **dovevano passare agli stati di `engine-ai`** prima di usare l'overlay (che parla con quelli; cosi' dice il CHANGELOG 3.0.0, lo stato delle due app oggi non e' verificato qui). Il diff delle quattro copie e
  cosa sostituisce ogni app e' in `artifacts/design/ai-ui-diff.md`.

## Regole trasversali

**Una vibrazione per momento.** L'aptica dell'assistente e' una macchina a stati pura (`FluidAssistantHapticTracker`) e ogni evento passa da `FluidHaptics`, che fonde i colpi a meno di 70 ms: due
componenti che dicono lo stesso momento non fanno un «toc-toc». Per questo i tasti dell'assistente non suonano da soli dove un altro evento arriva (`FluidStopPill` senza aptica: arriva `Stop` dall'overlay) e
`tapHaptic` della card e' spento di serie. Vedi [../regole.md](../regole.md).

**Dati, non flussi.** Ne' l'overlay ne' la sezione impostazioni sanno dove vive lo stato: l'app lo ricava dai suoi flussi e lo passa come valore. E' anche cio' che le fa funzionare con i quattro modi in cui
le app lo tengono (un ViewModel, una sessione, un servizio in primo piano, un `DataStore`).

**Il livello del microfono non ricompone.** Dove un componente lo vuole 50 volte al secondo prende una **lambda** (`FluidHaloField(level = () -> Float)`, `rememberFluidListeningGlow(level = …)`) e la legge
dentro il loop di frame; dove prende un valore (`FluidAssistantOverlay(micLevel)`), lo raccogli in un composable piccolo.

**Vetro.** Tutto cio' che e' vetro (card, barra, pillole, composer, picker) rifrange un `GlassBackdropState`. Il composer **esporta** il proprio materiale cosi' i tasti sopra ne piegano la capsula e non la
pagina sotto. Ruoli: card e barra `Modal`, dock e composer `Floating` (con riserva: il composer in `Floating` e' una scelta provata sul telefono, il ripiego e' `GlassRole.Modal`, una parola).
`FluidAssistantHalo` e `FluidHaloField` usano **solo gradienti**: niente shader, gira uguale sui device deboli.

**Movimento ridotto** (`LocalFluidMotionPolicy`: scala delle animazioni a zero):

| componente | cosa fa |
|---|---|
| `FluidAssistantHalo` | resta ferma e sfuma soltanto (il tempo non avanza, l'ampiezza salta al bersaglio) |
| `FluidHaloField` | l'orologio sta fermo; `staticWhenReduced = true` lo lascia fermo e visibile, `false` lo nasconde |
| alone del composer (`rememberFluidListeningGlow`) | l'orologio non avanza (stesso orologio) |
| `FluidFollowBottom` | la molla diventa un salto |
| `FluidAssistantBubble` | niente misura animata, niente dissolvenze di chip e azioni (`EnterTransition.None`) |
| `FluidBreathingDot` | e' `FluidLiveDot`: fermo |
| overlay (morph), card, dock | nessun controllo esplicito nel codice dell'overlay: usano i token `FluidMotion` |

**TalkBack.** Letto dal sorgente, e **non uniforme**:

- Il **dock** e' curato: `paneTitle` (il riquadro che si apre), la riga di stato e' una `liveRegion` educata, «Per esempio» e' un'intestazione, la risposta finita si annuncia una volta (primi 600 caratteri,
  `announceAnswer`).
- Il foglio «Dettagli» e il picker dei modelli hanno `paneTitle`.
- Le bolle: le `actions` di `FluidUserBubble` sono anche azioni personalizzate di TalkBack (`extraAccessibilityActions` ne aggiunge altre).
- **L'overlay e la card non annunciano niente da soli** (nessuna `liveRegion`, nessun `paneTitle`): chi usa TalkBack con l'overlay non sente le risposte arrivare. Se l'app lo serve, aggiunge l'annuncio
  (`header` della card e un `liveRegion`, o `announceForAccessibility` come fa il dock).
- Ordine dei servizi: le frecce su/giu' sono l'unica strada per chi non puo' trascinare (tastiera, TalkBack); la maniglia ha una descrizione («Tieni premuto e trascina per riordinare»).
- Bersagli: `FluidSmallAction` e `FluidPillAction` a 48 dp; `FluidBarIcon` e i tasti tondi del composer disegnano 40 dp (il tocco ha il suo minimo).

**Errori e limiti di richieste.** Cosa vede l'utente (`WaitingRateLimit` -> `SwitchingProvider` -> «Ha risposto Gemini: Groq era al limite» -> `Failed`), quale delle cinque `failRateLimited…` sceglie
`FluidAssistantTexts.failure`, e **perche' `failRateLimitedAll` non arriva da solo** (lo stato `Failed` non porta i servizi provati): [ai-ui-stringhe.md](ai-ui-stringhe.md).

## La pagina di prova: `fluidAiUiDemoItems()`

`LazyListScope.fluidAiUiDemoItems()` (`FluidAiUiDemo.kt`) mette in fila pezzi veri con **dati finti**: Markdown con lo stream, card + aureola + dock a sei stati (Ascolto, Lavoro, Scrive, Risposta, Conferma,
Errore), una chat con bolle e composer, e la sezione impostazioni. **Non e' nella galleria** (il modulo non e' un'app e la galleria non lo conosce): un'app ospite la chiama dentro un `FluidTheme`, nella lista di un
`FluidScreen`, e con l'host dei modali alla radice. E' il posto da cui **copiare i call-site** (non c'e' altro uso del modulo in `sample`).

## Cosa e' verificato dai test

114 test su JVM in sei file (`engine-ai-ui/src/test/.../aiui/`): niente schermo, niente vetro, niente moto.

| file | garantisce |
|---|---|
| `FluidOverlayLogicTest` (18) | l'aptica «una volta sola» (ascolto, primo parlato, risposta pronta, errore/stop/cambio servizio/attesa); il gettone dell'ascolto; la barra con/senza tastiera; la pagina toccata fa da parte la card; `FluidSentences` (frasi chiuse, ultima tenuta da parte fino a `final`); il resoconto di un errore (codice, messaggio, catena); la scala del pallino e la testata del dock |
| `FluidChatLogicTest` (23) | la regola del fondo, le chiavi della lista, il tasto del composer per ogni combinazione, `fill`/`sent`, la tabella degli esiti |
| `FluidAiSettingsLogicTest` (30) | ordine dei servizi, forma delle chiavi, guide, filtri/sezioni/riserve di OpenRouter, crediti e quote, `enabled`, le righe del registro, i segnaposto |
| `FluidAssistantTextsTest` (18) | la tabella pura delle frasi, i limiti di richieste, i servizi provati |
| `FluidEnglishStringsTest` (11) | le tre `english()` per riflessione, le varianti senza riserva automatica, `models = false`, `voiceActive` |
| `FluidHaloMathTest` (14) | umore dallo stato, velocita' e ampiezza per umore, attacco/rilascio indipendente dalla frequenza, macchie equispaziate sul perimetro, respiro/raggio/alpha, ciclo dei colori, i quattro colori dell'aureola |

**Non verificato** (in `docs/10-coerenza.md` la riga e' tutta «?»): il vetro, il morph dal tasto, il moto, TalkBack reale, il microfono reale, la lettura ad alta voce, la rete. Si guardano in mano sul telefono.

## Dove guardare

`engine-ai-ui/src/main/kotlin/dev/antigravity/fluidengine/aiui/`:

| percorso | cosa c'e' dentro |
|---|---|
| `overlay/` | overlay e stato, card, dock, pillole, barra di scrittura, aptica, voce, resoconto e foglio dei dettagli |
| `halo/` | `FluidAssistantHalo`, `FluidHaloField` (campo di luce, orologio, aurora, matematica pura) |
| `chat/` | `FluidFollowBottom`, bolle, azioni, `FluidComposer` |
| `markdown/FluidAssistantMarkdown.kt` | `FluidAssistantMarkdownOptions` |
| `settings/` | la sezione, la logica pura, la scheda chiave, il picker, l'ordine, i dettagli OpenRouter |
| `strings/` | le tre `Fluid*Strings` e `FluidAssistantTexts` |
| `FluidAiUiDemo.kt` | la pagina di prova |

Altro: `skill/fluid-engine/references/api/ai-ui.md` (catalogo), `artifacts/design/ai-ui-diff.md` (il diff delle quattro copie), `CHANGELOG.md` (cerca «ai-ui»: 3.0.0 nascita, 3.0.4 `english()`,
`models`, `enabledModifier`, `voiceActive`, `failoverSwitch`).
