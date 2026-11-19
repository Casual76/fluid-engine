package dev.antigravity.fluidengine.sample.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La parte pura degli scenari fissi (F7.1): che si lancino col loro nome, che i tempi siano quelli scritti, che le prove
 * di F1B abbiano la cadenza di allora e che le righe in logcat siano quelle che `tools/qa/perf-glass.sh` e
 * `tools/qa/perf-summary.py` cercano.
 */
class MeasureScenarioTest {

  private fun parse(vararg pairs: Pair<String, String>): ScenarioConfig? {
    val extras = pairs.toMap()
    return ScenarioConfig.parse { extras[it] }
  }

  @Test
  fun `senza uno scenario riconosciuto non parte niente`() {
    assertNull(parse())
    assertNull(parse("measure.scenario" to "scorrimento"))
    assertNull(parse("measure.scene" to "base"))
  }

  @Test
  fun `ogni scenario si lancia col suo nome e parte dai suoi default`() {
    MeasureScenario.entries.forEach { scenario ->
      val config = parse("measure.scenario" to scenario.key.uppercase())
      assertNotNull(scenario.key, config)
      assertEquals(scenario, config!!.scenario)
      assertEquals(scenario.cycles, config.cycles)
      assertEquals(ScenarioConfig.DefaultWarmupMillis, config.warmupMillis)
      assertEquals(ScenarioConfig.DefaultLeadMillis, config.leadMillis)
      assertTrue(config.hud)
    }
  }

  @Test
  fun `gli extra cambiano cicli e attese, e un refuso lascia quello che era`() {
    val config = parse(
      "measure.scenario" to "menu-tasto",
      "measure.cycles" to "5",
      "measure.warmup" to "4,5",
      "measure.lead" to "2",
      "measure.hud" to "off",
    )!!
    assertEquals(5, config.cycles)
    assertEquals(4_500L, config.warmupMillis)
    assertEquals(2_000L, config.leadMillis)
    assertFalse(config.hud)
    val clamped = parse("measure.scenario" to "menu-tasto", "measure.cycles" to "99", "measure.lead" to "0")!!
    assertEquals(ScenarioConfig.MaxCycles, clamped.cycles)
    // Meno di 200 ms fra PRONTO e il primo viaggio e lo script non farebbe in tempo ad azzerare gfxinfo.
    assertEquals(200L, clamped.leadMillis)
    val typo = parse("measure.scenario" to "menu-tasto", "measure.cycles" to "tre", "measure.hud" to "forse")!!
    assertEquals(MeasureScenario.MenuButton.cycles, typo.cycles)
    assertTrue(typo.hud)
  }

  @Test
  fun `la corsa ripete i tratti di ogni ciclo nello stesso ordine`() {
    val config = ScenarioConfig(MeasureScenario.MenuButton, cycles = 2, leadMillis = 1_000L)
    val timeline = config.timeline()
    assertEquals(listOf("1/2 apre", "1/2 chiude", "2/2 apre", "2/2 chiude"), timeline.map { "${it.cycleLabel} ${it.phase.name}" })
    assertEquals(1_000L + 2 * (900 + 1_200 + 900 + 1_200), config.durationMillis())
  }

  @Test
  fun `in un ciclo ogni tratto ha un nome suo`() {
    MeasureScenario.entries.forEach { scenario ->
      val names = scenario.phases.map { it.name }
      assertEquals(scenario.key, names.size, names.toSet().size)
      assertTrue(scenario.key, scenario.phases.all { it.journeyMillis > 0 && it.pauseMillis >= 0 })
      assertTrue(scenario.key, scenario.cycles in 1..ScenarioConfig.MaxCycles)
    }
  }

  @Test
  fun `dove lo script legge nel mezzo, ogni viaggio sta nel dump e la quiete lascia il tempo di leggere`() {
    // Il dump di gfxinfo tiene 120 fotogrammi, un secondo a 120 Hz: un viaggio più lungo ha una SONDA prima della fine.
    // E la lettura dopo FINE (un giro di adb, qualche centinaio di millisecondi) deve finire prima del viaggio dopo.
    MeasureScenario.entries.filter { it.probes }.forEach { scenario ->
      scenario.phases.forEach { phase ->
        val probe = phase.probeMillis
        assertTrue("${scenario.key}/${phase.name}", phase.journeyMillis <= 1_000L || (probe != null && probe < phase.journeyMillis))
        assertTrue("${scenario.key}/${phase.name}", phase.pauseMillis >= 1_000L)
      }
    }
  }

