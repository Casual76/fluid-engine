package dev.antigravity.fluidengine.ui.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlin.math.abs

/** Quanto dura un giro del gesto disegnato: abbastanza per leggerlo, poco per non ipnotizzare. */
private const val LoopMillis = 1600

/** Il fotogramma fermo con le animazioni ridotte: quello in cui il gesto e' piu' riconoscibile. */
private const val StillFrame = 0.45f

/**
 * Il gesto disegnato dentro un callout: un dito che tiene premuto, che scorre, che trascina. Una
 * frase puo' dire "tieni premuto e trascina", ma nessuna frase lo fa vedere in mezzo secondo.
 *
 * Con le animazioni ridotte resta un fermo immagine: il fotogramma piu' leggibile del giro.
 */
@Composable
fun FluidGestureHintAnimation(
  hint: FluidGestureHint,
  modifier: Modifier = Modifier,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val phase = if (reducedMotion) {
    StillFrame
  } else {
    val transition = rememberInfiniteTransition(label = "gestureHint")
    val animated by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(LoopMillis, easing = LinearEasing), RepeatMode.Restart),
      label = "gestureHintPhase",
    )
    animated
  }
  val trace = MaterialTheme.colorScheme.primary
  val ghost = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)

  Canvas(modifier) {
    when (hint) {
      FluidGestureHint.Tap -> drawTap(phase, trace)
      FluidGestureHint.LongPress -> drawLongPress(phase, trace, ghost)
      FluidGestureHint.SwipeHorizontal -> drawSwipe(phase, trace, ghost)
      FluidGestureHint.DragReorder -> drawReorder(phase, trace, ghost)
      FluidGestureHint.Scrub -> drawScrub(phase, trace, ghost)
      FluidGestureHint.LongPressAndTap -> drawLongPressAndTap(phase, trace, ghost)
    }
  }
}

private fun DrawScope.fingerRadius(): Float = 9.dp.toPx()

private fun DrawScope.drawFinger(center: Offset, color: Color, scale: Float = 1f) {
  drawCircle(color = color.copy(alpha = 0.22f), radius = fingerRadius() * 1.9f * scale, center = center)
  drawCircle(color = color, radius = fingerRadius() * scale, center = center)
}

/** Il tocco: il dito appare e un'onda si allarga e si spegne. */
private fun DrawScope.drawTap(phase: Float, trace: Color) {
  val center = Offset(size.width / 2f, size.height / 2f)
  drawFinger(center, trace)
  val wave = (phase % 0.5f) / 0.5f
  drawCircle(
    color = trace.copy(alpha = (1f - wave) * 0.5f),
    radius = fingerRadius() * (1f + wave * 2.2f),
    center = center,
    style = Stroke(width = 2.dp.toPx()),
  )
}

/** La pressione lunga: un cerchio che si riempie, e alla fine il dito che "aggancia". */
private fun DrawScope.drawLongPress(phase: Float, trace: Color, ghost: Color) {
  val center = Offset(size.width / 2f, size.height / 2f)
  val fill = (phase / 0.8f).coerceAtMost(1f)
  val radius = fingerRadius() * 2.1f
  drawCircle(color = ghost, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
  drawArc(
    color = trace,
    startAngle = -90f,
    sweepAngle = 360f * fill,
    useCenter = false,
    topLeft = Offset(center.x - radius, center.y - radius),
    size = Size(radius * 2, radius * 2),
    style = Stroke(width = 2.dp.toPx()),
  )
  drawFinger(center, trace, scale = if (fill >= 1f) 1.15f else 1f)
}

/** Lo scorrimento laterale: una capsula, e il dito che la porta da una parte e torna. */
private fun DrawScope.drawSwipe(phase: Float, trace: Color, ghost: Color) {
  val midY = size.height / 2f
  val pillWidth = size.width * 0.52f
  val pillHeight = 20.dp.toPx()
  drawRoundRect(
    color = ghost,
    topLeft = Offset((size.width - pillWidth) / 2f, midY - pillHeight / 2f),
    size = Size(pillWidth, pillHeight),
    cornerRadius = CornerRadius(pillHeight / 2f),
    style = Stroke(width = 2.dp.toPx()),
  )
  // Avanti e indietro con una sosta ai due estremi: il ritorno racconta che si puo' fare in due sensi.
  val travel = pillWidth * 0.36f
  val swing = 1f - abs(1f - (phase * 2f))
  val x = size.width / 2f - travel / 2f + travel * swing
  drawLine(
    color = trace.copy(alpha = 0.35f),
    start = Offset(size.width / 2f - travel / 2f, midY),
    end = Offset(x, midY),
    strokeWidth = 3.dp.toPx(),
  )
  drawFinger(Offset(x, midY), trace)
}

/** Il riordino: quattro tessere, il dito che ne prende una e la porta in un'altra casella. */
private fun DrawScope.drawReorder(phase: Float, trace: Color, ghost: Color) {
  val cell = Size(size.width * 0.2f, size.height * 0.36f)
  val gap = 6.dp.toPx()
  val originX = size.width / 2f - cell.width - gap / 2f
  val originY = size.height / 2f - cell.height - gap / 2f
  val positions = List(4) { index ->
    Offset(
      originX + (index % 2) * (cell.width + gap),
      originY + (index / 2) * (cell.height + gap),
    )
  }
  positions.forEach { corner ->
    drawRoundRect(
      color = ghost,
      topLeft = corner,
      size = cell,
      cornerRadius = CornerRadius(6.dp.toPx()),
      style = Stroke(width = 2.dp.toPx()),
    )
  }
  // Presa (primo quarto), viaggio, e rilascio nella casella opposta.
  val hold = (phase / 0.25f).coerceAtMost(1f)
  val travel = ((phase - 0.25f) / 0.5f).coerceIn(0f, 1f)
  val from = positions[0] + Offset(cell.width / 2f, cell.height / 2f)
  val to = positions[3] + Offset(cell.width / 2f, cell.height / 2f)
  val point = Offset(from.x + (to.x - from.x) * travel, from.y + (to.y - from.y) * travel)
  if (travel > 0f) {
    drawRoundRect(
      color = trace.copy(alpha = 0.28f),
      topLeft = point - Offset(cell.width / 2f, cell.height / 2f),
      size = cell,
      cornerRadius = CornerRadius(6.dp.toPx()),
    )
  }
  drawFinger(point, trace, scale = 0.85f + hold * 0.2f)
}

/** Lo scorrimento su una barra: tacche, e il dito che le percorre avanti e indietro. */
private fun DrawScope.drawScrub(phase: Float, trace: Color, ghost: Color) {
  val midY = size.height / 2f
  val left = size.width * 0.18f
  val right = size.width * 0.82f
  drawLine(ghost, Offset(left, midY), Offset(right, midY), strokeWidth = 3.dp.toPx())
  repeat(5) { index ->
    val x = left + (right - left) * index / 4f
    drawLine(ghost, Offset(x, midY - 5.dp.toPx()), Offset(x, midY + 5.dp.toPx()), strokeWidth = 2.dp.toPx())
  }
  val swing = 1f - abs(1f - (phase * 2f))
  drawFinger(Offset(left + (right - left) * swing, midY), trace, scale = 0.9f)
}

/** Tocco e pressione lunga nello stesso disegno: due gesti sullo stesso tasto, in fila. */
private fun DrawScope.drawLongPressAndTap(phase: Float, trace: Color, ghost: Color) {
  if (phase < 0.45f) {
    drawTap(phase / 0.45f * 0.5f, trace)
  } else {
    drawLongPress((phase - 0.45f) / 0.55f * 0.8f, trace, ghost)
  }
}
