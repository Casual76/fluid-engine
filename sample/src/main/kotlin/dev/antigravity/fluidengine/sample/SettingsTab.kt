package dev.antigravity.fluidengine.sample

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
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
      FluidListGroup(glass = true) {
        FluidListRow(title = "Versione", subtitle = "Fluid Glass ${appVersionName()}")
        FluidListDivider()
        FluidListRow(title = "Sorgente", subtitle = "fluid-engine", onClick = {})
      }
    }
    // I crediti stanno qui apposta: sono la stessa sezione che ogni app deve mettere nella propria
    // pagina "informazioni", e vale la pena guardarla girare sopra il canvas.
    fluidLicensesSection()
  }
}

/** La versione dichiarata dal pacchetto installato: quella vera, non una scritta a mano. */
@Composable
private fun appVersionName(): String {
  val context = androidx.compose.ui.platform.LocalContext.current
  return remember(context) {
    runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
  }
}
