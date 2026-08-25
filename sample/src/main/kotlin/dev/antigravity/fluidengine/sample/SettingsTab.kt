package dev.antigravity.fluidengine.sample

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.sample.update.fluidGlassUpdater
import kotlinx.coroutines.launch
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/** Tab four: what a settings page looks like once the content is made of the same material. */
@Composable
internal fun SettingsTab(bottomInset: Dp) {
  var amoled by remember { mutableStateOf(false) }
  var notifications by remember { mutableStateOf(true) }
  var theme by remember { mutableStateOf("Sistema") }

  FluidScreen(
    title = "Impostazioni",
    subtitle = "Canvas ambientale, contenuto in vetro, e i crediti.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards)
    },
  ) {
    item(key = "appearance-header") { FluidSectionHeader(title = "Aspetto") }
    item(key = "appearance") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf("Chiaro", "Scuro", "Sistema"),
          selected = theme,
          onSelect = { theme = it },
          label = { it },
        )
      }
    }
    item(key = "toggles") {
      FluidListGroup(glass = true) {
        SwitchRow("Nero assoluto", amoled) { amoled = it }
        FluidListDivider()
        SwitchRow("Notifiche", notifications) { notifications = it }
      }
    }
    item(key = "about-header") { FluidSectionHeader(title = "Informazioni") }
    item(key = "about") {
      UpdatesAndAboutGroup()
    }
    // I crediti stanno qui apposta: sono la stessa sezione che ogni app deve mettere nella propria
    // pagina "informazioni", e vale la pena guardarla girare sopra il canvas.
    fluidLicensesSection()
  }
}

/**
 * Versione, aggiornamento in-app e sorgente, in un gruppo solo.
 *
 * L'aggiornamento legge lo stesso manifest.json che il Pampa Store pubblica: un tocco controlla,
 * il tocco successivo installa. Le decisioni di cortesia (quando controllare da soli, quali
 * versioni ignorare) qui non esistono: questa è la galleria del motore, non un client di posta.
 */
@Composable
private fun UpdatesAndAboutGroup() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val updater = remember { fluidGlassUpdater(context) }
  var status by remember { mutableStateOf("Tocca per controllare") }
  var available by remember { mutableStateOf<AvailableAppUpdate?>(null) }
  var busy by remember { mutableStateOf(false) }

  FluidListGroup(glass = true) {
    FluidListRow(title = "Versione", subtitle = "Fluid Glass ${appVersionName()}")
    FluidListDivider()
    FluidListRow(
      title = "Aggiornamenti",
      subtitle = status,
      onClick = {
        if (busy) return@FluidListRow
        val update = available
        scope.launch {
          busy = true
          if (update == null) {
            status = "Controllo…"
            updater.check(currentVersionName = appVersion(context))
              .onSuccess { found ->
                if (found == null) {
                  status = "Sei all'ultima versione"
                } else {
                  available = found
                  status = "Disponibile ${found.version} — tocca per installare"
                }
              }
              .onFailure { status = "Controllo non riuscito: sei offline?" }
          } else {
            updater.install(update).collect { state ->
              status = when (state) {
                is AppUpdateInstallState.Downloading ->
                  "Scarico… ${(state.progress * 100).toInt()}%"
                is AppUpdateInstallState.Verifying -> state.message
                is AppUpdateInstallState.Installing -> state.message
                is AppUpdateInstallState.AwaitingUserAction -> state.message
                is AppUpdateInstallState.Installed -> "Installata: riapri l'app"
                is AppUpdateInstallState.Error -> "Errore: ${state.message}"
              }
            }
            available = null
          }
          busy = false
        }
      },
    )
    FluidListDivider()
    FluidListRow(title = "Sorgente", subtitle = "github.com/Casual76/fluid-engine", onClick = {})
  }
}

/** La versione dichiarata dal pacchetto installato: quella vera, non una scritta a mano. */
@Composable
private fun appVersionName(): String {
  val context = LocalContext.current
  return remember(context) { appVersion(context) }
}

private fun appVersion(context: android.content.Context): String = runCatching {
  context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
