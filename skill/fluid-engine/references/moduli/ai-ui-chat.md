# engine-ai-ui: il kit della chat (bolle, composer, «in fondo», Markdown)

Cio' che serve per una **conversazione a pagina intera**: la lista che si apre in fondo e segue la risposta solo se la si
sta guardando, la bolla della domanda a destra, la risposta senza card, la coda, il composer di vetro con l'alone in ascolto,
le azioni sotto un messaggio. Il testo delle risposte e' `FluidMarkdown` di `engine-ui` (qui c'e' solo l'opzione che nasconde i
marcatori `[[chip]]`). Tutto e' **senza dominio**: i messaggi, le versioni, gli allegati veri, i plugin, la coda come
concetto di runtime, i saluti per ora del giorno sono dell'app; il kit mette gli **slot** dove metterli.

Modello mentale: la pagina tiene una `LazyColumn` di messaggi; `FluidFollowBottom` decide quando scendere; `FluidComposer` e'
un `padding` in fondo alla lista (la sua altezza vera, piu' tastiera e barra di navigazione, e' cio' che la regola del fondo
misura). Il resto sono mattoni da mettere negli item. Overlay e dock sono in [ai-ui-overlay.md](ai-ui-overlay.md); le
parole in [ai-ui-stringhe.md](ai-ui-stringhe.md); le firme complete in [../api/ai-ui.md](../api/ai-ui.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la domanda dell'utente, a destra | `FluidUserBubble` |
| la risposta (testo a tutta larghezza, stato, chip, azioni) | `FluidAssistantBubble` |
| la risposta in corso quando non c'e' ancora una riga salvata (chat nuova) | `FluidLiveBubble` |
| una domanda scritta mentre l'assistente lavora, in attesa | `FluidQueuedBubble` |
| la pagina vuota con nome, frase e suggerimenti | `FluidEmptyGreeting` |
| cosa dire di una risposta senza testo (fallita, fermata, vuota) | `fluidOutcomeLines` + `FluidBubbleOutcome` |
| la barra in fondo (campo, +, chip del modello, microfono/invio/stop) | `FluidComposer` |
| quale tasto mostrare a destra del composer | `fluidComposerAction(...)` |
| il testo scritto dal composer, anche scritto da fuori | `FluidComposerState` / `rememberFluidComposerState()` |
| l'alone mentre ascolta, e la riga con le parole che arrivano | `rememberFluidListeningGlow` + `FluidComposerVoiceLine` |
| icona sotto un messaggio (Copia, Condividi) | `FluidSmallAction`; con la parola accanto `FluidPillAction` |
| copiare o condividere una risposta | `fluidCopyToClipboard`, `fluidShareText` |
| una lista che si apre in fondo e segue la risposta | `rememberFluidChatScroll` + `FluidFollowBottom` |
| la risposta di una pagina di impostazioni o di un overlay | non qui: [ai-ui-overlay.md](ai-ui-overlay.md) |
| Markdown fuori da una risposta d'assistente (note, novita') | `FluidMarkdown` di `engine-ui`, **senza** `FluidAssistantMarkdownOptions` |

**Non cosi'**: non usare `FluidTextField` nel composer ("un pozzo grigio sotto il testo" e' giusto in un modulo e sbagliato
dentro una capsula di vetro): c'e' `FluidComposerField`, con le parole direttamente sul vetro.

## Montare una chat

```kotlin
val keys = rememberFluidChatScrollKeys()
val scroll = rememberFluidChatScroll(key = keys.keyFor(conversationId, liveShown = live != null))
val composer = rememberFluidComposerState()
val backdrop = rememberGlassBackdrop()

LazyColumn(state = scroll.list, contentPadding = PaddingValues(bottom = composerHeightPlusInsets)) {
  items(messages, key = { it.id }) { m ->
    if (m.fromUser) FluidUserBubble(m.text) else FluidAssistantBubble(m.text, contentKey = m.id, settled = true)
  }
  live?.let { item { FluidLiveBubble(state, pending, onConfirm = { id, yes -> vm.resolve(id, yes) }) } }
}
FluidFollowBottom(scroll)      // niente da disegnare: ascolta la lista e il dito
FluidComposer(
  value = composer.text, onValueChange = { composer.text = it },
  backdrop = backdrop,
  action = fluidComposerAction(composer.text.isNotBlank(), busy = state.isBusy, listening = state is AssistantState.Listening, speaking = speaking),
  onSend = { if (vm.send(composer.text)) { composer.sent(); scroll.revealLatest() } },
  onStop = { vm.cancel() }, onVoice = { vm.startListening() }, onStopListening = { vm.stopListening() }, onMute = { speaker.stop() },
)
```

