package dev.pampa.codex.seal

/**
 * L'alfabeto runico dei sigilli.
 *
 * I caratteri vengono dal blocco Runic di Unicode e sono disegnati dal font incluso nell'APK: su un
 * telefono qualsiasi il font di sistema non ha quei glifi, e senza font si vedrebbero rettangoli
 * vuoti — il modo piu' rapido di trasformare un sigillo in un errore.
 *
 * La corrispondenza fra lettere e rune **non e' una cifratura**: quella vera sta in `:core:crypto` e
 * ha gia' fatto il suo lavoro prima che questo file venga chiamato. Qui serve solo che il disordine
 * sia coerente dentro un messaggio (la stessa lettera diventa sempre la stessa runa) e diverso fra
 * un messaggio e l'altro, cosi' due sigilli non si somigliano mai.
 */
object RuneAlphabet {

  /**
   * Il vecchio Futhark piu' le varianti anglosassoni: 75 glifi, tutti presenti in Noto Sans Runic.
   * Sono tanti apposta — mentre il messaggio si rivela le rune scorrono, e con ventiquattro segni si
   * vedrebbe il ciclo ripetersi.
   */
  private val POOL: List<Char> = ('\u16A0'..'\u16EA').toList()

  /** I tre segni di interpunzione runici: separano le parole senza uscire dall'alfabeto. */
  private val PUNCTUATION: List<Char> = listOf('\u16EB', '\u16EC', '\u16ED')

  /** Una runa qualunque: serve all'animazione, che ne cambia una decina al secondo. */
  fun randomRune(random: SeededRandom): Char = POOL[random.nextInt(POOL.size)]

  fun runeAt(index: Int): Char = POOL[Math.floorMod(index, POOL.size)]

  val size: Int get() = POOL.size

  /**
   * Trascrive un testo in rune.
   *
   * Gli spazi restano spazi (una fila di rune senza pause non si legge come un messaggio), la
   * punteggiatura diventa punteggiatura runica, tutto il resto pesca dalla permutazione del seme.
   */
  fun transcribe(text: String, seed: Long): String {
    val mapping = mappingFor(seed)
    val builder = StringBuilder(text.length)
    val punctuationRandom = SeededRandom(seed xor 0x5EA1)
    for (character in text) {
      builder.append(
        when {
          character.isWhitespace() -> ' '
          character.isLetterOrDigit() -> mapping.getValue(character.lowercaseChar())
          else -> PUNCTUATION[punctuationRandom.nextInt(PUNCTUATION.size)]
        },
      )
    }
    return builder.toString()
  }

  /**
   * La permutazione di questo messaggio: da lettera a runa.
   *
   * Costruita mescolando la riserva con il seme e assegnandola all'alfabeto latino piu' le cifre.
   * Deterministica, quindi chi manda e chi riceve vedono lo stesso sigillo senza scambiarsi niente.
   */
  fun mappingFor(seed: Long): Map<Char, Char> {
    val alphabet = ('a'..'z') + ('0'..'9') + listOf('à', 'è', 'é', 'ì', 'ò', 'ù')
    val shuffled = POOL.toMutableList()
    val random = SeededRandom(seed)
    // Fisher-Yates con il generatore dei sigilli: `shuffle(Random)` della libreria non garantisce
    // lo stesso risultato fra versioni della piattaforma, e qui il risultato deve essere identico
    // su due telefoni diversi.
    for (index in shuffled.lastIndex downTo 1) {
      val other = random.nextInt(index + 1)
      val swap = shuffled[index]
      shuffled[index] = shuffled[other]
      shuffled[other] = swap
    }
    return alphabet.withIndex().associate { (index, letter) ->
      letter to shuffled[index % shuffled.size]
    }
  }

  /**
   * Quante rune mostrare per un allegato, che di testo non ne ha: una riga corta e sempre uguale
   * per lo stesso messaggio.
   */
  fun placeholder(seed: Long, length: Int = 12): String {
    val random = SeededRandom(seed)
    return buildString { repeat(length) { append(randomRune(random)) } }
  }
}
