package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.glass.field.GlassBody1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight1ShaderString
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le scene ferme del banco scritte dalla ricetta (piano 4.2; F1A.5).
 *
 * Ogni giro di test le **compone** tutte e controlla che siano scene vere: ogni uniform che la scena imposta
 * esiste nella sorgente che nomina, ogni uniform della sorgente è impostato, i numeri sono finiti, e ciò che
 * il piano dice del materiale si ritrova nel JSON (il filo non cresce con la taglia, lo scuro non ha filo, i
 * grandi sono più smerigliati). **Solo se** `GLASS_SCENE_DIR` c'è le scrive (vedi [GlassSceneOutput]): nei giri
 * normali non resta nessun file.
 */
class GlassSceneExportTest {

  private val references: File by lazy { File(GlassTraceOutput.repoRoot(), "tools/glassbench/scenes/rif") }

  private val scenes: List<GlassScene> by lazy { GlassScenes.all(references) }

  private fun scene(path: String): Map<String, Any?> = scenes.single { it.path == path }.json

  @Suppress("UNCHECKED_CAST")
  private fun elements(scene: Map<String, Any?>): List<Map<String, Any?>> = scene["elements"] as List<Map<String, Any?>>

  private fun element(scene: Map<String, Any?>, name: String): Map<String, Any?> =
    elements(scene).single { it["name"] == name }

  @Suppress("UNCHECKED_CAST")
  private fun uniforms(layer: Any?): Map<String, Any?> = (layer as Map<String, Any?>)["uniforms"] as Map<String, Any?>

  @Suppress("UNCHECKED_CAST")
  private fun lights(el: Map<String, Any?>): List<Map<String, Any?>> = el["light"] as? List<Map<String, Any?>> ?: emptyList()

  @Suppress("UNCHECKED_CAST")
  private fun capture(el: Map<String, Any?>): Map<String, Any?> = el["capture"] as Map<String, Any?>

  private fun number(value: Any?): Double = (value as Number).toDouble()

  /** I nomi degli uniform che una sorgente AGSL dichiara (non gli ingressi `uniform shader`). */
  private fun declared(source: String): Set<String> =
    Regex("""uniform\s+(?!shader\b)\w+\s+(\w+)\s*(?:\[\d+])?\s*;""").findAll(source).map { it.groupValues[1] }.toSet()

  @Test
  fun everySceneSetsExactlyTheUniformsItsProgramsDeclare() {
    val body = declared(GlassBody1ShaderString)
    val light = declared(GlassLight1ShaderString)
    // Ciò che una lastra ferma non imposta: gli uniform di luce e d'ombra **in corpo**, che valgono zero a
    // riposo (`lightInBody` 0: uno uniform dichiarato e non impostato vale zero).
    val inBodyOnly = light - setOf("offset", "flex", "flexCenter", "pieceCount", "pieceRect", "pieceRadii", "pieceBlend",
      "pieceSmooth", "materialize", "aa", "lightPart") + setOf("shadowOffset", "shadowRadii", "shadowAlphas", "shadowColor")
    var materials = 0
    for (scene in scenes) {
      for (el in elements(scene.json)) {
        if (el["kind"] != "material") continue
        materials++
        val where = "${scene.path}: ${el["name"]}"
        assertEquals(where, "kotlin:GlassBody1ShaderString", (el["body"] as Map<*, *>)["source"])
        assertEquals(where, body - inBodyOnly, uniforms(el["body"]).keys)
        for (layer in lights(el)) {
          assertEquals(where, "kotlin:GlassLight1ShaderString", layer["source"])
          assertEquals(where, light, uniforms(layer).keys)
        }
      }
      // Lo scrittore rifiuta i numeri non finiti: se la scena si scrive, non ne ha.
      assertTrue(scene.encode().isNotEmpty())
    }
    assertTrue("nessuna lastra del materiale nuovo nelle scene", materials > 100)
  }

