package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidTransitionSourceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var diagnosticsWere = false

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat(ms / 16) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      compose.waitForIdle()
    }
  }

  @Test fun `stesso id prima sorgente posata poi seconda e infine nessuna`() {
    var first by mutableStateOf(true)
    var second by mutableStateOf(true)
    lateinit var source: FluidTransitionSource
    lateinit var other: FluidTransitionSource
    lateinit var density: Density
    compose.setContent {
      density = LocalDensity.current
      val namespace = rememberFluidGlassNamespace()
      val otherNamespace = rememberFluidGlassNamespace()
      source = remember { FluidTransitionSource("card", namespace) }
      other = remember { FluidTransitionSource("card", otherNamespace) }
      Column {
        if (first) Box(Modifier.size(60.dp, 30.dp).fluidTransitionSource("card", namespace, FluidCapsuleShape))
        if (second) Box(Modifier.size(80.dp, 40.dp).fluidTransitionSource("card", namespace))
        Box(Modifier.size(45.dp).fluidTransitionSource("card", otherNamespace))
      }
    }
    val before = compose.runOnIdle { requireNotNull(source.resolve()) }
    assertEquals(with(density) { 60.dp.toPx() }, before.bounds.width, 0.01f)
    assertEquals(FluidCapsuleShape, before.shape)
    compose.runOnIdle { first = false }
    val after = compose.runOnIdle { requireNotNull(source.resolve()) }
    assertEquals(before.bounds.topLeft, after.bounds.topLeft)
    assertEquals(with(density) { 80.dp.toPx() }, after.bounds.width, 0.01f)
    assertEquals(RectangleShape, after.shape)
    compose.runOnIdle { second = false }
    compose.runOnIdle {
      assertNull(source.resolve())
      assertEquals(with(density) { 45.dp.toPx() }, requireNotNull(other.resolve()).bounds.width, 0.01f)
    }
  }

  @Test fun `il gruppo segue il piano mentre perde un elemento`() {
    var wide by mutableStateOf(true)
    lateinit var main: FluidTransitionSource
    lateinit var sibling: FluidTransitionSource
    lateinit var removed: FluidTransitionSource
    lateinit var density: Density
    compose.mainClock.autoAdvance = false
    compose.setContent {
      density = LocalDensity.current
      main = rememberFluidToolbarTransitionSource("a")
      sibling = rememberFluidToolbarTransitionSource("b")
      removed = rememberFluidToolbarTransitionSource("c")
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidScreen("Note", toolbar = {
          group("tools") {
            button("a", FluidIcons.MoreHoriz, "A", onClick = {})
            button("b", FluidIcons.MoreHoriz, "B", onClick = {})
            if (wide) button("c", FluidIcons.MoreHoriz, "C", onClick = {})
          }
          primary("other", FluidIcons.MoreHoriz, "Azione separata", onClick = {})
        }) {}
      }
    }
    advance(256)
    val before = compose.runOnIdle {
      requireNotNull(main.resolve()).also {
        assertEquals(it, sibling.resolve())
        assertEquals(it, removed.resolve())
      }
    }
    val pitch = with(density) { FluidMetrics.GroupItemPitch.toPx() }
    assertEquals(pitch * 3, before.bounds.width, 1f)
    assertEquals(FluidCapsuleShape, before.shape)
    val touch = compose.onNodeWithContentDescription("A").fetchSemanticsNode().boundsInRoot
    assertEquals(touch.center.y, before.bounds.center.y, 1f)

    compose.runOnIdle { wide = false }
    Snapshot.sendApplyNotifications()
    val travels = buildList {
      repeat(36) {
        advance(16)
        add(compose.runOnIdle {
          requireNotNull(main.resolve()).also { current -> assertEquals(current, sibling.resolve()) }
        })
      }
    }
    val mid = travels.first {
      it.bounds.width in (pitch * 2.2f)..(pitch * 2.8f) && it.shape != FluidCapsuleShape
    }
    val corner = (mid.shape as ContinuousCornerShape).topStart.toPx(mid.bounds.size, density)
    assertTrue("raggio durante il viaggio: $corner in ${mid.bounds}", corner > 0f)
    assertEquals(mid.bounds.minDimension / 2f, corner, 1f)
    assertTrue("il piano ha aperto un viaggio", FluidGlassContainerDiagnostics.totalJourneys > 0)
    compose.runOnIdle { assertNull(removed.resolve()) }

    advance(960)
    val after = compose.runOnIdle { requireNotNull(main.resolve()) }
    assertEquals(pitch * 2, after.bounds.width, 1f)
    assertEquals(FluidCapsuleShape, after.shape)
  }

  @Test fun `con il vetro classico ogni sorgente ripiega sul proprio tasto`() {
    lateinit var first: FluidTransitionSource
    lateinit var second: FluidTransitionSource
    lateinit var density: Density
    compose.setContent {
      density = LocalDensity.current
      first = rememberFluidToolbarTransitionSource("a")
      second = rememberFluidToolbarTransitionSource("b")
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) {
          FluidScreen("Note", toolbar = {
            group("tools") {
              button("a", FluidIcons.MoreHoriz, "A", onClick = {})
              button("b", FluidIcons.MoreHoriz, "B", onClick = {})
            }
          }) {}
        }
      }
    }
    compose.runOnIdle {
      val left = requireNotNull(first.resolve())
      val right = requireNotNull(second.resolve())
      val pitch = with(density) { FluidMetrics.GroupItemPitch.toPx() }
      assertEquals(pitch, left.bounds.width, 1f)
      assertEquals(pitch, right.bounds.center.x - left.bounds.center.x, 1f)
      assertEquals(FluidCapsuleShape, left.shape)
    }
  }

  @Test fun `la barra ancorata registra lo stesso id nella pagina che la dichiara`() {
    var stack by mutableStateOf(listOf("root"))
    val sources = mutableMapOf<String, FluidTransitionSource>()
    lateinit var card: FluidTransitionSource
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val namespace = rememberFluidGlassNamespace()
      card = remember { FluidTransitionSource("card", namespace) }
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidPageHost(
          stack, onBack = { stack = stack.dropLast(1) },
          modifier = Modifier.fillMaxSize(), transition = { FluidPageTransition.Expand },
        ) { page ->
          sources[page] = rememberFluidToolbarTransitionSource("ok")
          FluidScreen(page, toolbar = {
            if (page == "root") button("ok", FluidIcons.MoreHoriz, "Opzioni", onClick = {})
            else text("ok", "Salva documento", onClick = {})
          }) {
            if (page == "root") item {
              Box(Modifier.size(80.dp).fluidTransitionSource("card", namespace, FluidCapsuleShape))
            }
          }
        }
      }
    }
    advance(256)
    val origin = compose.runOnIdle { requireNotNull(sources.getValue("root").resolve()) }
    compose.runOnIdle { stack = listOf("root", "child") }
    Snapshot.sendApplyNotifications()
    advance(1200)
    compose.runOnIdle {
      assertNull(sources.getValue("root").resolve())
      assertTrue(requireNotNull(sources.getValue("child").resolve()).bounds.width > origin.bounds.width)
      assertNull(card.resolve())
      assertEquals(FluidCapsuleShape, requireNotNull(card.resolve(includeCoveredPage = true)).shape)
      stack = listOf("root")
    }
    Snapshot.sendApplyNotifications()
    advance(1200)
    compose.runOnIdle {
      assertNull(sources.getValue("child").resolve())
      assertEquals(origin, sources.getValue("root").resolve())
    }
  }
}
