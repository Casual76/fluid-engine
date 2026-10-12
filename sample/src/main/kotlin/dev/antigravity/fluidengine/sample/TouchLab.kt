package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.currentCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidCard
import kotlin.math.roundToInt

/**
 * Lo stato della pagina di prova del tocco. Fuori dagli item della lista, cosi' un cursore spostato
 * resta dov'e' anche quando la sua riga esce dallo schermo e ci rientra.
 */
@Stable
internal class TouchLabState {
  var taps by mutableIntStateOf(0)
  var volume by mutableFloatStateOf(0.6f)
  var brightness by mutableFloatStateOf(40f)
  var chip by mutableStateOf("Tutti")
}

/**
 * La pagina di prova della 2.11.0: i pulsanti che crescono sotto il dito e gli slider che non
 * lasciano scorrere la pagina. Sta in cima alla scheda Controlli, che e' una pagina lunga apposta:
 * un cursore che tiene ferma la pagina si prova solo dove la pagina potrebbe muoversi.
 *
 * Cosa guardare (e sentire) su un telefono vero:
 *  * un tap rapidissimo su un tasto fa vedere tutta la pressione, crescita e ritorno, e conta;
 *  * tenuto premuto e fatto scorrere, il tasto si inclina verso il dito e scatta a ogni passo —
 *    tacche leggere, mai un ronzio, nemmeno strisciando veloce;
 *  * la maniglia di un cursore trascinata in diagonale muove il valore e non la pagina; una passata
 *    verticale partita sulla pista nuda invece scorre la pagina, come deve.
 */
internal fun LazyListScope.touchLabItems(state: TouchLabState) {
  item(key = "touch-header") {
    FluidSectionHeader(title = "Tocco", detail = "Tocchi registrati: ${state.taps}")
  }
  item(key = "touch-buttons") {
    FluidCard(glass = true) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FluidButton(text = "Principale", onClick = { state.taps++ }, modifier = Modifier.testTag("touch-primary"))
        FluidButton(text = "Tinta", onClick = { state.taps++ }, style = FluidButtonStyle.Tinted)
        FluidButton(
          text = "Piccolo",
          onClick = { state.taps++ },
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Small,
        )
      }
      FluidButton(
        text = "A tutta larghezza",
        onClick = { state.taps++ },
        size = FluidButtonSize.Large,
        fillWidth = true,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val backdrop = currentCanvasBackdrop() ?: currentGlassBackdrop()
        FluidGlassButton(text = "Vetro", onClick = { state.taps++ }, backdrop = backdrop)
        FluidGlassIconButton(onClick = { state.taps++ }, backdrop = backdrop) {
          Icon(Icons.Rounded.Favorite, contentDescription = "Preferito", tint = MaterialTheme.colorScheme.primary)
        }
        FluidGlassIconButton(onClick = { state.taps++ }, backdrop = backdrop) {
          Icon(Icons.Rounded.Share, contentDescription = "Condividi", tint = MaterialTheme.colorScheme.primary)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Tutti", "Recenti", "Preferiti").forEach { option ->
          FluidChip(
            label = option,
            selected = state.chip == option,
            onClick = {
              state.chip = option
              state.taps++
            },
          )
        }
      }
      TouchNote(
        "Un tap rapidissimo mostra comunque tutta la pressione. Tenuto premuto e fatto scorrere, " +
          "il tasto si inclina verso il dito e scatta a ogni passo: tacche leggere, mai un ronzio.",
      )
    }
  }

  item(key = "touch-sliders-header") { FluidSectionHeader(title = "Cursori") }
  item(key = "touch-sliders") {
    FluidCard(glass = true) {
      SliderRow(
        label = "Volume",
        value = "${(state.volume * 100).roundToInt()}%",
      ) {
        FluidSlider(
          value = state.volume,
          onValueChange = { state.volume = it },
          modifier = Modifier.testTag("touch-volume"),
        )
      }
      SliderRow(
        label = "Luminosita'",
        value = "${state.brightness.roundToInt()} cd",
      ) {
        FluidSlider(
          value = state.brightness,
          onValueChange = { state.brightness = it },
          valueRange = 0f..120f,
        )
      }
      SliderRow(label = "Disabilitato", value = "—") {
        FluidSlider(value = 0.3f, onValueChange = {}, enabled = false)
      }
      TouchNote(
        "Trascina la maniglia anche in diagonale: il valore segue il dito e la pagina resta " +
          "ferma. Una passata verticale partita sulla pista scorre la pagina, come deve. Un " +
          "tick a ogni ventesimo, un colpo piu' deciso alle estremita'.",
      )
    }
  }
}

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  slider()
}

@Composable
private fun TouchNote(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 2.dp),
  )
}
