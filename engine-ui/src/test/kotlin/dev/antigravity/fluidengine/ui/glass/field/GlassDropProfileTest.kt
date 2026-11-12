package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassScene
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassSceneOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.TodaySpring
import dev.antigravity.fluidengine.ui.glass.field.trace.glassColor
import dev.antigravity.fluidengine.ui.glass.field.trace.glassDp
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeMs
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeNanos
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import dev.antigravity.fluidengine.ui.glass.field.trace.round3
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropFaces
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropFaceAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il profilo della goccia (F1B.3, `GlassDropProfile.kt`): che sia **derivato** dal profilo di serie e non ricopiato,
 * che sia limpido, che la lente cresca con la pressione mentre filo, riflesso e ombra restano quelli di un tasto, e
 * che la regola del vetro annidato ne riduca l'ombra come quella di un tasto.
 *
 * In fondo, **solo se** ci sono le variabili d'ambiente, scrive le scene del banco (`GLASS_DROP_SCENE_DIR`, di solito
 * `tools/glassbench/scenes/f1b`: `pomello-*`, `goccia-a-meta-*`, `goccia-lente-*`) e la traccia della goccia
 * dell'interruttore (`GLASS_DROP_TRACE_DIR`, di solito `artifacts/liquid-glass/moto`: `f1b/goccia-interruttore-*`).
 */
class GlassDropProfileTest {

  private val base = GlassLiquidProfile.Shipped
  private val key = GlassDefaults.buttonOptics().sanitized()

  private fun drop(w: Float, h: Float, dark: Boolean, p: Float, nested: Boolean = false, profile: GlassLiquidProfile = glassDropProfile(base)) =
    glassDropRecipe(w, h, dark, p, nested = nested, profile = profile)

  private fun keyRecipe(w: Float, h: Float, dark: Boolean, nested: Boolean = false) =
    glassRecipe(GlassRole.Interactive, w, h, dark, optics = key, nested = nested, profile = base)

  @Test
  fun theDropFollowsTheStockProfileInsteadOfCopyingIt() {
    val other = base.copy(
      lightEdge = base.lightEdge.copy(rim = Color.Black.copy(alpha = 0.5f)),
      lightShadow = base.lightShadow.copy(ambientAlpha = 0.2f),
      lightVeil = base.lightVeil.copy(shade = Color(0x30000000)),
      lens = GlassLiquidLenses.Strong,
      travel = GlassLiquidTravels.Strong,
    )
    val stock = glassDropProfile(base)
    val moved = glassDropProfile(other)
    assertEquals(other.lightEdge, moved.lightEdge)
    assertEquals(other.lightShadow, moved.lightShadow)
    assertEquals(other.lightVeil.shade, moved.lightVeil.shade)
    assertNotEquals(stock.lens, moved.lens)
    assertEquals(glassDropLens(GlassLiquidLenses.Strong, GlassLiquidTravels.Strong), moved.lens)
    // Bordo, ombra, piccoli e annidato sono quelli di serie, per intero.
    assertEquals(base.darkEdge, stock.darkEdge)
    assertEquals(base.darkShadow, stock.darkShadow)
    assertEquals(base.smallShadow, stock.smallShadow, 0f)
    assertEquals(base.lightNesting, stock.lightNesting)
    assertEquals(base.darkNesting, stock.darkNesting)
    assertEquals(base.growth, stock.growth)
  }

  @Test
  fun theLensIsTheStockBandBentLikeTheTravellingMaterialAndFlat() {
    val lens = glassDropLens(base.lens, base.travel)
    assertEquals(base.lens.band, lens.band, 0f)
    assertEquals(base.lens.bend * (1f + base.travel.bend) * GlassDropBend, lens.bend, 1e-6f)
    assertEquals(GlassDropDome, lens.dome, 0f)
    // Con il viaggio spento resta soltanto il fattore della goccia: è il materiale in viaggio che la fa piegare.
    assertEquals(base.lens.bend * GlassDropBend, glassDropLens(base.lens, GlassLiquidTravels.Off).bend, 1e-6f)
  }

  @Test
  fun theDropIsClearNotASecondFrostedGlass() {
    for (dark in listOf(false, true)) {
      for ((w, h) in listOf(40f to 24f, 60f to 28f, 236f to 44f)) {
        val r = drop(w, h, dark, 1f)
        assertEquals("mescola", 1f, r.mix, 0f)
        assertEquals("saturazione", 1f, r.saturation, 0f)
        // Niente velo chiaro: un nero leggero, uguale ovunque (Apple: sotto la lente il fondo scurisce, non schiarisce).
        assertEquals("tono", GlassDropTone, r.veilColor.alpha, 1f / 255f)
        assertEquals(0f, r.veilColor.red, 0f)
        assertEquals(0f, r.veilAdapt, 0f)
        // L'ombreggiatura del corpo resta, quella del profilo.
        assertEquals(base.veil(dark).shade, r.bodyShade)
        assertEquals(0f, r.dim, 0f)
      }
    }
  }

