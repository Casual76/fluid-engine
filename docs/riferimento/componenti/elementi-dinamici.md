<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/elementi-dinamici.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Elementi dinamici

Gli **elementi dinamici** (piano «iOS in Android», fase F6B) sono le parti dell'interfaccia che non sono della pagina e
che **si trasformano**: la barra che diventa quella della selezione, il tasto che lavora e dice com'è andata, il tasto
che diventa «Annulla», l'avviso che nasce da un tasto, l'elemento che diventa una pagina. Prima ogni app se li
costruiva da sé («e maluccio», Alessio); ora ognuno ha dei **preset** pronti e una forma dichiarativa per farne uno su
misura, e l'app non scrive una riga di movimento.

Ogni elemento è un **viaggio d'oro**: nasce da qualcosa e ci torna. Non c'è mai un fotogramma in cui la faccia di
partenza e quella d'arrivo si leggono insieme, e il contenuto che cambia identità entra sfocato (lo scambio di R7).
I tempi sono dei profili dell'engine (`FluidMotion`, `FluidActionProfile`, `FluidActionGlyphProfile`, `FluidPageMotion`),
ognuno con la sua fonte nel KDoc.

Questa scheda è l'indice della famiglia e dice, per ogni elemento, cosa fa, quando usarlo, l'API con un esempio, le
scelte di Alessio, cosa fa da solo l'engine e cosa resta all'app, e dove si vede in galleria. I dettagli stanno nelle
schede di casa: la barra in [toolbar.md](toolbar.md), la tab bar in [barre.md](barre.md), gli avvisi in
[notifiche.md](notifiche.md), le pagine in [pagine.md](pagine.md). Le firme: [../api/barre.md](../api/barre.md),
[../api/menu-modali.md](../api/menu-modali.md), [../api/pagine.md](../api/pagine.md).

La prima parte (F6B.12) è la selezione, i tasti con esito, «Annulla», l'avviso dal tasto e l'elemento che diventa
pagina; la seconda (F6B.13) il campo coi preset e «trova», il composer, le pillole dei filtri, la barra sopra la
tastiera, la pillola di attività, il tasto diviso e il titolo che diventa selettore. La pillola di attività, il tasto
diviso, il titolo selettore e la regola dello stesso posto sono **provvisori**: Alessio li deve ancora confermare.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| una lista in cui si scelgono più righe, con le azioni in basso al posto della tab bar | `toolbar { if (selecting) selection(state, count, onSelectAll, onDone, actions = FluidSelectionActions.mail(…)) else selectButton { selecting = true } }` |
| le azioni della selezione in alto (una pagina senza barra in basso) o in una capsula sulla griglia | `selection(…, placement = FluidSelectionBarPlacement.Top)` o `Floating` |
| lo spazio a sinistra della riga che ospita il segno di selezione, senza che la lista salti | `FluidListRow(leading = { FluidSelectionLeading(selecting, selected) { piastrella } }, leadingTile = false)` |
| un tasto che mostra il lavoro e l'esito (invia, salva, copia, scarica, carica, preferito) | `FluidActionButton(FluidActionPreset.Save, runner.state, onClick = { runner.run { salva() } })` con `rememberFluidActionRunner` |
| lo stesso tasto nella barra o nella capsula | `button(…, action = FluidActionPreset.Send, state = …)`, `primary(…)`, `confirm(…)`, `FluidCapsuleAction(…, action, state)` |
| un tasto con esito con icone e parole sue | `FluidActionPreset.custom(idle, success, failure, words)` |
| «Annulla invio» dopo che la pagina si è chiusa, senza un avviso | `primary(id, …, undo = FluidUndoCountdown(rememberFluidUndoCountdownState(timing), onUndo))` |
| un «Annulla» temporaneo che compare nella barra e poi esce | `undoCountdown(id, FluidUndoCountdown(…))` |
| un avviso con testo che nasce dal tasto (non è di serie) | `host.show(FluidNotification.undo(title, seconds, onUndo, source = rememberFluidToolbarTransitionSource(id)))` |
| un mini lettore, una miniatura, una pillola o un tasto della barra che diventa la pagina | `composable(route, zoom = FluidZoomStyle.Rise)` e `Modifier.fluidZoomSource(…)` ([pagine.md](pagine.md)) |
| un campo di ricerca, di messaggio, di nota o per l'assistente, coi suoi tasti e il suo invio | `FluidScreen(search = FluidSearchSpec.search(…))`, `.message(…)`, `.note(…)`, `.assistant(…)` |
| la ricerca in alto che inghiotte i tasti accanto e li restituisce | `FluidSearchSpec.search(…, placement = TopBar, behavior = CollapseToButton, expansion = StayAtTop)` |
| cercare una parola nel testo della pagina, con «3/12» sopra la tastiera | `FluidSearchSpec.findInPage(…)` con `rememberFluidFindState(testo)` |
| un campo che cresce di riga in riga col «+», gli allegati, il microfono che diventa «Invia» | `FluidSearchSpec.message(…, composer = FluidComposerSlots(leading, attachments, trailing, voiceLine))` |
| il campo dell'assistente con l'alone | `fluidAssistantField(state, action, onSend, …)` (engine-ai-ui) |
| una fila di filtri che si raccoglie in «Filtri · N» se non ci sta | `FluidFilterChips(filters, selection, onChange, mode)` |
| i suggerimenti sotto il campo, coi recenti | `FluidSearchSuggestions(query, suggestions, onSuggestionClick, onRemoveRecent = …)` |
| una barra di comandi sopra la tastiera, per campo | `toolbar { keyboard(context = campo) { fieldNavigation(…); suggestions(…); history(…) } }` |
| un lavoro lungo che si vede in ogni barra, al posto del titolo | `FluidActivityHost(state)` alla radice e `state.publish(FluidActivity.sync(…))` |
| un'azione con le sue varianti in un vetro solo | `split(id, text, onClick, menu = { … })`, `FluidSplitButton(…)` |
| il titolo che apre la scelta dell'account o della località | `title(mode, menu = { … })` o `title(mode, panel = { … })` |

## La selezione

**Cosa fa.** Una pagina entra in selezione scrivendo `selection { }` nella sua barra al posto della barra di «sfoglia»,
con un `if`. La barra fa il resto con il suo modello: «Seleziona» e «Fine» sono lo stesso tasto (lo stesso vetro che si
stringe e cambia scritta sfocandola); il conteggio («3 selezionati») prende il posto del titolo agganciato ed è un'area
viva per TalkBack; «Seleziona tutto» sta a sinistra al posto dell'indietro e, con tutto scelto, diventa «Deseleziona»;
le azioni scendono in basso, con priorità e trabocco. Con una tab bar, **la pillola delle schede diventa la capsula
delle azioni** nello stesso contenitore (F6B.2): le schede escono sfocate, le azioni entrano sfocate, la larghezza
scorre; «Fine» o il back rifanno il viaggio inverso e la tab bar torna com'era, con la scheda scelta, i segni e la
posizione della lista.

**Quando usarla.** Ogni lista in cui si sceglie più di una riga per un'azione di massa: posta, note, foto, file. Non per
una scelta singola (quella è un tocco) né per un modulo con caselle (quello è un elenco di interruttori).

**L'API.**

```kotlin
val selection = rememberFluidSelectionState<Long>()
var selecting by rememberSaveable { mutableStateOf(false) }
FluidScreen(title = "Posta in arrivo", onBack = back, toolbar = {
  if (selecting) {
    selection(
      state = selection,
      count = selection.countIn(total),                      // il numero viene dall'app (anche su liste paginate)
      onSelectAll = { selection.selectAll(snapshot = query) },
      onDone = { selecting = false },
      actions = FluidSelectionActions.mail(onDelete = elimina, onArchive = archivia, onMove = sposta, onMarkRead = leggi),
    )
  } else {
    selectButton(onClick = { selecting = true })
  }
}) {
  items(messages, key = { it.id }) { m ->
    val picked = selecting && selection.isSelected(m.id)
    FluidListRow(m.from, m.subject, leadingTile = false, selected = picked,
      leading = { FluidSelectionLeading(selecting, picked) { FluidToneTile(FluidTone.Primary) { Text(m.from.take(1)) } } },
      onClick = { if (selecting) selection.toggle(m.id) else apri(m.id) })
  }
}
```

