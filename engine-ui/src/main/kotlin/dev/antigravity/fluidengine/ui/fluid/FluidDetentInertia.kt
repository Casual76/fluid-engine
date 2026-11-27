/*
 * L'inerzia a scatti (4.0, F8.2): il moto di un controllo che scorre col dito e si ferma su una voce, come la ruota di
 * `UIPickerView` e il righello di «Raddrizza» in Foto. Codice del Fluid Engine.
 *
 * Il modello e' quello di UIKit (WWDC18, sessione 803 «Designing Fluid Interfaces»; le analisi di `UIScrollView`): la
 * velocita' del lancio cala del 0,2% a millisecondo (`decelerationRate = .normal`, 0,998), quindi il moto e' un
 * decadimento esponenziale e il punto in cui si fermerebbe da solo si **proietta** dalla velocita':
 * `distanza = v · r / (1 − r) / 1000`, cioe' mezzo secondo di velocita'. Una ruota non si ferma dove capita: come
 * `scrollViewWillEndDragging(_:withVelocity:targetContentOffset:)`, il punto proiettato si porta sulla voce piu' vicina,
 * e il decadimento si ritocca (la sua costante) perche' finisca **esattamente li'**, partendo con la velocita' del dito.
 * Nessun rimbalzo, nessun secondo movimento: lo scatto e' la coda della stessa inerzia.
 *
 * Quando il ritocco sarebbe troppo (un dito quasi fermo, una voce proiettata che torna indietro, la fine della lista
 * vicina) il moto e' una molla critica con la velocita' del dito: la stessa risposta dei tasti (0,30 s).
 *
 * **Niente sfocatura** (4.0, F8.2b, scelta di Alessio del 24 novembre 2026): una ruota e una ghiera le muove il dito, e
 * cio' che muove l'utente resta nitido, anche durante l'inerzia che continua il suo lancio. Si sfoca solo cio' che l'app
 * muove da sola (la logica di Apple). La sfocatura del moto di F8.2 e' stata tolta.
 *
 * **L'ultimo pixel non si aspetta** (4.0, F8.2c): un decadimento lento (un dito lasciato piano) passava piu' di un secondo
 * sotto il pixel prima di dirsi fermo, e il valore (con AM e PM che lo seguono) arrivava quando la ruota sembrava ferma
 * da un pezzo. Sotto [FluidDetentMotion.HandoffPx] dalla voce il lancio finisce con la molla dell'assestamento: la coda
 * invisibile dura ~0,13 s, e il valore arriva alla posa che si vede, come in iOS.
 */
package dev.antigravity.fluidengine.ui.fluid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * I numeri del moto a scatti (F8.2), in un posto solo: il profilo della ruota ([FluidWheelPicker]) e della ghiera
 * ([FluidRulerDial]).
 */
internal object FluidDetentMotion {

  /**
   * Quanta velocita' resta dopo un millisecondo: 0,998, `UIScrollView.DecelerationRate.normal`. Fonte: WWDC18 803 e le
   * analisi pubbliche di `UIScrollView` (fiducia alta sul valore, media sul fatto che `UIPickerView` usi `.normal`:
   * la ruota di iOS gira a lungo dopo un lancio, come una lista, e `.fast` (0,99) la fermerebbe in un quinto della strada).
   */
  const val DecelerationRate: Float = 0.998f

  /** I secondi di velocita' che fanno la distanza proiettata: `r / (1 − r) / 1000` = 0,499 s. */
  const val ProjectionSeconds: Float = DecelerationRate / (1f - DecelerationRate) / 1000f

  /** La costante del decadimento naturale, al secondo: `−ln(r) · 1000` ≈ 2,0. */
  val NaturalDecayPerSecond: Float = (-ln(DecelerationRate.toDouble()) * 1000.0).toFloat()

  /**
   * Quanto il decadimento si puo' ritoccare per finire sulla voce: fra mezza e quattro volte quello naturale. Oltre, il
   * moto non sembrerebbe piu' un lancio che rallenta ma un freno, e si passa alla molla. A occhio, 23 novembre 2026.
   */
  const val DecayRetuneLow: Float = 0.5f
  const val DecayRetuneHigh: Float = 4f

  /**
   * La molla di un assestamento (dito lasciato quasi fermo, tocco su una voce, voce spenta che torna alla piu' vicina):
   * critica, 0,30 s, la risposta dei tasti ([FluidMotion.ResponseSnappy]). iOS assesta la ruota in ~0,25–0,3 s.
   */
  const val SettleResponseSeconds: Double = 0.30

  /** La molla di un valore cambiato da fuori (l'app, TalkBack): 0,38 s, quella di serie ([FluidMotion.ResponseStandard]). */
  const val FollowResponseSeconds: Double = 0.38

