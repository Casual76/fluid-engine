package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.glass.field.GlassBody1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassBody8ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyRingShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight1ShaderString
import dev.antigravity.fluidengine.ui.glass.field.GlassLight8ShaderString
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le scene e le tracce di F1B.1, scritte dal codice dell'engine ([F1bScenes], [F1bTraces]).
 *
 * Prima si controllano i fatti che le rendono credibili (ogni elemento imposta esattamente gli uniform che il suo
 * programma dichiara; a riposo i pezzi sono separati e la luce sta sul nodo; in viaggio la luce sta nel corpo; il
 * margine della cattura non cambia lungo una traccia). Poi, **solo se** la variabile d'ambiente c'è, si scrivono:
 * le scene con `GLASS_F1B_SCENE_DIR` (di solito `tools/glassbench/scenes/f1b`), le tracce con `GLASS_TRACE_DIR`
 * come tutte le altre (fase `f1b`).
 */
class F1bExportTest {

  private val scenes: List<GlassScene> by lazy { F1bScenes.all() }

  /** I nomi degli uniform che una sorgente AGSL dichiara (non gli ingressi `uniform shader`). */
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
  private fun lights(el: Map<String, Any?>): List<Map<String, Any?>> = el["light"] as? List<Map<String, Any?>> ?: emptyList()

  @Suppress("UNCHECKED_CAST")
  private fun elements(scene: GlassScene): List<Map<String, Any?>> = scene.json["elements"] as List<Map<String, Any?>>

  /** Gli uniform di luce e d'ombra in corpo, che una lastra di `glassSurface` ferma non scrive (valgono zero). */
  private val inBodyOnly: Set<String> by lazy {
    declared(GlassLight1ShaderString) - setOf(
      "offset", "flex", "flexCenter", "pieceCount", "pieceRect", "pieceRadii", "pieceBlend", "pieceSmooth", "materialize",
      "aa", "lightPart",
    ) + setOf("shadowOffset", "shadowRadii", "shadowAlphas", "shadowColor") +
      // `nodeClip` è di chi non ha il ritaglio del nodo (Fluid-physics): una lastra ferma lo lascia a zero.
      setOf("nodeClip")
  }

  /**
   * @param complete vero per l'elemento di una **traccia**: il validatore del movimento chiede ogni uniform
   *   dichiarato, quindi anche una lastra ferma li porta tutti (`completeRestBody`).
   */
  private fun checkElement(where: String, el: Map<String, Any?>, complete: Boolean = false) {
    assertEquals(where, "material", el["kind"])
    val bodySource = (el["body"] as Map<*, *>)["source"] as String
    val body = declared(sources.getValue(bodySource))
    val set = uniforms(el["body"]).keys
    if (bodySource == "kotlin:GlassBody1ShaderString" && !complete) {
      assertEquals(where, body - inBodyOnly, set)
    } else {
      // Una superficie fisica scrive tutto, sempre: l'istanza dello shader è una per processo.
      assertEquals(where, body, set)
    }
    for (layer in lights(el)) {
      assertEquals(where, declared(sources.getValue(layer["source"] as String)), uniforms(layer).keys)
    }
    assertFalse("$where: senza ombra (F1B.2)", "shadow" in el)
  }

  @Test
  fun everySceneSetsExactlyTheUniformsItsProgramsDeclare() {
    var count = 0
    for (scene in scenes) {
      for (el in elements(scene)) {
        checkElement("${scene.path}: ${el["name"]}", el)
        count++
      }
      assertTrue(scene.encode().isNotEmpty())
    }
    assertTrue("poche lastre: $count", count > 150)
    // Ogni scena c'è nei due temi.
    assertEquals(scenes.size, scenes.map { it.path }.toSet().size)
    assertEquals(scenes.count { it.path.endsWith("-chiaro.json") }, scenes.count { it.path.endsWith("-scuro.json") })
  }