- **Le posizioni** (`FluidSelectionBarPlacement`, non un enum): `ReplacingTabBar` (di serie: al posto della tab bar, o
  nella barra in basso della pagina se una tab bar non c'è), `AboveTabBar`, `Top` (accanto a «Fine», per le pagine senza
  barra in basso: le azioni stanno accanto a «Fine» se c'è posto, se no in «Altro»; «Seleziona tutto» sta al posto dell'indietro e resta in barra finché c'è
  posto), `Floating` (una capsula sulla griglia, che non le lascia spazio).
- **I preset** (`FluidSelectionActions`): `mail`, `gallery`, `files`, `deleteOnly`. Ricevono le callback e scrivono
  azioni, priorità e trabocco; un'azione con la callback null non c'è; si spengono da sole a zero elementi.
- **Su misura**: le azioni sono la DSL della barra. Un'azione si spegne a zero con `enabled = selectionHasItems`;
  un'azione distruttiva in una selezione in basso passa `placement = FluidToolbarPlacement.BottomBar`.
- **Le parole**: `FluidSelectionLabels` (il conteggio, «Seleziona tutto», «Fine») e `FluidSelectionActionLabels`.
- **Le identità**: `FluidSelectionToolbarIds` (`Toggle`, `SelectAll`, `Count`, `Actions`).

**Le scelte di Alessio.**
- Un vetro solo: la pillola delle schede diventa la capsula delle azioni, il contenuto si incrocia sfocato, e alla fine
  torna com'era (21 novembre). Dopo i video: va bene con l'attimo di vetro vuoto fra le due facce.
- Sul tablet con la barra laterale la barra delle azioni sta nel pannello della lista, come in Notes. Rail e barra
  laterale non si trasformano.

**Cosa fa da solo l'engine.**
- La trasformazione della barra in alto e della tab bar, con i tempi del gruppo che cambia larghezza (535 ms, R4 B2, per
  analogia: Apple non ha una clip della selezione).
- **Il back esce dalla selezione** e non dalla pagina (tasto, gesto del sistema, dito dal bordo), e niente cambia finché il
  gesto indietro non è confermato (D1): un gesto annullato lascia la selezione com'era.
- «Seleziona tutto» su una lista paginata resta la forma «tutto tranne» dello stato: l'engine non carica e non conta
  righe.
- La riga non salta: `FluidSelectionLeading` tiene la misura dello spazio `leading`.

**Cosa resta all'app.** Lo stato «sto selezionando» (`selecting`), il totale per il conteggio, le azioni vere e cosa
fanno, l'ordine in cui si compongono i `BackHandler` dei suoi menu.

**In galleria** (Chrome → «Elementi dinamici», `--es gallery.page DynamicElements`): `--es dynamic.scene
selezione-posta` (Mail: la tab bar che diventa le azioni, un solo «Annulla» per l'archiviazione in blocco; sul tablet
rail e barra laterale), `selezione-note` (Notes: «Seleziona tutto» su 120 note caricate a pagine, le azioni in alto e in «Altro»),
`selezione-foto` (Gallery: la capsula flottante), `selezione-file` (File: la barra in basso senza tab bar). Le scene
sono in `sample/…/dynamic/SelectionScenes.kt`.

## I tasti con esito

**Cosa fa.** Un tasto che, toccato, **lavora** e poi dice com'è andata, con un glifo animato secondo il significato
dell'icona. Invia: l'aeroplanino si carica e, a invio riuscito, parte; a fallimento torna e diventa «Riprova». Salva:
l'anello sottile attorno all'icona, poi la spunta si disegna lungo il suo verso mentre l'icona esce
sfocata; con un lavoro breve (niente anello) la spunta entra con lo scambio sfocato di R7, sovrapposta all'icona che esce:
mai un attimo di vetro vuoto (scelta di Alessio del 22 novembre 2026). Copia: la spunta per un secondo, senza
lavoro. Scarica e Carica: la percentuale nell'anello, con pausa e ripresa; a riuscita la freccia cade e diventa spunta.
Preferito: si riempie subito; se l'app rifiuta torna com'era con una scossa. L'esito negativo ha la tinta d'errore e
**trema solo il contenuto**: il vetro resta fermo e i vicini non saltano.

**Quando usarlo.** Ogni azione che può durare o non riuscire, e il cui esito l'utente deve vedere sul posto: invio,
salvataggio, scaricamento. Non per un'azione che apre qualcosa (lì l'esito è la cosa che si apre).

**L'API.**

```kotlin
val save = rememberFluidActionRunner(FluidActionPreset.Save)
FluidActionButton(FluidActionPreset.Save, save.state, onClick = { save.run { repository.salva(nota) } })

// Nella barra: lo stesso stato, la stessa faccia.
FluidScreen(title = "Nuova mail", toolbar = {
  primary("invia", FluidIcons.Send, "Invia", onClick = { send.run { api.invia(bozza) } },
    action = FluidActionPreset.Send, state = send.state)
}) { … }

// L'avanzamento e il fallimento, da dentro il lavoro.
download.run {
  for (chunk in chunks) { scrivi(chunk); progress = chunk.fine / totale }
  if (!verificato) fail()
}
```

- **Lo stato** (`FluidActionState`): `Idle`, `Working(progress, paused)`, `Succeeded`, `Failed`, `Cancelled`. È dell'app:
  il tasto non ha logica, quindi rotazione e ricomposizione non lo azzerano.
- **L'esecutore** facoltativo (`rememberFluidActionRunner` in un composable, `FluidActionRunner(viewModelScope, …)` in un
  ViewModel): sotto i 300 ms (`FluidActionDefaults.MinWorking`) si vede solo l'esito; il tasto è spento mentre lavora;
  l'esito resta per la tenuta (`FluidActionHold.Preset`, `For(durata)`, `UntilReset`); dopo la ricreazione torna col
  tempo che gli restava.
- **I preset** (`FluidActionPreset`): `Send`, `Save`, `Copy`, `Download`, `Upload`, `Favorite` e `favorite(selected)`;
  **su misura** `FluidActionPreset.custom(idle, success, failure, words, working, successHold, failureHold, showsWork,
  percentInRing)`, con lo scambio sfocato di R7 al posto del glifo.
- **La pausa** è dell'app: `FluidActionState.Working(progress, paused = true)` ferma la percentuale e tiene il tasto spento.
  Con l'esecutore, si mette sopra il suo stato (la scena della galleria lo fa così).
- **Le parole** per TalkBack: `LocalFluidActionLabels` (`FluidActionLabels`, `FluidActionWords`), di serie in italiano.

**Le scelte di Alessio.**
- I preset della prima versione: invia, salva, copia, scarica/carica, preferito (11 novembre).
- L'esito negativo fa tremare solo il contenuto, con la tinta d'errore dentro il vetro (21 novembre).
- Il lavoro dei preset senza un gesto proprio (Salva, Carica) è un anello sottile attorno all'icona, dentro il vetro.
- «Salva» riuscito: solo la spunta a taglia d'icona, niente disco pieno; il tasto resta di vetro (dopo i video).
- La spunta entra mentre l'icona esce, senza un attimo di vetro vuoto fra le due (22 novembre, sulle riprese di F6B.12).

**Cosa fa da solo l'engine.** I glifi animati, le varianti per il movimento ridotto (l'esito è un cambio d'icona senza
moto), la larghezza fissa (la più larga fra gli stati: il vetro non respira e i vicini non saltano), l'area viva che
dice «Invio in corso…», «Inviato», «Invio non riuscito», l'aptica di un evento per momento. Anello e percentuale sono
contenuto: non ricatturano il vetro.

**Cosa resta all'app.** Il lavoro vero e cosa vuol dire «riuscito»; la pausa e la ripresa di un trasferimento; il
preferito vero (`selected`); un lavoro che deve sopravvivere alla rotazione va in un ViewModel.

**In galleria**: `--es dynamic.scene tasti`. In alto «Invia» nella barra del compositore di Mail; sotto i sei preset e
«Sincronizza» su misura; i chip «Riesce», «Non riesce» e «Lavoro breve» scelgono come finisce il lavoro; «Pausa» e
«Annulla» comandano lo scaricamento. La scena è `sample/…/dynamic/ActionButtonScenes.kt`.

## «Invia» e il contatore «Annulla»

**Cosa fa.** Dopo l'invio la pagina del compositore si chiude, e **un tasto della pagina sotto diventa «Annulla»**: lo
stesso vetro, con la stessa identità, si riempie da un capo all'altro col tempo che resta. A tempo scaduto il contatore
sparisce e il tasto torna com'era. «Annulla» riapre la bozza.

**Quando usarlo.** Ogni azione che si può ancora ritirare per qualche secondo: l'invio di una mail, un'archiviazione in
blocco. È il riscontro di serie dell'engine per «Annulla invio».

