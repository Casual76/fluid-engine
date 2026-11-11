package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.glass.field.GlassBody1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight1ShaderString
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le scene della tavola di compatibilita' (F1A.8; vedi [GlassCompatScenes]).
 *
 * Ogni giro di test le compone e controlla che dicano cio' che devono: le due scene di un lato hanno le stesse
 * lastre negli stessi posti; il vetro di oggi porta l'ottica scritta per intero e la pellicola di chi la usa; il
 * materiale nuovo viene dalla ricetta, e la tinta l'ha letta la **regola dei modi** (una tinta scritta a mano o una
 * tinta di serie col colore cambiato e' la pellicola dipinta; una tinta di serie e' il velo). **Solo se**
 * `GLASS_SCENE_DIR` c'e' le scrive, in `compatibilita/`.
 */
class GlassCompatSceneExportTest {

  private val gusto: File by lazy { File(GlassTraceOutput.repoRoot(), "tools/glassbench/scenes/f1a/tavole") }

  private val scenes: List<GlassScene> by lazy { GlassCompatScenes.all(gusto) }

  private fun scene(path: String): Map<String, Any?> = scenes.single { it.path == path }.json

  @Suppress("UNCHECKED_CAST")
  private fun elements(scene: Map<String, Any?>): List<Map<String, Any?>> = scene["elements"] as List<Map<String, Any?>>

  @Suppress("UNCHECKED_CAST")
  private fun body(el: Map<String, Any?>): Map<String, Any?> =
    (el["body"] as Map<String, Any?>)["uniforms"] as Map<String, Any?>

  private fun declared(source: String): Set<String> =
    Regex("""uniform\s+(?!shader\b)\w+\s+(\w+)\s*(?:\[\d+])?\s*;""").findAll(source).map { it.groupValues[1] }.toSet()

  @Test
  fun bothSidesHaveTheSameSlabsInTheSamePlaces() {
    for (side in listOf("chiaro", "scuro")) {
      val today = elements(scene("compatibilita/oggi-$side.json"))
      val liquid = elements(scene("compatibilita/nuovo-$side.json"))
      assertEquals(today.map { it["name"] }, liquid.map { it["name"] })
      for ((a, b) in today.zip(liquid)) {
        for (key in listOf("x", "y", "w", "h", "radius", "icon", "icons", "label", "rows", "contentColor")) {
          assertEquals("$side: ${a["name"]}.$key", a[key], b[key])
        }
        assertEquals(null, a["kind"])
        assertEquals("material", b["kind"])
      }
    }
  }

  @Test
  fun todayCarriesTheWholeOpticsAndTheFilm() {
    for (side in listOf("chiaro", "scuro")) {
      for (el in elements(scene("compatibilita/oggi-$side.json"))) {
        @Suppress("UNCHECKED_CAST")
        val optics = el["optics"] as Map<String, Any?>
        // Il ruolo del banco piu' i diciassette campi di `GlassOptics`.
        assertEquals("${el["name"]}", 18, optics.size)
        assertTrue(el["tint"] is String)
      }
    }
  }

