package dev.antigravity.fluidengine.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Dove finisce il callout e da che parte guarda la sua punta. Coordinate della radice, in pixel. */
data class FluidCoachMarkPlacement(
  val x: Float,
  val y: Float,
  val below: Boolean,
  /** La punta, in coordinate del callout: sta sotto il centro dell'elemento, quando ci arriva. */
  val pointerX: Float,
)

/**
 * Dove mettere un callout rispetto al suo elemento. Puro: la geometria di un pop-up e' la cosa
 * piu' facile da sbagliare in fondo a uno schermo, ed e' anche la piu' facile da provare.
 *
 * Sotto l'elemento se c'e' posto, sopra altrimenti, e nel lato piu' capiente quando non ci sta da
 * nessuna delle due parti. Orizzontalmente e' centrato sull'elemento e rientrato nei margini; la
 * punta insegue il centro fin dove puo' senza uscire dagli angoli arrotondati.
 */
object FluidCoachMarkPlacer {

  fun place(
    anchor: Rect,
    calloutWidth: Float,
    calloutHeight: Float,
    screen: Size,
    safeTop: Float = 0f,
    safeBottom: Float = 0f,
    margin: Float = 0f,
    gap: Float = 0f,
    pointerInset: Float = 0f,
  ): FluidCoachMarkPlacement {
    val topLimit = safeTop + margin
    val bottomLimit = screen.height - safeBottom - margin
    val belowY = anchor.bottom + gap
    val aboveY = anchor.top - gap - calloutHeight
    val below = when {
      belowY + calloutHeight <= bottomLimit -> true
      aboveY >= topLimit -> false
      // Non ci sta da nessuna parte: vince il lato con piu' spazio, e il callout si rientra.
      else -> (bottomLimit - anchor.bottom) >= (anchor.top - topLimit)
    }
    val rawY = if (below) belowY else aboveY
    val y = rawY.coerceIn(topLimit, (bottomLimit - calloutHeight).coerceAtLeast(topLimit))

    val maxX = (screen.width - margin - calloutWidth).coerceAtLeast(margin)
    val x = (anchor.center.x - calloutWidth / 2f).coerceIn(margin, maxX)
    val pointerX = (anchor.center.x - x).coerceIn(
      pointerInset,
      (calloutWidth - pointerInset).coerceAtLeast(pointerInset),
    )
    return FluidCoachMarkPlacement(x = x, y = y, below = below, pointerX = pointerX)
  }
}

/**
 * L'elemento a cui un suggerimento si aggancia: misura i suoi limiti per il callout e, mentre e'
 * lui quello spiegato, si porta un alone. Senza, "questo" in una frase non indica niente.
 */
@Composable
fun Modifier.fluidTutorialAnchor(id: String): Modifier {
  val state = LocalFluidTutorialHostState.current ?: return this
  val highlighted = state.presenting?.anchorId == id
  val ring = MaterialTheme.colorScheme.primary
  return this
    .onGloballyPositioned { state.anchorBounds(id, it.boundsInRoot()) }
    .drawWithContent {
      drawContent()
      if (!highlighted) return@drawWithContent
      val inset = 6.dp.toPx()
      drawRoundRect(
        color = ring.copy(alpha = 0.55f),
        topLeft = Offset(-inset, -inset),
        size = Size(size.width + inset * 2, size.height + inset * 2),
        cornerRadius = CornerRadius(FluidRadius.Group.toPx()),
        style = Stroke(width = 2.dp.toPx()),
      )
    }
}

/**
 * Il padrone di casa dei suggerimenti: sta alla radice dell'app, sopra tutto il resto, e mostra
 * un callout per volta quando la politica dice che e' il momento.
 *
 * Non e' un modale. Nessuno scrim, la pagina sotto resta leggibile, e **un tocco fuori chiude** il
 * callout senza consumare il tocco: arriva lo stesso a quello che c'era sotto, perche' un
 * suggerimento che si mette fra il dito e l'app e' peggio del non averlo dato. Lo stesso tocco
 * conta anche come interazione, che e' quello che sblocca il suggerimento successivo.
 */
