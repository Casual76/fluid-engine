package dev.antigravity.fluidengine.sample.fluid.viewer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.galleryShowsTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
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
 * F6C.20 · **La fila in basso dei visori sta in fondo** (riprese del 23 novembre 2026: sul Tab S9 «Capitoli» copriva Play
 * nell'audio, «Estrai tutto» l'ultima riga dell'archivio, e Alessio: «i tasti nella parte inferiore dello schermo stanno
 * davvero tanto in alto»). Due assetti, col visore da solo e dentro una rotta di [FluidNavHost] (la barra la disegna
 * l'host, come sul dispositivo):
 * - **la galleria**: `ProvideFluidChrome` dichiara lo spazio della tab bar solo sulle pagine che la mostrano
 *   ([galleryShowsTabBar], F6B.15), e «Visori» non la mostra. «•••», la capsula al centro e condividi hanno il vetro a
 *   28 dp dal fondo (`FluidBottomBarPlacement.ScreenEdgeDistance`, la misura di F6B.15 e di Quick Look di iOS 26: 28 pt;
 *   il bersaglio da 48 a 26), o 4 dp sopra
 *   la barra di navigazione;
 * - **un'app con una tab bar sua** sotto il visore (lo spazio di 86 dp, nessuna tab bar dell'engine): la fila sale sopra
 *   di lei, e il contenuto le sta sopra (prima l'audio ne riservava solo la parte «in più» e «Capitoli» copriva Play).
 *
 * Per l'audio coi capitoli e per una pagina d'archivio (le righe, «Estrai tutto» al centro) nessun nodo del contenuto tocca
 * un tasto della fila, né a riposo né in fondo allo scorrimento. A 412 dp, e nelle sottoclassi a 360, 600, 840 e alla
 * larghezza del Tab S9 in orizzontale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w412dp-h891dp-xhdpi")
open class FluidViewerBottomRowUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  protected open val screen = 412

  enum class Kind { Audio, Archive, LongArchive }

  /** [appBar]: un'app con una tab bar sua sotto il visore; altrimenti la galleria. */
  private data class Scene(val kind: Kind, val hosted: Boolean, val dark: Boolean = false, val appBar: Boolean = false)

  private var scene by mutableStateOf(Scene(Kind.Audio, hosted = false))
  private var navigationDp = 0f
  private var diagnosticsWere = false
  private val rows = LazyListState()

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

  private val session = object : FluidMediaSession {
    override val playing = false
    override val positionMillis = 0L
    override val durationMillis = 12_000L
    override val canSeek = true
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMillis: Long) = Unit
  }

  private fun archive(count: Int) = FluidViewerContent.Custom(actions = {
    button("prova.estrai", FluidIcons.GridView, "Estrai tutto", {}, placement = FluidToolbarPlacement.BottomBar)
    viewerTypeActions { listOf(FluidContextAction("Ordina per nome") {}) }
  }) {
    LazyColumn(Modifier.fillMaxSize(), state = rows, contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
      top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
      item { Box(Modifier.fillMaxWidth().height(180.dp)) { Text("Progetto") } }
      items(count) { i ->
        Box(Modifier.fillMaxWidth().height(58.dp).testTag("riga-$i").clickable {}) { Text("file-$i.txt") }
      }
    }
  }

  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  private fun Viewer(kind: Kind) {
    val navigation = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    SideEffect { navigationDp = navigation.value }
    val info = when (kind) {
      Kind.Audio -> FluidViewerInfo(FluidFileInfo("Campanelle.m4a"), title = "Campanelle",
        subtitle = "Fluid Glass · Suoni della galleria", audioFormat = "AAC · 88 kbps",
        chapters = listOf(FluidAudioChapter("Inizio", 0), FluidAudioChapter("Metà", 4_000), FluidAudioChapter("Fine", 8_000)))
      else -> FluidViewerInfo(FluidFileInfo("progetto.zip"))
    }
    val content = when (kind) {
      Kind.Audio -> FluidViewerContent.Media(session)
      Kind.Archive -> archive(4)
      Kind.LongArchive -> archive(40)
    }
    FluidViewerInfoProvider({ info }) {
      FluidFileViewer(1, content = { content }, modifier = Modifier.fillMaxSize(), toolbar = {
        button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar)
      })
    }
  }

  private fun show(next: Scene) {
    compose.runOnUiThread { scene = next }
    Snapshot.sendApplyNotifications()
    advance(1500)
  }

  private fun setUp() {
    compose.setContent {
      val current = scene
      FluidTheme(EngineSettings(themeMode = if (current.dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        // La galleria dichiara lo spazio della tab bar per tutte le pagine, e «Visori» lo azzera; un'app con una tab bar
        // sua lo dichiara e basta.
        ProvideFluidChrome(rememberFluidChromeController(),
          when {
            current.appBar -> AppBarRoom
            galleryShowsTabBar(GalleryPage.Visori) -> FluidFloatingTabBarDefaults.ContentInset
            else -> 0.dp
          }, remember { FluidScrollToTopBus() }) {
          key(current) {
            val page: @Composable () -> Unit = {
              if (current.hosted) {
                val nav = rememberFluidNavController()
                FluidNavHost(nav, "visore") { fluidFileViewerRoute("visore") { Viewer(current.kind) } }
              } else {
                Viewer(current.kind)
              }
            }
            page()
          }
        }
      }
    }
    advance(1500)
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun SemanticsNode.dp(clipped: Boolean = false): Rect {
    if (clipped) {
      val density = compose.activity.resources.displayMetrics.density
      val b = boundsInRoot
      return Rect(b.left / density, b.top / density, b.right / density, b.bottom / density)
    }
    val id = this.id
    val box = compose.onNode(SemanticsMatcher("nodo $id") { it.id == id }, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return Rect(box.left.value, box.top.value, box.right.value, box.bottom.value)
  }

  private fun button(description: String): SemanticsNode = compose.onAllNodesWithContentDescription(description)
    .fetchSemanticsNodes().single { SemanticsActions.OnClick in it.config }

  private fun center(kind: Kind): SemanticsNode = when (kind) {
    Kind.Audio -> button("Capitoli")
    else -> button("Estrai tutto")
  }

  /** La fila in basso: «•••», la capsula al centro, condividi. */
  private fun bottomRow(kind: Kind): List<Rect> = listOf(button("Altro"), center(kind), button("Condividi")).map { it.dp() }

  private fun assertRowAtTheBottom(kind: Kind) {
    val height = compose.onRoot().getUnclippedBoundsInRoot().bottom.value
    // Il bordo basso del bersaglio da 48 dp: il vetro da 44 ci sta in mezzo, 2 dp più su.
    val expected = if (scene.appBar) navigationDp + AppBarRoom.value + 8f - 2f else maxOf(ScreenEdge, navigationDp + 4f) - 2f
    val row = bottomRow(kind)
    for (key in row) {
      assertEquals("$screen dp, $scene: il bordo basso di $key, a ${height - key.bottom} dp dal fondo", expected,
        height - key.bottom, 1.5f)
    }
    assertTrue("$screen dp, $scene: «•••», la capsula e condividi in fila: $row",
      row.zipWithNext().all { (a, b) -> a.right <= b.left + 0.5f } && row.first().left >= 0f &&
        row.last().right <= screen + 0.5f)
  }

  /** Nessun nodo del contenuto che si vede (ritagliato) tocca un tasto della fila. */
  private fun assertNothingUnderTheRow(kind: Kind) {
    val row = bottomRow(kind)
    val keys = listOf(button("Altro"), center(kind), button("Condividi")).map { it.id }.toSet()
    val top = row.minOf { it.top }
    val content = compose.onAllNodes(SemanticsMatcher("contenuto") { node ->
      node.config.getOrNull(SemanticsProperties.Text) != null || SemanticsActions.OnClick in node.config
    }, useUnmergedTree = true).fetchSemanticsNodes().filter { it.id !in keys && it.boundsInRoot.height > 0f }
    for (node in content) {
      val box = node.dp(clipped = true)
      if (box.bottom <= top + 0.5f) continue
      val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
      // I nodi della fila stessa (il testo dentro i tasti) stanno dentro un tasto: non sono contenuto.
      if (row.any { it.contains(box.center) && box.width <= it.width + 1f }) continue
      for (key in row) assertTrue("$screen dp, $scene: «$label» $box tocca la fila $key", !key.overlaps(box))
    }
  }

  @Test fun laFilaInBassoStaInFondoESenzaSovrapposizioni() {
    setUp()
    for (appBar in listOf(false, true)) for (hosted in listOf(false, true)) for (dark in listOf(false, true)) {
      for (kind in listOf(Kind.Audio, Kind.Archive)) {
        show(Scene(kind, hosted, dark, appBar))
        assertRowAtTheBottom(kind)
        assertNothingUnderTheRow(kind)
      }
    }
  }

  @Test fun inFondoAlloScorrimentoLUltimaRigaStaSopraLaFila() {
    setUp()
    for (appBar in listOf(false, true)) for (hosted in listOf(false, true)) {
      show(Scene(Kind.LongArchive, hosted, appBar = appBar))
      assertRowAtTheBottom(Kind.LongArchive)
      compose.runOnUiThread { rows.requestScrollToItem(41) }
      Snapshot.sendApplyNotifications()
      advance(600)
      val last = compose.onAllNodesWithText("file-39.txt", useUnmergedTree = true).fetchSemanticsNodes().single().dp(clipped = true)
      val top = bottomRow(Kind.LongArchive).minOf { it.top }
      assertTrue("$screen dp, hosted=$hosted, appBar=$appBar: l'ultima riga $last sopra la fila ($top)", last.bottom <= top + 0.5f)
      assertNothingUnderTheRow(Kind.LongArchive)
    }
  }

  @Test fun nellAudioPlayECapitoliNonSiToccano() {
    setUp()
    for (appBar in listOf(false, true)) for (hosted in listOf(false, true)) {
      show(Scene(Kind.Audio, hosted, appBar = appBar))
      val play = button("Riproduci").dp(clipped = true)
      val chapters = button("Capitoli").dp()
      assertTrue("$screen dp, hosted=$hosted, appBar=$appBar: Play $play e Capitoli $chapters", !play.overlaps(chapters))
      assertTrue("$screen dp: Play sopra la fila", play.bottom <= chapters.top + 0.5f)
    }
  }
}

/** `FluidBottomBarPlacement.ScreenEdgeDistance` (interna a engine-ui): dal fondo dello schermo al fondo del vetro. */
private const val ScreenEdge = 28f

/** Lo spazio di una tab bar da 62 dp col suo margine, come quello che la galleria dichiara nel formato telefono. */
private val AppBarRoom = 86.dp

@Config(sdk = [35], qualifiers = "it-rIT-w360dp-h780dp-xhdpi")
class FluidViewerBottomRowNarrowUiTest : FluidViewerBottomRowUiTest() {
  override val screen = 360
}

@Config(sdk = [35], qualifiers = "it-rIT-w600dp-h960dp-xhdpi")
class FluidViewerBottomRow600UiTest : FluidViewerBottomRowUiTest() {
  override val screen = 600
}

@Config(sdk = [35], qualifiers = "it-rIT-w840dp-h1180dp-xhdpi")
class FluidViewerBottomRow840UiTest : FluidViewerBottomRowUiTest() {
  override val screen = 840
}

/** Il Tab S9 in orizzontale: 2560 × 1600 px a 340 dpi, cioè 1205 × 753 dp. */
@Config(sdk = [35], qualifiers = "it-rIT-w1205dp-h753dp-land-xhdpi")
class FluidViewerBottomRowTabletUiTest : FluidViewerBottomRowUiTest() {
  override val screen = 1205
}
