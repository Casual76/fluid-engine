package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidFilter
import dev.antigravity.fluidengine.ui.fluid.FluidFilterChips
import dev.antigravity.fluidengine.ui.fluid.FluidFilterMode
import dev.antigravity.fluidengine.ui.fluid.FluidFilterPresets
import dev.antigravity.fluidengine.ui.fluid.FluidFilterSelection
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSuggestion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSuggestions
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6B.8 nella resa vera (Robolectric, testo misurato): la fila di pillole che a testo grande diventa il tasto «Filtri · N»
 * e apre il menu dal suo vetro, la selezione identica fra fila e menu, la pillola del periodo che si apre nei suoi valori,
 * i recenti con la ✕, lo stato vuoto prima del primo carattere, la semantica e i bersagli da 48 dp. La regola del
 * trabocco con i numeri di Transit e' in `engine-ui/.../FluidFilterChipsTest.kt`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidFilterChipsUiTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val filters = listOf(
    FluidFilter("aperti", "Aperti ora"),
    FluidFilter("accessibile", "Accessibile"),
    FluidFilter("wifi", "Wi-Fi"),
  )
  private val labelsOf = filters.map { it.label }

  private var selection by mutableStateOf(FluidFilterSelection.None)
  private var fontScale by mutableFloatStateOf(1f)
  private var rowFilters by mutableStateOf(filters)

  @Composable
  private fun Host(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val density = LocalDensity.current
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale),
        LocalFluidGlassModalHostState provides host,
        LocalFluidMotionPolicy provides FluidMotionPolicy(0f),
      ) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  /** Una volta per test (`setContent` si chiama una volta sola): poi cambiano lo stato e la scala. */
  private fun showRow(list: List<FluidFilter> = filters, widthDp: Int = 360, mode: FluidFilterMode = FluidFilterMode.Multiple) {
    rowFilters = list
    compose.setContent {
      Host {
        FluidFilterChips(
          filters = rowFilters,
          selection = selection,
          onChange = { selection = it },
          mode = mode,
          modifier = Modifier.width(widthDp.dp),
        )
      }
    }
    compose.waitForIdle()
  }

  /** Il bersaglio del tocco del nodo, in dp: almeno 48 × 48 (i 48 dp di Android). */
  private fun SemanticsNodeInteraction.assertTargetAtLeast48(): SemanticsNodeInteraction {
    val bounds = fetchSemanticsNode().touchBoundsInRoot
    val density = compose.density.density
    assertTrue("bersaglio alto ${bounds.height / density} dp", bounds.height / density >= 47.5f)
    assertTrue("bersaglio largo ${bounds.width / density} dp", bounds.width / density >= 47.5f)
    return this
  }

  private fun exists(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun keyNode(): SemanticsNode? =
    compose.onAllNodesWithText("Filtri", substring = true).fetchSemanticsNodes().firstOrNull()

  private fun selectedOf(text: String): Boolean = compose.onNodeWithText(text).fetchSemanticsNode().let { node ->
    generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Selected) } ?: false
  }

  /** L'etichetta [text] e' intera, senza puntini ne' tagli (in Transit si leggeva «Tutt», «Urba»). */
  private fun assertWhole(text: String) {
    // TalkBack vede una pillola sola: quella di misura (misurata e mai posata) non ha semantica nell'albero fuso.
    assertEquals("«$text» una volta sola per TalkBack", 1, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
    // Tagliata vuol dire andata a capo, piu' larga del suo nodo o coi puntini: la sola altezza della riga non conta.
    // Solo le etichette che si vedono: la pillola di misura e' misurata e mai posata, e il suo testo (senza a capo,
    // misurato a larghezza libera) ha per costruzione un paragrafo piu' largo del nodo.
    val shown = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
      .filter { node -> generateSequence(node) { it.parent }.all { it.layoutInfo.isPlaced } }
    assertEquals("«$text» si vede una volta", 1, shown.size)
    for (node in shown) {
      val results = ArrayList<TextLayoutResult>()
      node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
      val layout = results.first()
      assertEquals("«$text» su una riga", 1, layout.lineCount)
      assertFalse("«$text» andata a capo e tagliata", layout.multiParagraph.didExceedMaxLines)
      // Il paragrafo di un testo che puo' andare a capo e' largo quanto il posto offerto, il nodo quanto il testo: conta
      // dove finisce la riga, che deve stare nel nodo (un pixel di arrotondamento).
      val right = layout.getLineRight(0)
      assertTrue(
        "«$text» finisce a $right px in un nodo largo ${layout.size.width} px (nodo ${node.id}, ${node.boundsInRoot})",
        right <= layout.size.width + 1f,
      )
      assertFalse("«$text» coi puntini", layout.isLineEllipsized(0))
    }
  }

  @Test
  fun `a 1,0 la fila e' intera e senza tasto, a 2,0 si raccoglie in Filtri e nessuna etichetta si taglia`() {
    showRow()
    for (label in labelsOf) assertWhole(label)
    assertEquals(null, keyNode())

    for (scale in listOf(1.3f, 2f)) {
      fontScale = scale
      compose.waitForIdle()
      val visible = labelsOf.filter { exists(it) }
      for (label in visible) assertWhole(label)
      if (scale == 2f) assertTrue("a 2,0 c'e' il tasto", keyNode() != null)
      if (keyNode() == null) assertEquals("$scale: senza tasto la fila e' intera", labelsOf, visible)
    }
  }

  @Test
  fun `il tasto apre il menu con le pillole che contiene, le voci lasciano il menu aperto e la selezione e' la stessa`() {
    fontScale = 2f
    showRow()
    val key = requireNotNull(keyNode()) { "a 2,0 la fila deve raccogliersi" }
    val hidden = labelsOf.filterNot { exists(it) }
    assertTrue("almeno una pillola e' nel tasto", hidden.isNotEmpty())

    compose.onNodeWithText(key.config[SemanticsProperties.Text].first().text).performClick()
    compose.waitForIdle()
    // Il menu ha le pillole che la fila non mostra, con il loro nome intero.
    for (label in hidden) assertTrue("«$label» nel menu", exists(label))

    val pick = hidden.last()
    compose.onNodeWithText(pick).performClick()
    compose.waitForIdle()
    val id = filters.first { it.label == pick }.id
    assertTrue("la voce ha acceso il filtro", selection.isSelected(id))
    assertTrue("il menu resta aperto", exists(pick))
    assertTrue("la voce mostra la spunta a menu aperto", selectedOf(pick))
    // Il tasto conta il filtro acceso, e TalkBack lo dice.
    assertTrue(exists("Filtri · 1"))

    // Chiuso il menu e tornati a 1,0, la pillola nella fila e' accesa: la stessa selezione.
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.mainClock.advanceTimeBy(2_000)
    fontScale = 1f
    compose.waitForIdle()
    assertEquals(null, keyNode())
    assertTrue("la pillola nella fila e' accesa", selectedOf(pick))
    // E il tocco nella fila la spegne, come la voce del menu.
    compose.onNodeWithText(pick).performClick()
    compose.waitForIdle()
    assertFalse(selection.isSelected(id))
  }

  @Test
  fun `il tasto dice a TalkBack quanti filtri accesi contiene`() {
    selection = FluidFilterSelection.of("accessibile", "wifi")
    fontScale = 2f
    showRow()
    val key = requireNotNull(keyNode())
    val state = generateSequence(key) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.StateDescription) }
    val text = key.config[SemanticsProperties.Text].first().text
    val count = text.substringAfter("· ", "0").toInt()
    // «Wi-Fi», l'ultima, esce per prima: il tasto contiene almeno lei, accesa.
    assertTrue("$text conta i filtri accesi che contiene", count >= 1)
    assertEquals("attivi: $count", state)
  }

  @Test
  fun `la pillola del periodo si apre nei suoi valori dal suo vetro e mostra quello scelto`() {
    showRow(listOf(FluidFilter("aperti", "Aperti ora"), FluidFilterPresets.lastDays()))
    compose.onNodeWithText("Periodo").performClick()
    compose.waitForIdle()
    for (label in listOf("Qualsiasi", "7 giorni", "30 giorni", "Altro")) assertTrue("«$label» nel menu", exists(label))

    compose.onNodeWithText("30 giorni").performClick()
    compose.mainClock.advanceTimeBy(2_000)
    compose.waitForIdle()
    assertEquals("30", selection.valueOf("periodo"))
    assertFalse("una scelta fra alternative chiude il menu", exists("Qualsiasi"))
    compose.onNodeWithContentDescription("Periodo, 30 giorni").assertExists()
    assertTrue(selectedOf("30 giorni"))
  }

  @Test
  fun `in Single la fila e' un gruppo di scelte esclusive`() {
    selection = FluidFilterSelection.of("aperti")
    showRow(mode = FluidFilterMode.Single)
    compose.onNodeWithText("Wi-Fi").performClick()
    compose.waitForIdle()
    assertEquals(FluidFilterSelection.of("wifi"), selection)
    assertFalse(selectedOf("Aperti ora"))
    assertTrue(selectedOf("Wi-Fi"))
  }

  @Test
  fun `ogni pillola e il tasto sono pulsanti con lo stato e un bersaglio di almeno 48 dp`() {
    val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    showRow(filters + FluidFilterPresets.lastDays())
    for (scale in listOf(1f, 1.3f, 2f)) {
      fontScale = scale
      compose.waitForIdle()
      val buttons = compose.onAllNodes(button)
      val nodes = buttons.fetchSemanticsNodes()
      assertTrue(nodes.isNotEmpty())
      for (i in nodes.indices) {
        buttons[i].assertTargetAtLeast48()
        assertTrue("con lo stato «selezionato»", nodes[i].config.getOrNull(SemanticsProperties.Selected) != null)
      }
    }
  }

  // --- I suggerimenti -------------------------------------------------------------------------------------------

  private var picked = ""
  private val removed = ArrayList<String>()
  private var query by mutableStateOf("")
  private var loading by mutableStateOf(false)

  private fun showSuggestions(list: List<FluidSearchSuggestion>) {
    compose.setContent {
      Host {
        FluidSearchSuggestions(
          query = query,
          suggestions = list,
          onSuggestionClick = { picked = it.id },
          onRemoveRecent = { removed += it.id },
          loading = loading,
        )
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `prima del primo carattere niente Nessun risultato, dopo si`() {
    showSuggestions(emptyList())
    assertFalse(exists("Nessun risultato"))
    query = "sta"
    loading = true
    compose.waitForIdle()
    assertFalse("mentre i risultati arrivano", exists("Nessun risultato"))
    loading = false
    compose.waitForIdle()
    assertTrue(exists("Nessun risultato"))
  }

  @Test
  fun `i recenti hanno la ✕ per riga, da 48 dp, e l'azione per TalkBack`() {
    showSuggestions(listOf(
      FluidFilterPresets.recent("r1", "Stazione centrale"),
      FluidFilterPresets.nearby("n1", "Bar Roma"),
    ))
    compose.onNodeWithContentDescription("Rimuovi dai recenti: Stazione centrale")
      .assertTargetAtLeast48()
      .performClick()
    compose.waitForIdle()
    assertEquals(listOf("r1"), removed)
    // Il vicino non e' un recente: niente ✕.
    assertEquals(1, compose.onAllNodesWithContentDescription("Rimuovi dai recenti", substring = true).fetchSemanticsNodes().size)
    compose.onNodeWithText("Bar Roma").performClick()
    compose.waitForIdle()
    assertEquals("n1", picked)
    val row = compose.onNodeWithText("Stazione centrale").fetchSemanticsNode()
    val actions = generateSequence(row) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsActions.CustomActions) }
    assertTrue("l'azione per TalkBack sulla riga", actions.orEmpty().any { it.label == "Rimuovi dai recenti" })
  }
}