  @Test
  fun theLensGrowsWithThePressureAndTheCaptureStaysPut() {
    for (dark in listOf(false, true)) {
      val rest = drop(40f, 24f, dark, 0f)
      val half = drop(40f, 24f, dark, 0.5f)
      val full = drop(40f, 24f, dark, 1f)
      assertEquals(0f, abs(rest.refractionDp), 0f)
      assertTrue(abs(half.refractionDp) > 0f)
      assertEquals(abs(full.refractionDp) / 2f, abs(half.refractionDp), 1e-4f)
      // La banda e il margine non seguono la pressione: la cattura non si rifà a ogni fotogramma.
      assertEquals(rest.thicknessDp, full.thicknessDp, 0f)
      assertEquals(rest.paddingDp, full.paddingDp, 0f)
      assertEquals(rest.blurDp, full.blurDp, 0f)
      // Piega più di un tasto premuto: la goccia è la lente limpida e rifrangente.
      val pressedKey = glassRecipe(GlassRole.Interactive, 40f, 24f, dark, optics = key, pressed = 1f, profile = base)
      assertTrue("goccia ${full.refractionDp} contro tasto ${pressedKey.refractionDp}", abs(full.refractionDp) > abs(pressedKey.refractionDp))
    }
  }

  @Test
  fun edgeAndShadowAreThoseOfAKeyAndDoNotFollowThePressure() {
    for (dark in listOf(false, true)) {
      for (nested in listOf(false, true)) {
        val k = keyRecipe(40f, 24f, dark, nested)
        for (p in listOf(0f, 0.3f, 1f)) {
          val r = drop(40f, 24f, dark, p, nested)
          assertEquals(k.rimWidthDp, r.rimWidthDp, 0f)
          assertEquals(k.rimDark, r.rimDark)
          assertEquals(k.reflexStrength, r.reflexStrength, 0f)
          assertEquals(k.reflexDepthDp, r.reflexDepthDp, 0f)
          assertEquals(k.reflexHalo, r.reflexHalo, 0f)
          assertEquals(k.shadowAmbientAlpha, r.shadowAmbientAlpha, 0f)
          assertEquals(k.shadowContactAlpha, r.shadowContactAlpha, 0f)
          assertEquals(k.shadowAmbientRadiusDp, r.shadowAmbientRadiusDp, 0f)
          assertEquals(k.shadowOffsetYDp, r.shadowOffsetYDp, 0f)
          // La frangia no: la goccia piega il doppio di un tasto, e la frangia ne è una frazione.
          assertEquals(0f, r.dispersion, 0f)
        }
      }
    }
  }

  @Test
  fun inAPaneTheShadowIsReducedLikeAKeysAndOnlyOnce() {
    for (dark in listOf(false, true)) {
      val alone = drop(60f, 28f, dark, 0f, nested = false)
      val nested = drop(60f, 28f, dark, 0f, nested = true)
      val factor = base.nesting(dark).shadow
      assertEquals(alone.shadowAmbientAlpha * factor, nested.shadowAmbientAlpha, 1e-6f)
      assertTrue(nested.shadowAmbientAlpha < alone.shadowAmbientAlpha)
      // Il tono segue la regola come il velo di un tasto: sullo scuro un terzo.
      assertEquals(alone.veilColor.alpha * base.nesting(dark).veil, nested.veilColor.alpha, 1f / 255f)
    }
  }

