package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.SkipNext
import dev.antigravity.fluidengine.ui.theme.FluidCard

/**
 * Due cose della 3.0 che si guardano in mano: la crescita dei tasti tondi con un tetto, e il vetro che
 * si fa piu' denso col contrasto del sistema.
 *
 * Sul canvas ambientale della scheda, cosi' il vetro ha qualcosa da attraversare e un disco che
 * cresce ha dei vicini da invadere.
 */
internal fun LazyListScope.glassGrowthContrastItems() {
  // La crescita con un tetto, e la taglia libera.
  item(key = "growth-header") { FluidSectionHeader(title = "Tasti tondi: crescono, ma con un tetto") }
  item(key = "growth-discs") {
    FluidCard(glass = true) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        listOf(44.dp, 54.dp, 62.dp, 76.dp).forEachIndexed { index, size -> GrowthDisc(size, index) }
      }
      Text(
        text = "44, 54, 62 e 76 dp di vetro (FluidGlassIconButton(size = ...dp)), a 8 dp l'uno dall'altro. " +
          "Tienili premuti: fino a 48 dp il tasto cresce di un mezzo, intero come LibrePods; oltre, di 24 dp " +
          "in tutto, 12 per lato. Il 76 arriva a 100 dp, non a 114.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  // Il contrasto del sistema.
  item(key = "contrast-header") { FluidSectionHeader(title = "Contrasto: il vetro si fa piu' denso") }
  item(key = "contrast-levels") {
    FluidCard(glass = true) {
      Text(
        text = "Contrasto del sistema adesso: ${"%.1f".format(LocalFluidSystemContrast.current)} " +
          "(Impostazioni > Accessibilita' > Contrasto del colore, da Android 14).",
        style = MaterialTheme.typography.bodyMedium,
      )
      listOf(
        "Standard" to FluidSystemContrast.Standard,
        "Medio" to FluidSystemContrast.Medium,
        "Alto" to FluidSystemContrast.High,
      ).forEach { (name, level) ->
        // Il livello si forza qui dentro, dentro il tema: e' la stessa strada di quello del sistema.
        CompositionLocalProvider(LocalFluidSystemContrast provides level) {
          FluidCard(glass = true) {
            Text(text = "$name (${"%.1f".format(level)})", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              FluidGlassButton(text = "Tasto", onClick = {})
              FluidGlassIconButton(onClick = {}) {
                FluidIcon(FluidIcons.Filled.PlayArrow, contentDescription = "Riproduci ($name)")
              }
            }
          }
        }
      }
      Text(
        text = "Ogni pellicola di GlassDefaults sale verso il pieno (a medio il 40% della strada che le manca, ad " +
          "alto l'85%) e il suo bordo si fa piu' netto; a standard non cambia niente. Cambia il contrasto " +
          "di sistema con l'app aperta: segue.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun GrowthDisc(size: Dp, index: Int) {
  FluidGlassIconButton(onClick = {}, size = size) {
    FluidIcon(
      imageVector = when (index % 3) {
        0 -> FluidIcons.Filled.PlayArrow
        1 -> FluidIcons.Filled.Pause
        else -> FluidIcons.Filled.SkipNext
      },
      contentDescription = "Disco da ${size.value.toInt()} dp",
      modifier = Modifier.size(size / 2),
    )
  }
}
