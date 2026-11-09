package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matematica dei grafici, senza uno schermo: scale e tacche, ripartizione e «Altro», percentuali a 100, numeri nella
 * lingua, angoli e bersagli, barre, buchi, curve monotone, contrasto dei colori di serie e testo generato.
 */
class FluidChartMathTest {

  private val itl = Locale.ITALIAN
  private val en = Locale.US

  private fun entry(label: String, value: Float, color: Color = Color.Unspecified) = FluidChartEntry(label, value, color)

  private fun ready(layout: FluidDonutLayout) = layout as FluidDonutLayout.Ready

  // --- Scale ----------------------------------------------------------------------------------------------

  @Test
  fun `a nice scale starts at zero and ends on a round number above the data`() {
    val scale = fluidNiceScale(0.0, 1240.0, targetTicks = 5)
    assertEquals(0.0, scale.min, 1e-9)
    assertEquals(1500.0, scale.max, 1e-9)
    assertEquals(500.0, scale.step, 1e-9)
    assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0), scale.ticks)
  }

  @Test
  fun `all zero data gives a unit scale and never divides by zero`() {
    val scale = fluidNiceScale(0.0, 0.0)
    assertEquals(0.0, scale.min, 0.0)
    assertEquals(1.0, scale.max, 0.0)
    assertTrue(scale.ticks.size >= 2)
    assertEquals(0f, scale.fraction(0.0), 0f)
  }

  @Test
  fun `a flat non-zero line without zero opens a margin around it, and with zero keeps the zero`() {
    val open = fluidNiceScale(7.0, 7.0, includeZero = false)
    assertTrue(open.min < 7.0 && open.max > 7.0)
    val closed = fluidNiceScale(7.0, 7.0, includeZero = true)
    assertEquals(0.0, closed.min, 0.0)
    assertTrue(closed.max >= 7.0)
  }

  @Test
  fun `negative data keeps zero on the axis and covers the minimum`() {
    val scale = fluidNiceScale(-37.0, 120.0)
    assertTrue(scale.min <= -37.0)
    assertTrue(scale.max >= 120.0)
    assertTrue(scale.ticks.any { abs(it) < 1e-9 })
  }

  @Test
  fun `huge and degenerate values still give a finite scale`() {
    val huge = fluidNiceScale(0.0, 1e30)
    assertTrue(huge.max >= 1e30 * (1 - 1e-9) && huge.max.isFinite())
    assertTrue(huge.ticks.size in 2..9)
    val floatMax = fluidNiceScale(0.0, Float.MAX_VALUE.toDouble())
    assertTrue(floatMax.max.isFinite())
    val nan = fluidNiceScale(Double.NaN, Double.POSITIVE_INFINITY)
    assertTrue(nan.min.isFinite() && nan.max.isFinite() && nan.max > nan.min)
    // Estremi scambiati: li rimette in ordine.
    val swapped = fluidNiceScale(100.0, 0.0)
    assertEquals(0.0, swapped.min, 0.0)
    assertTrue(swapped.max >= 100.0)
  }

  @Test
  fun `a scale always covers the data and steps on one, two or five times a power of ten - five hundred random ranges`() {
    val random = Random(7)
    repeat(500) {
      val a = (random.nextDouble() - 0.3) * 10.0.pow(random.nextInt(-3, 9))
      val b = a + random.nextDouble() * 10.0.pow(random.nextInt(-3, 9))
      val lo = min(a, b)
      val hi = max(a, b)
      val scale = fluidNiceScale(lo, hi, targetTicks = 5, includeZero = random.nextBoolean())
      val tol = (scale.step * 1e-6)
      assertTrue("$lo..$hi -> $scale", scale.min <= lo + tol && scale.max >= hi - tol)
      assertTrue("$scale", scale.ticks.size in 2..8)
      val mantissa = scale.step / 10.0.pow(floor(log10(scale.step) + 1e-9))
      assertTrue("passo $scale mantissa $mantissa", listOf(1.0, 2.0, 5.0, 10.0).any { abs(it - mantissa) < 1e-6 })
      scale.ticks.zipWithNext().forEach { (x, y) -> assertEquals(scale.step, y - x, scale.step * 1e-6 + max(abs(scale.min), abs(scale.max)) * 1e-12) }
    }
  }

  @Test
  fun `a fixed scale ends exactly on the declared denominator`() {
    val scale = fluidFixedScale(0.0, 1240.0)
    assertEquals(1240.0, scale.max, 0.0)
    assertEquals(1240.0, scale.ticks.last(), 0.0)
    assertEquals(0.0, scale.ticks.first(), 0.0)
    assertTrue(scale.ticks.zipWithNext().all { (a, b) -> b > a })
    // L'ultima tacca tonda troppo vicina alla fine cede il posto.
    val close = fluidFixedScale(0.0, 1010.0)
    assertEquals(1010.0, close.ticks.last(), 0.0)
    assertFalse(close.ticks.contains(1000.0))
    // Degenere: scala unitaria.
    assertEquals(1.0, fluidFixedScale(0.0, 0.0).max, 0.0)
  }

  @Test
  fun `the tick budget shrinks as the text grows and never goes under two or over six`() {
    assertEquals(6, fluidTickBudget(600f, 30f))
    assertTrue(fluidTickBudget(400f, 30f) in 2..6)
    assertTrue(fluidTickBudget(400f, 60f) < fluidTickBudget(400f, 30f))
    assertEquals(2, fluidTickBudget(10f, 100f))
  }

  @Test
  fun `the label stride is the smallest that fits`() {
    assertEquals(1, fluidLabelStride(10) { true })
    assertEquals(3, fluidLabelStride(10) { it >= 3 })
    // Se nemmeno una ogni due... fino all'ultima: ne resta una sola.
    assertEquals(10, fluidLabelStride(10) { false })
    assertEquals(1, fluidLabelStride(1) { false })
    assertEquals(1, fluidLabelStride(0) { false })
  }

  // --- Ripartizione -----------------------------------------------------------------------------------------

  @Test
  fun `a breakdown gives fractions that sum to one in the callers order`() {
    val layout = ready(fluidDonutLayout(listOf(entry("A", 620f), entry("B", 210f), entry("C", 170f))))
    assertEquals(listOf("A", "B", "C"), layout.slices.map { it.label })
    assertEquals(1.0, layout.slices.sumOf { it.fraction.toDouble() }, 1e-6)
    assertEquals(0.62f, layout.slices[0].fraction, 1e-6f)
    assertEquals(1000f, layout.total, 0f)
    assertFalse(layout.normalized)
  }

  @Test
  fun `empty and all-zero are the empty state, not an error and not a division by zero`() {
    assertEquals(FluidDonutLayout.Empty, fluidDonutLayout(emptyList()))
    assertEquals(FluidDonutLayout.Empty, fluidDonutLayout(listOf(entry("A", 0f), entry("B", 0f))))
  }

  @Test
  fun `negative and non finite values are invalid and say which one`() {
    val negative = fluidDonutLayout(listOf(entry("A", 5f), entry("B", -1f))) as FluidDonutLayout.Invalid
    assertEquals(FluidDonutProblem.Negative, negative.problem)
    assertEquals(1, negative.index)
    val nan = fluidDonutLayout(listOf(entry("A", Float.NaN))) as FluidDonutLayout.Invalid
    assertEquals(FluidDonutProblem.NotFinite, nan.problem)
    val inf = fluidDonutLayout(listOf(entry("A", 1f), entry("B", Float.POSITIVE_INFINITY), entry("C", 2f))) as FluidDonutLayout.Invalid
    assertEquals(1, inf.index)
  }

  @Test
  fun `a declared whole that the values do not reach is refused unless normalising is explicit`() {
    val entries = listOf(entry("A", 60f), entry("B", 30f))
    val strict = fluidDonutLayout(entries, whole = 100f) as FluidDonutLayout.Invalid
    assertEquals(FluidDonutProblem.SumMismatch, strict.problem)
    assertEquals(100f, strict.declared, 0f)
    assertEquals(90f, strict.actual, 0f)
    val normalized = ready(fluidDonutLayout(entries, whole = 100f, sumPolicy = FluidChartSumPolicy.Normalize))
    assertTrue(normalized.normalized)
    assertEquals(1.0, normalized.slices.sumOf { it.fraction.toDouble() }, 1e-6)
    assertEquals(2f / 3f, normalized.slices[0].fraction, 1e-5f)
    // Entro lo 0,5% torna.
    val close = ready(fluidDonutLayout(listOf(entry("A", 60f), entry("B", 39.7f)), whole = 100f))
    assertFalse(close.normalized)
    // Un totale dichiarato assurdo e' un dato sbagliato anche se si normalizza.
    assertTrue(fluidDonutLayout(entries, whole = 0f, sumPolicy = FluidChartSumPolicy.Normalize) is FluidDonutLayout.Invalid)
    assertTrue(fluidDonutLayout(entries, whole = Float.NaN) is FluidDonutLayout.Invalid)
  }

  @Test
  fun `tiny slices fold into Other when there are at least two, and a lone tiny one stays`() {
    val two = ready(
      fluidDonutLayout(
        listOf(entry("A", 900f), entry("B", 60f), entry("C", 25f), entry("D", 15f)),
        otherLabel = "Altro",
      ),
    )
    assertEquals(listOf("A", "B", "Altro"), two.slices.map { it.label })
    val other = two.slices.last()
    assertTrue(other.isOther)
    assertEquals(listOf(2, 3), other.sourceIndices)
    assertEquals(40f, other.value, 0f)
    assertEquals(0.04f, other.fraction, 1e-6f)
    assertEquals(1.0, two.slices.sumOf { it.fraction.toDouble() }, 1e-6)

    val one = ready(fluidDonutLayout(listOf(entry("A", 900f), entry("B", 80f), entry("C", 20f))))
    assertEquals(listOf("A", "B", "C"), one.slices.map { it.label })
    assertFalse(one.slices.any { it.isOther })
  }

  @Test
  fun `more than the maximum keeps the largest and folds the rest, even if they are not tiny`() {
    val entries = (1..12).map { entry("v$it", 10f + it) }
    val layout = ready(fluidDonutLayout(entries, groupBelow = 0f, maxSlices = 5))
    assertEquals(5, layout.slices.size)
    assertTrue(layout.slices.last().isOther)
    // Restano le quattro maggiori: v12, v11, v10, v9, nell'ordine dei dati.
    assertEquals(listOf("v9", "v10", "v11", "v12", "Altro"), layout.slices.map { it.label })
    assertEquals((1..8).toList().map { it - 1 }, layout.slices.last().sourceIndices)
    assertEquals(1.0, layout.slices.sumOf { it.fraction.toDouble() }, 1e-6)
  }

  @Test
  fun `a single slice is the whole ring and a zero entry keeps its place`() {
    val single = ready(fluidDonutLayout(listOf(entry("Solo", 5f))))
    assertEquals(1f, single.slices.single().fraction, 0f)
    val zeros = ready(fluidDonutLayout(listOf(entry("A", 10f), entry("B", 0f), entry("C", 10f)), groupBelow = 0f))
    assertEquals(3, zeros.slices.size)
    assertEquals(0f, zeros.slices[1].fraction, 0f)
  }

  @Test
  fun `a huge value among tiny ones does not overflow the sum`() {
    val layout = ready(fluidDonutLayout(listOf(entry("big", Float.MAX_VALUE), entry("small", 1f)), groupBelow = 0f))
    assertEquals(1.0, layout.slices.sumOf { it.fraction.toDouble() }, 1e-6)
    assertTrue(layout.slices[0].fraction > 0.999f)
  }

  // --- Percentuali -------------------------------------------------------------------------------------------

  @Test
  fun `rounded percentages always sum to exactly one hundred`() {
    assertEquals(listOf(34.0, 33.0, 33.0), fluidRoundedPercents(listOf(1f / 3, 1f / 3, 1f / 3)))
    assertEquals(listOf(33.0, 33.0, 34.0), fluidRoundedPercents(listOf(0.333f, 0.333f, 0.334f)))
    val random = Random(11)
    repeat(300) {
      val n = random.nextInt(1, 12)
      val raw = List(n) { random.nextFloat() + 0.01f }
      val total = raw.sum()
      for (decimals in 0..1) {
        val percents = fluidRoundedPercents(raw.map { it / total }, decimals)
        assertEquals("n=$n d=$decimals $percents", 100.0, percents.sum(), 1e-6)
        assertTrue(percents.all { it >= 0.0 })
      }
    }
  }

  @Test
  fun `percentages degenerate cases`() {
    assertEquals(emptyList<Double>(), fluidRoundedPercents(emptyList()))
    assertEquals(listOf(0.0, 0.0), fluidRoundedPercents(listOf(0f, 0f)))
    assertEquals(listOf(100.0), fluidRoundedPercents(listOf(1f)))
    assertEquals(listOf(12.5, 87.5), fluidRoundedPercents(listOf(0.125f, 0.875f), decimals = 1))
  }

  @Test
  fun `a tiny share is written as under one percent, never as zero`() {
    val texts = fluidPercentTexts(listOf(0.997f, 0.003f), en)
    assertEquals("100%", texts[0])
    assertEquals("<1%", texts[1])
    assertEquals("0%", fluidPercentTexts(listOf(0f, 1f), en)[0])
  }

  @Test
  fun `percentages are written in the language of the locale`() {
    assertEquals("62%", fluidPercentTexts(listOf(0.62f, 0.38f), en)[0])
    assertEquals("62%", fluidPercentTexts(listOf(0.62f, 0.38f), itl)[0])
    val german = fluidPercentTexts(listOf(0.62f, 0.38f), Locale.GERMANY)[0]
    assertTrue(german, german.startsWith("62") && german.endsWith("%") && german.length == 4)
  }

  // --- Numeri nella lingua ----------------------------------------------------------------------------------

  @Test
  fun `numbers use the separators of the language and few digits`() {
    assertEquals("620", fluidFormatNumber(620.0, itl))
    assertEquals("12,3", fluidFormatNumber(12.34, itl))
    assertEquals("12.3", fluidFormatNumber(12.34, en))
    assertEquals("3,14", fluidFormatNumber(3.14159, itl))
    assertEquals("-5", fluidFormatNumber(-5.0, en))
    assertEquals("0", fluidFormatNumber(-0.0, en))
    assertEquals("12.345", fluidFormatNumber(12345.0, itl))
    assertEquals("12,345", fluidFormatNumber(12345.0, en))
  }

  @Test
  fun `large numbers go compact and enormous ones scientific`() {
    assertEquals("3,4M", fluidFormatNumber(3_400_000.0, itl))
    assertEquals("3.4M", fluidFormatNumber(3_400_000.0, en))
    assertEquals("12k", fluidFormatNumber(12_000.0, en, compactFrom = 10_000.0))
    assertEquals("1.5G", fluidFormatNumber(1.5e9, en))
    val enormous = fluidFormatNumber(1e30, en)
    assertTrue(enormous, enormous.contains("E"))
    assertEquals("—", fluidFormatNumber(Double.NaN, en))
    assertEquals("—", fluidFormatNumber(Double.POSITIVE_INFINITY, en))
  }

  // --- Angoli e bersagli ---------------------------------------------------------------------------------------

  @Test
  fun `angles tile the circle with a gap between slices and start at twelve o'clock`() {
    val angles = fluidDonutAngles(listOf(0.5f, 0.25f, 0.25f), gapDegrees = 4f)
    assertEquals(2f, angles[0].first, 1e-4f)
    assertEquals(176f, angles[0].second, 1e-4f)
    assertEquals(180f + 2f, angles[1].first, 1e-4f)
    // Fra due fette c'e' sempre il taglio.
    assertEquals(4f, angles[1].first - (angles[0].first + angles[0].second), 1e-4f)
    assertEquals(360f, angles.sumOf { (it.second + 4f).toDouble() }.toFloat(), 1e-3f)
  }

  @Test
  fun `a single visible slice is a full ring with no cut`() {
    val angles = fluidDonutAngles(listOf(1f), gapDegrees = 6f)
    assertEquals(0f, angles[0].first, 1e-4f)
    assertEquals(360f, angles[0].second, 1e-4f)
    val withZero = fluidDonutAngles(listOf(0f, 1f), gapDegrees = 6f)
    assertEquals(360f, withZero[1].second, 1e-4f)
    assertEquals(0f, withZero[0].second, 0f)
  }

  @Test
  fun `a slice narrower than the gap keeps half its angle instead of vanishing`() {
    val angles = fluidDonutAngles(listOf(0.99f, 0.005f, 0.005f), gapDegrees = 10f)
    assertEquals(0.9f, angles[1].second, 1e-3f)
    assertTrue(angles[1].second > 0f)
  }

  @Test
  fun `a tap finds the slice under it, gaps belong to a neighbour and the hole and the outside to nobody`() {
    val fractions = listOf(0.5f, 0.25f, 0.25f)
    val inner = 60f
    val outer = 100f
    // Ore 3 (destra) sta nella prima meta' (0..180 gradi).
    assertEquals(0, fluidDonutHit(80f, 0f, inner, outer, 0f, fractions))
    // Ore 7 e mezza: la seconda quota (180..270).
    assertEquals(1, fluidDonutHit(-50f, 80f, inner, outer, 0f, fractions))
    // Ore 10 e mezza: la terza (270..360).
    assertEquals(2, fluidDonutHit(-80f, -30f, inner, outer, 0f, fractions))
    // Il foro e il fuori.
    assertEquals(-1, fluidDonutHit(10f, 10f, inner, outer, 0f, fractions))
    assertEquals(-1, fluidDonutHit(150f, 0f, inner, outer, 0f, fractions))
    // Con tolleranza il dito appena fuori prende ancora l'anello.
    assertEquals(0, fluidDonutHit(110f, 0f, inner, outer, 12f, fractions))
    // Una fetta a zero non si trova mai.
    assertEquals(1, fluidDonutHit(10f, 80f, inner, outer, 0f, listOf(0f, 0.5f, 0.5f)))
    assertEquals(-1, fluidDonutHit(0f, 80f, inner, outer, 0f, listOf(0f, 0f)))
  }

  @Test
  fun `every angle belongs to exactly one slice`() {
    val fractions = listOf(0.4f, 0.0f, 0.35f, 0.25f)
    for (deg in 0 until 360) {
      val rad = Math.toRadians(deg.toDouble())
      val hit = fluidDonutHit((80 * kotlin.math.sin(rad)).toFloat(), (-80 * kotlin.math.cos(rad)).toFloat(), 60f, 100f, 0f, fractions)
      assertTrue("a $deg gradi: $hit", hit in listOf(0, 2, 3))
    }
  }

  @Test
  fun `arc bounds contain the quarter ring and the extremes of a half ring`() {
    // Primo quarto (ore 12 -> ore 3): da (cx, cy-100) a (cx+100, cy).
    val q = fluidArcBounds(100f, 100f, 100f, 60f, 0f, 90f)
    assertEquals(100f, q[0], 1e-3f) // sinistra: x dell'anello interno a ore 12
    assertEquals(0f, q[1], 1e-3f)
    assertEquals(200f, q[2], 1e-3f)
    assertEquals(100f, q[3], 1e-3f)
    // Mezzo anello a destra passa per ore 3: la larghezza arriva al raggio esterno.
    val half = fluidArcBounds(100f, 100f, 100f, 60f, 0f, 180f)
    assertEquals(200f, half[2], 1e-3f)
    assertEquals(0f, half[1], 1e-3f)
    assertEquals(200f, half[3], 1e-3f)
  }

  @Test
  fun `the ring gets thinner as the text grows`() {
    assertEquals(0.22f, fluidDonutRingFraction(1f), 1e-6f)
    assertEquals(0.15f, fluidDonutRingFraction(2f), 1e-6f)
    assertEquals(0.15f, fluidDonutRingFraction(3f), 1e-6f)
    assertEquals(0.22f, fluidDonutRingFraction(0.8f), 1e-6f)
    assertTrue(fluidDonutRingFraction(1.5f) in 0.15f..0.22f)
  }

  @Test
  fun `the entry starts complete with reduced motion or when already played, from zero otherwise`() {
    assertEquals(1f, fluidChartEnterStart(reducedMotion = true, alreadyPlayed = false), 0f)
    assertEquals(1f, fluidChartEnterStart(reducedMotion = false, alreadyPlayed = true), 0f)
    assertEquals(0f, fluidChartEnterStart(reducedMotion = false, alreadyPlayed = false), 0f)
  }

  // --- Barre ---------------------------------------------------------------------------------------------------

  @Test
  fun `bar spans sit centred in their slot, never thicker than the maximum and separated by a gap`() {
    val one = fluidBarSpans(slotExtent = 100f, seriesCount = 1, stacked = false, maxThickness = 40f, innerGap = 4f)
    assertEquals(1, one.size)
    assertEquals(40f, one[0].size, 1e-4f)
    assertEquals(30f, one[0].start, 1e-4f)

    val three = fluidBarSpans(slotExtent = 120f, seriesCount = 3, stacked = false, maxThickness = 40f, innerGap = 4f)
    assertEquals(3, three.size)
    three.zipWithNext().forEach { (a, b) -> assertEquals(4f, b.start - a.end, 1e-3f) }
    assertTrue(three.all { it.size <= 40f && it.start >= 0f && it.end <= 120f })
    // Centrate: stessi margini a sinistra e a destra.
    assertEquals(three.first().start, 120f - three.last().end, 1e-3f)

    val stacked = fluidBarSpans(slotExtent = 100f, seriesCount = 3, stacked = true, maxThickness = 40f, innerGap = 4f)
    assertTrue(stacked.all { it.start == stacked[0].start && it.end == stacked[0].end })
  }

  @Test
  fun `a narrow slot never gives a negative thickness`() {
    val spans = fluidBarSpans(slotExtent = 6f, seriesCount = 5, stacked = false, maxThickness = 40f, innerGap = 4f)
    assertTrue(spans.all { it.size >= 0f })
  }

  @Test
  fun `a stack accumulates from zero`() {
    val edges = fluidStackEdges(listOf(3f, 0f, 2f))
    assertEquals(listOf(0.0 to 3.0, 3.0 to 3.0, 3.0 to 5.0), edges)
  }

  @Test
  fun `the bar scale of a stack is the largest sum and a grouped chart the largest single value`() {
    val rows = listOf(listOf(3f, 4f), listOf(6f, 1f))
    val stacked = fluidBarScale(rows, 2, stacked = true, maxValue = null, targetTicks = 5)
    assertTrue(stacked.second == null)
    assertTrue(stacked.first!!.max >= 7.0)
    val grouped = fluidBarScale(rows, 2, stacked = false, maxValue = null, targetTicks = 5)
    assertTrue(grouped.first!!.max in 6.0..10.0)
    assertEquals(0.0, grouped.first!!.min, 0.0)
  }

  @Test
  fun `a declared denominator fixes the end of the scale and a value above it is invalid`() {
    val rows = listOf(listOf(412f), listOf(380f), listOf(90f))
    val ok = fluidBarScale(rows, 1, stacked = false, maxValue = 1240f, targetTicks = 5)
    assertEquals(1240.0, ok.first!!.max, 0.0)
    val over = fluidBarScale(listOf(listOf(1300f)), 1, stacked = false, maxValue = 1240f, targetTicks = 5)
    assertEquals(FluidSeriesProblem.ExceedsScale, over.second)
    assertTrue(over.first == null)
    assertEquals(
      FluidSeriesProblem.ExceedsScale,
      fluidBarScale(rows, 1, stacked = false, maxValue = 0f, targetTicks = 5).second,
    )
  }

  @Test
  fun `bars with bad data are refused - negative in a stack, not finite, ragged rows`() {
    assertEquals(FluidSeriesProblem.Negative, fluidBarScale(listOf(listOf(1f, -1f)), 2, true, null, 5).second)
    assertEquals(FluidSeriesProblem.NotFinite, fluidBarScale(listOf(listOf(Float.NaN)), 1, false, null, 5).second)
    assertEquals(FluidSeriesProblem.NotFinite, fluidBarScale(listOf(listOf(Float.POSITIVE_INFINITY)), 1, false, null, 5).second)
    assertEquals(FluidSeriesProblem.Ragged, fluidBarScale(listOf(listOf(1f), listOf(1f, 2f)), 1, false, null, 5).second)
    // Negativi nelle barre affiancate sono ammessi: la scala include lo zero e il minimo.
    val signed = fluidBarScale(listOf(listOf(-4f), listOf(9f)), 1, false, null, 5)
    assertTrue(signed.second == null)
    assertTrue(signed.first!!.min <= -4.0 && signed.first!!.max >= 9.0)
  }

  @Test
  fun `an enormous bar value gives a finite scale`() {
    val scale = fluidBarScale(listOf(listOf(1e30f), listOf(5f)), 1, false, null, 5).first!!
    assertTrue(scale.max >= 1e30 * (1 - 1e-9) && scale.max.isFinite())
    assertTrue(scale.fraction(5.0) < 1e-6f)
  }

  // --- Linee ---------------------------------------------------------------------------------------------------

  @Test
  fun `a hole breaks the line instead of joining its neighbours`() {
    assertEquals(listOf(listOf(0, 1), listOf(3)), fluidLineRuns(listOf(1f, 2f, null, 4f)))
    assertEquals(listOf(listOf(0, 1, 2)), fluidLineRuns(listOf(1f, 2f, 3f)))
    assertEquals(emptyList<List<Int>>(), fluidLineRuns(listOf(null, null)))
    assertEquals(listOf(listOf(1), listOf(3)), fluidLineRuns(listOf(null, 1f, null, 2f, null)))
    assertEquals(emptyList<List<Int>>(), fluidLineRuns(emptyList()))
    // Un valore non finito conta come un buco.
    assertEquals(listOf(listOf(0), listOf(2)), fluidLineRuns(listOf(1f, Float.NaN, 3f)))
  }

  @Test
  fun `a monotone curve never overshoots the measured values`() {
    val random = Random(5)
    repeat(200) {
      val n = random.nextInt(3, 12)
      val xs = FloatArray(n) { it * 10f }
      val ys = FloatArray(n) { random.nextFloat() * 100f }
      val t = fluidMonotoneTangents(xs, ys)
      for (i in 0 until n - 1) {
        val lo = min(ys[i], ys[i + 1])
        val hi = max(ys[i], ys[i + 1])
        val dx = xs[i + 1] - xs[i]
        for (step in 0..20) {
          val u = step / 20f
          val h00 = 2 * u * u * u - 3 * u * u + 1
          val h10 = u * u * u - 2 * u * u + u
          val h01 = -2 * u * u * u + 3 * u * u
          val h11 = u * u * u - u * u
          val y = h00 * ys[i] + h10 * dx * t[i] + h01 * ys[i + 1] + h11 * dx * t[i + 1]
          assertTrue("overshoot $y fuori da $lo..$hi (segmento $i)", y >= lo - 1e-3f && y <= hi + 1e-3f)
        }
      }
    }
  }

  @Test
  fun `tangents of one or two points are well defined`() {
    assertEquals(0, fluidMonotoneTangents(floatArrayOf(), floatArrayOf()).size)
    assertEquals(1, fluidMonotoneTangents(floatArrayOf(1f), floatArrayOf(1f)).size)
    val two = fluidMonotoneTangents(floatArrayOf(0f, 10f), floatArrayOf(0f, 5f))
    assertEquals(0.5f, two[0], 1e-6f)
    assertEquals(0.5f, two[1], 1e-6f)
    // Valori uguali: tangente piatta.
    assertTrue(fluidMonotoneTangents(floatArrayOf(0f, 1f, 2f), floatArrayOf(3f, 3f, 3f)).all { it == 0f })
  }

  @Test
  fun `slots map positions to indexes and back`() {
    assertEquals(0, fluidSlotAt(10f, 4, left = 10f, width = 100f))
    assertEquals(3, fluidSlotAt(110f, 4, left = 10f, width = 100f))
    assertEquals(1, fluidSlotAt(40f, 4, left = 10f, width = 100f))
    assertEquals(-1, fluidSlotAt(5f, 4, left = 10f, width = 100f))
    assertEquals(-1, fluidSlotAt(120f, 4, left = 10f, width = 100f))
    assertEquals(-1, fluidSlotAt(50f, 0, left = 10f, width = 100f))
    assertEquals(22.5f, fluidSlotCenter(0, 4, 10f, 100f), 1e-4f)
    assertEquals(60f, fluidSlotCenter(0, 1, 10f, 100f), 1e-4f)
  }

  // --- Colori di serie ----------------------------------------------------------------------------------------

  private val lightFilm = Color(0xFFFAFAFA)
  private val darkFilm = Color(0xFF4A4A4E)

  private data class Theme(val name: String, val dark: Boolean, val scheme: ColorScheme, val film: Color)

  private fun themes(): List<Theme> =
    listOf("fluid", "ember", "ocean", "jade").flatMap { name ->
      listOf(false, true).map { dark ->
        val scheme = resolveFluidColorScheme(
          settings = EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = name),
          isDark = dark,
        )
        Theme("$name/dark=$dark", dark, scheme, if (dark) darkFilm else lightFilm)
      }
    }

  @Test
  fun `the default series colours are the palette as it is and stand out at 3 to 1 on every surface in both themes`() {
    themes().forEach { theme ->
      for (count in 1..12) {
        val colors = fluidChartColors(List(count) { Color.Unspecified }, theme.scheme, theme.film, theme.dark)
        colors.forEachIndexed { i, color ->
          val expected = FluidIdentityColors.colorAt(fluidSeriesPaletteIndex(i, count)).forTheme(theme.dark)
          assertEquals("${theme.name} n=$count #$i: il ritocco non serve alla palette", expected, color)
          fluidChartSurfaces(theme.scheme, theme.film).forEach { surface ->
            val ratio = fluidContrastRatio(color, surface)
            assertTrue("${theme.name} n=$count #$i su $surface: $ratio:1", ratio >= 3f)
          }
        }
      }
    }
  }

  @Test
  fun `every colour of the whole palette passes on every surface in both themes`() {
    themes().forEach { theme ->
      val all = FluidIdentityColors.palette.map { it.forTheme(theme.dark) }
      val fixed = fluidChartColors(all, theme.scheme, theme.film, theme.dark)
      assertEquals(all, fixed)
    }
  }

  @Test
  fun `colours the app passes are moved by the least that reaches 3 to 1 - five hundred random colours`() {
    val random = Random(99)
    val colours = List(500) { Color(random.nextInt(0x1000000) or (0xFF shl 24)) } +
      listOf(Color.White, Color.Black, Color(0xFFFFFF00), Color(0xFF777777), Color(0xFFF2F2F2), Color(0xFF1C1B1F))
    themes().forEach { theme ->
      val fixed = fluidChartColors(colours, theme.scheme, theme.film, theme.dark)
      val surfaces = fluidChartSurfaces(theme.scheme, theme.film)
      fixed.forEachIndexed { i, c ->
        surfaces.forEach { s ->
          assertTrue("${theme.name} ${colours[i]} -> $c su $s: ${fluidContrastRatio(c, s)}", fluidContrastRatio(c, s) >= 3f - 0.02f)
        }
        // E il valore scritto dentro la barra si legge a 4,5:1.
        val readable = fluidReadableOn(c)
        assertTrue(fluidContrastRatio(readable.ink, readable.fill) >= FluidInkMinContrast - 0.01f)
      }
    }
  }

  @Test
  fun `a colour that already stands out is not touched`() {
    themes().forEach { theme ->
      val strong = if (theme.dark) Color(0xFFFFD54F) else Color(0xFF0D47A1)
      assertEquals(strong, fluidChartColors(listOf(strong), theme.scheme, theme.film, theme.dark).single())
    }
  }

  @Test
  fun `axis and value text reads at 4,5 to 1 on every surface in both themes`() {
    themes().forEach { theme ->
      fluidChartSurfaces(theme.scheme, theme.film).forEach { surface ->
        listOf("onSurface" to theme.scheme.onSurface, "muted" to fluidChartMutedText(theme.scheme, theme.film)).forEach { (name, ink) ->
          val ratio = fluidContrastRatio(ink, surface)
          assertTrue("${theme.name} $name su $surface: $ratio:1", ratio >= 4.5f)
        }
      }
    }
  }

  @Test
  fun `series colours spread over the palette and never collide below twelve`() {
    for (count in 1..12) {
      val indexes = List(count) { fluidSeriesPaletteIndex(it, count) }
      assertEquals("n=$count: $indexes", count, indexes.toSet().size)
    }
    // Da dodici in poi la palette gira.
    assertEquals(fluidSeriesPaletteIndex(0, 20), fluidSeriesPaletteIndex(12, 20))
    // Due serie sono blu e arancio.
    assertEquals(listOf(7, 1), List(2) { fluidSeriesPaletteIndex(it, 2) })
    // Posizione e conteggio fuori dai limiti non esplodono.
    assertTrue(fluidSeriesPaletteIndex(-3, 0) in 0..11)
  }

  // --- Testo generato ------------------------------------------------------------------------------------------

  private val italian = FluidLabels()
  private val english = FluidLabels.english()

  private fun sampleSlices() = ready(
    fluidDonutLayout(listOf(entry("Principale", 620f), entry("Promozioni", 210f), entry("Social", 170f))),
  ).slices

  @Test
  fun `a donut says what it is, its largest part and each slice with value and percentage - italian`() {
    val slices = sampleSlices()
    val percents = fluidPercentTexts(slices.map { it.fraction }, itl)
    val values = slices.map { fluidFormatNumber(it.value.toDouble(), itl) }
    val text = fluidDonutText(slices, values, percents, italian, itl, "Arrivi per categoria", null, null)
    assertEquals("Arrivi per categoria. Ripartizione in 3 parti. La più grande: Principale, 62%.", text.summary)
    assertEquals(
      listOf("Principale: 620, 62%", "Promozioni: 210, 21%", "Social: 170, 17%"),
      text.items,
    )
  }

  @Test
  fun `a donut says the same in english with english numbers`() {
    val slices = sampleSlices()
    val percents = fluidPercentTexts(slices.map { it.fraction }, en)
    val values = listOf("1,240", "5.5", "70")
    val text = fluidDonutText(slices, values, percents, english, en, null, "1,240", "Mail")
    assertEquals("Mail – 1,240. Breakdown in 3 parts. The largest: Principale, 62%.", text.summary)
    assertEquals("Principale: 1,240 (62%)", text.items[0])
    assertEquals("Social: 70 (17%)", text.items[2])
  }

  @Test
  fun `a slice whose value is hidden is read with its percentage only`() {
    val slices = sampleSlices()
    val percents = fluidPercentTexts(slices.map { it.fraction }, itl)
    val text = fluidDonutText(slices, listOf("", "", ""), percents, italian, itl, null, null, null)
    assertEquals("Principale: 62%", text.items[0])
  }

  @Test
  fun `bars say the kind, the number of items, the highest, the series and the declared scale`() {
    val groups = listOf(FluidBarGroup("Lavoro", listOf(412f)), FluidBarGroup("Clienti", listOf(380f)), FluidBarGroup("Casa", listOf(90f)))
    val texts = groups.map { g -> g.values.map { fluidFormatNumber(it.toDouble(), itl) } }
    val text = fluidBarsText(
      groups, listOf(FluidChartSeries("Mail")), texts, stacked = false,
      highest = 0 to "412", scaleLabel = "% sulle 1.240 mail", labels = italian, locale = itl, title = null,
    )
    assertEquals("Grafico a barre, 3 voci. Il valore più alto: Lavoro, 412. Scala: % sulle 1.240 mail.", text.summary)
    assertEquals(listOf("Lavoro: 412", "Clienti: 380", "Casa: 90"), text.items)

    val multi = fluidBarsText(
      listOf(FluidBarGroup("Mar", listOf(5f, 7f))),
      listOf(FluidChartSeries("Ricevute"), FluidChartSeries("Inviate")),
      listOf(listOf("5", "7")), stacked = true, highest = 0 to "12", scaleLabel = null, labels = english, locale = en, title = "Per mese",
    )
    assertEquals("Per mese. Stacked bar chart, 1 items. 2 series. Highest value: Mar, 12.", multi.summary)
    assertEquals("Mar – Ricevute 5, Inviate 7", multi.items.single())
  }

  @Test
  fun `a line says its points, range and every point, and a hole says there is no data`() {
    val x = listOf("1 mar", "2 mar", "3 mar")
    val text = fluidLineText(
      x, listOf(FluidChartSeries("Mail")), listOf(listOf("10", null, "30")),
      range = "10" to "30", scaleLabel = "mail al giorno", labels = italian, locale = itl, title = null,
    )
    assertEquals("Andamento, 3 punti, da 1 mar a 3 mar. Minimo 10, massimo 30. Scala: mail al giorno.", text.summary)
    assertEquals(listOf("1 mar: 10", "2 mar: nessun dato", "3 mar: 30"), text.items)

    val multi = fluidLineText(
      x, listOf(FluidChartSeries("A"), FluidChartSeries("B")), listOf(listOf("1", "2", "3"), listOf("4", null, "6")),
      range = "1" to "6", scaleLabel = null, labels = english, locale = en, title = null,
    )
    assertEquals("2 mar – A 2, B no data", multi.items[1])
    assertTrue(multi.summary.contains("2 series."))
  }

  @Test
  fun `the english chart labels are all translated and carry the same placeholders as the italian ones`() {
    val names = listOf(
      "chartNoData", "chartInvalidData", "chartInvalidSum", "chartOther", "chartLoading", "chartDonutSummary",
      "chartLargest", "chartBarsSummary", "chartStackedSummary", "chartSeriesCount", "chartHighest",
      "chartLineSummary", "chartRange", "chartScale", "chartSliceItem", "chartGroupItem", "chartNoValue",
    )
    val placeholder = Regex("%\\d\\$[sd]")
    names.forEach { name ->
      val field = FluidLabels::class.java.getDeclaredField(name).apply { isAccessible = true }
      val it = field.get(italian) as String
      val en = field.get(english) as String
      assertNotEquals("$name e' rimasto uguale", it, en)
      assertEquals(
        "$name: segnaposto diversi",
        placeholder.findAll(it).map { m -> m.value }.sorted().toList(),
        placeholder.findAll(en).map { m -> m.value }.sorted().toList(),
      )
    }
  }
}
