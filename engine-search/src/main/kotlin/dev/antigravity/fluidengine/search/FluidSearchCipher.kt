package dev.antigravity.fluidengine.search

import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultSection
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Come l'indice chiude i suoi dati sul disco (F8.22, «i dati sensibili nell'indice»). Con un cifrario:
 *
 * - **il testo conservato** (titoli e campi da cui nascono i pezzi evidenziati), le domande recenti e i vettori del
 *   significato si chiudono con [seal], legati al documento ([associated]: un pezzo non si apre al posto di un altro);
 * - **i campi riservati** ([FluidSearchField.sensitive]) entrano nell'indice delle parole come impronte HMAC-SHA256 con
 *   una chiave dell'indice (64 bit, in esadecimale: un «indice cieco»), che sta chiusa anch'essa nella cassaforte. Il
 *   file dell'indice allora non contiene ne' il testo ne' le parole di quei campi.
 *
 * Senza cifrario il testo conservato sta in chiaro nel file privato dell'app (come i dati dell'app stessa), e un campo
 * riservato non si puo' dichiarare.
 *
 * Cosa resta leggibile anche col cifrario: le radici delle parole dei campi **non** riservati (l'indice delle parole e'
 * fatto di loro), e quanti documenti ci sono. E' lo stesso compromesso di Core Spotlight, che protegge l'indice con la
 * classe di protezione dei file e non parola per parola.
 */
interface FluidSearchCipher {
  /** Chiude [plain], legandolo ad [associated]. */
  suspend fun seal(plain: ByteArray, associated: ByteArray): ByteArray

  /** Riapre quello che [seal] ha chiuso con gli stessi [associated]; lancia un'eccezione se non torna. */
  suspend fun open(sealed: ByteArray, associated: ByteArray): ByteArray

  /**
   * Vero se [error] dice che la chiave non apre piu' niente (un telefono nuovo, il Keystore azzerato, un dato di un'altra
   * chiave): allora l'indice si butta e si rifa'. Falso per una sezione solo chiusa (all'impronta), che va aperta e non
   * svuotata.
   */
  fun isLost(error: Throwable): Boolean = error is GeneralSecurityException

  companion object {
    /**
     * La cassaforte dell'engine: una sezione di `engine-vault` (di solito `VaultAccess.Device()`, che si apre da sola;
     * una sezione all'impronta vuole di essere aperta prima di ogni ricerca).
     */
    fun vault(section: VaultSection): FluidSearchCipher = object : FluidSearchCipher {
      override suspend fun seal(plain: ByteArray, associated: ByteArray): ByteArray = section.seal(plain, associated)
      override suspend fun open(sealed: ByteArray, associated: ByteArray): ByteArray = section.open(sealed, associated)
      override fun isLost(error: Throwable): Boolean =
        error is VaultException.Corrupt || error is VaultException.Invalidated || error is GeneralSecurityException
      override fun toString(): String = "FluidSearchCipher.vault"
    }
  }
}

/** Le impronte delle parole riservate: HMAC-SHA256 con la chiave dell'indice, i primi 8 byte in esadecimale. */
internal class BlindTerms(key: ByteArray) {
  private val spec = SecretKeySpec(key.copyOf(), "HmacSHA256")
  private val mac = ThreadLocal.withInitial { Mac.getInstance("HmacSHA256").apply { init(spec) } }

  /** L'impronta della radice [stem]: si trova per parola intera. */
  fun term(stem: String): String = token("t:$stem")

  /** L'impronta di un inizio di parola: si trova scrivendo le prime lettere (da [MinPrefix]). */
  fun prefix(prefix: String): String = token("p:$prefix")

  private fun token(input: String): String {
    val bytes = mac.get()!!.doFinal(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(1 + Bytes * 2)
    sb.append('h')
    for (i in 0 until Bytes) {
      val b = bytes[i].toInt() and 0xff
      sb.append(Hex[b ushr 4]).append(Hex[b and 0x0f])
    }
    return sb.toString()
  }

  companion object {
    /** Da quante lettere un inizio di parola riservato si trova (sotto, troppi documenti per una sola impronta). */
    const val MinPrefix: Int = 3

    /** Quanti inizi di parola al piu' per una parola riservata (oltre, la parola si trova intera). */
    const val MaxPrefix: Int = 12

    private const val Bytes = 8
    private val Hex = "0123456789abcdef".toCharArray()
  }
}
