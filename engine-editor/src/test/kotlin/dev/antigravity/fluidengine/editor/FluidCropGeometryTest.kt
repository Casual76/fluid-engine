package dev.antigravity.fluidengine.editor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.15 · La geometria del ritaglio sulla JVM: le rotazioni di 90° e lo specchio come in Foto, il ritaglio che resta
 * dentro la foto girata, le proporzioni, la matrice dai pixel della sorgente a quelli del file, la ricetta scritta e
 * riletta, annulla e ripristina, «Auto» che si spegne senza togliere le modifiche a mano.
 */
class FluidCropGeometryTest {

  private val sw = 4000
  private val sh = 3000

  private fun FluidAffine.at(x: Float, y: Float) = mapX(x, y) to mapY(x, y)
  private fun near(a: Pair<Float, Float>, b: Pair<Float, Float>, tol: Float = 0.01f) =
    abs(a.first - b.first) <= tol && abs(a.second - b.second) <= tol

  @Test fun senzaModificheLaMatriceELIdentita() {
    val g = FluidCropGeometry()
    assertTrue(g.isIdentity)
    assertEquals(sw to sh, g.outputSize(sw, sh))
    val m = g.sourceToOutput(sw, sh)
    assertTrue(near(m.at(0f, 0f), 0f to 0f))
    assertTrue(near(m.at(4000f, 3000f), 4000f to 3000f))
  }

  @Test fun ruotaASinistraPortaLAngoloInAltoADestraInAltoASinistra() {
    val g = FluidCropGeometry().rotatedLeft()
    assertEquals(1, g.quarterTurns)
    assertEquals(sh to sw, g.outputSize(sw, sh))
    val m = g.sourceToOutput(sw, sh)
    // In senso antiorario: l'angolo in alto a destra della foto va in alto a sinistra.
    assertTrue(near(m.at(4000f, 0f), 0f to 0f))
    assertTrue(near(m.at(0f, 0f), 0f to 4000f))
    // Quattro volte e si torna all'inizio.
    assertEquals(FluidCropGeometry(), g.rotatedLeft().rotatedLeft().rotatedLeft())
  }

  @Test fun loSpecchioEOrizzontaleSulloSchermoAncheDopoUnaRotazione() {
    val g = FluidCropGeometry().rotatedLeft().flippedHorizontally()
    val m = g.sourceToOutput(sw, sh)
    // Senza specchio l'angolo in alto a destra andava in alto a sinistra; con lo specchio va in alto a destra.
    assertTrue(near(m.at(4000f, 0f), 3000f to 0f))
    // Specchio, ruota, specchio, ruota... la combinazione resta un elemento del gruppo: ruotare uno specchiato gira all'indietro.
    val back = FluidCropGeometry().flippedHorizontally().rotatedLeft()
    assertEquals(3, back.quarterTurns)
    assertTrue(back.flipped)
    // Lo specchio cambia il verso del raddrizzamento e il lato del ritaglio.
    val crop = FluidCropGeometry(angle = 10f, centerX = 0.3f, width = 0.4f).flippedHorizontally()
    assertEquals(-10f, crop.angle)
    assertEquals(0.7f, crop.centerX, 1e-6f)
  }

  @Test fun ilRaddrizzamentoStringeIlRitaglioFinoAStareNellaFoto() {
    val g = FluidCropGeometry(angle = 10f).fitted(sw, sh)
    assertTrue(g.width < 1f && g.height < 1f)
    assertEquals(g.width, g.height, 1e-5f) // le stesse proporzioni
    // I quattro angoli del ritaglio, riportati nella foto, sono dentro (con un pixel di tolleranza).
    val back = g.sourceToOutput(sw, sh).inverted()
    val (ow, oh) = g.outputSize(sw, sh)
    for ((x, y) in listOf(0f to 0f, ow.toFloat() to 0f, 0f to oh.toFloat(), ow.toFloat() to oh.toFloat())) {
      val (sx, sy) = back.at(x, y)
      assertTrue("$sx,$sy", sx >= -1f && sx <= sw + 1f && sy >= -1f && sy <= sh + 1f)
    }
    // Ed e' il piu' grande possibile: almeno un angolo tocca il bordo.
    val touches = listOf(0f to 0f, ow.toFloat() to 0f, 0f to oh.toFloat(), ow.toFloat() to oh.toFloat()).any { (x, y) ->
      val (sx, sy) = back.at(x, y)
      abs(sx) < 2f || abs(sx - sw) < 2f || abs(sy) < 2f || abs(sy - sh) < 2f
    }
    assertTrue(touches)
    // A 10° la formula chiusa: s = min(W / (W cos + H sin), H / (W sin + H cos)).
    val c = cos(Math.toRadians(10.0)).toFloat()
    val s = sin(Math.toRadians(10.0)).toFloat()
    val expected = minOf(sw / (sw * c + sh * s), sh / (sw * s + sh * c))
    assertEquals(expected, g.width, 1e-4f)
  }

  @Test fun spostareNonFaUscireIlRitaglio() {
    val g = FluidCropGeometry(centerX = 0.5f, centerY = 0.5f, width = 0.5f, height = 0.5f)
    val moved = g.moved(10_000f, -10_000f, sw, sh)
    assertEquals(0.75f, moved.centerX, 1e-5f)
    assertEquals(0.25f, moved.centerY, 1e-5f)
  }