  @Test
  fun theLightComesWithTheLensAndTheRestingKnobIsACleanFace() {
    // A riposo niente filo né riflesso: il pomello è la faccia con la sua ombra (S2: il filo a 202 attorno al bianco).
    assertEquals(0f, glassDropLightAmount(0f, restRim = false), 0f)
    // Sotto il dito la luce viene in proporzione alla pressione, come la lente.
    for (p in listOf(0.1f, 0.25f, 0.5f, 0.9f)) assertEquals(p, glassDropLightAmount(p, restRim = false), 0f)
    assertEquals(1f, glassDropLightAmount(1f, restRim = false), 0f)
    // Fuori da 0..1 (una molla che sfora) resta dentro.
    assertEquals(1f, glassDropLightAmount(1.03f, restRim = false), 0f)
    assertEquals(0f, glassDropLightAmount(-0.02f, restRim = false), 0f)
    // Con la manopola `knobrim=on` la luce di prima, piena a ogni pressione.
    assertEquals(1f, glassDropLightAmount(0f, restRim = true), 0f)
    assertEquals(1f, glassDropLightAmount(0.4f, restRim = true), 0f)
    // L'ombra non dipende dalla pressione: resta quella del tasto anche a riposo (vedi sotto).
    for (dark in listOf(false, true)) {
      assertEquals(drop(40f, 24f, dark, 0f).shadowAmbientAlpha, drop(40f, 24f, dark, 1f).shadowAmbientAlpha, 0f)
      assertTrue(drop(40f, 24f, dark, 0f).shadowAmbientAlpha > 0f)
    }
  }

  @Test
  fun theKnobRimKnobTakesOnAndOffAndNothingElse() {
    try {
      assertEquals("on", GlassDropTuning.apply(mapOf("KnobRim" to " ON ")))
      assertTrue(GlassDropTuning.restRim)
      assertEquals("off", GlassDropTuning.apply(mapOf("knobrim" to "off")))
      assertEquals(false, GlassDropTuning.restRim)
      GlassDropTuning.apply(mapOf("knobrim" to "on"))
      // Un valore che non si riconosce non cambia niente e non si prende.
      assertEquals(null, GlassDropTuning.apply(mapOf("knobrim" to "forse")))
      assertTrue(GlassDropTuning.restRim)
      // Le manopole senza `knobrim` (o nessuna) tornano di serie.
      assertEquals(null, GlassDropTuning.apply(mapOf("edge" to "ios26")))
      assertEquals(false, GlassDropTuning.restRim)
      GlassDropTuning.apply(mapOf("knobrim" to "on"))
      assertEquals(null, GlassDropTuning.apply(null))
      assertEquals(false, GlassDropTuning.restRim)
      assertEquals(listOf("on", "off"), GlassDropTuning.Values)
    } finally {
      GlassDropTuning.apply(null)
    }
  }

  @Test
  fun theOpticsAreTheKeysWithTheRimFloored() {
    assertEquals(key.copy(edgeFloor = 1f, dispersion = false), GlassDropOptics)
    assertEquals(GlassRole.Interactive, GlassDropRole)
  }

  @Test
  fun theScenesAndTheTraceAreConsistentAndWrittenOnlyWhenAsked() {
    val scenes = DropScenes.all()
    assertEquals(18, scenes.size)
    for (scene in scenes) {
      @Suppress("UNCHECKED_CAST")
      val elements = scene.json["elements"] as List<Map<String, Any?>>
      assertTrue(scene.path, elements.any { it["kind"] == "material" })
      val dropLights = elements.filter { (it["name"] as String).let { n -> n.startsWith("pomello-luce") || n.startsWith("cursore-luce") } }
      if (scene.path.startsWith("pomello-")) {
        // A riposo il pomello è la faccia pulita: niente luce (il tasto accanto la tiene).
        assertTrue(scene.path, dropLights.isEmpty())
      } else {
        // Sotto il dito la luce della goccia sta sopra la faccia: due strati a parte, dopo il corpo, con l'alfa della
        // pressione dove non è piena.
        assertTrue(scene.path, dropLights.any { (it["name"] as String).endsWith("-luce-filo") })
        val half = scene.path.startsWith("goccia-a-meta-")
        for (light in dropLights) assertEquals(scene.path, if (half) 0.5 else null, light["alpha"])
      }
    }
    val traces = listOf(DropTrace.build(dark = false), DropTrace.build(dark = true))
    for (trace in traces) {
      assertTrue(trace.name, trace.frames.size > 40)
      // La pressione torna a zero: l'ultimo fotogramma è il pomello a riposo, faccia piena.
      @Suppress("UNCHECKED_CAST")
      val face = (trace.frames.last().elements.first { it["name"] == "pomello" }["content"] as List<Map<String, Any?>>)
        .first()["items"] as List<Map<String, Any?>>
      assertEquals(glassColor(Color.White), face.first()["color"])
    }
    GlassSceneOutput.requestedDir(System.getenv(DropScenes.Variable))?.let { GlassSceneOutput.write(it, scenes) }
    GlassTraceOutput.requestedDir(System.getenv(DropTrace.Variable))?.let { dir ->
      for (trace in traces) trace.write(GlassTraceOutput.fileFor(dir, trace))
    }
  }
}

