package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max

/**
 * Le forme concentriche (4.0, piano «iOS in Android» F3.5): la regola «raggio del contenitore − distanza dal bordo, mai
 * sotto il minimo», angolo per angolo, come aritmetica; la forma da sola; le misure dei gruppi in [FluidMetrics].
 *
 * Perché esiste. La regola la ripetono barre, capsula, tab bar e fogli (F4A, F4B, F5B): se sbaglia, sbaglia in ogni
 * angolo dell'interfaccia. Qui si prova senza layout (il modulo non ha Robolectric): la parte che legge l'albero
 * ([resolveConcentricRadii]) si limita a passare a queste funzioni il riquadro del pezzo nelle coordinate del
 * contenitore, e quella va vista sul dispositivo.
 */
class FluidConcentricShapeTest {

  private val one = Density(1f)
  private val ltr = LayoutDirection.Ltr

  // Un foglio da 400 x 600 px con angoli da 40: il caso di F5B a densità 1.
  private val sheet = FluidCornerRadii.all(40f)
  private val sheetSize = Size(400f, 600f)

  private fun assertRadii(expected: FluidCornerRadii, actual: FluidCornerRadii, message: String = "") {
    assertEquals("$message alto a sinistra", expected.topLeft, actual.topLeft, 1e-3f)
    assertEquals("$message alto a destra", expected.topRight, actual.topRight, 1e-3f)
    assertEquals("$message basso a destra", expected.bottomRight, actual.bottomRight, 1e-3f)
    assertEquals("$message basso a sinistra", expected.bottomLeft, actual.bottomLeft, 1e-3f)
  }

  @Test
  fun `un pezzo al centro sta al minimo`() {
    val piece = Rect(100f, 200f, 300f, 300f)
    assertRadii(FluidCornerRadii.all(8f), concentricCornerRadii(sheet, sheetSize, piece, minimumPx = 8f))
    assertRadii(FluidCornerRadii.Zero, concentricCornerRadii(sheet, sheetSize, piece, minimumPx = 0f))
  }

  @Test
  fun `un pezzo toccato al bordo basso e' concentrico nei due angoli bassi e al minimo in alto`() {
    // Una barra posata in fondo al foglio con 12 px di margine su tre lati.
    val bar = Rect(12f, 520f, 388f, 588f)
    val radii = concentricCornerRadii(sheet, sheetSize, bar, minimumPx = 6f)
    assertRadii(FluidCornerRadii(topLeft = 6f, topRight = 6f, bottomRight = 28f, bottomLeft = 28f), radii)
  }

  @Test
  fun `un pezzo a un solo bordo ma lontano dagli angoli resta al minimo`() {
    // Tocca il fondo ma sta in mezzo: vicino a lui il bordo del contenitore e' dritto, non c'e' curva da seguire.
    val button = Rect(150f, 540f, 250f, 600f)
    assertRadii(FluidCornerRadii.all(4f), concentricCornerRadii(sheet, sheetSize, button, minimumPx = 4f))
  }

  @Test
  fun `un pezzo all'angolo e' concentrico solo in quell'angolo`() {
    val corner = Rect(10f, 10f, 110f, 70f)
    val radii = concentricCornerRadii(sheet, sheetSize, corner, minimumPx = 5f)
    assertRadii(FluidCornerRadii(topLeft = 30f, topRight = 5f, bottomRight = 5f, bottomLeft = 5f), radii)
  }

