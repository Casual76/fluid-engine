package dev.antigravity.fluidengine.ui.glass.field

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ombra in viaggio come **sagoma sfocata in forma chiusa** (F1B.2): il riquadro sfocato meno i quattro angoli
 * ([GlassShadowProfile.piece], specchio di `glassShadowPiece`), contro una gaussiana vera calcolata qui per somma sui
 * pixel della sagoma, cioè ciò che fa la maschera a riposo. E il contorno della maschera a riposo per un angolo oltre
 * mezzo lato corto, che fino a F1B.2 aveva l'angolo limitato a mezzo lato.
 */
class GlassShadowShapeTest {

  private val sample = GlassFieldSample()

  /** Una sagoma del campo: centro nell'origine, mezza taglia, raggi, raccordo. */
  private class Shape(val halfWidth: Float, val halfHeight: Float, val radii: FloatArray, val smoothing: Float, val name: String)

  private fun inside(shape: Shape, x: Float, y: Float): Boolean {
    GlassFieldGeometry.slab(x, y, shape.halfWidth, shape.halfHeight, shape.radii, 0, shape.smoothing, sample)
    return sample.distance < 0f
  }

  /** La gaussiana vera della sagoma piena nel punto: somma sui centri dei pixel (2 x 2 campioni per pixel). */
  private fun blurred(shape: Shape, x: Float, y: Float, sigma: Float): Double {
    var sum = 0.0
    val k = 1.0 / (2.0 * Math.PI * sigma * sigma)
    var py = -shape.halfHeight
    while (py < shape.halfHeight) {
      var px = -shape.halfWidth
      while (px < shape.halfWidth) {
        for (sy in 0..1) for (sx in 0..1) {
          val cx = px + 0.25f + 0.5f * sx
          val cy = py + 0.25f + 0.5f * sy
          if (inside(shape, cx, cy)) {
            val dx = (x - cx).toDouble()
            val dy = (y - cy).toDouble()
            sum += 0.25 * k * exp(-(dx * dx + dy * dy) / (2.0 * sigma * sigma))
          }
        }
        px += 1f
      }
      py += 1f
    }
    return sum
  }

  /** I punti fuori dalla sagoma dove l'ombra si vede: sotto, ai lati, agli angoli, fra 0,2 e 2,5 deviazioni. */
  private fun probes(shape: Shape, sigma: Float): List<Pair<Float, Float>> {
    val out = ArrayList<Pair<Float, Float>>()
    for (k in listOf(0.2f, 0.6f, 1.2f, 2.5f)) {
      val reach = k * sigma
      out += 0f to shape.halfHeight + reach
      out += shape.halfWidth + reach to 0f
      out += 0f to -(shape.halfHeight + reach)
      // Sulla diagonale dell'angolo in basso a destra, fuori dal contorno.
      out += shape.halfWidth * 0.97f + reach * 0.7f to shape.halfHeight * 0.97f + reach * 0.7f
    }
    return out.filter { (x, y) -> !inside(shape, x, y) }
  }

  @Test
  fun aPieceIsItsBlurredBoxMinusItsCornersAsTheTrueGaussianSays() {
    // L'ombra B di serie a densità 1: raggio 34 dp, deviazione 20,1 px. Alla stessa scala tasto, capsula e menu.
    val sigma = GlassShadowProfile.sigma(34f)
    val shapes = listOf(
      Shape(22f, 22f, floatArrayOf(22f, 22f, 22f, 22f), 0f, "tasto da 44 dp"),
      Shape(78f, 28f, floatArrayOf(28f, 28f, 28f, 28f), 0.6f, "capsula 156 x 56"),
      Shape(85f, 96f, floatArrayOf(28f, 28f, 28f, 28f), 0.6f, "menu 170 x 192, raccordo di iOS"),
      Shape(40f, 20f, floatArrayOf(22f, 20f, 20f, 6f), 0f, "fumetto 22/20/20/6 alto 40: un angolo oltre mezzo lato"),
    )
    for (shape in shapes) {
      var worst = 0.0
      var worstStraight = 0.0
      for ((x, y) in probes(shape, sigma)) {
        val truth = blurred(shape, x, y, sigma)
        val closed = GlassShadowProfile.piece(x, y, shape.halfWidth, shape.halfHeight, shape.radii, 0, shape.smoothing, sigma)
        GlassFieldGeometry.slab(x, y, shape.halfWidth, shape.halfHeight, shape.radii, 0, shape.smoothing, sample)
        val straight = GlassShadowProfile.fall(sample.distance, sigma)
        worst = max(worst, abs(closed - truth))
        worstStraight = max(worstStraight, abs(straight - truth))
      }
      // Un centesimo della maschera è mezzo livello con l'ombra scura di serie (0,18) su bianco.
      assertTrue("${shape.name}: la forma chiusa dista ${"%.4f".format(worst)} dalla gaussiana vera", worst < 0.01)
      // E la caduta di un bordo dritto, quella di F1B.1, ne distava ben di più sui piccoli.
      if (shape.name.startsWith("tasto")) {
        assertTrue("tasto: la caduta dritta dista solo $worstStraight", worstStraight > 0.1)
      }
    }
  }

