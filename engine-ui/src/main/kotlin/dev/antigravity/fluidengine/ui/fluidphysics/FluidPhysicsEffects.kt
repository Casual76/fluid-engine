package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassTintLiquidMode
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassNestingScope
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.runtimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyChain
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldRingVertices
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidDispersion
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassRecipe
import dev.antigravity.fluidengine.ui.glass.field.GlassRingField
import dev.antigravity.fluidengine.ui.glass.field.glassBody
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import dev.antigravity.fluidengine.ui.glass.field.glassNodeClipCorner
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassSlabGeometry
import dev.antigravity.fluidengine.ui.glass.field.glassSlabIsSmall
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import dev.antigravity.fluidengine.ui.glass.field.glassTravelRecipe
import dev.antigravity.fluidengine.ui.glass.field.lerpGlassRecipe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Gli array di lavoro per gli uniform, uno per superficie: il piano è in pixel di layout e lo
 * shader vuole pixel di scope, e la conversione non deve allocare a ogni fotogramma.
 */
internal class PhysicsUniformScratch {
  val rects = FloatArray(PhysicsMaxPieces * 4)
  val radii = FloatArray(PhysicsMaxPieces * 4)
  val blends = FloatArray(PhysicsMaxPieces)
  val verts = FloatArray(PhysicsMaxRingVertices * 2)
}

/** Larghezza dell'antialias della maschera, in pixel di scope. */
private const val MaskAntialiasPx = 1.25f

/**
 * Su quale distanza la normale di una sagoma libera si media fra i lati vicini (3.0): abbastanza da
 * sciogliere lo spigolo fra due lati della goccia, poco rispetto a un lato.
 */
private val PolyNormalSmooth = 4.dp

/**
 * La profondità della lente come PESO (3.0): 0 è il bordo piatto, 1 la lente con profondità.
 *
 * Lo shader prendeva già un `float` (`grad + depthEffect * normalize(coord)`), era solo il Kotlin a
 * passare 0 o 1. Con un peso la profondità si sfuma lungo un viaggio fra due materiali — il menu che
 * parte da un tasto con profondità e arriva a un pannello senza — invece di accendersi di colpo a
 * metà strada. Chi passa un Boolean ottiene 0f / 1f, cioè esattamente quello di prima.
 */
internal fun Boolean.asDepthWeight(): Float = if (this) 1f else 0f

/**
 * Il valore che arriva all'uniform: tenuto fra 0 e 1 perché oltre non sarebbe più una miscela fra
 * la normale del bordo e quella radiale, ma una spinta che lo shader non ha mai avuto il compito di
 * reggere (un peso che sfora per un'interpolazione con overshoot non deve rovesciare la rifrazione).
 */
internal fun depthEffectUniform(weight: Float): Float = weight.coerceIn(0f, 1f)

/**
 * Il peso della frangia che arriva allo shader: fra 0 e 1, e zero sotto un millesimo (la sorgente
 * senza frangia costa un settimo dei campioni nella banda della lente) o se il driver ha rifiutato
 * la variante con la frangia — la lente resta, la frangia no.
 */
internal fun dispersionUniform(weight: Float): Float {
  if (!(weight > 0.001f)) return 0f
  if (!FluidPhysicsShaderHealth.dispersionHealthy()) return 0f
  return weight.coerceAtMost(1f)
}

/**
 * La lente di Fluid-physics per pezzi rettangolari (uno o molti, fusi con lo smin).
 *
 * Stessa disciplina di `lens()` (Lens.kt): salta sotto API 33 lasciando in piedi sfocatura e
 * vividezza già in catena, e sconta dal padding l'altezza di rifrazione — con un **padding che non
 * cambia mai durante il morph**, così la cattura non viene mai sporcata a metà viaggio (vedi il
 * contratto in DrawBackdropModifier).
 *
 * Blur e altezza, dalla regola della taglia (`glassSizedOptics`), crescono con la sagoma mentre
 * viaggia, quindi non sono piu' costanti: a restare ferma e' la loro *differenza*. La sfocatura non
 * supera mai l'altezza che la copre, e il padding (`max(0, sfocatura - altezza)`) vale zero a ogni
 * taglia. Chi chiama questa funzione con numeri propri deve rispettare lo stesso patto.
 *
 * A differenza della lente di serie, qui lo shader porta anche la **maschera** della silhouette
 * (il clip del layer è un rettangolo fisso) e, quando i pezzi sono più d'uno, la **tinta**: il
 * ponte dello smin è materiale che nessun path descrive, e una pellicola disegnata come path lo
 * lascerebbe nudo.
 */