  @Test
  fun atRestThePiecesAreApartAndTheLightIsInTheBodyLikeInTravel() {
    for (count in listOf(2, 4, 8)) {
      val scene = scenes.single { it.path == "gruppo-$count-chiaro.json" }
      val rest = elements(scene).single { it["name"] == "riposo-0" }
      val travel = elements(scene).single { it["name"] == "viaggio-0" }
      val restBody = uniforms(rest["body"])
      val travelBody = uniforms(travel["body"])
      assertEquals(count, restBody["pieceCount"])
      assertEquals(count, travelBody["pieceCount"])
      // A riposo nessun raggio di fusione. La luce è nel corpo, come in viaggio: nessun disegno sul nodo.
      assertEquals(List(8) { if (it < count) "0dp" else 0 }, restBody["pieceBlend"])
      assertEquals(1, restBody["lightInBody"])
      assertTrue(lights(rest).isEmpty())
      // Il ritaglio del nodo lo emula il corpo, fermo o in viaggio.
      assertEquals(listOf(1, "0dp"), restBody["nodeClip"])
      assertEquals(listOf(1, "0dp"), travelBody["nodeClip"])
      // In viaggio il collo è vivo e la luce è nel corpo.
      @Suppress("UNCHECKED_CAST")
      assertEquals("16dp", (travelBody["pieceBlend"] as List<Any?>)[1])
      assertEquals(1, travelBody["lightInBody"])
      assertTrue(lights(travel).isEmpty())
      // Stesso materiale: ciò che non è geometria né luce in corpo è uguale nei due.
      for (key in listOf("thickness", "refraction", "dome", "veilColor", "veilAdapt", "bodyShade", "bodyShadeDepth", "rimWidth", "rimDark", "reflexStrength")) {
        assertEquals("gruppo-$count: $key", restBody[key], travelBody[key])
      }
      assertEquals(rest["capture"], travel["capture"])
    }
  }

  @Test
  fun restAndTravelSetTheSameNumbersForTheSameSlab() {
    for (theme in listOf("chiaro", "scuro")) {
      fun scene(which: F1bScenes.RestVersusTravel) = scenes.single { it.path == "riposo-contro-viaggio-${which.suffix}-$theme.json" }
      val glass = elements(scene(F1bScenes.RestVersusTravel.Glass))
      val physics = elements(scene(F1bScenes.RestVersusTravel.PhysicsRest))
      val start = elements(scene(F1bScenes.RestVersusTravel.TravelStart))
      val end = elements(scene(F1bScenes.RestVersusTravel.TravelEnd))
      assertEquals(9, glass.size)
      for (i in glass.indices) {
        val where = "$theme ${glass[i]["name"]}"
        val g = uniforms(glass[i]["body"])
        // Il materiale: la lastra di glassSurface, la fisica ferma e i due capi del viaggio hanno gli stessi numeri.
        for (other in listOf(physics[i], start[i], end[i])) {
          val o = uniforms(other["body"])
          for (key in listOf(
            "thickness", "refraction", "dome", "dispersion", "magnify", "materialize", "veilColor", "veilAdapt", "tintColor",
            "tintMode", "tintFollow", "bodyShade", "bodyShadeDepth", "bodyShadeLean", "dim", "grain", "aa",
          )) {
            // Un numero è un numero comunque sia scritto (0 e 0.0): il banco li legge uguali.
            val expected = g[key]
            val actual = o[key]
            if (expected is Number && actual is Number) {
              assertEquals("$where: $key", expected.toDouble(), actual.toDouble(), 0.0)
            } else {
              assertEquals("$where: $key", expected, actual)
            }
          }
          @Suppress("UNCHECKED_CAST")
          val capture = other["capture"] as Map<String, Any?>
          @Suppress("UNCHECKED_CAST")
          val restCapture = glass[i]["capture"] as Map<String, Any?>
          // La sfocatura della fisica passa per i pixel del piano e torna in dp: può differire dall'altra
          // dell'ultima cifra di un float (5,337817 contro 5,3378167), cioè di niente.
          for (key in listOf("saturation", "blurDp", "w", "scale")) {
            val expected = (restCapture[key] as Number).toDouble()
            assertEquals("$where: $key", expected, (capture[key] as Number).toDouble(), abs(expected) * 1e-6)
          }
          // La sagoma: gli stessi raggi e lo stesso raccordo.
          @Suppress("UNCHECKED_CAST")
          assertEquals("$where: raccordo", (g["pieceSmooth"] as List<Any?>)[0], (o["pieceSmooth"] as List<Any?>)[0])
        }
        // La luce: sul nodo per la lastra di `glassSurface`; nel corpo, con gli stessi numeri, per la fisica,
        // ferma o in viaggio.
        val lightGlass = uniforms(lights(glass[i]).last())
        for (other in listOf(physics[i], start[i], end[i])) {
          val body = uniforms(other["body"])
          for (key in listOf("rimWidth", "rimDark", "rimFeather", "rimLift", "reflexDir", "reflexWidths", "reflexStrength", "reflexDepth", "reflexFloor", "reflexHalo", "lightColor")) {
            assertEquals("$where: luce in corpo $key", lightGlass[key], body[key])
          }
          assertTrue(lights(other).isEmpty())
          assertEquals(1, body["lightInBody"])
          @Suppress("UNCHECKED_CAST")
          assertEquals(1, (body["nodeClip"] as List<Any?>)[0])
        }
        assertEquals(2, lights(glass[i]).size)
        // La fisica ferma e il primo fotogramma del viaggio sono lo stesso disegno: stessi uniform, tutti.
        assertEquals("$where: fermo e primo fotogramma", uniforms(physics[i]["body"]), uniforms(start[i]["body"]))
      }
    }
  }

