package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.EnterExitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La comparsa della notifica per geometria (F1B.5): la sagoma della card nasce a 0,97 e cresce, invece di uno
 * `scaleIn` che scalava un antenato del vetro (`ref/regole.md`, «un ingresso non scala un vetro»).
 */
class FluidNotificationShapeTest {

  @Test
  fun theShapeStartsAndEndsWhereTheOldScaleDid() {
    assertEquals(0.97f, NotificationEnterShapeScale, 0f)
    assertEquals(0.985f, NotificationExitShapeScale, 0f)
    assertEquals(1f, fluidNotificationShapeScale(EnterExitState.Visible, reducedMotion = false, exitNone = false), 0f)
    assertEquals(0.97f, fluidNotificationShapeScale(EnterExitState.PreEnter, reducedMotion = false, exitNone = false), 0f)
    assertEquals(0.985f, fluidNotificationShapeScale(EnterExitState.PostExit, reducedMotion = false, exitNone = false), 0f)
  }

  @Test
  fun withoutAnArrivalOrAnExitTheShapeIsAlwaysFull() {
    // Animazioni ridotte: niente arrivo. Card lanciata via: niente uscita. Una molla da 1 a 1 non si muove.
    assertEquals(1f, fluidNotificationShapeScale(EnterExitState.PreEnter, reducedMotion = true, exitNone = true), 0f)
    assertEquals(1f, fluidNotificationShapeScale(EnterExitState.PostExit, reducedMotion = true, exitNone = true), 0f)
    assertEquals(1f, fluidNotificationShapeScale(EnterExitState.PostExit, reducedMotion = false, exitNone = true), 0f)
  }

  @Test
  fun theShapeSideIsWholePixelsAndNeverLargerThanTheCard() {
    assertEquals(1080, fluidNotificationShapeSide(1080, 1f))
    assertEquals(1048, fluidNotificationShapeSide(1080, 0.97f))
    assertEquals(1064, fluidNotificationShapeSide(1080, 0.985f))
    assertEquals(1080, fluidNotificationShapeSide(1080, 1.2f))
    assertEquals(1080, fluidNotificationShapeSide(1080, Float.NaN))
    assertEquals(0, fluidNotificationShapeSide(1080, -1f))
    assertEquals(0, fluidNotificationShapeSide(0, 0.97f))
    assertEquals(0, fluidNotificationShapeSide(-5, 0.97f))
    // Monotona: la sagoma che cresce non torna mai indietro di un pixel.
    var last = -1
    var s = 0.97f
    while (s <= 1f) {
      val side = fluidNotificationShapeSide(1179, s)
      assertTrue(side >= last)
      last = side
      s += 0.0005f
    }
  }

  @Test
  fun theNotificationNoLongerScalesAnAncestorOfItsGlass() {
    val root = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin")).first { it.isDirectory }
    val source = File(root, "dev/antigravity/fluidengine/ui/fluid/FluidNotification.kt").readText()
    val code = source.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
    for (call in listOf("scaleIn(", "scaleOut(", "scaleX =", "scaleY =")) {
      assertTrue("FluidNotification.kt chiama ancora $call", code.none { call in it })
    }
  }
}
