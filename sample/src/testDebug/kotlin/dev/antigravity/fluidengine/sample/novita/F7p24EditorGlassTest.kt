package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.editor.FluidAdjustment
import dev.antigravity.fluidengine.editor.FluidEditorSource
import dev.antigravity.fluidengine.editor.FluidImageEditor
import dev.antigravity.fluidengine.editor.FluidImageEditorState
import dev.antigravity.fluidengine.editor.rememberFluidImageEditorState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
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
 * F7.24 · Mentre il righello dell'editor cambia l'esposizione a ogni fotogramma, i vetri della testata e della capsula
 * («Annulla», «Fine», le frecce, «•••», «Regola/Ritaglia») **non ricatturano** il loro sfondo. Sul Tab S9 lo facevano
 * tutti e cinque a ogni fotogramma (la traccia di F8.15d: cinque strati ridisegnati e ~19 passate di disegno a
 * fotogramma): la foto non aveva uno strato suo, e il suo ridisegno faceva riregistrare lo strato in cui sta anche la
 * sorgente del vetro (il fondo), che a ogni registrazione avvisa i vetri (`LayerBackdropNode`).
 *
 * Il vetro vero (verdetti degli shader accettati); il disegno a ogni passo lo chiede PixelCopy, come nelle catture.
 *
 * Attenzione: in Robolectric il ridisegno degli strati che sul tablet faceva ridisegnare i vetri **non si vede** (la
 * stessa prova senza gli strati nuovi da' ugualmente zero catture, provato il 26 novembre 2026). Questa prova tiene ferma
 * solo la parte delle catture; la riprova vera e' la traccia di Perfetto sul tablet (gli strati ridisegnati e le passate
 * di disegno a fotogramma, vedi il rapporto di F7.24).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class F7p24EditorGlassTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var state: FluidImageEditorState

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    // Le catture si contano solo con la diagnostica accesa.
    FluidGlassDiagnostics.enabled = true
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  /** Un fotogramma disegnato davvero (in Robolectric il disegno lo chiede PixelCopy). */
  private fun frame() {
    advance(16)
    val window = compose.activity.window
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    image.recycle()
  }

  @Test
  fun `l'esposizione che cambia a ogni fotogramma non fa ricatturare i vetri`() {
    val photo = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.viewer_flower)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            state = rememberFluidImageEditorState(FluidEditorSource.bitmap(photo))
            FluidImageEditor(state, onCancel = {}, onDone = {})
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    // La foto si legge su un filo vero.
    val until = System.currentTimeMillis() + 15_000
    while (!state.isReady && System.currentTimeMillis() < until) {
      advance(32)
      Thread.sleep(5)
    }
    assertTrue("la foto non si e' aperta", state.isReady)
    // I vetri catturano lo sfondo la prima volta (e qualche fotogramma dopo, mentre si posano).
    repeat(12) { frame() }
    val warm = FluidGlassDiagnostics.totalCaptures
    assertTrue("i vetri dell'editor non hanno mai catturato: $warm", warm > 0)
    // Il righello che gira: un valore nuovo a ogni fotogramma, come col dito.
    for (k in 1..20) {
      compose.runOnIdle { state.setAdjustment(FluidAdjustment.Exposure, k * 2f) }
      frame()
    }
    val during = FluidGlassDiagnostics.totalCaptures - warm
    println("F7p24 vetri: $warm catture per partire, $during in 20 fotogrammi col righello")
    assertEquals("ricatture dei vetri mentre l'esposizione cambia", 0L, during)
  }
}
