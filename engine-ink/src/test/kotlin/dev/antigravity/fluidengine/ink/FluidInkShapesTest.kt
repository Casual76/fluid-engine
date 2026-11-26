package dev.antigravity.fluidengine.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * F8.16 · Le forme tenendo fermo il tratto: tratti a mano finti (con il tremolio del dito, rumore di ±2 unita' su forme
 * da 200) che diventano la forma giusta, e uno scarabocchio che resta com'e'.
 */
class FluidInkShapesTest {
  private val random = Random(16)

  private fun noisy(points: List<Pair<Float, Float>>, noise: Float = 2f): FluidInkPoints {
    val b = FluidInkPointsBuilder(points.size)
    points.forEachIndexed { i, (x, y) ->
      b.add(x + (random.nextFloat() - 0.5f) * 2f * noise, y + (random.nextFloat() - 0.5f) * 2f * noise, i * 8f)
    }
    return b.build()
  }

  private fun polygon(vararg corners: Pair<Float, Float>, perEdge: Int = 20, startAt: Float = 0f): List<Pair<Float, Float>> {
    val out = ArrayList<Pair<Float, Float>>()
    val n = corners.size
    for (e in 0 until n) {
      val (ax, ay) = corners[e]
      val (bx, by) = corners[(e + 1) % n]
      for (k in 0 until perEdge) {
        val t = k.toFloat() / perEdge
        out += (ax + (bx - ax) * t) to (ay + (by - ay) * t)
      }
    }
    val shift = (startAt * out.size).toInt()
    val rotated = out.drop(shift) + out.take(shift)
    return rotated + rotated.first()
  }

  private fun ellipse(cx: Float, cy: Float, a: Float, b: Float, count: Int = 80, overshoot: Float = 0.04f): List<Pair<Float, Float>> =
    (0..(count * (1 + overshoot)).toInt()).map {
      val t = it * 2 * PI / count
      (cx + a * cos(t)).toFloat() to (cy + b * sin(t)).toFloat()
    }

  private fun recognize(points: FluidInkPoints) = InkShapeRecognizer.recognize(points, minSize = 24f)

  @Test fun aWobblyCircleBecomesACircle() {
    val shape = recognize(noisy(ellipse(150f, 150f, 100f, 100f)))
    assertNotNull(shape)
    assertEquals(FluidInkShapeKind.Circle, shape!!.kind)
    // Il contorno perfetto: tutti i punti a 100 ± 4 dal centro.
    for (i in 0 until shape.outline.size / 2) {
      val r = hypot(shape.outline[i * 2] - 150f, shape.outline[i * 2 + 1] - 150f)
      assertEquals(100f, r, 4f)
    }
  }

  @Test fun aFlatLoopBecomesAnEllipse() {
    val shape = recognize(noisy(ellipse(200f, 100f, 160f, 60f)))
    assertEquals(FluidInkShapeKind.Ellipse, shape?.kind)
  }

  @Test fun fourCornersBecomeARectangle() {
    val shape = recognize(noisy(polygon(0f to 0f, 260f to 0f, 260f to 140f, 0f to 140f, startAt = 0.3f)))
    assertEquals(FluidInkShapeKind.Rectangle, shape?.kind)
    val o = shape!!.outline
    val xs = (0 until o.size / 2).map { o[it * 2] }
    val ys = (0 until o.size / 2).map { o[it * 2 + 1] }
    // Raddrizzato: i lati sono orizzontali e verticali, alle misure degli angoli trovati.
    assertEquals(0f, xs.min(), 4f)
    assertEquals(260f, xs.max(), 4f)
    assertEquals(140f, ys.max() - ys.min(), 6f)
  }

  @Test fun almostEqualSidesBecomeASquare() {
    val shape = recognize(noisy(polygon(0f to 0f, 200f to 0f, 200f to 190f, 0f to 190f)))
    assertEquals(FluidInkShapeKind.Square, shape?.kind)
  }

  @Test fun aTiltedRectangleStaysTilted() {
    val a = 25.0 * PI / 180
    fun r(x: Float, y: Float) = (x * cos(a) - y * sin(a)).toFloat() + 200f to (x * sin(a) + y * cos(a)).toFloat() + 100f
    val shape = recognize(noisy(polygon(r(0f, 0f), r(240f, 0f), r(240f, 120f), r(0f, 120f))))
    assertEquals(FluidInkShapeKind.Rectangle, shape?.kind)
    val o = shape!!.outline
    // Il primo lato: inclinato di 25° (o di 25 + 90), non raddrizzato.
    val angle = Math.toDegrees(kotlin.math.atan2((o[3] - o[1]).toDouble(), (o[2] - o[0]).toDouble())).let { ((it % 90) + 90) % 90 }
    assertEquals(25.0, angle, 4.0)
  }

  @Test fun threeCornersBecomeATriangle() {
    val shape = recognize(noisy(polygon(100f to 0f, 220f to 200f, 0f to 200f)))
    assertEquals(FluidInkShapeKind.Triangle, shape?.kind)
  }

  @Test fun anAlmostStraightStrokeBecomesALine() {
    val pts = (0..40).map { (it * 6f) to (it * 3f + if (it % 2 == 0) 1.5f else -1.5f) }
    val shape = recognize(noisy(pts, noise = 1f))
    assertEquals(FluidInkShapeKind.Line, shape?.kind)
    val o = shape!!.outline
    assertEquals(0f, o[0], 3f)
    assertEquals(240f, o[o.size - 2], 3f)
    assertTrue(!shape.closed)
  }

  @Test fun aScribbleStaysHandDrawn() {
    val pts = (0..120).map { val t = it / 10.0; (t * 20 + 40 * sin(t * 3)).toFloat() to (60 * cos(t * 2.3) + t * 5).toFloat() }
    assertNull(recognize(noisy(pts)))
  }

  @Test fun aSmallDotIsNotAShape() {
    assertNull(recognize(noisy(ellipse(10f, 10f, 5f, 5f), noise = 0.2f)))
  }

  @Test fun theShapeStrokeKeepsToolColorAndPressure() {
    val b = FluidInkPointsBuilder()
    ellipse(100f, 100f, 80f, 80f).forEachIndexed { i, (x, y) -> b.add(x, y, i * 8f, pressure = 0.4f) }
    val source = FluidInkStroke(7L, FluidInkTool.Pencil, androidx.compose.ui.graphics.Color.Blue, 3f, b.build(), FluidInkInput.Stylus)
    val shape = InkShapeRecognizer.recognize(source.points, 24f)!!
    val stroke = InkShapeRecognizer.strokeFor(source, shape)
    assertEquals(FluidInkTool.Pencil, stroke.tool)
    assertEquals(FluidInkShapeKind.Circle, stroke.shape)
    assertEquals(0.4f, stroke.points.pressure(3), 0.001f)
    // Il tempo cresce sempre, anche sul punto che chiude la forma.
    for (i in 1 until stroke.points.size) assertTrue(stroke.points.time(i) >= stroke.points.time(i - 1))
    val first = stroke.points.x(0) to stroke.points.y(0)
    val last = stroke.points.x(stroke.points.size - 1) to stroke.points.y(stroke.points.size - 1)
    assertTrue(abs(first.first - last.first) < 0.5f && abs(first.second - last.second) < 0.5f)
  }
}
