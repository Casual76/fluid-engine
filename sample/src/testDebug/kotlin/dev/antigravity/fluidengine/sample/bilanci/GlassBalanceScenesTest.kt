package dev.antigravity.fluidengine.sample.bilanci

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F7.28 · I bilanci del vetro: le scene di «Misure» (diciotto, più `menu-gruppo-barra` di F7.27 da F7.29) e i quattro
 * componenti che `GlassNodeCountTest` monta da soli (tasto, gruppo, popover, tab bar), con lo stesso albero, la stessa
 * finestra e gli stessi gesti di `GlassNodeCountTest`, col copione comune ([BalanceRuns]) e le righe di `vetro.bilancio`.
 *
 * Perché un gemello e non `GlassNodeCountTest` stesso: il suo copione dipende dal tempo vero (la cattura tenuta del
 * foglio si rinfresca ogni 110 ms di orologio vero, fuori dall'orologio della prova), e il lavoro in più per fotogramma
 * del misuratore spostava di una cattura i suoi tetti storici (`foglio-trascinato/su` 38 → 39). Qui i tetti delle
 * catture lo sanno (vedi il motivo delle righe).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w800dp-h1280dp-mdpi",
  application = GlassBenchDevice::class,
  shadows = [
    GlassBenchHeap::class, CountingRenderNode::class, CountingBitmap::class, CountingRenderEffect::class,
    CountingRuntimeShader::class,
  ],
)
class GlassBalanceScenesTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val harness = BalanceHarness(compose)
  private lateinit var restore: AutoCloseable

  @Before
  fun start() {
    compose.mainClock.autoAdvance = false
    restore = harness.install()
  }

  @After
  fun stop() = restore.close()

  private fun check(scenario: MeasureScenario) {
    val registry = GlassBalanceRegistry.require(VetroFile)
    val meter = BalanceRuns.measureScenario(harness, scenario, registry.margins[scenario.key] ?: 0)
    val problems = meter.compare(registry, scenario.key, proof = "GlassBalanceScenesTest")
    assertTrue("${scenario.key}:\n  ${problems.joinToString("\n  ")}", problems.isEmpty())
  }

  private fun component(label: String, content: @androidx.compose.runtime.Composable (dev.antigravity.fluidengine.ui.fluid.GlassBackdropState) -> Unit) {
    val registry = GlassBalanceRegistry.require(VetroFile)
    val meter = BalanceRuns.measureComponent(harness, label, registry.margins[label] ?: 0, content)
    val problems = meter.compare(registry, label, proof = "GlassBalanceScenesTest")
    assertTrue("$label:\n  ${problems.joinToString("\n  ")}", problems.isEmpty())
  }

  @Test fun tasto() = component("tasto") { backdrop ->
    FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Text("+") }
  }

  @Test fun gruppo() = component("gruppo") { backdrop ->
    FluidGlassContainer {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Text("${it + 1}") } }
      }
    }
  }

  @Test fun popover() = component("popover") { _ ->
    FluidGlassModalPortal(visible = true, onDismissRequest = {}, origin = { Rect(360f, 320f, 408f, 368f) }) {
      Box(Modifier.size(200.dp, 112.dp), contentAlignment = Alignment.Center) { Text("Scegli") }
    }
  }

  @Test fun tabBar() = component("tab-bar") { backdrop ->
    FluidFloatingTabBar(
      items = listOf("Foto", "Album", "Cerca").map { FluidTabItem(it, it, FluidIcons.Search) },
      selectedRoute = "Foto",
      onSelect = {},
      scrollConnection = rememberFluidFloatingTabBarScrollConnection(),
      backdrop = backdrop,
    )
  }

  @Test fun menuTasto() = check(MeasureScenario.MenuButton)
  @Test fun menuGruppo() = check(MeasureScenario.MenuGroup)
  @Test fun menuGruppoBarra() = check(MeasureScenario.MenuGroupBar)
  @Test fun contestuale() = check(MeasureScenario.ContextMenu)
  @Test fun capsulaPremuta() = check(MeasureScenario.CapsulePress)
  @Test fun foglioTrascinato() = check(MeasureScenario.SheetDrag)
  @Test fun ottoLastre() = check(MeasureScenario.EightSlabs)
  @Test fun scorrimentoFoto() = check(MeasureScenario.ScrollPhotos)
  @Test fun scorrimentoTesto() = check(MeasureScenario.ScrollText)
  @Test fun barraContesto() = check(MeasureScenario.BarContext)
  @Test fun foglioSale() = check(MeasureScenario.SheetRise)
  @Test fun pushPop() = check(MeasureScenario.PushPop)
  @Test fun zoomCard() = check(MeasureScenario.ZoomCard)
  @Test fun f1bGruppoMenu() = check(MeasureScenario.F1bGroupMenu)
  @Test fun f1bContestuale() = check(MeasureScenario.F1bContext)
  @Test fun f1bCard() = check(MeasureScenario.F1bCard)
  @Test fun selezione() = check(MeasureScenario.Selection)
  @Test fun composer() = check(MeasureScenario.ComposerField)
  @Test fun barraTastiera() = check(MeasureScenario.KeyboardBar)

  companion object {
    const val VetroFile = "vetro.bilancio"

    /** Le scene di «Misure» misurate qui: tutte (la completezza lo controlla in `GlassBalanceRegistryTest`). */
    internal val Measured = MeasureScenario.entries
  }
}
