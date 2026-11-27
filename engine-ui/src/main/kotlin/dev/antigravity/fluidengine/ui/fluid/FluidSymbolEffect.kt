package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// F8.5 · Gli effetti dei simboli: il vocabolario di SF Symbols (iOS 17, 18 e 26) sulle icone di FluidIcons.
//
// Ogni numero di movimento viene da un fotogramma di Apple, misurato in `artifacts/liquid-glass/sessioni/f8-5/
// riferimenti/misure.md` (video HD delle sessioni WWDC, 30000/1001 fps, cioe' un campione ogni 33,37 ms), salvo dove
// scritto «a occhio» con la data. Le curve sono i campioni misurati, passati per l'interpolazione monotona di
// [GlassContentCurve] (passa per i campioni, non li supera).

/** I numeri degli effetti, con la loro scheda. */
internal object FluidSymbolProfile {
  /** Un fotogramma delle sessioni WWDC: 1001/30 ms. */
  const val FrameMs: Float = 1001f / 30f

  private fun frames(vararg values: Float, every: Int = 1): GlassContentCurve =
    GlassContentCurve(FloatArray(values.size) { it * every * FrameMs }, values)

  /**
   * Il rimbalzo (`.bounce`, verso l'alto): la scala del simbolo intero. WWDC23 10197 «What's new in SF Symbols 5», 5:45–
   * 5:57, quattro rimbalzi a tempo vero (il sigillo con la spunta, la nota, «HA HA», il sole): +25 % a 167–200 ms,
   * −9 % a 400–433, fermo a 567–600. Qui la media del sigillo e del sole, un campione per fotogramma. Fiducia **alta**.
   */
  val BounceScale: GlassContentCurve = frames(
    1f, 1.03f, 1.05f, 1.12f, 1.185f, 1.24f, 1.255f, 1.235f, 1.15f, 1.065f, 0.99f, 0.925f, 0.905f, 0.92f, 0.94f, 0.96f,
    0.98f, 0.993f, 1f,
  )
  val BounceMs: Float = 18 * FrameMs

  /**
   * Il rimbalzo verso il basso (`.bounce.down`): la stessa curva capovolta e un po' piu' piccola (0,8), perche' un
   * simbolo che si stringe del 25 % si legge come uno che sparisce. A occhio, 24 novembre 2026 (Apple non lo mostra
   * in un fotogramma misurabile).
   */
  const val BounceDownFactor: Float = 0.8f

  /**
   * La pulsazione (`.pulse`): l'opacita' dello strato che pulsa scende al 48 % e torna, in un ciclo di **2,00 s**.
   * WWDC23 10197, 9:09–9:19, lo schermo del simbolo «condivisione schermo»: cinque cicli, massimi a 2,00 s l'uno
   * dall'altro, minimo 0,233 su massimo 0,487 della sua tinta. Fiducia **alta** sul ciclo ripetuto, **media** sul
   * singolo colpo (Apple lo mostra solo in ripetizione).
   */
  const val PulseMs: Float = 2000f
  const val PulseMinAlpha: Float = 0.48f

  /**
   * Il dondolio (`.wiggle`): lo spostamento lungo la sua direzione, in frazione della taglia del simbolo. WWDC24 10188
   * «What's new in SF Symbols 6», 2:56–3:02, l'aeroplanino lungo 315°: picco 0,073 a 133–167 ms, poi −0,047, +0,036,
   * −0,031 e il rientro, fermo a 901 ms (lo stesso numero di F6B.0). Fiducia **alta**.
   */
  val WiggleOffset: GlassContentCurve = frames(
    0f, 0.0101f, 0.0295f, 0.0557f, 0.0733f, 0.0728f, 0.0438f, 0.0083f, -0.0258f, -0.0475f, -0.0461f, -0.0336f,
    -0.0037f, 0.0152f, 0.0355f, 0.0364f, 0.0207f, 0.0014f, -0.0134f, -0.0299f, -0.0313f, -0.0281f, -0.0230f, -0.0189f,
    -0.0124f, -0.0069f, -0.0037f, 0f,
  )
  val WiggleMs: Float = 27 * FrameMs

  /** Il picco della curva del dondolio (0,073 della taglia): il punto che vale [WiggleDegrees]. */
  const val WigglePeak: Float = 0.0733f

  /**
   * Il dondolio che ruota (`.wiggle.clockwise`): la stessa curva come angolo, col picco a **10°**. WWDC24 10188,
   * 2:36–2:38, l'aeroplanino in senso orario: +9,5°, −8,5°, +5°, −5,5° e il rientro in ~0,85 s. Fiducia **alta**.
   */
  const val WiggleDegrees: Float = 10f

  /**
   * La rotazione (`.rotate`) una volta sola: un giro in **1,90 s**, che parte svelto (~390°/s) e frena a lungo. WWDC24
   * 10188, 3:41–3:44, l'ingranaggio: un campione ogni tre fotogrammi, in frazione del giro. Fiducia **alta**.
   */
  val RotateTurn: GlassContentCurve = frames(
    0f, 0.0764f, 0.1639f, 0.2722f, 0.3861f, 0.4944f, 0.5889f, 0.6694f, 0.7361f, 0.7931f, 0.8361f, 0.875f, 0.9083f,
    0.9306f, 0.9514f, 0.9681f, 0.9792f, 0.9875f, 0.9931f, 1f,
    every = 3,
  )
  val RotateMs: Float = 57 * FrameMs

  /**
   * La rotazione che continua (`.rotate` attivo): parte in **0,8 s** e poi gira a **180°/s**, un giro ogni 2 s. WWDC24
   * 10188, 3:45–3:53, la ventola: la rampa di 0,8 s e la velocita' fra 120 e 180°/s (due tratti del filmato). Fiducia
   * **media**. Quando si ferma finisce il giro con una frenata che parte alla stessa velocita' (a occhio, 24 novembre 2026).
   */
  const val SpinRampMs: Float = 800f
  const val SpinDegreesPerSecond: Float = 180f

  /**
   * Il respiro (`.breathe`): il simbolo cresce del **23 %** schiarendo fino al **65 %** e torna, salita e discesa di
   * **1,35 s** e una pausa, un ciclo di **3,0 s**. WWDC24 10188, 4:28–4:40, il cuore di destra (+23 % di larghezza, la
   * tinta da 154 a 100 di saturazione), lo stesso ciclo di F6B.0 (picchi a 3,00 s). Fiducia **alta**.
   */
  const val BreatheMs: Float = 3000f
  const val BreatheRiseMs: Float = 1350f
  const val BreatheScale: Float = 1.23f
  const val BreatheMinAlpha: Float = 0.65f

  /**
   * Il colore variabile (`.variableColor`): le tacche del Wi-Fi. WWDC23 10197, 10:00–10:32, i due Wi-Fi «Cumulative» e
   * «Iterative» a tempo vero. Una tacca parte ogni **334 ms** (10 fotogrammi) e si accende in **290 ms**; spenta vale il
   * **24 %** della tinta. «Iterative» (di serie): ogni tacca si spegne subito dopo, in 290 ms, e il giro dura le tacche
   * piu' una (1,34 s con tre); «Cumulative»: restano accese, tutte insieme per due tacche, si spengono insieme e restano
   * spente per due (2,34 s con tre). Fiducia **alta**.
   */
  const val VariableStepMs: Float = 10 * FrameMs
  const val VariableRiseMs: Float = 290f
  const val VariableDimAlpha: Float = 0.24f

  /**
   * Draw On e Draw Off (SF Symbols 7, iOS 26): il simbolo si disegna in **800 ms**. WWDC25 337, 4:31, l'anteprima
   * nell'app SF Symbols: 801 ms dal primo inchiostro all'ultimo tratto fermo (F6B.0). Fiducia **alta** sul tempo di
   * quella riproduzione. Gli strati partono uno dopo l'altro a meta' del precedente (per strato, la riproduzione di
   * serie di Apple): a occhio, 24 novembre 2026.
   */
  const val DrawMs: Float = 800f
  const val DrawStagger: Float = 0.5f

  /** La sostituzione (`.replace`): la curva R7 dello scambio d'icona del vetro, [GlassContentDefaults.DurationMs]. */
  val ReplaceMs: Float get() = GlassContentDefaults.DurationMs * GlassContentDefaults.EnterEnd
}

