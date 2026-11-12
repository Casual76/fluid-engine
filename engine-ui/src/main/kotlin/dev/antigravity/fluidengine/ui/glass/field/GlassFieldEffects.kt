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
 *    sta lì: il disegno di una lastra ferma non alloca. Quando cambia uno stato che il corpo legge (una
 *    pressione, l'intensità, la qualità) la catena si rifà, e un `RenderEffect` è immutabile: si ricostruisce
 *    l'ultimo anello (lo shader del corpo, e la sua unione col pezzo a monte), una volta per cambio. Il pezzo a
 *    monte resta, finché saturazione, raggio e mescola non cambiano ([GlassBodyChain]).
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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassNestingScope
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.asComposeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.fitToTexture
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import java.util.WeakHashMap
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
 *
 * Da F1B.1 lo riempie anche Fluid-physics, per le sagome che viaggiano: allora i pezzi sono fino a otto
 * ([pieces]) o un anello ([ring]), la luce può stare **dentro il corpo** ([lightInBody], coi suoi numeri nello
 * spazio della cattura: [setBodyLight]) e il margine della cattura è quello fisso del viaggio.
 *
 * @param pieces quante caselle hanno i pezzi: 1 per una lastra ferma di `glassSurface` (i programmi a un
 *   pezzo), [GlassFieldMaxPieces] per una superficie di Fluid-physics.
 */
internal class GlassDrawInputs(pieces: Int = 1) {

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
  val body: GlassFieldPlan = GlassFieldPlan(pieces = pieces)

  /** Quale nucleo disegna il corpo: decide il programma, e quali uniform della geometria si impostano. */
  var kernel: GlassBodyKernel = if (pieces == 1) GlassBodyKernel.One else GlassBodyKernel.Eight

  /** L'anello, per [GlassBodyKernel.Ring]. Nasce alla prima richiesta: una lastra ferma non ne ha bisogno. */
  val ring: GlassRingField by lazy(LazyThreadSafetyMode.NONE) { GlassRingField() }
  var bodyOffsetX: Float = 0f
  var bodyOffsetY: Float = 0f
  var thickness: Float = 0f
  var refraction: Float = 0f
  var dome: Float = 0f
  var dispersion: Float = 0f

  /** L'ingrandimento della sagoma in moto: zero a riposo. */
  var magnify: Float = 0f
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

  // ---- La luce in corpo (spazio della cattura), in viaggio -------------------------------------------------

  /**
   * Vero per una superficie di Fluid-physics, ferma o in viaggio: filo, riflesso e bagliore li compone il corpo
   * (`lightInBody` a 1) e i due disegni della luce sul nodo non si fanno. I numeri della luce che sono lunghezze o punti arrivano allora nello spazio
   * della cattura ([setBodyLight]); gli altri (colori, forze, angoli) sono quelli di [setLight].
   */
  var lightInBody: Boolean = false

  /**
   * Vero per chi disegna su un palco rettangolare e non ha il ritaglio del nodo sulla sagoma (Fluid-physics):
   * il corpo lo emula (`nodeClip` di `GlassBodyUniformsBlock`), così il pixel di contorno è quello di una
   * `glassSurface`. Falso per una lastra, che il ritaglio vero ce l'ha.
   */
  var nodeClip: Boolean = false

  /**
   * Con [nodeClip], il margine d'angolo del ritaglio emulato in pixel della cattura: quanto il ritaglio di una
   * lastra raccordata (al 98% del raggio) passa più largo della sagoma in diagonale. Zero per gli angoli
   * circolari. Vedi [glassNodeClipCorner].
   */
  var nodeClipCorner: Float = 0f
  var bodyRimWidth: Float = 0f
  var bodyReflexDepth: Float = 0f
  var bodyGlowCenterX: Float = 0f
  var bodyGlowCenterY: Float = 0f
  var bodyGlowRadius: Float = 0f

  // ---- L'ombra in corpo (spazio della cattura), F1B.2 -------------------------------------------------------

  /**
   * Gli uniform di [GlassTravelShadowBlock], per chi disegna la luce nel corpo ([lightInBody]): lo scostamento
   * dell'ambiente e i due raggi in pixel della cattura, le due alfa **senza** `materialize` (la applica il corpo),
   * il colore. Tutto a zero = nessuna ombra. Li scrive [setBodyShadow].
   */
  var shadowOffsetX: Float = 0f
  var shadowOffsetY: Float = 0f
  var shadowContactRadius: Float = 0f
  var shadowAmbientRadius: Float = 0f
  var shadowContactAlpha: Float = 0f
  var shadowAmbientAlpha: Float = 0f
  var shadowColor: Color = Color.Transparent

  /** Fin dove arriva l'ombra in corpo oltre il contorno, in pixel della cattura; zero senza ombra. */
  val shadowReach: Float
    get() = GlassShadowProfile.reach(
      shadowContactRadius,
      shadowAmbientRadius,
      shadowContactAlpha * shadowColor.alpha,
      shadowAmbientAlpha * shadowColor.alpha,
      kotlin.math.hypot(shadowOffsetX, shadowOffsetY),
    )

  /**
   * Fin dove arriva il solo **contatto** oltre il contorno, in pixel (F1B.2, giro di correzione): è l'unica parte
   * dell'ombra che legge la distanza dal contorno (l'ambiente legge la maschera del nucleo, nel punto spostato). Oltre,
   * al corpo basta una distanza approssimata per difetto, e il nucleo ad anello non deve fare il ciclo sui lati.
   */
  val shadowContactReach: Float
    get() = GlassShadowProfile.reach(
      shadowContactRadius,
      shadowAmbientRadius,
      shadowContactAlpha * shadowColor.alpha,
      0f,
      kotlin.math.hypot(shadowOffsetX, shadowOffsetY),
    )

  // ---- La luce (pixel del nodo) ----------------------------------------------------------------------------

  /** I pezzi come li riceve la luce: gli stessi del corpo, a risoluzione piena. */
  val light: GlassFieldPlan = GlassFieldPlan(pieces = pieces)
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
    setBodyMaterial(recipe, d, floor(max(recipe.paddingDp * d, 0f)))
  }

  /**
   * I numeri del corpo che **non** sono geometria: la catena a monte e gli uniform del materiale, nello spazio
   * della cattura. Li usa [setBody] per la lastra ferma, e chi i pezzi li mette da sé (Fluid-physics, F1B.1).
   *
   * @param density pixel della cattura per dp.
   * @param paddingPx il margine della cattura in pixel suoi, già intero: per una lastra ferma quello della
   *   ricetta, per una superficie che viaggia quello fisso del viaggio ([glassTravelPaddingDp]).
   */
  fun setBodyMaterial(recipe: GlassRecipe, density: Float, paddingPx: Float) {
    val d = density
    saturation = recipe.saturation
    blurPx = recipe.blurDp * d
    mix = recipe.mix
    this.paddingPx = paddingPx
    // La cattura è più grande della lastra di un margine per lato: `offset` riporta la coordinata del layer
    // nel sistema dell'elemento, come nella lente di oggi.
    bodyOffsetX = -paddingPx
    bodyOffsetY = -paddingPx

    thickness = recipe.thicknessDp * d
    refraction = recipe.refractionDp * d
    dome = recipe.dome
    dispersion = recipe.dispersion
    magnify = recipe.magnify
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
    glassSlabGeometry(shape, size, density, layoutDirection, size.maxDimension, geometry)
    light.setSlab(size.width, size.height, geometry)
    clipScale = geometry.clipScale
    setLightMaterial(
      recipe = recipe,
      density = density.density,
      glowAmount = glowAmount,
      glowCenterX = if (touch.isSpecified && touch.x.isFinite() && touch.y.isFinite()) touch.x.coerceIn(0f, size.width) else size.width / 2f,
      glowCenterY = if (touch.isSpecified && touch.x.isFinite() && touch.y.isFinite()) touch.y.coerceIn(0f, size.height) else size.height / 2f,
      glowRadius = size.maxDimension,
    )
  }

  /**
   * I numeri della luce che **non** sono geometria, in pixel di chi la disegna sul nodo. Li usa [setLight] per la
   * lastra ferma, e chi i pezzi li mette da sé (Fluid-physics, F1B.1).
   *
   * @param density pixel dello schermo per dp.
   * @param glowCenterX con [glowCenterY], dove sta il dito, già tenuto dentro la sagoma; [glowRadius] il raggio
   *   della campana (il lato lungo).
   */
  fun setLightMaterial(
    recipe: GlassRecipe,
    density: Float,
    glowAmount: Float,
    glowCenterX: Float,
    glowCenterY: Float,
    glowRadius: Float,
  ) {
    val d = density
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
    this.glowRadius = glowRadius
    this.glowCenterX = glowCenterX
    this.glowCenterY = glowCenterY

    val lit = recipe.materialize > 0f && recipe.lightColor.alpha > 0f
    drawsGlowing = lit && (recipe.reflexStrength > 0f || glow > 0f)
    drawsRim = recipe.drawsRim
  }

  /**
   * Le lunghezze e i punti della luce **nello spazio della cattura**, per la luce in corpo ([lightInBody]). Va
   * chiamata dopo [setLightMaterial], che dà tutto il resto: una sagoma in viaggio ha lo stesso filo e lo stesso
   * riflesso della lastra ferma, solo composti dal corpo.
   *
   * @param density pixel della cattura per dp.
   * @param scale pixel della cattura per pixel del nodo: porta di là il dito e il raggio del bagliore.
   */
  fun setBodyLight(recipe: GlassRecipe, density: Float, scale: Float) {
    bodyRimWidth = recipe.rimWidthDp * density
    bodyReflexDepth = recipe.reflexDepthDp * density
    bodyGlowCenterX = glowCenterX * scale
    bodyGlowCenterY = glowCenterY * scale
    bodyGlowRadius = glowRadius * scale
  }

  /**
   * L'ombra **nel corpo** (F1B.2): gli stessi numeri che la ricetta dà alla maschera a riposo di una lastra di quella
   * taglia (`GlassRecipe.shadowSpec`), portati nello spazio della cattura. I raggi passano da
   * [GlassShadowProfile.scaledRadius] (il mezzo pixel della piattaforma non scala); le alfa si dividono per
   * `materialize`, perché la ricetta le ha già moltiplicate per l'intensità e il corpo le moltiplica di nuovo.
   *
   * @param screenDensity pixel dello schermo per dp.
   * @param scale pixel della cattura per pixel dello schermo.
   */
  fun setBodyShadow(recipe: GlassRecipe, screenDensity: Float, scale: Float) {
    val amount = recipe.materialize
    if (!recipe.drawsShadow || !(amount > 0.001f) || !(screenDensity > 0f) || !(scale > 0f)) {
      clearBodyShadow()
      return
    }
    shadowContactRadius = GlassShadowProfile.scaledRadius(recipe.shadowContactRadiusDp * screenDensity, scale)
    shadowAmbientRadius = GlassShadowProfile.scaledRadius(recipe.shadowAmbientRadiusDp * screenDensity, scale)
    shadowOffsetX = recipe.shadowOffsetXDp * screenDensity * scale
    shadowOffsetY = recipe.shadowOffsetYDp * screenDensity * scale
    shadowContactAlpha = (recipe.shadowContactAlpha / amount).coerceIn(0f, 1f)
    shadowAmbientAlpha = (recipe.shadowAmbientAlpha / amount).coerceIn(0f, 1f)
    shadowColor = recipe.shadowColor
  }

  /** Nessuna ombra nel corpo. */
  fun clearBodyShadow() {
    shadowOffsetX = 0f
    shadowOffsetY = 0f
    shadowContactRadius = 0f
    shadowAmbientRadius = 0f
    shadowContactAlpha = 0f
    shadowAmbientAlpha = 0f
    shadowColor = Color.Transparent
  }
}

