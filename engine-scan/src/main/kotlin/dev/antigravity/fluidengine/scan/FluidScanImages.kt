/*
 * engine-scan (4.0, F8.17): raddrizzare e pulire la pagina. La prospettiva con la matrice di Android
 * (`Matrix.setPolyToPoly` a quattro punti), i filtri «Documento», «Scala di grigi», «Bianco e nero» e «Foto» come quelli
 * di VisionKit, scritti qui. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Il filtro di una pagina: i quattro di VisionKit (Color, Grayscale, Black & White, Photo). Non è un `enum` di proposito
 * (regola 3.11): un filtro nuovo è una costante in più.
 */
@Immutable
@JvmInline
value class FluidScanFilter private constructor(val id: String) {
  override fun toString(): String = id

  companion object {
    /** Il foglio pulito a colori: carta bianca, ombre tolte, inchiostro più deciso. Quello di serie, come in VisionKit. */
    val Document: FluidScanFilter = FluidScanFilter("documento")
    /** Come [Document], in scala di grigi. */
    val Grayscale: FluidScanFilter = FluidScanFilter("grigi")
    /** Solo bianco e nero, con la soglia che segue la luce di ogni zona: per i testi da archiviare o da faxare. */
    val BlackAndWhite: FluidScanFilter = FluidScanFilter("bianco-e-nero")
    /** La foto raddrizzata, senza ritocchi: per le foto e i disegni. */
    val Photo: FluidScanFilter = FluidScanFilter("foto")

    /** I filtri nell'ordine del menu. */
    val All: List<FluidScanFilter> = listOf(Document, Grayscale, BlackAndWhite, Photo)

    /** Il filtro col nome [id], o [Document]. */
    fun of(id: String?): FluidScanFilter = All.firstOrNull { it.id == id } ?: Document
  }
}

/** Raddrizzare e pulire le pagine. Tutto fuori dal filo principale: sono millisecondi su milioni di pixel. */
object FluidScanImages {
  /**
   * Il lato lungo della pagina raddrizzata: 2400 pixel, circa 205 punti per pollice su un A4. A occhio (25 novembre
   * 2026): si legge e si stampa bene, il testo si riconosce, e una pagina in memoria resta sotto i 17 MB.
   */
  const val DefaultMaxLongSide: Int = 2400

  /**
   * La misura della pagina raddrizzata dal foglio [quad] in una foto [width] × [height]: le proporzioni vere
   * ([estimatedAspect]) e il lato lungo dei pixel che la foto ha davvero, mai più di [maxLongSide].
   */
  fun outputSize(quad: FluidScanQuad, width: Int, height: Int, maxLongSide: Int = DefaultMaxLongSide): Pair<Int, Int> {
    val aspect = quad.estimatedAspect(width, height).coerceIn(0.1f, 10f)
    val (_, longest) = quad.sideRange(width, height)
    val long = min(maxLongSide.toFloat(), max(64f, longest)).roundToInt()
    return if (aspect >= 1f) long to max(1, (long / aspect).roundToInt()) else max(1, (long * aspect).roundToInt()) to long
  }

  /**
   * La pagina raddrizzata: il foglio [quad] di [photo] portato a un rettangolo con le proporzioni vere, girato di
   * [quarterTurns] quarti in senso orario. Il campionamento è bilineare; da una foto molto più grande della pagina si
   * riduce prima a metà quante volte serve, così i caratteri piccoli non si sgranano.
   */
  fun straighten(photo: Bitmap, quad: FluidScanQuad, quarterTurns: Int = 0, maxLongSide: Int = DefaultMaxLongSide): Bitmap {
    val (outW, outH) = outputSize(quad, photo.width, photo.height, maxLongSide)
    // Riduzioni a metà finché la foto non è al più il doppio della pagina: il bilineare non salta più pixel di quanti ne legge.
    var source = photo
    var factor = 1f
    val (_, longest) = quad.sideRange(photo.width, photo.height)
    while (longest * factor / 2f > max(outW, outH) * 1.5f && source.width > 64 && source.height > 64) {
      val half = Bitmap.createScaledBitmap(source, source.width / 2, source.height / 2, true)
      if (source !== photo) source.recycle()
      source = half
      factor /= 2f
    }
    val src = FloatArray(8)
    quad.points.forEachIndexed { i, p ->
      src[2 * i] = p.x * source.width
      src[2 * i + 1] = p.y * source.height
    }
    val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
    val matrix = Matrix()
    check(matrix.setPolyToPoly(src, 0, dst, 0, 4)) { "Il foglio non si raddrizza" }
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
      drawColor(android.graphics.Color.WHITE)
      drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
    if (source !== photo) source.recycle()
    return rotate(out, quarterTurns)
  }

