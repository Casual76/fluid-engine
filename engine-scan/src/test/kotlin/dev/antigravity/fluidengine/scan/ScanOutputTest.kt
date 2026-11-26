package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.hypot

/**
 * F8.17 · Lo scanner fuori dalla fotocamera: il PDF (le pagine, il formato riconosciuto, la tabella xref) e lo scatto da
 * solo una volta per foglio, mai con la mano che trema. I bordi, il raddrizzamento e i filtri si provano in
 * engine-scan-core (F8.17d).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanOutputTest {
  @Test fun ilPdfHaLePagineEGliIndirizziGiusti() {
    val jpeg = { w: Int, h: Int ->
      val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
      ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
    val out = ByteArrayOutputStream()
    ScanPdfWriter.write(listOf(ScanPdfWriter.Page(jpeg(1240, 1754), 1240, 1754), ScanPdfWriter.Page(jpeg(600, 1600), 600, 1600)),
      "Scansione 25 nov 2026, 14.32", out)
    val bytes = out.toByteArray()
    val text = String(bytes, Charsets.ISO_8859_1)
    assertTrue(text.startsWith("%PDF-1.4"))
    assertTrue(text.contains("/Count 2"))
    assertTrue("A4 riconosciuto", text.contains("/MediaBox [0 0 595.28 841.89]"))
    // La tabella xref: ogni scostamento punta davvero al suo oggetto.
    val xref = text.substring(text.lastIndexOf("startxref") + 10).trim().lines().first().toInt()
    val rows = text.substring(xref).lines().drop(3).takeWhile { it.endsWith(" n ") }
    assertEquals(9, rows.size)
    rows.forEachIndexed { i, row ->
      val offset = row.substring(0, 10).toInt()
      assertTrue("oggetto ${i + 1}", text.startsWith("${i + 1} 0 obj", offset))
    }
    assertEquals(612f to 792f, ScanPdfWriter.pageSize(850, 1100))
    assertEquals(841.89f to 595.28f, ScanPdfWriter.pageSize(1754, 1240))
  }

  @Test fun loScattoDaSoloUnaVoltaPerFoglio() {
    val tracker = FluidScanTracker()
    val quad = FluidScanQuad(FluidScanPoint(0.2f, 0.2f), FluidScanPoint(0.8f, 0.2f), FluidScanPoint(0.8f, 0.8f), FluidScanPoint(0.2f, 0.8f))
    val det = FluidScanDetection(quad, 0.9f, 0.75f)
    var captures = 0
    var t = 0L
    repeat(40) {
      val track = tracker.update(det, t)
      if (track.shouldCapture) captures++
      t += 66
    }
    assertEquals("una volta sola sullo stesso foglio", 1, captures)
    // Il foglio sparisce e torna: si può scattare di nuovo.
    repeat(8) { tracker.update(null, t); t += 66 }
    repeat(20) { if (tracker.update(det, t).shouldCapture) captures++; t += 66 }
    assertEquals(2, captures)
    // In manuale non scatta mai da solo.
    val manual = FluidScanTracker()
    var t2 = 0L
    repeat(40) { assertTrue(!manual.update(det, t2, auto = false).shouldCapture); t2 += 66 }
    // Un foglio piccolo chiede di avvicinarsi.
    val small = FluidScanQuad(FluidScanPoint(0.45f, 0.45f), FluidScanPoint(0.55f, 0.45f), FluidScanPoint(0.55f, 0.55f), FluidScanPoint(0.45f, 0.55f))
    assertEquals(FluidScanHint.Closer, FluidScanTracker().update(FluidScanDetection(small, 0.9f, 1f), 0).hint)
  }

  @Test fun laManoCheTremaNonScatta() {
    val tracker = FluidScanTracker()
    val base = FluidScanQuad(FluidScanPoint(0.2f, 0.2f), FluidScanPoint(0.8f, 0.2f), FluidScanPoint(0.8f, 0.8f), FluidScanPoint(0.2f, 0.8f))
    var t = 0L
    repeat(30) { i ->
      val d = if (i % 2 == 0) 0.03f else -0.03f
      val shaken = base.shifted(FluidScanPoint(d, 0f))
      assertTrue(!tracker.update(FluidScanDetection(shaken, 0.9f, 1f), t).shouldCapture)
      t += 66
    }
    assertTrue(abs(hypot(1f, 1f)) > 0f)
  }
}
