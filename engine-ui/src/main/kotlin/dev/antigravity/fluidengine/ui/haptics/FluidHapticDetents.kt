package dev.antigravity.fluidengine.ui.haptics

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Cosa merita un passo di un gesto continuo: niente, un tick, o il colpo di un'estremita'. */
internal enum class FluidDetent { None, Tick, Edge }

/**
 * Da un movimento continuo a scatti discreti, senza ronzio (2.11.0).
 *
 * Un dito che scorre manda un evento a ogni fotogramma del touchscreen — 120, 240 al secondo — e
 * vibrare a ognuno e' il ronzio che fa sembrare economico qualsiasi controllo: il motore non fa in
 * tempo a fermarsi fra un impulso e l'altro, e quello che si sente e' una vibrazione continua, non
 * una serie di tacche. Qui il movimento diventa tacche in due modi, e in entrambi c'e' un pavimento
 * di tempo fra una tacca e la successiva:
 *
 *  * **per distanza** ([travel]): un dito che si sposta su un tasto premuto. Una tacca ogni passo
 *    di strada percorsa, contata sul percorso e non sullo spostamento netto, cosi' anche un dito che
 *    va avanti e indietro sul posto fa sentire che il tasto lo sta seguendo. Il primo passo intero fa
 *    anche da zona morta: il tremolio di un pollice appoggiato non arriva mai a una tacca;
 *  * **per gradino** ([step]): la maniglia di uno slider che passa da un ventesimo all'altro. Le due
 *    estremita' sono un [FluidDetent.Edge] e non subiscono il pavimento: sono il "sei arrivato in
 *    fondo", che non deve mai sparire perche' due millisecondi prima e' passata una tacca.
 *
 * Il tempo e' quello dell'evento (`PointerInputChange.uptimeMillis`), non un orologio letto qui:
 * cosi' il pavimento e' esatto anche quando gli eventi arrivano a gruppi, e la classe si prova sul
 * computer senza fingere nessun orologio. E' un pavimento in piu', non al posto di quello di
 * [FluidHapticsImpl] (40 ms per tutti i tick continui dell'app): questo e' del singolo gesto e piu'
 * largo, perche' una tacca ogni 55 ms e' gia' il massimo che un dito sa contare.
 */
internal class FluidHapticDetents(
  private val minGapMillis: Long = DETENT_MIN_GAP_MILLIS,
) {
  private var travelled = 0f
  private var lastStep = 0
  private var lastAt = Long.MIN_VALUE / 2
  private var edge = NoEdge

  /** Un gesto nuovo: niente strada percorsa, e il gradino [step] e' quello da cui si parte. */
  fun restart(step: Int = 0) {
    travelled = 0f
    lastStep = step
    edge = NoEdge
  }

  /**
   * Il gesto riparte da una frazione gia' nota (la maniglia afferrata dov'e'): le estremita' contano
   * come gia' raggiunte, cosi' afferrare uno slider in fondo non suona come arrivarci.
   */
  fun restartAt(fraction: Float, steps: Int) {
    restart(stepOf(fraction, steps))
    edge = edgeOf(fraction)
  }

  /**
   * Il dito ha percorso [deltaPx] in piu'. Vero se questa strada merita una tacca adesso.
   *
   * @param stepPx la strada fra una tacca e l'altra.
   */
  fun travel(deltaPx: Float, stepPx: Float, nowMillis: Long): Boolean {
    if (stepPx <= 0f) return false
    travelled += abs(deltaPx)
    val step = floor(travelled / stepPx).toInt()
    if (step == lastStep) return false
    lastStep = step
    return admit(nowMillis)
  }

  /**
   * La maniglia e' a [fraction] (0..1) di una pista divisa in [steps] gradini. Cosa si sente.
   */
  fun step(fraction: Float, steps: Int, nowMillis: Long): FluidDetent {
    val currentEdge = edgeOf(fraction)
    val step = stepOf(fraction, steps)
    val stepChanged = step != lastStep
    lastStep = step
    if (currentEdge != edge) {
      edge = currentEdge
      if (currentEdge != NoEdge) {
        // Il colpo in fondo passa sempre, e conta come l'ultima tacca: la successiva, uscendo
        // dall'estremita', aspetta il suo turno come tutte.
        lastAt = nowMillis
        return FluidDetent.Edge
      }
    }
    if (!stepChanged) return FluidDetent.None
    return if (admit(nowMillis)) FluidDetent.Tick else FluidDetent.None
  }

  private fun admit(nowMillis: Long): Boolean {
    if (nowMillis - lastAt < minGapMillis) return false
    lastAt = nowMillis
    return true
  }

  private fun stepOf(fraction: Float, steps: Int): Int =
    (fraction.coerceIn(0f, 1f) * steps.coerceAtLeast(1)).roundToInt()

  private fun edgeOf(fraction: Float): Int = when {
    fraction <= 0f -> EdgeStart
    fraction >= 1f -> EdgeEnd
    else -> NoEdge
  }

  companion object {
    /**
     * Il pavimento fra due tacche dello stesso gesto. Sopra i 40 ms del motore, perche' quello e'
     * il limite di cio' che il vibratore riesce a separare, questo di cio' che un dito riesce a
     * contare: piu' fitte, le tacche tornano a fondersi in un ronzio anche se il motore le separa.
     */
    const val DETENT_MIN_GAP_MILLIS = 55L

    private const val NoEdge = 0
    private const val EdgeStart = 1
    private const val EdgeEnd = 2
  }
}
