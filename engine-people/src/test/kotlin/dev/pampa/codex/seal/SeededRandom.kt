package dev.pampa.codex.seal

/**
 * Il generatore pseudocasuale dei sigilli: SplitMix64.
 *
 * Non e' `java.util.Random` e non e' `kotlin.random.Random` per una ragione che qui e' tutto: **due
 * telefoni devono disegnare lo stesso sigillo**. Un generatore della libreria puo' cambiare
 * implementazione fra versioni della piattaforma, e il giorno in cui succede la stessa gemma
 * apparirebbe diversa a chi manda e a chi riceve. SplitMix64 e' quaranta righe di aritmetica
 * definita fino all'ultimo bit, quindi non ha versioni.
 */
class SeededRandom(seed: Long) {

  private var state: Long = seed

  fun nextLong(): Long {
    state += -0x61c8864680b583ebL // il passo aureo di SplitMix64
    var z = state
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
  }

  /** In `[0, 1)`, con 24 bit di risoluzione: piu' che sufficienti per una geometria. */
  fun nextFloat(): Float = ((nextLong() ushr 40) / 16_777_216f)

  /** In `[from, until)`. */
  fun nextFloat(from: Float, until: Float): Float = from + nextFloat() * (until - from)

  /** In `[0, bound)`. */
  fun nextInt(bound: Int): Int {
    require(bound > 0) { "limite non positivo: $bound" }
    return ((nextLong() ushr 33) % bound).toInt()
  }

  /** In `[from, until]`, estremi inclusi: comodo per "da tre a cinque". */
  fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from + 1)

  fun nextBoolean(): Boolean = (nextLong() ushr 63) == 1L

  /** Un elemento della lista, senza toglierlo. */
  fun <T> pick(items: List<T>): T = items[nextInt(items.size)]
}