/**
 * I numeri della Magic Replace (F8.5b), misurati su WWDC24 10188 «What's new in SF Symbols 6», 5:19–5:26: la campana
 * diventa barrata, torna campana, prende il pallino e lo perde, a tempo vero (un fotogramma ogni 33,37 ms;
 * `sessioni/f8-5b/riferimenti/misure.md`). Fiducia **alta** sui tempi, **media** sulle ampiezze dei rimbalzi.
 */
internal object FluidSymbolMagic {
  /** La penna percorre la barra in **267 ms** (8 fotogrammi: dal primo segno a ore 10 alla punta in fondo). */
  const val PenMs: Float = 8 * FluidSymbolProfile.FrameMs

  /** La barra si ritira verso il suo inizio in **300 ms** (9 fotogrammi). */
  const val RetractMs: Float = 9 * FluidSymbolProfile.FrameMs

  /**
   * Il pallino entra in dissolvenza dal taglio ed e' pieno a **233 ms** (7 fotogrammi), e il suo rimbalzo parte a
   * **100 ms**, quando e' a meta'. Misurato in F8.5c fotogramma per fotogramma sulla campana col pallino: pieno 0,11 ·
   * 0,26 · 0,47 · 0,63 · 0,82 · 0,96 · 1 (F8.5b lo faceva partire a 100 ms, in ritardo di mezza dissolvenza).
   */
  const val BadgeDelayMs: Float = 3 * FluidSymbolProfile.FrameMs
  const val BadgeFadeMs: Float = 7 * FluidSymbolProfile.FrameMs

  /**
   * Il pallino rimbalza come il rimbalzo dei simboli, un po' piu' piccolo e svelto: +21 % a 233 ms dal taglio, −12 % a
   * 467, fermo a 700 (misurati: area 8212 → 12101 → 7295 → 9353 px).
   */
  const val BadgeAmplitude: Float = 0.85f
  const val BadgeSpeed: Float = 1.2f

  /**
   * Il pallino se ne va in **167 ms** (5 fotogrammi): svanisce quasi in linea retta nei primi 133, stringendosi appena
   * (pieno 0,89 · 0,63 · 0,35 · 0,12 · 0, F8.5c), poi l'intaglio si richiude ([CutMs]).
   */
  const val BadgeOutMs: Float = 5 * FluidSymbolProfile.FrameMs

  /**
   * L'intaglio che fa posto al pallino si apre in **un fotogramma**, prima del pallino, e si richiude in uno quando il
   * pallino e' svanito (F8.5c, sulla campana col pallino: aperto a f128, richiuso a f170).
   */
  const val CutMs: Float = FluidSymbolProfile.FrameMs

  /** La base rimbalza poco: ±4–5 % (la larghezza della campana 537 → 563 → 517 px), il 18 % del rimbalzo pieno. */
  const val BaseAmplitude: Float = 0.18f

  /** Tutto e' fermo in **700 ms** (21 fotogrammi: 0,63–0,73 s nei quattro casi). */
  const val Ms: Float = 21 * FluidSymbolProfile.FrameMs

  /**
   * Un pezzo della base cambia in un lampo mentre la punta gli passa sopra: da mezza unita' prima del suo centro a mezza
   * unita' dopo (a occhio, 24 novembre 2026: nei fotogrammi di Apple il taglio nella campana segue la punta).
   */
  const val PieceReach: Float = 0.5f

  /** Con «Riduci movimento» i pezzi diversi si incrociano soltanto, senza penna ne' rimbalzi. */
  const val ReducedMs: Float = 300f
}

/**
 * Un effetto dei simboli, dal vocabolario di SF Symbols: si applica a un'icona con [fluidSymbolEffect] (una volta, a ogni
 * cambio di un valore), con [fluidSymbolEffectWhile] (finché è attivo), o dentro [FluidSymbol], che sa anche il colore
 * variabile per strati, la sostituzione e Draw On / Draw Off.
 *
 * I tempi, le curve e le ampiezze sono quelli di Apple, misurati sulle sessioni WWDC (`FluidSymbolProfile`).
 *
 * - [Bounce]: «è successo qualcosa» (la scheda scelta, un elemento aggiunto). +25 % e ritorno in 0,6 s.
 * - [Pulse]: «sta succedendo» (registra, trasmette): l'opacità che scende e torna, 2 s.
 * - [Wiggle], [WiggleUp], [WiggleClockwise], [wiggleRotation]: «guarda qui» (una notifica, un invito): il dondolio smorzato
 *   di 0,9 s. Quello che ruota attorno al manico fa la campanella.
 * - [Rotate], [RotateCounterClockwise]: un giro (1,9 s), o una rotazione continua finché è attivo (la sincronizzazione).
 * - [Breathe]: «è vivo» (il microfono che ascolta): cresce e schiarisce, 3 s.
 * - [VariableColor], [VariableColorCumulative]: le tacche che si accendono in fila (il Wi-Fi che cerca, il volume).
 *
 * [speed] e [repeat] cambiano la velocità e quante volte si ripete un effetto a colpo singolo, come `.speed` e `.repeat`
 * di Apple. «Riduci movimento» toglie lo spostamento, la scala e la rotazione: restano solo le opacità (pulsazione,
 * colore variabile, il respiro senza crescere), come in iOS.
 */
@Immutable
class FluidSymbolEffect private constructor(
  internal val kind: Int,
  internal val variant: Int,
  /** La velocità: 2 fa l'effetto in metà tempo. */
  val speed: Float,
  /** Quante volte suona un effetto a colpo singolo. */
  val repeatCount: Int,
  internal val pivotX: Float,
  internal val pivotY: Float,
) {
  init {
    require(speed > 0f && speed.isFinite()) { "velocità non valida: $speed" }
    require(repeatCount >= 1) { "ripetizioni non valide: $repeatCount" }
  }

  /** Lo stesso effetto alla velocità [factor] (`.speed` di Apple). */
  fun speed(factor: Float): FluidSymbolEffect = FluidSymbolEffect(kind, variant, factor, repeatCount, pivotX, pivotY)

  /** Lo stesso effetto ripetuto [count] volte a ogni colpo (`.repeat` di Apple). */
  fun repeat(count: Int): FluidSymbolEffect = FluidSymbolEffect(kind, variant, speed, count, pivotX, pivotY)

  override fun equals(other: Any?): Boolean = other is FluidSymbolEffect && other.kind == kind &&
    other.variant == variant && other.speed == speed && other.repeatCount == repeatCount && other.pivotX == pivotX &&
    other.pivotY == pivotY

  override fun hashCode(): Int =
    ((((kind * 31 + variant) * 31 + speed.hashCode()) * 31 + repeatCount) * 31 + pivotX.hashCode()) * 31 + pivotY.hashCode()

  override fun toString(): String = "FluidSymbolEffect(${Names[kind]}, variante $variant, ×$speed, $repeatCount volte)"

  companion object {
    internal const val KindBounce = 0
    internal const val KindPulse = 1
    internal const val KindWiggle = 2
    internal const val KindRotate = 3
    internal const val KindBreathe = 4
    internal const val KindVariable = 5
    private val Names = listOf("bounce", "pulse", "wiggle", "rotate", "breathe", "variableColor")

    // Varianti del dondolio.
    internal const val WiggleForward = 0
    internal const val WiggleVertical = 1
    internal const val WiggleRotation = 2

    private fun of(kind: Int, variant: Int = 0, pivotX: Float = 0.5f, pivotY: Float = 0.5f) =
      FluidSymbolEffect(kind, variant, 1f, 1, pivotX, pivotY)

    /** Il rimbalzo verso l'alto (`.bounce`): il simbolo cresce del 25 %, scende sotto la taglia e si posa, 0,6 s. */
    val Bounce: FluidSymbolEffect = of(KindBounce, +1)

    /** Il rimbalzo verso il basso (`.bounce.down`): si stringe e torna. */
    val BounceDown: FluidSymbolEffect = of(KindBounce, -1)

    /** La pulsazione (`.pulse`): l'opacità scende al 48 % e torna, 2 s. */
    val Pulse: FluidSymbolEffect = of(KindPulse)

    /** Il dondolio in avanti, nel verso della lettura (`.wiggle.forward`): 0,9 s, picco al 7 % della taglia. */
    val Wiggle: FluidSymbolEffect = of(KindWiggle, WiggleForward)

    /** Il dondolio in alto e in basso (`.wiggle.up`). */
    val WiggleUp: FluidSymbolEffect = of(KindWiggle, WiggleVertical)

    /** Il dondolio che ruota attorno al centro in senso orario (`.wiggle.clockwise`): ±10°. */
    val WiggleClockwise: FluidSymbolEffect = of(KindWiggle, WiggleRotation * 2 + 0)

    /**
     * Il dondolio che ruota attorno a un perno ([pivotX], [pivotY], in frazione del riquadro): la campanella si appende
     * in alto (`pivotY = 0,12`). [clockwise] falso parte in senso antiorario.
     */
    fun wiggleRotation(clockwise: Boolean = true, pivotX: Float = 0.5f, pivotY: Float = 0.5f): FluidSymbolEffect =
      of(KindWiggle, WiggleRotation * 2 + if (clockwise) 0 else 1, pivotX, pivotY)

    /** Un giro in senso orario (`.rotate`), 1,9 s; attivo, gira finché lo è. */
    val Rotate: FluidSymbolEffect = of(KindRotate, +1)

    /** Un giro in senso antiorario (`.rotate.counterClockwise`). */
    val RotateCounterClockwise: FluidSymbolEffect = of(KindRotate, -1)

    /** Il respiro (`.breathe`): +23 % e schiarito al 65 %, un ciclo di 3 s. */
    val Breathe: FluidSymbolEffect = of(KindBreathe)

    /** Il colore variabile «a uno a uno» (`.variableColor.iterative`): una tacca accesa per volta. */
    val VariableColor: FluidSymbolEffect = of(KindVariable, 0)

    /** Il colore variabile «a riempire» (`.variableColor.cumulative`): le tacche restano accese. */
    val VariableColorCumulative: FluidSymbolEffect = of(KindVariable, 1)
  }
}

