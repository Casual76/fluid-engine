package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.editor.FluidEditorSource
import dev.antigravity.fluidengine.editor.FluidImageEditor
import dev.antigravity.fluidengine.editor.FluidImageEditorState
import dev.antigravity.fluidengine.editor.FluidImageEditorTags
import dev.antigravity.fluidengine.editor.rememberFluidImageEditorState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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
 * F7.24b · Dopo un gesto sul righello, a dito fermo e righello posato, **nessuno chiede più fotogrammi**: il Recomposer
 * non ha lavoro in sospeso. Sul Tab S9 la pagina disegnava a 120 fotogrammi al secondo anche col dito fermo, dopo il
 * righello (editor e scena di F8.2) e non dopo la ruota. Tre casi: il righello da solo, la scena «Raddrizza una foto» di
 * F8.2, l'editor delle foto.
 *
 * Attenzione: la causa vera era la coda del lancio (2,4 s sotto la tacca dopo un rilascio veloce), che il dito finto di
 * Robolectric, lento, non produce: questa prova era verde anche prima della correzione. La tiene ferma la prova JVM sul
 * motore (`FluidRulerDriverSettleTest` in `engine-ui`); questa resta di guardia contro un'animazione che non finisce mai.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class F7p24bRulerIdleTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private var degrees by mutableFloatStateOf(0f)

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
    advance(800)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /** Il gesto del tablet: avanti 160 px in 1200 ms e indietro, col dito vero, poi il rilascio. */
  private fun pan(tag: String) {
    val b = bounds(tag)
    val finger = MaterialFinger(view)
    var at = Offset(b.center.x + 80f, b.center.y)
    finger.down(at)
    advance(16)
    repeat(75) {
      at += Offset(-160f / 75f, 0f)
      finger.move(at)
      advance(16)
    }
    repeat(75) {
      at += Offset(160f / 75f, 0f)
      finger.move(at)
      advance(16)
    }
    finger.up(at)
  }

  /** Quanti fotogrammi, in [ms] a dito fermo, trovano il Recomposer con del lavoro in sospeso. */
  private fun busyFrames(ms: Long): Int {
    var busy = 0
    var left = ms
    while (left > 0) {
      advance(16)
      if (Recomposer.runningRecomposers.value.any { it.hasPendingWork }) busy++
      left -= 16
    }
    return busy
  }

  private fun idleAfter(name: String, tag: String) {
    pan(tag)
    // La posa: lancio, molla, fine del gesto.
    advance(2_500)
    val busy = busyFrames(1_000)
    println("F7p24b $name: $busy fotogrammi con lavoro in sospeso nel secondo dopo la posa")
    assertEquals("$name: qualcosa chiede ancora fotogrammi a dito fermo", 0, busy)
  }

  @Test
  fun `il righello da solo si ferma dopo il gesto`() {
    show {
      FluidRulerDial(value = { degrees }, onValueChange = { degrees = it }, modifier = Modifier.testTag("ghiera"))
    }
    idleAfter("righello", "ghiera")
  }

  @Test
  fun `la scena Raddrizza di F8_2 si ferma dopo il gesto`() {
    val scene = F8p2Section.scenes.first { it.id == "ghiera-raddrizza" }
    show { scene.content({}) }
    idleAfter("F8.2", F8p2Tags.Dial)
  }

  @Test
  fun `l'editor si ferma dopo il righello dell'esposizione`() {
    val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(android.graphics.Color.rgb(120, 140, 90)) }
    lateinit var state: FluidImageEditorState
    show {
      state = rememberFluidImageEditorState(FluidEditorSource.bitmap(photo), previewMaxSide = 400)
      FluidImageEditor(state, onCancel = {}, onDone = {})
    }
    val until = System.currentTimeMillis() + 15_000
    while (!state.isReady && System.currentTimeMillis() < until) {
      advance(32)
      Thread.sleep(5)
    }
    assertTrue("la foto non si e' aperta", state.isReady)
    advance(600)
    idleAfter("editor", FluidImageEditorTags.Ruler)
  }
}
