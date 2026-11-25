package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto

/**
 * Il codice di recupero (`b\chiavi.md` § 3.1; critica S1). Lo genera **sempre lo Store**, mai il server: 128 bit
 * casuali, scritti in base32 (RFC 4648, maiuscole, senza riempimento) in gruppi da cinque, 26 caratteri:
 * `ABCDE-FGHIJ-KLMNO-PQRST-UVWXY-Z`. È lo stesso formato del telefono finto del server.
 *
 * Dal codice nascono due valori indipendenti (due `info` diversi di HKDF, RFC 5869 § 3.2):
 * - [auth], l'unico che va al server (`POST /v1/recovery`, `POST /v1/recovery/redeem`): serve solo a entrare;
 * - il seme della porta (`CustodyMath.doorSeed`), che resta sul telefono e apre i dati.
 *
 * Il codice non si stampa mai: [toString] lo copre, e le prove del banco controllano che nessuna richiesta lo contenga
 * in nessuna forma (testo, minuscole, senza trattini, base64, esadecimale).
 */
class RecoveryCode private constructor(private val bytes: ByteArray) {

  /** Come lo vede l'utente, una volta sola: 26 caratteri in gruppi da cinque. */
  val display: String = base32(bytes).chunked(5).joinToString("-")

  /** `auth = HKDF(codice, "pampa/v1/recovery-auth")`, in base64url: 43 caratteri, l'unica cosa che parte. */
  val auth: String get() = PampaBase64.encode(CustodyMath.recoveryAuth(bytes))

  /** Il segreto della porta del codice: resta nella custodia, per costruire e aprire la porta. */
  internal fun secret(): ByteArray = bytes.copyOf()

  override fun toString(): String = "RecoveryCode(••••)"
  override fun equals(other: Any?): Boolean = other is RecoveryCode && PampaCrypto.equal(bytes, other.bytes)
  override fun hashCode(): Int = bytes.contentHashCode()

  companion object {
    const val BYTES = 16
    const val LENGTH = 26
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Un codice nuovo, dal generatore del telefono. */
    fun generate(): RecoveryCode = RecoveryCode(PampaCrypto.random(BYTES))

    /** Per le prove e per chi ha già i 16 byte (il gestore delle password). */
    fun fromBytes(bytes: ByteArray): RecoveryCode {
      require(bytes.size == BYTES) { "il codice è di $BYTES byte" }
      return RecoveryCode(bytes.copyOf())
    }

    /**
     * Il codice scritto dall'utente, o `null` se non è un codice. Perdona quello che si sbaglia scrivendo a mano:
     * minuscole, spazi e trattini in qualunque punto, `0` per `O`, `1` per `I`, `8` per `B` (base32 non li usa).
     * Gli ultimi due bit del ventiseiesimo carattere devono essere zero: un errore lì non è un codice.
     */
    fun parse(text: String): RecoveryCode? {
      val clean = text.uppercase().filter { !it.isWhitespace() && it != '-' }
        .map { when (it) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; else -> it } }
        .joinToString("")
      if (clean.length != LENGTH || clean.any { it !in ALPHABET }) return null
      var buffer = 0L
      var bits = 0
      val out = ByteArray(BYTES)
      var n = 0
      for (c in clean) {
        buffer = (buffer shl 5) or ALPHABET.indexOf(c).toLong()
        bits += 5
        if (bits >= 8) {
          bits -= 8
          if (n < BYTES) out[n++] = ((buffer shr bits) and 0xff).toByte()
          buffer = buffer and ((1L shl bits) - 1)
        }
      }
      if (n != BYTES || buffer != 0L) return null
      return RecoveryCode(out)
    }

    private fun base32(bytes: ByteArray): String {
      val sb = StringBuilder()
      var buffer = 0
      var bits = 0
      for (b in bytes) {
        buffer = (buffer shl 8) or (b.toInt() and 0xff)
        bits += 8
        while (bits >= 5) {
          sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
          bits -= 5
        }
        buffer = buffer and ((1 shl bits) - 1)
      }
      if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
      return sb.toString()
    }
  }
}
