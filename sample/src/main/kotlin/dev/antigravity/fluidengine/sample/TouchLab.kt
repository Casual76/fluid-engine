package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.antigravity.fluidengine.sample.material.LocalGalleryPageOpener
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSizing
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidReduceTransparency
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.currentCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.glass.field.LocalFluidGlassMotionLight
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlin.math.roundToInt

/** I valori sopravvivono allo scorrimento che fa uscire e rientrare le scene dalla lista. */
@Stable
internal class TouchLabState {
  var taps by mutableIntStateOf(0)
  var riseSwitch by mutableStateOf(true)
  var riseSlider by mutableFloatStateOf(0.45f)
  var switchOne by mutableStateOf(true)
  var switchTwo by mutableStateOf(false)
  var volume by mutableFloatStateOf(0.6f)
  var brightness by mutableFloatStateOf(40f)
  var stops by mutableFloatStateOf(0.5f)
  var size by mutableStateOf(FluidGlassSize.Regular)
  var contrast by mutableStateOf<Float?>(null)
  var reduceTransparency by mutableStateOf(false)
  var reduceMotion by mutableStateOf(false)
}

/** Dal tasto isolato alle policy del materiale, sulla stessa pagina che può scorrere durante un gesto. */
internal fun LazyListScope.touchLabItems(state: TouchLabState) {
  item(key = "touch-header") {
    FluidSectionHeader(title = "Tasto premuto", detail = "Tocchi registrati: ${state.taps}")
  }
  item(key = "touch-buttons") {
    FluidCard {
      FluidGlassIconButton(
        onClick = { state.taps++ },
        backdrop = currentCanvasBackdrop() ?: currentGlassBackdrop(),
      ) {
        FluidIcon(FluidIcons.Share, contentDescription = "Condividi")
      }
      TouchNote("Tieni premuto il tondo, sposta il dito e rilascia. Il glifo segue il vetro e si sfoca.")
    }
  }

  item(key = "touch-capsule-header") { FluidSectionHeader(title = "Capsula trascinata") }
  item(key = "touch-capsule") {
    FluidCard {
      FluidGlassButton(
        text = "A tutta larghezza",
        onClick = { state.taps++ },
        sizing = FluidButtonSizing.Flexible,
      )
      TouchNote("Trascina in diagonale. Ripeti con un tocco breve per interrompere la crescita al rilascio.")
    }
  }

  item(key = "touch-neighbors-header") { FluidSectionHeader(title = "Bagliore ai vicini") }
  item(key = "touch-neighbors") {
    FluidCard {
      FluidGlassContainer {
        Row(
          horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GroupSpacing),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FluidGlassIconButton(onClick = { state.taps++ }) {
            FluidIcon(FluidIcons.Filled.PlayArrow, contentDescription = "Riproduci")
          }
          FluidGlassIconButton(onClick = { state.taps++ }) {
            FluidIcon(FluidIcons.Filled.Favorite, contentDescription = "Preferito")
          }
          FluidGlassIconButton(onClick = { state.taps++ }) {
            FluidIcon(FluidIcons.Share, contentDescription = "Condividi")
          }
        }
      }
      TouchNote("Tieni il primo tasto e osserva quanto si schiariscono il secondo e il terzo.")
    }
  }

  touchGestureControls(state)
  touchButtonStyles(state)
  touchMaterialOptions(state)
}

private fun LazyListScope.touchGestureControls(state: TouchLabState) {
  item(key = "rise-header") { FluidSectionHeader(title = "Salgono nel vetro") }
  item(key = "rise") {
    FluidCard {
      FluidToggleRow("Tocca o trascina", state.riseSwitch, { state.riseSwitch = it })
      FluidSlider(
        value = state.riseSlider,
        onValueChange = { state.riseSlider = it },
        contentDescription = "Cursore che sale nel vetro",
      )
      TouchNote("Tocca l’interruttore, poi trascina il cursore e fermati. La lente mostra la pista e si richiude al rilascio.")
    }
  }
  item(key = "switches-header") { FluidSectionHeader(title = "Interruttori") }
  item(key = "switches") {
    FluidListGroup(glass = true) {
      FluidToggleRow("Interruttore acceso", state.switchOne, { state.switchOne = it })
      FluidListDivider()
      FluidToggleRow("Interruttore spento", state.switchTwo, { state.switchTwo = it })
      FluidListDivider()
      FluidToggleRow("Disabilitato", true, {}, enabled = false)
    }
  }
  item(key = "touch-sliders-header") { FluidSectionHeader(title = "Cursori") }
  item(key = "touch-sliders") {
    FluidCard(glass = true) {
      val volume = "${(state.volume * 100).roundToInt()}%"
      SliderRow(label = "Volume", value = volume) {
        FluidSlider(
          value = state.volume,
          onValueChange = { state.volume = it },
          modifier = Modifier.testTag("touch-volume"),
          contentDescription = "Volume",
          stateDescription = volume,
        )
      }
      val brightness = "${state.brightness.roundToInt()} cd"
      SliderRow(label = "Luminosità", value = brightness) {
        FluidSlider(
          value = state.brightness,
          onValueChange = { state.brightness = it },
          valueRange = 0f..120f,
          contentDescription = "Luminosità",
          stateDescription = brightness,
        )
      }
      SliderRow(label = "A tacche", value = "${(state.stops * 100).roundToInt()}%") {
        FluidSlider(
          value = state.stops,
          onValueChange = { state.stops = it },
          modifier = Modifier.testTag("touch-stops"),
          snapValues = TouchLabStops,
          contentDescription = "Cursore a tacche",
        )
      }
      SliderRow(label = "Disabilitato", value = "—") {
        FluidSlider(value = 0.3f, onValueChange = {}, enabled = false, contentDescription = "Cursore disabilitato")
      }
      TouchNote("Trascina in verticale prima dalla maniglia, poi dalla pista vuota. La pagina scorre solo dalla pista.")
    }
  }
}

