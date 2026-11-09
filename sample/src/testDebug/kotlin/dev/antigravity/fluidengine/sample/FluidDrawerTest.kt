package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidDrawer
import dev.antigravity.fluidengine.ui.fluid.FluidDrawerState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionBackHandler
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarRow
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarSection
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidDrawerObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidDrawerState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Il pannello laterale (`FluidDrawer`, pm-f1/ge, C08).
 *
 * L'albero e' quello documentato: la pagina e la barra in un contenitore che il pannello oscura, poi il
 * pannello, poi l'host del menu che si trasforma. Si apre **solo col tasto** (☰); si chiude col back, col
 * tocco fuori, col dito verso sinistra e con l'azione di TalkBack. Il back segue la pila: menu, pannello,
 * selezione, pagina.
 *
 * Schermo da 360 dp a densita' 2: il pannello e' largo `min(320, 0,85 x 360)` = 306 dp piu' 12 di margine,
 * cioe' 636 px di corsa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidDrawerTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var drawer: FluidDrawerState
  private lateinit var morph: FluidMorphMenuState
  private lateinit var selection: FluidSelectionState<String>
  private var drawerComposed by mutableStateOf(true)
  private var picks = 0
  private var chosen = ""

  /** La corsa del pannello in pixel: 306 dp + 12 dp di margine, a densita' 2. */
  private val travelPx = (306f + 12f) * 2f

  private fun show(policy: FluidMotionPolicy = FluidMotionPolicy.Default) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides policy) {
          Screen()
        }
      }
    }
    compose.waitForIdle()
  }

  @androidx.compose.runtime.Composable
  private fun Screen() {
    val controller = rememberFluidNavController()
    nav = controller
    val state = rememberFluidDrawerState()
    drawer = state
    morph = rememberFluidMorphMenuState()
    selection = rememberFluidSelectionState()
    val backdrop = currentGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().fluidDrawerObscured(state)) {
        FluidNavHost(navController = controller, startDestination = "casa") {
          composable("casa") {
            Box(Modifier.fillMaxSize()) {
              Text("Casa", modifier = Modifier.align(Alignment.Center))
              FluidGlassIconButton(
                onClick = { state.open() },
                modifier = Modifier.align(Alignment.TopStart).testTag("hamburger"),
                backdrop = backdrop,
              ) { Icon(Icons.Rounded.Menu, contentDescription = "Cartelle") }
            }
          }
          composable("cartella") {
            // La selezione della pagina: composta con la pagina, prima del pannello che si apre dopo.
            FluidSelectionBackHandler(selection)
            Box(Modifier.fillMaxSize()) {
              Text("Cartella", modifier = Modifier.align(Alignment.Center))
            }
          }
        }
      }
      if (drawerComposed) {
        FluidDrawer(state = state, backdrop = backdrop, paneTitle = "Cartelle") {
          item { FluidSidebarSection(title = "Tutte le caselle") }
          item {
            FluidSidebarRow(
              label = "Posta in arrivo",
              selected = true,
              onClick = { chosen = "inbox" },
              icon = Icons.Rounded.Inbox,
              minHeight = FluidSidebarDefaults.ComfortableRowHeight,
              modifier = Modifier.testTag("row-inbox"),
            )
          }
          item {
            FluidMorphMenuButton(
              state = morph,
              actions = { listOf(FluidContextAction(label = "Rinomina", onClick = { picks++ })) },
              onClick = {},
              modifier = Modifier.testTag("menu"),
              text = "Altro",
              backdrop = backdrop,
              opensOnTap = true,
            )
          }
          items(30) { index ->
            FluidSidebarRow(
              label = "Cartella $index",
              selected = false,
              onClick = { chosen = "folder$index" },
              icon = Icons.Rounded.Folder,
              level = index % 3,
              minHeight = FluidSidebarDefaults.ComfortableRowHeight,
            )
          }
        }
      }
      FluidMorphMenuHost(state = morph, backdrop = backdrop)
    }
  }

  private fun openWithButton() {
    compose.onNodeWithTag("hamburger").performClick()
    compose.waitForIdle()
    assertTrue("il tasto apre il pannello", drawer.isOpen)
    assertEquals(1f, drawer.progress, 0.001f)
    compose.onNodeWithText("Posta in arrivo").assertExists()
  }

  private fun back() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private fun route(): String? = compose.runOnIdle { nav.currentBackStackEntry?.destination?.route }

  // --- Aprire e chiudere ----------------------------------------------------------------------------

  @Test
  fun `si apre col tasto e si chiude toccando la pagina oscurata`() {
    show()
    openWithButton()
    compose.onRoot().performTouchInput { click(Offset(width - 20f, height / 2f)) }
    compose.waitForIdle()
    assertFalse(drawer.isOpen)
    assertEquals(0f, drawer.progress, 0.001f)
    compose.onAllNodesWithText("Posta in arrivo").assertCountEquals(0)
    assertEquals("il tocco fuori non tocca la pagina", "casa", route())
  }

  @Test
  fun `il back chiude il pannello e non la pagina`() {
    show()
    compose.runOnIdle { nav.navigate("cartella") }
    compose.waitForIdle()
    compose.runOnIdle { drawer.open() }
    compose.waitForIdle()
    back()
    assertFalse(drawer.isOpen)
    assertEquals("cartella", route())
    back()
    assertEquals("casa", route())
  }

  @Test
  fun `una riga del pannello si tocca come sempre`() {
    show()
    openWithButton()
    compose.onNodeWithTag("row-inbox").performClick()
    compose.waitForIdle()
    assertEquals("inbox", chosen)
  }

  // --- Il dito --------------------------------------------------------------------------------------

  @Test
  fun `trascinato verso sinistra il pannello segue il dito, pixel per pixel`() {
    show()
    openWithButton()
    val start = Offset(400f, 500f)
    compose.onRoot().performTouchInput {
      down(start)
      // Oltre la soglia di movimento (32 px in Robolectric), poi 100 px in tutto.
      moveTo(start + Offset(-50f, 0f))
      moveTo(start + Offset(-100f, 0f))
    }
    compose.waitForIdle()
    val expected = 1f - 100f / travelPx
    assertTrue(
      "il pannello sta sotto il dito: atteso $expected, e' ${drawer.progress}",
      abs(drawer.progress - expected) < 0.01f,
    )
    assertTrue("mentre il dito c'e' resta aperto", drawer.isOpen)

    compose.onRoot().performTouchInput {
      moveTo(start + Offset(-400f, 0f))
      up()
    }
    compose.waitForIdle()
    assertFalse("oltre il 35% della corsa il rilascio chiude", drawer.isOpen)
    assertEquals(0f, drawer.progress, 0.001f)
  }

  @Test
  fun `un trascinamento corto e lento lo lascia aperto, e torna al suo posto`() {
    show()
    openWithButton()
    val start = Offset(400f, 500f)
    compose.onRoot().performTouchInput {
      down(start)
      repeat(8) { moveBy(Offset(-10f, 0f), delayMillis = 100) }
      repeat(5) { moveBy(Offset.Zero, delayMillis = 100) }
      up()
    }
    compose.waitForIdle()
    assertTrue("80 px lenti non bastano", drawer.isOpen)
    assertEquals(1f, drawer.progress, 0.001f)
  }

  @Test
  fun `una spinta veloce lo chiude anche se corta`() {
    show()
    openWithButton()
    compose.onRoot().performTouchInput {
      swipe(start = Offset(400f, 500f), end = Offset(280f, 500f), durationMillis = 60)
    }
    compose.waitForIdle()
    assertFalse(drawer.isOpen)
  }

  @Test
  fun `il trascinamento parte anche dalla pagina oscurata`() {
    show()
    openWithButton()
    compose.onRoot().performTouchInput {
      swipe(start = Offset(width - 10f, 500f), end = Offset(width - 500f, 500f), durationMillis = 200)
    }
    compose.waitForIdle()
    assertFalse(drawer.isOpen)
  }

  @Test
  fun `verso destra il pannello aperto non si muove`() {
    show()
    openWithButton()
    compose.onRoot().performTouchInput {
      swipe(start = Offset(200f, 500f), end = Offset(600f, 500f), durationMillis = 200)
    }
    compose.waitForIdle()
    assertTrue(drawer.isOpen)
    assertEquals(1f, drawer.progress, 0.001f)
  }

  @Test
  fun `nessun gesto dal bordo apre il pannello`() {
    show()
    compose.onRoot().performTouchInput {
      swipe(start = Offset(2f, 300f), end = Offset(500f, 300f), durationMillis = 200)
    }
    compose.waitForIdle()
    assertFalse("il bordo e' del back, non del pannello", drawer.isOpen)
    assertEquals(0f, drawer.progress, 0.001f)
    compose.onAllNodesWithText("Posta in arrivo").assertCountEquals(0)
  }

  // --- L'ordine del back ----------------------------------------------------------------------------

  @Test
  fun `il back chiude prima il menu, poi il pannello, poi la selezione, poi la pagina`() {
    show()
    compose.runOnIdle { nav.navigate("cartella") }
    compose.waitForIdle()
    compose.runOnIdle { selection.toggle("a") }
    compose.waitForIdle()
    assertTrue(selection.isActive)
    compose.runOnIdle { drawer.open() }
    compose.waitForIdle()
    compose.onNodeWithTag("menu").performClick()
    compose.waitForIdle()
    assertTrue("il menu e' aperto sopra il pannello", morph.isOpen)

    back()
    assertFalse("1: il menu", morph.isOpen)
    assertTrue("1: il pannello resta", drawer.isOpen)
    assertTrue("1: la selezione resta", selection.isActive)
    assertEquals("cartella", route())

    back()
    assertFalse("2: il pannello", drawer.isOpen)
    assertTrue("2: la selezione resta", selection.isActive)
    assertEquals("cartella", route())

    back()
    assertFalse("3: la selezione", selection.isActive)
    assertEquals("cartella", route())

    back()
    assertEquals("4: la pagina", "casa", route())
    assertEquals("un back non sceglie niente", 0, picks)
  }

  @Test
  fun `riaperto, il pannello torna davanti alla selezione`() {
    show()
    compose.runOnIdle { nav.navigate("cartella") }
    compose.waitForIdle()
    compose.runOnIdle { selection.toggle("a") }
    compose.runOnIdle { drawer.open() }
    compose.waitForIdle()
    back()
    assertFalse(drawer.isOpen)
    compose.runOnIdle { drawer.open() }
    compose.waitForIdle()
    back()
    assertFalse("riaperto, il back e' ancora suo", drawer.isOpen)
    assertTrue(selection.isActive)
  }

  // --- Movimento ridotto ----------------------------------------------------------------------------

  @Test
  fun `col movimento ridotto entra ed esce subito, e il dito lo segue lo stesso`() {
    show(policy = FluidMotionPolicy(durationScale = 0f))
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { drawer.open(); Snapshot.sendApplyNotifications() }
    repeat(3) { compose.mainClock.advanceTimeByFrame() }
    assertEquals("aperto in tre fotogrammi, cioe' subito", 1f, drawer.progress, 0.001f)
    compose.runOnUiThread { drawer.close(); Snapshot.sendApplyNotifications() }
    repeat(3) { compose.mainClock.advanceTimeByFrame() }
    assertEquals(0f, drawer.progress, 0.001f)
    compose.mainClock.autoAdvance = true

    compose.runOnIdle { drawer.open() }
    compose.waitForIdle()
    val start = Offset(400f, 500f)
    compose.onRoot().performTouchInput {
      down(start)
      // Lento: 80 px in 800 ms, poi fermo. Non basta a chiudere.
      repeat(8) { moveBy(Offset(-10f, 0f), delayMillis = 100) }
    }
    compose.waitForIdle()
    assertTrue(abs(drawer.progress - (1f - 80f / travelPx)) < 0.01f)
    compose.onRoot().performTouchInput {
      repeat(5) { moveBy(Offset.Zero, delayMillis = 100) }
      up()
    }
    compose.waitForIdle()
    assertEquals("rilasciato poco, torna aperto di colpo", 1f, drawer.progress, 0.001f)
  }

  /** Il controllo: a scala normale, un fotogramma dopo l'apertura il pannello e' ancora per strada. */
  @Test
  fun `a scala normale entra con la sua molla`() {
    show()
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { drawer.open(); Snapshot.sendApplyNotifications() }
    repeat(3) { compose.mainClock.advanceTimeByFrame() }
    assertTrue("in viaggio: ${drawer.progress}", drawer.progress in 0.001f..0.999f)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(1f, drawer.progress, 0.001f)
  }

  // --- Accessibilita' -------------------------------------------------------------------------------

  @Test
  fun `aperto, la pagina sotto e' nascosta a TalkBack e il pannello si annuncia e si chiude`() {
    show()
    val hidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
    compose.onAllNodes(hidden).assertCountEquals(0)
    openWithButton()
    compose.onNode(hidden).assert(hasAnyDescendant(hasText("Casa")))

    val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Cartelle")
    compose.onNode(pane)
      .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
      .performSemanticsAction(SemanticsActions.Dismiss)
    compose.waitForIdle()
    assertFalse(drawer.isOpen)
    compose.onAllNodes(hidden).assertCountEquals(0)
  }

  @Test
  fun `il tasto che lo apre ha il suo nome`() {
    show()
    compose.onNodeWithContentDescription("Cartelle").assertExists()
  }

  // --- Stato e composizione -------------------------------------------------------------------------

  @Test
  fun `una ricreazione lo ritrova aperto, mai a meta'`() {
    val restore = StateRestorationTester(compose)
    restore.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { Screen() }
    }
    compose.waitForIdle()
    compose.runOnIdle { drawer.open() }
    compose.waitForIdle()
    restore.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    assertTrue(drawer.isOpen)
    assertEquals(1f, drawer.progress, 0.001f)
    compose.onNodeWithText("Posta in arrivo").assertExists()
  }

  @Test
  fun `tolto dalla composizione da aperto si chiude senza uscita`() {
    show()
    openWithButton()
    compose.runOnIdle { drawerComposed = false }
    compose.waitForIdle()
    assertFalse(drawer.isOpen)
    assertEquals(0f, drawer.progress, 0.001f)
    // Nessun gestore rimasto: il back va alla pagina.
    compose.runOnIdle { nav.navigate("cartella") }
    compose.waitForIdle()
    back()
    assertEquals("casa", route())
  }
}
