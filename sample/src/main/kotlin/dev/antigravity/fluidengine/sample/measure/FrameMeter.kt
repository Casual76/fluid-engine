package dev.antigravity.fluidengine.sample.measure

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Trace
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/** Il tag fisso di ogni riga: `adb logcat -s FluidMisure`. */
internal const val MeasureLogTag = "FluidMisure"

/**
 * Dove finiscono i numeri: in logcat, sempre, e sullo schermo (le ultime righe).
 *
 * Le righe arrivano solo ai confini delle finestre, mai dentro: un testo che cambia a ogni fotogramma
 * sarebbe esso stesso un costo nella misura che sta mostrando.
 */
@Stable
internal class MeasureReport {
  val lines = mutableStateListOf<String>()

  /** Una riga del risultato. Va chiamata dal filo principale. */
  fun line(text: String) {
    Log.i(MeasureLogTag, text)
    lines += text
    while (lines.size > MaxHudLines) lines.removeAt(0)
  }

  private companion object {
    const val MaxHudLines = 12
  }
}

/**
 * Il tempo della scena, un valore per fotogramma.
 *
 * Tutto ciò che si muove in una scena è una funzione di [seconds], letta in fase di disegno: così due
 * lanci della stessa scena fanno lo stesso moto, e niente si ricompone a ogni fotogramma.
 */
@Stable
internal class MeasureClock {
  /** Secondi dal primo fotogramma della scena. */
  var seconds by mutableFloatStateOf(0f)
    private set

  /** Quanti fotogrammi ha visto la scena. */
  var frame by mutableIntStateOf(0)
    private set

  suspend fun run() {
    val start = withFrameNanos { it }
    while (true) {
      withFrameNanos { now ->
        seconds = (now - start) / 1_000_000_000f
        frame++
      }
    }
  }
}

/** Una finestra chiusa: i suoi fotogrammi e il riassunto dei tempi presi a mano dentro di lei. */
internal class MeasureWindow(val frames: MeasureFrames, val samples: Map<String, MeasureSummary>)

/**
 * I tempi di fotogramma della finestra dell'attività, presi con `Window.addOnFrameMetricsAvailableListener`.
 *
 * Il listener gira su un filo suo: il conto dei fotogrammi non deve pesare sul filo principale che sta
 * misurando. Si raccoglie solo dentro [collect]; fuori, i fotogrammi si buttano.
 */
internal class FrameMeter(private val window: Window) {

  private val thread = HandlerThread("FluidMisure")
  private val lock = Any()
  private var frames: MeasureFrames? = null
  private var samples: HashMap<String, LongBag>? = null
  private var started = false
  private var cookie = 0

  private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropCount ->
    synchronized(lock) {
      val target = frames ?: return@synchronized
      target.dropped += dropCount
      // Il primo fotogramma di una finestra paga il primo layout: non è una misura del vetro.
      if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@synchronized
      val ui = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
        metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
        metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
        metrics.getMetric(FrameMetrics.DRAW_DURATION)
      val render = metrics.getMetric(FrameMetrics.SYNC_DURATION) +
        metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) +
        metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
      val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
      target.add(
        vsyncNanos = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
        totalNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
        uiNanos = ui,
        renderNanos = render,
        gpuNanos = if (modern) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L,
        deadlineNanos = if (modern) metrics.getMetric(FrameMetrics.DEADLINE) else 0L,
      )
    }
  }

  /** La frequenza dello schermo adesso: decide il bilancio dove il sistema non lo dichiara. */
  val refreshHz: Float
    get() = window.decorView.display?.refreshRate?.takeIf { it > 0f } ?: 60f

  fun start() {
    if (started) return
    started = true
    thread.start()
    window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
  }

  fun stop() {
    if (!started) return
    started = false
    try {
      window.removeOnFrameMetricsAvailableListener(listener)
    } catch (_: IllegalArgumentException) {
      // La finestra è già stata smontata e il listener con lei: non c'è niente da togliere.
    }
    thread.quitSafely()
  }

  /**
   * Raccoglie i fotogrammi consegnati mentre [block] gira, dentro una sezione di traccia col nome
   * [label] (si ritrova in Perfetto fra le sezioni asincrone dell'app).
   *
   * @param capacity quanti fotogrammi al massimo: oltre, la finestra si dichiara traboccata.
   */
  suspend fun collect(label: String, capacity: Int, block: suspend () -> Unit): MeasureWindow {
    val target = MeasureFrames(capacity)
    val bag = HashMap<String, LongBag>()
    val section = label.take(MaxSectionName)
    val id = synchronized(lock) {
      frames = target
      samples = bag
      ++cookie
    }
    val traced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    if (traced) Trace.beginAsyncSection(section, id)
    try {
      block()
    } finally {
      if (traced) Trace.endAsyncSection(section, id)
      synchronized(lock) {
        frames = null
        samples = null
      }
    }
    val summaries = synchronized(lock) { bag.mapValues { it.value.summary() } }
    return MeasureWindow(target, summaries)
  }

  /** Un tempo preso a mano (una lettura, una compilazione) dentro la finestra aperta. Da qualunque filo. */
  fun sample(name: String, nanos: Long) {
    synchronized(lock) {
      val bag = samples ?: return
      bag.getOrPut(name) { LongBag() }.add(nanos)
    }
  }

  private companion object {
    /** Il limite di `Trace` per il nome di una sezione. */
    const val MaxSectionName = 127
  }
}

