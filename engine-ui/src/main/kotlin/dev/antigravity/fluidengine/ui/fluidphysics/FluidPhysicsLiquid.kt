package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Il vetro liquido di Fluid-physics (3.0): una superficie che viaggia e' una GOCCIA.
 *
 * La sagoma in viaggio non e' piu' una figura geometrica: e' un contorno di 48 punti, ognuno su una
 * molla verso il suo punto della sagoma a riposo di quell'istante (il rettangolo arrotondato fra il
 * mittente e il destinatario), legati fra loro da una tensione del bordo. Le molle vicino all'ancora —
 * il tasto da cui nasce il menu, la riga, il dito — sono dure e seguono senza ritardo; quelle lontane
 * sono morbide e restano indietro, e lo smorzamento e' misto: relativo vicino all'ancora (segue il
 * moto della sagoma), assoluto lontano (resiste al moto). Ne viene una forma che cambia con la
 * velocita' e con la direzione, che non e' mai la stessa figura due volte e che all'arrivo ondeggia e
 * si ricompone. E' la variante C del banco (`artifacts/banco/banco-liquido.html`), scelta da Alessio:
 * "cio' che rende davvero liquido un materiale e' proprio il fatto che non e' geometricamente ben
 * definito". Il corpo elastico per assi della prima versione (un rettangolo stirato su X e Y) e il collo
 * dell'ancora che si svuota sono stati tolti: la coda attaccata al tasto fa da sola quello che faceva
 * il collo.
 *
 * Il patto con il riposo non cambia: quando ogni punto e' entro un quarto di pixel dal suo punto a
 * riposo e niente si muove, la goccia si posa e la superficie torna la lastra esatta (lo stesso piano
 * di prima, al bit) — e non chiede piu' fotogrammi. Il primo fotogramma di un viaggio e' la lastra del
 * mittente. Con le animazioni ridotte non c'e' goccia: la sagoma va dritta come prima.
 */

/**
 * Se le superfici di Fluid-physics sotto questo punto sono di vetro liquido (3.0): la goccia e la
 * lente in moto. Vero di serie.
 *
 * Falso rimette la geometria di prima — il lerp fra due rettangoli arrotondati e il gonfiore uniforme
 * — senza toccare niente altro: esiste per CONFRONTARE i due (la galleria lo mette in un interruttore
 * del Playground), non come preferenza di un'app. Le animazioni ridotte spengono il liquido da sole.
 */
val LocalFluidPhysicsLiquid: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/**
 * Le manopole della goccia per la galleria (3.0). **Non e' API per le app**: esiste perche' Alessio
 * possa tarare la goccia in mano sul telefono, e i numeri che sceglie diventano token di
 * `FluidMotion`. Un'app che la usasse si legherebbe a un numero che domani cambia nome.
 *
 * @property edgeTension la tensione del bordo come frazione di quella del banco
 *   ([FluidMotion.DropEdgeTensionReference]): 0,2 di serie, da 0,2 a 2.
 */
@Immutable
data class FluidPhysicsDropTuning(val edgeTension: Float = FluidMotion.DropEdgeTensionDefault)

/** Le manopole della goccia in vigore: vedi [FluidPhysicsDropTuning]. Solo per la galleria. */
val LocalFluidPhysicsDropTuning: ProvidableCompositionLocal<FluidPhysicsDropTuning> =
  compositionLocalOf { FluidPhysicsDropTuning() }

// --- La goccia ---------------------------------------------------------------------------------

/**
 * La goccia di una superficie fisica: [points] punti, ognuno su una molla verso il suo punto a riposo,
 * e la tensione del bordo fra vicini. Si alimenta una volta per fotogramma ([step]) con il contorno a
 * riposo di quell'istante (campionato con [sampleRestContour], stessi indici a ogni fotogramma) e il
 * punto d'ancora; fra un fotogramma e l'altro integra a passi di [FluidMotion.DropStepSeconds] col
 * contorno a riposo interpolato linearmente, come il banco.
 *
 * Generica: non sa chi guida la sagoma (una molla di `morphTo`, l'orologio di un menu, il modale) e non
 * ha bisogno di saperlo — legge il moto dal contorno a riposo. Pura (il tempo lo passa il chiamante):
 * si prova in JVM.
 *
 * Le uscite sono [displacementX]/[displacementY] (lo scarto di ogni punto dal suo punto a riposo, in
 * pixel), [offsetX]/[offsetY] (lo scarto del baricentro) e [lens] (quanto la lente e' "in moto"); [version]
 * e [quiet] sono stato di snapshot, letto in disegno: scriverli invalida il disegno della superficie.
 */
