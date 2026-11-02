package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Il vetro liquido di Fluid-physics (3.0): una forma che viaggia e' UN corpo di liquido, non un
 * rettangolo arrotondato che cambia taglia.
 *
 * Fino alla beta 2 la sagoma in viaggio era il lerp fra due rettangoli arrotondati, con un gonfiore
 * uniforme oltre il traguardo: geometria pulita, e per questo "geometrica" — Alessio: "manca di quel
 * effetto vetro non geometrico durante le animazioni, come la pillola". La pillola (e il pomello dello
 * slider) sono gocce di Kyant: la sagoma si stira con la velocita' e si schiaccia all'arrivo su due
 * molle di smorzamento diverso, e la lente piega quello che c'e' sotto. Qui le stesse idee, per una
 * superficie che cambia anche forma:
 *
 *  1. **Il corpo e' elastico** ([LiquidBody]): due oscillatori, uno per asse, spinti dall'accelerazione
 *     del centro della sagoma lungo il moto. Accelerare stira il corpo lungo il viaggio (e lo assottiglia
 *     di traverso), frenare lo schiaccia (e lo allarga); i due assi hanno smorzamento e periodo diversi,
 *     quindi all'arrivo larghezza e altezza oscillano sfasate — la gelatina — e si posano.
 *  2. **Il corpo nasce dall'ancora** ([liquidRootFrame]): quando la sagoma parte da un tasto e ne esce
 *     (il menu sopra il tasto), il tasto resta un istante come seconda goccia che si svuota nel corpo,
 *     fusa con lo smooth minimum dello shader: si forma un collo che si assottiglia e si stacca. Al
 *     ritorno la strada inversa: il collo ricompare e il pannello viene risucchiato nel tasto.
 *  3. **La lente si fa goccia in moto** ([liquidLensOptics]): piu' rifrazione al bordo, meno latte, un
 *     filo d'ingrandimento — e a riposo la lastra di sempre.
 *
 * Il patto con il riposo non cambia: **a riposo tutto questo vale zero esatto**. Le deformazioni hanno
 * una soglia sotto cui il corpo si posa e scrive zero (non un numero piccolo), la seconda goccia esiste
 * solo strettamente fra i capi del viaggio, e la lente in moto torna l'ottica dichiarata bit per bit.
 * Con le animazioni ridotte non c'e' niente: niente oscillatori, niente collo, niente lente in moto.
 */

/**
 * Se le superfici di Fluid-physics sotto questo punto sono di vetro liquido (3.0): il corpo elastico,
 * l'ancora che si svuota col collo, la lente in moto. Vero di serie.
 *
 * Falso rimette la geometria di prima — il lerp fra due rettangoli arrotondati e il gonfiore uniforme
 * — senza toccare niente altro: esiste per CONFRONTARE i due (la galleria lo mette in un interruttore
 * del Playground), non come preferenza di un'app. Le animazioni ridotte spengono il liquido da sole.
 */
val LocalFluidPhysicsLiquid: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

// --- I numeri del liquido -----------------------------------------------------------------------
//
// Token di movimento che per ora vivono qui e non in `FluidMotion` (FluidMotion.kt e' in lavorazione
// altrove): da spostare in blocco, coi nomi che hanno.

internal object FluidLiquid {

  /**
   * Quanto l'accelerazione del centro, in lunghezze del corpo al secondo quadrato, stira il corpo.
   * Tarato sul menu che nasce da un tasto: ~14% di stiramento nel primo terzo del viaggio, ~6% di
   * schiacciamento all'arrivo.
   */
  const val StrainGain = 0.4f

  /**
   * Quanto lo stiramento lungo un asse assottiglia l'altro: un corpo che si allunga si stringe, uno
   * che si schiaccia si allarga. Non 0,5 ("volume costante" in due dimensioni): un po' di piu', perche'
   * su un pannello la larghezza e' la dimensione che l'occhio misura.
   */
  const val StrainPoisson = 0.6f