  @Test
  fun theNewMaterialReadsTheTintThroughTheModeRule() {
    val byKey = GlassCompatScenes.Cases.groupBy { it.key }
    // Scritte a mano (le app, l'orologio): pellicole dipinte. Di serie (Neutral): il velo. Col colore cambiato: pellicola.
    for (key in listOf("fluidify-tasto-su-foto", "fluidify-scheda-su-foto", "fluidify-pannello", "galleria-adattiva",
      "galleria-su-foto", "orologio-disco", "orologio-pillola", "orologio-avviso", "engine-indicatore-sync",
      "engine-tasto-scelto")) {
      for (case in byKey.getValue(key)) assertEquals(key, GlassTintMode.Fixed, GlassCompatScenes.resolvedMode(case))
    }
    for (key in listOf("fluidify-tasti-in-pagina", "engine-composer", "palco-playground")) {
      for (case in byKey.getValue(key)) assertEquals(key, GlassTintMode.Neutral, GlassCompatScenes.resolvedMode(case))
    }
    // E nel JSON: la pellicola prende il posto del velo (alfa del velo zero), il velo resta dove la tinta e' neutra.
    for (side in listOf("chiaro", "scuro")) {
      for (el in elements(scene("compatibilita/nuovo-$side.json"))) {
        val name = el["name"] as String
        val case = GlassCompatScenes.Cases.single { it.dark == (side == "scuro") && name.startsWith(it.key + ".") }
        val uniforms = body(el)
        val veil = uniforms["veilColor"] as String
        val tint = uniforms["tintColor"] as String
        when (GlassCompatScenes.resolvedMode(case)) {
          GlassTintMode.Fixed -> {
            assertTrue(name, veil.endsWith("00"))
            assertEquals(name, glassColor(case.tint.overlay), tint)
            assertEquals(name, 0f, (uniforms["tintMode"] as Number).toFloat(), 0f)
          }
          GlassTintMode.Neutral -> {
            assertFalse(name, veil.endsWith("00"))
            assertTrue(name, tint.endsWith("00"))
          }
          GlassTintMode.Colored -> error("$name: nessun caso e' una tinta di Apple")
        }
      }
    }
  }

  @Test
  fun theNewMaterialSetsExactlyTheUniformsOfItsPrograms() {
    val bodyUniforms = declared(GlassBody1ShaderString)
    val light = declared(GlassLight1ShaderString)
    val inBodyOnly = light - setOf("offset", "flex", "flexCenter", "pieceCount", "pieceRect", "pieceRadii", "pieceBlend",
      "pieceSmooth", "materialize", "aa", "lightPart") + setOf("shadowOffset", "shadowRadii", "shadowAlphas", "shadowColor") +
      // `nodeClip` (F1B.1) è di chi disegna senza il ritaglio del nodo, cioè di Fluid-physics: una lastra ferma il ritaglio vero ce
      // l'ha, e lo uniform resta a zero.
      setOf("nodeClip")
    for (side in listOf("chiaro", "scuro")) {
      for (el in elements(scene("compatibilita/nuovo-$side.json"))) {
        assertEquals("${el["name"]}", bodyUniforms - inBodyOnly, body(el).keys)
      }
    }
  }

  @Test
  fun everyRowOfEveryTavolaFramesItsCase() {
    val tavole = scenes.filter { it.path.startsWith("compatibilita/tavola-") }
    assertEquals(GlassCompatScenes.Tavole.size, tavole.size)
    var rows = 0
    for (tavola in tavole) {
      @Suppress("UNCHECKED_CAST")
      val grounds = tavola.json["fondi"] as Map<String, Any?>
      @Suppress("UNCHECKED_CAST")
      for (row in tavola.json["righe"] as List<Map<String, Any?>>) {
        rows++
        assertTrue("${tavola.path}: ${row["fondo"]}", row["fondo"] in grounds)
        @Suppress("UNCHECKED_CAST")
        val crop = (row["ritaglio"] as List<Number>).map { it.toInt() }
        assertTrue(crop.all { it >= 0 })
        assertTrue(crop[0] + crop[2] <= GlassCompatScenes.Width)
      }
    }
    // Ogni caso ha almeno una riga, in una tavola sola.
    assertEquals(GlassCompatScenes.Cases.sumOf { it.grounds.size }, rows)
  }

  @Test
  fun theScenesAreWrittenOnlyWhenAsked() {
    assertEquals(scenes.size, scenes.map { it.path }.toSet().size)
    assertTrue(scenes.all { it.path.startsWith("compatibilita/") })
    for (scene in scenes) assertTrue(scene.encode().isNotEmpty())
    val dir = GlassSceneOutput.requestedDir() ?: return
    val written = GlassSceneOutput.write(dir, scenes)
    println("scene di compatibilita' scritte dalla ricetta: ${written.size} in $dir")
    for (file in written) assertTrue(file.path, file.isFile && file.length() > 0)
  }
}
