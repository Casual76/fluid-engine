package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridCell
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridRange
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridSheet
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridStyle
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageMargins
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSheet
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerBackground
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerFind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6C.8 · (7) Le forme nuove di engine-ui, viste da un modulo di famiglia: `Grid` per i fogli e le tabelle, `Custom`
 * per le pagine che il modulo disegna, e il contratto `Pages` che serve anche a Office (il foglio del documento, la
 * misura, la resa, il conteggio che cresce). Tutto senza librerie: engine-ui resta senza decodificatori, e
 * `EngineUiDecoderFreeTest` lo controlla sui sorgenti nuovi.
 */
class FluidViewerFormsTest {

  @Test
  fun `a pages source written before F6C 8 keeps working, with the sheet taken from its size`() = runBlocking {
    val pdfLike = object : FluidPageSource {
      override val count = 2
      override suspend fun size(index: Int) = IntSize(595, 842)
      override suspend fun render(index: Int, target: IntSize): ImageBitmap = throw UnsupportedOperationException()
    }
    assertTrue(pdfLike.countFinal)
    val sheet = pdfLike.sheet(0)
    assertEquals(FluidPageSheet(595f, 842f), sheet)
    assertEquals(Color.White, sheet.paper)
    assertEquals(FluidPageMargins.None, sheet.margins)
    assertEquals(595f / 842f, sheet.aspectRatio, 1e-6f)
  }

  @Test
  fun `an office document gives its own sheet per section and a count that grows while it lays out`() = runBlocking {
    val document = object : FluidPageSource {
      var laidOut = 1
      override val count: Int get() = laidOut
      override val countFinal: Boolean get() = laidOut == 3
      override suspend fun size(index: Int) = if (index == 2) IntSize(842, 595) else IntSize(595, 842)
      override suspend fun render(index: Int, target: IntSize): ImageBitmap = throw UnsupportedOperationException()
      override suspend fun sheet(index: Int) = if (index == 2) {
        FluidPageSheet(841.89f, 595.28f, FluidPageMargins(72f, 72f, 72f, 72f))
      } else {
        FluidPageSheet.A4.copy(margins = FluidPageMargins(left = 70.85f, top = 70.85f, right = 56.7f, bottom = 56.7f))
      }
    }
    val content = FluidViewerContent.Pages(document)
    assertSame(document, content.source)
    assertFalse(document.countFinal)
    document.laidOut = 3
    assertTrue(document.countFinal)
    assertTrue("la sezione orizzontale", document.sheet(2).aspectRatio > 1f)
    assertEquals(595.28f, document.sheet(0).width, 1e-3f)
    assertEquals(FluidPageSheet(612f, 792f), FluidPageSheet.Letter)
  }

  @Test
  fun `a grid gives sheets, frozen rows, merges and cells in blocks`() = runBlocking {
    val source = object : FluidGridSource {
      override val sheets = listOf(
        FluidGridSheet("Bilancio", rowCount = 1000, columnCount = 12, frozenRows = 1, frozenColumns = 1,
          columnWidths = mapOf(0 to 160f, 5 to 0f), merges = listOf(FluidGridRange(0, 1, 0, 3))),
        FluidGridSheet("dati.csv", rowCount = 3, columnCount = 2, headers = false, countFinal = false),
      )

      override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell> =
        rows.flatMap { r -> columns.map { c -> FluidGridCell(r, c, "${r + 1}:${c + 1}", numeric = c > 0) } }
    }
    val grid = FluidViewerContent.Grid(source)
    assertSame(source, grid.source)
    val sheet = grid.source.sheets.first()
    assertEquals(FluidGridSheet.DefaultColumnWidth, sheet.columnWidth)
    assertEquals(FluidGridSheet.DefaultRowHeight, sheet.rowHeight)
    assertTrue((0 to 2) in sheet.merges.single())
    assertFalse((1 to 2) in sheet.merges.single())
    val block = source.cells(0, 0 until 64, 0 until 32)
    assertEquals(64 * 32, block.size)
    assertEquals(FluidGridStyle.Default, block.first().style)
    assertNull(block.first().style.horizontal)
    assertEquals(Alignment.Bottom, block.first().style.vertical)
    assertFalse(grid.source.sheets[1].countFinal)
  }

  @Test
  fun `a custom page declares its background, its find and its bar`() {
    val plain = FluidViewerContent.Custom {}
    assertEquals(FluidViewerBackground.Theme, plain.background)
    assertNull(plain.find)
    var typed = ""
    val find = object : FluidViewerFind {
      override val query: String get() = typed
      override fun onQueryChange(query: String) {
        typed = query
      }
      override val navigation = FluidSearchNavigation(0, 0, {}, {})
    }
    find.onQueryChange("capitolo")
    assertEquals("capitolo", find.query)
    assertEquals("Trova", find.label)
    val model = FluidViewerContent.Custom(background = FluidViewerBackground.Black, find = find, actions = {}) {}
    assertEquals(FluidViewerBackground.Black, model.background)
    assertSame(find, model.find)
    assertEquals("Black", FluidViewerBackground.Black.toString())
    assertEquals("Theme", FluidViewerBackground.Theme.toString())
  }
}
