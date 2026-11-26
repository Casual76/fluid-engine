package dev.antigravity.fluidengine.diagnostics

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import java.util.WeakHashMap

// F8.24 · I fotogrammi lenti per pagina, con JankStats. Il registro serve anche a F7: dice quali telefoni veri scattano,
// e in quale pagina. Le regole di F7 valgono anche qui: il conto di un fotogramma non alloca niente (array fissi, un
// ciclo per indice sugli stati di JankStats), e il segno della pagina si scrive solo quando la pagina entra o esce.

/** Il nome dello stato di JankStats che dice la pagina. */
internal const val FluidPageStateKey: String = "pagina"

/** Il nome della pagina quando l'app non ne ha segnata nessuna. */
internal const val FluidUnnamedPage: String = "app"

private val PageNamePattern = Regex("^[A-Za-z0-9._:/-]{1,48}$")

/**
 * Il nome di pagina che finisce nei rapporti: [name] se è un nome del codice (lettere, cifre, `.`, `_`, `:`, `/`, `-`,
 * fino a 48), «pagina» altrimenti. Così un titolo di nota passato per sbaglio non esce mai dal telefono.
 */
internal fun fluidSafePageName(name: String): String = if (PageNamePattern.matches(name)) name else "pagina"

/**
 * Segna la pagina [name] per i fotogrammi della diagnostica (F8.24): finché questa chiamata è nella composizione, i
 * fotogrammi della finestra contano per [name]. Si mette in cima a ogni pagina (`FluidDiagnosticsPage("impostazioni")`);
 * quando due pagine sono insieme (il passaggio fra due pagine) vale l'ultima entrata.
 *
 * [name] è un nome del codice, non un dato: un nome con spazi o lettere accentate diventa «pagina». Senza
 * `FluidDiagnostics.install` non fa niente.
 */
@Composable
fun FluidDiagnosticsPage(name: String) {
  val view = LocalView.current
  DisposableEffect(view, name) {
    (view.context.findActivity())?.let { activity -> FluidDiagnostics.get()?.frameTracker?.attach(activity) }
    val entry = FluidPageStates.push(view, fluidSafePageName(name))
    onDispose { FluidPageStates.pop(view, entry) }
  }
}

/** Le pagine segnate di ogni finestra, dalla più vecchia: solo sul thread principale (gli effetti di Compose). */
internal object FluidPageStates {
  class Entry(val name: String)

  private val stacks = WeakHashMap<View, ArrayList<Entry>>()

  fun push(view: View, name: String): Entry {
    val root = view.rootView
    val entry = Entry(name)
    stacks.getOrPut(root) { ArrayList() }.add(entry)
    apply(root)
    return entry
  }

  fun pop(view: View, entry: Entry) {
    val root = view.rootView
    stacks[root]?.remove(entry)
    apply(root)
  }

  /** La pagina in cima della finestra di [view], o nulla. */
  fun top(view: View): String? = stacks[view.rootView]?.lastOrNull()?.name

  /** Riscrive la pagina in cima nello stato di JankStats (anche dopo che JankStats è arrivato, a pagina già aperta). */
  fun apply(root: View) {
    val state = PerformanceMetricsState.getHolderForHierarchy(root).state ?: return
    val top = stacks[root]?.lastOrNull()
    if (top != null) state.putState(FluidPageStateKey, top.name) else state.removeState(FluidPageStateKey)
  }
}

/**
 * La somma dei fotogrammi per pagina, fra un invio alla coda e il prossimo: array fissi per al massimo [maxPages]
 * pagine (le altre contano per «altre»), sotto un lucchetto, perché JankStats chiama da un suo filo.
 */
internal class FluidFrameAggregator(private val maxPages: Int = 32) {
  private val lock = Any()
  private val names = arrayOfNulls<String>(maxPages)
  private var used = 0
  private val frames = LongArray(maxPages)
  private val slow = LongArray(maxPages)
  private val hangs = LongArray(maxPages)
  private val frozen = LongArray(maxPages)
  private val hitchNanos = LongArray(maxPages)
  private val durationNanos = LongArray(maxPages)
  private val worstNanos = LongArray(maxPages)
  private val buckets = LongArray(maxPages * FluidFrameBuckets.Count)

  /**
   * Un fotogramma della pagina [page]: [durationNanos] il suo tempo, [overrunNanos] il ritardo oltre la scadenza
   * (negativo se in anticipo), [isJank] il giudizio di JankStats.
   */
  fun onFrame(page: String, durationNanos: Long, overrunNanos: Long, isJank: Boolean) {
    synchronized(lock) {
      val i = slot(page)
      frames[i]++
      if (isJank) slow[i]++
      if (durationNanos >= HangNanos) hangs[i]++
      if (durationNanos >= FrozenNanos) frozen[i]++
      if (overrunNanos > 0) hitchNanos[i] += overrunNanos
      this.durationNanos[i] += durationNanos.coerceAtLeast(0)
      if (durationNanos > worstNanos[i]) worstNanos[i] = durationNanos
      buckets[i * FluidFrameBuckets.Count + FluidFrameBuckets.indexOf(durationNanos)]++
    }
  }