  @Test
  fun theReferenceScenesKeepEverythingButTheSlabs() {
    assertEquals(GlassScenes.References.size, scenes.count { it.path.startsWith("ricetta/") })
    for (name in GlassScenes.References) {
      val original = GlassSceneJson.parseObject(File(references, "$name.json").readText(Charsets.UTF_8))
      val rewritten = scene("ricetta/$name.json")
      for (key in original.keys - setOf("_", "elements")) {
        assertEquals("$name: `$key` deve restare quello della scena di riferimento", original[key], rewritten[key])
      }
      val before = elements(original)
      val after = elements(rewritten)
      assertEquals(before.map { it["name"] }, after.map { it["name"] })
      for ((old, new) in before.zip(after)) {
        assertEquals("$name: ${old["name"]}", "material", new["kind"])
        for (key in listOf("x", "y", "w", "h", "icon", "icons", "rows", "items", "iconSizeDp", "rowHeightDp")) {
          assertEquals("$name: ${old["name"]}.$key", old[key], new[key])
        }
        // Ciò che parla del vetro di oggi non passa: il ruolo è diventato una ricetta.
        assertFalse("role" in new)
        assertFalse("tint" in new)
      }
    }
  }

  @Test
  fun theRimIsTheSameOnEverySlabAndTheLargeOnesAreMoreFrosted() {
    val sample = scene("gusto/serie-chiaro.json")
    val names = listOf("tasto", "capsula", "barra", "menu", "pannello")
    assertEquals(names, elements(sample).map { it["name"] })
    for (name in names) {
      val el = element(sample, name)
      // Due disegni: prima la luce che somma, poi il filo.
      assertEquals(listOf("Plus", null), lights(el).map { it["blend"] })
      assertEquals(listOf(0, 1), lights(el).map { uniforms(it)["lightPart"] })
      for (layer in lights(el)) {
        assertEquals(name, "0.6dp", uniforms(layer)["rimWidth"])
        assertEquals(name, "7dp", uniforms(layer)["reflexDepth"])
      }
      @Suppress("UNCHECKED_CAST")
      val shadow = el["shadow"] as List<Map<String, Any?>>
      assertEquals(name, 2, shadow.size)
    }
    val blur = names.associateWith { number(capture(element(sample, it))["blurDp"]) }
    assertEquals(12.0, blur.getValue("tasto"), 1e-6)
    assertEquals(12.0, blur.getValue("capsula"), 1e-6)
    assertEquals(12.0, blur.getValue("barra"), 1e-6)
    assertTrue(blur.getValue("menu") > 12.0 && blur.getValue("menu") < 18.0)
    assertEquals(18.0, blur.getValue("pannello"), 1e-6)
    // A riposo, di serie, `w` è zero e il margine è quello che la lente non copre.
    assertEquals(0.0, number(capture(element(sample, "tasto"))["w"]), 0.0)
    assertEquals(1.44, number(capture(element(sample, "tasto"))["paddingDp"]), 1e-4)

    // La geometria: le capsule sono semicerchi senza raccordo; menu e pannello hanno il raccordo di iOS e il
    // ritaglio hardware al 98% del raggio.
    for (name in listOf("tasto", "capsula", "barra")) {
      val el = element(sample, name)
      assertEquals(listOf(0.0), (uniforms(el["body"])["pieceSmooth"] as List<*>).map { number(it) })
      assertNull(el["clipRadiusScale"])
    }
    for (name in listOf("menu", "pannello")) {
      val el = element(sample, name)
      assertEquals(0.6, number((uniforms(el["body"])["pieceSmooth"] as List<*>).single()), 1e-6)
      assertEquals(SceneSmoothedClipScale, number(el["clipRadiusScale"]), 0.0)
      assertEquals(true, el["clip"])
    }
  }

