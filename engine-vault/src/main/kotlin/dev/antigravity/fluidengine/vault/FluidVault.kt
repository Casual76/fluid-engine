package dev.antigravity.fluidengine.vault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import java.io.File
import javax.crypto.Cipher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Chi può aprire una sezione della cassaforte. Si sceglie quando la sezione nasce e non cambia più.
 */
sealed interface VaultAccess {
  /**
   * **Il solo telefono.** La sezione si apre senza chiedere niente, ma solo su questo telefono: la sua chiave sta nel
   * Keystore e non esce, e i file stanno fuori dal backup. È la classe «fino al primo sblocco» della Data Protection di
   * Apple, quella di serie su iOS.
   *
   * @param whileUnlocked solo a telefono sbloccato (la classe «Complete» di Apple, `setUnlockedDeviceRequired` da
   *   Android 9): a schermo spento la sezione si chiude e un lavoro in sottofondo non la legge.
   */
  data class Device(val whileUnlocked: Boolean = false) : VaultAccess

  /**
   * **L'impronta.** La sezione si apre solo dopo l'impronta o il volto forte, e si richiude da sola dopo [relockAfter]
   * senza usarla, e quando il telefono si blocca. È il Portachiavi con `kSecAccessControlBiometryCurrentSet`: un'impronta
   * aggiunta chiude la chiave per sempre, e la sezione si riapre con la [reserve] (con la frase pronta di
   * [VaultLabels.biometricsChanged]), che rifà la chiave da sola.
   */
  data class Biometric(
    val relockAfter: Duration = VaultDefaults.RelockAfter,
    val reserve: VaultReserve = VaultReserve.DeviceCredential,
  ) : VaultAccess {
    init {
      require(relockAfter.isPositive()) { "relockAfter deve essere positivo" }
    }
  }
}

/** La seconda porta di una sezione all'impronta: quella che resta quando l'impronta non va. */
enum class VaultReserve {
  /**
   * Il codice del telefono (PIN, sequenza o password dello schermo). Resta se si aggiunge un'impronta; non resta se si
   * toglie il blocco schermo. Come «Usa codice» di iOS.
   */
  DeviceCredential,

  /**
   * Un segreto dell'app (il PIN dell'app, una frase), dato con [VaultSection.setReserveSecret] a sezione aperta. Resta
   * anche se si toglie il blocco schermo, ma solo se la sezione ha già un segreto.
   */
  AppSecret,

  /** Nessuna: senza impronta i dati sono persi. Va bene solo per dati che si rifanno (una password da reinserire). */
  None,
}

/**
 * I numeri della cassaforte.
 */
object VaultDefaults {
  /**
   * Dopo quanto una sezione all'impronta si richiude senza usarla: 5 minuti. Apple scrive che aprire una nota bloccata
   * apre tutte le note bloccate «per qualche minuto» (support.apple.com, «Lock your notes on iPhone», iOS 27, letto il
   * 24/11/2026): il numero esatto non lo dice. **A occhio**, 24/11/2026.
   */
  val RelockAfter: Duration = 5.minutes

  /**
   * Per quanti secondi la chiave di riserva lavora dopo il codice del telefono: 30. Il tempo di aprire la sezione e,
   * dopo un'impronta aggiunta, di rifarne la chiave. **A occhio**, 24/11/2026.
   */
  const val ReserveWindowSeconds: Int = 30

  /**
   * I giri di PBKDF2-HMAC-SHA256 per il segreto dell'app: 210 000. È il numero di OWASP del 2023 per PBKDF2-HMAC-SHA512
   * (per SHA-256 ne chiede 600 000): basta, perché il risultato è chiuso anche da una chiave del Keystore e un attacco
   * fuori dal telefono non lo vede. Circa 0,2–0,5 s su un telefono di fascia media, una volta per apertura.
   */
  const val SecretIterations: Int = 210_000
}

/** Come si chiede l'autenticazione: l'impronta (col `CryptoObject`) o il codice del telefono. */
enum class VaultAuthMethod { Biometric, DeviceCredential }

/**
 * Una richiesta di autenticazione della cassaforte all'interfaccia.
 *
 * @param cipher per [VaultAuthMethod.Biometric], il `Cipher` da passare al dialogo di sistema come `CryptoObject`: la
 *   chiave lavora solo con lui. Null per il codice del telefono.
 * @param fallback la scritta del tasto in basso del dialogo dell'impronta («Usa il codice del telefono»): toccarlo dà
 *   [VaultAuthResult.Fallback]. Null: il tasto è «Annulla».
 */
class VaultAuthRequest(
  val method: VaultAuthMethod,
  val cipher: Cipher?,
  val title: String,
  val subtitle: String? = null,
  val fallback: String? = null,
)

