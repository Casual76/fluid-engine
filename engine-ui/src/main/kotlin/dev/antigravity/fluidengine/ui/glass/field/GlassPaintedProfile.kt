/*
 * Il VETRO DIPINTO: il vetro di un foglio o di un avviso che sta in un'altra finestra di piattaforma
 * (`ModalBottomSheet`, `Dialog`), dove non c'è niente da catturare (F1B.5, piano «iOS in Android», sezione F1B).
 *
 * Fino alla 4.0 `fluidStaticGlassSurface` aveva numeri suoi: tre gradienti in diagonale (un corpo del colore della
 * superficie al 10–18%, un tratto da 1,35 dp bianco in alto e nero in basso, un secondo tratto 2 dp più dentro),
 * sopra un fondo traslucido al 92% da cui si leggeva, non sfocata, la pagina dietro. Era un vetro di un'altra
 * famiglia: in un'app tutta `Liquid` il solo foglio di piattaforma aveva un bordo disegnato.
 *
 * Qui **non c'è un numero del materiale**: ogni passo si calcola dalla ricetta di serie (`glassRecipe`, cioè da
 * [GlassLiquidProfile.Default]) con le stesse formule degli shader, così se domani cambia il velo, il filo, il
 * riflesso o l'ombra di serie, il dipinto li segue (lo prova `GlassPaintedProfileTest`).
 *  - **corpo**: il velo della ricetta (con l'ispessimento dove lo sfondo gli somiglia, `veilAdapt`) e la tinta
 *    posati sopra ciò che il vetro vero vedrebbe dietro di sé: la pagina sotto lo scrim ([glassPaintedSeen]). È la
 *    formula di `GlassBodyShader` su un colore pieno ([glassPaintedBody]);
 *  - **ombreggiatura** verso il contorno (`bodyShade`, quadratica, pendente verso l'alto), **filo** sul perimetro
 *    (`rimDark`, che cede dove batte la luce di `rimLift`), **riga chiara** subito dentro il filo e **alone** che
 *    rientra per `reflexDepth`, in `Plus`: la formula di `GlassLightShader`, coi suoi lobi, su tratti della sagoma
 *    rientrata ([glassPaintedPlan]);
 *  - **ombra**: è proprio quella del vetro (`Modifier.glassShadow` di `GlassMaterial.kt`, le due maschere di
 *    `GlassShadow.kt` coi numeri della ricetta), messa da chi chiama fuori dal ritaglio.
 *
 * ### Cosa il dipinto non ha, e cosa fa al suo posto
 * Il vetro vero mostra la pagina sfocata, con la lente sul bordo. Qui non c'è una pagina: **non si finge**. Il
 * corpo è il colore **pieno** che il vetro vero ha sopra una pagina media, cioè la pagina dell'engine
 * (`colorScheme.background`: `#F2F2F7` e `#0D0D0F` con la quota d'accento del tema) sotto lo scrim dei modali
 * ([GlassPaintedScrim]). Una pagina uniforme sotto la smerigliatura e la lente resta uniforme: su quella pagina il
 * corpo del vetro vero è *esattamente* questo colore (meno l'ombreggiatura del bordo, che c'è anche qui). Su una
 * foto o su un testo il vetro vero cambia, il dipinto no: è la differenza dichiarata.
 *
 * ### Approssimazioni (a vista entro un livello o due, sul banco: `tools/glassbench/scenes/f1b/dipinto-*`)
 *  - i lobi della luce dipendono dall'angolo del bordo; il dipinto li porta con gradienti **verticali**, che sono
 *    esatti per l'asse di serie (alto e basso, `reflexDir` verticale) e sbagliano un asse in diagonale (il bordo
 *    iOS 26 delle tavole, che non è di serie): lì il dipinto usa la sola componente verticale;
 *  - l'ombreggiatura del corpo è fatta di due fasce, una verticale e una orizzontale: negli angoli si sommano
 *    (lo shader usa una sola distanza dal contorno), ed è al più un 7% di nero sul chiaro;
 *  - l'alone è una pila di tratti di mezzo dp: lo shader lo calcola per pixel.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ------------------------------------------------------------------------------------------------------------
// Lo scrim e la pagina
// ------------------------------------------------------------------------------------------------------------

/**
 * Lo **scrim** dei pannelli dipinti (il foglio di piattaforma di `FluidSheet`, l'avviso nella `Dialog` di
 * `FluidAlert`): un nero di quest'alfa sopra l'app.
 *
 * Sono i livelli dello scrim dei modali di vetro (`FluidGlassModalScrim`, `GlassTint.overlay`: nero al 12% sul
 * chiaro, al 20% sullo scuro), così un foglio di piattaforma e un foglio nell'host abbassano la pagina dello
 * stesso tanto. Prima il foglio aveva un nero al 42% e l'avviso il velo di piattaforma della `Dialog`: tre scrim
 * diversi per la stessa cosa. Lo scrim dell'host in più **smeriglia** la pagina: quello di piattaforma non può, e
 * non lo compensa scurendo di più (sarebbe un altro materiale).
 */
