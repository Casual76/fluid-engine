package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidEdges
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidFrosts
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidGrowths
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidLenses
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidShadows
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVeils
import dev.antigravity.fluidengine.ui.glass.field.GlassRecipe
import dev.antigravity.fluidengine.ui.glass.field.GlassShadowProfile
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * Le scene ferme del banco scritte DALLA RICETTA (piano 4.2): una scena ferma del materiale nuovo non porta
 * numeri ricopiati a mano, perché ciò che si misura contro Apple e ciò che Alessio sceglie su una tavola deve
 * essere ciò che l'engine disegnerà. Qui c'è chi le compone; `GlassSceneExportTest` le controlla e, se glielo
 * chiedono, le scrive.
 *
 * Due famiglie, in due sottocartelle di `tools/glassbench/scenes/f1a/`:
 *  - `ricetta/`: le inquadrature di `scenes/rif/` (i frame di Apple di R4 e R5 e le sei pagine dell'engine)
 *    **rilette** e riscritte con ogni lastra di oggi sostituita da ciò che `glassRecipe` dà per quel ruolo, quella
 *    taglia e quel lato. Fondale, pagina e finestre di misura restano quelli: `measure` dà gli stessi numeri
 *    della tabella «oggi», per il materiale nuovo;
 *  - `gusto/`: il **campione** (tasto, capsula, tab bar, menu, pannello grande), una scena per ogni variante di
 *    gusto e per lato. Le tavole le compone il banco (`glassbench.py tavola`), che cambia solo fondale e
 *    inquadratura: mai un numero del materiale.
 *
 * Cosa in una scena NON viene dalla ricetta, e perché: la **geometria** (i pezzi del campo, il raccordo, il
 * ritaglio), che è del piano del campo (`GlassFieldPlan`, F1A.6) e qui è anticipata da [slabGeometry]; e il
 * `glow`, che è del tocco e a riposo vale zero.
 */

/** Come il banco chiama un ruolo (`"role"` di una lastra di oggi) e cosa vuol dire per l'engine. */
internal class SceneSurface(
  val benchRole: String,
  val role: GlassRole,
  val optics: GlassOptics,
  /** L'alfa della pellicola di oggi per questo ruolo, lato scuro e lato chiaro (`TINT_ALPHA` del banco). */
  val filmAlphaDark: Float,
  val filmAlphaLight: Float,
)

internal fun sceneSurface(benchRole: String): SceneSurface = when (benchRole) {
  "bar" -> SceneSurface(benchRole, GlassRole.Bar, GlassDefaults.optics(GlassRole.Bar), 0.40f, 0.44f)
  "floating" -> SceneSurface(benchRole, GlassRole.Floating, GlassDefaults.optics(GlassRole.Floating), 0.48f, 0.52f)
  "interactive" ->
    SceneSurface(benchRole, GlassRole.Interactive, GlassDefaults.optics(GlassRole.Interactive), 0.34f, 0.38f)
  "button" -> SceneSurface(benchRole, GlassRole.Interactive, GlassDefaults.buttonOptics(), 0.34f, 0.38f)
  "modal" -> SceneSurface(benchRole, GlassRole.Modal, GlassDefaults.optics(GlassRole.Modal), 0.50f, 0.54f)
  "content" -> SceneSurface(benchRole, GlassRole.Content, GlassDefaults.optics(GlassRole.Content), 0.52f, 0.56f)
  "stackedcontent" ->
    SceneSurface(benchRole, GlassRole.Content, GlassDefaults.stackedContentOptics(), 0.30f, 0.34f)
  "panel" -> SceneSurface(benchRole, GlassRole.Modal, GlassDefaults.panelOptics(), 0.50f, 0.54f)
  "compactpanel" -> SceneSurface(benchRole, GlassRole.Modal, GlassDefaults.panelOptics(compact = true), 0.50f, 0.54f)
  "sharppagepanel" ->
    SceneSurface(benchRole, GlassRole.Modal, GlassDefaults.panelOptics(overSharpPage = true), 0.50f, 0.54f)
  "compactsharppagepanel" -> SceneSurface(
    benchRole, GlassRole.Modal, GlassDefaults.panelOptics(compact = true, overSharpPage = true), 0.50f, 0.54f,
  )
  else -> error("ruolo del banco sconosciuto: $benchRole")
}

/**
 * La pellicola di oggi (`GlassDefaults.glassFilm` con la palette di serie dell'engine), come la calcola il
 * banco: serve alla sola variante «oggi» della tavola del velo. È il termine di paragone, non il materiale
 * nuovo: per questo sta qui, ricopiata, e non nel profilo.
 */
internal fun todayFilm(surface: SceneSurface, darkSide: Boolean): Color =
  if (darkSide) Color(0xFF3E4043).copy(alpha = surface.filmAlphaDark)
  else Color(0xFFFBFBFB).copy(alpha = surface.filmAlphaLight)

/** L'alfa della tinta di Apple (`tintMode` 1) nelle scene: quasi opaca, come in R4 A7. Scelta di F1A.2. */
internal const val SceneTintAlpha: Float = 0.95f

/** Le due densità a cui le scene si rendono: il telefono (3) e il tablet (2,125). */
internal val SceneDensities: FloatArray = floatArrayOf(3f, 2.125f)

/** Come il campo vede una lastra ferma: i raggi, il raccordo, e il ritaglio hardware. */
internal class SlabGeometry(
  /** `pieceRadii[0]` nella forma del banco: `"$radii"` (i nominali), o quattro lunghezze in dp. */
  val radii: Any,
  /** `pieceSmooth[0]`. */
  val smoothing: Float,
  /** Vero se l'angolo è raccordato: allora il ritaglio hardware sta al 98% del raggio nominale. */
  val smoothed: Boolean,
)

/**
 * La regola «forma → piano» del piano (3.7), anticipata qui per le scene; il suo posto vero è `GlassFieldPlan`
 * (F1A.6), che deve dare gli stessi numeri:
 *  - capsula: un semicerchio esatto, raggi nominali e nessun raccordo;
 *  - grandi (lato lungo da [ContinuousCornerShape.SmallShapeCutoffPx] in su): raggi nominali e il raccordo di
 *    iOS; il ritaglio hardware al 98% del raggio, perché il raccordo esce dal cerchio nominale dello 0,44% del
 *    raggio sulla diagonale (decisione dell'orchestratore su F1A.1);
 *  - piccoli: il rettangolo arrotondato al raggio equivalente che l'engine già disegna (x0,82), senza raccordo.
 *
 * La soglia è in pixel, quindi dipende dalla densità: una scena vale a più densità solo se ogni sua lastra
 * cade dalla stessa parte a tutte ([SceneDensities]), e qui si controlla.
 */
internal fun slabGeometry(name: String, w: Double, h: Double, radius: Any?): SlabGeometry {
  val short = min(w, h)
  val nominal: List<Double> = when (radius) {
    null, "capsule" -> return SlabGeometry("\$radii", 0f, false)
    is Number -> List(4) { radius.toDouble() }
    is List<*> -> radius.map { (it as Number).toDouble() }
    else -> error("$name: raggio non leggibile: $radius")
  }
  require(nominal.size == 4) { "$name: servono quattro raggi, non ${nominal.size}" }
  if (nominal.all { it >= short / 2.0 }) return SlabGeometry("\$radii", 0f, false)
  val big = SceneDensities.map { max(w, h) * it >= ContinuousCornerShape.SmallShapeCutoffPx }
  require(big.distinct().size == 1) {
    "$name: a una densità è un «grande» e a un'altra un «piccolo»: la scena non varrebbe a tutte e due"
  }
  return if (big.first()) {
    SlabGeometry("\$radii", ContinuousCornerShape.IosSmoothing, true)
  } else {
    SlabGeometry(nominal.map { glassDp(it * ContinuousCornerShape.RoundedEquivalentFactor) }, 0f, false)
  }
}

/** I campi di una lastra del banco che dicono cosa c'è stampato sopra: passano così come sono. */
private val ContentKeys = listOf(
  "icon", "icons", "label", "rows", "items", "iconSizeDp", "labelSizeDp", "labelWeight", "rowHeightDp",
  "rowsTopDp", "contentColor", "contentLayer", "content", "contentClip",
)

private fun colorRgb(color: Color): String = glassColor(color).substring(0, 7)

private fun fmt(value: Float): String = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')

/**
 * Una lastra del materiale nuovo come elemento `kind: "material"` del banco, con i numeri di [recipe].
 *
 * @param shape i campi della lastra nella scena: `name`, `x`, `y`, `w`, `h`, `radius`, e il contenuto.
 * @param theme il tema di questa lastra, se diverso da quello della scena (o `null`).
 * @param note cosa ha chiesto alla ricetta: resta scritto nell'elemento (`_ricetta`), per chi legge la scena.
 */
internal fun liquidElement(
  shape: Map<String, Any?>,
  recipe: GlassRecipe,
  theme: String?,
  note: String,
): LinkedHashMap<String, Any?> {
  val name = shape.getValue("name") as String
  val w = (shape.getValue("w") as Number).toDouble()
  val h = (shape.getValue("h") as Number).toDouble()
  val geometry = slabGeometry(name, w, h, shape["radius"])

  fun field(): LinkedHashMap<String, Any?> = linkedMapOf(
    "offset" to "\$offset",
    "flex" to listOf(1, 0, 0, 1),
    "flexCenter" to listOf(0, 0),
    "pieceCount" to 1,
    "pieceRect" to listOf(listOf("\$center", "\$halfSize")),
    "pieceRadii" to listOf(geometry.radii),
    "pieceBlend" to listOf(0),
    "pieceSmooth" to listOf(geometry.smoothing),
  )

  val out = LinkedHashMap<String, Any?>()
  out["kind"] = "material"
  out["name"] = name
  out["_ricetta"] = note
  for (key in listOf("x", "y", "w", "h")) out[key] = shape.getValue(key)
  out["radius"] = shape["radius"] ?: "capsule"
  if (theme != null) out["theme"] = theme
  out["clip"] = true
  if (geometry.smoothed) out["clipRadiusScale"] = SceneSmoothedClipScale

  if (recipe.drawsShadow) {
    val share = GlassShadowProfile.contactShare(recipe.shadowContactRadiusDp, recipe.shadowAmbientRadiusDp)
    val colour = colorRgb(recipe.shadowColor)
    val weight = recipe.shadowColor.alpha
    val layers = ArrayList<Map<String, Any?>>()
    if (recipe.shadowContactAlpha > 0f) {
      layers += linkedMapOf(
        "radiusDp" to recipe.shadowContactRadiusDp,
        "offsetDp" to listOf(recipe.shadowOffsetXDp * share, recipe.shadowOffsetYDp * share),
        "alpha" to recipe.shadowContactAlpha * weight,
        "color" to colour,
      )
    }
    if (recipe.shadowAmbientAlpha > 0f) {
      layers += linkedMapOf(
        "radiusDp" to recipe.shadowAmbientRadiusDp,
        "offsetDp" to listOf(recipe.shadowOffsetXDp, recipe.shadowOffsetYDp),
        "alpha" to recipe.shadowAmbientAlpha * weight,
        "color" to colour,
      )
    }
    out["shadow"] = layers
  }

  out["capture"] = linkedMapOf(
    "saturation" to recipe.saturation,
    "blurDp" to recipe.blurDp,
    "w" to recipe.mix,
    "paddingDp" to recipe.paddingDp,
    "scale" to recipe.captureScale,
  )

  val body = field()
  body["thickness"] = glassDp(recipe.thicknessDp)
  body["refraction"] = glassDp(recipe.refractionDp)
  body["dome"] = recipe.dome
  body["dispersion"] = recipe.dispersion
  body["magnify"] = 0
  body["materialize"] = recipe.materialize
  body["veilColor"] = glassColor(recipe.veilColor)
  body["veilAdapt"] = recipe.veilAdapt
  body["tintColor"] = glassColor(recipe.tintColor)
  body["tintMode"] = recipe.tintMode
  body["tintFollow"] = recipe.tintFollow
  body["bodyShade"] = glassColor(recipe.bodyShade)
  body["bodyShadeDepth"] = glassDp(recipe.bodyShadeDepthDp)
  body["bodyShadeLean"] = listOf(recipe.bodyShadeLeanX, recipe.bodyShadeLeanY)
  body["dim"] = recipe.dim
  body["grain"] = recipe.grain
  body["aa"] = recipe.aa
  body["lightInBody"] = 0
  out["body"] = linkedMapOf("source" to "kotlin:GlassBody1ShaderString", "uniforms" to body)

  // La luce a riposo: due disegni dello stesso programma, prima la somma e poi il filo, e ognuno solo se ha
  // qualcosa da disegnare (è ciò che farà `onDrawLight` in F1A.6).
  fun light(part: Int): LinkedHashMap<String, Any?> {
    val u = field()
    u["lightPart"] = part
    u["rimWidth"] = glassDp(recipe.rimWidthDp)
    u["rimDark"] = glassColor(recipe.rimDark)
    u["rimFeather"] = recipe.rimFeather
    u["rimLift"] = recipe.rimLift
    u["reflexDir"] = listOf(recipe.reflexDirX, recipe.reflexDirY)
    u["reflexWidths"] = listOf(recipe.reflexHalfWidth, recipe.reflexOppositeHalfWidth)
    u["reflexStrength"] = recipe.reflexStrength
    u["reflexDepth"] = glassDp(recipe.reflexDepthDp)
    u["reflexFloor"] = recipe.reflexFloor
    u["reflexHalo"] = recipe.reflexHalo
    u["glowCenter"] = "\$center"
    u["glowRadius"] = glassDp(max(w, h))
    u["glow"] = 0
    u["glowFill"] = recipe.glowFill
    u["lightColor"] = glassColor(recipe.lightColor)
    u["materialize"] = recipe.materialize
    u["aa"] = recipe.aa
    val layer = LinkedHashMap<String, Any?>()
    layer["source"] = "kotlin:GlassLight1ShaderString"
    if (part == 0) layer["blend"] = "Plus"
    layer["uniforms"] = u
    return layer
  }
  val lights = ArrayList<Map<String, Any?>>()
  if (recipe.drawsReflex) lights += light(0)
  if (recipe.drawsRim) lights += light(1)
  if (lights.isNotEmpty()) out["light"] = lights

  for (key in ContentKeys) if (key in shape) out[key] = shape[key]
  return out
}

/** Il ritaglio hardware di una lastra raccordata, in frazione del raggio nominale. Vedi [slabGeometry]. */
internal const val SceneSmoothedClipScale: Double = 0.98

/** Cosa una lastra chiede alla ricetta oltre a ruolo, taglia e lato. */
internal class SceneAsk(
  val profile: (SceneSurface) -> GlassLiquidProfile = { GlassLiquidProfile.Default },
  val variant: GlassLiquidVariant = GlassLiquidVariant.Regular,
  /** Vero: il velo è la pellicola di oggi (un `GlassTint` dipinto com'è). */
  val todayFilm: Boolean = false,
  val label: String = "profilo di serie",
)

/** Una lastra del banco (`kind: "glass"`, col suo `role`) resa dal materiale nuovo. */
internal fun liquidFromGlass(el: Map<String, Any?>, sceneTheme: String, ask: SceneAsk): LinkedHashMap<String, Any?> {
  val surface = sceneSurface(el["role"] as? String ?: "floating")
  val ownTheme = el["theme"] as? String
  val dark = (ownTheme ?: sceneTheme) == "dark"
  val w = (el.getValue("w") as Number).toFloat()
  val h = (el.getValue("h") as Number).toFloat()
  val tint = (el["tint"] as? String)?.takeIf { it.startsWith("#") }?.let { parseColor(it).copy(alpha = SceneTintAlpha) }
  val recipe = glassRecipe(
    role = surface.role,
    widthDp = w,
    heightDp = h,
    darkSide = dark,
    variant = ask.variant,
    optics = surface.optics,
    intensity = (el["intensity"] as? Number)?.toFloat() ?: 1f,
    opticalDepth = (el["depth"] as? Number)?.toFloat() ?: 1f,
    quality = (el["quality"] as? Number)?.toFloat() ?: 1f,
    pressed = (el["pressed"] as? Number)?.toFloat() ?: 0f,
    film = if (ask.todayFilm) todayFilm(surface, dark) else Color.Unspecified,
    tint = tint ?: Color.Unspecified,
    profile = ask.profile(surface),
  )
  val note = "glassRecipe: ruolo ${surface.benchRole}, ${fmt(w)} x ${fmt(h)} dp, lato ${if (dark) "scuro" else "chiaro"}, " +
    ask.label + (if (ask.variant == GlassLiquidVariant.Clear) ", variante limpida" else "") +
    (if (tint != null) ", tinta ${el["tint"]}" else "")
  return liquidElement(el, recipe, ownTheme, note)
}

/** `#RRGGBB` o `#RRGGBBAA`, come li scrive il banco. */
internal fun parseColor(text: String): Color {
  val hex = text.removePrefix("#")
  require(hex.length == 6 || hex.length == 8) { "colore non leggibile: $text" }
  val r = hex.substring(0, 2).toInt(16)
  val g = hex.substring(2, 4).toInt(16)
  val b = hex.substring(4, 6).toInt(16)
  val a = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
  return Color(r, g, b, a)
}

/** Una scena scritta dalla ricetta: dove va (relativo alla cartella delle scene) e cosa contiene. */
internal class GlassScene(val path: String, val json: Map<String, Any?>) {
  fun encode(): String = GlassTraceJson.encode(json)
}

internal object GlassScenes {

  /** Le inquadrature di `scenes/rif/` che si riscrivono col materiale nuovo: tutte tranne il bordo dello scorrimento (F2). */
  val References: List<String> = listOf(
    "rif27-tabbar", "rif27-tabbar-telefono", "rif27-tasto-bianco", "rif27-scuro",
    "pagina-chiara", "pagina-scura", "pagina-nera", "pagina-bianca", "pagina-testo", "pagina-foto",
    "rif-tondo-chiaro", "rif-capsula-chiara", "rif-capsula-piatto", "rif-capsula-testo", "rif-barra-menu-foto",
    "rif-bianco-nero", "rif-tinta", "rif-lente-griglia", "rif-tondo-scuro",
  )

  /** Una scena di `scenes/rif/` (il suo testo) con ogni lastra di oggi sostituita dal materiale nuovo. */
  fun fromReference(name: String, text: String, ask: SceneAsk = SceneAsk()): GlassScene {
    val scene = GlassSceneJson.parseObject(text)
    val theme = scene["theme"] as? String ?: "dark"
    scene["_"] = "Scritta dalla ricetta (GlassSceneExportTest, F1A.5): e' `scenes/rif/$name.json` con ogni lastra " +
      "sostituita da cio' che `glassRecipe` da' per quel ruolo, quella taglia e quel lato, ${ask.label}. Non si " +
      "modifica a mano: si rigenera. " + (scene["_"] as? String ?: "")
    @Suppress("UNCHECKED_CAST")
    val elements = scene["elements"] as List<Map<String, Any?>>
    scene["elements"] = elements.map { el ->
      if ((el["kind"] ?: "glass") == "glass") liquidFromGlass(el, theme, ask) else el
    }
    return GlassScene("ricetta/$name.json", scene)
  }

  // ---- Il campione delle tavole di gusto --------------------------------------------------------------------

  const val SampleWidth: Int = 900
  const val SampleHeight: Int = 440

  private val MenuRows: List<Map<String, Any?>> = listOf(
    linkedMapOf("label" to "Copia", "icon" to "fluid:ios_share|share"),
    linkedMapOf("label" to "Rispondi", "icon" to "fluid:reply|back"),
    linkedMapOf("label" to "Cerca", "icon" to "fluid:search|search"),
    linkedMapOf("label" to "Elimina", "icon" to "fluid:delete|close", "color" to "#E5484D"),
  )

  /**
   * Le cinque lastre del campione, nella forma delle lastre di oggi (`kind: "glass"`): le quattro taglie del
   * piano (tasto 44 dp, capsula, menu, pannello grande) più una tab bar alta 62 dp, che è la taglia dei
   * ritagli di Apple. Il pannello ha il lato corto di 330 dp: la regola della taglia gli dà il guadagno pieno.
   */
  val SampleShapes: List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to "tasto", "role" to "button", "x" to 30, "y" to 39, "w" to 44, "h" to 44, "radius" to "capsule",
      "iconSizeDp" to 26, "icon" to "fluid:add|plus",
    ),
    linkedMapOf(
      "name" to "capsula", "role" to "floating", "x" to 104, "y" to 33, "w" to 156, "h" to 56, "radius" to "capsule",
      "iconSizeDp" to 26, "icons" to listOf("fluid:arrow_back|back", "fluid:add|plus", "fluid:circle|circle"),
    ),
    linkedMapOf(
      "name" to "barra", "role" to "floating", "x" to 290, "y" to 30, "w" to 220, "h" to 62, "radius" to "capsule",
      "iconSizeDp" to 28, "icons" to listOf("fluid:home|home", "fluid:grid_view|grid4", "fluid:search|search"),
    ),
    linkedMapOf(
      "name" to "menu", "role" to "compactpanel", "x" to 30, "y" to 150, "w" to 170, "h" to 192, "radius" to 28,
      "rowHeightDp" to 44, "rows" to MenuRows,
    ),
    linkedMapOf(
      "name" to "pannello", "role" to "panel", "x" to 540, "y" to 30, "w" to 330, "h" to 380, "radius" to 36,
      "rowHeightDp" to 52, "rows" to MenuRows.take(3),
    ),
  )

  private fun sampleMeasures(dark: Boolean): List<Map<String, Any?>> {
    val out = ArrayList<Map<String, Any?>>()
    for (shape in SampleShapes) {
      val name = shape.getValue("name") as String
      out += linkedMapOf(
        "kind" to "body", "element" to name, "label" to name, "rows" to listOf("corpo", "fondo", "corpo-fondo", "velo"),
      )
      for (side in listOf("top", "left", "bottom")) {
        out += linkedMapOf(
          "kind" to "edge", "element" to name, "side" to side, "label" to name, "refDp" to 62,
          "rows" to listOf("filo-scuro", "filo-largo", "riflesso"),
        )
      }
      if (!dark) {
        out += linkedMapOf(
          "kind" to "shadow", "element" to name, "label" to name,
          "rows" to listOf("ombra@0.03", "ombra@0.1", "ombra@0.2", "ombra-max", "ombra-estensione"),
        )
      }
    }
    return out
  }

  private fun sampleHead(note: String, dark: Boolean): LinkedHashMap<String, Any?> = linkedMapOf(
    "_" to note,
    "width" to SampleWidth,
    "height" to SampleHeight,
    "density" to 3.0,
    "theme" to if (dark) "dark" else "light",
    "palette" to "engine",
    "corner" to "engine",
    "background" to linkedMapOf("kind" to "color", "color" to if (dark) "#0D0D0F" else "#F2F2F7"),
  )

  /** Il campione col materiale nuovo: [key] è il nome della variante, [ask] ciò che chiede alla ricetta. */
  fun sample(key: String, dark: Boolean, ask: SceneAsk): GlassScene {
    val theme = if (dark) "dark" else "light"
    val scene = sampleHead(
      "Scritta dalla ricetta (GlassSceneExportTest, F1A.5): il campione delle tavole di gusto, variante `$key`, " +
        "lato ${if (dark) "scuro" else "chiaro"}: ${ask.label}. Tasto 44 dp, capsula, tab bar 62 dp, menu, pannello " +
        "grande. Le tavole le compone `glassbench.py tavola`, che cambia fondale e inquadratura e mai un numero " +
        "del materiale. Non si modifica a mano: si rigenera.",
      dark,
    )
    scene["elements"] = SampleShapes.map { liquidFromGlass(it, theme, ask) }
    scene["measure"] = sampleMeasures(dark)
    return GlassScene("gusto/$key-${if (dark) "scuro" else "chiaro"}.json", scene)
  }

  /** Lo stesso campione col **vetro di oggi**: le lastre restano `kind: "glass"`, e il banco le rende da `GlassMaterial.kt`. */
  fun sampleToday(dark: Boolean): GlassScene {
    val scene = sampleHead(
      "Il campione delle tavole di gusto col VETRO DI OGGI (modello Classic): le stesse cinque lastre di " +
        "`gusto/serie-*`, rese dal banco con le ottiche lette da fluid/GlassMaterial.kt. E' il «prima» della " +
        "tavola «tutto insieme». Scritta da GlassSceneExportTest perche' taglie e posizioni restino quelle del campione.",
      dark,
    )
    scene["elements"] = SampleShapes
    scene["measure"] = sampleMeasures(dark)
    return GlassScene("gusto/oggi-${if (dark) "scuro" else "chiaro"}.json", scene)
  }

  /** Su quali lati esiste una variante. */
  private enum class Sides { Both, Light, Dark }

  private class Taste(val key: String, val sides: Sides, val ask: SceneAsk)

  private fun taste(
    key: String,
    label: String,
    sides: Sides = Sides.Both,
    variant: GlassLiquidVariant = GlassLiquidVariant.Regular,
    todayFilm: Boolean = false,
    profile: (SceneSurface) -> GlassLiquidProfile,
  ) = Taste(key, sides, SceneAsk(profile, variant, todayFilm, label))

  private val Default = GlassLiquidProfile.Default

  /**
   * Le varianti di gusto, una per ogni colonna di una tavola. In ognuna cambia **un pezzo** del profilo di
   * serie; `serie` è la combinazione proposta.
   */
  private val Tastes: List<Taste> = listOf(
    taste("serie", "profilo di serie") { Default },
    // 1. Il bordo (lato chiaro).
    taste("bordo-ios26", "bordo di iOS 26: due archi di luce, nessun filo", Sides.Light) {
      Default.copy(lightEdge = GlassLiquidEdges.Ios26)
    },
    taste("bordo-oggi", "bordo di oggi: filetto scuro pieno sopra e sotto", Sides.Light) {
      Default.copy(lightEdge = GlassLiquidEdges.Today)
    },
    // 2. Il velo (lato chiaro).
    taste("velo-ios26", "velo di iOS 26: bianco al 40%", Sides.Light) { Default.copy(lightVeil = GlassLiquidVeils.Ios26) },
    taste("velo-oggi", "velo di oggi: la pellicola del ruolo dipinta com'e'", todayFilm = true) { Default },
    // 3. L'ombra.
    taste("ombra-ios26", "ombra di iOS 26: larga, una componente") {
      Default.copy(lightShadow = GlassLiquidShadows.Ios26, darkShadow = GlassLiquidShadows.Ios26)
    },
    taste("ombra-oggi", "ombra di oggi: raggio e peso del ruolo, colore del lato, senza crescita") { surface ->
      Default.copy(
        lightShadow = GlassLiquidShadows.today(surface.optics.shadowRadius, surface.optics.shadowAlpha, darkSide = false),
        darkShadow = GlassLiquidShadows.today(surface.optics.shadowRadius, surface.optics.shadowAlpha, darkSide = true),
        growth = Default.growth.copy(shadow = GlassLiquidGrowths.None.shadow),
      )
    },
    taste("ombra-tondo-apple", "ombra dei piccoli come Apple: un quarto di quella della barra") {
      Default.copy(smallShadow = GlassLiquidProfile.SmallShadowLikeApple)
    },
    // 4. La smerigliatura.
    taste("smeriglio-24", "smerigliatura 24 dp, solo raggio") { Default.copy(frost = GlassLiquidFrosts.Radius24) },
    taste("smeriglio-mista", "smerigliatura larga 36 dp con il 12% di nitido") {
      Default.copy(frost = GlassLiquidFrosts.WideMixed)
    },
    taste("smeriglio-ios26", "smerigliatura di iOS 26: 4,5 dp") { Default.copy(frost = GlassLiquidFrosts.Ios26) },
    // 5. La lente, col materiale pieno e nella variante limpida.
    taste("lente-leggera", "lente leggera") { Default.copy(lens = GlassLiquidLenses.Light) },
    taste("lente-marcata", "lente marcata") { Default.copy(lens = GlassLiquidLenses.Strong) },
    taste("limpida-leggera", "lente leggera", variant = GlassLiquidVariant.Clear) {
      Default.copy(lens = GlassLiquidLenses.Light)
    },
    taste("limpida-media", "lente media (di serie)", variant = GlassLiquidVariant.Clear) { Default },
    taste("limpida-marcata", "lente marcata", variant = GlassLiquidVariant.Clear) {
      Default.copy(lens = GlassLiquidLenses.Strong)
    },
    // 6. Il tema scuro.
    taste("scuro-filo", "tema scuro: riflesso e un filo chiaro sottile", Sides.Dark) {
      Default.copy(darkEdge = GlassLiquidEdges.DarkReflexAndWire)
    },
    taste("scuro-oggi", "tema scuro: il bordo di oggi", Sides.Dark) { Default.copy(darkEdge = GlassLiquidEdges.DarkToday) },
    taste("scuro-velo-profondo", "tema scuro: velo piu' profondo", Sides.Dark) {
      Default.copy(darkVeil = GlassLiquidVeils.DarkDeeper)
    },
    // 7. I grandi.
    taste("grandi-barra", "i grandi come una barra: ne' smerigliatura ne' ombra crescono") {
      Default.copy(growth = GlassLiquidGrowths.None)
    },
    taste("grandi-due-passi", "i grandi due passi in piu'") { Default.copy(growth = GlassLiquidGrowths.TwoSteps) },
  )

  /** I campioni: ogni variante sui lati dove esiste, più il vetro di oggi. */
  fun samples(): List<GlassScene> {
    val out = ArrayList<GlassScene>()
    for (taste in Tastes) {
      if (taste.sides != Sides.Dark) out += sample(taste.key, dark = false, taste.ask)
      if (taste.sides != Sides.Light) out += sample(taste.key, dark = true, taste.ask)
    }
    out += sampleToday(dark = false)
    out += sampleToday(dark = true)
    return out
  }

  /** Tutte le scene: le inquadrature rilette da [referenceFolder] (`tools/glassbench/scenes/rif`) e i campioni. */
  fun all(referenceFolder: File): List<GlassScene> =
    References.map { fromReference(it, File(referenceFolder, "$it.json").readText(Charsets.UTF_8)) } + samples()
}

