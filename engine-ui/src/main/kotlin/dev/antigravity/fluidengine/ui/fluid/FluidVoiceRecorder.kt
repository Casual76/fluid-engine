package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.foundation.audio.FluidRecordingQuality
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Replay
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession

// F8.13 · Il registratore con l'onda (idea `registratore-vocale`), vicino a Memo Vocali di iOS 26 per misure e
// comportamento: il tasto rosso che galleggia sopra l'elenco, e il pannello della registrazione che nasce da lui (il
// vetro del tasto diventa il pannello, Fluid-physics del piano flottante, e ci torna a registrazione finita). Qui c'e'
// solo l'interfaccia: la cattura, la pausa e la ripresa, il file e l'onda viva le fa `engine-audio`
// (`FluidAudioRecorder`), e il microfono, il servizio in primo piano e dove salvare restano dell'app.

/** In che punto e' un registratore, per l'interfaccia. Nuovo nella 4.0 (F8.13). */
enum class FluidRecorderPhase {
  /** Niente in corso: si vede il tasto rosso. */
  Idle,

  /** Registra: la testina rossa, l'onda che cresce, la capsula con la pausa. */
  Recording,

  /** In pausa: si puo' riascoltare quel che c'e' e riprendere. */
  Paused,
}

/** I testi del registratore, in italiano di serie. Un'app tradotta li passa tutti. */
@Immutable
data class FluidRecorderStrings(
  val record: String = "Registra",
  val pause: String = "Pausa",
  val resume: String = "Riprendi",
  val done: String = "Fine",
  val newRecording: String = "Nuova registrazione",
  val duration: String = "Durata",
  val play: String = "Riproduci",
  val pausePlayback: String = "Metti in pausa l'ascolto",
  val skipBack: String = "Indietro di 15 secondi",
  val skipForward: String = "Avanti di 15 secondi",
  val clipping: String = "Il suono è troppo forte",
  val interrupted: String = "In pausa: il microfono serve a un'altra app",
)

/** Le misure del registratore, da Memo Vocali di iOS 26 (guida dell'iPhone, «Make a recording», 1,13 px per punto). */
object FluidRecorderDefaults {
  /** Il tasto rosso dell'elenco: 52 dp (58 px nella guida). Fiducia alta. */
  val RecordButtonSize: Dp = 52.dp

  /** La capsula della pausa: 147 × 58 dp (166 × 66 px nella guida). Fiducia alta. */
  val PauseCapsuleWidth: Dp = 147.dp
  val PauseCapsuleHeight: Dp = 58.dp

  /** Le due barre rosse della pausa: 9 × 24 dp con 5 dp fra loro (10 × 27 px e 6 px nella guida). */
  val PauseBarWidth: Dp = 9.dp
  val PauseBarHeight: Dp = 24.dp
  val PauseBarGap: Dp = 5.dp

  /** L'onda del pannello: la fascia della registrazione, piu' bassa che a tutto schermo. */
  val TimelineHeight: Dp = 220.dp

  /** Il salto dei tasti di ascolto: 15 s, come Memo Vocali. */
  const val SkipMillis: Long = 15_000L

  /** Il raggio del pannello: quello dei fogli. */
  val PanelRadius: Dp = FluidRadius.Sheet
}

/**
 * Il **tempo della registrazione**, «00:07.00», come Memo Vocali: minuti, secondi e centesimi, 34 sp in grassetto a cifre
 * tabulari ([FluidTextStyles.largeNumeric]); oltre l'ora «1:02:03.45».
 *
 * Cambia cento volte al secondo, quindi **non e' un `Text`**: le cifre sono misurate una volta (dieci cifre, i due punti
 * e il punto) e ridisegnate dal disegno a ogni fotogramma, senza ricomporre e senza allocare, in uno strato suo (R1: il
 * ridisegno resta lì, e non fa riregistrare il pannello). Con [advancing] vero ridisegna a ogni fotogramma; fermo, non
 * disegna, salvo che [elapsedMillis] legga uno stato (la posizione di un lettore, nel riascolto in pausa): allora si
 * ridisegna quando lui cambia. Per TalkBack dice la durata ai secondi interi, un aggiornamento al secondo, e la larghezza
 * passa a quella delle ore oltre l'ora: nella registrazione e nel riascolto.
 */