/**
 * La goccia come la scrive il banco: il corpo e l'ombra della ricetta in un elemento `material`, la faccia come un
 * rettangolo del suo contenuto (ritagliato dalla sagoma, come `onDrawSurface` dal ritaglio del nodo), e la luce in
 * due elementi `shader` **dopo**, perché nel nodo la luce si disegna sopra la faccia (`onDrawLight`), mentre la luce
 * di un elemento `material` del banco starebbe sotto il suo contenuto.
 *
 * La pressione scala il `layerBlock` della goccia: nel nodo il corpo, la luce e l'ombra si fanno alla taglia del nodo
 * e il layer li ingrandisce tutti. Qui l'elemento ha la taglia ingrandita e la ricetta della taglia del nodo con ogni
 * lunghezza per la stessa scala ([scaled]). Resta più nitido del vero di quanto un layer ricampionato ammorbidisce, e
 * l'antialias della maschera (`aa`, in pixel) non cresce con la scala.
 */
internal object DropElements {

  /** Ogni lunghezza della ricetta per [s]: ciò che fa la scala del `layerBlock`. */
  fun scaled(recipe: GlassRecipe, s: Float): GlassRecipe = recipe.apply {
    blurDp *= s
    paddingDp *= s
    thicknessDp *= s
    refractionDp *= s
    bodyShadeDepthDp *= s
    rimWidthDp *= s
    reflexDepthDp *= s
    shadowContactRadiusDp *= s
    shadowAmbientRadiusDp *= s
    shadowOffsetXDp *= s
    shadowOffsetYDp *= s
  }

  /**
   * La goccia [name] col nodo in ([x], [y], [w], [h]) dp, alla pressione [p] e alla scala [s] del suo layer (attorno
   * al centro del nodo). Il primo elemento è il corpo con la faccia; poi la luce (somma e filo). Se [childOf] è dato,
   * il corpo va nei `children` di quell'elemento (coordinate relative) e la luce resta fuori, assoluta.
   */
  fun drop(
    name: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    p: Float,
    s: Float,
    dark: Boolean,
    face: Color,
    nested: Boolean = false,
    radius: Any = "capsule",
    parentX: Float = 0f,
    parentY: Float = 0f,
  ): Pair<LinkedHashMap<String, Any?>, List<LinkedHashMap<String, Any?>>> {
    val recipe = scaled(glassDropRecipe(w, h, dark, p, nested = nested), s)
    val sw = w * s
    val sh = h * s
    val sx = x - (sw - w) / 2f
    val sy = y - (sh - h) / 2f
    val faceAlpha = glassDropFaceAlpha(face, p)
    // Il layer ingrandisce anche l'angolo: un raggio scritto in dp cresce con la scala, una capsula resta capsula.
    val shown: Any = if (radius is Number) round3(radius.toFloat() * s) else radius
    val shape = linkedMapOf<String, Any?>(
      "name" to name, "x" to round3(sx - parentX), "y" to round3(sy - parentY), "w" to round3(sw), "h" to round3(sh),
      "radius" to shown,
      "items" to listOf(
        linkedMapOf(
          "kind" to "rect", "x" to 0, "y" to 0, "w" to round3(sw), "h" to round3(sh),
          "color" to glassColor(face.copy(alpha = faceAlpha)),
        ),
      ),
    )
    val note = "glassDropRecipe (F1B.3): nodo ${w}x$h dp, pressione ${round3(p)}, scala ${round3(s)}, " +
      "lato ${if (dark) "scuro" else "chiaro"}${if (nested) ", annidata" else ""}; faccia ${glassColor(face)} " +
      "all'alfa ${round3(faceAlpha)}; luce sopra la faccia, negli elementi $name-luce-*"
    val body = liquidElement(shape, recipe, theme = null, note = note)

    // La luce dei tasti viene con la lente (`glassDropLightAmount`): a riposo niente, in mezzo l'alfa della pressione.
    // Nel nodo ogni rettangolo della luce ha l'alfa per quella quantità (`GlassDropFadedLight`), con la sua miscela:
    // qui ogni elemento porta la stessa `alpha`, ed è la stessa cosa.
    val lightAmount = glassDropLightAmount(p, restRim = false)
    @Suppress("UNCHECKED_CAST")
    val layers = (body.remove("light") as? List<Map<String, Any?>> ?: emptyList())
      .takeIf { lightAmount > 0.002f } ?: emptyList()
    val lights = layers.map { layer ->
      val additive = layer["blend"] == "Plus"
      linkedMapOf<String, Any?>(
        "kind" to "shader",
        "name" to "$name-luce-${if (additive) "somma" else "filo"}",
        "source" to layer["source"],
        "x" to round3(sx), "y" to round3(sy), "w" to round3(sw), "h" to round3(sh),
        "radius" to shown,
        "clip" to true,
      ).also { el ->
        if (additive) el["blend"] = "Plus"
        if (lightAmount < 0.998f) el["alpha"] = round3(lightAmount)
        @Suppress("UNCHECKED_CAST")
        el["uniforms"] = LinkedHashMap(layer["uniforms"] as Map<String, Any?>)
        lightRadii(el, body)
      }
    }
    return body to lights
  }

