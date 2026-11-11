package dev.antigravity.fluidengine.ui.glass.field

import dev.antigravity.fluidengine.ui.fluidphysics.sdRoundedRect
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gli specchi del nucleo del campo ([GlassFieldGeometry]) e il contratto dei blocchi AGSL che il corpo include.
 *
 * Lo shader non si mette sotto JUnit, la sua aritmetica sì: [GlassFieldGeometry] è la trascrizione riga per
 * riga di `GlassFieldKernel.kt`, e ciò che qui torna vale per la sagoma di ogni vetro `Liquid`. Le soglie non
 * sono a occhio: le stesse grandezze sono calcolate in `artifacts/liquid-glass/bench/f1a-1/taratura_test.py`
 * con un port a 32 bit, e ogni soglia sta sopra il valore misurato lì con un margine dichiarato.
 */
class GlassFieldGeometryTest {

  private val sample = GlassFieldSample()

  private fun uniform(radius: Float) = floatArrayOf(radius, radius, radius, radius)

  private fun distance(
    relX: Float,
    relY: Float,
    halfWidth: Float,
    halfHeight: Float,
    radii: FloatArray,
    smoothing: Float,
  ): Float {
    GlassFieldGeometry.slab(relX, relY, halfWidth, halfHeight, radii, 0, smoothing, sample)
    return sample.distance
  }

  /** Gli uniform di un campo, nella forma in cui lo shader li riceve. */
  private class Pieces(capacity: Int) {
    val rects = FloatArray(capacity * GlassFieldGeometry.FloatsPerPiece)
    val radii = FloatArray(capacity * GlassFieldGeometry.FloatsPerPiece)
    val blend = FloatArray(capacity)
    val smooth = FloatArray(capacity)
    var count = 0

    fun add(
      centerX: Float,
      centerY: Float,
      halfWidth: Float,
      halfHeight: Float,
      radius: Float,
      blendRadius: Float = 0f,
      smoothing: Float = 0f,
    ): Pieces {
      val at = count * GlassFieldGeometry.FloatsPerPiece
      rects[at] = centerX
      rects[at + 1] = centerY
      rects[at + 2] = halfWidth
      rects[at + 3] = halfHeight
      for (k in 0 until GlassFieldGeometry.FloatsPerPiece) radii[at + k] = radius
      blend[count] = blendRadius
      smooth[count] = smoothing
      count++
      return this
    }
  }

  private fun field(pieces: Pieces, x: Float, y: Float, normalSpread: Float = GlassFieldNormalSpread): GlassFieldSample {
    val out = GlassFieldSample()
    GlassFieldGeometry.field(
      pieces.count, pieces.rects, pieces.radii, pieces.blend, pieces.smooth, x, y, out, normalSpread,
    )
    return out
  }

  // --- Un pezzo ---------------------------------------------------------------------------------

  @Test
  fun aRoundedRectHasTheDistancesGeometrySays() {
    val radii = uniform(20f)
    // 200 x 100 centrato nell'origine: il centro dista mezzo lato corto dal bordo più vicino.
    assertEquals(-50f, distance(0f, 0f, 100f, 50f, radii, 0f), 1e-4f)
    assertEquals(0f, distance(100f, 0f, 100f, 50f, radii, 0f), 1e-4f)
    assertEquals(10f, distance(110f, 0f, 100f, 50f, radii, 0f), 1e-4f)
    assertEquals(-7f, distance(0f, -43f, 100f, 50f, radii, 0f), 1e-4f)
    // Sull'arco: il centro dell'angolo è (80, 30), il punto a 45 gradi sta sul contorno.
    val onArc = 20f * sqrt(0.5f)
    assertEquals(0f, distance(80f + onArc, 30f + onArc, 100f, 50f, radii, 0f), 1e-4f)
    // Il vertice del rettangolo sta fuori dall'arco di r * (sqrt(2) - 1).
    assertEquals(20f * (sqrt(2f) - 1f), distance(100f, 50f, 100f, 50f, radii, 0f), 1e-4f)
  }

  @Test
  fun withoutSmoothingASlabIsTheRoundedRectOfToday() {
    // Il campo nuovo parte da quello di Fluid-physics: con l'angolo circolare deve dire lo stesso numero.
    val random = Random(11)
    var worst = 0f
    repeat(4000) {
      val halfWidth = 10f + random.nextFloat() * 300f
      val halfHeight = 10f + random.nextFloat() * 300f
      val radius = random.nextFloat() * min(halfWidth, halfHeight)
      val x = (random.nextFloat() * 2.6f - 1.3f) * halfWidth
      val y = (random.nextFloat() * 2.6f - 1.3f) * halfHeight
      val mine = distance(x, y, halfWidth, halfHeight, uniform(radius), 0f)
      val today = sdRoundedRect(x, y, 0f, 0f, halfWidth, halfHeight, radius)
      worst = maxOf(worst, abs(mine - today))
    }
    assertTrue("Il campo senza raccordo si scosta di $worst px dal rettangolo arrotondato di oggi", worst < 1e-3f)
  }

