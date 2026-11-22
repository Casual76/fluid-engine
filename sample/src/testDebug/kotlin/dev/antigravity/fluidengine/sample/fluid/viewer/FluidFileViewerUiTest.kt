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
import androidx.compose.ui.graphics.luminance
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
  private var videoSession: FluidMediaSession? = null

  private class VideoSession(
    override val videoAspectRatio: Float = 1.5f,
    private val position: () -> Long = { 100L },
  ) : FluidMediaSession, FluidVideoGeometry {
    override var playing by mutableStateOf(true)
      private set
    override val positionMillis get() = position()
    override val durationMillis = 30000L
    override val canSeek = true
    override fun play() { playing = true }
    override fun pause() { playing = false }
    var sought: Long? = null
      private set
    override fun seekTo(positionMillis: Long) { sought = positionMillis }
  }
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
                val body: @Composable () -> Unit = { Photo(Modifier.fillMaxSize().testTag("file-$i")) }
                videoSession?.let { FluidViewerContent.Media(it, videoSurface = body) } ?: FluidViewerContent.Image(1.5f, body)
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

  @Test fun inwardPinchBelowThresholdReturnsAndPastThresholdClosesIntoGrid() {
    show()
    fun pinch(end: Float) {
      compose.onRoot().performTouchInput {
        down(0, center - Offset(100f, 0f))
        down(1, center + Offset(100f, 0f))
        for (step in 1..8) {
          val radius = 100f + (end - 100f) * step / 8f
          updatePointerTo(0, center - Offset(radius, 0f))
          updatePointerTo(1, center + Offset(radius, 0f))
          move(24L)
        }
        advanceEventTime(160L)
        up(1)
        up(0)
      }
      compose.waitForIdle()
    }
    pinch(91f)
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    pinch(68f)
    compose.onNodeWithTag("thumb-1").assertIsDisplayed()
    assertEquals("grid", nav.currentDestination?.route)
  }

  @Test fun mediaChromeUsesDarkGlassAndDocumentsRestoreTheSystemPalette() {
    var kind by mutableIntStateOf(0)
    var appearance: Pair<Boolean, Boolean>? = null
    val session = VideoSession()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.LIGHT) }) {
        FluidFileViewer(1, content = {
          when (kind) {
            0 -> FluidViewerContent.Image(1.5f) { Box(Modifier.fillMaxSize()) }
            1 -> FluidViewerContent.Media(session, videoSurface = { Box(Modifier.fillMaxSize()) })
            else -> FluidViewerContent.Unknown(FluidFileInfo("note.pdf"))
          }
        }, toolbar = {
          item("palette", label = "Aspetto") {
            val side = LocalFluidSurfaceSide.current == true
            val dark = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < .5f
            SideEffect { appearance = side to dark }
            Text("A")
          }
        })
      }
    }
    compose.runOnIdle { assertEquals(true to true, appearance); kind = 1 }
    compose.runOnIdle { assertEquals(true to true, appearance); kind = 2 }
    compose.runOnIdle { assertEquals(false to false, appearance) }
  }

  @Test fun standaloneDragAndPinchOnlyDismissWithAnOptInCallback() {
    var enabled by mutableStateOf(false)
    var open by mutableStateOf(true)
    var calls = 0
    val close: () -> Unit = { calls++; open = false }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize().background(Color.Cyan)) {
          if (open) FluidFileViewer(1, content = {
            FluidViewerContent.Image(1.5f) { Photo(Modifier.fillMaxSize().testTag("standalone")) }
          }, onBack = {}, onDismissRequest = if (enabled) close else null)
        }
      }
    }
    fun squeeze() = compose.onRoot().performTouchInput {
      down(0, center - Offset(100f, 0f))
      down(1, center + Offset(100f, 0f))
      updatePointerTo(0, center - Offset(65f, 0f))
      updatePointerTo(1, center + Offset(65f, 0f))
      move(64)
      advanceEventTime(160)
      up(1)
      up(0)
    }
    squeeze()
    compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 220f)) }
    compose.onNodeWithTag("standalone").assertIsDisplayed()
    compose.runOnIdle { assertEquals(0, calls); enabled = true }
    compose.onNodeWithTag("standalone").assertIsDisplayed()
    compose.mainClock.autoAdvance = false
    compose.onRoot().performTouchInput {
      down(center)
      moveBy(Offset(0f, 48f))
      moveBy(Offset(0f, 172f))
    }
    compose.mainClock.advanceTimeByFrame()
    withWindow {
      val pixel = it.getPixel(10, it.height / 3)
      assertEquals(0, android.graphics.Color.red(pixel))
      assertTrue(android.graphics.Color.green(pixel) in 1..254)
    }
    compose.onRoot().performTouchInput { advanceEventTime(150); up() }
    assertEquals(0, calls)
    compose.mainClock.advanceTimeBy(1600)
    compose.mainClock.autoAdvance = true
    compose.onNodeWithTag("standalone").assertDoesNotExist()
    compose.runOnIdle { assertEquals(1, calls); open = true }
    compose.onNodeWithTag("standalone").assertIsDisplayed()
    squeeze()
    compose.onNodeWithTag("standalone").assertDoesNotExist()
    compose.runOnIdle { assertEquals(2, calls) }
  }

  @Test fun reducingZoomedVideoToOneKeepsTheRouteAndANewPinchClosesIt() {
    videoSession = VideoSession()
    show()
    val root = compose.onRoot()
    root.performTouchInput { doubleClick(Offset(width * .34f, height * .46f)) }
    val scale = { n: Int -> SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom $n%") }
    compose.onNode(scale(250)).assertExists()
    fun squeeze() = root.performTouchInput {
      val origin = Offset(centerX, height * .45f)
      down(0, origin - Offset(100f, 0f))
      down(1, origin + Offset(100f, 0f))
      for (step in 1..8) {
        val radius = 100f - 9f * step
        updatePointerTo(0, origin - Offset(radius, 0f))
        updatePointerTo(1, origin + Offset(radius, 0f))
        move(24L)
      }
      advanceEventTime(150L)
      up(1)
      up(0)
    }
    squeeze()
    compose.onNode(scale(100)).assertExists()
    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    squeeze()
    compose.onNodeWithTag("thumb-1").assertIsDisplayed()
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
    val session = VideoSession()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidFileViewer(1, content = { FluidViewerContent.Media(session, videoSurface = {
          Box(Modifier.fillMaxSize().testTag("video"))
        }) }, onBack = {}, info = { Text("Video") })
      }
    }
    compose.mainClock.advanceTimeBy(18000)
    compose.onNodeWithContentDescription("Informazioni").assertIsDisplayed()
    val pause = compose.onNodeWithContentDescription("Pausa")
    pause.performTouchInput { click() }
    compose.runOnIdle { assertFalse(session.playing) }
    compose.onNodeWithContentDescription("Riproduci").performTouchInput { click() }
    compose.runOnIdle { assertTrue(session.playing) }
    compose.onNodeWithContentDescription("Posizione di riproduzione").performTouchInput {
      swipe(Offset(width * .20f, centerY), Offset(width * .70f, centerY))
    }
    compose.runOnIdle { assertTrue((session.sought ?: 0L) in 1L..session.durationMillis) }
    compose.onNodeWithContentDescription("Informazioni").assertIsDisplayed()
    val video = compose.onNodeWithTag("video")
    video.performTouchInput { click(Offset(width * .23f, height * .42f)) }
    compose.mainClock.advanceTimeBy(18000)
    compose.onNodeWithContentDescription("Informazioni").assertDoesNotExist()
    pause.assertDoesNotExist()
    video.performTouchInput { click(Offset(width * .23f, height * .42f)) }
    compose.mainClock.advanceTimeBy(1200)
    compose.onNodeWithContentDescription("Informazioni").assertIsDisplayed()
    pause.assertIsDisplayed()
    compose.runOnIdle { assertTrue(session.playing) }
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

  private fun withWindow(action: (Bitmap) -> Unit) {
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
      action(bitmap)
    } finally {
      bitmap.recycle()
    }
  }

  private fun capture(file: File) = withWindow { bitmap ->
    file.parentFile?.mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    Unit
  }

  @Test fun windowBackdropArrivesLateAndRevealsGridOnFirstDragAndBack() {
    compose.setContent(scene)
    val folder = System.getenv("GLASS_F6C1_STILLS")?.let { File(it, "sfondo") }
    fun sample(name: String): Pair<Int, Int> {
      var result = 0 to 0
      withWindow { bitmap ->
        // Away from both toolbars. The floor of a black viewer is a positive control.
        val probe = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
        var black = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
          if (bitmap.getPixel(x, y) and 0x00ffffff == 0) black++
        }
        result = probe to black
        if (folder != null) {
          folder.mkdirs()
          File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
      }
      return result
    }
    val grid = sample("01-griglia")
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("thumb-1").performClick()
    compose.mainClock.advanceTimeBy(64)
    val early = sample("02-apertura")
    compose.mainClock.advanceTimeBy(1600)
    val opened = sample("03-aperto")
    assertEquals(android.graphics.Color.BLACK, opened.first)
    assertNotEquals(grid.first, opened.first)
    assertTrue("Early black samples ${early.second}, grid ${grid.second}, open ${opened.second}",
      early.second <= grid.second + 60 && opened.second > early.second + 2500)
    compose.onRoot().performTouchInput {
      down(center)
      moveBy(Offset(0f, 24f))
      moveBy(Offset(0f, 12f))
    }
    compose.mainClock.advanceTimeByFrame()
    fun light(color: Int) = android.graphics.Color.red(color)
    val first = light(sample("04-primo-trascinamento").first)
    compose.onRoot().performTouchInput { moveBy(Offset(0f, 90f)) }
    compose.mainClock.advanceTimeByFrame()
    val held = light(sample("04b-piu-in-basso").first)
    assertTrue("First $first, held $held", first < held && held < light(grid.first))
    compose.onRoot().performTouchInput { moveBy(Offset(0f, -90f)) }
    compose.mainClock.advanceTimeByFrame()
    assertTrue(light(sample("04c-inversione").first) < held)
    compose.onRoot().performTouchInput { advanceEventTime(200); up() }
    compose.mainClock.advanceTimeBy(1600)
    assertEquals(opened.first, sample("05-annullo").first)
    compose.runOnUiThread { nav.popBackStack() }
    compose.mainClock.advanceTimeBy(64)
    val backStart = light(sample("06-indietro").first)
    compose.mainClock.advanceTimeBy(64)
    val backMiddle = light(sample("07-dissolvenza").first)
    compose.mainClock.advanceTimeBy(240)
    assertTrue("Back $backStart, $backMiddle, grid ${light(grid.first)}", backStart in 1 until backMiddle && backMiddle <= light(grid.first))
    assertEquals(grid.first, sample("08-ritorno").first)
    compose.mainClock.autoAdvance = true
  }

  @Test fun videoPinchZoomKeepsTransportFixedLight() = videoZoom(false)
  @Test fun videoPinchZoomKeepsTransportFixedDark() = videoZoom(true)

  private fun videoZoom(night: Boolean) {
    val footage = System.getenv("GLASS_F6C1_VIDEO_INPUT")?.let(::File)
    val output = System.getenv("GLASS_F6C1_VIDEO_OUTPUT")?.let { File(it, if (night) "scuro" else "chiaro") }
    var frame by mutableIntStateOf(0)
    val session = VideoSession(16f / 9f) { frame * 1000L / 60L }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (night) ThemeMode.DARK else ThemeMode.LIGHT) }) {
        FluidFileViewer(1, content = { FluidViewerContent.Media(session, videoSurface = {
          val movieFrame = remember(frame) { footage?.let { BitmapFactory.decodeFile(File(it, "%03d.jpg".format(frame)).path).asImageBitmap() } }
          val surface = Modifier.fillMaxSize().testTag("film")
          if (movieFrame == null) Box(surface.background(Color.DarkGray))
          else Image(movieFrame, null, surface, contentScale = ContentScale.FillBounds)
        }) }, info = { Text("Video") })
      }
    }
    val root = compose.onRoot()
    val pause = compose.onNodeWithContentDescription("Pausa")
    val track = compose.onNodeWithContentDescription("Posizione di riproduzione")
    val pauseBounds = pause.fetchSemanticsNode().boundsInRoot
    val trackBounds = track.fetchSemanticsNode().boundsInRoot
    val movie = compose.onNodeWithTag("film")
    val original = movie.fetchSemanticsNode().boundsInRoot
    val frames = if (output == null) 12 else 84
    for (n in 0 until frames) {
      val start = if (output == null) 1 else 15
      val steps = if (output == null) 8 else 36
      if (n in start..start + steps) root.performTouchInput {
        val centerOfPinch = Offset(width * .35f, height * .47f)
        val gap = 28f + 42f * (n - start) / steps
        if (n == start) {
          down(0, centerOfPinch - Offset(gap, 0f))
          down(1, centerOfPinch + Offset(gap, 0f))
        } else {
          updatePointerTo(0, centerOfPinch - Offset(gap, 0f))
          updatePointerTo(1, centerOfPinch + Offset(gap, 0f))
          move(16L)
          if (n == start + steps) { up(1); up(0) }
        }
      }
      if (output != null) {
        compose.runOnIdle { frame = n }
        capture(File(output, "f%03d.png".format(n)))
      }
    }
    fun scale(percent: Int) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom $percent%")).assertExists()
    scale(250)
    val enlarged = movie.fetchSemanticsNode().boundsInRoot
    assertTrue(enlarged.height > original.height)
    assertEquals(pauseBounds, pause.fetchSemanticsNode().boundsInRoot)
    assertEquals(trackBounds, track.fetchSemanticsNode().boundsInRoot)
    root.performTouchInput { doubleClick(Offset(width * .23f, height * .46f)) }
    scale(100)
    pause.assertIsDisplayed()
    root.performTouchInput { doubleClick(Offset(width * .23f, height * .46f)) }
    scale(250)
    root.performTouchInput {
      val at = Offset(width * .35f, height * .47f)
      down(0, at - Offset(20f, 0f))
      down(1, at + Offset(20f, 0f))
      updatePointerTo(0, at - Offset(58f, 0f))
      updatePointerTo(1, at + Offset(58f, 0f))
      move()
      up(1)
      up(0)
    }
    scale(400)
    pause.assertIsDisplayed()
  }

  /** Optional lossless Android checkpoints alongside the 120 Hz motion-bench model. */
  @Test fun appearanceCheckpoints() {
    val folder = System.getenv("GLASS_F6C1_STILLS")?.let(::File)
    fun shot(name: String) { if (folder != null) capture(File(folder, "$name.png")) }
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