**L'API.**

```kotlin
var startedAt by rememberSaveable { mutableLongStateOf(-1L) }            // la finestra è dell'app
val timing = remember(startedAt) { if (startedAt < 0) null else FluidNotificationCountdown(8_000, startedAt) }
val countdown = timing?.let { rememberFluidUndoCountdownState(it) }
FluidScreen(title = "Posta in arrivo", toolbar = {
  primary("nuova", FluidIcons.Add, "Nuova mail", onClick = scrivi,
    undo = countdown?.let { FluidUndoCountdown(it, onUndo = { riapriBozza() }, accessibilityLabel = "Annulla invio") })
}) { … }
```

- `FluidUndoCountdownState(timing)` dice se la finestra è aperta (`isActive`) e accetta **un solo** annullamento
  (`undo`); `rememberFluidUndoCountdownState` ne salva il consumo.
- `FluidUndoCountdown(state, onUndo, label, accessibilityLabel, remainingLabel)` è la faccia: sta su `button`, `primary`,
  `FluidCapsuleAction` e `FluidActionButton` (`undo = …`).
- `undoCountdown(id, countdown)` è un tasto **temporaneo** col suo vetro, che alla fine esce con la lente del piano: un
  «Annulla» che prima non c'era (la scena della posta lo usa per l'archiviazione in blocco).

**Le scelte di Alessio** (dopo i video di F6B.4, 21 novembre): niente avviso grande di serie, «troppo invasivo». Dopo
l'invio la pagina si chiude (lo fa l'app); **quale tasto** diventa «Annulla» lo decide l'app (in Pampa Mail sarà «Nuova
mail»); a tempo scaduto il contatore sparisce e basta; «Annulla» riapre il compositore e l'app decide come (una pagina che
entra normale, di serie, oppure il tasto che diventa la pagina).

**Cosa fa da solo l'engine.** Il riempimento (letto nel disegno, a secondi interi col movimento ridotto), lo scambio
sfocato fra le due facce, la larghezza misurata per faccia, l'area viva che dice «Annulla invio, 6 secondi», il tocco
preso una volta sola sul thread UI, la scadenza seguita da sé. La sorgente del tasto
(`rememberFluidToolbarTransitionSource(id)`) resta la stessa anche mentre il tasto dice «Annulla».

**Cosa resta all'app.** L'operazione e la sua scadenza vera (l'istante di partenza, la durata, sull'orologio monotono),
cosa fa «Annulla», e chiudere la pagina dopo l'invio.

**In galleria**: `--es dynamic.scene invia` (la scena di F6B.4, `sample/…/catalog/NotificationSendDemo.kt`, che si apre
anche dalla voce «Avvisi con azione»); l'archiviazione in blocco di `selezione-posta`.

## L'avviso dal tasto (non di serie)

**Cosa fa.** Un avviso con testo che **nasce dal vetro del tasto** che l'ha pubblicato: la faccia del tasto si spegne
mentre la goccia si raccoglie, la goccia va al punto di posa vicino al tasto (sotto la barra in alto, sopra quella in
basso) e la card si apre attorno a lei col testo sfocato; all'uscita torna nel tasto se c'è ancora, altrimenti scivola via
come le notifiche di sempre. Il preset con «Annulla» ha attorno ad «Annulla» un anello che si svuota.

**Quando usarlo.** Quando l'app vuole davvero un avviso con testo legato a un tasto: un esito da spiegare in una frase
(«Eliminazione non riuscita: la conversazione è condivisa»). Per «Annulla invio» **no**: lì c'è il contatore nel tasto.

**L'API.** È `FluidNotification` con la sorgente in coda (`source`), più tre preset:

```kotlin
val host = LocalFluidNotificationHostState.current
val archive = rememberFluidToolbarTransitionSource("archivia")          // il tasto «archivia» della barra di questa pagina
scope.launch {
  host?.show(FluidNotification.undo("Conversazione archiviata", seconds = 5, onUndo = ripristina,
    source = archive, group = "posta"))
}
// Anche: FluidNotification.result(success = false, "Eliminazione non riuscita", source = …)
//        FluidNotification.reminder(FluidNotificationAction("Apri") { … }, "Bozza salvata", source = …)
```

Il riquadro e la forma della sorgente si leggono **quando si pubblica**: l'avviso nasce da lì anche se la pagina si è già
chiusa. Dettagli in [notifiche.md](notifiche.md), «L'avviso che nasce dal tasto».

**Le scelte di Alessio.** Posa vicino al tasto; il conto alla rovescia di «Annulla» è un anello che si svuota (21
novembre); non di serie per «Invia» (dopo i video).

**Cosa fa da solo l'engine.** Il viaggio in tre tempi (raccolta, viaggio, posa) con le curve del popover di F5B.1, il
ritorno nel tasto o l'uscita di sempre, l'anello attorno ad «Annulla».

**Cosa resta all'app.** La scadenza vera dell'operazione (l'anello non la decide), il testo, e montare l'host alla radice.

**In galleria**: `--es dynamic.scene avviso` (Mail: «Archivia» con «Annulla», «Segna come non letta» ed «Elimina» con un
esito positivo e uno negativo). La scena è `sample/…/dynamic/NoticeFromButtonScene.kt`.

## Un elemento che diventa pagina

**Cosa fa.** Un mini lettore, una miniatura, una pillola o un tasto della barra **diventa la pagina**, e la pagina torna
dentro di lui: la sagoma continua fra la sorgente e la pagina sulla molla di Apple (0,355 s nei due versi), il dito apre
e chiude e si inverte in ogni punto, l'elemento condiviso (la copertina, la foto) viaggia sul suo riquadro, i comandi
d'arrivo salgono per ultimi (dopo il 70% della corsa). La pagina aperta è una **rotta vera**: back, collegamenti,
ripristino dopo la morte del processo.

**Quando usarlo.** Quando l'elemento e la pagina sono la stessa cosa vista più grande: il brano e «In riproduzione», la
foto e il visore, la località e la scelta delle località, «Scrivi» e il compositore.

**L'API** (tutta in [pagine.md](pagine.md), «Un elemento che diventa pagina»):

```kotlin
Row(Modifier.fluidZoomSource("mini", ns, FluidZoomStyle.Rise, FluidCapsuleShape) { nav.navigate("lettore") }) { … }
composable("lettore", zoomSource = { FluidTransitionSource("mini", ns) }, zoom = FluidZoomStyle.Rise) { … }
composable("scrivi", zoom = FluidZoomStyle.fromToolbar("scrivi")) { Compositore() }
```

I preset (`FluidZoomStyle`): `Zoom` (card o miniatura, con `fluidSharedElement`), `Rise` (accessorio in basso, guidato dal
dito), `Grow` / `grow(tint, optics)` («Cresce»: una pillola di vetro, con `FluidGrowingPagePill` e `fluidZoomOpen`),
`fromToolbar(id)` (un tasto della barra).

**Le scelte di Alessio.** La versione completa (11 novembre); «Cresce»: vetro per tutta la corsa, opaca solo all'arrivo,
sfondo che si sfoca e si scurisce in modo progressivo, e lo stesso col dito (21 novembre); gli angoli restano arrotondati
col raggio di un foglio fino all'arrivo e si squadrano per ultimi (dopo i video). Niente migrazione delle app: i casi sono
scene della galleria.

**Cosa fa da solo l'engine.** Il viaggio, il dito, l'elemento condiviso, «Cresce», la sorgente consumata e il ripiego
senza salti, la rotta vera, il pannello del tablet.

**Cosa resta all'app.** Marcare la sorgente (fuori dalla barra) e l'immagine condivisa, e mettere sorgente e destinazione
in un grafo annidato perché un collegamento ricostruisca la pagina sotto.

**In galleria**: `--es dynamic.scene elemento` (le quattro scene di F6B.5c: il mini lettore di Fluidify, la griglia di
Gallery, la pillola di Weather, «Scrivi» di Mail), `sample/…/dynamic/ElementToPage.kt` ed `ElementToPageScenes.kt`.

## Il campo: i preset d'uso

**Cosa fa.** Il campo della barra (la lente che diventa campo, F4B.2) ha cinque **preset**, costruttori di
`FluidSearchSpec`: ognuno sceglie l'icona, il segnaposto, il tasto a destra (cancella o «Invia»), che cosa fa l'invio
(chiude o resta aperto) e se il testo va a capo. In alto, il campo che **resta in cima** (`StayAtTop`) inghiotte la riga:
i tasti accanto si sfocano subito (a 100 ms sono sotto il 6%), i loro vetri confluiscono nel vetro del campo, e alla
chiusura rinascono da lui col viaggio inverso.

**Quando usarlo.** Ogni campo che non è della pagina ma dell'interfaccia: la ricerca, il messaggio di una chat, una nota
veloce, la domanda all'assistente, «trova» (sotto).

**L'API.**

```kotlin
FluidScreen(title = "Posta in arrivo", search = FluidSearchSpec.search(query, { query = it }, prompt = "Cerca nella posta",
  placement = FluidSearchPlacement.TopBar, behavior = FluidSearchBehavior.CollapseToButton,
  expansion = FluidSearchExpansion.StayAtTop)) { … }

FluidScreen(title = "Anna", search = FluidSearchSpec.message(text, { text = it }, onSend = { invia(text); text = "" })) { … }
```

| preset | scopo | icona | tasto a destra | tastiera | invio | righe |
|---|---|---|---|---|---|---|
| `search` | Search | lente | cancella + X | Cerca | `onSearch`, **chiude** (la parola resta) | 1 |
| `message` | Input | fumetti | X → **Invia** con del testo | Invia | `onSend`, **resta aperto**; a testo vuoto niente | 5, va a capo |
| `note` | Input | matita | cancella + X | Fine | `onDone`, **chiude** | 4, va a capo |
| `assistant` | Input | scintilla | X → **Invia** con del testo | Invia | `onAsk`, **resta aperto** | 6, va a capo |
| `findInPage` | Search | lente | cancella + «3/12» ↑ ↓ + X | Cerca | risultato **successivo**, resta aperto | 1 |

I campi in coda a `FluidSearchSpec` (F6B.6), tutti con un valore di serie che rifà il campo di prima: `navigation`,
`onSubmit` (`FluidSearchSubmit.Close` o `StayOpen`; `null` è la regola di prima), `maxLines`, `icon`, `trailing`
(`FluidSearchTrailing`), `showsAtRest`.

**Le scelte di Alessio.** Dove sta il campo lo decide **l'app, pagina per pagina** (`expansion = StayAtTop` o
`AboveKeyboard`); i tasti inglobati si sfocano, rientrano nel campo e tornano alla chiusura (11 novembre). **«Tasti più
grandi»** (23 novembre, F6B.8b): i comandi dentro il campo (le frecce, la X, il «+», la dettatura, «Invia», lo stop) sono da
**44 dp** a vista (le icone da 26, il disco di «Invia» da 40 nel posto da 44), «Cancella» da **36** (il cerchio da 22), e il
campo aperto è alto **48 dp** (prima 40): ogni comando ha il bersaglio da 48 dp, disgiunto dai vicini (dove si
toccherebbero lo spazio si divide), tutto dentro il vetro. La lente a riposo e i tasti della barra restano da 46 col
bersaglio da 48.