  @Test
  fun theDarkSideHasNoWireAndItsOwnVeil() {
    val dark = scene("gusto/serie-scuro.json")
    val light = scene("gusto/serie-chiaro.json")
    for (el in elements(dark)) {
      // Un disegno solo: il riflesso. Il filo, con alfa zero, si salta.
      assertEquals(listOf("Plus"), lights(el).map { it["blend"] })
      assertEquals("#78788080", uniforms(el["body"])["veilColor"])
    }
    assertEquals("#F5F5F775", uniforms(element(light, "capsula")["body"])["veilColor"])
    // Il filo chiaro è una variante, ed è un filo: torna il secondo disegno.
    val wire = element(scene("gusto/scuro-filo-scuro.json"), "capsula")
    assertEquals(listOf("Plus", null), lights(wire).map { it["blend"] })
    assertEquals("#FFFFFF1A", uniforms(lights(wire)[1])["rimDark"])
    // Un elemento può stare dal lato opposto a quello della scena: il vetro chiaro sopra la foto scura di R5 M3.
    val mixed = scene("ricetta/rif27-scuro.json")
    assertEquals("#78788080", uniforms(element(mixed, "tabbar-scura")["body"])["veilColor"])
    assertEquals("#F5F5F775", uniforms(element(mixed, "barra-chiara")["body"])["veilColor"])
    assertEquals(2, lights(element(mixed, "barra-chiara")).size)
  }

  @Test
  fun everyTasteVariantChangesWhatItSaysAndOnlyThat() {
    val base = scene("gusto/serie-chiaro.json")
    fun differing(path: String, name: String): Set<String> {
      val a = element(base, name)
      val b = element(scene(path), name)
      return (a.keys + b.keys).filter { it != "_ricetta" && a[it] != b[it] }.toSet()
    }
    // Il bordo tocca solo la luce; l'ombra solo l'ombra; la smerigliatura solo la catena.
    assertEquals(setOf("light"), differing("gusto/bordo-ios26-chiaro.json", "capsula"))
    assertEquals(setOf("light"), differing("gusto/bordo-oggi-chiaro.json", "capsula"))
    assertEquals(setOf("shadow"), differing("gusto/ombra-ios26-chiaro.json", "capsula"))
    assertEquals(setOf("shadow"), differing("gusto/ombra-oggi-chiaro.json", "menu"))
    assertEquals(setOf("shadow"), differing("gusto/ombra-tondo-apple-chiaro.json", "tasto"))
    assertEquals(emptySet<String>(), differing("gusto/ombra-tondo-apple-chiaro.json", "capsula"))
    assertEquals(setOf("capture"), differing("gusto/smeriglio-24-chiaro.json", "barra"))
    assertEquals(setOf("capture"), differing("gusto/smeriglio-mista-chiaro.json", "barra"))
    assertEquals(setOf("body"), differing("gusto/velo-ios26-chiaro.json", "capsula"))
    assertEquals(setOf("body"), differing("gusto/velo-oggi-chiaro.json", "capsula"))
    // La lente cambia il corpo e, con la banda, il margine della cattura.
    assertEquals(setOf("body", "capture"), differing("gusto/lente-marcata-chiaro.json", "tasto"))
    // I grandi: le lastre piccole restano identiche, le grandi cambiano smerigliatura e ombra.
    assertEquals(emptySet<String>(), differing("gusto/grandi-due-passi-chiaro.json", "capsula"))
    assertEquals(setOf("capture", "shadow"), differing("gusto/grandi-due-passi-chiaro.json", "pannello"))
    assertEquals(setOf("capture", "shadow"), differing("gusto/grandi-barra-chiaro.json", "menu"))

    // La pellicola di oggi prende il posto del velo.
    val film = uniforms(element(scene("gusto/velo-oggi-chiaro.json"), "capsula")["body"])
    assertEquals("#F5F5F700", film["veilColor"])
    assertEquals("#FBFBFB85", film["tintColor"])
    // La variante limpida: tutto nitido, nessun velo, lo scurimento sotto.
    val clear = element(scene("gusto/limpida-media-chiaro.json"), "capsula")
    assertEquals(1.0, number(capture(clear)["w"]), 0.0)
    assertEquals(0.35, number(uniforms(clear["body"])["dim"]), 1e-6)
  }

