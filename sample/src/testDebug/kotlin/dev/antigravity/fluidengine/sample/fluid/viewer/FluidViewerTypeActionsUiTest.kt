package dev.antigravity.fluidengine.sample.fluid.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.viewer.VisoriFamiglia
import dev.antigravity.fluidengine.sample.viewer.VisoriFiles
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.data.fluidDataViewerInfo
import dev.antigravity.fluidengine.viewer.office.officeInfo
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import java.io.File
import java.text.NumberFormat
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
 * F6C.19, secondo giro (scelta di Alessio del 23 novembre): **«•••» c'è sempre** sulle pagine della tavola «D · Mix» e
 * tiene le azioni proprie del tipo, mai quelle del menu del nome. Per un PDF, le slide, un audio, un foglio, un JSON e
 * una pagina senza azioni del tipo: le voci, nessuna voce in comune col titolo, ogni voce che fa davvero la sua azione,
 * e la barra in basso («•••», l'azione del tipo, condividi) senza sovrapposizioni a 412 e 360 dp. Con
 * `FLUID_F6C19_EVIDENCE` scrive i fermi della pagina e di «•••» aperto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-xhdpi")
open class FluidViewerTypeActionsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  protected open val screen = 412

  enum class Kind { Pdf, Slides, Audio, Grid, Json, Plain, Sheet, Deck }

  private data class Scene(val kind: Kind, val serial: Int = 0)

  private var scene by mutableStateOf(Scene(Kind.Pdf))
  private var dark by mutableStateOf(false)
  private var diagnosticsWere = false
  private var pauses = 0
  private var speed by mutableFloatStateOf(1f)
  private val families = mutableMapOf<Kind, List<FluidViewerFile>>()
  private var opened by mutableStateOf<FluidViewerDocument?>(null)

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

  // --- Le scene ------------------------------------------------------------------------------------------------------

  private val session = object : FluidMediaSession, FluidAudioSpeed {
    override val playing = true
    override val positionMillis = 84_000L
    override val durationMillis = 222_000L
    override val canSeek = true
    override fun play() = Unit
    override fun pause() { pauses++ }
    override fun seekTo(positionMillis: Long) = Unit
    override val playbackSpeed: Float get() = speed
    override val canChangePlaybackSpeed = true
    override fun changePlaybackSpeed(speed: Float) { this@FluidViewerTypeActionsUiTest.speed = speed }
  }

  /** Le pagine di prova, con le note del relatore della prima slide. */
  private class Deck(private val cover: androidx.compose.ui.graphics.ImageBitmap, override val count: Int, slides: Boolean) :
    FluidPageSource, FluidPageNotes {
    private val size = if (slides) IntSize(960, 540) else IntSize(330, 467)
    override suspend fun size(index: Int) = size
    override suspend fun render(index: Int, target: IntSize) = cover
    override fun notes(index: Int): String? = if (index == 0) "Benvenuti: tre rotte, una per settimana." else null
    override val hasNotes: Boolean = slides
  }

  /** Un foglio lungo: 200 righe per 12 colonne, con il testo della cella. */
  private object LongSheet : FluidGridSource {
    override val sheets = listOf(FluidGridSheet("Movimenti", rowCount = 200, columnCount = 12))
    override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange) =
      rows.flatMap { r -> columns.map { c -> FluidGridCell(r, c, "${'A' + c}${r + 1}") } }
  }

  private fun fileName(kind: Kind) = when (kind) {
    Kind.Pdf -> "Quaderno di campo.pdf"
    Kind.Slides -> "Rotte di novembre.pptx"
    Kind.Audio -> "Lago all’alba.m4a"
    Kind.Grid -> "Movimenti.xlsx"
    Kind.Json -> "config.json"
    Kind.Plain -> "Appunti.note"
    Kind.Sheet -> "Bilancio 2026.xlsx"
    Kind.Deck -> "Rotte di novembre.pptx"
  }

  private fun familyFile(kind: Kind): Pair<VisoriFamiglia, String>? = when (kind) {
    Kind.Json -> VisoriFamiglia.Dati to "dati-json"
    Kind.Sheet -> VisoriFamiglia.Office to "office-xlsx"
    Kind.Deck -> VisoriFamiglia.Office to "office-pptx"
    else -> null
  }

  private fun setUp(first: Scene) {
    for (kind in Kind.entries) {
      val (family, id) = familyFile(kind) ?: continue
      val sample = family.files.single { it.id == id }
      families[kind] = runBlocking { VisoriFiles.prepare(compose.activity, listOf(sample), family.scene) }
    }
    scene = first
    val cover = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.viewer_notes_cover).asImageBitmap()
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        key(scene) { Page(scene.kind, cover) }
      }
    }
    advance(1200)
    waitFor("la pagina di ${first.kind}") { ready(first.kind) }
    advance(600)
  }

  @Composable
  private fun Page(kind: Kind, cover: androidx.compose.ui.graphics.ImageBitmap) {
    val actions: @Composable (Int) -> FluidViewerFileActions? = {
      FluidViewerFileActions(share = {}, openWith = {}, copy = {}, rename = {}, delete = {})
    }
    val toolbar: dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope.(Int) -> Unit = {
      button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar)
    }
    val family = families[kind]
    if (family != null) {
      val reader = rememberFluidViewerFiles(family)
      val document = reader.document(0)
      SideEffect { opened = document?.takeIf { it.failure == null } }
      val info = FluidViewerInfo(family.single().info)
      FluidViewerInfoProvider({ document?.let { if (kind == Kind.Json) fluidDataViewerInfo(it) else it.officeInfo() } ?: info }) {
        FluidFileViewer(1, content = { reader.content(0) }, toolbar = toolbar, modifier = Modifier.fillMaxSize(),
          fileActions = actions)
      }
      return
    }
    val file = FluidFileInfo(fileName(kind))
    val content: FluidViewerContent = when (kind) {
      Kind.Pdf -> FluidViewerContent.Pages(Deck(cover, 6, slides = false))
      Kind.Slides -> FluidViewerContent.Pages(Deck(cover, 4, slides = true))
      Kind.Audio -> FluidViewerContent.Media(session)
      Kind.Grid -> FluidViewerContent.Grid(LongSheet)
      else -> FluidViewerContent.Custom { if (chromeVisible) Text("Pagina con i controlli in vista") }
    }
    val info = when (kind) {
      Kind.Pdf -> FluidViewerInfo(file, title = "Quaderno di campo", thumbnail = cover, searchPages = { emptyList() })
      Kind.Audio -> FluidViewerInfo(file, title = "Lago all’alba", subtitle = "Pampa · Note di campo")
      else -> FluidViewerInfo(file)
    }
    FluidViewerInfoProvider({ info }) {
      FluidFileViewer(1, content = { content }, toolbar = toolbar, modifier = Modifier.fillMaxSize(), fileActions = actions)
    }
  }

  private fun ready(kind: Kind): Boolean = when (kind) {
    Kind.Json, Kind.Sheet, Kind.Deck -> opened != null && moreButton() != null
    else -> moreButton() != null
  }

  private fun switchTo(kind: Kind) {
    compose.runOnUiThread {
      opened = null
      scene = Scene(kind, scene.serial + 1)
    }
    Snapshot.sendApplyNotifications()
    advance(1200)
    waitFor("la pagina di $kind") { ready(kind) }
    // Una famiglia mostra la pagina vuota finché il file si apre: le sue azioni arrivano un passo dopo.
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
    assertTrue("non arriva: $what", condition())
  }

  // --- Lettura dell'albero -------------------------------------------------------------------------------------------

  private fun SemanticsNode.dp(): Rect {
    val id = this.id
    val box = compose.onNode(SemanticsMatcher("nodo $id") { it.id == id }, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return Rect(box.left.value, box.top.value, box.right.value, box.bottom.value)
  }

  private fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

  private fun moreButton(): SemanticsNode? = compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes()
    .singleOrNull { SemanticsActions.OnClick in it.config }

  private fun button(description: String): SemanticsNode = compose.onAllNodesWithContentDescription(description)
    .fetchSemanticsNodes().single { SemanticsActions.OnClick in it.config }

  private fun titleNode(text: String): SemanticsNode = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    .single { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.DropdownList }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  /** Le righe che compaiono dopo [open]: le voci del menu che si è aperto. */
  private fun opened(open: () -> Unit): List<SemanticsNode> {
    val before = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id }.toSet()
    open()
    return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.id !in before && it.text() != null }
  }

  private fun labels(nodes: List<SemanticsNode>) = nodes.map { it.text()!! }.toSet()

  /** La riga del menu che comincia con [label] (una voce col sottotitolo ha anche quello nel testo). */
  private fun List<SemanticsNode>.row(label: String) = single { it.text()!!.startsWith(label) }

  private fun rate(value: Float) = NumberFormat.getNumberInstance().format(value) + "×"

  private fun expectedMore(kind: Kind): Set<String> = when (kind) {
    Kind.Pdf -> setOf("Vai a pagina…", "Miniature")
    Kind.Slides, Kind.Deck -> setOf("Vai alla slide…", "Miniature", "Note del relatore")
    Kind.Audio -> setOf("Velocità ${rate(1f)}", "Timer di spegnimento Spento")
    Kind.Grid, Kind.Sheet -> setOf("Vai alla cella…")
    Kind.Json -> setOf("Espandi tutto", "Comprimi tutto")
    Kind.Plain -> setOf("Nascondi controlli")
  }

  // --- Le prove ------------------------------------------------------------------------------------------------------

  @Test fun `ogni pagina ha i tre puntini con le azioni del suo tipo, e nessuna e' anche nel titolo`() {
    setUp(Scene(Kind.Pdf))
    for (kind in Kind.entries) {
      if (kind != Kind.Pdf) switchTo(kind)
      val more = labels(opened { tap(requireNotNull(moreButton()) { "$kind: niente «•••»" }) })
      assertEquals("$kind: le voci di «•••»", expectedMore(kind), more)
      switchTo(kind)
      val title = labels(opened { tap(titleNode(fileName(kind).substringBeforeLast('.'))) })
      assertTrue("$kind: il menu del nome ha le voci del file ($title)", "Informazioni" in title)
      // Una voce di «•••» col sottotitolo («Velocità 1×») ha anche quello nel testo della riga.
      assertTrue("$kind: nessuna voce doppia: $title / $more",
        more.none { item -> title.any { item == it || item.startsWith("$it ") } })
    }
  }

  @Test fun `Vai a pagina porta alla pagina, e le note del relatore sono quelle della slide`() {
    setUp(Scene(Kind.Pdf))
    val pill = { compose.onAllNodesWithContentDescription("Miniature delle pagine").fetchSemanticsNodes().single()
      .config[SemanticsProperties.StateDescription] }
    assertEquals("Pagina 1 di 6", pill())
    tap(opened { tap(moreButton()!!) }.row("Vai a pagina…"))
    compose.onNode(hasSetTextAction()).performTextInput("5")
    advance(300)
    tap(compose.onAllNodesWithText("Vai").fetchSemanticsNodes().single { SemanticsActions.OnClick in it.config })
    advance(600)
    assertEquals("Pagina 5 di 6", pill())

    switchTo(Kind.Slides)
    tap(opened { tap(moreButton()!!) }.row("Note del relatore"))
    advance(600)
    assertTrue("le note della prima slide",
      compose.onAllNodesWithText("Benvenuti: tre rotte, una per settimana.").fetchSemanticsNodes().isNotEmpty())
  }

  @Test fun `la velocita' cambia il player, e il timer lo mette in pausa`() {
    setUp(Scene(Kind.Audio))
    val speeds = opened { tap(opened { tap(moreButton()!!) }.row("Velocità")) }
    assertEquals(listOf(.75f, 1f, 1.25f, 1.5f, 2f).map(::rate).toSet(), labels(speeds).filter { it.endsWith("×") }.toSet())
    tap(speeds.single { it.text() == rate(1.5f) })
    assertEquals(1.5f, speed)

    val timer = opened { tap(opened { tap(moreButton()!!) }.row("Timer di spegnimento")) }
    assertTrue(labels(timer).containsAll(setOf("Spento", "5 minuti", "15 minuti", "30 minuti", "1 ora", "Fine del brano")))
    tap(timer.single { it.text() == "5 minuti" })
    assertEquals(0, pauses)
    compose.mainClock.advanceTimeBy(5 * 60_000L + 1_000L)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5).plusSeconds(1))
    advance(100)
    assertEquals("il timer ha messo in pausa", 1, pauses)
    assertTrue("e si è spento", labels(opened { tap(moreButton()!!) }).contains("Timer di spegnimento Spento"))
  }

  @Test fun `Vai alla cella porta la riga in vista`() {
    setUp(Scene(Kind.Grid))
    fun shown(row: Int) = compose.onAllNodes(hasContentDescription("Riga $row", substring = true)).fetchSemanticsNodes()
      .any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == "Riga $row" || d.startsWith("Riga $row:") } == true }
    assertFalse("la riga 150 non si vede all'inizio", shown(150))
    tap(opened { tap(moreButton()!!) }.row("Vai alla cella…"))
    compose.onNode(hasSetTextAction()).performTextInput("C150")
    advance(300)
    tap(compose.onAllNodesWithText("Vai").fetchSemanticsNodes().single { SemanticsActions.OnClick in it.config })
    advance(600)
    assertTrue("la riga 150 in vista", shown(150))
  }

  @Test fun `Espandi tutto e Comprimi tutto aprono e chiudono l'albero`() {
    setUp(Scene(Kind.Json))
    fun strings() = compose.onAllNodes(hasContentDescription(", stringa, ", substring = true), useUnmergedTree = true)
      .fetchSemanticsNodes().size
    val initial = strings()
    assertTrue("l'albero di config.json ha delle stringhe in vista ($initial)", initial > 0)
    fun nested() = compose.onAllNodes(hasContentDescription("nome, stringa, engine-viewer-office"), useUnmergedTree = true)
      .fetchSemanticsNodes().size
    assertEquals("all'inizio gli oggetti dei moduli sono chiusi", 0, nested())
    tap(opened { tap(moreButton()!!) }.row("Comprimi tutto"))
    advance(300)
    // Resta aperta la radice, come negli ispettori: le chiavi di primo livello, con le loro due stringhe.
    assertEquals("chiuso: le stringhe della radice («nome», «versione»)", 2, strings())
    assertEquals("chiuso: i moduli non si vedono", 0, nested())
    tap(opened { tap(moreButton()!!) }.row("Espandi tutto"))
    advance(300)
    assertTrue("aperto tutto: più stringhe di prima (${strings()} > $initial)", strings() > initial)
    assertEquals("aperto tutto: il nome del primo modulo", 1, nested())
  }

  @Test fun `una pagina senza azioni del tipo ha Nascondi controlli`() {
    setUp(Scene(Kind.Plain))
    assertTrue(compose.onAllNodesWithText("Pagina con i controlli in vista").fetchSemanticsNodes().isNotEmpty())
    tap(opened { tap(moreButton()!!) }.row("Nascondi controlli"))
    advance(600)
    assertTrue("i controlli si nascondono",
      compose.onAllNodesWithText("Pagina con i controlli in vista").fetchSemanticsNodes().isEmpty())
  }

  @Test fun `i tre puntini, l'azione del tipo e condividi stanno in fila, nello schermo`() {
    setUp(Scene(Kind.Pdf))
    for (kind in listOf(Kind.Pdf, Kind.Slides, Kind.Audio, Kind.Grid, Kind.Json, Kind.Sheet, Kind.Deck)) {
      if (kind != Kind.Pdf) switchTo(kind)
      val screenRect = compose.onRoot().fetchSemanticsNode().dp()
      val bottom = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.dp() to it }
        .filter { (box, _) -> box.top > screenRect.height - 100f && box.width < screenRect.width * 0.8f }
        .sortedBy { it.first.left }
      val names = bottom.map { (_, n) -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: n.text() }
      assertTrue("$kind a $screen dp: «•••» a sinistra ($names)", names.firstOrNull() == "Altro")
      assertTrue("$kind a $screen dp: condividi a destra ($names)", names.lastOrNull() == "Condividi")
      for ((box, node) in bottom) assertTrue("$kind: ${node.text()} fuori dallo schermo ($box)",
        box.left >= -0.5f && box.right <= screenRect.right + 0.5f)
      for (i in bottom.indices) for (j in i + 1 until bottom.size) {
        val overlap = bottom[i].first.intersect(bottom[j].first)
        assertTrue("$kind a $screen dp: si toccano ${names[i]} e ${names[j]}", overlap.width <= 2.5f || overlap.height <= 0.5f)
      }
    }
  }

  // --- I fermi per Alessio -------------------------------------------------------------------------------------------

  @Test fun `fermi della pagina e dei tre puntini aperti, in chiaro e in scuro`() {
    val folder = System.getenv("FLUID_F6C19_EVIDENCE")?.let { File(it, "v2") } ?: return
    if (screen != 412) return
    setUp(Scene(Kind.Pdf))
    for ((theme, night) in listOf("chiaro" to false, "scuro" to true)) {
      for ((kind, name) in listOf(Kind.Pdf to "pdf", Kind.Audio to "audio", Kind.Sheet to "foglio", Kind.Deck to "slide",
        Kind.Json to "json")) {
        compose.runOnUiThread { dark = night }
        switchTo(kind)
        advance(600)
        capture(File(folder, "$theme/$name-pagina.png"))
        opened { tap(moreButton()!!) }
        advance(600)
        capture(File(folder, "$theme/$name-altro.png"))
      }
    }
  }

  private fun capture(target: File) {
    target.parentFile?.mkdirs()
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
      var result = PixelCopy.ERROR_UNKNOWN
      compose.runOnUiThread {
        PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
          { result = it }, Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PixelCopy.SUCCESS, result)
      target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
      bitmap.recycle()
    }
  }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-xhdpi")
class FluidViewerTypeActionsNarrowUiTest : FluidViewerTypeActionsUiTest() {
  override val screen = 360
}