internal object GlassPaintedScrim {
  /** Lato chiaro. */
  const val LightAlpha: Float = 0.12f

  /** Lato scuro. */
  const val DarkAlpha: Float = 0.20f

  fun alpha(darkSide: Boolean): Float = if (darkSide) DarkAlpha else LightAlpha

  fun color(darkSide: Boolean): Color = Color.Black.copy(alpha = alpha(darkSide))
}

/**
 * La pagina di riferimento quando quella del tema non è piena (un'app che mette una pellicola in `background`):
 * le pagine dell'engine senza accento.
 */
internal object GlassPaintedPage {
  val Light: Color = Color(0xFFF2F2F7)
  val Dark: Color = Color(0xFF0D0D0F)

  fun of(darkSide: Boolean): Color = if (darkSide) Dark else Light
}

/**
 * Quanto si fa d'accento la superficie dipinta di un controllo scelto (`fluidStaticGlassSurface(selected = true)`):
 * una pellicola d'accento a quest'alfa al posto del velo. È la stessa della selezione di `glassControlSurface`
 * (la pellicola `overlay` della tinta scelta), che col materiale nuovo prende il posto del velo allo stesso modo.
 */
internal const val GlassPaintedSelectionAlpha: Float = 0.36f

/**
 * Il passo dell'alone, in dp: l'alone dello shader cresce per pixel, il dipinto per tratti di questo spessore.
 * Mezzo dp sono 1–1,5 px: sotto il livello di grigio che l'alone perde da un tratto all'altro (sul chiaro satura
 * comunque al bianco). Non è un numero del materiale.
 */
internal const val GlassPaintedHaloStepDp: Float = 0.5f

/** Il tetto dei tratti dell'alone: un rientro enorme scritto a mano non diventa cento disegni. */
internal const val GlassPaintedHaloMaxSteps: Int = 24

/**
 * Il corpo di un pannello dipinto, per chi ci sta sopra: dentro un foglio o un avviso dipinti un altro vetro
 * dipinto (`fluidStaticGlassSurface`) è un vetro **annidato**, e prende la regola dell'annidato del profilo
 * (velo e ombra ridotti) sopra questo colore invece che sopra la pagina. `null` = sulla pagina.
 */
internal val LocalGlassPaintedPanel: ProvidableCompositionLocal<Color?> = staticCompositionLocalOf { null }

/** Un colore sopra un colore pieno, `SrcOver` con colori non premoltiplicati: il risultato è pieno. */
private fun overOpaque(dst: Color, src: Color, alpha: Float = src.alpha): Color {
  val a = clampGlassUnit(alpha)
  return Color(
    red = dst.red + (src.red - dst.red) * a,
    green = dst.green + (src.green - dst.green) * a,
    blue = dst.blue + (src.blue - dst.blue) * a,
    alpha = 1f,
  )
}

/**
 * Ciò che il vetro vero vedrebbe dietro un pannello modale su [page]: la pagina (piena: se il tema la dà
 * traslucida si posa sulla pagina di riferimento del lato) sotto lo scrim del lato.
 */
