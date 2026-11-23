package dev.antigravity.fluidengine.sample.fluid.viewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-mdpi")
class FluidDocumentJourneyUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private var showThumbnail by mutableStateOf(true)

  private fun install(scale: Float = 1f, document: FluidViewerContent? = null, freeze: Boolean = false) {
    compose.setContent {
      FluidTheme(remember { EngineSettings() }) {
        FluidMotionPolicyProvider(FluidMotionPolicy(scale)) {
          nav = rememberFluidNavController()
          pages = rememberFluidPageHostState()
          val thumb = FluidViewerThumbnail("report", rememberFluidGlassNamespace())
          FluidNavHost(nav, "files", state = pages) {
            composable("files") {
              Column(Modifier.padding(top = 100.dp, start = 24.dp)) {
                Text("Archivio")
                if (showThumbnail) Box(Modifier.size(90.dp, 65.dp).fluidViewerThumbnail(thumb)
                  .testTag("report-thumb").background(Color.Magenta).clickable { nav.navigate("report") })
              }
            }
            fluidFileViewerRoute("report", thumbnail = { _, _ -> thumb }) {
              FluidFileViewer(1, content = {
                document ?: FluidViewerContent.Custom {
                  Box(Modifier.fillMaxSize().testTag("report-body")) {
                    Text("Relazione", Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.CenterStart).size(80.dp, 260.dp).testTag("report-control")
                      .pointerInput(Unit) {
                        awaitEachGesture {
                          awaitFirstDown(requireUnconsumed = false)
                          do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                          } while (event.changes.any { it.pressed })
                        }
                      })
                  }
                }
              })
            }
          }
        }
      }
    }
    if (freeze) {
      compose.waitForIdle()
      assertTrue("The test needs a drawn and placed source", windowInk(android.graphics.Color.MAGENTA, "griglia") > 50)
      compose.mainClock.autoAdvance = false
    }
    compose.onNodeWithTag("report-thumb").performClick()
    if (document == null) compose.onNodeWithTag("report-body").assertIsDisplayed()
  }

  @Test fun childOwnsItsContactAndTheReaderClosesAFreePullPast110Dp() {
    install()
    compose.onNodeWithTag("report-control").performTouchInput {
      swipe(center - Offset(0f, 65f), center + Offset(0f, 80f), durationMillis = 480)
    }
    compose.onNodeWithTag("report-body").assertIsDisplayed()
    compose.onNodeWithTag("report-body").performTouchInput {
      swipe(center, center + Offset(0f, 95f), durationMillis = 420)
    }
    compose.onNodeWithTag("report-body").assertIsDisplayed()
    compose.onNodeWithTag("report-body").performTouchInput {
      swipe(center, center + Offset(0f, 155f), durationMillis = 480)
    }
    compose.onNodeWithTag("report-thumb").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w412dp-h860dp-mdpi")
  fun predictiveBackCancelsThenCommitsAndTheButtonCanCloseWithoutAThumbnail() {
    install()
    val dispatcher = compose.activity.onBackPressedDispatcher
    fun predictive(cancel: Boolean) {
      compose.runOnUiThread {
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(160f, 300f, .5f, BackEventCompat.EDGE_LEFT))
        if (cancel) dispatcher.dispatchOnBackCancelled() else dispatcher.onBackPressed()
      }
    }
    predictive(cancel = true)
    compose.onNodeWithTag("report-body").assertIsDisplayed()
    predictive(cancel = false)
    compose.onNodeWithTag("report-thumb").assertIsDisplayed()
    compose.onNodeWithTag("report-thumb").performClick()
    compose.onNodeWithTag("report-body").assertIsDisplayed()
    compose.runOnIdle { showThumbnail = false }
    compose.onNodeWithContentDescription("Indietro").performClick()
    compose.onNodeWithText("Archivio").assertIsDisplayed()
  }

  @Test fun zeroMotionStillReturnsToTheFiles() {
    install(scale = 0f)
    compose.onNodeWithContentDescription("Indietro").performClick()
    compose.onNodeWithTag("report-thumb").assertIsDisplayed()
  }

  @Test fun thumbnailStaysOnEachSampledFrameUntilTheFirstPageCanDraw() {
    val firstPage = CompletableDeferred<Unit>()
    val source = object : FluidPageSource {
      override val count = 1
      override suspend fun size(index: Int) = IntSize(250, 360)
      override suspend fun render(index: Int, target: IntSize) = firstPage.await().let {
        Bitmap.createBitmap(250, 360, Bitmap.Config.ARGB_8888).apply {
          eraseColor(android.graphics.Color.GREEN)
        }.asImageBitmap()
      }
    }
    install(document = FluidViewerContent.Pages(source), freeze = true)
    try {
      for (ms in 48..720 step 48) {
        compose.mainClock.advanceTimeBy(48)
        val ink = windowInk(android.graphics.Color.MAGENTA, "attesa-$ms")
        assertTrue("At ${ms}ms the waiting frame lost the thumbnail (${pages.kind}, ${pages.progress}, $ink samples)",
          ink > 50)
        if (ms >= 600) {
          val view = compose.activity.window.decorView
          assertTrue("The settled viewer must still show the enlarged thumbnail", ink > view.width * view.height / 18)
        }
      }
      firstPage.complete(Unit)
      compose.mainClock.advanceTimeBy(550)
      assertTrue("The first decoded page never replaced the thumbnail", windowInk(android.graphics.Color.GREEN, "pagina") > 50)
      assertEquals("The thumbnail lingered after the page became readable", 0, windowInk(android.graphics.Color.MAGENTA))
    } finally {
      firstPage.cancel()
      compose.mainClock.autoAdvance = true
    }
  }

  private fun windowInk(color: Int, frame: String? = null): Int {
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
      var code = PixelCopy.ERROR_UNKNOWN
      compose.runOnIdle {
        PixelCopy.request(window, bitmap, { code = it }, Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PixelCopy.SUCCESS, code)
      if (frame != null) System.getenv("GLASS_VIEWER_V2_PIXELS")?.let { out ->
        File(out, "$frame.png").apply { parentFile?.mkdirs() }.outputStream().use {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
      }
      var count = 0
      for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
        val pixel = bitmap.getPixel(x, y)
        // The native layer can darken a saturated face during zoom. Neutral paper and
        // loading text remain well outside this band, even on the veiled page.
        val distance = maxOf(
          kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(color)),
          kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(color)),
          kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(color)),
        )
        if (distance <= 52) count++
      }
      return count
    } finally {
      bitmap.recycle()
    }
  }
}
