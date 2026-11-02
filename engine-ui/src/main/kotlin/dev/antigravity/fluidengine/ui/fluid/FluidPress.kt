/*
 * La pressione dei pulsanti (3.0) e' LibrePods.
 *
 * Il comportamento di `FluidPressFeedback.Expand` — la molla di salita e di ritorno, la deriva
 * elastica col `tanh`, l'allungamento nel verso del dito, la scala massima dei tasti con testo e dei
 * tasti tondi, l'offset che torna da solo — e' portato dal codice di LibrePods, **GPL-3.0**:
 *
 *   me.kavishdevar.librepods.presentation.components.primitives
 *     StyledButton.kt      (righe 230-255: il blocco del layer; 326-389: molle, offset, gesto)
 *     StyledIconButton.kt  (righe 134-137 e 158-177: molle e blocco del layer; 194-244: gesto)
 *   https://github.com/librepods-org/librepods, branch `android/rewrite`, commit 3d4d270.
 *
 * Quei due file, a loro volta, usano le formule di `LiquidButton.kt` di Kyant (Apache-2.0), gia'
 * citate in LICENSES/AndroidLiquidGlass.md. Il testo completo di LibrePods e' in
 * LICENSES/LibrePods.md; il testo della licenza e' https://www.gnu.org/licenses/gpl-3.0.html.
 * Chi distribuisce un'app con questo file la distribuisce sotto GPL-3.0.
 *
 * Cosa abbiamo cambiato rispetto all'originale (le formule e le costanti sono le sue):
 *   - i nomi, il formato e la lingua dei commenti; la pressione e' uno stato condiviso
 *     (`FluidPressState`) che il click, il vetro e il bagliore leggono insieme, invece di tre
 *     `Animatable` dentro il composable di ogni pulsante;
 *   - la scala massima non e' un parametro del chiamante ma una proprieta' del feedback
 *     (`FluidPressFeedback.maxScale`): 0,10 per i tasti con testo e 0,5 per quelli tondi, che in
 *     LibrePods e' un numero scritto dentro `StyledIconButton`. Un tetto sulla crescita del lato
 *     lungo (16 dp) fa da `maxScale = 0,05` a larghezza piena, che LibrePods passa a mano;
 *   - le animazioni ridotte: crescita dimezzata, niente deriva ne' allungamento;
 *   - l'aptica non e' `LocalHapticFeedback` ma il vocabolario dell'engine, e ha un interruttore;
 *   - il click lungo (che LibrePods non ha), nel file `FluidButtonGesture.kt`.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrain
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainConfig
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHapticGrainConfig
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

// -------------------------------------------------------------------------------------------------
// La pressione, in un posto solo (2.11.0)
// -------------------------------------------------------------------------------------------------
//
// Prima ogni controllo aveva la sua: `fluidPressable` una scala in un `LaunchedEffect`, il vetro una
// molla dentro `GlassTouchHighlight`, lo slider un `animateFloatAsState`. Tre implementazioni, e lo
// stesso difetto in tutte e tre: il rilascio ripartiva da dove era arrivata la pressione.
//
// Qui ci sono tre pezzi, ognuno usato da tutti:
//
//  * [FluidPressMotion] — la corsa della pressione, da 0 a 1 e ritorno;
//  * [fluidPressTransform] — cosa diventa quella corsa, in scala e spostamento, per una taglia data;
//  * [FluidPressState] — la corsa piu' il dito: dove sta rispetto a dove e' sceso, e le tacche
//    aptiche mentre si sposta su un tasto premuto.
//
// Di chi e' il dito, dalla 3.0, lo dice un solo file: `FluidButtonGesture.kt`.

/**
 * Come un elemento risponde al dito.
 *
 * Due famiglie, e la differenza e' di sagoma, non di gusto:
 *
 *  * **[Expand]** — i pulsanti. Crescono sotto il dito **dal contatto**, lo seguono con una deriva
 *    elastica, si allungano nel verso in cui scorre e lo fanno sentire con la vibrazione di
 *    LibrePods. Un pulsante e' una cosa piccola e sola, e crescere e' il modo in cui dice "ti ho
 *    sentito" invece di "sono stato schiacciato";
 *  * **[yielding]** — le superfici: card, tessere, celle. Cedono di pochi centesimi, come sempre.
 *    Una card che crescesse invaderebbe le vicine, e una che seguisse il dito farebbe ballare la
 *    griglia in cui sta.
 *
 * @property pressedScale quanto cede una superficie a pressione piena (sotto 1). Ignorata da chi
 *   [expands]: la crescita di un pulsante e' [maxScale].
 * @property expands cresce invece di cedere.
 * @property followsFinger si sposta verso il dito (deriva elastica) e si allunga nel verso in cui scorre.
 * @property dragHaptics la grana aptica mentre il dito scorre sull'elemento premuto.
 * @property maxScale quanto cresce a pressione piena, in frazione: la scala e' `1 + maxScale`. 0,10
 *   per un tasto con testo e 0,5 per uno tondo, i numeri di LibrePods. Ignorata da chi non [expands].
 * @property longSideCapDp il tetto, in dp, della crescita del lato lungo di un tasto: oltre, la scala
 *   e' ridotta fino a stare dentro. Serve ai tasti larghi (un campo, un pulsante a tutta larghezza),
 *   dove il 10% sono trenta dp per lato: LibrePods li risolve passando 0,05 a mano a ogni tasto
 *   largo, qui lo fa la regola. Per il tasto tondo vale 24 dp in tutto, 12 per lato
 *   ([RoundLongSideCapDp]): fino a 48 dp non cambia niente (x1,5 intero), oltre cresce di quei 24 e
 *   non di piu' — un disco da 76 dp arriva a 100, non a 114, e non invade i vicini.
 */
