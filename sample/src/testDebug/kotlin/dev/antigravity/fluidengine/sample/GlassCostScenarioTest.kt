@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.measure.ScenarioActions
import dev.antigravity.fluidengine.sample.measure.ScenarioScene
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.glass.field.GlassCostLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
import dev.antigravity.fluidengine.ui.glass.field.glassWarmSlabSurface
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi")
class GlassCostScenarioTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun groupMenu() {
    // Il compilatore Skia di Robolectric ha un limite sul programma staticamente srotolato.
    // Per il solo censimento del grafo accorciamo i cicli sui pezzi fino al primo programma
    // accettato. Gli array degli uniform, le ricette, i filtri e le dimensioni restano quelli
    // veri. Il banco visivo compila e utilizza sempre il programma completo.
    val cache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(
      SharedRuntimeShaders::class.java, "shaders",
    )
    val installed = mutableListOf<String>()
    try {
      for (source in listOf(GlassShaderSources.Body8, GlassShaderSources.BodyFilms8)) {
        for (capacity in 8 downTo 1) {
          val loop = "i < 8; ++i"
          val program = source.text.replace(loop, "i < $capacity; ++i")
          try {
            val shader = RuntimeShader(program)
            if (capacity < 8) {
              check(program != source.text)
              check(cache.putIfAbsent(source.text, shader) == null)
              installed += source.text
            }
            println("F73 backend ${source.name}: limite $capacity pezzi")
            break
          } catch (error: IllegalArgumentException) {
            if (capacity == 1 || error.message?.contains("program is too large") != true) throw error
          }
        }
      }
      measure(MeasureScenario.MenuGroup, "apre")
    } finally {
      installed.forEach(cache::remove)
    }
  }
  @Test fun draggedSheet() = measure(MeasureScenario.SheetDrag, "su")
  @Test fun pressedCapsule() = measure(MeasureScenario.CapsulePress, "tieni")
  @Test fun eightSlabs() = measure(MeasureScenario.EightSlabs, "su")

  @Test fun stillLightReplaysUntilTheAngleOrShapeChanges() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    val records = mutableListOf<Boolean>()
    GlassCostLevers.onLight = { records.add(it) }
    val target = RenderNode("F7.3 luce")
    try {
      target.setPosition(0, 0, 180, 64)
      val light = glassWarmSlabSurface(1f).onDrawLight
      fun draw(width: Float, rotation: Float) {
        val native = target.beginRecording()
        try {
          CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(native), Size(width, 64f)) {
            light(this, rotation)
          }
        } finally { target.endRecording() }
      }
      draw(160f, 0f)
      draw(160f, 0f)
      draw(160f, 0.2f)
      draw(180f, 0.2f)
      draw(180f, 0.2f)
      assertEquals(listOf(true, false, true, true, false), records)
    } finally {
      GlassCostLevers.onLight = null
      target.discardDisplayList()
      FluidGlassDiagnostics.resetForTesting()
    }
  }

  @Test fun sharedShadowSeparatesRejoinsAndReleases() {
    val diag = FluidGlassDiagnostics
    val wasEnabled = diag.enabled
    diag.resetForTesting()
    diag.enabled = true
    diag.forceShaderVerdictsForTesting(true)
    val width = mutableIntStateOf(120)
    val count = mutableIntStateOf(2)
    val target = RenderNode("F7.3 ombre")
    try {
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          val source = rememberGlassBackdrop()
          Column {
            repeat(count.intValue) { index ->
              Box(Modifier.size(if (index == 0) 120.dp else width.intValue.dp, 48.dp)
                .glassSurface(state = source, shape = FluidCapsuleShape, role = GlassRole.Floating,
                  optics = GlassDefaults.optics(GlassRole.Floating), tint = GlassDefaults.floatingTint()))
            }
          }
        }
      }
      fun checkLayers(nodes: Int, layers: Int) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        target.setPosition(0, 0, view.width, view.height)
        val canvas = target.beginRecording()
        try { view.draw(canvas) } finally { target.endRecording() }
        assertEquals(nodes, diag.liveNodes.getValue(FluidGlassNodeKind.Shadow))
        assertEquals(layers, diag.liveLayers.getValue(FluidGlassLayerKind.Shadow))
      }
      checkLayers(2, 1)
      width.intValue = 130
      checkLayers(2, 2)
      width.intValue = 120
      checkLayers(2, 1)
      count.intValue = 1
      checkLayers(1, 1)
      count.intValue = 0
      checkLayers(0, 0)
    } finally {
      target.discardDisplayList()
      diag.enabled = wasEnabled
      diag.resetForTesting()
    }
  }

  private fun measure(scene: MeasureScenario, phase: String) {
    val memory = prepareGlassBenchMemory(compose.activity)
    val diagnostics = FluidGlassDiagnostics
    val enabled = diagnostics.enabled
    val warm = diagnostics.warmUpEnabled
    diagnostics.resetForTesting()
    diagnostics.enabled = true
    diagnostics.warmUpEnabled = false
    diagnostics.forceShaderVerdictsForTesting(true)
    var samples = 0
    var lightRecords = 0
    var lightHits = 0
    var bodies = 0
    var frosts = 0
    var pieces = 0
    var bodyShadows = 0
    val oldProbe = glassBodyProbe
    val menuFinishes = mutableSetOf<Size>()
    GlassCostLevers.onCapture = { sample, finish ->
      assertEquals((finish.width.toInt() + 1) / 2, sample.width)
      assertEquals((finish.height.toInt() + 1) / 2, sample.height)
      if (scene == MeasureScenario.MenuGroup) menuFinishes.add(finish)
      samples++
    }
    GlassCostLevers.onLight = { if (it) lightRecords++ else lightHits++ }
    glassBodyProbe = { _, input ->
      bodies++
      if (input.blurPx > 0f && input.mix < 1f) frosts++
      pieces = maxOf(pieces, input.body.count)
      if (input.lightInBody && (input.shadowAmbientAlpha > 0f || input.shadowContactAlpha > 0f)) bodyShadows++
    }
    val actions = ScenarioActions()
    val display = RenderNode("F7.3 scenario")
    lateinit var ui: CoroutineScope
    try {
      compose.mainClock.autoAdvance = false
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          ui = rememberCoroutineScope()
          val chrome = rememberFluidChromeController()
          val host = rememberFluidGlassModalHostState()
          val fallback = rememberGlassBackdrop()
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
            ProvideFluidChrome(chrome, 0.dp, remember { FluidScrollToTopBus() }) {
              Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().fluidChromePages()) { ScenarioScene(scene, actions, 0.dp) }
                FluidGlassModalHost(host, chrome.activeBackdrop.value ?: fallback)
              }
            }
          }
        }
      }
      fun frame() {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        Snapshot.sendApplyNotifications()
        compose.runOnUiThread {
          val view = compose.activity.window.decorView
          display.setPosition(0, 0, view.width, view.height)
          val canvas = display.beginRecording()
          try { view.draw(canvas) } finally { display.endRecording() }
        }
      }
      repeat(36) { frame() }
      val beforeRest = diagnostics.totalCaptures
      repeat(12) { frame() }
      assertTrue("scena pronta: $scene", actions.ready)
      val initial = diagnostics.totalCaptures
      val initialNodes = diagnostics.liveNodes.getValue(FluidGlassNodeKind.Backdrop)
      assertTrue("controllo positivo $scene: $initial catture", initial > 0)
      assertTrue(initialNodes > 0)
      if (scene == MeasureScenario.EightSlabs) {
        assertEquals(8, initialNodes)
        assertEquals(8, diagnostics.liveNodes.getValue(FluidGlassNodeKind.Shadow))
        assertEquals(1, diagnostics.liveLayers.getValue(FluidGlassLayerKind.Shadow))
      }
      println("F73 $scene riposo: nodes=${diagnostics.liveNodes} layers=${diagnostics.liveLayers} captures=$initial ultime12=${initial - beforeRest}")
      compose.runOnUiThread { ui.launch { checkNotNull(actions.action(phase)).invoke() } }
      var previous = initial
      val initialSamples = samples
      var peak = initialNodes
      repeat(110) { index ->
        bodies = 0
        frosts = 0
        bodyShadows = 0
        val previousSamples = samples
        frame()
        val total = diagnostics.totalCaptures
        val nodes = diagnostics.liveNodes
        peak = maxOf(peak, nodes.getValue(FluidGlassNodeKind.Backdrop))
        println("F73 $scene frame=$index captures=${total - previous} nodes=$nodes layers=${diagnostics.liveLayers} sample=${samples-previousSamples} bodies=$bodies blurUpdates=$frosts bodyShadowUpdates=$bodyShadows")
        previous = total
      }
      println("F73 $scene fine: campioni dimezzati=${samples - initialSamples}, luce records=$lightRecords hits=$lightHits, maxPezzi=$pieces")
      if (scene == MeasureScenario.MenuGroup) {
        assertTrue("finiture durante il viaggio: $menuFinishes", menuFinishes.isNotEmpty())
        assertTrue("ritaglio del menu realmente registrato: $menuFinishes", menuFinishes.all { it.height < 1280f })
        println("F73b finiture registrate $menuFinishes")
      }
      if (scene == MeasureScenario.MenuGroup) assertTrue("campione fisico ridotto durante il viaggio", samples > initialSamples)
      assertNull("gesto $phase", actions.failure)
      if (scene == MeasureScenario.MenuGroup) assertTrue("il menu monta il pannello: $initialNodes -> $peak", peak > initialNodes)
      if (scene == MeasureScenario.EightSlabs) assertEquals(8, peak)
    } finally {
      GlassCostLevers.onCapture = null
      GlassCostLevers.onLight = null
      glassBodyProbe = oldProbe
      display.discardDisplayList()
      diagnostics.enabled = enabled
      diagnostics.warmUpEnabled = warm
      diagnostics.resetForTesting()
      memory.close()
    }
  }
}
