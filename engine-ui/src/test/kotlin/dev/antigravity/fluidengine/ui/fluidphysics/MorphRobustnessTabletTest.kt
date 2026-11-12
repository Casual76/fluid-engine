package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Test

/** Gli stessi casi/gesti della pagina: nessun raster GPU e nessuna identità degli istanti col video. */
class MorphRobustnessTabletTest {
  @Test fun incidentiS2() {
    for((name,variant) in listOf("un-pixel-inverso" to "inverti50","taglia-24-600-inverso" to "terza75"))
      for(hz in listOf(60,120)) for(width in listOf(1000f,1440f)) {
        val c=MorphRobustnessCases.all.single { it.name==name }
        val result=MorphRobustnessRun.execute(c,variant,hz,true,stageWidth=width,density=2f,gallery=true)
        println("INCIDENTE $name/$variant ${hz}Hz palco=$width densita=2")
        result.issues.forEach { (k,v) -> println("  $k: $v") }
        result.frames.forEach(::println)
      }
  }
}
