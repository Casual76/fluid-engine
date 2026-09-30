package dev.antigravity.fluidengine.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FluidListRowLabelsTest {

  @Test
  fun `un titolo corto tiene accanto anche etichette larghe`() {
    // 600 di riga, "Verifica" 160, due pillole 300: il titolo ci sta nei 292 che restano.
    assertEquals(
      FluidRowLabelPlacement.Inline,
      fluidRowLabelPlacement(availableWidth = 600, titleWidth = 160, labelsWidth = 300, gap = 8),
    )
  }

  @Test
  fun `un titolo lungo con due pillole le manda sotto invece di spezzarsi`() {
    // Il caso della riga "ORIENTAMENTO: STARTING FROM…" con MODIFICATO e Compito.
    assertEquals(
      FluidRowLabelPlacement.Below,
      fluidRowLabelPlacement(availableWidth = 600, titleWidth = 2400, labelsWidth = 300, gap = 8),
    )
  }

  @Test
  fun `una pillola stretta resta accanto a un titolo lungo`() {
    // Il pannello dell'agenda sul tablet: "Compito" ne prende un quinto.
    assertEquals(
      FluidRowLabelPlacement.Inline,
      fluidRowLabelPlacement(availableWidth = 600, titleWidth = 2400, labelsWidth = 120, gap = 8),
    )
  }

  @Test
  fun `in una colonna stretta la stessa pillola va sotto un titolo lungo`() {
    // La colonna della home a tre colonne: "Compito" ne prende un terzo.
    assertEquals(
      FluidRowLabelPlacement.Below,
      fluidRowLabelPlacement(availableWidth = 440, titleWidth = 2400, labelsWidth = 130, gap = 8),
    )
  }

  @Test
  fun `la soglia e' un quarto della riga, spazio compreso`() {
    assertEquals(
      FluidRowLabelPlacement.Inline,
      fluidRowLabelPlacement(availableWidth = 1000, titleWidth = 5000, labelsWidth = 242, gap = 8),
    )
    assertEquals(
      FluidRowLabelPlacement.Below,
      fluidRowLabelPlacement(availableWidth = 1000, titleWidth = 5000, labelsWidth = 243, gap = 8),
    )
  }

  @Test
  fun `etichette piu' larghe della riga vanno sempre sotto`() {
    assertEquals(
      FluidRowLabelPlacement.Below,
      fluidRowLabelPlacement(availableWidth = 300, titleWidth = 10, labelsWidth = 320, gap = 8),
    )
  }

  @Test
  fun `senza etichette non c'e' niente da spostare`() {
    assertEquals(
      FluidRowLabelPlacement.Inline,
      fluidRowLabelPlacement(availableWidth = 300, titleWidth = 900, labelsWidth = 0, gap = 8),
    )
  }
}
