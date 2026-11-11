package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerPointFloats
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.continuousCornerPoints
import dev.antigravity.fluidengine.ui.fluid.continuousCornerSmoothing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quanto il contorno del campo raccordato si scosta dalla curva continua dell'engine.
 *
 * Nel materiale `Liquid` la sagoma del vetro la ritaglia lo shader, e lo shader non conosce le tre cubiche di
 * `ContinuousCornerShape`: le approssima con una superellisse ([GlassCornerReachSquare]). Il piano (3.7)
 * dichiara che lo scarto sta sotto lo 0,5% del raggio; qui lo si **misura**, come distanza vera fra i due
 * contorni, ai raggi di `FluidRadius` e alle densità dei due dispositivi di prova, e una legge ritoccata che
 * peggiora l'angolo ferma i test.
 *
 * La curva dell'engine è quella vera: i punti escono da `continuousCornerPoints`, la stessa funzione che
 * l'emettitore del path usa (e che un test qui sotto tiene uguale al bit all'emettitore di prima).
 */
class ContinuousCornerFieldTest {

  private val sample = GlassFieldSample()

  private val right = Offset(1f, 0f)
  private val down = Offset(0f, 1f)
  private val left = Offset(-1f, 0f)
  private val up = Offset(0f, -1f)

  /** Una sagoma dell'engine: taglia in pixel e raggi nell'ordine di `RoundRect` (alto-sx, alto-dx, basso-dx, basso-sx). */
  private class Shape(val width: Float, val height: Float, val radii: FloatArray) {
    constructor(width: Float, height: Float, radius: Float) : this(width, height, FloatArray(4) { radius })
  }

  /** La distanza del campo nel punto (x, y) del sistema della sagoma, origine in alto a sinistra. */
  private fun fieldDistance(shape: Shape, x: Double, y: Double, smoothing: Float): Double {
    GlassFieldGeometry.slab(
      (x - shape.width / 2.0).toFloat(), (y - shape.height / 2.0).toFloat(),
      shape.width / 2f, shape.height / 2f, shape.radii, 0, smoothing, sample,
    )
    return sample.distance.toDouble()
  }

  /**
   * Quanto dista il punto dal contorno del campo: lo si porta sul livello zero scendendo lungo il gradiente
   * (differenze finite) e si misura la strada fatta. Non ci si fida della distanza che il campo dichiara:
   * nell'angolo raccordato è esatta solo al primo ordine.
   */
  private fun gapToFieldContour(shape: Shape, px: Double, py: Double, smoothing: Float): Double {
    var x = px
    var y = py
    val h = 0.05
    repeat(6) {
      val d = fieldDistance(shape, x, y, smoothing)
      val gx = (fieldDistance(shape, x + h, y, smoothing) - fieldDistance(shape, x - h, y, smoothing)) / (2 * h)
      val gy = (fieldDistance(shape, x, y + h, smoothing) - fieldDistance(shape, x, y - h, smoothing)) / (2 * h)
      val g2 = gx * gx + gy * gy
      if (g2 < 1e-6) return abs(d)
      x -= d * gx / g2
      y -= d * gy / g2
    }
    return hypot(x - px, y - py)
  }

  /** I punti di un angolo dell'engine, campionati fitti sulle sue tre cubiche. */
  private fun engineCornerSamples(
    corner: Offset,
    incoming: Offset,
    outgoing: Offset,
    radius: Float,
    minDimension: Float,
    smoothing: Float,
    perCubic: Int = 160,
  ): List<DoubleArray> {
    val points = continuousCornerPoints(corner, incoming, outgoing, radius, minDimension, smoothing)
    val out = ArrayList<DoubleArray>()
    out += doubleArrayOf(points[0].toDouble(), points[1].toDouble())
    if (points.size < ContinuousCornerPointFloats) return out
    for (cubic in 0 until 3) {
      val at = cubic * 6
      for (step in 1..perCubic) {
        val t = step.toDouble() / perCubic
        val u = 1.0 - t
        val a = u * u * u
        val b = 3.0 * u * u * t
        val c = 3.0 * u * t * t
        val d = t * t * t
        out += doubleArrayOf(
          a * points[at] + b * points[at + 2] + c * points[at + 4] + d * points[at + 6],
          a * points[at + 1] + b * points[at + 3] + c * points[at + 5] + d * points[at + 7],
        )
      }
    }
    return out
  }

