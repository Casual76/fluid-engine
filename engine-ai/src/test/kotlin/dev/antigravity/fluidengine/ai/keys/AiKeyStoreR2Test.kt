package dev.antigravity.fluidengine.ai.keys

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultUnavailability
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un Keystore che per un momento non risponde (un errore passeggero del sistema), poi torna. */
private class BusyKeystoreCipher : SecretCipher {
  var busy = false
  override fun encrypt(plain: String): String = "plain:$plain"
  override fun decrypt(blob: String): String? {
    if (busy) throw VaultException.Unavailable(VaultUnavailability.KeystoreError)
    return blob.removePrefix("plain:")
  }
}

/** R2 (muse-f8-prime, minore della cassaforte): `AiKeyStore.key` con il Keystore che non risponde. */
class AiKeyStoreR2Test {
  @Test
  fun `il Keystore che non risponde adesso non fa cadere chi chiede la chiave, e la chiave resta`() = runBlocking {
    val file = File(Files.createTempDirectory("ai-keys-r2").toFile(), "keys.preferences_pb")
    val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { file })
    val cipher = BusyKeystoreCipher()
    val keys = AiKeyStore(data, cipher)
    keys.set(ProviderId.GROQ, "gsk_finta")

    cipher.busy = true
    assertNull("niente chiave adesso, ma nessuna eccezione", keys.key(ProviderId.GROQ))
    assertTrue("la chiave non si cancella per un errore passeggero", keys.states.first().getValue(ProviderId.GROQ).present)

    cipher.busy = false
    assertEquals("gsk_finta", keys.key(ProviderId.GROQ))
  }
}
