package dev.antigravity.fluidengine.viewer.graphics

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpRect
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerFileActions
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
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
 * F6C.20 · «•••» del provino di un carattere, aperto dal risolutore nel visore vero: «Testo di prova…» (il riquadro col
 * campo, e il testo che prende il posto della frase di prova a ogni misura), «Testo di serie» quando il testo è
 * cambiato, «Dimensione» con le misure e la spunta; nessuna voce col nome di una voce del menu del file.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-mdpi")
open class FontTypeActionsTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
  private var document: FluidViewerDocument? = null
  private var started = false
  private val fileMenu = FluidViewerStrings().run { setOf(share, openWith, copyFile, print, info, rename, move, duplicate, delete) }
  private val anyNode = SemanticsMatcher("ogni nodo") { true }

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    if (started) controller.pause().stop().destroy()
    document?.close()
  }

  @Test fun `il testo di prova e la misura cambiano il provino`() {
    show("studio.ttf")
    val sentence = FontFile(checkNotNull(javaClass.getResourceAsStream("/corpus/studio.ttf")).use { it.readBytes() })
      .faces.single().preview.sentence
    assertTrue("il carattere ha una frase di prova", sentence.isNotEmpty())
    assertEquals("di serie la frase a tre misure", 3, count(sentence))
    assertTrue(listOf("18 sp", "28 sp", "42 sp").all { count(it) == 1 })

    val rows = openMore()
    assertEquals(setOf("Testo di prova…", "Dimensione Tre misure"), labels(rows))
    for (item in rows.map { it.label()!! }) assertFalse("«$item» è nel menu del file", item in fileMenu)

    // «Testo di prova…»: il riquadro col campo; «Usa» mette il testo al posto della frase.
    tap(rows.row("Testo di prova…"))
    waitFor("il campo del testo") { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    compose.onNode(hasSetTextAction()).performTextInput("Zefiro danza sul vetro")
    advance(300)
    tap(compose.onAllNodesWithText("Usa").fetchSemanticsNodes().single { SemanticsActions.OnClick in it.config })
    waitFor("il testo di prova a tre misure") { count("Zefiro danza sul vetro") == 3 }
    assertEquals("la frase di serie se n'è andata", 0, count(sentence))

    // «Dimensione»: una misura sola, con la spunta.
    val more = openMore()
    assertEquals(setOf("Testo di prova…", "Testo di serie", "Dimensione Tre misure"), labels(more))
    tap(more.row("Dimensione"))
    for (size in SpecimenActions.Sizes) menuRow("$size sp")
    assertTrue("la spunta sulle tre misure", SemanticsProperties.Selected in menuRow("Tre misure").config)
    tap(menuRow("72 sp"))
    waitFor("la frase a 72 sp") { count("Zefiro danza sul vetro") == 1 && count("72 sp") == 1 && count("18 sp") == 0 }
    tap(openMore().row("Dimensione"))
    assertTrue("la spunta su 72 sp", SemanticsProperties.Selected in menuRow("72 sp").config)
    tap(menuRow("Tre misure"))
    waitFor("di nuovo tre misure") { count("Zefiro danza sul vetro") == 3 }

    // «Testo di serie» rimette la frase del carattere, e la voce sparisce.
    tap(openMore().row("Testo di serie"))
    waitFor("la frase di serie") { count(sentence) == 3 && count("Zefiro danza sul vetro") == 0 }
    assertEquals(setOf("Testo di prova…", "Dimensione Tre misure"), labels(openMore()))
  }

  // --- Il visore ---------------------------------------------------------------------------------------------------

  private fun show(name: String) {
    val context = RuntimeEnvironment.getApplication()
    val bytes = checkNotNull(FontTypeActionsTest::class.java.getResourceAsStream("/corpus/$name")).use { it.readBytes() }
    val file = File(context.cacheDir, name).apply { writeBytes(bytes) }
    val opened = runBlocking { FluidViewerRegistry.open(context, FluidViewerSource.of(file)) }
    document = opened
    assertEquals("graphics", opened.type.family)
    val actions = FluidViewerFileActions(share = {}, openWith = {}, copy = {}, print = {}, rename = {}, move = {},
      duplicate = {}, delete = {})
    started = true
    controller.setup().get().setContent {
      FluidTheme(EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false)) {
        FluidViewerInfoProvider({ FluidViewerInfo(opened.file.info) }) {
          // Il condividi dell'app in basso a destra, come in un'app vera: il terzo vetro della barra.
          FluidFileViewer(1, content = { opened.content }, onBack = {}, fileActions = { actions },
            toolbar = { button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar) })
        }
      }
    }
    advance(1200)
    waitFor("«•••» del carattere") { more() != null }
    advance(600)
    assertBar()
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
    assertTrue("non arriva: $what", condition())
  }

  private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

  private fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

  private fun SemanticsNode.label(): String? = config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

  private fun more(): SemanticsNode? = compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes()
    .singleOrNull { SemanticsActions.OnClick in it.config }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun opened(open: () -> Unit): List<SemanticsNode> {
    val before = compose.onAllNodes(anyNode).fetchSemanticsNodes().map { it.id }.toSet()
    open()
    return compose.onAllNodes(anyNode).fetchSemanticsNodes().filter {
      it.id !in before && it.text() != null && (SemanticsActions.OnClick in it.config || SemanticsProperties.Disabled in it.config)
    }
  }

  private fun openMore(): List<SemanticsNode> = opened { tap(requireNotNull(more()) { "niente «•••»" }) }

  private fun SemanticsNode.isRow() = text() != null &&
    (SemanticsActions.OnClick in config || SemanticsProperties.Disabled in config)

  /** La riga di un sottomenu aperto con l'etichetta [label]: il sottomenu prende il posto del menu nello stesso vetro. */
  private fun menuRow(label: String): SemanticsNode = compose.onAllNodes(anyNode).fetchSemanticsNodes()
    .filter { it.isRow() && it.label() == label }.let { rows ->
      assertTrue("la voce «$label»", rows.isNotEmpty())
      rows.last()
    }

  private fun SemanticsNode.box(): DpRect {
    val target = id
    return compose.onNode(SemanticsMatcher("nodo $target") { it.id == target }).getUnclippedBoundsInRoot()
  }

  /** «•••» e condividi in fondo, in vista e nello schermo, e un «•••» solo (un tasto nel trabocco ne farebbe un altro). */
  private fun assertBar() {
    val screen = compose.onRoot().getUnclippedBoundsInRoot()
    assertEquals(1, compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes().count { SemanticsActions.OnClick in it.config })
    for (label in listOf("Altro", "Condividi")) {
      val node = compose.onAllNodes(hasContentDescription(label) and hasClickAction()).fetchSemanticsNodes()
        .firstOrNull { it.box().top.value > screen.bottom.value - BarZone }
      assertTrue("«$label» non è nella barra a ${screen.right} dp", node != null)
      compose.onNode(SemanticsMatcher("«$label»") { it.id == node!!.id }).assertIsDisplayed()
      val box = node!!.box()
      assertTrue("«$label» esce dallo schermo: $box", box.left.value >= -0.5f && box.right.value <= screen.right.value + 0.5f)
    }
  }

  private companion object {
    /** La fascia in fondo allo schermo dove sta la barra in basso (dp). A occhio. */
    const val BarZone = 140f
  }

  private fun labels(nodes: List<SemanticsNode>) = nodes.map { it.text()!! }.toSet()

  private fun List<SemanticsNode>.row(label: String) = single { it.text()!!.startsWith(label) }
}

/** La stessa prova sul telefono stretto, a 360 dp. */
@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-mdpi")
class FontTypeActionsNarrowTest : FontTypeActionsTest()
