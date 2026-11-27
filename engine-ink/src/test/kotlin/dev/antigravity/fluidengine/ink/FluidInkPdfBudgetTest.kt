package dev.antigravity.fluidengine.ink

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R1-C · Le pagine di un PDF da annotare (review R1, Opus D4) stanno sotto il tetto di memoria del visore: la categoria
 * dei visori nel registro del processo (`FluidGlassResources.budget`), lo stesso che usa il PDF di engine-viewer. Sul
 * Tab S9 a zoom 1,6 il foglio chiedeva ogni pagina larga 4096 px: sei pagine A4 facevano 384 MiB.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidInkPdfBudgetTest {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  private class A4Pages(count: Int) : InkPdfPages(List(count) { Size(595f, 842f) }) {
    override fun draw(index: Int, bitmap: Bitmap) {
      bitmap.eraseColor(0xFFF0F0F0.toInt())
    }
  }

  private fun InkPdfPages.readyBytes(): Long = ready.values.sumOf { it.image.width.toLong() * it.image.height * 4L }

  @Test
  fun `the pages of an annotated pdf stay under the viewer ceiling`() = runBlocking {
    val budget = FluidGlassResources.get(context).budget
    val before = budget.usage(FluidMemoryCategory.VIEWER).usedBytes
    val limit = budget.usage(FluidMemoryCategory.VIEWER).limitBytes
    val pages = A4Pages(6)
    // Come il foglio a zoom 1,6 e oltre: ogni pagina alla larghezza piu' grande.
    for (i in 0 until 6) pages.prepare(i, InkPdfPages.MaxPagePixels)
    val bytes = pages.readyBytes()
    println("R1c pagine del PDF: ${bytes / 1024 / 1024} MiB pronte, tetto dei visori ${limit / 1024 / 1024} MiB")
    assertTrue("$bytes byte di pagine oltre il tetto $limit", bytes <= limit)
    assertTrue("le pagine contano nel registro", budget.usage(FluidMemoryCategory.VIEWER).usedBytes - before >= bytes)
    // La prima chiesta (quella in vista) resta la piu' nitida.
    val widths = (0 until 6).mapNotNull { pages.ready[it]?.widthPx }
    assertEquals(widths.max(), widths.first())
    pages.close()
    assertEquals("chiudendo il foglio la memoria torna", before, budget.usage(FluidMemoryCategory.VIEWER).usedBytes)
  }
}
