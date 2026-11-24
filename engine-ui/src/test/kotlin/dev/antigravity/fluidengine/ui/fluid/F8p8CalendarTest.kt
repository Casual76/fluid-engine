package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.snapshots.Snapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.8 · il calendario senza disegno: i tocchi della scelta (un giorno, giorni sparsi, l'intervallo con il primo tocco, il
 * secondo e quello che ricomincia), gli stili dei giorni, dove si posa una pagina, la resistenza oltre i limiti, il moto
 * del cambio di mese senza allocazioni per fotogramma, i segni che scrivono solo i giorni che cambiano, e i testi della
 * capsula.
 */
class F8p8CalendarTest {
  private fun d(text: String) = LocalDate.parse(text)

  @Test fun singleAndScatteredDays() {
    assertEquals(FluidDateSelection.Single(d("2026-11-25")), fluidCalendarTap(FluidDateSelection.Single(null), d("2026-11-25")))
    val one = fluidCalendarTap(FluidDateSelection.Multiple(), d("2026-11-25"))
    // Un giorno di un altro mese si aggiunge; toccato di nuovo, si toglie.
    val two = fluidCalendarTap(one, d("2026-12-03"))
    assertEquals(FluidDateSelection.Multiple(setOf(d("2026-11-25"), d("2026-12-03"))), two)
    assertEquals(FluidDateSelection.Multiple(setOf(d("2026-12-03"))), fluidCalendarTap(two, d("2026-11-25")))
  }

  @Test fun rangeFirstSecondAndRestartingTaps() {
    val first = fluidCalendarTap(FluidDateSelection.Range(), d("2026-11-09"))
    assertEquals(FluidDateSelection.Range(d("2026-11-09"), null), first)
    val second = fluidCalendarTap(first, d("2026-11-20"))
    assertEquals(FluidDateSelection.Range(d("2026-11-09"), d("2026-11-20")), second)
    assertEquals(12L, (second as FluidDateSelection.Range).range!!.dayCount)
    // A intervallo completo, un tocco ricomincia da li'.
    assertEquals(FluidDateSelection.Range(d("2026-11-15"), null), fluidCalendarTap(second, d("2026-11-15")))
    // Un secondo tocco prima dell'inizio ricomincia dal giorno toccato.
    assertEquals(FluidDateSelection.Range(d("2026-11-02"), null), fluidCalendarTap(first, d("2026-11-02")))
    // Lo stesso giorno due volte e' un intervallo di un giorno.
    assertEquals(FluidDateSelection.Range(d("2026-11-09"), d("2026-11-09")), fluidCalendarTap(first, d("2026-11-09")))
  }

  @Test fun stylesOfTheBand() {
    val range = FluidDateSelection.Range(d("2026-11-09"), d("2026-11-20"))
    assertEquals(FluidCalendarStyle.RangeStart, fluidCalendarStyleOf(range, d("2026-11-09")))
    assertEquals(FluidCalendarStyle.RangeEnd, fluidCalendarStyleOf(range, d("2026-11-20")))
    assertEquals(FluidCalendarStyle.InRange, fluidCalendarStyleOf(range, d("2026-11-15")))
    assertEquals(0, fluidCalendarStyleOf(range, d("2026-11-21")))
    val oneDay = FluidDateSelection.Range(d("2026-11-09"), d("2026-11-09"))
    assertEquals(FluidCalendarStyle.RangeStart or FluidCalendarStyle.RangeEnd, fluidCalendarStyleOf(oneDay, d("2026-11-09")))
    assertEquals(FluidCalendarStyle.Selected, fluidCalendarStyleOf(FluidDateSelection.Multiple(setOf(d("2026-12-03"))), d("2026-12-03")))
  }

  @Test fun aPageLandsOnAWholeMonthAtMostOneAway() {
    // Lasciata a un quinto senza velocita': torna. Oltre la meta': passa.
    assertEquals(0, fluidCalendarPageLanding(0.2f, 0f, reduced = false, canPrev = true, canNext = true))
    assertEquals(1, fluidCalendarPageLanding(0.6f, 0f, reduced = false, canPrev = true, canNext = true))
    // Un colpo deciso da poco: passa, ma mai oltre un mese.
    assertEquals(1, fluidCalendarPageLanding(0.15f, 3f, reduced = false, canPrev = true, canNext = true))
    assertEquals(1, fluidCalendarPageLanding(0.15f, 30f, reduced = false, canPrev = true, canNext = true))
    assertEquals(-1, fluidCalendarPageLanding(-0.1f, -4f, reduced = false, canPrev = true, canNext = true))
    // All'ultimo mese consentito non si passa.
    assertEquals(0, fluidCalendarPageLanding(0.7f, 3f, reduced = false, canPrev = true, canNext = false))
    // Col movimento ridotto la proiezione e' corta: un colpo leggero da poco non passa.
    assertEquals(0, fluidCalendarPageLanding(0.15f, 3f, reduced = true, canPrev = true, canNext = true))
  }

