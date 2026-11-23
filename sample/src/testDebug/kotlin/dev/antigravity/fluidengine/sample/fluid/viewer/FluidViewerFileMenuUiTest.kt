package dev.antigravity.fluidengine.sample.fluid.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.icons.Download
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
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
 * F6C.19 nell'albero vero: in cima al visore il nome del file **senza estensione con «⌄»** è un elenco a discesa che
 * apre il menu del file (il titolo selettore di F6B.11). Per un PDF, un audio, un archivio e uno sconosciuto: le voci
 * giuste, quelle dell'app solo se date, nessuna voce doppia fra il titolo e «•••», «n di N» sotto il nome, e nessun
 * bersaglio della barra sopra un altro, a 412 e a 360 dp. Con `FLUID_F6C19_EVIDENCE` scrive i fermi per Alessio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-xhdpi")
open class FluidViewerFileMenuUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  protected open val screen = 412

  enum class Kind { Pdf, Audio, Archive, Unknown }

  private data class Scene(val kind: Kind, val app: Boolean = false, val name: String? = null, val serial: Int = 0)

  private var scene by mutableStateOf(Scene(Kind.Pdf))
  private var dark by mutableStateOf(false)
  private val calls = mutableListOf<String>()
  private var diagnosticsWere = false

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

  // --- La scena ------------------------------------------------------------------------------------------------------

  private val session = object : FluidMediaSession {
    override val playing = false
    override val positionMillis = 84_000L
    override val durationMillis = 222_000L
    override val canSeek = true
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMillis: Long) = Unit
  }

  private fun fileName(kind: Kind, custom: String?) = custom ?: when (kind) {
    Kind.Pdf -> "Quaderno di campo.pdf"
    Kind.Audio -> "Lago all’alba.m4a"
    Kind.Archive -> "Progetto.zip"
    Kind.Unknown -> "Scena.blend"
  }

  private fun setUp(first: Scene) {
    scene = first
    val cover = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.viewer_notes_cover).asImageBitmap()
    val pages = FluidViewerContent.Pages(object : FluidPageSource {
      override val count = 6
      override suspend fun size(index: Int) = IntSize(330, 467)
      override suspend fun render(index: Int, target: IntSize) = cover
    })
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        key(scene) {
          val current = scene
          val name = fileName(current.kind, current.name)
          val file = FluidFileInfo(name, when (current.kind) {
            Kind.Pdf -> "application/pdf"
            Kind.Audio -> "audio/mp4"
            Kind.Archive -> "application/zip"
            Kind.Unknown -> null
          }, 2_400_000)
          val content: FluidViewerContent = when (current.kind) {
            Kind.Pdf -> pages
            Kind.Audio -> FluidViewerContent.Media(session)
            Kind.Archive -> FluidViewerContent.Custom(actions = {
              // Come il «•••» di engine-viewer-archive: le azioni del tipo, più un «Condividi» che il titolo ha già.
              action("archivio.altro", FluidIcons.MoreHoriz, "Altro", placement = FluidToolbarPlacement.BottomBar) {
                listOf(FluidContextAction("Ordina") { calls += "ordina" }, FluidContextAction("Filtra") {},
                  FluidContextAction("Condividi") { calls += "condividi-archivio" })
              }
              button("archivio.estrai", FluidIcons.Download, "Estrai tutto", {}, placement = FluidToolbarPlacement.BottomBar)
            }) { Box(Modifier.fillMaxSize()) }
            Kind.Unknown -> FluidViewerContent.Unknown(file, openWith = {}, share = {})
          }
          val info = when (current.kind) {
            Kind.Pdf -> FluidViewerInfo(file, title = "Quaderno di campo", thumbnail = cover,
              facts = listOf("6 pagine", "A4"), searchPages = { emptyList() })
            // Un'app di prima di F6C.19: dà solo le azioni del pannello info, niente fileActions.
            Kind.Audio -> FluidViewerInfo(file, title = "Lago all’alba", subtitle = "Pampa · Note di campo",
              actions = listOf(FluidViewerInfoAction("Apri con…") { calls += "apri-legacy" }))
            Kind.Archive -> FluidViewerInfo(file, actions = listOf(FluidViewerInfoAction("Apri con…") {},
              FluidViewerInfoAction("Condividi") {}, FluidViewerInfoAction("Estrai tutto") {}))
            Kind.Unknown -> FluidViewerInfo(file, facts = listOf("BLEND"))
          }
          val count = if (current.kind == Kind.Pdf) 10 else 1
          FluidViewerInfoProvider({ info }) {
            FluidFileViewer(count, initial = if (current.kind == Kind.Pdf) 5 else 0, content = { content },
              toolbar = {
                if (current.kind != Kind.Unknown) button("share", FluidIcons.IosShare, "Condividi", { calls += "share-barra" },
                  placement = FluidToolbarPlacement.BottomBar)
              },
              modifier = Modifier.fillMaxSize(),
              fileActions = {
                if (current.kind == Kind.Audio) null
                else FluidViewerFileActions(share = { calls += "condividi" }, openWith = { calls += "apri" },
                  copy = { calls += "copia" }, rename = if (current.app) ({ calls += "rinomina" }) else null,
                  delete = if (current.app) ({ calls += "elimina" }) else null)
              })
          }
        }
      }
    }
    advance(1200)
  }

  private fun switchTo(next: Scene) {
    compose.runOnUiThread { scene = next.copy(serial = scene.serial + 1) }
    Snapshot.sendApplyNotifications()
    advance(1200)
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  // --- Lettura dell'albero -------------------------------------------------------------------------------------------

  private fun SemanticsNode.dp(): Rect {
    val id = this.id
    val box = compose.onNode(SemanticsMatcher("nodo $id") { it.id == id }, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return Rect(box.left.value, box.top.value, box.right.value, box.bottom.value)
  }

  private fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

  private fun titleNode(text: String): SemanticsNode = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    .single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }

  private fun button(description: String): SemanticsNode = compose.onAllNodesWithContentDescription(description)
    .fetchSemanticsNodes().single { SemanticsActions.OnClick in it.config }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  /** Le voci che compaiono dopo [open], dall'alto in basso: le righe del menu che si è aperto. */
  private fun opened(open: () -> Unit): List<SemanticsNode> {
    val before = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id }.toSet()
    open()
    return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
      .filter { it.id !in before && it.text() != null }
      .sortedBy { it.dp().top }
  }

  private fun screenDp(): Rect = compose.onRoot().fetchSemanticsNode().dp()

  private fun expectedTitle(kind: Kind, custom: String? = null) =
    fileName(kind, custom).substringBeforeLast('.')

  private fun expectedMenu(kind: Kind, app: Boolean) = when (kind) {
    Kind.Pdf -> listOfNotNull("Rinomina".takeIf { app }, "Condividi", "Apri con…", "Copia", "Stampa", "Informazioni",
      "Elimina".takeIf { app })
    Kind.Audio -> listOf("Apri con…", "Informazioni")
    Kind.Archive, Kind.Unknown -> listOfNotNull("Rinomina".takeIf { app }, "Condividi", "Apri con…", "Copia",
      "Informazioni", "Elimina".takeIf { app })
  }

  // --- Le prove ------------------------------------------------------------------------------------------------------

  @Test fun `il titolo e' un elenco a discesa col nome senza estensione, e apre le voci giuste`() {
    setUp(Scene(Kind.Pdf))
    for (kind in Kind.entries) {
      if (kind != Kind.Pdf) switchTo(Scene(kind))
      val name = expectedTitle(kind)
      val title = titleNode(name)
      assertEquals("$kind: il ruolo", Role.DropdownList, title.config.getOrNull(SemanticsProperties.Role))
      assertEquals("$kind: che cosa fa", "Mostra le azioni del file", title.config[SemanticsActions.OnClick].label)
      assertTrue("$kind: niente estensione nel titolo",
        compose.onAllNodesWithText(fileName(kind, null)).fetchSemanticsNodes()
          .none { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList })
      val box = title.dp()
      assertTrue("$kind: il bersaglio del titolo ${box.width} × ${box.height}", box.height >= 47.9f && box.width >= 47.9f)
      val items = opened { tap(title) }
      assertEquals("$kind: le voci del menu del file", expectedMenu(kind, app = false), items.map { it.text() })
      val screen = screenDp()
      for (item in items) {
        val row = item.dp()
        assertTrue("$kind: ${item.text()} nello schermo ($row)", row.left >= -0.5f && row.right <= screen.right + 0.5f)
        assertTrue("$kind: ${item.text()} sotto la barra ($row, titolo $box)", row.top >= box.top)
      }
    }
  }

  @Test fun `le voci dell'app compaiono solo se l'app le da'`() {
    setUp(Scene(Kind.Pdf, app = true))
    val withApp = opened { tap(titleNode("Quaderno di campo")) }.map { it.text() }
    assertEquals(expectedMenu(Kind.Pdf, app = true), withApp)
    switchTo(Scene(Kind.Pdf, app = false))
    val without = opened { tap(titleNode("Quaderno di campo")) }.map { it.text() }
    assertFalse(without.contains("Rinomina") || without.contains("Elimina"))
    switchTo(Scene(Kind.Unknown, app = true))
    assertEquals(expectedMenu(Kind.Unknown, app = true), opened { tap(titleNode("Scena")) }.map { it.text() })
  }

  @Test fun `una voce fa la sua azione, e Informazioni apre il pannello`() {
    setUp(Scene(Kind.Pdf, app = true))
    tap(opened { tap(titleNode("Quaderno di campo")) }.single { it.text() == "Rinomina" })
    assertEquals(listOf("rinomina"), calls)
    tap(opened { tap(titleNode("Quaderno di campo")) }.single { it.text() == "Informazioni" })
    advance(600)
    assertTrue("il pannello info", compose.onAllNodesWithText("6 pagine").fetchSemanticsNodes().isNotEmpty())
    // L'app di prima, con le sole azioni del pannello: la voce del titolo è la sua.
    switchTo(Scene(Kind.Audio))
    calls.clear()
    tap(opened { tap(titleNode("Lago all’alba")) }.single { it.text() == "Apri con…" })
    assertEquals(listOf("apri-legacy"), calls)
  }

  @Test fun `nessuna voce e' doppia fra il titolo e i tre puntini`() {
    setUp(Scene(Kind.Archive))
    val title = opened { tap(titleNode("Progetto")) }.map { it.text()!! }
    switchTo(Scene(Kind.Archive))
    val more = opened { tap(button("Altro")) }.map { it.text()!! }
    // Un menu che sale da un tasto in basso mette la prima voce vicino al dito: l'ordine si legge al contrario.
    assertEquals("«•••» tiene le azioni del tipo", setOf("Ordina", "Filtra"), more.toSet())
    assertEquals(2, more.size)
    assertTrue("nessuna voce doppia: $title / $more", title.intersect(more.toSet()).isEmpty())
    assertFalse("«Estrai tutto» resta nella barra in basso", title.contains("Estrai tutto"))
    // Un documento e un audio non hanno azioni del tipo oltre a quelle della barra: niente «•••».
    for (kind in listOf(Kind.Pdf, Kind.Audio, Kind.Unknown)) {
      switchTo(Scene(kind))
      assertTrue("$kind: niente «•••»", compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes().isEmpty())
    }
  }

  @Test fun `n di N resta sotto il nome, e nessun bersaglio della barra ne tocca un altro`() {
    setUp(Scene(Kind.Pdf))
    for (current in listOf(Scene(Kind.Pdf), Scene(Kind.Audio), Scene(Kind.Archive), Scene(Kind.Unknown),
      Scene(Kind.Pdf, name = "Relazione di fine anno del gruppo di lavoro sulle dune costiere.pdf"))) {
      if (current != Scene(Kind.Pdf)) switchTo(current)
      val label = "${current.kind} ${current.name ?: ""} a $screen dp"
      val title = titleNode(expectedTitle(current.kind, current.name)).dp()
      val back = button("Indietro").dp()
      val info = button("Informazioni").dp()
      assertTrue("$label: il titolo $title e l'indietro $back", title.left >= back.right - 0.5f)
      assertTrue("$label: il titolo $title e «i» $info", title.right <= info.left + 0.5f)
      if (current.kind == Kind.Pdf) {
        val counter = compose.onAllNodesWithText("6 di 10").fetchSemanticsNodes().single().dp()
        assertTrue("$label: «6 di 10» $counter sotto il centro del titolo $title", counter.center.y > title.center.y)
      }
      val screen = screenDp()
      val targets = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.dp() to it }
        .filter { (box, _) -> box.top < 140f && box.width < screen.width * 0.8f }
      for ((box, node) in targets) assertTrue("$label: ${node.text()} fuori dallo schermo ($box)",
        box.left >= -0.5f && box.right <= screen.right + 0.5f)
      for (i in targets.indices) for (j in i + 1 until targets.size) {
        val (a, na) = targets[i]
        val (b, nb) = targets[j]
        val overlap = a.intersect(b)
        assertTrue("$label: si toccano ${na.text() ?: na.config.getOrNull(SemanticsProperties.ContentDescription)} $a e " +
          "${nb.text() ?: nb.config.getOrNull(SemanticsProperties.ContentDescription)} $b",
          overlap.width <= 2.5f || overlap.height <= 0.5f)
      }
    }
  }

  // --- I fermi per Alessio -------------------------------------------------------------------------------------------

  @Test fun `fermi del titolo e del menu aperto, in chiaro e in scuro`() {
    val folder = System.getenv("FLUID_F6C19_EVIDENCE")?.let { File(it) } ?: return
    if (screen != 412) return
    setUp(Scene(Kind.Pdf, app = true))
    for ((theme, night) in listOf("chiaro" to false, "scuro" to true)) {
      for (kind in listOf(Kind.Pdf, Kind.Audio, Kind.Archive)) {
        compose.runOnUiThread { dark = night }
        switchTo(Scene(kind, app = true))
        capture(File(folder, "$theme/${kind.name.lowercase()}-titolo.png"))
        opened { tap(titleNode(expectedTitle(kind))) }
        advance(600)
        capture(File(folder, "$theme/${kind.name.lowercase()}-menu.png"))
        if (kind == Kind.Archive) {
          // «•••» dell'archivio: le azioni del tipo, senza le voci che il titolo ha già.
          switchTo(Scene(kind, app = true))
          opened { tap(button("Altro")) }
          advance(600)
          capture(File(folder, "$theme/archive-altro.png"))
        }
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
class FluidViewerFileMenuNarrowUiTest : FluidViewerFileMenuUiTest() {
  override val screen = 360
}
