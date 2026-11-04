<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/pagine-back-e-barra.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Pagine: il back sul dito, `FluidPageInput`, la barra ancorata, i token

Seconda meta' della famiglia «Pagine e navigazione» (la prima, con host, `FluidNavHost`, migrazione e stili, e' [pagine.md](pagine.md)).
Qui sta tutto quello che riguarda **il dito e il tasto indietro**: il riconoscitore del back dentro l'app (`FluidBackSwipe.EdgeOnly`/`Anywhere`/`Off`), il back di sistema (e predittivo)
che l'host segue sulla posizione vera del dito, la regola del rilascio, i `BackHandler`; poi la **barra unica per host** (la pagina la descrive, l'host la disegna), `FluidPageChrome`,
i numeri di `FluidPageMotion` e cio' che e' verificato dai test. Firme: [../api/pagine.md](../api/pagine.md). Il perche': [../regole.md](../regole.md) («Le pagine non sfumano e seguono il dito», «Le pagine: una barra per host»)
e [docs/riferimenti-3.0.md](../../riferimenti-3.0.md) sezione 2.

**Modello mentale.** Un dito, un pilota: o il riconoscitore dell'app (`FluidPageInput.kt`, sull'host) o il back di sistema (un gestore sul dispatcher di navigationevent), mai due insieme. Entrambi dicono all'host la **corsa** in pixel
(`dragTo`), e l'host disegna tutto da li'. Il rilascio non toglie la pagina da se': **chiede** il back al dispatcher, come farebbe il sistema.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il back col dito come quello di sistema (dal bordo) | niente: `FluidBackSwipe.EdgeOnly` e' di serie in `composable(...)`, `FluidPageHost`, `FluidNavHost` |
| una pagina che si tira da qualunque punto | `backSwipe = FluidBackSwipe.Anywhere` (opzione esplicita; era di serie dalla 3.0.0 alla 3.0.2) |
| una pagina senza back col dito | `backSwipe = FluidBackSwipe.Off` |
| «uscire senza salvare?» su un editor | un `BackHandler`/`PredictiveBackHandler` nella pagina, abilitato solo con modifiche da perdere; **non** spegnere il gesto |
| una pagina piena di gesti propri (mappe, pager, griglie in selezione) | niente: col bordo di serie non c'entrano; se si era messo `EdgeOnly` a mano si toglie |
| sapere dov'e' il dito / quanto e' avanzata la pagina | `FluidPageHostState.progress`, `isInteractive`, `velocity` (letti in disegno) |
| la barra di una pagina con titolo grande e azioni | `FluidScreen` (registra da sola); `FluidCollapsingTopBar` |
| la barra di una pagina che non e' una `FluidScreen` | `FluidPageChrome(title, onBack, backdrop, barTint, actions)` |
| una barra mia dentro una pagina | non farlo: due barre; la pagina descrive, l'host disegna |
| NON cosi': guardare il `progress` del back di sistema per muovere una pagina | la pagina sta sul `touchX` (l'host lo fa gia'); il `progress` serve solo a dedurre un annullo |
| NON cosi': una `tween` di durata fissa all'uscita | la molla parte dalla velocita' del dito (`FluidPageMotion.page()`) |
| NON cosi': un `PredictiveBackHandler` per animare la pagina | l'host e' gia' sul dispatcher; il tuo gestore serve solo a decidere se il back vale |

## Il back sul dito: la regola

La pagina sta sulla posizione vera del dito (`touchX`) **pixel per pixel, finche' il dito non la lascia**. Ne seguono le altre scelte (dal KDoc e da `regole.md`):

