package dev.antigravity.fluidengine.foundation.memory

/**
 * Byte per una o più superfici vive contemporaneamente. Passare le dimensioni fisiche, comprensive del
 * padding del filtro, e il formato effettivo (per esempio 4 byte/pixel per RGBA8 o 8 per RGBA FP16).
 * [rowStrideBytes] comprende l'eventuale allineamento di riga. La dimensione del file su disco è indipendente.
 * Valori non positivi o stride corto generano IllegalArgumentException, un prodotto fuori Long ArithmeticException.
 */
fun fluidSurfaceBytes(
  width: Long,
  height: Long,
  bytesPerPixel: Int,
  copies: Int = 1,
  rowStrideBytes: Long? = null,
): Long {
  require(width > 0 && height > 0 && bytesPerPixel > 0 && copies > 0)
  val packed = Math.multiplyExact(width, bytesPerPixel.toLong())
  val stride = rowStrideBytes ?: packed
  require(stride >= packed)
  return Math.multiplyExact(Math.multiplyExact(stride, height), copies.toLong())
}
