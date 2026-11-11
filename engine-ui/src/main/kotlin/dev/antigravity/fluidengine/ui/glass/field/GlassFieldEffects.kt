/*
 * L'INNESTO del materiale `Liquid` nel nodo del vetro: come una ricetta ([glassRecipe]) e una forma diventano
 * la catena di `RenderEffect` del corpo, i due disegni della luce e l'ombra di una lastra ferma.
 *
 * Tre cose, dalla più pura alla più legata alla piattaforma:
 *  - [GlassDrawInputs] e [glassDrawInputs]: **i numeri così come arrivano allo shader** (pixel dello spazio in
 *    cui ogni programma gira, colori, geometria). Sono puri, ed è qui che il test di parità guarda: ciò che
 *    le scene del banco scrivono dalla ricetta deve coincidere con ciò che l'engine imposta (piano 4.4);
 *  - [glassBody] e [drawGlassLight]: gli stessi numeri messi negli uniform, la catena a monte, i due disegni;
 *  - [GlassLiquidSurface]: lo stato di una superficie, che `glassSurface` ricorda e che dà al nodo le sue
 *    lambda. Tutto ciò che si riusa da un fotogramma all'altro (ricette, array, la catena a monte, l'ombra)
 *    sta lì: nel disegno non si alloca.
 *
 * L'ordine dei pixel è quello del piano (3.3): l'ombra prima del nodo e fuori dal suo ritaglio; la cattura
 * con `ColorFilter(saturazione) -> Blur(Clamp) -> mescola w -> corpo`, **un layer solo** (cancello C1); poi
 * la luce, fuori dal materiale esportato, sotto il contenuto.
 */
package dev.antigravity.fluidengine.ui.glass.field

import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.asComposeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.fitToTexture
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------------------------------
// Gli ingressi del disegno: puri
// ------------------------------------------------------------------------------------------------------------

/**
 * Ciò che l'innesto passa al disegno di una lastra ferma, **nelle unità di chi lo riceve**.
 *
 * Due spazi, e la differenza è tutto il motivo per cui questo tipo esiste:
 *  - il **corpo** gira sulla cattura, che è più piccola dello schermo (`backdropScale`): le sue lunghezze sono
 *    dp per la densità *dello scope degli effetti*, che è già moltiplicata per la frazione di risoluzione;
 *  - la **luce** si disegna sul nodo, a risoluzione piena: dp per la densità dello schermo.
 *
 * Sbagliare spazio non lancia e non si vede in un test della ricetta: dà un filo spesso il doppio su una
 * lastra a mezza risoluzione. Per questo i numeri passano tutti di qui, e [glassDrawInputs] li dà a chi li
 * vuole confrontare.
 *
 * È un contenitore che la superficie riusa: le due metà ([setBody], [setLight]) si riscrivono per intero.
 */
internal class GlassDrawInputs {

  // ---- La catena a monte (spazio della cattura) ------------------------------------------------------------

  var saturation: Float = 1f

  /** Il raggio di sfocatura, in pixel della cattura. */
  var blurPx: Float = 0f

  /** La mescola `w`: quanto del nitido resta. */
  var mix: Float = 1f

  /**
   * Il margine della cattura, in pixel suoi, **intero**: il layer si posa a `-padding.toInt()` e il contenuto
   * si registra traslato di `padding`, quindi una frazione sposterebbe il vetro di quella frazione rispetto
   * al nodo. Troncato, come fa il banco.
   */
  var paddingPx: Float = 0f

  // ---- Il corpo (spazio della cattura) ---------------------------------------------------------------------

  /** I pezzi come li riceve il corpo. */
  val body: GlassFieldPlan = GlassFieldPlan(pieces = 1)
  var bodyOffsetX: Float = 0f
  var bodyOffsetY: Float = 0f
  var thickness: Float = 0f
  var refraction: Float = 0f
  var dome: Float = 0f
  var dispersion: Float = 0f
  var materialize: Float = 0f
  var veilColor: Color = Color.Transparent
  var veilAdapt: Float = 0f
  var tintColor: Color = Color.Transparent
  var tintMode: Float = 0f
  var tintFollow: Float = 0f
  var bodyShade: Color = Color.Transparent
  var bodyShadeDepth: Float = 0f
  var bodyShadeLeanX: Float = 0f
  var bodyShadeLeanY: Float = 0f
  var dim: Float = 0f
  var grain: Float = 0f
  var aa: Float = GlassLiquidAa

  // ---- La luce (pixel del nodo) ----------------------------------------------------------------------------