internal fun glassPaintedSeen(page: Color, darkSide: Boolean): Color {
  val full = if (page.alpha >= 1f) page else overOpaque(GlassPaintedPage.of(darkSide), page)
  return overOpaque(full, GlassPaintedScrim.color(darkSide))
}

// ------------------------------------------------------------------------------------------------------------
// La ricetta e il corpo
// ------------------------------------------------------------------------------------------------------------

/**
 * La ricetta di un vetro dipinto: quella di un pannello modale vero (`GlassRole.Modal`, `panelOptics`, come il
 * foglio e l'avviso dell'host) della stessa taglia, col profilo di serie.
 *
 * @param nested vero per un vetro dipinto **dentro** un pannello dipinto (un controllo nel foglio).
 * @param film una pellicola che prende il posto del velo (la selezione), o `Unspecified`.
 * @param largeVeil quanto del velo dei grandi; di serie dalla taglia. Fogli e avvisi passano uno: sono grandi
 *   anche quando sono bassi, e il loro colore si sceglie prima della misura (è il `containerColor` del foglio).
 */
internal fun glassPaintedRecipe(
  widthDp: Float,
  heightDp: Float,
  darkSide: Boolean,
  nested: Boolean = false,
  film: Color = Color.Unspecified,
  largeVeil: Float = Float.NaN,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  out: GlassRecipe = GlassRecipe(),
): GlassRecipe = glassRecipe(
  role = GlassRole.Modal,
  widthDp = widthDp,
  heightDp = heightDp,
  darkSide = darkSide,
  optics = GlassDefaults.panelOptics(),
  film = film,
  nested = nested,
  profile = profile,
  largeVeil = largeVeil,
  out = out,
)

/**
 * Il **corpo** del vetro sopra il colore pieno [seen]: la formula di `GlassBodyShader` riga per riga (scurimento
 * della variante limpida, velo che si ispessisce dove lo sfondo gli somiglia, tinta la cui luminosità segue lo
 * sfondo), senza la smerigliatura e la lente, che su un colore pieno non cambiano niente. Il risultato è pieno.
 */
internal fun glassPaintedBody(recipe: GlassRecipe, seen: Color): Color {
  val amount = clampGlassUnit(recipe.materialize)
  val ground = if (seen.alpha >= 1f) seen else overOpaque(Color.Black, seen)
  val dimmed = overOpaque(ground, Color.Black, clampGlassUnit(recipe.dim) * amount)
  val luma = 0.2126f * dimmed.red + 0.7152f * dimmed.green + 0.0722f * dimmed.blue
  val veilColor = recipe.veilColor
  val apart = max(abs(dimmed.red - veilColor.red), max(abs(dimmed.green - veilColor.green), abs(dimmed.blue - veilColor.blue)))
  val likeness = 1f - apart
  val veil = clampGlassUnit(veilColor.alpha * (1f + recipe.veilAdapt * likeness * likeness)) * amount
  var body = overOpaque(dimmed, veilColor, veil)
  val tint = recipe.tintColor
  val follow = 1f - clampGlassUnit(recipe.tintMode) * recipe.tintFollow * (1f - luma)
  val tinting = clampGlassUnit(tint.alpha) * amount
  if (tinting > 0f) {
    body = overOpaque(body, Color(red = tint.red * follow, green = tint.green * follow, blue = tint.blue * follow), tinting)
  }
  return body
}

/**
 * Il corpo di un **pannello** dipinto (foglio, avviso) di lato [darkSide] sopra la pagina [page]: velo dei grandi,
 * pagina sotto lo scrim. Non dipende dalla misura: è il `containerColor` del foglio.
 */
internal fun glassPaintedPanelBody(
  page: Color,
  darkSide: Boolean,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
): Color = glassPaintedBody(
  glassPaintedRecipe(PanelReferenceDp, PanelReferenceDp, darkSide, largeVeil = 1f, profile = profile),
  glassPaintedSeen(page, darkSide),
)

/** La taglia con cui si chiede la ricetta di un pannello prima della sua misura: un pannello grande qualsiasi. */
private const val PanelReferenceDp: Float = 320f

