package dev.antigravity.fluidengine.viewer.graphics

import androidx.compose.ui.graphics.asAndroidBitmap
import dev.antigravity.fluidengine.viewer.FluidFileThumbnails
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DngThumbnailTest {
  @Test fun cameraOriginalRendersItsEmbeddedPreview() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    // Native Google Pixel 4 XL capture, raw.pixls.us sample 3502, CC0. See CORPUS.md.
    val source = FluidViewerSource.of("camera-pixel.dng", context.cacheDir, "image/x-adobe-dng",
      read = { requireNotNull(javaClass.getResourceAsStream("/corpus/camera-pixel.dng")) })
    source.use {
      val thumbnail = FluidFileThumbnails.read(context, source, 240)
      assertNotNull(thumbnail)
      val bitmap = requireNotNull(thumbnail).image.asAndroidBitmap()
      assertTrue(maxOf(bitmap.width, bitmap.height) in 96..240)
      val colors = (0 until bitmap.height step 7).flatMap { y ->
        (0 until bitmap.width step 7).map { x -> bitmap.getPixel(x, y) }
      }
      assertTrue(colors.distinct().size > 50)
    }
  }
}