  /**
   * Un elemento `shader` del banco non conosce `$radii` (lo conosce solo un `material`): dove il corpo li chiede così,
   * i raggi della luce si scrivono per esteso, quelli nominali della sagoma del suo riquadro (`radius`), come li
   * darebbe `$radii`. Dove il corpo li ha già per esteso (una forma che il banco disegna al raggio equivalente), la
   * luce prende i suoi.
   */
  @Suppress("UNCHECKED_CAST")
  fun lightRadii(el: MutableMap<String, Any?>, body: Map<String, Any?>) {
    val u = el["uniforms"] as MutableMap<String, Any?>
    val bodyRadii = ((body["body"] as Map<String, Any?>)["uniforms"] as Map<String, Any?>)["pieceRadii"] as List<Any?>
    if (bodyRadii.firstOrNull() != "\$radii") {
      u["pieceRadii"] = bodyRadii
      return
    }
    val w = (el.getValue("w") as Number).toFloat()
    val h = (el.getValue("h") as Number).toFloat()
    val r = when (val radius = el["radius"]) {
      is Number -> minOf(radius.toFloat(), minOf(w, h) / 2f)
      else -> minOf(w, h) / 2f
    }
    u["pieceRadii"] = listOf(List(4) { glassDp(r) })
  }
}

/**
 * Le scene del banco della goccia, nei due temi e su tre fondi (`piatta`, `testo`, `foto`): accanto a un **tasto** di
 * vetro da 44 dp (il metro della luce), il pomello dell'**interruttore** acceso sulla sua pista, quello del
 * **cursore** sopra la pagina (la pista è sottile), e una **goccia in una lastra** (la pillola del segmentato senza
 * etichette, figlia della lastra: campiona il suo materiale, ed è annidata). Tre pressioni:
 *  - `pomello-*`: a riposo, faccia piena;
 *  - `goccia-a-meta-*`: pressione 0,5, layer a metà della crescita;
 *  - `goccia-lente-*`: pressione piena, layer cresciuto (×1,5 i pomelli, ×78/56 la pillola): la lente limpida.
 *
 * La scala del layer qui è `1 + (crescita − 1) · p`: nel controllo vero la crescita ha le sue molle (più morbide della
 * pressione, con un superamento), e la coppia (p, scala) di un fotogramma vero è quella della traccia.
 */
internal object DropScenes {

  const val Variable = "GLASS_DROP_SCENE_DIR"

  /** L'accento di serie del banco per lato (come le scene della pillola della tab bar). */
  fun accent(dark: Boolean): String = if (dark) "#2ED8A0" else "#1F9E6E"

  private const val KnobW = 40f
  private const val KnobH = 24f
  private const val KnobGrow = 1.5f
  private const val PillGrow = 78f / 56f

  private fun background(kind: String, dark: Boolean): Map<String, Any?> = when (kind) {
    "piatta" -> linkedMapOf("kind" to "color", "color" to if (dark) "#0D0D0F" else "#F2F2F7")
    "testo" -> linkedMapOf(
      "kind" to "text",
      "lines" to listOf(
        "Muoversi con i mezzi in Toscana: i bus dove passano, gli orari",
        "della sera e le fermate che conviene conoscere prima di partire.",
        "Le linee extraurbane cambiano d'estate; il biglietto si compra",
        "anche a bordo, ma costa di più. Le corse notturne sono poche.",
        "Muoversi con i mezzi in Toscana: i bus dove passano, gli orari",
      ),
      "fontSizeDp" to 17, "lineHeightDp" to 22, "paddingDp" to 10,
      "color" to if (dark) "#E6E6EA" else "#1A1A1C",
      "background" to if (dark) "#0D0D0F" else "#F2F2F7",
    )
    "foto" -> linkedMapOf(
      "kind" to "synthetic", "scaleDp" to 46, "detail" to 1.0, "contrast" to 1.25,
      "palette" to listOf("#111114", "#F4D21C", "#F06AA8", "#1A1A22", "#35C2D6", "#F4D21C", "#E8651C", "#111114"),
    )
    else -> error(kind)
  }