  @Test
  fun `con margini diversi conta il maggiore, e l'angolo interno resta dentro quello esterno`() {
    // 2 px dal lato e 20 dall'alto: con il minore (2) l'angolo da 38 sporgerebbe dalla curva del contenitore.
    val piece = Rect(2f, 20f, 200f, 200f)
    val radii = concentricCornerRadii(sheet, sheetSize, piece, minimumPx = 0f)
    assertEquals(20f, radii.topLeft, 1e-3f)
    // Su una griglia di margini: il cerchio dell'angolo interno sta dentro quello dell'angolo esterno.
    for (dx in 0..40 step 2) for (dy in 0..40 step 2) {
      val r = concentricCornerRadii(sheet, sheetSize, Rect(dx.toFloat(), dy.toFloat(), 300f, 300f), 0f).topLeft
      if (r <= 0f) continue
      val centreDistance = hypot((40f - (dx + r)).toDouble(), (40f - (dy + r)).toDouble()).toFloat()
      assertTrue("margini $dx, $dy: raggio $r esce dal contenitore", centreDistance + r <= 40f + 1e-3f)
      assertEquals(40f - max(dx, dy), r, 1e-3f)
    }
  }

  @Test
  fun `il minimo vince sul raggio concentrico quando questo e' piu' piccolo`() {
    // 36 px dall'angolo di un raggio 40: il concentrico sarebbe 4, il minimo ne chiede 10.
    val piece = Rect(36f, 36f, 200f, 200f)
    assertEquals(10f, concentricCornerRadii(sheet, sheetSize, piece, minimumPx = 10f).topLeft, 1e-3f)
    assertEquals(4f, concentricCornerRadii(sheet, sheetSize, piece, minimumPx = 0f).topLeft, 1e-3f)
  }

  @Test
  fun `in una capsula un pezzo con lo stesso margine sopra, sotto e di lato e' una capsula`() {
    // Una barra a capsula da 300 x 56 px; dentro, un tasto a 6 px dai bordi alto 44.
    val barSize = Size(300f, 56f)
    val capsule = cornerRadiiOf(FluidCapsuleShape, barSize, one, ltr)
    assertRadii(FluidCornerRadii.all(28f), capsule, "capsula")
    val button = Rect(6f, 6f, 50f, 50f)
    val radii = concentricCornerRadii(capsule, barSize, button, minimumPx = 0f)
    // 28 - 6 = 22, cioè mezzo lato: il tasto è un tondo concentrico alla capsula, a sinistra; a destra sta lontano.
    assertEquals(22f, radii.topLeft, 1e-3f)
    assertEquals(22f, radii.bottomLeft, 1e-3f)
    assertEquals(0f, radii.topRight, 1e-3f)
    // Un pezzo largo quanto la barra meno i margini è una capsula da entrambi i lati.
    val inner = concentricCornerRadii(capsule, barSize, Rect(6f, 6f, 294f, 50f), minimumPx = 0f)
    assertRadii(FluidCornerRadii.all(22f), inner, "capsula interna")
  }

  @Test
  fun `i raggi non sono mai negativi e mai oltre mezzo lato corto`() {
    // Un minimo enorme su un pezzo da 40 px: una capsula, non un angolo da 100.
    val small = Rect(0f, 0f, 120f, 40f)
    assertRadii(FluidCornerRadii.all(20f), concentricCornerRadii(sheet, sheetSize, small, minimumPx = 100f))
    // Un minimo negativo, un contenitore con raggi negativi o più grandi di sé, un pezzo che sporge.
    val odd = concentricCornerRadii(
      FluidCornerRadii(-10f, 500f, Float.NaN, Float.POSITIVE_INFINITY),
      Size(100f, 60f),
      Rect(-20f, -5f, 130f, 70f),
      minimumPx = -3f,
    )
    for (r in listOf(odd.topLeft, odd.topRight, odd.bottomRight, odd.bottomLeft)) {
      assertTrue("raggio $r", r in 0f..37.5f)
    }
    // Il raggio del contenitore conta fino a mezzo *suo* lato corto: 500 su un contenitore alto 60 vale 30.
    assertEquals(30f, odd.topRight, 1e-3f)
    // Un pezzo vuoto non ha angoli.
    assertRadii(FluidCornerRadii.Zero, concentricCornerRadii(sheet, sheetSize, Rect(5f, 5f, 5f, 50f), minimumPx = 8f))
  }