  /** I pezzi come li riceve la luce: gli stessi del corpo, a risoluzione piena. */
  val light: GlassFieldPlan = GlassFieldPlan(pieces = 1)
  var rimWidth: Float = 0f
  var rimDark: Color = Color.Transparent
  var rimFeather: Float = 0f
  var rimLift: Float = 0f
  var reflexDirX: Float = 0f
  var reflexDirY: Float = -1f
  var reflexHalfWidth: Float = 0f
  var reflexOppositeHalfWidth: Float = 0f
  var reflexStrength: Float = 0f
  var reflexDepth: Float = 0f
  var reflexFloor: Float = 0f
  var reflexHalo: Float = 0f
  var glowCenterX: Float = 0f
  var glowCenterY: Float = 0f
  var glowRadius: Float = 0f
  var glow: Float = 0f
  var glowFill: Float = 0f
  var lightColor: Color = Color.White
  var lightMaterialize: Float = 0f
  var lightAa: Float = GlassLiquidAa

  /** Vero se c'è una luce da sommare: il primo disegno (`lightPart` 0, `BlendMode.Plus`). */
  var drawsGlowing: Boolean = false

  /** Vero se c'è un filo da posare: il secondo disegno (`lightPart` 1, `SrcOver`). */
  var drawsRim: Boolean = false

  // ---- Il ritaglio hardware --------------------------------------------------------------------------------

  /** La frazione del raggio nominale a cui sta il ritaglio hardware ([GlassSlabGeometry.clipScale]). */
  var clipScale: Float = 1f

  private val geometry = GlassSlabGeometry()

  /**
   * La metà del **corpo**.
   *
   * @param size la taglia della lastra nello spazio della cattura (quella dello scope degli effetti).
   * @param density la densità di quello spazio: dello schermo per la frazione di risoluzione.
   * @param maxSidePx il lato lungo in pixel dello **schermo**: decide i «piccoli» ([glassSlabIsSmall]) allo
   *   stesso modo per corpo, luce, ombra e ritaglio, qualunque sia la risoluzione della cattura.
   */
  fun setBody(
    recipe: GlassRecipe,
    shape: Shape,
    size: Size,
    density: Density,
    layoutDirection: LayoutDirection,
    maxSidePx: Float,
  ) {
    val d = density.density
    glassSlabGeometry(shape, size, density, layoutDirection, maxSidePx, geometry)
    body.setSlab(size.width, size.height, geometry)
    clipScale = geometry.clipScale

    saturation = recipe.saturation
    blurPx = recipe.blurDp * d
    mix = recipe.mix
    paddingPx = floor(max(recipe.paddingDp * d, 0f))
    // La cattura è più grande della lastra di un margine per lato: `offset` riporta la coordinata del layer
    // nel sistema dell'elemento, come nella lente di oggi.
    bodyOffsetX = -paddingPx
    bodyOffsetY = -paddingPx

    thickness = recipe.thicknessDp * d
    refraction = recipe.refractionDp * d
    dome = recipe.dome
    dispersion = recipe.dispersion
    materialize = recipe.materialize
    veilColor = recipe.veilColor
    veilAdapt = recipe.veilAdapt
    tintColor = recipe.tintColor
    tintMode = recipe.tintMode
    tintFollow = recipe.tintFollow
    bodyShade = recipe.bodyShade
    bodyShadeDepth = recipe.bodyShadeDepthDp * d
    bodyShadeLeanX = recipe.bodyShadeLeanX
    bodyShadeLeanY = recipe.bodyShadeLeanY
    dim = recipe.dim
    grain = recipe.grain
    // In pixel dello spazio in cui gira, come oggi: non è una lunghezza del materiale, è la rampa di un
    // pixel e un quarto della maschera.
    aa = recipe.aa
  }