@Composable
fun FluidRecorderClock(
  elapsedMillis: () -> Long,
  advancing: Boolean,
  modifier: Modifier = Modifier,
  style: TextStyle = FluidTextStyles.largeNumeric,
  color: Color = MaterialTheme.colorScheme.onSurface,
  label: String = FluidRecorderStrings().duration,
) {
  val measurer = rememberTextMeasurer(cacheSize = 0)
  val glyphs = remember(style, color, measurer) { ClockGlyphs(measurer, style.copy(color = color)) }
  var frame by remember { mutableIntStateOf(0) }
  var seconds by remember { mutableLongStateOf(elapsedMillis() / 1000L) }
  val latest by rememberUpdatedState(elapsedMillis)
  LaunchedEffect(advancing) {
    seconds = latest() / 1000L
    if (advancing) {
      while (true) {
        withFrameNanos { }
        frame++
        val now = latest() / 1000L
        if (now != seconds) seconds = now
      }
    } else {
      // Fermo, il tempo può essere quello di un lettore (il riascolto in pausa): uno stato, che si segue al secondo
      // (R1). Un orologio che non è uno stato dà un valore solo.
      snapshotFlow { latest() / 1000L }.collect { seconds = it }
    }
  }
  val hours = seconds >= 3_600L
  val width = with(LocalDensity.current) { glyphs.width(hours).toDp() }
  Spacer(
    modifier
      .width(width)
      .height(with(LocalDensity.current) { glyphs.height.toDp() })
      .clearAndSetSemantics {
        contentDescription = "$label ${FluidScrubbing.clock(seconds * 1000L)}"
      }
      .graphicsLayer()
      .drawBehind {
        if (frame < 0) return@drawBehind
        glyphs.draw(this, latest().coerceAtLeast(0L))
      },
  )
}

/** Le cifre del tempo, misurate una volta. */
private class ClockGlyphs(measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle) {
  private val digits: Array<TextLayoutResult> = Array(10) { measurer.measure(('0' + it).toString(), style) }
  private val colon = measurer.measure(":", style)
  private val dot = measurer.measure(".", style)
  private val digit = digits.maxOf { it.size.width }.toFloat()
  val height: Float = digits[0].size.height.toFloat()

  fun width(hours: Boolean): Float =
    if (hours) digit * 7 + colon.size.width * 2 + dot.size.width else digit * 6 + colon.size.width + dot.size.width

  /** Dove scrivere la prossima cifra: un campo e non una variabile catturata, che allocherebbe a ogni disegno. */
  private var cursor = 0f

  fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, millis: Long) {
    val centis = (millis / 10L) % 100L
    val totalSeconds = millis / 1000L
    val s = totalSeconds % 60L
    val m = (totalSeconds / 60L) % 60L
    val h = totalSeconds / 3_600L
    cursor = 0f
    if (h > 0L) {
      put(scope, digits[(h % 10L).toInt()], digit)
      put(scope, colon, colon.size.width.toFloat())
    }
    two(scope, m)
    put(scope, colon, colon.size.width.toFloat())
    two(scope, s)
    put(scope, dot, dot.size.width.toFloat())
    two(scope, centis)
  }

  /** Ogni cifra al centro della sua cella: le tabulari hanno gia' la stessa larghezza, questo tiene anche un font che no. */
  private fun put(scope: androidx.compose.ui.graphics.drawscope.DrawScope, layout: TextLayoutResult, advance: Float) {
    scope.drawText(layout, topLeft = Offset(cursor + (advance - layout.size.width) / 2f, 0f))
    cursor += advance
  }

  private fun two(scope: androidx.compose.ui.graphics.drawscope.DrawScope, value: Long) {
    put(scope, digits[((value / 10L) % 10L).toInt()], digit)
    put(scope, digits[(value % 10L).toInt()], digit)
  }
}

/**
 * Il **tasto rosso** di Memo Vocali: un disco di vetro tinto di rosso (`systemRed`) da 52 dp, che galleggia sopra
 * l'elenco. Il vetro tinto e' quello di serie dell'engine (la dose C di F6B.16). Il tocco suona `Tap`; TalkBack dice
 * «Registra».
 */
@Composable
fun FluidRecordButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  enabled: Boolean = true,
  label: String = FluidRecorderStrings().record,
) {
  FluidGlassIconButton(
    onClick = onClick,
    size = FluidRecorderDefaults.RecordButtonSize,
    modifier = modifier.semantics { contentDescription = label },
    backdrop = backdrop,
    enabled = enabled,
    style = FluidGlassButtonStyle.Prominent(recordingRed()),
  ) {}
}

