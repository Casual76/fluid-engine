package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.Immutable

/**
 * Cosa dicono i primi byte di una GIF o di una WebP: misura, se è animata, quanti fotogrammi e quanti giri.
 *
 * [animated] è vero da due fotogrammi in su (GIF) o con il segno d'animazione (WebP, `VP8X` con `ANIM`).
 * [frameCount] è esatto solo se i byte letti arrivano alla fine del file, altrimenti `null`: per sapere se una GIF
 * è animata basta arrivare al secondo fotogramma. [loopCount] è 0 per «all'infinito», `null` se il file non lo dice
 * (una GIF senza l'estensione NETSCAPE si gioca una volta).
 */
@Immutable
data class FluidImageHeader(
  /** `"gif"` o `"webp"`. */
  val format: String,
  val width: Int,
  val height: Int,
  val animated: Boolean,
  val frameCount: Int?,
  val loopCount: Int?,
) {
  /** Il rapporto d'aspetto, `null` se la misura manca. */
  val aspectRatio: Float? get() = if (width > 0 && height > 0) width.toFloat() / height else null

  companion object {
    /**
     * Legge l'intestazione dai primi [length] byte, o `null` se non sono una GIF o una WebP (anche troncate). Non
     * decodifica i pixel: segue i blocchi e conta i fotogrammi. Basta il primo mezzo megabyte per una GIF normale.
     */
    fun read(bytes: ByteArray, length: Int = bytes.size): FluidImageHeader? {
      val n = minOf(length, bytes.size)
      return when {
        n >= 13 && ascii(bytes, 0, "GIF8") -> gif(bytes, n)
        n >= 16 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP") -> webp(bytes, n)
        else -> null
      }
    }

    private fun ascii(b: ByteArray, at: Int, text: String): Boolean =
      at + text.length <= b.size && text.indices.all { b[at + it].toInt() == text[it].code }

    private fun u8(b: ByteArray, at: Int) = b[at].toInt() and 0xFF
    private fun le16(b: ByteArray, at: Int) = u8(b, at) or (u8(b, at + 1) shl 8)
    private fun le24(b: ByteArray, at: Int) = le16(b, at) or (u8(b, at + 2) shl 16)
    private fun le32(b: ByteArray, at: Int) = le24(b, at).toLong() or (u8(b, at + 3).toLong() shl 24)

    private fun gif(b: ByteArray, n: Int): FluidImageHeader {
      val width = le16(b, 6)
      val height = le16(b, 8)
      val flags = u8(b, 10)
      var at = 13
      if (flags and 0x80 != 0) at += 3 * (1 shl ((flags and 0x07) + 1))
      var frames = 0
      var loops: Int? = null
      var complete = false
      // Salta una catena di sotto-blocchi (misura, dati… zero); -1 se i byte finiscono prima.
      fun skipBlocks(from: Int): Int {
        var p = from
        while (p < n) {
          val size = u8(b, p)
          if (size == 0) return p + 1
          p += size + 1
        }
        return -1
      }
      loop@ while (at < n) {
        when (u8(b, at)) {
          0x21 -> {
            if (at + 2 >= n) break@loop
            val label = u8(b, at + 1)
            if (label == 0xFF && at + 14 < n && u8(b, at + 2) == 11 &&
              (ascii(b, at + 3, "NETSCAPE2.0") || ascii(b, at + 3, "ANIMEXTS1.0"))
            ) {
              val sub = at + 14
              if (sub + 3 < n && u8(b, sub) >= 3 && u8(b, sub + 1) == 1) loops = le16(b, sub + 2)
            }
            at = skipBlocks(at + 2).takeIf { it > 0 } ?: break@loop
          }
          0x2C -> {
            if (at + 10 > n) break@loop
            frames++
            val local = u8(b, at + 9)
            var p = at + 10
            if (local and 0x80 != 0) p += 3 * (1 shl ((local and 0x07) + 1))
            p += 1 // la misura minima del codice LZW
            at = skipBlocks(p).takeIf { it > 0 } ?: break@loop
          }
          0x3B -> { complete = true; break@loop }
          else -> break@loop // un blocco sconosciuto: la GIF è rotta da qui, si tiene ciò che si è contato
        }
      }
      return FluidImageHeader("gif", width, height, animated = frames > 1, frameCount = frames.takeIf { complete }, loopCount = loops)
    }

    private fun webp(b: ByteArray, n: Int): FluidImageHeader? {
      var at = 12
      var width = 0
      var height = 0
      var animationFlag = false
      var frames = 0
      var loops: Int? = null
      val riffEnd = minOf(n.toLong(), 8 + le32(b, 4)).toInt()
      var complete = false
      while (at + 8 <= riffEnd) {
        val size = le32(b, at + 4)
        val data = at + 8
        when {
          ascii(b, at, "VP8X") && data + 10 <= n -> {
            animationFlag = u8(b, data) and 0x02 != 0
            width = le24(b, data + 4) + 1
            height = le24(b, data + 7) + 1
          }
          ascii(b, at, "ANIM") && data + 6 <= n -> loops = le16(b, data + 4)
          ascii(b, at, "ANMF") -> frames++
          ascii(b, at, "VP8 ") && width == 0 && data + 10 <= n && u8(b, data + 3) == 0x9D &&
            u8(b, data + 4) == 0x01 && u8(b, data + 5) == 0x2A -> {
            width = le16(b, data + 6) and 0x3FFF
            height = le16(b, data + 8) and 0x3FFF
          }
          ascii(b, at, "VP8L") && width == 0 && data + 5 <= n && u8(b, data) == 0x2F -> {
            val bits = le32(b, data + 1)
            width = (bits and 0x3FFF).toInt() + 1
            height = ((bits shr 14) and 0x3FFF).toInt() + 1
          }
        }
        val next = data.toLong() + size + (size and 1)
        if (next > Int.MAX_VALUE) break
        at = next.toInt()
        if (at >= riffEnd) complete = riffEnd == (8 + le32(b, 4)).toInt()
      }
      if (width == 0 && height == 0 && !animationFlag) return null
      // Il segno d'animazione con un fotogramma solo, letto fino in fondo, è una WebP ferma.
      val animated = animationFlag && (!complete || frames > 1)
      return FluidImageHeader(
        "webp", width, height, animated = animated,
        frameCount = if (complete) (if (animationFlag) frames else 1) else if (!animationFlag) 1 else null,
        loopCount = loops,
      )
    }
  }
}