  /** L'asse X: meno smorzato e piu' lento dell'Y, cosi' i due assi non si muovono mai in coppia. */
  const val StrainDampingX = 0.38f
  const val StrainResponseX = 0.30f

  /** L'asse Y. */
  const val StrainDampingY = 0.5f
  const val StrainResponseY = 0.26f

  /** Il tetto della deformazione per asse: oltre, un pannello smette di essere quel pannello. */
  const val MaxStrain = 0.14f

  /** Di quanto un lato puo' spostarsi per la deformazione, al massimo: mai contro il bordo dello schermo. */
  const val MaxStrainSideDp = 8f

  /**
   * La lunghezza minima con cui si misura il corpo: un tasto piccolo che parte veloce si stirerebbe
   * all'infinito se lo si misurasse sulla sua taglia vera.
   */
  const val MinBodyLengthDp = 40f

  /**
   * Fra 0 e questo progresso la deformazione entra pian piano: al progresso zero la sagoma deve essere
   * ESATTAMENTE l'ancora (il tasto vero si nasconde e si rimette in quel fotogramma), e un corpo che
   * arriva sul tasto ancora schiacciato salterebbe al cambio di proprietario.
   */
  const val StrainAnchorSpan = 0.25f

  /** Sotto questa velocita' del centro il corpo e' fermo. */
  const val QuietSpeedDp = 4f

  /** Sotto questa deformazione (e questa velocita' di deformazione) il corpo si posa: zero esatto. */
  const val QuietStrain = 0.0004f
  const val QuietStrainRate = 0.004f

  /** Il passo d'integrazione massimo: piu' fine di un fotogramma, perche' le molle restino stabili. */
  const val MaxStepSeconds = 1f / 240f

  /** Un fotogramma piu' lungo di cosi' e' uno stallo: non se ne ricava un'accelerazione. */
  const val StallSeconds = 0.05f

  // --- La seconda goccia: l'ancora che si svuota ---

  /** A questo progresso l'ancora si e' svuotata del tutto nel corpo. */
  const val RootDrainEnd = 0.85f

  /**
   * Mentre si svuota, il centro della goccia rimasta scivola verso il bordo che guarda il corpo, per
   * questa frazione: a meta' strada il collo resta attaccato fino alla fine invece di lasciare un
   * puntino isolato sull'ancora.
   */
  const val RootSlide = 0.5f

  /** Il raggio di fusione fra l'ancora e il corpo, in lati corti dell'ancora. */
  const val RootBlendFraction = 0.6f

  /** Il lato corto dell'ancora su cui si misura la fusione, fra questi due (dp). */
  const val RootBlendMinDp = 24f
  const val RootBlendMaxDp = 56f

  /** La fusione entra in questo tratto iniziale del viaggio (1/3): al progresso zero e' spenta. */
  const val RootBlendRamp = 3f

  /** Sotto questa scala la goccia rimasta non si disegna piu'. */
  const val RootMinScale = 0.02f

  // --- La lente in moto ---

  /** A questa velocita' del centro la lente e' tutta "goccia". */
  const val LensFullSpeedDp = 700f

  /** Quanto in fretta la lente segue il moto (costante di tempo, secondi). */
  const val LensFollowSeconds = 0.06f

  /** Sotto questo peso la lente in moto e' spenta. */
  const val QuietLens = 0.002f

  /** La lente in moto: altezza e spostamento cresciuti, sfocatura calata (vedi [liquidLensOptics]). */
  const val LensHeightGain = 0.6f
  const val LensAmountGain = 0.8f
  const val LensBlurDrop = 0.45f

  /** L'ingrandimento al centro, a lente piena: un filo, la goccia non e' una lente da orologiaio. */
  const val LensMagnify = 0.035f

  /** La frangia della goccia in moto, come peso: meta' di quella di un tasto. */
  const val LensDispersion = 0.5f

  // --- Il contenuto ---
  //
  // Il contenuto sta dentro il vetro formato, mai sopra il vetro che si forma: arriva quando il corpo
  // e' quasi il traguardo, e se ne va per primo quando il corpo comincia a ritirarsi.

