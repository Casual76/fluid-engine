package dev.antigravity.fluidengine.account.host.custody

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.vault.AndroidVaultKeystore
import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultKeyAuth
import dev.antigravity.fluidengine.vault.VaultKeySpec
import dev.antigravity.fluidengine.vault.VaultKeystore
import java.util.EnumMap

/** La chiave di quella classe adesso non si apre: il telefono è bloccato (classe madre), o non è ancora stato sbloccato. */
class CustodyLockedException(val keyClass: KeyClass) : Exception("la chiave ${keyClass.name.lowercase()} si apre solo a telefono sbloccato")

/**
 * La chiave del Keystore non c'è più o non vale più: un ripristino di fabbrica parziale, un aggiornamento che cambia il
 * Keystore, il blocco schermo tolto su Android 9–14 (`b\chiavi.md` § 11, punto 7). I file chiusi con lei sono persi:
 * lo Store se ne accorge e riapre la chiave dei dati da una porta (codice, passkey col PRF, passaggio).
 */
class CustodyKeyLostException(val keyClass: KeyClass, cause: Throwable? = null) : Exception("la chiave ${keyClass.name.lowercase()} del Keystore non vale più", cause)

/**
 * Dove la custodia chiude i suoi file: una chiave per classe ([KeyClass]). L'implementazione vera è
 * [KeystoreCustodyVault], sopra la cassaforte dell'engine (`engine-vault`, F8.23); le prove usano
 * `testing.MemoryCustodyVault`, che sa fare il telefono bloccato e la chiave persa.
 */
interface CustodyVault {
  /** Chiude [plaintext] con la chiave della classe [keyClass], legandolo ad [aad]. */
  fun seal(keyClass: KeyClass, plaintext: ByteArray, aad: ByteArray): ByteArray

  /** Apre; [CustodyLockedException] se la classe adesso è chiusa, [CustodyKeyLostException] se la chiave non vale più. */
  fun open(keyClass: KeyClass, sealed: ByteArray, aad: ByteArray): ByteArray

  /** Cancella le chiavi (uscita, uscita da remoto, eliminazione). */
  fun destroy()
}

/**
 * La custodia sopra la cassaforte dell'engine (`engine-vault`, F8.23): una chiave AES-256-GCM per classe, creata e usata
 * dal [VaultKeystore] (il Keystore di Android in un'app, `MemoryVaultKeystore` nelle prove), col formato della cassaforte
 * (`nonce(12) ‖ testo cifrato ‖ tag`). Niente Keystore usato a mano: le invalidazioni, l'autenticazione mancante e il
 * ripiego da StrongBox al TEE li traduce `engine-vault`, e qui diventano le eccezioni della custodia.
 *
 * - **Madre**: `unlockedDeviceRequired` da [Config.unlockedRequiredFromApi] (di serie Android 15). La pagina di
 *   `KeyGenParameterSpec.Builder` dice che su Android 9–14 queste chiavi si cancellavano togliendo il blocco schermo e
 *   non si creavano senza, e consiglia di usarle solo da Android 15 (letta il 25/11, fiducia media). Sotto, il controllo
 *   «telefono bloccato» lo fa il codice con [isDeviceLocked], che vale su tutte le versioni.
 * - **Consegna**: senza vincoli di sblocco né d'impronta (`VaultKeyAuth.None`), usabile dopo il primo sblocco.
 * - **StrongBox** ([Config.strongBox]): di serie solo per la madre, che si usa di rado (StrongBox è più lento).
 */
open class KeystoreCustodyVault(
  private val keystore: VaultKeystore,
  private val isDeviceLocked: () -> Boolean,
  private val config: Config = Config(),
  private val sdkInt: Int = Build.VERSION.SDK_INT,
) : CustodyVault {

  enum class StrongBoxUse { NONE, MOTHER_ONLY, ALL }

  data class Config(
    val aliasPrefix: String = "dev.pampa.account.custody",
    val strongBox: StrongBoxUse = StrongBoxUse.MOTHER_ONLY,
    val unlockedRequiredFromApi: Int = 35,
  )

  /** Le chiavi messe davvero in StrongBox (null se non si sa), per il rapporto della prova sul tablet. */
  val strongBoxBacked: MutableMap<KeyClass, Boolean?> = EnumMap(KeyClass::class.java)

  private fun alias(keyClass: KeyClass) = "${config.aliasPrefix}.${keyClass.name.lowercase()}"

  /** Come nasce la chiave di [keyClass]: la ricetta della cassaforte con le regole della custodia. */
  fun specFor(keyClass: KeyClass): VaultKeySpec = VaultKeySpec(
    auth = VaultKeyAuth.None,
    strongBox = when (config.strongBox) {
      StrongBoxUse.NONE -> false
      StrongBoxUse.MOTHER_ONLY -> keyClass == KeyClass.MOTHER
      StrongBoxUse.ALL -> true
    },
    unlockedDeviceRequired = keyClass == KeyClass.MOTHER && sdkInt >= config.unlockedRequiredFromApi,
  )

  override fun seal(keyClass: KeyClass, plaintext: ByteArray, aad: ByteArray): ByteArray = guarded(keyClass) {
    synchronized(this) {
      if (!keystore.contains(alias(keyClass))) strongBoxBacked[keyClass] = keystore.createSecretKey(alias(keyClass), specFor(keyClass)).strongBox
    }
    val op = keystore.aesEncrypt(alias(keyClass))
    op.associate(aad)
    val body = op.finish(plaintext)
    op.iv + body
  }

  override fun open(keyClass: KeyClass, sealed: ByteArray, aad: ByteArray): ByteArray = guarded(keyClass) {
    if (sealed.size < PampaCrypto.NONCE_BYTES + PampaCrypto.TAG_BYTES) throw PampaCryptoException("file della custodia troncato")
    if (!keystore.contains(alias(keyClass))) throw CustodyKeyLostException(keyClass)
    val op = keystore.aesDecrypt(alias(keyClass), sealed.copyOfRange(0, PampaCrypto.NONCE_BYTES))
    op.associate(aad)
    op.finish(sealed.copyOfRange(PampaCrypto.NONCE_BYTES, sealed.size))
  }

  override fun destroy() {
    for (c in KeyClass.entries) runCatching { keystore.delete(alias(c)) }
  }

  private fun <T> guarded(keyClass: KeyClass, block: () -> T): T {
    if (keyClass == KeyClass.MOTHER && isDeviceLocked()) throw CustodyLockedException(keyClass)
    return try {
      block()
    } catch (e: VaultException.Invalidated) {
      throw CustodyKeyLostException(keyClass, e)
    } catch (e: VaultException.NeedsAuthentication) {
      // Su Android 15+ il Keystore rifiuta la madre a telefono bloccato come un'autenticazione mancante.
      throw CustodyLockedException(keyClass)
    } catch (e: VaultException.Corrupt) {
      throw PampaCryptoException("file della custodia toccato o di un'altra chiave", e)
    } catch (e: VaultException) {
      throw PampaCryptoException("cassaforte: ${e.message}", e)
    }
  }
}

/**
 * La custodia nel Keystore di Android, attraverso `engine-vault` ([AndroidVaultKeystore]): è quella che usa lo Store.
 * Il telefono bloccato lo dice `KeyguardManager.isDeviceLocked`.
 */
class AndroidKeystoreVault(
  context: Context,
  config: KeystoreCustodyVault.Config = KeystoreCustodyVault.Config(),
) : KeystoreCustodyVault(
  keystore = AndroidVaultKeystore(context),
  isDeviceLocked = { context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true },
  config = config,
)
