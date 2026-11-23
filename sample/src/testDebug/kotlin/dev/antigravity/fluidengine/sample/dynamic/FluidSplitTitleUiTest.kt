package dev.antigravity.fluidengine.sample.dynamic

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.selectButton
import dev.antigravity.fluidengine.ui.fluid.toolbar.selection
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
 * F6B.11 nell'albero vero (la galleria «Elementi dinamici» e una `FluidScreen`): il tasto diviso nella barra e da solo,
 * il titolo che diventa selettore (menu e pannello) e un tasto che cambia nello stesso posto (l'indietro che diventa
 * «Seleziona tutto»), che resta un vetro solo. Per ogni elemento: il nome per TalkBack, il ruolo, i bersagli di almeno
 * 48 × 48 dp e, sul telefono (360 e 412 dp, in verticale e in orizzontale), sul pieghevole e sul tablet, **nessuna
 * intersezione** fra i bersagli della barra, tutti dentro lo schermo. Come si vede lo dicono il banco e il tablet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class FluidSplitTitleUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  private var diagnosticsWere = false

  @After fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  /** Il contenuto col tema e l'host dei modali (i menu e i pannelli si aprono lì). */
  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    advance(900)
  }

  private fun node(name: String): SemanticsNode = compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes()
    .single { SemanticsActions.OnClick in it.config }

  private fun textNode(text: String): SemanticsNode = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    .first { SemanticsActions.OnClick in it.config }

  /** Il riquadro del nodo in dp, **non ritagliato** dal genitore (un bersaglio da 48 dp esce dal vetro da 46). */
  private fun SemanticsNode.dp(): Rect {
    val id = this.id
    val box = compose.onNode(SemanticsMatcher("nodo $id") { it.id == id }, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return Rect(box.left.value, box.top.value, box.right.value, box.bottom.value)
  }

  private fun SemanticsNode.isTarget(what: String, role: Role = Role.Button) {
    val box = dp()
    assertTrue("$what: ${box.width} × ${box.height} dp", box.width >= 47.9f && box.height >= 47.9f)
    assertEquals(what, role, config.getOrNull(SemanticsProperties.Role))
  }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun shows(text: String): Boolean = compose.onAllNodes(
    hasText(text, substring = true) or hasContentDescription(text, substring = true), useUnmergedTree = true,
  ).fetchSemanticsNodes().isNotEmpty()

  private fun screenDp(): Rect = compose.onRoot().fetchSemanticsNode().dp()

  /**
   * I bersagli della riga della barra in alto (il pezzo alto [rowBottomDp]): non si toccano, e stanno nello schermo. Non
   * conta l'area del titolo di sempre, larga quanto la riga, che riporta in cima.
   */
  private fun assertBarTargetsApart(name: String, rowBottomDp: Float = 140f) {
    val screen = screenDp()
    val targets = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.dp() to it }
      .filter { (box, _) -> box.top < rowBottomDp && box.width < screen.width * 0.8f }
    assertTrue("$name: i bersagli della barra (${targets.size})", targets.size >= 2)
    for ((box, node) in targets) {
      val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
      assertTrue("$name: $label fuori dallo schermo ($box su $screen)", box.left >= -0.5f && box.right <= screen.right + 0.5f)
    }
    for (i in targets.indices) for (j in i + 1 until targets.size) {
      val (a, na) = targets[i]
      val (b, nb) = targets[j]
      val overlap = a.intersect(b)
      // Due icone dello stesso gruppo hanno i bersagli da 48 dp a passo 46 (F4A.2): si toccano di 2 dp per costruzione,
      // da prima di F6B.11. Si conta un'intersezione oltre quella.
      val apart = overlap.width <= 2.5f || overlap.height <= 0.5f
      assertTrue("$name: si toccano ${na.config.getOrNull(SemanticsProperties.ContentDescription)} $a e " +
        "${nb.config.getOrNull(SemanticsProperties.ContentDescription)} $b", apart)
    }
  }

  // --- Il tasto diviso ---------------------------------------------------------------------------------------------

  @Test fun `Invia e la freccia sono due pulsanti da 48 dp, disgiunti, che fanno l'azione e aprono il menu`() {
    show { DynamicElementsPage(onBack = {}, initial = DynamicScene.SplitButton) }
    val send = node("Invia")
    val arrow = node("Opzioni di invio")
    send.isTarget("Invia")
    arrow.isTarget("Opzioni di invio")
    val a = send.dp()
    val b = arrow.dp()
    assertTrue("disgiunti: $a e $b", a.right <= b.left + 0.5f)
    assertTrue("la pressione lunga sull'azione apre il menu", SemanticsActions.OnLongClick in send.config)
    tap(send)
    assertTrue(shows("Inviata ora"))
    tap(node("Opzioni di invio"))
    assertTrue("il menu è aperto", shows("Programma invio"))
    tap(textNode("Programma invio"))
    advance(600)
    assertTrue(shows("Programmata per domani alle 8"))
    assertBarTargetsApart("tasto diviso")
  }

  @Test fun `il tasto diviso da solo ha due bersagli da 48 dp e la freccia apre il suo menu`() {
    show { DynamicElementsPage(onBack = {}, initial = DynamicScene.SplitButton) }
    val export = node("Esporta")
    val formats = node("Formati di esportazione")
    export.isTarget("Esporta")
    formats.isTarget("Formati di esportazione")
    assertTrue("disgiunti", export.dp().right <= formats.dp().left + 0.5f)
    tap(formats)
    assertTrue(shows("Come PDF"))
    // L'icona: il nome per TalkBack è l'azione, la freccia il suo «Altro».
    node("Condividi").isTarget("Condividi")
    node("Altro (Condividi)").isTarget("Altro (Condividi)")
  }

  // --- Il titolo che diventa selettore -------------------------------------------------------------------------------

  @Test fun `il titolo grande col disco e' un selettore che apre gli account, e la scelta cambia il titolo`() {
    show { DynamicElementsPage(onBack = {}, initial = DynamicScene.TitleMenu) }
    val title = compose.onAllNodesWithText("Posta in arrivo").fetchSemanticsNodes()
      .first { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Heading) != null }
    assertEquals(Role.DropdownList, title.config.getOrNull(SemanticsProperties.Role))
    assertEquals("Cambia casella", title.config[SemanticsActions.OnClick].label)
    assertTrue("largo almeno 48 dp", title.dp().width >= 48f)
    tap(title)
    assertTrue("gli account", shows("Lavoro") && shows("Aggiungi account"))
    tap(textNode("Lavoro"))
    advance(600)
    assertTrue("il titolo è il nuovo account", compose.onAllNodesWithText("Lavoro").fetchSemanticsNodes().isNotEmpty())
  }

  @Test fun `con un'attivita' la pillola sta accanto al titolo selettore, che resta, e non si toccano`() {
    show { DynamicElementsPage(onBack = {}, initial = DynamicScene.TitleMenu) }
    tap(compose.onAllNodesWithText("Sincronizza").fetchSemanticsNodes().first { SemanticsActions.OnClick in it.config })
    advance(600)
    val pill = compose.onAllNodesWithContentDescription("Sincronizzo", substring = true).fetchSemanticsNodes()
    assertTrue("la pillola c'è", pill.isNotEmpty())
    assertTrue("il titolo selettore resta", compose.onAllNodesWithText("Posta in arrivo").fetchSemanticsNodes()
      .any { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList })
    assertBarTargetsApart("titolo e pillola")
  }

  @Test fun `il titolo in linea apre il pannello delle localita', e la scelta lo chiude`() {
    show { DynamicElementsPage(onBack = {}, initial = DynamicScene.TitlePanel) }
    val title = textNode("Firenze")
    title.isTarget("Firenze", Role.DropdownList)
    assertEquals("Cambia località", title.config[SemanticsActions.OnClick].label)
    tap(title)
    advance(600)
    val rows = compose.onAllNodesWithText("Roma").fetchSemanticsNodes().filter { SemanticsActions.OnClick in it.config }
    assertTrue("il pannello con le località", rows.isNotEmpty())
    tap(rows.first())
    advance(900)
    assertTrue("chiuso", compose.onAllNodesWithText("Milano").fetchSemanticsNodes().isEmpty())
    assertTrue(compose.onAllNodesWithText("Roma").fetchSemanticsNodes().isNotEmpty())
  }

  // --- Nessuna intersezione: telefono, pieghevole, tablet ------------------------------------------------------------

  private fun checkScenes(name: String) {
    var scene by mutableStateOf(DynamicScene.SplitButton)
    show { androidx.compose.runtime.key(scene) { DynamicElementsPage(onBack = {}, initial = scene) } }
    assertBarTargetsApart("$name, tasto diviso")
    compose.runOnIdle { scene = DynamicScene.TitlePanel }
    Snapshot.sendApplyNotifications()
    advance(900)
    assertBarTargetsApart("$name, titolo")
    compose.runOnIdle { scene = DynamicScene.TitleMenu }
    Snapshot.sendApplyNotifications()
    advance(900)
    assertBarTargetsApart("$name, titolo con il menu")
  }

  @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun `a 360 dp i bersagli della barra non si toccano`() = checkScenes("360")
  @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun `a 412 dp i bersagli della barra non si toccano`() = checkScenes("412")
  @Test @Config(qualifiers = "w780dp-h360dp-xhdpi") fun `in orizzontale sul telefono i bersagli non si toccano`() = checkScenes("780x360")
  @Test @Config(qualifiers = "w673dp-h841dp-xhdpi") fun `sul pieghevole aperto i bersagli non si toccano`() = checkScenes("673")
  @Test @Config(qualifiers = "w753dp-h1205dp-xhdpi") fun `sul tablet in verticale i bersagli non si toccano`() = checkScenes("753")
  @Test @Config(qualifiers = "w1205dp-h753dp-xhdpi") fun `sul tablet in orizzontale i bersagli non si toccano`() = checkScenes("1205")

  // --- Un tasto che cambia nello stesso posto resta un vetro solo --------------------------------------------------

  private val rest get() = FluidGlassContainerDiagnostics.lastRest

  @Test fun `l'indietro che diventa Seleziona tutto e' lo stesso vetro del piano, senza lente`() {
    // Il registro del piano, come in `FluidToolbarBarTest`: i vetri veri, coi programmi accettati.
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    show {
      var selecting by remember { mutableStateOf(false) }
      val state = remember { FluidSelectionState<Int>() }
      FluidScreen(title = "Posta", onBack = {}, toolbar = {
        if (selecting) {
          selection(state, count = null, onSelectAll = { state.select(1) }, onDone = { selecting = false },
            placement = FluidSelectionBarPlacement.Top) {}
        } else {
          selectButton(onClick = { selecting = true })
        }
      }) {}
    }
    val back = rest.singleOrNull { it.id.toString().contains("fluid:back") }
      ?: error("il vetro dell'indietro a riposo: $rest")
    val journeys = FluidGlassContainerDiagnostics.totalJourneys
    tap(compose.onAllNodesWithContentDescription("Seleziona").fetchSemanticsNodes().firstOrNull { SemanticsActions.OnClick in it.config }
      ?: error("«Seleziona»"))
    assertTrue("un viaggio è partito", FluidGlassContainerDiagnostics.totalJourneys > journeys)
    assertTrue("«Seleziona tutto» c'è", compose.onAllNodesWithContentDescription("Seleziona tutto").fetchSemanticsNodes().isNotEmpty())
    node("Seleziona tutto").isTarget("Seleziona tutto")
    val now = rest.singleOrNull { it.id == back.id } ?: error("il vetro dell'indietro in selezione: $rest")
    assertEquals("lo stesso vetro", back.id, now.id)
    assertEquals("fermo al suo posto", back.bounds, now.bounds)
    assertTrue("nessun vetro nuovo per «Seleziona tutto»: $rest", rest.none { it.id.toString().contains("selection-all") })
    // E all'uscita dalla selezione l'indietro torna nello stesso vetro.
    tap(compose.onAllNodesWithContentDescription("Fine").fetchSemanticsNodes().firstOrNull { SemanticsActions.OnClick in it.config }
      ?: error("«Fine»"))
    assertTrue("l'indietro di nuovo nello stesso vetro: $rest", rest.any { it.id == back.id })
  }
}
