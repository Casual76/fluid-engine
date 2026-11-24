package dev.antigravity.fluidengine.ui.glass.quality.android

import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.view.FrameMetrics
import android.view.View
import android.view.ViewGroup
import android.view.Window
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityPolicy
import kotlin.math.roundToLong

/** Il decor possiede un osservatore senza disegno, dimensioni, input né accessibilità.
 * Riceve la visibilità della finestra anche prima dell'API 34 e per Dialog.hide() senza detach. */
internal class GlassWindow(
  private val process: FluidGlassResources,
  private val window: Window,
  val policy: GlassQualityPolicy,
) : DisplayManager.DisplayListener {
  var owners = 0
  var visibleDenied = false
  var scrollSources = 0
  var visible = false
    private set
  private val decor = window.decorView as ViewGroup
  private val displays = decor.context.getSystemService(DisplayManager::class.java)
  private val marker = object : View(decor.context) {
    override fun onAttachedToWindow() { super.onAttachedToWindow(); checkVisibility() }
    override fun onDetachedFromWindow() { synchronized(process) { setVisible(false) }; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); checkVisibility() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
      super.onVisibilityChanged(changedView, visibility)
      if (changedView === decor) checkVisibility()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); refreshDisplay() }
  }
  private var installed = false
  private var period = 0L
  private var displayId = -1
  private var epoch: WindowFrames? = null

  fun install() {
    marker.visibility = View.GONE
    marker.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    installed = true
    decor.addView(marker, ViewGroup.LayoutParams(0, 0))
    checkVisibility()
  }

  fun uninstall() {
    installed = false
    setVisible(false)
    // Una Dialog può smontare Compose mentre ViewGroup sta ancora iterando i figli nel detach.
    // I listener sono già chiusi; togliere la sentinella GONE solo dopo quella visita.
    Handler(decor.context.mainLooper).post { decor.removeView(marker) }
  }

  fun checkVisibility() = synchronized(process) {
    setVisible(installed && process.mayShow() && decor.isAttachedToWindow &&
      decor.windowVisibility == View.VISIBLE && decor.visibility == View.VISIBLE)
  }

  fun setVisible(value: Boolean) {
    if (visible == value) return
    visible = value
    if (value) {
      displays?.registerDisplayListener(this, Handler(decor.context.mainLooper))
      readDisplay()
      policy.onWindow(System.nanoTime(), true, false, displayId, period)
      startFrames()
    } else {
      stopFrames()
      displays?.unregisterDisplayListener(this)
      policy.onWindow(System.nanoTime(), false, false, displayId, period)
    }
    process.publish()
  }

  private fun readDisplay() {
    val display = decor.display
    val hz = display?.refreshRate ?: 0f
    period = if (hz.isFinite() && hz >= 1f) (1_000_000_000.0 / hz).roundToLong() else 0
    displayId = display?.displayId ?: -1
  }

  private fun refreshDisplay() = synchronized(process) {
    if (!visible) return@synchronized
    val oldPeriod = period
    val oldId = displayId
    readDisplay()
    if (period != oldPeriod || displayId != oldId) {
      stopFrames()
      policy.onWindow(System.nanoTime(), true, false, displayId, period)
      startFrames()
    }
  }

  override fun onDisplayChanged(id: Int) = refreshDisplay()
  override fun onDisplayAdded(id: Int) = refreshDisplay()
  override fun onDisplayRemoved(id: Int) = refreshDisplay()

  private fun startFrames() {
    if (!decor.isHardwareAccelerated || period == 0L) return
    val listener = WindowFrames(process, policy, process.metricsHandler(), displayId, period)
    epoch = listener
    window.addOnFrameMetricsAvailableListener(listener, listener.handler)
    scrollChanged()
  }

  fun scrollChanged() { epoch?.scrolling(scrollSources > 0) }

  private fun stopFrames() {
    val listener = epoch ?: return
    listener.close()
    epoch = null
    window.removeOnFrameMetricsAvailableListener(listener)
  }
}

