package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.haptics.FluidHapticDetents
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

// -------------------------------------------------------------------------------------------------
// La pressione, in un posto solo (2.11.0)
// -------------------------------------------------------------------------------------------------
//
// Prima ogni controllo aveva la sua: `fluidPressable` una scala in un `LaunchedEffect`, il vetro una
// molla dentro `GlassTouchHighlight`, lo slider un `animateFloatAsState`. Tre implementazioni, e lo
// stesso difetto in tutte e tre: il rilascio ripartiva da dove era arrivata la pressione. Un tap vero
// dura 30-60 ms, meno della corsa d'ingresso di qualsiasi molla, quindi il tasto si muoveva di un
// decimo e tornava indietro — e dentro una lista era peggio, perche' `clickable` ritarda la pressione
// di 100 ms per non accendere tutto quello che uno scorrimento attraversa, e un tap piu' rapido di
// cosi' consegnava pressione e rilascio nello stesso fotogramma: nessuna animazione, del tutto.
//
// Qui ci sono tre pezzi, ognuno usato da tutti:
//
//  * [FluidPressMotion] — la corsa che arriva sempre in cima prima di tornare;
//  * [fluidPressTransform] — cosa diventa quella corsa, in scala e spostamento, per una taglia data;
//  * [FluidPressState] — la corsa piu' il dito: dove sta rispetto a dove e' sceso, e le tacche
//    aptiche mentre si sposta su un tasto premuto.

/**
 * Come un elemento risponde al dito.
 *
 * Due famiglie, e la differenza e' di sagoma, non di gusto:
 *
 *  * **[Expand]** — i pulsanti. Crescono sotto il dito, gli si inclinano incontro, si allungano nel
 *    verso in cui scorre e lo fanno sentire con tacche leggere. Un pulsante e' una cosa piccola e
 *    sola, e crescere e' il modo in cui dice "ti ho sentito" invece di "sono stato schiacciato";
 *  * **[yielding]** — le superfici: card, tessere, celle. Cedono di pochi centesimi, come sempre.
 *    Una card che crescesse invaderebbe le vicine, e una che seguisse il dito farebbe ballare la
 *    griglia in cui sta.
 *
 * @property pressedScale quanto cede una superficie a pressione piena (sotto 1). Ignorata da chi
 *   [expands]: la crescita di un pulsante dipende dalla sua taglia, vedi [fluidPressExpansion].
 * @property expands cresce invece di cedere.
 * @property followsFinger si inclina verso il dito e si allunga nel verso in cui scorre.
 * @property dragHaptics tacche aptiche mentre il dito scorre sull'elemento premuto.
 */
