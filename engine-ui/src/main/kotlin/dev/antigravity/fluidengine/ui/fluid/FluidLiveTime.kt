package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.FluidFormatWidth
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/*
 * F8.1 · I testi vivi: il tempo relativo che si aggiorna da solo («2 min fa», `FluidRelativeText`) e il conto alla
 * rovescia (`FluidTimerText`, `FluidCountdownBadge`), come `Text(date, style: .relative)` e `Text(timerInterval:)` di
 * SwiftUI. Nessuno ridisegna a ogni fotogramma: ognuno sa quando il suo testo cambia e aspetta fino ad allora, e su una
 * pagina con tanti tempi vivi l'orologio della pagina ([FluidLiveClock]) li sveglia tutti insieme, al più una volta al
 * secondo.
 */

/** L'ora dei testi vivi, in millisecondi dall'epoca. Di serie quella del sistema; le prove ne passano una finta. */
fun interface FluidTimeSource {
  fun nowMillis(): Long

  companion object {
    /** L'orologio del sistema (`System.currentTimeMillis`). */
    val System: FluidTimeSource = FluidTimeSource { java.lang.System.currentTimeMillis() }
  }
}

/** L'ora dei testi vivi sotto questo punto dell'albero. */
val LocalFluidTimeSource = staticCompositionLocalOf { FluidTimeSource.System }

/** L'orologio della pagina, se c'è ([FluidLiveClockProvider]); senza, ogni testo vivo aspetta da solo. */
val LocalFluidLiveClock = staticCompositionLocalOf<FluidLiveClock?> { null }

/**
 * L'orologio unico di una pagina per i testi vivi: **un'attesa sola** per tutti i [FluidRelativeText] e i
 * [FluidTimerText] sotto di lui, invece di un'attesa per riga. Si sveglia al primo cambio che serve, arrotondato al
 * secondo pieno, e in quel giro aggiorna tutti i testi il cui cambio è arrivato: una lista di cento righe col «12 min fa»
 * batte al più una volta al secondo, e solo quando un testo cambia davvero (la scheda dell'idea: «un ticker per riga in
 * una lista lunga costerebbe: deve essere uno solo»).
 *
 * Si mette una volta per pagina con [FluidLiveClockProvider]. Non è uno stato di Compose: i testi si iscrivono e lui
 * incrementa il contatore di chi è arrivato.
 */
@Stable
class FluidLiveClock {
  internal class Waiter {
    var deadline: Long = Long.MAX_VALUE
    val tick = mutableIntStateOf(0)
  }

  private val waiters = ArrayList<Waiter>()
  private val wake = Channel<Unit>(Channel.CONFLATED)

  /** Quante volte l'orologio si è svegliato: per le prove. */
  internal var wakeups = 0
    private set

  internal fun add(waiter: Waiter) {
    waiters += waiter
    wake.trySend(Unit)
  }

  internal fun remove(waiter: Waiter) {
    waiters -= waiter
  }

  internal fun schedule(waiter: Waiter, deadline: Long) {
    if (waiter.deadline == deadline) return
    waiter.deadline = deadline
    wake.trySend(Unit)
  }

  internal suspend fun run(time: FluidTimeSource) {
    while (true) {
      val now = time.nowMillis()
      var next = Long.MAX_VALUE
      for (index in waiters.indices) {
        val waiter = waiters[index]
        if (waiter.deadline <= now) {
          waiter.deadline = Long.MAX_VALUE
          waiter.tick.intValue++
        } else if (waiter.deadline < next) {
          next = waiter.deadline
        }
      }
      if (next == Long.MAX_VALUE) {
        wake.receive()
        continue
      }
      // Al secondo pieno dopo il primo cambio: i cambi dello stesso secondo si fanno in un giro solo.
      val aligned = (next + 999L) / 1000L * 1000L
      withTimeoutOrNull((aligned - now).coerceAtLeast(1L)) { wake.receive() }
      wakeups++
    }
  }
}

/** Un orologio della pagina ricordato. */
@Composable
fun rememberFluidLiveClock(): FluidLiveClock = remember { FluidLiveClock() }

/**
 * Mette l'orologio unico ([FluidLiveClock]) sotto [content]: una volta per pagina, attorno alla lista che ha i tempi vivi.
 * Gira solo finché c'è un testo che aspetta.
 */
@Composable
fun FluidLiveClockProvider(
  clock: FluidLiveClock = rememberFluidLiveClock(),
  content: @Composable () -> Unit,
) {
  val time = LocalFluidTimeSource.current
  // Solo mentre la pagina si vede, come il cronometro (R2): in secondo piano l'orologio non si sveglia; al ritorno i
  // testi il cui cambio e' passato si aggiornano subito.
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(clock, time, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { clock.run(time) } }
  CompositionLocalProvider(LocalFluidLiveClock provides clock, content = content)
}

/**
 * L'ora di adesso per un testo vivo, che **ricompone solo al suo prossimo cambio**: [nextChange] dice, da un'ora, fra
 * quanti millisecondi il testo cambia. Con l'orologio della pagina aspetta con lui; senza, da solo.
 */
