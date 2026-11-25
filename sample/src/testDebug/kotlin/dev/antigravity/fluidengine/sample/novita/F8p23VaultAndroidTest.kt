package dev.antigravity.fluidengine.sample.novita

import android.app.Application
import android.app.KeyguardManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.ai.keys.AiKeyStore
import dev.antigravity.fluidengine.ai.keys.KeystoreCipher
import dev.antigravity.fluidengine.ai.keys.SecretCipher
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.vault.AndroidVaultKeystore
import dev.antigravity.fluidengine.vault.FluidKeystoreKey
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.VaultAccess
import dev.antigravity.fluidengine.vault.VaultAuthMethod
import dev.antigravity.fluidengine.vault.VaultAuthRequest
import dev.antigravity.fluidengine.vault.VaultAuthResult
import dev.antigravity.fluidengine.vault.VaultAuthenticator
import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultInvalidation
import dev.antigravity.fluidengine.vault.VaultKeyAuth
import dev.antigravity.fluidengine.vault.VaultKeySpec
import dev.antigravity.fluidengine.vault.VaultUnlock
import dev.antigravity.fluidengine.vault.preferencesDataStore
import java.io.File
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Il `KeystoreCipher` di `engine-ai` com'era fino alla 3.x (commit 751e3cf), copiato qui riga per riga: è «la versione
 * di oggi» che ha scritto le chiavi nelle app. Le chiavi che scrive devono riaprirsi con quello nuovo (che passa da
 * `engine-vault`), e viceversa.
 */
private class KeystoreCipherOggi(private val alias: String = "fluidengine.ai") : SecretCipher {
  override fun encrypt(plain: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv
    val blob = ByteArray(iv.size + encrypted.size)
    iv.copyInto(blob)
    encrypted.copyInto(blob, iv.size)
    return PREFIX + Base64.getEncoder().encodeToString(blob)
  }