  /** In apertura le voci entrano fra questi due punti del viaggio. */
  const val ContentOpenFrom = 0.55f
  const val ContentOpenTo = 0.9f

  /** In chiusura escono fra questi due (da 0,94 a 0,62): prima che il liquido si ritiri davvero. */
  const val ContentCloseFrom = 0.62f
  const val ContentCloseTo = 0.94f
}

// --- Il corpo elastico ------------------------------------------------------------------------

/**
 * Il corpo elastico di una superficie fisica: due oscillatori smorzati, uno per asse, che il moto
 * della sagoma spinge e che a riposo si posano a zero esatto.
 *
 * Si alimenta una volta per fotogramma ([step]) con il centro e il lato corto della sagoma *senza*
 * deformazione, nell'istante del fotogramma: da li' ricava velocita' e accelerazione per differenze, e
 * l'accelerazione lungo il moto (positiva se il corpo accelera nel verso in cui va, negativa se frena)
 * spinge gli oscillatori. Il resto e' fisica di una gelatina: accelerare stira, frenare schiaccia,
 * e ogni asse risuona col suo periodo.
 *
 * Generico: non sa chi guida la sagoma (una molla di `morphTo`, l'orologio del menu, il modale, un
 * dito) e non ha bisogno di saperlo, perche' legge il moto da cio' che si disegna. Puro (nessun tipo
 * Android, il tempo lo passa il chiamante): si prova in JVM con una traiettoria sintetica.
 *
 * Le uscite ([strainX], [strainY], [lens]) sono stato di snapshot, lette in fase di disegno: scriverle
 * invalida il disegno della superficie e nient'altro.
 */
internal class LiquidBody {

  /** La deformazione per asse, gia' fermata al tetto: +0,1 = il 10% piu' lungo su quell'asse. */
  var strainX by mutableFloatStateOf(0f)
    private set
  var strainY by mutableFloatStateOf(0f)
    private set

  /** Quanto la lente e' "goccia in moto", 0..1. */
  var lens by mutableFloatStateOf(0f)
    private set

  /** Cambia a ogni uscita nuova: la chiave della cache del piano. */
  var version by mutableIntStateOf(0)
    private set

  /** Vero quando tutte le uscite valgono zero esatto: il corpo e' posato. */
  val quiet: Boolean get() = strainX == 0f && strainY == 0f && lens == 0f

  private var ex = 0f
  private var ux = 0f
  private var ey = 0f
  private var uy = 0f
  private var lensRaw = 0f

  private var hasSample = false
  private var lastTimeNanos = 0L
  private var cx = 0f
  private var cy = 0f
  private var vx = 0f
  private var vy = 0f

  /** Dimentica la storia del moto (non la deformazione): il prossimo campione riparte da li'. */
  fun forgetHistory() {
    hasSample = false
  }

  /** Tutto a zero, subito: le animazioni ridotte, una superficie che se ne va. */
  fun reset() {
    ex = 0f; ux = 0f; ey = 0f; uy = 0f; lensRaw = 0f
    hasSample = false
    vx = 0f; vy = 0f
    publish(0f, 0f, 0f)
  }

