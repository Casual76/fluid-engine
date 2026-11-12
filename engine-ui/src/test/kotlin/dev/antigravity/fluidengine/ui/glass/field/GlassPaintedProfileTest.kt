package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassScene
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassSceneOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.glassColor
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il vetro dipinto (F1B.5, `GlassPaintedProfile.kt`): che sia **derivato** dalla ricetta di serie e non ricopiato
 * (cambiato un pezzo del profilo, il dipinto cambia con lui), che il corpo sia la formula dello shader, che i passi
 * del bordo abbiano i pesi del vetro vero dove il vetro vero li ha, che lo scrim sia uno.
 *
 * In fondo, **solo se** c'è la variabile d'ambiente `GLASS_PAINTED_SCENE_DIR` (di solito `tools/glassbench/scenes/f1b`),
 * scrive le scene del banco `dipinto-*`: il vetro vero (scritto da `glassRecipe`) e, nella chiave `_dipinto`, i passi
 * del dipinto di oggi e di quello nuovo, che `dipinto-rendi.py` disegna accanto.
 */
class GlassPaintedProfileTest {

  private val base = GlassLiquidProfile.Shipped
  private val density = 3f

  /** Un colore sRGB di Compose tiene 8 bit per canale: mezzo livello per ogni passaggio, e un po' di margine. */
  private val eightBit = 1.5f / 255f

  private fun plan(
    profile: GlassLiquidProfile,
    dark: Boolean,
    wDp: Float = 280f,
    hDp: Float = 136f,
    radiusDp: Float = 18f,
    seen: Color = glassPaintedSeen(GlassPaintedPage.of(dark), dark),
    nested: Boolean = false,
  ): Pair<GlassRecipe, GlassPaintedPlan> {
    val recipe = glassPaintedRecipe(wDp, hDp, dark, nested = nested, largeVeil = 1f, profile = profile)
    val r = radiusDp * density
    return recipe to glassPaintedPlan(recipe, seen, wDp * density, hDp * density, density, floatArrayOf(r, r, r, r))
  }

  /** Il filo: l'ultimo passo che copre al tratto, il più vicino al contorno. */
  private fun GlassPaintedPlan.rim(): GlassPaintedLayer =
    layers.last { it.blend == GlassPaintedBlend.Over && it.stroke > 0f }

  /** La riga chiara: il primo passo che somma. */
  private fun GlassPaintedPlan.line(): GlassPaintedLayer = layers.first { it.blend == GlassPaintedBlend.Plus }

  private fun GlassPaintedPlan.halo(): List<GlassPaintedLayer> = layers.filter { it.blend == GlassPaintedBlend.Plus }.drop(1)

  private fun GlassPaintedPlan.shade(axis: GlassPaintedAxis): GlassPaintedLayer =
    layers.first { it.blend == GlassPaintedBlend.Over && it.stroke == 0f && it.axis == axis }

  private fun close(expected: Float, actual: Float, tolerance: Float = 1e-4f, what: String = "") =
    assertTrue("$what: atteso $expected, avuto $actual", abs(expected - actual) <= tolerance)

  // --- Il corpo -------------------------------------------------------------------------------------------------

  @Test
  fun theBodyIsTheShaderFormulaOverThePageUnderTheScrim() {
    for (dark in listOf(false, true)) {
      val page = GlassPaintedPage.of(dark)
      val seen = glassPaintedSeen(page, dark)
      // Lo scrim è un nero della sua alfa sopra la pagina.
      close(page.red * (1f - GlassPaintedScrim.alpha(dark)), seen.red, eightBit, "scrim")
      val recipe = glassPaintedRecipe(320f, 320f, dark, largeVeil = 1f, profile = base)
      val veil = base.largeVeil(dark)
      // A mano, la riga del velo di `GlassBodyShader`: alfa ispessita dove lo sfondo somiglia al velo.
      val apart = maxOf(abs(seen.red - veil.color.red), abs(seen.green - veil.color.green), abs(seen.blue - veil.color.blue))
      val a = (veil.color.alpha * (1f + veil.adapt * (1f - apart) * (1f - apart))).coerceIn(0f, 1f)
      val expected = seen.red + (veil.color.red - seen.red) * a
      val body = glassPaintedBody(recipe, seen)
      close(expected, body.red, eightBit, "corpo (dark=$dark)")
      assertEquals(1f, body.alpha, 0f)
      assertEquals(body, glassPaintedPanelBody(page, dark, base))
    }
  }