internal fun BackdropEffectScope.slabGroupMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Float,
  tintInShader: Color,
  /**
   * Il peso della frangia cromatica, 0..1 (3.0): quella di `glassSurface` per le ottiche che la
   * chiedono. A zero — o se il driver ha rifiutato la variante — si usa la sorgente di sempre.
   */
  chromaticAberration: Float = 0f,
  /** L'ingrandimento della goccia in moto (3.0, [liquidMagnify]): zero a riposo. */
  magnify: Float = 0f,
) {
  if (!isRuntimeShaderSupported()) return

  val height = refractionHeightPx.fastCoerceAtLeast(0.0001f)
  if (padding > 0f) {
    padding = physicsLensPadding(padding, height)
  }

  val count = plan.pieceCount.coerceIn(1, PhysicsMaxPieces)
  for (i in 0 until count * 4) {
    scratch.rects[i] = plan.pieceRects[i] * scopeFactor
    scratch.radii[i] = plan.pieceRadii[i] * scopeFactor
  }
  // Gli slot oltre pieceCount non vengono letti dal loop, ma l'array uniform va riempito per
  // intero: un residuo del fotogramma prima non deve poter diventare un pezzo fantasma.
  for (i in count * 4 until scratch.rects.size) {
    scratch.rects[i] = 0f
    scratch.radii[i] = 0f
  }
  for (i in 0 until PhysicsMaxPieces) {
    scratch.blends[i] = if (i < count) plan.pieceBlend[i] * scopeFactor else 0f
  }

  val currentPadding = padding
  val chroma = dispersionUniform(chromaticAberration)
  if (chroma > 0f) {
    runtimeShaderEffect("FluidPhysicsSlabGroupDispersion", SlabGroupDispersionShaderString, "content") {
      setFloatUniform("offset", -currentPadding, -currentPadding)
      setFloatUniform("pieceCount", count.toFloat())
      setFloatUniform("pieceRect", scratch.rects)
      setFloatUniform("pieceRadii", scratch.radii)
      setFloatUniform("pieceBlend", scratch.blends)
      setFloatUniform("refractionHeight", height)
      setFloatUniform("refractionAmount", -refractionAmountPx)
      setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
      setFloatUniform("magnify", magnify)
      setFloatUniform(
        "fieldCenter",
        plan.bounds.center.x * scopeFactor,
        plan.bounds.center.y * scopeFactor,
      )
      setFloatUniform(
        "fieldHalfSize",
        plan.bounds.width / 2f * scopeFactor,
        plan.bounds.height / 2f * scopeFactor,
      )
      setFloatUniform("chromaticAberration", chroma)
      setFloatUniform("aa", MaskAntialiasPx)
      setColorUniform("tintColor", tintInShader)
    }
    return
  }
  runtimeShaderEffect("FluidPhysicsSlabGroup", SlabGroupRefractionShaderString, "content") {
    setFloatUniform("offset", -currentPadding, -currentPadding)
    setFloatUniform("pieceCount", count.toFloat())
    setFloatUniform("pieceRect", scratch.rects)
    setFloatUniform("pieceRadii", scratch.radii)
    setFloatUniform("pieceBlend", scratch.blends)
    setFloatUniform("refractionHeight", height)
    setFloatUniform("refractionAmount", -refractionAmountPx)
    setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
    setFloatUniform("magnify", magnify)
    setFloatUniform(
      "fieldCenter",
      plan.bounds.center.x * scopeFactor,
      plan.bounds.center.y * scopeFactor,
    )
    setFloatUniform("aa", MaskAntialiasPx)
    setColorUniform("tintColor", tintInShader)
  }
}

/**
 * La stessa lente con la profondità come interruttore: lo era prima della 3.0 e i chiamanti che
 * hanno ancora un `GlassOptics.depthEffect` booleano restano com'erano (0f / 1f, nessun cambio di
 * resa). Il peso fra 0 e 1 è l'overload sopra.
 */
internal fun BackdropEffectScope.slabGroupMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Boolean,
  tintInShader: Color,
) {
  slabGroupMorphLens(
    plan, scopeFactor, scratch, refractionHeightPx, refractionAmountPx, depthEffect.asDepthWeight(), tintInShader,
  )
}

/** La lente di Fluid-physics per una sagoma libera: stesso contratto di quella sopra. */
internal fun BackdropEffectScope.polyMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Float,
  tintInShader: Color,
  /** Il peso della frangia cromatica: vedi [slabGroupMorphLens]. */
  chromaticAberration: Float = 0f,
  /** L'ingrandimento della goccia in moto: vedi [slabGroupMorphLens]. */
  magnify: Float = 0f,
) {
  if (!isRuntimeShaderSupported()) return

  val height = refractionHeightPx.fastCoerceAtLeast(0.0001f)
  if (padding > 0f) {
    padding = physicsLensPadding(padding, height)
  }

  val count = plan.vertCount.coerceIn(3, PhysicsMaxRingVertices)
  for (i in 0 until count * 2) {
    scratch.verts[i] = plan.verts[i] * scopeFactor
  }
  for (i in count * 2 until scratch.verts.size) {
    scratch.verts[i] = 0f
  }
  // Il riquadro fuori dal quale lo shader esce subito (3.0), allargato di quanto la sagoma puo' sporgere
  // dal proprio anello (l'erosione) e dell'antialias.
  val margin = (plan.soften + MaskAntialiasPx) * scopeFactor + 2f
  val bx0 = plan.bounds.left * scopeFactor - margin
  val by0 = plan.bounds.top * scopeFactor - margin
  val bx1 = plan.bounds.right * scopeFactor + margin
  val by1 = plan.bounds.bottom * scopeFactor + margin
  val smoothPx = PolyNormalSmooth.toPx() * scopeFactor
  val smooth = smoothPx * smoothPx
  // La fascia della goccia: la sagoma a riposo e quanto il contorno se ne puo' allontanare, piu' la
  // corda dell'anello e un pixel di margine. Negativa fuori dalla goccia: nessuna scorciatoia.
  val rest = plan.dropRest
  val restFrame = rest?.frame
  val restRadii = rest?.cornerRadii
  val reach = if (rest != null) (plan.dropReach + DropReachMarginPx) * scopeFactor else -1f

  val currentPadding = padding
  val chroma = dispersionUniform(chromaticAberration)
  if (chroma > 0f) {
    runtimeShaderEffect("FluidPhysicsPolyDispersion", PolyMorphDispersionShaderString, "content") {
      setFloatUniform("offset", -currentPadding, -currentPadding)
      setFloatUniform("vertCount", count.toFloat())
      setFloatUniform("verts", scratch.verts)
      setFloatUniform("bbox", bx0, by0, bx1, by1)
      setFloatUniform("normalSmooth", smooth)
      setDropBand(restFrame, restRadii, reach, scopeFactor)
      setFloatUniform("soften", plan.soften * scopeFactor)
      setFloatUniform("refractionHeight", height)
      setFloatUniform("refractionAmount", -refractionAmountPx)
      setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
      setFloatUniform("magnify", magnify)
      setFloatUniform(
        "fieldCenter",
        plan.bounds.center.x * scopeFactor,
        plan.bounds.center.y * scopeFactor,
      )
      setFloatUniform(
        "fieldHalfSize",
        plan.bounds.width / 2f * scopeFactor,
        plan.bounds.height / 2f * scopeFactor,
      )
      setFloatUniform("chromaticAberration", chroma)
      setFloatUniform("aa", MaskAntialiasPx)
      setColorUniform("tintColor", tintInShader)
    }
    return
  }
  runtimeShaderEffect("FluidPhysicsPoly", PolyMorphRefractionShaderString, "content") {
    setFloatUniform("offset", -currentPadding, -currentPadding)
    setFloatUniform("vertCount", count.toFloat())
    setFloatUniform("verts", scratch.verts)
    setFloatUniform("bbox", bx0, by0, bx1, by1)
    setFloatUniform("normalSmooth", smooth)
    setDropBand(restFrame, restRadii, reach, scopeFactor)
    setFloatUniform("soften", plan.soften * scopeFactor)
    setFloatUniform("refractionHeight", height)
    setFloatUniform("refractionAmount", -refractionAmountPx)
    setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
    setFloatUniform("magnify", magnify)
    setFloatUniform(
      "fieldCenter",
      plan.bounds.center.x * scopeFactor,
      plan.bounds.center.y * scopeFactor,
    )
    setFloatUniform("aa", MaskAntialiasPx)
    setColorUniform("tintColor", tintInShader)
  }
}

