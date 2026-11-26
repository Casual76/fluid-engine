package dev.antigravity.fluidengine.sample.novita

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.antigravity.fluidengine.ai.keys.SecretCipher
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F9.2 · Le scene della galleria: la ricerca dei tool sul catalogo vero di PampAI, la tabella dei gettoni, e la pagina
 * «Connettori» sui server finti con l'accesso del NAS (il browser finto approva e torna).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F9p2ScenesUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Il Keystore non c'e' in Robolectric: i gettoni del NAS finto si "cifrano" cosi'. */
  private object PlainCipher : SecretCipher {
    override fun encrypt(plain: String): String = "p:" + plain.reversed()
    override fun decrypt(blob: String): String? = blob.removePrefix("p:").reversed().takeIf { blob.startsWith("p:") }
  }

  @Before fun cipher() {
    F9p2Demo.cipher = PlainCipher
  }

  private fun open(scene: String) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        NewElementsPageAt(onBack = {}, initial = scene)
      }
    }
  }

  private fun waitText(text: String, substring: Boolean = false) =
    compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun `la ricerca dei tool trova il meteo, e le altre app non sono di serie`() {
    open("ricerca-tool")
    waitText("strumenti di PampAI", substring = true)
    // Di serie solo PampAI: 69 strumenti, le altre app (139) raggiungibili.
    waitText("69 strumenti di PampAI e cerca_strumenti")
    waitText("139 strumenti delle altre app, caricati quando servono")
    waitText("meteo_", substring = true)
    compose.onNode(hasSetTextAction()).performTextClearance()
    compose.onNode(hasSetTextAction()).performTextInput("quando passa il 23 alla fermata")
    waitText("bus_", substring = true)
  }

  @Test
  fun `la tabella dei gettoni si legge`() {
    open("gettoni")
    waitText("LE CINQUE DOMANDE")
    assertTrue(compose.onAllNodesWithTag(F9p2Tags.Chart).fetchSemanticsNodes().isNotEmpty())
    waitText("Meteo")
    waitText("Generica")
  }

  @Test
  fun `i connettori finti, l'accesso del NAS col browser finto e la descrizione truccata`() {
    open("connettori")
    waitText("Calendario")
    waitText("Serve l'accesso")
    compose.onAllNodesWithText("NAS di casa")[0].performClick()
    waitText("Entra")
    compose.onAllNodes(hasText("Entra") and hasClickAction())[0].performClick()
    // Il browser finto approva e torna: il NAS e' collegato coi suoi strumenti.
    compose.waitUntil(15_000) { compose.onAllNodesWithText("Cerca file").fetchSemanticsNodes().isNotEmpty() }
    val connectors = F9p2Demo.connectors(compose.activity.applicationContext)
    val nas = runBlocking { connectors.views.first() }.single { it.connector.name == "NAS di casa" }
    assertTrue(nas.signedIn)
    assertEquals(2, nas.tools.size)
  }
}
