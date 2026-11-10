package dev.antigravity.fluidengine.sample.measure

import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logica pura della pagina «Misure» (F0.6): i numeri che finiscono nel rapporto di una sessione sul
 * dispositivo devono essere giusti prima di arrivarci, e gli extra di lancio non devono poter far
 * partire una misura diversa da quella che la sua chiave dichiara.
 */
class MeasurePureTest {

  // --- Percentili e riassunti ---------------------------------------------------------------------

  @Test
  fun `il percentile e il rango piu vicino, mai un valore che non esiste`() {
    val sorted = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    assertEquals(5L, MeasureSummary.percentile(sorted, 0.50))
    assertEquals(9L, MeasureSummary.percentile(sorted, 0.90))
    assertEquals(10L, MeasureSummary.percentile(sorted, 0.99))
    assertEquals(1L, MeasureSummary.percentile(sorted, 0.0))
    assertEquals(10L, MeasureSummary.percentile(sorted, 1.0))
    assertEquals(0L, MeasureSummary.percentile(LongArray(0), 0.5))
    assertEquals(7L, MeasureSummary.percentile(longArrayOf(7), 0.99))
  }

  @Test
  fun `il riassunto guarda solo i primi valori e non riordina l array di chi chiama`() {
    val values = longArrayOf(30, 10, 20, 999)
    val summary = MeasureSummary.of(values, 3)
    assertEquals(3, summary.count)
    assertEquals(20L, summary.p50)
    assertEquals(30L, summary.p90)
    assertEquals(30L, summary.max)
    assertArrayEquals(longArrayOf(30, 10, 20, 999), values)
    assertEquals(0, MeasureSummary.of(values, 0).count)
    assertEquals(
      "p50=3.21 p90=4.10 p99=7.80 max=12.30",
      MeasureSummary(4, 3_210_000, 4_100_000, 7_800_000, 12_300_000).millis(),
    )
  }

  // --- Fotogrammi -----------------------------------------------------------------------------------

  /** Una riga per fotogramma: vsync, totale, bilancio. Il RenderThread vale meta del totale. */
  private fun frames(capacity: Int, vararg rows: LongArray): MeasureFrames {
    val frames = MeasureFrames(capacity)
    rows.forEach { frames.add(it[0], it[1], 0L, it[1] / 2, 0L, it[2]) }
    return frames
  }

  @Test
  fun `oltre il bilancio conta col bilancio del fotogramma, e col periodo dove manca`() {
    val frames = frames(
      4,
      longArrayOf(0, 9_000_000, 8_333_333),
      longArrayOf(1, 8_000_000, 8_333_333),
      longArrayOf(2, 17_000_000, 0),
      longArrayOf(3, 10_000_000, 0),
    )
    assertEquals(2, frames.overBudget(16_666_666L))
    assertEquals(4, frames.count)
    assertFalse(frames.overflowed)
    frames.add(4, 1, 1, 1, 1, 1)
    assertTrue("il quinto fotogramma non ha posto", frames.overflowed)
    assertEquals(4, frames.count)
  }

  @Test
  fun `attorno a un vsync si trovano il picco e la mediana, e fuori niente`() {
    val frames = frames(
      8,
      longArrayOf(100, 5, 0),
      longArrayOf(200, 40, 0),
      longArrayOf(300, 7, 0),
      longArrayOf(400, 6, 0),
      longArrayOf(500, 8, 0),
      longArrayOf(600, 9, 0),
    )
    assertEquals(40L, frames.maxBetween(frames.total, 150, 350))
    assertEquals(7L, frames.medianBetween(frames.total, 300, 500))
    assertEquals(-1L, frames.maxBetween(frames.total, 700, 900))
    assertEquals(-1L, frames.medianBetween(frames.total, 700, 900))
    assertEquals(20L, frames.maxBetween(frames.render, 150, 350))
  }

  @Test
  fun `la riga di una finestra porta chiave, conteggi e tempi`() {
    val frames = frames(4, longArrayOf(0, 4_000_000, 8_333_333), longArrayOf(1, 9_000_000, 8_333_333))
    frames.dropped = 3
    val line = measureWindowLine("scene=base", 2, 3, frames, 120f, "luma=0.5")
    assertTrue(
      line,
      line.startsWith(
        "RIS scene=base run=2/3 frames=2 hz=120 budget_ms=8.33 | total_ms p50=4.00 p90=9.00 p99=9.00 max=9.00 |",
      ),
    )
    assertTrue(line, line.contains("| render_ms p50=2.00 p90=4.50 "))
    assertTrue(line, line.endsWith("| over=1 over_pct=50.00 dropped=3 | luma=0.5"))
    val spread = measureSpreadLine("scene=base", listOf(frames, frames))
    assertTrue(
      spread,
      spread.startsWith("SPREAD scene=base runs=2 (min/med/max) total_p50=4.00/4.00/4.00 total_p90=9.00/9.00/9.00"),
    )
  }

