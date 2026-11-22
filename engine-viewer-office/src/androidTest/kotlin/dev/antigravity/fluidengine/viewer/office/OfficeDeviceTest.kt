package dev.antigravity.fluidengine.viewer.office

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.viewer.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeDeviceTest {
  @Test
  fun firstVisiblePageAndMemory() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().context
    val out = File(context.filesDir, "office-proof").also { it.mkdirs() }
    val rows = mutableListOf("file,run,first_ms,java_peak_delta_bytes,pss_peak_delta_kib")
    repeat(3) { pass ->
      val names = listOf("Giorni-300.docx", "Celle-100000.xlsx")
      for (name in if (pass % 2 == 0) names else names.reversed()) {
        val file = File(context.cacheDir, name)
        context.assets.open(name).use { input -> file.outputStream().use(input::copyTo) }
        val rt = Runtime.getRuntime()
        fun heap() = rt.totalMemory() - rt.freeMemory()
        val baseHeap = heap()
        val basePss = Debug.getPss()
        var peakHeap = baseHeap
        var peakPss = basePss
        val monitor =
          launch(Dispatchers.Default) {
            while (isActive) {
              peakHeap = maxOf(peakHeap, heap())
              peakPss = maxOf(peakPss, Debug.getPss())
              delay(35)
            }
          }
        try {
          val start = SystemClock.elapsedRealtimeNanos()
          FluidViewerRegistry.open(context, FluidViewerSource.of(file, deleteOnClose = true)).use {
            doc ->
            assertNull(doc.failure)
            val first =
              when (val content = doc.content) {
                is FluidViewerContent.Pages -> {
                  content.source.render(0, IntSize(640, 828)).asAndroidBitmap().recycle()
                  val elapsed = SystemClock.elapsedRealtimeNanos() - start
                  withTimeout(60000) { while (!content.source.countFinal) delay(10) }
                  assertEquals(300, content.source.count)
                  elapsed
                }
                is FluidViewerContent.Grid -> {
                  assertEquals(80, content.source.cells(0, 0..9, 0..7).size)
                  val elapsed = SystemClock.elapsedRealtimeNanos() - start
                  assertEquals("99999", content.source.cells(0, 999..999, 99..99).single().text)
                  elapsed
                }
                else -> error("Forma inattesa: $name")
              }
            peakHeap = maxOf(peakHeap, heap())
            peakPss = maxOf(peakPss, Debug.getPss())
            val line = "$name,${pass+1},${first/1_000_000},${peakHeap-baseHeap},${peakPss-basePss}"
            rows += line
            Log.i("FluidOfficeProof", line)
            File(out, "performance.csv").writeText(rows.joinToString("\n"))
          }
        } finally {
          monitor.cancelAndJoin()
        }
      }
    }
  }
}
