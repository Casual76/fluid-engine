package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPieceRecord
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.fluidGlassUnion
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarIds
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * La barra dichiarata nell'albero (piano «iOS in Android», F4A.2): una `FluidScreen` vera con `toolbar`, il cui stato
 * cambia come lo cambia la pagina di prova (la galleria «Barre (prova)»), e il contenitore del piano che ne porta i vetri.
 *
 * Prova che la barra **diventa i pezzi del piano** che il modello dice (un vetro per gruppo, con l'identità che resta fra
 * due stati: l'indietro è lo stesso vetro da «sfoglia» a «seleziona»), che ogni cambio di stato **apre un viaggio senza
 * che la pagina scriva un'animazione**, che ogni tasto ha il suo nome e un bersaglio di almeno 48 dp, che il trabocco ha
 * il suo tasto con le azioni nascoste raggiungibili, che **la tinta e la selezione restano del loro pezzo** dentro un
 * grappolo (review di F3, D R4), e che una pagina con lo slot `actions` di sempre **non cambia**. Non prova come si vede:
 * Robolectric non disegna; i viaggi si guardano sul tablet (`riprese.md` della sessione).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidToolbarBarTest {

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

  private fun page(scene: ToolbarScene) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        ToolbarContent(scene, autoMs = 0L, bottomInset = 0.dp, onBack = {})
      }
    }
    compose.waitForIdle()
    advance(400)
  }

  /** Tocca il tasto che si chiama [label]: il viaggio parte, e il test lo lascia arrivare. */
  private fun tap(label: String) {
    compose.onNodeWithContentDescription(label).performClick()
    Snapshot.sendApplyNotifications()
    advance(64)
  }

  /** Cambia lo stato dalla riga «Cambia stato» della pagina, fuori dalla barra. */
  private fun toggleFromPage() {
    compose.onNodeWithTag("toolbar-toggle").performClick()
    Snapshot.sendApplyNotifications()
    advance(64)
  }

  private val rest: List<FluidGlassPieceRecord> get() = FluidGlassContainerDiagnostics.lastRest

  /**
   * I membri del gruppo alla nascita del vetro, letti dall'identità che il registro del piano espone
   * (`FluidToolbarGlassId(a,b#0)`): il tipo è interno all'engine, il suo nome nel registro no.
   */
  private fun membersOf(record: FluidGlassPieceRecord): Set<String> =
    record.id.toString().substringAfter("(").substringBeforeLast("#").split(",").toSet()

  private fun isToolbarGlass(record: FluidGlassPieceRecord) = record.id.toString().startsWith("FluidToolbarGlassId(")

  private fun glassOf(member: Any): FluidGlassPieceRecord = rest.single { member.toString() in membersOf(it) }

  /** Ogni tasto ha il suo nome, è un pulsante, e il dito ha almeno 48 dp per lato. */
  private fun assertButton(label: String) {
    val node = compose.onNodeWithContentDescription(label)
    node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    assertEquals(label, Role.Button, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    assertTrue(label, node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
  }

  @Test
  fun `la barra ridotta conserva il bersaglio finale senza aprire un viaggio di vetro`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          ToolbarContent(ToolbarScene.Primary, autoMs = 0, bottomInset = 0.dp, onBack = {})
        }
      }
    }
    compose.waitForIdle()
    val before = compose.onNodeWithContentDescription("Preferito").getBoundsInRoot()
    tap("Preferito")
    val destination = compose.onNodeWithContentDescription("Preferito").getBoundsInRoot()
    advance(1_200)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(destination, compose.onNodeWithContentDescription("Preferito").getBoundsInRoot())
    assertEquals(before.top, destination.top)
  }

  @Test
  fun `il campo principale sale con la lista e resta raggiungibile dopo il ritiro`() {
    page(ToolbarScene.Minimize)
    val field = compose.onNodeWithText("Cerca nelle note")
    val before = field.getBoundsInRoot().top
    assertButton("Opzioni")
    compose.onNodeWithTag("toolbar-page").performTouchInput {
      swipeUp(startY = bottom * 0.75f, endY = bottom * 0.35f)
    }
    advance(1_200)
    val pinned = field.getBoundsInRoot().top
    assertTrue("campo più vicino alla barra di stato", pinned < before)
    compose.onNodeWithContentDescription("Opzioni").assertDoesNotExist()
    compose.onNodeWithTag("toolbar-page").performTouchInput {
      swipeDown(startY = bottom * 0.35f, endY = bottom * 0.75f)
    }
    advance(1_200)
    assertEquals(before, field.getBoundsInRoot().top)
    assertButton("Opzioni")
  }

  @Test
  fun `da sfoglia a seleziona e ritorno, la barra cambia gruppi e l'indietro resta lo stesso vetro`() {
    page(ToolbarScene.BrowseSelect)
    // Sfoglia: indietro, «Seleziona», i tre puntini. Un vetro per gruppo, ognuno con l'identità della barra.
    assertEquals(rest.toString(), 3, rest.size)
    assertTrue(rest.toString(), rest.all(::isToolbarGlass))
    val back = glassOf(FluidToolbarIds.Back).id
    listOf("Indietro", "Seleziona", "Opzioni").forEach(::assertButton)
    assertEquals("la prima misura non viaggia", 0L, FluidGlassContainerDiagnostics.totalJourneys)

    // La pagina cambia solo lo stato: il viaggio lo apre il contenitore.
    tap("Seleziona")
    assertTrue("un viaggio partito", FluidGlassContainerDiagnostics.totalJourneys >= 1L)
    advance(1_200)
    assertEquals(rest.toString(), 3, rest.size)
    assertEquals("l'indietro è lo stesso vetro", back, glassOf(FluidToolbarIds.Back).id)
    val group = glassOf("share")
    assertEquals("condividi ed elimina dividono un vetro", setOf("share", "delete"), membersOf(group))
    val confirm = glassOf(FluidToolbarIds.Confirm)
    assertTrue("la conferma è a destra del gruppo", confirm.bounds.left > group.bounds.right)
    listOf("Indietro", "Condividi", "Elimina", "Conferma").forEach(::assertButton)
    assertTrue(compose.onAllNodesWithContentDescription("Seleziona").fetchSemanticsNodes().isEmpty())
    // La conferma tinta a 12 dp dal gruppo neutro: un grappolo solo, due tinte (D R4).
    assertEquals(2, confirm.clusterSize)
    assertNotEquals("la conferma ha la sua tinta, il gruppo la sua", confirm.drawnTint, group.drawnTint)

    tap("Conferma")
    advance(1_200)
    assertEquals(back, glassOf(FluidToolbarIds.Back).id)
    assertTrue(compose.onAllNodesWithContentDescription("Seleziona").fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun `un tasto che diventa principale divide il gruppo e prende la tinta, gli altri restano neutri`() {
    page(ToolbarScene.Primary)
    val before = glassOf("star")
    assertEquals("tre icone in un vetro", setOf("edit", "share", "star"), membersOf(before))
    tap("Preferito")
    advance(1_200)
    val group = glassOf("edit")
    // Il vetro nuovo della stella è nato da sola (il vecchio, nato coi tre, è rimasto al gruppo).
    val star = rest.single { membersOf(it) == setOf("star") }
    assertEquals("la parte grande tiene il vetro", before.id, group.id)
    assertNotEquals(group.id, star.id)
    assertEquals("il vetro è quello nato coi tre", setOf("edit", "share", "star"), membersOf(group))
    assertNotEquals("la principale è tinta, il gruppo no", star.drawnTint, group.drawnTint)
    assertTrue(
      FluidGlassContainerDiagnostics.lastJourney.toString(),
      FluidGlassContainerDiagnostics.lastJourney.any { it.toId == star.id },
    )
  }

  @Test
  fun `quando il posto non basta i tasti a priorita' bassa vanno nel menu Altro, raggiungibili`() {
    page(ToolbarScene.Overflow)
    // Ciò che sta in `overflow { }` è sempre nel menu: il tasto «Altro» c'è già.
    assertButton("Altro")
    fun hidden(): List<String> = compose.onNodeWithContentDescription("Altro").fetchSemanticsNode().config
      .getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    assertEquals(listOf("Aggiungi ai preferiti"), hidden())
    toggleFromPage()
    advance(1_200)
    assertEquals("R5 B11: indietro più tre vetri, Altro compreso", 4, rest.size)
    assertEquals(setOf("Modifica", "Aggiungi ai preferiti"), hidden().toSet())
    assertTrue(compose.onAllNodesWithContentDescription("Modifica").fetchSemanticsNodes().isEmpty())
    assertButton("Condividi")
    assertButton("Archivia")
    toggleFromPage()
    advance(1_200)
    assertEquals(listOf("Aggiungi ai preferiti"), hidden())
  }

  @Test
  fun `la barra usa more e confirm della radice senza riusare actions e dateTimeConfirm`() {
    var labels by mutableStateOf(FluidLabels.english())
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidLabels provides labels) {
          FluidScreen(title = "Labels", toolbar = {
            confirm(onClick = {})
            overflow { text("hidden", "Hidden", onClick = {}) }
          }) { }
        }
      }
    }
    compose.waitForIdle()
    assertButton("More")
    assertButton("Confirm")
    compose.runOnIdle { labels = labels.copy(more = "Options", confirm = "Save", actions = "Commands", dateTimeConfirm = "Choose date") }
    compose.waitForIdle()
    assertButton("Options")
    assertButton("Save")
    compose.onNodeWithContentDescription("Commands").assertDoesNotExist()
    compose.onNodeWithContentDescription("Choose date").assertDoesNotExist()
  }

  @Test
  fun `la selezione di un tasto in un gruppo e' sua, e la conferma tinta accanto resta tinta`() {
    page(ToolbarScene.Tint)
    val group = glassOf("bold")
    val confirm = glassOf(FluidToolbarIds.Confirm)
    assertNotEquals(confirm.drawnTint, group.drawnTint)
    // Nel gruppo la selezione è del tasto: «Corsivo» selezionato, gli altri no; il tocco la sposta su «Grassetto».
    fun selected(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Selected) == true
    assertTrue(selected("Corsivo"))
    assertFalse(selected("Grassetto"))
    assertFalse(selected("Sottolineato"))
    tap("Grassetto")
    advance(1_200)
    assertTrue(selected("Grassetto"))
    assertFalse(selected("Corsivo"))
    assertEquals("la selezione non cambia il vetro del gruppo", group.id, glassOf("bold").id)
  }

  @Test
  fun `in un blocco unito il tasto selezionato si disegna con la sua tinta e non la passa agli altri`() {
    // Il caso della scheda del piano flottante (Grassetto, Corsivo, Sottolineato uniti), che fino a F4A.1 si disegnava
    // con la tinta del primo pezzo.
    var selectedIndex by mutableStateOf(1)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
          val ns = rememberFluidGlassNamespace()
          FluidGlassContainer(Modifier.padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(FluidIcons.FormatBold, FluidIcons.FormatItalic, FluidIcons.FormatUnderlined).forEachIndexed { index, icon ->
                FluidGlassIconButton(
                  onClick = {},
                  backdrop = backdrop,
                  selected = index == selectedIndex,
                  modifier = Modifier.fluidGlassId(index, ns).fluidGlassUnion("formato", ns),
                ) { FluidIcon(icon, null) }
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
    advance(400)
    fun tints() = (0..2).map { index -> rest.single { it.id == index }.drawnTint }
    assertTrue(rest.toString(), rest.all { it.clusterSize == 3 })
    assertEquals("i due non selezionati hanno la stessa tinta", tints()[0], tints()[2])
    assertNotEquals("il selezionato ha la sua", tints()[0], tints()[1])
    // Seleziona il primo: la tinta va con lui, il secondo torna neutro (prima: il primo tingeva tutto il blocco).
    selectedIndex = 0
    Snapshot.sendApplyNotifications()
    advance(400)
    assertEquals(tints()[1], tints()[2])
    assertNotEquals(tints()[0], tints()[1])
  }

  @Test
  fun `una pagina con lo slot actions non cambia, e il suo indietro sta dove sta quello della barra dichiarata`() {
    var declared by mutableStateOf(false)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        if (declared) {
          FluidScreen(
            title = "Pagina",
            onBack = {},
            toolbar = { button("share", FluidIcons.IosShare, "Condividi", onClick = {}) },
          ) { item { } }
        } else {
          FluidScreen(
            title = "Pagina",
            onBack = {},
            actions = { FluidBarAction(icon = FluidIcons.IosShare, contentDescription = "Condividi", onClick = {}) },
          ) { item { } }
        }
      }
    }
    compose.waitForIdle()
    advance(400)
    // Lo slot di sempre: nessun contenitore del piano, i tasti di sempre (F1A.11: vetri a 16 dp dai bordi).
    assertEquals("nessun contenitore per lo slot actions", 0, FluidGlassContainerDiagnostics.liveContainers)
    assertTrue("nessun pezzo del piano", FluidGlassContainerDiagnostics.lastRest.isEmpty())
    val width = 400f
    fun centerX(label: String): Float {
      val bounds = compose.onNodeWithContentDescription(label).getBoundsInRoot()
      return ((bounds.left + bounds.right) / 2f).value
    }
    // Il centro del tasto non dipende da quale nodo porta il nome (riquadro da 48, lente da 44 o icona).
    assertEquals("indietro: vetro da 44 a 16 dp", 16f + 22f, centerX("Indietro"), 0.5f)
    assertEquals("azione: vetro da 44 a 16 dp da destra", width - 16f - 22f, centerX("Condividi"), 0.5f)
    val legacyBack = centerX("Indietro")
    val legacyAction = centerX("Condividi")

    // La stessa pagina con la barra dichiarata: gli stessi tasti negli stessi posti, ora sul piano.
    declared = true
    Snapshot.sendApplyNotifications()
    advance(400)
    assertEquals(1, FluidGlassContainerDiagnostics.liveContainers)
    assertEquals(legacyBack, centerX("Indietro"), 0.5f)
    assertEquals(legacyAction, centerX("Condividi"), 0.5f)
  }
}
