# Suggerimenti al primo uso (tutorial)

Questa famiglia (`ui/tutorial/`) mostra **un callout per volta**, agganciato a un elemento, la prima volta che una persona incontra una funzione: due parole di titolo, **una frase**, e il gesto disegnato se il gesto e' la cosa da spiegare.
**Non e' un tour**: e' quello che una persona accanto direbbe indicando l'elemento. Il componente sa **disegnare** e **decidere il momento**; **cosa e' gia' stato visto lo ricorda l'app**, mai il componente (qui dentro non c'e' nessuna memoria).

**Modello mentale.** Alla radice sta un padrone di casa (`FluidTutorialHost`) con il suo stato; ogni schermata dichiara la sua chiave, **offre** i candidati non ancora visti e **marca** gli elementi con `fluidTutorialAnchor`. Una politica pura
(`FluidTutorialPolicy`) dice quando uno puo' parlare: l'ancora e' sullo schermo, la mano e' ferma da un po', nessun pannello dell'engine e' in scena, e dopo l'ultimo callout c'e' stata un'interazione.
Si lega al vetro (il callout e' un pannello di vetro con un tasto di vetro), ai modali (`modalPresenting`) e alle pagine (una chiave di schermata per rotta). Firme complete: [../api/tutorial.md](../api/tutorial.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| spiegare una funzione la prima volta che compare | `FluidTutorial(id, priority, title, text, hint)` offerto con `FluidTutorialHostState.offer` |
| montare i suggerimenti nell'app | `rememberFluidTutorialHostState()` + `LocalFluidTutorialHostState` + `FluidTutorialHost` alla radice + `fluidTutorialTouches` sul contenitore |
| indicare a quale elemento si riferisce | `Modifier.fluidTutorialAnchor("id")` sull'elemento |
| far vedere il gesto (tieni premuto, scorri, riordina) | `hint = FluidGestureHint.…` (sei gesti) |
| sapere che un suggerimento e' stato mostrato / chiuso / spento | `state.onShown(id)`, `state.onDismissed(id, optOut)` |
| non parlare mentre una schermata carica | `state.loading(true)` … `state.loading(false)` |
| ritirare un suggerimento che non serve piu' | `state.withdraw(id)` |
| mostrare un callout in una galleria o in un test, fuori dal padrone di casa | `FluidCoachMark(…, placement = FluidCoachMarkPlacement(…))` direttamente |
| provare la politica | `FluidTutorialPolicy.choose(…)` con un orologio finto |
| un tutorial a piu' passi, un tour, un overlay che oscura la pagina | non e' questo: qui un callout per volta, nessuno scrim, nessun «avanti» (vedi trappole) |
| NON cosi': un pannello modale per spiegare una cosa | un callout non e' un modale: lascia leggere e toccare la pagina |

## Regole trasversali

1. **Uno per volta, sempre.** `presenting` e' un solo valore. Dopo una chiusura il successivo **aspetta un'interazione** dell'utente: due callout di fila senza che l'utente abbia fatto niente si leggono come un tutorial obbligatorio, che e' esattamente quello che questa fase non vuole essere.
2. **Non e' un modale.** Nessuno scrim, la pagina sotto resta leggibile; **un tocco fuori chiude il callout senza consumare il tocco** (arriva lo stesso a quello che c'era sotto: un suggerimento che si mette fra il dito e l'app e' peggio del non averlo dato). Il tocco conta anche come interazione.
3. **La quiete prima di parlare.** Una mano che sta facendo qualcosa non si interrompe: 600 ms senza tocchi ne' scorrimenti.
4. **L'app ricorda.** `onShown` scatta quando il callout *entra in scena*, non quando l'utente lo ha letto: li' l'app lo segna come visto. `onDismissed(id, optOut)` dice se l'utente ha chiesto di non vederne piu' (il link che li spegne tutti).

## Montare i suggerimenti (la ricetta)

