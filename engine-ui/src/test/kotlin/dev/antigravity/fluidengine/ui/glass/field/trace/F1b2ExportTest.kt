package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.glass.field.GlassBody1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassBody8ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyRingShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight8ShaderString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le scene e le tracce di F1B.2 ([F1b2Scenes], [F1b2Traces]), scritte dal codice dell'engine.
 *
 * Prima i fatti che le rendono credibili: ogni elemento imposta gli uniform che il suo programma dichiara; le lastre
 * ferme portano l'ombra a maschere e le superfici fisiche l'ombra nel corpo, con gli stessi numeri; nella barra il
 * vetro torna ai tasti quando la forma si ferma, prima che la pagina si posi, e mai un fotogramma resta senza vetro.
 * Poi, **solo se** le variabili d'ambiente ci sono, si scrivono: le scene con `GLASS_F1B2_SCENE_DIR` (di solito
 * `tools/glassbench/scenes/f1b`), le tracce con `GLASS_TRACE_DIR` (fase `f1b-2`).
 */
class F1b2ExportTest {

  private fun declared(source: String): Set<String> =
    Regex("""uniform\s+(?!shader\b)\w+\s+(\w+)\s*(?:\[\d+])?\s*;""").findAll(source).map { it.groupValues[1] }.toSet()

  private val sources = mapOf(
    "kotlin:GlassBody1ShaderString" to GlassBody1ShaderString,
    "kotlin:GlassBody8ShaderString" to GlassBody8ShaderString,
    "kotlin:GlassBodyRingShaderString" to GlassBodyRingShaderString,
    "kotlin:GlassLight1ShaderString" to GlassLight1ShaderString,
    "kotlin:GlassLight8ShaderString" to GlassLight8ShaderString,
  )

  @Suppress("UNCHECKED_CAST")
  private fun uniforms(layer: Any?): Map<String, Any?> = (layer as Map<String, Any?>)["uniforms"] as Map<String, Any?>

  @Suppress("UNCHECKED_CAST")
  private fun elements(scene: GlassScene): List<Map<String, Any?>> = scene.json["elements"] as List<Map<String, Any?>>

  private fun source(el: Map<String, Any?>): String = (el["body"] as Map<*, *>)["source"] as String

  /** Ogni uniform che il corpo dichiara (una traccia li vuole tutti); per una lastra ferma di una scena, quelli che scrive. */
  private fun checkBody(where: String, el: Map<String, Any?>, complete: Boolean) {
    assertEquals(where, "material", el["kind"])
    val body = declared(sources.getValue(source(el)))
    val set = uniforms(el["body"]).keys
    if (complete || source(el) != "kotlin:GlassBody1ShaderString") {
      assertEquals(where, body, set)
    } else {
      assertTrue("$where: uniform non dichiarati ${set - body}", body.containsAll(set))
    }
  }

  private fun alphas(el: Map<String, Any?>): List<Number> {
    @Suppress("UNCHECKED_CAST")
    return uniforms(el["body"])["shadowAlphas"] as List<Number>
  }

  @Test
  fun theShadowScenesPutTheMasksAtRestAndTheShaderInTravel() {
    val scenes = F1b2Scenes.all()
    assertEquals(8, scenes.size)
    for (scene in scenes) {
      // Il nome comincia con «ombra-riposo-viaggio»: il modo è la parola prima del tema.
      val travel = scene.path.endsWith("-viaggio-chiaro.json") || scene.path.endsWith("-viaggio-scuro.json")
      for (el in elements(scene)) {
        val where = "${scene.path}: ${el["name"]}"
        checkBody(where, el, complete = false)
        if (travel) {
          // Ombra nel corpo, accesa, e nessuna maschera.
          assertFalse("$where: una superficie fisica non ha maschere", "shadow" in el)
          assertEquals(1, uniforms(el["body"])["lightInBody"])
          assertTrue("$where: l'ombra nel corpo è spenta", alphas(el).sumOf { it.toDouble() } > 0.0)
        } else {
          // Le due maschere della ricetta, sotto la lastra di `glassSurface`.
          assertTrue("$where: la lastra ferma non ha la sua ombra", "shadow" in el)
          assertEquals("kotlin:GlassBody1ShaderString", source(el))
        }
      }
    }
    // Riposo e viaggio hanno gli stessi numeri d'ombra: il tasto sopra il testo, nei due temi.
    for (theme in listOf("chiaro", "scuro")) {
      val rest = elements(scenes.single { it.path == "ombra-riposo-viaggio-testo-riposo-$theme.json" }).single { it["name"] == "tasto" }
      val travel = elements(scenes.single { it.path == "ombra-riposo-viaggio-testo-viaggio-$theme.json" }).single { it["name"] == "tasto" }
      @Suppress("UNCHECKED_CAST")
      val masks = rest["shadow"] as List<Map<String, Any?>>
      val ambient = masks.maxBy { (it["radiusDp"] as Number).toDouble() }
      @Suppress("UNCHECKED_CAST")
      val radii = uniforms(travel["body"])["shadowRadii"] as List<Any?>
      assertEquals(
        "$theme: raggio dell'ambiente",
        (ambient["radiusDp"] as Number).toDouble(),
        (radii[1] as String).removeSuffix("dp").toDouble(),
        0.01,
      )
      assertEquals("$theme: alfa dell'ambiente", (ambient["alpha"] as Number).toDouble(), alphas(travel)[1].toDouble(), 1e-6)
    }
    val dir = GlassSceneOutput.requestedDir(System.getenv(SceneVariable)) ?: return
    val written = GlassSceneOutput.write(dir, scenes)
    println("scene di F1B.2 scritte: ${written.size} in $dir")
  }

