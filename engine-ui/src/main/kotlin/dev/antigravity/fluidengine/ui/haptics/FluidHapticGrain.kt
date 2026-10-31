package dev.antigravity.fluidengine.ui.haptics

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs
import kotlin.math.exp

/**
 * Come si decide e come suona la grana di un dito che trascina un tasto (3.0).
 *
 * Le due si possono confrontare in mano dalla galleria, una accanto all'altra: [LibrePods] e' quella
 * di serie, perche' e' il riferimento che si e' deciso di eguagliare; [Fluid] e' quella che l'engine
 * aveva scritto prima, e resta perche' la sua idea (la velocita' misurata, non lo scatto di un
 * evento) e' ancora una proposta.
 */
enum class FluidHapticGrainStyle {
  /**
   * Come LibrePods (GPL-3.0, `StyledButton.kt:382` e `StyledIconButton.kt:235`, branch
   * `android/rewrite`, commit 3d4d270): ogni evento del puntatore il cui spostamento supera
   * `sqrt(350)` pixel (distanza al quadrato > 350) fa un `SegmentFrequentTick` di sistema, **senza
   * tetto di frequenza e senza memoria**. Piano non suona niente; veloce suona a ogni evento. Il
   * confronto e' in pixel, non in dp, come nell'originale: la soglia vale quel che vale la densita'.
   */
  LibrePods,

  /**
   * La grana dell'engine: la velocita' del dito e' misurata (media esponenziale in dp/s sugli istanti
   * degli eventi), la soglia ha isteresi e i granelli sono irregolari, come uno strofinio. Una
   * composizione di primitive, con la forza che dipende dalla velocita'.
   */
  Fluid,
}

/**
 * I numeri della grana (3.0). Di serie vanno bene per tutte le app: esistono come classe perche' la
 * galleria li tara dal vivo, sul telefono, che e' l'unico posto dove la grana si giudica.
 *
 * @property style quale delle due grane suona, vedi [FluidHapticGrainStyle]. Gli altri numeri sono
 *   tutti di [FluidHapticGrainStyle.Fluid], salvo [librePodsSquaredPx].
 * @property librePodsSquaredPx la soglia di [FluidHapticGrainStyle.LibrePods]: un evento suona se la
 *   sua distanza al quadrato, in pixel, la supera. 350, come l'originale.
 * @property onsetDpPerSec sopra questa velocita' la grana parte.
 * @property releaseDpPerSec sotto questa si spegne. Piu' bassa della soglia di partenza: un dito che
 *   oscilla attorno a una soglia sola accenderebbe e spegnerebbe la grana a ogni evento.
 * @property fullDpPerSec la velocita' a cui l'intensita' arriva al massimo.
 * @property spacingDp la strada fra un granello e l'altro, prima del jitter.
 * @property spacingJitter quanto ogni passo puo' scostarsi dal precedente (0,3 = ±30%).
 * @property minGapMillis il pavimento fra due granelli: sotto, il motore li fonde in un ronzio.
 * @property minIntensity l'intensita' appena sopra la soglia.
 * @property maxIntensity l'intensita' a [fullDpPerSec] e oltre.
 * @property intensityJitter quanto ogni granello puo' scostarsi in intensita' (0,2 = ±20%).
 * @property smoothingMillis la costante di tempo della media sulla velocita'.
 */
@Immutable
data class FluidHapticGrainConfig(
  val style: FluidHapticGrainStyle = FluidHapticGrainStyle.LibrePods,
  val librePodsSquaredPx: Float = 350f,
  val onsetDpPerSec: Float = 520f,
  val releaseDpPerSec: Float = 380f,
  val fullDpPerSec: Float = 2400f,
  val spacingDp: Float = 8f,
  val spacingJitter: Float = 0.3f,
  val minGapMillis: Long = 9L,
  val minIntensity: Float = 0.14f,
  val maxIntensity: Float = 0.5f,
  val intensityJitter: Float = 0.2f,
  val smoothingMillis: Float = 24f,
) {
  companion object {
    val Default = FluidHapticGrainConfig()
  }
}

/**
 * La grana in uso. Le app non lo toccano: e' il gancio della pagina di taratura della galleria.
 */
val LocalFluidHapticGrainConfig: ProvidableCompositionLocal<FluidHapticGrainConfig> =
  staticCompositionLocalOf { FluidHapticGrainConfig.Default }

/**
 * La grana di un dito che trascina veloce un tasto (3.0): niente sotto una certa velocita', e sopra
 * un treno fitto di granelli leggeri e irregolari, come uno strofinio su una superficie ruvida.
 *
 * Prende il posto delle tacche a distanza fissa ([FluidHapticDetents.travel]): un tick ogni 10 dp a
 * qualsiasi velocita' era un ticchettio anche a passo d'uomo, e a dito fermo che oscilla un rumore.
 * L'idea viene da LibrePods (un tick di sistema per ogni evento del puntatore che si sposta di piu' di
 * una soglia: piano non suona niente, veloce diventa una grana).
 *
 * **Di serie, dalla 3.0, e' proprio quella** ([FluidHapticGrainStyle.LibrePods]: la regola di
 * LibrePods, portata cosi' com'e'). L'altra ([FluidHapticGrainStyle.Fluid], la scrittura di prima della
 * 3.0, che e' quel che segue) si sceglie con [FluidHapticGrainConfig.style], per confrontarle in mano.
 * Le tre differenze della grana dell'engine:
 *
 *  * **la velocita' e' misurata**, non dedotta dallo spostamento di un evento: gli eventi arrivano a
 *    60, 120 o 240 Hz e a gruppi, e la stessa soglia in pixel per evento voleva dire velocita' diverse
 *    su schermi diversi. Qui e' una media esponenziale in dp/s sugli istanti degli eventi;
 *  * **la soglia ha isteresi**: parte a [FluidHapticGrainConfig.onsetDpPerSec] e si spegne solo sotto
 *    [FluidHapticGrainConfig.releaseDpPerSec];
 *  * **i granelli non sono regolari**: passo e intensita' oscillano con un rumore deterministico (un
 *    seme per gesto). Una superficie ruvida non e' periodica, e un treno regolare si legge di nuovo
 *    come un ticchettio, solo piu' veloce.
 *
 * Pura e senza orologio proprio: il tempo e' quello degli eventi, cosi' si prova sul computer.
 */
