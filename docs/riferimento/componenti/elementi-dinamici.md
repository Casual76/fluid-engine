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

Questa è la prima parte (F6B.12): selezione, tasti con esito, «Annulla», avviso dal tasto, elemento → pagina. Il
campo e il composer, le pillole dei filtri, la barra sopra la tastiera, la pillola di attività, il tasto diviso e il
titolo che diventa selettore arrivano con F6B.13.

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
  barra in basso: su un telefono le azioni, e con loro «Seleziona tutto», stanno in «Altro»), `Floating` (una capsula sulla griglia, che non le lascia
  spazio).
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
rail e barra laterale), `selezione-note` (Notes: «Seleziona tutto» su 120 note caricate a pagine, le azioni in alto e, sul telefono, in «Altro»),
`selezione-foto` (Gallery: la capsula flottante), `selezione-file` (File: la barra in basso senza tab bar). Le scene
sono in `sample/…/dynamic/SelectionScenes.kt`.

## I tasti con esito

**Cosa fa.** Un tasto che, toccato, **lavora** e poi dice com'è andata, con un glifo animato secondo il significato
dell'icona. Invia: l'aeroplanino si carica e, a invio riuscito, parte; a fallimento torna e diventa «Riprova». Salva:
l'anello sottile attorno all'icona, poi la spunta si disegna lungo il suo verso. Copia: la spunta per un secondo, senza
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

## Come si prova

**I test.** Un test Robolectric per scena, `FluidDynamicElementsUiTest` (sample): la scena si apre, i bersagli sono di
almeno 48 dp, la semantica è giusta (i nomi dei tasti, il ruolo di pulsante, il conteggio, le aree vive dell'esito e del
contatore), e il gesto principale fa ciò che deve. Sotto, i test di ogni pacchetto: `FluidSelectionToolbarTest` (D1 col
dispatcher vero, «Seleziona tutto» su `AllExcept`), `FluidFloatingTabBarSelectionTest` e `FluidTabSelectionHostTest` (la
tab bar che diventa le azioni e torna al pixel), `FluidActionStateTest`, `FluidActionToolbarTest` e
`FluidActionButtonUiTest` (soglia, tenuta, ripresa, parole, bersagli), `FluidActionGlyphsTest`, `FluidUndoCountdownTest` e
`FluidUndoCountdownUiTest`, `FluidNotificationSourceTest`, `FluidPageOpenGestureTest`, `FluidSharedElementTest`,
`FluidZoomPresetsTest` e `FluidElementToPageUiTest`.

**Le tracce del banco** stanno in `tools/glassbench/traces/f6b/`, una cartella per traccia, con le righe del piano (F6B,
«Il banco del movimento»):

| elemento | tracce |
|---|---|
| selezione | `selezione-entra`, `selezione-esce`, `selezione-back-confermato`, `selezione-back-annullato`, `selezione-senza-tabbar` |
| tasti con esito | `tasto-invia-riuscito`, `tasto-invia-fallito`, `tasto-salva`, `tasto-copia`, `tasto-scarica-percentuale`, `tasto-preferito-rifiutato`, `tasto-lavoro-breve` (curve `curve.csv`: i fotogrammi sono riprese della finestra) |
| «Annulla» e avviso | `invia-composto`, `annulla-contatore`, `annulla-scade`, `avviso-dal-tasto`, `avviso-annulla-torna`, `avviso-sorgente-sparita` |
| elemento → pagina | `elemento-zoom`, `elemento-sale-col-dito`, `elemento-cresce`, `elemento-dalla-barra`, `elemento-interrotto-25`, `elemento-interrotto-50`, `elemento-interrotto-75`, `elemento-giu-chiude` |

Il `README.md` della cartella dice, per ogni traccia, il test che la scrive, la variabile d'ambiente e dove sta il collage
di tutti i fotogrammi.

**Non verificato dai test**: come si vede in mano. Il collaudo sul Tab S9 (F6B.14) guarda la tab bar che diventa le
azioni a 120 Hz, il gesto indietro di One UI in selezione, TalkBack sugli esiti e sul contatore, il testo al 200%, il mini
lettore tirato col dito, il ripristino dopo `am kill` e la rotazione a metà viaggio.

## Dove guardare

- `engine-ui/…/ui/fluid/toolbar/FluidSelectionToolbar.kt`: `selection`, le posizioni, i preset, `FluidSelectionLeading`.
- `…/ui/fluid/FluidFloatingTabBar.kt` e `FluidAdaptiveNavigation.kt`: la tab bar che diventa le azioni.
- `…/ui/fluid/action/`: `FluidActionState.kt` (stato, esecutore, preset), `FluidActionButton.kt` (il tasto e il tipo della
  barra), `FluidActionGlyphs.kt` (i glifi).
- `…/ui/fluid/FluidUndoCountdown.kt`: il contatore; `FluidNotification.kt` e `FluidNotificationSource.kt`: l'avviso dal tasto.
- `…/ui/page/FluidZoomPresets.kt`, `FluidSharedElement.kt`, `FluidPageHostState.kt`: l'elemento che diventa pagina.
- La galleria: `sample/…/dynamic/` (`DynamicElements.kt` è l'indice).