  @Test
  fun theBodyFollowsTheStockVeilInsteadOfCopyingIt() {
    val lighter = base.copy(lightVeil = base.lightVeil.copy(color = Color(0x80E0E0E8)))
    val darker = base.copy(darkLargeVeil = base.darkLargeVeil.copy(color = Color(0x80202024)))
    val page = GlassPaintedPage.Light
    assertNotEquals(glassPaintedPanelBody(page, false, base), glassPaintedPanelBody(page, false, lighter))
    assertNotEquals(glassPaintedPanelBody(GlassPaintedPage.Dark, true, base), glassPaintedPanelBody(GlassPaintedPage.Dark, true, darker))
    // Il velo dei piccoli sullo scuro non tocca il pannello: un foglio è grande.
    val smallOnly = base.copy(darkVeil = base.darkVeil.copy(color = Color(0x80FFFFFF)))
    assertEquals(glassPaintedPanelBody(GlassPaintedPage.Dark, true, base), glassPaintedPanelBody(GlassPaintedPage.Dark, true, smallOnly))
  }

  @Test
  fun theDarkPanelTakesTheDeepVeilAndTheTextStillReads() {
    // Il velo scuro dei grandi c'è perché il testo secondario dell'engine (#AAAAB2) si legga: almeno 6:1 (S1-ter).
    val body = glassPaintedPanelBody(GlassPaintedPage.Dark, true, base)
    val text = Color(0xFFAAAAB2)
    fun lum(c: Color): Double {
      fun lin(v: Float) = if (v <= 0.04045f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
      return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }
    val ratio = (lum(text) + 0.05) / (lum(body) + 0.05)
    assertTrue("contrasto del testo secondario sul foglio scuro: $ratio", ratio >= 6.0)
  }

  // --- Il bordo -------------------------------------------------------------------------------------------------

  @Test
  fun theLightRimIsTheStockWireAndYieldsUnderTheLight() {
    val (recipe, plan) = plan(base, dark = false)
    val rim = plan.rim()
    assertEquals(recipe.rimDark.copy(alpha = 1f), rim.color)
    close(recipe.rimWidthDp * density, rim.stroke, what = "spessore del filo")
    val h = plan.heightPx
    val sides = rim.alphaAt(h / 2f)
    val top = rim.alphaAt(rim.inset)
    // Sui fianchi il filo è pieno; in alto e in basso cede di `rimLift` (sul ritaglio T6 il 64% dei fianchi).
    close(recipe.rimDark.alpha, sides, what = "filo sui fianchi")
    close(recipe.rimDark.alpha * (1f - recipe.rimLift), top, 1e-3f, "filo in alto")
    close(top, rim.alphaAt(h - rim.inset), 1e-3f, "filo in basso")
  }

  @Test
  fun theLightReflexIsALineAtTopAndBottomAndNothingOnTheSides() {
    val (recipe, plan) = plan(base, dark = false)
    val line = plan.line()
    assertEquals(Color.White, line.color)
    close(recipe.reflexStrength, line.alphaAt(line.inset), 1e-3f, "riga in alto")
    close(recipe.reflexStrength, line.alphaAt(plan.heightPx - line.inset), 1e-3f, "riga in basso")
    close(0f, line.alphaAt(plan.heightPx / 2f), 1e-3f, "riga sui fianchi")
    // La riga sta subito dentro il filo, larga due fili (`GlassLightLineWidth`).
    val rim = recipe.rimWidthDp * density
    close(rim + rim * GlassLightLineWidth / 2f, line.inset, 1e-3f, "posto della riga")
    // L'alone rientra fino a `reflexDepth` e scende con la distanza.
    val halo = plan.halo()
    assertTrue(halo.isNotEmpty())
    val peaks = halo.map { it.peak }
    assertTrue("l'alone scende verso il corpo: $peaks", peaks.zipWithNext().all { (a, b) -> b <= a + 1e-6f })
    assertTrue(peaks.first() <= recipe.reflexStrength * recipe.reflexHalo + 1e-6f)
    assertTrue(halo.last().inset <= recipe.reflexDepthDp * density + rim)
  }

  @Test
  fun theDarkSideHasTheLightWireAndTheStrongerReflex() {
    val (recipe, plan) = plan(base, dark = true)
    val rim = plan.rim()
    // Il filo chiaro (la B scelta, resa più decisa): bianco, pieno in alto e in basso, ceduto sui fianchi.
    assertEquals(Color.White, rim.color)
    close(recipe.rimDark.alpha, rim.alphaAt(rim.inset), 1e-3f, "filo chiaro in alto")
    close(recipe.rimDark.alpha * (1f + recipe.rimLift), rim.alphaAt(plan.heightPx / 2f), 1e-3f, "filo chiaro sui fianchi")
    close(recipe.reflexStrength, plan.line().alphaAt(plan.line().inset), 1e-3f, "riflesso scuro")
    assertTrue(recipe.reflexStrength > plan(base, dark = false).first.reflexStrength)
  }

  @Test
  fun theEdgeFollowsTheProfile() {
    val heavier = base.copy(lightEdge = base.lightEdge.copy(rim = Color.Black.copy(alpha = 0.5f), reflexStrength = 0.2f))
    val (_, a) = plan(base, dark = false)
    val (heavyRecipe, b) = plan(heavier, dark = false)
    // La ricetta porta l'alfa del profilo (per il peso del bordo dell'ottica, che sui pannelli di serie è pieno).
    close(0.5f * heavyRecipe.rimDark.alpha / 0.5f, b.rim().alphaAt(b.heightPx / 2f), 1e-3f, "filo dal profilo")
    assertTrue(heavyRecipe.rimDark.alpha > 0.45f)
    assertNotEquals(a.rim().peak, b.rim().peak)
    close(heavyRecipe.reflexStrength, b.line().peak, 1e-3f, "riga dal profilo")
    assertTrue(heavyRecipe.reflexStrength > 0.18f)
    val noWire = base.copy(darkEdge = GlassLiquidEdges.DarkReflex)
    val (_, c) = plan(noWire, dark = true)
    assertTrue("senza filo nessun passo copre al tratto", c.layers.none { it.blend == GlassPaintedBlend.Over && it.stroke > 0f })
    // Senza filo la riga nasce dal contorno.
    close(c.line().stroke / 2f, c.line().inset, 1e-3f, "riga dal contorno")
  }

  @Test
  fun theShadeLeansUpwardsAsInTheShader() {
    val (recipe, plan) = plan(base, dark = false)
    val vertical = plan.shade(GlassPaintedAxis.Vertical)
    val horizontal = plan.shade(GlassPaintedAxis.Horizontal)
    val a = recipe.bodyShade.alpha
    close(a, vertical.alphaAt(0f), 1e-3f, "ombreggiatura in alto")
    close(a * 0.25f, vertical.alphaAt(plan.heightPx), 1e-3f, "ombreggiatura in basso")
    close(a * 0.625f, horizontal.alphaAt(0f), 1e-3f, "ombreggiatura sui fianchi")
    close(0f, vertical.alphaAt(plan.heightPx / 2f), 1e-4f, "ombreggiatura in mezzo")
    // Quadratica: a metà profondità un quarto.
    val depth = recipe.bodyShadeDepthDp * density
    close(a * 0.25f, vertical.alphaAt(depth / 2f), 2e-3f, "metà profondità")
  }

  @Test
  fun aPaintedControlOnAPaintedPanelIsNested() {
    for (dark in listOf(false, true)) {
      val panel = glassPaintedPanelBody(GlassPaintedPage.of(dark), dark, base)
      val nested = glassPaintedRecipe(120f, 44f, dark, nested = true, profile = base)
      val alone = glassPaintedRecipe(120f, 44f, dark, profile = base)
      close(alone.veilColor.alpha * base.nesting(dark).veil, nested.veilColor.alpha, eightBit, "velo annidato")
      // Sul chiaro il tasto resta un poco più chiaro del pannello, sullo scuro una decina di livelli sopra: mai una
      // toppa (S1-ter, difetto 4).
      val body = glassPaintedBody(nested, panel)
      val lift = (body.red - panel.red) * 255f
      assertTrue("tasto dipinto sul pannello dipinto (dark=$dark): $lift livelli", lift >= -1f && lift <= 20f)
    }
  }

  @Test
  fun everyPlanIsFiniteAndItsGradientsIncrease() {
    val sizes = listOf(44f to 44f, 120f to 44f, 280f to 136f, 360f to 284f, 1f to 1f, 0f to 0f)
    for (dark in listOf(false, true)) for ((w, h) in sizes) for (radius in listOf(0f, 18f, 38f, 400f)) {
      val (_, p) = plan(base, dark, w, h, radius)
      for (layer in p.layers) {
        assertTrue(layer.inset.isFinite() && layer.stroke.isFinite())
        assertTrue(layer.alphas.all { it.isFinite() && it in 0f..1f })
        assertTrue("posizioni non crescenti a $w x $h r=$radius", layer.positions.toList().zipWithNext().all { (a, b) -> b > a })
        assertTrue(layer.corners.all { it.isFinite() && it >= 0f })
      }
    }
  }

  @Test
  fun theScrimIsOneAndHasTheModalLevels() {
    assertEquals(0.12f, GlassPaintedScrim.alpha(false), 0f)
    assertEquals(0.20f, GlassPaintedScrim.alpha(true), 0f)
    assertEquals(Color.Black.copy(alpha = 0.2f), GlassPaintedScrim.color(true))
  }

  @Test
  fun theScenesAreConsistentAndWrittenOnlyWhenAsked() {
    val scenes = PaintedScenes.all()
    assertEquals(12, scenes.size)
    for (scene in scenes) {
      @Suppress("UNCHECKED_CAST")
      val painted = scene.json["_dipinto"] as Map<String, Any?>
      assertTrue(scene.path, painted.keys.containsAll(listOf("oggi", "nuovo")))
    }
    val dir = GlassSceneOutput.requestedDir(System.getenv(PaintedScenes.Variable)) ?: return
    GlassSceneOutput.write(dir, scenes)
  }
}

/**
 * Le scene del banco del vetro dipinto: un foglio ancorato in basso e un avviso, su pagina, testo e foto, nei due
 * temi. L'elemento della scena è il **vetro vero** (lo stesso pannello nell'host, scritto da `glassRecipe`); nella
 * chiave `_dipinto` ci sono i passi del dipinto **di oggi** (fino alla 4.0) e di quello **nuovo**, in pixel del
 * pannello a densità 3, che `tools/glassbench/scenes/f1b/dipinto-rendi.py` disegna con Skia come li disegna Compose.
 */
internal object PaintedScenes {

