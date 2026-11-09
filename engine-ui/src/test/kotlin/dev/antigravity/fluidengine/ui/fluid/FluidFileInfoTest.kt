package dev.antigravity.fluidengine.ui.fluid

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class FluidFileInfoTest {
  @Test fun `copre tutte le categorie e MIME normalizzati`() {
    val files = mapOf("a.PNG" to FluidFileKind.Image, "a.pdf" to FluidFileKind.Pdf,
      "a.docx" to FluidFileKind.Document, "a.xlsx" to FluidFileKind.Spreadsheet,
      "a.pptx" to FluidFileKind.Presentation, "a.tar.gz" to FluidFileKind.Archive,
      "a.flac" to FluidFileKind.Audio, "a.mp4" to FluidFileKind.Video,
      "a.kt" to FluidFileKind.Text, "a.apk" to FluidFileKind.Executable, "file" to FluidFileKind.Other)
    files.forEach { (name, kind) -> assertEquals(name, kind, FluidFileKind.from(null, name)) }
    assertEquals(FluidFileKind.Pdf, FluidFileKind.from(" Application/PDF ; charset=utf-8", "file"))
    assertEquals(FluidFileKind.Other, FluidFileKind.from("", ".hidden"))
  }
  @Test fun `MIME senza estensione copre le famiglie e ignora i parametri`() {
    mapOf("image/png" to FluidFileKind.Image, "audio/mpeg" to FluidFileKind.Audio,
      "video/mp4" to FluidFileKind.Video, "application/msword" to FluidFileKind.Document,
      "application/vnd.ms-excel" to FluidFileKind.Spreadsheet,
      "application/vnd.ms-powerpoint" to FluidFileKind.Presentation,
      "application/zip" to FluidFileKind.Archive, "application/json" to FluidFileKind.Text,
      "text/x-shellscript" to FluidFileKind.Executable, "application/octet-stream" to FluidFileKind.Other
    ).forEach { (mime, kind) -> assertEquals(mime, kind, FluidFileKind.from(mime, "senza-estensione")) }
  }
  @Test fun `ultima estensione e ogni segnale eseguibile prevalgono sul MIME`() {
    assertEquals(FluidFileKind.Executable, FluidFileKind.from("application/pdf", "fattura.pdf.APK"))
    assertEquals(FluidFileKind.Executable, FluidFileKind.from("application/x-executable", "foto.jpg"))
    assertEquals(FluidFileKind.Pdf, FluidFileKind.from("image/png", "file.pdf"))
    assertEquals(FluidFileKind.Pdf, FluidFileKind.from(null, "innocuo.apk.pdf"))
    assertEquals(FluidFileKind.Other, FluidFileKind.from(null, "a.apk."))
  }
  @Test fun `dimensioni decimali locali e confini senza overflow`() {
    assertEquals("0 B", fluidFormatFileSize(0, Locale.ITALIAN))
    assertEquals("999 B", fluidFormatFileSize(999, Locale.US))
    for ((bytes, unit) in listOf(1000L to "KB", 1000000L to "MB", 1000000000L to "GB")) {
      assertEquals("1 $unit", fluidFormatFileSize(bytes, Locale.US))
      assertEquals("1,2 $unit", fluidFormatFileSize(bytes * 12 / 10, Locale.ITALIAN))
      assertEquals("1.2 $unit", fluidFormatFileSize(bytes * 12 / 10, Locale.US))
    }
    assertEquals("1 MB", fluidFormatFileSize(999999, Locale.US))
    assertTrue(fluidFormatFileSize(Long.MAX_VALUE, Locale.US).endsWith(" EB"))
  }
  @Test(expected = IllegalArgumentException::class) fun `rifiuta dimensioni negative`() { fluidFormatFileSize(-1) }
  @Test fun `troncamento conserva ultima estensione anche con budget minimo`() {
    assertEquals("fatt….apk", fluidTruncateFileName("fattura.pdf.apk", 9))
    assertEquals("….apk", fluidTruncateFileName("fattura.pdf.apk", 1))
    assertEquals("nome.pdf", fluidTruncateFileName("nome.pdf", 8))
    assertEquals("…", fluidTruncateFileName("nome", 1))
    assertEquals("😀….pdf", fluidTruncateFileName("😀abcdefgh.pdf", 6))
    assertEquals(".pdf", fluidFileExtension("dir.ext/nome.pdf"))
  }
}
