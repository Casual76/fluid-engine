package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassSceneJson
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Le scene del bordo dello scorrimento sul banco (`tools/glassbench/scenes/f2/bordo-*.json`) descrivono ciò che
 * l'engine disegnerà (piano 4.2, «scene dalla ricetta»).
 *
 * Le scrive uno specchio Python della logica pura (`tools/glassbench/scenes/f2/bordo-genera.py`), perché F2.4 ha tarato
 * il profilo sul banco prima di poter compilare. Questo test chiude il giro al contrario: per ogni striscia legge
 * il profilo che la scena dichiara (`_bordo.variant`: `soft`, `hard`, `fadeOnly`, `soft*0.7`, `soft*1.4`), la
 * risoluzione, la geometria e il colore, rifà gli uniform con [glassScrollEdgeUniforms] e li confronta con quelli
 * scritti. Se il profilo dell'engine cambia senza che le scene si rigenerino, o lo specchio diverge, fallisce: le
 * tavole e le misure di F2.4 non descriverebbero più l'engine.
 */
class GlassScrollEdgeSceneTest {

  private val folder: File by lazy { File(GlassTraceOutput.repoRoot(), "tools/glassbench/scenes/f2") }

  @Test
  fun everyStripInTheBenchScenesHasTheUniformsTheEngineComputes() {
    val files = folder.listFiles { f -> f.name.startsWith("bordo-") && f.name.endsWith(".json") }?.sortedBy { it.name }
      ?: emptyList()
    assertTrue("Nessuna scena del bordo in $folder", files.size >= 30)
    var strips = 0
    val problems = mutableListOf<String>()
    for (file in files) {
      val scene = GlassSceneJson.parseObject(file.readText(Charsets.UTF_8))
      @Suppress("UNCHECKED_CAST")
      val elements = scene["elements"] as List<Map<String, Any?>>
      for (el in elements) {
        @Suppress("UNCHECKED_CAST")
        val meta = el["_bordo"] as? Map<String, Any?> ?: continue
        strips++
        problems += check(file.name, el, meta)
      }
    }
    if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    println("Strisce confrontate con l'engine: $strips in ${files.size} scene di ${folder.path}")
  }

  private fun check(file: String, el: Map<String, Any?>, meta: Map<String, Any?>): List<String> {
    val out = mutableListOf<String>()
    val variant = meta["variant"] as String
    val base = when (variant) {
      "soft" -> GlassScrollEdgeProfile.Soft
      "hard" -> GlassScrollEdgeProfile.Hard
      "fadeOnly" -> GlassScrollEdgeProfile.FadeOnly
      "soft*0.7" -> GlassScrollEdgeProfile.Soft.scaled(0.7f)
      "soft*1.4" -> GlassScrollEdgeProfile.Soft.scaled(1.4f)
      else -> return listOf("$file: profilo `$variant` sconosciuto")
    }
    val scale = num(meta["scale"])
    val px = num(meta["density"]) * scale
    val profile = base.atResolution(scale)
    val top = meta["side"] == "top"
    val strip = num(meta["stripDp"])
    val controls = num(meta["controlsDp"])
    val unit = num(meta["unitDp"])
    val veilText = meta["veilColor"] as String
    val u = glassScrollEdgeUniforms(
      profile = profile,
      top = top,
      widthPx = num(el["w"]) * px,
      heightPx = strip * px,
      controlsPx = controls * px,
      unitPx = unit * px,
      veilColor = color(veilText),
      darkContent = num(meta["shadeDark"]),
    )
    if (el["source"] != "kotlin:GlassEdgeBlurShaderString") out += "$file: sorgente ${el["source"]}"
    if (abs(num(el["h"]) - strip) > 1e-6f) out += "$file: alta ${el["h"]}, la striscia è $strip"
    if (strip + 1e-4f < controls + profile.reach * unit) out += "$file: la striscia finisce prima del profilo"
    if (abs(num(el["scale"]) - scale) > 1e-6f) out += "$file: scala ${el["scale"]} contro $scale"
    @Suppress("UNCHECKED_CAST")
    val written = el["uniforms"] as Map<String, Any?>
    fun same(name: String, expected: FloatArray) {
      val got = flat(written[name])
      if (got.size != expected.size) {
        out += "$file: `$name` ha ${got.size} valori, l'engine ${expected.size}"
        return
      }
      for (i in expected.indices) {
        if (abs(got[i] - expected[i]) > 1e-5f + 1e-4f * max(abs(got[i]), abs(expected[i]))) {
          out += "$file: `$name`[$i] = ${got[i]}, l'engine dà ${expected[i]}"
          return
        }
      }
    }
    same("axis", floatArrayOf(1f, 0f))
    same("finish", floatArrayOf(0f))
    same("centerWeight", floatArrayOf(u.centerWeight))
    same("taps", u.taps)
    same("edgeLine", u.edgeLine)
    same("blurRamp", u.blurRamp)
    same("veilKnots", u.veilKnots)
    same("veilKnob", u.veilKnob)
    same("grain", floatArrayOf(u.grain))
    same("shade", u.shade)
    same("fadeRamp", u.fadeRamp)
    same("amount", floatArrayOf(u.amount))
    if (written["veilColor"] != veilText || u.veilColor != color(veilText)) out += "$file: colore del velo"
    // La seconda passata cambia solo direzione e finitura: il resto lo eredita, come l'istanza unica dello shader.
    @Suppress("UNCHECKED_CAST")
    val passes = el["passes"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val second = passes.single()["uniforms"] as Map<String, Any?>
    if (second.keys != setOf("axis", "finish") || flat(second["axis"]).toList() != listOf(0f, 1f) ||
      flat(second["finish"]).toList() != listOf(1f)
    ) {
      out += "$file: la seconda passata non è (axis 0,1; finish 1)"
    }
    return out
  }

  private fun num(value: Any?): Float = (value as Number).toFloat()

  /** Un uniform scritto nella scena, appiattito; `$…` (valori del banco) non c'entrano qui e si saltano. */
  private fun flat(value: Any?): FloatArray {
    val out = mutableListOf<Float>()
    fun walk(v: Any?) {
      when (v) {
        is Number -> out += v.toFloat()
        is List<*> -> v.forEach { walk(it) }
        else -> Unit
      }
    }
    walk(value)
    return out.toFloatArray()
  }

  private fun color(text: String): Color {
    val hex = text.removePrefix("#")
    val rgb = hex.substring(0, 6).toLong(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toLong(16) else 0xFF
    return Color((alpha shl 24 or rgb).toInt())
  }

  @Test
  fun theColorReaderReadsWhatTheBenchWrites() {
    assertEquals(Color(0xFFF2F2F7), color("#F2F2F7"))
    assertEquals(Color(0x800D0D0F), color("#0D0D0F80"))
  }
}