  const val Variable = "GLASS_PAINTED_SCENE_DIR"

  private const val Width = 360
  private const val Height = 320
  private const val Density = 3f

  /** Il foglio: a filo dello schermo, il fondo oltre il bordo. */
  private const val SheetY = 116
  private const val SheetH = 236
  private const val SheetRadius = 38f

  /** La maniglia di `FluidGrabber`: 36 × 5 a 8 dp dalla cima, in una fascia di 21 dp. */
  private const val HandleTop = 8
  private const val HandleBand = 21

  /** La sagoma dipinta del foglio scende 48 dp oltre il fondo (`SheetBottomBleed`). */
  private const val SheetBleed = 48

  private const val AlertX = 40
  private const val AlertY = 92
  private const val AlertW = 280
  private const val AlertH = 136
  private const val AlertRadius = 18f

  /** Lo scrim di oggi: il foglio di piattaforma, nero al 42%; la `Dialog`, il `backgroundDimAmount` di Material (0,6, presunto). */
  private const val TodaySheetScrim = 0.42f
  private const val TodayDialogScrim = 0.6f

  private fun hex(c: Color) = glassColor(c)

  private fun scheme(dark: Boolean) = resolveFluidColorScheme(EngineSettings(), dark)

  private fun background(kind: String, dark: Boolean): Map<String, Any?> = when (kind) {
    "pagina" -> linkedMapOf("kind" to "color", "color" to hex(scheme(dark).background).substring(0, 7))
    "testo" -> linkedMapOf(
      "kind" to "text",
      "lines" to listOf(
        "Muoversi con i mezzi in Toscana: i bus dove",
        "passano, gli orari della sera e le fermate",
        "che conviene conoscere prima di partire.",
        "Le linee extraurbane cambiano d'estate.",
        "Il biglietto si compra anche a bordo.",
        "Muoversi con i mezzi in Toscana: i bus dove",
        "passano, gli orari della sera e le fermate",
        "che conviene conoscere prima di partire.",
        "Le linee extraurbane cambiano d'estate.",
        "Il biglietto si compra anche a bordo.",
        "Muoversi con i mezzi in Toscana: i bus dove",
        "passano, gli orari della sera e le fermate",
        "che conviene conoscere prima di partire.",
        "Le linee extraurbane cambiano d'estate.",
      ),
      "fontSizeDp" to 17, "lineHeightDp" to 22, "paddingDp" to 12,
      "color" to if (dark) "#E6E6EA" else "#1A1A1C",
      "background" to hex(scheme(dark).background).substring(0, 7),
    )
    "foto" -> linkedMapOf(
      "kind" to "synthetic", "scaleDp" to 46, "detail" to 1.0, "contrast" to 1.25,
      "palette" to listOf("#111114", "#F4D21C", "#F06AA8", "#1A1A22", "#35C2D6", "#F4D21C", "#E8651C", "#111114"),
    )
    else -> error(kind)
  }