// ------------------------------------------------------------------------------------------------------------
// Il piano: i passi del disegno
// ------------------------------------------------------------------------------------------------------------

/** Come un passo si posa: coprendo (il filo, l'ombreggiatura) o sommando luce (riflesso, alone, bagliore). */
internal enum class GlassPaintedBlend { Over, Plus }

/** La direzione del gradiente di un passo. */
internal enum class GlassPaintedAxis { Vertical, Horizontal }

/**
 * Un passo del dipinto: la sagoma **rientrata** di [inset] px (con gli angoli ridotti dello stesso tanto, come
 * una distanza dal contorno), piena ([stroke] zero) o al tratto largo [stroke] px centrato su quella sagoma, del
 * colore [color] (non premoltiplicato, pieno) con un'alfa che segue [alphas] alle posizioni [positions] (px del
 * pannello, lungo [axis]).
 *
 * @param corners i raggi di **questa** sagoma, px: in alto a sinistra, in alto a destra, in basso a destra, in
 *   basso a sinistra.
 */
internal class GlassPaintedLayer(
  val inset: Float,
  val stroke: Float,
  val color: Color,
  val blend: GlassPaintedBlend,
  val axis: GlassPaintedAxis,
  val positions: FloatArray,
  val alphas: FloatArray,
  val corners: FloatArray,
) {
  /** L'alfa più forte del passo. */
  val peak: Float get() = alphas.maxOrNull() ?: 0f
}

/**
 * Il dipinto di una lastra di [widthPx] × [heightPx]: il [body] pieno e i passi [layers], nell'ordine in cui si
 * disegnano (ombreggiatura, luce che somma, filo: lo stesso ordine del vetro vero, dove il filo copre la luce).
 */
internal class GlassPaintedPlan(
  val widthPx: Float,
  val heightPx: Float,
  val body: Color,
  val layers: List<GlassPaintedLayer>,
)

/**
 * Il piano del dipinto di una lastra con la ricetta [recipe], sopra [seen], grande [widthPx] × [heightPx] a
 * densità [density], con gli angoli [corners] (px: alto-sinistra, alto-destra, basso-destra, basso-sinistra).
 * Puro: lo provano i test e lo scrive il banco.
 */
