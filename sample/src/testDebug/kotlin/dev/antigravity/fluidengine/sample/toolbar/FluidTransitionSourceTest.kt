package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import android.os.Handler
import android.graphics.Bitmap
import android.view.PixelCopy
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import java.io.File
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

  private fun capture(name: String) {
    val dir = System.getenv("FLUID_S4_MENU_FRAMES")?.let(::File) ?: return
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(window, null, image, { status ->
        check(status == PixelCopy.SUCCESS)
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
      }, Handler(Looper.getMainLooper()))
    }
    compose.waitForIdle()
  }

  @Test fun `un menu dichiarato consuma e restituisce solo il suo gruppo`() {
    var picked = 0
    lateinit var source: FluidTransitionSource
    lateinit var neighbour: FluidTransitionSource
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val host = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      source = rememberFluidToolbarTransitionSource("menu")
      neighbour = rememberFluidToolbarTransitionSource("separate")
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidScreen("Note", toolbar = {
              group("tools") {
                button("first", FluidIcons.MoreHoriz, "Primo", onClick = {})
                button("second", FluidIcons.MoreHoriz, "Secondo", onClick = {})
                action("menu", FluidIcons.MoreHoriz, "Menu del gruppo") {
                  listOf(FluidContextAction("Scegli bene") { picked++ })
                }
              }
              group("other") { button("separate", FluidIcons.MoreHoriz, "Separato", onClick = {}) }
            }) {}
            FluidGlassModalHost(state = host, backdrop = backdrop)
          }
        }
      }
    }
    advance(256)
    val before = compose.runOnIdle { requireNotNull(source.resolve()) }
    val separate = compose.runOnIdle { requireNotNull(neighbour.resolve()) }
    assertTrue(before.bounds.width > separate.bounds.width * 2)
    compose.onNodeWithContentDescription("Menu del gruppo").performClick()
    advance(704)
    compose.onNodeWithContentDescription("Primo").assertDoesNotExist()
    assertEquals("il pezzo di riposo resta pronto durante il viaggio", before, compose.runOnIdle { source.resolve() })
    assertEquals(separate, compose.runOnIdle { neighbour.resolve() })
    compose.onNodeWithText("Scegli bene").performClick()
    advance(960)
    assertEquals(1, compose.runOnIdle { picked })
    assertEquals(before, compose.runOnIdle { source.resolve() })
    compose.onNodeWithContentDescription("Primo").assertExists()
  }

  @Test fun `il dito del gruppo e del tasto sceglie la riga senza un secondo down`() {
    val picks = androidx.compose.runtime.mutableStateListOf<String>()
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val host = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            val actions = {
              listOf("Condividi", "Aggiungi ai preferiti", "Elimina").map { label ->
                FluidContextAction(label) { picks += label }
              }
            }
            FluidScreen("Foto", toolbar = {
              group("tools", placement = FluidToolbarPlacement.BottomBar) {
                button("share", FluidIcons.MoreHoriz, "Condividi foto", onClick = {})
                button("star", FluidIcons.MoreHoriz, "Preferiti", onClick = {})
                action("menu", FluidIcons.MoreHoriz, "Menu del gruppo", menu = actions)
              }
            }) { item { androidx.compose.material3.Text("Scelta: " + (picks.lastOrNull() ?: "—")) } }
            FluidBarAction(FluidIcons.MoreHoriz, "Altro", onClick = {}, actions = actions,
              opensOn = FluidMenuOpensOn.Tap, modifier = Modifier.align(Alignment.TopEnd))
            // Sonda del gesto: composizione, registrazione, celle e hit-test restano quelli del menu.
            // Il backend Skia di Robolectric non compila GlassBody8 al ridisegno di una cattura.
            FluidGlassModalHost(state = host, backdrop = backdrop, modifier = Modifier.drawWithContent { })
          }
        }
      }
    }
    advance(256)
    val cases = listOf("Menu del gruppo" to "Condividi", "Menu del gruppo" to "Aggiungi ai preferiti", "Altro" to "Elimina")
    for ((source, label) in cases) {
      val root = compose.onRoot()
      val downAt = compose.onNodeWithContentDescription(source).fetchSemanticsNode().boundsInRoot.center
      val shareAt = compose.onNodeWithContentDescription("Condividi foto").fetchSemanticsNode().boundsInRoot.center
      root.performTouchInput { down(downAt) }
      advance(768)
      val cell = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
      val target = if (label == "Condividi") cell.center.copy(x = shareAt.x.coerceIn(cell.left, cell.right - 1f)) else cell.center
      root.performTouchInput {
        val duration = if (label == "Condividi") 480L else 3232L
        repeat(24) { i -> moveTo(downAt + (target - downAt) * ((i + 1) / 24f), delayMillis = duration / 24) }
        up()
      }
      advance(800)
      assertEquals(label, picks.lastOrNull())
      compose.onNodeWithText(label).assertDoesNotExist()
      capture("$label-scelto")
    }
    assertEquals(cases.map { it.second }, picks)
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
