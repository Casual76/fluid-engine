<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/contenuto.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Contenuto: righe, card, liste, identità, testo, web, grafici

Tutto quello che sta *dentro* una schermata dopo la chrome: il gruppo di righe (`FluidListGroup` + `FluidListRow`), le
righe di impostazioni (interruttore, scelta, selettore, slider), le card e le aperture di sezione (`FluidCard`,
`FluidHero`, `FluidVividCard`), gli stati di caricamento (scheletri, barre, passi), i segni piccoli (pallino dal vivo,
cerchio di selezione, punti del pager), il testo (`FluidMarkdown`, testo che arriva, testo che si accende con l'audio),
il visore di immagini e il riordino con trascinamento. **Dalla 3.1.0 (Fase 1 di Pampa Mail)** anche le liste lunghe e paginate (`fluidGroupedItems`, `fluidSwipeActions`, `FluidJumpPill`), l'identità e la selezione (`FluidAvatar`, `FluidBadge`,
`FluidSelectionState`), i file (`FluidFileTile`), il contenuto web chiuso (`FluidWebContent`) e i grafici (`FluidDonutChart`, `FluidBarChart`, `FluidLineChart`). Le schermate stesse (`FluidScreen`, la barra, il fondale
ambientale) e il vetro come materiale sono in altre famiglie; qui si descrive come il contenuto vi si appoggia.
I file stanno in `engine-ui/.../ui/fluid/` e, per il gruppo, la riga e la card, in `engine-ui/.../ui/theme/FluidComponents.kt`.

**Modello mentale in due frasi.** Una lista è *un contenitore con righe dentro*: il contenitore è la superficie (e, se serve,
il vetro), la riga è solo testo, icona e un colore piccolo sulla piastrella. Il colore e il vetro stanno sempre un livello
più in alto di dove verrebbe naturale metterli: sul contenitore, sulla piastrella, mai sulla riga.

