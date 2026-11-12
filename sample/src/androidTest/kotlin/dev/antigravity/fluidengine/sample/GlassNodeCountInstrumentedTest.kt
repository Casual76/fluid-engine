package dev.antigravity.fluidengine.sample

import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.util.IdentityHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il gemello sul dispositivo di `GlassNodeCountTest` (sample/src/testDebug): la stessa lastra ferma, con il
 * compilatore vero e **disegnata**, così i layer che nascono al primo disegno ci sono. Gli stessi numeri per `Liquid`:
 * 4 nodi e 3 layer (cattura, posa, ombra), senza ombra 3 e 2. Per `Classic` i nodi sono 5 (tre stanno nei file di Kyant,
 * che la diagnostica non conta ancora: si contano dalla catena), ma i layer che la diagnostica **legge** sono 2 (cattura e
 * posa): il «5 e 5» del gemello in Robolectric deduce gli altri tre dal codice dei nodi di Kyant, qui non si leggono.
 *
 * Si lancia nelle sessioni sul tablet (S2), con il comando nel rapporto di F1A.7.
 */
@RunWith(AndroidJUnit4::class)
class GlassNodeCountInstrumentedTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    assumeTrue("Il materiale nuovo esiste da Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
  }

  private val shown = mutableStateOf(true)

  @Composable
  private fun Scene(role: GlassRole, model: FluidGlassModel) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidGlassModel provides model) {
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
                  ),
              )
            }
          }
        }
      }
    }
  }

  private fun chainOf(tag: String): Pair<Int, Int> {
    val infos = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.getModifierInfo()
    val layers = IdentityHashMap<Any, Unit>()
    for (info in infos) info.extra?.let { layers[it] = Unit }
    return infos.size to layers.size
  }

  private fun counted(
    before: Map<FluidGlassLayerKind, Int>,
    beforeNodes: Map<FluidGlassNodeKind, Int>,
  ): Pair<Map<FluidGlassNodeKind, Int>, Map<FluidGlassLayerKind, Int>> =
    FluidGlassDiagnostics.liveNodes.mapValues { (k, v) -> v - beforeNodes.getValue(k) }.filterValues { it != 0 } to
      FluidGlassDiagnostics.liveLayers.mapValues { (k, v) -> v - before.getValue(k) }.filterValues { it != 0 }

  private fun measure(label: String, role: GlassRole, model: FluidGlassModel): Pair<Int, Pair<Map<FluidGlassNodeKind, Int>, Map<FluidGlassLayerKind, Int>>> {
    val beforeLayers = FluidGlassDiagnostics.liveLayers
    val beforeNodes = FluidGlassDiagnostics.liveNodes
    compose.setContent { Scene(role, model) }
    compose.waitForIdle()
    // Un fotogramma disegnato: i layer del primo disegno esistono.
    compose.mainClock.advanceTimeByFrame()
    compose.waitForIdle()
    val (bareNodes, bareLayers) = chainOf(Bare)
    val (slabNodes, slabLayers) = chainOf(Slab)
    val nodes = slabNodes - bareNodes
    val census = counted(beforeLayers, beforeNodes)
    Log.i(
      Tag,
      "$label: $nodes nodi, ${slabLayers - bareLayers} layer di posa dalla catena; diagnostica nodi ${census.first}, " +
        "layer ${census.second}; superfici ${FluidGlassDiagnostics.liveSurfaces}; verdetti ${FluidGlassDiagnostics.shaderVerdicts.keys}",
    )
    compose.runOnIdle { shown.value = false }
    compose.waitForIdle()
    assertEquals("$label: i layer non tornano", beforeLayers, FluidGlassDiagnostics.liveLayers)
    assertEquals("$label: i nodi non tornano", beforeNodes, FluidGlassDiagnostics.liveNodes)
    return nodes to census
  }

  /** Su un dispositivo che rifiuta la lastra il vetro resta `Classic`: lo dice `GlassShaderCompileInstrumentedTest`. */
  private fun assumeTheSlabCompiles() {
    val verdicts = FluidGlassDiagnostics.shaderVerdicts
    assumeTrue(
      "Il compilatore di questo dispositivo ha rifiutato la lastra: $verdicts",
      verdicts.containsKey("GlassBody1ShaderString") && verdicts["GlassBody1ShaderString"] == null &&
        verdicts["GlassLight1ShaderString"] == null,
    )
  }

  @Test
  fun aRestingLiquidSlabHasFourNodesAndThreeLayers() {
    val (nodes, census) = measure("Liquid", GlassRole.Floating, FluidGlassModel.Liquid)
    assumeTheSlabCompiles()
    assertEquals(5, nodes) // 4.0 (F2.3): piu' la sonda dello sfondo
    assertEquals(mapOf(FluidGlassNodeKind.Backdrop to 1, FluidGlassNodeKind.Shadow to 1), census.first)
    assertEquals(
      mapOf(FluidGlassLayerKind.Capture to 1, FluidGlassLayerKind.Placement to 1, FluidGlassLayerKind.Shadow to 1),
      census.second,
    )
  }

  @Test
  fun aLiquidSlabWithoutShadowHasThreeNodesAndTwoLayers() {
    val (nodes, census) = measure("Liquid senza ombra", GlassRole.Content, FluidGlassModel.Liquid)
    assumeTheSlabCompiles()
    assertEquals(4, nodes) // 4.0 (F2.3): piu' la sonda dello sfondo
    assertEquals(mapOf(FluidGlassNodeKind.Backdrop to 1), census.first)
    assertEquals(mapOf(FluidGlassLayerKind.Capture to 1, FluidGlassLayerKind.Placement to 1), census.second)
  }

  @Test
  fun aRestingClassicSlabHasFiveNodes() {
    val (nodes, census) = measure("Classic", GlassRole.Floating, FluidGlassModel.Classic)
    assertEquals(5, nodes)
    // Posa e cattura; i tre layer dei nodi di Kyant (anello, ombra, ombra interna) la diagnostica non li vede ancora.
    assertEquals(mapOf(FluidGlassNodeKind.Backdrop to 1), census.first)
    assertEquals(mapOf(FluidGlassLayerKind.Capture to 1, FluidGlassLayerKind.Placement to 1), census.second)
  }

  private companion object {
    const val Tag = "GlassNodeCount"
    const val Bare = "senza-vetro"
    const val Slab = "lastra"
  }
}
