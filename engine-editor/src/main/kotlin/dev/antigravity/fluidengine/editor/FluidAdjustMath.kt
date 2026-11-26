package dev.antigravity.fluidengine.editor

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Le curve delle regolazioni. **Una fonte sola** per il calcolo sulla CPU (le tabelle di [AdjustPlan]) e per lo shader
 * dell'anteprima ([AdjustShader]): ogni costante qui ha la sua gemella nel testo AGSL, e `FluidAdjustShaderTest` controlla
 * che il testo dica gli stessi numeri.
 *
 * **Le fonti.** Foto non pubblica le sue curve. Dove Core Image ha il filtro pubblico equivalente (documentato nella «Core
 * Image Filter Reference») la forma viene da li', fiducia media: l'esposizione e' la moltiplicazione nella luce lineare di
 * `CIExposureAdjust` (`s.rgb * pow(2, ev)`), saturazione e luminanza quelle di `CIColorControls` (pesi Rec. 709), la
 * vividezza quella di `CIVibrance` (spinge i colori spenti), il calore e la tinta il guadagno sui canali di
 * `CITemperatureAndTint`, la nitidezza una maschera di contrasto sulla luminanza come `CISharpenLuminance`, la
 * vignettatura la caduta radiale di `CIVignette`. **Quanto** sposta ogni regolazione a ±100 (le costanti sotto) e' a occhio
 * (F8.15, 25 novembre 2026), sulle foto di prova della galleria accanto alle schermate di Foto in iOS 26 e 27.
 */
internal object AdjustCurves {
  /** ±100 di esposizione = ±1,5 stop nella luce lineare. A occhio. */
  const val ExposureStops = 1.5
  /** ±100 di calore = rosso ×2^±0,35 e blu ×2^∓0,35 nella luce lineare, a luminanza costante. A occhio. */
  const val WarmthStops = 0.35
  /** ±100 di tinta = verde ×2^∓0,25 (la tinta positiva va verso il magenta, come in Foto). A occhio. */
  const val TintStops = 0.25
  /** +100 di punto di nero: il 12% piu' scuro diventa nero. A occhio. */
  const val BlackPointCut = 0.12
  /** −100 di punto di nero: il nero sale al 10%. A occhio. */
  const val BlackPointLift = 0.10
  /** ±100 di luminosita' = gamma 2^∓0,75 sui toni medi (nero e bianco fermi). A occhio. */
  const val BrightnessGamma = 0.75
  /** La forza delle ombre e delle luci: la gobba cubica sulla meta' bassa e su quella alta. Oltre questi valori la curva
   *  smetterebbe di salire (la condizione e' nel KDoc di [shadows] e [highlights]). */
  const val ShadowsUp = 1.6
  const val ShadowsDown = 0.95
  const val HighlightsUp = 0.95
  const val HighlightsDown = 1.6
  /** −100 di contrasto: i toni si stringono al 60% attorno al grigio medio. */
  const val ContrastFlat = 0.6
  /** La brillantezza e' una miscela: ombre su, luci giu', un po' di luminosita' e di contrasto. A occhio. */
  const val BrillianceShadows = 0.55
  const val BrillianceHighlights = -0.5
  const val BrillianceBrightness = 0.15
  const val BrillianceContrast = 0.1
  /** +100 di nitidezza: 1,6 volte il dettaglio fine (il laplaciano a croce, raggio [sharpRadius]). */
  const val SharpAmount = 1.6
  /** +100 di definizione: 0,9 volte il dettaglio largo (luminanza meno la mappa sfocata). */
  const val DefinitionAmount = 0.9
  /** Il tetto del dettaglio aggiunto, per non fare aloni. */
  const val DetailLimit = 0.3f
  /** La vignettatura: +100 scurisce gli angoli al 45%, −100 li schiarisce del 45%; la caduta parte al 35% della semidiagonale. */
  const val VignetteDark = 0.55f
  const val VignetteLight = 0.45f
  const val VignetteStart = 0.35f
  /** La nitidezza lavora a un raggio proporzionale al lato corto (1/1200, almeno 1 pixel): l'anteprima e il file sono simili. */
  const val SharpRadiusDivisor = 1200f
  /** La mappa della definizione: la luminanza ridotta a 32 pixel sul lato corto, poi sfocata (≈ 2–3% del lato corto). */
  const val DefinitionMapShort = 32

