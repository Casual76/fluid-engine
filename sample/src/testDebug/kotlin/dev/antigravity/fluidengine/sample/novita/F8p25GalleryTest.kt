package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.widget.FluidQuickTileTags
import dev.antigravity.fluidengine.widget.FluidWidgetShowcaseTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.25 nella pagina «Elementi nuovi»: la riga del pacchetto, le cinque scene che si aprono (F8.25b: il riquadro rapido
 * nelle impostazioni dell'app, non nella vetrina), lo stato pubblicato dal
 * telefono che arriva alla Tile, e il preferito del polso che passa da «In coda» a «Registrato» una volta sola anche
 * dopo un riavvio dell'orologio e una riconsegna.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class F8p25GalleryTest {
  @get:Rule val compose = createComposeRule()

  private fun open(id: String) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize()) { NewElementsPageAt(onBack = {}, initial = id) }
      }
    }
    frames(600)
  }

  private fun frames(millis: Long) {
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeBy(millis)
  }

  private fun hasText(text: String) =
    compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  @Test fun laRigaDelPacchetto() {
    val section = NewElementSections.single { it.pack == "F8.25" }
    assertEquals(listOf("widget-vetrina", "impostazioni-app", "wear-tile", "wear-stato", "wear-ricevuta"), section.scenes.map { it.id })
    assertTrue(F8p25TryItems.all { it.id.startsWith("f8-25-") })
  }

  @Test fun ogniScenaSiApre() {
    var scene by mutableStateOf("widget-vetrina")
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize()) { androidx.compose.runtime.key(scene) { NewElementsPageAt(onBack = {}, initial = scene) } }
      }
    }
    val marks = mapOf(
      "widget-vetrina" to "Aggiungi alla Home",
      "impostazioni-app" to "Aggiungi alle Impostazioni rapide",
      "wear-tile" to "Le complicazioni",
      "wear-stato" to "Pubblica: Matematica",
      "wear-ricevuta" to "Salva un preferito",
    )
    for ((id, mark) in marks) {
      scene = id
      frames(800)
      assertTrue("$id: $mark", hasText(mark))
    }
  }

  @Test fun laVetrinaMostraIDueWidget() {
    open("widget-vetrina")
    compose.onNodeWithTag(FluidWidgetShowcaseTags.card("Agenda")).assertExists()
    // La vetrina e' un elemento solo della pagina: la seconda scheda e' gia' composta. (Scorrere con l'orologio fermo
    // non finirebbe mai.)
    compose.onNodeWithTag(FluidWidgetShowcaseTags.card("Assenze"), useUnmergedTree = true).assertExists()
    // F8.25b, scelta di Alessio: il riquadro delle Impostazioni rapide non sta mai nella vetrina.
    compose.onNodeWithTag(FluidQuickTileTags.Card, useUnmergedTree = true).assertDoesNotExist()
    assertTrue(!hasText("Aggiungi alle Impostazioni rapide"))
  }

  @Test fun ilRiquadroStaNelleImpostazioniDellApp() {
    open("impostazioni-app")
    compose.onNodeWithTag(FluidQuickTileTags.Card, useUnmergedTree = true).assertExists()
    assertTrue(hasText("Impostazioni rapide"))
    assertTrue(hasText("Aggiungi alle Impostazioni rapide"))
    // Niente widget qui: la vetrina e' un'altra scena.
    assertTrue(!hasText("Aggiungi alla Home"))
  }

  @Test fun loStatoDelTelefonoArrivaAllaTile() {
    open("wear-stato")
    val tile = compose.onNodeWithTag(F8p25Tags.tile("stato"))
    assertTrue(tile.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().startsWith("In attesa"))
    compose.onNodeWithTag(F8p25Tags.Publish).performClick()
    frames(300)
    assertEquals(
      "Matematica. 10:00. Aula 3 · fra 12 min",
      compose.onNodeWithTag(F8p25Tags.tile("stato")).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single(),
    )
    compose.onNodeWithTag(F8p25Tags.Finish).performClick()
    frames(300)
    assertTrue(
      compose.onNodeWithTag(F8p25Tags.tile("stato")).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        .endsWith("Non aggiornato"),
    )
  }

  @Test fun ilPreferitoDelPolsoArrivaUnaVoltaSola() {
    open("wear-ricevuta")
    // Il telefono si allontana; il preferito resta in coda anche dopo un riavvio dell'orologio.
    compose.onNodeWithTag(F8p25Tags.Near).performClick()
    frames(500)
    compose.onNodeWithTag(F8p25Tags.Favorite).performClick()
    frames(1_000)
    assertTrue(hasText("In coda"))
    assertTrue(hasText("Niente di salvato."))
    compose.onNodeWithTag(F8p25Tags.Restart).performClick()
    frames(1_000)
    assertTrue("dopo il riavvio la coda c'e' ancora", hasText("In coda"))
    // Il telefono torna: il preferito arriva, si salva, e torna la ricevuta.
    compose.onNodeWithTag(F8p25Tags.Near).performClick()
    frames(1_500)
    assertTrue(hasText("Registrato"))
    assertTrue(hasText("preferito · {\"brano\":\"1\"}"))
    // Riconsegnare non salva due volte: la ripetizione si riconosce.
    compose.onNodeWithTag(F8p25Tags.Resend).performClick()
    frames(1_000)
    assertEquals(1, compose.onAllNodesWithText("preferito ·", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  @Test fun ilComandoCheScadeNonSiApplica() {
    open("wear-ricevuta")
    compose.onNodeWithTag(F8p25Tags.Near).performClick()
    frames(500)
    compose.onNodeWithTag(F8p25Tags.Play).performClick()
    frames(500)
    assertTrue(hasText("In coda"))
    // Dieci secondi col telefono lontano: scade al polso, e al ritorno del telefono non si applica.
    frames(11_000)
    compose.onNodeWithTag(F8p25Tags.Near).performClick()
    frames(1_500)
    assertTrue(hasText("Scaduto"))
    assertTrue(hasText("Niente di salvato."))
  }
}
