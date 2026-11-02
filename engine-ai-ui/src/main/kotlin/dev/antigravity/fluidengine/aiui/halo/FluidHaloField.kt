package dev.antigravity.fluidengine.aiui.halo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// Il campo di luce dell'assistente: un solo orologio, un solo pittore, tre usi. Portato da PampAI
// (`halo/HaloField.kt`), dove era nato e dove era l'unico a servire tre superfici diverse.
//
// 1. L'alone di una sessione sopra un'altra app: macchie che viaggiano lungo il bordo dello schermo.
// 2. L'alone della capsula del composer mentre ascolta: stesso pittore, `path` diverso.
// 3. L'aurora sullo sfondo di una chat mentre l'assistente lavora: macchie larghe che vagano
//    dentro la pagina invece che sul suo bordo.
//
// La regola che tiene insieme tutto: il tempo e l'ampiezza vivono in `FluidHaloClock`, due stati che si
// leggono SOLO dentro il draw. Cosi' a sessanta fotogrammi al secondo si invalida il disegno di un
// nodo, e nessun fratello ricompone (la card con il Markdown, la lista della chat).

/**
 * Come si muove l'alone: tace (lento, basso), ascolta (segue la voce), lavora (pulsa), scrive
 * (scorre), ha finito, ha sbagliato. L'aureola in cima ([FluidAssistantHalo]) non ha un umore
 * `IDLE`: lo legge come `DONE`.
 */
enum class FluidHaloMood { HIDDEN, IDLE, LISTENING, WORKING, WRITING, DONE, ERROR }

/** L'umore dell'alone dallo stato dell'assistente. */
fun AssistantState.haloMood(): FluidHaloMood = when (this) {
  is AssistantState.Listening -> FluidHaloMood.LISTENING
  AssistantState.Transcribing, is AssistantState.Classifying, is AssistantState.Working, is AssistantState.WaitingRateLimit,
  is AssistantState.SwitchingProvider, is AssistantState.AwaitingConfirmation,
  -> FluidHaloMood.WORKING
  is AssistantState.Answering -> FluidHaloMood.WRITING
  is AssistantState.Done -> FluidHaloMood.DONE
  is AssistantState.Failed -> FluidHaloMood.ERROR
  else -> FluidHaloMood.IDLE
}

/**
 * Quanti giri del perimetro fa una macchia in un secondo. Mentre scrive scorre (un giro in undici
 * secondi), in ascolto va a passo d'uomo, a riposo quasi non si muove ma si muove: e' quel poco di
 * moto che distingue un alone da una cornice. Da nascosto tiene il passo di riposo, cosi' durante
 * la dissolvenza in uscita le macchie non si inchiodano.
 */
fun FluidHaloMood.perimeterSpeed(): Float = when (this) {
  FluidHaloMood.WRITING -> 0.09f
  FluidHaloMood.LISTENING -> 0.06f
  FluidHaloMood.WORKING -> 0.033f
  FluidHaloMood.IDLE, FluidHaloMood.HIDDEN -> 0.015f
  FluidHaloMood.DONE, FluidHaloMood.ERROR -> 0.02f
}

/**
 * Il moltiplicatore del tempo dell'orologio per questo umore: 1 in ascolto. Il pittore converte il
 * tempo in giri del perimetro con [HaloBaseSpeed], quindi `tempo() * HaloBaseSpeed == perimeterSpeed()`;
 * il respiro e l'ondeggiare delle macchie scalano insieme al moto.
 */
fun FluidHaloMood.tempo(): Float = perimeterSpeed() / HaloBaseSpeed

/**
 * Quanto e' acceso l'alone, da 0 a 1. In ascolto segue il microfono ma non parte mai da zero: fra
 * due sillabe deve restare qualcosa. A riposo e' basso, nell'errore e al lavoro e' alto.
 */
fun FluidHaloMood.amplitudeTarget(level: Float): Float = when (this) {
  FluidHaloMood.HIDDEN -> 0f
  FluidHaloMood.LISTENING -> 0.30f + 0.70f * level.coerceIn(0f, 1f)
  FluidHaloMood.IDLE -> 0.35f
  FluidHaloMood.DONE -> 0.45f
  FluidHaloMood.ERROR, FluidHaloMood.WORKING, FluidHaloMood.WRITING -> 0.6f
}

