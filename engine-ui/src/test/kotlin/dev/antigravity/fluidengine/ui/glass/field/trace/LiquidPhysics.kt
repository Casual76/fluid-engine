package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.fluidPressedBounds
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLargeVeil
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLiquidRecipes
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.physicsFormShortPx
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidInputs
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidRecipe
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRecipeShortPx
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import kotlin.math.floor
import kotlin.math.max

/*
 * Una superficie di Fluid-physics disegnata dal MATERIALE NUOVO (F1B.1), come elemento `kind: "material"` del
 * banco. È il gemello di `todayPhysicsElement` (`GlassTraceToday.kt`), con una differenza che conta: quello
 * **ricostruisce** gli uniform delle due lenti di oggi, questo **non ricostruisce niente**. I numeri sono quelli
 * che l'engine imposta: `physicsLiquidRecipe` (la ricetta del fotogramma) e `physicsLiquidInputs` (pezzi, anello,
 * dove sta la luce), le stesse due funzioni che chiama `PhysicsLiquidSurface`. Qui c'è solo la scrittura in JSON.
 *
 * Cosa il banco NON rende di una superficie fisica, dichiarato:
 *  - l'**ombra**. Nel ramo `Liquid` è ancora quella di prima (`PhysicsShadowLayers`), che il banco non ha mai
 *    saputo rendere, e la sua parità con l'ombra del materiale è del pacchetto F1B.2. Per questo le lastre ferme
 *    di queste scene e di queste tracce sono scritte **senza ombra** anche loro ([withoutShadow]): ciò che si
 *    confronta fra riposo e viaggio è corpo, velo, smerigliatura, filo e riflesso;
 *  - il **ritaglio hardware**: quello di una superficie fisica a `Full` è il rettangolo del nodo, quindi `clip`
 *    falso, e la sagoma la ritaglia il corpo.
 */

/** Una tinta di serie dell'engine per il materiale nuovo: `Neutral`, cioè il velo lo decide il profilo. */
internal val LiquidNeutralTint: GlassTint =
  GlassTint(overlay = Color.Transparent, fallback = Color.Transparent, hairline = Color.Transparent, mode = GlassTintMode.Neutral)

/** Gli uniform della luce in corpo e dell'ombra in viaggio, che ogni corpo dichiara: nell'ordine dello shader. */
private fun MutableMap<String, Any?>.putBodyLight(inputs: GlassDrawInputs, density: Float, origin: Offset) {
  this["lightInBody"] = if (inputs.lightInBody) 1 else 0
  // Il margine d'angolo è una lunghezza: in dp, così una resa a un'altra densità resta giusta.
  this["nodeClip"] = listOf(if (inputs.nodeClip) 1 else 0, glassDp(inputs.nodeClipCorner / density))
  this["rimWidth"] = glassDp(inputs.bodyRimWidth / density)
  this["rimDark"] = glassColor(inputs.rimDark)
  this["rimFeather"] = inputs.rimFeather
  this["rimLift"] = inputs.rimLift
  this["reflexDir"] = listOf(inputs.reflexDirX, inputs.reflexDirY)
  this["reflexWidths"] = listOf(inputs.reflexHalfWidth, inputs.reflexOppositeHalfWidth)
  this["reflexStrength"] = inputs.reflexStrength
  this["reflexDepth"] = glassDp(inputs.bodyReflexDepth / density)
  this["reflexFloor"] = inputs.reflexFloor
  this["reflexHalo"] = inputs.reflexHalo
  this["glowCenter"] = listOf(glassDp((inputs.bodyGlowCenterX - origin.x) / density), glassDp((inputs.bodyGlowCenterY - origin.y) / density))
  this["glowRadius"] = glassDp(inputs.bodyGlowRadius / density)
  this["glow"] = inputs.glow
  this["glowFill"] = inputs.glowFill
  this["lightColor"] = glassColor(inputs.lightColor)
  // L'ombra in corpo (F1B.2): i numeri che l'engine imposta, lunghezze in dp (la cattura di una superficie fisica è a
  // scala 1, quindi un raggio in pixel della cattura diviso la densità è il raggio in dp). Zero dove è spenta.
  this["shadowOffset"] = listOf(glassDp(inputs.shadowOffsetX / density), glassDp(inputs.shadowOffsetY / density))
  this["shadowRadii"] = listOf(glassDp(inputs.shadowContactRadius / density), glassDp(inputs.shadowAmbientRadius / density))
  this["shadowAlphas"] = listOf(inputs.shadowContactAlpha, inputs.shadowAmbientAlpha)
  this["shadowColor"] = glassColor(inputs.shadowColor)
}