  /**
   * La metà della **luce**.
   *
   * @param size la taglia del nodo, in pixel dello schermo.
   * @param glowAmount 0..1, quanto è acceso il bagliore del dito (la pressione per il suo peso); zero a
   *   riposo.
   * @param touch dove sta il dito, in pixel del nodo; non specificato = il centro della lastra.
   */
  fun setLight(
    recipe: GlassRecipe,
    shape: Shape,
    size: Size,
    density: Density,
    layoutDirection: LayoutDirection,
    glowAmount: Float = 0f,
    touch: Offset = Offset.Unspecified,
  ) {
    val d = density.density
    glassSlabGeometry(shape, size, density, layoutDirection, size.maxDimension, geometry)
    light.setSlab(size.width, size.height, geometry)
    clipScale = geometry.clipScale

    rimWidth = recipe.rimWidthDp * d
    rimDark = recipe.rimDark
    rimFeather = recipe.rimFeather
    rimLift = recipe.rimLift
    reflexDirX = recipe.reflexDirX
    reflexDirY = recipe.reflexDirY
    reflexHalfWidth = recipe.reflexHalfWidth
    reflexOppositeHalfWidth = recipe.reflexOppositeHalfWidth
    reflexStrength = recipe.reflexStrength
    reflexDepth = recipe.reflexDepthDp * d
    reflexFloor = recipe.reflexFloor
    reflexHalo = recipe.reflexHalo
    lightColor = recipe.lightColor
    lightMaterialize = recipe.materialize
    lightAa = recipe.aa

    // Il bagliore di oggi, negli uniform della luce: il raggio è il lato lungo della lastra
    // (`glassButtonGlowRecipe`), il centro è il dito tenuto dentro la lastra.
    val pressed = clampGlassUnit(glowAmount)
    glow = recipe.glowPeak * pressed
    glowFill = recipe.glowFill
    glowRadius = size.maxDimension
    if (touch.isSpecified && touch.x.isFinite() && touch.y.isFinite()) {
      glowCenterX = touch.x.coerceIn(0f, size.width)
      glowCenterY = touch.y.coerceIn(0f, size.height)
    } else {
      glowCenterX = size.width / 2f
      glowCenterY = size.height / 2f
    }

    val lit = recipe.materialize > 0f && recipe.lightColor.alpha > 0f
    drawsGlowing = lit && (recipe.reflexStrength > 0f || glow > 0f)
    drawsRim = recipe.drawsRim
  }
}

/**
 * Gli ingressi del disegno di una lastra ferma, tutti insieme: il **punto di osservazione** di ciò che
 * l'innesto passa agli shader (piano 3.2, 4.4). Pura.
 *
 * È esattamente ciò che fa [GlassLiquidSurface] in due tempi (il corpo quando si rifà la catena, la luce a
 * ogni disegno), chiamando le stesse due metà: chi confronta questi numeri con una scena del banco confronta
 * ciò che l'engine imposta.
 *
 * @param size la taglia del nodo, in pixel dello schermo.
 * @param density la densità dello schermo.
 * @param captureScale la frazione di risoluzione della cattura (`backdropScale` per il fattore di qualità,
 *   dopo il tetto di texture): corpo e catena ricevono taglia e densità moltiplicate per questa.
 */
internal fun glassDrawInputs(
  recipe: GlassRecipe,
  shape: Shape,
  size: Size,
  density: Float,
  layoutDirection: LayoutDirection = LayoutDirection.Ltr,
  captureScale: Float = 1f,
  glowAmount: Float = 0f,
  touch: Offset = Offset.Unspecified,
  out: GlassDrawInputs = GlassDrawInputs(),
): GlassDrawInputs {
  out.setBody(
    recipe = recipe,
    shape = shape,
    size = Size(size.width * captureScale, size.height * captureScale),
    density = Density(density * captureScale),
    layoutDirection = layoutDirection,
    maxSidePx = size.maxDimension,
  )
  out.setLight(recipe, shape, size, Density(density), layoutDirection, glowAmount, touch)
  return out
}

/**
 * La frazione di risoluzione a cui una superficie `Liquid` cattura, decisa in composizione come quella di
 * oggi: è un parametro del nodo, e la taglia in composizione non c'è.
 *
 * È `captureScale` della ricetta **senza la regola della taglia**. Per ogni ruolo di serie non cambia niente:
 * la sfocatura del profilo è già oltre i 10 dp a cui `glassResolutionScale` tocca il pavimento, e crescere
 * con la taglia non può farla scendere. Cambia solo per un'ottica scritta a mano che attenua la sfocatura
 * sotto i 10 dp su una lastra grande, che cattura allora a una risoluzione un poco più alta di quella che la
 * ricetta chiederebbe alla sua taglia: dalla parte sicura.
 */
internal fun glassLiquidCaptureScale(
  role: GlassRole,
  optics: GlassOptics,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
): Float = glassRecipe(role = role, widthDp = 0f, heightDp = 0f, darkSide = false, optics = optics, profile = profile)
  .captureScale

// ------------------------------------------------------------------------------------------------------------
// La catena del corpo
// ------------------------------------------------------------------------------------------------------------

