package dev.antigravity.fluidengine.ui.fluid.share

import org.junit.Assert.assertEquals
import org.junit.Test

/** F8.10 · i conti della lettura a campioni ([FluidImageSample]): potenze di due, mai sotto la miniatura, mai sopra la stampa. */
class FluidImageSampleTest {

  @Test fun aThumbnailKeepsTheShortSideAtLeastAsBigAsAsked() {
    // 4000 × 3000 per una miniatura da 128: 3000 / 16 = 187 (ancora sopra), 3000 / 32 = 93 (sotto): 16.
    assertEquals(16, fluidSampleAtLeast(4000, 3000, 128))
    for (sample in listOf(fluidSampleAtLeast(4000, 3000, 128), fluidSampleAtLeast(1080, 2340, 200))) {
      assertEquals(0, sample and (sample - 1))
    }
    assertEquals(true, 1080 / fluidSampleAtLeast(1080, 2340, 200) >= 200)
  }

  @Test fun aSmallImageIsReadWhole() {
    assertEquals(1, fluidSampleAtLeast(100, 80, 128))
    assertEquals(1, fluidSampleAtMost(2000, 1500, 3508))
  }

  @Test fun printNeverGoesOverTheLongSide() {
    // 5000 px di lato lungo: intera sarebbe oltre i 3508 di un A4 a 300 dpi; a meta' (2500) ci sta.
    assertEquals(2, fluidSampleAtMost(5000, 3000, 3508))
    assertEquals(4, fluidSampleAtMost(12_000, 9_000, 3508))
    for ((w, h) in listOf(5000 to 3000, 12_000 to 9_000, 3509 to 10, 7016 to 7016)) {
      val sample = fluidSampleAtMost(w, h, 3508)
      assertEquals("$w × $h", true, maxOf(w, h) / sample <= 3508)
      assertEquals("$w × $h, non piu' del necessario", true, sample == 1 || maxOf(w, h) / (sample / 2) > 3508)
    }
  }

  @Test fun aZeroTargetReadsWhole() {
    assertEquals(1, fluidSampleAtLeast(4000, 3000, 0))
    assertEquals(1, fluidSampleAtMost(4000, 3000, 0))
  }
}