/** I pezzi di un piano del campo come uniform del banco: otto caselle, quelle vuote a zero. */
private fun MutableMap<String, Any?>.putPieces(plan: GlassFieldPlan, density: Float, origin: Offset) {
  val n = plan.pieces
  this["offset"] = "\$offset"
  this["flex"] = listOf(1, 0, 0, 1)
  this["flexCenter"] = listOf(0, 0)
  this["pieceCount"] = plan.count
  this["pieceRect"] = List(n) { i ->
    val at = i * GlassFieldGeometry.FloatsPerPiece
    if (i >= plan.count) {
      listOf(0, 0, 0, 0)
    } else {
      listOf(
        glassDp((plan.rects[at] - origin.x) / density),
        glassDp((plan.rects[at + 1] - origin.y) / density),
        glassDp(plan.rects[at + 2] / density),
        glassDp(plan.rects[at + 3] / density),
      )
    }
  }
  this["pieceRadii"] = List(n) { i ->
    val at = i * GlassFieldGeometry.FloatsPerPiece
    if (i >= plan.count) listOf(0, 0, 0, 0) else List(4) { j -> glassDp(plan.radii[at + j] / density) }
  }
  this["pieceBlend"] = List(n) { i -> if (i >= plan.count) 0 else glassDp(plan.blend[i] / density) }
  this["pieceSmooth"] = List(n) { i -> if (i >= plan.count) 0f else plan.smooth[i] }
  // Il nucleo a otto pesa normale e centro su un raggio suo (F1B.2); quello a un pezzo non lo dichiara.
  if (n > 1) this["pieceSoft"] = List(n) { i -> if (i >= plan.count) 0 else glassDp(plan.soft[i] / density) }
}

/**
 * Gli ingressi del disegno di [plan] con [recipe], come li calcola la superficie: cattura a risoluzione piena
 * (`PhysicsLiquidCaptureScale`), margine fisso del viaggio.
 */
internal fun liquidPhysicsInputs(
  plan: PhysicsRenderPlan,
  recipe: GlassRecipe,
  density: Float,
  paddingDp: Float,
  rest: Boolean,
  /**
   * Vero (F1B.2) per l'ombra in corpo che l'engine disegna; falso per le scene e le tracce di F1B.1, scritte
   * **senza ombra** di proposito (la ricetta perde le due alfa prima di diventare ingressi, come una superficie
   * senza ombra: nessun uniform d'ombra, la fascia dell'anello quella dell'antialias).
   */
  shadow: Boolean = false,
): GlassDrawInputs {
  if (!shadow) {
    recipe.shadowContactAlpha = 0f
    recipe.shadowAmbientAlpha = 0f
  }
  return physicsLiquidInputs(
    plan = plan,
    recipe = recipe,
    scale = 1f,
    screenDensity = density,
    paddingPx = floor(max(paddingDp * density, 0f)),
    out = GlassDrawInputs(GlassFieldMaxPieces),
  )
}

/**
 * L'elemento `material` di una superficie fisica in un fotogramma: il palco ([stage], pixel dell'host; di serie
 * tutta la tela) e dentro il campo, coi numeri di [inputs].
 *
 * @param origin dove sta l'angolo della tela nell'host, in pixel: le coordinate del piano sono dell'host.
 */