/**
 * La parte della catena **a monte** del corpo, tenuta finché i suoi tre numeri non cambiano:
 *
 *     ColorFilter(saturazione) -> Blur(Clamp) -> BlendMode(Plus, w * nitido, (1 - w) * sfocato)
 *
 * Una pressione cambia un uniform del corpo a ogni fotogramma, e a ogni fotogramma la catena va ricostruita
 * (un `RenderEffect` è immutabile). Ma saturazione, raggio e mescola durante una pressione non cambiano:
 * tenendo questo pezzo si ricostruisce solo l'ultimo anello, e non si riallocano matrice e filtri.
 */
internal class GlassBodyChain {

  private var saturation = Float.NaN
  private var blurPx = Float.NaN
  private var mix = Float.NaN
  private var upstream: android.graphics.RenderEffect? = null
  private val matrix = FloatArray(20)

  /** Quante volte il pezzo a monte è stato ricostruito: per i test e la diagnostica. */
  var builds: Int = 0
    private set

  /** Il pezzo a monte, o `null` se non fa niente (saturazione 1 e nessuna sfocatura). */
  @RequiresApi(Build.VERSION_CODES.S)
  fun upstream(saturation: Float, blurPx: Float, mix: Float): android.graphics.RenderEffect? {
    if (saturation == this.saturation && blurPx == this.blurPx && mix == this.mix) return upstream
    this.saturation = saturation
    this.blurPx = blurPx
    this.mix = mix
    builds++

    val sharp = if (saturation != 1f) {
      android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturationMatrix(saturation)))
    } else {
      null
    }
    val w = clampGlassUnit(mix)
    upstream = if (!(blurPx > 0f) || w >= 1f) {
      // Tutto nitido: la sfocatura non si costruisce nemmeno. Il margine della cattura non dipende da
      // questo (lo dà la ricetta, a sfocatura piena), quindi non si muove.
      sharp
    } else {
      val blurred = if (sharp != null) {
        android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, sharp, Shader.TileMode.CLAMP)
      } else {
        android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
      }
      if (w <= 0f) {
        blurred
      } else {
        // Via A (piano 3.6): i due rami pesati e sommati. Il peso sta nell'alfa: un filtro di colore lavora
        // sul colore non premoltiplicato, quindi scalare la sola alfa scala tutti e quattro i canali
        // premoltiplicati, ed è ciò che `Plus` somma.
        val soft = android.graphics.RenderEffect.createColorFilterEffect(
          ColorMatrixColorFilter(alphaMatrix(1f - w)),
          blurred,
        )
        val crisp = if (sharp != null) {
          android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(alphaMatrix(w)), sharp)
        } else {
          android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(alphaMatrix(w)))
        }
        android.graphics.RenderEffect.createBlendModeEffect(soft, crisp, android.graphics.BlendMode.PLUS)
      }
    }
    return upstream
  }

  /** La matrice di saturazione di `colorControls` (`backdrop/effects/ColorFilter.kt`): la stessa di oggi. */
  private fun saturationMatrix(saturation: Float): FloatArray {
    val inverse = 1f - saturation
    val r = 0.213f * inverse
    val g = 0.715f * inverse
    val b = 0.072f * inverse
    matrix.fill(0f)
    matrix[0] = r + saturation; matrix[1] = g; matrix[2] = b
    matrix[5] = r; matrix[6] = g + saturation; matrix[7] = b
    matrix[10] = r; matrix[11] = g; matrix[12] = b + saturation
    matrix[18] = 1f
    return matrix
  }

  private fun alphaMatrix(alpha: Float): FloatArray {
    matrix.fill(0f)
    matrix[0] = 1f
    matrix[6] = 1f
    matrix[12] = 1f
    matrix[18] = alpha
    return matrix
  }
}

/** Gli uniform della geometria, uguali per corpo e luce (`GlassFieldKernel1`): un pezzo, flessione a riposo. */
private fun RuntimeShader.setGlassField(plan: GlassFieldPlan, offsetX: Float, offsetY: Float) {
  setFloatUniform("offset", offsetX, offsetY)
  // La flessione è di F6.1; va impostata sempre, perché uno uniform non impostato vale zero e una matrice a
  // zero schiaccia la lastra in un punto.
  setFloatUniform("flex", 1f, 0f, 0f, 1f)
  setFloatUniform("flexCenter", 0f, 0f)
  setFloatUniform("pieceCount", plan.count.toFloat())
  setFloatUniform("pieceRect", plan.rects)
  setFloatUniform("pieceRadii", plan.radii)
  setFloatUniform("pieceBlend", plan.blend)
  setFloatUniform("pieceSmooth", plan.smooth)
}

