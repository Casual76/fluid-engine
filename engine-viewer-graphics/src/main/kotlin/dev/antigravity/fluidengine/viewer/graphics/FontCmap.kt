package dev.antigravity.fluidengine.viewer.graphics

/** Unicode cmap formats used by the specimen. A mapping to glyph zero is absent. */
internal class FontCmap(
  private val data: GraphicBytes,
  private val start: Int,
  private val length: Int,
  private val glyphCount: Int?,
) {
  private val subtables = buildList {
    if (length < 4) return@buildList
    val visited = HashSet<Int>()
    val count = minOf(data.u16(start + 2), 256, (length - 4) / 8)
    repeat(count) { index ->
      val record = start + 4 + index * 8
      val platform = data.u16(record)
      val encoding = data.u16(record + 2)
      if (platform != 0 && !(platform == 3 && (encoding == 1 || encoding == 10))) return@repeat
      val offset = data.u32(record + 4)
      if (offset > length - 4 || !visited.add(offset.toInt())) return@repeat
      val at = start + offset.toInt()
      val format = data.u16(at)
      val bytes = when (format) {
        0, 4, 6 -> data.u16(at + 2).toLong()
        10, 12, 13 -> if (length - offset >= 8) data.u32(at + 4) else 0L
        else -> 0L
      }
      if (bytes in 4..(length - offset)) add(Subtable(at, bytes.toInt(), format))
    }
  }

  operator fun contains(codePoint: Int): Boolean = subtables.any {
    val glyph = it.glyph(codePoint)
    glyph > 0 && (glyphCount == null || glyph < glyphCount)
  }

  private inner class Subtable(private val at: Int, private val bytes: Int, private val format: Int) {
    private fun word(offset: Int) = if (offset in 0..bytes - 2) data.u16(at + offset) else 0
    private fun long(offset: Int) = if (offset in 0..bytes - 4) data.u32(at + offset) else 0L

    fun glyph(codePoint: Int): Long = when (format) {
      0 -> if (codePoint in 0..255 && 6 + codePoint < bytes) data.u8(at + 6 + codePoint).toLong() else 0L
      4 -> segments(codePoint).toLong()
      6 -> trimmed(codePoint, word(6).toLong(), word(8).toLong(), 10)
      10 -> trimmed(codePoint, long(12), long(16), 20)
      else -> groups(codePoint)
    }

    private fun trimmed(codePoint: Int, first: Long, count: Long, valuesAt: Int): Long {
      val index = codePoint - first
      if (index < 0 || index >= count || valuesAt + index * 2 > bytes - 2) return 0
      return word(valuesAt + index.toInt() * 2).toLong()
    }

    private fun segments(codePoint: Int): Int {
      if (codePoint !in 0..0xfffe) return 0
      val count = word(6) / 2
      if (count == 0 || 16 + 8 * count > bytes) return 0
      val startCodes = 16 + 2 * count
      val index = firstAtLeast(count, codePoint.toLong()) { word(14 + 2 * it).toLong() }
      if (index == count || codePoint < word(startCodes + 2 * index)) return 0
      val delta = word(startCodes + 2 * count + 2 * index)
      val rangeAt = startCodes + 4 * count + 2 * index
      val rangeOffset = word(rangeAt)
      if (rangeOffset == 0) return (codePoint + delta) and 65535
      val entry = rangeAt + rangeOffset + 2 * (codePoint - word(startCodes + 2 * index))
      if (entry < 16 + 8 * count) return 0
      val glyph = word(entry)
      return if (glyph == 0) 0 else (glyph + delta) and 65535
    }

    private fun groups(codePoint: Int): Long {
      val count = long(12)
      if (count == 0L || count > (bytes - 16) / 12) return 0
      val index = firstAtLeast(count.toInt(), codePoint.toLong()) { long(20 + 12 * it) }
      if (index == count.toInt()) return 0
      val first = long(16 + 12 * index)
      if (codePoint < first) return 0
      val glyph = long(24 + 12 * index)
      return if (format == 13) glyph else glyph + codePoint - first
    }
  }
}

private inline fun firstAtLeast(count: Int, target: Long, value: (Int) -> Long): Int {
  var low = 0
  var high = count
  while (low < high) {
    val mid = (low + high) ushr 1
    if (value(mid) < target) low = mid + 1 else high = mid
  }
  return low
}