internal fun liquidPhysicsElement(
  name: String,
  inputs: GlassDrawInputs,
  origin: Offset,
  density: Float,
  canvasWidthDp: Float,
  canvasHeightDp: Float,
  content: List<Map<String, Any?>> = emptyList(),
  stage: Rect? = null,
  note: String = "",
  /**
   * Vero per una scena **ferma** (`glassbench.py render`): il banco fermo non conosce le potenze della densità
   * (`"16dp^2"`), che sono del banco di moto, quindi un'area si scrive già in pixel alla densità della scena.
   */
  still: Boolean = false,
): LinkedHashMap<String, Any?> {
  val d = density
  val box = stage ?: Rect(origin.x, origin.y, origin.x + canvasWidthDp * d, origin.y + canvasHeightDp * d)
  // Il sistema del campo ha l'origine nell'angolo dell'elemento: i punti si scrivono rispetto a lui.
  val corner = box.topLeft
  val element = LinkedHashMap<String, Any?>()
  element["kind"] = "material"
  element["name"] = name
  if (note.isNotEmpty()) element["_ricetta"] = note
  element["x"] = round3((box.left - origin.x) / d)
  element["y"] = round3((box.top - origin.y) / d)
  element["w"] = round3(box.width / d)
  element["h"] = round3(box.height / d)
  element["radius"] = 0
  element["clip"] = false
  element["capture"] = linkedMapOf(
    "saturation" to inputs.saturation,
    "blurDp" to inputs.blurPx / d,
    "w" to inputs.mix,
    "paddingDp" to inputs.paddingPx / d,
    "scale" to 1,
  )

  val ring = inputs.kernel == GlassBodyKernel.Ring
  val body = LinkedHashMap<String, Any?>()
  if (ring) {
    val r = inputs.ring
    body["offset"] = "\$offset"
    body["flex"] = listOf(1, 0, 0, 1)
    body["flexCenter"] = listOf(0, 0)
    body["vertCount"] = r.count
    body["verts"] = List(r.verts.size / 2) { i ->
      if (i >= r.count) listOf(0, 0)
      else listOf(glassDp((r.verts[i * 2] - corner.x) / d), glassDp((r.verts[i * 2 + 1] - corner.y) / d))
    }
    body["bbox"] = listOf(
      glassDp((r.bbox[0] - corner.x) / d), glassDp((r.bbox[1] - corner.y) / d),
      glassDp((r.bbox[2] - corner.x) / d), glassDp((r.bbox[3] - corner.y) / d),
    )
    body["normalSmooth"] = if (still) r.normalSmooth else glassDpPower((r.normalSmooth / (d * d)).toDouble(), 2)
    body["soften"] = glassDp(r.soften / d)
    body["fieldCenter"] = listOf(glassDp((r.centerX - corner.x) / d), glassDp((r.centerY - corner.y) / d))
    if (r.dropReach >= 0f) {
      body["restCenter"] = listOf(glassDp((r.restCenterX - corner.x) / d), glassDp((r.restCenterY - corner.y) / d))
      body["restHalfSize"] = listOf(glassDp(r.restHalfWidth / d), glassDp(r.restHalfHeight / d))
      body["restRadii"] = List(4) { glassDp(r.restRadii[it] / d) }
      body["restSmooth"] = r.restSmooth
      body["restBlend"] = r.restBlend
      body["dropReach"] = glassDp(r.dropReach / d)
    } else {
      body["restCenter"] = listOf(0, 0)
      body["restHalfSize"] = listOf(0, 0)
      body["restRadii"] = listOf(0, 0, 0, 0)
      body["restSmooth"] = 0
      body["restBlend"] = 0
      body["dropReach"] = -1
    }
    body["ringBand"] = listOf(glassDp(r.bandOuter / d), glassDp(r.bandInner / d))
  } else {
    body.putPieces(inputs.body, d, corner)
  }
  body["thickness"] = glassDp(inputs.thickness / d)
  body["refraction"] = glassDp(inputs.refraction / d)
  body["dome"] = inputs.dome
  body["dispersion"] = inputs.dispersion
  body["magnify"] = inputs.magnify
  body["materialize"] = inputs.materialize
  body["veilColor"] = glassColor(inputs.veilColor)
  body["veilAdapt"] = inputs.veilAdapt
  body["tintColor"] = glassColor(inputs.tintColor)
  body["tintMode"] = inputs.tintMode
  body["tintFollow"] = inputs.tintFollow
  body["bodyShade"] = glassColor(inputs.bodyShade)
  body["bodyShadeDepth"] = glassDp(inputs.bodyShadeDepth / d)
  body["bodyShadeLean"] = listOf(inputs.bodyShadeLeanX, inputs.bodyShadeLeanY)
  body["dim"] = inputs.dim
  body["grain"] = inputs.grain
  body["aa"] = inputs.aa
  body.putBodyLight(inputs, d, corner)
  element["body"] = linkedMapOf(
    "source" to if (ring) "kotlin:GlassBodyRingShaderString" else "kotlin:GlassBody8ShaderString",
    "uniforms" to body,
  )

  // La luce sul nodo: solo per le lastre ferme, nei due disegni di `drawGlassLight`.
  if (!ring && !inputs.lightInBody) {
    fun light(part: Int): LinkedHashMap<String, Any?> {
      val u = LinkedHashMap<String, Any?>()
      u.putPieces(inputs.light, d, corner)
      u["lightPart"] = part
      u["rimWidth"] = glassDp(inputs.rimWidth / d)
      u["rimDark"] = glassColor(inputs.rimDark)
      u["rimFeather"] = inputs.rimFeather
      u["rimLift"] = inputs.rimLift
      u["reflexDir"] = listOf(inputs.reflexDirX, inputs.reflexDirY)
      u["reflexWidths"] = listOf(inputs.reflexHalfWidth, inputs.reflexOppositeHalfWidth)
      u["reflexStrength"] = inputs.reflexStrength
      u["reflexDepth"] = glassDp(inputs.reflexDepth / d)
      u["reflexFloor"] = inputs.reflexFloor
      u["reflexHalo"] = inputs.reflexHalo
      u["glowCenter"] = listOf(glassDp((inputs.glowCenterX - corner.x) / d), glassDp((inputs.glowCenterY - corner.y) / d))
      u["glowRadius"] = glassDp(inputs.glowRadius / d)
      u["glow"] = inputs.glow
      u["glowFill"] = inputs.glowFill
      u["lightColor"] = glassColor(inputs.lightColor)
      u["materialize"] = inputs.lightMaterialize
      u["aa"] = inputs.lightAa
      val layer = LinkedHashMap<String, Any?>()
      layer["source"] = "kotlin:GlassLight8ShaderString"
      if (part == 0) layer["blend"] = "Plus"
      layer["uniforms"] = u
      return layer
    }
    val lights = ArrayList<Map<String, Any?>>()
    if (inputs.drawsGlowing) lights += light(0)
    if (inputs.drawsRim) lights += light(1)
    if (lights.isNotEmpty()) element["light"] = lights
  }
  if (content.isNotEmpty()) {
    // Il contenuto arriva nelle coordinate della tela; il banco lo posa rispetto all'elemento. Con un palco che non
    // comincia nell'angolo della tela (la barra di F1B.2, col nodo allargato) va riportato al suo angolo, ritaglio
    // dello strato compreso. Con il palco di serie lo scarto è zero e niente cambia.
    val dx = (box.left - origin.x) / d
    val dy = (box.top - origin.y) / d
    element["content"] = if (dx == 0f && dy == 0f) content else content.map { layer ->
      LinkedHashMap(layer).also {
        (layer["x"] as? Number)?.let { x -> it["x"] = round3(x.toFloat() - dx) }
        (layer["y"] as? Number)?.let { y -> it["y"] = round3(y.toFloat() - dy) }
        @Suppress("UNCHECKED_CAST")
        (layer["clip"] as? Map<String, Any?>)?.let { clip ->
          it["clip"] = LinkedHashMap(clip).also { c ->
            c["x"] = round3((clip.getValue("x") as Number).toFloat() - dx)
            c["y"] = round3((clip.getValue("y") as Number).toFloat() - dy)
          }
        }
      }
    }
    element["contentClip"] = "alpha"
  }
  return element
}

