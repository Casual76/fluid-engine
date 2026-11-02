package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassSizedOptics
import kotlin.math.max

/**
 * Il materiale di una superficie fisica in un fotogramma: l'ottica, e i due interruttori di
 * [GlassOptics] come pesi.
 *
 * `depthEffect` e `dispersion` sono Boolean in [GlassOptics] perche' una lastra ferma o li ha o non
 * li ha. Una superficie che *diventa* un'altra cosa — il tasto con la cupola e la frangia che si apre
 * in un pannello piatto — non puo' accenderli o spegnerli in un fotogramma: lo shader prende due
 * float, e qui sono due float. A riposo valgono esattamente 0 o 1, cioe' il Boolean di sempre.
 */
@Immutable
internal class PhysicsMaterial(
  /** Gia' passata da `sanitized()`. I Boolean dentro non contano: contano i due pesi qui sotto. */
  val optics: GlassOptics,
  val depthEffect: Float,
  val dispersion: Float,
)

internal fun GlassOptics.asPhysicsMaterial(): PhysicsMaterial =
  PhysicsMaterial(this, depthEffect.asDepthWeight(), dispersion.asDepthWeight())

/**
 * L'ottica a meta' strada fra [from] e [to]: ogni numero sfuma, nessuno scatta.
 *
 * [t] e' tenuto fra 0 e 1 (un NaN vale 0): il materiale non oltrepassa mai i propri capi, anche se la
 * molla che lo guida lo fa — una sfocatura oltre quella del pannello, o una rifrazione sotto quella
 * del tasto, non sono un rimbalzo ma un terzo materiale che nessuno ha chiesto. I due Boolean
 * passano al capo d'arrivo a meta' strada; chi disegna usa i pesi di [physicsMaterialAt], non loro.
 *
 * Pura, e il motivo e' il vincolo del padding: la sfocatura non deve mai superare l'altezza della
 * lente che la copre, per nessun t (vedi [physicsCapturePaddingPx]), e un vincolo su un intervallo
 * si inchioda in un test, non a occhio.
 */
internal fun lerpGlassOptics(from: GlassOptics, to: GlassOptics, t: Float): GlassOptics {
  val f = clampGlassUnit(t)
  if (f <= 0f) return from
  if (f >= 1f) return to
  // `a + (b - a) * f`, non il `lerp` di Compose (`(1 - f) * a + f * b`): fra due capi uguali
  // quello sbaglia di un ulp, e un'altezza di lente un ulp sotto la sfocatura apre un margine di
  // cattura di mezzo milionesimo di pixel — abbastanza per rifare la cattura.
  fun mix(a: Float, b: Float) = a + (b - a) * f
  fun mix(a: Dp, b: Dp) = (a.value + (b.value - a.value) * f).dp
  return GlassOptics(
    blurScale = mix(from.blurScale, to.blurScale),
    refractionHeight = mix(from.refractionHeight, to.refractionHeight),
    refractionAmount = mix(from.refractionAmount, to.refractionAmount),
    depthEffect = if (f < 0.5f) from.depthEffect else to.depthEffect,
    dispersion = if (f < 0.5f) from.dispersion else to.dispersion,
    vibrancy = mix(from.vibrancy, to.vibrancy),
    highlightWidth = mix(from.highlightWidth, to.highlightWidth),
    highlightAlpha = mix(from.highlightAlpha, to.highlightAlpha),
    highlightAngle = mix(from.highlightAngle, to.highlightAngle),
    innerShadowRadius = mix(from.innerShadowRadius, to.innerShadowRadius),
    innerShadowAlpha = mix(from.innerShadowAlpha, to.innerShadowAlpha),
    shadowRadius = mix(from.shadowRadius, to.shadowRadius),
    shadowAlpha = mix(from.shadowAlpha, to.shadowAlpha),
    pressedDepthBoost = mix(from.pressedDepthBoost, to.pressedDepthBoost),
    edgeFloor = mix(from.edgeFloor, to.edgeFloor),
    backdropResolution = mix(from.backdropResolution, to.backdropResolution),
    sizeResponse = mix(from.sizeResponse, to.sizeResponse),
  )
}

/**
 * Il materiale a [t] fra [from] e [to], con la cupola e la frangia come pesi: [lerpGlassOptics] piu'
 * i due interruttori sfumati. Senza [from] e' [to], tale e quale.
 */
internal fun physicsMaterialAt(from: GlassOptics?, to: GlassOptics, t: Float): PhysicsMaterial {
  if (from == null) return to.asPhysicsMaterial()
  val f = clampGlassUnit(t)
  return PhysicsMaterial(
    optics = lerpGlassOptics(from, to, f),
    depthEffect = from.depthEffect.asDepthWeight() + (to.depthEffect.asDepthWeight() - from.depthEffect.asDepthWeight()) * f,
    dispersion = from.dispersion.asDepthWeight() + (to.dispersion.asDepthWeight() - from.dispersion.asDepthWeight()) * f,
  )
}

/**
 * La sorgente del materiale di una superficie: fissa senza [from], sfumata con. Tiene l'ultimo
 * risultato, perche' in un fotogramma il materiale lo chiedono in tre (gli effetti, il bordo,
 * l'ombra) e la miscela e' la stessa: un'ottica nuova per fotogramma, non tre.
 */
internal class PhysicsMaterialSource(private val from: GlassOptics?, private val to: GlassOptics) {
  private val fixed = to.asPhysicsMaterial()
  private var lastBlend = Float.NaN
  private var last: PhysicsMaterial = fixed

  /** Il capo d'arrivo: e' l'ottica che una superficie dichiara, e a cui si tara la cattura. */
  val target: GlassOptics get() = to