/**
 * La catena del corpo di una lastra ferma `Liquid`, al posto di `colorControls` + `blur` + `lens` di oggi:
 * saturazione, sfocatura, mescola a monte, e lo shader del corpo come ultimo anello. **Un layer solo.**
 *
 * Imposta anche il margine della cattura, che viene dalla ricetta a sfocatura piena e non da ciò che la
 * catena ha in questo momento: resta fermo mentre intensità, qualità e pressione cambiano, e la cattura non
 * si rifà per un cambio di soli uniform (invariante 1).
 *
 * Se il compilatore del dispositivo rifiuta la sorgente (non dovrebbe succedere: la superficie sarebbe rimasta
 * `Classic`) resta la catena a monte, cioè un vetro smerigliato senza lente: mai un'eccezione nel disegno.
 */
internal fun BackdropEffectScope.glassBody(inputs: GlassDrawInputs, chain: GlassBodyChain) {
  if (!isRuntimeShaderSupported()) return
  padding = inputs.paddingPx
  val upstream = chain.upstream(inputs.saturation, inputs.blurPx, inputs.mix)
  val shader = obtainEngineShader("GlassBody1", GlassShaderSources.Body1)
  if (shader == null) {
    renderEffect = upstream?.asComposeRenderEffect()
    return
  }
  shader.apply {
    setGlassField(inputs.body, inputs.bodyOffsetX, inputs.bodyOffsetY)
    setFloatUniform("thickness", inputs.thickness)
    setFloatUniform("refraction", inputs.refraction)
    setFloatUniform("dome", inputs.dome)
    setFloatUniform("dispersion", inputs.dispersion)
    // La goccia in moto: di una lastra ferma, zero.
    setFloatUniform("magnify", 0f)
    setFloatUniform("materialize", inputs.materialize)
    setColorUniform("veilColor", inputs.veilColor)
    setFloatUniform("veilAdapt", inputs.veilAdapt)
    setColorUniform("tintColor", inputs.tintColor)
    setFloatUniform("tintMode", inputs.tintMode)
    setFloatUniform("tintFollow", inputs.tintFollow)
    setColorUniform("bodyShade", inputs.bodyShade)
    setFloatUniform("bodyShadeDepth", inputs.bodyShadeDepth)
    setFloatUniform("bodyShadeLean", inputs.bodyShadeLeanX, inputs.bodyShadeLeanY)
    setFloatUniform("dim", inputs.dim)
    setFloatUniform("grain", inputs.grain)
    setFloatUniform("aa", inputs.aa)
    // A riposo luce e ombra non stanno nel corpo: la luce è due disegni sopra, l'ombra due maschere sotto.
    // L'istanza dello shader è una per processo: chi viaggia (F1B) lo riaccende per sé.
    setFloatUniform("lightInBody", 0f)
  }
  val body = RuntimeShaderEffect(shader, "content")
  renderEffect = if (upstream == null) {
    body
  } else {
    android.graphics.RenderEffect.createChainEffect(body.asAndroidRenderEffect(), upstream).asComposeRenderEffect()
  }
}

// ------------------------------------------------------------------------------------------------------------
// La luce a riposo
// ------------------------------------------------------------------------------------------------------------

/**
 * La luce a riposo: **due disegni dello stesso programma**, sul rettangolo del nodo. Prima la luce che somma
 * (`lightPart` 0, `BlendMode.Plus`: riga chiara, alone, bagliore del dito), poi il filo (`lightPart` 1,
 * `SrcOver`); ognuno solo se ha qualcosa da disegnare. Vedi [GlassLight1ShaderString].
 *
 * Gli uniform si impostano **qui dentro**, a ogni disegno: lo shader è un'istanza sola per processo, che ogni
 * superficie riscrive, e il nodo si ridisegna anche da sé (`ref/regole.md`, «La lambda di `record` dipende
 * solo dal proprio layer»). Fra i due disegni cambia un uniform solo: la piattaforma fotografa lo shader nel
 * momento in cui il pennello entra nella display list, quindi il primo disegno tiene il suo `lightPart`.
 *
 * @param brush un pennello che porta [shader], tenuto dalla superficie: non si alloca per fotogramma.
 */