**Cosa fa da solo l'engine.** Il viaggio della lente da 46 dp nel campo da 48, i vicini che rientrano e tornano, la salita sopra la tastiera, il segnaposto che lascia subito il posto al testo (mai due scritte nello
stesso posto), l'invio che chiude o resta aperto, lo stato salvato.

**Cosa resta all'app.** Il testo (`query`, `onQueryChange`), cosa fa l'invio, e l'apertura se la vuole tenere lei
(`active` e `onActiveChange` insieme).

**In galleria**: `--es dynamic.scene campo` (Mail: la ricerca in alto che inghiotte «Scrivi» e l'account, il titolo che
sceglie l'account; una lista sceglie il preset). La scena è `sample/…/dynamic/FieldScenes.kt`.

## «Trova nella pagina»

**Cosa fa.** Un modo del campo: a riposo **non c'è**; aperto nasce per lente in fondo, sale sopra la tastiera, mostra
«3/12» con le frecce (un'area viva per TalkBack, «3 di 12») e **non tocca la barra in alto**. L'invio passa al risultato
successivo e il campo resta aperto. Se la tastiera scende resta in fondo, come in Apple; si chiude con la X o con
indietro.

**Quando usarlo.** Una pagina lunga di testo in cui si cerca una parola: una trascrizione, un documento, una mail lunga.

**L'API.**

```kotlin
val find = rememberFluidFindState(trascrizione)               // String o AnnotatedString; senza maiuscole né accenti
var finding by rememberSaveable { mutableStateOf(false) }
var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
FluidScreen(title = "Riunione", toolbar = { button("trova", FluidIcons.Search, "Trova", onClick = { finding = true }) },
  search = FluidSearchSpec.findInPage(find.query, find::onQueryChange, find.navigation, finding, { finding = it })) {
  item { Text(find.highlight(FluidFindDefaults.highlights()), onTextLayout = { layout = it }) }
}
LaunchedEffect(find.scrollRequest) { find.scrollRequest?.let { request -> layout?.let { scorriA(request.boundsIn(it).top) } } }
```

`FluidFindState`: `matches`, `total`, `current`, `currentRange`, `next()`, `previous()`, `highlight(…)` e
`scrollRequest` (cambia a ogni cambio dell'occorrenza corrente). Le app con contenuti complessi contano da sé le occorrenze
e passano una `FluidSearchNavigation`.

**Le scelte di Alessio.** «Ci sta sì»: un modo del campo, non un componente a sé (11 novembre).

**Cosa fa da solo l'engine.** La nascita senza predecessore (il contenuto entra da 0,75, R7 A6), la salita con la
tastiera, il contatore e le frecce, l'area viva, la barra in alto ferma (i suoi fissati non vanno in «Altro»).

**Cosa resta all'app.** Portare in vista l'occorrenza corrente (lo scorrimento è suo: `scrollRequest` dice dove).

**In galleria**: `--es dynamic.scene trova` (Notes: una trascrizione). Il banco: la traccia `trova-sopra-tastiera`.

## Il composer dentro il campo

**Cosa fa.** Il campo con più righe (`message`, `note`, `assistant`) **cresce di riga in riga** fino a `maxLines`, poi il
testo scorre dentro. È un pannello che cresce: il **bordo basso resta sopra la tastiera** e sale quello alto. Dentro il
campo ci sono le parti del composer:
- il **vassoio degli allegati** sopra il testo, che cresce verso l'alto e si richiude col bordo basso fermo;
- lo **slot a sinistra** (il «+»);
- il **tasto a destra a stati**, una sagoma sola: il microfono a campo vuoto, «Invia» appena c'è del testo (con l'anello
  del tasto con esito mentre l'invio lavora, poi la spunta), lo stop mentre si detta; le facce si incrociano sfocate;
- la **riga della voce** al posto del testo mentre si ascolta.

**La lista lascia il posto al campo cresciuto** (F6B.13): `FluidScreen` aggiunge in fondo alla lista l'altezza in più del
campo e la quota della tastiera, così a fine scorrimento l'ultima riga si legge sopra il vetro del campo, con e senza
tastiera. Lo spazio si legge mentre la lista si misura: la crescita e la tastiera non ricompongono la pagina. Vale per
ogni campo che occupa il fondo (in basso, o aperto sopra la tastiera); un campo a riposo che si ritira con la barra porta
via anche questo spazio. Conta solo la parte di tastiera che copre davvero la schermata: non quella già tolta da un
`imePadding()` attorno a `FluidScreen`, né quella che Android ha scavalcato spostando in su la finestra (`adjustPan`, il
modo di serie di un'Activity che non dichiara `windowSoftInputMode`).

**Quando usarlo.** Una chat (Messaggi, Mail), una risposta veloce, la domanda all'assistente.

**L'API.**

```kotlin
val send = rememberFluidActionRunner(FluidActionPreset.Send)
FluidScreen(title = "Anna", search = FluidSearchSpec.message(text, { text = it },
  onSend = { val t = text; text = ""; send.run { api.invia(t) } },
  composer = FluidComposerSlots(
    leading = { FluidCapsuleAction(FluidIcons.Add, "Aggiungi un allegato", onClick = scegli, size = FluidSearchFieldDefaults.ActionSize) },
    attachments = if (allegati.isEmpty()) null else { { Allegati(allegati) } },
    trailing = if (detta) FluidSearchTrailing.stop(onStop = fermaDettatura)
      else FluidSearchTrailing.voice(onVoice = detta, sending = send.state),
    voiceLine = if (detta) { { FluidComposerVoiceLine(parziale, false, { livello }, onTap = fermaDettatura) } } else null,
  ))) { items(messaggi) { … } }
```

`FluidSearchTrailing` è una classe (non un enum, 3.11): `Clear` e `Send` come prima, più `send(sending)`,
`voice(onVoice, sending)` e `stop(onStop, label, icon)`. Il composer nuovo dell'assistente, con l'alone, è
`fluidAssistantField(…)` di engine-ai-ui: [../moduli/ai-ui-chat.md](../moduli/ai-ui-chat.md), «Il composer dentro il campo».

**Le scelte di Alessio.** «Ne costruiamo uno nuovo sui nuovi componenti aggiornati, sempre reso un po' speciale (tipo
campo di testo con alone attorno)» (21 novembre): il campo dell'engine ospita le parti, engine-ai-ui costruisce su di lui il
composer dell'assistente con l'alone; `FluidComposer` di prima resta com'è. «L'alone deve essere animato» (22 novembre).

**Cosa fa da solo l'engine.** La crescita con la molla del cromo e il piano che la segue **senza viaggio**
(`FluidGlassAnimation.None`); il vetro che sborda dalla riga (i vicini e il bersaglio da 48 dp non si spostano); il
vassoio; lo scambio sfocato delle facce del tasto; **niente doppio invio** (a testo vuoto niente, con l'invio che lavora
niente, e per 345 ms dopo un invio né lo stesso testo né un tocco sul tasto che cambia faccia); la riga della voce; il
margine della lista (sopra).

**Cosa resta all'app.** Gli allegati veri (i launcher, le miniature), la dettatura vera, cosa fa l'invio, e la chat.

**In galleria**: `--es dynamic.scene messaggio` (Messaggi e Mail: le righe, il vassoio, il tasto a stati, la lista che
resta sopra il campo) e `assistente` (PampAI: l'alone, i chip, le pillole, la voce; mentre risponde, la pillola di
attività). Le scene sono in `sample/…/dynamic/ComposerScenes.kt`.

## Filtri e suggerimenti a pillole

**Cosa fa.** Una **fila di pillole dichiarata** (`FluidFilterChips`), a scelta singola o multipla, che si usa sotto il
campo (nello slot `suggestions`), nel pannello dei suggerimenti o nella pagina. **Quando non ci sta nella larghezza**,
testo grande compreso, le pillole che non ci stanno si raccolgono in **un** tasto di vetro «Filtri · 2» (il numero conta
le accese che contiene): il menu nasce dal suo vetro **come dal gruppo della barra** (F5A.2), con le stesse voci a
interruttore che lasciano il menu aperto. Un'etichetta non si tronca mai. Una pillola a più valori («Periodo»: 7 giorni,
30 giorni, Altro) si apre nei suoi valori dal suo vetro. I **suggerimenti** (`FluidSearchSuggestions`) sono una lista
sola per pertinenza, coi recenti che si tolgono dalla ✕, e mai «Nessun risultato» prima del primo carattere.

**Quando usarlo.** Ogni ricerca con dei filtri (Transit, Mail, Foto); una fila di ambiti esclusivi; i recenti.

**L'API.**

```kotlin
var filtri by rememberSaveable(stateSaver = FluidFilterSelection.Saver) { mutableStateOf(FluidFilterSelection.of("aperti")) }
FluidScreen(title = "Fermate", search = FluidSearchSpec.search(query, { query = it }, suggestions = {
  Column {
    FluidFilterChips(listOf(FluidFilter("aperti", "Aperti ora", priority = FluidToolbarPriority.High),
      FluidFilter("metro", "Metro", priority = FluidToolbarPriority.Low), FluidFilterPresets.lastDays()),
      filtri, { filtri = it })
    FluidSearchSuggestions(query, suggerimenti, onSuggestionClick = apri, onRemoveRecent = togli)
  }
})) { … }
```

I preset (`FluidFilterPresets`): `scopes` (ambiti, esclusivi), `choices` (scelta multipla), `range` e `lastDays`
(intervalli), `recent`, `nearby`, `popular`, `nearbyAndPopular`. Le parole: `FluidFilterLabels`, `LocalFluidFilterLabels`.

**Le scelte di Alessio.** La fila che diventa menu è un **comportamento di serie** dei filtri, non un componente: quando
non ci sta nella larghezza, testo grande compreso (21 novembre). Il menu nasce come dal gruppo della barra, non dal
tasto singolo (22 novembre, dopo i video).

**Cosa fa da solo l'engine.** La misura delle pillole al testo di adesso, la priorità di chi esce (prima le `Low`, poi
dalla coda), il tasto «Filtri · N», il menu dal vetro, la pillola a più valori, l'ordine dei suggerimenti, la ✕ dei recenti
(48 dp, e la stessa azione per TalkBack sulla riga).

**Cosa resta all'app.** I filtri e cosa vogliono dire, la pertinenza dei suggerimenti, i recenti veri.

**La pillola che diventa gettone** dentro il campo della ricerca è la sezione dopo, «I gettoni nel campo della ricerca»
(F6B.8b).

**In galleria**: `--es dynamic.scene filtri` (Transit: la fila sotto il campo, «Periodo», i recenti; le linee nella
pagina; con `--es gallery.text 2.0` la fila diventa «Filtri · N»). La scena è in `sample/…/dynamic/FilterScenes.kt`.

## I gettoni nel campo della ricerca

**Cosa fa.** I **gettoni** stanno dentro il campo della ricerca, prima del testo, e il testo scorre dopo di loro: è
`searchable(text:tokens:suggestedTokens:)` di SwiftUI. I **gettoni suggeriti** sono una fila di pillole di vetro accanto al
campo aperto (sopra il campo in basso, sotto quello in alto). La pillola scelta **diventa gettone con un viaggio vero**: il
suo vetro vola dentro il campo, si fa piccolo e diventa il gettone tinto d'accento, mentre il suo posto nella fila si
richiude; il gettone tolto rifà il viaggio al contrario e torna pillola nel posto che la fila gli riapre (l'ordine dichiarato,
non in fondo). In nessun fotogramma si leggono insieme la pillola e il gettone: sono un disegno solo che cambia forma, e la
scritta è la stessa (cambia solo l'inchiostro).

Un gettone si toglie **toccandolo**, o col tasto **«cancella» a testo vuoto** come in `UISearchTextField`: il primo colpo lo
**sceglie** (si inverte, e TalkBack lo dice), il secondo lo toglie. Sul telefono a 360 dp tre nomi corti e il testo stanno
nel campo vuoto; quando i gettoni non ci stanno con un po' di testo (64 dp), **i primi si raccolgono in «+N»** (come il campo
«A:» di Mail) e l'ultimo arrivato resta in vista; TalkBack ha un'azione per toglierne ognuno. Un gettone che l'app aggiunge
senza pillola (da un suggerimento, da sé) nasce sfocato al suo posto (R7), e uno tolto che non è nella fila esce sfocato.

**Quando usarlo.** Una ricerca che restringe per categorie: «da: persona» in Mail, i tipi di Foto, le linee di Transit.
Per filtri che non entrano nel campo (interruttori, periodi) restano le pillole di «Filtri e suggerimenti» sopra.

**L'API.**

```kotlin
var gettoni by rememberSaveable(stateSaver = …) { mutableStateOf(emptyList<FluidToken>()) }
val persone = FluidFilterPresets.tokens(listOf(FluidFilterValue("anna", "Anna"), FluidFilterValue("luca", "Luca")))
FluidScreen(title = "Posta", search = FluidSearchSpec.search(query, { query = it }, prompt = "Cerca nella posta",
  tokens = gettoni, onTokensChange = { gettoni = it }, filters = persone)) {
  items(posta.filter { m -> gettoni.isEmpty() || gettoni.any { it.label == m.da } }) { … }
}
```

I campi in coda a `FluidSearchSpec` (F6B.8b): `tokens` (i gettoni, dell'app come `query`), `onTokensChange` (i gettoni
nuovi: una pillola scelta in fondo, un gettone tolto), `filters` (i gettoni suggeriti, un `FluidFilterSet`; in `Single` un
gettone della fila prende il posto dell'altro). `FluidSearchSpec.search(…)` li prende per nome. Il preset **Gettoni** è
`FluidFilterPresets.tokens(values, label, icon, mode)`: una pillola per valore, con l'id e l'etichetta del valore (di serie
senza simbolo, perché tre gettoni stiano sul telefono). Il gettone è `FluidToken(id, label)`. Le parole per TalkBack sono in
`FluidSearchLabels` (`tokenRemove`, `tokenSelected`, `tokensGathered`).

**Le scelte.** Alessio non ha scelto niente per il viaggio (la riga «pillola → gettone» del piano è **a occhio**); le ho
fatte io il 23 novembre seguendo Apple e il piano, e sono nel rapporto di F6B.8b:
- il viaggio sta sulla curva del campo che si apre (334 ms, `CubicBezier(.38, 0, .35, 1)`, F4B.2): la pillola entra nel
  campo nel tempo in cui il campo si apre;
- il vetro svanisce fra il 35% e l'80% del viaggio mentre entra la tinta (al ritorno fra il 20% e il 65%): prima è tutto
  vetro, dopo tutto gettone;
- il gettone è la capsula d'accento alta 28 dp nel campo (da 52 dalla F6B.15; il `tokenBackgroundColor` di iOS è la tinta del campo),
  scelto si inverte; mai più stretto di 48 dp;
- il «cancella» a testo vuoto sceglie e poi toglie (`UISearchTextField`), il tocco toglie subito;
- a campo chiuso o col movimento ridotto il cambio è di colpo; i gettoni a riposo si vedono nel campo ma non si toccano.

**Cosa fa da solo l'engine.** La fila che si richiude e si riapre, il viaggio (che insegue il campo se la tastiera lo
sposta, e si inverte a metà), il gettone e il suo bersaglio da 48 dp, il «cancella» a testo vuoto (anche quello della
tastiera, che a testo vuoto non manda niente: il campo tiene un carattere di guardia, come `FluidTokenField`), «+N», la
nascita e l'uscita sfocate, i gettoni che escono col campo che si chiude.

**Cosa resta all'app.** I gettoni (`tokens`, `onTokensChange`), che cosa vogliono dire per la ricerca, e i gettoni
suggeriti.

**Negli altri campi (F6B.15).** Scelta di Alessio del 23 novembre 2026, pomeriggio: «i gettoni anche negli altri campi».
Il **campo sotto il titolo** (`FluidSearchSpec(placement = FluidSearchPlacement.Principal, …)`, gli stessi `tokens`,
`onTokensChange` e `filters`) e **`FluidSearchField` nella pagina** (tre parametri in coda: `tokens`, `onTokensChange`,
`filters`) hanno lo stesso modello e lo stesso viaggio: la fila delle pillole sta **sotto** il campo ed entra col campo che si
apre; la pillola scelta vola nel campo e diventa gettone, il gettone tolto torna pillola al suo posto, il «cancella» a testo
vuoto sceglie e poi toglie. Senza gettoni né `filters`, `FluidSearchField` è quello di prima, nodo per nodo.

```kotlin
FluidSearchField(query, { query = it }, placeholder = "Cerca nelle note",
  tokens = etichette, onTokensChange = { etichette = it },
  filters = FluidFilterPresets.tokens(listOf(FluidFilterValue("casa", "Casa"), FluidFilterValue("idee", "Idee"))))
```

**Le misure dopo le riprese del 23 novembre (F6B.15).** Il campo aperto è alto **52 dp** e il gettone (28 dp) ci sta con 12 dp
d'aria sopra e sotto; la fila delle pillole sta a 8 dp dal vetro del campo (che nella riga in alto da 46 esce di 3 dp sopra e
sotto). Col campo in alto (il tablet) la lista lascia libera la fila, che prima copriva l'intestazione della lista.

