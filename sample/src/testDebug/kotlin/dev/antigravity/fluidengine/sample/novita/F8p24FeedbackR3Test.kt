package dev.antigravity.fluidengine.sample.novita

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.diagnostics.FluidLog
import dev.antigravity.fluidengine.diagnostics.testing.FluidDiagnosticsTesting
import dev.antigravity.fluidengine.feedback.FluidFeedbackConfig
import dev.antigravity.fluidengine.feedback.FluidFeedbackFile
import dev.antigravity.fluidengine.feedback.FluidFeedbackHost
import dev.antigravity.fluidengine.feedback.FluidFeedbackState
import dev.antigravity.fluidengine.feedback.rememberFluidFeedbackState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R3 (review `sol-ai`, n. 12) · Lo screenshot di lavoro rimasto nella cache da un processo ucciso dopo averlo scritto si
 * cancella quando «Segnala un problema» si riapre; quello di questo processo resta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p24FeedbackR3Test {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var app: Application

  @Before fun start() {
    app = ApplicationProvider.getApplicationContext()
    FluidDiagnosticsTesting.reset()
    FluidLog.clear()
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidDiagnosticsTesting.reset()
    FluidLog.clear()
    FluidFeedbackFile.workDir(app).deleteRecursively()
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

  @Test
  fun segnala_loScreenshotDiUnProcessoMortoSiCancellaAllaRiapertura() {
    val work = FluidFeedbackFile.workDir(app)
    // Scritto da un processo di prima, ucciso prima che la bozza partisse o si dimenticasse.
    val orphan = File(work, "screenshot-1000.png").apply { writeBytes(byteArrayOf(1, 2, 3)); setLastModified(1_000_000L) }
    // Scritto da questo processo (per esempio da un'altra segnalazione ancora aperta): resta.
    val mine = File(work, "screenshot-2000.png").apply { writeBytes(byteArrayOf(4, 5, 6)) }
    lateinit var state: FluidFeedbackState
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            state = rememberFluidFeedbackState(FluidFeedbackConfig("Fluid Glass"), diagnostics = null)
            FluidFeedbackHost(state) { Text("Pagina") }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(200)
    state.open()
    // Le informazioni si raccolgono su Dispatchers.IO: si aspetta che ci siano, con un tetto.
    repeat(50) {
      if (state.info == null) {
        advance(64)
        Thread.sleep(10)
      }
    }
    advance(200)
    assertNotNull("il foglio ha raccolto le informazioni", state.info)
    assertFalse("lo screenshot del processo morto è rimasto nella cache", orphan.exists())
    assertTrue("lo screenshot di questo processo resta", mine.exists())
  }
}