La chiave della lista cambia quando si apre un'altra conversazione: la lista riparte e va in fondo.

## «In fondo»: `FluidFollowBottom`

**Cos'e'.** Le tre regole delle chat che si usano: (1) la conversazione **si apre gia' in fondo**, senza scorrere davanti a tutti i
messaggi, e tornando dalle impostazioni ritrova il punto; (2) mentre la risposta si forma la lista la segue **solo se si e'
in fondo** (a meno di 48 dp): chi e' risalito a rileggere non viene riportato giu', si riaggancia tornando lui in fondo;
(3) una domanda appena mandata porta in fondo **con la molla**, anche da risaliti.

**Comportamento.**

- **Nessun loop di fotogrammi**: si ascoltano la misura della lista e il dito con `snapshotFlow` (`layoutInfo`,
  `isScrollInProgress`), cosi' a riposo non si chiede nessun fotogramma e la batteria non se ne accorge.
- Attende la prima misura (al massimo 600 ms, poi mostra comunque) e porta in fondo **prima** di impostare `positioned`: finche'
  `positioned` e' falso la lista non si disegna (uno o due fotogrammi), cosi' non si vede la cima un attimo prima del salto.
- Il fondo e' `distanceToEnd()`: dal bordo basso dell'ultimo item (piu' il padding in fondo, cioe' il composer) al bordo basso
  della lista; `null` se l'ultimo item non e' in vista. `jumpToEnd()` e' il salto di una volta.
- Il fondo si insegue **solo quando si allontana da solo** (il contenuto cresce, il composer o la tastiera si alzano): un dito
  lasciato a venti pixel dal fondo resta dov'e'. Item nuovi lontani -> molla; crescita di pochi pixel -> inseguimento senza animazione.
- Con il movimento ridotto la molla diventa un salto (`toEnd(animated = !reducedMotion)`).
- Il dito ha la precedenza: uno scorrimento nostro interrotto dal dito non chiude l'ascolto del fondo (`guarded`).

**La decisione e' pura**: `FluidFollowRule.decide(follow, scrolling, distance, newItems, grew, revealed, slackPx)` ritorna
`FluidFollowDecision(follow, action)` con `FluidFollowAction.None`, `ScrollToEnd`, `ChaseGrowth`. Ordine dei casi: `revealed` ->
in fondo e riaggancia; `scrolling` -> decide il dito; `!follow` -> ci si riaggancia solo tornando in fondo; ultimo item non in
vista, o item nuovo oltre lo scarto -> `ScrollToEnd`; crescita -> `ChaseGrowth`. **La crescita del contenuto non stacca mai il
seguito**: e' la risposta che si allunga. E' la parte che sbaglia in silenzio (una lista che salta sotto il dito di chi
rilegge): per questo e' una funzione e si prova sul computer.

**`FluidChatScroll`** tiene `list` (il `LazyListState`), `positioned`, `follow` e `reveals`. `revealLatest()` (da chiamare quando
parte una domanda) riaggancia il seguito e incrementa `reveals`. Si salva con la pagina (`Saver`: posizione, `positioned`,
`follow`): tornando dalle impostazioni **a meta' risposta** il seguito riprende da solo.

**`FluidChatScrollKeys`** (con `rememberFluidChatScrollKeys()`): quale lista mostra la chat. Una per conversazione; l'eccezione e'
la chat nuova che nasce su disco mentre l'assistente le risponde: e' la stessa pagina, e cambiarle lista vorrebbe dire rifarla
da capo (e nasconderla per un fotogramma) proprio mentre la prima risposta arriva. Allora la conversazione appena nata
**eredita** la chiave della chat nuova. `keyFor(conversationId, liveShown)`: `liveShown` dice se c'e' una risposta viva.

