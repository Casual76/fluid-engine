package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** Le misure e le parole di [FluidStepProgress]. */
object FluidStepProgressDefaults {
  /** Oltre questi passi la versione a segmenti diventerebbe una grana: si ripiega sulla barra continua. */
  const val MaxSegments: Int = 12

  /** L'altezza dei segmenti: la barra continua dell'engine ne ha 4. */
  val SegmentHeight: Dp = 4.dp

  /** Lo spazio fra i segmenti. */
  val SegmentGap: Dp = 4.dp

  /** «Passo 2 di 5». Un'app in un'altra lingua passa la sua frase come `label`. */
  fun label(step: Int, steps: Int): String = "Passo $step di $steps"
}

/**
 * Quanta strada e' fatta al passo [step] (da 1, anche frazionario mentre anima) di [steps]: `step / steps`, fra 0 e 1.
 *
 * Il passo 2 di 5 e' il 40%, non il 25%: «passo 2» dice che ci si e' arrivati, e la barra deve
 * confermarlo — a passo 1 di 5 vuota sembrerebbe che non si sia ancora partiti, e all'ultimo passo non
 * e' mai piena. E' la regola che l'onboarding di MyCloud aveva scritto a mano.
 */
internal fun fluidStepFraction(step: Float, steps: Int): Float =
  if (steps <= 0) 0f else (step / steps).coerceIn(0f, 1f)

/**
 * Quanto e' pieno il segmento [index] (da 0) quando la barra e' arrivata a [position] passi (anche
 * frazionario, mentre anima): 0 prima, 1 dopo, lineare mentre ci si passa sopra.
 */
internal fun fluidStepSegmentFill(index: Int, position: Float): Float = (position - index).coerceIn(0f, 1f)

/** Si disegnano segmenti solo se richiesti **e** se ce ne stanno: fra 2 e [FluidStepProgressDefaults.MaxSegments]. */
internal fun fluidStepSegmented(requested: Boolean, steps: Int): Boolean =
  requested && steps in 2..FluidStepProgressDefaults.MaxSegments

/**
 * L'intestazione di una procedura a passi: «Passo 2 di 5» e una barra che dice a che punto si e',
 * senza rubare la scena al titolo.
 *
 * Tre app se l'erano scritta a mano — l'onboarding di MyCloud (testo e barra), il convertitore (una
 * fila di chip con il passo in corso che respira) — e qui sono due forme dello stesso pezzo: la barra
 * continua di serie, a segmenti con [segmented].
 *
 * **La barra si muove con la molla della casa** ([FluidMotion.smooth]) e non salta di passo in passo;
 * nella forma a segmenti il riempimento corre da un segmento al successivo con lo stesso numero. Con le
 * animazioni ridotte arriva senza passaggi.
 *
 * **A segmenti, non oltre dodici.** Con piu' passi ([FluidStepProgressDefaults.MaxSegments]) i
 * segmenti sarebbero una grana, e la barra continua ripiega da sola.
 *
 * Per TalkBack e' un blocco solo, letto come [label], con il passo e il numero dei passi come intervallo
 * di avanzamento: chi non vede sa a che punto e', non solo che c'e' una barra.
 *
 * @param step il passo corrente, da 1; fuori da 0..[steps] si ferma ai limiti.
 * @param label la frase a sinistra; di serie «Passo 2 di 5» ([FluidStepProgressDefaults.label]).
 * @param detail a destra, piu' piano: il nome del passo, o una percentuale. Null: niente.
 * @param segmented un segmento per passo invece della barra continua.
 * @param pulseCurrent il passo in corso respira, come il pallino dal vivo ([FluidLiveDot], lo stesso
 *   respiro): per un'operazione che sta girando e che altrimenti sembra ferma, non per un modulo da
 *   compilare. Solo nella forma a segmenti, e fermo con le animazioni ridotte.
 * @param color il riempimento; di serie l'accento. La barra continua lo porta da sola a un contrasto
 *   di 3:1 sulla pista ([FluidProgressBar]).
 */