**In galleria**: `--es dynamic.scene gettoni` (Mail, «da: persona»: la ricerca in basso sul telefono, le persone sopra il
campo), `gettoni-titolo` (la stessa posta col campo sotto il titolo grande) e `gettoni-pagina` (Notes, le etichette con
`FluidSearchField` nella pagina). Il banco: la traccia `pillola-gettone` (e `-scuro`). Le scene sono in
`sample/…/dynamic/FilterScenes.kt` e `TokenFieldScenes.kt`.


## La barra sopra la tastiera

**Cosa fa.** Una barra di comandi **sopra la tastiera**, una per campo: si vede quando l'IME è su e un campo ha il fuoco
(o sempre, se lo si chiede), segue l'altezza della tastiera **fotogramma per fotogramma**, e quando la tastiera arriva in
ritardo (Android dà solo l'altezza finale) la insegue con la molla di F4B.2. Fra due campi **si trasforma** come una barra
in alto che cambia stato: gruppi, priorità, trabocco e scambio sfocato sono quelli della barra dichiarata.

**Quando usarlo.** Un modulo con più campi (passare al successivo e «Fatto»), il compositore di una mail (suggerimenti
dei destinatari, allegati, formattazione), un editor (annulla e ripeti), la ricerca nel testo.

**L'API.**

```kotlin
FluidScreen(title = "Nuova mail", toolbar = {
  keyboard(context = campo, fieldFocused = campo != Nessuno) {          // la chiave di contesto: un cambio è una trasformazione
    when (campo) {
      Destinatari -> suggestions(proposte) { aggiungi(it) }
      Oggetto -> quickActions(onAttach = allega, onPhoto = foto)
      Testo -> { button("b", FluidIcons.FormatBold, "Grassetto", grassetto, selected = acceso); history(puoi, rifai, annulla, ripeti) }
    }
    fieldNavigation(focusManager, keyboardController, hasPrevious = campo != Destinatari, hasNext = campo != Testo)
  }
}) { … }
// Fuori da FluidScreen: FluidKeyboardBar(toolbar = { … }) in un Box che riempie la finestra.
```

I preset, funzioni dello scope: `fieldNavigation` (precedente, successivo, «Fatto», col `FocusManager`), `quickActions`
(allega, foto, dettatura, «Scrivi con Aria»), `suggestions` (con `FluidTokenSuggestion`), `history` (annulla e ripeti),
`findInText` (il contatore di «trova»). La **barra del testo ricco** è un preset con la firma di sempre:
[../moduli/richtext.md](../moduli/richtext.md), «La barra e il tasto «Aa»».

**Le scelte di Alessio.** «Ottima idea, con dei preset e poi la possibilità di crearla su misura» (11 novembre); con la
**tastiera fisica** la barra resta in fondo alla finestra (21 novembre).

**Cosa fa da solo l'engine.** La quota dell'IME letta in misura (nessun fotogramma di ritardo quando la tastiera anima i
suoi inset), il ripiego con la molla, la tastiera flottante o divisa (la barra sul bordo basso della finestra), il back
che chiude tastiera e barra insieme, il campo di «trova» sopra la barra, il velo più pieno sul bordo verso la tastiera
(il vetro non rifrange un'altra finestra).

**Cosa resta all'app.** La chiave di contesto (quale campo ha il fuoco), i comandi veri.

**In galleria**: `--es dynamic.scene tastiera` (Mail «Scrivi»: una barra per campo, «Fatto»; in alto «Invia ▾») e
`testo-ricco` (Notes: la barra del testo ricco). Le scene sono in `sample/…/dynamic/KeyboardBarScenes.kt`; la scena
minima di F6B.9 resta in «Barre (prova)» (`--es toolbar.scene keyboard`).

## La pillola di attività

> **Provvisoria.** F6B.10 è integrata in modo provvisorio: Alessio la deve ancora confermare. Restano aperte due domande:
> **i tempi della card** (oggi quelli del popover dalla sorgente, F5B.1: si legge a ~200 ms e si ferma a ~250, più svelta
> del menu della barra; oppure quelli del menu, F5A.2, per la sola card o per tutti i popover) e **il posto dell'anellino
> sul tablet** (oggi al centro della striscia della barra di stato, dove sul Tab S9 c'è la maniglia della finestra di
> Samsung; oppure un poco a destra del centro, ~44 dp, sempre).

**Cosa fa.** Un lavoro lungo dell'app (una sincronizzazione, un trasferimento, una registrazione, l'assistente che
risponde, un timer) si pubblica **una volta alla radice**, e **ogni barra in alto** lo mostra **al posto del titolo**
finché dura: il titolo esce sfocato sopra il vetro che nasce sul posto, e la scritta della pillola entra sfocata (lo
scambio di R7). Tre forme: **compatta** (l'anello o l'icona, una frase breve, la percentuale o il tempo a cifre tabulari),
**minima** (il solo anello, quando la barra è piena o il centro è occupato) ed **estesa** (al tocco, una card che nasce
dalla pillola, con i dettagli, le altre attività e i comandi). **L'esito**: riuscito, la spunta e un battito, poi esce;
non riuscito, il segno d'errore nella tinta d'errore. A barra ritirata della pillola resta **l'anellino**, piccolo, nella
striscia della barra di stato.

**Quando usarlo.** Ogni lavoro che dura più di qualche secondo e che l'utente deve poter seguire da ogni pagina.

**L'API.**

```kotlin
val attivita = rememberFluidActivityHostState()
FluidActivityHost(attivita) {                                  // alla radice, dentro l'host dei modali (la card è un popover)
  App()
}
attivita.publish(FluidActivity.sync(progress = { avanzamento }, detail = "Posta in arrivo"))
attivita.finish("sync", FluidActivityOutcome.succeeded())      // o failed(…), o HandedOff: l'esito lo dice un altro
// In una pagina: activity(hidden = true) per non mostrarla, activity(placement = Trailing) per tenerla fra i tasti.
```

I preset: `FluidActivity.sync`, `.transfer`, `.recording`, `.assistant`, `.timer`. La coda (`FluidActivityHostState`): chi
prende la pillola è l'attività della card aperta, poi un esito in vista, poi la più importante, a parità la prima
arrivata; le altre sono il contatore («+2»). Con due barre in vista (lista e dettaglio) la pillola è **una sola**, di serie
in quella del dettaglio (`pane`).

**Le scelte di Alessio.** Una per l'app, pubblicata alla radice e mostrata da ogni barra; una pagina può nasconderla (21
novembre); **al posto del titolo**, «in quanto già il titolo è dinamico in base alla pagina» (21 novembre); a barra
ritirata «resta come anellino» (22 novembre); con due barre in vista una pillola sola (22 novembre). Dopo i video del
secondo giro: niente entrata da fuori, uscita continua, card nel tema (terzo giro, da confermare).