  @Test
  fun `le prove di F1B hanno la cadenza degli script di allora e nessuna lettura nel mezzo`() {
    // moto/f1b/partenza/strumenti/lotto-gfx-cfh.sh: (c) Menù, Pop-up, Menù, Tasti ogni 1,6 s per due giri; (f) tenuta
    // 800 ms + 1,4 s, poi chiusura + 1,8 s, tre volte; (h) tocco + 1,9 s, Indietro + 1,9 s, tre volte.
    fun cadence(scenario: MeasureScenario) = scenario.phases.map { it.journeyMillis + it.pauseMillis }
    assertEquals(listOf(1_600L, 1_600L, 1_600L, 1_600L), cadence(MeasureScenario.F1bGroupMenu))
    assertEquals(2, MeasureScenario.F1bGroupMenu.cycles)
    assertEquals(listOf(2_200L, 1_800L), cadence(MeasureScenario.F1bContext))
    assertEquals(3, MeasureScenario.F1bContext.cycles)
    assertEquals(listOf(1_900L, 1_900L), cadence(MeasureScenario.F1bCard))
    assertEquals(3, MeasureScenario.F1bCard.cycles)
    listOf(MeasureScenario.F1bGroupMenu, MeasureScenario.F1bContext, MeasureScenario.F1bCard).forEach {
      assertFalse(it.key, it.probes)
    }
    assertTrue(MeasureScenario.entries.filter { !it.key.startsWith("f1b-") }.all { it.probes })
  }

  /** La stessa espressione di `_MARKER_RE` e `_FIELD_RE` di `tools/qa/perf-summary.py`. */
  private val marker = Regex("""SCENARIO (INIZIO|FINE|SONDA|PRONTO|CHIUSO|ERRORE)\b(.*)""")
  private val field = Regex("""(\w+)=(\S+)""")

  private fun fields(line: String): Pair<String, Map<String, String>> {
    val match = requireNotNull(marker.find(line)) { line }
    return match.groupValues[1] to field.findAll(match.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
  }

  @Test
  fun `le righe portano quello che lo script e il riassunto leggono`() {
    val config = ScenarioConfig(MeasureScenario.F1bGroupMenu)
    val step = config.timeline()[5]
    val (kind, ready) = fields(ScenarioLines.ready(config, 123L))
    assertEquals("PRONTO", kind)
    assertEquals("f1b-c", ready["id"])
    assertEquals("no", ready["sondaggi"])
    assertEquals("menu,popup,menu-2,tasti", ready["fasi"])
    assertEquals("123", ready["t_ns"])
    val (startKind, start) = fields(ScenarioLines.start(config, step, 456L))
    assertEquals("INIZIO", startKind)
    assertEquals("2/2", start["ciclo"])
    assertEquals("popup", start["fase"])
    assertEquals("456", start["t_ns"])
    assertEquals("SONDA", fields(ScenarioLines.probe(config, step, 1L)).first)
    val (endKind, end) = fields(ScenarioLines.end(config, step, 789L, note = "riaperto"))
    assertEquals("FINE", endKind)
    assertEquals("789", end["t_ns"])
    assertEquals("riaperto", end["nota"])
    assertEquals("CHIUSO", fields(ScenarioLines.closed(config, 9L, 10L)).first)
    val (errorKind, error) = fields(ScenarioLines.error(config, "scena non pronta"))
    assertEquals("ERRORE", errorKind)
    assertEquals("scena_non_pronta", error["motivo"])
    // Lo script cerca queste parole con grep: devono restare all'inizio della riga, dopo «SCENARIO ».
    assertTrue(ScenarioLines.ready(config, 0L).startsWith("SCENARIO PRONTO "))
    assertTrue(ScenarioLines.ready(ScenarioConfig(MeasureScenario.MenuButton), 0L).contains("sondaggi=si"))
  }

  @Test
  fun `lo script conosce gli stessi scenari, nello stesso ordine`() {
    // I test girano dalla cartella del modulo (`sample/`).
    val script = File("../tools/qa/perf-glass.sh")
    assertTrue("manca ${script.path}", script.exists())
    val line = script.readLines().map { it.trimEnd('\r') }.first { it.startsWith("ALL_SCENARIOS=") }
    val keys = line.substringAfter('"').substringBefore('"').split(',')
    assertEquals(MeasureScenario.entries.map { it.key }, keys)
  }

  @Test
  fun `le forme del palco di F1B sono quelle della demo del Playground`() {
    val stage = androidx.compose.ui.unit.IntSize(1000, 800)
    val buttons = f1bStageForm(F1bStagePhase.Buttons, stage, groupRadiusPx = 40f, sheetRadiusPx = 60f)
    val menu = f1bStageForm(F1bStagePhase.Menu, stage, groupRadiusPx = 40f, sheetRadiusPx = 60f)
    val popup = f1bStageForm(F1bStagePhase.Popup, stage, groupRadiusPx = 40f, sheetRadiusPx = 60f)
    // Due tasti larghi un terzo del palco, il menu al 58%, il pop-up all'84%: `PracticalDemos.demoFormFor`.
    assertEquals(340f * 2 + 70f, buttons.frame.width, 0.5f)
    assertEquals(580f, menu.frame.width, 0.5f)
    assertEquals(840f, popup.frame.width, 0.5f)
    assertEquals(576f, popup.frame.height, 0.5f)
  }
}
