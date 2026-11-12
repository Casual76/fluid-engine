package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ------------------------------------------------------------------------------------------------------------
// Il collo che dura (F1B.9)
//
// Il collo liquido fra due pezzi che si separano o si uniscono, nel materiale nuovo, per i viaggi di `morphTo`.
// Fino a F1B il collo moriva col **progresso** del viaggio (`travelFusion`, l'ultimo 32%), e la molla di serie
// arriva al 99% del progresso in ~160 ms su ~660: il collo durava due o tre fotogrammi a 120 Hz (~25 ms, sessione
// finale di F1B, sezione 4.6). Qui vive per un tempo suo, misurato sul **tempo che manca alla posa** della molla,
// e la sua forma non dipende più da quanto lontano sono i pezzi ma da una sola coordinata nel tempo:
//
//  - ν = 1: il collo è pieno, cioè il raccordo più grande che non porta mai il contorno fuori dal filo dei lobi
//    ([physicsNeckFull]): due pezzi vicini sono una goccia sola, senza tacca né perlina;
//  - 0 < ν < 1: la vita del collo è ν^(2/3) di quella piena, la legge della rottura di un getto (il raggio del
//    collo va come il tempo alla rottura alla 2/3): si assottiglia piano e si strozza di colpo;
//  - ν = 0: la rottura, una volta sola, a una frazione fissa del tempo del viaggio (`GlassLiquidNeck`);
//  - -1 < ν < 0: pezzi staccati, i lobi che si ritirano;
//  - ν = -1: nessun raccordo. È il riposo: pezzi separati del tutto (scelta di Alessio, 2026-11-11).
//
// Le guide esterne (`driveExternally`: la barra fra le pagine) non hanno un orologio della molla e tengono la
// legge del progresso, com'era ([PhysicsProgressNeckFade]).
// ------------------------------------------------------------------------------------------------------------

/**
 * Il collo delle guide esterne e dei piani costruiti a mano, che seguono ancora il **progresso**
 * ([travelFusion]): il valore di F1B (`GlassLiquidNeck` fino a F1B.9), congelato qui perché la manopola `travelneck`
 * ora regola il collo nel tempo. La barra fra le pagine è di F4A: non cambia aspetto per questo pacchetto.
 */
internal const val PhysicsProgressNeckFade: Float = 0.32f

/**
 * In quanta parte del viaggio il collo nasce (F1B.9): 9% del tempo fino alla posa, ~60 ms con la molla di serie
 * (~660 ms), in una salita che decelera; il ponte nasce a un quinto di questa, ~12 ms. Due tasti che si uniscono in (c)
 * si toccano a ~30 ms: a quel punto il collo è già quasi pieno e la giunzione è una U larga e tonda che si riempie in due
 * fotogrammi, non la V di un fotogramma della sessione finale. Il prezzo è un fotogramma in cui il ponte, appena nato, è
 * sottile fra tasti ancora lontani. Provato sul tablet anche 15% (~100 ms, `moto/f1b/collo/giro2-salita015/`): il ponte
 * nasce più vicino, ma al contatto il collo è debole e la giunzione torna una tacca stretta a punta tonda, troppo simile
 * alla V. 2026-11-12.
 */
internal const val PhysicsNeckRise: Float = 0.09f

/**
 * Mezza larghezza della strozzatura, in frazione del tempo del viaggio (F1B.9): ~80 ms con la molla di serie. Il
 * collo pieno comincia ad assottigliarsi a `GlassLiquidNeck` meno questa, si rompe a `GlassLiquidNeck` e i lobi sono
 * del tutto tondi a `GlassLiquidNeck` più questa. Mai più larga di `GlassLiquidNeck`: il primo fotogramma di un viaggio
 * deve avere il collo che ha ereditato (vedi [physicsNeckAt]).
 */
internal const val PhysicsNeckPinch: Float = 0.12f

/**
 * L'esponente della vita del collo: raggio del collo ∝ (tempo alla rottura)^(2/3), la legge della rottura di un
 * getto non viscoso. Con un esponente uno il collo indugiava sottile per 4–5 fotogrammi (le «punte» della partenza);
 * con 2/3 resta spesso più a lungo e si strozza in un fotogramma.
 */
internal const val PhysicsNeckPinchExponent: Float = 2f / 3f

/**
 * Fin dove il collo arriva, in multipli del raggio di fusione del gruppo (`blendRadius`, la distanza di fusione che
 * l'app dichiara): fino a un raggio di fusione di varco il collo è intero, a due non c'è più. Due pezzi lontani più di
 * così non si toccano mai, qualunque cosa dica l'orologio.
 */
internal const val PhysicsNeckReach: Float = 2f