@Composable
internal fun rememberLiveNow(key: Any?, nextChange: (Long) -> Long): Long {
  val time = LocalFluidTimeSource.current
  val clock = LocalFluidLiveClock.current
  val waiter = remember(clock) { FluidLiveClock.Waiter() }
  // Letto qui: l'incremento dell'orologio ricompone questo testo, e solo lui.
  waiter.tick.intValue
  val now = time.nowMillis()
  val deadline = now + nextChange(now).coerceAtLeast(1L)
  if (clock != null) {
    DisposableEffect(clock, waiter) {
      clock.add(waiter)
      onDispose { clock.remove(waiter) }
    }
    SideEffect { clock.schedule(waiter, deadline) }
  } else {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(waiter, deadline, key, lifecycle) {
      // Solo mentre la pagina si vede (R2): in secondo piano il testo non si sveglia, e al ritorno si aggiorna.
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        delay((deadline - time.nowMillis()).coerceAtLeast(1L))
        waiter.tick.intValue++
      }
    }
  }
  return now
}

/** La lingua della configurazione, quella che l'app mostra. */
@Composable
internal fun currentLocale(): Locale {
  val locales = LocalConfiguration.current.locales
  return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

/**
 * Un tempo relativo che **si aggiorna da solo**, al passo giusto: «ora», «2 min fa», «tra 3 giorni» (F8.1,
 * `formattatori-e-tempi-vivi`), come `Text(date, style: .relative)` di SwiftUI, con le parole e le soglie di
 * [FluidFormat.relative]. Batte una volta al minuto a «12 min fa», una volta all'ora a «3 h fa», mai a ogni fotogramma; con
 * [FluidLiveClockProvider] sulla pagina tutti i tempi della pagina battono insieme.
 *
 * TalkBack legge la forma intera («2 minuti fa»).
 */
@Composable
fun FluidRelativeText(
  instant: Instant,
  modifier: Modifier = Modifier,
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
  width: FluidFormatWidth = FluidFormatWidth.Short,
  locale: Locale = currentLocale(),
) {
  val now = rememberLiveNow(instant) { at -> FluidFormat.relativeNextChangeMillis(instant, Instant.ofEpochMilli(at)) }
  val at = Instant.ofEpochMilli(now)
  val text = FluidFormat.relative(instant, at, locale, width)
  val spoken = if (width == FluidFormatWidth.Wide) text else FluidFormat.relative(instant, at, locale, FluidFormatWidth.Wide)
  Text(text = text, modifier = modifier.semantics { contentDescription = spoken }, style = style, color = color)
}

/** I contatori delle prove: quante volte un conto alla rovescia si è ricomposto. */
internal object FluidLiveTimeDiagnostics {
  var timerCompositions: Int = 0
}

/** I secondi che mancano, per eccesso: «0:01» fino all'ultimo istante, come `Text(timerInterval:)`. */
internal fun fluidSecondsLeft(endsAtMillis: Long, nowMillis: Long): Long {
  val remaining = endsAtMillis - nowMillis
  return if (remaining <= 0L) 0L else (remaining + 999L) / 1000L
}

/** Fra quanto il secondo mostrato cambia. */
internal fun fluidNextSecondMillis(endsAtMillis: Long, nowMillis: Long): Long {
  val remaining = endsAtMillis - nowMillis
  if (remaining <= 0L) return Long.MAX_VALUE / 4
  val rest = remaining % 1000L
  return if (rest == 0L) 1000L else rest
}

/**
 * Il tempo che manca a [endsAt], da cronometro: «4:59», «1:02:07» (F8.1, `conto-alla-rovescia`), come
 * `Text(timerInterval:)` di SwiftUI. Le cifre **scorrono** come i numeri che scorrono ([FluidNumericText]), verso il
 * basso perché il tempo cala, e sono tabulari: la riga non balla.
 *
 * - **A riposo** ricompone una volta al secondo, quando la cifra cambia, e non ridisegna fra un secondo e l'altro; con
 *   l'app in sottofondo non batte affatto.
 * - **A zero** chiama [onExpire] una volta, e resta a «0:00».
 * - **TalkBack** legge «Mancano 4 minuti e 59 secondi».
 */
@Composable
fun FluidTimerText(
  endsAt: Instant,
  modifier: Modifier = Modifier,
  style: TextStyle = FluidTextStyles.numeric,
  color: Color = Color.Unspecified,
  onExpire: (() -> Unit)? = null,
) {
  val seconds = rememberFluidSecondsLeft(endsAt, onExpire)
  FluidTimerDigits(seconds, modifier, style, color, spoken = true)
}

/** Le cifre del cronometro per [seconds] secondi: il testo e, se [spoken], la frase di TalkBack. */
@Composable
private fun FluidTimerDigits(seconds: Long, modifier: Modifier, style: TextStyle, color: Color, spoken: Boolean) {
  SideEffect { FluidLiveTimeDiagnostics.timerCompositions++ }
  val locale = currentLocale()
  val text = FluidFormat.timer(Duration.ofSeconds(seconds), locale)
  FluidNumericText(
    text = text,
    value = seconds.toDouble(),
    modifier = modifier,
    style = style,
    color = color,
    contentDescription = if (spoken) fluidRemainingWords(seconds, locale, LocalFluidLabels.current.liveText) else text,
  )
}

/** «Mancano 4 minuti e 59 secondi», o «Tempo scaduto». */
internal fun fluidRemainingWords(seconds: Long, locale: Locale, words: FluidLiveTextStrings): String =
  if (seconds == 0L) {
    words.expired
  } else {
    String.format(locale, words.remaining, FluidFormat.duration(Duration.ofSeconds(seconds), locale, FluidFormatWidth.Wide))
  }

/** I secondi che mancano, aggiornati una volta al secondo e solo mentre la pagina si vede. */
@Composable
internal fun rememberFluidSecondsLeft(endsAt: Instant, onExpire: (() -> Unit)?): Long {
  val time = LocalFluidTimeSource.current
  val end = endsAt.toEpochMilli()
  var seconds by remember(end, time) { mutableLongStateOf(fluidSecondsLeft(end, time.nowMillis())) }
  var fired by rememberSaveable(end) { mutableStateOf(false) }
  val expire by rememberUpdatedState(onExpire)
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(end, time, lifecycle) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (true) {
        val now = time.nowMillis()
        seconds = fluidSecondsLeft(end, now)
        if (seconds == 0L) {
          if (!fired) {
            fired = true
            expire?.invoke()
          }
          break
        }
        delay(fluidNextSecondMillis(end, now))
      }
    }
  }
  return seconds
}

