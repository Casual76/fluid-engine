package dev.antigravity.fluidengine.sample

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.catalog.webContentHostileSamples
import dev.antigravity.fluidengine.ui.fluid.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Misura esplorativa: include composizione e creazione WebView, non impone soglie da hardware reale. */
class FluidWebContentPerformanceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun firstPaintTypicalAndHugeDocuments() {
    val samples = webContentHostileSamples()
    var html by mutableStateOf("")
    val state = FluidWebContentState()
    var started = 0L
    var painted = 0L
    compose.setContent {
      FluidWebContent(html, {}, state = state, modifier = Modifier.fillMaxWidth(),
        sizing = FluidWebSizing.Viewport(500.dp), placeholder = {})
      LaunchedEffect(state.phase) {
        if (state.phase == FluidWebPhase.Ready) painted = SystemClock.elapsedRealtime()
      }
    }
    for (name in listOf("Newsletter larga", "Lungo e malformato", "Newsletter larga")) {
      compose.runOnIdle {
        painted = 0
        started = SystemClock.elapsedRealtime()
        Log.i("FluidWebTiming", "inizio campione=$name elapsed=$started caratteri=${samples.getValue(name).length}")
        html = samples.getValue(name)
      }
      compose.waitUntil(30_000) { painted > started || state.phase == FluidWebPhase.Error }
      assertEquals("$name: ${state.failure}", FluidWebPhase.Ready, state.phase)
      Log.i("FluidWebTiming", "prima pittura campione=$name elapsed=$painted durataMs=${painted - started}")
    }
  }

}
