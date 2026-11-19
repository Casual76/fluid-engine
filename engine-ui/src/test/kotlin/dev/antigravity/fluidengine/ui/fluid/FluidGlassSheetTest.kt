package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I detent del foglio di vetro (F5B.2): altezze, rilascio, opacità continua, rientro, angoli concentrici, la molla
 * tarata su Apple. Densità 3, una finestra da telefono di 411 × 891 dp con la barra di stato a 40 dp.
 */
class FluidGlassSheetTest {

  private val d = 3f
  private val windowH = 891f * d
  private val windowW = 411f * d
  private val topInset = 40f * d
  private val large = fluidSheetLargeHeightPx(windowH, topInset, d)

  private fun stops(vararg detents: FluidSheetDetent) = fluidSheetStops(detents.toList(), windowH, topInset, d)

  @Test
  fun `tutta altezza arriva sotto la barra di stato, a meta' e' il 52 per cento della finestra`() {
    assertEquals(windowH - topInset - FluidSheetLargeTopGap.value * d, large, 0.01f)
    val s = stops(FluidSheetDetent.Large, FluidSheetDetent.Medium)
    assertEquals(listOf(FluidSheetDetent.Medium, FluidSheetDetent.Large), s.detents)
    assertEquals(windowH * 0.52f, s.heights[0], 0.01f)
    assertEquals(large, s.heights[1], 0.01f)
  }

  @Test
  fun `senza detent vale tutta altezza, e due detent alla stessa altezza sono uno`() {
    val none = fluidSheetStops(emptyList(), windowH, topInset, d)
    assertEquals(listOf(FluidSheetDetent.Large), none.detents)
    val twice = stops(FluidSheetDetent.Large, FluidSheetDetent.Fraction(1f))
    assertEquals(1, twice.heights.size)
    assertEquals(FluidSheetDetent.Large, twice.detents.single())
  }

  @Test
  fun `frazione di tutta altezza, altezza fissa fra il minimo e tutta altezza`() {
    val s = stops(FluidSheetDetent.Fraction(0.25f), FluidSheetDetent.Height(10.dp), FluidSheetDetent.Height(5000.dp))
    assertArrayEquals(floatArrayOf(FluidSheetMinDetentHeight.value * d, large * 0.25f, large), s.heights, 0.01f)
  }