  @Test fun theSettleSpringNeverPassesTheArrival() {
    // Un colpo fortissimo verso il mese dopo: la molla parte veloce ma non oltre il limite che la farebbe passare.
    val w = (2.0 * Math.PI / FluidCalendarDefaults.PageResponseSeconds).toFloat()
    assertEquals(w * 0.7f, fluidCalendarSettleSpeed(0.3f, 40f, 1f, FluidCalendarDefaults.PageResponseSeconds), 1e-4f)
    assertEquals(2f, fluidCalendarSettleSpeed(0.3f, 2f, 1f, FluidCalendarDefaults.PageResponseSeconds), 0f)
    // La velocita' che si allontana dall'arrivo resta: la molla la frena e torna.
    assertEquals(-3f, fluidCalendarSettleSpeed(0.3f, -3f, 1f, FluidCalendarDefaults.PageResponseSeconds), 0f)
    val m = FluidDetentInertia()
    m.springTo(0.3f, fluidCalendarSettleSpeed(0.3f, 40f, 1f, FluidCalendarDefaults.PageResponseSeconds), 1f, 800f,
      FluidCalendarDefaults.PageResponseSeconds)
    var max = 0f
    while (m.step(1f / 120f)) max = maxOf(max, m.position)
    assertTrue("oltre l'arrivo: $max", max <= 1f + 1e-4f)
    assertEquals(1f, m.position, 0f)
  }

  @Test fun rubberBandResistsAndNeverReachesAPage() {
    assertEquals(0f, fluidCalendarRubber(0f), 0f)
    var last = 0f
    for (i in 1..20) {
      val r = fluidCalendarRubber(i * 0.1f)
      assertTrue(r > last && r < 1f)
      last = r
    }
    assertTrue(fluidCalendarRubber(0.5f) < 0.25f)
  }

  @Test fun rowsAndWeekStart() {
    // Novembre 2026 comincia di domenica: in Italia sei celle vuote, il 24 sta nella quinta riga (4), il 30 nella sesta.
    assertEquals(4, fluidCalendarRowOf(d("2026-11-24"), DayOfWeek.MONDAY))
    assertEquals(5, fluidCalendarRowOf(d("2026-11-30"), DayOfWeek.MONDAY))
    // Negli Stati Uniti la domenica 1 apre la prima riga.
    assertEquals(0, fluidCalendarRowOf(d("2026-11-01"), DayOfWeek.SUNDAY))
    assertEquals(DayOfWeek.MONDAY, FluidDateTimeMath.weekStart(Locale.ITALY))
    assertEquals(DayOfWeek.SUNDAY, FluidDateTimeMath.weekStart(Locale.US))
  }

  @Test fun theMonthChangeStepsWithoutAllocating() {
    val state = FluidCalendarState(YearMonth.of(2026, 11))
    state.widthPx = 1000f
    val counter = AllocationCounter()
    fun run(frames: Int): Long {
      state.pageMotion.springTo(0.3f, 1.5f, 1f, 1000f, FluidCalendarDefaults.PageResponseSeconds)
      var t = 1_000_000_000L
      val before = counter.read()
      for (i in 0 until frames) {
        t += 8_333_333L
        state.stepPage(t)
        // Una molla che si ferma prima della fine della serie riparte: si misura sempre un moto vero.
        if (!state.pageMotion.moving) state.pageMotion.springTo(0.3f, 1.5f, 1f, 1000f, FluidCalendarDefaults.PageResponseSeconds)
      }
      return counter.read() - before
    }
    run(2000)
    val short = run(1000)
    val long = run(5000)
    val perFrame = (long - short).toDouble() / 4000.0
    assertTrue("byte per fotogramma: $perFrame", perFrame < 1.0)
  }

  @Test fun theStripStepsWithoutAllocating() {
    val state = FluidCalendarState(YearMonth.of(2026, 11))
    val counter = AllocationCounter()
    fun run(frames: Int): Long {
      state.collapseMotion.springTo(0.2f, 0f, 1f, 400f, FluidCalendarDefaults.CollapseResponseSeconds)
      var t = 1_000_000_000L
      val before = counter.read()
      for (i in 0 until frames) {
        t += 8_333_333L
        state.stepCollapse(t)
        if (!state.collapseMotion.moving) state.collapseMotion.springTo(0.2f, 0f, 1f, 400f, FluidCalendarDefaults.CollapseResponseSeconds)
      }
      return counter.read() - before
    }
    run(2000)
    val short = run(1000)
    val long = run(5000)
    assertTrue((long - short).toDouble() / 4000.0 < 1.0)
  }

