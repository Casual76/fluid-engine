package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.media.FluidAttachmentPanel
import dev.antigravity.fluidengine.media.FluidAttachmentPanelTags
import dev.antigravity.fluidengine.media.FluidDeviceMediaSource
import dev.antigravity.fluidengine.media.FluidFakeMediaSource
import dev.antigravity.fluidengine.media.FluidMediaAlbum
import dev.antigravity.fluidengine.media.FluidMediaDelivery
import dev.antigravity.fluidengine.media.FluidMediaItem
import dev.antigravity.fluidengine.media.FluidMediaLabels
import dev.antigravity.fluidengine.media.FluidMediaPicker
import dev.antigravity.fluidengine.media.FluidMediaPickerTags
import dev.antigravity.fluidengine.media.FluidMediaProgress
import dev.antigravity.fluidengine.media.FluidMediaRefusal
import dev.antigravity.fluidengine.media.FluidMediaRequest
import dev.antigravity.fluidengine.media.FluidMediaSource
import dev.antigravity.fluidengine.media.rememberFluidMediaPickerState
import dev.antigravity.fluidengine.sample.material.MaterialFinger
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
 * R3 (review del 27/11/2026) · Il selettore e il pannello degli allegati (F8.14) nei casi della review R3, col dito vero:
 * - difetto 2c: gli album che non arrivano dicono «… non è disponibile» con «Riprova», invece di un gruppo vuoto;
 * - difetto 17: nel pannello una foto in consegna da una sorgente lenta non si allega due volte e conta già per il tetto,
 *   e una consegna rifiutata (o che fallisce) lo dice con l'avviso del selettore.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class R3m1MediaTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private val labels = FluidMediaLabels()

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

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidGlassModalHostState provides host,
          LocalFluidFirstUseMemory provides remember { FluidFirstUseMemory.inMemory() },
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(800)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun exists(tag: String): Boolean =
    compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun shown(text: String): Boolean =
    compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /** Un tocco svelto col dito vero: giù, 48 ms, su, e [after] ms prima del prossimo gesto. */
  private fun tap(at: Offset, after: Long = 400) {
    finger.down(at)
    advance(48)
    finger.up(at)
    advance(after)
  }

  private fun tapTag(tag: String, after: Long = 400) = tap(bounds(tag).center, after)

  // --- Difetto 2c: gli album ------------------------------------------------------------------------------------------

  /** Una sorgente i cui album non arrivano (il NAS spento) finché [failing] è vero. */
  private class AlbumsFlaky(private val inner: FluidFakeMediaSource) : FluidMediaSource by inner {
    @Volatile var failing = true
    var asked = 0
    override suspend fun albums(): List<FluidMediaAlbum> {
      asked++
      if (failing) throw IOException("Il NAS non risponde")
      return inner.albums()
    }
  }

  @Test
  fun `gli album che non arrivano lo dicono e riprovano`() {
    val flaky = AlbumsFlaky(FluidFakeMediaSource.sample(count = 30))
    show {
      val state = rememberFluidMediaPickerState(listOf(flaky), FluidMediaRequest.attachments(10))
      FluidMediaPicker(state, onResult = {}, onCancel = {}, offerSystemPicker = false)
    }
    val tabs = bounds(FluidMediaPickerTags.Tabs)
    tap(Offset(tabs.left + tabs.width * 0.75f, tabs.center.y))
    advance(400)
    assertTrue("la pagina degli album c'è", exists(FluidMediaPickerTags.Albums))
    assertTrue("gli album sono stati chiesti", flaky.asked >= 1)
    assertTrue("dice che la sorgente non risponde, invece di un gruppo vuoto", shown("MyCloud Gallery non è disponibile"))
    assertFalse(exists(FluidMediaPickerTags.album("mycloud", "vacanze")))
    flaky.failing = false
    compose.onAllNodes(hasText(labels.retry), useUnmergedTree = false).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    advance(800)
    assertTrue("riprovando arrivano", exists(FluidMediaPickerTags.album("mycloud", "vacanze")))
    assertFalse(shown("non è disponibile"))
  }

  // --- Difetto 17: il pannello ----------------------------------------------------------------------------------------

  private val attached = mutableStateListOf<String>()
  private var attaches = 0

  private fun panel(source: FluidMediaSource, maxAttachments: Int = Int.MAX_VALUE) = show {
    FluidAttachmentPanel(
      recents = source,
      sources = emptyList(),
      attached = attached,
      onAttach = {
        attaches++
        attached += it.item!!.key
      },
      onRemove = { attached.remove(it) },
      maxAttachments = maxAttachments,
    )
  }

  @Test
  fun `nel pannello una foto in consegna non si allega due volte e conta per il tetto`() {
    // Il NAS: ogni risposta arriva dopo 600 ms.
    val slow = FluidFakeMediaSource.sample(id = FluidDeviceMediaSource.SourceId, label = "Telefono", count = 12)
    panel(slow, maxAttachments = 1)
    advance(400)
    assertTrue(exists(FluidAttachmentPanelTags.recent("device/m001")))
    slow.latencyMillis = 600L
    // Tre tocchi svelti, tutti prima che la prima consegna risponda: la stessa foto due volte, poi un'altra.
    tapTag(FluidAttachmentPanelTags.recent("device/m001"), after = 32)
    tapTag(FluidAttachmentPanelTags.recent("device/m001"), after = 32)
    tapTag(FluidAttachmentPanelTags.recent("device/m003"), after = 32)
    advance(1_600)
    assertEquals("una consegna sola", 1, attaches)
    assertEquals("la foto in consegna contava già per il tetto di una", listOf("device/m001"), attached.toList())
  }

  /** Una sorgente che rifiuta ogni consegna (il backup non risponde), o che lancia se [throwing]. */
  private class Refusing(private val inner: FluidFakeMediaSource) : FluidMediaSource by inner {
    var throwing = false
    override suspend fun deliver(items: List<FluidMediaItem>, request: FluidMediaRequest, onProgress: (FluidMediaProgress) -> Unit): FluidMediaDelivery {
      if (throwing) throw IOException("Il provider è caduto")
      return FluidMediaDelivery.Refused(FluidMediaRefusal.Unreachable)
    }
  }

  @Test
  fun `nel pannello una consegna rifiutata lo dice con la frase del selettore`() {
    val refusing = Refusing(FluidFakeMediaSource.sample(id = FluidDeviceMediaSource.SourceId, label = "Telefono", count = 12))
    panel(refusing)
    tapTag(FluidAttachmentPanelTags.recent("device/m001"))
    advance(800)
    assertTrue("il rifiuto si dice", shown(labels.refusedUnreachable))
    assertTrue(attached.isEmpty())
    compose.onAllNodes(hasText(labels.ok), useUnmergedTree = false).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    advance(800)
    assertFalse(shown(labels.refusedUnreachable))
    // Una consegna che lancia non chiude l'app: lo dice anche lei.
    refusing.throwing = true
    tapTag(FluidAttachmentPanelTags.recent("device/m002"))
    advance(800)
    assertTrue("l'errore si dice", shown(labels.refusedFailed))
    assertTrue(attached.isEmpty())
  }
}
