package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.6 · I conti dei grafici da toccare e del quadrante, puri: quale colonna sta sotto il dito, cosa scrive la capsula,
 * quando suona una tacca, dove sta un valore nel quadrante e cosa legge TalkBack.
 */
class F8p6ChartTouchMathTest {

  private val italian = Locale.ITALY

  private fun plan(count: Int, left: Float = 40f, width: Float = 600f): FluidCartesianPlan {
    val scale = fluidNiceScale(dataMin = 0.0, dataMax = 10.0, targetTicks = 5, includeZero = true)
    return FluidCartesianPlan(scale, count, left, 10f, left + width, 370f, 420f, 40f, 16f, 1, 50f, 12f)
  }

  private fun model(values: List<List<Float?>>, labels: List<String>, series: List<String> = emptyList()) = FluidChartScrubModel(
    xLabels = labels,
    seriesLabels = series,
    values = values,
    colors = values.map { Color.Blue },
    format = { v -> fluidFormatNumber(v.toDouble(), italian) },
    delta = null,
    noValue = "nessun dato",
    labels = FluidDataLabels(),
    locale = italian,
    dots = true,
  )

  @Test
  fun `la colonna sotto il dito, e fuori dall'area l'estremo`() {
    val p = plan(count = 6)
    assertEquals(0, fluidChartScrubIndex(41f, p))
    assertEquals(0, fluidChartScrubIndex(139f, p))
    assertEquals(1, fluidChartScrubIndex(141f, p))
    assertEquals(5, fluidChartScrubIndex(639f, p))
    // Il dito che sborda resta sull'estremo, non perde lo scrubber.
    assertEquals(0, fluidChartScrubIndex(-80f, p))
    assertEquals(5, fluidChartScrubIndex(2000f, p))
    assertEquals(-1, fluidChartScrubIndex(100f, plan(count = 0)))
  }

  @Test
  fun `l'intervallo va dal piu' piccolo al piu' grande, un dito e' un punto`() {
    assertNull(fluidChartScrubRange(-1, -1))
    assertEquals(3..3, fluidChartScrubRange(3, -1))
    assertEquals(3..3, fluidChartScrubRange(3, 3))
    assertEquals(1..4, fluidChartScrubRange(4, 1))
  }

  @Test
  fun `la capsula scrive il valore e l'etichetta, con due dita la differenza e l'intervallo`() {
    val m = model(listOf(listOf(6f, 7.5f, null, 9f)), listOf("Set", "Ott", "Nov", "Dic"))
    assertEquals("7,5" to "Ott", m.text(1, -1))
    assertEquals("nessun dato" to "Nov", m.text(2, -1))
    // Da 6 a 9: +3, cioe' +50 %, come Borsa con due dita.
    assertEquals("+3 (+50%)" to "Set – Dic", m.text(3, 0))
    // Un buco a un estremo: niente differenza inventata.
    assertEquals("nessun dato" to "Ott – Nov", m.text(1, 2))
  }

  @Test
  fun `con piu' serie la capsula le nomina tutte`() {
    val m = model(listOf(listOf(1f, 2f), listOf(3f, 5f)), listOf("A", "B"), series = listOf("Foto", "Video"))
    assertEquals("Foto 1 · Video 3" to "A", m.text(0, -1))
    assertEquals("Foto +1 (+100%) · Video +2 (+67%)" to "A – B", m.text(0, 1))
  }

  @Test
  fun `la differenza ha il meno tipografico e la percentuale piccola ha un decimale`() {
    val f: (Float) -> String = { fluidFormatNumber(it.toDouble(), italian) }
    val labels = FluidDataLabels()
    assertEquals("−2 (−25%)", fluidChartDeltaText(8f, 6f, f, italian, labels))
    assertEquals("+0,5 (+5%)", fluidChartDeltaText(10f, 10.5f, f, italian, labels))
    assertEquals("+0,1 (+1%)", fluidChartDeltaText(10f, 10.1f, f, italian, labels))
    // Da zero non c'e' una percentuale.
    assertEquals("+4", fluidChartDeltaText(0f, 4f, f, italian, labels))
    assertEquals("0 (0%)", fluidChartDeltaText(5f, 5f, f, italian, labels))
  }

  @Test
  fun `il punto piu' alto della colonna, e il fondo per una colonna vuota`() {
    val p = plan(count = 2)
    val m = model(listOf(listOf(5f, null), listOf(8f, null)), listOf("A", "B"))
    assertEquals(p.yOf(8.0), m.top(p, 0), 0.01f)
    assertEquals(p.bottom, m.top(p, 1), 0.01f)
  }

  private class Played : FluidHaptics {
    override val engine: FluidHapticEngine = FluidHapticEngine.Composition
    val events = mutableListOf<FluidHapticEvent>()
    override fun play(event: FluidHapticEvent) {
      events += event
    }

    override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
  }

