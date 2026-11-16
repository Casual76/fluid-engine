package dev.antigravity.fluidengine.sample.assistant

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.aiui.chat.FluidComposer
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.FluidGlassRoundButton
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantActions
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantCard
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantDock
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantTextBar
import dev.antigravity.fluidengine.aiui.overlay.FluidChipPill
import dev.antigravity.fluidengine.aiui.overlay.FluidCollapsedPill
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
import org.junit.Assert.assertFalse
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
 * La pagina «Assistente» (F3.2b, F3.2e) in un albero vero, e le superfici di `engine-ai-ui` col contenitore: da soli
 * sulla pagina i pezzi piccoli **e i pannelli** (card, dock, barra di scrittura, pillola ridotta, capsula del composer)
 * sono **un contenitore con un pezzo solo** (il patto dell'inchiostro: girano o scuriscono); i pezzi che i pannelli
 * portano (stop, chip, tasti tondi, il tasto del dock) **non** sono pezzi (vetro annidato del pannello); dentro un
 * contenitore dell'app sono pezzi di quello. Il `modifier` dell'app arriva al vetro (`weight`, misura imposta). La pagina
 * si apre, cambia fondo, card e vetro senza eccezioni, e il fondo che cambia non apre viaggi.
 *
 * Non prova come si vede né il giro: Robolectric non disegna e la sonda non legge. Il giro, lo scurimento e l'inchiostro
 * si guardano sul tablet (`artifacts/liquid-glass/sessioni/f3-2b/riprese.md`, `sessioni/f3-2e/riprese.md`).
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

  /** I pannelli della pagina (F3.2e): pillola ridotta, barra di scrittura, card, dock e composer. */
  private val Panels = 5

  /** Un contenitore per pezzo piccolo da solo e uno per pannello. */
  private val PageContainers = AlonePieces + Panels

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
  fun `la pagina si apre e ogni pezzo e ogni pannello da solo e' un contenitore con un pezzo`() {
    val before = containers
    openPage()
    compose.onNodeWithTag("assistant-page").assertExists()
    compose.onNodeWithText("F3.2e ·", substring = true).assertExists()
    // Undici contenitori: uno per pezzo piccolo che sta da solo e uno per pannello. I due chip della card, i due tasti
    // tondi del composer e il tasto dell'engine del dock non ne aggiungono: sono vetro annidato del loro pannello.
    assertEquals("contenitori della pagina", PageContainers, containers - before)
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
    assertEquals(PageContainers, containers - before)
  }

  @Test
  fun `card al lavoro, tema scuro e vetro di prima si aprono senza eccezioni`() {
    val before = containers
    openPage()
    // La card al lavoro porta lo stop annidato: nessun contenitore in più.
    compose.onNodeWithTag("assistant-card").performClick()
    advance(400)
    compose.onNodeWithText("Card: al lavoro").assertExists()
    assertEquals(PageContainers, containers - before)
    compose.onNodeWithTag("assistant-theme").performClick()
    advance(400)
    compose.onNodeWithText("Tema: scuro").assertExists()
    // Col vetro di prima i contenitori ci sono ma non disegnano: ogni pezzo è il tasto di sempre.
    compose.onNodeWithTag("assistant-model").performClick()
    advance(400)
    compose.onNodeWithText("Vetro: ${FluidGlassModel.Classic.name}").assertExists()
    assertEquals(PageContainers, containers - before)
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
  fun `la card e' un pezzo che scurisce, e i suoi chip non sono pezzi`() {
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
    // Un contenitore, quello della card; un pezzo solo, la card, che esporta il suo materiale ai due chip annidati.
    assertEquals(1, containers - before)
    compose.onNodeWithText("meteo").assertExists()
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals("solo la card è un pezzo: $rest", 1, rest.size)
    assertTrue("la card esporta il suo materiale: $rest", rest.single().exports)
    assertTrue("la card scurisce in continuo", FluidGlassContainerDiagnostics.lastRestLarge)
  }

  @Test
  fun `la card al lavoro porta lo stop annidato, non un pezzo`() {
    val before = containers
    showAlone { stage ->
      FluidAssistantCard(
        state = sampleState(AssistantCardState.Working),
        pending = null,
        backdrop = stage,
        onStop = {},
        onChip = {},
        onConfirm = { _, _ -> },
      )
    }
    assertEquals(1, containers - before)
    assertEquals(1, FluidGlassContainerDiagnostics.lastRest.size)
  }

  @Test
  fun `il dock e' un pezzo che scurisce, e il suo tasto di vetro non e' un pezzo`() {
    val before = containers
    showAlone { stage ->
      FluidAssistantDock(
        state = sampleState(AssistantCardState.Answer),
        micLevel = MicLevel(0f, speaking = false),
        pending = null,
        backdrop = stage,
        actions = FluidAssistantActions(onAskText = {}, onAskVoice = {}),
      )
    }
    assertEquals(1, containers - before)
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals("solo il dock è un pezzo: $rest", 1, rest.size)
    assertTrue("il dock scurisce, anche se è Floating", FluidGlassContainerDiagnostics.lastRestLarge)
  }

  @Test
  fun `la barra di scrittura e' un pezzo che gira`() {
    val before = containers
    showAlone { stage ->
      FluidAssistantTextBar(
        backdrop = stage,
        busy = false,
        micAvailable = true,
        onSend = {},
        onVoice = {},
        onNewConversation = {},
        autoFocus = false,
      )
    }
    assertEquals(1, containers - before)
    assertEquals(1, FluidGlassContainerDiagnostics.lastRest.size)
    assertFalse("la barra è Modal ma gira", FluidGlassContainerDiagnostics.lastRestLarge)
  }

  @Test
  fun `la pillola ridotta e' un pezzo che gira, e la misura dell'app arriva al vetro`() {
    val before = containers
    showAlone { stage ->
      FluidCollapsedPill(text = "Sole, 24 °C", backdrop = stage, onExpand = {}, modifier = Modifier.width(300.dp))
    }
    assertEquals(1, containers - before)
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals(1, rest.size)
    assertFalse(FluidGlassContainerDiagnostics.lastRestLarge)
    // xhdpi: 300 dp = 600 px. Il `modifier` sta attorno al contenitore, che passa i minimi alla capsula.
    assertEquals("la larghezza dell'app è quella del vetro: $rest", 600f, rest.single().bounds.width, 0.5f)
  }

  @Test
  fun `la capsula del composer e' un pezzo che gira, e i tasti tondi non sono pezzi`() {
    val before = containers
    showAlone { stage ->
      FluidComposer(
        value = "",
        onValueChange = {},
        backdrop = stage,
        action = FluidComposerAction.Voice,
        onSend = {},
        requestMicPermission = false,
        leading = { controls -> FluidGlassRoundButton(FluidIcons.Mic, "Allega", controls, onClick = {}) },
      )
    }
    assertEquals(1, containers - before)
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals("solo la capsula è un pezzo: $rest", 1, rest.size)
    assertTrue("esporta il suo materiale ai tasti, come prima: $rest", rest.single().exports)
    assertFalse("larga, ma è una barra: gira", FluidGlassContainerDiagnostics.lastRestLarge)
  }

  @Test
  fun `un pezzo piccolo da solo riceve il weight dell'app`() {
    showAlone { stage ->
      Row(Modifier.width(300.dp)) {
        FluidChipPill(label = "meteo", backdrop = stage, onClick = {}, modifier = Modifier.weight(1f))
      }
    }
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals("il weight allarga il vetro del chip, non solo il contenitore: $rest", 600f, rest.single().bounds.width, 0.5f)
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