/** Il fotogramma di un effetto sul simbolo intero: scala, spostamento (in frazione del riquadro), rotazione, perno, opacità. */
internal class FluidSymbolFrame {
  var scale = 1f
  var translationX = 0f
  var translationY = 0f
  var rotation = 0f
  var pivotX = 0.5f
  var pivotY = 0.5f
  var alpha = 1f

  fun rest() {
    scale = 1f; translationX = 0f; translationY = 0f; rotation = 0f; pivotX = 0.5f; pivotY = 0.5f; alpha = 1f
  }

  val atRest: Boolean
    get() = scale == 1f && translationX == 0f && translationY == 0f && rotation == 0f && alpha == 1f
}

/** Quanto dura un ciclo dell'effetto, in ms a velocità 1; [layers] conta per il colore variabile. */
internal fun fluidSymbolCycleMs(effect: FluidSymbolEffect, layers: Int = 1, indefinite: Boolean = false): Float {
  val n = layers.coerceAtLeast(1)
  return when (effect.kind) {
    FluidSymbolEffect.KindBounce -> FluidSymbolProfile.BounceMs
    FluidSymbolEffect.KindPulse -> FluidSymbolProfile.PulseMs
    FluidSymbolEffect.KindWiggle -> FluidSymbolProfile.WiggleMs
    FluidSymbolEffect.KindRotate ->
      if (indefinite) 360f / FluidSymbolProfile.SpinDegreesPerSecond * 1000f else FluidSymbolProfile.RotateMs
    FluidSymbolEffect.KindBreathe -> FluidSymbolProfile.BreatheMs
    else -> FluidSymbolProfile.VariableStepMs * if (effect.variant == 1) n + 4 else n + 1
  }
}

/**
 * Quando un effetto torna a riposo, in ms di orologio dall'inizio: un colpo singolo dura le sue ripetizioni; uno attivo
 * non finisce ([Float.POSITIVE_INFINITY]) finché non lo si ferma a [stopAt], e da lì finisce il ciclo in corso (la rotazione
 * finisce il giro frenando).
 */
internal fun fluidSymbolEndMs(effect: FluidSymbolEffect, indefinite: Boolean, stopAt: Float, layers: Int = 1): Float {
  val cycles = fluidSymbolCyclesEndMs(effect, indefinite, stopAt, layers)
  // Il colore variabile finisce con le tacche spente: risalgono insieme alla tinta piena in una tacca di tempo.
  return if (effect.kind == FluidSymbolEffect.KindVariable) cycles + FluidSymbolProfile.VariableRiseMs / effect.speed else cycles
}

/** La fine dell'ultimo ciclo intero (per il colore variabile, prima della risalita finale). */
internal fun fluidSymbolCyclesEndMs(effect: FluidSymbolEffect, indefinite: Boolean, stopAt: Float, layers: Int = 1): Float {
  val cycle = fluidSymbolCycleMs(effect, layers, indefinite) / effect.speed
  if (!indefinite) return cycle * effect.repeatCount
  if (stopAt.isNaN()) return Float.POSITIVE_INFINITY
  if (effect.kind == FluidSymbolEffect.KindRotate) {
    val angle = fluidSymbolSpinDegrees(stopAt * effect.speed)
    val remaining = ceil(angle / 360f - 1e-4f) * 360f - angle
    return stopAt + fluidSymbolSpinStopMs(remaining) / effect.speed
  }
  return ceil(stopAt / cycle - 1e-4f).coerceAtLeast(1f) * cycle
}

/**
 * L'opacità delle tacche nella risalita finale del colore variabile (dalla spenta alla piena), o -1 fuori dalla risalita.
 * [ms] è l'orologio dall'inizio.
 */
internal fun fluidSymbolVariableReturn(effect: FluidSymbolEffect, ms: Float, indefinite: Boolean, stopAt: Float, layers: Int): Float {
  val cycles = fluidSymbolCyclesEndMs(effect, indefinite, stopAt, layers)
  if (ms < cycles) return -1f
  val k = glassContentSmoothstep(0f, FluidSymbolProfile.VariableRiseMs, (ms - cycles) * effect.speed)
  return FluidSymbolProfile.VariableDimAlpha + (1f - FluidSymbolProfile.VariableDimAlpha) * k
}

/** L'angolo della rotazione continua dopo [ms] (velocità 1): la rampa di [FluidSymbolProfile.SpinRampMs] e poi 180°/s. */
internal fun fluidSymbolSpinDegrees(ms: Float): Float {
  val w = FluidSymbolProfile.SpinDegreesPerSecond / 1000f
  val ramp = FluidSymbolProfile.SpinRampMs
  return if (ms < ramp) w * ms * ms / (2f * ramp) else w * (ms - ramp / 2f)
}

/** Quanto dura la frenata che percorre [degrees] partendo a 180°/s (una cubica in uscita: tre volte il tempo a velocità piena). */
internal fun fluidSymbolSpinStopMs(degrees: Float): Float =
  if (degrees <= 0f) 0f else 3f * degrees / (FluidSymbolProfile.SpinDegreesPerSecond / 1000f)

/**
 * Il fotogramma di [effect] al tempo [ms] dall'inizio (orologio, non ancora diviso per la velocità), scritto in [out].
 * [indefinite] vero per un effetto attivo, fermato a [stopAt] (NaN se non è fermato). [rtl] gira il dondolio in avanti
 * nelle lingue da destra a sinistra; [layered] lascia il colore variabile al disegno per strati. Restituisce vero finché l'effetto si muove. Pura e senza allocazioni.
 */
