package dev.antigravity.fluidengine.map

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.26c · Le correzioni viste sul tablet il 26/11/2026 (formato telefono, 1080 × 2340 a 420): i crediti di OpenFreeMap e
 * TalkBack che li leggeva due volte, i tasti della scheda che spezzavano le parole, il tasto della posizione che diceva
 * «non disponibile» prima del tocco, e la mappa in `SurfaceView` chiusa nella registrazione del vetro (nera).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidMapPhoneFixesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val duomo = FluidGeoPoint(43.77310, 11.25600)

  private fun show(engine: FluidMapEngine = FluidSketchMapEngine(), content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMapEngine provides engine) {
          val host = rememberFluidGlassModalHostState()
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
            Box(Modifier.fillMaxSize()) {
              content()
              FluidGlassModalHost(state = host, backdrop = null)
            }
          }
        }
      }
    }
    settle()
  }

  private fun settle(ms: Long = 1_000) {
    var t = 0L
    while (t < ms) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      t += 16
    }
    compose.waitForIdle()
  }

  private fun layoutOf(text: String): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
      .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
    return results.single()
  }

  @Test
  fun `i crediti dicono OpenFreeMap, OpenMapTiles e OpenStreetMap, e TalkBack li legge una volta`() {
    val short = FluidMapStyles.OpenFreeMapAttribution.short
    for (name in listOf("OpenFreeMap", "OpenMapTiles", "OpenStreetMap")) assertTrue(short, short.contains(name))
    // Il «©» resta attaccato al suo nome: a capo si va solo fra un nome e l'altro.
    assertFalse(short, short.contains("© "))
    show { FluidMap(FluidMapState(FluidMapCamera(duomo, 14.0)), Modifier.fillMaxSize()) }
    val chip = compose.onNodeWithTag(FluidMapTags.Attribution).fetchSemanticsNode()
    assertEquals(listOf("Crediti della mappa: $short"), chip.config[SemanticsProperties.ContentDescription])
    // Prima la scritta sul vetro si univa alla descrizione: «Crediti della mappa: © OpenStreetMap, © OpenStreetMap».
    assertNull(chip.config.getOrNull(SemanticsProperties.Text))
    assertTrue(chip.config.contains(SemanticsActions.OnClick))
  }

  @Test
  fun `nel formato telefono i tasti della scheda tengono le parole intere, con l'etichetta sotto l'icona`() {
    show { Box(Modifier.fillMaxWidth()) { FluidMapPlaceCard(FluidMapPlace("Duomo", duomo, subtitle = "Firenze · 16 foto"), onDirections = {}) } }
    for (label in listOf("Indicazioni", "Condividi")) {
      val layout = layoutOf(label)
      assertEquals("$label su una riga", 1, layout.lineCount)
      assertFalse("$label intera", layout.isLineEllipsized(0))
    }
    // Sotto l'icona: il tasto e' alto (56 dp), e l'etichetta sta sotto il centro del tasto.
    val button = compose.onNodeWithTag(FluidMapPlaceTags.Directions).fetchSemanticsNode().boundsInRoot
    val label = compose.onNodeWithText("Indicazioni", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue("alto ${button.height}", button.height >= 55f * 2.625f)
    assertTrue("sotto", label.center.y > button.center.y)
    // «Copia»: TalkBack dice solo «Copia coordinate», non «Copia coordinate, Copia».
    val copy = compose.onNodeWithTag(FluidMapPlaceTags.Copy).fetchSemanticsNode()
    assertEquals(listOf("Copia coordinate"), copy.config[SemanticsProperties.ContentDescription])
    assertNull(copy.config.getOrNull(SemanticsProperties.Text))
  }

  @Test
  fun `sul tablet i tasti della scheda restano con l'etichetta accanto all'icona`() {
    show { Box(Modifier.requiredWidth(700.dp)) { FluidMapPlaceCard(FluidMapPlace("Duomo", duomo), onDirections = {}) } }
    val button = compose.onNodeWithTag(FluidMapPlaceTags.Directions).fetchSemanticsNode().boundsInRoot
    assertTrue("basso ${button.height}", button.height < 50f * 2.625f)
    assertEquals(1, layoutOf("Indicazioni").lineCount)
  }

  @Test
  fun `il tasto della posizione prima del tocco dice Posizione, non non disponibile`() {
    val state = FluidMapState(FluidMapCamera(duomo, 14.0))
    show { FluidMap(state, Modifier.fillMaxSize(), onLocationRequest = {}) }
    assertEquals(
      listOf("Posizione"),
      compose.onNodeWithTag(FluidMapTags.Location).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
    )
  }

  @Test
  fun `la mappa vera in TextureView ha di serie il limite dei fotogrammi dello schermo`() {
    // Sul Tab S9 MapLibre in TextureView disegnava 220 fotogrammi al secondo su uno schermo da 120.
    val context = compose.activity
    val screen = FluidMapLibreEngine.screenMaximumFps(context)
    assertTrue("$screen", screen in 30..240)
    assertEquals(screen, FluidMapLibreEngine(context).maximumFps)
    assertEquals(0, FluidMapLibreEngine(context, FluidMapRenderMode.Texture, maximumFps = 0).maximumFps)
  }

  /** Un motore finto che dice solo se il vetro lo vede: la sua superficie e' un nodo col tag «motore». */
  private class PlainEngine(override val glassSamplesMap: Boolean) : FluidMapEngine {
    @Composable
    override fun Surface(state: FluidMapState, style: FluidMapStyle, dark: Boolean, modifier: Modifier) {
      Box(modifier.testTag("motore"))
    }

    override suspend fun snapshot(request: FluidMapSnapshotRequest): ImageBitmap? = null
  }

  private fun parentOfEngine(): List<String>? =
    compose.onNodeWithTag("motore", useUnmergedTree = true).fetchSemanticsNode().parent?.config
      ?.getOrNull(SemanticsProperties.ContentDescription)

  @Test
  fun `una mappa che il vetro non vede sta fuori dalla registrazione del vetro`() {
    // La SurfaceView apre un buco nella finestra: dentro il layer fuori schermo della sorgente il buco restava chiuso, e la
    // mappa sul Tab S9 era nera. Fuori dalla sorgente la mappa sta sotto, e la sorgente (vuota) porta solo TalkBack.
    show(PlainEngine(glassSamplesMap = false)) {
      FluidMap(FluidMapState(FluidMapCamera(duomo, 14.0)), Modifier.fillMaxSize(), onLocationRequest = {})
    }
    assertNull("fuori dalla sorgente", parentOfEngine())
  }

  @Test
  fun `una mappa che il vetro rifrange resta dentro la registrazione del vetro`() {
    show(PlainEngine(glassSamplesMap = true)) {
      FluidMap(FluidMapState(FluidMapCamera(duomo, 14.0)), Modifier.fillMaxSize(), onLocationRequest = {})
    }
    assertTrue("dentro la sorgente", parentOfEngine().orEmpty().firstOrNull().orEmpty().startsWith("Mappa"))
  }
}
