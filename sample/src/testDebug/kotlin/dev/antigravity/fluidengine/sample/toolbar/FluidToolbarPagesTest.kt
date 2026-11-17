package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPieceRecord
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarIds
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageBarDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
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
 * La barra dichiarata **fra pagine** nell'albero vero (piano «iOS in Android», F4A.3): l'host della prova «Barre fra
 * pagine» con le sue pagine vere, e il registro del piano flottante che porta i vetri della barra dell'host.
 *
 * Prova che fra due pagine con gli stessi `id` i vetri sono **gli stessi pezzi** del piano (nessuna morte e nascita, e
 * due tasti che cambiano solo icona non aprono nemmeno un viaggio), che un tasto nuovo nasce dal fianco del vicino e
 * l'indietro della figlia compare per lente, che il viaggio **parte al push** mentre la pagina è ancora in corsa, che
 * il contenuto dei tasti che restano resta nitido, che il gesto indietro **non cambia i tasti** finché il dito tiene la
 * pagina, che all'annullo non succede niente e che alla conferma la barra passa alla pagina sotto; e che due pagine con
 * le `actions` di sempre non hanno il contenitore del modello. Non prova come si vede: i viaggi si guardano sul tablet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidToolbarPagesTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private lateinit var host: FluidPageHostState

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidPageBarDiagnostics.gestureFollowsFinger = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidPageBarDiagnostics.gestureFollowsFinger = false
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

  private fun pages(start: ToolbarHostPage) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val state = rememberFluidPageHostState()
        host = state
        ToolbarPagesContent(ToolbarPagesLaunch.Request(start, 0L, false), bottomInset = 0.dp, onBack = {}, hostState = state)
      }
    }
    compose.waitForIdle()
    advance(1_200)
  }

  private fun tapTag(tag: String) {
    compose.onNodeWithTag(tag).performClick()
    Snapshot.sendApplyNotifications()
  }

  private val rest: List<FluidGlassPieceRecord> get() = FluidGlassContainerDiagnostics.lastRest

  /** I membri del gruppo alla nascita del vetro, dal nome che il registro del piano espone (`FluidToolbarGlassId(a,b#0)`). */
  private fun membersOf(id: Any?): Set<String> =
    id.toString().substringAfter("(").substringBeforeLast("#").split(",").toSet()

  private fun glassOf(member: Any): FluidGlassPieceRecord = rest.single { member.toString() in membersOf(it.id) }

  private fun present(label: String): Boolean =
    compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun `due tasti che cambiano solo icona fra due pagine sono gli stessi vetri, fermi, senza viaggio`() {
    pages(ToolbarHostPage.Note)
    val back = glassOf(FluidToolbarIds.Back)
    val lead = glassOf("lead")
    val trail = glassOf("trail")
    assertTrue(present("Condividi") && present("Opzioni"))
    val journeys = FluidGlassContainerDiagnostics.totalJourneys

    tapTag("toolbar-pages-open")
    advance(64)
    assertTrue("la pagina corre", host.isMoving)
    // I tasti sono già quelli della pagina d'arrivo (la cima della pila richiesta), dal primo fotogramma del push.
    assertTrue(present("Modifica") && present("Preferito"))
    assertFalse(present("Condividi") || present("Opzioni"))
    advance(1_500)
    assertFalse(host.isMoving)

    assertEquals("nessun vetro si è mosso: niente viaggio", journeys, FluidGlassContainerDiagnostics.totalJourneys)
    for ((before, member) in listOf(back to FluidToolbarIds.Back, lead to "lead", trail to "trail")) {
      val after = glassOf(member)
      assertEquals("$member: lo stesso pezzo del piano", before.id, after.id)
      assertEquals("$member: fermo al pixel", before.bounds, after.bounds)
    }
  }

  @Test
  fun `un tasto diventa due al push, il viaggio parte col push e chi resta resta nitido`() {
    pages(ToolbarHostPage.Notes)
    val trail = glassOf("trail")
    assertTrue("la radice non ha l'indietro", rest.none { FluidToolbarIds.Back.toString() in membersOf(it.id) })
    val journeys = FluidGlassContainerDiagnostics.totalJourneys

    tapTag("toolbar-pages-open")
    advance(80)
    assertTrue("la pagina è ancora in corsa", host.isMoving && host.progress < 0.9f)
    assertTrue("il viaggio della barra è partito col push", FluidGlassContainerDiagnostics.totalJourneys > journeys)
    val flights = FluidGlassContainerDiagnostics.lastJourney
    val kept = flights.single { it.toId == trail.id }
    assertEquals("i tre puntini sono lo stesso vetro", trail.id, kept.fromId)
    assertEquals("Matched", kept.change)
    val born = flights.single { membersOf(it.toId) == setOf("lead") }
    assertEquals("il tasto nuovo nasce dal fianco del vicino (D3)", "BornFromNeighbour", born.change)
    assertEquals(trail.id, born.bornFromId)
    val back = flights.single { FluidToolbarIds.Back.toString() in membersOf(it.toId) }
    assertEquals("l'indietro della figlia compare per lente, sul posto (R7 21)", "Materialize", back.change)
    val contents = FluidGlassContainerDiagnostics.lastContents
    assertEquals("l'icona che resta resta nitida: niente lampo", "Rest", contents.single { it.id == trail.id }.role)

    advance(1_500)
    assertEquals(trail.id, glassOf("trail").id)
    assertTrue(present("Condividi") && present("Opzioni") && present("Indietro"))
  }

  @Test
  fun `il gesto non cambia i tasti finche' non conferma, l'annullo non fa niente, la conferma li trasforma`() {
    pages(ToolbarHostPage.Group)
    val glasses = rest.map { it.id }.toSet()
    val journeys = FluidGlassContainerDiagnostics.totalJourneys
    val root = compose.onNodeWithTag("toolbar-pages")

    // Un dito dal bordo, tenuto a metà strada e riportato quasi al bordo: annullo.
    root.performTouchInput {
      down(Offset(2f, centerY))
      repeat(10) { moveBy(Offset(width * 0.05f, 0f)); advanceEventTime(16) }
    }
    advance(160)
    assertEquals("il dito tiene la pagina", FluidPageMove.Back, host.kind)
    assertTrue("tenuto: i tasti sono ancora quelli della pagina in cima", present("Modifica") && present("Condividi"))
    assertEquals("nessun viaggio della barra durante il trascinamento", journeys, FluidGlassContainerDiagnostics.totalJourneys)
    root.performTouchInput {
      repeat(10) { moveBy(Offset(-width * 0.048f, 0f)); advanceEventTime(16) }
      repeat(10) { advanceEventTime(16); moveBy(Offset(0f, 0f)) }
      up()
    }
    advance(1_500)
    assertFalse(host.isMoving)
    assertTrue("all'annullo non succede niente", present("Condividi"))
    assertEquals(journeys, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(glasses, rest.map { it.id }.toSet())

    // Ora fino in fondo e lasciato: la conferma. La barra si trasforma in quella di «Allegato».
    root.performTouchInput {
      down(Offset(2f, centerY))
      repeat(16) { moveBy(Offset(width * 0.05f, 0f)); advanceEventTime(16) }
    }
    advance(160)
    assertTrue("ancora tenuto oltre la soglia: i tasti non cambiano", present("Condividi"))
    assertEquals(journeys, FluidGlassContainerDiagnostics.totalJourneys)
    root.performTouchInput { up() }
    advance(64)
    assertTrue("alla conferma parte il viaggio", FluidGlassContainerDiagnostics.totalJourneys > journeys)
    advance(1_500)
    assertFalse(host.isMoving)
    assertFalse("il gruppo di tre si è diviso nei due tasti di «Allegato»", present("Condividi"))
    assertTrue(present("Modifica") && present("Preferito"))
  }

  @Test
  fun `una pagina di sempre accanto a una dichiarata ha l'indietro del modello e le sue azioni di prima`() {
    pages(ToolbarHostPage.Folders)
    val plus = glassOf("trail")
    tapTag("toolbar-pages-open")
    advance(80)
    assertTrue(host.isMoving)
    val back = FluidGlassContainerDiagnostics.lastJourney.single { FluidToolbarIds.Back.toString() in membersOf(it.toId) }
    assertEquals("l'indietro della pagina di prima compare per lente, nel modello", "Materialize", back.change)
    advance(1_500)
    assertFalse(host.isMoving)
    // A riposo sulla pagina di prima: l'indietro è ancora del modello (lo stesso vetro), l'azione è la sua di sempre.
    assertTrue(present("Indietro") && present("Condividi (di prima)"))
    assertTrue(rest.any { FluidToolbarIds.Back.toString() in membersOf(it.id) })
    assertTrue(rest.none { "trail" in membersOf(it.id) })
    tapTag("toolbar-pages-back")
    advance(1_500)
    assertFalse(present("Condividi (di prima)") || present("Indietro"))
    assertTrue("il «più» di Cartelle torna", present("Nuova cartella"))
    assertTrue(rest.none { FluidToolbarIds.Back.toString() in membersOf(it.id) })
    assertTrue(rest.any { "trail" in membersOf(it.id) } && plus.bounds == glassOf("trail").bounds)
  }

  @Test
  fun `due pagine con le actions di sempre non hanno il contenitore del modello`() {
    compose.mainClock.autoAdvance = false
    var stack by mutableStateOf(listOf("a"))
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidPageHost(stack = stack, onBack = { stack = stack.dropLast(1) }, modifier = Modifier.fillMaxSize()) { page ->
          FluidScreen(
            title = "Pagina $page",
            onBack = if (page == "a") null else ({ stack = stack.dropLast(1) }),
            actions = { FluidBarAction(FluidIcons.IosShare, "Condividi $page", onClick = {}) },
          ) { item { } }
        }
      }
    }
    compose.waitForIdle()
    advance(600)
    fun modelGlass() = rest.filter { it.id.toString().startsWith("FluidToolbarGlassId(") }
    assertTrue("nessun vetro del modello nella barra di sempre", modelGlass().isEmpty())
    assertTrue(present("Condividi a"))
    compose.runOnIdle { stack = listOf("a", "b") }
    // In coda alla suite il ricompositore non vede lo stato scritto fuori da un tocco finché non lo si notifica.
    Snapshot.sendApplyNotifications()
    advance(64)
    assertTrue(modelGlass().isEmpty())
    advance(1_200)
    assertTrue(present("Condividi b"))
    assertTrue(present("Indietro"))
    assertTrue(modelGlass().isEmpty())
    assertEquals("nessun viaggio del piano", 0L, FluidGlassContainerDiagnostics.totalJourneys)
  }
}
