package dev.antigravity.fluidengine.foundation.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * La scala del livello audio (F8.13): da un campione PCM a un'altezza da disegnare fra 0 e 1, **che non satura**.
 *
 * Un'onda lineare mostra solo le voci forti (una voce a mezzo metro sta fra -30 e -20 dBFS, cioè fra il 3% e il 10%
 * dell'altezza), e una scala in decibel pura porta ogni frase un po' alta al soffitto: le barre diventano un muro e
 * non si legge più dove si parla. Qui la scala è in decibel da [FloorDb] a 0 dBFS, e sopra [Knee] il valore si piega
 * con una tangente iperbolica: una voce forte sta ancora sotto il soffitto, e solo un segnale a fondo scala ci arriva
 * vicino (a 0 dBFS di RMS il valore è ~0,93). Il soffitto vero (1) non si raggiunge mai: lo «satura» resta un segnale a
 * parte, [isClipping], letto sul picco dei campioni.
 *
 * Numeri a occhio del 25 novembre 2026 (F8.13), tarati sulla lezione di prova della galleria e sull'onda della guida
 * dell'iPhone (Memo Vocali, iOS 26), dove una voce normale occupa circa un terzo della fascia: fiducia media.
 */
object FluidAudioLevel {

  /** Il pavimento: sotto -54 dBFS di RMS il valore è 0 (respiro, ventole, il fruscio del microfono). */
  const val FloorDb: Float = -54f

  /** Il ginocchio: fino qui il valore segue i decibel, sopra si piega senza toccare il soffitto. */
  const val Knee: Float = 0.72f

  /** Un picco da qui in su (in frazione del fondo scala) è un campione tagliato: [isClipping]. */
  const val ClipPeak: Float = 0.985f

  /**
   * L'altezza per un RMS [rms] in frazione del fondo scala (0..1). Monotona: un suono più forte non è mai più basso.
   */
  fun fromRms(rms: Float): Float {
    if (rms.isNaN() || rms <= 0f) return 0f
    return fromDb(20f * log10(rms.coerceAtMost(1f)))
  }

  /** L'altezza per un livello in dBFS (0 o meno). */
  fun fromDb(db: Float): Float {
    if (db.isNaN()) return 0f
    val linear = ((db - FloorDb) / -FloorDb).coerceIn(0f, 1f)
    if (linear <= Knee) return linear
    val room = 1f - Knee
    return Knee + room * tanh((linear - Knee) / room)
  }

  /** L'RMS dei campioni PCM 16 bit `samples[from until to]`, in frazione del fondo scala. Niente allocazioni. */
  fun rms(samples: ShortArray, from: Int = 0, to: Int = samples.size): Float {
    val start = from.coerceIn(0, samples.size)
    val end = to.coerceIn(start, samples.size)
    if (end == start) return 0f
    var sum = 0.0
    for (i in start until end) {
      val s = samples[i].toDouble()
      sum += s * s
    }
    return (sqrt(sum / (end - start)) / FullScale).toFloat()
  }

  /** Il picco dei campioni `samples[from until to]`, in frazione del fondo scala. */
  fun peak(samples: ShortArray, from: Int = 0, to: Int = samples.size): Float {
    val start = from.coerceIn(0, samples.size)
    val end = to.coerceIn(start, samples.size)
    var best = 0
    for (i in start until end) best = max(best, abs(samples[i].toInt()))
    return (best / FullScale).toFloat()
  }

  /** Vero se un picco [peak] (da [peak]) è un campione tagliato. */
  fun isClipping(peak: Float): Boolean = peak >= ClipPeak

  /** Il fondo scala dei campioni a 16 bit. */
  private const val FullScale = 32768.0
}
