package dev.antigravity.fluidengine.map

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerLocation
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * F8.26 · La mappa col dito vero (Robolectric), sul disegno dell'engine: trascinare, pizzicare, il doppio tocco; i comandi
 * di vetro (posizione, bussola, livelli); i grappoli che si aprono al tocco; l'attribuzione sempre in vista coi crediti;
 * la cartolina e il luogo del visore; TalkBack e «Riduci movimento».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidMapUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val engine = FluidSketchMapEngine()
  private val signoria = FluidGeoPoint(43.76956, 11.25581)
  private val duomo = FluidGeoPoint(43.77310, 11.25600)
  private val pitti = FluidGeoPoint(43.76510, 11.25000)

  private val markers = listOf(
    FluidMapMarker("signoria", signoria, "Piazza della Signoria"),
    FluidMapMarker("duomo", duomo, "Duomo", subtitle = "38 foto"),
    FluidMapMarker("pitti", pitti, "Palazzo Pitti"),
  )

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default,
          LocalFluidMapEngine provides engine,
        ) {
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

  private fun settle(ms: Long = 1_200) {
    var t = 0L
    while (t < ms) {
      // Le scritture dello stato fatte dalla prova (la posizione, la camera, il foglio) arrivano alla composizione.
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      t += 16
    }
    compose.waitForIdle()
  }

  private fun markerCenter(id: String): Offset {
    val b = compose.onNodeWithTag(FluidMapTags.marker(id), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    return Offset((b.left + b.right) / 2f, b.bottom)
  }

  @Test
  fun `il dito trascina la mappa e i segnaposto lo seguono al pixel`() {
    // Fra la Signoria e il Duomo: tutti e due sullo schermo.
    val state = FluidMapState(FluidMapCamera(FluidGeoPoint(43.7713, 11.2559), 15.6))
    show { FluidMap(state, Modifier.fillMaxSize(), markers = markers) }
    val before = markerCenter("duomo")
    val camera = state.camera
    compose.onNodeWithTag(FluidMapTags.Map).performTouchInput {
      // Piano (circa 300 px/s): sotto la soglia del lancio, la mappa si ferma dove la lascia il dito.
      down(center)
      repeat(30) { moveBy(Offset(-10f, 20f / 3f)); advanceEventTime(32) }
      advanceEventTime(120)
      up()
    }
    settle(64)
    val after = markerCenter("duomo")
    assertEquals(before.x - 300f, after.x, 1.5f)
    assertEquals(before.y + 200f, after.y, 1.5f)
    // La camera si e' mossa di quanto ha detto il dito, e il dito lento non lancia la mappa.
    val p = FluidMapProjector(camera, state.sizePx.width.toFloat(), state.sizePx.height.toFloat(), state.density)
    val expected = p.panned(Offset(-300f, 200f)).center
    assertEquals(expected.latitude, state.camera.center.latitude, 1e-6)
    assertEquals(expected.longitude, state.camera.center.longitude, 1e-6)
    assertFalse(state.isGestureActive)
  }

  @Test
  fun `due dita pizzicano attorno al punto fra le dita e il doppio tocco avvicina di un passo`() {
    val state = FluidMapState(FluidMapCamera(signoria, 14.0))
    show { FluidMap(state, Modifier.fillMaxSize(), markers = markers) }
    compose.onNodeWithTag(FluidMapTags.Map).performTouchInput {
      pinch(center - Offset(100f, 0f), center - Offset(200f, 0f), center + Offset(100f, 0f), center + Offset(200f, 0f), 300)
    }
    settle(100)
    assertEquals(15.0, state.camera.zoom, 0.08)
    val zoomed = state.camera.zoom
    compose.onNodeWithTag(FluidMapTags.Map).performTouchInput { doubleClick(center) }
    settle(600)
    assertEquals(zoomed + 1.0, state.camera.zoom, 0.02)
  }

  @Test
  fun `il tasto della posizione chiede il permesso, poi porta la mappa sulla posizione e la segue finche' il dito la sposta`() {
    val state = FluidMapState(FluidMapCamera(pitti, 12.0))
    var asked = 0
    show { FluidMap(state, Modifier.fillMaxSize(), onLocationRequest = { asked += 1 }) }
    compose.onNodeWithTag(FluidMapTags.Location).assertIsDisplayed().performClick()
    settle(100)
    assertEquals(1, asked)
    // La posizione finta della galleria (mai quella vera del tablet).
    state.userLocation = FluidMapUserLocation(duomo, accuracyMeters = 25f)
    settle(100)
    compose.onNodeWithTag(FluidMapTags.Location).performClick()
    settle(900)
    assertEquals(FluidMapTracking.Follow, state.tracking)
    assertEquals(duomo.latitude, state.camera.center.latitude, 1e-5)
    assertEquals(FluidMapMotion.UserZoom, state.camera.zoom, 1e-6)
    assertEquals("Posizione, la mappa ti segue", description(FluidMapTags.Location))
    // La posizione si sposta: la mappa la segue.
    state.userLocation = FluidMapUserLocation(signoria)
    settle(900)
    assertEquals(signoria.latitude, state.camera.center.latitude, 1e-5)
    // Il dito sposta la mappa: smette di seguire.
    compose.onNodeWithTag(FluidMapTags.Map).performTouchInput { swipeLeft(durationMillis = 400) }
    settle(1500)
    assertEquals(FluidMapTracking.Off, state.tracking)
  }

  @Test
  fun `la bussola compare solo col nord fuori posto e toccata lo riporta su`() {
    val state = FluidMapState(FluidMapCamera(signoria, 15.0))
    show { FluidMap(state, Modifier.fillMaxSize()) }
    assertEquals(0, compose.onAllNodesWithTag(FluidMapTags.Compass).fetchSemanticsNodes().size)
    state.moveTo(state.camera.copy(bearing = 45.0))
    settle(400)
    assertEquals("Bussola, 45 gradi. Tocca per tornare al nord", description(FluidMapTags.Compass))
    compose.onNodeWithTag(FluidMapTags.Compass).performClick()
    settle(800)
    assertEquals(0.0, state.camera.bearing, 1e-6)
    assertEquals(0, compose.onAllNodesWithTag(FluidMapTags.Compass).fetchSemanticsNodes().size)
  }

  @Test
  fun `col movimento ridotto la bussola e il tocco su un grappolo saltano invece di volare`() {
    val state = FluidMapState(FluidMapCamera(signoria, 15.0, bearing = 120.0))
    show(reduced = true) { FluidMap(state, Modifier.fillMaxSize(), markers = markers) }
    compose.onNodeWithTag(FluidMapTags.Compass).performClick()
    settle(32)
    assertEquals(0.0, abs(state.camera.bearing) % 360.0, 1e-6)
    state.moveTo(state.camera.copy(zoom = 11.0))
    settle(64)
    val cluster = clusterTag()
    compose.onNodeWithTag(cluster).performClick()
    settle(32)
    assertTrue("zoom ${state.camera.zoom}", state.camera.zoom > 12.0)
  }

  private fun clusterTag(): String {
    val nodes = compose.onAllNodes(SemanticsMatcher("grappolo") { n ->
      n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.endsWith("luoghi") == true
    }, useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("nessun grappolo", nodes.isNotEmpty())
    return nodes.first().config[SemanticsProperties.TestTag]
  }

  @Test
  fun `i segnaposto vicini sono un grappolo col numero, che toccato avvicina finche' si dividono`() {
    val state = FluidMapState(FluidMapCamera(signoria, 11.0))
    show { FluidMap(state, Modifier.fillMaxSize(), markers = markers) }
    val tag = clusterTag()
    assertEquals("3 luoghi", description(tag))
    compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    settle(1_500)
    assertTrue("zoom ${state.camera.zoom}", state.camera.zoom > 12.0)
    // Avvicinando il grappolo si divide: il grappolo di tre non c'e' piu', e almeno un segnaposto e' solo col suo nome.
    assertEquals(0, compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size)
    val singles = listOf("signoria", "duomo", "pitti").sumOf { compose.onAllNodesWithTag(FluidMapTags.marker(it), useUnmergedTree = true).fetchSemanticsNodes().size }
    assertTrue("segnaposto soli: $singles", singles >= 1)
  }

  @Test
  fun `il tocco su un segnaposto lo sceglie e TalkBack lo dice`() {
    val state = FluidMapState(FluidMapCamera(FluidGeoPoint(43.7713, 11.2559), 16.0))
    var picked: FluidMapMarker? = null
    val selected = androidx.compose.runtime.mutableStateOf<String?>(null)
    show {
      FluidMap(state, Modifier.fillMaxSize(), markers = markers, selectedMarkerId = selected.value, onMarkerClick = {
        picked = it
        selected.value = it.id
      })
    }
    compose.onNodeWithTag(FluidMapTags.marker("duomo"), useUnmergedTree = true).performClick()
    settle(400)
    assertEquals("duomo", picked?.id)
    val node = compose.onNodeWithTag(FluidMapTags.marker("duomo"), useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("Duomo, 38 foto", node.config[SemanticsProperties.ContentDescription].first())
    assertEquals(true, node.config.getOrNull(SemanticsProperties.Selected))
  }

  @Test
  fun `i livelli cambiano lo stile dal pannello che nasce dal tasto`() {
    val state = FluidMapState(FluidMapCamera(signoria, 14.0))
    show { FluidMap(state, Modifier.fillMaxSize()) }
    assertEquals("Livelli della mappa, Esplora", description(FluidMapTags.Layers))
    compose.onNodeWithTag(FluidMapTags.Layers).performClick()
    settle(600)
    compose.onNodeWithTag(FluidMapTags.style("vivace")).assertIsDisplayed().performClick()
    settle(600)
    assertEquals("vivace", state.styleId)
    assertEquals("Livelli della mappa, Vivace", description(FluidMapTags.Layers))
  }

  @Test
  fun `l'attribuzione e' sempre in vista, sopra il foglio, e toccata mostra i crediti`() {
    val state = FluidMapState(FluidMapCamera(signoria, 14.0))
    show { FluidMap(state, Modifier.fillMaxSize()) }
    compose.onNodeWithTag(FluidMapTags.Attribution).assertIsDisplayed()
    assertEquals("Crediti della mappa: ${FluidMapStyles.OpenFreeMapAttribution.short}", description(FluidMapTags.Attribution))
    val low = compose.onNodeWithTag(FluidMapTags.Attribution).fetchSemanticsNode().boundsInRoot.bottom
    state.bottomObscured = 300.dp
    settle(1_000)
    val high = compose.onNodeWithTag(FluidMapTags.Attribution).fetchSemanticsNode().boundsInRoot.bottom
    assertEquals(300f * state.density, low - high, 1.5f)
    compose.onNodeWithTag(FluidMapTags.Attribution).performClick()
    settle(600)
    compose.onNodeWithText("Dati © contributori di OpenStreetMap, licenza ODbL").assertIsDisplayed()
  }

  @Test
  fun `TalkBack ha le azioni della mappa, e Ingrandisci avvicina`() {
    val state = FluidMapState(FluidMapCamera(signoria, 13.0))
    show { FluidMap(state, Modifier.fillMaxSize(), markers = markers) }
    val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode()
    val actions = node.config[SemanticsActions.CustomActions].map { it.label }
    assertEquals(listOf("Ingrandisci", "Riduci", "Mostra tutti i luoghi"), actions)
    assertTrue(node.config[SemanticsProperties.ContentDescription].first().startsWith("Mappa, "))
    compose.runOnUiThread { node.config[SemanticsActions.CustomActions].first().action() }
    settle(600)
    assertEquals(14.0, state.camera.zoom, 0.01)
  }

  @Test
  fun `la cartolina e' ferma, si legge come mappa del luogo e toccata si apre`() {
    var opened = 0
    show {
      FluidMapPostcard(signoria, Modifier.fillMaxWidth().height(160.dp), title = "Piazza della Signoria", onClick = { opened += 1 })
    }
    settle(400)
    val node = compose.onNodeWithTag(FluidMapTags.Postcard).fetchSemanticsNode()
    assertEquals("Mappa di Piazza della Signoria", node.config[SemanticsProperties.ContentDescription].first())
    compose.onNodeWithTag(FluidMapTags.Postcard).performClick()
    assertEquals(1, opened)
    // L'immagine e' fatta e tenuta: la stessa cartolina non si rifa'.
    val size = node.size
    val key = FluidMapSnapshotRequest(FluidMapCamera(signoria, FluidMapPostcardDefaults.Zoom), size.width, size.height, node.layoutInfo.density.density, FluidMapStyles.Explore, false)
    assertNotNull(FluidMapSnapshots.cached(key))
  }

  @Test
  fun `il luogo del visore ha le coordinate di Mappe, la cartolina e Apri la mappa`() {
    var opened = 0
    val location: FluidViewerLocation = fluidViewerLocation(signoria, "Piazza della Signoria") { opened += 1 }
    assertEquals("43,76956° N, 11,25581° E", location.coordinates)
    assertNotNull(location.map)
    location.openMap!!.invoke()
    assertEquals(1, opened)
    show { location.map!!.invoke() }
    compose.onNodeWithTag(FluidMapTags.Postcard).assertIsDisplayed()
  }

  @Test
  fun `senza posizione e senza richiesta il tasto della posizione non c'e'`() {
    val state = FluidMapState(FluidMapCamera(signoria, 14.0))
    show { FluidMap(state, Modifier.fillMaxSize()) }
    assertEquals(0, compose.onAllNodesWithTag(FluidMapTags.Location).fetchSemanticsNodes().size)
    assertNull(state.userLocation)
  }

  /** La descrizione del nodo col tag: nell'albero unito (un tasto unisce la sua icona), o nel suo nodo se e' lui a dirla. */
  private fun description(tag: String): String {
    val merged = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
    return (merged ?: compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]).first()
  }

}