  private fun scrimItem(alpha: Float): Map<String, Any?> = linkedMapOf(
    "kind" to "rect", "x" to 0, "y" to 0, "w" to Width, "h" to Height, "color" to hex(Color.Black.copy(alpha = alpha)),
  )

  private fun text(text: String, x: Number, y: Number, size: Number, color: Color, weight: Int = 400, align: String = "left") =
    linkedMapOf<String, Any?>(
      "kind" to "text", "text" to text, "x" to x, "y" to y, "sizeDp" to size, "weight" to weight, "align" to align,
      "color" to hex(color).substring(0, 7),
    )

  private fun handle(dark: Boolean): Map<String, Any?> = linkedMapOf(
    "kind" to "rect", "x" to (Width - 36) / 2, "y" to HandleTop, "w" to 36, "h" to 5, "radius" to 2.5,
    "color" to hex(scheme(dark).onSurface.copy(alpha = 0.18f)),
  )

  private fun sheetContent(dark: Boolean): List<Map<String, Any?>> {
    val s = scheme(dark)
    return listOf(
      text("Ancorato al bordo", 20, HandleBand + 28, 22, s.onSurface, 600),
      text("Riga 1 di contenuto, sopra la pagina.", 20, HandleBand + 62, 15, s.onSurfaceVariant),
      text("Riga 2 di contenuto, sopra la pagina.", 20, HandleBand + 88, 15, s.onSurfaceVariant),
      text("Riga 3: il testo principale si legge qui.", 20, HandleBand + 114, 15, s.onSurface),
    )
  }