  const val LumaR = 0.2126f
  const val LumaG = 0.7152f
  const val LumaB = 0.0722f

  fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
  fun toSrgb(l: Double): Double = if (l <= 0.0031308) l * 12.92 else 1.055 * l.pow(1.0 / 2.4) - 0.055

  /** I guadagni lineari di calore, tinta ed esposizione per R, G, B. */
  fun gains(exposure: Double, warmth: Double, tint: Double): DoubleArray {
    val r = 2.0.pow(WarmthStops * warmth)
    val b = 2.0.pow(-WarmthStops * warmth)
    val g = 2.0.pow(-TintStops * tint)
    val n = LumaR * r + LumaG * g + LumaB * b
    val e = 2.0.pow(ExposureStops * exposure)
    return doubleArrayOf(r / n * e, g / n * e, b / n * e)
  }

  fun blackPoint(p: Double, k: Double): Double = when {
    k > 0 -> max(0.0, (p - BlackPointCut * k) / (1 - BlackPointCut * k))
    k < 0 -> p + BlackPointLift * -k * (1 - p)
    else -> p
  }

  /**
   * Le ombre sulla meta' bassa: t' = t + s·k·t·(1−t)², con t = 2p. La pendenza a meta' resta 1 (nessuno spigolo col resto
   * della curva); la curva sale sempre finche' k ≤ 3 in su e k ≤ 1 in giu'.
   */
  fun shadows(p: Double, s: Double): Double {
    if (s == 0.0 || p >= 0.5) return p
    val t = p * 2
    val k = if (s > 0) ShadowsUp else ShadowsDown
    return (t + s * k * t * (1 - t) * (1 - t)) / 2
  }

  /** Le luci sulla meta' alta: t' = t + h·k·t²·(1−t), con t = 2p − 1. Sale sempre finche' k ≤ 1 in su e k ≤ 3 in giu'. */
  fun highlights(p: Double, h: Double): Double {
    if (h == 0.0 || p <= 0.5) return p
    val t = (p - 0.5) * 2
    val k = if (h > 0) HighlightsUp else HighlightsDown
    return 0.5 + (t + h * k * t * t * (1 - t)) / 2
  }

  fun brightness(p: Double, b: Double): Double = if (b == 0.0) p else max(0.0, p).pow(2.0.pow(-BrightnessGamma * b))

  fun contrast(p: Double, c: Double): Double = when {
    c > 0 -> p + (p * p * (3 - 2 * p) - p) * c
    c < 0 -> p + (0.5 + (p - 0.5) * ContrastFlat - p) * -c
    else -> p
  }

  /** Tutta la curva di un canale, dal valore in gamma (0…1) d'ingresso a quello d'uscita. */
  fun tone(input: Double, gain: Double, t: ToneParams): Double {
    var p = toSrgb(min(1.0, toLinear(input) * gain))
    p = blackPoint(p, t.blackPoint).coerceIn(0.0, 1.0)
    if (t.brilliance != 0.0) {
      p = shadows(p, BrillianceShadows * t.brilliance)
      p = highlights(p, BrillianceHighlights * t.brilliance)
      p = brightness(p, BrillianceBrightness * t.brilliance)
      p = contrast(p, BrillianceContrast * t.brilliance).coerceIn(0.0, 1.0)
    }
    p = shadows(p, t.shadows)
    p = highlights(p, t.highlights).coerceIn(0.0, 1.0)
    p = brightness(p, t.brightness)
    p = contrast(p, t.contrast)
    return p.coerceIn(0.0, 1.0)
  }

  fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
  }

  /** Il fattore della vignettatura in un punto del ritaglio ([x], [y] in frazioni). */
  fun vignette(x: Float, y: Float, amount: Float): Float {
    if (amount == 0f) return 1f
    val dx = (x - 0.5f) * 2f
    val dy = (y - 0.5f) * 2f
    val fall = smoothstep(VignetteStart, 1f, sqrt(dx * dx + dy * dy) / Sqrt2)
    return if (amount > 0) 1f - VignetteDark * amount * fall else 1f + VignetteLight * -amount * fall
  }

  private const val Sqrt2 = 1.4142135f

  fun sharpRadius(shortSide: Int): Int = max(1, (shortSide / SharpRadiusDivisor).roundToInt())
}

/** I parametri della curva tonale, in frazioni (−1…1). */
internal data class ToneParams(
  val blackPoint: Double,
  val brilliance: Double,
  val shadows: Double,
  val highlights: Double,
  val brightness: Double,
  val contrast: Double,
)

/**
 * Le regolazioni pronte per il calcolo: tre tabelle (una per canale, dall'ingresso a 8 bit all'uscita in gamma) per tutto
 * cio' che lavora su un canale alla volta, e i numeri di cio' che mescola i canali o guarda i vicini.
 */
internal class AdjustPlan(val adjustments: FluidAdjustments) {
  private fun f(a: FluidAdjustment) = adjustments[a] / 100f

  val exposure = f(FluidAdjustment.Exposure)
  val warmth = f(FluidAdjustment.Warmth)
  val tint = f(FluidAdjustment.Tint)
  val tone = ToneParams(
    blackPoint = f(FluidAdjustment.BlackPoint).toDouble(),
    brilliance = f(FluidAdjustment.Brilliance).toDouble(),
    shadows = f(FluidAdjustment.Shadows).toDouble(),
    highlights = f(FluidAdjustment.Highlights).toDouble(),
    brightness = f(FluidAdjustment.Brightness).toDouble(),
    contrast = f(FluidAdjustment.Contrast).toDouble(),
  )
  val saturation = f(FluidAdjustment.Saturation)
  val vibrance = f(FluidAdjustment.Vibrance)
  val sharp = AdjustCurves.SharpAmount.toFloat() * f(FluidAdjustment.Sharpness)
  val definition = AdjustCurves.DefinitionAmount.toFloat() * f(FluidAdjustment.Definition)
  val vignette = f(FluidAdjustment.Vignette)
  val gains = AdjustCurves.gains(exposure.toDouble(), warmth.toDouble(), tint.toDouble())

  val identity = adjustments.isNeutral
  val toneIdentity = exposure == 0f && warmth == 0f && tint == 0f && tone == ToneParams(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
  val needsNeighbours = sharp != 0f

  /** Le tre tabelle: [lut] di 768 valori, R poi G poi B, in gamma 0…1. */
  val lut: FloatArray = FloatArray(768).also { out ->
    for (ch in 0 until 3) for (i in 0 until 256) {
      out[ch * 256 + i] = if (toneIdentity) i / 255f else AdjustCurves.tone(i / 255.0, gains[ch], tone).toFloat()
    }
  }
}

/**
 * La mappa della definizione: la luminanza (in gamma) della **sorgente intera**, ridotta a [AdjustCurves.DefinitionMapShort]
 * pixel sul lato corto e sfocata col nucleo binomiale 3×3. Si legge con l'interpolazione bilineare ai centri dei pixel e
 * i bordi fermi, come un `BitmapShader` con `CLAMP` e il filtro: lo shader dell'anteprima la riceve come immagine e da' lo
 * stesso numero. I valori sono a 8 bit, come nell'immagine dello shader.
 */
internal class DefinitionMap(val width: Int, val height: Int, val values: ByteArray) {

