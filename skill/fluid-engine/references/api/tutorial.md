# API · Suggerimenti e tutorial

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/tutorial/FluidCoachMark.kt`

- `data class FluidCoachMarkPlacement(val x: Float, val y: Float, val below: Boolean, val pointerX: Float)` — Dove finisce il callout e da che parte sta rispetto al suo elemento. Coordinate della radice, in pixel.
- `object FluidCoachMarkPlacer` — Dove mettere un callout rispetto al suo elemento. Puro: la geometria di un pop-up e' la cosa piu' facile da sbagliare in fondo a uno schermo, ed e' anche la piu' facile da provare.
  - `fun place(anchor: Rect, calloutWidth: Float, calloutHeight: Float, screen: Size, safeTop: Float = 0f, safeBottom: Float = 0f, margin: Float = 0f, gap: Float = 0f, pointerInset: Float = 0f): FluidCoachMarkPlacement`
- `@Composable fun Modifier.fluidTutorialAnchor(id: String): Modifier` — L'elemento a cui un suggerimento si aggancia: misura i suoi limiti per il callout e, mentre e' lui quello spiegato, si porta un alone. Senza, "questo" in una frase non indica niente.
- `@Composable fun FluidTutorialHost(state: FluidTutorialHostState, labels: FluidTutorialLabels, backdrop: GlassBackdropState? = null, modalPresenting: () -> Boolean = { false })` — Il padrone di casa dei suggerimenti: sta alla radice dell'app, sopra tutto il resto, e mostra un callout per volta quando la politica dice che e' il momento.
- `fun Modifier.fluidTutorialTouches(state: FluidTutorialHostState): Modifier` — L'osservatore dei tocchi, da mettere sul **contenitore** della pagina e mai sopra di essa: un nodo genitore vede il tocco nel giro iniziale senza consumarlo, e i figli lo ricevono lo stesso. Sopra la pagina, invece, lo intercetterebbe e basta.
- `@Composable fun FluidCoachMark(tutorial: FluidTutorial, labels: FluidTutorialLabels, placement: FluidCoachMarkPlacement?, backdrop: GlassBackdropState?, onMeasured: (IntSize) -> Unit, onDismiss: () -> Unit, onOptOut: () -> Unit)` — Il callout: vetro, un titolo di due parole, una frase, il gesto disegnato quando serve, e i due modi di chiuderlo — "Ok" (un tasto di vetro), e il link che li spegne tutti.

## `engine-ui/ui/tutorial/FluidGestureHintAnimation.kt`

- `@Composable fun FluidGestureHintAnimation(hint: FluidGestureHint, modifier: Modifier = Modifier)` — Il gesto disegnato dentro un callout: un dito che tiene premuto, che scorre, che trascina. Una frase puo' dire "tieni premuto e trascina", ma nessuna frase lo fa vedere in mezzo secondo.

## `engine-ui/ui/tutorial/FluidTutorial.kt`

- `data class FluidTutorial(val id: String, val priority: Int, val title: String, val text: String, val hint: FluidGestureHint? = null, val anchorId: String = id)` — Un suggerimento al primo uso (1.20.0): due parole di titolo, **una frase**, e il gesto disegnato se il gesto e' la cosa da spiegare. Non e' un tour: e' quello che una persona accanto direbbe indicando l'elemento, la prima volta che lo si incontra.
- `enum class FluidGestureHint` — Il gesto che il callout mima: un dito disegnato vale piu' di una frase che lo descrive.
- `data class FluidTutorialLabels(val dismiss: String, val next: String, val optOut: String)` — Le parole dei tasti del callout, in lingua dell'app.
- `data class FluidTutorialPolicy(val quietMillis: Long = DEFAULT_QUIET_MILLIS)` — Quando un suggerimento puo' comparire. Pura di proposito: le regole sono tutte di tempo e di stato, e un tempo si prova con un orologio finto meglio che con un dito.
  - `fun choose(candidates: List<FluidTutorial>, anchors: Map<String, Rect>, nowMillis: Long, lastInteractionMillis: Long, loading: Boolean, modalPresenting: Boolean, awaitingInteraction: Boolean): FluidTutorial?`
  - `companion object`
    - `const val DEFAULT_QUIET_MILLIS` — Sei decimi di secondo: il tempo in cui una mano che stava facendo qualcosa si ferma.
    - `const val TICK_MILLIS` — Ogni quanto la politica si richiede se e' il momento, mentre qualcosa e' in attesa.
    - `const val IDLE_TICK_MILLIS` — Lo stesso, quando non c'e' niente da mostrare: il ciclo resta, ma quasi fermo.
- `class FluidTutorialHostState internal constructor(internal val policy: FluidTutorialPolicy, private val clock: () -> Long)` — Lo stato del padrone di casa dei suggerimenti: chi e' in coda, dove sono gli elementi, quando l'utente ha toccato per l'ultima volta, e chi e' in scena adesso (uno solo per volta).
  - `var presenting: FluidTutorial? by mutableStateOf(null)` — Il suggerimento in scena, se c'e'. Uno per volta, sempre.
  - `val pendingCount: Int get()` — Quanti ne restano in coda per questa schermata: serve al ciclo per andare in letargo.
  - `var onShown: (String) -> Unit` — Chiamato quando un suggerimento entra in scena: l'app lo segna come visto.
  - `var onDismissed: (id: String, optOut: Boolean) -> Unit` — Chiamato alla chiusura: `optOut` vero se l'utente ha chiesto di non vederne piu'.
  - `fun offer(tutorial: FluidTutorial, screen: String)` — Offre un candidato per la schermata corrente. Ripetibile: il doppione non entra due volte.
  - `fun withdraw(id: String)` — Toglie un candidato dalla coda (l'app l'ha appena segnato visto, o non serve piu').
  - `var presentingAnchor: Rect? by mutableStateOf(null)` — Dove sta l'elemento del suggerimento in scena. Questo si' e' uno stato: il callout lo segue se l'elemento si sposta, e assegnare un valore uguale non ricompone niente.
  - `fun anchorBounds(id: String, bounds: Rect?)` — I limiti dell'elemento, in coordinate della radice; null quando esce di scena.
  - `fun touchAt(position: Offset)` — Un tocco sulla pagina, visto da chi ospita i suggerimenti. Vale come interazione (e quindi sblocca il suggerimento successivo) e, se cade fuori dal callout, lo chiude.
  - `fun interacted()` — Un dito, uno scorrimento, un tasto: il conto della quiete riparte da qui.
  - `fun loading(active: Boolean)` — Mentre una schermata carica non si parla: la pagina non e' ancora quella che si vedra'.
  - `fun screenChanged(key: String?)` — Cambio di schermata: la coda della precedente non ha piu' senso, e la quiete riparte.
  - `fun dismiss(optOut: Boolean = false)` — Chiude quello in scena. Dopo, il successivo aspetta **un'interazione**: due callout di fila senza che l'utente abbia fatto niente si leggono come un tutorial obbligatorio, che e' esattamente quello che questa fase non vuole essere.
- `val LocalFluidTutorialHostState: ProvidableCompositionLocal<FluidTutorialHostState?>` — Il padrone di casa della schermata corrente; null dove i suggerimenti non sono montati.
- `@Composable fun rememberFluidTutorialHostState(policy: FluidTutorialPolicy = FluidTutorialPolicy(), clock: () -> Long = System::currentTimeMillis): FluidTutorialHostState`