  fun scene(prefix: String, p: Float, kind: String, dark: Boolean): GlassScene {
    val theme = if (dark) "dark" else "light"
    val side = if (dark) "scuro" else "chiaro"
    val knobScale = 1f + (KnobGrow - 1f) * p
    val pillScale = 1f + (PillGrow - 1f) * p
    val elements = ArrayList<Map<String, Any?>>()

    // Il tasto: il metro della luce.
    val keyRecipe = glassRecipe(
      GlassRole.Interactive, 44f, 44f, dark, optics = GlassDefaults.buttonOptics().sanitized(),
    )
    elements += liquidElement(
      linkedMapOf("name" to "tasto", "x" to 14, "y" to 33, "w" to 44, "h" to 44, "radius" to "capsule"),
      keyRecipe, null, "glassRecipe: ruolo interactive, ottica dei tasti, 44 x 44 dp, lato $side (il metro della luce)",
    )

    // L'interruttore acceso: la pista nella pagina, il pomello sul lato acceso.
    val (switchKnob, switchLight) = DropElements.drop(
      "pomello", x = 76f + 2f + 20f, y = 43f, w = KnobW, h = KnobH, p = p, s = knobScale, dark = dark,
      face = GlassDropFaces.Light,
    )
    elements += switchKnob
    elements += switchLight

    // Il cursore: la pista sottile, il pomello sopra la pagina.
    val (sliderKnob, sliderLight) = DropElements.drop(
      "cursore", x = 198f, y = 43f, w = KnobW, h = KnobH, p = p, s = knobScale, dark = dark, face = GlassDropFaces.Light,
    )
    elements += sliderKnob
    elements += sliderLight

    // La goccia in una lastra: la pillola del segmentato, figlia della lastra (vetro su vetro, annidata).
    val paneX = 284f
    val paneY = 39f
    val paneRecipe = glassRecipe(GlassRole.Content, 128f, 32f, dark, optics = GlassDefaults.optics(GlassRole.Content).sanitized())
    val pane = liquidElement(
      linkedMapOf("name" to "lastra", "x" to paneX, "y" to paneY, "w" to 128, "h" to 32, "radius" to 9),
      paneRecipe, null, "glassRecipe: ruolo content, 128 x 32 dp, lato $side (la pista del segmentato)",
    )
    val (pill, pillLight) = DropElements.drop(
      "pillola", x = paneX + 2f, y = paneY + 2f, w = 60f, h = 28f, p = p, s = pillScale, dark = dark,
      face = GlassDropFaces.forSurface(dark), nested = true, radius = 7, parentX = paneX, parentY = paneY,
    )
    pane["children"] = listOf(pill)
    elements += pane
    elements += pillLight

    val page = listOf(
      // La pista dell'interruttore, accesa.
      linkedMapOf("kind" to "rect", "x" to 76, "y" to 41, "w" to 64, "h" to 28, "radius" to 14, "color" to accent(dark)),
      // La pista del cursore: il fondo e il riempimento fino al pomello.
      linkedMapOf("kind" to "rect", "x" to 160, "y" to 52, "w" to 110, "h" to 6, "radius" to 3, "color" to "#7878803D"),
      linkedMapOf("kind" to "rect", "x" to 160, "y" to 52, "w" to 58, "h" to 6, "radius" to 3, "color" to accent(dark)),
    )
    val json = linkedMapOf<String, Any?>(
      "_" to "Scritta dalla ricetta (GlassDropProfileTest, F1B.3): la goccia sul materiale nuovo a pressione ${round3(p)}, " +
        "accanto a un tasto; fondo $kind, lato $side. Non si modifica a mano: si rigenera.",
      "width" to 430, "height" to 110, "density" to 3.0, "theme" to theme, "palette" to "engine", "corner" to "engine",
      "background" to background(kind, dark),
      "page" to page,
      "elements" to elements,
      "measure" to listOf(
        linkedMapOf("kind" to "body", "element" to "pomello", "label" to "pomello, al centro",
          "at" to listOf(0.35, 0.3, 0.3, 0.4), "rows" to listOf("corpo", "fondo", "corpo-fondo")),
        linkedMapOf("kind" to "body", "element" to "tasto", "label" to "tasto, al centro",
          "at" to listOf(0.35, 0.35, 0.3, 0.3), "rows" to listOf("corpo", "fondo", "corpo-fondo")),
      ),
    )
    return GlassScene("$prefix-$kind-$side.json", json)
  }

