package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Chiede una conferma sopra l'elemento da cui parte. Con una [source] disponibile la pagina
 * resta attiva e si annulla toccando fuori. Al centro, la conferma interrompe il compito e offre
 * il tasto Annulla. Ogni scelta abilitata chiude la conferma e poi esegue l'azione.
 */
@Composable
fun FluidConfirmationDialog(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  source: FluidTransitionSource? = null,
  title: String? = null,
  actions: List<FluidAlertAction>,
  cancelLabel: String = "Annulla",
) {
  val cancel = FluidAlertAction(cancelLabel, onDismissRequest)
  val spec = FluidAlertSpec(
    title.orEmpty(), null, actions.sortedBy { it.emphasis != FluidAlertAction.Emphasis.Destructive }.map { action ->
      action.copy(onClick = {
        onDismissRequest()
        action.onClick()
      })
    },
    null, Modifier, confirmationCancel = cancel,
  )
  val host = LocalFluidGlassModalHostState.current
  if (host != null) {
    FluidAlertInHost(host, onDismissRequest, spec, source, visible = visible)
  } else if (visible) {
    FluidAlertDialog(onDismissRequest, spec.title, spec.modifier, null, spec.actions + cancel, null)
  }
}

/** La conferma si apre su un [item] presente e conserva il dato durante il viaggio di ritorno. */
@Composable
fun <T : Any> FluidConfirmationDialog(
  item: T?,
  onDismissRequest: () -> Unit,
  source: FluidTransitionSource? = null,
  title: ((T) -> String?)? = null,
  cancelLabel: String = "Annulla",
  actions: (T) -> List<FluidAlertAction>,
) {
  var lastItem by remember { mutableStateOf(item) }
  if (item != null) lastItem = item
  val shown = lastItem ?: return
  FluidConfirmationDialog(item != null, onDismissRequest, source, title?.invoke(shown), actions(shown), cancelLabel)
}

internal fun fluidConfirmationFrame(anchor: Rect?, size: Size, viewport: Size, margin: Float, gap: Float): Rect {
  val x = if (anchor == null) (viewport.width - size.width) / 2f
    else (anchor.center.x - size.width / 2f).coerceIn(margin, (viewport.width - size.width - margin).coerceAtLeast(margin))
  val y = if (anchor == null) (viewport.height - size.height) / 2f
    else (anchor.top - gap - size.height).coerceIn(margin, (viewport.height - size.height - margin).coerceAtLeast(margin))
  return Rect(x, y, x + size.width, y + size.height)
}
