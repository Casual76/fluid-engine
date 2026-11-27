// ThumbHash, portato in Kotlin dalla versione di riferimento di Evan Wallace (https://github.com/evanw/thumbhash,
// `java/com/madebyevan/thumbhash/ThumbHash.java` e `js/thumbhash.js`). Copyright (c) 2023 Evan Wallace. Licenza MIT:
// il testo intero è in LICENSES/ThumbHash.md dell'engine. Le formule sono quelle dell'originale, riga per riga; i nomi e i
// commenti sono nostri.

package dev.antigravity.fluidengine.ui.fluid

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Un'immagine piccola decodificata da un ThumbHash: [width] × [height] pixel (al più 32 per lato) in RGBA, un byte per
 * canale, non premoltiplicati.
 */
class FluidThumbHashImage internal constructor(val width: Int, val height: Int, val rgba: ByteArray)

/**
 * ThumbHash (F8.1, `immagine-che-arriva`): l'impronta di un'immagine in **poche decine di byte**, che si decodifica in
 * una miniatura sfocata coi colori veri, le proporzioni e la trasparenza. È il segnaposto di [FluidAsyncImage]: l'app (o
 * il server) la calcola una volta al caricamento con [encode] e la salva accanto all'immagine.
 *
 * Porta del codice di riferimento di Evan Wallace, MIT (`LICENSES/ThumbHash.md`), senza librerie.
 */
object FluidThumbHash {
  /**
   * L'impronta di un'immagine di [width] × [height] pixel (al più 100 per lato: si rimpicciolisce prima, il dettaglio
   * oltre non entra comunque nell'impronta), in RGBA non premoltiplicato, un byte per canale.
   */
  fun encode(width: Int, height: Int, rgba: ByteArray): ByteArray {
    require(width in 1..100 && height in 1..100) { "${width}x$height non sta in 100x100" }
    require(rgba.size == width * height * 4) { "servono ${width * height * 4} byte, ne arrivano ${rgba.size}" }
    // Il colore medio.
    var avgR = 0f
    var avgG = 0f
    var avgB = 0f
    var avgA = 0f
    var j = 0
    for (i in 0 until width * height) {
      val alpha = (rgba[j + 3].toInt() and 255) / 255f
      avgR += alpha / 255f * (rgba[j].toInt() and 255)
      avgG += alpha / 255f * (rgba[j + 1].toInt() and 255)
      avgB += alpha / 255f * (rgba[j + 2].toInt() and 255)
      avgA += alpha
      j += 4
    }
    if (avgA > 0f) {
      avgR /= avgA
      avgG /= avgA
      avgB /= avgA
    }
    val hasAlpha = avgA < width * height
    val lLimit = if (hasAlpha) 5 else 7 // meno bit alla luminanza se c'è la trasparenza
    val lx = max(1, (lLimit.toFloat() * width / max(width, height)).roundToInt())
    val ly = max(1, (lLimit.toFloat() * height / max(width, height)).roundToInt())
    val l = FloatArray(width * height) // luminanza
    val p = FloatArray(width * height) // giallo - blu
    val q = FloatArray(width * height) // rosso - verde
    val a = FloatArray(width * height) // alfa
    // Da RGBA a LPQA, sopra il colore medio.
    j = 0
    for (i in 0 until width * height) {
      val alpha = (rgba[j + 3].toInt() and 255) / 255f
      val r = avgR * (1f - alpha) + alpha / 255f * (rgba[j].toInt() and 255)
      val g = avgG * (1f - alpha) + alpha / 255f * (rgba[j + 1].toInt() and 255)
      val b = avgB * (1f - alpha) + alpha / 255f * (rgba[j + 2].toInt() and 255)
      l[i] = (r + g + b) / 3f
      p[i] = (r + g) / 2f - b
      q[i] = r - g
      a[i] = alpha
      j += 4
    }
    val lChannel = Channel(max(3, lx), max(3, ly)).encode(width, height, l)
    val pChannel = Channel(3, 3).encode(width, height, p)
    val qChannel = Channel(3, 3).encode(width, height, q)
    val aChannel = if (hasAlpha) Channel(5, 5).encode(width, height, a) else null
    // Le costanti.
    val isLandscape = width > height
    val header24 = (63f * lChannel.dc).roundToInt() or
      ((31.5f + 31.5f * pChannel.dc).roundToInt() shl 6) or
      ((31.5f + 31.5f * qChannel.dc).roundToInt() shl 12) or
      ((31f * lChannel.scale).roundToInt() shl 18) or
      (if (hasAlpha) 1 shl 23 else 0)
    val header16 = (if (isLandscape) ly else lx) or
      ((63f * pChannel.scale).roundToInt() shl 3) or
      ((63f * qChannel.scale).roundToInt() shl 9) or
      (if (isLandscape) 1 shl 15 else 0)
    val acStart = if (hasAlpha) 6 else 5
    val acCount = lChannel.ac.size + pChannel.ac.size + qChannel.ac.size + (aChannel?.ac?.size ?: 0)
    val hash = ByteArray(acStart + (acCount + 1) / 2)
    hash[0] = header24.toByte()
    hash[1] = (header24 shr 8).toByte()
    hash[2] = (header24 shr 16).toByte()
    hash[3] = header16.toByte()
    hash[4] = (header16 shr 8).toByte()
    if (aChannel != null) {
      hash[5] = ((15f * aChannel.dc).roundToInt() or ((15f * aChannel.scale).roundToInt() shl 4)).toByte()
    }
    // I fattori che variano.
    var acIndex = 0
    for (channel in listOfNotNull(lChannel, pChannel, qChannel, aChannel)) {
      for (f in channel.ac) {
        val at = acStart + (acIndex shr 1)
        hash[at] = (hash[at].toInt() or ((15f * f).roundToInt() shl ((acIndex and 1) shl 2))).toByte()
        acIndex++
      }
    }
    return hash
  }

