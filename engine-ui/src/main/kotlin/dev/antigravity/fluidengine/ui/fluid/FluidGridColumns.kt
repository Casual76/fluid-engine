package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite

/**
 * Quante colonne stanno in [available] dp, con tessere larghe almeno [minItem].
 *
 * Una griglia a due colonne fisse fa tessere da mezzo schermo su un tablet, e una a colonna sola
 * in un pannello da 380 dp le fa giganti: la misura della tessera e' la costante, il numero di
 * colonne ne discende. Il minimo e' quello di una tessera con un'icona e due righe di testo. Mai
 * zero, perche' una griglia senza colonne non e' vuota, e' rotta; mai piu' di [maxColumns], perche'
 * oltre le tessere smettono di essere tessere e diventano icone.
 */
fun fluidGridColumns(
  available: Dp,
  minItem: Dp = 150.dp,
  spacing: Dp = 12.dp,
  maxColumns: Int = 5,
): Int {
  if (!available.isFinite || available <= 0.dp) return 1
  return ((available + spacing) / (minItem + spacing)).toInt().coerceIn(1, maxColumns)
}
