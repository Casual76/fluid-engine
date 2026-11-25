package dev.pampa.codex.crypto

/**
 * L'identificatore che una persona detta a voce o incolla in un messaggio: `CDX-7K3P-2M9Q`.
 *
 * Deriva dalla **chiave pubblica di firma**, non dall'account: e' la stessa su tutti i dispositivi
 * di una persona, non cambia quando l'account viene collegato, e non si puo' scegliere. Chi conosce
 * il Codex ID di qualcuno puo' chiedere di essere aggiunto, ma non puo' fingersi lui, perche' la
 * scheda che il server restituisce e' firmata dalla chiave da cui l'ID e' nato.
 *
 * L'alfabeto e' Crockford base32: niente I, L, O, U, quindi niente "uno o elle?" al telefono e
 * nessuna parola sgradevole per caso.
 */
object CodexId {

  private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private const val BODY_LENGTH = 8
  const val PREFIX = "CDX"

  fun fromSigningKey(ed25519Public: ByteArray): String {
    require(ed25519Public.size == IdentityKeys.KEY_SIZE) { "chiave di firma non valida" }
    val digest = Digests.sha256("codex-id-v1".toByteArray(Charsets.US_ASCII) + ed25519Public)
    return format(encode(digest, BODY_LENGTH))
  }

  /** `CDX-XXXX-XXXX` da otto caratteri, con i trattini dove aiutano a leggere. */
  private fun format(body: String): String = "$PREFIX-${body.substring(0, 4)}-${body.substring(4)}"

  /** Toglie trattini, spazi e maiuscole/minuscole, e riporta le lettere confondibili. */
  fun normalize(input: String): String? {
    val cleaned = StringBuilder()
    for (character in input.trim().uppercase()) {
      when (character) {
        '-', ' ', '–' -> Unit
        'I', 'L' -> cleaned.append('1')
        'O' -> cleaned.append('0')
        'U' -> cleaned.append('V')
        else -> {
          if (character !in ALPHABET && !(cleaned.isEmpty() && PREFIX.contains(character))) return null
          cleaned.append(character)
        }
      }
    }
    val text = cleaned.toString().removePrefix(PREFIX)
    if (text.length != BODY_LENGTH) return null
    if (text.any { it !in ALPHABET }) return null
    return format(text)
  }

  fun isValid(input: String): Boolean = normalize(input) != null

  private fun encode(data: ByteArray, characters: Int): String {
    val result = StringBuilder(characters)
    var buffer = 0L
    var bits = 0
    var index = 0
    while (result.length < characters) {
      if (bits < 5) {
        if (index >= data.size) throw IllegalArgumentException("dati insufficienti")
        buffer = (buffer shl 8) or (data[index++].toLong() and 0xFF)
        bits += 8
      }
      bits -= 5
      result.append(ALPHABET[((buffer ushr bits) and 0x1F).toInt()])
    }
    return result.toString()
  }
}