  @Test
  fun `una tacca per punto nuovo, con la rete dei 55 ms, e l'app avvisata a ogni punto e al rilascio`() {
    val haptics = Played()
    val seen = mutableListOf<IntRange?>()
    val scrubber = FluidChartScrubber(FluidChartScrubState()).apply {
      this.haptics = haptics
      onScrub = { seen += it }
    }
    scrubber.begin()
    scrubber.move(2, -1, 1_000)
    scrubber.move(2, -1, 1_016) // stesso punto: niente
    scrubber.move(3, -1, 1_100)
    scrubber.move(4, -1, 1_120) // entro 55 ms: niente tacca, ma il punto cambia
    scrubber.move(4, 1, 1_300) // il secondo dito
    scrubber.end()
    assertEquals(3, haptics.events.size)
    assertTrue(haptics.events.all { it == FluidHapticEvent.Tick })
    assertEquals(listOf(2..2, 3..3, 4..4, 1..4, null), seen)
    assertFalse(scrubber.state.active)
  }

  // --- Il quadrante ------------------------------------------------------------------------------------------------

  @Test
  fun `il valore nel suo intervallo, fermo agli estremi, e la soglia superata`() {
    val limits = listOf(FluidGaugeThreshold(20f, "Attenzione"), FluidGaugeThreshold(25f, "Limite"))
    val r = fluidGaugeReading(18f, 0f..100f, limits)
    assertEquals(0.18f, r.fraction, 1e-5f)
    assertEquals(-1, r.active)
    assertEquals(listOf(0.2f, 0.25f), r.thresholdFractions)
    assertFalse(r.clamped)
    assertEquals(0, fluidGaugeReading(22f, 0f..100f, limits).active)
    assertEquals(1, fluidGaugeReading(25f, 0f..100f, limits).active)
    // Fuori dall'intervallo: fermo all'estremo, e lo dice.
    val over = fluidGaugeReading(130f, 0f..100f, limits)
    assertEquals(1f, over.fraction, 0f)
    assertTrue(over.clamped)
    // Un intervallo vuoto non divide per zero; un valore non finito vale il minimo.
    assertEquals(0f, fluidGaugeReading(5f, 3f..3f).fraction, 0f)
    assertEquals(0f, fluidGaugeReading(Float.NaN, 0f..1f).fraction, 0f)
    assertTrue(fluidGaugeReading(Float.NaN, 0f..1f).clamped)
  }

  @Test
  fun `l'arco e' aperto in basso, l'anello parte da ore 12`() {
    val (start, sweep) = fluidGaugeArcAngles(FluidGaugeStyle.Arc)
    assertEquals(90f + FluidGaugeDefaults.ArcGapDegrees / 2f, start, 0f)
    assertEquals(360f - FluidGaugeDefaults.ArcGapDegrees, sweep, 0f)
    // Simmetrico rispetto al basso (90 gradi): l'inizio e la fine sono alla stessa distanza.
    assertEquals(start - 90f, 90f + 360f - (start + sweep), 0.001f)
    assertEquals(-90f to 360f, fluidGaugeArcAngles(FluidGaugeStyle.Ring))
  }

  @Test
  fun `TalkBack legge il valore, l'intervallo e le soglie, e quale e' superata`() {
    val limits = listOf(FluidGaugeThreshold(25f, "Limite"))
    val labels = FluidDataLabels()
    assertEquals(
      "18 %, da 0 a 100 %. Limite 25 %.",
      fluidGaugeDescription("18 %", "0", "100 %", limits, listOf("25 %"), fluidGaugeReading(18f, 0f..100f, limits), labels),
    )
    assertEquals(
      "27 %, da 0 a 100 %. oltre: Limite 25 %.",
      fluidGaugeDescription("27 %", "0", "100 %", limits, listOf("25 %"), fluidGaugeReading(27f, 0f..100f, limits), labels),
    )
    // Una soglia senza nome si chiama «soglia».
    val bare = listOf(FluidGaugeThreshold(6f))
    assertEquals(
      "5,5, da 0 a 10. soglia 6.",
      fluidGaugeDescription("5,5", "0", "10", bare, listOf("6"), fluidGaugeReading(5.5f, 0f..10f, bare), labels),
    )
  }

  @Test
  fun `le soglie senza colore sono l'avviso e, l'ultima, l'errore del tema`() {
    val scheme = lightColorScheme()
    val limits = listOf(FluidGaugeThreshold(25f), FluidGaugeThreshold(20f), FluidGaugeThreshold(5f, color = Color.Green))
    assertEquals(scheme.error, fluidGaugeThresholdColor(0, limits, scheme, dark = false))
    assertTrue(fluidGaugeThresholdColor(1, limits, scheme, dark = false) != scheme.error)
    assertEquals(Color.Green, fluidGaugeThresholdColor(2, limits, scheme, dark = false))
  }
}