  /** I quattro angoli nello stesso ordine e con le stesse direzioni di `ContinuousCornerShape.createOutline`. */
  private fun engineOutlineSamples(shape: Shape, smoothing: Float, cornerIndex: Int): List<DoubleArray> {
    val minDimension = min(shape.width, shape.height)
    return when (cornerIndex) {
      0 -> engineCornerSamples(Offset(0f, 0f), up, right, shape.radii[0], minDimension, smoothing)
      1 -> engineCornerSamples(Offset(shape.width, 0f), right, down, shape.radii[1], minDimension, smoothing)
      2 -> engineCornerSamples(Offset(shape.width, shape.height), down, left, shape.radii[2], minDimension, smoothing)
      else -> engineCornerSamples(Offset(0f, shape.height), left, up, shape.radii[3], minDimension, smoothing)
    }
  }

  /** Lo scarto massimo, in pixel, fra un angolo dell'engine e il contorno del campo. */
  private fun worstGap(shape: Shape, cornerIndex: Int, engineSmoothing: Float, fieldSmoothing: Float): Double =
    engineOutlineSamples(shape, engineSmoothing, cornerIndex)
      .maxOf { gapToFieldContour(shape, it[0], it[1], fieldSmoothing) }

  @Test
  fun theSmoothedFieldFollowsTheEngineCurveWithinHalfAPercentOfTheRadius() {
    val ios = ContinuousCornerShape.IosSmoothing
    // I raggi di FluidRadius (Small, Control, Card, Group, Sheet) e il 28 dei menu, in dp.
    val radiiDp = listOf(10f, 12f, 18f, 22f, 28f, 38f)
    // Tab S9 e S25.
    val densities = listOf(2.125f, 3f)
    // Il lato corto in multipli del raggio: 2 è la capsula (smoothing zero), 3,2 lascia allo smoothing tutto
    // il lato che chiede, in mezzo il lato lo riduce (2,5 r -> 0,25; 3 r -> 0,5). 6,4 è un pannello.
    val shortSides = listOf(2f, 2.2f, 2.5f, 3f, 3.2f, 6.4f)
    var worst = 0.0
    var worstWhere = ""
    val table = StringBuilder()
    for (density in densities) {
      for (radiusDp in radiiDp) {
        val radius = radiusDp * density
        val row = StringBuilder("  r ${radiusDp.toInt()} dp x $density = ${"%.1f".format(radius)} px:")
        for (shortSide in shortSides) {
          val shape = Shape(width = radius * shortSide * 1.6f, height = radius * shortSide, radius = radius)
          val spent = continuousCornerSmoothing(radius, shape.height, ios)
          val gap = (0 until 4).maxOf { worstGap(shape, it, ios, ios) }
          val share = gap / radius
          row.append("  s ${"%.2f".format(spent)}: ${"%.3f".format(share * 100)}% (${"%.2f".format(gap)} px)")
          if (share > worst) {
            worst = share
            worstWhere = "raggio $radiusDp dp a densità $density, lato corto $shortSide r"
          }
        }
        table.append(row).append('\n')
      }
    }
    println("Scarto fra il contorno del campo raccordato e la curva dell'engine, per smoothing speso:\n$table")
    println("Scarto massimo: ${"%.3f".format(worst * 100)}% del raggio ($worstWhere)")
    // Misurato in F1A.1: 0,44% a smoothing pieno. Il piano (3.7) dichiara 0,5%: è la soglia.
    assertTrue(
      "Il campo raccordato si scosta dalla curva dell'engine del ${worst * 100}% del raggio ($worstWhere): " +
        "il piano ne dichiara al più 0,5",
      worst <= 0.005,
    )
    // Una soglia che non misura niente passerebbe anche con il campo sbagliato: l'ottimo è 0,44%, e se qui
    // esce molto meno il test sta guardando un'altra curva.
    assertTrue("Scarto massimo ${worst * 100}%: troppo piccolo per essere la curva dell'engine", worst > 0.0035)
  }

