package dev.antigravity.fluidengine.people.testing

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.PersonKeyRole
import dev.antigravity.fluidengine.people.PersonLockedException
import dev.antigravity.fluidengine.people.PersonVault

/**
 * La cassaforte della persona in memoria, per le prove e per i dispositivi finti del banco: due chiavi AES casuali,
 * una per classe, e due interruttori per fingere il telefono bloccato ([motherLocked]: la radice non si apre) o non
 * ancora sbloccato dall'accensione ([allLocked]). Non va mai in un'app vera: lì c'è la custodia del Keystore.
 */
class MemoryPersonVault : PersonVault {
  private val keys = PersonKeyRole.entries.associateWith { PampaCrypto.random(32) }

  @Volatile var motherLocked: Boolean = false
  @Volatile var allLocked: Boolean = false

  /** Quante volte si è aperta la radice: le prove controllano che il lavoro in sottofondo non la tocchi. */
  @Volatile var rootOpens: Int = 0
    private set

  override fun seal(role: PersonKeyRole, plaintext: ByteArray, aad: ByteArray): ByteArray {
    check(role, "chiudere")
    val nonce = PampaCrypto.random(12)
    return nonce + PampaCrypto.aesGcmSeal(keys.getValue(role), nonce, plaintext, aad)
  }

  override fun open(role: PersonKeyRole, sealed: ByteArray, aad: ByteArray): ByteArray {
    check(role, "aprire")
    if (role == PersonKeyRole.ROOT) rootOpens++
    return PampaCrypto.aesGcmOpen(keys.getValue(role), sealed.copyOf(12), sealed.copyOfRange(12, sealed.size), aad)
  }

  private fun check(role: PersonKeyRole, what: String) {
    if (allLocked) throw PersonLockedException("telefono non ancora sbloccato dall'accensione: non posso $what")
    if (role == PersonKeyRole.ROOT && motherLocked) throw PersonLockedException("telefono bloccato: la radice resta chiusa")
  }
}
