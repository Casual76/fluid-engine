package dev.antigravity.fluidengine.sample.dynamic

import android.os.Looper
import android.view.View
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
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
 * F6B.13 nell'albero vero: le scene nuove della galleria «Elementi dinamici» (il campo e «trova», il composer, le
 * pillole, la barra sopra la tastiera). Per ogni scena: si apre com'è nella galleria; i bersagli sono di almeno 48 × 48 dp;
 * la semantica dice la cosa giusta; il gesto principale fa ciò che deve; e sul telefono, a 360 e a 412 dp, **nessun
 * bersaglio sta sopra un altro**, in alto (la barra) e in basso (il campo, le barre).
 *
 * F6B.8b ha tolto le eccezioni di F6B.13: la lente a riposo, i comandi dentro il campo, «Cancella», le pillole e i chip
 * del vassoio, il titolo grande selettore e la fascia del titolo agganciato hanno ora il bersaglio da 48 dp attorno al
 * disegno di prima (vedi [knownSmall]). La scena `gettoni` ha il viaggio vero della pillola nel campo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class FluidDynamicFieldsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var root: View

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

  /** La scena col tema e l'host dei modali alla radice, come nella galleria. */
  private fun open(scene: DynamicScene) = show { DynamicElementsPage(onBack = {}, initial = scene) }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        root = LocalView.current
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

  /** La tastiera: un inserto `ime` mandato alla vista di Compose, come lo manda il sistema. */
  private fun keyboard(heightDp: Int) {
    compose.runOnUiThread {
      val px = (heightDp * root.resources.displayMetrics.density).toInt()
      ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
        .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
        .build())
    }
    Snapshot.sendApplyNotifications()
    advance(600)
  }

  private fun nodes(name: String): List<SemanticsNode> =
    compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().filter { SemanticsActions.OnClick in it.config }

  private fun node(name: String): SemanticsNode = nodes(name).singleOrNull()
    ?: error("«$name»: ${nodes(name).size} nodi da toccare")

  private fun textNode(text: String): SemanticsNode = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    .firstOrNull { SemanticsActions.OnClick in it.config } ?: error("«$text» da toccare non c'è")

  /** I bersagli che dicono [name], come scritta (il segnaposto di un campo) o come nome. */
  private fun targets(name: String): List<SemanticsNode> = compose.onAllNodes(
    hasText(name) or hasContentDescription(name),
  ).fetchSemanticsNodes().filter { SemanticsActions.OnClick in it.config && SemanticsActions.SetText !in it.config }

  /** Il riquadro del nodo in dp, non ritagliato dal genitore. */
  private fun SemanticsNode.dp(): Rect {
    val id = this.id
    val box = compose.onNode(SemanticsMatcher("nodo $id") { it.id == id }, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return Rect(box.left.value, box.top.value, box.right.value, box.bottom.value)
  }

  private fun SemanticsNode.label(): String =
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
      ?: config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
      ?: "nodo $id"

  private fun SemanticsNode.isTarget(what: String, role: Role? = Role.Button) {
    val box = dp()
    assertTrue("$what: ${box.width} × ${box.height} dp", box.width >= 47.9f && box.height >= 47.9f)
    if (role != null) assertEquals(what, role, config.getOrNull(SemanticsProperties.Role))
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

  /** TalkBack lo legge: un nodo dell'albero fuso lo dice (un pezzo che esce sfocato resta per un po' in quello non fuso). */
  private fun reads(text: String): Boolean = compose.onAllNodes(
    hasText(text, substring = true) or hasContentDescription(text, substring = true),
  ).fetchSemanticsNodes().isNotEmpty()

  private fun type(text: String) {
    compose.onNode(hasSetTextAction()).performTextReplacement(text)
    Snapshot.sendApplyNotifications()
    advance(600)
  }

  private fun screenDp(): Rect = compose.onRoot().fetchSemanticsNode().dp()

  /** La lista della scena in fondo (l'azione di scorrimento, come TalkBack): con l'orologio a mano niente aspetta. */
  private fun scrollToEnd() {
    val list = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).fetchSemanticsNodes()
      .filter { SemanticsProperties.VerticalScrollAxisRange in it.config }.maxBy { it.dp().height }
    val scroll = list.config[SemanticsActions.ScrollBy].action
    compose.runOnUiThread { scroll?.invoke(0f, 100_000f) }
    Snapshot.sendApplyNotifications()
    advance(300)
  }

  /**
   * I bersagli più piccoli di 48 dp che la prova non conta, col motivo. Dopo F6B.8b ne resta uno solo:
   * - gli editor: il bersaglio è il campo che li contiene (largo quanto il campo, alto 48 dp), non la riga del testo.
   *
   * Le eccezioni di F6B.13 non ci sono più: la lente a riposo (46 dp di vetro, 48 di bersaglio), i comandi dentro il campo
   * (40 dp di disegno, 48 di bersaglio, disgiunti), «Cancella» (17 dp di cerchio), le pillole e i chip del vassoio
   * dell'assistente (30 dp), il titolo grande selettore (≈41 dp) e la fascia del titolo agganciato (46 dp).
   */
  private fun knownSmall(node: SemanticsNode): Boolean = SemanticsActions.SetText in node.config

  /** Un comando dentro il campo: un pulsante col suo nome e il bersaglio da 48 dp (F6B.8b). */
  private fun SemanticsNode.isFieldControl(what: String) = isTarget(what)

  /** Il nodo sta in una fila che scorre di lato (la barra del testo ricco): fuori dallo schermo per costruzione. */
  private fun SemanticsNode.scrollsSideways(): Boolean {
    var up = parent
    while (up != null) {
      if (SemanticsProperties.HorizontalScrollAxisRange in up.config) return true
      up = up.parent
    }
    return false
  }

  /**
   * Ogni bersaglio della scena è di almeno 48 × 48 dp (tranne quelli di [knownSmall]), e quelli in alto (la barra, sopra
   * [topDp]) e in basso (gli ultimi [bottomFromEndDp]) non si toccano e stanno nello schermo. I bersagli che ne contengono
   * altri (il campo coi suoi tasti) e quelli larghi quasi quanto lo schermo (la riga del titolo, le righe della lista) non
   * contano come sovrapposizioni. Gli errori si raccolgono tutti, e la prova li dice insieme.
   */
  private fun assertTargets(name: String, topDp: Float = 140f, bottomFromEndDp: Float = 140f) {
    val screen = screenDp()
    val problems = ArrayList<String>()
    val all = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.dp() to it }
      .filter { (box, _) -> box.width > 0f && box.height > 0f && box.bottom > 0f && box.top < screen.bottom }
    for ((box, node) in all) {
      if (knownSmall(node)) continue
      if (box.width < 47.5f || box.height < 47.5f) problems += "«${node.label()}» è ${box.width} × ${box.height} dp"
    }
    val bands = listOf("in alto" to all.filter { it.first.top < topDp }, "in basso" to all.filter { it.first.bottom > screen.bottom - bottomFromEndDp })
    for ((band, targets) in bands) {
      val small = targets.filter { (box, node) -> box.width < screen.width * 0.8f && SemanticsActions.SetText !in node.config }
      for ((box, node) in small) {
        if (node.scrollsSideways()) continue
        if (box.left < -0.5f || box.right > screen.right + 0.5f) problems += "$band: «${node.label()}» fuori dallo schermo ($box)"
      }
      for (i in small.indices) for (j in i + 1 until small.size) {
        val (a, na) = small[i]
        val (b, nb) = small[j]
        if (a.contains(b) || b.contains(a)) continue
        val overlap = a.intersect(b)
        // Due icone dello stesso gruppo hanno i bersagli da 48 dp a passo 46 (F4A.2): si toccano di 2 dp per costruzione.
        if (overlap.width > 2.5f && overlap.height > 0.5f) problems += "$band: si toccano «${na.label()}» $a e «${nb.label()}» $b"
      }
    }
    assertTrue("$name:\n${problems.joinToString("\n")}", problems.isEmpty())
  }

  private fun Rect.contains(other: Rect): Boolean =
    other.left >= left - 0.5f && other.right <= right + 0.5f && other.top >= top - 0.5f && other.bottom <= bottom + 0.5f

  // --- Il campo ------------------------------------------------------------------------------------------------------

  @Test fun `il campo della posta si apre in alto, e i preset lo portano in basso con le loro parole`() {
    open(DynamicScene.FieldPresets)
    // Il titolo è il selettore degli account (F6B.11); «Scrivi» e la lente sono pulsanti.
    val title = compose.onAllNodesWithText("Posta in arrivo").fetchSemanticsNodes()
      .first { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Heading) != null }
    assertEquals(Role.DropdownList, title.config.getOrNull(SemanticsProperties.Role))
    node("Scrivi").isTarget("Scrivi")
    // La lente a riposo: il vetro da 46 dp, il bersaglio da 48 (F6B.8b); pulsante, col suo nome.
    val lens = node("Cerca nella posta")
    lens.isTarget("la lente a riposo")
    assertTargets("campo, a riposo")
    tap(lens)
    assertTrue("il campo è aperto in alto", compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    assertTrue("il campo sta in alto", compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp().top < 140f)
    type("fattura")
    assertTargets("campo aperto in alto")
    // Il preset «Messaggio»: il campo in basso, con le sue parole.
    tap(textNode("Messaggio"))
    val message = targets("Messaggio").firstOrNull { it.dp().top > screenDp().bottom - 160f } ?: error("il campo «Messaggio» in basso")
    message.isTarget("Messaggio")
    tap(message)
    type("Ci vediamo alle otto")
    node("Invia").isFieldControl("Invia")
    tap(node("Invia"))
    assertTrue("l'invio è arrivato", shows("Inviato «Ci vediamo alle otto»"))
    assertTrue("«Messaggio» resta aperto dopo l'invio", compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    assertTargets("campo «Messaggio» aperto")
  }

  @Test fun `trova sta in fondo, conta le occorrenze e non tocca la barra in alto`() {
    open(DynamicScene.FindInPage)
    val share = node("Condividi").dp()
    assertTrue("a riposo il campo non c'è", compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
    tap(node("Trova"))
    val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
    assertTrue("il campo sta in fondo", field.dp().top > screenDp().bottom - 200f)
    type("progetto")
    assertTrue("cinque occorrenze", shows("«progetto»: 5 occorrenze"))
    // Il contatore è un'area viva, e le frecce sono pulsanti.
    assertTrue("il contatore per TalkBack", compose.onAllNodes(SemanticsMatcher("area viva") {
      SemanticsProperties.LiveRegion in it.config
    }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertTrue("la barra in alto è ferma", node("Condividi").dp() == share)
    assertTargets("trova aperto")
  }

  // --- Il composer ---------------------------------------------------------------------------------------------------

  @Test fun `il messaggio ha il piu', il tasto a stati e il vassoio, e l'invio resta aperto`() {
    open(DynamicScene.MessageComposer)
    val field = targets("Messaggio").single()
    field.isTarget("Messaggio")
    node("Dettatura").isFieldControl("Dettatura")
    tap(field)
    tap(node("Aggiungi un allegato"))
    assertTrue("il vassoio con l'allegato", reads("Foto 1"))
    type("Riga uno\nRiga due\nRiga tre")
    assertTrue("a campo pieno il tasto è «Invia»", nodes("Invia").isNotEmpty())
    tap(node("Invia"))
    advance(1_500)
    scrollToEnd()
    assertTrue("il messaggio è nella chat", shows("Riga uno"))
    assertTrue("il campo resta aperto", compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    assertTrue("il vassoio si è svuotato", !reads("Togli Foto 1"))
    assertTargets("messaggio")
  }

  @Test fun `l'assistente ha i chip e la voce, e mentre risponde la pillola prende il posto del titolo`() {
    open(DynamicScene.AssistantComposer)
    assertTrue("il chip del modello", reads("Modello · Auto"))
    val field = targets("Scrivi un messaggio…").single()
    field.isTarget("il campo dell'assistente")
    tap(field)
    type("Che cosa manca per il trasloco?")
    tap(node("Invia"))
    advance(400)
    assertTrue("la pillola di attività", reads("Assistente"))
    assertTrue("Aria risponde", shows("Aria sta rispondendo"))
    assertTargets("assistente al lavoro")
    advance(3_000)
    assertTrue("la risposta è arrivata", shows("Ne parlo con Marta"))
    assertTrue("la pillola se n'è andata", !reads("Assistente"))
  }

  // --- Le pillole ----------------------------------------------------------------------------------------------------

  @Test fun `le fermate hanno i filtri e i recenti sotto il campo, e le linee nella pagina`() {
    open(DynamicScene.Filters)
    // Nella pagina: le linee a scelta singola.
    val all = textNode("Tutte")
    all.isTarget("Tutte", role = null)
    assertEquals("Tutte è scelta", true, all.config.getOrNull(SemanticsProperties.Selected))
    tap(textNode("Notte"))
    assertTrue("solo la notturna", shows("Piazza della Libertà") && !shows("Careggi"))
    // Il campo: i filtri e i recenti, mai «Nessun risultato» prima del primo carattere.
    val field = targets("Cerca una fermata").first()
    tap(field)
    assertTrue("i filtri sotto il campo", reads("Aperti ora") && reads("Accessibile"))
    assertTrue("i recenti", reads("Aeroporto"))
    assertFalse("nessun «Nessun risultato» a campo vuoto", reads("Nessun risultato"))
    val remove = compose.onAllNodesWithContentDescription("Rimuovi dai recenti", substring = true).fetchSemanticsNodes()
      .filter { SemanticsActions.OnClick in it.config }
    assertTrue("la ✕ dei recenti", remove.isNotEmpty())
    remove.first().isTarget("Rimuovi dai recenti")
    assertTargets("filtri, campo aperto")
  }

  /** Le pillole dei gettoni suggeriti: i pulsanti che dicono [name] e non sono scelti (i gettoni lo sono, o no). */
  private fun pills(name: String): List<SemanticsNode> = compose.onAllNodesWithText(name).fetchSemanticsNodes()
    .filter { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Selected) == false &&
      SemanticsProperties.ContentDescription !in it.config }

  /** I gettoni nel campo: i pulsanti che dicono [name] come nome. */
  private fun tokenNodes(name: String): List<SemanticsNode> = compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes()
    .filter { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Selected) != null }

  @Test fun `una pillola scelta vola nel campo e diventa gettone, e il gettone tolto torna pillola`() {
    open(DynamicScene.Tokens)
    // Il campo a riposo, in basso: toccato si apre, e accanto ci sono le persone.
    val field = targets("Cerca nella posta").first()
    field.isTarget("il campo")
    tap(field)
    val luca = pills("Luca").single()
    luca.isTarget("la pillola «Luca»")
    val fieldTop = compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp().top
    assertTrue("la fila sta sopra il campo", luca.dp().bottom <= fieldTop)
    val before = pills("Anna").single().dp()
    // La pillola sparisce dalla fila nello stesso fotogramma in cui parte il suo volo; il gettone non si legge ancora.
    val action = luca.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(100)
    assertTrue("a metà volo «Luca» non si legge, né pillola né gettone", pills("Luca").isEmpty() && tokenNodes("Luca").isEmpty())
    advance(600)
    // Arrivato: il gettone nel campo, la posta di Luca, la fila richiusa (Anna non si è mossa, Marta ha preso il posto).
    val token = tokenNodes("Luca").single()
    token.isTarget("il gettone «Luca»")
    val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp()
    assertEquals("il gettone sta nella riga del campo", editor.center.y, token.dp().center.y, 4f)
    assertTrue("prima del testo", token.dp().right <= editor.left + 1f)
    assertTrue("solo la posta di Luca", shows("Partita di giovedì") && !shows("Fattura di novembre"))
    assertTrue("la pillola non c'è più", pills("Luca").isEmpty())
    assertEquals("Anna resta al suo posto", before.left, pills("Anna").single().dp().left, 0.5f)
    assertTrue("Marta ha preso il posto di Luca", pills("Marta").single().dp().left < before.right + 60f)
    // «Cancella» a testo vuoto: il primo colpo sceglie il gettone, il secondo lo toglie, e torna pillola.
    compose.onNode(hasSetTextAction()).performTextReplacement("")
    Snapshot.sendApplyNotifications()
    advance(200)
    assertEquals("scelto dal primo cancella", true, tokenNodes("Luca").single().config.getOrNull(SemanticsProperties.Selected))
    compose.onNode(hasSetTextAction()).performTextReplacement("")
    Snapshot.sendApplyNotifications()
    advance(100)
    assertTrue("a metà ritorno «Luca» non si legge", pills("Luca").isEmpty() && tokenNodes("Luca").isEmpty())
    advance(600)
    assertTrue("di nuovo pillola", pills("Luca").size == 1)
    assertTrue("di nuovo tutta la posta", shows("Fattura di novembre"))
    // La pillola torna al suo posto dichiarato: fra Anna e Marta.
    assertTrue("al suo posto", pills("Anna").single().dp().left < pills("Luca").single().dp().left &&
      pills("Luca").single().dp().left < pills("Marta").single().dp().left)
    // Il gettone toccato torna pillola anche lui.
    tap(pills("Marta").single())
    advance(300)
    tap(tokenNodes("Marta").single())
    advance(300)
    assertTrue("toccato, torna pillola", pills("Marta").size == 1 && tokenNodes("Marta").isEmpty())
    assertTargets("gettoni, campo aperto")
  }

  @Test fun `tre gettoni e il testo stanno nel campo del telefono`() {
    open(DynamicScene.Tokens)
    tap(targets("Cerca nella posta").first())
    for (name in listOf("Anna", "Luca", "Marta")) {
      tap(pills(name).single())
      advance(300)
    }
    val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp()
    for (name in listOf("Anna", "Luca", "Marta")) {
      val token = tokenNodes(name).single().dp()
      assertTrue("«$name» nel campo: $token, testo $editor", token.left >= 0f && token.right <= editor.left + 1f)
    }
    assertTrue("il testo ha ancora almeno 56 dp: $editor", editor.width >= 56f)
    assertTargets("tre gettoni")
  }

  // --- La barra sopra la tastiera ------------------------------------------------------------------------------------

  @Test fun `scrivi ha una barra per campo, che cambia passando da un campo all'altro`() {
    open(DynamicScene.KeyboardBar)
    // In alto il tasto diviso di F6B.11: due pulsanti.
    node("Invia").isTarget("Invia")
    node("Opzioni di invio").isTarget("Opzioni di invio")
    keyboard(300)
    tap(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().first())
    assertTrue("nei destinatari, i suggerimenti", reads("Anna Bellini"))
    node("Campo successivo").isTarget("Campo successivo")
    tap(node("Campo successivo"))
    assertTrue("nell'oggetto, le azioni rapide", reads("Allega") && reads("Foto"))
    tap(node("Campo successivo"))
    assertTrue("nel testo, la formattazione", reads("Grassetto") && reads("Scrivi con Aria"))
    assertTargets("tastiera, nel testo", bottomFromEndDp = 420f)
    tap(node("Fatto"))
    keyboard(0)
    assertFalse("«Fatto» chiude la barra", reads("Grassetto"))
  }

  @Test fun `la barra del testo ricco sale con Aa e dice cosa e' acceso`() {
    open(DynamicScene.RichTextKeyboard)
    val format = nodes("Formattazione").first()
    format.isTarget("Formattazione", role = null)
    keyboard(300)
    tap(format)
    val bold = nodes("Grassetto")
    assertTrue("la barra del testo ricco", bold.isNotEmpty())
    bold.first().isTarget("Grassetto", role = Role.Switch)
    assertTargets("testo ricco", bottomFromEndDp = 420f)
  }

  // --- Nessuna sovrapposizione sul telefono -----------------------------------------------------------------------

  private fun checkScenes(width: String) {
    var scene by mutableStateOf(DynamicScene.FieldPresets)
    show { androidx.compose.runtime.key(scene) { DynamicElementsPage(onBack = {}, initial = scene) } }
    listOf(
      DynamicScene.FieldPresets, DynamicScene.FindInPage, DynamicScene.MessageComposer, DynamicScene.AssistantComposer,
      DynamicScene.Filters, DynamicScene.Tokens, DynamicScene.KeyboardBar, DynamicScene.RichTextKeyboard,
      DynamicScene.ActivityPill,
    ).forEach { next ->
      compose.runOnIdle { scene = next }
      Snapshot.sendApplyNotifications()
      advance(900)
      assertTargets("$width, ${next.key}")
    }
  }

  @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun `a 360 dp nessun bersaglio sta sopra un altro`() = checkScenes("360")
  @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun `a 412 dp nessun bersaglio sta sopra un altro`() = checkScenes("412")
}
