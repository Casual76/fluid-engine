@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.glassFrameMarker
import dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityReason
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.glass.quality.android.GlassSimulation
import dev.antigravity.fluidengine.ui.glass.quality.android.GlassVariantSignals
import dev.antigravity.fluidengine.ui.glass.quality.android.GlassWindow
import dev.antigravity.fluidengine.ui.glass.quality.android.WindowFrames
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.FrameMetricsBuilder
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F7.26 · **Il telefono debole da capo a fondo.** Un Galaxy A52s simulato (6 GB, heap 256 MiB, Snapdragon 778G: il
 * profilo `a52s` dell'extra `fluid.glass.device` della bench) sopra il dispositivo di prova, con un pannello di vetro
 * grande sopra una pagina di testo che scorre, e i fotogrammi che la politica riceve dalle metriche della finestra.
 *
 * Che cosa si guarda, nell'ordine in cui succede sul telefono:
 * 1. **la decisione prima della prima cattura grande**: il registro nasce con la riga dell'A52s (64 MiB, 24 di vetro) e
 *    il vetro Protetto, e la prima cattura del pannello è già alla scala del livello; il picco all'avvio contro il tetto;
 * 2. **la risalita a riposo**: alla prima posa sana dopo i cinque secondi dall'ultimo cambio, un livello (dalla F7.26e la
 *    GPU bassa sale a Ridotto coi segnali, `LowGpuSignalCeiling`; fino a F7.26d servivano trenta secondi di fotogrammi);
 * 3. **l'isteresi**: due ritardi sparsi in una finestra da 120, o un picco solo, non cambiano niente;
 * 4. **la discesa in moto**: tre ritardi di fila scendono di un livello a metà gesto; la cattura del gesto resta quella
 *    (nessuna nuova scala prima della posa), e la scala nuova arriva alla posa;
 * 5. **niente pulsazioni**: dopo una discesa, dieci secondi buoni non bastano per risalire.
 *
 * Il vetro è quello di ripiego (verdetti degli shader rifiutati): il corpo `Liquid` non compila nel Robolectric. Le
 * immagini dei momenti prima, durante e dopo ogni cambio, nella stessa posizione della pagina, finiscono in
 * `sample/build/f7p26-pixels/`, coi numeri dello scarto sotto il pannello.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-mdpi", application = F7p26WeakDevice::class, shadows = [GlassBenchHeap::class])
