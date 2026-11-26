package dev.antigravity.fluidengine.ink

import android.graphics.Matrix

// F8.16b · La misura della latenza del tratto, per il tablet (`adb shell setprop log.tag.FluidInkLatency DEBUG`): a
// tratto finito una riga nel log con le tre parti, in ms. Spenta di serie: senza la proprieta' non registra niente e non
// alloca.
//
// La lezione della prima finestra sul tablet (26/11/2026): androidx.ink 1.0 compila i suoi dati di latenza (`LatencyData`)
// **solo** per i punti che riceve come `MotionEvent`; per quelli che riceve a gruppi (`StrokeInputBatch`) non ne crea
// nessuno (il TODO b/364655356 nella libreria), e la resa risultava «n=0». Per questo il tratto veloce passa gli eventi
// veri ad androidx.ink (con le trasformazioni qui sotto), e resta a gruppi solo il tratto lungo il righello, le cui
// posizioni le sposta l'engine.

/** Il tag del log della latenza del tratto, e quanti campioni tiene per tratto. */
internal const val LatencyTag = "FluidInkLatency"
internal const val LatencySamples = 2048

/**
 * Le tre parti della latenza di un tratto, in nanosecondi:
 * - **ingresso**: dal sensore (il tempo del `MotionEvent`) a quando il foglio riceve il punto (la consegna di Compose);
 * - **resa**: da quando androidx.ink riceve il punto a quando il punto e' disegnato;
 * - **totale**: dal sensore a quando il punto e' disegnato (`osDetectsEvent` → la fine del disegno).
 *
 * «Disegnato» vuol dire l'istante dei pixel stimato da androidx.ink (`estimatedPixelPresentationTime`) se c'e'; se no la
 * fine delle chiamate di disegno nel front buffer (`canvasFrontBufferStrokesRenderHelperData.finishesDrawCalls`). Il
 * disegnatore che androidx.ink 1.0 usa da Android 13 (front buffer) **non stima mai l'istante dei pixel** (lo si e' visto
 * sul Tab S9 il 26/11/2026: la resa era sempre «n=0»), ma segna quando ha finito di disegnare: nel front buffer il punto
 * va sullo schermo al passaggio successivo della scansione del pannello, cioe' entro un refresh (8,3 ms a 120 Hz), in
 * media mezzo. La riga del log dice quale dei due istanti ha usato.
 *
 * Conta solo i punti veri aggiunti al tratto (`ADD`): l'inizio, la fine e i punti predetti hanno tempi d'altro genere.
 * Un campo che androidx.ink non ha compilato (`Long.MIN_VALUE`) non conta.
 */
internal class InkLatencyProbe(private val capacity: Int = LatencySamples) {
  private val input = LongArray(capacity)
  private val render = LongArray(capacity)
  private val total = LongArray(capacity)
  var inputCount = 0
    private set
  var renderCount = 0
    private set
  var totalCount = 0
    private set

  /** Il foglio ha ricevuto un punto rilevato a [eventNanos], adesso ([nowNanos]). */
  fun input(eventNanos: Long, nowNanos: Long) {
    if (inputCount >= capacity || eventNanos == Long.MIN_VALUE) return
    input[inputCount++] = (nowNanos - eventNanos).coerceAtLeast(0L)
  }

  /** Quanti punti avevano l'istante dei pixel stimato, e quanti solo la fine del disegno nel front buffer. */
  var presentedCount = 0
    private set
  var drawnCount = 0
    private set

  /**
   * I dati di androidx.ink di un punto ([add] vero per un punto vero aggiunto al tratto): la fine e' [presentationNanos]
   * se androidx.ink l'ha stimata, se no [frontBufferDoneNanos].
   */
  fun ink(add: Boolean, osDetectsNanos: Long, getsActionNanos: Long, presentationNanos: Long, frontBufferDoneNanos: Long = Long.MIN_VALUE) {
    if (!add) return
    val end = when {
      presentationNanos != Long.MIN_VALUE -> presentationNanos.also { presentedCount++ }
      frontBufferDoneNanos != Long.MIN_VALUE -> frontBufferDoneNanos.also { drawnCount++ }
      else -> return
    }
    if (getsActionNanos != Long.MIN_VALUE && renderCount < capacity) render[renderCount++] = (end - getsActionNanos).coerceAtLeast(0L)
    if (osDetectsNanos != Long.MIN_VALUE && totalCount < capacity) total[totalCount++] = (end - osDetectsNanos).coerceAtLeast(0L)
  }

  /** La riga del log: mediana, 95° percentile e massimo di ogni parte, e fino a quale istante (pixel o disegno). */
  fun summary(): String {
    val until = when {
      presentedCount > 0 && drawnCount == 0 -> " (fino ai pixel stimati)"
      drawnCount > 0 && presentedCount == 0 -> " (fino al disegno nel front buffer)"
      presentedCount > 0 -> " (pixel stimati $presentedCount, disegno $drawnCount)"
      else -> ""
    }
    return "ingresso: ${stats(input, inputCount)} | resa: ${stats(render, renderCount)} | totale: ${stats(total, totalCount)}$until"
  }

  fun reset() {
    inputCount = 0
    renderCount = 0
    totalCount = 0
    presentedCount = 0
    drawnCount = 0
  }

  private fun stats(values: LongArray, count: Int): String {
    if (count == 0) return "n=0"
    val sorted = values.copyOf(count).also { it.sort() }
    fun ms(v: Long) = "%.1f".format(java.util.Locale.ROOT, v / 1_000_000.0)
    return "n=$count p50=${ms(sorted[count / 2])} p95=${ms(sorted[(count * 95 / 100).coerceAtMost(count - 1)])} max=${ms(sorted[count - 1])} ms"
  }
}

/**
 * Le due trasformazioni per dare ad androidx.ink gli eventi veri, che arrivano nelle coordinate della vista di Compose
 * (la radice) e non del foglio: [toView] porta un punto dell'evento nel foglio (lo spostamento [dx], [dy] e' la posizione
 * locale di Compose meno quella dell'evento, letto sul primo evento del tratto), [toWorld] lo porta nelle coordinate del
 * foglio (`(locale − angolo) / scala`, come [InkView.toWorld]). Il tratto di androidx.ink sta cosi' nelle stesse
 * coordinate del modello.
 */
internal class InkMotionTransforms(dx: Float, dy: Float, scale: Float, ox: Float, oy: Float) {
  val toView: Matrix = Matrix().apply { setTranslate(dx, dy) }
  val toWorld: Matrix = Matrix().apply {
    setTranslate(dx - ox, dy - oy)
    postScale(1f / scale, 1f / scale)
  }
}

/**
 * Quale via usa il tratto veloce: gli eventi veri (con la latenza misurata da androidx.ink e i suoi punti storici), o i
 * gruppi di punti dell'engine quando l'engine li sposta (il tratto lungo il righello) o quando l'evento non c'e'.
 */
internal fun inkUsesMotionEvents(snappedToRuler: Boolean, hasMotionEvent: Boolean, hasPointerId: Boolean): Boolean =
  !snappedToRuler && hasMotionEvent && hasPointerId