  override fun decrypt(blob: String): String? {
    if (!blob.startsWith(PREFIX)) return null
    return try {
      val bytes = Base64.getDecoder().decode(blob.removePrefix(PREFIX))
      if (bytes.size <= IV_BYTES) return null
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
      String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: AEADBadTagException) {
      null
    } catch (e: KeyPermanentlyInvalidatedException) {
      null
    } catch (e: UnrecoverableKeyException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  private fun key(): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val PREFIX = "v1:"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
  }
}

/** Il dialogo finto per il Keystore finto: «sì», e il Keystore vede l'impronta o il codice. */
private class AndroidFakeAuthenticator : VaultAuthenticator {
  val requests = mutableListOf<VaultAuthRequest>()
  override suspend fun authenticate(request: VaultAuthRequest): VaultAuthResult {
    requests += request
    if (request.method == VaultAuthMethod.Biometric) FakeAndroidKeyStore.approveNext() else FakeAndroidKeyStore.authenticate()
    return VaultAuthResult.Success
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F8p23VaultAndroidTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Before fun start() {
    FakeAndroidKeyStore.install()
    shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
  }

  @After fun stop() {
    scope.cancel()
    FakeAndroidKeyStore.uninstall()
  }

  private fun vault(dir: File = File(app.noBackupFilesDir, "fluid-vault/prova")): FluidVault =
    FluidVault.create(dir = dir, keystore = AndroidVaultKeystore(app), scope = scope)

  // --- engine-ai: l'alias e il formato di prima ---------------------------------------------------------------------

  @Test fun `le chiavi scritte dalla versione di oggi si riaprono con KeystoreCipher nuovo, stesso alias`() {
    val blob = KeystoreCipherOggi().encrypt("sk-or-v1-7f3a9c2e41b8d605")
    assertEquals(setOf("fluidengine.ai"), FakeAndroidKeyStore.entries.keys)
    assertEquals("sk-or-v1-7f3a9c2e41b8d605", KeystoreCipher().decrypt(blob))
    // Nessuna chiave nuova: la cassaforte ha usato quella che c'era.
    assertEquals(setOf("fluidengine.ai"), FakeAndroidKeyStore.entries.keys)
    // E all'indietro: una versione di prima rilegge quello che scrive la nuova.
    assertEquals("sk-ant-api03-Qm4x8LpZ2v", KeystoreCipherOggi().decrypt(KeystoreCipher().encrypt("sk-ant-api03-Qm4x8LpZ2v")))
  }

  @Test fun `anche l'alias di un'app (fluidweather_ai) resta quello`() {
    val blob = KeystoreCipherOggi("fluidweather.ai").encrypt("gsk_meteo")
    assertEquals("gsk_meteo", KeystoreCipher("fluidweather.ai").decrypt(blob))
    assertNull("un altro alias non apre", KeystoreCipher().decrypt(blob))
    assertTrue("fluidweather.ai" in FakeAndroidKeyStore.entries)
  }

  @Test fun `AiKeyStore legge le chiavi salvate dalla versione di oggi nel suo DataStore`() = runBlocking {
    val file = File(app.filesDir, "datastore/ai_keys_prova.preferences_pb")
    val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store = PreferenceDataStoreFactory.create(scope = dataScope) { file }
    store.edit { it[stringPreferencesKey("key_${ProviderId.OPENROUTER.id}")] = KeystoreCipherOggi().encrypt("sk-or-v1-segreto") }
    val keys = AiKeyStore(store, KeystoreCipher())
    assertEquals("sk-or-v1-segreto", keys.key(ProviderId.OPENROUTER))
    assertTrue(keys.currentStates().getValue(ProviderId.OPENROUTER).present)
    dataScope.cancel()
  }

  @Test fun `una chiave nuova di engine-ai nasce nel chip separato quando c'è, e nel TEE quando non c'è`() {
    KeystoreCipher().encrypt("x")
    assertFalse(FakeAndroidKeyStore.entries.getValue("fluidengine.ai").spec.isStrongBoxBacked)
    FakeAndroidKeyStore.entries.clear()
    FakeAndroidKeyStore.strongBoxAvailable = true
    KeystoreCipher().encrypt("x")
    assertTrue(FakeAndroidKeyStore.entries.getValue("fluidengine.ai").spec.isStrongBoxBacked)
  }

  // --- il Keystore vero, visto dalla cassaforte ---------------------------------------------------------------------

  @Test fun `le chiavi della cassaforte chiedono al Keystore quello che serve`() {
    val ks = AndroidVaultKeystore(app)
    ks.createSecretKey("a", VaultKeySpec())
    ks.createKeyPair("b", VaultKeySpec(auth = VaultKeyAuth.BiometricEachUse))
    ks.createKeyPair("r", VaultKeySpec(auth = VaultKeyAuth.Recent(30)))
    ks.createSecretKey("u", VaultKeySpec(unlockedDeviceRequired = true))
    val a = FakeAndroidKeyStore.entries.getValue("a").spec
    assertFalse(a.isUserAuthenticationRequired)
    assertTrue(a.isRandomizedEncryptionRequired)
    assertEquals(256, a.keySize)
    val b = FakeAndroidKeyStore.entries.getValue("b").spec
    assertTrue(b.isUserAuthenticationRequired)
    assertTrue(b.isInvalidatedByBiometricEnrollment)
    assertEquals(0, b.userAuthenticationValidityDurationSeconds)
    assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, b.userAuthenticationType)
    assertEquals(setOf(KeyProperties.PURPOSE_DECRYPT), setOf(b.purposes))
    val r = FakeAndroidKeyStore.entries.getValue("r").spec
    assertFalse(r.isInvalidatedByBiometricEnrollment)
    assertEquals(30, r.userAuthenticationValidityDurationSeconds)
    assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL, r.userAuthenticationType)
    assertTrue(FakeAndroidKeyStore.entries.getValue("u").spec.isUnlockedDeviceRequired)
  }

  @Test fun `la chiave diretta dà lo stesso formato di engine-ai e i dati associati contano`() {
    val key = FluidKeystoreKey.android("prova.diretta", app)
    val sealed = key.seal("ciao".toByteArray(), associated = "a".toByteArray())
    assertEquals(12 + 4 + 16, sealed.size)
    assertEquals("ciao", String(key.open(sealed, associated = "a".toByteArray())))
    try {
      key.open(sealed, associated = "b".toByteArray())
      throw AssertionError("altri dati associati")
    } catch (e: VaultException.Corrupt) {
      // giusto
    }
  }

