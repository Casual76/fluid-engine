package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityReason
import kotlin.math.roundToLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.FrameMetricsBuilder
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F7.26d · Il caso «fotogramma senza tempo della GPU», riprodotto al PC prima della corsa sul tablet.
 *
 * Sul Tab S9 (finestra del 26 novembre) l'A52s simulato non è risalito in 75 s di scorrimento sano e l'S22 col carico
 * della GPU (21,9 % di fotogrammi lenti) non è sceso. Queste prove fanno passare fotogrammi veri di `FrameMetrics` da
 * [WindowFrames], col profilo simulato acceso come sul tablet, e mostrano che cosa vede la politica:
 * - in orario ma senza tempo della GPU (`GPU_DURATION` negativo, come quando il segnale della GPU non è ancora arrivato):
 *   «incompleto», non vota e azzera la risalita, quindi l'A52s resta Protetto; con il tempo della GPU risale;
 * - in ritardo per la GPU, senza tempo della GPU: «incompleto», nessuna discesa; con il tempo: discesa a Protetto;
 * - in ritardo con un tempo della GPU che non copre il ritardo: «non attribuito», nessuna discesa.
 * La riga `voti` della diagnostica dice ogni volta quanti fotogrammi hanno votato e perché gli altri no.
 *
 * F7.26e · La corsa breve sul tablet ha detto che lì il tempo della GPU c'è sempre, e che il buco era un altro: col carico
 * vero la scadenza di Android si allunga (58 ms) e il lavoro della GPU (30 ms) le resta sotto, quindi «non attribuito».
 * Le ultime due prove rigiocano i fotogrammi veri del tablet (`f7-26e/V1-vero.tsv` e `f7-26e/V3-s22-carico.tsv`, dalle righe per fotogramma della bench):
 * V3 col carico deve scendere, V1 sano (il Tab S9 vero) no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, shadows = [RecoveryTestHeap::class])
class GlassFrameVotesTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()
  private val period = 8_333_333L
  private var now = 0L
  private var maker: String? = null
  private var model: String? = null
  private var crashHandler: Thread.UncaughtExceptionHandler? = null

  @Before fun tablet() {
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    GlassVariantSignals.simulation = null
    maker = Build.SOC_MANUFACTURER
    model = Build.SOC_MODEL
    crashHandler = Thread.getDefaultUncaughtExceptionHandler()
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", "QTI")
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", "SM8550")
    val am = shadowOf(app.getSystemService(ActivityManager::class.java)!!)
    am.setIsLowRamDevice(false)
    am.setMemoryClass(512)
    am.setMemoryInfo(ActivityManager.MemoryInfo().apply {
      totalMem = 7_386_728L * 1024
      availMem = 1_466_159_104L
      threshold = 226_492_416L
    })
    shadowOf(app.getSystemService(PowerManager::class.java)!!).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
    ShadowLog.clear()
  }

  @After fun restore() {
    GlassVariantSignals.simulation = null
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", maker)
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", model)
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    Thread.setDefaultUncaughtExceptionHandler(crashHandler)
  }

  private class Scene(val resources: FluidGlassResources, val activity: org.robolectric.android.controller.ActivityController<Activity>,
    val window: Window, val frames: WindowFrames) {
    val optics get() = resources.decision(window).optics
    fun close() { frames.close(); activity.pause().stop().destroy() }
  }

  /** La prima Activity col profilo simulato, visibile, e il listener dei fotogrammi della sua finestra a 120 Hz. */
  private fun scene(profile: String): Scene {
    // Il registro esiste prima della prima Activity (come con l'inizializzatore dell'app): così ne legge l'extra.
    val resources = FluidGlassResources.get(app)
    val intent = Intent(app, Activity::class.java).putExtra(GlassSimulation.Extra, profile)
    val activity = Robolectric.buildActivity(Activity::class.java, intent).setup()
    val decor = activity.get().window.decorView
    val info = ReflectionHelpers.getField<Any>(decor, "mAttachInfo")
    ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
    decor.dispatchWindowVisibilityChanged(View.VISIBLE)
    val window = activity.get().window
    val owner = ReflectionHelpers.getField<Map<Window, GlassWindow>>(resources, "windows").getValue(window)
    now = System.nanoTime()
    val frames = WindowFrames(resources, owner.policy, Handler(Looper.getMainLooper()), 0, period, owner.render) { now }
    return Scene(resources, activity, window, frames)
  }

  /**
   * Un fotogramma: [draw] di CPU, [swap] di attesa dopo i comandi (in Robolectric il totale finisce lì; sul dispositivo è
   * l'attesa della GPU dentro il totale), [gpu] il tempo della GPU, `null` per non darlo.
   */
  private fun Scene.frame(draw: Long, gpu: Long?, swap: Long = 0L) {
    now += period
    val builder = FrameMetricsBuilder()
      .setMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, now)
      .setMetric(FrameMetrics.VSYNC_TIMESTAMP, now)
      .setMetric(FrameMetrics.DEADLINE, period)
      .setMetric(FrameMetrics.DRAW_DURATION, draw)
      .setMetric(FrameMetrics.SWAP_BUFFERS_DURATION, swap)
    if (gpu != null) builder.setMetric(FrameMetrics.GPU_DURATION, gpu)
    frames.onFrameMetricsAvailable(window, builder.build(), 0)
  }

  private fun Scene.motion(count: Int, draw: Long, gpu: Long?, swap: Long = 0L) {
    frames.scrolling(true)
    repeat(count) { frame(draw, gpu, swap) }
    frames.scrolling(false)
  }

  private fun voteLines() = ShadowLog.getLogsForTag(GlassVariantSignals.SimTag).map { it.msg }.filter { it.contains(" voti ") }

  private fun lastPose() = voteLines().last { "posa-" in it }

  // F7.26e: una GPU sconosciuta, perché quella bassa adesso sale a Ridotto alla prima posa anche senza fotogrammi
  // ([dev.antigravity.fluidengine.ui.glass.quality.LowGpuSignalCeiling]); il Pieno di una sconosciuta va ancora guadagnato.
  @Test fun inOrarioSenzaTempoDellaGpuNonSiSale() {
    val a52s = scene("ignoto")
    try {
      assertEquals("GPU sconosciuta: Ridotto all'avvio", GlassOptics.Reduced, a52s.optics)
      // 31 s di scorrimento in orario (2 ms di CPU su 8,3), il tempo della GPU che non c'è.
      a52s.motion(3_720, draw = 2_000_000L, gpu = -1L)
      val blind = lastPose()
      println("F726D | senza GPU | $blind")
      assertEquals("nessuna risalita", GlassOptics.Reduced, a52s.optics)
      val window = voteLines().first { " voti 120 " in it }
      println("F726D | senza GPU, prima finestra | $window")
      assertTrue(window, "votati=0 " in window && "incompleti=120 (gpu<0=120 " in window && "gpu assente=120 " in window)
      assertTrue(blind, "ottica=Reduced" in blind)
      // Lo stesso scorrimento col tempo della GPU (3 ms, dentro il totale come sul dispositivo: il totale finisce quando
      // la GPU ha finito): sale di un gradino alla posa.
      a52s.motion(3_720, draw = 2_000_000L, gpu = 3_000_000L, swap = 3_000_000L)
      val seen = lastPose()
      voteLines().filter { " voti 120 " in it }.takeLast(2).forEach { println("F726D | con GPU, finestra | $it") }
      println("F726D | con GPU | $seen")
      assertEquals(GlassOptics.Full, a52s.optics)
      assertTrue(seen, "ottica=Full" in seen)
      val voted = voteLines().last { " voti 120 " in it }
      assertTrue(voted, "votati=120 " in voted && "app=120 (tardi=0)" in voted)
    } finally {
      a52s.close()
    }
  }

  @Test fun inRitardoPerLaGpuSenzaIlSuoTempoNonSiScende() {
    val s22 = scene("s22")
    try {
      assertEquals("GPU media: Ridotto all'avvio", GlassOptics.Reduced, s22.optics)
      // 1 ms di CPU, poi 25 ms ad aspettare la GPU: tre periodi di ritardo, e la GPU senza tempo.
      s22.motion(240, draw = 1_000_000L, gpu = -1L, swap = 25_000_000L)
      println("F726D | tardi senza GPU | ${lastPose()}")
      assertEquals("oggi: nessuna discesa", GlassOptics.Reduced, s22.optics)
      // La GPU dà un tempo che non copre il ritardo (2 ms): non attribuito, nessuna discesa.
      s22.motion(240, draw = 1_000_000L, gpu = 2_000_000L, swap = 25_000_000L)
      val short = lastPose()
      println("F726D | tardi con GPU corta | $short")
      assertTrue(short, "tardi-lavoro-corto=" in short)
      assertEquals(GlassOptics.Reduced, s22.optics)
      // La GPU dà il suo tempo vero (25 ms): il ritardo è del vetro, si scende.
      s22.motion(240, draw = 1_000_000L, gpu = 25_000_000L, swap = 25_000_000L)
      println("F726D | tardi con GPU | ${lastPose()}")
      assertEquals(GlassOptics.Protected, s22.optics)
    } finally {
      s22.close()
    }
  }

  /** Un fotogramma vero del tablet, in millisecondi, come lo scrive la riga per fotogramma della bench. */
  private class Real(val t: Long, val total: Double, val deadline: Double, val wait: Double, val cpu: Double,
    val sync: Double, val commands: Double, val swap: Double, val gpu: Double)

  private fun real(name: String): List<Real> =
    checkNotNull(javaClass.classLoader!!.getResourceAsStream("f7-26e/$name.tsv")).bufferedReader().readLines()
      .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("t\t") }
      .map { line ->
        val c = line.split('\t').map(String::toDouble)
        Real(c[0].toLong(), c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8])
      }

  private fun ns(ms: Double) = (ms * 1_000_000).roundToLong()

  /**
   * Il fotogramma vero dentro [WindowFrames]: la stessa CPU, i comandi, la GPU e la scadenza; il resto del totale (in
   * Robolectric il totale è la somma delle fasi) va nell'attesa iniziale e nello swap, che non sono lavoro dell'app.
   */
  private fun Scene.replay(frame: Real) {
    now += period
    val cpu = ns(frame.cpu)
    val sync = ns(frame.sync)
    val commands = ns(frame.commands)
    var wait = ns(frame.wait)
    var swap = ns(frame.swap)
    val rest = ns(frame.total) - (wait + cpu + sync + commands + swap)
    if (rest >= 0) swap += rest else wait = maxOf(0L, wait + rest)
    val metrics = FrameMetricsBuilder()
      .setMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, now)
      .setMetric(FrameMetrics.VSYNC_TIMESTAMP, now)
      .setMetric(FrameMetrics.DEADLINE, ns(frame.deadline))
      .setMetric(FrameMetrics.UNKNOWN_DELAY_DURATION, wait)
      .setMetric(FrameMetrics.DRAW_DURATION, cpu)
      .setMetric(FrameMetrics.SYNC_DURATION, sync)
      .setMetric(FrameMetrics.COMMAND_ISSUE_DURATION, commands)
      .setMetric(FrameMetrics.SWAP_BUFFERS_DURATION, swap)
      .setMetric(FrameMetrics.GPU_DURATION, ns(frame.gpu))
      .build()
    frames.onFrameMetricsAvailable(window, metrics, 0)
  }

  @Test fun iFotogrammiVeriColCaricoDellaGpuFannoScendere() {
    val s22 = scene("s22")
    try {
      assertEquals(GlassOptics.Reduced, s22.optics)
      val frames = real("V3-s22-carico")
      var dropAt: Real? = null
      s22.frames.scrolling(true)
      for (frame in frames) {
        s22.replay(frame)
        if (dropAt == null && s22.optics < GlassOptics.Reduced) dropAt = frame
      }
      s22.frames.scrolling(false)
      val reason = s22.policyReason()
      println("F726E | V3 col carico | discesa al fotogramma di t=${dropAt?.t} ms | $reason | ${lastPose()}")
      // La discesa arriva coi fotogrammi del carico (da 12 s a 30 s), non nello scorrimento sano di prima.
      val drop = checkNotNull(dropAt) { "col carico vero della GPU la scala non scende" }
      assertTrue("la discesa a t=${drop.t} ms", drop.t in 12_000L..30_500L)
      assertTrue("$reason", reason == GlassQualityReason.ConsecutiveMisses || reason == GlassQualityReason.TwoBadWindows)
    } finally {
      s22.close()
    }
  }

  @Test fun loScorrimentoSanoDelTabS9VeroNonScende() {
    val tablet = scene("vero")
    try {
      val start = tablet.optics
      var lowest = start
      tablet.frames.scrolling(true)
      repeat(2) { for (frame in real("V1-vero")) { tablet.replay(frame); lowest = minOf(lowest, tablet.optics) } }
      tablet.frames.scrolling(false)
      println("F726E | V1 sano due volte | da $start, il più basso $lowest | ${lastPose()}")
      assertEquals("nessuna discesa nello scorrimento sano", start, lowest)
    } finally {
      tablet.close()
    }
  }

  @Test fun lePartiSaneDiV3NonScendono() {
    val s22 = scene("s22")
    try {
      s22.frames.scrolling(true)
      for (frame in real("V3-s22-carico").filter { it.t < 12_000L || it.t > 30_500L }) s22.replay(frame)
      s22.frames.scrolling(false)
      println("F726E | V3 senza carico | ${s22.optics} | ${lastPose()}")
      assertEquals("nessuna discesa nelle parti sane di V3", GlassOptics.Reduced, s22.optics)
    } finally {
      s22.close()
    }
  }

  private fun Scene.policyReason(): GlassQualityReason? =
    ReflectionHelpers.getField<Map<Window, GlassWindow>>(resources, "windows").getValue(window).policy.lastReason

  @Test fun unaRigaPerFotogrammaConVotiOgni() {
    val tablet = scene("vero,voti=ogni")
    try {
      tablet.motion(3, draw = 2_000_000L, gpu = -1L)
      val lines = ShadowLog.getLogsForTag(GlassVariantSignals.SimTag).map { it.msg }.filter { "fotogramma " in it }
      lines.forEach { println("F726D | $it") }
      assertTrue(lines.toString(), lines.size >= 2)
      assertTrue(lines.toString(), lines.all { "voto=Incomplete perché=gpu<0" in it && "votato=0" in it })
    } finally {
      tablet.close()
    }
  }
}
