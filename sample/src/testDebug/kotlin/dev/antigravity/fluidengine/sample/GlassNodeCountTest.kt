@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
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
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.field.GlassCostLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
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
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi")
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
  @Test fun menuContestuale() = checkScenario(MeasureScenario.ContextMenu, menuText = "Condividi")

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
    content: @Composable (ScenarioActions, GlassBackdropState) -> Unit,
  ) {
    val actions = ScenarioActions()
    val display = RenderNode("F7.6 • finestra")
    val previousObserver = GlassCostLevers.onCapture
    val finishes = mutableListOf<IntSize>()
    lateinit var scope: CoroutineScope
    lateinit var layers: TrackedLayers
    var logIndex = 0
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
      fun frames(count: Int): Peak {
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
            val captures = captureRecords(logs.subList(logIndex, logs.size))
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
        compose.runOnUiThread { scope.launch { checkNotNull(actions.action(phase)) { "$label/$phase" }.invoke() } }
        val travel = frames(((millis + FrameMillis - 1) / FrameMillis).toInt())
        checkPeak("$label/$phase", travel)
        checkPeak("$label/dopo-$phase", frames(RestFrames))
        if (menuText != null) {
          if (phase == "apre") compose.onNodeWithText(menuText).assertExists()
          if (phase == "chiude") compose.onNodeWithText(menuText).assertDoesNotExist()
          val menuCaptures = if (label == MeasureScenario.MenuButton.key) travel.windowedCaptures else travel.menuCaptures
          assertTrue("$label/$phase: nessuna finitura osservata nel viaggio", travel.finishes > 0 && menuCaptures > 0)
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

  /** I marker distinguono la finitura del menu dalle catture della card e del velo. */
  private fun captureRecords(logs: List<ShadowLog.LogItem>): List<Capture> = buildList {
    var drawingMenu = false
    for (entry in logs) {
      val line = entry.msg
      if (" kind=menu.surface " in line) {
        drawingMenu = " size=" in line
      }
      val size = CaptureSize.find(line) ?: continue
      add(Capture(
        IntSize(size.groupValues[1].toInt(), size.groupValues[2].toInt()),
        windowed = !line.endsWith("window=null"),
        fromMenu = drawingMenu,
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

    // Massimi osservati a 800 × 1280 px con il clock della prova. Ordine di Peak.counters:
    // Liquid/nodi/ombre · layer cattura/posa/materiale/ombra · catture/frame e a piena finestra ·
    // cattura L/H · finiture/frame, L/H e a piena finestra · catture totali · layer espliciti e
    // a piena finestra · layer espliciti L/H. Le taglie sono in pixel.
    val Ceilings = """
      tasto/monta                  1/1/1   1/1/0/1   1/0     46/46      0/0/0/0         1      0/0    0/0
      tasto/fermo                  1/1/1   1/1/0/1   0/0     0/0        0/0/0/0         0      0/0    0/0
      capsula-premuta/monta        1/2/1   2/2/0/1   2/0     800/110    0/0/0/0         2      3/0    0/0
      capsula-premuta/fermo        1/2/1   2/2/0/1   1/0     158/54     0/0/0/0         1      3/0    0/0
      capsula-premuta/tieni        1/2/1   2/2/0/1   1/0     198/94     0/0/0/0         129    3/0    0/0
      capsula-premuta/dopo-tieni   1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      3/0    0/0
      gruppo/monta                 1/1/0   1/1/0/0   1/0     370/258    0/0/0/0         1      0/0    0/0
      gruppo/fermo                 1/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      0/0    0/0
      menu-tasto/monta             1/1/1   1/1/0/1   1/0     95/46      0/0/0/0         1      2/0    0/0
      menu-tasto/fermo             1/1/1   1/1/0/1   0/0     0/0        0/0/0/0         0      2/0    0/0
      menu-tasto/apre              3/3/2   3/3/0/2   2/0     652/684    1/652/684/0     41     3/1    800/1280
      menu-tasto/dopo-apre         3/3/2   3/3/0/2   0/0     0/0        0/0/0/0         0      2/1    800/1280
      menu-tasto/chiude            3/3/2   3/3/0/2   2/0     652/684    1/652/684/0     17     3/1    800/1280
      menu-tasto/dopo-chiude       1/1/1   1/1/0/1   0/0     0/0        0/0/0/0         0      2/1    800/1280
      menu-gruppo/monta            2/4/1   4/4/0/1   2/1     800/1280   0/0/0/0         4      8/0    800/110
      menu-gruppo/fermo            1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      6/0    800/110
      menu-gruppo/apre             3/5/2   5/5/0/2   3/0     620/992    1/476/428/0     59     10/1   800/1280
      menu-gruppo/dopo-apre        3/5/2   5/5/0/2   1/0     244/44     0/0/0/0         6      9/1    800/1280
      menu-gruppo/chiude           3/5/2   5/5/0/2   2/0     476/428    1/476/428/0     17     10/1   800/1280
      menu-gruppo/dopo-chiude      1/2/1   2/2/0/1   0/0     0/0        0/0/0/0         0      6/0    800/110
      contestuale/monta            1/3/0   3/3/0/0   2/1     800/1280   0/0/0/0         3      6/0    0/0
      contestuale/fermo            0/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      4/0    0/0
      contestuale/apre             3/5/1   5/5/0/1   4/1     812/1292   1/812/1292/0    64     8/1    800/1280
      contestuale/dopo-apre        3/5/1   5/5/0/1   0/0     0/0        0/0/0/0         0      6/1    800/1280
      contestuale/chiude           3/5/1   5/5/0/1   3/1     812/1292   2/812/1292/0    20     8/1    800/1280
      contestuale/dopo-chiude      0/1/0   1/1/0/0   0/0     0/0        0/0/0/0         0      4/0    760/72
      foglio-trascinato/monta      1/2/1   2/2/0/1   2/0     800/486    0/0/0/0         25     6/0    0/0
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