/**
 * Il margine d'angolo del ritaglio emulato ([GlassDrawInputs.nodeClipCorner]) per un pezzo di raggio [radius]
 * (pixel) e raccordo [smoothing]: il ritaglio hardware di una lastra raccordata sta a [GlassSmoothedClipScale]
 * del raggio, quindi in diagonale passa più largo della sagoma, e lì non taglia. Pura.
 *
 * Il raccordo di un pezzo in viaggio sale da zero a quello della forma in modo continuo, e il margine con lui
 * (pieno da [GlassNodeClipFullSmoothing] in su): niente scatto dove il ritaglio vero passerebbe da 1 a 0,98.
 */
internal fun glassNodeClipCorner(radius: Float, smoothing: Float): Float {
  if (!(radius > 0f) || !(smoothing > 0f)) return 0f
  val weight = if (smoothing >= GlassNodeClipFullSmoothing) 1f else smoothing / GlassNodeClipFullSmoothing
  return (1f - GlassSmoothedClipScale) * radius * weight
}

/** Il raccordo da cui il margine di [glassNodeClipCorner] è pieno: una frazione piccola di quello di iOS (0,6). */
internal const val GlassNodeClipFullSmoothing: Float = 0.1f

/** Quale nucleo del campo disegna un corpo: vedi `GlassFieldKernel.kt`. */
internal enum class GlassBodyKernel {
  /** Una lastra ferma: `GlassBody1`, senza ciclo. */
  One,