  @Test
  fun theRadiusIsThatOfTheQuadrantTheCentredPointFallsIn() {
    // L'ordine è quello di RoundRect: alto-sinistra, alto-destra, basso-destra, basso-sinistra, con y verso
    // il basso. È il difetto di F0.2: con la coordinata del layer al posto di quella centrata vinceva
    // sempre l'angolo in basso a destra.
    assertEquals(10f, GlassFieldGeometry.radiusAt(-5f, -5f, 10f, 20f, 30f, 40f), 0f)
    assertEquals(20f, GlassFieldGeometry.radiusAt(5f, -5f, 10f, 20f, 30f, 40f), 0f)
    assertEquals(30f, GlassFieldGeometry.radiusAt(5f, 5f, 10f, 20f, 30f, 40f), 0f)
    assertEquals(40f, GlassFieldGeometry.radiusAt(-5f, 5f, 10f, 20f, 30f, 40f), 0f)

    val mixed = floatArrayOf(10f, 20f, 30f, 40f)
    val signs = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
    for ((index, sign) in signs.withIndex()) {
      for (smoothing in listOf(0f, 0.6f)) {
        // Il vertice del rettangolo: lì conta solo il raggio di quell'angolo.
        val x = sign.first * 100f
        val y = sign.second * 60f
        val expected = distance(x, y, 100f, 60f, uniform(mixed[index]), smoothing)
        assertEquals(expected, distance(x, y, 100f, 60f, mixed, smoothing), 0f)
        assertTrue("Il vertice di un angolo arrotondato sta fuori dalla sagoma", expected > 0f)
      }
    }
  }

  @Test
  fun aSlabIsSymmetricWhenItsCornersAre() {
    val random = Random(3)
    val radii = uniform(34f)
    val a = GlassFieldSample()
    val b = GlassFieldSample()
    repeat(500) {
      val x = random.nextFloat() * 200f
      val y = random.nextFloat() * 140f
      for (smoothing in listOf(0f, 0.6f)) {
        GlassFieldGeometry.slab(x, y, 150f, 100f, radii, 0, smoothing, a)
        GlassFieldGeometry.slab(-x, y, 150f, 100f, radii, 0, smoothing, b)
        assertEquals(a.distance, b.distance, 0f)
        assertEquals(a.normalX, -b.normalX, 0f)
        assertEquals(a.normalY, b.normalY, 0f)
        GlassFieldGeometry.slab(x, -y, 150f, 100f, radii, 0, smoothing, b)
        assertEquals(a.distance, b.distance, 0f)
        assertEquals(a.normalX, b.normalX, 0f)
        assertEquals(a.normalY, -b.normalY, 0f)
      }
    }
    // Un quadrato è uguale a se stesso anche scambiando gli assi.
    repeat(500) {
      val x = random.nextFloat() * 160f
      val y = random.nextFloat() * 160f
      GlassFieldGeometry.slab(x, y, 120f, 120f, radii, 0, 0.6f, a)
      GlassFieldGeometry.slab(y, x, 120f, 120f, radii, 0, 0.6f, b)
      assertEquals(a.distance, b.distance, 1e-4f)
      assertEquals(a.normalX, b.normalY, 1e-5f)
    }
  }

  @Test
  fun aCapsuleStaysAnExactSemicircleWhateverSmoothingIsAsked() {
    // Raggio = mezzo lato corto: allo smoothing non resta lato da spendere, e il campo deve essere quello
    // circolare al bit. Una pillola con le estremità «quasi» tonde si vede subito.
    val a = GlassFieldSample()
    val b = GlassFieldSample()
    val random = Random(5)
    for (radii in listOf(uniform(24f), uniform(999f))) {
      repeat(2000) {
        val x = (random.nextFloat() * 2.4f - 1.2f) * 120f
        val y = (random.nextFloat() * 2.4f - 1.2f) * 24f
        GlassFieldGeometry.slab(x, y, 120f, 24f, radii, 0, 0f, a)
        GlassFieldGeometry.slab(x, y, 120f, 24f, radii, 0, 0.6f, b)
        assertEquals(a.distance, b.distance, 0f)
        assertEquals(a.normalX, b.normalX, 0f)
        assertEquals(a.normalY, b.normalY, 0f)
      }
    }
    assertEquals(0f, GlassFieldGeometry.effectiveSmoothing(24f, 24f, 0.6f), 0f)
    // Sull'estremità: il semicerchio di raggio 24 attorno a (96, 0).
    assertEquals(0f, distance(96f + 24f * cos(0.7f), 24f * sin(0.7f), 120f, 24f, uniform(24f), 0.6f), 1e-4f)
  }

  @Test
  fun theSmoothedCornerLeavesTheCircleWithoutAStep() {
    // In viaggio pieceSmooth sale da zero al valore di riposo: appena sopra zero la sagoma deve essere
    // ancora quella circolare, o l'arrivo di ogni pannello avrebbe uno scatto.
    val random = Random(17)
    var worst = 0f
    repeat(4000) {
      val x = (random.nextFloat() * 2.4f - 1.2f) * 150f
      val y = (random.nextFloat() * 2.4f - 1.2f) * 100f
      val circular = distance(x, y, 150f, 100f, uniform(40f), 0f)
      val barely = distance(x, y, 150f, 100f, uniform(40f), 0.001f)
      worst = maxOf(worst, abs(circular - barely))
    }
    assertTrue("Fra smoothing 0 e 0,001 la distanza salta di $worst px", worst < 0.01f)
    // E cresce in modo regolare: un centesimo di smoothing in più non sposta il contorno di un decimo di pixel.
    var step = 0f
    for (i in 0 until 60) {
      val s = i / 100f
      val onDiagonal = 150f - 40f + 40f * sqrt(0.5f)
      step = maxOf(
        step,
        abs(
          distance(onDiagonal, 100f - 40f + 40f * sqrt(0.5f), 150f, 100f, uniform(40f), s) -
            distance(onDiagonal, 100f - 40f + 40f * sqrt(0.5f), 150f, 100f, uniform(40f), s + 0.01f),
        ),
      )
    }
    assertTrue("Un centesimo di smoothing sposta il contorno di $step px sulla diagonale", step < 0.1f)
  }

