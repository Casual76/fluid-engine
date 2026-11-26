package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.viewer.currentFluidViewerZoom
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Dove sta la tendina del confronto: [fraction] e' la parte di «prima» che si vede, da sinistra (0 = tutto «dopo», 1 =
 * tutto «prima»). Lo cambia il dito sulla maniglia, TalkBack, o l'app con [showBefore], [showAfter] e [split].
 */
@Stable
class FluidBeforeAfterState(initial: Float = 0.5f) {
  private val anim = Animatable(initial.coerceIn(0f, 1f))

  /** La parte di «prima» che si vede, da sinistra. */
  val fraction: Float get() = anim.value

  /** Sposta la tendina subito (il dito). */
  suspend fun snapTo(fraction: Float) = anim.snapTo(fraction.coerceIn(0f, 1f))

  /** Tutta «prima» (il tasto «Prima»); [animate] falso col movimento ridotto. */
  suspend fun showBefore(animate: Boolean = true) = to(1f, animate)

  /** Tutta «dopo». */
  suspend fun showAfter(animate: Boolean = true) = to(0f, animate)

  /** Di nuovo a meta'. */
  suspend fun split(animate: Boolean = true) = to(0.5f, animate)

  private suspend fun to(target: Float, animate: Boolean) {
    if (animate) anim.animateTo(target, FluidMotion.standard()) else anim.snapTo(target)
  }
}

/** Lo stato della tendina, ricordato anche dopo la rotazione dello schermo. */
@Composable
fun rememberFluidBeforeAfterState(initial: Float = 0.5f): FluidBeforeAfterState =
  rememberSaveable(saver = Saver(save = { it.fraction }, restore = { FluidBeforeAfterState(it) })) {
    FluidBeforeAfterState(initial)
  }

/** Le parole del confronto. */
@Immutable
data class FluidBeforeAfterLabels(
  val before: String = "Prima",
  val after: String = "Dopo",
  /** Cio' che TalkBack dice della maniglia. */
  val divider: String = "Confronto prima e dopo",
  /** Lo stato della maniglia per TalkBack: quanta parte di «prima» si vede. */
  val state: (Int) -> String = { "Prima al $it%" },
)

/**
 * Il **confronto a tendina** (idea `confronto-prima-dopo`, F8.15): due versioni della stessa immagine una sopra l'altra,
 * e una maniglia che si sposta col dito per vedere [before] a sinistra e [after] a destra. Le due capsule «Prima» e
 * «Dopo» in alto mostrano ciascuna versione intera (un altro tocco torna a meta'), come chiede la scheda dell'idea.
 *
 * In Foto di Apple non c'e' la tendina (Foto mostra l'originale tenendo premuta la foto: lo fa l'editor di
 * `engine-editor`); qui serve dove le due versioni vanno guardate insieme: un ritocco in MyCloud Gallery, gli artefatti
 * della compressione in Universal Converter, una scansione corretta in Pampa Notes.
 *
 * - **Due immagini gia' allineate**: le stesse misure e la stessa inquadratura. L'engine non allinea (la scheda: se le
 *   inquadrature sono diverse, l'allineamento e' dell'app).
 * - **Lo zoom e' condiviso**: dentro il visore dei file (`FluidViewerContent.Image(ratio) { FluidBeforeAfter(…) }`) lo zoom
 *   del visore muove le due versioni insieme, e la maniglia resta della sua misura e nel centro di cio' che si vede.
 *   La maniglia tiene il suo dito: lo sfoglio del visore non parte da li'.
 * - **Dito nitido**: la maniglia segue il dito 1:1, niente sfocature; un `Threshold` quando passa a meta'.
 * - **TalkBack**: la maniglia e' un cursore («Prima al 50%») che si regola con «aumenta» e «diminuisci».
 * - Fermo, non ridisegna: il taglio si legge nel disegno, e cambia solo quando la tendina si muove.
 */
