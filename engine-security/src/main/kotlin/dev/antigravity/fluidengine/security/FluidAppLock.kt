package dev.antigravity.fluidengine.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.VaultAccess
import dev.antigravity.fluidengine.vault.VaultSection
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo stato del blocco dell'app. */
sealed interface FluidAppLockState {
  /** Le impostazioni non sono ancora lette: l'app non si mostra (un attimo, all'avvio). */
  data object Loading : FluidAppLockState

  /** Aperta. */
  data object Unlocked : FluidAppLockState

  /** Bloccata: si vede la schermata di blocco, il contenuto non si disegna e TalkBack non lo legge. */
  data object Locked : FluidAppLockState
}

/**
 * Le impostazioni del blocco, quelle della riga pronta ([FluidAppLockSettingsGroup]).
 *
 * @param timeout dopo quanto fuori dall'app, al rientro, si blocca (zero: subito, come «Richiedi Face ID»).
 * @param hasPin vero se c'è un PIN dell'app, la riserva quando l'impronta e il codice del telefono non ci sono.
 */
data class FluidAppLockSettings(
  val enabled: Boolean = false,
  val timeout: Duration = Duration.ZERO,
  val hasPin: Boolean = false,
  val pinLength: Int = FluidAppLockDefaults.PinLength,
)

/** Com'è andato un PIN dell'app. */
sealed interface FluidPinCheck {
  /** Giusto: l'app si apre. */
  data object Correct : FluidPinCheck

  /** Sbagliato, per la [failures]-esima volta di fila; [waitUntil] non nullo: prima di riprovare si aspetta. */
  data class Wrong(val failures: Int, val waitUntil: Long?) : FluidPinCheck

  /** Troppi tentativi: si riprova da [until] (in `SystemClock.elapsedRealtime`). */
  data class Wait(val until: Long) : FluidPinCheck

  /** Non c'è un PIN dell'app. */
  data object NoPin : FluidPinCheck
}

/**
 * **Il blocco dell'app** (F8.23, `blocco-biometrico`): l'app si blocca quando ci torni dopo il tempo scelto, e si
 * sblocca con l'impronta, il volto o il codice del telefono, o con il PIN dell'app come riserva. È «Richiedi Face ID»
 * di iOS 18: l'interfaccia è [FluidAppLockHost] (la schermata di vetro, il dialogo di sistema, l'anteprima oscurata
 * nelle recenti), le impostazioni sono la riga pronta [FluidAppLockSettingsGroup].
 *
 * **Quando non blocca.** Mai durante una condivisione o una scelta di file: prima di aprirle si chiama
 * [allowLeaving] (o si usa [rememberFluidLockSafeLauncher]), e il rientro da lì non blocca. Mai per una rotazione o un
 * cambio di configurazione. Mai per un pannello di sistema che non copre l'app (le notifiche, il dialogo
 * dell'impronta).
 *
 * **Dove sta.** Le impostazioni e il PIN (PBKDF2, mai in chiaro) stanno in una sezione «solo telefono» della cassaforte,
 * fuori dal backup: su un telefono nuovo il blocco riparte spento.
 *
 * **Il blocco è una porta, non una cassaforte**: chi ha i file dell'app li legge comunque. I dati che contano vanno in
 * una sezione all'impronta di `engine-vault` ([lockVaultOnLock] la richiude insieme all'app).
 *
 * Uno per app, creato all'avvio (nell'`Application` o in un singleton): tiene il conto delle attività aperte da solo.
 *
 * @param lockVaultOnLock quando l'app si blocca si richiudono anche le sezioni della cassaforte.
 */
