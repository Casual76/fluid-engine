package dev.antigravity.fluidengine.sample.novita

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidLiveBackground
import dev.antigravity.fluidengine.ui.fluid.FluidLiveBackgroundCanvas
import dev.antigravity.fluidengine.ui.fluid.FluidLiveBackgroundState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidLiveBackgroundState
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.11 · Gli sfondi vivi: il tempo avanza a 30 Hz e non a ogni fotogramma; si fermano con «Riduci movimento», col
 * risparmio energetico, con la batteria scarica e a pagina nascosta, e ripartono; dentro una FluidScreen il fondale si
 * muove davvero (dai pixel) e col movimento ridotto è fermo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class F8p11LiveBackgroundTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(millis: Long) {
    // Prima i messaggi del looper (i segnali di Android arrivano lì), poi le scritture di stato, poi il tempo.
    shadowOf(Looper.getMainLooper()).idle()
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeBy(millis)
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()
  }

  private fun canvas(style: FluidLiveBackground = FluidLiveBackground.Aurora, reduced: () -> Boolean = { false }): FluidLiveBackgroundState {
    lateinit var state: FluidLiveBackgroundState
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val policy = if (reduced()) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy.Default
        CompositionLocalProvider(LocalFluidMotionPolicy provides policy) {
          state = rememberFluidLiveBackgroundState()
          FluidLiveBackgroundCanvas(style, Modifier.fillMaxSize(), state = state)
        }
      }
    }
    advance(100)
    return state
  }

  @Test fun trentaVolteAlSecondoEFermoColMovimentoRidotto() {
    var reduced by mutableStateOf(false)
    val state = canvas { reduced }
    assertTrue(state.isRunning)
    val start = state.framesDrawn
    advance(1_000)
    val rate = state.framesDrawn - start
    assertTrue("30 Hz e non a ogni fotogramma: $rate istanti in un secondo", rate in 24L..36L)

    reduced = true
    advance(100)
    assertFalse(state.isRunning)
    val still = state.framesDrawn
    val before = capture("ridotto-1")
    advance(1_000)
    assertEquals("fermo: nessun istante nuovo", still, state.framesDrawn)
    assertTrue("fermo: lo stesso fotogramma", before.sameAs(capture("ridotto-2")))

    reduced = false
    advance(200)
    assertTrue(state.isRunning)
    assertTrue(state.framesDrawn > still)
  }

  @Test fun fermoColRisparmioEColBatteriaScarica() {
    val state = canvas(FluidLiveBackground.Stars)
    val app = compose.activity.application
    val power = shadowOf(app.getSystemService(PowerManager::class.java))

    power.setIsPowerSaveMode(true)
    app.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    advance(100)
    assertFalse("risparmio energetico", state.isRunning)
    power.setIsPowerSaveMode(false)
    app.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    advance(100)
    assertTrue(state.isRunning)

    app.sendBroadcast(Intent(Intent.ACTION_BATTERY_LOW))
    advance(100)
    assertFalse("batteria scarica", state.isRunning)
    app.sendBroadcast(Intent(Intent.ACTION_BATTERY_OKAY))
    advance(100)
    assertTrue(state.isRunning)
  }

  @Test fun fermoAPaginaNascosta() {
    val state = canvas(FluidLiveBackground.Waves)
    assertTrue(state.isRunning)
    compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    advance(100)
    assertFalse("l'Activity non è più in primo piano", state.isRunning)
    val still = state.framesDrawn
    advance(1_000)
    assertEquals(still, state.framesDrawn)
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    advance(100)
    assertTrue(state.isRunning)
  }

  @Test fun ilFondaleVivoDiUnaFluidScreen() {
    var reduced by mutableStateOf(false)
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy.Default
        CompositionLocalProvider(LocalFluidMotionPolicy provides policy) {
          FluidScreen(
            title = "Ti diamo il benvenuto",
            ambient = FluidAmbient(FluidHeroTone.Primary, FluidHeroMotif.Glow, live = FluidLiveBackground.Waves),
          ) {
            item { FluidListGroup(glass = true) { FluidListRow(title = "Una riga di vetro", subtitle = "sopra le onde") } }
          }
        }
      }
    }
    advance(200)
    val a = capture("fondale-1")
    advance(2_000)
    val b = capture("fondale-2")
    // Le onde stanno nella metà bassa: lì il fondale cambia.
    assertTrue("il fondale si muove", changed(a, b, top = a.height * 6 / 10) > 0.01f)
    reduced = true
    advance(200)
    val c = capture("fondale-3")
    advance(2_000)
    val d = capture("fondale-4")
    assertEquals("col movimento ridotto è fermo", 0f, changed(c, d, top = 0), 0f)
  }

  /** I tre sfondi in chiaro e in scuro, a 12 s (la posa ferma): opachi, e diversi dal fondo piatto. */
  @Test fun iTreSfondiInChiaroEInScuro() {
    var dark by mutableStateOf(false)
    var style by mutableStateOf(FluidLiveBackground.Aurora)
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val mode = if (dark) dev.antigravity.fluidengine.foundation.ThemeMode.DARK else dev.antigravity.fluidengine.foundation.ThemeMode.LIGHT
      FluidTheme(settings = EngineSettings(themeMode = mode)) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
          FluidLiveBackgroundCanvas(style, Modifier.fillMaxSize())
        }
      }
    }
    for (side in listOf(false, true)) for (s in listOf(FluidLiveBackground.Aurora, FluidLiveBackground.Waves, FluidLiveBackground.Stars)) {
      dark = side
      style = s
      advance(100)
      val image = capture("posa-$s-${if (side) "scuro" else "chiaro"}")
      assertEquals("opaco", 255, android.graphics.Color.alpha(image.getPixel(image.width / 2, image.height / 2)))
      val colors = HashSet<Int>()
      for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) colors += image.getPixel(x, y)
      assertTrue("$s: il fondo e almeno tre tinte (${colors.size})", colors.size >= 4)
    }
  }

  private fun changed(a: Bitmap, b: Bitmap, top: Int): Float {
    var diff = 0
    var total = 0
    for (y in top until a.height step 2) for (x in 0 until a.width step 2) {
      total++
      if (a.getPixel(x, y) != b.getPixel(x, y)) diff++
    }
    return diff.toFloat() / total
  }

  private fun capture(label: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(window, image, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    System.getenv("GLASS_F8P11_PIXELS")?.let { path ->
      val dir = File(path).apply { mkdirs() }
      File(dir, "sfondo-$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return image
  }
}
