package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect

/**
 * Fluid Engine addition (4.0.0, F8.10): quanto dopo l'ultima cattura presa **in movimento** un vetro su vetro riscrive le
 * proprietà del suo strato di posa ([forceLayerDefaults]). Abbastanza perché il pannello che lo ospita abbia finito di
 * muoversi (lo zoom del foglio finisce circa 100 ms dopo l'ultima cattura della «x», sul Tab S9), poco perché non si veda:
 * l'immagine è già quella giusta, cambia solo uno strato rimasto indietro. Ogni cattura in movimento lo sposta più in là.
 */
internal const val PlacementSettleMillis: Long = 250L

/**
 * Fluid Engine addition (4.0.0, F8.10): scrive in [scope] alfa e `renderEffect` con un valore diverso e poi con quello di
 * serie, così Compose li **riapplica** al `RenderNode` dello strato. Lo scope dei layer è condiviso e segna da riapplicare
 * solo i campi cambiati rispetto al suo ultimo valore: un blocco che non li tocca (lo strato di posa di un vetro scrive
 * solo ritaglio, forma e composizione) non li riscrive mai, qualunque cosa lo strato abbia.
 *
 * Il tablet, 25 novembre 2026: la «x» di vetro del foglio di condivisione restava, a foglio fermo, il disegno sfocato di
 * metà zoom (in una sequenza di aperture a freddo: la prima e poi quasi tutte dall'undicesima). Ridisegnare il vetro,
 * rifarne la cattura, registrare di nuovo il contenuto del tasto o lo strato del foglio non la rimetteva a posto;
 * riscrivere le **proprietà** del suo strato di posa sì, da solo (senza registrare di nuovo il contenuto). Con questa
 * riscrittura a riposo, 0 «x» sfocate su 12 aperture a freddo. Il blocco di serie, dopo, scrive i suoi campi.
 */
internal fun forceLayerDefaults(scope: GraphicsLayerScope) {
  scope.alpha = 0.5f
  scope.alpha = 1f
  scope.renderEffect = ForcingEffect
  scope.renderEffect = null
}

/** Un effetto qualunque, mai applicato: serve solo a far segnare `renderEffect` come cambiato in [forceLayerDefaults]. */
private val ForcingEffect: RenderEffect = BlurEffect(0.5f, 0.5f)
