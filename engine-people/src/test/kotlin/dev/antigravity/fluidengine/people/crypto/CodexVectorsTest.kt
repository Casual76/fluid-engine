package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.hex
import dev.antigravity.fluidengine.people.toHex
import dev.pampa.codex.crypto.Base32
import dev.pampa.codex.crypto.IdentityKeys
import dev.pampa.codex.crypto.MediaCipher
import dev.pampa.codex.seal.RuneAlphabet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I vettori noti di Codex (`core/crypto`, `PrimitivesKatTest`) e la prova che la crittografia portata dia **gli stessi
 * byte** di quella di Codex dove il formato è tenuto così com'è. La copia di riferimento di Codex sta fra le prove
 * (`dev.pampa.codex`, vedi `LEGGIMI.md`); la repo di Codex non si tocca.
 */
class CodexVectorsTest {
  private val random = Random(0xF106)

  // --- I vettori degli standard, come in Codex --------------------------------------------------------------------

  @Test
  fun `HKDF-SHA256, RFC 5869 caso 1 (Codex)`() {
    val okm = PampaCrypto.hkdfRaw(hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"), hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
    assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.toHex())
  }

  @Test
  fun `AES-256-GCM, vettori 13 e 14 della specifica GCM (Codex)`() {
    val key = ByteArray(32)
    val nonce = ByteArray(12)
    assertEquals("530f8afbc74536b9a963b4f1c4cb738b", PampaCrypto.aesGcmSeal(key, nonce, ByteArray(0), ByteArray(0)).toHex())
    val sealed = PampaCrypto.aesGcmSeal(key, nonce, ByteArray(16), ByteArray(0))
    assertEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919", sealed.toHex())
    assertArrayEquals(ByteArray(16), PampaCrypto.aesGcmOpen(key, nonce, sealed, ByteArray(0)))
  }

