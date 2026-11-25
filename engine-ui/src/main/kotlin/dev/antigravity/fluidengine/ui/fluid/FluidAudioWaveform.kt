package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// F8.13 · L'onda audio da scorrere (idea `onda-audio`) e l'onda della registrazione di Memo Vocali.
//
// Due forme dello stesso inviluppo ([FluidAudioEnvelope] di engine-foundation):
//  - [FluidAudioWaveform], **tutta nella riga**, come il messaggio audio di Messaggi: barre tonde, quelle gia'
//    ascoltate nell'accento, il dito che scorre per andare avanti e indietro;
//  - [FluidAudioTimeline], **che scorre sotto la testina ferma al centro**, come la registrazione e il taglio di Memo
//    Vocali di iOS 26: il righello dei secondi sotto, il pizzico per lo zoom, la testina rossa mentre registra.
//
// Tutte e due sono mosse dal dito, quindi **nitide** (regola di Alessio del 24 novembre: «dito nitido, app sfocata»):
// niente sfocatura nel trascinamento, nel lancio ne' quando l'onda scorre da sola durante l'ascolto, che e' la stessa
// pagina e non un contenuto che cambia identita'. La posizione arriva come lambda, come in `FluidSpokenText` e in
// `FluidScrubber`: letta nel disegno, il battito del lettore ridipinge senza ricomporre, e un'onda ferma non disegna.

/** Le misure, i colori di serie e i testi di [FluidAudioWaveform] e [FluidAudioTimeline]. */
object FluidAudioWaveformDefaults {

  /**
   * Il passo delle barre: 4 dp. Misurato sulla guida dell'iPhone (iOS 26, «Send and receive audio messages» e «Make a
   * recording» di Memo Vocali): 4,5 px a 1,13 px per punto, nella bolla di Messaggi e nell'onda di Memo Vocali. Fiducia
   * alta sul passo, le immagini sono ufficiali.
   */
  val BarPitch: Dp = 4.dp

  /**
   * La barra della riga: 2 dp, tonda. Nella bolla di Messaggi le barre sono 1–2 px nella guida (~1,5 pt); su Android
   * 2 dp hanno lo stesso peso. A occhio, 25 novembre 2026.
   */
  val BarWidth: Dp = 2.dp

  /** L'altezza di serie della riga: 44 dp, il bersaglio del dito. Le barre ne usano il 70% al massimo. */
  val Height: Dp = 44.dp

  /** Quanto dell'altezza usa la barra piu' alta della riga. */
  const val BarHeightFraction: Float = 0.7f

  /** Il tratto di un salto con la tastiera e con le azioni di TalkBack: 15 s, come i tasti di Memo Vocali. */
  const val KeyStepMillis: Long = 15_000L

  /** Il nome per TalkBack. */
  const val ContentDescription: String = "Posizione nell'audio"

  const val StepBackLabel: String = "Indietro di 15 secondi"
  const val StepForwardLabel: String = "Avanti di 15 secondi"

  /** Il valore a voce: «1:05 di 3:20». */
  val Spoken: (positionMillis: Long, durationMillis: Long) -> String = { position, duration ->
    "${FluidScrubbing.clock(position)} di ${FluidScrubbing.clock(duration)}"
  }
}

/**
 * L'**onda audio da scorrere**, tutta nella riga (F8.13, idea `onda-audio`): il messaggio audio di Messaggi di iOS 26.
 * Barre tonde ogni [FluidAudioWaveformDefaults.BarPitch], alte quanto la media del loro tratto di [envelope]; quelle
 * gia' ascoltate in [playedColor], le altre in [pendingColor]; un silenzio è un puntino (la barra piu' bassa e' larga
 * quanto e' alta), come i tratti muti di Memo Vocali.
 *
 * **Il dito**, come [FluidScrubber], in tre tempi: a riposo l'onda mostra [position]; sotto il dito mostra il dito, che
 * sposta la posizione **di quanto si muove** (relativo, come «scorri a destra e a sinistra sul messaggio» di Messaggi:
 * prenderla non la sposta), con [onScrub] a ogni valore; al rilascio [onSeek] **una volta**, e l'onda tiene il punto
 * lasciato finche' il lettore non l'ha raggiunto o passa [FluidMotion.ScrubberHoldMillis]. Un **tocco** salta al punto
 * toccato. Le estremita' suonano un `Threshold`; nel mezzo niente aptica, come lo scrubber continuo (un tick per barra
 * sarebbe un ronzio).
 *
 * **Accessibilita'.** Per TalkBack e' uno slider: [contentDescription], il valore a voce ([valueDescription]),
 * l'intervallo, `setProgress` e due azioni da [keyStepMillis]. Da tastiera, le frecce. Con le animazioni ridotte non
 * cambia niente: l'onda non ha animazioni sue.
 *
 * **Costo.** Le altezze delle barre si calcolano quando cambiano la larghezza o l'inviluppo (per uno che cresce, quando
 * cresce), in un array riusato; il disegno non alloca e un'onda ferma non disegna.
 *
 * @param envelope l'inviluppo, da `engine-audio` (la registrazione, o un file decodificato) o dall'app.
 * @param position dove e' l'ascolto, in millisecondi. Una lambda: vedi l'intestazione del file.
 * @param onSeek il salto, una volta, in millisecondi dentro `0..durationMillis`.
 * @param durationMillis la durata vera dell'audio; di serie quella dell'inviluppo.
 */