  fun all(): List<GlassScene> = listOf("pomello" to 0f, "goccia-a-meta" to 0.5f, "goccia-lente" to 1f).flatMap { (prefix, p) ->
    listOf("piatta", "testo", "foto").flatMap { kind -> listOf(false, true).map { dark -> scene(prefix, p, kind, dark) } }
  }
}

/**
 * La traccia della goccia: un **tocco sull'interruttore** che lo accende (`GlassDragAnimation.animateToValue(1)`), a
 * 120 Hz come il tablet, con le molle dell'engine (`FluidMotion`: valore e pressione (1, 1000), scala X (0,6, 250),
 * scala Y (0,7, 250)) e la regola del rilascio (`release()`: la goccia si lascia quando il valore è entro il 2,5%
 * della corsa dal traguardo e la pressione è arrivata al 96%). Lo squash è zero: un tocco non passa dal tracciatore
 * della velocità.
 *
 * **Approssimato, dichiarato**: la pista nella pagina è del colore acceso dall'inizio (nell'engine il colore va dal
 * grigio all'accento col valore; nella traccia la pagina non cambia); le lunghezze come in [DropElements].
 */
internal object DropTrace {

  const val Variable = "GLASS_DROP_TRACE_DIR"

  private const val Fps = 120
  private const val TrackX = 48f
  private const val TrackY = 18f
  private const val Inset = 2f
  private const val Travel = 20f

  fun build(dark: Boolean): GlassTrace {
    val side = if (dark) "scuro" else "chiaro"
    val valueSpec = FluidMotion.dropValue(0.001f)
    val pressIn = TodaySpring(FluidMotion.DropPress, 0f, 1f)
    val value = TodaySpring(valueSpec, 0f, 1f)
    val growX = TodaySpring(FluidMotion.DropScaleX, 1f, 1.5f)
    val growY = TodaySpring(FluidMotion.DropScaleY, 1f, 1.5f)
    // Il fotogramma del rilascio: il primo in cui il valore è arrivato e la pressione anche.
    var release = 0
    while (release < Fps) {
      val n = glassTraceTimeNanos(release, Fps)
      if (abs(value.value(n) - 1f) < 0.025f && pressIn.value(n) >= 0.96f) break
      release++
    }
    val atRelease = glassTraceTimeNanos(release, Fps)
    val pressOut = TodaySpring(FluidMotion.DropPress, pressIn.value(atRelease), 0f, pressIn.velocity(atRelease))
    val shrinkX = TodaySpring(FluidMotion.DropScaleX, growX.value(atRelease), 1f, growX.velocity(atRelease))
    val shrinkY = TodaySpring(FluidMotion.DropScaleY, growY.value(atRelease), 1f, growY.velocity(atRelease))

    val frames = ArrayList<GlassTraceFrame>()
    var tail = 0
    for (k in -6 until 240) {
      val n = glassTraceTimeNanos(k, Fps)
      val since = glassTraceTimeNanos(k - release, Fps)
      val released = k >= release
      val p = when {
        k < 0 -> 0f
        released -> pressOut.value(since)
        else -> pressIn.value(n)
      }
      val v = if (k < 0) 0f else value.value(n)
      val sx = when {
        k < 0 -> 1f
        released -> shrinkX.value(since)
        else -> growX.value(n)
      }
      val sy = when {
        k < 0 -> 1f
        released -> shrinkY.value(since)
        else -> growY.value(n)
      }
      // La scala del layer è una per asse; la ricetta ne vuole una sola per le lunghezze: la media, come il
      // tasto premuto delle tracce di F1A. La sagoma invece è quella vera, per asse.
      val s = (sx + sy) / 2f
      val nodeX = TrackX + Inset + Travel * v
      val nodeY = TrackY + Inset
      val (knob, light) = DropElements.drop(
        "pomello", x = nodeX, y = nodeY, w = 40f, h = 24f, p = p, s = s, dark = dark, face = GlassDropFaces.Light,
      )
      // La sagoma per asse: ricentrata sul nodo con le due scale.
      fun reshape(el: MutableMap<String, Any?>) {
        el["x"] = round3(nodeX - (40f * sx - 40f) / 2f)
        el["y"] = round3(nodeY - (24f * sy - 24f) / 2f)
        el["w"] = round3(40f * sx)
        el["h"] = round3(24f * sy)
      }
      reshape(knob)
      // La faccia: nelle tracce il contenuto è una lista di strati (TRACE.md), ritagliati dalla sagoma.
      @Suppress("UNCHECKED_CAST")
      val faceItems = knob.remove("items") as List<MutableMap<String, Any?>>
      faceItems.first().also { it["w"] = round3(40f * sx); it["h"] = round3(24f * sy) }
      knob["content"] = listOf(linkedMapOf("name" to "faccia", "items" to faceItems))
      knob["contentClip"] = "shape"
      light.forEach {
        reshape(it)
        DropElements.lightRadii(it, knob)
      }
      completeBody(knob)
      frames += GlassTraceFrame(glassTraceTimeMs(k, Fps), listOf(knob) + light)
      val settled = released && pressOut.finished(since) && shrinkX.finished(since) && shrinkY.finished(since) &&
        value.finished(n)
      if (settled) tail++
      if (tail > 12) break
    }
    return GlassTrace(
      name = "goccia-interruttore-$side",
      phase = "f1b",
      density = 3f,
      theme = if (dark) "dark" else "light",
      widthDp = 136f,
      heightDp = 60f,
      frames = frames,
      fps = Fps,
      head = linkedMapOf(
        "_" to "La goccia dell'interruttore sul materiale nuovo (F1B.3, GlassDropProfileTest): un tocco che lo accende, " +
          "pressione -> lente -> rilascio, con le molle dell'engine. Pista ferma del colore acceso (vedi DropTrace).",
        "palette" to "engine",
        "background" to linkedMapOf("kind" to "color", "color" to if (dark) "#0D0D0F" else "#F2F2F7"),
        "page" to listOf(
          linkedMapOf("kind" to "rect", "x" to TrackX, "y" to TrackY, "w" to 64, "h" to 28, "radius" to 14,
            "color" to DropScenes.accent(dark)),
        ),
        "events" to listOf(
          linkedMapOf("t" to round3(glassTraceTimeMs(release, Fps).toFloat()), "kind" to "mark", "label" to "rilascio"),
        ),
        "probes" to listOf(
          linkedMapOf("name" to "larghezza", "kind" to "value", "path" to "pomello.w", "label" to "il pomello (dp)", "unit" to "dp"),
          linkedMapOf("name" to "centro", "kind" to "luma", "rect" to listOf(TrackX + 26, TrackY + 10, 12, 8),
            "label" to "il centro della pista"),
        ),
      ),
    )
  }