/** Le uniform della fascia della goccia: vedi `PhysicsDropBandBlock`. */
private fun RuntimeShader.setDropBand(frame: Rect?, radii: FluidCornerRadii?, reach: Float, scopeFactor: Float) {
  if (frame == null || radii == null || reach < 0f) {
    setFloatUniform("restCenter", 0f, 0f)
    setFloatUniform("restHalfSize", 0f, 0f)
    setFloatUniform("restRadii", 0f, 0f, 0f, 0f)
    setFloatUniform("dropReach", -1f)
    return
  }
  setFloatUniform("restCenter", frame.center.x * scopeFactor, frame.center.y * scopeFactor)
  setFloatUniform("restHalfSize", frame.width / 2f * scopeFactor, frame.height / 2f * scopeFactor)
  setFloatUniform(
    "restRadii",
    radii.topLeft * scopeFactor,
    radii.topRight * scopeFactor,
    radii.bottomRight * scopeFactor,
    radii.bottomLeft * scopeFactor,
  )
  setFloatUniform("dropReach", reach)
}

/**
 * Il margine della fascia della goccia oltre lo scarto massimo dei suoi vertici, in pixel di layout:
 * la corda dell'anello sull'arco (meno di un quarto di pixel) e un pixel per gli arrotondamenti.
 */
internal const val DropReachMarginPx = 1.25f

/** La lente per sagoma libera con la profondità come interruttore: vedi l'overload a peso sopra. */
internal fun BackdropEffectScope.polyMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Boolean,
  tintInShader: Color,
) {
  polyMorphLens(
    plan, scopeFactor, scratch, refractionHeightPx, refractionAmountPx, depthEffect.asDepthWeight(), tintInShader,
  )
}

// ----------------------------------------------------------------------------------------------------------
// Il materiale nuovo (4.0, F1B.1): Fluid-physics disegnato dal campo di `glass/field`
// ----------------------------------------------------------------------------------------------------------
//
// A `Full`, dove il materiale nuovo può esistere (`resolvePhysicsGlass`), le due lenti qui sopra non si
// chiamano: la sagoma la disegna lo stesso corpo di `glassSurface` (`GlassBody8`, `GlassBodyRing`), con la
// stessa ricetta. Qui c'è la traduzione, tutta pura: dal piano del motore ai pezzi del campo, dal materiale del
// viaggio alla ricetta, e i due insieme negli ingressi del disegno. Le scene e le tracce del banco le scrivono
// queste stesse funzioni (`test/.../trace/`), quindi ciò che si guarda sul PC è ciò che l'engine imposta.

/** Su quale distanza la normale di un anello si media fra i lati vicini: vedi [PolyNormalSmooth]. */
internal const val PhysicsRingNormalSmoothDp: Float = 4f

/**
 * Il lato corto su cui una superficie fisica chiede la ricetta, in pixel di layout.
 *
 * La ricetta di `glassSurface` dipende dalla taglia solo attraverso il lato corto (banda della lente, cupola,
 * crescita dei grandi, velo scuro dei grandi), e un campo ha **una** ricetta per tutti i suoi pezzi: qui è quella
 * del pezzo col lato corto più grande. Per i tasti di una barra, che sono alti uguali, è la ricetta di ognuno; per
 * una lastra sola è la sua. Per la goccia è la sagoma a riposo che insegue, non il contorno che oscilla: la
 * ricetta non trema con la gelatina, e all'arrivo è quella della lastra ferma.
 */
internal fun physicsRecipeShortPx(plan: PhysicsRenderPlan): Float {
  plan.dropRest?.let { return it.frame.minDimension }
  if (plan.mode == PlanModePoly) return plan.bounds.minDimension
  var widest = 0f
  for (i in 0 until plan.pieceCount.coerceIn(0, PhysicsMaxPieces)) {
    widest = max(widest, 2f * min(plan.pieceRects[i * 4 + 2], plan.pieceRects[i * 4 + 3]))
  }
  return widest
}

/** Il lato corto su cui una forma ferma chiederebbe la ricetta, in pixel di layout: la regola di [physicsRecipeShortPx]. */
internal fun physicsFormShortPx(form: FluidForm): Float = when (form) {
  is FluidForm.Group -> form.pieces.maxOf { it.frame.minDimension }
  else -> form.frame.minDimension
}

