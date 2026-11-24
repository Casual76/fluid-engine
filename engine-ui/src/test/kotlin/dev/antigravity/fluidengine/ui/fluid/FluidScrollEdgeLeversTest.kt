package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.GlassFieldLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeCaptureScale
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeSplit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il bordo dello scorrimento nella leva 9 di F7 (F7.4): quante passate fa la striscia (presente o assente), la tenuta
 * della cattura mentre il bordo si piega, e la metà risoluzione che resta spenta finché Alessio non la sceglie.
 *
 * Che la striscia esca uguale al bit con una passata sola dove non sfoca, e trasparente senza passate dove non c'è
 * intensità, lo prova il banco (`artifacts/liquid-glass/sessioni/f7-4/`); qui le regole che decidono quando.
 */
class FluidScrollEdgeLeversTest {

  @After
  fun levers() = GlassFieldLevers.resetForTesting()

  // ---- Presente e assente ---------------------------------------------------------------------------------------

  @Test
  fun withoutIntensityTheStripRunsNoPassAndWithItAllOfThem() {
    val soft = GlassScrollEdgeProfile.Soft.blurShare
    assertEquals("nessun contenuto sotto: trasparente, senza passate", 0, glassScrollEdgePasses(0f, null, soft))
    assertEquals(0, glassScrollEdgePasses(-0.3f, null, soft))
    assertEquals(0, glassScrollEdgePasses(Float.NaN, null, soft))
    assertEquals("contenuto sotto: sfocatura e velo", 2, glassScrollEdgePasses(1f, null, soft))
    assertEquals(2, glassScrollEdgePasses(0.4f, null, soft))
    // Sotto la soglia del cancello ma sopra lo zero il materiale esportato porta ancora un velo: si resta al bit.
    assertEquals(2, glassScrollEdgePasses(GlassScrollEdgeOffBelow / 2f, null, soft))
  }

  @Test
  fun aProfileThatDoesNotBlurRunsOnlyTheVeilPass() {
    val bottom = FluidScrollEdgeBottomAutomatic!!
    assertEquals("in basso `Automatic` sfuma senza sfocare", 0f, bottom.blurShare, 0f)
    assertEquals(1, glassScrollEdgePasses(1f, null, bottom.blurShare))
    assertEquals(0, glassScrollEdgePasses(0f, null, bottom.blurShare))
    assertEquals(2, glassScrollEdgePasses(1f, null, GlassScrollEdgeProfile.Hard.blurShare))
  }

  @Test
  fun betweenTwoPagesTheSplitDecides() {
    // La barra ancorata fra due pagine: ogni pagina porta la sua intensità, e `amount` non conta (come negli uniform).
    val split = GlassScrollEdgeSplit(from = 0f, to = 400f, outside = 0f, inside = 0.6f)
    assertEquals(2, glassScrollEdgePasses(0f, split, 1f))
    split.inside = 0f
    assertEquals("tutte e due le pagine senza striscia", 0, glassScrollEdgePasses(1f, split, 1f))
    split.outside = 0.2f
    assertEquals(2, glassScrollEdgePasses(0f, split, 1f))
    // Un taglio rovesciato è «nessun taglio»: decide `amount`.
    val none = GlassScrollEdgeSplit(from = 10f, to = 0f, outside = 1f, inside = 1f)
    assertEquals(0, glassScrollEdgePasses(0f, none, 1f))
    assertEquals(2, glassScrollEdgePasses(1f, none, 1f))
  }

  // ---- La tenuta -------------------------------------------------------------------------------------------------

  /** Una piega della tab bar (62 → 52 dp) a 120 Hz: l'altezza del bordo cambia a ogni fotogramma per 300 ms. */
  private fun fold(from: Int, to: Int): List<Int> = (1..36).map { from + (to - from) * it / 36 }

