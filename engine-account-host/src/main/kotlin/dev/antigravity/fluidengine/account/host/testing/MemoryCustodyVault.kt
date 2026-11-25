package dev.antigravity.fluidengine.account.host.testing

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyLostException
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.custody.CustodyVault
import dev.antigravity.fluidengine.account.host.custody.KeyClass
import java.util.EnumMap

/**
 * La cassaforte finta delle prove: fa quello che fa il Keystore per la custodia, compresi i casi che Robolectric non
 * ha (critica F2):
 * - [locked]: il telefono bloccato, la madre non si apre;
 * - [beforeFirstUnlock]: appena acceso, non si apre niente;
 * - [invalidate]: la chiave del Keystore persa (`b\chiavi.md` § 11, punto 7);
 * - [opened]: quante volte si è aperta ogni classe, per provare che il sottofondo non tocca mai la madre (punto 13).
 *
 * Mai in un'app.
 */
class MemoryCustodyVault : CustodyVault {
  @Volatile var locked: Boolean = false
  @Volatile var beforeFirstUnlock: Boolean = false

  private val keys = EnumMap<KeyClass, ByteArray>(KeyClass::class.java)
  val opened: MutableMap<KeyClass, Int> = EnumMap(KeyClass::class.java)
  val sealedCount: MutableMap<KeyClass, Int> = EnumMap(KeyClass::class.java)

  @Synchronized
  override fun seal(keyClass: KeyClass, plaintext: ByteArray, aad: ByteArray): ByteArray {
    guard(keyClass)
    val key = keys.getOrPut(keyClass) { PampaCrypto.random(32) }
    sealedCount[keyClass] = (sealedCount[keyClass] ?: 0) + 1
    val nonce = PampaCrypto.random(PampaCrypto.NONCE_BYTES)
    return nonce + PampaCrypto.aesGcmSeal(key, nonce, plaintext, aad)
  }

  @Synchronized
  override fun open(keyClass: KeyClass, sealed: ByteArray, aad: ByteArray): ByteArray {
    guard(keyClass)
    val key = keys[keyClass] ?: throw CustodyKeyLostException(keyClass)
    opened[keyClass] = (opened[keyClass] ?: 0) + 1
    return try {
      PampaCrypto.aesGcmOpen(key, sealed.copyOfRange(0, PampaCrypto.NONCE_BYTES), sealed.copyOfRange(PampaCrypto.NONCE_BYTES, sealed.size), aad)
    } catch (e: Exception) {
      throw CustodyKeyLostException(keyClass, e)
    }
  }

  @Synchronized
  override fun destroy() {
    keys.clear()
  }

  /** Il Keystore ha perso le chiavi (tutte, o solo quella di [keyClass]). */
  @Synchronized
  fun invalidate(keyClass: KeyClass? = null) {
    if (keyClass == null) keys.clear() else keys.remove(keyClass)
  }

  private fun guard(keyClass: KeyClass) {
    if (beforeFirstUnlock) throw CustodyLockedException(keyClass)
    if (locked && keyClass == KeyClass.MOTHER) throw CustodyLockedException(keyClass)
  }
}
