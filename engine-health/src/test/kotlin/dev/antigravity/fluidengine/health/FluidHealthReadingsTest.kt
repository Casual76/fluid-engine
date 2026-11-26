package dev.antigravity.fluidengine.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * F8.26b · «Lo gestisce il provider, noi sommiamo»: le card leggono i totali di Health Connect (le aggregazioni), e un
 * giorno senza dati resta «Nessun dato»; i buchi si dicono dai tratti con dati (rari) del provider. Il Health Connect finto
 * fa come quello vero: per l'attivita' e il sonno tiene l'app più in alto nell'ordine **dell'utente**, per gli altri tipi
 * mette insieme tutte le app.
 */
class FluidHealthReadingsTest {
  private val zone = ZoneId.of("Europe/Rome")
  private val day = LocalDate.of(2026, 11, 20)
  private fun at(h: Int, m: Int = 0, d: LocalDate = day): Instant = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant()

  private val phone = FluidHealthSource("com.example.phone", "Telefono", FluidHealthDeviceKind.Phone)
  private val watch = FluidHealthSource("com.example.watch", "Orologio", FluidHealthDeviceKind.Watch)
  private val sounds = FluidHealthSource("dev.pampa.sounds", "Pampa Sounds", FluidHealthDeviceKind.Phone)

  private var n = 0
  private fun steps(from: Instant, to: Instant, count: Int, source: FluidHealthSource) =
    FluidHealthInterval("s${n++}", FluidHealthMetric.Steps, from, to, count.toDouble(), source)

  private fun gateway(vararg order: FluidHealthSource, data: List<FluidHealthSample>) =
    FluidMemoryHealthGateway(sourcePriority = order.map { it.packageName }).apply { grantAll(); put(*data.toTypedArray()) }

  // --- Le letture dai totali ------------------------------------------------------------------------------------

  @Test
  fun `un totale che non c'e' e' Nessun dato, uno zero scritto e' zero, e la fonte e' quella dell'aggregazione`() {
    assertEquals(FluidHealthReading.NoData, FluidHealthReadings.reading(FluidHealthAggregate.empty(FluidHealthMetric.Steps, at(0), at(20))))
    val zero = FluidHealthReadings.reading(FluidHealthAggregate(FluidHealthMetric.Steps, at(0), at(20), total = 0.0, sources = listOf(phone)), gapRule = null)
    assertEquals(FluidHealthReading.Value(0.0, listOf(phone)), zero)
    val value = FluidHealthReadings.reading(FluidHealthAggregate(FluidHealthMetric.Steps, at(0), at(20), total = 7412.0, sources = listOf(watch, sounds)), gapRule = null)
    assertEquals(listOf(watch, sounds), (value as FluidHealthReading.Value).sources)
  }

  @Test
  fun `i buchi vengono dai tratti con dati del provider - il pomeriggio si dice, la notte e la mattina no`() {
    val buckets = (0 until 25).map { i -> // dalle 8:00 alle 14:15, un quarto d'ora per volta
      FluidHealthAggregate(FluidHealthMetric.Steps, at(8).plus(Duration.ofMinutes(15L * i)), at(8).plus(Duration.ofMinutes(15L * (i + 1))), total = 100.0)
    }
    val gaps = FluidHealthReadings.gaps(buckets, at(0), at(18, 30), zone, FluidHealthGapRule())
    assertEquals(listOf(FluidHealthGap(at(14, 15), at(18, 30))), gaps)
    // Un'ora senza passi alla scrivania non e' un buco.
    val desk = buckets.filterIndexed { i, _ -> i !in 8..11 }
    assertTrue(FluidHealthReadings.gaps(desk, at(0), at(14, 15), zone, FluidHealthGapRule()).isEmpty())
    // Dopo le 23 non c'e' giornata sveglia.
    val late = listOf(FluidHealthAggregate(FluidHealthMetric.Steps, at(22, 45), at(23), total = 10.0))
    assertTrue(FluidHealthReadings.gaps(late, at(0), at(23, 59), zone, FluidHealthGapRule()).isEmpty())
  }

  @Test
  fun `il battito viene dalle statistiche del provider, e senza dati non dice zero`() {
    val a = FluidHealthAggregate(FluidHealthMetric.HeartRate, at(0), at(20), min = 54.0, max = 131.0, average = 68.0, count = 512, sources = listOf(watch))
    val h = FluidHealthReadings.heart(a)!!
    assertEquals(54.0, h.min, 0.0)
    assertEquals(131.0, h.max, 0.0)
    assertEquals(512L, h.count)
    assertNull(FluidHealthReadings.heart(FluidHealthAggregate.empty(FluidHealthMetric.HeartRate, at(0), at(20))))
  }

  @Test
  fun `il giorno di oggi si ferma ad adesso, la notte va dalle 18 alle 18`() {
    assertEquals(at(0) to at(15), FluidHealthReadings.dayRange(day, zone, now = at(15)))
    assertEquals(at(0) to at(0, 0, day.plusDays(1)), FluidHealthReadings.dayRange(day, zone, now = at(9, 0, day.plusDays(3))))
    assertEquals(at(18, 0, day.minusDays(1)) to at(18), FluidHealthReadings.nightRange(day, zone))
  }

  @Test
  fun `il peso e' l'ultima misura, mai una media`() {
    val w = listOf(
      FluidHealthPoint("w1", FluidHealthMetric.Weight, at(8, 0, day.minusDays(3)), 72.9, phone),
      FluidHealthPoint("w2", FluidHealthMetric.Weight, at(8), 72.4, phone),
    )
    assertEquals(72.4, FluidHealthReadings.latest(w)!!.value, 0.0)
  }

