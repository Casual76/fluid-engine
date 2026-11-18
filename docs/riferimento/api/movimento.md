<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/movimento.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Pressione, gesto e movimento

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

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
  - `val FloatingTabSearchPage: TweenSpec<Float>` — La pagina di ricerca dell’app può velarsi insieme alla barra. In Photos il primo frame conserva la foto.
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
  - `fun groupedItemPlacement(): SpringSpec<IntOffset>` — Una riga di una lista che si sposta perche' un'altra e' entrata o uscita: critica (una lista non rimbalza: e' un contenitore) e col passo delle liste (`ResponseStandard`).
  - `const val GroupedItemFadeInMillis: Int` — Quanto dura la comparsa di una riga nuova, in millisecondi: la dissolvenza d'ingresso di serie.
  - `const val GroupedItemFadeOutMillis: Int` — Quanto dura l'uscita di una riga tolta, in millisecondi: piu' breve dell'ingresso, come ogni uscita.
  - `fun swipeSettle(): SpringSpec<Float>` — La riga che torna a posto dopo uno scorrimento di lato (annullato, o confermato su un'azione che non la toglie): pixel, critica, rapida. Parte dalla velocita' del dito, come la pagina del back.
  - `fun swipeExit(): SpringSpec<Float>` — La riga che esce del tutto verso il lato, confermata un'azione che la toglie: la stessa molla del ritorno, perche' e' lo stesso oggetto che finisce lo stesso gesto, solo verso l'altro capo.
  - `fun <T> swipeArm(): SpringSpec<T>` — Il colore dell'azione che si accende quando la soglia e' superata: un lampo, perche' e' la risposta a un momento (la soglia) e deve arrivare insieme al colpo dell'aptica, non dopo.
  - `fun jumpPillOffset(): SpringSpec<IntOffset>` — La pillola «N nuove» che entra o esce: per offset e alfa, mai per scala (e' vetro).
  - `const val JumpPillFadeInMillis: Int` — La dissolvenza della pillola «N nuove», in millisecondi, in entrata.
  - `const val JumpPillFadeOutMillis: Int` — La dissolvenza della pillola «N nuove», in millisecondi, in uscita.
  - `const val DrawerResponseSeconds: Double` — La risposta della molla del pannello laterale, in secondi: 0,34, quella delle pagine (`FluidPageMotion.ResponsePageSeconds`). Il pannello e' la cosa piu' grande che si muove dopo una pagina, arriva dallo stesso bordo e si chiude sotto lo stesso dito: con un'altra molla due gesti gemelli avrebbero due tempi.
  - `val DrawerTravel: SpringSpec<Float>` — Il pannello laterale che entra, esce o torna dopo un rilascio, **in frazione di corsa** (0 chiuso, 1 aperto): criticamente smorzata (un pannello non rimbalza contro il bordo), risposta [DrawerResponseSeconds], ferma sotto un millesimo di corsa (meno di mezzo pixel su un pannello da 320 dp). Parte dalla velocita' del dito, convertita in corsa al secondo, come la molla delle pagine.
  - `const val ChartEnterMillis: Int` — Quanto dura la crescita d'ingresso di un grafico, in millisecondi: piu' lenta di un'uscita, sobria.
  - `fun chartEnter(): FiniteAnimationSpec<Float>` — L'ingresso di un grafico: una frazione 0..1 per cui si moltiplicano barre, arco e tracciato. Una curva senza rimbalzo ([EaseOut]) e non una molla: una barra che supera il suo valore e torna indietro direbbe per un attimo un numero falso.
  - `fun chartSelect(): SpringSpec<Float>` — Il segmento o la barra scelti che si evidenziano, e tornano: critica, col passo dei controlli.
  - `const val PlaneContextMillis: Int` — Un cambio di contesto: pezzi che si spostano, cambiano forma, nascono da un vicino o vi confluiscono, in millisecondi. R4 B2 (a, c), fiducia **alta**: 270–330 ms sul telefono; il centro della forchetta, che è anche [DurationExpand]: un pezzo che diventa un altro e una destinazione che nasce dal tasto toccato sono lo stesso gesto.
  - `const val PlaneAppearMillis: Int` — Una comparsa per lente (`Materialize`), in millisecondi. R4 B3, fiducia **media**: la rifrazione sale e il contorno si stringe in 430 ms, con l'opacità del corpo ferma; il simbolo si condensa da 2× a 1× nello stesso tempo.
  - `const val PlaneDisappearMillis: Int` — Una scomparsa per lente, in millisecondi. R4 B3, fiducia **media**: il simbolo se ne va in 130 ms, la rifrazione va a zero entro 400, poi il contorno; più lenta della comparsa perché l'ultima cosa che sparisce è il bordo.
  - `const val PlaneAppearScale: Float` — Di quanto è più largo il contorno di un vetro che compare all'inizio (e di uno che scompare alla fine). R4 B3, fiducia **media**: 8–10% più largo; 1,09 è il centro della forchetta. **Scelta di Alessio, 2026-11-16**, sulla tavola «scala iniziale della comparsa» di F3.3 (1,09 / 1,045 / 1,00): 1,09, come R4.
  - `const val PlaneRestEnvelope: Float` — La busta della molla del piano, in frazione della corsa di ogni lato e raggio, che il viaggio raggiunge nel suo bilancio (F3.2c). Per una molla smorzata a 0,9 che parte ferma, la busta all'1,5% è il punto da cui la sagoma resta per sempre entro lo 0,5% della corsa: a 300 ms di bilancio il riposo a vista cade a ~294 ms (modello di F3.2c, contro i 270–330 di R4 B2).
  - `const val PlaneRestUnit: Float` — La stessa soglia per `materialize` (0..1), assoluta: 0,2%. Più stretta della corsa di un lato, perché l'ultimo 5% di `materialize` è il simbolo che si condensa da 2× a 1× (`glassMaterializeChannels`): all'1,5% sarebbe ancora 1,2×.
  - `const val PlaneSettlePx: Float` — Quando finisce un viaggio del piano: ogni lato e raggio a mezzo pixel dall'arrivo (la regola dell'engine per il fermo, `FluidGlassPlaneTolerancePx`). Da lì la geometria è quella d'arrivo, al bit, e il palco lascia il vetro al riposo senza un salto che si veda.
  - `const val PlaneSettleUnit: Float` — La stessa soglia per `materialize`: un ventesimo della [PlaneRestUnit].
  - `const val PlaneFirstStepMillis: Int` — Di quanto avanza al più l'orologio di un viaggio del piano in ognuno dei suoi primi [PlaneFirstSteps] fotogrammi, in millisecondi (F3.8a): un fotogramma a 60 Hz. A schermo fermo un pannello a frequenza variabile gira a 30 Hz, e quando un cambio arriva senza un tocco sale a 120 con un buco di 58–92 ms senza fotogrammi (S3, `framestats`: intervallo da 33,3 a 8,3 ms): con l'orologio a muro il secondo fotogramma del viag…
  - `const val PlaneFirstSteps: Int` — Quanti fotogrammi dopo la partenza valgono il tetto di [PlaneFirstStepMillis]: quelli della salita del pannello.
  - `const val PlaneSwellMaxDp: Float` — Il **rigonfiamento** del vetro in viaggio (F3.8a, punto 8) non sposta mai un lato di più di così, in dp. Quanto e quando un pezzo si gonfia lo dice il carattere del viaggio (F3.9, `GlassLiquidJourney.swell`: il 15% del lato corto al 45% della fase, misurato su tasti e barre, cioè 3,6 dp per lato su un tasto da 48 dp e 4,8 su una barra da 64). Questo tetto non li tocca;
  - `const val PlaneReleaseMillis: Int` — Il **riposo a vista** di una nascita o di una confluenza, in millisecondi: 540. È il **ritorno** del vetro rilasciato: l'istante in cui ripassa per il suo posto dopo il superamento. R4 B2 (b), fiducia **alta** (due clip a tempo vero, coincidono), sull'orologio corretto: arrivo a ~283–296 ms, colmo a ~367, ritorno a ~537–559.
  - `const val PlaneReleaseDelay: Float` — L'**attesa**, in frazione di [PlaneReleaseMillis]: 0,23 (124 ms). Fino a lì nessun vetro viaggia: il vetro si gonfia e si accende, chi sta per nascere si sporge appena ([PlaneReleaseCreep]), il donatore che deve spostarsi resta dov'è. Poi il donatore parte, e chi nasce da un donatore che non va da nessuna parte è rilasciato.
  - `const val PlaneReleaseLead: Float` — Di quanto un donatore **che si sposta** rilascia dopo l'attesa, in frazione di [PlaneReleaseMillis]: 0,08 se va lontano almeno quanto è alto, in proporzione se va meno lontano, zero se resta dov'è. Il donatore va per primo: R4 B2 (d), fiducia **media** (un render): sull'orologio corretto (t0 = f9030, ritorno a f9066, 1201 ms) il tondo lascia il blob a 0,31 della fase (0,23 + 0,08).
  - `const val PlaneReleaseStagger: Float` — Di quanto parte dopo ogni nato in più dello stesso donatore, dalla stessa parte, in frazione di [PlaneReleaseMillis]: **il più lontano parte per primo**, il più vicino per ultimo. R4 B2 (d), fiducia **medio-bassa** (una clip, un render, i lobi letti dal bordo alto): il tondo lascia il blob a 0,31 della fase e il triangolo a 0,40.
  - `const val PlaneReleaseSwing: Float` — Il mezzo periodo della molla di rilascio, in frazione del tempo **fra il rilascio e il ritorno**: 0,587, cioè `1 / (1 + 0,705)`, dove 0,705 è la frazione del mezzo periodo in cui una molla smorzata a [PlaneReleaseDamping] che parte ferma passa per l'arrivo. Così ogni vetro rilasciato ripassa per il suo posto **al ritorno** ([PlaneReleaseMillis]) e chi parte dopo è più svelto.
  - `const val PlaneReleaseDamping: Float` — Lo smorzamento della molla di rilascio: 0,6, cioè un superamento del 9,5% della corsa di ogni lato e un secondo passaggio, dall'altra parte, dello 0,9%. Chi è rilasciato viaggia **rigido**: il suo centro, dove sta il contenuto, supera il posto e torna col vetro.
  - `const val PlaneReleaseCreep: Float` — Quanto chi sta per nascere si sporge dal donatore **prima** del rilascio, in frazione della sua corsa: 0,11. R4 B2 (b), fiducia **media**: sull'orologio corretto il bordo di testa ha fatto l'1, il 4 e il 7% della corsa a 33, 67 e 100 ms, prima che la molla parta. È più di 0,08 (F3.12a) perché chi nasce ora parte più piccolo del donatore ([PlaneReleaseBud]): deve sporgersi di più per uscirne quanto esce in Apple.
  - `const val PlaneReleaseBud: Float` — Quanto è grande chi nasce quando esce dal donatore, in frazione della sua misura dentro il donatore: 0,8. Da lì **cresce mentre esce**, con la molla di rilascio, ed è della sua misura all'arrivo: è la «goccia con la vita» di Apple invece della pillola.
  - `const val PlaneReleaseTailFrom: Float` — Da quando lo scarto dall'arrivo di una **confluenza** comincia a spegnersi, in frazione di [PlaneReleaseMillis]: 0,8. Chi confluisce è dentro chi lo accoglie da prima: la sua coda non si vede, e finisce al riposo a vista. Chi nasce non la usa più (F3.12c): il suo vetro fa la coda di Apple finché lo scarto si vede ([PlaneSettlePx]).
  - `const val PlaneReleaseSwell: Float` — Il **rigonfiamento** di chi è rilasciato, al colmo, in unità del rigonfiamento del carattere del viaggio (`GlassLiquidJourney.swell`, 15% del lato corto): 1,27, cioè +19% di altezza. È un'oscillazione smorzata che parte quando il vetro passa per il suo posto: colmo ~95 ms dopo, ritorno ~285 ms dopo, poi un poco **sotto** la sua misura (−3%), e ferma quando lo scarto è sotto il mezzo pixel.
  - `const val PlaneReleaseWaist: Float` — La **vita** della goccia che nasce (giro di correzione di F3, scelta di Alessio del 17 novembre: «C corretta», la variante `SoftWaist` di `sessioni/esplora-vita/`): quanto è larga la vita del collo fra il donatore e chi nasce, al più stretto, in frazione di quella del collo scelto.
  - `const val PlaneReleaseWaistFrom: Float` — Quando la vita della goccia ([PlaneReleaseWaist]) comincia a stringersi, dopo il rilascio, in frazione del mezzo periodo della molla di rilascio: 0,31 (~200 ms in una nascita da un donatore fermo, quando il lobo è appena uscito). Sull'orologio di chi nasce: un fratello rilasciato dopo stringe dopo.
  - `const val PlaneReleaseWaistTo: Float` — Quando la vita della goccia è al suo più stretto ([PlaneReleaseWaist]), dopo il rilascio, in frazione del mezzo periodo della molla di rilascio: 0,93 (~350 ms in una nascita da un donatore fermo, fra il colmo e la rottura). Fra [PlaneReleaseWaistFrom] e qui con uno smoothstep; da qui alla rottura resta stretta.
  - `const val PlaneMergeDamping: Float` — Lo smorzamento di un vetro che confluisce in un vicino: 0,62, cioè un superamento dell'8,4%. R4 B2 (b), fiducia **alta**: 8,4% e 8,4% sul bordo lontano nelle due confluenze di classe B. Quel superamento non esce dall'altra parte del vicino: è la **spinta** che sposta chi accoglie, tutto intero (F3.12a; in F3.11 si gonfiava un lato).
  - `const val PlaneMergePeak: Float` — Quando cade il colmo di una confluenza, in frazione di [PlaneReleaseMillis]: 0,62 (335 ms). R4 B2 (b), fiducia **alta** (l'orologio della confluenza non cambia: t0 = f8963): il bordo lontano di chi confluisce fa il 7, il 23, il 40 e il 57% della corsa a 33, 67, 100 e 133 ms. Parte senza attesa: il collo c'è già al primo fotogramma.
  - `const val PlaneMergeCore: Float` — Quanto è grande **di traverso** chi confluisce quando è dentro chi lo accoglie, in frazione della sua misura dentro di lui: 0,66 (lungo la corsa resta quanto il vicino: il suo bordo lontano arriva al bordo del vicino coi tempi di Apple). Ci arriva con la molla della confluenza: **si rimpicciolisce mentre entra**, e finché il bordo lontano non è passato restano due lobi col collo.
  - `const val PlaneMergeCoreRegrowFrom: Float` — Da quando, in frazione del riposo a vista ([PlaneReleaseMillis]), il nucleo di chi è confluito torna della misura del vicino: 0,37 (200 ms), quando in Apple il lobo è già dentro (dopo ~170 ms non si vede più). Fino a [PlaneMergeCoreRegrowTo] (0,6, ~325 ms), con uno smoothstep.
  - `const val PlaneMergeCoreRegrowTo: Float` — Vedi [PlaneMergeCoreRegrowFrom]: 0,6 del riposo a vista.
  - `const val PlaneMergePush: Float` — La spinta più grande su chi accoglie un vetro che confluisce, in frazione del suo lato corto: 0,2. La spinta è l'8,4% della corsa di chi confluisce ([PlaneMergeDamping]); in Apple fa 0,16–0,18 D su un tondo (R4 B2 (b): il centro del tondo che resta va a +0,18 D a ~300 ms, poi **dall'altra parte** e torna piano fino a ~900). Il tetto è per i vetri che arrivano da lontano.
  - `const val PlaneMergeRebound: Float` — Di quanto chi accoglie torna **dall'altra parte** dopo la spinta, in frazione della spinta: 0,38. R4 B2 (b), fiducia **alta** (due clip): il centro del tondo va a +0,18 D a ~300 ms, a −0,068 D a ~590, ed è fermo a ~900. Fino a F3.12a la spinta finiva a 500 ms senza tornare indietro.
  - `const val PlaneMergeBreath: Float` — Il **rigonfiamento** di chi accoglie una confluenza, al colmo, in unità del rigonfiamento del carattere del viaggio: uno (+15% di altezza), a ~170 ms; poi un poco **sotto** la sua misura ([PlaneMergeBreathUnder]) e torna. Chi accoglie non prende il rigonfiamento comune (il suo colmo cadrebbe a ~240 ms): questo è il suo.
  - `const val PlaneMergeBreathUnder: Float` — Quanto chi accoglie va sotto la sua misura dopo il colmo, nelle stesse unità di [PlaneMergeBreath]: 0,31 (−4,7%).
  - `const val PlaneDonorRecoil: Float` — Di quanto il **donatore rincula** verso chi nasce da lui, in frazione del suo lato corto: 0,10. R4 B2 (b), fiducia **alta** (due clip a tempo vero): il bordo lontano del tondo che resta rientra di 0,071–0,075 D a 233–267 ms mentre il tondo si gonfia di qualche punto (+0,02–0,03 sul bordo): il tondo trasla di ~0,10.
  - `const val PlaneDonorRebound: Float` — Di quanto il donatore **rimbalza** dall'altra parte dopo il rinculo, in frazione del suo lato corto: 0,07. R4 B2 (b), fiducia **alta**: il bordo lontano va a +0,065–0,07 D a ~470–500 ms, col tondo un poco sotto la sua misura. Poi torna piano, con un ultimo passaggio di [PlaneDonorSettle] verso chi è nato, fermo a ~850 ms.
  - `const val PlaneDonorSettle: Float` — L'ultimo passaggio del donatore verso chi è nato, dopo il rimbalzo, in frazione del suo lato corto: 0,015. R4 B2 (b), fiducia **media**: il bordo lontano del tondo è 0,014 D dentro il suo posto finale a ~770 ms e ci torna a ~900.
  - `const val PlaneDonorBreath: Float` — Il **rigonfiamento** del donatore, al colmo, in unità del rigonfiamento del carattere del viaggio: 0,27 (+4% di altezza), a metà della salita di chi nasce; poi un poco sotto la sua misura ([PlaneDonorBreathUnder]) e torna. Il donatore non prende il rigonfiamento comune: **si gonfia poco**.
  - `const val PlaneDonorBreathUnder: Float` — Quanto il donatore va sotto la sua misura dopo il colmo, nelle stesse unità di [PlaneDonorBreath]: 0,17 (−2,5%).
  - `const val PlaneResizeMillis: Int` — Il **riposo a vista** di un cambio di larghezza, in millisecondi: 535, nello stesso senso di [PlaneContextMillis] (la molla del piano stirata perché la sua busta scenda a [PlaneRestEnvelope] qui: il bordo resta poi entro lo 0,5% della corsa).
  - `const val PlaneResizeWaitMillis: Int` — L'**attesa** di un cambio di larghezza, in millisecondi: 24. Il bordo che si chiude ha fatto il 2% della corsa a 33 ms e l'11% a 67 nelle due clip (anche nel render, che non ha un dito da alzare): una molla che parte subito è avanti di un fotogramma nei primi 100 ms (F4A.5b, fiducia **media**: la differenza è di un fotogramma). Un viaggio ripartito a metà non aspetta.
  - `const val PlaneResizeMinShare: Float` — Da quanto un vetro che cambia larghezza è un «cambio di larghezza», in frazione della sua altezza: 0,5 (un tasto che entra o esce da un gruppo è 1). Sotto, una scritta che cambia di qualche lettera, resta il tempo di [PlaneContextMillis]. Scelta di F4A.5b.
  - `const val PlaneResizeAnchorShare: Float` — Quanto può muoversi il bordo **fermo** di un cambio di larghezza, in frazione dell'altezza: 0,25. Un vetro che si sposta mentre cambia larghezza non è un gruppo ancorato al margine: viaggia col tempo di chi si sposta. Scelta di F4A.5b.
  - `const val PlaneResizeDrag: Float` — Il **trascinamento** di un cambio di larghezza, in frazione dell'altezza del vetro: 0,155. Tutto il vetro va nel verso del bordo che si muove, al colmo a ~300 ms, poi torna con un piccolo passaggio dall'altra parte ([PlaneResizeDragBack]) a ~550 ed è fermo a ~780.
  - `const val PlaneResizeDragShare: Float` — Il trascinamento non supera questa frazione della strada che fa il centro del vetro: 0,3. Scelta di F4A.5b.
  - `const val PlaneResizeDragBack: Float` — Di quanto il vetro trascinato passa **dall'altra parte** tornando, in frazione del trascinamento: 0,2. R4 B2 (a, b), fiducia **media** (il passaggio è di 2–4 punti di H, vicino alla risoluzione della clip): −0,018 H rispetto al posto di prima, −0,04 rispetto a quello d'arrivo, a ~550–600 ms.
  - `const val PlaneContentBreath: Float` — Il **respiro** di un vetro che resta della sua misura ma **cambia contenuto** (il tondo dell'elenco che diventa la lente, F4A.5b), al colmo, in unità del rigonfiamento del carattere del viaggio: 1,1 (+16,5% di altezza) a ~200 ms, poi un poco sotto la sua misura ([PlaneContentBreathUnder]) a ~490 e fermo a ~760.
  - `const val PlaneContentBreathUnder: Float` — Quanto il vetro che cambia contenuto va sotto la sua misura dopo il colmo, nelle unità di [PlaneContentBreath]: 0,17.
  - `const val PlaneResizeBreath: Float` — Il **rigonfiamento** di un vetro che cambia larghezza, al colmo, in unità del rigonfiamento del carattere del viaggio: 0,9 (+13,5% di altezza) a ~200 ms, poi un poco sotto la sua misura ([PlaneResizeBreathUnder]) a ~470 e fermo a ~650. È suo, al posto del comune: il comune ha il colmo al 45% del riposo a vista, che qui cadrebbe a ~240 ms.
  - `const val PlaneResizeBreathUnder: Float` — Quanto il vetro che cambia larghezza va sotto la sua misura, nelle unità di [PlaneResizeBreath]: 0,2 (−3%).

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
