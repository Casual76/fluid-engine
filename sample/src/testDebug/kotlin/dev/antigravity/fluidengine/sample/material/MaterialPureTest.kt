package dev.antigravity.fluidengine.sample.material

import dev.antigravity.fluidengine.sample.measure.MeasureConfig
import dev.antigravity.fluidengine.sample.measure.compilePlan
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La parte pura della pagina «Materiale» (F1A.9): è ciò che la sessione sul tablet scrive e legge da riga di comando.
 * Un extra letto male è una foto della scena sbagliata, e una riga di logcat che cambia formato è un collage che
 * non si rifà: per questo sono fermi qui.
 */
class MaterialPureTest {

  private fun material(vararg extras: Pair<String, String>): MaterialConfig? = MaterialConfig.parse(mapOf(*extras)::get)

  private fun gallery(vararg extras: Pair<String, String>): GalleryGlassLaunch = GalleryGlassLaunch.parse(mapOf(*extras)::get)

  // --- Le scene ---------------------------------------------------------------------------------------------

  @Test
  fun `ogni scena ha un nome suo, un titolo e le voci che copre`() {
    val keys = MaterialScene.entries.map { it.key }
    assertEquals("nomi ripetuti", keys.size, keys.toSet().size)
    MaterialScene.entries.forEach { scene ->
      assertTrue(scene.key, scene.key.matches(Regex("[a-z]+")))
      assertTrue(scene.key, scene.title.isNotBlank() && scene.detail.isNotBlank())
      assertTrue("${scene.key}: quali voci copre", scene.voices.startsWith("F1A."))
      assertEquals(scene, MaterialConfig.defaults(scene).scene)
    }
    // Le tredici scene: le quattordici voci di F1A.6 (il ritorno dallo sfondo non ha una scena sua), F1A.7, F1A.8.
    assertEquals(13, MaterialScene.entries.size)
  }

  @Test
  fun `ogni voce di F1A_6 ha una scena`() {
    val covered = MaterialScene.entries
      .flatMap { scene ->
        Regex("F1A\\.6 ([0-9, ]+)").find(scene.voices)?.groupValues?.get(1)?.split(',').orEmpty()
      }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { it.toInt() }
      .toSet()
    // La 8 (ritorno dallo sfondo) sta con `components` e il tasto Home; la 14 (destra-sinistra e orologio) con `rtl`,
    // e l'orologio non è nella galleria.
    assertEquals((1..14).toSet(), covered)
    // F1A.8 ha le sue scene.
    assertTrue(MaterialScene.entries.count { "F1A.8" in it.voices } >= 3)
  }

  // --- Gli extra della pagina ---------------------------------------------------------------------------------

  @Test
  fun `senza scena la pagina mostra l'elenco`() {
    assertNull(material())
    assertNull(material("material.scene" to "nessuna"))
    assertNull(material("material.backdrop" to "photo"))
  }

  @Test
  fun `una scena parte coi suoi default`() {
    val edge = material("material.scene" to "EDGE")!!
    assertEquals(MaterialConfig.defaults(MaterialScene.Edge), edge)
    assertEquals(MaterialBackdrop.Page, edge.backdrop)
    assertEquals(MaterialMotion.Still, edge.motion)
    assertEquals(MaterialModels.Both, edge.models)
    assertTrue(edge.hud)
    assertEquals(MaterialBackdrop.Photo, material("material.scene" to "apps")!!.backdrop)
    assertEquals(MaterialBackdrop.Text, material("material.scene" to "press")!!.backdrop)
  }

  @Test
  fun `gli extra cambiano la scena, e un valore sbagliato resta il default`() {
    val config = material(
      "material.scene" to "intensity",
      "material.backdrop" to "page-dark",
      "material.motion" to "scroll",
      "material.speed" to "600",
      "material.model" to "liquid",
      "material.cycle" to "hold",
      "material.period" to "2,5",
      "material.intensity" to "0.5",
      "material.hud" to "off",
    )!!
    assertEquals(MaterialBackdrop.PageDark, config.backdrop)
    assertEquals(MaterialMotion.Scroll, config.motion)
    assertEquals(600f, config.speedDp, 0f)
    assertEquals(MaterialModels.Liquid, config.models)
    assertEquals(MaterialCycle.Hold, config.cycle)
    assertEquals(2.5f, config.periodSeconds, 0f)
    assertEquals(0.5f, config.intensity!!, 0f)
    assertFalse(config.hud)

    val wrong = material(
      "material.scene" to "intensity",
      "material.backdrop" to "marmo",
      "material.model" to "tutti",
      "material.intensity" to "molta",
      "material.period" to "0",
    )!!
    assertEquals(MaterialConfig.defaults(MaterialScene.Intensity).copy(periodSeconds = 0.5f), wrong)
  }

