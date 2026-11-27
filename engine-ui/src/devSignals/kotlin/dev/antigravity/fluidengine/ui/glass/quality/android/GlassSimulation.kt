package dev.antigravity.fluidengine.ui.glass.quality.android

import android.os.SystemClock
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuClass
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuTable
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal

/**
 * F7.26 · **Un dispositivo simulato**, solo nelle build di debug e bench: memoria, GPU e calore di un telefono che non
 * abbiamo, sopra il tablet vero. Arriva con l'extra `fluid.glass.device` della prima Activity
 * (`adb shell am start … --es fluid.glass.device a52s,load=160@20-50,thermal=severe@70-90`), niente proprietà di sistema.
 *
 * La memoria e la GPU sostituiscono la lettura vera dalla prima Activity in poi (l'heap mai sopra quello vero: ART non
 * dà di più). Il calore, il carico della GPU e i ritardi finti valgono a tempo, in secondi dal momento in cui il profilo
 * si applica. Il carico della GPU è lavoro vero ([GlassGpuLoad]): uno shader invisibile sopra la finestra, che i
 * fotogrammi pagano davvero, così la politica scende per i ritardi misurati e non per un numero inventato.
 *
 * La grammatica, a voci separate da virgole: un nome di [presets] (facoltativo, per primo), poi `ram=<GiB>`,
 * `heap=<MiB>`, `lowram=0|1`, `soc=<SOC_MODEL>`, `gpu=low|mid|high|unknown`, `mpc=<classe>`,
 * `thermal=none|light|moderate|severe[@da-a]`, `load=<iterazioni>[@da-a]`, `late[@da-a]`, `fonte=piatta|texture`, `pieno[@da-a]` (F7.26g: il credito del vetro tutto occupato in quella
 * fase, per vedere il gradino più basso e il suo ritorno), `voti=ogni|finestre` (F7.26d: la riga
 * dei voti per ogni fotogramma, o solo ogni 120 e alle pose, che è il solito). `@20-` vale dal secondo 20 in
 * poi, `@-30` fino al 30. Le voci a tempo si possono ripetere.
 */