  @Test fun `cifra e decifra valori, file e DataStore col Keystore di Android`() = runBlocking {
    val section = vault().section()
    section.values.putString("imap", "hunter2")
    section.file("referto.pdf").write(ByteArray(150_000) { (it % 251).toByte() })
    val prefs = section.preferencesDataStore("conti")
    prefs.edit { it[stringPreferencesKey("iban")] = "IT60X0542811101000000123456" }
    val again = vault().section()
    assertEquals("hunter2", again.values.getString("imap"))
    assertEquals(150_000, again.file("referto.pdf").read().size)
    assertEquals("IT60X0542811101000000123456", prefs.data.first()[stringPreferencesKey("iban")])
    // Tutto fuori dal backup: dentro noBackupFilesDir.
    assertTrue(section.file("referto.pdf").file.canonicalPath.startsWith(app.noBackupFilesDir.canonicalPath))
  }

  @Test fun `la sezione all'impronta vuole il dialogo vero, e l'impronta aggiunta passa al codice e rifà la chiave`() = runBlocking {
    val access = VaultAccess.Biometric()
    vault().section("note", access).apply {
      assertEquals(VaultUnlock.Opened(), unlock(AndroidFakeAuthenticator()))
      values.putString("nota", "4821")
    }
    // Senza dialogo il Keystore non apre: il doFinal chiede l'impronta.
    val liar = VaultAuthenticator { VaultAuthResult.Success }
    assertTrue(vault().section("note", access).unlock(liar) is VaultUnlock.Failed)

    val auth = AndroidFakeAuthenticator()
    assertEquals(VaultUnlock.Opened(), vault().section("note", access).unlock(auth))
    assertEquals(VaultAuthMethod.Biometric, auth.requests.single().method)

    FakeAndroidKeyStore.enrollBiometric()
    val afterEnroll = AndroidFakeAuthenticator()
    val section = vault().section("note", access)
    assertEquals(VaultUnlock.Opened(recovered = VaultInvalidation.BiometricsChanged), section.unlock(afterEnroll))
    assertEquals(listOf(VaultAuthMethod.DeviceCredential), afterEnroll.requests.map { it.method })
    assertEquals("4821", section.values.getString("nota"))
    val next = AndroidFakeAuthenticator()
    assertEquals(VaultUnlock.Opened(), vault().section("note", access).unlock(next))
    assertEquals(listOf(VaultAuthMethod.Biometric), next.requests.map { it.method })
  }

  @Test fun `il blocco schermo tolto si legge dal KeyguardManager come motivo`() = runBlocking {
    val access = VaultAccess.Biometric()
    vault().section("note", access).unlock(AndroidFakeAuthenticator())
    FakeAndroidKeyStore.removeScreenLock()
    shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
    assertEquals(VaultUnlock.Lost(VaultInvalidation.ScreenLockRemoved), vault().section("note", access).unlock(AndroidFakeAuthenticator()))
  }

  @Test fun `il manifesto dell'app chiede USE_BIOMETRIC, che il dialogo di sistema vuole`() {
    // Visto sul Tab S9 il 25/11/2026: senza, canAuthenticate lancia una SecurityException e l'app si chiude. Robolectric
    // non controlla il permesso: si legge il manifesto unito, che porta quello di engine-security.
    val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
    assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.USE_BIOMETRIC"))
  }

  @Test fun `la finestra di riserva rifiuta senza un'autenticazione recente`() {
    val ks = AndroidVaultKeystore(app)
    ks.createKeyPair("r", VaultKeySpec(auth = VaultKeyAuth.Recent(30)))
    try {
      ks.rsaDecrypt("r")
      throw AssertionError("doveva chiedere il codice")
    } catch (e: VaultException.NeedsAuthentication) {
      assertTrue(e.cause is UserNotAuthenticatedException)
    }
    FakeAndroidKeyStore.authenticate()
    ks.rsaDecrypt("r") // adesso sì
  }
}