  @Test
  fun `il contrasto si chiede per nome o per numero`() {
    assertNull(material("material.scene" to "contrast")!!.contrast)
    assertEquals(0f, material("material.scene" to "contrast", "material.contrast" to "standard")!!.contrast!!, 0f)
    assertEquals(0.5f, material("material.scene" to "contrast", "material.contrast" to "medium")!!.contrast!!, 0f)
    assertEquals(1f, material("material.scene" to "contrast", "material.contrast" to "high")!!.contrast!!, 0f)
    assertEquals(1f, material("material.scene" to "contrast", "material.contrast" to "7")!!.contrast!!, 0f)
    assertNull(material("material.scene" to "contrast", "material.contrast" to "all")!!.contrast)
  }

  @Test
  fun `la chiave porta solo cio' che la scena legge`() {
    assertEquals(
      "scene=components backdrop=page motion=still models=both",
      MaterialConfig.defaults(MaterialScene.Components).key,
    )
    assertEquals(
      "scene=press backdrop=text motion=scroll speed=240 models=both cycle=cycle period=3",
      MaterialConfig.defaults(MaterialScene.Press).copy(motion = MaterialMotion.Scroll).key,
    )
    assertEquals(
      "scene=intensity backdrop=page motion=still models=liquid cycle=hold period=3 intensity=0.25",
      MaterialConfig(MaterialScene.Intensity, models = MaterialModels.Liquid, cycle = MaterialCycle.Hold, intensity = 0.25f).key,
    )
    assertEquals(
      "scene=contrast backdrop=page motion=still models=both contrast=0.50",
      MaterialConfig(MaterialScene.Contrast, contrast = 0.5f).key,
    )
  }

  // --- Gli interruttori della galleria -----------------------------------------------------------------------

  @Test
  fun `gli interruttori della galleria`() {
    assertTrue(gallery().isEmpty)
    assertEquals(FluidGlassModel.Classic, gallery("gallery.model" to "Classic").model)
    assertEquals(FluidGlassModel.Liquid, gallery("gallery.model" to "liquid").model)
    assertNull(gallery("gallery.model" to "vetro").model)
    assertEquals(32, gallery("gallery.api" to "32").api)
    assertEquals(30, gallery("gallery.api" to " 30 ").api)
    assertEquals(GalleryGlassLaunch.ApiOff, gallery("gallery.api" to "off").api)
    assertNull(gallery("gallery.api" to "dodici").api)
    assertEquals(false, gallery("gallery.warmup" to "off").warmUp)
    assertEquals(true, gallery("gallery.warmup" to "on").warmUp)
    assertNull(gallery("gallery.warmup" to "forse").warmUp)
    assertEquals(mapOf("edge" to "ios26", "blur" to "24"), gallery("gallery.tune" to "edge=ios26,blur=24").tune)
    assertEquals(emptyMap<String, String>(), gallery("gallery.tune" to "off").tune)
    assertFalse(gallery("gallery.warmup" to "off").isEmpty)
  }

  @Test
  fun `le manopole si scrivono e si leggono allo stesso modo`() {
    val tune = GalleryGlassLaunch.parseTune("Edge=IOS26; blur=24 rimalpha=0.4,senza,=vuoto,vuoto=")
    assertEquals(mapOf("edge" to "ios26", "blur" to "24", "rimalpha" to "0.4"), tune)
    assertEquals("edge=ios26,blur=24,rimalpha=0.4", GalleryGlassLaunch.tuneText(tune))
    assertEquals(tune, GalleryGlassLaunch.parseTune(GalleryGlassLaunch.tuneText(tune)))
    assertEquals("off", GalleryGlassLaunch.tuneText(emptyMap()))
    assertEquals(emptyMap<String, String>(), GalleryGlassLaunch.parseTune("default"))
  }

