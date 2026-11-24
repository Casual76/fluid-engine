package dev.antigravity.fluidengine.ui.glass.quality

/** Politica F7.10. Un solo proprietario serializza letture, frame ed eventi di finestra F7.11.
 * Il costruttore produce la prima [decision] anche prima che esista una finestra. I metodi
 * restituiscono il motivo solo quando cambia lo snapshot. Nessun timer, lavoro Android o lettura nel draw.
 *
 * [shaderCeiling] è il massimo della ricetta realmente utilizzabile (null = sonda ancora ignota).
 * [appCeiling] incorpora le restrizioni già risolte dal chiamante. Un livello consente una spesa,
 * non abilita una ricetta che il tier effettivo ha escluso.
 */
internal class GlassQualityPolicy(
  capacity: GlassDeviceCapacity = GlassDeviceCapacity(),
  conditions: GlassConditions = GlassConditions(),
  shaderCeiling: GlassOptics? = null,
  appCeiling: GlassOptics = GlassOptics.Full,
) {
  private var device = capacity
  private var signals = conditions
  private var technicalLimit = shaderCeiling
  private var appLimit = appCeiling
  private var lowRamSeen = capacity.lowRamDevice == true
  private var budgetDenied = false
  private val frames = GlassFrameHistory()
  private var visible = false
  private var moving = false
  private var display = -1
  private var period = 0L
  private var motionStart = 0L
  private var clock = -1L
  private var lastChange = -1L
  private var promotedAt = -1L
  private var rollbackAt = -1L
  private var retryDelayNs = 0L

  internal fun budgetRollbackAt(now: Long) = now >= 0 && rollbackAt == now
  private fun recentlyPromoted(now: Long) = promotedAt >= 0 && now - promotedAt in 0..5_000_000_000L
  internal val nextOptics get() = minOf(decision.optics.higher(), technicalLimit ?: GlassOptics.Full, appLimit)
  internal val nextMemoryProfile get() = memoryFor(nextOptics)

  private val validMemoryReading get() = signals.pressureThresholdBytes > 0 && signals.availableBytes >= 0 &&
    (device.totalRamBytes <= 0 || maxOf(signals.pressureThresholdBytes, signals.availableBytes) <= device.totalRamBytes)
  private val validHeap get() = signals.heapFraction in 0.0..1.0
  private val completeMemory get() = device.complete && validMemoryReading && validHeap && signals.lowMemory != null
  private val ampleMemory get() = completeMemory && signals.lowMemory == false && signals.heapFraction < 0.70 &&
    signals.availableBytes - signals.pressureThresholdBytes > signals.pressureThresholdBytes / 2
  private val cool get() = signals.thermal == GlassThermal.None || signals.thermal == GlassThermal.Light
  private fun healthy() = ampleMemory && cool && signals.powerSave == false && !budgetDenied

  private fun criticalReason(): GlassQualityReason? = when {
    signals.lowMemory == true || validMemoryReading && signals.availableBytes <= signals.pressureThresholdBytes ->
      GlassQualityReason.MemoryPressure
    validHeap && signals.heapFraction >= 0.85 -> GlassQualityReason.HeapPressure
    budgetDenied -> GlassQualityReason.VisibleBudgetDenied
    signals.thermal == GlassThermal.SevereOrWorse -> GlassQualityReason.SevereThermal
    else -> null
  }

  private var canRecover = healthy()
  private var prefetch = completeMemory && criticalReason() == null
  private fun physicalProfile() = if (lowRamSeen) GlassMemoryProfile.Protected else device.memoryCeiling
  private fun memoryFor(level: GlassOptics) = minOf(physicalProfile(), when (level) {
    GlassOptics.Full -> GlassMemoryProfile.Full
    GlassOptics.Reduced -> GlassMemoryProfile.Reduced
    GlassOptics.Protected -> GlassMemoryProfile.Protected
  })

  private fun initialOptics(): GlassOptics {
    val cold = when {
      criticalReason() != null || physicalProfile() == GlassMemoryProfile.Protected -> GlassOptics.Protected
      physicalProfile() == GlassMemoryProfile.Full && device.recognizedGpuDriver && device.mediaPerformanceClass > 0 &&
        technicalLimit == GlassOptics.Full && canRecover -> GlassOptics.Full
      else -> GlassOptics.Reduced
    }
    return minOf(cold, appLimit, technicalLimit ?: GlassOptics.Full)
  }

  var decision: GlassQualityDecision = initialOptics().let {
    GlassQualityDecision(it, memoryFor(it), device.heapReferenceBytes, prefetch)
  }
    private set
  val peakDeadlineOverrunNs get() = frames.peakOverrunNs

  /** Una nuova lettura Android può riaprire la risalita. Il veto del lettore ha un rilascio esplicito. */
  internal val needsRecoveryReading: Boolean
    get() = !budgetDenied && (decision.optics < minOf(technicalLimit ?: GlassOptics.Full, appLimit) || !prefetch)

  /** Unica base tempi (ns monotoni) per le chiamate. Un riavvio del clock apre una nuova epoca. */
  private fun observeClock(nowNs: Long): Boolean {
    if (nowNs < 0) return false
    if (nowNs < clock) {
      frames.newEpoch()
      motionStart = nowNs
      lastChange = nowNs
      promotedAt = -1L
      if (rollbackAt >= 0) rollbackAt = nowNs
    }
    clock = nowNs
    return true
  }

  private fun publish(level: GlassOptics, reason: GlassQualityReason): GlassQualityReason? {
    val memory = memoryFor(level)
    val heap = device.heapReferenceBytes
    if (level == decision.optics && memory == decision.memoryProfile &&
      heap == decision.heapReferenceBytes && prefetch == decision.allowPrefetch
    ) return null
    if (level > decision.optics) {
      // Una promozione precedente che ha retto chiude la sua serie di ricadute.
      if (promotedAt >= 0 && !recentlyPromoted(clock)) retryDelayNs = 0L
      promotedAt = clock
      rollbackAt = -1L
    } else if (level < decision.optics) {
      if (reason == GlassQualityReason.VisibleBudgetDenied && recentlyPromoted(clock)) {
        rollbackAt = clock
        retryDelayNs = if (retryDelayNs == 0L) 60_000_000_000L else minOf(240_000_000_000L, retryDelayNs * 2)
      } else {
        rollbackAt = -1L
        retryDelayNs = 0L
      }
      promotedAt = -1L
    }
    decision = GlassQualityDecision(level, memory, heap, prefetch)
    lastChange = clock
    frames.resetVotes()
    return reason
  }

  fun updateCapacity(nowNs: Long, value: GlassDeviceCapacity): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    device = value
    lowRamSeen = lowRamSeen || value.lowRamDevice == true
    return applyLimits(GlassQualityReason.Capacity)
  }

  fun updateConditions(nowNs: Long, value: GlassConditions): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    signals = value
    return applyLimits(GlassQualityReason.MissingSignals)
  }

  /** Passare false solo dopo una nuova ammissione del visibile, anche di dimensione ridotta. */
  fun setVisibleBudgetDenied(nowNs: Long, denied: Boolean): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    budgetDenied = denied
    return applyLimits(GlassQualityReason.VisibleBudgetDenied)
  }

  fun setCeilings(nowNs: Long, shader: GlassOptics?, app: GlassOptics): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    // Un campione assente non cancella un verdetto già ottenuto per questa ricetta.
    if (shader != null) technicalLimit = shader
    appLimit = app
    return applyLimits(GlassQualityReason.TechnicalCeiling)
  }

  private fun applyLimits(defaultReason: GlassQualityReason): GlassQualityReason? {
    canRecover = healthy()
    if (!canRecover) frames.invalidateRecovery()
    val critical = criticalReason()
    if (critical != null || !completeMemory) prefetch = false
    if (critical != null) return publish(GlassOptics.Protected, critical)

    var target = decision.optics
    var reason = defaultReason
    if (target == GlassOptics.Full) {
      val restriction = when {
        signals.thermal == GlassThermal.Moderate -> GlassQualityReason.ModerateThermal
        signals.powerSave == true -> GlassQualityReason.PowerSave
        !completeMemory || signals.powerSave == null || signals.thermal == GlassThermal.Unknown ->
          GlassQualityReason.MissingSignals
        else -> null
      }
      if (restriction != null) {
        target = GlassOptics.Reduced
        reason = restriction
      }
    }
    val shader = technicalLimit ?: GlassOptics.Full
    if (shader < target) {
      target = shader
      reason = GlassQualityReason.TechnicalCeiling
    }
    if (appLimit < target) {
      target = appLimit
      reason = GlassQualityReason.AppCeiling
    }
    return publish(target, reason)
  }

  /** Cambiare refresh effettivo o display apre una nuova serie. Uscire dal moto offre la posa una volta sola. */
  fun onWindow(nowNs: Long, isVisible: Boolean, inMotion: Boolean, displayId: Int, periodNs: Long) =
    onWindow(nowNs, isVisible, inMotion, displayId, periodNs, retryFits = true)

  internal fun onWindow(
    nowNs: Long, isVisible: Boolean, inMotion: Boolean, displayId: Int, periodNs: Long,
    retryFits: Boolean,
  ): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    val changed = visible != isVisible || display != displayId || period != periodNs
    val wasMoving = moving
    visible = isVisible
    display = displayId
    period = periodNs
    moving = visible && inMotion && display >= 0 && period in 1..1_000_000_000L
    if (changed) frames.newEpoch()
    if (changed || moving != wasMoving) {
      frames.breakSequence()
      motionStart = nowNs
    }
    if (!changed && wasMoving && !moving && canRecover && frames.recoveryReady && cooldownOver() &&
      retryFits && (rollbackAt < 0 || nowNs - rollbackAt >= retryDelayNs)) {
      prefetch = true
      return publish(nextOptics, GlassQualityReason.SettledRecovery)
    }
    return null
  }

  private fun cooldownOver() = lastChange < 0 || clock - lastChange >= 5_000_000_000L

  /** TOTAL_DURATION e DEADLINE sono durate, workNs è lavoro app misurabile in quel frame.
   * frameTimeNs è il vsync originale, nowNs l'ora di consegna. Un report perso spezza la consecutività.
   */
  fun onFrame(
    nowNs: Long, frameTimeNs: Long, totalDurationNs: Long, deadlineNs: Long, workNs: Long,
    evidence: GlassFrameEvidence, droppedReports: Int = 0,
  ): GlassQualityReason? {
    if (!observeClock(nowNs) || !moving || frameTimeNs !in motionStart..nowNs) return null
    val reason = frames.record(frameTimeNs, period, totalDurationNs, deadlineNs, workNs, evidence, droppedReports, canRecover)
      ?: return null
    if (reason == GlassQualityReason.TwoBadWindows && !cooldownOver()) return null
    return publish(decision.optics.lower(), reason)
  }
}
