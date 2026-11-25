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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidPrivateGate
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.vault.VaultAccess
import dev.antigravity.fluidengine.vault.VaultAuthResult
import dev.antigravity.fluidengine.vault.VaultAuthenticator
import dev.antigravity.fluidengine.vault.VaultPrompt
import dev.antigravity.fluidengine.vault.VaultReserve
import dev.antigravity.fluidengine.vault.VaultSection
import dev.antigravity.fluidengine.vault.VaultSectionState
import dev.antigravity.fluidengine.vault.VaultUnlock
import kotlinx.coroutines.launch

/** I tag dei test del contenuto protetto. */
object FluidLockedContentTags {
  const val Placeholder = "fluid-locked:chiuso"
  const val View = "fluid-locked:visualizza"
  const val Message = "fluid-locked:messaggio"
}

/**
 * **Il contenuto protetto** (F8.23): una cartella, un album, le note, che restano chiusi finché non li apri. Chiuso, al
 * posto di [content] c'è il segno di iOS (il lucchetto, «Album nascosto è bloccato», «Visualizza»), e il contenuto non
 * si compone: non si disegna, non lo legge TalkBack, non passa in uno screenshot.
 *
 * È il pezzo generico: chi apre lo decide [onView] (un dialogo, un PIN). Per un contenuto cifrato c'è
 * [FluidVaultContent], che si apre e si richiude con una sezione della cassaforte.
 *
 * @param title il nome di quello che è chiuso, nella frase «%1$s è bloccato» ([FluidSecurityLabels.contentLockedTitle]).
 * @param message una frase al posto della riga sotto il titolo (un errore, l'impronta cambiata).
 */
@Composable
fun FluidLockedContent(
  unlocked: Boolean,
  title: String,
  onView: () -> Unit,
  modifier: Modifier = Modifier,
  message: String? = null,
  labels: FluidSecurityLabels = LocalFluidSecurityLabels.current,
  content: @Composable () -> Unit,
) {
  if (unlocked) {
    content()
    return
  }
  Column(
    modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp).testTag(FluidLockedContentTags.Placeholder),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    FluidIcon(FluidIcons.Lock, contentDescription = null, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      labels.contentLockedTitle.format(title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { heading() },
    )
    Text(
      message ?: labels.contentLockedSubtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag(FluidLockedContentTags.Message).semantics { liveRegion = LiveRegionMode.Polite },
    )
    FluidButton(
      text = labels.contentView,
      onClick = onView,
      style = FluidButtonStyle.Tinted,
      modifier = Modifier.testTag(FluidLockedContentTags.View),
    )
  }
}

/**
 * Il contenuto di una **sezione all'impronta** della cassaforte: chiuso finché la sezione è chiusa, aperto dopo
 * «Visualizza» (il dialogo dell'impronta, poi la riserva), e di nuovo chiuso quando la sezione si richiude da sola
 * (dopo qualche minuto senza usarla, allo schermo spento, al blocco dell'app). Un'impronta aggiunta dà la frase pronta
 * della cassaforte e passa alla riserva; con il PIN dell'app come riserva, [onNeedsSecret] lo chiede.
 *
 * @param authenticator il dialogo; di serie quello di sistema ([rememberFluidSystemAuthenticator]).
 */
@Composable
fun FluidVaultContent(
  section: VaultSection,
  title: String,
  modifier: Modifier = Modifier,
  prompt: VaultPrompt = VaultPrompt(title = LocalFluidSecurityLabels.current.promptTitle.format(title)),
  authenticator: VaultAuthenticator? = rememberFluidSystemAuthenticator(),
  onNeedsSecret: (() -> Unit)? = null,
  labels: FluidSecurityLabels = LocalFluidSecurityLabels.current,
  content: @Composable () -> Unit,
) {
  val state by section.state.collectAsState()
  val scope = rememberCoroutineScope()
  var message by remember { mutableStateOf<String?>(null) }
  val reserve = (section.access as? VaultAccess.Biometric)?.reserve ?: VaultReserve.None
  FluidLockedContent(
    unlocked = state is VaultSectionState.Open,
    title = title,
    message = message,
    modifier = modifier,
    labels = labels,
    onView = {
      val auth = authenticator ?: return@FluidLockedContent
      scope.launch {
        val result = section.unlock(auth, prompt)
        message = section.labels.message(result, reserve)
        if (result is VaultUnlock.NeedsSecret) onNeedsSecret?.invoke()
      }
    },
    content = content,
  )
}

/**
 * Il cancello dei valori riservati (`Modifier.fluidPrivate` di `engine-ui`) con il dialogo di sistema: il valore si
 * scopre solo dopo l'impronta, il volto o il codice del telefono. Da mettere in `LocalFluidPrivateGate`.
 */
@Composable
fun rememberFluidBiometricGate(
  title: String,
  authenticator: FluidLockAuthenticator? = rememberFluidSystemAuthenticator()?.asLockAuthenticator(),
): FluidPrivateGate? {
  val auth = authenticator ?: return null
  return remember(auth, title) { FluidPrivateGate { auth.authenticateUser(title, null, null) == VaultAuthResult.Success } }
}
