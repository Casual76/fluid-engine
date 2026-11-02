package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion tokens calibrated the way Apple parameterises its springs.
 *
 * SwiftUI describes a spring with `response` (the time the system takes to reach the target when
 * critically damped) and `dampingFraction`. Compose instead wants `stiffness` and `dampingRatio`.
 * Compose's solver derives the natural frequency as `sqrt(stiffness)` in rad/s, so the conversion
 * is exact:
 *
 * ```
 * omega     = 2 * PI / response
 * stiffness = omega * omega
 * ```
 *
 * Keeping the tokens expressed in `response` terms is what stops the motion from drifting: every
 * animation in the app finishes in a duration that is a deliberate multiple of the others, which is
 * the single biggest reason iOS motion reads as "one system" rather than a pile of tweens.
 */
object FluidMotion {

  private const val TwoPi = 6.283185307179586

  /** Converts an Apple-style `response` (seconds) into a Compose stiffness. */
  fun stiffnessForResponse(response: Double): Float {
    val omega = TwoPi / response
    return (omega * omega).toFloat()
  }

  /** ~0.20s. Micro-feedback that must land before the finger lifts. */
  val ResponseInstant = stiffnessForResponse(0.20)

  /** ~0.26s. The large title's handoff: tight enough to track a finger, slow enough to be read. */
  val ResponseTitle = stiffnessForResponse(0.26)

  /** ~0.30s. Press states, switches, chrome cross-fades. */
  val ResponseSnappy = stiffnessForResponse(0.30)

  /** ~0.38s. The workhorse: list changes, selection, most enter/exit. */
  val ResponseStandard = stiffnessForResponse(0.38)

  /** ~0.50s. Large surfaces: sheets, expanding cards, hero content. */
  val ResponseSmooth = stiffnessForResponse(0.50)

  /** ~0.62s. Full-screen or long-travel movement. */
  val ResponseGentle = stiffnessForResponse(0.62)

  /**
   * Critically damped. Nothing overshoots, which is what chrome (bars, backgrounds, opacity)
   * must do — a bouncing navigation bar is the single loudest "this is not premium" tell.
   */
  const val DampingChrome = 1f

  /** A whisper of settle. Reads as "physical" without looking springy. */
  const val DampingStandard = 0.9f

  /** Deliberate, visible settle for playful elements only. */
  const val DampingFluid = 0.74f

  // --- Float ----------------------------------------------------------------

  fun <T> instant(): SpringSpec<T> = spring(DampingChrome, ResponseInstant)

  fun <T> snappy(): SpringSpec<T> = spring(DampingChrome, ResponseSnappy)

  fun <T> standard(): SpringSpec<T> = spring(DampingStandard, ResponseStandard)

  fun <T> smooth(): SpringSpec<T> = spring(DampingStandard, ResponseSmooth)

  fun <T> gentle(): SpringSpec<T> = spring(DampingChrome, ResponseGentle)

  fun <T> fluid(): SpringSpec<T> = spring(DampingFluid, ResponseStandard)

  // --- Menus (3.0) ------------------------------------------------------------
  //
  // Un menu e' UN viaggio: l'ancora diventa il pannello e il pannello torna ancora, con la stessa
  // geometria nei due versi (`FluidMenuJourney`). Qui i numeri di quel viaggio, tutti insieme, perche'
  // il menu del morph, quello contestuale e l'avviso li condividono e non devono poter divergere.

  /** ~0.36s: piu' viva della molla standard, perche' un menu risponde a un dito che aspetta. */
  val MenuOpenStiffness = stiffnessForResponse(0.36)

  /**
   * Sottosmorzata di proposito: l'apertura oltrepassa il pannello e si posa — il "pop" del menu.
   * Il picco di (progress − 1) per questo smorzamento e' [MenuPeakOvershoot].
   */
  const val MenuOpenDamping = 0.62f

  /** L'apertura del menu: la molla sottosmorzata che porta l'ancora al pannello. */
  fun <T> menuOpen(): SpringSpec<T> = spring(MenuOpenDamping, MenuOpenStiffness)

  /** La chiusura: criticamente smorzata, nessun rimbalzo — un menu che si ritira non oscilla. */
  fun <T> menuClose(): SpringSpec<T> = spring(DampingChrome, ResponseStandard)

