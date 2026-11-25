package dev.pampa.codex.crypto

/**
 * Base32 di Crockford: il modo in cui un pezzo di Codex diventa qualcosa che si puo' incollare in
 * un messaggio, mostrare in un QR o dettare al telefono.
 *
 * L'alfabeto e' lo stesso del Codex ID e per lo stesso motivo: niente I, L, O, U. Chi legge non
 * deve mai chiedersi "uno o elle?", e nessuna parola sgradevole nasce per caso da byte casuali.
 * In lettura le lettere confondibili vengono ricondotte al loro numero, quindi una scheda
 * ricopiata a mano con una `O` al posto di uno zero entra lo stesso.
 *
 * **Non e' una cifratura.** Quello che ci passa dentro resta leggibile a chiunque lo decodifichi:
 * ci passano solo cose pubbliche — schede firmate, chiavi pubbliche — e la firma dentro la scheda
 * e' cio' che le rende affidabili, non la codifica.
 */
object Base32 {

  private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

  /** Da byte a testo. Nessun riempimento: la lunghezza si ricava dai caratteri. */
  fun encode(data: ByteArray): String {
    val out = StringBuilder((data.size * 8 + 4) / 5)
    var buffer = 0L
    var bits = 0
    for (byte in data) {
      buffer = (buffer shl 8) or (byte.toLong() and 0xFF)
      bits += 8
      while (bits >= 5) {
        bits -= 5
        out.append(ALPHABET[((buffer ushr bits) and 0x1F).toInt()])
      }
    }
    // I bit avanzati diventano un ultimo carattere, completato con zeri: in lettura si scartano.
    if (bits > 0) out.append(ALPHABET[((buffer shl (5 - bits)) and 0x1F).toInt()])
    return out.toString()
  }

  /**
   * Da testo a byte, oppure `null` se non e' base32.
   *
   * `null` e non un'eccezione: qui arriva quello che una persona ha incollato, e un incollaggio
   * sbagliato e' un caso normale da spiegare a schermo, non un errore di programmazione.
   */
  fun decode(text: String): ByteArray? {
    val out = ArrayList<Byte>(text.length * 5 / 8 + 1)
    var buffer = 0L
    var bits = 0
    for (raw in text) {
      // Spazi, trattini e a capo li mette chi formatta per far respirare il codice: non contano.
      if (raw.isWhitespace() || raw == '-' || raw == '_') continue
      val symbol = when (val character = raw.uppercaseChar()) {
        'I', 'L' -> '1'
        'O' -> '0'
        'U' -> 'V'
        else -> character
      }
      val value = ALPHABET.indexOf(symbol)
      if (value < 0) return null
      buffer = (buffer shl 5) or value.toLong()
      bits += 5
      if (bits >= 8) {
        bits -= 8
        out.add(((buffer ushr bits) and 0xFF).toByte())
      }
    }
    return out.toByteArray()
  }

  /**
   * Lo stesso testo, spezzato in gruppi perche' l'occhio possa ritrovare il punto.
   *
   * Una scheda contatto sono trecento caratteri: senza gruppi, chi la ricopia perde la riga al
   * terzo tentativo e chi la guarda non capisce nemmeno dove finisce.
   */
  fun grouped(text: String, group: Int = 5, perLine: Int = 8): String {
    val chunks = text.chunked(group)
    return chunks.chunked(perLine).joinToString("\n") { it.joinToString(" ") }
  }
}
