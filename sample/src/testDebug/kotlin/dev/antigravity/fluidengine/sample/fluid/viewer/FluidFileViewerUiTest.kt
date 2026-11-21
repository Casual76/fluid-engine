package dev.antigravity.fluidengine.sample.fluid.viewer

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import android.net.Uri
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Info
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.navigation.*
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual destinations, pointer input, sheet portal and system back dispatcher on Android's JVM runtime. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FluidFileViewerUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private var count by mutableIntStateOf(3)
  private var thumbnails by mutableStateOf(true)
  private var dark = false
  private val plate by lazy {
    System.getenv("GLASS_F6C1_PHOTO")?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
  }
  @Composable private fun Photo(modifier: Modifier) {
    val picture = plate
    if (picture != null) Image(picture, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Color.DarkGray))
  }
  private var actions = 0
  private val scene: @Composable () -> Unit = {
    FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
      nav = rememberFluidNavController()
      pages = rememberFluidPageHostState()
      val ns = rememberFluidGlassNamespace()
      val modal = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides modal) {
        Box(Modifier.fillMaxSize()) {
          FluidNavHost(nav, "grid", state = pages) {
            composable("grid") {
              Column(Modifier.fillMaxWidth().padding(top = 60.dp, start = 14.dp, end = 14.dp)) {
                Text("Foto", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  repeat(if (thumbnails) count else 0) { i ->
                    Photo(Modifier.size(110.dp, 72.dp).testTag("thumb-$i").fluidViewerThumbnail(FluidViewerThumbnail(i, ns))
                      .clickable { nav.navigate("file/$i") })
                  }
                }
              }
            }
            fluidFileViewerRoute("file/{index}", listOf(navArgument("index") { type = NavType.IntType }),
              deepLinks = listOf(navDeepLink { uriPattern = "viewer://file/{index}" }),
              initial = { it.arguments!!.getInt("index") }, thumbnail = { _, i -> FluidViewerThumbnail(i, ns) }) {
              FluidFileViewer(count, content = { i ->
                FluidViewerContent.Image(1.5f) { Photo(Modifier.fillMaxSize().testTag("file-$i")) }
              }, onBack = { nav.popBackStack() }, tools = {
                tool("crop", FluidIcons.Info, "Ritaglia", actions = {
                  button("apply", FluidIcons.Info, "Applica", { actions++ })
                }) { Text("Editor $it") }
              }, info = { Column(Modifier.padding(16.dp)) {
                Text("Dettagli $it", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Text("Dune.jpg")
                Text("Immagine JPEG")
              } })
            }
          }
          FluidGlassModalHost(modal, backdrop)
        }
      }
    }
  }

  private fun show(i: Int = 1) {
    compose.setContent(scene)
    compose.onNodeWithTag("thumb-$i").performClick()
    compose.waitForIdle()
  }

  private fun back() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  @Test fun tapHidesAndRestoresChromeWithoutMovingFileOrStartingATimer() {
    show()
    val file = compose.onNodeWithTag("file-1")
    val bounds = file.fetchSemanticsNode().boundsInRoot
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    file.performTouchInput { click() }
    compose.mainClock.advanceTimeBy(1200)
    compose.onNodeWithContentDescription("Ritaglia").assertDoesNotExist()
    compose.mainClock.advanceTimeBy(15000)
    compose.onNodeWithContentDescription("Ritaglia").assertDoesNotExist()
    assertEquals(bounds, file.fetchSemanticsNode().boundsInRoot)
    file.performTouchInput { click() }
    compose.mainClock.advanceTimeBy(1200)
    compose.onNodeWithContentDescription("Ritaglia").assertIsDisplayed()
  }

  @Test fun swipeAndButtonOpenSameInfoAndBackReturnsToolCapsule() {
    show()
    compose.onNodeWithTag("file-1").performTouchInput { swipe(center, center - Offset(0f, 180f)) }
    compose.onNodeWithText("Dettagli 1").assertIsDisplayed()
    back()
    compose.onNodeWithContentDescription("Informazioni").performClick()
    compose.onNodeWithText("Dettagli 1").assertIsDisplayed()
    back()
    compose.onNodeWithContentDescription("Ritaglia").performClick()
    compose.onNodeWithText("Editor 1").assertIsDisplayed()
    compose.onNodeWithContentDescription("Applica").performClick()
    assertEquals(1, actions)
    val dispatcher = compose.activity.onBackPressedDispatcher
    compose.runOnUiThread {
      dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
      dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, .5f, BackEventCompat.EDGE_LEFT))
      dispatcher.dispatchOnBackCancelled()
    }
    compose.onNodeWithText("Editor 1").assertIsDisplayed()
    back()
    compose.onNodeWithContentDescription("Ritaglia").assertIsDisplayed()
    compose.onNodeWithContentDescription("Applica").assertDoesNotExist()
  }

  @Test fun pinchOwnsVerticalMotionThenPagerAndDownDismissTakeTheirTurns() {
    show(0)
    val first = compose.onNodeWithTag("file-0")
    first.performTouchInput {
      down(0, center - Offset(12f, 0f))
      down(1, center + Offset(12f, 0f))
      moveTo(0, center - Offset(50f, 0f))
      moveTo(1, center + Offset(50f, 0f))
      up(1)
      moveBy(0, Offset(0f, 90f))
      up(0)
    }
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 400%")).assertExists()
    compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, height * .43f)) }
    compose.onNodeWithText("1 di 3").assertIsDisplayed()
    compose.onRoot().performTouchInput { doubleClick(center) }
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%")).assertExists()
    compose.onRoot().performTouchInput { swipeLeft() }
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, height * .43f)) }
    compose.waitForIdle()
    assertEquals("grid", nav.currentDestination?.route)
  }

  @Test fun deepLinkThenPageChangeAndRecreationRestoreTheSettledFile() {
    compose.setContent(scene)
    compose.runOnIdle {
      nav.navigate(NavDeepLinkRequest.Builder.fromUri(Uri.parse("viewer://file/2")).build())
    }
    compose.onNodeWithText("3 di 3").assertIsDisplayed()
    compose.onRoot().performTouchInput { swipe(Offset(width * .2f, centerY), Offset(width * .9f, centerY)) }
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    compose.onNodeWithContentDescription("Ritaglia").performClick()
    compose.activityRule.scenario.recreate()
    compose.setContent(scene)
    compose.onNodeWithText("Editor 1").assertIsDisplayed()
    back()
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    back()
    assertEquals("grid", nav.currentDestination?.route)
  }

  @Test fun oneOrZeroFilesStillHaveAnExitAndNeverResolveAnInvalidPage() {
    count = 1
    show(0)
    compose.onNodeWithText("1 di 1").assertIsDisplayed()
    compose.runOnIdle { count = 0 }
    compose.onNodeWithText("Nessun file").assertIsDisplayed()
    back()
    assertEquals("grid", nav.currentDestination?.route)
  }

  @Test fun deepLinkCanArriveBeforeFileListLoads() {
    count = 0
    compose.setContent(scene)
    compose.runOnIdle { nav.navigate("file/2") }
    compose.onNodeWithText("Nessun file").assertIsDisplayed()
    compose.runOnIdle { count = 3 }
    compose.onNodeWithText("3 di 3").assertIsDisplayed()
  }

  @Test fun shortPullAndReversalReturnToViewerAndSlowPullOver110DpExitsWithoutThumbnail() {
    show(0)
    val screen = compose.onRoot()
    screen.performTouchInput {
      down(center)
      moveBy(Offset(0f, 55f), delayMillis = 140)
      moveBy(Offset(0f, -35f), delayMillis = 140)
      advanceEventTime(160)
      up()
    }
    compose.onNodeWithText("1 di 3").assertIsDisplayed()
    compose.runOnIdle { thumbnails = false }
    screen.performTouchInput {
      down(center)
      repeat(12) { moveBy(Offset(0f, 10f), delayMillis = 40) }
      advanceEventTime(160)
      up()
    }
    compose.waitForIdle()
    assertEquals("grid", nav.currentDestination?.route)
  }

  @Test fun playingVideoKeepsItsChromeUntilTheUserTaps() {
    val session = object : FluidMediaSession {
      override val playing = true
      override val positionMillis = 100L
      override val durationMillis = 30000L
      override val canSeek = true
      override fun play() {}
      override fun pause() {}
      override fun seekTo(positionMillis: Long) {}
    }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidFileViewer(1, content = { FluidViewerContent.Media(session, videoSurface = {
          Box(Modifier.fillMaxSize().testTag("video"))
        }) }, onBack = {}, info = { Text("Video") })
      }
    }
    compose.mainClock.advanceTimeBy(18000)
    compose.onNodeWithContentDescription("Informazioni").assertIsDisplayed()
    compose.onNodeWithTag("video").performTouchInput { click() }
    compose.mainClock.advanceTimeBy(18000)
    compose.onNodeWithContentDescription("Informazioni").assertDoesNotExist()
  }

  @Suppress("DEPRECATION")
  @Config(sdk = [28])
  @Test fun systemBarsFollowTapAndRestoreIndependentOriginalVisibility() {
    var opened by mutableStateOf(false)
    val decor = compose.activity.window.decorView
    val status = View.SYSTEM_UI_FLAG_FULLSCREEN
    val navigation = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    compose.setContent {
      if (opened) FluidFileViewer(1, content = {
        FluidViewerContent.Image { Box(Modifier.fillMaxSize().testTag("surface")) }
      }, onBack = { opened = false })
    }
    compose.runOnIdle {
      decor.systemUiVisibility = (decor.systemUiVisibility and status.inv()) or navigation
      opened = true
    }
    compose.onNodeWithTag("surface").assertIsDisplayed()
    compose.runOnIdle { assertEquals(0, decor.systemUiVisibility and (status or navigation)) }
    compose.onNodeWithTag("surface").performTouchInput { click() }
    compose.mainClock.advanceTimeBy(1200)
    compose.runOnIdle {
      assertEquals(status or navigation, decor.systemUiVisibility and (status or navigation))
      opened = false
    }
    compose.runOnIdle { assertEquals(navigation, decor.systemUiVisibility and (status or navigation)) }
  }

  @Test fun shellOwnsOneToolCapsuleAndReturnsItOnDone() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val chrome = rememberFluidChromeController()
        ProvideFluidChrome(chrome, 80.dp, remember { FluidScrollToTopBus() }) {
          Box(Modifier.fillMaxSize()) {
            FluidFileViewer(1, content = { FluidViewerContent.Image {} }, onBack = {}, tools = {
              tool("crop", FluidIcons.Info, "Ritaglia") { Text("Editor aperto") }
            })
            FluidFloatingTabBar("album", scrollConnection = rememberFluidFloatingTabBarScrollConnection(),
              modifier = Modifier.align(Alignment.BottomCenter).testTag("sede-shell"),
              backdrop = chrome.activeBackdrop.value ?: rememberGlassBackdrop()) {
              tab("album", { Text("Album") }, { Text("A") }, onClick = {})
            }
          }
        }
      }
    }
    val shell = hasAnyAncestor(hasTestTag("sede-shell"))
    compose.onNode(hasContentDescription("Ritaglia") and shell, useUnmergedTree = true).performClick()
    compose.onNodeWithText("Editor aperto").assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Fine").assertCountEquals(1)
    compose.onNode(hasContentDescription("Fine") and shell, useUnmergedTree = true).performClick()
    compose.onNodeWithContentDescription("Ritaglia").assertIsDisplayed()
  }

  /** Optional lossless Android checkpoints alongside the 120 Hz motion-bench model. */
  @Test fun appearanceCheckpoints() {
    val folder = System.getenv("GLASS_F6C1_STILLS")?.let(::File)
    fun shot(name: String) {
      if (folder == null) return
      folder.mkdirs()
      val window = compose.activity.window
      val view = window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      try {
        var result = PixelCopy.ERROR_UNKNOWN
        compose.runOnIdle {
          PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
            { result = it }, Handler(Looper.getMainLooper()))
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PixelCopy.SUCCESS, result)
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      } finally {
        bitmap.recycle()
      }
    }
    dark = true
    compose.setContent(scene)
    shot("01-miniature")
    compose.onNodeWithTag("thumb-1").performClick()
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    shot("02-visore")
    compose.onNodeWithTag("file-1").performTouchInput { click() }
    compose.mainClock.advanceTimeBy(1200)
    shot("03-solo-foto")
    compose.onNodeWithTag("file-1").performTouchInput { click() }
    compose.mainClock.advanceTimeBy(1200)
    compose.onNodeWithTag("file-1").performTouchInput { swipe(center, center - Offset(0f, 100f)) }
    compose.onNodeWithText("Dettagli 1").assertIsDisplayed()
    shot("04-info")
    back()
    compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, height * .43f)) }
    compose.onNodeWithTag("thumb-1").assertIsDisplayed()
    shot("05-ritorno")
  }

  @Config(qualifiers = "w1000dp-h800dp-mdpi")
  @Test fun largeWindowPresentsInfoBesideTheFile() {
    show()
    compose.onNodeWithContentDescription("Informazioni").performClick()
    val body = compose.onNodeWithText("Dettagli 1").fetchSemanticsNode().boundsInRoot
    val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
    assertTrue(body.left > window.center.x)
    back()
    compose.onNodeWithText("Dettagli 1").assertDoesNotExist()
  }
}