/**
 * Il **pannello della registrazione**, il contenuto del foglio di Memo Vocali: il titolo con l'ora e la durata, l'onda
 * che scorre sotto la testina ([FluidAudioTimeline]: rossa mentre registra, dell'accento in pausa), il tempo grande
 * ([FluidRecorderClock]), i tasti dell'ascolto (meno e piu' 15 secondi e Play, spenti mentre registra, come in Memo
 * Vocali) e in fondo la **capsula della pausa**: due barre rosse mentre registra, «Riprendi» in rosso in pausa. In alto a
 * destra «Fine» (la spunta, vetro tinto dell'accento).
 *
 * Non disegna il vetro: sta dentro un pannello o un foglio. [FluidVoiceRecorder] lo mette nel vetro che nasce dal tasto.
 *
 * In pausa, se c'e' [playback] (un lettore di quel che si e' registrato finora), l'onda si scorre col dito e i tasti
 * dell'ascolto funzionano: Memo Vocali lascia riascoltare prima di riprendere. Riprendere registra in coda.
 *
 * @param elapsedMillis il tempo registrato, un orologio: letto a ogni fotogramma mentre registra.
 * @param clipping vero quando il suono satura: compare la riga che lo dice, e TalkBack la legge una volta.
 * @param interrupted vero quando la pausa l'ha decisa il telefono (una chiamata, un'altra app col microfono).
 */
