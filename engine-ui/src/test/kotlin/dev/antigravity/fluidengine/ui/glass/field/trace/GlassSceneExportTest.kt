package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.field.GlassBody1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
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

  /** Le lastre di una scena e, dentro, quelle annidate (`children`), in ordine. */
  @Suppress("UNCHECKED_CAST")
  private fun slabs(elements: List<Map<String, Any?>>): List<Map<String, Any?>> =
    elements.flatMap { el -> listOf(el) + slabs(el["children"] as? List<Map<String, Any?>> ?: emptyList()) }

  @Suppress("UNCHECKED_CAST")
  private fun children(el: Map<String, Any?>): List<Map<String, Any?>> = el["children"] as List<Map<String, Any?>>

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
      for (el in slabs(elements(scene.json))) {
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
      // L'ombra B di serie è una componente sola, larga: nessun contatto.
      @Suppress("UNCHECKED_CAST")
      val shadow = el["shadow"] as List<Map<String, Any?>>
      assertEquals(name, 1, shadow.size)
    }
    // La smerigliatura D (4,5 dp), che i grandi fanno crescere di un passo (fino a 6,75 dp a guadagno pieno).
    val blur = names.associateWith { number(capture(element(sample, it))["blurDp"]) }
    assertEquals(4.5, blur.getValue("tasto"), 1e-6)
    assertEquals(4.5, blur.getValue("capsula"), 1e-6)
    assertEquals(4.5, blur.getValue("barra"), 1e-6)
    assertTrue(blur.getValue("menu") > 4.5 && blur.getValue("menu") < 6.75)
    assertEquals(6.75, blur.getValue("pannello"), 1e-6)
    // A riposo, di serie, `w` è zero; e con 4,5 dp la banda della lente copre tutta la sfocatura: nessun margine.
    assertEquals(0.0, number(capture(element(sample, "tasto"))["w"]), 0.0)
    assertEquals(0.0, number(capture(element(sample, "tasto"))["paddingDp"]), 0.0)
    // La risoluzione della cattura non dipende dalla taglia: la decide il ruolo (come l'innesto in composizione).
    assertEquals(1, names.map { number(capture(element(sample, it))["scale"]) }.toSet().size)

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

  /** Il velo che la ricetta dà a una lastra di quel ruolo e quella taglia, come lo scrive una scena. */
  private fun veilOf(role: GlassRole, w: Float, h: Float, dark: Boolean): String =
    glassColor(glassRecipe(role = role, widthDp = w, heightDp = h, darkSide = dark).veilColor)

  @Test
  fun theDarkSideHasTheLightWireAndTheLargeOnesADeeperVeil() {
    val dark = scene("gusto/serie-scuro.json")
    val light = scene("gusto/serie-chiaro.json")
    for (el in elements(dark)) {
      // La B di serie: due disegni, il riflesso e poi il filo chiaro che copre.
      assertEquals(listOf("Plus", null), lights(el).map { it["blend"] })
      assertEquals("#FFFFFF57", uniforms(lights(el)[1])["rimDark"])
    }
    // Il velo scuro: quello dei piccoli sul tasto, quello dei grandi su menu e pannello, in mezzo la capsula e
    // la barra, sempre più scure con la taglia.
    fun veil(name: String) = uniforms(element(dark, name)["body"])["veilColor"] as String
    assertEquals("#78788080", veil("tasto"))
    assertEquals("#36363C80", veil("menu"))
    assertEquals("#36363C80", veil("pannello"))
    val greys = listOf("tasto", "capsula", "barra", "menu").map { veil(it).substring(1, 3).toInt(16) }
    assertEquals(greys.sortedDescending(), greys)
    assertTrue(greys[1] < greys[0] && greys[2] > greys[3])
    // Sul chiaro il velo non cambia con la taglia.
    for (el in elements(light)) assertEquals("#F5F5F775", uniforms(el["body"])["veilColor"])
    // Il bordo scuro della tavola: il solo riflesso è un disegno, la B al 10% due.
    val reflex = element(scene("gusto/scuro-riflesso-scuro.json"), "capsula")
    assertEquals(listOf("Plus"), lights(reflex).map { it["blend"] })
    val faint = element(scene("gusto/scuro-filo-tenue-scuro.json"), "capsula")
    assertEquals("#FFFFFF1A", uniforms(lights(faint)[1])["rimDark"])
    // Un elemento può stare dal lato opposto a quello della scena: il vetro chiaro sopra la foto scura di R5 M3.
    val mixed = scene("ricetta/rif27-scuro.json")
    assertEquals(
      veilOf(GlassRole.Floating, 380f, 62f, dark = true),
      uniforms(element(mixed, "tabbar-scura")["body"])["veilColor"],
    )
    assertEquals("#F5F5F775", uniforms(element(mixed, "barra-chiara")["body"])["veilColor"])
    assertEquals(2, lights(element(mixed, "barra-chiara")).size)
  }

  @Test
  fun theTextSampleIsBeforeAndAfter() {
    for (side in listOf("chiaro", "scuro")) {
      val after = scene("gusto/testo-serie-$side.json")
      val before = scene("gusto/testo-prima-$side.json")
      assertEquals(listOf("foglio", "card", "annidato", "tondo26"), elements(after).map { it["name"] })
      assertEquals(elements(after).map { it["name"] }, elements(before).map { it["name"] })
      // La card è un vetro sul contenuto: nessuna ombra.
      assertNull(element(after, "card")["shadow"])
    }
    // Sullo scuro foglio e card prendono il velo dei grandi; prima avevano quello dei piccoli.
    val after = scene("gusto/testo-serie-scuro.json")
    val before = scene("gusto/testo-prima-scuro.json")
    for (name in listOf("foglio", "card")) {
      assertEquals("#36363C80", uniforms(element(after, name)["body"])["veilColor"])
      assertEquals("#78788080", uniforms(element(before, name)["body"])["veilColor"])
    }
    // Il tasto annidato prende il vetro annidato: sullo scuro un terzo del velo (0,35 di 0x80 = 0x2D), e
    // nella variante di prima, che la regola non l'aveva, il velo pieno.
    assertEquals("#7878802D", uniforms(element(after, "annidato")["body"])["veilColor"])
    assertEquals("#78788080", uniforms(element(before, "annidato")["body"])["veilColor"])
    // Il tondo da 26 dp: il riflesso più tenue e meno profondo di quello del tasto, l'ombra più piccola.
    val dot = element(after, "tondo26")
    val button = element(scene("gusto/serie-scuro.json"), "tasto")
    assertTrue(number(uniforms(lights(dot)[0])["reflexStrength"]) < number(uniforms(lights(button)[0])["reflexStrength"]))
    assertEquals("4.16dp", uniforms(lights(dot)[0])["reflexDepth"])
    @Suppress("UNCHECKED_CAST")
    fun ambient(el: Map<String, Any?>) = number((el["shadow"] as List<Map<String, Any?>>).last()["radiusDp"])
    assertEquals(ambient(button) * 26.0 / 44.0, ambient(dot), 1e-3)
  }

  @Test
  fun theNestedGlassTakesLessVeilAndLessShadowAndNothingElse() {
    for (side in listOf("chiaro", "scuro")) {
      val after = scene("gusto/annidato-serie-$side.json")
      val before = scene("gusto/annidato-prima-$side.json")
      assertEquals(listOf("notifica", "foglio"), elements(after).map { it["name"] })
      assertEquals(listOf("tasto-notifica"), children(element(after, "notifica")).map { it["name"] })
      assertEquals(listOf("tasto-foglio"), children(element(after, "foglio")).map { it["name"] })
      // I pannelli stanno sulla pagina: identici con e senza la regola.
      for (name in listOf("notifica", "foglio")) {
        val a = element(after, name).filterKeys { it != "_ricetta" && it != "children" }
        val b = element(before, name).filterKeys { it != "_ricetta" && it != "children" }
        assertEquals("$side: $name", b, a)
      }
      // I tasti annidati: cambiano il corpo (il velo) e l'ombra, e niente altro.
      for ((panel, button) in listOf("notifica" to "tasto-notifica", "foglio" to "tasto-foglio")) {
        val a = children(element(after, panel)).single()
        val b = children(element(before, panel)).single()
        val differing = (a.keys + b.keys).filter { it != "_ricetta" && a[it] != b[it] }.toSet()
        val expected = if (side == "scuro") setOf("body", "shadow") else setOf("shadow")
        assertEquals("$side: $button", expected, differing)
        assertTrue((a["_ricetta"] as String).endsWith(", annidata"))
        @Suppress("UNCHECKED_CAST")
        fun ambient(el: Map<String, Any?>) = number((el["shadow"] as List<Map<String, Any?>>).last()["alpha"])
        assertEquals("$side: $button", ambient(b) * 0.5, ambient(a), 1e-6)
      }
    }
    // Sullo scuro il velo del tasto annidato scende a un terzo, lo stesso grigio.
    val dark = children(element(scene("gusto/annidato-serie-scuro.json"), "notifica")).single()
    val darkBefore = children(element(scene("gusto/annidato-prima-scuro.json"), "notifica")).single()
    assertEquals("#7878802D", uniforms(dark["body"])["veilColor"])
    assertEquals("#78788080", uniforms(darkBefore["body"])["veilColor"])
    // I tasti stanno dentro i pannelli: le loro coordinate sono relative al pannello.
    val panel = element(scene("gusto/annidato-serie-chiaro.json"), "notifica")
    val child = children(panel).single()
    assertTrue(number(child["x"]) + number(child["w"]) <= number(panel["w"]))
    assertTrue(number(child["y"]) + number(child["h"]) <= number(panel["h"]))
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
    assertEquals(setOf("shadow"), differing("gusto/ombra-ios27-chiaro.json", "capsula"))
    assertEquals(setOf("shadow"), differing("gusto/ombra-ios27-chiaro.json", "pannello"))
    assertEquals(setOf("shadow"), differing("gusto/ombra-oggi-chiaro.json", "menu"))
    assertEquals(setOf("shadow"), differing("gusto/ombra-tondo-apple-chiaro.json", "tasto"))
    assertEquals(emptySet<String>(), differing("gusto/ombra-tondo-apple-chiaro.json", "capsula"))
    for (frost in listOf("12", "24", "mista", "mezzo")) {
      assertEquals(frost, setOf("capture"), differing("gusto/smeriglio-$frost-chiaro.json", "barra"))
    }
    assertEquals(setOf("body"), differing("gusto/velo-ios26-chiaro.json", "capsula"))
    assertEquals(setOf("body"), differing("gusto/velo-oggi-chiaro.json", "capsula"))
    // La lente cambia il corpo; con 4,5 dp di sfocatura la banda copre la sfocatura in ogni variante, quindi il
    // margine della cattura resta zero.
    assertEquals(setOf("body"), differing("gusto/lente-marcata-chiaro.json", "tasto"))
    // I grandi: le lastre piccole restano identiche, le grandi cambiano smerigliatura e ombra.
    assertEquals(emptySet<String>(), differing("gusto/grandi-due-passi-chiaro.json", "capsula"))
    assertEquals(setOf("capture", "shadow"), differing("gusto/grandi-due-passi-chiaro.json", "pannello"))
    assertEquals(setOf("capture", "shadow"), differing("gusto/grandi-barra-chiaro.json", "menu"))
    assertEquals(setOf("shadow"), differing("gusto/grandi-passo-chiaro.json", "pannello"))
    // Il velo scuro dei grandi tocca solo il corpo, e solo dei grandi.
    fun differingDark(path: String, name: String): Set<String> {
      val a = element(scene("gusto/serie-scuro.json"), name)
      val b = element(scene(path), name)
      return (a.keys + b.keys).filter { it != "_ricetta" && a[it] != b[it] }.toSet()
    }
    assertEquals(emptySet<String>(), differingDark("gusto/scuro-grandi-come-piccoli-scuro.json", "tasto"))
    assertEquals(setOf("body"), differingDark("gusto/scuro-grandi-come-piccoli-scuro.json", "pannello"))
    assertEquals(setOf("light"), differingDark("gusto/scuro-riflesso-scuro.json", "capsula"))

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
