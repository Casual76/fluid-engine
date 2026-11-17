package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le tracce del giro di correzione di F3 ([F38aTraces.correctionScenes], fase `f3-corr`): ogni scena di nascita e di
 * confluenza **col contenitore stretto della galleria** (`-stretto`), e «uno-due» fatto partire da un tocco su «Modifica»
 * premuto, nei due contenitori. Senza la variabile d'ambiente `GLASS_FCORR_DIR` si costruiscono e si controllano, senza
 * scrivere niente; con la variabile si scrivono sotto quella cartella (`<nome>/trace.json`), accanto a quelle di F3.8a e di
 * F3.10 scritte con `GLASS_F38A_DIR` e `GLASS_F310_DIR` sulla stessa cartella.
 *
 * I controlli leggono la traccia come una tavola: dove arriva il bordo di testa del vetro che nasce, quanto è spinto chi
 * accoglie, che misura ha il tasto premuto al primo fotogramma del palco.
 */
class F3CorrExportTest {

  private val scenes by lazy { F38aTraces.correctionScenes() }
  private val traces by lazy { scenes.associate { it.name to F38aTraces.build(it) } }
  private val wide by lazy {
    F38aTraces.scenes().filter { "${it.name}-stretto" in traces }.associate { it.name to F38aTraces.build(it) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun elements(frame: GlassTraceFrame): List<Map<String, Any?>> = frame.elements

  private fun number(map: Map<String, Any?>, key: String): Double = (map[key] as? Number)?.toDouble() ?: Double.NaN

  /** Un numero della traccia: una lunghezza è scritta in dp col suffisso (`"12.5dp"`). */
  private fun dp(value: Any?): Double = when (value) {
    is Number -> value.toDouble()
    is String -> value.removeSuffix("dp").toDouble()
    else -> Double.NaN
  }

  /**
   * I pezzi del corpo di ogni elemento del fotogramma il cui nome comincia per [prefix], in dp della tela: (sinistra,
   * destra, mezza larghezza, mezza altezza), dagli uniform `pieceRect` (centro e mezzi lati, rispetto all'elemento).
   */
  @Suppress("UNCHECKED_CAST")
  private fun pieces(frame: GlassTraceFrame, prefix: String): List<DoubleArray> {
    val out = ArrayList<DoubleArray>()
    for (element in elements(frame)) {
      val name = element["name"] as? String ?: continue
      if (!name.startsWith(prefix)) continue
      val x = number(element, "x").takeIf { it.isFinite() } ?: 0.0
      val uniforms = (element["body"] as? Map<String, Any?>)?.get("uniforms") as? Map<String, Any?> ?: continue
      val count = (uniforms["pieceCount"] as? Number)?.toInt() ?: continue
      val rects = uniforms["pieceRect"] as? List<List<Any?>> ?: continue
      for (r in rects.take(count)) {
        val cx = x + dp(r[0])
        val hw = dp(r[2])
        out += doubleArrayOf(cx - hw, cx + hw, hw, dp(r[3]))
      }
    }
    return out
  }

  /** Il bordo destro più lontano del vetro del palco nel fotogramma, in dp; NaN se il palco non c'è. */
  private fun stageRight(frame: GlassTraceFrame): Double =
    pieces(frame, "palco").maxOfOrNull { it[1] } ?: Double.NaN

  @Test
  fun theTracesBuildAndExportOnlyWhenAsked() {
    val dir = GlassTraceOutput.requestedDir(System.getenv(Variable))
    println("tracce del giro di correzione di F3: $Variable = ${System.getenv(Variable)} -> ${dir ?: "niente da scrivere"}")
    assertEquals("nomi unici", traces.size, scenes.size)
    assertTrue("una variante stretta per ogni nascita e confluenza", traces.keys.count { it.endsWith("-stretto") } >= 8)
    for (trace in traces.values) {
      assertTrue(trace.name, trace.frames.all { it.elements.isNotEmpty() })
      dir?.let { trace.write(File(File(it, trace.name), "trace.json")) }
    }
  }

  @Test
  fun aNarrowContainerReachesAsFarAsAWideOne() {
    // La prova differenziale del banco (R3 di C): il bordo di testa del palco, fotogramma per fotogramma, è lo stesso col
    // contenitore stretto della galleria e con la tela larga di F3.12c. Il contorno lo dà la traccia, non il modello.
    for ((name, large) in wide) {
      val narrow = traces.getValue("$name-stretto")
      assertEquals(name, large.frames.size, narrow.frames.size)
      var compared = 0
      for (i in large.frames.indices) {
        val a = stageRight(large.frames[i])
        val b = stageRight(narrow.frames[i])
        if (a.isNaN() && b.isNaN()) continue
        assertEquals("$name t=${large.frames[i].tMs}", a, b, 0.002)
        compared++
      }
      assertTrue("$name: $compared fotogrammi del palco", compared > 30)
    }
  }

  @Test
  fun thePressedButtonStartsTheStagePressed() {
    // R2 di C, rilievo 1 di A: «Modifica» premuto (×1,5 a pressione piena) e il primo fotogramma del palco. Il vetro fermo
    // premuto e il palco hanno la stessa misura, e la misura scende con continuità.
    for (name in listOf("uno-due-tocco", "uno-due-tocco-stretto")) {
      val trace = traces.getValue(name)
      // Il tondo: il più largo dei pezzi alti quanto larghi (il riposo prima del cambio, il palco dopo).
      fun width(frame: GlassTraceFrame): Double = (pieces(frame, "riposo") + pieces(frame, "palco"))
        .filter { kotlin.math.abs(it[2] - it[3]) < 0.5 }.maxOfOrNull { 2.0 * it[2] } ?: 0.0
      val before = trace.frames.last { it.tMs < 0.0 }
      val start = trace.frames.first { it.tMs >= 0.0 }
      val rest = width(before)
      assertTrue("$name: il tasto premuto è più grande: $rest dp", rest > 60.0)
      assertEquals("$name: il primo fotogramma del palco ha la misura premuta", rest, width(start), 3.0)
      val widths = trace.frames.filter { it.tMs >= 0.0 && it.tMs <= 120.0 }.map { width(it) }
      widths.zipWithNext().forEach { (a, b) -> assertTrue("$name: $a -> $b dp in un fotogramma", kotlin.math.abs(a - b) < 6.0) }
    }
  }

  private companion object {
    const val Variable = "GLASS_FCORR_DIR"
  }
}