  @Test
  fun theFieldOfASlabHasNoJumps() {
    // Zone d'angolo, quadranti con raggi diversi, il passaggio fra la distanza corretta e la norma: da
    // nessuna parte la distanza può fare un salto. Misurato: al più 1,03 px per pixel (soglia 1,25, più il
    // rumore dei 32 bit su un passo di 0,05 px).
    val random = Random(7)
    var worst = 0f
    repeat(300) {
      val halfWidth = 20f + random.nextFloat() * 280f
      val halfHeight = 20f + random.nextFloat() * 280f
      val limit = 1.3f * min(halfWidth, halfHeight)
      val radii = floatArrayOf(
        random.nextFloat() * limit, random.nextFloat() * limit, random.nextFloat() * limit, random.nextFloat() * limit,
      )
      val smoothing = listOf(0f, 0.3f, 0.6f, 1f)[random.nextInt(4)]
      repeat(400) {
        val x = (random.nextFloat() * 2.6f - 1.3f) * halfWidth
        val y = (random.nextFloat() * 2.6f - 1.3f) * halfHeight
        val angle = random.nextFloat() * 6.2831855f
        val here = distance(x, y, halfWidth, halfHeight, radii, smoothing)
        val there = distance(x + 0.05f * cos(angle), y + 0.05f * sin(angle), halfWidth, halfHeight, radii, smoothing)
        worst = maxOf(worst, abs(there - here) / 0.05f)
      }
    }
    assertTrue("La distanza cambia di $worst px per pixel: c'è un salto", worst < 1.25f + 0.02f)
  }

  @Test
  fun theNormalIsTheGradientOfTheDistance() {
    // Con normalSpread 1 la normale deve essere la direzione in cui la distanza cresce: lo si controlla con
    // le differenze finite. Vicino al contorno (dove stanno filo e riflessi) entro un grado, e la distanza
    // cresce di un pixel per pixel: è ciò che rende il filo di spessore costante lungo l'angolo raccordato.
    // Misurato: 0,08 gradi senza raccordo, 0,49 con; pendenza fra 0,992 e 1.
    val radii = uniform(40f)
    val out = GlassFieldSample()
    for (smoothing in listOf(0f, 0.6f)) {
      val random = Random(23)
      var worstNear = 0.0
      var worstAnywhere = 0.0
      var slopeLow = 1.0
      var slopeHigh = 1.0
      repeat(20000) {
        val x = (random.nextFloat() * 2.4f - 1.2f) * 160f
        val y = (random.nextFloat() * 2.4f - 1.2f) * 110f
        // Sulle diagonali interne e nel centro di un angolo il campo ha una piega vera: lì non c'è gradiente.
        if (abs((abs(x) - 160f) - (abs(y) - 110f)) < 2f) return@repeat
        val h = 0.25f
        val gx = (distance(x + h, y, 160f, 110f, radii, smoothing) - distance(x - h, y, 160f, 110f, radii, smoothing)) / (2.0 * h)
        val gy = (distance(x, y + h, 160f, 110f, radii, smoothing) - distance(x, y - h, 160f, 110f, radii, smoothing)) / (2.0 * h)
        val length = hypot(gx, gy)
        if (length < 0.5) return@repeat
        GlassFieldGeometry.slab(x, y, 160f, 110f, radii, 0, smoothing, out, normalSpread = 1f)
        assertEquals(1.0, hypot(out.normalX.toDouble(), out.normalY.toDouble()), 1e-4)
        val cosine = ((gx * out.normalX + gy * out.normalY) / length).coerceIn(-1.0, 1.0)
        val degrees = Math.toDegrees(acos(cosine))
        worstAnywhere = maxOf(worstAnywhere, degrees)
        if (abs(out.distance) < 3f) {
          worstNear = maxOf(worstNear, degrees)
          slopeLow = minOf(slopeLow, length)
          slopeHigh = maxOf(slopeHigh, length)
        }
      }
      println(
        "smoothing $smoothing: normale contro gradiente, vicino al contorno $worstNear gradi, ovunque " +
          "$worstAnywhere gradi; pendenza della distanza sul contorno fra $slopeLow e $slopeHigh",
      )
      assertTrue("smoothing $smoothing: vicino al contorno la normale sbaglia di $worstNear gradi", worstNear < 1.0)
      assertTrue("smoothing $smoothing: la normale sbaglia di $worstAnywhere gradi", worstAnywhere < 12.0)
      assertTrue(
        "smoothing $smoothing: sul contorno la distanza cresce di $slopeLow..$slopeHigh px per pixel",
        slopeLow > 0.97 && slopeHigh < 1.03,
      )
    }
  }