  /** Il capo di partenza, se c'e'. */
  val origin: GlassOptics? get() = from

  fun at(blend: Float): PhysicsMaterial {
    if (from == null) return fixed
    if (blend == lastBlend) return last
    last = physicsMaterialAt(from, to, blend)
    lastBlend = blend
    return last
  }
}

/**
 * Gli uniform della lente fisica per un fotogramma, gia' in pixel dello scope.
 *
 * Sono **i numeri di `glassSurface`**: la stessa regola della taglia ([glassSizedOptics]), la stessa
 * profondita' (intensita' per qualita'), la stessa spinta della pressione sullo spostamento
 * (`1 + pressione * pressedDepthBoost`) e la stessa frangia (solo sopra qualita' 0,75). E' questo che
 * fa di una superficie fisica ferma una lastra: non una ricetta simile, la stessa funzione.
 */
@Immutable
internal class PhysicsLensUniforms(
  /** Il raggio da dare a `blur()`. */
  val blurPx: Float,
  /** L'altezza della lente, gia' per la profondita'. */
  val refractionHeightPx: Float,
  /** Lo spostamento al bordo, gia' per la profondita' e per la pressione. */
  val refractionAmountPx: Float,
  /** Il peso della cupola, 0..1. */
  val depthEffect: Float,
  /** Il peso della frangia cromatica, 0..1: zero sotto la qualita' a cui `glassSurface` la spegne. */
  val dispersion: Float,
)

/**
 * La lente di una superficie fisica, calcolata come la calcola `glassSurface`
 * (`GlassMaterial.kt`, il blocco `effects`): vedi [PhysicsLensUniforms].
 *
 * @param amount intensita' del materiale, gia' fra 0 e 1.
 * @param quality il livello di [dev.antigravity.fluidengine.ui.fluid.FluidGlassQuality], 1 se nessuno.
 * @param minDimPx il lato corto della *sagoma* in pixel dello scope (la taglia che decide la regola).
 * @param lensAvailable falso dove non c'e' la lente (tier Balanced): la regola della taglia si spegne.
 * @param press la pressione del dito, 0..1: approfondisce lo spostamento, mai l'altezza — l'altezza
 *   tiene il padding della cattura, e una pressione non deve sporcarla.
 */
internal fun physicsLensUniforms(
  material: PhysicsMaterial,
  baseBlurPx: Float,
  amount: Float,
  quality: Float,
  minDimPx: Float,
  density: Float,
  lensAvailable: Boolean,
  press: Float = 0f,
): PhysicsLensUniforms {
  val optics = material.optics
  val sized = glassSizedOptics(
    optics = optics,
    baseBlurPx = baseBlurPx,
    amount = amount,
    quality = quality,
    minDimPx = minDimPx,
    density = density,
    lensAvailable = lensAvailable,
  )
  // `glassSurface`: depth = opticalDepth * amount * q, e una superficie fisica non ha un'opticalDepth
  // propria (vale 1).
  val depth = amount * quality
  val pressed = clampGlassUnit(press)
  return PhysicsLensUniforms(
    blurPx = sized.blurPx,
    refractionHeightPx = sized.refractionHeightPx * depth,
    refractionAmountPx = sized.refractionAmountPx * depth * (1f + pressed * optics.pressedDepthBoost),
    depthEffect = depthEffectUniform(material.depthEffect),
    // Sette campioni per pixel sono la prima cosa che se ne va a pagina in movimento: la stessa
    // soglia di `glassSurface` (`dispersion && q > 0.75`).
    dispersion = if (quality > 0.75f) clampGlassUnit(material.dispersion) else 0f,
  )
}

/**
 * Il margine della cattura che la catena lascera' con questi uniform: quello che `blur()` e la lente
 * calcolano davvero (vedi `effects/Blur.kt` e `slabGroupMorphLens`).
 *
 * `blur()` allarga il margine solo se c'e' gia' un effetto in catena — la vividezza, che ogni ruolo
 * di serie ha — e la lente lo riduce della propria altezza. Il contratto di Fluid-physics vuole che
 * questo numero **non cambi mai durante un viaggio**: a materiale e qualita' pieni vale zero, a ogni
 * taglia e a ogni miscela fra due ottiche, perche' la sfocatura non supera mai l'altezza che la copre.
 */
internal fun physicsCapturePaddingPx(uniforms: PhysicsLensUniforms, vibrancyApplied: Boolean): Float {
  var padding = if (vibrancyApplied && uniforms.blurPx > 0f) uniforms.blurPx else 0f
  if (padding > 0f) padding = physicsLensPadding(padding, max(uniforms.refractionHeightPx, 0.0001f))
  return padding
}

/**
 * Il margine che resta dopo la lente: `max(0, margine - altezza)`, con quello che sta sotto un quarto
 * di pixel portato a zero.
 *
 * Un quarto di pixel di margine non cattura niente in piu' di zero, ma un margine che *cambia* rifa'
 * la cattura (vedi `DrawBackdropModifier`). E una sfocatura alta esattamente quanto la lente — il
 * pannello compatto su una pagina nitida, 7 dp su 7 — fa oscillare la differenza fra zero e qualche
 * milionesimo di pixel per puro arrotondamento: senza la soglia, ogni fotogramma di una miscela
 * avrebbe sporcato la cattura.
 */
internal fun physicsLensPadding(padding: Float, height: Float): Float {
  val left = padding - height
  return if (left < CapturePaddingSnapPx) 0f else left
}

/** Sotto questo margine (in pixel dello scope) la cattura non si allarga: vedi [physicsLensPadding]. */
internal const val CapturePaddingSnapPx = 0.25f