**Cosa fa da solo l'engine.** La coda e le priorità, le tre forme, la comparsa morbida e l'uscita inversa, l'esito, la
card, l'anellino, la larghezza che cambia solo con la frase, TalkBack (nome, stato, «Mostra i dettagli», l'esito).
L'anello, le cifre e il battito si ridisegnano soltanto: non ricatturano il vetro.

**Cosa resta all'app.** Il lavoro vero, il suo avanzamento, i comandi (pausa, annulla, apri) e quando finisce.

**In galleria**: `--es dynamic.scene attivita` (Mail che sincronizza, con la barra dichiarata: la pillola, la card,
l'esito, l'anellino), `titolo-menu` (la pillola accanto al titolo che è un selettore) e `assistente` (la pillola mentre
l'assistente risponde). Le scene sono in `sample/…/dynamic/ActivityPillScene.kt`, `SplitTitleScenes.kt` e `ComposerScenes.kt`.

### Fuori dall'app (F8.19)

La stessa attività **continua fuori dall'app** come notifica viva: su Android 16 un Live Update (il gettone nella barra di
stato, la barra a tappe, la schermata di blocco), prima una notifica con l'avanzamento. **Si pubblica una volta**, nella
pillola; uscendo diventa la notifica, rientrando la notifica se ne va e torna la pillola. È la Live Activity che continua
nella Dynamic Island (ActivityKit). La notifica la fa `engine-notify` (opt-in, scheda [notify](../moduli/notify.md)); il
ponte sta qui e parla col modello puro di `engine-foundation` (`FluidLiveUpdate`, `FluidLiveUpdateSink`).