@Immutable
data class FluidPressFeedback(
  val pressedScale: Float = DefaultYieldScale,
  val expands: Boolean = false,
  val followsFinger: Boolean = false,
  val dragHaptics: Boolean = false,
  val maxScale: Float = TextMaxScale,
  val longSideCapDp: Float = TextLongSideCapDp,
) {
  companion object {
    /** I pulsanti dell'engine con un testo dentro: crescono del 10%, seguono il dito, vibrano come LibrePods. */
    val Expand: FluidPressFeedback = FluidPressFeedback(
      pressedScale = 1f,
      expands = true,
      followsFinger = true,
      dragHaptics = true,
    )

    /**
     * I tasti tondi, solo icona: crescono del 50%, e' il carattere di `StyledIconButton` di
     * LibrePods (un tasto da 44 dp arriva a 66, e al picco della molla a 70). Per tutto il resto,
     * [Expand].
     *
     * **Con un tetto, che LibrePods non ha** (3.0): la crescita del diametro non supera 24 dp in
     * tutto, 12 per lato ([RoundLongSideCapDp]). Sotto i 48 dp, il bersaglio minimo del tocco, la
     * scala e' quella di LibrePods e non cambia di un pixel: 40 e 44 dp arrivano a 60 e 66. Sopra, il
     * 50% sarebbe troppo — un disco da 76 dp a 114 dp, 19 dp per lato, finisce sul vicino — e cresce
     * di 24 dp e basta: 54 dp a 78 (x1,44), 62 a 86 (x1,39), 76 a 100 (x1,32).
     */
    val ExpandRound: FluidPressFeedback = FluidPressFeedback(
      pressedScale = 1f,
      expands = true,
      followsFinger = true,
      dragHaptics = true,
      maxScale = RoundMaxScale,
      longSideCapDp = RoundLongSideCapDp,
    )

    /** Le superfici: cedono fino a [scale] e stanno ferme. Il comportamento di sempre. */
    fun yielding(scale: Float = DefaultYieldScale): FluidPressFeedback = FluidPressFeedback(pressedScale = scale)

    /** Nessuna trasformazione: per chi si disegna la pressione da se' (un vetro, una pastiglia). */
    val None: FluidPressFeedback = FluidPressFeedback(pressedScale = 1f)

    /** La cessione storica di `fluidPressable`. */
    const val DefaultYieldScale: Float = 0.974f
  }
}