@Composable
fun FluidTutorialHost(
  state: FluidTutorialHostState,
  labels: FluidTutorialLabels,
  backdrop: GlassBackdropState? = null,
  modalPresenting: () -> Boolean = { false },
) {
  LaunchedEffect(state) {
    while (isActive) {
      state.evaluate(modalPresenting())
      val idle = state.presenting == null && state.pendingCount == 0
      delay(if (idle) FluidTutorialPolicy.IDLE_TICK_MILLIS else FluidTutorialPolicy.TICK_MILLIS)
    }
  }

  val tutorial = state.presenting
  val anchor = tutorial?.let { state.anchors[it.anchorId] }
  // L'ultimo mostrato resta finche' l'uscita non e' finita: senza, il callout sparirebbe di colpo
  // lasciando in scena solo la sua ombra che si spegne.
  var shown by remember { mutableStateOf<FluidTutorial?>(null) }
  var shownAnchor by remember { mutableStateOf<Rect?>(null) }
  LaunchedEffect(tutorial, anchor) {
    if (tutorial != null) shown = tutorial
    if (anchor != null) shownAnchor = anchor
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val screen = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
    val margin = with(density) { 16.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val pointerInset = with(density) { 26.dp.toPx() }

    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val placement = shownAnchor?.takeIf { calloutSize.width > 0 }?.let { bounds ->
      FluidCoachMarkPlacer.place(
        anchor = bounds,
        calloutWidth = calloutSize.width.toFloat(),
        calloutHeight = calloutSize.height.toFloat(),
        screen = screen,
        margin = margin,
        gap = gap,
        pointerInset = pointerInset,
      )
    }

    Box(
      Modifier
        .fillMaxSize()
        .pointerInput(tutorial?.id, placement) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val down = event.changes.firstOrNull { it.pressed && it.previousPressed.not() } ?: continue
              state.interacted()
              if (state.presenting == null) continue
              val bounds = placement?.let {
                Rect(it.x, it.y, it.x + calloutSize.width, it.y + calloutSize.height)
              }
              if (bounds == null || !bounds.contains(down.position)) state.dismiss()
            }
          }
        },
    )

    AnimatedVisibility(
      visible = tutorial != null && anchor != null,
      enter = fadeIn(FluidMotion.fadeIn(160)) + scaleIn(initialScale = 0.94f),
      exit = fadeOut(FluidMotion.fadeOut(120)) + scaleOut(targetScale = 0.96f),
    ) {
      val current = shown ?: return@AnimatedVisibility
      FluidCoachMark(
        tutorial = current,
        labels = labels,
        placement = placement,
        backdrop = backdrop,
        onMeasured = { calloutSize = it },
        onDismiss = { state.dismiss() },
        onOptOut = { state.dismiss(optOut = true) },
      )
    }
  }
}

/**
 * Il callout: vetro, un titolo di due parole, una frase, il gesto disegnato quando serve, e i due
 * modi di chiuderlo — "Ok", e il link che li spegne tutti.
 *
 * Con [placement] nullo il callout e' gia' composto ma invisibile: la sua posizione dipende dalla
 * sua misura, e la misura si sa solo dopo averlo disegnato una volta.
 */
@Composable
fun FluidCoachMark(
  tutorial: FluidTutorial,
  labels: FluidTutorialLabels,
  placement: FluidCoachMarkPlacement?,
  backdrop: GlassBackdropState?,
  onMeasured: (IntSize) -> Unit,
  onDismiss: () -> Unit,
  onOptOut: () -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val scheme = MaterialTheme.colorScheme
  val surface = if (backdrop != null) {
    Modifier.glassSurface(
      state = backdrop,
      tint = GlassDefaults.modalTint(),
      shape = shape,
      role = GlassRole.Modal,
    )
  } else {
    Modifier.background(scheme.surfaceContainerHigh, shape)
  }
  val pointerFill = scheme.surfaceContainerHigh.copy(alpha = 0.94f)

  Column(
    modifier = Modifier
      .offset { IntOffset(placement?.x?.toInt() ?: 0, placement?.y?.toInt() ?: 0) }
      .alpha(if (placement == null) 0f else 1f)
      .widthIn(max = 320.dp)
      .onSizeChanged(onMeasured)
      .then(surface)
      .drawWithContent {
        drawContent()
        val place = placement ?: return@drawWithContent
        // La punta verso l'elemento: e' quello che rende "questo" un pronome con un referente.
        val half = 7.dp.toPx()
        val depth = 7.dp.toPx()
        val path = Path().apply {
          if (place.below) {
            moveTo(place.pointerX - half, 0f)
            lineTo(place.pointerX, -depth)
            lineTo(place.pointerX + half, 0f)
          } else {
            moveTo(place.pointerX - half, size.height)
            lineTo(place.pointerX, size.height + depth)
            lineTo(place.pointerX + half, size.height)
          }
          close()
        }
        drawPath(path, color = pointerFill)
      }
      .padding(16.dp),
  ) {
    Text(
      text = tutorial.title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = scheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = tutorial.text,
      style = MaterialTheme.typography.bodyMedium,
      color = scheme.onSurfaceVariant,
    )
    tutorial.hint?.let { hint ->
      Spacer(Modifier.height(10.dp))
      FluidGestureHintAnimation(
        hint = hint,
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp),
      )
    }
    Spacer(Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = labels.optOut,
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier
          .fluidPressable(onClick = onOptOut, role = Role.Button)
          .padding(vertical = 6.dp, horizontal = 2.dp),
      )
      Spacer(Modifier.width(12.dp))
      Text(
        text = labels.dismiss,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onPrimaryContainer,
        modifier = Modifier
          .background(scheme.primaryContainer, FluidCapsuleShape)
          .fluidPressable(onClick = onDismiss, role = Role.Button, haptic = FluidHapticEvent.Close)
          .padding(horizontal = 18.dp, vertical = 8.dp),
      )
    }
  }
}
