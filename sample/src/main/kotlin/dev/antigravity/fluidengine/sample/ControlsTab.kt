package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidTone

/** Il laboratorio del tocco apre la scheda, seguito dalle prove dei singoli componenti. */
@Composable
internal fun ControlsTab(bottomInset: Dp) {
  val touchLab = remember { TouchLabState() }
  var segment by remember { mutableStateOf("Mese") }
  var chip by remember { mutableStateOf("Tutti") }
  var query by remember { mutableStateOf("") }

  FluidScreen(
    title = "Controlli",
    subtitle = "Dal tasto premuto ai controlli a gesto, alla luce e all’accessibilità del vetro.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Dots)
    },
  ) {
    touchLabItems(touchLab)

    hapticLabItems()

    item(key = "segments-header") { FluidSectionHeader(title = "Segmenti") }
    item(key = "segments") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf("Giorno", "Settimana", "Mese"),
          selected = segment,
          onSelect = { segment = it },
          label = { it },
        )
        Text(
          text = "Trascina la pastiglia fra i segmenti: la lente rifrange la copia " +
            "accentata delle etichette, non un riempimento.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "chips-header") { FluidSectionHeader(title = "Pillole") }
    item(key = "chips") {
      FluidCard(glass = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("Tutti", "Letti", "Da leggere").forEach { option ->
            FluidChip(label = option, selected = chip == option, onClick = { chip = option })
          }
        }
      }
    }

    item(key = "field-header") { FluidSectionHeader(title = "Campo di testo") }
    item(key = "field") {
      FluidCard(glass = true) {
        FluidTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = "Cerca",
        )
      }
    }

    item(key = "tiles-header") { FluidSectionHeader(title = "Piastrelle") }
    item(key = "tiles") {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidMetricTile(
          label = "Media",
          value = "7,4",
          detail = "in salita",
          tone = FluidTone.Success,
          glass = true,
          modifier = Modifier.weight(1f),
        )
        FluidMetricTile(
          label = "Assenze",
          value = "3",
          detail = "da giustificare",
          tone = FluidTone.Warning,
          glass = true,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // I buchi che le app dichiaravano (fase 12a): taglie dei tasti di vetro, messaggio toccabile,
    // campo segreto, barre leggibili, piastrella, stato vuoto, pannelli di vetro, aptica a viste.
    gapsDemoItems()
  }
}

@Composable
internal fun SwitchRow(
  label: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
    )
    FluidSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
  }
}