/**
 * Lo spostamento con cui un piano porta il collo nel tempo nella stessa casella del collo nel progresso
 * (`PhysicsRenderPlan.fusion`): 0..1 è il peso di [travelFusion], `3 + ν` (cioè 2..4) è la coordinata del collo nel
 * tempo. Una casella sola perché chi copia un piano (la serie di robustezza lo congela per rimisurarlo) porti il collo
 * con sé senza saperlo.
 */
internal const val PhysicsNeckTimedBase: Float = 3f

/** Il valore da scrivere in `PhysicsRenderPlan.fusion` per la coordinata del collo [neck] (-1..1). */
internal fun physicsNeckEncode(neck: Float): Float =
  PhysicsNeckTimedBase + (if (neck.isFinite()) neck.coerceIn(-1f, 1f) else -1f)

/** Vero se [fusion] porta un collo nel tempo (F1B.9) e non il peso del progresso. */
internal fun physicsNeckIsTimed(fusion: Float): Boolean = fusion >= PhysicsNeckTimedBase - 1.5f

/** La coordinata del collo portata da [fusion] (vedi [physicsNeckEncode]). */
internal fun physicsNeckDecode(fusion: Float): Float = (fusion - PhysicsNeckTimedBase).coerceIn(-1f, 1f)

/** Tiene [fusion] dentro uno dei due intervalli validi: 0..1 (progresso) o 2..4 (tempo). */
internal fun physicsNeckSanitize(fusion: Float): Float = when {
  !fusion.isFinite() -> 0f
  physicsNeckIsTimed(fusion) -> fusion.coerceIn(PhysicsNeckTimedBase - 1f, PhysicsNeckTimedBase + 1f)
  else -> fusion.coerceIn(0f, 1f)
}

// --- La legge nel tempo -------------------------------------------------------------------------------------

/**
 * La coordinata del collo a [elapsed], la frazione del tempo del viaggio già passata (0 alla partenza, 1 alla posa).
 *
 * Nasce da [start] (il collo del viaggio interrotto, -1 da fermi) e sale a 1 in [PhysicsNeckRise], decelerando; resta
 * pieno; si strozza attorno a [breakAt] in [PhysicsNeckPinch] per parte, con una rampa più ripida proprio allo zero
 * (lo smoothstep di uno smoothstep): la rottura passa in un fotogramma. Alla posa vale -1.
 *
 * Al primo istante vale [start], qualunque sia [breakAt]: la strozzatura non comincia mai prima della partenza
 * (per questo la sua mezza larghezza è al più [breakAt]). È ciò che fa ripartire un viaggio interrotto o invertito dal
 * collo che c'era.
 */
internal fun physicsNeckAt(elapsed: Float, start: Float, breakAt: Float): Float {
  if (!elapsed.isFinite()) return -1f
  val x = elapsed.coerceIn(0f, 1f)
  val s = if (start.isFinite()) start.coerceIn(-1f, 1f) else -1f
  val b = if (breakAt.isFinite()) breakAt.coerceIn(0.05f, 0.5f) else 0.32f
  val h = min(PhysicsNeckPinch, b)
  val r = (x / PhysicsNeckRise).coerceIn(0f, 1f)
  val risen = s + (1f - s) * (1f - (1f - r) * (1f - r) * (1f - r))
  val u = (x - (b - h)) / (2f * h)
  val fall = 1f - physicsNeckPinchEase(u)
  return (-1f + (risen + 1f) * fall).coerceIn(-1f, 1f)
}

/** Lo smoothstep di uno smoothstep: piano ai due capi, il doppio più ripido in mezzo (2,25 contro 1,5). */
private fun physicsNeckPinchEase(u: Float): Float {
  val a = u.coerceIn(0f, 1f)
  val s = a * a * (3f - 2f * a)
  return s * s * (3f - 2f * s)
}

/**
 * L'orologio del collo di un viaggio di `morphTo`: quanta parte del tempo fino alla posa è passata, letta dallo stato
 * della molla (progresso e velocità) con la stessa aritmetica con cui Compose decide quando la molla è posata
 * ([FloatSpringSpec.getDurationNanos]). Una molla non ha memoria: il tempo che le manca dipende solo da dove sta e da
 * quanto corre, quindi l'orologio non ha bisogno di un frame clock e il piano resta una funzione pura dello stato.
 *
 * Per un viaggio che non è una molla (la coreografia della casa, una tween) non c'è uno stato da cui leggere il
 * tempo: l'orologio usa il progresso, che lì cresce col tempo.
 */
