package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCelebration
import dev.antigravity.fluidengine.ui.fluid.FluidCelebrationState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSymbol
import dev.antigravity.fluidengine.ui.fluid.FluidSymbolEffect
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.Wifi
import dev.antigravity.fluidengine.ui.fluid.fluidSymbolEffect
import dev.antigravity.fluidengine.ui.fluid.fluidSymbolEffectWhile
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCelebrationState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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
 * F8.5 · la festa e gli effetti dei simboli in un albero Compose vero: la festa parte, finisce ed esce dalla composizione,
 * e dopo la finestra non ridisegna piu'; con «Riduci movimento» un segno fermo (un disegno solo) e poi via; ogni effetto
 * dei simboli ha la sua durata e torna a riposo, dopo nessun ridisegno; un effetto attivo spento finisce il ciclo. Si
 * contano i cambi di stato applicati (`Snapshot.registerApplyObserver`), di chiunque: ogni fotogramma di un'animazione
 * scrive uno stato (l'orologio dell'icona, il passo della festa); zero cambi vuol dire niente da ricomporre, da
 * ripiazzare o da ridisegnare. In Robolectric la finestra non disegna davvero, e un contatore dei disegni resta a zero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class F8p5FestaSimboliUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var changes = 0
  private var observer: ObserverHandle? = null

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    observer = Snapshot.registerApplyObserver { changed, _ -> if (changed.isNotEmpty()) changes++ }
  }

  @After fun stop() {
    observer?.dispose()
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      // Cio' che fa il gestore globale degli snapshot dopo ogni giro del main: le scritture del fotogramma arrivano a chi
      // le osserva (il disegno, il livello, il Recomposer, e il contatore di questa prova).
      Snapshot.sendApplyNotifications()
    }
  }

  /**
   * Un cambio fatto dal test, notificato subito: senza `sendApplyNotifications` il Recomposer lo vede solo quando il
   * gestore globale degli snapshot gira, e dopo altre classi di prova nello stesso processo non e' detto che giri.
   */
  private fun change(block: () -> Unit) {
    compose.runOnIdle {
      block()
      Snapshot.sendApplyNotifications()
    }
  }

  /** I cambi di stato applicati durante [ms] millisecondi: zero, niente da ridisegnare. */
  private fun drawsDuring(ms: Int): Int {
    val before = changes
    advance(ms)
    return changes - before
  }

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current
        CompositionLocalProvider(LocalFluidMotionPolicy provides policy) {
          Box(Modifier.fillMaxSize()) { content() }
        }
      }
    }
    advance(600)
  }

  // --- La festa ----------------------------------------------------------------------------------------------

  @Test fun `la festa parte, finisce, esce dalla composizione e dopo nessuno stato cambia piu'`() {
    lateinit var state: FluidCelebrationState
    show {
      state = rememberFluidCelebrationState()
      FluidCelebration(state, Modifier.testTag("festa"))
    }
    assertFalse(state.isCelebrating)
    compose.onNodeWithTag("festa").assertDoesNotExist()
    assertEquals("ferma, la pagina non ridisegna", 0, drawsDuring(300))
    change { state.launch() }
    advance(100)
    assertTrue(state.isCelebrating)
    compose.onNodeWithTag("festa").assertExists()
    val during = drawsDuring(1_000)
    assertTrue("la festa si disegna a ogni fotogramma: $during", during >= 50)
    // La pioggia dura meno della vita dei pezzi (4,2 s).
    advance(4_000)
    assertFalse("la festa e' finita", state.isCelebrating)
    compose.onNodeWithTag("festa").assertDoesNotExist()
    advance(100)
    assertEquals("finita la festa, zero ridisegni", 0, drawsDuring(1_000))
  }

  @Test fun `lo scoppio dal tasto parte e finisce, e una festa nuova riparte da capo`() {
    lateinit var state: FluidCelebrationState
    show {
      state = rememberFluidCelebrationState()
      FluidCelebration(state)
    }
    change { state.launch(androidx.compose.ui.geometry.Offset(400f, 1500f)) }
    advance(1_500)
    assertTrue(state.isCelebrating)
    // Una seconda festa mentre la prima e' in corso riparte da capo: dura ancora quasi tutta.
    change { state.launch(androidx.compose.ui.geometry.Offset(400f, 1500f)) }
    // La prima, da sola, finirebbe a ~2,5 s: a 3 s dall'inizio c'e' ancora la seconda.
    advance(1_500)
    assertTrue("la seconda e' ancora in corso", state.isCelebrating)
    advance(4_000)
    assertFalse(state.isCelebrating)
    assertEquals(0, drawsDuring(500))
  }

  @Test fun `con riduci movimento niente pioggia, un segno fermo disegnato una volta, poi via`() {
    lateinit var state: FluidCelebrationState
    show(reduced = true) {
      state = rememberFluidCelebrationState()
      FluidCelebration(state, Modifier.testTag("festa"))
    }
    change { state.launch() }
    advance(150)
    assertTrue(state.isCelebrating)
    compose.onNodeWithTag("festa").assertExists()
    // Fermo: nessun fotogramma di movimento mentre il segno resta.
    assertEquals("il segno e' fermo", 0, drawsDuring(500))
    // Poi svanisce (0,25 s di dissolvenza, solo opacita') ed esce.
    advance(900)
    assertFalse(state.isCelebrating)
    compose.onNodeWithTag("festa").assertDoesNotExist()
    assertEquals(0, drawsDuring(500))
  }

  // --- Gli effetti dei simboli --------------------------------------------------------------------------------

  /** La durata di Apple di ogni effetto a colpo singolo, in ms (vedi `FluidSymbolProfile`). */
  private val discrete = listOf(
    FluidSymbolEffect.Bounce to 601, FluidSymbolEffect.Pulse to 2000, FluidSymbolEffect.Wiggle to 901,
    FluidSymbolEffect.wiggleRotation(pivotY = 0.12f) to 901, FluidSymbolEffect.Rotate to 1902,
    FluidSymbolEffect.Breathe to 3000, FluidSymbolEffect.VariableColor to 1625,
    FluidSymbolEffect.VariableColorCumulative to 2626,
  )

  @Test fun `ogni effetto del simbolo suona al cambio, dura quanto Apple e torna a riposo senza ridisegni`() {
    var trigger by mutableIntStateOf(0)
    var effect by mutableStateOf(FluidSymbolEffect.Bounce)
    show {
      FluidSymbol(FluidIcons.Wifi, "Wi-Fi", effect = effect, trigger = trigger)
    }
    compose.onNode(hasContentDescription("Wi-Fi")).assertIsDisplayed()
    assertEquals("la prima composizione non suona", 0, drawsDuring(300))
    for ((e, ms) in discrete) {
      change { effect = e; trigger++ }
      advance(32)
      val moving = drawsDuring(ms / 2)
      assertTrue("$e si muove: $moving ridisegni", moving > 3)
      advance(ms / 2 + 120)
      assertEquals("$e finito: a riposo, zero ridisegni", 0, drawsDuring(400))
    }
  }

  @Test fun `il modificatore sul simbolo intero suona e torna a riposo`() {
    var trigger by mutableIntStateOf(0)
    show {
      androidx.compose.material3.Icon(FluidIcons.Star, "Preferito", Modifier.fluidSymbolEffect(FluidSymbolEffect.Bounce, trigger))
    }
    assertEquals(0, drawsDuring(300))
    change { trigger++ }
    advance(32)
    assertTrue(drawsDuring(300) > 3)
    advance(400)
    assertEquals(0, drawsDuring(400))
  }

  @Test fun `un effetto attivo va finche' lo e', poi finisce il ciclo e si ferma`() {
    var active by mutableStateOf(false)
    show {
      androidx.compose.material3.Icon(FluidIcons.Star, null, Modifier.fluidSymbolEffectWhile(FluidSymbolEffect.Pulse, active))
    }
    assertEquals(0, drawsDuring(300))
    change { active = true }
    advance(32)
    assertTrue(drawsDuring(3_000) > 100)
    change { active = false }
    // Spento a ~3 s: finisce il secondo ciclo, a 4 s dall'inizio.
    advance(1_200)
    assertEquals(0, drawsDuring(500))
  }

  @Test fun `la sostituzione sfoca la vecchia e la nuova, e Draw On e Off finiscono a riposo`() {
    var playing by mutableStateOf(false)
    var drawn by mutableStateOf(true)
    show {
      FluidSymbol(if (playing) FluidIcons.Pause else FluidIcons.PlayArrow, if (playing) "Pausa" else "Riproduci")
      FluidSymbol(FluidIcons.Check, "Fatto", drawn = drawn)
    }
    compose.onNode(hasContentDescription("Riproduci")).assertExists()
    change { playing = true }
    advance(32)
    assertTrue(drawsDuring(200) > 3)
    advance(300)
    compose.onNode(hasContentDescription("Pausa")).assertExists()
    assertEquals("sostituzione finita", 0, drawsDuring(400))
    change { drawn = false }
    advance(32)
    assertTrue(drawsDuring(400) > 3)
    advance(500)
    assertEquals("Draw Off finito", 0, drawsDuring(400))
    change { drawn = true }
    advance(32)
    assertTrue(drawsDuring(400) > 3)
    advance(500)
    assertEquals("Draw On finito", 0, drawsDuring(400))
  }

  @Test fun `con riduci movimento il rimbalzo non muove niente e il respiro solo schiarisce`() {
    var trigger by mutableIntStateOf(0)
    var effect by mutableStateOf(FluidSymbolEffect.Bounce)
    show(reduced = true) {
      FluidSymbol(FluidIcons.Star, "Preferito", effect = effect, trigger = trigger)
    }
    change { trigger++ }
    advance(700)
    assertEquals("riposo", 0, drawsDuring(300))
    change { effect = FluidSymbolEffect.Breathe; trigger++ }
    advance(32)
    assertTrue("il respiro schiarisce anche col movimento ridotto", drawsDuring(1_000) > 3)
    advance(2_200)
    assertEquals(0, drawsDuring(300))
  }

  // --- Le scene --------------------------------------------------------------------------------------------------

  @Test fun `la scena del voto festeggia e torna ferma`() {
    show { NewElementsPageAt(onBack = {}, initial = "festa-voto") }
    compose.onNodeWithTag(F8p5Tags.Celebrate).performScrollTo().performClick()
    advance(200)
    compose.onNode(hasText("Festa in corso")).assertExists()
    advance(5_000)
    compose.onNode(hasText("Ferma: nessun disegno")).assertExists()
  }

  @Test fun `la scena dell'installazione fa partire lo scoppio dal tasto`() {
    show { NewElementsPageAt(onBack = {}, initial = "festa-installa") }
    compose.onNodeWithTag(F8p5Tags.Install).performScrollTo().performClick()
    advance(1_400)
    compose.onNode(hasText("Festa in corso")).assertExists()
    compose.onNode(hasText("Apri")).assertExists()
    advance(5_000)
    compose.onNode(hasText("Ferma: nessun disegno")).assertExists()
  }

  @Test fun `le scene dei simboli si aprono, con le caselle e le icone nominate`() {
    show { NewElementsPageAt(onBack = {}, initial = "simboli-tavola") }
    for (id in listOf("rimbalzo", "variabile", "disegno")) compose.onNodeWithTag(F8p5Tags.cell(id)).assertExists()
    advance(3_000)
    compose.onNode(hasContentDescription("Colore variabile")).assertExists()
  }

  @Test fun `le scene di F8 5 sono nella pagina degli elementi nuovi`() {
    for (id in listOf("festa-voto", "festa-installa", "simboli-tavola", "simboli-app")) {
      assertTrue(id, newElementScene(id) != null)
    }
    assertTrue(NewElementSections.any { it.pack == "F8.5" && it.scenes.size == 4 })
  }
}