  @Test
  fun theRestVersusTravelTableOfF1b1CanBeRedoneWithTheShadowOn() {
    val scenes = F1bScenes.restVersusTravelWithShadow()
    assertEquals(8, scenes.size)
    for (scene in scenes) for (el in elements(scene)) {
      val where = "${scene.path}: ${el["name"]}"
      checkBody(where, el, complete = false)
      if (source(el) == "kotlin:GlassBody1ShaderString") assertTrue("$where: senza maschere", "shadow" in el)
      else assertTrue("$where: ombra in corpo spenta", alphas(el).sumOf { it.toDouble() } > 0.0)
    }
    // Fuori dall'albero: la tabella è una misura di F1B.2 (artifacts/liquid-glass/bench/f1b-2/scene).
    val dir = GlassSceneOutput.requestedDir(System.getenv(TableVariable)) ?: return
    GlassSceneOutput.write(dir, scenes)
  }

  private fun finish(trace: GlassTrace) {
    for (frame in trace.frames) for (el in frame.elements) {
      if (el["kind"] == "material") checkBody("${trace.name} @${frame.tMs}: ${el["name"]}", el, complete = true)
    }
    assertTrue(trace.encode().isNotEmpty())
    GlassTraceOutput.writeIfRequested(trace)?.let { println("traccia scritta: $it (${trace.frames.size} fotogrammi)") }
  }

  @Test
  fun theBarHandsTheGlassBackWhenTheShapeStopsAndNeverLeavesAFrameEmpty() {
    for (dark in listOf(false, true)) {
      val built = F1b2Traces.bar(dark)
      val frames = built.trace.frames.filter { it.tMs >= 0.0 }
      // Ogni fotogramma ha vetro: o la superficie (un elemento solo, «barra») o i tasti.
      for (frame in frames) {
        val names = frame.elements.map { it["name"] as String }
        assertTrue("@${frame.tMs}: nessun vetro", names.isNotEmpty())
        assertFalse("@${frame.tMs}: superficie e tasti insieme", "barra" in names && names.size > 1)
      }
      // Il vetro torna ai tasti prima che la pagina si posi, e da lì non cambia più.
      val lastTravel = frames.indexOfLast { f -> f.elements.any { it["name"] == "barra" } }
      val settled = built.settledFrame
      assertTrue("la superficie porta il vetro fino al fotogramma $lastTravel, la pagina si posa al $settled", lastTravel + 1 < settled)
      // I tasti che tornano sono quelli della pagina sopra, con l'ombra a maschere.
      val back = frames[lastTravel + 1].elements
      assertTrue(back.all { "shadow" in it })
      assertEquals(setOf("b-aggiungi", "b-indietro"), back.map { it["name"] }.toSet())
      finish(built.trace)
    }
  }

  @Test
  fun theOtherGoldenJourneysCarryTheShadowInTheirBody() {
    for (dark in listOf(false, true)) {
      val menu = F1b2Traces.menu(dark)
      assertEquals(if (dark) "dark" else "light", menu.trace.theme)
      val surfaces = menu.trace.frames.flatMap { f -> f.elements.filter { it["name"] == "menu" } }
      assertTrue(surfaces.isNotEmpty())
      assertTrue(surfaces.all { alphas(it).sumOf { a -> a.toDouble() } > 0.0 })
      finish(menu.trace)
      for (built in listOf(F1b2Traces.split(dark), F1b2Traces.groupToMenu(dark))) {
        val bodies = built.trace.frames.flatMap { f -> f.elements.filter { it["name"] == "superficie" } }
        assertTrue(bodies.all { alphas(it).sumOf { a -> a.toDouble() } > 0.0 })
        finish(built.trace)
      }
    }
  }

  private companion object {
    const val SceneVariable = "GLASS_F1B2_SCENE_DIR"
    const val TableVariable = "GLASS_F1B2_TABLE_DIR"
  }
}