/** Quanto ci mette il velo di una superficie che viaggia a passare tutto da quello dei piccoli a quello dei grandi. */
internal const val PhysicsLargeVeilSeconds: Float = 0.3f

/**
 * Il passo più lungo che [PhysicsLargeVeil] accetta fra due disegni: un disegno dopo una pausa (una superficie ferma
 * non si ridisegna) conta come un fotogramma a 30 Hz, non come il tempo passato.
 */
internal const val PhysicsLargeVeilMaxStepSeconds: Float = 1f / 30f

/**
 * Il peso del velo dei grandi che una superficie fisica mostra (F1B.2, sessione sul tablet dopo F1B.1, riga 14): sul
 * lato scuro un tasto ha il velo dei piccoli e un pannello quello dei grandi, più profondo di 33 livelli su 255 sul
 * fondo scuro (`GlassLiquidVeils.DarkLighter` e `DarkLarge`). Il peso seguiva il lato corto di ogni fotogramma, e la
 * molla che apre un menu gli fa attraversare la fascia dei due veli (48–96 dp) in due o tre fotogrammi: sul tablet il
 * tasto grigio diventava il pannello scuro a −6 e −22 livelli, a +92…+141 ms. Nessuna funzione della taglia o del
 * progresso lo può spalmare: la forma cambia quasi tutta in una decina di fotogrammi.
 *
 * Qui il peso è uno stato che va **verso quello della forma d'arrivo** ([target]) a passo costante, tutto il passaggio
 * in [PhysicsLargeVeilSeconds]: parte con la forma e arriva prima che la forma si posi (un menu si apre in ~450 ms,
 * si chiude in ~430), quindi al passaggio di consegne con la lastra ferma (o col tasto che torna) è già quello della
 * lastra. Fra due forme della stessa famiglia di taglia (i tasti della barra, un tasto che diventa due) non si muove.
 * Al primo disegno parte dal peso della forma di partenza ([origin]): la superficie che nasce col viaggio prende il
 * posto di un vetro fermo con il suo velo.
 *
 * Il tempo è quello del disegno, passato da chi chiama: l'engine il `System.nanoTime` del disegno, il banco
 * l'istante del fotogramma.
 */
internal class PhysicsLargeVeil {
  private var shown = Float.NaN
  private var lastNanos = 0L

  /** Il peso da disegnare adesso, 0..1. */
  fun next(origin: Float, target: Float, nowNanos: Long): Float {
    val goal = clampGlassUnit(if (target.isFinite()) target else 0f)
    if (!shown.isFinite()) {
      shown = clampGlassUnit(if (origin.isFinite()) origin else goal)
      lastNanos = nowNanos
      return shown
    }
    val dt = ((nowNanos - lastNanos).coerceAtLeast(0L) / 1_000_000_000f).coerceAtMost(PhysicsLargeVeilMaxStepSeconds)
    lastNanos = nowNanos
    val step = dt / PhysicsLargeVeilSeconds
    shown = if (goal > shown) min(goal, shown + step) else max(goal, shown - step)
    return shown
  }

  /** Dimentica: il prossimo disegno riparte dalla forma di partenza. */
  fun reset() {
    shown = Float.NaN
  }
}

/**
 * I pezzi del piano di Fluid-physics come li riceve il campo ([GlassFieldPlan]), nello spazio di chi disegna.
 *
 * Tre cose separano i due piani, e sono il motivo per cui questa funzione esiste:
 *  - i **raggi** e il **raccordo** sono quelli che il campo vede ([PhysicsRenderPlan.fieldRadii],
 *    [PhysicsRenderPlan.fieldSmooth]), cioè la regola forma → piano di `glassSurface`: a riposo una lastra fisica
 *    ha la sagoma della lastra ferma, pixel per pixel;
 *  - il **raggio di fusione** è quello del gruppo per quanto il collo è vivo ([PhysicsRenderPlan.fusion]): zero
 *    a riposo, dove due pezzi distinti sono separati del tutto;
 *  - le lunghezze passano per [scale] (pixel di chi disegna per pixel di layout: la frazione di risoluzione della
 *    cattura).
 */
internal fun physicsFieldPlan(plan: PhysicsRenderPlan, scale: Float, out: GlassFieldPlan): GlassFieldPlan {
  out.clear()
  val count = plan.pieceCount.coerceIn(0, min(PhysicsMaxPieces, out.pieces))
  val fusion = plan.fusion.coerceIn(0f, 1f)
  for (i in 0 until count) {
    val at = i * 4
    out.add(
      soft = if (i == 0) 0f else physicsPieceSoft(plan, i) * scale,
      centerX = plan.pieceRects[at] * scale,
      centerY = plan.pieceRects[at + 1] * scale,
      halfWidth = plan.pieceRects[at + 2] * scale,
      halfHeight = plan.pieceRects[at + 3] * scale,
      topLeft = plan.fieldRadii[at] * scale,
      topRight = plan.fieldRadii[at + 1] * scale,
      bottomRight = plan.fieldRadii[at + 2] * scale,
      bottomLeft = plan.fieldRadii[at + 3] * scale,
      blend = if (i == 0) 0f else plan.pieceBlend[i] * fusion * scale,
      smoothing = plan.fieldSmooth[i],
    )
  }
  return out
}

