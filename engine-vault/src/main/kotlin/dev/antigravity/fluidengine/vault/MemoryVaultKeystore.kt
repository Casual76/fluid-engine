package dev.antigravity.fluidengine.vault

import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Collections
import java.util.IdentityHashMap
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Un Keystore in memoria, **per i test e per il computer**: mai in un'app vera (le chiavi stanno in chiaro nella memoria
 * del processo e muoiono con lui).
 *
 * Fa le stesse cose del Keystore di Android, con la crittografia vera di Java, e finge quello che un test non può fare
 * con un telefono:
 * - [authenticate] è un'autenticazione dell'utente (l'impronta forte o il codice): apre le chiavi
 *   [VaultKeyAuth.Recent] per i loro secondi;
 * - [approve] è il dialogo di sistema riuscito con un `CryptoObject`: apre **una** operazione di una chiave
 *   [VaultKeyAuth.BiometricEachUse];
 * - [enrollBiometric] aggiunge un'impronta (invalida le chiavi all'impronta), [removeScreenLock] toglie il blocco
 *   schermo (invalida tutte le chiavi con autenticazione), [wipe] azzera il Keystore.
 *
 * @param clock il tempo che passa, in millisecondi (di serie quello del processo): un test lo muove a mano.
 */
class MemoryVaultKeystore(
  private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : VaultKeystore {
  private sealed class Entry(val spec: VaultKeySpec) {
    var invalid: VaultInvalidation? = null
  }

  private class Secret(spec: VaultKeySpec, val key: ByteArray) : Entry(spec)

  private class Pair(spec: VaultKeySpec, val pair: KeyPair) : Entry(spec)

  private val entries = LinkedHashMap<String, Entry>()
  private val approved: MutableSet<Cipher> = Collections.newSetFromMap(IdentityHashMap())
  private var lastAuthentication: Long? = null

  /** Vero se il telefono finto ha un blocco schermo. */
  var deviceSecure: Boolean = true

  /** Vero se il telefono finto ha almeno un'impronta registrata. */
  var biometricsEnrolled: Boolean = true

  /** Vero se il telefono finto ha StrongBox: lo dice solo [VaultKeyInfo.strongBox]. */
  var strongBoxAvailable: Boolean = false

  /** Quante operazioni sono state concluse: per i test che contano gli usi del Keystore. */
  var operations: Int = 0
    private set

  override val isDeviceSecure: Boolean get() = deviceSecure

  /** Le chiavi che ci sono adesso. */
  val aliases: Set<String> get() = synchronized(entries) { entries.keys.toSet() }

  /** Un'autenticazione dell'utente, adesso: apre le chiavi a finestra ([VaultKeyAuth.Recent]). */
  fun authenticate() {
    lastAuthentication = clock()
  }

  /** Il dialogo di sistema è riuscito col `CryptoObject` [cipher]: l'operazione all'impronta si può concludere. */
  fun approve(cipher: Cipher) {
    synchronized(approved) { approved += cipher }
    authenticate()
  }

  /** Un'impronta aggiunta: le chiavi [VaultKeyAuth.BiometricEachUse] non valgono più. */
  fun enrollBiometric() {
    biometricsEnrolled = true
    synchronized(entries) {
      entries.values.filter { it.spec.auth == VaultKeyAuth.BiometricEachUse && it.invalid == null }
        .forEach { it.invalid = VaultInvalidation.BiometricsChanged }
    }
  }

  /** Il blocco schermo tolto: tutte le chiavi con autenticazione non valgono più. */
  fun removeScreenLock() {
    deviceSecure = false
    biometricsEnrolled = false
    synchronized(entries) {
      entries.values.filter { it.spec.auth != VaultKeyAuth.None }.forEach { it.invalid = VaultInvalidation.ScreenLockRemoved }
    }
  }

  /** Il Keystore azzerato: nessuna chiave resta. */
  fun wipe() {
    synchronized(entries) { entries.clear() }
  }

  override fun contains(alias: String): Boolean = synchronized(entries) { alias in entries }

  override fun delete(alias: String) {
    synchronized(entries) { entries.remove(alias) }
  }

  override fun createSecretKey(alias: String, spec: VaultKeySpec): VaultKeyInfo {
    checkCreatable(spec)
    synchronized(entries) { entries[alias] = Secret(spec, VaultCrypto.randomBytes(VaultCrypto.KeyBytes)) }
    return VaultKeyInfo(alias, spec.auth, strongBox = spec.strongBox && strongBoxAvailable)
  }

  override fun createKeyPair(alias: String, spec: VaultKeySpec): VaultKeyInfo {
    checkCreatable(spec)
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    synchronized(entries) { entries[alias] = Pair(spec, pair) }
    return VaultKeyInfo(alias, spec.auth, strongBox = spec.strongBox && strongBoxAvailable)
  }

  override fun publicKey(alias: String): PublicKey? = (synchronized(entries) { entries[alias] } as? Pair)?.pair?.public

  override fun aesEncrypt(alias: String): VaultCipherOp {
    val entry = usable<Secret>(alias)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val iv = VaultCrypto.randomBytes(VaultCrypto.NonceBytes)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(entry.key, "AES"), GCMParameterSpec(VaultCrypto.TagBits, iv))
    return MemoryOp(alias, entry, cipher, iv)
  }

  override fun aesDecrypt(alias: String, iv: ByteArray): VaultCipherOp {
    val entry = usable<Secret>(alias)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    try {
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(entry.key, "AES"), GCMParameterSpec(VaultCrypto.TagBits, iv))
    } catch (e: GeneralSecurityException) {
      throw VaultException.Corrupt(cause = e)
    }
    return MemoryOp(alias, entry, cipher, iv)
  }

  override fun rsaDecrypt(alias: String): VaultCipherOp {
    val entry = usable<Pair>(alias)
    val cipher = Cipher.getInstance(VaultCrypto.RsaOaep)
    cipher.init(Cipher.DECRYPT_MODE, entry.pair.private, VaultCrypto.OaepParams)
    return MemoryOp(alias, entry, cipher, ByteArray(0))
  }

  private fun checkCreatable(spec: VaultKeySpec) {
    if (spec.auth == VaultKeyAuth.None) return
    if (!deviceSecure) throw VaultException.Unavailable(VaultUnavailability.NoScreenLock)
    if (spec.auth == VaultKeyAuth.BiometricEachUse && !biometricsEnrolled) {
      throw VaultException.Unavailable(VaultUnavailability.NoBiometrics)
    }
  }

  private inline fun <reified E : Entry> usable(alias: String): E {
    val entry = synchronized(entries) { entries[alias] } as? E ?: throw VaultException.Invalidated(VaultInvalidation.KeyLost)
    entry.invalid?.let { throw VaultException.Invalidated(it) }
    val auth = entry.spec.auth
    // Come il Keystore: una chiave a finestra si rifiuta già all'init, una all'impronta solo al doFinal.
    if (auth is VaultKeyAuth.Recent && !recentlyAuthenticated(auth.seconds)) throw VaultException.NeedsAuthentication()
    return entry
  }

  private fun recentlyAuthenticated(seconds: Int): Boolean {
    val at = lastAuthentication ?: return false
    return clock() - at <= seconds * 1_000L
  }

  private inner class MemoryOp(
    private val alias: String,
    private val entry: Entry,
    override val cipher: Cipher,
    override val iv: ByteArray,
  ) : VaultCipherOp {
    override fun associate(data: ByteArray) {
      if (data.isNotEmpty()) cipher.updateAAD(data)
    }

    override fun finish(input: ByteArray): ByteArray {
      entry.invalid?.let { throw VaultException.Invalidated(it) }
      if (synchronized(entries) { entries[alias] } !== entry) throw VaultException.Invalidated(VaultInvalidation.KeyLost)
      if (entry.spec.auth == VaultKeyAuth.BiometricEachUse) {
        val ok = synchronized(approved) { approved.remove(cipher) }
        if (!ok) throw VaultException.NeedsAuthentication()
      }
      return try {
        cipher.doFinal(input).also { operations++ }
      } catch (e: AEADBadTagException) {
        throw VaultException.Corrupt(cause = e)
      } catch (e: GeneralSecurityException) {
        throw VaultException.Corrupt(cause = e)
      }
    }
  }
}
