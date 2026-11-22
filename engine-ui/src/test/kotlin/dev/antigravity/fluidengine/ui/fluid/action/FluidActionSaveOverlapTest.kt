package dev.antigravity.fluidengine.ui.fluid.action

import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentSmoothstep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Salva» riuscito senza vetro vuoto (scelta di Alessio del 22 novembre 2026, sulle riprese di F6B.12): la spunta entra
 * mentre l'icona esce. Con l'anello (un lavoro oltre i 300 ms) l'anello si chiude, poi la spunta si disegna lungo il suo
 * verso; l'icona di Salva dentro l'anello esce con le curve di chi esce di R7 e resta, sfocata, finché la spunta non c'è.
 * Senza anello (il lavoro breve) è lo scambio sfocato di R7, provato da `F6b12ShortWorkTrace`.
 *
 * Il criterio: in ogni istante qualcosa si legge, cioè l'anello o l'icona hanno almeno il 20% d'opacità, o la spunta è
 * disegnata almeno per un decimo del suo tratto. I tempi di F6B.3b (l'anello chiuso a 48 ms e spento a 67, la spunta
 * dai 67 ai 334 ms) non cambiano: li prova ancora `FluidActionGlyphsTest`.
 */
class FluidActionSaveOverlapTest {
  private val profile = FluidActionGlyphProfile

  private fun legible(ring: Float, icon: Float, check: Float) = ring >= .2f || icon >= .2f || check >= .1f

  @Test fun `con l'anello la spunta entra mentre l'icona esce e non resta un attimo di vetro vuoto`() {
    val end = (profile.SaveDrawStartMs + profile.DrawMs).toFloat()
    var t = 0f
    while (t <= end) {
      assertTrue("a $t ms: anello ${profile.saveRingAlpha(t)}, icona ${profile.saveBaseAlpha(t)}, spunta ${profile.saveReveal(t)}",
        legible(profile.saveRingAlpha(t), profile.saveBaseAlpha(t), profile.saveReveal(t)))
      t += 2f
    }
    // Le due facce convivono: la spunta comincia prima che l'icona sia sparita.
    assertTrue(profile.saveBaseAlpha(profile.SaveDrawStartMs.toFloat()) > .5f)
    assertTrue(profile.SaveBaseExitMs > profile.SaveDrawStartMs + 100f)
    assertEquals(0f, profile.saveBaseAlpha(profile.SaveBaseExitMs), 0f)
    assertEquals(1f, profile.saveBaseAlpha(0f), 0f)
  }

  @Test fun `con l'uscita di prima, in 67 ms, il vuoto c'era`() {
    // La stessa misura con l'icona che usciva tutta nei primi 67 ms: il criterio cade, cioè la prova sopra distingue.
    val before = { ms: Float -> 1f - glassContentSmoothstep(0f, 1f, (ms / profile.SaveDrawStartMs).coerceIn(0f, 1f)) }
    val gaps = (0..334).map { it.toFloat() }.filter { !legible(profile.saveRingAlpha(it), before(it), profile.saveReveal(it)) }
    assertTrue("prima: nessun istante vuoto?", gaps.isNotEmpty())
    assertTrue("il vuoto stava dopo i 67 ms: $gaps", gaps.first() >= profile.SaveDrawStartMs - 10f)
  }

  @Test fun `senza anello lo scambio di R7 tiene sempre una faccia leggibile`() {
    var u = 0f
    while (u <= 1.2f) {
      val sum = GlassContentCurves.exitAlpha.at(u) + GlassContentCurves.enterAlpha.at(u)
      assertTrue("a u = $u la somma è $sum", sum >= .5f)
      u += .01f
    }
    // Le due facce convivono fra 0,36 T e 0,66 T (R7: 70–100 ms sul telefono).
    assertTrue(GlassContentDefaults.EnterAt < GlassContentDefaults.ExitEnd)
  }
}