internal fun fluidSymbolEffectFrame(
  effect: FluidSymbolEffect,
  ms: Float,
  indefinite: Boolean,
  stopAt: Float,
  reduced: Boolean,
  out: FluidSymbolFrame,
  rtl: Boolean = false,
  layered: Boolean = false,
): Boolean {
  out.rest()
  val end = fluidSymbolEndMs(effect, indefinite, stopAt)
  if (ms >= end || ms < 0f) return false
  val t = ms * effect.speed
  val cycle = fluidSymbolCycleMs(effect, 1, indefinite)
  val phase = t - floor(t / cycle) * cycle
  when (effect.kind) {
    FluidSymbolEffect.KindBounce -> {
      if (reduced) return false
      val s = FluidSymbolProfile.BounceScale.at(phase) - 1f
      out.scale = 1f + if (effect.variant >= 0) s else -s * FluidSymbolProfile.BounceDownFactor
    }
    FluidSymbolEffect.KindPulse -> {
      out.alpha = 1f - (1f - FluidSymbolProfile.PulseMinAlpha) * (1f - cos(2f * PI.toFloat() * phase / cycle)) / 2f
    }
    FluidSymbolEffect.KindWiggle -> {
      if (reduced) return false
      val o = FluidSymbolProfile.WiggleOffset.at(phase)
      when (effect.variant) {
        FluidSymbolEffect.WiggleForward -> out.translationX = if (rtl) -o else o
        FluidSymbolEffect.WiggleVertical -> out.translationY = -o
        else -> {
          val sign = if (effect.variant % 2 == 0) 1f else -1f
          out.rotation = sign * o / FluidSymbolProfile.WigglePeak * FluidSymbolProfile.WiggleDegrees
          out.pivotX = effect.pivotX
          out.pivotY = effect.pivotY
        }
      }
    }
    FluidSymbolEffect.KindRotate -> {
      if (reduced) return false
      val degrees = if (!indefinite) {
        FluidSymbolProfile.RotateTurn.at(phase) * 360f
      } else if (stopAt.isNaN() || ms <= stopAt) {
        fluidSymbolSpinDegrees(t)
      } else {
        val from = fluidSymbolSpinDegrees(stopAt * effect.speed)
        val target = ceil(from / 360f - 1e-4f) * 360f
        val span = fluidSymbolSpinStopMs(target - from)
        val k = ((t - stopAt * effect.speed) / span).coerceIn(0f, 1f)
        from + (target - from) * (1f - (1f - k) * (1f - k) * (1f - k))
      }
      out.rotation = effect.variant * (degrees - floor(degrees / 360f) * 360f)
      out.pivotX = effect.pivotX
      out.pivotY = effect.pivotY
    }
    FluidSymbolEffect.KindBreathe -> {
      val rise = FluidSymbolProfile.BreatheRiseMs
      val k = when {
        phase < rise -> (1f - cos(PI.toFloat() * phase / rise)) / 2f
        phase < 2f * rise -> (1f + cos(PI.toFloat() * (phase - rise) / rise)) / 2f
        else -> 0f
      }
      if (!reduced) out.scale = 1f + (FluidSymbolProfile.BreatheScale - 1f) * k
      out.alpha = 1f - (1f - FluidSymbolProfile.BreatheMinAlpha) * k
    }
    // Il colore variabile sull'icona intera; [FluidSymbol] lo fa per strati nel disegno ([layered]).
    else -> if (!layered) {
      val back = fluidSymbolVariableReturn(effect, ms, indefinite, stopAt, 1)
      out.alpha = if (back >= 0f) back else fluidSymbolVariableAlpha(effect, phase, 0, 1)
    }
  }
  return true
}

/**
 * L'opacità dello strato [layer] di [layers] nel colore variabile, alla fase [phase] (ms dentro il ciclo, già alla
 * velocità dell'effetto). Fra [FluidSymbolProfile.VariableDimAlpha] (spento) e 1 (acceso).
 */
internal fun fluidSymbolVariableAlpha(effect: FluidSymbolEffect, phase: Float, layer: Int, layers: Int): Float {
  val step = FluidSymbolProfile.VariableStepMs
  val rise = FluidSymbolProfile.VariableRiseMs
  val start = layer * step
  val on = if (effect.variant == 1) {
    // A riempire: si accende al suo turno, resta, si spengono tutte insieme dopo due tacche di pausa.
    val fallAt = (layers + 2) * step
    when {
      phase < start -> 0f
      phase < fallAt -> glassContentSmoothstep(start, start + rise, phase)
      else -> 1f - glassContentSmoothstep(fallAt, fallAt + rise, phase)
    }
  } else {
    // A uno a uno: si accende al suo turno e si spegne subito dopo.
    when {
      phase < start -> 0f
      phase < start + rise -> glassContentSmoothstep(start, start + rise, phase)
      else -> 1f - glassContentSmoothstep(start + rise, start + 2f * rise, phase)
    }
  }
  return FluidSymbolProfile.VariableDimAlpha + (1f - FluidSymbolProfile.VariableDimAlpha) * on
}


// --- L'orologio: un solo giro di fotogrammi per icona ------------------------------------------------------------

/**
 * L'orologio di un'icona: **un solo** giro di fotogrammi per tutto quello che l'icona fa (l'effetto, la sostituzione,
 * Draw On e Off), che si ferma quando tutto è a riposo. Scrive un solo stato per fotogramma ([now], ms), letto solo nel
 * livello e nel disegno: nessuna ricomposizione per fotogramma, nessuna allocazione nel giro (la chiamata del fotogramma è
 * un oggetto solo, fatto una volta). [drawVersion] cambia solo quando parte qualcosa che cambia il disegno (il colore
 * variabile, Draw On e Off), così il disegno si iscrive a [now] solo allora.
 */
internal class FluidSymbolClock {
  /** L'ora dell'icona, in ms dal primo fotogramma. Lo leggono solo il livello e il disegno. */
  val now = mutableFloatStateOf(0f)

  /** Cambia quando parte un canale che cambia il disegno. */
  val drawVersion = mutableIntStateOf(0)

  var effect: FluidSymbolEffect? = null
  var indefinite = false
  var layers = 1

  /** Inizio di ogni canale: [Pending] vuol dire «al prossimo fotogramma», [Idle] fermo. */
  var effectStart = Idle
  var effectStop = Float.NaN
  var replaceStart = Idle
  var drawStart = Idle

  /** La sostituzione in corso e' una Magic Replace (dura [FluidSymbolMagic.Ms]) e non quella sfocata. */
  var replaceMagic = false

  /** Chiamata (dal fotogramma) quando la sostituzione è finita: chi esce può lasciare la composizione. */
  var onReplaceDone: (() -> Unit)? = null

  /** I fotogrammi fatti, per le prove. */
  var frames = 0L
    private set

  private var origin = -1L
  private var job: Job? = null
  private var running = false

  /**
   * Il ciclo di vita di chi lo contiene è sotto STARTED (R2): una pagina coperta ma composta, l'app in sottofondo. Niente
   * fotogrammi; i canali restano dove sono e ripartono al ritorno.
   */
  var stopped = false
    private set

  /**
   * «Riduci movimento» (R2): rotazione, oscillazione e rimbalzo restano a riposo, e a riposo non chiedono fotogrammi. Le
   * opacità (pulsazione, respiro, colore variabile) e la sostituzione continuano.
   */
  var reduced = false
    private set

  private val onFrame: (Long) -> Unit = { nanos ->
    if (origin < 0L) origin = nanos
    val t = (nanos - origin) / 1_000_000f
    if (effectStart.isNaN()) effectStart = t
    if (replaceStart.isNaN()) replaceStart = t
    if (drawStart.isNaN()) drawStart = t
    frames++
    val replacing = replaceStart != Idle
    running = busy(t) && !restsWhileReduced()
    if (replacing && replaceStart == Idle) onReplaceDone?.invoke()
    now.floatValue = t
  }

  /** Vero se un canale è in corso al tempo [t]; i canali finiti tornano [Idle]. */
  fun busy(t: Float): Boolean {
    val e = effect
    if (e != null && effectStart != Idle && !effectStart.isNaN() &&
      t - effectStart >= fluidSymbolEndMs(e, indefinite, effectStop - effectStart, layers)
    ) {
      effectStart = Idle
      effectStop = Float.NaN
    }
    val replaceMs = if (replaceMagic) FluidSymbolMagic.Ms else FluidSymbolProfile.ReplaceMs
    if (replaceStart != Idle && !replaceStart.isNaN() && t - replaceStart >= replaceMs) replaceStart = Idle
    if (drawStart != Idle && !drawStart.isNaN() && t - drawStart >= FluidSymbolProfile.DrawMs) drawStart = Idle
    return effectStart != Idle || replaceStart != Idle || drawStart != Idle
  }

  /** Vero se l'unico canale aperto è un effetto che col movimento ridotto non muove niente. */
  private fun restsWhileReduced(): Boolean {
    if (!reduced || replaceStart != Idle || drawStart != Idle) return false
    val kind = effect?.kind ?: return true
    return kind == FluidSymbolEffect.KindBounce || kind == FluidSymbolEffect.KindWiggle || kind == FluidSymbolEffect.KindRotate
  }

  private fun channelOpen(): Boolean = effectStart != Idle || replaceStart != Idle || drawStart != Idle

  /** Fa partire il giro dei fotogrammi in [scope], se non gira già (e se chi lo contiene è in vista). */
  fun kick(scope: CoroutineScope) {
    running = true
    if (stopped || job?.isActive == true) return
    job = scope.launch {
      while (running && !stopped) withFrameNanos(onFrame)
    }
  }

  /** Il ciclo di vita sopra o sotto STARTED: sotto il giro si ferma, sopra riparte se un canale è aperto. */
  fun setStopped(value: Boolean, scope: CoroutineScope) {
    if (stopped == value) return
    stopped = value
    if (value) {
      job?.cancel()
      job = null
    } else if (running || channelOpen()) {
      kick(scope)
    }
  }

