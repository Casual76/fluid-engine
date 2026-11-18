package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * Tab two: every control the engine has, standing on a real canvas instead of on grey.
 *
 * It opens with the touch lab of 2.11.0 (see [touchLabItems]): the buttons that grow under the
 * finger and the sliders that hold the page still, on a page long enough to scroll.
 */
@Composable
internal fun ControlsTab(bottomInset: Dp) {
  val touchLab = remember { TouchLabState() }
  var switchOne by remember { mutableStateOf(true) }
  var switchTwo by remember { mutableStateOf(false) }
  var segment by remember { mutableStateOf("Mese") }
  var chip by remember { mutableStateOf("Tutti") }
  var query by remember { mutableStateOf("") }
  var riseSwitch by remember { mutableStateOf(true) }
  var riseSlider by remember { mutableFloatStateOf(0.45f) }

  FluidScreen(
    title = "Controlli",
    subtitle = "Tocco, cursori, interruttore, segmenti, pillole, pulsanti e campi, sul canvas ambientale.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Dots)
    },
  ) {
    touchLabItems(touchLab)

    item(key = "switches-header") { FluidSectionHeader(title = "Interruttori") }
    item(key = "switches") {
      FluidListGroup(glass = true) {
        SwitchRow("Interruttore acceso", switchOne) { switchOne = it }
        FluidListDivider()
        SwitchRow("Interruttore spento", switchTwo) { switchTwo = it }
        FluidListDivider()
        SwitchRow("Disabilitato", true, enabled = false) {}
      }
    }

    item(key = "switch-note") {
      Text(
        text = "Tocca ovunque sull'interruttore per accenderlo. Tenuto premuto, il bianco del " +
          "pomello sparisce e sotto si apre una copia schiacciata della pista: e' l'unica " +
          "lastra del controllo, e la pista si vede solo attraverso di lui.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }

    // F6.3: i due pomelli che salgono nel vetro, su una card piena come la pagina delle Impostazioni di Apple: li'
    // la lente non ha altro da piegare che la pista, e si vede com'e' fatta.
    item(key = "rise-header") { FluidSectionHeader(title = "Salgono nel vetro") }
    item(key = "rise") {
      FluidCard {
        SwitchRow("Tocca o trascina", riseSwitch) { riseSwitch = it }
        FluidSlider(
          value = riseSlider,
          onValueChange = { riseSlider = it },
          contentDescription = "Cursore che sale nel vetro",
        )
        Text(
          text = "Al tocco il pomello diventa una lente limpida una volta e mezza piu' grande, senza " +
            "rimbalzo, con attorno un alone del colore della pista. Un tocco sull'interruttore la forma " +
            "sul posto, poi la fa correre e la richiude all'arrivo; la pista cambia colore quando il " +
            "pomello passa la meta'. Trascinata, la lente si allunga quando accelera e si stringe quando " +
            "frena; a velocita' costante torna la sua.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

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

    item(key = "chips-header") { FluidSectionHeader(title = "Pillole e pulsanti") }
    item(key = "chips") {
      FluidCard(glass = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("Tutti", "Letti", "Da leggere").forEach { option ->
            FluidChip(label = option, selected = chip == option, onClick = { chip = option })
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FluidButton(text = "Principale", onClick = {}, size = FluidButtonSize.Medium)
          FluidButton(
            text = "Tinta",
            onClick = {},
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Medium,
          )
          FluidButton(
            text = "Elimina",
            onClick = {},
            style = FluidButtonStyle.Destructive,
            size = FluidButtonSize.Medium,
          )
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
    glassGrowthContrastItems()
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