  /**
   * Quanto gonfia il pannello oltre il traguardo per ogni unita' di oltrepasso: lo stesso 0,6 di
   * `FluidPopoverWindowPopGain` nel modale, spostato fra i token perche' un numero solo decida il
   * "pop" di ogni finestra di menu.
   */
  const val MenuInflationGain = 0.6f

  /**
   * Il picco atteso di (progress − 1) con [menuOpen]: `exp(−πζ / √(1 − ζ²))` per ζ = 0,62, cioe'
   * 0,084. Sostituisce lo 0,15 che tarava il tetto sui fianchi per la rincorsa e la molla del
   * morph: la molla del menu e' un'altra, e un tetto tarato su un picco piu' alto lasciava il
   * pannello piu' stretto del margine che aveva.
   */
  const val MenuPeakOvershoot = 0.084f

  /** Il contenuto del pannello entra fra questi due punti del viaggio (progress), mai prima. */
  const val MenuContentOpenFrom = 0.25f
  const val MenuContentOpenTo = 0.75f

  /**
   * In chiusura le voci seguono la sagoma e si spengono fra questi due punti: da ~0,92 a ~0,12. Era
   * la finestra dell'apertura rovesciata e le voci sparivano in ~40 ms; cosi' restano leggibili per
   * 60-200 ms mentre il pannello si ritira.
   */
  const val MenuContentCloseFrom = 0.12f
  const val MenuContentCloseTo = 0.92f

  /** La faccia dell'ancora (icona, etichetta) si spegne entro questo punto del viaggio. */
  const val MenuFaceTo = 0.35f

  /** La dissolvenza di uscita del menu (la goccia e il contenuto dopo una scelta), in millisecondi. */
  const val MenuFadeOutMillis = 280

  // --- Typed variants -------------------------------------------------------
  //
  // Compose's default visibility threshold for a generic <T> is the Float one (0.01). Applied to
  // Dp / IntOffset / IntSize that threshold is far too small: the spring keeps ticking for
  // sub-pixel amounts long after the movement is visually over, which both wastes frames and, when
  // several such animations overlap, produces the low-grade shimmer that reads as "glitchy".

  fun dp(
    dampingRatio: Float = DampingStandard,
    stiffness: Float = ResponseStandard,
  ): SpringSpec<Dp> = spring(dampingRatio, stiffness, Dp.VisibilityThreshold)

  fun intOffset(
    dampingRatio: Float = DampingStandard,
    stiffness: Float = ResponseStandard,
  ): SpringSpec<IntOffset> = spring(dampingRatio, stiffness, IntOffset.VisibilityThreshold)

  fun intSize(
    dampingRatio: Float = DampingStandard,
    stiffness: Float = ResponseStandard,
  ): SpringSpec<IntSize> = spring(dampingRatio, stiffness, IntSize.VisibilityThreshold)

  // --- Duration-based -------------------------------------------------------
  //
  // Reserved for opacity, where a spring buys nothing: alpha has no momentum to preserve, and a
  // critically damped spring's tail makes a fade feel slower than its nominal duration.

  /** Apple's standard ease for content that enters. */
  val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

  /** Symmetric ease for cross-fades and colour changes. */
  val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

  /** For content leaving: starts immediately, so the exit never feels sticky. */
  val EaseIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

  /**
   * Decisive start, long calm settle, no overshoot. For whole pages.
   *
   * A page is the largest thing that moves in the app, and it is the one thing that must never
   * bounce: an overshoot on a full screen reads as the layout being loose rather than as energy.
   */
  val EaseEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