internal fun glassPaintedPlan(
  recipe: GlassRecipe,
  seen: Color,
  widthPx: Float,
  heightPx: Float,
  density: Float,
  corners: FloatArray,
): GlassPaintedPlan {
  val w = finitePositive(widthPx)
  val h = finitePositive(heightPx)
  val body = glassPaintedBody(recipe, seen)
  val layers = ArrayList<GlassPaintedLayer>()
  if (w <= 0f || h <= 0f || !(density > 0f)) return GlassPaintedPlan(w, h, body, layers)
  val amount = clampGlassUnit(recipe.materialize)

  // ---- L'ombreggiatura del corpo: due fasce, verticale e orizzontale -----------------------------------------
  val shade = recipe.bodyShade
  val shadeAlpha = clampGlassUnit(shade.alpha) * amount
  val depth = min(finitePositive(recipe.bodyShadeDepthDp) * density, min(w, h) / 2f)
  if (shadeAlpha > 0f && depth > 0f) {
    val lx = recipe.bodyShadeLeanX
    val ly = recipe.bodyShadeLeanY
    val lean = 1f + kotlin.math.sqrt(lx * lx + ly * ly)
    // Quanto pende verso ognuno dei quattro lati: `(1 + normale · lean) / (1 + |lean|)`, la formula dello shader.
    fun facing(nx: Float, ny: Float): Float = max((1f + nx * lx + ny * ly) / lean, 0f)
    val shadeColor = shade.copy(alpha = 1f)
    layers += shadeBand(GlassPaintedAxis.Vertical, h, depth, shadeAlpha * facing(0f, -1f), shadeAlpha * facing(0f, 1f), shadeColor, corners)
    layers += shadeBand(GlassPaintedAxis.Horizontal, w, depth, shadeAlpha * facing(-1f, 0f), shadeAlpha * facing(1f, 0f), shadeColor, corners)
  }

  // ---- La luce: riga chiara e alone, in `Plus` ---------------------------------------------------------------
  val rim = finitePositive(recipe.rimWidthDp) * density
  val rimDark = recipe.rimDark
  val wireThere = clampGlassUnit(rimDark.alpha * GlassLightRimGate)
  val lineWidth = rim * GlassLightLineWidth
  val start = rim * wireThere
  val light = recipe.lightColor
  val strength = clampGlassUnit(recipe.reflexStrength) * clampGlassUnit(light.alpha) * amount
  val dirY = if (recipe.reflexDirX == 0f && recipe.reflexDirY == 0f) -1f else recipe.reflexDirY /
    kotlin.math.sqrt(recipe.reflexDirX * recipe.reflexDirX + recipe.reflexDirY * recipe.reflexDirY)
  val halfTop = recipe.reflexHalfWidth
  val halfBottom = recipe.reflexOppositeHalfWidth
  val lightColor = light.copy(alpha = 1f)
  if (strength > 0f && lineWidth > 0f) {
    val floor = clampGlassUnit(recipe.reflexFloor)
    // La riga: subito dentro il filo (dal contorno, se il filo non c'è), larga due fili.
    layers += lobeLayer(
      inset = start + lineWidth / 2f, stroke = lineWidth, w = w, h = h, corners = corners, dirY = dirY,
      halfTop = halfTop, halfBottom = halfBottom, core = GlassLightLobeCore,
      color = lightColor, blend = GlassPaintedBlend.Plus,
    ) { lobe -> strength * (floor + (1f - floor) * lobe) }
    // L'alone: da dove finisce la riga a `reflexDepth`, `(1 − x / depth)^1,5` su lobi più stretti.
    val halo = clampGlassUnit(recipe.reflexHalo)
    val reach = finitePositive(recipe.reflexDepthDp) * density
    val zone = reach - lineWidth
    if (halo > 0f && zone > 0f) {
      val steps = ceil(zone / (GlassPaintedHaloStepDp * density)).toInt().coerceIn(1, GlassPaintedHaloMaxSteps)
      val step = zone / steps
      for (k in 0 until steps) {
        val middle = lineWidth + step * (k + 0.5f)
        val band = (1f - clampGlassUnit(middle / reach)).pow(GlassLightHaloFalloff)
        if (strength * halo * band * 255f < 0.25f) continue
        layers += lobeLayer(
          inset = start + middle, stroke = step, w = w, h = h, corners = corners, dirY = dirY,
          halfTop = halfTop * GlassLightHaloSpan, halfBottom = halfBottom * GlassLightHaloSpan, core = GlassLightHaloCore,
          color = lightColor, blend = GlassPaintedBlend.Plus,
        ) { lobe -> strength * halo * lobe * band }
      }
    }
  }

  // ---- Il filo, che copre ------------------------------------------------------------------------------------
  val rimAlpha = clampGlassUnit(rimDark.alpha) * amount
  if (rimAlpha > 0f && rim > 0f) {
    val lift = recipe.rimLift.coerceIn(-1f, 1f)
    layers += lobeLayer(
      inset = rim / 2f, stroke = rim, w = w, h = h, corners = corners, dirY = dirY,
      halfTop = halfTop, halfBottom = halfBottom, core = GlassLightLobeCore,
      color = rimDark.copy(alpha = 1f), blend = GlassPaintedBlend.Over,
    ) { lobe -> rimAlpha * (if (lift >= 0f) 1f - lift * lobe else 1f + lift * (1f - lobe)) }
  }
  return GlassPaintedPlan(w, h, body, layers)
}

/** Il bagliore uniforme di una superficie dipinta premuta per [pressed]: il bagliore lontano dal dito del vetro. */
internal fun glassPaintedGlow(recipe: GlassRecipe, pressed: Float): Float =
  clampGlassUnit(pressed) * recipe.glowPeak * clampGlassUnit(recipe.glowFill)

