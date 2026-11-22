package dev.antigravity.fluidengine.viewer

import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il risolutore da MIME, estensione e firma al preset, con la regola dell'eseguibile di `FluidFileKind`. */
class FluidViewerTypeTest {

  private fun preset(mime: String?, name: String, header: ByteArray? = null) = FluidViewerType.of(mime, name, header).preset

  @Test
  fun `each kind has its preset from the extension`() {
    assertEquals(FluidViewerPreset.Image, preset(null, "foto.JPG"))
    assertEquals(FluidViewerPreset.Image, preset(null, "anim.gif"))
    assertEquals(FluidViewerPreset.Image, preset(null, "anim.webp"))
    assertEquals(FluidViewerPreset.Video, preset(null, "clip.mp4"))
    assertEquals(FluidViewerPreset.Audio, preset(null, "voce.m4a"))
    assertEquals(FluidViewerPreset.Pages, preset(null, "contratto.pdf"))
    assertEquals(FluidViewerPreset.Text, preset(null, "Main.kt"))
    assertEquals(FluidViewerPreset.Text, preset(null, "dati.csv"))
    assertEquals(FluidViewerPreset.Unknown, preset(null, "tesi.docx"))
    assertEquals(FluidViewerPreset.Unknown, preset(null, "archivio.zip"))
  }

  @Test
  fun `the preset follows the mime when the name says nothing`() {
    assertEquals(FluidViewerPreset.Image, preset("image/png", "senza-estensione"))
    assertEquals(FluidViewerPreset.Video, preset("video/quicktime", "IMG_0001"))
    assertEquals(FluidViewerPreset.Audio, preset("audio/ogg", "nota"))
    assertEquals(FluidViewerPreset.Pages, preset("application/pdf", "documento"))
    assertEquals(FluidViewerPreset.Text, preset("text/plain", "LEGGIMI"))
    assertEquals(FluidViewerPreset.Text, preset("application/json", "risposta"))
    assertEquals(FluidViewerPreset.Text, preset("application/ld+json", "risposta"))
    assertEquals(FluidViewerPreset.Text, preset("application/rss+xml", "feed"))
  }

  @Test
  fun `a null mime falls back on the extension`() {
    val type = FluidViewerType.of(null, "note.md")
    assertEquals(FluidViewerPreset.Text, type.preset)
    assertNull(type.mimeType)
    assertEquals(FluidSyntax.Markdown, type.language)
  }

  @Test
  fun `a generic or malformed mime counts as no mime`() {
    for (generic in listOf("application/octet-stream", "*/*", "application/unknown", "binary/octet-stream", "", "png", "image/", "/png", "a/b/c", "multipart/mixed; boundary=x")) {
      val type = FluidViewerType.of(generic, "foto.png")
      assertEquals(generic, FluidViewerPreset.Image, type.preset)
      assertNull(generic, type.mimeType)
      assertEquals(generic, FluidViewerPreset.Unknown, preset(generic, "file-senza-nome"))
    }
  }

  @Test
  fun `a mime with parameters or capitals is the clean one`() {
    val type = FluidViewerType.of("Text/Plain; charset=UTF-8", "LEGGIMI")
    assertEquals(FluidViewerPreset.Text, type.preset)
    assertEquals("text/plain", type.mimeType)
    assertEquals(FluidViewerPreset.Image, preset(" IMAGE/JPEG ", "x"))
  }

  @Test
  fun `the extension wins over a conflicting mime, as in FluidFileKind`() {
    assertEquals(FluidViewerPreset.Image, preset("video/mp4", "foto.png"))
    assertEquals(FluidViewerPreset.Text, preset("image/png", "appunti.txt"))
    assertEquals(FluidFileKind.Image, FluidViewerType.of("video/mp4", "foto.png").kind)
  }

  @Test
  fun `between video and audio the mime decides`() {
    assertEquals(FluidViewerPreset.Audio, preset("audio/mp4", "solo-audio.mp4"))
    assertEquals(FluidViewerPreset.Video, preset("video/ogg", "clip.ogg"))
    assertEquals(FluidViewerPreset.Audio, preset("audio/webm", "voce.webm"))
  }

  @Test
  fun `an executable stays an executable from either source`() {
    // Dall'estensione, con un MIME innocuo.
    for (name in listOf("gioco.apk", "setup.exe", "script.sh", "avvio.ps1", "x.bat", "app.jar", "Foto.PNG.apk")) {
      val type = FluidViewerType.of("image/png", name)
      assertEquals(name, FluidFileKind.Executable, type.kind)
      assertEquals(name, FluidViewerPreset.Unknown, type.preset)
      assertTrue(name, type.executable)
      assertNull(name, type.language)
    }
    // Dal MIME, con un nome innocuo.
    for (mime in listOf("application/vnd.android.package-archive", "application/x-msdownload", "text/x-shellscript", "application/x-sh")) {
      val type = FluidViewerType.of(mime, "foto.png")
      assertEquals(mime, FluidFileKind.Executable, type.kind)
      assertEquals(mime, FluidViewerPreset.Unknown, type.preset)
    }
    // Nemmeno la firma dei byte lo declassa.
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    assertEquals(FluidViewerPreset.Unknown, preset("application/vnd.android.package-archive", "x", png))
  }

