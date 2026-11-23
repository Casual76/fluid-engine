package dev.antigravity.fluidengine.viewer.office

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeCaptureTest {
  @Test
  fun exportCorpusPages() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().context
    val root = File(context.filesDir, "office-proof").apply { mkdirs() }
    val names =
      context.assets.list("").orEmpty().filter {
        it.substringAfterLast('.') in setOf("docx", "rtf", "odt", "ods", "xlsx", "odp", "pptx")
      }
    for (name in names.filterNot { it in listOf("Protetto.docx", "Danneggiato.docx") }) {
      val file = File(context.cacheDir, name)
      context.assets.open(name).use { input -> file.outputStream().use(input::copyTo) }
      FluidViewerRegistry.open(context, FluidViewerSource.of(file, deleteOnClose = true)).use { doc
        ->
        assertNull(name + " " + doc.failure?.message, doc.failure)
        val folder = File(root, name).apply { mkdirs() }
        when (val content = doc.content) {
          is FluidViewerContent.Pages -> {
            val source = content.source
            withTimeout(60000) { while (!source.countFinal) delay(10) }
            File(folder, "pages.txt").writeText(source.count.toString())
            for (i in 0 until source.count) {
              val size = source.size(i)
              val w = if (source.count > 30) 240 else 1000
              File(folder, "lines-${i+1}.txt")
                .writeText((source as OfficePages).bodyLines(i).joinToString("\n"))
              val bitmap =
                source
                  .render(i, IntSize(w, (w.toFloat() * size.height / size.width).toInt()))
                  .asAndroidBitmap()
              File(folder, "page-${i+1}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
              }
              bitmap.recycle()
            }
          }
          is FluidViewerContent.Grid ->
            for ((i, sheet) in content.source.sheets.withIndex()) {
              File(folder, "sheet-$i.txt")
                .writeText("${sheet.name}\n${sheet.rowCount}\n${sheet.columnCount}")
              File(folder, "sheet-$i.tsv").bufferedWriter().use { out ->
                for (first in 0 until sheet.rowCount step 32) {
                  val rows = first until minOf(first + 32, sheet.rowCount)
                  for (cell in content.source.cells(i, rows, 0 until sheet.columnCount)) {
                    out.appendLine("${cell.row}\t${cell.column}\t${cell.text}")
                  }
                }
              }
            }
          else -> error("Forma inattesa per $name")
        }
        File(folder, "info.txt")
          .writeText(doc.details.joinToString("\n") { "${it.label} = ${it.value}" })
      }
    }
  }
}
