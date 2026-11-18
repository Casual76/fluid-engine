package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il sollevamento del menu contestuale (F5A.4): da dove parte la card, dove si posa, come la segue il contenuto,
 * quale forma prende, e quanto scurisce lo sfondo. Sono le cose che sbagliate di un pixel fanno un gradino nel
 * fotogramma del click lungo o in quello in cui la riga ricompare — e da un collage non si vedono.
 */
class FluidContextLiftTest {

  private val eps = 1e-3f

  /** Una riga di lista a densita' 3: 360 x 56 dp a 30 dp dal bordo sinistro. */
  private val row = Rect(30f * 3f, 400f * 3f, 390f * 3f, 456f * 3f)
  private val host = Size(411f * 3f, 891f * 3f)
  private val margin = 16f * 3f

  private fun assertRect(expected: Rect, actual: Rect, tolerance: Float = eps) {
    assertEquals("left", expected.left, actual.left, tolerance)
    assertEquals("top", expected.top, actual.top, tolerance)
    assertEquals("right", expected.right, actual.right, tolerance)
    assertEquals("bottom", expected.bottom, actual.bottom, tolerance)
  }

  // --- La partenza: la card e' la riga come il dito l'ha gonfiata --------------------------------

  @Test
  fun anchorScaleIsOneAtRestAndTheSwellUnderTheFinger() {
    assertEquals(1f, fluidContextAnchorScale(0f), 0f)
    assertEquals(1f + FluidContextAnchorSwell, fluidContextAnchorScale(1f), 0f)
    // Fermata ai capi: la molla della pressione oltrepassa, la riga no.
    assertEquals(1f + FluidContextAnchorSwell, fluidContextAnchorScale(1.2f), 0f)
    assertEquals(1f, fluidContextAnchorScale(-0.1f), 0f)
  }

  @Test
  fun liftedFrameGrowsAroundTheCentreLikeTheRowUnderTheFinger() {
    val lifted = fluidLiftedFrame(row)
    assertEquals(row.center.x, lifted.center.x, eps)
    assertEquals(row.center.y, lifted.center.y, eps)
    assertEquals(row.width * FluidLiftedPreviewScale, lifted.width, eps)
    assertEquals(row.height * FluidLiftedPreviewScale, lifted.height, eps)
  }

  @Test
  fun atTheLongPressTheCardIsExactlyTheSwollenRow() {
    // La riga sotto il dito, al click lungo: gonfiata attorno al centro (il `graphicsLayer` dell'ancora).
    for (press in listOf(0f, 0.3f, 0.8f, 1f)) {
      val s0 = fluidContextAnchorScale(press)
      val swollen = fluidLiftedFrame(row, s0)
      val target = fluidLiftedFrame(row)
      val start = fluidLiftStartFraction(row, target, s0)
      // Il progresso della card a p = 0 e' la frazione di partenza; la sagoma e' il lerp della corsa.
      val card = lerp(row, target, fluidLiftProgress(0f, start))
      assertRect(swollen, card)
      // E il contenuto e' l'immagine della riga alla stessa scala: niente gradino del 2% al click lungo.
      val map = fluidLiftContentMap(target = row, current = card)
      assertEquals("scala al click lungo, pressione $press", s0, map.scale, 1e-5f)
      assertEquals(0f, map.translation.x, eps)
      assertEquals(0f, map.translation.y, eps)
    }
  }

  @Test
  fun theCardArrivesWhereTheTargetIsWhateverTheStart() {
    val target = fluidLiftedFrame(row)
    for (start in listOf(0f, 0.4f, 0.667f, 1f)) {
      assertRect(target, lerp(row, target, fluidLiftProgress(1f, start)))
    }
  }

  @Test
  fun closedWithTheFingerUpTheCardEndsExactlyOnTheRowAtRest() {
    // Al ritorno la partenza torna viva: a dito alzato la scala e' 1, la frazione zero, e a p = 0 la card e'
    // la riga a riposo — quella che ricompare nella pagina nello stesso fotogramma.
    val target = fluidLiftedFrame(row)
    val start = fluidLiftStartFraction(row, target, fluidContextAnchorScale(0f))
    assertEquals(0f, start, 0f)
    assertRect(row, lerp(row, target, fluidLiftProgress(0f, start)))
    assertEquals(1f, fluidLiftContentMap(row, lerp(row, target, fluidLiftProgress(0f, start))).scale, 1e-6f)
  }

  @Test
  fun theOvershootIsTheSameSpringFromTheStart() {
    // Oltre l'1 la card segue l'oltrepasso della molla, accorciato della strada gia' fatta dal dito.
    assertEquals(1.0384f, fluidLiftProgress(1.0384f, 0f), 1e-6f)
    assertEquals(1f + 0.0384f * (1f - 0.6f), fluidLiftProgress(1.0384f, 0.6f), 1e-6f)
  }