- **la molla parte dalla velocita' del dito**, mai una `tween` di durata fissa: al rilascio la velocita' non si butta, e la pagina continua il suo gesto invece di cominciarne un altro. Criticamente smorzata (`FluidMotion.DampingChrome` = 1): **una pagina non rimbalza mai**;
- **il rilascio** (`FluidPageMotion.backRelease`): conferma oltre il 35% della larghezza (`CommitFraction`) con un tetto di 180 dp (`CommitCapDp`: su un tablet da 800 dp il 35% sono 280 dp, un braccio intero), oppure con una spinta di 400 dp/s (`FlingDpPerSecond`: in dp, quindi uguale su ogni densita'; LibrePods usa 1000 px/s, circa 380 dp/s sul suo telefono).
  Una spinta **contraria** della stessa forza annulla anche oltre la distanza: il dito ha cambiato idea e la molla gli da' ragione. Larghezza zero: conferma;
- **un gesto confermato chiama `onBack()` prima della molla**, che continua mentre la pila si accorcia: l'app non deve sapere che c'e' una molla. Se l'app non toglie la pagina, torna a posto (due fotogrammi dopo);
- **nessuna vibrazione nel gesto**: una pagina che scorre non e' un momento da sentire;
- **un back annullato torna** con la molla piu' corta `pageReturn()` (`FluidMotion.ResponseSnappy` = 0,30 s, mentre `page()` ha risposta 0,34 s: fra `standard` 0,38 e `snappy` 0,30, perche' un push non deve sembrare una tenda);
- **un dito su una pagina che sta tornando a posto la riprende al volo**, dal punto in cui e' (`canGrab`): corsa, verso e pagine restano quelli di prima.

## FluidBackSwipe: EdgeOnly, Anywhere, Off

**Cos'e'.** Da dove si puo' tirare indietro una pagina col dito, dentro l'app. Il back di sistema non c'entra (con la navigazione a gesti i tocchi sul bordo non arrivano nemmeno all'app, e il gesto di sistema segue comunque il dito).

| valore | cosa fa |
|---|---|
| `EdgeOnly` | **di serie (3.0.3)**, come il gesto di sistema: solo dai primi 20 dp del bordo d'ingresso (`FluidPageMotion.EdgeZoneDp`; a destra in RTL). Decide nel passaggio `Initial`, prima dei figli: righe, card, tasti e scorrimenti in mezzo alla pagina non c'entrano e non serve piu' sapere chi consuma il dito |
| `Anywhere` | da qualunque punto, **purche' nessun figlio si sia preso il dito per primo**. Decide nel passaggio `Main`, dopo i figli. Uno scorrimento orizzontale arrivato al suo inizio passa la mano alla pagina |
| `Off` | mai col dito dentro l'app; restano il back di sistema (con gesto: la pagina lo segue comunque) e il tasto. Per chi non vuole che la pagina si muova col dito |

**Uso.**

```kotlin
composable("dettaglio") { /* bordo: di serie */ }
composable("galleria", backSwipe = FluidBackSwipe.Anywhere) { /* da qualunque punto libero */ }
composable("editor", backSwipe = FluidBackSwipe.Off) { /* niente dito: solo tasto e back di sistema */ }
```

**Con `Anywhere`: chi consuma vince.** Non parte il back da:
- un tasto (`FluidPressGrip.HoldsFinger`: i tasti consumano gli spostamenti, `clickable` il contatto): un trascinamento orizzontale che parte su un tasto e' del tasto;
- il segmentato, anche dal segmento gia' scelto (la goccia), dalla 3.0.1;
- una riga, una card o un messaggio **cliccabili**: `clickable` consuma il contatto, e il riconoscitore lo legge come «un figlio se l'e' preso»;
- uno slider (non passa mai la mano), un interruttore;
- una lista che scorre di lato **finche' non e' al suo inizio**: da li' offre il resto del dito alla pagina (`FluidPageNestedScroll`, solo con `Anywhere`).
Il back «ovunque» parte quindi dove nessuno prende il dito (il fondo, il testo, i titoli) e **sempre dal bordo**. Un primo movimento contro l'uscita o in verticale (oltre la soglia di movimento) abbandona; con `Anywhere`, se un pager o una riga potrebbero tornare al loro inizio, il riconoscitore resta a guardare.
Un gesto che parte nella **riga dei controlli della barra** (sopra `statusBars + FluidScreenDefaults.ControlRowHeight`, 44 dp) e' dei suoi tasti: mai il back (serve anche al menu delle azioni, che si sceglie trascinando).

