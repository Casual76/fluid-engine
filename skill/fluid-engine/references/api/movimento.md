# API · Pressione, gesto e movimento

_Generato sull'engine 3.0.5 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidButtonGesture.kt`

- `enum class FluidPressGrip` — Di chi e' il dito che e' sceso su un pulsante, quando una pagina che lo contiene vuole scorrere.

## `engine-ui/ui/fluid/FluidClock.kt`

- `@Composable fun rememberCurrentDate(): LocalDate` — La data di oggi, che resta oggi.

## `engine-ui/ui/fluid/FluidGooey.kt`

- `fun Modifier.gooey(blurRadiusPx: () -> Float): Modifier` — Composites this element into one offscreen layer and gooifies it: blurs by [blurRadiusPx], then snaps the blur back to a crisp edge via an alpha threshold. Offscreen compositing is what makes overlapping shapes' alpha bleed together *before* the threshold — two independently-filtered layers would just look like two blurred blobs with a seam, not a merged one.

## `engine-ui/ui/fluid/FluidInteractions.kt`

- `@Composable fun Modifier.fluidPressable(onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, enabled: Boolean = true, pressedScale: Float = FluidPressFeedback.DefaultYieldScale, role: androidx.compose.ui.semantics.Role? = null, interactionSource: MutableInteractionSource? = null, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, focusShape: Shape? = null, feedback: FluidPressFeedback? = null, pressState: FluidPressState? = null, longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart, grip: FluidPressGrip = FluidPressGrip.HoldsFinger): Modifier` — Press feedback in the shape Apple gives it: the element itself answers the finger.
- `@Composable fun Modifier.fluidRowPressable(onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, enabled: Boolean = true, animateFeedback: Boolean = true, shape: Shape? = null, highlightColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha…, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart, role: Role? = null): Modifier` — Press feedback for a row inside a grouped list.

## `engine-ui/ui/fluid/FluidMotion.kt`

