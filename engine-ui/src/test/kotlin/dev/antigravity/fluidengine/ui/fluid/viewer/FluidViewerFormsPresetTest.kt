package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6C.8 · Le forme `Grid` e `Custom` nel telaio: il colore della pagina e chi possiede lo zoom, e i conti del preset
 * minimo della griglia (le colonne, le unioni, l'allineamento, i blocchi letti e dimenticati).
 */
class FluidViewerFormsPresetTest {

  @Test
  fun `a black custom page is black but keeps its own gestures, and the old forms are unchanged`() {
    val black = FluidViewerContent.Custom(background = FluidViewerBackground.Black) {}
    val theme = FluidViewerContent.Custom {}
    assertTrue(black.blackBackground)
    assertFalse("la pagina nera di un modulo (un modello 3D) non è zoomata dal telaio", black.frameZoomed)
    assertFalse(theme.blackBackground)
    assertFalse(theme.frameZoomed)
    val grid = FluidViewerContent.Grid(object : FluidGridSource {
      override val sheets = emptyList<FluidGridSheet>()
      override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange) = emptyList<FluidGridCell>()
    })
    assertFalse(grid.blackBackground)
    assertFalse(grid.frameZoomed)
    // Prima di F6C.8 le due proprietà coincidevano: per le forme di allora coincidono ancora.
    val pages = FluidViewerContent.Pages(object : FluidPageSource {
      override val count = 0
      override suspend fun size(index: Int) = IntSize(1, 1)
      override suspend fun render(index: Int, target: IntSize): ImageBitmap = throw UnsupportedOperationException()
    })
    val image = FluidViewerContent.Image(1f) {}
    for (old in listOf(image, pages, FluidViewerContent.Unknown(FluidFileInfo("x.bin")))) {
      assertEquals(old.toString(), old.blackBackground, old.frameZoomed)
    }
  }

  @Test
  fun `the column axis places, hides and finds columns`() {
    val axis = GridAxis(5, 80f, mapOf(1 to 120f, 3 to 0f))
    assertEquals(0f, axis.start(0))
    assertEquals(80f, axis.start(1))
    assertEquals(200f, axis.start(2))
    assertEquals(280f, axis.start(3))
    assertEquals(280f, axis.start(4))
    assertEquals(360f, axis.total)
    assertEquals(0f, axis.size(3))
    assertEquals(0, axis.indexAt(-10f))
    assertEquals(0, axis.indexAt(79.9f))
    assertEquals(1, axis.indexAt(80f))
    assertEquals(2, axis.indexAt(279f))
    assertEquals("la colonna nascosta non si trova mai", 4, axis.indexAt(280f))
    assertEquals(4, axis.indexAt(10_000f))
    assertEquals(0, GridAxis(0, 80f, emptyMap()).indexAt(10f))
  }

  @Test
  fun `column names, merges and the automatic alignment are those of a spreadsheet`() {
    assertEquals(listOf("A", "B", "Z", "AA", "AZ", "BA", "ZZ", "AAA"), listOf(0, 1, 25, 26, 51, 52, 701, 702).map(::gridColumnName))
    val merges = GridMerges(listOf(FluidGridRange(1, 1, 3, 2), FluidGridRange(5, 0, 5, 0), FluidGridRange(4, 3, 2, 3)))
    assertEquals("un blocco di una cella sola o rovesciato non è un'unione", 1, merges.all.size)
    assertEquals(FluidGridRange(1, 1, 3, 2), merges.at(2, 2))
    assertNull(merges.at(2, 3))
    assertNull(merges.at(0, 1))
    assertEquals(1, merges.inRow(3).size)
    assertEquals(0, merges.inRow(4).size)
    assertEquals(Alignment.End, gridHorizontal(FluidGridCell(0, 0, "12,5", numeric = true)))
    assertEquals(Alignment.Start, gridHorizontal(FluidGridCell(0, 0, "Totale")))
    assertEquals(Alignment.CenterHorizontally,
      gridHorizontal(FluidGridCell(0, 0, "12", FluidGridStyle(horizontal = Alignment.CenterHorizontally), numeric = true)))
  }

  @Test
  fun `the cells are read in blocks, once, and the far ones are forgotten`() = runBlocking {
    val reads = mutableListOf<Pair<IntRange, IntRange>>()
    val source = object : FluidGridSource {
      override val sheets = listOf(FluidGridSheet("Foglio1", 100_000, 200))
      override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell> {
        reads += rows to columns
        // Una cella fuori dal blocco chiesto non entra.
        return listOf(FluidGridCell(rows.first, columns.first, "${rows.first}:${columns.first}"), FluidGridCell(rows.last + 1, 0, "fuori"))
      }
    }
    val store = GridCellStore(source, 0)
    val merges = GridMerges(listOf(FluidGridRange(0, 40, 200, 45)))
    store.ensure(GridWindow(firstRow = 100, lastRow = 140, firstColumn = 0, lastColumn = 10, frozenRows = 1, frozenColumns = 0), merges)
    // Righe 100-140 nei blocchi 1 e 2, la riga bloccata nel blocco 0, e l'unione ancorata in (0, 40).
    assertEquals(listOf(64 until 128 to (0 until 32), 128 until 192 to (0 until 32), 0 until 64 to (0 until 32), 0 until 64 to (32 until 64)), reads)
    assertEquals("64:0", store[64, 0]?.text)
    assertNull(store[128, 1])
    assertNull("fuori dal blocco", store[128 + 64, 0]?.takeIf { it.text == "fuori" })
    assertEquals("le celle caricate della riga, in ordine di colonna", "A 0:0; AG 0:32", store.rowText(0))
    store.ensure(GridWindow(100, 140, 0, 10, 1, 0), merges)
    assertEquals("nessuna lettura in più", 4, reads.size)
    for (block in 0 until GridCellStore.MaxBlocks + 10) {
      store.ensure(GridWindow(block * 64 + 1000 * 64, block * 64 + 1000 * 64, 0, 0, 0, 0), GridMerges(emptyList()))
    }
    assertEquals(GridCellStore.MaxBlocks, store.blockCount)
    assertNull("i blocchi lontani si dimenticano", store[64, 0])
  }

  @Test
  fun `a failed read stops reading until retry`() = runBlocking {
    var fail = true
    var calls = 0
    val source = object : FluidGridSource {
      override val sheets = listOf(FluidGridSheet("Foglio1", 10, 10))
      override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell> {
        calls++
        if (fail) error("file rotto")
        return listOf(FluidGridCell(0, 0, "ok"))
      }
    }
    val store = GridCellStore(source, 0)
    val window = GridWindow(0, 9, 0, 9, 0, 0)
    store.ensure(window, GridMerges(emptyList()))
    assertTrue(store.failure is IllegalStateException)
    store.ensure(window, GridMerges(emptyList()))
    assertEquals("ferma finché non si riprova", 1, calls)
    fail = false
    store.retry()
    assertNull(store.failure)
    store.ensure(window, GridMerges(emptyList()))
    assertEquals("ok", store[0, 0]?.text)
  }
}