  @Test
  fun `the kind is always FluidFileKind from, untouched`() {
    for ((mime, name) in listOf(null to "a.svg", "image/svg+xml" to "logo", "text/csv" to "t.csv", null to "a.kts", "application/octet-stream" to "x")) {
      assertEquals(FluidFileKind.from(mime, name), FluidViewerType.of(mime, name).kind)
    }
  }

  @Test
  fun `images without a platform decoder go to unknown`() {
    assertEquals(FluidViewerPreset.Unknown, preset(null, "logo.svg"))
    assertEquals(FluidViewerPreset.Unknown, preset("image/png", "logo.svg"))
    assertEquals(FluidViewerPreset.Unknown, preset("image/svg+xml", "logo"))
    assertEquals(FluidViewerPreset.Unknown, preset("image/tiff", "scansione"))
    assertEquals(FluidFileKind.Image, FluidViewerType.of(null, "logo.svg").kind)
  }

  @Test
  fun `names without an extension and dotfiles`() {
    assertEquals(FluidViewerPreset.Text, preset(null, "README"))
    assertEquals(FluidViewerPreset.Text, preset(null, "Makefile"))
    assertEquals(FluidViewerPreset.Text, preset(null, ".gitignore"))
    assertEquals(FluidViewerPreset.Text, preset(null, "path/to/LICENSE"))
    assertEquals(FluidViewerPreset.Unknown, preset(null, "dati"))
    assertEquals(FluidViewerPreset.Unknown, preset(null, "archivio."))
  }

  @Test
  fun `the last extension counts`() {
    assertEquals(FluidViewerPreset.Unknown, preset(null, "backup.tar.gz"))
    assertEquals(FluidViewerPreset.Text, preset(null, "build.gradle.kts"))
    assertEquals(FluidSyntax.Kotlin, FluidViewerType.of(null, "build.gradle.kts").language)
    assertEquals(FluidViewerPreset.Text, preset(null, "C:\\Users\\a\\note.txt"))
  }

  @Test
  fun `the signature fills in only when name and mime are silent`() {
    fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    val pdf = "%PDF-1.7\n".toByteArray()
    val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
    val mp4 = bytes(0, 0, 0, 0x18) + "ftypisom".toByteArray()
    val heic = bytes(0, 0, 0, 0x18) + "ftypheic".toByteArray()
    val m4a = bytes(0, 0, 0, 0x18) + "ftypM4A ".toByteArray()
    val bmp = "BM".toByteArray() + ByteArray(12) + bytes(40, 0, 0, 0)
    assertEquals(FluidViewerPreset.Pages, preset(null, "scaricato", pdf))
    assertEquals(FluidViewerPreset.Image, preset(null, "scaricato", bytes(0xFF, 0xD8, 0xFF, 0xE0)))
    assertEquals(FluidViewerPreset.Image, preset(null, "scaricato", "GIF89a".toByteArray()))
    assertEquals(FluidViewerPreset.Image, preset("application/octet-stream", "scaricato", webp))
    assertEquals(FluidViewerPreset.Video, preset(null, "scaricato", mp4))
    assertEquals(FluidViewerPreset.Image, preset(null, "scaricato", heic))
    assertEquals(FluidViewerPreset.Audio, preset(null, "scaricato", m4a))
    assertEquals(FluidViewerPreset.Image, preset(null, "scaricato", bmp))
    assertEquals(FluidViewerPreset.Audio, preset(null, "scaricato", "ID3\u0004".toByteArray()))
    assertEquals(FluidViewerPreset.Video, preset(null, "scaricato", bytes(0x1A, 0x45, 0xDF, 0xA3)))
    // Un testo che comincia con «BM» non è una bitmap.
    assertEquals(FluidViewerPreset.Unknown, preset(null, "scaricato", "BMW, nota del giorno".toByteArray()))
    // Il nome vince sulla firma.
    assertEquals(FluidViewerPreset.Text, preset(null, "finto.txt", pdf))
    assertNull(FluidViewerType.sniff(ByteArray(0)))
    assertNull(FluidViewerType.sniff("ciao".toByteArray()))
  }

  @Test
  fun `the preset prints its name`() {
    assertEquals(
      listOf("Image", "Video", "Audio", "Pages", "Text", "Unknown"),
      listOf(FluidViewerPreset.Image, FluidViewerPreset.Video, FluidViewerPreset.Audio, FluidViewerPreset.Pages, FluidViewerPreset.Text, FluidViewerPreset.Unknown).map { it.toString() },
    )
  }
}