  @Test
  fun aPieceBornFromTheFlankOfItsNeighbourStartsAtNothingAndLeavesWithoutABridge() {
    // Il caso delle fasi dopo: un tasto nuovo nasce dal fianco del vicino con un collo che si rompe. Il piano è
    // quello del motore; qui si controlla ciò che arriva al campo ai due capi e in mezzo.
    val first = F1bScenes.birthPlan(16f, 24f, 0f)
    val mid = F1bScenes.birthPlan(16f, 24f, 0.35f)
    val last = F1bScenes.birthPlan(16f, 24f, 1f)
    for (plan in listOf(first, mid, last)) assertEquals(2, plan.pieceCount)
    // Alla nascita il pezzo nuovo è un punto (il lato minimo del motore) e il collo è spento: nessuna gobba sul
    // fianco del vicino al primo fotogramma.
    assertEquals(0f, first.fusion, 0f)
    assertTrue("alla nascita il pezzo è largo ${first.pieceRects[4 + 2]} px", first.pieceRects[4 + 2] <= 2f && first.pieceRects[4 + 3] <= 2f)
    // E sta tutto dentro il vicino (un cerchio): il primo fotogramma è il vicino da solo, senza un pixel che sporge.
    val reach = kotlin.math.hypot(first.pieceRects[4] - first.pieceRects[0], first.pieceRects[5] - first.pieceRects[1]) +
      kotlin.math.hypot(first.pieceRects[4 + 2], first.pieceRects[4 + 3])
    assertTrue("il seme arriva a $reach px dal centro del vicino, che ha raggio ${first.pieceRects[2]}", reach < first.pieceRects[2] - 1f)
    // In mezzo il collo è vivo e il pezzo cresce; all'arrivo è spento e i due sono staccati.
    assertTrue(mid.fusion > 0.5f)
    assertTrue(mid.pieceRects[4 + 2] > first.pieceRects[4 + 2])
    assertEquals(0f, last.fusion, 0f)
    // Niente di non finito negli uniform, a nessun progresso (un raggio 0/0, un raccordo su un lato nullo).
    var t = 0f
    while (t <= 1f) {
      val plan = F1bScenes.birthPlan(16f, 24f, t)
      for (i in 0 until 8) {
        assertTrue("t=$t rect[$i]", plan.pieceRects[i].isFinite())
        assertTrue("t=$t raggio[$i]", plan.fieldRadii[i].isFinite() && plan.fieldRadii[i] >= 0f)
      }
      assertTrue("t=$t raccordo", plan.fieldSmooth[0].isFinite() && plan.fieldSmooth[1].isFinite())
      assertTrue("t=$t fusione", plan.fusion in 0f..1f)
      t += 0.05f
    }
    // E la scena lo porta nei due temi.
    val scene = scenes.single { it.path == "ponte-stretto-chiaro.json" }
    assertEquals(F1bScenes.BirthProgress.size * 3, elements(scene).count { (it["name"] as String).startsWith("nascita-") })
  }

