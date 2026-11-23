package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

internal class TiffPages(bytes: ByteArray, private val maxPixels: Long) : FluidPageSource {
  private val file = TiffDirectory(bytes)
  private val pages = file.pages().map(::Page)
  override val count get() = pages.size

  override suspend fun size(index: Int): IntSize = pages[index].let { IntSize(it.width, it.height) }

  override suspend fun render(index: Int, target: IntSize) = withContext(Dispatchers.Default) {
    pages[index].render(target).asImageBitmap()
  }

  private inner class Page(ifd: TiffDirectory.Directory) {
    val width = ifd.number(256)
    val height = ifd.number(257)
    private val photo = ifd.number(262, 1)
    private val samples = ifd.number(277, if (photo == 2) 3 else 1)
    private val bits = ifd.values(258, 8).firstOrNull()?.toInt() ?: 1
    private val compression = ifd.number(259, 1)
    private val prediction = ifd.number(317, 1)
    private val tile = ifd.number(322) > 0
    private val blockWidth = if (tile) ifd.number(322) else width
    private val blockHeight = if (tile) ifd.number(323) else (ifd.values(278, 1).firstOrNull() ?: height.toLong()).coerceAtMost(height.toLong()).toInt()
    private val offsets = ifd.values(if (tile) 324 else 273)
    private val lengths = ifd.values(if (tile) 325 else 279)
    private val planar = ifd.number(284, 1)
    private val alpha = ifd.values(338, 8).firstOrNull()?.toInt() ?: 0
    private val palette = if (photo == 3) ifd.values(320) else LongArray(0)
    private val across: Int
    private val down: Int
    private val planeCount: Int
    private val storedSamples: Int

    init {
      checkPixels(width, height, maxPixels)
      val sampleFormat = ifd.values(339, 8)
      if (photo !in 0..3 || bits !in listOf(8, 16) || ifd.values(258, 8).any { it.toInt() != bits } ||
        samples !in (if (photo == 2) 3..4 else 1..2) || sampleFormat.any { it != 1L } ||
        planar !in 1..2 || prediction !in 1..2 || compression !in listOf(1, 5, 8, 32946, 32773)
      ) unsupported("Variante TIFF non supportata")
      if (photo == 3 && palette.size != 3 * (1 shl bits)) corrupt("Tavolozza TIFF incompleta")
      checkPixels(blockWidth, blockHeight, maxPixels)
      across = (width - 1) / blockWidth + 1
      down = (height - 1) / blockHeight + 1
      planeCount = if (planar == 2) samples else 1
      storedSamples = if (planar == 2) 1 else samples
      if (across.toLong() * down * planeCount != offsets.size.toLong() || offsets.size != lengths.size)
        corrupt("Indice delle strisce o dei tasselli TIFF incompleto")
      offsets.indices.forEach { file.data.offset(offsets[it], lengths[it]) }
    }

    suspend fun render(target: IntSize): Bitmap {
      val outputWidth = target.width.coerceIn(1, width)
      val outputHeight = target.height.coerceIn(1, height)
      val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
      val row = IntArray(outputWidth)
      val blockX = IntArray(outputWidth) { x -> (x.toLong() * width / outputWidth).toInt() }
      var strip = -1
      var blocks = emptyList<List<ByteArray>>()
      try {
        repeat(outputHeight) { y ->
          currentCoroutineContext().ensureActive()
          val sourceY = (y.toLong() * height / outputHeight).toInt()
          val index = sourceY / blockHeight
          if (index != strip) {
            strip = index
            blocks = List(across) { x -> List(planeCount) { plane -> block(index, x, plane) } }
          }
          for (x in row.indices) {
            val sourceX = blockX[x]
            row[x] = color(blocks[sourceX / blockWidth], sourceX % blockWidth, sourceY % blockHeight)
          }
          bitmap.setPixels(row, 0, outputWidth, 0, y, outputWidth, 1)
        }
        return bitmap
      } catch (t: Throwable) {
        bitmap.recycle()
        throw t
      }
    }

    private fun block(y: Int, x: Int, plane: Int): ByteArray {
      val h = if (tile) blockHeight else minOf(blockHeight, height - y * blockHeight)
      val size = blockWidth * h * storedSamples * (bits / 8)
      val index = plane * across * down + y * across + x
      val offset = file.data.offset(offsets[index], lengths[index])
      val input = file.data.part(offset, lengths[index].toInt())
      val decoded = when (compression) {
        1 -> input.also { if (it.size < size) corrupt("Striscia TIFF incompleta") }
        5 -> tiffLzw(input, size)
        32773 -> unpackBits(input, size)
        else -> inflateExactly(input, size)
      }
      if (prediction == 2) undoPrediction(decoded, h)
      return decoded
    }

    private fun sample(bytes: ByteArray, index: Int): Int {
      val at = index * (bits / 8)
      if (bits == 8) return bytes[at].toInt() and 255
      val first = bytes[at].toInt() and 255
      val second = bytes[at + 1].toInt() and 255
      return if (file.order == ByteOrder.LITTLE_ENDIAN) first or (second shl 8) else (first shl 8) or second
    }

    private fun undoPrediction(bytes: ByteArray, rows: Int) {
      val stride = blockWidth * storedSamples
      for (y in 0 until rows) for (x in storedSamples until stride) {
        val index = y * stride + x
        val value = sample(bytes, index) + sample(bytes, index - storedSamples)
        val at = index * (bits / 8)
        if (bits == 8) bytes[at] = value.toByte()
        else {
          val little = file.order == ByteOrder.LITTLE_ENDIAN
          bytes[at] = (if (little) value else value ushr 8).toByte()
          bytes[at + 1] = (if (little) value ushr 8 else value).toByte()
        }
      }
    }

    private fun color(planes: List<ByteArray>, x: Int, y: Int): Int {
      val point = y * blockWidth + x
      fun raw(channel: Int): Int = if (planar == 2) sample(planes[channel], point)
        else sample(planes[0], point * samples + channel)
      fun component(channel: Int) = raw(channel) ushr (bits - 8)
      val value = component(0)
      val entry = if (photo == 3) raw(0) else 0
      val a = if (alpha in 1..2 && samples > (if (photo == 2) 3 else 1)) component(samples - 1) else 255
      fun unpremultiply(c: Int) = if (alpha == 1 && a in 1..254) (c * 255 / a).coerceAtMost(255) else c
      val r = when (photo) { 0 -> 255 - value; 3 -> (palette[entry] ushr 8).toInt(); else -> value }
      val g = when (photo) { 0 -> r; 2 -> component(1); 3 -> (palette[entry + palette.size / 3] ushr 8).toInt(); else -> value }
      val b = when (photo) { 0 -> r; 2 -> component(2); 3 -> (palette[entry + 2 * palette.size / 3] ushr 8).toInt(); else -> value }
      return (a shl 24) or (unpremultiply(r) shl 16) or (unpremultiply(g) shl 8) or unpremultiply(b)
    }
  }
}