/**
 * La forma del campo: quante macchie, quanto grandi, quanto accese, e come si sommano.
 *
 * **Il blend.** [BlendMode.Plus] somma luce: sopra uno scrim scuro, o su una pagina scura, due
 * macchie che si toccano fanno una luce piu' viva, e non si vede mai un bordo. Ma la somma va verso
 * il bianco: su una superficie chiara satura e sparisce. Chi disegna sopra una pagina chiara passa
 * [BlendMode.SrcOver] ([haloBlendForTheme] sceglie in base alla luminanza).
 *
 * @param blobCount quante macchie sul perimetro. Sotto le dieci si contano; a quattordici, con il
 *   raggio di sessanta dp, si vedono come una luce mossa.
 * @param blobRadius il raggio base di una macchia; il respiro e l'ampiezza lo portano fra 0.5x e 1.2x.
 * @param peakAlpha l'alpha di una macchia a piena ampiezza (a riposo scende a 0.18).
 * @param edgeStroke il filo sul bordo, la luce che c'e' anche fra una macchia e l'altra; 0 per non averlo.
 * @param inset quanto rientra la forma dal bordo del canvas (per la capsula, che ha un margine).
 */
@Immutable
data class FluidHaloFieldSpec(
  val blobCount: Int = 14,
  val blobRadius: Dp = 64.dp,
  val peakAlpha: Float = 0.45f,
  val edgeStroke: Dp = 2.dp,
  val edgeAlpha: Float = 0.25f,
  val cornerRadius: Dp = 44.dp,
  val inset: Dp = 0.dp,
  val blendMode: BlendMode = BlendMode.Plus,
)

/** I quattro colori da cui l'alone ricava i suoi cicli per umore. */
@Immutable
data class FluidHaloColours(val accent: Color, val secondary: Color, val tertiary: Color, val error: Color) {

  /**
   * Il ciclo di colori per un umore, sei voci che il pittore ripete sulle macchie
   * (`colours[i % 6]`). L'errore e' rosso con un accenno di accento, il "fatto" torna sull'accento,
   * il resto alterna i tre colori del tema. Le alpha parziali (0.8, 0.9) sono macchie un po' piu'
   * deboli delle altre: un alone tutto uguale sembra stampato.
   */
  fun cycle(mood: FluidHaloMood): List<Color> = when (mood) {
    FluidHaloMood.ERROR -> listOf(error, error.copy(alpha = 0.8f), accent.copy(alpha = 0.5f), error, error.copy(alpha = 0.7f), accent.copy(alpha = 0.5f))
    FluidHaloMood.DONE -> listOf(accent, secondary.copy(alpha = 0.9f), accent.copy(alpha = 0.9f), tertiary.copy(alpha = 0.8f), accent, secondary.copy(alpha = 0.8f))
    else -> listOf(accent, secondary, tertiary, accent.copy(alpha = 0.85f), secondary.copy(alpha = 0.9f), tertiary.copy(alpha = 0.9f))
  }

  companion object {
    /** Primario, secondario, terziario ed errore del tema corrente. */
    @Composable
    fun fromTheme(): FluidHaloColours = MaterialTheme.colorScheme.let { FluidHaloColours(it.primary, it.secondary, it.tertiary, it.error) }
  }
}

/** [BlendMode.Plus] su una superficie scura, [BlendMode.SrcOver] su una chiara (vedi [FluidHaloFieldSpec]). */
@Composable
fun haloBlendForTheme(): BlendMode = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) BlendMode.Plus else BlendMode.SrcOver

/**
 * Il tempo e l'ampiezza smussata dell'alone. Due stati che si leggono SOLO dentro il draw: chi li
 * legge in composizione ricompone sessanta volte al secondo, e se lo merita.
 *
 * `time` e' in secondi al tempo 1 (vedi [tempo]); `amplitude` va da 0 a 1 e segue il bersaglio con
 * attacco veloce e rilascio lento, come un indicatore di livello.
 */
@Stable
class FluidHaloClock {
  var time by mutableFloatStateOf(0f)
  var amplitude by mutableFloatStateOf(0f)
}