/** Com'è andata un'autenticazione. */
sealed interface VaultAuthResult {
  /** Riuscita: il `CryptoObject` è sbloccato, o il codice è stato scritto. */
  data object Success : VaultAuthResult

  /** La persona ha toccato il tasto della riserva (il codice del telefono, il PIN dell'app). */
  data object Fallback : VaultAuthResult

  /** Annullata (indietro, tocco fuori, «Annulla»). */
  data object Cancelled : VaultAuthResult

  /** Non riuscita, con la frase del sistema (troppi tentativi, sensore occupato). */
  data class Failed(val message: String) : VaultAuthResult

  /** Questo modo non c'è sul telefono (nessuna impronta, nessun blocco schermo). */
  data class Unavailable(val reason: VaultUnavailability) : VaultAuthResult
}

/**
 * Chi mostra il dialogo dell'impronta o del codice per la cassaforte. La cassaforte non ha interfaccia: questo lo dà
 * l'app, o `engine-security` con il dialogo di sistema già pronto (`FluidSystemAuthenticator`).
 */
fun interface VaultAuthenticator {
  /** Mostra il dialogo e aspetta l'esito. Si chiama sul thread principale. */
  suspend fun authenticate(request: VaultAuthRequest): VaultAuthResult
}

/** Le parole del dialogo per aprire una sezione: il titolo («Sblocca le note») e una riga sotto. */
data class VaultPrompt(val title: String, val subtitle: String? = null)

/** Com'è andata l'apertura di una sezione. */
sealed interface VaultUnlock {
  /**
   * Aperta. [recovered] non nullo: aperta con la riserva dopo che la chiave dell'impronta si era chiusa per quel
   * motivo; la chiave nuova è già fatta, e la prossima volta basta l'impronta.
   */
  data class Opened(val recovered: VaultInvalidation? = null) : VaultUnlock

  /** La persona ha annullato. */
  data object Cancelled : VaultUnlock

  /** La riserva è il segreto dell'app: va chiesto (il PIN dell'app) e passato a [VaultSection.unlockWithSecret]. */
  data class NeedsSecret(val invalidation: VaultInvalidation?) : VaultUnlock

  /** Il segreto dell'app non è quello giusto. */
  data object WrongSecret : VaultUnlock

  /** La sezione non si apre più ([reason]) e non c'è riserva: si ricomincia con [VaultSection.reset]. */
  data class Lost(val reason: VaultInvalidation) : VaultUnlock

  /** La protezione chiesta non c'è su questo telefono. */
  data class Unavailable(val reason: VaultUnavailability) : VaultUnlock

  /** Non riuscita, con la frase del sistema. */
  data class Failed(val message: String) : VaultUnlock
}

/** Lo stato di una sezione, da osservare. */
sealed interface VaultSectionState {
  /** Chiusa: per leggerla serve [VaultSection.unlock]. Una sezione «solo telefono» si apre da sola al primo uso. */
  data object Closed : VaultSectionState

  /** Aperta; [relocksAt] è quando si richiude da sola (in `SystemClock.elapsedRealtime`), nullo se non si richiude. */
  data class Open(val relocksAt: Long?) : VaultSectionState

  /** Non si apre più ([reason]) e non ha riserva. */
  data class Lost(val reason: VaultInvalidation) : VaultSectionState
}

/**
 * **La cassaforte** dell'app (F8.23): valori, file e DataStore cifrati con chiavi del Keystore (StrongBox quando c'è),
 * divisi in sezioni. Ogni sezione ha la sua chiave dei dati, chiusa da una chiave del Keystore, e un accesso:
 * [VaultAccess.Device] (il solo telefono) o [VaultAccess.Biometric] (l'impronta, che si richiude da sola).
 *
 * ```kotlin
 * val vault = FluidVault.get(context)
 * vault.section().values.putString("imap.password", password)           // solo questo telefono
 * val notes = vault.section("note", VaultAccess.Biometric())              // l'impronta
 * when (notes.unlock(authenticator, VaultPrompt("Sblocca le note"))) { … }
 * val sealed = vault.section("account").seal(rootKey, associated = aad)    // i byte di F10.4
 * ```
 *
 * **Niente va mai in backup**: tutto sta in `noBackupFilesDir/fluid-vault/<nome>`, che Android non copia né nel backup
 * nel cloud né nel passaggio fra telefoni. Le chiavi del Keystore comunque non si copiano: un backup che porta via i dati
 * li deve decifrare e ricifrare con la sua chiave (quella dell'account, `K_bak` di F10).
 *
 * **Fuori dal thread principale**: ogni lettura e scrittura è `suspend` e lavora su [Dispatchers.IO].
 *
 * Una per nome e per processo: [get] restituisce sempre la stessa.
 */
