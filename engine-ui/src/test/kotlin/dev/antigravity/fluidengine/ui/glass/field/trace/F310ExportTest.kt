package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le tracce delle tavole di F3.10 ([F38aTraces.f310Scenes], fase `f3-10`): «Seleziona» diventa tre con la luce del
 * viaggio agganciata al palco, senza tocco e col tocco. Senza la variabile d'ambiente `GLASS_F310_DIR` si costruiscono e
 * si controllano; con la variabile si scrivono sotto quella cartella (`<nome>/trace.json`), per il banco.
 *
 * I controlli leggono i numeri che il palco mette in ogni campo (`_luce`, `_dito`, `_riempito`, scritti dalla stessa
 * `fluidGlassStagePlane` del contenitore): la luce del carattere parte da zero e si spegne prima del riposo; il tocco fa
 * partire il campo dal bagliore che il vetro fermo mostrava, lo riempie e lo spegne col carattere; senza tocco niente.
 */
class F310ExportTest {

  private val traces by lazy { F38aTraces.f310Scenes().associate { it.name to F38aTraces.build(it) } }

  private fun stage(frame: GlassTraceFrame): Map<String, Any?>? =
    frame.elements.firstOrNull { (it["name"] as? String)?.startsWith("palco") == true }

  private fun number(map: Map<String, Any?>?, key: String): Double = (map?.get(key) as? Number)?.toDouble() ?: 0.0

  @Test
  fun theTracesBuildAndExportOnlyWhenAsked() {
    val dir = GlassTraceOutput.requestedDir(System.getenv(Variable))
    println("tracce di F3.10: $Variable = ${System.getenv(Variable)} -> ${dir ?: "niente da scrivere"}")
    assertEquals(9, traces.size)
    for (trace in traces.values) {
      assertEquals("f3-10", trace.phase)
      assertTrue(trace.name, trace.frames.all { it.elements.isNotEmpty() })
      dir?.let { trace.write(File(File(it, trace.name), "trace.json")) }
    }
  }

  @Test
  fun theLightOfTheJourneyRisesFromZeroAndIsGoneBeforeRest() {
    for (fund in listOf("chiaro", "scuro", "testo")) {
      val lit = traces.getValue("seleziona-$fund-meta")
      val first = lit.frames.first { it.tMs >= 0.0 }
      assertEquals("$fund: al primo fotogramma del palco la luce è zero", 0.0, number(stage(first), "_luce"), 0.0)
      val peak = lit.frames.mapNotNull { stage(it) }.maxOf { number(it, "_luce") }
      assertTrue("$fund: la luce arriva piena ($peak)", peak > 0.99)
      // F3.12a: il riposo a vista è a 500 ms (era 300): il 90% del viaggio è 450 ms.
      val late = lit.frames.filter { it.tMs >= 455.0 }.mapNotNull { stage(it) }
      assertTrue("$fund: spenta dal 90% del viaggio", late.all { number(it, "_luce") == 0.0 })
      // Senza tocco nessun bagliore.
      assertTrue("$fund: senza tocco nessun dito", lit.frames.mapNotNull { stage(it) }.all { number(it, "_dito") == 0.0 })
      // «oggi» (F3.8a): niente luce.
      val before = traces.getValue("seleziona-$fund-oggi")
      assertTrue("$fund: oggi senza luce", before.frames.mapNotNull { stage(it) }.all { number(it, "_luce") == 0.0 })
    }
  }

  @Test
  fun aTouchStartsTheFieldFromThePressAndFillsIt() {
    for (fund in listOf("chiaro", "scuro", "testo")) {
      val touched = traces.getValue("seleziona-$fund-dito")
      val first = stage(touched.frames.first { it.tMs >= 0.0 })
      assertEquals("$fund: il campo parte dal bagliore della pressione", 0.8, number(first, "_dito"), 1e-3)
      assertEquals("$fund: con la campana del vetro fermo", 0.0, number(first, "_riempito"), 1e-3)
      val full = stage(touched.frames.first { it.tMs >= 120.0 })
      assertTrue("$fund: a 120 ms il bagliore è pieno", number(full, "_dito") > 0.99)
      val half = stage(touched.frames.first { it.tMs >= 255.0 })
      assertTrue("$fund: a metà viaggio il bagliore ha riempito il vetro", number(half, "_riempito") > 0.98)
      val late = touched.frames.filter { it.tMs >= 455.0 }.mapNotNull { stage(it) }
      assertTrue("$fund: spento col carattere", late.all { number(it, "_dito") == 0.0 })
    }
  }

  private companion object {
    const val Variable = "GLASS_F310_DIR"
  }
}