/**
 * Il raggio su cui il pezzo [index] pesa normale e centro col già fuso (`pieceSoft`, F1B.2), in pixel di layout: il
 * suo raggio di fusione **nominale** (non quello del collo di adesso) per quanto si sovrappone ai pezzi che lo
 * precedono. Zero fra pezzi che non si toccano, cioè a riposo e alle due estremità di ogni viaggio fra tasti.
 *
 * Perché: in viaggio il collo nasce e muore col progresso ([PhysicsRenderPlan.fusion]), e verso l'arrivo di un gruppo
 * che diventa un menu i tre pezzi si coprono quasi del tutto con il collo già spento. Lì l'unione netta sceglie a
 * scatto di quale pezzo è un punto, e normale e centro saltano da uno all'altro: la lente mostrava due righe
 * verticali per 3–4 fotogrammi (`moto/f1b/gruppo-diventa-menu/gruppo-diventa-menu-cuciture.png`). Con questo raggio
 * la lente vede i pezzi sovrapposti come una lente sola; la sagoma non cambia.
 *
 * La sovrapposizione è quella dei riquadri: quanto entrano l'uno nell'altro lungo l'asse dove entrano meno, sul
 * raggio nominale, addolcita. Continua nel tempo, perché lo è la geometria.
 */
internal fun physicsPieceSoft(plan: PhysicsRenderPlan, index: Int): Float {
  // Il raggio chiesto, non quello effettivo: dove i pezzi si compenetrano il varco spegne il secondo, ed è proprio lì
  // che normale e centro vanno pesati.
  val nominal = plan.nominalBlend
  if (!(nominal > 0f)) return 0f
  val at = index * 4
  var overlap = 0f
  for (j in 0 until index) {
    val bt = j * 4
    val gapX = abs(plan.pieceRects[at] - plan.pieceRects[bt]) - (plan.pieceRects[at + 2] + plan.pieceRects[bt + 2])
    val gapY = abs(plan.pieceRects[at + 1] - plan.pieceRects[bt + 1]) - (plan.pieceRects[at + 3] + plan.pieceRects[bt + 3])
    val depth = -max(gapX, gapY)
    if (depth > 0f) overlap = max(overlap, (depth / nominal).coerceIn(0f, 1f))
  }
  return nominal * overlap * overlap * (3f - 2f * overlap)
}

/**
 * L'anello del piano di Fluid-physics come lo riceve il nucleo ad anello, nello spazio della cattura.
 *
 * @param scale pixel della cattura per pixel di layout.
 * @param density pixel della cattura per dp.
 * @param bandOuter fin dove la distanza deve essere vera fuori dalla sagoma, in pixel della cattura.
 * @param bandInner lo stesso dentro: almeno quanto il corpo legge accanto al contorno.
 */
internal fun physicsRingField(
  plan: PhysicsRenderPlan,
  scale: Float,
  density: Float,
  bandOuter: Float,
  bandInner: Float,
  out: GlassRingField,
): GlassRingField {
  val count = plan.vertCount.coerceIn(0, GlassFieldRingVertices)
  out.count = count
  for (i in 0 until count * 2) out.verts[i] = plan.verts[i] * scale
  // Le caselle oltre il conteggio non si leggono, ma lo uniform va mandato per intero: niente residui.
  for (i in count * 2 until out.verts.size) out.verts[i] = 0f
  val bounds = plan.bounds
  val soften = plan.soften * scale
  // L'erosione del campo allarga la sagoma di `soften`: il riquadro la deve contenere.
  val margin = soften + bandOuter
  out.bbox[0] = bounds.left * scale - margin
  out.bbox[1] = bounds.top * scale - margin
  out.bbox[2] = bounds.right * scale + margin
  out.bbox[3] = bounds.bottom * scale + margin
  val smoothPx = PhysicsRingNormalSmoothDp * density
  out.normalSmooth = smoothPx * smoothPx
  out.soften = soften
  out.centerX = bounds.center.x * scale
  out.centerY = bounds.center.y * scale
  out.bandOuter = bandOuter
  out.bandInner = bandInner
  val rest = plan.dropRest
  if (rest == null) {
    out.restCenterX = 0f
    out.restCenterY = 0f
    out.restHalfWidth = 0f
    out.restHalfHeight = 0f
    out.restRadii.fill(0f)
    out.restSmooth = 0f
    out.restBlend = 0f
    out.dropReach = -1f
  } else {
    val f = rest.frame
    val half = f.minDimension / 2f
    out.restCenterX = f.center.x * scale
    out.restCenterY = f.center.y * scale
    out.restHalfWidth = f.width / 2f * scale
    out.restHalfHeight = f.height / 2f * scale
    // La lastra a riposo come la vede il campo (F1B.2): gli stessi raggi e lo stesso raccordo del nucleo a lastre
    // che la sostituirà (`buildRestPlan`), non i nominali ad arco di cerchio. È la sagoma su cui l'anello si posa
    // (`RoundedRectPerimeter.setField`), quella della scorciatoia della fascia e quella verso cui va il campo.
    val smoothing = if (rest.smoothing.isFinite()) rest.smoothing else 0f
    val g = glassSlabGeometry(
      width = f.width,
      height = f.height,
      topLeft = rest.cornerRadii.topLeft,
      topRight = rest.cornerRadii.topRight,
      bottomRight = rest.cornerRadii.bottomRight,
      bottomLeft = rest.cornerRadii.bottomLeft,
      smoothing = smoothing,
      continuous = smoothing > 0f,
      small = glassSlabIsSmall(f.maxDimension),
      out = out.restGeometry,
    )
    out.restRadii[0] = g.topLeft.coerceIn(0f, half) * scale
    out.restRadii[1] = g.topRight.coerceIn(0f, half) * scale
    out.restRadii[2] = g.bottomRight.coerceIn(0f, half) * scale
    out.restRadii[3] = g.bottomLeft.coerceIn(0f, half) * scale
    out.restSmooth = g.smoothing
    out.restBlend = plan.dropSettle
    out.dropReach = (plan.dropReach + DropReachMarginPx) * scale
  }
  return out
}