@Composable
fun FluidAudioWaveform(
  envelope: FluidAudioEnvelope,
  position: () -> Long,
  onSeek: (Long) -> Unit,
  modifier: Modifier = Modifier,
  durationMillis: Long = envelope.durationMillis,
  enabled: Boolean = true,
  onScrubStart: (() -> Unit)? = null,
  onScrub: ((Long) -> Unit)? = null,
  onScrubEnd: (() -> Unit)? = null,
  playedColor: Color = MaterialTheme.colorScheme.primary,
  pendingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
  barWidth: Dp = FluidAudioWaveformDefaults.BarWidth,
  barPitch: Dp = FluidAudioWaveformDefaults.BarPitch,
  contentDescription: String = FluidAudioWaveformDefaults.ContentDescription,
  valueDescription: (positionMillis: Long, durationMillis: Long) -> String = FluidAudioWaveformDefaults.Spoken,
  keyStepMillis: Long = FluidAudioWaveformDefaults.KeyStepMillis,
  stepBackLabel: String = FluidAudioWaveformDefaults.StepBackLabel,
  stepForwardLabel: String = FluidAudioWaveformDefaults.StepForwardLabel,
) {
  val duration = durationMillis.coerceAtLeast(0L)
  val haptics = LocalFluidHaptics.current
  val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
  val hold = remember { FluidAudioSeekHold() }
  val latestPosition by rememberUpdatedState(position)
  val latestSeek by rememberUpdatedState(onSeek)
  val bars = remember { FluidWaveformBars() }
  hold.Settle(latestPosition, duration)

  fun shown(): Long = hold.shown(latestPosition()).coerceIn(0L, duration)
  fun seek(target: Long, origin: Long) {
    val clamped = target.coerceIn(0L, duration)
    hold.release(clamped, origin)
    latestSeek(clamped)
  }

  val input = if (enabled && duration > 0L) {
    Modifier
      .pointerInput(duration, forward) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val slop = viewConfiguration.touchSlop
          var dragging = false
          var travelled = 0f
          var vertical = 0f
          var origin = 0L
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
              if (dragging) {
                val target = hold.value
                hold.endDrag()
                seek(target, origin)
                onScrubEnd?.invoke()
              } else if (!change.isConsumed) {
                // Un tocco: si salta dove si e' toccato.
                val fraction = (down.position.x / size.width).fastCoerceIn(0f, 1f).let { if (forward > 0) it else 1f - it }
                val target = (duration * fraction).roundToLong()
                if (target == 0L || target == duration) haptics.play(FluidHapticEvent.Threshold) else haptics.play(FluidHapticEvent.Tap)
                seek(target, shown())
              }
              break
            }
            // La pagina che scorre in verticale si prende il dito: l'onda lo lascia.
            if (!dragging && change.isConsumed) break
            val delta = change.positionChange()
            travelled += delta.x * forward
            vertical += delta.y
            if (!dragging) {
              if (abs(vertical) > slop && abs(vertical) > abs(travelled)) break
              if (abs(travelled) > slop) {
                dragging = true
                origin = shown()
                hold.drag(origin)
                onScrubStart?.invoke()
              }
            }
            if (dragging) {
              change.consume()
              // 1:1 dal punto in cui il dito si e' posato (lo slop compreso): l'onda sta sotto il dito.
              val before = hold.value
              val next = (origin + travelled / size.width * duration).roundToLong().coerceIn(0L, duration)
              if (next != before) {
                hold.moveDrag(next)
                if ((next == 0L || next == duration) && before != next) haptics.play(FluidHapticEvent.Threshold)
                onScrub?.invoke(next)
              }
            }
          }
        }
      }
      .onKeyEvent { event ->
        val direction = when (event.key) {
          Key.DirectionRight -> forward
          Key.DirectionLeft -> -forward
          else -> 0
        }
        if (direction != 0 && event.type == KeyEventType.KeyDown) {
          val from = shown()
          seek(from + direction * keyStepMillis, from)
          true
        } else {
          false
        }
      }
      .focusable()
  } else {
    Modifier
  }

  Spacer(
    modifier
      .fillMaxWidth()
      .height(FluidAudioWaveformDefaults.Height)
      .semantics {
        this.contentDescription = contentDescription
        val now = shown()
        stateDescription = valueDescription(now, duration)
        progressBarRangeInfo = ProgressBarRangeInfo(now.toFloat(), 0f..duration.coerceAtLeast(1L).toFloat())
        if (enabled && duration > 0L) {
          setProgress { target ->
            if (!target.isFinite()) return@setProgress false
            seek(target.roundToLong(), shown())
            true
          }
          customActions = listOf(
            CustomAccessibilityAction(stepBackLabel) { val from = shown(); seek(from - keyStepMillis, from); true },
            CustomAccessibilityAction(stepForwardLabel) { val from = shown(); seek(from + keyStepMillis, from); true },
          )
        } else {
          disabled()
        }
      }
      .then(input)
      .drawBehind {
        val pitch = barPitch.toPx()
        val width = barWidth.toPx().coerceAtMost(pitch)
        val count = floor((size.width + pitch - width) / pitch).toInt().coerceAtLeast(0)
        if (count == 0) return@drawBehind
        val heights = bars.heights(envelope, count, duration)
        val played = if (duration > 0L) shown().toFloat() / duration else 0f
        val maxHalf = size.height * FluidAudioWaveformDefaults.BarHeightFraction / 2f
        val centerY = size.height / 2f
        // Le barre si centrano nella riga: lo spazio che avanza si divide fra i due lati.
        val start = (size.width - ((count - 1) * pitch + width)) / 2f + width / 2f
        for (i in 0 until count) {
          val half = max(width / 2f, heights[i] * maxHalf)
          val slot = start + i * pitch
          val x = if (forward > 0) slot else size.width - slot
          val color = if ((i + 0.5f) / count <= played) playedColor else pendingColor
          drawBar(color, x, centerY, half, width)
        }
      },
  )
}

