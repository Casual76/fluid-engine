package dev.antigravity.fluidengine.viewer.ar

import kotlin.math.cos
import kotlin.math.sin

/** One AR world unit is a metre; native units of OBJ and STL are treated as metres. */
internal class ArPlacement(
  private val center: FloatArray,
  private val halfExtent: FloatArray,
) {
  var scale = 1f
    private set
  private var yaw = 0f
  private var offsetX = 0f
  private var offsetZ = 0f

  fun reset() {
    scale = 1f
    yaw = 0f
    offsetX = 0f
    offsetZ = 0f
  }

  fun move(dx: Float, dz: Float) {
    offsetX += dx
    offsetZ += dz
  }

  fun transform(ratio: Float, angle: Float) {
    if (ratio.isFinite() && ratio > 0f) scale = (scale * ratio).coerceIn(0.1f, 10f)
    if (angle.isFinite()) yaw = (yaw + angle) % (2f * Math.PI.toFloat())
  }

  internal fun transformModel(anchor: FloatArray, out: FloatArray) {
    val cosine = cos(yaw) * scale
    val sine = sin(yaw) * scale
    out.fill(0f)
    out[0] = cosine
    out[2] = -sine
    out[5] = scale
    out[8] = sine
    out[10] = cosine
    out[12] = anchor[0] + offsetX - cosine * center[0] - sine * center[2]
    out[13] = anchor[1] - (center[1] - halfExtent[1]) * scale
    out[14] = anchor[2] + offsetZ + sine * center[0] - cosine * center[2]
    out[15] = 1f
  }

  internal fun transformShadow(anchor: FloatArray, out: FloatArray) {
    // Overscan the model footprint so the shadow from the oblique light has room.
    val extent = (halfExtent.maxOrNull() ?: 0f) * scale * 4f + 0.05f
    out.fill(0f)
    out[0] = extent
    out[5] = extent
    out[10] = extent
    out[12] = anchor[0] + offsetX
    out[13] = anchor[1] - 0.001f
    out[14] = anchor[2] + offsetZ
    out[15] = 1f
  }
}

internal fun horizontalIntersection(origin: FloatArray, ray: FloatArray, height: Float): FloatArray? {
  if (ray[1] >= -0.001f) return null
  val distance = (height - origin[1]) / ray[1]
  if (distance !in 0f..20f) return null
  return floatArrayOf(origin[0] + ray[0] * distance, origin[2] + ray[2] * distance)
}