  // --- «Niente doppioni»: i totali sono del provider -----------------------------------------------------------

  @Test
  fun `niente doppioni - la card prende il totale del provider, non la somma dei record che si sovrappongono`() = runBlocking {
    // Telefono e orologio contano le stesse ore: i record sommati a mano farebbero 7 600.
    val data = listOf(steps(at(9), at(12), 3100, phone), steps(at(9), at(12), 3400, watch), steps(at(12), at(13), 1100, phone))
    val g = gateway(watch, phone, data = data)
    val reading = g.day(FluidHealthMetric.Steps, day, zone, now = at(20), gapRule = null) as FluidHealthReading.Value
    assertEquals(4500.0, reading.value, 1e-9)
    assertTrue("la somma a mano dei record non e' la lettura", data.sumOf { it.value } != reading.value)
    assertEquals("letto con l'aggregazione", 1, g.aggregations)
    assertEquals("nessuna lettura dei record", 0, g.reads)
    // L'ordine lo decide la persona nelle impostazioni del provider, non l'engine: col telefono primo conta il telefono.
    val other = gateway(phone, watch, data = data).day(FluidHealthMetric.Steps, day, zone, now = at(20), gapRule = null) as FluidHealthReading.Value
    assertEquals(4200.0, other.value, 1e-9)
    assertEquals(listOf(phone), other.sources)
  }

  @Test
  fun `il finto fa come Health Connect - senza ordine scelto conta chi ha scritto prima`() = runBlocking {
    val data = listOf(steps(at(9), at(10), 900, watch), steps(at(9), at(10), 1000, phone))
    val r = gateway(data = data).day(FluidHealthMetric.Steps, day, zone, now = at(20), gapRule = null) as FluidHealthReading.Value
    assertEquals(900.0, r.value, 1e-9)
    assertEquals(listOf(watch), r.sources)
  }

  @Test
  fun `l'acqua di due app si somma - Health Connect toglie i doppioni solo per attivita' e sonno`() = runBlocking {
    val t = at(10)
    val data = listOf(
      FluidHealthInterval("a1", FluidHealthMetric.Hydration, t, t, 250.0, phone),
      FluidHealthInterval("a2", FluidHealthMetric.Hydration, t, t, 250.0, watch),
    )
    val r = gateway(watch, phone, data = data).day(FluidHealthMetric.Hydration, day, zone, now = at(20), gapRule = null) as FluidHealthReading.Value
    assertEquals(500.0, r.value, 1e-9)
    assertEquals(listOf(phone, watch), r.sources)
  }

  @Test
  fun `una notte scritta da Sounds e dall'orologio e' una sola, contata una volta dal provider`() = runBlocking {
    val data = listOf(
      FluidHealthSession("n1", FluidHealthMetric.Sleep, at(23, 0, day.minusDays(1)), at(7), sounds, "Timer della notte"),
      FluidHealthSession("n2", FluidHealthMetric.Sleep, at(23, 20, day.minusDays(1)), at(6, 50), watch),
    )
    val r = gateway(watch, sounds, data = data).night(day, zone) as FluidHealthReading.Value
    assertEquals("otto ore, non quindici", 480.0, r.value, 1e-6)
    assertEquals(listOf(watch, sounds).sortedBy { it.packageName }, r.sources)
  }

  @Test
  fun `la settimana ha il giorno vuoto come Nessun dato, i tratti sono radi come quelli del provider`() = runBlocking {
    val data = listOf(steps(at(9), at(10), 5000, phone), steps(at(9, 0, day.plusDays(2)), at(10, 0, day.plusDays(2)), 7000, phone))
    val g = gateway(data = data)
    val week = g.week(FluidHealthMetric.Steps, day, 3, zone)
    assertTrue(week[0] is FluidHealthReading.Value)
    assertEquals(FluidHealthReading.NoData, week[1])
    assertEquals(7000.0, (week[2] as FluidHealthReading.Value).value, 1e-9)
    val buckets = g.aggregateByDuration(FluidHealthMetric.Steps, at(0), at(23, 59), Duration.ofHours(1))
    assertEquals("solo l'ora con dati", 1, buckets.size)
    assertEquals(at(9), buckets[0].start)
  }

  @Test
  fun `senza permesso il totale non si legge`() = runBlocking {
    val g = FluidMemoryHealthGateway()
    try {
      g.aggregate(FluidHealthMetric.Steps, at(0), at(20))
      fail("doveva dire permesso negato")
    } catch (_: SecurityException) {
    }
  }

  @Test
  fun `nessun toString porta via un valore o un orario`() {
    val texts = listOf(
      steps(at(9), at(10), 4321, phone).toString(),
      FluidHealthAggregate(FluidHealthMetric.Steps, at(0), at(20), total = 4321.0).toString(),
      FluidHealthPoint("p", FluidHealthMetric.Weight, at(8), 87.65, phone).toString(),
      FluidHealthSession("x", FluidHealthMetric.Sleep, at(1), at(7), sounds).toString(),
      FluidHealthReading.Value(4321.0, listOf(phone)).toString(),
      FluidHealthRing("Passi", 4321.0, 10000.0, "4 321", "10 000").toString(),
      FluidHeartSummary(50.0, 4321.0, 60.0, 3, null, emptyList()).toString(),
    )
    for (text in texts) assertFalse(text, text.contains("4321") || text.contains("4 321") || text.contains("87.65") || text.contains("2026"))
  }
}
