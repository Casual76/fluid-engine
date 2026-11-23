package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import java.io.IOException

internal class Photoshop(bytes: ByteArray) {
  private val data = GraphicBytes(bytes)
  private val version = data.u16(4)
  private val channels = data.u16(12)
  val height = data.u32(14).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: corrupt("Altezza Photoshop non valida")
  val width = data.u32(18).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: corrupt("Larghezza Photoshop non valida")
  private val bits = data.u16(22)
  private val mode = data.u16(24)
  private val colorSize = data.u32(26)
  private val resourcesAt = data.offset(30L + colorSize, 4)
  private val resourcesEnd = data.offset(resourcesAt + 4L + data.u32(resourcesAt))
  private val large = version == 2
  private val layerLengthField = if (large) 8 else 4
  private val layerLength = lengthAt(resourcesEnd)
  private val layerStart = data.offset(resourcesEnd.toLong() + layerLengthField, layerLength)
  private val composite = (layerStart + layerLength).toInt()
  private val layerInfoSize = if (layerLength >= layerLengthField) lengthAt(layerStart) else 0L
  private val hasLayerCount = layerInfoSize >= 2 && layerInfoSize <= layerLength - layerLengthField
  private val layerCount = if (hasLayerCount) data.u16(layerStart + layerLengthField).toShort().toInt() else 0

  init {
    if (data.text(0, 4) != "8BPS" || version !in 1..2 || width <= 0 || height <= 0 || channels !in 1..56)
      corrupt("Intestazione Photoshop non valida")
  }

  private fun lengthAt(offset: Int): Long = if (large) data.i64(offset) else data.u32(offset)

  fun image(maxPixels: Long, edge: Int? = null): Pair<Bitmap, Boolean> {
    return try {
      renderComposite(maxPixels, edge) to false
    } catch (e: IOException) {
      thumbnail(maxPixels, edge)?.let { it to true } ?: throw e
    }
  }

  fun details(preview: Boolean): List<FluidViewerDetail> = listOf(
    FluidViewerDetail("Formato", if (large) "PSB" else "PSD"),
    FluidViewerDetail("Dimensioni", "${width} × ${height} px"),
    FluidViewerDetail("Livelli", kotlin.math.abs(layerCount).toString(), "Immagine"),
    FluidViewerDetail("Colore", when (mode) {
      0 -> "Bitmap"; 1 -> "Scala di grigi"; 2 -> "Indicizzato"; 3 -> "RGB"; 4 -> "CMYK"; 9 -> "Lab"
      else -> "Modo $mode"
    }, "Immagine"),
    FluidViewerDetail("Profondità", "$bits bit per canale", "Immagine"),
    FluidViewerDetail("Anteprima", if (preview) "Miniatura salvata in Photoshop" else "Immagine composita", "Lettura"),
  )

  private fun thumbnail(maxPixels: Long, edge: Int?): Bitmap? {
    var offset = resourcesAt + 4
    while (offset + 12 <= resourcesEnd && data.text(offset, 4) == "8BIM") {
      val id = data.u16(offset + 4)
      val nameLength = data.u8(offset + 6) + 1
      val sizeField = data.offset(offset + 6L + nameLength + nameLength % 2, 4)
      val n = data.u32(sizeField)
      val begin = sizeField + 4
      if (n > resourcesEnd - begin) return null
      if (id in listOf(1036, 1033) && n > 28 && data.u32(begin) == 1L) {
        decodeRaster(data.bytes, begin + 28, n.toInt() - 28, maxPixels, edge)?.let { return it }
      }
      offset = (begin + n + n % 2).toInt()
    }
    return null
  }