class F7p26WeakDeviceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null
  private val mib = 1_048_576L

  @Before fun start() {
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
  }

  @After fun stop() {
    ReflectionHelpers.setField(frameLog, "_value", previousFrameLog)
    FluidGlassDiagnostics.resetForTesting()
    GlassVariantSignals.simulation = null
  }

  private fun drawHardwareFrame() {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val display = RenderNode("F7.26")
      display.setPosition(0, 0, view.width, view.height)
      val canvas = display.beginRecording()
      try { view.draw(canvas) } finally {
        display.endRecording()
        display.discardDisplayList()
      }
    }
  }

  private fun settle(ms: Long = 16) {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    Snapshot.sendApplyNotifications()
    drawHardwareFrame()
  }

  private val owner = Regex("""\bowner=(\S+)""")
  private val scale = Regex("""\bscale=([0-9.]+)""")

  private fun logCursor() = ShadowLog.getLogsForTag(FrameTag).size

  /** Le scale delle catture del pannello dal log del vetro, a partire da [from]. */
  private fun captures(from: Int): List<Float> = ShadowLog.getLogsForTag(FrameTag).drop(from)
    .filter { " kind=capture " in it.msg && owner.find(it.msg)?.groupValues?.get(1) == "pannello" }
    .map { scale.find(it.msg)!!.groupValues[1].toFloat() }

  private val nodeId = Regex(""" id=(\d+) """)
  private var panelId: String? = null

  /** Che cosa ha fatto il nodo del pannello da [from]: quanti eventi per tipo, e l'ultima decisione di cattura. */
  private fun panelTrace(label: String, from: Int) {
    val logs = ShadowLog.getLogsForTag(FrameTag)
    if (panelId == null) panelId = logs.firstOrNull { " kind=capture " in it.msg && "owner=pannello" in it.msg }
      ?.let { nodeId.find(it.msg)?.groupValues?.get(1) }
    val mine = logs.drop(from).filter { panelId != null && " id=$panelId " in it.msg }
    val kinds = mine.groupingBy { it.msg.substringAfter("kind=").substringBefore(' ') }.eachCount()
    val decide = mine.lastOrNull { " kind=decide " in it.msg }?.msg?.substringAfter("kind=decide ")
    val capture = mine.lastOrNull { " kind=capture " in it.msg }?.msg?.substringAfter("kind=capture ")
    println("F726TRACE | $label | eventi $kinds | ultima decisione $decide | ultima cattura $capture")
  }

  private fun pixels(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f7p26-pixels").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  /** Lo scarto per canale sotto il pannello (media e massimo, su 255), fuori da un bordo di 8 px. */
  private fun difference(a: Bitmap, b: Bitmap): Pair<Double, Int> {
    var sum = 0L
    var max = 0
    var count = 0L
    for (y in PanelTop + 8 until PanelTop + PanelHeight - 8) for (x in PanelLeft + 8 until PanelLeft + PanelWidth - 8) {
      val p = a.getPixel(x, y)
      val q = b.getPixel(x, y)
      for (shift in intArrayOf(16, 8, 0)) {
        val d = abs((p shr shift and 0xFF) - (q shr shift and 0xFF))
        sum += d
        if (d > max) max = d
        count++
      }
    }
    return sum.toDouble() / count to max
  }

  private fun makeVisible(window: Window) = compose.runOnUiThread {
    val decor = window.decorView
    val info = ReflectionHelpers.getField<Any>(decor, "mAttachInfo")
    ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
    decor.dispatchWindowVisibilityChanged(View.VISIBLE)
  }

  @Test fun ilTelefonoDeboleDaCapoAFondo() {
    val resources = FluidGlassResources.get(compose.activity)
    val budget = resources.budget
    val glass = FluidMemoryCategory.GLASS

    // 1 · Prima di qualunque vetro: la riga e il livello dell'A52s.
    val start = resources.decision(null)
    println("F726E2E | avvio | ${GlassVariantSignals.simulation} | ottica ${start.optics} riga ${start.memoryProfile} " +
      "heap ${start.heapReferenceBytes / mib} MiB | ${resources.usageLine()}")
    assertEquals(GlassOptics.Protected, start.optics)
    assertEquals(GlassMemoryProfile.Reduced, start.memoryProfile)
    assertEquals(256 * mib, start.heapReferenceBytes)
    assertEquals(24 * mib, budget.usage(glass).limitBytes)
    assertEquals(64 * mib, budget.usage().limitBytes)
    assertEquals("niente di grande prima della decisione", 0L, budget.usage().peakBytes)
    assertTrue("pagine dei documenti nella memoria grafica", resources.viewerHardwarePages)

    val scroll = mutableIntStateOf(0)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        // Una sfocatura leggera (3 dp): la scala del ruolo resta vicina a 1, e il tetto del livello conta. Sotto una
        // sfocatura da pannello (il ruolo `Modal`) la scala del ruolo è già 0,475 e i tre livelli catturano uguale.
        val page = rememberGlassBackdrop(blurRadius = 3.dp)
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.White)) {
            Column(Modifier.offset { IntOffset(0, -scroll.intValue) }.padding(top = 20.dp, start = 10.dp)) {
              repeat(70) { row ->
                BasicText(
                  "Riga $row · Scadenza 2026-11-26, vacanze 21/12 – 6/1, compiti di fisica",
                  style = TextStyle(fontSize = 15.sp, color = if (row % 3 == 0) Color(0xFF1565C0) else Color.Black),
                  maxLines = 1,
                )
              }
            }
          }
          Box(Modifier.align(Alignment.TopStart).offset(PanelLeft.dp, PanelTop.dp).glassFrameMarker("pannello")) {
            Box(Modifier.size(PanelWidth.dp, PanelHeight.dp)
              .fluidGlassPanel(page, GlassDefaults.floatingTint(), RoundedCornerShape(28.dp), GlassRole.Floating, large = true))
          }
        }
      }
    }
    compose.waitForIdle()
    repeat(4) { settle() }
    // Due secondi di quiete: la scala locale di F7.2 (la metà per i vetri grandi ridisegnati spesso) torna piena, e resta
    // solo il tetto del livello.
    settle(2_000)
    settle()
    val first = captures(0)
    panelTrace("avvio", 0)
    val startPeak = budget.usage(glass).peakBytes
    println("F726E2E | prime catture $first | picco del vetro all'avvio ${startPeak / 1024} KiB su ${budget.usage(glass).limitBytes / 1024} | " +
      "processo ${budget.usage().peakBytes / 1024} KiB su ${budget.usage().limitBytes / 1024} | rifiuti ${budget.usage().refusals}")
    assertTrue("il pannello ha catturato", first.isNotEmpty())
    assertTrue("nessuna cattura sopra la scala Protetta prima di un cambio: $first", first.all { it <= 0.5f })
    assertTrue(startPeak in 1..budget.usage(glass).limitBytes)
    val atStart = pixels("01-avvio-protetto")

    // La finestra e le sue metriche, con l'orologio della prova.
    val window = compose.activity.window
    makeVisible(window)
    shadowOf(Looper.getMainLooper()).idle()
    val owner = ReflectionHelpers.getField<Map<Window, GlassWindow>>(resources, "windows").getValue(window)
    assertTrue("la finestra è in vista", owner.visible)
    assertEquals(GlassOptics.Protected, owner.policy.decision.optics)
    val period = 8_333_333L
    var now = System.nanoTime()
    val monitor = WindowFrames(resources, owner.policy, Handler(Looper.getMainLooper()), 0, period, owner.render) { now }
    val changes = ArrayList<String>()

    /**
     * Un gesto: [frames] fotogrammi di moto (in ritardo dove [late] lo dice), la pagina che scorre e [draws] disegni
     * veri, poi la pagina torna dov'era e la posa. [onFrame] guarda dopo ogni fotogramma.
     */
    fun gesture(label: String, frames: Int, draws: Int = 8, late: (Int) -> Boolean = { false }, onFrame: (Int) -> Unit = {}) {
      val lease = compose.runOnUiThread<java.io.Closeable> { resources.scrollStarted(window) }
      monitor.scrolling(true)
      val before = owner.policy.decision.optics
      for (i in 0 until frames) {
        now += period
        val slow = late(i)
        val metrics = FrameMetricsBuilder()
          .setMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, now)
          .setMetric(FrameMetrics.VSYNC_TIMESTAMP, now)
          .setMetric(FrameMetrics.DEADLINE, period)
          .setMetric(FrameMetrics.DRAW_DURATION, if (slow) 2 * period else period / 2)
          .build()
        val optics = owner.policy.decision.optics
        monitor.onFrameMetricsAvailable(window, metrics, 0)
        if (owner.policy.decision.optics != optics) {
          changes += "$label in moto f$i ${optics}→${owner.policy.decision.optics} ${owner.policy.lastReason}"
        }
        if (draws > 0 && i % (frames / draws).coerceAtLeast(1) == 0) {
          scroll.intValue = (scroll.intValue + 37) % 300
          settle()
        }
        onFrame(i)
      }
      scroll.intValue = 0
      settle()
      now += period
      monitor.scrolling(false)
      lease.close()
      repeat(3) { settle() }
      // La quiete dopo la posa, come sul telefono: nessun disegno forzato, solo il tempo.
      settle(2_000)
      settle()
      val after = owner.policy.decision.optics
      val posed = changes.lastOrNull()?.startsWith("$label ") != true && after != before
      if (posed) changes += "$label alla posa ${before}→$after ${owner.policy.lastReason}"
      if (after != before) println("F726E2E | $label | $before → $after (${owner.policy.lastReason}) | ${resources.usageLine()}")
    }

    try {
      // 2 · La risalita a riposo: la prima posa sana, un livello. F7.26e: sei secondi di moto bastano (più dei cinque
      // che la politica aspetta dopo l'ultimo cambio, qui il profilo applicato all'avvio), non i trenta di prima.
      var cursor = logCursor()
      gesture("risalita", 720)
      assertEquals(GlassOptics.Reduced, owner.policy.decision.optics)
      assertEquals(GlassQualityReason.SettledRecovery, owner.policy.lastReason)
      assertEquals("la riga resta quella della memoria", GlassMemoryProfile.Reduced, owner.policy.decision.memoryProfile)
      val reducedRest = pixels("02-ridotto-a-riposo")
      val up = difference(atStart, reducedRest)
      val afterRise = captures(cursor)
      panelTrace("risalita", cursor)
      println("F726TRACE | ottica letta dal nodo ${owner.render.optics} | in moto ${owner.render.scrolling}/${owner.render.windowMotion}")
      println("F726E2E | Protetto → Ridotto alla posa | catture $afterRise | scarto sotto il pannello media %.2f max %d".format(up.first, up.second))
      assertTrue("il livello che sale rifà la cattura alla scala del ruolo, senza un disegno forzato: $afterRise",
        afterRise.isNotEmpty() && afterRise.last() > 0.5f)

      // 3 · L'isteresi: due ritardi sparsi ogni 120 fotogrammi (1,7 %) e un picco isolato non cambiano niente.
      now += 6_000_000_000L
      gesture("isteresi", 1_200, late = { it % 120 == 30 || it % 120 == 90 || it == 1_150 })
      assertEquals(GlassOptics.Reduced, owner.policy.decision.optics)

      // 4 · La discesa in moto: tre ritardi di fila a metà gesto. La cattura del gesto resta; la nuova scala alla posa.
      now += 6_000_000_000L
      cursor = logCursor()
      var dropAt = -1
      var dropCursor = -1
      var beforeDrop: Bitmap? = null
      var afterDrop: Bitmap? = null
      var beforeScales = emptyList<Float>()
      var duringScales = emptyList<Float>()
      // Sette disegni (ai fotogrammi 0, 85, 170, 255, 340, …): fra il 299 e il cambio la pagina resta ferma.
      gesture("discesa", 600, draws = 7, late = { it in 300..303 }) { i ->
        if (i == 299) {
          settle()
          beforeDrop = pixels("03-in-moto-prima-del-cambio")
        }
        if (dropAt < 0 && owner.policy.decision.optics == GlassOptics.Protected) {
          dropAt = i
          beforeScales = captures(cursor)
          dropCursor = logCursor()
          settle()
          afterDrop = pixels("04-in-moto-subito-dopo-il-cambio")
        }
        if (i == 599) duringScales = captures(dropCursor)
      }
      assertEquals(GlassOptics.Protected, owner.policy.decision.optics)
      assertEquals(GlassQualityReason.ConsecutiveMisses, owner.policy.lastReason)
      assertTrue("scende al quarto fotogramma della serie, non prima", dropAt in 302..303)
      val afterPose = captures(dropCursor).drop(duringScales.size)
      panelTrace("discesa", cursor)
      val drop = difference(checkNotNull(beforeDrop), checkNotNull(afterDrop))
      println("F726E2E | Ridotto → Protetto a metà gesto (f$dropAt) | catture prima $beforeScales | dopo il cambio, nel gesto " +
        "$duringScales | dopo la posa $afterPose | scarto prima/dopo il cambio, stessa pagina: media %.2f max %d".format(drop.first, drop.second))
      val gestureScale = beforeScales.lastOrNull()
      assertTrue("nel gesto, dopo il cambio, nessuna scala nuova prima della posa: $beforeScales → $duringScales",
        gestureScale == null || duringScales.all { it == gestureScale })
      assertTrue("alla posa la scala del livello Protetto: $afterPose", afterPose.isNotEmpty() && afterPose.last() <= 0.5f)
      val protectedRest = pixels("05-protetto-a-riposo")
      val down = difference(reducedRest, protectedRest)
      println("F726E2E | Ridotto → Protetto alla posa: scarto sotto il pannello media %.2f max %d".format(down.first, down.second))

      // 5 · Niente pulsazioni: dieci secondi buoni non bastano, trenta sì.
      now += 6_000_000_000L
      gesture("dieci-secondi", 1_200)
      assertEquals(GlassOptics.Protected, owner.policy.decision.optics)
      now += 6_000_000_000L
      cursor = logCursor()
      gesture("trenta-secondi", 3_700)
      assertEquals(GlassOptics.Reduced, owner.policy.decision.optics)
      val riseAgain = captures(cursor)
      println("F726E2E | Protetto → Ridotto, seconda volta | catture $riseAgain")
      assertTrue("risale anche la seconda volta: $riseAgain", riseAgain.isNotEmpty() && riseAgain.last() > 0.5f)
      now += 6_000_000_000L
      gesture("ancora-trenta", 3_700)
      assertEquals("se regge arriva al Pieno, con la riga della memoria", GlassOptics.Full, owner.policy.decision.optics)
      assertEquals(GlassMemoryProfile.Reduced, owner.policy.decision.memoryProfile)
      val fullRest = pixels("06-pieno-a-riposo")
      val top = difference(reducedRest, fullRest)
      println("F726E2E | Ridotto → Pieno alla posa: scarto media %.2f max %d".format(top.first, top.second))

      val process = budget.usage()
      println("F726E2E | cambi $changes")
      println("F726E2E | memoria: vetro picco ${budget.usage(glass).peakBytes / 1024} KiB su ${budget.usage(glass).limitBytes / 1024}, " +
        "processo picco ${process.peakBytes / 1024} KiB su ${process.limitBytes / 1024}, rifiuti ${process.refusals}")
      assertTrue(budget.usage(glass).peakBytes <= budget.usage(glass).limitBytes)
      assertEquals("quattro cambi, nessuno di troppo: $changes", 4, changes.size)
      assertTrue(changes[1].startsWith("discesa in moto"))
    } finally {
      monitor.close()
    }
  }

  private companion object {
    const val FrameTag = "FluidGlassFrame"
    const val PanelLeft = 20
    const val PanelTop = 90
    const val PanelWidth = 360
    const val PanelHeight = 740
  }
}

/** Il dispositivo di prova (come [GlassBenchDevice]) con sopra l'A52s simulato, prima della prima lettura del registro. */
internal class F7p26WeakDevice : Application() {
  private var previousHandler: Thread.UncaughtExceptionHandler? = null

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    val manager = shadowOf(getSystemService(ActivityManager::class.java)!!)
    manager.setIsLowRamDevice(false)
    manager.setMemoryClass(512)
    manager.setMemoryInfo(ActivityManager.MemoryInfo().apply {
      totalMem = 8L shl 30
      availMem = 4L shl 30
      threshold = 128L shl 20
    })
    // Lo stesso profilo dell'extra della bench: la prima lettura del registro è già quella del telefono.
    GlassVariantSignals.simulation = GlassSimulation.parse("a52s")
  }

  override fun onTerminate() {
    try {
      super.onTerminate()
    } finally {
      GlassVariantSignals.simulation = null
      ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
      Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
  }
}
