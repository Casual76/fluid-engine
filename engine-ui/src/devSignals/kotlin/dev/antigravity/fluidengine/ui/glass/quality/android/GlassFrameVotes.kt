package dev.antigravity.fluidengine.ui.glass.quality.android

import android.os.Build
import android.view.FrameMetrics
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityPolicy
import java.util.Locale

/**
 * F7.26d · Come la politica del vetro vede ogni fotogramma. Solo nelle build di prova, e solo col profilo simulato acceso
 * (l'extra `fluid.glass.device`, anche `vero`): [WindowFrames] chiama questi metodi dietro `GlassVariantSignals.noting`,
 * che in release è la costante `false`, e la classe di release è vuota.
 *
 * Una riga `voti` nel tag `FluidF7Sim` ogni 120 fotogrammi in moto e a ogni posa: quanti hanno votato e quanti no, e
 * perché; che tempo della GPU portavano (assente, zero, più lungo del totale, più lungo della CPU); i fotogrammi scartati
 * prima di ogni voto (vecchi, doppi, dal futuro, a finestra ferma). Con `voti=ogni` nel profilo, anche una riga per
 * fotogramma. I contatori sono campi; le righe nascono qui, sul filo delle metriche, mai nel listener.
 */
internal class GlassFrameVotes(
  private val displayId: Int, private val period: Long, private val policy: GlassQualityPolicy,
) {
  private val epoch = ++epochs
  private var n = 0
  private var app = 0
  private var appLate = 0
  private var unFirst = 0
  private var unLate = 0
  private var incTotal = 0
  private var incDeadline = 0
  private var incCpu = 0
  private var incCommands = 0
  private var incGpu = 0
  private var incWork = 0
  private var estimated = 0
  private var droppedFrames = 0
  private var droppedReports = 0
  private var old = 0
  private var duplicate = 0
  private var future = 0
  private var still = 0
  private var gpuMissing = 0
  private var gpuZero = 0
  private var gpuOverTotal = 0
  private var gpuBound = 0
  private var late = 0
  private var maxTotal = 0L
  private var maxGpu = 0L
  private var maxCpu = 0L
  private var maxWait = 0L
  private var minDeadline = Long.MAX_VALUE
  private var maxDeadline = 0L
  private var lastCalls = policy.frameCalls
  private var lastVotes = policy.frameVotes
  private var called = 0L
  private var voted = 0L
  private var serial = 0L

  init {
    GlassVariantSignals.note { "voti epoca=$epoch display=$displayId periodo=${ms(period)}ms" }
  }

  fun discarded(vsync: Long, born: Long, lastVsync: Long, now: Long) {
    when {
      vsync < born -> old++
      vsync <= lastVsync -> duplicate++
      vsync > now -> future++
    }
  }

  fun still() { still++ }

  fun frame(metrics: FrameMetrics, dropped: Int, total: Long, deadline: Long, work: Long, evidence: GlassFrameEvidence) {
    var cpu = 0L
    var cpuMissing = false
    for (index in FrameMetrics.INPUT_HANDLING_DURATION..FrameMetrics.DRAW_DURATION) {
      val value = metrics.getMetric(index)
      if (value < 0) cpuMissing = true else cpu += value
    }
    val commands = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
    val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L
    val wait = metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
    val sync = metrics.getMetric(FrameMetrics.SYNC_DURATION)
    val swap = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
    val firstDraw = metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L
    val isLate = total >= 0 && deadline > 0 && total > deadline
    n++
    if (isLate) late++
    if (dropped > 0) { droppedFrames++; droppedReports += dropped }
    when {
      gpu < 0 -> gpuMissing++
      gpu == 0L -> gpuZero++
      else -> {
        if (gpu > total) gpuOverTotal++
        if (gpu > cpu + maxOf(commands, 0L)) gpuBound++
      }
    }
    maxTotal = maxOf(maxTotal, total)
    maxGpu = maxOf(maxGpu, gpu)
    maxCpu = maxOf(maxCpu, cpu + maxOf(commands, 0L))
    maxWait = maxOf(maxWait, wait)
    if (deadline > 0) { minDeadline = minOf(minDeadline, deadline); maxDeadline = maxOf(maxDeadline, deadline) }
    val why = when (evidence) {
      GlassFrameEvidence.Estimated -> { estimated++; "prima-di-31" }
      GlassFrameEvidence.Incomplete -> when {
        total < 0 -> { incTotal++; "totale<0" }
        deadline <= 0 -> { incDeadline++; "scadenza<=0" }
        cpuMissing -> { incCpu++; "cpu<0" }
        commands < 0 -> { incCommands++; "comandi<0" }
        gpu < 0 -> { incGpu++; "gpu<0" }
        else -> { incWork++; "lavoro>totale" }
      }
      GlassFrameEvidence.Unattributed -> if (firstDraw) { unFirst++; "primo-disegno" } else { unLate++; "tardi-lavoro-corto" }
      GlassFrameEvidence.MeasuredApp -> { app++; if (isLate) { appLate++; "tardi" } else "in-orario" }
    }
    val calls = policy.frameCalls
    val votes = policy.frameVotes
    val reached = calls > lastCalls
    val counted = votes > lastVotes
    called += calls - lastCalls
    voted += votes - lastVotes
    lastCalls = calls
    lastVotes = votes
    serial++
    if (GlassVariantSignals.simulation?.everyFrame == true) GlassVariantSignals.note {
      "fotogramma $epoch/$serial tot=${ms(total)} scad=${ms(deadline)} attesa=${ms(wait)} cpu=${ms(cpu)} " +
        "sync=${ms(sync)} comandi=${ms(commands)} swap=${ms(swap)} gpu=${ms(gpu)} lavoro=${ms(work)} " +
        "voto=${evidence.name} perché=$why persi=$dropped " +
        "storia=${if (reached) 1 else 0} votato=${if (counted) 1 else 0}"
    }
    if (n >= 120) flush("120")
  }

  /** A ogni posa, dopo la decisione: la riga c'è sempre, anche senza fotogrammi dall'ultima, col livello uscito. */
  fun pose(why: Int) {
    flush(when (why) { ScrollEnd -> "posa-fine-scorrimento"; Gap -> "posa-buco"; else -> "posa-quiete" }, always = true)
  }

  private fun flush(cause: String, always: Boolean = false) {
    if (!always && n == 0 && old + duplicate + future + still == 0) return
    GlassVariantSignals.note {
      "voti $cause epoca=$epoch fotogrammi=$n votati=$voted storia=$called app=$app (tardi=$appLate) " +
        "nonattribuiti=${unFirst + unLate} (primo=$unFirst tardi-lavoro-corto=$unLate) " +
        "incompleti=${incTotal + incDeadline + incCpu + incCommands + incGpu + incWork} (gpu<0=$incGpu " +
        "lavoro>totale=$incWork cpu<0=$incCpu comandi<0=$incCommands totale<0=$incTotal scadenza=$incDeadline) " +
        "stimati=$estimated persi=$droppedFrames/$droppedReports | scarti vecchi=$old doppi=$duplicate futuri=$future " +
        "fermo=$still | gpu assente=$gpuMissing zero=$gpuZero >totale=$gpuOverTotal >cpu=$gpuBound | tardi=$late/$n " +
        "max tot=${ms(maxTotal)} gpu=${ms(maxGpu)} cpu+comandi=${ms(maxCpu)} attesa=${ms(maxWait)} " +
        "scadenza=${if (maxDeadline == 0L) "-" else "${ms(minDeadline)}-${ms(maxDeadline)}"} periodo=${ms(period)} | " +
        "politica ottica=${policy.decision.optics} ${policy.describeFrames()}"
    }
    n = 0; app = 0; appLate = 0; unFirst = 0; unLate = 0
    incTotal = 0; incDeadline = 0; incCpu = 0; incCommands = 0; incGpu = 0; incWork = 0
    estimated = 0; droppedFrames = 0; droppedReports = 0; old = 0; duplicate = 0; future = 0; still = 0
    gpuMissing = 0; gpuZero = 0; gpuOverTotal = 0; gpuBound = 0; late = 0
    maxTotal = 0; maxGpu = 0; maxCpu = 0; maxWait = 0; minDeadline = Long.MAX_VALUE; maxDeadline = 0
    called = 0; voted = 0
  }

  companion object {
    const val ScrollEnd = 0
    const val Gap = 1
    const val Quiet = 2
    private var epochs = 0

    private fun ms(ns: Long) = if (ns < 0) ns.toString() else String.format(Locale.ROOT, "%.2f", ns / 1e6)
  }
}
