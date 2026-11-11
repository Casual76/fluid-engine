package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.fitToTexture
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidSurface
import dev.antigravity.fluidengine.ui.glass.field.GlassShadowPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassShadowProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassShadowSpec
import dev.antigravity.fluidengine.ui.glass.field.GlassSmoothedClipScale
import dev.antigravity.fluidengine.ui.glass.field.glassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * La **parità fra l'innesto e il banco** (F1A.6; piano 4.4): ciò che `glassSurface` passa agli shader per una
 * lastra è ciò che `GlassSceneExportTest` scrive nelle scene da cui nascono `golden/liquid`, le misure contro
 * Apple e le tavole di gusto.
 *
 * Non confronta la ricetta con se stessa. Da una parte c'è una scena come la legge il banco: un JSON con
 * lunghezze in dp e valori `$...` che `render.py` risolve. Dall'altra c'è [GlassLiquidSurface], lo stato che
 * `glassSurface` ricorda, interrogato con la taglia e lo scope che il nodo gli darebbe. In mezzo ci sono tutte
 * le cose che una scena non decide e l'innesto sì: quale spazio (la cattura rimpicciolita o lo schermo), il
 * margine troncato, i raggi letti dalla `Shape`, il ritaglio, il lato del tema.
 *
 * ### La conversione di unità, dichiarata
 * È quella di `render_material` (`tools/glassbench/render.py`), e niente altro:
 *  - `"Ndp"` → `N` per la densità dello spazio: `densità x scala della cattura` per il corpo, `densità` per la
 *    luce;
 *  - `"$center"`, `"$halfSize"` → mezza taglia della lastra in quello spazio; `"$radii"` → i raggi nominali,
 *    limitati a mezzo lato corto; `"$offset"` → meno il margine (zero per la luce);
 *  - il margine → `int(paddingDp x densità x scala)`: troncato;
 *  - i colori → `#RRGGBBAA`, cioè gli 8 bit per canale che anche `setColorUniform` passa;
 *  - `aa` e `grain` non sono lunghezze del materiale: restano il numero scritto, in entrambi gli spazi.
 *
 * ### Cosa la parità non copre
 *  - **la scala di una lastra con `layerBlock`** (un tasto che cresce sotto il dito): l'engine la cattura a
 *    risoluzione piena, come ha sempre fatto, mentre la scena del tasto usa la frazione della ricetta. Gli
 *    uniform in dp sono gli stessi; cambia quanti pixel li ricevono. Qui si confronta alla scala della scena;
 *  - `flex`, `flexCenter`, `magnify`, `lightInBody`, `pieceBlend`: non sono della ricetta né della forma, li
 *    scrive `glassBody` come costanti di una lastra ferma (identità, zero) e la scena li scrive uguali;
 *  - che i pixel coincidano: quello lo dicono `compare --golden liquid` (il banco) e il dispositivo.
 */
class GlassDrawParityTest {

  private val tolerance = 2e-3f

  private fun near(where: String, expected: Float, actual: Float) {
    val slack = tolerance + abs(expected) * 2e-5f
    assertTrue("$where: la scena dice $expected, l'innesto passa $actual", abs(expected - actual) <= slack)
  }

  private fun near(where: String, expected: List<Float>, actual: List<Float>) {
    assertEquals("$where: quanti numeri", expected.size, actual.size)
    for (i in expected.indices) near("$where[$i]", expected[i], actual[i])
  }

  private fun number(value: Any?): Float = (value as Number).toFloat()

  /** Una lunghezza di scena (`"12dp"`, o un numero già in pixel) nello spazio di densità [density]. */
  private fun px(value: Any?, density: Float): Float = when (value) {
    is String -> {
      require(value.endsWith("dp")) { "lunghezza non leggibile: $value" }
      value.removeSuffix("dp").toFloat() * density
    }
    else -> number(value)
  }

  @Suppress("UNCHECKED_CAST")
  private fun map(value: Any?): Map<String, Any?> = value as Map<String, Any?>

  @Suppress("UNCHECKED_CAST")
  private fun list(value: Any?): List<Any?> = value as List<Any?>

  private fun shapeOf(radius: Any?): Shape = when (radius) {
    null, "capsule" -> FluidCapsuleShape
    is Number -> ContinuousCornerShape(radius.toFloat().dp)
    else -> error("raggio di scena non previsto: $radius")
  }

