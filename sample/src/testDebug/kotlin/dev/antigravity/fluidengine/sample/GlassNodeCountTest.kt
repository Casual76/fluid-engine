@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityPolicy
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.glass.quality.android.GlassWindow
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.sample.material.MaterialTarget
import dev.antigravity.fluidengine.sample.material.trackBounds
import dev.antigravity.fluidengine.sample.measure.rememberScenarioFinger
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.measure.ScenarioActions
import dev.antigravity.fluidengine.sample.measure.ScenarioScene
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextMenuController
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.recordContextPreview
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.field.GlassCostLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.sample.dynamic.ActivityPillScene

/**
 * Quanti nodi e quanti layer costa una lastra ferma (piano «iOS in Android», 3.3, 4.4, F1A-f), contati in due modi
 * che non dipendono l'uno dall'altro:
 *  - **dalla catena dei modificatori** del nodo di layout (`LayoutInfo.getModifierInfo()`): un elemento per nodo, e
 *    il layer di posa dove c'è. Si confronta una scatola con il vetro e la stessa scatola senza;
 *  - **dalla diagnostica dell'engine** (`FluidGlassDiagnostics.liveNodes` e `liveLayers`), che conta dove i layer
 *    nascono e si rilasciano. Sotto Robolectric contiamo dopo aggancio, posa e primo disegno, quando la sagoma
 *    determina quale maschera d'ombra usare. Il gemello sul dispositivo è `GlassNodeCountInstrumentedTest`.
 *
 * I numeri, e perché sono questi:
 *  - **`Liquid`: 4 nodi, 3 layer** (cattura, posa, ombra). I nodi sono la presenza (salta il disegno a intensità
 *    zero), l'ombra, il nodo del vetro e la dissolvenza del contenuto: due di questi non hanno layer né passate a
 *    riposo. Il piano (3.3) chiede 3 layer, e 2 senza ombra: coincide;
 *  - **`Liquid` senza ombra** (ruolo `Content`): 3 nodi, 2 layer;
 *  - **`Classic`**: 5 nodi (maschera, ombra interna, ombra, anello, vetro) e 5 layer (posa, cattura, e uno ciascuno
 *    per ombra interna, ombra e anello). Questi ultimi tre nodi stanno nei file di Kyant fuori dal perimetro di F1A.7
 *    e la diagnostica non li conta ancora: qui si contano i nodi dalla catena, e il loro layer si legge dal codice
 *    (`createGraphicsLayer` nel loro `onAttach`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi", application = GlassBenchDevice::class, shadows = [GlassBenchHeap::class])
class GlassNodeCountTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private var warmUpWas = false
  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null

  @Before
  fun start() {
    // Il logger risolve il flag una volta per processo. La suite condivide la JVM con altre scene,
    // quindi salva e ripristina anche il valore sentinella della Lazy, se era ancora da inizializzare.
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    diagnosticsWere = FluidGlassDiagnostics.enabled
    warmUpWas = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
  }

  @After
  fun stop() {
    ReflectionHelpers.setField(frameLog, "_value", previousFrameLog)
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.warmUpEnabled = warmUpWas
  }

  @Test fun ilPrimoMenuParteGiaConIlProfiloDelBanco() {
    val resources = FluidGlassResources.get(compose.activity)
    val window = compose.activity.window
    resources.acquire(window).use {
      val process = resources.decision(null)
      val first = resources.decision(window)
      assertTrue(process.optics != GlassOptics.Protected)
      assertEquals(process, first)
      assertEquals(1024L shl 20, first.heapReferenceBytes)
      val before = resources.budget.usage(FluidMemoryCategory.GLASS)
      assertEquals(0L, before.usedBytes)
      // F7.18: il tetto è quello della fascia del banco (8 GiB, heap 1024 MiB: Pieno) qualunque sia l'ottica d'avvio.
      assertEquals(FluidMemoryLimits.Full.forHeap(first.heapReferenceBytes).glassBytes, before.limitBytes)
      resources.refresh()
      assertEquals(first, resources.decision(window))
      assertEquals(before.limitBytes, resources.budget.usage(FluidMemoryCategory.GLASS).limitBytes)
    }
  }

  @Test fun livelloProtettoCatturaIlGrandeFondaleUnaVoltaAMetaLato() {
    ShadowLog.setLoggable("FluidF7Hot", android.util.Log.VERBOSE)
    val resources = dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources.get(compose.activity)
    try {
      resources.refresh()
      val before = ShadowLog.getLogsForTag(FrameTag).size
      compose.setContent {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Blue))
          Box(Modifier.size(640.dp, 512.dp).drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape }, {}))
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      val frames = ShadowLog.getLogsForTag(FrameTag).drop(before).map { it.msg }.filter { " kind=capture " in it }
      assertTrue("cattura grande in Protetto: $frames", frames.any { "scale=0.5 " in it && "size=320 x 256" in it })
      val atRest = FluidGlassDiagnostics.totalCaptures
      repeat(4) { compose.mainClock.advanceTimeByFrame() }
      compose.waitForIdle()
      assertEquals("catture nuove alla posa", atRest, FluidGlassDiagnostics.totalCaptures)
    } finally {
      ShadowLog.setLoggable("FluidF7Hot", android.util.Log.INFO)
      resources.refresh()
    }
  }

  @Test fun livelliCattureDuranteGestoEAllaPosa() {
    val resources = FluidGlassResources.get(compose.activity)
    val window = compose.activity.window
    val lease = resources.acquire(window)
    val watch = ReflectionHelpers.getField<Map<android.view.Window, GlassWindow>>(resources, "windows").getValue(window)
    val original = watch.policy
    val pm = compose.activity.getSystemService(PowerManager::class.java)!!
    val healthy = GlassConditions(4L shl 30, 128L shl 20, false, 0.2, GlassThermal.None, false)
    val full = GlassQualityPolicy(
      GlassDeviceCapacity(8L shl 30, 2L shl 30, 2048, false, 35, true),
      healthy, GlassOptics.Full,
    )
    val tick = mutableIntStateOf(0)
    val mount = mutableStateOf(true)
    var draws = 0
    var cursor = ShadowLog.getLogsForTag(FrameTag).size
    fun captures(): List<Float> {
      compose.runOnUiThread { tick.intValue++; Snapshot.sendApplyNotifications() }
      drawHardwareFrame()
      val logs = ShadowLog.getLogsForTag(FrameTag)
      val scales = logs.drop(cursor).filter { " kind=capture " in it.msg }.map {
        it.msg.substringAfter(" scale=").substringBefore(' ').toFloat()
      }
      cursor = logs.size
      return scales
    }

    try {
      shadowOf(pm).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
      resources.refresh()
      synchronized(resources) {
        ReflectionHelpers.setField(watch, "policy", full)
        resources.publish()
      }
      compose.setContent {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Blue))
          if (mount.value) Box(Modifier.size(640.dp, 512.dp).drawPlainBackdrop(
            backdrop.backdrop, { FluidCapsuleShape }, {}, onDrawBehind = { tick.intValue; draws++ },
          ))
        }
      }
      compose.waitForIdle()
      assertEquals(GlassOptics.Full, resources.decision(window).optics)
      assertEquals(listOf(1f), captures())
      val scroll = resources.scrollStarted(window)
      try {
        ShadowLog.setLoggable("FluidF7Warm", Log.VERBOSE)
        resources.refresh()
        assertEquals(GlassOptics.Reduced, resources.decision(window).optics)
        assertEquals(emptyList<Float>(), captures())
        // Un nuovo lettore entra mentre la finestra scorre. Quello che era visibile ha tenuto la cattura.
        compose.runOnIdle { mount.value = false }
        compose.waitForIdle()
        compose.runOnIdle { mount.value = true }
        compose.waitForIdle()
        assertEquals(listOf(0.75f), captures())
        ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
        resources.refresh()
        assertEquals(GlassOptics.Protected, resources.decision(window).optics)
        assertEquals(emptyList<Float>(), captures())
      } finally { scroll.close() }
      assertEquals(listOf(0.5f), captures())
      val start = draws
      val allocations = ShadowLog.getLogsForTag(FrameTag).size
      val peak = resources.budget.usage(FluidMemoryCategory.GLASS).peakBytes
      repeat(12) { assertEquals(emptyList<Float>(), captures()) }
      assertTrue("il nodo ha davvero ridisegnato", draws >= start + 12)
      assertTrue("una prenotazione nuova per frame", ShadowLog.getLogsForTag(FrameTag).drop(allocations)
        .none { " kind=glassBudgetReserve " in it.msg })
      assertEquals(peak, resources.budget.usage(FluidMemoryCategory.GLASS).peakBytes)
    } finally {
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      ShadowLog.setLoggable("FluidF7Warm", Log.INFO)
      synchronized(resources) { ReflectionHelpers.setField(watch, "policy", original); resources.refresh() }
      lease.close()
    }
  }

  @Test fun rifiutoVisibileRegistraSoloIlRipiegoERestituisceIByte() {
    ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
    val resources = FluidGlassResources.get(compose.activity)
    val budget = resources.budget
    resources.refresh()
    val category = FluidMemoryCategory.GLASS
    val before = budget.usage(category)
    val held = checkNotNull(budget.tryReserve(category, before.limitBytes - before.usedBytes - 100_000L))
    val shown = mutableStateOf(true)
    val cursor = ShadowLog.getLogsForTag(FrameTag).size
    try {
      compose.setContent {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Blue))
          if (shown.value) Box(Modifier.size(640.dp, 512.dp).drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape }, {}))
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      val records = ShadowLog.getLogsForTag(FrameTag).drop(cursor).filter { " kind=capture " in it.msg }
      assertEquals("il rifiuto precede la prima texture", 1, records.size)
      assertTrue(records.single().msg, "scale=0.25 " in records.single().msg)
      val admitted = budget.usage(category)
      assertTrue(admitted.refusals > before.refusals)
      assertEquals(160L * 128 * 4, admitted.residentBytes - before.residentBytes)
      held.close()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
      drawHardwareFrame()
      assertEquals("credito tornato alla posa", 320L * 256 * 4,
        budget.usage(category).residentBytes - before.residentBytes)
      compose.runOnIdle { shown.value = false }
      compose.waitForIdle()
      assertEquals(before.residentBytes, budget.usage(category).residentBytes)
    } finally {
      held.close()
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      resources.refresh()
    }
  }

  @Test fun protettoSospendeIlLettoreCopertoERiprendeConUnaCatturaFresca() {
    ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val covered = mutableStateOf(false)
    val color = mutableStateOf(Color.Red)
    lateinit var backdrop: GlassBackdropState
    try {
      compose.setContent {
        backdrop = rememberGlassBackdrop()
        val view = androidx.compose.ui.platform.LocalView.current
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind { drawRect(color.value) })
          CompositionLocalProvider(dev.antigravity.fluidengine.ui.fluid.LocalCoveredGlassView provides
            if (covered.value) view else null) {
            Box(Modifier.size(640.dp, 512.dp).drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape }, {}))
          }
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      val used = resources.budget.usage(FluidMemoryCategory.GLASS).residentBytes
      assertTrue(used >= 320L * 256 * 4)
      compose.runOnIdle { covered.value = true }
      compose.waitForIdle()
      drawHardwareFrame()
      assertEquals(0, backdrop.layerBackdrop!!.readerCount)
      assertTrue(resources.budget.usage(FluidMemoryCategory.GLASS).residentBytes < used)
      val paused = FluidGlassDiagnostics.totalCaptures
      compose.runOnIdle { color.value = Color.Blue }
      drawHardwareFrame()
      assertEquals(paused, FluidGlassDiagnostics.totalCaptures)
      compose.runOnIdle { covered.value = false }
      compose.waitForIdle()
      drawHardwareFrame()
      assertEquals(1, backdrop.layerBackdrop!!.readerCount)
      assertEquals(paused + 1, FluidGlassDiagnostics.totalCaptures)
    } finally {
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      resources.refresh()
    }
  }

  @Test fun senzaCreditoIlVetroESfocatoSenzaUnaGrandeTexture() {
    ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val budget = resources.budget
    val category = FluidMemoryCategory.GLASS
    val before = budget.usage(category)
    val all = checkNotNull(budget.tryReserve(category, before.limitBytes - before.usedBytes))
    val cursor = ShadowLog.getLogsForTag(FrameTag).size
    try {
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          val backdrop = rememberGlassBackdrop()
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Blue))
            Box(Modifier.size(640.dp, 512.dp).glassSurface(
              state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
              role = GlassRole.Content, optics = GlassDefaults.optics(GlassRole.Content),
            )) { Text("42") }
          }
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      val records = ShadowLog.getLogsForTag(FrameTag).drop(cursor)
      // F7.26f: senza credito il gradino più basso è il glassmorphism, non il pieno: una cattura minuscola fuori dal
      // registro (al più 1 MiB di stima, come le catture piccole), sfocata e tinta. Niente byte del registro.
      assertTrue(records.any { " kind=glassFloor " in it.msg })
      assertTrue(records.none { " kind=glassSolid " in it.msg || " kind=glassDeniedFill " in it.msg })
      val captures = records.filter { " kind=capture " in it.msg }
      assertTrue("il gradino più basso non ha catturato", captures.isNotEmpty())
      for (record in captures) {
        val size = checkNotNull(Regex(""" size=(\d+) x (\d+) """).find(record.msg)) { record.msg }
        val bytes = size.groupValues[1].toLong() * size.groupValues[2].toLong() * 4 * 2
        assertTrue("una cattura grande senza credito: ${record.msg.take(200)}", bytes <= 1024L * 1024)
      }
      assertEquals(before.residentBytes, budget.usage(category).residentBytes)
      compose.onNodeWithText("42").assertExists()
    } finally {
      all.close()
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      resources.refresh()
    }
  }

  /**
   * F7.26g (review R3, D6): un vetro che cambia misura mentre sta nel gradino più basso ci rientra con la misura nuova.
   * Fino a F7.26f usciva dal gradino, la misura nuova era negata a ogni scala e si rigiocava la cattura minuscola della
   * misura vecchia, col vetro pieno sopra: nessuna cattura nuova, nessun rientro.
   */
  @Test fun unVetroCheCresceNelGradinoPiuBassoCiRientraConLaMisuraNuova() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val budget = resources.budget
    val category = FluidMemoryCategory.GLASS
    val before = budget.usage(category)
    val all = checkNotNull(budget.tryReserve(category, before.limitBytes - before.usedBytes))
    val tall = mutableStateOf(false)
    val cursor = ShadowLog.getLogsForTag(FrameTag).size
    try {
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          val backdrop = rememberGlassBackdrop()
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Blue))
            Box(Modifier.size(640.dp, if (tall.value) 820.dp else 512.dp).glassSurface(
              state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
              role = GlassRole.Content, optics = GlassDefaults.optics(GlassRole.Content),
            )) { Text("42") }
          }
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      val first = ShadowLog.getLogsForTag(FrameTag).drop(cursor)
      assertTrue("il gradino più basso non è partito", first.any { " kind=glassFloor " in it.msg })
      val grow = ShadowLog.getLogsForTag(FrameTag).size
      compose.runOnIdle { tall.value = true }
      compose.waitForIdle()
      drawHardwareFrame()
      val after = ShadowLog.getLogsForTag(FrameTag).drop(grow)
      val sizes = after.filter { " kind=capture " in it.msg }.mapNotNull { Regex(""" size=(\d+) x (\d+) """).find(it.msg) }
        .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
      println("F726G D6 | dopo la crescita: gradino ${after.count { " kind=glassFloor " in it.msg }}, catture $sizes, " +
        "pieni ${after.count { " kind=glassSolid " in it.msg || " kind=glassDeniedFill " in it.msg }}")
      assertTrue("dopo la crescita il vetro non rientra nel gradino più basso", after.any { " kind=glassFloor " in it.msg })
      assertTrue("nessuna cattura della misura nuova: $sizes", sizes.isNotEmpty())
      assertTrue(after.none { " kind=glassSolid " in it.msg || " kind=glassDeniedFill " in it.msg })
      assertEquals(before.residentBytes, budget.usage(category).residentBytes)
    } finally {
      all.close()
      resources.refresh()
    }
  }

  @Test fun ilMezzoLatoLocaleNonDimezzaLaFinituraDelViaggioProtetto() {
    ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val tick = mutableIntStateOf(0)
    val extents = mutableListOf<Pair<IntSize, IntSize>>()
    val previous = GlassCostLevers.onCapture
    GlassCostLevers.onCapture = { sample, finish ->
      extents.add(sample to IntSize(finish.width.toInt(), finish.height.toInt()))
    }
    try {
      compose.setContent {
        val source = dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCanvasBackdrop { drawRect(Color.Blue) }
        val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val travel = remember(layer, density) {
          dev.antigravity.fluidengine.ui.glass.field.GlassTravelCapture(layer, density, {})
        }
        Box(Modifier.size(512.dp).drawBackdrop(
          backdrop = source, shape = { FluidCapsuleShape }, effects = travel.effects,
          onDrawBackdrop = travel.capture, backdropScaleFactor = { 0.25f },
          highlight = null, shadow = null, innerShadow = null,
          onDrawBehind = { tick.intValue },
        ))
      }
      compose.waitForIdle()
      repeat(6) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        compose.runOnUiThread { tick.intValue++; Snapshot.sendApplyNotifications() }
        drawHardwareFrame()
      }
      assertTrue("almeno quattro campioni distinti", extents.size >= 4)
      extents.forEach { (sample, finish) ->
        assertEquals("il filo viaggia alla risoluzione di uscita", IntSize(512, 512), finish)
        assertEquals("il campione resta a metà lato", IntSize(256, 256), sample)
      }
    } finally {
      GlassCostLevers.onCapture = previous
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      resources.refresh()
    }
  }

  @Test fun unFiltroAggiuntoAllaPosaPrenotaLoScratchSenzaRicatturare() {
    ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val filtered = mutableStateOf(false)
    val category = FluidMemoryCategory.GLASS
    val base = resources.budget.usage(category).residentBytes
    try {
      compose.setContent {
        val backdrop = rememberGlassBackdrop()
        val effect = remember { android.graphics.RenderEffect.createOffsetEffect(0f, 0f).asComposeRenderEffect() }
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Red))
          Box(Modifier.size(640.dp, 512.dp).drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape },
            effects = { renderEffect = if (filtered.value) effect else null }))
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      assertEquals(320L * 256 * 4, resources.budget.usage(category).residentBytes - base)
      val captures = FluidGlassDiagnostics.totalCaptures
      compose.runOnUiThread { filtered.value = true; Snapshot.sendApplyNotifications() }
      compose.waitForIdle()
      drawHardwareFrame()
      assertEquals("cattura e scratch", 2 * 320L * 256 * 4, resources.budget.usage(category).residentBytes - base)
      assertEquals(captures, FluidGlassDiagnostics.totalCaptures)
    } finally {
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      resources.refresh()
    }
  }

  private fun drawHardwareFrame() {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val display = RenderNode("F7.12")
      display.setPosition(0, 0, view.width, view.height)
      val canvas = display.beginRecording()
      try { view.draw(canvas) } finally {
        display.endRecording()
        display.discardDisplayList()
      }
    }
  }

  private val shown = mutableStateOf(true)

  /** Due scatole uguali, una con il vetro e una senza: la differenza fra le due catene è il vetro. */
  @Composable
  private fun Scene(role: GlassRole) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val backdrop = rememberGlassBackdrop()
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
          Box(Modifier.testTag(Bare).size(156.dp, 56.dp))
          if (shown.value) {
            Box(
              Modifier
                .testTag(Slab)
                .size(156.dp, 56.dp)
                .glassSurface(
                  state = backdrop,
                  tint = GlassDefaults.floatingTint(),
                  shape = FluidCapsuleShape,
                  role = role,
                  optics = GlassDefaults.optics(role),
                ),
            )
          }
        }
      }
    }
  }

  /** Nodi e layer di posa della catena di [tag]: un elemento per nodo; i layer distinti, per identità. */
  private class Chain(val nodes: Int, val placementLayers: Int, val elements: List<String>)

  private fun chain(tag: String): Chain {
    val infos = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.getModifierInfo()
    val layers = IdentityHashMap<Any, Unit>()
    for (info in infos) info.extra?.let { layers[it] = Unit }
    return Chain(infos.size, layers.size, infos.map { it.modifier::class.java.simpleName })
  }

  private class Census(
    val nodes: Map<FluidGlassNodeKind, Int> = FluidGlassDiagnostics.liveNodes,
    val layers: Map<FluidGlassLayerKind, Int> = FluidGlassDiagnostics.liveLayers,
  ) {
    fun minus(before: Census): Pair<Map<FluidGlassNodeKind, Int>, Map<FluidGlassLayerKind, Int>> =
      nodes.mapValues { (k, v) -> v - before.nodes.getValue(k) }.filterValues { it != 0 } to
        layers.mapValues { (k, v) -> v - before.layers.getValue(k) }.filterValues { it != 0 }
  }

  /** Monta la lastra, la misura, la toglie: restituisce la catena e ciò che la diagnostica ha visto nascere. */
  private fun measure(
    label: String,
    role: GlassRole,
  ): Triple<Chain, Pair<Map<FluidGlassNodeKind, Int>, Map<FluidGlassLayerKind, Int>>, Census> {
    val before = Census()
    compose.setContent { Scene(role) }
    compose.waitForIdle()
    // La maschera condivisa si chiede al primo disegno, quando forma e ricetta sono note.
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      val node = android.graphics.RenderNode("F7.3 censimento")
      node.setPosition(0, 0, view.width, view.height)
      val canvas = node.beginRecording()
      try { view.draw(canvas) } finally { node.endRecording(); node.discardDisplayList() }
    }
    val bare = chain(Bare)
    val slab = chain(Slab)
    val glass = Chain(
      nodes = slab.nodes - bare.nodes,
      placementLayers = slab.placementLayers - bare.placementLayers,
      elements = slab.elements.drop(bare.elements.size).ifEmpty { slab.elements },
    )
    val counted = Census().minus(before)
    println(
      "GlassNodeCount $label: ${glass.nodes} nodi ${slab.elements} (senza vetro ${bare.elements}), " +
        "${glass.placementLayers} layer di posa dalla catena; diagnostica: nodi ${counted.first}, layer ${counted.second}",
    )
    // Il contatore scende: tolta la lastra, la diagnostica torna dov'era.
    compose.runOnIdle { shown.value = false }
    compose.waitForIdle()
    val after = Census()
    assertEquals("$label: i nodi non tornano", before.nodes, after.nodes)
    assertEquals("$label: i layer non tornano", before.layers, after.layers)
    return Triple(glass, counted, before)
  }

  @Test
  fun `una lastra Liquid ferma ha 4 nodi e 3 layer`() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    val (glass, counted) = measure("Liquid", GlassRole.Floating)
    // Dalla 4.0 (F2.3) piu' la sonda dello sfondo, un nodo accanto al vetro di una lastra radice: 5. I layer non
    // cambiano (la miniatura della sonda non e' un layer di posa ne' uno che la diagnostica conta).
    assertEquals(5, glass.nodes)
    assertTrue(glass.elements.toString(), glass.elements.any { it == "GlassProbeElement" })
    assertTrue(glass.elements.toString(), glass.elements.any { it == "DrawBackdropElement" })
    assertTrue(glass.elements.toString(), glass.elements.any { it == "GlassShadowElement" })
    assertTrue(glass.elements.toString(), glass.elements.none { it in KyantClassicNodes })
    assertEquals(mapOf(FluidGlassNodeKind.Backdrop to 1, FluidGlassNodeKind.Shadow to 1), counted.first)
    assertEquals(
      mapOf(FluidGlassLayerKind.Capture to 1, FluidGlassLayerKind.Placement to 1, FluidGlassLayerKind.Shadow to 1),
      counted.second,
    )
    // Il layer di posa contato dal nodo è quello che la catena mostra.
    assertEquals(glass.placementLayers, counted.second[FluidGlassLayerKind.Placement])
    assertEquals(3, counted.second.values.sum())
  }

  @Test
  fun `una lastra Liquid senza ombra ha 3 nodi e 2 layer`() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    val (glass, counted) = measure("Liquid senza ombra", GlassRole.Content)
    // Piu' la sonda (F2.3): 4.
    assertEquals(4, glass.nodes)
    assertTrue(glass.elements.toString(), glass.elements.any { it == "GlassProbeElement" })
    assertTrue(glass.elements.toString(), glass.elements.none { it == "GlassShadowElement" })
    assertEquals(mapOf(FluidGlassNodeKind.Backdrop to 1), counted.first)
    assertEquals(mapOf(FluidGlassLayerKind.Capture to 1, FluidGlassLayerKind.Placement to 1), counted.second)
    assertEquals(2, counted.second.values.sum())
  }

  @Test
  fun `una lastra Classic ferma ha 5 nodi e 5 layer`() {
    // Il vetro di prima, come lo prende un dispositivo il cui compilatore rifiuta.
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    val (glass, counted) = measure("Classic", GlassRole.Floating)
    assertEquals(5, glass.nodes)
    val kyant = glass.elements.filter { it in KyantClassicNodes }
    assertEquals(glass.elements.toString(), KyantClassicNodes, kyant.toSet())
    assertEquals(mapOf(FluidGlassNodeKind.Backdrop to 1), counted.first)
    assertEquals(mapOf(FluidGlassLayerKind.Capture to 1, FluidGlassLayerKind.Placement to 1), counted.second)
    // Posa e cattura contate, più un layer per ognuno dei tre nodi di Kyant (dal loro `onAttach`).
    assertEquals(5, counted.second.values.sum() + kyant.size)
  }

  @Test fun tasto() = checkComponent("tasto") { _, backdrop ->
    FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Text("+") }
  }

  @Test fun capsula() = checkScenario(MeasureScenario.CapsulePress)

  @Test fun gruppo() = checkComponent("gruppo") { _, backdrop ->
    FluidGlassContainer {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Text("${it + 1}") } }
      }
    }
  }

  @Test fun menuDalTasto() = checkScenario(MeasureScenario.MenuButton, menuText = "Condividi")
  @Test fun menuDalGruppo() = checkScenario(MeasureScenario.MenuGroup, menuText = "Aggiungi ai preferiti")

  /**
   * F8.5d · Il menu dal gruppo con la lettura della memoria vecchia di due secondi prima di ogni fase: come sul
   * telefono quando il menu resta aperto piu' di un secondo, e come nel lotto con la JVM lenta (l'orologio della prova
   * e' finto, l'eta' della lettura si misura in tempo vero). Senza la rilettura al cambio di campione la chiusura non
   * aveva la finitura del viaggio («chiude: finiture=0 catture=1»): la cattura rimandata aspettava un ridisegno che
   * non arrivava.
   */
  /**
   * F8.5d · L'eta' della lettura della memoria si misura sull'orologio di sistema: sul telefono e' il tempo vero, sulle
   * prove e' quello dei fotogrammi. Un PC carico che rallenta la JVM non deve invecchiarla (le prove del vetro cadevano
   * solo nei lotti lenti); un secondo di fotogrammi si'.
   */
  @Test fun laLetturaDellaMemoriaInvecchiaConLOrologioDiSistema() {
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    assertTrue(resources.allocationReadingFresh)
    Thread.sleep(1_500)
    assertTrue("il tempo vero da solo non invecchia la lettura", resources.allocationReadingFresh)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
    assertTrue("un secondo e mezzo di fotogrammi la invecchia", !resources.allocationReadingFresh)
    resources.refreshBeforeAllocation()
    assertTrue(resources.allocationReadingFresh)
  }

  @Test fun menuDalGruppoConLaLetturaDellaMemoriaVecchia() {
    val scenario = MeasureScenario.MenuGroup
    checkComponent(scenario.key, scenario.phases.map { it.name to it.journeyMillis }, "Aggiungi ai preferiti",
      staleReadingBeforePhases = true) { actions, _ -> ScenarioScene(scenario, actions, 0.dp) }
  }
  @Test fun menuContestuale() = checkScenario(MeasureScenario.ContextMenu, menuText = "Condividi")

  @Test fun menuContestualeConPressioneDa555Ms() {
    val scenario = MeasureScenario.ContextMenu
    val timeout = 555L
    val phases = scenario.phases.map { it.name to it.journeyMillis }
    checkComponent(scenario.key, phases, menuText = "Condividi", expectedLiftMillis = timeout) { actions, _ ->
      val platform = LocalViewConfiguration.current
      val tablet = remember(platform) {
        object : ViewConfiguration by platform {
          override val longPressTimeoutMillis = timeout
        }
      }
      CompositionLocalProvider(LocalViewConfiguration provides tablet) { ScenarioScene(scenario, actions, 0.dp) }
    }
  }

  @Test fun altroRegistraLaFacciaAncheQuandoApreSenzaUnFotogrammaDiPressione() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            ActivityPillScene(onBack = {})
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
    val before = ShadowLog.getLogsForTag(FrameTag).size
    compose.onNodeWithContentDescription("Altro").performClick()
    val display = RenderNode("Altro")
    try {
      compose.runOnIdle {
        val view = compose.activity.window.decorView
        display.setPosition(0, 0, view.width, view.height)
        val canvas = display.beginRecording()
        try {
          view.draw(canvas)
        } finally {
          display.endRecording()
        }
      }
      val captures = ShadowLog.getLogsForTag(FrameTag).drop(before).count { " kind=menu.preview.record " in it.msg }
      assertEquals("un'unica registrazione della faccia nel menu", 1, captures)
    } finally {
      display.discardDisplayList()
    }
  }

  @Test fun laRegistrazioneDellaRigaSegueIlContenutoDuranteLaPressione() {
    compose.mainClock.autoAdvance = false
    val outer = mutableStateOf(Color.White)
    val inner = mutableStateOf(Color.Red)
    val width = mutableStateOf(80.dp)
    val display = RenderNode("F7.3c")
    lateinit var controller: FluidContextMenuController
    var lastInk: Color? = null
    var outerDraws = 0
    compose.setContent {
      controller = rememberFluidContextMenu(actions = { emptyList() })
      Box(Modifier.layout { measurable, _ ->
        val row = measurable.measure(Constraints.fixed(width.value.roundToPx(), 48.dp.roundToPx()))
        layout(row.width, row.height) { row.place(0, 0) }
      }.drawWithContent {
        drawRect(outer.value)
        outerDraws++
        drawContent()
      }.recordContextPreview(controller).drawBehind {
        lastInk = inner.value
        drawRect(inner.value)
      })
    }
    // Cambiare il disegno esterno simula il bagliore senza dipendere dalla durata della sua molla.
    fun frame(): Int {
      Snapshot.sendApplyNotifications()
      val start = ShadowLog.getLogsForTag(FrameTag).size
      val view = compose.activity.window.decorView
      display.setPosition(0, 0, view.width, view.height)
      val canvas = display.beginRecording()
      try {
        check(canvas.isHardwareAccelerated)
        view.draw(canvas)
      } finally {
        display.endRecording()
      }
      return ShadowLog.getLogsForTag(FrameTag).drop(start).count { " kind=menu.preview.record " in it.msg }
    }
    try {
      compose.waitForIdle()
      // Un solo canvas hardware per fotogramma: il looper di Robolectric può anche chiedere
      // traversate software. Il disegno esterno deve avvenire davvero in tutti i dodici passi.
      compose.runOnUiThread {
        assertEquals(0, frame())
        controller.arm(true)
        assertEquals(1, frame())
        repeat(12) { i ->
          val before = outerDraws
          outer.value = if (i % 2 == 0) Color.Black else Color.White
          assertEquals("solo bagliore, frame $i", 0, frame())
          assertTrue(outerDraws > before)
        }
        inner.value = Color.Blue
        assertEquals("cambio del contenuto", 1, frame())
        assertEquals(Color.Blue, lastInk)
        width.value = 96.dp
        assertEquals("cambio di misura", 1, frame())
        assertEquals(IntSize(96, 48), controller.layer.size)
        controller.entry.lifted = true
        inner.value = Color.Green
        assertEquals("la card rigioca la registrazione", 0, frame())
        assertEquals(Color.Blue, lastInk)
        controller.arm(false)
        controller.entry.lifted = false
        assertEquals(0, frame())
        assertEquals(Color.Green, lastInk)
        controller.arm(true)
        assertEquals("nuova pressione", 1, frame())
      }
    } finally {
      display.discardDisplayList()
    }
  }

  @Test fun sorelleDelGruppoDurantePressioneERipresaDelloScroll() {
    val target = MaterialTarget()
    val draws = IntArray(5)
    val display = RenderNode("lista")
    lateinit var finger: MaterialFinger
    lateinit var scroll: ScrollState
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        finger = rememberScenarioFinger()
        scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
          FluidListGroup {
            FluidListRow("Nota della riunione", "Tieni premuto", modifier = Modifier.trackBounds(target),
              onClick = {}, contextActions = { emptyList() })
            repeat(5) { i ->
              FluidListDivider()
              FluidListRow("Nota ${i + 2}", "Una riga della lista", modifier = Modifier.drawWithContent {
                draws[i]++
                drawContent()
              }, onClick = {})
            }
          }
          Box(Modifier.size(100.dp, 1800.dp))
        }
      }
    }
    fun groupRecords(): Int = ShadowLog.getLogsForTag(FrameTag).count { " kind=menu.group.record " in it.msg }
    fun frame() {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FrameMillis))
      Snapshot.sendApplyNotifications()
      compose.runOnUiThread {
        val view = compose.activity.window.decorView
        display.setPosition(0, 0, view.width, view.height)
        val c = display.beginRecording()
        try { view.draw(c) } finally { display.endRecording() }
      }
    }
    try {
      repeat(6) { frame() }
      val idle = groupRecords()
      repeat(6) { frame() }
      val before = draws.copyOf()
      assertEquals("nessuna cache del gruppo a riposo", idle, groupRecords())
      val down = checkNotNull(target.bounds).center
      compose.runOnUiThread { finger.down(down) }
      repeat(35) { frame() }
      val delta = draws.indices.map { draws[it] - before[it] }
      val group = groupRecords() - idle
      println("Sorelle durante la pressione: $delta. Registrazioni gruppo: $group")
      assertTrue("pressione realmente disegnata nel gruppo", group > 0)
      assertTrue("ridisegni delle sorelle: $delta", delta.all { it <= 2 })
      compose.runOnUiThread { finger.up(down) }
      repeat(8) { frame() }
      val released = groupRecords()
      repeat(5) { frame() }
      assertEquals("il gruppo è tornato a riposo", released, groupRecords())
      compose.runOnUiThread { finger.down(down) }
      frame()
      compose.runOnUiThread { finger.move(down - Offset(0f, 40f)) }
      repeat(3) { frame() }
      val scrolling = groupRecords()
      repeat(5) { i ->
        compose.runOnUiThread { finger.move(down - Offset(0f, 48f + i * 8f)) }
        frame()
      }
      assertTrue("la lista si deve spostare", scroll.value > 0)
      assertEquals("la cache del gruppo termina allo scroll", scrolling, groupRecords())
      compose.runOnUiThread { finger.up(down - Offset(0f, 80f)) }
      repeat(5) { frame() }
      assertEquals("nessuna cache nel rilascio o nel fling", scrolling, groupRecords())
    } finally {
      display.discardDisplayList()
    }
  }

  @Test fun foglio() = checkScenario(MeasureScenario.SheetDrag)
  @Test fun ottoLastre() = checkScenario(MeasureScenario.EightSlabs)

  @Test fun popover() = checkComponent("popover") { _, _ ->
    FluidGlassModalPortal(visible = true, onDismissRequest = {}, origin = { Rect(360f, 320f, 408f, 368f) }) {
      Box(Modifier.size(200.dp, 112.dp), contentAlignment = Alignment.Center) { Text("Scegli") }
    }
  }

  @Test fun tabBar() = checkComponent("tab-bar") { _, backdrop ->
    FluidFloatingTabBar(
      items = listOf("Foto", "Album", "Cerca").map { FluidTabItem(it, it, FluidIcons.Search) },
      selectedRoute = "Foto",
      onSelect = {},
      scrollConnection = rememberFluidFloatingTabBarScrollConnection(),
      backdrop = backdrop,
    )
  }

  private fun checkScenario(scenario: MeasureScenario, menuText: String? = null) =
    checkComponent(scenario.key, scenario.phases.map { it.name to it.journeyMillis }, menuText) { actions, _ ->
      ScenarioScene(scenario, actions, 0.dp)
    }

  /**
   * Lo stesso gesto e lo stesso albero di «Misure» per i viaggi. L'orologio è fermo fra i disegni, così il
   * tetto vede anche il fotogramma in cui convivono sorgente e destinazione, durante andata e ritorno.
   */
  private fun checkComponent(
    label: String,
    journeys: List<Pair<String, Long>> = emptyList(),
    menuText: String? = null,
    expectedLiftMillis: Long? = null,
    staleReadingBeforePhases: Boolean = false,
    content: @Composable (ScenarioActions, GlassBackdropState) -> Unit,
  ) {
    val actions = ScenarioActions()
    val display = RenderNode("F7.6 • finestra")
    val previousObserver = GlassCostLevers.onCapture
    val finishes = mutableListOf<IntSize>()
    lateinit var scope: CoroutineScope
    lateinit var layers: TrackedLayers
    var logIndex = 0
    var extraHoldMillis = 0L
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    GlassCostLevers.onCapture = { sample, finish ->
      val extent = IntSize(finish.width.toInt(), finish.height.toInt())
      assertEquals("$label: campione e finitura di viaggio", IntSize((extent.width + 1) / 2, (extent.height + 1) / 2), sample)
      finishes.add(extent)
    }
    val shaderCache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(
      SharedRuntimeShaders::class.java, "shaders",
    )
    val substitutes = mutableListOf<String>()
    try {
      // Come in GlassCostScenarioTest: il limite Skia riguarda i cicli srotolati. La ricetta,
      // gli uniform e le dimensioni restano quelli del percorso Liquid di produzione.
      for (source in listOf(GlassShaderSources.Body8, GlassShaderSources.BodyFilms8)) {
        for (capacity in 8 downTo 1) {
          val program = source.text.replace("i < 8; ++i", "i < $capacity; ++i")
          try {
            val shader = RuntimeShader(program)
            if (capacity < 8) {
              check(program != source.text)
              check(shaderCache.putIfAbsent(source.text, shader) == null)
              substitutes.add(source.text)
            }
            break
          } catch (error: IllegalArgumentException) {
            if (capacity == 1 || error.message?.contains("program is too large") != true) throw error
          }
        }
      }
      compose.mainClock.autoAdvance = false
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          scope = rememberCoroutineScope()
          if (expectedLiftMillis != null) {
            extraHoldMillis = expectedLiftMillis - LocalViewConfiguration.current.longPressTimeoutMillis
          }
          val graphics = LocalGraphicsContext.current
          layers = remember(graphics) { TrackedLayers(graphics) }
          val chrome = rememberFluidChromeController()
          val host = rememberFluidGlassModalHostState()
          val backdrop = rememberGlassBackdrop()
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host, LocalGraphicsContext provides layers) {
            ProvideFluidChrome(chrome, 0.dp, remember { FluidScrollToTopBus() }) {
              Box(Modifier.fillMaxSize()) {
                if (journeys.isEmpty()) {
                  Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
                }
                Box(Modifier.fillMaxSize().fluidChromePages()) {
                  Box(Modifier.align(Alignment.Center)) { content(actions, backdrop) }
                }
                FluidGlassModalHost(host, chrome.activeBackdrop.value ?: backdrop)
              }
            }
          }
        }
      }
      fun frames(count: Int, preview: MutableList<Int>? = null): Peak {
        val peak = Peak()
        repeat(count) {
          val before = FluidGlassDiagnostics.totalCaptures
          compose.mainClock.advanceTimeByFrame()
          shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FrameMillis))
          Snapshot.sendApplyNotifications()
          compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val window = IntSize(view.width, view.height)
            check(window.width > 0 && window.height > 0) { "$label: finestra non posata" }
            display.setPosition(0, 0, window.width, window.height)
            val canvas = display.beginRecording()
            try { view.draw(canvas) } finally { display.endRecording() }
            val logs = ShadowLog.getLogsForTag(FrameTag)
            val events = logs.subList(logIndex, logs.size)
            preview?.add(events.count { " kind=menu.preview.record " in it.msg })
            val captures = captureRecords(events)
            logIndex = logs.size
            peak.observe(window, captures, finishes, layers.sizes(), FluidGlassDiagnostics.totalCaptures - before)
            finishes.clear()
          }
        }
        return peak
      }
      val mount = frames(48)
      assertTrue("$label: serve almeno una lastra Liquid realmente disegnata", mount.liquid > 0 && mount.captures > 0)
      if (journeys.isNotEmpty()) assertTrue("$label: scena di Misure pronta", actions.ready)
      checkPeak("$label/monta", mount)
      checkPeak("$label/fermo", frames(RestFrames))
      for ((phase, millis) in journeys) {
        if (staleReadingBeforePhases) {
          // L'ultima lettura della memoria ha due secondi: la prossima cattura grande deve rileggerla.
          val resources = FluidGlassResources.get(compose.activity)
          val now = android.os.SystemClock.elapsedRealtimeNanos()
          ReflectionHelpers.setField(resources, "allocationReadingAtNs", now - 2_000_000_000L)
        }
        compose.runOnUiThread { scope.launch { checkNotNull(actions.action(phase)) { "$label/$phase" }.invoke() } }
        val preview = if (label == MeasureScenario.ContextMenu.key) mutableListOf<Int>() else null
        // Allineiamo l'inizio dei venti frame di riposo al distacco effettivo, anche a 555 ms.
        val journeyMillis = if (phase == "apre") millis + extraHoldMillis else millis
        val travel = frames(((journeyMillis + FrameMillis - 1) / FrameMillis).toInt(), preview)
        if (preview != null) {
          val total = preview.sum()
          println("Contestuale/$phase (soglia ${expectedLiftMillis ?: "sistema"} ms) record=$total per frame da ${FrameMillis}ms: $preview")
          if (phase == "apre" && expectedLiftMillis != null) {
            val firstCopyMillis = (preview.indexOfFirst { it > 0 } + 1) * FrameMillis
            assertTrue("la copia deve avvenire al distacco: $firstCopyMillis ms",
              firstCopyMillis in expectedLiftMillis - FrameMillis..expectedLiftMillis + FrameMillis)
          }
          if (phase == "apre") assertTrue("pressione reale, registrazioni=$total", total in 1..2)
          else assertEquals("ritorno della riga", 0, total)
        }
        checkPeak("$label/$phase", travel)
        checkPeak("$label/dopo-$phase", frames(RestFrames))
        if (menuText != null) {
          if (phase == "apre") compose.onNodeWithText(menuText).assertExists()
          if (phase == "chiude") compose.onNodeWithText(menuText).assertDoesNotExist()
          val menuCaptures = if (label == MeasureScenario.MenuButton.key) travel.windowedCaptures else travel.menuCaptures
          assertTrue("$label/$phase: finiture=${travel.finishes} catture=$menuCaptures, " +
            "rifiuti=${ShadowLog.getLogsForTag(FrameTag).filter { "kind=glassBudgetDenied" in it.msg }.takeLast(4).map { it.msg }}",
            travel.finishes > 0 && menuCaptures > 0)
          assertEquals(
            "$label/$phase: finitura del menu a piena finestra o senza ritaglio; ripristinare la finestra F7.3b",
            0, if (label == MeasureScenario.MenuButton.key) travel.fullFinishes else travel.unboundedMenuCaptures,
          )
        }
      }
      assertEquals("$label: azioni del copione", null, actions.failure)
    } finally {
      GlassCostLevers.onCapture = previousObserver
      substitutes.forEach(shaderCache::remove)
      display.discardDisplayList()
    }
  }

  private class TrackedLayers(private val delegate: GraphicsContext) : GraphicsContext by delegate {
    private val active = mutableSetOf<GraphicsLayer>()

    override fun createGraphicsLayer(): GraphicsLayer =
      delegate.createGraphicsLayer().also { active.add(it) }

    override fun releaseGraphicsLayer(layer: GraphicsLayer) {
      active.remove(layer)
      delegate.releaseGraphicsLayer(layer)
    }

    fun sizes(): List<IntSize> = active.map { it.size }
  }

  private data class Capture(val size: IntSize, val windowed: Boolean, val fromMenu: Boolean)

  /** Ogni cattura valida dichiara il proprio antenato anche se il padre ha riusato la sua registrazione. */
  private fun captureRecords(logs: List<ShadowLog.LogItem>): List<Capture> = buildList {
    for (entry in logs) {
      val line = entry.msg
      val size = CaptureSize.find(line) ?: continue
      val owner = line.substringAfter(" owner=", "").substringBefore(' ')
      add(Capture(
        IntSize(size.groupValues[1].toInt(), size.groupValues[2].toInt()),
        windowed = " window=" in line && " window=null" !in line,
        fromMenu = owner == "menu.surface",
      ))
    }
  }

  private class Peak {
    var liquid = 0
      private set
    var captures = 0L
      private set
    var finishes = 0
      private set
    var fullFinishes = 0
      private set
    var menuCaptures = 0
      private set
    var unboundedMenuCaptures = 0
      private set
    var windowedCaptures = 0
      private set
    private val nodes = IntArray(FluidGlassNodeKind.entries.size)
    private val layers = IntArray(FluidGlassLayerKind.entries.size)
    private var capturesPerFrame = 0
    private var finishesPerFrame = 0
    private var fullCaptures = 0
    private var widestCapture = 0
    private var tallestCapture = 0
    private var widestFinish = 0
    private var tallestFinish = 0
    private var explicitLayers = 0
    private var explicitFullLayers = 0
    private var widestLayer = 0
    private var tallestLayer = 0

    fun observe(window: IntSize, recorded: List<Capture>, travel: List<IntSize>, sizes: List<IntSize>, delta: Long) {
      val diagnostics = FluidGlassDiagnostics
      diagnostics.liveNodes.forEach { (kind, count) -> nodes[kind.ordinal] = maxOf(nodes[kind.ordinal], count) }
      diagnostics.liveLayers.forEach { (kind, count) -> layers[kind.ordinal] = maxOf(layers[kind.ordinal], count) }
      liquid = maxOf(liquid, diagnostics.liveSurfaces.getValue(FluidGlassModel.Liquid))
      captures += delta
      finishes += travel.size
      capturesPerFrame = maxOf(capturesPerFrame, delta.toInt())
      finishesPerFrame = maxOf(finishesPerFrame, travel.size)
      fullCaptures = maxOf(fullCaptures, recorded.count { it.size.covers(window) })
      fullFinishes = maxOf(fullFinishes, travel.count { it.covers(window) })
      windowedCaptures += recorded.count { it.windowed }
      menuCaptures += recorded.count { it.fromMenu }
      unboundedMenuCaptures += recorded.count { it.fromMenu && (!it.windowed || it.size.covers(window)) }
      explicitLayers = maxOf(explicitLayers, sizes.size)
      explicitFullLayers = maxOf(explicitFullLayers, sizes.count { it.covers(window) })
      for (layer in sizes) {
        widestLayer = maxOf(widestLayer, layer.width)
        tallestLayer = maxOf(tallestLayer, layer.height)
      }
      for (capture in recorded) {
        widestCapture = maxOf(widestCapture, capture.size.width)
        tallestCapture = maxOf(tallestCapture, capture.size.height)
      }
      for (size in travel) {
        widestFinish = maxOf(widestFinish, size.width)
        tallestFinish = maxOf(tallestFinish, size.height)
      }
      assertEquals("ogni cattura valida deve fornire le dimensioni al censimento", delta, recorded.size.toLong())
    }

    fun counters(): Map<String, Int> = linkedMapOf(
      "superfici Liquid" to liquid,
      "nodi vetro/striscia" to nodes[FluidGlassNodeKind.Backdrop.ordinal],
      "nodi ombra" to nodes[FluidGlassNodeKind.Shadow.ordinal],
      "layer cattura" to layers[FluidGlassLayerKind.Capture.ordinal],
      "layer posa" to layers[FluidGlassLayerKind.Placement.ordinal],
      "layer materiale esportato" to layers[FluidGlassLayerKind.Material.ordinal],
      "layer ombra" to layers[FluidGlassLayerKind.Shadow.ordinal],
      "catture per frame" to capturesPerFrame,
      "catture a piena finestra per frame" to fullCaptures,
      "larghezza cattura (px)" to widestCapture,
      "altezza cattura (px)" to tallestCapture,
      "finiture per frame" to finishesPerFrame,
      "larghezza finitura (px)" to widestFinish,
      "altezza finitura (px)" to tallestFinish,
      "finiture a piena finestra per frame" to fullFinishes,
      "catture nella fase" to captures.toInt(),
      "layer espliciti Compose" to explicitLayers,
      "layer espliciti Compose a piena finestra" to explicitFullLayers,
      "larghezza layer espliciti (px)" to widestLayer,
      "altezza layer espliciti (px)" to tallestLayer,
    )

    private fun IntSize.covers(window: IntSize): Boolean = width >= window.width && height >= window.height
  }

  private fun checkPeak(label: String, peak: Peak) {
    val counters = peak.counters()
    val limits = checkNotNull(Ceilings[label]) { "$label: manca la riga in GlassNodeCountTest.Ceilings" }
    check(counters.size == limits.size) { "$label: colonne incoerenti in GlassNodeCountTest.Ceilings" }
    val regressions = counters.entries.zip(limits).mapNotNull { (counter, limit) ->
      val (name, value) = counter
      if (value > limit) "$name $value (tetto $limit)" else null
    }
    assertTrue(
      "$label: ${regressions.joinToString()}. Verificare il costo e, se voluto, aggiornare la riga " +
        "in GlassNodeCountTest.Ceilings. Rilievo completo: $counters",
      regressions.isEmpty(),
    )
  }

  private companion object {
    const val Bare = "senza-vetro"
    const val Slab = "lastra"
    const val FrameTag = "FluidGlassFrame"
    const val FrameMillis = 16L
    const val RestFrames = 20
    val CaptureSize = Regex("""\bkind=capture\b.*\bsize=(\d+) x (\d+)\b""")

    // S4g-bis (b): nei menu col vetro dell'ancora un layer esplicito in piu' per il pannello, lo strato della lente delle voci
    // (`FluidGlassMenuPanel`, `inkLens`) che si sposta dentro la finestra; l'effetto e la sua opacita' stavano sul nodo delle voci.
    // F7.23: `capsula-premuta/tieni` da 129 a 130 catture nella fase (2,2 s). Per esclusione è la velocità filtrata della
    // flessione: senza la tirata verso il dito, e con la deriva di prima, restano 130. La flessione ora cambia con
    // continuità anche dove il dito quasi si ferma, e un vetro che cambia ricattura. Stessa cattura per fotogramma (una,
    // 198 × 94 px).
    // Massimi osservati a 800 × 1280 px con il clock della prova. Ordine di Peak.counters:
    // Liquid/nodi/ombre · layer cattura/posa/materiale/ombra · catture/frame e a piena finestra ·
    // cattura L/H · finiture/frame, L/H e a piena finestra · catture totali · layer espliciti e
    // a piena finestra · layer espliciti L/H. Le taglie sono in pixel.
    val Ceilings = """
      tasto/monta                  1/1/1   1/1/0/1   1/0     46/46      0/0/0/0         1      0/0    0/0
      tasto/fermo                  1/1/1   1/1/0/1   0/0     0/0        0/0/0/0         0      0/0    0/0
      capsula-premuta/monta        1/2/1   2/2/0/1   2/0     800/110    0/0/0/0         2      3/0    0/0
      capsula-premuta/fermo        1/2/1   2/2/0/1   1/0     158/54     0/0/0/0         1      3/0    0/0
      capsula-premuta/tieni        1/2/1   2/2/0/1   1/0     198/94     0/0/0/0         130    3/0    0/0
      capsula-premuta/dopo-tieni   1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      3/0    0/0
      gruppo/monta                 1/1/0   1/1/0/0   1/0     370/258    0/0/0/0         1      0/0    0/0
      gruppo/fermo                 1/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      0/0    0/0
      menu-tasto/monta             1/1/1   1/1/0/1   1/0     95/46      0/0/0/0         1      2/0    0/0
      menu-tasto/fermo             1/1/1   1/1/0/1   0/0     0/0        0/0/0/0         0      2/0    0/0
      menu-tasto/apre              3/3/2   3/3/0/2   2/0     652/684    1/652/684/0     41     4/1    800/1280
      menu-tasto/dopo-apre         3/3/2   3/3/0/2   0/0     0/0        0/0/0/0         0      3/1    800/1280
      menu-tasto/chiude            3/3/2   3/3/0/2   2/0     652/684    1/652/684/0     17     4/1    800/1280
      menu-tasto/dopo-chiude       1/1/1   1/1/0/1   0/0     0/0        0/0/0/0         0      2/1    800/1280
      menu-gruppo/monta            2/4/1   4/4/0/1   2/1     800/1280   0/0/0/0         4      8/0    800/110
      menu-gruppo/fermo            1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      6/0    800/110
      menu-gruppo/apre             3/5/2   5/5/0/2   3/0     620/992    1/476/428/0     59     11/1   800/1280
      menu-gruppo/dopo-apre        3/5/2   5/5/0/2   1/0     244/44     0/0/0/0         6      10/1    800/1280
      menu-gruppo/chiude           3/5/2   5/5/0/2   2/0     476/428    1/476/428/0     17     11/1   800/1280
      menu-gruppo/dopo-chiude      1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      6/0    800/110
      contestuale/monta            1/3/0   3/3/0/0   2/1     800/1280   0/0/0/0         3      6/0    0/0
      contestuale/fermo            0/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      4/0    0/0
      contestuale/apre             3/5/1   5/5/0/1   4/1     812/1292   1/812/1292/0    64     9/1    800/1280
      contestuale/dopo-apre        3/5/1   5/5/0/1   0/0     0/0        0/0/0/0         0      7/1    800/1280
      contestuale/chiude           3/5/1   5/5/0/1   3/1     812/1292   2/812/1292/0    20     9/1    800/1280
      contestuale/dopo-chiude      0/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      4/0    760/72
      foglio-trascinato/monta      1/2/1   2/2/0/1   2/0     800/486    0/0/0/0         27     6/0    0/0
      foglio-trascinato/fermo      1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      6/0    0/0
      foglio-trascinato/su         1/2/1   2/2/0/1   1/0     452/931    0/0/0/0         38     6/0    0/0
      foglio-trascinato/dopo-su    1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      6/0    0/0
      foglio-trascinato/giu        1/2/1   2/2/0/1   1/0     452/927    0/0/0/0         49     6/0    0/0
      foglio-trascinato/dopo-giu   0/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      3/0    0/0
      popover/monta                1/2/0   2/2/0/0   2/1     812/1292   1/812/1292/1    4      3/0    406/646
      popover/fermo                1/2/0   2/2/0/0   0/0     0/0        0/0/0/0         0      2/0    0/0
      tab-bar/monta                2/3/2   3/3/0/2   3/0     199/52     0/0/0/0         41     2/0    263/52
      tab-bar/fermo                2/3/2   3/3/0/2   0/0     0/0        0/0/0/0         0      2/0    263/52
      otto-lastre/monta            8/8/8   8/8/0/1   8/0     274/102    0/0/0/0         8      1/0    0/0
      otto-lastre/fermo            8/8/8   8/8/0/1   0/0     0/0        0/0/0/0         0      1/0    0/0
      otto-lastre/su               8/8/8   8/8/0/1   0/0     0/0        0/0/0/0         0      1/0    0/0
      otto-lastre/dopo-su          8/8/8   8/8/0/1   0/0     0/0        0/0/0/0         0      1/0    0/0
      otto-lastre/giu              8/8/8   8/8/0/1   0/0     0/0        0/0/0/0         0      1/0    0/0
      otto-lastre/dopo-giu         8/8/8   8/8/0/1   0/0     0/0        0/0/0/0         0      1/0    0/0
    """.trimIndent().lineSequence().associate { line ->
      val (label, limits) = line.split(Regex("\\s+"), limit = 2)
      label to limits.split(Regex("[/\\s]+")).map(String::toInt)
    }

    /** I nodi del vetro di prima che stanno nei file di Kyant e tengono un layer ciascuno. */
    val KyantClassicNodes = setOf("InnerShadowElement", "ShadowElement", "HighlightElement")
  }
}
