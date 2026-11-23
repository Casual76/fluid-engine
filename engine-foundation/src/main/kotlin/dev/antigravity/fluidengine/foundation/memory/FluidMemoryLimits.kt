package dev.antigravity.fluidengine.foundation.memory

import java.math.BigInteger

/** Il costo di una risorsa resta nella stessa categoria anche quando passa dalla cache allo schermo. */
enum class FluidMemoryCategory { GLASS, OTHER_CACHE, THUMBNAILS, VIEWER }

/** Tetti indipendenti in byte. Più categorie possono competere per un tetto di processo più basso. */
data class FluidMemoryLimits(
  val processBytes: Long,
  val glassBytes: Long,
  val otherCacheBytes: Long,
  val thumbnailBytes: Long,
  val viewerBytes: Long,
) {
  init {
    require(processBytes >= 0 && glassBytes >= 0 && otherCacheBytes >= 0 && thumbnailBytes >= 0 && viewerBytes >= 0)
  }

  /** Quota effettiva della categoria per questa configurazione. */
  operator fun get(category: FluidMemoryCategory): Long = when (category) {
    FluidMemoryCategory.GLASS -> glassBytes
    FluidMemoryCategory.OTHER_CACHE -> otherCacheBytes
    FluidMemoryCategory.THUMBNAILS -> thumbnailBytes
    FluidMemoryCategory.VIEWER -> viewerBytes
  }

  /**
   * Scala la riga già scelta dalla politica su min(somma, tetto di processo, heap di riferimento / 4).
   * Le differenze fra somme cumulative arrotondate assegnano anche l'ultimo byte. Il calcolo esatto
   * avviene solo quando cambia la politica, anche con quote la cui somma supera Long.MAX_VALUE.
   */
  fun forHeap(referenceHeapBytes: Long): FluidMemoryLimits {
    require(referenceHeapBytes >= 0)
    val sum = glassBytes.toBigInteger() + otherCacheBytes.toBigInteger() +
      thumbnailBytes.toBigInteger() + viewerBytes.toBigInteger()
    val total = minOf(sum, minOf(processBytes, referenceHeapBytes / 4).toBigInteger())
    if (total == sum) return copy(processBytes = total.toLong())

    var prefix = BigInteger.ZERO
    var assigned = 0L
    fun portion(bytes: Long): Long {
      prefix += bytes.toBigInteger()
      val next = (prefix * total / sum).toLong()
      val portion = next - assigned
      assigned = next
      return portion
    }
    return FluidMemoryLimits(total.toLong(), portion(glassBytes), portion(otherCacheBytes), portion(thumbnailBytes), portion(viewerBytes))
  }

  companion object {
    private const val MiB = 1024L * 1024
    /** Massimi iniziali F7, profilo Pieno. Applicare [forHeap] prima del primo lavoro. */
    val Full = FluidMemoryLimits(128 * MiB, 48 * MiB, 8 * MiB, 24 * MiB, 48 * MiB)
    /** Massimi iniziali F7, profilo Ridotto. */
    val Reduced = FluidMemoryLimits(64 * MiB, 24 * MiB, 4 * MiB, 12 * MiB, 24 * MiB)
    /** Massimi iniziali F7, profilo Protetto. */
    val Protected = FluidMemoryLimits(32 * MiB, 12 * MiB, 2 * MiB, 4 * MiB, 14 * MiB)
  }
}
