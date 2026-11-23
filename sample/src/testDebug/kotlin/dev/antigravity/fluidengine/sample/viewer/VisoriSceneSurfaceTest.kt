package dev.antigravity.fluidengine.sample.viewer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.ViewerGallery
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6C.20 · **Il modello 3D aperto dalla galleria accende la sua superficie.** Sul Tab S9 (riprese di F6C.20) «Anelli»
 * aperto da «Visori» restava una pagina vuota: il file si leggeva (titolo, «Riproduci»), ma la sessione di Filament non
 * nasceva mai (nessun «FEngine created» nel logcat all'apertura). La pagina accende la superficie solo quando è la pagina
 * ferma del visore e il suo ciclo di vita è almeno RESUMED.
 *
 * Sulla JVM Filament non si carica: una superficie che prova davvero a partire fallisce e la pagina lo dice («Il modello
 * non si apre su questo dispositivo»). Qui quel messaggio è la prova che la superficie è stata accesa; una superficie
 * tenuta spenta lascia la pagina vuota, come sul tablet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class VisoriSceneSurfaceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val seen = mutableMapOf<String, FluidViewerDocument>()
  private val deps = VisoriDeps(opened = { file, document -> seen[file.id] = document })

  private fun waitFor(what: String, condition: () -> Boolean) {
    try {
      compose.waitUntil(10_000) { condition() }
    } catch (e: ComposeTimeoutException) {
      throw AssertionError("Non compare: $what", e)
    }
  }

  private fun shown(text: String) =
    compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun described(text: String) =
    compose.onAllNodesWithContentDescription(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes()
      .isNotEmpty()

  private val surfaceStarted get() = shown("Il modello non si apre su questo dispositivo")

  @Test fun ilModelloApertoDallaGalleriaAccendeLaSuperficie() {
    compose.setContent { FluidTheme(settings = remember { EngineSettings() }) { ViewerGallery(deps) {} } }
    compose.waitForIdle()
    compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("visori-modello-glb"))
    compose.onNodeWithTag("visori-modello-glb").performClick()
    compose.waitForIdle()
    waitFor("anelli aperto") { seen["modello-glb"] != null }
    assertEquals("3d", seen.getValue("modello-glb").type.family)
    waitFor("la pagina del modello") { described("Modello 3D") || surfaceStarted }
    waitFor("la superficie del modello accesa (sulla JVM Filament manca: la pagina dice che non si apre)") { surfaceStarted }
  }
}