internal class PhysicsNeckClock private constructor(
  private val spring: FloatSpringSpec?,
  private val totalNanos: Long,
  private val startVelocity: Float,
) {
  /**
   * La frazione del viaggio già passata, 0..1, con il progresso a [progress] e la sua velocità a [velocity].
   *
   * A progresso zero la velocità è quella di partenza, non quella letta: fra l'installazione di un viaggio e il suo
   * primo fotogramma il progresso può dire ancora la velocità di prima (zero), e l'orologio di un viaggio che eredita
   * una velocità avrebbe letto un tempo già passato, cioè un collo che salta proprio all'inversione.
   */
  fun elapsed(progress: Float, velocity: Float): Float {
    if (!progress.isFinite()) return 1f
    val s = spring ?: return progress.coerceIn(0f, 1f)
    if (totalNanos <= 0L) return 1f
    val v = if (progress == 0f || !velocity.isFinite()) startVelocity else velocity
    val remaining = s.getDurationNanos(progress, 1f, v)
    return (1f - remaining.toFloat() / totalNanos.toFloat()).coerceIn(0f, 1f)
  }

  companion object {
    /** L'orologio del viaggio che parte ora con [spec] verso 1, alla velocità [initialVelocity]. */
    fun of(spec: AnimationSpec<Float>, initialVelocity: Float): PhysicsNeckClock {
      val v = if (initialVelocity.isFinite()) initialVelocity else 0f
      val s = spec as? SpringSpec<Float> ?: return PhysicsNeckClock(null, 0L, v)
      val spring = FloatSpringSpec(
        dampingRatio = s.dampingRatio,
        stiffness = s.stiffness,
        visibilityThreshold = s.visibilityThreshold ?: Spring.DefaultDisplacementThreshold,
      )
      return PhysicsNeckClock(spring, spring.getDurationNanos(0f, 1f, v), v)
    }
  }
}

// --- La forma del collo -------------------------------------------------------------------------------------

/**
 * Il raccordo più grande fra due capi tondi di raggio r i cui centri distano 2c, in multipli di r, al variare di
 * c/r, per cui il contorno fra i due centri non esce mai dal filo dei lobi (la retta tangente ai due capi) di più del
 * 2‰ di r. Calcolato sullo smooth minimum polinomiale del campo (`glassField`), per bisezione su un reticolo fino:
 * `moto/f1b/collo/strumenti/kfull.py`.
 *
 * Perché questo tetto: fino a F1B il raccordo era il raggio di fusione del gruppo, spento solo quando i fianchi
 * coincidono; in mezzo, con una tacca poco profonda e un raccordo grande, la giunzione usciva dal filo dei tasti
 * (il «piccolo rigonfiamento tondo» di (c) alla sessione finale) e un raccordo all'inviluppo esatto del solo punto
 * di mezzo lasciava una perlina con due fossette ai lati. Oltre c/r ≈ 1,7 (un varco di 1,4 raggi) nessun ponte sta
 * dentro il filo: un collo più lungo sarebbe più grasso dei lobi, e lì il collo non c'è.
 */
private val PhysicsNeckFullX = floatArrayOf(
  0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.8f, 2f,
)
private val PhysicsNeckFullK = floatArrayOf(
  0f, 0.0279f, 0.0871f, 0.1835f, 0.308f, 0.4519f, 0.6099f, 0.7781f, 0.9536f, 1.1347f, 1.3199f, 1.5082f, 1.699f,
  1.8917f, 2.0859f, 2.2815f, 2.4779f, 2.8733f, 3.2709f,
)

/** Il raccordo pieno per c/r = [cOverR], in multipli di r: vedi [PhysicsNeckFullX]. Oltre la tabella, lineare. */
internal fun physicsNeckFull(cOverR: Float): Float {
  if (!(cOverR > 0f)) return 0f
  val xs = PhysicsNeckFullX
  val ks = PhysicsNeckFullK
  val last = xs.size - 1
  if (cOverR >= xs[last]) return ks[last] + (cOverR - xs[last]) * (ks[last] - ks[last - 1]) / (xs[last] - xs[last - 1])
  var i = 1
  while (xs[i] < cOverR) i++
  val f = (cOverR - xs[i - 1]) / (xs[i] - xs[i - 1])
  return ks[i - 1] + (ks[i] - ks[i - 1]) * f
}

/**
 * (vita del collo pieno a contatto / r)²: il collo pieno di due capi che si toccano. Lega la forma di due pezzi
 * sovrapposti a quella di due pezzi staccati, perché al contatto le due regole diano lo stesso raccordo.
 */
private val PhysicsNeckTouchShape: Float = run {
  val full = physicsNeckFull(1f)
  (1f + full / 4f) * (1f + full / 4f) - 1f
}

