package dev.antigravity.fluidengine.vault

import android.content.Context

/**
 * Una chiave AES-256-GCM del Keystore usata **direttamente**: ogni cifratura entra nel Keystore e la chiave non è mai
 * nella memoria dell'app. È la forma più semplice della cassaforte, per pochi segreti piccoli (una chiave di un
 * servizio, un token): ogni operazione costa un viaggio al Keystore, millisecondi nel TEE e decine con StrongBox.
 * Per molti valori, file o un DataStore serve una sezione di [FluidVault].
 *
 * Il formato è quello di `KeystoreCipher` di `engine-ai` dalla 2.x: **nonce(12) ‖ testo cifrato ‖ tag(16)**, col nonce
 * scelto dal Keystore. Le chiavi già scritte con quel formato e con lo stesso [alias] si riaprono senza toccare niente.
 *
 * La chiave nasce alla prima [seal], con [spec] (StrongBox quando c'è). Se c'è già (scritta da una versione di prima),
 * si usa com'è: i suoi parametri non si cambiano.
 *
 * **Fuori dal thread principale**: [seal] e [open] parlano col Keystore e sono bloccanti.
 */
class FluidKeystoreKey(
  /** Il nome della chiave nel Keystore. Cambiarlo vuol dire perdere l'accesso a quello che ha cifrato. */
  val alias: String,
  private val keystore: VaultKeystore,
  private val spec: VaultKeySpec = VaultKeySpec(),
) {
  init {
    require(alias.isNotBlank()) { "alias vuoto" }
  }

  /** Vero se la chiave c'è già nel Keystore. */
  val exists: Boolean get() = keystore.contains(alias)

  /**
   * Cifra [plain]: nonce(12) ‖ testo cifrato ‖ tag(16). Crea la chiave se manca.
   *
   * @param associated i dati associati (AES-GCM): non cifrati ma legati al risultato, da ripassare uguali a [open].
   * @throws VaultException se il Keystore non può (vedi [VaultKeystore.aesEncrypt]).
   */
  fun seal(plain: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray {
    synchronized(this) {
      if (!keystore.contains(alias)) keystore.createSecretKey(alias, spec)
    }
    val op = keystore.aesEncrypt(alias)
    op.associate(associated)
    val body = op.finish(plain)
    return op.iv + body
  }

  /**
   * Decifra quello che [seal] ha cifrato.
   *
   * @throws VaultException.Corrupt se il dato non torna (toccato, troncato, altra chiave).
   * @throws VaultException.Invalidated se la chiave non c'è più o non vale più.
   */
  fun open(sealed: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray {
    if (sealed.size <= VaultCrypto.NonceBytes) throw VaultException.Corrupt("dato troppo corto")
    val op = keystore.aesDecrypt(alias, sealed.copyOfRange(0, VaultCrypto.NonceBytes))
    op.associate(associated)
    return op.finish(sealed.copyOfRange(VaultCrypto.NonceBytes, sealed.size))
  }

  /** Toglie la chiave dal Keystore: quello che ha cifrato non si riapre più. */
  fun delete() {
    keystore.delete(alias)
  }

  companion object {
    /** La chiave [alias] nel Keystore di Android. [context] serve solo a leggere il blocco schermo; può mancare. */
    fun android(alias: String, context: Context? = null, spec: VaultKeySpec = VaultKeySpec()): FluidKeystoreKey =
      FluidKeystoreKey(alias, AndroidVaultKeystore(context), spec)
  }
}