/** Sopra questa lente in moto (`LiquidDrop.lens`) il campo della goccia è tutto anello: vedi [physicsDropSettle]. */
internal const val DropSettleLensFrom: Float = 0.05f

/** Sotto questa lente in moto il campo è tutto lastra: la soglia di quiete della goccia (`LiquidDrop.QuietLens`). */
internal const val DropSettleLensTo: Float = 0.002f

/** Sopra questo scarto dalla sagoma a riposo, in dp, il campo è tutto anello. */
internal const val DropSettleReachFromDp: Float = 2.5f

/** Sotto questo scarto il contorno sta già sulla lastra: lo scarto non trattiene più il campo. */
internal const val DropSettleReachToDp: Float = 0.25f

/**
 * Quanto il campo di una goccia è già quello della lastra che la sostituirà (F1B.2, `restBlend` di
 * `GlassFieldKernelRing`): zero in volo, uno quando si posa.
 *
 * Perché: la goccia si posa cambiando nucleo, dall'anello alla lastra, e i due nuclei danno la stessa sagoma ma non
 * lo stesso campo dentro: la normale dell'anello è la media delle normali dei lati, quella della lastra è allargata
 * negli angoli. Il riflesso la legge, e alla posa del menu negli angoli c'era uno scalino fino a 74 livelli su 255
 * (63 anche con gli angoli dell'anello sul contorno della lastra). Questo peso porta il campo dall'uno all'altro
 * mentre la goccia si calma, così al cambio di nucleo non resta niente da cambiare.
 *
 * È il prodotto di due rampe sullo stesso stato che decide la posa: la lente in moto ([lens], che decresce in modo
 * esponenziale) e lo scarto del contorno dalla sagoma a riposo ([reachDp]), tutte e due **in scala logaritmica**,
 * cioè lineari nel tempo per una coda che si smorza: il passaggio dura una decina di fotogrammi (a 60 Hz) invece di
 * uno. Lo scarto tiene il peso a zero finché la goccia è ancora deformata: la gelatina in volo resta quella di
 * sempre, e il contorno si sposta verso la lastra di meno di mezzo pixel. Senza stato: si legge dal fotogramma.
 */
internal fun physicsDropSettle(lens: Float, reachDp: Float): Float {
  fun logRamp(value: Float, from: Float, to: Float): Float {
    if (!value.isFinite()) return 0f
    if (value <= to) return 1f
    if (value >= from) return 0f
    return (ln(from / value) / ln(from / to)).coerceIn(0f, 1f)
  }
  return logRamp(lens, DropSettleLensFrom, DropSettleLensTo) * logRamp(reachDp, DropSettleReachFromDp, DropSettleReachToDp)
}

/** Gli appoggi di [physicsLiquidRecipe]: la ricetta del capo d'arrivo (che è anche il risultato) e quella di partenza. */
internal class PhysicsLiquidRecipes {
  val to = GlassRecipe()
  val from = GlassRecipe()
}

/**
 * La ricetta del materiale nuovo per una superficie fisica in un fotogramma: **quella di `glassSurface`** per lo
 * stesso ruolo, la stessa ottica, la stessa taglia e lo stesso lato, più ciò che è del viaggio.
 *
 *  - l'**ottica** è quella mescolata fra partenza e arrivo ([PhysicsMaterialSource], la stessa aritmetica
 *    `a + (b − a) · f` di sempre): ai due capi è l'ottica del capo, alla lettera;
 *  - la **tinta** di partenza e quella d'arrivo danno due ricette, mescolate campo per campo ([lerpGlassRecipe]):
 *    una pellicola dipinta com'è (`Fixed`) prende il posto del velo, una tinta di Apple (`Colored`) gli sta
 *    sopra, e fra le due il viaggio scivola senza un fotogramma in cui il velo scatta;
 *  - la **frangia** è un peso ([PhysicsMaterial.dispersion]), non l'interruttore dell'ottica: un tasto con la
 *    frangia che diventa un pannello senza la perde lungo il viaggio;
 *  - il materiale **in viaggio** ([glassTravelRecipe]) al peso [motion]: a zero niente cambia.
 *
 * Con [motion] a zero, senza partenza, è `glassRecipe` e basta: lo prova `PhysicsLiquidRecipeTest`.
 *
 * @param shortDp il lato corto in dp ([physicsRecipeShortPx] per la densità dello schermo).
 * @param motion quanto la sagoma si muove, 0..1 (`LiquidDrop.lens`: zero esatto a goccia posata).
 */
internal fun physicsLiquidRecipe(
  role: GlassRole,
  material: PhysicsMaterial,
  shortDp: Float,
  darkSide: Boolean,
  amount: Float,
  quality: Float,
  press: Float,
  tint: GlassTint,
  tintFrom: GlassTint?,
  tintBlend: Float,
  nested: Boolean,
  motion: Float,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  scratch: PhysicsLiquidRecipes = PhysicsLiquidRecipes(),
  /** Il peso del velo dei grandi ([PhysicsLargeVeil]); non finito = dalla taglia [shortDp], come una lastra ferma. */
  largeVeil: Float = Float.NaN,
): GlassRecipe {
  fun fill(of: GlassTint, out: GlassRecipe): GlassRecipe {
    val mode = glassTintLiquidMode(of)
    return glassRecipe(
      role = role,
      widthDp = shortDp,
      heightDp = shortDp,
      darkSide = darkSide,
      optics = material.optics,
      intensity = amount,
      quality = quality,
      pressed = press,
      film = if (mode == GlassTintMode.Fixed) of.overlay else Color.Unspecified,
      tint = if (mode == GlassTintMode.Colored) of.overlay else Color.Unspecified,
      nested = nested,
      profile = profile,
      largeVeil = largeVeil,
      out = out,
    )
  }
  val recipe = fill(tint, scratch.to)
  val blend = clampGlassUnit(tintBlend)
  if (tintFrom != null && blend < 1f) {
    fill(tintFrom, scratch.from)
    lerpGlassRecipe(scratch.from, recipe, blend, recipe)
  }
  recipe.dispersion = if (quality > 0.75f) GlassLiquidDispersion * clampGlassUnit(material.dispersion) else 0f
  return glassTravelRecipe(recipe, profile.travel, motion, quality)
}

