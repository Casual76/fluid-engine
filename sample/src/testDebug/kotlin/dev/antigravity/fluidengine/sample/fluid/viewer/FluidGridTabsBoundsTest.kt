package dev.antigravity.fluidengine.sample.fluid.viewer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.LocalFluidBottomInset
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6C.15b · La capsula delle schede non copre mai le righe. A fine scorrimento l'ultima riga del foglio sta intera
 * sopra le schede, e le schede sopra la capsula in basso, sia in un'app senza tab bar sia con l'inset di una tab bar
 * (la galleria nel formato telefono): come Numbers. Anche le pagine e le slide finiscono sopra la capsula.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-mdpi")
class FluidGridTabsBoundsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun phone360() = checkGrid("360 dp")

  @Config(sdk = [35], qualifiers = "w412dp-h892dp-mdpi")
  @Test fun phone412() = checkGrid("412 dp")

  @Test fun pagesEndAboveTheCapsule360() = checkPages("360 dp")

  @Config(sdk = [35], qualifiers = "w412dp-h892dp-mdpi")
  @Test fun pagesEndAboveTheCapsule412() = checkPages("412 dp")

  /** Three sheets: tabs, a coordinate sheet with [rows] rows of five columns. */
  private class Book(rows: Int) : FluidGridSource {
    override val sheets = listOf("Riepilogo", "Entrate", "Uscite").map { FluidGridSheet(it, rows, 5, frozenRows = 1) }
    override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange) =
      rows.filter { it < this.sheets[sheet].rowCount }.flatMap { r -> columns.filter { it < 5 }.map { c -> FluidGridCell(r, c, "R${r + 1}C${c + 1}") } }
  }

  private fun checkGrid(device: String) {
    var inset by mutableStateOf(0.dp)
    var rows by mutableIntStateOf(60)
    var font by mutableFloatStateOf(1f)
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, font), LocalFluidBottomInset provides inset) {
        FluidTheme(EngineSettings()) {
          key(inset, rows, font) {
            val content = remember { FluidViewerContent.Grid(Book(rows)) }
            FluidFileViewer(1, content = { content }, toolbar = {
              button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar)
            }, renderContent = { file, visible -> FluidGridPreset(file as FluidViewerContent.Grid, visible) })
          }
        }
      }
    }
    // 86 dp: la stanza di una tab bar da 62 dp con il suo margine, come la dà la galleria nel formato telefono.
    for (bottomInset in listOf(0.dp, 86.dp)) for (count in listOf(60, 8)) for (scale in listOf(1f, 2f)) {
      compose.runOnIdle { inset = bottomInset; rows = count; font = scale }
      val label = "$device, inset $bottomInset, $count righe, testo ×$scale"
      compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Fogli").fetchSemanticsNodes().isNotEmpty() }
      compose.waitForIdle()
      val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
      val tabs = compose.onNodeWithContentDescription("Fogli").fetchSemanticsNode().boundsInRoot
      val share = shareBounds()
      val capsuleTop = root.bottom - dp(16.dp.takeIf { bottomInset == 0.dp } ?: (bottomInset + 8.dp)) - dp(48.dp)
      assertTrue("$label: le schede $tabs toccano la capsula (cima $capsuleTop)", tabs.bottom <= capsuleTop - dp(8.dp) + 1f)
      assertTrue("$label: le schede $tabs coprono il tasto $share", tabs.bottom <= share.top + 1f)

      val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      scrollToEnd(list, label)
      val last = rowBounds().maxBy { it.second.bottom }
      println("griglia $label: ultima ${last.first} fino a ${last.second.bottom}, schede ${tabs.top}–${tabs.bottom}, " +
        "tasto ${share.top}–${share.bottom}, schermo ${root.bottom}")
      assertTrue("$label: ${last.first} (${last.second}) finisce sotto le schede $tabs", last.second.bottom <= tabs.top + 0.5f)
      if (count == 60) {
        val data = rowBounds().firstOrNull { it.first.startsWith("Riga 60") }
        assertNotNull("$label: l'ultima riga del foglio è in vista", data)
        assertTrue("$label: l'ultima riga del foglio si legge intera", data!!.second.bottom <= tabs.top + 0.5f)
      }
    }
  }

  private fun checkPages(device: String) {
    var inset by mutableStateOf(0.dp)
    compose.setContent {
      CompositionLocalProvider(LocalFluidBottomInset provides inset) {
        FluidTheme(EngineSettings()) {
          key(inset) {
            val document = remember { FluidViewerContent.Pages(object : FluidPageSource {
              override val count = 3
              // Pagine verticali: tre non stanno in uno schermo, quindi la colonna scorre davvero.
              override suspend fun size(index: Int) = androidx.compose.ui.unit.IntSize(612, 792)
              override suspend fun render(index: Int, target: androidx.compose.ui.unit.IntSize) =
                androidx.compose.ui.graphics.ImageBitmap(target.width, target.height)
            }) }
            FluidFileViewer(1, content = { document }, toolbar = {
              button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar)
            }, renderContent = { file, visible -> FluidPagesPreset(file as FluidViewerContent.Pages, visible) })
          }
        }
      }
    }
    for (bottomInset in listOf(0.dp, 86.dp)) {
      compose.runOnIdle { inset = bottomInset }
      compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Pagina 3 di 3").fetchSemanticsNodes().isNotEmpty() ||
        compose.onAllNodesWithContentDescription("Pagina 1 di 3").fetchSemanticsNodes().isNotEmpty() }
      val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      scrollToEnd(list, "$device, inset $bottomInset")
      val page = compose.onNodeWithContentDescription("Pagina 3 di 3").fetchSemanticsNode().boundsInRoot
      val share = shareBounds()
      println("pagine $device, inset $bottomInset: ultima pagina fino a ${page.bottom}, tasto ${share.top}")
      assertTrue("$device, inset $bottomInset: l'ultima pagina $page finisce sotto la capsula $share", page.bottom <= share.top)
    }
  }

  /**
   * Trascinamenti in su finché la lista non va oltre: è ferma in fondo, col suo margine. Una lista pigra dà come
   * massimo la posizione più 100 finché può scorrere ancora, e la posizione stessa quando è in fondo.
   */
  private fun scrollToEnd(list: SemanticsNodeInteraction, label: String) {
    for (attempt in 0 until 40) {
      val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
      if (range.value() >= range.maxValue()) break
      // Dalla metà in su: più in basso ci sono le schede, che non passano il dito alla lista.
      list.performTouchInput { swipeUp(startY = height * 0.5f, endY = height * 0.1f) }
      compose.waitForIdle()
    }
    val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    assertTrue("$label: la lista è scorsa", range.value() > 0f)
    assertEquals("$label: in fondo", range.maxValue(), range.value(), 0.01f)
  }

  private fun dp(value: Dp) = value.value * compose.activity.resources.displayMetrics.density

  private fun shareBounds(): Rect =
    compose.onAllNodes(hasContentDescription("Condividi") or hasText("Condividi")).fetchSemanticsNodes()
      .map { it.boundsInRoot }.filter { it.height > 0f }.maxBy { it.bottom }

  private fun rowBounds(): List<Pair<String, Rect>> =
    compose.onAllNodes(SemanticsMatcher("una riga") { node ->
      node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith("Riga ") == true
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].first() to it.boundsInRoot }
      .filter { it.second.height > 0f }
}
