package dev.antigravity.fluidengine.ai.mcp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F9.2 - La pagina «Connettori» col server finto: TalkBack (le righe, la regola di ogni strumento, l'avviso sulla
 * descrizione truccata), «Riduci movimento» (una parola ferma al posto della rotella), l'aggiunta da indirizzo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidConnectorsPageTest {
  @get:Rule val compose = createComposeRule()

  /** Un server che si ferma su tools/list finche' la prova non lo lascia andare: la pagina resta "in corso". */
  private class Gate(val server: McpMemoryServer) : McpTransport {
    @Volatile var hold: CompletableDeferred<Unit>? = null
    override suspend fun send(request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange {
      if ((request.body?.get("method") as? JsonPrimitive)?.content == "tools/list") hold?.await()
      return server.send(request, onMessage)
    }
  }

  private val gate = Gate(calendarServer())
  private val connectors = McpConnectors(
    McpConnectorStore(tempPrefs("page-c")),
    McpTokenStore(tempPrefs("page-t"), ReversingCipher()),
    McpOAuth(FakeAuthHttp(), "dev.pampa.prova:/mcp", "Prova"),
    transport = { gate },
  )
  private val opened = mutableListOf<String>()

  private fun show(reduced: Boolean = false, withConnector: Boolean = true) {
    if (withConnector) runBlocking { connectors.add(McpConnectorLink("https://cal.example.com/mcp", "Calendario")) }
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          FluidConnectorsPage(connectors, onOpenUrl = { opened += it }, onScanQr = { "https://qr.example.com/mcp#nome=Dal%20QR" })
        }
      }
    }
  }

  private fun waitText(text: String, substring: Boolean = false) =
    compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun `TalkBack legge le righe, la regola di ogni strumento e l'avviso sulla descrizione truccata`() {
    show()
    waitText("Collegato · 3 strumenti")
    compose.onAllNodesWithText("Calendario").fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
    compose.onAllNodesWithText("Collegato · 3 strumenti")[0].performClick()
    waitText("STRUMENTI")
    // La regola: il nome dello strumento nella descrizione, la scelta nello stato.
    val policy = compose.onAllNodes(hasContentDescription("Prima di usarlo: Crea evento"), useUnmergedTree = true).fetchSemanticsNodes().single()
    assertEquals("Chiedi", policy.config.getOrNull(SemanticsProperties.StateDescription))
    // L'avviso della descrizione truccata dice di quale strumento parla.
    val warning = compose.onAllNodesWithContentDescription("Descrizione sospetta: all'assistente arriva solo il nome: Somma", useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals(1, warning.size)
    // Le note del servizio si leggono, con la frase che dice che all'assistente non arrivano.
    waitText("Usa sempre evento_crea", substring = true)
    waitText("Le scrive il servizio", substring = true)
    // I tasti si toccano.
    assertTrue(compose.onAllNodes(hasText("Aggiorna") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
    assertTrue(compose.onAllNodes(hasText("Togli connettore") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun `con Riduci movimento il lavoro e' una parola ferma, senza rotella`() {
    show(reduced = true)
    waitText("Collegato · 3 strumenti")
    compose.onAllNodesWithText("Collegato · 3 strumenti")[0].performClick()
    waitText("Aggiorna")
    gate.hold = CompletableDeferred()
    compose.onAllNodes(hasText("Aggiorna") and hasClickAction())[0].performClick()
    waitText("In corso…")
    assertEquals(0, compose.onAllNodesWithContentDescription("In corso…", useUnmergedTree = true).fetchSemanticsNodes().size)
    gate.hold?.complete(Unit)
    compose.waitUntil(10_000) { compose.onAllNodesWithText("In corso…").fetchSemanticsNodes().isEmpty() }
  }

  @Test
  fun `senza Riduci movimento la rotella c'e', e dice cosa fa`() {
    show(reduced = false)
    waitText("Collegato · 3 strumenti")
    compose.onAllNodesWithText("Collegato · 3 strumenti")[0].performClick()
    waitText("Aggiorna")
    gate.hold = CompletableDeferred()
    compose.onAllNodes(hasText("Aggiorna") and hasClickAction())[0].performClick()
    waitText("In corso…")
    assertEquals(1, compose.onAllNodesWithContentDescription("In corso…", useUnmergedTree = true).fetchSemanticsNodes().size)
    gate.hold?.complete(Unit)
  }

  @Test
  fun `aggiungere da indirizzo, l'indirizzo sbagliato e il QR`() {
    show(withConnector = false)
    waitText("Nessun connettore")
    compose.onNode(hasSetTextAction()).performTextInput("ftp://x")
    compose.onAllNodes(hasText("Aggiungi") and hasClickAction())[0].performClick()
    waitText("Serve un indirizzo https")
    // Il QR porta l'indirizzo e il nome: il connettore si apre subito.
    compose.onAllNodesWithText("Scansiona un QR")[0].performClick()
    waitText("Dal QR")
    assertEquals("Dal QR", runBlocking { connectors.views.first() }.single().connector.name)
  }
}
