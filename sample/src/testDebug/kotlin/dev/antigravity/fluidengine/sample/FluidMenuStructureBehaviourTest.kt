package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMenuActions
import dev.antigravity.fluidengine.ui.fluid.FluidMenuElementSize
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOrder
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.Open
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.Tap
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class StructureHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()

  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * La struttura dei menu (4.0, F5A.3) nell'host dei modali vero: il sottomenu che si apre dentro il menu e torna
 * indietro (con l'intestazione e col back), la voce che lascia il menu aperto e si aggiorna dal vivo, l'ordine
 * `Priority` che capovolge un menu aperto verso l'alto, la fila di simboli.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidMenuStructureBehaviourTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val haptics = StructureHaptics()
  private val picked = mutableListOf<String>()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  private fun showInModalHost(content: @Composable () -> Unit) {
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
  }

  /** Una card che apre [actions] tenendo premuto, e il menu aperto e lasciato (il dito si solleva fermo). */
  private fun openCard(actions: () -> List<FluidContextAction>) {
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = 40.dp)
            .size(120.dp)
            .fluidContextMenu(actions = actions)
            .testTag("card"),
        )
      }
    }
    compose.onNodeWithTag("card").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    compose.onNodeWithTag("card").performTouchInput { up() }
    compose.waitForIdle()
  }

  private fun sortMenu(): List<FluidContextAction> = listOf(
    FluidContextAction(
      label = "Ordina",
      subtitle = "Per data",
      children = listOf(
        FluidContextAction("Per data") { picked += "Per data" },
        FluidContextAction("Per nome") { picked += "Per nome" },
      ),
    ) { picked += "madre" },
    FluidContextAction("Elimina", startsSection = true) { picked += "Elimina" },
  )

  @Test
  fun `una voce coi figli apre il sottomenu dentro il menu, e il figlio sceglie e chiude`() {
    openCard(::sortMenu)
    compose.onNodeWithText("Per data").assertExists() // il sottotitolo della voce madre
    compose.onNodeWithText("Per nome").assertDoesNotExist()

    compose.onNodeWithText("Ordina").performClick()
    compose.waitForIdle()

    // Il menu non si e' chiuso: e' diventato il sottomenu, con la voce madre in cima.
    compose.onNodeWithText("Ordina").assertExists()
    compose.onNodeWithText("Per nome").assertExists()
    compose.onNodeWithText("Elimina").assertDoesNotExist()
    assertTrue("la voce madre non e' un'azione", picked.isEmpty())
    assertEquals(listOf(Open, Tap), haptics.played)

    compose.onNodeWithText("Per nome").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Per nome"), picked)
    compose.onNodeWithText("Per nome").assertDoesNotExist()
    assertEquals(listOf(Open, Tap, Tap), haptics.played)
  }

  @Test
  fun `l'intestazione del sottomenu torna al menu di prima`() {
    openCard(::sortMenu)
    compose.onNodeWithText("Ordina").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Ordina").performClick()
    compose.waitForIdle()

    compose.onNodeWithText("Elimina").assertExists()
    compose.onNodeWithText("Per nome").assertDoesNotExist()
    assertTrue(picked.isEmpty())
  }

  @Test
  fun `il back dentro un sottomenu torna indietro, il secondo chiude`() {
    openCard(::sortMenu)
    compose.onNodeWithText("Ordina").performClick()
    compose.waitForIdle()

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    compose.onNodeWithText("Elimina").assertExists()
    compose.onNodeWithText("Per nome").assertDoesNotExist()

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    compose.onNodeWithText("Elimina").assertDoesNotExist()
    assertTrue(picked.isEmpty())
  }

  @Test
  fun `una voce che lascia il menu aperto fa la sua azione e si aggiorna dal vivo`() {
    var grid by mutableStateOf(false)
    openCard {
      FluidMenuActions {
        listOf(
          FluidContextAction("Griglia", selected = grid, keepsMenuOpen = true) { grid = !grid },
          FluidContextAction("Chiudi") { picked += "Chiudi" },
        )
      }
    }
    fun selected() = compose.onNodeWithText("Griglia").fetchSemanticsNode().let { node ->
      // La spunta e' lo stato «selezionato» della riga (il nodo cliccabile che contiene il testo).
      generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Selected) } ?: false
    }
    assertEquals(false, selected())

    compose.onNodeWithText("Griglia").performClick()
    compose.waitForIdle()

    assertTrue("l'azione e' partita", grid)
    compose.onNodeWithText("Chiudi").assertExists()
    assertEquals("la voce mostra lo stato nuovo, a menu aperto", true, selected())
    assertEquals(listOf(Open, Tap), haptics.played)
  }

  private fun rowTop(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

  private fun openBottomBar(order: FluidMenuOrder) {
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        FluidBarAction(
          icon = Icons.Rounded.Star,
          contentDescription = "Altro",
          onClick = {},
          actions = {
            FluidMenuActions(
              actions = listOf("Uno", "Due", "Tre").map { label -> FluidContextAction(label) { picked += label } },
              order = order,
            )
          },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .testTag("bar"),
        )
      }
    }
    compose.onNodeWithTag("bar").performClick()
    compose.waitForIdle()
  }

  @Test
  fun `Priority capovolge un menu che si apre verso l'alto, cosi' la prima voce sta vicino al dito`() {
    openBottomBar(FluidMenuOrder.Priority)
    assertTrue("Uno in fondo", rowTop("Uno") > rowTop("Due"))
    assertTrue(rowTop("Due") > rowTop("Tre"))
    // E sceglie la voce che si vede, non quella che stava li' nell'ordine scritto.
    compose.onNodeWithText("Uno").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Uno"), picked)
  }

  @Test
  fun `di serie l'ordine resta quello scritto anche verso l'alto`() {
    openBottomBar(FluidMenuOrder.Automatic)
    assertTrue(rowTop("Uno") < rowTop("Due"))
    assertTrue(rowTop("Due") < rowTop("Tre"))
  }

  @Test
  fun `la fila Small mette la prima sezione su una riga di simboli che si scelgono`() {
    openCard {
      FluidMenuActions(elementSize = FluidMenuElementSize.Small) {
        listOf(
          FluidContextAction("Condividi", Icons.Rounded.Share) { picked += "Condividi" },
          FluidContextAction("Preferiti", Icons.Rounded.Star) { picked += "Preferiti" },
          FluidContextAction("Elimina", Icons.Rounded.Delete, startsSection = true) { picked += "Elimina" },
        )
      }
    }
    val share = compose.onNodeWithContentDescription("Condividi").fetchSemanticsNode().boundsInRoot
    val star = compose.onNodeWithContentDescription("Preferiti").fetchSemanticsNode().boundsInRoot
    // Senza etichetta visibile, affiancati sulla stessa fila; la lista sotto.
    compose.onNodeWithText("Condividi").assertDoesNotExist()
    assertEquals(share.top, star.top, 0.5f)
    assertTrue(star.left >= share.right - 0.5f)
    assertTrue(rowTop("Elimina") >= share.bottom)

    compose.onNodeWithContentDescription("Preferiti").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Preferiti"), picked)
  }
}
