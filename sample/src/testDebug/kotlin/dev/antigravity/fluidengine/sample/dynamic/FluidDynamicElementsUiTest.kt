package dev.antigravity.fluidengine.sample.dynamic

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * La galleria degli elementi dinamici (piano «iOS in Android», F6B.12): un test per scena. Ognuno apre la scena com'è
 * nella galleria, guarda che i bersagli siano di almeno 48 dp e che la semantica dica la cosa giusta (il nome dei tasti,
 * il ruolo di pulsante, il conteggio, l'area viva degli esiti e del contatore), e fa il gesto principale della scena.
 * Come si vede non lo prova: i fotogrammi sono le tracce del banco, il resto si guarda sul tablet (F6B.14).
 *
 * L'orologio è sempre a mano, a fotogrammi da 16 ms, come in `FluidElementToPageUiTest`: le barre e i vetri possono
 * chiedere fotogrammi anche a riposo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidDynamicElementsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var closed = 0

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  // --- Aiuti ---------------------------------------------------------------------------------------------------------

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun open(scene: DynamicScene?) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        DynamicElementsPage(onBack = { closed++ }, initial = scene)
      }
    }
    advance(800)
  }

  private fun SemanticsNodeInteraction.role(): Role? = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)

  /** Il bersaglio: almeno 48 × 48 dp, e risponde al tocco (o è spento e lo dice). */
  private fun SemanticsNodeInteraction.isTarget(what: String): SemanticsNodeInteraction {
    assertWidthIsAtLeast(48.dp)
    assertHeightIsAtLeast(48.dp)
    val config = fetchSemanticsNode().config
    assertTrue("$what: né tocco né spento", SemanticsActions.OnClick in config || SemanticsProperties.Disabled in config)
    return this
  }

  /** Il tasto che si chiama [name]: uno solo, un pulsante, con un bersaglio di almeno 48 dp. */
  private fun button(name: String, substring: Boolean = false): SemanticsNodeInteraction {
    val node = compose.onNodeWithContentDescription(name, substring = substring)
    node.isTarget(name)
    assertEquals(name, Role.Button, node.role())
    return node
  }

  /** Il primo dei tasti che si chiamano [name] che esiste ancora, o null. */
  private fun present(vararg names: String): String? = names.firstOrNull {
    compose.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty()
  }

  /** Qualcosa nell'albero dice [text], come scritta o come nome. */
  private fun shows(text: String): Boolean = compose.onAllNodes(
    hasText(text, substring = true) or hasContentDescription(text, substring = true),
    useUnmergedTree = true,
  ).fetchSemanticsNodes().isNotEmpty()

  /**
   * TalkBack legge [text]: un nodo dell'albero fuso lo dice. Un pezzo che esce sfocato resta per qualche fotogramma
   * nell'albero non fuso, ma non si legge più.
   */
  private fun reads(text: String): Boolean = compose.onAllNodes(
    hasText(text, substring = true) or hasContentDescription(text, substring = true),
  ).fetchSemanticsNodes().isNotEmpty()

  /** Il back di sistema. Lo stato che scrive va annunciato allo snapshot: l'orologio è a mano. */
  private fun pressBack() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
  }

  /** Le azioni che TalkBack propone su [node] (il menu «Altro» le offre così). */
  private fun customActions(node: SemanticsNodeInteraction): List<String> =
    node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

  private fun live(name: String, substring: Boolean = false): LiveRegionMode? =
    compose.onNodeWithContentDescription(name, substring = substring).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.LiveRegion)

  // --- L'indice ------------------------------------------------------------------------------------------------------

  // Una finestra alta: l'indice sta tutto nello schermo. Lo scorrimento delle prove (`performScrollToNode`) aspetta che la
  // lista si fermi, e con l'orologio a mano non si ferma mai.
  @Test @Config(qualifiers = "w400dp-h2400dp-xhdpi")
  fun `l'indice elenca ogni scena con un bersaglio da 48 dp, e il back della scena torna all'indice`() {
    open(null)
    compose.onNodeWithTag(DynamicTags.Index).assertExists()
    assertTrue("il segno della pagina", shows("F6B.12 ·"))
    DynamicScene.entries.forEach { scene ->
      compose.onNodeWithTag(DynamicTags.row(scene)).isTarget(scene.key)
      assertTrue("${scene.key}: il titolo", shows(scene.title))
      assertTrue("${scene.key}: l'extra di lancio", shows("dynamic.scene ${scene.key}"))
    }
    compose.onNodeWithTag(DynamicTags.row(DynamicScene.SelectionFiles)).performClick()
    advance(800)
    assertTrue(shows("Documenti"))
    pressBack()
    advance(800)
    compose.onNodeWithTag(DynamicTags.Index).assertExists()
    assertEquals("il back della scena non chiude la pagina", 0, closed)
  }

  @Test fun `l'extra di lancio sceglie la scena, e un valore sconosciuto apre l'indice`() {
    assertEquals(DynamicScene.entries.size, DynamicScene.entries.map { it.key }.toSet().size)
    DynamicScene.entries.forEach { assertEquals(it, DynamicScene.of(it.key)) }
    assertEquals(DynamicScene.SelectionNotes, DynamicScene.of(" SELEZIONE-NOTE "))
    assertEquals(DynamicScene.ActionButtons, DynamicScene.of("actionbuttons"))
    assertNull(DynamicScene.of("nessuna"))
    assertNull(DynamicScene.of(null))
    // Ogni elemento ha almeno una scena.
    DynamicElement.entries.forEach { element -> assertTrue(DynamicScene.entries.any { it.element == element }) }
  }

  // --- La selezione --------------------------------------------------------------------------------------------------

  @Test fun `posta - la tab bar diventa le azioni, il conteggio cambia e Archivia lascia un solo Annulla`() {
    open(DynamicScene.SelectionMail)
    assertTrue("la tab bar delle schede", shows("Cartelle"))
    button("Seleziona").performClick()
    advance(800)
    button("Fine")
    assertTrue(shows("0 selezionati"))
    for (id in 0..2) compose.onNodeWithTag(SelectionSceneTags.message(id)).performClick()
    advance(400)
    assertTrue(shows("3 selezionati"))
    // La tab bar non c'è più: al suo posto, nello stesso vetro, le azioni della posta.
    assertFalse("le schede sono uscite", reads("Cartelle"))
    button("Elimina")
    val put = present("Archivia", "Sposta", "Elimina")
    assertNotNull(put)
    button(put!!).performClick()
    advance(800)
    // La selezione è chiusa, le tre righe sono andate e resta un solo «Annulla» per tutte e tre, che si riempie.
    compose.onNodeWithTag(SelectionSceneTags.message(0)).assertDoesNotExist()
    assertTrue("le schede sono tornate", reads("Cartelle"))
    val undo = button("Annulla l'archiviazione", substring = true)
    assertEquals(LiveRegionMode.Polite, live("Annulla l'archiviazione", substring = true))
    undo.performClick()
    advance(800)
    for (id in 0..2) compose.onNodeWithTag(SelectionSceneTags.message(id)).assertExists()
    assertFalse(reads("Annulla l'archiviazione"))
  }

  @Test @Config(qualifiers = "w1400dp-h900dp-xhdpi")
  fun `posta sul tablet - con la barra laterale le azioni stanno nel pannello della lista`() {
    open(DynamicScene.SelectionMail)
    button("Pannello laterale").performClick()
    advance(1600)
    button("Chiudi il pannello")
    val pane = compose.onNodeWithTag(SelectionSceneTags.MailList).getBoundsInRoot()
    button("Seleziona").performClick()
    advance(800)
    compose.onNodeWithTag(SelectionSceneTags.message(4)).performClick()
    advance(400)
    assertTrue(shows("1 selezionato"))
    val delete = button("Elimina").getBoundsInRoot()
    assertTrue("le azioni nel pannello della lista: $delete in $pane", delete.left >= pane.left && delete.right <= pane.right)
    assertTrue("le azioni in basso", delete.top > (pane.top + pane.bottom) / 2)
    // Rail e barra laterale non si trasformano.
    button("Chiudi il pannello")
    assertEquals("il pannello della lista non cambia", pane, compose.onNodeWithTag(SelectionSceneTags.MailList).getBoundsInRoot())
  }

  @Test fun `note - Seleziona tutto sceglie anche le note non caricate e togliendone una il conto scende`() {
    open(DynamicScene.SelectionNotes)
    assertTrue("la cartella si carica a pagine", shows("Caricate $SelectionNotesPage di $SelectionNotesTotal"))
    button("Seleziona").performClick()
    advance(800)
    assertTrue(shows("0 selezionati"))
    // In alto i posti sono tre, «Altro» compreso: a 400 dp le azioni stanno in «Altro» (spente a zero, e TalkBack propone
    // solo quelle eseguibili). «Seleziona tutto» sta al posto dell'indietro e resta in barra quando c'è posto (scelta di
    // Alessio del 22 novembre 2026): a 400 dp c'è.
    assertFalse(customActions(button("Altro")).contains("Seleziona tutto"))
    button("Seleziona tutto").performClick()
    advance(400)
    assertTrue(shows("$SelectionNotesTotal selezionati"))
    // Tutto scelto: lo stesso tasto dice «Deseleziona», e le azioni si accendono.
    button("Deseleziona")
    assertTrue(customActions(button("Altro")).containsAll(listOf("Sposta", "Elimina")))
    compose.onNodeWithTag(SelectionSceneTags.note(0)).performClick()
    advance(400)
    assertTrue(shows("${SelectionNotesTotal - 1} selezionati"))
    button("Seleziona tutto")
    button("Fine").performClick()
    advance(800)
    button("Seleziona")
  }

  @Test fun `foto - la capsula delle azioni galleggia sulla griglia e ogni foto scelta lo dice`() {
    open(DynamicScene.SelectionPhotos)
    compose.onNodeWithTag(SelectionSceneTags.photo(0)).isTarget("foto")
    button("Seleziona").performClick()
    advance(800)
    compose.onNodeWithTag(SelectionSceneTags.photo(0)).performClick()
    compose.onNodeWithTag(SelectionSceneTags.photo(1)).performClick()
    advance(400)
    assertTrue(shows("2 selezionati"))
    assertEquals(true, compose.onNodeWithTag(SelectionSceneTags.photo(0)).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Selected))
    assertEquals(false, compose.onNodeWithTag(SelectionSceneTags.photo(2)).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Selected))
    button("Condividi")
    button("Elimina").performClick()
    advance(800)
    compose.onNodeWithTag(SelectionSceneTags.photo(0)).assertDoesNotExist()
    button("Seleziona")
  }

  @Test fun `file - senza tab bar la barra in basso della pagina cambia azioni e torna`() {
    open(DynamicScene.SelectionFiles)
    button("Filtra")
    button("Nuova cartella")
    button("Seleziona").performClick()
    advance(800)
    compose.onNodeWithTag(SelectionSceneTags.file(0)).performClick()
    advance(400)
    assertTrue(shows("1 selezionato"))
    assertTrue("«Filtra» è uscita", compose.onAllNodesWithContentDescription("Filtra").fetchSemanticsNodes().isEmpty())
    button("Condividi")
    button("Elimina")
    button("Fine").performClick()
    advance(800)
    button("Filtra")
    button("Nuova cartella")
  }

  // --- I tasti con esito ---------------------------------------------------------------------------------------------

  @Test fun `tasti - i sei preset e quello su misura hanno nome e bersaglio, e dicono l'esito`() {
    open(DynamicScene.ActionButtons)
    // «Invia» c'è due volte: nella barra del compositore e da solo, con lo stesso stato.
    val sends = compose.onAllNodesWithContentDescription("Invia").fetchSemanticsNodes()
    assertEquals(2, sends.size)
    sends.forEach { assertEquals(Role.Button, it.config.getOrNull(SemanticsProperties.Role)) }
    listOf("Salva", "Copia", "Scarica", "Carica", "Aggiungi ai preferiti", "Sincronizza").forEach { button(it) }

    // Non riesce: l'esito negativo, detto da un'area viva.
    compose.onNodeWithTag(ActionSceneTags.outcome(ActionOutcome.Failure)).performClick()
    button("Salva").performClick()
    advance(400)
    assertTrue("mentre lavora lo dice", shows("Salvataggio in corso…"))
    advance(1600)
    button("Salvataggio non riuscito")
    assertEquals(LiveRegionMode.Polite, live("Salvataggio non riuscito"))
    advance(3000)
    button("Salva")

    // Lavoro breve: solo l'esito, mai l'anello (sotto i 300 ms il tasto resta con la faccia di riposo).
    compose.onNodeWithTag(ActionSceneTags.outcome(ActionOutcome.Short)).performClick()
    button("Salva").performClick()
    advance(400)
    button("Salvato")

    // Il preferito, rifiutato: torna com'era.
    compose.onNodeWithTag(ActionSceneTags.outcome(ActionOutcome.Failure)).performClick()
    button("Aggiungi ai preferiti").performClick()
    advance(2000)
    button("Non aggiunto ai preferiti")
  }

  @Test @Config(qualifiers = "w400dp-h2400dp-xhdpi")
  fun `tasti - lo scaricamento si mette in pausa, tiene la percentuale e riprende`() {
    open(DynamicScene.ActionButtons)
    compose.onNodeWithTag(ActionSceneTags.outcome(ActionOutcome.Success)).performClick()
    button("Scarica").performClick()
    advance(700)
    val pause = compose.onNodeWithTag(ActionSceneTags.Pause)
    pause.isTarget("pausa").performClick()
    advance(64)
    val paused = compose.onNodeWithContentDescription("In pausa")
    paused.isTarget("in pausa")
    val held = paused.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    assertNotNull(held)
    assertTrue("la percentuale: $held", held!!.endsWith("%"))
    advance(800)
    assertEquals("in pausa la percentuale resta", held,
      compose.onNodeWithContentDescription("In pausa").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    compose.onNodeWithTag(ActionSceneTags.Pause).performClick()
    advance(2400)
    assertTrue("riprende e finisce", shows("Scaricato"))
  }

  // --- «Invia» col contatore -----------------------------------------------------------------------------------------

  @Test fun `invia - la scena di F6B_4 si apre dalla galleria con «Nuova mail»`() {
    open(DynamicScene.SendUndo)
    assertTrue(shows("Posta in arrivo"))
    button("Nuova mail").performClick()
    advance(1000)
    button("Invia")
    assertTrue(shows("Ci vediamo domani."))
  }

  // --- L'avviso dal tasto --------------------------------------------------------------------------------------------

  @Test fun `avviso - Archivia pubblica l'avviso col suo Annulla, Elimina quello dell'errore`() {
    open(DynamicScene.Notice)
    button("Segna come non letta")
    button("Elimina")
    button("Archivia").performClick()
    advance(1200)
    assertTrue(shows("Conversazione archiviata"))
    assertTrue("archiviata", shows("Archivio"))
    val liveCards = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
      .fetchSemanticsNodes()
    assertTrue("l'avviso è un'area viva", liveCards.isNotEmpty())
    val undo = compose.onNode(hasText("Annulla") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    undo.isTarget("Annulla").performClick()
    advance(1200)
    assertTrue(shows("Archivia: toccato «Annulla»"))
    assertTrue("di nuovo nella posta", shows("Posta in arrivo"))
    button("Elimina").performClick()
    advance(1200)
    assertTrue(shows("Eliminazione non riuscita"))
  }

  // --- L'elemento che diventa pagina ---------------------------------------------------------------------------------

  @Test fun `elemento - le quattro scene di F6B_5c si raggiungono dalla galleria`() {
    open(DynamicScene.ElementToPage)
    listOf("Il mini lettore", "Dalla griglia al visore", "La pillola della località", "«Scrivi» dalla barra").forEach {
      compose.onNodeWithText(it).isTarget(it)
    }
    compose.onNodeWithText("Dalla griglia al visore").performClick()
    advance(800)
    compose.onNodeWithTag(ElementToPageTags.thumbnail(0)).isTarget("miniatura")
    pressBack()
    advance(800)
    assertTrue("l'indice dei quattro casi", reads("Il mini lettore"))
    pressBack()
    advance(800)
    compose.onNodeWithTag(DynamicTags.Index).assertExists()
  }
}
