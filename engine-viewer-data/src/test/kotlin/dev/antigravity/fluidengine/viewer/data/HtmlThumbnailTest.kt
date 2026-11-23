package dev.antigravity.fluidengine.viewer.data

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HtmlThumbnailTest {
  @Test fun rendersInlineAndLocalCssAndBothKindsOfImageWithoutRequestingRemoteResources() = runBlocking {
    val files = mutableListOf<String>()
    val embedded = Base64.getEncoder().encodeToString(image(0xFFFF8C00.toInt()))
    val bitmap = HtmlThumbnail.render("""
      <html><head><link rel="stylesheet" href="css/local.css">
        <link rel=stylesheet href=https://example.com/remote.css>
        <style>body {background-color:#eef6fe} .hero .accent {color:#008000}</style></head>
      <body><main class="hero"><p class="accent">Titolo verde della pagina</p>
      <p style="color:#e00070; font-size:24px">Testo rosa in linea</p>
      <p><img src="foto/locale.png"><img src="data:image/png;base64,$embedded" width="66" height="34"></p>
      <img src="//example.com/remote.png"><img src="%2f%2fexample.com/escaped.png"><img src="file:///secret.png">
      <img src="&#104;ttps://example.com/entity.png"><img src="../privato.png">
      <script>fetch('https://example.com/script');</script></main></body></html>
    """.trimIndent(), 480) { path ->
      files += path
      when (path) {
        "css/local.css" -> ".hero img {width:100px; height:40px} .hero .accent {background:#6050ee}".byteInputStream()
        "foto/locale.png" -> image(0xFF3088BE.toInt()).inputStream()
        else -> error("Unexpected resource $path")
      }
    }.asAndroidBitmap()
    assertEquals(listOf("css/local.css", "foto/locale.png"), files)
    assertEquals(0xFFEEF6FE.toInt(), bitmap.getPixel(0, 0))
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (color in listOf(0xFF008000, 0xFFE00070, 0xFF6050EE, 0xFF3088BE, 0xFFFF8C00)) {
      assertTrue("Missing CSS or image pixel ${color.toString(16)}", pixels.count { it == color.toInt() } > 20)
    }
  }

  private fun image(color: Int): ByteArray {
    val pixels = Bitmap.createBitmap(12, 9, Bitmap.Config.ARGB_8888)
    pixels.eraseColor(color)
    return ByteArrayOutputStream().also { pixels.compress(Bitmap.CompressFormat.PNG, 100, it); pixels.recycle() }.toByteArray()
  }
}
