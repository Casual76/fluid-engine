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
  fun `una pillola sola resta accanto a un titolo lungo`() {
    assertEquals(
      FluidRowLabelPlacement.Inline,
      fluidRowLabelPlacement(availableWidth = 600, titleWidth = 2400, labelsWidth = 140, gap = 8),
    )
  }

  @Test
  fun `la soglia e' quattro decimi della riga, spazio compreso`() {
    assertEquals(
      FluidRowLabelPlacement.Inline,
      fluidRowLabelPlacement(availableWidth = 1000, titleWidth = 5000, labelsWidth = 392, gap = 8),
    )
    assertEquals(
      FluidRowLabelPlacement.Below,
      fluidRowLabelPlacement(availableWidth = 1000, titleWidth = 5000, labelsWidth = 393, gap = 8),
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