  /** Il valore nel punto ([x], [y]) in pixel della mappa (il centro del pixel i e' i + 0,5). */
  fun sample(x: Float, y: Float): Float {
    val fx = (x - 0.5f).coerceIn(0f, (width - 1).toFloat())
    val fy = (y - 0.5f).coerceIn(0f, (height - 1).toFloat())
    val x0 = floor(fx).toInt()
    val y0 = floor(fy).toInt()
    val x1 = min(x0 + 1, width - 1)
    val y1 = min(y0 + 1, height - 1)
    val tx = fx - x0
    val ty = fy - y0
    val a = at(x0, y0) + (at(x1, y0) - at(x0, y0)) * tx
    val b = at(x0, y1) + (at(x1, y1) - at(x0, y1)) * tx
    return a + (b - a) * ty
  }

  private fun at(x: Int, y: Int) = (values[y * width + x].toInt() and 0xFF) / 255f

  companion object {
    /** Dalla sorgente ([pixels] ARGB, [w] × [h]), in qualunque risoluzione. */
    fun of(pixels: IntArray, w: Int, h: Int): DefinitionMap {
      val short = AdjustCurves.DefinitionMapShort
      val mw: Int
      val mh: Int
      if (w <= h) {
        mw = min(w, short)
        mh = max(1, (h.toFloat() * mw / w).roundToInt())
      } else {
        mh = min(h, short)
        mw = max(1, (w.toFloat() * mh / h).roundToInt())
      }
      // La media a riquadri: ogni pixel della mappa e' la media dei pixel della sorgente che copre.
      val sum = FloatArray(mw * mh)
      val count = IntArray(mw * mh)
      for (y in 0 until h) {
        val my = min(mh - 1, y * mh / h)
        for (x in 0 until w) {
          val mx = min(mw - 1, x * mw / w)
          val c = pixels[y * w + x]
          val l = AdjustCurves.LumaR * ((c shr 16) and 0xFF) + AdjustCurves.LumaG * ((c shr 8) and 0xFF) +
            AdjustCurves.LumaB * (c and 0xFF)
          sum[my * mw + mx] += l / 255f
          count[my * mw + mx]++
        }
      }
      val mean = FloatArray(mw * mh) { if (count[it] == 0) 0f else sum[it] / count[it] }
      // Il binomiale 3×3 (1 2 1), coi bordi fermi.
      val out = ByteArray(mw * mh)
      for (y in 0 until mh) for (x in 0 until mw) {
        var acc = 0f
        for (dy in -1..1) for (dx in -1..1) {
          val wgt = (if (dx == 0) 2 else 1) * (if (dy == 0) 2 else 1)
          acc += wgt * mean[(y + dy).coerceIn(0, mh - 1) * mw + (x + dx).coerceIn(0, mw - 1)]
        }
        out[y * mw + x] = ((acc / 16f) * 255f).roundToInt().coerceIn(0, 255).toByte()
      }
      return DefinitionMap(mw, mh, out)
    }
  }
}

/**
 * Dove sta ogni pixel dell'immagine che si calcola: [toOutput] lo porta in frazioni del ritaglio (per la vignettatura),
 * [toMap] in pixel della [map] (per la definizione). [sharpRadius] e' il raggio della nitidezza in pixel dell'immagine.
 * [rowOffset] dice da che riga dell'immagine intera parte il pezzo che si calcola (il salvataggio a pezzi).
 */
internal class AdjustContext(
  val toOutput: FluidAffine,
  val toMap: FluidAffine?,
  val map: DefinitionMap?,
  val sharpRadius: Int,
  val rowOffset: Int = 0,
)

/**
 * Il calcolo sulla CPU, puro (nessuna classe di Android): lo usano il salvataggio, l'anteprima finita e, sotto Android 13,
 * l'anteprima mentre il dito muove. Lavora su righe: chi chiama divide il lavoro fra i fili ([process] con `rows`).
 */
internal object AdjustKernel {

