package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.viewer.FluidViewerRaster
import androidx.exifinterface.media.ExifInterface
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class CameraRaw(private val bytes: ByteArray, private val format: String) {
  private data class Range(val start: Int, val length: Int)
  private val previews = ArrayList<Range>()
  private val exifs = ArrayList<ExifInterface>()
  private val data = GraphicBytes(bytes)

  init {
    if (format == "cr3") {
      scanBoxes(0, bytes.size)
    } else {
      readExif(bytes)
      try {
        readTiffPreviews()
      } catch (_: FluidViewerException) {
        // Una IFD proprietaria può mancare anche in un file con una miniatura EXIF valida.
      }
    }
  }

  private fun readExif(input: ByteArray) {
    try {
      exifs += ExifInterface(input.inputStream())
    } catch (_: IOException) {
      // La visualizzazione della foto non dipende dalle informazioni della fotocamera.
    }
  }

  private fun readTiffPreviews() {
    val tiff = TiffDirectory(bytes)
    val pending = ArrayDeque<Long>().apply { add(tiff.first) }
    val seen = HashSet<Long>()
    while (pending.isNotEmpty() && seen.size < 96) {
      val offset = pending.removeFirst()
      if (offset == 0L || !tiff.data.valid(offset, 2) || !seen.add(offset)) continue
      val ifd = tiff.at(offset)
      pending.addLast(ifd.next)
      ifd.values(330, 64).forEach(pending::addLast)
      val starts = ifd.values(513, 16)
      val sizes = ifd.values(514, 16)
      starts.zip(sizes).forEach { (at, n) -> jpeg(at, n) }
      val strips = ifd.values(273, 256)
      val lengths = ifd.values(279, 256)
      strips.zip(lengths).forEach { (at, n) -> jpeg(at, n) }
    }
  }

  private fun jpeg(at: Long, length: Long) {
    if (previews.size < 96 && length >= 4 && data.valid(at, length) && data.u16(at.toInt()) == 0xffd8)
      previews += Range(at.toInt(), length.toInt())
  }

  private fun scanBoxes(start: Int, end: Int, depth: Int = 0) {
    if (depth > 8) return
    var offset = start
    var count = 0
    while (offset + 8 <= end && count++ < 4096) {
      val size = data.u32(offset)
      val type = data.text(offset + 4, 4)
      val header = if (size == 1L) 16 else 8
      val length = when {
        size == 0L -> (end - offset).toLong()
        size == 1L && offset + 16 <= end -> data.i64(offset + 8)
        else -> size
      }
      if (length < header || length > end - offset) return
      val body = offset + header
      val next = (offset + length).toInt()
      when (type) {
        "moov", "trak", "mdia", "minf", "stbl" -> scanBoxes(body, next, depth + 1)
        "uuid" -> if (next - body >= 16) {
          val high = data.i64(body)
          val low = data.i64(body + 8)
          when {
            high == 0xeaf42b5e1c984b88UL.toLong() && low == 0xb9fbb7dc406e4d16UL.toLong() ->
              scanBoxes(body + 24, next, depth + 1) // UUID Canon PreviewImage, otto byte di prefisso.
            high == 0x85c0b687820f11e0UL.toLong() && low == 0x8111f4ce462b6a48UL.toLong() ->
              scanBoxes(body + 16, next, depth + 1) // UUID Canon metadata (CMT1–CMT4).
          }
        }
        "CMT1", "CMT2", "CMT4" -> if (next - body in 8..1_048_576) {
          val content = data.part(body, next - body)
          if (type == "CMT1") readExif(content)
          else exifDirectory(content, if (type == "CMT2") 34665 else 34853)?.let(::readExif)
        }
        "PRVW" -> if (next - body >= 16) {
          val n = data.u32(body + 12)
          if (n <= next - body - 16) jpeg(body + 16L, n)
        }
      }
      offset = next
    }
  }

  /** Canon puts the Exif and GPS directories in independent TIFF blobs. Point a primary IFD
   * at each directory so ExifInterface reads its normal tags without moving any stored offsets. */
  private fun exifDirectory(input: ByteArray, tag: Int): ByteArray? {
    val order = when (String(input, 0, 2, Charsets.US_ASCII)) {
      "II" -> ByteOrder.LITTLE_ENDIAN
      "MM" -> ByteOrder.BIG_ENDIAN
      else -> return null
    }
    val root = input.size + input.size % 2
    val output = ByteBuffer.wrap(input.copyOf(root + 18)).order(order)
    val original = output.getInt(4)
    if (original !in 8 until input.size) return null
    output.putInt(4, root)
    output.position(root)
    output.putShort(1).putShort(tag.toShort()).putShort(4).putInt(1).putInt(original).putInt(0)
    return output.array()
  }

  suspend fun preview(maxPixels: Long, edge: Int?): Bitmap? {
    suspend fun decode(input: ByteArray, start: Int, length: Int): Bitmap? {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(input, start, length, bounds)
      if (bounds.outWidth < 1 || bounds.outHeight < 1) return null
      val (w, h) = fitRaster(bounds.outWidth, bounds.outHeight, edge)
      return FluidViewerRaster.render(null, IntSize(w, h), bytesPerPixel = 48) { size ->
        decodeRaster(input, start, length, maxPixels, maxOf(size.width, size.height))?.let { orient(it).asImageBitmap() }
      }?.asAndroidBitmap()
    }
    for (preview in previews.distinct().sortedByDescending { it.length }) {
      decode(bytes, preview.start, preview.length)?.let { return it }
    }
    // Some NEFs store the preview in a Nikon MakerNote instead of a standard TIFF preview IFD.
    // Scan JPEG markers only as a fallback and accept a complete 8-bit display image.
    for (preview in embeddedJpegs().sortedByDescending { it.length }) {
      decode(bytes, preview.start, preview.length)?.let { return it }
    }
    for (exif in exifs) {
      val thumbnail = exif.thumbnailBytes ?: continue
      decode(thumbnail, 0, thumbnail.size)?.let { return it }
    }
    return null
  }

  private suspend fun embeddedJpegs(): List<Range> = buildList {
    var i = 0
    while (i <= bytes.size - 4 && size < 64) {
      if (i and 65535 == 0) currentCoroutineContext().ensureActive()
      if (bytes[i].toInt() != -1 || bytes[i + 1].toInt() != -40 || bytes[i + 2].toInt() != -1) {
        i++
        continue
      }
      val end = jpegEnd(i)
      if (end > i) {
        add(Range(i, end - i))
        i = end
      } else i++
    }
  }

  private fun jpegEnd(start: Int): Int {
    var i = start + 2
    var display = false
    var scan = false
    while (i < bytes.size - 1) {
      if (bytes[i++].toInt() != -1) {
        if (!scan) return -1
        continue
      }
      while (i < bytes.size && bytes[i].toInt() == -1) i++
      if (i == bytes.size) return -1
      val marker = bytes[i++].toInt() and 255
      if (marker == 217) return if (display && scan) i else -1
      if (marker == 216) return -1
      if (scan && (marker == 0 || marker in 208..215)) continue
      if (i > bytes.size - 2) return -1
      val length = data.u16(i)
      if (length < 2 || length > bytes.size - i) return -1
      if (marker == 192 || marker == 193 || marker == 194) display = length >= 8 && data.u8(i + 2) == 8
      if (marker in listOf(195, 197, 198, 199, 201, 202, 203, 205, 206, 207)) return -1
      if (marker == 218) {
        if (!display) return -1
        scan = true
      }
      i += length
    }
    return -1
  }

  private fun orient(bitmap: Bitmap): Bitmap {
    val exif = exifs.firstOrNull { it.getAttribute(ExifInterface.TAG_ORIENTATION) != null } ?: return bitmap
    if (!exif.isFlipped && exif.rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply {
      if (exif.isFlipped) postScale(-1f, 1f)
      postRotate(exif.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      .also { if (it !== bitmap) bitmap.recycle() }
  }

  fun details(platform: Boolean): List<FluidViewerDetail> = buildList {
    add(FluidViewerDetail("Formato", format.uppercase(java.util.Locale.ROOT)))
    add(FluidViewerDetail("Anteprima", if (platform) "Decodifica DNG di Android" else "Sviluppo della fotocamera", "Lettura"))
    if (platform) add(FluidViewerDetail("Alternativa", "Anteprima incorporata (sviluppo della fotocamera)", "Lettura"))
    for ((label, tag) in listOf(
      "Marca" to ExifInterface.TAG_MAKE, "Modello" to ExifInterface.TAG_MODEL,
      "Obiettivo" to ExifInterface.TAG_LENS_MODEL, "Data" to ExifInterface.TAG_DATETIME_ORIGINAL,
      "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "Tempo" to ExifInterface.TAG_EXPOSURE_TIME,
      "Diaframma" to ExifInterface.TAG_F_NUMBER, "Focale" to ExifInterface.TAG_FOCAL_LENGTH,
      "Compensazione" to ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    )) exifs.firstNotNullOfOrNull { it.getAttribute(tag)?.trim()?.takeIf(String::isNotEmpty) }?.let {
      add(FluidViewerDetail(label, it, "Fotocamera ed esposizione"))
    }
    exifs.firstNotNullOfOrNull { it.latLong }?.let { add(FluidViewerDetail("Coordinate", it.joinToString(", "), "Posizione")) }
  }
}