  @Test
  fun theLensNormalIsUnitAndTurnsTheCornerEarlierThanTheOutline() {
    val out = GlassFieldSample()
    val radii = uniform(40f)
    // Sui lati è l'asse, esatto.
    GlassFieldGeometry.slab(0f, -95f, 150f, 100f, radii, 0, 0.6f, out)
    assertEquals(0f, out.normalX, 0f)
    assertEquals(-1f, out.normalY, 0f)
    GlassFieldGeometry.slab(-149f, 10f, 150f, 100f, radii, 0, 0.6f, out)
    assertEquals(-1f, out.normalX, 0f)
    assertEquals(0f, out.normalY, 0f)
    // Appena prima dell'angolo (a 50 px dal vertice, con un raggio di 40) il contorno è ancora dritto, ma
    // la normale allargata sta già girando: è ciò che toglie la strizzata dal centro dell'angolo.
    GlassFieldGeometry.slab(100f, -99f, 150f, 100f, radii, 0, 0f, out, normalSpread = 1f)
    assertEquals(0f, out.normalX, 0f)
    GlassFieldGeometry.slab(100f, -99f, 150f, 100f, radii, 0, 0f, out)
    assertTrue("La normale allargata non gira prima del contorno: x = ${out.normalX}", out.normalX > 0.05f)
    val random = Random(29)
    repeat(4000) {
      val x = (random.nextFloat() * 2.4f - 1.2f) * 150f
      val y = (random.nextFloat() * 2.4f - 1.2f) * 100f
      GlassFieldGeometry.slab(x, y, 150f, 100f, radii, 0, if (it % 2 == 0) 0f else 0.6f, out)
      assertEquals(1f, hypot(out.normalX, out.normalY), 1e-4f)
    }
    // L'allargamento non supera mai mezzo lato corto: su una capsula la normale è quella vera.
    val capsule = uniform(24f)
    val exact = GlassFieldSample()
    repeat(1000) {
      val x = (random.nextFloat() * 2.4f - 1.2f) * 120f
      val y = (random.nextFloat() * 2.4f - 1.2f) * 24f
      GlassFieldGeometry.slab(x, y, 120f, 24f, capsule, 0, 0f, out)
      GlassFieldGeometry.slab(x, y, 120f, 24f, capsule, 0, 0f, exact, normalSpread = 1f)
      assertEquals(exact.normalX, out.normalX, 0f)
      assertEquals(exact.normalY, out.normalY, 0f)
    }
  }

  // --- La fusione -------------------------------------------------------------------------------

  @Test
  fun theSmoothMinimumIsAUnionThatNeverGoesAboveTheTrueOne() {
    val random = Random(31)
    repeat(4000) {
      val a = random.nextFloat() * 200f - 100f
      val b = random.nextFloat() * 200f - 100f
      val k = random.nextFloat() * 40f
      val fused = GlassFieldGeometry.smoothMin(a, b, k)
      assertTrue("smin($a, $b, $k) = $fused sta sopra il minimo", fused <= min(a, b) + 1e-4f)
      // Più lontani del raggio di fusione, i due campi non si sentono.
      if (abs(a - b) >= k) assertEquals(min(a, b), fused, 1e-4f)
      // E mai più di un quarto del raggio sotto il minimo: è la gobba massima del ponte.
      assertTrue(fused >= min(a, b) - k / 4f - 1e-4f)
    }
    // Dove i due campi coincidono l'unione è gonfiata di un quarto del raggio: per questo a riposo va a zero.
    assertEquals(7f - 5f, GlassFieldGeometry.smoothMin(7f, 7f, 20f), 1e-5f)
    // A raggio zero è l'unione netta.
    assertEquals(-3f, GlassFieldGeometry.smoothMin(-3f, 4f, 0f), 1e-4f)
    assertEquals(-3f, GlassFieldGeometry.smoothMin(4f, -3f, 0f), 1e-4f)
    assertEquals(1f, GlassFieldGeometry.fusionWeight(-10f, 20f, 5f), 0f)
    assertEquals(0f, GlassFieldGeometry.fusionWeight(20f, -10f, 5f), 0f)
    assertEquals(0.5f, GlassFieldGeometry.fusionWeight(3f, 3f, 5f), 0f)
  }

  @Test
  fun twoPiecesWithinReachGrowABridgeAndFarApartStayThemselves() {
    // Due tondi di raggio 20 a 50 px fra i centri (10 px di varco), raggio di fusione 20.
    val fused = Pieces(2).add(100f, 100f, 20f, 20f, 20f).add(150f, 100f, 20f, 20f, 20f, blendRadius = 20f)
    val apart = Pieces(2).add(100f, 100f, 20f, 20f, 20f).add(150f, 100f, 20f, 20f, 20f, blendRadius = 0f)
    // Nel mezzo del varco ogni pezzo dista 5 px: senza fusione il campo vale 5 (fuori), con la fusione
    // scende di un quarto del raggio e tocca zero: lì nasce il ponte.
    assertEquals(5f, field(apart, 125f, 100f).distance, 1e-3f)
    assertEquals(0f, field(fused, 125f, 100f).distance, 1e-3f)
    assertTrue("Poco sopra il mezzo del varco il ponte non c'è più", field(fused, 125f, 104f).distance > 0f)
    // Lontano dall'altro pezzo, il campo è quello del pezzo.
    assertEquals(-20f, field(fused, 100f, 100f).distance, 1e-3f)
    assertEquals(field(apart, 70f, 100f).distance, field(fused, 70f, 100f).distance, 0f)
    // A riposo (fusione zero) fra due pezzi che non si toccano non c'è materiale: è la scelta di Alessio.
    var x = 121f
    while (x <= 129f) {
      assertTrue(field(apart, x, 100f).distance > 0.5f)
      x += 0.5f
    }
  }