  /** Fino a otto lastre fuse: `GlassBody8`. Gruppi, viaggi fra lastre, e ogni lastra di Fluid-physics. */
  Eight,

  /** Una sagoma ad anello: `GlassBodyRing`. La goccia in viaggio e le sagome libere. */
  Ring,
}

/**
 * L'anello come lo riceve il nucleo ad anello ([GlassFieldKernelRing]), nello spazio di chi disegna. Un
 * contenitore riusato: nessuna allocazione per fotogramma.
 */
internal class GlassRingField {
  var count: Int = 0

  /** `verts`: x, y interlacciati; le caselle oltre [count] a zero. */
  val verts: FloatArray = FloatArray(GlassFieldRingVertices * 2)

  /** `bbox`: sinistra, alto, destra, basso, già allargato di [bandOuter]. */
  val bbox: FloatArray = FloatArray(4)
  var normalSmooth: Float = 0f
  var soften: Float = 0f
  var centerX: Float = 0f
  var centerY: Float = 0f
  var restCenterX: Float = 0f
  var restCenterY: Float = 0f
  var restHalfWidth: Float = 0f
  var restHalfHeight: Float = 0f

  /** `restRadii`: i raggi che il campo dà alla lastra a riposo (la regola forma → piano), non quelli nominali. */
  val restRadii: FloatArray = FloatArray(4)

