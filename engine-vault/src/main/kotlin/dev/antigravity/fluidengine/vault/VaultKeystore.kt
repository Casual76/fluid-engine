package dev.antigravity.fluidengine.vault

import java.security.PublicKey
import javax.crypto.Cipher

/**
 * Che cosa chiede una chiave del Keystore prima di lavorare.
 *
 * Sono le tre classi che servono alla cassaforte (F8.23), prese da quelle del Portachiavi di Apple
 * (`SecAccessControlCreateFlags`, letto il 24/11/2026, fiducia alta):
 * - [None]: niente, basta il telefono (il Portachiavi «dopo il primo sblocco»);
 * - [BiometricEachUse]: ogni uso chiede l'impronta o il volto **forte**, e un'impronta aggiunta la invalida per sempre
 *   (`kSecAccessControlBiometryCurrentSet`);
 * - [Recent]: si usa per qualche secondo dopo un'autenticazione forte o col codice del telefono, e un'impronta nuova
 *   non la tocca (`kSecAccessControlUserPresence`). È la riserva delle sezioni all'impronta.
 *
 * Togliere il blocco schermo invalida sia [BiometricEachUse] sia [Recent]: è Android, non si può evitare.
 */
sealed interface VaultKeyAuth {
  /** Nessuna autenticazione: la chiave lavora finché il telefono è acceso e l'app gira. */
  data object None : VaultKeyAuth

  /** Ogni operazione chiede l'impronta forte, con un `CryptoObject`. Invalidata da un'impronta nuova. */
  data object BiometricEachUse : VaultKeyAuth

  /** La chiave lavora per [seconds] secondi dopo un'autenticazione forte o col codice del telefono. */
  data class Recent(val seconds: Int) : VaultKeyAuth {
    init {
      require(seconds in 1..3600) { "finestra non valida: $seconds s" }
    }
  }
}

/**
 * Come nasce una chiave del Keystore.
 *
 * @param auth che cosa chiede prima di lavorare.
 * @param strongBox nel chip separato quando il telefono lo ha (StrongBox, Android 9+): più lento, più al riparo. Senza
 *   chip la chiave nasce nell'ambiente protetto del processore (TEE), sempre.
 * @param unlockedDeviceRequired solo a telefono sbloccato (`setUnlockedDeviceRequired`, Android 9+): la classe
 *   «Complete» della Data Protection di Apple. Spento di serie, perché un lavoro in sottofondo a schermo spento non
 *   potrebbe più leggere niente.
 */
data class VaultKeySpec(
  val auth: VaultKeyAuth = VaultKeyAuth.None,
  val strongBox: Boolean = true,
  val unlockedDeviceRequired: Boolean = false,
)

/**
 * Quello che si sa di una chiave appena nata.
 *
 * @param strongBox vero se sta nel chip separato, falso se nel TEE, nullo se non si sa (una chiave trovata già fatta).
 */
data class VaultKeyInfo(
  val alias: String,
  val auth: VaultKeyAuth,
  val strongBox: Boolean?,
)

/**
 * Un'operazione di cifratura pronta: il [cipher] da passare al dialogo di sistema quando la chiave chiede l'impronta
 * ([VaultKeyAuth.BiometricEachUse], il `CryptoObject` di `BiometricPrompt`), e [finish] che la conclude.
 */
interface VaultCipherOp {
  /** Il `Cipher` inizializzato: va dato a `BiometricPrompt.CryptoObject`, mai usato a mano. */
  val cipher: Cipher

  /** Il nonce scelto dal Keystore per una cifratura AES-GCM; vuoto per le altre operazioni. */
  val iv: ByteArray

  /** I dati associati (AES-GCM): autenticati ma non cifrati. Prima di [finish]. */
  fun associate(data: ByteArray)

  /**
   * Conclude l'operazione su [input].
   *
   * @throws VaultException.NeedsAuthentication se la chiave chiede un'autenticazione che non c'è stata.
   * @throws VaultException.Invalidated se la chiave non vale più.
   * @throws VaultException.Corrupt se il testo cifrato non torna (dato toccato, chiave sbagliata).
   */
  fun finish(input: ByteArray): ByteArray
}