  /**
   * Calcola le righe [rows] di [dst] da [src] (entrambi ARGB, [w] × [h]); [src] ha [margin] righe in piu' sopra e sotto
   * il pezzo per i vicini della nitidezza (righe d'immagine reali, o la prima e l'ultima ripetute ai bordi).
   * Il colore resta opaco: le foto non hanno trasparenza, e l'alfa si copia com'e'.
   */
  fun process(
    src: IntArray, w: Int, h: Int, plan: AdjustPlan, ctx: AdjustContext, dst: IntArray, rows: IntRange = 0 until h,
  ) {
    if (plan.identity) {
      for (y in rows) System.arraycopy(src, y * w, dst, y * w, w)
      return
    }
    val lut = plan.lut
    val sat = 1f + plan.saturation
    val vib = plan.vibrance
    val sharp = plan.sharp
    val def = if (ctx.map != null && ctx.toMap != null) plan.definition else 0f
    val vig = plan.vignette
    val r = ctx.sharpRadius
    val map = ctx.map
    val toMap = ctx.toMap
    val toOut = ctx.toOutput
    for (y in rows) {
      val row = y * w
      val gy = (y + ctx.rowOffset).toFloat() + 0.5f
      for (x in 0 until w) {
        val c = src[row + x]
        val ri = (c shr 16) and 0xFF
        val gi = (c shr 8) and 0xFF
        val bi = c and 0xFF
        var rf = lut[ri]
        var gf = lut[256 + gi]
        var bf = lut[512 + bi]
        if (sat != 1f || vib != 0f) {
          val l = AdjustCurves.LumaR * rf + AdjustCurves.LumaG * gf + AdjustCurves.LumaB * bf
          val chroma = max(rf, max(gf, bf)) - min(rf, min(gf, bf))
          val k = sat * (1f + vib * (1f - chroma))
          rf = l + (rf - l) * k
          gf = l + (gf - l) * k
          bf = l + (bf - l) * k
        }
        if (sharp != 0f || def != 0f) {
          val lc = lumIn(c)
          var d = 0f
          if (sharp != 0f) {
            val n = lumIn(src[row + max(0, x - r)]) + lumIn(src[row + min(w - 1, x + r)]) +
              lumIn(src[max(0, y - r) * w + x]) + lumIn(src[min(h - 1, y + r) * w + x])
            d += sharp * (4f * lc - n) / 8f
          }
          if (def != 0f) {
            val px = x + 0.5f
            d += def * (lc - map!!.sample(toMap!!.mapX(px, gy), toMap.mapY(px, gy)))
          }
          d = d.coerceIn(-AdjustCurves.DetailLimit, AdjustCurves.DetailLimit)
          rf += d
          gf += d
          bf += d
        }
        if (vig != 0f) {
          val px = x + 0.5f
          val v = AdjustCurves.vignette(toOut.mapX(px, gy), toOut.mapY(px, gy), vig)
          rf *= v
          gf *= v
          bf *= v
        }
        dst[row + x] = (c and 0xFF000000.toInt()) or (to8(rf) shl 16) or (to8(gf) shl 8) or to8(bf)
      }
    }
  }

  private fun lumIn(c: Int): Float =
    (AdjustCurves.LumaR * ((c shr 16) and 0xFF) + AdjustCurves.LumaG * ((c shr 8) and 0xFF) +
      AdjustCurves.LumaB * (c and 0xFF)) / 255f

  private fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}

/**
 * «Auto», come la bacchetta di «Regola» in Foto: guarda l'istogramma della foto e propone esposizione, punto di nero,
 * luci, ombre, contrasto, vividezza e calore. Apple non dice come (e' un modello suo); qui sono regole semplici sui
 * percentili e sulla media dei colori, a occhio (F8.15), che non spingono mai oltre ±40: «Auto» corregge, non stravolge.
 */
object FluidAutoAdjust {