/**
 * L'orologio dell'alone: un loop di `withFrameNanos` che accumula `time += dt * speed` e porta
 * `amplitude` verso `target()`.
 *
 * `target` si legge **dentro il loop di frame**, non in composizione: chi passa una lambda che legge
 * uno `StateFlow` del microfono a cinquanta hertz non fa ricomporre nessuno. L'attacco e' circa 0.35
 * per fotogramma a sessanta hertz e il rilascio 0.08, scalati sul dt reale. Con `economy` il loop
 * lavora un fotogramma si' e uno no (~30 Hz). Con il moto ridotto il tempo sta fermo e l'ampiezza
 * va al bersaglio in un colpo.
 *
 * Se `running` e' falso il loop non gira, ma prima lascia decadere l'ampiezza a zero: l'alone non
 * muore secco.
 */
@Composable
fun rememberFluidHaloClock(running: Boolean, speed: Float, target: () -> Float, economy: Boolean = false): FluidHaloClock {
  val clock = remember { FluidHaloClock() }
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val currentSpeed by rememberUpdatedState(speed)
  val currentTarget by rememberUpdatedState(target)
  LaunchedEffect(running, economy, reducedMotion) {
    if (reducedMotion) {
      // Niente moto: il tempo resta dov'e', l'ampiezza salta al bersaglio (e lo segue se cambia).
      snapshotFlow { if (running) currentTarget().coerceIn(0f, 1f) else 0f }.collect { clock.amplitude = it }
      return@LaunchedEffect
    }
    var last = 0L
    var skip = false
    while (true) {
      withFrameNanos { now ->
        if (economy) {
          skip = !skip
          if (skip) return@withFrameNanos
        }
        if (last != 0L) {
          // Il tetto al dt: tornando dal background il primo fotogramma non teletrasporta le macchie.
          val dt = ((now - last) / 1_000_000_000f).coerceAtMost(MaxFrameDt)
          clock.time += dt * currentSpeed
          val goal = if (running) currentTarget().coerceIn(0f, 1f) else 0f
          clock.amplitude = haloApproach(clock.amplitude, goal, dt)
        }
        last = now
      }
      if (!running && clock.amplitude <= AmplitudeFloor) {
        clock.amplitude = 0f
        break
      }
    }
  }
  return clock
}

/**
 * Il campo di luce con il livello del microfono come numero, letto in composizione: va bene per chi
 * lo raccoglie gia' dentro un composable suo, piccolo. Chi ha il flusso a cinquanta hertz passi la
 * lambda (l'altra firma), che legge solo il loop di frame.
 */
@Composable
fun FluidHaloField(
  mood: FluidHaloMood,
  level: Float,
  modifier: Modifier = Modifier,
  spec: FluidHaloFieldSpec = FluidHaloFieldSpec(),
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
  path: ((Size, Density) -> Path)? = null,
  staticWhenReduced: Boolean = true,
) {
  FluidHaloField(mood = mood, level = { level }, modifier = modifier, spec = spec, colours = colours, path = path, staticWhenReduced = staticWhenReduced)
}

/**
 * Il campo di luce, completo: presenza (dissolvenza 400 ms in entrata, 600 in uscita), colori per
 * umore animati, orologio, e un canvas a schermo intero che disegna con [drawFluidHaloField].
 *
 * `Path` e `PathMeasure` si rifanno solo quando cambia la misura; `time` e `amplitude` si leggono
 * dentro il draw. [level] e' una lambda e la legge **solo il loop di frame** dell'orologio: il
 * microfono a cinquanta hertz non ricompone il campo.
 *
 * @param path la forma su cui viaggiano le macchie, se non e' il rettangolo arrotondato del canvas
 *   (la capsula del composer). Si costruisce una volta per misura.
 * @param staticWhenReduced con il moto ridotto: `true` = fermo ma visibile (una sessione, dove
 *   l'alone dice "l'assistente e' qui"), `false` = nascosto (una chat, dove e' solo decorazione).
 */