  /** Il movimento ridotto acceso o spento: spento, un effetto rimasto aperto riparte. */
  fun setReduced(value: Boolean, scope: CoroutineScope) {
    if (reduced == value) return
    reduced = value
    if (!value && channelOpen()) kick(scope)
  }

  fun cancel() {
    job?.cancel()
    job = null
    running = false
  }

  /** L'effetto a colpo singolo: riparte da capo. */
  fun play(effect: FluidSymbolEffect, scope: CoroutineScope) {
    this.effect = effect
    indefinite = false
    effectStop = Float.NaN
    effectStart = Pending
    if (effect.kind == FluidSymbolEffect.KindVariable) drawVersion.intValue++
    kick(scope)
  }

  /** L'effetto attivo: parte, o (se [active] è falso) finisce il ciclo in corso. Si può chiamare a ogni composizione. */
  fun setActive(effect: FluidSymbolEffect, active: Boolean, scope: CoroutineScope) {
    if (active) {
      if (this.effect == effect && indefinite && effectStart != Idle && effectStop.isNaN()) return
      this.effect = effect
      indefinite = true
      effectStop = Float.NaN
      effectStart = Pending
      if (effect.kind == FluidSymbolEffect.KindVariable) drawVersion.intValue++
      kick(scope)
    } else if (indefinite && effectStart != Idle && effectStop.isNaN()) {
      if (effectStart.isNaN()) {
        effectStart = Idle
      } else {
        effectStop = now.floatValue
        kick(scope)
      }
    }
  }

  /** Draw On o Off: parte al prossimo fotogramma. */
  fun startDraw(scope: CoroutineScope) {
    drawStart = Pending
    drawVersion.intValue++
    kick(scope)
  }

  /** Il fotogramma dell'effetto adesso. Legge sempre [now], così il livello segue ogni canale. */
  fun effectFrame(out: FluidSymbolFrame, reduced: Boolean, rtl: Boolean, layered: Boolean = false): Boolean {
    val t = now.floatValue
    val e = effect
    if (e == null || effectStart == Idle || effectStart.isNaN()) {
      out.rest()
      return false
    }
    return fluidSymbolEffectFrame(e, t - effectStart, indefinite, effectStop - effectStart, reduced, out, rtl, layered)
  }

  companion object {
    const val Idle: Float = Float.NEGATIVE_INFINITY
    const val Pending: Float = Float.NaN
  }
}

// --- Il modificatore -------------------------------------------------------------------------------------------

/**
 * Suona [effect] sul contenuto (un'icona, un'immagine) ogni volta che [trigger] cambia, come `symbolEffect(_:value:)` di
 * SwiftUI: la prima composizione non suona. Lavora sul simbolo intero (scala, spostamento, rotazione, opacità nel
 * livello del contenuto): il contenuto non si ridisegna e non si ricompone. Il colore variabile per strati, la
 * sostituzione e Draw On / Off sono in [FluidSymbol]; qui il colore variabile fa salire e scendere l'icona intera.
 *
 * Esempio, la scheda della tab bar che rimbalza quando la scegli:
 * `FluidIcon(FluidIcons.Home, null, Modifier.fluidSymbolEffect(FluidSymbolEffect.Bounce, trigger = selezioni))`.
 */
fun Modifier.fluidSymbolEffect(effect: FluidSymbolEffect, trigger: Any?): Modifier =
  this then FluidSymbolEffectElement(effect, trigger, discrete = true, active = false)

/**
 * Tiene [effect] in corso finché [active] è vero, come `symbolEffect(_:isActive:)`: la sincronizzazione che gira, il
 * microfono che respira. Spento, finisce il ciclo in corso e si ferma a riposo (la rotazione finisce il giro frenando).
 * Fuori dallo schermo il giro dei fotogrammi si ferma col nodo; si ferma anche in una pagina coperta (il ciclo di vita
 * sotto STARTED) e riparte al ritorno, e con «Riduci movimento» rotazione, oscillazione e rimbalzo restano a riposo
 * senza chiedere fotogrammi.
 */
fun Modifier.fluidSymbolEffectWhile(effect: FluidSymbolEffect, active: Boolean): Modifier =
  this then FluidSymbolEffectElement(effect, null, discrete = false, active = active)

private class FluidSymbolEffectElement(
  val effect: FluidSymbolEffect,
  val trigger: Any?,
  val discrete: Boolean,
  val active: Boolean,
) : ModifierNodeElement<FluidSymbolEffectNode>() {
  override fun create() = FluidSymbolEffectNode(effect, trigger, discrete, active)

  override fun update(node: FluidSymbolEffectNode) = node.update(effect, trigger, discrete, active)

  override fun InspectorInfo.inspectableProperties() {
    name = if (discrete) "fluidSymbolEffect" else "fluidSymbolEffectWhile"
    properties["effect"] = effect
    properties[if (discrete) "trigger" else "active"] = if (discrete) trigger else active
  }

  override fun equals(other: Any?): Boolean = other is FluidSymbolEffectElement && other.effect == effect &&
    other.trigger == trigger && other.discrete == discrete && other.active == active

  override fun hashCode(): Int = ((effect.hashCode() * 31 + (trigger?.hashCode() ?: 0)) * 31 + discrete.hashCode()) * 31 +
    active.hashCode()
}

private class FluidSymbolEffectNode(
  private var effect: FluidSymbolEffect,
  private var trigger: Any?,
  private var discrete: Boolean,
  private var active: Boolean,
) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
  private val clock = FluidSymbolClock()
  private val frame = FluidSymbolFrame()

  private val layerBlock: GraphicsLayerScope.() -> Unit = {
    val reduced = currentValueOf(LocalFluidMotionPolicy).reducedMotion
    val rtl = currentValueOf(LocalLayoutDirection) == LayoutDirection.Rtl
    clock.effectFrame(frame, reduced, rtl)
    applySymbolFrame(frame)
  }

  private var lifecycle: Lifecycle? = null
  private val lifecycleObserver = LifecycleEventObserver { owner, _ ->
    clock.setStopped(!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), coroutineScope)
  }

  override fun onAttach() {
    clock.setReduced(currentValueOf(LocalFluidMotionPolicy).reducedMotion, coroutineScope)
    // Una pagina coperta (CREATED) o l'app in sottofondo: il giro dei fotogrammi si ferma (R2).
    lifecycle = currentValueOf(LocalLifecycleOwner).lifecycle.also { it.addObserver(lifecycleObserver) }
    if (!discrete && active) clock.setActive(effect, true, coroutineScope)
  }

  override fun onDetach() {
    lifecycle?.removeObserver(lifecycleObserver)
    lifecycle = null
    clock.cancel()
    clock.effectStart = FluidSymbolClock.Idle
    clock.effectStop = Float.NaN
  }

  fun update(effect: FluidSymbolEffect, trigger: Any?, discrete: Boolean, active: Boolean) {
    val changedTrigger = trigger != this.trigger
    val changedActive = active != this.active || effect != this.effect || discrete != this.discrete
    this.effect = effect
    this.trigger = trigger
    this.discrete = discrete
    this.active = active
    if (!isAttached) return
    if (discrete && changedTrigger) clock.play(effect, coroutineScope)
    if (!discrete && changedActive) clock.setActive(effect, active, coroutineScope)
  }

  override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
    // Il movimento ridotto cambiato: Compose rimisura i nodi che leggono i locali quando i locali cambiano.
    clock.setReduced(currentValueOf(LocalFluidMotionPolicy).reducedMotion, coroutineScope)
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layerBlock) }
  }
}

/** Porta un fotogramma dell'effetto nel livello: lo spostamento è in frazione del riquadro. */
internal fun GraphicsLayerScope.applySymbolFrame(frame: FluidSymbolFrame) {
  scaleX = frame.scale
  scaleY = frame.scale
  translationX = frame.translationX * size.width
  translationY = frame.translationY * size.height
  rotationZ = frame.rotation
  alpha = frame.alpha
  transformOrigin = TransformOrigin(frame.pivotX, frame.pivotY)
}

// --- Il simbolo che sa i suoi strati -------------------------------------------------------------------------------