/** TIFF 6.0, §13: MSB first, with the code width advanced one entry early. */
private fun tiffLzw(input: ByteArray, count: Int): ByteArray {
  val result = ByteArray(count)
  val prefix = IntArray(4096)
  val suffix = ByteArray(4096)
  val chain = ByteArray(4096)
  var width = 9
  var next = 258
  var last = -1
  var bit = 0
  var output = 0
  var first = 0
  while (output < count && bit + width <= input.size.toLong() * 8) {
    var code = 0
    repeat(width) {
      code = (code shl 1) or ((input[bit / 8].toInt() ushr (7 - bit % 8)) and 1)
      bit++
    }
    if (code == 256) {
      width = 9
      next = 258
      last = -1
      continue
    }
    if (code == 257) break
    if (code > next || (code == next && last < 0)) corrupt("Codice LZW non valido")
    var pointer = code
    var length = 0
    if (code == next) {
      chain[length++] = first.toByte()
      pointer = last
    }
    while (pointer >= 258) {
      chain[length++] = suffix[pointer]
      pointer = prefix[pointer]
    }
    first = pointer
    chain[length++] = first.toByte()
    if (length > count - output) corrupt("Striscia LZW più lunga della misura dichiarata")
    for (i in length - 1 downTo 0) result[output++] = chain[i]
    if (last != -1 && next < 4096) {
      prefix[next] = last
      suffix[next++] = first.toByte()
      if (next == (1 shl width) - 1 && width < 12) width++
    }
    last = code
  }
  if (output != count) corrupt("Striscia LZW incompleta")
  return result
}
