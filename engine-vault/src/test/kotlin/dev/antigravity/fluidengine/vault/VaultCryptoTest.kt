package dev.antigravity.fluidengine.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultCryptoTest {
  private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

  @Test fun `HKDF-SHA256 torna coi vettori della RFC 5869`() {
    // Caso 1 dell'RFC 5869, appendice A.1.
    val okm1 = VaultCrypto.hkdf(
      ikm = ByteArray(22) { 0x0b },
      salt = hex("000102030405060708090a0b0c"),
      info = hex("f0f1f2f3f4f5f6f7f8f9"),
      length = 42,
    )
    assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm1)
    // Caso 3, appendice A.3: sale e info vuoti.
    val okm3 = VaultCrypto.hkdf(ikm = ByteArray(22) { 0x0b }, salt = ByteArray(0), info = ByteArray(0), length = 42)
    assertArrayEquals(hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"), okm3)
  }

  @Test fun `AES-GCM si apre solo con la stessa chiave e gli stessi dati associati`() {
    val key = VaultCrypto.randomBytes(32)
    val sealed = VaultCrypto.seal(key, "segreto".toByteArray(), "conto:1".toByteArray())
    assertEquals(12 + 7 + 16, sealed.size)
    assertEquals("segreto", String(VaultCrypto.open(key, sealed, "conto:1".toByteArray())))
    expectCorrupt { VaultCrypto.open(key, sealed, "conto:2".toByteArray()) }
    expectCorrupt { VaultCrypto.open(VaultCrypto.randomBytes(32), sealed, "conto:1".toByteArray()) }
    val touched = sealed.copyOf().also { it[14] = (it[14].toInt() xor 1).toByte() }
    expectCorrupt { VaultCrypto.open(key, touched, "conto:1".toByteArray()) }
    expectCorrupt { VaultCrypto.open(key, sealed.copyOf(10), "conto:1".toByteArray()) }
  }

  @Test fun `due cifrature dello stesso testo non si assomigliano`() {
    val key = VaultCrypto.randomBytes(32)
    val a = VaultCrypto.seal(key, "uguale".toByteArray(), ByteArray(0))
    val b = VaultCrypto.seal(key, "uguale".toByteArray(), ByteArray(0))
    assertFalse(a.contentEquals(b))
  }

  @Test fun `PBKDF2 dà la stessa chiave per lo stesso PIN e una diversa per un altro`() {
    val salt = VaultCrypto.randomBytes(16)
    val a = VaultCrypto.pbkdf2("482913".toCharArray(), salt, 1_000)
    val b = VaultCrypto.pbkdf2("482913".toCharArray(), salt, 1_000)
    val c = VaultCrypto.pbkdf2("482914".toCharArray(), salt, 1_000)
    assertArrayEquals(a, b)
    assertFalse(a.contentEquals(c))
    assertTrue(VaultCrypto.constantTimeEquals(a, b))
    assertFalse(VaultCrypto.constantTimeEquals(a, c))
  }

  @Test fun `un record troncato è un dato non valido, non un'eccezione qualsiasi`() {
    val bytes = VaultRecordWriter().int(7).text("ciao").bytes(byteArrayOf(1, 2, 3)).toByteArray()
    val reader = VaultRecordReader(bytes)
    assertEquals(7, reader.int())
    assertEquals("ciao", reader.text())
    assertArrayEquals(byteArrayOf(1, 2, 3), reader.bytes())
    assertTrue(reader.atEnd())
    expectCorrupt { VaultRecordReader(bytes.copyOf(bytes.size - 2)).apply { int(); text(); bytes() } }
  }

  private fun expectCorrupt(block: () -> Unit) {
    try {
      block()
      fail("doveva essere un dato non valido")
    } catch (e: VaultException.Corrupt) {
      // giusto
    }
  }
}
