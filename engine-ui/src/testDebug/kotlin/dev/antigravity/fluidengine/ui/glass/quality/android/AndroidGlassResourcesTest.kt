package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.FrameMetricsBuilder
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import org.robolectric.shadows.ShadowWindow
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidGlassResourcesTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()
  private val am get() = app.getSystemService(ActivityManager::class.java)!!
  private val pm get() = app.getSystemService(PowerManager::class.java)!!
  private var crashHandler: Thread.UncaughtExceptionHandler? = null

  @Before fun newProcess() {
    // Robolectric riusa il classloader fra processi Application distinti.
    RuntimeEnvironment.getApplication()
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    crashHandler = Thread.getDefaultUncaughtExceptionHandler()
  }

  @After fun restoreHandler() { Thread.setDefaultUncaughtExceptionHandler(crashHandler) }

  private fun visibleActivity() = Robolectric.buildActivity(Activity::class.java).setup().also {
    // Senza WindowManager di sistema Robolectric lascia GONE in AttachInfo anche dopo setup().
    // Recapitare la notifica Android alla gerarchia attaccata, inclusa la sentinella del decor.
    val decor = it.get().window.decorView
    val info = ReflectionHelpers.getField<Any>(decor, "mAttachInfo")
    ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
    decor.dispatchWindowVisibilityChanged(View.VISIBLE)
  }

  private fun prepare(low: Boolean = false) {
    shadowOf(am).setIsLowRamDevice(low)
    shadowOf(am).setMemoryClass(512)
    shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply {
      totalMem = 8L shl 30
      availMem = 4L shl 30
      threshold = 256L shl 20
    })
    shadowOf(pm).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
  }

  @Test fun startupPrecedesFirstLayerAndTwoWindowsShareTheSameBudget() {
    prepare(low = true)
    val resources = GlassStartup().create(app)
    assertEquals(GlassOptics.Protected, resources.decision(null).optics)
    assertEquals(12L shl 20, resources.budget.usage(FluidMemoryCategory.GLASS).limitBytes)
    assertFalse(resources.prefetchAllowed.value)
    val first = visibleActivity()
    val second = visibleActivity()
    assertSame(resources.budget, FluidGlassResources.get(second.get()).budget)
    assertEquals(GlassOptics.Protected, resources.decision(first.get().window).optics)
    assertTrue(resources.prefetchAllowed.value)
    first.pause().stop().destroy()
    assertTrue(resources.prefetchAllowed.value)
    second.pause().stop().destroy()
    assertFalse(resources.prefetchAllowed.value)
  }

  @Test fun runningTrimStopsPrefetchBeforeCallingOnlyTheActiveModules() {
    prepare()
    val resources = FluidGlassResources.get(app)
    val activity = visibleActivity()
    assertTrue(resources.prefetchAllowed.value)
    val received = mutableListOf<Int>()
    val old = resources.observeTrim { error("Modulo chiuso") }
    val active = resources.observeTrim { assertFalse(resources.prefetchAllowed.value); received.add(it) }
    old.close()
    app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
    assertEquals(listOf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW), received)
    assertEquals(GlassOptics.Protected, resources.decision(activity.get().window).optics)
    active.close()
    app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    assertEquals(1, received.size)
    activity.pause().stop().destroy()
  }

  @Config(shadows = [RecoveryTestHeap::class])
  @Test fun coldMemoryPressureClearsDuringMotionWithoutAllocationOrForegroundEvent() {
    prepare()
    val memory = ActivityManager.MemoryInfo().apply {
      totalMem = 8L shl 30
      availMem = 200L shl 20
      threshold = 256L shl 20
      lowMemory = true
    }
    shadowOf(am).setMemoryInfo(memory)
    val resources = GlassStartup().create(app)
    val activity = visibleActivity()
    val window = activity.get().window
    val owner = ReflectionHelpers.getField<Map<Window, GlassWindow>>(resources, "windows").getValue(window)
    val period = 20_000_000L
    var now = System.nanoTime()
    val frames = WindowFrames(resources, owner.policy, Handler(Looper.getMainLooper()), 0, period, owner.render) { now }
    fun motion(count: Int) {
      frames.scrolling(true)
      repeat(count) {
        now += period
        val metrics = FrameMetricsBuilder()
          .setMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, now)
          .setMetric(FrameMetrics.VSYNC_TIMESTAMP, now)
          .setMetric(FrameMetrics.DEADLINE, period)
          .setMetric(FrameMetrics.DRAW_DURATION, period / 4)
          .build()
        if (it == 0) {
          assertEquals(period, metrics.getMetric(FrameMetrics.DEADLINE))
          assertEquals(period / 4, metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        frames.onFrameMetricsAvailable(window, metrics, 0)
      }
    }
    try {
      assertEquals(GlassOptics.Protected, resources.decision(window).optics)
      assertFalse(resources.prefetchAllowed.value)
      motion(300)
      // Cambia soltanto la memoria offerta dal sistema. Nessun trim, broadcast, riapertura o allocazione.
      memory.availMem = 4L shl 30
      memory.lowMemory = false
      shadowOf(am).setMemoryInfo(memory)
      motion(300)
      val healthy = ReflectionHelpers.getField<GlassReading>(resources, "reading")
      assertEquals(false, healthy.conditions.lowMemory)
      motion(1)
      assertSame(healthy, ReflectionHelpers.getField<GlassReading>(resources, "reading"))
      frames.scrolling(false)
      // F7.26, review R2 (difetto 1): la discesa era della memoria, non dei fotogrammi. Con la lettura sana la prima
      // posa risale di un livello senza chiedere trenta secondi di moto (qui ne sono passati dodici); fino a F7.25 il
      // vetro restava Protetto, la riga a 12 MiB e il prefetch fermo.
      assertEquals(GlassOptics.Reduced, resources.decision(window).optics)
      assertEquals(dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile.Full,
        resources.decision(window).memoryProfile)
      assertTrue(resources.prefetchAllowed.value)
      motion(1501)
      // Il dito resta fermo prima del rilascio e la memoria torna sotto soglia.
      memory.lowMemory = true
      memory.availMem = memory.threshold
      shadowOf(am).setMemoryInfo(memory)
      now += 6_000_000_000L
      frames.scrolling(false)
      assertEquals(GlassOptics.Protected, resources.decision(window).optics)
      memory.lowMemory = false
      memory.availMem = 4L shl 30
      shadowOf(am).setMemoryInfo(memory)
      motion(1802)
      assertEquals(GlassOptics.Protected, resources.decision(window).optics)
      frames.scrolling(false)
      assertEquals(GlassOptics.Reduced, resources.decision(window).optics)
      // F7.18: tolta la pressione il tetto torna quello della fascia (8 GiB, classe 512 MiB: Pieno), anche con
      // l'ottica ancora Ridotta. Fino a F7.17 seguiva l'ottica e restava a 24 MiB.
      val decided = resources.decision(window)
      assertEquals(dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile.Full, decided.memoryProfile)
      assertEquals(dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits.Full.forHeap(decided.heapReferenceBytes).glassBytes,
        resources.budget.usage(FluidMemoryCategory.GLASS).limitBytes)
      assertTrue(resources.prefetchAllowed.value)
    } finally {
      frames.close()
      activity.pause().stop().destroy()
    }
  }

  @Config(shadows = [RecoveryTestHeap::class])
  @Test fun unaRisalitaRifiutataNonSiRipeteConLaStessaSpesaVisibile() {
    prepare()
    val resources = GlassStartup().create(app)
    val activity = visibleActivity()
    val window = activity.get().window
    val owner = ReflectionHelpers.getField<Map<Window, GlassWindow>>(resources, "windows").getValue(window)
    val budget = resources.budget
    val glass = FluidMemoryCategory.GLASS
    val period = 100_000_000L
    var now = System.nanoTime()
    val monitor = WindowFrames(resources, owner.policy, Handler(Looper.getMainLooper()), 0, period, owner.render) { now }
    fun settleAfter(seconds: Int) {
      monitor.scrolling(true)
      repeat(seconds * 10) {
        now += period
        val frame = FrameMetricsBuilder().setMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, now)
          .setMetric(FrameMetrics.VSYNC_TIMESTAMP, now).setMetric(FrameMetrics.DEADLINE, period)
          .setMetric(FrameMetrics.DRAW_DURATION, period / 4).build()
        monitor.onFrameMetricsAvailable(window, frame, 0)
      }
      monitor.scrolling(false)
    }

    // Un fondale RGBA8 filtrato da 1080 x 2200: cattura + scratch, righe allineate a 64 byte.
    fun bytes(width: Int, height: Int) = dev.antigravity.fluidengine.foundation.memory.fluidSurfaceBytes(
      width.toLong(), height.toLong(), 4, 2, (width * 4L + 63L) / 64L * 64L)
    val previous = checkNotNull(budget.tryReserve(glass, bytes(540, 1100))).use { checkNotNull(it.confirm()) }
    // F7.18: il tetto non segue più l'ottica, quindi l'altra occupazione visibile riempie il tetto della fascia fino a
    // lasciare fuori la stessa eccedenza del tablet (1.915.904 B) invece dei 3 MiB sotto il tetto Ridotto di allora.
    val excess = 27_081_728L - 25_165_824L
    val others = budget.usage(glass).limitBytes - budget.usage(glass).usedBytes - bytes(1080, 2200) + excess
    val other = checkNotNull(budget.tryReserve(glass, others)).use { checkNotNull(it.confirm()) }
    val reader = Any()
    fun denial(denied: Boolean) = resources.visibleCaptureDenied(window, denied, reader, bytes(1080, 2200), now)
    try {
      denial(true)
      denial(false)
      var refusedUpgrades = 0
      repeat(5) { attempt ->
        settleAfter(40)
        if (resources.decision(window).optics == GlassOptics.Reduced) {
          now += 488_000_000L
          assertNull(budget.tryReserve(glass, bytes(1080, 2200)))
          refusedUpgrades++
          println("F712C prova ${attempt + 1} rifiutata: usati=${budget.usage(glass).usedBytes}, " +
            "richiesti=${bytes(1080, 2200)}, limite=${budget.usage(glass).limitBytes}")
          denial(true)
          denial(false) // Il piccolo campione residente è ancora ammesso.
        }
      }
      assertEquals("stesso fondale, stessa occupazione, cinque pose sane", 1, refusedUpgrades)
      other.close()
      settleAfter(80)
      assertEquals(GlassOptics.Reduced, resources.decision(window).optics)
      checkNotNull(budget.tryReserve(glass, bytes(1080, 2200))).close()

      // Il costo rifiutato a Pieno non deve impedire il gradino intermedio.
      settleAfter(40)
      assertEquals(GlassOptics.Full, resources.decision(window).optics)
      val fullCost = budget.usage(glass).limitBytes
      assertNull(budget.tryReserve(glass, fullCost))
      now += 488_000_000L
      resources.visibleCaptureDenied(window, true, reader, fullCost, now)
      resources.visibleCaptureDenied(window, false, reader, fullCost, now)
      settleAfter(80)
      assertEquals(GlassOptics.Reduced, resources.decision(window).optics)
      settleAfter(40)
      assertEquals(GlassOptics.Reduced, resources.decision(window).optics)
      resources.forgetRejectedCapture(window, reader)
      settleAfter(40)
      assertEquals(GlassOptics.Full, resources.decision(window).optics)
    } finally {
      previous.close()
      other.close()
      monitor.close()
      activity.pause().stop().destroy()
    }
  }

  @Test fun severeThermalFromPowerManagerProtectsAllWindowsImmediately() {
    prepare()
    val resources = FluidGlassResources.get(app)
    val activity = visibleActivity()
    assertTrue(resources.decision(activity.get().window).optics > GlassOptics.Protected)
    shadowOf(pm).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(GlassOptics.Protected, resources.decision(activity.get().window).optics)
    assertFalse(resources.prefetchAllowed.value)
    activity.pause().stop().destroy()
  }

  @Config(sdk = [32])
  @Test fun pre33SystemPowerBroadcastRefreshesWithoutAnAppPermission() {
    prepare()
    val resources = FluidGlassResources.get(app)
    val before = ReflectionHelpers.getField<GlassReading>(resources, "reading")
    assertEquals(false, before.conditions.powerSave)
    shadowOf(pm).setIsPowerSaveMode(true)
    app.sendBroadcast(android.content.Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    shadowOf(Looper.getMainLooper()).idle()
    val after = ReflectionHelpers.getField<GlassReading>(resources, "reading")
    assertEquals(true, after.conditions.powerSave)
  }

  @Test fun hiddenDetachedAndReopenedWindowRejectsAnAlreadyQueuedCallback() {
    prepare()
    val resources = FluidGlassResources.get(app)
    val controller = visibleActivity()
    val window = controller.get().window
    // Robolectric non ha RenderThread. Solo questo decor è marcato accelerato. La vera
    // registrazione e rimozione dell'adattatore attraversano la Window shadow del framework.
    window.decorView.visibility = View.GONE
    val attachInfo = ReflectionHelpers.getField<Any>(window.decorView, "mAttachInfo")
    ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
    window.decorView.visibility = View.VISIBLE
    val listeners = ReflectionHelpers.getField<Set<Window.OnFrameMetricsAvailableListener>>(
      shadowOf(window) as ShadowWindow, "onFrameMetricsAvailableListeners")
    val old = listeners.single() as WindowFrames
    window.decorView.visibility = View.GONE
    assertTrue(listeners.isEmpty())
    assertFalse(resources.prefetchAllowed.value)
    window.decorView.visibility = View.VISIBLE
    assertNotSame(old, listeners.single())
    val before = resources.decision(window)
    val vsync = System.nanoTime()
    val frame = org.robolectric.shadows.FrameMetricsBuilder()
      .setMetric(android.view.FrameMetrics.INTENDED_VSYNC_TIMESTAMP, vsync).build()
    old.onFrameMetricsAvailable(window, frame, 0)
    assertEquals(-1L, ReflectionHelpers.getField<Long>(old, "lastVsync").toLong())
    val live = listeners.single() as WindowFrames
    live.onFrameMetricsAvailable(window, frame, 0)
    assertEquals(vsync, ReflectionHelpers.getField<Long>(live, "lastVsync").toLong())
    assertSame(before, resources.decision(window))
    controller.pause().stop().destroy()
    assertTrue(listeners.isEmpty())
    assertFalse(resources.prefetchAllowed.value)
  }

  @Test fun lastDialogLeaseCanCloseDuringTheFrameworkDetachTraversal() {
    prepare()
    val resources = FluidGlassResources.get(app)
    val activity = visibleActivity()
    val dialog = android.app.Dialog(activity.get())
    val child = View(activity.get())
    dialog.setContentView(child)
    dialog.show()
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue(child.isAttachedToWindow)
    val window = dialog.window!!
    val decor = window.decorView as android.view.ViewGroup
    val before = decor.childCount
    val lease = resources.acquire(window)
    assertEquals(before + 1, decor.childCount)
    child.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) = Unit
      override fun onViewDetachedFromWindow(v: View) = lease.close()
    })
    dialog.dismiss()
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(before, decor.childCount)
    activity.pause().stop().destroy()
  }

  private fun previous(reason: Int, description: String, process: String, pid: Int = 4321,
    timestamp: Long = System.currentTimeMillis()) {
    shadowOf(am).addApplicationExitInfo(ApplicationExitInfoBuilder.newBuilder()
      .setProcessName(process).setPackageUid(Process.myUid()).setRealUid(Process.myUid())
      .setPid(pid).setTimestamp(timestamp).setReason(reason).setDescription(description).build())
  }

  @Test fun ownPreviousJavaOomIsUsedEvenWhenAnotherProcessExitedMoreRecently() {
    prepare()
    previous(ApplicationExitInfo.REASON_CRASH, "java.lang.OutOfMemoryError", Application.getProcessName())
    previous(ApplicationExitInfo.REASON_USER_REQUESTED, "", Application.getProcessName() + ":worker")
    val resources = GlassStartup().create(app)
    val observed = ReflectionHelpers.getField<AndroidGlassSignals>(resources, "signals")
    assertEquals("OutOfMemoryError", observed.previousExit)
    assertEquals(GlassOptics.Protected, resources.decision(null).optics)
  }

  @Test fun aNewerOrdinaryExitInOurProcessSupersedesAnOlderOom() {
    prepare()
    previous(ApplicationExitInfo.REASON_LOW_MEMORY, "", Application.getProcessName())
    previous(ApplicationExitInfo.REASON_CRASH, "java.lang.IllegalStateException", Application.getProcessName())
    val resources = GlassStartup().create(app)
    val signals = ReflectionHelpers.getField<AndroidGlassSignals>(resources, "signals")
    assertNull(signals.previousExit)
    val initialReading = ReflectionHelpers.getField<GlassReading>(resources, "reading")
    assertEquals(initialReading.toString(), GlassOptics.Reduced, resources.decision(null).optics)
  }

  @Test fun lowMemoryExitStartsProtectedWithoutOpeningAWindow() {
    prepare()
    previous(ApplicationExitInfo.REASON_LOW_MEMORY, "", Application.getProcessName())
    assertEquals(GlassOptics.Protected, GlassStartup().create(app).decision(null).optics)
  }

  @Test fun uncaughtJavaOomWithGenericExitDescriptionPreservesTheHostHandler() {
    prepare()
    val crash = IllegalStateException("decoder", OutOfMemoryError())
    var forwarded: Throwable? = null
    Thread.setDefaultUncaughtExceptionHandler { _, error -> forwarded = error }
    AndroidGlassSignals(app)
    Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)
    assertSame(crash, forwarded)
    val name = Application.getProcessName()
    val marker = java.io.File(app.noBackupFilesDir, "fluid-oom-" + android.net.Uri.encode(name))
    assertTrue(marker.isFile)
    previous(ApplicationExitInfo.REASON_CRASH, "", name, Process.myPid(), marker.lastModified())
    assertEquals("OutOfMemoryError", AndroidGlassSignals(app).previousExit)
    assertFalse(marker.exists())
  }

  @Test fun debugSignalsAreOptIn() {
    prepare()
    val signals = AndroidGlassSignals(app)
    assertFalse(GlassVariantSignals.lateFrames())
    assertEquals(4L, GlassFrameTimings().duration(4L, 10L, 10L, GlassFrameEvidence.MeasuredApp))
    assertEquals(false, signals.read().capacity.lowRamDevice)
    for (tag in listOf("FluidF7LowRam", "FluidF7Hot", "FluidF7Late")) ShadowLog.setLoggable(tag, Log.VERBOSE)
    val simulated = signals.read()
    assertEquals(true, simulated.capacity.lowRamDevice)
    assertEquals(192, simulated.capacity.heapClassMiB)
    assertEquals(dev.antigravity.fluidengine.ui.glass.quality.GlassThermal.SevereOrWorse, simulated.conditions.thermal)
    assertTrue(GlassVariantSignals.lateFrames())
    val frame = GlassFrameTimings()
    assertEquals(20L, frame.duration(4L, 10L, 10L, GlassFrameEvidence.MeasuredApp))
    assertEquals(4L, frame.duration(4L, 10L, 10L, GlassFrameEvidence.Estimated))
  }

  @Test fun visibleDenialBeforeAWindowAndInsideASecondWindowUpdatesTheSameLedger() {
    prepare()
    val resources = FluidGlassResources.get(app)
    val budget = resources.budget
    // F7.18: il rifiuto abbassa l'ottica e ferma il prefetch, ma non il tetto del registro: il tetto è della fascia.
    val tier = budget.usage().limitBytes
    resources.visibleBudgetDenied(null, true)
    assertEquals(GlassOptics.Protected, resources.decision(null).optics)
    assertEquals(tier, budget.usage().limitBytes)
    val first = visibleActivity()
    assertFalse(resources.prefetchAllowed.value)
    resources.visibleBudgetDenied(null, false)
    val second = visibleActivity()
    resources.visibleBudgetDenied(second.get().window, true)
    assertEquals(tier, budget.usage().limitBytes)
    assertSame(budget, FluidGlassResources.get(first.get()).budget)
    assertFalse(resources.prefetchAllowed.value)
    second.pause().stop().destroy()
    first.pause().stop().destroy()
  }

  @Test fun compiledListenerDoesNotAllocateOrBoxTheMetricsOfAFrame() {
    assertNoFrameAllocations(WindowFrames::class.java, mutableSetOf("onFrameMetricsAvailable", "appWork"))
    assertNoFrameAllocations(GlassFrameTimings::class.java, mutableSetOf("duration"))
  }

  private fun assertNoFrameAllocations(type: Class<*>, paths: MutableSet<String>) {
    val bytecode = type.getResourceAsStream(type.simpleName + ".class")!!
    bytecode.use {
      ClassReader(it).accept(object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?,
          exceptions: Array<out String>?): MethodVisitor? {
          if (!paths.remove(name)) return null
          return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitTypeInsn(opcode: Int, type: String) {
              assertFalse("$name alloca $type", opcode == Opcodes.NEW || opcode == Opcodes.ANEWARRAY)
            }
            override fun visitIntInsn(opcode: Int, operand: Int) { assertNotEquals(Opcodes.NEWARRAY, opcode) }
            override fun visitMultiANewArrayInsn(descriptor: String, dimensions: Int) { fail(descriptor) }
            override fun visitMethodInsn(opcode: Int, owner: String, method: String, desc: String, isInterface: Boolean) {
              assertFalse("$name crea un wrapper $owner", method == "valueOf" && owner.startsWith("java/lang/"))
            }
          }
        }
      }, 0)
    }
    assertTrue(paths.isEmpty())
  }
}

/**
 * Solo l'heap Java del dispositivo di prova: le condizioni lowMemory/availMem vengono da ActivityManager. F7.26: poi le
 * condizioni della variante (i flag e il profilo simulato), così una sola classe d'ombra serve a tutte le prove del
 * pacchetto (Robolectric ne tiene una sola per l'oggetto, per tutto il sandbox).
 */
@Implements(value = GlassVariantSignals::class, isInAndroidSdk = false)
internal class RecoveryTestHeap {
  @Implementation protected fun conditions(real: GlassConditions): GlassConditions =
    org.robolectric.shadow.api.Shadow.directlyOn(
      GlassVariantSignals, GlassVariantSignals::class.java, "conditions",
      org.robolectric.util.ReflectionHelpers.ClassParameter.from(GlassConditions::class.java, real.copy(heapFraction = 0.2)),
    )
}
