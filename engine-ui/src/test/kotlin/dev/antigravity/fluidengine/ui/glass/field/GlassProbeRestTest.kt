package dev.antigravity.fluidengine.ui.glass.field

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il riposo della sonda (F7.4, leva 7 del piano: «sonda sospesa quando la cattura non cambia; frequenza per
 * contenitore»): quante letture fa una sonda a riposo e in movimento, con la leva accesa e spenta.
 *
 * Il banco fa girare lo schedulatore e il riposo come il nodo ([GlassProbeNode.pump]): un passo per fotogramma (8 ms,
 * 120 Hz), prima la lettura che torna (e i suoi numeri al riposo), poi gli eventi del fotogramma, poi la decisione dello
 * schedulatore e il cancello [glassProbeRestGate]. Il nodo si sveglia con gli eventi e con un timer, qui a ogni
 * fotogramma: la lettura parte al primo fotogramma dopo il cancello, come col timer.
 */
class GlassProbeRestTest {

  @After
  fun levers() = GlassFieldLevers.resetForTesting()

  private class Bench(
    val restOn: Boolean = true,
    val latencyMillis: Long = 8L,
  ) {
    val scheduler = GlassProbeScheduler(
      minIntervalMillis = 120L,
      cadenceMillis = 500L,
      fastPxPerSecond = 1_000f,
      motionQuietMillis = 100L,
      maxBackoffMillis = 2_000L,
    )
    val rest = GlassProbeRest()
    val reads = mutableListOf<Long>()

    /** Ciò che la miniatura vede sotto il vetro a un istante. */
    var under: (Long) -> GlassBackdropStats = { Light }
    var unannounced = false
    private var returnsAt = Long.MAX_VALUE
    private var returning: GlassBackdropStats = Light

    /** Gli eventi di un fotogramma, come li vede il nodo. */
    inner class Events(private val now: Long) {
      fun revision() {
        scheduler.onRevision(now)
        rest.onRevision(now)
      }

      fun moved(px: Float = 4f) {
        scheduler.onGeometry(now, px)
        rest.onMoved()
      }

      /** Il contenuto di una sorgente scorre (lo scorrimento annunciato: `LayerBackdrop.scrollMotion`). */
      fun scrolling() = rest.onMoved()
    }

    fun frame(now: Long, events: Events.() -> Unit = {}) {
      if (now >= returnsAt) {
        returnsAt = Long.MAX_VALUE
        scheduler.onReadDone(now, GlassProbeOutcome.Read)
        rest.onReading(returning)
      }
      Events(now).events()
      scheduler.setUnannounced(unannounced)
      val decision = scheduler.next(now)
      if (decision is GlassProbeDecision.Read) {
        val notBefore = glassProbeRestGate(decision, rest, unannounced, restOn)
        if (now >= notBefore) {
          scheduler.onReadSent(now, decision.rerecord)
          rest.onReadSent(now)
          reads += now
          returning = under(now)
          returnsAt = now + latencyMillis
        }
      }
    }

    fun run(from: Long, until: Long, events: Events.(Long) -> Unit = {}) {
      var now = from
      while (now < until) {
        val at = now
        frame(at) { events(this, at) }
        now += FrameMillis
      }
    }

    fun born() = frame(0L) { moved(0f) }

    fun readsBetween(from: Long, until: Long): List<Long> = reads.filter { it in from until until }
  }

  // ---- A riposo e in movimento ---------------------------------------------------------------------------------

  @Test
  fun `a pagina ferma la sonda legge una volta, con la leva e senza`() {
    for (on in listOf(true, false)) {
      val bench = Bench(restOn = on)
      bench.born()
      bench.run(8L, 10_000L)
      assertEquals("leva ${if (on) "accesa" else "spenta"}", listOf(0L), bench.reads)
    }
  }

  @Test
  fun `una pagina che si ridisegna altrove, con le letture uguali, si legge un quarto delle volte`() {
    val before = Bench(restOn = false).apply {
      born()
      run(8L, 10_000L) { revision() }
    }
    val after = Bench(restOn = true).apply {
      born()
      run(8L, 10_000L) { revision() }
    }
    println("GlassProbeRest altrove: 10 s, ${before.reads.size} letture prima, ${after.reads.size} dopo; dopo: ${after.reads}")
    // Prima: una lettura ogni 120 ms, 84 in 10 s.
    assertTrue("prima: ${before.reads.size}", before.reads.size in 80..86)
    // Dopo: le prime tre alla cadenza di sempre (0, 120, 240: la terza è la seconda uguale), poi 240, poi 480 ms fino al tetto.
    assertEquals(listOf(0L, 120L, 240L, 480L, 960L, 1_440L), after.reads.take(6))
    assertTrue("dopo: ${after.reads.size}", after.reads.size in 20..25)
    // Mai più di [GlassProbeRestMaxMillis] fra due letture (più un fotogramma).
    for (i in 1 until after.reads.size) {
      assertTrue("${after.reads[i - 1]} → ${after.reads[i]}", after.reads[i] - after.reads[i - 1] <= GlassProbeRestMaxMillis + FrameMillis)
    }
    assertTrue("il riposo è a ${after.rest.steady} letture uguali", after.rest.steady >= GlassProbeRestAfter)
  }