internal class LiquidDrop(val points: Int = FluidMotion.DropPoints) {

  /** Vero quando la goccia e' posata: ogni scarto e' zero esatto e la superficie e' una lastra. */
  var quiet by mutableStateOf(true)
    private set

  /** Cambia a ogni uscita nuova: la chiave della cache del piano. */
  var version by mutableIntStateOf(0)
    private set

  /** Quanto la lente e' "goccia in moto", 0..1. */
  var lens by mutableFloatStateOf(0f)
    private set

  /** Lo scarto di ogni punto dal suo punto a riposo, pixel (pubblicato a ogni passo). */
  val displacementX = FloatArray(points)
  val displacementY = FloatArray(points)

  /** Lo scarto del baricentro della goccia da quello della sagoma a riposo, pixel. */
  var offsetX = 0f
    private set
  var offsetY = 0f
    private set

  private val rest = FloatArray(points * 2)
  private val restPrev = FloatArray(points * 2)
  private val p = FloatArray(points * 2)
  private val v = FloatArray(points * 2)
  private val weight = FloatArray(points)

  private var hasSample = false
  private var lastTimeNanos = 0L
  private var lensRaw = 0f

  /** Dimentica il tempo dell'ultimo campione: il prossimo riparte da li' (la forma resta dov'e'). */
  fun forgetHistory() {
    hasSample = false
  }

  /** Posata, subito: le animazioni ridotte, una superficie che se ne va. */
  fun reset() {
    hasSample = false
    v.fill(0f)
    lensRaw = 0f
    settle()
  }

