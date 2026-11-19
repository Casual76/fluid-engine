package dev.antigravity.fluidengine.sample.bottom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAdaptiveNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.fluidBackgroundExtension
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.Star

private val destinations = listOf(
  FluidTabItem("raccolta", "Raccolta", FluidIcons.PhotoLibrary),
  FluidTabItem("serie", "Serie", FluidIcons.GridView),
  FluidTabItem("preferiti", "Preferiti", FluidIcons.Star),
)

/**
 * Il quarto capitolo della galleria di F4B, a finestra intera: tre destinazioni dichiarate una volta sola che diventano
 * tab bar, rail o tab bar in alto secondo la larghezza, e in alto il tasto che apre la barra laterale e la richiude. I
 * fondali cambiano colore con la scheda perché si vedano la luce che il pannello prende dalla pagina e l'estensione
 * dello sfondo sotto la colonna. Le riprese la riconoscono dal titolo «Atlante cromatico».
 */
@Composable
internal fun TabletNavigationPage() {
  var route by rememberSaveable { mutableStateOf(destinations.first().route) }
  val (title, colors) = when (route) {
    "serie" -> "Maree" to listOf(Color(0xFF0A4757), Color(0xFF418C9D), Color(0xFFE5D7AA))
    "preferiti" -> "Notturno" to listOf(Color(0xFF422052), Color(0xFF865C86), Color(0xFFD7AF91))
    else -> "Terra rossa" to listOf(Color(0xFF702B39), Color(0xFFD75035), Color(0xFFF1B66E))
  }
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
    FluidAdaptiveNavigation(destinations, route, { route = it.route }) { insets ->
      Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().fluidBackgroundExtension()) {
          drawRect(Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height)))
          drawCircle(colors.last(), radius = size.minDimension * .34f,
            center = Offset(size.width * .77f, size.height * .29f))
          drawCircle(colors.first().copy(alpha = .65f), radius = size.width * .81f,
            center = Offset(size.width * .14f, size.height + size.width * .40f))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(insets).padding(36.dp)) {
          Text("Atlante cromatico", color = Color.White, style = MaterialTheme.typography.titleMedium)
          Text(title, color = Color.White, style = MaterialTheme.typography.displayMedium)
        }
      }
    }
  }
}
