package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Test

/** Controllo di convergenza: un allarme d'area non viene promosso a difetto dal solo integratore. */
class MorphRobustnessOracleTest {
  @Test fun convergenzaDeiSaltiIstanteDiRitargeting() {
    for ((name,variant) in listOf("cerchio-lastra" to "inverti25", "raggi-asimmetrici" to "inverti25", "uno-due" to "goccia-interrompi50")) {
      val c=MorphRobustnessCases.all.single { it.name==name }
      for(n in listOf(12,24,48,96)) {
        val result=MorphRobustnessRun.execute(c,variant,120,true,precision=n)
        println("CONVERGENZA $name/$variant griglia=$n")
        result.issues.filterKeys { it.endsWith(":area") || it.contains("baricentro") }.forEach { (k,v) -> println("$k $v") }
      }
    }
  }
}
