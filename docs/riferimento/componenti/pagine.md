<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/pagine.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Pagine e navigazione (3.0): host, `FluidNavHost`, migrazione

Questa famiglia muove le pagine dell'app: la pila di schermate che si apre da destra, si chiude col dito dal bordo e fa
scorrere la barra senza portarsela dietro. Sta in `ui/page/` (l'host, senza dipendenze da navigation) e in `ui/navigation/`
(`FluidNavHost`, il `NavController` di sempre con le pagine Fluid). Si lega alle barre (`FluidScreen` registra la sua barra
nell'host, vedi `pagine-back-e-barra.md`), al vetro (la barra rifrange le due pagine insieme), alla tab bar (`FluidPageHostState.fractionToward`)
e ai modali (un `BackHandler` aperto dopo l'host passa davanti al back).

**Modello mentale.** Una pila di chiavi diventa **due strati opachi** (la pagina in cima e quella sotto) e **un orologio solo**,
`travel`, i pixel che la pagina sopra ha percorso verso l'uscita: ogni cosa che si vede e' una funzione di `travel`, quindi
il dito, la molla e il tasto indietro disegnano lo stesso fotogramma. Le pagine **non sfumano mai** e, col dito, **stanno sotto il dito**
pixel per pixel (mai sul `progress` del gesto di sistema).

Questa scheda: host, navigazione, stili, stato, migrazione e cosa sostituire del vecchio. Il back col dito, il back predittivo di
sistema, `FluidPageInput`, la barra ancorata, i token numerici e i test stanno in [pagine-back-e-barra.md](pagine-back-e-barra.md).
Firme complete: [../api/pagine.md](../api/pagine.md). Il perche' storico: [../regole.md](../regole.md) («Le pagine non sfumano e seguono il dito»,
«Le pagine: una barra per host») e [docs/riferimenti-3.0.md](../../riferimenti-3.0.md) sezione 2 (come fa LibrePods, da cui l'host e' portato, GPL-3.0).

## Aprire una pagina da un indirizzo (F8.12)

L'app dichiara una riga `FluidAddressPage` per ogni tipo concreto. La riga di una nota può
leggere un id intero, scriverlo e restituire il quaderno genitore. La tabella codifica in UTF-8
ogni segmento e ogni parametro della query. L'app valida i parametri che ammette. I nomi delle
pagine sono unici. Il genitore deve essere calcolabile anche per una risorsa cancellata o chiusa.
Per un identificatore senza genitore noto si può tornare alla home.
Una catena può contenere fino a 64 pagine. Un ciclo o una gerarchia oltre questo limite rifiuta l'ingresso.

```kotlin
val addresses = FluidAddressNavigation(table, ::routeOf, missingRoute = "mancante", signInRoute = "accesso")
FluidNavHost(nav, "home", addresses, initialRequest) {
  // Le destinazioni Fluid dell'app, comprese le due pagine di servizio.
}
```

Per ricevere il link, una Activity dedicata con filtro VIEW/BROWSABLE e modalità singleTop
chiama `takeInitialFluidAddress` in onCreate prima di installare il grafo. Questo metodo consuma
l'intero intent di lancio. In onNewIntent chiama `newFluidAddress` dentro il callback e consegna
la richiesta ad `addresses.open(nav, request)`. Il sample contiene il collegamento completo,
compreso l'arrivo di un intent prima che la composizione sia pronta.
Le rotte della tabella possono essere stringhe o oggetti serializzabili di Navigation. Chi ha già
un grafo tipizzato lo passa all'overload con `graph`, che mantiene anche le opzioni di movimento fra schede.

L'ingresso sostituisce la pila con il percorso naturale. A freddo l'host riceve la pila prima
del primo disegno. A caldo la nuova cima entra con il movimento Fluid. Indietro percorre i
genitori e dalla radice torna al sistema. La ricreazione riparte dall'ultima voce salvata.
Se manca la risorsa, l'app mostra la propria pagina di avviso sopra i genitori disponibili.
Per un accesso richiesto, l'engine conserva la richiesta sulla voce di login. Dopo l'accesso reale
l'app chiama `addresses.resumeAfterSignIn(nav)`. L'engine rivaluta visibilità ed esistenza.
L'app mantiene i consueti controlli di sessione anche quando mostra dati già aperti.
Per gli appunti o la condivisione la pagina usa `table.addressOf(pagina)` con i dati della propria voce.

Ogni ingresso esterno richiede che la pagina e i suoi genitori siano pubblici. Per le app Pampa
si passano i nomi esatti ammessi. Su Android 15 e successivi la chiamante può condividere la
propria identità con `ActivityOptions.setShareIdentityEnabled(true)`. L'engine verifica la firma
con PackageManager per il chiamante di quella consegna. Un mittente anonimo riceve il trattamento
pubblico. Sulle versioni precedenti, per un collegamento privato fra app si può usare
`fluidAddressFromCall` dentro ContentProvider.call e inoltrare il risultato alla UI sul canale
interno dell'app. Il nome del pacchetto fornito da Android e la firma devono coincidere con quelli attesi.
Per i futuri App Links, `httpsHost` accetta un solo dominio nella forma https://dominio/nome/id.
Quando ci sarà il dominio, l'app aggiungerà il filtro autoVerify e l'associazione Digital Asset Links.

Il riferimento Apple per consegna e ricostruzione è documentato in [onOpenURL](https://developer.apple.com/documentation/swiftui/view/onopenurl%28perform%3A%29) e [NavigationPath](https://developer.apple.com/documentation/swiftui/navigationpath).
Il mittente per singolo intent è descritto da Android in [ComponentCaller](https://developer.android.com/reference/android/app/ComponentCaller).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| le pagine di un'app che ha un `NavController` (deep link, `ViewModel` per voce, `navigation { }`, `dialog { }`) | `FluidNavHost` + `composable(...)` di `dev.antigravity.fluidengine.ui.navigation` |
| migrare un `NavHost` esistente | cambiare `NavHost(` in `FluidNavHost(` e l'import di `composable`; togliere transizioni e `FluidRouteMotionHost` (ricetta sotto) |
| costruire il grafo a mano (`NavGraph` gia' pronto) | `rememberFluidNavController()` + overload `FluidNavHost(navController, graph)` |
| passare da una scheda a un'altra tenendo una pila sola | `navController.navigateToPeer(route)` + `peer = FluidPeerMove { from, to -> … }` su `FluidNavHost` |
| una pila di chiavi mia, senza navigation | `FluidPageHost(stack, onBack) { key -> … }` |
| una pagina che «si chiude da sola» (si rimpicciolisce e scivola via) | `composable(route, transition = FluidPageTransition.ShrinkSlide)` |
| una pagina che si apre dal punto toccato | `transition = FluidPageTransition.Expand` |
| una card (o un elemento qualunque) che **diventa la pagina** e ci torna, anche col dito (lo zoom di iOS) | `Modifier.fluidTransitionSource(id, ns)` sulla card + `composable(route, transition = Expand, zoomSource = { entry -> FluidTransitionSource(id, ns) })` (4.0, sezione «Lo zoom dalla sorgente») |
| un mini lettore, una miniatura, una pillola o un tasto della barra che **diventa la pagina** (Musica, Foto, Meteo, Mail) | un preset: `composable(route, zoomSource = …, zoom = FluidZoomStyle.Rise)` + `Modifier.fluidZoomSource(id, ns, FluidZoomStyle.Rise) { … }` (F6B.5c, sezione «Un elemento che diventa pagina») |
| più pagine affiancate con un segmentato sopra che le segue col dito (Voti / Note / Assenze) | `FluidSegmentedPager(segments) { index, contentPadding -> … }` (F8.4) |
| la pagina che si tira indietro da qualunque punto | `backSwipe = FluidBackSwipe.Anywhere` (di serie e' `EdgeOnly`) |
| una pagina senza gesto indietro col dito | `backSwipe = FluidBackSwipe.Off` (il back di sistema e il tasto restano) |
| una conferma «uscire senza salvare?» sul back | un `BackHandler` nella pagina: non serve spegnere il gesto |
| la tab bar che sparisce quando si apre una pagina di dettaglio | `FluidPageHostState.fractionToward { key -> … }` con lo `state` passato all'host |
| sapere se una pagina e' davanti / coperta | `FluidPageScope.isFront` / `isCovered` (ricevitore del contenuto) |
| la barra in una pagina che non e' una `FluidScreen` | `FluidPageChrome(title, onBack, …)` |
| NON cosi': dissolvenze, scale o sfocature fra pagine | non esistono piu': nessuna pagina sfuma, ne' sfoca quella sotto, ne' ha uno scrim |
| NON cosi': `NavHost` di navigation-compose con `enterTransition`/`popExitTransition` | `FluidNavHost` (il back di `NavHost` corre 2,5 volte il dito) |
| NON cosi': `FluidRouteMotionHost` attorno alle destinazioni | togliere l'involucro (vedi «Il vecchio, e cosa lo sostituisce») |

## Tre regole trasversali

1. **Opaca dal primo all'ultimo fotogramma.** Nessuna pagina sfuma, nessuna sfoca quella sotto, nessuno scrim. Due pagine leggibili
   una attraverso l'altra, anche per 100 ms, fanno sembrare l'app un prototipo, e una sfocatura a tutto schermo e' il fotogramma piu' caro
   proprio quando ce n'e' meno budget. L'unica trasparenza e' quella del passo fra schede (la pagina che esce sfuma in 150 ms; quella
   che arriva non sfuma mai). Il layer della pagina non usa mai `alpha` ne' `RenderEffect` per le pagine vere.
2. **Sotto il dito.** Il back col dito segue `touchX`, la posizione vera del dito; il `progress` del sistema serve solo a dedurre un annullo. Il perche': `NavHost`
   cerca la transizione sul `progress` (smussato dal sistema con una sua molla e scalato su una sua distanza) passato per un easing;
   con `EaseEmphasized` a un quinto del gesto (p = 0,20) la pagina e' gia' a mezzo schermo, **due volte e mezzo il dito**. Una pagina che corre
   davanti al dito non e' fluida, e' scollegata. Dettagli in [pagine-back-e-barra.md](pagine-back-e-barra.md).
3. **Una barra per host.** La pagina *descrive* la sua barra, l'host ne disegna una sola, ferma sopra le pagine che si muovono.
   Una pagina che si disegna da sola una barra sopra il contenuto fa vedere due barre, e la sua viaggerebbe col dito. Vedi `pagine-back-e-barra.md`.
   **Dalla 4.0 (F4A.3), con la barra dichiarata** (`FluidScreen(toolbar = { … })`, anche `FluidPageChrome(toolbar = …)`) i tasti
   non sono più «ancorati alla pagina»: la barra dell'host è **una sola, disegnata dal modello** della cima della pila
   richiesta, e fra due pagine i tasti **restano al loro posto e si trasformano** in quelli della pagina d'arrivo (stesso `id`,
   stesso vetro), sul tempo del piano flottante; il gesto indietro li cambia solo al rilascio che conferma. Vedi
   `pagine-back-e-barra.md`, «La barra disegnata dal modello», e [toolbar.md](toolbar.md).
   **La barra che si ritira scorrendo** (`FluidScreen(barMinimize = …)`, F4A.4) si ritira anche dentro un host (giro di
   correzione di F4A): il titolo e le azioni di sempre di una pagina seguono la corsa della loro pagina; l'indietro, la barra
   disegnata dal modello e la riga che riporta in cima (che sono una per host) seguono a riposo la pagina sopra e, mentre
   due pagine si muovono, la corsa di ognuna pesata come lo sfondo della barra: fra una pagina con la barra ritirata e una
   aperta i tasti tornano con la pagina, non in un fotogramma. A barra ritirata la riga non prende tocchi: il campo fissato
   che sale fin lì si tocca.

## FluidNavHost, rememberFluidNavController, composable

**Cos'e'.** `NavHost` con le pagine Fluid: il `NavController` resta quello di navigation (deep link, stato salvato, `ViewModel` per voce,
`savedStateHandle`, `navigation { }` annidati, `dialog { }`); cambia solo chi muove le pagine. `FluidPageNavigator` (nome `"fluidPage"`) fa per l'host quello
che `ComposeNavigator` fa per `NavHost`, usando solo API pubbliche di `NavigatorState`.

**Firma essenziale.** Tre overload (stringa, rotte tipizzate, grafo gia' costruito); tutti i parametri: [../api/pagine.md](../api/pagine.md).

```kotlin
@Composable fun FluidNavHost(
  navController: NavHostController,
  startDestination: String,              // oppure Any (rotte tipizzate) o `graph: NavGraph`
  modifier: Modifier = Modifier,
  route: String? = null,
  peer: FluidPeerMove<NavBackStackEntry>? = null,   // l'ordine delle schede, se la pila ne contiene
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  builder: NavGraphBuilder.() -> Unit,
)

fun NavGraphBuilder.composable(
  route: String, arguments: List<NamedNavArgument> = emptyList(), deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
)
```

Ci sono anche `composable(route: KClass<T>, typeMap, …)` e `composable<T>(…)` con `reified` per le rotte `@Serializable`.
`rememberFluidNavController(vararg navigators)` e' un `rememberNavController` col navigator delle pagine gia' dentro.

**Uso** (dal `sample`, `PagesDemo.kt`):

```kotlin
val navController = rememberFluidNavController()
FluidNavHost(navController = navController, startDestination = "elenco", modifier = Modifier.fillMaxSize()) {
  composable("elenco") {
    FluidScreen(title = "Elenco") {
      item { FluidListRow(title = "Apri dettaglio", onClick = { navController.navigate("dettaglio") }) }
    }
  }
  composable("dettaglio", transition = FluidPageTransition.ShrinkSlide) {
    FluidScreen(title = "Dettaglio", onBack = { navController.popBackStack() }) { /* … */ }
  }
}
```

**Comportamento.**
- **Il `composable` e' quello dell'engine.** Stesso nome di `androidx.navigation.compose.composable` di proposito: migrare un grafo vuol dire cambiare un import
  (e le etichette `this@composable` compilano). Le destinazioni non portano animazioni: portano uno stile (`transition`) e un gesto (`backSwipe`).
  Il contenuto riceve la `NavBackStackEntry` e ha come ricevitore un `FluidPageScope`.
- **Rete di sicurezza della migrazione.** Se nel grafo resta un `composable` di `androidx.navigation.compose`, quella destinazione non si disegnerebbe (il suo contenuto e' interno a navigation-compose
  e nessuno lo disegna). `FluidNavHost` lo rileva: in **debug** (`FLAG_DEBUGGABLE`) l'app si ferma con un errore che dice quali rotte, in release scrive solo un log `FluidNavHost`.
- **Serve un `ViewModelStoreOwner`.** `FluidNavHost` lo legge da `LocalViewModelStoreOwner` e ha un `checkNotNull`: fuori da un'`Activity`/`ComponentActivity` (o in un test senza proprietario) lancia con un messaggio.
- **`dialog { }`** funziona: se il controller ha un `DialogNavigator` (di serie in `rememberNavController`) l'host monta il `DialogHost`.
- **`pageColor`.** Ogni pagina e' dipinta col fondo del tema (la pagina e' carta). Trasparente, o `Color.Unspecified`, l'host non dipinge niente: serve a un'app che ha un fondale suo dietro
  l'host (un canvas ambientale condiviso, l'artwork di un player). Il prezzo e' nelle transizioni: le pagine non sono piu' due strati opachi e, mentre una scivola sull'altra, si vedono per trasparenza.
  Va bene dove ogni pagina disegna da se' il proprio fondo o dove il fondale e' lo stesso per tutte (3.0.1).
- **Il colore del testo viene dal fondo.** Con un `pageColor` dipinto l'host fornisce `LocalContentColor` dal fondo: un testo senza colore esplicito non esce nero sul nero (3.0.1).
- **Stato salvato.** La chiave con cui una pagina sta in composizione e' l'`id` della voce, non la voce: l'hash di una `NavBackStackEntry` dipende da oggetti nuovi a ogni ricreazione dell'Activity, quindi con la voce come
  chiave ogni `rememberSaveable` (un campo di testo, il passo di un onboarding) si perdeva a un cambio di tema o a una rotazione. E' la fix della 3.0.2 (trovata da PampAI). Con `FluidNavHost` il contenuto di una pagina ha gia'
  `ViewModelStore`, ciclo di vita e stato salvabile dalla voce (`LocalOwnersProvider`).
- **Ciclo di vita.** Lo governa il `NavController`: la pagina in cima e' `RESUMED`, quella coperta `CREATED`; mentre un gesto o un movimento e' in corso le due voci restano almeno `STARTED`
  (`prepareForTransition`) e a riposo ogni voce va al suo stato finale (`onTransitionComplete`: cima `RESUMED`, sotto `CREATED`, quelle uscite distrutte con il loro `ViewModelStore`).
  Un salto di piu' pagine (`popBackStack(route, inclusive = false)` oltre la penultima) tiene viva la vecchia cima finche' non e' uscita: esce davanti agli occhi, le pagine in mezzo spariscono senza farsi vedere.
- **Segnale di rotta.** Quando la cima cambia, l'host lo comunica a chi sta fuori dalle pagine nella stessa finestra (`FluidRouteSignal.routeChanged`): la barra flottante si riapre e si chiude il fermo dello scorrimento di una lista che non c'e' piu' (3.0.1).
- **Dipendenze.** `engine-ui` espone `navigation-compose` 2.9.8 come `api` (la versione che le app hanno gia'; una che aveva la 2.8.5 passa alla 2.9.8).

**Trappole.**
- *Import dimenticato*: `androidx.navigation.compose.composable` nel grafo. Sintomo: in debug l'app si ferma all'avvio con «queste destinazioni sono di androidx.navigation.compose.composable»; in release la pagina e' vuota.
- *`FluidRouteMotionHost` attorno alla destinazione*: non compila (vuole un `AnimatedVisibilityScope` che qui non c'e'). Va tolto.
- *Un `NavController` creato con `rememberNavController()` e un grafo costruito a mano*: i builder `composable(...)` cercano il navigator nel provider *mentre il grafo si costruisce*. Per `FluidNavHost(navController, graph)` serve `rememberFluidNavController()`;
  gli overload con `startDestination` lo aggiungono da soli a qualunque controller.
- *`composable` con le quattro transizioni di `NavHost`* (`enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition`): non esistono. C'e' lo stile della pagina.
- *La tubatura dell'origine del tocco in `savedStateHandle`* (per la crescita dal punto toccato): non serve piu'. `FluidPageTransition.Expand` legge il punto toccato da solo (il riconoscitore dell'host lo registra a ogni contatto).

**Non usarlo quando.** La pila non e' di navigation (una pila tua di stato, un wizard): `FluidPageHost`. Una sola schermata senza storia: nessun host.

**Versione.** Dalla 3.0.0. 3.0.1: `pageColor`, colore del testo, `navigateToPeer`, `fractionToward`. 3.0.2: stato salvato per `id` della voce. 3.0.3: `EdgeOnly` di serie.

## Migrare un'app da `NavHost` a `FluidNavHost`

1. `NavHost(` diventa `FluidNavHost(`, con lo stesso `navController`, `startDestination`, `route` e grafo. `rememberNavController()` resta; per un grafo costruito a mano con `NavGraph` serve `rememberFluidNavController()`.
2. L'import di `composable` diventa `dev.antigravity.fluidengine.ui.navigation.composable`. Cercare e togliere ogni `import androidx.navigation.compose.composable`.
3. Via le quattro transizioni e i loro helper. Al loro posto: `composable(route, transition = …, backSwipe = …)`. `FluidPageTransition.Expand` dove si voleva tenere la crescita dal tocco; via la tubatura dell'origine del tocco nel `savedStateHandle`.
4. Via i `FluidRouteMotionHost` attorno alle destinazioni.
5. `peer = FluidPeerMove { from, to -> … }` dalla funzione che gia' ordina le schede (positivo se `to` sta dopo `from`, zero se non sono pari).
6. Il tocco su una scheda diventa `navController.navigateToPeer(route)` (sotto). Le due ricette scritte a mano si rompono: `popUpTo(startDestination)` accumula le schede appena la partenza non e' piu' nella pila (A, B, C, A, B… e il back le ripercorre tutte);
   `popUpTo(graph.id) { saveState = true }` nelle migrazioni alla 3.0 non ha mai riportato lo stato salvato di una scheda.
7. Una shell con una tab bar che cambia visibilita' fra due pagine legge `FluidPageHostState.fractionToward`; si passa il proprio `state` a `FluidNavHost`.
8. **Da guardare in mano**: pagine opache, angoli del telefono, la pagina sotto che arretra di un terzo, il back che non corre piu' davanti al dito, la barra ferma; le pagine con un `BackHandler` (editor che chiede conferma);
   le pagine piene di gesti propri (mappe, griglie in selezione, pager) non devono piu' chiedere `EdgeOnly`, che e' di serie.

Cambia a vista in ogni app che migra: le transizioni che ognuna scriveva (scala, espansione, sfocatura della pagina che esce, angoli da 30 dp) lasciano il posto allo scorrimento da destra con la pagina sotto che arretra di un terzo.

## navigateToPeer: una scheda alla volta, una pila sola

**Cos'e'.** `NavHostController.navigateToPeer(route)` (e la variante con rotta tipizzata `@Serializable`) passa a una scheda tenendo **una pila sola**: dopo il passo la pila e' `[route]`, e un back da una scheda esce dall'app invece di attraversare le schede viste prima.
Tre app avevano scritto la stessa cosa a mano e le due ricette che giravano erano sbagliate in due modi diversi (vedi punto 6 della migrazione).

**Uso.**

```kotlin
val tabs = listOf("home", "cerca", "impostazioni")
FluidNavHost(
  navController = navController, startDestination = "home",
  peer = FluidPeerMove { from, to ->
    val i = tabs.indexOf(from.destination.route); val j = tabs.indexOf(to.destination.route)
    if (i < 0 || j < 0) 0 else j - i      // zero: non sono pari, valgono le regole delle pagine
  },
) { /* composable(...) per ogni scheda e per le pagine figlie */ }

// Il tocco su una scheda:
navController.navigateToPeer("cerca")
```

**Comportamento** (dal sorgente):
- se `route` e' gia' in cima non succede niente: il secondo tocco su una scheda e' dell'app (per esempio tornare in cima alla lista);
- se e' nella pila sotto una pagina figlia ci si torna **con la stessa voce** (stesso stato) e si chiudono le figlie, come il tocco sulla scheda di iOS;
- altrimenti la pila si svuota fino al grafo compreso (`popUpTo(graph.id) { inclusive = true }`) e si apre `route` con `launchSingleTop`.
- Il `builder` aggiunge opzioni al passo (un'animazione di navigation non conta: le pagine le muove l'host).
- **Non salva lo stato delle schede lasciate**: una scheda riaperta riparte, come riparte la sua lista. Chi vuole tenere lo stato di ogni scheda tiene **un `FluidNavHost` per scheda**, non una pila sola.
- L'host vede la cima cambiare: con `peer` e' il passo fra schede, senza `peer` e' un push.

**Non usarlo quando.** Servono schede con stato conservato (un host per scheda). La destinazione non e' una scheda ma una pagina figlia (`navigate`).

**Versione.** Dalla 3.0.1.

## FluidPageHost e FluidPageScope

**Cos'e'.** L'host senza navigation: riceve la pila obiettivo, ne fa il confronto con quella di prima e muove le pagine. `FluidNavHost` lo usa (internamente con le sue chiavi e proprietari).

**Firma essenziale.**

```kotlin
@Composable fun <K : Any> FluidPageHost(
  stack: List<K>,                                   // dalla radice alla cima; le chiavi devono restare uguali (equals)
  onBack: () -> Unit,                               // un gesto confermato la chiama PRIMA che la pagina finisca di uscire
  modifier: Modifier = Modifier,
  transition: (K) -> FluidPageTransition = { FluidPageTransition.Slide },
  backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly },
  peer: FluidPeerMove<K>? = null,
  onTransitionStart: (List<K>) -> Unit = {},        // le due pagine che il dito comincia a separare (cima, sotto)
  onSettled: () -> Unit = {},                       // l'host e' tornato a riposo
  leaving: Set<K>? = null,                          // chiavi uscite dalla pila ma ancora disegnabili (null: tutte)
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  zoomSource: (K) -> FluidTransitionSource? = { null },  // 4.0: la sorgente dello zoom di una pagina Expand
  content: @Composable FluidPageScope.(K) -> Unit,
)
```

**Uso.**

```kotlin
var stack by remember { mutableStateOf(listOf("elenco")) }
FluidPageHost(stack = stack, onBack = { stack = stack.dropLast(1) }) { key ->
  when (key) {
    "elenco" -> FluidScreen(title = "Elenco") { /* … apre: stack = stack + "dettaglio" */ }
    else -> FluidScreen(title = "Dettaglio", onBack = { stack = stack.dropLast(1) }) { /* … */ }
  }
}
```

**Comportamento.**
- **Il diff della pila** (`FluidPageMotion.classifyStackChange`): cima uguale (o una pila vuota) = niente; cima nuova con la vecchia ancora nella pila = push; la nuova cima c'era gia' sotto = pop (anche di piu' pagine: si muove solo la cima, le altre spariscono);
  cima nuova e vecchia non piu' presente = «Replace», trattata come push; le due cime **pari** secondo `peer` = passo fra schede. **I pari vincono su tutto**: `[home] → [home, cerca]` e' strutturalmente un push ma e' un passo laterale.
- **Due strati.** La pagina sotto resta composta ma non disegnata mentre e' coperta, esce dall'albero semantico (`clearAndSetSemantics`, quindi TalkBack non la legge) e ha il ciclo di vita fermo a `CREATED`
  (con `FluidNavHost` lo fa il `NavController`; con `FluidPageHost` un proprietario del ciclo di vita con tetto). Resta composta perche' torna subito sotto il dito.
- **`onBack`** si chiama quando un gesto e' confermato (o col back di sistema senza gesto: 3 tasti, Android 13 e prima, tastiera) *prima* che la pagina finisca di uscire: l'app accorcia la pila e la molla continua. Se l'app **non** la accorcia
  (un «salvare le modifiche?») la pagina, dopo due fotogrammi, torna dov'era con la molla del ritorno.
  **Non viene chiamato se una pagina ha un `BackHandler` attivo**: il back confermato passa dal dispatcher, risponde il gestore abilitato registrato per ultimo (dettagli in `pagine-back-e-barra.md`).
- **Input bloccato.** Durante una conferma (o un pop) la pagina che esce non prende tocchi; durante un push o un passo fra schede non li prende la pagina coperta (`pageInputGate`).
- **Chi e' davanti.** `LocalFluidRouteFront` per ogni pagina passa di mano a meta' uscita (`FluidPageMotion.FrontHandoff` = 0,5), in AND con il davanti di chi contiene l'host: serve al vetro e alla chrome per rifrangere la pagina giusta.
  Mentre due pagine si muovono l'host «timbra» (`markMoving`) e il vetro scende alla qualita' di uno scorrimento veloce per quei fotogrammi (due pagine esistono insieme: due fondali, due serie di pannelli).
- **La chiave in composizione** (`FluidPageHost` usa la chiave stessa): deve avere un `hashCode()` che sopravvive alla ricreazione dell'Activity, perche' la chiave di ogni `rememberSaveable` dentro la pagina deriva dall'hash di composizione. E per salvare lo stato la chiave deve essere salvabile
  (`canBeSaved` del registro: stringhe, numeri, `Parcelable`, `Serializable`): una chiave che non lo e' compone la pagina **senza** stato salvato, in silenzio.
- **Host largo zero** (un pannello fuori scena) o prima pila: niente da animare, si va subito a riposo.
- **Dispatcher.** Se l'host non e' dentro un'`Activity` che da' un `NavigationEventDispatcherOwner`, il back di sistema non e' registrato e il gesto confermato col dito toglie la pagina direttamente.

**Trappole.**
- Una chiave nuova a ogni composizione (un `data class` con un campo che cambia, un oggetto senza `equals`): ogni composizione e' un push o un replace.
- Una pila che l'app non accorcia dopo `onBack`: la pagina torna indietro da sola dopo due fotogrammi (voluto, e' la conferma di uscita).
- Leggere `state.progress` in composizione ricompone a ogni fotogramma del movimento.

**Non usarlo quando.** Hai un `NavController`: `FluidNavHost`.

### FluidPageScope

`isCovered` (questa pagina e' la sotto, a riposo, composta ma non disegnata), `isFront` (davanti adesso, passa di mano a meta' uscita), `transition` (lo `FluidPageHostState`). `LocalFluidPageTransition` e' l'host piu' vicino, `null` fuori da un host:
`FluidScreen` lo usa per non misurare le ancore del titolo mentre le pagine si muovono (`isMoving || !routeFront`).

## FluidPageHostState

**Cos'e'.** Lo stato di un host: cosa si sta muovendo, quanto, e se c'e' un dito. **Si legge, non si scrive** (lo pilotano la pila, il dito e il back di sistema). Si crea con `rememberFluidPageHostState()` (uno per host) e si passa a `FluidNavHost`/`FluidPageHost` per leggerlo da fuori
(le shell: la tab bar che cambia visibilita' fra due pagine).

**Il primo posizionamento.** Nel push la molla parte al cambio di pila. Il primo posizionamento identifica la pagina
effettivamente comparsa. Se arriva un pop prima di allora, l'host conserva la pagina visibile. Nel caso
`[R,A] → [R,A,B] → [R]` esce A. Con movimento ridotto le pagine si incrociano sul posto. Durante il push l'host tiene
la pagina di partenza opaca e avvia la dissolvenza quando può disegnare quella di arrivo.

**Preparare la destinazione.** Se la pagina conosce già il prossimo dettaglio, può passare la sua chiave a
`FluidPageHost(precomposePage = dettaglio, …)`. Dopo un fotogramma a riposo l'host compone e misura la
destinazione con i vincoli reali. Le righe visibili di una lista lazy hanno così già la propria composizione
quando arriva il tocco. La pagina resta senza posizionamento, con ciclo di vita `CREATED`. Se la pila apre
quella chiave, la stessa istanza viene posata. Una previsione sostituita viene eliminata. Gli effetti di
composizione possono eseguire lavoro già in preparazione: legare le azioni che richiedono l'apertura al ciclo
di vita o a `isFront`. Le schede, Expand e il movimento ridotto seguono il loro percorso dedicato.
Con una destinazione sconosciuta l'host compone il push ordinario da fermo nella composizione che riceve la pila.

**Campi.**

| campo | significato |
|---|---|
| `kind: FluidPageMove` | `None` (riposo), `Push`, `Pop` (la pagina se ne va da sola: tasto indietro, 3 tasti), `Back` (il dito la tiene o l'ha appena lasciata, molla compresa), `Peer` |
| `isInteractive` | il dito tiene la pagina (in-app o col gesto di sistema) |
| `from` / `to` | la pagina che lascia il davanti e quella che lo prende (la chiave della pila; in `FluidNavHost` la `NavBackStackEntry`), `null` a riposo |
| `progress` | 0 all'inizio, 1 alla fine, 0 a riposo. Push: `1 − uscita`; Pop/Back: la frazione d'uscita; Peer: il passo con la curva. Un back annullato torna verso 0 |
| `velocity` | px/s della pagina sopra lungo l'uscita, positiva = verso fuori, mentre una molla la muove; al primo fotogramma dopo il rilascio e' quella del dito |
| `isMoving` | qualcosa si muove (un back annullato che torna compreso) |

**`fractionToward(isHidden)`.** Il conto della tab bar che se ne va con la pagina di dettaglio: 0 su una pagina dove una cosa c'e', 1 su una dove non c'e', in mezzo al passo di `progress` (un back a dito compreso, che puo' tornare indietro).
`isHidden` riceve la chiave della pagina (in `FluidNavHost` la `NavBackStackEntry`) **o `null` prima della prima pila**. A riposo e' 0/1 secondo la pagina in cima; fra due pagine dello stesso lato resta ferma. Prima della 3.0.1 ogni app lo ricalcolava da `from`, `to` e `progress`
e ognuna sbagliava un caso (il riposo, il back annullato, due pagine che la nascondono tutte e due).

```kotlin
val hidden: (Any?) -> Boolean = { (it as? NavBackStackEntry)?.destination?.route == "dettaglio" }
Modifier.graphicsLayer { translationY = pages.fractionToward(hidden) * size.height }   // in disegno, mai in composizione
```

**Trappole.** `progress`, `velocity`, `fractionToward` leggono lo stato del movimento: chiamati in composizione ricompongono a ogni fotogramma. Leggerli in disegno o in layout (`graphicsLayer { }`, `offset { }`).
Un host, uno stato: condividere lo stesso `FluidPageHostState` fra due host li confonde.

**Versione.** `fractionToward` dalla 3.0.1.

## Stili: FluidPageTransition

Come una pagina entra e, soprattutto, come se ne va col dito. **Tutte e tre sono opache.**

| stile | entra / esce | la pagina sotto |
|---|---|---|
| `Slide` (di serie) | da destra (da sinistra in RTL); la pagina sotto arretra di un terzo | arretra di `W/3 · (1 − uscita)` |
| `ShrinkSlide` | prima rimpicciolisce sul posto fino al 90% nel primo 30% della corsa (smoothstep), poi scivola via; uscita a 1,25 larghezze (rimpicciolita al 90% e spostata di 0,95 W ha il bordo sinistro sul bordo destro dello schermo) | ferma e piena |
| `Expand` | si apre dal punto toccato (scala 0,91 → 1, opaca); torna indietro come `ShrinkSlide`. **Con una sorgente è uno zoom** (4.0): nasce dalla sorgente e ci torna, vedi sotto | ferma |

`ShrinkSlide` e' per la pagina che dice «sono una cosa sola che si chiude», non «torno indietro di un livello». La pagina sotto sta ferma con gli stili che rimpiccioliscono perche' una pagina sopra piu' piccola dello schermo lascia scoperte due strisce (sopra e sotto),
e una pagina sotto spostata di lato le lascerebbe vuote: era lo sfondo nero della finestra in alto e in basso a destra (beta 2, sull'emulatore). Con `Slide` fra la pagina sotto e quella sopra non c'e' mai un buco (il bordo d'uscita della sotto sta sempre oltre il bordo d'ingresso della sopra).

**Interruzioni (4.0).** Con `Slide` e `ShrinkSlide` lo stato conserva posizione e velocita' quando un gesto o un pop interrompe il push, e quando un push riprende il pop della stessa voce; il riconoscitore in-app e il back di sistema conservano corsa e verso anche al primo evento della presa, compreso il bordo opposto del sistema, mantenendo i vincoli `EdgeOnly`. `Expand` completa prima l'apertura (scala e origine diverse in chiusura). Regressione JVM del primo evento verificata; conferma visiva a 120 Hz sul dispositivo pendente.

**La carta.** Mentre la pagina sopra si muove (corsa oltre mezzo pixel, o scala minore di 1) e' una **carta**: angoli del telefono (`rootWindowInsets.getRoundedCorner`, solo dove l'host tocca davvero un angolo dello schermo: un pannello di dettaglio a destra ha gli angoli a destra e retti a sinistra; un host sotto una barra di sistema opaca non li ha in alto;
sotto API 31 o su un display rettangolare restano retti e c'e' solo l'ombra) e ombra da 36 dp (nera su superficie chiara, grigia su scura). A riposo il layer non ritaglia e non proietta niente. La forma si crea una volta per geometria, non a ogni fotogramma.

**Con le animazioni ridotte** (`LocalFluidMotionPolicy.reducedMotion`) push, pop e cambio di scheda usano una dissolvenza sul posto. Il gesto interattivo segue il dito e si posa al rilascio.

**RTL.** La pagina esce a sinistra, sempre sotto il dito (`direction = −1`); il passo fra schede inverte il lato.

## Lo zoom dalla sorgente (4.0, F5B.3)

**Cos'è.** `navigationTransition(.zoom(sourceID:in:))` di SwiftUI: una card, una miniatura, un tasto **diventa la pagina**.
La pagina nasce dal riquadro e dalla forma della sorgente e cresce fino allo schermo; al pop ci rientra; col dito si
rimpicciolisce sotto il dito e torna nella sorgente, o si riapre se il dito ci ripensa. Mentre la pagina è aperta la
sorgente non si disegna («consumata») e rinasce nel fotogramma in cui la sagoma ci rientra: **la sorgente e la pagina non
si vedono mai insieme**.

```kotlin
val ns = rememberFluidGlassNamespace()                       // sopra FluidNavHost: lo usano griglia e grafo
FluidNavHost(navController, startDestination = "griglia") {
  composable("griglia") {
    FluidScreen(title = "Luoghi") {
      items(luoghi) { luogo ->
        Card(Modifier.fluidPressable(onClick = { navController.navigate("luogo/${luogo.id}") })
          .fluidTransitionSource(luogo.id, ns, ContinuousCornerShape(FluidRadius.Card))   // prima di forma e fondo
          .clip(ContinuousCornerShape(FluidRadius.Card)))
      }
    }
  }
  composable(
    "luogo/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType }),
    transition = FluidPageTransition.Expand,
    backSwipe = FluidBackSwipe.Anywhere,                     // la pagina si tira da ogni punto, come in iOS
    zoomSource = { entry -> FluidTransitionSource(entry.arguments!!.getInt("id"), ns) },
  ) { entry -> /* la pagina */ }
}
```

Con `FluidPageHost` è `zoomSource = { key -> … }`. La scena della galleria è `ZoomDemo.kt` («Zoom dalla sorgente», in
Chrome → Navigazione).

**Come si muove.**
- **Un orologio solo.** Lo zoom corre sulla stessa corsa di push, pop e gesto: la sagoma è una funzione della corsa.
  Quando una sagoma viaggia fra la sorgente e la pagina, **nei due versi** (push, pop, conferma del dito, le inversioni),
  la corsa ha **una molla sua**, `FluidPageMotion.zoom()`: quella dello zoom di UIKit misurata su WWDC24 10145 (iOS 18,
  l'apertura da una cella toccata: critica, risposta **0,355 s**, `ResponseZoomSeconds`), appena più morbida della molla
  delle pagine (0,34 s). In UIKit apertura e ritorno sono la stessa transizione. **Quel video è a metà velocità** (lo dice
  Apple nella trascrizione): ogni tempo letto sui suoi fotogrammi va diviso per due. Fino alla correzione dell'orologio
  la molla era 0,71 s, letta senza quella nota, e lo zoom andava lento il doppio; ora, per una card della griglia su un
  telefono, l'apertura si ferma verso 600 ms e il ritorno col tasto arriva sulla card verso 470 ms. `Slide`, `ShrinkSlide` e l'`Expand` senza sorgente
  restano sulla molla delle pagine, al bit. A ogni cambio di fase (push, pop, il dito che prende, il rilascio che conferma o
  annulla, un pop che interrompe il push) la sagoma **riparte da quella che si vede**: nessun salto, in nessun verso.
  Lo zoom si inverte come `Slide` (l'Expand dal punto toccato no).
- **La pagina sta intera nella sagoma**, in scala: la copre (nessun vuoto ai lati) ancorata **in alto al centro**, perché
  la parte della pagina che corrisponde alla sua card (la foto, il titolo) sta in cima. Gli angoli vanno da quelli della
  sorgente (il raggio circolare equivalente di una forma continua) a quelli del telefono. L'ombra della carta cresce con
  l'apertura: nel primo fotogramma non compare di colpo.
- **Il dito** (`FluidBackSwipe` di sempre, dal bordo o da ogni punto, e il back di sistema): la sagoma si rimpicciolisce
  attorno al punto toccato fino al 60% a una larghezza intera (`FluidZoom.FollowShrink`, a occhio) e **il punto toccato
  resta sotto il dito**. La regola del rilascio è quella delle pagine; confermato, la sagoma vola dalla sua posizione alla
  sorgente; annullato, torna intera, e un dito la riprende a metà.
- **La sorgente nascosta.** Si nasconde nel fotogramma in cui la pagina compare sulla sua sagoma (non prima: nel
  fotogramma del cambio di pila la pagina non è ancora composta) e rinasce quando la pagina esce dalla pila, a riposo.
  Il ritorno finisce quando la sagoma è **arrivata** sulla sorgente (entro 1 dp, `FluidZoom.ArrivalDp`), non alla coda
  della molla: nessun salto di forma alla fine. Il modificatore nasconde ciò che gli sta **dopo** nella catena: va messo
  prima di forma, fondo e contenuto.
- **La dissolvenza, di serie** (scelta di Alessio del 19 novembre 2026, sui video a tempo vero). Come Apple, dentro la
  sagoma la pagina e la card si dissolvono l'una nell'altra: la sorgente consumata si registra in un layer e l'host la
  disegna dentro la sagoma, larga quanto lei e in alto, sopra la pagina che sbiadisce (il fondo della pagina resta
  pieno). Quanto si vede si legge dalla **strada** della sagoma e non dal tempo (`FluidZoom.crossfade`, da 0,33 a 0,9),
  quindi vale uguale col tasto, col dito e all'apertura, e un push che si inverte in pop non salta:
  - **all'apertura** la card nasce piena nella sagoma e sbiadisce mentre la pagina compare, fra ≈30 e ≈130 ms (Apple,
    WWDC24 10145 a tempo vero: da ≈23 a ≈115 ms);
  - **al ritorno col tasto** la card compare sulla pagina a ≈85 ms, le due si vedono insieme fino a ≈180 ms, e la sagoma
    è la sola card verso 200 ms (Apple: la cella da 67 ms, sola verso 190); la card «rinasce» quando la sagoma è
    arrivata, ma è la stessa immagine e non si vede;
  - **col dito** la dissolvenza parte quando la sagoma ha fatto un terzo della strada, di solito dopo il rilascio.

  Con le animazioni ridotte non c'è zoom, quindi nemmeno dissolvenza: vale il ripiego qui sotto. Resta diverso da Apple:
  sotto la fotografia della card, che ha la proporzione della card, per qualche fotogramma si vedono le righe della pagina
  che sbiadiscono (la sagoma a metà strada è più alta della card). `FluidPageHostState.zoomCrossfade` (interno) la spegne
  per le tracce di confronto (`zoom-torna-senza-dissolvenza`).
- **Il ripiego.** Senza `zoomSource`, con le animazioni ridotte, o se la sorgente al momento non si trova (uscita dallo
  scorrimento, pagina cambiata), vale l'`Expand` di sempre: dal punto toccato in andata, «rimpicciolisce e scivola» al
  ritorno. Una sorgente sparita **a metà** di un ritorno già partito: la sagoma atterra dove l'aveva trovata.

**La barra durante lo zoom** (scelta di F5B.3). La regola di F4A vale anche qui: la barra dell'host **resta ferma** e i
suoi tasti si trasformano in quelli della pagina d'arrivo sul tempo del piano flottante, come in un push; la barra in
basso dichiarata (F4B.1) pure. Apple fa così: in UIKit la barra è del controller di navigazione e non entra nello zoom, che
porta solo il contenuto; in iOS 26 i suoi tasti di vetro si trasformano sul posto. Il **titolo grande** invece è
contenuto: quello della pagina che arriva viaggia con la sagoma, in scala, e si vede solo dentro; quello della pagina
sotto resta dov'è, coperto dove la sagoma lo copre. Lo disegna la barra, sopra le pagine, ma segue la **dissolvenza della
sua pagina** (`FluidPageHostState.zoomContentAlpha`, la stessa curva del contenuto): all'apertura compare con la pagina e
non sopra la card, al ritorno se ne va con lei e la sagoma è la sola card verso 200 ms, col tasto e col dito. Il ritaglio
è nelle coordinate del titolo, che sta sotto la barra di stato (corr-s4-zoom: prima il «Faro» restava nitido sulla card
fino all'arrivo, ≈480 ms, e il titolo della griglia sporgeva sopra la sagoma per l'altezza della barra di stato). Il vetro della barra rifrange la pagina sotto finché la sagoma non
arriva sotto la riga dei controlli (la registrazione della pagina sopra è intera e non scalata: impilata prima, la barra
avrebbe mostrato la pagina d'arrivo sopra la griglia).

**Regola 5 («mai un layer scalato sopra un vetro»).** La pagina che cresce è scalata dal suo layer, come quella di
`ShrinkSlide` e dell'`Expand` di sempre. È sicuro per il vetro **dentro** la pagina: campiona la registrazione della pagina
stessa, che sta sotto lo stesso layer, e lo scarto fra vetro e fondale si misura nello spazio della pagina
(`localPositionOf`), che la scala non tocca; si vede in scala, giusto. Il vetro **fuori** dalla pagina (la barra
dell'host, la tab bar) non è scalato: sta sopra le pagine, e per la sua rifrazione vale il punto sopra.

**Per F6B («un elemento che diventa pagina»).** La geometria è in `ui/page/FluidZoom.kt`, pura e senza tempo: `FluidZoomShape`
(riquadro e raggi), `FluidZoom.lerp` (la sagoma fra due sagome), `follow` (la sagoma sotto il dito), `content` e
`contentClip` (la pagina dentro la sagoma, e il ritaglio nelle sue coordinate), `cornersOf`/`shapeOf` (la sorgente). I
preset di F6B (mini lettore, card, tasto) danno i loro orologi e le loro sagome d'arrivo.

**Trappole.**
- Il namespace va ricordato **fuori** dal grafo (lo usano la griglia e `zoomSource`): uno nuovo a ogni composizione non
  ritrova mai la sorgente.
- `fluidTransitionSource` dopo `clip`/`background`: consumata, sparisce solo l'interno e resta la forma vuota.
- Una sorgente che a riposo è coperta da un'altra cosa (una tab bar) torna lo stesso: la sagoma atterra sotto.

## Un elemento che diventa pagina (F6B.5c)

**Cos'è.** Lo zoom dalla sorgente nei quattro casi che le app ripetono, ognuno in una riga per parte: la sorgente dice
con quale preset si apre, la rotta lo dichiara. Il movimento è tutto dell'host (lo zoom di F5B.3, il dito che apre e la
chiusura in giù di F6B.5a, l'elemento condiviso e «Cresce» di F6B.5b): l'app non scrive una molla, una soglia o un
`graphicsLayer`. I preset sono in `ui/page/FluidZoomPresets.kt`.

| preset | il caso | la sorgente | la pagina |
|---|---|---|---|
| `FluidZoomStyle.Zoom` | una card o una miniatura diventa la pagina (Foto: la griglia e il visore) | `Modifier.fluidZoomSource(id, ns, FluidZoomStyle.Zoom, forma) { apri() }`, più `fluidSharedElement` sull'immagine | l'immagine marcata con la stessa chiave viaggia sul suo riquadro |
| `FluidZoomStyle.Rise` | un accessorio in basso diventa la pagina intera (Musica: il mini lettore → «In riproduzione») | `fluidZoomSource(…, FluidZoomStyle.Rise, …)` sul **contenuto** dell'accessorio: si apre al tocco e tirandolo su | la copertina condivisa viaggia, i comandi arrivano dopo il 70% |
| `FluidZoomStyle.Grow`, `FluidZoomStyle.grow(tint, optics)` | una pillola di vetro diventa pagina o pannello (Meteo: la pillola della località) | `FluidGrowingPagePill(id, ns, backdrop, Modifier.fluidZoomOpen(FluidZoomStyle.Grow) { apri() })` | vetro per tutta la corsa, opaca all'arrivo; i comandi in `Modifier.fluidGrowingPageChrome()` si vedono già sul vetro |
| `FluidZoomStyle.fromToolbar(id)`, `FluidZoomStyle.FromToolbar` | un tasto della barra diventa la pagina (Mail: «Scrivi» → compositore) | il tasto della barra dichiarata, `button(id, …, onClick = { apri() })`: niente da marcare | una lastra di vetro nasce da sotto il tasto e cresce come in «Cresce» |

```kotlin
val ns = rememberFluidGlassNamespace()                       // sopra FluidNavHost, come per lo zoom
FluidNavHost(nav, startDestination = "libreria") {
  composable("libreria") {
    FluidScreen("Libreria", overlay = { backdrop ->
      FluidFloatingCapsule(Modifier.align(Alignment.BottomCenter), backdrop = backdrop, fillWidth = true) {
        Row(Modifier.weight(1f).fluidZoomSource("mini", ns, FluidZoomStyle.Rise, FluidCapsuleShape) {
          nav.navigate("lettore")
        }) { Box(Modifier.size(36.dp).fluidSharedElement("copertina", ns).background(copertina)); Text("Il brano") }
      }
    }) { /* … */ }
  }
  composable("lettore", zoomSource = { FluidTransitionSource("mini", ns) }, zoom = FluidZoomStyle.Rise) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).fluidSharedElement("copertina", ns).background(copertina))
  }
  composable("scrivi", zoom = FluidZoomStyle.fromToolbar("scrivi")) { Compositore() }   // il tasto «scrivi» della pagina sotto
}
```

Con `FluidPageHost` gli stessi parametri valgono per chiave:
`FluidPageHost(stack, onBack, zoom = { key -> if (key == "lettore") FluidZoomStyle.Rise else null }, zoomSource = { … })`.

**Le firme.**

```kotlin
class FluidZoomStyle {                       // non un enum: si confronta con ==, un `when` ha sempre il suo else
  val dismiss: FluidPageDismiss              // di serie per tutti: Down
  val opensByDragging: Boolean               // Rise e Grow
  companion object {
    val Zoom; val Rise; val Grow; val FromToolbar
    fun grow(tint: GlassTint? = null, optics: GlassOptics = GlassDefaults.buttonOptics()): FluidZoomStyle
    fun fromToolbar(itemId: Any): FluidZoomStyle
  }
}
@Composable fun Modifier.fluidZoomSource(id: Any, namespace: FluidGlassNamespace, style: FluidZoomStyle = Zoom,
  shape: Shape = RectangleShape, state: FluidPageHostState? = null, openByDragging: Boolean = style.opensByDragging,
  onOpen: () -> Unit): Modifier
@Composable fun Modifier.fluidZoomOpen(style: FluidZoomStyle, state: FluidPageHostState? = null,
  openByDragging: Boolean = style.opensByDragging, onOpen: () -> Unit): Modifier
fun NavGraphBuilder.composable(route: String, arguments, deepLinks, transition = Expand, backSwipe = EdgeOnly,
  zoomSource = null, zoom: FluidZoomStyle, dismiss = zoom.dismiss, content)   // anche KClass e reified
@Composable fun <K : Any> FluidPageHost(stack, onBack, zoom: (K) -> FluidZoomStyle?, modifier,
  transition = { Expand se ha un preset, altrimenti Slide }, …, zoomSource, precomposePage,
  dismiss = { zoom(it)?.dismiss ?: None }, content)
```

**Cosa fa da solo l'engine.**
- **Il viaggio d'oro.** La sorgente non si vede mentre la pagina è aperta e rinasce quando la sagoma ci rientra; la
  faccia della sorgente esce sfocata entro il 30% della corsa e la chrome d'arrivo entra dopo il 70%. Sorgente e arrivo
  non sono mai leggibili insieme.
- **Il dito.** `Rise` e `Grow` si aprono tirando su la sorgente; tutti si chiudono tirando giù la pagina, con la lista
  interna che cede il dito solo al bordo. Il dito si ferma, cambia verso e riprende ovunque, anche durante la molla.
- **«Cresce», anche dai tasti della barra.** `Grow` e i preset della barra portano il vetro fino alla posa: la lastra
  prende la misura della sagoma, lo sfondo si sfoca e si scurisce con la curva di F6B.5b, la pagina diventa opaca solo
  all'arrivo.
- **La sorgente della barra.** `fromToolbar(id)` usa il tasto `id` della barra dichiarata dalla **pagina sotto**, la
  stessa identità di `rememberFluidToolbarTransitionSource(id)` in quella pagina. Al ritorno la barra dell'host si sta
  ritrasformando nella barra della pagina sotto: se il tasto non è ancora al suo posto, la sagoma torna nel riquadro in
  cui la pagina l'aveva trovato all'apertura.
- **La rotta vera.** È un `composable` di `FluidNavHost` come gli altri: back stack, `deepLinks`, `ViewModel` e stato
  salvato della voce. Aperta da un collegamento o ripristinata (rotazione, morte del processo) la pagina è **già aperta,
  senza viaggio**; il ritorno cerca la sorgente nella pagina sotto (anche coperta) e, se non c'è, usa il ripiego.
  Perché il collegamento ricostruisca la pagina della sorgente sotto la destinazione, la sorgente e la destinazione
  stanno in un grafo annidato la cui partenza è la pagina della sorgente:
  `navigation(startDestination = "foto", route = "foto-grafo") { composable("foto") {…}; composable("foto/{n}", …) }`.
  Un collegamento con l'app aperta (`navigate(uri)`) mette la pagina sopra quella di adesso: senza la sorgente sotto,
  si apre e torna col ripiego.
- **Il ripiego senza salti.** Una sorgente uscita dallo scorrimento, una pagina ripristinata senza la sua sorgente, le
  animazioni ridotte: vale l'`Expand` di sempre, dal punto toccato e «rimpicciolisce e scivola» al ritorno, senza salti.
  Una sorgente sparita a metà ritorno: la sagoma atterra dove l'aveva trovata.

**Cosa non fa.**
- **Non marca la sorgente per l'app** fuori dalla barra: il mini lettore, la miniatura e la pillola li marca l'app (con
  `fluidZoomSource`, `FluidGrowingPagePill`); la tab bar e i suoi accessori restano dell'app.
- **Non sceglie l'immagine condivisa**: va marcata con `fluidSharedElement(chiave, ns)` nella sorgente e nella pagina.
  Senza, la pagina viaggia intera dentro la sagoma, con la dissolvenza di F5B.3.
- **Il tasto della barra resta della barra**: non si nasconde e non si stacca. La barra non entra nello zoom e si
  trasforma nella barra della pagina d'arrivo con lo scambio sfocato di sempre; la lastra di «Cresce» nasce sotto il
  tasto. Se la pagina d'arrivo tiene un tasto nello stesso posto (il compositore con «Invia» dove c'era «Scrivi»), il
  vetro resta fermo e cambia solo l'icona. A differenza del foglio che nasce dal tasto (F5B.3), qui la faccia del tasto
  non si nasconde: fra due pagine la barra cambia, e una faccia nascosta all'apertura e restituita all'arrivo sparirebbe
  e ricomparirebbe in un fotogramma (lo mostravano i fotogrammi del banco, `elemento-dalla-barra`).
- **Il collegamento non ricostruisce da solo la pagina della sorgente**: lo fa la struttura del grafo (sopra).
- **Non migra le app**: i casi di Fluidify, Gallery, Weather e Mail sono scene della galleria, rifatte con l'engine
  (`sample/…/dynamic/ElementToPage.kt`, `ElementToPageScenes.kt`). Le app le migra Alessio, a mano.

**Trappole.**
- `fluidZoomSource` va **in testa** alla catena dei modificatori, come `fluidTransitionSource`: consumata, la sorgente
  non disegna ciò che le sta dopo.
- Su `FluidGrowingPagePill` si usa `fluidZoomOpen`, non `fluidZoomSource`: la pillola si marca da sé, e due sorgenti con
  la stessa identità sullo stesso elemento si contendono la fotografia.
- Il namespace va ricordato fuori dal grafo, come per lo zoom: uno nuovo a ogni composizione non ritrova la sorgente.
- Con un preset, `transition` è di serie `Expand`: una rotta che chiede `Slide` ignora la sorgente.
- Il preset della barra cerca il tasto nella **pagina sotto**: un compositore aperto da un collegamento sopra un'altra
  pagina non lo trova e torna col ripiego.

**Versione.** Dalla 4.0 (F6B.5c). La scena della galleria è «Un elemento che diventa pagina» (`ElementToPagePage`); i
collegamenti `fluidengine://elementi/lettore`, `…/foto/{n}`, `…/luoghi`, `…/scrivi`.

## Il pager col segmentato: `FluidSegmentedPager` (F8.4)

Un pager orizzontale con sopra un segmentato flottante, sincronizzati: le schede segmentate di App Store e di Salute
in iOS 26 (ClasseViva «Voti / Note / Assenze», Fluidify «Brani / Album / Artisti»). Scena `pager-segmentato`.

**Il segmentato è la pillola della tab bar** (nota di Alessio: «non tanto come l'anteprima ma più tipo la pillola di
navigazione»): è un `FluidFloatingTabBar` a schede di sole parole, alto 48 dp, con il suo vetro, la sua goccia e la sua
fisica, non una copia. Il collegamento col pager è un aggancio interno della tab bar (spento di serie: fuori da un pager
la tab bar non cambia di un pixel):

- **il dito che trascina le pagine trascina la pillola**: la goccia si preme e diventa lente come sotto il dito sulla
  barra, segue la frazione esatta del pager con la molla rigida del trascinamento, si allunga con la velocità e tira la
  capsula di qualche dp; quando le pagine si posano si rilascia;
- **un tocco su un segmento** muove la goccia come sulla tab bar e porta il pager con l'animazione delle pagine;
- **la goccia trascinata sulla capsula** sceglie al rilascio (un tic per segmento) e il pager la segue.

```kotlin
val pager = rememberFluidSegmentedPagerState(pageCount = { 3 })
FluidSegmentedPager(segments = listOf("Voti", "Note", "Assenze"), state = pager) { index, contentPadding ->
  LazyColumn(contentPadding = contentPadding) { … }     // il contenuto parte sotto la capsula e ci scorre sotto
}
```

| | |
|---|---|
| `state.selectedPage` | la pagina su cui il pager si è fermato (`settledPage`) |
| `state.indicatorPosition` | dove sta la goccia adesso, in segmenti (1,5 = a metà fra il secondo e il terzo) |
| `state.animateScrollToPage(i)` | porta pager e pillola |
| `backdrop` | ciò che la capsula rifrange; di serie le pagine stesse |
| `beyondViewportPageCount` | le pagine vicine restano composte (di serie 1): la pillola non aspetta una pagina che si costruisce |

- Guidata dal pager, la barra **non** è la navigazione: le azioni della selezione (`selection(…)`) non la sostituiscono.
- **TalkBack**: ogni segmento è un tasto col suo titolo, e quello scelto dice «selezionato». **Movimento ridotto**: la goccia arriva senza
  viaggio né lente.
- Le misure (48 dp di altezza, segmenti fino a 112 dp, 8 dp dal bordo) sono a occhio sulle schermate di iOS 26,
  `FluidSegmentedPagerDefaults`.

## Il passo fra schede: FluidPeerMove

**Cos'e'.** `fun interface FluidPeerMove<in K> { fun direction(from: K, to: K): Int }`: l'ordine fra pari. Due destinazioni che sono schede non sono una dentro l'altra: non si aprono come pagine e non si chiudono col dito, fanno il passo laterale di sempre.
`direction` torna positivo se `to` sta dopo `from` (arriva dal lato di fine), negativo se prima, **zero se non sono pari**: allora valgono le regole delle pagine.

**Comportamento.** Il passo dura `FluidMotion.DurationPeer` (280 ms, lineare poi curvato con `EaseEmphasized`); la pagina che arriva sta sopra, opaca, e percorre il 13% della larghezza (`FluidMotion.PeerSlideFraction`); quella che esce va dall'altra parte dello stesso tanto e sfuma nei suoi 150 ms
(`DurationPeerFadeOut`, con `EaseIn`). Un dito non separa due schede pari. Il passo parte dal primo posizionamento
della scheda che arriva. Titolo grande e sottotitolo restano entro il bordo di lettura. Con movimento ridotto la
scheda arriva in dissolvenza sul posto.

## Il vecchio, e cosa lo sostituisce

Tutto in `ui/theme/RouteMotion.kt` e `ui/theme/Motion.kt`. Questa e' la tabella «cosa togliere, cosa mettere».

| vecchio | stato | cosa mettere |
|---|---|---|
| `FluidRouteMotionHost(animatedVisibilityScope, content)` attorno a una destinazione di `NavHost` | `@Deprecated`; resta per chi non migra: non sfoca piu' ne' arrotonda, tiene solo il «davanti» e il vetro che si assottiglia | `FluidNavHost`; togliere l'involucro |
| `FluidRouteMotionHost(scope: FluidPageScope, content)` (la forma che compila dentro `FluidNavHost`) | `@Deprecated`, `ReplaceWith("content()")`: non fa niente | lasciare solo `content()` |
| `FluidRouteMotion.ExpandInitialScale` (0,91) | `@Deprecated` | `FluidPageMotion.ExpandInitialScale` (e `FluidPageTransition.Expand`) |
| `FluidRouteMotion.ExpandParentScale` (1,04) | `@Deprecated` | niente: la pagina coperta non si scala, arretra di un terzo (`FluidPageMotion.belowOffset`) |
| `FluidRouteMotion.ExpandCornerRadius` (30 dp) | `@Deprecated` | niente: la pagina che si muove ha gli angoli del telefono (`pageCardRadii`) |
| `FluidRouteMotion.ExitBlur` (6 dp) | `@Deprecated` | niente: nessuna pagina si sfoca |
| `RouteMotionSignals.hierarchical` | `@Deprecated`, non fa piu' niente | toglierlo |
| `FluidMotion.DurationRouteFadeOut` (240 ms) | `@Deprecated` | niente per le pagine (non sfumano); per le schede `FluidMotion.DurationPeerFadeOut` |
| `NavHost` con `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` | di navigation-compose | `FluidNavHost` + `transition =` e `backSwipe =` su `composable(...)` |
| tubatura dell'origine del tocco in `savedStateHandle` (crescita dal punto toccato) | a carico dell'app | `transition = FluidPageTransition.Expand` |

Non sono deprecati e restano utili: `LocalFluidRouteFront` (chi e' davanti: lo forniscono l'host e `FluidPaneScaffold`; chi disegna chrome sopra le pagine lo legge), `LocalRouteMotionSignals`/`RouteMotionSignals.glassQuality`
(il vetro che scende a `FluidGlassQuality.ScrollingFloor` per 40 ms a ogni timbro di movimento; dalla 4.0 non la riga dei tasti della barra ancorata, che resta a qualità piena: [pagine-back-e-barra.md](pagine-back-e-barra.md)) e `FluidMotionScheme` in `Motion.kt`.
`FluidMotionScheme` non e' una cosa delle pagine: e' il `MotionScheme` che `FluidTheme` passa a Material 3 (`defaultSpatialSpec` = `FluidMotion.standard()`, `fastSpatialSpec` = `snappy()`, `slowSpatialSpec` = `smooth()`, `defaultEffectsSpec` = `snappy()`, `fastEffectsSpec` = `instant()`, `slowEffectsSpec` = `smooth()`),
cosi' switch, chip e fogli di Material si muovono sulle stesse molle della chrome custom; non c'e' niente da sostituire.
`FluidTouchOriginState`/`rememberFluidTouchOrigin`/`fluidTouchOriginTracker`/`MotionOrigin` (l'origine del tocco della vecchia crescita) restano pubblici e non deprecati, ma nel codice dell'engine non li legge piu' nessuno: l'host registra il punto toccato da solo.

**Trappola della migrazione.** Un grafo migrato con un `FluidRouteMotionHost(this@composable) { … }` residuo compila (e' l'overload deprecato col `FluidPageScope`) ma non fa niente; quello con l'`AnimatedVisibilityScope` non compila. Cercare `FluidRouteMotionHost` dopo la migrazione.

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/…`) | cosa c'e' dentro |
|---|---|
| `page/FluidPageHost.kt` | `FluidPageHost`, `FluidPageScope`, `PageLayer` (il layer che disegna la carta, `pageLayer`), il blocco dell'input, il ciclo di vita con tetto |
| `page/FluidPageHostState.kt` | la macchina (`Idle`, `Pushing`, `Popping`, `Peer`, `Dragging`, `Committing`, `Cancelling`), `beginPush/Pop/Peer`, `commit`, `release`, `requestBack`, `fractionToward` |
| `page/FluidPageTransition.kt` | `FluidPageTransition`, `FluidBackSwipe`, `FluidPeerMove`, `FluidPageMove` |
| `page/FluidPageMotion.kt` | costanti e funzioni pure (corsa, rilascio, frame, parallasse, classificazione della pila, geometria della barra) |
| `page/FluidZoom.kt` | la geometria dello zoom (4.0, F5B.3): `FluidZoomShape`, `FluidZoomContent`, `FluidZoom` (sagome, dito, contenuto, raggi della sorgente) |
| `page/FluidZoomPresets.kt` | i preset di «un elemento che diventa pagina» (F6B.5c): `FluidZoomStyle`, `fluidZoomSource`, `fluidZoomOpen`, le sorgenti che l'host ne ricava |
| `navigation/FluidNavHost.kt`, `FluidPageNavigator.kt`, `FluidPeerNavigation.kt` | l'host con navigation, il navigator e i builder `composable`, `navigateToPeer` |
| `theme/RouteMotion.kt`, `theme/Motion.kt` | il vecchio (tabella sopra) e `FluidMotionScheme` |
| il resto (input, barra, test) | [pagine-back-e-barra.md](pagine-back-e-barra.md) |