@Composable
fun FluidHaloField(
  mood: FluidHaloMood,
  level: () -> Float,
  modifier: Modifier = Modifier,
  spec: FluidHaloFieldSpec = FluidHaloFieldSpec(),
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
  path: ((Size, Density) -> Path)? = null,
  staticWhenReduced: Boolean = true,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val shown = mood != FluidHaloMood.HIDDEN && (staticWhenReduced || !reducedMotion)
  val presence = remember { Animatable(0f) }
  LaunchedEffect(shown) {
    presence.animateTo(if (shown) 1f else 0f, if (shown) FluidMotion.fadeIn(PresenceFadeInMs) else FluidMotion.fadeOut(PresenceFadeOutMs))
  }
  // L'umore e il livello si leggono nel loop di frame (rememberFluidHaloClock tiene l'ultima lambda).
  val clock = rememberFluidHaloClock(running = shown, speed = mood.tempo(), target = { mood.amplitudeTarget(level()) })
  val cycle = remember(colours, mood) { colours.cycle(mood) }
  // Sei `State<Color>`: si leggono nel draw, cosi' il cambio d'umore anima i colori senza ricomporre.
  val animated = cycle.mapIndexed { index, colour -> animateColorAsState(colour, FluidMotion.color(), label = "haloColour$index") }
  val frameColours = remember(cycle.size) { MutableList(cycle.size) { Color.Transparent } }
  val geometry = remember(spec.cornerRadius, spec.inset, path) { HaloGeometry() }
  val settled = remember(presence) { derivedStateOf { presence.value <= HaloPresenceFloor } }
  if (!shown && settled.value) return
  Spacer(
    modifier.fillMaxSize().drawBehind {
      geometry.ensure(size, this, spec, path)
      for (i in frameColours.indices) frameColours[i] = animated[i].value
      drawFluidHaloField(geometry.measure, geometry.path, clock.time, clock.amplitude, presence.value, frameColours, spec)
    },
  )
}

/**
 * Il pittore, senza stato: le macchie viaggiano sul perimetro misurato, equispaziate (`i/n` piu' il
 * tempo, piu' un ondeggiare), ognuna del colore `colours[i % colours.size]`, ognuna un gradiente
 * radiale dal colore al trasparente; poi il filo sul bordo.
 *
 * Equispaziate apposta: la prima versione dava a ogni macchia una velocita' sua, e si ammucchiavano
 * in tre e si contavano. L'ondeggiare (piu' o meno 1.2% del perimetro) basta a non farle sembrare
 * una collana.
 *
 * Ritorna subito sotto [HaloPresenceFloor]: a presenza zero non si registra niente, e il primo e
 * l'ultimo fotogramma di una dissolvenza sono la trappola classica del `Plus` (una macchia
 * trasparente costa quanto una piena).
 */
fun DrawScope.drawFluidHaloField(
  measure: PathMeasure,
  path: Path,
  time: Float,
  amplitude: Float,
  presence: Float,
  colours: List<Color>,
  spec: FluidHaloFieldSpec,
) {
  if (presence <= HaloPresenceFloor || colours.isEmpty()) return
  val length = measure.length
  if (length <= 0f) return
  val n = spec.blobCount
  val baseRadius = spec.blobRadius.toPx()
  val alpha = blobAlpha(amplitude, presence, spec.peakAlpha)
  for (i in 0 until n) {
    val center = measure.getPosition(blobFraction(i, n, time) * length)
    val radius = baseRadius * blobRadiusFactor(amplitude, blobBreath(i, n, time))
    val colour = colours[i % colours.size]
    drawCircle(
      brush = Brush.radialGradient(listOf(colour.copy(alpha = alpha * colour.alpha), colour.copy(alpha = 0f)), center = center, radius = radius),
      radius = radius,
      center = center,
      blendMode = spec.blendMode,
    )
  }
  if (spec.edgeStroke > 0.dp && spec.edgeAlpha > 0f) {
    // Il filo sul bordo: la luce che c'e' anche fra una macchia e l'altra.
    val edgeAlpha = spec.edgeAlpha * presence
    drawPath(
      path = path,
      brush = Brush.linearGradient(listOf(colours[0].copy(alpha = edgeAlpha), colours[1 % colours.size].copy(alpha = edgeAlpha))),
      style = Stroke(width = spec.edgeStroke.toPx()),
      blendMode = spec.blendMode,
    )
  }
}