  private fun alertContent(dark: Boolean): List<Map<String, Any?>> {
    val s = scheme(dark)
    val hairline = hex(s.onSurface.copy(alpha = 0.12f))
    return listOf(
      text("Eliminare la nota?", AlertW / 2, 38, 17, s.onSurface, 600, "center"),
      text("Non si potrà recuperare: resta solo", AlertW / 2, 62, 13, s.onSurface, 400, "center"),
      text("nel cestino per trenta giorni.", AlertW / 2, 79, 13, s.onSurface, 400, "center"),
      linkedMapOf("kind" to "rect", "x" to 0, "y" to 92, "w" to AlertW, "h" to 0.5, "color" to hairline),
      linkedMapOf("kind" to "rect", "x" to AlertW / 2, "y" to 92, "w" to 0.5, "h" to AlertH - 92, "color" to hairline),
      text("Annulla", AlertW / 4, 120, 17, s.primary, 400, "center"),
      text("Elimina", AlertW * 3 / 4, 120, 17, s.error, 600, "center"),
    )
  }

  /** Un passo di disegno in pixel del pannello: la sagoma spostata di [offset] e grande [size], piena o al tratto. */
  private fun op(
    offset: Pair<Float, Float>,
    size: Pair<Float, Float>,
    radii: FloatArray,
    stroke: Float,
    blend: String,
    from: Pair<Float, Float>,
    to: Pair<Float, Float>,
    stops: List<Pair<Float, Color>>,
  ): Map<String, Any?> = linkedMapOf(
    "offset" to listOf(offset.first, offset.second),
    "size" to listOf(size.first, size.second),
    "radii" to radii.toList(),
    "strokePx" to stroke,
    "blend" to blend,
    "from" to listOf(from.first, from.second),
    "to" to listOf(to.first, to.second),
    "stops" to stops.map { (t, c) -> listOf(t, hex(c)) },
  )

