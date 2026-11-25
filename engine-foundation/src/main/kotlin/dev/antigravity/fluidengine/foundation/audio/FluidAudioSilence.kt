package dev.antigravity.fluidengine.foundation.audio

import kotlin.math.max

/**
 * I silenzi di un audio, dal suo inviluppo (F8.13): il **taglio dei silenzi** del registratore (quanto tenere in testa e
 * in coda) e i tratti muti che l'onda disegna come puntini, come Memo Vocali.
 *
 * La soglia si adatta alla stanza: è il più alto fra [MinThreshold] e il rumore di fondo misurato (il decimo percentile
 * dell'inviluppo) più [AboveFloor]. In una stanza silenziosa il silenzio è sotto -44 dBFS; accanto a una ventola la
 * soglia sale con lei, e la ventola non diventa «parlato». Le soglie sono quelle della cattura di `engine-ai`
 * (`SpeechCapture.VadConfig.speechFloorRms`, -45 dBFS, provata sul telefono il 3 settembre 2026) portate nella scala di
 * [FluidAudioLevel]: fiducia media.
 *
 * Per saltare i silenzi **mentre si ascolta** (il «Salta silenzi» di Memo Vocali) c'è `engine-playback`, che lo fa col
 * lettore di Media3 senza toccare il file.
 */
object FluidAudioSilence {

  /** La soglia più bassa: -44 dBFS nella scala di [FluidAudioLevel]. */
  val MinThreshold: Float = FluidAudioLevel.fromDb(-44f)

  /** Quanto sopra il rumore di fondo deve stare un valore per non essere silenzio. */
  const val AboveFloor: Float = 0.08f

  /** Un tratto muto più corto di così non è un silenzio: è la pausa fra due parole. */
  const val MinSilenceMillis: Long = 700L

  /** Quanto si tiene prima della prima parola e dopo l'ultima quando si taglia: una frase non deve partire mozzata. */
  const val KeepMillis: Long = 300L

  /** La soglia per [envelope]: vedi l'intestazione. Un inviluppo vuoto ha [MinThreshold]. */
  fun threshold(envelope: FluidAudioEnvelope): Float {
    val count = envelope.size
    if (count == 0) return MinThreshold
    // Il decimo percentile con un istogramma a 64 caselle: niente ordinamento di 360 000 valori.
    val histogram = IntArray(Bins)
    for (i in 0 until count) histogram[(envelope[i] * (Bins - 1)).toInt().coerceIn(0, Bins - 1)]++
    val target = count / 10
    var seen = 0
    var bin = 0
    while (bin < Bins - 1 && seen + histogram[bin] <= target) {
      seen += histogram[bin]
      bin++
    }
    val floor = bin.toFloat() / (Bins - 1)
    return max(MinThreshold, floor + AboveFloor).coerceAtMost(0.95f)
  }

  /**
   * I tratti muti lunghi almeno [minMillis], in millisecondi, in ordine. Un audio tutto muto è un tratto solo.
   */
  fun ranges(
    envelope: FluidAudioEnvelope,
    threshold: Float = threshold(envelope),
    minMillis: Long = MinSilenceMillis,
  ): List<LongRange> {
    val out = ArrayList<LongRange>()
    val bucket = envelope.bucketMillis.toLong()
    var start = -1
    val count = envelope.size
    for (i in 0..count) {
      val quiet = i < count && envelope[i] < threshold
      if (quiet && start < 0) start = i
      if (!quiet && start >= 0) {
        val from = start * bucket
        val to = i * bucket
        if (to - from >= minMillis) out += from until to
        start = -1
      }
    }
    return out
  }

  /**
   * Dove tagliare la testa e la coda mute: dall'inizio della prima parte sonora meno [keepMillis] alla fine dell'ultima
   * più [keepMillis], dentro l'audio. Null se l'audio è tutto muto (non c'è niente da tenere).
   */
  fun trimBounds(
    envelope: FluidAudioEnvelope,
    threshold: Float = threshold(envelope),
    keepMillis: Long = KeepMillis,
  ): LongRange? {
    val count = envelope.size
    var first = -1
    var last = -1
    for (i in 0 until count) {
      if (envelope[i] >= threshold) {
        if (first < 0) first = i
        last = i
      }
    }
    if (first < 0) return null
    val bucket = envelope.bucketMillis.toLong()
    val from = (first * bucket - keepMillis).coerceAtLeast(0L)
    val to = ((last + 1) * bucket + keepMillis).coerceAtMost(envelope.durationMillis)
    return from until to
  }

  private const val Bins = 64
}