```kotlin
// Alla radice dell'app:
val tutorial = rememberFluidTutorialHostState()
LaunchedEffect(tutorial) {
  tutorial.onShown = { id -> visti += id }                         // l'app ricorda: segnato visto all'ingresso
  tutorial.onDismissed = { _, optOut -> if (optOut) suggerimentiSpenti = true }
}
CompositionLocalProvider(LocalFluidTutorialHostState provides tutorial) {
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().fluidTutorialTouches(tutorial)) { /* il contenuto dell'app, host delle pagine compreso */ }
    FluidTutorialHost(
      state = tutorial,
      labels = FluidTutorialLabels(dismiss = "Ok", next = "Avanti", optOut = "Non mostrare piu'"),
      backdrop = backdrop,                                         // una registrazione di vetro; null: pannello pieno
      modalPresenting = { modalHost.isOnScreen },                  // legge un oggetto vivo, vedi trappole
    )
  }
}

// In una schermata:
val tutorial = LocalFluidTutorialHostState.current
LaunchedEffect(tutorial) {
  tutorial?.screenChanged("impostazioni")                          // PRIMA di offrire: la chiave deve coincidere
  if ("menu" !in visti) tutorial?.offer(
    FluidTutorial(id = "menu", priority = 1, title = "Tieni premuto", text = "Premi a lungo una riga per aprire il menu.", hint = FluidGestureHint.LongPress),
    screen = "impostazioni",
  )
}
Box(Modifier.fluidTutorialAnchor("menu")) { /* l'elemento spiegato */ }
```

I tocchi si osservano dal **contenitore** e mai da sopra: un fratello a tutto schermo con un `pointerInput` si prende l'intero percorso di hit test e l'app sotto smette di rispondere al dito (successo davvero, 1.20.1 → 1.20.2). Per questo `FluidTutorialHost` non mette niente sopra la pagina, e per questo
esiste `fluidTutorialTouches`.

## FluidTutorial, FluidGestureHint, FluidTutorialLabels

**Cos'e'.** `FluidTutorial` e' un suggerimento (dalla 1.20.0).

```kotlin
data class FluidTutorial(
  val id: String,                     // chiave stabile: l'app la ricorda come «visto»; il doppione non entra due volte
  val priority: Int,                  // chi parla per primo quando piu' suggerimenti della stessa schermata sono pronti (vince la piu' alta)
  val title: String,                  // due parole
  val text: String,                   // UNA frase
  val hint: FluidGestureHint? = null, // il gesto disegnato, se il gesto e' la cosa da spiegare
  val anchorId: String = id,          // l'elemento a cui si aggancia (`fluidTutorialAnchor(anchorId)`); di solito ha il nome del suggerimento
)
```

`anchorId` diverso da `id` serve quando piu' suggerimenti puntano allo stesso elemento, o quando l'elemento ha gia' un'ancora con un altro nome (`FluidTutorialPolicyTest`: «l'ancora puo' avere un nome diverso dal suggerimento»).

**`FluidGestureHint`**: `Tap`, `LongPress`, `SwipeHorizontal`, `DragReorder`, `Scrub`, `LongPressAndTap`. «Un dito disegnato vale piu' di una frase che lo descrive»: la frase puo' dire «tieni premuto e trascina», nessuna frase lo fa vedere in mezzo secondo.

**`FluidTutorialLabels(dismiss, next, optOut)`**: le parole dei tasti, nella lingua dell'app (non passano da `FluidLabels`: le fornisce l'app). **`next` oggi non e' letto da nessun componente**: `FluidCoachMark` disegna solo `dismiss` e `optOut` (il campo c'e', la UI no). Si compila lo stesso e si passa un testo qualunque.

**Trappole.**
- Un titolo lungo o piu' frasi: il callout ha larghezza massima 320 dp; e' pensato per due parole e una frase.
- `id` non stabili (con un contatore, un timestamp): l'app non riconoscerebbe mai il «visto».
- Piu' suggerimenti sullo stesso elemento con lo stesso `anchorId` e una priorita' uguale: vince l'ordine di offerta.

## FluidTutorialPolicy: quando un suggerimento puo' comparire

**Cos'e'.** Una `data class` pura (le regole sono tutte di tempo e di stato, si prova con un orologio finto). **`data class` non e' un vezzo**: e' l'unica cosa che tiene in piedi `rememberFluidTutorialHostState` (1.30.1). Quella funzione costruisce la politica come argomento di default e ci fa un
`remember(policy)`; senza uguaglianza di valore ogni ricomposizione ne produce una diversa, il `remember` la legge come chiave nuova e **rifa' il padrone di casa da zero**: ogni suggerimento offerto finisce in un'istanza che verra' buttata e **sullo schermo non compare mai niente**. Il difetto e' invisibile e totale (le offerte partono, la coda resta vuota).