  @Test
  fun aPreviewNarrowerThanTheElementStartsFromTheElementAtRest() {
    val narrow = Rect(row.center.x - 100f, row.top - 50f, row.center.x + 100f, row.bottom + 50f)
    assertEquals(0f, fluidLiftStartFraction(row, narrow, 1.02f), 0f)
  }

  @Test
  fun aWiderPreviewStartsAsWideAsTheSwollenElement() {
    val tile = Rect(100f, 300f, 388f, 588f) // una tessera da 96 dp
    val preview = fluidContextPreviewFrame(tile, Size(840f, 540f), host, margin)
    val s0 = 1.02f
    val card = lerp(tile, preview, fluidLiftProgress(0f, fluidLiftStartFraction(tile, preview, s0)))
    assertEquals(tile.width * s0, card.width, 1e-2f)
  }

  // --- Il contenuto segue la sagoma con una scala sola --------------------------------------------

  @Test
  fun contentScaleIsTheSmallerOfTheTwoAxes() {
    // Il gonfiore dell'arrivo e' piu' alto che largo (`LiftedSwellY` > `LiftedSwellX`): la scala dei lati corti
    // porterebbe il testo di una riga fuori dai fianchi del vetro.
    val current = Rect(row.left - 10f, row.top - 20f, row.right + 10f, row.bottom + 20f)
    val map = fluidLiftContentMap(row, current)
    assertEquals(current.width / row.width, map.scale, 1e-6f)
    assertTrue(row.width * map.scale <= current.width + eps)
    assertTrue(row.height * map.scale <= current.height + eps)
  }

  @Test
  fun aDegenerateRectLeavesTheContentWhereItIs() {
    assertEquals(FluidMenuContentMap.Identity, fluidLiftContentMap(Rect.Zero, row))
    assertEquals(FluidMenuContentMap.Identity, fluidLiftContentMap(row, Rect(10f, 10f, 10f, 40f)))
  }

  // --- L'anteprima personalizzata: dove si posa ----------------------------------------------------

  @Test
  fun previewIsCentredOnTheElement() {
    val tile = Rect(500f, 900f, 788f, 1188f)
    val frame = fluidContextPreviewFrame(tile, Size(600f, 400f), host, margin)
    assertEquals(tile.center.x, frame.center.x, eps)
    assertEquals(tile.center.y, frame.center.y, eps)
    assertEquals(600f, frame.width, eps)
    assertEquals(400f, frame.height, eps)
  }

  @Test
  fun previewStaysInsideTheScreenMargins() {
    val corner = Rect(0f, 0f, 120f, 120f)
    val frame = fluidContextPreviewFrame(corner, Size(900f, 600f), host, margin)
    assertEquals(margin, frame.left, eps)
    assertEquals(margin, frame.top, eps)
    val far = Rect(host.width - 120f, host.height - 120f, host.width, host.height)
    val other = fluidContextPreviewFrame(far, Size(900f, 600f), host, margin)
    assertEquals(host.width - margin, other.right, eps)
    assertEquals(host.height - margin, other.bottom, eps)
    // Piu' grande dello schermo meno i margini non diventa.
    val huge = fluidContextPreviewFrame(row, Size(host.width * 2f, host.height * 2f), host, margin)
    assertEquals(host.width - margin * 2f, huge.width, eps)
    assertEquals(host.height - margin * 2f, huge.height, eps)
  }

  @Test
  fun previewLeavesRoomForTheMenu() {
    val max = fluidContextPreviewMaxSize(host, margin)
    assertEquals(host.width - margin * 2f, max.width, eps)
    assertEquals((host.height - margin * 2f) * FluidContextPreviewMaxHeightFraction, max.height, eps)
    assertTrue(FluidContextPreviewMaxHeightFraction < 0.6f)
  }

  @Test
  fun withoutAMeasuredPreviewTheTargetIsTheLiftedElement() {
    assertRect(fluidLiftedFrame(row), fluidContextLiftTarget(row, null, host, margin))
    assertRect(
      fluidContextPreviewFrame(row, Size(600f, 300f), host, margin),
      fluidContextLiftTarget(row, Size(600f, 300f), host, margin),
    )
  }

  // --- La forma del ritaglio -----------------------------------------------------------------------

  private val density = Density(3f)
  private val card = Size(600f, 300f)

  @Test
  fun noShapeIsThePanelRadius() {
    assertEquals(FluidCornerRadii.all(66f), fluidContextPreviewRadii(null, card, density, LayoutDirection.Ltr, 66f))
  }