  /** I fotogrammi sommati finora, per pagina, nel giorno [day]; con [reset] si ricomincia da zero. */
  fun snapshot(day: String, reset: Boolean): List<FluidPageFrames> = synchronized(lock) {
    val out = ArrayList<FluidPageFrames>(used)
    for (i in 0 until used) {
      if (frames[i] == 0L) continue
      out += FluidPageFrames(
        page = names[i]!!, day = day, frames = frames[i], slow = slow[i], hangs = hangs[i], frozen = frozen[i],
        hitchMillis = hitchNanos[i] / 1_000_000, durationMillis = durationNanos[i] / 1_000_000,
        worstMillis = worstNanos[i] / 1_000_000,
        buckets = List(FluidFrameBuckets.Count) { b -> buckets[i * FluidFrameBuckets.Count + b] },
      )
    }
    if (reset) {
      used = 0
      names.fill(null)
      frames.fill(0); slow.fill(0); hangs.fill(0); frozen.fill(0)
      hitchNanos.fill(0); durationNanos.fill(0); worstNanos.fill(0); buckets.fill(0)
    }
    out
  }

  private fun slot(page: String): Int {
    for (i in 0 until used) if (names[i] == page) return i
    if (used < maxPages - 1) {
      names[used] = page
      return used++
    }
    // L'ultimo posto è «altre»: una pagina in più non sposta mai le altre.
    val last = maxPages - 1
    if (names[last] == null) {
      names[last] = "altre"
      used = maxPages
    }
    return last
  }

  companion object {
    /** Un blocco: 250 ms senza fotogrammi, come lo conta l'Organizer di Xcode (fiducia media: la soglia dei «hang»). */
    const val HangNanos: Long = 250_000_000

    /** Un fotogramma bloccato di Android vitals: 700 ms (developer.android.com, «Slow rendering»; fiducia alta). */
    const val FrozenNanos: Long = 700_000_000
  }
}

/** L'ascoltatore di JankStats di una finestra: nessuna allocazione per fotogramma. */
internal class FrameListener(
  private val aggregator: FluidFrameAggregator,
  private val expectedNanos: Long,
) : JankStats.OnFrameListener {
  override fun onFrame(volatileFrameData: FrameData) {
    val states = volatileFrameData.states
    var page: String? = null
    for (i in states.indices) {
      val state = states[i]
      if (state.key == FluidPageStateKey) page = state.value
    }
    val duration: Long
    val overrun: Long
    when (volatileFrameData) {
      is FrameDataApi31 -> {
        duration = volatileFrameData.frameDurationCpuNanos
        overrun = volatileFrameData.frameOverrunNanos
      }
      is FrameDataApi24 -> {
        duration = volatileFrameData.frameDurationCpuNanos
        overrun = duration - expectedNanos
      }
      else -> {
        duration = volatileFrameData.frameDurationUiNanos
        overrun = duration - expectedNanos
      }
    }
    aggregator.onFrame(page ?: FluidUnnamedPage, duration, overrun, volatileFrameData.isJank)
  }
}

/**
 * Attacca JankStats a ogni finestra delle attività dell'app mentre sono visibili, e chiama [onBackground] quando
 * l'ultima attività esce dallo schermo (il momento di passare i fotogrammi alla coda).
 */
internal class FluidFrameTracker(
  private val aggregator: FluidFrameAggregator,
  private val onBackground: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
  private val trackers = WeakHashMap<Activity, JankStats>()
  private var started = 0

  override fun onActivityStarted(activity: Activity) {
    started++
    attach(activity)
  }

  /**
   * Attacca JankStats alla finestra di [activity], se non c'è già. La chiama anche [FluidDiagnosticsPage], per
   * un'attività già aperta quando la diagnostica si è accesa (le registrazioni non ripassano le attività di prima).
   */
  fun attach(activity: Activity) {
    val existing = trackers[activity]
    if (existing != null) {
      existing.isTrackingEnabled = true
      return
    }
    val window = activity.window ?: return
    val stats = runCatching { JankStats.createAndTrack(window, FrameListener(aggregator, expectedNanos(activity, window))) }.getOrNull() ?: return
    trackers[activity] = stats
    // Una pagina segnata prima che JankStats ci fosse (la composizione può arrivare prima) entra adesso.
    window.peekDecorView()?.let { FluidPageStates.apply(it.rootView) }
  }

  override fun onActivityStopped(activity: Activity) {
    started = (started - 1).coerceAtLeast(0)
    trackers[activity]?.isTrackingEnabled = false
    if (started == 0) onBackground()
  }

  override fun onActivityDestroyed(activity: Activity) {
    trackers.remove(activity)?.isTrackingEnabled = false
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  private fun expectedNanos(activity: Activity, window: Window): Long {
    val hz = fluidRefreshRate(activity, window)
    return (1_000_000_000.0 / hz).toLong()
  }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is android.content.ContextWrapper -> baseContext.findActivity()
  else -> null
}

/** La frequenza dello schermo della finestra, in Hz (60 se non si sa). */
@Suppress("DEPRECATION")
internal fun fluidRefreshRate(activity: Activity?, window: Window?): Float {
  val display = when {
    Build.VERSION.SDK_INT >= 30 && activity != null -> runCatching { activity.display }.getOrNull()
    else -> window?.windowManager?.defaultDisplay
  }
  return display?.refreshRate?.takeIf { it >= 1f } ?: 60f
}
