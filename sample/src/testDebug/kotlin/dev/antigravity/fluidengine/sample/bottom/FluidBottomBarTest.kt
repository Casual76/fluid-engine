package dev.antigravity.fluidengine.sample.bottom

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidBottomBarTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  @Test fun aCenteredLooseCapsuleKeepsItsCenterWhileItsWidthTravels() {
    compose.mainClock.autoAdvance = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    try {
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          var wide by remember { mutableStateOf(false) }
          Box(Modifier.fillMaxSize()) {
            FluidFloatingCapsule(Modifier.align(Alignment.BottomCenter).testTag("capsula")) {
              FluidCapsuleAction(FluidIcons.Add, "Alterna", onClick = { wide = !wide })
              if (wide) FluidCapsuleAction(FluidIcons.Edit, "Modifica", onClick = {})
            }
          }
        }
      }
      advance(320)
      val capsule = compose.onNodeWithTag("capsula")
      val first = capsule.getBoundsInRoot()
      val firstWidth = first.right - first.left
      compose.onNodeWithContentDescription("Alterna").performClick()
      val widths = mutableListOf<androidx.compose.ui.unit.Dp>()
      repeat(40) {
        advance(16)
        val frame = capsule.getBoundsInRoot()
        assertEquals(((first.left + first.right) / 2).value, ((frame.left + frame.right) / 2).value, 0.6f)
        widths += frame.right - frame.left
      }
      advance(1_200)
      val last = capsule.getBoundsInRoot().let { it.right - it.left }
      assertEquals(52.dp, last - firstWidth)
      assertTrue("larghezze intermedie: $widths", widths.distinct().count { it > firstWidth && it < last } >= 3)
    } finally {
      FluidGlassDiagnostics.resetForTesting()
    }
  }

  @Test fun standaloneSeparatesTopAndBottomAndReleasesItsLastAction() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        var visible by remember { mutableStateOf(true) }
        FluidScreen(title = "Due sedi", toolbar = {
          button("alto", FluidIcons.Edit, "Alterna", { visible = !visible })
          if (visible) button("basso", FluidIcons.Add, "Nascondi", { visible = false }, FluidToolbarPlacement.BottomBar)
        }) {}
      }
    }
    advance(1_200)
    val upper = compose.onNodeWithContentDescription("Alterna")
    val lower = compose.onNodeWithContentDescription("Nascondi")
    val upperRest = upper.getBoundsInRoot()
    val lowerRest = lower.getBoundsInRoot()
    assertTrue(lowerRest.top > upperRest.bottom + 100.dp)
    assertTrue(lowerRest.bottom - lowerRest.top >= 48.dp)
    compose.onNodeWithContentDescription("Altro").assertDoesNotExist()
    lower.performClick()
    advance(1_200)
    lower.assertDoesNotExist()
    assertEquals(upperRest, upper.getBoundsInRoot())
    upper.performClick()
    advance(1_200)
    assertEquals(lowerRest, lower.getBoundsInRoot())
  }

  @Test fun bottomBarKeepsBothPageOwnersAndTheSamePushAndPopFrames() {
    val controllers = mutableListOf<NavHostController>()
    val clocks = mutableListOf<FluidPageHostState>()
    val wrongOwners = mutableListOf<String>()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        // Due host della stessa dimensione e sullo stesso orologio, uno con la barra F4B.1 esplicita.
        Box(Modifier.fillMaxSize()) {
          for (withBar in listOf(false, true)) {
            val nav = rememberFluidNavController()
            val clock = rememberFluidPageHostState()
            SideEffect {
              if (nav !in controllers) { controllers += nav; clocks += clock }
            }
            FluidNavHost(nav, "a", Modifier.fillMaxSize(), state = clock) {
              for (route in listOf("a", "b")) {
                composable(route) { entry ->
                  val owner = LocalViewModelStoreOwner.current
                  SideEffect { if (owner !== entry) wrongOwners += route }
                  FluidScreen(title = route, toolbar = if (withBar && route == "a") {
                    { group("azioni", FluidToolbarPlacement.BottomBar) {
                      button("foto", FluidIcons.Add, "Foto", onClick = {})
                    } }
                  } else null) {}
                }
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
    assertEquals(2, controllers.size)
    compose.mainClock.autoAdvance = false
    val frames = mutableListOf("movimento,frame,t_ms,fase_controllo,fase_barra,bit_controllo,bit_barra")
    fun compareFrames(expected: FluidPageMove) {
      val start = compose.mainClock.currentTime
      var movingFrames = 0
      repeat(120) { frame ->
        compose.mainClock.advanceTimeByFrame()
        val control = clocks[0]
        val bar = clocks[1]
        frames += listOf(expected, frame, compose.mainClock.currentTime - start, control.kind, bar.kind,
          control.progress.toRawBits(), bar.progress.toRawBits()).joinToString(",")
        assertEquals("fase $expected al frame $frame", control.kind, bar.kind)
        assertEquals("corsa $expected al frame $frame", control.progress.toRawBits(), bar.progress.toRawBits())
        if (bar.kind == expected) movingFrames++
      }
      assertTrue("la molla deve partire e arrivare a riposo", movingFrames in 2..119)
      assertEquals(FluidPageMove.None, clocks[1].kind)
      assertTrue("owner di pagina cambiati: $wrongOwners", wrongOwners.isEmpty())
    }
    compose.runOnUiThread { controllers.forEach { it.navigate("b") } }
    compareFrames(FluidPageMove.Push)
    compose.runOnUiThread { controllers.forEach { it.popBackStack() } }
    compareFrames(FluidPageMove.Pop)
    System.getenv("GLASS_F4B2_TRAVEL_CSV")?.let { path ->
      java.io.File(path).writeText(frames.joinToString("\n", postfix = "\n"))
    }
  }

  @Test fun theGalleryKeepsOneBottomActionInPlaceAcrossPushAndPop() {
    compose.mainClock.autoAdvance = false
    compose.setContent { FluidTheme(settings = remember { EngineSettings() }) { BottomBarPage {} } }
    advance(1_200)
    val plus = compose.onNodeWithContentDescription("Cambia gruppo")
    val rest = plus.getBoundsInRoot()
    compose.onNodeWithText("Separa il più").performClick()
    advance(1_200)
    compose.onNodeWithText("Riunisci i comandi").performClick()
    advance(1_200)
    compose.onNodeWithText("Apri la foto").performClick()
    advance(1_200)
    compose.onNodeWithContentDescription("Preferito").assertDoesNotExist()
    assertEquals(rest, plus.getBoundsInRoot())
    compose.onNodeWithContentDescription("Torna alla raccolta").performClick()
    advance(1_200)
    compose.onNodeWithContentDescription("Preferito").assertExists()
    assertEquals(rest, plus.getBoundsInRoot())
    compose.onNodeWithText("Espandi la capsula").performClick()
    advance(800)
    compose.onNodeWithContentDescription("Contrassegna").assertExists()
  }
}