  @Test
  fun theCaptureKeepsItsSizeWhileTheTabBarFoldsAndSettlesOnceAfter() {
    val hold = GlassScrollEdgeHold(slackPx = 72, settleMillis = 250L)
    val extents = mutableListOf<Int>()
    var now = 0L
    fun frame(actual: Int) {
      extents += hold.extent(now, actual)
      now += 8L
    }
    frame(600)
    assertEquals(600, hold.heldPx)
    assertNull("alla nascita la taglia è esatta", hold.settleAtMillis())
    // Si piega: 600 → 570 px. La cattura tiene 600 per tutta la piega.
    fold(600, 570).forEach(::frame)
    assertEquals(setOf(600), extents.toSet())
    val settle = hold.settleAtMillis()!!
    // Ferma: tiene finché la pausa non è passata, poi torna esatta una volta sola.
    while (now < settle) frame(570)
    frame(570)
    assertEquals(570, hold.heldPx)
    assertNull(hold.settleAtMillis())
    // Si riapre: la prima crescita prende il margine, e il resto della riapertura ci sta dentro.
    fold(570, 600).forEach(::frame)
    assertEquals(570 + 1 + 72, hold.heldPx)
    val reopen = hold.settleAtMillis()!!
    while (now < reopen) frame(600)
    frame(600)
    assertEquals(600, hold.heldPx)
    // In tutto quattro taglie, cioè tre registrazioni, contro una per fotogramma senza tenuta.
    val changes = extents.zipWithNext().count { (a, b) -> a != b }
    val withoutHold = (listOf(600) + fold(600, 570) + fold(570, 600)).zipWithNext().count { (a, b) -> a != b }
    println("FluidScrollEdgeLevers tenuta: $changes taglie nuove contro $withoutHold senza tenuta")
    assertEquals(3, changes)
    assertTrue(withoutHold >= 55)
  }

  @Test
  fun duranteIlMotoRidottoIlBordoMantieneIlVeloConUnaPassata() {
    assertEquals(2, glassScrollEdgePasses(1f, null, 1f))
    assertEquals(1, glassScrollEdgePasses(1f, null, 1f, singleAxis = true))
    assertEquals(0, glassScrollEdgePasses(0f, null, 1f, singleAxis = true))
  }

  @Test
  fun aStillEdgeNeverChangesItsCapture() {
    val hold = GlassScrollEdgeHold(slackPx = 72)
    var now = 0L
    repeat(500) {
      assertEquals(420, hold.extent(now, 420))
      now += 8L
    }
    assertNull(hold.settleAtMillis())
  }

  // ---- Le leve di serie ------------------------------------------------------------------------------------------

  @Test
  fun byDefaultTheLeversWithoutVisibleChangeAreOnAndHalfResolutionIsOff() {
    GlassFieldLevers.resetForTesting()
    assertTrue(GlassFieldLevers.edgeHold)
    assertTrue(GlassFieldLevers.edgeLean)
    assertFalse("la metà risoluzione cambia la resa: la sceglie Alessio", GlassFieldLevers.edgeHalf)
    assertEquals(1f, GlassScrollEdgeCaptureScale, 0f)
  }

  @Test
  fun atHalfResolutionTheComponentKeepsItsHeight() {
    // Il componente è alto `height + reach` della risoluzione piena: a metà la portata del bordo morbido è la stessa, e
    // cambia solo dove la striscia comincia a dissolversi (la tavola per Alessio).
    val soft = GlassScrollEdgeProfile.Soft
    assertEquals(soft.atResolution(1f).reach, soft.atResolution(GlassScrollEdgeHalfScale).reach, 0f)
    assertTrue(soft.atResolution(GlassScrollEdgeHalfScale).fadeFrom < soft.fadeFrom)
    val bottom = FluidScrollEdgeBottomAutomatic!!
    assertEquals(bottom.atResolution(1f).reach, bottom.atResolution(GlassScrollEdgeHalfScale).reach, 0f)
  }
}
