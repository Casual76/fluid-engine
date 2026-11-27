package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.editor.FluidAdjustment
import dev.antigravity.fluidengine.editor.FluidEditorSource
import dev.antigravity.fluidengine.editor.FluidImageEdit
import dev.antigravity.fluidengine.editor.FluidImageEditor
import dev.antigravity.fluidengine.editor.FluidImageEditorState
import dev.antigravity.fluidengine.editor.rememberFluidImageEditorState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
 * R1-C · L'editor delle foto (F8.15) nei casi della review R1 (Muse 6 e 10): una seconda bitmap aperta nello stesso posto
 * parte dalla foto com'e', non dalla ricetta della prima; una foto che non si apre ha «Riprova».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class R1cEditorTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var state: FluidImageEditorState

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  /** Aspetta in tempo vero (la foto si legge su un filo vero) finche' [done] e' vero, al piu' 15 s. */
  private fun waitFor(done: () -> Boolean) {
    val until = System.currentTimeMillis() + 15_000
    while (!done() && System.currentTimeMillis() < until) {
      advance(32)
      Thread.sleep(5)
    }
    advance(200)
  }

  private fun photo(color: Int) = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

  @Test
  fun `una seconda bitmap nello stesso posto non eredita la ricetta della prima`() {
    var source by mutableStateOf(FluidEditorSource.bitmap(photo(0xFF3366CC.toInt())))
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        state = rememberFluidImageEditorState(source, previewMaxSide = 300)
      }
    }
    advance(400)
    compose.runOnIdle {
      state.rotateLeft()
      state.setAdjustment(FluidAdjustment.Exposure, 0.6f)
      state.commit()
    }
    advance(400)
    val first = state
    assertNotEquals("la prima foto e' modificata", FluidImageEdit.Original, first.edit)
    // L'app apre un'altra foto in memoria nello stesso editor («Modifica» sulla foto dopo).
    compose.runOnIdle { source = FluidEditorSource.bitmap(photo(0xFFCC6633.toInt())) }
    advance(400)
    assertTrue("uno stato nuovo per la foto nuova", state !== first)
    assertEquals("la foto nuova si apre com'e'", FluidImageEdit.Original, state.edit)
  }

  @Test
  fun `una foto che non si apre ha riprova`() {
    val file = File(compose.activity.cacheDir, "r1c-${System.nanoTime()}.png")
    file.delete()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        state = rememberFluidImageEditorState(FluidEditorSource.uri(Uri.fromFile(file)), previewMaxSide = 300)
        Box(Modifier.fillMaxSize()) { FluidImageEditor(state, onCancel = {}, onDone = {}) }
      }
    }
    waitFor { state.loadError != null }
    assertNotNull("la foto non c'e': errore", state.loadError)
    // Il file arriva (il permesso torna, la copia finisce): «Riprova» la apre senza uscire.
    file.outputStream().use { photo(0xFF33AA55.toInt()).compress(Bitmap.CompressFormat.PNG, 100, it) }
    compose.onAllNodes(hasText("Riprova"), useUnmergedTree = false).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    waitFor { state.isReady }
    assertTrue("riprovando la foto si apre", state.isReady)
    assertEquals(null, state.loadError)
  }
}
