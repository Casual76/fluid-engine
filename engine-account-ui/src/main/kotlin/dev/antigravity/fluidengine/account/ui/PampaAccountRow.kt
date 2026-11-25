package dev.antigravity.fluidengine.account.ui

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.account.AccountSections
import dev.antigravity.fluidengine.account.AccountState
import dev.antigravity.fluidengine.account.PampaAccount
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidAvatarDefaults
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * Le parole della riga «Account Pampa» (`b\architettura.md` § 2.4). Di serie in italiano; un'app in un'altra lingua
 * passa le sue. Sono di base: la grafica e le parole finali arrivano con F10.11.
 */
data class PampaAccountLabels(
  val title: String = "Account Pampa",
  val noStore: String = "L'Account Pampa si usa con Pampa Store",
  val storeTooOld: String = "Aggiorna Pampa Store per usare l'account",
  val signedOut: String = "Accedi con Pampa Store",
  val signedIn: String = "Account Pampa",
  /** Accanto al nome, se la sessione è nata dal codice di recupero e non è ancora normale. */
  val recoverySession: String = "Entrato col codice di recupero",
)

/** Cosa fa un tocco sulla riga, secondo lo stato. */
enum class PampaAccountAction {
  /** Lo Store non c'è: la sua pagina di rilascio. */
  DOWNLOAD_STORE,
  /** Lo Store è vecchio: la sua pagina, per aggiornarlo. */
  UPDATE_STORE,
  /** Nessuno è entrato: lo Store sulla pagina d'accesso. */
  SIGN_IN,
  /** Dentro: lo Store sulla pagina dell'account. */
  OPEN_ACCOUNT,
}

/** Quello che mostra la riga, ricavato dallo stato senza Compose: titolo, sottotitolo, nome per l'avatar, azione. */
data class PampaAccountRowModel(val title: String, val subtitle: String, val avatarName: String, val avatarKey: String, val action: PampaAccountAction)

/** La riga per [state]. Una funzione pura: le prove la controllano stato per stato. */
fun pampaAccountRowModel(state: AccountState, labels: PampaAccountLabels = PampaAccountLabels()): PampaAccountRowModel = when (state) {
  AccountState.NoStore -> PampaAccountRowModel(labels.title, labels.noStore, "", "pampa", PampaAccountAction.DOWNLOAD_STORE)
  is AccountState.StoreTooOld -> PampaAccountRowModel(labels.title, labels.storeTooOld, "", "pampa", PampaAccountAction.UPDATE_STORE)
  AccountState.SignedOut -> PampaAccountRowModel(labels.title, labels.signedOut, "", "pampa", PampaAccountAction.SIGN_IN)
  is AccountState.SignedIn -> PampaAccountRowModel(
    title = state.profile.displayName.ifBlank { labels.title },
    subtitle = if (state.profile.recoverySession) labels.recoverySession else labels.signedIn,
    avatarName = state.profile.displayName,
    avatarKey = state.profile.id.ifBlank { state.profile.displayName },
    action = PampaAccountAction.OPEN_ACCOUNT,
  )
}

/**
 * L'avatar dell'account: le iniziali del nome su un colore che viene dall'id dell'account (lo stesso in ogni app), o il
 * glifo di una persona quando nessuno è entrato. È [FluidAvatar] dell'engine: nessuna crittografia, nessuna chiave
 * (progetto, T10).
 */
@Composable
fun PampaAccountAvatar(state: AccountState, modifier: Modifier = Modifier, size: Dp = FluidAvatarDefaults.Size) {
  val model = pampaAccountRowModel(state)
  FluidAvatar(name = model.avatarName, key = model.avatarKey, modifier = modifier, size = size)
}

/**
 * La riga «Account Pampa» per le impostazioni di un'app, nei quattro stati di [AccountState]:
 * - `NoStore`: «L'Account Pampa si usa con Pampa Store», il tocco porta alla pagina di rilascio dello Store;
 * - `StoreTooOld`: «Aggiorna Pampa Store per usare l'account»;
 * - `SignedOut`: «Accedi con Pampa Store», che apre lo Store sulla pagina giusta;
 * - `SignedIn`: «Alessio · Account Pampa», con l'avatar.
 *
 * È una riga di gruppo dell'engine ([FluidListRow]): sta dentro un `FluidListGroup` come le altre.
 */
@Composable
fun PampaAccountRow(
  state: AccountState,
  onAction: (PampaAccountAction) -> Unit,
  modifier: Modifier = Modifier,
  labels: PampaAccountLabels = PampaAccountLabels(),
) {
  val model = pampaAccountRowModel(state, labels)
  FluidListRow(
    title = model.title,
    subtitle = model.subtitle,
    modifier = modifier,
    leading = { FluidAvatar(name = model.avatarName, key = model.avatarKey) },
    leadingTile = false,
    onClick = { onAction(model.action) },
  )
}

/**
 * La riga già collegata a [PampaAccount]: legge lo stato, lo rilegge quando la riga compare, e al tocco apre lo Store
 * sulla pagina giusta. Senza Store chiama [onDownloadStore] (l'indirizzo della pagina di rilascio lo sa l'app, R8).
 */
@Composable
fun PampaAccountRow(
  onDownloadStore: () -> Unit,
  modifier: Modifier = Modifier,
  labels: PampaAccountLabels = PampaAccountLabels(),
) {
  val context = LocalContext.current
  val state by PampaAccount.state(context).collectAsState()
  LaunchedEffect(Unit) { PampaAccount.refresh(context) }
  PampaAccountRow(state, modifier = modifier, labels = labels, onAction = { action ->
    val section = when (action) {
      PampaAccountAction.DOWNLOAD_STORE -> null
      PampaAccountAction.UPDATE_STORE -> AccountSections.ACCOUNT
      PampaAccountAction.SIGN_IN -> AccountSections.SIGN_IN
      PampaAccountAction.OPEN_ACCOUNT -> AccountSections.ACCOUNT
    }
    val intent = section?.let { PampaAccount.storeIntent(context, it) }
    if (intent == null) {
      onDownloadStore()
    } else {
      try {
        context.startActivity(intent)
      } catch (_: ActivityNotFoundException) {
        onDownloadStore()
      }
    }
  })
}