/** Una lastra ferma del materiale nuovo senza la sua ombra: vedi la nota in testa. */
internal fun withoutShadow(element: LinkedHashMap<String, Any?>): LinkedHashMap<String, Any?> {
  element.remove("shadow")
  return element
}

/**
 * Chi veste una traccia: il vetro di oggi (F0.5) o il materiale nuovo (F1B.1). La corsa (geometria, molle,
 * tempi, contenuto) è la stessa funzione; cambia solo quale elemento del banco si scrive per il tasto fermo e per
 * la superficie fisica.
 */
internal enum class TraceDress { Today, Liquid }

/**
 * Il tasto di vetro fermo o premuto col materiale nuovo, come lo disegna `glassControlSurface`: la ricetta del
 * ruolo `Interactive` con l'ottica dei tasti, alla taglia di riposo; l'elemento alla taglia premuta (lo stesso
 * compromesso dichiarato di `LiquidTasto`). Cattura a risoluzione piena, perché un tasto ha un `layerBlock`.
 */
internal fun liquidButtonElement(
  name: String,
  rest: Rect,
  transform: FluidPressTransform,
  pressed: Float,
  origin: Offset,
  density: Float,
  content: List<Map<String, Any?>>,
  darkSide: Boolean = false,
  /** Vero (F1B.2): il tasto porta la sua ombra a riposo, le due maschere della ricetta. Falso per F1B.1. */
  shadow: Boolean = false,
): Map<String, Any?> {
  val d = density
  val shown = fluidPressedBounds(rest, transform)
  val recipe = glassRecipe(
    role = GlassRole.Interactive,
    widthDp = rest.width / d,
    heightDp = rest.height / d,
    darkSide = darkSide,
    optics = GlassDefaults.buttonOptics().sanitized(),
    pressed = pressed.coerceIn(0f, 1f),
  )
  val shape = linkedMapOf<String, Any?>(
    "name" to name,
    "x" to round3((shown.left - origin.x) / d),
    "y" to round3((shown.top - origin.y) / d),
    "w" to round3(shown.width / d),
    "h" to round3(shown.height / d),
    "radius" to "capsule",
  )
  val made = liquidElement(shape, recipe, theme = null, note = "Interactive, tasto, pressed ${round3(pressed)}")
  val element = if (shadow) made else withoutShadow(made)
  @Suppress("UNCHECKED_CAST")
  (element["capture"] as MutableMap<String, Any?>)["scale"] = 1
  completeRestBody(element)
  // Il contenuto arriva nelle coordinate della tela (come per l'elemento di oggi, che la copre tutta); questo
  // elemento sta sul tasto, e il banco posa il contenuto rispetto all'elemento: si riporta al suo angolo.
  if (content.isNotEmpty()) {
    val left = (shape.getValue("x") as Number).toDouble()
    val top = (shape.getValue("y") as Number).toDouble()
    element["content"] = content.map { layer ->
      LinkedHashMap(layer).also {
        it["x"] = round3(((layer.getValue("x") as Number).toDouble() - left).toFloat())
        it["y"] = round3(((layer.getValue("y") as Number).toDouble() - top).toFloat())
      }
    }
  }
  return element
}

