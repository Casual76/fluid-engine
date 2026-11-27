package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.media.FluidFakeMediaSource
import dev.antigravity.fluidengine.media.FluidMediaPage
import dev.antigravity.fluidengine.media.FluidMediaPicker
import dev.antigravity.fluidengine.media.FluidMediaPickerState
import dev.antigravity.fluidengine.media.FluidMediaPickerTags
import dev.antigravity.fluidengine.media.FluidMediaQuery
import dev.antigravity.fluidengine.media.FluidMediaRefusal
import dev.antigravity.fluidengine.media.FluidMediaRequest
import dev.antigravity.fluidengine.media.FluidMediaSource
import dev.antigravity.fluidengine.media.FluidPickedMedia
import dev.antigravity.fluidengine.media.rememberFluidMediaPickerState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.IOException
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
 * R1-C · Il selettore di foto (F8.14) nei casi della review R1 (Muse 1 e 2):
 * - la scelta che sopravvive alla rotazione e alla morte del processo con gli elementi, non solo con le chiavi: scelte
 *   oltre la prima pagina, dopo la ricreazione il ✓ consegna tutto, nell'ordine;
 * - una chiave senza elemento (scelta dall'app, mai arrivata da una pagina) non fa un ✓ muto: esce dalla scelta e si dice;
 * - una sorgente che non risponde non sembra una libreria vuota: lo dice, e «Riprova» riparte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class R1cMediaPickerTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var state: FluidMediaPickerState
  private val results = mutableStateListOf<FluidPickedMedia>()

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  @Composable
  private fun Picker(sources: List<FluidMediaSource>, limit: Int) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalFluidGlassModalHostState provides host,
        LocalFluidFirstUseMemory provides remember { FluidFirstUseMemory.inMemory() },
      ) {
        Box(Modifier.fillMaxSize()) {
          state = rememberFluidMediaPickerState(sources, FluidMediaRequest.attachments(limit))
          FluidMediaPicker(state, onResult = { results.clear(); results += it }, onCancel = {}, offerSystemPicker = false)
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private fun confirm() {
    compose.onNodeWithTag(FluidMediaPickerTags.Confirm, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(800)
  }

  private fun shown(text: String): Boolean =
    compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun `la scelta da un album sopravvive alla rotazione e il conferma consegna tutto`() {
    // Il telefono e MyCloud Gallery, come in Pampa Mail: la griglia di partenza e' il telefono.
    val phone = FluidFakeMediaSource.sample(id = "device", label = "Telefono", count = 30)
    val cloud = FluidFakeMediaSource.sample(count = 60)
    val sources = listOf<FluidMediaSource>(phone, cloud)
    val restore = StateRestorationTester(compose)
    restore.setContent { Picker(sources, limit = 10) }
    advance(800)
    // Un album di MyCloud aperto e sfogliato: le sue foto arrivano solo dalla sua pagina.
    val album = state.feed(cloud, state.request.query(album = "vacanze"))
    compose.runOnIdle { album.loadMore() }
    advance(400)
    val root = state.feed(phone, state.request.query())
    assertTrue("le foto sono arrivate: ${root.items.size} e ${album.items.size}", root.items.size >= 5 && album.items.size >= 4)
    val chosen = listOf(root.items[3], album.items[1], root.items[0], album.items[3])
    compose.runOnIdle { chosen.forEach { state.selection.toggle(it.key) } }
    advance(200)
    assertEquals(chosen.map { it.key }, state.selectedItems.map { it.key })

    // La rotazione (o la morte del processo): si salva lo stato e si ricompone da capo, sulla prima pagina.
    // Con l'orologio fermo il tester non ricompone fra lo smontaggio e il rimontaggio: la ricreazione non avverrebbe.
    compose.mainClock.autoAdvance = true
    restore.emulateSavedInstanceStateRestore()
    compose.mainClock.autoAdvance = false
    advance(1200)
    val albumAgain = state.feed(cloud, state.request.query(album = "vacanze"))
    println("R1c selettore: dopo la ricreazione l'album ha ${albumAgain.items.size} foto, scelte ${state.selection.countIn(0)}")
    assertTrue("l'album non e' aperto: ${albumAgain.items.size}", albumAgain.items.isEmpty())
    assertEquals("la scelta ha ancora i suoi elementi", chosen.map { it.key }, state.selectedItems.map { it.key })
    confirm()
    assertEquals("il ✓ consegna tutto, nell'ordine dei tocchi", chosen.map { it.key }, results.map { it.item!!.key })
  }

  @Test
  fun `una chiave senza elemento non lascia un conferma muto`() {
    val cloud = FluidFakeMediaSource.sample(count = 20)
    compose.setContent { Picker(listOf(cloud), limit = 10) }
    advance(800)
    // Una chiave scelta dall'app che nessuna pagina ha mai portato.
    compose.runOnIdle { state.selection.toggle("mycloud/sparito") }
    advance(200)
    confirm()
    assertTrue("niente esce", results.isEmpty())
    assertEquals("l'avviso dice che non c'e' piu'", FluidMediaRefusal.NothingAvailable, state.notice?.refusal)
    assertEquals(1, state.notice?.skipped)
    assertEquals("la chiave morta esce dalla scelta", 0, state.selection.countIn(0))
  }

  /** Una sorgente il cui backup non risponde alla prima pagina finche' [failing] e' vero. */
  private class Flaky(private val inner: FluidFakeMediaSource) : FluidMediaSource by inner {
    @Volatile var failing = true
    var asked = 0
    override suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage {
      asked++
      if (failing) throw IOException("Il NAS non risponde")
      return inner.page(query, cursor, limit)
    }
  }

  @Test
  fun `una sorgente che non risponde lo dice e riprova`() {
    val flaky = Flaky(FluidFakeMediaSource.sample(count = 30))
    compose.setContent { Picker(listOf(flaky), limit = 10) }
    advance(1200)
    assertTrue("la prima pagina e' stata chiesta", flaky.asked >= 1)
    assertFalse("non sembra una libreria vuota", shown("Nessuna foto"))
    assertTrue("dice che la sorgente non risponde", shown("MyCloud Gallery non è disponibile"))
    flaky.failing = false
    compose.onAllNodes(hasText("Riprova"), useUnmergedTree = false).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    advance(1200)
    val feed = state.feed(flaky, state.request.query())
    assertEquals("riprovando arrivano le foto", 30, feed.items.size)
    assertFalse(shown("non è disponibile"))
  }
}
