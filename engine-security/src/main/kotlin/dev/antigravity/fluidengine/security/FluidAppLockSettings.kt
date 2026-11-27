package dev.antigravity.fluidengine.security

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCodeField
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidPickerOption
import dev.antigravity.fluidengine.ui.fluid.FluidPickerRow
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.icons.Fingerprint
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Key
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCodeFieldState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.vault.VaultAuthResult
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** I tag dei test delle impostazioni del blocco. */
object FluidAppLockSettingsTags {
  const val Toggle = "fluid-lock:impostazioni:interruttore"
  const val Timeout = "fluid-lock:impostazioni:tempo"
  const val Pin = "fluid-lock:impostazioni:pin"
  const val PinSetup = "fluid-lock:impostazioni:scelta-pin"

  /** Il PIN di adesso, chiesto prima di spegnere il blocco o di cambiare il PIN (R2). */
  const val PinCheck = "fluid-lock:impostazioni:pin-attuale"

  /** La riga che dice un salvataggio rifiutato dalla cassaforte (R2). */
  const val Message = "fluid-lock:impostazioni:messaggio"
}

/**
 * **La riga pronta delle impostazioni** del blocco (F8.23): un gruppo con l'interruttore «Richiedi l'impronta», la scelta
 * «Blocca» (subito, dopo 1, 5, 15 minuti, dopo un'ora) e il «PIN dell'app» (sceglierlo due volte, cambiarlo,
 * toglierlo), scritto lì nel gruppo con il campo a codice di F8.3. Senza blocco schermo né PIN l'interruttore è spento e
 * dice perché. Il blocco schermo si rilegge quando la persona torna dalle impostazioni del telefono.
 *
 * **Prima di spegnere il blocco o di cambiare e togliere il PIN si dice chi si è** (R2), come iOS per «Richiedi Face ID» e
 * per «Cambia codice»: il dialogo di sistema ([authenticator]), oppure il PIN di adesso se il telefono non ha né impronta
 * né codice. Accendere il blocco e scegliere il primo PIN non chiedono niente. Un salvataggio che la cassaforte rifiuta
 * lo dice ([FluidSecurityLabels.settingSaveFailed]), e la scelta del PIN resta aperta.
 *
 * Il selettore del tempo è un [FluidPickerRow]: serve un `FluidGlassModalHost` alla radice, come per ogni portale.
 *
 * @param timeouts le scelte di «Blocca»; di serie [FluidAppLockDefaults.Timeouts].
 * @param pinLength le cifre di un PIN nuovo.
 * @param authenticator il dialogo che conferma chi sei prima di spegnere il blocco o di cambiare il PIN; di serie quello
 *   di sistema dell'attività. Null: solo il PIN di adesso (e niente se non c'è un PIN). Dalla 4.0 (R2).
 */