  /**
   * Un fotogramma: il contorno a riposo di questo istante ([restContour], x,y interlacciati, [points]
   * punti), l'ancora ([anchorX], [anchorY]) e fin dove arriva ([reachPx]), all'istante [timeNanos].
   * [edgeTension] e' la frazione di [FluidMotion.DropEdgeTensionReference]; [density] tara le soglie.
   *
   * @return vero finche' la goccia e' viva (si muove o non si e' posata): il chiamante chiede un altro
   *   fotogramma. Falso quando si e' posata, e allora ogni scarto vale zero esatto.
   */
  fun step(
    timeNanos: Long,
    restContour: FloatArray,
    anchorX: Float,
    anchorY: Float,
    reachPx: Float,
    density: Float,
    edgeTension: Float = FluidMotion.DropEdgeTensionDefault,
  ): Boolean {
    val n = points
    if (!hasSample) {
      hasSample = true
      lastTimeNanos = timeNanos
      restContour.copyInto(rest, endIndex = n * 2)
      rest.copyInto(restPrev)
      // Da posata, la goccia parte ESATTAMENTE dalla sagoma a riposo: il primo fotogramma e' la lastra.
      if (quiet) {
        rest.copyInto(p)
        v.fill(0f)
      }
      return true
    }
    val raw = (timeNanos - lastTimeNanos) / 1_000_000_000f
    if (!(raw > 0f)) return true
    lastTimeNanos = timeNanos
    // Un fotogramma lungo (uno stallo) non e' un moto: si integra al piu' un trentesimo di secondo.
    val dt = min(raw, FluidMotion.DropMaxFrameSeconds)
    rest.copyInto(restPrev)
    restContour.copyInto(rest, endIndex = n * 2)

    val steps = max(1, ceil(dt / FluidMotion.DropStepSeconds).toInt())
    val h = dt / steps
    val kNear = FluidMotion.DropStiffnessNear
    val kFar = FluidMotion.DropStiffnessFar
    val zeta = FluidMotion.DropDamping
    val ratio = n / 48f
    val ks = FluidMotion.DropEdgeTensionReference * edgeTension.coerceAtLeast(0f) * ratio * ratio
    val reach = max(reachPx, 1f)
    for (s in 1..steps) {
      val f = s / steps.toFloat()
      for (i in 0 until n) {
        val ix = i * 2
        val iy = ix + 1
        val rx = restPrev[ix] + (rest[ix] - restPrev[ix]) * f
        val ry = restPrev[iy] + (rest[iy] - restPrev[iy]) * f
        val vrx = (rest[ix] - restPrev[ix]) / dt
        val vry = (rest[iy] - restPrev[iy]) / dt
        val w = exp(-hypot(rx - anchorX, ry - anchorY) / reach)
        weight[i] = w
        val k = kFar + (kNear - kFar) * w
        val c = 2f * zeta * sqrt(k)
        val j0 = ((i + n - 1) % n) * 2
        val j1 = ((i + 1) % n) * 2
        // Vicino all'ancora lo smorzamento e' relativo al moto della sagoma (segue senza ritardo),
        // lontano e' assoluto (resta indietro): e' la coda.
        //
        // La tensione del bordo tira ogni punto verso i vicini, ma sullo SCARTO dalla sagoma a riposo e
        // non sulla posizione (come nel banco): sulla posizione un bordo teso stringe gli angoli anche
        // da fermo — l'equilibrio non e' la sagoma, la goccia non si posa mai sulla lastra e gli angoli
        // del pannello si smussano (i bordi "esageratamente smussati" di Alessio). Sullo scarto liscia
        // la deformazione e basta, e a riposo il contorno e' la sagoma esatta.
        val dx0 = p[j0] - rPrevAt(j0, f) - (p[ix] - rx)
        val dx1 = p[j1] - rPrevAt(j1, f) - (p[ix] - rx)
        val dy0 = p[j0 + 1] - rPrevAt(j0 + 1, f) - (p[iy] - ry)
        val dy1 = p[j1 + 1] - rPrevAt(j1 + 1, f) - (p[iy] - ry)
        val fx = k * (rx - p[ix]) + c * (w * vrx - v[ix]) + ks * (dx0 + dx1)
        val fy = k * (ry - p[iy]) + c * (w * vry - v[iy]) + ks * (dy0 + dy1)
        v[ix] += fx * h
        v[iy] += fy * h
      }
      for (i in 0 until n * 2) p[i] += v[i] * h
    }
    if (!p[0].isFinite() || !p[1].isFinite()) {
      // Una sicura, non una taratura: un'integrazione che sfuggisse non deve poter rovesciare la sagoma.
      rest.copyInto(p)
      v.fill(0f)
    }

    // La lente in moto segue la velocita' del baricentro della sagoma a riposo.
    var cx = 0f
    var cy = 0f
    var pcx = 0f
    var pcy = 0f
    var restMoving = false
    var maxDev = 0f
    var maxSpeed = 0f
    for (i in 0 until n) {
      val ix = i * 2
      cx += rest[ix]
      cy += rest[ix + 1]
      pcx += restPrev[ix]
      pcy += restPrev[ix + 1]
      if (rest[ix] != restPrev[ix] || rest[ix + 1] != restPrev[ix + 1]) restMoving = true
      maxDev = max(maxDev, hypot(p[ix] - rest[ix], p[ix + 1] - rest[ix + 1]))
      maxSpeed = max(maxSpeed, hypot(v[ix], v[ix + 1]))
    }
    val unit = max(density, 0.0001f)
    val speed = hypot(cx - pcx, cy - pcy) / n / dt
    val lensTarget = (speed / (FluidMotion.LiquidLensFullSpeedDp * unit)).coerceIn(0f, 1f)
    lensRaw += (lensTarget - lensRaw) * (1f - exp(-dt / FluidMotion.LiquidLensFollowSeconds))

    val settled = !restMoving && maxDev < FluidMotion.DropRestPx &&
      maxSpeed < FluidMotion.DropRestSpeedDp * unit && lensRaw < QuietLens
    if (settled) {
      rest.copyInto(p)
      v.fill(0f)
      lensRaw = 0f
      settle()
      return false
    }
    var ox = 0f
    var oy = 0f
    for (i in 0 until n) {
      val ix = i * 2
      displacementX[i] = p[ix] - rest[ix]
      displacementY[i] = p[ix + 1] - rest[ix + 1]
      ox += displacementX[i]
      oy += displacementY[i]
    }
    offsetX = ox / n
    offsetY = oy / n
    lens = lensRaw.coerceIn(0f, 1f)
    quiet = false
    version++
    return true
  }