**`FluidFollowAnswer(scroll, following)`**: lo stesso per una colonna con uno scorrimento solo (la card dell'overlay): ogni
120 ms torna in fondo se ci si era, scarto 12 dp (`FluidFollowRule.followColumn`).

**Trappole.**

- Il **padding in fondo** della lista deve essere l'altezza vera del composer + tastiera + barra: la regola misura contro quello.
  Un padding fisso fa "arrivare" l'ultima riga sotto il composer.
- Se non chiami `scroll.revealLatest()` all'invio, una domanda mandata da risaliti non riporta in fondo.
- `FluidFollowBottom` non disegna niente: va dichiarato **una volta** accanto alla lista, non dentro un item.

## Le bolle

### `FluidUserBubble`

**Cos'e'.** La domanda: una bolla `primaryContainer` a destra (angoli `FluidRadius.Card`), larga al massimo l'86% della riga. Non e'
una card a tutta larghezza: in una conversazione le due voci si distinguono per **forma** prima che per etichetta.

**Slot** (li scrive l'app): `leading` (fuori a sinistra, es. una matita «modifica» solo sull'ultima domanda), `attachments`
(le miniature vere: l'engine non conosce una libreria d'immagini), `below` (es. le frecce «‹ 2/3 ›» di versioni).

**Azioni.** `actions: List<FluidMessageAction>` (`label`, `icon`, `onClick`) sono le voci del menu di pressione lunga **e** le azioni
personalizzate di TalkBack (le stesse, una volta). `extraAccessibilityActions` aggiunge azioni solo per TalkBack. Senza `actions`
la pressione lunga non apre niente.

`style = FluidMessageStyle.Card` disegna invece una `FluidCard` con l'etichetta `userLabel`: per le pagine fatte di carte (un
elenco), non per una chat. `glass` mette la carta in vetro.

### `FluidAssistantBubble`

**Cos'e'.** La risposta: testo a tutta larghezza, **senza card**. Una risposta chiusa in un riquadro si legge come una scheda; una
conversazione vuole che le parole stiano sulla pagina.

**Firma essenziale** (i parametri che contano):

```kotlin
FluidAssistantBubble(
  text = answer,
  streaming = false,            // il testo sta arrivando: le parole nuove sfumano dentro
  settled = !streaming,         // il lavoro e' finito: azioni e chip si mostrano
  contentKey = message.id,      // identita' del messaggio (vedi sotto)
  status = "…", statusError = false,
  header = { /* la traccia dei passi, dominio */ },
  confirmation = pending, onConfirm = { id, yes -> … },
  outcome = FluidBubbleOutcome.None, busy = streaming,
  chips = chips, onChip = { … }, switchLine = "Ha risposto Gemini: …",
  actions = { FluidSmallAction(icon, "Copia", onClick = { … }) },
)
```

**Comportamento.**

- **La fine della risposta e' un movimento, non cinque.** Prima, al «fatto», la riga di stato spariva di colpo, la traccia
  compariva, i paragrafi diventavano Markdown, le azioni spuntavano e la misura animata si spegneva a meta'. Ora il testo e' lo stesso
  nodo (`FluidMarkdown` con `streaming` che torna falso), azioni e chip **sfumano dentro**, e la colonna anima la sua misura
  finche' la risposta e' viva (`animateSize`, di serie = `streaming`; molla `DampingChrome`/`ResponseSnappy`).
- `contentKey`: «e' nato adesso?» si decide **al primo passaggio per messaggio** (se al primo passaggio il testo c'era gia',
  la chat e' stata riaperta e il testo e' gia' letto: non si rivela). Una versione vecchia ritrovata con le frecce non si ri-rivela.
  Senza `contentKey` ogni ricomposizione ripartirebbe da zero. Il tuo `id` di messaggio va bene.
