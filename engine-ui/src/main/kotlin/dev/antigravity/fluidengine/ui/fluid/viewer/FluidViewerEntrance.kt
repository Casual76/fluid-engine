package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentSmoothstep
import dev.antigravity.fluidengine.ui.page.ZoomFaces

internal class ViewerEntrance {
  var ready by mutableStateOf(false)
  var revealed by mutableStateOf(false)
}

/** Presets that load data claim the handoff. A synchronous app renderer can use its own placement. */
internal class ViewerEntranceClaim(private val entrance: ViewerEntrance) {
  var waitsForPreset by mutableStateOf(false)
  val revealed: Boolean get() = entrance.revealed
  private var hasPlaced by mutableStateOf(false)
  private var rendererPlaced by mutableStateOf(false)
  private val blocked = mutableStateMapOf<Any, Unit>()
  val canReveal: Boolean get() = (hasPlaced || (rendererPlaced && !waitsForPreset)) && blocked.isEmpty()

  fun placed() { hasPlaced = true }
  fun rendererPlaced() { rendererPlaced = true }

  fun loading(token: Any, waiting: Boolean) {
    if (entrance.ready) return
    if (waiting) blocked[token] = Unit else blocked.remove(token)
  }

  fun remove(token: Any) { blocked.remove(token) }
}

internal val LocalViewerEntrance = staticCompositionLocalOf<ViewerEntranceClaim?> { null }

@Composable
internal fun awaitViewerEntrance(): ViewerEntranceClaim? = LocalViewerEntrance.current.also { claim ->
  if (claim != null) SideEffect { claim.waitsForPreset = true }
}

/** An asynchronous surface keeps the thumbnail until it has real ink or a terminal result. */
@Composable
internal fun viewerEntranceLoading(waiting: () -> Boolean) {
  val claim = LocalViewerEntrance.current ?: return
  val token = remember(claim) { Any() }
  DisposableEffect(claim, token) { onDispose { claim.remove(token) } }
  val blocked = waiting()
  SideEffect { claim.loading(token, blocked) }
}

/** F6C.10 v2, 22 November. R7 changes faces through blur. The handoff starts only after the preset is placed.
 * The 150 ms budget matches the measured F5B.3 exchange. Phase thresholds are visual estimates. */
internal object ViewerEntranceMotion {
  const val RevealMillis = 150

  fun faces(reveal: Float, arrival: Float): ZoomFaces {
    val phase = minOf(reveal, glassContentSmoothstep(.08f, .90f, arrival))
    val blur = GlassContentDefaults.PeakBlur
    return ZoomFaces(
      sourceAlpha = 1f - glassContentSmoothstep(.42f, .74f, phase),
      pageAlpha = glassContentSmoothstep(.30f, .42f, phase),
      sourceBlur = blur * glassContentSmoothstep(.12f, .38f, phase),
      pageBlur = blur * (1f - glassContentSmoothstep(.74f, 1f, phase)),
      riseDp = 0f,
    )
  }
}
