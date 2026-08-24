package dev.antigravity.fluidengine.ui.fluid

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Small API-neutral handle around the Android 13 runtime shader.
 *
 * Keeping [RuntimeShader] behind this interface prevents the ordinary API 26 path from touching an
 * Android 13 class while the verifier is loading [GlassMaterial]. One instance belongs to one glass
 * surface: uniforms are mutable, so sharing it would make two panes overwrite one another while a
 * frame is being assembled.
 */
internal interface GlassRuntimeRefraction {
  val renderEffect: RenderEffect

  fun update(
    inputWidth: Float,
    inputHeight: Float,
    surfaceLeft: Float,
    surfaceTop: Float,
    surfaceWidth: Float,
    surfaceHeight: Float,
    cornerRadius: Float,
    displacement: Float,
    rimWidth: Float,
    magnification: Float,
  )
}

internal fun createGlassRuntimeRefractionOrNull(): GlassRuntimeRefraction? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
  return try {
    Api33GlassRuntimeRefraction()
  } catch (_: IllegalArgumentException) {
    // A vendor shader compiler rejecting valid AGSL must not take the whole interface down. The
    // caller transparently keeps the API 31 magnified-rim implementation instead.
    null
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33GlassRuntimeRefraction : GlassRuntimeRefraction {
  private val shader = RuntimeShader(GlassRefractionShader)

  override val renderEffect: RenderEffect =
    AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()

  override fun update(
    inputWidth: Float,
    inputHeight: Float,
    surfaceLeft: Float,
    surfaceTop: Float,
    surfaceWidth: Float,
    surfaceHeight: Float,
    cornerRadius: Float,
    displacement: Float,
    rimWidth: Float,
    magnification: Float,
  ) {
    shader.setFloatUniform("inputSize", inputWidth, inputHeight)
    shader.setFloatUniform("surfaceOrigin", surfaceLeft, surfaceTop)
    shader.setFloatUniform("surfaceSize", surfaceWidth, surfaceHeight)
    shader.setFloatUniform("cornerRadius", cornerRadius)
    shader.setFloatUniform("displacement", displacement)
    shader.setFloatUniform("rimWidth", rimWidth)
    shader.setFloatUniform("magnification", magnification)
  }
}

/**
 * Refracts only the optical band that [glassSurface] later masks into the perimeter.
 *
 * The signed-distance field supplies a real surface normal for rectangles, rounded rectangles and
 * capsules. Sampling inward along that normal bends the captured backdrop into the rim; the tiny
 * centre-directed scale adds the lens compression visible in thick glass without moving controls
 * drawn above the material.
 */
private const val GlassRefractionShader = """
  uniform shader content;
  uniform float2 inputSize;
  uniform float2 surfaceOrigin;
  uniform float2 surfaceSize;
  uniform float cornerRadius;
  uniform float displacement;
  uniform float rimWidth;
  uniform float magnification;

  float roundedBoxDistance(float2 point, float2 halfSize, float radius) {
    float2 q = abs(point) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
  }

  float2 roundedBoxNormal(float2 point, float2 halfSize, float radius) {
    const float epsilon = 1.0;
    float dx = roundedBoxDistance(point + float2(epsilon, 0.0), halfSize, radius) -
      roundedBoxDistance(point - float2(epsilon, 0.0), halfSize, radius);
    float dy = roundedBoxDistance(point + float2(0.0, epsilon), halfSize, radius) -
      roundedBoxDistance(point - float2(0.0, epsilon), halfSize, radius);
    float2 gradient = float2(dx, dy);
    return gradient / max(length(gradient), 0.001);
  }

  half4 main(float2 fragCoord) {
    float2 safeSurfaceSize = max(surfaceSize, float2(1.0));
    float2 centre = surfaceOrigin + safeSurfaceSize * 0.5;
    float2 halfSize = safeSurfaceSize * 0.5;
    float radius = clamp(cornerRadius, 0.0, min(halfSize.x, halfSize.y));
    float2 local = fragCoord - centre;
    float distanceToEdge = roundedBoxDistance(local, halfSize, radius);
    float band = 1.0 - smoothstep(0.0, max(rimWidth * 2.5, 2.0), abs(distanceToEdge));
    float2 normal = roundedBoxNormal(local, halfSize, radius);

    float2 lensCoord = centre + (fragCoord - centre) * (1.0 - magnification * band);
    float2 sampleCoord = lensCoord - normal * displacement * band;
    sampleCoord = clamp(sampleCoord, float2(0.5), max(inputSize - 0.5, float2(0.5)));
    return content.eval(sampleCoord);
  }
"""
