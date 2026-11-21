package dev.antigravity.fluidengine.sample.bottom

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidTabSelectionHostTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val selection = FluidSelectionState<Int>()
  private val list = LazyListState(18, 13)
  private var tapped = 0
  private var navigated = 0

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    // Ogni campione attende layout e disegno anche quando non esportiamo le PNG.
    compose.waitForIdle()
  }

  private fun saveFrame(name: String, measureStandalone: Boolean = false): Int {
    val directory = System.getenv("GLASS_F6B2_UI_DIR")
    if (directory == null && !measureStandalone) return 0
    val bounds = compose.onNodeWithTag("navigazione", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
    val rect = android.graphics.Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
      bounds.right.roundToInt(), bounds.bottom.roundToInt())
    val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, rect, bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    if (directory != null) {
      File(directory).mkdirs()
      File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    // Il cuore è scuro sul vetro chiaro. L'area interna esclude bordo, ombra e pagina.
    val ink = if (measureStandalone) {
      val side = bitmap.height
      val margin = side / 8
      val width = side - 2 * margin
      val pixels = IntArray(width * width)
      bitmap.getPixels(pixels, 0, width, bitmap.width - side + margin, margin, width, width)
      pixels.count { android.graphics.Color.red(it) < 120 && android.graphics.Color.green(it) < 120 &&
        android.graphics.Color.blue(it) < 120 }
    } else 0
    bitmap.recycle()
    return ink
  }

  private fun setTabs(actions: FluidToolbarScope.() -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val chrome = rememberFluidChromeController()
        ProvideFluidChrome(chrome, 80.dp, remember { FluidScrollToTopBus() }) {
          Box(Modifier.fillMaxSize()) {
            FluidScreen(title = "Fotografie", listState = list, toolbar = {
              if (selection.isActive) selection(selection, 1, null, {}, actions = actions)
              else selectButton({ selection.select(7) })
            }) { items(80) { Text("Foto " + it, Modifier.height(56.dp)) } }
            FluidFloatingTabBar(selectedTabKey = "album", scrollConnection = rememberFluidFloatingTabBarScrollConnection(),
              modifier = Modifier.align(Alignment.BottomCenter).testTag("navigazione"),
              backdrop = chrome.activeBackdrop.value ?: rememberGlassBackdrop()) {
              tab("library", { Text("Libreria") }, { FluidIcon(FluidIcons.Home, null) }, { navigated++ })
              tab("album", { Text("Album") }, { FluidIcon(FluidIcons.GridView, null) },
                badge = FluidTabBadge(3, "Tre novità"), onClick = { navigated++ })
              standaloneTab("star", { FluidIcon(FluidIcons.Favorite, "Preferito") }, FluidTabRole.Prominent, { navigated++ })
            }
          }
        }
      }
    }
    advance(800)
  }

  @Test fun selectionKeepsRouteBadgeAndScrollAndBackWaitsForRelease() {
    setTabs(FluidSelectionActions.gallery({ tapped++ }, {}, onFavorite = {}, onHide = {}))
    val album = compose.onNodeWithText("Album")
    val before = album.getBoundsInRoot()
    val position = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
    val initialInk = saveFrame("00-schede", measureStandalone = true)
    assertTrue(initialInk > 0)
    compose.onNodeWithContentDescription("Seleziona").performClick()
    for (i in 1..8) { advance(64); saveFrame("entra-$i") }
    advance(288)
    saveFrame("09-azioni")
    album.assertDoesNotExist()
    val share = compose.onNodeWithContentDescription("Condividi")
    val more = compose.onNodeWithContentDescription("Altro")
    val trash = compose.onNodeWithContentDescription("Elimina")
    val shareBounds = share.getBoundsInRoot()
    assertTrue(shareBounds.top > 400.dp)
    assertTrue(trash.getBoundsInRoot().right <= more.getBoundsInRoot().left)
    share.performClick()
    assertEquals(1, tapped)
    val back = compose.activity.onBackPressedDispatcher
    fun gesture() {
      compose.runOnUiThread {
        back.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
        back.dispatchOnBackProgressed(BackEventCompat(200f, 300f, .7f, BackEventCompat.EDGE_LEFT))
      }
      advance(160)
      assertEquals(shareBounds, share.getBoundsInRoot())
    }
    gesture()
    compose.runOnUiThread { back.dispatchOnBackCancelled() }
    advance(640)
    assertEquals(shareBounds, share.getBoundsInRoot())
    gesture()
    compose.runOnUiThread { back.onBackPressed() }
    compose.runOnIdle { assertFalse("Il back confermato deve chiudere la selezione", selection.isActive) }
    for (i in 1..8) { advance(64); saveFrame("esce-$i") }
    advance(288)
    val returnedInk = saveFrame("10-ritorno", measureStandalone = true)
    assertTrue("Inchiostro del tondo: $initialInk prima, $returnedInk al ritorno",
      kotlin.math.abs(returnedInk - initialInk) <= initialInk / 20)
    share.assertDoesNotExist()
    assertEquals(before, album.getBoundsInRoot())
    assertEquals("Tre novità", album.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
    assertEquals(0, navigated)
  }

  @Test fun deleteOnlyMergesTheCircleAndDoneReturnsItsInk() {
    setTabs(FluidSelectionActions.deleteOnly({ tapped++ }))
    val initialInk = saveFrame("solo-elimina-prima", measureStandalone = true)
    compose.onNodeWithContentDescription("Seleziona").performClick()
    advance(800)
    val host = compose.onNodeWithTag("navigazione", useUnmergedTree = true).getBoundsInRoot()
    val delete = compose.onNodeWithContentDescription("Elimina")
    val position = delete.getBoundsInRoot()
    assertEquals((host.left + host.right).value / 2f, (position.left + position.right).value / 2f, 1f)
    compose.onNodeWithContentDescription("Altro").assertDoesNotExist()
    saveFrame("solo-elimina-azioni")
    delete.performClick()
    assertEquals(1, tapped)
    compose.onNodeWithContentDescription("Fine").performClick()
    advance(800)
    val returnedInk = saveFrame("solo-elimina-ritorno", measureStandalone = true)
    assertTrue(initialInk > 0 && kotlin.math.abs(returnedInk - initialInk) <= initialInk / 20)
    compose.onNodeWithText("Album").assertExists()
    delete.assertDoesNotExist()
  }

  private fun pane(sidebar: Boolean) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidAdaptiveNavigation(listOf(FluidTabItem("album", "Album", FluidIcons.GridView)), "album", {}) {
          FluidListDetailScaffold(ambient = null, list = {
            FluidScreen("Foto", modifier = Modifier.testTag("lista"), toolbar = {
              if (selection.isActive) selection(selection, 1, null, {}, actions = FluidSelectionActions.deleteOnly({ tapped++ }))
              else selectButton({ selection.select(7) })
            }) {}
          }, detail = { Text("Dettaglio") })
        }
      }
    }
    advance(640)
    if (sidebar) {
      compose.onNodeWithContentDescription("Pannello laterale").performClick()
      advance(1600)
    }
    val area = compose.onNodeWithTag("lista").getBoundsInRoot()
    compose.onNodeWithContentDescription("Seleziona").performClick()
    advance(800)
    val button = compose.onNodeWithContentDescription("Elimina")
    val bounds = button.getBoundsInRoot()
    assertTrue(bounds.left >= area.left && bounds.right <= area.right)
    assertTrue(bounds.top > (area.top + area.bottom) / 2)
    if (sidebar) compose.onNodeWithContentDescription("Chiudi il pannello").assertExists()
    assertEquals(area, compose.onNodeWithTag("lista").getBoundsInRoot())
    button.performClick()
    assertEquals(1, tapped)
  }

  @Test @Config(qualifiers = "w800dp-h900dp-xhdpi")
  fun railLeavesActionsInTheList() = pane(false)

  @Test @Config(qualifiers = "w1400dp-h900dp-xhdpi")
  fun sidebarLeavesActionsInTheList() = pane(true)
}