private fun LazyListScope.touchButtonStyles(state: TouchLabState) {
  item(key = "touch-styles-header") { FluidSectionHeader(title = "Stili e taglie") }
  item(key = "touch-styles") {
    val open = LocalGalleryPageOpener.current
    FluidCard {
      FluidSegmentedControl(
        options = FluidGlassSize.entries,
        selected = state.size,
        onSelect = { state.size = it },
        label = { it.name },
      )
      CompositionLocalProvider(LocalFluidGlassSize provides state.size) {
        FluidGlassButton(text = "Vetro", onClick = { state.taps++ })
        FluidGlassButton(text = "Limpido", style = FluidGlassButtonStyle.Clear, onClick = { state.taps++ })
        FluidGlassButton(text = "Prominente", style = FluidGlassButtonStyle.Prominent, onClick = { state.taps++ })
      }
      FluidButton(
        text = "Principale",
        onClick = { state.taps++ },
        modifier = Modifier.testTag("touch-primary"),
      )
      FluidButton(
        text = "Tinta",
        style = FluidButtonStyle.Tinted,
        onClick = { state.taps++ },
      )
      FluidButton(
        text = "Piccolo",
        style = FluidButtonStyle.Plain,
        size = FluidButtonSize.Small,
        onClick = { state.taps++ },
      )
      FluidButton(text = "Elimina", style = FluidButtonStyle.Destructive, onClick = { state.taps++ })
      if (open != null) {
        FluidListRow(
          title = "Tasti nel foglio e nel popover",
          subtitle = "Taglia e stile nelle presentazioni",
          onClick = { open(GalleryPage.ButtonStyles) },
        )
        FluidListRow(
          title = "Liste, campi e stepper",
          subtitle = "Azioni a scorrimento e ripetizione alla pressione",
          onClick = { open(GalleryPage.ListsForms) },
        )
      }
    }
  }
}

private fun LazyListScope.touchMaterialOptions(state: TouchLabState) {
  item(key = "touch-light-header") { FluidSectionHeader(title = "Luce in movimento") }
  item(key = "touch-light") {
    val enabled = LocalFluidGlassMotionLight.current
    FluidCard {
      FluidGlassButton(text = "Riflesso sul bordo", onClick = { state.taps++ })
      TouchNote(
        if (enabled) "Attiva. Inclina il dispositivo e guarda il bordo della capsula."
        else "Spenta di serie. Attivala da Impostazioni → Aspetto → Luce in movimento, poi inclina il dispositivo.",
      )
    }
  }
  item(key = "touch-access-header") { FluidSectionHeader(title = "Accessibilità del vetro") }
  item(key = "touch-access") {
    val systemContrast = LocalFluidSystemContrast.current
    val systemTransparency = LocalFluidReduceTransparency.current
    val systemMotion = LocalFluidMotionPolicy.current
    FluidCard {
      FluidSegmentedControl(
        options = TouchLabContrasts.keys.toList(),
        selected = TouchLabContrasts.entries.first { it.value == state.contrast }.key,
        onSelect = { state.contrast = TouchLabContrasts.getValue(it) },
        label = { it },
      )
      FluidToggleRow("Simula trasparenza ridotta", state.reduceTransparency, { state.reduceTransparency = it })
      FluidToggleRow("Simula movimento ridotto", state.reduceMotion, { state.reduceMotion = it })
      CompositionLocalProvider(
        LocalFluidSystemContrast provides (state.contrast ?: systemContrast),
        LocalFluidReduceTransparency provides (state.reduceTransparency || systemTransparency),
        LocalFluidMotionPolicy provides if (state.reduceMotion) FluidMotionPolicy(0f) else systemMotion,
      ) {
        FluidGlassButton(text = "Vetro di prova", onClick = { state.taps++ })
      }
      TouchNote("Le opzioni di prova agiscono sul tasto qui sopra. Per la recessione, cambia finestra in modalità divisa.")
    }
  }
}

private val TouchLabStops = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
private val TouchLabContrasts = mapOf(
  "Sistema" to null,
  "Base" to FluidSystemContrast.Standard,
  "Medio" to FluidSystemContrast.Medium,
  "Alto" to FluidSystemContrast.High,
)

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
