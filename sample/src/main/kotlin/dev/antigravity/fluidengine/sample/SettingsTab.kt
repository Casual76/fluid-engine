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
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.update.fluidGlassUpdater
import kotlinx.coroutines.launch
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidPickerOption
import dev.antigravity.fluidengine.ui.fluid.FluidPickerRow
import dev.antigravity.fluidengine.ui.fluid.FluidSliderRow
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * Tab four: what a settings page looks like once the content is made of the same material.
 *
 * L'aspetto e' quello vero (dalla 3.0): [fluidAppearanceSection] scrive in [settings], che la galleria
 * passa al `FluidTheme`. Serve a una cosa precisa: giudicare il bordo del vetro che si trasforma sul
 * tema chiaro (e su quello scuro) senza uscire dall'app ne' ricreare l'attivita'.
 */
@Composable
internal fun SettingsTab(
  bottomInset: Dp,
  settings: EngineSettings,
  onSettingsChange: (EngineSettings) -> Unit,
) {
  FluidScreen(
    title = "Impostazioni",
    subtitle = "Aspetto, canvas ambientale, contenuto in vetro, e i crediti.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards)
    },
  ) {
    fluidAppearanceSection(
      settings = settings,
      onSettingsChange = onSettingsChange,
      glass = true,
      footnote = "Il tema chiaro serve per giudicare il bordo del vetro che si trasforma: sul chiaro " +
        "è scuro, sullo scuro è la metà più tenue.",
    )
    // Le righe della 3.0.1: il selettore che diventa un pop-up, lo slider in una riga, e una riga di scelta
    // con la piastrella dell'app (`leadingTile = false`).
    item(key = "rows-header") { FluidSectionHeader(title = "Righe di impostazioni") }
    item(key = "rows") { SettingRowsGroup() }
    item(key = "about-header") { FluidSectionHeader(title = "Informazioni") }
    item(key = "about") {
      UpdatesAndAboutGroup()
    }
    // I crediti stanno qui apposta: sono la stessa sezione che ogni app deve mettere nella propria
    // pagina "informazioni", e vale la pena guardarla girare sopra il canvas.
    fluidLicensesSection(glass = true)
  }
}

/** Il selettore, lo slider e le righe di scelta con una piastrella dell'app, in un gruppo di vetro. */
@Composable
private fun SettingRowsGroup() {
  var quality by remember { mutableStateOf("media") }
  var volume by remember { mutableFloatStateOf(0.6f) }
  var format by remember { mutableStateOf("png") }
  FluidListGroup(glass = true) {
    FluidPickerRow(
      title = "Qualità",
      selectedKey = quality,
      options = listOf(
        FluidPickerOption("bassa", "Bassa", subtitle = "File piccoli"),
        FluidPickerOption("media", "Media"),
        FluidPickerOption("alta", "Alta", subtitle = "File grandi, più lenti"),
      ),
      onSelected = { quality = it },
    )
    FluidListDivider()
    FluidSliderRow(
      title = "Volume",
      value = volume,
      onValueChange = { volume = it },
      valueLabel = "${(volume * 100).roundToInt()}%",
    )
    FluidListDivider()
    for ((key, label, colour) in listOf(
      Triple("png", "PNG", Color(0xFF2E7D32)),
      Triple("mp4", "MP4", Color(0xFF6A1B9A)),
    )) {
      FluidChoiceRow(
        title = label,
        selected = format == key,
        onSelect = { format = key },
        leading = {
          FluidToneTile(color = colour) {
            Icon(if (key == "png") Icons.Rounded.Image else Icons.Rounded.Movie, contentDescription = null)
          }
        },
        leadingTile = false,
      )
      if (key == "png") FluidListDivider()
    }
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