/**
 * La corsa di una pressione: va in cima, ci resta finche' il dito c'e', torna a riposo.
 *
 * Per le **superfici** e con le animazioni ridotte c'e' una regola: **il rilascio non parte prima che
 * la corsa d'ingresso sia arrivata** ([waitsForArrival]). Un [release] che cade a meta' viene messo
 * da parte, e lo esegue la corsa stessa nel fotogramma in cui passa [ArrivalFraction] — quindi un tap
 * di 20 ms fa vedere la stessa pressione completa di uno di mezzo secondo, solo senza la pausa in
 * cima. Il ritorno eredita la velocita' della salita, quindi non c'e' nessuna giuntura fra le due.
 *
 * Per i **pulsanti** la regola non c'e' (dalla 3.0): come in LibrePods il rilascio parte subito, dal
 * valore e dalla velocita' in cui trova la salita. Un tocco breve fa crescere il tasto di quanto la
 * molla ha fatto in quel tempo, e il resto lo fa il rimbalzo: e' il "scatta" invece dell'"arriva".
 *
 * Il controllo si fa nel blocco per fotogramma di `animateTo`, non con un `snapshotFlow`: funziona
 * uguale con le animazioni a durata zero (il blocco arriva una volta, gia' in cima) e si prova sul
 * computer con un orologio di fotogrammi finto, senza un Recomposer che giri.
 *
 * Una nuova [press] durante il ritorno lo interrompe e risale da dove si trova: un doppio tap non
 * aspetta che il primo sia finito. Una [press] a dito gia' giu' non fa niente.
 */
@Stable
internal class FluidPressMotion(private val scope: CoroutineScope) {

  /** La molla d'ingresso. Per i pulsanti e' quella di LibrePods: 0,5 di smorzamento, 300 di rigidezza. */
  var pressSpec: AnimationSpec<Float> = FluidPressSprings.ExpandIn

  /** La molla del ritorno. Qui si puo' rimbalzare: il dito non c'e' piu', nessuno aspetta. */
  var releaseSpec: AnimationSpec<Float> = FluidPressSprings.ExpandOut

  /** Vedi la regola sopra. Vero di serie; falso per i pulsanti, che rilasciano subito come LibrePods. */
  var waitsForArrival: Boolean = true

  private val animatable = Animatable(0f, VisibilityThreshold)
  private var arrived = true
  private var generation = 0

  /** 0 a riposo, 1 a pressione piena; le molle possono uscire di poco da entrambi i lati. */
  val value: Float get() = animatable.value

  /** Il dito c'e'. Non osservabile: letto nel disegno e negli eventi, mai nella composizione. */
  var held: Boolean = false
    private set

  /** Vero mentre la corsa si muove: la finestra in cui chi la disegna e' davvero vivo. */
  val isRunning: Boolean get() = animatable.isRunning

  fun press() {
    // Un dito che e' gia' giu' (la pressione da tastiera che segue una pressione di puntatore, un
    // evento ripetuto) non riparte: la corsa e' in corso, o in cima.
    if (held) return
    held = true
    arrived = false
    val pass = ++generation
    scope.launch {
      animatable.animateTo(1f, pressSpec) {
        if (value >= ArrivalFraction) arrive(pass)
      }
      // Posata senza passare la soglia: succede solo a una molla che si ferma prima, ma la regola
      // e' che il rilascio parta comunque.
      arrive(pass)
    }
  }

  fun release() {
    if (!held) return
    held = false
    if (arrived || !waitsForArrival) relax()
    // Altrimenti ci pensa la corsa d'ingresso, nel fotogramma in cui arriva.
  }

  private fun arrive(pass: Int) {
    if (pass != generation || arrived) return
    arrived = true
    if (!held) relax()
  }

  private fun relax() {
    scope.launch { animatable.animateTo(0f, releaseSpec) }
  }

  companion object {
    /**
     * Quando la corsa d'ingresso si considera arrivata. Non 1: una molla ci arriva in un tempo
     * infinito, e a 0.96 la differenza non si vede ma il tap non aspetta la coda.
     */
    const val ArrivalFraction: Float = 0.96f

    private const val VisibilityThreshold = 0.001f
  }
}

/**
 * Le molle della pressione.
 *
 * **Per i pulsanti sono quelle di LibrePods**, `spring(0.5f, 300f, 0.001f)` sia in salita sia in
 * discesa (`StyledButton.kt:329`, `StyledIconButton.kt:134`): sottosmorzata, arriva in cima in circa
 * 0,2 s sorpassandola del 16% e, al rilascio, scende sotto il riposo di un 16% del percorso fatto
 * prima di tornarci — e' il "pop". Prima della 3.0 erano due molle nostre piu' corte (0,62 e 0,5,
 * 0,22 e 0,32 s di risposta): quella d'ingresso arrivava in meta' del tempo, e il tocco rapido
 * "arrivava" invece di "scattare".
 *
 * La deriva e l'allungamento tornano con una molla **criticamente smorzata** (1; 300), `:331`: non
 * rimbalzano, rimbalza solo la scala. Il suo punto di arrivo e' lo stesso del bagliore, che torna al
 * punto in cui il dito e' sceso.
 *
 * Le superfici tengono le molle che avevano: ingresso istantaneo e ritorno [FluidMotion.fluid].
 * Con le animazioni ridotte niente rimbalza: due molle criticamente smorzate, e la corsa resta
 * intera — a durata zero, un fotogramma premuto e uno rilasciato, ma mai uno saltato.
 */