- `object FluidMotion` — Motion tokens calibrated the way Apple parameterises its springs.
  - `fun stiffnessForResponse(response: Double): Float` — Converts an Apple-style `response` (seconds) into a Compose stiffness.
  - `val ResponseInstant` — ~0.20s. Micro-feedback that must land before the finger lifts.
  - `val ResponseTitle` — ~0.26s. The large title's handoff: tight enough to track a finger, slow enough to be read.
  - `val ResponseSnappy` — ~0.30s. Press states, switches, chrome cross-fades.
  - `val ResponseStandard` — ~0.38s. The workhorse: list changes, selection, most enter/exit.
  - `val ResponseSmooth` — ~0.50s. Large surfaces: sheets, expanding cards, hero content.
  - `val ResponseGentle` — ~0.62s. Full-screen or long-travel movement.
  - `const val DampingChrome` — Critically damped. Nothing overshoots, which is what chrome (bars, backgrounds, opacity) must do — a bouncing navigation bar is the single loudest "this is not premium" tell.
  - `const val DampingStandard` — A whisper of settle. Reads as "physical" without looking springy.
  - `const val DampingFluid` — Deliberate, visible settle for playful elements only.
  - `fun <T> instant(): SpringSpec<T>`
  - `fun <T> snappy(): SpringSpec<T>`
  - `fun <T> standard(): SpringSpec<T>`
  - `fun <T> smooth(): SpringSpec<T>`
  - `fun <T> gentle(): SpringSpec<T>`
  - `fun <T> fluid(): SpringSpec<T>`
  - `val MenuOpenStiffness` — ~0.36s: piu' viva della molla standard, perche' un menu risponde a un dito che aspetta.
  - `const val MenuOpenDamping` — Sottosmorzata di proposito: l'apertura oltrepassa il pannello e si posa — il "pop" del menu. Il picco di (progress − 1) per questo smorzamento e' [MenuPeakOvershoot].
  - `fun <T> menuOpen(): SpringSpec<T>` — L'apertura del menu: la molla sottosmorzata che porta l'ancora al pannello.
  - `fun <T> menuClose(): SpringSpec<T>` — La chiusura: criticamente smorzata, nessun rimbalzo — un menu che si ritira non oscilla.
  - `const val MenuInflationGain` — Quanto gonfia il pannello oltre il traguardo per ogni unita' di oltrepasso: lo stesso 0,6 di `FluidPopoverWindowPopGain` nel modale, spostato fra i token perche' un numero solo decida il "pop" di ogni finestra di menu.
  - `const val MenuPeakOvershoot` — Il picco atteso di (progress − 1) con [menuOpen]: `exp(−πζ / √(1 − ζ²))` per ζ = 0,62, cioe' 0,084. Sostituisce lo 0,15 che tarava il tetto sui fianchi per la rincorsa e la molla del morph: la molla del menu e' un'altra, e un tetto tarato su un picco piu' alto lasciava il pannello piu' stretto del margine che aveva.
  - `const val MenuContentOpenFrom` — **DEPRECATO: Dalla 3.0 il contenuto arriva a corpo quasi formato: LiquidContentOpenFrom.** — Il contenuto del pannello entra fra questi due punti del viaggio (progress), mai prima.
  - `const val MenuContentOpenTo` — **DEPRECATO: Dalla 3.0: LiquidContentOpenTo.**
  - `const val MenuContentCloseFrom` — **DEPRECATO: Dalla 3.0 il contenuto se ne va per primo: LiquidContentCloseFrom.** — In chiusura le voci seguono la sagoma e si spengono fra questi due punti: da ~0,92 a ~0,12. Era la finestra dell'apertura rovesciata e le voci sparivano in ~40 ms; cosi' restano leggibili per 60-200 ms mentre il pannello si ritira.
  - `const val MenuContentCloseTo` — **DEPRECATO: Dalla 3.0: LiquidContentCloseTo.**
  - `const val MenuFaceTo` — La faccia dell'ancora (icona, etichetta) si spegne entro questo punto del viaggio.
  - `const val MenuFadeOutMillis` — La dissolvenza di uscita del menu (la goccia e il contenuto dopo una scelta), in millisecondi.
  - `const val DropPoints` — Quanti punti ha il contorno della goccia, campionati per lunghezza d'arco.
  - `const val DropStiffnessNear` — La rigidezza della molla di un punto accanto all'ancora (1/s²): segue senza ritardo.
  - `const val DropStiffnessFar` — La rigidezza di un punto lontano dall'ancora: morbida, la coda resta indietro.
  - `const val DropDamping` — Lo smorzamento delle molle dei punti, frazione del critico.
  - `const val DropEdgeTensionReference` — La tensione del bordo del banco con 48 punti (1/s²): quanto ogni punto tira verso la media dei due vicini. Va per il quadrato del numero di punti; la goccia ne usa la frazione [DropEdgeTensionDefault].
  - `const val DropEdgeTensionDefault` — La frazione di [DropEdgeTensionReference] di serie: 0,2 (140 con 48 punti), scelta di Alessio — a 0,7 i bordi erano "esageratamente smussati". Con un bordo cosi' poco teso ogni punto segue quasi da solo il suo punto a riposo, e gli angoli del pannello restano definiti anche in viaggio.
  - `const val DropAnchorReachDp` — Fin dove l'ancora irrigidisce le molle (decadimento esponenziale), per un menu o un morph, in dp.
  - `const val DropFingerReachDp` — Lo stesso per una superficie trascinata, attorno al dito, in dp.
  - `const val DropStepSeconds` — Il passo d'integrazione delle molle (Euler semi-implicito), in secondi.
  - `const val DropMaxFrameSeconds` — Il fotogramma piu' lungo che si integra (oltre e' uno stallo, non un moto), in secondi.
  - `const val DropRestPx` — Sotto questo scarto, in pixel, fra ogni punto e il suo punto a riposo la goccia e' una lastra.
  - `const val DropRestSpeedDp` — E sotto questa velocita' dei punti, in dp al secondo.
  - `const val DropOpenStiffness` — La molla del viaggio d'andata della goccia: risposta ~0,28 s (k 500), poco sottosmorzata.
  - `const val DropCloseStiffness` — La molla del ritorno: ~0,50 s (k 158), un filo piu' lenta dell'andata (Alessio: "troppo veloce").
  - `const val DropJourneyDamping` — Lo smorzamento delle due molle del viaggio.
  - `fun <T> dropOpen(): SpringSpec<T>` — L'apertura di una goccia (un menu che nasce da un tasto, un morph).
  - `fun <T> dropClose(): SpringSpec<T>` — Il ritorno di una goccia nella sua ancora.
  - `const val DropInflationGain` — Il gonfiore uniforme oltre il traguardo, per unita' d'oltrepasso della molla: `1 + 0,55·(m − 1)` sulle misure; sotto lo zero lo stesso al contrario, attorno all'ancora.
  - `const val DropPeakOvershoot` — Il picco atteso di (m − 1) con [dropOpen]: `exp(−πζ/√(1 − ζ²))` per ζ = 0,72.
  - `const val LiquidContentOpenFrom` — Il contenuto di un pannello di vetro liquido arriva fra questi due punti del viaggio. Erano 0,55 e 0,9 fino alla 3.0.3: le voci si leggevano solo a pannello quasi fermo, ed era meta' della lentezza che si sentiva in apertura. Restano ritagliate dentro il corpo (`travelClip`), e con la molla d'andata della 3.0.4 sono piene ~90 ms dopo la partenza.
  - `const val LiquidContentOpenTo`
  - `const val LiquidContentCloseFrom` — E se ne va per primo fra questi due, al ritorno (da 0,94 a 0,62).
  - `const val LiquidContentCloseTo`
  - `const val LiquidContentMinScale` — La scala del contenuto mentre arriva o se ne va: `0,86 + 0,14 · opacita'`.
  - `const val LiquidLensFullSpeedDp` — La lente in moto: piena a questa velocita' del corpo, in dp al secondo.
  - `const val LiquidLensFollowSeconds` — Quanto in fretta la lente in moto segue la velocita' (costante di tempo, secondi).
  - `const val LiftedSwellX` — Il gonfiore della riga sollevata del menu contestuale all'oltrepasso della molla, per asse: ~2% sui fianchi (una riga larga quanto lo schermo non deve toccarne i bordi), ~5% in altezza.
  - `const val LiftedSwellY`
  - `fun dp(dampingRatio: Float = DampingStandard, stiffness: Float = ResponseStandard): SpringSpec<Dp>`
  - `fun intOffset(dampingRatio: Float = DampingStandard, stiffness: Float = ResponseStandard): SpringSpec<IntOffset>`
  - `fun intSize(dampingRatio: Float = DampingStandard, stiffness: Float = ResponseStandard): SpringSpec<IntSize>`
  - `val EaseOut: Easing` — Apple's standard ease for content that enters.
  - `val EaseInOut: Easing` — Symmetric ease for cross-fades and colour changes.
  - `val EaseIn: Easing` — For content leaving: starts immediately, so the exit never feels sticky.
  - `val EaseEmphasized: Easing` — Decisive start, long calm settle, no overshoot. For whole pages.
  - `val EaseSpringy: Easing` — The same shape with about three percent of overshoot, for elements that are small enough to carry it: a chip, an icon, a badge.
  - `const val DurationExpand: Int` — A destination growing out of the control that was touched.
  - `const val DurationCollapse: Int` — The same movement undone. Slightly quicker: going back should feel like a release.
  - `const val DurationPeer: Int` — Peer destinations: one ordered step sideways, along the axis the tab bar itself implies.
  - `const val DurationPeerFadeIn: Int` — A peer page arriving does not fade at all; only the one leaving does, and briefly.
  - `const val DurationPeerFadeOut: Int`
  - `const val PeerSlideFraction: Float` — How far a peer page travels, as a fraction of the width.
  - `const val DurationRouteFadeOut: Int` — **DEPRECATO: Dalla 3.0 nessuna pagina sfuma: FluidNavHost/FluidPageHost muovono pagine opache. Le schede ** — How long a covered page takes to give way underneath the one that replaced it.
  - `fun <T> fadeIn(durationMillis: Int = 220): FiniteAnimationSpec<T>`
  - `fun <T> fadeOut(durationMillis: Int = 160): FiniteAnimationSpec<T>`
  - `fun <T> crossFade(durationMillis: Int = 200): FiniteAnimationSpec<T>`
  - `fun <T> color(durationMillis: Int = 240): AnimationSpec<T>` — Colour transitions read best on a plain ease — springs make tints wobble.
  - `const val PixelThreshold: Float` — Mezzo pixel: sotto questa distanza una molla che muove pixel e' ferma.
  - `fun pixels(dampingRatio: Float = DampingStandard, stiffness: Float = ResponseStandard): SpringSpec<Float>` — Una molla per una distanza in pixel (un trascinamento che torna, uno scatto di scorrimento): i valori dei token con la soglia di [PixelThreshold], perche' quella di serie (0,01) farebbe girare la molla ancora a lungo dopo l'ultimo pixel visibile.
  - `fun <T> tuned(dampingRatio: Float, stiffness: Float): SpringSpec<T>` — Una molla con smorzamento e rigidezza dati dai token (`Damping*`, `Response*`), per chi li sceglie a tempo di esecuzione (attacco e rilascio, entrata e uscita) e non ha un nome suo.
  - `fun <T> flick(): SpringSpec<T>` — ~0,16 s, senza rimbalzo: la molla di serie di Compose (rigidezza 1500), che `animate*AsState` e `Transition.animateFloat` usavano quando nessuno ne dava una. Per un valore che si accende o si spegne in un lampo (un lavaggio di selezione, l'alfa di una lente). Un nome, non un default implicito.
  - `val TabTugReturn: SpringSpec<Float>` — Il ritorno della barra flottante dopo un tiro del dito sul puck: (1, 300), mezzo pixel.
  - `const val GooeyMillis: Int` — La durata della piegatura "gommosa" fra barra in linea ed estesa, e del puck che arriva con lei.
  - `val GooeyPulse: KeyframesSpec<Float>` — Il picco di sfocatura della piegatura: 0 all'inizio, 1 a meta' (curva `FastOutSlowIn`), 0 alla fine.
  - `val PuckEnter: TweenSpec<Float>` — Il puck che entra con la barra che si apre: lungo quanto la piegatura ([GooeyMillis]), `FastOutSlowIn`.
  - `val FoldCrossFade: SpringSpec<Float>` — Il cambio fra barra in linea ed estesa: la dissolvenza di serie di `AnimatedContent` (rigidezza 400, critica).
  - `const val TabPuckLeadMillis: Long` — Quanto il puck resta solo a viaggiare prima che la pagina scelta cominci a costruirsi, in millisecondi.
  - `val TabBarFold: SpringSpec<Float>` — La barra ripiegata che si apre e si chiude: (0,9, rigidezza 400), un filo di assestamento.
  - `const val TabEnterMillis: Int` — Durata e finestra (frazioni della durata) dell'entrata di una scheda: invisibile fino a meta', piena a 0,8.
  - `const val TabEnterStartFraction: Float`
  - `const val TabEnterEndFraction: Float`
  - `fun tabEnterAlpha(entering: Boolean): FiniteAnimationSpec<Float>` — L'alfa di una scheda della barra: sale fra [TabEnterStartFraction] e [TabEnterEndFraction] se entra, lineare se esce.
  - `fun tabEnterBlur(entering: Boolean, blurPx: Float): FiniteAnimationSpec<Float>` — La sfocatura di una scheda che entra: [blurPx] fino a [TabEnterStartFraction], zero a [TabEnterEndFraction].
  - `val SheetSettle: SpringSpec<Float>` — Il foglio trascinato che torna al suo posto: (0,9, rigidezza 500) dalla velocita' del dito, mezzo pixel.
  - `const val PopoverFadeInMillis: Int` — L'opacita' del pop-up in entrata: arriva ben prima della geometria, cosi' il contenuto e' gia' leggibile.
  - `const val PopoverFadeOutMillis: Int` — In uscita dura di piu' che in entrata: l'occhio segue il pannello che torna nella riga, e uno scrim orfano non resta.
  - `const val PopoverWindowFadeOutMillis: Int` — La dissolvenza di chiusura della finestra di Fluid-physics: la sagoma deve atterrare sulla riga prima che finisca.
  - `const val PopoverWindowRunUpEnd: Float` — Fin dove arriva la rincorsa della finestra ancorata (frazione del viaggio): ancora in accelerazione, poi la molla.
  - `val PopoverWindowRunUp: TweenSpec<Float>` — La rincorsa: 130 ms tutti in ease-in (`CubicBezier(0,55; 0; 0,85; 0,55)`), finisce ripida per passare la velocita' alla molla. Il moto, detto parola per parola: "accelerazione continua finche' non raggiunge il bordo, e allora inizia a rallentare, oltre, fino a ritornare alla posizione finale" — una rincorsa che copre il novanta per cento del viaggio ancora in accelerazione ([PopoverWindowRunUpEnd]) e una molla che ne…
  - `val PopoverWindowSettleX: SpringSpec<Float>` — L'assestamento della finestra dopo la rincorsa, orizzontale: (0,55, 300). Piu' morbida di Y, per lo squash.
  - `val PopoverWindowSettleY: SpringSpec<Float>` — L'assestamento della finestra dopo la rincorsa, verticale: (0,62, 300).
  - `val PopoverOpenX: SpringSpec<Float>` — L'apertura del pop-up classico, orizzontale: (0,62, 260). Due molle diverse danno lo squash, due uguali un pallone.
  - `val PopoverOpenY: SpringSpec<Float>` — L'apertura del pop-up classico, verticale: (0,74, 260).
  - `val PopoverWindowExit: SpringSpec<Float>` — La chiusura della finestra di Fluid-physics: critica e rigida (620), piu' di quella classica ([PopoverExit]), perche' la sagoma deve ATTERRARE sulla riga prima che la dissolvenza ([PopoverWindowFadeOutMillis]) finisca: l'ordine delle due cose rende la chiusura un gesto solo invece di una sparizione per aria.
  - `val PopoverExit: SpringSpec<Float>` — La chiusura del pop-up classico: critica (420), un'uscita che rimbalza sembra un pannello cacciato dallo schermo.
  - `fun dropValue(visibilityThreshold: Float): SpringSpec<Float>` — Il valore della goccia (la sua posizione sulla pista): (1, 1000), critica e rigida; la soglia la da' la pista.
  - `fun dropVelocity(dampingRatio: Float, visibilityThreshold: Float): SpringSpec<Float>` — La velocita' tracciata della goccia, da cui nasce lo squash: (smorzamento dato, 300). Quello di Kyant e' 0,5.
  - `val DropPress: SpringSpec<Float>` — La pressione della goccia, che la fa lente: (1, 1000), critica e rigida.
  - `val DropScaleX: SpringSpec<Float>` — La scala X della goccia: (0,6, 250). Piu' morbida di Y: e' questa differenza che fa lo squash.
  - `val DropScaleY: SpringSpec<Float>` — La scala Y della goccia: (0,7, 250).
  - `val GlowPosition: SpringSpec<Offset>` — Il punto di luce che segue il dito e torna al punto di partenza: (0,5, 300).
  - `val GlowPress: SpringSpec<Float>` — L'accensione e lo spegnimento della luce del dito: (0,5, 300), la stessa molla dei tasti di LibrePods.
  - `const val PulseMillis: Int` — Meta' di un respiro (l'andata; il ritorno e' uguale): la durata che quattro app su cinque avevano scelto, e quella dello spinner.
  - `const val PulseLowAlpha: Float` — Il punto basso del respiro di un segno pieno (un pallino): abbastanza basso da vedersi, abbastanza alto da non sparire sul chiaro.
  - `const val PulseFillLowAlpha: Float` — Il punto basso del respiro di un riempimento (il passo in corso): piu' alto di [PulseLowAlpha], a 0,35 sembrerebbe il passo da fare.
  - `fun pulse(periodMillis: Int = PulseMillis, startOffsetMillis: Int = 0): InfiniteRepeatableSpec<Float>` — Il respiro: un'andata e ritorno di [periodMillis] per fase, che parte da [startOffsetMillis] dentro il ciclo (l'orologio, cosi' tutti i pallini in scena battono insieme).
  - `const val SpinnerMillis: Int` — Il giro dello spinner a raggi: un'intera corona in 900 ms, lineare.
  - `const val IndeterminateMillis: Int` — Il segmento della barra indeterminata: 1100 ms da un bordo all'altro.
  - `fun loop(durationMillis: Int, easing: Easing = LinearEasing, restMillis: Int = 0): InfiniteRepeatableSpec<Float>` — Un giro che si ripete da capo ([RepeatMode.Restart]): [durationMillis] di moto, poi [restMillis] di pausa (il ritardo di `tween`), poi ancora. Lineare di serie: un giro che si ripete non ha un'accelerazione da raccontare, salvo dove la chiede lui (la barra, il riflesso).
  - `const val FastScrollerHideMillis: Long` — Quanto la maniglia dello scorrimento rapido resta in vista dopo l'ultimo movimento della lista, in millisecondi. Il valore che le due app di partenza avevano scelto (1,4 s in MyCloud, mezzo secondo di dissolvenza in Fluidify), un filo sotto; prima era una costante privata di `FluidFastScroller`.
  - `const val ScrubberHoldMillis: Long` — Quanto un `FluidScrubber` tiene la maniglia dove e' stata lasciata, aspettando che la posizione che arriva da fuori la raggiunga, in millisecondi.

## `engine-ui/ui/fluid/FluidMotionPolicy.kt`

- `data class FluidMotionPolicy(val durationScale: Float)` — One source of truth for motion that must disappear when the system animator scale is zero.
  - `val reducedMotion: Boolean get()`
  - `val allowDecorativeMotion: Boolean get()`
  - `val allowElasticity: Boolean get()`
  - `companion object`
    - `val Default`
- `val LocalFluidMotionPolicy`
- `@Composable fun rememberFluidMotionPolicy(): FluidMotionPolicy`
- `@Composable fun FluidMotionPolicyProvider(policy: FluidMotionPolicy = rememberFluidMotionPolicy(), content: @Composable () -> Unit)`

## `engine-ui/ui/fluid/FluidPress.kt`

- `data class FluidPressFeedback(val pressedScale: Float = DefaultYieldScale, val expands: Boolean = false, val followsFinger: Boolean = false, val dragHaptics: Boolean = false, val maxScale: Float = TextMaxScale, val longSideCapDp: Float = TextLongSideCapDp)` — Come un elemento risponde al dito.
  - `companion object`
    - `val Expand: FluidPressFeedback` — I pulsanti dell'engine con un testo dentro: crescono del 10%, seguono il dito, vibrano come LibrePods.
    - `val ExpandRound: FluidPressFeedback` — I tasti tondi, solo icona: crescono del 50%, e' il carattere di `StyledIconButton` di LibrePods (un tasto da 44 dp arriva a 66, e al picco della molla a 70). Per tutto il resto, [Expand].
    - `fun yielding(scale: Float = DefaultYieldScale): FluidPressFeedback` — Le superfici: cedono fino a [scale] e stanno ferme. Il comportamento di sempre.
    - `val None: FluidPressFeedback` — Nessuna trasformazione: per chi si disegna la pressione da se' (un vetro, una pastiglia).
    - `const val DefaultYieldScale: Float` — La cessione storica di `fluidPressable`.
- `class FluidPressState internal constructor(private val scope: CoroutineScope, feedback: FluidPressFeedback)` — Lo stato di una pressione, condivisibile fra chi la guida e chi la disegna.
  - `val progress: Float get()` — Quanto e' premuto: 0 a riposo, 1 premuto. Le molle possono uscire di poco da entrambi i lati.
- `@Composable fun rememberFluidPressState(feedback: FluidPressFeedback = FluidPressFeedback.Expand): FluidPressState` — Uno stato di pressione per un controllo, da passare a chi lo guida e a chi lo disegna. Vedi [FluidPressState].
- `val LocalFluidPressImmediate` — **DEPRECATO: Non ha nessun effetto dalla 3.0: ogni pulsante e' premuto al contatto. Si puo' togliere.** — Non fa piu' niente (3.0). Fino alla 2.13 diceva ai pulsanti sotto di essere "del dito" gia' al tocco invece che dopo 100 ms, per la chrome in cui non scorre niente. Dalla 3.0 ogni pulsante e' premuto **al contatto** e il dito che scende su di lui e' suo fino al sollevamento ([FluidPressGrip.HoldsFinger]), quindi non c'e' piu' un'attesa da azzerare ne' una lista da non attraversare.