private fun finitePositive(value: Float): Float = if (value.isFinite() && value > 0f) value else 0f

/** I raggi della sagoma rientrata di [inset]: ridotti dello stesso tanto, mai oltre metà del lato corto. */
private fun insetCorners(corners: FloatArray, inset: Float, w: Float, h: Float): FloatArray {
  val half = max(min(w, h) / 2f - inset, 0f)
  return FloatArray(4) { i -> (max(corners.getOrElse(i) { 0f } - inset, 0f)).coerceAtMost(half) }
}

/**
 * Una fascia d'ombreggiatura lungo [axis], lunga [length]: `alfa · (1 − d / depth)²` dai due capi ([near] al
 * capo alto o sinistro, [far] all'altro), zero in mezzo; dove le due si toccano, la somma.
 */
private fun shadeBand(
  axis: GlassPaintedAxis,
  length: Float,
  depth: Float,
  near: Float,
  far: Float,
  color: Color,
  corners: FloatArray,
): GlassPaintedLayer {
  fun at(p: Float): Float {
    fun one(d: Float, a: Float) = if (d >= depth) 0f else a * (1f - d / depth).let { it * it }
    return min(one(p, near) + one(length - p, far), 1f)
  }
  // Sedici tratti: con otto, a x4 sul chiaro si leggevano le giunture della parabola (bande di Mach di un livello).
  val samples = 16
  val stops = ArrayList<Float>()
  for (k in 0..samples) stops += depth * k / samples
  for (k in samples downTo 0) stops += length - depth * k / samples
  val positions = increasing(stops.map { it.coerceIn(0f, length) })
  return GlassPaintedLayer(
    inset = 0f, stroke = 0f, color = color, blend = GlassPaintedBlend.Over, axis = axis,
    positions = positions, alphas = FloatArray(positions.size) { at(positions[it]) }, corners = corners.copyOf(),
  )
}

/**
 * Un passo che segue i lobi della luce: lungo la sagoma rientrata di [inset], dall'alto in basso, l'angolo del
 * bordo dalla verticale dà quanto il passo è nel lobo (`glassLightLobe` dello shader); [value] traduce il lobo
 * nell'alfa. Sugli archi degli angoli l'altezza dice l'angolo (`y = r (1 − cos θ)`), sui fianchi il lobo è zero.
 */
private fun lobeLayer(
  inset: Float,
  stroke: Float,
  w: Float,
  h: Float,
  corners: FloatArray,
  dirY: Float,
  halfTop: Float,
  halfBottom: Float,
  core: Float,
  color: Color,
  blend: GlassPaintedBlend,
  value: (Float) -> Float,
): GlassPaintedLayer {
  val radii = insetCorners(corners, inset, w, h)
  val top = max(radii[0], radii[1])
  val bottom = max(radii[2], radii[3])
  fun lobe(facing: Float): Float =
    max(glassPaintedLobe(facing, halfTop, core), glassPaintedLobe(-facing, halfBottom, core))
  val angles = lobeAngles(halfTop, halfBottom, core)
  val ys = ArrayList<Float>(angles.size * 2)
  val vs = ArrayList<Float>(angles.size * 2)
  // Arco alto: la normale è (± sin θ, −cos θ), quindi rispetto all'asse verticale `facing = −cos θ · dirY`.
  for (theta in angles) {
    ys += inset + top * (1f - cos(theta))
    vs += value(lobe(-cos(theta) * dirY))
  }
  // Arco basso: `facing = cos θ · dirY`, dal fianco al fondo.
  for (theta in angles.reversed()) {
    ys += (h - inset) - bottom * (1f - cos(theta))
    vs += value(lobe(cos(theta) * dirY))
  }
  val order = ys.indices.sortedBy { ys[it] }
  val positions = increasing(order.map { ys[it] })
  val alphas = FloatArray(positions.size) { clampGlassUnit(vs[order[it]]) }
  return GlassPaintedLayer(
    inset = inset, stroke = stroke, color = color, blend = blend, axis = GlassPaintedAxis.Vertical,
    positions = positions, alphas = alphas, corners = radii,
  )
}