  /** I raggi nominali di una lastra, come li dà `corner_radii_px` del banco, in pixel a densità [density]. */
  private fun nominalRadii(radius: Any?, widthPx: Float, heightPx: Float, density: Float): List<Float> {
    val cap = min(widthPx, heightPx) / 2f
    return when (radius) {
      null, "capsule" -> List(4) { cap }
      is Number -> List(4) { min(radius.toFloat() * density, cap) }
      else -> error("raggio di scena non previsto: $radius")
    }
  }

  /**
   * La geometria di uno strato di scena (i campi di `field()` in `GlassScenes.kt`) contro il piano che
   * l'innesto manda allo shader. [space] è la densità dello spazio dello strato, [scale] la frazione di
   * risoluzione (1 per la luce), [padding] il margine in pixel di quello spazio.
   */
  private fun compareField(
    where: String,
    uniforms: Map<String, Any?>,
    el: Map<String, Any?>,
    plan: GlassFieldPlan,
    offsetX: Float,
    offsetY: Float,
    density: Float,
    scale: Float,
    padding: Float,
  ) {
    val w = number(el["w"]) * density * scale
    val h = number(el["h"]) * density * scale
    assertEquals("$where.offset", "\$offset", uniforms["offset"])
    near("$where.offset", listOf(-padding, -padding), listOf(offsetX, offsetY))
    assertEquals("$where.pieceCount", 1, (uniforms["pieceCount"] as Number).toInt())
    assertEquals("$where.pieceCount", 1, plan.count)
    assertEquals("$where.pieceRect", listOf(listOf("\$center", "\$halfSize")), uniforms["pieceRect"])
    near("$where.pieceRect", listOf(w / 2f, h / 2f, w / 2f, h / 2f), plan.rects.toList())
    val radii = list(uniforms["pieceRadii"]).single()
    val expectedRadii = if (radii == "\$radii") {
      nominalRadii(el["radius"], number(el["w"]) * density, number(el["h"]) * density, density).map { it * scale }
    } else {
      list(radii).map { px(it, density * scale) }
    }
    near("$where.pieceRadii", expectedRadii, plan.radii.toList())
    near("$where.pieceSmooth", list(uniforms["pieceSmooth"]).map { number(it) }, plan.smooth.toList())
    near("$where.pieceBlend", list(uniforms["pieceBlend"]).map { number(it) }, plan.blend.toList())
    // Le costanti di una lastra ferma, che `glassBody` e `drawGlassLight` scrivono uguali.
    assertEquals("$where.flex", listOf(1, 0, 0, 1), list(uniforms["flex"]).map { (it as Number).toInt() })
    assertEquals("$where.flexCenter", listOf(0, 0), list(uniforms["flexCenter"]).map { (it as Number).toInt() })
  }

  private val fieldKeys = setOf("offset", "flex", "flexCenter", "pieceCount", "pieceRect", "pieceRadii", "pieceBlend", "pieceSmooth")

  /** Tutti gli uniform del corpo di una scena contro [inputs]. Uno uniform che qui non ha un confronto fa fallire. */
  private fun compareBody(where: String, el: Map<String, Any?>, inputs: GlassDrawInputs, density: Float, scale: Float) {
    val capture = map(el["capture"])
    val space = density * scale
    near("$where.saturation", number(capture["saturation"]), inputs.saturation)
    near("$where.blur", number(capture["blurDp"]) * space, inputs.blurPx)
    near("$where.w", number(capture["w"]), inputs.mix)
    val padding = floor(number(capture["paddingDp"]) * space)
    assertEquals("$where.padding", padding, inputs.paddingPx, 0f)

    val uniforms = map(map(el["body"])["uniforms"])
    compareField(where, uniforms, el, inputs.body, inputs.bodyOffsetX, inputs.bodyOffsetY, density, scale, padding)
    for ((name, value) in uniforms) {
      val at = "$where.$name"
      when (name) {
        in fieldKeys -> Unit
        "thickness" -> near(at, px(value, space), inputs.thickness)
        "refraction" -> near(at, px(value, space), inputs.refraction)
        "dome" -> near(at, number(value), inputs.dome)
        "dispersion" -> near(at, number(value), inputs.dispersion)
        "materialize" -> near(at, number(value), inputs.materialize)
        "veilColor" -> assertEquals(at, value, glassColor(inputs.veilColor))
        "veilAdapt" -> near(at, number(value), inputs.veilAdapt)
        "tintColor" -> assertEquals(at, value, glassColor(inputs.tintColor))
        "tintMode" -> near(at, number(value), inputs.tintMode)
        "tintFollow" -> near(at, number(value), inputs.tintFollow)
        "bodyShade" -> assertEquals(at, value, glassColor(inputs.bodyShade))
        "bodyShadeDepth" -> near(at, px(value, space), inputs.bodyShadeDepth)
        "bodyShadeLean" -> near(at, list(value).map { number(it) }, listOf(inputs.bodyShadeLeanX, inputs.bodyShadeLeanY))
        "dim" -> near(at, number(value), inputs.dim)
        "grain" -> near(at, number(value), inputs.grain)
        "aa" -> near(at, number(value), inputs.aa)
        // Le due costanti che `glassBody` scrive per una lastra ferma.
        "magnify", "lightInBody" -> assertEquals(at, 0, (value as Number).toInt())
        else -> fail("$at: uno uniform del corpo che la prova di parità non confronta")
      }
    }
  }