/**
 * Gli ingressi del disegno di una superficie fisica col materiale nuovo: il piano del motore e la ricetta,
 * insieme, **nelle unità di chi li riceve** ([GlassDrawInputs]).
 *
 * La luce sta **sempre nel corpo** (`lightInBody`), ferma o in viaggio, lastra o anello: il corpo compone filo,
 * riflesso e bagliore sulla sagoma di quel fotogramma, ponti compresi. Una superficie fisica ferma è così lo
 * stesso programma con gli stessi uniform del primo fotogramma del suo viaggio: fra le due non c'è niente che
 * possa scattare. (La prima stesura teneva la luce sul nodo da ferma, come `glassSurface`: sul banco quei due
 * disegni, che il palco non ritaglia sulla sagoma, lasciavano sul contorno un anello di un pixel diverso dal
 * viaggio.) Contro una `glassSurface` la differenza è dentro due livelli su 255, e sul pixel di contorno la
 * chiude il **ritaglio emulato** ([GlassDrawInputs.nodeClip]).
 *
 * @param scale pixel della cattura per pixel di layout.
 * @param screenDensity pixel dello schermo per dp.
 * @param paddingPx il margine della cattura, in pixel suoi, già intero: quello fisso del viaggio.
 * @param touchX con [touchY], il dito in pixel di layout (non finito = il centro della sagoma).
 */
internal fun physicsLiquidInputs(
  plan: PhysicsRenderPlan,
  recipe: GlassRecipe,
  scale: Float,
  screenDensity: Float,
  paddingPx: Float,
  glowAmount: Float = 0f,
  touchX: Float = Float.NaN,
  touchY: Float = Float.NaN,
  /** Il ritaglio emulato ([GlassDrawInputs.nodeClip]); falso solo dalla manopola di diagnosi `travelclip=off`. */
  nodeClip: Boolean = true,
  out: GlassDrawInputs = GlassDrawInputs(GlassFieldMaxPieces),
): GlassDrawInputs {
  val captureDensity = screenDensity * scale
  out.setBodyMaterial(recipe, captureDensity, paddingPx)
  val bounds = plan.bounds
  val finger = touchX.isFinite() && touchY.isFinite()
  out.setLightMaterial(
    recipe = recipe,
    density = screenDensity,
    glowAmount = glowAmount,
    glowCenterX = if (finger) touchX.coerceIn(bounds.left, max(bounds.left, bounds.right)) else bounds.center.x,
    glowCenterY = if (finger) touchY.coerceIn(bounds.top, max(bounds.top, bounds.bottom)) else bounds.center.y,
    glowRadius = bounds.maxDimension,
  )
  out.setBodyLight(recipe, captureDensity, scale)
  // L'ombra in corpo (F1B.2): quella della maschera a riposo di una lastra di questa taglia. È ciò che fa sparire
  // l'ombra a parte di Fluid-physics sotto il materiale nuovo.
  out.setBodyShadow(recipe, screenDensity, scale)
  if (plan.mode == PlanModePoly) {
    out.kernel = GlassBodyKernel.Ring
    out.lightInBody = true
    // Dentro, la distanza deve essere vera fin dove il corpo la legge: la banda della lente, l'ombreggiatura, e
    // la luce (filo, riga chiara, rientro del riflesso: `GlassLightBlock` esce oltre questa profondità).
    val light = out.bodyRimWidth * 4f + out.bodyReflexDepth + 2f
    val inner = max(max(out.thickness, out.bodyShadeDepth), light) + out.aa + 1f
    // Fuori, la distanza deve essere vera fin dove l'ombra la legge: il punto spostato (`p - shadowOffset`) di un
    // pixel dentro la portata dell'ombra sta fino a portata più scostamento dal contorno.
    val shadow = out.shadowReach
    val outer = (if (shadow > 0f) shadow + kotlin.math.hypot(out.shadowOffsetX, out.shadowOffsetY) else 0f) + out.aa + 1f
    physicsRingField(plan, scale, captureDensity, bandOuter = outer, bandInner = inner, out = out.ring)
    out.body.clear()
    // Il margine d'angolo del ritaglio emulato: un anello non ne ha. Una goccia che si posa prende quello della lastra
    // che la sostituirà, con lo stesso peso del campo (F1B.2): senza, all'ultimo fotogramma ad anello i pixel del
    // contorno negli angoli restavano fino a 20 livelli diversi dal primo a lastra.
    val ring = out.ring
    out.nodeClipCorner = if (ring.dropReach >= 0f && ring.restBlend > 0f) {
      val widest = max(max(ring.restRadii[0], ring.restRadii[1]), max(ring.restRadii[2], ring.restRadii[3]))
      glassNodeClipCorner(widest, ring.restSmooth) * ring.restBlend
    } else {
      0f
    }
  } else {
    out.kernel = GlassBodyKernel.Eight
    out.lightInBody = true
    physicsFieldPlan(plan, scale, out.body)
    // Il margine d'angolo del ritaglio emulato: quello del pezzo che ne chiede di più.
    var corner = 0f
    for (i in 0 until min(plan.pieceCount, GlassFieldMaxPieces)) {
      val at = i * 4
      val widest =
        max(max(plan.fieldRadii[at], plan.fieldRadii[at + 1]), max(plan.fieldRadii[at + 2], plan.fieldRadii[at + 3]))
      corner = max(corner, glassNodeClipCorner(widest * scale, plan.fieldSmooth[i]))
    }
    out.nodeClipCorner = corner
  }
  out.light.clear()
  // Il ritaglio hardware di una superficie fisica è il rettangolo del nodo: la sagoma la ritaglia il corpo, che
  // emula anche l'antialias del ritaglio che una lastra ha dal suo nodo.
  out.clipScale = 1f
  out.nodeClip = nodeClip
  return out
}