- I chip arrivano con il messaggio finale (`AnimatedVisibility` con `fadeIn`, nessuna con movimento ridotto) e sono `FluidChip`.
- `switchLine` sta sotto i chip: la riserva automatica e' accesa di default, un servizio che cambia in silenzio e' un modello
  che cambia in silenzio. Costruiscila con `FluidAssistantTexts.switchLine(switches, assistantStrings)`.
- Le `actions` stanno in una riga che compare **solo a `settled`**.
- `status` e' la riga sopra il testo (blu `primary`, rossa se `statusError`): di solito `FluidAssistantTexts.statusLine(state, assistantStrings)`.
- Con `style = Card`: una `FluidCard` con l'etichetta `assistantLabel`, evidenziata se l'esito e' `Failed`.

### `FluidBubbleOutcome` e `fluidOutcomeLines`

**Cos'e'.** Come finisce una risposta senza (o oltre) il suo testo, come **tabella pura**: la parte che si dimentica un caso (una
risposta fermata a meta' che sembra tronca per un guasto). Esiti: `None`, `Failed(message)`, `Cancelled`, `Empty`, `Interrupted`.

`fluidOutcomeLines(outcome, hasText, busy, settled, strings)` ritorna `FluidOutcomeLines(instead, after)`:

| esito | senza testo (`instead`) | con testo (`after`) |
|---|---|---|
| `Failed(msg)` | `msg` in rosso | testo resta, `msg` piccola e rossa sotto |
| `Cancelled` | «Fermata prima della risposta.» (grigio) | «Fermata qui.» solo a lavoro finito (`settled`) |
| `Empty` | «Nessuna risposta: il servizio non ha scritto niente.» (se non `busy`) | niente |
| `Interrupted` | «Interrotta: l'app si e' chiusa prima della risposta.» (se non `busy`) | niente |
| `None` | niente | niente |

Mentre lavora (`busy`) `Empty` e `Interrupted` non dicono nulla: lo dice la riga di stato. Il `message` di `Failed` viene di solito
da `FluidAssistantTexts.failure(...)` (vedi [ai-ui-stringhe.md](ai-ui-stringhe.md)).

### `FluidLiveBubble`

**Cos'e'.** La risposta in corso quando la conversazione e' nuova e non c'e' ancora un messaggio salvato: la domanda (dallo stato, finche'
la riga non arriva) e sotto riga di stato e testo. Tutto da `AssistantState`. Mostra anche il testo di `Done`, `Failed` e `Cancelled`:
fra la fine della risposta e il primo messaggio salvato passa un fotogramma, e in quel fotogramma il testo non deve sparire. La bolla della
domanda e' la stessa che `FluidUserBubble` disegna un attimo dopo, nello stesso posto: il cambio non si vede. Passa `contentKey = "live"`.

### `FluidQueuedBubble`

La domanda scritta mentre l'assistente lavorava: la bolla piu' tenue (alpha 0,6), con sotto «In coda» e una X (`FluidSmallAction`) che la
riporta nel campo. Il **concetto di coda e' dell'app** (un runtime che la tiene): qui c'e' solo come si vede. `attachmentCount > 0`
aggiunge «N allegati».

### `FluidEmptyGreeting`

Un segno (slot `mark`), il nome al centro, una frase, e dei suggerimenti toccabili (`FluidChip`). La frase e i suggerimenti li sceglie
l'app (sono dominio) e si scelgono **una volta per apertura**, non a ogni ricomposizione: e' esattamente il contrario di un saluto.
In una lista passa `Modifier.fillParentMaxHeight()`; `bottomInset` (96 dp) lascia spazio al composer.

## Azioni sotto un messaggio

- `FluidSmallAction(icon, description, onClick, enabled)`: **48 dp** di bersaglio (erano 32, poi 44: il minimo di Android per un dito),
  icona da 18 dp leggibile; spenta, alpha 0,38 e il tocco non passa. Senza aptica propria.
- `FluidPillAction(icon, label, onClick, enabled)`: l'azione con la parola («Riprova», «Dettagli»): sotto una risposta fallita l'icona da sola non
  basta. La pillola e' bassa (32 dp), il bersaglio resta 48.
