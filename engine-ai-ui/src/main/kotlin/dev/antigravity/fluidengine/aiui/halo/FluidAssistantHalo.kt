package dev.antigravity.fluidengine.aiui.halo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlin.math.PI
import kotlin.math.sin

/**
 * L'aureola in cima allo schermo: quattro macchie di colore che scorrono lungo il bordo alto e
 * sfumano verso il basso. In ascolto l'ampiezza segue il livello del microfono; al lavoro pulsa
 * lenta; mentre scrive scorre come un'onda. Con le animazioni ridotte resta ferma e sfuma
 * soltanto. Solo gradienti: niente shader, cosi' gira uguale sui device deboli.
 *
 * E' l'aureola di ClasseViva e di FluidWeather (due fork dello stesso file: la seconda aveva i
 * colori scritti nel codice, la prima li prendeva dal tema). **Union**: i colori sono
 * [FluidHaloColours], di serie quelli del tema; un'app con una palette sua li passa. Per una luce
 * che corre lungo tutto il perimetro dello schermo c'e' [FluidHaloField].
 *
 * @param level il livello del microfono, da 0 a 1; conta solo in ascolto.
 * @param height quanto scende l'aureola dal bordo alto.
 */
@Composable
fun FluidAssistantHalo(
  mood: FluidHaloMood,
  level: Float,
  modifier: Modifier = Modifier,
  height: Dp = 132.dp,
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val visible = mood != FluidHaloMood.HIDDEN
  val presence by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = spring(dampingRatio = FluidMotion.DampingStandard, stiffness = FluidMotion.ResponseSmooth),
    label = "haloPresence",
  )
  // Attacco veloce e rilascio lento, come un indicatore di livello vero: con una molla sola la
  // voce arrivava smussata e l'aureola sembrava indifferente a quello che si stava dicendo.
  val target = if (mood == FluidHaloMood.LISTENING) 0.30f + level * 0.70f else 0.55f
  val amplitude = remember { Animatable(0.55f) }
  LaunchedEffect(target, mood, reducedMotion) {
    if (reducedMotion) {
      amplitude.snapTo(target)
      return@LaunchedEffect
    }
    val rising = target > amplitude.value
    amplitude.animateTo(
      targetValue = target,
      animationSpec = spring(
        dampingRatio = FluidMotion.DampingStandard,
        stiffness = if (rising) TopHaloAttackStiffness else TopHaloReleaseStiffness,
      ),
    )
  }
  val amplitudeValue = amplitude.value
  var time by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(visible, reducedMotion, mood) {
    if (!visible || reducedMotion) return@LaunchedEffect
    val speed = when (mood) {
      FluidHaloMood.WRITING -> 1.6f
      FluidHaloMood.LISTENING -> 1.1f
      FluidHaloMood.WORKING -> 0.6f
      else -> 0.3f
    }
    var last = 0L
    while (true) {
      withFrameNanos { now ->
        if (last != 0L) time += (now - last) / 1_000_000_000f * speed
        last = now
      }
    }
  }
  if (presence <= 0.001f) return
  // I colori dei tre caratteri (ascolto, lavoro, errore) si sciolgono l'uno nell'altro: prima
  // cambiavano di scatto, ed e' quello che faceva sembrare bruschi i passaggi di stato.
  val targetColours = remember(colours, mood) { topHaloColours(colours, mood) }
  val animated = targetColours.mapIndexed { index, colour ->
    animateColorAsState(targetValue = colour, animationSpec = FluidMotion.smooth(), label = "haloColour$index").value
  }
  Canvas(modifier.fillMaxWidth().height(height)) {
    drawTopHalo(time, amplitudeValue, presence, animated)
  }
}

/** I quattro colori delle macchie per un umore: l'errore e' l'unico che prende il rosso del tema. */
internal fun topHaloColours(colours: FluidHaloColours, mood: FluidHaloMood): List<Color> = when (mood) {
  FluidHaloMood.ERROR -> listOf(
    colours.error,
    colours.error.copy(alpha = 0.7f),
    colours.error.copy(alpha = 0.9f),
    colours.error.copy(alpha = 0.55f),
  )
  FluidHaloMood.DONE, FluidHaloMood.IDLE -> listOf(
    colours.accent,
    colours.secondary.copy(alpha = 0.9f),
    colours.accent.copy(alpha = 0.9f),
    colours.tertiary.copy(alpha = 0.8f),
  )
  else -> listOf(colours.accent, colours.secondary, colours.tertiary, colours.accent.copy(alpha = 0.85f))
}

private fun DrawScope.drawTopHalo(time: Float, amplitude: Float, presence: Float, colours: List<Color>) {
  val width = size.width
  val height = size.height
  val blobs = colours.size
  for (i in 0 until blobs) {
    val phase = time * (0.8f + i * 0.17f) + i * (PI.toFloat() * 2f / blobs)
    val x = width * (0.5f + 0.42f * sin(phase))
    val y = -height * 0.35f + height * 0.18f * sin(phase * 0.7f + i)
    val radius = width * (0.28f + 0.14f * amplitude * (0.6f + 0.4f * sin(phase * 1.3f)))
    val alpha = (0.55f + 0.35f * amplitude) * presence
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(colours[i].copy(alpha = alpha * colours[i].alpha), colours[i].copy(alpha = 0f)),
        center = Offset(x, y),
        radius = radius,
      ),
      radius = radius,
      center = Offset(x, y),
    )
  }
}

/** L'attacco: la voce sale subito, come su un indicatore di livello. */
private const val TopHaloAttackStiffness = 1_400f

/** Il rilascio: scende piano, cosi' fra due sillabe l'aureola non si spegne. */
private const val TopHaloReleaseStiffness = 180f
