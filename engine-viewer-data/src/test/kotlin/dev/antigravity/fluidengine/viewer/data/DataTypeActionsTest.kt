package dev.antigravity.fluidengine.viewer.data

import android.content.ClipboardManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerFileActions
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerStrings
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * F6C.20 · «•••» delle pagine della famiglia «data» senza azioni del tipo fino a F6C.19 (Markdown, HTML, EPUB, mail,
 * calendario, contatti): ogni pagina, aperta da un file vero col risolutore e mostrata nel visore vero con il menu del
 * file e il condividi dell'app, ha in «•••» le sue voci, nessuna col nome di una voce del menu del file né di un tasto
 * della sua barra, e ogni voce provata fa davvero la sua azione (gli appunti, il titolo portato in vista, la dimensione
 * data alla pagina web, il capitolo, le intestazioni, l'allegato aperto, le immagini remote). Ogni tasto della barra è
 * in vista e nello schermo, a 412 dp e (la classe sotto) a 360 dp: la barra tiene tre vetri (R5 B11), «•••», il
 * centro e condividi. La WebView su Robolectric non c'è: si guarda ciò che la pagina le dà.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-mdpi")
open class DataTypeActionsTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
  private val documents = ArrayList<FluidViewerDocument>()
  private var started = false

  /** Le voci del menu del nome, che il visore toglierebbe da «•••». */
  private val fileMenu = FluidViewerStrings().run { setOf(share, openWith, copyFile, print, info, rename, move, duplicate, delete) }
  private val anyNode = SemanticsMatcher("ogni nodo") { true }

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    if (started) controller.pause().stop().destroy()
    documents.forEach { it.close() }
  }

  // --- Il visore ---------------------------------------------------------------------------------------------------

  private fun file(name: String): File {
    val context = RuntimeEnvironment.getApplication()
    val dir = File(context.cacheDir, "azioni").apply { mkdirs() }
    val target = File(dir, name)
    requireNotNull(javaClass.classLoader!!.getResourceAsStream("data/$name")).use { input -> target.outputStream().use { input.copyTo(it) } }
    return target
  }

  private fun show(name: String, openWith: (() -> Unit)? = null) {
    val context = RuntimeEnvironment.getApplication()
    val document = runBlocking { FluidViewerRegistry.open(context, FluidViewerSource.of(file(name)), openWith = openWith) }
    documents += document
    val actions = FluidViewerFileActions(share = {}, openWith = {}, copy = {}, print = {}, rename = {}, move = {},
      duplicate = {}, delete = {})
    started = true
    controller.setup().get().setContent {
      FluidTheme(EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false)) {
        FluidViewerInfoProvider({ fluidDataViewerInfo(document) }) {
          // Il condividi dell'app in basso a destra, come in un'app vera: il terzo vetro della barra.
          FluidFileViewer(1, content = { document.content }, onBack = {}, fileActions = { actions },
            toolbar = { button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar) })
        }
      }
    }
    advance(1200)
    waitFor("«•••» di $name") { more() != null }
    advance(600)
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun waitFor(what: String, condition: () -> Boolean) {
    repeat(150) {
      if (condition()) return
      advance(100)
      Thread.sleep(10)
    }
    assertTrue("non arriva: $what (${texts()})", condition())
  }

  private fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

  /** L'etichetta della voce, senza il sottotitolo: il primo testo della riga. */
  private fun SemanticsNode.label(): String? = config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

  private fun SemanticsNode.box(): DpRect {
    val target = id
    return compose.onNode(SemanticsMatcher("nodo $target") { it.id == target }).getUnclippedBoundsInRoot()
  }

  /** Per i messaggi: i testi e le descrizioni che si vedono ora. */
  private fun texts(): String = compose.onAllNodes(anyNode).fetchSemanticsNodes().mapNotNull {
    it.text() ?: it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
  }.joinToString(" | ")

  private fun more(): SemanticsNode? = compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes()
    .singleOrNull { SemanticsActions.OnClick in it.config }

  private fun screen(): DpRect = compose.onRoot().getUnclippedBoundsInRoot()

  /** Un tasto della barra in basso: la sua descrizione è il nome, e sta nella fascia in fondo allo schermo. */
  private fun bar(label: String): SemanticsNode? {
    val bottom = screen().bottom.value
    return compose.onAllNodes(hasContentDescription(label) and hasClickAction()).fetchSemanticsNodes()
      .firstOrNull { it.box().top.value > bottom - BarZone }
  }

  /**
   * La barra in basso: «•••», ognuno di [labels] e condividi, in vista e dentro lo schermo, e un «•••» solo (un tasto
   * mandato nel trabocco ne farebbe un secondo).
   */
  private fun assertBar(vararg labels: String) {
    val screen = screen()
    assertEquals("un «•••» solo nella barra (${texts()})", 1,
      compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes().count { SemanticsActions.OnClick in it.config })
    for (label in listOf("Altro", *labels, "Condividi")) {
      val node = bar(label)
      assertTrue("«$label» non è nella barra a ${screen.right} dp (${texts()})", node != null)
      compose.onNode(SemanticsMatcher("«$label»") { it.id == node!!.id }).assertIsDisplayed()
      val box = node!!.box()
      assertTrue("«$label» esce dallo schermo a ${screen.right} dp: $box",
        box.left.value >= screen.left.value - 0.5f && box.right.value <= screen.right.value + 0.5f &&
          box.bottom.value <= screen.bottom.value + 0.5f)
    }
  }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  /** Le righe che compaiono dopo [open]: le voci del menu che si è aperto, anche quelle spente. */
  private fun opened(open: () -> Unit): List<SemanticsNode> {
    val before = compose.onAllNodes(anyNode).fetchSemanticsNodes().map { it.id }.toSet()
    open()
    return compose.onAllNodes(anyNode).fetchSemanticsNodes().filter { it.id !in before && it.isRow() }
  }

  private fun SemanticsNode.isRow() = text() != null &&
    (SemanticsActions.OnClick in config || SemanticsProperties.Disabled in config)

  /** Apre «•••» e ne dà le righe. */
  private fun openMore(): List<SemanticsNode> = opened { tap(requireNotNull(more()) { "niente «•••» (${texts()})" }) }

  /** La riga di un sottomenu aperto con l'etichetta [label]: il sottomenu prende il posto del menu nello stesso vetro. */
  private fun menuRow(label: String): SemanticsNode = compose.onAllNodes(anyNode).fetchSemanticsNodes()
    .filter { it.isRow() && it.label() == label }.let { rows ->
      assertTrue("la voce «$label» (${texts()})", rows.isNotEmpty())
      rows.last()
    }

  private fun labels(nodes: List<SemanticsNode>) = nodes.map { it.text()!! }.toSet()

  /** La riga che comincia con [label] (una voce col sottotitolo ha anche quello nel testo). */
  private fun List<SemanticsNode>.row(label: String) = single { it.text()!!.startsWith(label) }

  /** Le voci di «•••» sono [expected], e nessuna ha il nome di una voce del menu del file o di [bar]. */
  private fun assertMore(expected: Set<String>, rows: List<SemanticsNode>, bar: Set<String> = emptySet()) {
    assertEquals("le voci di «•••»", expected, labels(rows))
    for (item in rows.map { it.label()!! }) {
      assertFalse("«$item» è anche nel menu del file", item in fileMenu)
      assertFalse("«$item» è anche un tasto della barra", item in bar)
    }
  }

  private fun clipboard(): String? = controller.get().getSystemService(ClipboardManager::class.java)
    .primaryClip?.getItemAt(0)?.text?.toString()

  private fun hasValue(key: SemanticsPropertyKey<Int>, value: Int) =
    compose.onAllNodes(SemanticsMatcher.expectValue(key, value), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun inPane(title: String) = hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

  private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

  // --- Markdown ----------------------------------------------------------------------------------------------------

  @Config(qualifiers = "+h560dp")
  @Test fun `Markdown, l'indice porta il reso al titolo e il testo va negli appunti`() {
    show("nota.md")
    assertBar("Reso", "Sorgente")
    val heading = "Il codice del rilievo"
    // Il titolo reso (non la riga del foglio): il suo blocco comincia col titolo.
    fun headingTop(): Float? = compose.onAllNodes(hasText(heading, substring = true) and !inPane("Indice")).fetchSemanticsNodes()
      .firstOrNull()?.box()?.top?.value
    val height = screen().let { it.bottom - it.top }.value
    val before = headingTop()
    assertTrue("all'inizio «$heading» è sotto la metà dello schermo ($before di $height)", before == null || before > height / 2)

    val rows = openMore()
    assertMore(setOf("Indice", "Copia il testo"), rows, bar = setOf("Reso", "Sorgente"))
    tap(rows.row("Indice"))
    // Il foglio dei titoli, rientrati per livello.
    val toc = compose.onAllNodes(hasClickAction() and inPane("Indice")).fetchSemanticsNodes().mapNotNull { it.label() }
    assertEquals(listOf("Quaderno di campo", "Le misure", "Da fare", heading), toc)
    tap(compose.onAllNodes(hasText(heading) and hasClickAction() and inPane("Indice")).fetchSemanticsNodes().single())
    advance(900)
    waitFor("il foglio si chiude") { compose.onAllNodes(inPane("Indice")).fetchSemanticsNodes().isEmpty() }
    val after = headingTop()
    assertTrue("«$heading» portato in vista, in alto ($after di $height)", after != null && after < height / 2)

    tap(openMore().row("Copia il testo"))
    val copied = clipboard()
    assertTrue("gli appunti hanno il Markdown: $copied", copied != null && copied.startsWith("# Quaderno di campo") &&
      copied.contains("## Il codice del rilievo") && copied.contains("<div>Questo HTML resta testo.</div>"))

    // Nella sorgente: «Trova | Reso» in barra, e «Indice» di «•••» torna al reso sul titolo scelto.
    tap(requireNotNull(bar("Sorgente")))
    assertBar("Trova", "Reso")
    val fromSource = openMore()
    assertMore(setOf("Indice", "Copia il testo"), fromSource, bar = setOf("Trova", "Reso"))
    tap(fromSource.row("Indice"))
    tap(compose.onAllNodes(hasText("Le misure") and hasClickAction() and inPane("Indice")).fetchSemanticsNodes().single())
    waitFor("il foglio si chiude") { compose.onAllNodes(inPane("Indice")).fetchSemanticsNodes().isEmpty() }
    assertBar("Reso", "Sorgente")
    assertTrue("di nuovo il reso, con «Le misure» in vista", shown("Le misure"))
  }

  @Test fun `Markdown, i titoli dell'indice saltano i blocchi di codice e tolgono i segni`() {
    val chunks = DataMarkdown.chunks("# Uno\n\ntesto\n\n```\n# non è un titolo\n```\n\n## Due con `codice` e **forza**\n\n    # rientrato\n\n### [Tre](https://example.com) ###\n")
    val headings = DataMarkdown.headings(chunks)
    assertEquals(listOf("Uno", "Due con codice e forza", "Tre"), headings.map { it.title })
    assertEquals(listOf(1, 2, 3), headings.map { it.level })
    assertTrue(headings.zipWithNext().all { (a, b) -> a.chunk < b.chunk })
  }

  // --- HTML --------------------------------------------------------------------------------------------------------

  @Test fun `HTML, la dimensione del testo arriva alla pagina e ricarica c'e' sul reso`() {
    show("pagina.html")
    assertBar("Reso", "Sorgente")
    assertTrue("di serie il testo è al 100%", hasValue(DataTextZoomKey, 100))
    val rows = openMore()
    assertMore(setOf("Dimensione del testo 100%", "Ricarica"), rows, bar = setOf("Reso", "Sorgente"))
    tap(rows.row("Dimensione del testo"))
    val steps = DataProfile.TextZoomSteps.map { menuRow("$it%") }
    assertEquals(listOf("85%", "100%", "115%", "125%", "150%"), steps.map { it.label() })
    assertTrue("la spunta sul passo di ora", SemanticsProperties.Selected in menuRow("100%").config)
    tap(menuRow("125%"))
    assertTrue("la pagina web ha il testo al 125%", hasValue(DataTextZoomKey, 125))
    val reopened = openMore()
    assertTrue("il menu dice il passo nuovo", labels(reopened).contains("Dimensione del testo 125%"))
    // Il menu aperto si chiude con la voce di ora; dalla sorgente la dimensione torna al reso, e «Ricarica» non c'è.
    tap(reopened.row("Ricarica"))
    tap(requireNotNull(bar("Sorgente")))
    // Nella sorgente il telaio mette «Trova»: resta il tasto per tornare al reso, nello stesso vetro (tre vetri, R5 B11).
    assertBar("Trova", "Reso")
    assertTrue("la metà scelta non è un tasto", bar("Sorgente") == null)
    val fromSource = openMore()
    assertEquals(setOf("Dimensione del testo 125%"), labels(fromSource))
    tap(fromSource.row("Dimensione del testo"))
    tap(menuRow("85%"))
    waitFor("di nuovo il reso, all'85%") { hasValue(DataTextZoomKey, 85) }
  }

  @Test fun `HTML con le immagini remote permesse, Carica immagini sta in tre puntini`() {
    FluidViewerRegistry.install(FluidDataFamily(FluidDataSettings(remoteImages = { FluidWebResponse.Block })))
    try {
      show("pagina.html")
      assertBar("Reso", "Sorgente")
      assertTrue(shown("2 immagini remote bloccate"))
      val rows = openMore()
      assertMore(setOf("Carica immagini", "Dimensione del testo 100%", "Ricarica"), rows, bar = setOf("Reso", "Sorgente"))
      tap(rows.row("Carica immagini"))
      waitFor("le immagini remote permesse") { !shown("immagini remote bloccate") }
      assertEquals(setOf("Dimensione del testo 100%", "Ricarica"), labels(openMore()))
    } finally {
      FluidViewerRegistry.install(FluidDataFamily())
    }
  }

  // --- EPUB --------------------------------------------------------------------------------------------------------

  @Test fun `EPUB, i capitoli vicini e la dimensione del testo stanno in tre puntini, Indice in barra`() {
    show("libro3.epub")
    assertBar("Indice")
    assertTrue(shown("Capitolo 1 di 3"))
    val rows = openMore()
    assertMore(setOf("Capitolo precedente", "Capitolo successivo", "Torna all'inizio", "Dimensione del testo 100%"), rows,
      bar = setOf("Indice"))
    assertTrue("al primo capitolo «Capitolo precedente» è spenta", SemanticsProperties.Disabled in rows.row("Capitolo precedente").config)
    assertTrue("al primo capitolo «Torna all'inizio» è spenta", SemanticsProperties.Disabled in rows.row("Torna all'inizio").config)
    tap(rows.row("Dimensione del testo"))
    tap(menuRow("125%"))
    assertTrue("il libro al 125%", hasValue(DataTextZoomKey, 125))
    assertEquals(24, DataProfile.bookFontSize(125))
    assertTrue("il foglio del lettore scrive 24 px", hasValue(DataReaderFontKey, 24))
    val css = DataWeb.readerCss(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Black,
      androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.Blue, false, 16, DataProfile.bookFontSize(125))
    assertTrue(css, css.contains("font-size: 24px !important; line-height"))

    tap(openMore().row("Capitolo successivo"))
    waitFor("il secondo capitolo") { shown("Capitolo 2 di 3") }
    tap(openMore().row("Capitolo successivo"))
    waitFor("il terzo capitolo") { shown("Capitolo 3 di 3") }
    val last = openMore()
    assertTrue("all'ultimo capitolo «Capitolo successivo» è spenta", SemanticsProperties.Disabled in last.row("Capitolo successivo").config)
    assertFalse("ora «Torna all'inizio» si sceglie", SemanticsProperties.Disabled in last.row("Torna all'inizio").config)
    tap(last.row("Torna all'inizio"))
    waitFor("di nuovo il primo capitolo") { shown("Capitolo 1 di 3") }
    assertBar("Indice")
  }

  // --- Mail --------------------------------------------------------------------------------------------------------

  @Test fun `Mail, tutte le intestazioni e il foglio degli allegati che apre l'allegato`() {
    show("messaggio.eml")
    assertBar()
    assertFalse(shown("X-Mailer: Posta di prova 1.0"))
    val rows = openMore()
    assertMore(setOf("Mostra tutte le intestazioni", "Allegati"), rows)
    tap(rows.row("Mostra tutte le intestazioni"))
    waitFor("le intestazioni tutte") { shown("X-Mailer: Posta di prova 1.0") }
    assertTrue("con le parole codificate lette", shown("Subject: Preventivo e riepilogo delle spese — novembre"))
    val back = openMore()
    assertEquals(setOf("Mostra meno intestazioni", "Allegati"), labels(back))
    tap(back.row("Mostra meno intestazioni"))
    waitFor("di nuovo poche intestazioni") { !shown("X-Mailer: Posta di prova 1.0") }

    tap(openMore().row("Allegati"))
    val sheet = compose.onAllNodes(hasClickAction() and inPane("Allegati")).fetchSemanticsNodes().mapNotNull { it.label() }
    assertEquals("il foglio ha i due allegati", listOf("preventivo.pdf", "riepilogo spese €.csv"), sheet)
    tap(compose.onAllNodes(hasText("riepilogo spese €.csv", substring = true) and hasClickAction() and inPane("Allegati"))
      .fetchSemanticsNodes().single())
    waitFor("l'allegato aperto col suo preset") { shown("Torna al messaggio") }
  }

  @Test fun `Mail con le immagini remote permesse, Carica immagini resta il tasto della barra`() {
    FluidViewerRegistry.install(FluidDataFamily(FluidDataSettings(remoteImages = { FluidWebResponse.Block })))
    try {
      show("messaggio.eml")
      assertBar("Carica immagini")
      assertMore(setOf("Mostra tutte le intestazioni", "Allegati"), openMore(), bar = setOf("Carica immagini"))
    } finally {
      FluidViewerRegistry.install(FluidDataFamily())
    }
  }

  // --- Calendario e contatti ---------------------------------------------------------------------------------------

  @Test fun `Calendario, gli eventi in testo negli appunti, e Aggiungi al calendario resta in barra`() {
    show("riunioni.ics", openWith = {})
    assertBar("Aggiungi al calendario")
    val rows = openMore()
    assertMore(setOf("Copia gli eventi"), rows, bar = setOf("Aggiungi al calendario"))
    tap(rows.row("Copia gli eventi"))
    val copied = requireNotNull(clipboard())
    for (part in listOf("Revisione dei visori", "Sala riunioni, primo piano", "Ferie al mare", "Tutto il giorno", "Natale")) {
      assertTrue("«$part» negli appunti: $copied", copied.contains(part))
    }
    assertEquals("un blocco per evento: $copied", 7, copied.split("\n\n").size)
  }

  @Test fun `Contatti, i contatti in testo negli appunti, e Aggiungi ai contatti resta in barra`() {
    show("contatti.vcf", openWith = {})
    assertBar("Aggiungi ai contatti")
    val rows = openMore()
    assertMore(setOf("Copia i contatti"), rows, bar = setOf("Aggiungi ai contatti"))
    tap(rows.row("Copia i contatti"))
    val copied = requireNotNull(clipboard())
    for (part in listOf("Marta Rossi", "Geologa · Studio Dune", "+39 333 1234567", "marta@example.com")) {
      assertTrue("«$part» negli appunti: $copied", copied.contains(part))
    }
  }

  private companion object {
    /** La fascia in fondo allo schermo dove sta la barra in basso (dp): la barra e il suo margine. A occhio. */
    const val BarZone = 140f
  }
}

/** Le stesse prove sul telefono stretto, a 360 dp. */
@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-mdpi")
class DataTypeActionsNarrowTest : DataTypeActionsTest()
