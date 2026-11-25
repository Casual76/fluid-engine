package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.revisions.FluidVersionChoiceTags
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
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
 * F8.21 · Le scene della galleria: due dispositivi finti nella stessa scena che si sincronizzano (anche senza rete, poi
 * allineati), e un conflitto risolto col foglio di vetro. I motori girano davvero, fuori dal thread principale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp-xhdpi")
class F8p21SyncScenesUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  /** Aspetta [condition] facendo andare l'orologio: i motori lavorano su altri thread, in tempo vero. */
  private fun waitFor(what: String, condition: () -> Boolean) {
    repeat(600) {
      if (condition()) return
      advance(32)
      Thread.sleep(4)
    }
    throw AssertionError("non e' successo: $what")
  }

  private fun open(scene: String) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            NewElementsPageAt(onBack = {}, initial = scene)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(600)
  }

  private fun has(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun click(tag: String) {
    compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(64)
  }

  private fun clickText(text: String) {
    compose.onNodeWithText(text, substring = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(64)
  }

  @Test fun `le scene sono nell'indice coi nomi brevi`() {
    assertEquals(listOf("sync-due-dispositivi", "sync-conflitto", "sync-fermo"), F8p21Section.scenes.map { it.id })
    assertTrue(NewElementSections.contains(F8p21Section))
  }

  @Test fun `due dispositivi - una voce aggiunta senza rete arriva all'altro quando la rete torna`() {
    open("sync-due-dispositivi")
    waitFor("il tablet riceve le voci iniziali") { has(F8p21Tags.item("tablet", "Borraccia")) }
    // Il tablet perde la rete; il telefono aggiunge una voce, il tablet ne aggiunge un'altra.
    click(F8p21Tags.network("tablet"))
    click(F8p21Tags.add("telefono"))
    waitFor("la voce nasce subito sul telefono") { has(F8p21Tags.item("telefono", "Crema solare")) }
    click(F8p21Tags.add("tablet"))
    advance(2_000)
    // Senza rete il tablet scrive lo stesso: la sua voce (anche lui «Crema solare», perche' non sa del telefono) aspetta.
    assertEquals(1, compose.onAllNodes(hasTestTag(F8p21Tags.item("tablet", "Crema solare")), useUnmergedTree = true).fetchSemanticsNodes().size)
    // Una voce sola, con tre campi: una modifica in attesa, non tre (visto sul tablet, 25/11/2026).
    assertTrue(compose.onAllNodes(hasText("Senza rete · 1 modifica in attesa")).fetchSemanticsNodes().isNotEmpty())
    // La rete torna: due aggiunte fatte insieme restano tutte e due, su tutti e due i dispositivi.
    click(F8p21Tags.network("tablet"))
    waitFor("le due liste si allineano") {
      listOf("telefono", "tablet").all { device ->
        compose.onAllNodes(hasTestTag(F8p21Tags.item(device, "Crema solare")), useUnmergedTree = true).fetchSemanticsNodes().size == 2
      }
    }
    waitFor("nessuna modifica in attesa") {
      compose.onAllNodes(hasText("in attesa", substring = true)).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test fun `un conflitto si risolve col foglio e vale su tutti e due`() {
    open("sync-conflitto")
    waitFor("la nota arriva al tablet") {
      compose.onAllNodes(hasText("davanti a scuola", substring = true)).fetchSemanticsNodes().size >= 2
    }
    clickText("1 · Senza rete")
    advance(800)
    clickText("2 · Torna la rete")
    waitFor("il conflitto si vede sul telefono") { has(F8p21Tags.choose("telefono")) }
    click(F8p21Tags.choose("telefono"))
    waitFor("il foglio si apre") { has(FluidVersionChoiceTags.Pane) }
    advance(600)
    // Si sceglie la versione del telefono (7:45).
    val card = compose.onAllNodes(hasText("7:45", substring = true), useUnmergedTree = false).fetchSemanticsNodes()
      .first { node -> node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) }
    compose.onNode(androidx.compose.ui.test.SemanticsMatcher("card") { it.id == card.id }).performSemanticsAction(SemanticsActions.OnClick)
    advance(300)
    click(FluidVersionChoiceTags.Keep)
    waitFor("il foglio si chiude") { !has(FluidVersionChoiceTags.Pane) }
    waitFor("tutti e due hanno la versione scelta e nessun conflitto") {
      compose.onAllNodes(hasText("alle 7:45 in stazione", substring = true)).fetchSemanticsNodes().size >= 2 &&
        !has(F8p21Tags.choose("telefono")) && !has(F8p21Tags.choose("tablet"))
    }
  }

  @Test fun `fermo per quattro mesi - rilegge tutto e non perde niente`() {
    open("sync-fermo")
    waitFor("il vecchio tablet ha la lista") { has(F8p21Tags.item("vecchio", "Crema solare")) }
    clickText("1 · Il vecchio tablet")
    waitFor("la voce nata sul vecchio tablet") { has(F8p21Tags.item("vecchio", "Pile per la torcia")) }
    clickText("2 · Sul telefono")
    waitFor("la crema se ne va dal telefono") { !has(F8p21Tags.item("telefono", "Crema solare")) && has(F8p21Tags.item("telefono", "Ombrello")) }
    clickText("3 · Passano quattro mesi")
    waitFor("il relay toglie la pietra tombale") { compose.onAllNodes(hasText("pietra tombale", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    clickText("4 · Il vecchio tablet si riaccende")
    waitFor("rilegge tutto") { compose.onAllNodes(hasText("Ha riletto tutto", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    waitFor("le due liste sono uguali: la voce del vecchio c'e', la crema no, l'ombrello si'") {
      has(F8p21Tags.item("telefono", "Pile per la torcia")) && !has(F8p21Tags.item("vecchio", "Crema solare")) &&
        has(F8p21Tags.item("vecchio", "Ombrello"))
    }
  }
}
