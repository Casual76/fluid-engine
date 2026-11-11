package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le misure di [FluidMetrics] (4.0, piano «iOS in Android» F1A.11): i numeri di R6 e delle scelte dell'orchestratore
 * del 2026-11-10, e le relazioni fra loro che i componenti danno per vere.
 *
 * Perche' esiste. Prima ogni componente scriveva le sue taglie a mano, e il giorno in cui una cambiava le altre no.
 * Qui i numeri stanno fermi ai valori decisi, e le regole (l'icona a 0,6 del vetro, il titolo che segue i tasti, i
 * margini misurati sul vetro e non sul riquadro) si provano come aritmetica.
 */
class FluidMetricsTest {

  @Test
  fun `i tasti restano quelli di prima, a 16 dp dal bordo e 8 fra due vetri`() {
    // Il vetro e il bersaglio non cambiano (R6: 44 dp contro 44 pt, il tasto non era piccolo).
    assertEquals(44.dp, FluidMetrics.GlassButtonLens)
    assertEquals(48.dp, FluidMetrics.TouchTarget)
    // Cambiano i margini: 16 dal bordo (era 8), 8 fra due vetri (era 6), 12 fra due gruppi (Apple 12,1 pt).
    assertEquals(16.dp, FluidMetrics.ScreenEdgeMargin)
    assertEquals(8.dp, FluidMetrics.GlassSpacing)
    assertEquals(12.dp, FluidMetrics.GroupSpacing)
    assertTrue(FluidMetrics.GroupSpacing > FluidMetrics.GlassSpacing)
  }

  @Test
  fun `le taglie delle icone sono quelle decise`() {
    assertEquals(24.dp, FluidMetrics.IconSize)
    assertEquals(26.dp, FluidMetrics.BarIconSize)
    assertEquals(24.dp, FluidMetrics.BackIconSize)
    assertEquals(26.dp, FluidMetrics.CapsuleIconSize)
    assertEquals(28.dp, FluidMetrics.TabBarIconSize)
    assertEquals(22.dp, FluidMetrics.MenuIconSize)
  }

  @Test
  fun `l'icona sta a sei decimi del vetro, al dp intero`() {
    assertEquals(0.6f, FluidMetrics.IconLensRatio, 0f)
    assertEquals(24.dp, FluidMetrics.iconSizeIn(40.dp))
    assertEquals(26.dp, FluidMetrics.iconSizeIn(44.dp))
    assertEquals(32.dp, FluidMetrics.iconSizeIn(54.dp))
    assertEquals(46.dp, FluidMetrics.iconSizeIn(76.dp))
    // L'azione della barra e' un tasto da 44: la sua icona e' quella che la regola da' al vetro.
    assertEquals(FluidMetrics.iconSizeIn(FluidMetrics.GlassButtonLens), FluidMetrics.BarIconSize)
    // La sagoma di un'icona e' circa 0,75 del suo riquadro (R6): a 0,6 del vetro sta fra 0,44 e 0,48, quella di Apple.
    val silhouette = FluidMetrics.BarIconSize.value * 0.75f / FluidMetrics.GlassButtonLens.value
    assertTrue("sagoma a $silhouette del vetro", silhouette in 0.43f..0.48f)
  }

  @Test
  fun `la tab bar e' alta 62 con l'etichetta a 11 sp, e ci sta l'icona da 28`() {
    assertEquals(62.dp, FluidMetrics.TabBarHeight)
    assertEquals(11.sp, FluidMetrics.TabBarLabelSize)
    assertEquals(14.sp, FluidMetrics.TabBarLabelLineHeight)
    assertEquals(FluidMetrics.TabBarHeight, FluidFloatingTabBarDefaults.OpenHeight)
    // Il contenuto di una scheda aperta, a testo di serie: 4 + 6 di margini sopra e sotto, l'icona, l'etichetta
    // (2 dp di accostamento fra le due). Deve stare nel pavimento, se no la barra crescerebbe a ogni schermo.
    val content = 4f + 6f + FluidMetrics.TabBarIconSize.value - 2f + FluidMetrics.TabBarLabelLineHeight.value + 6f + 4f
    assertTrue("contenuto $content dp", content <= FluidMetrics.TabBarHeight.value)
  }

  @Test
  fun `il rail delle schede ha la taglia della tab bar, 28 dp, e l'icona ci sta nella scheda`() {
    // Cambio voluto, non un test piegato: fino alla 3.x e fino al pacchetto F1A.11b il rail disegnava 24 dp scritti
    // in FluidTabItemFace e le schede non scelte in `onSurfaceVariant`. Ora e' la stessa barra su due larghezze:
    // taglia dal token della tab bar e inchiostro pieno per tutte (la scelta si legge dalla gemella piena e dalla goccia).
    assertEquals(28.dp, FluidTabBarDefaults.RailIconSize)
    assertEquals(FluidMetrics.TabBarIconSize, FluidTabBarDefaults.RailIconSize)
    // La scheda del rail e' alta 64 dp: l'icona, i 2 dp d'aria e una riga di etichetta (16 dp di `labelSmall`) ci
    // stanno con margine, anche con la scheda scelta sollevata di 1,5 dp.
    val content = FluidTabBarDefaults.RailIconSize.value + 2f + 16f + 1.5f
    assertTrue("contenuto $content dp", content <= FluidTabBarDefaults.Height.value)
  }

  // Le misure della barra in alto (margini e titolo agganciato) stanno in FluidChromeMotionTest, con la barra.
}