  /** La coordinata [index] del contorno a riposo interpolato a [f] dentro il fotogramma. */
  private fun rPrevAt(index: Int, f: Float): Float = restPrev[index] + (rest[index] - restPrev[index]) * f

  /** La posizione di adesso del punto [i] (pixel): per i test e per chi disegna la goccia nuda. */
  fun pointX(i: Int): Float = p[i * 2]
  fun pointY(i: Int): Float = p[i * 2 + 1]

  /** La rigidezza relativa del punto [i] all'ultimo passo (0 lontano, 1 sull'ancora). */
  fun anchorWeight(i: Int): Float = weight[i]

  private fun settle() {
    displacementX.fill(0f)
    displacementY.fill(0f)
    offsetX = 0f
    offsetY = 0f
    val changed = !quiet || lens != 0f
    lens = 0f
    quiet = true
    if (changed) version++
  }

  private companion object {
    const val QuietLens = 0.002f
  }
}

// --- Il contorno a riposo ------------------------------------------------------------------------

/**
 * Il perimetro di un rettangolo arrotondato a quattro raggi, come nove tratti percorsi dall'alto al
 * centro in senso orario (sullo schermo): mezzo lato alto, angolo in alto a destra, lato destro, ...,
 * l'altra meta' del lato alto. E' il `rrAt` del banco, coi raggi per angolo dell'engine.
 */
internal class RoundedRectPerimeter {
  private val len = FloatArray(9)
  private var cx = 0f
  private var top = 0f
  private var left = 0f
  private var right = 0f
  private var bottom = 0f
  private var rTL = 0f
  private var rTR = 0f
  private var rBR = 0f
  private var rBL = 0f

  /** La lunghezza totale del perimetro, pixel. */
  var total = 0f
    private set

  fun set(frame: Rect, radii: FluidCornerRadii) {
    left = frame.left
    top = frame.top
    right = frame.right
    bottom = frame.bottom
    cx = frame.center.x
    val half = min(frame.width, frame.height) / 2f
    rTL = radii.topLeft.coerceIn(0f, half)
    rTR = radii.topRight.coerceIn(0f, half)
    rBR = radii.bottomRight.coerceIn(0f, half)
    rBL = radii.bottomLeft.coerceIn(0f, half)
    val q = (PI / 2).toFloat()
    len[0] = max(0f, (right - rTR) - cx)
    len[1] = q * rTR
    len[2] = max(0f, (bottom - rBR) - (top + rTR))
    len[3] = q * rBR
    len[4] = max(0f, (right - rBR) - (left + rBL))
    len[5] = q * rBL
    len[6] = max(0f, (bottom - rBL) - (top + rTL))
    len[7] = q * rTL
    len[8] = max(0f, cx - (left + rTL))
    total = len.sum()
  }

  /** La lunghezza del tratto [k] (0..8: i dispari sono gli angoli). */
  fun segment(k: Int): Float = len[k]

  /** Il punto a distanza [d] lungo il perimetro, in [out] a [index] (x, y). */
  fun pointAt(d: Float, out: FloatArray, index: Int) {
    var rem = if (total > 0f) ((d % total) + total) % total else 0f
    var k = 0
    while (k < 8 && rem > len[k]) {
      rem -= len[k]
      k++
    }
    val x: Float
    val y: Float
    when (k) {
      0 -> { x = cx + rem; y = top }
      1 -> { val a = arc(rem, rTR); x = right - rTR + rTR * cos(-HalfPi + a); y = top + rTR + rTR * sin(-HalfPi + a) }
      2 -> { x = right; y = top + rTR + rem }
      3 -> { val a = arc(rem, rBR); x = right - rBR + rBR * cos(a); y = bottom - rBR + rBR * sin(a) }
      4 -> { x = right - rBR - rem; y = bottom }
      5 -> { val a = arc(rem, rBL); x = left + rBL + rBL * cos(HalfPi + a); y = bottom - rBL + rBL * sin(HalfPi + a) }
      6 -> { x = left; y = bottom - rBL - rem }
      7 -> { val a = arc(rem, rTL); x = left + rTL + rTL * cos(Pi + a); y = top + rTL + rTL * sin(Pi + a) }
      else -> { x = left + rTL + rem; y = top }
    }
    out[index] = x
    out[index + 1] = y
  }