/** Una barra tonda verticale centrata in [x], alta `2 * half` (un puntino se `half` e' mezzo spessore). */
private fun DrawScope.drawBar(color: Color, x: Float, centerY: Float, half: Float, width: Float) {
  val inner = (half - width / 2f).coerceAtLeast(0f)
  drawLine(color, Offset(x, centerY - inner), Offset(x, centerY + inner), strokeWidth = width, cap = StrokeCap.Round)
}

/**
 * Le altezze delle barre della riga, ricalcolate solo quando cambiano l'inviluppo, il numero delle barre, la durata o
 * (per un inviluppo che cresce) la sua misura. L'array si rialloca solo se le barre sono di piu'.
 */
private class FluidWaveformBars {
  private var values = FloatArray(0)
  private var envelope: FluidAudioEnvelope? = null
  private var count = -1
  private var size = -1
  private var duration = -1L

  fun heights(envelope: FluidAudioEnvelope, count: Int, duration: Long): FloatArray {
    if (envelope === this.envelope && count == this.count && envelope.size == size && duration == this.duration) return values
    if (values.size < count) values = FloatArray(count)
    val span = if (count > 0) duration.toDouble() / count else 0.0
    val bucket = envelope.bucketMillis
    for (i in 0 until count) {
      val from = (i * span).toLong()
      val to = ((i + 1) * span).toLong().coerceAtLeast(from + 1)
      // La media del tratto, non il picco: una barra della riga copre centinaia di millisecondi, e col picco il parlato
      // diventava un muro di barre alte uguali (visto sul Tab S9 il 25/11/2026). Messaggi ha altezze varie. Un tratto
      // piu' corto di un valore prende il valore.
      val first = (from / bucket).toInt()
      val last = ((to - 1) / bucket).toInt().coerceAtMost(envelope.size - 1)
      if (last <= first) {
        values[i] = envelope.peak(from, to)
      } else {
        var sum = 0f
        for (k in first..last) sum += envelope[k]
        values[i] = sum / (last - first + 1)
      }
    }
    this.envelope = envelope
    this.count = count
    this.size = envelope.size
    this.duration = duration
    return values
  }
}

/**
 * Il dito e il punto lasciato, in comune fra la riga e l'onda che scorre: i tre tempi di [FluidScrubber]. Gli stati sono
 * di Compose, ma si leggono solo nel disegno.
 */