class FluidAppLock internal constructor(
  private val section: VaultSection,
  private val vault: FluidVault?,
  private val clock: () -> Long,
  private val scope: CoroutineScope,
  private val pinIterations: Int,
  private val lockVaultOnLock: Boolean,
  private val wallClock: () -> Long = System::currentTimeMillis,
) {
  /**
   * Il blocco dell'app [context], con le impostazioni nella cassaforte [vault] (sezione `fluid-lock-<name>`). [name]
   * distingue due blocchi nella stessa app (la galleria ne fa uno per scena).
   */
  constructor(
    context: Context,
    vault: FluidVault = FluidVault.get(context),
    name: String = "app",
    lockVaultOnLock: Boolean = true,
  ) : this(
    section = vault.section("fluid-lock-$name", VaultAccess.Device()),
    vault = vault,
    clock = SystemClock::elapsedRealtime,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    pinIterations = FluidAppLockDefaults.PinIterations,
    lockVaultOnLock = lockVaultOnLock,
  ) {
    FluidAppForeground.attach(context, this)
  }

  private val _state = MutableStateFlow<FluidAppLockState>(FluidAppLockState.Loading)
  private val _settings = MutableStateFlow(FluidAppLockSettings())
  private var hiddenAt: Long? = null
  private var leaveUntil: Long = Long.MIN_VALUE
  private var leftSinceAllowance = false

  /** Bloccata, aperta, o ancora da leggere. */
  val state: StateFlow<FluidAppLockState> = _state.asStateFlow()

  /** Le impostazioni di adesso. */
  val settings: StateFlow<FluidAppLockSettings> = _settings.asStateFlow()

  /** Quante volte l'app si è bloccata (per i test e per far ripartire il dialogo automatico). */
  var lockCount: Int = 0
    private set

  init {
    scope.launch { load() }
  }

  private suspend fun load() {
    val loaded = try {
      val v = section.values
      FluidAppLockSettings(
        enabled = v.getString(KeyEnabled) == "1",
        timeout = (v.getString(KeyTimeout)?.toLongOrNull() ?: 0L).milliseconds,
        hasPin = v.contains(KeyPin),
        pinLength = v.getString(KeyPinLength)?.toIntOrNull() ?: FluidAppLockDefaults.PinLength,
      )
    } catch (e: Exception) {
      // La cassaforte non si legge (Keystore azzerato): il blocco riparte spento invece di chiudere fuori la persona.
      FluidAppLockSettings()
    }
    _settings.value = loaded
    if (_state.value == FluidAppLockState.Loading) {
      if (loaded.enabled) engage() else _state.value = FluidAppLockState.Unlocked
    }
  }

  /** Vero se il blocco si può accendere: c'è un blocco schermo sicuro o un PIN dell'app. */
  fun canEnable(context: Context): Boolean = FluidBiometrics.capability(context).deviceCredential || settings.value.hasPin

  /** Accende o spegne il blocco. Acceso, l'app resta aperta adesso: si blocca al prossimo rientro. */
  suspend fun setEnabled(enabled: Boolean) {
    section.values.putString(KeyEnabled, if (enabled) "1" else "0")
    _settings.value = _settings.value.copy(enabled = enabled)
    if (!enabled) _state.value = FluidAppLockState.Unlocked
  }

  /** Dopo quanto fuori, al rientro, l'app si blocca. */
  suspend fun setTimeout(timeout: Duration) {
    require(!timeout.isNegative()) { "tempo negativo" }
    section.values.putString(KeyTimeout, timeout.inWholeMilliseconds.toString())
    _settings.value = _settings.value.copy(timeout = timeout)
  }

  /**
   * Imposta il PIN dell'app ([pin], solo cifre, 4–8), o lo toglie (null). Si tiene solo la sua impronta (PBKDF2 con un
   * sale), mai il PIN. Chi lo passa lo azzera dopo.
   */
  suspend fun setPin(pin: CharArray?) {
    val values = section.values
    if (pin == null) {
      values.remove(KeyPin)
      values.remove(KeyPinLength)
      _settings.value = _settings.value.copy(hasPin = false)
      return
    }
    require(pin.size in 4..8 && pin.all { it in '0'..'9' }) { "il PIN dell'app sono da 4 a 8 cifre" }
    val salt = FluidPinHash.salt()
    val hash = FluidPinHash.hash(pin, salt, pinIterations)
    values.putBytes(KeyPin, FluidPinHash.encode(salt, pinIterations, hash))
    values.putString(KeyPinLength, pin.size.toString())
    values.remove(KeyFailures)
    values.remove(KeyLastFailure)
    _settings.value = _settings.value.copy(hasPin = true, pinLength = pin.size)
  }

  /**
   * Controlla il PIN dell'app. Giusto: l'app si apre. Sbagliato: conta, e dal quinto errore di fila fa aspettare come
   * iPhone ([FluidAppLockDefaults.pinDelay]). Chi passa [pin] lo azzera dopo.
   */
  suspend fun checkPin(pin: CharArray): FluidPinCheck {
    val values = section.values
    val stored = values.getBytes(KeyPin) ?: return FluidPinCheck.NoPin
    val failures = values.getString(KeyFailures)?.toIntOrNull() ?: 0
    val lastFailure = values.getString(KeyLastFailure)?.let(::decodeFailureTime)
    FluidAppLockRules.pinWaitUntil(failures, lastFailure, clock())?.let { return FluidPinCheck.Wait(it) }
    val correct = FluidPinHash.verify(pin, stored)
    stored.fill(0)
    if (correct) {
      values.remove(KeyFailures)
      values.remove(KeyLastFailure)
      unlock()
      return FluidPinCheck.Correct
    }
    val now = clock()
    val next = failures + 1
    values.putString(KeyFailures, next.toString())
    values.putString(KeyLastFailure, encodeFailureTime(now))
    return FluidPinCheck.Wrong(next, FluidAppLockRules.pinWaitUntil(next, now, now))
  }

  /** Blocca subito (un tasto «Blocca ora», o lo schermo che si spegne con il tempo «Subito»). */
  fun lock() {
    if (!_settings.value.enabled) return
    engage()
  }

  /** Apre l'app: dopo un'autenticazione riuscita (il dialogo di sistema, il PIN). */
  fun unlock() {
    _state.value = FluidAppLockState.Unlocked
  }

  /**
   * La prossima uscita dall'app non la blocca: si chiama prima di aprire una condivisione, una scelta di file, la
   * fotocamera di sistema. Il permesso vale per un rientro solo e al più per [within].
   */
  fun allowLeaving(within: Duration = FluidAppLockDefaults.LeaveAllowance) {
    leaveUntil = clock() + within.inWholeMilliseconds
    leftSinceAllowance = false
  }

  /** Toglie il permesso di [allowLeaving] prima del tempo (l'uscita non c'è stata). */
  fun cancelLeaving() {
    leaveUntil = Long.MIN_VALUE
  }

  /** Stacca il blocco dal conto delle attività: per un blocco che vive meno dell'app (una scena della galleria). */
  fun close() {
    FluidAppForeground.detach(this)
    scope.cancel()
  }

  // --- il ciclo di vita -------------------------------------------------------------------------------------------

  internal fun onAppHidden() {
    hiddenAt = clock()
    if (leaveUntil != Long.MIN_VALUE) leftSinceAllowance = true
  }

  internal fun onAppShown() {
    val at = hiddenAt ?: return
    hiddenAt = null
    val now = clock()
    val allowed = leftSinceAllowance && now <= leaveUntil
    if (leftSinceAllowance) leaveUntil = Long.MIN_VALUE
    leftSinceAllowance = false
    val s = _settings.value
    if (_state.value == FluidAppLockState.Unlocked &&
      FluidAppLockRules.shouldLockOnReturn(s.enabled, now - at, s.timeout, allowed)
    ) {
      engage()
    }
  }

  /** L'attività è tornata in primo piano senza essere uscita: un permesso di uscire non usato si toglie. */
  internal fun onAppResumed() {
    // Il permesso c'era ma l'app non è uscita (un pannello che la copre solo in parte, un dialogo): non deve valere
    // per un'uscita vera più tardi.
    if (leaveUntil != Long.MIN_VALUE && !leftSinceAllowance) leaveUntil = Long.MIN_VALUE
  }

  private fun engage() {
    lockCount++
    _state.value = FluidAppLockState.Locked
    if (lockVaultOnLock) vault?.lockAll()
  }

  private fun encodeFailureTime(now: Long): String = "$now:${bootMarker()}"

  /** Il tempo dell'ultimo errore, o «adesso» se il telefono si è riavviato da allora (si aspetta da capo). */
  private fun decodeFailureTime(text: String): Long? {
    val parts = text.split(':')
    val at = parts.getOrNull(0)?.toLongOrNull() ?: return null
    val boot = parts.getOrNull(1)?.toLongOrNull() ?: return clock()
    return if (kotlin.math.abs(boot - bootMarker()) > 60_000) clock() else at
  }

  // L'ora dell'accensione: stabile finché il telefono non si riavvia (o qualcuno sposta l'orologio, e allora si aspetta
  // da capo: mai di meno).
  private fun bootMarker(): Long = wallClock() - clock()

  internal companion object {
    const val KeyEnabled = "enabled"
    const val KeyTimeout = "timeout-ms"
    const val KeyPin = "pin"
    const val KeyPinLength = "pin-length"
    const val KeyFailures = "pin-failures"
    const val KeyLastFailure = "pin-last-failure"

    /** Per i test: un blocco con la sezione, l'orologio e lo scope scelti a mano, fuori dal conto delle attività. */
    fun forTests(
      section: VaultSection,
      clock: () -> Long,
      scope: CoroutineScope,
      pinIterations: Int = 1_000,
      vault: FluidVault? = null,
    ): FluidAppLock = FluidAppLock(section, vault, clock, scope, pinIterations, lockVaultOnLock = vault != null, wallClock = clock)

    /** Tutti i blocchi vivi: un dialogo di sistema che copre l'app non deve bloccarla al ritorno. */
    internal fun allowLeavingForSystemUi(within: Duration) {
      FluidAppForeground.locks().forEach { it.allowLeaving(within) }
    }

    internal fun cancelLeavingForSystemUi() {
      FluidAppForeground.locks().forEach { it.cancelLeaving() }
    }
  }
}