  private fun arc(rem: Float, r: Float): Float = if (r > 0f) rem / r else 0f

  private companion object {
    const val HalfPi = (PI / 2).toFloat()
    const val Pi = PI.toFloat()
  }
}

/**
 * Il contorno a riposo di [slab] in [count] punti equidistanti lungo il perimetro, dall'alto al centro
 * in senso orario: gli stessi indici fra il mittente e il destinatario, quindi fra un fotogramma e
 * l'altro (il "per lunghezza d'arco" del banco).
 */
internal fun sampleRestContour(slab: FluidForm.Slab, count: Int, out: FloatArray, perimeter: RoundedRectPerimeter = RoundedRectPerimeter()) {
  perimeter.set(slab.frame, slab.cornerRadii)
  val total = perimeter.total
  for (i in 0 until count) perimeter.pointAt(total * i / count, out, i * 2)
}

/**
 * Le posizioni lungo il perimetro (in frazioni 0..1 dall'alto al centro) dei punti con cui si
 * DISEGNA la goccia: [count] punti, un numero fisso per ogni angolo ([cornerShare] del totale, divisi
 * fra gli angoli che ci sono) e il resto sui lati, in proporzione alla lunghezza. Cosi' gli angoli del
 * pannello restano definiti — dodici punti su un quarto di cerchio stanno entro un quinto di pixel
 * dall'arco — mentre la goccia si simula con [FluidMotion.DropPoints] punti equidistanti, che bastano
 * alla sua forma ma su un pannello largo lascerebbero due punti per angolo.
 */
internal fun renderParameters(perimeter: RoundedRectPerimeter, count: Int, out: FloatArray, cornerShare: Float = 0.5f) {
  val total = perimeter.total
  if (!(total > 0f)) {
    for (i in 0 until count) out[i] = i / count.toFloat()
    return
  }
  var corners = 0
  for (k in 1..7 step 2) if (perimeter.segment(k) > 0.5f) corners++
  var edges = 0f
  for (k in 0..8 step 2) edges += perimeter.segment(k)
  val perCorner = when {
    corners == 0 -> 0
    edges < 1f -> count / corners
    else -> (count * cornerShare / corners).toInt()
  }
  val edgeBudget = count - perCorner * corners
  val counts = IntArray(9)
  var assigned = 0
  for (k in 0..8) {
    counts[k] = when {
      k % 2 == 1 -> if (perimeter.segment(k) > 0.5f) perCorner else 0
      edges > 0f -> (edgeBudget * perimeter.segment(k) / edges).toInt()
      else -> 0
    }
    assigned += counts[k]
  }
  // L'avanzo degli arrotondamenti al lato piu' lungo (o al primo angolo, se lati non ce ne sono).
  var target = 0
  if (edges >= 1f) {
    for (k in 0..8 step 2) if (perimeter.segment(k) > perimeter.segment(target)) target = k
  } else {
    target = (1..7 step 2).firstOrNull { counts[it] > 0 } ?: 0
  }
  counts[target] += count - assigned
  var index = 0
  var start = 0f
  for (k in 0..8) {
    val l = perimeter.segment(k)
    val c = counts[k]
    for (j in 0 until c) {
      if (index < count) {
        out[index] = (start + l * j / c) / total
        index++
      }
    }
    start += l
  }
  while (index < count) {
    out[index] = index / count.toFloat()
    index++
  }
}

/**
 * Il contorno da DISEGNARE della goccia: [count] punti della sagoma a riposo [slab] (esatti, coi
 * parametri di [renderParameters]) spostati dello scarto della goccia interpolato lungo il perimetro
 * (Catmull-Rom periodica fra i [drop] punti simulati). A goccia posata e' la sagoma a riposo, entro un
 * quinto di pixel; in viaggio segue la goccia senza faccette agli angoli.
 */