  /** [bitmap] girata di [quarterTurns] quarti di giro in senso orario (la stessa se zero). */
  fun rotate(bitmap: Bitmap, quarterTurns: Int): Bitmap {
    val turns = quarterTurns.mod(4)
    if (turns == 0) return bitmap
    val matrix = Matrix().apply { postRotate(90f * turns) }
    val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (out !== bitmap) bitmap.recycle()
    return out
  }

  /** La pagina col filtro [filter]: una bitmap nuova (o la stessa con [FluidScanFilter.Photo]). */
  fun filter(bitmap: Bitmap, filter: FluidScanFilter): Bitmap {
    if (filter == FluidScanFilter.Photo) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    ScanFilters.apply(pixels, w, h, filter)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
  }
}

/**
 * I filtri sui pixel, senza Android (si provano sul computer). L'idea è quella degli scanner di lavagne (Zhang e He,
 * 2007, § 4): **la luce della carta** si stima zona per zona come il chiaro più chiaro dei dintorni (un massimo su una
 * griglia grossa, poi sfumato), e ogni pixel si divide per lei. La carta diventa bianca anche sotto un'ombra, e
 * l'inchiostro resta del suo colore.
 */
internal object ScanFilters {
  fun apply(pixels: IntArray, w: Int, h: Int, filter: FluidScanFilter) {
    val light = paperLight(pixels, w, h)
    when (filter) {
      FluidScanFilter.Document -> document(pixels, light, color = true)
      FluidScanFilter.Grayscale -> document(pixels, light, color = false)
      FluidScanFilter.BlackAndWhite -> blackAndWhite(pixels, light, w, h)
      else -> Unit
    }
  }

  /**
   * La luce della carta per ogni pixel (luminanza 0..255): il 90° percentile di ogni cella di una griglia di circa
   * 40 × 40 celle, allargato di una cella (massimo) e poi sfumato due volte (media 3 × 3), letto in bilineare.
   */
  fun paperLight(pixels: IntArray, w: Int, h: Int): FloatArray {
    val cell = max(8, max(w, h) / 40)
    val gw = (w + cell - 1) / cell
    val gh = (h + cell - 1) / cell
    val grid = FloatArray(gw * gh)
    val histogram = IntArray(64)
    for (gy in 0 until gh) for (gx in 0 until gw) {
      java.util.Arrays.fill(histogram, 0)
      var count = 0
      val x1 = min(w, (gx + 1) * cell)
      val y1 = min(h, (gy + 1) * cell)
      var y = gy * cell
      while (y < y1) {
        var x = gx * cell
        while (x < x1) {
          histogram[luma(pixels[y * w + x]) shr 2]++
          count++
          x += 2
        }
        y += 2
      }
      val target = (count * 0.9f).toInt()
      var sum = 0
      var value = 63
      for (i in 0 until 64) { sum += histogram[i]; if (sum > target) { value = i; break } }
      grid[gy * gw + gx] = (value * 4 + 2).toFloat()
    }
    // Un'ombra stretta (il bordo del telefono) non deve sembrare carta scura: allargare il chiaro di una cella.
    val dilated = FloatArray(grid.size)
    for (gy in 0 until gh) for (gx in 0 until gw) {
      var m = 0f
      for (dy in -1..1) for (dx in -1..1) {
        val x = (gx + dx).coerceIn(0, gw - 1)
        val y = (gy + dy).coerceIn(0, gh - 1)
        m = max(m, grid[y * gw + x])
      }
      dilated[gy * gw + gx] = m
    }
    var smooth = dilated
    repeat(2) {
      val next = FloatArray(smooth.size)
      for (gy in 0 until gh) for (gx in 0 until gw) {
        var s = 0f
        for (dy in -1..1) for (dx in -1..1) {
          val x = (gx + dx).coerceIn(0, gw - 1)
          val y = (gy + dy).coerceIn(0, gh - 1)
          s += smooth[y * gw + x]
        }
        next[gy * gw + gx] = s / 9f
      }
      smooth = next
    }
    val light = FloatArray(w * h)
    for (y in 0 until h) {
      val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
      val y0 = fy.toInt(); val y1 = min(gh - 1, y0 + 1); val ty = fy - y0
      for (x in 0 until w) {
        val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
        val x0 = fx.toInt(); val x1 = min(gw - 1, x0 + 1); val tx = fx - x0
        val a = smooth[y0 * gw + x0] * (1 - tx) + smooth[y0 * gw + x1] * tx
        val b = smooth[y1 * gw + x0] * (1 - tx) + smooth[y1 * gw + x1] * tx
        light[y * w + x] = max(24f, a * (1 - ty) + b * ty)
      }
    }
    return light
  }