/** Un oggetto per registrazione. Un callback Android già in coda vede la propria epoca chiusa.
 * Il passo di disegno è un indizio di moto anche per inerzia e animazioni senza dito. Due disegni
 * entro quattro periodi aprono la serie. Dopo quattro periodi senza disegno si valuta la posa.
 * Soglia prudenziale di F7.11 (24 novembre 2026), da verificare sul pannello a refresh variabile. */
internal class WindowFrames(
  private val process: FluidGlassResources,
  private val policy: GlassQualityPolicy,
  val handler: Handler,
  private val displayId: Int,
  private val period: Long,
) : Window.OnFrameMetricsAvailableListener, Runnable {
  private val quietNs = 4 * period
  private val born = System.nanoTime()
  private var closed = false
  private var moving = false
  private var scrolling = false
  private var lastVsync = -1L
  private var lastDelivery = 0L
  private val timings = GlassFrameTimings()

  fun close() {
    closed = true
    handler.removeCallbacks(this)
  }

  fun scrolling(value: Boolean) {
    if (closed || scrolling == value) return
    scrolling = value
    handler.removeCallbacks(this)
    val now = System.nanoTime()
    if (value) {
      moving = true
      policy.onWindow(now, true, true, displayId, period)
    } else {
      lastVsync = -1
      pose(now)
    }
  }

  override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropped: Int) {
    synchronized(process) {
      if (closed) return
      val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
      val now = System.nanoTime()
      if (vsync < born || vsync <= lastVsync || vsync > now) return
      val continuous = lastVsync >= 0 && vsync - lastVsync <= quietNs
      lastVsync = vsync
      lastDelivery = now
      if (!moving && continuous) {
        moving = true
        policy.onWindow(now, true, true, displayId, period)
        schedulePose(quietNs)
      } else if (moving && !scrolling && !continuous) {
        handler.removeCallbacks(this)
        pose(now)
      }
      if (!moving) return
      val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
      val modern = Build.VERSION.SDK_INT >= 31
      val deadline = if (modern) metrics.getMetric(FrameMetrics.DEADLINE) else period
      val work = appWork(metrics)
      val evidence = when {
        !modern -> GlassFrameEvidence.Estimated
        total < 0 || deadline <= 0 || work !in 0..total -> GlassFrameEvidence.Incomplete
        metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L || (total > deadline && work <= deadline) ->
          GlassFrameEvidence.Unattributed
        else -> GlassFrameEvidence.MeasuredApp
      }
      val elapsed = timings.duration(total, deadline, period, evidence)
      val appDuration = timings.duration(work, deadline, period, evidence)
      if (policy.onFrame(now, vsync, elapsed, deadline, appDuration, evidence, dropped) != null) process.publish()
    }
  }

  // CPU e GPU possono sovrapporsi. Escludiamo l'attesa iniziale, swap e sync esterno.
  // Non sommare due tempi sovrapposti per attribuire all'app un ritardo del compositore.
  private fun appWork(metrics: FrameMetrics): Long {
    var cpu = 0L
    for (index in FrameMetrics.INPUT_HANDLING_DURATION..FrameMetrics.DRAW_DURATION) {
      val duration = metrics.getMetric(index)
      if (duration < 0) return -1
      cpu += duration
    }
    val commands = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
    if (commands < 0) return -1
    val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L
    return if (gpu < 0) -1 else maxOf(cpu + commands, gpu)
  }

  // Un solo messaggio pendente, sul thread delle metriche. Non richiede mai un frame alla UI.
  private fun schedulePose(remainingNs: Long) {
    handler.postDelayed(this, (remainingNs + 999_999L) / 1_000_000L)
  }

  override fun run() {
    synchronized(process) {
      if (closed || !moving) return
      val now = System.nanoTime()
      val remaining = quietNs - (now - lastDelivery)
      if (remaining > 0) schedulePose(remaining) else pose(now)
    }
  }

  private fun pose(now: Long) {
    moving = false
    if (policy.onWindow(now, true, false, displayId, period) != null) process.publish()
  }
}