@Stable
internal class FluidAudioSeekHold {
  /** 0 a riposo, 1 sotto il dito, 2 tenuto dopo il rilascio. */
  var phase by mutableIntStateOf(0)
    private set

  /** Il valore sotto il dito, o quello lasciato. */
  var value by mutableLongStateOf(0L)
    private set

  private var origin = 0L
  private var heldAt by mutableLongStateOf(0L)

  fun shown(external: Long): Long = if (phase == 0) external else value

  fun drag(at: Long) {
    value = at
    phase = 1
  }

  fun moveDrag(at: Long) {
    value = at
  }

  fun endDrag() {
    if (phase == 1) phase = 0
  }

  fun release(target: Long, from: Long) {
    value = target
    origin = from
    phase = 2
    heldAt++
  }

  /** Finisce il fermo quando il lettore e' arrivato, o dopo [FluidMotion.ScrubberHoldMillis]. */
  @Composable
  fun Settle(position: () -> Long, duration: Long) {
    val key = heldAt
    LaunchedEffect(key) {
      if (phase != 2) return@LaunchedEffect
      val range = 0f..duration.coerceAtLeast(1L).toFloat()
      withTimeoutOrNull(FluidMotion.ScrubberHoldMillis) {
        snapshotFlow { position() }.first { external ->
          FluidScrubbing.caughtUp(external.toFloat(), value.toFloat(), origin.toFloat(), range, 0f)
        }
      }
      if (phase == 2 && heldAt == key) phase = 0
    }
  }
}

// ----------------------------------------------------------------------------------------------------------------------
// L'onda che scorre
// ----------------------------------------------------------------------------------------------------------------------

/** Le misure di [FluidAudioTimeline], da Memo Vocali di iOS 26 (guida dell'iPhone, «Make a recording» e «Edit»). */
object FluidAudioTimelineDefaults {

  /**
   * Lo zoom di serie: 100 dp al secondo. Misurato sul righello della registrazione e del taglio di Memo Vocali (113 px
   * fra due secondi a 1,13 px per punto): fiducia alta. A questo zoom una barra ogni 4 dp e' una barra ogni 40 ms.
   */
  const val DefaultDpPerSecond: Float = 100f

  /** Lo zoom massimo col pizzico: 400 dp al secondo, una barra ogni 10 ms. A occhio, 25 novembre 2026. */
  const val MaxDpPerSecond: Float = 400f

  /** Lo zoom minimo: l'audio intero nella larghezza, ma mai sotto 0,5 dp al secondo. */
  const val MinDpPerSecond: Float = 0.5f

  /** Le barre dell'onda che scorre: 1 dp ogni 4 dp. Nella guida una barra e' 1 px a 1,13 px per punto: fiducia media. */
  val BarWidth: Dp = 1.dp

  /** La testina: 1 dp, con un pallino da 7 dp sopra e sotto la fascia (8 px nella guida). */
  val PlayheadWidth: Dp = 1.dp
  val PlayheadDot: Dp = 7.dp

  /** L'altezza del righello sotto la fascia, con le scritte dei secondi. */
  val RulerHeight: Dp = 24.dp

  /** L'altezza di serie: 240 dp. In Memo Vocali la fascia e' alta ~305 punti a tutto schermo; qui e' un componente. */
  val Height: Dp = 240.dp

  /** Quanto della fascia usa la barra piu' alta. */
  const val BarHeightFraction: Float = 0.86f

  /** Il nome per TalkBack. */
  const val ContentDescription: String = FluidAudioWaveformDefaults.ContentDescription
  const val ZoomInLabel: String = "Ingrandisci l'onda"
  const val ZoomOutLabel: String = "Riduci l'onda"

  /** Il rosso della testina mentre si registra: `systemRed` delle HIG «Color» (#FF3B30 in chiaro, #FF453A in scuro). */
  val RecordingRedLight: Color = Color(0xFFFF3B30)
  val RecordingRedDark: Color = Color(0xFFFF453A)

  /**
   * Ogni quanti secondi una scritta del righello, perche' due scritte stiano ad almeno [minLabelDp]: 1, 2, 5, 10, 15,
   * 30 s, poi 1, 2, 5, 10, 15, 30 minuti, poi le ore.
   */
  fun labelEverySeconds(dpPerSecond: Float, minLabelDp: Float = 64f): Long {
    for (candidate in LabelSteps) if (candidate * dpPerSecond >= minLabelDp) return candidate
    var hours = 3_600L
    while (hours * dpPerSecond < minLabelDp) hours *= 2
    return hours
  }