  @Test
  fun `l'altezza visibile si allunga con l'elastico sopra il piu' alto e resta al piu' basso sotto`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    assertEquals(s.heights[0] + 100f, fluidSheetVisibleHeight(s.heights[0] + 100f, s, windowH), 0.001f)
    val stretched = fluidSheetVisibleHeight(large + 300f, s, windowH)
    assertTrue("l'elastico tira meno del dito: $stretched", stretched > large && stretched < large + 300f * 0.56f)
    assertTrue(stretched < large + windowH * FluidSheetStretchFraction)
    assertEquals(s.heights[0], fluidSheetVisibleHeight(s.heights[0] - 200f, s, windowH), 0.001f)
    assertEquals(200f, fluidSheetSinkPx(s.heights[0] - 200f, s), 0.001f)
    assertEquals(0f, fluidSheetSinkPx(s.heights[0] + 10f, s), 0.001f)
  }

  @Test
  fun `il rilascio va al detent piu' vicino a dove lo slancio porta il foglio`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    val medium = s.heights[0]
    val mid = (medium + large) / 2f
    // Fermo: il piu' vicino.
    assertEquals(0, fluidSheetSettleTarget(s, mid - 10f, 0f, d))
    assertEquals(1, fluidSheetSettleTarget(s, mid + 10f, 0f, d))
    // Lanciato verso l'alto dalla meta' bassa: lo slancio lo porta in cima.
    assertEquals(1, fluidSheetSettleTarget(s, medium + 60f, 2000f * d, d))
    // Lanciato giu' dalla cima di un foglio a due detent: al detent basso, non fuori.
    assertEquals(0, fluidSheetSettleTarget(s, large - 30f, -3000f * d, d))
  }

  @Test
  fun `si chiude oltre la soglia sotto il piu' basso, o con un colpo giu' vicino al piu' basso`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    val medium = s.heights[0]
    assertEquals(FluidSheetDismiss, fluidSheetSettleTarget(s, medium - FluidSheetDismissTravel.value * d - 1f, 0f, d))
    assertEquals(0, fluidSheetSettleTarget(s, medium - FluidSheetDismissTravel.value * d + 5f, 0f, d))
    assertEquals(FluidSheetDismiss, fluidSheetSettleTarget(s, medium - 5f, -(FluidSheetDismissVelocity.value + 1f) * d, d))
    assertEquals(0, fluidSheetSettleTarget(s, medium - 5f, -(FluidSheetDismissVelocity.value - 50f) * d, d))
  }

  @Test
  fun `un foglio alto quanto il contenuto si chiude e torna come prima`() {
    // Le soglie di sempre: 110 dp di corsa o 900 dp/s verso il basso; altrimenti torna su.
    val content = FluidSheetStops(listOf(FluidSheetDetent.Large), floatArrayOf(300f * d))
    assertEquals(FluidSheetDismiss, fluidSheetSettleTarget(content, 300f * d - 111f * d, 0f, d))
    assertEquals(0, fluidSheetSettleTarget(content, 300f * d - 100f * d, 0f, d))
    assertEquals(FluidSheetDismiss, fluidSheetSettleTarget(content, 300f * d - 20f, -950f * d, d))
    assertEquals(0, fluidSheetSettleTarget(content, 300f * d - 20f, -850f * d, d))
  }

  @Test
  fun `vetro a meta', opaco in cima, in continuo e senza tornare indietro`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    val medium = s.heights[0]
    assertEquals(0f, fluidSheetLargeness(medium, s, large), 0f)
    assertEquals(1f, fluidSheetLargeness(large, s, large), 0f)
    // Fino a meta' del tratto resta vetro (Apple apre i lati dalla meta' in su).
    assertEquals(0f, fluidSheetLargeness((medium + large) / 2f, s, large), 1e-4f)
    var previous = 0f
    var steepest = 0f
    val steps = 400
    for (i in 0..steps) {
      val h = medium + (large - medium) * i / steps
      val now = fluidSheetLargeness(h, s, large)
      assertTrue("scende a $h", now >= previous - 1e-6f)
      steepest = maxOf(steepest, now - previous)
      previous = now
    }
    // Nessuno scatto: al passo di un duecentesimo del tratto l'opacita' cresce al piu' di 1,5/(0,5*400) ≈ 0,0075.
    assertTrue("passo massimo $steepest", steepest < 0.008f)
    // Un foglio senza detent parziali conta da meta' di tutta altezza.
    val onlyLarge = stops(FluidSheetDetent.Large)
    assertEquals(1f, fluidSheetLargeness(large, onlyLarge, large), 0f)
    assertEquals(0f, fluidSheetLargeness(large * 0.7f, onlyLarge, large), 0f)
  }

  @Test
  fun `il velo cresce solo nell'ultimo decimo del tratto e insegue senza superare`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    val medium = s.heights[0]
    val span = large - medium
    assertEquals(0f, fluidSheetLargeness(large - 0.11f * span, s, large, FluidSheetOpaqueBand), 0f)
    assertEquals(0.5f, fluidSheetLargeness(large - 0.05f * span, s, large, FluidSheetOpaqueBand), 1e-4f)
    assertEquals(1f, fluidSheetLargeness(large, s, large, FluidSheetOpaqueBand), 0f)
    // L'inseguimento: a un passo pari al ritardo fa il 63% della strada, non supera mai, a dt zero non si muove.
    assertEquals(1f - kotlin.math.exp(-1f), fluidSheetVeilStep(0f, 1f, FluidSheetVeilLagMillis), 1e-5f)
    assertEquals(0.3f, fluidSheetVeilStep(0.3f, 1f, 0f), 0f)
    var v = 0f
    repeat(60) {
      val next = fluidSheetVeilStep(v, 1f, 16.667f)
      assertTrue(next >= v && next <= 1f)
      v = next
    }
    assertTrue("a un secondo e' arrivato: $v", v > 0.999f)
    assertEquals(0.8f, fluidSheetVeilStep(Float.NaN, 0.8f, 16f), 0f)
  }

  @Test
  fun `oltre la soglia il velo cresce con la strada fino al detent sopra, senza scatti`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    val medium = s.heights[0]
    val ceiling = fluidSheetDimCeilingPx(s, medium)
    assertEquals(large, requireNotNull(ceiling), 0f)
    // Alla soglia e sotto (anche mentre il foglio scende per chiudersi) nessun velo, come il tocco; in cima e oltre, intero.
    assertEquals(0f, fluidSheetDim(medium, medium, ceiling), 0f)
    assertEquals(0f, fluidSheetDim(medium + 0.5f, medium, ceiling), 0f)
    assertEquals(0f, fluidSheetDim(medium - 200f, medium, ceiling), 0f)
    assertEquals(1f, fluidSheetDim(large, medium, ceiling), 0f)
    assertEquals(1f, fluidSheetDim(large + 300f, medium, ceiling), 0f)
    assertEquals(0.5f, fluidSheetDim((medium + large) / 2f, medium, ceiling), 1e-4f)
    // Il difetto di F5B.4: 6 dp sopra la soglia la pagina passava da 242 a 212 livelli in un fotogramma. Ora, a passi di
    // 6 dp lungo tutta la strada, la pagina chiara (242) scende al piu' di due livelli per passo, e il primo passo di
    // mezzo livello.
    val page = 242f
    val step = 6f * d
    var previous = 0f
    var steepest = 0f
    var h = medium
    while (h < large + step) {
      val now = fluidSheetDim(h, medium, ceiling)
      assertTrue("il velo non torna indietro salendo, a $h", now >= previous)
      steepest = maxOf(steepest, (now - previous) * FluidSheetDimLight * page)
      previous = now
      h += step
    }
    assertTrue("passo massimo $steepest livelli", steepest < 2f)
    assertTrue("primo passo", fluidSheetDim(medium + step, medium, ceiling) * FluidSheetDimLight * page < 0.5f)
  }

  @Test
  fun `senza soglia il velo e' intero, senza un detent sopra non c'e' mai`() {
    val s = stops(FluidSheetDetent.Medium, FluidSheetDetent.Large)
    // Il foglio di sempre (nessun undimmedUpTo): il velo arriva solo con la presenza dell'host.
    for (h in listOf(0f, s.heights[0], large, large + 100f)) assertEquals(1f, fluidSheetDim(h, null, null), 0f)
    // undimmedUpTo = Large: nessun detent sopra, la pagina resta chiara anche col foglio tirato oltre la cima.
    assertEquals(null, fluidSheetDimCeilingPx(s, large))
    assertEquals(0f, fluidSheetDim(large + 300f, large, null), 0f)
    // Una soglia sotto il detent piu' basso: il velo e' intero gia' al piu' basso e cala solo se il foglio scende.
    val low = FluidSheetMinDetentHeight.value * d
    val first = fluidSheetDimCeilingPx(s, low)
    assertEquals(s.heights[0], requireNotNull(first), 0f)
    assertEquals(1f, fluidSheetDim(s.heights[0], low, first), 0f)
    assertTrue(fluidSheetDim(s.heights[0] - 100f, low, first) in 0.01f..0.99f)
    assertEquals(0f, fluidSheetDim(Float.NaN, s.heights[0], large), 0f)
  }

  @Test
  fun `la pagina sotto un foglio si scurisce quanto in Apple, piu' che sotto gli altri modali`() {
    // WWDC25 284 f25440 (22,7%) e 323 f12203 (22,5-24%): vedi FluidSheetDimLight.
    assertEquals(0.23f, FluidSheetDimLight, 0f)
    assertTrue(FluidSheetDimLight > GlassPaintedScrim.alpha(false))
    // Sullo scuro e' una scelta: almeno quanto il chiaro, e piu' dei modali sullo scuro.
    assertTrue(FluidSheetDimDark >= FluidSheetDimLight && FluidSheetDimDark > GlassPaintedScrim.alpha(true))
    assertEquals(Color.Black.copy(alpha = FluidSheetDimLight), fluidSheetDimColor(false))
    assertEquals(Color.Black.copy(alpha = FluidSheetDimDark), fluidSheetDimColor(true))
  }

  @Test
  fun `il rientro e' pieno a mezza altezza e si chiude in cima`() {
    assertEquals(FluidSheetFloatingInset.value * d, fluidSheetInsetPx(0f, d), 1e-4f)
    assertEquals(0f, fluidSheetInsetPx(1f, d), 1e-4f)
    assertEquals(FluidSheetFloatingInset.value * d / 2f, fluidSheetInsetPx(0.5f, d), 1e-4f)
  }

  @Test
  fun `gli angoli bassi sono concentrici a quelli dello schermo`() {
    val window = Size(windowW, windowH)
    val inset = fluidSheetInsetPx(0f, d)
    val top = FluidRadius.Sheet.value * d
    val min = FluidRadius.Card.value * d
    // Un telefono con angoli da 52 dp: il foglio a 5 dp dal bordo li ripete a 47.
    val corner = 52f * d
    assertEquals(corner - inset, fluidSheetBottomRadiusPx(window, windowW - 2f * inset, inset, corner, top, min), 0.01f)
    // Angoli piccoli: non sotto il minimo.
    assertEquals(min, fluidSheetBottomRadiusPx(window, windowW - 2f * inset, inset, 10f * d, top, min), 0.01f)
    // Uno schermo ad angoli vivi, o un foglio in mezzo a un tablet: il raggio di sopra.
    assertEquals(top, fluidSheetBottomRadiusPx(window, windowW - 2f * inset, inset, 0f, top, min), 0.01f)
    assertEquals(top, fluidSheetBottomRadiusPx(Size(1280f * d, 800f * d), 620f * d, inset, corner, top, min), 0.01f)
  }

  @Test
  fun `la dissolvenza segue la molla e non esce da zero e uno`() {
    assertEquals(0f, fluidSheetCrossFadeAlpha(-0.2f), 0f)
    assertEquals(0.4f, fluidSheetCrossFadeAlpha(0.4f), 0f)
    assertEquals(1f, fluidSheetCrossFadeAlpha(1.03f), 0f)
    assertEquals(0f, fluidSheetCrossFadeAlpha(Float.NaN), 0f)
  }

  @Test
  fun `la molla dei detent sta sulla curva di Apple e supera appena`() {
    // WWDC25 219, f7362-f7374 (il foglio che cresce dal tocco): 60% a 100 ms, 76% a 133, 88% a 167, 95% a 200,
    // fermo a ~400 ms (f7374). Tolleranza: un fotogramma di Apple (33 ms) vale ~10 punti a meta' corsa. Apple parte piu'
    // svelto (23% a 33 ms: il tocco gli da' una spinta), per questo il confronto comincia a 100 ms.
    // La molla lavora in pixel (la soglia di quiete e' mezzo pixel): un viaggio vero di 1000 px, letto in frazione.
    val spring = TargetBasedAnimation(FluidSheetDetentSpring, Float.VectorConverter, 0f, 1000f, 0f)
    fun at(ms: Int) = spring.getValueFromNanos(ms * 1_000_000L) / 1000f
    val apple = mapOf(100 to 0.60f, 133 to 0.76f, 167 to 0.88f, 200 to 0.95f)
    for ((ms, value) in apple) assertEquals("a $ms ms", value, at(ms), 0.07f)
    var peak = 0f
    for (ms in 0..800 step 4) peak = maxOf(peak, at(ms))
    assertTrue("supera appena: $peak", peak > 1f && peak < 1.025f)
    for (ms in 440..800 step 20) assertEquals("fermo a $ms ms", 1f, at(ms), 0.005f)
  }

  @Test
  fun `scendendo il foglio arriva da sopra senza rimbalzo, sulla curva di Apple`() {
    // WWDC25 219, f7422-f7434 (il foglio a meta' che torna piccolo): 35% a 100 ms, 70% a 167, 88% a 233, 97% a 300.
    val spring = TargetBasedAnimation(FluidSheetDetentSpringDown, Float.VectorConverter, 0f, 1000f, 0f)
    fun at(ms: Int) = spring.getValueFromNanos(ms * 1_000_000L) / 1000f
    for ((ms, value) in mapOf(167 to 0.70f, 233 to 0.88f, 300 to 0.97f)) assertEquals("a $ms ms", value, at(ms), 0.09f)
    for (ms in 0..800 step 4) assertTrue("mai oltre il detent a $ms ms", at(ms) <= 1.0005f)
  }
}
