package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassLiquidMinSdk
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.resolveFluidGlassModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il contorno del vetro `Liquid` è quello della `Shape` (F1A, giro di correzione; rilievo R2 del critico C).
 *
 * Il metro è **il contorno che Compose produce**: `Shape.createOutline` (che riduce le coppie di raggi sullo stesso
 * fianco quando superano il lato corto) e `RoundRect.contains` (che riduce tutti i raggi insieme se due raggi accanto
 * superano un lato, come Skia). Non una seconda copia della regola del campo: la distanza del campo viene dallo
 * specchio del nucleo (`GlassFieldGeometry.slab`), con i raggi che la regola **forma → piano** gli dà.
 *
 * Un pixel conta come diverso quando il suo centro sta da una parte del contorno di Compose e il campo lo mette
 * dall'altra con più di mezzo pixel di margine: l'antialias del bordo non è una differenza di sagoma.
 *
 * **Da F1B.1** il nucleo rende anche l'angolo oltre mezzo lato corto (il ramo largo di `glassSlab`): le forme che
 * fino al giro di correzione di F1A restavano `Classic` qui sotto sono `Liquid`, con zero pixel dalla parte sbagliata.
 */
class GlassFieldOutlineTest {

  private val density1 = Density(1f)

  private fun composeInside(outline: Outline, x: Float, y: Float): Boolean = when (outline) {
    is Outline.Rectangle -> outline.rect.contains(Offset(x, y))
    is Outline.Rounded -> outline.roundRect.contains(Offset(x, y))
    is Outline.Generic -> error("un contorno generico non si confronta qui")
  }

  /**
   * Quanti pixel il campo mette dalla parte sbagliata del contorno di Compose, con i raggi di [geometry] (quelli che
   * la superficie riceve) su una lastra [size].
   */
  private fun mismatches(shape: Shape, size: Size, geometry: GlassSlabGeometry, rtl: Boolean = false): Int {
    val outline = shape.createOutline(size, if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, density1)
    val radii = floatArrayOf(geometry.topLeft, geometry.topRight, geometry.bottomRight, geometry.bottomLeft)
    val sample = GlassFieldSample()
    var count = 0
    for (j in 0 until size.height.toInt()) {
      for (i in 0 until size.width.toInt()) {
        val x = i + 0.5f
        val y = j + 0.5f
        GlassFieldGeometry.slab(
          x - size.width / 2f, y - size.height / 2f, size.width / 2f, size.height / 2f, radii, 0, geometry.smoothing,
          sample,
        )
        val d = sample.distance
        if (abs(d) <= 0.5f) continue
        if ((d < 0f) != composeInside(outline, x, y)) count++
      }
    }
    return count
  }