  @Test
  fun theFusedFieldIsContinuousAndItsBlendedNormalIsItsGradient() {
    val fused = Pieces(2).add(100f, 100f, 20f, 20f, 20f).add(150f, 100f, 20f, 20f, 20f, blendRadius = 20f)
    // Lungo la riga dei centri, attraverso tutta la zona di fusione: nessun salto.
    var previous = field(fused, 60f, 100f).distance
    var worstStep = 0f
    var x = 60.05f
    while (x <= 190f) {
      val here = field(fused, x, 100f).distance
      worstStep = maxOf(worstStep, abs(here - previous) / 0.05f)
      previous = here
      x += 0.05f
    }
    assertTrue("Il campo fuso cambia di $worstStep px per pixel lungo la riga dei centri", worstStep < 1.25f + 0.02f)

    // Dentro la zona di fusione la normale mescolata con h è la derivata esatta della distanza fusa (i
    // termini nel gradiente di h si annullano): contro le differenze finite, senza normalizzare. Misurato:
    // errore massimo 0,0001.
    val random = Random(37)
    val first = Pieces(1).add(100f, 100f, 20f, 20f, 20f)
    val second = Pieces(1).add(150f, 100f, 20f, 20f, 20f)
    var worst = 0.0
    var checked = 0
    repeat(6000) {
      val px = 80f + random.nextFloat() * 90f
      val py = 70f + random.nextFloat() * 60f
      val a = field(first, px, py).distance
      val b = field(second, px, py).distance
      if (abs(a - b) >= 19f || hypot(px - 100f, py - 100f) < 2f || hypot(px - 150f, py - 100f) < 2f) return@repeat
      val h = 0.25f
      val gx = (field(fused, px + h, py, 1f).distance - field(fused, px - h, py, 1f).distance) / (2.0 * h)
      val gy = (field(fused, px, py + h, 1f).distance - field(fused, px, py - h, 1f).distance) / (2.0 * h)
      val here = field(fused, px, py, 1f)
      worst = maxOf(worst, hypot(gx - here.normalX, gy - here.normalY))
      checked++
    }
    assertTrue("Troppi pochi punti nella zona di fusione: $checked", checked > 500)
    assertTrue("La normale mescolata si scosta di $worst dal gradiente della distanza fusa", worst < 0.03)
  }

  @Test
  fun theCentreFollowsThePieceThePointBelongsTo() {
    val fused = Pieces(2).add(100f, 100f, 20f, 20f, 20f).add(150f, 100f, 20f, 20f, 20f, blendRadius = 20f)
    // Dentro un pezzo, lontano dall'altro: il suo centro. È ciò che fa di ogni pezzo una lente sua.
    var here = field(fused, 92f, 100f)
    assertEquals(100f, here.centerX, 1e-4f)
    assertEquals(100f, here.centerY, 1e-4f)
    here = field(fused, 158f, 104f)
    assertEquals(150f, here.centerX, 1e-4f)
    // Nel mezzo del ponte i due pezzi pesano uguale.
    here = field(fused, 125f, 100f)
    assertEquals(125f, here.centerX, 1e-3f)
    assertEquals(0.5f, GlassFieldGeometry.fusionWeight(5f, 5f, 20f), 0f)
    // E in mezzo passa dall'uno all'altro senza salti.
    var previous = field(fused, 100f, 100f).centerX
    var x = 100.25f
    while (x <= 150f) {
      val centre = field(fused, x, 100f).centerX
      assertTrue("Il centro torna indietro o salta a x = $x: $previous -> $centre", centre >= previous && centre - previous < 2f)
      previous = centre
      x += 0.25f
    }
  }

  // --- L'uscita su pieceCount, e la variante a un pezzo --------------------------------------------

