package dev.antigravity.fluidengine.viewer.scene

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * F6C.20 · «Rotazione automatica» di «•••»: la camera gira attorno al modello sull'asse verticale, come `auto-rotate` di
 * `<model-viewer>`. Il modello è inquadrato col centro nell'origine ([SceneTexture]), quindi girare occhio, punto
 * guardato e alto della camera attorno all'asse Y che passa per l'origine è lo stesso che girare il modello sul suo
 * piedistallo; il dito resta quello del manipolatore, girato insieme. Kotlin puro.
 */
internal object SceneSpin {
  /** La velocità: `rotation-per-second` di serie di `<model-viewer>` (Google), 32 gradi al secondo. */
  const val DegreesPerSecond: Double = 32.0

  /**
   * Quando il dito lascia il modello, la rotazione riprende dopo questa pausa: `auto-rotate-delay` di serie di
   * `<model-viewer>`, 3000 ms.
   */
  const val ResumeDelayNanos: Long = 3_000_000_000L

  private const val Turn = 2 * PI

  /** L'angolo [angle] (radianti) dopo [elapsedNanos] di rotazione, ridotto a un giro. */
  fun advance(angle: Double, elapsedNanos: Long): Double {
    if (elapsedNanos <= 0L) return angle
    return (angle + Math.toRadians(DegreesPerSecond) * elapsedNanos / 1_000_000_000.0) % Turn
  }

  /** Gira ogni vettore di [vectors] (x, y, z) di [angle] radianti attorno all'asse Y che passa per l'origine. */
  fun turn(angle: Double, vararg vectors: DoubleArray) {
    if (angle == 0.0) return
    val c = cos(angle)
    val s = sin(angle)
    for (v in vectors) {
      val x = v[0]
      val z = v[2]
      v[0] = x * c + z * s
      v[2] = -x * s + z * c
    }
  }
}