  @Test
  fun `il contenuto che scorre sotto il vetro non lascia riposare, anche quando le letture tornano uguali`() {
    // Una lista che scorre su un tratto uniforme: letture tutte uguali, ma ciò che entra subito dopo non lo dicono.
    val before = Bench(restOn = false).apply {
      born()
      run(8L, 5_000L) { revision(); scrolling() }
    }
    val after = Bench(restOn = true).apply {
      born()
      run(8L, 5_000L) { revision(); scrolling() }
    }
    assertEquals(before.reads, after.reads)
    assertTrue("una ogni 120 ms: ${after.reads.size}", after.reads.size in 40..43)
  }

  @Test
  fun `il vetro che si sposta non riposa`() {
    val before = Bench(restOn = false).apply {
      born()
      run(8L, 3_000L) { moved(0.2f) }
    }
    val after = Bench(restOn = true).apply {
      born()
      run(8L, 3_000L) { moved(0.2f) }
    }
    assertEquals(before.reads, after.reads)
  }

  @Test
  fun `un fondo che cambia su una pagina ferma si legge subito, anche dopo un riposo`() {
    val bench = Bench(restOn = true)
    bench.born()
    // Due secondi di animazione altrove: la sonda riposa.
    bench.run(8L, 2_000L) { revision() }
    assertTrue(bench.rest.steady >= GlassProbeRestAfter)
    // Un secondo fermo, poi il fondo sotto il vetro passa a scuro e la sorgente avvisa una volta (F3.8a, F3.10).
    bench.run(2_000L, 3_000L)
    bench.under = { Dark }
    bench.frame(3_000L) { revision() }
    assertEquals("la revisione dopo il silenzio legge nello stesso fotogramma", 3_000L, bench.reads.last())
  }

  @Test
  fun `un cambio sotto il vetro mentre la pagina si anima altrove arriva al piu' dopo il tetto`() {
    val bench = Bench(restOn = true)
    bench.under = { at -> if (at < 5_000L) Light else Dark }
    bench.born()
    bench.run(8L, 8_000L) { revision() }
    val first = bench.reads.first { it >= 5_000L }
    assertTrue("prima lettura del fondo scuro a $first", first - 5_000L <= GlassProbeRestMaxMillis + FrameMillis)
    // Vista la differenza, la sonda torna alla cadenza di sempre per le due letture che servono a riposare di nuovo.
    val after = bench.readsBetween(first, 8_000L)
    assertEquals(120L, after[1] - after[0])
  }

  @Test
  fun `la cadenza fissa delle sorgenti che non avvisano non cambia`() {
    val before = Bench(restOn = false).apply {
      unannounced = true
      born()
      run(8L, 6_000L)
    }
    val after = Bench(restOn = true).apply {
      unannounced = true
      born()
      run(8L, 6_000L)
    }
    assertEquals(before.reads, after.reads)
    assertTrue("cadenza di 500 ms: ${after.reads}", after.reads.size >= 10)
  }

  @Test
  fun `con la leva spenta e' lo schedulatore di prima, lettura per lettura`() {
    val plain = GlassProbeScheduler(
      minIntervalMillis = 120L,
      cadenceMillis = 500L,
      fastPxPerSecond = 1_000f,
      motionQuietMillis = 100L,
      maxBackoffMillis = 2_000L,
    )
    val reads = mutableListOf<Long>()
    var returnsAt = Long.MAX_VALUE
    var now = 0L
    while (now < 6_000L) {
      if (now >= returnsAt) {
        returnsAt = Long.MAX_VALUE
        plain.onReadDone(now, GlassProbeOutcome.Read)
      }
      if (now == 0L) plain.onGeometry(now, 0f) else plain.onRevision(now)
      val decision = plain.next(now)
      if (decision is GlassProbeDecision.Read) {
        plain.onReadSent(now, decision.rerecord)
        reads += now
        returnsAt = now + 8L
      }
      now += FrameMillis
    }
    val off = Bench(restOn = false).apply {
      born()
      run(8L, 6_000L) { revision() }
    }
    assertEquals(reads, off.reads)
  }