  @Test
  fun separatePiecesAddUpAndCoincidentOnesCountOnce() {
    val sigma = GlassShadowProfile.sigma(34f)
    val radii = floatArrayOf(22f, 22f, 22f, 22f, 22f, 22f, 22f, 22f)
    val smooth = floatArrayOf(0f, 0f)
    // Due tasti a 8 dp l'uno dall'altro: l'ombra del gruppo è la somma delle due (la gaussiana è lineare).
    val apart = floatArrayOf(-26f, 0f, 22f, 22f, 26f, 0f, 22f, 22f)
    for ((x, y) in listOf(0f to 30f, -26f to 40f, 60f to 10f)) {
      val one = GlassShadowProfile.piece(x + 26f, y, 22f, 22f, radii, 0, 0f, sigma)
      val other = GlassShadowProfile.piece(x - 26f, y, 22f, 22f, radii, 4, 0f, sigma)
      assertEquals(one + other, GlassShadowProfile.group(x, y, 2, apart, radii, smooth, sigma), 1e-6f)
    }
    // Lo stesso tasto due volte (un pezzo che nasce dentro l'altro): un'ombra sola, non il doppio.
    val same = floatArrayOf(0f, 0f, 22f, 22f, 0f, 0f, 22f, 22f)
    for ((x, y) in listOf(0f to 30f, 30f to 10f, 0f to 60f)) {
      val single = GlassShadowProfile.piece(x, y, 22f, 22f, radii, 0, 0f, sigma)
      val group = GlassShadowProfile.group(x, y, 2, same, radii, smooth, sigma)
      // La parte comune è il riquadro, che eccede il tasto tondo dei suoi angoli: il gruppo resta un poco sotto.
      assertTrue("($x, $y): due tasti uguali danno $group contro $single", group <= single + 1e-6f && group > single * 0.8f)
    }
  }

  @Test
  fun aCornerBeyondHalfTheShortSideKeepsItsRadiusInTheRestContour() {
    // Il fumetto di chat 22/22/22/6 alto 40 dp (a densità 3): l'angolo da 66 px sta oltre mezzo lato corto (60).
    val radii = floatArrayOf(66f, 60f, 60f, 18f)
    val halfWidth = 150f
    val halfHeight = 60f
    assertTrue(GlassFieldGeometry.isWide(halfWidth, halfHeight, radii, 0))
    assertTrue("il ramo largo ha solo archi di cerchio", GlassShadowContour.isCircular(halfWidth, halfHeight, radii, 0, 0.6f))
    val points = FloatArray(GlassShadowContour.MaxFloats)
    val count = GlassShadowContour.slab(0f, 0f, halfWidth, halfHeight, radii, 0, 0.6f, points)
    var worst = 0f
    for (i in 0 until count) {
      GlassFieldGeometry.slab(points[i * 2], points[i * 2 + 1], halfWidth, halfHeight, radii, 0, 0.6f, sample)
      worst = max(worst, abs(sample.distance))
    }
    assertTrue("il contorno dell'ombra a riposo sta a $worst px dalla sagoma del campo", worst < 0.05f)
    // Il punto più a sinistra dell'angolo in alto a sinistra sta a 66 px dal vertice, non a 60.
    val leftmostTop = (0 until count).filter { abs(points[it * 2] + halfWidth) < 0.01f }.minOf { points[it * 2 + 1] }
    assertEquals(-halfHeight + 66f, leftmostTop, 0.05f)
  }
}