/**
 * Una capsula col tempo che scende (F8.1, `conto-alla-rovescia`): un anello che si svuota e il cronometro
 * ([FluidTimerText]), come i timer delle Live Activity e di WidgetKit (`ProgressView(timerInterval:)` accanto a
 * `Text(timerInterval:)`). Per il timer della notte di Sounds, la consegna di un compito, l'«Annulla invio» in linea.
 *
 * La capsula è quieta, nel tono dell'accento: sta nella riga o nella barra, non galleggia (un'app che la mette sopra il
 * contenuto la mette in un suo vetro). L'anello avanza di un passo al secondo con una molla breve e poi sta fermo: a
 * riposo la capsula non ridisegna.
 *
 * @param total la durata intera, per l'anello: null, niente anello.
 */
@Composable
fun FluidCountdownBadge(
  endsAt: Instant,
  modifier: Modifier = Modifier,
  total: Duration? = null,
  tone: Color = MaterialTheme.colorScheme.primary,
  onExpire: (() -> Unit)? = null,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val seconds = rememberFluidSecondsLeft(endsAt, onExpire)
  val spoken = fluidRemainingWords(seconds, currentLocale(), LocalFluidLabels.current.liveText)
  val totalSeconds = total?.seconds?.takeIf { it > 0L }
  val fraction = if (totalSeconds == null) 0f else (seconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
  // Letto solo nel disegno: l'anello che avanza non ricompone.
  val ring = animateFloatAsState(
    targetValue = fraction,
    animationSpec = if (reduced) snap() else spring(
      FluidMotion.DampingChrome,
      FluidMotion.stiffnessForResponse(FluidTextMotion.DigitResponseSeconds),
      0.001f,
    ),
    label = "anello",
  )
  val track = tone.copy(alpha = CountdownTrackAlpha)
  Row(
    modifier = modifier
      .clip(FluidCapsuleShape)
      .background(tone.copy(alpha = CountdownFillAlpha))
      .clearAndSetSemantics { contentDescription = spoken }
      .padding(horizontal = CountdownPaddingH, vertical = CountdownPaddingV),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(CountdownGap),
  ) {
    if (totalSeconds != null) {
      Spacer(
        Modifier
          .size(CountdownRing)
          .drawBehind {
            val stroke = CountdownStroke.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            val sweep = 360f * ring.value
            if (sweep > 0.5f) {
              drawArc(tone, -90f, -sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
          },
      )
    }
    CompositionLocalProvider(LocalContentColor provides tone) {
      FluidTimerDigits(
        seconds = seconds,
        modifier = Modifier,
        style = FluidTextStyles.numeric.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
        color = tone,
        spoken = false,
      )
    }
  }
}

/** L'anello della capsula: 16 dp, il glifo di un'icona piccola; tratto 2,5 dp, il tratto delle icone dell'engine (R6). */
private val CountdownRing = 16.dp
private val CountdownStroke = 2.5.dp
private val CountdownGap = 6.dp
private val CountdownPaddingH = 10.dp
private val CountdownPaddingV = 5.dp

/** Il fondo della capsula e la pista dell'anello, nel tono: 0,14 e 0,24, i pesi di una selezione quieta (a occhio, 23 nov 2026). */
private const val CountdownFillAlpha = 0.14f
private const val CountdownTrackAlpha = 0.24f
