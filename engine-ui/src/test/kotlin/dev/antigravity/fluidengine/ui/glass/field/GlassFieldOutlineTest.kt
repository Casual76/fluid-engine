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
  fun oneCornerPastHalfTheShortSideIsNotSomethingTheFieldCanDrawSoItStaysClassic() {
    // Il caso del critico C: 80 + 20 = 100 sul fianco sinistro, una forma valida che Compose disegna con l'angolo a 80.
    val shape = RoundedCornerShape(topStart = 80.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val size = Size(200f, 100f)
    val outline = shape.createOutline(size, LayoutDirection.Ltr, density1) as Outline.Rounded
    assertEquals(80f, outline.roundRect.topLeftCornerRadius.x, 0f)
    val g = geometry(shape, size)
    assertFalse("un angolo a 80 su un lato corto di 100 il campo non lo sa rendere", g.fits)
    assertFalse(glassFieldFits(shape, size, density1, LayoutDirection.Ltr))
    assertEquals(FluidGlassModel.Classic, modelFor(shape, size))
    // Ciò che il campo avrebbe disegnato (l'angolo limitato a 50): è il difetto che il critico ha misurato (1298 pixel
    // con l'antialias), e questo confronto lo vede: l'area fra i due archi è (80² − 50²)(1 − π/4) ≈ 840 pixel, meno la
    // fascia di mezzo pixel che qui non si conta. Il punto (10, 20) sta fuori dalla forma e dentro quel vetro.
    val before = mismatches(shape, size, g)
    assertTrue("il confronto non vede l'angolo limitato: $before pixel", before > 500)
    assertFalse(composeInside(outline, 10f, 20f))

    // La stessa forma su una lastra abbastanza alta: l'angolo sta in mezzo lato, il campo la rende identica.
    val tall = Size(200f, 160f)
    val fitting = geometry(shape, tall)
    assertTrue(fitting.fits)
    assertEquals(listOf(80f, 20f, 20f, 20f), fitting.radii())
    assertEquals(0, mismatches(shape, tall, fitting))
    assertEquals(FluidGlassModel.Liquid, modelFor(shape, tall))
  }

  // ---- 2 · una coppia sullo stesso fianco che supera il lato --------------------------------------------------

  @Test
  fun aPairThatExceedsTheShortSideIsScaledLikeComposeAndRendersWhereItFits() {
    val size = Size(240f, 100f)
    // 70 + 70 sul fianco sinistro: Compose li riduce a 50 e 50, mezzo lato. Il campo la rende uguale.
    val even = RoundedCornerShape(topStart = 70.dp, bottomStart = 70.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val g = geometry(even, size)
    assertTrue(g.fits)
    assertEquals(listOf(50f, 12f, 12f, 50f), g.radii())
    assertEquals(0, mismatches(even, size, g))
    assertEquals(FluidGlassModel.Liquid, modelFor(even, size))

    // 70 + 40: Compose dà 63,6 e 36,4. Il primo passa mezzo lato: resta Classic.
    val uneven = RoundedCornerShape(topStart = 70.dp, bottomStart = 40.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val u = geometry(uneven, size)
    assertFalse(u.fits)
    assertEquals(FluidGlassModel.Classic, modelFor(uneven, size))

    // Una forma continua grande: l'emettitore dell'engine limita ogni angolo a mezzo lato **dopo** la riduzione di
    // Compose. 80 + 40 su un lato corto di 300 px (100 dp a densità 3) diventano 200 e 100 px: l'alto a 150 (mezzo
    // lato), il basso resta 100. Prima della correzione il campo gli dava 120 (limitato, non ridotto).
    val continuous = ContinuousCornerShape(topStart = 80.dp, bottomStart = 40.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    val big = glassSlabGeometry(continuous, Size(900f, 300f), Density(3f), LayoutDirection.Ltr, 900f)
    assertTrue(big.fits)
    assertEquals(150f, big.topLeft, 1e-3f)
    assertEquals(100f, big.bottomLeft, 1e-3f)
  }

  // ---- 3 · il fumetto di chat ---------------------------------------------------------------------------------

  @Test
  fun aChatBubbleIsLiquidWhereItsCornersFitAndClassicWhereComposeDrawsThemPastHalf() {
    val bubble = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
    assertFalse("gli angoli sono diversi: dipende dalla taglia", glassFieldFitsAtAnySize(bubble))

    // Una riga, 40 dp: il fianco destro (22 + 22) supera il lato e Compose lo porta a 20 e 20; il sinistro (22 + 6) no,
    // e l'angolo da 22 resta oltre mezzo lato.
    val oneLine = Size(220f, 40f)
    val outline = bubble.createOutline(oneLine, LayoutDirection.Ltr, density1) as Outline.Rounded
    assertEquals(22f, outline.roundRect.topLeftCornerRadius.x, 1e-4f)
    assertEquals(20f, outline.roundRect.topRightCornerRadius.x, 1e-4f)
    assertFalse(geometry(bubble, oneLine).fits)
    assertEquals(FluidGlassModel.Classic, modelFor(bubble, oneLine))

    // 44 dp: tutti a mezzo lato o meno. Il campo la rende identica, da sinistra a destra e da destra a sinistra.
    for (height in listOf(44f, 60f, 96f)) {
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

  // ---- La capsula e il tondo non cambiano -----------------------------------------------------------------------

  @Test
  fun theCapsuleTheCircleAndUniformCornersAreUnchangedAndNeverMeasured() {
    for (shape in listOf(CircleShape, FluidCapsuleShape, RoundedCornerShape(12.dp), ContinuousCornerShape(28.dp), RectangleShape)) {
      assertTrue(shape.toString(), glassFieldFitsAtAnySize(shape))
    }
    assertTrue(glassFieldFitsAtAnySize(AbsoluteRoundedCornerShape(8.dp)))
    assertFalse(glassFieldFitsAtAnySize(ContinuousCornerShape(topStart = 38.dp, topEnd = 38.dp)))

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
  fun aSmallEngineShapeWithOneCornerPastHalfStaysClassic() {
    // Sotto i 256 px l'engine disegna un rettangolo arrotondato col raggio equivalente, e un angolo già a mezzo lato
    // resta com'è, anche oltre: 30 su un lato corto di 40 (con 10 sotto, la coppia sta nel lato) esce a 30.
    val small = ContinuousCornerShape(topStart = 30.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 10.dp)
    val g = glassSlabGeometry(small, Size(100f, 40f), density1, LayoutDirection.Ltr, 100f)
    assertFalse(g.fits)
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
