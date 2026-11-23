package dev.antigravity.fluidengine.aiui.chat

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.halo.wrap01

// La luce continua attorno al campo dell'assistente (F6B.16). Parole di Alessio del 23 novembre 2026, sul composer: «mostra
// l'alone intorno fatto come delle piccole sfere di aloni […] che non mi sono mai piaciuti in quanto un alone non è composto
// da sfere, ma è qualcosa più dinamico. Nel nostro caso non servirebbe nemmeno qualcosa di troppo spinto, ma se guardi tipo
// gemini … non si vedono sfere nell'alone superiore». Al posto delle sei macchie, dell'anello e della scia di F6B.7: **una
// luce sola** che abbraccia il bordo del vetro e sfuma verso l'esterno, coi colori del tema che scorrono piano lungo il
// perimetro, come il bagliore di Apple Intelligence in iOS 26 e l'alone di Gemini. Nessun punto e nessuna macchia tonda:
// il colore cambia solo lungo il bordo, la luce solo allontanandosi da lui.

/** I numeri della luce del campo (F6B.16). */
internal object FluidAssistantFieldGlowProfile {
  // **L'intensità di serie: la 2, la vivida, scelta di Alessio del 23 novembre 2026, sera**, fra le due fotografate sul
  // Tab S9 in formato telefono accanto all'alone di Gemini (`artifacts/liquid-glass/sessioni/f6b-16/per-alessio/
  // tavola-alone.png`). L'altra, la 1 discreta (8 dp, filo 1,6 dp, alfa 0,30 e 0,55), non è più nel codice. A occhio
  // dentro la tavola, 23 novembre 2026.

  /** La distanza in cui la luce cala a un terzo (1/e) allontanandosi dal bordo. */
  val Spread: Dp = 13.dp

  /** Lo spessore del filo di luce che abbraccia il bordo, appena fuori dal vetro. */
  val Core: Dp = 2.2.dp

  /** L'alfa della luce sfumata, sul bordo, a voce piena. */
  const val GlowAlpha: Float = 0.45f

  /** L'alfa del filo, sul bordo, a voce piena. */
  const val CoreAlpha: Float = 0.75f

  /** Quanto si estende il disegno oltre il bordo, in multipli di [Spread]: lì la luce è sotto 1%. */
  const val ReachSpreads: Float = 4.6f

  /** A voce zero la luce resta al 55% della piena; la voce aggiunge il resto. */
  const val FloorShare: Float = 0.55f

  /** La voce allarga la luce fino al 30% in più. */
  const val SpreadGain: Float = 0.30f

  /**
   * Il passo dei colori lungo il bordo: un giro ogni 14 s di tempo dell'orologio (a campo aperto; più svelto al lavoro).
   * Lento, perché la luce si legga come un fluire e non come qualcosa che gira.
   */
  const val ColourLapsPerSec: Float = 1f / 14f

  /** L'onda di luce lungo il bordo: due creste larghe (mai una macchia), che fanno un giro ogni 9 s. */
  const val WaveLapsPerSec: Float = 1f / 9f

  /** Quanto l'onda cambia la luce: dal 70% al 100%. */
  const val WaveDepth: Float = 0.30f
}

/** Lo shader della luce: un disegno solo, un rettangolo attorno al vetro. La sagoma è la distanza dal rettangolo tondo. */
private const val GlowShaderSource = """
uniform float4 box;
uniform float radius;
uniform float spread;
uniform float core;
uniform float colourPhase;
uniform float wavePhase;
uniform float waveDepth;
uniform float glowAlpha;
uniform float coreAlpha;
layout(color) uniform half4 c0;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;

float roundBox(float2 p, float2 c, float2 h, float r) {
  float2 q = abs(p - c) - (h - r);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half3 cycle(float t) {
  float x = fract(t) * 3.0;
  float k = fract(x);
  k = k * k * (3.0 - 2.0 * k);
  if (x < 1.0) return mix(c0.rgb, c1.rgb, half(k));
  if (x < 2.0) return mix(c1.rgb, c2.rgb, half(k));
  return mix(c2.rgb, c0.rgb, half(k));
}

half4 main(float2 p) {
  float2 c = (box.xy + box.zw) * 0.5;
  float2 h = (box.zw - box.xy) * 0.5;
  float d = roundBox(p, c, h, radius);
  if (d < -1.0) return half4(0.0);
  float outside = max(d, 0.0);
  float2 v = (p - c) / max(h, float2(1.0));
  float u = atan(v.y, v.x) * 0.15915494 + 0.5;
  half3 colour = cycle(u + colourPhase);
  float wave = 1.0 - waveDepth * 0.5 * (1.0 - sin(6.2831853 * (2.0 * u - wavePhase)));
  float glow = exp(-outside / spread);
  float rim = exp(-(outside * outside) / (core * core));
  float a = clamp((glowAlpha * glow + coreAlpha * rim) * wave, 0.0, 1.0) * smoothstep(-1.0, 0.0, d);
  return half4(colour * half(a), half(a));
}
"""