  private fun solid(w: Float, h: Float, radii: FloatArray, color: Color) =
    op(0f to 0f, w to h, radii, 0f, "SrcOver", 0f to 0f, 0f to h, listOf(0f to color, 1f to color))

  /** I passi di un piano nuovo ([glassPaintedPlan]) come li disegna `Modifier.glassPainted`. */
  private fun planOps(plan: GlassPaintedPlan): List<Map<String, Any?>> = plan.layers.filter { it.peak > 0f }.map { layer ->
    val i = layer.inset
    val vertical = layer.axis == GlassPaintedAxis.Vertical
    val length = if (vertical) plan.heightPx else plan.widthPx
    op(
      offset = i to i,
      size = (plan.widthPx - 2f * i) to (plan.heightPx - 2f * i),
      radii = layer.corners,
      stroke = layer.stroke,
      blend = if (layer.blend == GlassPaintedBlend.Plus) "Plus" else "SrcOver",
      from = 0f to 0f,
      to = if (vertical) 0f to plan.heightPx else plan.widthPx to 0f,
      stops = layer.positions.indices.map { k -> (layer.positions[k] / length).coerceIn(0f, 1f) to layer.color.copy(alpha = layer.alphas[k]) },
    )
  }

  /**
   * I passi del dipinto **di oggi** (`fluidStaticGlassSurface` fino alla 4.0), alla lettera, su una colonna di
   * [w] × [h] px spostata in giù di [top]: corpo in diagonale, tratto da 1,35 dp, secondo tratto 2 dp più dentro.
   */
  private fun todayOps(dark: Boolean, w: Float, h: Float, top: Float, radii: FloatArray): List<Map<String, Any?>> {
    val s = scheme(dark)
    val light = !dark
    val body = s.surface
    val topLight = Color.White.copy(alpha = if (light) 0.86f else 0.58f)
    val quietLight = Color.White.copy(alpha = if (light) 0.24f else 0.16f)
    val lowerShade = Color.Black.copy(alpha = if (light) 0.30f else 0.52f)
    val innerShade = s.onSurface.copy(alpha = if (light) 0.22f else 0.32f)
    val inset = 2f * Density
    val innerRadii = FloatArray(4) { minOf(radii[it], (minOf(w, h) - 2f * inset) / 2f) }
    return listOf(
      op(0f to top, w to h, radii, 0f, "SrcOver", 0f to top, w to (top + h), listOf(
        0f to body.copy(alpha = 0.18f), 0.46f to body.copy(alpha = 0.10f), 1f to body.copy(alpha = 0.15f),
      )),
      op(0f to top, w to h, radii, 1.35f * Density, "SrcOver", 0f to top, w to (top + h), listOf(
        0f to topLight, 0.38f to quietLight, 0.68f to Color.Transparent, 1f to lowerShade,
      )),
      op(inset to (top + inset), (w - 2f * inset) to (h - 2f * inset), innerRadii, 0.95f * Density, "SrcOver",
        inset to (top + inset), (w + inset) to (top + h + inset), listOf(
          0f to quietLight, 0.5f to Color.Transparent, 1f to innerShade,
        )),
    )
  }

