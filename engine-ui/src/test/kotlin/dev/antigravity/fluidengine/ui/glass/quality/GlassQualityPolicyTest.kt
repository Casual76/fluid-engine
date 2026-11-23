package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Full
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Protected
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Reduced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassQualityPolicyTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val capable = GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 34, true)
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)
  private val tenMs = 10_000_000L

  private inner class Run(
    device: GlassDeviceCapacity = capable, conditions: GlassConditions = cool,
    shader: GlassOptics? = Full, app: GlassOptics = Full, var period: Long = tenMs,
  ) {
    val policy = GlassQualityPolicy(device, conditions, shader, app)
    val state get() = policy.decision
    var now = 0L
    var display = 0
    fun window(moving: Boolean = true, visible: Boolean = true) = policy.onWindow(now, visible, moving, display, period)
    fun frame(
      total: Long = period / 2, work: Long = total, deadline: Long = period, dropped: Int = 0,
      evidence: GlassFrameEvidence = GlassFrameEvidence.MeasuredApp,
    ): GlassQualityReason? {
      now += period
      return policy.onFrame(now, now, total, deadline, work, evidence, dropped)
    }
    fun timely(count: Int) = repeat(count) { assertNull(frame()) }
    fun oneWindow(misses: Int, size: Int = 120): GlassQualityReason? {
      var result: GlassQualityReason? = null
      repeat(size) { i ->
        val total = if (i % 10 == 0 && i / 10 < misses) period + 1 else period / 2
        val reason = frame(total)
        if (reason != null) {
          assertNull(result)
          result = reason
        }
      }
      return result
    }
    fun recoveryFrames() = timely(30_000_000_000L.div(period).toInt() + 2)
    fun signal(value: GlassConditions) = policy.updateConditions(now, value)
    fun trace(label: String, before: GlassOptics, reason: GlassQualityReason?) {
      println("F7TRACE | ${now / 1_000_000} ms | $label | $before -> ${state.optics} | ${reason ?: "invariato"} | ${state.memoryProfile} | prefetch=${state.allowPrefetch}")
    }
  }

  @Test fun coldStartAndTenWindowsOnCapable120Hz() {
    val r = Run(period = 8_333_333)
    assertEquals(Full, r.state.optics)
    assertEquals(GlassMemoryProfile.Full, r.state.memoryProfile)
    assertEquals(512 * mib, r.state.heapReferenceBytes)
    assertTrue(r.state.allowPrefetch)
    val initial = r.state
    r.window()
    r.timely(1200)
    assertNull(r.window(false))
    assertSame(initial, r.state)
  }

  @Test fun memoryBoundariesIncludeTheExactByteAndTheSmallerHeap() {
    for ((bytes, optics) in listOf(4 * gib - 1 to Protected, 4 * gib to Protected, 4 * gib + 1 to Reduced,
      6 * gib - 1 to Reduced, 6 * gib to Reduced, 6 * gib + 1 to Full)) {
      assertEquals(optics, Run(capable.copy(totalRamBytes = bytes)).state.optics)
    }
    for ((bytes, optics) in listOf(192 * mib - 1 to Protected, 192 * mib to Protected, 192 * mib + 1 to Reduced,
      256 * mib - 1 to Reduced, 256 * mib to Reduced, 256 * mib + 1 to Full)) {
      val result = Run(capable.copy(maxHeapBytes = bytes)).state
      assertEquals(optics, result.optics)
      assertEquals(bytes, result.heapReferenceBytes)
    }
    assertEquals(192 * mib, Run(capable.copy(heapClassMiB = 192)).state.heapReferenceBytes)
    assertEquals(Protected, Run(capable.copy(heapClassMiB = 192)).state.optics)
    assertEquals(Protected, Run(capable.copy(lowRamDevice = true)).state.optics)
    assertEquals(Reduced, Run(capable.copy(totalRamBytes = 6 * gib, mediaPerformanceClass = 0)).state.optics)
  }

  @Test fun unknownDriverCertificationAndShaderCanBeOvercomeByMeasuredMotion() {
    for (r in listOf(Run(capable.copy(recognizedGpuDriver = false)), Run(capable.copy(mediaPerformanceClass = 0)),
      Run(shader = null))) {
      assertEquals(Reduced, r.state.optics)
      r.window()
      r.timely(3000) // Solo 2999 intervalli osservati, 29,990 s.
      assertNull(r.window(false))
      assertEquals(Reduced, r.state.optics)
      r.window()
      r.timely(2)
      assertEquals(Reduced, r.state.optics)
      assertEquals(GlassQualityReason.SettledRecovery, r.window(false))
      assertEquals(Full, r.state.optics)
      assertNull(r.window(false))
    }
  }

  @Test fun knownLimitsRemainBindingAndOnlyActualTransitionsHaveAReason() {
    val r = Run()
    assertEquals(GlassQualityReason.TechnicalCeiling, r.policy.setCeilings(0, Reduced, Full))
    assertNull(r.policy.setCeilings(0, null, Full))
    r.window()
    r.recoveryFrames()
    assertNull(r.window(false))
    assertEquals(Reduced, r.state.optics)
    assertEquals(GlassQualityReason.AppCeiling, r.policy.setCeilings(r.now, Full, Protected))
    assertNull(r.policy.setCeilings(r.now, Full, Full))
    r.window()
    r.recoveryFrames()
    assertEquals(GlassQualityReason.SettledRecovery, r.window(false))
    assertEquals(Reduced, r.state.optics)
  }

  @Test fun incompleteMemoryDefersOptionalWorkUntilFreshEvidenceAndPose() {
    val r = Run(GlassDeviceCapacity(), GlassConditions())
    assertEquals(Reduced, r.state.optics)
    assertFalse(r.state.allowPrefetch)
    assertEquals(0L, r.state.heapReferenceBytes)
    r.window()
    r.recoveryFrames()
    assertNull(r.window(false))
    assertEquals(GlassQualityReason.Capacity, r.policy.updateCapacity(r.now, capable))
    assertNull(r.signal(cool))
    r.window()
    r.recoveryFrames()
    assertEquals(GlassQualityReason.SettledRecovery, r.window(false))
    assertEquals(Full, r.state.optics)
    assertTrue(r.state.allowPrefetch)
  }

  @Test fun firstVisibleDenialPrecedesFirstFrameAndLowRamMemorySurvivesRecovery() {
    val r = Run(capable.copy(lowRamDevice = true, totalRamBytes = 4 * gib, maxHeapBytes = 192 * mib))
    assertEquals(Protected, r.state.optics)
    val first = r.state.optics
    r.trace("prima apertura 4 GiB", first, null)
    val denial = r.policy.setVisibleBudgetDenied(0, true)
    assertEquals(GlassQualityReason.VisibleBudgetDenied, denial)
    assertFalse(r.state.allowPrefetch)
    r.trace("visibile negato", first, denial)
    assertNull(r.policy.setVisibleBudgetDenied(0, true))
    assertNull(r.policy.setVisibleBudgetDenied(0, false))
    r.trace("visibile ridotto ammesso", first, null)
    r.window()
    r.recoveryFrames()
    val recovery = r.window(false)
    assertEquals(GlassQualityReason.SettledRecovery, recovery)
    assertEquals(Reduced, r.state.optics)
    assertTrue(r.state.allowPrefetch)
    assertEquals(GlassMemoryProfile.Protected, r.state.memoryProfile)
    r.trace("prima posa", first, recovery)
    r.window(visible = false)
    r.policy.updateCapacity(r.now, capable)
    r.window()
    r.recoveryFrames()
    assertEquals(GlassQualityReason.SettledRecovery, r.window(false))
    assertEquals(Full, r.state.optics)
    assertEquals(GlassMemoryProfile.Protected, r.state.memoryProfile)
    r.trace("rientro e seconda posa", Reduced, GlassQualityReason.SettledRecovery)
  }

  @Test fun twoNonOverlappingWindowsRequire120AndFourMissesEach() {
    val r = Run()
    r.window()
    assertNull(r.oneWindow(4, 119))
    assertNull(r.frame())
    repeat(5) {
      assertNull(r.oneWindow(3))
      assertNull(r.oneWindow(4))
    }
    assertNull(r.oneWindow(4, 119))
    assertEquals(Full, r.state.optics)
    assertEquals(GlassQualityReason.TwoBadWindows, r.frame())
    assertEquals(Reduced, r.state.optics)
    assertNull(r.policy.onFrame(r.now, r.now, 1, 1, 1, GlassFrameEvidence.MeasuredApp))
    assertNull(r.oneWindow(4))
    assertNull(r.oneWindow(4)) // Solo 2,4 s dal cambio; la pausa ordinaria dura 5 s.
    assertEquals(Reduced, r.state.optics)
  }

  @Test fun wakeAt30HzUsesItsOwnPeriodAndDeadlineCanSpanTwoSlots() {
    val r = Run(period = 33_333_333)
    r.window()
    repeat(4) { assertNull(r.frame(33_000_000)) }
    assertEquals(0L, r.policy.peakDeadlineOverrunNs)
    r.period = 8_333_333
    r.window()
    repeat(4) { assertNull(r.frame(total = 12_000_000, deadline = 16_666_666)) }
    assertEquals(0L, r.policy.peakDeadlineOverrunNs)
    repeat(2) { assertNull(r.frame(r.period + 1)) }
    assertEquals(GlassQualityReason.ConsecutiveMisses, r.frame(r.period + 1))
    assertEquals(1L, r.policy.peakDeadlineOverrunNs)
    assertEquals(Reduced, r.state.optics)
  }

  @Test fun rapidDropRequiresThreeConsecutiveAmongFourAndFreshEvidenceForAnotherDrop() {
    val r = Run()
    r.window()
    for (late in listOf(true, true, false, true)) {
      assertNull(r.frame(if (late) 2 * tenMs else tenMs))
    }
    assertEquals(tenMs, r.policy.peakDeadlineOverrunNs)
    assertNull(r.frame(2 * tenMs))
    assertEquals(GlassQualityReason.ConsecutiveMisses, r.frame(2 * tenMs))
    repeat(3) { assertNull(r.frame(2 * tenMs)) }
    assertEquals(GlassQualityReason.ConsecutiveMisses, r.frame(tenMs))
    assertEquals(Protected, r.state.optics)
  }

  @Test fun severeDuringCooldownThenOnlyOneUpgradePerHealthySettling() {
    val r = Run()
    r.window()
    r.timely(1)
    repeat(2) { assertNull(r.frame(2 * tenMs)) }
    val first = r.frame(2 * tenMs)
    assertEquals(GlassQualityReason.ConsecutiveMisses, first)
    r.trace("tre ritardi app", Full, first)
    val heat = r.signal(cool.copy(thermal = GlassThermal.SevereOrWorse))
    assertEquals(GlassQualityReason.SevereThermal, heat)
    assertFalse(r.state.allowPrefetch)
    r.trace("termica SEVERE", Reduced, heat)
    assertNull(r.signal(cool.copy(powerSave = true)))
    r.recoveryFrames()
    assertNull(r.window(false))
    r.trace("posa LIGHT con risparmio", Protected, null)
    assertNull(r.signal(cool.copy(heapFraction = 0.70)))
    r.window()
    r.recoveryFrames()
    assertNull(r.window(false))
    r.trace("posa con heap al 70%", Protected, null)
    assertNull(r.signal(cool))
    r.window()
    r.recoveryFrames()
    val up = r.window(false)
    assertEquals(GlassQualityReason.SettledRecovery, up)
    assertEquals(Reduced, r.state.optics)
    r.trace("posa fresca", Protected, up)
    r.window()
    r.recoveryFrames()
    val full = r.window(false)
    assertEquals(GlassQualityReason.SettledRecovery, full)
    r.trace("posa successiva", Reduced, full)
    assertEquals(Full, r.state.optics)
  }

  @Test fun heapThresholdAndAvailablePressureApplySynchronouslyEvenInsideCooldown() {
    for (fraction in listOf(Math.nextDown(0.85), 0.85, Math.nextUp(0.85))) {
      val r = Run()
      assertEquals(GlassQualityReason.PowerSave, r.signal(cool.copy(powerSave = true)))
      val reason = r.signal(cool.copy(heapFraction = fraction))
      if (fraction < 0.85) {
        assertNull(reason)
        assertEquals(Reduced, r.state.optics)
      } else {
        assertEquals(GlassQualityReason.HeapPressure, reason)
        assertEquals(Protected, r.state.optics)
        assertFalse(r.state.allowPrefetch)
      }
    }
    for (delta in -1L..1L) {
      val r = Run()
      val reason = r.signal(cool.copy(availableBytes = cool.pressureThresholdBytes + delta))
      assertEquals(if (delta <= 0) GlassQualityReason.MemoryPressure else null, reason)
      assertEquals(delta > 0, r.state.allowPrefetch)
    }
    val r = Run()
    assertEquals(GlassQualityReason.ModerateThermal, r.signal(cool.copy(thermal = GlassThermal.Moderate)))
    assertEquals(Reduced, r.state.optics)
    assertEquals(GlassQualityReason.MemoryPressure, r.signal(cool.copy(lowMemory = true)))
    assertFalse(r.state.allowPrefetch)
  }

  @Test fun recoveryNeedsStrictHeapAndAvailableMarginAndKnownPowerAndThermal() {
    val threshold = 101L
    for ((conditions, allowed) in listOf(
      cool.copy(availableBytes = 151, pressureThresholdBytes = threshold) to false,
      cool.copy(availableBytes = 152, pressureThresholdBytes = threshold) to true,
      cool.copy(availableBytes = 150, pressureThresholdBytes = 100) to false,
      cool.copy(heapFraction = Math.nextDown(0.70)) to true,
      cool.copy(heapFraction = 0.70) to false,
      cool.copy(heapFraction = Math.nextUp(0.70)) to false,
      cool.copy(thermal = GlassThermal.Unknown) to false,
      cool.copy(powerSave = null) to false,
      cool.copy(heapFraction = Double.NaN) to false,
      cool.copy(heapFraction = Double.POSITIVE_INFINITY) to false,
      cool.copy(heapFraction = -0.5) to false,
      cool.copy(heapFraction = 1.5) to false,
      cool.copy(availableBytes = capable.totalRamBytes + 1) to false,
      cool.copy(availableBytes = -1) to false,
      cool.copy(pressureThresholdBytes = 0) to false,
    )) {
      val r = Run(shader = null)
      r.signal(conditions)
      r.window()
      r.recoveryFrames()
      assertEquals(conditions.toString(), allowed, r.window(false) == GlassQualityReason.SettledRecovery)
    }
  }

  @Test fun recoveryUsesThreeNonOverlappingGoodWindowsAndNearestRankP95() {
    // A 10 Hz la terza finestra termina dopo 36 s. Sei lavori al limite lasciano buono il rango 114.
    for ((slow, workDelta, shouldRise) in listOf(Triple(6, 0L, true), Triple(7, -1L, true),
      Triple(7, 0L, false), Triple(7, 1L, false))) {
      val r = Run(shader = null, period = 100_000_000)
      r.window()
      repeat(359) { i ->
        val work = if (i % 120 < slow) 80_000_000 + workDelta else 20_000_000
        assertNull(r.frame(work))
      }
      assertNull(r.window(false))
      r.window()
      r.timely(1)
      assertEquals(shouldRise, r.window(false) == GlassQualityReason.SettledRecovery)
    }
    val r = Run(shader = null, period = 100_000_000)
    r.window()
    repeat(3) { assertNull(r.oneWindow(1)) } // 1/120 supera lo 0,5%.
    assertNull(r.window(false))
  }

  @Test fun badWindowsRespectTheFiveSecondBoundary() {
    for (delta in -1L..1L) {
      val r = Run()
      r.window()
      repeat(4) { r.frame(2 * tenMs) }
      assertEquals(Reduced, r.state.optics)
      r.now += 5_000_000_000L - 240 * tenMs + delta
      assertNull(r.oneWindow(4))
      assertEquals(if (delta < 0) null else GlassQualityReason.TwoBadWindows, r.oneWindow(4))
    }
  }

  @Test fun missingReportsAndInvalidSamplesNeverVoteOrBridgeAFastDrop() {
    val r = Run()
    r.window()
    repeat(2) { assertNull(r.frame(2 * tenMs)) }
    assertNull(r.frame(2 * tenMs, dropped = 9))
    repeat(2) { assertNull(r.frame(2 * tenMs)) }
    for (evidence in GlassFrameEvidence.entries.filter { it != GlassFrameEvidence.MeasuredApp }) {
      repeat(120) { assertNull(r.frame(2 * tenMs, evidence = evidence)) }
    }
    repeat(2) { assertNull(r.frame(2 * tenMs)) }
    assertNull(r.frame(total = 1, work = 2))
    assertNull(r.frame(total = -1))
    assertNull(r.frame(deadline = 0))
    assertNull(r.frame(dropped = -1))
    repeat(3) { assertNull(r.frame(2 * tenMs)) }
    assertEquals(GlassQualityReason.ConsecutiveMisses, r.frame())
    assertEquals(Reduced, r.state.optics)
    repeat(3001) { assertNull(r.frame(evidence = GlassFrameEvidence.Estimated)) }
    assertNull(r.window(false))
  }

  @Test fun backgroundDisplayRefreshAndClockRestartEachDiscardPartialHistory() {
    for (change in listOf("background", "display", "refresh", "clock")) {
      val r = Run(shader = null)
      r.window()
      r.timely(3600)
      when (change) {
        "background" -> {
          r.window(visible = false)
          assertNull(r.window(visible = false))
          r.now += 3_600_000_000_000
          r.timely(120)
          r.window()
        }
        "display" -> { r.display++; r.window() }
        "refresh" -> { r.period = 16_666_667; r.window() }
        "clock" -> { r.now = 0; r.window() }
      }
      assertNull(r.window(false))
      r.window()
      r.recoveryFrames()
      assertEquals(change, GlassQualityReason.SettledRecovery, r.window(false))
    }
  }

  @Test fun oldAndFutureCallbacksAreIgnoredAndSilentTimeDoesNotEarnRecovery() {
    val r = Run(shader = null, period = 8_333_333)
    r.window()
    r.timely(360)
    repeat(5) {
      assertNull(r.policy.onFrame(r.now, r.now, 2 * r.period, r.period, r.period, GlassFrameEvidence.MeasuredApp))
      assertNull(r.policy.onFrame(r.now, r.now - 1, 2 * r.period, r.period, r.period, GlassFrameEvidence.MeasuredApp))
      assertNull(r.policy.onFrame(r.now, r.now + 1, 2 * r.period, r.period, r.period, GlassFrameEvidence.MeasuredApp))
    }
    r.now += 60_000_000_000
    r.timely(1)
    assertNull(r.window(false))
    r.timely(4000)
    r.period = 0
    r.window()
    assertNull(r.policy.onFrame(r.now, r.now, 1, 1, 1, GlassFrameEvidence.MeasuredApp))
    assertEquals(Reduced, r.state.optics)
  }

  @Test fun twoWindowOwnersNeverShareVotesAndClosingTheLastOneTwiceIsIdempotent() {
    val first = Run()
    val last = Run()
    first.window()
    last.window()
    assertNull(first.oneWindow(4))
    assertNull(last.oneWindow(4))
    first.window(visible = false)
    first.timely(120)
    assertEquals(GlassQualityReason.TwoBadWindows, last.oneWindow(4))
    assertEquals(Full, first.state.optics)
    assertNull(last.window(visible = false))
    assertNull(last.window(visible = false))
    last.now += 60_000_000_000
    last.timely(360)
    last.window()
    last.timely(360)
    assertNull(last.window(false))
    assertEquals(Reduced, last.state.optics)
  }

  @Test fun criticalPressureCanAlreadyBePresentInTheConstructor() {
    for (signals in listOf(cool.copy(lowMemory = true), cool.copy(heapFraction = 0.85),
      cool.copy(availableBytes = cool.pressureThresholdBytes), cool.copy(thermal = GlassThermal.SevereOrWorse))) {
      val r = Run(conditions = signals)
      assertEquals(Protected, r.state.optics)
      assertEquals(GlassMemoryProfile.Protected, r.state.memoryProfile)
      assertFalse(r.state.allowPrefetch)
    }
  }
}