- `fluidCopyToClipboard(context, text, label, confirmation)`: da Android 13 la conferma la mostra il sistema; **sotto il 33** un tocco
  sembrava non fare niente, quindi mostra un `Toast` con `confirmation` **solo se lo passi**. `label` di serie e' «Assistente»
  (non tradotto: passalo).
- `fluidShareText(context, markdown, chooserTitle)`: condivide come testo semplice (`plainText` con elenchi e codice, senza marcatori `[[chip]]`).

## Il composer

**Cos'e'.** La barra in fondo, nella forma delle chat che si usano: il campo sopra; sotto la riga con `leading` (il «+»), `chips` (il chip del
modello) e il tasto a destra. Sopra la barra, `pills` (allegati, plugin, la strada per le impostazioni se manca una chiave).

**Firma essenziale.**

```kotlin
FluidComposer(
  value: String, onValueChange: (String) -> Unit,
  backdrop: GlassBackdropState,
  action: FluidComposerAction,          // di solito fluidComposerAction(...)
  onSend: () -> Unit,
  placeholder: String = FluidChatStrings().composerPlaceholder,
  strings: FluidChatStrings = FluidChatStrings(),
  onStop, onVoice, onStopListening, onMute,
  pills, leading: (RowScope.(controlBackdrop: GlassBackdropState) -> Unit)?, chips, voiceLine,
  glow: FluidListeningGlow? = null,
  maxLines = 6, requestMicPermission = true, onBoundsChange, resampleIntervalMillis = 0L,
  voiceActive: Boolean = action == FluidComposerAction.StopListening,
)
```

Tutti i parametri: [../api/ai-ui.md](../api/ai-ui.md).

**Uso** (da `FluidAiUiDemo`, la pagina di prova):

```kotlin
val composer = rememberFluidComposerState()
FluidComposer(
  value = composer.text, onValueChange = { composer.text = it },
  backdrop = backdrop,
  action = fluidComposerAction(hasInput = composer.text.isNotBlank(), busy = false, listening = false, speaking = false),
  onSend = { composer.sent() },
  chips = { FluidComposerChip(label = "Modello · Auto", onClick = {}) },
)
```

**Comportamento.**

- **Il campo e' sempre usabile.** Mentre l'assistente lavora si scrive e si manda lo stesso: la domanda va in coda e parte quando ha finito
  (`StopAndQueue` tiene lo stop **e** l'invio). `onSend` svuota il campo (`composer.sent()`) **solo se la domanda e' partita davvero**, e lo
  lascia se non e' potuta partire (assistente spento, senza chiavi).
- **Il tasto a destra** e' `fluidComposerAction(hasInput, busy, listening, speaking, micAvailable = true)`, pura. Ordine: ascolta > lavora con
  qualcosa di scritto > lavora > legge ad alta voce e campo vuoto > campo vuoto con microfono > invio. Cioe': `StopListening` (sempre, qualunque
  altra cosa), `StopAndQueue`, `Stop`, `Mute`, `Voice`, `Send`. Con testo scritto «Zitta» non prende il posto dell'invio.
- **Il vetro.** La capsula rifrange `backdrop` (fondale e lista insieme) e **esporta** il proprio materiale finito: i tasti tondi che ci stanno
  sopra piegano la capsula e non la pagina tre strati piu' giu'. Prima campionavano la pagina direttamente e ogni tasto era un buco nitido. `leading`
  riceve quel fondale (`controlBackdrop`) per i suoi tasti. Ruolo `GlassRole.Floating`, forma `ComposerShape` (raggio `ComposerCorner` 26 dp),
  film all'80% di quello dei `floatingTint` (era un velo bianco che copriva il vetro).
- **Slot che restano all'app**: allegati (i launcher e le miniature sono suoi), i menu del «+» e del modello (si aprono con `FluidGlassModalPortal`,
  ancorati a `onBoundsChange`: il rettangolo della capsula in coordinate di radice), la modifica di un messaggio, i plugin.