  @Test
  fun aCircularCornerMissesTheEngineCurveByMoreThanOnePercent() {
    // Il termine di paragone, e la prova che la misura vede la differenza: con l'angolo circolare al raggio
    // nominale (pieceSmooth zero) lo scarto è quello che il piano riporta, 1,25% del raggio.
    val ios = ContinuousCornerShape.IosSmoothing
    for (radius in listOf(38f * 3f, 22f * 2.125f)) {
      val shape = Shape(width = radius * 8f, height = radius * 6f, radius = radius)
      val circular = (0 until 4).maxOf { worstGap(shape, it, ios, 0f) } / radius
      val smoothed = (0 until 4).maxOf { worstGap(shape, it, ios, ios) } / radius
      println("raggio $radius px: cerchio ${"%.3f".format(circular * 100)}%, raccordo ${"%.3f".format(smoothed * 100)}%")
      assertEquals(0.0125, circular, 0.0006)
      assertTrue("Il raccordo ($smoothed) deve più che dimezzare lo scarto del cerchio ($circular)", smoothed < circular / 2.5)
    }
  }

  @Test
  fun aCapsuleOfTheEngineIsTheSemicircleOfTheField() {
    // Raggio = mezzo lato corto: l'engine spende smoothing zero e disegna un quarto di cerchio per angolo
    // (come una cubica sola: esatta a meno di 2,7 decimillesimi del raggio). Il campo, a cui arriva lo
    // smoothing della forma, deve fare lo stesso semicerchio.
    val ios = ContinuousCornerShape.IosSmoothing
    for (height in listOf(44f * 2.125f, 48f * 3f, 62f * 3f)) {
      val shape = Shape(width = height * 4f, height = height, radius = height / 2f)
      assertEquals(0f, continuousCornerSmoothing(height / 2f, height, ios), 0f)
      val gap = (0 until 4).maxOf { worstGap(shape, it, ios, ios) } / (height / 2f)
      println("capsula alta $height px: scarto ${"%.4f".format(gap * 100)}% del raggio")
      assertTrue("Una capsula dell'engine e quella del campo differiscono del ${gap * 100}% del raggio", gap < 0.0005)
    }
    // E un raggio chiesto più grande del mezzo lato (CornerSize(50) su un lato dispari, o 999) è la stessa capsula.
    val shape = Shape(width = 400f, height = 93f, radius = 999f)
    val gap = (0 until 4).maxOf { worstGap(shape, it, ios, ios) } / 46.5
    assertTrue("Raggio oltre il mezzo lato: scarto ${gap * 100}% del raggio", gap < 0.0005)
  }

  @Test
  fun everyCornerOfASheetFollowsItsOwnRadius() {
    val ios = ContinuousCornerShape.IosSmoothing
    // Un foglio: arrotondato in alto, vivo in basso. E una sagoma con quattro raggi diversi.
    val sheet = Shape(360f * 3f, 300f * 3f, floatArrayOf(114f, 114f, 0f, 0f))
    val mixed = Shape(240f * 3f, 176f * 3f, floatArrayOf(114f, 66f, 36f, 0f))
    for (shape in listOf(sheet, mixed)) {
      for (corner in 0 until 4) {
        val radius = shape.radii[corner]
        val gap = worstGap(shape, corner, ios, ios)
        if (radius == 0f) {
          // Un angolo vivo: il path passa per il vertice, e il vertice sta sul contorno del campo.
          assertTrue("Angolo vivo $corner: il campo dista $gap px dal vertice", gap < 1e-3)
        } else {
          assertTrue(
            "Angolo $corner (raggio $radius): scarto ${gap / radius * 100}% del raggio",
            gap / radius <= 0.005,
          )
        }
      }
    }
  }