internal class GlassSimulation(
  val label: String,
  val totalRamBytes: Long? = null,
  val heapMiB: Int? = null,
  val lowRam: Boolean? = null,
  val gpu: GlassGpuClass? = null,
  val socName: String? = null,
  val mediaClass: Int? = null,
  val thermal: List<Phase<GlassThermal>> = emptyList(),
  val gpuLoad: List<Phase<Int>> = emptyList(),
  val late: List<Phase<Boolean>> = emptyList(),
  private val clock: () -> Long = SystemClock::uptimeMillis,
  /** `fonte=piatta`: la sorgente a schermo intero senza texture fuori schermo ([GlassVariantSignals.flatSource]). */
  val flatSource: Boolean = false,
  /** `voti=ogni`: una riga per fotogramma nella diagnostica dei voti ([GlassFrameVotes]). */
  val everyFrame: Boolean = false,
  /** F7.26g, `pieno@da-a`: le fasi col registro del vetro esaurito ([GlassVariantSignals.exhaustNow]). */
  val exhaust: List<Phase<Boolean>> = emptyList(),
) {
  /** Una fase a tempo, in ms dall'applicazione; [toMs] `Long.MAX_VALUE` vuol dire fino alla fine. */
  class Phase<T>(val value: T, val fromMs: Long, val toMs: Long)

  private val startMs = clock()

  fun elapsedMs() = clock() - startMs

  private fun <T> List<Phase<T>>.now(): T? {
    val t = elapsedMs()
    return lastOrNull { t >= it.fromMs && t < it.toMs }?.value
  }

  fun thermalNow(): GlassThermal? = thermal.now()
  fun gpuLoadNow(): Int = gpuLoad.now() ?: 0
  fun lateNow(): Boolean = late.now() == true
  fun exhaustNow(): Boolean = exhaust.now() == true
  val hasGpuLoad get() = gpuLoad.isNotEmpty()

  /** I confini delle fasi, in ms dall'applicazione: lì si rilegge la condizione e si ridisegna il carico. */
  val boundaries: List<Long>
    get() = (thermal + gpuLoad + late + exhaust).flatMap { listOf(it.fromMs, it.toMs) }
      .filter { it in 1 until Long.MAX_VALUE }.distinct().sorted()

  fun capacity(real: GlassDeviceCapacity): GlassDeviceCapacity {
    val gpuClass = gpu ?: real.gpu
    return real.copy(
      totalRamBytes = totalRamBytes ?: real.totalRamBytes,
      maxHeapBytes = heapMiB?.let { minOf(real.maxHeapBytes.takeIf { m -> m > 0 } ?: Long.MAX_VALUE, it * MiB) }
        ?: real.maxHeapBytes,
      heapClassMiB = heapMiB ?: real.heapClassMiB,
      lowRamDevice = lowRam ?: real.lowRamDevice,
      mediaPerformanceClass = mediaClass ?: real.mediaPerformanceClass,
      recognizedGpuDriver = gpuClass == GlassGpuClass.High,
      gpu = gpuClass,
    )
  }

  /** La RAM disponibile non supera un terzo di quella simulata; il calore della fase attiva, se c'è. */
  fun conditions(real: GlassConditions): GlassConditions = real.copy(
    availableBytes = totalRamBytes?.takeIf { real.availableBytes >= 0 }?.let { minOf(real.availableBytes, it / 3) }
      ?: real.availableBytes,
    thermal = thermalNow() ?: real.thermal,
  )

  override fun toString() = "profilo=$label ram=${totalRamBytes ?: "vera"} heapMiB=${heapMiB ?: "vero"} " +
    "lowRam=${lowRam ?: "vero"} soc=${socName ?: "-"} gpu=${gpu ?: "vera"} mpc=${mediaClass ?: "vera"} " +
    "thermal=${thermal.text()} load=${gpuLoad.text()} late=${late.text()} pieno=${exhaust.text()} fonte=${if (flatSource) "piatta" else "texture"} " +
    "voti=${if (everyFrame) "ogni" else "finestre"}"

  private fun <T> List<Phase<T>>.text() = if (isEmpty()) "-" else joinToString("+") {
    "${it.value}@${it.fromMs / 1000.0}-${if (it.toMs == Long.MAX_VALUE) "" else (it.toMs / 1000.0).toString()}"
  }

  companion object {
    const val Extra = "fluid.glass.device"
    private const val MiB = 1_048_576L
    private const val GiB = 1024 * MiB

    private fun gib(value: Double) = (value * GiB).toLong()

    /**
     * I dispositivi tipo. I numeri della RAM sono il `MemTotal` che Android dà (un po' sotto il taglio di vendita); gli
     * heap sono `dalvik.vm.heapgrowthlimit`. Il Tab S9 è misurato (F7.18); gli altri vengono dalle schede tecniche e dai
     * profili di sistema pubblicati per quei modelli (fiducia media).
     */
    val presets: Map<String, GlassSimulation> get() = mapOf(
      "vero" to GlassSimulation("vero", clock = Still),
      "a52s" to GlassSimulation("a52s", gib(5.5), 256, false, null, "SM7325", 0, clock = Still),
      "a52s8" to GlassSimulation("a52s8", gib(7.3), 256, false, null, "SM7325", 0, clock = Still),
      "s22" to GlassSimulation("s22", gib(7.3), 512, false, null, "s5e9925", 0, clock = Still),
      "tabs9" to GlassSimulation("tabs9", 7_386_728L * 1024, 512, false, null, "SM8550", 0, clock = Still),
      "s25" to GlassSimulation("s25", gib(11.0), 512, false, null, "SM8750", 34, clock = Still),
      "4gb" to GlassSimulation("4gb", gib(3.6), 256, false, null, "s5e8825", 0, clock = Still),
      "go" to GlassSimulation("go", gib(1.8), 128, true, null, "SM4250", 0, clock = Still),
      "ignoto" to GlassSimulation("ignoto", gib(11.0), 512, false, GlassGpuClass.Unknown, "X1000", 0, clock = Still),
    )

    /** L'orologio dei modelli: un dispositivo tipo non parte finché [parse] non lo copia col suo. */
    private val Still: () -> Long = { 0L }

    /** Null se [spec] non si capisce: la build di prova scrive la riga e lascia il dispositivo vero. */
    fun parse(spec: String, clock: () -> Long = SystemClock::uptimeMillis): GlassSimulation? {
      val tokens = spec.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
      if (tokens.isEmpty()) return null
      val base = presets[tokens.first().lowercase()]
      var changed = false
      var ram = base?.totalRamBytes
      var heap = base?.heapMiB
      var lowRam = base?.lowRam
      var gpu = base?.gpu
      var soc = base?.socName
      var mpc = base?.mediaClass
      val thermal = ArrayList<Phase<GlassThermal>>()
      val load = ArrayList<Phase<Int>>()
      val late = ArrayList<Phase<Boolean>>()
      var flat = false
      var every = false
      val exhaust = ArrayList<Phase<Boolean>>()
      for (token in if (base != null) tokens.drop(1) else tokens) {
        val key = token.substringBefore('=').substringBefore('@').trim().lowercase()
        val raw = if ('=' in token) token.substringAfter('=') else ""
        val value = raw.substringBefore('@').trim()
        val window = if ('@' in token) window(token.substringAfter('@')) ?: return null else 0L to Long.MAX_VALUE
        when (key) {
          "ram" -> ram = value.toDoubleOrNull()?.let(::gib) ?: return null
          "heap" -> heap = value.toIntOrNull() ?: return null
          "lowram" -> lowRam = value == "1" || value.equals("true", ignoreCase = true)
          "soc" -> { soc = value; gpu = null }
          "gpu" -> gpu = GlassGpuClass.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: return null
          "mpc" -> mpc = value.toIntOrNull() ?: return null
          "thermal" -> thermal += Phase(thermalOf(value) ?: return null, window.first, window.second)
          "load" -> load += Phase(value.toIntOrNull()?.coerceIn(0, GlassGpuLoad.MaxIterations) ?: return null,
            window.first, window.second)
          "late" -> late += Phase(true, window.first, window.second)
          "pieno" -> exhaust += Phase(true, window.first, window.second)
          "fonte" -> flat = when (value.lowercase()) {
            "piatta" -> true
            "texture" -> false
            else -> return null
          }
          "voti" -> every = when (value.lowercase()) {
            "ogni" -> true
            "finestre" -> false
            else -> return null
          }
          else -> return null
        }
        if (key != "thermal" && key != "load" && key != "late" && key != "fonte" && key != "voti" && key != "pieno") {
          changed = true
        }
      }
      val label = when {
        base == null -> "su misura"
        changed -> base.label + "+"
        else -> base.label
      }
      val gpuClass = gpu ?: soc?.let { GlassGpuTable.classify(null, it) }
      return GlassSimulation(label, ram, heap, lowRam, gpuClass, soc, mpc, thermal, load, late, clock, flat, every, exhaust)
    }

    private fun window(text: String): Pair<Long, Long>? {
      val from = text.substringBefore('-').trim()
      val to = if ('-' in text) text.substringAfter('-').trim() else ""
      val start = if (from.isEmpty()) 0L else ((from.toDoubleOrNull() ?: return null) * 1000).toLong()
      val end = if (to.isEmpty()) Long.MAX_VALUE else ((to.toDoubleOrNull() ?: return null) * 1000).toLong()
      return if (end > start) start to end else null
    }

    private fun thermalOf(value: String) = when (value.lowercase()) {
      "none", "0" -> GlassThermal.None
      "light", "1" -> GlassThermal.Light
      "moderate", "2" -> GlassThermal.Moderate
      "severe", "3" -> GlassThermal.SevereOrWorse
      else -> null
    }
  }
}