  /** `restSmooth`: il raccordo che il campo dà alla lastra a riposo (zero = angolo circolare). */
  var restSmooth: Float = 0f

  /** `restBlend`: quanto il campo è già quello della lastra a riposo, 0..1 (F1B.2: la goccia che si posa). */
  var restBlend: Float = 0f

  /** `dropReach`: negativo se non è una goccia. */
  var dropReach: Float = -1f

  /** `ringBand.x`: fin dove la distanza è vera fuori dalla sagoma. */
  var bandOuter: Float = 0f

  /** `ringBand.y`: fin dove è vera dentro. */
  var bandInner: Float = 0f

  /** Un appoggio per chi calcola [restRadii] e [restSmooth] con la regola forma → piano: niente allocazioni nel disegno. */
  internal val restGeometry: GlassSlabGeometry = GlassSlabGeometry()
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
 * È `captureScale` della ricetta, che dalla F1A.5c si decide sulla sfocatura del ruolo **senza la regola della
 * taglia** proprio perché qui la taglia non c'è: il numero è lo stesso a ogni taglia, nelle scene del banco e
 * nelle lastre vere. Una lastra grande, che sfoca un poco di più, cattura così a una risoluzione un poco più alta
 * di quella che la sua sfocatura permetterebbe: dalla parte sicura.
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

/** Gli uniform della geometria ad anello ([GlassFieldKernelRing]). */
private fun RuntimeShader.setGlassRing(ring: GlassRingField, offsetX: Float, offsetY: Float) {
  setFloatUniform("offset", offsetX, offsetY)
  setFloatUniform("flex", 1f, 0f, 0f, 1f)
  setFloatUniform("flexCenter", 0f, 0f)
  setFloatUniform("vertCount", ring.count.toFloat())
  setFloatUniform("verts", ring.verts)
  setFloatUniform("bbox", ring.bbox)
  setFloatUniform("normalSmooth", ring.normalSmooth)
  setFloatUniform("soften", ring.soften)
  setFloatUniform("fieldCenter", ring.centerX, ring.centerY)
  setFloatUniform("restCenter", ring.restCenterX, ring.restCenterY)
  setFloatUniform("restHalfSize", ring.restHalfWidth, ring.restHalfHeight)
  setFloatUniform("restRadii", ring.restRadii)
  setFloatUniform("restSmooth", ring.restSmooth)
  setFloatUniform("restBlend", ring.restBlend)
  setFloatUniform("dropReach", ring.dropReach)
  setFloatUniform("ringBand", ring.bandOuter, ring.bandInner)
}

/**
 * Gli uniform della geometria a lastre, uguali per corpo e luce (`GlassFieldKernel1`, `GlassFieldKernel8`): i
 * pezzi del piano, flessione a riposo. Gli array hanno le caselle del programma a cui vanno: uno o otto.
 */
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
  // Solo il nucleo a otto pesa normale e centro su un raggio suo (F1B.2): quello a un pezzo non lo dichiara.
  if (plan.pieces > 1) setFloatUniform("pieceSoft", plan.soft)
}