  @Test
  fun theFieldSpendsTheSameSmoothingAsTheEmitter() {
    // La riduzione dello smoothing per mancanza di lato è scritta due volte, in Kotlin per il path e in AGSL
    // per il campo: se divergono, una pillola quasi capsula ha due curve.
    for (radius in listOf(0f, 0.00005f, 4f, 21.25f, 30f, 46.75f, 80.75f, 114f, 500f)) {
      for (minDimension in listOf(0f, 1f, 40f, 93.5f, 144f, 228f, 364.8f, 900f)) {
        for (smoothing in listOf(-0.5f, 0f, 0.3f, 0.6f, 1f, 1.7f)) {
          val budget = minDimension / 2f
          val clamped = min(radius, budget).coerceAtLeast(0f)
          val engine = continuousCornerSmoothing(radius, minDimension, smoothing)
          val field = GlassFieldGeometry.effectiveSmoothing(clamped, budget, smoothing)
          // Sotto un decimillesimo di pixel di raggio il campo lo tratta da angolo vivo: lì lo smoothing
          // non sposta niente.
          if (clamped > 0.0001f) {
            assertEquals("raggio $radius, lato $minDimension, smoothing $smoothing", engine, field, 1e-6f)
          } else {
            assertEquals(0f, field, 0f)
          }
        }
      }
    }
  }

  @Test
  fun theFieldContourTurnsTheCornerWithoutBumpsOrKinks() {
    // Si cammina sul contorno del campo attorno a un angolo raccordato, a passi d'angolo uguali visti dal
    // centro del raccordo: la direzione deve girare sempre dalla stessa parte (niente gobbe) e la svolta
    // non deve fare salti da un passo all'altro (niente spigoli). Misurato: svolta fra 0,06 e 0,68 gradi a
    // passo, salto fra svolte vicine 0,023 gradi.
    val ios = ContinuousCornerShape.IosSmoothing
    val shape = Shape(720f, 528f, 114f)
    val reach = GlassFieldGeometry.cornerReach(114f, ios)
    val centreX = shape.width - reach.toDouble()
    val centreY = shape.height - reach.toDouble()
    val points = ArrayList<DoubleArray>()
    for (step in 0..200) {
      val angle = step / 200.0 * (PI / 2)
      var inside = 0.0
      var outside = 2.2 * reach
      repeat(48) {
        val middle = (inside + outside) / 2
        if (fieldDistance(shape, centreX + middle * cos(angle), centreY + middle * sin(angle), ios) < 0) {
          inside = middle
        } else {
          outside = middle
        }
      }
      points += doubleArrayOf(centreX + inside * cos(angle), centreY + inside * sin(angle))
    }
    var previousHeading = Double.NaN
    var previousTurn = Double.NaN
    var smallestTurn = Double.MAX_VALUE
    var largestTurn = -Double.MAX_VALUE
    var largestJump = 0.0
    for (i in 1 until points.size) {
      val heading = Math.toDegrees(atan2(points[i][1] - points[i - 1][1], points[i][0] - points[i - 1][0]))
      if (!previousHeading.isNaN()) {
        var turn = heading - previousHeading
        if (turn > 180) turn -= 360
        if (turn < -180) turn += 360
        smallestTurn = minOf(smallestTurn, turn)
        largestTurn = maxOf(largestTurn, turn)
        if (!previousTurn.isNaN()) largestJump = maxOf(largestJump, abs(turn - previousTurn))
        previousTurn = turn
      }
      previousHeading = heading
    }
    println("Contorno raccordato: svolta a passo fra $smallestTurn e $largestTurn gradi, salto massimo $largestJump")
    assertTrue("Il contorno ha una gobba: svolta fra $smallestTurn e $largestTurn gradi", smallestTurn * largestTurn > 0)
    assertTrue("Il contorno ha uno spigolo: una svolta di $largestTurn gradi in un passo", maxOf(abs(smallestTurn), abs(largestTurn)) < 1.5)
    assertTrue("La curvatura del contorno salta: $largestJump gradi fra due passi", largestJump < 0.25)
  }