**Comportamento del riconoscitore** (sull'host, **mai sulla pagina che si muove**: dentro un layer traslato le posizioni sono relative al layer e una pagina che insegue il dito vedrebbe il dito fermo):
- la corsa e' `dito − punto di contatto`, verso l'uscita, a partire dal fotogramma che supera la soglia di movimento (`touchSlop`; quella di Robolectric e' 32 px): dito e bordo della pagina restano alla distanza che avevano al contatto. LibrePods accumulava i delta dopo la soglia e la pagina restava indietro di una soglia;
- preso il dito, consuma tutto nel passaggio `Initial`: i figli vedono il gesto consumato e lasciano. Mentre la pagina e' del dito il resto orizzontale si beve (niente stiramento di overscroll sotto una pagina che si sposta, niente lancio della riga quando la pagina la lascia);
- la velocita' si misura come `draggable` (anche i campioni storici di un evento), nelle coordinate dell'host che non si muove, ed e' quella che passa alla molla;
- se il gesto si interrompe (l'host esce dalla composizione, il puntatore sparisce) decide la distanza.

**Trappole.**
- Un'app che aveva messo `EdgeOnly` per i propri gesti (mappe, pager, griglie in selezione) puo' toglierlo; una che voleva il gesto ovunque nella 3.0.0-3.0.2 deve ora passare `Anywhere` (3.0.3, **cambia al tatto**).
- Con `Anywhere` una lista fatta di tasti o di righe cliccabili non si tira indietro dal mezzo: voluto. Per il back si parte dal bordo.
- Una pagina con `Off` si muove comunque col **gesto di sistema** (e' il sistema che lo da'): `Off` spegne solo il dito dentro l'app.
- Il test del «da qualunque punto» deve chiedere `Anywhere` (di serie e' il bordo).

**Versione.** 3.0.0: `Anywhere` di serie, 3.0.1: segmentato che tiene il dito, 3.0.3: `EdgeOnly` di serie.

## Il back passa dal dispatcher: BackHandler e conferme

Il rilascio col dito, oltre la soglia, manda al dispatcher di navigationevent lo stesso «indietro completato» che il sistema manda a fine gesto. Risponde il **gestore abilitato registrato per ultimo**:

- **l'host si registra prima delle pagine**, quindi sta sotto i loro `BackHandler`. Un `BackHandler` (o un `PredictiveBackHandler`) abilitato dopo l'host risponde per primo: la pagina torna sotto il dito con la molla dell'annullo, il gestore gira (una volta) e decide.
  Un editor che chiede conferma la chiede quindi anche al dito. Vale anche per i gestori dell'engine (ricerca aperta, menu, modali), che si registrano dopo;
- senza gestori risponde l'host: la pagina esce, `onBack()` e la molla;
- **sotto la soglia nessun gestore gira**: e' un gesto annullato;
- un gestore che toglie la pagina da se' (un «salva e chiude») la fa uscire con la pila accorciata una volta sola: la molla riprende da dove era arrivata;
- un gestore **registrato prima dell'host** (piu' in alto nell'albero, composto prima) sta sotto di lui e il dito toglie la pagina lo stesso;
- dopo un gestore che ha consumato il dito, un gesto senza gestore toglie la pagina di nuovo normalmente;
- una spinta corta e veloce passa dal gestore come una lenta.

**Uso** (la pagina «Bozza» del sample, `PagesDemo.kt`):

```kotlin
composable("bozza") {
  var dirty by remember { mutableStateOf(true) }
  var asking by remember { mutableStateOf(false) }
  BackHandler(enabled = dirty) { asking = true }          // abilitato solo con modifiche da perdere
  FluidScreen(title = "Bozza", onBack = { if (dirty) asking = true else navController.popBackStack() }) { /* … */ }
  if (asking) FluidAlert(/* "Resta" / "Esci" -> navController.popBackStack() */)
}
```

**Trappole.** Un `BackHandler` sempre abilitato su una pagina impedisce al dito (e al sistema) di toglierla: abilitarlo solo quando serve. Una pagina senza dispatcher (host fuori da un'`Activity` con `NavigationEventDispatcherOwner`): niente back di sistema e il dito toglie direttamente.
`FluidBackSwipe.Off` non serve piu' per le conferme di uscita (3.0.1/3.0.3: era la toppa di prima).

## Il back di sistema e il back predittivo

**Cosa fa il sistema.** Con la navigazione a gesti (il KDoc dice Android 14+) arrivano all'app inizio, avanzamenti (con `touchX` e `progress`) e fine; coi 3 tasti, o fino ad Android 13, arriva solo la fine: e' un *tap-back* (l'app toglie la pagina e la molla l'accompagna fuori, `requestBack`).
Il `progress` lo smussa il sistema con una molla sua e lo scala su una sua distanza: **non e' la posizione del dito**. L'host lo ignora per muovere la pagina.

**Cosa fa l'host** (`FluidPageBackHandler`, sullo stesso dispatcher dei `BackHandler` dell'app):
- **la pagina segue `touchX`**. Il verso lo da' il bordo da cui e' partito il gesto (`FluidSwipeEdge`: dal bordo destro la pagina esce a sinistra). L'**ancora** da cui si misura il dito e' il bordo dell'host se tocca il bordo da cui e' partito il gesto (la pagina salta sotto il dito al primo evento: il dito era gia' a qualche pixel dal bordo
  quando il sistema ha riconosciuto il gesto), altrimenti il primo evento (un pannello di dettaglio in mezzo allo schermo: la pagina parte da zero) (`systemAnchor`). La corsa e' `(touchX − ancora) · verso`, mai oltre l'uscita (`systemTravel`);
- **la decisione e' del sistema**: a fine gesto conferma o annulla da se' (`release(decided = ...)`); la velocita' per la molla l'host la misura sui `touchX` con un `VelocityTracker1D`;
- **un dito, un pilota (3.0.4).** Con la navigazione a gesti l'app vede il dito che parte dal bordo finche' il sistema non se lo prende (riceve un annullo del puntatore), e il riconoscitore del bordo puo' aver gia' cominciato a tirare la pagina. Il sistema allora la prende **da dov'e'**:
  l'ancora si sposta perche' il primo evento dia la corsa che la pagina ha gia', invece di farla saltare sotto il dito (fino a 20 dp). Vale anche per una pagina che sta tornando a posto;
- **l'abilitazione** segue la pila: il gestore e' attivo solo con piu' di una pagina.

### Come l'host deduce l'annullo (CHANGELOG 3.0.4)

Il sistema annulla un back con ~200 ms di ritardo: prima riporta il suo `progress` a zero con un'animazione sua, a dito gia' alzato, poi chiama l'annullo. Per non lasciare la pagina ferma un decimo di secondo l'host **deduce l'annullo in anticipo** (`FluidPageMotion.inferSystemCancel`):

```kotlin
// il progresso e' sceso `falls` eventi di fila (almeno SystemCancelFalls = 2) col dito fermo (SystemStillPx = 0,5 px)
// NEL PUNTO PIU' LONTANO del gesto (reach >= farthest - SystemStillPx)
falls >= SystemCancelFalls && reach >= farthest - SystemStillPx
```

**Perche' il punto piu' lontano.** Misurato sull'emulatore (API 37): quando il dito torna verso il bordo il sistema decide che il back non partira' e riporta il suo `progress` a zero con la sua molla **a dito fermo e ancora giu'** (da 0,53 a 0 in sei fotogrammi, `touchX` immobile).
E' lo stesso disegno di un annullo; lo fa anche la molla che smussa il `progress` dopo un passo indietro o il tremolio di un pollice. Fino alla 3.0.3 un evento in calo col dito fermo bastava: la pagina partiva verso casa sotto un dito fermo e il movimento dopo la rimetteva sotto il dito, lo scatto «come indecisa su dove sia il dito».
Nel punto piu' lontano invece il progresso puo' solo salire finche' il dito e' giu': se scende, il dito si e' alzato. Altrove la pagina resta sotto il dito e aspetta la decisione del sistema (l'annullo vero arriva quando la sua molla ha finito).

Dopo un annullo dedotto: se i `touchX` riprendono a muoversi, il dito c'era ancora e la pagina si **riprende da dove la molla l'ha portata**, e da li' la si muove quanto il dito (l'ancora diventa `touchX − corsa · verso`; rimetterla sotto il punto di prima era un salto in avanti). Se invece arriva la conferma del sistema, `systemCommit` porta fuori la pagina da dov'e'.
Il «dito fermo» si misura dal punto in cui si e' fermato, non dall'evento prima: un dito che va piano (meno di mezzo pixel per evento) si sta muovendo.

**Se un'app vede ancora lo scatto.** La pagina che «parte» verso casa a dito fermo dopo un piccolo ritorno e' il difetto della 3.0.3: dalla 3.0.4 e' risolto nell'engine, non c'e' niente da compensare nell'app (e va aggiornata l'engine, non aggiunta una toppa).

## FluidPageHostState: cosa legge l'app durante il back

`isInteractive` e' vero mentre un dito (in-app o di sistema) tiene la pagina; `kind` e' `Back` finche' la molla non arriva a riposo (conferma o annullo compresi); `velocity` e' la velocita' del dito al rilascio e poi quella della molla.
Un back annullato porta `progress` indietro verso 0. Il resto dello stato: [pagine.md](pagine.md), sezione `FluidPageHostState`.

## FluidPageInput (il file)

`ui/page/FluidPageInput.kt` non ha API pubblica: contiene `fluidPageBackSwipe` (il riconoscitore sull'host), `FluidPageNestedScroll` (il passaggio di mano degli scorrimenti), `FluidPageBackHandler` (il back di sistema) e `displayCornerRadii` (gli angoli del display, API 31+).
La nota in testa al file dice cosa e' stato cambiato rispetto a LibrePods (da cui e' portato, GPL-3.0; `LICENSES/LibrePods.md`):

- il riconoscitore sta sull'host, non sulla pagina che si muove;
- corsa = dito − contatto dal fotogramma oltre la soglia;
- i primi 20 dp del bordo reclamano il dito *prima* dei figli; un primo movimento contro l'uscita abbandona; uno scorrimento orizzontale al suo inizio passa la mano;
- il back di sistema arriva dal dispatcher con `touchX` (non `PredictiveBackHandler` col `progress`), cosi' i gestori interni dell'app restano prima di noi;
- il dito dentro l'app, quando conferma, non toglie la pagina: manda al dispatcher lo stesso «indietro» e, se un `BackHandler` risponde per primo, la pagina torna sotto il dito e il gestore decide;
- la velocita' e' misurata e passa alla molla; nessuna vibrazione durante il gesto.

**Chi consuma vince** e' la regola di questo file per `Anywhere` (vedi sopra). La nota che diceva «i tasti non consumano» (3.0.0) era la regola della beta 1, cambiata dopo la beta 2; e' stata corretta nel KDoc e in `docs/03` con la 3.0.1.

## La barra: una per host

**Cos'e'.** Dentro un `FluidPageHost`/`FluidNavHost` la barra non viaggia con la pagina: **una per host, ferma**, sopra le pagine e sotto la chrome dell'app. La pagina la *descrive* (`FluidPageBarSpec`: titolo, tasto indietro, azioni, i numeri del titolo agganciato, il vetro da rifrangere) e l'host la disegna e la fa passare da una pagina all'altra
leggendo tutto in disegno, **senza ricomposizioni per fotogramma**.

**Chi registra.** `FluidScreen` e `FluidCollapsingTopBar` registrano la barra quando c'e' un host (fuori da un host disegnano quella di sempre: nessuna firma e' cambiata). Per una pagina che non e' una `FluidScreen` c'e' `FluidPageChrome`.
Il posto e' `LocalFluidPageBar` (interno): l'host lo da' a ogni pagina; `FluidScreen` fornisce `LocalFluidPageBar provides null` al **suo** contenuto e agli overlay, cosi' una `FluidScreen` dentro un pannello o un foglio disegna la sua barra come sempre.
`FluidListDetailScaffold` a due pannelli fa lo stesso: ogni pannello disegna la sua barra. Piu' registrazioni nella stessa pagina: vince l'ultima. La registrazione avviene **in composizione**, non in un effetto: la barra dell'host si compone dopo le pagine nello stesso passaggio e la vede al primo fotogramma.

### Cosa fa l'host con la barra (`FluidAnchoredBar`, interno)

- **Il vetro vede le due pagine insieme.** Mentre si muovono la barra rifrange la registrazione di tutte e due (`rememberCombinedGlassBackdrop`), ognuna al suo offset vivo, e l'intensita' passa dall'una all'altra: peso `1 − e` alla pagina sopra, `e` a quella sotto, con `e` = frazione d'uscita. Lo stesso impilamento va alla chrome dell'app
  (`FluidChromeController.setTransitionBackdrop`): una barra che rifrangesse una pagina sola mostrerebbe, sotto il vetro, una pagina che sta per essere coperta, e la tab bar non cambia fondale di colpo a meta'. La lettura in disegno fa rifare la cattura a ogni fotogramma del movimento (le pagine si spostano sotto un vetro fermo).
- **Il titolo grande e' contenuto, quello agganciato e' chrome.** Il titolo grande viaggia con la sua pagina, allo stesso pixel (la barra legge `pageOffsetX`/`pageScale` dello stato, la stessa geometria del layer); dove la pagina sopra copre quella sotto la barra lo ritaglia (`upperCoverage`). Il titolo agganciato resta fermo:
  quello che esce sparisce nella prima meta' e scivola di `BarTitleShiftDp` = 16 dp verso l'uscita; quello che arriva compare dal 40% e arriva dallo stesso lato (`barTitleFrame`). Si ferma prima del tasto indietro e delle azioni (misura di `FluidTopBarControls`, 3.0.1).
- **Il tasto indietro e' uno, mai ricreato.** Se ce l'hanno tutte e due le pagine sta fermo al pixel, sempre intero; fra radice e figlia nasce (o si spegne) da un **seme** di un pixel; il suo alpha segue `barIconAlpha`.
- **I tasti di vetro diventano quelli della pagina accanto.** Durante il moto il vetro del singolo tasto si spegne e una superficie sola di Fluid-physics, guidata da fuori (`FluidPhysicsState.driveExternally(from, to, progress)` fra gruppi di lastre), porta i pezzi della pagina che esce su quelli della pagina che arriva.
  L'accoppiamento (`barPieceJourney`) va dall'esterno verso l'interno: la prima azione da destra con la prima da destra, il tasto indietro col tasto indietro; chi resta senza compagno lo riceve come seme. **Oltre sei pezzi**, o con un controllo che non e' una capsula di vetro, le icone si incrociano e basta.
  Il raccordo fra le lenti e' piccolo (4 dp) di proposito: ai due capi i pezzi devono essere i tasti, separati come a riposo.
- **Le icone non si leggono mai insieme.** `barMorph(e)`: fermo nel primo e nell'ultimo 10% (un dito appena posato non deve gia' deformare i tasti), poi una smoothstep; le icone della pagina sopra se ne vanno presto (`1 − m/0,35`), quelle della sotto arrivano tardi (`(m − 0,25)/0,5`).
- **Si tocca solo a riposo.** Durante il movimento la riga dei controlli beve tutto prima dei controlli, e il tocco sul titolo (scorrere in cima) non fa niente. Un gesto partito sulla barra e' della barra: mai il back.
- **Misure.** Altezza dell'area = `statusBars` + `FluidScreenDefaults.ControlRowHeight` (44 dp) + `FluidScreenDefaults.GlassFadeTail` (34 dp); la riga dei controlli e' `statusBars + 44 dp`. Il contenuto di una pagina che non e' una `FluidScreen` deve lasciare lo spazio da se'.
- **Colore.** Sotto il contesto di ogni pagina c'e' un `LocalContentColor` di base (`onBackground`): una registrazione che non lo dice ha comunque il testo del fondo, non il nero (3.0.1). Chi lo dice vince.

**Trappole.**
- Disegnare una barra propria sopra il contenuto di una pagina: due barre, e la tua viaggia col dito.
- Un `FluidPageChrome` in una pagina dentro un host **non disegna niente da se'**: si descrive e basta. Fuori da un host si disegna in cima a chi lo chiama (da mettere in un `Box` sopra il contenuto).
- Pagine con un numero diverso di azioni: i tasti si trasformano (e' la demo: una, due, tre azioni); oltre sei pezzi nessun viaggio. Non e' un difetto.
- Nessun test dice com'e' il passaggio dal vetro del tasto alla superficie fisica (un lampo di un fotogramma?), il titolo grande in `ShrinkSlide`, la fluidita' della ricattura e il back di sistema su One UI: **si guardano in mano**.

**Versione.** Fase 8 della 3.0. 3.0.1: titolo agganciato che si ferma prima dei controlli, colore di base. `ui.page.FluidCornerRadii` si chiama `FluidPageCorners` (il nome era gia' di Fluid-physics).

## FluidPageChrome

**Cos'e'.** La barra di una pagina che non e' una `FluidScreen`: titolo agganciato, tasto indietro, azioni, e il vetro se c'e' una registrazione da rifrangere.

**Firma essenziale.**

```kotlin
@Composable fun FluidPageChrome(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,                 // null: niente tasto indietro
  backdrop: GlassBackdropState? = null,         // senza: barra trasparente, titolo e controlli sul contenuto
  barTint: GlassTint? = null,                   // di serie GlassDefaults.barTint()
  actions: @Composable RowScope.() -> Unit = {},
)
```

**Uso.**

```kotlin
composable("impostazioni") {
  Box(Modifier.fillMaxSize()) {
    FluidPageChrome(title = "Impostazioni", onBack = { navController.popBackStack() }, actions = { /* FluidBarAction(...) */ })
    // contenuto: padding in alto = statusBars + FluidScreenDefaults.ControlRowHeight
  }
}
```

**Comportamento.** Dentro un host registra la sua specifica (`morphState = null`: non c'e' titolo grande, solo quello agganciato, sempre «docked»; senza `onTapTitle`) con `context = currentCompositionLocalContext`: le azioni si compongono nella barra dell'host ma vedono quello che vedevano nella pagina (tema, `ViewModel` della voce, host dei modali).
Fuori da un host disegna la barra da se' (vetro con intensita' 1 se c'e' `backdrop`). Il colore del titolo e dei tasti e' `onBackground` **prima** di registrarsi, come fa `FluidScreen`: una pagina senza `FluidScreen` non ha mai detto il colore del testo, e fuori da una `Surface` valeva nero (titolo nero sul tema scuro, 3.0.1).

**Non usarlo quando.** La pagina e' una `FluidScreen` (registra da sola).

**Versione.** Dalla 3.0 (fase 8); colore del testo dalla 3.0.1.

## I token di FluidPageMotion

Costanti e funzioni pure; **un orologio solo**: ogni cosa che si vede e' funzione di `travel`. Le molle vivono qui e non in `FluidMotion` (sono di un componente solo; diventeranno token quando le useranno altri). Tutti i valori sono dal sorgente:

| costante | valore | cosa governa |
|---|---|---|
| `BelowParallaxFraction` | 1/3 | quanto arretra la pagina sotto, in frazione della larghezza |
| `EdgeZoneDp` | 20 dp | la zona del bordo che reclama il dito prima dei figli |
| `CommitFraction` / `CommitCapDp` | 0,35 / 180 dp | oltre questa frazione (col tetto) un rilascio lento conferma |
| `FlingDpPerSecond` | 400 dp/s | la spinta che decide da sola, nei due versi |
| `ShrinkTravelFraction` / `ShrinkAmount` | 0,30 / 0,10 | `ShrinkSlide`: il primo 30% della corsa rimpicciolisce (fino al 90%) |
| `ShrinkExitFraction` | 1,25 | dove finisce la corsa di `ShrinkSlide` ed `Expand`, in larghezze |
| `ExpandInitialScale` | 0,91 | da dove parte `Expand` |
| `CardElevationDp` | 36 dp | l'ombra della carta |
| `CardThresholdPx` | 0,5 px | sotto questa corsa niente angoli ne' ombra |
| `SystemStillPx` / `SystemCancelFalls` | 0,5 px / 2 | il dito di sistema fermo; eventi di fila per dedurre un annullo (3.0.4) |
| `FrontHandoff` | 0,5 | la pagina sopra resta «davanti» fino a meta' uscita |
| `ResponsePageSeconds` | 0,34 s | risposta della molla delle pagine (`ResponsePage` = `FluidMotion.stiffnessForResponse(0,34)`) |
| `VisibilityThresholdPx` | 0,5 px | le molle lavorano in pixel |
| `BarTitleShiftDp` | 16 dp | lo spostamento di lato del titolo agganciato |

Funzioni pure utili a chi scrive un test o una shell: `page()` e `pageReturn()` (le due molle, `DampingChrome`), `exitTravel`, `exitFraction`, `inAppTravel`, `systemTravel`, `systemAnchor`, `systemDirection`, `inferSystemCancel`, `backRelease`, `pageFrame`, `belowParallax`, `belowOffset`, `pageIsFront`, `peerFrame`, `pageCardRadii`, `classifyStackChange`,
`barMorph`, `barIconAlpha`, `barTitleFrame`, `barPieceJourney`. Tipi: `FluidPageFrame(translation, scale)`, `FluidPageCorners`, `FluidSwipeEdge` (`Left`, `Right`, `None`), `FluidStackChangeKind` (`None`, `Push`, `Pop`, `Replace`, `Peer`), `FluidStackChange`.

## Cosa e' verificato dai test

- `FluidPageMotionTest` (`engine-ui`, puri, 31 casi): la corsa del dito e di sistema (ancora sul bordo o sul primo evento, bordo destro), l'annullo dedotto, la regola del rilascio con tetto in dp e spinta nei due versi, larghezza zero, gli stili (`Slide`, la continuita' di `ShrinkSlide` a 0,30 W e a 1,25 W, `Expand`),
  nessun buco fra la pagina sotto e quella sopra (anche con la sopra rimpicciolita), il davanti che passa di mano a meta', gli angoli solo dove l'host tocca un angolo, la classificazione della pila (anche i pari che vincono sul push), il passo fra schede (13%, 150 ms), il morph dei tasti, le icone che non si leggono insieme, il titolo agganciato, l'accoppiamento dei pezzi, le molle criticamente smorzate.
- `FluidPageHostTest` (`sample`, Robolectric, 26 casi, schermo 360 dp a densita' 2): dal bordo e da un punto qualunque (con `Anywhere`) la pagina sta sotto il dito a ogni fotogramma; il default e' il bordo; il back di sistema con un `progress` volutamente storto segue `touchX`; conferma e annullo di sistema; i rilasci lenti sotto e sopra il 35%;
  spinta corta, spinta indietro oltre meta'; la velocita' del dito nella molla; il tasto indietro con corsa monotona; il salto di piu' pagine; il tasto indietro della barra uno e fermo al pixel; il titolo grande che viaggia con la sua pagina; riga che scorre di lato che passa la mano o scorre prima; cursore e segmentato che non portano mai indietro; un gesto che parte sulla barra e' della barra; RTL; animazioni ridotte; la bozza con modifiche che chiede al dito.
- `FluidPageBackHandlerTest` (12): `BackHandler` e `PredictiveBackHandler` attivi rispondono al dito oltre la soglia, la pagina torna con la molla, sotto soglia non gira, un gestore che toglie la pagina da se', uno spento non cambia niente, uno registrato prima dell'host sta sotto, il back di sistema con e senza gestori.
- `FluidPageSystemBackTest` (4): col dito fermo dopo un passo indietro la pagina resta sotto il dito; il dito che torna verso il bordo la porta con se' anche quando il sistema azzera il progresso; un annullo vero si riconosce ancora in anticipo; il back di sistema che prende un gesto gia' partito nell'app continua da dove e' la pagina.
- `FluidPageStateRestorationTest` (3): lo stato della pagina in cima e di quella coperta sopravvive alla ricreazione dell'Activity (`FluidNavHost` anche a due ricreazioni di fila, `FluidPageHost`).
- `FluidPeerNavigationTest` (3): le schede non si accumulano nemmeno quando la partenza non c'e' piu'; una figlia si chiude passando a un'altra scheda; la scheda sotto una figlia si ritrova con la sua voce e quella in cima non cambia.
- `FluidPageFractionTest` (2): `fractionToward` a riposo, in movimento (mai fuori da 0..1) e fermo fra pagine dello stesso lato. `FluidNavHostPageColorTest` (4): `pageColor` di serie, colorato, trasparente, `Unspecified`.
- Collegati ma di altre schede: `ExternalGroupDriveTest` e `FluidChromeTransitionTest` (la superficie Fluid-physics guidata da fuori e il fondale di transizione della chrome).
- **Non provati**: gli scorrimenti annidati oltre quelli in `FluidPageHostTest`, due host affiancati, `Expand` a livello di host (la sua geometria e' coperta dai test puri), e tutto cio' che e' fotogramma (si guarda in mano, sul dispositivo).

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/page/`) | cosa c'e' dentro |
|---|---|
| `FluidPageInput.kt` | `fluidPageBackSwipe` (riconoscitore dell'host), `FluidPageNestedScroll`, `FluidPageBackHandler` (back di sistema, annullo dedotto, ripresa), `displayCornerRadii` |
| `FluidPageBar.kt` | `FluidPageBarSpec`, `FluidPageBarSlot`/`FluidPageBarRegistration` (il registro), `FluidBarPieceSink`/`FluidBarPiece` (i pezzi di vetro) |
| `FluidPageAnchoredBar.kt` | `FluidAnchoredBar` (vetro, titoli, tasto indietro, azioni, viaggio dei pezzi con `FluidBarJourney`) e `FluidPageChrome` |
| `FluidPageMotion.kt` | i token e le funzioni pure |
| `FluidPageHostState.kt` | `beginDrag`, `dragTo`, `release`, `confirmThroughDispatcher`, `systemCommit`, `commit`, `requestBack` |

Il resto della famiglia (host, `FluidNavHost`, migrazione, vecchio): [pagine.md](pagine.md).
