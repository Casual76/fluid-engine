package dev.antigravity.fluidengine.viewer.office

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.viewer.*
import java.io.File
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfficeCorpusTest {
  @Test
  fun embeddedOdfThumbnailFitsTheRequestedSize() = runBlocking {
    FluidViewerSource.of(File("src/test/resources/corpus/Canale.odt")).use { source ->
      val preview = FluidViewerRegistry.thumbnail(RuntimeEnvironment.getApplication(), source, 64)
      assertNotNull(preview)
      assertTrue(preview!!.width in 1..64 && preview.height in 1..64)
    }
  }

  private val corpus = File("src/test/resources/corpus")
  private val output = File("build/office-proof")

  private suspend fun open(file: File) =
    FluidViewerRegistry.open(RuntimeEnvironment.getApplication(), FluidViewerSource.of(file))

  private fun requireOpened(doc: FluidViewerDocument) {
    doc.failure?.let { throw AssertionError(doc.file.name + ": " + it.message, it) }
  }

  @Test fun infoDatesUseTheDeviceZoneAndKeepTheSavedTimestamp() = runBlocking {
    val raw = "2013-12-23T23:15:00Z"
    val rome = ZoneId.of("Europe/Rome")
    val italian = officeDisplayDate(raw, Locale.ITALY, rome)
    assertTrue(italian, "24 dic 2013" in italian && "00:15" in italian)
    val american = officeDisplayDate(raw, Locale.US, ZoneId.of("America/New_York"))
    assertTrue(american, "Dec 23, 2013" in american && "6:15" in american)
    assertTrue("23:15" in officeDisplayDate("2013-12-23T23:15:00", Locale.ITALY, rome))
    assertEquals("23 dic 2013", officeDisplayDate("2013-12-23", Locale.ITALY, rome))
    assertEquals("data ignota", officeDisplayDate("data ignota", Locale.ITALY, rome))
    open(File(corpus, "Calibri.docx")).use { document ->
      val row = document.officeInfo().sections.flatMap { it.rows }.first { it.label == "Creato" }
      assertEquals(raw, row.copyValue)
      assertNotEquals(raw, row.value)
    }
  }

  @Test fun annualWorkbookHasThreeDistinctSheetsWithMonthlyTotals() = runBlocking {
    open(File(corpus, "Bilancio 2026.xlsx")).use { document ->
      requireOpened(document)
      val grid = (document.content as FluidViewerContent.Grid).source
      assertEquals(listOf("Riepilogo", "Entrate", "Uscite"), grid.sheets.map { it.name })
      assertEquals(3, grid.sheets.map { it.color }.distinct().size)
      assertTrue(grid.sheets.all { it.rowCount >= 22 && it.columnCount == 5 && it.frozenRows == 1 })
      assertTrue(grid.sheets.first().columnWidths.getValue(4) > grid.sheets.first().columnWidths.getValue(1))
      assertEquals("Totale 2026", grid.cells(0, 13..13, 0..0).single().text)
      assertTrue(grid.cells(0, 1..12, 3..3).all { "€" in it.text })
    }
  }

  @Test
  fun documentsAndSlidesRenderThroughRegistry() = runBlocking {
    for (f in
      corpus
        .listFiles()
        .orEmpty()
        .filter {
          it.extension in setOf("docx", "rtf", "odt", "pptx", "odp") &&
            it.name !in setOf("Protetto.docx", "Danneggiato.docx")
        }
        .sorted()) {
      open(f).use { doc ->
        requireOpened(doc)
        val source = (doc.content as FluidViewerContent.Pages).source
        withTimeout(60000) { while (!source.countFinal) delay(10) }
        val folder = File(output, f.name).also { it.mkdirs() }
        File(folder, "pages.txt").writeText(source.count.toString())
        File(folder, "info.txt")
          .writeText(doc.details.joinToString("\n") { it.label + " = " + it.value })
        for (i in 0 until source.count) {
          val size = source.size(i)
          File(folder, "lines-${i+1}.txt")
            .writeText((source as OfficePages).bodyLines(i).joinToString("\n"))
          val w = if (source.count > 30) 240 else 1000
          val bitmap =
            source
              .render(i, IntSize(w, (w.toFloat() * size.height / size.width).toInt()))
              .asAndroidBitmap()
          File(folder, "page-${i+1}.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
          }
          bitmap.recycle()
        }
        assertTrue(source.count > 0)
        when (f.name) {
          "Giorni-300.docx" -> assertEquals(300, source.count)
          "Aptos.docx" -> {
            assertTrue("Aptos, tolleranza di una pagina", source.count in 1..3)
            assertTrue(doc.details.any { it.label == "Aptos" && "approssimate" in it.value })
            assertTrue(doc.details.any { it.label == "A capo" && "non latini" in it.value })
          }
          "Percorso.pptx" -> {
            assertEquals(1, source.count)
            assertTrue(doc.details.any { "SmartArt" in it.value })
          }
          "Canale di accesso.docx" -> {
            assertEquals(2, source.count)
            assertTrue(source.sheet(0).aspectRatio < 1)
          }
          "Rotte.pptx", "Rotte di novembre.pptx",
          "Rotte.odp" -> {
            assertEquals(2, source.count)
            assertTrue(
              doc.details.any { it.section == "Note del relatore" && it.value.contains("livello") }
            )
          }
          "Canale.docx",
          "Canale.rtf",
          "Canale.odt" -> {
            assertTrue(source.sheet(0).aspectRatio > 1)
            assertEquals(f.name, 2, source.count)
          }
          else -> {
            assertEquals(f.name, 3, source.count)
            val reference =
              File("src/test/resources/linee-word", f.nameWithoutExtension + ".txt")
                .readText()
                .replace("\r\n", "\n")
                .trimEnd()
                .split("\n\u000c\n")
            for (index in 0 until source.count) {
              val lines = (source as OfficePages).bodyLines(index).filter(String::isNotBlank)
              assertEquals(
                "${f.name} pagina ${index + 1}",
                reference[index],
                lines.joinToString("\n"),
              )
            }
          }
        }
      }
    }
  }

  @Test
  fun cachedValuesFormatsAndSheetGeometry() = runBlocking {
    for (name in listOf("Bilancio.xlsx", "Bilancio.ods", "Celle-100000.xlsx")) {
      open(File(corpus, name)).use { doc ->
        requireOpened(doc)
        val src = (doc.content as FluidViewerContent.Grid).source
        val folder = File(output, name).also { it.mkdirs() }
        for ((i, sheet) in src.sheets.withIndex()) {
          val lines = src.cells(i, 0..12, 0..9)
          File(folder, "sheet-$i.tsv")
            .writeText(lines.joinToString("\n") { "${it.row}\t${it.column}\t${it.text}" })
          File(folder, "sheet-$i.txt")
            .writeText("${sheet.name}\n${sheet.rowCount}\n${sheet.columnCount}")
        }
        if (name.startsWith("Celle")) {
          val all = src.cells(0, 0..999, 0..99)
          assertEquals(100000, all.size)
          assertTrue(all.all { it.text == (it.row * 100 + it.column).toString() })
          assertEquals("99999", src.cells(0, 999..999, 99..99).single().text)
        } else {
          assertEquals(3, src.sheets.size)
          val values = src.cells(0, 1..2, 0..2).associate { (it.row to it.column) to it.text }
          assertTrue(values[2 to 1].orEmpty().contains("2469"))
          assertTrue(values[1 to 2].orEmpty().contains('%'))
          assertTrue(values[1 to 1].orEmpty().contains('€'))
          assertEquals(1, src.sheets[0].frozenRows)
          assertEquals(1, src.sheets[0].frozenColumns)
          if (name.endsWith("xlsx")) {
            assertEquals("22/11/2026", values[1 to 0])
            assertEquals("20,5%", values[1 to 2])
            assertEquals("€ 1.234,50", values[1 to 1])
            assertNotNull(src.sheets[0].color)
            assertEquals(1, src.sheets[0].merges.size)
          }
        }
      }
    }
  }

  @Test
  fun protectedAndCorruptFilesKeepTheReason() = runBlocking {
    for ((name, reason) in
      listOf(
        "Protetto.docx" to FluidViewerFailure.Protected,
        "Danneggiato.docx" to FluidViewerFailure.Corrupt,
      )) {
      open(File(corpus, name)).use { doc ->
        assertTrue(name, doc.content is FluidViewerContent.Unknown)
        assertEquals(reason, doc.failure?.failure)
      }
    }
  }
}
