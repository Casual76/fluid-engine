package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.trace.TodaySpring
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeNanos
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropFaces
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropFaceAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * L'etichetta del segmentato durante un tocco (F1B.3, giro di correzione; difetto 12 della partenza, riga 23 di S2):
 * «Mese» grigio → nero al tocco → verde sotto la lente → sbiadito → nero, e doppia per tre fotogrammi alla partenza.
 *
 * Si fa correre la molla vera di un tocco (`GlassDragAnimation.animateToValue`: valore e pressione (1, 1000), il
 * rilascio quando il valore è entro il 2,5% della corsa e la pressione al 96%) a 120 Hz, e si guarda a ogni
 * fotogramma ciò che le regole del controllo decidono: l'aspetto dell'etichetta nella pista e nella copia d'accento
 * ([segmentedLabelLooksSelected]), dove e quanto si vede l'etichetta ridisegnata sopra la goccia
 * ([segmentedOverlayTranslation], [segmentedOverlayAlpha]). Le regole di prima, scritte qui accanto, mostrano che il
 * test vede il difetto.
 */
class FluidSegmentedLabelTest {

  private val count = 3
  private val segmentWidth = 120f
  private val fps = 120

  /** Un fotogramma di un tocco: dove sta la goccia, quanto è premuta. */
  private class Frame(val value: Float, val pressure: Float)

  /** Un tocco dal segmento [from] al segmento [to], con le molle e la regola del rilascio di `GlassDragAnimation`. */
  private fun tap(from: Int, to: Int): List<Frame> {
    val value = TodaySpring(FluidMotion.dropValue(0.001f), from.toFloat(), to.toFloat())
    val pressIn = TodaySpring(FluidMotion.DropPress, 0f, 1f)
    val threshold = (count - 1) * 0.025f
    var release = 0
    while (release < fps * 2) {
      val n = glassTraceTimeNanos(release, fps)
      if (abs(value.value(n) - to) < threshold && pressIn.value(n) >= 0.96f) break
      release++
    }
    val at = glassTraceTimeNanos(release, fps)
    val pressOut = TodaySpring(FluidMotion.DropPress, pressIn.value(at), 0f, pressIn.velocity(at))
    val frames = ArrayList<Frame>()
    for (k in 0 until fps * 2) {
      val n = glassTraceTimeNanos(k, fps)
      val p = if (k < release) pressIn.value(n) else pressOut.value(glassTraceTimeNanos(k - release, fps))
      frames += Frame(value.value(n), p)
      if (k > release && pressOut.finished(glassTraceTimeNanos(k - release, fps)) && value.finished(n)) break
    }
    return frames
  }

  /** Quante volte una sequenza di valori cambia. */
  private fun <T> changes(seq: List<T>): Int = seq.zipWithNext().count { (a, b) -> a != b }

  @Test
  fun `during a tap each label changes look once, and only when the drop reaches it`() {
    for ((from, to) in listOf(0 to 2, 2 to 0, 0 to 1, 1 to 2)) {
      val frames = tap(from, to)
      val drop = frames.map { segmentedDropIndex(it.value, count) }
      val arriving = drop.map { segmentedLabelLooksSelected(to, it) }
      val leaving = drop.map { segmentedLabelLooksSelected(from, it) }
      assertEquals("$from → $to: l'etichetta d'arrivo", 1, changes(arriving))
      assertEquals("$from → $to: quella di partenza", 1, changes(leaving))
      // Cambia quando la goccia ci arriva sopra (oltre metà strada), non al tocco.
      val flip = arriving.indexOfFirst { it }
      assertTrue("$from → $to: cambia al fotogramma $flip", abs(frames[flip].value - to) <= 0.5f)
      assertTrue("$from → $to: non al tocco", flip > 0)
      // Prima l'aspetto seguiva la scelta (`currentIndex`), che diventa [to] al tocco: l'etichetta d'arrivo cambiava
      // al fotogramma zero, con la goccia ancora sull'altra, e poi di nuovo sotto la lente (la copia d'accento).
      assertTrue("$from → $to: al tocco la goccia non c'è ancora", drop.first() != to)
    }
  }

  @Test
  fun `the label on the drop sits on the label it covers and comes and goes with the face, never double`() {
    val face = GlassDropFaces.forSurface(dark = false)
    for ((from, to) in listOf(0 to 2, 2 to 0, 0 to 1)) {
      var doubled = 0
      var doubledBefore = 0
      for (f in tap(from, to)) {
        val dropIndex = segmentedDropIndex(f.value, count)
        val under = dropIndex * segmentWidth
        // Adesso: sul segmento della goccia, con l'alfa della faccia.
        val overlayX = segmentedOverlayTranslation(dropIndex, segmentWidth, rtl = false)
        val alpha = segmentedOverlayAlpha(face, f.pressure)
        assertEquals(glassDropFaceAlpha(face, f.pressure), alpha, 0f)
        if (alpha > 0.01f && abs(overlayX - under) > 0.5f) doubled++
        // Prima: seguiva la goccia (`pill.value`) e sfumava tre volte più in fretta della pressione.
        val beforeX = f.value * segmentWidth
        val beforeAlpha = (1f - f.pressure * 3f).coerceIn(0f, 1f)
        if (beforeAlpha > 0.01f && abs(beforeX - under) > 0.5f) doubledBefore++
      }
      assertEquals("$from → $to: fotogrammi con l'etichetta doppia", 0, doubled)
      assertTrue("$from → $to: la regola di prima la raddoppiava ($doubledBefore fotogrammi)", doubledBefore > 0)
    }
  }

  @Test
  fun `at release the label on the face is as visible as the face, so nothing under it reads faded`() {
    for (dark in listOf(false, true)) {
      val face = GlassDropFaces.forSurface(dark)
      var fadedBefore = 0
      for (f in tap(0, 2)) {
        val faceAlpha = glassDropFaceAlpha(face, f.pressure)
        assertEquals(faceAlpha, segmentedOverlayAlpha(face, f.pressure), 0f)
        // Prima: la faccia copriva l'etichetta per più di un decimo e quella ridisegnata sopra mancava ancora.
        if (faceAlpha - (1f - f.pressure * 3f).coerceIn(0f, 1f) > 0.1f) fadedBefore++
      }
      assertTrue("dark=$dark: prima l'etichetta sotto la faccia si leggeva sbiadita ($fadedBefore fotogrammi)", fadedBefore > 0)
    }
  }

  @Test
  fun `the drop index is the nearest segment, inside the track`() {
    assertEquals(0, segmentedDropIndex(0.49f, 3))
    assertEquals(1, segmentedDropIndex(0.5f, 3))
    assertEquals(2, segmentedDropIndex(2.3f, 3))
    assertEquals(0, segmentedDropIndex(-0.4f, 3))
    assertEquals(0, segmentedDropIndex(Float.NaN, 3))
    // Da destra a sinistra l'etichetta ridisegnata va verso sinistra, come la goccia.
    assertEquals(-240f, segmentedOverlayTranslation(2, 120f, rtl = true), 0f)
    assertEquals(Color.White, GlassDropFaces.forSurface(dark = false))
  }
}