```kotlin
// Un host per tutta la vita dell'app (nell'Application o in un ViewModel), così il lavoro continua a pagina chiusa:
activities.mirrorOutside(FluidNotify.get(this).liveUpdates.whenOutside, MainScope())
// Oppure, per un host alla radice dell'attività, dentro la composizione:
FluidActivityHost(activities) {
  FluidActivityOutside(activities, FluidNotify.get(LocalContext.current).liveUpdates.whenOutside)
  App()
}
```

- Ogni `publish`, `finish` e `remove` passa subito; l'avanzamento (una lambda letta dalla pillola quando disegna) fuori lo
  rilegge lo specchio una volta al secondo, **solo mentre l'app è fuori**.
- I comandi della card (Pausa, Annulla) sono i tasti della notifica e chiamano le stesse funzioni.
- L'esito di un'attività finita fuori resta nella notifica per quattro ore; `HandedOff` e `remove` la tolgono.
- `FluidActivity.toLiveUpdate(open)` e `FluidActivityOutcome.toLiveOutcome()` sono la traduzione, se serve a mano.
- Scena: `--es novita.scene attivita-fuori` (la conversione di Universal Converter).

## Il tasto diviso

> **Provvisorio.** F6B.11 è integrata in modo provvisorio: Alessio la deve ancora confermare.

**Cosa fa.** Un vetro solo con due zone, l'azione (una scritta o un'icona) e la freccia, divise da un filo sottile.
Toccare l'azione la fa; la freccia, o la pressione lunga sull'azione, apre il menu **dallo stesso vetro** (il vetro intero
del tasto diventa il menu e ci rientra). Due bersagli disgiunti, ognuno di almeno 48 × 48 dp.

**Quando usarlo.** Un'azione principale con delle varianti: «Invia ▾ Programma invio», «Esporta ▾ come PDF».

**L'API.**

```kotlin
toolbar = {
  split("invia", "Invia", onClick = invia, menuLabel = "Opzioni di invio",
    menu = { listOf(FluidContextAction("Programma invio", icon = FluidIcons.Schedule, onClick = programma)) })
  action("allega", FluidIcons.Image, "Allega", onClick = allegaFoto, menu = { altriAllegati })  // tocco = azione, pressione = menu
}
FluidSplitButton(label = "Esporta", onClick = esporta, menu = { formati }, menuLabel = "Formati di esportazione")
```

**Le scelte di Alessio.** Un vetro solo, con un filo sottile fra azione e freccia (21 novembre).

**Cosa fa da solo l'engine.** Il filo e la freccia (`FluidSplitButtonDefaults`, a occhio), i due bersagli, il menu dal
vetro, la faccia che torna quando il menu rientra (F6B.11b), TalkBack (due pulsanti).

**Cosa resta all'app.** L'azione e le voci del menu.

**In galleria**: `--es dynamic.scene tasto-diviso` (Mail «Scrivi»: «Invia ▾» nella barra, «Esporta ▾» da solo) e
`tastiera` («Invia ▾» sopra il compositore con la barra sopra la tastiera).

## Il titolo che diventa selettore

> **Provvisorio.** F6B.11 è integrata in modo provvisorio: Alessio la deve ancora confermare.

**Cosa fa.** Il titolo, grande e agganciato, porta accanto un piccolo disco con la freccia ed è la **sorgente** di un menu
(gli account, le cartelle) o di un pannello (le località). Il menu nasce dal disco e ci rientra; il pannello è un popover
dal titolo, e sul telefono un foglio che nasce dal titolo. Per TalkBack il titolo è un «elenco a discesa».

**Quando usarlo.** Quando il titolo dice dove si è e da lì si cambia: la casella di posta, la cartella, la località.

**L'API.**

```kotlin
toolbar = {
  title(FluidToolbarTitleMode.Large, selectorHint = "Cambia casella",
    menu = { account.map { FluidContextAction(it, selected = it == scelto, onClick = { scelto = it }) } })
  // oppure: title(FluidToolbarTitleMode.Inline, panel = { Localita(onScelta = { dismiss() }) })
}
```

**Le scelte di Alessio.** Il titolo nudo con la freccia; la pillola di vetro è uno stile a scelta (21 novembre), che oggi
non c'è.

**Cosa fa da solo l'engine.** Il disco (misure in frazioni del carattere: segue il titolo grande che diventa quello
agganciato), il bersaglio che comprende il disco, l'ancora del menu, il popover e il foglio dal titolo. **Con un'attività
in corso** la pillola **non** prende il posto di un titolo che è un selettore (è un comando): sta minima accanto, fra i
tasti, e la card si apre dall'anello. Se la pagina dichiara `activity(placement = Title)` vince la pagina.

**Cosa resta all'app.** Le voci o il contenuto del pannello, e cosa cambia la scelta.