  @Test
  fun theWideCornersReachTheFieldAsComposeDrawsThem() {
    val scene = scenes.single { it.path == "angolo-oltre-meta-chiaro.json" }
    fun radii(name: String): List<Any?> {
      val el = elements(scene).single { it["name"] == "$name-0" }
      @Suppress("UNCHECKED_CAST")
      return (uniforms(el["body"])["pieceRadii"] as List<List<Any?>>)[0]
    }
    // Il fumetto alto 40: l'angolo da 22 resta 22 (oltre mezzo lato), la coppia di destra scende a 20 e 20.
    assertEquals(listOf("22dp", "20dp", "20dp", "6dp"), radii("fumetto-40"))
    assertEquals(listOf("22dp", "22dp", "22dp", "6dp"), radii("fumetto-44"))
    assertEquals(listOf("80dp", "20dp", "20dp", "20dp"), radii("angolo-80"))
    assertEquals(listOf("80dp", "20dp", "80dp", "20dp"), radii("foglia"))
  }

  @Test
  fun theScenesAreWrittenOnlyWhenAsked() {
    val dir = GlassSceneOutput.requestedDir(System.getenv(SceneVariable)) ?: return
    val written = GlassSceneOutput.write(dir, scenes)
    println("scene di F1B.1 scritte: ${written.size} in $dir")
    for (file in written) assertTrue(file.path, file.isFile && file.length() > 0)
  }

  // ---- Le tracce ---------------------------------------------------------------------------------------------

  private fun bodies(trace: GlassTrace, name: String): List<Map<String, Any?>> =
    trace.frames.mapNotNull { frame -> frame.elements.firstOrNull { it["name"] == name } }

  private fun finish(trace: GlassTrace) {
    for (frame in trace.frames) for (el in frame.elements) {
      if (el["kind"] == "material") checkElement("${trace.name} @${frame.tMs}: ${el["name"]}", el, complete = true)
    }
    assertTrue(trace.encode().isNotEmpty())
    GlassTraceOutput.writeIfRequested(trace)?.let { println("traccia scritta: $it (${trace.frames.size} fotogrammi)") }
  }

  /** Il margine della cattura della superficie fisica lungo la traccia: uno solo, o la cattura si rifarebbe. */
  private fun paddings(trace: GlassTrace, name: String): Set<Any?> =
    bodies(trace, name).map { (it["capture"] as Map<*, *>)["paddingDp"] }.toSet()