  @Test fun marksWriteOnlyTheDaysThatChange() {
    val marks = FluidCalendarMarks()
    val month = YearMonth.of(2026, 11)
    val first = mapOf(d("2026-11-03") to FluidCalendarMark(2), d("2026-11-25") to FluidCalendarMark(3))
    var writes = 0
    val observer = Snapshot.registerGlobalWriteObserver { writes++ }
    try {
      marks.setMonth(month, first)
      assertEquals(2, writes)
      writes = 0
      // Lo stesso mese che arriva di nuovo dalla rete: niente cambia, niente si scrive.
      marks.setMonth(month, first.toMap())
      assertEquals(0, writes)
      // Un giorno cambia, uno se ne va, uno arriva: tre scritture.
      marks.setMonth(month, mapOf(d("2026-11-03") to FluidCalendarMark(1), d("2026-11-28") to FluidCalendarMark(1)))
      assertEquals(3, writes)
    } finally {
      observer.dispose()
    }
    assertEquals(FluidCalendarMark(1), marks[d("2026-11-28")])
    assertEquals(null, marks[d("2026-11-25")])
    assertEquals("3 impegni", FluidCalendarLabels().markText(FluidCalendarMark(3)))
    assertEquals("1 event", FluidCalendarLabels.english().markText(FluidCalendarMark(1)))
    assertEquals("2 verifiche", FluidCalendarLabels().markText(FluidCalendarMark(2, description = "2 verifiche")))
  }

  @Test fun capsuleTexts() {
    val it = Locale.ITALY
    val en = Locale.US
    val words = FluidCalendarLabels()
    assertEquals("23 nov 2026", fluidDateCapsuleText(FluidDateSelection.Single(d("2026-11-23")), it, words))
    assertEquals("Nov 23, 2026", fluidDateCapsuleText(FluidDateSelection.Single(d("2026-11-23")), en, FluidCalendarLabels.english()))
    assertEquals("23–30 nov 2026", fluidDateCapsuleText(FluidDateSelection.Range(d("2026-11-23"), d("2026-11-30")), it, words))
    assertEquals("23 nov – 5 dic 2026", fluidDateCapsuleText(FluidDateSelection.Range(d("2026-11-23"), d("2026-12-05")), it, words))
    assertEquals("21 dic 2026 – 6 gen 2027", fluidDateCapsuleText(FluidDateSelection.Range(d("2026-12-21"), d("2027-01-06")), it, words))
    assertEquals("Nov 23 – 30, 2026", fluidDateRangeText(d("2026-11-23"), d("2026-11-30"), en))
    assertEquals("Nov 23 – Dec 5, 2026", fluidDateRangeText(d("2026-11-23"), d("2026-12-05"), en))
    assertEquals("Dal 23 nov 2026", fluidDateCapsuleText(FluidDateSelection.Range(d("2026-11-23"), null), it, words))
    assertEquals("3 giorni", fluidDateCapsuleText(FluidDateSelection.Multiple(setOf(d("2026-11-23"), d("2026-11-24"), d("2026-12-01"))), it, words))
    assertEquals("Nessuna data", fluidDateCapsuleText(FluidDateSelection.Single(null), it, words))
    val units = FluidDurationUnits()
    assertEquals("45 min", fluidDurationText(45L * 60L, false, units))
    assertEquals("1 h 30 min", fluidDurationText(90L * 60L, false, units))
    assertEquals("2 h", fluidDurationText(120L * 60L, false, units))
    assertEquals("1 min 5 s", fluidDurationText(65L, true, units))
  }

  @Test fun recurrencePresetsAndOrdinals() {
    assertEquals(FluidRecurrencePreset.Never, fluidRecurrencePresetOf(null))
    assertEquals(
      FluidRecurrencePreset.Biweekly,
      fluidRecurrencePresetOf(dev.antigravity.fluidengine.foundation.FluidRecurrenceRule(dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency.Weekly, 2)),
    )
    // Martedi' 24 novembre 2026 e' il quarto martedi' del mese e anche l'ultimo; il 17 no; il 29 dicembre e' il quinto.
    assertEquals(4, fluidWeekdayOrdinal(d("2026-11-24")))
    assertEquals(true, fluidIsLastWeekday(d("2026-11-24")))
    assertEquals(false, fluidIsLastWeekday(d("2026-11-17")))
    assertEquals(5, fluidWeekdayOrdinal(d("2026-12-29")))
    assertEquals(true, fluidIsLastWeekday(d("2026-12-29")))
  }

  /** I byte allocati finora da questo filo (HotSpot), per riflessione: la classpath dei test Android non ha `java.lang.management`. */
  private class AllocationCounter {
    private val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    private val method = Class.forName("com.sun.management.ThreadMXBean")
      .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private val args = arrayOf<Any>(Thread.currentThread().id)

    fun read(): Long = method.invoke(bean, *args) as Long
  }
}