**In galleria**: `--es dynamic.scene titolo-menu` (Mail: gli account), `titolo-pannello` (Weather: le località) e
`campo` (Mail: il titolo che sceglie l'account accanto alla ricerca che resta in cima).

## Un tasto che cambia resta un vetro solo

> **Provvisorio** come il resto di F6B.11.

Quando nello stesso posto della barra un tasto ne diventa un altro (l'indietro che diventa «Seleziona tutto», Chiudi,
la pillola), **il vetro resta uno**: cambia misura con la molla, se serve, e il contenuto si scambia sfocato (R7). La lente
resta per un tasto che nasce o se ne va **davvero**. È la scelta di Alessio del 22 novembre («è come se ci fossero due
tasti durante lo scambio»). Il dettaglio è in [toolbar.md](toolbar.md), «La regola dello stesso posto».

## Come si prova

**I test.** Un test Robolectric per scena: `FluidDynamicElementsUiTest` (sample, le scene di F6B.12) e
`FluidDynamicFieldsUiTest` (sample, quelle di F6B.13: campo, «trova», composer, pillole, gettoni, barra sopra la tastiera,
testo ricco; e a 360 e 412 dp nessun bersaglio sopra un altro in ognuna, la pillola di attività compresa). `FluidSplitTitleUiTest`
(sample) prova il tasto diviso e il titolo selettore, `FluidActivityPhoneUiTest` la pillola sul telefono. Ogni test: la scena si apre, i bersagli sono di
almeno 48 dp, la semantica è giusta (i nomi dei tasti, il ruolo di pulsante, il conteggio, le aree vive dell'esito e del
contatore), e il gesto principale fa ciò che deve. Sotto, i test di ogni pacchetto: `FluidSelectionToolbarTest` (D1 col
dispatcher vero, «Seleziona tutto» su `AllExcept`), `FluidFloatingTabBarSelectionTest` e `FluidTabSelectionHostTest` (la
tab bar che diventa le azioni e torna al pixel), `FluidActionStateTest`, `FluidActionToolbarTest` e
`FluidActionButtonUiTest` (soglia, tenuta, ripresa, parole, bersagli), `FluidActionGlyphsTest`, `FluidUndoCountdownTest` e
`FluidUndoCountdownUiTest`, `FluidNotificationSourceTest`, `FluidPageOpenGestureTest`, `FluidSharedElementTest`,
`FluidZoomPresetsTest` e `FluidElementToPageUiTest`; della seconda parte `FluidToolbarSearchPresetsTest` (i preset,
«trova»), `FluidComposerInFieldTest` e `ComposerTracesTest` (la crescita, il vassoio, il tasto a stati), `FluidSearchListRoomTest`
e `FluidScreenGrownFieldListTest` (la lista sotto il campo cresciuto, F6B.13, a 360 e 412 dp, una e cinque righe, con e senza
tastiera, e dentro `imePadding()`), `FluidFilterChipsTest` e `FluidFilterChipsUiTest` (la fila che diventa menu alle scale 1,0,
1,3 e 2,0), `FluidKeyboardBarTest` e `FluidKeyboardBarComposeTest` (la quota che segue un IME finto), `FluidActivityQueueTest`
(priorità e contatore), `FluidToolbarSplitTitleTest` e `FluidToolbarMenuSourceTest` (il vetro solo, il tasto diviso, il
titolo, la faccia che torna dopo il menu). Le scene di «Misure» (F7.1) per gli elementi che si ripetono: `selezione`,
`campo-composer`, `tastiera-contesto` (`MeasureScenarioUiTest`).

**I bersagli sotto i 48 dp, dell'engine.** Il test delle scene di F6B.13 li dichiara uno per uno: la lente della ricerca a
riposo (46 × 46 dp: i tasti della barra hanno un bersaglio da 48 attorno al vetro da 46, la lente no); i comandi dentro il
campo (le frecce e la X di «trova», il «+» e il tasto a destra del composer: 40 dp; la X che cancella: 28); le pillole e i
chip del vassoio del composer dell'assistente (30 dp); il titolo grande che è un selettore (≈41 dp, scelta di F6B.11); la
fascia del titolo agganciato che riporta in cima (46 dp). Sono da decidere con Alessio.

**Le tracce del banco** stanno in `tools/glassbench/traces/f6b/`, una cartella per traccia, con le righe del piano (F6B,
«Il banco del movimento»):

| elemento | tracce |
|---|---|
| selezione | `selezione-entra`, `selezione-esce`, `selezione-back-confermato`, `selezione-back-annullato`, `selezione-senza-tabbar` |
| tasti con esito | `tasto-invia-riuscito`, `tasto-invia-fallito`, `tasto-salva`, `tasto-copia`, `tasto-scarica-percentuale`, `tasto-preferito-rifiutato`, `tasto-lavoro-breve` (curve `curve.csv`: i fotogrammi sono riprese della finestra) |
| «Annulla» e avviso | `invia-composto`, `annulla-contatore`, `annulla-scade`, `avviso-dal-tasto`, `avviso-annulla-torna`, `avviso-sorgente-sparita` |
| elemento → pagina | `elemento-zoom`, `elemento-sale-col-dito`, `elemento-cresce`, `elemento-dalla-barra`, `elemento-interrotto-25`, `elemento-interrotto-50`, `elemento-interrotto-75`, `elemento-giu-chiude` |
| campo e composer | `campo-messaggio`, `campo-alto-inghiotte`, `trova-sopra-tastiera`, `composer-righe`, `composer-allegati`, `composer-tasto-stati`, `composer-alone` |
| pillole | `pillole-menu-200`, `pillola-periodo-menu` (`pillola-gettone` manca: manca il gettone nel campo) |
| tastiera | `tastiera-contesto`, `tastiera-in-ritardo` |
| attività | `attivita-compare`, `attivita-estesa`, `attivita-esito`, `attivita-anellino` |
| diviso, titolo, stesso posto | `tasto-diviso-menu` (il «diviso-menu» del piano), `titolo-selettore` (il «titolo-menu»), `titolo-pannello`, `tasto-cambia-un-vetro` |

Il `README.md` della cartella dice, per ogni traccia, il test che la scrive, la variabile d'ambiente e dove sta il collage
di tutti i fotogrammi.

**Non verificato dai test**: come si vede in mano. Il collaudo sul Tab S9 (F6B.14) guarda la tab bar che diventa le
azioni a 120 Hz, il gesto indietro di One UI in selezione, TalkBack sugli esiti e sul contatore, il testo al 200%, il mini
lettore tirato col dito, il ripristino dopo `am kill` e la rotazione a metà viaggio; e, per la seconda parte, la tastiera
vera (Samsung Keyboard, Gboard, flottante e fisica) sotto il campo che cresce e la barra sopra la tastiera, l'alone a 120 Hz e
il suo costo sul GPU, la fila a testo grande, la pillola di attività e il suo anellino.

## Dove guardare

- `engine-ui/…/ui/fluid/toolbar/FluidSelectionToolbar.kt`: `selection`, le posizioni, i preset, `FluidSelectionLeading`.
- `…/ui/fluid/FluidFloatingTabBar.kt` e `FluidAdaptiveNavigation.kt`: la tab bar che diventa le azioni.
- `…/ui/fluid/action/`: `FluidActionState.kt` (stato, esecutore, preset), `FluidActionButton.kt` (il tasto e il tipo della
  barra), `FluidActionGlyphs.kt` (i glifi).
- `…/ui/fluid/FluidUndoCountdown.kt`: il contatore; `FluidNotification.kt` e `FluidNotificationSource.kt`: l'avviso dal tasto.
- `…/ui/page/FluidZoomPresets.kt`, `FluidSharedElement.kt`, `FluidPageHostState.kt`: l'elemento che diventa pagina.
- `…/ui/fluid/FluidSearch.kt` (i preset del campo, `FluidComposerSlots`, `FluidSearchTrailing`), `FluidFindInText.kt`
  («trova»), `toolbar/FluidToolbarSearch.kt` (il campo che cresce, la lista che gli lascia il posto: `ToolbarSearchListRoom`).
- `…/ui/fluid/FluidFilterChips.kt`: la fila, il menu, i suggerimenti, i preset.
- `…/ui/fluid/toolbar/FluidKeyboardBar.kt`: la barra sopra la tastiera, la quota e i preset.
- `…/ui/fluid/activity/FluidActivity.kt`: la pillola di attività, la coda, l'host, la card, l'anellino.
- `…/ui/fluid/FluidSplitButton.kt`, `toolbar/FluidToolbarTitleSelector.kt`: il tasto diviso e il titolo selettore.
- `engine-ai-ui/…/aiui/chat/FluidComposerInField.kt`: il composer dell'assistente con l'alone.
- La galleria: `sample/…/dynamic/` (`DynamicElements.kt` è l'indice). Le scene di F6B.13: `FieldScenes.kt`, `ComposerScenes.kt`, `FilterScenes.kt`,
  `KeyboardBarScenes.kt`; quelle di F6B.10 e F6B.11: `ActivityPillScene.kt`, `SplitTitleScenes.kt`. Gli scenari di «Misure»:
  `sample/…/measure/MeasureScenarioScenes.kt`.