  @Test
  fun thePurePointsAreTheOnesTheEmitterAlwaysDrew() {
    // `continuousCornerPoints` è uscita dall'emettitore del path senza cambiare un conto: qui accanto c'è
    // l'emettitore com'era nella 3.1.2, e i punti devono essere uguali al bit, per ogni angolo e direzione.
    val frames = listOf(
      Triple(Offset(900f, 0f), right, down),
      Triple(Offset(900f, 640f), down, left),
      Triple(Offset(0f, 640f), left, up),
      Triple(Offset(0f, 0f), up, right),
      Triple(Offset(123.5f, 77.25f), right, down),
    )
    var compared = 0
    for ((corner, incoming, outgoing) in frames) {
      for (radius in listOf(0f, -3f, 0.5f, 21.25f, 30f, 46.75f, 80.75f, 114f, 320f, 5000f)) {
        for (minDimension in listOf(0f, 44f, 93.5f, 144f, 228f, 640f)) {
          for (smoothing in listOf(-1f, 0f, 0.25f, 0.6f, 1f, 2f)) {
            val expected = cornerAsInThreeOneTwo(corner, incoming, outgoing, radius, minDimension, smoothing)
            val actual = continuousCornerPoints(corner, incoming, outgoing, radius, minDimension, smoothing)
            assertArrayEquals(
              "angolo $corner, raggio $radius, lato $minDimension, smoothing $smoothing",
              expected, actual, 0f,
            )
            compared++
          }
        }
      }
    }
    assertEquals(5 * 10 * 6 * 6, compared)
    // Con il raggio: partenza più tre cubiche. Senza: la sola partenza, cioè l'angolo vivo.
    assertEquals(ContinuousCornerPointFloats, continuousCornerPoints(Offset(900f, 0f), right, down, 30f, 640f, 0.6f).size)
    assertEquals(2, continuousCornerPoints(Offset(900f, 0f), right, down, 0f, 640f, 0.6f).size)
    // La partenza sta sul lato entrante, a (1 + s) r dal vertice; l'arrivo sul lato uscente, alla stessa distanza.
    val points = continuousCornerPoints(Offset(900f, 0f), right, down, 30f, 640f, 0.6f)
    assertEquals(900f - 48f, points[0], 1e-4f)
    assertEquals(0f, points[1], 0f)
    assertEquals(900f, points[18], 1e-4f)
    assertEquals(48f, points[19], 1e-4f)
  }

  /** `Path.continuousCorner` della 3.1.2, riga per riga, con i punti raccolti invece che disegnati. */
  private fun cornerAsInThreeOneTwo(
    corner: Offset,
    incoming: Offset,
    outgoing: Offset,
    requestedRadius: Float,
    minDimension: Float,
    smoothing: Float,
  ): FloatArray {
    val sqrt2 = sqrt(2f)
    val degToRad = (PI / 180.0).toFloat()
    val budget = minDimension / 2f
    val radius = min(requestedRadius, budget).coerceAtLeast(0f)
    val s = if (radius <= 0f) {
      0f
    } else {
      smoothing.coerceIn(0f, 1f).coerceAtMost((budget / radius - 1f).coerceAtLeast(0f))
    }

    val start = corner - incoming * ((1f + s) * radius)
    if (radius <= 0f) return floatArrayOf(start.x, start.y)

    val p = (1f + s) * radius
    val arcSweepDeg = 90f * (1f - s)
    val arcChord = sin(arcSweepDeg / 2f * degToRad) * radius * sqrt2
    val alphaDeg = (90f - arcSweepDeg) / 2f
    val betaDeg = 45f * s
    val handle = radius * tan(betaDeg / 2f * degToRad)
    val c = handle * cos(alphaDeg * degToRad)
    val d = c * tan(alphaDeg * degToRad)
    val b = (p - arcChord - c - d) / 3f
    val a = 2f * b

    fun local(alongIncoming: Float, alongOutgoing: Float): Offset =
      start + incoming * alongIncoming + outgoing * alongOutgoing

    val arcStart = local(a + b + c, d)
    val arcEnd = arcStart + incoming * arcChord + outgoing * arcChord
    val tangentAt = { deg: Float -> incoming * cos(deg * degToRad) + outgoing * sin(deg * degToRad) }
    val k = 4f / 3f * tan(arcSweepDeg / 4f * degToRad) * radius
    val all = listOf(
      start,
      local(a, 0f), local(a + b, 0f), arcStart,
      arcStart + tangentAt(alphaDeg) * k, arcEnd - tangentAt(alphaDeg + arcSweepDeg) * k, arcEnd,
      local(p, d + arcChord + c), local(p, d + arcChord + b + c), local(p, p),
    )
    val out = FloatArray(all.size * 2)
    for ((index, point) in all.withIndex()) {
      out[index * 2] = point.x
      out[index * 2 + 1] = point.y
    }
    return out
  }
}