internal object FluidPressSprings {
  val ExpandIn: AnimationSpec<Float> = spring(0.5f, 300f, 0.001f)
  val ExpandOut: AnimationSpec<Float> = spring(0.5f, 300f, 0.001f)
  val LeanReturn: AnimationSpec<Offset> = spring(1f, 300f, Offset.VisibilityThreshold)
  val YieldIn: AnimationSpec<Float> = spring(FluidMotion.DampingChrome, FluidMotion.ResponseInstant, 0.001f)
  val YieldOut: AnimationSpec<Float> = spring(FluidMotion.DampingFluid, FluidMotion.ResponseStandard, 0.001f)
  val ReducedIn: AnimationSpec<Float> = spring(FluidMotion.DampingChrome, FluidMotion.ResponseInstant, 0.001f)
  val ReducedOut: AnimationSpec<Float> = spring(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy, 0.001f)

  fun configure(motion: FluidPressMotion, feedback: FluidPressFeedback, reducedMotion: Boolean) {
    when {
      reducedMotion -> {
        motion.pressSpec = ReducedIn
        motion.releaseSpec = ReducedOut
      }
      feedback.expands -> {
        motion.pressSpec = ExpandIn
        motion.releaseSpec = ExpandOut
      }
      else -> {
        motion.pressSpec = YieldIn
        motion.releaseSpec = YieldOut
      }
    }
    // Il rilascio immediato e' dei pulsanti che si muovono davvero: con le animazioni ridotte la
    // pressione resta intera, come per le superfici.
    motion.waitsForArrival = reducedMotion || !feedback.expands
  }
}

/**
 * Lo stato di una pressione, condivisibile fra chi la guida e chi la disegna.
 *
 * Il caso per cui esiste come oggetto, e non solo dentro [fluidPressable], e' il pulsante di vetro:
 * il click sta in `fluidPressable`, la sagoma in [glassControlSurface], e se ognuno tenesse la sua
 * pressione il vetro crescerebbe con un orologio e il click con un altro. Con uno stato solo,
 * `fluidPressable(pressState = s)` lo guida e `glassControlSurface(pressState = s)` lo disegna:
 * un dito, una corsa, una serie di tacche.
 *
 * Il dito e' una **somma di spostamenti** ([lean]), non una posizione: e' quello che fa LibrePods
 * (`StyledButton.kt:385`, `offsetAnimation.snapTo(value + dragAmount)`), e significa che se la
 * pagina scorre sotto un dito fermo sul tasto il tasto lo segue lo stesso — il dito si e' mosso
 * sullo schermo anche se non si e' mosso sul tasto. Al rilascio la somma torna a zero con una molla
 * criticamente smorzata e si porta dietro il bagliore.
 *
 * Tutto quello che cambia durante il gesto e' letto nel disegno o nel layer, mai nella composizione:
 * un tasto premuto e trascinato non ricompone niente.
 */