  @Test
  fun `senza contenitore vale il minimo, fino a mezzo lato`() {
    assertRadii(FluidCornerRadii.all(12f), concentricRadiiAlone(Size(100f, 80f), 12f))
    assertRadii(FluidCornerRadii.all(15f), concentricRadiiAlone(Size(100f, 30f), 40f))
    assertRadii(FluidCornerRadii.Zero, concentricRadiiAlone(Size(100f, 30f), -4f))
    // Anche come Shape, per chi non la risolve: una continua al minimo, col suo raccordo.
    val alone = FluidConcentricShape(minimum = 12.dp)
    assertTrue(alone is ContinuousCornerShape)
    assertEquals(ContinuousCornerShape.IosSmoothing, alone.smoothing, 0f)
    assertRadii(FluidCornerRadii.all(24f), cornerRadiiOf(alone, Size(200f, 100f), Density(2f), ltr), "a densità 2")
    assertRadii(FluidCornerRadii.Zero, cornerRadiiOf(FluidConcentricShape(), Size(200f, 100f), one, ltr), "minimo zero")
    assertRadii(FluidCornerRadii.Zero, cornerRadiiOf(FluidConcentricShape(minimum = (-5).dp), Size(20f, 20f), one, ltr))
  }

  @Test
  fun `a densità diverse i raggi scalano con i dp`() {
    // Un foglio da 38 dp, un pezzo a 8 dp dall'angolo, minimo 4 dp: 30 dp a ogni densità.
    for (d in listOf(1f, 2f, 2.625f, 3f, 3.5f)) {
      val density = Density(d)
      val size = Size(400f * d, 700f * d)
      val piece = Rect(8f * d, 8f * d, 392f * d, 200f * d)
      val radii = concentricRadiiIn(ContinuousCornerShape(38.dp), size, piece, 4.dp, density, ltr)
      assertEquals("densità $d", 30f * d, radii.topLeft, 1e-2f)
      assertEquals("densità $d", 30f * d, radii.topRight, 1e-2f)
      assertEquals("densità $d", 4f * d, radii.bottomLeft, 1e-2f)
    }
  }

  @Test
  fun `la forma del contenitore si legge giusta, anche in RTL`() {
    val size = Size(200f, 100f)
    assertRadii(FluidCornerRadii.Zero, cornerRadiiOf(RectangleShape, size, one, ltr), "rettangolo")
    assertRadii(FluidCornerRadii.all(50f), cornerRadiiOf(CircleShape, size, one, ltr), "cerchio")
    val onlyTopStart = RoundedCornerShape(topStart = 30.dp)
    assertRadii(FluidCornerRadii(30f, 0f, 0f, 0f), cornerRadiiOf(onlyTopStart, size, one, ltr), "LTR")
    assertRadii(FluidCornerRadii(0f, 30f, 0f, 0f), cornerRadiiOf(onlyTopStart, size, one, LayoutDirection.Rtl), "RTL")
    // Le forme assolute non si specchiano.
    val absolute = AbsoluteRoundedCornerShape(topLeft = 30.dp)
    assertRadii(FluidCornerRadii(30f, 0f, 0f, 0f), cornerRadiiOf(absolute, size, one, LayoutDirection.Rtl), "assoluta")
    // Un raggio più grande del contenitore vale mezzo lato corto, come lo disegna.
    assertRadii(FluidCornerRadii.all(50f), cornerRadiiOf(ContinuousCornerShape(200.dp), size, one, ltr), "oltre")
  }