/** Una lista di `Long` che cresce, senza un oggetto per valore. */
private class LongBag {
  private var values = LongArray(256)
  private var count = 0

  fun add(value: Long) {
    if (count == values.size) values = values.copyOf(count * 2)
    values[count++] = value
  }

  fun summary(): MeasureSummary = MeasureSummary.of(values, count)
}

/** Esegue [block] dentro una sezione di traccia sincrona: si vede in Perfetto sul filo che la chiama. */
internal inline fun <T> measureSection(name: String, block: () -> T): T {
  Trace.beginSection(name)
  try {
    return block()
  } finally {
    Trace.endSection()
  }
}

/**
 * Come [measureSection], per un lavoro che sospende: una sezione sincrona resterebbe aperta sul filo
 * principale mentre lui fa altro, e in Perfetto tutto quel resto le finirebbe dentro.
 */
internal suspend fun <T> measureAsyncSection(name: String, cookie: Int, block: suspend () -> T): T {
  val traced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  if (traced) Trace.beginAsyncSection(name, cookie)
  try {
    return block()
  } finally {
    if (traced) Trace.endAsyncSection(name, cookie)
  }
}

/** Quanti fotogrammi può tenere una finestra di [seconds]: fino a 240 Hz, con margine. */
internal fun measureCapacity(seconds: Float): Int = (seconds * 250f).toInt() + 512

/**
 * La misura di serie: un'attesa, poi [MeasureConfig.runs] finestre di [MeasureConfig.seconds] l'una, una
 * riga per finestra, la dispersione fra le finestre e la riga `FINE`, che chi aspetta da riga di
 * comando usa per sapere che può leggere.
 *
 * @param extra ciò che la scena aggiunge alla riga di una finestra (i tempi presi a mano).
 */
internal suspend fun runMeasureWindows(
  config: MeasureConfig,
  meter: FrameMeter?,
  report: MeasureReport,
  extra: (MeasureWindow) -> String = { "" },
) {
  report.line(
    "AVVIO ${config.key} | warmup_s=${config.warmupSeconds} window_s=${config.seconds} runs=${config.runs}",
  )
  if (meter == null) {
    report.line("FINE ${config.key} | senza finestra dell'attivita': nessun tempo di fotogramma")
    return
  }
  delay((config.warmupSeconds * 1000f).toLong())
  val windows = ArrayList<MeasureFrames>(config.runs)
  for (run in 1..config.runs) {
    val window = meter.collect("Misure ${config.scene.key} $run/${config.runs}", measureCapacity(config.seconds)) {
      delay((config.seconds * 1000f).toLong())
    }
    windows += window.frames
    report.line(measureWindowLine(config.key, run, config.runs, window.frames, meter.refreshHz, extra(window)))
  }
  report.line(measureSpreadLine(config.key, windows))
  report.line("FINE ${config.key}")
}
