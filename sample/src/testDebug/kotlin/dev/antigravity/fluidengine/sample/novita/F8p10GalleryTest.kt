package dev.antigravity.fluidengine.sample.novita

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F8.10 · Le scene della condivisione nella pagina «Elementi nuovi»: ognuna ha il suo nome breve, si apre e sta in piedi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi", application = dev.antigravity.fluidengine.sample.GlassBenchDevice::class, shadows = [dev.antigravity.fluidengine.sample.GlassBenchHeap::class])
class F8p10GalleryTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  @Test fun everySceneOpensAndStands() {
    assertEquals(
      listOf("condividi-voto", "immagini-tre-formati", "prepara-foto", "pdf-pagella", "condividi-link", "arrivi"),
      F8p10Scenes.map { it.id },
    )
    assertTrue(NewElementSections.any { it.pack == "F8.10" && it.scenes === F8p10Scenes })
    var wanted by mutableStateOf<String?>(null)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { key(wanted) { NewElementsPage(onBack = {}, initial = wanted) } }
    }
    for (scene in F8p10Scenes) {
      wanted = scene.id
      compose.waitForIdle()
      assertTrue("${scene.id} non si vede", compose.onAllNodesWithText(scene.title).fetchSemanticsNodes().isNotEmpty())
    }
  }
}
