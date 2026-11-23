package dev.antigravity.fluidengine.viewer.scene

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerFileActions
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerStrings
import dev.antigravity.fluidengine.ui.theme.FluidTheme
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
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * F6C.20 · La pagina di un modello 3D nel visore vero, col condividi dell'app: la barra tiene tre vetri (R5 B11), quindi
 * al centro un comando solo («Riproduci/Pausa» per un modello animato, «Inquadra» per uno fermo, «AR» quando
 * l'estensione lo mostra), in vista e nello schermo a 412 e 360 dp; in «•••» «Inquadra», «Animazioni» (ogni clip per
 * nome, con la spunta, solo con almeno due) e «Rotazione automatica», nessuna col nome di una voce del menu del file o
 * di un tasto della barra. Filament su Robolectric non c'è: la superficie è una finta che ricorda che cosa la pagina le
 * chiede, la stessa chiamata che riceve [SceneTexture]. La rotazione della camera è provata a parte ([SceneSpin]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-mdpi")
open class SceneTypeActionsTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
  private var started = false
  private val fileMenu = FluidViewerStrings().run { setOf(share, openWith, copyFile, print, info, rename, move, duplicate, delete) }
  private val anyNode = SemanticsMatcher("ogni nodo") { true }
  private var surface: RecordingSurface? = null

  /** La superficie finta: tiene l'ultimo ordine della pagina. */
  private class RecordingSurface(context: Context) : View(context), SceneSurface {
    var drawing = false
    var playing = false
    var clip = -1
    var frameRequests = -1
    var rotating = false
    override fun update(active: Boolean, play: Boolean, animation: Int, frameRequest: Int, autoRotate: Boolean) {
      drawing = active
      playing = play
      clip = animation
      frameRequests = frameRequest
      rotating = autoRotate
    }
    override fun dispose() = Unit
  }

  /** Un'estensione come quella di engine-viewer-ar: il suo tasto «AR» al centro della barra. */
  private class ArLike : FluidSceneViewerControls {
    var opened = 0
    override val overlayVisible = false
    override fun actions(toolbar: FluidToolbarScope) {
      toolbar.text("3d.ar", "AR", { opened++ }, FluidToolbarPlacement.BottomBar)
    }
    @Composable override fun Overlay(active: Boolean) = Unit
  }

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    if (started) controller.pause().stop().destroy()
  }

  @Test fun `le animazioni per nome, Inquadra e la rotazione automatica arrivano alla superficie`() {
    val model = model("Danza.gltf", TwoClips)
    assertEquals(listOf("Salto", null), model.animationNames)
    show(model, "Danza.gltf")
    assertBar("Riproduci")
    assertTrue("niente «Animazione successiva»: la sostituisce «Animazioni»", bar("Animazione successiva") == null)

    val rows = openMore()
    assertEquals(setOf("Inquadra", "Animazioni Salto", "Rotazione automatica"), labels(rows))
    assertClean(rows, bar = setOf("Riproduci"))
    tap(rows.row("Animazioni"))
    assertTrue("la spunta sulla prima", SemanticsProperties.Selected in menuRow("Salto").config)
    tap(menuRow("Animazione 2"))
    val drawn = requireNotNull(surface)
    assertEquals("la superficie mostra la seconda clip", 1, drawn.clip)
    assertTrue("e la fa suonare", drawn.playing)
    assertBar("Pausa")

    val menu = openMore()
    assertEquals(setOf("Inquadra", "Animazioni Animazione 2", "Rotazione automatica"), labels(menu))
    assertFalse(SemanticsProperties.Selected in menu.row("Rotazione automatica").config)
    tap(menu.row("Rotazione automatica"))
    assertTrue("la camera gira da sola", drawn.rotating)
    assertTrue(drawn.drawing)
    val on = openMore()
    assertTrue("la spunta sulla rotazione", SemanticsProperties.Selected in on.row("Rotazione automatica").config)
    tap(on.row("Rotazione automatica"))
    assertFalse("spenta", drawn.rotating)

    val frame = drawn.frameRequests
    tap(openMore().row("Inquadra"))
    assertEquals("«Inquadra» chiede l'inquadratura alla superficie", frame + 1, drawn.frameRequests)
  }

  @Test fun `un modello con una clip sola ha Riproduci in barra e in tre puntini Inquadra e la rotazione`() {
    val model = runBlocking { openFluidSceneModel(FluidViewerSource.of(resource("anelli.glb"))) }
    assertEquals(1, model.stats.animations)
    show(model, "anelli.glb")
    assertBar("Riproduci")
    val rows = openMore()
    assertEquals(setOf("Inquadra", "Rotazione automatica"), labels(rows))
    assertClean(rows, bar = setOf("Riproduci"))
    tap(rows.row("Rotazione automatica"))
    assertTrue(requireNotNull(surface).rotating)
  }

  @Test fun `un modello fermo ha Inquadra in barra e in tre puntini la rotazione`() {
    val model = runBlocking { openFluidSceneModel(FluidViewerSource.of(resource("piramide-binaria.stl"))) }
    assertEquals(0, model.stats.animations)
    show(model, "piramide-binaria.stl")
    assertBar("Inquadra")
    val rows = openMore()
    assertEquals(setOf("Rotazione automatica"), labels(rows))
    val frame = requireNotNull(surface).frameRequests
    tap(requireNotNull(bar("Inquadra")))
    assertEquals(frame + 1, requireNotNull(surface).frameRequests)
  }

  @Test fun `con il tasto AR al centro i comandi del modello stanno in tre puntini`() {
    val ar = ArLike()
    show(model("Danza.gltf", TwoClips), "Danza.gltf", ar)
    assertBar("AR")
    assertTrue("«Riproduci» non è in barra", bar("Riproduci") == null)
    val rows = openMore()
    assertEquals(setOf("Riproduci", "Inquadra", "Animazioni Salto", "Rotazione automatica"), labels(rows))
    assertClean(rows, bar = setOf("AR"))
    tap(rows.row("Riproduci"))
    assertTrue("suona", requireNotNull(surface).playing)
    val playing = openMore()
    assertTrue("e il menu dice «Pausa»", labels(playing).contains("Pausa"))
    tap(playing.row("Pausa"))
    assertFalse("in pausa", requireNotNull(surface).playing)
    tap(requireNotNull(bar("AR")))
    assertEquals(1, ar.opened)
  }

  @Test fun `la rotazione gira la camera attorno all'asse verticale del modello`() {
    // 32 gradi al secondo, e un giro intero torna all'inizio.
    assertEquals(Math.toRadians(32.0), SceneSpin.advance(0.0, 1_000_000_000L), 1e-9)
    assertEquals(Math.toRadians(24.0), SceneSpin.advance(0.0, 12_000_000_000L), 1e-9)
    assertEquals(0.5, SceneSpin.advance(0.5, 0L), 0.0)
    val eye = doubleArrayOf(3.2, 1.95, 4.5)
    val target = doubleArrayOf(0.0, 0.0, 0.0)
    val up = doubleArrayOf(0.0, 1.0, 0.0)
    SceneSpin.turn(PI / 2, eye, target, up)
    assertEquals(4.5, eye[0], 1e-9)
    assertEquals(1.95, eye[1], 1e-9)
    assertEquals(-3.2, eye[2], 1e-9)
    assertEquals("la distanza dal modello resta", sqrt(3.2 * 3.2 + 4.5 * 4.5), sqrt(eye[0] * eye[0] + eye[2] * eye[2]), 1e-9)
    assertEquals(listOf(0.0, 0.0, 0.0), target.toList())
    assertEquals(listOf(0.0, 1.0, 0.0), up.toList())
    SceneSpin.turn(-PI / 2, eye)
    assertEquals(3.2, eye[0], 1e-9)
    assertEquals(4.5, eye[2], 1e-9)
  }

  // --- Il visore ---------------------------------------------------------------------------------------------------

  private fun model(name: String, json: String): FluidSceneModel {
    val file = File(RuntimeEnvironment.getApplication().cacheDir, name).apply { writeText(json) }
    return runBlocking { openFluidSceneModel(FluidViewerSource.of(file)) }
  }

  private fun resource(name: String): File {
    val bytes = checkNotNull(SceneTypeActionsTest::class.java.getResourceAsStream("/corpus/$name")).use { it.readBytes() }
    return File(RuntimeEnvironment.getApplication().cacheDir, name).apply { writeBytes(bytes) }
  }

  private fun show(model: FluidSceneModel, name: String, extension: FluidSceneViewerControls? = null) {
    val factory = SceneSurfaceFactory { context, _, onFirstFrame, _ ->
      RecordingSurface(context).also {
        surface = it
        it.post(onFirstFrame)
      }
    }
    val page = ScenePage(model, name, null, factory, extension)
    val actions = FluidViewerFileActions(share = {}, openWith = {}, copy = {}, print = {}, rename = {}, move = {},
      duplicate = {}, delete = {})
    started = true
    controller.setup().get().setContent {
      FluidTheme(EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false)) {
        FluidViewerInfoProvider({ FluidViewerInfo(FluidFileInfo(name)) }) {
          // Il condividi dell'app in basso a destra, come in un'app vera: il terzo vetro della barra.
          FluidFileViewer(1, content = { page.content }, onBack = {}, fileActions = { actions },
            toolbar = { button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar) })
        }
      }
    }
    advance(1200)
    waitFor("«•••» del modello") { more() != null && surface != null }
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

  private fun SemanticsNode.label(): String? = config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

  private fun SemanticsNode.box(): DpRect {
    val target = id
    return compose.onNode(SemanticsMatcher("nodo $target") { it.id == target }).getUnclippedBoundsInRoot()
  }

  private fun texts(): String = compose.onAllNodes(anyNode).fetchSemanticsNodes().mapNotNull {
    it.text() ?: it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
  }.joinToString(" | ")

  private fun more(): SemanticsNode? = compose.onAllNodesWithContentDescription("Altro").fetchSemanticsNodes()
    .singleOrNull { SemanticsActions.OnClick in it.config }

  /** Un tasto della barra in basso: la sua descrizione è il nome, e sta nella fascia in fondo allo schermo. */
  private fun bar(label: String): SemanticsNode? {
    val bottom = compose.onRoot().getUnclippedBoundsInRoot().bottom.value
    return compose.onAllNodes(hasContentDescription(label) and hasClickAction()).fetchSemanticsNodes()
      .firstOrNull { it.box().top.value > bottom - BarZone }
  }

  /** «•••», [labels] e condividi in vista e dentro lo schermo, e un «•••» solo (un tasto nel trabocco ne farebbe un altro). */
  private fun assertBar(vararg labels: String) {
    val screen = compose.onRoot().getUnclippedBoundsInRoot()
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

  /** Nessuna voce di «•••» ha il nome di una voce del menu del file o di un tasto della barra. */
  private fun assertClean(rows: List<SemanticsNode>, bar: Set<String>) {
    for (item in rows.map { it.label()!! }) {
      assertFalse("«$item» è nel menu del file", item in fileMenu)
      assertFalse("«$item» è un tasto della barra", item in bar)
    }
  }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun SemanticsNode.isRow() = text() != null &&
    (SemanticsActions.OnClick in config || SemanticsProperties.Disabled in config)

  private fun opened(open: () -> Unit): List<SemanticsNode> {
    val before = compose.onAllNodes(anyNode).fetchSemanticsNodes().map { it.id }.toSet()
    open()
    return compose.onAllNodes(anyNode).fetchSemanticsNodes().filter { it.id !in before && it.isRow() }
  }

  private fun openMore(): List<SemanticsNode> = opened { tap(requireNotNull(more()) { "niente «•••» (${texts()})" }) }

  /** La riga di un sottomenu aperto con l'etichetta [label]: il sottomenu prende il posto del menu nello stesso vetro. */
  private fun menuRow(label: String): SemanticsNode = compose.onAllNodes(anyNode).fetchSemanticsNodes()
    .filter { it.isRow() && it.label() == label }.let { rows ->
      assertTrue("la voce «$label» (${texts()})", rows.isNotEmpty())
      rows.last()
    }

  private fun labels(nodes: List<SemanticsNode>) = nodes.map { it.text()!! }.toSet()

  private fun List<SemanticsNode>.row(label: String) = single { it.text()!!.startsWith(label) }

  private companion object {
    /** La fascia in fondo allo schermo dove sta la barra in basso (dp): la barra e il suo margine. A occhio. */
    const val BarZone = 140f

    /** Un triangolo con due animazioni, la prima col nome e la seconda senza. */
    val TwoClips = """
      {"asset":{"version":"2.0"},"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
      "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
      "accessors":[{"count":3,"min":[0,0,0],"max":[1,1,0]}],
      "animations":[{"name":"Salto","channels":[],"samplers":[]},{"name":" ","channels":[],"samplers":[]}]}
    """.trimIndent()
  }
}

/** Le stesse prove sul telefono stretto, a 360 dp. */
@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-mdpi")
class SceneTypeActionsNarrowTest : SceneTypeActionsTest()