  // ---- Le regole del riposo, da sole ---------------------------------------------------------------------------

  @Test
  fun `il riposo cresce di letture uguali e si chiude alla prima diversa`() {
    val rest = GlassProbeRest()
    assertEquals(120L, rest.interval())
    rest.onReading(Light)
    rest.onReading(Light)
    assertEquals(120L, rest.interval())
    rest.onReading(Light)
    assertEquals(240L, rest.interval())
    rest.onReading(Light)
    assertEquals(480L, rest.interval())
    repeat(20) { rest.onReading(Light) }
    assertEquals("il tetto", GlassProbeRestMaxMillis, rest.interval())
    rest.onReading(Dark)
    assertEquals(0, rest.steady)
    assertEquals(120L, rest.interval())
  }

  @Test
  fun `senza una lettura partita non c'e' niente da aspettare`() {
    val rest = GlassProbeRest()
    repeat(5) { rest.onReading(Light) }
    assertEquals(Long.MIN_VALUE, rest.notBefore())
    rest.onReadSent(1_000L)
    assertEquals(1_000L + rest.interval(), rest.notBefore())
    rest.onMoved()
    assertEquals(Long.MIN_VALUE, rest.notBefore())
  }

  @Test
  fun `una revisione dopo il silenzio sveglia, una nel mezzo di un'animazione no`() {
    val rest = GlassProbeRest()
    rest.onRevision(0L)
    repeat(4) { rest.onReading(Light) }
    rest.onRevision(8L)
    rest.onRevision(16L)
    assertTrue(rest.steady >= GlassProbeRestAfter)
    rest.onRevision(16L + GlassProbeRestFreshMillis + 1L)
    assertEquals(0, rest.steady)
  }

  @Test
  fun `il cancello lascia passare la geometria, le sorgenti che non avvisano e la leva spenta`() {
    val rest = GlassProbeRest()
    repeat(4) { rest.onReading(Light) }
    rest.onReadSent(100L)
    val revision = GlassProbeDecision.Read(rerecord = false)
    val geometry = GlassProbeDecision.Read(rerecord = true)
    assertTrue(glassProbeRestGate(revision, rest, unannounced = false, enabled = true) > 100L)
    assertEquals(Long.MIN_VALUE, glassProbeRestGate(geometry, rest, unannounced = false, enabled = true))
    assertEquals(Long.MIN_VALUE, glassProbeRestGate(revision, rest, unannounced = true, enabled = true))
    assertEquals(Long.MIN_VALUE, glassProbeRestGate(revision, rest, unannounced = false, enabled = false))
    assertEquals(Long.MIN_VALUE, glassProbeRestGate(revision, null, unannounced = false, enabled = true))
  }

  @Test
  fun `due letture uguali sono uguali entro mezzo livello, e non oltre`() {
    assertTrue(Light.sameAs(Light))
    assertTrue(Light.sameAs(Light.copy(luminance = Light.luminance + 0.4f / 255f)))
    assertFalse(Light.sameAs(Light.copy(luminance = Light.luminance + 2f / 255f)))
    assertFalse(Light.sameAs(Light.copy(red = Light.red - 0.01f)))
    assertTrue(Light.sameAs(Light.copy(variance = Light.variance * 1.0005f)))
    assertFalse(Light.sameAs(Light.copy(variance = Light.variance * 1.01f)))
    assertFalse("una riga di testo che entra sposta l'energia", Light.sameAs(Light.copy(textEnergy = 0.02f)))
    // La quota scura che non si conosce (NaN) è uguale a se stessa.
    val unknown = Light.copy(darkShare = Float.NaN)
    assertTrue(unknown.sameAs(unknown))
    assertFalse(unknown.sameAs(Light))
  }

  @Test
  fun `di serie la leva e' accesa`() {
    GlassFieldLevers.resetForTesting()
    assertTrue(GlassFieldLevers.probeRest)
  }

  private companion object {
    const val FrameMillis = 8L

    val Light = GlassBackdropStats(
      luminance = 0.92f,
      variance = 0.0021f,
      gradientEnergy = 0.0009f,
      red = 0.93f,
      green = 0.92f,
      blue = 0.90f,
      coverage = 1f,
      darkShare = 0f,
      textEnergy = 0.0007f,
    )

    val Dark = Light.copy(luminance = 0.08f, red = 0.07f, green = 0.08f, blue = 0.09f, darkShare = 1f)
  }
}