/** Gli uniform della luce che il corpo dichiara anche lui (la luce in corpo), nell'ordine dello shader. */
private val RestBodyLightKeys = listOf(
  "rimWidth", "rimDark", "rimFeather", "rimLift", "reflexDir", "reflexWidths", "reflexStrength", "reflexDepth",
  "reflexFloor", "reflexHalo", "glowCenter", "glowRadius", "glow", "glowFill", "lightColor",
)

/**
 * Completa il corpo di una lastra ferma per una **traccia**: il corpo dichiara anche la luce in corpo (spenta,
 * `lightInBody` 0), l'ombra in viaggio (alfa zero) e il ritaglio emulato (`nodeClip`, zero: una lastra ha quello vero
 * del suo nodo). Lo scrittore delle scene ferme non li scrive perché il banco fermo li tollera; il validatore del
 * movimento (`glassmotion.py validate`) chiede ogni uniform dichiarato. È ciò che `LiquidTasto` fa per la sua traccia.
 */
@Suppress("UNCHECKED_CAST")
internal fun completeRestBody(element: Map<String, Any?>) {
  val body = (element["body"] as? Map<String, Any?>)?.get("uniforms") as? MutableMap<String, Any?> ?: return
  val light = (element["light"] as? List<Map<String, Any?>>)?.firstOrNull()?.get("uniforms") as? Map<String, Any?>
  if (light != null) for (key in RestBodyLightKeys) if (key !in body && key in light) body[key] = light[key]
  body.putIfAbsent("shadowOffset", listOf(glassDp(0f), glassDp(0f)))
  body.putIfAbsent("shadowRadii", listOf(glassDp(0f), glassDp(0f)))
  body.putIfAbsent("shadowAlphas", listOf(0, 0))
  body.putIfAbsent("shadowColor", "#00000000")
  body.putIfAbsent("nodeClip", listOf(0, 0))
}