/**
 * Lo stato di una superficie fisica disegnata dal materiale nuovo: ciò che `fluidPhysicsSurface` ricorda fra
 * una composizione e l'altra, e le due lambda che dà al nodo. È il gemello di `GlassLiquidSurface` per una
 * sagoma che può viaggiare.
 *
 * Tutto ciò che cambia per fotogramma (intensità, miscele, qualità, pressione, e la sagoma, che sta nello stato)
 * si legge **in disegno**: uno stato letto negli effetti rifà la sola catena, senza ricomporre.
 *
 * Il **margine della cattura** si decide qui, una volta ([paddingDp]): non dipende dalla taglia della sagoma,
 * quindi un viaggio non rifà la cattura a ogni fotogramma.
 */
@Stable
internal class PhysicsLiquidSurface(
  private val state: FluidPhysicsState,
  private val role: GlassRole,
  private val material: PhysicsMaterialSource,
  private val darkSide: Boolean,
  private val screenDensity: Float,
  private val tint: GlassTint,
  private val tintFrom: GlassTint?,
  private val pressState: FluidPressState?,
  private val glowWeight: Float,
  private val profile: GlassLiquidProfile,
  private val intensity: () -> Float,
  private val tintBlend: () -> Float,
  private val opticsBlend: () -> Float,
  private val quality: () -> Float,
  private val layoutWidth: () -> Float,
  private val onPanel: () -> Boolean,
) {

  /** Il margine della cattura per tutta la vita della superficie: vedi [glassTravelPaddingDp]. */
  val paddingDp: Float = glassTravelPaddingDp(role, material.target, material.origin, profile)

  private val inputs = GlassDrawInputs(GlassFieldMaxPieces)
  private val recipes = PhysicsLiquidRecipes()
  private val chain = GlassBodyChain()
  private val largeVeil = PhysicsLargeVeil()

  // Il vetro annidato, come in `GlassLiquidSurface`: lo dice il nodo (o il fondo), al primo giro degli effetti.
  private var nestedSeen = false
  private var nestedState by mutableStateOf(false)

  private fun noteNesting(scope: BackdropEffectScope) {
    val panel = onPanel()
    val onGlass = (scope as? GlassNestingScope)?.standsOnGlass == true || panel
    if (onGlass != nestedSeen) {
      nestedSeen = onGlass
      nestedState = onGlass
    }
  }

  /**
   * Riempie [inputs] per il fotogramma di adesso. Falso se non c'è niente da disegnare.
   *
   * @param scale pixel della cattura per pixel di layout (uno per chi disegna sul nodo).
   */
  private fun fill(scale: Float, nested: Boolean): Boolean {
    val amount = clampGlassUnit(intensity())
    if (amount <= 0.001f || !(scale > 0f) || !(screenDensity > 0f)) return false
    val plan = state.ensurePlan()
    val q = quality()
    val press = pressState?.progress ?: 0f
    val shortDp = physicsRecipeShortPx(plan) / screenDensity
    // Il velo dei grandi va verso quello della forma d'arrivo a passo costante (F1B.2): vedi [PhysicsLargeVeil].
    val origin = state.journeyOriginNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it) / screenDensity) }
    val target = state.journeyTargetNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it) / screenDensity) }
    val now = glassLiquidLargeVeilGain(shortDp)
    val large = largeVeil.next(origin = origin ?: now, target = target ?: now, nowNanos = System.nanoTime())
    val recipe = physicsLiquidRecipe(
      role = role,
      material = material.at(opticsBlend()),
      shortDp = shortDp,
      darkSide = darkSide,
      amount = amount,
      quality = q,
      press = press,
      tint = tint,
      tintFrom = tintFrom,
      tintBlend = tintBlend(),
      nested = nested,
      // La lente in moto: zero esatto a goccia posata, quindi a riposo la ricetta è quella della lastra.
      motion = state.liquid.lens,
      profile = profile,
      scratch = recipes,
      largeVeil = large,
    )
    val finger = pressState?.glow
    val bounds = plan.bounds
    physicsLiquidInputs(
      plan = plan,
      recipe = recipe,
      scale = scale,
      screenDensity = screenDensity,
      paddingPx = floor(max(paddingDp * screenDensity * scale, 0f)),
      glowAmount = clampGlassUnit(press) * glowWeight,
      touchX = if (finger != null) bounds.left + finger.x else Float.NaN,
      touchY = if (finger != null) bounds.top + finger.y else Float.NaN,
      nodeClip = profile.travelNodeClip,
      out = inputs,
    )
    return true
  }

  /** `effects` di `drawBackdrop`: la catena del corpo, sulla cattura. */
  val effects: BackdropEffectScope.() -> Unit = {
    noteNesting(this)
    if (size.isSpecified && size.width > 0f && size.height > 0f) {
      if (fill(scopeFactor(size.width, layoutWidth()), nestedSeen)) glassBody(inputs, chain)
    }
  }

  /**
   * `onDrawLight` di `drawBackdrop`: non disegna niente, perché la luce di una superficie fisica è nel corpo
   * ([physicsLiquidInputs]). C'è perché è da lei che il nodo sa di portare il materiale nuovo (il censimento di
   * `FluidGlassDiagnostics` e l'osservazione degli effetti in disegno).
   */
  val onDrawLight: DrawScope.() -> Unit = {}
}
