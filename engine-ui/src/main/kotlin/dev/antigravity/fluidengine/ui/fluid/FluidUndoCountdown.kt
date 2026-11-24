package dev.antigravity.fluidengine.ui.fluid

import android.os.SystemClock
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlinx.coroutines.delay

/**
 * Una finestra di annullamento dell'app. Condividere lo stato fra più posti rende atomico il consumo
 * sul thread UI. La fine del contatore cambia la faccia del controllo e non esegue operazioni dell'app.
 */
@Stable
class FluidUndoCountdownState internal constructor(
  val timing: FluidNotificationCountdown,
  private val clock: () -> Long,
  finished: Boolean = false,
) {
  constructor(timing: FluidNotificationCountdown) : this(timing, SystemClock::elapsedRealtime)

  internal var finished by mutableStateOf(finished)
    private set

  /** Si legge e si modifica sul thread UI, come l'azione del tasto. */
  val isActive: Boolean get() = !finished && remainingMillis() > 0L

  /** Accetta un solo tocco entro la finestra, anche se il disegno è rimasto un fotogramma indietro. */
  fun undo(onUndo: () -> Unit): Boolean {
    if (!isActive) return false
    finished = true
    onUndo()
    return true
  }

  internal fun remainingMillis(): Long =
    timing.durationMillis - (clock() - timing.startedAtElapsedRealtimeMillis).coerceIn(0L, timing.durationMillis)

  internal fun fill(): Float = 1f - remainingMillis().toFloat() / timing.durationMillis

  internal fun spokenSeconds(): Long {
    val left = remainingMillis()
    return (left / MillisPerSecond) + if (left % MillisPerSecond == 0L) 0L else 1L
  }

  internal suspend fun followDeadline() {
    while (!finished) {
      val remaining = remainingMillis()
      if (remaining == 0L) {
        finished = true
        return
      }
      delay(remaining)
    }
  }

  internal companion object {
    /** Salva il consumo e usa l'istante dell'app anche dopo la ricreazione. */
    fun saver(timing: FluidNotificationCountdown, clock: () -> Long = SystemClock::elapsedRealtime) =
      Saver<FluidUndoCountdownState, Boolean>(
        save = { it.finished },
        restore = { FluidUndoCountdownState(timing, clock, it) },
      )
  }
}

/** Ricorda il consumo. L'app conserva [timing], sul clock monotono, insieme all'operazione da annullare. */
@Composable
fun rememberFluidUndoCountdownState(timing: FluidNotificationCountdown): FluidUndoCountdownState {
  val state = rememberSaveable(timing, saver = FluidUndoCountdownState.saver(timing)) {
    FluidUndoCountdownState(timing)
  }
  TrackFluidUndoDeadline(state)
  return state
}

/** La faccia temporanea di un tasto. Testo, lingua, azione e navigazione appartengono al chiamante. */
@Immutable
data class FluidUndoCountdown(
  val state: FluidUndoCountdownState,
  val onUndo: () -> Unit,
  val label: String = "Annulla",
  val accessibilityLabel: String = label,
  val remainingLabel: (Long) -> String = { seconds -> if (seconds == 1L) "1 secondo" else "$seconds secondi" },
) {
  internal fun undo() {
    state.undo(onUndo)
  }
}

@Composable
internal fun TrackFluidUndoDeadline(state: FluidUndoCountdownState?) {
  LaunchedEffect(state) { state?.followDeadline() }
}

/** L'orologio resta nel disegno. Si ricompone il testo di accessibilità solo al cambio di secondo annunciato. */
@Composable
internal fun rememberFluidUndoFill(state: FluidUndoCountdownState): State<Float> {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val fill = remember(state) { mutableFloatStateOf(state.fill()) }
  LaunchedEffect(state, reduced) {
    // Una finestra lunga (la copia delicata, 60 s: F8.3) avanza di frazioni di punto per fotogramma: a passi di 125 ms il
    // bordo si sposta di meno di mezzo dp su un tasto da 120, e il tasto non ridisegna 120 volte al secondo per un minuto.
    val long = state.timing.durationMillis >= FluidUndoLongWindowMillis
    while (state.isActive) {
      fill.floatValue = state.fill()
      when {
        reduced -> delay(state.nextSecondDelay())
        long -> delay(FluidUndoLongWindowStepMillis)
        else -> withInfiniteAnimationFrameNanos { }
      }
    }
    fill.floatValue = state.fill()
  }
  return fill
}

