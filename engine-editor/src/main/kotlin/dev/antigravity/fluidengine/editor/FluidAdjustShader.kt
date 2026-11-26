package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * L'anteprima mentre il dito muove, da Android 13: le regolazioni come shader AGSL sulla foto, ricalcolate dalla GPU a
 * ogni fotogramma senza toccare la CPU e senza allocare (cambiano solo gli uniform). Il testo si scrive **dalle costanti
 * di [AdjustCurves]**: la curva della GPU e quella delle tabelle della CPU sono la stessa formula, e differiscono solo per
 * gli arrotondamenti (al piu' un livello su 255, `FluidAdjustShaderTest`).
 */
internal object AdjustShaderSource {
  private fun f(v: Double) = String.format(Locale.ROOT, "%.6f", v)
  private fun f(v: Float) = f(v.toDouble())

  val text: String by lazy {
    val c = AdjustCurves
    """
    uniform shader image;
    uniform shader defMap;
    uniform float2 mapScale;
    uniform float3 gains;
    uniform float4 toneA;
    uniform float4 toneB;
    uniform float4 detail;
    uniform float3 outX;
    uniform float3 outY;

    float toLinear(float v) { return v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4); }
    float toSrgb(float l) { return l <= 0.0031308 ? l * 12.92 : 1.055 * pow(l, 1.0 / 2.4) - 0.055; }
    float blackPoint(float p, float k) {
      if (k > 0.0) return max(0.0, (p - ${f(c.BlackPointCut)} * k) / (1.0 - ${f(c.BlackPointCut)} * k));
      if (k < 0.0) return p + ${f(c.BlackPointLift)} * -k * (1.0 - p);
      return p;
    }
    float shadows(float p, float s) {
      if (s == 0.0 || p >= 0.5) return p;
      float t = p * 2.0;
      float k = s > 0.0 ? ${f(c.ShadowsUp)} : ${f(c.ShadowsDown)};
      return (t + s * k * t * (1.0 - t) * (1.0 - t)) * 0.5;
    }
    float highlights(float p, float h) {
      if (h == 0.0 || p <= 0.5) return p;
      float t = (p - 0.5) * 2.0;
      float k = h > 0.0 ? ${f(c.HighlightsUp)} : ${f(c.HighlightsDown)};
      return 0.5 + (t + h * k * t * t * (1.0 - t)) * 0.5;
    }
    float brightness(float p, float b) { return b == 0.0 ? p : pow(max(p, 0.0), exp2(-${f(c.BrightnessGamma)} * b)); }
    float contrast(float p, float k) {
      if (k > 0.0) return p + (p * p * (3.0 - 2.0 * p) - p) * k;
      if (k < 0.0) return p + (0.5 + (p - 0.5) * ${f(c.ContrastFlat)} - p) * -k;
      return p;
    }
    float tone(float x, float gain) {
      float p = toSrgb(min(1.0, toLinear(x) * gain));
      p = clamp(blackPoint(p, toneA.x), 0.0, 1.0);
      if (toneA.y != 0.0) {
        p = shadows(p, ${f(c.BrillianceShadows)} * toneA.y);
        p = highlights(p, ${f(c.BrillianceHighlights)} * toneA.y);
        p = brightness(p, ${f(c.BrillianceBrightness)} * toneA.y);
        p = clamp(contrast(p, ${f(c.BrillianceContrast)} * toneA.y), 0.0, 1.0);
      }
      p = shadows(p, toneA.z);
      p = clamp(highlights(p, toneA.w), 0.0, 1.0);
      p = brightness(p, toneB.x);
      p = contrast(p, toneB.y);
      return clamp(p, 0.0, 1.0);
    }
    float lumOf(half4 c) {
      float a = c.a;
      float3 rgb = a > 0.0 ? float3(c.rgb) / a : float3(0.0);
      return dot(rgb, float3(${f(c.LumaR)}, ${f(c.LumaG)}, ${f(c.LumaB)}));
    }
    half4 main(float2 p) {
      half4 src = image.eval(p);
      float a = src.a;
      float3 c = a > 0.0 ? float3(src.rgb) / a : float3(0.0);
      float3 o = float3(tone(c.r, gains.r), tone(c.g, gains.g), tone(c.b, gains.b));
      float sat = toneB.z;
      float vib = toneB.w;
      if (sat != 1.0 || vib != 0.0) {
        float l = dot(o, float3(${f(c.LumaR)}, ${f(c.LumaG)}, ${f(c.LumaB)}));
        float chroma = max(o.r, max(o.g, o.b)) - min(o.r, min(o.g, o.b));
        o = l + (o - l) * (sat * (1.0 + vib * (1.0 - chroma)));
      }
      if (detail.x != 0.0 || detail.z != 0.0) {
        float lc = dot(c, float3(${f(c.LumaR)}, ${f(c.LumaG)}, ${f(c.LumaB)}));
        float d = 0.0;
        if (detail.x != 0.0) {
          float r = detail.y;
          float n = lumOf(image.eval(p + float2(-r, 0.0))) + lumOf(image.eval(p + float2(r, 0.0))) +
            lumOf(image.eval(p + float2(0.0, -r))) + lumOf(image.eval(p + float2(0.0, r)));
          d += detail.x * (4.0 * lc - n) / 8.0;
        }
        if (detail.z != 0.0) d += detail.z * (lc - float(defMap.eval(p * mapScale).r));
        d = clamp(d, -${f(c.DetailLimit)}, ${f(c.DetailLimit)});
        o += d;
      }
      if (detail.w != 0.0) {
        float2 q = float2(dot(outX, float3(p, 1.0)), dot(outY, float3(p, 1.0)));
        float2 dd = (q - 0.5) * 2.0;
        float fall = smoothstep(${f(c.VignetteStart)}, 1.0, length(dd) / 1.4142135);
        float v = detail.w > 0.0 ? 1.0 - ${f(c.VignetteDark)} * detail.w * fall : 1.0 + ${f(c.VignetteLight)} * -detail.w * fall;
        o *= v;
      }
      o = clamp(o, 0.0, 1.0);
      return half4(half3(o * a), half(a));
    }
    """.trimIndent()
  }
}

/**
 * Lo shader dell'anteprima su una foto ([image], la sorgente ridotta) con la sua mappa della definizione ([map]). Si
 * disegna con [draw] sotto la trasformazione del chiamante (le coordinate locali sono i pixel di [image]); [update]
 * cambia solo gli uniform.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AdjustShader(image: Bitmap, private val map: Bitmap) {
  private val width = image.width.toFloat()
  private val height = image.height.toFloat()
  private val shader = RuntimeShader(AdjustShaderSource.text)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  init {
    shader.setInputShader("image", BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
      it.filterMode = BitmapShader.FILTER_MODE_LINEAR
    })
    shader.setInputShader("defMap", BitmapShader(map, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
      it.filterMode = BitmapShader.FILTER_MODE_LINEAR
    })
    shader.setFloatUniform("mapScale", map.width / width, map.height / height)
    paint.shader = shader
  }

  private var last: FluidAdjustments? = null
  private var lastOut: FluidAffine? = null

  /**
   * Gli uniform per [adjustments]; [toOutput] porta i pixel di [image] in frazioni del ritaglio (la vignettatura) e
   * [sharpRadius] e' il raggio della nitidezza in pixel di [image]. Niente allocazioni se non cambia niente.
   */
  private val plan = FloatArray(PlanNumbers.Size)

  fun update(adjustments: FluidAdjustments, toOutput: FluidAffine, sharpRadius: Float) {
    if (adjustments != last) {
      PlanNumbers.fill(adjustments, plan)
      shader.setFloatUniform("gains", plan[0], plan[1], plan[2])
      shader.setFloatUniform("toneA", plan[3], plan[4], plan[5], plan[6])
      shader.setFloatUniform("toneB", plan[7], plan[8], plan[9], plan[10])
      shader.setFloatUniform("detail", plan[11], sharpRadius, plan[12], plan[13])
      last = adjustments
    }
    if (toOutput != lastOut) {
      shader.setFloatUniform("outX", toOutput.a, toOutput.c, toOutput.tx)
      shader.setFloatUniform("outY", toOutput.b, toOutput.d, toOutput.ty)
      lastOut = toOutput
    }
  }

  /** Disegna la foto intera ([image]) nelle coordinate locali di [canvas]. */
  fun draw(canvas: Canvas) {
    canvas.drawRect(0f, 0f, width, height, paint)
  }
}