@Composable
fun FluidAppLockSettingsGroup(
  lock: FluidAppLock,
  modifier: Modifier = Modifier,
  appName: String = rememberAppName(),
  timeouts: List<Duration> = FluidAppLockDefaults.Timeouts,
  pinLength: Int = FluidAppLockDefaults.PinLength,
  labels: FluidSecurityLabels = LocalFluidSecurityLabels.current,
  authenticator: FluidLockAuthenticator? = rememberFluidSystemAuthenticator(labels)?.asLockAuthenticator(),
) {
  val settings by lock.settings.collectAsState()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // Riletti a ogni ritorno: la persona può aver messo (o tolto) un blocco schermo nelle impostazioni del telefono (R2).
  val canEnable = rememberReadOnResume(context, settings.hasPin) { lock.canEnable(context) }
  val capability = rememberReadOnResume(context, null) { FluidBiometrics.capability(context) }
  var editingPin by remember { mutableStateOf(false) }
  // Una scelta del PIN rifiutata dalla cassaforte riparte da capo, con la frase dell'errore.
  var pinAttempt by remember { mutableIntStateOf(0) }
  var pinError by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  // Quello che aspetta il PIN di adesso (spegnere, cambiare il PIN) quando il dialogo di sistema non c'è.
  var pendingCheck by remember { mutableStateOf<(() -> Unit)?>(null) }

  // Una scrittura nella cassaforte: se la rifiuta, lo si dice (prima l'errore si perdeva e sembrava accettata).
  val save: (suspend () -> Unit) -> Unit = { write ->
    scope.launch {
      error = try {
        write()
        null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        labels.settingSaveFailed
      }
    }
  }
  // Prima di togliere una protezione si dice chi si è: il dialogo di sistema, o il PIN di adesso.
  val confirm: (() -> Unit) -> Unit = { action ->
    val auth = authenticator?.takeIf { capability.anyBiometrics || capability.deviceCredential }
    when {
      auth != null -> scope.launch {
        when (auth.authenticateUser(labels.confirmTitle, null, if (settings.hasPin) labels.usePin else null)) {
          VaultAuthResult.Success -> action()
          VaultAuthResult.Fallback -> if (settings.hasPin) pendingCheck = action
          else -> Unit
        }
      }
      settings.hasPin -> pendingCheck = action
      else -> action()
    }
  }

  FluidListGroup(modifier) {
    FluidToggleRow(
      title = labels.settingTitle,
      subtitle = if (canEnable || settings.enabled) labels.settingSubtitle.format(appName) else labels.settingUnavailable,
      checked = settings.enabled,
      enabled = canEnable || settings.enabled,
      onCheckedChange = { on -> if (on) save { lock.setEnabled(true) } else confirm { save { lock.setEnabled(false) } } },
      leading = { FluidIcon(FluidIcons.Fingerprint, contentDescription = null) },
      modifier = Modifier.testTag(FluidAppLockSettingsTags.Toggle),
    )
    if (settings.enabled) {
      FluidListDivider()
      FluidPickerRow(
        title = labels.settingTimeout,
        selectedKey = settings.timeout,
        options = (timeouts + settings.timeout).distinct().sorted().map { FluidPickerOption(it, labels.timeout(it)) },
        onSelected = { chosen -> save { lock.setTimeout(chosen) } },
        modifier = Modifier.testTag(FluidAppLockSettingsTags.Timeout),
      )
    }
    FluidListDivider()
    FluidListRow(
      title = labels.settingPin,
      subtitle = labels.settingPinSubtitle,
      meta = if (settings.hasPin) labels.pinOn else labels.pinOff,
      leading = { FluidIcon(FluidIcons.Key, contentDescription = null) },
      onClick = {
        when {
          editingPin -> editingPin = false
          pendingCheck != null -> pendingCheck = null
          settings.hasPin -> confirm {
            pinError = null
            editingPin = true
          }
          else -> {
            pinError = null
            editingPin = true
          }
        }
      },
      modifier = Modifier.testTag(FluidAppLockSettingsTags.Pin),
    )
    pendingCheck?.let { action ->
      FluidListDivider()
      CurrentPinStep(
        lock = lock,
        length = settings.pinLength,
        labels = labels,
        onCorrect = {
          pendingCheck = null
          action()
        },
        onCancel = { pendingCheck = null },
      )
    }
    if (editingPin) {
      FluidListDivider()
      key(pinAttempt) {
        FluidPinSetup(
          length = pinLength,
          hasPin = settings.hasPin,
          labels = labels,
          error = pinError,
          onDone = { pin ->
            scope.launch {
              val saved = try {
                lock.setPin(pin)
                true
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                false
              } finally {
                pin?.fill('0')
              }
              if (saved) {
                pinError = null
                editingPin = false
              } else {
                // Resta aperta: la persona non deve credere di avere un PIN che non c'è.
                pinError = labels.settingSaveFailed
                pinAttempt++
              }
            }
          },
          onCancel = {
            pinError = null
            editingPin = false
          },
        )
      }
    }
    error?.let { message ->
      FluidListDivider()
      Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .testTag(FluidAppLockSettingsTags.Message)
          .semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

/** Sceglie un PIN in due passi (scrivilo, scrivilo di nuovo), o lo toglie. [error]: la frase di un tentativo rifiutato. */
@Composable
private fun FluidPinSetup(
  length: Int,
  hasPin: Boolean,
  labels: FluidSecurityLabels,
  error: String?,
  onDone: (CharArray?) -> Unit,
  onCancel: () -> Unit,
) {
  var first by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf(error) }
  val code = rememberFluidCodeFieldState(length)
  Column(
    Modifier.fillMaxWidth().padding(16.dp).testTag(FluidAppLockSettingsTags.PinSetup),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      if (first == null) labels.pinChooseTitle else labels.pinConfirmTitle,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
    )
    message?.let { PinMessage(it) }
    FluidCodeField(
      state = code,
      secret = true,
      autoFocus = true,
      pasteButton = false,
      singleRow = true,
      label = if (first == null) labels.pinChooseTitle else labels.pinConfirmTitle,
      onComplete = { typed ->
        val chosen = first
        if (chosen == null) {
          first = typed
          message = null
          code.clear()
        } else if (chosen == typed) {
          onDone(typed.toCharArray())
        } else {
          first = null
          message = labels.pinMismatch
          code.fail()
        }
      },
    )
    if (hasPin) {
      FluidButton(text = labels.pinRemove, onClick = { onDone(null) }, style = FluidButtonStyle.Destructive)
    }
    FluidButton(text = labels.cancel, onClick = onCancel, style = FluidButtonStyle.Plain)
  }
}

/**
 * Il PIN di adesso, prima di spegnere il blocco o di cambiare il PIN (R2), quando il telefono non ha impronta né codice:
 * gli stessi errori e le stesse attese della schermata di blocco ([FluidAppLock.checkPin]), così non si prova un PIN dopo
 * l'altro da qui.
 */
@Composable
private fun CurrentPinStep(
  lock: FluidAppLock,
  length: Int,
  labels: FluidSecurityLabels,
  onCorrect: () -> Unit,
  onCancel: () -> Unit,
) {
  var message by remember { mutableStateOf<String?>(null) }
  var waitUntil by remember { mutableStateOf<Long?>(null) }
  val code = rememberFluidCodeFieldState(length)
  val scope = rememberCoroutineScope()
  // Fine dell'attesa dopo troppi PIN sbagliati: un solo risveglio, come nella schermata di blocco.
  LaunchedEffect(waitUntil) {
    val until = waitUntil ?: return@LaunchedEffect
    delay((until - SystemClock.elapsedRealtime()).coerceAtLeast(0))
    waitUntil = null
    message = null
  }
  Column(
    Modifier.fillMaxWidth().padding(16.dp).testTag(FluidAppLockSettingsTags.PinCheck),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(labels.pinCurrentTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    message?.let { PinMessage(it) }
    FluidCodeField(
      state = code,
      secret = true,
      autoFocus = true,
      enabled = waitUntil == null,
      pasteButton = false,
      singleRow = true,
      label = labels.pinCurrentTitle,
      onComplete = { typed ->
        scope.launch {
          val pin = typed.toCharArray()
          val result = try {
            lock.checkPin(pin)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            code.fail()
            message = labels.authenticationFailed
            return@launch
          } finally {
            pin.fill('0')
          }
          when (result) {
            FluidPinCheck.Correct -> {
              code.clear()
              onCorrect()
            }
            is FluidPinCheck.Wrong -> {
              code.fail()
              message = result.waitUntil?.let { labels.pinWaitText(it - SystemClock.elapsedRealtime()) } ?: labels.pinWrong
              waitUntil = result.waitUntil
            }
            is FluidPinCheck.Wait -> {
              code.fail()
              message = labels.pinWaitText(result.until - SystemClock.elapsedRealtime())
              waitUntil = result.until
            }
            // Il PIN non c'è più: non c'è niente da chiedere.
            FluidPinCheck.NoPin -> onCorrect()
          }
        }
      },
    )
    FluidButton(text = labels.cancel, onClick = onCancel, style = FluidButtonStyle.Plain)
  }
}

@Composable
private fun PinMessage(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    textAlign = TextAlign.Center,
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}