  @Test
  fun `un contenitore concentrico si risolve prima lui, poi i suoi pezzi`() {
    // Foglio da 40 px; dentro, a 10 px, una barra concentrica (30 negli angoli alti); dentro la barra, a 6 px, un tasto.
    val barSize = Size(380f, 80f)
    val bar = FluidConcentricShape(minimum = 4.dp)
    val barRadii = concentricCornerRadii(sheet, sheetSize, Rect(10f, 10f, 390f, 90f), minimumPx = 4f)
    assertRadii(FluidCornerRadii(30f, 30f, 4f, 4f), barRadii, "barra")
    val button = Rect(6f, 6f, 70f, 70f)
    val radii = concentricRadiiIn(bar, barSize, button, 2.dp, one, ltr, containerRadii = { barRadii })
    assertEquals(24f, radii.topLeft, 1e-3f)
    assertEquals(2f, radii.bottomLeft, 1e-3f)
    // Senza chi conosce l'albero, il contenitore concentrico vale il suo minimo (4 - 6 < 2: il minimo del tasto).
    assertRadii(FluidCornerRadii.all(2f), concentricRadiiIn(bar, barSize, button, 2.dp, one, ltr))
  }

  @Test
  fun `la forma risolta e' una continua con gli stessi angoli e lo stesso raccordo, nei due versi`() {
    val radii = FluidCornerRadii(topLeft = 30f, topRight = 4f, bottomRight = 8f, bottomLeft = 12f)
    val shape = FluidConcentricShape(minimum = 4.dp, smoothing = 0.3f)
    val size = Size(200f, 100f)
    for (direction in LayoutDirection.values()) {
      val resolved = shape.resolvedTo(radii, direction)
      assertEquals(ContinuousCornerShape::class.java, resolved.javaClass)
      assertEquals(0.3f, resolved.smoothing, 0f)
      // Rilette nello stesso verso, tornano gli stessi angoli fisici.
      assertRadii(radii, cornerRadiiOf(resolved, size, one, direction), direction.name)
    }
  }

  @Test
  fun `una concentrica non e' uguale a una continua con lo stesso raggio`() {
    assertEquals(FluidConcentricShape(8.dp), FluidConcentricShape(8.dp))
    assertEquals(FluidConcentricShape(8.dp).hashCode(), FluidConcentricShape(8.dp).hashCode())
    assertNotEquals(FluidConcentricShape(8.dp), FluidConcentricShape(10.dp))
    assertNotEquals(FluidConcentricShape(8.dp), FluidConcentricShape(8.dp, smoothing = 0f))
    assertNotEquals(ContinuousCornerShape(8.dp), FluidConcentricShape(8.dp))
    assertNotEquals(FluidConcentricShape(8.dp), ContinuousCornerShape(8.dp))
    // Le continue di sempre restano uguali per valore.
    assertEquals(ContinuousCornerShape(8.dp), ContinuousCornerShape(8.dp))
    // Una copia con angoli scritti a mano è una continua qualsiasi: chi dà gli angoli smette di chiederli.
    val copy = FluidConcentricShape(8.dp).copy(all = CornerSize(3.dp))
    assertFalse(copy is FluidConcentricShape)
  }

  // --- Misure dei gruppi ---------------------------------------------------------------------------------------

  @Test
  fun `le misure dei gruppi sono quelle di R6`() {
    assertEquals(12.dp, FluidMetrics.GroupSpacing)
    assertEquals(44.dp, FluidMetrics.GroupItemPitch)
    // Il passo è il vetro di un tasto: un gruppo di n azioni è largo n x 44.
    assertEquals(FluidMetrics.GlassButtonLens, FluidMetrics.GroupItemPitch)
    assertEquals(48.dp, FluidMetrics.TouchTarget)
    assertTrue(FluidMetrics.TouchTarget >= FluidMetrics.GroupItemPitch)
  }

  @Test
  fun `i margini della capsula sono quelli della tab bar`() {
    assertEquals(14.dp, FluidMetrics.CapsuleSideMargin)
    assertEquals(16.dp, FluidMetrics.CapsuleBottomMargin)
    assertEquals(FluidFloatingTabBarDefaults.HorizontalMargin, FluidMetrics.CapsuleSideMargin)
    assertEquals(FluidFloatingTabBarDefaults.BottomMargin, FluidMetrics.CapsuleBottomMargin)
  }
}
