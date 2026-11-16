package dev.antigravity.fluidengine.ui.glass.field

import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La comparsa per lente (F3.3, R4 B3): i tre canali di `materialize` e la ricetta che ne esce. Pura.
 */
class GlassMaterializeTest {

  private fun full() = glassRecipe(
    role = GlassRole.Interactive, widthDp = 44f, heightDp = 44f, darkSide = false, optics = GlassDefaults.buttonOptics(),
    intensity = 1f,
  )

  @Test
  fun theContourComesFirstTheLensLaterTheSymbolLastAndAllAreContinuous() {
    var previous = glassMaterializeChannels(0f)
    assertEquals(0f, previous.contour, 0f)
    assertEquals(0f, previous.lens, 0f)
    assertEquals(0f, previous.symbolAlpha, 0f)
    assertEquals(GlassMaterializeSymbolScale, previous.symbolScale, 0f)
    var m = 0.001f
    while (m <= 1f) {
      val now = glassMaterializeChannels(m)
      assertTrue("m=$m: la lente non supera mai il contorno", now.lens <= now.contour)
      assertTrue("m=$m: monotoni", now.contour >= previous.contour && now.lens >= previous.lens &&
        now.symbolAlpha >= previous.symbolAlpha && now.symbolScale <= previous.symbolScale)
      assertTrue("m=$m: continui", now.contour - previous.contour < 0.02f && now.lens - previous.lens < 0.02f &&
        now.symbolAlpha - previous.symbolAlpha < 0.05f && previous.symbolScale - now.symbolScale < 0.06f)
      previous = now
      m += 0.001f
    }
    val end = glassMaterializeChannels(1f)
    assertEquals(1f, end.contour, 0f)
    assertEquals(1f, end.lens, 0f)
    assertEquals(1f, end.symbolAlpha, 0f)
    assertEquals(1f, end.symbolScale, 0f)
    // R4 B3: nei primi istanti c'è il solo contorno (pieno quando la lente non è ancora a un decimo).
    val early = glassMaterializeChannels(GlassMaterializeContourEnd)
    assertEquals(1f, early.contour, 0f)
    assertTrue(early.lens < 0.1f)
    assertEquals(0f, early.symbolAlpha, 0f)
  }

  @Test
  fun aFullRecipeIsUntouchedAndAnEmptyOneIsTheSharpBackdrop() {
    val reference = full()
    val same = glassMaterializeRecipe(full(), 1f)
    assertEquals(reference.materialize, same.materialize, 0f)
    assertEquals(reference.refractionDp, same.refractionDp, 0f)
    assertEquals(reference.veilColor, same.veilColor)
    assertEquals(reference.mix, same.mix, 0f)
    assertEquals(reference.saturation, same.saturation, 0f)
    assertEquals(reference.shadowAmbientAlpha, same.shadowAmbientAlpha, 0f)

    val none = glassMaterializeRecipe(full(), 0f)
    assertEquals("a zero il vetro non c'è", 0f, none.materialize, 0f)
    assertEquals("e lo sfondo è nitido: nessuna sfocatura", 0f, none.blurDp, 0f)
    assertEquals("senza mescolare due rami", reference.mix, none.mix, 0f)
    assertEquals(1f, none.saturation, 0f)
    assertEquals(0f, none.shadowAmbientAlpha, 0f)
    // Il margine della cattura non cambia mai: la cattura non si rifà.
    assertEquals(reference.paddingDp, none.paddingDp, 0f)
    assertEquals(reference.captureScale, none.captureScale, 0f)
  }

  @Test
  fun theFrostGrowsWithTheLensOnOneBranchSoThereIsNeverADoubleImage() {
    val reference = full()
    assertTrue("il profilo di serie è a un ramo solo a riposo", reference.mix == 0f)
    var previous = -1f
    var m = 0f
    while (m <= 1f) {
      val r = glassMaterializeRecipe(full(), m)
      val lens = glassMaterializeChannels(m).lens
      // Il raggio è glassMixRadius di F3.4 con `w = 1 - lente`; la mescola resta a un ramo per tutta la comparsa.
      assertEquals("m=$m", reference.blurDp * lens, r.blurDp, 1e-4f)
      assertEquals("m=$m", 0f, r.mix, 0f)
      assertTrue("m=$m: il raggio non torna indietro", r.blurDp >= previous)
      previous = r.blurDp
      m += 0.01f
    }
  }

  @Test
  fun inTheShaderProductTheLensAndTheBodyFollowTheLensChannel() {
    val reference = full()
    for (m in listOf(0.1f, 0.3f, 0.5f, 0.8f, 0.95f)) {
      val c = glassMaterializeChannels(m)
      val r = glassMaterializeRecipe(full(), m)
      // Lo shader moltiplica tutto per `materialize` (il contorno): lo spostamento visto è quello della lente.
      assertEquals("m=$m", reference.refractionDp * c.lens, r.refractionDp * r.materialize, 1e-4f)
      assertEquals("m=$m", reference.veilColor.alpha * c.lens, r.veilColor.alpha * r.materialize, 1e-2f)
      assertEquals("m=$m: il contorno", c.contour, r.materialize, 1e-6f)
      // L'opacità del corpo non entra: niente in ricetta la porta, la copertura è dello shader.
      assertTrue(r.mix in 0f..1f && r.blurDp <= reference.blurDp)
    }
  }
}
