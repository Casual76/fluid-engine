package dev.antigravity.fluidengine.viewer.data

import dev.antigravity.fluidengine.viewer.FluidTextCharset
import dev.antigravity.fluidengine.viewer.FluidTextDecoder
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** I byte di un file letti fino a un tetto: [length] utili in [bytes], [truncated] se il file continua. */
internal class DataBytes(val bytes: ByteArray, val length: Int, val truncated: Boolean) {
  fun copy(): ByteArray = bytes.copyOf(length)
}

/** Un testo decodificato: il charset riconosciuto (con il BOM), se è stato tagliato, i suoi a capo. */
internal class DataDecoded(val text: String, val charset: FluidTextCharset, val truncated: Boolean, val bytesRead: Int) {
  val newline: String by lazy { DataText.newline(text) }
  val lineCount: Int by lazy { if (text.isEmpty()) 0 else text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1 }
}

/** La lettura dei testi della famiglia, in Kotlin puro: il charset lo decide FluidTextDecoder, come per il preset testo. */
internal object DataText {

  /** Legge [input] fino a [limit] byte; il buffer cresce col file, non parte dal tetto. */
  fun read(input: InputStream, limit: Int, sizeHint: Long? = null): DataBytes {
    val known = sizeHint?.takeIf { it >= 0 }?.plus(1)?.coerceAtMost(limit.toLong())?.toInt()
    var data = ByteArray((known ?: minOf(limit, 64 * 1024)).coerceAtLeast(1))
    var filled = 0
    while (filled < limit) {
      if (filled == data.size) data = data.copyOf(minOf(limit, maxOf(data.size * 2, 8192)))
      val n = input.read(data, filled, data.size - filled)
      if (n < 0) break
      filled += n
    }
    val truncated = filled >= limit && input.read() >= 0
    return DataBytes(data, filled, truncated)
  }

  /**
   * Decodifica [bytes]: il BOM vince, poi [declared] (il `charset=` di un MIME o di un `<meta>`), poi la stima di
   * FluidTextDecoder (UTF-8 senza errori, altrimenti windows-1252). I byte non validi diventano U+FFFD.
   */
  fun decode(bytes: DataBytes, declared: String? = null): DataDecoded {
    val charset = FluidTextDecoder.detect(bytes.bytes, bytes.length, declared = declared?.let { "text/plain; charset=$it" },
      complete = !bytes.truncated)
    val text = decodeWith(bytes.bytes, charset.bomLength, bytes.length - charset.bomLength, charset.charset)
    return DataDecoded(text.removePrefix("\uFEFF"), charset, bytes.truncated, bytes.length)
  }

  /** [length] byte da [offset] nel [charset], con la sostituzione per i byte non validi. */
  fun decodeWith(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String {
    if (length <= 0) return ""
    val decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    return decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString()
  }

  /** Il charset di [name] se Java lo conosce (alias compresi: «latin1», «utf8», «cp1252»), altrimenti `null`. */
  fun charsetOf(name: String?): Charset? {
    val clean = name?.trim()?.trim('"', '\'')?.takeIf { it.isNotEmpty() } ?: return null
    val aliases = mapOf("utf8" to "UTF-8", "latin1" to "ISO-8859-1", "cp1252" to "windows-1252", "ascii" to "US-ASCII",
      "us-ascii" to "US-ASCII", "unicode-1-1-utf-8" to "UTF-8", "x-unknown" to "", "unknown-8bit" to "", "ks_c_5601-1987" to "EUC-KR",
      "gb2312" to "GBK")
    val mapped = aliases[clean.lowercase(Locale.ROOT)] ?: clean
    if (mapped.isEmpty()) return null
    return try {
      Charset.forName(mapped)
    } catch (_: Exception) {
      null
    }
  }

  /** «LF», «CRLF», «CR», «misti» o «nessuno»: come il pannello info del preset testo. */
  fun newline(text: String): String {
    var lf = 0
    var crlf = 0
    var cr = 0
    var i = 0
    while (i < text.length) {
      when (text[i]) {
        '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') { crlf++; i++ } else cr++
        '\n' -> lf++
      }
      i++
    }
    val kinds = listOf(lf, crlf, cr).count { it > 0 }
    return when {
      kinds == 0 -> "nessuno"
      kinds > 1 -> "misti"
      crlf > 0 -> "CRLF"
      cr > 0 -> "CR"
      else -> "LF"
    }
  }

  /**
   * Il charset dichiarato da un documento HTML nei primi 1024 byte (`<meta charset>` o `content="…; charset=…"`),
   * letto come fa un browser: in ASCII, prima di decodificare.
   */
  fun htmlCharset(bytes: ByteArray, length: Int): String? {
    val head = String(bytes, 0, minOf(length, 1024), Charsets.ISO_8859_1).lowercase(Locale.ROOT)
    val match = Regex("""<meta[^>]+charset\s*=\s*["']?\s*([a-z0-9_\-:.]+)""").find(head) ?: return null
    return match.groupValues[1]
  }

  /** Il charset dichiarato in testa a un XML (`<?xml version="1.0" encoding="…"?>`). */
  fun xmlCharset(bytes: ByteArray, length: Int): String? {
    val head = String(bytes, 0, minOf(length, 256), Charsets.ISO_8859_1)
    return Regex("""<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9_\-.:]+)["']""").find(head)?.groupValues?.get(1)
  }

  /** La dimensione decimale per le persone: «2,3 KB», «12,4 MB» (come `FluidFileInfo`). */
  fun size(bytes: Long): String {
    if (bytes < 1000) return "$bytes byte"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
      value /= 1000
      unit++
    }
    val text = if (value >= 100) "%.0f".format(Locale.ITALY, value) else "%.1f".format(Locale.ITALY, value).removeSuffix(",0")
    return "$text ${units[unit]}"
  }

  /** Un numero intero con il punto delle migliaia: «100.000». */
  fun count(n: Int): String = "%,d".format(Locale.ITALY, n)
  fun count(n: Long): String = "%,d".format(Locale.ITALY, n)

  /** Il nome del charset per le persone: «UTF-8», «UTF-8 con BOM», «windows-1252 (stimato)». */
  fun charsetLabel(charset: FluidTextCharset): String {
    val name = charset.charset.name()
    return when {
      charset.bomLength > 0 -> "$name con BOM"
      charset.certain -> name
      else -> "$name (riconosciuto)"
    }
  }
}
