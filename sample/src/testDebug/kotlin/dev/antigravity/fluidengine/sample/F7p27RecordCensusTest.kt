@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.measure.ScenarioActions
import dev.antigravity.fluidengine.sample.measure.ScenarioScene
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/**
 * F7.27, il censimento delle registrazioni: quali liste di disegno si registrano di nuovo a ogni fotogramma mentre il
 * gruppo della barra in basso diventa menu (prima apertura e a caldo) e mentre il foglio sale.
 *
 * Sul Tab S9 una lista registrata di nuovo vuol dire che HWUI rifà il suo layer e, se è la pagina, tutte le copie
 * sfocate di chi la legge (velo, finestra del menu, barre, strisce). Qui, prima di ogni disegno, si leggono i layer di
 * Compose da rifare ([F7p27Census]), con la taglia e i modificatori del nodo. Non misura millisecondi: dice dove
 * stanno le registrazioni che il tablet paga. Le righe `F727` finiscono nell'uscita della prova (XML dei risultati).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w411dp-h891dp-420dpi",
  application = GlassBenchDevice::class,
  shadows = [GlassBenchHeap::class],
)
class F7p27RecordCensusTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun groupBarFirstAndWarm() = withLargeBodies {
    census(MeasureScenario.MenuGroupBar, listOf("apre" to 70, "chiude" to 70, "apre" to 70, "chiude" to 70, "apre" to 70))
  }

  @Test fun sheetRise() = census(MeasureScenario.SheetRise, listOf("sale" to 80, "scende" to 70, "sale" to 80))

  /** Il compilatore Skia di Robolectric rifiuta i corpi a otto pezzi: come in `GlassCostScenarioTest`, cicli accorciati. */
  private fun withLargeBodies(block: () -> Unit) {
    val cache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(SharedRuntimeShaders::class.java, "shaders")
    val installed = mutableListOf<String>()
    try {
      for (source in listOf(GlassShaderSources.Body8, GlassShaderSources.BodyFilms8)) {
        for (capacity in 8 downTo 1) {
          val program = source.text.replace("i < 8; ++i", "i < $capacity; ++i")
          try {
            val shader = RuntimeShader(program)
            if (capacity < 8 && cache.putIfAbsent(source.text, shader) == null) installed += source.text
            break
          } catch (error: IllegalArgumentException) {
            if (capacity == 1 || error.message?.contains("program is too large") != true) throw error
          }
        }
      }
      block()
    } finally {
      installed.forEach(cache::remove)
    }
  }

  private fun census(scene: MeasureScenario, steps: List<Pair<String, Int>>) {
    val diagnostics = FluidGlassDiagnostics
    val enabled = diagnostics.enabled
    val warm = diagnostics.warmUpEnabled
    diagnostics.resetForTesting()
    diagnostics.enabled = true
    diagnostics.warmUpEnabled = false
    diagnostics.forceShaderVerdictsForTesting(true)
    val actions = ScenarioActions()
    val display = RenderNode("F7.27 censimento")
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
      fun frame(): List<F7p27Record> {
        val dirty = LinkedHashMap<Any, F7p27Record>()
        F7p27Census.changed.clear()
        compose.mainClock.advanceTimeByFrame()
        Snapshot.sendApplyNotifications()
        compose.runOnUiThread { F7p27Census.dirtyLayers(compose.activity.window.decorView, dirty) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8))
        Snapshot.sendApplyNotifications()
        compose.runOnUiThread {
          val view = compose.activity.window.decorView
          F7p27Census.dirtyLayers(view, dirty)
          display.setPosition(0, 0, view.width, view.height)
          val canvas = display.beginRecording()
          try { view.draw(canvas) } finally { display.endRecording() }
        }
        return dirty.values.toList()
      }
      val applies = Snapshot.registerApplyObserver { changed, _ -> F7p27Census.changed.addAll(changed) }
      repeat(60) { frame() }
      assertTrue("scena pronta: $scene", actions.ready)
      assertTrue("nessun layer trovato: i nomi interni di Compose sono cambiati", F7p27Census.layersSeen > 0)
      val rest = (0 until 12).map { frame() }
      println("F727 $scene riposo: registrazioni per fotogramma ${rest.map { it.size }}")
      var cycle = 0
      for ((phase, frames) in steps) {
        if (phase == steps.first().first) cycle++
        compose.runOnUiThread { ui.launch { checkNotNull(actions.action(phase)).invoke() } }
        val all = (0 until frames).map { frame() }
        val label = "$scene ciclo=$cycle fase=$phase"
        all.forEachIndexed { index, records ->
          val area = records.sumOf { it.width.toLong() * it.height }
          if (F7p27Census.moved.isNotEmpty()) println("F727 MOSSI $label f=$index ${F7p27Census.moved.distinct().take(6).joinToString(" | ")}")
          F7p27Census.moved.clear()
          if (F7p27Census.recomposed.isNotEmpty()) println("F727 RICOMPOSTI $label f=$index ${F7p27Census.recomposed.distinct().take(8).joinToString(" | ")}")
          F7p27Census.recomposed.clear()
          records.filter { it.culprits.isNotEmpty() }.forEach { record ->
            println("F727 COLPA $label f=$index ${record.width}x${record.height} ${record.signature.take(60)} <- ${record.culprits}")
          }
          println("F727 $label f=$index n=${records.size} area=${area / 1000}k " +
            records.groupingBy { "${it.width}x${it.height} ${it.signature}" }.eachCount().entries
              .sortedByDescending { it.value }.joinToString(" | ") { "${it.value}× ${it.key}" })
        }
        // In sintesi: per ogni registratore, in quanti fotogrammi del tratto si è registrato di nuovo.
        val bySignature = HashMap<String, Int>()
        all.forEach { records -> records.map { "${it.width}x${it.height} ${it.signature}" }.toSet().forEach { bySignature.merge(it, 1, Int::plus) } }
        println("F727 SINTESI $label fotogrammi=$frames " +
          bySignature.entries.sortedByDescending { it.value }.take(40).joinToString(" || ") { "${it.value}/$frames ${it.key}" })
        assertNull("gesto $phase", actions.failure)
      }
      applies.dispose()
    } finally {
      display.discardDisplayList()
      diagnostics.enabled = enabled
      diagnostics.warmUpEnabled = warm
      diagnostics.resetForTesting()
    }
  }
}