  /**
   * Un fotogramma: il centro ([centerX], [centerY]) e il lato corto ([length]) della sagoma senza
   * deformazione, in pixel, all'istante [timeNanos]. [density] tara le soglie in dp.
   *
   * @return vero finche' il corpo e' vivo (si muove o non si e' ancora posato): il chiamante chiede
   *   un altro fotogramma. Falso quando si e' posato, e allora le uscite valgono zero esatto.
   */
  fun step(timeNanos: Long, centerX: Float, centerY: Float, length: Float, density: Float): Boolean {
    if (!hasSample) {
      hasSample = true
      lastTimeNanos = timeNanos
      cx = centerX
      cy = centerY
      vx = 0f
      vy = 0f
      return true
    }
    val dt = (timeNanos - lastTimeNanos) / 1_000_000_000f
    if (!(dt > 0f)) return true
    lastTimeNanos = timeNanos
    val nvx = (centerX - cx) / dt
    val nvy = (centerY - cy) / dt
    cx = centerX
    cy = centerY
    // Uno stallo (un fotogramma perso lungo, il processo in pausa) non e' un'accelerazione: la
    // velocita' si prende, la spinta no.
    val stalled = dt > FluidLiquid.StallSeconds
    val ax = if (stalled) 0f else (nvx - vx) / dt
    val ay = if (stalled) 0f else (nvy - vy) / dt
    vx = nvx
    vy = nvy

    val speed = hypot(nvx, nvy)
    val unit = max(density, 0.0001f)
    // L'accelerazione lungo il moto, per asse: positiva se accelera nel verso in cui va (stira),
    // negativa se frena (schiaccia).
    val alongX = if (speed > 0.0001f) ax * (nvx / speed) else 0f
    val alongY = if (speed > 0.0001f) ay * (nvy / speed) else 0f
    val body = max(length, FluidLiquid.MinBodyLengthDp * unit)
    val fx = FluidLiquid.StrainGain * (alongX - FluidLiquid.StrainPoisson * alongY) / body
    val fy = FluidLiquid.StrainGain * (alongY - FluidLiquid.StrainPoisson * alongX) / body

    val steps = max(1, ceil(dt / FluidLiquid.MaxStepSeconds).toInt())
    val h = dt / steps
    val wx = TwoPi / FluidLiquid.StrainResponseX
    val wy = TwoPi / FluidLiquid.StrainResponseY
    repeat(steps) {
      ux += (-wx * wx * ex - 2f * FluidLiquid.StrainDampingX * wx * ux + fx) * h
      ex += ux * h
      uy += (-wy * wy * ey - 2f * FluidLiquid.StrainDampingY * wy * uy + fy) * h
      ey += uy * h
    }
    // Una sicura, non una taratura: un'integrazione che sfuggisse non deve poter rovesciare la sagoma.
    ex = ex.coerceIn(-StrainRunaway, StrainRunaway)
    ey = ey.coerceIn(-StrainRunaway, StrainRunaway)

    val lensTarget = (speed / (FluidLiquid.LensFullSpeedDp * unit)).coerceIn(0f, 1f)
    lensRaw += (lensTarget - lensRaw) * (1f - exp(-dt / FluidLiquid.LensFollowSeconds))

    val moving = speed > FluidLiquid.QuietSpeedDp * unit
    val settled = !moving &&
      abs(ex) < FluidLiquid.QuietStrain && abs(ey) < FluidLiquid.QuietStrain &&
      abs(ux) < FluidLiquid.QuietStrainRate && abs(uy) < FluidLiquid.QuietStrainRate &&
      lensRaw < FluidLiquid.QuietLens
    if (settled) {
      ex = 0f; ux = 0f; ey = 0f; uy = 0f; lensRaw = 0f
      vx = 0f; vy = 0f
      publish(0f, 0f, 0f)
      return false
    }
    publish(
      ex.coerceIn(-FluidLiquid.MaxStrain, FluidLiquid.MaxStrain),
      ey.coerceIn(-FluidLiquid.MaxStrain, FluidLiquid.MaxStrain),
      lensRaw.coerceIn(0f, 1f),
    )
    return true
  }

  private fun publish(sx: Float, sy: Float, l: Float) {
    if (sx == strainX && sy == strainY && l == lens) return
    strainX = sx
    strainY = sy
    lens = l
    version++
  }

  private companion object {
    const val TwoPi = 6.2831855f
    const val StrainRunaway = 0.5f
  }
}

// --- La geometria del liquido ------------------------------------------------------------------

