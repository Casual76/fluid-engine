package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationAction
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha suonato il tema durante un gesto. */
private const val Title = "Sincronizzazione"

private class RecordedHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * La notifica in-app sotto un dito finto, dentro un albero Compose vero.
 *
 * Quello che qui si prova e' quello che le funzioni pure di `FluidNotificationGestureTest` non possono
 * dire: che il dito sia davvero collegato — un trascinamento in su chiude la notifica, uno corto la
 * lascia, uno in giu' la apre — che un tocco sul tasto e un trascinamento che parte dal tasto
 * restino due cose diverse, e che un dito appoggiato fermi davvero il conto alla rovescia.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidNotificationDragTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = RecordedHaptics()
  private lateinit var state: FluidNotificationHostState
  private lateinit var scope: CoroutineScope
  private var actionClicks = 0

  @Before
  fun reset() {
    actionClicks = 0
  }

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  /**
   * Mette l'host in scena e ci pubblica le notifiche, nell'ordine. Senza vetro il gesto si prova
   * da solo; con [glass] la card e' quella vera, con il suo materiale e il tasto che lo rifrange.
   */
  private fun show(vararg notifications: FluidNotification, glass: Boolean = false) {
    compose.setContent {
      Themed {
        val host = rememberFluidNotificationHostState()
        state = host
        scope = rememberCoroutineScope()
        val backdrop = if (glass) rememberGlassBackdrop() else null
        Box(Modifier.fillMaxSize()) {
          FluidNotificationHost(
            state = host,
            modifier = Modifier.align(Alignment.TopCenter),
            backdrop = backdrop,
          )
        }
      }
    }
    compose.runOnIdle { notifications.forEach { scope.launch { state.show(it) } } }
    compose.waitForIdle()
  }

  private fun notification(durationMillis: Long = 600_000L) = FluidNotification(
    id = "demo",
    title = Title,
    message = "Sono arrivati quattordici nuovi voti e due comunicazioni della segreteria. " +
      "Chiusa la notifica ne mostra due righe, aperta il testo intero, con le sue azioni, " +
      "e questa e' abbastanza lunga da non starci in due righe su uno schermo di telefono.",
    durationMillis = durationMillis,
    actions = listOf(FluidNotificationAction("Apri") { actionClicks++ }),
  )

  /** La card e' in scena: il titolo e' nell'albero semantico (una card sparita non c'e' piu'). */
  private fun isShown(): Boolean =
    compose.onAllNodesWithText(Title).fetchSemanticsNodes().isNotEmpty()

  private fun waitUntilGone() {
    compose.waitUntil(timeoutMillis = 5_000L) { !isShown() }
  }

  @Test
  fun `trascinare in su oltre la soglia lancia via la notifica`() {
    show(notification())
    compose.onNodeWithText(Title).assertIsDisplayed()

    // 70 dp di corsa a 437 dp/s: chiude per distanza, non per spinta.
    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(10) { moveBy(Offset(0f, -14f)) }
      up()
    }
    waitUntilGone()

    assertTrue("la notifica e' ancora in scena", !isShown())
    assertTrue("il colpo della soglia non e' arrivato: ${haptics.played}", FluidHapticEvent.Threshold in haptics.played)
  }

  @Test
  fun `lanciata via, lascia il posto alla notifica dopo`() {
    show(notification(), notification().copy(id = "seconda", title = "Seconda"))
    compose.onNodeWithText(Title).assertIsDisplayed()

    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(10) { moveBy(Offset(0f, -14f)) }
      up()
    }
    compose.waitUntil(timeoutMillis = 5_000L) {
      compose.onAllNodesWithText("Seconda").fetchSemanticsNodes().isNotEmpty()
    }

    compose.onNodeWithText("Seconda").assertIsDisplayed()
    assertTrue("la prima non e' uscita di scena", !isShown())
  }

  @Test
  fun `con il vetro la card si apre e si lancia via come senza`() {
    show(notification(), glass = true)
    compose.onNodeWithText(Title).assertIsDisplayed()

    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(14) { moveBy(Offset(0f, 14f)) }
      up()
    }
    compose.waitForIdle()
    compose.onNodeWithText("Apri").assertIsDisplayed()

    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(10) { moveBy(Offset(0f, -14f)) }
      up()
    }
    waitUntilGone()
    assertTrue(!isShown())
  }

  @Test
  fun `un trascinamento corto torna al suo posto e la notifica resta`() {
    show(notification())
    val before = compose.onNodeWithText(Title).getUnclippedBoundsInRoot()

    // 36 px, di cui 16 di slop: 10 dp di corsa vera, ben sotto i 40.
    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(4) { moveBy(Offset(0f, -9f)) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la notifica e' sparita per 10 dp di trascinamento", isShown())
    val after = compose.onNodeWithText(Title).getUnclippedBoundsInRoot()
    assertEquals("non e' tornata al suo posto", before.top.value, after.top.value, 0.6f)
  }

  @Test
  fun `tirare in giu' apre la card con le azioni e non scade piu'`() {
    show(notification(durationMillis = 2_000L))
    // Chiusa non ci sono azioni.
    compose.onNodeWithText("Apri").assertDoesNotExist()

    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(14) { moveBy(Offset(0f, 14f)) }
      up()
    }
    compose.waitForIdle()

    compose.onNodeWithText("Apri").assertIsDisplayed()
    assertTrue("manca il tick sulla soglia: ${haptics.played}", FluidHapticEvent.Tick in haptics.played)

    // Il tempo di lettura e' di due secondi: ben oltre, la card aperta c'e' ancora.
    compose.mainClock.advanceTimeBy(30_000L)
    compose.waitForIdle()
    assertTrue("una card aperta e' scaduta", isShown())
  }

  @Test
  fun `un tocco sul tasto chiudi fa quello che ha sempre fatto`() {
    show(notification())

    compose.onNodeWithContentDescription("Chiudi notifica").performClick()
    waitUntilGone()

    assertTrue("la notifica e' ancora in scena", !isShown())
    assertTrue("un tap non e' un lancio", FluidHapticEvent.Threshold !in haptics.played)
  }

  @Test
  fun `un trascinamento che parte dal tasto non e' anche un tocco`() {
    show(notification())

    compose.onNodeWithContentDescription("Chiudi notifica").performTouchInput {
      down(center)
      // Piccolo: oltre lo slop, sotto la soglia di lancio. Se contasse come tocco, chiuderebbe.
      repeat(4) { moveBy(Offset(0f, 9f)) }
      up()
    }
    compose.waitForIdle()

    assertTrue("il trascinamento ha fatto anche da tocco sul tasto", isShown())
  }

  @Test
  fun `l'azione di una card aperta la esegue e chiude la notifica`() {
    show(notification())
    compose.onNodeWithText(Title).performTouchInput {
      down(center)
      repeat(14) { moveBy(Offset(0f, 14f)) }
      up()
    }
    compose.waitForIdle()

    compose.onNodeWithText("Apri").performClick()
    waitUntilGone()

    assertEquals(1, actionClicks)
    assertTrue("la notifica e' ancora in scena", !isShown())
  }

  @Test
  fun `le azioni di accessibilita' aprono e chiudono senza trascinare`() {
    show(notification())

    compose.onNodeWithText("Apri").assertDoesNotExist()
    val card = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Notifica in-app"))
    fun actions() = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
    assertEquals(listOf("Espandi", "Chiudi"), actions().map { it.label })

    compose.runOnUiThread { actions().first { it.label == "Espandi" }.action() }
    compose.waitForIdle()
    compose.onNodeWithText("Apri").assertIsDisplayed()
    assertEquals("da aperta resta solo Chiudi", listOf("Chiudi"), actions().map { it.label })

    compose.runOnUiThread { actions().first { it.label == "Chiudi" }.action() }
    waitUntilGone()
    assertTrue("la notifica e' ancora in scena", !isShown())
  }

  @Test
  fun `un dito sulla card ferma il conto alla rovescia e al rilascio riparte`() {
    show(notification(durationMillis = 2_000L))
    compose.mainClock.autoAdvance = false

    compose.onNodeWithText(Title).performTouchInput { down(center) }
    // Dieci secondi con il dito fermo sopra: cinque volte il tempo di lettura.
    compose.mainClock.advanceTimeBy(10_000L)
    assertTrue("la card e' sparita sotto il dito", isShown())

    compose.onNodeWithText(Title).performTouchInput { up() }
    compose.mainClock.advanceTimeBy(1_000L)
    assertTrue("la card e' sparita un secondo dopo il rilascio", isShown())

    compose.mainClock.advanceTimeBy(2_000L)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertTrue("la card non e' scaduta dopo il rilascio", !isShown())
  }
}