Catalogo delle firme complete: [`../api/contenuto.md`](../api/contenuto.md) (file di `fluid/`) e [`../api/tema.md`](../api/tema.md)
(`FluidListGroup`, `FluidListRow`, `FluidListDivider`, `FluidToneTile`, `FluidCard`, `FluidEmptyState`, che vivono in `theme/`).
Le regole di fondo (vetro, movimento, aptica): [`../regole.md`](../regole.md) e [`../design-system.md`](../design-system.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un elenco di voci in una schermata | `FluidListGroup { FluidListRow(...) ; FluidListDivider() ; ... }` dentro un `item { }` della schermata |
| un elenco **lungo** su un fondale di vetro | `fluidGlassGroups(items, key) { FluidListRow(...) }` (spezza in pannelli da 8 righe); non `items { }` con `FluidListGroup(glass = true)` per riga |
| un elenco lungo che **cambia mentre lo si legge** (una casella che riceve), o paginato | `fluidGroupedItems(count, key = { id })`: una riga per item, chiavi stabili, fondo opaco — [`contenuto-righe-liste.md`](contenuto-righe-liste.md), «Liste lunghe, una riga per elemento» |
| archiviare, segnare, eliminare con uno scorrimento di lato | `Modifier.fluidSwipeActions(state, startAction, endAction)` sulla `FluidListRow`, e le stesse azioni per TalkBack |
| «3 nuove in alto» senza spostare la lista | `FluidJumpPill` nell'`overlay` di `FluidScreen` |
| azioni di TalkBack su una riga (stella, archivia, seleziona) | `FluidListRow(customActions = …)` |
| una sezione breve di vetro dentro una colonna accanto ad altre | `FluidGlassGroup(items) { ... }` (un pannello solo, non spezza) |
| un'etichetta sopra un gruppo / una nota sotto | `FluidSectionHeader(title, detail)` / `FluidSectionFootnote(text)` |
| una riga on/off | `FluidToggleRow` — **non** una `FluidListRow` con un `FluidSwitch` nel `badge` (due bersagli, due vibrazioni) |
| una scelta singola fra poche voci, tutte visibili | un gruppo di `FluidChoiceRow` con `Modifier.selectableGroup()` — **non** radio button |
| una scelta singola fra molte voci o secondaria | `FluidPickerRow(selectedKey, options)`: la riga diventa il pop-up (serve un `FluidGlassModalHost`) |
| un valore continuo (volume, soglia, minuti) in una riga | `FluidSliderRow(valueLabel = "30 minuti")` |
| una riga che apre una pagina | `FluidListRow(onClick = …)`: freccia e ruolo pulsante vengono da soli |
| una riga che sceglie cosa mostrare accanto (elenco+dettaglio) | `FluidListRow(selected = …, disclosure = false)` |
| un colore per categoria (cartella, conto, tipo) sulle righe | `leadingTile = false` + `FluidToneTile(color = …)` nel `leading`; **non** colorare lo sfondo della riga |
| pillole di stato accanto al titolo | `FluidListRow(labels = { … })`; il `badge` è per un valore o un controllo stretto |
| un riquadro autonomo (non una lista) | `FluidCard(glass = true)`; `highlighted = true` per quello che deve dire una cosa |
| un dato in primo piano con fatti di contorno, in apertura di schermata | `FluidHero(tone, motif, …)`; se lo spazio conta, `FluidHeroBand` |
| un elemento isolato interamente del proprio colore | `FluidVividCard(FluidVividColors.from(start, end))` — **non** dentro un `FluidListGroup` |
| un testo vuoto con un motivo e una cosa da fare | `FluidEmptyState(title, detail, illustration)`; con un tasto: `FluidEmptyState(title, detail, actionLabel, onAction)` |
| un'etichetta in un colore che sceglie l'utente | `FluidStatusBadge(label, color)`: il testo lo sceglie il contrasto |
| il cerchio di una persona (monogramma o foto), o un mosaico | `FluidAvatar`, `FluidAvatarStack`; i colori da `FluidIdentityColors` — «Identità e selezione» in [`contenuto-testo-e-media.md`](contenuto-testo-e-media.md) |
| «ci sono 3 non lette», «c'è qualcosa» | `FluidBadge(count)` o `FluidBadge()` |
| selezione multipla, «Seleziona tutto» su una lista paginata, il back che esce dalla selezione | `rememberFluidSelectionState`, `FluidSelectionBackHandler` |
| un allegato, un file (anche compatto) | `FluidFileTile` / `FluidFileChip` |
| mostrare un HTML ricevuto (mail, newsletter) senza che faccia niente | `FluidWebContent` — [`contenuto-web.md`](contenuto-web.md) |
| ripartizione, confronto, andamento | `FluidDonutChart` (solo voci disgiunte) / `FluidBarChart` / `FluidLineChart` — [`grafici.md`](grafici.md) |
| «sto caricando» di una lista o di una griglia | `FluidSkeletonList` / `FluidSkeletonTile` / `FluidSkeletonText` (stessa altezza della riga vera: niente salto) |
| «sto caricando» di una schermata di cui non so la forma | `FluidLoadingBlock` (un `FluidSpinner` in un blocco da almeno 180 dp) |
| un'operazione senza durata nota | `FluidIndeterminateBar` o `FluidSpinner`; con una durata nota, `FluidProgressBar` |
| una procedura a passi | `FluidStepProgress(step, steps)` — **non** una `FluidProgressBar` con un testo a mano |
| un dato che si aggiorna da solo | `FluidLiveDot` accanto al dato |
| selezione multipla | `FluidSelectionMark(selected)` nel `leading` della riga, o sull'angolo di una miniatura (`onPhoto = true`) |
| i punti di un pager | `FluidPagerDots(state)` o `FluidPagerDots(pageCount, position)` |
| testo Markdown (nota, novità di una versione, risposta di un assistente) | `FluidMarkdown(markdown, style, selectable)` |
| il testo di un modello che sta arrivando | `FluidMarkdown(streaming = true)`, o `FluidRevealingText` se non è Markdown |
| il testo che segue un audio | `FluidSpokenText(text, words, positionMs = { … })` |
| Markdown senza segni (voce di sistema, anteprima, condivisione) | `FluidMarkdownParser.plainText(markdown)` |
| ingrandire un'immagine a tutto schermo | `FluidImageViewer(visible, …) { page -> … }`; per ingrandire un'immagine *dentro* una tua pagina, `Modifier.fluidZoomable(state)` |
| righe o tessere riordinabili | `rememberFluidReorderState` + `fluidReorderable` sull'elemento (lista, riga o griglia) |

## Regole trasversali

**1. Il vetro va sul contenitore, mai sulla riga.** Un `FluidListGroup(glass = true)` di dodici righe è un nodo di vetro, non
dodici: ogni superficie costa una registrazione di layer e una catena di `RenderEffect` sui propri limiti, quindi il materiale un
livello troppo in basso porta una schermata da otto superfici a ottanta. Testo, icone e badge sopra il vetro restano opachi al 100%.
`glass = true` è una *richiesta*: senza un canvas ambientale nello scope (schermata piatta, finestra a parte, sotto API 31) il
componente disegna la superficie opaca di sempre, ed è questo che rende sicuro aggiungerla prima che ogni schermata abbia il canvas.
Dentro un foglio o un pop-up di vetro (che pubblicano il proprio materiale) diventa vetro *impilato* sul foglio (3.0.0).
Hanno `glass`: `FluidListGroup`, `FluidCard`, `FluidMetricTile`, `FluidInlineMessage`, `fluidGlassGroups`/`FluidGlassGroup` (sempre).
Non l'hanno: `FluidHero`, `FluidHeroBand`, `FluidVividCard`, le righe (sono dentro un contenitore che l'ha o no).