  /** La miniatura dell'impronta: al più 32 pixel sul lato lungo, con le proporzioni dell'immagine. */
  fun decode(hash: ByteArray): FluidThumbHashImage {
    require(hash.size >= 5) { "un ThumbHash ha almeno 5 byte" }
    val header24 = (hash[0].toInt() and 255) or ((hash[1].toInt() and 255) shl 8) or ((hash[2].toInt() and 255) shl 16)
    val header16 = (hash[3].toInt() and 255) or ((hash[4].toInt() and 255) shl 8)
    val lDc = (header24 and 63) / 63f
    val pDc = ((header24 shr 6) and 63) / 31.5f - 1f
    val qDc = ((header24 shr 12) and 63) / 31.5f - 1f
    val lScale = ((header24 shr 18) and 31) / 31f
    val hasAlpha = (header24 shr 23) != 0
    // R2 (difetto 27): con l'alfa il sesto byte e' l'intestazione dell'alfa. Un'impronta corrotta di 5 byte si rifiuta
    // con lo stesso errore delle altre, non con un indice fuori dall'array.
    require(!hasAlpha || hash.size >= 6) { "un ThumbHash con l'alfa ha almeno 6 byte" }
    val pScale = ((header16 shr 3) and 63) / 63f
    val qScale = ((header16 shr 9) and 63) / 63f
    val isLandscape = (header16 shr 15) != 0
    val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else header16 and 7)
    val ly = max(3, if (isLandscape) header16 and 7 else (if (hasAlpha) 5 else 7))
    val aDc = if (hasAlpha) (hash[5].toInt() and 15) / 15f else 1f
    val aScale = if (hasAlpha) ((hash[5].toInt() and 255) shr 4) / 15f else 0f
    // I fattori, con la saturazione alzata di 1,25 per compensare la quantizzazione (come l'originale).
    val acStart = if (hasAlpha) 6 else 5
    var acIndex = 0
    fun channel(nx: Int, ny: Int, scale: Float): FloatArray {
      val ac = ArrayList<Float>()
      for (cy in 0 until ny) {
        var cx = if (cy > 0) 0 else 1
        while (cx * ny < nx * (ny - cy)) {
          val at = acStart + (acIndex shr 1)
          val nibble = if (at < hash.size) ((hash[at].toInt() and 255) shr ((acIndex and 1) shl 2)) and 15 else 0
          ac += (nibble / 7.5f - 1f) * scale
          acIndex++
          cx++
        }
      }
      return ac.toFloatArray()
    }
    val lAc = channel(lx, ly, lScale)
    val pAc = channel(3, 3, pScale * 1.25f)
    val qAc = channel(3, 3, qScale * 1.25f)
    val aAc = if (hasAlpha) channel(5, 5, aScale) else null
    // Dalla DCT a RGBA.
    val ratio = aspectRatio(hash)
    val w = (if (ratio > 1f) 32f else 32f * ratio).roundToInt().coerceAtLeast(1)
    val h = (if (ratio > 1f) 32f / ratio else 32f).roundToInt().coerceAtLeast(1)
    val rgba = ByteArray(w * h * 4)
    val cxStop = max(lx, if (hasAlpha) 5 else 3)
    val cyStop = max(ly, if (hasAlpha) 5 else 3)
    val fx = FloatArray(cxStop)
    val fy = FloatArray(cyStop)
    var i = 0
    for (y in 0 until h) {
      for (x in 0 until w) {
        var l = lDc
        var p = pDc
        var q = qDc
        var a = aDc
        for (cx in 0 until cxStop) fx[cx] = cos(PI / w * (x + 0.5) * cx).toFloat()
        for (cy in 0 until cyStop) fy[cy] = cos(PI / h * (y + 0.5) * cy).toFloat()
        // L
        var k = 0
        for (cy in 0 until ly) {
          var cx = if (cy > 0) 0 else 1
          val fy2 = fy[cy] * 2f
          while (cx * ly < lx * (ly - cy)) {
            l += lAc[k] * fx[cx] * fy2
            k++
            cx++
          }
        }
        // P e Q
        k = 0
        for (cy in 0 until 3) {
          var cx = if (cy > 0) 0 else 1
          val fy2 = fy[cy] * 2f
          while (cx < 3 - cy) {
            val f = fx[cx] * fy2
            p += pAc[k] * f
            q += qAc[k] * f
            k++
            cx++
          }
        }
        // A
        if (aAc != null) {
          k = 0
          for (cy in 0 until 5) {
            var cx = if (cy > 0) 0 else 1
            val fy2 = fy[cy] * 2f
            while (cx < 5 - cy) {
              a += aAc[k] * fx[cx] * fy2
              k++
              cx++
            }
          }
        }
        // In RGB
        val b = l - 2f / 3f * p
        val r = (3f * l - b + q) / 2f
        val g = r - q
        rgba[i] = (max(0f, 255f * min(1f, r))).toInt().toByte()
        rgba[i + 1] = (max(0f, 255f * min(1f, g))).toInt().toByte()
        rgba[i + 2] = (max(0f, 255f * min(1f, b))).toInt().toByte()
        rgba[i + 3] = (max(0f, 255f * min(1f, a))).toInt().toByte()
        i += 4
      }
    }
    return FluidThumbHashImage(w, h, rgba)
  }

  /** Il colore medio dell'impronta: il fondo più semplice mentre l'immagine arriva. */
  fun averageColor(hash: ByteArray): Color {
    require(hash.size >= 5) { "un ThumbHash ha almeno 5 byte" }
    val header = (hash[0].toInt() and 255) or ((hash[1].toInt() and 255) shl 8) or ((hash[2].toInt() and 255) shl 16)
    val l = (header and 63) / 63f
    val p = ((header shr 6) and 63) / 31.5f - 1f
    val q = ((header shr 12) and 63) / 31.5f - 1f
    val hasAlpha = (header shr 23) != 0
    require(!hasAlpha || hash.size >= 6) { "un ThumbHash con l'alfa ha almeno 6 byte" }
    val a = if (hasAlpha) (hash[5].toInt() and 15) / 15f else 1f
    val b = l - 2f / 3f * p
    val r = (3f * l - b + q) / 2f
    val g = r - q
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a)
  }

  /** Le proporzioni (larghezza / altezza) dell'immagine, approssimate, lette dall'impronta. */
  fun aspectRatio(hash: ByteArray): Float {
    require(hash.size >= 5) { "un ThumbHash ha almeno 5 byte" }
    val header = hash[3].toInt() and 255
    val hasAlpha = (hash[2].toInt() and 0x80) != 0
    val isLandscape = (hash[4].toInt() and 0x80) != 0
    val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else header and 7
    val ly = if (isLandscape) header and 7 else (if (hasAlpha) 5 else 7)
    return lx.toFloat() / ly.coerceAtLeast(1)
  }

  /**
   * L'impronta come immagine per Compose, **decodificata una volta e tenuta**: in una griglia lunga la stessa impronta
   * torna a ogni scorrimento (la scheda dell'idea: «la decodifica delle impronte va messa in cache»). Al più 128
   * impronte, le meno usate escono.
   */
  fun imageBitmap(hash: ByteArray): ImageBitmap {
    val key = hash.toKey()
    Cache.get(key)?.let { return it }
    val image = decode(hash)
    val pixels = IntArray(image.width * image.height)
    var j = 0
    for (index in pixels.indices) {
      val r = image.rgba[j].toInt() and 255
      val g = image.rgba[j + 1].toInt() and 255
      val b = image.rgba[j + 2].toInt() and 255
      val a = image.rgba[j + 3].toInt() and 255
      pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
      j += 4
    }
    val bitmap = Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    Cache.put(key, bitmap)
    return bitmap
  }

  private val Cache = LruCache<String, ImageBitmap>(128)

  private fun ByteArray.toKey(): String {
    val chars = CharArray(size * 2)
    for (index in indices) {
      val v = this[index].toInt() and 255
      chars[index * 2] = Hex[v shr 4]
      chars[index * 2 + 1] = Hex[v and 15]
    }
    return String(chars)
  }

  private val Hex = "0123456789abcdef".toCharArray()

  /** Un canale della DCT: i suoi fattori costante ([dc]) e variabili normalizzati ([ac]) e la scala. */
  private class Channel(val nx: Int, val ny: Int) {
    var dc = 0f
    var ac = FloatArray(0)
    var scale = 0f

    fun encode(w: Int, h: Int, channel: FloatArray): Channel {
      val fx = FloatArray(w)
      val acList = ArrayList<Float>()
      for (cy in 0 until ny) {
        var cx = 0
        while (cx * ny < nx * (ny - cy)) {
          var f = 0f
          for (x in 0 until w) fx[x] = cos(PI / w * cx * (x + 0.5)).toFloat()
          for (y in 0 until h) {
            val fy = cos(PI / h * cy * (y + 0.5)).toFloat()
            for (x in 0 until w) f += channel[x + y * w] * fx[x] * fy
          }
          f /= (w * h).toFloat()
          if (cx > 0 || cy > 0) {
            acList += f
            scale = max(scale, abs(f))
          } else {
            dc = f
          }
          cx++
        }
      }
      ac = acList.toFloatArray()
      if (scale > 0f) for (index in ac.indices) ac[index] = 0.5f + 0.5f / scale * ac[index]
      return this
    }
  }
}
