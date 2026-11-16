package dev.antigravity.fluidengine.sample.plane

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPieceRecord
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
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
 * La pagina «Piano flottante» (F3.6) in un albero vero: ogni scena si apre, il suo comando cambia solo lo stato, e il
 * contenitore riporta i viaggi che la scena dichiara (chi nasce da chi, chi compare, chi si fonde, chi riparte a metà).
 * In nessuna scena l'ordine dei modificatori è sbagliato.
 *
 * Non prova come si vede: Robolectric non disegna, e il collo, la comparsa, il passaggio di consegne e il giro uniforme
 * si guardano sul tablet (`artifacts/liquid-glass/sessioni/f3-6/riprese-S3.md`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class PlanePageTest {

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

  private fun open(scene: PlaneScene, spacing: Dp = PlaneSpacing.Medium, interruptMs: Long = 0L): PlaneLive {
    val live = PlaneLive(interruptMs = interruptMs)
    compose.setContent { PlaneContent(PlaneConfig(scene = scene, spacing = spacing), live, bottomInset = 0.dp, onBack = {}) }
    compose.waitForIdle()
    advance(400)
    return live
  }

  /** Il comando della scena, dal suo tasto: la stessa strada di un dito. */
  private fun command() {
    compose.onNodeWithTag("plane-command").performClick()
    advance(64)
  }

  private val rest: List<FluidGlassPieceRecord> get() = FluidGlassContainerDiagnostics.lastRest

  private fun journey(): String = FluidGlassContainerDiagnostics.lastJourney.toString()

  /** I tasti di vetro dei comandi in cima alla pagina: Indietro, Scena, Tema, Spacing, Vetro, Automatico, Interrompi, il comando. */
  private val ControlButtons = 8

  private fun noOrderWarnings() =
    assertEquals("la pagina scrive gli attributi prima del vetro", 0L, FluidGlassContainerDiagnostics.totalOrderWarnings)

  @Test
  fun `un tasto diventa due, nasce dal vicino e vi torna`() {
    open(PlaneScene.OneTwo)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    command()
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.any {
      it.toId == "fatto" && it.change == "BornFromNeighbour" && it.bornFromId == "modifica"
    })
    advance(800)
    assertEquals(setOf("modifica", "fatto"), rest.map { it.id }.toSet())
    command()
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.any {
      it.fromId == "fatto" && it.change == "MergeIntoNeighbour" && it.mergesIntoId == "modifica"
    })
    advance(800)
    assertEquals(listOf("modifica"), rest.map { it.id })
    noOrderWarnings()
  }

  @Test
  fun `due capsule si fondono in un grappolo e si dividono`() {
    open(PlaneScene.Capsules)
    assertTrue(rest.toString(), rest.size == 2 && rest.all { it.clusterSize == 1 })
    command()
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.count { it.change == "Matched" } == 2)
    advance(800)
    assertTrue("accostate sono una sagoma: $rest", rest.size == 2 && rest.all { it.clusterSize == 2 })
    command()
    advance(800)
    assertTrue("divise tornano due: $rest", rest.all { it.clusterSize == 1 })
    assertEquals(2L, FluidGlassContainerDiagnostics.totalJourneys)
    noOrderWarnings()
  }

  @Test
  fun `il tasto lontano compare per lente e scompare`() {
    open(PlaneScene.Appear)
    command()
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.any { it.toId == "play" && it.change == "Materialize" })
    advance(800)
    assertEquals(setOf("cerca", "preferiti", "play"), rest.map { it.id }.toSet())
    command()
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.any { it.fromId == "play" && it.change == "Dematerialize" })
    advance(800)
    noOrderWarnings()
  }

  @Test
  fun `tre viste lontane diventano un grappolo solo con l'unione`() {
    open(PlaneScene.Union)
    assertTrue(rest.toString(), rest.size == 3 && rest.all { it.clusterSize == 1 })
    command()
    advance(800)
    assertTrue("unite: $rest", rest.size == 3 && rest.all { it.clusterSize == 3 })
    noOrderWarnings()
  }

  @Test
  fun `la barra cambia contesto e Fatto nasce dalla barra`() {
    open(PlaneScene.Context)
    command()
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.any {
      it.toId == "fatto" && it.change == "BornFromNeighbour" && it.bornFromId == "barra"
    })
    // Il contenuto cambia nei tre posti senza eccezioni, anche a metà.
    advance(120)
    command()
    advance(1_000)
    assertEquals(listOf("barra"), rest.map { it.id })
    compose.onNodeWithTag("plane-slot-0").assertExists()
    noOrderWarnings()
  }

  @Test
  fun `il fondo che cambia non muove il vetro, e i tre vetri hanno una sonda sola`() {
    open(PlaneScene.Uniform)
    assertTrue(rest.toString(), rest.size == 3)
    command()
    advance(800)
    assertEquals("cambia il fondo, non il piano", 0L, FluidGlassContainerDiagnostics.totalJourneys)
    // I tasti dei comandi sono vetri a sé (fuori da ogni contenitore) e sondano ognuno per sé: otto sonde. I tre vetri
    // della scena ne aggiungono una sola, quella del contenitore; se sondassero da soli sarebbero undici.
    assertEquals("otto tasti dei comandi più una sonda per il contenitore", ControlButtons + 1, FluidGlassProbeDiagnostics.liveProbes)
    noOrderWarnings()
  }

  @Test
  fun `Clear e Regular nel contenitore sono due grappoli`() {
    open(PlaneScene.Clear)
    assertEquals(setOf("regular", "clear"), rest.map { it.id }.toSet())
    assertTrue(rest.toString(), rest.all { it.clusterSize == 1 })
    command()
    advance(400)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    noOrderWarnings()
  }

  @Test
  fun `i raggi concentrici seguono i margini, e il cambio di margine viaggia`() {
    open(PlaneScene.Concentric)
    // xhdpi: il pannello ha raggio 38 dp. «a» sta a 8 dp (38 − 8 = 30, oltre mezzo lato: 22 dp = 44 px), «c» a 24 dp
    // dal basso e dai lati (14 dp = 28 px), e gli angoli lontani dai bordi stanno al minimo (4 dp = 8 px).
    fun radiiOf(id: String) = rest.single { it.id == id }.cornerRadii
    assertEquals(rest.toString(), 44f, radiiOf("a").topLeft, 1f)
    assertEquals(rest.toString(), 8f, radiiOf("a").bottomRight, 1f)
    assertEquals(rest.toString(), 28f, radiiOf("c").bottomLeft, 1f)
    command()
    advance(800)
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    // Dieci dp in più: «a» 38 − 18 = 20 dp, «b» 38 − 26 = 12 dp, «c» 38 − 34 = 4 dp (il minimo).
    assertEquals(rest.toString(), 40f, radiiOf("a").topLeft, 1f)
    assertEquals(rest.toString(), 24f, radiiOf("b").topRight, 1f)
    assertEquals(rest.toString(), 8f, radiiOf("c").bottomLeft, 1f)
    noOrderWarnings()
  }

  @Test
  fun `il comando che interrompe cambia lo stato due volte a meta' viaggio, e il viaggio riparte`() {
    val live = open(PlaneScene.Interrupt, interruptMs = PlaneLaunch.DefaultInterruptMs)
    compose.onNodeWithTag("plane-command").performClick()
    advance(1_200)
    assertEquals("tre cambi", 3, live.step)
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals("due ripartenze, non due viaggi in coda", 2L, FluidGlassContainerDiagnostics.totalRetargets)
    assertEquals(setOf("modifica", "fatto"), rest.map { it.id }.toSet())
    noOrderWarnings()
  }

  @Test
  fun `il tasto Scena passa alla scena dopo, e la scena nuova riparte dallo stato iniziale`() {
    val live = open(PlaneScene.OneTwo)
    command()
    assertEquals(1, live.step)
    compose.onNodeWithTag("plane-scene").performClick()
    advance(400)
    assertEquals(0, live.step)
    compose.onNodeWithText("F3.6 · capsule", substring = true).assertExists()
    assertTrue(rest.toString(), rest.map { it.id }.toSet() == setOf("sinistra", "destra"))
  }

  @Test
  fun `ferma, la pagina non viaggia da sola`() {
    open(PlaneScene.OneTwo)
    advance(3_000)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalRetargets)
  }
}