**2. Il colore sta sulla piastrella, mai sul fondo della riga.** In un gruppo il `tone` (o un `FluidToneTile(color)`) tinge la
piastrella dell'icona e l'occhiello (`eyebrow`); una riga tinta per tono trasformava un gruppo ordinato in un mosaico. Il
corrispettivo per gli elementi *isolati* è `FluidVividCard`, dove invece tutta la superficie è del proprio colore.

**3. Una riga è un nodo solo per TalkBack, e quello che sta dentro la riga oltre titolo e sottotitolo non viene letto.** Vale per
`FluidListRow` con `onClick` (nome = titolo + sottotitolo + pillole + badge fusi, `Role.Button`, `selected`) e, in modo più
rigido, per le righe di impostazioni: `FluidToggleRow`, `FluidChoiceRow`, `FluidPickerRow` **azzerano** la semantica interna
(`clearAndSetSemantics`) e si danno nome «titolo, sottotitolo». Perché: il `ListItem` di Material fonde da sé la semantica e Compose non
fonde un figlio che a sua volta fonde; senza questo la riga erano due nodi (uno col clic e senza nome, uno col nome e senza clic).
Conseguenza pratica: l'informazione che serve a chi non vede va nel **sottotitolo**, non in un badge o in un'icona.

**4. Le righe cedono, non crescono.** La pressione di una riga è una tinta (`onSurface` al 7%, istantanea in entrata, 260 ms in
uscita) più il bordo del fuoco da tastiera e la metà della tinta sotto il mouse; mai una scala, perché una riga che si rimpicciolisce in
una pila di righe uguali rompe la sagoma del gruppo. Le card e le tessere invece cedono con una scala (`FluidCard` 0,974,
`FluidVividCard` 0,97); i *tasti* (`FluidHero` azione, `FluidButton`) crescono.

**5. Una vibrazione per momento.** Le righe di impostazioni disattivano la vibrazione generica della riga (`haptic = null`) e suonano
solo la propria: `ToggleOn`/`ToggleOff` per l'interruttore, `Tick` per una scelta che si sposta, `Open` per il selettore, `Tick`
per ogni posto attraversato dal riordino. Se componi una riga tua con `fluidRowPressable`, decidi tu quale colpo suona
e passa `haptic = null` se c'è già.