/**
 * Un'icona che sa i suoi strati: il simbolo di SF Symbols per le icone di
 * [dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons]. Oltre a ciò che fa [fluidSymbolEffect] sa:
 *
 * - **il colore variabile per strati** ([FluidSymbolEffect.VariableColor]): le tacche del Wi-Fi o le onde
 *   dell'altoparlante si accendono in fila (gli strati che cambiano sono i tratti dell'icona, dal più piccolo al più
 *   grande; il resto, come il corpo dell'altoparlante, resta acceso);
 * - **la sostituzione** (`.replace`): quando [icon] cambia fra due icone **parenti** (la campana e la campana barrata,
 *   la campana e quella che suona, il microfono e il microfono muto) e' la **Magic Replace** di SF Symbols 6: resta ferma
 *   la parte comune, la barra si disegna con la penna e taglia la base mentre passa (o si ritira), il pallino compare con
 *   un rimbalzo, la base rimbalza appena; 0,7 s, nitida. Fra icone che non hanno niente in comune (play e pausa) la
 *   vecchia esce e la nuova entra **sfocate**, con le curve di Apple misurate sullo scambio d'icona delle barre (R7,
 *   300 ms): il tono di F8, «il contenuto che cambia identità entra sfocato». Lo decide l'engine dai tracciati
 *   (`SymbolMagicPlanner`), come Apple ricade sulla sostituzione semplice quando i simboli non sono parenti;
 * - **Draw On e Draw Off** (SF Symbols 7): [drawn] che passa a vero disegna l'icona con la penna, a falso la cancella. I
 *   tratti si percorrono da un capo all'altro, le sagome con un buco (un anello, la campanella) girando da ore 12, il
 *   resto compare al suo turno; 0,8 s. È fedele dove l'icona è fatta di tratti (la spunta, il Wi-Fi, le onde); una
 *   croce o una freccia, che nel contorno di Material sono un pezzo solo, compaiono.
 *
 * Un solo giro di fotogrammi per icona, che si ferma a riposo: ferma, l'icona non ridisegna. Con «Riduci movimento»
 * restano le opacità (e un effetto continuo che muove soltanto non chiede fotogrammi); Draw On e Off diventano
 * immediati. In una pagina coperta (il ciclo di vita sotto STARTED) il giro si ferma e riparte al ritorno.
 *
 * @param icon l'icona; quando cambia c'è la sostituzione.
 * @param contentDescription ciò che TalkBack dice; null se l'icona accompagna un testo.
 * @param tint il colore; di serie `LocalContentColor`.
 * @param size il lato del riquadro; di serie quello del posto ([LocalFluidIconSize]).
 * @param effect l'effetto: a colpo singolo a ogni cambio di [trigger], o continuo finché [active] è vero.
 * @param trigger il valore che fa suonare [effect] (la prima composizione non suona).
 * @param active per gli effetti continui: vero finché devono andare.
 * @param drawn falso nasconde l'icona con Draw Off; tornando vero la disegna con Draw On.
 */
@Composable
fun FluidSymbol(
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  size: Dp = LocalFluidIconSize.current,
  effect: FluidSymbolEffect? = null,
  trigger: Any? = null,
  active: Boolean = false,
  drawn: Boolean = true,
) {
  val scope = rememberCoroutineScope()
  val clock = remember { FluidSymbolClock() }
  val holder = remember { FluidSymbolHolder(icon, drawn, trigger) }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  // Si legge per ricomporre quando chi esce ha finito.
  holder.exitVersion.intValue
  var replaced = false
  if (icon != holder.shown) {
    // Magic Replace dove i tracciati lo permettono (le due icone sono parenti), altrimenti la sostituzione sfocata.
    val from = SymbolGeometryBuilder.of(holder.shown)
    val to = SymbolGeometryBuilder.of(icon)
    val plan = SymbolMagicPlanner.plan(from, to)
    if (plan != null) {
      holder.magic = SymbolMagicDrawing(from, plan, to)
      holder.previous = null
    } else {
      holder.magic = null
      holder.previous = holder.shown
    }
    holder.shown = icon
    replaced = true
    // La partenza si segna subito, non nel SideEffect: il livello dell'icona che entra puo' ricalcolarsi mentre la
    // composizione si applica, prima del SideEffect, e con la sostituzione non ancora partita mostrerebbe per un
    // fotogramma l'icona nuova nitida sopra la vecchia (visto nei fermi di F8.5b). Il campo non e' stato osservato.
    clock.replaceMagic = holder.magic != null
    clock.replaceStart = FluidSymbolClock.Pending
  }
  var redraw = false
  if (drawn != holder.drawOn) {
    holder.drawOn = drawn
    redraw = !reduced
  }
  var fire = false
  if (trigger != holder.trigger) {
    holder.trigger = trigger
    fire = true
  }
  val geometry = remember(icon) { SymbolGeometryBuilder.of(icon) }
  clock.layers = fluidSymbolVariableLayers(geometry).size.coerceAtLeast(1)
  SideEffect {
    clock.onReplaceDone = {
      holder.previous = null
      holder.magic = null
      holder.exitVersion.intValue++
    }
    clock.setReduced(reduced, scope)
    if (replaced) clock.kick(scope)
    if (redraw) clock.startDraw(scope)
    if (effect != null) {
      if (active || clock.indefinite) clock.setActive(effect, active, scope)
      if (!active && fire) clock.play(effect, scope)
    }
  }
  DisposableEffect(clock) { onDispose { clock.cancel() } }
  // Una pagina coperta (CREATED) o l'app in sottofondo: il giro dei fotogrammi si ferma e riparte al ritorno (R2).
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, clock) {
    val observer = LifecycleEventObserver { owner, _ ->
      clock.setStopped(!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), scope)
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  val exiting = holder.previous
  Box(
    modifier
      .size(size)
      .then(
        if (contentDescription != null) {
          Modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }
        } else {
          Modifier
        },
      ),
  ) {
    if (exiting != null) {
      key(exiting) {
        SymbolLayerContent(SymbolGeometryBuilder.of(exiting), exiting, tint, clock, SymbolRole.Exit, reduced, rtl, true, null)
      }
    }
    SymbolLayerContent(geometry, icon, tint, clock, if (exiting != null) SymbolRole.Enter else SymbolRole.Rest,
      reduced, rtl, holder.drawOn, holder.magic)
  }
}

/** Ciò che il simbolo ricorda fra una composizione e l'altra, senza stato osservato (tranne [exitVersion]). */
private class FluidSymbolHolder(var shown: ImageVector, var drawOn: Boolean, var trigger: Any?) {
  var previous: ImageVector? = null

  /** La Magic Replace in corso, se le due icone sono parenti. */
  var magic: SymbolMagicDrawing? = null
  val exitVersion = mutableIntStateOf(0)
}

private enum class SymbolRole { Rest, Enter, Exit }

/** Gli strati che cambiano nel colore variabile: i tratti, dal più piccolo; se non ce n'è, tutti. */
internal fun fluidSymbolVariableLayers(geometry: SymbolGeometry): List<Int> {
  val strokes = geometry.layers.indices.filter { geometry.layers[it].kind == SymbolDrawKind.Stroke }
  val chosen = strokes.ifEmpty { geometry.layers.indices.toList() }
  return chosen.sortedBy { abs(geometry.layers[it].outer.signedArea) }
}

/** I pezzi riusati dal disegno di un'icona: niente allocazioni nel fotogramma. */
private class SymbolDrawCache(geometry: SymbolGeometry) {
  val paths: List<Path> = geometry.layers.map { it.nodes.toPath() }
  /**
   * L'ordine di Draw On: prima le sagome che contengono (l'anello, la campanella, il corpo dell'altoparlante), poi i
   * tratti e il resto nell'ordine del tracciato, come SF Symbols che disegna il cerchio prima della spunta.
   */
  val drawOrder: IntArray = geometry.layers.indices.sortedBy { if (geometry.layers[it].kind == SymbolDrawKind.Sweep) 0 else 1 }
    .toIntArray()
  val variableRank: IntArray = IntArray(geometry.layers.size) { -1 }.also { rank ->
    fluidSymbolVariableLayers(geometry).forEachIndexed { order, layer -> rank[layer] = order }
  }
  val sweepRects: List<Rect> = geometry.layers.map {
    val r = max(geometry.viewportWidth, geometry.viewportHeight)
    Rect(it.centerX - r, it.centerY - r, it.centerX + r, it.centerY + r)
  }
  val window = Path()
  val xs = FloatArray(symbolStrokeWindowCapacity())
  val ys = FloatArray(symbolStrokeWindowCapacity())
  val cap = FloatArray(3)
  val frame = FluidSymbolFrame()
  val blurs = arrayOfNulls<RenderEffect>(BlurSteps + 1)
  val blurReady = BooleanArray(BlurSteps + 1)