@Stable
class FluidPressState internal constructor(
  private val scope: CoroutineScope,
  feedback: FluidPressFeedback,
) {
  internal val motion = FluidPressMotion(scope)
  internal var feedback: FluidPressFeedback = feedback
    private set
  internal var reducedMotion: Boolean = false
    private set

  /** L'aptica del tema, letta da chi guida il gesto. Aggiornata a ogni composizione. */
  internal var haptics: FluidHaptics = NoOpFluidHaptics
    private set
  private var grainConfig: FluidHapticGrainConfig = FluidHapticGrainConfig.Default
  private val grain = FluidHapticGrain()
  private var gestures = 0
  private var travel = Offset.Zero
  private val leanAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

  init {
    FluidPressSprings.configure(motion, feedback, reducedMotion = false)
  }

  /**
   * Dove e' sceso il dito, nelle coordinate dell'elemento. Stato, perche' lo legge il disegno del
   * bagliore e puo' cambiare mentre un ritorno e' ancora in corso.
   */
  internal var origin: Offset by mutableStateOf(Offset.Zero)
    private set

  /**
   * Quanto si e' mosso il dito dal contatto, in pixel dello schermo. Una molla: lo [touchMove] la
   * porta dove e' il dito, il [touchEnd] la riporta a zero. Letto solo in disegno e nel layer.
   */
  internal val lean: Offset get() = leanAnimation.value

  /**
   * Il centro del bagliore: dove il dito e' sceso piu' la strada che ha fatto. Torna al punto di
   * discesa insieme a [lean].
   */
  internal val glow: Offset get() = origin + lean

  /** Quanto e' premuto: 0 a riposo, 1 premuto. Le molle possono uscire di poco da entrambi i lati. */
  val progress: Float get() = motion.value

  internal fun update(
    feedback: FluidPressFeedback,
    haptics: FluidHaptics,
    reducedMotion: Boolean,
    grainConfig: FluidHapticGrainConfig = FluidHapticGrainConfig.Default,
  ) {
    if (feedback != this.feedback || reducedMotion != this.reducedMotion) {
      FluidPressSprings.configure(motion, feedback, reducedMotion)
    }
    this.feedback = feedback
    this.haptics = haptics
    this.reducedMotion = reducedMotion
    this.grainConfig = grainConfig
    grain.config = grainConfig
  }

  internal fun press() = motion.press()

  internal fun release() = motion.release()

  /** Il dito e' sceso in [position]. Da qui si misura la strada percorsa. */
  internal fun touchDown(position: Offset) {
    origin = position
    travel = Offset.Zero
    scope.launch { leanAnimation.snapTo(Offset.Zero) }
    // Un seme diverso a ogni gesto: due trascinamenti uguali non suonano lo stesso treno, come due
    // strofinii sulla stessa superficie.
    gestures++
    grain.restart(seed = gestures * 7919 + 1)
  }

  /**
   * Il dito si e' mosso di [positionChange] (in pixel dello schermo) all'istante [uptimeMillis]. La
   * grana arriva a pressione in corso, e per i pulsanti la pressione parte al contatto: dal primo
   * evento. Per [FluidHapticGrainStyle.LibrePods] e' un tick per ogni evento sopra la soglia.
   *
   * @param grainEnabled falso per un controllo che ha detto di vibrare gia' per conto suo.
   */
  internal fun touchMove(
    positionChange: Offset,
    uptimeMillis: Long,
    pxPerDp: Float,
    grainEnabled: Boolean = true,
  ) {
    travel += positionChange
    val target = travel
    scope.launch { leanAnimation.snapTo(target) }
    val intensity = grain.onMove(positionChange.getDistance(), pxPerDp, uptimeMillis)
    if (intensity != null && grainEnabled && motion.held && feedback.dragHaptics) {
      haptics.grain(intensity, grainConfig.style)
    }
  }

  /** Il dito se n'e' andato: la strada percorsa torna a zero con la molla critica. */
  internal fun touchEnd() {
    scope.launch { leanAnimation.animateTo(Offset.Zero, FluidPressSprings.LeanReturn) }
  }
}

/**
 * Uno stato di pressione per un controllo, da passare a chi lo guida e a chi lo disegna.
 * Vedi [FluidPressState].
 */
@Composable
fun rememberFluidPressState(feedback: FluidPressFeedback = FluidPressFeedback.Expand): FluidPressState {
  val scope = rememberCoroutineScope()
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val grainConfig = LocalFluidHapticGrainConfig.current
  val state = remember(scope) { FluidPressState(scope, feedback) }
  SideEffect { state.update(feedback, haptics, reducedMotion, grainConfig) }
  return state
}

/**
 * Segue il dito su un elemento premuto: deriva, punto luce, tacche. Non e' il percorso dei pulsanti
 * ([fluidButtonGesture], in `FluidButtonGesture.kt`, che ha anche le vibrazioni del contatto e del
 * rilascio, il click lungo e il possesso del dito): e' il vetro che segue il dito **da solo**, senza
 * un click proprio.
 *
 * Legge nel passaggio Final e non consuma niente, quindi non toglie il gesto a nessuno: una
 * superficie senza un click non e' qualcosa che tiene il dito, e **non si ferma se la pagina parte a
 * scorrere** — il vetro che sta sotto il dito continua a rispondere. Con [ownsPress] e' anche lui a
 * premere e rilasciare; senza, la pressione la decide chi guida l'elemento.
 */
