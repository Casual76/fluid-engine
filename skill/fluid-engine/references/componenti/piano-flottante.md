# Piano flottante

Il **piano flottante** è il modo dichiarativo di fare le trasformazioni del vetro dalla 4.0: chi scrive l'app dice dove
sta un vetro, che identità ha e come vuole comparire; **il contenitore** (`FluidGlassContainer`) decide chi si fonde, chi
nasce da chi, chi compare per lente, e anima ogni cambio da solo. È `GlassEffectContainer` di SwiftUI, con
`glassEffect(_:in:)`, `glassEffectID`, `glassEffectUnion` e `glassEffectTransition`. Accanto ci sono il **contenuto che si
sfoca e si incrocia** (`FluidGlassContentTransition`, il `blurReplace` dentro un vetro) e le **forme concentriche**
(`FluidConcentricShape`), che servono alle barre. Tutte le firme: [api/vetro.md](../api/vetro.md) e
[api/tema.md](../api/tema.md).

**Modello mentale.** L'app scrive **solo lo stato**, con un `if`: nessuna animazione, nessun riquadro, nessun `id` oltre a
quelli che dichiara. Il contenuto è già al suo posto d'arrivo e non si stira mai; viaggia solo il vetro. Dentro il
contenitore **il vetro dei pezzi lo disegna il contenitore**, dietro al contenuto: a riposo una cattura per grappolo di
vetri vicini, durante un cambio un «palco» di Fluid-physics guidato dal piano. Il lato chiaro o scuro lo decide **una
sonda sola** per tutti i vetri, e il contenuto ne riceve l'inchiostro.

