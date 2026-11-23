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
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

  // WWDC25 284, f5414..5421. Contorno esterno della capsula, un campione ogni 33,37 ms.
  // L'interpolazione monotona mantiene la pendenza ai campioni senza un rimbalzo al tondo.
  private fun tabContour(vararg samples: Float): TweenSpec<Float> = tween(
    durationMillis = ((samples.size - 1) * 1001f / 30f + 0.5f).toInt(),
    easing = Easing { fraction ->
      val position = fraction.coerceIn(0f, 1f) * samples.lastIndex
      val i = position.toInt().coerceAtMost(samples.lastIndex - 1)
      val u = position - i
      val delta = samples[i + 1] - samples[i]
      fun tangent(index: Int): Float {
        if (index == 0 || index == samples.lastIndex) return 0f
        val a = samples[index] - samples[index - 1]
        val b = samples[index + 1] - samples[index]
        return if (a * b > 0f) 2f * a * b / (a + b) else 0f
      }
      samples[i] + u * ((1f - u) * ((1f - u) * tangent(i) - u * tangent(i + 1)) + u * (3f - 2f * u) * delta)
    },
  )

  internal val FloatingTabFold = tabContour(0f, 0.05f, 0.52f, 0.70f, 0.86f, 0.94f, 0.98f, 1f)
  internal val TabBarPlayer = tabContour(0f, 0.02f, 0.30f, 0.46f, 0.65f, 0.80f, 0.91f, 1f)
  // Photos, f30302..30305. La tastiera copre il contorno nei fotogrammi successivi.
  internal val TabBarSearch = tabContour(0f, 0.31f, 0.59f, 0.94f, 1f)

  /** La pagina di ricerca dell’app può velarsi insieme alla barra. In Photos il primo frame conserva la foto. */
  val FloatingTabSearchPage: TweenSpec<Float> = tabContour(0f, 0f, 0.60f, 0.97f, 1f)

  /** La lente segue la pressione ottica: formata fra 133 e 200 ms (R1 T2). */
  internal fun tabLensScale(pressure: Float, pressedScale: Float): Float =
    1f + (pressedScale - 1f) * (pressure / 0.96f).coerceIn(0f, 1f)

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
  @Deprecated("Dalla 3.0 il contenuto arriva a corpo quasi formato: LiquidContentOpenFrom.", ReplaceWith("FluidMotion.LiquidContentOpenFrom"))
  const val MenuContentOpenFrom = 0.25f

  @Deprecated("Dalla 3.0: LiquidContentOpenTo.", ReplaceWith("FluidMotion.LiquidContentOpenTo"))
  const val MenuContentOpenTo = 0.75f

  /**
   * In chiusura le voci seguono la sagoma e si spengono fra questi due punti: da ~0,92 a ~0,12. Era
   * la finestra dell'apertura rovesciata e le voci sparivano in ~40 ms; cosi' restano leggibili per
   * 60-200 ms mentre il pannello si ritira.
   */
  @Deprecated("Dalla 3.0 il contenuto se ne va per primo: LiquidContentCloseFrom.", ReplaceWith("FluidMotion.LiquidContentCloseFrom"))
  const val MenuContentCloseFrom = 0.12f

  @Deprecated("Dalla 3.0: LiquidContentCloseTo.", ReplaceWith("FluidMotion.LiquidContentCloseTo"))
  const val MenuContentCloseTo = 0.92f

  /** La faccia dell'ancora (icona, etichetta) si spegne entro questo punto del viaggio. */
  const val MenuFaceTo = 0.35f

  /** La dissolvenza di uscita del menu (la goccia e il contenuto dopo una scelta), in millisecondi. */
  const val MenuFadeOutMillis = 280

  // --- La goccia (3.0, il vetro liquido) -------------------------------------------------------
  //
  // Una superficie di Fluid-physics che viaggia e' una goccia: un contorno di [DropPoints] punti, ognuno
  // su una molla verso il suo punto della sagoma a riposo, legati fra loro da una tensione del bordo.
  // Vicino all'ancora (il tasto, la riga, il dito) le molle sono dure e seguono subito; lontano sono
  // morbide e restano indietro: la coda. Scelta di Alessio sul banco (variante C), coi cursori di serie
  // e due ritocchi: il bordo poco teso (0,2 del banco) e la chiusura piu' lenta dell'apertura.
  // I numeri sono quelli del banco, in dp e secondi.

  /** Quanti punti ha il contorno della goccia, campionati per lunghezza d'arco. */
  const val DropPoints = 48

  /** La rigidezza della molla di un punto accanto all'ancora (1/s²): segue senza ritardo. */
  const val DropStiffnessNear = 1500f

  /** La rigidezza di un punto lontano dall'ancora: morbida, la coda resta indietro. */
  const val DropStiffnessFar = 320f

  /** Lo smorzamento delle molle dei punti, frazione del critico. */
  const val DropDamping = 0.72f

  /**
   * La tensione del bordo del banco con 48 punti (1/s²): quanto ogni punto tira verso la media dei due
   * vicini. Va per il quadrato del numero di punti; la goccia ne usa la frazione [DropEdgeTensionDefault].
   */
  const val DropEdgeTensionReference = 700f

  /**
   * La frazione di [DropEdgeTensionReference] di serie: 0,2 (140 con 48 punti), scelta di Alessio —
   * a 0,7 i bordi erano "esageratamente smussati". Con un bordo cosi' poco teso ogni punto segue quasi
   * da solo il suo punto a riposo, e gli angoli del pannello restano definiti anche in viaggio.
   */
  const val DropEdgeTensionDefault = 0.2f

  /** Fin dove l'ancora irrigidisce le molle (decadimento esponenziale), per un menu o un morph, in dp. */
  const val DropAnchorReachDp = 110f

  /** Lo stesso per una superficie trascinata, attorno al dito, in dp. */
  const val DropFingerReachDp = 70f

  /** Il passo d'integrazione delle molle (Euler semi-implicito), in secondi. */
  const val DropStepSeconds = 1f / 240f

  /** Il fotogramma piu' lungo che si integra (oltre e' uno stallo, non un moto), in secondi. */
  const val DropMaxFrameSeconds = 1f / 30f

  /** Sotto questo scarto, in pixel, fra ogni punto e il suo punto a riposo la goccia e' una lastra. */
  const val DropRestPx = 0.25f

  /** E sotto questa velocita' dei punti, in dp al secondo. */
  const val DropRestSpeedDp = 3f

  /**
   * La molla del viaggio d'andata della goccia: risposta ~0,28 s (k 500), poco sottosmorzata.
   *
   * Era ~0,42 s (k 224) fino alla 3.0.3, e in mano i menu «si chiudono alla velocita' giusta ma si
   * aprono lenti» (Alessio): misurato sull'emulatore a 30 fps, dal primo fotogramma che cambia al
   * pannello fermo e leggibile ci volevano ~570 ms, contro ~330 ms dal primo fotogramma della chiusura
   * al tasto di nuovo a riposo. Lo smorzamento resta (lo stesso oltrepasso, il "pop" della goccia):
   * cambia solo il tempo.
   */
  const val DropOpenStiffness = 500f

  /** La molla del ritorno: ~0,50 s (k 158), un filo piu' lenta dell'andata (Alessio: "troppo veloce"). */
  const val DropCloseStiffness = 158f

  /** Lo smorzamento delle due molle del viaggio. */
  const val DropJourneyDamping = 0.72f

  /** L'apertura di una goccia (un menu che nasce da un tasto, un morph). */
  fun <T> dropOpen(): SpringSpec<T> = spring(DropJourneyDamping, DropOpenStiffness)

  /** Il ritorno di una goccia nella sua ancora. */
  fun <T> dropClose(): SpringSpec<T> = spring(DropJourneyDamping, DropCloseStiffness)

  /**
   * Il gonfiore uniforme oltre il traguardo, per unita' d'oltrepasso della molla: `1 + 0,55·(m − 1)`
   * sulle misure; sotto lo zero lo stesso al contrario, attorno all'ancora.
   */
  const val DropInflationGain = 0.55f

  /** Il picco atteso di (m − 1) con [dropOpen]: `exp(−πζ/√(1 − ζ²))` per ζ = 0,72. */
  const val DropPeakOvershoot = 0.0384f

  /**
   * Il contenuto di un pannello di vetro liquido arriva fra questi due punti del viaggio. Erano 0,55 e
   * 0,9 fino alla 3.0.3: le voci si leggevano solo a pannello quasi fermo, ed era meta' della lentezza
   * che si sentiva in apertura. Restano ritagliate dentro il corpo (`travelClip`), e con la molla
   * d'andata della 3.0.4 sono piene ~90 ms dopo la partenza.
   */
  const val LiquidContentOpenFrom = 0.4f
  const val LiquidContentOpenTo = 0.75f

  /** E se ne va per primo fra questi due, al ritorno (da 0,94 a 0,62). */
  const val LiquidContentCloseFrom = 0.62f
  const val LiquidContentCloseTo = 0.94f

  /** La scala del contenuto mentre arriva o se ne va: `0,86 + 0,14 · opacita'`. */
  const val LiquidContentMinScale = 0.86f

  // Posizioni della molla d'apertura a circa 33, 67 e 83 ms. La risalita
  // continua all'inizio dell'espansione, come nei fotogrammi B1 sulla foto.
  internal const val MenuGatherEnd = 0.19f
  internal const val MenuExpandStart = 0.52f
  internal const val MenuTravelEnd = 0.66f
  internal const val MenuGroupGatherWidth = 0.60f
  internal const val MenuSeedDiameterFraction = 0.92f
  internal const val MenuGroupTravelBias = 0.55f
  internal const val MenuButtonGatherEnd = 0.058f
  internal const val MenuButtonFirstRise = 0.19f
  internal const val MenuButtonBloom = 0.36f
  internal const val MenuButtonBubble = 0.52f
  internal const val MenuButtonPanel = 0.82f
  internal const val MenuButtonRoundnessEnd = 0.94f
  internal const val MenuButtonLensMagnification = 1.42f
  internal const val MenuButtonLensInkPivot = 0.32f
  internal const val MenuLiquidCloseStiffness = 900f
  internal const val MenuContentFocusMillis = 270
  /**
   * Messa a fuoco del menu contestuale, il percorso usato da «Altro».
   * F5A.2 (Apple f13046 e seguenti): leggibile a 234–267 ms, nitido a 334 ms.
   * La scelta di Alessio del 23 novembre estende questo orologio ai popover.
   */
  internal const val ContextMenuFocusMillis = 334

  internal val ContextMenuFocusEasing = Easing { f ->
    1f - sqrt((1f - f).coerceIn(0f, 1f))
  }

  /**
   * I due bordi di «Altro» (F5A.2) danno la fase del popover: sale il bordo lontano, poi quello
   * vicino copre l'ultimo tratto fino alla posa a vista nelle riprese F6B.10.
   * Il solo bordo lontano arrivava presto: sul Tab S9 la card era già entro 2 px dalla posa
   * circa 240 ms dopo l'inizio del viaggio, prima che il menu avesse finito di posarsi.
   */
  internal val PopoverTravelOpen: TweenSpec<Float> = tween(
    durationMillis = FluidContextBirthProfile.VisibleSettleMillis.roundToInt(),
    easing = Easing { fraction ->
      val profile = FluidContextBirthProfile
      val elapsed = fraction * profile.VisibleSettleMillis
      val near = profile.Near.at(elapsed)
      val nearAtRest = profile.Near.at(profile.VisibleSettleMillis)
      val remaining = (near - nearAtRest).coerceAtLeast(0f)
      minOf(fluidContextBirthProgress(elapsed), 1f - remaining)
    },
  )
  internal const val MenuContentExitMillis = 185
  internal const val MenuReturnMillis = 233
  internal const val MenuReturnFocusMillis = 100
  internal const val MenuReturnInkMillis = 90
  // R4 B1 (a), alta: prima cede il bordo lontano, poi si raccoglie la bolla sul tasto.
  internal const val MenuReturnHold = 67f / MenuReturnMillis
  internal const val MenuReturnBubble = 150f / MenuReturnMillis
  internal const val MenuReturnFarEdge = 0.12f
  internal const val MenuReturnNearEdge = 0.02f
  internal const val MenuReturnWidthHold = 0.10f
  internal const val MenuReturnBubbleSize = 1.45f
  internal const val MenuReturnBubbleLift = 0.22f
  // R4 B1 (a), alta: un solo superamento, più evidente sul lato verticale lontano.
  internal const val MenuOvershootStartMillis = 92
  internal const val MenuOvershootPeakMillis = 125
  internal const val MenuOvershootWidthEndMillis = 183
  internal const val MenuOvershootEndMillis = 208
  internal const val MenuOvershootWidth = 0.034f
  internal const val MenuOvershootFarEdge = 0.06f
  internal const val MenuContentInkFull = 0.72f
  internal const val MenuContentFieldFade = 0.38f
  internal const val MenuContentFieldEnd = 0.85f
  internal const val MenuContentFirstAlpha = 0.28f
  internal const val MenuContentMaxBlurDp = 5f
  internal const val MenuGroupFaceProgressSpan = 0.98f
  // R4 B1 (b): il bagliore è visibile fra 33 e 67 ms. Intensità candidata, a occhio.
  internal const val MenuSpotStart = 0.12f
  internal const val MenuSpotCrest = 0.34f
  internal const val MenuSpotEnd = 0.55f
  internal const val MenuSpotPeak = 0.36f
  internal const val MenuSpotSide = 0.35f
  internal const val MenuSpotRadius = 0.46f

  /** La lente in moto: piena a questa velocita' del corpo, in dp al secondo. */
  const val LiquidLensFullSpeedDp = 700f

  /** Quanto in fretta la lente in moto segue la velocita' (costante di tempo, secondi). */
  const val LiquidLensFollowSeconds = 0.06f

  /**
   * Il gonfiore della riga sollevata del menu contestuale all'oltrepasso della molla, per asse: ~2% sui
   * fianchi (una riga larga quanto lo schermo non deve toccarne i bordi), ~5% in altezza.
   */
  const val LiftedSwellX = 0.25f
  const val LiftedSwellY = 0.6f

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

  // --- pm-f1/ga ---
  //
  // Liste lunghe e righe (Fase 1 di Pampa Mail, F1.2a): le righe che entrano ed escono una per una
  // (`fluidGroupedItems`), la riga che si scorre di lato (`fluidSwipeActions`) e la pillola «N nuove»
  // (`FluidJumpPill`). Nessun numero nuovo: sono i token di sempre con un nome, cosi' chi li cerca li
  // trova qui e il grep di `spring(`/`tween(` fuori da questo file resta vuoto.

  /**
   * Una riga di una lista che si sposta perche' un'altra e' entrata o uscita: critica (una lista non
   * rimbalza: e' un contenitore) e col passo delle liste (`ResponseStandard`).
   */
  fun groupedItemPlacement(): SpringSpec<IntOffset> = intOffset(DampingChrome, ResponseStandard)

  /** Quanto dura la comparsa di una riga nuova, in millisecondi: la dissolvenza d'ingresso di serie. */
  const val GroupedItemFadeInMillis: Int = 220

  /** Quanto dura l'uscita di una riga tolta, in millisecondi: piu' breve dell'ingresso, come ogni uscita. */
  const val GroupedItemFadeOutMillis: Int = 160

  /**
   * La riga che torna a posto dopo uno scorrimento di lato (annullato, o confermato su un'azione che non
   * la toglie): pixel, critica, rapida. Parte dalla velocita' del dito, come la pagina del back.
   */
  fun swipeSettle(): SpringSpec<Float> = pixels(DampingChrome, ResponseSnappy)

  /**
   * La riga che esce del tutto verso il lato, confermata un'azione che la toglie: la stessa molla del
   * ritorno, perche' e' lo stesso oggetto che finisce lo stesso gesto, solo verso l'altro capo.
   */
  fun swipeExit(): SpringSpec<Float> = pixels(DampingChrome, ResponseSnappy)

  /**
   * Il colore dell'azione che si accende quando la soglia e' superata: un lampo, perche' e' la risposta
   * a un momento (la soglia) e deve arrivare insieme al colpo dell'aptica, non dopo.
   */
  fun <T> swipeArm(): SpringSpec<T> = flick()

  /** La pillola «N nuove» che entra o esce: per offset e alfa, mai per scala (e' vetro). */
  fun jumpPillOffset(): SpringSpec<IntOffset> = intOffset(DampingChrome, ResponseSnappy)

  /** La dissolvenza della pillola «N nuove», in millisecondi, in entrata. */
  const val JumpPillFadeInMillis: Int = 180

  /** La dissolvenza della pillola «N nuove», in millisecondi, in uscita. */
  const val JumpPillFadeOutMillis: Int = 140
  // --- fine pm-f1/ga ---

  // --- pm-f1/ge ---
  // Il pannello laterale (`FluidDrawer`).

  /**
   * La risposta della molla del pannello laterale, in secondi: 0,34, quella delle pagine
   * (`FluidPageMotion.ResponsePageSeconds`). Il pannello e' la cosa piu' grande che si muove dopo una
   * pagina, arriva dallo stesso bordo e si chiude sotto lo stesso dito: con un'altra molla due gesti
   * gemelli avrebbero due tempi.
   */
  const val DrawerResponseSeconds: Double = 0.34

  /**
   * Il pannello laterale che entra, esce o torna dopo un rilascio, **in frazione di corsa** (0 chiuso,
   * 1 aperto): criticamente smorzata (un pannello non rimbalza contro il bordo), risposta
   * [DrawerResponseSeconds], ferma sotto un millesimo di corsa (meno di mezzo pixel su un pannello da
   * 320 dp). Parte dalla velocita' del dito, convertita in corsa al secondo, come la molla delle pagine.
   */
  val DrawerTravel: SpringSpec<Float> = spring(DampingChrome, stiffnessForResponse(DrawerResponseSeconds), 0.001f)
  // --- fine pm-f1/ge ---

  // --- pm-f1/gg1 ---
  // I grafici (`FluidCharts.kt`).

  /** Quanto dura la crescita d'ingresso di un grafico, in millisecondi: piu' lenta di un'uscita, sobria. */
  const val ChartEnterMillis: Int = 520

  /**
   * L'ingresso di un grafico: una frazione 0..1 per cui si moltiplicano barre, arco e tracciato. Una curva
   * senza rimbalzo ([EaseOut]) e non una molla: una barra che supera il suo valore e torna indietro
   * direbbe per un attimo un numero falso.
   */
  fun chartEnter(): FiniteAnimationSpec<Float> = tween(ChartEnterMillis, easing = EaseOut)

  /** Il segmento o la barra scelti che si evidenziano, e tornano: critica, col passo dei controlli. */
  fun chartSelect(): SpringSpec<Float> = spring(DampingChrome, ResponseSnappy)
  // --- fine pm-f1/gg1 ---

  // --- Il piano flottante (4.0, piano «iOS in Android», F3.2) ---
  //
  // I bilanci di tempo dei viaggi del vetro dentro un `FluidGlassContainer`. Non sono curve: il piano valuta sempre la
  // molla [standard] e ne cambia solo l'unità di tempo, così il viaggio arriva nel bilancio senza perdere la velocità
  // ereditata da un viaggio interrotto (`GlassPlaneJourney`). Erano i numeri provvisori di F3.1 in `GlassPlaneMotion`.
  //
  // **Il bilancio è il riposo a vista, non la fine dei conti** (F3.2c). Fino a F3.2 la molla si stirava perché arrivasse
  // a un centesimo di pixel in 300 ms: la coda numerica di una molla smorzata a 0,9 è più di metà del suo tempo, quindi la
  // sagoma si fermava a vista a 133 ms e il 39% della corsa passava nei primi 33 (rilievo di F3.3 sulla traccia
  // `contesto-capsule-curva`, contro i 270–330 ms di R4 B2). Ora la molla si stira perché la sua busta scenda a
  // [PlaneRestEnvelope] della corsa nel bilancio, cioè perché la sagoma entri per sempre nello 0,5% della corsa (la
  // tolleranza di riposo di `glassmotion.py curve`) a circa il bilancio; il viaggio finisce dopo, quando ogni lato è a
  // [PlaneSettlePx] dal suo arrivo, e quella coda non si vede.

  /**
   * Un cambio di contesto: pezzi che si spostano, cambiano forma, nascono da un vicino o vi confluiscono, in
   * millisecondi. R4 B2 (a, c), fiducia **alta**: 270–330 ms sul telefono; il centro della forchetta, che è anche
   * [DurationExpand]: un pezzo che diventa un altro e una destinazione che nasce dal tasto toccato sono lo stesso gesto.
   * È l'istante del **riposo a vista** (vedi la nota in testa a questa sezione), non la fine del viaggio.
   */
  const val PlaneContextMillis: Int = DurationExpand

  /**
   * Una comparsa per lente (`Materialize`), in millisecondi. R4 B3, fiducia **media**: la rifrazione sale e il
   * contorno si stringe in 430 ms, con l'opacità del corpo ferma; il simbolo si condensa da 2× a 1× nello stesso tempo.
   */
  const val PlaneAppearMillis: Int = 430

  /**
   * Una scomparsa per lente, in millisecondi. R4 B3, fiducia **media**: il simbolo se ne va in 130 ms, la rifrazione
   * va a zero entro 400, poi il contorno; più lenta della comparsa perché l'ultima cosa che sparisce è il bordo.
   */
  const val PlaneDisappearMillis: Int = 470

  /**
   * Di quanto è più largo il contorno di un vetro che compare all'inizio (e di uno che scompare alla fine). R4 B3,
   * fiducia **media**: 8–10% più largo; 1,09 è il centro della forchetta. **Scelta di Alessio, 2026-11-16**, sulla tavola
   * «scala iniziale della comparsa» di F3.3 (1,09 / 1,045 / 1,00): 1,09, come R4.
   */
  const val PlaneAppearScale: Float = 1.09f

  /**
   * La busta della molla del piano, in frazione della corsa di ogni lato e raggio, che il viaggio raggiunge nel suo
   * bilancio (F3.2c). Per una molla smorzata a 0,9 che parte ferma, la busta all'1,5% è il punto da cui la sagoma resta per
   * sempre entro lo 0,5% della corsa: a 300 ms di bilancio il riposo a vista cade a ~294 ms (modello di F3.2c, contro i
   * 270–330 di R4 B2). Una busta più bassa allungherebbe la parte che si vede, una più alta la accorcerebbe.
   */
  const val PlaneRestEnvelope: Float = 0.015f

  /**
   * La stessa soglia per `materialize` (0..1), assoluta: 0,2%. Più stretta della corsa di un lato, perché l'ultimo 5% di
   * `materialize` è il simbolo che si condensa da 2× a 1× (`glassMaterializeChannels`): all'1,5% sarebbe ancora 1,2×.
   */
  const val PlaneRestUnit: Float = 0.002f

  /**
   * Quando finisce un viaggio del piano: ogni lato e raggio a mezzo pixel dall'arrivo (la regola dell'engine per il
   * fermo, `FluidGlassPlaneTolerancePx`). Da lì la geometria è quella d'arrivo, al bit, e il palco lascia il vetro al
   * riposo senza un salto che si veda.
   */
  const val PlaneSettlePx: Float = 0.5f

  /** La stessa soglia per `materialize`: un ventesimo della [PlaneRestUnit]. */
  const val PlaneSettleUnit: Float = 0.0005f

  /**
   * Di quanto avanza al più l'orologio di un viaggio del piano in ognuno dei suoi primi [PlaneFirstSteps] fotogrammi, in
   * millisecondi (F3.8a): un fotogramma a 60 Hz. A schermo fermo un pannello a frequenza variabile gira a 30 Hz, e quando
   * un cambio arriva senza un tocco sale a 120 con un buco di 58–92 ms senza fotogrammi (S3, `framestats`: intervallo da
   * 33,3 a 8,3 ms): con l'orologio a muro il secondo fotogramma del viaggio cadeva già a un terzo del moto, e il collo
   * compariva pieno. Così il viaggio perde quel tempo invece di saltarlo: dura qualche decina di millisecondi in più, e
   * parte da fermo. A regime (60 o 120 Hz) non taglia niente.
   */
  const val PlaneFirstStepMillis: Int = 17

  /** Quanti fotogrammi dopo la partenza valgono il tetto di [PlaneFirstStepMillis]: quelli della salita del pannello. */
  const val PlaneFirstSteps: Int = 3

  /**
   * Il **rigonfiamento** del vetro in viaggio (F3.8a, punto 8) non sposta mai un lato di più di così, in dp. Quanto e
   * quando un pezzo si gonfia lo dice il carattere del viaggio (F3.9, `GlassLiquidJourney.swell`: il 15% del lato corto
   * al 45% della fase, misurato su tasti e barre, cioè 3,6 dp per lato su un tasto da 48 dp e 4,8 su una barra da 64).
   * Questo tetto non li tocca; ferma i pannelli grandi del piano (la card dell'assistente, lato corto di un centinaio di
   * dp e più), che con la stessa frazione uscirebbero di 8–15 dp per lato fino a toccare i vicini a 16 dp.
   */
  const val PlaneSwellMaxDp: Float = 6f

  // --- La coreografia della nascita e della confluenza (piano «iOS in Android», F3.11, F3.12a e F3.12c) ---
  //
  // Un vetro che **nasce da un vicino** non va dritto al suo posto con l'orologio di tutti: il vicino (il «donatore») va
  // per primo verso il suo arrivo, e chi nasce resta dentro di lui, lo segue, e dopo un ritardo ne viene **rilasciato**
  // con una molla che supera l'arrivo e torna. Chi **confluisce** in un vicino fa il verso opposto: ci entra subito,
  // dovunque il vicino stia andando, e lo spinge. Le misure sono in `artifacts/liquid-glass/sessioni/f3-11/misure.md`,
  // `…/f3-12a/misure.md` e `…/f3-12c/misure.md`: quattro clip a tempo vero dell'interfaccia ingrandita (R4 B2 (b), 4:49,
  // 4:51, 4:57 e 4:59, classe B) e il render rallentato «Select → Done» (R4 B2 (d), classe C).
  //
  // **I tempi sono quelli di Apple a tempo vero** (F3.12a, scelta di Alessio del 16 novembre sul video accanto ad Apple:
  // «Come Apple»), **sull'orologio corretto** dall'analisi fotogramma per fotogramma (F3.12c): l'ultimo fotogramma fermo
  // delle nascite è f8670 / f8913, uno prima di quello usato fino a F3.12a, quindi ogni misura di Apple è 33 ms più tardi.
  // Attesa, colmo e ritorno sono numeri separati, non una durata sola stirata. Chi nasce esce **piccolo** dal donatore e
  // cresce mentre esce ([PlaneReleaseBud]: la goccia con la vita), arriva **gonfio** e si sgonfia oltre la sua misura
  // ([PlaneReleaseSwell]), e il suo vetro fa la **coda** di Apple (un secondo piccolo passaggio, finché lo scarto è sopra il
  // mezzo pixel). Il donatore rincula davvero e si gonfia poco ([PlaneDonorRecoil], [PlaneDonorBreath]); chi confluisce si
  // rimpicciolisce mentre entra ([PlaneMergeCore]) e chi lo accoglie si gonfia, è spinto, e torna con la sua coda
  // ([PlaneMergeBreath], [PlaneMergeRebound]).

  /**
   * Il **riposo a vista** di una nascita o di una confluenza, in millisecondi: 540. È il **ritorno** del vetro rilasciato:
   * l'istante in cui ripassa per il suo posto dopo il superamento. R4 B2 (b), fiducia **alta** (due clip a tempo vero,
   * coincidono), sull'orologio corretto: arrivo a ~283–296 ms, colmo a ~367, ritorno a ~537–559. Su questo orologio
   * contano il contenuto, la luce, l'alone e il collo (`GlassPlaneJourney.settleMs`). Dopo, la **coda** di Apple (un
   * secondo passaggio di poco più dell'1% della corsa, il donatore e chi accoglie che tornano piano) tiene il palco finché
   * lo scarto si vede, fino a ~800–950 ms: non è più il bilancio a fermare il vetro. Fino a F3.12a era 500, sull'orologio
   * di un fotogramma prima e con la coda spenta a 500; fino a F3.11 era il bilancio di un cambio di contesto (300 ms), su
   * cui resta chi si sposta soltanto ([PlaneContextMillis]).
   */
  const val PlaneReleaseMillis: Int = 540

  /**
   * L'**attesa**, in frazione di [PlaneReleaseMillis]: 0,23 (124 ms). Fino a lì nessun vetro viaggia: il vetro si gonfia
   * e si accende, chi sta per nascere si sporge appena ([PlaneReleaseCreep]), il donatore che deve spostarsi resta dov'è.
   * Poi il donatore parte, e chi nasce da un donatore che non va da nessuna parte è rilasciato. R4 B2 (b), fiducia
   * **alta** sull'orologio corretto: il bordo di testa ha fatto l'1, il 4, il 7 e il 9,5% della strada a 33, 67, 100 e 133
   * ms, poi corre (20% a 167, 47% a 200). Fino a F3.12a era 0,17 (85 ms) e la molla partiva 60–70 ms prima di Apple.
   */
  const val PlaneReleaseDelay: Float = 0.23f

  /**
   * Di quanto un donatore **che si sposta** rilascia dopo l'attesa, in frazione di [PlaneReleaseMillis]: 0,08 se va
   * lontano almeno quanto è alto, in proporzione se va meno lontano, zero se resta dov'è. Il donatore va per primo: R4
   * B2 (d), fiducia **media** (un render): sull'orologio corretto (t0 = f9030, ritorno a f9066, 1201 ms) il tondo lascia il
   * blob a 0,31 della fase (0,23 + 0,08). Fino a F3.12a era 0,12, con l'attesa a 0,17.
   */
  const val PlaneReleaseLead: Float = 0.08f

  /**
   * Di quanto parte dopo ogni nato in più dello stesso donatore, dalla stessa parte, in frazione di
   * [PlaneReleaseMillis]: **il più lontano parte per primo**, il più vicino per ultimo. R4 B2 (d), fiducia
   * **medio-bassa** (una clip, un render, i lobi letti dal bordo alto): il tondo lascia il blob a 0,31 della fase e il
   * triangolo a 0,40.
   */
  const val PlaneReleaseStagger: Float = 0.09f

  /**
   * Il mezzo periodo della molla di rilascio, in frazione del tempo **fra il rilascio e il ritorno**: 0,587, cioè
   * `1 / (1 + 0,705)`, dove 0,705 è la frazione del mezzo periodo in cui una molla smorzata a [PlaneReleaseDamping] che
   * parte ferma passa per l'arrivo. Così ogni vetro rilasciato ripassa per il suo posto **al ritorno** ([PlaneReleaseMillis])
   * e chi parte dopo è più svelto. R4 B2 (b), fiducia **alta**, orologio corretto: rilascio a 124 ms, arrivo a 296, colmo
   * a 368, ritorno a 540 (Apple ~283–296, ~367, ~537–559).
   */
  const val PlaneReleaseSwing: Float = 0.587f

  /**
   * Lo smorzamento della molla di rilascio: 0,6, cioè un superamento del 9,5% della corsa di ogni lato e un secondo
   * passaggio, dall'altra parte, dello 0,9%. Chi è rilasciato viaggia **rigido**: il suo centro, dove sta il contenuto,
   * supera il posto e torna col vetro. R4 B2 (b), fiducia **alta** sul bordo di testa (13,2% nelle due nascite di classe
   * B: il 9,5% della molla più il rigonfiamento del pezzo nato, [PlaneReleaseSwell]) e **media** sul centro (i simboli
   * superano il loro posto di 0,17 D, il 10,5% della corsa, col rimbalzo di ritorno a un decimo: smorzamento 0,58). I
   * contenitori dell'engine non rimbalzano: questo è il pezzo che un vetro lascia andare, non un pannello.
   */
  const val PlaneReleaseDamping: Float = 0.6f

  /**
   * Quanto chi sta per nascere si sporge dal donatore **prima** del rilascio, in frazione della sua corsa: 0,11. R4 B2
   * (b), fiducia **media**: sull'orologio corretto il bordo di testa ha fatto l'1, il 4 e il 7% della corsa a 33, 67 e 100
   * ms, prima che la molla parta. È più di 0,08 (F3.12a) perché chi nasce ora parte più piccolo del donatore
   * ([PlaneReleaseBud]): deve sporgersi di più per uscirne quanto esce in Apple.
   */
  const val PlaneReleaseCreep: Float = 0.11f

  /**
   * Quanto è grande chi nasce quando esce dal donatore, in frazione della sua misura dentro il donatore: 0,8. Da lì
   * **cresce mentre esce**, con la molla di rilascio, ed è della sua misura all'arrivo: è la «goccia con la vita» di
   * Apple invece della pillola. R4 B2 (b), fiducia **media** (due clip, il contorno alto): il lobo che nasce è alto 0,87–0,91
   * H a 200 ms, 0,93–0,95 a 233, 0,90–0,96 a 267, quando il donatore è alto 1,02–1,05; fino a F3.12a chi nasce viaggiava a
   * misura piena sotto il donatore e l'insieme era una pillola fino a ~267 ms.
   */
  const val PlaneReleaseBud: Float = 0.8f

  /**
   * Da quando lo scarto dall'arrivo di una **confluenza** comincia a spegnersi, in frazione di [PlaneReleaseMillis]: 0,8.
   * Chi confluisce è dentro chi lo accoglie da prima: la sua coda non si vede, e finisce al riposo a vista. Chi nasce non
   * la usa più (F3.12c): il suo vetro fa la coda di Apple finché lo scarto si vede ([PlaneSettlePx]).
   */
  const val PlaneReleaseTailFrom: Float = 0.8f

  /**
   * Il **rigonfiamento** di chi è rilasciato, al colmo, in unità del rigonfiamento del carattere del viaggio
   * (`GlassLiquidJourney.swell`, 15% del lato corto): 1,27, cioè +19% di altezza. È un'oscillazione smorzata che parte
   * quando il vetro passa per il suo posto: colmo ~95 ms dopo, ritorno ~285 ms dopo, poi un poco **sotto** la sua misura
   * (−3%), e ferma quando lo scarto è sotto il mezzo pixel. Chi è rilasciato non prende il rigonfiamento comune: questo è
   * il suo. R4 B2 (b), fiducia **alta** (due clip, il contorno alto): il lobo nato è alto +6% a 300 ms, +14% a 334, **+19%
   * a 367–400**, +12% a 467, +4% a 534, −1,4% a 601, −3% a 667–700, −1% a 800. Fino a F3.12a era 0,9 sopra il comune, e
   * finiva al bilancio.
   */
  const val PlaneReleaseSwell: Float = 1.27f

  /**
   * La **vita** della goccia che nasce (giro di correzione di F3, scelta di Alessio del 17 novembre: «C corretta», la
   * variante `SoftWaist` di `sessioni/esplora-vita/`): quanto è larga la vita del collo fra il donatore e chi nasce, al
   * più stretto, in frazione di quella del collo scelto. Il collo resta quello (forma A, «spesso», esponente 1/2, rottura
   * a 0,38 del suo orologio, ~415 ms): questo restringe solo la **vita prima della rottura**, che in Apple è profonda
   * mentre il nato esce. Da [PlaneReleaseWaistFrom] a [PlaneReleaseWaistTo] la vita si stringe, poi resta stretta fino
   * alla rottura: **mai una riapertura** (il prototipo C tornava al collo di prima a 367 ms e la vita si riallargava da
   * 0,60 a 0,71 H, mentre in Apple continua a stringersi). R4 B2 (b), fiducia **media** (due clip, il contorno alto,
   * `esplora-vita/misure/`): la sezione della vita è 0,72–0,75 H a 267 ms, 0,63–0,67 a 300, 0,54–0,59 a 333, 0,42–0,48 a
   * 367; F3.12c era 0,89, 0,84, 0,86, 0,71.
   */
  const val PlaneReleaseWaist: Float = 0.64f

  /**
   * Quando la vita della goccia ([PlaneReleaseWaist]) comincia a stringersi, dopo il rilascio, in frazione del mezzo
   * periodo della molla di rilascio: 0,31 (~200 ms in una nascita da un donatore fermo, quando il lobo è appena uscito).
   * Sull'orologio di chi nasce: un fratello rilasciato dopo stringe dopo.
   */
  const val PlaneReleaseWaistFrom: Float = 0.31f

  /**
   * Quando la vita della goccia è al suo più stretto ([PlaneReleaseWaist]), dopo il rilascio, in frazione del mezzo
   * periodo della molla di rilascio: 0,93 (~350 ms in una nascita da un donatore fermo, fra il colmo e la rottura). Fra
   * [PlaneReleaseWaistFrom] e qui con uno smoothstep; da qui alla rottura resta stretta.
   */
  const val PlaneReleaseWaistTo: Float = 0.93f

  /**
   * Lo smorzamento di un vetro che confluisce in un vicino: 0,62, cioè un superamento dell'8,4%. R4 B2 (b), fiducia
   * **alta**: 8,4% e 8,4% sul bordo lontano nelle due confluenze di classe B. Quel superamento non esce dall'altra parte
   * del vicino: è la **spinta** che sposta chi accoglie, tutto intero (F3.12a; in F3.11 si gonfiava un lato).
   */
  const val PlaneMergeDamping: Float = 0.62f

  /**
   * Quando cade il colmo di una confluenza, in frazione di [PlaneReleaseMillis]: 0,62 (335 ms). R4 B2 (b), fiducia
   * **alta** (l'orologio della confluenza non cambia: t0 = f8963): il bordo lontano di chi confluisce fa il 7, il 23, il 40
   * e il 57% della corsa a 33, 67, 100 e 133 ms. Parte senza attesa: il collo c'è già al primo fotogramma.
   */
  const val PlaneMergePeak: Float = 0.62f

  /**
   * Quanto è grande **di traverso** chi confluisce quando è dentro chi lo accoglie, in frazione della sua misura dentro di
   * lui: 0,66 (lungo la corsa resta quanto il vicino: il suo bordo lontano arriva al bordo del vicino coi tempi di Apple).
   * Ci arriva con la molla della confluenza: **si rimpicciolisce mentre entra**, e finché il bordo lontano non è passato
   * restano due lobi col collo. R4 B2 (b), fiducia **media** (due clip, il contorno alto): il lobo che confluisce è alto
   * 0,97, 0,92, 0,86, 0,81 e 0,77 H a 33, 67, 100, 133 e 167 ms. Fino a F3.12a restava della sua altezza e l'insieme era
   * una pillola. Quando è dentro torna della misura del vicino ([PlaneMergeCoreRegrowFrom], [PlaneMergeCoreRegrowTo]).
   */
  const val PlaneMergeCore: Float = 0.66f

  /**
   * Da quando, in frazione del riposo a vista ([PlaneReleaseMillis]), il nucleo di chi è confluito torna della misura del
   * vicino: 0,37 (200 ms), quando in Apple il lobo è già dentro (dopo ~170 ms non si vede più). Fino a [PlaneMergeCoreRegrowTo]
   * (0,6, ~325 ms), con uno smoothstep. Senza, il nucleo restava più basso del vicino fino alla fine e l'unione morbida
   * faceva due «orecchie» sul fianco di chi accoglie, da ~300 a ~500 ms (la prima versione di F3.12c, sul banco): un vetro
   * dentro un altro della stessa misura non si vede, uno quasi della stessa misura sì.
   */
  const val PlaneMergeCoreRegrowFrom: Float = 0.37f

  /** Vedi [PlaneMergeCoreRegrowFrom]: 0,6 del riposo a vista. */
  const val PlaneMergeCoreRegrowTo: Float = 0.6f

  /**
   * La spinta più grande su chi accoglie un vetro che confluisce, in frazione del suo lato corto: 0,2. La spinta è
   * l'8,4% della corsa di chi confluisce ([PlaneMergeDamping]); in Apple fa 0,16–0,18 D su un tondo (R4 B2 (b): il centro
   * del tondo che resta va a +0,18 D a ~300 ms, poi **dall'altra parte** e torna piano fino a ~900). Il tetto è per i
   * vetri che arrivano da lontano.
   */
  const val PlaneMergePush: Float = 0.2f

  /**
   * Di quanto chi accoglie torna **dall'altra parte** dopo la spinta, in frazione della spinta: 0,38. R4 B2 (b), fiducia
   * **alta** (due clip): il centro del tondo va a +0,18 D a ~300 ms, a −0,068 D a ~590, ed è fermo a ~900. Fino a F3.12a la
   * spinta finiva a 500 ms senza tornare indietro.
   */
  const val PlaneMergeRebound: Float = 0.38f

  /**
   * Il **rigonfiamento** di chi accoglie una confluenza, al colmo, in unità del rigonfiamento del carattere del viaggio:
   * uno (+15% di altezza), a ~170 ms; poi un poco **sotto** la sua misura ([PlaneMergeBreathUnder]) e torna. Chi accoglie
   * non prende il rigonfiamento comune (il suo colmo cadrebbe a ~240 ms): questo è il suo. R4 B2 (b), fiducia **alta**
   * (due clip, il contorno alto al centro del tondo): +1, +5, +9, +13, **+15%** a 33, 67, 100, 133 e 167 ms, +10% a 233,
   * −1,4% a 300, **−4,7% a 367**, −2,4% a 500, zero a ~700. Fino a F3.12a un piano a +14% da 130 a 330 ms.
   */
  const val PlaneMergeBreath: Float = 1f

  /** Quanto chi accoglie va sotto la sua misura dopo il colmo, nelle stesse unità di [PlaneMergeBreath]: 0,31 (−4,7%). */
  const val PlaneMergeBreathUnder: Float = 0.31f

  /**
   * Di quanto il **donatore rincula** verso chi nasce da lui, in frazione del suo lato corto: 0,10. R4 B2 (b), fiducia
   * **alta** (due clip a tempo vero): il bordo lontano del tondo che resta rientra di 0,071–0,075 D a 233–267 ms mentre il
   * tondo si gonfia di qualche punto (+0,02–0,03 sul bordo): il tondo trasla di ~0,10. Fino a F3.12a era 0,077 col
   * rigonfiamento comune (+15%) che lo copriva tutto: sul banco il bordo lontano rientrava di appena 0,01 H.
   */
  const val PlaneDonorRecoil: Float = 0.10f

  /**
   * Di quanto il donatore **rimbalza** dall'altra parte dopo il rinculo, in frazione del suo lato corto: 0,07. R4 B2 (b),
   * fiducia **alta**: il bordo lontano va a +0,065–0,07 D a ~470–500 ms, col tondo un poco sotto la sua misura. Poi torna
   * piano, con un ultimo passaggio di [PlaneDonorSettle] verso chi è nato, fermo a ~850 ms.
   */
  const val PlaneDonorRebound: Float = 0.07f

  /**
   * L'ultimo passaggio del donatore verso chi è nato, dopo il rimbalzo, in frazione del suo lato corto: 0,015. R4 B2 (b),
   * fiducia **media**: il bordo lontano del tondo è 0,014 D dentro il suo posto finale a ~770 ms e ci torna a ~900.
   */
  const val PlaneDonorSettle: Float = 0.015f

  /**
   * Il **rigonfiamento** del donatore, al colmo, in unità del rigonfiamento del carattere del viaggio: 0,27 (+4% di
   * altezza), a metà della salita di chi nasce; poi un poco sotto la sua misura ([PlaneDonorBreathUnder]) e torna. Il
   * donatore non prende il rigonfiamento comune: **si gonfia poco**. R4 B2 (b), fiducia **media** (due clip, il contorno
   * alto al centro del tondo): +4–6% a 233–300 ms, −3% a 534–567, fermo a ~800 (l'analisi lo legge a ~1,03 H). Fino a
   * F3.12a prendeva il comune, +15%, che copriva il rinculo.
   */
  const val PlaneDonorBreath: Float = 0.27f

  /** Quanto il donatore va sotto la sua misura dopo il colmo, nelle stesse unità di [PlaneDonorBreath]: 0,17 (−2,5%). */
  const val PlaneDonorBreathUnder: Float = 0.17f

  // --- Il cambio di larghezza (piano «iOS in Android», F4A.5b) ---
  //
  // Un gruppo di una barra che perde o guadagna un tasto: la capsula cambia larghezza con un bordo fermo (quello del
  // margine) e uno che si muove. Fino a F4A.5 viaggiava con l'orologio di chi si sposta soltanto ([PlaneContextMillis]):
  // il tondo del «+» era formato a ~167 ms, in Apple a ~300–367. Le misure sono in
  // `artifacts/liquid-glass/sessioni/f4a-5b/misure.md`: le due clip **a tempo vero** di 219 (il telefono col dito a 4:44,
  // t0 = f8551, e il render a 4:48, t0 = f8633), che coincidono entro un fotogramma. Il bordo che si chiude ha **la molla
  // del piano**, più lenta e dopo un'attesa; **tutto il vetro è trascinato** nel verso di quel bordo e torna (il «+» va a
  // destra di 0,16 H e torna, con un piccolo passaggio dall'altra parte); il vetro **si gonfia** del 13–14% a ~200 ms, coi
  // tempi suoi ([PlaneResizeBreath]). La clip ingrandita di 16:02 (R4 B2 (c)) mostra lo stesso trascinamento, sul vetro
  // che si allarga, con un orologio ~1,35 volte più lento: non è a tempo vero.

  /**
   * Il **riposo a vista** di un cambio di larghezza, in millisecondi: 535, nello stesso senso di [PlaneContextMillis] (la
   * molla del piano stirata perché la sua busta scenda a [PlaneRestEnvelope] qui: il bordo resta poi entro lo 0,5% della
   * corsa). R4 B2 (a, b) rimisurato in F4A.5b, fiducia **alta** (due clip a tempo vero, coincidono): tolto il
   * trascinamento, il bordo che si chiude fa il 2, 11, 21, 35, 47, 61, 70, 81, 86, 91, 94, 97, 99% della corsa a 33…567
   * ms; la molla di serie (smorzamento 0,9) che parte dopo [PlaneResizeWaitMillis] e arriva a vista qui fa 21, 61, 85, 96%
   * a 100, 200, 300, 400 (scarto quadratico 0,011 sui diciassette campioni). Fino a F4A.5 la capsula viaggiava sui 300 ms
   * del contesto: 27, 71, 92, 99%, cioè ~100 ms avanti a metà corsa. I «270–330 ms» di R4 B2 (a) erano il tondo che
   * *sembra* formato e lo scambio delle icone, non il riposo del bordo. Con un cambio di larghezza nel viaggio, è
   * l'orologio comune di tutti i vetri che si muovono.
   */
  const val PlaneResizeMillis: Int = 535

  /**
   * L'**attesa** di un cambio di larghezza, in millisecondi: 24. Il bordo che si chiude ha fatto il 2% della corsa a 33 ms
   * e l'11% a 67 nelle due clip (anche nel render, che non ha un dito da alzare): una molla che parte subito è avanti di un
   * fotogramma nei primi 100 ms (F4A.5b, fiducia **media**: la differenza è di un fotogramma). Un viaggio ripartito a metà
   * non aspetta.
   */
  const val PlaneResizeWaitMillis: Int = 24

  /**
   * Da quanto un vetro che cambia larghezza è un «cambio di larghezza», in frazione della sua altezza: 0,5 (un tasto che
   * entra o esce da un gruppo è 1). Sotto, una scritta che cambia di qualche lettera, resta il tempo di
   * [PlaneContextMillis]. Scelta di F4A.5b.
   */
  const val PlaneResizeMinShare: Float = 0.5f

  /**
   * Quanto può muoversi il bordo **fermo** di un cambio di larghezza, in frazione dell'altezza: 0,25. Un vetro che si
   * sposta mentre cambia larghezza non è un gruppo ancorato al margine: viaggia col tempo di chi si sposta. Scelta di F4A.5b.
   */
  const val PlaneResizeAnchorShare: Float = 0.25f

  /**
   * Il **trascinamento** di un cambio di larghezza, in frazione dell'altezza del vetro: 0,155. Tutto il vetro va nel verso
   * del bordo che si muove, al colmo a ~300 ms, poi torna con un piccolo passaggio dall'altra parte
   * ([PlaneResizeDragBack]) a ~550 ed è fermo a ~780. R4 B2 (a, b) in F4A.5b, fiducia **alta** (due clip a tempo vero): il centro
   * del «+» va a +0,16 / +0,17 H a 300 ms e il bordo di destra a +0,20 / +0,22 H a 267 (con il rigonfiamento), poi
   * −0,02…−0,04 H a ~550–580. È ciò che R4 B2 (c) leggeva come «il bordo sinistro rientra di 0,19 H prima di allargarsi»:
   * il bordo non rientra, il vetro intero è tirato dal bordo che si allarga. Mai più di [PlaneResizeDragShare] della
   * strada che fa il centro del vetro: un cambio piccolo trascina poco.
   */
  const val PlaneResizeDrag: Float = 0.155f

  /** Il trascinamento non supera questa frazione della strada che fa il centro del vetro: 0,3. Scelta di F4A.5b. */
  const val PlaneResizeDragShare: Float = 0.3f

  /**
   * Di quanto il vetro trascinato passa **dall'altra parte** tornando, in frazione del trascinamento: 0,2. R4 B2 (a, b),
   * fiducia **media** (il passaggio è di 2–4 punti di H, vicino alla risoluzione della clip): −0,018 H rispetto al posto di
   * prima, −0,04 rispetto a quello d'arrivo, a ~550–600 ms.
   */
  const val PlaneResizeDragBack: Float = 0.2f

  /**
   * Il **respiro** di un vetro che resta della sua misura ma **cambia contenuto** (il tondo dell'elenco che diventa la
   * lente, F4A.5b), al colmo, in unità del rigonfiamento del carattere del viaggio: 1,1 (+16,5% di altezza) a ~200 ms, poi
   * un poco sotto la sua misura ([PlaneContentBreathUnder]) a ~490 e fermo a ~760. R4 B2 (a, b) in F4A.5b, fiducia
   * **alta** (due clip a tempo vero, il tondo di sinistra): +1,8 / 2,5, +5,3 / 6,6, +9,5 / 10,9, +13,2 / 14,7, **+16,7 /
   * 16,3%** a 67…200 ms, −2,2 / −2,9% a ~490. Vale per i vetri che dichiarano il loro contenuto (la barra), non per un
   * vetro qualsiasi: fino a F4A.5 il tondo era fermo e non entrava nel palco.
   */
  const val PlaneContentBreath: Float = 1.1f

  /** Quanto il vetro che cambia contenuto va sotto la sua misura dopo il colmo, nelle unità di [PlaneContentBreath]: 0,17. */
  const val PlaneContentBreathUnder: Float = 0.17f

  /**
   * Il **rigonfiamento** di un vetro che cambia larghezza, al colmo, in unità del rigonfiamento del carattere del viaggio:
   * 0,9 (+13,5% di altezza) a ~200 ms, poi un poco sotto la sua misura ([PlaneResizeBreathUnder]) a ~470 e fermo a ~650. È
   * suo, al posto del comune: il comune ha il colmo al 45% del riposo a vista, che qui cadrebbe a ~240 ms. R4 B2 (a, b) in
   * F4A.5b, fiducia **alta** (due clip a tempo vero, rispetto all'altezza d'arrivo): +1,3 / 1,6, +4,1 / 5,3, +8,5 / 9,2,
   * +10,1 / 12,5, **+13,2 / 13,8%** a 67…200 ms, +9,8 / 10,4 a 267, +3,1 / 4,2 a 334, **−3,0 / −2,6%** a ~467, zero a ~650.
   */
  const val PlaneResizeBreath: Float = 0.9f

  /** Quanto il vetro che cambia larghezza va sotto la sua misura, nelle unità di [PlaneResizeBreath]: 0,2 (−3%). */
  const val PlaneResizeBreathUnder: Float = 0.2f
}