/**
 * Il conto delle attività aperte dell'app, per sapere quando esce e quando torna, come `ProcessLifecycleOwner` ma
 * senza la libreria: un cambio di configurazione (la rotazione) non conta come uscita.
 */
internal object FluidAppForeground : Application.ActivityLifecycleCallbacks {
  private var installedOn: Application? = null
  private var started = 0
  private var changingConfiguration = false
  private val locks = CopyOnWriteArrayList<WeakReference<FluidAppLock>>()

  fun attach(context: Context, lock: FluidAppLock) {
    val app = context.applicationContext as? Application ?: (context as? Activity)?.application
    if (app != null && installedOn !== app) {
      installedOn?.unregisterActivityLifecycleCallbacks(this)
      app.registerActivityLifecycleCallbacks(this)
      installedOn = app
      // Un'attività già aperta (il blocco nasce dopo onStart): conta come in primo piano.
      started = if (context is Activity) 1 else started
    }
    locks += WeakReference(lock)
  }

  fun detach(lock: FluidAppLock) {
    locks.removeAll { it.get() == null || it.get() === lock }
  }

  fun locks(): List<FluidAppLock> = locks.mapNotNull { it.get() }

  /** Per i test: il conto da capo. */
  internal fun resetForTests() {
    installedOn?.unregisterActivityLifecycleCallbacks(this)
    installedOn = null
    started = 0
    changingConfiguration = false
    locks.clear()
  }

  override fun onActivityStarted(activity: Activity) {
    started++
    if (started == 1) {
      if (changingConfiguration) changingConfiguration = false else locks().forEach { it.onAppShown() }
    }
  }

  override fun onActivityStopped(activity: Activity) {
    started = (started - 1).coerceAtLeast(0)
    if (started == 0) {
      if (activity.isChangingConfigurations) changingConfiguration = true else locks().forEach { it.onAppHidden() }
    }
  }

  override fun onActivityResumed(activity: Activity) {
    locks().forEach { it.onAppResumed() }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit
}
