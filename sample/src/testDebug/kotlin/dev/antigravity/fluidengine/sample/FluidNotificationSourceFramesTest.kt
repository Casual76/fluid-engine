@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationDelivery
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Percorsi dell'host e del modificatore reali. I fotogrammi a 120 Hz sono in FluidNotificationSourceTraceTest. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class FluidNotificationSourceFramesTest {
  @get:Rule val compose = createComposeRule()

  private val showSource = mutableStateOf(true)
  private val mountHost = mutableStateOf(true)
  private lateinit var source: FluidTransitionSource
  private lateinit var host: FluidNotificationHostState
  private lateinit var scope: CoroutineScope

  private fun advance(millis: Int) {
    repeat((millis + 15) / 16) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun install(hostMounted: Boolean = true) {
    mountHost.value = hostMounted
    compose.setContent {
      FluidTheme(EngineSettings()) {
        val namespace = rememberFluidGlassNamespace()
        source = remember(namespace) { FluidTransitionSource("send", namespace) }
        host = rememberFluidNotificationHostState()
        scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize()) {
          if (showSource.value) FluidGlassButton(
            "Sorgente",
            onClick = {},
            modifier = Modifier.align(Alignment.TopEnd).fluidTransitionSource("send", namespace, FluidCapsuleShape),
          )
          if (mountHost.value) FluidNotificationHost(host)
        }
      }
    }
    compose.runOnIdle { assertNotNull(source.resolve()) }
    compose.mainClock.autoAdvance = !hostMounted
  }

  @Test fun capturesAtPublicationBeforeTheQueuedSourceDetaches() {
    install(hostMounted = false)
    val original = compose.runOnIdle {
      val geometry = checkNotNull(source.resolve())
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        host.show(FluidNotification("source", "Seconda", "Entra dalla sorgente",
          durationMillis = 60_000, source = source))
      }
      geometry
    }
    compose.runOnIdle { showSource.value = false }
    compose.onNodeWithText("Sorgente").assertDoesNotExist()
    compose.runOnIdle {
      assertNull(source.resolve())
      mountHost.value = true
    }
    compose.mainClock.autoAdvance = false
    advance(700)
    compose.onNodeWithText("Seconda").assertIsDisplayed()
    compose.runOnUiThread {
      assertEquals(original, host.presentation.source?.initial)
      assertTrue(!source.isHidden)
      assertTrue(host.updateCurrent(host.currentNotification!!.copy(message = "Aggiornata", source = null)))
      assertEquals(original, host.presentation.source?.initial)
    }
    advance(420)
    compose.onNodeWithText("Aggiornata").assertIsDisplayed()
  }

  @Test fun sourceCanDisappearDuringTheFlightAndTheQueueStillAdvances() {
    install()
    var delivery: FluidNotificationDelivery? = null
    compose.runOnUiThread {
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        delivery = host.show(FluidNotification("source", "Dal tasto", "La partenza resta", source = source))
      }
    }
    advance(100)
    compose.runOnUiThread {
      assertTrue(source.isHidden)
      showSource.value = false
    }
    advance(650)
    compose.onNodeWithText("Dal tasto").assertIsDisplayed()
    compose.runOnUiThread {
      assertEquals(FluidNotificationDelivery.Presented, delivery)
      assertNull(source.resolve())
      host.dismissCurrent()
      scope.launch { host.show(FluidNotification("after", "Dopo", "La coda riparte")) }
    }
    advance(950)
    compose.onNodeWithText("Dopo").assertIsDisplayed()
    compose.runOnUiThread { assertTrue(!source.isHidden) }
  }

  @Test fun replacementWithTheSameSourceStartsItsOwnJourney() {
    install()
    fun publish(id: String, title: String) {
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        host.show(FluidNotification.undo(title, 4, {}, source = source, group = "massa", id = id))
      }
    }
    compose.runOnUiThread { publish("old", "Prima modifica") }
    advance(650)
    compose.onNodeWithText("Prima modifica").assertIsDisplayed()
    compose.runOnUiThread { publish("new", "Due modifiche") }
    advance(650)
    compose.onNodeWithText("Prima modifica").assertDoesNotExist()
    compose.onNodeWithText("Due modifiche").assertIsDisplayed()
    compose.onNodeWithText("Annulla").assertIsDisplayed()
  }

}