- **Il microfono.** Il tasto `Voice` chiama `onVoice` se il permesso c'e' (o se `requestMicPermission = false`), altrimenti lancia la richiesta e, se
  concessa, chiama `onVoice`. Se `micAvailable` e' falso (`fluidComposerAction`), il campo vuoto offre `Send`, non il microfono.
- **`voiceActive`** (3.0.4): la riga della voce (`voiceLine`) resta al posto del campo. Di serie solo mentre ascolta; durante la **trascrizione** il tasto
  e' lo stop dell'assistente e il campo tornava, mentre la riga ha la sua scritta «Trascrivo…». Passa `state.isVoiceActive()` (vero in `Listening` e
  `Transcribing`) per tenerla anche li'. Senza `voiceLine` il campo resta.
- `resampleIntervalMillis`: ogni quanto, al massimo, la capsula rifa' la sua cattura; zero e' «quando serve». Sopra un fondale animato lo alza chi anima il fondale.

**Trappole.**

- **`placeholder` di serie e' `FluidChatStrings().composerPlaceholder`**, cioe' il default **italiano**, anche se passi `strings = FluidChatStrings.english()`:
  passa `placeholder = strings.composerPlaceholder` (e `composerBusyPlaceholder` mentre lavora: nessun componente lo usa da solo).
- Con `micAvailable = false` e campo vuoto il tasto e' `Send`: `onSend` deve ignorare il testo vuoto.
- Il permesso del microfono nel composer si legge **una volta** (`remember`), non a ogni `ON_RESUME`: va bene perche' il lancio con permesso gia' concesso
  ritorna subito, ma non aspettarti una riga che si aggiorna da sola.
- `FluidComposerState.fill(value)` **aggiunge** a quello gia' scritto (separato da una riga vuota; uno spazio non e' testo), non lo cancella: un suggerimento
  toccato mentre lavora finisce nel campo senza perdere quello che si stava scrivendo. Si salva con la pagina (una rotazione non lo perde).
- `FluidComposerPill(removeDescription = "Togli")` ha il default italiano **non tradotto**: passalo.

### `FluidComposerState`, `fluidComposerAction`

`FluidComposerState(text)`: `text`, `fill(value)`, `sent()`, `Saver`. Sta **fuori** dal composer perche' anche la pagina ci scrive (un suggerimento, una domanda
tolta dalla coda che torna qui) e perche' un'anteprima possa costruire un composer con una riga sola. Lo «stato di modifica» («modifica e rinvia») e' dominio: l'app lo
tiene accanto.

### L'alone in ascolto e la riga della voce

- `rememberFluidListeningGlow(listening, transcribing, level: () -> Float, colours, blend)` costruisce `FluidListeningGlow`: l'orologio segue il microfono (letto **nel
  loop di frame**, non in composizione: lo `StateFlow` a 50 Hz non ricompone nessuno) e la presenza sfuma dentro (220 ms) e fuori (260 ms). Mentre trascrive
  l'alone **rallenta e si assesta** (ampiezza 0,30, un terzo del passo): la voce e' finita, si aspetta. Nel silenzio l'ampiezza resta a 0,25: fra due sillabe non
  si spegne.