  @Test
  fun theLoopStopsAtPieceCountAndNeverReadsBeyondIt() {
    // Otto caselle, tre pezzi vivi, spazzatura nelle altre: lo shader esce dal ciclo su pieceCount, e ciò
    // che sta oltre non deve entrare nel campo nemmeno come NaN.
    val exact = Pieces(3)
      .add(60f, 60f, 40f, 24f, 24f)
      .add(150f, 60f, 30f, 30f, 12f, blendRadius = 16f, smoothing = 0.6f)
      .add(100f, 140f, 80f, 50f, 22f, blendRadius = 8f, smoothing = 0.6f)
    val padded = Pieces(GlassFieldMaxPieces)
      .add(60f, 60f, 40f, 24f, 24f)
      .add(150f, 60f, 30f, 30f, 12f, blendRadius = 16f, smoothing = 0.6f)
      .add(100f, 140f, 80f, 50f, 22f, blendRadius = 8f, smoothing = 0.6f)
    for (i in 3 * GlassFieldGeometry.FloatsPerPiece until padded.rects.size) {
      padded.rects[i] = Float.NaN
      padded.radii[i] = Float.NaN
    }
    for (i in 3 until GlassFieldMaxPieces) {
      padded.blend[i] = Float.NaN
      padded.smooth[i] = Float.NaN
    }
    // Il raggio di fusione del pezzo zero non si legge: non c'è niente prima a cui fonderlo.
    padded.blend[0] = Float.NaN
    val random = Random(41)
    repeat(3000) {
      val x = random.nextFloat() * 220f
      val y = random.nextFloat() * 220f
      val a = field(exact, x, y)
      val b = field(padded, x, y)
      assertEquals(a.distance, b.distance, 0f)
      assertEquals(a.normalX, b.normalX, 0f)
      assertEquals(a.normalY, b.normalY, 0f)
      assertEquals(a.centerX, b.centerX, 0f)
      assertEquals(a.centerY, b.centerY, 0f)
    }
    // Con due soli pezzi vivi il terzo non c'è: dentro di lui il campo dice «fuori».
    padded.count = 2
    assertTrue(field(padded, 100f, 170f).distance > 0f)
    assertTrue(field(exact, 100f, 170f).distance < 0f)
    // Zero pezzi: campo vuoto, la distanza che lo shader restituisce senza entrare nel ciclo.
    padded.count = 0
    assertEquals(GlassFieldGeometry.Empty, field(padded, 100f, 100f).distance, 0f)
    // Più pezzi del tetto: il ciclo si ferma al tetto, come il limite costante dello shader.
    assertEquals(8, GlassFieldMaxPieces)
  }

  @Test
  fun onePieceIsTheSameFieldInTheTwoVariants() {
    // GlassFieldKernel1 non ha ciclo: restituisce il pezzo zero. Il campo a otto con pieceCount 1 deve dare
    // lo stesso numero, o una lastra cambierebbe sagoma entrando in un gruppo. (Sul banco: le scene
    // prova-angolo-variante-1 e -8 sono uguali al pixel.)
    val one = Pieces(GlassFieldMaxPieces).add(128f, 96f, 120f, 88f, 0f, smoothing = 0.6f)
    one.radii[0] = 38f
    one.radii[1] = 22f
    one.radii[2] = 12f
    one.radii[3] = 0f
    val slab = GlassFieldSample()
    val random = Random(43)
    repeat(3000) {
      val x = random.nextFloat() * 256f
      val y = random.nextFloat() * 192f
      val whole = field(one, x, y)
      GlassFieldGeometry.slab(x - 128f, y - 96f, 120f, 88f, one.radii, 0, 0.6f, slab)
      assertEquals(slab.distance, whole.distance, 0f)
      assertEquals(slab.normalX, whole.normalX, 0f)
      assertEquals(slab.normalY, whole.normalY, 0f)
      assertEquals(128f, whole.centerX, 0f)
      assertEquals(96f, whole.centerY, 0f)
      assertEquals(
        whole.distance,
        GlassFieldGeometry.fieldDistance(one.count, one.rects, one.radii, one.blend, one.smooth, x, y),
        0f,
      )
    }
  }

  // --- Il punto interrogato, e i due profili ---------------------------------------------------------

  @Test
  fun theQueriedPointIsTheCoordinateMovedByOffsetAndUnbentByFlex() {
    val identity = floatArrayOf(1f, 0f, 0f, 1f)
    // A riposo: la coordinata della cattura, meno il padding.
    assertEquals(30f, GlassFieldGeometry.fieldPointX(40f, 25f, -10f, -10f, identity, 60f, 60f), 1e-5f)
    assertEquals(15f, GlassFieldGeometry.fieldPointY(40f, 25f, -10f, -10f, identity, 60f, 60f), 1e-5f)
    // Una lastra allargata del doppio in x attorno al centro: la matrice è l'inversa, 0,5 in x. Il pixel
    // che sullo schermo sta 40 px a destra del centro interroga il campo 20 px a destra.
    val wide = floatArrayOf(0.5f, 0f, 0f, 1f)
    assertEquals(80f, GlassFieldGeometry.fieldPointX(100f, 70f, 0f, 0f, wide, 60f, 60f), 1e-5f)
    assertEquals(70f, GlassFieldGeometry.fieldPointY(100f, 70f, 0f, 0f, wide, 60f, 60f), 1e-5f)
    // Il centro della flessione non si muove, qualunque matrice arrivi.
    val shear = floatArrayOf(0.8f, 0.3f, -0.2f, 1.1f)
    assertEquals(60f, GlassFieldGeometry.fieldPointX(60f, 60f, 0f, 0f, shear, 60f, 60f), 1e-5f)
    assertEquals(60f, GlassFieldGeometry.fieldPointY(60f, 60f, 0f, 0f, shear, 60f, 60f), 1e-5f)
  }

  @Test
  fun thicknessProfileAndCoverageHaveTheirEndpoints() {
    assertEquals(0f, GlassFieldGeometry.circleMap(0f), 0f)
    assertEquals(1f, GlassFieldGeometry.circleMap(1f), 0f)
    assertEquals(1f, GlassFieldGeometry.circleMap(1.5f), 0f)
    assertTrue(GlassFieldGeometry.circleMap(0.5f) < 0.5f)
    // La maschera: piena mezzo pixel dentro, vuota mezzo pixel fuori, metà sul contorno.
    assertEquals(1f, GlassFieldGeometry.coverage(-0.5f, 1f), 0f)
    assertEquals(0.5f, GlassFieldGeometry.coverage(0f, 1f), 0f)
    assertEquals(0f, GlassFieldGeometry.coverage(0.5f, 1f), 0f)
    assertEquals(0.5f, GlassFieldGeometry.coverage(0f, 0f), 0f)
  }

