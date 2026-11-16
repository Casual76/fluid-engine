package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le tracce di F3.4 si costruiscono a ogni giro di test; si scrivono solo se `GLASS_CONTENT_TRACE_DIR` lo chiede
 * (variabile separata: il giro completo non riesporta le tracce degli altri pacchetti). Di solito
 * `tools/glassbench/traces/f3-contenuto`.
 */
class GlassContentTraceExportTest {

  @Suppress("UNCHECKED_CAST")
  private fun layersOf(frame: GlassTraceFrame) =
    (frame.elements.single()["content"] as List<Map<String, Any?>>)

  @Test fun contentTracesExportWithStillEndsAndContinuousInterruption() {
    val scenarios = GlassContentTraces.scenarios()
    assertEquals(listOf("scambio", "resta", "nasce", "scambio-interrotto", "come-apple"), scenarios.map { it.name })
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_CONTENT_TRACE_DIR"))
    for (scenario in scenarios) {
      val trace = GlassContentTraces.build(scenario)
      assertTrue(trace.encode().isNotEmpty())
      // Il preambolo è fermo e uguale al fotogramma a t = 0: il cambio non salta alla partenza.
      val zero = trace.frames.first { it.tMs == 0.0 }
      trace.frames.filter { it.tMs < 0.0 }.forEach { assertEquals(layersOf(zero), layersOf(it)) }
      // In coda il contenuto d'arrivo è fermo e nitido: gli ultimi cinque fotogrammi sono uguali.
      val tail = trace.frames.takeLast(5).map { layersOf(it) }
      tail.forEach { assertEquals(tail.first(), it) }
      tail.first().forEach { layer ->
        assertEquals(1.0, layer["alpha"] as Double, 1e-6)
        assertEquals(1.0, layer["scale"] as Double, 1e-6)
        assertEquals(0.0, layer["blurDp"] as Double, 1e-6)
      }
      // Il «più» che resta non si sfoca mai, nemmeno mentre corre col vetro (R7 A3).
      if (scenario.name == "resta") {
        trace.frames.forEach { frame ->
          val more = layersOf(frame).filter { (it["name"] as String).startsWith("piu-") }
          assertEquals(1, more.size)
          assertEquals(0.0, more.single()["blurDp"] as Double, 0.0)
          assertEquals(1.0, more.single()["alpha"] as Double, 0.0)
        }
      }
      // Chi nasce non ha nessuno davanti: nel primo fotogramma visibile è a scala 0,75.
      if (scenario.name == "nasce") {
        val first = trace.frames.map { layersOf(it).single() }.first { (it["alpha"] as Double) > 0.0 }
        assertEquals(0.75, first["scale"] as Double, 1e-3)
      }
      output?.let { directory ->
        val file = File(File(directory, trace.name), "trace.json")
        trace.write(file)
        println("traccia F3.4: $file (${trace.frames.size} fotogrammi)")
      }
    }
  }
}
