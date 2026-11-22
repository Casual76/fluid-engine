package dev.antigravity.fluidengine.viewer.graphics

import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Locale

internal const val MaxFontBytes = 32 * 1024 * 1024

internal data class FontAxis(val tag: String, val name: String, val min: Float, val normal: Float, val max: Float)

internal class FontFace(
  val names: Map<Int, String>,
  val glyphCount: Int?,
  val unitsPerEm: Int?,
  val weight: Int?,
  val alphabets: List<String>,
  val axes: List<FontAxis>,
) {
  val family get() = names[16] ?: names[1] ?: names[4] ?: "Carattere"
  val style get() = names[17] ?: names[2] ?: "Normale"

  fun details(): List<FluidViewerDetail> = buildList {
    add(FluidViewerDetail("Famiglia", family))
    add(FluidViewerDetail("Stile", style))
    if (axes.isNotEmpty()) add(FluidViewerDetail("Assi", axes.joinToString(", ") { it.tag }))
    for ((id, label) in listOf(4 to "Nome completo", 6 to "Nome PostScript", 5 to "Versione", 8 to "Produttore", 9 to "Autore"))
      names[id]?.let { add(FluidViewerDetail(label, it, "Nomi")) }
    for ((id, label) in listOf(0 to "Copyright", 13 to "Licenza", 14 to "Riferimento"))
      names[id]?.let { add(FluidViewerDetail(label, it, "Licenza")) }
    glyphCount?.let { add(FluidViewerDetail("Glifi", it.toString(), "Carattere")) }
    unitsPerEm?.let { add(FluidViewerDetail("Unità per em", it.toString(), "Carattere")) }
    weight?.let { add(FluidViewerDetail("Peso OpenType", it.toString(), "Carattere")) }
    if (alphabets.isNotEmpty()) add(FluidViewerDetail("Scritture", alphabets.joinToString(", "), "Carattere"))
  }
}

internal class FontFile(input: ByteArray) {
  val bytes = if (GraphicBytes(input).text(0, 4) == "wOFF") decodeWoff(input) else input
  private val data = GraphicBytes(bytes)
  val faces = if (data.text(0, 4) == "ttcf") {
    val count = data.u32(8)
    if (count !in 1..128) corrupt("La collezione di caratteri non ha un indice valido")
    List(count.toInt()) { index -> parseFace(data.offset(data.u32(12 + index * 4), 12)) }
  } else listOf(parseFace(0))

  private fun parseFace(header: Int): FontFace {
    if (data.u32(header) != 0x00010000L && data.text(header, 4) !in setOf("OTTO", "true"))
      unsupported("Formato del carattere non supportato")
    val count = data.u16(header + 4)
    if (count !in 1..4095) corrupt("Indice delle tabelle del carattere non valido")
    data.offset(header + 12L, count * 16L)
    val tables = buildMap {
      repeat(count) { i ->
        val at = header + 12 + i * 16
        val length = data.u32(at + 12)
        val start = data.offset(data.u32(at + 8), length)
        put(data.text(at, 4), Table(start, length.toInt()))
      }
    }
    val names = tables["name"]?.let(::names).orEmpty()
    val glyphCount = tables["maxp"]?.word(4)
    val unitsPerEm = tables["head"]?.word(18)
    val weight = tables["OS/2"]?.word(4)
    val alphabets = tables["cmap"]?.let(::alphabets).orEmpty()
    val axes = tables["fvar"]?.let { axes(it, names) }.orEmpty()
    return FontFace(names, glyphCount, unitsPerEm, weight, alphabets, axes)
  }

  private inner class Table(val at: Int, val size: Int) {
    fun has(offset: Int, count: Int) = offset >= 0 && count >= 0 && offset <= size && count <= size - offset
    fun word(offset: Int) = if (has(offset, 2)) data.u16(at + offset) else null
    fun long(offset: Int) = if (has(offset, 4)) data.u32(at + offset) else null
  }

  private fun names(table: Table): Map<Int, String> {
    val count = table.word(2) ?: return emptyMap()
    val strings = table.word(4) ?: return emptyMap()
    val records = HashMap<Int, Pair<Int, String>>()
    for (i in 0 until count.coerceAtMost(4096)) {
      val record = 6 + i * 12
      if (!table.has(record, 12)) break
      val platform = table.word(record)!!
      val encoding = table.word(record + 2)!!
      val language = table.word(record + 4)!!
      val name = table.word(record + 6)!!
      val size = table.word(record + 8)!!
      val start = strings + table.word(record + 10)!!
      val charset = when {
        platform == 0 || platform == 3 && encoding in listOf(0, 1, 10) -> Charsets.UTF_16BE
        platform == 1 && encoding == 0 -> runCatching { Charset.forName("x-MacRoman") }.getOrNull() ?: continue
        else -> continue
      }
      if (!table.has(start, size) || size !in 1..8192) continue
      val rank = when {
        platform == 3 && language and 1023 == 16 -> 5
        platform == 3 && language and 1023 == 9 -> 4
        platform == 0 -> 3
        platform == 1 && language == 0 -> 2
        else -> 1
      }
      if ((records[name]?.first ?: 0) >= rank) continue
      val value = String(bytes, table.at + start, size, charset).trim().takeIf { it.isNotEmpty() } ?: continue
      records[name] = rank to value
    }
    return records.mapValues { it.value.second }
  }

