package dev.antigravity.fluidengine.sample.fluid.viewer

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.viewer.VisoriFamiglia
import dev.antigravity.fluidengine.sample.viewer.VisoriFiles
import dev.antigravity.fluidengine.sample.viewer.VisoriWarmPage
import dev.antigravity.fluidengine.sample.viewer.prepareVisoriFirstContent
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.office.officeInfo
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import java.time.Duration
import kotlinx.coroutines.runBlocking
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
 * F6C.20 · Due voci delle riprese del 23 novembre 2026, nell'albero vero.
 *
 * - **Le note del relatore** di «Rotte di novembre.pptx» (la prima slide ha `notesSlide1.xml`). Sul dispositivo la
 *   galleria apre le slide con la prima pagina già resa ([prepareVisoriFirstContent], [VisoriWarmPage]) e l'involucro
 *   perdeva `FluidPageNotes`: «•••» non aveva «Note del relatore». Qui la stessa strada della galleria, col file vero
 *   aperto dal modulo Office: la voce c'è e mostra la nota.
 * - **«•••» del testo e del codice** (scelta di Alessio del 23 novembre 2026): «Vai alla riga…», «Numeri di riga»,
 *   «Copia il testo», che fanno davvero la loro azione, senza voci del menu del nome.
 *
 * A 412 dp, e nelle sottoclassi a 360 e alla larghezza del Tab S9 in orizzontale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-xhdpi")
open class FluidViewerNotesAndTextUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var dark by mutableStateOf(false)
  private var diagnosticsWere = false
  private var warm by mutableStateOf<VisoriWarmPage?>(null)

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private val fileActions: @Composable (Int) -> FluidViewerFileActions? = {
    FluidViewerFileActions(share = {}, openWith = {}, copy = {}, rename = {}, delete = {})
  }

  /** Le voci del menu del nome: nessuna voce di «•••» può essere una di queste. */
  private val fileMenu = setOf("Rinomina", "Condividi", "Apri con…", "Copia", "Stampa", "Informazioni", "Elimina")

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
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

  private fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

  private fun moreButton(): SemanticsNode? = compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes()
    .singleOrNull { SemanticsActions.OnClick in it.config }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun opened(open: () -> Unit): List<SemanticsNode> {
    val before = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id }.toSet()
    open()
    return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.id !in before && it.text() != null }
  }

  private fun List<SemanticsNode>.row(label: String) = single { it.text()!!.startsWith(label) }

  private fun shown(text: String, substring: Boolean = true) =
    compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  // --- Le note del relatore, con la pagina pronta della galleria ------------------------------------------------------

  @Test fun leNoteDelRelatoreArrivanoAncheConLaPrimaPaginaGiaResa() {
    val sample = VisoriFamiglia.Office.files.single { it.id == "office-pptx" }
    val files: List<FluidViewerFile> = runBlocking { VisoriFiles.prepare(compose.activity, listOf(sample), VisoriFamiglia.Office.scene) }
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val reader = rememberFluidViewerFiles(files)
        // La strada della galleria sul dispositivo: la prima pagina resa prima del viaggio, poi il visore la usa.
        LaunchedEffect(reader) { warm = prepareVisoriFirstContent(reader, 0, 1080) }
        val page = warm
        if (page != null) {
          val document = reader.document(0)
          FluidViewerInfoProvider({ document?.officeInfo() ?: FluidViewerInfo(files.single().info) }) {
            FluidFileViewer(1, content = { page.content }, modifier = Modifier.fillMaxSize(), fileActions = fileActions)
          }
        }
      }
    }
    advance(1200)
    waitFor("la pagina pronta delle slide") { warm != null && moreButton() != null }
    advance(600)
    // L'involucro porta la capacità della sorgente.
    val source = warm!!.content.source
    assertTrue("la pagina pronta ha perso FluidPageNotes: ${source::class}", source is FluidPageNotes)
    assertTrue((source as FluidPageNotes).hasNotes)
    for (theme in listOf(false, true)) {
      compose.runOnUiThread { dark = theme }
      advance(900)
      val items = opened { tap(moreButton()!!) }
      val labels = items.map { it.text()!! }.toSet()
      assertTrue("dark=$theme: $labels", "Note del relatore" in labels)
      assertTrue("dark=$theme, nessuna voce del nome: $labels", labels.none { it in fileMenu })
      tap(items.row("Note del relatore"))
      waitFor("la nota della prima slide") { shown("verificare il livello prima della partenza") }
      compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
      advance(900)
    }
  }

  // --- «•••» del testo e del codice -----------------------------------------------------------------------------------

  private class Lines(val total: Int) : FluidTextSource {
    override val language: String = "Kotlin"
    override val lineCount: Int = total
    override suspend fun readLines(start: Int, count: Int): List<String> =
      (start until minOf(total, start + count)).map { "val riga${it + 1} = ${it + 1}" }
  }

  @Test fun ilTestoEIlCodiceHannoLeLoroVociInAltro() {
    val info = FluidViewerInfo(FluidFileInfo("Rotte.kt", sizeBytes = 9_000))
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        FluidViewerInfoProvider({ info }) {
          FluidFileViewer(1, content = { FluidViewerContent.Text(Lines(400)) }, modifier = Modifier.fillMaxSize(),
            fileActions = fileActions)
        }
      }
    }
    advance(1500)
    waitFor("la pagina del codice") { moreButton() != null && shown("val riga1 = 1") }
    // Le capsule di lettura restano al centro, «•••» a sinistra.
    val more = moreButton()!!.boundsInRoot
    val size = compose.onAllNodesWithContentDescription("Dimensione del testo").fetchSemanticsNodes().single().boundsInRoot
    assertTrue("«•••» $more a sinistra di «Aa» $size", more.right <= size.left)

    var items = opened { tap(moreButton()!!) }
    val labels = items.map { it.text()!! }.toSet()
    assertEquals(setOf("Vai alla riga…", "Numeri di riga", "Copia il testo"), labels)
    assertTrue(labels.none { it in fileMenu })
    assertTrue("i numeri di riga sono accesi di serie",
      items.row("Numeri di riga").config.getOrNull(SemanticsProperties.Selected) == true)

    // «Copia il testo»: tutto il file negli appunti.
    tap(items.row("Copia il testo"))
    advance(900)
    val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    assertEquals(400, copied.lines().size)
    assertTrue(copied.startsWith("val riga1 = 1") && copied.endsWith("val riga400 = 400"))

    // «Numeri di riga»: si spengono, e la spunta lo dice (i numeri non sono nell'albero: TalkBack legge le righe).
    items = opened { tap(moreButton()!!) }
    tap(items.row("Numeri di riga"))
    advance(600)
    items = opened { tap(moreButton()!!) }
    val numbers = items.row("Numeri di riga")
    assertFalse("i numeri di riga sono spenti", numbers.config.getOrNull(SemanticsProperties.Selected) == true)
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    advance(900)

    // «Vai alla riga…»: 250 porta in vista la riga 250.
    assertFalse(shown("val riga250 = 250"))
    items = opened { tap(moreButton()!!) }
    tap(items.row("Vai alla riga…"))
    compose.onNode(hasSetTextAction()).performTextInput("250")
    advance(300)
    val go = compose.onAllNodesWithText("Vai").fetchSemanticsNodes().first { SemanticsActions.OnClick in it.config }
    tap(go)
    waitFor("la riga 250 in vista") { shown("val riga250 = 250") }
  }
}

@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-xhdpi")
class FluidViewerNotesAndTextNarrowUiTest : FluidViewerNotesAndTextUiTest()

/** Il Tab S9 in orizzontale: 1205 × 753 dp. */
@Config(sdk = [35], qualifiers = "it-rIT-w1205dp-h753dp-land-xhdpi")
class FluidViewerNotesAndTextTabletUiTest : FluidViewerNotesAndTextUiTest()
