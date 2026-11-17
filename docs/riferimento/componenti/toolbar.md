<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/toolbar.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

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
  Indietro, Chiudi e principale non escono mai. Il tasto «Altro» apre un menu con le azioni nascoste (il menu
  contestuale dell'engine; il menu di un gruppo vero arriva con F5A) e, per TalkBack, le offre come azioni del tasto.
- **Il piano**: la barra è un `FluidGlassContainer` alto quanto la riga; ogni gruppo è un pezzo con l'identità del suo
  vetro (`FluidToolbarGlassId`: il gruppo **alla nascita** del vetro, che il registro del piano espone), così un gruppo
  che cresce è lo stesso vetro, una divisione lascia il vetro alla parte più grande e le altre nascono dal vicino, una
  fusione lo prende dalla parte più grande e le altre confluiscono. Un tasto che il modello fa ripartire per lente
  (troppo lontano, incrociato) non trascina il suo vetro.
- **Il contenuto**: ogni tasto ha la sua chiave di contenuto (simbolo, tinta, selezione) in una
  `FluidGlassContentTransition` sua: un tasto che cambia icona la incrocia sfocandosi e il vetro non si muove; quello che
  resta resta nitido. Un tasto nuovo dentro un vetro che c'era entra sfocato; uno che esce da un vetro che resta si sfoca
  dove era, senza occupare posto, mentre il vetro si chiude. Chi arriva o se ne va con un vetro suo lo porta il piano.
  Il pezzo del gruppo passa `fluidGlassContentAnchor(Start/End)` secondo il lato (`Center` al centro): mentre la
  capsula cambia larghezza, il contenuto segue il bordo ancorato e ogni icona resta alla sua posizione d'arrivo,
  senza la deriva di metà variazione che produceva il centro. I bordi sono logici (anche RTL); un eventuale rimbalzo
  del bordo porta ancora il contenuto con sé. Fuori dalla barra l'ancora di serie è `Center`, identica al piano F3.12a.
- **Selezione e tinta per tasto**: `selected` è una pellicola dietro **quel** tasto (`GlassDefaults.selectionTint`) e la
  sua icona nell'accento, anche in un gruppo di più icone: non passa agli altri. La principale tinta accanto a un gruppo
  (a 12 dp, cioè nello stesso grappolo del piano) resta tinta: il contenitore disegna ogni tinta sulla sua striscia
  ([piano-flottante.md](piano-flottante.md), «L'unione»).
- **Il titolo**: `Large` (di serie) è il titolo grande che scorre e si aggancia, com'era; `Inline` è solo quello
  agganciato (nessun titolo nella lista); `None` nessuno. Il **sottotitolo** sta sotto il titolo grande e, agganciato,
  sotto quello della barra, più piccolo (il titolo sale di 16 dp per fargli posto). Gli **inset** del titolo agganciato
  li dà la misura d'**arrivo** dei gruppi, non il vetro in volo: mentre un gruppo cambia larghezza il titolo non balla.
- **Semantica e bersagli**: ogni tasto ha il suo nome (`label`; indietro, Chiudi, conferma e «Altro» con le parole
  dell'engine) e il ruolo di pulsante, la selezione e il numero del badge come stato; il bersaglio è almeno 48 × 48 dp,
  centrato sul tasto, senza cambiare il vetro. Un tasto spento ha il contenuto al 38%.
  Le parole di serie vengono da `LocalFluidLabels`: `back`, `close`, **`confirm`** («Conferma» / «Confirm») e
  **`more`** («Altro» / «More»), personalizzabili alla radice e indipendenti da `dateTimeConfirm` e `actions`.

## La ricerca

`FluidScreen(search = FluidSearchSpec(…))` o `search(spec)` nel blocco (che vince): un punto solo,
`FluidToolbarSearchSlot` (`ui/fluid/toolbar/FluidToolbarSearch.kt`). In F4A posa il `FluidSearchField` di oggi **così
com'è**, come primo elemento della lista sotto il titolo: sempre aperto, o richiudibile in un tondo se la pagina chiede
`behavior = CollapseToButton` e possiede l'apertura (`active`, `onActiveChange`); senza fuoco automatico. La posizione
nella barra, il passaggio da tasto a campo, i vicini e la tastiera sono di F4B, che riscrive quel file senza toccare
`FluidScreen` né la DSL.

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
| lo stesso `id` con un'icona diversa | il vetro non si muove, le icone si incrociano sfocandosi (nessun viaggio del piano) |
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
  una qualifica sul dispositivo. I limiti misurati e le tavole a tempo vero sono nel rapporto F4A.5.
- **Taratura del contesto, F4A.5:** la capsula si assesta intorno a 300 ms, mentre nel riferimento ingrandito a
  16:02 continua ad allargarsi fino a circa 900 ms; il bordo sinistro di Fluid sporge durante il rigonfiamento,
  invece di rientrare prima come Apple. La nascita fra pagine ha il collo e la rottura nell'ordine giusto, ma il
  gruppo nuovo cresce più grosso e prima del riferimento 256. Sono differenze osservate, ancora da tarare.

## Cosa è verificato dai test

- `FluidToolbarDslTest` (engine): la DSL nel modello di F4A.1 (chiavi pure, larghezze misurate, l'indietro aggiunto da
  `onBack`, principale e conferma tinte e sole), il trabocco per priorità, l'identità dei vetri fra due stati (il gruppo
  che cresce, la divisione, «sfoglia ↔ seleziona» con l'indietro fermo), gli inset del titolo d'arrivo, la ricerca.
- `FluidGlassTintSplitTest` (engine): le strisce di tinta di un grappolo.
- `GlassPlaneLensIdentityTest`, `FluidToolbarExitTransitionTest` e `FluidToolbarAnchorsTest` (F4A.5): lente con un
  vicino, identità conservata fra figlie, ripartenza durante la lente, sottotitolo nell'host e modi `None`/`Principal`.
- `F4aToolbarExportTest`: cinque tracce a 120 Hz (tre stati nei due temi, contesto, push/pop, cambio interrotto),
  dai componenti puri della barra; esportazione solo con `GLASS_F4A5_DIR`.
- `FluidToolbarContentAnchorTest` (engine, F4A.2b): gruppo 1→3→1 guidato dal conducente reale a 120 Hz, bordi iniziale
  e finale, LTR/RTL, densità 1/1,5/2/3/3,5; icona che resta entro mezzo pixel, nitida, senza cambiare il ritaglio;
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
- La galleria: `sample/…/toolbar/` (pagina «Barre (prova)», cinque scene; pagina «Barre fra pagine», F4A.3).
- `…/ui/page/FluidPageAnchoredBar.kt` e `FluidPageBar.kt`: la barra dell'host disegnata dal modello (F4A.3).