  // --- Extra di lancio ------------------------------------------------------------------------------

  private fun parse(vararg extras: Pair<String, String>): MeasureConfig? {
    val map = extras.toMap()
    return MeasureConfig.parse { map[it] }
  }

  @Test
  fun `senza una scena riconosciuta non esiste una misura`() {
    assertNull(parse())
    assertNull(parse("measure.scene" to "nonesiste"))
    assertNull(parse("measure.glass" to "today"))
  }

  @Test
  fun `ogni scena si lancia col suo nome e parte dai suoi default`() {
    assertEquals(MeasureScene.entries.size, MeasureScene.entries.map { it.key }.toSet().size)
    MeasureScene.entries.forEach { scene ->
      val config = parse("measure.scene" to scene.key)
      assertNotNull(scene.key, config)
      assertEquals(MeasureConfig.defaults(scene), config)
      assertEquals(scene, config!!.scene)
    }
    // A cattura ferma: i soli uniform non si misurano sopra un fondale che scorre.
    assertEquals(MeasureMotion.Still, MeasureConfig.defaults(MeasureScene.Uniforms).motion)
    assertEquals(MeasureAnimation.Press, MeasureConfig.defaults(MeasureScene.Uniforms).animation)
    assertFalse(MeasureConfig.defaults(MeasureScene.Pieces).clip)
  }

  @Test
  fun `gli extra cambiano i default, e un refuso lascia quello che era`() {
    val config = parse(
      "measure.scene" to " Uniforms ",
      "measure.mount" to "two-composited",
      "measure.scale" to "0.5",
      "measure.element" to "panel",
      "measure.animate" to "appear",
      "measure.light" to "off",
      "measure.glass" to "vetro",
      "measure.pieces" to "99",
      "measure.runs" to "tre",
      "measure.tier" to "balanced",
    )!!
    assertEquals(MeasureScene.Uniforms, config.scene)
    assertEquals(MeasureMount.TwoComposited, config.mount)
    assertEquals(0.5f, config.scale, 0f)
    assertEquals(MeasureElement.Panel, config.element)
    assertEquals(MeasureAnimation.Appear, config.animation)
    assertFalse(config.light)
    assertEquals("un valore che non esiste lascia il default", MeasureGlass.Proto, config.glass)
    assertEquals("un numero fuori dal campo si riporta dentro", MeasureConfig.MaxPieces, config.pieces)
    assertEquals("un valore che non e un numero lascia il default", 3, config.runs)
    assertEquals(FluidPhysicsTier.Balanced, config.tier)
    assertNull(parse("measure.scene" to "physics", "measure.tier" to "auto")!!.tier)
    assertTrue(parse("measure.scene" to "pieces", "measure.variant" to "fit")!!.fitVariant)
  }

  @Test
  fun `la chiave porta solo quello che la scena legge, sempre nello stesso ordine`() {
    assertEquals(
      "scene=compare backdrop=text motion=scroll speed=900 element=all glass=proto mount=one light=on shadow=on " +
        "blur=12 dispersion=1 clip=on animate=none scale=1",
      MeasureConfig.defaults(MeasureScene.Compare).key,
    )
    assertEquals(
      "scene=compare backdrop=text motion=still element=all glass=today animate=none scale=1",
      parse("measure.scene" to "compare", "measure.glass" to "today", "measure.motion" to "still")!!.key,
    )
    assertEquals(
      "scene=pieces backdrop=text motion=scroll speed=900 pieces=4 variant=fit glass=proto mount=one light=on " +
        "shadow=on blur=12 dispersion=1 clip=off animate=none scale=0.50",
      parse(
        "measure.scene" to "pieces",
        "measure.pieces" to "4",
        "measure.variant" to "fit",
        "measure.scale" to "0.5",
      )!!.key,
    )
    assertEquals(
      "scene=edge backdrop=text motion=scroll speed=1800 strip=two-pass edges=both height=96 blur=14 scale=1",
      MeasureConfig.defaults(MeasureScene.Edge).key,
    )
    assertEquals(
      "scene=probe backdrop=text motion=scroll speed=900 period=120 thread=main probes=1 side=16",
      MeasureConfig.defaults(MeasureScene.Probe).key,
    )
    assertEquals("scene=compile backdrop=text", MeasureConfig.defaults(MeasureScene.Compile).key)
    assertEquals(
      "scene=physics backdrop=photo tier=auto journey=square",
      MeasureConfig.defaults(MeasureScene.Physics).key,
    )
    // Un extra che la scena non legge non cambia la chiave: due righe uguali sono la stessa misura.
    assertEquals(
      MeasureConfig.defaults(MeasureScene.Base).key,
      parse("measure.scene" to "base", "measure.mount" to "two", "measure.pieces" to "8")!!.key,
    )
  }
}