Con le altre famiglie: il materiale, le varianti e il vetro che si adatta sono in [vetro.md](vetro.md); il motore che
disegna il palco è in [fluid-physics.md](fluid-physics.md) e [fluid-physics-motore.md](fluid-physics-motore.md); le
forme e i raggi sono in [tema.md](tema.md); il movimento ridotto in [movimento.md](movimento.md). Le barre, i menu e i
fogli della 4.0 sono **clienti** del piano: lo usano, non lo riscrivono.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un tasto che diventa due, una barra che cambia azioni, un tasto che compare | `FluidGlassContainer { … if (stato) … }` con `Modifier.fluidGlassId(id, ns)` su ogni vetro; nessuna animazione |
| la barra in alto di una schermata, con tasti che cambiano con lo stato | la barra **dichiarata**, `FluidScreen(toolbar = { … })`: il contenitore, i gruppi e le identità li fa lei ([toolbar.md](toolbar.md)) |
| un vetro su un elemento qualsiasi (un'icona, un testo, un `Box`) | `Modifier.fluidGlass(glass, shape)`; in un contenitore è un pezzo del piano, fuori è un vetro a sé |
| lo spazio dei nomi degli `id` | `rememberFluidGlassNamespace()` |
| un blocco di tasti che è una cosa sola (azioni dello stesso gruppo, una capsula di comandi) | `Modifier.fluidGlassUnion(id, ns)` su ognuno, **prima** del vetro: una sagoma sola anche ferma (sotto, «L'unione») |
| un vetro che compare sempre per lente, anche vicino a un altro | `Modifier.fluidGlassTransition(FluidGlassTransition.Materialize)` |
| lente alla comparsa/scomparsa, stessa geometria se resta lo stesso `id` | `Modifier.fluidGlassTransition(FluidGlassTransition.MaterializeWhenAlone)` |
| un vetro che c'è o non c'è, senza transizione | `FluidGlassTransition.Identity` |
| un cambio senza viaggio, o con la mia molla | `FluidGlassContainer(animation = FluidGlassAnimation.None)` / `FluidGlassAnimation.Custom(spec)` |
| le icone di una barra che cambiano contesto | una `FluidGlassContentTransition` **per posto**, con la chiave del contenuto |
| un angolo che segue quello del contenitore | `FluidConcentricShape(minimum)` sul pezzo, `Modifier.fluidContainerShape(forma)` sul contenitore |
| gli stessi raggi per chi disegna da sé | `rememberFluidConcentricRadii(minimum)` + `Modifier.fluidConcentricRadii(state)` |
| un vetro solo che gira chiaro/scuro col suo contenuto | un `FluidGlassContainer` con **un pezzo solo** (il patto dell'inchiostro) |
| un pannello (card, dock, barra di scrittura) col ruolo e la tinta di un `glassSurface`, che gira o scurisce | `Modifier.fluidGlassPanel(backdrop, tint, shape, role, large = …)` in un contenitore (sotto, «I pannelli») |
| dei tasti di vetro **sopra** un pannello, che ne campionano il materiale | il contenuto del pannello in `FluidGlassNested { … }`: vetro annidato, non pezzi |
| un pezzo solo nel suo contenitore che riceve `weight` o una misura dal genitore | `FluidGlassContainer(modifier, propagateMinConstraints = true)` |
| il limpido sopra una foto | `Modifier.fluidGlass(FluidGlass.Clear)` (sotto, «`Clear`») |
| capire che cosa ha deciso il piano | `FluidGlassContainerDiagnostics` (viaggi, ripartenze, avvisi d'ordine, ultimo viaggio, ultimo riposo) |
| vedere tutto questo su un dispositivo | la pagina «Piano flottante» della galleria (`--es gallery.page plane`) |
| un contenitore attorno a una lista che scorre | **non così**: il contenitore va attorno alla barra, non alla lista (sotto, «Quando usarlo») |
| `fluidGlassId` scritto dopo il vetro | **non così**: non ha effetto, e in debug il log `FluidGlass` lo dice (sotto, «L'ordine è contratto») |

## Quando usarlo, e quando no

**Sì**, attorno a un gruppo di vetri che cambia insieme: una barra di tasti, la capsula in basso con la sua ricerca, un
tasto che diventa due, i tasti di un lettore, il tasto che si accende accanto a un altro. È la forma di serie di ogni
trasformazione fra vetri vicini dalla 4.0.

**Anche** attorno a un vetro solo, quando il suo contenuto deve girare chiaro/scuro con lui (il patto dell'inchiostro,
sotto): un contenitore con un pezzo.

**No** attorno a una lista o a un contenuto che scorre: per il piano ogni fotogramma di scorrimento è un cambio di
posizione dei pezzi, cioè un viaggio. (Il layout giusto è l'altro: il contenitore **fermo attorno alla barra** e la
lista che scorre **sotto** di lui, come nella scena `lista` della galleria.) Un contenitore che si sposta **tutto insieme** (la barra che scorre via) invece non
cambia niente: le coordinate dei pezzi sono le sue. **Un contenitore che cambia misura** (giro di correzione di F3: una
rotazione senza ricreazione, lo schermo diviso, la tastiera, una riga che si allarga attorno a lui, una densità nuova)
non viaggia: se il genitore gli dà vincoli nuovi e i pezzi sono gli stessi, i vetri seguono il riquadro **senza viaggio e
senza luce** nel fotogramma dopo, col loro contenuto; un vetro in viaggio arriva lì. Un pezzo che entra o esce nello stesso
fotogramma viaggia come sempre. **Il viaggio esce dal contenitore**: il superamento di chi nasce e la spinta di chi
accoglie arrivano fino al bordo della **finestra**, non del contenitore, quindi un contenitore che fascia la sua riga (il
caso tipo, e le barre) ha la coreografia intera senza un riquadro inventato; un genitore che ritaglia (`clip`, una lista
che ritaglia il suo riquadro) la taglia col resto. Un vetro che sta **dentro** un pannello dello stesso contenitore
non è un pezzo: va in un `FluidGlassNested` (sotto, «I pannelli»), altrimenti è un fratello del pannello e ci si fonde.
**No** per le superfici che hanno già il loro motore (menu che si trasforma, foglio, pop-up): sono clienti del piano nelle
fasi che li portano.

## Le firme

| SwiftUI | Fluid |
|---|---|
| `glassEffect(_:in:)` | `Modifier.fluidGlass(glass = FluidGlass.Regular, shape = FluidCapsuleShape, backdrop = currentGlassBackdrop())` |
| `GlassEffectContainer(spacing:)` | `FluidGlassContainer(modifier, spacing = FluidGlassContainerDefaults.Spacing, animation = FluidGlassAnimation.Default, propagateMinConstraints = false) { }` |
| `glassEffect` su un pannello che porta contenuto | `Modifier.fluidGlassPanel(backdrop, tint, shape, role = GlassRole.Floating, optics = GlassDefaults.optics(role), large = null, exports = null, resampleIntervalMillis = 0L)` |
| (i controlli dentro un pannello di vetro) | `FluidGlassNested { … }` |
| `@Namespace` | `rememberFluidGlassNamespace()` → `FluidGlassNamespace` |
| `glassEffectID(_:in:)` | `Modifier.fluidGlassId(id, namespace)` |
| `glassEffectUnion(id:namespace:)` | `Modifier.fluidGlassUnion(id, namespace)` |
| `glassEffectTransition(_:)` | `Modifier.fluidGlassTransition(FluidGlassTransition.MatchedGeometry / Materialize / Identity / MaterializeWhenAlone)` |
| `withAnimation` | niente: il contenitore anima da solo; `FluidGlassAnimation.Default / None / Custom(spec)` |
| `blurReplace` dentro un vetro | `FluidGlassContentTransition(targetState, key = …) { stato -> … }` |
| `ConcentricRectangle`, `containerShape(_:)` | `FluidConcentricShape(minimum)`, `Modifier.fluidContainerShape(shape)` |

Il caso tipo (è la scena `uno-due` della galleria):

```kotlin
val ns = rememberFluidGlassNamespace()
FluidGlassContainer {
  Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
    FluidGlassIconButton(onClick = edit, modifier = Modifier.fluidGlassId("edit", ns)) { FluidIcon(FluidIcons.Edit, null) }
    if (editing) {
      Box(Modifier.fluidGlassId("done", ns).fluidGlass().height(44.dp).padding(horizontal = 20.dp)) { Text("Fatto") }
    }
  }
}
```

Un `if` e nessuna animazione: «done» nasce da «edit» se gli sta entro lo `spacing`, altrimenti compare per lente. I tasti
di vetro dell'engine (`FluidGlassIconButton`, `FluidGlassButton`, ogni `glassControlSurface`) dentro un contenitore sono
pezzi come gli altri; `FluidGlassIconButton` inoltra al suo vetro gli attributi scritti sul suo `modifier`.

Il contenuto del contenitore è posato come in un `Box` (in alto all'inizio, il contenitore grande quanto il più grande
dei figli): di solito è una `Row` o una `Column`. Come in un `Box`, di serie il contenuto **non** riceve i vincoli minimi
del contenitore; con `propagateMinConstraints = true` li riceve (sotto, «I pannelli»).

## L'ordine è contratto

**`fluidGlassId`, `fluidGlassUnion` e `fluidGlassTransition` valgono per il `fluidGlass` (o il tasto di vetro dell'engine)
alla loro destra nella stessa catena, e per nessun altro**: né i figli, né un vetro più a sinistra. Scritti dopo non
hanno effetto, e in una build di debug il log `FluidGlass` dice «… non ha effetto: va scritto prima del fluidGlass …».

```kotlin
Modifier.fluidGlassId("play", ns).fluidGlass()   // giusto: il vetro è "play"
Modifier.fluidGlass().fluidGlassId("play", ns)   // l'id non conta, avviso in debug
```

Il perché: è la regola dei modificatori di Compose (ciò che sta a sinistra avvolge ciò che sta a destra), e un vetro
con due nomi possibili, a seconda di dove si scrive l'attributo, è un vetro che si accoppia male in silenzio. Lo provano
`FluidGlassContainerOrderTest` (engine) e `FluidGlassContainerTest` (galleria); il contatore è
`FluidGlassContainerDiagnostics.totalOrderWarnings`.

## Gli `id` e lo `spacing`

**Date gli `id`.** Lo stesso `id` prima e dopo è **lo stesso vetro**, che si sposta e cambia forma. Senza `id` i vetri si
accoppiano per lato e per posizione (come la barra di prima), e quando ne sparisce uno in mezzo possono sbagliare: un
vetro prende il posto del vicino e la barra «scivola». Due vetri con lo stesso `id` nello stesso contenitore: vale il
primo, l'altro è anonimo. Gli `id` valgono nel loro spazio dei nomi (`rememberFluidGlassNamespace`): lo stesso `id` in
due spazi è due vetri.

**Lo `spacing`** (`FluidGlassContainerDefaults.Spacing`, **16 dp**) dice tre cose insieme:

- fin dove due vetri sono **un grappolo**: una cattura sola a riposo, un campo fuso in viaggio (a riposo i pezzi
  **distinti** di un grappolo restano separati del tutto, scelta di Alessio del 2026-11-11: il collo esiste solo in
  viaggio);
- fin dove un vetro nuovo **nasce dal vicino** invece di comparire per lente;
- il raggio di fusione del collo in viaggio: fino a uno `spacing` di varco il collo è intero, a una volta e mezza si
  rompe, al doppio non arriva.

**16 dp è la scelta di Alessio del 2026-11-16** (tavola «distanza di fusione» di F3.3: stretta 8 / media 16 / larga 24,
fusi già a riposo): copre i due vetri di un gruppo (8 dp, `FluidMetrics.GlassSpacing`) e due gruppi vicini (12 dp,
`FluidMetrics.GroupSpacing`) con un margine, perché l'arrotondamento al pixel non decida chi si fonde. Apple non la
documenta (i suoi esempi usano 20 e 40 pt). La pagina «Piano flottante» riprende le tre distanze della tavola con
`plane.fusion stretta|media|larga` (la manopola `FluidGlassContainerDiagnostics.fusionPreview`, solo per le prove).

## L'unione: un blocco di tasti è una cosa sola

**Quando più tasti formano un blocco, dichiaratelo con `fluidGlassUnion`.** A riposo due vetri distinti, anche a 8 dp,
restano **separati del tutto** (tre vetri vicini sono tre vetri); quelli della stessa unione sono **una sagoma sola**,
ferma o in viaggio: una goccia col suo collo pieno fra un tasto e l'altro, come la capsula di comandi di Apple. È la
fusione a riposo che Alessio ha voluto riservare a chi la dichiara (2026-11-16: «con incitamento agli utilizzatori
dell'engine a sfruttare l'unione in blocchi di tasti quando appropriato»).

```kotlin
val ns = rememberFluidGlassNamespace()
FluidGlassContainer {
  // Tre azioni dello stesso gruppo: una capsula di comandi.
  Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
    for (action in listOf(Bold, Italic, Underline)) {
      FluidGlassIconButton(
        onClick = { apply(action) },
        modifier = Modifier.fluidGlassId(action, ns).fluidGlassUnion("formato", ns),
      ) { FluidIcon(action.icon, action.label) }
    }
  }
}
```

**Sì**: azioni dello stesso gruppo che si leggono insieme (formato del testo, i comandi di un lettore, «indietro / avanti»
di una pagina, una capsula con più icone), un controllo segmentato fatto di vetri, i tasti di una barra che stanno sempre
insieme. **No**: tasti di **significato diverso** accostati per caso (un «Cerca» accanto a un «Condividi» non sono una
cosa sola: fusi, sembrano un comando unico); **gruppi lontani** (oltre circa un raggio e mezzo d'angolo di varco, 30 dp
per tasti da 44, il collo pieno non sta più nel filo dei tasti: l'unione resta una cattura sola con sagome separate, e non
serve); un vetro che **compare o scompare per lente** (mentre compare è un campo a sé, e si unisce agli altri solo a
riposo); due **gruppi diversi** della stessa barra (12 dp, `FluidMetrics.GroupSpacing`): ognuno la sua unione, o
nessuna. Un'unione resta un grappolo solo fino a otto pezzi; `fluidGlassUnion` va **prima** del vetro, come `fluidGlassId`.

**Ogni pezzo ha la sua tinta, anche in un grappolo** (F4A.2, la correzione della review di F3, D R4). Fino a F4A.1 un
grappolo di più pezzi si disegnava con la tinta del primo: la selezione di un `FluidGlassIconButton(selected = true)` in un
blocco unito non si vedeva (o tingeva tutto il blocco), e un `FluidGlass.Regular.tint(…)` accanto a vetri non tinti
perdeva la sua tinta o la dava agli altri. Ora, quando i pezzi di un grappolo (a riposo) o di un campo del palco (in
viaggio) **non hanno tutti la stessa pellicola**, il contenitore disegna **una superficie per tinta**, ognuna ritagliata
sulla **striscia dei suoi pezzi** lungo la riga: la sagoma resta una (stesse lastre, stesso collo, stessa unione), cambia
solo la pellicola. Fra due pezzi distinti il taglio cade a metà del varco, dove non c'è vetro: ognuno ha la sua pellicola,
intera. In un blocco unito cade nel collo: la selezione è la striscia del suo tasto, come la selezione dentro una capsula
di Apple. In viaggio le strisce seguono le lastre a ogni fotogramma (chi nasce dentro il donatore porta la sua tinta da
metà della parte in cui si sovrappongono). Il confronto è sulla sola **tinta**: pezzi con la stessa pellicola e ruolo o
ottica diversi (un `fluidGlass` accanto a un tasto) restano un'unica superficie col ruolo e l'ottica del primo, com'erano.
Il registro dice con che tinta si disegna ogni vetro (`FluidGlassPieceRecord.drawnTint`). Costa una superficie (una
cattura) in più per ogni tinta in più, solo nei grappoli che ne hanno più d'una. È ciò che serve alla barra dichiarata
([toolbar.md](toolbar.md)): l'azione principale tinta a 12 dp da un gruppo neutro è nello stesso grappolo.

## Quando lo stato cambia

Il piano confronta lo stato di prima e quello di adesso, una volta per cambio:

| prima → adesso | che cosa fa | quanto dura |
|---|---|---|
| stesso `id` | **lo stesso vetro** si sposta e cambia forma; i vicini che si avvicinano si fondono passando per un collo, quelli che si allontanano si strozzano e si dividono | molla di serie, ferma a vista a `FluidMotion.PlaneContextMillis` (300 ms) |
| stesso `id`, la larghezza cambia di almeno mezza altezza **con un bordo fermo** (un gruppo di una barra che perde o guadagna un tasto, F4A.5b) | **cambio di larghezza**: il bordo che si muove parte dopo 24 ms, più lento; **il vetro intero è trascinato** nel suo verso (0,155 H, colmo a ~300 ms) e torna con un piccolo passaggio dall'altra parte; si gonfia coi tempi suoi (+13,5% a ~200 ms). Chi viaggia con lui prende il suo orologio | `FluidMotion.PlaneResizeMillis` (535 ms a vista; il trascinamento fino a ~780) |
| stesso `id`, stessa misura, **contenuto dichiarato diverso** (`fluidGlassContentKey`, solo la barra) nello stesso viaggio di un altro vetro | **respira**: +16,5% a ~200 ms, un poco sotto a ~490, fermo a ~760. Un cambio *solo* di contenuto non è un viaggio: il vetro resta fermo | — |
| `id` nuovo entro `spacing` da un vetro di prima | **nasce da lui** (il vicino si misura dal posto di **prima**, quindi due nuovi dalla stessa parte nascono entrambi da un donatore solo se stanno entrambi entro lo `spacing` dal suo vecchio riquadro: un donatore largo che si sposta, scena `due-nati`): gli sta dentro, piccolo, e dopo un'attesa ne è **rilasciato** con una molla che supera il suo posto e torna, crescendo mentre esce (vedi «La nascita e la confluenza», sotto) | `FluidMotion.PlaneReleaseMillis` (540 ms a vista; la coda fino a ~850–900) |
| `id` nuovo più lontano (o `Materialize`) | **compare per lente** al suo posto: la rifrazione sale, il contorno si stringe da `FluidMotion.PlaneAppearScale` (×1,09); il corpo non sfuma mai | `FluidMotion.PlaneAppearMillis` (430 ms) |
| un vetro che se ne va vicino a un altro | **vi confluisce**: gli entra dentro subito, rimpicciolendosi, e lo spinge; quello torna al suo posto con un piccolo passaggio dall'altra parte | 540 ms a vista; la coda fino a ~900 |
| un vetro che se ne va lontano | **scompare per lente** | `FluidMotion.PlaneDisappearMillis` (470 ms) |
| `fluidGlassUnion` uguale | **un grappolo solo e una sagoma sola**, anche oltre lo `spacing` (mai oltre otto pezzi), ferma o in viaggio | — |
| gli stessi vetri, l'unione che **nasce** o **si scioglie** («Unisci», «Separa») | **un viaggio del collo**, anche se nessun pezzo si muove: il ponte nasce a circa un quarto ed è pieno a metà; al contrario si strozza e si rompe una volta, come ogni divisione | 300 ms |
| un vetro che non cambia, lontano da chi viaggia | **resta al suo vetro di riposo**: non passa dal palco, e due fermi vicini non si uniscono perché un terzo compare | — |
| `Identity` | c'è o non c'è | nessuna |

**Due pezzi che si scambiano di posto non viaggiano** (review di F3, D R11). Se due `id` si scambiano i posti (una barra
riordinabile, un trascinamento, un cambio di direzione di lettura) l'accoppiamento li scioglie come incrociati (è la regola
«non incrociare i pezzi»): ognuno esce per lente dal posto vecchio ed entra per lente nel nuovo, senza scorrere. Se le barre di F4A
dovranno riordinare, lì si decide se resta così o diventa un viaggio incrociato.

**`Materialize` e `Identity` valgono anche per un pezzo che resta** (D R12). Un pezzo dichiarato `Materialize` non viene
accoppiato a quello di prima con lo stesso `id` (salvo una ripartenza a metà viaggio): se un vicino che nasce lo sposta,
non scorre, **esce per lente dal posto vecchio ed entra per lente nel nuovo**. Un pezzo `Identity` accoppiato per `id`
salta al posto nuovo senza viaggio. La transizione governa dunque anche lo spostamento, non solo l'inserzione e la
rimozione come in `glassEffectTransition` di SwiftUI: `Materialize` va dato a chi compare davvero, non a un tasto che
può essere spostato da un vicino che nasce.

**`MaterializeWhenAlone` (F4A.5)** forza la lente soltanto per chi non ha predecessore/successore: non nasce dal
vicino e non vi confluisce, ma lo stesso `id`, nello stesso contenitore e nella stessa variante, si accoppia come
`MatchedGeometry` (anche se cambia il nodo). È la scelta della barra per indietro e Chiudi: fra due figlie il vetro
resta, fra radice e figlia compare per lente anche quando la radice ha un altro tasto a sinistra. Senza `id` non
usa le euristiche di ruolo/simbolo/ordine; l'incrocio esplicito degli `id` conserva il ripiego locale per lente.

**I tempi sono quelli a vista** (F3.2c): la sagoma entra per sempre nello 0,5% della corsa al tempo della tabella (la
capsula della traccia `contesto-capsule-curva` a ~294 ms, contro i 270–330 ms di Apple), e il viaggio finisce poco dopo,
quando ogni lato è a mezzo pixel dal suo arrivo. Fino a F3.2 la molla si stirava sulla coda numerica e la sagoma si
fermava a vista a 133 ms. **Il collo** si strozza una volta sola, «spesso» (esponente 1/2, scelta di Alessio del
2026-11-16), e si rompe dopo che i pezzi si sono staccati, a circa tre quarti del moto (~228 ms su 300; in una nascita,
ferma a vista a 540 ms, a ~415 ms, dopo il colmo: Apple lo rompe a ~433, la scelta «com'è oggi»). Il suo orologio
dura il doppio del riposo a vista, così il collo lungo scelto da Alessio cade nella stessa fase della molla in cui è
stato tarato. Durante il viaggio il vetro **si illumina e si fa lattiginoso** (il carattere del viaggio, sotto) e torna
al vetro di riposo prima di posarsi.

**La nascita e la confluenza** (F3.11, F3.12a e F3.12c; le misure sono in `artifacts/liquid-glass/sessioni/f3-11/misure.md`,
`…/f3-12a/misure.md` e `…/f3-12c/misure.md`, sulle clip di Apple a tempo vero, con l'orologio corretto dall'analisi
fotogramma per fotogramma: un fotogramma dopo quello che leggevano F3.11 e F3.12a). Un vetro che nasce da un vicino non
va dritto al suo posto:

1. **l'attesa**: per i primi 0,23 (124 ms) nessun vetro viaggia; il vetro si accende, e chi sta per nascere si sporge
   appena dal vicino (il «donatore»): in Apple il bordo di testa ha fatto solo il 9,5% della strada a 133 ms;
2. **il donatore va per primo** verso il suo posto, se ne ha uno nuovo, e chi nasce gli resta dentro **più piccolo**
   (0,8 della sua misura dentro il donatore, `FluidMotion.PlaneReleaseBud`), a filo del bordo dalla parte del suo posto;
3. **il rilascio**: alla fine dell'attesa se il donatore resta dov'è, un poco dopo se si sposta (0,31 del viaggio se va
   lontano almeno quanto è alto), e +0,09 per ogni fratello più vicino dalla stessa parte (il più lontano parte per
   primo). Chi è rilasciato ha una molla che **supera il suo posto del 9,5% della corsa e torna**, e che sta fra il
   rilascio e il ritorno: chi parte dopo è più svelto, e i colmi cadono insieme (a ~0,7–0,8 del viaggio). Con la stessa
   molla **cresce mentre esce**: è la goccia con la vita di Apple, un lobo più piccolo del donatore unito da un collo,
   non una pillola. Coi tempi di Apple: arrivo a ~296 ms, colmo a ~367, ritorno a 540;
4. **si gonfia passando per il suo posto**: +19% del lato corto al colmo (~385 ms), poi un poco sotto la sua misura
   (−3% a ~680) e fermo: al colmo il suo bordo di testa è oltre il posto del 13–14% della corsa, come in Apple. È il suo
   rigonfiamento, non quello comune (`FluidMotion.PlaneReleaseSwell`);
5. **il donatore che resta dov'è rincula** verso chi esce (0,10 del suo lato corto, ~4,4 dp su un tasto da 44, a ~260
   ms), **rimbalza** dall'altra parte (0,07 a ~480), torna con un ultimo piccolo passaggio (0,015 a ~710) e si ferma a
   ~860; intanto si gonfia **poco** (+4%) invece del rigonfiamento comune. Uno che se ne va non rincula;
6. a **540 ms** è il riposo a vista: su quell'orologio contano il contenuto, la luce, l'alone e il collo (che si rompe a
   ~415 ms, dopo il colmo). Poi la **coda di Apple**: chi è nato ripassa piano dall'altra parte del suo posto (al secondo passaggio
   misura −1,7% della corsa a ~633 ms sul banco e −2% sul tablet, S4: lo 0,9% era la sola molla, il resto è il
   rigonfiamento che torna sotto) e il palco lo tiene finché lo scarto si vede, fino al mezzo pixel (~850–900 ms).

Un vetro che **confluisce** fa il verso opposto, senza attesa: entra nel vicino dovunque quello stia andando,
**rimpicciolendosi** (0,66 della sua misura dentro di lui, `FluidMotion.PlaneMergeCore`: fino a quando è entrato restano
due lobi col collo; quando è dentro, fra 0,37 e 0,6 del riposo a vista, **diventa il vicino**, lati, raggi e
rigonfiamento, perché due sagome quasi uguali fanno nell'unione due «orecchie» sul fianco), e la sua spinta (l'8,4% della sua corsa, al più 0,2 del lato corto di chi lo accoglie) **sposta il
vicino tutto intero** nel suo verso, col suo contenuto, al colmo a ~300 ms; poi quello torna, passa un poco dall'altra
parte (0,38 della spinta a ~590) e si ferma a ~900. Intanto chi accoglie si gonfia presto (+15% a ~170 ms) e poi va un
poco sotto la sua misura (−4,7% a ~370), invece del rigonfiamento comune. Un vetro che se ne va confluendo proprio nel
vetro che nasce da lui è lo stesso vetro che cambia identità (la staffetta): vola come un vetro solo, ed è lui il
donatore. Fra più vicini vince quello della stessa misura («Fatto» continua «Seleziona», non il tondo di mezzo). **Il contenuto sta dentro il
vetro e rimbalza con lui**: vedi sotto. Con `FluidGlassAnimation.Custom` niente di questo: la spec porta la sua curva. I
numeri sono i token `PlaneRelease*`, `PlaneMerge*` e `PlaneDonor*` di `FluidMotion`.

**La partenza del viaggio** (F3.8a; rivista nel giro di correzione di F3). Il viaggio **non** parte nel fotogramma del
cambio: sul tablet (S4) senza un tocco parte 70–120 ms dopo il cambio di stato (col tocco 19–43 ms), e a schermo fermo
un pannello a frequenza variabile gira a 30 Hz e sale a 120 con un buco di 50–100 ms senza fotogrammi; il primo
fotogramma costa 22–43 ms sul thread UI. I primi tre passi dell'orologio hanno un tetto di 17 ms
(`FluidMotion.PlaneFirstStepMillis`): quel tempo è perso, non moto saltato (prima il secondo fotogramma cadeva già a un
terzo del viaggio, col collo pieno), ma il buco c'è. Nel giro di correzione di F3 la parte grossa si è vista: fra il cambio e il primo fotogramma del palco ci sono **due passaggi** (il fotogramma in cui l'app compone e posa il pezzo, e il primo in cui il contenitore misura), che a schermo fermo, a 30 Hz, fanno ~62 ms in mediana sul tablet; il motore ne aggiunge esattamente uno, il minimo se il fotogramma del cambio deve mostrare ancora il riposo di prima. Il primo fotogramma del palco non compone più il riposo d'arrivo (arriva al secondo), il ponte fra due vetri staccati nasce sottile e cresce invece di comparire pieno, e il palco vota la frequenza alta (`Modifier.preferredFrameRate`, da Android 15) dal suo primo fotogramma. Restano da misurare sul tablet il voto della frequenza già al cambio di stato e il costo del costruttore del viaggio (8–10 ms nell'APK di debug).
Negli ultimi tre decimi del viaggio lo scarto dall'arrivo si spegne con una finestra liscia: nessun lato arriva per
conto suo con un ritocco di un pixel dopo che la sagoma è ferma a vista (la coda di Apple, sotto il mezzo pixel, è
un'altra cosa e c'è: «Costi noti»).

**Il rigonfiamento** (F3.8a, col numero e la curva del carattere del viaggio di F3.9, `GlassLiquidJourney` nel profilo):
i vetri di un cambio di contesto, di una nascita dal vicino, di una confluenza, di un'unione e di una separazione **si
gonfiano** nel mezzo del viaggio, del 15% del lato corto al 45% della fase (3,6 dp per lato su un tasto da 48 dp),
rientrato al 95%; mai più di `FluidMotion.PlaneSwellMaxDp` (6 dp per lato), così un pannello grande non si deforma. Un
viaggio interrotto riparte dal rigonfiamento che ha; chi compare o scompare per lente non si gonfia. **Nella nascita e
nella confluenza ognuno ha il suo** (F3.12c, `GlassPlanePieceFrame.swell` e `swellShare`): chi nasce, il donatore, chi
confluisce e chi accoglie non prendono il rigonfiamento comune, che ha il colmo a metà viaggio per tutti, ma quello
misurato su Apple per ciascuno (sopra), nelle stesse unità del carattere e dentro lo stesso tetto; può anche andare un
poco sotto la misura del vetro. La manopola è `travelswell` (o `journey=off`, che spegne tutto il carattere) di
`gallery.tune`: a zero spegne anche quelli propri.

**La luce del viaggio** (F3.9, agganciata al palco da F3.10; scelta di Alessio del 16 novembre: «a metà, col
rigonfiamento di Apple, più la luce del dito»). Nello stesso viaggio il vetro **si illumina e si fa lattiginoso**: il
velo va a metà verso il bianco, l'ombra sotto la sagoma diventa un alone di luce **appena visibile senza tocco**
(alfa 0,04, scelta di Alessio del 17 novembre 2026; prima 0,50), pieno col dito e dimezzato al buio; la sfocatura raddoppia e la
limpidezza del vetro in viaggio cede allo smerigliato. Sale in ~70 ms, resta piena fino a metà viaggio e si spegne con
una coda lunga entro il 90%: alla partenza e all'arrivo il vetro è quello di riposo al bit. **L'alone ha i suoi tempi**
(F3.12c, `GlassLiquidJourney.glowLevel`, come nel render di Apple): sale con la luce, è pieno fino a 0,31 del viaggio e si
spegne a 0,69, quando il corpo è ancora acceso. Col bagliore del dito anche il **filo** scuro del bordo cala (il blocco
bianco del render ha tutto il bordo morbido). Sul tema scuro prende metà
della luce. È il render di Apple «Select → Done» preso a metà (`GlassLiquidJourneys.Shipped`): la luce piena è un blocco
bianco che sul testo e al buio copre tutto. Solo il palco del contenitore la porta: menu e Fluid-physics fuori dal piano
restano «un po' limpidi» (la scelta B di F1B), e chi compare o scompare per lente ha la sua comparsa.

**La luce del dito** (F3.10). Quando il cambio parte da **un tocco su un vetro del contenitore** (un tasto che diventa
due quando lo premi, «Seleziona» che diventa tre), il bagliore della pressione di quel vetro **entra nel viaggio**: il
campo che nasce da lui comincia dal bagliore che il vetro fermo mostrava (il click arriva al rilascio, il dito si sta
alzando), lo allarga fino a riempire tutta la sagoma entro metà viaggio, e lo spegne con la luce del carattere. In
Apple il bagliore del tocco riempie il vetro in ~230 ms: è probabilmente il blocco bianco del render. Lo porta ogni
pezzo interattivo del contenitore: `fluidGlass(FluidGlass.Regular.interactive())`, `glassControlSurface`,
`FluidGlassButton`, `FluidGlassIconButton`. Un cambio scritto dall'app senza un tocco (un timer, un evento, un vetro
fuori dal contenitore) parte dal livello del carattere. Le manopole: `journey=off|gentle|half|apple` (le intensità della
tavola di F3.9), `finger=on|off` e `travelfinger` (quanto bagliore si porta), più `travellight`, `travelglow`,
`travelfrost`, `travelswell`.

**Un cambio a metà viaggio riparte da dove i vetri si trovano**, con la velocità che hanno, e il collo riparte da quello
che c'era (invariante 16): niente torna indietro di colpo, niente compare due volte, e il viaggio non va in coda. Con
`FluidGlassAnimation.Custom(spec)` la molla dell'app conserva la velocità, una curva a durata no (riparte da ferma).

**La comparsa per lente** (R4 B3, bilancio 430 ms): prima il contorno, già più largo (i primi ~70 ms); poi la
lente, con la sfocatura che cresce con lei fino a ~230 ms (mai un'immagine doppia); il **simbolo** (il contenuto del
pezzo, qualunque sia) arriva da un quarto della comparsa, dilatato 2× e traslucido, **ritagliato sul vetro**, e si
condensa: a 167–234 ms scala ~2× e alfa ~0,22–0,40, quasi contratto a ~367, ultimo assestamento fino a 430 ms
(taratura PC del 17 novembre 2026; da verificare sul tablet). Chi compare o scompare è sempre un campo a sé, quindi la comparsa è la stessa
accanto a un vetro che si sposta. **La scomparsa** (470 ms) fa la strada al contrario: il simbolo via nei primi
~130 ms (resta dov'era e si sfoca, anche se l'app l'ha già tolto: vedi sotto), la rifrazione a zero entro ~400 ms, poi
il contorno. Una comparsa interrotta riparte dal punto della curva in cui si trova. La durata a vista resta da
qualificare sul tablet: nella correzione PC del 17 novembre la figura è sotto soglia a ~417/~458 ms, non basta il
bilancio 430/470 a dichiararla uguale ad Apple; l'aggancio delle code del piano resta al nucleo.

**Movimento ridotto**, `FluidGlassAnimation.None` o scala delle animazioni di sistema a zero: nessun viaggio, i vetri vanno
al posto nuovo nel fotogramma dopo il cambio; **anche se arrivano a viaggio avviato** (giro di correzione di F3: fino a
F3.12c la scala a zero in corsa sollevava un'eccezione e `None` lasciava finire il viaggio), il viaggio finisce in quel
fotogramma. La scala di sistema allunga i viaggi (è il «lento» delle riprese).
**Il contenitore la legge da solo** (la scala di sistema, `MotionDurationScale`), in ogni app. **Il contenuto no**:
`FluidGlassContentTransition` legge `LocalFluidMotionPolicy`, che vale scala 1 finché la radice dell'app non installa
`FluidMotionPolicyProvider` (`FluidTheme` non lo fa: la trappola nota dell'engine, in [movimento.md](movimento.md),
«Movimento ridotto»). Senza il fornitore, col movimento ridotto il vetro va al posto nuovo e le icone si sfocano comunque
per 300 ms. **Da installare nella radice di ogni app che adotta il piano.**

## Il passaggio di consegne: riposo e palco

Per ogni vetro, in ogni fotogramma, c'è **una superficie sola** che lo disegna:

| fotogramma | chi disegna |
|---|---|
| il fotogramma del cambio | il **riposo di prima**, col **contenuto di prima dov'era** (giro di correzione di F3: il pezzo è già al posto nuovo nel layout, e il contenitore mostra la registrazione di prima finché il vetro non lo raggiunge, un fotogramma): è il primo fotogramma del viaggio, che parte esattamente da lì, **compresa la pressione** del tasto toccato (la lastra premuta, che il palco rilascia con la molla della pressione) |
| dal fotogramma dopo, per tutto il viaggio | il **palco**: una superficie di Fluid-physics per campo del viaggio, grande quanto il palco più il margine dell'ombra, guidata dal piano «a fotogramma» (`driveByFrame`: la forma di ogni fotogramma col suo collo, le unioni, la comparsa e il vetro in viaggio). I grappoli d'arrivo si montano spenti (niente disegno, niente cattura) dal secondo fotogramma del palco, per alleggerire il primo; il palco chiede al sistema la frequenza alta (`preferredFrameRate`, dall'API 35) |
| l'ultimo fotogramma | i **grappoli d'arrivo** riprendono il vetro nella stessa composizione in cui il palco si smonta, dalla geometria d'arrivo (entro mezzo pixel) |

Chi porta il vetro si decide in composizione, una volta per tutte le superfici (la regola del passaggio di consegne in
[../regole.md](../regole.md)). Riposo e palco usano la stessa ricetta e lo stesso corpo: col materiale nuovo il passaggio
non si vede, purché sia uno. Il criterio sul dispositivo è F3-b: nei primi e negli ultimi cinque fotogrammi del viaggio
nessun salto.

**Chi non cambia non passa dal palco** (F3.8a): un grappolo che c'era uguale prima e dopo, fermo, e che non tocca
nessun vetro in viaggio tiene la sua superficie di riposo per tutto il viaggio degli altri. Niente passaggio di
consegne, niente ricetta del viaggio, e due tasti fermi a 8 dp restano due tasti mentre un terzo compare lontano.

## Il contenuto dei pezzi durante un viaggio

**Il contenuto di un pezzo si vede solo dentro il suo vetro** (F3.8a), anche in un pannello che cambia misura (il dock dell'«Assistente», `fluidGlassPanel` con `FluidGlassAnimation.None`): dal giro di correzione di F3 il contenuto di un pezzo che si è spostato aspetta il suo vetro un fotogramma, così contenuto e geometria nuova cambiano nello stesso fotogramma (prima la riga «Chiedi qualcosa…» stava per ~41 ms fuori dal suo vetro). L'app non fa niente: mette e toglie i pezzi come
sempre, e il contenitore porta il contenuto di ognuno lungo la **fase** del volo del suo vetro (0 alla partenza, 1 al
riposo a vista), ritagliato a ogni fotogramma sulla sagoma in volo. È la sequenza del render di Apple «Select → Done»
(R4 B2 (d), R7 A): il vecchio è già sfocato all'8% ed è sparito nel vetro al 16% (Apple: 14–17%); il nuovo arriva
sfocato dentro il vetro dopo metà viaggio e si mette a fuoco mentre il vetro arriva al colmo, con una rampa che
scende presto e finisce piano (F3.12c: sul tempo delle clip di Apple a tempo vero). La curva è continua;
il minimo σ 0,5 px di `BlurEffect` lascia ancora un piccolo gradino finale, da rifinire in F6.4. **Ogni contenuto
sta al centro del suo vetro e si muove con lui** (F3.12a): quello di un tasto che nasce supera il suo posto e torna col vetro, quello del donatore lo
segue nel rinculo. In Apple i simboli fanno lo stesso e non cambiano misura: il contenuto trasla, non si stira.

| il vetro del pezzo | il suo contenuto |
|---|---|
| resta (stesso `id`) | **nitido, viaggia col suo vetro**: spostato di quanto il centro della lastra è lontano dal posto d'arrivo, mai stirato; anche nel rinculo e nella spinta |
| nasce da un vicino | l'opacità comincia a fase 0,42 del contenuto, circa 30 ms prima di F3.12c (~200 ms col donatore fermo), e sale più piano fino a fase 1,30 (~630 ms); resta sfocato e traslucido **dentro il suo vetro e ne fa il rimbalzo**. Scala e messa a fuoco conservano il loro orologio: leggibile a ~267–300 ms e nitido a vista a ~370; resta il minimo σ 0,5 px di `BlurEffect`, da rifinire in F6.4 |
| compare per lente | da un quarto della comparsa, ancora ~2× e traslucido a 167–234 ms, quasi contratto a ~367; ultimo assestamento fino a 430 ms, da verificare in mano |
| confluisce in un vicino | **entra nel vicino col suo vetro** anche se l'app l'ha già tolto, e intanto si sfoca e svanisce: si legge ancora, sfocato, a ~130 ms ed è sparito a ~230 (F3.12c, come Apple a tempo vero; era ~80 ms), ritagliato sulla lastra che si ritira |
| confluisce nel vetro che nasce da lui (la staffetta, «Select → Done») | come sopra, ma via entro il 16% del volo (~85 ms): è lo stesso vetro che cambia identità |
| scompare per lente | resta dov'era, si sfoca e svanisce nei primi ~130 ms della scomparsa (R4 B3) |
| `Identity` | c'è o non c'è, col vetro |

Un cambio a metà viaggio riparte dall'aspetto che il contenuto ha in quel momento (un «Fatto» che stava nascendo e
viene tolto esce da lì, e se torna rientra da lì): nessuna scritta salta. Il contenuto è registrato in un layer del
contenitore, che resta disegnabile quando il nodo del pezzo esce dalla composizione; a riposo è un layer senza effetti.
Sotto Android 12 non c'è la sfocatura: resta la dissolvenza, sempre dentro il vetro. Vale per ogni contenuto del
pezzo, anche una `FluidGlassContentTransition`, che continua a occuparsi del contenuto che cambia **dentro** un vetro
che resta. La diagnostica dice la parte di ognuno: `FluidGlassContainerDiagnostics.lastContents`.

## Il contenuto che si sfoca e si incrocia

`FluidGlassContentTransition(targetState, modifier, key, blurReference, contentColor, contentAlignment) { stato -> … }`:
chi esce si sfoca, rimpicciolisce appena e svanisce; chi entra arriva sfocato e si fa nitido; i due si incrociano al
massimo della sfocatura, e la tinta cambia lì dentro. **Il contenuto con la stessa `key` nei due stati non si tocca**:
resta nitido anche se il vetro intorno corre.

- **Una transizione per posto**: in una barra di tre icone, tre transizioni. Una sola per tutta la barra sfocherebbe anche
  l'icona che resta.
- `key` è l'identità del contenuto (il simbolo, il testo); `null` è il vuoto (niente da disegnare, e il contenuto dopo il
  vuoto nasce da scala 0,75).
- `blurReference` è la sagoma su cui si misura la sfocatura: per un'icona la sua taglia (26 dp in una barra,
  `FluidMetrics.BarIconSize`), per un testo l'altezza di riga. La sfocatura massima è 0,15 della sagoma (≈2,9 dp su
  un'icona da 26 dp), a metà del tempo; il tempo di serie è **300 ms**, quello del vetro (`FluidMotion.PlaneContextMillis`):
  il massimo cade a 150 ms dal cambio. Nella clip di Apple a 16:02 (un render ingrandito, tempo ≈434 ms) il massimo è a
  200–267 ms; al passo dell'interfaccia vera (270–330 ms) cade a 135–165 ms. La curva è la stessa, cambia il tempo.
- `contentColor`, se c'è, dà il colore di ogni stato e lo incrocia dentro la sfocatura: il colore arriva prima della forma.
- Sotto Android 12 (API 31) non c'è `RenderEffect`: il cambio è una dissolvenza senza sovrapposizione. Con il movimento
  ridotto il contenuto cambia di colpo. La scala delle animazioni di sistema allunga anche questo.
- La forma del vetro non è affare suo: la cambia il contenitore. Il contenuto sta già al suo posto d'arrivo.
- **Il contenuto che resta in un vetro che cambia forma** (F4A.5b): nelle clip di Apple a tempo vero il «+» che resta
  mentre la capsula si chiude su di lui si sfoca anche lui (0,8 della sfocatura dello scambio, con la stessa curva) e torna
  nitido. Nella barra è una variante da scegliere (`gallery.tune barkeep=soft`, di serie `sharp`: nitido, com'era); la
  regola non è decisa. I tempi dello scambio restano quelli: misurati sulle due clip a tempo vero, coincidono con la curva
  di serie entro il rumore (F4A.5b, `artifacts/liquid-glass/sessioni/f4a-5b/misure.md`).

## Le forme concentriche

`FluidConcentricShape(minimum)`: raggio = raggio del contenitore − distanza dal suo bordo, **angolo per angolo**, mai sotto
`minimum` né oltre mezzo lato corto. La distanza di un angolo è la **maggiore** delle due (dal bordo verticale e da
quello orizzontale): con margini diversi è l'unica che tiene l'angolo interno dentro quello esterno. Il contenitore di
forma si dichiara con `Modifier.fluidContainerShape(shape)` (un `ModifierLocal`): il riquadro è quello che vedono i
modificatori **alla sua destra**, quindi in `Modifier.fluidContainerShape(s).padding(8.dp)` il contenuto sta a 8 dp dal
bordo e un suo angolo concentrico prende il raggio di `s` meno 8.

- **Dentro un `FluidGlassContainer`** il piano riceve la forma già risolta: per il campo una concentrica è una forma ad
  angoli tondi come le altre, e un cambio di margine viaggia coi raggi (scena `concentriche` della galleria).
- **Fuori da un contenitore del piano** i modificatori dell'engine la risolvono al layout: **il primo fotogramma vede il
  minimo**.
- Da sola, senza contenitore di forma, è una `ContinuousCornerShape` di raggio `minimum`.
- Chi disegna da sé: `val radii = rememberFluidConcentricRadii(minimum)` e `Modifier.fluidConcentricRadii(radii)` sul nodo
  da misurare; `radii.radii` sono i quattro raggi in px.

Le misure che servono alle barre sono in `FluidMetrics`: 8 dp fra due vetri (`GlassSpacing`), 12 fra due gruppi
(`GroupSpacing`), passo di 44 dp delle icone dentro un gruppo (`GroupItemPitch`), margine della capsula dai bordi
(`CapsuleSideMargin`, `CapsuleBottomMargin`), bersaglio di tocco 48 dp (`TouchTarget`).

## Il lato e l'inchiostro: il patto delle app

**Adattamento uniforme.** Lato e inchiostro li decide il contenitore, con **una sonda sola** sul riquadro del grappolo più
grande: i pezzi non sondano e girano insieme, nello stesso fotogramma. Una barra di tre vetri sopra una foto che cambia
gira tutta insieme (scena `adattamento`). Un grappolo più grande di un tasto (lato corto oltre la soglia dei grandi)
scurisce in continuo invece di girare, come i grandi di [vetro.md](vetro.md). Un contenitore dentro un vetro adattivo (un
foglio, un pop-up) non sonda: prende il lato da lui.

**Il patto dell'inchiostro è il contenitore.** Il contenuto dentro un `FluidGlassContainer` riceve l'inchiostro del lato
(`LocalContentColor`, lo schema di colori del lato e `LocalFluidGlassAppearance`) e al giro svanisce e risale con lui.
**Un `fluidGlass` da solo, fuori da un contenitore, non gira** (come `glassSurface`): il vetro non sa di che colore è
ciò che l'app gli disegna sopra, e un vetro che girasse sotto un testo scuro lo renderebbe illeggibile. **Per far girare
un vetro solo basta un contenitore con un pezzo.** Chi colora a mano (`tint = …`) legge `LocalFluidGlassAppearance`.

I tasti di vetro dell'engine dentro un contenitore prendono il lato del contenitore anche col loro inchiostro (il
difetto del «tasto annidato del lato del tema» è chiuso qui, per i tasti dentro un contenitore).

## I pannelli e il vetro annidato

Dalla F3.2e il contenitore sa anche **un pannello**: un vetro che porta contenuto (una card, un dock, la barra di
scrittura, la capsula di un campo), con il ruolo, la pellicola e l'ottica di un `glassSurface`. `fluidGlass` non basta:
ha la ricetta dei controlli (la loro pellicola, il ruolo `Floating` o `Interactive`) e la tinta di Apple, non la pellicola
di un pannello. Le quattro cose che servono:

```kotlin
// La pellicola si legge FUORI dal contenitore: è quella del lato del tema, e il lato lo fa il contenitore.
val tint = GlassDefaults.modalTint()
FluidGlassContainer(modifier, propagateMinConstraints = true) {          // 4. i vincoli dell'app arrivano al vetro
  Column(
    Modifier
      .fillMaxWidth()
      .fluidGlassPanel(backdrop, tint, shape, GlassRole.Modal)            // 1. il pezzo-pannello
      .padding(16.dp),
  ) {
    FluidGlassNested {                                                    // 2. i vetri qui dentro sono annidati
      Text(answer)
      FluidGlassIconButton(onClick = stop) { FluidIcon(FluidIcons.Filled.Stop, null) }
    }
  }
}
```

1. **Il pezzo-pannello**, `Modifier.fluidGlassPanel(backdrop, tint, shape, role, optics, large, exports,
   resampleIntervalMillis)`: in un contenitore il vetro lo disegna il contenitore **con gli stessi ingressi** che
   `glassSurface` riceverebbe (la pellicola dell'app, il ruolo, l'ottica passata da `sanitized()`, nessun filo,
   l'ombra e il corpo della stessa lastra). Fuori da un contenitore, col vetro di prima e sotto Android 13 **è**
   `glassSurface(…)`, al pixel com'era. Identità, unione e transizione si scrivono prima, come per `fluidGlass`.
2. **Il vetro annidato**, `FluidGlassNested { … }`: dentro, un vetro (`fluidGlass`, `glassControlSurface`, i tasti di
   vetro dell'engine) **non è un pezzo**: è un vetro come fuori dal contenitore, che campiona il materiale del pannello
   (la regola del vetro annidato: velo e ombra ridotti) e prende il **lato del contenitore**, anche mentre gira o scurisce.
   Non aggiunge nodi di layout (lo scope della `Column` resta in vista). Senza, un vetro nel contenuto di un pannello è
   un **fratello** del pannello nel piano e ci si fonde (com'era prima di F3.2e).
3. **L'export**: il pannello pubblica il suo materiale, sempre. Di serie in uno stato suo, che si registra solo mentre
   qualcuno lo campiona; con `exports = stato` in quello dell'app (la capsula del composer lo passa ai suoi tasti con
   `rememberCombinedGlassBackdrop`, come prima). Il nodo del pannello fa da contenitore di vetro (`glassContainer`) per
   il suo contenuto: il vetro annidato lo trova da solo, come lo trovava dentro un `glassSurface`. In un grappolo di più
   pezzi esporta la superficie del grappolo (il primo pezzo che esporta), e durante un viaggio il campo che lo porta.
4. **I vincoli**: un pezzo solo nel suo contenitore riceve il `modifier` dell'app **sul contenitore** (è lui il figlio
   del genitore: `align`, `weight`, `padding`, i tag restano dove l'app li ha scritti). Con
   `propagateMinConstraints = true` il contenitore passa al contenuto i vincoli minimi (come
   `Box(propagateMinConstraints = true)`): un `Modifier.weight` o una misura imposta allargano **il vetro**, non solo il
   contenitore. Di serie è falso, perché in un contenitore con più pezzi cambierebbe il layout di chi ci sta dentro (una
   `Row` costretta alla larghezza del contenitore sposta i pezzi di un `Arrangement.Center`): è l'errore di
   `ProvideFluidChrome` in F2 (C-R1), da non ripetere.

**Gira o scurisce.** Un pannello dice se è un **grande** che scurisce in continuo (`large = true`: card, dock, foglio) o
un **piccolo** che gira (`large = false`: barre, capsule). Null: lo dice il ruolo, come per i grandi dell'engine (`Modal`
e `Content` scuriscono). Il contenitore segue ciò che ha dichiarato un pezzo del grappolo più grande; senza
dichiarazioni (i `fluidGlass` e i tasti) la taglia, come prima: il grappolo di **area maggiore** del contenitore, e in lui il
**lato corto più grande** fra i suoi pezzi; da **72 dp** in su è un grande che scurisce (la rampa dei grandi va da 48 a 96
dp, e a metà, 72, passa). Una capsula lunga ma bassa accanto a un pannello conta per la sua altezza. Va detto quando il ruolo sbaglia: una barra di scrittura
`Modal` alta una riga gira, un dock `Floating` a raggio di foglio scurisce, la capsula larga di un composer (due righe,
più di cento dp) gira anche se la taglia la direbbe grande.

**Un pannello da solo**: `FluidGlassAnimation.None` è di solito la scelta giusta. Il pannello non ha vicini da cui nascere
né in cui confluire, e il suo contenuto cresce (una risposta che arriva, la tastiera che sale): con la molla del piano il
vetro inseguirebbe il contenuto per un terzo di secondo, col testo fuori dal bordo; senza viaggio lo segue nel fotogramma
dopo. È la scelta dei pannelli di `engine-ai-ui` ([../moduli/ai-ui-overlay.md](../moduli/ai-ui-overlay.md)).

## `Clear`

`Modifier.fluidGlass(FluidGlass.Clear)` è il limpido: niente smerigliatura, uno scurimento fisso del 35% sotto il vetro
(HIG), nessuna adattività. Va usato solo se tutte e tre: sta sopra contenuto ricco (foto, video), quel contenuto regge lo
scurimento, e ciò che sta sul vetro è chiaro e marcato.

Dalla F3.2c è **la variante della ricetta** fino alle superfici (lastra ferma, superficie fisica del riposo e del palco),
dentro e fuori da un contenitore: lo sfondo arriva nitido per la mescola a monte, **col raggio di sfocatura che resta**
(piano 3.6), quindi fra limpido e di serie cambia un peso e la cattura non si rifà. Col vetro di prima e sotto Android 13
il limpido è il vetro di serie. `Clear` e `Regular` nello stesso contenitore non sono mai un grappolo (varianti diverse).

## Cosa non fa ancora

- **Fratelli, se non annidati**: un vetro dentro un altro pezzo dello stesso contenitore, fuori da un `FluidGlassNested`,
  è suo fratello e non ne campiona il materiale. Un vetro annidato in un pannello non viaggia col piano (non è un pezzo):
  si sposta col contenuto. Il contenitore dentro un pannello di vetro campiona il pannello (scena `concentriche`).
- **Un pannello che cresce** è un cambio del piano: con `FluidGlassAnimation.None` il vetro lo segue nel fotogramma dopo,
  e il contenuto con lui (dal giro di correzione di F3 cambiano nello stesso fotogramma: un fotogramma di ritardo per
  tutti e due mentre la tastiera sale o la risposta arriva, mai il contenuto fuori dal vetro); con la molla di serie lo
  insegue.
- **Un pezzo la cui misura segue un valore continuo** (una capsula di avanzamento, un timer che si allarga, uno slider di
  vetro): il cambio di misura è del pezzo e non del contenitore, quindi per costruzione ogni fotogramma apre o fa ripartire
  un viaggio, la luce non si spegne più e il contenuto vive ritagliato su una sagoma in ritardo (review di F3, D R15; non
  ancora provato in galleria). Come per il pannello che cresce: `FluidGlassAnimation.None`, o un vetro fuori dal
  contenitore. La regola del contenitore che cambia misura (sopra) non lo copre: lì cambiano i vincoli del genitore, qui
  la misura di un pezzo.
- **Forme**: una forma che il campo non sa rendere (un `Outline` generico) resta un vetro a sé, fuori dal piano.
- **L'unione lontana**: oltre circa un raggio e mezzo d'angolo di varco il collo pieno non sta nel filo dei tasti, e
  l'unione resta una cattura sola con sagome separate. Apple a quella distanza disegna probabilmente una capsula sola
  (l'inviluppo): da vedere in S3.
- **Crescita sotto il dito**: c'è nelle lastre di un pezzo solo; in un grappolo di più pezzi la lastra non cresce (il
  contenuto sì): la porta nella geometria la fase dei controlli (F6.1). `depth`, `frozen`, `heldWhileMoving` di
  `glassControlSurface` li decide il contenitore.
- **Chi compare accanto a chi si sposta** è un campo a sé (una superficie in più per la durata della comparsa): la sua
  comparsa è esatta, ma mentre compare non si fonde col vicino.
- **Il primo fotogramma di un contenitore nuovo**: le superfici di riposo si montano nel fotogramma dopo la prima misura;
  dalla 4.0 (F3.10) il contenuto dei pezzi aspetta lo stesso fotogramma, e vetro e contenuto compaiono insieme, un
  fotogramma dopo la composizione. Non ricomporre il contenitore per cambiare stato (una `key(...)` attorno alla
  pagina): si cambia lo stato dei pezzi, e il piano viaggia.
- **Una sorgente che cambia solo nel disegno** (un fondo che passa da chiaro a scuro in `drawBehind`, sotto un antenato
  col suo layer): dalla 4.0 (F3.10) `glassBackdropSource` lo annuncia da sé (`glassSourceRedraws`, dentro il suo layer),
  e la sonda del contenitore rilegge. Non serve più `GlassBackdropState.notifyChanged()` nel disegno; resta per lo
  scorrimento fatto dal codice e per un figlio con un suo layer che cambia a pagina ferma.
- **La luce del dito nei grappoli**: un pezzo di un grappolo di più pezzi a riposo non mostra ancora il bagliore della
  pressione (F6.1); toccato, il suo viaggio porta il bagliore da zero, in ~70 ms, invece che da quello del vetro fermo.
  Un `fluidGlassPanel` non è interattivo: il suo tocco non porta luce.
- **Una scritta che cambia lunghezza** (stesso `id`, la capsula cambia taglia per il testo, scena `testo`): il contenuto
  nuovo è già della misura d'arrivo mentre il vetro è ancora in viaggio, e il contenitore lo ritaglia sulla sagoma in volo,
  quindi per qualche decina di ms la scritta lunga può comparire tagliata ai bordi della capsula che si allarga. Non ancora
  visto sul tablet: da riprendere, e da dire qui com'è.
- **Il rientro iniziale** di una capsula che si allarga (R4 B2 (c): il bordo rientra di 0,19 H prima di allargarsi) non
  c'è: le molle per lato partono ferme e non anticipano.
- **Il contenuto di un pezzo che cambia larghezza viaggia col centro del vetro**: in una capsula ancorata a un bordo che
  si allarga (un gruppo della barra che guadagna un'icona) il contenuto che resta si sposta per un attimo verso il bordo e
  torna, ritagliato. Da guardare sul tablet; un contenuto ancorato al bordo fermo è una proposta per la barra (F4A.2).
- **Altri componenti** che posano il `modifier` dell'app fuori dal vetro non inoltrano ancora gli attributi del piano:
  `FluidGlassMenuButton`, il tasto di `FluidMorphMenuButton` e i controlli di `FluidControls.kt`. Lì un `fluidGlassId` non
  ha effetto e in debug il log lo dice; arrivano con barre e menu (F4A, F5A).
- **Vetro di prima** (`FluidGlassModel.Classic`) e **sotto Android 13**: il contenitore non disegna; ogni pezzo è il vetro
  di sempre, senza viaggi.
- **Più di otto pezzi** in un grappolo: il grappolo si divide, dichiarato.
- **Ruolo e ottica restano del primo pezzo** in un grappolo (la tinta no, dalla F4A.2: sopra, «L'unione»). Vale per i
  **pannelli**: un pannello e un tasto vicini con la stessa pellicola si disegnano col ruolo e l'ottica di chi viene
  prima; un pannello sta di solito da solo.
- **La striscia di una tinta è dritta**: il taglio fra due tinte è una retta a metà fra i pezzi, non segue il collo. In un
  blocco unito il passaggio di tinta è netto, a metà del collo; da guardare sul tablet (scena `tinta` di «Barre (prova)»).
- Le tre tavole di gusto di F3.3 sono decise (Alessio, 2026-11-16): distanza di fusione media (16 dp), collo «spesso»
  (1/2), comparsa da 1,09. Il collo «spesso» vale per tutto l'engine: anche i viaggi di `morphTo` (menu, barre, pannelli)
  hanno l'esponente 1/2 (prima restavano a 2/3; `PhysicsNeckPinchExponent`, e `PhysicsPlaneNeckPinch` è lo stesso numero).

## Costi noti

- **A riposo**: una cattura per grappolo (un pezzo solo è la lastra di sempre, con l'ombra a maschere; due o più sono
  una superficie fisica ferma con l'ombra in corpo, il nodo allargato fin dove arriva l'ombra) e **una sonda** per
  contenitore. Un contenitore fermo non chiede fotogrammi: il suo orologio dorme finché niente cambia.
- **In viaggio**: una superficie fisica per campo del viaggio, per tutta la durata (il riposo a vista più la coda sotto
  il mezzo pixel: ~440 ms per una capsula che si stringe di 336 px), guidata «a fotogramma»: la guida si installa una
  volta, ogni fotogramma è una forma nuova (una piccola allocazione) e il piano si rifà solo quando la forma cambia.
  Una comparsa accanto a un vetro che si sposta è una superficie in più.
- **Il contenuto che si sfoca**: un `RenderEffect` di sfocatura per strato, solo durante il cambio.
- **La luce del viaggio** (F3.10): la cattura di un campo del palco ha il margine della sfocatura al colmo del carattere
  (con «a metà» il doppio: 13,5 dp per lato invece di 6,75), e nel mezzo del viaggio il raggio raddoppia. Da misurare
  sul tablet (framestats del RenderThread, `journey=off` contro di serie).
- **Il contenuto dei pezzi**: un layer per pezzo, sempre (a riposo senza effetti: prende il posto del layer della
  dissolvenza dell'inchiostro); in viaggio un ritaglio a sagoma per pezzo e una sfocatura solo per chi nasce, compare o
  se ne va. Chi se n'è andato tiene il suo layer finché il suo contenuto non è svanito, poi lo rilascia.
- **Chi non cambia** non ha costo di viaggio: resta la sua cattura di riposo.
- **Da misurare sul dispositivo** (sessione S3): il costo di un palco con 4 e con 8 pezzi (scena `molti` della galleria:
  gli otto sono uniti, quindi un campo solo pieno), e un viaggio sopra un contenuto che scorre (la cattura del palco va
  rifatta a ogni fotogramma: scena `lista`, la lista scorre da sola a ogni cambio di stato).
- **La coda di Apple** (F3.12c): una nascita tiene il palco fino a ~850–900 ms e una confluenza fino a ~900 (erano 500),
  finché lo scarto è sopra il mezzo pixel: una quarantina di fotogrammi in più a 120 Hz, con la luce già spenta e il
  collo già rotto (il palco costa lì quanto un vetro che si sposta piano). Da misurare in F7.

## La diagnostica

`FluidGlassContainerDiagnostics` (per le prove e le sessioni, non per le app): `totalJourneys` (viaggi partiti),
`totalRetargets` (ripartiti a metà), `totalOrderWarnings`, `liveContainers`, `lastJourney` (un
`FluidGlassFlightRecord` per vetro in volo: da quale `id` a quale, il cambio, il vicino da cui nasce o in cui confluisce)
e `lastRest` (un `FluidGlassPieceRecord` per vetro a riposo: `id`, riquadro, raggi risolti, taglia del grappolo, unione,
`exports` se esporta il suo materiale), `lastRestLarge` (se nell'ultimo riposo il contenitore scurisce in continuo),
`lastStart` (giro di correzione di F3: quanto ha aspettato l'ultima partenza da fermo dal momento in cui il contenitore
ha saputo del cambio, e il lavoro del suo primo fotogramma; nelle tracce di Perfetto le sezioni `FluidGlass.step` e
`FluidGlass.start`),
`lastContents` (un `FluidGlassContentRecord` per pezzo dell'ultimo viaggio: `id`, la parte del suo contenuto, se il pezzo
se n'è andato), `lastFinger` (F3.10: il bagliore da cui comincia il campo del vetro toccato nell'ultimo viaggio, zero
senza tocco). Le sonde vive: `FluidGlassProbeDiagnostics.liveProbes`; i ridisegni delle sorgenti che le hanno svegliate:
`totalSourceRedraws`. La pagina «Piano flottante» della galleria scrive tutto questo in
logcat (`adb logcat -s FluidPiano`), una riga per cambio di stato e per fine viaggio.

## Cosa è verificato dai test

- `FluidGlassTintSplitTest` (F4A.2): le tinte di un grappolo e le loro strisce (il taglio nel varco, la selezione in un
  blocco unito, una colonna, chi nasce dentro il donatore). In galleria, `FluidToolbarBarTest`: in un blocco unito di tre
  `FluidGlassIconButton` il selezionato si disegna con la sua tinta e gli altri no, anche quando la selezione si sposta; la
  conferma tinta accanto a un gruppo neutro resta tinta.
- `GlassPlaneTest`: la logica pura del piano (registro, grappoli, accoppiamento, classificazione, palco, viaggio
  interrompibile, riposo a vista dentro R4 B2, comparse in un campo a sé). `GlassPlaneResizeTest` (F4A.5b): il cambio di
  larghezza (orologio, attesa, curva contro le due clip, trascinamento e suo tetto nel posto del palco, il gruppo che si
  allarga tirato dalla partenza, chi viaggia con lui sul suo orologio, l'interruzione senza attesa) e il respiro del
  vetro che cambia contenuto; `FluidGlassContentKeyTest`: un cambio solo di contenuto non parte ma il riposo lo sa. `PhysicsPlaneNeckTest`: il collo del piano
  (una rottura sola a tre quarti del viaggio, la riunione che si vede, l'esponente «spesso», l'unione che è una sagoma
  sola a riposo e i distinti separati).
- `FluidGlassContainerDriverTest`, `FluidGlassContainerOrderTest`, `FluidGlassContainerProbeTest`,
  `FluidGlassContainerLookTest`: il conducente, l'ordine dei modificatori, la sonda unica, l'aspetto dei pezzi.
  `FluidGlassContainerPanelTest` (F3.2e): l'aspetto del pannello (gli ingressi di `glassSurface`, il grande dichiarato o
  del ruolo, l'export), la decisione del grande del contenitore, i vincoli del contenuto.
- `FluidGlassContentTransitionTest` (il contenuto che si sfoca, e la grammatica del contenuto dei pezzi sulla fase),
  `FluidConcentricShapeTest` (l'aritmetica dei raggi), `FluidMetricsTest`.
- F3.8a: in `GlassPlaneTest` i tempi della comparsa e della scomparsa, la comparsa interrotta, la coda senza ritocchi, i
  pezzi fermi, l'unione come viaggio, la fase; in `FluidGlassContainerDriverTest` il tetto dei primi passi, il collo
  delle unioni che nascono e si sciolgono, i grappoli che restano al riposo, il carattere del viaggio e il
  rigonfiamento; in `GlassProbeSchedulerTest` la sorgente che ridisegna e fa rileggere la sonda; `F38aExportTest` legge
  dalle tracce del banco ciò che S3 ha visto sul tablet (nessuna scritta fuori dal vetro, il contenuto di chi nasce dopo
  metà viaggio, due fermi che non passano dal palco, «Unisci» e «Separa» come viaggi).
- F3.11, F3.12a e F3.12c: in `GlassPlaneTest` la staffetta e la parentela, il donatore che va per primo dopo l'attesa,
  chi nasce piccolo dentro il donatore, la molla di rilascio coi tempi di Apple sull'orologio corretto (arrivo a ~296
  ms, colmo a ~367, ritorno a 540, la coda dall'altra parte e ferma sotto il mezzo pixel, il vetro rigido), il
  rigonfiamento proprio di chi nasce e del donatore al posto del comune, i colmi di due nati che
  cadono insieme, il rinculo e il rimbalzo del donatore, il pezzo che arriva gonfio, la spinta su chi accoglie,
  l'interruzione a metà rilascio, il superamento che non esce dal posto del palco (la finestra, non il contenitore: giro
  di correzione di F3, con la prova differenziale contenitore stretto contro largo), la vita della goccia che si stringe
  fino alla rottura senza riaprirsi, la spec dell'app senza coreografia; in
  `FluidGlassContainerDriverTest` il contenuto che viaggia col suo vetro e il rigonfiamento per pezzo; in
  `PhysicsPlaneNeckTest` la rottura dopo il colmo e, per un donatore che se ne va lasciando un varco da 12 a 26 dp, un
  collo che si rompe una volta sola e non si rifà; `F38aExportTest` legge dalle tracce che «Fatto» supera il suo posto
  col vetro, si mette a fuoco con una rampa senza scalino (leggibile a 300 ms, nitido al colmo), che «Modifica» rincula e rimbalza, e che il contenuto di chi confluisce si
  legge fino a ~230 ms; `F310ExportTest` che l'alone sale con la luce e si spegne prima; `GlassJourneyTest` l'alone sul
  suo livello e il filo che cala col dito.
- F3.10: `FluidGlassJourneyLightTest` (la luce del carattere nel palco, `journey=off` che spegne luce e rigonfiamento, il
  tocco che fa partire il campo dal bagliore della pressione e lo riempie, il cambio senza tocco, la ripartenza),
  `PhysicsJourneyLightTest` (la ricetta col carattere dopo il vetro in viaggio, al bit a livello zero, il margine del
  palco, il bagliore del dito); in `GlassProbeSchedulerTest` l'avviso montato dentro il layer della sorgente;
  `F310ExportTest` le tracce delle tavole di F3.10; nella galleria `GlassSourceRedrawsTest` (un contenitore fermo sopra un
  fondo che cambia solo colore rilegge e gira) e `FluidGlassFingerLightTest` (un tocco vero accende il campo, un cambio
  dall'app no).
- Nella galleria, in Robolectric: `FluidGlassContainerTest` (i pezzi si accoppiano come dichiarato, l'ordine, una cattura
  per grappolo e una sonda, il tasto dell'engine come pezzo, la concentrica nell'albero) e `PlanePageTest` (ogni scena
  della pagina «Piano flottante», le diciassette: il viaggio dichiarato, nessun avviso d'ordine, il fondo che cambia non
  apre viaggi, il comando che interrompe fa un viaggio e due ripartenze; le sette del giro di correzione di F3, `due-nati`,
  `bordo`, `tre-vicini`, `lista`, `testo`, `piu-contenitori` e `ridimensiona`, controllano gli `id` e chi nasce da chi). `FluidGlassContainerPanelTest` (F3.2e): il pannello è un
  pezzo che esporta e dichiara il grande, i vetri in `FluidGlassNested` non sono pezzi, senza lo sono, un `weight` e una
  misura imposta arrivano al vetro, di serie i minimi non passano e un contenitore di più pezzi non cambia layout;
  `AssistantPageTest` (le superfici di `engine-ai-ui`).
- **Non verificato dai test**: come si vede. Il collo, la comparsa, il passaggio di consegne, il giro uniforme e il
  contenuto sfocato si guardano sul tablet (sessione S3, elenco in `artifacts/liquid-glass/sessioni/f3-6/riprese-S3.md`)
  e sul banco (`tools/glassbench`, scene `f3/`).

## Dove guardare

- `engine-ui/…/ui/fluid/FluidGlassContainer.kt`: contenitore, modificatori e loro ordine, conducente, palco e riposo,
  sonda unica, `FluidGlassNested`, diagnostica. `…/ui/fluid/FluidGlass.kt`: `FluidGlass`, `Modifier.fluidGlass` e
  `Modifier.fluidGlassPanel`.
- `…/ui/glass/field/GlassPlane.kt`: la logica pura (registro, grappoli, accoppiamento, viaggio); il contratto delle tracce
  in `tools/glassbench/traces/f3/README.md`.
- `…/ui/fluid/FluidGlassContentTransition.kt`, `…/ui/fluid/FluidConcentricShape.kt`, `…/ui/fluid/FluidMetrics.kt`, i token
  `Plane*` in `…/ui/fluid/FluidMotion.kt`.
- La galleria: `sample/…/plane/` (pagina «Piano flottante», diciassette scene con il loro `LEGGIMI.md`; fra queste i casi
  che mettono alla prova le decisioni: due nati da un tasto, il bordo del contenitore, tre tasti a 8 dp, la lista che
  scorre, la scritta che cambia lunghezza, due contenitori, il contenitore che cambia misura).
