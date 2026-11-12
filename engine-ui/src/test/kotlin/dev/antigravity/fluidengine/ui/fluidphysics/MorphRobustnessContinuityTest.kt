package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Criteri numerici del banco: non misurano la visibilità di uno scatto sul raster. */
internal class MorphRobustnessContinuity {
  companion object {
    // Solo sicura contro esplosioni: 20 scale/s NON è una certificazione di continuità visiva.
    const val ExplosionSpeed = 20f
    // La molla di default (risposta ~0,36 s) ha omega² ~305/s². 400/s² lascia margine
    // al campionamento e alle grandezze normalizzate, ma distingue un impulso da velocità costante.
    // Non è un limite percettivo né una prova matematica di continuità fra i campioni.
    const val Acceleration = 400f
  }
  private var previous: FloatArray? = null
  private var previousTime = 0L
  private var velocity: FloatArray? = null
  private var velocityDt = 0f

  fun sample(time: Long, values: FloatArray, labels: List<String>, report: (String, String) -> Unit) {
    val old = previous
    if (old == null) { previous = values; previousTime = time; return }
    val dt = (time - previousTime) / 1e9f
    if (dt == 0f) {
      values.indices.forEach { if (abs(values[it]-old[it]) > tolerance(labels[it]))
        report("passo-zero:${labels[it]}", "delta=${values[it]-old[it]}") }
      return
    }
    require(dt > 0f)
    val v = FloatArray(values.size) { (values[it]-old[it])/dt }
    for (i in values.indices) {
      val eps = tolerance(labels[i])
      if (abs(values[i]-old[i]) > ExplosionSpeed*dt + eps)
        report("esplosione:${labels[i]}", "delta=${values[i]-old[i]} limite=${ExplosionSpeed*dt+eps}")
      velocity?.let { prior ->
        val midpointDt = (dt+velocityDt)/2f
        // Due differenze contengono due errori di quantizzazione; i colori hanno 8 bit.
        val budget = Acceleration*midpointDt + eps/dt + eps/velocityDt
        if (abs(v[i]-prior[i]) > budget)
          report("accelerazione:${labels[i]}", "a=${(v[i]-prior[i])/midpointDt} limite=${budget/midpointDt}")
      }
    }
    previous = values; previousTime = time; velocity = v; velocityDt = dt
  }
  private fun tolerance(label: String) = if (label.startsWith("velo-")) 1f/255f else .001f
}

class MorphRobustnessContinuityTest {
  @Test fun distingueUnoScattoDaUnTrattoVeloce() {
    val straight = MorphRobustnessContinuity()
    val defects = mutableListOf<String>()
    for (i in 0..12) straight.sample(i*16_666_667L,floatArrayOf(i*.1f),listOf("baricentro-x")) { k,_ -> defects += k }
    assertTrue(defects.isEmpty()) // 6 scale/s costanti, pur veloci.
    val impulse = MorphRobustnessContinuity()
    for ((t,x) in listOf(0L to 0f,16_666_667L to 0f,33_333_334L to .15f))
      impulse.sample(t,floatArrayOf(x),listOf("baricentro-x")) { k,_ -> defects += k }
    assertTrue(defects.contains("accelerazione:baricentro-x"))
    assertFalse(defects.any { it.startsWith("esplosione") })
  }
  @Test fun confrontaIlPrimoCampioneColRiposoEControllaIlPassoZero() {
    val m = MorphRobustnessContinuity(); val defects = mutableListOf<String>()
    m.sample(0L,floatArrayOf(0f),listOf("area")) { k,_ -> defects += k }
    m.sample(8_333_333L,floatArrayOf(.5f),listOf("area")) { k,_ -> defects += k }
    assertTrue(defects.contains("esplosione:area"))
    m.sample(8_333_333L,floatArrayOf(.7f),listOf("area")) { k,_ -> defects += k }
    assertTrue(defects.contains("passo-zero:area"))
  }
}
