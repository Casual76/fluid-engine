package dev.antigravity.fluidengine.ui.glass.backdrop

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.Looper
import android.os.Trace
import android.util.Log
import android.view.View
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.snapshots.Snapshot
import java.lang.ref.WeakReference

/**
 * Correlazione fra stato, catena installata e disegno sullo stesso orologio di `gfxinfo` (nanosecondi monotoni).
 * Spenta di serie; per le sole prove si abilita `log.tag.FluidGlassFrame=VERBOSE` prima di avviare il processo.
 * `FluidGlassHighRate=VERBOSE` abilita separatamente l'esperimento sul risveglio del pannello, senza cambiare i default.
 */
internal object GlassFrameDiagnostics {
  val enabled: Boolean by lazy { runCatching { Log.isLoggable("FluidGlassFrame", Log.VERBOSE) }.getOrDefault(false) }
  private val highRate: Boolean by lazy { runCatching { Log.isLoggable("FluidGlassHighRate", Log.VERBOSE) }.getOrDefault(false) }
  private val windowRate: Boolean by lazy { runCatching { Log.isLoggable("FluidGlassWindowRate", Log.VERBOSE) }.getOrDefault(false) }
  private var watching = false

  inline fun event(kind: String, owner: Any, crossinline values: @DisallowComposableCalls () -> String = { "" }) {
    // La diagnostica non deve aggiungere dipendenze all'osservatore degli effetti (per esempio leggendo ink).
    if (enabled) Snapshot.withoutReadObservation {
      Log.v("FluidGlassFrame", "t=${System.nanoTime()} kind=$kind id=${System.identityHashCode(owner)} ${values()}")
    }
  }

  inline fun <T> span(kind: String, owner: Any, block: () -> T): T {
    if (!enabled) return block()
    val start = System.nanoTime()
    Trace.beginSection("Glass.$kind")
    try {
      return block()
    } finally {
      Trace.endSection()
      event(kind, owner) { "duration=${System.nanoTime() - start}" }
    }
  }

  /** Solo esperimento: installa la sonda delle scritture dopo la quiete, prima della ricomposizione e della misura. */
  fun composed(view: View, owner: Any) {
    event("compose", owner)
    if ((!highRate && !windowRate) || watching) return
    watching = true
    val weakView = WeakReference(view)
    var lastWrite = System.nanoTime()
    // Solo nella prova: intercetta il primo stato scritto dopo la quiete, prima di aspettare la ricomposizione.
    // La richiesta iniziale nella sola composizione del contenitore non basta: il suo figlio può ricomporsi da solo.
    Snapshot.registerGlobalWriteObserver {
      val now = System.nanoTime()
      val afterRest = now - lastWrite > 500_000_000L
      lastWrite = now
      val current = weakView.get()
      if (afterRest && current != null) {
        if (Looper.myLooper() == Looper.getMainLooper()) requestRate(current)
        else current.post { requestRate(current) }
      }
    }
  }

  private fun requestRate(view: View) {
    event("rateRequest", view) { "window=$windowRate" }
    if (windowRate) {
      var context = view.context
      while (context is ContextWrapper && context !is Activity) context = context.baseContext
      val window = (context as? Activity)?.window ?: return
      val previous = window.attributes.preferredRefreshRate
      val rate = view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: return
      window.attributes = window.attributes.apply { preferredRefreshRate = rate }
      view.postDelayed({
        window.attributes = window.attributes.apply { preferredRefreshRate = previous }
        event("rateRelease", view)
      }, 1_100L)
    } else if (Build.VERSION.SDK_INT >= 35) {
      val previous = view.requestedFrameRate
      view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
      view.postDelayed({
        view.setRequestedFrameRate(previous)
        event("rateRelease", view)
      }, 1_100L)
    }
  }
}
