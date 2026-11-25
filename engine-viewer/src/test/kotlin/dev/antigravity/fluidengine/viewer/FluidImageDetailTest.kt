package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.18 · I tasselli delle foto enormi: quando serve il tassello (solo oltre la risoluzione della foto ridotta), quale
 * parte dell'originale si decodifica (quella in vista, con lo zoom e lo spostamento del telaio), a che campionamento
 * (almeno un pixel dell'originale per pixel dello schermo) e dove si disegna.
 */
class FluidImageDetailTest {
  // Una foto da 192 MP (16000 × 12000) ridotta a 12 MP (4000 × 3000), larga quanto il telefono (1080 px).
  private val source = IntSize(16_000, 12_000)
  private val box = IntSize(1080, 810)
  private val viewport = IntSize(1080, 2340)

  @Test
  fun `the reduced photo is enough until the zoom goes past its resolution`() {
    assertNull(imageDetailPlan(source, 4000, box, viewport, 1f, 0f, 0f))
    assertNull(imageDetailPlan(source, 4000, box, viewport, 4f, 0f, 0f))
    assertNotNull(imageDetailPlan(source, 4000, box, viewport, 5f, 0f, 0f))
    // Una foto non ridotta non ha tasselli.
    assertNull(imageDetailPlan(IntSize(4000, 3000), 4000, box, viewport, 8f, 0f, 0f))
  }

  @Test
  fun `the visible part of the original is decoded at screen resolution`() {
    val plan = checkNotNull(imageDetailPlan(source, 4000, box, viewport, 8f, 0f, 0f))
    // Al centro: 1080 / 8 = 135 px del riquadro, cioè 2000 px dell'originale; in altezza 2340 / 8 = 292,5 px, cioè
    // da 3833 a 8167 px dell'originale.
    assertEquals(7000f, plan.source.left.toFloat(), 2f)
    assertEquals(9000f, plan.source.right.toFloat(), 2f)
    assertEquals(3833f, plan.source.top.toFloat(), 2f)
    assertEquals(8167f, plan.source.bottom.toFloat(), 2f)
    // 2000 px dell'originale su 1080 px dello schermo: campionare a 2 darebbe meno di un pixel per pixel.
    assertEquals(1, plan.sample)
    assertEquals(472.5f, plan.local.left, 0.5f)
    assertEquals(607.5f, plan.local.right, 0.5f)
    // Più su di zoom, meno originale: a 16× sono 1000 px, ancora a campionamento 1.
    val closer = checkNotNull(imageDetailPlan(source, 4000, box, viewport, 16f, 0f, 0f))
    assertTrue(closer.source.width < plan.source.width)
  }

  @Test
  fun `the pan moves the tile and the edges stay inside the photo`() {
    // Spostati a destra di 3000 px dello schermo: si guarda la parte sinistra della foto.
    val left = checkNotNull(imageDetailPlan(source, 4000, box, viewport, 8f, 3000f, 0f))
    assertTrue(left.source.left < 7000)
    assertTrue(left.local.left >= 0f)
    // Oltre il bordo non si vede niente.
    assertNull(imageDetailPlan(source, 4000, box, viewport, 8f, 100_000f, 0f))
  }

  @Test
  fun `a big view of a huge photo is sampled down`() {
    // A 5× su un tablet largo 2560 px la foto in vista è tutta l'altezza e metà della larghezza: si campiona.
    val tablet = IntSize(2560, 1920)
    val plan = checkNotNull(imageDetailPlan(source, 4000, tablet, IntSize(2560, 1600), 5f, 0f, 0f))
    assertTrue(plan.sample >= 1)
    val pixels = plan.output.width.toLong() * plan.output.height
    assertTrue("il tassello resta della misura dello schermo: $pixels", pixels <= 4L * 2560 * 1600)
  }
}
