package dev.antigravity.fluidengine.viewer.graphics

import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

internal const val MaxGraphicBytes = 96 * 1024 * 1024
internal const val MaxRasterPixels = 16_000_000L

internal fun corrupt(message: String): Nothing = throw FluidViewerException(FluidViewerFailure.Corrupt, message)
internal fun unsupported(message: String): Nothing = throw FluidViewerException(FluidViewerFailure.Unsupported, message)
internal fun tooLarge(message: String): Nothing = throw FluidViewerException(FluidViewerFailure.TooLarge, message)

internal suspend fun InputStream.readGraphic(limit: Int = MaxGraphicBytes): ByteArray =
  dev.antigravity.fluidengine.viewer.FluidViewerRaster.readBytes(this, limit)

internal class GraphicBytes(val bytes: ByteArray, order: ByteOrder = ByteOrder.BIG_ENDIAN) {
  private val buffer = ByteBuffer.wrap(bytes).order(order)
  val size get() = bytes.size
  fun valid(at: Long, length: Long) = at >= 0 && length >= 0 && at <= size && length <= size - at
  fun offset(at: Long, length: Long = 0): Int {
    if (!valid(at, length)) corrupt("Dati grafici incompleti")
    return at.toInt()
  }

  fun u8(at: Int): Int = bytes[offset(at.toLong(), 1)].toInt() and 255
  fun u16(at: Int): Int = buffer.getShort(offset(at.toLong(), 2)).toInt() and 65535
  fun u32(at: Int): Long = buffer.getInt(offset(at.toLong(), 4)).toLong() and 0xffffffffL
  fun i64(at: Int): Long = buffer.getLong(offset(at.toLong(), 8))
  fun text(at: Int, length: Int): String = String(bytes, offset(at.toLong(), length.toLong()), length, Charsets.ISO_8859_1)
  fun part(at: Int, length: Int): ByteArray = bytes.copyOfRange(offset(at.toLong(), length.toLong()), at + length)
}

internal fun inflateExactly(input: ByteArray, size: Int): ByteArray {
  val data = ByteArray(size)
  InflaterInputStream(input.inputStream()).use { stream ->
    var offset = 0
    while (offset < size) {
      val n = stream.read(data, offset, size - offset)
      if (n < 0) corrupt("Dati compressi incompleti")
      offset += n
    }
    if (stream.read() != -1) corrupt("I dati gonfiati superano la misura dichiarata")
  }
  return data
}

internal fun unpackBits(input: ByteArray, size: Int): ByteArray {
  val out = ByteArray(size)
  var src = 0
  var dst = 0
  while (src < input.size && dst < size) {
    val control = input[src++].toInt()
    if (control == -128) continue
    val count = if (control >= 0) control + 1 else 1 - control
    if (count > size - dst) corrupt("Dati PackBits non validi")
    if (control >= 0) {
      if (count > input.size - src) corrupt("Riga PackBits interrotta")
      input.copyInto(out, dst, src, src + count)
      src += count
    } else {
      if (src == input.size) corrupt("Riga PackBits interrotta")
      out.fill(input[src++], dst, dst + count)
    }
    dst += count
  }
  if (dst != size) corrupt("Riga PackBits incompleta")
  return out
}

internal fun checkPixels(width: Int, height: Int, max: Long = MaxRasterPixels) {
  if (width <= 0 || height <= 0) corrupt("Dimensioni non valide")
  if (width.toLong() * height > max) tooLarge("L'immagine supera il limite di ${max / 1_000_000} megapixel")
}