internal fun buildDropRing(
  slab: FluidForm.Slab,
  drop: LiquidDrop,
  count: Int,
  out: FloatArray,
  perimeter: RoundedRectPerimeter,
  parameters: FloatArray,
): Float {
  perimeter.set(slab.frame, slab.cornerRadii)
  renderParameters(perimeter, count, parameters)
  val total = perimeter.total
  val n = drop.points
  var reach = 0f
  for (i in 0 until count) {
    val u = parameters[i]
    perimeter.pointAt(u * total, out, i * 2)
    val x = u * n
    val i1 = x.toInt().coerceIn(0, n - 1)
    val f = x - i1
    val i0 = (i1 + n - 1) % n
    val i2 = (i1 + 1) % n
    val i3 = (i1 + 2) % n
    val dx = catmullRom(drop.displacementX[i0], drop.displacementX[i1], drop.displacementX[i2], drop.displacementX[i3], f)
    val dy = catmullRom(drop.displacementY[i0], drop.displacementY[i1], drop.displacementY[i2], drop.displacementY[i3], f)
    out[i * 2] += dx
    out[i * 2 + 1] += dy
    reach = maxOf(reach, sqrt(dx * dx + dy * dy))
  }
  return reach
}

/** Catmull-Rom uniforme fra [b] e [c], con [a] e [d] come vicini. */
internal fun catmullRom(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
  val t2 = t * t
  val t3 = t2 * t
  return 0.5f * ((2f * b) + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3)
}

/** Quanti punti ha il contorno che si disegna: il doppio di quelli simulati. */
internal const val DropRenderPoints = 96

// --- Le utilita' --------------------------------------------------------------------------------

/** Smoothstep fra [from] e [to]. */
internal fun liquidSmoothstep(from: Float, to: Float, x: Float): Float {
  val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * La sagoma a riposo di un viaggio fra due lastre al progresso [t]: il lerp fino all'1; oltre il
 * traguardo gonfiato attorno al proprio centro (`1 + gain·(t − 1)` per asse); sotto lo zero la
 * partenza sgonfiata allo stesso modo (`1 + gain·t`), attorno al suo centro — il ritorno che oltrepassa
 * l'ancora e si ricompone, come nel banco.
 */
internal fun dropRestSlab(from: FluidForm.Slab, to: FluidForm.Slab, t: Float, gainX: Float, gainY: Float): FluidForm.Slab = when {
  t > 1f -> inflateSlab(to, to.frame.center.x, to.frame.center.y, 1f + (t - 1f) * gainX, 1f + (t - 1f) * gainY)
  t < 0f -> inflateSlab(
    from,
    from.frame.center.x,
    from.frame.center.y,
    (1f + t * gainX).coerceAtLeast(0.5f),
    (1f + t * gainY).coerceAtLeast(0.5f),
  )
  else -> {
    val v = lerpSlab(from, to, t)
    FluidForm.Slab(Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]), FluidCornerRadii(v[4], v[5], v[6], v[7]))
  }
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
    blurScale = optics.blurScale * (1f - LensBlurDrop * w),
    refractionHeight = (optics.refractionHeight.value * (1f + LensHeightGain * w)).dp,
    refractionAmount = (optics.refractionAmount.value * (1f + LensAmountGain * w)).dp,
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
    dispersion = material.dispersion + (LensDispersion - material.dispersion).coerceAtLeast(0f) * w,
  )
}

/** L'ingrandimento al centro della lente in moto, 0 a riposo. */
internal fun liquidMagnify(weight: Float): Float {
  val w = weight.coerceIn(0f, 1f)
  return if (w <= 0f) 0f else LensMagnify * w
}

/** La lente in moto: altezza e spostamento cresciuti, sfocatura calata. */
internal const val LensHeightGain = 0.6f
internal const val LensAmountGain = 0.8f
internal const val LensBlurDrop = 0.45f

/** L'ingrandimento al centro, a lente piena: un filo, la goccia non e' una lente da orologiaio. */
internal const val LensMagnify = 0.035f

/** La frangia della goccia in moto, come peso: meta' di quella di un tasto. */
internal const val LensDispersion = 0.5f