  /**
   * Col movimento ridotto non c'e' inerzia lunga: il lancio porta al piu' una voce avanti (i secondi di velocita' che
   * contano sono questi, non mezzo secondo) e l'assestamento e' corto, 0,20 s ([FluidMotion.ResponseInstant]). Lo
   * scatto resta. A occhio, 23 novembre 2026.
   */
  const val ReducedProjectionSeconds: Float = 0.05f
  const val ReducedSettleResponseSeconds: Double = 0.20

  /** Sotto questo scarto, in pixel, il moto e' finito e si posa esatto: il resto della coda non si vede. */
  const val RestPx: Float = 0.25f

  /** E sotto questa velocita', in pixel al secondo. */
  const val RestSpeedPx: Float = 12f

  /**
   * Sotto questo scarto, in pixel, un lancio che decade passa la mano alla molla dell'assestamento
   * ([SettleResponseSeconds]): la ruota sembra gia' ferma, e la coda esponenziale fino a [RestPx] durerebbe
   * `ln(4) / k`, cioe' 0,7–1,4 s con la costante di un dito lasciato piano. La molla critica, presa con la velocita' del
   * decadimento, chiude l'ultimo pixel in ~0,13 s senza scavalcare la voce (la sua frequenza, 21 al secondo, e' piu' alta
   * di quella del decadimento). F8.2c, 24 novembre 2026: in iOS le colonne collegate (AM e PM) cambiano appena la ruota
   * sembra ferma.
   */
  const val HandoffPx: Float = 1f
}

/**
 * Il moto di un controllo a scatti, nello spazio delle voci (una voce = 1), senza orologio proprio e senza allocazioni:
 * chi lo usa lo fa avanzare a ogni fotogramma con [step]. Due modi: il decadimento che finisce su [target] (un lancio)
 * e la molla critica (un assestamento). Soluzioni esatte, non integrate: lo stesso moto a 60 e a 120 Hz.
 */
internal class FluidDetentInertia {
  /** Dove sta adesso, in voci. */
  var position: Float = 0f
    private set

  /** Quanto corre adesso, in voci al secondo. */
  var velocity: Float = 0f
    private set

  /** Dove finira'. */
  var target: Float = 0f
    private set

  /** Vero finche' si muove. */
  var moving: Boolean = false
    private set

  /** La costante del decadimento in corso, al secondo; zero se il moto e' una molla. */
  var decayPerSecond: Float = 0f
    private set

  private var omega = 0f
  private var restSteps = 0f
  private var restSpeed = 0f
  private var handoffSteps = 0f
  private var handoffOmega = 0f
  /** F7.24b: sotto questa velocita' (voci al secondo) il decadimento passa la mano alla molla; zero: solo [handoffSteps]. */
  private var handoffSpeed = 0f

  /** Fermo a [value]. */
  fun snapTo(value: Float) {
    position = value
    target = value
    velocity = 0f
    moving = false
    decayPerSecond = 0f
  }

  /**
   * Un lancio da [from] con [speed] (voci al secondo) che finisce su [to]. Se il decadimento che ci arriva e' troppo
   * lontano da quello naturale, una molla critica di [settleSeconds] con la stessa velocita'. [handoffSpeedPx] (pixel al
   * secondo, zero di serie) fa passare la mano alla molla anche quando il decadimento e' gia' lento (F7.24b, la ghiera).
   */
  fun flingTo(
    from: Float,
    speed: Float,
    to: Float,
    pxPerStep: Float,
    settleSeconds: Double = FluidDetentMotion.SettleResponseSeconds,
    handoffSpeedPx: Float = 0f,
  ) {
    val k = fluidDetentDecayFor(from, speed, to)
    if (k > 0f) {
      start(from, speed, to, pxPerStep)
      decayPerSecond = k
      // L'ultimo pixel lo chiude la molla dell'assestamento (F8.2c).
      handoffOmega = (2.0 * PI / settleSeconds).toFloat()
      handoffSpeed = if (handoffSpeedPx > 0f) handoffSpeedPx / (if (pxPerStep > 0f) pxPerStep else 1f) else 0f
    } else {
      springTo(from, speed, to, pxPerStep, settleSeconds)
    }
  }

  /** Una molla critica da [from] con [speed] verso [to], con la risposta [responseSeconds] alla maniera di Apple. */
  fun springTo(from: Float, speed: Float, to: Float, pxPerStep: Float, responseSeconds: Double) {
    start(from, speed, to, pxPerStep)
    decayPerSecond = 0f
    omega = (2.0 * PI / responseSeconds).toFloat()
  }

  /** Il dito l'ha preso: si ferma dov'e'. */
  fun stop() {
    target = position
    velocity = 0f
    moving = false
  }