**Le condizioni, tutte insieme** (`choose` torna `null` se una manca):
- ci sono candidati; non sta caricando (`loading`); non c'e' un pannello dell'engine in scena (`modalPresenting`); non si aspetta un'interazione dopo una chiusura (`awaitingInteraction`);
- sono passati `quietMillis` (600 ms) dall'ultima interazione e dall'ultimo caricamento;
- l'**ancora e' sullo schermo**: i limiti misurati esistono e hanno larghezza e altezza positive;
- fra piu' candidati vince la priorita' piu' alta, a parita' l'ordine in cui sono stati offerti.

| costante di `FluidTutorialPolicy` | valore | cosa governa |
|---|---|---|
| `DEFAULT_QUIET_MILLIS` | 600 ms | la quiete: «il tempo in cui una mano che stava facendo qualcosa si ferma» |
| `TICK_MILLIS` | 120 ms | ogni quanto il ciclo si richiede se e' il momento, mentre qualcosa e' in attesa |
| `IDLE_TICK_MILLIS` | 400 ms | lo stesso quando non c'e' niente da mostrare (il ciclo resta ma quasi fermo) |

**Uso.** Raramente si costruisce a mano: `rememberFluidTutorialHostState(policy = FluidTutorialPolicy(quietMillis = 900))` per una quiete piu' lunga.

## FluidTutorialHostState e rememberFluidTutorialHostState

**Cos'e'.** Lo stato del padrone di casa: chi e' in coda, dove sono gli elementi, quando l'utente ha toccato per l'ultima volta, e chi e' in scena adesso. `rememberFluidTutorialHostState(policy, clock)` lo ricorda con `remember(policy)`; `clock` e' `System::currentTimeMillis`
di serie e si sostituisce con un orologio finto nei test.

**Firma essenziale.**

| membro | cosa fa |
|---|---|
| `presenting: FluidTutorial?` | il suggerimento in scena (solo lettura, uno per volta) |
| `pendingCount: Int` | quanti ne restano in coda per questa schermata: serve al ciclo per andare in letargo |
| `onShown: (String) -> Unit` | chiamato quando un suggerimento entra in scena: **l'app lo segna come visto** |
| `onDismissed: (id, optOut) -> Unit` | chiamato alla chiusura; `optOut` vero se l'utente ha chiesto di non vederne piu' |
| `screenChanged(key: String?)` | cambio di schermata: la coda della precedente non ha piu' senso, la quiete riparte |
| `offer(tutorial, screen)` | offre un candidato per la schermata corrente; ripetibile (il doppione non entra due volte) |
| `withdraw(id)` | toglie un candidato dalla coda (l'app l'ha segnato visto, o non serve piu'); se e' quello in scena lo toglie |
| `anchorBounds(id, bounds)` | i limiti dell'elemento in coordinate della radice; `null` quando esce di scena (lo chiama `fluidTutorialAnchor`) |
| `presentingAnchor: Rect?` | dove sta l'elemento del suggerimento in scena (questo e' uno stato: il callout lo segue) |
| `touchAt(position)` | un tocco sulla pagina: vale come interazione e, se cade fuori dal callout, lo chiude (lo chiama `fluidTutorialTouches`) |
| `interacted()` | un dito, uno scorrimento, un tasto: il conto della quiete riparte da qui |
| `loading(active)` | mentre una schermata carica non si parla (contatore: `true`/`false` annidati) |
| `dismiss(optOut = false)` | chiude quello in scena; dopo, il successivo aspetta un'interazione |

`LocalFluidTutorialHostState` e' il padrone di casa della schermata corrente, `null` dove i suggerimenti non sono montati (e `fluidTutorialAnchor` allora non fa niente).

**Comportamento.**
- **Le ancore stanno in una mappa normale, non in uno stato.** `onGloballyPositioned` scrive a ogni passata di layout (a ogni fotogramma, mentre una lista scorre) e una mappa di snapshot avrebbe invalidato chiunque la leggesse ogni volta: il padrone di casa, cioe' la radice dell'app. La home ne usciva a scatti.
  A servire alla composizione e' solo `presentingAnchor` (assegnare un valore uguale non ricompone niente).