/**
 * Il Keystore visto dalla cassaforte: crea, trova e usa le chiavi per nome.
 *
 * In un'app vera è il Keystore di Android ([AndroidVaultKeystore]). Nei test, e sul computer, è [MemoryVaultKeystore],
 * che fa le stesse cose in memoria e finge l'impronta, le impronte aggiunte e il blocco schermo tolto.
 *
 * Due famiglie di chiavi:
 * - **AES-256-GCM** ([createSecretKey], [aesEncrypt], [aesDecrypt]): veloci, cifrano e decifrano dentro il Keystore;
 * - **RSA-2048-OAEP** ([createKeyPair], [publicKey], [rsaDecrypt]): si chiude con la pubblica, fuori dal Keystore e
 *   senza chiedere niente, e si apre con la privata, che chiede l'impronta. È così che una sezione all'impronta nasce,
 *   e si richiude con una chiave nuova dopo un'impronta aggiunta, con **un solo** dialogo.
 */
interface VaultKeystore {
  /** Vero se il telefono ha un blocco schermo sicuro (PIN, sequenza, password). */
  val isDeviceSecure: Boolean

  /** Vero se c'è una chiave con questo nome. */
  fun contains(alias: String): Boolean

  /** Toglie la chiave. Nessun errore se non c'era. */
  fun delete(alias: String)

  /**
   * Crea una chiave AES-256-GCM.
   *
   * @throws VaultException.Unavailable se il telefono non può farla (nessun blocco schermo, nessuna impronta).
   */
  fun createSecretKey(alias: String, spec: VaultKeySpec): VaultKeyInfo

  /** Crea una coppia RSA-2048 per OAEP (SHA-256, MGF1 SHA-1): la privata resta nel Keystore. */
  fun createKeyPair(alias: String, spec: VaultKeySpec): VaultKeyInfo

  /** La chiave pubblica della coppia [alias], fuori dal Keystore; null se la coppia non c'è. */
  fun publicKey(alias: String): PublicKey?

  /**
   * Una cifratura AES-GCM con la chiave [alias]. Il nonce lo sceglie il Keystore.
   *
   * @throws VaultException.Invalidated se la chiave non c'è più o non vale più.
   * @throws VaultException.NeedsAuthentication se la chiave chiede un'autenticazione recente che non c'è.
   */
  fun aesEncrypt(alias: String): VaultCipherOp

  /** Una decifratura AES-GCM con la chiave [alias] e il nonce [iv]. Stesse eccezioni di [aesEncrypt]. */
  fun aesDecrypt(alias: String, iv: ByteArray): VaultCipherOp

  /** Una decifratura RSA-OAEP con la privata della coppia [alias]. Stesse eccezioni di [aesEncrypt]. */
  fun rsaDecrypt(alias: String): VaultCipherOp
}

/** Perché una chiave non vale più. */
enum class VaultInvalidation {
  /** Un'impronta o un volto aggiunti (o tutti tolti): la chiave all'impronta si è chiusa per sicurezza. */
  BiometricsChanged,

  /** Il blocco schermo è stato tolto: Android butta tutte le chiavi che chiedono un'autenticazione. */
  ScreenLockRemoved,

  /** La chiave non c'è più: il Keystore è stato azzerato, o i dati vengono da un altro telefono. */
  KeyLost,
}

/** Perché una sezione non si può proteggere come chiesto, su questo telefono. */
enum class VaultUnavailability {
  /** Nessun blocco schermo sicuro: le chiavi con autenticazione non si possono creare. */
  NoScreenLock,

  /** Nessuna impronta o volto forte registrati. */
  NoBiometrics,

  /** Il Keystore non risponde (un errore del sistema). */
  KeystoreError,
}

/** Gli errori della cassaforte. Ognuno ha la sua frase pronta in [VaultLabels]. */
sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause) {
  /** La sezione è chiusa: va aperta con `unlock` prima di leggere o scrivere. */
  class Locked(val section: String) : VaultException("la sezione «$section» è chiusa")

  /** La chiave non vale più ([reason]): i dati si riaprono solo con la riserva, se c'è. */
  class Invalidated(val reason: VaultInvalidation, cause: Throwable? = null) :
    VaultException("chiave non più valida: $reason", cause)

  /** La chiave chiede un'autenticazione recente (l'impronta o il codice) che non c'è stata. */
  class NeedsAuthentication(cause: Throwable? = null) : VaultException("serve un'autenticazione", cause)

  /** Il dato non torna: toccato, troncato, o chiuso con un'altra chiave. */
  class Corrupt(message: String = "dato cifrato non valido", cause: Throwable? = null) : VaultException(message, cause)

  /** La protezione chiesta non c'è su questo telefono ([reason]). */
  class Unavailable(val reason: VaultUnavailability, cause: Throwable? = null) :
    VaultException("non disponibile: $reason", cause)
}
