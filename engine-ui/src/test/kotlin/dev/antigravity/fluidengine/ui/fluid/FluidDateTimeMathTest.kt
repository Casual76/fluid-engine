package dev.antigravity.fluidengine.ui.fluid

import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class FluidDateTimeMathTest {
  @Test fun gapsAreRejectedAndOverlapsRequireExplicitOffset() {
    listOf(Triple("Europe/Rome", "2026-03-29T02:30", "2026-10-25T02:30"),
      Triple("America/New_York", "2026-03-08T02:30", "2026-11-01T01:30")).forEach { (id, gap, overlap) ->
      val zone = ZoneId.of(id)
      assertNull(FluidDateTimeMath.resolve(LocalDateTime.parse(gap), zone, null))
      val local = LocalDateTime.parse(overlap)
      assertNull(FluidDateTimeMath.resolve(local, zone, null))
      val candidates = zone.rules.getValidOffsets(local).map { FluidDateTimeMath.resolve(local, zone, it)!! }
      assertEquals(2, candidates.size)
      assertEquals(3600L, Duration.between(candidates[0], candidates[1]).seconds)
      candidates.forEach { assertEquals(local, it.atZone(zone).toLocalDateTime()) }
    }
  }
  @Test fun leapDayAndMonthYearBoundaries() {
    assertEquals(29, FluidDateTimeMath.cells(YearMonth.of(2024, 2), Locale.ITALY).filterNotNull().size)
    assertEquals(28, FluidDateTimeMath.cells(YearMonth.of(2025, 2), Locale.US).filterNotNull().size)
    assertEquals(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31).plusDays(1))
    assertEquals(YearMonth.of(2026, 12), YearMonth.of(2027, 1).minusMonths(1))
  }
  @Test fun localeWeekStartAndLeadingCells() {
    assertEquals(DayOfWeek.MONDAY, FluidDateTimeMath.weekStart(Locale.ITALY))
    assertEquals(DayOfWeek.SUNDAY, FluidDateTimeMath.weekStart(Locale.US))
    assertEquals(6, FluidDateTimeMath.cells(YearMonth.of(2026, 11), Locale.ITALY).takeWhile { it == null }.size)
    assertNotNull(FluidDateTimeMath.cells(YearMonth.of(2026, 11), Locale.US).first())
  }
  @Test fun boundsClampAndConfirmationRevalidatesPast() {
    val now = Instant.parse("2026-11-09T10:00:00Z")
    val future = now.plusSeconds(60)
    assertEquals(now, FluidDateTimeMath.clamp(now.minusSeconds(60), now, future))
    assertEquals(future, FluidDateTimeMath.clamp(future.plusSeconds(60), now, future))
    assertNull(FluidDateTimeMath.clamp(now, future, now))
    assertTrue(FluidDateTimeMath.valid(future, now, future, future))
    assertFalse(FluidDateTimeMath.valid(future, future.plusNanos(1), null, null))
    assertFalse(FluidDateTimeMath.valid(future, now, null, now))
    assertFalse(FluidDateTimeMath.valid(null, now, null, null))
  }
  @Test fun localDayBoundsHandleDstAndMidnight() {
    val zone = ZoneId.of("Europe/Rome")
    val day = LocalDate.of(2026, 3, 29)
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    assertEquals(23L, Duration.between(start, end).toHours())
    assertTrue(FluidDateTimeMath.dayAvailable(day, zone, start, end.minusSeconds(1), end))
    assertFalse(FluidDateTimeMath.dayAvailable(day, zone, end, null, null))
    assertFalse(FluidDateTimeMath.dayAvailable(day, zone, start, null, start.minusNanos(1)))
  }
  @Test fun initialMinuteRoundsInsideBoundsRatherThanBelowMinimum() {
    val now = Instant.parse("2026-11-09T10:00:30Z")
    assertEquals(Instant.parse("2026-11-09T10:01:00Z"), FluidDateTimeMath.initialMinute(now, now, null, null))
    assertNull(FluidDateTimeMath.initialMinute(now, now, null, now.plusSeconds(20)))
    assertEquals(Instant.parse("2026-11-09T10:02:00Z"),
      FluidDateTimeMath.initialMinute(now.plusSeconds(3600), now, null, now.plusSeconds(120)))
  }

}
