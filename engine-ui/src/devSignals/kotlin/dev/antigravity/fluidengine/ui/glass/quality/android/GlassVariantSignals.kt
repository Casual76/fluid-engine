package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal

/** Solo nelle varianti debug e bench. I flag si leggono fuori dal percorso per-frame. */
internal object GlassVariantSignals {
  private fun enabled(tag: String) = Log.isLoggable(tag, Log.VERBOSE)

  /** F7.26: il dispositivo simulato dall'extra della prima Activity ([GlassSimulation]), o null. */
  @Volatile var simulation: GlassSimulation? = null
    internal set

  fun capacity(real: GlassDeviceCapacity): GlassDeviceCapacity {
    val base = if (enabled("FluidF7LowRam"))
      real.copy(totalRamBytes = 4L shl 30, heapClassMiB = 192, lowRamDevice = true) else real
    return simulation?.capacity(base) ?: base
  }

  fun conditions(real: GlassConditions): GlassConditions {
    val base = real.copy(
      availableBytes = if (enabled("FluidF7LowRam")) minOf(real.availableBytes, 4L shl 30) else real.availableBytes,
      thermal = when {
        enabled("FluidF7Hot") -> GlassThermal.SevereOrWorse
        enabled("FluidF7Warm") -> maxOf(real.thermal, GlassThermal.Moderate)
        else -> real.thermal
      },
    )
    return simulation?.conditions(base) ?: base
  }

  fun lateFrames() = enabled("FluidF7Late")
  fun logExit(cause: String) {
    if (enabled("FluidF7Exit")) Log.d("FluidF7Exit", cause)
  }

  /**
   * F7.26: la sorgente a schermo intero senza la texture fuori schermo (`fonte=piatta` nel profilo simulato), per la
   * misura sul tablet della domanda 3. Letto nel blocco del layer della sorgente: vale per le sorgenti che nascono dopo.
   */
  val flatSource: Boolean get() = simulation?.flatSource == true

  /**
   * F7.26g: il registro del vetro esaurito a comando (`pieno@da-a` del profilo simulato). Lo legge il registro a ogni
   * lettura, anche ai confini delle fasi, e tiene occupato tutto il credito del vetro finché la fase dura.
   */
  fun exhaustNow(): Boolean = simulation?.exhaustNow() == true

  /** F7.26: le righe dei livelli e della memoria si scrivono solo col profilo simulato acceso. */
  val noting: Boolean get() = simulation != null

  private val sources = java.util.WeakHashMap<Any, String>()

  /**
   * F7.26d: una riga per ogni sorgente a schermo intero (`glassBackdropSource`) quando cambia se è campionata, la sua
   * misura o la fonte. Dice quante texture fuori schermo tiene la scena (la domanda dei 19 MB). Dal filo principale.
   */
  fun noteSource(layer: Any, samples: Boolean, width: Float, height: Float) {
    if (simulation == null) return
    val state = "campionata=$samples ${width.toInt()}x${height.toInt()} fonte=${if (flatSource) "piatta" else "texture"}"
    if (sources.put(layer, state) == state) return
    note { "sorgente ${System.identityHashCode(layer)} $state attive=${sources.values.count { it.startsWith("campionata=true") }}" }
  }

  inline fun note(line: () -> String) {
    val sim = simulation ?: return
    Log.i(SimTag, "t=${sim.elapsedMs()}ms " + line())
  }

  /**
   * F7.26: legge l'extra [GlassSimulation.Extra] della prima Activity che lo porta e accende il profilo. Ai confini delle
   * fasi a tempo rilegge le condizioni ([refresh]) e ridisegna il carico; scrive la memoria ogni mezzo secondo per i primi
   * 20 secondi, poi ogni 5 ([sample]). Vero se il profilo è appena stato acceso.
   */
  fun launch(activity: Activity, refresh: () -> Unit, sample: () -> String): Boolean {
    if (simulation != null) return false
    val spec = try { activity.intent?.getStringExtra(GlassSimulation.Extra) } catch (_: RuntimeException) { null }
      ?: return false
    val parsed = GlassSimulation.parse(spec)
    if (parsed == null) {
      Log.w(SimTag, "profilo non capito, resta il dispositivo vero: $spec")
      return false
    }
    simulation = parsed
    Log.i(SimTag, "t=0ms acceso $parsed")
    val main = Handler(Looper.getMainLooper())
    for (at in parsed.boundaries) main.postDelayed({
      note { "fase calore=${parsed.thermalNow() ?: "vero"} carico=${parsed.gpuLoadNow()} ritardi=${parsed.lateNow()}" }
      refresh()
      GlassGpuLoad.view?.invalidate()
    }, at)
    // Dopo onCreate, onStart e onResume (un messaggio dopo): il decor ha già il contenuto, e un'app che chiede le sue
    // caratteristiche di finestra dopo super.onCreate non trova un decor già fatto.
    if (parsed.hasGpuLoad) main.post { GlassGpuLoad.attach(activity, parsed) }
    val sampler = object : Runnable {
      override fun run() {
        note { sample() }
        main.postDelayed(this, if (parsed.elapsedMs() < 20_000) 500 else 5_000)
      }
    }
    main.post(sampler)
    return true
  }

  const val SimTag = "FluidF7Sim"
}

internal class GlassFrameTimings {
  private val forceLate = GlassVariantSignals.lateFrames()
  fun duration(real: Long, deadline: Long, period: Long, evidence: GlassFrameEvidence): Long =
    if ((forceLate || GlassVariantSignals.simulation?.lateNow() == true) && evidence == GlassFrameEvidence.MeasuredApp)
      deadline + period else real
}
