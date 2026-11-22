package dev.antigravity.fluidengine.viewer.graphics

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsDeviceTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun nativeHeicAndAvif() = runBlocking {
    for (name in listOf("studio.heic", "studio.avif")) {
      FluidViewerRegistry.open(context, context.corpusSource(name)).use { document ->
        assertNull("$name: ${document.failure}", document.failure)
        assertTrue(name, document.content is FluidViewerContent.Image)
      }
    }
  }

  @Test
  fun cameraRawShowsPreviewAndExif() = runBlocking {
    for (name in listOf("camera.cr2", "camera.cr3", "camera.nef", "camera.arw")) {
      FluidViewerRegistry.open(context, context.corpusSource(name)).use { document ->
        assertNull("$name: ${document.failure}", document.failure)
        assertTrue(name, document.content is FluidViewerContent.Image)
        assertTrue(name, document.details.any { it.value == "Sviluppo della fotocamera" })
        assertTrue(name, document.details.any { it.label == "Modello" })
      }
    }
  }

  @Test
  fun cameraDngDecodesOnPlatform() = runBlocking {
    FluidViewerRegistry.open(context, context.corpusSource("camera-pixel.dng")).use { document ->
      assertNull(document.failure)
      assertTrue(document.content is FluidViewerContent.Image)
      assertTrue(document.details.joinToString(), document.details.any { it.value == "Decodifica DNG di Android" })
    }
  }

  @Test
  fun tiffRendersTwoDistinctPages() = runBlocking {
    FluidViewerRegistry.open(context, context.corpusSource("studio-pages.tiff")).use { document ->
      val pages = (document.content as FluidViewerContent.Pages).source
      assertEquals(2, pages.count)
      val colors = (0 until pages.count).map { pages.render(it, IntSize(180, 120)).asAndroidBitmap().getPixel(0, 0) }
      assertEquals(0xff163548.toInt(), colors[0])
      assertEquals(0xff713951.toInt(), colors[1])
      assertNotEquals(colors[0], colors[1])
    }
  }

  @Test
  fun photoshopUsesCompositeOrJpegThumbnail() = runBlocking {
    for ((name, expected) in mapOf("composito.psd" to "Immagine composita", "miniatura.psd" to "Miniatura salvata in Photoshop")) {
      FluidViewerRegistry.open(context, context.corpusSource(name)).use { document ->
        assertNull("$name: ${document.failure}", document.failure)
        assertTrue(name, document.content is FluidViewerContent.Image)
        assertTrue(name, document.details.any { it.value == expected })
      }
    }
  }

  @Test
  fun sfntVariationsAndWoffBuildPlatformTypefaces() = runBlocking {
    for (name in listOf("studio.ttf", "studio.otf", "studio.ttc", "studio-variable.ttf", "studio.woff")) {
      FluidViewerRegistry.open(context, context.corpusSource(name)).use { document ->
        assertNull("$name: ${document.failure}", document.failure)
        assertTrue(name, document.content is FluidViewerContent.Custom)
        if (name == "studio-variable.ttf") assertTrue(document.details.any { it.value == "wght" })
      }
    }
  }

  @Test
  fun svgAndSvgzCanDrawAtFourTimesTheSize() = runBlocking {
    for (name in listOf("tracce.svg", "tracce.svgz")) {
      context.corpusSource(name).use { source ->
        val small = FluidViewerRegistry.thumbnail(context, source, 90)
        val large = FluidViewerRegistry.thumbnail(context, source, 360)
        assertNotNull(name, small)
        assertNotNull(name, large)
        assertEquals(small!!.width * 4, large!!.width)
        assertEquals(small.height * 4, large.height)
        assertTrue(large.asAndroidBitmap().getPixel(273, 75) != large.asAndroidBitmap().getPixel(273, 35))
      }
    }
  }
}