@Immutable
data class FluidPressFeedback(
  val pressedScale: Float = DefaultYieldScale,
  val expands: Boolean = false,
  val followsFinger: Boolean = false,
  val dragHaptics: Boolean = false,
) {
  companion object {
    /** I pulsanti dell'engine: crescono, seguono il dito, scattano sotto il dito che scorre. */
    val Expand: FluidPressFeedback = FluidPressFeedback(
      pressedScale = 1f,
      expands = true,
      followsFinger = true,
      dragHaptics = true,
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
 * L'unica regola e' quella che mancava: **il rilascio non parte prima che la corsa d'ingresso sia
 * arrivata.** Un [release] che cade a meta' viene messo da parte, e lo esegue la corsa stessa nel
 * fotogramma in cui passa [ArrivalFraction] — quindi un tap di 20 ms fa vedere la stessa pressione
 * completa di uno di mezzo secondo, solo senza la pausa in cima. Il ritorno eredita la velocita'
 * della salita, quindi non c'e' nessuna giuntura fra le due.
 *
 * Il controllo si fa nel blocco per fotogramma di `animateTo`, non con un `snapshotFlow`: funziona
 * uguale con le animazioni a durata zero (il blocco arriva una volta, gia' in cima) e si prova sul
 * computer con un orologio di fotogrammi finto, senza un Recomposer che giri.
 *
 * Una nuova [press] durante il ritorno lo interrompe e risale da dove si trova: un doppio tap non
 * aspetta che il primo sia finito.
 */
@Stable
internal class FluidPressMotion(private val scope: CoroutineScope) {

  /** La molla d'ingresso. Va scelta veloce: e' il tempo che un tap rapido deve aspettare. */
  var pressSpec: AnimationSpec<Float> = FluidPressSprings.ExpandIn

  /** La molla del ritorno. Qui si puo' rimbalzare: il dito non c'e' piu', nessuno aspetta. */
  var releaseSpec: AnimationSpec<Float> = FluidPressSprings.ExpandOut

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
    if (arrived) relax()
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
 * Le molle della pressione, tarate come il resto di [FluidMotion] (per `response`).
 *
 * Per i pulsanti l'ingresso e' sottosmorzato e corto (0.22 s, smorzamento 0.62): arriva a 0.96 in
 * circa 95 ms — il tempo che un tap rapido aspetta — e passa la cima di un 8%, che e' il "pop". Il
 * ritorno e' piu' morbido e rimbalza davvero (0.32 s, 0.5): il pulsante scende un poco sotto la sua
 * misura e ci torna, ed e' la meta' del perche' sembra fatto di qualcosa.
 *
 * Le superfici tengono le molle che avevano: ingresso istantaneo e ritorno [FluidMotion.fluid].
 * Con le animazioni ridotte niente rimbalza: due molle criticamente smorzate, e la corsa resta
 * intera — a durata zero, un fotogramma premuto e uno rilasciato, ma mai uno saltato.
 */
internal object FluidPressSprings {
  val ExpandIn: AnimationSpec<Float> = spring(0.62f, FluidMotion.stiffnessForResponse(0.22), 0.001f)
  val ExpandOut: AnimationSpec<Float> = spring(0.5f, FluidMotion.stiffnessForResponse(0.32), 0.001f)
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
 * Tutto quello che cambia durante il gesto e' letto nel disegno o nel layer, mai nella composizione:
 * un tasto premuto e trascinato non ricompone niente.
 */
@Stable
class FluidPressState internal constructor(
  scope: CoroutineScope,
  feedback: FluidPressFeedback,
) {
  internal val motion = FluidPressMotion(scope)
  internal var feedback: FluidPressFeedback = feedback
    private set
  internal var reducedMotion: Boolean = false
    private set
  private var haptics: FluidHaptics = NoOpFluidHaptics
  private val detents = FluidHapticDetents()
  private var origin = Offset.Zero
  private var last = Offset.Zero

  init {
    FluidPressSprings.configure(motion, feedback, reducedMotion = false)
  }

  /** Il dito rispetto a dove e' sceso. Stato, letto solo in disegno: muoverlo non ricompone. */
  internal var lean: Offset by mutableStateOf(Offset.Zero)
    private set

  /** L'ultimo punto toccato, nelle coordinate dell'elemento: il punto luce del vetro. */
  internal var touch: Offset by mutableStateOf(Offset.Zero)
    private set

  /** Quanto e' premuto: 0 a riposo, 1 premuto. Le molle possono uscire di poco da entrambi i lati. */
  val progress: Float get() = motion.value

  internal fun update(feedback: FluidPressFeedback, haptics: FluidHaptics, reducedMotion: Boolean) {
    if (feedback != this.feedback || reducedMotion != this.reducedMotion) {
      FluidPressSprings.configure(motion, feedback, reducedMotion)
    }
    this.feedback = feedback
    this.haptics = haptics
    this.reducedMotion = reducedMotion
  }

  internal fun press() = motion.press()

  internal fun release() = motion.release()

  /** Il dito e' sceso in [position]. Da qui si misurano inclinazione e strada percorsa. */
  internal fun touchDown(position: Offset) {
    origin = position
    last = position
    lean = Offset.Zero
    touch = position
    detents.restart()
  }

  /**
   * Il dito si e' spostato in [position]. Le tacche arrivano solo a pressione in corso: prima che
   * `clickable` la confermi (i suoi 100 ms dentro una lista) il dito potrebbe stare iniziando uno
   * scorrimento, e una tacca li' direbbe "ti ho preso" a un gesto che non e' per questo tasto.
   */
  internal fun touchMove(position: Offset, uptimeMillis: Long, stepPx: Float) {
    val delta = (position - last).getDistance()
    last = position
    lean = position - origin
    touch = position
    if (motion.held && feedback.dragHaptics && detents.travel(delta, stepPx, uptimeMillis)) {
      haptics.play(FluidHapticEvent.DragTick)
    }
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
  val state = remember(scope) { FluidPressState(scope, feedback) }
  SideEffect { state.update(feedback, haptics, reducedMotion) }
  return state
}

/**
 * Segue il dito su un elemento premuto: inclinazione, punto luce, tacche.
 *
 * Legge nel passaggio Final e non consuma niente, quindi non toglie il gesto a nessuno — e vede per
 * ultimo se qualcuno se l'e' preso: un cambio consumato e' uno scorrimento partito, e da li' questo
 * tasto non segue piu' niente. Con [ownsPress] e' anche lui a premere e rilasciare (il vetro senza
 * un click proprio, che risponde subito come ha sempre fatto); senza, la pressione la decide
 * `clickable` attraverso le sue interazioni, ritardo nelle liste compreso.
 */
internal fun Modifier.fluidPressTracking(
  state: FluidPressState,
  ownsPress: Boolean,
): Modifier = this.pointerInput(state, ownsPress) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
    state.touchDown(down.position)
    if (ownsPress) state.press()
    val stepPx = FluidPressDragStep.toPx()
    try {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
        if (!change.pressed || change.isConsumed) break
        if (change.positionChanged()) state.touchMove(change.position, change.uptimeMillis, stepPx)
      }
    } finally {
      if (ownsPress) state.release()
    }
  }
}

/** Un passo fra due tacche sotto un dito che scorre su un tasto premuto. */
internal val FluidPressDragStep: Dp = 10.dp

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
 * pressione [progress], col dito spostato di [lean] da dove e' sceso.
 *
 *  * **Crescita** (`expands`): vedi [fluidPressExpansion]. Con le animazioni ridotte e' dimezzata,
 *    non tolta: la risposta al dito resta, sparisce solo il movimento che ci sta attorno.
 *  * **Inclinazione** (`followsFinger`): una frazione dello spostamento del dito passata in una
 *    `tanh`, che lascia libero il primo tratto e poi si irrigidisce — la sagoma di una cosa morbida
 *    che e' attaccata. Mai oltre 8 dp, e mai oltre un sesto del lato corto: un'icona da 44 dp che
 *    scappasse di 8 dp sembrerebbe staccata.
 *  * **Allungamento**: lungo il verso del dito, e un poco piu' sottile di traverso, con la stessa
 *    `tanh` sulla frazione di lato percorsa. Pesato dalla pressione, quindi si scioglie col ritorno.
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
  if (width <= 0f || height <= 0f || progress == 0f) return FluidPressTransform.Identity
  val base = if (feedback.expands) {
    val growth = fluidPressExpansion(width, height, dp) - 1f
    1f + growth * (if (reducedMotion) ReducedAmplitude else 1f) * progress
  } else {
    1f + (feedback.pressedScale - 1f) * progress
  }
  val held = progress.coerceIn(0f, 1f)
  if (!feedback.followsFinger || reducedMotion || held == 0f) {
    return FluidPressTransform(base, base, 0f, 0f)
  }
  val reach = min(LeanMaxDp * dp, min(width, height) * LeanMaxFraction)
  val translationX = reach * tanh(lean.x * LeanGain / reach) * held
  val translationY = reach * tanh(lean.y * LeanGain / reach) * held
  val alongX = tanh(abs(lean.x) / width) * StretchMax * held
  val alongY = tanh(abs(lean.y) / height) * StretchMax * held
  return FluidPressTransform(
    scaleX = base * (1f + alongX - alongY * StretchThinning),
    scaleY = base * (1f + alongY - alongX * StretchThinning),
    translationX = translationX,
    translationY = translationY,
  )
}

/**
 * Di quanto cresce un pulsante a pressione piena, come scala uniforme.
 *
 * Una percentuale fissa sbaglia da entrambi i lati: il 14% di un'icona da 44 dp sono 6 dp e si
 * vedono appena, il 14% di un pulsante a tutta larghezza sono 50 dp e invade i margini. Quindi la
 * crescita e' in dp, e il lato che comanda e' quello che arriva prima al suo tetto: **6 dp sul lato
 * corto, al massimo 14 dp sul lato lungo, e mai oltre il 14%.** Un'icona cresce del 13%, un
 * pulsante medio del 12% (14 dp in larghezza), uno a tutta larghezza del 4% — sempre 7 dp per
 * parte. Il tetto sul lato lungo e' quello che tiene un tasto premuto lontano dal vicino: a 18 dp,
 * con l'inclinazione verso il dito, un pulsante medio toccava quello accanto in una fila a 12 dp
 * (visto sui fotogrammi, non immaginato).
 * Uniforme perche' il testo dentro non si deformi.
 */
internal fun fluidPressExpansion(width: Float, height: Float, dp: Float): Float {
  if (width <= 0f || height <= 0f) return 1f
  val short = min(width, height)
  val long = max(width, height)
  val growth = minOf(
    ExpandShortSideDp * dp / short,
    ExpandLongSideDp * dp / long,
    ExpandMaxScale - 1f,
  )
  return 1f + growth.coerceAtLeast(0f)
}

internal const val ExpandShortSideDp = 6f
internal const val ExpandLongSideDp = 14f
internal const val ExpandMaxScale = 1.14f

/** Con le animazioni ridotte un pulsante cresce la meta': risponde, ma senza muoversi molto. */
internal const val ReducedAmplitude = 0.5f

internal const val LeanMaxDp = 8f
internal const val LeanMaxFraction = 1f / 6f

/** Quanta parte dello spostamento del dito diventa inclinazione, prima che la `tanh` la freni. */
internal const val LeanGain = 0.15f

/** Allungamento massimo lungo il verso del dito. */
internal const val StretchMax = 0.08f

/** Quanto si assottiglia di traverso per ogni unita' di allungamento: meno della meta' del volume. */
internal const val StretchThinning = 0.5f
