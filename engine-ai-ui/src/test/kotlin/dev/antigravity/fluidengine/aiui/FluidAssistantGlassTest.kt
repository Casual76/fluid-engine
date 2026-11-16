package dev.antigravity.fluidengine.aiui

import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantSurface
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La famiglia delle cinque superfici-pannello dell'assistente sul contenitore (4.0, F3.2e, inventario di
 * `review/F2/G1-inventario.md`): card e dock **scuriscono** in continuo come i grandi, la barra di scrittura, la pillola
 * ridotta e la capsula del composer **girano** come i piccoli. E il ruolo del vetro resta quello di prima, perché sul
 * lato del tema l'aspetto non cambi (stessa ricetta: ruolo, pellicola e ottica sono gli ingressi di `glassSurface`).
 */
class FluidAssistantGlassTest {

  @Test
  fun `card e dock scuriscono, barra, pillola e composer girano`() {
    val darken = FluidAssistantSurface.entries.filter { it.large }.toSet()
    assertEquals(setOf(FluidAssistantSurface.Card, FluidAssistantSurface.Dock), darken)
  }

  @Test
  fun `il ruolo di ogni superficie e' quello che aveva il suo glassSurface`() {
    // Prima di F3.2e: card `Modal`, dock `Floating`, barra `Modal`, pillola `Floating`, composer `Floating`.
    assertEquals(GlassRole.Modal, FluidAssistantSurface.Card.role)
    assertEquals(GlassRole.Floating, FluidAssistantSurface.Dock.role)
    assertEquals(GlassRole.Modal, FluidAssistantSurface.TextBar.role)
    assertEquals(GlassRole.Floating, FluidAssistantSurface.CollapsedPill.role)
    assertEquals(GlassRole.Floating, FluidAssistantSurface.Composer.role)
  }

  @Test
  fun `il ruolo da solo sbaglierebbe due volte, ed e' per questo che la famiglia e' scritta`() {
    // La barra è `Modal` (il ruolo dei grandi) ma gira; il dock è `Floating` ma scurisce.
    assertEquals(GlassRole.Modal, FluidAssistantSurface.TextBar.role)
    assertEquals(false, FluidAssistantSurface.TextBar.large)
    assertEquals(GlassRole.Floating, FluidAssistantSurface.Dock.role)
    assertEquals(true, FluidAssistantSurface.Dock.large)
  }
}
