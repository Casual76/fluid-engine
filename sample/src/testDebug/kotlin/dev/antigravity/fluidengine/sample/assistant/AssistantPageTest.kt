package dev.antigravity.fluidengine.sample.assistant

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.chat.FluidGlassRoundButton
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantCard
import dev.antigravity.fluidengine.aiui.overlay.FluidChipPill
import dev.antigravity.fluidengine.aiui.overlay.FluidListeningHint
import dev.antigravity.fluidengine.aiui.overlay.FluidStopPill
import dev.antigravity.fluidengine.aiui.overlay.LocalFluidAssistantPanel
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
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
 * La pagina «Assistente» (F3.2b) in un albero vero, e i pezzi di `engine-ai-ui` col contenitore: da soli sulla pagina i
 * pezzi piccoli sono **un contenitore con un pezzo solo** (il patto dell'inchiostro: girano), sopra la card, il dock e il
 * composer no (vetro annidato del lato del pannello), dentro un contenitore dell'app sono pezzi di quello. La pagina si
 * apre, cambia fondo, card e vetro senza eccezioni, e il fondo che cambia non apre viaggi.
 *
 * Non prova come si vede né il giro: Robolectric non disegna e la sonda non legge. Il giro e l'inchiostro si guardano sul
 * tablet (`artifacts/liquid-glass/sessioni/f3-2b/riprese.md`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp-xhdpi")
class AssistantPageTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  /** Fa passare [ms] di tempo finto a fotogrammi da 16 ms, col filo principale al passo. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private val containers: Int get() = FluidGlassContainerDiagnostics.liveContainers

  /** I pezzi che la pagina posa da soli: due tasti tondi, lo stop, due chip e la capsula dell'ascolto. */
  private val AlonePieces = 6

  private fun openPage(config: AssistantConfig = AssistantConfig()): AssistantLive {
    val live = AssistantLive()
    compose.setContent { AssistantContent(config, live, bottomInset = 0.dp, onBack = {}) }
    compose.waitForIdle()
    advance(400)
    return live
  }

  /** Un pezzo solo dentro il tema della galleria, sul fondale di una sorgente, come lo posa un'app. */
  private fun showAlone(content: @Composable (GlassBackdropState) -> Unit) {
    compose.setContent {
      FluidTheme(settings = EngineSettings(), systemBars = false) {
        val stage = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides stage) { content(stage) }
      }
    }
    compose.waitForIdle()
    advance(400)
  }

  @Test
  fun `la pagina si apre e ogni pezzo piccolo da solo e' un contenitore con un pezzo`() {
    val before = containers
    openPage()
    compose.onNodeWithTag("assistant-page").assertExists()
    compose.onNodeWithText("F3.2b ·", substring = true).assertExists()
    // Sei contenitori: uno per pezzo che sta da solo. La card, il dock, la barra e il composer ne aggiungono zero, anche
    // se portano due chip, due tasti tondi e un tasto dell'engine: sono vetro annidato del loro pannello.
    assertEquals("contenitori della pagina", AlonePieces, containers - before)
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals("un riposo d'un contenitore di un pezzo solo: $rest", 1, rest.size)
    assertEquals("il pezzo è un grappolo di uno: $rest", 1, rest.single().clusterSize)
  }

  @Test
  fun `il fondo che cambia non apre viaggi e non rifa' la pagina`() {
    val before = containers
    val live = openPage()
    assertEquals(AssistantFund.Light, live.fund)
    compose.onNodeWithTag("assistant-fund").performClick()
    advance(200)
    assertEquals(AssistantFund.Dark, live.fund)
    compose.onNodeWithTag("assistant-fund").performClick()
    advance(200)
    assertEquals(AssistantFund.Photo, live.fund)
    assertEquals(2, live.step)
    assertEquals("cambiare fondo non è un cambio di stato dei vetri", 0L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(AlonePieces, containers - before)
  }

  @Test
  fun `card al lavoro, tema scuro e vetro di prima si aprono senza eccezioni`() {
    val before = containers
    openPage()
    // La card al lavoro porta lo stop annidato: nessun contenitore in più.
    compose.onNodeWithTag("assistant-card").performClick()
    advance(400)
    compose.onNodeWithText("Card: al lavoro").assertExists()
    assertEquals(AlonePieces, containers - before)
    compose.onNodeWithTag("assistant-theme").performClick()
    advance(400)
    compose.onNodeWithText("Tema: scuro").assertExists()
    // Col vetro di prima i contenitori ci sono ma non disegnano: ogni pezzo è il tasto di sempre.
    compose.onNodeWithTag("assistant-model").performClick()
    advance(400)
    compose.onNodeWithText("Vetro: ${FluidGlassModel.Classic.name}").assertExists()
    assertEquals(AlonePieces, containers - before)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalOrderWarnings)
  }

  @Test
  fun `il tasto tondo, lo stop, il chip e l'ascolto da soli si fanno un contenitore`() {
    val before = containers
    showAlone { stage ->
      Row {
        FluidGlassRoundButton(FluidIcons.Mic, "Parla", stage, onClick = {})
        FluidStopPill(stage, onStop = {})
        FluidChipPill(label = "meteo", backdrop = stage, onClick = {})
      }
      FluidListeningHint(backdrop = stage, elapsedMillis = 0L, onStop = {})
    }
    assertEquals(4, containers - before)
  }

  @Test
  fun `su un pannello che lo dice i pezzi restano annidati`() {
    val before = containers
    showAlone { stage ->
      CompositionLocalProvider(LocalFluidAssistantPanel provides true) {
        Row {
          FluidGlassRoundButton(FluidIcons.Mic, "Parla", stage, onClick = {})
          FluidStopPill(stage, onStop = {})
          FluidChipPill(label = "meteo", backdrop = stage, onClick = {})
        }
      }
    }
    assertEquals(0, containers - before)
  }

  @Test
  fun `la card porta i suoi chip e il suo stop senza contenitori`() {
    val before = containers
    showAlone { stage ->
      FluidAssistantCard(
        state = sampleState(AssistantCardState.Answer),
        pending = null,
        backdrop = stage,
        onStop = {},
        onChip = {},
        onConfirm = { _, _ -> },
      )
    }
    assertEquals(0, containers - before)
    compose.onNodeWithText("meteo").assertExists()
  }

  @Test
  fun `dentro un contenitore dell'app i tasti tondi sono pezzi di quello`() {
    val before = containers
    showAlone { stage ->
      FluidGlassContainer {
        Row {
          FluidGlassRoundButton(FluidIcons.Mic, "Parla", stage, onClick = {})
          FluidGlassRoundButton(FluidIcons.Mic, "Ancora", stage, onClick = {})
        }
      }
    }
    // Un contenitore solo, quello dell'app, e i due tasti sono suoi pezzi (vicini: un grappolo di due).
    assertEquals(1, containers - before)
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals("due pezzi nel contenitore dell'app: $rest", 2, rest.size)
    assertTrue("i due tasti vicini sono un grappolo: $rest", rest.all { it.clusterSize == 2 })
  }
}
