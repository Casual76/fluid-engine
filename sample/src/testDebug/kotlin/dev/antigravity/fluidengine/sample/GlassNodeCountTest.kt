package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Quanti nodi e quanti layer costa una lastra ferma (piano «iOS in Android», 3.3, 4.4, F1A-f), contati in due modi
 * che non dipendono l'uno dall'altro:
 *  - **dalla catena dei modificatori** del nodo di layout (`LayoutInfo.getModifierInfo()`): un elemento per nodo, e
 *    il layer di posa dove c'è. Si confronta una scatola con il vetro e la stessa scatola senza;
 *  - **dalla diagnostica dell'engine** (`FluidGlassDiagnostics.liveNodes` e `liveLayers`), che conta dove i layer
 *    nascono e si rilasciano. Sotto Robolectric conta ciò che si alloca all'aggancio dei nodi (cattura, ombra) e
 *    alla prima posa; il gemello sul dispositivo è `GlassNodeCountInstrumentedTest`.
 *
 * Con i verdetti forzati (il modello non dipende da ciò che Robolectric fa di un `RuntimeShader`) e senza disegnare:
 * i nodi e i layer esistono dall'aggancio.
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
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class GlassNodeCountTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
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

  private companion object {
    const val Bare = "senza-vetro"
    const val Slab = "lastra"

    /** I nodi del vetro di prima che stanno nei file di Kyant e tengono un layer ciascuno. */
    val KyantClassicNodes = setOf("InnerShadowElement", "ShadowElement", "HighlightElement")
  }
}
