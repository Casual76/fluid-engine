package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.viewer.FluidDecodedImage
import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import java.io.File

/** The camera's JPEG is already developed. A DNG with only a Bayer image needs the system decoder. */
internal suspend fun dngThumbnail(request: FluidViewerRequest, edge: Int, maxPixels: Long) =
  embeddedDngPreview(request, edge, maxPixels)?.asImageBitmap() ?: platformDngThumbnail(request, edge, maxPixels)

private suspend fun embeddedDngPreview(request: FluidViewerRequest, edge: Int, maxPixels: Long): Bitmap? =
  try {
    CameraRaw(request.source.open().use { it.readGraphic() }, "dng").preview(maxPixels, edge)
  } catch (_: FluidViewerException) {
    null
  }

private suspend fun platformDngThumbnail(request: FluidViewerRequest, edge: Int, maxPixels: Long) =
  if (Build.VERSION.SDK_INT < 28) null else try {
    request.source.seekable().use { descriptor ->
      val image = FluidDecodedImage.decode(File("/proc/self/fd/${descriptor.fd}"), minOf(maxPixels, edge.toLong() * edge),
        animate = false).still?.asAndroidBitmap() ?: return@use null
      val (width, height) = fitRaster(image.width, image.height, edge)
      Bitmap.createScaledBitmap(image, width, height, true)
        .also { if (it !== image) image.recycle() }.asImageBitmap()
    }
  } catch (_: FluidViewerException) {
    null
  }