  /** Gli strati di luce di una scena contro [inputs]: quanti, in che ordine, con che modo di comporre, e ogni uniform. */
  private fun compareLight(where: String, el: Map<String, Any?>, inputs: GlassDrawInputs, density: Float) {
    val layers = (el["light"] as? List<*>)?.map { map(it) } ?: emptyList()
    val expected = ArrayList<Pair<Int, String?>>()
    if (inputs.drawsGlowing) expected += 0 to "Plus"
    if (inputs.drawsRim) expected += 1 to null
    assertEquals(
      "$where: i disegni della luce (parte, modo di comporre)",
      layers.map { (map(it["uniforms"])["lightPart"] as Number).toInt() to (it["blend"] as String?) },
      expected,
    )
    for (layer in layers) {
      val uniforms = map(layer["uniforms"])
      val part = (uniforms["lightPart"] as Number).toInt()
      val here = "$where.luce$part"
      compareField(here, uniforms, el, inputs.light, 0f, 0f, density, 1f, 0f)
      for ((name, value) in uniforms) {
        val at = "$here.$name"
        when (name) {
          in fieldKeys, "lightPart" -> Unit
          "rimWidth" -> near(at, px(value, density), inputs.rimWidth)
          "rimDark" -> assertEquals(at, value, glassColor(inputs.rimDark))
          "rimFeather" -> near(at, number(value), inputs.rimFeather)
          "rimLift" -> near(at, number(value), inputs.rimLift)
          "reflexDir" -> near(at, list(value).map { number(it) }, listOf(inputs.reflexDirX, inputs.reflexDirY))
          "reflexWidths" ->
            near(at, list(value).map { number(it) }, listOf(inputs.reflexHalfWidth, inputs.reflexOppositeHalfWidth))
          "reflexStrength" -> near(at, number(value), inputs.reflexStrength)
          "reflexDepth" -> near(at, px(value, density), inputs.reflexDepth)
          "reflexFloor" -> near(at, number(value), inputs.reflexFloor)
          "reflexHalo" -> near(at, number(value), inputs.reflexHalo)
          "glowCenter" -> {
            assertEquals(at, "\$center", value)
            near(at, listOf(number(el["w"]) * density / 2f, number(el["h"]) * density / 2f), listOf(inputs.glowCenterX, inputs.glowCenterY))
          }
          "glowRadius" -> near(at, px(value, density), inputs.glowRadius)
          "glow" -> near(at, number(value), inputs.glow)
          "glowFill" -> near(at, number(value), inputs.glowFill)
          "lightColor" -> assertEquals(at, value, glassColor(inputs.lightColor))
          "materialize" -> near(at, number(value), inputs.lightMaterialize)
          "aa" -> near(at, number(value), inputs.lightAa)
          else -> fail("$at: uno uniform della luce che la prova di parità non confronta")
        }
      }
    }
  }