internal fun DrawScope.drawGlassLight(inputs: GlassDrawInputs, shader: RuntimeShader, brush: Brush) {
  if (!inputs.drawsGlowing && !inputs.drawsRim) return
  shader.apply {
    // Si disegna sul nodo, non su una cattura più grande: nessun margine da togliere.
    setGlassField(inputs.light, 0f, 0f)
    setFloatUniform("rimWidth", inputs.rimWidth)
    setColorUniform("rimDark", inputs.rimDark)
    setFloatUniform("rimFeather", inputs.rimFeather)
    setFloatUniform("rimLift", inputs.rimLift)
    setFloatUniform("reflexDir", inputs.reflexDirX, inputs.reflexDirY)
    setFloatUniform("reflexWidths", inputs.reflexHalfWidth, inputs.reflexOppositeHalfWidth)
    setFloatUniform("reflexStrength", inputs.reflexStrength)
    setFloatUniform("reflexDepth", inputs.reflexDepth)
    setFloatUniform("reflexFloor", inputs.reflexFloor)
    setFloatUniform("reflexHalo", inputs.reflexHalo)
    setFloatUniform("glowCenter", inputs.glowCenterX, inputs.glowCenterY)
    setFloatUniform("glowRadius", inputs.glowRadius)
    setFloatUniform("glow", inputs.glow)
    setFloatUniform("glowFill", inputs.glowFill)
    setColorUniform("lightColor", inputs.lightColor)
    setFloatUniform("materialize", inputs.lightMaterialize)
    setFloatUniform("aa", inputs.lightAa)
  }
  if (inputs.drawsGlowing) {
    shader.setFloatUniform("lightPart", 0f)
    drawRect(brush = brush, blendMode = BlendMode.Plus)
  }
  if (inputs.drawsRim) {
    shader.setFloatUniform("lightPart", 1f)
    drawRect(brush = brush)
  }
}

// ------------------------------------------------------------------------------------------------------------
// La superficie
// ------------------------------------------------------------------------------------------------------------

/**
 * Il bagliore del dito, come lo dà un controllo al proprio vetro: quanto è acceso e dove.
 *
 * @param amount 0..1, letto in disegno: la pressione per il suo peso (con le animazioni ridotte, meno).
 * @param position dove sta il dito, in pixel del nodo, letto in disegno.
 */
@Stable
internal class GlassTouchLight(val amount: () -> Float, val position: () -> Offset)

/**
 * Lo stato di una superficie `Liquid` ferma: ciò che `glassSurface` ricorda fra una composizione e l'altra, e
 * le lambda che dà al nodo.
 *
 * Gli ingressi che cambiano spesso (intensità, profondità, pressione, qualità, pellicola, bagliore) sono
 * **lambda lette in disegno**: uno stato letto lì ridisegna, o rifà la sola catena, senza ricomporre e senza
 * che il nodo veda un elemento nuovo. Quelli che cambiano di rado (ruolo, ottica, forma, lato) sono valori:
 * quando cambiano la superficie si ricrea, le lambda sono nuove, e il nodo rifà ciò che deve.
 *
 * La ricetta si calcola tre volte, in tre momenti che il nodo non lega fra loro (l'ombra si disegna prima
 * del nodo, la catena si rifà quando cambia uno stato che ha letto, la luce a ogni disegno): ognuna in un
 * contenitore suo, senza allocare. È aritmetica pura sugli stessi ingressi; legare i tre momenti con una cache
 * vorrebbe dire decidere chi è arrivato per primo.
 *
 * @param optics già passata da `sanitized()`.
 * @param screenDensity la densità dello schermo: lo scope degli effetti ne ha una rimpicciolita, e i
 *   «piccoli» si decidono sui pixel veri.
 * @param film la pellicola di un `GlassTint` dipinto com'è (`GlassTintMode.Fixed`), o non specificato.
 * @param tint la tinta di Apple (`GlassTintMode.Colored`), o non specificato.
 */