/**
 * L'aurora sullo sfondo di una chat: poche macchie larghe (un terzo, mezza pagina) che vagano su
 * curve di Lissajous con periodi tutti diversi, cosi' non vanno mai in sincrono e non si ripetono
 * mai uguali. I raggi sono frazioni della larghezza; il colore gira piano nel ciclo.
 */
@Immutable
data class FluidAuroraSpec(
  val blobCount: Int = 5,
  /** Il raggio della macchia piu' piccola, per larghezza del canvas. */
  val radiusMin: Float = 0.35f,
  val radiusMax: Float = 0.55f,
  val peakAlphaLight: Float = 0.20f,
  val peakAlphaDark: Float = 0.30f,
  /** Secondi per un'andata e ritorno in orizzontale, uno per macchia (cicla se ce ne sono di piu'). */
  val periodsX: List<Float> = listOf(11f, 13.5f, 9.5f, 16f, 12.5f, 14.5f),
  val periodsY: List<Float> = listOf(14f, 10.5f, 16.5f, 12f, 9f, 15.5f),
  /** Quanti giri del ciclo di colori al secondo: uno ogni quaranta secondi. */
  val driftCyclesPerSec: Float = 1f / 40f,
  /** Quanto respira il raggio (frazione). */
  val breath: Float = 0.08f,
)

/**
 * Disegna l'aurora in `size`. Solo fase di disegno: zero allocazioni oltre i brush. `Plus` se la
 * pagina e' scura, `SrcOver` se e' chiara (vedi [FluidHaloFieldSpec] per il perche'). Ritorna subito
 * sotto un'alpha di 0.002.
 */
fun DrawScope.drawFluidAuroraField(
  time: Float,
  amplitude: Float,
  presence: Float,
  colours: List<Color>,
  spec: FluidAuroraSpec,
  dark: Boolean,
) {
  val peak = (if (dark) spec.peakAlphaDark else spec.peakAlphaLight) * presence * amplitude
  if (peak <= AuroraFloor || colours.isEmpty() || spec.blobCount <= 0) return
  val w = size.width
  val h = size.height
  val n = spec.blobCount
  val blend = if (dark) BlendMode.Plus else BlendMode.SrcOver
  for (i in 0 until n) {
    val periodX = spec.periodsX[i % spec.periodsX.size]
    val periodY = spec.periodsY[i % spec.periodsY.size]
    val cx = w * (0.5f + 0.38f * sin(TwoPi * time / periodX + i * 2.399f))
    val cy = h * (0.5f + 0.42f * cos(TwoPi * time / periodY + i * 1.7f + 0.9f))
    val spread = if (n > 1) i / (n - 1f) else 0f
    val radius = w * (spec.radiusMin + (spec.radiusMax - spec.radiusMin) * spread) * (1f + spec.breath * sin(TwoPi * time / 7f + i))
    val colour = sampleCycle(colours, i / n.toFloat() + time * spec.driftCyclesPerSec)
    val center = Offset(cx, cy)
    drawCircle(
      brush = Brush.radialGradient(
        colorStops = arrayOf(0f to colour.copy(alpha = peak), 0.55f to colour.copy(alpha = 0.45f * peak), 1f to colour.copy(alpha = 0f)),
        center = center,
        radius = radius,
      ),
      radius = radius,
      center = center,
      blendMode = blend,
    )
  }
}

// ---- La matematica pura, testabile sulla JVM ----------------------------------------------------

/** Dove sta la macchia `i` di `n` lungo il perimetro, da 0 a 1: equispaziate, in moto, con un ondeggiare. */
internal fun blobFraction(i: Int, n: Int, time: Float): Float =
  wrap01(i.toFloat() / n + time * HaloBaseSpeed + HaloWobble * sin(time * 0.9f + i * 1.7f))

/** Il respiro della macchia `i`: fra 0.7 e 1, sfasato di macchia in macchia cosi' non respirano in coro. */
internal fun blobBreath(i: Int, n: Int, time: Float): Float = 0.85f + 0.15f * sin(time * 1.1f + i * TwoPi / n)

/** Il raggio rispetto a quello base: fra 0.75 (ampiezza zero) e 1.2 (piena), per il respiro. */
internal fun blobRadiusFactor(amplitude: Float, breath: Float): Float = (0.75f + 0.45f * amplitude) * breath