/** `glassLightLobe` di `GlassLightShader`: pieno fino a `halfWidth · full`, zero da `halfWidth`. */
internal fun glassPaintedLobe(facing: Float, halfWidth: Float, full: Float): Float {
  val low = cos(halfWidth)
  val high = max(cos(halfWidth * full), low + 0.0001f)
  val t = clampGlassUnit((facing - low) / (high - low))
  return t * t * (3f - 2f * t)
}

/** Gli angoli dalla verticale a cui si campionano gli archi: i capi, e fitti dove i lobi cambiano. */
private fun lobeAngles(halfTop: Float, halfBottom: Float, core: Float): List<Float> {
  val quarter = (PI / 2.0).toFloat()
  val set = sortedSetOf(0f, quarter)
  for (half in listOf(halfTop, halfBottom)) {
    if (!half.isFinite() || half <= 0f) continue
    val from = (half * core).coerceIn(0f, quarter)
    val to = half.coerceIn(0f, quarter)
    for (k in 0..8) set += from + (to - from) * k / 8f
  }
  for (k in 1 until 6) set += quarter * k / 6f
  return set.toList()
}

/** Le posizioni di un gradiente, rese strettamente crescenti (due posizioni uguali diventano un gradino netto). */
private fun increasing(values: List<Float>): FloatArray {
  val out = FloatArray(values.size)
  var last = Float.NEGATIVE_INFINITY
  for (i in values.indices) {
    val v = max(values[i], last + 0.001f)
    out[i] = v
    last = v
  }
  return out
}

// ------------------------------------------------------------------------------------------------------------
// Il disegno
// ------------------------------------------------------------------------------------------------------------

/**
 * I raggi di [shape] a [size], px (alto-sinistra, alto-destra, basso-destra, basso-sinistra), come li disegna la
 * forma. Una forma che non ha angoli (una `GenericShape`) vale zero: il dipinto ne rientra la sagoma per misura.
 */
internal fun glassPaintedCorners(shape: Shape, size: Size, density: Density, layoutDirection: LayoutDirection): FloatArray {
  if (shape !is CornerBasedShape) return FloatArray(4)
  val ts = shape.topStart.toPx(size, density)
  val te = shape.topEnd.toPx(size, density)
  val be = shape.bottomEnd.toPx(size, density)
  val bs = shape.bottomStart.toPx(size, density)
  val rtl = layoutDirection == LayoutDirection.Rtl
  val raw = if (rtl) floatArrayOf(te, ts, bs, be) else floatArrayOf(ts, te, be, bs)
  return insetCorners(raw, 0f, size.width, size.height)
}

/** La sagoma di [shape] rientrata come il passo [layer]: gli angoli del passo, alla misura rientrata. */
private fun paintedOutline(
  shape: Shape,
  layer: GlassPaintedLayer?,
  size: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): Outline {
  val inset = layer?.inset ?: 0f
  val inner = Size(max(size.width - inset * 2f, 0f), max(size.height - inset * 2f, 0f))
  if (layer == null || inset == 0f || shape !is CornerBasedShape) return shape.createOutline(inner, layoutDirection, density)
  val c = layer.corners
  // `copy` vuole gli angoli per inizio e fine: in RTL l'inizio è a destra.
  val rtl = layoutDirection == LayoutDirection.Rtl
  val resized = shape.copy(
    topStart = CornerSize(if (rtl) c[1] else c[0]),
    topEnd = CornerSize(if (rtl) c[0] else c[1]),
    bottomEnd = CornerSize(if (rtl) c[3] else c[2]),
    bottomStart = CornerSize(if (rtl) c[2] else c[3]),
  )
  return resized.createOutline(inner, layoutDirection, density)
}

