package dev.antigravity.fluidengine.sample.assistant

import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Gli extra di lancio della pagina «Assistente» (F3.2b) e il giro dei fondi: logica pura. */
class AssistantPureTest {

  private fun parse(vararg extras: Pair<String, String>): Pair<AssistantConfig, AssistantStart> {
    val map = extras.toMap()
    return AssistantLaunch.parse(inheritedDark = false, inherited = FluidGlassModel.Liquid) { map[it] }
  }

  @Test
  fun `senza extra la pagina parte di serie`() {
    val (config, start) = parse()
    assertEquals(AssistantConfig(), config)
    assertEquals(AssistantStart(), start)
  }

  @Test
  fun `ogni extra si legge, in italiano o in inglese`() {
    val (config, start) = parse(
      AssistantLaunch.ExtraFund to "Foto",
      AssistantLaunch.ExtraTheme to "dark",
      AssistantLaunch.ExtraModel to "classic",
      AssistantLaunch.ExtraCard to "lavoro",
      AssistantLaunch.ExtraAuto to "acceso",
    )
    assertEquals(AssistantConfig(dark = true, model = FluidGlassModel.Classic, card = AssistantCardState.Working), config)
    assertEquals(AssistantStart(fund = AssistantFund.Photo, autoMs = AssistantLaunch.DefaultAutoMs), start)
    assertEquals(AssistantFund.Dark, parse(AssistantLaunch.ExtraFund to "scuro").second.fund)
  }

  @Test
  fun `l'automatico prende i millisecondi, mai sotto mezzo secondo, e si spegne`() {
    assertEquals(4_000L, parse(AssistantLaunch.ExtraAuto to "4000ms").second.autoMs)
    assertEquals(500L, parse(AssistantLaunch.ExtraAuto to "10").second.autoMs)
    assertEquals(0L, parse(AssistantLaunch.ExtraAuto to "spento").second.autoMs)
    assertEquals(0L, parse(AssistantLaunch.ExtraAuto to "boh").second.autoMs)
  }

  @Test
  fun `un valore sconosciuto lascia quello di serie`() {
    val (config, start) = parse(AssistantLaunch.ExtraFund to "viola", AssistantLaunch.ExtraCard to "dorme")
    assertEquals(AssistantFund.Light, start.fund)
    assertEquals(AssistantCardState.Answer, config.card)
  }

  @Test
  fun `il fondo gira chiaro, scuro, foto e conta i passi`() {
    val live = AssistantLive()
    val seen = ArrayList<String>()
    repeat(4) { live.change { fund, step -> seen += "${fund.key}:$step" } }
    assertEquals(listOf("scuro:1", "foto:2", "chiaro:3", "scuro:4"), seen)
    assertEquals(AssistantFund.Dark, live.fund)
  }
}
