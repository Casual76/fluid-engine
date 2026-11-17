package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
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

/**
 * Conta i gruppi reali del compilatore Compose, anche quando il loro contenuto riesce a saltare la ricomposizione.
 * Il lettore controllato consegna il fondo uniforme disegnato: si provano sonda, giro e albero, non HWUI o GPU.
 */
@OptIn(InternalComposeTracingApi::class, InternalComposeApi::class, ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class GlassFlipRecompositionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val fund = mutableStateOf(Color.White)
  private val sides = linkedMapOf<String, Boolean>()
  private val contentCounts = linkedMapOf<String, Int>()
  private val trace = Groups()
  private var diagnosticsWere = false
  private var warmUpWas = true

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    warmUpWas = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidGlassProbeDiagnostics.readerForTesting = { fund.value }
    Composer.setTracer(trace)
  }

  @After fun stop() {
    Composer.setTracer(null)
    FluidGlassDiagnostics.warmUpEnabled = warmUpWas
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun frame() {
    compose.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    Snapshot.sendApplyNotifications()
  }

  private fun drawWindow() = compose.runOnUiThread {
    val view = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
  }

  @Composable private fun Label(id: String) {
    contentCounts[id] = (contentCounts[id] ?: 0) + 1
    sides[id] = LocalFluidGlassAppearance.current?.isDark ?: false
    Text(id, color = LocalContentColor.current)
  }

  @Composable private fun Button(backdrop: GlassBackdropState, id: String) {
    FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Label(id) }
  }

  @Test fun aContainerAndAnIndependentGlassRecomposeOnlyAtTheInkCrossing() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val composition = currentComposer.composition
      DisposableEffect(composition) {
        val observation = checkNotNull(composition.setObserver(trace))
        onDispose { observation.dispose() }
      }
      FluidTheme(settings = remember { EngineSettings() }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind { drawRect(fund.value) })
          // Non esegue gli shader che il compilatore nativo di Robolectric non accetta; albero e sonde sono veri.
          Column(Modifier.padding(24.dp).drawWithContent { }) {
            FluidGlassContainer(spacing = 0.dp) {
              Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(3) { Button(backdrop, "pezzo$it") }
              }
            }
            Button(backdrop, "autonomo")
          }
        }
      }
    }
    repeat(200) { frame() }
    trace.collectReads = false
    assertEquals("una sonda del contenitore e una del vetro autonomo", 2, FluidGlassProbeDiagnostics.liveProbes)
    assertEquals(4, sides.size)
    assertTrue(sides.toString(), sides.values.none { it })

    for (dark in listOf(true, false)) {
      val beforeContent = contentCounts.toMap()
      val journeys = FluidGlassContainerDiagnostics.totalJourneys
      fund.value = if (dark) Color.Black else Color.White
      Snapshot.sendApplyNotifications()
      drawWindow()
      val perFrame = ArrayList<Map<String, Int>>()
      val passes = ArrayList<Int>()
      repeat(180) { index ->
        trace.counts.clear()
        trace.passes = 0
        trace.nanos = 0L
        frame()
        val counts = trace.counts.toMap()
        perFrame += counts
        passes += trace.passes
        if (counts.isNotEmpty() || trace.passes > 0) {
          println("GlassFlipRecomposition dark=$dark step=$index passes=${trace.passes} composeNs=${trace.nanos} groups=$counts")
        }
      }
      val totals = linkedMapOf<String, Int>()
      for (counts in perFrame) for ((name, count) in counts) totals[name] = (totals[name] ?: 0) + count
      println("GlassFlipRecomposition dark=$dark passes=${passes.sum()} totals=$totals reads=${trace.reads} contentBefore=$beforeContent contentAfter=$contentCounts")
      assertTrue("il giro termina sul lato chiesto: $sides", sides.values.all { it == dark })
      assertEquals("il cambio del fondo non avvia un viaggio", journeys, FluidGlassContainerDiagnostics.totalJourneys)
      for ((id, count) in contentCounts) assertTrue("$id: una ricomposizione al cambio lato", count - beforeContent.getValue(id) <= 1)
      for (group in listOf("GlassInkContent", "rememberGlassAdaptiveState", "FluidGlassContainer", "FluidGlassFields")) {
        val activeFrames = perFrame.count { (it[group] ?: 0) > 0 }
        assertTrue("$group si ricompone in $activeFrames passi invece che solo all'attraversamento: $totals", activeFrames <= 2)
      }
      assertTrue("il Recomposer attraversa la composizione in ${passes.count { it > 0 }} passi: $passes", passes.count { it > 0 } <= 2)
    }
  }

  private class Groups : CompositionTracer, CompositionObserver {
    val counts = linkedMapOf<String, Int>()
    val reads = linkedSetOf<String>()
    var collectReads = true
    var passes = 0
    var nanos = 0L
    private var began = 0L
    override fun onBeginComposition(composition: ObservableComposition) {
      passes++
      began = System.nanoTime()
    }
    override fun onEndComposition(composition: ObservableComposition) { nanos += System.nanoTime() - began }
    override fun onScopeEnter(scope: RecomposeScope) = Unit
    override fun onScopeExit(scope: RecomposeScope) = Unit
    override fun onScopeDisposed(scope: RecomposeScope) = Unit
    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
    override fun onReadInScope(scope: RecomposeScope, value: Any) {
      // Conserva solo le firme delle letture: niente valori di stato letti dalla diagnostica.
      if (!collectReads) return
      val read = Throwable().stackTrace.firstOrNull { it.className.endsWith(".GlassAdaptiveState") }
      if (read != null) reads += read.methodName
    }
    override fun isTraceInProgress(): Boolean = true
    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
      if (info.startsWith("dev.antigravity.fluidengine.ui.")) {
        val name = info.substringBefore(" (").substringAfterLast('.')
        counts[name] = (counts[name] ?: 0) + 1
      }
    }
    override fun traceEventEnd() = Unit
  }
}