internal class FluidHapticGrain(
  var config: FluidHapticGrainConfig = FluidHapticGrainConfig.Default,
) {
  private var speed = 0f
  private var lastAt = NoTime
  private var pendingDp = 0f
  private var active = false
  private var travelledDp = 0f
  private var nextStepDp = 0f
  private var lastGrainAt = NoTime
  private var noise = 1

  init {
    restart()
  }

  /** Un gesto nuovo: velocita' a zero e il rumore che riparte dal seme dato. */
  fun restart(seed: Int = 1) {
    speed = 0f
    lastAt = NoTime
    pendingDp = 0f
    active = false
    travelledDp = 0f
    lastGrainAt = NoTime
    noise = if (seed == 0) 1 else seed
    nextStepDp = jittered(config.spacingDp, config.spacingJitter)
  }

  /** La velocita' stimata adesso, in dp/s: per la pagina di taratura. */
  val speedDpPerSec: Float get() = speed

  /**
   * Il dito si e' spostato di [deltaPx] (la distanza percorsa dall'evento, in pixel) all'istante
   * [uptimeMillis]. Ritorna l'intensita' (0..1) di un granello da suonare adesso, o null.
   *
   * Per [FluidHapticGrainStyle.LibrePods] la decisione e' una sola e senza memoria: un granello a ogni
   * evento la cui distanza al quadrato supera [FluidHapticGrainConfig.librePodsSquaredPx]. L'intensita'
   * che ritorna e' sempre 1: il tick di sistema non si dosa.
   */
  fun onMove(deltaPx: Float, pxPerDp: Float, uptimeMillis: Long): Float? {
    if (config.style == FluidHapticGrainStyle.LibrePods) {
      // La velocita' si stima lo stesso, per chi la guarda in una pagina di taratura: la decisione
      // non la usa.
      if (pxPerDp > 0f) measure(abs(deltaPx) / pxPerDp, uptimeMillis)
      return if (deltaPx * deltaPx > config.librePodsSquaredPx) 1f else null
    }
    if (pxPerDp <= 0f) return null
    val deltaDp = abs(deltaPx) / pxPerDp
    when (measure(deltaDp, uptimeMillis)) {
      // Il primo evento non ha un prima: fissa l'istante e basta.
      Sample.First -> return null
      // Eventi dello stesso istante (un gruppo): la strada si somma al prossimo campione.
      Sample.Grouped -> {
        if (active) travelledDp += deltaDp
        return null
      }
      Sample.Fresh -> Unit
    }

    if (!active && speed >= config.onsetDpPerSec) {
      active = true
      travelledDp = 0f
    } else if (active && speed < config.releaseDpPerSec) {
      active = false
      travelledDp = 0f
      return null
    }
    if (!active) return null

    travelledDp += deltaDp
    if (travelledDp < nextStepDp) return null
    if (lastGrainAt != NoTime && uptimeMillis - lastGrainAt < config.minGapMillis) return null
    // Niente arretrati: un granello saltato per il pavimento non si recupera dopo, o un dito veloce
    // accumulerebbe un debito che esce tutto in una raffica.
    travelledDp = 0f
    nextStepDp = jittered(config.spacingDp, config.spacingJitter)
    lastGrainAt = uptimeMillis
    val strength = smoothstep(config.onsetDpPerSec, config.fullDpPerSec, speed)
    val base = config.minIntensity + (config.maxIntensity - config.minIntensity) * strength
    return jittered(base, config.intensityJitter).coerceIn(0f, 1f)
  }

  private enum class Sample { First, Grouped, Fresh }

  /** Aggiorna la media della velocita' con lo spostamento di [deltaDp] all'istante [uptimeMillis]. */
  private fun measure(deltaDp: Float, uptimeMillis: Long): Sample {
    if (lastAt == NoTime) {
      lastAt = uptimeMillis
      return Sample.First
    }
    val dt = uptimeMillis - lastAt
    if (dt <= 0L) {
      pendingDp += deltaDp
      return Sample.Grouped
    }
    val instant = (pendingDp + deltaDp) / (dt / 1000f)
    pendingDp = 0f
    lastAt = uptimeMillis
    val alpha = 1f - exp(-dt / config.smoothingMillis)
    speed += alpha * (instant - speed)
    return Sample.Fresh
  }

  private fun jittered(value: Float, amount: Float): Float = value * (1f + amount * (nextNoise() * 2f - 1f))

  /** Un passo di un generatore congruenziale lineare: deterministico, in [0, 1). */
  private fun nextNoise(): Float {
    noise = noise * 1103515245 + 12345
    return ((noise ushr 8) and 0xFFFF) / 65536f
  }

  private fun smoothstep(from: Float, to: Float, x: Float): Float {
    if (to <= from) return if (x >= to) 1f else 0f
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  private companion object {
    const val NoTime = Long.MIN_VALUE
  }
}