  /** La proposta per una foto, dai suoi pixel ARGB (una versione ridotta basta: bastano qualche migliaio di pixel). */
  fun suggest(pixels: IntArray): FluidAdjustments {
    if (pixels.isEmpty()) return FluidAdjustments.Neutral
    val hist = IntArray(256)
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var chroma = 0.0
    val step = max(1, pixels.size / 65_536)
    var n = 0
    var i = 0
    while (i < pixels.size) {
      val c = pixels[i]
      val r = (c shr 16) and 0xFF
      val g = (c shr 8) and 0xFF
      val b = c and 0xFF
      val l = (AdjustCurves.LumaR * r + AdjustCurves.LumaG * g + AdjustCurves.LumaB * b).roundToInt().coerceIn(0, 255)
      hist[l]++
      sumR += AdjustCurves.toLinear(r / 255.0)
      sumG += AdjustCurves.toLinear(g / 255.0)
      sumB += AdjustCurves.toLinear(b / 255.0)
      chroma += (max(r, max(g, b)) - min(r, min(g, b))) / 255.0
      n++
      i += step
    }
    fun percentile(p: Double): Double {
      val target = p * n
      var acc = 0
      for (v in 0 until 256) {
        acc += hist[v]
        if (acc >= target) return v / 255.0
      }
      return 1.0
    }
    val low = percentile(0.005)
    val median = percentile(0.5)
    val high = percentile(0.995)
    val clipped = (250 until 256).sumOf { hist[it] }.toDouble() / n
    val dark = (0 until 40).sumOf { hist[it] }.toDouble() / n

    var out = FluidAdjustments.Neutral
    // Esposizione: la mediana verso il grigio medio della foto (0,46 in gamma), al massimo un stop, a meta' forza.
    val ev = (ln2(AdjustCurves.toLinear(0.46) / max(1e-4, AdjustCurves.toLinear(median)))).coerceIn(-1.0, 1.0) * 0.5
    out = out.with(FluidAdjustment.Exposure, (ev / AdjustCurves.ExposureStops * 100).toFloat().coerceIn(-40f, 40f))
    // Punto di nero: un nero che non arriva a nero (foto velata).
    if (low > 0.03) {
      out = out.with(FluidAdjustment.BlackPoint, ((low - 0.015) / AdjustCurves.BlackPointCut * 100 * 0.8).toFloat().coerceIn(0f, 40f))
    }
    // Luci: un cielo bruciato si recupera.
    if (clipped > 0.02) out = out.with(FluidAdjustment.Highlights, (-clipped * 400).toFloat().coerceIn(-40f, 0f))
    // Ombre: molta foto nel buio si apre un poco.
    if (dark > 0.25) out = out.with(FluidAdjustment.Shadows, (dark * 60).toFloat().coerceIn(0f, 30f))
    // Contrasto: una foto piatta (i percentili vicini) prende un po' di curva.
    val range = high - low
    if (range < 0.8) out = out.with(FluidAdjustment.Contrast, ((0.8 - range) * 80).toFloat().coerceIn(0f, 35f))
    // Vividezza: i colori spenti si ravvivano.
    val meanChroma = chroma / n
    if (meanChroma < 0.2) out = out.with(FluidAdjustment.Vibrance, ((0.2 - meanChroma) * 120).toFloat().coerceIn(0f, 20f))
    // Calore: il «mondo grigio», a meta' forza. Un blu dominante scalda, un arancio raffredda.
    val rb = ln2((sumR + 1e-6) / (sumB + 1e-6))
    if (abs(rb) > 0.15) {
      out = out.with(FluidAdjustment.Warmth, (-rb / (2 * AdjustCurves.WarmthStops) * 100 * 0.5).toFloat().coerceIn(-30f, 30f))
    }
    return out
  }

  private fun ln2(x: Double) = kotlin.math.ln(x) / kotlin.math.ln(2.0)
}