/**
 * I numeri degli uniform, nello stesso ordine e con gli stessi conti di [AdjustPlan] (senza le tabelle). [fill] scrive in
 * un array dello shader, senza allocare: si chiama a ogni fotogramma mentre il righello gira.
 */
internal object PlanNumbers {
  const val Size = 14

  fun of(a: FluidAdjustments): FloatArray = FloatArray(Size).also { fill(a, it) }

  fun fill(a: FluidAdjustments, out: FloatArray) {
    fun f(x: FluidAdjustment) = a[x] / 100f
    // I guadagni di AdjustCurves.gains, scritti qui senza l'array di appoggio.
    val warmth = f(FluidAdjustment.Warmth).toDouble()
    val r = Math.pow(2.0, AdjustCurves.WarmthStops * warmth)
    val b = Math.pow(2.0, -AdjustCurves.WarmthStops * warmth)
    val g = Math.pow(2.0, -AdjustCurves.TintStops * f(FluidAdjustment.Tint).toDouble())
    val n = AdjustCurves.LumaR * r + AdjustCurves.LumaG * g + AdjustCurves.LumaB * b
    val e = Math.pow(2.0, AdjustCurves.ExposureStops * f(FluidAdjustment.Exposure).toDouble())
    out[0] = (r / n * e).toFloat()
    out[1] = (g / n * e).toFloat()
    out[2] = (b / n * e).toFloat()
    out[3] = f(FluidAdjustment.BlackPoint)
    out[4] = f(FluidAdjustment.Brilliance)
    out[5] = f(FluidAdjustment.Shadows)
    out[6] = f(FluidAdjustment.Highlights)
    out[7] = f(FluidAdjustment.Brightness)
    out[8] = f(FluidAdjustment.Contrast)
    out[9] = 1f + f(FluidAdjustment.Saturation)
    out[10] = f(FluidAdjustment.Vibrance)
    out[11] = AdjustCurves.SharpAmount.toFloat() * f(FluidAdjustment.Sharpness)
    out[12] = AdjustCurves.DefinitionAmount.toFloat() * f(FluidAdjustment.Definition)
    out[13] = f(FluidAdjustment.Vignette)
  }
}