  @Test
  fun theCornerLawStartsAtTheCircleAndStaysInsideTheSideTheEngineGrants() {
    assertEquals(40f, GlassFieldGeometry.cornerReach(40f, 0f), 0f)
    assertEquals(2f, GlassFieldGeometry.cornerPower(0f), 0f)
    // A 0,6 il punto trovato dalla ricerca di F1A.1: semiasse 1,197 r, esponente 2,50.
    assertEquals(1.1969f, GlassFieldGeometry.cornerReach(1f, 0.6f), 2e-4f)
    assertEquals(2.5004f, GlassFieldGeometry.cornerPower(0.6f), 2e-4f)
    var previousReach = 1f
    var previousPower = 2f
    for (i in 1..100) {
      val s = i / 100f
      val reach = GlassFieldGeometry.cornerReach(1f, s)
      val power = GlassFieldGeometry.cornerPower(s)
      assertTrue("Il semiasse non cresce con lo smoothing a s = $s", reach > previousReach)
      assertTrue("L'esponente non cresce con lo smoothing a s = $s", power > previousPower)
      // L'emettitore dell'engine concede all'angolo (1 + s) * r di lato: il raccordo deve starci.
      assertTrue("A s = $s il raccordo consuma $reach r, più di ${1f + s} r", reach <= 1f + s)
      previousReach = reach
      previousPower = power
    }
  }

  // --- Il contratto AGSL ----------------------------------------------------------------------------

  @Test
  fun theKernelBlocksKeepTheSignaturesTheBodyAndTheLightAreWrittenAgainst() {
    for (kernel in listOf(GlassFieldKernel1, GlassFieldKernel8)) {
      assertTrue("float2 glassFieldPoint(float2 coord)" in kernel)
      assertTrue("float glassField(float2 p, out float2 normal, out float2 center)" in kernel)
      assertTrue("float3 glassSlab(float2 rel, float2 halfSize, float4 radii, float smoothing)" in kernel)
      assertTrue("float glassRadiusAt(float2 rel, float4 radii)" in kernel)
      assertTrue("float glassCircleMap(float x)" in kernel)
      for (uniform in listOf("float2 offset", "float4 flex", "float2 flexCenter", "float pieceCount")) {
        assertTrue("Manca `uniform $uniform;`", "uniform $uniform;" in kernel)
      }
    }
    for (name in listOf("float4 pieceRect", "float4 pieceRadii", "float pieceBlend", "float pieceSmooth")) {
      assertTrue("uniform $name[1];" in GlassFieldKernel1)
      assertTrue("uniform $name[8];" in GlassFieldKernel8)
    }
    // La variante a uno non ha ciclo; quella a otto ne ha uno solo, a limite costante, con l'uscita su uniform.
    assertFalse("for (" in GlassFieldKernel1)
    assertEquals(1, Regex("""\bfor\s*\(""").findAll(GlassFieldKernel8).count())
    assertTrue("for (int i = 0; i < 8; ++i) {" in GlassFieldKernel8)
    assertTrue("if (float(i) >= pieceCount) break;" in GlassFieldKernel8)

    // La firma di F1A.1 (`half4 glassLight(p, d, normal)`) non c'è più: da F1A.3b la luce ha due parti che si
    // compongono in due modi, e F1A.5 ha tolto la funzione che le fondeva in un colore non valido.
    assertTrue(
      "void glassLightParts(float2 p, float d, float2 normal, bool lit, out half4 glowing, out half4 rim)" in
        GlassLightBlock,
    )
    assertFalse("half4 glassLight(" in GlassLightBlock)
    for (uniform in listOf(
      "uniform float rimWidth;", "layout(color) uniform half4 rimDark;", "uniform float2 reflexDir;",
      "uniform float2 reflexWidths;", "uniform float reflexStrength;", "uniform float reflexDepth;",
      "uniform float reflexFloor;", "uniform float2 glowCenter;", "uniform float glowRadius;", "uniform float glow;",
      "uniform float glowFill;", "layout(color) uniform half4 lightColor;",
    )) {
      assertTrue("Il blocco della luce non dichiara più `$uniform`", uniform in GlassLightBlock)
    }
    // Da F1B.2 l'ombra in viaggio è in tre parti: la legge (uniform e caduta), la maschera del nucleo, la funzione.
    assertTrue("half4 glassTravelShadow(float2 p, float d)" in GlassTravelShadowFunctionBlock)
    for (mask in listOf(GlassShadowMask1, GlassShadowMask8, GlassShadowMaskRing)) {
      assertTrue("float glassShadowMask(float2 q, float sigma, out float seen)" in mask)
    }
    assertTrue("uniform float pieceSoft[8];" in GlassFieldKernel8)
    assertFalse("pieceSoft" in GlassFieldKernel1)
    for (uniform in listOf(
      "uniform float2 shadowOffset;", "uniform float2 shadowRadii;", "uniform float2 shadowAlphas;",
      "layout(color) uniform half4 shadowColor;",
    )) {
      assertTrue("Il blocco dell'ombra non dichiara più `$uniform`", uniform in GlassTravelShadowBlock)
    }
    // `materialize` e `aa` sono del programma che include: se un blocco li dichiarasse, il corpo (che li ha
    // fra i suoi) non compilerebbe più per una doppia dichiarazione.
    for (block in listOf(
      GlassFieldKernel1, GlassFieldKernel8, GlassLightBlock, GlassTravelShadowBlock, GlassShadowMask1, GlassShadowMask8,
      GlassShadowMaskRing, GlassTravelShadowFunctionBlock,
    )) {
      assertFalse(Regex("""uniform\s+\w+\s+(materialize|aa)\s*;""").containsMatchIn(block))
    }
  }