  /** L'ombra come la risolve il nodo: prima la sagoma, poi i numeri. */
  private fun shadowOf(surface: GlassLiquidSurface, size: Size, density: Float, pieces: GlassShadowPieces): GlassShadowSpec? {
    var spec: GlassShadowSpec? = null
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(android.graphics.Canvas()), size) {
      with(surface.shadowOutline) { resolve(pieces) }
      spec = surface.shadow()
    }
    return spec
  }

  /** Le maschere dell'ombra di una scena contro i numeri che l'innesto dà al nodo, e la sagoma contro quella della luce. */
  private fun compareShadow(where: String, el: Map<String, Any?>, surface: GlassLiquidSurface, size: Size, density: Float) {
    val pieces = GlassShadowPieces()
    val spec = shadowOf(surface, size, density, pieces)
    val layers = (el["shadow"] as? List<*>)?.map { map(it) }
    if (layers == null) {
      assertNull("$where: la scena non ha ombra", spec)
      return
    }
    assertNotNull("$where: la scena ha un'ombra", spec)
    spec!!
    // Lo scrittore delle scene mette una maschera per componente accesa: contatto, poi ambiente.
    val share = GlassShadowProfile.contactShare(spec.contactRadius.value, spec.ambientRadius.value)
    val weight = spec.color.alpha
    val expected = ArrayList<List<Float>>()
    if (spec.contactAlpha > 0f) {
      expected += listOf(spec.contactRadius.value, spec.offset.x.value * share, spec.offset.y.value * share, spec.contactAlpha * weight)
    }
    if (spec.ambientAlpha > 0f) {
      expected += listOf(spec.ambientRadius.value, spec.offset.x.value, spec.offset.y.value, spec.ambientAlpha * weight)
    }
    assertEquals("$where: quante maschere", layers.size, expected.size)
    for ((layer, numbers) in layers.zip(expected)) {
      val offset = list(layer["offsetDp"]).map { number(it) }
      near("$where.ombra", listOf(number(layer["radiusDp"]), offset[0], offset[1], number(layer["alpha"])), numbers)
      assertEquals("$where.ombra.colore", layer["color"], glassColor(spec.color).substring(0, 7))
    }
    // La sagoma dell'ombra è quella che riceve la luce: gli stessi pezzi, in pixel del nodo.
    val light = surface.drawInputs(size).light
    assertEquals("$where.ombra.pezzi", 1, pieces.count)
    near("$where.ombra.rect", light.rects.toList(), pieces.rects.take(4))
    near("$where.ombra.raggi", light.radii.toList(), pieces.radii.take(4))
    near("$where.ombra.raccordo", light.smooth[0], pieces.smooth[0])
  }

  /** Il ritaglio hardware di una scena (`clip`, `clipRadiusScale`) contro la forma che l'innesto dà al nodo. */
  private fun compareClip(where: String, el: Map<String, Any?>, surface: GlassLiquidSurface, size: Size, density: Float) {
    assertEquals("$where.clip", true, el["clip"])
    val scale = (el["clipRadiusScale"] as? Number)?.toFloat() ?: 1f
    near("$where.clipRadiusScale", scale, surface.drawInputs(size).clipScale)
    if (scale != 1f) assertEquals(SceneSmoothedClipScale.toFloat(), GlassSmoothedClipScale, 0f)
    val outline = surface.clipShapeBlock().createOutline(size, LayoutDirection.Ltr, Density(density))
    val body = surface.drawInputs(size, scale = 1f).body
    // Il banco ritaglia al rettangolo arrotondato dei raggi del campo per quella frazione.
    val rect = (outline as Outline.Rounded).roundRect
    near(
      "$where.ritaglio",
      body.radii.map { it * scale },
      listOf(rect.topLeftCornerRadius.x, rect.topRightCornerRadius.x, rect.bottomRightCornerRadius.x, rect.bottomLeftCornerRadius.x),
    )
  }

  /** Una lastra del campione, su un lato, a una densità: la scena contro l'innesto. */
  private fun compareSample(scene: GlassScene, shape: Map<String, Any?>, dark: Boolean, density: Float, ask: SceneAsk) {
    val name = shape.getValue("name") as String
    val where = "${scene.path} @$density: $name"
    @Suppress("UNCHECKED_CAST")
    val el = (scene.json["elements"] as List<Map<String, Any?>>).single { it["name"] == name }
    val bench = sceneSurface(shape["role"] as String)
    val size = Size(number(shape["w"]) * density, number(shape["h"]) * density)

    // Ciò che `glassSurface` ricorda per questa lastra: ruolo, ottica passata da `sanitized()`, forma, lato.
    val surface = GlassLiquidSurface(
      role = bench.role,
      optics = bench.optics.sanitized(),
      shape = shapeOf(shape["radius"]),
      darkSide = dark,
      screenDensity = density,
      intensity = { 1f },
      opticalDepth = { 1f },
      pressed = { 0f },
      quality = { 1f },
      film = { if (ask.todayFilm) todayFilm(bench, dark) else Color.Unspecified },
      tint = { Color.Unspecified },
      touch = { null },
    )

    // La frazione di risoluzione: quella che l'innesto decide in composizione è quella scritta nella scena.
    val sceneScale = number(map(el["capture"])["scale"])
    assertEquals("$where: la frazione di risoluzione", sceneScale, surface.captureScale, 0f)
    val scale = fitToTexture(sceneScale, size.width, size.height)
    assertEquals(where, scale, surface.effectiveCaptureScale(size), 0f)

    compareBody(where, el, surface.drawInputs(size), density, scale)
    compareLight(where, el, surface.drawInputs(size), density)
    compareShadow(where, el, surface, size, density)
    compareClip(where, el, surface, size, density)
  }

  @Test
  fun everySlabOfTheSampleReceivesTheUniformsItsSceneWrites() {
    var compared = 0
    for (dark in listOf(false, true)) {
      val ask = SceneAsk()
      val scene = GlassScenes.sample("serie", dark, ask)
      for (shape in GlassScenes.SampleShapes) {
        for (density in SceneDensities) {
          compareSample(scene, shape, dark, density, ask)
          compared++
        }
      }
    }
    // Tasto, capsula, tab bar, menu, pannello; chiaro e scuro; telefono e tablet.
    assertEquals(20, compared)
    assertEquals(listOf("tasto", "capsula", "barra", "menu", "pannello"), GlassScenes.SampleShapes.map { it["name"] })
  }

  /** Una tinta scritta a mano (`GlassTintMode.Fixed`) è la pellicola dipinta com'è: la variante «velo di oggi» delle tavole. */
  @Test
  fun aFixedFilmReachesTheBodyAsTheScenesWriteIt() {
    val ask = SceneAsk(todayFilm = true, label = "pellicola di oggi")
    for (dark in listOf(false, true)) {
      val scene = GlassScenes.sample("velo-oggi", dark, ask)
      for (shape in GlassScenes.SampleShapes) compareSample(scene, shape, dark, 3f, ask)
    }
  }

  /** Lo stato della superficie e la funzione pura danno gli stessi ingressi: l'adattatore non aggiunge niente. */
  @Test
  fun theSurfaceAdapterAddsNothingToTheDrawInputs() {
    for (shape in GlassScenes.SampleShapes) {
      val bench = sceneSurface(shape["role"] as String)
      val density = 3f
      val w = number(shape["w"])
      val h = number(shape["h"])
      val size = Size(w * density, h * density)
      val form = shapeOf(shape["radius"])
      val surface = GlassLiquidSurface(
        role = bench.role, optics = bench.optics.sanitized(), shape = form, darkSide = false, screenDensity = density,
        intensity = { 1f }, opticalDepth = { 1f }, pressed = { 0f }, quality = { 1f },
        film = { Color.Unspecified }, tint = { Color.Unspecified }, touch = { null },
      )
      val scale = surface.effectiveCaptureScale(size)
      val adapted = surface.drawInputs(size)
      val pure = glassDrawInputs(
        recipe = glassRecipe(bench.role, w, h, darkSide = false, optics = bench.optics.sanitized()),
        shape = form,
        size = size,
        density = density,
        captureScale = scale,
      )
      val where = shape["name"] as String
      near("$where.thickness", pure.thickness, adapted.thickness)
      near("$where.refraction", pure.refraction, adapted.refraction)
      near("$where.blur", pure.blurPx, adapted.blurPx)
      assertEquals("$where.padding", pure.paddingPx, adapted.paddingPx, 0f)
      near("$where.body.rects", pure.body.rects.toList(), adapted.body.rects.toList())
      near("$where.body.radii", pure.body.radii.toList(), adapted.body.radii.toList())
      near("$where.body.smooth", pure.body.smooth.toList(), adapted.body.smooth.toList())
      near("$where.light.radii", pure.light.radii.toList(), adapted.light.radii.toList())
      near("$where.rimWidth", pure.rimWidth, adapted.rimWidth)
      near("$where.reflexDepth", pure.reflexDepth, adapted.reflexDepth)
      assertEquals("$where.clipScale", pure.clipScale, adapted.clipScale, 0f)
      assertEquals("$where.veil", pure.veilColor, adapted.veilColor)
      assertEquals("$where.rim", pure.rimDark, adapted.rimDark)
      assertEquals("$where.drawsGlowing", pure.drawsGlowing, adapted.drawsGlowing)
      assertEquals("$where.drawsRim", pure.drawsRim, adapted.drawsRim)
      // E la taglia in dp che la ricetta riceve non dipende dalla risoluzione della cattura: stessi numeri a
      // scala piena, moltiplicati.
      val full = surface.drawInputs(size, scale = 1f).thickness
      near("$where: lo spessore a mezza cattura", full * scale, surface.drawInputs(size, scale = scale).thickness)
    }
  }
}
