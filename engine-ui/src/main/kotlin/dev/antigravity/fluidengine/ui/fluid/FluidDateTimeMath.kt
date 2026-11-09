package dev.antigravity.fluidengine.ui.fluid

import java.time.*
import java.time.temporal.WeekFields
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Calendar arithmetic deliberately independent of Android and of the scheduling domain. */
internal object FluidDateTimeMath {
  fun weekStart(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek
  fun cells(month: YearMonth, locale: Locale): List<LocalDate?> {
    val padding = Math.floorMod(month.atDay(1).dayOfWeek.value - weekStart(locale).value, 7)
    return List(padding) { null } + (1..month.lengthOfMonth()).map(month::atDay)
  }
  // A gap has no candidate. An overlap requires an explicit offset; never silently shift either.
  fun resolve(local: LocalDateTime, zone: ZoneId, offset: ZoneOffset?): Instant? {
    val offsets = zone.rules.getValidOffsets(local)
    val chosen = if (offsets.size == 1) offsets.single() else offset?.takeIf { it in offsets }
    return chosen?.let(local::toInstant)
  }
  fun valid(value: Instant?, now: Instant, min: Instant?, max: Instant?): Boolean =
    value != null && value >= now && (min == null || value >= min) && (max == null || value <= max)
  fun clamp(value: Instant, min: Instant, max: Instant?): Instant? =
    if (max != null && max < min) null else maxOf(value, min).let { if (max != null) minOf(it, max) else it }
  fun initialMinute(value: Instant, now: Instant, min: Instant?, max: Instant?): Instant? {
    val lower = maxOf(now, min ?: now)
    val floor = lower.truncatedTo(ChronoUnit.MINUTES)
    val first = if (floor == lower) floor else floor.plusSeconds(60)
    return clamp(value.truncatedTo(ChronoUnit.MINUTES), first, max?.truncatedTo(ChronoUnit.MINUTES))
  }
  fun dayAvailable(day: LocalDate, zone: ZoneId, now: Instant, min: Instant?, max: Instant?): Boolean {
    val lower = maxOf(now, min ?: now, day.atStartOfDay(zone).toInstant())
    return lower < day.plusDays(1).atStartOfDay(zone).toInstant() && (max == null || lower <= max)
  }
}
