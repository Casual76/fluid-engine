package dev.antigravity.fluidengine.sample.catalog

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.sample.rememberMatrixFlag
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdown
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.rememberFluidUndoCountdownState
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme

internal const val SendDraftText = "Ci vediamo domani."
private const val InboxRoute = "send-inbox"
private const val ComposerRoute = "send-composer"
private const val NewMailId = "new-mail"
private const val UndoDurationMillis = 8_000L

/** La bozza e la spedizione simulata sono della galleria. L'engine conosce il tasto e il suo tempo. */
@Composable
internal fun NotificationSendDemo(onBack: () -> Unit = {}) {
  val nav = rememberFluidNavController()
  val onBackNow = rememberUpdatedState(onBack)
  var dark by rememberMatrixFlag { it.theme.isDark }
  var draft by rememberSaveable { mutableStateOf(SendDraftText) }
  var startedAt by rememberSaveable { mutableLongStateOf(-1L) }
  val timing = remember(startedAt) {
    if (startedAt < 0L) null else FluidNotificationCountdown(UndoDurationMillis, startedAt)
  }
  val countdown = timing?.let { rememberFluidUndoCountdownState(it) }
  val pending = countdown?.isActive == true
  LaunchedEffect(pending, timing) {
    if (timing != null && !pending) {
      startedAt = -1L
      draft = ""
    }
  }
  val countdownNow = rememberUpdatedState(countdown)

  FluidTheme(matrixDemoSettings(dark)) {
    FluidNavHost(nav, startDestination = InboxRoute, modifier = Modifier.fillMaxSize()) {
      composable(InboxRoute) {
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val showCounter = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        val undo = countdownNow.value.takeIf { showCounter }?.let {
          FluidUndoCountdown(it, onUndo = {
            startedAt = -1L
            nav.navigate(ComposerRoute) { launchSingleTop = true }
          }, accessibilityLabel = "Annulla invio")
        }
        // L'ID resta lo stesso: rememberFluidToolbarTransitionSource(NewMailId) risolve anche la faccia Annulla.
        FluidScreen(title = "Posta in arrivo", onBack = { onBackNow.value() }, toolbar = {
          primary(NewMailId, FluidIcons.Add, "Nuova mail", onClick = {
            nav.navigate(ComposerRoute) { launchSingleTop = true }
          }, undo = undo)
        }) {
          item {
            FluidListGroup {
              FluidListRow(title = "Riunione di domani", subtitle = "Martina · Conferma dell'orario", disclosure = false)
              FluidListDivider()
              FluidListRow(title = "Foto della gita", subtitle = "Giorgio · Due allegati", disclosure = false)
              FluidListDivider()
              FluidListRow(title = "Appunti", subtitle = "Elena · Il riepilogo della settimana", disclosure = false)
            }
          }
          item { FluidButton("Chiaro / scuro", onClick = { dark = !dark }) }
        }
      }
      composable(ComposerRoute) {
        FluidScreen(title = "Nuova mail", onBack = { nav.popBackStack() }, toolbar = {
          primary("send", FluidIcons.Send, "Invia", enabled = draft.isNotBlank(), onClick = {
            startedAt = SystemClock.elapsedRealtime()
            nav.popBackStack()
          })
        }) {
          item {
            FluidTextField(
              value = draft,
              onValueChange = { draft = it },
              modifier = Modifier.fillMaxWidth(),
              label = "Messaggio",
            )
          }
        }
      }
    }
  }
}