internal fun Modifier.fluidPressTracking(
  state: FluidPressState,
  ownsPress: Boolean,
): Modifier = this.pointerInput(state, ownsPress) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
    state.touchDown(down.position)
    if (ownsPress) state.press()
    try {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) state.touchMove(change.positionChange(), change.uptimeMillis, density)
      }
    } finally {
      state.touchEnd()
      if (ownsPress) state.release()
    }
  }
}

/**
 * Cosa diventa una pressione, per un elemento di una certa taglia.
 *
 * Pura e senza Compose attorno, perche' e' il punto in cui un numero sbagliato si vede subito su
 * un telefono e mai in una revisione: ogni regola qui sotto e' provata in `FluidPressTest`.
 */
@Immutable
internal data class FluidPressTransform(
  val scaleX: Float,
  val scaleY: Float,
  val translationX: Float,
  val translationY: Float,
) {
  val isIdentity: Boolean
    get() = scaleX == 1f && scaleY == 1f && translationX == 0f && translationY == 0f

  companion object {
    val Identity = FluidPressTransform(1f, 1f, 0f, 0f)
  }
}

/**
 * La trasformazione di un elemento largo [width] e alto [height] (in px, con [dp] px per dp) a
 * pressione [progress], col dito spostato di [lean] da dove e' sceso. **Le formule sono quelle del
 * blocco del layer di LibrePods** (`StyledButton.kt:230-255`, `StyledIconButton.kt:158-177`), con
 * `m` il lato corto e `M` il lato lungo:
 *
 *  * **Crescita** (`expands`): scala uniforme `1 + g · progress`, con `g` da [fluidPressGrowth].
 *    Con le animazioni ridotte `g` e' dimezzata, non tolta: la risposta al dito resta, sparisce solo
 *    il movimento che ci sta attorno.
 *  * **Deriva** (`followsFinger`): `m · tanh(0,05 · lean / m)` su ciascun asse. Il guadagno iniziale
 *    e' il 5% e il tetto e' il lato corto: su un tasto da 48 dp la deriva e' 1 dp a 20 dp di
 *    trascinamento, 5 dp a 100, 14,5 dp a 300. **Non e' pesata dalla pressione**: la somma del dito
 *    ha la sua molla di ritorno, e deriva e crescita tornano ognuna col proprio passo.
 *  * **Allungamento**: `scala + 0,1 · |cos θ · lean.x / M| · min(w/h, 1)` in x e l'analogo in y, con
 *    `θ = atan2(lean.y, lean.x)`. Lineare e senza tetto: a 400 dp di trascinamento su un tasto da
 *    160 dp, un 25% in piu'. E' la parte che piu' "deforma" il tasto, ed e' di LibrePods.
 *
 * Senza animazioni ridotte, e solo per chi [FluidPressFeedback.followsFinger]. Una superficie che cede
 * ([FluidPressFeedback.yielding]) scala e sta ferma.
 */
internal fun fluidPressTransform(
  feedback: FluidPressFeedback,
  progress: Float,
  lean: Offset,
  width: Float,
  height: Float,
  dp: Float,
  reducedMotion: Boolean,
): FluidPressTransform {
  if (width <= 0f || height <= 0f) return FluidPressTransform.Identity
  val base = if (feedback.expands) {
    val growth = fluidPressGrowth(feedback, width, height, dp) * (if (reducedMotion) ReducedAmplitude else 1f)
    1f + growth * progress
  } else {
    1f + (feedback.pressedScale - 1f) * progress
  }
  if (!feedback.followsFinger || reducedMotion || lean == Offset.Zero) {
    return FluidPressTransform(base, base, 0f, 0f)
  }
  val minDimension = min(width, height)
  val maxDimension = max(width, height)
  val angle = atan2(lean.y, lean.x)
  return FluidPressTransform(
    scaleX = base + StretchGain * abs(cos(angle) * lean.x / maxDimension) * min(width / height, 1f),
    scaleY = base + StretchGain * abs(sin(angle) * lean.y / maxDimension) * min(height / width, 1f),
    translationX = minDimension * tanh(LeanGain * lean.x / minDimension),
    translationY = minDimension * tanh(LeanGain * lean.y / minDimension),
  )
}

