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

  // --- S1-bis: il lettore della sonda e la sequenza della compilazione --------------------------------

  @Test
  fun `il modo background non esiste piu, e chi lo chiede resta su main e lo legge nella chiave`() {
    val config = parse("measure.scene" to "probe", "measure.thread" to "background")!!
    assertEquals(MeasureThread.Main, config.thread)
    assertTrue(config.key, config.key.contains("thread=main"))
    assertNull(MeasureThread.entries.firstOrNull { it.key == "background" })
  }

  @Test
  fun `il lettore porta in chiave la fase, e le prove solo quando sono accese`() {
    val reader = parse("measure.scene" to "probe", "measure.thread" to "reader", "measure.period" to "100")!!
    assertEquals(MeasureThread.Reader, reader.thread)
    assertEquals(MeasurePhase.Post, reader.phase)
    assertEquals(
      "scene=probe backdrop=text motion=scroll speed=900 period=100 thread=reader probes=1 side=16 phase=post",
      reader.key,
    )
    val shared = parse(
      "measure.scene" to "probe",
      "measure.thread" to "reader-shared",
      "measure.probes" to "4",
      "measure.phase" to "frame",
      "measure.flip" to "1000",
      "measure.verify" to "on",
    )!!
    assertEquals(MeasureThread.ReaderShared, shared.thread)
    assertEquals(MeasurePhase.Frame, shared.phase)
    assertEquals(1000L, shared.flipMillis)
    assertTrue(shared.verify)
    assertTrue(shared.key, shared.key.endsWith("probes=4 side=16 phase=frame flip=1000 verify=on"))
    // Un cambio piu fitto di dieci al secondo non e una prova: resta spento.
    assertEquals(0L, parse("measure.scene" to "probe", "measure.flip" to "20")!!.flipMillis)
    // Per `toImageBitmap()` la fase non conta, e la chiave non la porta.
    assertFalse(parse("measure.scene" to "probe", "measure.phase" to "any")!!.key.contains("phase"))
  }

  @Test
  fun `le varianti di diagnosi del lettore stanno in chiave solo quando non sono quelle di serie`() {
    val plain = parse("measure.scene" to "probe", "measure.thread" to "reader")!!
    assertEquals(MeasureRoot.Layer, plain.root)
    assertEquals(MeasureVia.Renderer, plain.via)
    assertTrue("di serie la radice si ri-registra a ogni richiesta", plain.rerecord)
    assertFalse(plain.readerOpaque || plain.readerStart || plain.readerGpuUsage || plain.readerLight)
    val diagnosis = parse(
      "measure.scene" to "probe",
      "measure.thread" to "reader",
      "measure.root" to "direct",
      "measure.rerecord" to "off",
      "measure.via" to "lock-hw",
      "measure.opaque" to "on",
      "measure.start" to "on",
      "measure.usage" to "gpu",
      "measure.light0" to "on",
    )!!
    assertEquals(MeasureRoot.Direct, diagnosis.root)
    assertEquals(MeasureVia.LockHardware, diagnosis.via)
    assertFalse(diagnosis.rerecord)
    assertTrue(
      diagnosis.key,
      diagnosis.key.endsWith("phase=post root=direct rerecord=off via=lock-hw opaque=on start=on usage=gpu light0=on"),
    )
    // Senza lettore le varianti non contano, e la chiave non le porta.
    val main = parse("measure.scene" to "probe", "measure.root" to "solid", "measure.via" to "lock-sw")!!
    assertEquals("scene=probe backdrop=text motion=scroll speed=900 period=120 thread=main probes=1 side=16", main.key)
  }

  @Test
  fun `la sequenza della compilazione accetta solo nomi veri, e senza extra e quella di S1`() {
    assertEquals(
      listOf("body1", "light1", "body2", "light2", "body4", "light4", "body8", "light8", "edge"),
      MeasureConfig.defaults(MeasureScene.Compile).steps,
    )
    assertEquals(
      listOf("body1", "body8", "light1", "light8", "edge"),
      MeasureConfig.parseCompileSteps(" body1, body8 ,light1,light8,edge "),
    )
    assertEquals(listOf("edge", "edge2"), MeasureConfig.parseCompileSteps("edge+edge2"))
    assertNull(MeasureConfig.parseCompileSteps(""))
    assertNull("un nome che non esiste", MeasureConfig.parseCompileSteps("body1,body3"))
    assertNull("la stessa striscia due volte", MeasureConfig.parseCompileSteps("edge,edge"))
    assertNull("troppi passi", MeasureConfig.parseCompileSteps(List(13) { "body1" }.joinToString(",")))
    val custom = parse("measure.scene" to "compile", "measure.steps" to "light8,body8,body8")!!
    assertEquals("scene=compile backdrop=text steps=light8,body8,body8", custom.key)
    // Un refuso nella sequenza lascia quella di serie, e la chiave lo dice (niente `steps=`).
    assertEquals("scene=compile backdrop=text", parse("measure.scene" to "compile", "measure.steps" to "bod1")!!.key)
  }

  @Test
  fun `nel piano una luce va sul suo corpo se c e, altrimenti entra da sola`() {
    val s1 = compilePlan(MeasureConfig.DefaultCompileSteps)
    assertEquals(4, s1.elements.size)
    assertEquals(listOf(0, 2, 4, 6), s1.elements.map { it.bodyStep })
    assertEquals(listOf(1, 3, 5, 7), s1.elements.map { it.lightStep })
    assertEquals(8, s1.topStrip)
    assertEquals(-1, s1.bottomStrip)

    val plan = compilePlan(listOf("light8", "body8", "body8", "light8", "edge2", "light8", "light8"))
    assertEquals(4, plan.elements.size)
    // La prima luce non ha un corpo prima di lei: da sola.
    assertEquals(-1, plan.elements[0].bodyStep)
    assertEquals(0, plan.elements[0].lightStep)
    // Due corpi uguali sono due elementi; la luce va sull'ultimo che non ne ha, poi sull'altro.
    assertEquals(1, plan.elements[1].bodyStep)
    assertEquals(5, plan.elements[1].lightStep)
    assertEquals(2, plan.elements[2].bodyStep)
    assertEquals(3, plan.elements[2].lightStep)
    // Finiti i corpi liberi, un'altra luce entra da sola.
    assertEquals(-1, plan.elements[3].bodyStep)
    assertEquals(6, plan.elements[3].lightStep)
    assertEquals(-1, plan.topStrip)
    assertEquals(4, plan.bottomStrip)
  }

  @Test
  fun `il confronto fra due letture conta luminanza e canali, e pixel uguali danno zero`() {
    val argb = intArrayOf(0xFF102030.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
    val same = byteArrayOf(0x10, 0x20, 0x30, -1, -1, -1, 0, 0, 0)
    val equal = probeDifference(argb, same)
    assertEquals(0, equal.maxChannelDelta)
    assertEquals(0f, equal.meanChannelDelta, 0f)
    assertEquals(0f, equal.lumaDelta, 1e-6f)

    // Il verde del primo pixel sale di 3 livelli, il blu dell'ultimo di 6: 9 livelli su 9 canali.
    val shifted = byteArrayOf(0x10, 0x23, 0x30, -1, -1, -1, 0, 0, 6)
    val different = probeDifference(argb, shifted)
    assertEquals(6, different.maxChannelDelta)
    assertEquals(1f, different.meanChannelDelta, 1e-6f)
    assertEquals((0.7152f * 3f + 0.0722f * 6f) / 255f / 3f, different.lumaDelta, 1e-6f)

    // Con uno scarto si legge la seconda casella di un lettore condiviso.
    val twoSlots = ByteArray(9) + same
    assertEquals(0, probeDifference(argb, twoSlots, rgbOffset = 9).maxChannelDelta)
    assertEquals(-1, probeDifference(IntArray(0), same).maxChannelDelta)
  }

  @Test
  fun `la prova chiaro scuro dice dopo quanto la sonda vede il cambio, una volta sola`() {
    val tracker = FlipTracker(probes = 2, staleNanos = 25L)
    // Prima del primo cambio nessuna lettura c'entra.
    assertNull(tracker.onReading(0, 0.9f, 0L, 5L))
    assertEquals(0, tracker.mark(100L, toDark = true))
    // Chiesta prima del cambio e ancora chiara: legittima, non stantia.
    assertNull(tracker.onReading(0, 0.9f, 90L, 104L))
    // Chiesta poco dopo il cambio e ancora chiara: dentro il margine.
    assertNull(tracker.onReading(0, 0.9f, 110L, 118L))
    val seen = tracker.onReading(0, 0.05f, 120L, 131L)!!
    assertEquals(FlipTracker.Kind.Seen, seen.kind)
    assertEquals(31L, seen.seenNanos)
    assertEquals(20L, seen.waitNanos)
    assertEquals(11L, seen.pipeNanos)
    // La stessa sonda non lo vede due volte.
    assertNull(tracker.onReading(0, 0.05f, 140L, 150L))
    // L'altra sonda, chiesta ben dopo il cambio, legge ancora chiaro: stantia.
    val stale = tracker.onReading(1, 0.9f, 160L, 170L)!!
    assertEquals(FlipTracker.Kind.Stale, stale.kind)
    assertEquals(60L, stale.waitNanos)
    // Al cambio seguente la seconda sonda non aveva ancora visto il primo.
    assertEquals(1, tracker.mark(300L, toDark = false))
    assertEquals(FlipTracker.Kind.Seen, tracker.onReading(1, 0.8f, 310L, 320L)!!.kind)
    assertNull("una lettura senza numero non dice niente", tracker.onReading(0, Float.NaN, 310L, 320L))
    assertNull(tracker.onReading(7, 0.8f, 310L, 320L))
  }
}