  private fun renderComposite(maxPixels: Long, edge: Int?): Bitmap {
    if (composite + 2 > data.size) unsupported("Il file Photoshop non contiene l'immagine composita")
    checkPixels(width, height, maxPixels)
    val compression = data.u16(composite)
    val planes = when (mode) { 1, 2 -> 1; 3 -> 3; 4 -> 4; else -> unsupported("Modo colore Photoshop non supportato") }
    if (bits !in listOf(8, 16) || mode == 2 && (colorSize < 768 || bits != 8) || channels < planes ||
      compression !in 0..1) unsupported("Immagine composita Photoshop non supportata")
    val hasAlpha = layerCount < 0 && channels > planes
    val components = planes + if (hasAlpha) 1 else 0
    val stride = width * (bits / 8)
    val start = composite + 2
    val rowCount = height.toLong() * channels
    val lengthBytes = if (large) 4 else 2
    val rowOffsets: IntArray
    val rowLengths: IntArray
    if (compression == 0) {
      data.offset(start.toLong(), rowCount * stride)
      rowOffsets = IntArray(0)
      rowLengths = IntArray(0)
    } else {
      val tableEnd = data.offset(start + rowCount * lengthBytes)
      rowLengths = IntArray(height * components)
      rowOffsets = IntArray(rowLengths.size)
      var offset = tableEnd
      for (row in 0 until rowCount.toInt()) {
        val field = start + row * lengthBytes
        val length = if (large) data.u32(field) else data.u16(field).toLong()
        val end = data.offset(offset + length)
        if (row < rowOffsets.size) {
          rowOffsets[row] = offset
          rowLengths[row] = length.toInt()
        }
        offset = end
      }
    }
    val (outWidth, outHeight) = fitRaster(width, height, edge)
    val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(outWidth)
    try {
      for (y in 0 until outHeight) {
        val sourceY = (y.toLong() * height / outHeight).toInt()
        val rows = List(components) { component ->
          val row = component * height + sourceY
          if (compression == 0) data.part(start + row * stride, stride)
          else unpackBits(data.part(rowOffsets[row], rowLengths[row]), stride)
        }
        for (x in 0 until outWidth) {
          val index = (x.toLong() * width / outWidth).toInt() * (bits / 8)
          fun sample(channel: Int) = rows[channel][index].toInt() and 255
          val a = if (hasAlpha) sample(planes) else 255
          val gray = sample(0)
          val red = when (mode) { 2 -> data.u8(30 + gray); 4 -> gray * sample(3) / 255; else -> gray }
          val green = when (mode) { 2 -> data.u8(286 + gray); 3 -> sample(1); 4 -> sample(1) * sample(3) / 255; else -> gray }
          val blue = when (mode) { 2 -> data.u8(542 + gray); 3 -> sample(2); 4 -> sample(2) * sample(3) / 255; else -> gray }
          pixels[x] = (a shl 24) or (red shl 16) or (green shl 8) or blue
        }
        output.setPixels(pixels, 0, outWidth, 0, y, outWidth, 1)
      }
      return output
    } catch (t: Throwable) {
      output.recycle()
      throw t
    }
  }
}

internal fun fitRaster(width: Int, height: Int, maxEdge: Int?): Pair<Int, Int> {
  val largest = maxOf(width, height)
  if (maxEdge == null || maxEdge >= largest) return width to height
  return (width.toLong() * maxEdge / largest).toInt().coerceAtLeast(1) to
    (height.toLong() * maxEdge / largest).toInt().coerceAtLeast(1)
}

internal fun decodeRaster(
  bytes: ByteArray, offset: Int, length: Int, maxPixels: Long, maxEdge: Int? = null,
): Bitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, offset, length, bounds)
  val width = bounds.outWidth
  val height = bounds.outHeight
  if (width <= 0 || height <= 0) return null
  var sample = 1
  while (width.toLong().div(sample) * (height.toLong().div(sample)) > maxPixels ||
    (maxEdge != null && maxOf(width, height) / sample > maxEdge * 2L)
  ) sample *= 2
  val bitmap = BitmapFactory.decodeByteArray(bytes, offset, length, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
  val (w, h) = fitRaster(bitmap.width, bitmap.height, maxEdge)
  return Bitmap.createScaledBitmap(bitmap, w, h, true).also { if (it !== bitmap) bitmap.recycle() }
}