  /**
   * The same shape with about three percent of overshoot, for elements that are small enough to
   * carry it: a chip, an icon, a badge.
   *
   * Never for a whole page. At page size even a three percent overshoot is thirty pixels of the
   * layout growing past its size and coming back, which does not read as energy — it reads as the
   * page bouncing, and that is exactly the complaint this vocabulary exists to answer.
   *
   * Una curva e non una molla, per gli elementi piccoli che si animano a durata. **Non** per le
   * pagine: fino alla 2.13 il back predittivo cercava le transizioni di rotta sul `progress` di
   * sistema passato per curve come questa, e una pagina che corre 2,5 volte il dito a un quinto del
   * gesto non sta sotto il dito. Dalla 3.0 le pagine non si cercano: le muove `FluidPageHost`, un
   * orologio solo che il dito tiene pixel per pixel e che al rilascio passa a una molla con la
   * velocita' del dito (`FluidPageMotion.page()`).
   */
  val EaseSpringy: Easing = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1.14f)

  // --- Route timings ---------------------------------------------------------
  //
  // One budget per kind of change, so two different navigations never disagree about how long
  // "opening something" takes.

  /**
   * A destination growing out of the control that was touched.
   *
   * Three hundred, not the three hundred and forty it used to be. Both numbers are inside the range
   * a page transition is allowed to take, but the frames of a route change are the most expensive
   * the app ever draws, so every extra millisecond is a millisecond spent at the highest risk of
   * being dropped — and a transition that stutters reads as slow twice over.
   */
  const val DurationExpand: Int = 300

  /** The same movement undone. Slightly quicker: going back should feel like a release. */
  const val DurationCollapse: Int = 260

  /** Peer destinations: one ordered step sideways, along the axis the tab bar itself implies. */
  const val DurationPeer: Int = 280

  /**
   * A peer page arriving does not fade at all; only the one leaving does, and briefly.
   *
   * [DurationPeerFadeIn] survives for the one case with no direction to slide in from — a pair the
   * tab order cannot rank — where a fade is the only thing left to animate.
   */
  const val DurationPeerFadeIn: Int = 110
  const val DurationPeerFadeOut: Int = 150

  /**
   * How far a peer page travels, as a fraction of the width.
   *
   * Enough to be unmistakably directional and nowhere near a full-width wipe: a wipe says the two
   * pages are nested, which is the one thing tabs are not. Both pages move the same distance the
   * same way, so the arriving one keeps the departing one covered throughout.
   */
  const val PeerSlideFraction: Float = 0.13f

  /**
   * How long a covered page takes to give way underneath the one that replaced it.
   *
   * There is no matching duration for the arriving side: a hierarchical page never fades in at all.
   * It carries a rounded edge for as long as it is smaller than the screen, so it has no hard
   * rectangular boundary to disguise, and every millisecond a page spends translucent is a
   * millisecond in which two screens can be read through each other.
   *
   * Deprecato dalla 3.0: le pagine sono opache dal primo all'ultimo fotogramma, nessuna sfuma piu'.
   */
  @Deprecated(
    "Dalla 3.0 nessuna pagina sfuma: FluidNavHost/FluidPageHost muovono pagine opache. Le schede " +
      "usano DurationPeerFadeOut.",
  )
  const val DurationRouteFadeOut: Int = 240

  fun <T> fadeIn(durationMillis: Int = 220): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = EaseOut)

  fun <T> fadeOut(durationMillis: Int = 160): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = EaseIn)

  fun <T> crossFade(durationMillis: Int = 200): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = EaseInOut)

  /** Colour transitions read best on a plain ease — springs make tints wobble. */
  fun <T> color(durationMillis: Int = 240): AnimationSpec<T> =
    tween(durationMillis, easing = EaseInOut)

  // --- Fase 10: il movimento che stava scritto a mano (3.0) -----------------------------------------
  //
  // Molle, durate e curve che vivevano nei file dei componenti coi numeri in chiaro (`spring(1f, 300f,
  // 0.5f)`, un `tween(130, CubicBezierEasing(...))`, un `animate*AsState` senza spec) e che la matrice
  // di `docs/10-coerenza.md` segnava ✗3. Qui hanno un nome e gli STESSI valori di prima: nessun
  // movimento e' cambiato, e la pillola, il puck, lo slider e l'interruttore escono identici (R1).
  // Chi aggiunge un movimento nuovo lo mette qui, non nel suo file.

  /** Mezzo pixel: sotto questa distanza una molla che muove pixel e' ferma. */
  const val PixelThreshold: Float = 0.5f

  /**
   * Una molla per una distanza in pixel (un trascinamento che torna, uno scatto di scorrimento): i
   * valori dei token con la soglia di [PixelThreshold], perche' quella di serie (0,01) farebbe girare
   * la molla ancora a lungo dopo l'ultimo pixel visibile.
   */
  fun pixels(dampingRatio: Float = DampingStandard, stiffness: Float = ResponseStandard): SpringSpec<Float> =
    spring(dampingRatio, stiffness, PixelThreshold)

  /**
   * Una molla con smorzamento e rigidezza dati dai token (`Damping*`, `Response*`), per chi li sceglie
   * a tempo di esecuzione (attacco e rilascio, entrata e uscita) e non ha un nome suo.
   */
  fun <T> tuned(dampingRatio: Float, stiffness: Float): SpringSpec<T> = spring(dampingRatio, stiffness)

  /**
   * ~0,16 s, senza rimbalzo: la molla di serie di Compose (rigidezza 1500), che `animate*AsState` e
   * `Transition.animateFloat` usavano quando nessuno ne dava una. Per un valore che si accende o si
   * spegne in un lampo (un lavaggio di selezione, l'alfa di una lente). Un nome, non un default implicito.
   */
  fun <T> flick(): SpringSpec<T> = spring(DampingChrome, Spring.StiffnessMedium)

  // Barra a pillola: il puck, la piegatura e le bande.

  /** Il ritorno della barra flottante dopo un tiro del dito sul puck: (1, 300), mezzo pixel. */
  val TabTugReturn: SpringSpec<Float> = spring(DampingChrome, 300f, PixelThreshold)

  /** La durata della piegatura "gommosa" fra barra in linea ed estesa, e del puck che arriva con lei. */
  const val GooeyMillis: Int = 300

  /** Il picco di sfocatura della piegatura: 0 all'inizio, 1 a meta' (curva `FastOutSlowIn`), 0 alla fine. */
  val GooeyPulse: KeyframesSpec<Float> = keyframes {
    durationMillis = GooeyMillis
    0f at 0
    1f atFraction 0.5f using FastOutSlowInEasing
    0f at GooeyMillis
  }

  /** Il puck che entra con la barra che si apre: lungo quanto la piegatura ([GooeyMillis]), `FastOutSlowIn`. */
  val PuckEnter: TweenSpec<Float> = tween(GooeyMillis, easing = FastOutSlowInEasing)

  /** Il cambio fra barra in linea ed estesa: la dissolvenza di serie di `AnimatedContent` (rigidezza 400, critica). */
  val FoldCrossFade: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

  /** Quanto il puck resta solo a viaggiare prima che la pagina scelta cominci a costruirsi, in millisecondi. */
  const val TabPuckLeadMillis: Long = 160L

  /** La barra ripiegata che si apre e si chiude: (0,9, rigidezza 400), un filo di assestamento. */
  val TabBarFold: SpringSpec<Float> = spring(0.9f, Spring.StiffnessMediumLow)

  /** Durata e finestra (frazioni della durata) dell'entrata di una scheda: invisibile fino a meta', piena a 0,8. */
  const val TabEnterMillis: Int = 150
  const val TabEnterStartFraction: Float = 0.5f
  const val TabEnterEndFraction: Float = 0.8f

  /** L'alfa di una scheda della barra: sale fra [TabEnterStartFraction] e [TabEnterEndFraction] se entra, lineare se esce. */
  fun tabEnterAlpha(entering: Boolean): FiniteAnimationSpec<Float> = keyframes {
    durationMillis = TabEnterMillis
    if (entering) {
      0f atFraction TabEnterStartFraction using FastOutSlowInEasing
      1f atFraction TabEnterEndFraction
    }
  }

  /** La sfocatura di una scheda che entra: [blurPx] fino a [TabEnterStartFraction], zero a [TabEnterEndFraction]. */
  fun tabEnterBlur(entering: Boolean, blurPx: Float): FiniteAnimationSpec<Float> = keyframes {
    durationMillis = TabEnterMillis
    if (entering) {
      blurPx atFraction TabEnterStartFraction using FastOutSlowInEasing
      0f atFraction TabEnterEndFraction
    }
  }

  // Foglio e pop-up (FluidGlassModal).

  /** Il foglio trascinato che torna al suo posto: (0,9, rigidezza 500) dalla velocita' del dito, mezzo pixel. */
  val SheetSettle: SpringSpec<Float> = spring(0.9f, 500f, PixelThreshold)

  /** L'opacita' del pop-up in entrata: arriva ben prima della geometria, cosi' il contenuto e' gia' leggibile. */
  const val PopoverFadeInMillis: Int = 110

  /** In uscita dura di piu' che in entrata: l'occhio segue il pannello che torna nella riga, e uno scrim orfano non resta. */
  const val PopoverFadeOutMillis: Int = 240

  /** La dissolvenza di chiusura della finestra di Fluid-physics: la sagoma deve atterrare sulla riga prima che finisca. */
  const val PopoverWindowFadeOutMillis: Int = 280

  /** Fin dove arriva la rincorsa della finestra ancorata (frazione del viaggio): ancora in accelerazione, poi la molla. */
  const val PopoverWindowRunUpEnd: Float = 0.92f

  /**
   * La rincorsa: 130 ms tutti in ease-in (`CubicBezier(0,55; 0; 0,85; 0,55)`), finisce ripida per
   * passare la velocita' alla molla. Il moto, detto parola per parola: "accelerazione continua
   * finche' non raggiunge il bordo, e allora inizia a rallentare, oltre, fino a ritornare alla
   * posizione finale" — una rincorsa che copre il novanta per cento del viaggio ancora in
   * accelerazione ([PopoverWindowRunUpEnd]) e una molla che ne eredita la velocita': l'oltrepasso e'
   * slancio vero, non un calcio dato alla fine. La prima rincorsa (180 ms all'80%, easing molle) era
   * stata bocciata come "lineare, lenta": il difetto era il ritmo, non l'idea.
   */
  val PopoverWindowRunUp: TweenSpec<Float> =
    tween(130, easing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.55f))

  /** L'assestamento della finestra dopo la rincorsa, orizzontale: (0,55, 300). Piu' morbida di Y, per lo squash. */
  val PopoverWindowSettleX: SpringSpec<Float> = spring(0.55f, 300f, 0.001f)

  /** L'assestamento della finestra dopo la rincorsa, verticale: (0,62, 300). */
  val PopoverWindowSettleY: SpringSpec<Float> = spring(0.62f, 300f, 0.001f)

  /** L'apertura del pop-up classico, orizzontale: (0,62, 260). Due molle diverse danno lo squash, due uguali un pallone. */
  val PopoverOpenX: SpringSpec<Float> = spring(0.62f, 260f, 0.001f)

  /** L'apertura del pop-up classico, verticale: (0,74, 260). */
  val PopoverOpenY: SpringSpec<Float> = spring(0.74f, 260f, 0.001f)

  /**
   * La chiusura della finestra di Fluid-physics: critica e rigida (620), piu' di quella classica
   * ([PopoverExit]), perche' la sagoma deve ATTERRARE sulla riga prima che la dissolvenza
   * ([PopoverWindowFadeOutMillis]) finisca: l'ordine delle due cose rende la chiusura un gesto solo
   * invece di una sparizione per aria.
   */
  val PopoverWindowExit: SpringSpec<Float> = spring(DampingChrome, 620f, 0.001f)

  /** La chiusura del pop-up classico: critica (420), un'uscita che rimbalza sembra un pannello cacciato dallo schermo. */
  val PopoverExit: SpringSpec<Float> = spring(DampingChrome, 420f, 0.001f)

  // La goccia di Kyant e il bagliore del dito (glass/interaction).

  /** Il valore della goccia (la sua posizione sulla pista): (1, 1000), critica e rigida; la soglia la da' la pista. */
  fun dropValue(visibilityThreshold: Float): SpringSpec<Float> = spring(DampingChrome, 1000f, visibilityThreshold)

  /** La velocita' tracciata della goccia, da cui nasce lo squash: (smorzamento dato, 300). Quello di Kyant e' 0,5. */
  fun dropVelocity(dampingRatio: Float, visibilityThreshold: Float): SpringSpec<Float> =
    spring(dampingRatio, 300f, visibilityThreshold)

  /** La pressione della goccia, che la fa lente: (1, 1000), critica e rigida. */
  val DropPress: SpringSpec<Float> = spring(DampingChrome, 1000f, 0.001f)

  /** La scala X della goccia: (0,6, 250). Piu' morbida di Y: e' questa differenza che fa lo squash. */
  val DropScaleX: SpringSpec<Float> = spring(0.6f, 250f, 0.001f)

  /** La scala Y della goccia: (0,7, 250). */
  val DropScaleY: SpringSpec<Float> = spring(0.7f, 250f, 0.001f)

  /** Il punto di luce che segue il dito e torna al punto di partenza: (0,5, 300). */
  val GlowPosition: SpringSpec<Offset> = spring(0.5f, 300f, Offset.VisibilityThreshold)

  /** L'accensione e lo spegnimento della luce del dito: (0,5, 300), la stessa molla dei tasti di LibrePods. */
  val GlowPress: SpringSpec<Float> = spring(0.5f, 300f, 0.001f)

  // Il respiro e i giri che si ripetono.

  /** Meta' di un respiro (l'andata; il ritorno e' uguale): la durata che quattro app su cinque avevano scelto, e quella dello spinner. */
  const val PulseMillis: Int = 900

  /** Il punto basso del respiro di un segno pieno (un pallino): abbastanza basso da vedersi, abbastanza alto da non sparire sul chiaro. */
  const val PulseLowAlpha: Float = 0.35f

  /** Il punto basso del respiro di un riempimento (il passo in corso): piu' alto di [PulseLowAlpha], a 0,35 sembrerebbe il passo da fare. */
  const val PulseFillLowAlpha: Float = 0.55f

  /**
   * Il respiro: un'andata e ritorno di [periodMillis] per fase, che parte da [startOffsetMillis]
   * dentro il ciclo (l'orologio, cosi' tutti i pallini in scena battono insieme).
   */
  fun pulse(periodMillis: Int = PulseMillis, startOffsetMillis: Int = 0): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(
      animation = tween(durationMillis = periodMillis, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(startOffsetMillis, StartOffsetType.FastForward),
    )

  /** Il giro dello spinner a raggi: un'intera corona in 900 ms, lineare. */
  const val SpinnerMillis: Int = 900

  /** Il segmento della barra indeterminata: 1100 ms da un bordo all'altro. */
  const val IndeterminateMillis: Int = 1100

  /**
   * Un giro che si ripete da capo ([RepeatMode.Restart]): [durationMillis] di moto, poi [restMillis]
   * di pausa (il ritardo di `tween`), poi ancora. Lineare di serie: un giro che si ripete non ha
   * un'accelerazione da raccontare, salvo dove la chiede lui (la barra, il riflesso).
   */
  fun loop(durationMillis: Int, easing: Easing = LinearEasing, restMillis: Int = 0): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(
      animation = tween(durationMillis = durationMillis, delayMillis = restMillis, easing = easing),
      repeatMode = RepeatMode.Restart,
    )

  // --- Scorrimento rapido e scrubber (3.0, fase 12c) -------------------------------------------------
  //
  // Due durate di *attesa*, non di movimento: quanto un controllo aspetta prima di cambiare idea. Stanno
  // qui perche' un numero di tempo nel file di un componente e' esattamente cio' che la fase 10 ha tolto.

  /**
   * Quanto la maniglia dello scorrimento rapido resta in vista dopo l'ultimo movimento della lista, in
   * millisecondi. Il valore che le due app di partenza avevano scelto (1,4 s in MyCloud, mezzo secondo
   * di dissolvenza in Fluidify), un filo sotto; prima era una costante privata di `FluidFastScroller`.
   */
  const val FastScrollerHideMillis: Long = 1_200L

  /**
   * Quanto un `FluidScrubber` tiene la maniglia dove e' stata lasciata, aspettando che la posizione
   * che arriva da fuori la raggiunga, in millisecondi.
   *
   * Un salto di un lettore non e' istantaneo: fra il rilascio e la prima posizione nuova passa il
   * tempo di un seek (decine di millisecondi in locale, qualche secondo su una rete che bufferizza), e
   * in quel tempo la posizione che arriva e' ancora la vecchia. Senza un fermo la maniglia tornerebbe
   * indietro per un attimo e poi ripartirebbe in avanti. Il fermo finisce da solo quando la posizione
   * ha raggiunto il punto, e questo e' il tetto: un seek che l'app ha rifiutato non deve lasciare la
   * maniglia dove nessuno sta riproducendo. Tre secondi sono un numero di partenza, da giudicare in
   * mano con un seek lento.
   */
  const val ScrubberHoldMillis: Long = 3_000L
}