  companion object {
    /** La sfocatura si prende a quarti di pixel, fino a 12 px: ogni effetto si fa una volta sola. */
    const val BlurSteps = 48
  }
}

@Composable
private fun BoxScope.SymbolLayerContent(
  geometry: SymbolGeometry,
  icon: ImageVector,
  tint: Color,
  clock: FluidSymbolClock,
  role: SymbolRole,
  reduced: Boolean,
  rtl: Boolean,
  drawOn: Boolean,
  magic: SymbolMagicDrawing?,
) {
  val cache = remember(geometry) { SymbolDrawCache(geometry) }
  val mirror = icon.autoMirror && rtl
  Spacer(
    Modifier
      .matchParentSize()
      .graphicsLayer {
        val f = cache.frame
        if (role == SymbolRole.Exit) {
          clock.now.floatValue
          f.rest()
        } else {
          clock.effectFrame(f, reduced, rtl, layered = true)
        }
        applySymbolFrame(f)
        if (magic != null && clock.replaceStart != FluidSymbolClock.Idle && !reduced) {
          // La Magic Replace: la base rimbalza appena, niente sfocatura (Apple la tiene nitida).
          val start = clock.replaceStart
          val t = if (start.isNaN()) 0f else clock.now.floatValue - start
          val s = 1f + magic.plan.bounce * (FluidSymbolProfile.BounceScale.at(t) - 1f) * FluidSymbolMagic.BaseAmplitude
          scaleX *= s
          scaleY *= s
          renderEffect = null
        } else if (role != SymbolRole.Rest && clock.replaceStart != FluidSymbolClock.Idle) {
          val start = clock.replaceStart
          val u = if (start.isNaN()) 0f else (clock.now.floatValue - start) / GlassContentDefaults.DurationMs
          val content = if (role == SymbolRole.Exit) GlassContentRole.Exit else GlassContentRole.Enter
          val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          alpha *= GlassContentCurves.alpha(content, u, blurAvailable)
          if (!reduced) {
            val s = GlassContentCurves.scale(content, u)
            scaleX *= s
            scaleY *= s
          }
          renderEffect = if (blurAvailable) {
            symbolBlur(cache, glassContentSigmaPx(GlassContentCurves.blur(content, u) * GlassContentDefaults.PeakBlur,
              size.minDimension))
          } else {
            null
          }
        } else {
          if (role == SymbolRole.Exit) alpha = 0f
          renderEffect = null
        }
      }
      .drawBehind {
        if (magic != null && clock.replaceStart != FluidSymbolClock.Idle) {
          drawSymbolMagic(magic, geometry, tint, clock, reduced, mirror)
        } else {
          drawSymbol(geometry, cache, tint, clock, drawOn, mirror)
        }
      },
  )
}

private fun symbolBlur(cache: SymbolDrawCache, sigmaPx: Float): RenderEffect? {
  val step = (sigmaPx * 4f).toInt().coerceIn(0, SymbolDrawCache.BlurSteps)
  if (step == 0) return null
  if (!cache.blurReady[step]) {
    cache.blurs[step] = glassContentBlurEffect(step / 4f)
    cache.blurReady[step] = true
  }
  return cache.blurs[step]
}

/** Disegna gli strati di un'icona col colore variabile e Draw On / Off del momento. */
private fun DrawScope.drawSymbol(
  geometry: SymbolGeometry,
  cache: SymbolDrawCache,
  tint: Color,
  clock: FluidSymbolClock,
  drawOn: Boolean,
  mirror: Boolean,
) {
  clock.drawVersion.intValue
  val e = clock.effect
  val variable = e != null && e.kind == FluidSymbolEffect.KindVariable && clock.effectStart != FluidSymbolClock.Idle
  val drawing = clock.drawStart != FluidSymbolClock.Idle
  // L'orologio si legge solo quando qualcosa cambia il disegno: un rimbalzo muove il livello, non ridisegna.
  val now = if (variable || drawing) clock.now.floatValue else 0f
  if (!drawOn && !drawing) return
  val n = geometry.layers.size
  var phase = -1f
  var back = -1f
  if (variable) {
    val effect = e!!
    val start = if (clock.effectStart.isNaN()) now else clock.effectStart
    val elapsed = now - start
    val stop = clock.effectStop - start
    if (elapsed < fluidSymbolEndMs(effect, clock.indefinite, stop, clock.layers)) {
      back = fluidSymbolVariableReturn(effect, elapsed, clock.indefinite, stop, clock.layers)
      val t = elapsed * effect.speed
      val cycle = fluidSymbolCycleMs(effect, clock.layers, clock.indefinite)
      phase = t - floor(t / cycle) * cycle
    }
  }
  val drawStart = if (clock.drawStart.isNaN()) now else clock.drawStart
  val drawElapsed = if (drawing) now - drawStart else 0f
  val layerMs = FluidSymbolProfile.DrawMs / (1f + FluidSymbolProfile.DrawStagger * (n - 1).coerceAtLeast(0))
  val sx = size.width / geometry.viewportWidth
  val sy = size.height / geometry.viewportHeight
  val center = Offset(size.width / 2f, size.height / 2f)
  withTransform({
    if (mirror) scale(-1f, 1f, pivot = center)
    scale(sx, sy, pivot = Offset.Zero)
  }) {
    for (k in 0 until n) {
      val i = cache.drawOrder[k]
      val rank = cache.variableRank[i]
      val alpha = when {
        rank < 0 || phase < 0f -> 1f
        back >= 0f -> back
        else -> fluidSymbolVariableAlpha(e!!, phase, rank, clock.layers)
      }
      if (!drawing) {
        drawPath(cache.paths[i], tint, alpha = alpha)
        continue
      }
      val start = k * FluidSymbolProfile.DrawStagger * layerMs
      val p = glassContentSmoothstep(0f, 1f, ((drawElapsed - start) / layerMs).coerceIn(0f, 1f))
      val from = if (drawOn) 0f else p
      val to = if (drawOn) p else 1f
      drawSymbolWindow(geometry.layers[i], i, cache, tint, alpha, from, to)
    }
  }
}

/** Il pezzo [from]..[to] dello strato: il tratto fra due frazioni, lo spicchio, o la comparsa. */
private fun DrawScope.drawSymbolWindow(
  layer: SymbolLayer,
  index: Int,
  cache: SymbolDrawCache,
  tint: Color,
  alpha: Float,
  from: Float,
  to: Float,
) {
  if (to - from <= 0f) return
  if (from <= 0f && to >= 1f) {
    drawPath(cache.paths[index], tint, alpha = alpha)
    return
  }
  val stroke = layer.stroke
  when {
    stroke != null -> {
      val count = symbolStrokeWindow(stroke, from, to, cache.xs, cache.ys)
      if (count < 3) return
      val path = cache.window
      path.reset()
      path.moveTo(cache.xs[0], cache.ys[0])
      for (k in 1 until count) path.lineTo(cache.xs[k], cache.ys[k])
      path.close()
      drawPath(path, tint, alpha = alpha)
      // La punta tonda della penna, dove il tratto è arrivato (o da dove si cancella).
      symbolStrokeCap(stroke, if (from > 0f) from else to, cache.cap)
      drawCircle(tint, radius = cache.cap[2], center = Offset(cache.cap[0], cache.cap[1]), alpha = alpha)
    }
    layer.kind == SymbolDrawKind.Sweep -> {
      val path = cache.window
      path.reset()
      path.moveTo(layer.centerX, layer.centerY)
      path.arcTo(cache.sweepRects[index], -90f + 360f * from, 360f * (to - from), false)
      path.close()
      clipPath(path) { drawPath(cache.paths[index], tint, alpha = alpha) }
    }
    else -> {
      // Compare: da metà taglia e trasparente, nella sua parte del tempo.
      val k = if (from <= 0f) to else 1f - from
      withTransform({ scale(0.5f + 0.5f * k, 0.5f + 0.5f * k, pivot = Offset(layer.centerX, layer.centerY)) }) {
        drawPath(cache.paths[index], tint, alpha = alpha * k)
      }
    }
  }
}

// --- La Magic Replace (F8.5b) ------------------------------------------------------------------------------------

