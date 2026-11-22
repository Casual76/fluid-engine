package dev.antigravity.fluidengine.sample.adaptive

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAdaptiveNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La navigazione adattiva sotto la barra di stato (scelta di Alessio del 22 novembre 2026, sulle riprese di F6B.12: nella
 * posta sul tablet il rail saliva sotto l'ora). Il rail e la tab bar in alto stanno sotto la barra di stato come la barra
 * in alto di `FluidScreen`; la pagina resta a finestra intera; un antenato che ha già consumato la barra di stato (la
 * regola di prima: `safeDrawingPadding` attorno) non la fa contare due volte.
 *
 * La barra di stato è finta: 48 px (24 dp a xhdpi) consegnati alla vista di Compose, come in `FluidZoomTitleUiTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h900dp-xhdpi")
class FluidAdaptiveStatusBarTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val statusBarPx = 48
  private val statusBar = 24.dp
  private lateinit var root: View
  private var insets = PaddingValues()

  private val items = listOf(
    FluidTabItem("posta", "Posta", FluidIcons.Inbox),
    FluidTabItem("cartelle", "Cartelle", FluidIcons.Folder),
    FluidTabItem("cerca", "Cerca", FluidIcons.Search),
  )

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    compose.waitForIdle()
  }

  private fun show(consumedOutside: Boolean) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        root = LocalView.current
        val outer = if (consumedOutside) Modifier.fillMaxSize().statusBarsPadding() else Modifier.fillMaxSize()
        Box(outer.testTag("contenitore")) {
          FluidAdaptiveNavigation(items, "posta", onSelect = {}) { padding ->
            insets = padding
            Box(Modifier.fillMaxSize().testTag("pagina"))
          }
        }
      }
    }
    advance(400)
  }

  private fun statusBar(px: Int) {
    compose.runOnUiThread {
      val bars = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px, 0, 0))
        .setVisible(WindowInsetsCompat.Type.statusBars(), px > 0)
        .build()
      ViewCompat.dispatchApplyWindowInsets(root, bars)
    }
    advance(400)
  }

  /** Il bordo alto del nodo [tag]; la scheda del rail ha anche la sua copia sotto la goccia, nello stesso posto. */
  private fun top(tag: String): Dp = compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().getBoundsInRoot().top

  @Test fun `il rail sta sotto la barra di stato e la pagina resta a finestra intera`() {
    show(consumedOutside = false)
    val rail = top("top_level_posta")
    assertEquals(0.dp, top("pagina"))
    statusBar(statusBarPx)
    assertEquals("il rail scende di quanto è alta la barra di stato", (rail + statusBar).value, top("top_level_posta").value, .5f)
    assertEquals("la pagina resta a finestra intera: la barra di stato la conta FluidScreen", 0.dp, top("pagina"))
  }

  @Test fun `una barra di stato gia consumata da un antenato non si conta due volte`() {
    show(consumedOutside = true)
    val before = top("top_level_posta")
    statusBar(statusBarPx)
    // L'antenato ha posato la barra di stato: il contenitore scende di 24 dp, e il rail con lui. Non di 48.
    assertEquals(statusBar.value, top("contenitore").value, .5f)
    assertEquals((before + statusBar).value, top("top_level_posta").value, .5f)
    assertEquals("la pagina parte dove parte il contenitore", top("contenitore").value, top("pagina").value, .5f)
  }

  @Test @Config(qualifiers = "w1400dp-h900dp-xhdpi")
  fun `sul tablet largo la tab bar in alto sta sotto la barra di stato e la pagina lo sa`() {
    show(consumedOutside = false)
    val padding = insets.calculateTopPadding()
    statusBar(statusBarPx)
    assertEquals("lo spazio sopra la pagina comprende la barra di stato", (padding + statusBar).value,
      insets.calculateTopPadding().value, .5f)
    assertEquals(0.dp, top("pagina"))
  }
}
