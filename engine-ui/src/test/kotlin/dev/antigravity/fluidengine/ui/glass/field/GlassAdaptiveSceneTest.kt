package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassSceneJson
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import dev.antigravity.fluidengine.ui.glass.field.trace.parseColor
import dev.antigravity.fluidengine.ui.glass.field.trace.sceneSurface
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Le scene del materiale adattivo sul banco (`tools/glassbench/scenes/f2/adattivo-*.json`) descrivono ciò che l'engine
 * disegnerà (piano 4.2, «scene dalla ricetta»).
 *
 * Le scrive uno specchio Python (`artifacts/liquid-glass/bench/f2-2/adattivo.py`), perché F2.2 ha tarato sul banco
 * prima di poter compilare (come F2.4 col bordo). Questo test chiude il giro al contrario: per ogni lastra legge ciò
 * che la scena dichiara di aver chiesto (`_adattivo`: ruolo del banco, taglia, lato del tema, lato continuo, misura
 * della sonda, cursore, variante, tinta, i numeri dell'adattività), rifà la lastra con [glassRecipe] e
 * [liquidElement] e confronta catena, corpo, luce e ombra. Se la ricetta cambia senza che le scene si rigenerino, o
 * lo specchio diverge, fallisce: le tavole di F2.2 non descriverebbero più l'engine.
 *
 * Tolleranze: i colori di un livello su 255 (lo specchio e Compose arrotondano lo stesso numero in doppia e in
 * singola precisione), i numeri un millesimo.
 */
class GlassAdaptiveSceneTest {

  private val folder: File by lazy { File(GlassTraceOutput.repoRoot(), "tools/glassbench/scenes/f2") }

  @Test
  fun everySlabInTheAdaptiveScenesIsWhatTheRecipeComputes() {
    val files = folder.listFiles { f -> f.name.startsWith("adattivo-") && f.name.endsWith(".json") }
      ?.sortedBy { it.name } ?: emptyList()
    assertTrue("Nessuna scena adattiva in $folder", files.size >= 20)
    var slabs = 0
    val problems = mutableListOf<String>()
    for (file in files) {
      val scene = GlassSceneJson.parseObject(file.readText(Charsets.UTF_8))
      @Suppress("UNCHECKED_CAST")
      val elements = scene["elements"] as List<Map<String, Any?>>
      for (el in elements) {
        @Suppress("UNCHECKED_CAST")
        val meta = el["_adattivo"] as? Map<String, Any?> ?: continue
        slabs++
        problems += check("${file.name}: ${el["name"]}", el, meta)
      }
    }
    if (problems.isNotEmpty()) fail(problems.take(40).joinToString("\n") + "\n(${problems.size} in tutto)")
    println("Lastre adattive confrontate con l'engine: $slabs in ${files.size} scene di ${folder.path}")
    assertTrue(slabs >= 40)
  }

  private fun num(value: Any?): Float = (value as Number).toFloat()

  private fun check(where: String, el: Map<String, Any?>, meta: Map<String, Any?>): List<String> {
    val out = mutableListOf<String>()
    val surface = sceneSurface(meta["benchRole"] as String)
    @Suppress("UNCHECKED_CAST")
    val asked = meta["adaptive"] as Map<String, Any?>
    val adaptive = GlassLiquidAdaptives.Shipped.copy(
      shadowOnText = num(asked["shadowOnText"]),
      shadowHue = num(asked["shadowHue"]),
      clearDimFollow = num(asked["clearDimFollow"]),
      tintHalo = num(asked["tintHalo"]),
      tintRim = num(asked["tintRim"]),
      lightFloor = num(asked["lightFloor"]),
    )
    val profile = GlassLiquidProfile.Shipped.copy(adaptive = adaptive)
    val material = GlassAdaptiveMaterial()
    if (meta["measured"] == true) {
      material.measure(
        luminance = num(meta["luminance"]),
        deviation = num(meta["deviation"]),
        texture = num(meta["texture"]),
        color = parseColor(meta["color"] as String),
        spread = adaptive.darkLevelSpread,
      )
    } else {
      material.unmeasured()
    }
    val darkness = (meta["darkness"] as? Number)?.toFloat()
    if (darkness != null) {
      material.darkness = darkness
      // Un grande scurisce con la quota di nero che la sonda stima: lo specchio deve averla calcolata come l'engine.
      if (meta["measured"] == true && meta["dark"] == false) {
        val share = glassBlackShare(num(meta["luminance"]), num(meta["variance"]))
        if (abs(share - darkness) > 1e-4f) out += "$where: quota di nero $darkness, l'engine dice $share"
      }
    }
    val recipe = glassRecipe(
      role = surface.role,
      widthDp = num(meta["w"]),
      heightDp = num(meta["h"]),
      darkSide = meta["dark"] == true,
      variant = if (meta["variant"] == "clear") GlassLiquidVariant.Clear else GlassLiquidVariant.Regular,
      optics = surface.optics,
      tint = (meta["tint"] as? String)?.let(::parseColor) ?: Color.Unspecified,
      profile = profile,
      adaptive = material,
      preference = num(meta["preference"]),
    )
    val expected = liquidElement(el, recipe, theme = null, note = "")
    for (key in listOf("capture", "body", "light", "shadow")) {
      same("$where.$key", expected[key], el[key], out)
    }
    return out
  }

  /** [expected] (dall'engine) contro [actual] (dalla scena), ricorsivo. */
  private fun same(where: String, expected: Any?, actual: Any?, out: MutableList<String>) {
    when {
      expected is Map<*, *> && actual is Map<*, *> -> {
        if (expected.keys != actual.keys) {
          out += "$where: chiavi ${expected.keys - actual.keys} mancano, ${actual.keys - expected.keys} in più"
          return
        }
        for (k in expected.keys) same("$where.$k", expected[k], actual[k], out)
      }
      expected is List<*> && actual is List<*> -> {
        if (expected.size != actual.size) {
          out += "$where: ${expected.size} contro ${actual.size}"
          return
        }
        for (i in expected.indices) same("$where[$i]", expected[i], actual[i], out)
      }
      expected is Number && actual is Number -> {
        val e = expected.toDouble()
        val a = actual.toDouble()
        if (abs(e - a) > 1e-3 + 1e-3 * abs(e)) out += "$where: l'engine $e, la scena $a"
      }
      expected is String && actual is String && expected.startsWith("#") && actual.startsWith("#") -> {
        val e = channels(expected)
        val a = channels(actual)
        if (e.size != a.size || e.indices.any { abs(e[it] - a[it]) > 1 }) out += "$where: l'engine $expected, la scena $actual"
      }
      expected is String && actual is String && expected.endsWith("dp") && actual.endsWith("dp") -> {
        val e = expected.removeSuffix("dp").toDouble()
        val a = actual.removeSuffix("dp").toDouble()
        if (abs(e - a) > 2e-3 * max(1.0, abs(e))) out += "$where: l'engine $expected, la scena $actual"
      }
      expected == null && actual == null -> Unit
      else -> if (expected != actual) out += "$where: l'engine $expected, la scena $actual"
    }
  }

  private fun channels(text: String): List<Int> =
    text.removePrefix("#").chunked(2).map { it.toInt(16) }
}
