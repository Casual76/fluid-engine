package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le manopole di debug del velo della striscia (F2.5, giro sul velo): `edgeveil`, `edgefade`, `edgecap`, `edgecurve`. A
 * 1 (e `photo`), o senza manopole, i numeri della striscia sono quelli di serie al bit; altrimenti scalano, tagliano o
 * cambiano la curva del velo e nient'altro.
 */
class GlassScrollEdgeTuningTest {

  @After
  fun backToShipped() {
    GlassScrollEdgeTuning.apply(null)
  }

  private fun uniforms(top: Boolean, profile: GlassScrollEdgeProfile = GlassScrollEdgeProfile.Soft) =
    glassScrollEdgeUniforms(
      profile = profile,
      top = top,
      widthPx = 1600f,
      heightPx = 300f,
      controlsPx = 160f,
      unitPx = 94f,
      veilColor = Color.White,
    )

  private fun knots(curve: GlassVeilCurve) = FloatArray(GlassEdgeVeilKnots * 4).also { curve.writeKnots(it) }.toList()

  @Test
  fun withoutKnobs_theStripIsTheShippedOne_bitForBit() {
    GlassScrollEdgeTuning.apply(null)
    for (top in listOf(true, false)) {
      val u = uniforms(top)
      assertEquals(knots(GlassScrollEdgeProfile.Soft.veil), u.veilKnots.toList())
      assertEquals(listOf(1f, 1f), u.veilKnob.toList())
    }
    // Le manopole a 1, e `edgecurve=photo`, scritte a mano sono la stessa cosa.
    GlassScrollEdgeTuning.apply(mapOf("edgeveil" to "1", "edgefade" to "1", "edgecap" to "1", "edgecurve" to "photo"))
    val u = uniforms(top = true)
    assertEquals(knots(GlassScrollEdgeProfile.Soft.veil), u.veilKnots.toList())
    assertEquals(listOf(1f, 1f), u.veilKnob.toList())
  }

  @Test
  fun theCeiling_keepsTheCurveAndCutsWhatIsAbove() {
    GlassScrollEdgeTuning.apply(mapOf("edgecap" to "0.2"))
    val u = uniforms(top = true)
    // La curva resta quella di serie; lo shader la taglia a 0,2 col suo ginocchio morbido.
    assertEquals(knots(GlassScrollEdgeProfile.Soft.veil), u.veilKnots.toList())
    assertEquals(listOf(1f, 0.2f), u.veilKnob.toList())
    // In basso lo stesso tetto.
    assertEquals(0.2f, uniforms(top = false).veilKnob[1], 0f)
  }

  @Test
  fun theScales_actOnTheirOwnSideOnly() {
    GlassScrollEdgeTuning.apply(mapOf("edgeveil" to "0.5", "edgecap" to "0.3"))
    val topSide = uniforms(top = true)
    assertEquals(listOf(0.5f, 0.3f), topSide.veilKnob.toList())
    val bottomSide = uniforms(top = false)
    assertEquals(listOf(1f, 0.3f), bottomSide.veilKnob.toList())

    GlassScrollEdgeTuning.apply(mapOf("edgeveil" to "0", "edgefade" to "0"))
    assertEquals(0f, uniforms(top = true).veilKnob[0], 0f)
    assertEquals(0f, uniforms(top = false).veilKnob[0], 0f)
    // La sfocatura non si tocca: senza velo in alto resta.
    assertEquals(GlassScrollEdgeProfile.Soft.blurShare, uniforms(top = true).blurRamp[3], 0f)
  }

  @Test
  fun theCurveKnob_showsTheSteepTextCurveOnRequestOnly() {
    GlassScrollEdgeTuning.apply(mapOf("EdgeCurve" to " Text "))
    assertTrue(GlassScrollEdgeTuning.textCurve)
    val text = uniforms(top = true)
    assertEquals(knots(GlassScrollEdgeProfile.Soft.veilText), text.veilKnots.toList())
    // Lo scurimento resta «0,8 del valore» al bordo dei tasti anche con la curva del testo.
    assertEquals(GlassScrollEdgeProfile.Soft.darkDim, text.shade[1] * GlassScrollEdgeProfile.Soft.veil(0f, text = true), 1e-6f)
    assertEquals(knots(GlassScrollEdgeProfile.Soft.veilText), uniforms(top = false).veilKnots.toList())
    // Un valore che non si riconosce (anche `auto`, che non c'è) lascia la curva di serie.
    for (value in listOf("auto", "testo", "")) {
      assertTrue(GlassScrollEdgeTuning.apply(mapOf("edgecurve" to value)).isEmpty())
      assertFalse(GlassScrollEdgeTuning.textCurve)
      assertEquals(knots(GlassScrollEdgeProfile.Soft.veil), uniforms(top = true).veilKnots.toList())
    }
  }

  @Test
  fun apply_saysWhatItTook_andForgetsTheRest() {
    val taken = GlassScrollEdgeTuning.apply(
      mapOf("EdgeVeil" to "0,5", "edgecap" to "0.360", "edgefade" to "2", "blur" to "24", "edgecurve" to "text"),
    )
    assertEquals(mapOf("edgeveil" to "0.5", "edgecap" to "0.36", "edgecurve" to "text"), taken)
    assertEquals(0.5f, GlassScrollEdgeTuning.topVeil, 0f)
    // Un valore fuori intervallo non si prende: resta di serie.
    assertEquals(1f, GlassScrollEdgeTuning.bottomFade, 0f)
    // Una chiamata nuova sostituisce tutto: quello che manca torna a 1.
    assertTrue(GlassScrollEdgeTuning.apply(mapOf("edgefade" to "0")).keys == setOf("edgefade"))
    assertEquals(1f, GlassScrollEdgeTuning.topVeil, 0f)
    assertEquals(1f, GlassScrollEdgeTuning.veilCeiling, 0f)
    assertFalse(GlassScrollEdgeTuning.textCurve)
  }

  @Test
  fun knobValues_areNumbersBetweenZeroAndOne() {
    assertEquals(0.25f, glassScrollEdgeKnobValue(" 0,25 ")!!, 0f)
    assertEquals(0f, glassScrollEdgeKnobValue("0")!!, 0f)
    assertNull(glassScrollEdgeKnobValue("1.5"))
    assertNull(glassScrollEdgeKnobValue("-0.1"))
    assertNull(glassScrollEdgeKnobValue("molto"))
    assertNull(glassScrollEdgeKnobValue("NaN"))
    assertEquals("1", glassScrollEdgeKnobText(1f))
    assertEquals("0.18", glassScrollEdgeKnobText(0.18f))
  }
}
