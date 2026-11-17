package dev.antigravity.fluidengine.sample.plane

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
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
  fun `un blocco di tasti con l'unione e' una sagoma sola a riposo`() {
    open(PlaneScene.Union)
    // A 8 dp sono già un grappolo (una cattura), ma tre vetri distinti: nessuna unione, separati del tutto a riposo.
    assertTrue(rest.toString(), rest.size == 3 && rest.all { it.clusterSize == 3 && it.unionId == null })
    command()
    advance(800)
    assertTrue("unite: $rest", rest.size == 3 && rest.all { it.clusterSize == 3 && it.unionId == "sagoma" })
    noOrderWarnings()
  }

  @Test
  fun `quattro e otto vetri volano insieme, gli otto uniti in un campo solo`() {
    open(PlaneScene.Many)
    command()
    assertEquals("un viaggio per contenitore", 2L, FluidGlassContainerDiagnostics.totalJourneys)
    advance(800)
    // `lastRest` è l'ultimo riposo pubblicato, di uno dei due contenitori: quattro vetri distinti in un grappolo, o gli
    // otto uniti in un grappolo solo.
    assertTrue(
      rest.toString(),
      rest.size == 4 && rest.all { it.unionId == null && it.clusterSize == 4 } ||
        rest.size == 8 && rest.all { it.unionId == "otto" && it.clusterSize == 8 },
    )
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

  // ----------------------------------------------------------------------------------------------------------
  // Le scene del giro di correzione di F3 (review F3, A 13, D R6, C R10): si compongono, hanno gli `id` dichiarati e nessun
  // avviso d'ordine. Come si vedono lo dicono solo le riprese: `artifacts/liquid-glass/sessioni/f3-corr-scene/riprese.md`.
  // ----------------------------------------------------------------------------------------------------------

  private fun born(): Map<Any?, Any?> =
    FluidGlassContainerDiagnostics.lastJourney.filter { it.change == "BornFromNeighbour" }.associate { it.toId to it.bornFromId }

  private fun merged(): Map<Any?, Any?> =
    FluidGlassContainerDiagnostics.lastJourney.filter { it.change == "MergeIntoNeighbour" }.associate { it.fromId to it.mergesIntoId }

  @Test
  fun `un tasto ne fa nascere due, dalla stessa parte, e tornando confluiscono nel donatore`() {
    open(PlaneScene.TwoBorn)
    assertEquals(listOf("seleziona"), rest.map { it.id })
    command()
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(journey(), mapOf("condividi" to "seleziona", "elimina" to "seleziona"), born())
    advance(1_200)
    assertEquals(setOf("condividi", "elimina", "seleziona"), rest.map { it.id }.toSet())
    command()
    assertEquals(journey(), mapOf("condividi" to "seleziona", "elimina" to "seleziona"), merged())
    advance(1_200)
    assertEquals(listOf("seleziona"), rest.map { it.id })
    noOrderWarnings()
  }

  @Test
  fun `accanto al bordo del contenitore un tasto nasce verso il bordo e uno verso l'interno`() {
    open(PlaneScene.Edge)
    assertEquals(setOf("indietro", "modifica", "elenco", "condividi"), rest.map { it.id }.toSet())
    command()
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(journey(), mapOf("preferiti" to "modifica", "cerca" to "condividi"), born())
    advance(1_200)
    assertEquals(
      setOf("indietro", "modifica", "preferiti", "elenco", "cerca", "condividi"),
      rest.map { it.id }.toSet(),
    )
    // Le due righe hanno la stessa larghezza: l'ultimo tasto di ognuna sta sul bordo del contenitore.
    fun right(id: String) = rest.single { it.id == id }.bounds.right
    assertEquals(rest.toString(), right("preferiti"), right("condividi"), 1f)
    command()
    assertEquals(journey(), mapOf("preferiti" to "modifica", "cerca" to "condividi"), merged())
    advance(1_200)
    assertEquals(setOf("indietro", "modifica", "elenco", "condividi"), rest.map { it.id }.toSet())
    noOrderWarnings()
  }

  @Test
  fun `tre tasti a 8 dp non uniti, il terzo ne fa nascere un quarto e i due fermi non viaggiano`() {
    open(PlaneScene.ThreeNear)
    // Tre vetri distinti in un grappolo (8 dp fra i vetri), nessuna unione.
    assertTrue(rest.toString(), rest.size == 3 && rest.all { it.clusterSize == 3 && it.unionId == null })
    command()
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(journey(), mapOf("elimina" to "modifica"), born())
    // I due che non cambiano non nascono, non confluiscono e non compaiono: restano pezzi che ci sono.
    val fixed = FluidGlassContainerDiagnostics.lastJourney.filter { it.fromId == "cerca" || it.fromId == "condividi" }
    assertTrue(journey(), fixed.none { it.change != "Matched" })
    advance(1_200)
    assertTrue("quattro vetri distinti: $rest", rest.size == 4 && rest.all { it.clusterSize == 4 && it.unionId == null })
    noOrderWarnings()
  }

  @Test
  fun `un viaggio sopra una lista che scorre, la lista scende da sola e risale, un viaggio per cambio`() {
    open(PlaneScene.Scrolling)
    compose.onNodeWithText("Voce 1").assertExists()
    command()
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(journey(), mapOf("fatto" to "modifica"), born())
    advance(2_000)
    assertEquals(setOf("modifica", "fatto"), rest.map { it.id }.toSet())
    compose.onNodeWithText("Voce 1").assertDoesNotExist()
    command()
    advance(2_000)
    assertEquals(listOf("modifica"), rest.map { it.id })
    compose.onNodeWithText("Voce 1").assertExists()
    assertEquals(2L, FluidGlassContainerDiagnostics.totalJourneys)
    noOrderWarnings()
  }

  @Test
  fun `la scritta che cambia lunghezza allarga il pezzo e non muove il tasto fermo`() {
    open(PlaneScene.Label)
    assertEquals(setOf("indietro", "salva"), rest.map { it.id }.toSet())
    val short = rest.single { it.id == "salva" }.bounds
    val still = rest.single { it.id == "indietro" }.bounds
    command()
    assertTrue(journey(), FluidGlassContainerDiagnostics.lastJourney.any { it.toId == "salva" && it.change == "Matched" })
    advance(1_200)
    val long = rest.single { it.id == "salva" }.bounds
    assertTrue("la capsula si allarga per la scritta: ${short.width} -> ${long.width}", long.width > short.width + 40f)
    assertEquals("il tasto fermo sta dov'era", still.left, rest.single { it.id == "indietro" }.bounds.left, 0.5f)
    noOrderWarnings()
  }

  @Test
  fun `due contenitori nella stessa pagina cambiano insieme, un viaggio e una sonda ciascuno`() {
    open(PlaneScene.Several)
    assertEquals(2, FluidGlassContainerDiagnostics.liveContainers)
    command()
    assertEquals("un viaggio per contenitore", 2L, FluidGlassContainerDiagnostics.totalJourneys)
    advance(1_200)
    // `lastRest` è quello dell'ultimo contenitore che ha pubblicato: la barra in alto o la capsula sotto.
    assertTrue(
      rest.toString(),
      rest.map { it.id }.toSet() == setOf("modifica", "fatto") || rest.map { it.id }.toSet() == setOf("barra", "fatto"),
    )
    assertEquals("otto tasti dei comandi più una sonda per contenitore", ControlButtons + 2, FluidGlassProbeDiagnostics.liveProbes)
    noOrderWarnings()
  }

  @Test
  fun `il contenitore che cambia misura porta i pezzi con se, agli stessi id`() {
    open(PlaneScene.Resize)
    assertEquals(setOf("indietro", "cerca", "condividi"), rest.map { it.id }.toSet())
    val before = compose.onNodeWithTag("plane-resize-end").getBoundsInRoot().right
    command()
    advance(1_200)
    assertEquals(setOf("indietro", "cerca", "condividi"), rest.map { it.id }.toSet())
    val after = compose.onNodeWithTag("plane-resize-end").getBoundsInRoot().right
    // Da 200 a 340 dp: l'ultimo tasto sta sempre al capo della riga, che si è allargata di 140 dp.
    assertEquals("l'ultimo tasto segue il capo della riga", 140f, (after - before).value, 1f)
    // La regola del giro di correzione (D R5): cambia solo il riquadro del contenitore, i pezzi lo seguono senza viaggio.
    assertEquals("nessun viaggio per un contenitore che cambia misura", 0L, FluidGlassContainerDiagnostics.totalJourneys)
    command()
    advance(1_200)
    assertEquals(before.value, compose.onNodeWithTag("plane-resize-end").getBoundsInRoot().right.value, 1f)
    assertEquals("nessun viaggio nemmeno al ritorno", 0L, FluidGlassContainerDiagnostics.totalJourneys)
    noOrderWarnings()
  }

  @Test
  fun `ferma, la pagina non viaggia da sola`() {
    open(PlaneScene.OneTwo)
    advance(3_000)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalRetargets)
  }
}