  @Test fun leProporzioniDannoIlRitaglioPiuGrandeECentrato() {
    val square = FluidCropGeometry(aspect = FluidCropAspect.Square).reset(sw, sh)
    val (w, h) = square.outputSize(sw, sh)
    assertEquals(3000, w)
    assertEquals(3000, h)
    val wide = FluidCropGeometry().largestWithRatio(16f / 9f, sw, sh)
    val (ww, wh) = wide.outputSize(sw, sh)
    assertEquals(4000, ww)
    assertEquals(2250, wh)
    // Il 16:9 girato e' il 9:16; il libero e il quadrato restano.
    assertEquals(FluidCropAspect.custom(9, 16), FluidCropAspect.W16x9.rotated())
    assertEquals(FluidCropAspect.Square, FluidCropAspect.Square.rotated())
    assertNull(FluidCropAspect.Freeform.ratio(1.3f))
    assertEquals(1.3f, FluidCropAspect.Original.ratio(1.3f))
    // Ruotando a sinistra anche le proporzioni girano.
    assertEquals(FluidCropAspect.custom(9, 16), FluidCropGeometry(aspect = FluidCropAspect.W16x9).rotatedLeft().aspect)
  }

  @Test fun laRicettaSiScriveESiRilegge() {
    val edit = FluidImageEdit(
      FluidCropGeometry(quarterTurns = 3, flipped = true, angle = -7.5f, centerX = 0.41f, centerY = 0.52f, width = 0.6f,
        height = 0.7f, aspect = FluidCropAspect.W4x3),
      FluidAdjustments.of(FluidAdjustment.Exposure to 12f, FluidAdjustment.Contrast to -30f, FluidAdjustment.Definition to 44f),
    ).withAuto(FluidAdjustments.of(FluidAdjustment.Warmth to 8f))
    val text = edit.encode()
    assertTrue(text, text.startsWith("fluid-edit/1;"))
    assertEquals(edit, FluidImageEdit.decode(text))
    assertEquals(FluidImageEdit.Original, FluidImageEdit.decode(FluidImageEdit.Original.encode()))
    // Una regolazione sconosciuta (di una versione futura) si ignora, un testo qualsiasi non e' una ricetta.
    val future = FluidImageEdit.decode("fluid-edit/2;q=0;adj=exposure:10,clarity2:30;nuovo=1")!!
    assertEquals(10f, future.adjustments[FluidAdjustment.Exposure])
    assertNull(FluidImageEdit.decode("ciao"))
    assertTrue(FluidImageEdit.Original.isOriginal)
    assertFalse(edit.isOriginal)
  }

  @Test fun annullaERipristinaUnPassoPerGesto() {
    val h = FluidEditHistory()
    assertFalse(h.canUndo)
    val a = FluidImageEdit.Original.with(FluidAdjustment.Exposure, 20f)
    val b = a.copy(geometry = a.geometry.rotatedLeft())
    assertTrue(h.commit(a))
    assertFalse(h.commit(a)) // uguale: nessun passo
    h.commit(b)
    assertEquals(a, h.undo())
    assertEquals(FluidImageEdit.Original, h.undo())
    assertNull(h.undo())
    assertEquals(a, h.redo())
    // Un passo nuovo toglie quelli da ripristinare.
    h.commit(a.with(FluidAdjustment.Contrast, 10f))
    assertFalse(h.canRedo)
    // «Ripristina originale» e' un passo come gli altri: si annulla.
    h.commit(FluidImageEdit.Original)
    assertEquals(a.with(FluidAdjustment.Contrast, 10f), h.undo())
  }

  @Test fun autoSiSpegneLasciandoLeModificheAMano() {
    val manual = FluidImageEdit.Original.with(FluidAdjustment.Exposure, 10f)
    val auto = manual.withAuto(FluidAdjustments.of(FluidAdjustment.Exposure to 25f, FluidAdjustment.Contrast to 15f))
    assertEquals(35f, auto.adjustments[FluidAdjustment.Exposure])
    assertEquals(15f, auto.adjustments[FluidAdjustment.Contrast])
    assertTrue(auto.isAuto)
    // Poi una modifica a mano sopra «Auto»…
    val tweaked = auto.with(FluidAdjustment.Contrast, 40f)
    // …e «Auto» spento toglie solo la sua parte.
    val off = tweaked.withAuto(null)
    assertFalse(off.isAuto)
    assertEquals(10f, off.adjustments[FluidAdjustment.Exposure])
    assertEquals(25f, off.adjustments[FluidAdjustment.Contrast])
  }

  @Test fun leRegolazioniStannoNelloroIntervalloAlPassoIntero() {
    val a = FluidAdjustments.Neutral.with(FluidAdjustment.Sharpness, -30f).with(FluidAdjustment.Exposure, 150.4f)
      .with(FluidAdjustment.Warmth, 12.6f)
    assertEquals(0f, a[FluidAdjustment.Sharpness])
    assertEquals(100f, a[FluidAdjustment.Exposure])
    assertEquals(13f, a[FluidAdjustment.Warmth])
    assertEquals(14, FluidAdjustment.All.size)
    assertEquals(FluidAdjustment.All.size, FluidAdjustment.All.map { it.id }.toSet().size)
  }
}