@Composable
internal fun rememberFluidUndoDescription(countdown: FluidUndoCountdown?): String? {
  if (countdown == null) return null
  val state = countdown.state
  val spoken = remember(state) { mutableStateOf(state.announcementSeconds()) }
  LaunchedEffect(state) {
    while (state.isActive) {
      spoken.value = state.announcementSeconds()
      delay(state.nextSecondDelay())
    }
  }
  return "${countdown.accessibilityLabel}, ${countdown.remainingLabel(spoken.value)}"
}

internal fun SemanticsPropertyReceiver.fluidUndoDescription(description: String) {
  contentDescription = description
  liveRegion = LiveRegionMode.Polite
}

internal fun Modifier.fluidUndoFill(fill: State<Float>, color: Color): Modifier =
  clip(FluidCapsuleShape).drawBehind {
    val width = size.width * fill.value
    val start = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
    drawRect(color.copy(alpha = FluidUndoFillAlpha), Offset(start, 0f), Size(width, size.height))
  }

/** Il pieno avanza sul posto. A occhio, 21 novembre 2026, sullo stesso peso ottico di una selezione. */
private const val FluidUndoFillAlpha = 0.24f

/** Da quanto una finestra è lunga (10 s) e il passo del suo pieno (125 ms). A occhio, 23 novembre 2026 (F8.3). */
internal const val FluidUndoLongWindowMillis = 10_000L
internal const val FluidUndoLongWindowStepMillis = 125L
private const val MillisPerSecond = 1_000L

/** Tre annunci nella maggior parte delle finestre brevi. A occhio, 21 novembre 2026. */
internal fun FluidUndoCountdownState.announcementSeconds(): Long {
  val seconds = spokenSeconds()
  val total = timing.durationMillis / MillisPerSecond +
    if (timing.durationMillis % MillisPerSecond == 0L) 0L else 1L
  val interval = when {
    seconds > 10 -> 5L
    seconds > 3 -> 2L
    else -> 1L
  }
  return (total - (total - seconds) / interval * interval).coerceAtLeast(1L)
}

private fun FluidUndoCountdownState.nextSecondDelay(): Long =
  (remainingMillis() % MillisPerSecond).takeIf { it > 0L } ?: MillisPerSecond

internal data class FluidUndoSlot(val active: FluidUndoCountdown?, val face: FluidUndoCountdown?)

/**
 * La faccia di un posto col contatore per [FluidGlassContentTransition]: il contatore ([countdown]) o la faccia di riposo
 * (null). **Lo stato della transizione non è mai `null`**, e nemmeno la chiave: la transizione non disegna uno strato con lo
 * stato `null` e tratta la chiave `null` come il vuoto, e un tasto che tornava a riposo dopo il contatore restava un vetro
 * senza contenuto (F8.3, visto sul tablet con la copia delicata; provato in `F8p3FieldsUiTest`).
 */
@Immutable
internal class FluidUndoFace(val countdown: FluidUndoCountdown?) {
  val key: Any get() = countdown?.label ?: FluidUndoRestFace
}

private object FluidUndoRestFace

/** Tiene il nodo del tasto anche quando l'app ritira il contatore e la faccia in uscita si sfoca. */
@Composable
internal fun rememberFluidUndoSlot(countdown: FluidUndoCountdown?): FluidUndoSlot? {
  val active = countdown?.takeIf { it.state.isActive }
  var installed by remember { mutableStateOf(countdown != null) }
  var face by remember { mutableStateOf(active) }
  if (!installed && countdown == null) return null
  TrackFluidUndoDeadline(active?.state)
  SideEffect {
    installed = true
    face = active
  }
  return FluidUndoSlot(active, face)
}

@Composable
internal fun fluidUndoLabelWidth(text: String, style: TextStyle, padding: Dp): Dp {
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val px = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
  return ceil(px / density.density).dp + padding * 2
}