/**
 * Il raccordo, in pixel, fra due pezzi alla coordinata del collo [neck], prima della scala della cattura.
 *
 * @param depth di quanto si compenetrano lungo l'asse dove entrano meno: positivo sovrapposti, negativo staccati
 *   (allora `-depth` è il varco);
 * @param ends la somma dei raggi d'angolo dei due capi che si guardano;
 * @param nominal il raggio di fusione del gruppo: zero, nessun collo; fissa fin dove il collo arriva ([PhysicsNeckReach]).
 *
 * Staccati, il ponte c'è se il raccordo supera il doppio del varco (lo smooth minimum fa ponte finché il varco è meno
 * di mezzo raggio): fra la rottura (ν = 0, raccordo = 2·varco) e il pieno la vita del collo a metà strada è
 * ν^(2/3) della vita piena. Sovrapposti, il raccordo è la stessa forma del contatto scalata sul pieno di adesso: al
 * contatto le due regole coincidono, e due pezzi che coincidono (la fine di ogni riunione) non hanno raccordo.
 */
internal fun physicsNeckPairBlend(depth: Float, ends: Float, nominal: Float, neck: Float): Float {
  if (!(nominal > 0f) || !(ends > 0f) || !depth.isFinite() || !neck.isFinite()) return 0f
  val n = neck.coerceIn(-1f, 1f)
  val r = ends / 2f
  val gap = -depth
  // Mezza distanza fra i centri dei due capi che si guardano: r + varco/2 staccati, meno di r sovrapposti.
  val c = max(ends - depth, 0f) / 2f
  val full = physicsNeckFull(c / r) * r
  if (gap <= 0f) {
    if (n <= 0f) return 0f
    val shape = n.pow(2f * PhysicsNeckPinchExponent)
    val weight = (sqrt(1f + PhysicsNeckTouchShape * shape) - 1f) / (sqrt(1f + PhysicsNeckTouchShape) - 1f)
    return weight * full
  }
  val reach = 1f - liquidSmoothstep(nominal, PhysicsNeckReach * nominal, gap)
  if (!(reach > 0f)) return 0f
  val bridge = min(2f * gap, full)
  val k = when {
    n < 0f -> bridge * (1f + n)
    full > 2f * gap -> {
      val fullWaist2 = (r + full / 4f) * (r + full / 4f) - c * c
      val waist = n.pow(PhysicsNeckPinchExponent) * sqrt(max(fullWaist2, 0f))
      4f * (sqrt(c * c + waist * waist) - r)
    }
    else -> full
  }
  return reach * max(k, 0f)
}

/**
 * Il raccordo del pezzo [index] col già fuso, alla coordinata del collo [neck], in pixel di layout: quello con il
 * pezzo **più vicino** fra quelli che lo precedono (il campo lo unisce a tutti con un raggio solo, e il collo che si
 * vede è quello col vicino).
 *
 * I raggi d'angolo sono quelli che il campo vede (`fieldRadii`): il tetto senza perlina vale per gli angoli che il campo
 * disegna davvero. Con quelli della geometria del viaggio (`pieceRadii`, provati) il raccordo resta continuo quando il
 * raggio del campo salta al cambio di destinazione, ma è calcolato per angoli più tondi di quelli disegnati e gonfia
 * la giunzione: sulla serie di robustezza andava peggio (vedi il rapporto di F1B.9).
 */
internal fun physicsTimedNeckBlend(plan: PhysicsRenderPlan, index: Int, neck: Float): Float {
  val nominal = plan.nominalBlend
  if (!(nominal > 0f) || index <= 0) return 0f
  val at = index * 4
  var best = Float.NEGATIVE_INFINITY
  var bestEnds = 0f
  for (j in 0 until index) {
    val bt = j * 4
    val dx = abs(plan.pieceRects[at] - plan.pieceRects[bt])
    val dy = abs(plan.pieceRects[at + 1] - plan.pieceRects[bt + 1])
    val depth = -max(dx - (plan.pieceRects[at + 2] + plan.pieceRects[bt + 2]), dy - (plan.pieceRects[at + 3] + plan.pieceRects[bt + 3]))
    if (depth > best) {
      best = depth
      bestEnds = physicsNeckCorner(plan, index) + physicsNeckCorner(plan, j)
    }
  }
  return physicsNeckPairBlend(best, bestEnds, nominal, neck)
}

/** Il raggio d'angolo medio del pezzo [index] nel campo: quanto sono tondi i suoi capi. */
private fun physicsNeckCorner(plan: PhysicsRenderPlan, index: Int): Float {
  val at = index * 4
  return (plan.fieldRadii[at] + plan.fieldRadii[at + 1] + plan.fieldRadii[at + 2] + plan.fieldRadii[at + 3]) / 4f
}
