package dev.antigravity.fluidengine.ui.glass.backdrop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Collections
import java.util.WeakHashMap

/**
 * F8.10, diagnostica: la **fotografia a riposo** dello stato dei vetri vivi, a richiesta, senza toccare i tempi dei
 * fotogrammi. Il log `FluidGlassFrame` scrive a ogni fotogramma e rallenta quanto basta a far sparire un difetto di tempi
 * (la «x» sfocata del foglio di condivisione: 2 aperture su 10 a log spento, nessuna su 10 col log acceso); questa invece
 * non scrive niente finché non la si chiede:
 *
 * ```
 * adb shell am broadcast -a dev.antigravity.fluidengine.DUMP_GLASS_STATE -p <pacchetto>
 * adb logcat -d -s FluidGlassState
 * ```
 *
 * Solo in un'app **debuggable** sul dispositivo: in release (e in Robolectric) [active] resta falso, nessun ricevitore si
 * registra, nessuna sonda si iscrive e le catene dei modificatori sono quelle di sempre. Si accende al primo vetro
 * attaccato ([install], da `DrawBackdropNode`). Tutto sul thread principale.
 */
internal object GlassStateDump {
  /** L'azione del broadcast che chiede la fotografia. */
  const val Action: String = "dev.antigravity.fluidengine.DUMP_GLASS_STATE"

  private const val Tag = "FluidGlassState"

  /** Vero in un'app debuggable sul dispositivo, dal primo vetro attaccato. Letto in composizione e in disegno. */
  var active: Boolean = false
    private set

  private var installed = false
  private var dumps = 0

  /** Le sonde vive. Deboli: una sonda se ne va con chi la possiede, anche senza [unregister]. */
  private val probes: MutableSet<Probe> = Collections.newSetFromMap(WeakHashMap())

  /** Chi sa descrivere il proprio stato in una riga. [now] è `System.currentTimeMillis()` della fotografia. */
  fun interface Probe {
    fun describe(now: Long): String
  }

  /** Accende la fotografia se l'app è debuggable e non gira in Robolectric. Una volta per processo. */
  fun install(context: Context) {
    if (installed) return
    installed = true
    val app = context.applicationContext ?: context
    val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable || Build.FINGERPRINT == "robolectric") return
    active = true
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) = dump()
    }
    // Esportato: il broadcast arriva dalla shell di adb, un altro utente. Solo nelle app debuggable.
    ContextCompat.registerReceiver(app, receiver, IntentFilter(Action), ContextCompat.RECEIVER_EXPORTED)
  }

  fun register(probe: Probe) {
    if (active) probes.add(probe)
  }

  fun unregister(probe: Probe) {
    if (active) probes.remove(probe)
  }

  /** Scrive la fotografia: una riga per sonda, fra un'intestazione e una chiusura con lo stesso numero. */
  fun dump() {
    dumps++
    val now = System.currentTimeMillis()
    val list = probes.toList()
    Log.i(Tag, "dump=$dumps sonde=${list.size}")
    list.forEachIndexed { index, probe ->
      val line = runCatching { probe.describe(now) }.getOrElse { "errore=$it" }
      Log.i(Tag, "dump=$dumps n=$index $line")
    }
    Log.i(Tag, "dump=$dumps fine")
  }
}

/**
 * Una sonda semplice per uno strato o un contenuto: [values] sono gli ultimi valori applicati, [layerAt] quando sono
 * stati scritti, [contentAt] l'ultima volta che il contenuto si è registrato (0 = mai visto). Vive quanto chi la tiene.
 */
internal class GlassLayerProbe(private val kind: String) : GlassStateDump.Probe {
  var values: String = ""
  var layerAt: Long = 0L
  var contentAt: Long = 0L

  fun applied(values: String) {
    this.values = values
    layerAt = System.currentTimeMillis()
    GlassStateDump.register(this)
  }

  fun recorded() {
    contentAt = System.currentTimeMillis()
    GlassStateDump.register(this)
  }

  override fun describe(now: Long): String =
    "kind=$kind id=${System.identityHashCode(this)} $values" +
      " layerAge=${if (layerAt == 0L) "mai" else "${now - layerAt}ms"}" +
      " contentAge=${if (contentAt == 0L) "mai" else "${now - contentAt}ms"}"
}