  /**
   * «Documento»: ogni canale diviso per la luce della carta (la carta a 255), poi i livelli: il nero al 2% più scuro, una
   * curva che scurisce un poco l'inchiostro (gamma 1,4) e il quasi bianco portato a bianco. In grigio con [color] falso.
   */
  private fun document(pixels: IntArray, light: FloatArray, color: Boolean) {
    // Il punto del nero: il 2° percentile della luminanza già normalizzata.
    val histogram = IntArray(256)
    for (i in pixels.indices) histogram[(luma(pixels[i]) * 255f / light[i]).toInt().coerceIn(0, 255)]++
    var sum = 0
    var black = 0
    val target = pixels.size / 50
    for (i in 0 until 256) { sum += histogram[i]; if (sum > target) { black = i; break } }
    black = min(black, 120)
    val curve = IntArray(256) { v ->
      val t = ((v - black).toFloat() / (WhitePoint - black)).coerceIn(0f, 1f)
      (255f * Math.pow(t.toDouble(), 1.4).toFloat()).roundToInt().coerceIn(0, 255)
    }
    for (i in pixels.indices) {
      val c = pixels[i]
      val gain = 255f / light[i]
      if (color) {
        val r = curve[((c shr 16 and 0xFF) * gain).toInt().coerceIn(0, 255)]
        val g = curve[((c shr 8 and 0xFF) * gain).toInt().coerceIn(0, 255)]
        val b = curve[((c and 0xFF) * gain).toInt().coerceIn(0, 255)]
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
      } else {
        val y = curve[(luma(c) * gain).toInt().coerceIn(0, 255)]
        pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
      }
    }
  }

  /**
   * «Bianco e nero»: la luminanza normalizzata e poi la soglia di Bradley (media di un riquadro di un sedicesimo della
   * pagina con l'immagine integrale): nero se sotto l'85% della media dei dintorni e sotto 200 su 255.
   */
  private fun blackAndWhite(pixels: IntArray, light: FloatArray, w: Int, h: Int) {
    val norm = IntArray(pixels.size) { (luma(pixels[it]) * 255f / light[it]).toInt().coerceIn(0, 255) }
    val integral = LongArray((w + 1) * (h + 1))
    for (y in 0 until h) {
      var row = 0L
      for (x in 0 until w) {
        row += norm[y * w + x]
        integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row
      }
    }
    val half = max(4, max(w, h) / 32)
    for (y in 0 until h) {
      val y0 = max(0, y - half); val y1 = min(h, y + half + 1)
      for (x in 0 until w) {
        val x0 = max(0, x - half); val x1 = min(w, x + half + 1)
        val area = (x1 - x0) * (y1 - y0)
        val total = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
        val v = norm[y * w + x]
        val ink = v * area < total * 0.85f && v < 200
        pixels[y * w + x] = if (ink) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
      }
    }
  }

  fun luma(c: Int): Int = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000

  /** Il quasi bianco della carta normalizzata che diventa bianco: 235. A occhio, come la carta di VisionKit. */
  private const val WhitePoint = 235
}
