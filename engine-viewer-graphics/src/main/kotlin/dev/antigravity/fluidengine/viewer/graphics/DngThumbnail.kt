package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.viewer.FluidDecodedImage
import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import java.io.File

/** The camera's JPEG is already developed. A DNG with only a Bayer image needs the system decoder. */
internal suspend fun dngThumbnail(request: FluidViewerRequest, edge: Int, maxPixels: Long) =
  (try { firstDngPreview(request, edge) } catch (_: java.io.IOException) { null })?.asImageBitmap() ?: embeddedDngPreview(request, edge, maxPixels)?.asImageBitmap()
    ?: platformDngThumbnail(request, edge, maxPixels)

/** Un IFD di un DNG può indicare la JPEG sviluppata. Il RAW intero rimane sul disco. */
private suspend fun firstDngPreview(request: FluidViewerRequest, edge: Int): Bitmap? {
  val range = request.source.open().use { raw ->
    val input = DataInputStream(raw)
    val field = ByteBuffer.wrap(ByteArray(12))
    input.readFully(field.array(), 0, 8)
    field.order(when (field.getShort(0).toInt()) {
      0x4949 -> ByteOrder.LITTLE_ENDIAN
      0x4d4d -> ByteOrder.BIG_ENDIAN
      else -> return null
    })
    fun short(at: Int) = field.getShort(at).toInt() and 65535
    fun uint(at: Int) = field.getInt(at).toLong() and 0xffffffffL
    if (short(2) != 42) return null
    var ifd = uint(4)
    var position = 8L
    // Alcune fotocamere mettono la JPEG nell'IFD0, altre dopo i tasselli RAW nell'IFD1.
    repeat(32) {
      if (ifd < position || ifd > MaxGraphicBytes || !input.skipExact(ifd - position)) return null
      input.readFully(field.array(), 0, 2)
      val fields = short(0)
      if (fields > 4096) return null
      var offset = 0L
      var length = 0L
      var compression = 0L
      var orientation = 1L
      repeat(fields) {
        input.readFully(field.array())
        if (uint(4) == 1L) {
          val value = when (short(2)) { 3 -> short(8).toLong(); 4 -> uint(8); else -> 0L }
          when (short(0)) {
            259 -> compression = value
            273, 513 -> offset = value
            279, 514 -> length = value
            274 -> orientation = value
          }
        }
      }
      if (orientation == 1L && compression in 6L..7L && offset > 0 && length in 1..MaxGraphicBytes.toLong() &&
        offset <= MaxGraphicBytes - length) return@use offset to length
      input.readFully(field.array(), 0, 4)
      position = ifd + 2 + fields * 12L + 4
      ifd = uint(0)
    }
    return null
  }
  suspend fun decode(options: BitmapFactory.Options): Bitmap? = request.source.open().use { input ->
    if (!input.skipExact(range.first)) return@use null
    BitmapFactory.decodeStream(object : java.io.FilterInputStream(input) {
      var left = range.second
      override fun read(): Int = if (left == 0L) -1 else super.read().also { if (it >= 0) left-- }
      override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left == 0L) return -1
        return super.read(b, off, minOf(len.toLong(), left).toInt()).also { if (it > 0) left -= it }
      }
    }, null, options)
  }
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  decode(bounds)
  if (bounds.outMimeType != "image/jpeg" || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  var sample = 1
  while (maxOf(bounds.outWidth, bounds.outHeight).toLong() / sample > edge * 2L) sample *= 2
  val bitmap = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
  val (w, h) = fitRaster(bitmap.width, bitmap.height, edge)
  return Bitmap.createScaledBitmap(bitmap, w, h, true).also { if (bitmap !== it) bitmap.recycle() }
}

private fun InputStream.skipExact(count: Long): Boolean {
  var left = count
  while (left > 0) {
    val skipped = skip(left)
    if (skipped > 0) left -= skipped
    else if (read() < 0) return false else left--
  }
  return true
}

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
