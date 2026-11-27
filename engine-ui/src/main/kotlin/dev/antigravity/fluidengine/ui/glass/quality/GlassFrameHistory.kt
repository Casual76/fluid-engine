package dev.antigravity.fluidengine.ui.glass.quality

/** Finestre disgiunte di 120 frame. Le soglie F7 si possono verificare con soli contatori:
 * p95 (rango ceil(0,95 × 120) = 114) è sotto il limite se almeno 114 lavori sono sotto il limite.
 *
 * F7.26g (review R3, D2) · **Sopra i 60 Hz la finestra buona si misura su due periodi.** A 120 Hz l'80 % di un periodo è
 * 6,7 ms, e sui fotogrammi veri del Tab S9 sano (`f7-26e/V1-vero.tsv`, il Liquid a Pieno che scorre con il 2 % di
 * fotogrammi lenti per `gfxinfo`) ogni finestra ne aveva 64–102 «lenti» contro i 6 ammessi, più uno o due ritardi
 * dell'app: la risalita coi fotogrammi non arrivava mai, per nessun dispositivo. Ora, con un periodo sotto i 16 ms:
 * «lento» è un lavoro oltre [HighRefreshSlowPeriods] periodi, e una finestra buona ammette fino a [HighRefreshLateFrames]
 * ritardi dell'app (una cattiva ne vuole quattro). Un fotogramma non attribuito, a ogni frequenza, si salta: non vota e
 * non azzera più le finestre buone e i trenta secondi. A 60 Hz il resto è quello di F7.
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

  /** F7.26d, diagnostica delle build di prova: i fotogrammi arrivati qui e quelli che hanno votato. Due somme, niente altro. */
  var calls = 0L
    private set
  var votes = 0L
    private set

  /** F7.26d: lo stato delle finestre, per la riga dei voti delle build di prova. Mai sul percorso di un fotogramma. */
  fun describe() = "finestra=$count tardi=$lateCount lenti=$slowWorkCount sana=$wholeWindowHealthy " +
    "buone=$goodWindows cattive=$badWindows attivoMs=${activeNs / 1_000_000}"

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
    calls++
    // F7.26g: un ritardo che non è dell'app (lo dice WindowFrames) interrompe la serie ma non toglie la risalita.
    if (evidence == GlassFrameEvidence.Unattributed && droppedReports == 0) {
      breakSequence()
      return null
    }
    if (evidence != GlassFrameEvidence.MeasuredApp || droppedReports != 0 ||
      deadline <= 0 || total < 0 || work !in 0..total
    ) {
      breakSequence()
      invalidateRecovery()
      return null
    }
    votes++
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
    val highRefresh = period < HighRefreshPeriodNs
    // ceil(4 × period / 5) senza moltiplicare; l'uguaglianza a 0,8 è esclusa. Sopra i 60 Hz, due periodi (F7.26g).
    if (if (highRefresh) work > HighRefreshSlowPeriods * period else work >= period - period / 5) slowWorkCount++
    val lateAllowed = if (highRefresh) HighRefreshLateFrames else 0
    wholeWindowHealthy = wholeWindowHealthy && healthy
    if (lateCount > lateAllowed || slowWorkCount > 6 || !wholeWindowHealthy) goodWindows = 0

    val fastDrop = contiguousSamples == 4 && (priorRun == 3 || consecutiveLate == 3)
    if (count == WindowSize) {
      // 4/120 atteint 3 %, 3/120 le manque. 0/120 seul reste strictement sous 0,5 %.
      badWindows = if (100 * lateCount >= 3 * WindowSize) minOf(2, badWindows + 1) else 0
      goodWindows = if (wholeWindowHealthy && lateCount <= lateAllowed && slowWorkCount <= 6) minOf(3, goodWindows + 1) else 0
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

    /** F7.26g: sotto questo periodo (sopra i 60 Hz, con margine per i 59,94) valgono le due soglie qui sotto. */
    private const val HighRefreshPeriodNs = 16_000_000L

    /** F7.26g: a 90/120 Hz un fotogramma è «lento» se il lavoro dell'app supera due periodi (16,7 ms a 120 Hz). */
    private const val HighRefreshSlowPeriods = 2L

    /** F7.26g: a 90/120 Hz una finestra buona ammette due ritardi dell'app su 120 (1,7 %); una cattiva ne vuole quattro. */
    private const val HighRefreshLateFrames = 2
  }
}
