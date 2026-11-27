package dev.antigravity.fluidengine.ui.fluid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2, il minore di F8.11: il tempo degli sfondi vivi ricomincia da zero ogni [FluidLiveBackgroundDefaults.WrapSeconds].
 * Perché il salto non si veda, in quel tempo ogni onda deve fare un numero intero di giri. Le frequenze sono quelle del
 * disegno (`CanvasLive`, in radianti al secondo): aloni (x e y), creste e loro increspatura, stelle.
 */
class F8p11LiveBackgroundWrapTest {
  private val frequencies: List<Float> = buildList {
    for (i in 0 until 3) {
      add(0.14f + 0.03f * i)
      add(0.09f + 0.02f * i)
      add(0.16f + 0.04f * i)
    }
    add(0.11f)
    for (k in 0 until 7) add(0.6f + k * 0.2f)
  }

  @Test
  fun `al ritorno a zero ogni onda ha fatto giri interi`() {
    val wrap = FluidLiveBackgroundDefaults.WrapSeconds.toDouble()
    for (f in frequencies) {
      val turns = f * wrap / (2 * PI)
      // Un millesimo di giro: sotto il decimo di pixel anche per l'alone più largo (14% della pagina).
      assertTrue("$f rad/s in $wrap s: $turns giri", abs(turns - turns.roundToLong()) < 1e-3)
    }
  }
}