  /** Avanza di [dtSeconds]. Vero se si muove ancora dopo questo passo. */
  fun step(dtSeconds: Float): Boolean {
    if (!moving) return false
    if (dtSeconds <= 0f) return true
    var e = position - target
    var v = velocity
    if (decayPerSecond > 0f) {
      val decay = exp(-decayPerSecond * dtSeconds)
      e *= decay
      v = -decayPerSecond * e
      if (abs(e) <= handoffSteps || abs(v) <= handoffSpeed) {
        // Sembra gia' ferma: da qui la molla, con la stessa velocita' (F8.2c). Senza, la coda sotto il pixel dura un secondo.
        decayPerSecond = 0f
        omega = handoffOmega
      }
    } else {
      // La molla critica, esatta: e(t) = (e0 + (v0 + w·e0)·t)·exp(−w·t).
      val w = omega
      val decay = exp(-w * dtSeconds)
      val k = v + w * e
      val nextE = (e + k * dtSeconds) * decay
      val nextV = (v - w * k * dtSeconds) * decay
      e = nextE
      v = nextV
    }
    if (abs(e) <= restSteps && abs(v) <= restSpeed) {
      position = target
      velocity = 0f
      moving = false
      return false
    }
    position = target + e
    velocity = v
    return true
  }

  private fun start(from: Float, speed: Float, to: Float, pxPerStep: Float) {
    position = from
    velocity = speed
    target = to
    val px = if (pxPerStep > 0f) pxPerStep else 1f
    restSteps = FluidDetentMotion.RestPx / px
    restSpeed = FluidDetentMotion.RestSpeedPx / px
    handoffSteps = FluidDetentMotion.HandoffPx / px
    handoffSpeed = 0f
    moving = from != to || speed != 0f
    if (!moving) velocity = 0f
  }
}

/**
 * La costante del decadimento che porta da [from], con la velocita' [speed], a fermarsi su [to]: `speed / (to − from)`.
 * Zero se non si puo' (verso opposto, distanza nulla) o se e' fuori dalla banda di [FluidDetentMotion.DecayRetuneLow]–
 * [FluidDetentMotion.DecayRetuneHigh] volte quella naturale: allora il moto e' una molla.
 */
internal fun fluidDetentDecayFor(from: Float, speed: Float, to: Float): Float {
  val distance = to - from
  if (distance == 0f || speed == 0f || sign(distance) != sign(speed)) return 0f
  val k = speed / distance
  val natural = FluidDetentMotion.NaturalDecayPerSecond
  return if (k >= natural * FluidDetentMotion.DecayRetuneLow && k <= natural * FluidDetentMotion.DecayRetuneHigh) k else 0f
}

/**
 * Dove si ferma un lancio lasciato a [position] con [speed] (voci al secondo): la voce piu' vicina al punto proiettato,
 * tenuta fra [lower] e [upper] (null: una ruota che gira all'infinito). Col movimento ridotto la proiezione e' corta e
 * porta al piu' una voce oltre quella sotto il dito.
 */
internal fun fluidDetentLanding(
  position: Float,
  speed: Float,
  reducedMotion: Boolean,
  lower: Float?,
  upper: Float?,
): Float {
  val nearest = fluidDetentRound(position)
  val raw = if (reducedMotion) {
    val projected = fluidDetentRound(position + speed * FluidDetentMotion.ReducedProjectionSeconds)
    projected.coerceIn(nearest - 1f, nearest + 1f)
  } else {
    fluidDetentRound(position + speed * FluidDetentMotion.ProjectionSeconds)
  }
  var landed = raw
  if (lower != null && landed < lower) landed = lower
  if (upper != null && landed > upper) landed = upper
  return landed
}

/** La voce piu' vicina: a meta' vince quella avanti, come nei passi di `FluidScrubber`. */
internal fun fluidDetentRound(value: Float): Float = floor(value + 0.5f)

/** L'indice di una voce di una ruota di [count] voci che gira all'infinito: sempre fra 0 e `count − 1`. */
internal fun fluidDetentWrap(index: Int, count: Int): Int = if (count <= 0) 0 else Math.floorMod(index, count)

/**
 * La posizione di [index] piu' vicina a [position] su una ruota di [count] voci che gira all'infinito: la strada corta,
 * come iOS quando un valore cambia da fuori. Su una ruota che non gira e' [index].
 */
internal fun fluidDetentNearest(position: Float, index: Int, count: Int, cyclic: Boolean): Float {
  if (!cyclic || count <= 0) return index.toFloat()
  val here = fluidDetentRound(position).roundToInt()
  val current = fluidDetentWrap(here, count)
  var delta = index - current
  val half = count / 2
  if (delta > half) delta -= count
  if (delta < -half) delta += count
  return (here + delta).toFloat()
}