@Stable
internal class GlassLiquidSurface(
  private val role: GlassRole,
  private val optics: GlassOptics,
  private val shape: Shape,
  private val darkSide: Boolean,
  private val screenDensity: Float,
  private val intensity: () -> Float,
  private val opticalDepth: () -> Float,
  private val pressed: () -> Float,
  private val quality: () -> Float,
  private val film: () -> Color,
  private val tint: () -> Color,
  private val touch: () -> GlassTouchLight?,
  private val profile: GlassLiquidProfile = GlassLiquidProfile.Default,
) {

  /** Vedi [glassLiquidCaptureScale]. */
  val captureScale: Float = glassLiquidCaptureScale(role, optics, profile)

  /** Vero se questa superficie può avere un'ombra: un ruolo senza (`Bar`, `Content`) non monta il nodo. */
  val castsShadow: Boolean = optics.shadowAlpha > 0f

  private fun recipe(widthDp: Float, heightDp: Float, out: GlassRecipe): GlassRecipe = glassRecipe(
    role = role,
    widthDp = widthDp,
    heightDp = heightDp,
    darkSide = darkSide,
    optics = optics,
    intensity = intensity(),
    opticalDepth = opticalDepth(),
    quality = quality(),
    pressed = pressed(),
    film = film(),
    tint = tint(),
    profile = profile,
    out = out,
  )

  // ---- Il ritaglio hardware --------------------------------------------------------------------------------

  private val clipGeometry = GlassSlabGeometry()

  /**
   * La forma che il nodo riceve al posto di quella chiesta: lo **stesso rettangolo arrotondato** che il campo
   * disegna, alla frazione del raggio a cui deve stare il ritaglio hardware ([GlassSmoothedClipScale] per le
   * lastre raccordate). Nel modello `Liquid` la forma del nodo serve solo a questo: lente, anello e ombra di
   * oggi, che la leggevano, non ci sono.
   *
   * Un contorno arrotondato e mai un path: il ritaglio resta quello hardware (invariante 18).
   */
  private val clipShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
      val g = glassSlabGeometry(shape, size, density, layoutDirection, size.maxDimension, clipGeometry)
      val k = g.clipScale
      if (g.topLeft <= 0f && g.topRight <= 0f && g.bottomRight <= 0f && g.bottomLeft <= 0f) {
        return Outline.Rectangle(Rect(Offset.Zero, size))
      }
      return Outline.Rounded(
        RoundRect(
          rect = Rect(Offset.Zero, size),
          topLeft = CornerRadius(g.topLeft * k),
          topRight = CornerRadius(g.topRight * k),
          bottomRight = CornerRadius(g.bottomRight * k),
          bottomLeft = CornerRadius(g.bottomLeft * k),
        ),
      )
    }
  }

  /** `shape` di `drawBackdrop`: un'identità stabile, perché l'elemento del nodo resti uguale a se stesso. */
  val clipShapeBlock: () -> Shape = { clipShape }

  // ---- Il corpo --------------------------------------------------------------------------------------------

  private val inputs = GlassDrawInputs()
  private val bodyRecipe = GlassRecipe()
  private val chain = GlassBodyChain()

  /**
   * La metà del corpo di [inputs], dallo scope degli effetti: taglia e densità sono quelle **della cattura**.
   * Falso se lo scope non ha ancora una taglia (alla prima applicazione, prima di ogni disegno).
   */
  private fun fillBody(size: Size, density: Density, layoutDirection: LayoutDirection): Boolean {
    val d = density.density
    if (!size.isSpecified || !(size.width > 0f) || !(size.height > 0f) || !(d > 0f)) return false
    val recipe = recipe(size.width / d, size.height / d, bodyRecipe)
    // I pixel dello schermo del lato lungo: il nodo ha una taglia intera, e lo scope la porta moltiplicata
    // per la frazione di risoluzione. Arrotondare toglie l'errore del prodotto, perché corpo e ritaglio
    // cadano sempre dalla stessa parte della soglia dei «piccoli».
    val scale = d / screenDensity
    val maxSidePx =
      if (scale > 0f && scale.isFinite()) (size.maxDimension / scale).roundToInt().toFloat() else size.maxDimension
    inputs.setBody(recipe, shape, size, density, layoutDirection, maxSidePx)
    return true
  }

  /** `effects` di `drawBackdrop`. */
  val effects: BackdropEffectScope.() -> Unit = {
    if (fillBody(size, this, layoutDirection)) glassBody(inputs, chain)
  }

  // ---- La luce ---------------------------------------------------------------------------------------------

  private val lightRecipe = GlassRecipe()

  // L'istanza è quella della cache di processo, la stessa che la sonda ha compilato; il pennello è di questa
  // superficie. Pigri: una superficie che non si disegna mai non li chiede.
  private val lightShader: RuntimeShader? by lazy(LazyThreadSafetyMode.NONE) {
    RuntimeShaderCacheImpl().obtainEngineShader("GlassLight1", GlassShaderSources.Light1)
  }
  private val lightBrush: Brush? by lazy(LazyThreadSafetyMode.NONE) {
    lightShader?.let { ShaderBrush(it.asComposeShader()) }
  }

  /** La metà della luce di [inputs], dal disegno del nodo: taglia e densità sono quelle dello schermo. */
  private fun fillLight(size: Size, density: Density, layoutDirection: LayoutDirection): Boolean {
    val d = density.density
    if (!size.isSpecified || !(size.width > 0f) || !(size.height > 0f) || !(d > 0f)) return false
    val recipe = recipe(size.width / d, size.height / d, lightRecipe)
    val finger = touch()
    inputs.setLight(
      recipe = recipe,
      shape = shape,
      size = size,
      density = density,
      layoutDirection = layoutDirection,
      glowAmount = finger?.amount?.invoke() ?: 0f,
      touch = finger?.position?.invoke() ?: Offset.Unspecified,
    )
    return true
  }

  /** `onDrawLight` di `drawBackdrop`. */
  val onDrawLight: DrawScope.() -> Unit = light@{
    if (!fillLight(size, this, layoutDirection)) return@light
    if (!inputs.drawsGlowing && !inputs.drawsRim) return@light
    val shader = lightShader ?: return@light
    val brush = lightBrush ?: return@light
    drawGlassLight(inputs, shader, brush)
  }

  /**
   * Ciò che questa superficie passa al disegno a una certa taglia: l'**adattatore** dal suo stato a
   * [glassDrawInputs], per i test di parità (piano 4.4). Fa le stesse due chiamate che fanno [effects] e
   * [onDrawLight], con lo scope che il nodo darebbe loro: la cattura a `captureScale`, la luce a risoluzione
   * piena. Il contenitore restituito è quello della superficie: si rilegge prima del prossimo disegno.
   *
   * @param size la taglia del nodo, in pixel dello schermo.
   * @param scale la frazione di risoluzione effettiva della cattura; di serie [effectiveCaptureScale].
   */
  fun drawInputs(
    size: Size,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    scale: Float = effectiveCaptureScale(size),
  ): GlassDrawInputs {
    fillBody(
      size = Size(size.width * scale, size.height * scale),
      density = Density(screenDensity * scale),
      layoutDirection = layoutDirection,
    )
    fillLight(size, Density(screenDensity), layoutDirection)
    return inputs
  }

  /**
   * La frazione di risoluzione a cui il nodo cattura una lastra di questa taglia a qualità piena e senza
   * `layerBlock`: [captureScale] dopo il tetto di texture, come la calcola il nodo.
   */
  fun effectiveCaptureScale(size: Size): Float = fitToTexture(captureScale, size.width, size.height)

  // ---- L'ombra ---------------------------------------------------------------------------------------------

  private val shadowRecipe = GlassRecipe()
  private val shadowGeometry = GlassSlabGeometry()
  private var shadowWidthDp = 0f
  private var shadowHeightDp = 0f
  private var shadowSpec: GlassShadowSpec? = null

  /**
   * La sagoma dell'ombra: gli stessi pezzi che riceve la luce, in pixel del nodo. Il nodo la chiede **prima**
   * dei numeri dell'ombra ([shadow]), e qui si prende nota della taglia: la ricetta la vuole, e la lambda dei
   * numeri non ha un `DrawScope` da cui leggerla.
   */
  val shadowOutline: GlassShadowOutline = GlassShadowOutline { out ->
    shadowWidthDp = if (density > 0f) size.width / density else 0f
    shadowHeightDp = if (density > 0f) size.height / density else 0f
    val g = glassSlabGeometry(shape, size, this, layoutDirection, size.maxDimension, shadowGeometry)
    out.setSlab(size.width, size.height, g.topLeft, g.topRight, g.bottomRight, g.bottomLeft, g.smoothing)
  }

  /**
   * I numeri dell'ombra, letti in disegno. `GlassRecipe.shadowSpec()` alloca: l'oggetto si tiene finché i
   * numeri non cambiano, e a riposo non cambiano mai.
   */
  val shadow: () -> GlassShadowSpec? = shadow@{
    val recipe = recipe(shadowWidthDp, shadowHeightDp, shadowRecipe)
    if (!recipe.drawsShadow) return@shadow null
    val held = shadowSpec
    if (held != null &&
      held.contactRadius.value == recipe.shadowContactRadiusDp &&
      held.ambientRadius.value == recipe.shadowAmbientRadiusDp &&
      held.offset.x.value == recipe.shadowOffsetXDp &&
      held.offset.y.value == recipe.shadowOffsetYDp &&
      held.contactAlpha == recipe.shadowContactAlpha &&
      held.ambientAlpha == recipe.shadowAmbientAlpha &&
      held.color == recipe.shadowColor
    ) {
      held
    } else {
      recipe.shadowSpec().also { shadowSpec = it }
    }
  }
}