@Composable
fun FluidBeforeAfter(
  before: @Composable () -> Unit,
  after: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  state: FluidBeforeAfterState = rememberFluidBeforeAfterState(),
  labels: FluidBeforeAfterLabels = FluidBeforeAfterLabels(),
  showLabels: Boolean = true,
) {
  val scope = rememberCoroutineScope()
  val haptics = LocalFluidHaptics.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val zoom = currentFluidViewerZoom()
  val density = LocalDensity.current
  val handleTouch = with(density) { FluidBeforeAfterLook.HandleTouch.toPx() }
  BoxWithConstraints(modifier) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    Box(Modifier.fillMaxSize()) { after() }
    Box(
      Modifier.fillMaxSize().drawWithContent {
        clipRect(right = size.width * state.fraction) { this@drawWithContent.drawContent() }
      },
    ) { before() }
    // La linea e la maniglia: della loro misura anche con lo zoom del visore.
    Box(
      Modifier
        .fillMaxSize()
        .pointerInput(state, widthPx) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val lineX = state.fraction * size.width
            val scale = zoom?.scale ?: 1f
            if (abs(down.position.x - lineX) * scale > handleTouch / 2f) return@awaitEachGesture
            down.consume()
            var last = state.fraction
            horizontalDrag(down.id) { change ->
              change.consume()
              val next = (change.position.x / size.width).coerceIn(0f, 1f)
              if ((last - 0.5f) * (next - 0.5f) < 0f) haptics.play(FluidHapticEvent.Threshold)
              last = next
              scope.launch { state.snapTo(next) }
            }
          }
        }
        .semantics {
          contentDescription = labels.divider
          stateDescription = labels.state((state.fraction * 100).roundToInt())
          progressBarRangeInfo = ProgressBarRangeInfo(state.fraction, 0f..1f, steps = 19)
          setProgress { target ->
            scope.launch { state.snapTo(target) }
            true
          }
        },
    ) {
      val ink = Color.White
      Canvas(Modifier.fillMaxSize()) {
        val x = size.width * state.fraction
        val scale = zoom?.scale ?: 1f
        val line = FluidBeforeAfterLook.LineWidth.toPx() / scale
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(x + line, 0f), Offset(x + line, size.height), line)
        drawLine(ink, Offset(x, 0f), Offset(x, size.height), line, cap = StrokeCap.Butt)
      }
      val knob = FluidBeforeAfterLook.Knob
      Box(
        Modifier
          .offset {
            val scale = zoom?.scale ?: 1f
            val x = (widthPx * state.fraction - knob.toPx() / 2f).roundToInt()
            // Il centro di cio' che si vede: il visore sposta il contenuto di pan, a scala `scale`.
            val visibleCenter = heightPx / 2f - (zoom?.panY ?: 0f) / scale
            IntOffset(x, (visibleCenter - knob.toPx() / 2f).roundToInt())
          }
          .size(knob)
          .graphicsLayer {
            val scale = zoom?.scale ?: 1f
            scaleX = 1f / scale
            scaleY = 1f / scale
          }
          .fluidGlass(FluidGlass.Regular, FluidCapsuleShape),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(Modifier.size(FluidBeforeAfterLook.ChevronBox)) {
          val s = size.minDimension
          val stroke = 2.dp.toPx()
          val c = center
          val arm = s * 0.22f
          val gap = s * 0.16f
          // ‹ ›: le due frecce della maniglia.
          drawLine(ink, Offset(c.x - gap, c.y - arm), Offset(c.x - gap - arm, c.y), stroke, StrokeCap.Round)
          drawLine(ink, Offset(c.x - gap - arm, c.y), Offset(c.x - gap, c.y + arm), stroke, StrokeCap.Round)
          drawLine(ink, Offset(c.x + gap, c.y - arm), Offset(c.x + gap + arm, c.y), stroke, StrokeCap.Round)
          drawLine(ink, Offset(c.x + gap + arm, c.y), Offset(c.x + gap, c.y + arm), stroke, StrokeCap.Round)
        }
      }
    }
    if (showLabels) {
      val labelModifier = Modifier.padding(FluidBeforeAfterLook.LabelMargin)
      Box(Modifier.align(Alignment.TopStart).then(labelModifier)) {
        BeforeAfterLabel(labels.before, visible = state.fraction > 0.12f) {
          scope.launch { if (state.fraction > 0.95f) state.split(!reduced) else state.showBefore(!reduced) }
        }
      }
      Box(Modifier.align(Alignment.TopEnd).then(labelModifier)) {
        BeforeAfterLabel(labels.after, visible = state.fraction < 0.88f) {
          scope.launch { if (state.fraction < 0.05f) state.split(!reduced) else state.showAfter(!reduced) }
        }
      }
    }
  }
}

@Composable
private fun BeforeAfterLabel(text: String, visible: Boolean, onClick: () -> Unit) {
  val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, FluidMotion.standard(), label = "etichetta")
  Box(
    Modifier
      .graphicsLayer { this.alpha = alpha }
      .fluidPressable(onClick = onClick, enabled = visible, role = androidx.compose.ui.semantics.Role.Button)
      .background(Color.Black.copy(alpha = FluidBeforeAfterLook.LabelScrim), FluidCapsuleShape)
      .padding(horizontal = 10.dp, vertical = 4.dp),
  ) {
    Text(text.uppercase(), color = Color.White, style = MaterialTheme.typography.labelMedium)
  }
}

/**
 * Le misure della tendina. A occhio (F8.15, 25 novembre 2026): Apple non ha una tendina; la maniglia ha la misura di un
 * tasto tondo piccolo dell'engine e la scritta quella delle etichette sopra la foto in «Regola» di Foto («ESPOSIZIONE»).
 */
internal object FluidBeforeAfterLook {
  val LineWidth = 2.dp
  val Knob = 40.dp
  val ChevronBox = 22.dp
  /** Il dito prende la maniglia entro 44 dp attorno alla linea (il bersaglio minimo). */
  val HandleTouch = 44.dp
  val LabelMargin = 12.dp
  const val LabelScrim = 0.42f
}