/**
 * La catena del corpo di una superficie `Liquid`, al posto di `colorControls` + `blur` + `lens` di oggi:
 * saturazione, sfocatura, mescola a monte, e lo shader del corpo come ultimo anello. **Un layer solo.** Il
 * programma lo sceglie [GlassDrawInputs.kernel]: una lastra ferma, fino a otto lastre fuse, un anello (F1B.1); e
 * [GlassDrawInputs.lightInBody] gli fa comporre anche la luce, come deve una sagoma in viaggio.
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
  val shader = when (inputs.kernel) {
    GlassBodyKernel.One -> obtainEngineShader("GlassBody1", GlassShaderSources.Body1)
    GlassBodyKernel.Eight -> obtainEngineShader("GlassBody8", GlassShaderSources.Body8)
    GlassBodyKernel.Ring -> obtainEngineShader("GlassBodyRing", GlassShaderSources.BodyRing)
  }
  if (shader == null) {
    renderEffect = upstream?.asComposeRenderEffect()
    return
  }
  shader.apply {
    if (inputs.kernel == GlassBodyKernel.Ring) {
      setGlassRing(inputs.ring, inputs.bodyOffsetX, inputs.bodyOffsetY)
    } else {
      setGlassField(inputs.body, inputs.bodyOffsetX, inputs.bodyOffsetY)
    }
    setFloatUniform("thickness", inputs.thickness)
    setFloatUniform("refraction", inputs.refraction)
    setFloatUniform("dome", inputs.dome)
    setFloatUniform("dispersion", inputs.dispersion)
    // La sagoma in moto: di una lastra ferma, zero.
    setFloatUniform("magnify", inputs.magnify)
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
    setFloatUniform("nodeClip", if (inputs.nodeClip) 1f else 0f, inputs.nodeClipCorner)
    // A riposo luce e ombra non stanno nel corpo: la luce è due disegni sopra, l'ombra due maschere sotto.
    // L'istanza dello shader è una per processo, e ogni superficie la riscrive per intero prima di fotografarla
    // nel suo effetto: chi viaggia accende la luce in corpo per sé, chi è fermo la spegne.
    if (inputs.lightInBody) {
      setFloatUniform("lightInBody", 1f)
      setFloatUniform("rimWidth", inputs.bodyRimWidth)
      setColorUniform("rimDark", inputs.rimDark)
      setFloatUniform("rimFeather", inputs.rimFeather)
      setFloatUniform("rimLift", inputs.rimLift)
      setFloatUniform("reflexDir", inputs.reflexDirX, inputs.reflexDirY)
      setFloatUniform("reflexWidths", inputs.reflexHalfWidth, inputs.reflexOppositeHalfWidth)
      setFloatUniform("reflexStrength", inputs.reflexStrength)
      setFloatUniform("reflexDepth", inputs.bodyReflexDepth)
      setFloatUniform("reflexFloor", inputs.reflexFloor)
      setFloatUniform("reflexHalo", inputs.reflexHalo)
      setFloatUniform("glowCenter", inputs.bodyGlowCenterX, inputs.bodyGlowCenterY)
      setFloatUniform("glowRadius", inputs.bodyGlowRadius)
      setFloatUniform("glow", inputs.glow)
      setFloatUniform("glowFill", inputs.glowFill)
      setColorUniform("lightColor", inputs.lightColor)
      // L'ombra in corpo (F1B.2): sempre scritta per intero, anche a zero, perché uno uniform tiene il valore
      // dell'ultimo che l'ha scritto e l'istanza dello shader è condivisa.
      setFloatUniform("shadowOffset", inputs.shadowOffsetX, inputs.shadowOffsetY)
      setFloatUniform("shadowRadii", inputs.shadowContactRadius, inputs.shadowAmbientRadius)
      setFloatUniform("shadowAlphas", inputs.shadowContactAlpha, inputs.shadowAmbientAlpha)
      setColorUniform("shadowColor", inputs.shadowColor)
    } else {
      setFloatUniform("lightInBody", 0f)
    }
  }
  val body = RuntimeShaderEffect(shader, "content")
  renderEffect = if (upstream == null) {
    body
  } else {
    android.graphics.RenderEffect.createChainEffect(body.asAndroidRenderEffect(), upstream).asComposeRenderEffect()
  }
}

/**
 * `onDrawBackdrop` di una lastra `Liquid`: prima del fondo, una cancellazione dell'area della cattura, poi il fondo.
 *
 * Perché: il corpo è un `RenderEffect` sul layer della cattura, e la piattaforma non disegna un layer che non ha
 * **nessuna** operazione registrata, quindi non ne esegue nemmeno l'effetto. Un fondo che non ha niente da dare (il
 * fondo vuoto di un controllo su un contenuto opaco, una sorgente non ancora posata) non registra niente; la cattura
 * allora conteneva solo la traslazione del margine, se il margine c'era. Il corpo esisteva o no a seconda del
 * margine (`max(0, sfocatura − banda della lente)`), cioè della smerigliatura: con i 4,5 dp di serie e con gli 8 di
 * M il margine di un tasto è zero e il tasto restava un anello vuoto (solo la luce, che si disegna sul nodo); con
 * 12 dp o 36 il corpo c'era, velo pieno sopra il niente. Misurato sul Tab S9 (F1A.13): con `blur=11.4` il tasto da
 * 48 dp (banda 11,5 dp, margine zero) è vuoto e la croce da 44 (banda 10,6, margine 1,8 px) ha il corpo.
 *
 * La cancellazione è un'operazione vera (`Clear` non è un disegno «che non disegna niente», che il registratore
 * scarterebbe), e su un layer appena nato non cambia un pixel. Con un fondo vero il fondo le si disegna sopra.
 */
