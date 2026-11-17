package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class GlassPlaneTraceExportTest {
  @Test fun sixProductionJourneysExportWithStableBoundaryFramesAndInterruptions() {
    val scenarios = GlassPlaneTraces.scenarios()
    assertEquals(listOf("uno-due", "divisione", "contesto-capsule", "comparsa", "uno-due-interrotto", "divisione-interrotta"),
      scenarios.map { it.name })
    // Variabile separata: il batch completo non deve riesportare le tracce degli altri pacchetti.
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_PLANE_TRACE_DIR"))
    for (scenario in scenarios) {
      val trace = GlassPlaneTraces.build(scenario)
      assertTrue(trace.encode().isNotEmpty())
      assertEquals(80, trace.frames.size)
      assertTrue(trace.frames.any { it.tMs == 0.0 })
      val first = scenario.frame(0.0)
      val last = scenario.frame(1240.0)
      assertTrue(last.finished)
      assertEquals(first.fields, scenario.frame(0.01).fields)
      // La partenza non salta: in 0,01 ms da fermo i lati non cambiano di un centesimo di pixel.
      val justAfter = scenario.frame(0.01)
      first.pieces.zip(justAfter.pieces).forEach { (before, after) ->
        assertEquals(before.bounds.left, after.bounds.left, 0.01f)
        assertEquals(before.bounds.right, after.bounds.right, 0.01f)
      }
      trace.frames.takeLast(5).forEach { frame ->
        assertEquals(last.pieces.map { it.bounds }, scenario.frame(frame.tMs).pieces.map { it.bounds })
      }
      // Anche l'evento è C1: la posizione e la velocità del fotogramma vivo passano al viaggio nuovo.
      scenario.interruptAtMs?.let { time ->
        val before = scenario.journey.frame(time)
        val after = scenario.frame(time)
        before.pieces.forEach { piece ->
          val continuing = after.pieces.single { it.piece.key == piece.piece.key }
          assertEquals(piece.bounds, continuing.bounds)
          assertEquals(piece.velocity, continuing.velocity)
        }
        // I due viaggi interrotti sono una nascita e una confluenza: riposo a vista a 540 ms (F3.12c; 500 in F3.12a,
        // 300 prima).
        assertTrue(abs(scenario.journey.settleMs - 540.0) < 0.5)
      }
      output?.let { directory ->
        val file = File(File(directory, trace.name), "trace.json")
        trace.write(file)
        println("traccia F3.1: $file (${trace.frames.size} fotogrammi)")
      }
    }
  }
}