  @Test
  fun todaySampleIsTheSameSlabsAsTodayGlass() {
    for (side in listOf("chiaro", "scuro")) {
      val today = scene("gusto/oggi-$side.json")
      val liquid = scene("gusto/serie-$side.json")
      assertEquals(elements(liquid).map { it["name"] }, elements(today).map { it["name"] })
      for ((a, b) in elements(today).zip(elements(liquid))) {
        assertNull(a["kind"])
        assertNotNull(a["role"])
        for (key in listOf("x", "y", "w", "h", "radius")) assertEquals(a[key], b[key])
      }
    }
  }

  @Test
  fun theGeometryRuleIsTheOneOfThePlan() {
    assertEquals("\$radii", slabGeometry("capsula", 156.0, 56.0, "capsule").radii)
    assertEquals(0f, slabGeometry("capsula", 156.0, 56.0, "capsule").smoothing, 0f)
    // Un raggio da mezzo lato corto in su è una capsula, comunque sia scritto.
    assertFalse(slabGeometry("tondo", 44.0, 44.0, 22).smoothed)
    assertFalse(slabGeometry("tondo", 44.0, 44.0, 1000).smoothed)
    // Grande: raggi nominali, raccordo, ritaglio al 98%.
    val big = slabGeometry("menu", 170.0, 192.0, 28)
    assertTrue(big.smoothed)
    assertEquals(0.6f, big.smoothing, 0f)
    assertEquals("\$radii", big.radii)
    // Piccolo e non capsula: il raggio equivalente dell'engine, senza raccordo.
    val small = slabGeometry("chip", 72.0, 36.0, 10)
    assertFalse(small.smoothed)
    assertEquals(List(4) { "8.2dp" }, small.radii)
    // A cavallo della soglia fra le due densità la scena non varrebbe a tutte e due: si ferma.
    val failure = runCatching { slabGeometry("carta", 100.0, 60.0, 12) }.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException)
  }

  @Test
  fun theReaderReadsWhatTheWriterWrites() {
    val text = """{"a": 1, "b": [1.5, -25e-2, "x\n\"y\"", true, null], "c": {"d": {}, "e": []}}"""
    val parsed = GlassSceneJson.parseObject(text)
    assertEquals(listOf("a", "b", "c"), parsed.keys.toList())
    assertEquals(1L, parsed["a"])
    assertEquals(listOf(1.5, -0.25, "x\n\"y\"", true, null), parsed["b"])
    assertEquals(parsed, GlassSceneJson.parse(GlassTraceJson.encode(parsed)))
    // Una scena intera sopravvive al giro: ciò che si scrive è ciò che il banco rileggerà.
    val sample = scenes.first { it.path == "gusto/serie-chiaro.json" }
    val again = GlassSceneJson.parseObject(sample.encode())
    assertEquals(sample.json.keys.toList(), again.keys.toList())
    assertEquals(GlassTraceJson.encode(again), sample.encode())
  }

  @Test
  fun theScenesAreWrittenOnlyWhenAsked() {
    assertNull(GlassSceneOutput.requestedDir(value = null))
    assertNull(GlassSceneOutput.requestedDir(value = "  "))
    val root = File("radice").absoluteFile
    assertEquals(File(root, "tools/glassbench/scenes/f1a"), GlassSceneOutput.requestedDir("tools/glassbench/scenes/f1a") { root })
    val absolute = File("altrove").absoluteFile
    assertEquals(absolute, GlassSceneOutput.requestedDir(absolute.path) { error("la radice non serve") })

    // I percorsi sono tutti distinti e stanno nelle due sottocartelle.
    assertEquals(scenes.size, scenes.map { it.path }.toSet().size)
    assertTrue(scenes.all { it.path.startsWith("ricetta/") || it.path.startsWith("gusto/") })

    val dir = GlassSceneOutput.requestedDir() ?: return
    val written = GlassSceneOutput.write(dir, scenes)
    println("scene scritte dalla ricetta: ${written.size} in $dir")
    for (file in written) assertTrue(file.path, file.isFile && file.length() > 0)
  }
}
