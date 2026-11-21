package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageDismiss
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.fluidOpenGesture
import dev.antigravity.fluidengine.ui.page.fluidOpenGestureHost
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class FluidVerticalPageInputTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private lateinit var list: LazyListState
  private var opens = 0

  private fun show() {
    compose.setContent(content)
    compose.waitForIdle()
  }

  private val content: @Composable () -> Unit = {
      pages = rememberFluidPageHostState()
      nav = rememberFluidNavController()
      val ns = rememberFluidGlassNamespace()
      val source = remember(ns) { FluidTransitionSource("accessorio", ns) }
      FluidNavHost(nav, "raccolta", state = pages) {
        composable("raccolta") {
          val open = { opens++; nav.navigate("lettore") }
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height(64.dp).testTag("accessorio")
              .fluidTransitionSource("accessorio", ns).fluidOpenGesture(onOpen = open).clickable(onClick = open))
          }
        }
        composable("lettore", transition = FluidPageTransition.Expand, zoomSource = { source },
          dismiss = FluidPageDismiss.Down) {
          list = rememberLazyListState(initialFirstVisibleItemIndex = 2)
          LazyColumn(Modifier.fillMaxSize(), state = list) {
            items(40) { Box(Modifier.fillMaxWidth().height(48.dp)) }
          }
        }
      }
  }

  @Test fun laRicreazioneRiprendeLaMollaDallaQuotaSalvata() {
    show()
    compose.mainClock.autoAdvance = false
    val hit = compose.onNodeWithTag("accessorio").fetchSemanticsNode().boundsInRoot.center
    val root = compose.onRoot()
    root.performTouchInput { down(hit); repeat(8) { moveBy(Offset(0f, -32f)) } }
    repeat(3) { compose.mainClock.advanceTimeByFrame() }
    root.performTouchInput { up() }
    val atRelease = pages.progress
    assertTrue(atRelease in 0.1f..0.6f)
    val old = pages
    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    repeat(2) { compose.mainClock.advanceTimeByFrame() }
    compose.waitForIdle()
    assertTrue(old !== pages)
    assertEquals("lettore", nav.currentDestination?.route)
    assertTrue("riparte dalla quota di rilascio, con due vsync per la nuova posa: " + pages.progress,
      pages.isMoving && pages.progress >= atRelease - 0.01f && pages.progress < 0.9f)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertFalse(pages.isMoving)
    assertEquals(1, opens)
  }

  @Test fun laShellTieneIlDitoDopoCheLAccessorioEsceDallaComposizione() {
    var visible by mutableStateOf(true)
    compose.setContent {
      var stack by remember { mutableStateOf(listOf("raccolta")) }
      pages = rememberFluidPageHostState()
      val ns = rememberFluidGlassNamespace()
      val source = remember(ns) { FluidTransitionSource("accessorio", ns) }
      Box(Modifier.fillMaxSize().fluidOpenGestureHost(pages)) {
        FluidPageHost(stack, onBack = { stack = stack.dropLast(1) }, state = pages,
          transition = { FluidPageTransition.Expand }, zoomSource = { source }) {
          Box(Modifier.fillMaxSize())
        }
        if (visible) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
          .testTag("esterno").fluidTransitionSource("accessorio", ns)
          .fluidOpenGesture(state = pages) { opens++; stack = stack + "lettore" })
      }
    }
    val hit = compose.onNodeWithTag("esterno").fetchSemanticsNode().boundsInRoot.center
    val root = compose.onRoot()
    root.performTouchInput { down(hit); repeat(5) { moveBy(Offset(0f, -32f)) } }
    compose.waitForIdle()
    val initial = pages.progress
    assertTrue(pages.isInteractive)
    compose.runOnIdle { visible = false }
    root.performTouchInput { repeat(6) { moveBy(Offset(0f, -32f)) } }
    assertTrue(pages.isInteractive && pages.progress > initial)
    root.performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(1f, pages.fractionToward { it == "lettore" }, 0f)
    assertEquals(1, opens)
  }

  @Test fun ilDitoApreDallaSorgenteEPassaDallaListaAllaPaginaNelloStessoContatto() {
    show()
    val hit = compose.onNodeWithTag("accessorio").fetchSemanticsNode().boundsInRoot.center
    val root = compose.onRoot()
    root.performTouchInput { down(hit); repeat(5) { moveBy(Offset(0f, -32f)) } }
    compose.waitForIdle()
    assertTrue(pages.isInteractive)
    val reached = pages.progress
    root.performTouchInput { moveBy(Offset(0f, 48f)) }
    assertTrue(pages.progress < reached)
    root.performTouchInput { repeat(7) { moveBy(Offset(0f, -32f)) }; up() }
    compose.waitForIdle()
    assertEquals("lettore", nav.currentDestination?.route)
    assertEquals(1, opens)

    root.performTouchInput { down(Offset(340f, 200f)); repeat(4) { moveBy(Offset(0f, 32f)) } }
    compose.waitForIdle()
    assertTrue(list.canScrollBackward)
    assertFalse(pages.isInteractive)
    root.performTouchInput { repeat(6) { moveBy(Offset(0f, 32f)) } }
    compose.waitForIdle()
    assertFalse(list.canScrollBackward)
    assertTrue(pages.isInteractive)
    root.performTouchInput { repeat(8) { moveBy(Offset(0f, 32f)) }; up() }
    compose.waitForIdle()
    assertEquals("raccolta", nav.currentDestination?.route)
    assertEquals(1, opens)
  }
}