**6. Movimento ridotto = fermo, mai a metà.** Gli scheletri stanno a una trasparenza fissa (`FluidSkeletonDefaults.RestAlpha`),
il pallino dal vivo sta pieno (un segno a mezza opacità che non si muove si legge come spento), la barra a passi arriva senza
passaggi, il testo che arriva compare com'è, la molla del riordino e dello zoom si salta. Tutto passa da `LocalFluidMotionPolicy`.
Eccezione da sapere: `FluidSpinner` e `FluidIndeterminateBar` non consultano la politica (nel sorgente non c'è alcun riferimento).

## Dove sta cosa

| scheda | contenuto |
|---|---|
| [`contenuto-righe-liste.md`](contenuto-righe-liste.md) | `FluidListGroup`, `FluidListRow`, `FluidListDivider`, `FluidToneTile`; `FluidSectionHeader`, `FluidSectionFootnote`, `FluidEmptyState`, `FluidLoadingBlock`; `fluidGlassGroups`, `fluidGlassGroupsIndexed`, `FluidGlassGroup`; `FluidToggleRow`, `FluidChoiceRow`, `FluidPickerRow`, `FluidSliderRow`; `FluidCard`, `FluidMetricTile`; `FluidHero`, `FluidHeroBand`, `FluidVividCard`; dalla 3.1.0 `fluidGroupedItems`, `FluidGroupedMaterial`, `fluidSwipeActions`, `FluidJumpPill`, `customActions` della riga, `FluidEmptyState` con azione, `FluidStatusBadge(label, color)` |
| [`contenuto-web.md`](contenuto-web.md) | `FluidWebContent`: il contenitore web chiuso, cosa resta all'app, altezza, scurimento, stampa (sezione «Prestazioni» in sospeso) |
| [`grafici.md`](grafici.md) | `FluidDonutChart`, `FluidBarChart`, `FluidLineChart`, `FluidChartLegend`, i riquadri di stato; scale, accessibilità, colori a 3:1 |
| [`contenuto-testo-e-media.md`](contenuto-testo-e-media.md) | `FluidAvatar`, `FluidAvatarStack`, `fluidMonogram`, `FluidIdentityColors`, `FluidBadge`, `FluidSelectionState`, `FluidFileTile`, `FluidFileChip`; `FluidSkeleton*`, `FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`, `FluidStepProgress`; `FluidLiveDot`, `FluidSelectionMark`, `FluidPagerDots`; `FluidMarkdown` + `FluidMarkdownParser` (cosa supporta e cosa no), `FluidRevealingText`, `FluidSpokenText`; `FluidImageViewer` + `fluidZoomable`; `fluidReorderable` |

## Token, costanti, Defaults

| nome | valore | dove |
|---|---|---|
| `FluidRadius.Small / Control / Card / Group / Sheet` | 10 / 12 / 18 / 22 / 38 dp | `FluidShape.kt` (gruppo = Group, card = Card, piastrella = Small) |
| `FluidToneTileSize` | 32 dp (contenuto 19) | `FluidComponents.kt` |
| `FluidGroupOpenEdge` | 2 dp | idem |
| `FluidGlassGroupMaxRows` | 8 | `FluidGlassGroups.kt` |
| `FluidSkeletonDefaults` | `LowAlpha` 0,06 · `HighAlpha` 0,13 · `RestAlpha` 0,09 · `Shape` = `FluidRadius.Small` · `TileShape` = `FluidRadius.Card` · `LineHeight` 13 · `TitleHeight` 15 · `CaptionHeight` 11 dp | `FluidSkeleton.kt` |
| `FluidLiveDotDefaults.Size` | 7 dp | `FluidLiveDot.kt` |
| `FluidMotion.PulseMillis` / `PulseLowAlpha` / `PulseFillLowAlpha` | 900 ms / 0,35 / 0,55 | `FluidMotion.kt` |
| `FluidMotion.SpinnerMillis` / `IndeterminateMillis` | 900 / 1100 ms | idem |
| `FluidPagerDotsDefaults` | `DotSize` 7 · `ActiveSize` 10 · `Spacing` 8 dp · `InactiveAlpha` 0,35 · `ActiveAlpha` 0,95 · `MaxVisible` 7 | `FluidPagerDots.kt` |
| `FluidStepProgressDefaults` | `MaxSegments` 12 · `SegmentHeight` 4 · `SegmentGap` 4 dp · `label(step, steps)` | `FluidStepProgress.kt` |
| `FluidSelectionMarkDefaults` | `Size` 24 · `RingWidth` 1,75 dp · `PhotoScrimAlpha` 0,28 · `description(selected)` | `FluidSelectionMark.kt` |
| `FluidRevealMillis` / `FluidRewriteMillis` | 180 / 120 ms | `FluidTextReveal.kt` |
| `FluidZoom` | `MinScale` 1 · `DefaultMaxScale` 4 · `DoubleTapScale` 2,5 (+ funzioni pure) | `FluidImageViewer.kt` |
| `FluidReorderDefaults` | `LiftScale` 1,04 · `AutoScrollEdge` 72 dp · `AutoScrollMaxSpeed` 900 dp/s · `FallbackShadow` 10 dp | `FluidReorderable.kt` |
| `LocalFluidItemSpacing` | 14 dp (`FluidScreenDefaults.ItemSpacing`) | `FluidScreen.kt` |

Parole traducibili (italiano di serie, un'app tradotta le passa tutte): le parole dei grafici, dei file, dell'avatar e del contenuto web stanno in `FluidLabels` (con `english()`); `FluidMarkdownStrings`, `FluidImageViewerStrings`, `FluidReorderStrings`, `FluidStepProgressDefaults.label`, `FluidSelectionMarkDefaults.description`, il `pageLabel` di `FluidPagerDots`.

## Cosa è verificato dai test

- **`FluidMarkdownParserTest`** (33 test, JVM): ogni riga della tabella dei costrutti sopra — paragrafi e a capo, titoli e cancelletti di chiusura, grassetto/corsivo/barrato annidati, segni aperti che restano testo (lo stream a metà), `_` in una parola, link e URL nudi, escape, marcatori dei chip,
  elenchi (numero di partenza, annidamento, righe vuote, numero in mezzo a un paragrafo), blocchi di codice chiusi e aperti, citazioni, tabelle (allineamenti, intestazione senza trattini, numero di colonne sbagliato), righe, `plainText`, cose da fare, le misure dei titoli (`headingFor`). Non c'è un test di UI del renderer.
- **`FluidTextRevealTest`** (10): il tracker delle parole (primo testo già dentro, parole nuove, parola tagliata, riscrittura più veloce, maschera, durata zero), l'`AnnotatedString` (stili e link). **`FluidSpokenTextTest`** (6): `fluidSpokenChars` in tutti i casi.
- **`FluidGlassGroupsTest`** (10): il piano di taglio (vuota, intera, primo/medio/ultimo, ogni pannello tranne l'ultimo si unisce al successivo, ogni riga in un pannello solo, `maxRows < 1` rifiutato, l'altezza dichiarata minore di quella disegnata) e, in `FluidGapsTest`, che venti righe diventano tre item.
  **Non** verificato: il taglio invisibile e il costo (si guardano in mano).
- **`FluidSkeletonTest`** (10), **`FluidPagerDotsTest`** (13), **`FluidStepProgressTest`** (8), **`FluidSelectionMarkTest`** (5), **`FluidLiveDotTest`** (7): la matematica pura (respiro, larghezze, altezze, peso e rampa dei punti, 40% di «passo 2 di 5», segmenti, colore della spunta, fase dell'orologio). `FluidCapsulesAndPartsTest`
  (sample, Robolectric): il segno dice lo stato e si può silenziare, i punti dicono la pagina, una pagina sola non ha punti, l'intestazione a passi è un blocco solo nelle due forme, il pallino è muto senza motivo, la lista di scheletri si nomina una volta, e i pezzi compongono a movimento ridotto.
- **`FluidZoomTest`** (22) e **`FluidReorderTest`** (24): i conti — limiti, spostamento, punto fermo sotto le dita, doppio tocco, gradini; bersaglio sotto il centro, traslazione, scorrimento automatico, `fluidMoved`. In `MoreComponentsTest` (sample): il visore si apre nell'host con il contatore, la chiusura, nessun crash senza host, doppio tocco e pizzico,
  azioni di TalkBack, un dito a scala 1 lasciato al pager; il riordino (trascinare due posti in giù riordina la lista dell'app, un colpo per sollevamento e per posto, il tenuto-premuto breve non solleva, muovere prima del tenuto è uno scroll, `enabled = false` non fa niente, un posto vietato non si prende, azioni di TalkBack, griglia).
- **`FluidRowsAndSheetsTest`** (sample, 22): il tocco su `FluidToggleRow` commuta una volta e vibra come l'interruttore, un nodo solo con `Role.Switch`, disattivata non risponde né si trascina, il pomello si trascina con una vibrazione sola e una deriva verticale non scorre la pagina; la scelta è un radio, `Tick` solo se si sposta, la riga già scelta avvisa senza vibrare.
  `FluidMatrixTest`: `FluidListRow` con `onClick` è **un nodo solo** col nome, `Role.Button` e `selected`; senza `onClick` è un'etichetta senza ruolo. `FluidListRowLabelsTest` (7): accanto o sotto, la soglia di un quarto.
- **`FluidHeroLayoutTest`** (5): le colonne delle metriche (3 a 331 dp, 2 a 285, 1 a 215, e con la scala del carattere). **`FluidVividColorsTest`** (3): il contenuto leggibile su entrambi i capi (4,5:1; 3,5 per le famiglie a luminanza media), sette partenze diverse e contenuto ≥ 4,0:1 della fascia in ogni schema.
- **Fase 1 di Pampa Mail (3.1.0), liste lunghe.** `FluidGroupedItemsTest` (`engine-ui`, 14): il pezzo di ogni riga (una sola intera, primo e ultimo, mai un angolo tondo nel mezzo), una riga che entra in cima o l'ultima che esce, il vuoto
  riassorbito e l'altezza dichiarata mai negativa, la sagoma stabile per pezzo, l'opaco di serie, **50.000 righe senza materializzare niente**. `FluidSwipeActionsTest` (23): la soglia (35% col tetto di 180 dp), di chi è il dito (il verticale
  vince, il diagonale no, il bordo no), il 1:1 e la resistenza, l'elastico dal lato senza azione, l'isteresi, la conferma e l'annullo (anche con la spinta contraria), l'inchiostro a 4,5:1 su qualunque fondo. `FluidLongListsTest` (`sample`, 25): una
  riga è un item, a filo e col tocco sulla fascia richiusa, **una riga che entra in cima non sposta quella che si legge**, lo stato di una riga segue la chiave anche dopo la ricreazione, 50.000 righe e un salto lontano, i tre fondi senza
  canvas, il gesto dall'inizio alla fine (un solo `Threshold`, l'azione una volta sola, il verticale è della lista, la pressione lunga del menu, il bordo), le azioni dello scorrimento e quelle della riga **sullo stesso nodo**, spenta, testo al
  200% e tema scuro, movimento ridotto, la pillola «N nuove», lo stato vuoto con azione e la pillola a colore libero.
- **Identità, selezione, file.** `FluidMonogramTest` (34) e `FluidIdentityColorsTest` (13): le iniziali (emoji, bandiere, accenti scomposti, bidi, indirizzi, scritture non latine, tetto di 512 caratteri) e la palette (contrasti provati su
  tutte le tinte nei due temi, indice stabile, giro di `colorAt`). `FluidBadgeTest` (8). `FluidSelectionStateTest` (24): «Seleziona tutto» senza materializzare chiavi, esclusioni, testimone, `retainOnly`/`forget`, il saver (anche con chiavi di
  dominio, il tetto che salva vuoto). `FluidIdentityTest` (`sample`, 29): avatar decorativo o nominato, bersaglio da 48 dp, casella con lo stato nella lingua dell'app, il back che esce dalla selezione **e il menu aperto che si chiude prima**,
  la selezione che sopravvive alla ricreazione, righe annidate (16 dp per livello, tetto al sesto), `FluidBarAction` con l'avatar (un bottone con nome e contenuto muto, menu al tocco e al tenuto), `FluidCapsuleAction(selected)` come interruttore.
  `FluidFileInfoTest` (6) e `FluidFileTileTest` (7): tutte le categorie, **l'estensione eseguibile prevale**, dimensioni locali e confini, il troncamento che conserva l'estensione, semantica in italiano e inglese, nome lungo a 200% e scuro, chip
  con bersaglio espanso che cede alla lista, il progresso che non ricompone.
- **Non provati da test**: `FluidPickerRow` e `FluidSliderRow` (solo la demo `SettingsTab`), `FluidCard` (`enabled`, `sampleOnce`: vetro e costo si guardano in mano), `FluidHero`/`FluidHeroBand`/`FluidVividCard` nel disegno (solo la matematica del colore), il rendering di `FluidMarkdown`, `FluidProgressBar`, `FluidSpinner`, `FluidSkeletonRow` nel layout vero.

## Dove guardare

| file | cosa c'è |
|---|---|
| `engine-ui/.../ui/theme/FluidComponents.kt` | `FluidTone`, `FluidListGroup`/`FluidGroupSegment`/`FluidGroupOpenEdge`, `FluidListRow`, `FluidListDivider`, `FluidToneTile`, `FluidCard`, `FluidMetricTile`, `FluidEmptyState`, `FluidLoading`; il modificatore privato `fluidContentGlass` che dà il vetro al contenuto |
| `engine-ui/.../ui/theme/FluidListRowHeadline.kt` | titolo + pillole, la regola «accanto o sotto» (`fluidRowLabelPlacement`) |
| `engine-ui/.../ui/fluid/FluidContent.kt` | `FluidSectionHeader`, `FluidSectionFootnote`, `FluidLoadingBlock` |
| `engine-ui/.../ui/fluid/FluidGlassGroups.kt` | `fluidGlassGroups`, `fluidGlassGroupsIndexed`, `FluidGlassGroup`, il piano di taglio (interno, testato) |
| `engine-ui/.../ui/fluid/FluidSettingRows.kt` | le quattro righe di impostazioni, `FluidPickerOption` |
| `engine-ui/.../ui/fluid/FluidHero.kt`, `FluidVividCard.kt` | apertura, fascia, motivi; card vivida, colori, effetti |
| `engine-ui/.../ui/fluid/FluidSkeleton.kt`, `FluidProgress.kt`, `FluidStepProgress.kt`, `FluidLiveDot.kt` (e il respiro condiviso `rememberFluidPulse`) | caricamento, barre, passi, pallino |
| `engine-ui/.../ui/fluid/FluidSelectionMark.kt`, `FluidPagerDots.kt` | segni piccoli |
| `engine-ui/.../ui/fluid/FluidMarkdown.kt`, `FluidMarkdownParser.kt`, `FluidTextReveal.kt`, `FluidSpokenText.kt` | Markdown (resa, parser), testo che arriva, testo che segue l'audio |
| `engine-ui/.../ui/fluid/FluidImageViewer.kt`, `FluidReorderable.kt` | visore e zoom, riordino |
| `sample/.../catalog/RowsAndSheetsDemo.kt`, `SettingsTab.kt`, `catalog/CapsulesAndPartsDemo.kt`, `catalog/MoreComponentsDemo.kt`, `GapsDemo.kt`, `MaterialTab.kt` | i call-site veri di righe, selettore/slider, pezzi piccoli, riordino/visore, pannelli lunghi, fascia e card vivida |
| `engine-ui/.../ui/fluid/FluidGroupedItems.kt`, `FluidSwipeActions.kt`, `FluidJumpPill.kt` | le liste lunghe, il gesto di lato, la pillola delle novità (3.1.0) |
| `engine-ui/.../ui/fluid/FluidAvatar.kt`, `FluidIdentityColors.kt`, `FluidBadge.kt`, `FluidSelectionState.kt`, `FluidFileTile.kt`, `FluidFileInfo.kt` | identità, selezione, file (3.1.0) |
| `engine-ui/.../ui/fluid/FluidWebContent.kt`, `FluidCharts.kt`, `FluidChartMath.kt` | il contenitore web, i grafici e i loro conti (3.1.0) |
| `sample/.../catalog/LongListsDemo.kt`, `IdentityDemo.kt`, `WebContentDemo.kt`, `ChartsDemo.kt`, `FileTileDemo.kt` | i call-site veri della Fase 1 |
| `engine-ui/src/test/.../ui/fluid/*Test.kt`, `sample/src/testDebug/.../*Test.kt` | i test citati sopra |