  private val LabelSteps = longArrayOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1_800, 3_600)
}

/**
 * Lo zoom di un'[FluidAudioTimeline], in dp al secondo: il pizzico lo cambia, l'app lo legge o lo fissa. Di serie
 * [FluidAudioTimelineDefaults.DefaultDpPerSecond].
 */
@Stable
class FluidAudioTimelineState(initialDpPerSecond: Float = FluidAudioTimelineDefaults.DefaultDpPerSecond) {
  /** Lo zoom di adesso, in dp al secondo. */
  var dpPerSecond by mutableFloatStateOf(initialDpPerSecond)
    internal set

  /** Fissa lo zoom, dentro i limiti di [FluidAudioTimelineDefaults]. */
  fun zoomTo(dpPerSecond: Float) {
    if (dpPerSecond.isFinite()) {
      this.dpPerSecond = dpPerSecond.coerceIn(FluidAudioTimelineDefaults.MinDpPerSecond, FluidAudioTimelineDefaults.MaxDpPerSecond)
    }
  }
}

/** Lo stato dello zoom per questa composizione. */
@Composable
fun rememberFluidAudioTimelineState(initialDpPerSecond: Float = FluidAudioTimelineDefaults.DefaultDpPerSecond): FluidAudioTimelineState =
  remember { FluidAudioTimelineState(initialDpPerSecond) }

/**
 * L'**onda che scorre sotto la testina** (F8.13): la registrazione e il taglio di Memo Vocali di iOS 26. La testina sta
 * ferma al centro, con un pallino sopra e uno sotto la fascia; l'onda scorre sotto di lei, a
 * [FluidAudioTimelineState.dpPerSecond] (di serie 100 dp al secondo, come Memo Vocali); sotto, il righello dei secondi.
 *
 * **Mentre si registra** ([recording] vero) la testina e le barre sono rosse (come Memo Vocali: visto accanto alla guida
 * dell'iPhone sul Tab S9 il 25/11/2026, dove le nostre erano nere), a destra c'e' solo la linea a puntini di cio' che deve
 * ancora venire, e l'onda non si tocca: e' il registratore che la fa crescere. Con [advancing] vero ridisegna a ogni
 * fotogramma (la posizione e' un orologio, non uno stato); fermo, non disegna.
 *
 * **Ascoltando**, il dito trascina l'onda 1:1 (l'onda va dove va il dito, la posizione al contrario), e la lancia con
 * l'inerzia; al rilascio, o a fine lancio, [onSeek] una volta, e il punto resta finche' il lettore non l'ha raggiunto.
 * Il **pizzico** cambia lo zoom attorno alla testina, fra l'audio intero nella larghezza e
 * [FluidAudioTimelineDefaults.MaxDpPerSecond]. Niente sfocatura in nessuno dei due: li muove il dito.
 *
 * **Accessibilita'.** Uno slider come [FluidAudioWaveform], piu' due azioni per lo zoom. **Movimento ridotto**: il
 * lancio non c'e' (l'onda si ferma dove il dito la lascia).
 *
 * @param durationMillis la durata, letta a ogni disegno: mentre si registra cresce.
 * @param recording la forma della registrazione (vedi sopra).
 * @param advancing vero mentre la posizione avanza da sola senza essere uno stato (la registrazione non in pausa).
 * @param bandColor il fondo della fascia: di serie il grigio chiaro di Memo Vocali, `surfaceContainerHigh`.
 */