/**
 * Di quanto cresce un pulsante a pressione piena, in frazione: la scala e' `1 + crescita`.
 *
 * E' [FluidPressFeedback.maxScale] — il numero di LibrePods — tranne che per un tasto largo: la
 * crescita del lato lungo non supera [FluidPressFeedback.longSideCapDp] dp in totale. Un tasto da
 * 120 dp cresce del 10% (12 dp), uno da 200 dp dell'8%, uno a tutta larghezza (320 dp) del 5%: che
 * e' il numero che LibrePods passa a mano a ogni suo tasto a larghezza piena. Senza il tetto il
 * 10% di un campo di 340 dp sono 34 dp, e invade i margini. Un tasto tondo ha il suo tetto,
 * [RoundLongSideCapDp]: x1,5 fino a 48 dp, poi 24 dp in tutto.
 */
internal fun fluidPressGrowth(feedback: FluidPressFeedback, width: Float, height: Float, dp: Float): Float {
  if (width <= 0f || height <= 0f) return 0f
  val long = max(width, height)
  return min(feedback.maxScale, feedback.longSideCapDp * dp / long).coerceAtLeast(0f)
}

/** La scala massima di un tasto con testo: `StyledButton.kt:95`. */
internal const val TextMaxScale = 0.10f

/** La scala massima di un tasto tondo: `StyledIconButton.kt:160`, `lerp(1f, 1.5f, progress)`. */
internal const val RoundMaxScale = 0.5f

/** Il tetto della crescita del lato lungo di un tasto con testo: vedi [fluidPressGrowth]. */
internal const val TextLongSideCapDp = 16f

/**
 * Il tetto della crescita del diametro di un tasto tondo: 24 dp in tutto, 12 per lato.
 *
 * Il numero non e' libero. **Fino a 48 dp la regola di LibrePods deve restare intera**: un tasto da
 * 44 dp cresce di 22 dp (x1,5) e uno da 48 di 24, quindi il tetto non puo' stare sotto i 24, e 24
 * e' il piu' piccolo che non cambia niente per i tasti che l'engine ha sempre avuto (40, 44, e il
 * bersaglio da 48). Un tetto piu' basso accorcerebbe anche loro; uno piu' alto lascerebbe correre i
 * dischi grandi. Con 24 c'e' pure il margine per l'arrotondamento dei pixel: 44 dp a densita' 2,625
 * sono 115,5 px, misurati 116, e il tetto in px (63) sta comunque ben sopra il 50% (58).
 *
 * Oltre i 48 dp la crescita **resta di 24 dp**, 12 per lato, qualunque sia il tasto: i dischi del
 * trasporto di un player (62 e 76 dp) arrivano a 86 e 100 dp invece di 93 e 114, e con 16 dp fra
 * loro non si toccano. Non e' un numero che garantisca niente a ogni distanza: a 10 dp dal vicino
 * restano 2 dp di sovrapposizione, a 8 dp quattro. Sono i 12 dp per lato che un tasto da 48 dp ha
 * sempre avuto, e che nessuno ha mai trovato troppi; un tasto che deve stare piu' stretto passa a
 * una taglia piu' piccola, o a un `FluidPressFeedback.ExpandRound.copy(longSideCapDp = ...)`.
 *
 * (Un tetto che lasci intero il 44 e tenga il 76 a 8 dp dal vicino non esiste: il 44, da solo, esce
 * di 11 dp per lato.)
 */
internal const val RoundLongSideCapDp = 24f

/** Con le animazioni ridotte un pulsante cresce la meta': risponde, ma senza muoversi molto. */
internal const val ReducedAmplitude = 0.5f

/** Il guadagno iniziale della deriva: `initialDerivative`, `StyledButton.kt:238`. */
internal const val LeanGain = 0.05f

/** Quanto si allunga per unita' di strada: `maxDragScale`, `StyledButton.kt:245`. */
internal const val StretchGain = 0.1f

/**
 * Non fa piu' niente (3.0). Fino alla 2.13 diceva ai pulsanti sotto di essere "del dito" gia' al
 * tocco invece che dopo 100 ms, per la chrome in cui non scorre niente. Dalla 3.0 ogni pulsante e'
 * premuto **al contatto** e il dito che scende su di lui e' suo fino al sollevamento
 * ([FluidPressGrip.HoldsFinger]), quindi non c'e' piu' un'attesa da azzerare ne' una lista da non
 * attraversare. Resta dichiarato perche' le app che lo davano alla loro chrome compilino: si puo'
 * togliere, e se ne andra'.
 */
val LocalFluidPressImmediate = compositionLocalOf { false }
