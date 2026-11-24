package dev.antigravity.fluidengine.sample.novita

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.FluidAddressPage
import dev.antigravity.fluidengine.foundation.FluidAddressParameters
import dev.antigravity.fluidengine.foundation.FluidAddressRequest
import dev.antigravity.fluidengine.foundation.FluidAddressTable
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.navigation.FluidAddressNavigation
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p12AddressMotionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController
  private lateinit var host: FluidPageHostState
  private val seen = mutableSetOf<String>()
  private val table = FluidAddressTable("app", listOf(FluidAddressPage<String, String>(
    String::class, "pagina", { it.segments.singleOrNull() }, { FluidAddressParameters(listOf(it)) },
    parent = { when (it) { "nota" -> "quaderno"; "home" -> null; else -> "home" } }, public = true,
  )))
  private val addresses = FluidAddressNavigation(table, { it }, "mancante", "accesso")
  private fun open(page: String) = FluidAddressRequest(table.addressOf(page))

  private fun show(scale: Float) {
    compose.setContent {
      FluidMotionPolicyProvider(FluidMotionPolicy(scale)) {
        nav = rememberFluidNavController()
        host = rememberFluidPageHostState()
        FluidNavHost(nav, "home", addresses, open("nota"), Modifier.fillMaxSize(), state = host) {
          for (page in listOf("home", "quaderno", "nota", "altra")) composable(page) {
            SideEffect { seen.add(page) }
            Text(page)
          }
        }
      }
    }
    compose.waitForIdle()
    assertEquals("nota", nav.currentDestination?.route)
    assertFalse("la home è passata davanti all'avvio", "home" in seen)
    assertEquals(FluidPageMove.None, host.kind)
  }

  @Test fun coldShowsTheDestinationAndWarmUsesTheNormalPush() {
    show(1f)
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { addresses.open(nav, open("altra")) }
    repeat(3) { compose.mainClock.advanceTimeByFrame() }
    assertEquals(FluidPageMove.Push, host.kind)
    assertTrue(host.progress in 0f..1f)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(FluidPageMove.None, host.kind)
    assertEquals("altra", nav.currentDestination?.route)
  }

  @Test fun reducedMotionReachesTheSameDestinationAndBackParent() {
    show(0f)
    compose.runOnIdle { addresses.open(nav, open("altra")) }
    compose.waitForIdle()
    assertEquals(FluidPageMove.None, host.kind)
    compose.runOnIdle { nav.popBackStack() }
    compose.waitForIdle()
    assertEquals("home", nav.currentDestination?.route)
  }
}
