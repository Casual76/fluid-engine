package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import dev.antigravity.fluidengine.vault.VaultCipherOp
import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultKeyAuth
import dev.antigravity.fluidengine.vault.VaultKeystore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La custodia sopra la cassaforte dell'engine (`engine-vault`, F8.23), col Keystore finto di `engine-vault`
 * ([MemoryVaultKeystore]): le ricette delle due classi, il telefono bloccato, la chiave persa, il file toccato.
 * La strada vera (il Keystore di Android) l'ha provata il tablet il 25/11 (`f10-4\prova-tablet.txt`).
 */
class KeystoreCustodyVaultTest {
  @get:Rule val tmp = TemporaryFolder()

  private var locked = false
  private val keystore = MemoryVaultKeystore()
  private fun vault(sdk: Int = 35, config: KeystoreCustodyVault.Config = KeystoreCustodyVault.Config(), ks: VaultKeystore = keystore) =
    KeystoreCustodyVault(ks, isDeviceLocked = { locked }, config = config, sdkInt = sdk)

  private val aad = "custodia.bin".toByteArray()

  @Test
  fun `le ricette - madre in StrongBox e da sbloccato su Android 15, consegna senza vincoli`() {
    val v = vault(sdk = 35)
    val mother = v.specFor(KeyClass.MOTHER)
    val delivery = v.specFor(KeyClass.DELIVERY)
    assertEquals(VaultKeyAuth.None, mother.auth)
    assertTrue(mother.strongBox)
    assertTrue(mother.unlockedDeviceRequired)
    assertEquals(VaultKeyAuth.None, delivery.auth)
    assertFalse(delivery.strongBox)
    assertFalse(delivery.unlockedDeviceRequired)
    // Sotto Android 15 la madre è una chiave normale: il blocco lo controlla il codice.
    assertFalse(vault(sdk = 34).specFor(KeyClass.MOTHER).unlockedDeviceRequired)
    val none = vault(config = KeystoreCustodyVault.Config(strongBox = KeystoreCustodyVault.StrongBoxUse.NONE))
    assertFalse(none.specFor(KeyClass.MOTHER).strongBox)
  }

  @Test
  fun `chiude e riapre le due classi, con le chiavi nella cassaforte`() {
    keystore.strongBoxAvailable = true
    val v = vault()
    for (k in KeyClass.entries) {
      val data = PampaCrypto.random(300)
      val sealed = v.seal(k, data, aad)
      assertEquals(12 + 300 + 16, sealed.size)
      assertArrayEquals(data, v.open(k, sealed, aad))
    }
    assertEquals(setOf("dev.pampa.account.custody.delivery", "dev.pampa.account.custody.mother"), keystore.aliases)
    assertEquals(true, v.strongBoxBacked[KeyClass.MOTHER])
    assertEquals(false, v.strongBoxBacked[KeyClass.DELIVERY])
  }

  @Test
  fun `il file toccato, troncato, con un altro aad o dell'altra classe non si apre`() {
    val v = vault()
    val sealed = v.seal(KeyClass.MOTHER, PampaCrypto.random(64), aad)
    val touched = sealed.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
    for (bad in listOf<() -> Unit>(
      { v.open(KeyClass.MOTHER, touched, aad) },
      { v.open(KeyClass.MOTHER, sealed.copyOf(20), aad) },
      { v.open(KeyClass.MOTHER, sealed, "lavoro.bin".toByteArray()) },
      { v.seal(KeyClass.DELIVERY, ByteArray(1), aad); v.open(KeyClass.DELIVERY, sealed, aad) },
    )) {
      assertTrue(runCatching { bad() }.exceptionOrNull() is PampaCryptoException)
    }
  }

  @Test
  fun `a telefono bloccato la madre resta chiusa, la consegna no`() {
    val v = vault()
    val mother = v.seal(KeyClass.MOTHER, ByteArray(8), aad)
    val delivery = v.seal(KeyClass.DELIVERY, ByteArray(8), aad)
    locked = true
    assertTrue(runCatching { v.open(KeyClass.MOTHER, mother, aad) }.exceptionOrNull() is CustodyLockedException)
    assertTrue(runCatching { v.seal(KeyClass.MOTHER, ByteArray(8), aad) }.exceptionOrNull() is CustodyLockedException)
    assertArrayEquals(ByteArray(8), v.open(KeyClass.DELIVERY, delivery, aad))
    locked = false
    assertArrayEquals(ByteArray(8), v.open(KeyClass.MOTHER, mother, aad))
  }

  @Test
  fun `il Keystore che chiede l'autenticazione (Android 15 a schermo bloccato) diventa telefono bloccato`() {
    val refusing = object : VaultKeystore by keystore {
      override fun aesDecrypt(alias: String, iv: ByteArray): VaultCipherOp = throw VaultException.NeedsAuthentication()
    }
    val v = vault(ks = refusing)
    val sealed = v.seal(KeyClass.MOTHER, ByteArray(8), aad)
    assertTrue(runCatching { v.open(KeyClass.MOTHER, sealed, aad) }.exceptionOrNull() is CustodyLockedException)
  }

  @Test
  fun `la chiave persa o invalidata diventa CustodyKeyLostException`() {
    val v = vault()
    val mother = v.seal(KeyClass.MOTHER, ByteArray(8), aad)
    val delivery = v.seal(KeyClass.DELIVERY, ByteArray(8), aad)
    keystore.wipe()
    for ((k, s) in listOf(KeyClass.MOTHER to mother, KeyClass.DELIVERY to delivery)) {
      val e = runCatching { v.open(k, s, aad) }.exceptionOrNull()
      assertTrue("$k: $e", e is CustodyKeyLostException && e.keyClass == k)
    }
    // Il blocco schermo tolto, nel modello di engine-vault: cadono le chiavi con autenticazione, le nostre
    // (VaultKeyAuth.None) restano. Su Android 9–14 il Keystore vero cancellava anche quelle con unlockedDeviceRequired:
    // per questo la madre lo chiede solo da Android 15 (rapporto § 7).
    val again = vault()
    val s2 = again.seal(KeyClass.MOTHER, ByteArray(8), aad)
    keystore.removeScreenLock()
    assertArrayEquals(ByteArray(8), again.open(KeyClass.MOTHER, s2, aad))
  }

  @Test
  fun `destroy toglie solo le chiavi della custodia`() {
    val other = KeystoreCustodyVault(keystore, { false }, KeystoreCustodyVault.Config(aliasPrefix = "altra"), sdkInt = 35)
    other.seal(KeyClass.DELIVERY, ByteArray(1), aad)
    val v = vault()
    v.seal(KeyClass.MOTHER, ByteArray(1), aad)
    v.seal(KeyClass.DELIVERY, ByteArray(1), aad)
    v.destroy()
    assertEquals(setOf("altra.delivery"), keystore.aliases)
  }

  @Test
  fun `la custodia intera passa dalla cassaforte e torna uguale`() {
    val server = FakeKeysServer()
    val code = RecoveryCode.generate()
    val start = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(code)))
    val ring = CustodyKeyring(vault(), tmp.root)
    ring.saveCustody(start.custody, listOf(WorkKeyGrant("dev.pampa.pampai", setOf(AppKeyPurpose.APP))))
    val back = ring.loadCustody()
    assertNotNull(back)
    assertArrayEquals(start.custody.cd(1), back!!.cd(1))
  }
}