- `Modifier.fluidListeningGlow(glow, shape)` (che `FluidComposer` applica da solo se passi `glow`) va su un contenitore **esterno** al pannello di vetro, che non
  clippa: dentro la capsula le macchie le copre il vetro, fuori sbordano di ~20 dp e diventano l'alone; l'anello (1,5 dp, colori che girano un giro ogni 8 s,
  alpha da 0,25 a 0,70 con la voce) e' disegnato **dopo** `drawContent`, sul filo del bordo. **Non** si scala ne' si sposta il vetro (scalare un layer di vetro
  scala anche l'immagine campionata): la capsula resta ferma, e' la luce a muoversi. Sotto la soglia di presenza non si disegna niente.
- Movimento ridotto: l'orologio non avanza e l'ampiezza va al bersaglio (stesso `rememberFluidHaloClock` del campo di luce).
- `FluidComposerVoiceLine(partial, transcribing, amplitude, onTap, strings)` e' la riga al posto del campo: un punto che pulsa con la voce e le parole riconosciute che
  **arrivano**, non che si sostituiscono. Il riconoscitore riemette l'intera frase due-cinque volte al secondo; qui va a `FluidRevealingText`, che tiene ferme le parole
  gia' viste e sfuma dentro solo le nuove (e riscrive in fretta la coda quando il riconoscitore cambia idea). Senza parole mostra «Ti ascolto…» (`strings.listening`), in
  trascrizione «Trascrivo…» (`strings.transcribing`) piu' tenue. E' alta quanto il campo a una riga (`VoiceLineHeight` 48 dp: la capsula non cambia misura passando
  dalla penna alla voce; a 44 dp il bordo saltava di 4 dp). Il transcript sta in una finestra alta una riga e mezza che segue la fine; un tocco sulla riga **annulla l'ascolto**
  (`onTap`). **Le parole stanno in `FluidAssistantStrings`, non in `FluidChatStrings`.**
- `AssistantState.isVoiceActive()`: `Listening` o `Transcribing`.

### I pezzi

`FluidComposerField` (campo sul vetro, `ImeAction.Send`), `FluidComposerChip(label, onClick)` (30 dp, con freccia), `FluidComposerPill(icon, label, onClick, accent, removable,
trailingLink)` (toccarla la toglie; `removable = false` e' un collegamento e al posto della X c'e' una freccia), `FluidGlassRoundButton(icon, description, backdrop, tint, onClick)`
(40 dp, lo stesso disco per «+», microfono, invio, stop).

## Il Markdown dell'assistente

`FluidAssistantMarkdownOptions = FluidMarkdownOptions(hideChipMarkers = true)`: i marcatori `[[chip]]` che il modello scrive per i chip sotto la risposta spariscono dal testo (a fine
risposta diventano chip, e mostrarli mentre arrivano non dice niente). `FluidMarkdown` di `engine-ui` di serie **non** li tocca (un testo qualunque puo' usare le parentesi doppie per se').
Bolle, card, dock, voce e condivisione lo passano da soli; chi disegna una risposta per conto suo (una cronologia, un'anteprima) lo passa come `options`.

## Cosa e' verificato dai test

`FluidChatLogicTest` (23 test, JVM): la regola del fondo per ogni caso (domanda nuova riporta giu' anche da risaliti, il dito decide mentre scorre, chi rilegge non e' trascinato giu',
riaggancio tornando in fondo, molla per item lontani e inseguimento per crescite piccole, dito lasciato vicino al fondo resta fermo), la card che segue la risposta, le chiavi della lista (una per
conversazione, la chat che nasce su disco **eredita**, una chat nuova senza domanda in corso non ruba la chiave), il tasto del composer per ogni combinazione (incl. «ascoltando e' sempre lo stop
dell'ascolto» su tutte le 8 combinazioni e «senza microfono il campo vuoto non offre la voce»), `fill`/`sent`, la tabella degli esiti. `FluidEnglishStringsTest`: la riga della voce resta anche mentre
trascrive (`fluidComposerShowsVoiceLine`). **Non verificati** (si guardano in mano): il vetro del composer, l'alone, il movimento delle bolle, il fondo su una lista vera.

## Dove guardare

| file (`.../aiui/chat/`) | cosa c'e' |
|---|---|
| `FluidFollowBottom.kt` | `FluidFollowRule`, `FluidChatScroll`, `FluidChatScrollKeys`, `FluidFollowBottom`, `FluidFollowAnswer`, `distanceToEnd`, `jumpToEnd` |
| `FluidChatBubbles.kt` | bolle, esiti (`FluidBubbleOutcome`, `fluidOutcomeLines`), `FluidLiveBubble`, `FluidEmptyGreeting`, `FluidMessageStyle` |
| `FluidChatActions.kt` | `FluidSmallAction`, `FluidPillAction`, copia e condividi |
| `FluidComposer.kt` | composer, stato, tasto, alone, riga della voce, chip e pillole |
| `../markdown/FluidAssistantMarkdown.kt` | `FluidAssistantMarkdownOptions` |