  /** Le maschere dell'ombra di una ricetta, come le scrive `liquidElement`. */
  private fun shadowOf(recipe: GlassRecipe): List<Map<String, Any?>> {
    if (!recipe.drawsShadow) return emptyList()
    val share = GlassShadowProfile.contactShare(recipe.shadowContactRadiusDp, recipe.shadowAmbientRadiusDp)
    val colour = hex(recipe.shadowColor).substring(0, 7)
    val weight = recipe.shadowColor.alpha
    val out = ArrayList<Map<String, Any?>>()
    if (recipe.shadowContactAlpha > 0f) out += linkedMapOf(
      "radiusDp" to recipe.shadowContactRadiusDp,
      "offsetDp" to listOf(recipe.shadowOffsetXDp * share, recipe.shadowOffsetYDp * share),
      "alpha" to recipe.shadowContactAlpha * weight, "color" to colour,
    )
    if (recipe.shadowAmbientAlpha > 0f) out += linkedMapOf(
      "radiusDp" to recipe.shadowAmbientRadiusDp,
      "offsetDp" to listOf(recipe.shadowOffsetXDp, recipe.shadowOffsetYDp),
      "alpha" to recipe.shadowAmbientAlpha * weight, "color" to colour,
    )
    return out
  }

  fun scene(panel: String, kind: String, dark: Boolean): GlassScene {
    val side = if (dark) "scuro" else "chiaro"
    val s = scheme(dark)
    val sheet = panel == "foglio"
    val x = if (sheet) 0 else AlertX
    val y = if (sheet) SheetY else AlertY
    val w = if (sheet) Width else AlertW
    val h = if (sheet) SheetH else AlertH
    val radius: Any = if (sheet) listOf(SheetRadius, SheetRadius, 0f, 0f) else AlertRadius
    val r = (if (sheet) SheetRadius else AlertRadius) * Density
    val radiiPx = if (sheet) floatArrayOf(r, r, 0f, 0f) else floatArrayOf(r, r, r, r)
    val wPx = w * Density
    val hPx = h * Density
    val content = if (sheet) listOf(handle(dark)) + sheetContent(dark) else alertContent(dark)

    // Il vetro vero: lo stesso pannello nell'host (ruolo `Modal`, `panelOptics`), che campiona la pagina sotto lo scrim.
    val trueRecipe = glassRecipe(GlassRole.Modal, w.toFloat(), h.toFloat(), dark, optics = GlassDefaults.panelOptics())
    val element = liquidElement(
      linkedMapOf("name" to panel, "x" to x, "y" to y, "w" to w, "h" to h, "radius" to radius, "items" to content),
      trueRecipe, null, "glassRecipe: ruolo Modal, panelOptics, $w x $h dp, lato $side (il $panel di vetro nell'host)",
    )

    // Il dipinto nuovo, come lo disegnano `FluidSheet` (contenitore + passi da sopra la maniglia a oltre il fondo) e
    // `FluidAlert` (corpo + passi), con l'ombra di `glassShadow(shape, panelOptics)`.
    val page = s.background
    val body = glassPaintedPanelBody(page, dark)
    val seen = glassPaintedSeen(page, dark)
    val areaH = if (sheet) hPx + SheetBleed * Density else hPx
    val recipe = glassPaintedRecipe(w.toFloat(), areaH / Density, dark, largeVeil = 1f)
    val newPlan = glassPaintedPlan(recipe, seen, wPx, areaH, Density, radiiPx)
    val shadowRecipe = glassRecipe(GlassRole.Floating, w.toFloat(), h.toFloat(), dark, optics = GlassDefaults.panelOptics())
    val nuovo = linkedMapOf<String, Any?>(
      "scrim" to GlassPaintedScrim.alpha(dark),
      "shadow" to shadowOf(shadowRecipe),
      "under" to listOf(solid(wPx, hPx, radiiPx, body)),
      "handle" to if (sheet) listOf(handle(dark)) else emptyList(),
      "over" to planOps(newPlan),
      "items" to if (sheet) sheetContent(dark) else alertContent(dark),
      "_corpo" to hex(body),
    )

    // Il dipinto di oggi: il foglio col contenitore al 92% e il dipinto sotto la maniglia; l'avviso col fondo al 92%.
    val oggi = if (sheet) {
      val top = HandleBand * Density
      linkedMapOf<String, Any?>(
        "scrim" to TodaySheetScrim,
        "shadow" to emptyList<Any>(),
        "under" to listOf(solid(wPx, hPx, radiiPx, s.surface.copy(alpha = 0.92f))),
        "handle" to listOf(handle(dark)),
        "over" to todayOps(dark, wPx, hPx - top, top, radiiPx),
        "items" to sheetContent(dark),
      )
    } else {
      linkedMapOf<String, Any?>(
        "scrim" to TodayDialogScrim,
        "shadow" to emptyList<Any>(),
        "under" to listOf(solid(wPx, hPx, floatArrayOf(0f, 0f, 0f, 0f), s.surfaceContainerHigh.copy(alpha = 0.92f))),
        "handle" to emptyList<Any>(),
        "over" to todayOps(dark, wPx, hPx, 0f, radiiPx),
        "items" to alertContent(dark),
      )
    }

    val json = linkedMapOf<String, Any?>(
      "_" to "Scritta dalla ricetta (GlassPaintedProfileTest, F1B.5): il $panel di vetro VERO nell'host (l'elemento) " +
        "e, in `_dipinto`, i passi del vetro dipinto di oggi e di quello nuovo (pixel del pannello a densita' 3), che " +
        "dipinto-rendi.py disegna accanto. Fondo $kind, lato $side. Non si modifica a mano: si rigenera.",
      "width" to Width, "height" to Height, "density" to Density, "theme" to if (dark) "dark" else "light",
      "palette" to "engine", "corner" to "engine",
      "background" to background(kind, dark),
      "page" to listOf(scrimItem(GlassPaintedScrim.alpha(dark))),
      "elements" to listOf(element),
      "_dipinto" to linkedMapOf(
        "pannello" to linkedMapOf("name" to panel, "x" to x, "y" to y, "w" to w, "h" to h, "radius" to radius),
        "oggi" to oggi,
        "nuovo" to nuovo,
      ),
    )
    return GlassScene("dipinto-$panel-$kind-$side.json", json)
  }

  fun all(): List<GlassScene> = listOf("foglio", "avviso").flatMap { panel ->
    listOf("pagina", "testo", "foto").flatMap { kind -> listOf(false, true).map { dark -> scene(panel, kind, dark) } }
  }
}
