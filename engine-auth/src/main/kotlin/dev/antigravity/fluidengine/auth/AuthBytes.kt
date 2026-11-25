package dev.antigravity.fluidengine.auth

import java.security.MessageDigest
import java.util.Base64

/** Base64url senza «=», come in tutto WebAuthn e nel contratto del server (`api.md` § 1). */
internal object Base64Url {
  private val encoder = Base64.getUrlEncoder().withoutPadding()
  private val decoder = Base64.getUrlDecoder()
  private val shape = Regex("^[A-Za-z0-9_-]*$")

  fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

  /** Accetta anche l'alfabeto con `+` e `/` e il riempimento: qualche gestore li usa nei campi che non firma. */
  fun decode(text: String): ByteArray {
    val clean = text.trimEnd('=').replace('+', '-').replace('/', '_')
    if (!shape.matches(clean)) throw FluidWebAuthnJsonException("base64url non valido")
    return try {
      decoder.decode(clean)
    } catch (e: IllegalArgumentException) {
      throw FluidWebAuthnJsonException("base64url non valido", e)
    }
  }
}

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/**
 * Un lettore CBOR minimo (RFC 8949), quanto basta per l'`attestationObject` di WebAuthn: mappe, array, byte, testo,
 * interi, semplici. Non serve scrivere CBOR, né leggere i tag o i numeri con la virgola, che in un
 * `attestationObject` di tipo «none» non ci sono. Lunghezze indefinite rifiutate: WebAuthn vuole il CBOR canonico.
 */
internal class CborReader(private val data: ByteArray) {
  private var pos = 0

  fun readItem(): Any? {
    val initial = next()
    val major = initial ushr 5
    val info = initial and 0x1f
    return when (major) {
      0 -> length(info)
      1 -> -1 - length(info)
      2 -> bytes(length(info))
      3 -> String(bytes(length(info)), Charsets.UTF_8)
      4 -> List(count(length(info))) { readItem() }
      5 -> {
        val n = count(length(info))
        val map = LinkedHashMap<Any?, Any?>(n)
        repeat(n) { map[readItem()] = readItem() }
        map
      }
      6 -> throw FluidWebAuthnJsonException("CBOR: tag non gestito")
      else -> when (info) {
        20 -> false
        21 -> true
        22, 23 -> null
        else -> throw FluidWebAuthnJsonException("CBOR: valore semplice non gestito")
      }
    }
  }

  private fun next(): Int {
    if (pos >= data.size) throw FluidWebAuthnJsonException("CBOR troncato")
    return data[pos++].toInt() and 0xff
  }

  private fun length(info: Int): Long = when {
    info < 24 -> info.toLong()
    info == 24 -> next().toLong()
    info == 25 -> (next().toLong() shl 8) or next().toLong()
    info == 26 -> (0 until 4).fold(0L) { acc, _ -> (acc shl 8) or next().toLong() }
    info == 27 -> (0 until 8).fold(0L) { acc, _ -> (acc shl 8) or next().toLong() }
    else -> throw FluidWebAuthnJsonException("CBOR: lunghezza indefinita")
  }

  private fun count(n: Long): Int {
    if (n < 0 || n > data.size - pos) throw FluidWebAuthnJsonException("CBOR: lunghezza fuori misura")
    return n.toInt()
  }

  private fun bytes(n: Long): ByteArray {
    val size = count(n)
    val out = data.copyOfRange(pos, pos + size)
    pos += size
    return out
  }
}