@Composable
fun FluidAudioTimeline(
  envelope: FluidAudioEnvelope,
  position: () -> Long,
  onSeek: (Long) -> Unit,
  modifier: Modifier = Modifier,
  durationMillis: () -> Long = { envelope.durationMillis },
  state: FluidAudioTimelineState = rememberFluidAudioTimelineState(),
  recording: Boolean = false,
  advancing: Boolean = false,
  enabled: Boolean = !recording,
  onScrubStart: (() -> Unit)? = null,
  onScrub: ((Long) -> Unit)? = null,
  onScrubEnd: (() -> Unit)? = null,
  barColor: Color = if (recording) recordingRed() else MaterialTheme.colorScheme.onSurface,
  playheadColor: Color = if (recording) recordingRed() else MaterialTheme.colorScheme.primary,
  bandColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  rulerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  contentDescription: String = FluidAudioTimelineDefaults.ContentDescription,
  valueDescription: (positionMillis: Long, durationMillis: Long) -> String = FluidAudioWaveformDefaults.Spoken,
  keyStepMillis: Long = FluidAudioWaveformDefaults.KeyStepMillis,
  stepBackLabel: String = FluidAudioWaveformDefaults.StepBackLabel,
  stepForwardLabel: String = FluidAudioWaveformDefaults.StepForwardLabel,
  zoomInLabel: String = FluidAudioTimelineDefaults.ZoomInLabel,
  zoomOutLabel: String = FluidAudioTimelineDefaults.ZoomOutLabel,
) {
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
  val hold = remember { FluidAudioSeekHold() }
  val latestPosition by rememberUpdatedState(position)
  val latestDuration by rememberUpdatedState(durationMillis)
  val latestSeek by rememberUpdatedState(onSeek)
  val labels = remember { FluidRulerLabels() }
  val measurer = rememberTextMeasurer(cacheSize = 0)
  val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", color = rulerColor)
  // Il battito dei fotogrammi mentre la posizione avanza da sola: letto solo nel disegno.
  var frame by remember { mutableIntStateOf(0) }
  if (advancing) {
    LaunchedEffect(Unit) {
      while (true) {
        withFrameNanos { }
        frame++
      }
    }
  }
  hold.Settle(latestPosition, latestDuration().coerceAtLeast(1L))

  fun duration(): Long = latestDuration().coerceAtLeast(0L)
  fun shown(): Long = hold.shown(latestPosition()).coerceIn(0L, duration())
  fun seek(target: Long, origin: Long) {
    val clamped = target.coerceIn(0L, duration())
    hold.release(clamped, origin)
    latestSeek(clamped)
  }

  val interactive = enabled && !recording
  val input = if (interactive) {
    Modifier
      .pointerInput(forward, reducedMotion) {
        coroutineScope {
          var fling: Job? = null
          var flingOrigin = 0L
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Un dito che si posa ferma il lancio: l'onda resta dov'e' arrivata.
            val stopped = fling?.isActive == true
            fling?.cancel()
            fling = null
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var origin = shown()
            var dragging = false
            var travelled = 0f
            var at = 0f
            var pinchStart = 0f
            var zoomStart = state.dpPerSecond
            val slop = viewConfiguration.touchSlop
            while (true) {
              val event = awaitPointerEvent()
              val pressed = event.changes.filter { it.pressed }
              if (pressed.isEmpty()) break
              if (pressed.size >= 2) {
                // Il pizzico: lo zoom segue il rapporto delle distanze fra le due dita, attorno alla testina.
                val a = pressed[0].position
                val b = pressed[1].position
                val distance = hypot(a.x - b.x, a.y - b.y)
                if (pinchStart == 0f) {
                  pinchStart = distance
                  zoomStart = state.dpPerSecond
                } else if (distance > 0f) {
                  val fit = fitDpPerSecond(size.width / density.density, duration())
                  state.dpPerSecond = (zoomStart * distance / pinchStart)
                    .coerceIn(max(fit, FluidAudioTimelineDefaults.MinDpPerSecond), FluidAudioTimelineDefaults.MaxDpPerSecond)
                }
                pressed.forEach { it.consume() }
                continue
              }
              pinchStart = 0f
              val change = pressed[0]
              tracker.addPosition(change.uptimeMillis, change.position)
              val dx = change.positionChange().x * forward
              if (!dragging) {
                travelled += dx
                if (abs(travelled) > slop) {
                  dragging = true
                  if (!stopped) {
                    origin = shown()
                    hold.drag(origin)
                    onScrubStart?.invoke()
                  }
                  // 1:1 dal punto in cui il dito si e' posato: lo slop gia' percorso conta.
                  val msPerPx = 1000f / (state.dpPerSecond * density.density)
                  at = (hold.value - travelled * msPerPx).fastCoerceIn(0f, duration().toFloat())
                  hold.moveDrag(at.roundToLong())
                }
              } else {
                // L'onda segue il dito 1:1: il dito a destra porta indietro nel tempo.
                val msPerPx = 1000f / (state.dpPerSecond * density.density)
                val before = hold.value
                at = (at - dx * msPerPx).fastCoerceIn(0f, duration().toFloat())
                val next = at.roundToLong()
                if (next != before) {
                  hold.moveDrag(next)
                  if ((next == 0L || next == duration()) && before != next) haptics.play(FluidHapticEvent.Threshold)
                  onScrub?.invoke(next)
                }
                change.consume()
              }
            }
            if (!dragging) {
              // Un tocco che ferma un lancio: il salto va dove l'onda si e' fermata.
              if (stopped) {
                hold.endDrag()
                seek(hold.value, flingOrigin)
                onScrubEnd?.invoke()
              }
              return@awaitEachGesture
            }
            // Il lancio: un decadimento esponenziale della velocita' del dito, come lo scorrimento di una lista. Con il
            // movimento ridotto si ferma dove il dito l'ha lasciata.
            val velocity = tracker.calculateVelocity().x * forward
            val from = if (stopped) flingOrigin else origin
            if (!reducedMotion && abs(velocity) > FlingMinVelocity) {
              val msPerPx = 1000f / (state.dpPerSecond * density.density)
              flingOrigin = from
              fling = launch {
                var speed = -velocity * msPerPx // ms di audio per secondo di tempo
                var at = hold.value.toFloat()
                var last = 0L
                while (abs(speed) > FlingStopSpeed) {
                  val now = withFrameNanos { it }
                  if (last != 0L) {
                    val dt = (now - last) / 1e9f
                    at = (at + speed * dt).fastCoerceIn(0f, duration().toFloat())
                    speed *= exp(-FlingFriction * dt)
                    hold.moveDrag(at.roundToLong())
                    onScrub?.invoke(at.roundToLong())
                    if (at <= 0f || at >= duration()) {
                      haptics.play(FluidHapticEvent.Threshold)
                      break
                    }
                  }
                  last = now
                }
                hold.endDrag()
                seek(at.roundToLong(), from)
                onScrubEnd?.invoke()
              }
            } else {
              hold.endDrag()
              seek(hold.value, from)
              onScrubEnd?.invoke()
            }
          }
        }
      }
      .onKeyEvent { event ->
        val direction = when (event.key) {
          Key.DirectionRight -> forward
          Key.DirectionLeft -> -forward
          else -> 0
        }
        if (direction != 0 && event.type == KeyEventType.KeyDown) {
          val from = shown()
          seek(from + direction * keyStepMillis, from)
          true
        } else {
          false
        }
      }
      .focusable()
  } else {
    Modifier
  }

  Spacer(
    modifier
      .fillMaxWidth()
      .height(FluidAudioTimelineDefaults.Height)
      .semantics {
        this.contentDescription = contentDescription
        val now = shown()
        val total = duration()
        stateDescription = valueDescription(now, total)
        progressBarRangeInfo = ProgressBarRangeInfo(now.toFloat(), 0f..total.coerceAtLeast(1L).toFloat())
        if (interactive) {
          setProgress { target ->
            if (!target.isFinite()) return@setProgress false
            seek(target.roundToLong(), shown())
            true
          }
          customActions = listOf(
            CustomAccessibilityAction(stepBackLabel) { val from = shown(); seek(from - keyStepMillis, from); true },
            CustomAccessibilityAction(stepForwardLabel) { val from = shown(); seek(from + keyStepMillis, from); true },
            CustomAccessibilityAction(zoomInLabel) { state.zoomTo(state.dpPerSecond * 2f); true },
            CustomAccessibilityAction(zoomOutLabel) { state.zoomTo(state.dpPerSecond / 2f); true },
          )
        } else if (!recording) {
          disabled()
        }
      }
      .then(input)
      .drawBehind {
        // Il battito: lo si legge perche' il disegno si rifaccia a ogni fotogramma mentre la posizione avanza.
        if (frame < 0) return@drawBehind
        val total = duration()
        val now = shown()
        val ruler = FluidAudioTimelineDefaults.RulerHeight.toPx()
        val dot = FluidAudioTimelineDefaults.PlayheadDot.toPx()
        val bandTop = dot / 2f
        val bandBottom = size.height - ruler
        val centerX = size.width / 2f
        val centerY = (bandTop + bandBottom) / 2f
        val pxPerMs = state.dpPerSecond * density.density / 1000f
        val pitch = FluidAudioWaveformDefaults.BarPitch.toPx()
        val width = FluidAudioTimelineDefaults.BarWidth.toPx()
        val msPerBar = pitch / pxPerMs
        drawRect(bandColor, topLeft = Offset(0f, bandTop), size = androidx.compose.ui.geometry.Size(size.width, bandBottom - bandTop))

        // Le barre stanno su una griglia di tempo fissa: scorrendo si spostano con l'onda, non si ricalcolano.
        val leftMs = now - centerX / pxPerMs
        val rightMs = now + centerX / pxPerMs
        val firstBar = floor(leftMs / msPerBar).toLong().coerceAtLeast(0L)
        val lastBar = ceil(rightMs / msPerBar).toLong()
        val maxHalf = (bandBottom - bandTop) * FluidAudioTimelineDefaults.BarHeightFraction / 2f
        val faded = barColor.copy(alpha = barColor.alpha * 0.35f)
        var bar = firstBar
        while (bar <= lastBar) {
          val fromMs = (bar * msPerBar).toLong()
          val toMs = ((bar + 1) * msPerBar).toLong().coerceAtLeast(fromMs + 1)
          val x = centerX + forward * ((bar + 0.5f) * msPerBar - now) * pxPerMs
          if (fromMs < total) {
            val level = envelope.peak(fromMs, min(toMs, total))
            drawBar(barColor, x, centerY, max(width / 2f, level * maxHalf), width)
          } else if (recording) {
            // Cio' che deve ancora venire: la linea a puntini di Memo Vocali.
            drawBar(faded, x, centerY, width / 2f, width)
          }
          bar++
        }

        // Il righello: tacche ogni quarto dell'intervallo delle scritte, le scritte dei tempi.
        val every = FluidAudioTimelineDefaults.labelEverySeconds(state.dpPerSecond) * 1000L
        val minor = every / 4
        val tickTop = bandBottom + 2.dp.toPx()
        val firstTick = floor(leftMs / minor).toLong().coerceAtLeast(0L)
        val lastTick = ceil(rightMs / minor).toLong()
        var tick = firstTick
        while (tick <= lastTick) {
          val ms = tick * minor
          val x = centerX + forward * (ms - now) * pxPerMs
          val major = ms % every == 0L
          drawLine(rulerColor.copy(alpha = if (major) 0.6f else 0.3f), Offset(x, tickTop), Offset(x, tickTop + (if (major) 6.dp else 4.dp).toPx()), 1.dp.toPx())
          if (major) {
            val layout = labels.get(ms, measurer, labelStyle)
            drawText(layout, topLeft = Offset(x + if (forward > 0) 3.dp.toPx() else -3.dp.toPx() - layout.size.width, tickTop + 4.dp.toPx()))
          }
          tick++
        }

        // La testina, ferma al centro.
        drawLine(playheadColor, Offset(centerX, bandTop), Offset(centerX, bandBottom), FluidAudioTimelineDefaults.PlayheadWidth.toPx())
        drawCircle(playheadColor, dot / 2f, Offset(centerX, bandTop))
        drawCircle(playheadColor, dot / 2f, Offset(centerX, bandBottom))
      },
  )
}