class FluidVault internal constructor(
  /** Il nome della cassaforte: le sue chiavi e la sua cartella. */
  val name: String,
  internal val dir: File,
  internal val keystore: VaultKeystore,
  /** Le frasi pronte: quelle di [VaultUnlock] e quelle dei dialoghi. */
  val labels: VaultLabels,
  internal val clock: () -> Long,
  internal val scope: CoroutineScope,
  internal val io: CoroutineDispatcher,
  internal val secretIterations: Int,
) {
  private val sections = LinkedHashMap<String, VaultSection>()
  internal val dataStores = HashMap<String, Any>()

  init {
    requireName(name)
  }

  /**
   * La sezione [name], con l'accesso [access]. Sempre la stessa per lo stesso nome. Una sezione nata con un accesso non
   * ne cambia: chiederla con un altro è un errore (per cambiarlo, [VaultSection.reset] e una sezione nuova).
   */
  fun section(name: String = DefaultSection, access: VaultAccess = VaultAccess.Device()): VaultSection {
    requireName(name)
    synchronized(sections) {
      sections[name]?.let { existing ->
        check(existing.access == access) { "la sezione «$name» ha già l'accesso ${existing.access}, non $access" }
        return existing
      }
      return VaultSection(this, name, access).also { sections[name] = it }
    }
  }

  /** Le sezioni chieste finora in questo processo. */
  val openedSections: List<VaultSection> get() = synchronized(sections) { sections.values.toList() }

  /** Chiude subito tutte le sezioni (quelle «solo telefono» si riapriranno da sole al prossimo uso). */
  fun lockAll() {
    openedSections.forEach { it.lock() }
  }

  /** Lo schermo si è spento: si chiudono le sezioni all'impronta e quelle «solo a telefono sbloccato». */
  internal fun onScreenOff() {
    openedSections.filter { section ->
      when (val access = section.access) {
        is VaultAccess.Biometric -> true
        is VaultAccess.Device -> access.whileUnlocked
      }
    }.forEach { it.lock() }
  }

  companion object {
    /** Il nome della sezione e della cassaforte di serie. */
    const val DefaultSection: String = "default"

    private val vaults = HashMap<String, FluidVault>()

    /**
     * La cassaforte [name] di questa app, una per processo. Al primo uso si mette in ascolto dello schermo che si spegne
     * (per chiudere le sezioni all'impronta), con un ricevitore non esportato.
     */
    fun get(context: Context, name: String = DefaultSection, labels: VaultLabels = VaultLabels()): FluidVault {
      requireName(name)
      val app = context.applicationContext ?: context
      synchronized(vaults) {
        vaults[name]?.let { return it }
        val vault = FluidVault(
          name = name,
          dir = File(app.noBackupFilesDir, "fluid-vault/$name"),
          keystore = AndroidVaultKeystore(app),
          labels = labels,
          clock = SystemClock::elapsedRealtime,
          scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
          io = Dispatchers.IO,
          secretIterations = VaultDefaults.SecretIterations,
        )
        vault.listenToScreen(app)
        vaults[name] = vault
        return vault
      }
    }

    /**
     * Una cassaforte con le parti scelte a mano: per i test ([MemoryVaultKeystore], un orologio finto, lo scope del
     * test) e per chi la vuole in una cartella sua. La cartella deve stare fuori dal backup.
     *
     * @param clock il tempo in millisecondi, monotono (di serie `SystemClock.elapsedRealtime`).
     * @param secretIterations i giri di PBKDF2 del segreto dell'app: abbassarli solo nei test.
     */
    fun create(
      dir: File,
      keystore: VaultKeystore,
      name: String = DefaultSection,
      labels: VaultLabels = VaultLabels(),
      clock: () -> Long = SystemClock::elapsedRealtime,
      scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
      io: CoroutineDispatcher = Dispatchers.IO,
      secretIterations: Int = VaultDefaults.SecretIterations,
    ): FluidVault = FluidVault(name, dir, keystore, labels, clock, scope, io, secretIterations)

    private fun requireName(name: String) {
      require(name.isNotEmpty() && name.length <= 64 && name.all { it in 'a'..'z' || it in '0'..'9' || it in "._-" }) {
        "nome non valido: «$name» (lettere minuscole, cifre, punto, trattino, trattino basso)"
      }
    }
  }

  private fun listenToScreen(context: Context) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
      }
    }
    val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
    try {
      if (Build.VERSION.SDK_INT >= 33) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        context.registerReceiver(receiver, filter)
      }
    } catch (_: RuntimeException) {
      // Un contesto che non accetta ricevitori (un test): restano il tempo e lockAll.
    }
  }
}
