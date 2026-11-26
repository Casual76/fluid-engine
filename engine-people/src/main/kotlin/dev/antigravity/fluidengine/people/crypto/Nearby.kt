package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto

/**
 * Il generatore pseudocasuale dei sigilli di Codex (`SeededRandom`, SplitMix64): quaranta righe di aritmetica definita
 * fino all'ultimo bit, così due telefoni disegnano **la stessa** runa e la stessa gemma senza dipendere da una libreria
 * che cambia fra versioni.
 */
class SeededRandom(seed: Long) {
  private var state: Long = seed

  fun nextLong(): Long {
    state += -0x61c8864680b583ebL
    var z = state
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
  }

  /** In `[0, bound)`. */
  fun nextInt(bound: Int): Int {
    require(bound > 0) { "limite non positivo: $bound" }
    return ((nextLong() ushr 33) % bound).toInt()
  }

  /** In `[0, 1)`, con 24 bit. */
  fun nextFloat(): Float = ((nextLong() ushr 40) / 16_777_216f)
}

/**
 * La runa della vicinanza: i glifi del blocco Runic di Unicode (`RuneAlphabet` di Codex, i 75 segni da U+16A0 a
 * U+16EA, disegnati dal font incluso nell'app). Quattro rune sono circa 25 bit, come il confronto numerico del
 * Bluetooth: chi si mette in mezzo nella stanza ha un tentativo solo, e una volta su 2²⁵ indovina.
 */
object PampaRune {
  val POOL: List<Char> = ('ᚠ'..'ᛪ').toList()
  const val LENGTH = 4

  /** [length] rune dal seme [seed] (almeno 8 byte). */
  fun of(seed: ByteArray, length: Int = LENGTH): String {
    require(seed.size >= 8) { "il seme della runa è di almeno 8 byte" }
    var s = 0L
    for (i in 0 until 8) s = (s shl 8) or (seed[i].toLong() and 0xFF)
    val random = SeededRandom(s)
    return buildString { repeat(length) { append(POOL[random.nextInt(POOL.size)]) } }
  }
}

/**
 * Come due telefoni vicini si scambiano le schede **senza passare dal server** e si riconoscono (`NearbyHandshake` di
 * Codex; `progetto.md` § 3.6, «Vicino»; critica S5): il NameDrop di Pampa.
 *
 * 1. **L'annuncio non dice chi sei**: un nome casuale per questa sola sessione ([advertisedName]), che non si collega a
 *    niente. Chi è nel raggio sente solo rumore.
 * 2. **Il saluto** ([hello]), sul canale diretto: la scheda intera, una sfida da 16 byte e una chiave X25519 effimera.
 * 3. **La risposta** ([answer]): la firma della chiave di lavoro su sfida, codice e le due effimere. Chi non ha le
 *    chiavi della scheda non passa, e la firma non vale in un altro canale.
 * 4. **La runa** ([rune]): dall'accordo fra le due effimere e dalle due schede. I due la vedono sui due schermi e
 *    confermano: chi si mettesse in mezzo con due canali vedrebbe due rune diverse.
 *
 * Dopo la conferma il contatto è **verificato subito**: la chiave intera è arrivata senza intermediari.
 */
object NearbyHandshake {
  private val MAGIC = "PMN1".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1
  const val CHALLENGE_BYTES = 16

  class Refused(message: String) : Exception(message)

  class Hello(val card: PersonCard, val challenge: ByteArray, val ephemeral: ByteArray)

  /** Il nome con cui ci si annuncia in questa sessione: 8 byte casuali in Crockford minuscolo, mai la persona. */
  fun advertisedName(): String = Crockford32.encode(PampaCrypto.random(8)).lowercase()

  fun newChallenge(): ByteArray = PampaCrypto.random(CHALLENGE_BYTES)

  fun hello(card: PersonCard, challenge: ByteArray, ephemeral: ByteArray): ByteArray {
    require(challenge.size == CHALLENGE_BYTES && ephemeral.size == 32)
    return ByteWriter(1024).bytes(MAGIC).u8(VERSION).string(card.raw).string(card.sig).blob(challenge).blob(ephemeral).build()
  }

  /** Legge un saluto e ne controlla la scheda; [Refused] se non è un saluto di Pampa o la scheda non torna. */
  fun readHello(bytes: ByteArray): Hello {
    val reader = ByteReader(bytes)
    try {
      reader.expectMagic(MAGIC)
      if (reader.u8() != VERSION) throw Refused("saluto di un'altra versione")
      val raw = reader.string()
      val sig = reader.string()
      val card = try {
        PersonCard.verify(raw, sig)
      } catch (e: PeopleDetected) {
        throw Refused(e.what)
      }
      val challenge = reader.blob()
      val eph = reader.blob()
      if (challenge.size != CHALLENGE_BYTES || eph.size != 32) throw Refused("saluto di misura sbagliata")
      // R2 · Un'effimera di ordine piccolo farebbe cadere la runa con un'eccezione grezza: un rifiuto pulito.
      if (X25519Key.isWeak(eph)) throw Refused("saluto con una chiave debole")
      return Hello(card, challenge, eph)
    } catch (_: ByteReader.Malformed) {
      throw Refused("non è un saluto di Pampa")
    }
  }

