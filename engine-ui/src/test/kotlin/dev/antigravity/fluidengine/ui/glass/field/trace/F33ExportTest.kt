package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le tracce e le scene di F3.3 ([F33Traces]). Senza la variabile d'ambiente `GLASS_F33_DIR` si costruiscono e si
 * controllano, senza scrivere niente (i giri normali dei test non lasciano file); con la variabile si scrivono sotto
 * quella cartella, una per traccia (`<nome>/trace.json`).
 */
class F33ExportTest {

  @Test
  fun theJourneysCarryTheNeckAndTheAppearanceAndExportOnlyWhenAsked() {
    val dir = GlassTraceOutput.requestedDir(System.getenv(Variable))
    val traces = (F33Traces.scenarios() + F33Traces.tastes()).map { F33Traces.build(it) } +
      F33Traces.stills().map { F33Traces.buildStill(it) }
    assertEquals("nomi unici", traces.size, traces.map { it.name }.distinct().size)
    for (trace in traces) {
      assertTrue(trace.name, trace.encode().isNotEmpty())
      assertTrue(trace.name, trace.frames.all { it.elements.isNotEmpty() })
      dir?.let { trace.write(File(File(it, trace.name), "trace.json")) }
    }
  }

  @Test
  fun theFirstAndLastFramesOfAJourneyHaveNoNeck() {
    for (scenario in F33Traces.scenarios().filter { it.name.startsWith("collo-") }) {
      val trace = F33Traces.build(scenario)
      for (frame in listOf(trace.frames.first(), trace.frames.last())) {
        frame.elements.forEach { element ->
          assertEquals("${scenario.name} t=${frame.tMs}: a riposo nessun collo", -1.0, element["_collo"] as Double, 0.0)
        }
      }
    }
  }

  companion object {
    const val Variable = "GLASS_F33_DIR"
  }
}