/** Il rosso della registrazione dal lato del tema. */
@Composable
internal fun recordingRed(): Color =
  if (MaterialTheme.colorScheme.isDarkScheme()) FluidAudioTimelineDefaults.RecordingRedDark else FluidAudioTimelineDefaults.RecordingRedLight

private fun androidx.compose.material3.ColorScheme.isDarkScheme(): Boolean = background.luminanceApprox() < 0.5f

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Lo zoom che mette [durationMillis] intero in [widthDp]. */
private fun fitDpPerSecond(widthDp: Float, durationMillis: Long): Float =
  if (durationMillis <= 0L) FluidAudioTimelineDefaults.MaxDpPerSecond else widthDp / (durationMillis / 1000f)

/** Sotto questa velocita' (px/s) un rilascio non e' un lancio. */
private const val FlingMinVelocity = 400f

/** Sotto questa velocita' (ms di audio per secondo) il lancio e' finito. */
private const val FlingStopSpeed = 30f

/** L'attrito del lancio: e^-4 al secondo, come una lista lanciata piano. A occhio, 25 novembre 2026. */
private const val FlingFriction = 4f

/**
 * Le scritte del righello, misurate una volta per tempo: il disegno di un fotogramma non misura testo e non alloca (le
 * chiavi stanno in un `LongArray`, cercate in fila: niente `Long` inscatolati). Si tengono le ultime [Capacity]; una
 * scritta nuova entra solo quando un tempo nuovo entra nella fascia.
 */
private class FluidRulerLabels {
  private val keys = LongArray(Capacity) { Long.MIN_VALUE }
  private val values = arrayOfNulls<TextLayoutResult>(Capacity)
  private var next = 0
  private var style: TextStyle? = null

  fun get(ms: Long, measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle): TextLayoutResult {
    if (style != this.style) {
      keys.fill(Long.MIN_VALUE)
      values.fill(null)
      this.style = style
    }
    for (i in 0 until Capacity) if (keys[i] == ms) values[i]?.let { return it }
    val layout = measurer.measure(FluidScrubbing.clock(ms), style)
    keys[next] = ms
    values[next] = layout
    next = (next + 1) % Capacity
    return layout
  }

  private companion object {
    const val Capacity = 48
  }
}