/** Smoothstep fra [from] e [to]. */
internal fun liquidSmoothstep(from: Float, to: Float, x: Float): Float {
  val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * Quanto della deformazione arriva sulla sagoma al progresso [p]: zero sull'ancora (p = 0), pieno da
 * [FluidLiquid.StrainAnchorSpan] in poi. Oltre l'1 e a riposo e' pieno: l'arrivo e' dove la gelatina
 * si vede.
 */
internal fun liquidStrainWeight(p: Float): Float =
  if (!(p > 0f)) 0f else liquidSmoothstep(0f, FluidLiquid.StrainAnchorSpan, p)

/**
 * Le otto cifre di uno slab (sinistra, alto, larghezza, altezza, quattro raggi) deformate attorno al
 * proprio centro: [strainX] e [strainY] gia' pesati, e ogni lato che si sposta al massimo di [maxSidePx].
 * I raggi seguono il fattore minore, e mai oltre il mezzo lato: un tasto tondo resta tondo anche
 * schiacciato.
 */
internal fun strainSlab(values: FloatArray, strainX: Float, strainY: Float, maxSidePx: Float): FloatArray {
  if (strainX == 0f && strainY == 0f) return values
  val w = values[2]
  val h = values[3]
  val dx = (w * strainX / 2f).coerceIn(-maxSidePx, maxSidePx)
  val dy = (h * strainY / 2f).coerceIn(-maxSidePx, maxSidePx)
  val nw = max(w + 2f * dx, MinSlabSidePx)
  val nh = max(h + 2f * dy, MinSlabSidePx)
  val sx = if (w > 0f) nw / w else 1f
  val sy = if (h > 0f) nh / h else 1f
  val radiusScale = min(sx, sy)
  val half = min(nw, nh) / 2f
  return floatArrayOf(
    values[0] + (w - nw) / 2f,
    values[1] + (h - nh) / 2f,
    nw,
    nh,
    (values[4] * radiusScale).coerceIn(0f, half),
    (values[5] * radiusScale).coerceIn(0f, half),
    (values[6] * radiusScale).coerceIn(0f, half),
    (values[7] * radiusScale).coerceIn(0f, half),
  )
}

/**
 * Un anello di vertici deformato attorno al centro del suo riquadro, in un array nuovo: la stessa
 * deformazione di [strainSlab] per una sagoma libera.
 */
internal fun strainRing(ring: FloatArray, count: Int, strainX: Float, strainY: Float, maxSidePx: Float): FloatArray {
  if (strainX == 0f && strainY == 0f) return ring
  val bounds = ringBounds(ring, count)
  val w = bounds.width
  val h = bounds.height
  val sx = if (w > 0f) 1f + (w * strainX).coerceIn(-2f * maxSidePx, 2f * maxSidePx) / w else 1f
  val sy = if (h > 0f) 1f + (h * strainY).coerceIn(-2f * maxSidePx, 2f * maxSidePx) / h else 1f
  val c = bounds.center
  val out = FloatArray(count * 2)
  for (i in 0 until count) {
    out[i * 2] = c.x + (ring[i * 2] - c.x) * sx
    out[i * 2 + 1] = c.y + (ring[i * 2 + 1] - c.y) * sy
  }
  return out
}

/**
 * Quanto resta dell'ancora al progresso [p]: tutta alla partenza, niente da [FluidLiquid.RootDrainEnd]
 * in poi. Una funzione del progresso, non del tempo: il viaggio di ritorno la ripercorre all'inverso
 * (l'ancora ricompare e risucchia il corpo), e un viaggio invertito a meta' la trova dove l'aveva
 * lasciata.
 */
internal fun liquidRootScale(p: Float): Float = 1f - liquidSmoothstep(0f, FluidLiquid.RootDrainEnd, p)

/**
 * L'ancora che si svuota, a scala [scale]: il riquadro di [origin] rimpicciolito attorno al proprio
 * centro, col centro che scivola verso il punto del bordo di [origin] piu' vicino al centro di
 * [target] (vedi [FluidLiquid.RootSlide]).
 */
internal fun liquidRootFrame(origin: Rect, target: Rect, scale: Float): Rect {
  val s = scale.coerceIn(0f, 1f)
  val c = origin.center
  val t = target.center
  val edgeX = t.x.coerceIn(origin.left, origin.right)
  val edgeY = t.y.coerceIn(origin.top, origin.bottom)
  val slide = (1f - s) * FluidLiquid.RootSlide
  val x = c.x + (edgeX - c.x) * slide
  val y = c.y + (edgeY - c.y) * slide
  val hw = origin.width / 2f * s
  val hh = origin.height / 2f * s
  return Rect(x - hw, y - hh, x + hw, y + hh)
}

/**
 * Il raggio di fusione fra l'ancora e il corpo al progresso [p], in pixel: una frazione del lato corto
 * dell'ancora, spento al progresso zero (li' corpo e ancora coincidono, e lo smooth minimum di due
 * campi uguali gonfierebbe la sagoma di un quarto del raggio).
 */
internal fun liquidRootBlend(origin: Rect, p: Float, density: Float): Float {
  val side = origin.minDimension.coerceIn(FluidLiquid.RootBlendMinDp * density, FluidLiquid.RootBlendMaxDp * density)
  val ramp = (p * FluidLiquid.RootBlendRamp).coerceIn(0f, 1f)
  return FluidLiquid.RootBlendFraction * side * ramp
}

/**
 * Se il corpo, partendo da [origin], ne esce davvero: il centro del traguardo sta fuori dalla partenza
 * (il corpo va da un'altra parte, non cresce sul posto) e il traguardo non la contiene. Falso per le
 * azioni della barra, che si aprono SOPRA il tasto (una seconda goccia dentro il corpo non si vedrebbe
 * e costerebbe un pezzo di campo in piu' per pixel), e per le forme che cambiano attorno allo stesso
 * centro (il Playground): li' l'ancora coinciderebbe col corpo, e lo smooth minimum di due campi
 * quasi uguali la gonfierebbe di un quarto del raggio di fusione.
 */
internal fun liquidLeavesOrigin(origin: Rect, target: Rect): Boolean {
  if (origin.contains(target.center)) return false
  val slack = 1f
  return origin.left < target.left - slack || origin.top < target.top - slack ||
    origin.right > target.right + slack || origin.bottom > target.bottom + slack
}

// --- La lente in moto -------------------------------------------------------------------------

/**
 * L'ottica della goccia in moto: [optics] con la lente cresciuta e la sfocatura calata, al peso
 * [weight] (0 = [optics] identica, la stessa istanza). E' la lente della pillola tenuta premuta, in
 * proporzione: il vetro che si muove piega quello che c'e' sotto invece di smerigliarlo.
 *
 * Il patto del margine regge per costruzione: la sfocatura scende mentre l'altezza sale, quindi una
 * sfocatura che non superava l'altezza ai due capi non la supera in nessun punto della miscela.
 */
internal fun liquidLensOptics(optics: GlassOptics, weight: Float): GlassOptics {
  val w = weight.coerceIn(0f, 1f)
  if (w <= 0f) return optics
  return optics.copy(
    blurScale = optics.blurScale * (1f - FluidLiquid.LensBlurDrop * w),
    refractionHeight = (optics.refractionHeight.value * (1f + FluidLiquid.LensHeightGain * w)).dp,
    refractionAmount = (optics.refractionAmount.value * (1f + FluidLiquid.LensAmountGain * w)).dp,
  )
}

/**
 * Il materiale di un fotogramma con la lente in moto al peso [weight]: l'ottica di
 * [liquidLensOptics], la cupola che si accende (la goccia e' una lente intera, non un bordo smussato)
 * e un filo di frangia. A peso zero e' [material], la stessa istanza: a riposo niente cambia.
 */
internal fun liquidMaterial(material: PhysicsMaterial, weight: Float): PhysicsMaterial {
  val w = weight.coerceIn(0f, 1f)
  if (w <= 0f) return material
  return PhysicsMaterial(
    optics = liquidLensOptics(material.optics, w),
    depthEffect = material.depthEffect + (1f - material.depthEffect) * w,
    dispersion = material.dispersion + (FluidLiquid.LensDispersion - material.dispersion).coerceAtLeast(0f) * w,
  )
}

/** L'ingrandimento al centro della lente in moto, 0 a riposo. */
internal fun liquidMagnify(weight: Float): Float {
  val w = weight.coerceIn(0f, 1f)
  return if (w <= 0f) 0f else FluidLiquid.LensMagnify * w
}
