package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R2 · La chiave X25519 debole (review `muse-account`, minore): Bouncy Castle rifiuta già l'accordo con una pubblica di
 * ordine piccolo, quindi non c'è un segreto noto; ma l'eccezione usciva grezza (IllegalStateException) e faceva
 * cadere le parole, la vicinanza e gli invii. Adesso è un [PeopleDetected], e la scheda con una tale chiave non passa.
 */
class WeakX25519R2Test {

  /** Le pubbliche di ordine piccolo note (l'elenco di libsodium): zero, uno, i due d'ordine 8, p-1, p, p+1. */
  private val weak: List<ByteArray> = listOf(
    "0000000000000000000000000000000000000000000000000000000000000000",
    "0100000000000000000000000000000000000000000000000000000000000000",
    "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800",
    "5f9c95bca3508c24b1d0b1559c83ef5b04445cc4581c8e86d8224eddd09f1157",
    "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
    "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
    "eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
  ).map { hex -> ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }

  private fun detected(block: () -> Any?): String = try {
    block()
    fail("doveva essere rifiutato")
    ""
  } catch (e: PeopleDetected) {
    e.what
  }

  @Test
  fun `l'accordo e HPKE verso una pubblica di ordine piccolo - un rifiuto pulito`() {
    val mine = X25519Key.random()
    for (w in weak) {
      assertEquals("chiave di cifratura debole", detected { mine.agree(w) })
      assertEquals("chiave di cifratura debole", detected { PeopleHpke.seal(w, "info".toByteArray(), ByteArray(0), PampaCrypto.random(16)) })
    }
    // Una pubblica vera passa.
    assertEquals(32, mine.agree(X25519Key.random().publicKey).size)
  }

  @Test
  fun `una scheda con la chiave di cifratura di ordine piccolo non passa la verifica`() {
    val root = Ed25519Key.random()
    val sign = Ed25519Key.random()
    for (enc in weak) {
      val work = WorkPublic(1, enc, sign.publicKey, root.sign(WorkPublic.signatureInput(1, enc, sign.publicKey)))
      assertTrue("le chiavi di lavoro sono firmate davvero dalla radice", work.signedBy(root.publicKey))
      assertEquals("chiave di cifratura debole", detected { PersonCard.sign(root, "Marco", ByteArray(16) { 7 }, work, 1_764_000_000_000) })
    }
    // Con una chiave vera la stessa scheda passa.
    val good = X25519Key.random().publicKey
    val work = WorkPublic(1, good, sign.publicKey, root.sign(WorkPublic.signatureInput(1, good, sign.publicKey)))
    assertEquals("Marco", PersonCard.sign(root, "Marco", ByteArray(16) { 7 }, work, 1_764_000_000_000).name)
  }

  @Test
  fun `la vicinanza con un'effimera di ordine piccolo - il saluto si rifiuta, non cade la runa`() {
    val marco = TestPerson("Marco")
    for (w in weak) {
      val hello = NearbyHandshake.hello(marco.card, PampaCrypto.random(NearbyHandshake.CHALLENGE_BYTES), w)
      val r = runCatching { NearbyHandshake.readHello(hello) }
      assertTrue("era ${r.exceptionOrNull() ?: r.getOrNull()}", r.exceptionOrNull() is NearbyHandshake.Refused)
      assertEquals("saluto con una chiave debole", r.exceptionOrNull()!!.message)
    }
  }
}