/** L'alpha di una macchia: da 0.18 a `peakAlpha` con l'ampiezza, per la presenza. */
internal fun blobAlpha(amplitude: Float, presence: Float, peakAlpha: Float): Float = (BlobFloorAlpha + (peakAlpha - BlobFloorAlpha) * amplitude) * presence

/**
 * Un passo dell'ampiezza verso il bersaglio in `dt` secondi: sale con [AttackPerFrame] per
 * fotogramma di riferimento, scende con [ReleasePerFrame]; due mezzi passi fanno un passo intero.
 */
internal fun haloApproach(current: Float, goal: Float, dt: Float): Float {
  val perFrame = if (goal > current) AttackPerFrame else ReleasePerFrame
  val k = 1f - (1f - perFrame).pow(dt * ReferenceHz)
  return current + (goal - current) * k
}

/** Il colore del ciclo a `t` (che gira: 1.2 e' come 0.2), interpolando fra un colore e il successivo. */
internal fun sampleCycle(colours: List<Color>, t: Float): Color {
  val n = colours.size
  if (n == 1) return colours[0]
  val position = wrap01(t) * n
  val index = position.toInt() % n
  return lerp(colours[index], colours[(index + 1) % n], position - position.toInt())
}

/** `t` riportato in [0, 1), anche se negativo. */
internal fun wrap01(t: Float): Float = ((t % 1f) + 1f) % 1f

/** Giri del perimetro per secondo di tempo dell'orologio (al tempo 1, l'ascolto): un giro in circa diciassette secondi. */
internal const val HaloBaseSpeed = 0.06f

/** Quanto ondeggia una macchia intorno al suo posto, in frazione del perimetro. */
internal const val HaloWobble = 0.012f

/** L'alpha di una macchia ad ampiezza zero: l'alone a riposo non sparisce. */
internal const val BlobFloorAlpha = 0.18f

/** L'attacco: la voce sale subito, come su un indicatore di livello. Frazione della distanza per fotogramma a [ReferenceHz]. */
internal const val AttackPerFrame = 0.35f

/** Il rilascio: scende piano, cosi' fra due sillabe l'alone non si spegne. */
internal const val ReleasePerFrame = 0.08f

/** La cadenza a cui [AttackPerFrame] e [ReleasePerFrame] sono definiti; a 120 Hz il passo si dimezza da solo. */
internal const val ReferenceHz = 60f

/** Il dt massimo per fotogramma: oltre, il tempo fa un salto e le macchie si teletrasportano. */
private const val MaxFrameDt = 1f / 15f

/** Sotto questa ampiezza, a `running` falso, il loop si ferma. */
private const val AmplitudeFloor = 0.004f

/** Sotto questa presenza il pittore non disegna: a zero un `Plus` costa e non si vede. */
internal const val HaloPresenceFloor = 0.004f

private const val AuroraFloor = 0.002f

private const val PresenceFadeInMs = 400
private const val PresenceFadeOutMs = 600

private const val TwoPi = (2.0 * PI).toFloat()

/**
 * La forma misurata, rifatta solo quando cambia la misura: `Path` e `PathMeasure` costano, e nel
 * draw a sessanta hertz non si allocano. Vive in un `remember`, non in `drawWithCache`, perche' il
 * canvas ricompone a cinquanta hertz con il microfono e `drawWithCache` butterebbe via la cache a
 * ogni giro.
 */
private class HaloGeometry {
  private var size: Size = Size.Unspecified
  val path: Path = Path()
  val measure: PathMeasure = PathMeasure()

  fun ensure(newSize: Size, density: Density, spec: FluidHaloFieldSpec, factory: ((Size, Density) -> Path)?) {
    if (newSize == size) return
    size = newSize
    path.reset()
    if (factory != null) {
      path.addPath(factory(newSize, density))
    } else {
      with(density) {
        val inset = spec.inset.toPx()
        val corner = spec.cornerRadius.toPx()
        path.addRoundRect(RoundRect(Rect(inset, inset, newSize.width - inset, newSize.height - inset), CornerRadius(corner, corner)))
      }
    }
    measure.setPath(path, forceClosed = true)
  }
}
