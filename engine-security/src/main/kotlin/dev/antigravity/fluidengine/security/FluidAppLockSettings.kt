package dev.antigravity.fluidengine.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlin.time.Duration
import kotlinx.coroutines.launch

/** I tag dei test delle impostazioni del blocco. */
object FluidAppLockSettingsTags {
  const val Toggle = "fluid-lock:impostazioni:interruttore"
  const val Timeout = "fluid-lock:impostazioni:tempo"
  const val Pin = "fluid-lock:impostazioni:pin"
  const val PinSetup = "fluid-lock:impostazioni:scelta-pin"
}

/**
 * **La riga pronta delle impostazioni** del blocco (F8.23): un gruppo con l'interruttore «Richiedi l'impronta», la scelta
 * «Blocca» (subito, dopo 1, 5, 15 minuti, dopo un'ora) e il «PIN dell'app» (sceglierlo due volte, cambiarlo,
 * toglierlo), scritto lì nel gruppo con il campo a codice di F8.3. Senza blocco schermo né PIN l'interruttore è spento e
 * dice perché.
 *
 * Il selettore del tempo è un [FluidPickerRow]: serve un `FluidGlassModalHost` alla radice, come per ogni portale.
 *
 * @param timeouts le scelte di «Blocca»; di serie [FluidAppLockDefaults.Timeouts].
 * @param pinLength le cifre di un PIN nuovo.
 */
@Composable
fun FluidAppLockSettingsGroup(
  lock: FluidAppLock,
  modifier: Modifier = Modifier,
  appName: String = rememberAppName(),
  timeouts: List<Duration> = FluidAppLockDefaults.Timeouts,
  pinLength: Int = FluidAppLockDefaults.PinLength,
  labels: FluidSecurityLabels = LocalFluidSecurityLabels.current,
) {
  val settings by lock.settings.collectAsState()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val canEnable = remember(context, settings.hasPin) { lock.canEnable(context) }
  var editingPin by remember { mutableStateOf(false) }

  FluidListGroup(modifier) {
    FluidToggleRow(
      title = labels.settingTitle,
      subtitle = if (canEnable || settings.enabled) labels.settingSubtitle.format(appName) else labels.settingUnavailable,
      checked = settings.enabled,
      enabled = canEnable || settings.enabled,
      onCheckedChange = { on -> scope.launch { runCatching { lock.setEnabled(on) } } },
      leading = { FluidIcon(FluidIcons.Fingerprint, contentDescription = null) },
      modifier = Modifier.testTag(FluidAppLockSettingsTags.Toggle),
    )
    if (settings.enabled) {
      FluidListDivider()
      FluidPickerRow(
        title = labels.settingTimeout,
        selectedKey = settings.timeout,
        options = (timeouts + settings.timeout).distinct().sorted().map { FluidPickerOption(it, labels.timeout(it)) },
        onSelected = { chosen -> scope.launch { runCatching { lock.setTimeout(chosen) } } },
        modifier = Modifier.testTag(FluidAppLockSettingsTags.Timeout),
      )
    }
    FluidListDivider()
    FluidListRow(
      title = labels.settingPin,
      subtitle = labels.settingPinSubtitle,
      meta = if (settings.hasPin) labels.pinOn else labels.pinOff,
      leading = { FluidIcon(FluidIcons.Key, contentDescription = null) },
      onClick = { editingPin = !editingPin },
      modifier = Modifier.testTag(FluidAppLockSettingsTags.Pin),
    )
    if (editingPin) {
      FluidListDivider()
      FluidPinSetup(
        length = pinLength,
        hasPin = settings.hasPin,
        labels = labels,
        onDone = { pin ->
          scope.launch {
            try {
              runCatching { lock.setPin(pin) }
            } finally {
              pin?.fill('0')
            }
            editingPin = false
          }
        },
        onCancel = { editingPin = false },
      )
    }
  }
}

/** Sceglie un PIN in due passi (scrivilo, scrivilo di nuovo), o lo toglie. */
@Composable
private fun FluidPinSetup(
  length: Int,
  hasPin: Boolean,
  labels: FluidSecurityLabels,
  onDone: (CharArray?) -> Unit,
  onCancel: () -> Unit,
) {
  var first by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
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
    message?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
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
