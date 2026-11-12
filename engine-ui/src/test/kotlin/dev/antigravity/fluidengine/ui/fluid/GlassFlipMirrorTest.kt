package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.trace.GlassSceneJson
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * La traccia `giro` del banco (`artifacts/liquid-glass/bench/f2-3/giro.py`) rende i fotogrammi con uno specchio Python
 * della legge del giro. Quello specchio scrive i suoi campioni in `tools/glassbench/scenes/f2/giro-legge.json`; qui
 * l'engine ([GlassFlipLaw]) li rifà. Se divergono, le immagini del banco non descrivono più l'engine.
 */
class GlassFlipMirrorTest {

  private val file: File by lazy { File(GlassTraceOutput.repoRoot(), "tools/glassbench/scenes/f2/giro-legge.json") }

  private fun num(value: Any?): Float = (value as Number).toFloat()

  @Test
  fun theBenchMirrorIsTheEngineLaw() {
    assertTrue("manca $file", file.isFile)
    val doc = GlassSceneJson.parseObject(file.readText(Charsets.UTF_8))
    @Suppress("UNCHECKED_CAST")
    val cases = doc["cases"] as List<Map<String, Any?>>
    assertTrue(cases.size >= 6)
    val problems = mutableListOf<String>()
    for (case in cases) {
      @Suppress("UNCHECKED_CAST")
      val spec = (case["spec"] as List<Any?>).map { (it as Number).toInt() }
      val timing = GlassFlipTiming(spec[0], spec[1], spec[2], spec[3])
      val law = GlassFlipLaw(timing)
      law.snap(dark = case["start"] == true)
      @Suppress("UNCHECKED_CAST")
      val turns = (case["turns"] as List<List<Any?>>).map { (it[0] as Number).toLong() to (it[1] == true) }.toMutableList()
      @Suppress("UNCHECKED_CAST")
      for (sample in case["samples"] as List<List<Any?>>) {
        val t = (sample[0] as Number).toLong()
        while (turns.isNotEmpty() && turns.first().first <= t) {
          val (at, dark) = turns.removeAt(0)
          law.turn(at, dark)
        }
        val body = law.body(t)
        val ink = law.ink(t)
        if (abs(body - num(sample[1])) > 2e-4f || abs(ink - num(sample[2])) > 2e-4f) {
          problems += "${case["timing"]} ${case["name"]} a $t ms: specchio ${sample[1]} / ${sample[2]}, engine $body / $ink"
        }
      }
    }
    if (problems.isNotEmpty()) fail(problems.take(30).joinToString("\n") + "\n(${problems.size} in tutto)")
  }
}