  @Test
  fun roundedShapesGiveTheirOwnCorners() {
    val radii = fluidContextPreviewRadii(
      RoundedCornerShape(topStart = 10.dp, topEnd = 20.dp, bottomEnd = 30.dp, bottomStart = 40.dp),
      card, density, LayoutDirection.Ltr, 66f,
    )
    assertEquals(FluidCornerRadii(30f, 60f, 90f, 120f), radii)
    // Da destra a sinistra gli angoli d'inizio e di fine si scambiano.
    val rtl = fluidContextPreviewRadii(
      RoundedCornerShape(topStart = 10.dp, topEnd = 20.dp, bottomEnd = 30.dp, bottomStart = 40.dp),
      card, density, LayoutDirection.Rtl, 66f,
    )
    assertEquals(FluidCornerRadii(60f, 30f, 120f, 90f), rtl)
  }

  @Test
  fun aCircleLiftsAsACircleAndACornerNeverPassesHalfTheShortSide() {
    val square = Size(192f, 192f)
    assertEquals(FluidCornerRadii.all(96f), fluidContextPreviewRadii(CircleShape, square, density, LayoutDirection.Ltr, 66f))
    assertEquals(
      FluidCornerRadii.all(150f),
      fluidContextPreviewRadii(RoundedCornerShape(200.dp), card, density, LayoutDirection.Ltr, 66f),
    )
  }

  @Test
  fun continuousShapeGivesItsNominalCorners() {
    assertEquals(
      FluidCornerRadii.all(FluidRadius.Group.value * 3f),
      fluidContextPreviewRadii(ContinuousCornerShape(FluidRadius.Group), card, density, LayoutDirection.Ltr, 1f),
    )
  }

  @Test
  fun rectangleIsSquareAndAFreeShapeFallsBackToThePanelRadius() {
    assertEquals(FluidCornerRadii.Zero, fluidContextPreviewRadii(RectangleShape, card, density, LayoutDirection.Ltr, 66f))
    val star = object : Shape {
      override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path())
    }
    assertEquals(FluidCornerRadii.all(66f), fluidContextPreviewRadii(star, card, density, LayoutDirection.Ltr, 66f))
  }

  // --- Lo scambio elemento → anteprima --------------------------------------------------------------

  @Test
  fun theElementLeavesBeforeThePreviewIsFullAndThePreviewArrivesWhileTheCardForms() {
    assertTrue(FluidLiftSwapOutFrom < FluidLiftSwapInFrom)
    assertTrue(FluidLiftSwapInFrom < FluidLiftSwapOutTo)
    assertTrue(FluidLiftSwapOutTo < FluidLiftSwapInTo)
    // L'anteprima e' piena prima che la molla d'apertura arrivi al traguardo.
    assertTrue(FluidLiftSwapInTo < 1f)
  }

  // --- Lo sfondo del contestuale ------------------------------------------------------------------

  @Test
  fun modalScrimIsThePaintedOne() {
    assertEquals(GlassPaintedScrim.LightAlpha, FluidScrimLook.Modal.lightAlpha, 0f)
    assertEquals(GlassPaintedScrim.DarkAlpha, FluidScrimLook.Modal.darkAlpha, 0f)
    assertEquals(3.75f, FluidScrimLook.Modal.blurDp, 1e-5f)
  }

  @Test
  fun everyContextLevelIsDarkerThanTheMenusAndTheyAreOrdered() {
    val levels = FluidContextScrimLevel.entries.map { it.look }
    for (dark in listOf(false, true)) {
      for (look in levels) assertTrue(look.alpha(dark) > FluidScrimLook.Modal.alpha(dark))
      for (i in 1 until levels.size) {
        assertTrue(levels[i].alpha(dark) > levels[i - 1].alpha(dark))
        assertTrue(levels[i].blurScale >= levels[i - 1].blurScale)
      }
    }
  }

  @Test
  fun noContextLevelAveragesThePageAway() {
    // Invariante 20 (R2): oltre ~8 dp un pannello a tutto schermo trasmette la media della pagina.
    assertTrue(FluidScrimBlurCeilingDp < 8f)
    for (level in FluidContextScrimLevel.entries) {
      assertTrue("${level.name}: ${level.look.blurDp} dp", level.look.blurDp <= FluidScrimBlurCeilingDp)
      assertTrue(level.look.blurDp >= FluidScrimLook.Modal.blurDp)
    }
  }

  @Test
  fun theProposalIsLevelB() {
    assertEquals(FluidContextScrimLevel.B.look, FluidContextScrimLook)
  }
}