  /** Gli uniform della luce che il corpo dichiara anche lui (la luce in corpo, spenta), come in `LiquidTasto`. */
  private val BodyLightKeys = listOf(
    "rimWidth", "rimDark", "rimFeather", "rimLift", "reflexDir", "reflexWidths", "reflexStrength", "reflexDepth",
    "reflexFloor", "reflexHalo", "glowCenter", "glowRadius", "glow", "glowFill", "lightColor",
  )

  /**
   * Il validatore del movimento chiede ogni uniform dichiarato: il corpo porta anche la luce in corpo (spenta,
   * `lightInBody` 0) e l'ombra in corpo (alfa zero), che a riposo non si disegnano. I valori della luce vengono dalla
   * ricetta (gli stessi della luce sopra), quelli della luce in corpo non scritti dalla luce restano a zero.
   */
  @Suppress("UNCHECKED_CAST")
  private fun completeBody(element: Map<String, Any?>) {
    val body = (element["body"] as? Map<String, Any?>)?.get("uniforms") as? MutableMap<String, Any?> ?: return
    body.putIfAbsent("rimWidth", glassDp(0f))
    body.putIfAbsent("rimDark", "#00000000")
    body.putIfAbsent("rimFeather", 0)
    body.putIfAbsent("rimLift", 0)
    body.putIfAbsent("reflexDir", listOf(0, -1))
    body.putIfAbsent("reflexWidths", listOf(0, 0))
    body.putIfAbsent("reflexStrength", 0)
    body.putIfAbsent("reflexDepth", glassDp(0f))
    body.putIfAbsent("reflexFloor", 0)
    body.putIfAbsent("reflexHalo", 0)
    body.putIfAbsent("glowCenter", "\$center")
    body.putIfAbsent("glowRadius", glassDp(0f))
    body.putIfAbsent("glow", 0)
    body.putIfAbsent("glowFill", 0)
    body.putIfAbsent("lightColor", "#FFFFFFFF")
    body.putIfAbsent("shadowOffset", listOf(glassDp(0f), glassDp(0f)))
    body.putIfAbsent("shadowRadii", listOf(glassDp(0f), glassDp(0f)))
    body.putIfAbsent("shadowAlphas", listOf(0, 0))
    body.putIfAbsent("shadowColor", "#00000000")
    // Il ritaglio emulato è di chi non ha il ritaglio del nodo (Fluid-physics): una lastra ferma lo lascia a zero.
    body.putIfAbsent("nodeClip", listOf(0, 0))
    check(BodyLightKeys.all { it in body })
  }
}