/**
 * Il pittore della luce del campo: lo shader dove c'è (Android 13+), altrimenti un filo col gradiente a ventaglio sul bordo
 * (lo stesso disegno solo, senza la luce sfumata). Tiene lo shader e il tracciato: niente allocazioni per fotogramma.
 */
internal class FluidAssistantFieldGlowPainter {
  private val shader: RuntimeShader? = if (Build.VERSION.SDK_INT >= 33) {
    try { RuntimeShader(GlowShaderSource) } catch (_: Throwable) { null }
  } else null
  private val brush: ShaderBrush? = shader?.let { ShaderBrush(it) }
  private val path = Path()
  private var pathFor: RoundRect? = null
  private val stops = Array(13) { Color.Transparent }

  /** Vero se disegna con lo shader (la luce sfumata); falso col ripiego (il solo filo). */
  val shaded: Boolean get() = shader != null

  /**
   * Disegna la luce attorno a [glass]: [colours] i tre colori che scorrono, [time] il tempo dell'orologio, [amplitude] la
   * voce (0..1), [alpha] presenza e respiro.
   */
  fun DrawScope.draw(
    glass: RoundRect,
    colours: List<Color>,
    time: Float,
    amplitude: Float,
    alpha: Float,
    blend: BlendMode,
  ) {
    if (alpha <= 0f || colours.isEmpty()) return
    val voice = amplitude.coerceIn(0f, 1f)
    val strength = alpha.coerceIn(0f, 1f) * (FluidAssistantFieldGlowProfile.FloorShare + (1f - FluidAssistantFieldGlowProfile.FloorShare) * voice)
    val spread = FluidAssistantFieldGlowProfile.Spread.toPx() * (1f + FluidAssistantFieldGlowProfile.SpreadGain * voice)
    val colourPhase = wrap01(time * FluidAssistantFieldGlowProfile.ColourLapsPerSec)
    val wavePhase = wrap01(time * FluidAssistantFieldGlowProfile.WaveLapsPerSec)
    val s = shader
    if (s != null && brush != null) {
      val radius = glass.topLeftCornerRadius.x
      s.setFloatUniform("box", glass.left, glass.top, glass.right, glass.bottom)
      s.setFloatUniform("radius", radius)
      s.setFloatUniform("spread", spread)
      s.setFloatUniform("core", FluidAssistantFieldGlowProfile.Core.toPx())
      s.setFloatUniform("colourPhase", colourPhase)
      s.setFloatUniform("wavePhase", wavePhase)
      s.setFloatUniform("waveDepth", FluidAssistantFieldGlowProfile.WaveDepth)
      s.setFloatUniform("glowAlpha", FluidAssistantFieldGlowProfile.GlowAlpha * strength)
      s.setFloatUniform("coreAlpha", FluidAssistantFieldGlowProfile.CoreAlpha * strength)
      s.setColorUniform("c0", colours[0].toArgb())
      s.setColorUniform("c1", colours[1 % colours.size].toArgb())
      s.setColorUniform("c2", colours[2 % colours.size].toArgb())
      val reach = spread * FluidAssistantFieldGlowProfile.ReachSpreads
      drawRect(
        brush = brush,
        topLeft = Offset(glass.left - reach, glass.top - reach),
        size = Size(glass.width + reach * 2f, glass.height + reach * 2f),
        blendMode = blend,
      )
      return
    }
    // Il ripiego: il filo sul bordo (metà la copre il vetro), coi colori che scorrono a ventaglio.
    if (pathFor != glass) {
      pathFor = glass
      path.reset()
      path.addRoundRect(glass)
    }
    val n = stops.size - 1
    for (k in 0..n) {
      val t = wrap01(k / n.toFloat() - colourPhase) * 3f
      val i = t.toInt() % 3
      stops[k] = androidx.compose.ui.graphics.lerp(colours[i % colours.size], colours[(i + 1) % colours.size], t - t.toInt())
    }
    val center = Offset(glass.left + glass.width / 2f, glass.top + glass.height / 2f)
    drawPath(
      path = path,
      brush = Brush.sweepGradient(stops.toList(), center = center),
      alpha = (FluidAssistantFieldGlowProfile.CoreAlpha * strength).coerceIn(0f, 1f),
      style = Stroke(width = FluidAssistantFieldGlowProfile.Core.toPx() * 2f),
      blendMode = blend,
    )
  }
}