@Composable
fun FluidRecorderPanel(
  phase: FluidRecorderPhase,
  envelope: FluidAudioEnvelope,
  elapsedMillis: () -> Long,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  title: String = FluidRecorderStrings().newRecording,
  subtitle: String? = null,
  playback: FluidMediaSession? = null,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  clipping: Boolean = false,
  interrupted: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
  bottomStart: (@Composable () -> Unit)? = null,
  bottomEnd: (@Composable () -> Unit)? = null,
  strings: FluidRecorderStrings = FluidRecorderStrings(),
) {
  val scheme = MaterialTheme.colorScheme
  val red = recordingRed()
  val recording = phase == FluidRecorderPhase.Recording
  val listening = !recording && playback != null
  Column(
    modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // La riga in alto: il tasto dell'app a sinistra, «Fine» a destra.
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { leading?.invoke() }
      FluidGlassIconButton(
        onClick = onDone,
        size = 44.dp,
        backdrop = backdrop,
        style = FluidGlassButtonStyle.Prominent,
        modifier = Modifier.semantics { contentDescription = strings.done },
      ) {
        FluidIcon(FluidIcons.Check, null)
      }
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = scheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
    if (subtitle != null) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    Spacer(Modifier.height(16.dp))
    val timeline = rememberFluidAudioTimelineState()
    FluidAudioTimeline(
      envelope = envelope,
      position = if (listening) ({ playback!!.positionMillis }) else elapsedMillis,
      onSeek = { playback?.seekTo(it) },
      durationMillis = if (listening) ({ playback!!.durationMillis ?: envelope.durationMillis }) else elapsedMillis,
      state = timeline,
      recording = recording,
      advancing = recording,
      enabled = listening,
      modifier = Modifier.height(FluidRecorderDefaults.TimelineHeight),
    )
    if (clipping || interrupted) {
      Text(
        text = if (interrupted) strings.interrupted else strings.clipping,
        style = MaterialTheme.typography.bodySmall,
        color = if (interrupted) scheme.onSurfaceVariant else red,
        modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
    Spacer(Modifier.height(18.dp))
    FluidRecorderClock(
      elapsedMillis = if (listening) ({ playback!!.positionMillis }) else elapsedMillis,
      advancing = recording,
      label = strings.duration,
    )
    // I tasti dell'ascolto: spenti mentre registra, come in Memo Vocali (grigi, non nascosti).
    Row(
      Modifier.padding(top = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(28.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val transport = listening && playback?.canSeek == true
      SkipBy(strings.skipBack, back = true, enabled = transport) { playback?.skipBy(-FluidRecorderDefaults.SkipMillis) }
      val playing = playback?.playing == true
      Box(
        Modifier
          .size(48.dp)
          .fluidPressable(
            onClick = { if (playing) playback?.pause() else playback?.play() },
            enabled = listening,
            role = androidx.compose.ui.semantics.Role.Button,
          )
          .semantics { contentDescription = if (playing) strings.pausePlayback else strings.play },
        contentAlignment = Alignment.Center,
      ) {
        FluidIcon(
          if (playing) FluidIcons.Filled.Pause else FluidIcons.Filled.PlayArrow,
          null,
          size = 40.dp,
          tint = if (listening) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
        )
      }
      SkipBy(strings.skipForward, back = false, enabled = transport) { playback?.skipBy(FluidRecorderDefaults.SkipMillis) }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { bottomStart?.invoke() }
      PauseCapsule(recording = recording, onPause = onPause, onResume = onResume, backdrop = backdrop, strings = strings, red = red)
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { bottomEnd?.invoke() }
    }
  }
}

/** La capsula di vetro della pausa: due barre rosse, o «Riprendi» in rosso. Cede sotto il dito come ogni tasto. */
@Composable
private fun PauseCapsule(
  recording: Boolean,
  onPause: () -> Unit,
  onResume: () -> Unit,
  backdrop: GlassBackdropState,
  strings: FluidRecorderStrings,
  red: Color,
) {
  Box(
    Modifier
      .widthIn(min = FluidRecorderDefaults.PauseCapsuleWidth)
      .height(FluidRecorderDefaults.PauseCapsuleHeight)
      .fluidGlass(FluidGlass.Regular, FluidCapsuleShape, backdrop)
      .fluidPressable(
        onClick = if (recording) onPause else onResume,
        role = androidx.compose.ui.semantics.Role.Button,
      )
      .clearAndSetSemantics {
        contentDescription = if (recording) strings.pause else strings.resume
        role = androidx.compose.ui.semantics.Role.Button
        onClick { if (recording) onPause() else onResume(); true }
      }
      .padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (recording) {
      Spacer(
        Modifier
          .size(FluidRecorderDefaults.PauseBarWidth * 2 + FluidRecorderDefaults.PauseBarGap, FluidRecorderDefaults.PauseBarHeight)
          .drawBehind {
            val bar = FluidRecorderDefaults.PauseBarWidth.toPx()
            val corner = CornerRadius(bar * 0.22f)
            drawRoundRect(red, Offset.Zero, Size(bar, size.height), corner)
            drawRoundRect(red, Offset(size.width - bar, 0f), Size(bar, size.height), corner)
          },
      )
    } else {
      Text(strings.resume, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = red)
    }
  }
}

@Composable
private fun SkipBy(label: String, back: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val ink = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
  Box(
    Modifier
      .size(48.dp)
      .fluidPressable(onClick = onClick, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    FluidIcon(FluidIcons.Replay, null, Modifier.scale(if (back) 1f else -1f, 1f), size = 32.dp, tint = ink)
    Text(
      "15",
      Modifier.padding(top = 4.dp).clearAndSetSemantics {},
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = ink,
    )
  }
}

/** Un salto relativo, dentro la durata. */
private fun FluidMediaSession.skipBy(delta: Long) {
  val end = durationMillis?.takeIf { it > 0L } ?: return
  if (!canSeek) return
  seekTo((positionMillis + delta).coerceIn(0L, end))
}

/**
 * Il **registratore** di Memo Vocali in un pezzo solo: a riposo il tasto rosso ([FluidRecordButton]); registrando, il
 * suo vetro diventa il pannello ([FluidRecorderPanel]) e a registrazione finita ci torna. Il cambio lo anima il piano
 * flottante (`FluidGlassContainer`, Fluid-physics): un vetro solo con la stessa identita', che cambia forma; il
 * contenuto nuovo entra sfocato, perche' cambia identita' (regola di F8: lo muove l'app).
 *
 * Va in fondo alla pagina, sopra l'elenco delle registrazioni (`Alignment.BottomCenter` in un `Box`). Lo stato e'
 * dell'app (di serie quello di `FluidAudioRecorder` di `engine-audio`): [phase] dice cosa mostrare, [onRecord] parte.
 */
@Composable
fun FluidVoiceRecorder(
  phase: FluidRecorderPhase,
  onRecord: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onDone: () -> Unit,
  envelope: FluidAudioEnvelope,
  elapsedMillis: () -> Long,
  modifier: Modifier = Modifier,
  title: String = FluidRecorderStrings().newRecording,
  subtitle: String? = null,
  playback: FluidMediaSession? = null,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  clipping: Boolean = false,
  interrupted: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
  bottomStart: (@Composable () -> Unit)? = null,
  bottomEnd: (@Composable () -> Unit)? = null,
  strings: FluidRecorderStrings = FluidRecorderStrings(),
) {
  val namespace = rememberFluidGlassNamespace()
  FluidGlassContainer(modifier = modifier) {
    if (phase == FluidRecorderPhase.Idle) {
      Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        FluidRecordButton(
          onClick = onRecord,
          backdrop = backdrop,
          label = strings.record,
          modifier = Modifier.fluidGlassId(RecorderGlassId, namespace),
        )
      }
    } else {
      Box(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .fluidGlassId(RecorderGlassId, namespace)
          .fluidGlassPanel(
            backdrop,
            GlassDefaults.modalTint(),
            ContinuousCornerShape(FluidRecorderDefaults.PanelRadius),
            GlassRole.Modal,
          ),
      ) {
        FluidGlassNested {
          FluidRecorderPanel(
            phase = phase,
            envelope = envelope,
            elapsedMillis = elapsedMillis,
            onPause = onPause,
            onResume = onResume,
            onDone = onDone,
            title = title,
            subtitle = subtitle,
            playback = playback,
            backdrop = backdrop,
            clipping = clipping,
            interrupted = interrupted,
            leading = leading,
            bottomStart = bottomStart,
            bottomEnd = bottomEnd,
            strings = strings,
          )
        }
      }
    }
  }
}

/** L'identita' del vetro che passa dal tasto al pannello. */
private const val RecorderGlassId = "fluid-recorder"

// ----------------------------------------------------------------------------------------------------------------------
// La qualità (F8.13b)
// ----------------------------------------------------------------------------------------------------------------------

/**
 * I testi della riga della qualità, in italiano di serie: il titolo, il nome di ogni preimpostazione e la riga sotto
 * («AAC 96 kbit/s · circa 44 MB all'ora»). Un'app tradotta li passa tutti.
 */
@Immutable
data class FluidRecordingQualityStrings(
  val title: String = "Qualità di registrazione",
  val voice: String = "Voce per l'IA",
  val standard: String = "Standard",
  val high: String = "Alta",
  val lossless: String = "Senza perdita",
  /** La riga sotto una preimpostazione: il formato e lo spazio di un'ora già scritto («43,6 MB»). */
  val detail: (quality: FluidRecordingQuality, perHour: String) -> String = { quality, perHour ->
    val format = if (quality.lossless) "WAV ${quality.sampleRate / 1000} kHz" else "AAC ${quality.bitRate / 1000} kbit/s"
    "$format · circa $perHour all'ora"
  },
) {
  /** Il nome di [quality]; una preimpostazione che questi testi non conoscono si chiama col suo id. */
  fun label(quality: FluidRecordingQuality): String = when (quality) {
    FluidRecordingQuality.Voice -> voice
    FluidRecordingQuality.Standard -> standard
    FluidRecordingQuality.High -> high
    FluidRecordingQuality.Lossless -> lossless
    else -> quality.id
  }
}

/**
 * La **riga della qualità di registrazione** (F8.13b), pronta per la pagina delle impostazioni di un'app: il titolo, la
 * qualità di adesso a destra, e al tocco la riga diventa il pop-up di vetro con le preimpostazioni ([FluidPickerRow]),
 * ognuna col suo formato e con **lo spazio che occupa un'ora** («AAC 96 kbit/s · circa 43,6 MB all'ora»). Sotto il
 * titolo, la stessa riga per la qualità scelta.
 *
 * Va in un gruppo di vetro, come le altre righe delle impostazioni:
 *
 * ```kotlin
 * FluidListGroup(glass = true) {
 *   FluidRecordingQualityRow(selected = qualita, onSelected = { qualita = it })   // poi FluidAudioRecorder.start(file, qualita)
 * }
 * ```
 *
 * Di serie le quattro di [FluidRecordingQuality.All]; un'app che non vuole offrire il WAV passa le sue ([qualities]).
 * Serve un `FluidGlassModalHost` alla radice, come per ogni pop-up.
 */
@Composable
fun FluidRecordingQualityRow(
  selected: FluidRecordingQuality,
  onSelected: (FluidRecordingQuality) -> Unit,
  modifier: Modifier = Modifier,
  qualities: List<FluidRecordingQuality> = FluidRecordingQuality.All,
  enabled: Boolean = true,
  strings: FluidRecordingQualityStrings = FluidRecordingQualityStrings(),
) {
  val options = remember(qualities, strings) {
    qualities.map { quality ->
      FluidPickerOption(key = quality.id, label = strings.label(quality), subtitle = fluidRecordingQualityDetail(quality, strings))
    }
  }
  FluidPickerRow(
    title = strings.title,
    selectedKey = selected.id,
    options = options,
    onSelected = { id -> qualities.firstOrNull { it.id == id }?.let(onSelected) },
    modifier = modifier,
    subtitle = fluidRecordingQualityDetail(selected, strings),
    enabled = enabled,
  )
}

/** La riga sotto una preimpostazione, con lo spazio di un'ora nella lingua del telefono. */
internal fun fluidRecordingQualityDetail(quality: FluidRecordingQuality, strings: FluidRecordingQualityStrings): String =
  strings.detail(quality, FluidFormat.bytes(quality.bytesPerHour))
