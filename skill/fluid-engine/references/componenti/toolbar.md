# La barra dichiarata (`toolbar`)

Dalla 4.0 (piano «iOS in Android», F4A.2) i tasti della barra in alto di una `FluidScreen` si **dichiarano**: la pagina
dice chi sono (un'icona, una scritta, l'azione principale, la conferma, indietro, Chiudi, uno spazio, un gruppo, ciò che
sta sempre nel menu «Altro»), e la barra decide da sola gruppi, vetri, misure e trabocco. Ogni cambio di stato della
pagina (da «sfoglia» a «seleziona», un tasto che diventa principale, un tasto che non ci sta più) **si trasforma da solo
sul piano flottante**: la pagina non scrive animazioni. È `toolbar { ToolbarItem(…) }` di SwiftUI. Lo slot `actions` di
sempre resta identico; se la pagina passa `toolbar`, vince `toolbar`.

Con le altre famiglie: il piano flottante che porta i vetri è in [piano-flottante.md](piano-flottante.md); la schermata,
il titolo grande e il bordo dello scorrimento in [schermata.md](schermata.md); la barra in basso (tab bar, capsula, campo
di ricerca) in [barre.md](barre.md); la barra unica dell'host delle pagine in [pagine-back-e-barra.md](pagine-back-e-barra.md).
Le firme: [../api/barre.md](../api/barre.md).

**Modello mentale.** La pagina scrive **lo stato**, con un `if`, come per il piano flottante. L'`id` di un tasto è la sua
identità fra due stati: **lo stesso `id` è lo stesso tasto**, che resta, si sposta o cambia icona; un `id` nuovo nasce dal
vicino o compare per lente. La barra fa un vetro per gruppo, e un gruppo che guadagna o perde un'icona è **lo stesso
vetro** che si allarga o si stringe.

## Il caso tipo

```kotlin
var selecting by remember { mutableStateOf(false) }
FluidScreen(title = "Note", onBack = back, toolbar = {
  if (selecting) {
    button("share", FluidIcons.IosShare, "Condividi", onClick = share)
    button("delete", FluidIcons.Delete, "Elimina", onClick = delete, role = FluidButtonRole.Destructive)
    confirm(onClick = { selecting = false })          // la spunta tinta, sola a destra
  } else {
    text("select", "Seleziona", onClick = { selecting = true })
    button("more", FluidIcons.MoreHoriz, "Opzioni", onClick = more)
  }
}) {
  items(notes) { … }
}
```

Nessuna animazione: l'indietro resta fermo (lo stesso vetro), «Seleziona» e i tre puntini se ne vanno, condividi ed
elimina nascono **in un vetro solo** (sono icone vicine), la conferma nasce tinta e sola a destra. È la scena
`sfoglia-seleziona` della pagina «Barre (prova)» della galleria.

## La DSL