@Composable
fun FluidStepProgress(
  step: Int,
  steps: Int,
  modifier: Modifier = Modifier,
  label: String = FluidStepProgressDefaults.label(step, steps),
  detail: String? = null,
  segmented: Boolean = false,
  pulseCurrent: Boolean = false,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val position = animateFloatAsState(
    targetValue = step.coerceIn(0, steps.coerceAtLeast(0)).toFloat(),
    animationSpec = if (reducedMotion) snap() else FluidMotion.smooth(),
    label = "fluid step progress",
  )
  val asSegments = fluidStepSegmented(segmented, steps)

  Column(
    modifier = modifier
      .fillMaxWidth()
      // Un blocco solo per chi non vede: la frase e dove si e' arrivati, non tre nodi sparsi.
      .semantics(mergeDescendants = true) {
        contentDescription = label
        val total = steps.coerceAtLeast(1)
        progressBarRangeInfo = ProgressBarRangeInfo(
          current = step.coerceIn(1, total).toFloat(),
          range = 1f..total.toFloat(),
          steps = (total - 2).coerceAtLeast(0),
        )
      },
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = label,
        // Le cifre tabulari: «Passo 9 di 14» e «Passo 10 di 14» non devono spostare quello che hanno a destra.
        style = MaterialTheme.typography.labelLarge.copy(
          fontWeight = FontWeight.SemiBold,
          fontFeatureSettings = "tnum",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = detail == null),
      )
      if (detail != null) {
        Text(
          text = detail,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          // A destra, contro il bordo della barra: e' il nome del passo, non la continuazione della frase.
          textAlign = TextAlign.End,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = true),
        )
      }
    }
    if (asSegments) {
      StepSegments(
        steps = steps,
        step = step,
        position = { position.value },
        color = color,
        pulseCurrent = pulseCurrent,
      )
    } else {
      FluidProgressBar(
        progress = { fluidStepFraction(position.value, steps) },
        color = color,
      )
    }
  }
}

/**
 * La barra a segmenti: uno per passo, pista e riempimento in un disegno solo.
 *
 * Il riempimento di ogni segmento viene dalla posizione animata ([fluidStepSegmentFill]), quindi
 * mentre la molla corre il colore passa da un segmento al successivo e non accende il nuovo di colpo.
 * Il passo in corso, se [pulseCurrent], respira fra il 55% e il pieno — il bagliore che il convertitore
 * dava al suo chip — con il respiro condiviso ([rememberFluidPulse]).
 */
@Composable
private fun StepSegments(
  steps: Int,
  step: Int,
  position: () -> Float,
  color: Color,
  pulseCurrent: Boolean,
) {
  val track = MaterialTheme.colorScheme.onSurface.copy(alpha = StepTrackAlpha)
  val pulse = rememberFluidPulse(active = pulseCurrent)
  val current = step.coerceIn(1, steps) - 1
  Canvas(modifier = Modifier.fillMaxWidth().height(FluidStepProgressDefaults.SegmentHeight)) {
    val gap = FluidStepProgressDefaults.SegmentGap.toPx()
    val segmentWidth = (size.width - gap * (steps - 1)) / steps
    val radius = CornerRadius(size.height / 2f, size.height / 2f)
    val here = position()
    for (index in 0 until steps) {
      val x = index * (segmentWidth + gap)
      drawRoundRect(color = track, topLeft = Offset(x, 0f), size = Size(segmentWidth, size.height), cornerRadius = radius)
      val fill = fluidStepSegmentFill(index, here)
      if (fill > 0f) {
        val breathe = if (index == current && pulse != null) lerp(StepPulseLowAlpha, 1f, pulse.value) else 1f
        drawRoundRect(
          color = color,
          topLeft = Offset(x, 0f),
          // Il riempimento cresce da sinistra dentro il segmento, non si accende tutto insieme.
          size = Size(segmentWidth * fill, size.height),
          cornerRadius = radius,
          alpha = breathe,
        )
      }
    }
  }
}

/** La pista dei segmenti: il testo a bassa opacita', come quella della barra continua. */
private const val StepTrackAlpha = 0.12f

// TODO token FluidMotion: PulseLowAlpha per un riempimento (non un pallino): il punto basso del respiro
// di un passo in corso. Piu' alto di FluidPulseLowAlpha (0,35) perche' e' una barra e non un punto: a
// 0,35 il passo in corso sembrerebbe quello ancora da fare.
private const val StepPulseLowAlpha = 0.55f
