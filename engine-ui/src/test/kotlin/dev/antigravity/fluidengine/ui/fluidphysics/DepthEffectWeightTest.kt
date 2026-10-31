package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La profondita' della lente fisica e' un peso (3.0), non piu' un interruttore. Il Kotlin passava
 * 0f o 1f a uno shader che prendeva gia' un float: questi test inchiodano che i chiamanti con un
 * Boolean ottengano ESATTAMENTE quel che ottenevano prima, e che un peso fra 0 e 1 passi com'e'.
 */
class DepthEffectWeightTest {

  @Test
  fun aBooleanIsTheSameZeroOrOneAsBefore() {
    assertEquals(1f, true.asDepthWeight(), 0f)
    assertEquals(0f, false.asDepthWeight(), 0f)
  }

  @Test
  fun aWeightBetweenZeroAndOneReachesTheUniformUntouched() {
    assertEquals(0f, depthEffectUniform(0f), 0f)
    assertEquals(0.35f, depthEffectUniform(0.35f), 0f)
    assertEquals(1f, depthEffectUniform(1f), 0f)
  }

  @Test
  fun aWeightOutsideTheRangeIsHeldAtItsEnds() {
    // Un peso che nasce da un'interpolazione con overshoot (una molla che oltrepassa) non deve
    // spingere la normale radiale oltre quella del bordo.
    assertEquals(0f, depthEffectUniform(-0.2f), 0f)
    assertEquals(1f, depthEffectUniform(1.3f), 0f)
  }

  @Test
  fun theBooleanRoundTripKeepsTheOldBehaviour() {
    // L'interruttore passato per il peso e poi per l'uniform e' l'`if (depthEffect) 1f else 0f` di prima.
    for (flag in listOf(true, false)) {
      assertEquals(if (flag) 1f else 0f, depthEffectUniform(flag.asDepthWeight()), 0f)
    }
  }
}
