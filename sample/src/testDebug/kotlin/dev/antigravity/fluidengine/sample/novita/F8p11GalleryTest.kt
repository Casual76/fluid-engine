package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F8.11 nella pagina «Elementi nuovi»: la riga del pacchetto, le otto scene che si aprono, il caso delle novità. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class F8p11GalleryTest {
  @get:Rule val compose = createComposeRule()

  private fun open(id: String, memory: FluidFirstUseMemory = FluidFirstUseMemory.inMemory()) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host, LocalFluidFirstUseMemory provides memory) {
          Box(Modifier.fillMaxSize()) {
            NewElementsPageAt(onBack = {}, initial = id)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    frames(600)
  }

  private fun frames(millis: Long) {
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeBy(millis)
  }

  @Test fun laRigaDelPacchetto() {
    val section = NewElementSections.single { it.pack == "F8.11" }
    assertEquals(8, section.scenes.size)
    assertEquals(
      listOf("benvenuto", "novita", "benvenuto-passi", "benvenuto-testo-grande", "permessi", "sfondi-vivi", "traguardo", "avvio-pronto"),
      section.scenes.map { it.id },
    )
    assertTrue(F8p11TryItems.all { it.id.startsWith("f8-11-") })
  }

  @Test fun ogniScenaSiApre() {
    var scene by mutableStateOf("benvenuto")
    val memory = FluidFirstUseMemory.inMemory()
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host, LocalFluidFirstUseMemory provides memory) {
          Box(Modifier.fillMaxSize()) {
            androidx.compose.runtime.key(scene) { NewElementsPageAt(onBack = {}, initial = scene) }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    val marks = mapOf(
      "benvenuto" to "Benvenuto in Note",
      "novita" to "Aggiorna a 2.1.0",
      "benvenuto-passi" to "Scrivi a modo tuo",
      "benvenuto-testo-grande" to "Note riservate",
      "permessi" to "Scegli le foto da caricare",
      "sfondi-vivi" to "Movimento ridotto (prova)",
      "traguardo" to "giorni di fila",
      "avvio-pronto" to "Riapri",
    )
    for ((id, mark) in marks) {
      scene = id
      frames(800)
      assertTrue("$id: $mark", compose.onAllNodesWithText(mark, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
  }

  @Test fun leNovitaUnaVoltaNellaScena() {
    open("novita")
    // 2.0.0 non ha novità nel manifest: niente foglio.
    compose.onNodeWithText("Novità di Note").assertDoesNotExist()
    compose.onNodeWithTag(F8p11Tags.Update).performClick()
    frames(800)
    compose.onNodeWithText("Novità di Note").assertExists()
    compose.onNodeWithText("Note fissate").assertExists()
    compose.onNodeWithText("Continua").performClick()
    frames(800)
    compose.onNodeWithTag(F8p11Tags.Reopen).performClick()
    frames(800)
    compose.onNodeWithText("Novità di Note").assertDoesNotExist()
  }
}
