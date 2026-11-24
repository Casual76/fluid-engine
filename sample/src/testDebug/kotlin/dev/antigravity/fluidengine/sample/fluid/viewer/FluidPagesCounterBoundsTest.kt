package dev.antigravity.fluidengine.sample.fluid.viewer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FluidPagesCounterBoundsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun phone() = checkReader("telefono")

  @Config(sdk = [35], qualifiers = "w753dp-h1205dp-mdpi")
  @Test fun tabS9() = checkReader("tablet")

  @Test fun lastPageOfLongDocumentWithLimitedResidentBytes() {
    val context = compose.activity
    val count = 180
    val process = dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources.get(context)
    val category = dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory.VIEWER
    val base = process.budget.usage(category).usedBytes
    // La UI e le sue mani sul registro sono reali. Il renderer PDF nativo ha la prova Android a 300 pagine.
    val source = object : FluidPageSource {
      override val count = 180
      override suspend fun size(index: Int) = IntSize(160, 240)
      override suspend fun render(index: Int, target: IntSize) =
        checkNotNull(dev.antigravity.fluidengine.viewer.FluidViewerRaster.render(context, target) { size ->
          ImageBitmap(size.width, size.height)
        })
    }
    var show by mutableStateOf(true)
    try {
      assertEquals(count, source.count)
      compose.setContent { if (show) FluidTheme(EngineSettings()) {
        val document = remember { FluidViewerContent.Pages(source) }
        FluidFileViewer(1, content = { document }, renderContent = { _, visible -> FluidPagesPreset(document, visible) })
      } }
      val scroll = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      fun waitPage(number: Int) = compose.waitUntil(20_000) {
        compose.onAllNodesWithContentDescription("Pagina $number di $count").fetchSemanticsNodes().isNotEmpty()
      }
      waitPage(1)
      scroll.performScrollToIndex(count - 1)
      waitPage(count)
      compose.onNodeWithContentDescription("Miniature delle pagine").assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pagina $count di $count"))
      val usage = process.budget.usage(category)
      assertTrue(usage.residentBytes > base && usage.usedBytes <= usage.limitBytes)
      scroll.performScrollToIndex(0)
      waitPage(1)
    } finally {
      compose.runOnIdle { show = false }
      compose.waitForIdle()
    }
    compose.waitUntil(10_000) { process.budget.usage(category).usedBytes == base }
  }

  private fun checkReader(device: String) {
    var size by mutableStateOf(IntSize(612, 792))
    var font by mutableFloatStateOf(1f)
    val recorded = mutableListOf("forma,font,capsuleTop,capsuleBottom,pageTop")
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, font)) {
        FluidTheme(EngineSettings()) {
          key(size, font) {
            val pageSize = size
            val document = remember { FluidViewerContent.Pages(object : FluidPageSource {
              override val count = 3
              override suspend fun size(index: Int) = pageSize
              override suspend fun render(index: Int, target: IntSize) = ImageBitmap(target.width, target.height)
            }) }
            val strings = if (font == 1f) FluidPagesPresetStrings() else FluidPagesPresetStrings(
              counter = { n, total -> "$n di $total\nPagine" })
            FluidFileViewer(1, content = { document }, renderContent = { _, visible ->
              FluidPagesPreset(document, visible, strings = strings)
            })
          }
        }
      }
    }
    for ((name, natural) in listOf("PDF" to IntSize(612, 792), "Office" to IntSize(595, 842),
      "Slide" to IntSize(960, 540))) for (scale in listOf(1f, 2f)) {
      compose.runOnIdle { size = natural; font = scale }
      val page = compose.onNodeWithContentDescription("Pagina 1 di 3")
      compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Pagina 1 di 3").fetchSemanticsNodes().isNotEmpty() }
      compose.waitForIdle()
      val counter = compose.onNodeWithContentDescription("Miniature delle pagine")
      val chip = counter.fetchSemanticsNode().boundsInRoot
      val first = page.fetchSemanticsNode().boundsInRoot
      recorded += "$name,$scale,${chip.top},${chip.bottom},${first.top}"
      assertTrue("$device $name font=$scale: capsula $chip, pagina $first", first.top > chip.bottom)
      if (scale == 2f) assertTrue("È misurata una capsula a due righe: $chip", chip.height > 48f)

      // The space belongs to the start of the list. The page can slide behind the pinned control.
      val reader = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      reader.performTouchInput {
        val start = Offset(width / 2f, height / 2f)
        swipe(start, start - Offset(0f, 110f), durationMillis = 650)
      }
      compose.waitForIdle()
      val after = counter.fetchSemanticsNode().boundsInRoot
      assertEquals(chip.top, after.top, 1f)
      assertTrue("$device $name: la pagina deve passare sotto la capsula",
        page.fetchSemanticsNode().boundsInRoot.overlaps(after))
      reader.performScrollToIndex(0)
      compose.waitForIdle()
      assertTrue(page.fetchSemanticsNode().boundsInRoot.top > counter.fetchSemanticsNode().boundsInRoot.bottom)
    }
    System.getenv("FLUID_PAGES_BOUNDS")?.let { dir ->
      File(dir).mkdirs()
      File(dir, "$device.csv").writeText(recorded.joinToString("\n"))
    }
  }
}