/** Un layer di Compose che si registrerà di nuovo: la sua taglia e i modificatori del nodo che lo porta. */
internal data class F7p27Record(val width: Int, val height: Int, val signature: String, val culprits: String = "")

/**
 * Il censimento per riflessione: si scorre l'albero dei `LayoutNode` di ogni `AndroidComposeView` della finestra e, per
 * ogni coordinatore con un layer, si legge `isDirty` (il layer rifarà la sua lista di disegno al prossimo disegno). I nomi
 * interni sono quelli di Compose 1.10/1.11; se cambiano la prova lo dice (nessun layer trovato).
 */
internal object F7p27Census {
  private fun call(target: Any, name: String): Any? =
    target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(target)

  private fun field(target: Any, name: String): Any? {
    var type: Class<*>? = target.javaClass
    while (type != null) {
      type.declaredFields.firstOrNull { it.name == name }?.let { it.isAccessible = true; return it.get(target) }
      type = type.superclass
    }
    return null
  }

  var layersSeen = 0

  /** Gli stati cambiati in questo fotogramma (dall'osservatore delle applicazioni). */
  val changed: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

  private var scopeMaps: List<Any> = emptyList()

  /** Gli stati che il disegno di [scope] ha letto, dall'osservatore delle letture della vista. */
  private fun reads(scope: Any): List<Any> {
    val out = ArrayList<Any>()
    for (map in scopeMaps) {
      val scopeToValues = field(map, "scopeToValues") ?: continue
      val values = scopeToValues.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 1 }
        ?.invoke(scopeToValues, scope) ?: continue
      val forEachKey = values.javaClass.methods.firstOrNull { it.name == "forEachKey" } ?: continue
      forEachKey.invoke(values, { key: Any? -> if (key != null) out += key; Unit })
    }
    return out
  }

  fun dirtyLayers(view: android.view.View, into: MutableMap<Any, F7p27Record>) {
    if (view.javaClass.simpleName == "AndroidComposeView") {
      val observer = call(view, "getSnapshotObserver")?.let { field(it, "observer") }
      val vector = observer?.let { field(it, "observedScopeMaps") }
      scopeMaps = if (vector == null) emptyList() else {
        val size = call(vector, "getSize") as? Int ?: 0
        @Suppress("UNCHECKED_CAST")
        val content = field(vector, "content") as? Array<Any?> ?: emptyArray()
        content.take(size).filterNotNull()
      }
      call(view, "getRoot")?.let { visit(it, into, 0) }
    }
    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) dirtyLayers(view.getChildAt(i), into)
  }

  /** Posizione e misura dei nodi dentro la barra al fotogramma prima: chi si muove fa rifare il layer che lo contiene. */
  private var barPlaces = HashMap<Any, String>()
  val moved = ArrayList<String>()
  private var barElements = HashMap<Any, List<Int>>()
  private val nextElements = HashMap<Any, List<Int>>()
  val recomposed = ArrayList<String>()

  private fun places(node: Any, depth: Int, out: MutableMap<Any, String>) {
    val coordinates = call(node, "getCoordinates") as? androidx.compose.ui.layout.LayoutCoordinates
    if (coordinates != null && coordinates.isAttached) {
      val at = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
      out[node] = "${at.x.toInt()},${at.y.toInt()} ${coordinates.size.width}x${coordinates.size.height} d$depth ${describe(node, depth)}"
    }
    // Le identità degli elementi dei modificatori: cambiano se il nodo si è ricomposto con elementi nuovi.
    @Suppress("UNCHECKED_CAST")
    val infos = call(node, "getModifierInfo") as? List<Any> ?: emptyList()
    val elements = infos.mapNotNull { info -> call(info, "getModifier") }
    val ids = elements.map { System.identityHashCode(it) }
    val previous = barElements[node]
    if (previous != null && previous != ids) {
      val changed = elements.filterIndexed { i, _ -> i >= previous.size || previous[i] != ids[i] }
        .map { it.javaClass.name.substringAfterLast('.') }.distinct()
      recomposed += "d$depth ${describe(node, depth).take(50)}: $changed"
    }
    nextElements[node] = ids
    @Suppress("UNCHECKED_CAST")
    val children = call(node, "getChildren\$ui") as? List<Any> ?: emptyList()
    for (child in children) places(child, depth + 1, out)
  }

  private fun visit(node: Any, into: MutableMap<Any, F7p27Record>, depth: Int) {
    if (describe(node, depth).contains("EnterExitTransitionElement")) {
      val now = HashMap<Any, String>()
      nextElements.clear()
      places(node, depth, now)
      barElements = HashMap(nextElements)
      if (barPlaces.isNotEmpty()) for ((key, place) in now) {
        val before = barPlaces[key]
        if (before != null && before != place) moved += "$before -> ${place.substringBefore(" d")}"
      }
      barPlaces = now
    }
    val inner = call(node, "getInnerCoordinator\$ui")
    var coordinator = call(node, "getOuterCoordinator\$ui")
    while (coordinator != null) {
      val layer = call(coordinator, "getLayer")
      if (layer != null) {
        layersSeen++
        if (field(layer, "isDirty") == true && layer !in into) {
          val size = call(coordinator, "getSize-YbymL2g") as? Long ?: 0L
          val culprits = (reads(coordinator) + reads(layer)).filter { it in changed }
            .joinToString(" ; ") { "${it.toString().take(70)}#${System.identityHashCode(it)}" }
          into[layer] = F7p27Record((size shr 32).toInt(), (size and 0xffffffffL).toInt(), describe(node, depth), culprits)
        }
      }
      if (coordinator === inner) break
      coordinator = call(coordinator, "getWrapped\$ui")
    }
    @Suppress("UNCHECKED_CAST")
    val children = call(node, "getChildren\$ui") as? List<Any> ?: emptyList()
    for (child in children) visit(child, into, depth + 1)
  }

  private fun describe(node: Any, depth: Int): String {
    @Suppress("UNCHECKED_CAST")
    val infos = call(node, "getModifierInfo") as? List<Any> ?: emptyList()
    val names = infos.mapNotNull { info -> call(info, "getModifier")?.javaClass?.name?.substringAfterLast('.') }
      .filter { !it.startsWith("Padding") && !it.startsWith("Size") && !it.startsWith("Fill") && !it.startsWith("Wrap") }
      .distinct().take(6)
    return "d$depth ${names.joinToString("+")}"
  }
}
