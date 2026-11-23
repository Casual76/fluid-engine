package dev.antigravity.fluidengine.viewer.scene

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F6C.20 · Il viewport del modello 3D. Sul Tab S9 «Anelli» aperto dalla galleria restava bianco: l'entrata dalla
 * miniatura fa nascere la superficie della [android.view.TextureView] prima che la sessione parta, e `UiHelper` di
 * Filament annuncia allora la superficie con 0×0 (le misure «desiderate», che non diamo). Il log del tablet:
 * `sessione creata 1600x2560, disponibile true` → `superficie collegata, 1600x2560` → `misure 0x0` → `primo fotogramma`,
 * cioè un fotogramma senza viewport contato come primo. Le misure che mancano si prendono dalla vista.
 */
class SceneViewportTest {
  @Test fun `le misure annunciate valgono quando ci sono`() {
    assertEquals(IntSize(1080, 2340), SceneViewport.of(1080, 2340, 1600, 2560))
  }

  @Test fun `con la superficie gia nata UiHelper annuncia 0x0 e il viewport prende la misura della vista`() {
    assertEquals(IntSize(1600, 2560), SceneViewport.of(0, 0, 1600, 2560))
    assertEquals("una sola misura nulla basta a non fidarsi", IntSize(1600, 2560), SceneViewport.of(1600, 0, 1600, 2560))
  }

  @Test fun `senza misure da nessuna parte non c'e ancora un viewport`() {
    assertNull(SceneViewport.of(0, 0, 0, 0))
    assertNull(SceneViewport.of(0, 0, 1600, 0))
  }
}
