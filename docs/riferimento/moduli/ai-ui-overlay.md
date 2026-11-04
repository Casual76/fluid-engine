<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/ai-ui-overlay.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-ai-ui: overlay, card, pannello ancorato, aureola, aptica e voce

L'assistente "in scena" sopra una pagina: l'aureola in cima, la barra di scrittura, la card che cresce dal
tasto, la pillola in cui si riduce, il pannello ancorato in basso, l'aptica di stato, la lettura ad alta voce, il
foglio «Dettagli» di un errore. Nessun pezzo conosce un ViewModel: ricevono **valori** (`AssistantState`,
`MicLevel`, `PendingConfirmation`) e rispondono con **callback** (`FluidAssistantActions`). Tutto cio' che e'
dominio (cosa e' un chip, dove porta, cosa dice un gruppo di strumenti) lo mette l'app.

Per montare il tutto (ricetta, collegamento all'orchestratore, errori) leggi prima [ai-ui.md](ai-ui.md). La chat
a pagina intera e' in [ai-ui-chat.md](ai-ui-chat.md), le impostazioni in [ai-ui-impostazioni.md](ai-ui-impostazioni.md),
le parole in [ai-ui-stringhe.md](ai-ui-stringhe.md). Il catalogo delle firme e' [../api/ai-ui.md](../api/ai-ui.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| l'assistente sopra la pagina: aureola + barra + card, non modale | `FluidAssistantOverlay` (+ `FluidAssistantOverlayState`) |
| l'assistente come pannello fisso in basso (c'e' qualcosa di vivo sopra, p.es. una mappa) | `FluidAssistantDock` |
| la sola card della risposta, in un layout mio | `FluidAssistantCard` |
| una luce in cima che segue voce / lavoro / scrittura | `FluidAssistantHalo` |
| una luce sul perimetro di una forma qualunque (schermo, capsula) o un'aurora di sfondo | `FluidHaloField`, `drawFluidAuroraField` |
| il foglio con il perche' di un errore e «Copia» | `FluidFailureDetailsSheet` (+ `FluidFailureReport`) |
| leggere la risposta ad alta voce | `rememberFluidTtsSpeaker()` -> `speaker` dell'overlay, oppure un mio `FluidSpeaker` |
| una conversazione a pagina intera, con bolle e composer | non qui: [ai-ui-chat.md](ai-ui-chat.md) |
| le impostazioni (chiavi, modelli, ordine) | non qui: [ai-ui-impostazioni.md](ai-ui-impostazioni.md) |
| cambiare la resa del corpo della risposta (non Markdown) | `FluidAssistantCardOptions(body = ...)` o `FluidAssistantCard(body = ...)` |
| aggiungere una riga mia sopra/sotto il corpo della card | `header` / `footer` (stessi due slot) |

**Non cosi'**: non incapsulare l'overlay in un `FluidGlassModalPortal` "perche' e' un pop-up". L'overlay e' non
modale per scelta (nessuno scrim, i tocchi fuori passano alla pagina); un modale lo renderebbe un dialogo.
Il pannello che cresce dal tasto e' fatto a mano dentro l'overlay.

## Cosa serve intorno

- `FluidAssistantOverlay` e' un'**estensione di `BoxScope`**: va dentro un `Box(Modifier.fillMaxSize())` radice,
  fratello della pagina, **sopra** di essa. Si allinea da solo in alto (`Alignment.TopCenter`).
- `backdrop: GlassBackdropState` e' quello della pagina (`rememberGlassBackdrop()` + `Modifier.glassBackdropSource`
  sul corpo scorrevole, come per ogni vetro dell'engine): card, barra e pillole rifrangono quello.
- Il foglio dei dettagli e il picker dei modelli passano dal **host dei modali** (`FluidGlassModalHost` alla
  radice). Senza host non si aprono **e non vanno in crash**: il portale esce in silenzio.
- Nessun permesso proprio: `RECORD_AUDIO` arriva con `engine-ai`, e l'overlay lo chiede **solo** al tocco del microfono.

## L'overlay

**Cos'e'.** Aureola in cima, barra di scrittura (modo testo), card della risposta che *cresce dal tasto*, e la
pillola in cui si riduce quando l'utente torna a usare la pagina. La domanda vive nell'app: chiudere l'overlay
**non** la ferma (salvo il trascinamento verso l'alto mentre lavora).

**Firma essenziale.**

```kotlin
@Composable
fun BoxScope.FluidAssistantOverlay(
  state: AssistantState,
  micLevel: MicLevel,
  pending: PendingConfirmation?,
  overlay: FluidAssistantOverlayState,
  backdrop: GlassBackdropState,
  actions: FluidAssistantActions,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
  chipLabel: (AnswerChip) -> String = ::fluidDefaultChipLabel,
  speaker: FluidSpeaker? = null,
  speakReplies: Boolean = false,
  lastMode: AskMode? = null,
  haptics: Boolean = true,
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
  card: FluidAssistantCardOptions = FluidAssistantCardOptions(),
  onForegroundChanged: ((Boolean) -> Unit)? = null,
  collapseOnChip: (AnswerChip) -> Boolean = { true },
  dismissDrag: Dp = 56.dp,
)
```

Tutti i parametri: [../api/ai-ui.md](../api/ai-ui.md).

**Uso** (la pagina tocca l'overlay in due punti: tocco e pressione lunga sul tasto dell'assistente).

```kotlin
val overlay = remember { FluidAssistantOverlayState() }
Box(Modifier.fillMaxSize()) {
  Page(
    modifier = Modifier.onGloballyPositioned { /* il rettangolo del tasto: */ },
    onAssistantTap = { overlay.openVoice() },
    onAssistantLongPress = { overlay.openText() },
    onAnyInteraction = { overlay.onPageInteraction() },
  )
  FluidAssistantOverlay(
    state = state, micLevel = mic, pending = pending,
    overlay = overlay, backdrop = backdrop,
    actions = FluidAssistantActions(
      onAskText = { question -> vm.ask(question, AskMode.TEXT) },
      onAskVoice = { vm.startListening() },
      onStopListening = { vm.stopListening() },
      onCancel = { vm.cancel() },
      onDismiss = { vm.reset() },
      onNewConversation = { vm.newConversation() },
      onConfirm = { id, yes -> vm.resolve(id, yes) },
      onChip = { chip -> vm.open(chip) },
    ),
    card = FluidAssistantCardOptions(onDetails = { report = it }),
  )
}
FluidFailureDetailsSheet(report = report, onDismiss = { report = null })
```

`overlay.originBounds = rect` va impostato con il rettangolo del tasto (in coordinate di radice): senza,
l'overlay compare sfumando e salendo di un quarto della sua altezza invece di crescere dal tasto.

**Comportamento.** Cio' che il nome non dice, dal sorgente:

- *Gesti.* Tocco sul tasto -> `openVoice()`; pressione lunga -> `openText()`; pagina toccata/scorsa ->
  `onPageInteraction()`, che **riduce la card a pillola senza fermare niente**; tocco sulla pillola -> la riespande;
  card trascinata in alto di oltre `dismissDrag` (56 dp, **in dp, non in pixel**: su uno schermo denso 160 px erano
  mezzo centimetro) -> se lavora `onCancel()`, poi `onDismiss()` e `hide()`; dopo il rilascio la card torna a posto con
  una molla. Un tocco sul corpo della card **zittisce la voce** (`speaker?.stop()`), non altro.
- *Modi.* `FluidOverlayMode` ha `HIDDEN`, `VOICE` (aureola grande, tasto «tocca per finire»), `TEXT` (barra + card).
  La barra di scrittura c'e' solo in `TEXT` con `composing` e card non ridotta. Dopo l'invio la barra sparisce
  (`sent()`): chi ha scritto non ha piu' niente da scrivere. A `Done` la barra torna (`resumeComposing()`) **senza
  tastiera** (`autoFocus = false`), perche' riaprirla col fuoco copriva la risposta appena aspettata. In `VOICE`
  la barra non torna mai.
- *Stato che nasce altrove.* `Listening` a overlay nascosto -> `openVoice()`; qualunque stato occupato a overlay
  nascosto -> modo `TEXT`; uno stato "da leggere" (`FluidAssistantTexts.wantsExpanded`: `Answering`, `Done`, `Failed`,
  `AwaitingConfirmation`) riespande la card ridotta. Una domanda partita da una notifica o da un servizio apre quindi
  l'overlay da sola.
- *`HeardNothing`.* Mostra «Non ho sentito niente» per 1,6 s, poi `onDismiss()` e, se si era in `VOICE`, `hide()`.
- *Il morph.* Un solo `Animatable` 0..1: apertura con `FluidMotion.tuned(DampingFluid, ResponseStandard)`, chiusura
  con `DampingChrome`/`ResponseSnappy`. Il pannello e' scalato e traslato sul rettangolo del tasto e a progresso 0
  coincide con esso; il **contenuto arriva dopo la forma** (`alpha = (progress - 0.25) / 0.55`): schiacciato, il testo
  sarebbe solo deformazione. Il pannello ha `statusBarsPadding` e `imePadding`: sale sopra la tastiera.
- *Aureola dell'overlay.* Umore: nascosto se non visibile o card ridotta; `Listening` -> `LISTENING` (con il livello del
  microfono); `Answering` -> `WRITING`; `Failed` -> `ERROR`; `Done` -> `DONE`; altro occupato -> `WORKING`. Altezza 150 dp in
  `VOICE`, 96 altrimenti.
- *Gettone dell'ascolto.* Ogni `openVoice()` incrementa `voiceRequest`; **un solo** effetto lo consuma
  (`consumeVoiceRequest()`), e solo se non e' gia' occupato. Se il microfono non e' concesso passa a `TEXT` e lancia il
  permesso; concesso il permesso, riapre la voce da solo. Un contatore letto da un effetto bastava a far partire un
  secondo `AudioRecord` a ogni ricomposizione.
- *Il permesso* si rilegge a ogni `ON_RESUME` (concesso dalle impostazioni di sistema, prima restava «negato» finche' la
  schermata non veniva ricreata).
- *Voce.* Legge solo se `speakReplies` **e** `lastMode == AskMode.VOICE`. La voce si azzera **all'inizio di una
  domanda** (`Listening`, `Classifying`, `Transcribing`), non a ogni aggiornamento dello stato: con la chiave su
  `state` l'effetto ripartiva a ogni frame audio e fermava la voce cinquanta volte al secondo. `Cancelled`, `Failed`,
  `Idle` la zittiscono. L'overlay **non** chiama `release()`: lo fa `rememberFluidTtsSpeaker` allo smontaggio.
- *Primo piano.* `onForegroundChanged(true/false)` a `ON_START`/`ON_STOP` e `false` allo smontaggio: serve a decidere se
  la risposta va anche in notifica.

**Trappole.**

- `micLevel` arriva **per valore**: il livello del microfono cambia a ~50 Hz. Se lo raccogli (`collectAsState`) nello
  stesso composable che disegna la pagina, la pagina ricompone 50 volte al secondo. Raccoglilo in un composable piccolo
  che contiene solo l'overlay. (Il livello non sta in `AssistantState` apposta: ricomponeva tutto l'overlay a ogni
  frame audio e faceva ripartire gli effetti che fermavano la voce.)
- Con `suppressed = true` (una schermata mostra gia' la conversazione intera) l'overlay si nasconde da solo e non
  riappare finche' non lo rimetti a `false`.
- `collapseOnChip` vale `true` per qualunque chip: se un chip **non** porta in un'altra pagina, passa `{ false }`, o la
  card si ridurra' a ogni tocco.
- `onDetails` e' opzionale e, se manca, la card non mostra «Dettagli». Il foglio va nella **pagina**, non nell'item di una
  lista: un portale dentro un item che esce di vista si chiude da solo.
- La lettura ad alta voce vale solo per le domande fatte a voce: se non passi `lastMode`, non legge mai.

**Non usarlo quando.** C'e' una mappa o altro contenuto vivo sotto che deve restare visibile: `FluidAssistantDock`.
Se la conversazione e' la pagina: [ai-ui-chat.md](ai-ui-chat.md).

**Versione.** Dalla 3.0 (`engine-ai-ui` nasce nella 3.0.0). Il pallino che respira passa da 700 a 900 ms.

### `FluidAssistantActions` e `FluidAssistantCardOptions`

`FluidAssistantActions(onAskText, onAskVoice, onStopListening, onCancel, onDismiss, onNewConversation, onConfirm, onChip)`
sono solo callback; solo `onAskText` e `onAskVoice` sono obbligatori. Callback e non ViewModel perche' lo stato
dell'assistente vive in posti diversi (un ViewModel, una sessione, un servizio in primo piano) e l'overlay non deve
sapere dove.

`FluidAssistantCardOptions(maxBodyHeight = 380.dp, tapHaptic = null, showSwitchLine = true, onDetails = null, header,
footer, body)` raccoglie le dodici opzioni della card per non ripeterle sull'overlay.

### `FluidAssistantOverlayState`

Stato tenuto **fuori** dalla composizione della pagina (un `remember` alla radice), cosi' sopravvive alle
ricomposizioni. Campi osservabili: `mode`, `collapsed`, `composing` (sola lettura), `voiceRequest` (sola lettura),
`autoFocus` (sola lettura), `originBounds`, `suppressed`. Metodi: `openVoice`, `openText`, `sent`, `resumeComposing`,
`hide`, `onPageInteraction`, `collapseForNavigation` (un tool, o un chip, ha aperto una pagina: la card si fa da parte;
la navigazione e' dell'app), `consumeVoiceRequest`. `openVoice()`/`openText()` riaprono anche una card ridotta
(`collapsed = false`) e **non** toccano `originBounds`.

## La card

**Cos'e'.** Domanda in piccolo, riga di stato con il pallino che respira, risposta in streaming con il Markdown, chip,
tasto stop discreto mentre lavora. E' vetro **sopra** la pagina (ruolo `GlassRole.Modal`, forma `FluidRadius.Sheet`),
non un dialogo: quello che c'e' dietro resta leggibile.

**Firma essenziale.**

```kotlin
FluidAssistantCard(
  state = state, pending = pending, backdrop = backdrop,
  onStop = { vm.cancel() }, onChip = { vm.open(it) }, onConfirm = { id, yes -> vm.resolve(id, yes) },
  onDetails = { report = it },     // facoltativo: senza, nessun «Dettagli»
  maxBodyHeight = 380.dp,
)
```

**Comportamento.**

- La domanda (`FluidAssistantTexts.question(state)`) sta sopra la riga di stato, max 2 righe. Non c'e' in ascolto ne' in
  trascrizione.
- Il tasto stop (`FluidStopPill`) compare mentre l'assistente e' occupato **tranne** in `AwaitingConfirmation` (li' parlano
  Conferma/Annulla).
- Il corpo e' `FluidMarkdown` con `FluidAssistantMarkdownOptions` (i marcatori `[[chip]]` non si vedono). Cambia con
  `animateFirst = false`: la card non rivela il primo pezzo, e' il testo che cresce. E' alto al massimo `maxBodyHeight`
  e **segue la risposta**: `FluidFollowAnswer` torna in fondo ogni 120 ms **solo se ci si era**; chi risale a
  rileggere resta dov'e' e si riaggancia tornando in fondo (scarto 12 dp). Senza, la card si allungava fino al tetto e
  le parole nuove finivano sotto il bordo.
- La conferma (`FluidConfirmationRow`) si mostra se `pending != null` e lo stato e' occupato **o** e' `AwaitingConfirmation`.
- A `Done`: i chip (`FluidChipPill`, a capo), poi la riga «Ha risposto Gemini: Groq era al limite» (`showSwitchLine`).
  La riserva automatica e' accesa di default: un cambio di servizio non detto e' un altro modello che risponde senza che
  si sappia.
- A `Failed` con `onDetails`: tasto «Dettagli» con `FluidFailureReport.of(state)` (**senza** `switches`: vedi sotto).
- `tapHaptic` e' l'evento al tocco sul corpo; di serie niente (zittire non e' un momento).
- `FluidStatusLine`: la frase che sale e prende il posto della precedente (slide + dissolvenza), col pallino finche' occupato;
  rossa per `Failed`.

**Trappole.** `FluidAssistantCard` da sola **non** gestisce ne' il morph ne' il trascinamento ne' la pillola: sono
dell'overlay. Se la usi in un tuo layout, la riduzione la fai tu (`FluidCollapsedPill`).

**Non usarla quando.** Serve un layout ancorato: `FluidAssistantDock`.

## Il pannello ancorato: `FluidAssistantDock`

**Cos'e'.** L'assistente come pannello di vetro appoggiato in basso (ruolo `GlassRole.Floating`, `FluidRadius.Sheet`) invece di
aureola + barra + card. Nato per una pagina con una mappa viva sotto: il momento in cui si vuole guardarla e' proprio quello
in cui l'assistente sta cercando. Non e' una schermata e non e' un dialogo.

**Firma essenziale.**

```kotlin
FluidAssistantDock(
  state = state, micLevel = mic, pending = pending, backdrop = backdrop,
  actions = FluidAssistantActions(onAskText = { vm.ask(it, AskMode.TEXT) }, onAskVoice = { vm.startListening() }, onStopListening = { vm.stopListening() }),
  suggestions = listOf("Esempio uno", "Esempio due"),   // dominio: li scrive l'app
  maxChips = 3, maxBodyHeight = 280.dp,
  onClose = { /* nascondi il pannello */ },
)
```

**Comportamento.** Dall'alto: testata (`FluidMicDot`, riga di stato, chiudi), corpo con la risposta, esempi toccabili a
riposo, chip, conferma, riga di ingresso (campo + un tasto).

- Il tasto a destra del campo: `Listening` -> stop dell'ascolto (`onStopListening`); campo vuoto -> microfono
  (`onAskVoice`); altrimenti invio. L'invio della tastiera funziona (`ImeAction.Send`): prima diceva «Invia» e non faceva niente.
- **Il corpo prende quello che avanza** (`weight(1f, fill = false)` dentro una colonna con `imePadding`): testata, chip, conferma
  e riga di ingresso si misurano prima, la risposta scorre nello spazio che resta. Con la tastiera aperta e una risposta lunga
  a carattere grande, il tetto fisso veniva misurato per primo e la riga per scrivere restava con gli avanzi, tagliata.
- **La testata cresce col testo** (`heightIn(min = 46.dp)`, fino a cinque righe): una frase d'errore a carattere grande
  va su quattro righe, e una misura fissa ne mostrava una e mezza.
- A riposo e a campo vuoto (`Idle`), con `suggestions`: «Per esempio…» + le voci; il blocco prende anche lui "quello che
  avanza" e scorre. Una voce toccata chiama `onAskText(voce)`.
- La conferma si guarda dall'azione **in attesa** (`pending`), **non** dallo stato: lo stato lo scrivono in sei punti
  l'orchestratore e la sessione, e basta che uno arrivi dopo perche' il tasto non compaia mai. Mostra «Serve una conferma»
  in testata. I due tasti dividono la larghezza (`fillButtons = true`).
- La X in testata fa `onCancel()` **e** `onDismiss()` e poi `onClose()`: chiudere il pannello e' anche fermare. Non c'e' un
  tasto stop separato mentre lavora.
- Il testo della testata: a riposo `idleStatus`, a `Done` `dockDone`, altrimenti la riga di stato.
- Il campo di invio ignora l'invio con testo vuoto o mentre `Listening`.

**TalkBack.** Il riquadro si annuncia (`paneTitle = strings.title`), la riga di stato e' una `liveRegion` educata, «Per
esempio» e' un'intestazione, e a risposta finita (`Done`) il testo (i **primi 600 caratteri**) si annuncia una volta con
`announceForAccessibility` se `announceAnswer` e' vero.

**Trappole.**

- Il tasto a destra offre il microfono anche mentre l'assistente lavora (campo vuoto, non in ascolto): `onAskVoice` deve
  saper rispondere «occupato» da solo.
- La bozza (`draft`) e' un `remember`, non un `rememberSaveable`: una rotazione la perde.
- `maxChips` taglia `Done.chips` senza dire nulla: gli altri non si vedono da nessuna parte.
- Nessun tasto «Dettagli» ne' riga di cambio servizio: se vuoi la traccia dell'errore usa la card, o scrivi tu lo stato
  d'errore nel `header` di un tuo layout.

**Non usarlo quando.** Vuoi la card che cresce da un tasto sopra una pagina: `FluidAssistantOverlay`.

### Pezzi riusabili (`FluidAssistantParts.kt`, `FluidAssistantTextBar.kt`)

| pezzo | cosa e' |
|---|---|
| `FluidBreathingDot` | il pallino di `FluidLiveDot`: un respiro solo per tutta l'app, fermo col movimento ridotto |
| `FluidStopPill` | tasto stop da 40 dp di vetro dei controlli; **nessuna** aptica al tocco (arriva `Stop` dall'overlay) |
| `FluidChipPill(label, backdrop, onClick, maxLines)` | chip di vetro, minimo 40 dp; `maxLines = 2` per i nomi lunghi |
| `FluidCollapsedPill(text, backdrop, onExpand, error)` | la card ridotta: puntino (rosso se `error`) e una riga |
| `FluidListeningHint(backdrop, elapsedMillis, onStop)` | «tocca per finire» sotto l'aureola, bersaglio da 48 dp; mostra i secondi da 1 s in poi |
| `FluidConfirmationRow(pending, onConfirm, strings, fillButtons)` | titolo + dettaglio + Conferma/Annulla |
| `FluidAssistantTextBar` | la capsula di scrittura: campo, microfono (solo se `micAvailable` e campo vuoto), invio (campo pieno), freccia circolare «nuova conversazione» (o `leading`) |
| `FluidBarIcon` | icona della barra: 40 dp di disegno, tocco a 48 |
| `FluidMicDot(level, speaking, busy)` | scala 1 + 0,5*livello parlando, 1,15 al lavoro, 1 a riposo |

`FluidAssistantTextBar`: l'invio ignora il testo vuoto **e** `busy`; il testo si salva con la pagina (`rememberSaveable`); il
fuoco arriva da solo solo con `autoFocus` (altrimenti niente tastiera). `leading = {}` toglie il tasto «nuova conversazione».

## L'aureola e il campo di luce

### `FluidAssistantHalo` (bordo alto)

**Cos'e'.** Quattro macchie di colore che scorrono lungo il bordo alto e sfumano in basso. Solo gradienti radiali: niente
shader, gira uguale sui device deboli.

```kotlin
FluidAssistantHalo(mood = FluidHaloMood.LISTENING, level = mic.level, height = 96.dp, colours = FluidHaloColours.fromTheme())
```

- *Ampiezza.* In ascolto `0,30 + level * 0,70`, altrimenti 0,55. **Attacco veloce, rilascio lento** (molla con rigidezza 1400
  in salita, 180 in discesa): con una molla sola la voce arrivava smussata e l'aureola sembrava indifferente.
- *Velocita'* (tempo dell'orologio): `WRITING` 1,6, `LISTENING` 1,1, `WORKING` 0,6, resto 0,3.
- *Colori.* Ogni umore ha quattro colori e **si sciolgono** l'uno nell'altro (animazione `FluidMotion.smooth()`): prima
  cambiavano di scatto. `ERROR` e' l'unico che prende il rosso del tema (con alpha 1, 0,7, 0,9, 0,55); `DONE`/`IDLE` tornano sull'accento.
- *Movimento ridotto.* (`LocalFluidMotionPolicy.current.reducedMotion`, cioe' scala animazioni a zero): il tempo non avanza,
  l'ampiezza salta al bersaglio, resta ferma e sfuma soltanto.
- Se la presenza e' sotto 0,001 non disegna e non compone: non costa niente da nascosta.
- Non ha un umore `IDLE` proprio: `IDLE` si legge come `DONE`.

### `FluidHaloField` (perimetro, capsula, aurora)

**Cos'e'.** Il campo di luce completo: un orologio, un pittore, tre usi. (1) un alone di sessione sul bordo dello schermo,
(2) l'alone della capsula del composer mentre ascolta (con `path` diverso), (3) l'aurora sullo sfondo di una chat.

```kotlin
FluidHaloField(mood = mood, level = { mic.level }, spec = FluidHaloFieldSpec(), staticWhenReduced = true)
```

Due firme: `level: Float` (letto in composizione, per chi lo raccoglie gia' dentro un composable piccolo) e `level: () -> Float`
(**legge solo il loop di frame**: il flusso a 50 Hz non ricompone nessuno). Passa la lambda se hai il flusso.

- *La regola che tiene insieme tutto.* Il tempo e l'ampiezza vivono in `FluidHaloClock`, due stati che si leggono **solo
  dentro il draw**: a 60 fps si invalida il disegno di un nodo e nessun fratello ricompone (la card con il Markdown, la lista).
  `rememberFluidHaloClock(running, speed, target, economy)` e' un loop `withFrameNanos`; `economy` lavora un fotogramma si' e uno no (~30 Hz).
- *Presenza.* Dissolvenza 400 ms in entrata, 600 in uscita. Sotto 0,004 il pittore non disegna: a presenza zero una macchia
  `Plus` costa quanto una piena e non si vede (il primo e l'ultimo fotogramma di una dissolvenza sono la trappola classica).
- *Pittore.* Macchie **equispaziate** sul perimetro (`i/n` + tempo + un ondeggiare dell'1,2%): la prima versione dava a ogni
  macchia una velocita' sua e si ammucchiavano in tre. Ogni macchia e' un gradiente radiale; poi un filo sul bordo.
- *Orologio.* `dt` tetto 1/15 s (tornando dal background il primo fotogramma non teletrasporta le macchie); attacco 0,35 e
  rilascio 0,08 per fotogramma a 60 Hz, scalati sul `dt` reale. Con `running = false` l'ampiezza decade a zero prima di fermarsi.
- *Movimento ridotto.* Il tempo sta fermo, l'ampiezza va al bersaglio. `staticWhenReduced = true` (di serie): fermo ma
  visibile (l'alone dice «l'assistente e' qui»); `false`: nascosto (una chat, dove e' decorazione).

**Il blend.** `BlendMode.Plus` somma luce: su sfondo scuro due macchie che si toccano fanno una luce piu' viva. Ma va verso
il bianco: su una pagina chiara satura e sparisce. `haloBlendForTheme()` sceglie `Plus` se la luminanza di `surface` e' sotto 0,5,
`SrcOver` altrimenti; `FluidHaloFieldSpec.blendMode` di serie e' `Plus`, passa tu il blend giusto su una pagina chiara.

**Numeri veri.** `FluidHaloFieldSpec`: 14 macchie, raggio 64 dp, `peakAlpha` 0,45, filo 2 dp a 0,25, angoli 44 dp. Giri del
perimetro al secondo (`perimeterSpeed()`): scrive 0,09 (un giro in ~11 s), ascolta 0,06, lavora 0,033, riposo 0,015 (si
muove: e' quel poco che distingue un alone da una cornice), fatto/errore 0,02. `amplitudeTarget`: ascolto `0,30 + 0,70*level`,
riposo 0,35, fatto 0,45, errore/lavora/scrive 0,6, nascosto 0. L'alone della capsula in ascolto (`GlowSpec` del composer)
ha 6 macchie da 28 dp. `FluidAuroraSpec`: 5 macchie larghe (raggio 0,35..0,55 della larghezza), alpha 0,20 chiaro / 0,30
scuro, periodi diversi per macchia (curve di Lissajous: mai in sincrono), il colore gira un ciclo ogni 40 s.

**Umore dallo stato.** `AssistantState.haloMood()`: `Listening` -> `LISTENING`; `Transcribing`, `Classifying`, `Working`,
`WaitingRateLimit`, `SwitchingProvider`, `AwaitingConfirmation` -> `WORKING`; `Answering` -> `WRITING`; `Done` -> `DONE`;
`Failed` -> `ERROR`; il resto (`Idle`, `HeardNothing`, `Cancelled`) -> `IDLE`. `FluidHaloColours.cycle(mood)` da' sei voci per umore
(`colours[i % 6]`); le alpha parziali (0,8, 0,9) rendono alcune macchie piu' deboli: un alone tutto uguale sembra stampato.

## L'aptica di stato: `FluidAssistantHapticTracker`

L'assistente e' la parte dell'app che si usa senza guardarla: si sente sotto il dito. Il conto e' una **macchina a stati
pura** (le regole che contano sono tutte «una volta sola»): l'overlay la usa da sola (`haptics = true`), chi non usa l'overlay
la chiama a mano e suona gli eventi con `rememberFluidHaptics()`.

| quando | evento |
|---|---|
| comincia un ascolto (una volta) | `ListenStart` |
| il primo parlato dell'ascolto (`onSpeaking(true)`, una volta per ascolto, e solo ascoltando) | `SpeechDetected` |
| l'ascolto finisce | `ListenEnd` |
| prima `Done` (anche se lo stato si riemette) | `ReplyReady` |
| `Failed` | `Error` |
| `Cancelled` | `Stop` |
| `SwitchingProvider` | `ProviderSwitched` |
| `WaitingRateLimit`, un tick per secondo diverso | `WaitTick` |
| conferma/rifiuto dal tocco sui tasti dell'overlay | `ActionConfirmed` / `Reject` |

Un ascolto che finisce in errore suona **prima la fine, poi l'errore**, in quest'ordine. Ogni evento passa poi da
`FluidHaptics`, che fonde i colpi a meno di 70 ms (una vibrazione per momento, vedi [../regole.md](../regole.md)).

## La voce

- **Lettura.** `FluidSpeaker` e' l'interfaccia (`speakNewSentences(fullText, final)`, `restart()`, `stop()`, `release()`):
  l'overlay non sa che voce sia, sa quando ricominciare, quando leggere il pezzo nuovo, quando zittirsi. Chi ha voci cloud
  le implementa e le passa. `FluidTtsSpeaker(context, locales)` e' la voce di sistema (gratis, offline); `locales` e' la
  lista in ordine di prova (di serie lingua del telefono, poi inglese): e' un parametro e non una scelta dell'engine perche'
  e' la lingua dell'app. `rememberFluidTtsSpeaker(enabled, locales)` non crea il motore finche' `enabled` e' falso (un
  `TextToSpeech` costa un servizio) e lo rilascia allo smontaggio.
- **Quali frasi.** `FluidSentences.next(plain, from, final)` e' **pura**: fine frase = `.`, `!`, `?`, `…` seguiti da spazio o fine,
  oppure un a capo. Restano da parte i pezzi non chiusi finche' lo stream non li chiude; a `final` anche l'ultimo, chiuso o no.
  Il testo e' `FluidMarkdownParser.plainText` con `FluidAssistantMarkdownOptions`: niente asterischi ne' marcatori `[[chip]]` letti.
- **Ascolto e trascrizione.** Nell'overlay e nel dock l'ascolto parte da `onAskVoice`; lo stato `Listening` porta `elapsedMillis`
  (il livello sta a parte, `MicLevel`), poi `Transcribing`. Il composer ha la sua riga della voce: [ai-ui-chat.md](ai-ui-chat.md).
- **Permesso.** Concesso-o-no si rilegge a `ON_RESUME`; il tocco senza permesso apre la barra di testo e lancia la richiesta.

## Errori: `FluidFailureReport` e `FluidFailureDetailsSheet`

**Cos'e'.** «Il servizio ha risposto con un errore» da solo non dice cosa fare; il codice e il messaggio del servizio si': a chi
li va a cercare e a chi li deve incollare in una segnalazione. Il resoconto e il foglio non hanno dominio.

```kotlin
FluidFailureReport(kind, provider, model = null, reason = null, httpCode = null, providerMessage = null, switches = emptyList(), technical = null)
FluidFailureReport.of(failed: AssistantState.Failed, switches: List<ProviderSwitch> = emptyList())
```

- `of(state)` ricava quello che l'engine sa **subito**: `kind`, `provider`, `reason`, `error.httpCode`, `error.providerMessage`,
  `error.message` (come `technical`). **Non** sa i cambi di servizio (`Failed` non li porta) ne' il `model`: l'app che ha
  una riga di registro (`AiRequestLog`) costruisce il resoconto con quelli, es. `FluidFailureReport.of(state, log.switches).copy(model = ...)`.
  Il tasto «Dettagli» della card chiama invece `of(state)` secco: per avere la catena nel foglio, tieni tu l'ultimo `report`
  e chiama `copy`.
- `summary(strings)`, `reasonLine(strings)` («Groq era al limite»), `chain` («Groq -> Gemini -> OpenRouter», solo se i servizi
  sono piu' d'uno), `chainReasons(strings)`, `asText(strings, header)` (il testo di «Copia»: righe semplici, pronto da incollare;
  il dettaglio tecnico non si ripete se uguale al messaggio del servizio).
- `FluidFailureDetailsSheet(report, onDismiss, strings)`: foglio di vetro dal host dei modali, «Copia» in fondo (con barra di navigazione
  rispettata). `report = null` lo chiude **con la sua animazione di uscita**; non toglierlo dalla composizione. Le righe senza dati
  (servizio, risposta, servizi provati) non si disegnano. `FluidFailureDetailsContent` e' il solo contenuto, per le anteprime.
- Il «Copia» del foglio passa `fluidCopyToClipboard` **senza** testo di conferma: sotto Android 13 non c'e' nessun avviso
  (il sistema lo mostra solo da 33).

## Dove guardare

| percorso (`engine-ai-ui/src/main/kotlin/dev/antigravity/fluidengine/aiui/`) | cosa c'e' |
|---|---|
| `overlay/FluidAssistantOverlay.kt` | overlay, `FluidAssistantActions`, `FluidAssistantCardOptions` (morph, gettone, aptica, voce) |
| `overlay/FluidAssistantOverlayState.kt` | `FluidOverlayMode`, `FluidAssistantOverlayState` |
| `overlay/FluidAssistantCard.kt` | card, `FluidStatusLine`, `fluidDefaultChipLabel` |
| `overlay/FluidAssistantDock.kt` | pannello ancorato, `FluidMicDot` |
| `overlay/FluidAssistantParts.kt`, `FluidAssistantTextBar.kt` | pillole, conferma, barra di scrittura |
| `overlay/FluidAssistantHaptics.kt` | `FluidAssistantHapticTracker` |
| `overlay/FluidSpeaker.kt` | `FluidSpeaker`, `FluidTtsSpeaker`, `FluidSentences` |
| `overlay/FluidFailureReport.kt`, `FluidFailureDetailsSheet.kt` | resoconto e foglio |
| `halo/FluidAssistantHalo.kt`, `halo/FluidHaloField.kt` | aureola, campo di luce, orologio, matematica pura |