  /** La risposta alla sfida [peerChallenge]: firmo chi sono e le due effimere, con la mia chiave di lavoro. */
  fun answer(peerChallenge: ByteArray, myCard: PersonCard, myEphemeral: ByteArray, peerEphemeral: ByteArray, work: WorkKeys): ByteArray =
    work.sign.sign(transcript(peerChallenge, myCard.code, myEphemeral, peerEphemeral))

  /** Controlla la risposta di [peer] alla mia sfida [myChallenge]. */
  fun verify(myChallenge: ByteArray, peer: PersonCard, peerEphemeral: ByteArray, myEphemeral: ByteArray, signature: ByteArray): Boolean =
    Ed25519Key.verify(peer.work.sign, transcript(myChallenge, peer.code, peerEphemeral, myEphemeral), signature)

  /** La runa che i due telefoni mostrano, uguale da tutte e due le parti. */
  fun rune(myEphemeral: X25519Key, peerEphemeral: ByteArray, myCard: PersonCard, peerCard: PersonCard): String {
    val dh = myEphemeral.agree(peerEphemeral)
    val (lo, hi) = if (myCard.raw < peerCard.raw) myCard to peerCard else peerCard to myCard
    val salt = PampaCrypto.sha256(lo.raw.toByteArray(Charsets.UTF_8), hi.raw.toByteArray(Charsets.UTF_8))
    return PampaRune.of(PampaCrypto.hkdf(dh, salt, PeopleLabels.NEARBY_RUNE, 8)).also { dh.fill(0) }
  }

  private fun transcript(challenge: ByteArray, signerCode: String, signerEphemeral: ByteArray, otherEphemeral: ByteArray): ByteArray =
    ByteWriter(128).bytes(MAGIC).u8(VERSION).string(PeopleLabels.NEARBY_AUTH).string(signerCode).blob(challenge).blob(signerEphemeral).blob(otherEphemeral).build()
}

/**
 * Il QR personale (critica S5): la **scheda intera**, quindi la chiave intera e niente da indovinare, più un gettone
 * d'invito monouso (24 ore, registrato nella casella) e un **segreto** che non va mai al server. Chi inquadra manda
 * una richiesta col gettone e, dentro la CDXB, la prova di aver visto il QR ([proof]): così è verificato **per tutti e
 * due**, anche se il server conosce il gettone (che viaggia in chiaro accanto alla busta per saltare il tetto).
 *
 * `PMQ1:<scheda>.<firma>.<gettone>.<segreto>`, tutto in base64url: circa 560 caratteri, un QR da leggere a 30 cm.
 */
class PersonQr(val card: PersonCard, val invite: String, val secret: ByteArray) {
  init {
    require(secret.size == SECRET_BYTES) { "il segreto del QR è di $SECRET_BYTES byte" }
  }

  fun encode(): String = "$PREFIX${card.raw}.${card.sig}.$invite.${PampaBase64.encode(secret)}"

  /** La prova di chi ha inquadrato: `HMAC(HKDF(segreto, "pampa/v1/qr-prova"), scheda di chi chiede)`. */
  fun proof(requesterCardRaw: String): String =
    PampaBase64.encode(PampaCrypto.hmacSha256(PampaCrypto.hkdf(secret, PampaCrypto.EMPTY, QR_PROOF), requesterCardRaw.toByteArray(Charsets.UTF_8)))

  fun checkProof(requesterCardRaw: String, proof: String): Boolean =
    runCatching { PampaCrypto.equal(PampaBase64.decode(proof), PampaBase64.decode(proof(requesterCardRaw))) }.getOrDefault(false)

  companion object {
    const val PREFIX = "PMQ1:"
    const val SECRET_BYTES = 16
    const val QR_PROOF = "pampa/v1/qr-prova"

    fun create(card: PersonCard, invite: String): PersonQr = PersonQr(card, invite, PampaCrypto.random(SECRET_BYTES))

    /** Legge un QR; `null` se non è un QR personale di Pampa o la scheda non torna. */
    fun decode(text: String): PersonQr? {
      val t = text.trim()
      if (!t.startsWith(PREFIX, ignoreCase = true)) return null
      val parts = t.substring(PREFIX.length).split('.')
      if (parts.size != 4) return null
      return runCatching { PersonQr(PersonCard.verify(parts[0], parts[1]), parts[2], PampaBase64.decode(parts[3])) }.getOrNull()
    }

    fun looksLikePersonQr(text: String): Boolean = text.trim().startsWith(PREFIX, ignoreCase = true)
  }
}
