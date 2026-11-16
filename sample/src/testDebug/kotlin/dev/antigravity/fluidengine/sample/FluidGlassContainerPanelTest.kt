package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNested
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
 * Il pezzo-pannello del piano flottante nell'albero (4.0, piano «iOS in Android» F3.2e): `fluidGlassPanel` dentro un
 * `FluidGlassContainer` è un pezzo che esporta il suo materiale e dichiara se è un grande; i vetri dentro un
 * `FluidGlassNested` sono vetro annidato (non pezzi) e prendono il lato del contenitore; `propagateMinConstraints` fa
 * arrivare al vetro un `weight` e una misura imposta, e di serie il layout di chi sta dentro non cambia.
 *
 * Non prova come si vede: Robolectric non disegna. L'aspetto sul lato del tema è lo stesso di `glassSurface` per
 * costruzione degli ingressi (`FluidGlassContainerPanelTest` dell'engine); il giro e lo scurimento si guardano sul
 * tablet (`artifacts/liquid-glass/sessioni/f3-2e/riprese.md`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidGlassContainerPanelTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  /** Fa passare [ms] di tempo finto a fotogrammi da 16 ms, col filo principale al passo. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  /** Una pagina con una sorgente da rifrangere e, sopra, [content]. */
  private fun scene(content: @Composable (GlassBackdropState) -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
          Box(Modifier.padding(24.dp)) { content(backdrop) }
        }
      }
    }
    compose.waitForIdle()
    advance(200)
  }

  private val rest get() = FluidGlassContainerDiagnostics.lastRest

  private val SheetShape = ContinuousCornerShape(FluidRadius.Sheet)

  @Test
  fun `un pannello nel contenitore e' un pezzo che esporta il suo materiale, e un Modal e' un grande`() {
    scene { backdrop ->
      val tint = GlassDefaults.modalTint()
      FluidGlassContainer(propagateMinConstraints = true) {
        Column(Modifier.width(300.dp).fluidGlassPanel(backdrop, tint, SheetShape, GlassRole.Modal).padding(16.dp)) {
          Text("Domani c'è sole")
        }
      }
    }
    assertEquals(rest.toString(), 1, rest.size)
    assertTrue("il pannello esporta: $rest", rest.single().exports)
    assertEquals("300 dp a xhdpi", 600f, rest.single().bounds.width, 0.5f)
    assertTrue("un Modal scurisce in continuo, come i fogli", FluidGlassContainerDiagnostics.lastRestLarge)
    assertEquals("i pezzi non sondano: la sonda è del contenitore", 1, FluidGlassProbeDiagnostics.liveProbes)
  }

  @Test
  fun `una barra Modal alta una riga dichiara di essere un piccolo`() {
    // Una barra `Modal` alta una riga: gira.
    scene { backdrop ->
      val tint = GlassDefaults.modalTint()
      FluidGlassContainer {
        Box(Modifier.width(300.dp).fluidGlassPanel(backdrop, tint, SheetShape, GlassRole.Modal, large = false)) {
          Text("Chiedi qualcosa", Modifier.padding(12.dp))
        }
      }
    }
    assertFalse(FluidGlassContainerDiagnostics.lastRestLarge)
  }

  @Test
  fun `un dock Floating largo dichiara di essere un grande`() {
    // Scurisce, anche se un `Floating` di serie girerebbe.
    scene { backdrop ->
      val tint = GlassDefaults.floatingTint()
      FluidGlassContainer {
        Box(Modifier.width(300.dp).fluidGlassPanel(backdrop, tint, SheetShape, GlassRole.Floating, large = true)) {
          Text("Dock", Modifier.padding(12.dp))
        }
      }
    }
    assertTrue(FluidGlassContainerDiagnostics.lastRestLarge)
  }

  @Test
  fun `i vetri dentro FluidGlassNested sono annidati nel pannello, non pezzi, e ne prendono il lato`() {
    var outer: FluidGlassAppearance? = null
    var nested: FluidGlassAppearance? = null
    scene { backdrop ->
      val tint = GlassDefaults.modalTint()
      val ns = rememberFluidGlassNamespace()
      FluidGlassContainer {
        outer = LocalFluidGlassAppearance.current
        Column(Modifier.width(300.dp).fluidGlassId("card", ns).fluidGlassPanel(backdrop, tint, SheetShape, GlassRole.Modal)) {
          FluidGlassNested {
            nested = LocalFluidGlassAppearance.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FluidGlassIconButton(onClick = {}) {}
              Box(Modifier.size(44.dp).fluidGlass(backdrop = backdrop))
            }
          }
        }
      }
    }
    assertEquals("solo il pannello è un pezzo: $rest", listOf<Any?>("card"), rest.map { it.id })
    assertEquals("un grappolo di uno", 1, rest.single().clusterSize)
    assertNotNull(outer)
    assertSame("il vetro annidato vede il lato del contenitore", outer, nested)
    assertEquals("nessun avviso d'ordine", 0L, FluidGlassContainerDiagnostics.totalOrderWarnings)
  }

  @Test
  fun `senza FluidGlassNested i vetri del contenuto sono fratelli del pannello, come prima`() {
    scene { backdrop ->
      val tint = GlassDefaults.modalTint()
      FluidGlassContainer {
        Column(Modifier.width(300.dp).fluidGlassPanel(backdrop, tint, SheetShape, GlassRole.Modal)) {
          Box(Modifier.size(44.dp).fluidGlass(backdrop = backdrop))
        }
      }
    }
    assertEquals("il pannello e il vetro del contenuto: due pezzi, un grappolo ($rest)", 2, rest.size)
    assertTrue(rest.toString(), rest.all { it.clusterSize == 2 })
    assertEquals("esporta solo il pannello", 1, rest.count { it.exports })
  }

  @Test
  fun `il materiale dell'app passa al pezzo, e un fluidGlass non esporta`() {
    scene { backdrop ->
      val tint = GlassDefaults.floatingTint()
      val material = rememberGlassBackdrop()
      FluidGlassContainer(spacing = 0.dp) {
        Column {
          Box(Modifier.size(300.dp, 96.dp).fluidGlassPanel(backdrop, tint, SheetShape, exports = material))
          Spacer(Modifier.size(40.dp))
          Box(Modifier.size(44.dp).fluidGlass(backdrop = backdrop))
        }
      }
    }
    assertEquals(rest.toString(), 2, rest.size)
    assertEquals("il pannello esporta, il vetro dei controlli no: $rest", listOf(true, false), rest.sortedBy { it.bounds.top }.map { it.exports })
  }

  @Test
  fun `un pezzo solo riceve dal contenitore il weight`() {
    scene { backdrop ->
      Column {
        Row(Modifier.width(300.dp)) {
          FluidGlassContainer(Modifier.weight(1f), propagateMinConstraints = true) {
            Box(Modifier.fluidGlass(backdrop = backdrop)) { Spacer(Modifier.size(44.dp)) }
          }
        }
      }
    }
    assertEquals("il weight arriva al vetro: $rest", 600f, rest.single().bounds.width, 0.5f)
  }

  @Test
  fun `un pezzo solo riceve dal contenitore la misura imposta`() {
    scene { backdrop ->
      FluidGlassContainer(Modifier.size(200.dp, 80.dp), propagateMinConstraints = true) {
        Box(Modifier.fluidGlass(backdrop = backdrop)) { Spacer(Modifier.size(44.dp)) }
      }
    }
    assertEquals("la misura imposta arriva al vetro: $rest", 400f, rest.single().bounds.width, 0.5f)
    assertEquals(160f, rest.single().bounds.height, 0.5f)
  }

  @Test
  fun `di serie il contenuto non riceve i minimi`() {
    scene { backdrop ->
      FluidGlassContainer(Modifier.size(200.dp, 80.dp)) {
        Box(Modifier.fluidGlass(backdrop = backdrop)) { Spacer(Modifier.size(44.dp)) }
      }
    }
    assertEquals("come in un Box: il vetro è grande quanto il suo contenuto ($rest)", 88f, rest.single().bounds.width, 0.5f)
  }

  @Test
  fun `di serie un contenitore con piu' pezzi non cambia layout`() {
    scene { backdrop ->
      FluidGlassContainer(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.Center) {
          Box(Modifier.size(44.dp).fluidGlass(backdrop = backdrop))
          Spacer(Modifier.width(8.dp))
          Box(Modifier.size(44.dp).fluidGlass(backdrop = backdrop))
        }
      }
    }
    // La Row non è costretta alla larghezza del contenitore: resta stretta e in alto all'inizio, i pezzi dove erano.
    val lefts = rest.map { it.bounds.left }.sorted()
    assertEquals(rest.toString(), listOf(0f, 104f), lefts)
  }

  @Test
  fun `fuori da un contenitore il pannello e' un glassSurface, nessun piano`() {
    val before = FluidGlassContainerDiagnostics.liveContainers
    scene { backdrop ->
      val tint = GlassDefaults.modalTint()
      Column(Modifier.width(300.dp).fluidGlassPanel(backdrop, tint, SheetShape, GlassRole.Modal)) {
        FluidGlassNested { Text("Fuori") }
      }
    }
    assertEquals(before, FluidGlassContainerDiagnostics.liveContainers)
    assertTrue("nessun riposo del piano: $rest", rest.isEmpty())
  }
}
