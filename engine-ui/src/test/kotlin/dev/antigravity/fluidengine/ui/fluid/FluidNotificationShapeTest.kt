package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
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

  /**
   * Giro di correzione di F1B (S2, parte 2.3: blocco tagliato a destra e bordi impilati): in **ogni fotogramma** della
   * molla vera della comparsa, a 120 Hz, in entrata e in uscita, la sagoma (vetro, `Surface`, ritaglio, ombra: un nodo
   * solo, la colonna della card) e il contenuto hanno la stessa larghezza, il contenuto non sporge mai di lato, e in
   * fondo sporge al più del 3% dell'altezza, dentro il margine di 12 dp (lo ritaglia la stessa sagoma).
   */
  @Test
  fun inEveryFrameOfTheSpringShapeAndContentShareOneMeasure() {
    val density = 2.125f
    val bottomMarginPx = 12f * density
    for ((width, contentHeight) in listOf(1190 to 170, 1080 to 240, 720 to 153, 1 to 1)) {
      for ((from, to) in listOf(NotificationEnterShapeScale to 1f, 1f to NotificationExitShapeScale)) {
        val animation = TargetBasedAnimation(
          animationSpec = FluidMotion.snappy<Float>(),
          typeConverter = Float.VectorConverter,
          initialValue = from,
          targetValue = to,
        )
        val frame = 1_000_000_000L / 120L
        var t = 0L
        var frames = 0
        var lastHeight = -1
        while (true) {
          val scale = animation.getValueFromNanos(t)
          val layout = fluidNotificationShapeLayout(width, contentHeight, scale)
          val where = "$width x $contentHeight, $from -> $to, fotogramma $frames (scala $scale)"
          assertEquals(where, layout.width, layout.contentWidth)
          assertEquals(where, width, layout.width)
          assertTrue(where, layout.height <= layout.contentHeight)
          val overflow = layout.contentHeight - layout.height
          assertTrue("$where: sporge $overflow px", overflow <= contentHeight * 0.03f + 1f && overflow <= bottomMarginPx + 1f)
          // In entrata la sagoma solo cresce, in uscita solo cala: nessun bordo che torna indietro.
          if (lastHeight >= 0) {
            if (to > from) assertTrue(where, layout.height >= lastHeight) else assertTrue(where, layout.height <= lastHeight)
          }
          lastHeight = layout.height
          frames++
          if (t >= animation.durationNanos) break
          t += frame
        }
        assertTrue("la molla ha dei fotogrammi: $frames", frames > 3)
        assertEquals(fluidNotificationShapeSide(contentHeight, to), lastHeight)
      }
    }
  }

  @Test
  fun theCardNoLongerNarrowsItsGlassNode() {
    val root = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin")).first { it.isDirectory }
    val source = File(root, "dev/antigravity/fluidengine/ui/fluid/FluidNotification.kt").readText()
    // La cornice che restringeva il solo nodo del vetro, con il contenuto misurato pieno, non c'è più: la sagoma e il
    // contenuto hanno una misura sola (`fluidNotificationShape`, sulla colonna della card).
    assertTrue("fluidNotificationShapeFrame" !in source)
    assertTrue(".fluidNotificationShape(shape, shapeScale)" in source)
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