  @Test
  fun theBlocksAreSourcesTheCompilerCanReadAndHaveNoMain() {
    val blocks = mapOf(
      "GlassFieldSlabBlock" to GlassFieldSlabBlock,
      "GlassFieldPointBlock" to GlassFieldPointBlock,
      "GlassFieldKernel1" to GlassFieldKernel1,
      "GlassFieldKernel8" to GlassFieldKernel8,
      "GlassLightBlock" to GlassLightBlock,
      "GlassTravelShadowBlock" to GlassTravelShadowBlock,
      "GlassShadowMask1" to GlassShadowMask1,
      "GlassShadowMask8" to GlassShadowMask8,
      "GlassShadowMaskRing" to GlassShadowMaskRing,
      "GlassTravelShadowFunctionBlock" to GlassTravelShadowFunctionBlock,
    )
    // Parole che SkSL o GLSL tengono per sé: `flat` ha tenuto Fluid-physics in ripiego dalla 3.0.0 alla
    // 3.1.2. `smooth`, `sample`, `centroid`, `filter` SkSL le lascerebbe passare come nomi, un driver no.
    val forbidden = Regex("""\b(flat|noperspective|smooth|centroid|sample|patch|filter|input|output|discard|while|do|dFdx|dFdy|fwidth)\b""")
    for ((name, text) in blocks) {
      assertFalse("$name: resta un `$` di un template Kotlin non risolto", '$' in text)
      assertFalse("$name: un blocco condiviso non ha `main`", Regex("""\bmain\s*\(""").containsMatchIn(text))
      val word = forbidden.find(text.lines().joinToString("\n") { it.substringBefore("//") })
      assertTrue("$name usa `${word?.value}`, che in AGSL o nel GLSL del driver è una parola riservata", word == null)
      assertFalse("$name: un letterale con il suffisso `f` di Kotlin", Regex("""\d\.?\d*f\b""").containsMatchIn(text))
      assertEquals("$name: graffe spaiate", text.count { it == '{' }, text.count { it == '}' })
      assertEquals("$name: parentesi spaiate", text.count { it == '(' }, text.count { it == ')' })
    }
    // I numeri della legge arrivano allo shader come letterali con la virgola: un `1` al posto di `1.0`
    // sarebbe un intero, e `intero * float` in AGSL non compila.
    assertTrue("s2 * (0.25 + 0.495 * s)" in GlassFieldSlabBlock)
    assertTrue("s2 * (0.79 + 1.0 * s)" in GlassFieldSlabBlock)
    assertTrue("min(reach * 1.5, minHalf)" in GlassFieldSlabBlock)
    assertEquals(0.25f, GlassCornerReachSquare, 0f)
    assertEquals(0.495f, GlassCornerReachCube, 0f)
    assertEquals(0.79f, GlassCornerPowerSquare, 0f)
    assertEquals(1.0f, GlassCornerPowerCube, 0f)
    assertEquals(1.5f, GlassFieldNormalSpread, 0f)
  }

  @Test
  fun theBenchProbesCarryTheKernelVerbatim() {
    // Il nucleo è un blocco senza `main`: finché corpo e luce non esistono, sul banco lo compilano e lo
    // rendono due sonde che ne tengono una copia. La sorgente è il Kotlin: se cambia, la copia va riscritta
    // (artifacts/liquid-glass/bench/f1a-1/genera_sonde.py), o le scene prova-angolo-* mostrerebbero un
    // campo che l'engine non ha più.
    val folder = listOf(File("../tools/glassbench/shaders"), File("tools/glassbench/shaders"))
      .firstOrNull { it.isDirectory }
      ?: throw AssertionError("Non trovo tools/glassbench/shaders da ${File(".").absolutePath}")
    for ((name, kernel) in listOf("GlassFieldKernel1" to GlassFieldKernel1, "GlassFieldKernel8" to GlassFieldKernel8)) {
      val file = File(folder, "prova-campo-${name.last()}.agsl")
      assertTrue("Manca la sonda ${file.path}", file.isFile)
      val text = file.readText().replace("\r\n", "\n")
      val open = "// >>> $name\n"
      val close = "\n// <<< $name"
      val from = text.indexOf(open)
      val to = text.indexOf(close)
      assertTrue("${file.name} non ha i due segni `>>> $name` e `<<< $name`", from >= 0 && to > from)
      assertEquals(
        "${file.name} non contiene più $name così com'è in GlassFieldKernel.kt: riscrivi la copia fra i due segni",
        kernel.replace("\r\n", "\n").trim('\n'),
        text.substring(from + open.length, to),
      )
    }
  }
}