- Il padrone di casa **rivaluta ogni 120 ms** (400 ms se non c'e' niente da mostrare ne' in coda): non sta nel loop della composizione.
- Scegliere un candidato lo toglie dalla coda, lo mette in `presenting`, e chiama `onShown`.
- `dismiss` imposta l'attesa dell'interazione e riparte la quiete: chiudere non basta a far parlare il successivo, serve un dito.

**Trappole.**
- **`offer` ignora in silenzio** una schermata diversa dalla corrente: se non hai chiamato `screenChanged(key)` con **la stessa stringa** prima, l'offerta e' scartata e non compare niente (`FluidTutorialPolicyTest`: «chi non e' stato offerto per questa schermata non entra»).
- **`screenChanged` svuota la coda**: chiamarlo a ogni ricomposizione cancella le offerte; va chiamato quando la schermata cambia davvero (un `LaunchedEffect` con la chiave).
- **La politica e' costruita con `remember(policy)`**: se la passi tu, deve essere una `data class` stabile (lo e'); non costruirne una nuova a ogni composizione con un valore che cambia.
- **`modalPresenting` e' letto dal ciclo avviato una volta** (`LaunchedEffect(state)`): una lambda che cattura un valore che cambia (un `Boolean` locale) legge sempre il primo; deve leggere un oggetto vivo (`{ modalHost.isOnScreen }`). *Dedotto dal sorgente, non provato da un test.*
- `onShown`, segnato all'ingresso, vuol dire «mostrato», non «letto»: se l'app vuole contare solo i letti, usi `onDismissed`.
- Un'ancora nella pagina **coperta** di un host (composta ma non disegnata): la politica chiede solo che i limiti esistano; offrire il suggerimento solo dalla pagina davanti (`FluidPageScope.isFront`, vedi [pagine.md](pagine.md)) e dichiarare una chiave di schermata per rotta. *Consiglio dedotto, da guardare in mano.*

**Versione.** 1.20.0. 1.20.1 → 1.20.2: i tocchi si osservano dal contenitore (`fluidTutorialTouches`), `FluidTutorialHost` non mette piu' niente sopra la pagina. 1.20.1: `fluidTutorialAnchor` dimentica i limiti quando l'elemento esce di scena. 1.21.0: i limiti non passano piu' dallo snapshot. 1.30.1: la politica e' una `data class`. 3.0 (fase 9): niente punta dipinta; 3.0.1: colore del testo del callout.

## fluidTutorialAnchor

**Cos'e'.** `@Composable fun Modifier.fluidTutorialAnchor(id: String): Modifier`: l'elemento a cui un suggerimento si aggancia. Misura i suoi limiti (`boundsInRoot`) e, **mentre e' lui quello spiegato, si porta un alone**. Senza, «questo» in una frase non indica niente (e il callout non ha piu' la punta: vedi sotto).

**Comportamento.**
- L'alone e' un rettangolo arrotondato attorno all'elemento: colore `primary` al 55% di opacita', bordo di 2 dp, rientro di 6 dp fuori dai limiti, raggio `FluidRadius.Group`. Compare solo se `state.presenting?.anchorId == id`.
- Un elemento che **esce di scena porta via i suoi limiti** (`onDispose` toglie l'ancora): senza, un callout finirebbe per indicare il punto dove c'era un tasto che ora non c'e' piu' (un tasto dentro un `AnimatedVisibility`, una schermata lasciata).
- Senza `LocalFluidTutorialHostState` (non montato) restituisce il modificatore invariato: nessun costo, nessun errore.

**Trappole.** L'`id` dell'ancora e' `FluidTutorial.anchorId`, che di serie e' l'`id` del suggerimento: un refuso fra i due e il suggerimento aspetta per sempre (la politica non trova l'ancora). Nessun errore, nessun log.

## FluidTutorialHost e fluidTutorialTouches

**Cos'e'.** Il padrone di casa: sta alla radice dell'app, sopra tutto il resto, e mostra un callout per volta quando la politica dice che e' il momento. `fluidTutorialTouches(state)` e' l'osservatore dei tocchi, da mettere sul **contenitore** della pagina e mai sopra.

**Firma essenziale.**

```kotlin
@Composable fun FluidTutorialHost(
  state: FluidTutorialHostState,
  labels: FluidTutorialLabels,
  backdrop: GlassBackdropState? = null,        // il vetro che il callout rifrange; null: pannello pieno
  modalPresenting: () -> Boolean = { false },   // un pannello dell'engine e' in scena: non parlare
)
fun Modifier.fluidTutorialTouches(state: FluidTutorialHostState): Modifier
```

**Comportamento.**
- **Il ciclo.** Un `LaunchedEffect(state)` richiama `state.evaluate(modalPresenting())` a intervalli di 120 ms (400 se idle). Il callout in scena resta composto finche' l'uscita non e' finita (senza, sparirebbe di colpo lasciando in scena solo l'ombra che si spegne).
- **Posizione** (`FluidCoachMarkPlacer.place`, pura): margini di 16 dp, distanza di 12 dp dall'elemento. **Sotto l'elemento se c'e' posto, sopra altrimenti, e nel lato piu' capiente quando non ci sta da nessuna delle due parti** (e il callout si rientra nei margini e nelle zone sicure);
  orizzontalmente centrato sull'elemento e rientrato nei margini. Un callout piu' largo dello schermo si appoggia al margine invece di sparire.
- **Ingresso e uscita di geometria e di alfa, mai di scala** (3.0, fase 9): `fadeIn(160)` + uno scivolamento verticale di 8 dp con `FluidMotion.snappy()` verso il posto che gli spetta (scende se sta sotto, sale se sta sopra); uscita `fadeOut(120)` + scivolamento.
  Il perche': il callout e' un pannello di vetro e un `scaleIn` su un nodo di vetro scala anche quello che il vetro sta rifrangendo: il testo della pagina dietro si ingrandirebbe di un 6% per il tempo dell'entrata, e quel testo sta fermo. Lo scivolamento e' un offset di layout (il vetro campiona dove sta davvero).
- **Nessun nodo sopra la pagina.** Il padrone di casa non mette niente di intercettante a tutto schermo; i limiti del callout (`calloutBounds`) li legge chi osserva i tocchi.
- **Colore del testo.** L'host sta alla radice, fuori da una `Surface`: fornisce `LocalContentColor` = `onSurface` (3.0.1).
- **Il tocco.** `fluidTutorialTouches` legge solo i *down* nel passaggio `Initial` (un nodo genitore vede il tocco senza consumarlo, i figli lo ricevono lo stesso) e chiama `state.touchAt(position)`.

**Trappole.**
- `fluidTutorialTouches` **sopra** la pagina (come fratello) la intercetta: l'app sotto smette di rispondere (1.20.1). Va sul `Box` che avvolge il contenuto.
- Dimenticare `fluidTutorialTouches`: nessun errore, ma i tocchi non contano come interazione (il successivo non si sblocca) e un tocco fuori non chiude. Era un'aggiunta obbligata di 1.20.2: chi montava il padrone di casa prima ha dovuto aggiungerla.
- `labels` e `backdrop` non sono nello stato: sono parametri del composable, e vanno passati a ogni composizione.

## FluidCoachMark e FluidCoachMarkPlacement

**Cos'e'.** Il callout: vetro, un titolo di due parole, una frase, il gesto disegnato quando serve, e i due modi di chiuderlo — «Ok» (un tasto di vetro) e il link che li spegne tutti. E' pubblico per essere mostrato in una galleria o in un test (`sample/…/catalog/AllGlassDemo.kt`, `AllGlassTest`); in un'app lo mostra `FluidTutorialHost`.

**Firma essenziale.**

```kotlin
@Composable fun FluidCoachMark(
  tutorial: FluidTutorial,
  labels: FluidTutorialLabels,
  placement: FluidCoachMarkPlacement?,   // null: composto ma invisibile (alpha 0), serve per misurarlo
  backdrop: GlassBackdropState?,
  onMeasured: (IntSize) -> Unit,         // la posizione dipende dalla misura: si sa solo dopo averlo disegnato una volta
  onDismiss: () -> Unit,
  onOptOut: () -> Unit,
)
data class FluidCoachMarkPlacement(val x: Float, val y: Float, val below: Boolean, val pointerX: Float)   // pixel, coordinate della radice
```

**Comportamento.**
- **Tutto vetro.** Con `backdrop` il pannello e' il pannello flottante di tutti (`glassSurface` con `GlassRole.Modal`, `GlassDefaults.panelTint()`, `GlassDefaults.panelOptics(overSharpPage = true)`: sta sopra una pagina nitida, quindi sale solo la sfocatura) e «Ok» e' un `FluidGlassButton` scelto (`selected = true`),
  `FluidGlassSize.Compact`, **colpo aptico `Close`** (la capsula che sostituisce). Il tasto di vetro sta su un pannello di vetro e ne campiona il materiale da solo (vetro su vetro, 2.13.0).
- **Senza `backdrop`, o senza `RenderEffect`** (`isRenderEffectSupported()` falso) il pannello e' pieno (`surfaceContainerHigh`) e «Ok» e' una capsula piena (`primaryContainer`, ancora `Close`, `FluidPressFeedback.Expand`): si chiude lo stesso (e' un test: «without glass the coach mark keeps its solid capsule, and it still dismisses»).
- **Misure.** Larghezza massima 320 dp, riempimento 16 dp, raggio `FluidRadius.Card` (continuo), l'animazione del gesto alta 44 dp; titolo `titleSmall` semi-grassetto, frase `bodyMedium` in `onSurfaceVariant`.
- **Si prende i propri tocchi**: senza, toccarne il testo premerebbe quello che ci sta dietro. Il link «non mostrare piu'» e' un testo pressabile con `FluidPressFeedback.Expand`.
- **Niente punta (3.0, fase 9).** Era un triangolo dipinto, opaco al 94%, appoggiato al bordo di un pannello di vetro: l'unica cosa del callout che non rifrangeva. Farne parte della sagoma non si puo' (la lente e' la dislocazione di un rettangolo arrotondato; una sagoma qualsiasi perde la lente e tiene la sfocatura),
  e uno shader nuovo accanto a quello del vetro e' proprio cio' che la regola dei materiali vieta. Il suo mestiere — dare a «questo» un referente — lo fanno l'alone di `fluidTutorialAnchor` e il posizionamento. `pointerX` resta nella geometria ma da li' non lo legge nessun disegno; `below` decide anche da che parte il callout scivola entrando.
- **TalkBack.** Il sorgente non imposta una semantica propria del callout (nessun `liveRegion`/`paneTitle`): i due tasti sono pressabili con ruolo `Button`.

**Trappole.**
- `placement = null` e' lo stato di misura, non di errore: con `placement` nullo il callout e' composto ma invisibile; chi lo usa a mano deve riscriverlo con la posizione dopo `onMeasured`.
- Dentro un test senza fondale (`backdrop = null`) il callout e' pieno: giusto, non un difetto.

**Non usarlo quando.** Serve una finestra che oscura (un modale: `FluidGlassModal…`, vedi la scheda dei modali) o una conferma (`FluidAlert`).

## FluidCoachMarkPlacer

**Cos'e'.** La geometria pura di un pop-up: «la cosa piu' facile da sbagliare in fondo a uno schermo, ed e' anche la piu' facile da provare».

```kotlin
FluidCoachMarkPlacer.place(
  anchor: Rect, calloutWidth: Float, calloutHeight: Float, screen: Size,
  safeTop: Float = 0f, safeBottom: Float = 0f, margin: Float = 0f, gap: Float = 0f, pointerInset: Float = 0f,
): FluidCoachMarkPlacement
```

Sotto l'elemento (`anchor.bottom + gap`) se ci sta fra `safeBottom` e il fondo; altrimenti sopra; altrimenti il lato con piu' spazio, e `y` e' rientrata fra `safeTop + margin` e `schermo − safeBottom − margin − altezza`. `x` e' centrata sull'elemento e rientrata fra `margin` e `schermo − margin − larghezza`;
`pointerX` e' il centro dell'elemento nelle coordinate del callout, rientrato di `pointerInset`. Il padrone di casa lo chiama con margini 16 dp, distanza 12 dp e rientro 26 dp (e **senza** `safeTop`/`safeBottom`: i margini sono quelli).

## FluidGestureHintAnimation

**Cos'e'.** Il gesto disegnato dentro il callout (`Canvas`): un dito che tiene premuto, che scorre, che trascina. Un giro dura 1600 ms (`FluidMotion.loop(1600)`: «abbastanza per leggerlo, poco per non ipnotizzare»).

| `FluidGestureHint` | cosa disegna |
|---|---|
| `Tap` | il dito appare e un'onda si allarga e si spegne |
| `LongPress` | un cerchio che si riempie (fino all'80% del giro) e, alla fine, il dito che «aggancia» (scala 1,15) |
| `SwipeHorizontal` | una capsula, e il dito che la porta da una parte e torna (con una sosta ai due estremi: «si puo' fare in due sensi») |
| `DragReorder` | quattro tessere, il dito che ne prende una (primo quarto) e la porta nella casella opposta |
| `Scrub` | una barra con cinque tacche e il dito che le percorre avanti e indietro |
| `LongPressAndTap` | il tocco nel primo 45% del giro e la pressione lunga nel resto: due gesti sullo stesso tasto, in fila |

**Comportamento.** Con le animazioni ridotte (`LocalFluidMotionPolicy.reducedMotion`) resta un **fermo immagine**: il fotogramma piu' leggibile del giro (fase 0,45). Colori dal tema: traccia `primary`, sagome `onSurfaceVariant` al 30%.
**Versione.** 1.20.0.

## Cosa e' verificato dai test

- `FluidTutorialPolicyTest` (`engine-ui`, 15 casi, orologio finto): senza candidati niente; due politiche uguali sono la stessa chiave per `remember` (il bug della 1.30.1); prima della quiete nessuno parla; un caricamento, un pannello aperto o l'attesa di un tocco rimandano; senza l'elemento sullo schermo il suggerimento aspetta;
  fra piu' candidati vince la priorita' piu' alta; l'ancora puo' avere un nome diverso dal suggerimento; uno per volta e il successivo aspetta un'interazione; chi non e' stato offerto per questa schermata non entra; cambiando schermata la coda si svuota; un caricamento ferma tutto finche' non finisce;
  il link «non mostrare piu'» arriva all'app come `optOut`; un tocco fuori dal callout lo chiude, uno dentro no; senza niente in scena un tocco vale solo come interazione; un suggerimento ritirato non torna.
- `FluidCoachMarkPlacementTest` (6): sotto l'elemento quando c'e' posto, sopra quando sotto non ci sta, un elemento che riempie lo schermo non fa uscire il callout, margini agli angoli, la punta (`pointerX`) che insegue il centro senza uscire dagli angoli, un callout piu' largo dello schermo si appoggia al margine.
- `AllGlassTest` (`sample`): «Ok» chiude e suona `FluidHapticEvent.Close` (non un tasto qualsiasi); senza vetro la capsula piena chiude lo stesso. La demo e' la sezione «Suggerimento» di `AllGlassDemo`.
- **Non provati da test**: il ciclo del padrone di casa nel tempo reale (`FluidTutorialHost`), il posizionamento dentro un'app vera, l'alone di `fluidTutorialAnchor`, il disegno di `FluidGestureHintAnimation`, la resa del vetro del callout (`docs/10-coerenza.md`, riga «suggerimenti al primo uso»: la resa e' ancora da guardare in mano in piu' celle).

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/tutorial/`) | cosa c'e' dentro |
|---|---|
| `FluidTutorial.kt` | `FluidTutorial`, `FluidGestureHint`, `FluidTutorialLabels`, `FluidTutorialPolicy`, `FluidTutorialHostState`, `LocalFluidTutorialHostState`, `rememberFluidTutorialHostState` |
| `FluidCoachMark.kt` | `FluidCoachMarkPlacement`, `FluidCoachMarkPlacer`, `fluidTutorialAnchor`, `FluidTutorialHost`, `fluidTutorialTouches`, `FluidCoachMark` |
| `FluidGestureHintAnimation.kt` | il gesto disegnato (un `draw…` per gesto) |
| `design-system.md`, sezione «Suggerimenti al primo uso (1.20.0)» | la ricetta di montaggio in forma breve ([../design-system.md](../design-system.md)) |
