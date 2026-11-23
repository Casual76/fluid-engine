package dev.antigravity.fluidengine.ui.glass.quality

/** Finestre disgiunte di 120 frame. Le soglie F7 si possono verificare con soli contatori:
 * p95 (rango ceil(0,95 × 120) = 114) è sotto il limite se almeno 114 lavori sono sotto il limite.
 */
internal class GlassFrameHistory {
  private var count = 0
  private var lateCount = 0
  private var slowWorkCount = 0
  private var wholeWindowHealthy = true
  private var consecutiveLate = 0
  private var contiguousSamples = 0
  private var lastFrameTime = -1L
  private var lastObservation = -1L
  private var goodWindows = 0
  private var badWindows = 0
  private var activeNs = 0L

  var peakOverrunNs = 0L
    private set

  val recoveryReady get() = goodWindows >= 3 && activeNs >= RecoveryNs

  fun resetVotes() {
    count = 0
    lateCount = 0
    slowWorkCount = 0
    badWindows = 0
    wholeWindowHealthy = true
    invalidateRecovery()
    breakSequence()
  }

  fun newEpoch() {
    resetVotes()
    peakOverrunNs = 0
    lastFrameTime = -1
  }

  fun invalidateRecovery() {
    goodWindows = 0
    activeNs = 0
    if (count > 0) wholeWindowHealthy = false
  }

  fun breakSequence() {
    contiguousSamples = 0
    consecutiveLate = 0
    lastObservation = -1
  }

  /** frameTime è il timestamp del vsync previsto, univoco nell'epoca. Il callback può arrivare più tardi. */
  fun record(
    frameTime: Long, period: Long, total: Long, deadline: Long, work: Long,
    evidence: GlassFrameEvidence, droppedReports: Int, healthy: Boolean,
  ): GlassQualityReason? {
    if (frameTime <= lastFrameTime) return null
    lastFrameTime = frameTime
    if (evidence != GlassFrameEvidence.MeasuredApp || droppedReports != 0 ||
      deadline <= 0 || total < 0 || work !in 0..total
    ) {
      breakSequence()
      invalidateRecovery()
      return null
    }
    if (healthy && lastObservation >= 0) {
      // Un callback dopo una pausa testimonia al più un periodo di moto, mai tutta l'attesa.
      activeNs = minOf(RecoveryNs, activeNs + minOf(period, frameTime - lastObservation))
    }
    lastObservation = frameTime
    val late = total > deadline
    peakOverrunNs = maxOf(peakOverrunNs, total - deadline)
    contiguousSamples = minOf(4, contiguousSamples + 1)
    val priorRun = consecutiveLate
    consecutiveLate = if (late) minOf(3, consecutiveLate + 1) else 0
    count++
    if (late) lateCount++
    // ceil(4 × period / 5) senza moltiplicare; l'uguaglianza a 0,8 è esclusa.
    if (work >= period - period / 5) slowWorkCount++
    wholeWindowHealthy = wholeWindowHealthy && healthy
    if (lateCount > 0 || slowWorkCount > 6 || !wholeWindowHealthy) goodWindows = 0

    val fastDrop = contiguousSamples == 4 && (priorRun == 3 || consecutiveLate == 3)
    if (count == WindowSize) {
      // 4/120 atteint 3 %, 3/120 le manque. 0/120 seul reste strictement sous 0,5 %.
      badWindows = if (100 * lateCount >= 3 * WindowSize) minOf(2, badWindows + 1) else 0
      goodWindows = if (wholeWindowHealthy && lateCount == 0 && slowWorkCount <= 6) minOf(3, goodWindows + 1) else 0
      count = 0
      lateCount = 0
      slowWorkCount = 0
      wholeWindowHealthy = true
      if (badWindows == 2 && !fastDrop) return GlassQualityReason.TwoBadWindows
    }
    return if (fastDrop) GlassQualityReason.ConsecutiveMisses else null
  }

  companion object {
    private const val WindowSize = 120
    private const val RecoveryNs = 30_000_000_000L
  }
}