  /**
   * Come [mismatches], ma contro il **tracciato** del rettangolo arrotondato (gli archi in fila come li mette Skia,
   * 256 punti l'uno, e la regola pari-dispari): serve dove le zone di due angoli opposti si sovrappongono e
   * `RoundRect.contains` non descrive più ciò che si disegna.
   */
  private fun pathMismatches(size: Size, geometry: GlassSlabGeometry): Int {
    val radii = floatArrayOf(geometry.topLeft, geometry.topRight, geometry.bottomRight, geometry.bottomLeft)
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    val centers = listOf(
      Triple(radii[0].toDouble(), radii[0].toDouble(), 180.0),
      Triple(w - radii[1], radii[1].toDouble(), 270.0),
      Triple(w - radii[2], h - radii[2], 0.0),
      Triple(radii[3].toDouble(), h - radii[3], 90.0),
    )
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    for ((index, corner) in centers.withIndex()) {
      val r = radii[index].toDouble()
      for (k in 0..256) {
        val angle = Math.toRadians(corner.third + 90.0 * k / 256.0)
        xs += corner.first + r * kotlin.math.cos(angle)
        ys += corner.second + r * kotlin.math.sin(angle)
      }
    }
    fun inside(x: Double, y: Double): Boolean {
      var odd = false
      var j = xs.size - 1
      for (i in xs.indices) {
        if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) odd = !odd
        j = i
      }
      return odd
    }
    val sample = GlassFieldSample()
    var count = 0
    for (j in 0 until size.height.toInt()) {
      for (i in 0 until size.width.toInt()) {
        val x = i + 0.5f
        val y = j + 0.5f
        GlassFieldGeometry.slab(
          x - size.width / 2f, y - size.height / 2f, size.width / 2f, size.height / 2f, radii, 0, geometry.smoothing,
          sample,
        )
        if (abs(sample.distance) <= 0.5f) continue
        if ((sample.distance < 0f) != inside(x.toDouble(), y.toDouble())) count++
      }
    }
    return count
  }

  private fun geometry(shape: Shape, size: Size, rtl: Boolean = false): GlassSlabGeometry =
    glassSlabGeometry(shape, size, density1, if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, size.maxDimension)

  private fun GlassSlabGeometry.radii() = listOf(topLeft, topRight, bottomRight, bottomLeft)

  private fun modelFor(shape: Shape, size: Size): FluidGlassModel = resolveFluidGlassModel(
    sdk = FluidGlassLiquidMinSdk,
    shadersHealthy = { true },
    requested = FluidGlassModel.Liquid,
    falloff = GlassFalloff.Uniform,
    genericShape = !glassFieldRenders(shape) || !glassFieldFits(shape, size, density1, LayoutDirection.Ltr),
  )

  // ---- 1 · un angolo solo oltre mezzo lato corto -------------------------------------------------------------

  @Test
  fun oneCornerPastHalfTheShortSideIsDrawnAsComposeDrawsIt() {
    // Il caso del critico C: 80 + 20 = 100 sul fianco sinistro, una forma valida che Compose disegna con l'angolo a 80.
    val shape = RoundedCornerShape(topStart = 80.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val size = Size(200f, 100f)
    val outline = shape.createOutline(size, LayoutDirection.Ltr, density1) as Outline.Rounded
    assertEquals(80f, outline.roundRect.topLeftCornerRadius.x, 0f)
    val g = geometry(shape, size)
    assertTrue(g.fits)
    assertTrue("un angolo a 80 su un lato corto di 100 prende il ramo largo", g.wide)
    assertEquals(listOf(80f, 20f, 20f, 20f), g.radii())
    assertTrue(glassFieldFits(shape, size, density1, LayoutDirection.Ltr))
    assertEquals(0, mismatches(shape, size, g))
    assertEquals(FluidGlassModel.Liquid, modelFor(shape, size))
    // Il punto (10, 20) sta fuori dalla forma: fino a F1B.1 il campo, limitato a 50, lo metteva dentro il vetro.
    assertFalse(composeInside(outline, 10f, 20f))
    val sample = GlassFieldSample()
    GlassFieldGeometry.slab(10f - 100f, 20f - 50f, 100f, 50f, floatArrayOf(80f, 20f, 20f, 20f), 0, 0f, sample)
    assertTrue("fuori dalla sagoma: ${sample.distance}", sample.distance > 0.5f)
    // Il confronto vede davvero la differenza: coi raggi limitati di prima sono centinaia di pixel.
    val capped = GlassSlabGeometry().set(50f, 20f, 20f, 20f)
    assertTrue(mismatches(shape, size, capped) > 500)

    // La stessa forma su una lastra abbastanza alta: l'angolo sta in mezzo lato, il ramo è quello di sempre.
    val tall = Size(200f, 160f)
    val fitting = geometry(shape, tall)
    assertFalse(fitting.wide)
    assertEquals(listOf(80f, 20f, 20f, 20f), fitting.radii())
    assertEquals(0, mismatches(shape, tall, fitting))
    assertEquals(FluidGlassModel.Liquid, modelFor(shape, tall))
  }

  // ---- 2 · una coppia sullo stesso fianco che supera il lato --------------------------------------------------

  @Test
  fun aPairThatExceedsTheShortSideIsScaledLikeComposeAndRendersEverywhere() {
    val size = Size(240f, 100f)
    // 70 + 70 sul fianco sinistro: Compose li riduce a 50 e 50, mezzo lato. Il campo la rende uguale.
    val even = RoundedCornerShape(topStart = 70.dp, bottomStart = 70.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val g = geometry(even, size)
    assertFalse(g.wide)
    assertEquals(listOf(50f, 12f, 12f, 50f), g.radii())
    assertEquals(0, mismatches(even, size, g))
    assertEquals(FluidGlassModel.Liquid, modelFor(even, size))

    // 70 + 40: Compose dà 63,6 e 36,4. Il primo passa mezzo lato: ramo largo, stesso contorno.
    val uneven = RoundedCornerShape(topStart = 70.dp, bottomStart = 40.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val u = geometry(uneven, size)
    assertTrue(u.fits && u.wide)
    assertEquals(63.636f, u.topLeft, 1e-2f)
    assertEquals(36.364f, u.bottomLeft, 1e-2f)
    assertEquals(0, mismatches(uneven, size, u))
    assertEquals(FluidGlassModel.Liquid, modelFor(uneven, size))

    // Una «foglia»: due angoli opposti larghi, le cui zone si sovrappongono. Il campo la rende lo stesso.
    val leaf = RoundedCornerShape(topStart = 80.dp, topEnd = 20.dp, bottomEnd = 80.dp, bottomStart = 20.dp)
    val square = Size(100f, 100f)
    val l = geometry(leaf, square)
    assertTrue(l.wide)
    assertEquals(listOf(80f, 20f, 80f, 20f), l.radii())
    // Qui `RoundRect.contains` non è il tracciato: prova un angolo solo per punto (il primo la cui zona lo contiene),
    // e dove due zone si sovrappongono dà per dentro un punto che l'arco opposto taglia fuori. Il paragone è il
    // tracciato che Skia disegna (quattro archi di fila), campionato a poligono.
    assertEquals(0, pathMismatches(square, l))
    assertTrue("il paragone di Compose qui sbaglia, ed è il motivo di quello sopra", mismatches(leaf, square, l) > 0)

    // Una forma continua grande: l'emettitore dell'engine limita ogni angolo a mezzo lato **dopo** la riduzione di
    // Compose. 80 + 40 su un lato corto di 300 px (100 dp a densità 3) diventano 200 e 100 px: l'alto a 150 (mezzo
    // lato), il basso resta 100. Prima della correzione il campo gli dava 120 (limitato, non ridotto).
    val continuous = ContinuousCornerShape(topStart = 80.dp, bottomStart = 40.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    val big = glassSlabGeometry(continuous, Size(900f, 300f), Density(3f), LayoutDirection.Ltr, 900f)
    assertTrue(big.fits)
    assertFalse(big.wide)
    assertEquals(150f, big.topLeft, 1e-3f)
    assertEquals(100f, big.bottomLeft, 1e-3f)
  }

  // ---- 3 · il fumetto di chat ---------------------------------------------------------------------------------

  @Test
  fun aChatBubbleIsLiquidAtEveryHeightWithTheOutlineComposeDraws() {
    val bubble = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
    assertTrue("non c'è più niente da misurare: il campo la rende a ogni taglia", glassFieldFitsAtAnySize(bubble))

    // Una riga, 40 dp: il fianco destro (22 + 22) supera il lato e Compose lo porta a 20 e 20; il sinistro (22 + 6) no,
    // e l'angolo da 22 resta oltre mezzo lato. Fino a F1B.1 era `Classic`: due vetri nella stessa chat.
    val oneLine = Size(220f, 40f)
    val outline = bubble.createOutline(oneLine, LayoutDirection.Ltr, density1) as Outline.Rounded
    assertEquals(22f, outline.roundRect.topLeftCornerRadius.x, 1e-4f)
    assertEquals(20f, outline.roundRect.topRightCornerRadius.x, 1e-4f)
    val one = geometry(bubble, oneLine)
    assertTrue(one.fits && one.wide)
    assertEquals(listOf(22f, 20f, 20f, 6f), one.radii())
    assertEquals(0, mismatches(bubble, oneLine, one))
    assertEquals(FluidGlassModel.Liquid, modelFor(bubble, oneLine))

    // A ogni altezza, da sinistra a destra e da destra a sinistra, e in mezzo ai pixel (densità 2,125).
    for (height in listOf(30f, 36f, 40f, 44f, 60f, 96f)) {
      val size = Size(220f, height)
      val g = geometry(bubble, size)
      assertTrue("$height", g.fits)
      assertEquals("$height", 0, mismatches(bubble, size, g))
      val mirrored = geometry(bubble, size, rtl = true)
      assertEquals("$height rtl", listOf(g.topRight, g.topLeft, g.bottomLeft, g.bottomRight), mirrored.radii())
      assertEquals("$height rtl", 0, mismatches(bubble, size, mirrored, rtl = true))
      assertEquals(FluidGlassModel.Liquid, modelFor(bubble, size))
    }
  }

  @Test
  fun theWideBranchAgreesWithTheOldOneWhereBothCanDrawAndItsNormalIsAUnitVector() {
    // La stessa lastra, con un raggio un filo sotto e un filo sopra mezzo lato: i due rami danno la stessa distanza.
    val sample = GlassFieldSample()
    val other = GlassFieldSample()
    val below = floatArrayOf(49.99f, 20f, 20f, 20f)
    val above = floatArrayOf(50.02f, 20f, 20f, 20f)
    assertFalse(GlassFieldGeometry.isWide(100f, 50f, below, 0))
    assertTrue(GlassFieldGeometry.isWide(100f, 50f, above, 0))
    var j = 0.5f
    while (j < 100f) {
      var i = 0.5f
      while (i < 200f) {
        GlassFieldGeometry.slab(i - 100f, j - 50f, 100f, 50f, below, 0, 0f, sample)
        GlassFieldGeometry.slab(i - 100f, j - 50f, 100f, 50f, above, 0, 0f, other)
        // Dentro e accanto al contorno la distanza è la stessa (fuori, lontano dagli angoli, resta un limite inferiore).
        if (sample.distance < 2f) assertEquals("($i, $j)", sample.distance, other.distance, 0.05f)
        val length = kotlin.math.hypot(other.normalX, other.normalY)
        assertEquals("($i, $j) normale", 1f, length, 1e-3f)
        i += 3f
      }
      j += 3f
    }
    // Una lastra larga: il gradiente della distanza è la normale con l'allargamento a 1 (differenze finite).
    val radii = floatArrayOf(80f, 20f, 20f, 20f)
    val probe = GlassFieldSample()
    for ((x, y) in listOf(30f to 30f, 15f to 60f, 60f to 12f, 150f to 80f, 100f to 5f)) {
      GlassFieldGeometry.slab(x - 100f, y - 50f, 100f, 50f, radii, 0, 0f, sample, normalSpread = 1f)
      GlassFieldGeometry.slab(x + 0.05f - 100f, y - 50f, 100f, 50f, radii, 0, 0f, probe, normalSpread = 1f)
      val dx = (probe.distance - sample.distance) / 0.05f
      GlassFieldGeometry.slab(x - 100f, y + 0.05f - 50f, 100f, 50f, radii, 0, 0f, probe, normalSpread = 1f)
      val dy = (probe.distance - sample.distance) / 0.05f
      assertEquals("($x, $y) x", dx, sample.normalX, 0.02f)
      assertEquals("($x, $y) y", dy, sample.normalY, 0.02f)
    }
  }

  // ---- La capsula e il tondo non cambiano -----------------------------------------------------------------------

  @Test
  fun theCapsuleTheCircleAndUniformCornersAreUnchangedAndNeverMeasured() {
    for (shape in listOf(CircleShape, FluidCapsuleShape, RoundedCornerShape(12.dp), ContinuousCornerShape(28.dp), RectangleShape)) {
      assertTrue(shape.toString(), glassFieldFitsAtAnySize(shape))
    }
    assertTrue(glassFieldFitsAtAnySize(AbsoluteRoundedCornerShape(8.dp)))
    assertTrue(glassFieldFitsAtAnySize(ContinuousCornerShape(topStart = 38.dp, topEnd = 38.dp)))
    // Una forma che il campo non conosce resta fuori, a ogni taglia.
    assertFalse(glassFieldFitsAtAnySize(androidx.compose.foundation.shape.CutCornerShape(8.dp)))

    for ((w, h) in listOf(44f to 44f, 156f to 56f, 56f to 220f)) {
      val size = Size(w, h)
      val circle = geometry(CircleShape, size)
      assertTrue(circle.fits)
      assertEquals(List(4) { minOf(w, h) / 2f }, circle.radii())
      assertEquals(0, mismatches(CircleShape, size, circle))
      val capsule = geometry(FluidCapsuleShape, size)
      assertTrue(capsule.fits)
      assertEquals(List(4) { minOf(w, h) / 2f }, capsule.radii())
      assertEquals(0f, capsule.smoothing, 0f)
    }
    // Quattro angoli uguali e enormi: Compose li riduce a mezzo lato, il campo pure.
    val huge = RoundedCornerShape(500.dp)
    val g = geometry(huge, Size(300f, 64f))
    assertTrue(g.fits)
    assertEquals(List(4) { 32f }, g.radii())
    assertEquals(0, mismatches(huge, Size(300f, 64f), g))
  }

  @Test
  fun aSmallEngineShapeWithOneCornerPastHalfIsDrawnWithItsEquivalentRadii() {
    // Sotto i 256 px l'engine disegna un rettangolo arrotondato col raggio equivalente, e un angolo già a mezzo lato
    // resta com'è, anche oltre: 30 su un lato corto di 40 (con 10 sotto, la coppia sta nel lato) esce a 30.
    val small = ContinuousCornerShape(topStart = 30.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 10.dp)
    val g = glassSlabGeometry(small, Size(100f, 40f), density1, LayoutDirection.Ltr, 100f)
    assertTrue(g.fits && g.wide)
    assertEquals(30f, g.topLeft, 1e-4f)
    assertEquals(0, mismatches(small, Size(100f, 40f), g))
    // Con un angolo a mezzo lato esatto il campo la rende.
    val atHalf = ContinuousCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
    val h = glassSlabGeometry(atHalf, Size(100f, 40f), density1, LayoutDirection.Ltr, 100f)
    assertTrue(h.fits)
    assertEquals(listOf(20f, 6f * ContinuousCornerShape.RoundedEquivalentFactor, 0f, 0f), h.radii())
  }

  @Test
  fun aSlabWithoutASizeIsNeverJudged() {
    val bubble = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
    assertTrue(glassFieldFits(bubble, Size.Unspecified, density1, LayoutDirection.Ltr))
    assertTrue(glassFieldFits(bubble, Size(0f, 40f), density1, LayoutDirection.Ltr))
    for (side in listOf(Float.NaN, -4f, 0f)) {
      val g = glassSlabGeometry(side, 40f, 22f, 22f, 22f, 6f, 0f, continuous = false, small = false)
      for (r in g.radii()) assertTrue("$side: $r", r.isFinite() && r >= 0f)
    }
  }
}
