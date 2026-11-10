package dev.antigravity.fluidengine.sample

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Censimento dei canali di rete: un documento minimo per canale, e un socket locale che conta le connessioni.
 *
 * La prova guarda i socket e non `onRequest`, perche' il difetto da escludere e' proprio il traffico che
 * `onRequest` non vede. Nel log `FluidWebCensus` resta, per ogni canale, anche che cosa e' arrivato al gestore.
 */
@RunWith(Parameterized::class)
class FluidWebContentNetworkCensusTest(private val name: String) {
  companion object {
    @JvmStatic @Parameterized.Parameters(name = "{0}")
    fun channels(): List<Array<Any>> = webNetworkCensus().forThisRun().map { arrayOf<Any>(it.name) }
  }

  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun opensNoNativeSocket() {
    val channel = webNetworkCensus().single { it.name == name }
    val state = FluidWebContentState()
    val requests = CopyOnWriteArrayList<String>()
    val links = CopyOnWriteArrayList<String>()
    SocketWatch().use { watch ->
      val html = channel.html(watch.target)
      compose.setContent {
        FluidTheme(EngineSettings(), systemBars = false) {
          FluidWebContent(html, { links += it.url }, state = state, modifier = Modifier.fillMaxWidth().testTag("web"),
            sizing = FluidWebSizing.Viewport(400.dp), placeholder = {}, onLinkLongPress = { links += "long:${it.url}" },
            onRequest = { requests += it.url.toString(); FluidWebResponse.Block })
        }
      }
      compose.waitUntil(20_000) { state.phase == FluidWebPhase.Ready || state.phase == FluidWebPhase.Error }
      assertEquals("${state.failure}", FluidWebPhase.Ready, state.phase)
      when (channel.gesture) {
        WebGesture.None -> Unit
        WebGesture.Tap -> compose.onNodeWithTag("web").performTouchInput { click(Offset(80f, 50f)) }
        WebGesture.LongPress -> compose.onNodeWithTag("web").performTouchInput { longClick(Offset(80f, 50f), durationMillis = 900) }
      }
      watch.settle(1800)
      val toTarget = requests.filter { watch.target.authority in it }
      Log.i("FluidWebCensus", "canale=$name socket=${watch.connections.size} primiByte=${watch.connections} " +
        "onRequestVersoIlBersaglio=${toTarget.size} onRequest=$requests link=$links")
      assertTrue("$name ha aperto ${watch.connections.size} connessioni native (primi byte ${watch.connections}); " +
        "onRequest=$requests", watch.connections.isEmpty())
    }
  }
}