  @Test
  fun `X25519, vettore di RFC 7748 (Codex)`() {
    val alice = X25519Key.fromPrivate(hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"))
    val bob = X25519Key.fromPrivate(hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"))
    assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", alice.publicKey.toHex())
    assertEquals("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", bob.publicKey.toHex())
    val shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
    assertEquals(shared, alice.agree(bob.publicKey).toHex())
    assertEquals(shared, bob.agree(alice.publicKey).toHex())
  }

  @Test
  fun `Ed25519, vettore 1 di RFC 8032 (Codex)`() {
    val key = Ed25519Key.fromPrivate(hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"))
    assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", key.publicKey.toHex())
    val sig = key.sign(ByteArray(0))
    assertEquals(
      "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
      sig.toHex(),
    )
    assertTrue(Ed25519Key.verify(key.publicKey, ByteArray(0), sig))
    assertFalse(Ed25519Key.verify(key.publicKey, "altro".toByteArray(), sig))
  }

  // --- Gli stessi byte della copia di riferimento -----------------------------------------------------------------

  @Test
  fun `le firme e gli accordi sono quelli di Codex`() {
    repeat(40) {
      val priv = random.nextBytes(32)
      val msg = random.nextBytes(random.nextInt(0, 300))
      assertArrayEquals(IdentityKeys.ed25519PublicKey(priv), Ed25519Key.fromPrivate(priv).publicKey)
      assertArrayEquals(IdentityKeys.sign(priv, msg), Ed25519Key.fromPrivate(priv).sign(msg))
      val other = random.nextBytes(32)
      assertArrayEquals(IdentityKeys.agree(priv, IdentityKeys.x25519PublicKey(other)), X25519Key.fromPrivate(priv).agree(X25519Key.fromPrivate(other).publicKey))
    }
  }

  @Test
  fun `il base32 di Crockford e i campi binari sono quelli di Codex`() {
    for (size in 0..80) {
      val data = random.nextBytes(size)
      val text = Crockford32.encode(data)
      assertEquals(Base32.encode(data), text)
      assertArrayEquals(Base32.decode(text), Crockford32.decode(text))
      assertArrayEquals(data, Crockford32.decode(text.lowercase().chunked(4).joinToString("-")))
    }
    assertArrayEquals(Base32.decode("0IL1-OUv"), Crockford32.decode("0IL1-OUv"))
    val ours = ByteWriter().u8(7).u16(65000).u32(0xFEEDBEEFL).i64(-5).string("città").blob(byteArrayOf(1, 2, 3)).build()
    val codex = dev.pampa.codex.crypto.ByteWriter().u8(7).u16(65000).u32(0xFEEDBEEFL).i64(-5).string("città").blob(byteArrayOf(1, 2, 3)).build()
    assertArrayEquals(codex, ours)
    val r = ByteReader(ours)
    assertEquals(7, r.u8()); assertEquals(65000, r.u16()); assertEquals(0xFEEDBEEFL, r.u32()); assertEquals(-5L, r.i64())
    assertEquals("città", r.string()); assertArrayEquals(byteArrayOf(1, 2, 3), r.blob())
  }

  @Test
  fun `CDXM si apre nei due sensi con Codex, con la stessa chiave e la stessa misura`() {
    val chatKey = random.nextBytes(32)
    assertArrayEquals(MediaCipher.key(chatKey, "foto-1"), Cdxm.keyFor(chatKey, "foto-1"))
    for (size in listOf(0, 1, 15, 16, Cdxm.CHUNK - 1, Cdxm.CHUNK, Cdxm.CHUNK + 1, 3 * Cdxm.CHUNK + 77)) {
      assertEquals(MediaCipher.encryptedSize(size.toLong()), Cdxm.encryptedSize(size.toLong()))
      val plain = random.nextBytes(size)
      val key = random.nextBytes(32)
      val byCodex = ByteArrayOutputStream().also { MediaCipher.encrypt(key, ByteArrayInputStream(plain), it, size.toLong()) }.toByteArray()
      val opened = ByteArrayOutputStream().also { Cdxm.decrypt(key, ByteArrayInputStream(byCodex), it) }.toByteArray()
      assertArrayEquals("misura $size, Codex → engine", plain, opened)
      val byEngine = ByteArrayOutputStream().also { Cdxm.encrypt(key, ByteArrayInputStream(plain), it, size.toLong()) }.toByteArray()
      val back = ByteArrayOutputStream().also { MediaCipher.decrypt(key, ByteArrayInputStream(byEngine), it) }.toByteArray()
      assertArrayEquals("misura $size, engine → Codex", plain, back)
      assertEquals(byCodex.size, byEngine.size)
      // L'intestazione è la stessa fino al nonce di base, che è casuale.
      assertArrayEquals(byCodex.copyOf(21), byEngine.copyOf(21))
    }
  }

  @Test
  fun `CDXM col nonce fissato da i byte che Codex sa aprire, e un blocco toccato non si apre`() {
    val key = random.nextBytes(32)
    val plain = random.nextBytes(2 * Cdxm.CHUNK + 5)
    val out = ByteArrayOutputStream().also { Cdxm.encryptWith(key, ByteArrayInputStream(plain), it, plain.size.toLong(), ByteArray(12) { 0x42 }) }.toByteArray()
    val again = ByteArrayOutputStream().also { Cdxm.encryptWith(key, ByteArrayInputStream(plain), it, plain.size.toLong(), ByteArray(12) { 0x42 }) }.toByteArray()
    assertArrayEquals(out, again)
    assertArrayEquals(plain, ByteArrayOutputStream().also { MediaCipher.decrypt(key, ByteArrayInputStream(out), it) }.toByteArray())
    val tampered = out.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
    assertTrue(runCatching { Cdxm.decrypt(key, ByteArrayInputStream(tampered), ByteArrayOutputStream()) }.exceptionOrNull() is Cdxm.Malformed)
    val truncated = out.copyOf(out.size - 10)
    assertTrue(runCatching { Cdxm.decrypt(key, ByteArrayInputStream(truncated), ByteArrayOutputStream()) }.exceptionOrNull() is Cdxm.Malformed)
  }

  @Test
  fun `il generatore dei sigilli e l'alfabeto delle rune sono quelli di Codex`() {
    for (seed in listOf(0L, 1L, -1L, 0x5EA1L, random.nextLong())) {
      val ours = SeededRandom(seed)
      val codex = dev.pampa.codex.seal.SeededRandom(seed)
      repeat(200) { assertEquals(codex.nextLong(), ours.nextLong()) }
      val a = SeededRandom(seed)
      val b = dev.pampa.codex.seal.SeededRandom(seed)
      repeat(200) { assertEquals(b.nextInt(75), a.nextInt(75)) }
    }
    assertEquals(RuneAlphabet.size, PampaRune.POOL.size)
    PampaRune.POOL.forEachIndexed { i, c -> assertEquals(RuneAlphabet.runeAt(i), c) }
    // La runa usa il generatore come Codex usa `placeholder`: stesso seme, stesse rune.
    val seed = random.nextBytes(8)
    var s = 0L
    for (x in seed) s = (s shl 8) or (x.toLong() and 0xFF)
    assertEquals(RuneAlphabet.placeholder(s, 4), PampaRune.of(seed))
  }
}