| chiamata | che cosa è | gruppo |
|---|---|---|
| `back(onClick, label?, id = FluidToolbarIds.Back)` | il chevron in un tondo di vetro, a sinistra. Con `FluidScreen(onBack = …)` e senza un `back` o un `close` dichiarati lo mette la barra | a sé |
| `close(onClick, label?, id = FluidToolbarIds.Close, placement = Navigation)` | la X in un tondo di vetro, di serie dove sta l'indietro | a sé |
| `confirm(onClick, label?, id = FluidToolbarIds.Confirm, enabled, tint)` | la **spunta tinta** (di serie l'accento, icona chiara), a destra: l'azione principale | a sé |
| `primary(id, icon, label, onClick, enabled, tint)` | l'azione principale con un'icona sua, vetro tinto | a sé |
| `button(id, icon, label, onClick, placement, priority, role, tint, selected, enabled, badge, badgeDot, sharedBackground)` | un tasto con un'icona (26 dp) | con le icone vicine dello stesso lato |
| `text(id, text, onClick, placement, priority, role, tint, enabled)` | un tasto con una scritta («Seleziona»), largo quanto la scritta misurata più 16 dp per lato | a sé (testo e icone mai insieme) |
| `item(id, label?, width = 44.dp, placement, priority, onClick?) { … }` | un elemento **libero col suo vetro** per ciò che la DSL non copre (un avatar, un indicatore) | a sé |
| `spacer(width?)` | uno spazio fisso: spezza un gruppo in due vetri (di serie 12 dp, la distanza fra due gruppi) | — |
| `flexibleSpacer()` | uno spazio flessibile: spinge ciò che segue all'altro estremo | — |
| `group(id, placement, priority, sharedBackground) { … }` | un gruppo esplicito: i suoi tasti stanno insieme e non si uniscono ai vicini di fuori | il suo |
| `overflow(label?) { … }` | ciò che sta **sempre** nel menu «Altro» (il tasto coi tre puntini) | — |
| `title(mode, subtitle?)`, `subtitle(text)` | il modo del titolo (`Large`, `Inline`, `None`) e il sottotitolo | — |
| `search(spec)` | la ricerca della pagina (vedi «La ricerca») | — |

`role`: `FluidButtonRole.Destructive` colora l'icona (e la voce del menu) col colore d'errore; `Confirm` e `Close` fanno
del tasto una conferma o un Chiudi; `Cancel` lo mette a sinistra. `placement` (`FluidToolbarPlacement`) e `priority`
(`FluidToolbarPriority`: `Low`, `Automatic`, `High`) sono quelli di SwiftUI; `Principal` mette un elemento **al centro, al
posto del titolo**. `id` diversi per tasti diversi: due tasti con lo stesso `id`, o due azioni principali (una conferma
**e** una principale), sono un errore del modello.

## Che cosa fa da sola

- **I gruppi** (R1 B1–B10): le icone vicine sullo stesso lato condividono un vetro; una scritta, l'azione principale, la
  conferma, indietro e Chiudi hanno il loro; uno `spacer` spezza un gruppo, un `flexibleSpacer` separa e spinge. Un gruppo
  si **divide** quando un suo tasto diventa principale, e torna unito quando smette.
- **Le misure** (R6, `FluidMetrics`): icone a 26 dp, vetro alto 44, passo 44 dp dentro un gruppo, 12 dp fra due gruppi,
  16 dp dai bordi. L'indietro sta esattamente dove stava quello della barra di sempre.
- **Il trabocco** (R5 B11): al più tre gruppi, «Altro» **compreso**, oltre ai vetri d'uscita (indietro e Chiudi): su
  iPhone si vedono indietro più tre vetri. Le uscite occupano comunque larghezza; quando il posto non basta escono prima
  i tasti `Low`, poi `Automatic`, poi `High`, a parità i più interni.
  Il tetto vale anche su tablet largo. Un gruppo esplicito tiene insieme i tasti visibili, ma la priorità decide
  per ogni tasto chi resta in barra. Indietro, Chiudi e la principale hanno precedenza. In una finestra stretta
  la conferma testuale si accorcia e il suo nome accessibile rimane intero. Se lo spazio fisico è insufficiente
  anche per le lenti, «Altro» resta visibile e raccoglie i comandi. Le azioni disabilitate rimangono spente
  nel menu e TalkBack propone solo le azioni eseguibili.
  Con `FluidToolbarPlacement.BottomBar` lo spazio e il menu «Altro» si calcolano nella barra inferiore.
  Una testata autonoma (`FluidCollapsingTopBar` o `FluidPageChrome` senza host) conserva quei comandi nel proprio «Altro».
  `PinnedTrailing` occupa il bordo destro. Il collegamento che lo sposta nel menu con la ricerca attiva arriva in F4B.2.
- **Il piano**: la barra è un `FluidGlassContainer` alto quanto la riga; ogni gruppo è un pezzo con l'identità del suo
  vetro (`FluidToolbarGlassId`: il gruppo **alla nascita** del vetro, che il registro del piano espone), così un gruppo
  che cresce è lo stesso vetro, una divisione lascia il vetro alla parte più grande e le altre nascono dal vicino, una
  fusione lo prende dalla parte più grande e le altre confluiscono. Un tasto che il modello fa ripartire per lente
  (troppo lontano, incrociato) non trascina il suo vetro.
- **Il contenuto**: ogni tasto ha la sua chiave di contenuto (simbolo, tinta, selezione) in una
  `FluidGlassContentTransition` sua: un tasto che cambia icona la incrocia sfocandosi e il vetro non si muove; quello che
  resta mantiene la propria identità. Quando esce, il simbolo si sfoca alle coordinate che occupava nella barra.
  Il vetro che si stringe può coprirlo. Quando si sostituisce il contenuto, entrata e uscita condividono la finestra R7.
  Se l'identità ritorna durante l'uscita, la barra cancella la copia precedente.
  Il pezzo del gruppo passa `fluidGlassContentAnchor(Start/End)` secondo il lato (`Center` al centro): mentre la
  capsula cambia larghezza, il contenuto segue il bordo ancorato e ogni icona resta alla sua posizione d'arrivo,
  senza la deriva di metà variazione che produceva il centro. I bordi sono logici (anche RTL); un eventuale rimbalzo
  del bordo porta ancora il contenuto con sé. Fuori dalla barra l'ancora di serie è `Center`, identica al piano F3.12a.
- **I tempi di un gruppo che cambia larghezza** (F4A.5b, misurati sulle due clip di Apple a tempo vero, il telefono a
  4:44 e il render a 4:48, che coincidono): è un **cambio di larghezza** del piano (`FluidMotion.PlaneResizeMillis`,
  535 ms a vista). Il bordo che si chiude o si apre parte dopo 24 ms e fa metà strada a ~175 ms (fino a F4A.5 a ~90);
  il vetro intero è **trascinato** nel suo verso di 0,155 H, al colmo a ~300 ms, e torna (il «+» di Apple va a destra
  di 0,16 H e torna; il «rientro» del bordo fermo del gruppo che si allarga è lo stesso trascinamento), e l'icona che
  resta va col suo vetro, senza scivolarci dentro; il vetro si gonfia del 13,5% a ~200 ms. Il tondo che nello stesso
  cambio **cambia solo simbolo** respira (+16,5% a ~200 ms): il gruppo dichiara al piano cosa porta
  (`fluidGlassContentKey`, interno). Un cambio fatto **solo** di icone non muove i vetri.
- **Il contenuto che resta** (F4A.5b): l'icona che resta in un gruppo che cambia larghezza **si ammorbidisce come in
  Apple** (0,8 della sfocatura dello scambio, nitida a ~300 ms) e torna: è la scelta di Alessio del 17 novembre 2026
  («morbido, come Apple»), sulle clip del telefono a tempo vero. Con `gallery.tune barkeep=sharp` resta nitida (com'era
  fino a F4A.5). Un’icona che conserva ID e simbolo ha una sola copia visibile lungo tutto lo scambio.
  Quando cambia gruppo, l’icona segue la lastra tra il posto iniziale e quello finale. Le icone mantenute nello stesso
  gruppo seguono la larghezza effettiva del vetro tra i propri centri nella barra. Se il posto coincide nei due stati, il
  glifo resta sul pixel di layout anche quando il vetro rincula.
  Il criterio è «chi resta non salta». In `principale` l'apertura del varco richiede 12 dp fra i due riposi
  con l'ancora a destra. Il glifo e il suo bersaglio percorrono la stessa traiettoria e tornano allo slot al termine.
- **Selezione e tinta per tasto.** `selected` colora lo sfondo del singolo posto e la sua icona. Il vetro di un’azione
  principale ha la sua pellicola anche accanto a un gruppo neutro. La tinta sfuma attraverso il collo e si spegne
  mentre la lastra entra nel vicino. Il simbolo assume il colore della nuova azione in proporzione alla pellicola visibile.
  Il fondo della selezione e l'accento del glifo leggono lo stesso progresso. Una ripartenza del vetro conserva la pellicola già mostrata.
- **Il titolo**: `Large` (di serie) è il titolo grande che scorre e si aggancia, com'era; `Inline` è solo quello
  agganciato (nessun titolo nella lista); `None` nessuno. Il **sottotitolo** sta sotto il titolo grande e, agganciato,
  sotto quello della barra, più piccolo (il titolo sale di 16 dp per fargli posto). Gli **inset** del titolo agganciato
  li dà la misura d'**arrivo** dei gruppi, non il vetro in volo: mentre un gruppo cambia larghezza il titolo non balla.
- **Semantica e bersagli**: ogni tasto ha il suo nome (`label`; indietro, Chiudi, conferma e «Altro» con le parole
  dell'engine) e il ruolo di pulsante, la selezione e il numero del badge come stato; il bersaglio è almeno 48 × 48 dp,
  centrato sul tasto, senza cambiare il vetro. Un tasto spento ha il contenuto al 38%.
  Per un avatar o un contenuto libero si danno nome e comando al posto, per esempio
  `item("profilo", label = "Profilo", onClick = apriProfilo) { Avatar() }`. TalkBack legge quel nome e invoca
  la stessa azione dal trabocco. Per `back()` e `close()` il callback è obbligatorio e manca un parametro
  `enabled`. Per rimuovere l'uscita durante un salvataggio occorre anche togliere l'eventuale `onBack` automatico.
  Le parole di serie vengono da `LocalFluidLabels`: `back`, `close`, **`confirm`** («Conferma» / «Confirm») e
  **`more`** («Altro» / «More»), personalizzabili alla radice e indipendenti da `dateTimeConfirm` e `actions`.

## La barra in basso

Una `FluidScreen` posa automaticamente i comandi sopra la navigazione di sistema. Se l’app fornisce
un inset per la tab bar, i comandi stanno sopra di essa. La lista riserva lo spazio per l’ultima riga.
Per lasciare il filtro a sinistra e le altre azioni a destra si mette lo spazio flessibile nel gruppo in basso.

```kotlin
toolbar = {
  group("foto", placement = FluidToolbarPlacement.BottomBar) {
    button("filtro", FluidIcons.FilterList, "Filtra", apriFiltri)
    flexibleSpacer()
    button("preferito", FluidIcons.Favorite, "Preferito", cambiaPreferito)
    if (separato) spacer()
    button("aggiungi", FluidIcons.Add, "Aggiungi", aggiungi)
  }
}
```

Ogni posto misura 48 dp e porta l’icona della barra da 26 dp. Con lo spaziatore fisso il più si stacca
dal gruppo e il suo bersaglio lo segue. Un gruppo annidato eredita la posizione del genitore, un tasto con
posizione esplicita usa la propria. In un `FluidPageHost` la barra inferiore appartiene all’host. Al push e al
pop legge la dichiarazione della destinazione. Durante il gesto annullabile conserva i comandi della pagina
in primo piano. La politica `barMinimize` della pagina guida anche il ritiro verso il basso.
La scena «Barra in basso» in Chrome si apre con `gallery.page=bottombar`.

## Le sorgenti delle transizioni (F5A.1)

Ogni elemento posato della toolbar dichiarata ha una sorgente con il proprio `id`. Nel contesto della pagina che
dichiara la barra, `rememberFluidToolbarTransitionSource("ordina")` restituisce il riferimento all'elemento.
Tutti i tasti di un gruppo restituiscono la stessa capsula. Il riquadro viene dal pezzo del piano a riposo e dal suo
ritaglio in viaggio, compresi i raggi correnti. Se il piano non ha il pezzo, si usa il riquadro del tasto.

Un comando trasferito nel trabocco perde la propria sorgente finché resta una voce del menu. «Altro» ha un'identità
interna che la barra può usare per aprire il trabocco dalla sua capsula.
L'host usa il contesto della pagina che dichiara la barra, così due pagine possono dichiarare lo stesso `id`.
Il riferimento della pagina di partenza torna disponibile quando la pagina riprende il suo posto. Vedi
[Sorgenti delle transizioni](menu-modali.md#sorgenti-delle-transizioni).

## La ricerca

`FluidScreen(search = FluidSearchSpec(…))` o `search(spec)` nel blocco (che vince): un punto solo,
`FluidToolbarSearchSlot` (`ui/fluid/toolbar/FluidToolbarSearch.kt`). Posa il `FluidSearchField` sotto il titolo:
sempre aperto, o richiudibile in un tondo se la pagina chiede
`behavior = CollapseToButton` e possiede l'apertura (`active`, `onActiveChange`); senza fuoco automatico. La posizione
nella barra, il passaggio da tasto a campo, i vicini e la tastiera sono di F4B, che riscrive quel file senza toccare
`FluidScreen` né la DSL.

La schermata usa `query`, `onQueryChange`, `prompt` e `onSearch`. Con `CollapseToButton` legge anche `active`
e `onActiveChange`. Senza un proprietario dello stato di apertura mostra il campo aperto. `suggestions`, `scopes`,
`purpose` ed `expansion` acquisteranno effetto in F4B.2. In F4A le posizioni diverse da `Principal` usano il campo
sotto il titolo. Solo `FluidScreen` posa il campo, anche se gli altri ingressi della DSL accettano `search(spec)`.

Con `FluidSearchPlacement.Principal`, `FluidScreen` ritira la barra andando verso il fondo della lista. Titolo e tasti
si sfocano e svaniscono seguendo il dito. Il campo conserva il fuoco, risale sul suo riempimento piatto e passa al
vetro vicino alla barra di stato. Il rilascio a metà chiude la corsa con una molla e lo scorrimento inverso rivela la barra.
Per le altre posizioni e le opzioni sugli inset, vedi [FluidScreen](schermata.md#fluidscreen).

Nell'host il campo fissato si disegna sopra la striscia che campiona la lista. I comandi già ritirati lasciano la
barra al pop. Nel passaggio tra uno slot `actions` e un gruppo dichiarato, il vetro dello slot sostiene il gruppo
durante l'ingresso delle icone.

Con movimento ridotto il ritiro usa solo l'opacità per titolo e azioni. I cambi della barra dichiarata si incrociano in
dissolvenza nei rispettivi posti di riposo e i bersagli del nuovo stato sono disponibili nella geometria finale.

## Dove si usa

- `FluidScreen(toolbar = { … })`: la barra in alto della schermata.
- `FluidCollapsingTopBar(toolbar = { … })`: la barra di chi ha la sua lista; il titolo segue la piega di `collapse`
  (`Large` e `Inline` lì sono lo stesso), `None` lo toglie.
- `FluidPageChrome(toolbar = { … })` (4.0, F4A.3): la barra di una pagina che non è una `FluidScreen`.
- **Dentro un `FluidPageHost`** (o `FluidNavHost`) la barra la disegna l'host, **una sola, dal modello** (F4A.3): sotto,
  «Fra pagine».

## Fra pagine (F4A.3)

Dentro un host la barra dichiarata non entra ed esce con la sua pagina: l'host disegna **una barra sola dal modello
della cima della pila richiesta**, e quando la cima cambia i tasti **restano al loro posto e si trasformano** in quelli
della pagina d'arrivo. Le regole sono quelle di un cambio di stato nella stessa pagina, perché **è** un cambio di stato
del contenitore:

| fra la pagina di partenza e quella d'arrivo | che cosa si vede |
|---|---|
| lo stesso `id` (l'indietro comune, i tre puntini) | lo stesso vetro, fermo al pixel; l'icona uguale non si tocca |
| lo stesso `id` con un'icona diversa | il vetro non si muove, le icone si incrociano sfocandosi (nessun viaggio del piano); se nello stesso cambio un altro vetro cambia forma, respira con lui (F4A.5b) |
| un `id` nuovo accanto a un vetro | nasce dal fianco del vicino col collo (D3) |
| un `id` nuovo senza vicini | compare per lente, sul posto |
| indietro o Chiudi che arrivano o se ne vanno | lente sul posto anche con altri tasti a sinistra; se l'`id` resta, lo stesso vetro fermo (`MaterializeWhenAlone`, R7 21) |
| un `id` che se ne va | confluisce nel vicino, o scompare per lente |
| icone separate che diventano un gruppo, o il contrario | lo stesso vetro che si fonde o si divide; le icone che restano nitide |
| il titolo in linea | si scambia sfocandosi, con un passo di 8 dp nel verso della navigazione (nessuno fra schede) |
| il titolo grande | è contenuto: viaggia nitido con la sua pagina |

Il **sottotitolo agganciato** dichiarato con `title(…, subtitle = …)` o `subtitle(…)` arriva anche alla barra
dell'host (`FluidPageBarSpec.subtitle`): il titolo sale di 16 dp, il sottotitolo usa gli stessi inset simmetrici,
compare con la piega e segue passo, opacità e sfocatura del titolo fra pagine. `None` e `Principal` li tolgono entrambi.

**Quando.** Al push e al pop col tasto, subito, sul tempo del piano (non sul progresso della pagina). Col **gesto
indietro** solo al rilascio che conferma: durante il trascinamento la barra resta quella della pagina in cima, e
all'annullo non succede niente (D1). Un cambio di scheda è come un cambio di stato, senza verso (D4). Un cambio che
arriva a metà riparte da dove si trova.

**Perché funzioni, gli `id`.** Lo stesso tasto ha lo stesso `id` in tutte le pagine: `FluidToolbarIds.Back` lo dà la
DSL all'indietro di ogni pagina, gli altri li sceglie l'app. Per Chiudi al posto dell'indietro nello stesso vetro (un
foglio sopra una figlia), `close(onClick, id = FluidToolbarIds.Back)`.

**Le pagine con lo slot `actions` di sempre** non cambiano finché l'host non mostra una barra dichiarata: un host di sole
pagine di sempre è quello di prima. Da quando ne mostra una, l'indietro di ogni pagina è del modello (fermo al pixel) e le
azioni delle pagine di sempre restano nella loro riga, come prima. Dettagli, la variante del gesto che segue il dito (solo prove) e le trappole:
[pagine-back-e-barra.md](pagine-back-e-barra.md), «La barra disegnata dal modello». La prova in galleria è «Barre fra
pagine» (`--es gallery.page toolbarpages`, con `pages.auto`, `pages.follow`, `pages.start`).

## Limiti noti

- **Il menu «Altro»** è il menu contestuale dell'engine (senza un host dei modali non si apre: le azioni restano
  raggiungibili da TalkBack); il menu che nasce dal gruppo è di F5A.
- **I confronti sul PC** (`tools/glassbench/traces/f4a/`, F4A.5) eseguono risolutore, memoria e conducente Kotlin,
  ricetta e viaggi del contenuto; non eseguono il disegno Compose né la GPU. Tempi e sagome contro Apple non sono
  una qualifica sul dispositivo. Le tavole del ritiro F4A.4 illustrano la geometria costruita nell'esportatore. I limiti misurati e le tavole a tempo vero sono nel rapporto F4A.5.
- **Taratura del contesto** (F4A.5, poi F4A.5b): i tempi dei gruppi che cambiano larghezza sono quelli di Apple a
  tempo vero (sopra). Restano diversi: il riferimento ingrandito di 16:02 è ~1,35 volte più lento (non è a tempo vero)
  e lì la larghezza del gruppo che si allarga cresce più piano del suo bordo; da noi il bordo che si allarga supera il
  suo posto di qualche punto in più. La nascita fra pagine (F3.12) ha il collo e la rottura nell'ordine giusto, ma il
  gruppo nuovo cresce più grosso e prima del riferimento 256. Dettagli e tavole nel rapporto F4A.5b.

## Cosa è verificato dai test

- `FluidToolbarDslTest` (engine): la DSL nel modello di F4A.1 (chiavi pure, larghezze misurate, l'indietro aggiunto da
  `onBack`, principale e conferma tinte e sole), il trabocco per priorità, l'identità dei vetri fra due stati (il gruppo
  che cresce, la divisione, «sfoglia ↔ seleziona» con l'indietro fermo), gli inset del titolo d'arrivo, la ricerca.
- `FluidGlassTintSplitTest` (engine): la corrispondenza fra lastra e pellicola nel grappolo.
- `GlassPlaneLensIdentityTest`, `FluidToolbarExitTransitionTest` e `FluidToolbarAnchorsTest` (F4A.5): lente con un
  vicino, identità conservata fra figlie, ripartenza durante la lente, sottotitolo nell'host e modi `None`/`Principal`.
- `F4aToolbarExportTest`: otto tracce a 120 Hz (tre stati nei due temi e nella variante «morbida», contesto, push/pop,
  cambio interrotto, principale tinta che nasce e confluisce nei due temi), dai componenti puri della barra;
  esportazione solo con `GLASS_F4A5_DIR`. Lo smoothing è quello della forma sul dispositivo.
- `FluidGlassTintJourneyTest`: tinta nella sfocatura del singolo contenuto, lastre coincidenti senza strisce, staffetta
  con un colore solo e interruzione dal colore corrente.
- `FluidToolbarKeptContentTest` (F4A.5b): la manopola `barkeep`, la forma che i posti ricevono (cambia solo se il gruppo
  cambia tasti, solo con la variante «morbida») e ciò che ogni vetro dichiara di portare.
- `FluidToolbarContentAnchorTest` (engine, F4A.2b): gruppo 1→3→1 guidato dal conducente reale a 120 Hz, bordi iniziale
  e finale, LTR/RTL, densità 1/1,5/2/3/3,5; icona che resta col bordo del suo vetro (non scivola; da F4A.5b la sposta
  solo il trascinamento, entro 0,155 H, ed è al suo posto alla fine), nitida, senza cambiare il ritaglio;
  centro di serie identico a F3.12a, ordine degli attributi, lente, parole italiane/inglesi e tracce del banco.
- `FluidToolbarBarTest` (galleria, Robolectric): la barra vera da «sfoglia» a «seleziona» e ritorno (vetri, `id` nel
  registro del piano, semantica e bersagli da 48 dp, un viaggio senza animazioni della pagina), il tasto che diventa
  principale, il trabocco, la selezione per tasto, la tinta per pezzo in un blocco unito, e una pagina con `actions`
  che non crea contenitori e ha i tasti dove li aveva (il suo indietro dove sta quello della barra dichiarata).
  Da F4A.2b il trabocco prova indietro più tre vetri, e le parole della barra seguono `more` e `confirm` personalizzati.
- `FluidPageBarModelTest` (engine, F4A.3) e `FluidToolbarPagesTest` (galleria, Robolectric, F4A.3): la barra dell'host fra
  pagine (chi dà i tasti in ogni fotogramma di push, pop, gesto tenuto/annullato/confermato, cambio di scheda; gli stessi
  pezzi del piano fra due pagine; il viaggio che parte al push; il titolo che si scambia sul tempo suo). Elenco in
  [pagine-back-e-barra.md](pagine-back-e-barra.md).
- **Non verificato dai test**: come si vede. I viaggi si guardano sul tablet (`--es gallery.page toolbar --es
  toolbar.scene sfoglia-seleziona --es toolbar.auto true`, `adb logcat -s FluidBarre`).

## Dove guardare

- `engine-ui/…/ui/fluid/toolbar/FluidToolbar.kt`: la DSL, la traduzione nel modello, l'identità dei vetri, la resa.
- `…/ui/fluid/toolbar/FluidToolbarModel.kt`: il modello puro di F4A.1 (gruppi, priorità, trabocco, differenze).
- `…/ui/fluid/toolbar/FluidToolbarSearch.kt`: il punto unico della ricerca.
- `…/ui/fluid/FluidScreen.kt`: `toolbar`, `search`, il modo del titolo, il sottotitolo agganciato.
- La galleria: `sample/…/toolbar/` («Barre (prova)», anche `toolbar.scene ritiro` e `telefono-apple` per il riferimento f8551; «Barre fra pagine», F4A.3).
- `…/ui/page/FluidPageAnchoredBar.kt` e `FluidPageBar.kt`: la barra dell'host disegnata dal modello (F4A.3).
