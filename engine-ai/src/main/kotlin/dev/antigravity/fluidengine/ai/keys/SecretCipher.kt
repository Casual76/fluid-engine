package dev.antigravity.fluidengine.ai.keys

import dev.antigravity.fluidengine.vault.AndroidVaultKeystore
import dev.antigravity.fluidengine.vault.FluidKeystoreKey
import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultKeystore
import java.util.Base64

/**
 * Cifra e decifra un segreto. Esiste come interfaccia perche' il Keystore non c'e' sul computer:
 * i test lo sostituiscono con una versione trasparente, e il resto del codice non se ne accorge.
 */
interface SecretCipher {
  fun encrypt(plain: String): String

  /** Null quando il blob non si puo' leggere: chiave del Keystore cambiata, dato corrotto. */
  fun decrypt(blob: String): String?
}

/**
 * AES-256-GCM con la chiave dentro l'Android Keystore: e' quello che `EncryptedSharedPreferences`
 * faceva prima di essere deprecata (security-crypto 1.1.0-alpha07, aprile 2025: "usate
 * direttamente il Keystore"). Nessuna dipendenza oltre l'engine, stesso livello di protezione.
 *
 * Dalla 4.0 (F8.23) il lavoro lo fa la cassaforte dell'engine ([FluidKeystoreKey] di `engine-vault`), con **lo stesso
 * alias e lo stesso formato**: le chiavi salvate dalle versioni di prima si riaprono senza toccare niente, e una
 * versione di prima rilegge quelle scritte da questa. Una chiave del Keystore che manca nasce ora nel chip separato
 * (StrongBox) quando il telefono lo ha; una che c'e' gia' resta com'e'.
 *
 * Formato del blob: `v1:` + base64(iv[12] || testo cifrato || tag). L'IV lo sceglie il Keystore a
 * ogni cifratura (`setRandomizedEncryptionRequired`), quindi due salvataggi della stessa chiave
 * non si assomigliano.
 *
 * Parla col Keystore e blocca: va chiamata fuori dal thread principale ([AiKeyStore] lo fa da se').
 */
class KeystoreCipher internal constructor(
  private val alias: String,
  keystore: VaultKeystore,
) : SecretCipher {

  constructor(alias: String = DEFAULT_ALIAS) : this(alias, AndroidVaultKeystore())

  private val key = FluidKeystoreKey(alias, keystore)

  override fun encrypt(plain: String): String =
    PREFIX + Base64.getEncoder().encodeToString(key.seal(plain.toByteArray(Charsets.UTF_8)))

  override fun decrypt(blob: String): String? {
    if (!blob.startsWith(PREFIX)) return null
    return try {
      val bytes = Base64.getDecoder().decode(blob.removePrefix(PREFIX))
      if (bytes.size <= IV_BYTES) return null
      String(key.open(bytes), Charsets.UTF_8)
    } catch (e: VaultException.Corrupt) {
      null
    } catch (e: VaultException.Invalidated) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  companion object {
    /**
     * L'alias della chiave nel Keystore. Un'app che aveva gia' il proprio (FluidWeather usava
     * `fluidweather.ai`) lo passa al costruttore, altrimenti le chiavi salvate non si leggono piu'.
     */
    const val DEFAULT_ALIAS = "fluidengine.ai"
    private const val PREFIX = "v1:"
    private const val IV_BYTES = 12
  }
}