/**
 * I tracciati esatti di una Magic Replace, fatti una volta al cambio d'icona: la parte comune, i pezzi nuovi e quelli
 * che spariscono (operazioni sui tracciati, ogni pezzo ritagliato col suo contorno), e la regione dei pezzi di bande e
 * pallini, fuori dalla quale i pezzi diversi si incrociano soltanto. Nel fotogramma si riusano due tracciati di
 * ritaglio: nessuna allocazione.
 *
 * Dentro il contorno di un pallino non c'e' niente di fermo: il pallino si disegna intero, e quello che l'altra icona
 * ha li' ([badgeOther]: in `notifications_unread`, il tratto della campana che il pallino copre) si incrocia col resto,
 * cioe' va via con l'intaglio prima che il pallino arrivi (F8.5c).
 */
internal class SymbolMagicDrawing(from: SymbolGeometry, val plan: SymbolMagicPlan, to: SymbolGeometry) {
  /** La parte comune fuori dai pallini, ferma; [shared] e' tutta, per «Riduci movimento». */
  val common = Path()
  val shared = Path()
  val added = Path()
  val removed = Path()
  val special = Path()
  val bandAdded: List<List<Path>>
  val bandRemoved: List<List<Path>>
  val badges: List<Path>
  val badgeOther: List<Path>
  val clip = Path()
  val cap = Path()

  init {
    val a = from.layers.flatMap { it.nodes }.toPath()
    val b = to.layers.flatMap { it.nodes }.toPath()
    shared.op(a, b, PathOperation.Intersect)
    added.op(b, a, PathOperation.Difference)
    removed.op(a, b, PathOperation.Difference)
    fun outline(piece: SymbolPiece): Path {
      val outline = Path()
      outline.moveTo(piece.xs[0], piece.ys[0])
      for (k in 1 until piece.xs.size) outline.lineTo(piece.xs[k], piece.ys[k])
      outline.close()
      special.addPath(outline)
      return outline
    }
    fun cut(source: Path, outline: Path): Path = Path().apply { op(source, outline, PathOperation.Intersect) }
    bandAdded = plan.bands.map { band -> band.added.map { cut(added, outline(it)) } }
    bandRemoved = plan.bands.map { band -> band.removed.map { cut(removed, outline(it)) } }
    val zone = Path()
    val outlines = plan.badges.map { outline(it.piece).also { o -> zone.addPath(o) } }
    badges = plan.badges.mapIndexed { i, badge -> cut(if (badge.appears) b else a, outlines[i]) }
    badgeOther = plan.badges.mapIndexed { i, badge -> cut(if (badge.appears) a else b, outlines[i]) }
    if (plan.badges.isEmpty()) common.addPath(shared) else common.op(shared, zone, PathOperation.Difference)
  }
}

/** Il semipiano della [band] dietro la punta ([behind] vero: fino a [front]) o davanti, in [path]. */
private fun symbolHalfPlane(path: Path, band: SymbolBand, front: Float, behind: Boolean) {
  path.reset()
  val far = 200f
  val vx = -band.uy * far
  val vy = band.ux * far
  val from = if (behind) -far else front
  val to = if (behind) front else far
  val ax = band.startX + band.ux * from
  val ay = band.startY + band.uy * from
  val bx = band.startX + band.ux * to
  val by = band.startY + band.uy * to
  path.moveTo(ax + vx, ay + vy)
  path.lineTo(bx + vx, by + vy)
  path.lineTo(bx - vx, by - vy)
  path.lineTo(ax - vx, ay - vy)
  path.close()
}

/**
 * Un fotogramma della Magic Replace: la parte comune ferma; lungo ogni banda la penna (o la ritirata) col suo capo tondo,
 * e i pezzi della banda dell'altra icona (il taglio nella campana) si aprono o si chiudono quando la penna ci passa; i
 * pallini con la dissolvenza e il rimbalzo; il resto (i bordi che cambiano fra due disegni simili, il taglio per il
 * pallino) si incrocia.
 */
private fun DrawScope.drawSymbolMagic(
  magic: SymbolMagicDrawing,
  geometry: SymbolGeometry,
  tint: Color,
  clock: FluidSymbolClock,
  reduced: Boolean,
  mirror: Boolean,
) {
  val start = clock.replaceStart
  val now = clock.now.floatValue
  val t = if (start.isNaN()) 0f else now - start
  val plan = magic.plan
  val sx = size.width / geometry.viewportWidth
  val sy = size.height / geometry.viewportHeight
  val center = Offset(size.width / 2f, size.height / 2f)
  withTransform({
    if (mirror) scale(-1f, 1f, pivot = center)
    scale(sx, sy, pivot = Offset.Zero)
  }) {
    drawPath(if (reduced) magic.shared else magic.common, tint)
    if (reduced) {
      // Niente penna e niente rimbalzi: i pezzi diversi si incrociano e basta.
      val k = glassContentSmoothstep(0f, FluidSymbolMagic.ReducedMs, t)
      drawPath(magic.added, tint, alpha = k)
      drawPath(magic.removed, tint, alpha = 1f - k)
      return@withTransform
    }
    // Il resto: fuori dai pezzi delle bande e dei pallini.
    val k = glassContentSmoothstep(plan.restStartMs, plan.restMs, t)
    clipPath(magic.special, ClipOp.Difference) {
      drawPath(magic.added, tint, alpha = k)
      drawPath(magic.removed, tint, alpha = 1f - k)
    }
    for (i in plan.bands.indices) {
      val band = plan.bands[i]
      val p = if (band.drawsOn) {
        glassContentSmoothstep(0f, FluidSymbolMagic.PenMs, t)
      } else {
        1f - glassContentSmoothstep(0f, FluidSymbolMagic.RetractMs, t)
      }
      val front = p * band.length
      // La penna lascia [material] dietro di se'; davanti c'e' ancora [other] (il taglio nella base, o la base intera).
      val material = if (band.drawsOn) magic.bandAdded[i] else magic.bandRemoved[i]
      val other = if (band.drawsOn) magic.bandRemoved[i] else magic.bandAdded[i]
      val otherAt = if (band.drawsOn) band.removedAt else band.addedAt
      if (p >= 1f) {
        for (k2 in material.indices) drawPath(material[k2], tint)
        continue
      }
      if (p > 0f) {
        symbolHalfPlane(magic.clip, band, front, behind = true)
        clipPath(magic.clip) { for (k2 in material.indices) drawPath(material[k2], tint) }
        magic.cap.reset()
        magic.cap.asAndroidPath().addCircle(band.startX + band.ux * front, band.startY + band.uy * front,
          band.capRadius, android.graphics.Path.Direction.CW)
        clipPath(magic.cap) { for (k2 in material.indices) drawPath(material[k2], tint) }
      }
      // I pezzi dell'altra icona cambiano interi, in un lampo, quando la punta passa sul loro centro: tagliati dal fronte
      // lascerebbero schegge (il fianco della campana che Material toglie per intero).
      for (k2 in other.indices) {
        val alpha = 1f - glassContentSmoothstep(otherAt[k2] - FluidSymbolMagic.PieceReach,
          otherAt[k2] + FluidSymbolMagic.PieceReach, front)
        if (alpha > 0f) drawPath(other[k2], tint, alpha = alpha)
      }
    }
    for (i in plan.badges.indices) {
      val badge = plan.badges[i]
      val path = magic.badges[i]
      val pivot = Offset(badge.centerX, badge.centerY)
      // Quello che l'altra icona ha sotto il pallino va e viene col resto (l'intaglio).
      val other = if (badge.appears) 1f - k else k
      if (other > 0f) drawPath(magic.badgeOther[i], tint, alpha = other)
      if (badge.appears) {
        val alpha = glassContentSmoothstep(0f, FluidSymbolMagic.BadgeFadeMs, t)
        if (alpha > 0f) {
          val since = t - FluidSymbolMagic.BadgeDelayMs
          val bounce = FluidSymbolProfile.BounceScale.at(since * FluidSymbolMagic.BadgeSpeed) - 1f
          val s = 1f + bounce * FluidSymbolMagic.BadgeAmplitude
          withTransform({ scale(s, s, pivot = pivot) }) { drawPath(path, tint, alpha = alpha) }
        }
      } else {
        val q = (t / (FluidSymbolMagic.BadgeOutMs - FluidSymbolMagic.CutMs)).coerceIn(0f, 1f)
        val s = 1f - 0.1f * q
        withTransform({ scale(s, s, pivot = pivot) }) { drawPath(path, tint, alpha = 1f - q) }
      }
    }
  }
}
