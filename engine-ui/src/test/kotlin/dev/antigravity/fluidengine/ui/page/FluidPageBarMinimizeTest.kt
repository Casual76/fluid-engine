package dev.antigravity.fluidengine.ui.page

import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il ritiro della barra dell'host (giro di correzione di F4A, B1 e C8): l'indietro, la barra del modello e l'area che
 * riporta in cima sono uno per host. A riposo seguono la pagina sopra; fra due pagine che si muovono la corsa di ognuna,
 * pesata come lo sfondo della barra, così nessun tasto compare o se ne va in un fotogramma.
 */
class FluidPageBarMinimizeTest {

  @Test
  fun `a riposo vale la pagina sopra`() {
    assertEquals(1f, fluidPageBarMinimizeProgressOf(listOf(PageRole.Upper to 1f), moving = false, frontFraction = 0f), 0f)
    assertEquals(
      0.25f,
      fluidPageBarMinimizeProgressOf(listOf(PageRole.Lower to 1f, PageRole.Upper to 0.25f), moving = false, frontFraction = 0f),
      0f,
    )
    assertEquals("senza pagine, aperta", 0f, fluidPageBarMinimizeProgressOf(emptyList(), moving = false, frontFraction = 0f), 0f)
  }

  @Test
  fun `fra una pagina ritirata e una aperta la barra torna con la corsa, senza salti`() {
    // Un push da una pagina ritirata (sotto) a una aperta (sopra): frontFraction va da 1 (appena partito) a 0 (arrivato).
    val entries = listOf(PageRole.Lower to 1f, PageRole.Upper to 0f)
    var previous = fluidPageBarMinimizeProgressOf(entries, moving = true, frontFraction = 1f)
    assertEquals("al primo fotogramma la barra è ancora ritirata", 1f, previous, 1e-6f)
    var step = 0f
    for (i in 1..100) {
      val e = 1f - i / 100f
      val now = fluidPageBarMinimizeProgressOf(entries, moving = true, frontFraction = e)
      step = maxOf(step, previous - now)
      previous = now
    }
    assertEquals("all'arrivo è aperta", 0f, previous, 1e-6f)
    assertEquals("nessun passo più grande di un centesimo di corsa", 0.01f, step, 1e-4f)
  }

  @Test
  fun `una pagina sola che la barra mostra porta la sua corsa anche mentre si muove`() {
    assertEquals(0.6f, fluidPageBarMinimizeProgressOf(listOf(PageRole.Lower to 0.6f), moving = true, frontFraction = 0.3f), 1e-6f)
    assertEquals(0.6f, fluidPageBarMinimizeProgressOf(listOf(PageRole.Upper to 0.6f), moving = true, frontFraction = 0.3f), 1e-6f)
  }

  @Test
  fun `la corsa fuori dai limiti si ferma ai capi`() {
    assertEquals(1f, fluidPageBarMinimizeProgressOf(listOf(PageRole.Upper to 3f), moving = false, frontFraction = 0f), 0f)
    assertEquals(0f, fluidPageBarMinimizeProgressOf(listOf(PageRole.Upper to -1f), moving = true, frontFraction = 0f), 0f)
  }
}