internal val GlassLiquidOnDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
  drawRect(color = Color.Transparent, blendMode = BlendMode.Clear)
  drawBackdrop()
}

// ------------------------------------------------------------------------------------------------------------
// I materiali pubblicati dalle lastre `Liquid`
// ------------------------------------------------------------------------------------------------------------

/**
 * I materiali che una lastra `Liquid` pubblica con `exports` (4.0.0, F1A.13): con quante lastre li pubblicano.
 *
 * Servono al vetro annidato quando il controllo **non è figlio** del pannello su cui sta, cioè lo schema che il KDoc
 * di `exports` insegna alle app («un controllo su una barra»): il pannello in un nodo, i controlli in un fratello che
 * campiona `rememberCombinedGlassBackdrop(pagina, materiale)`. Il nodo del controllo non trova un vetro fra i suoi
 * antenati e per lui la superficie sta sulla pagina; ma un controllo che campiona il materiale di un pannello sta su
 * quel pannello, e il velo pieno sopra un materiale che ne ha già uno è la toppa grigia (S1-ter, difetto 4).
 *
 * Solo le lastre `Liquid` pubblicano qui: la fascia in alto delle barre è vetro di prima (`Classic`), e i tasti che
 * ci stanno sopra restano lastre sulla pagina, come la regola dell'annidato ha deciso (F1A.12).
 *
 * Un contatore di stato per materiale, in una mappa debole: chi lo legge negli effetti viene rifatto quando il
 * materiale comincia o smette di essere pubblicato, e solo lui. Si usa dal thread principale.
 */
