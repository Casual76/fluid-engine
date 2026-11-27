package dev.antigravity.fluidengine.map

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R3 · Le correzioni della review R3 su engine-map: i comandi col foglio in cima (5), i crediti col carattere grande (16)
 * e la posizione vecchia presentata come attuale (24).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidMapR3Test {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val px = 2.625f
  private val duomo = FluidGeoPoint(43.77310, 11.25600)
  private val signoria = FluidGeoPoint(43.76956, 11.25581)

  private fun show(content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMapEngine provides FluidSketchMapEngine()) {
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

  // --- 5 · col foglio in cima i comandi spariscono davvero --------------------------------------------------------------

  private fun locationNodes() =
    compose.onAllNodesWithTag(FluidMapTags.Location, useUnmergedTree = true).fetchSemanticsNodes()

  @Test
  fun `col foglio in cima i comandi non si toccano e TalkBack non li legge`() {
    val state = FluidMapState(FluidMapCamera(duomo, 14.0))
    var requests = 0
    show { FluidMap(state, Modifier.fillMaxSize(), onLocationRequest = { requests += 1 }) }
    val low = locationNodes().single().boundsInRoot
    // Il foglio al detent alto: copre quasi tutta la mappa (oltre il 60 %, dove i comandi spariscono).
    val sheet = 650f
    state.bottomObscured = sheet.dp
    settle(1_200)
    assertTrue("TalkBack non deve trovare il tasto della posizione: ${locationNodes().size}", locationNodes().isEmpty())
    assertTrue(compose.onAllNodesWithTag(FluidMapTags.Controls, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    // Il dito dove il tasto sarebbe (alzato col foglio) tocca la mappa, non il tasto invisibile.
    val where = Offset(low.center.x, low.center.y - sheet * px)
    compose.onRoot().performTouchInput { click(where) }
    settle(400)
    assertEquals("il tocco non chiede la posizione", 0, requests)
    // Il foglio torna giu': i comandi tornano.
    state.bottomObscured = 300.dp
    settle(1_200)
    assertEquals(1, locationNodes().size)
  }

  // --- 16 · i crediti col carattere grande di sistema -------------------------------------------------------------------

  private fun layoutOf(node: SemanticsNode): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
    return results.single()
  }

  private fun assertPostcardKeepsOpenStreetMap(scale: Float) {
    show {
      Column {
        FluidMapPostcard(signoria, Modifier.size(FluidMapPostcardDefaults.MinWidth, FluidMapPostcardDefaults.MinWidth), title = "Signoria")
        FluidMapPostcard(signoria, Modifier.size(156.dp, 88.dp), title = "Signoria")
      }
    }
    val postcards = compose.onAllNodesWithTag(FluidMapTags.Postcard, useUnmergedTree = true).fetchSemanticsNodes()
    val texts = compose.onAllNodes(hasText("OpenStreetMap", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals(2, texts.size)
    texts.forEachIndexed { i, node ->
      val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
      val layout = layoutOf(node)
      assertTrue("$i a $scale: «$text»", text.contains(FluidMapStyles.OpenFreeMapAttribution.compact))
      assertEquals("$i a $scale: «$text» su una riga", 1, layout.lineCount)
      assertFalse("$i a $scale: «$text» intera (larga ${layout.multiParagraph.width} su ${layout.size.width})", layout.hasVisualOverflow)
      val card = postcards[i].boundsInRoot
      val b = node.boundsInRoot
      assertTrue("$i a $scale: dentro la cartolina $b in $card", b.left >= card.left && b.right <= card.right + 0.5f && b.bottom <= card.bottom + 0.5f)
    }
  }

  @Test
  @Config(fontScale = 1.3f)
  fun `col carattere a 1,3 la cartolina piu' stretta tiene OpenStreetMap intero`() = assertPostcardKeepsOpenStreetMap(1.3f)

  @Test
  @Config(fontScale = 2f)
  fun `col carattere a 2 la cartolina piu' stretta tiene OpenStreetMap intero`() = assertPostcardKeepsOpenStreetMap(2f)

  @Test
  @Config(fontScale = 2f)
  fun `sulla mappa col carattere a 2 la scritta corta non sta in due righe - il caso del revisore`() {
    // La larghezza della scritta sulla mappa, in un telefono da 360 dp: i comandi accanto (16 + 48 + 8), 8 a sinistra,
    // il vetro con 8 per lato. (A 411 dp, il Galaxy S25, la scritta corta sta ancora in due righe, al limite.)
    var lines = 0
    show {
      val measurer = rememberTextMeasurer()
      val density = LocalDensity.current
      val width = with(density) { (360.dp - 8.dp - 72.dp - 16.dp).roundToPx() }
      val style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
      lines = measurer.measure(FluidMapStyles.OpenFreeMapAttribution.short, style, constraints = Constraints(maxWidth = width)).lineCount
    }
    assertTrue("righe: $lines", lines > 2)
  }

  /** La scritta che il vetro dei crediti sceglie a [phoneWidth], e quante righe prende. */
  private fun chipText(phoneWidth: androidx.compose.ui.unit.Dp): Pair<String, Int> {
    var chosen = "" to 0
    show {
      val measurer = rememberTextMeasurer()
      val density = LocalDensity.current
      val width = with(density) { (phoneWidth - 8.dp - 72.dp - 16.dp).roundToPx() }
      val style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
      val text = fluidMapAttributionText(FluidMapStyles.OpenFreeMapAttribution, width, style, measurer, density)
      chosen = text to measurer.measure(text, style, constraints = Constraints(maxWidth = width)).lineCount
    }
    return chosen
  }

  @Test
  @Config(fontScale = 2f)
  fun `sulla mappa col carattere a 2 resta OpenStreetMap intero, nella scritta compatta`() {
    val (text, lines) = chipText(360.dp)
    assertEquals(FluidMapStyles.OpenFreeMapAttribution.compact, text)
    assertTrue("righe: $lines", lines <= 2)
  }

  @Test
  @Config(fontScale = 2f)
  fun `col carattere a 2 TalkBack dice sempre i crediti interi`() {
    show { FluidMap(FluidMapState(FluidMapCamera(duomo, 14.0)), Modifier.fillMaxSize()) }
    assertEquals(
      listOf("Crediti della mappa: ${FluidMapStyles.OpenFreeMapAttribution.short}"),
      compose.onNodeWithTag(FluidMapTags.Attribution).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
    )
  }

  @Test
  @Config(fontScale = 1.3f)
  fun `sulla mappa col carattere a 1,3 la scritta corta ci sta ancora`() {
    val (text, lines) = chipText(360.dp)
    assertEquals(FluidMapStyles.OpenFreeMapAttribution.short, text)
    assertTrue("righe: $lines", lines <= 2)
  }

  // --- 24 · la posizione vecchia non entra come attuale -----------------------------------------------------------------

  private fun locationManager() = compose.activity.getSystemService(LocationManager::class.java)

  private fun prepareLocation(at: Location): FluidSystemLocation {
    shadowOf(compose.activity.application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val manager = locationManager()
    shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    val location = FluidSystemLocation(compose.activity)
    val provider = location.provider()!!
    shadowOf(manager).setProviderEnabled(provider, true)
    at.provider = provider
    shadowOf(manager).setLastKnownLocation(provider, at)
    return location
  }

  private fun fix(timeMillis: Long, elapsedNanos: Long = 0L) = Location(LocationManager.GPS_PROVIDER).apply {
    latitude = duomo.latitude
    longitude = duomo.longitude
    accuracy = 20f
    time = timeMillis
    elapsedRealtimeNanos = elapsedNanos
  }

  @Test
  fun `l'ultima posizione di ieri non entra nel flusso come se fosse adesso`() {
    val location = prepareLocation(fix(System.currentTimeMillis() - 24 * 3_600_000L))
    val first = runBlocking { withTimeoutOrNull(300) { location.updates().first() } }
    assertNull("una posizione di ieri: $first", first)
  }

  @Test
  fun `l'ultima posizione vecchia secondo l'orologio del telefono non entra`() {
    // L'eta' dal tempo del sistema che non torna indietro (elapsedRealtimeNanos), come la conta Android.

    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(2))
    val location = prepareLocation(fix(System.currentTimeMillis(), elapsedNanos = 1_000_000L))
    val first = runBlocking { withTimeoutOrNull(300) { location.updates().first() } }
    assertNull("due ore fa: $first", first)
  }

  @Test
  fun `l'ultima posizione di adesso entra subito, con il suo istante`() {
    val taken = System.currentTimeMillis()
    val location = prepareLocation(fix(taken, elapsedNanos = SystemClock.elapsedRealtimeNanos()))
    val first = runBlocking { withTimeoutOrNull(300) { location.updates().first() } }
    assertEquals(duomo.latitude, first!!.point.latitude, 1e-9)
    // L'istante arriva alla mappa e all'app: prima si perdeva in toFluid().
    assertEquals(taken, first.timeMillis)
    assertEquals(taken, location.last()!!.timeMillis)
  }
}
