package dev.antigravity.fluidengine.viewer.graphics

import java.nio.ByteOrder

internal class TiffDirectory(bytes: ByteArray) {
  private val little = bytes.size >= 2 && bytes[0] == 73.toByte() && bytes[1] == 73.toByte()
  val order = if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
  val data = GraphicBytes(bytes, order)
  val first: Long

  init {
    if (data.text(0, 2) !in setOf("II", "MM") || data.u16(2) != 42) corrupt("Intestazione TIFF non valida")
    first = data.u32(4)
  }

  inner class Directory(private val start: Int) {
    private val entryCount = data.u16(start)
    private val end = data.offset(start.toLong() + 2L + entryCount * 12, 4)
    private val tags = buildMap {
      repeat(entryCount) { index ->
        val at = start + 2 + index * 12
        put(data.u16(at), at)
      }
    }
    val next get() = data.u32(end)

    fun values(tag: Int, max: Int = 262_144): LongArray {
      val entry = tags[tag] ?: return LongArray(0)
      val type = data.u16(entry + 2)
      val width = when (type) {
        1, 7 -> 1
        3 -> 2
        4, 13 -> 4
        else -> return LongArray(0)
      }
      val n = data.u32(entry + 4)
      if (n > max) tooLarge("Tabella TIFF troppo grande")
      val length = n * width
      val origin = if (length <= 4) entry + 8 else data.offset(data.u32(entry + 8), length)
      return LongArray(n.toInt()) { index ->
        val at = origin + index * width
        when (width) {
          1 -> data.u8(at).toLong()
          2 -> data.u16(at).toLong()
          else -> data.u32(at)
        }
      }
    }

    fun number(tag: Int, default: Int = 0): Int = values(tag, 16).firstOrNull()?.let {
      if (it > Int.MAX_VALUE) tooLarge("Dimensione TIFF troppo grande")
      it.toInt()
    } ?: default
  }

  fun at(offset: Long): Directory = Directory(data.offset(offset, 2))

  fun pages(): List<Directory> {
    val seen = HashSet<Long>()
    return buildList {
      var offset = first
      while (offset != 0L) {
        if (!seen.add(offset)) corrupt("Indice TIFF circolare")
        if (size == 512) tooLarge("Il TIFF supera 512 pagine")
        val page = at(offset)
        add(page)
        offset = page.next
      }
      if (isEmpty()) corrupt("Il TIFF non contiene pagine")
    }
  }
}
