package dev.antigravity.fluidengine.ui.glass.quality

/** Tetto al lato del solo grande campione di sfondo. Il nodo sceglie il minimo con il suo tetto locale. */
internal enum class GlassOptics(val backgroundScaleCeiling: Float) {
  Protected(0.5f), Reduced(0.75f), Full(1f);

  fun lower() = entries[(ordinal - 1).coerceAtLeast(0)]
  fun higher() = entries[(ordinal + 1).coerceAtMost(Full.ordinal)]
}

/** Seleziona una riga dei bilanci di processo F7.9. L'aritmetica delle quote appartiene al registro. */
internal enum class GlassMemoryProfile { Protected, Reduced, Full }

internal enum class GlassThermal { Unknown, None, Light, Moderate, SevereOrWorse }
internal enum class GlassFrameEvidence { MeasuredApp, Estimated, Unattributed, Incomplete }

internal data class GlassDeviceCapacity(
  val totalRamBytes: Long = 0,
  val maxHeapBytes: Long = 0,
  val heapClassMiB: Int = 0,
  val lowRamDevice: Boolean? = null,
  val mediaPerformanceClass: Int = 0,
  val recognizedGpuDriver: Boolean = false,
) {
  private val classBytes get() = heapClassMiB.toLong() * MiB
  val heapReferenceBytes get() = when {
    maxHeapBytes > 0 && classBytes > 0 -> minOf(maxHeapBytes, classBytes)
    maxHeapBytes > 0 -> maxHeapBytes
    classBytes > 0 -> classBytes
    else -> 0L
  }
  val complete get() = totalRamBytes > 0 && maxHeapBytes > 0 && heapClassMiB > 0 && lowRamDevice != null

  /** Soglie iniziali della proposta F7, GiB/MiB binari, estremi inclusi. */
  val memoryCeiling get() = when {
    lowRamDevice == true || totalRamBytes in 1..4 * GiB || heapReferenceBytes in 1..192 * MiB ->
      GlassMemoryProfile.Protected
    !complete || totalRamBytes <= 6 * GiB || heapReferenceBytes <= 256 * MiB -> GlassMemoryProfile.Reduced
    else -> GlassMemoryProfile.Full
  }

  companion object {
    private const val MiB = 1_048_576L
    private const val GiB = 1024 * MiB
  }
}

/** Una lettura fuori dal draw. Quantità assenti: zero per la soglia, -1 per la disponibilità, NaN per l'heap. */
internal data class GlassConditions(
  val availableBytes: Long = -1,
  val pressureThresholdBytes: Long = 0,
  val lowMemory: Boolean? = null,
  val heapFraction: Double = Double.NaN,
  val thermal: GlassThermal = GlassThermal.Unknown,
  val powerSave: Boolean? = null,
)

/** Snapshot immutabile, riusato finché non cambia uno dei quattro risultati. */
internal data class GlassQualityDecision(
  val optics: GlassOptics,
  val memoryProfile: GlassMemoryProfile,
  val heapReferenceBytes: Long,
  val allowPrefetch: Boolean,
)

internal enum class GlassQualityReason {
  Capacity, TechnicalCeiling, AppCeiling, SevereThermal, MemoryPressure, HeapPressure, VisibleBudgetDenied,
  PowerSave, ModerateThermal, MissingSignals, ConsecutiveMisses, TwoBadWindows, SettledRecovery,
}
