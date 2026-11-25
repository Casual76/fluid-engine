package dev.antigravity.fluidengine.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest

/** I flag e l'AAGUID, letti da byte scritti a mano (WebAuthn § 6.1). */
class FluidAuthenticatorDataTest {

  private fun rpHash(rpId: String) = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())

  private fun authData(flags: Int, counter: Int = 0, aaguid: ByteArray? = null, credentialId: ByteArray = ByteArray(0)): ByteArray {
    val head = rpHash("prova.pampa-apps.com") + byteArrayOf(flags.toByte()) +
      byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte())
    if (aaguid == null) return head
    return head + aaguid + byteArrayOf((credentialId.size ushr 8).toByte(), credentialId.size.toByte()) + credentialId
  }

  @Test
  fun iFlagUnoPerUno() {
    val cases = mapOf(
      0x01 to "UP", 0x04 to "UV", 0x08 to "BE", 0x10 to "BS",
    )
    for ((bit, name) in cases) {
      val data = FluidAuthenticatorData.parse(authData(bit))
      assertEquals(name, bit == 0x01, data.userPresent)
      assertEquals(name, bit == 0x04, data.userVerified)
      assertEquals(name, bit == 0x08, data.backupEligible)
      assertEquals(name, bit == 0x10, data.backedUp)
      assertFalse(data.hasAttestedCredential)
    }
    val all = FluidAuthenticatorData.parse(authData(0x1d, counter = 258))
    assertTrue(all.userPresent && all.userVerified && all.backupEligible && all.backedUp)
    assertEquals(258L, all.signCount)
    assertTrue(all.isFor("prova.pampa-apps.com"))
    assertFalse(all.isFor("pampa-apps.com"))
    assertNull(all.aaguid)
  }

  @Test
  fun lAaguidDelGestore() {
    // Samsung Pass scrive «SAMSUNG» in ASCII; con attestation «none» un gestore può anche lasciarlo a zero.
    val samsung = byteArrayOf(0x53, 0x41, 0x4d, 0x53, 0x55, 0x4e, 0x47, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val data = FluidAuthenticatorData.parse(authData(0x45 or 0x18, aaguid = samsung, credentialId = byteArrayOf(1, 2, 3)))
    assertEquals("53414d53-554e-4700-0000-000000000000", data.aaguid)
    assertFalse(data.aaguidIsZero)
    assertEquals("AQID", data.credentialId)
    val zero = FluidAuthenticatorData.parse(authData(0x45, aaguid = ByteArray(16)))
    assertTrue(zero.aaguidIsZero)
  }

  @Test
  fun byteTroncatiSonoUnErrore() {
    val bad = listOf(
      ByteArray(10),
      authData(0x45).copyOf(40),
      authData(0x45, aaguid = ByteArray(16), credentialId = ByteArray(8)).let { it.copyOf(it.size - 3) },
    )
    for (bytes in bad) {
      try {
        FluidAuthenticatorData.parse(bytes)
        fail("doveva rifiutare ${bytes.size} byte")
      } catch (e: FluidWebAuthnJsonException) {
        // giusto
      }
    }
  }

  @Test
  fun unAttestationObjectSenzaAuthData() {
    // CBOR: {"fmt": "none"}
    val cbor = byteArrayOf(0xa1.toByte(), 0x63, 'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), 0x64, 'n'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte(), 'e'.code.toByte())
    try {
      FluidAuthenticatorData.fromAttestationObject(cbor)
      fail("doveva rifiutare")
    } catch (e: FluidWebAuthnJsonException) {
      assertEquals("attestationObject senza authData", e.message)
    }
  }
}