internal object GlassLiquidPanels {

  private val counts = WeakHashMap<Backdrop, MutableIntState>()

  private fun counter(material: Backdrop): MutableIntState = counts.getOrPut(material) { mutableIntStateOf(0) }

  /** Una lastra `Liquid` comincia a pubblicare [material]. */
  fun publish(material: Backdrop) {
    counter(material).intValue += 1
  }

  /** La lastra smette: [material] resta pubblicato finché un'altra lo pubblica. */
  fun withdraw(material: Backdrop) {
    val counter = counts[material] ?: return
    if (counter.intValue > 0) counter.intValue -= 1
  }

  /** Vero se una lastra `Liquid` pubblica [part]. Letto negli effetti: è uno stato. */
  fun isPublished(part: Backdrop): Boolean = counter(part).intValue > 0
}

/**
 * Vero se [backdrop] contiene, a qualunque profondità, un materiale che [isPanel] riconosce come pubblicato da un
 * pannello `Liquid`: la superficie che lo campiona sta su quel pannello. Pura. Vedi [GlassLiquidPanels].
 */
internal fun glassSamplesLiquidPanel(backdrop: Backdrop, isPanel: (Backdrop) -> Boolean): Boolean {
  var found = false
  backdrop.forEachPart { part -> if (!found && isPanel(part)) found = true }
  return found
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
 * @param topLeft con [area], il rettangolo su cui disegnare, di serie tutto il nodo. Una superficie il cui nodo è
 *   più grande della sagoma (un palco di Fluid-physics) lo restringe al riquadro della sagoma: il programma
 *   valuta il campo per ogni pixel che gli si dà. La coordinata dello shader resta quella del nodo.
 */
internal fun DrawScope.drawGlassLight(
  inputs: GlassDrawInputs,
  shader: RuntimeShader,
  brush: Brush,
  topLeft: Offset = Offset.Zero,
  area: Size = size,
) {
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
    drawRect(brush = brush, topLeft = topLeft, size = area, blendMode = BlendMode.Plus)
  }
  if (inputs.drawsRim) {
    shader.setFloatUniform("lightPart", 1f)
    drawRect(brush = brush, topLeft = topLeft, size = area)
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
 * Un ingresso non viene da chi compone ma dal **nodo**: se la lastra sta dentro un'altra lastra di vetro
 * ([nested]). Vedi lì.
 *
 * @param optics già passata da `sanitized()`.
 * @param screenDensity la densità dello schermo: lo scope degli effetti ne ha una rimpicciolita, e i
 *   «piccoli» si decidono sui pixel veri.
 * @param film la pellicola di un `GlassTint` dipinto com'è (`GlassTintMode.Fixed`), o non specificato.
 * @param tint la tinta di Apple (`GlassTintMode.Colored`), o non specificato.
 * @param onPanel vero se il fondo che la lastra campiona contiene il materiale pubblicato da un pannello `Liquid`
 *   (F1A.13, [GlassLiquidPanels]): l'altra metà della risposta di [nested]. Letta negli effetti, quindi uno stato
 *   che legge li rifà quando cambia.
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
  private val onPanel: () -> Boolean = { false },
) {

  /** Vedi [glassLiquidCaptureScale]. */
  val captureScale: Float = glassLiquidCaptureScale(role, optics, profile)

  /** Vero se questa superficie può avere un'ombra: un ruolo senza (`Bar`, `Content`) non monta il nodo. */
  val castsShadow: Boolean = optics.shadowAlpha > 0f

  private fun recipe(widthDp: Float, heightDp: Float, nested: Boolean, out: GlassRecipe): GlassRecipe = glassRecipe(
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
    nested = nested,
    profile = profile,
    out = out,
  )

  // ---- Il vetro annidato -----------------------------------------------------------------------------------

  /** Ciò che il corpo ha visto l'ultima volta: un campo semplice, perché la catena non legga lo stato che scrive. */
  private var nestedSeen = false

  /** Lo stesso, come stato: luce e ombra lo leggono in disegno, e un cambio le ridisegna. */
  private var nestedState by mutableStateOf(false)

  /**
   * Vero se questa lastra sta **dentro** un'altra lastra di vetro e ne campiona il materiale: un tasto in una
   * notifica, un'azione su un foglio, il `glassSurface` che un'app mette dentro un altro. La ricetta allora
   * prende il vetro annidato del profilo ([GlassLiquidProfile.nesting]: meno velo, meno ombra).
   *
   * Non lo dice nessun parametro: lo dice il nodo del vetro, che per il vetro su vetro (2.13.0) cerca già il
   * pannello che lo contiene fra i suoi antenati (`GlassNestingScope.standsOnGlass`, da `drawBackdrop`). Un
   * `CompositionLocal` non basterebbe: un modificatore non lo può fornire al contenuto, e un'app che si fa il
   * pannello con `glassSurface` su un suo `Box` resterebbe fuori. Un vetro **accanto** a un altro (i tasti di una
   * barra, la capsula sopra la pagina) non ha un vetro fra gli antenati, e resta una lastra sulla pagina.
   *
   * L'altra metà (F1A.13): una lastra che campiona il **materiale pubblicato** da un pannello `Liquid` ([onPanel],
   * [GlassLiquidPanels]) sta su quel pannello anche se non ne è figlia. È lo schema di `exports` con i controlli in
   * un nodo fratello del pannello: il nodo non ha un vetro fra gli antenati, ma il fondo dice su cosa sta. La
   * fascia delle barre è vetro di prima e non pubblica qui: i loro tasti restano lastre sulla pagina.
   *
   * Un pannello **opaco** dell'engine (la notifica senza fondo, il foglio senza fondo) diventa un contenitore anche
   * lui, col proprio fondo come materiale (`glassOpaquePanel`): i suoi tasti arrivano qui dalla prima metà.
   *
   * Arriva al primo giro degli effetti, che il nodo fa quando si attacca, prima del primo disegno: il primo
   * fotogramma ha già il velo giusto. Se il pannello compare o se ne va dopo, il nodo rifà gli effetti; la luce
   * si ridisegna nello stesso fotogramma, l'ombra (che si disegna prima del nodo) in quello dopo.
   */
  val nested: Boolean get() = nestedState

  /**
   * Annota la risposta del nodo, o del fondo. Chiamata dagli [effects]; per i test, dallo scope che la porta.
   * [onPanel] si chiede sempre, anche quando il nodo ha già risposto di sì: è letto qui perché la sua lettura
   * iscriva gli effetti al suo stato, e una lastra che oggi sta in un pannello domani può starne fuori.
   */
  private fun noteNesting(scope: BackdropEffectScope) {
    val panel = onPanel()
    val onGlass = (scope as? GlassNestingScope)?.standsOnGlass == true || panel
    if (onGlass != nestedSeen) {
      nestedSeen = onGlass
      nestedState = onGlass
    }
  }

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
    val recipe = recipe(size.width / d, size.height / d, nestedSeen, bodyRecipe)
    // I pixel dello schermo del lato lungo: il nodo ha una taglia intera, e lo scope la porta moltiplicata
    // per la frazione di risoluzione. Arrotondare toglie l'errore del prodotto, perché corpo e ritaglio
    // cadano sempre dalla stessa parte della soglia dei «piccoli».
    val scale = d / screenDensity
    val maxSidePx =
      if (scale > 0f && scale.isFinite()) (size.maxDimension / scale).roundToInt().toFloat() else size.maxDimension
    inputs.setBody(recipe, shape, size, density, layoutDirection, maxSidePx)
    return true
  }

  /**
   * `effects` di `drawBackdrop`. Prima di tutto prende dal nodo se la lastra è annidata ([nested]): anche al
   * primo giro, quando lo scope non ha ancora una taglia e il corpo non si costruisce.
   */
  val effects: BackdropEffectScope.() -> Unit = {
    noteNesting(this)
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
    val recipe = recipe(size.width / d, size.height / d, nestedState, lightRecipe)
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
    val recipe = recipe(shadowWidthDp, shadowHeightDp, nestedState, shadowRecipe)
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