  @Test
  fun `la riga del profilo dice cosa l'engine ha preso e cosa ha scartato`() {
    assertEquals(
      "PROFILO chiesto=edge=ios26,colore=rosso preso=edge=ios26 scartato=colore",
      GalleryGlassSwitches.tuneLine(mapOf("edge" to "ios26", "colore" to "rosso"), mapOf("edge" to "ios26"), applied = true, byHand = false),
    )
    assertEquals(
      "PROFILO chiesto=off preso=off scartato=- (a mano)",
      GalleryGlassSwitches.tuneLine(emptyMap(), emptyMap(), applied = true, byHand = true),
    )
    assertTrue(
      GalleryGlassSwitches.tuneLine(mapOf("blur" to "24"), emptyMap(), applied = false, byHand = false)
        .endsWith("NON APPLICATO (app non debuggable)"),
    )
  }

  // --- Lo stato --------------------------------------------------------------------------------------------

  @Test
  fun `lo stato si legge in righe fisse`() {
    val status = MaterialStatus(
      requested = FluidGlassModel.Liquid,
      realApi = 36,
      simulatedApi = null,
      warmUpEnabled = true,
      diagnosticsOn = true,
      surfaces = mapOf(FluidGlassModel.Liquid to 9, FluidGlassModel.Classic to 11),
      choices = mapOf(
        FluidGlassModelReason.Liquid to 9,
        FluidGlassModelReason.ClassicRequested to 10,
        FluidGlassModelReason.FadeDown to 0,
      ),
      nodes = mapOf(FluidGlassNodeKind.Backdrop to 20, FluidGlassNodeKind.Shadow to 6),
      layers = mapOf(FluidGlassLayerKind.Capture to 20, FluidGlassLayerKind.Material to 0),
      warmUp = listOf("Slab: Done (2/2), principale 3.1 ms"),
      shadersTried = 13,
      shadersRefused = emptyList(),
      tuning = mapOf("edge" to "ios26"),
    )
    assertEquals(
      listOf(
        "vetro chiesto=liquid api=36 simulato=- riscaldamento=on diagnostica=on",
        "superfici liquid=9 classic=11 | perche Liquid=9 ClassicRequested=10",
        "nodi Backdrop=20 Shadow=6 | layer Capture=20",
        "caldo Slab: Done (2/2), principale 3.1 ms",
        "shader provate=13 rifiutate=-",
        "profilo edge=ios26",
      ),
      status.lines(),
    )
    assertTrue(status.logLine("scene=edge").startsWith("STATO scene=edge | vetro chiesto=liquid"))
  }

  // --- Il passo `engine` di «Misure» --------------------------------------------------------------------------

  @Test
  fun `il passo engine entra una volta sola, al suo posto`() {
    assertEquals(listOf("engine", "body1"), MeasureConfig.parseCompileSteps("engine,body1"))
    assertNull("due volte", MeasureConfig.parseCompileSteps("engine,engine"))
    val plan = compilePlan(listOf("body1", "engine", "light1"))
    assertEquals(1, plan.engineStep)
    assertEquals(1, plan.elements.size)
    assertEquals(-1, compilePlan(MeasureConfig.DefaultCompileSteps).engineStep)
    assertTrue(MeasureConfig.parse(mapOf("measure.scene" to "compile", "measure.steps" to "engine,body1")::get)!!.key.contains("steps=engine,body1"))
  }

  // --- Il LEGGIMI ------------------------------------------------------------------------------------------

  /**
   * Il `LEGGIMI.md` è ciò che l'agente della sessione segue alla lettera: ogni scena ci deve stare col suo comando,
   * e ogni extra che la pagina legge col suo nome.
   */
  @Test
  fun `il LEGGIMI copre ogni scena e ogni extra`() {
    val readme = File("src/main/kotlin/dev/antigravity/fluidengine/sample/material/LEGGIMI.md")
    assertTrue("manca ${readme.absolutePath}", readme.isFile)
    val text = readme.readText()
    MaterialScene.entries.forEach { scene ->
      assertTrue("la scena ${scene.key} non ha un comando", text.contains("scena ${scene.key}"))
    }
    listOf("backdrop", "motion", "speed", "model", "cycle", "period", "intensity", "contrast", "hud").forEach { name ->
      assertTrue("manca material.$name", text.contains("material.$name"))
    }
    listOf("gallery.model", "gallery.api", "gallery.warmup", "gallery.tune").forEach { name ->
      assertTrue("manca $name", text.contains(name))
    }
    MaterialBackdrop.entries.forEach { backdrop -> assertTrue("manca il fondo ${backdrop.key}", text.contains("`${backdrop.key}`")) }
    for (voice in 1..14) assertTrue("manca la voce F1A.6 $voice", text.contains("| 6.$voice "))
  }
}