  private fun axes(table: Table, names: Map<Int, String>): List<FontAxis> {
    val start = table.word(4) ?: return emptyList()
    val count = table.word(8) ?: return emptyList()
    val stride = table.word(10) ?: return emptyList()
    if (count > 64 || stride < 20 || !table.has(start, count * stride)) corrupt("Assi variabili non validi")
    fun fixed(offset: Int) = table.long(offset)!!.toInt() / 65536f
    return List(count) { i ->
      val at = start + i * stride
      val tag = data.text(table.at + at, 4)
      val min = fixed(at + 4)
      val normal = fixed(at + 8)
      val max = fixed(at + 12)
      if (tag.any { it !in ' '..'~' || it == '\'' || it == ',' } || min > normal || normal > max)
        corrupt("Asse variabile non valido")
      val name = table.word(at + 18)!!
      FontAxis(tag, names[name] ?: tag, min, normal, max)
    }
  }

  private fun alphabets(table: Table): List<String> {
    val count = table.word(2) ?: return emptyList()
    val coverage = linkedMapOf(
      "Latino" to 0x0041..0x024f, "Greco" to 0x0370..0x03ff, "Cirillico" to 0x0400..0x052f,
      "Ebraico" to 0x0590..0x05ff, "Arabo" to 0x0600..0x06ff, "Devanagari" to 0x0900..0x097f,
      "Kana" to 0x3041..0x30ff, "Han" to 0x4e00..0x9fff, "Hangul" to 0xac00..0xd7a3,
    )
    val found = HashSet<String>()
    fun add(first: Int, last: Int) {
      for ((name, range) in coverage) if (first <= range.last && last >= range.first) found += name
    }
    val seen = HashSet<Int>()
    for (i in 0 until count.coerceAtMost(256)) {
      val record = 4 + i * 8
      val platform = table.word(record) ?: break
      val encoding = table.word(record + 2) ?: break
      if (platform != 0 && !(platform == 3 && encoding in listOf(1, 10))) continue
      val at = table.long(record + 4)?.takeIf { it <= table.size - 2 }?.toInt() ?: continue
      if (!seen.add(at)) continue
      when (table.word(at)) {
        4 -> {
          val segments = (table.word(at + 6) ?: 0) / 2
          val starts = at + 16 + 2 * segments
          if (!table.has(starts, 2 * segments)) continue
          for (index in 0 until segments) {
            val first = table.word(starts + index * 2)!!
            val last = table.word(at + 14 + index * 2) ?: continue
            if (first != 65535 && first <= last) add(first, last)
          }
        }
        12, 13 -> {
          val groups = table.long(at + 12)?.takeIf { it <= 100_000 && table.has(at + 16, it.toInt() * 12) } ?: continue
          for (index in 0 until groups.toInt()) {
            val group = at + 16 + index * 12
            val first = table.long(group)!!
            val last = table.long(group + 4)!!
            if (first <= last && last <= 0x10ffff) add(first.toInt(), last.toInt())
          }
        }
      }
      if (found.size == coverage.size) break
    }
    return coverage.keys.filter(found::contains)
  }
}

/** WOFF 1.0, sezioni 4 e 5: il font ricostruito contiene soltanto le tabelle SFNT. */
internal fun decodeWoff(input: ByteArray): ByteArray {
  val data = GraphicBytes(input)
  if (data.text(0, 4) != "wOFF" || data.u32(8) != input.size.toLong() || data.u16(14) != 0)
    corrupt("Intestazione WOFF non valida")
  val count = data.u16(12)
  val outputSize = data.u32(16)
  if (outputSize > MaxFontBytes) tooLarge("Il carattere WOFF supera il limite dopo la decompressione")
  if (count !in 1..4095 || outputSize < 12 + count * 16) corrupt("Indice WOFF non valido")
  val directoryEnd = data.offset(44 + count * 20L)
  val output = ByteBuffer.allocate(outputSize.toInt())
  val power = Integer.highestOneBit(count)
  output.putInt(data.u32(4).toInt()).putShort(count.toShort()).putShort((16 * power).toShort())
    .putShort(Integer.numberOfTrailingZeros(power).toShort()).putShort((16 * (count - power)).toShort())
  var writeAt = 12 + count * 16
  var head = -1
  var previousTag = -1L
  for (i in 0 until count) {
    val at = 44 + i * 20
    val tag = data.u32(at)
    val offset = data.u32(at + 4)
    val compressed = data.u32(at + 8)
    val size = data.u32(at + 12)
    val padded = (size + 3) and -4L
    if (tag <= previousTag || offset < directoryEnd || offset % 4 != 0L || compressed > size || padded > outputSize - writeAt)
      corrupt("Tabella WOFF non valida")
    val source = data.offset(offset, compressed)
    previousTag = tag
    val table = data.part(source, compressed.toInt())
    val raw = if (compressed == size) table else inflateExactly(table, size.toInt())
    if (tag == 0x68656164L && raw.size >= 12) {
      raw.fill(0, 8, 12)
      head = writeAt
    }
    output.putInt(tag.toInt()).putInt(data.u32(at + 16).toInt()).putInt(writeAt).putInt(size.toInt())
    raw.copyInto(output.array(), writeAt)
    writeAt += padded.toInt()
  }
  if (writeAt != outputSize.toInt() || head < 0) corrupt("Il carattere WOFF non ha le tabelle richieste")
  var checksum = 0
  for (i in 0 until writeAt step 4) checksum += output.getInt(i)
  output.putInt(head + 8, 0xB1B0AFBA.toInt() - checksum)
  return output.array()
}

internal fun Float.axisValue(): String = String.format(Locale.getDefault(), "%.2f", this).trimEnd('0').trimEnd('.', ',')