/**
 * Il **vetro dipinto** di [shape]: corpo, ombreggiatura, luce e filo del piano ([glassPaintedPlan]) calcolati
 * dalla ricetta di serie alla taglia del nodo. Disegna **dietro** il contenuto. L'ombra no: la mette chi chiama,
 * con `glassShadow`, fuori dal ritaglio.
 *
 * @param seen ciò che starebbe dietro il vetro vero (la pagina sotto lo scrim, o il corpo del pannello dipinto
 *   che lo porta).
 * @param drawBody falso per non posare il corpo (lo dipinge già il contenitore: il `containerColor` del foglio).
 * @param extendTop di quanti px la sagoma dipinta sale **sopra** il nodo, letto in disegno: il contenuto del
 *   foglio di piattaforma sta sotto la maniglia, e il vetro del foglio comincia sopra di lei.
 * @param extendBottomPx di quanti px la sagoma scende sotto il nodo (il fondo del foglio sta oltre lo schermo).
 */
internal fun Modifier.glassPainted(
  shape: Shape,
  darkSide: Boolean,
  seen: Color,
  nested: Boolean = false,
  film: Color = Color.Unspecified,
  largeVeil: Float = Float.NaN,
  drawBody: Boolean = true,
  pressure: () -> Float = { 0f },
  extendTop: () -> Float = { 0f },
  extendBottomPx: Float = 0f,
): Modifier = this.drawWithCache {
  val top = max(extendTop().let { if (it.isFinite()) it else 0f }, 0f)
  val area = Size(size.width, size.height + top + max(extendBottomPx, 0f))
  val profile = GlassLiquidProfile.Default
  val recipe = glassPaintedRecipe(
    widthDp = area.width / density,
    heightDp = area.height / density,
    darkSide = darkSide,
    nested = nested,
    film = if (film.isSpecified) film else Color.Unspecified,
    largeVeil = largeVeil,
    profile = profile,
  )
  val corners = glassPaintedCorners(shape, area, this, layoutDirection)
  val plan = glassPaintedPlan(recipe, seen, area.width, area.height, density, corners)
  val bodyOutline = shape.createOutline(area, layoutDirection, this)
  val outlines = plan.layers.map { paintedOutline(shape, it, area, this, layoutDirection) }
  val brushes = plan.layers.map { layer ->
    val stops = Array(layer.positions.size) { i ->
      val length = if (layer.axis == GlassPaintedAxis.Vertical) area.height else area.width
      (layer.positions[i] / length).coerceIn(0f, 1f) to layer.color.copy(alpha = layer.alphas[i])
    }
    // Il gradiente è in coordinate del pannello; la sagoma del passo si disegna spostata di `inset`.
    if (layer.axis == GlassPaintedAxis.Vertical) {
      Brush.verticalGradient(*stops, startY = -layer.inset, endY = area.height - layer.inset)
    } else {
      Brush.horizontalGradient(*stops, startX = -layer.inset, endX = area.width - layer.inset)
    }
  }
  onDrawBehind {
    translate(0f, -top) {
      if (drawBody) drawOutline(bodyOutline, plan.body)
      for (i in plan.layers.indices) {
        val layer = plan.layers[i]
        if (layer.peak <= 0f) continue
        translate(layer.inset, layer.inset) {
          drawOutline(
            outline = outlines[i],
            brush = brushes[i],
            style = if (layer.stroke > 0f) Stroke(width = layer.stroke) else Fill,
            blendMode = if (layer.blend == GlassPaintedBlend.Plus) BlendMode.Plus else BlendMode.SrcOver,
          )
        }
      }
      val glow = glassPaintedGlow(recipe, pressure())
      if (glow > 0f) drawOutline(bodyOutline, recipe.lightColor.copy(alpha = glow), blendMode = BlendMode.Plus)
    }
  }
}

/** Per i test: la posizione del gradiente [layer] a [y] px del pannello, l'alfa interpolata. */
internal fun GlassPaintedLayer.alphaAt(position: Float): Float {
  if (positions.isEmpty()) return 0f
  if (position <= positions.first()) return alphas.first()
  if (position >= positions.last()) return alphas.last()
  for (i in 1 until positions.size) {
    if (position <= positions[i]) {
      val t = (position - positions[i - 1]) / (positions[i] - positions[i - 1])
      return alphas[i - 1] + (alphas[i] - alphas[i - 1]) * t
    }
  }
  return alphas.last()
}