/**
 * Dove finiscono le scene: nella cartella detta da `GLASS_SCENE_DIR`, assoluta o relativa alla radice del
 * repo. Quella del banco è `tools/glassbench/scenes/f1a`:
 *
 * ```
 * $env:GLASS_SCENE_DIR = "tools/glassbench/scenes/f1a"
 * powershell -File artifacts\liquid-glass\tools\build-gradle.ps1 `
 *   -Task ":engine-ui:testDebugUnitTest --tests *GlassSceneExportTest --rerun" -Name lg-...
 * ```
 *
 * **Niente si scrive se la variabile non c'è**: i giri normali dei test compongono e controllano le scene ma
 * non toccano il repo. È una variabile sua, e non `GLASS_TRACE_DIR` delle tracce (il piano, 4.2, diceva «la
 * stessa»): le tracce vanno in `artifacts/`, ignorata da git, le scene in una cartella tracciata, e chi
 * rigenera le une non deve riscrivere le altre.
 */
internal object GlassSceneOutput {

  const val Variable = "GLASS_SCENE_DIR"

  fun requestedDir(value: String? = System.getenv(Variable), root: () -> File = { GlassTraceOutput.repoRoot() }): File? {
    if (value.isNullOrBlank()) return null
    val dir = File(value.trim())
    return if (dir.isAbsolute) dir else File(root(), value.trim())
  }

  /** Scrive [scenes] sotto [dir]; i file scritti. */
  fun write(dir: File, scenes: List<GlassScene>): List<File> = scenes.map { scene ->
    val file = File(dir, scene.path)
    file.parentFile?.mkdirs()
    file.writeText(scene.encode(), Charsets.UTF_8)
    file
  }
}
