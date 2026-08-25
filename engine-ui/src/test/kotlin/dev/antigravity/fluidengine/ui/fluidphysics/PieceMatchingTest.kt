package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceMatchingTest {

  @Test
  fun aMergeSendsBothSourcesToTheOneTarget() {
    val pairs = matchPieces(
      sourceCenters = listOf(Offset(0f, 0f), Offset(200f, 0f)),
      sourceSizes = listOf(80f, 80f),
      targetCenters = listOf(Offset(100f, 0f)),
      targetSizes = listOf(300f),
    )
    assertEquals(2, pairs.size)
    assertTrue(pairs.all { it[1] == 0 })
    assertEquals(setOf(0, 1), pairs.map { it[0] }.toSet())
  }

  @Test
  fun aSplitStartsBothTargetsFromTheOneSource() {
    val pairs = matchPieces(
      sourceCenters = listOf(Offset(100f, 0f)),
      sourceSizes = listOf(300f),
      targetCenters = listOf(Offset(0f, 0f), Offset(200f, 0f)),
      targetSizes = listOf(80f, 80f),
    )
    assertEquals(2, pairs.size)
    assertTrue(pairs.all { it[0] == 0 })
    assertEquals(setOf(0, 1), pairs.map { it[1] }.toSet())
  }

  @Test
  fun threeToTwoLeavesNoOrphanOnEitherSide() {
    val pairs = matchPieces(
      sourceCenters = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(500f, 0f)),
      sourceSizes = listOf(50f, 50f, 50f),
      targetCenters = listOf(Offset(50f, 0f), Offset(500f, 50f)),
      targetSizes = listOf(120f, 60f),
    )
    assertEquals(3, pairs.size)
    // Ogni sorgente compare una volta; ogni destinazione almeno una.
    assertEquals(setOf(0, 1, 2), pairs.map { it[0] }.toSet())
    assertEquals(setOf(0, 1), pairs.map { it[1] }.toSet())
    // Il pezzo lontano va dalla destinazione lontana, non trascinato attraverso lo schermo.
    assertEquals(1, pairs.first { it[0] == 2 }[1])
  }

  @Test
  fun nearestWinsAndTheChoreographyIsDeterministic() {
    val sources = listOf(Offset(0f, 0f), Offset(300f, 0f))
    val sizes = listOf(60f, 60f)
    val targets = listOf(Offset(310f, 0f), Offset(10f, 0f))
    val first = matchPieces(sources, sizes, targets, listOf(60f, 60f))
    val second = matchPieces(sources, sizes, targets, listOf(60f, 60f))

    assertEquals(first.map { it.toList() }, second.map { it.toList() })
    assertEquals(1, first.first { it[0] == 0 }[1])
    assertEquals(0, first.first { it[0] == 1 }[1])
  }

  @Test
  fun costTiesBreakByIndexNotByChance() {
    // Due destinazioni perfettamente equidistanti: vince l'indice più basso, sempre.
    val pairs = matchPieces(
      sourceCenters = listOf(Offset(100f, 0f)),
      sourceSizes = listOf(50f),
      targetCenters = listOf(Offset(0f, 0f), Offset(200f, 0f)),
      targetSizes = listOf(50f, 50f),
    )
    assertEquals(0, pairs.first { it[0] == 0 && it[1] == 0 }[1])
  }
}
