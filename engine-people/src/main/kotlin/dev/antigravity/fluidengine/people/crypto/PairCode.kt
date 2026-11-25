package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto

/**
 * La chiave di coppia fra due persone, e da lei la gemma e le quattro parole della verifica a distanza
 * (`d\server-e-chiavi.md` § 4; scelta D5; critica S5; contratto `people.formulas.pairKey`, `gem`, `words`).
 *
 * ```
 * chiave di coppia = HKDF(ikm = X25519(mia enc, sua enc), sale = SHA-256(radice minore ‖ radice maggiore), "pampa/v1/coppia")
 * gemma            = HKDF(chiave di coppia, sale, "pampa/v1/gemma", 16 byte)
 * parole           = i primi 44 bit di HKDF(chiave di coppia, sale, "pampa/v1/parole", 6 byte): 4 indici da 11 bit
 * ```
 *
 * **Il sale contro il «compleanno»** (rapporto di F10.5, § 9; la scelta è nel rapporto di F10.6). Nel contratto il
 * sale è vuoto. Ma un server che ha già pagato le due ricerche del codice (circa 2⁴⁰ l'una) può cambiare liberamente le
 * chiavi di cifratura delle due schede false, e cercare due parole uguali con un attacco del compleanno: circa 2²²
 * chiavi per lato, secondi o minuti. Per chiuderlo c'è l'**impegno**, come lo SAS di ZRTP: chi comincia (chi manda la
 * richiesta, o chi rinnova le chiavi) manda prima solo [commit] di un nonce suo, legato alle due chiavi di
 * cifratura; l'altro risponde col suo nonce in chiaro; poi chi ha cominciato rivela il suo. Il [salt] delle parole
 * nasce dai due nonce. Così il server deve scegliere le sue chiavi prima di sapere il nonce di chi è onesto, e ogni
 * tentativo riesce una volta su 2⁴⁴: niente più compleanno. Senza i nonce ([EMPTY_SALT]) le formule sono quelle del
 * contratto, e servono solo alle prove d'interoperabilità.
 *
 * L'ordine delle radici è quello del telefono finto del server: si confrontano **le stringhe base64url**, non i byte.
 * Il contratto dice «min/max» senza dire come: è scritto nel rapporto.
 */
object PairCode {
  val EMPTY_SALT: ByteArray = ByteArray(0)
  const val NONCE_BYTES = 16

  fun pairKey(myEnc: X25519Key, theirEnc: ByteArray, myRoot: ByteArray, theirRoot: ByteArray): ByteArray {
    val dh = myEnc.agree(theirEnc)
    try {
      val a = PampaBase64.encode(myRoot)
      val b = PampaBase64.encode(theirRoot)
      val (lo, hi) = if (a < b) myRoot to theirRoot else theirRoot to myRoot
      return PampaCrypto.hkdf(dh, PampaCrypto.sha256(lo, hi), PeopleLabels.PAIR)
    } finally {
      dh.fill(0)
    }
  }

  /** L'impegno di chi comincia: `SHA-256("pampa/v1/parole-impegno|" ‖ nonce ‖ sua radice ‖ sua enc ‖ enc dell'altro)`. */
  fun commit(nonce: ByteArray, committerRoot: ByteArray, committerEnc: ByteArray, peerEnc: ByteArray): ByteArray {
    require(nonce.size == NONCE_BYTES) { "il nonce delle parole è di $NONCE_BYTES byte" }
    return PampaCrypto.sha256(PeopleLabels.WORDS_COMMIT.toByteArray(Charsets.UTF_8), nonce, committerRoot, committerEnc, peerEnc)
  }

  /** Il nonce rivelato torna con l'impegno ricevuto prima? */
  fun opens(commitment: ByteArray, nonce: ByteArray, committerRoot: ByteArray, committerEnc: ByteArray, peerEnc: ByteArray): Boolean =
    nonce.size == NONCE_BYTES && PampaCrypto.equal(commitment, commit(nonce, committerRoot, committerEnc, peerEnc))

  /** Il sale delle parole e della gemma, dai due nonce: prima quello di chi ha cominciato. */
  fun salt(committerNonce: ByteArray, responderNonce: ByteArray): ByteArray =
    PampaCrypto.sha256(PeopleLabels.WORDS_SALT.toByteArray(Charsets.UTF_8), committerNonce, responderNonce)

  /** Il seme della gemma, 16 byte, uguale sui due telefoni. */
  fun gem(pairKey: ByteArray, salt: ByteArray = EMPTY_SALT): ByteArray = PampaCrypto.hkdf(pairKey, salt, PeopleLabels.GEM, 16)

  /** I quattro indici da 11 bit (44 bit) nell'elenco di 2.048 parole ([ItalianWords]). */
  fun words(pairKey: ByteArray, salt: ByteArray = EMPTY_SALT): List<Int> {
    val bytes = PampaCrypto.hkdf(pairKey, salt, PeopleLabels.WORDS, 6)
    var v = 0L
    for (b in bytes) v = (v shl 8) or (b.toLong() and 0xFF)
    v = v ushr 4
    return listOf(3, 2, 1, 0).map { i -> ((v ushr (i * 11)) and 2047).toInt() }
  }

  /** Le quattro parole da dirsi al telefono. */
  fun wordsText(pairKey: ByteArray, salt: ByteArray = EMPTY_SALT): List<String> = words(pairKey, salt).map(ItalianWords::word)
}