  @Test
  fun theMenuJourneyIsTheSameRunDressedInTheNewMaterial() {
    val today = OggiMenuApertura.build()
    val built = F1bTraces.menu()
    val trace = built.trace
    assertEquals(F1bTraces.Phase, trace.phase)
    // Stessa corsa: stessi fotogrammi, stessi istanti, stessa sagoma d'arrivo.
    assertEquals(today.trace.frames.size, trace.frames.size)
    assertEquals(today.trace.frames.map { it.tMs }, trace.frames.map { it.tMs })
    assertEquals(today.panelPx, built.panelPx)
    assertEquals(today.ringFrames, built.ringFrames)
    val menu = bodies(trace, "menu")
    assertTrue(menu.isNotEmpty())
    assertEquals("il margine della cattura è uno per tutto il viaggio", 1, paddings(trace, "menu").size)
    // La goccia è un anello con la luce in corpo; posata è una lastra, e la luce resta nel corpo.
    assertTrue(menu.any { (it["body"] as Map<*, *>)["source"] == "kotlin:GlassBodyRingShaderString" })
    val last = menu.last()
    assertEquals("kotlin:GlassBody8ShaderString", (last["body"] as Map<*, *>)["source"])
    assertEquals(1, uniforms(last["body"])["lightInBody"])
    assertTrue(lights(last).isEmpty())
    finish(trace)
  }

  @Test
  fun theBarJourneyIsTheSameRunDressedInTheNewMaterial() {
    val today = OggiBarraPagine.build()
    val built = F1bTraces.bar()
    val trace = built.trace
    assertEquals(today.trace.frames.size, trace.frames.size)
    assertEquals(today.morphs, built.morphs)
    assertEquals(today.settledFrame, built.settledFrame)
    val bar = bodies(trace, "barra")
    assertEquals(today.morphs.size, bar.size)
    assertEquals(1, paddings(trace, "barra").size)
    // Quattro pezzi in un campo solo; a riposo (il primo fotogramma del palco, a progresso 1) nessun collo.
    assertEquals(4, uniforms(bar.first()["body"])["pieceCount"])
    assertEquals(List(8) { if (it < 4) "0dp" else 0 }, uniforms(bar.first()["body"])["pieceBlend"])
    // I tasti fermi prima e dopo sono lastre del materiale nuovo.
    assertEquals("kotlin:GlassBody1ShaderString", (trace.frames.first().elements.first()["body"] as Map<*, *>)["source"])
    assertEquals("kotlin:GlassBody1ShaderString", (trace.frames.last().elements.first()["body"] as Map<*, *>)["source"])
    finish(trace)
  }

  @Test
  fun aButtonBecomesTwoAndComesBackWithANeckOnlyOnTheWay() {
    val built = F1bTraces.split()
    // A riposo: uno, poi due separati, poi di nuovo uno. Mai un collo da fermi.
    assertEquals(1, built.pieces.first())
    assertEquals(1, built.pieces.last())
    assertTrue(built.pieces.contains(2))
    built.rests.forEachIndexed { i, rest -> if (rest) assertEquals("fotogramma $i fermo", 0f, built.fusions[i], 0f) }
    // In viaggio il collo nasce e muore: c'è almeno un fotogramma col collo pieno, e la fusione non salta.
    assertTrue(built.fusions.max() > 0.95f)
    for (i in 1 until built.fusions.size) {
      assertTrue("fra $i-1 e $i: ${built.fusions[i - 1]} -> ${built.fusions[i]}", kotlin.math.abs(built.fusions[i] - built.fusions[i - 1]) < 0.45f)
    }
    assertEquals(1, paddings(built.trace, "superficie").size)
    finish(built.trace)
  }

  @Test
  fun aGroupOfButtonsBecomesAMenu() {
    val built = F1bTraces.groupToMenu()
    assertEquals(3, built.pieces.first())
    assertEquals(1, built.pieces.last())
    built.rests.forEachIndexed { i, rest -> if (rest) assertEquals(0f, built.fusions[i], 0f) }
    val frames = bodies(built.trace, "superficie")
    // Parte col vetro dei tasti e arriva con quello del pannello: la banda della lente cresce con la sagoma.
    assertEquals("10.56dp", uniforms(frames.first()["body"])["thickness"])
    assertEquals("40dp", uniforms(frames.last()["body"])["thickness"])
    assertEquals(1, paddings(built.trace, "superficie").size)
    finish(built.trace)
  }

  private companion object {
    const val SceneVariable = "GLASS_F1B_SCENE_DIR"
  }
}