/**
 * Lo stato di una superficie fisica `Liquid` lungo una traccia: il materiale fra partenza e arrivo, le tinte, il
 * margine fisso. Un fotogramma è [element].
 */
internal class LiquidPhysicsDress(
  private val role: GlassRole,
  private val material: PhysicsMaterialSource,
  private val density: Float,
  private val darkSide: Boolean = false,
  private val profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  /** Vero (F1B.2): l'ombra in corpo dell'engine. Falso: le tracce di F1B.1, senza ombra. */
  private val shadow: Boolean = false,
) {
  val paddingDp: Float = glassTravelPaddingDp(role, material.target, material.origin, profile)
  private val recipes = PhysicsLiquidRecipes()
  private val follower = PhysicsLargeVeil()

  /**
   * Il peso del velo dei grandi all'istante [timeMs] del fotogramma, come lo calcola `PhysicsLiquidSurface.fill`
   * ([PhysicsLargeVeil], F1B.2); non finito = dalla taglia, come le tracce di F1B.1.
   */
  private fun largeVeil(state: FluidPhysicsState, plan: PhysicsRenderPlan, timeMs: Double): Float {
    if (!timeMs.isFinite()) return Float.NaN
    val now = glassLiquidLargeVeilGain(physicsRecipeShortPx(plan) / density)
    val origin = state.journeyOriginNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it) / density) }
    val target = state.journeyTargetNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it) / density) }
    return follower.next(origin ?: now, target ?: now, (timeMs * 1_000_000.0).toLong())
  }

  /** La ricetta del fotogramma, come la chiede `PhysicsLiquidSurface.fill`. */
  fun recipe(
    plan: PhysicsRenderPlan,
    opticsBlend: Float,
    motion: Float,
    largeVeil: Float = Float.NaN,
  ): GlassRecipe = physicsLiquidRecipe(
    role = role,
    material = material.at(opticsBlend),
    shortDp = physicsRecipeShortPx(plan) / density,
    darkSide = darkSide,
    amount = 1f,
    quality = 1f,
    press = 0f,
    tint = LiquidNeutralTint,
    tintFrom = null,
    tintBlend = 1f,
    nested = false,
    motion = motion,
    profile = profile,
    scratch = recipes,
    largeVeil = largeVeil,
  )

  fun element(
    name: String,
    state: FluidPhysicsState,
    plan: PhysicsRenderPlan,
    opticsBlend: Float,
    origin: Offset,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    content: List<Map<String, Any?>>,
    /** Il nodo della superficie, in pixel dell'host; di serie tutta la tela (un palco grande quanto la finestra). */
    stage: Rect? = null,
    /** L'istante del fotogramma, ms (F1B.2: il velo dei grandi lo segue nel tempo); non finito = dalla taglia. */
    timeMs: Double = Double.NaN,
  ): Map<String, Any?> {
    val recipe = recipe(plan, opticsBlend, state.liquid.lens, largeVeil(state, plan, timeMs))
    val rest = state.restFormNow() != null
    val inputs = liquidPhysicsInputs(plan, recipe, density, paddingDp, rest, shadow)
    return liquidPhysicsElement(
      name = name,
      inputs = inputs,
      origin = origin,
      density = density,
      canvasWidthDp = canvasWidthDp,
      canvasHeightDp = canvasHeightDp,
      content = content,
      stage = stage,
      note = if (rest) "fisica ferma" else "fisica in viaggio, luce in corpo",
    )
  }
}
