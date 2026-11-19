package dev.antigravity.fluidengine.sample.buttons

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSizing
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.LocalFluidButtonSizing
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Share

/**
 * La pagina «Stili dei tasti» (piano «iOS in Android», F6.4): i tre stili dei tasti di vetro (`Glass`, `Clear`,
 * `Prominent`, cioe' `.glass`, `.glass(.clear)` e `.glassProminent` di SwiftUI) per le tre taglie, la larghezza
 * (`Fitted` e `Flexible`), e un foglio aperto da una barra `Compact` i cui tasti tornano alla taglia di serie.
 *
 * - **Tre stili, tre taglie**: per `Compact`, `Regular` e `Large` una riga di tondi (le icone a 0,6 del vetro: 24, 26,
 *   32 dp) e una di tasti con etichetta, uno per stile. La taglia la da' l'ambiente (`LocalFluidGlassSize`), non il tasto.
 * - **Larghezza**: un tasto adattato, due flessibili che si dividono la riga, uno flessibile da solo.
 * - **Barra Compact e foglio**: tre tondi compatti in un contenitore; i tre puntini aprono un foglio a meta' altezza i cui
 *   tasti sono `Regular`, `Glass` e `Fitted` come in iOS 27, anche se la barra che lo apre e' compatta, limpida e
 *   flessibile.
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page ButtonStyles [--es buttons.sheet open]
 *
 * Ferma, la pagina non chiede fotogrammi: niente si muove finche' non la si tocca.
 */
@Composable
internal fun ButtonStylesPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val startsOpen = remember(context) {
    context.findActivity()?.intent?.getStringExtra(ExtraSheet)?.equals("open", ignoreCase = true) == true
  }
  var sheet by remember { mutableStateOf(startsOpen) }
  var last by remember { mutableStateOf("Nessun tocco ancora") }

  FluidScreen(
    title = "Stili dei tasti",
    subtitle = "F6.4 · tre stili per tre taglie, larghezza adattata e flessibile, foglio da una barra compatta",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards) },
  ) {
    item(key = "styles-header") {
      FluidSectionHeader(
        title = "Tre stili, tre taglie",
        detail = "Vetro, limpido e in evidenza. La taglia la da' l'ambiente: ogni riga la fornisce una volta.",
      )
    }
    FluidGlassSize.entries.forEach { size ->
      item(key = "styles-${size.name}") {
        CompositionLocalProvider(LocalFluidGlassSize provides size) {
          StylesRow(size = size, onTap = { last = it })
        }
      }
    }
    item(key = "styles-note") {
      FluidSectionFootnote(
        "In evidenza la tinta resta l'accento: copre quanto serve perche' l'etichetta stia ad almeno 4,5:1 su qualunque " +
          "fondo, e prende l'inchiostro che sull'accento si legge. Le icone sono 0,6 del vetro: " +
          FluidGlassSize.entries.joinToString(" / ") { "${it.iconSize.value.toInt()}" } + " dp. Ultimo tocco: $last.",
      )
    }

    item(key = "sizing-header") {
      FluidSectionHeader(
        title = "Larghezza",
        detail = "Adattato quanto l'etichetta; flessibile tutta la larghezza data. In una riga, a ciascuno il suo peso.",
      )
    }
    item(key = "sizing") {
      Column(verticalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
        FluidGlassButton("Adattato", { last = "Adattato" }, Modifier.testTag("buttons-fitted"))
        Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
          FluidGlassButton("Annulla", { last = "Annulla" }, Modifier.weight(1f), sizing = FluidButtonSizing.Flexible)
          FluidGlassButton(
            "Conferma", { last = "Conferma" }, Modifier.weight(1f),
            style = FluidGlassButtonStyle.Prominent, sizing = FluidButtonSizing.Flexible,
          )
        }
        CompositionLocalProvider(LocalFluidButtonSizing provides FluidButtonSizing.Flexible) {
          FluidGlassButton("Flessibile dall'ambiente", { last = "Flessibile" }, Modifier.testTag("buttons-flexible"))
        }
      }
    }

    item(key = "sheet-header") {
      FluidSectionHeader(
        title = "Barra Compact e foglio",
        detail = "La barra e' compatta, limpida e flessibile. Il foglio che apre non eredita: i suoi tasti sono di serie.",
      )
    }
    item(key = "sheet-bar") {
      CompositionLocalProvider(
        LocalFluidGlassSize provides FluidGlassSize.Compact,
        LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Clear,
        LocalFluidButtonSizing provides FluidButtonSizing.Flexible,
      ) {
        FluidGlassContainer {
          Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
            FluidGlassIconButton(onClick = { last = "Condividi (barra)" }) { FluidIcon(FluidIcons.Share, "Condividi") }
            FluidGlassIconButton(onClick = { last = "Modifica (barra)" }) { FluidIcon(FluidIcons.Edit, "Modifica") }
            FluidGlassIconButton(onClick = { sheet = true }, modifier = Modifier.testTag("buttons-open-sheet")) {
              FluidIcon(FluidIcons.MoreHoriz, "Apri il foglio")
            }
          }
        }
      }
    }
  }
  // Il foglio, dichiarato nello stesso ambiente della barra (compatta, limpida, flessibile) e fuori dalla lista, cosi'
  // resta aperto anche se la barra scorre via: lo disegna l'host, e il foglio riporta i tasti ai valori di serie.
  CompositionLocalProvider(
    LocalFluidGlassSize provides FluidGlassSize.Compact,
    LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Clear,
    LocalFluidButtonSizing provides FluidButtonSizing.Flexible,
  ) {
    FluidGlassSheet(
      visible = sheet,
      onDismissRequest = { sheet = false },
      detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
      paneTitle = "Foglio",
    ) {
      SheetContent(onTap = { last = it }, onClose = { sheet = false })
    }
  }
}

/** Una riga per taglia: i tre tondi e i tre tasti con etichetta, uno per stile. */
@Composable
private fun StylesRow(size: FluidGlassSize, onTap: (String) -> Unit) {
  val styles = listOf(
    "Vetro" to FluidGlassButtonStyle.Glass,
    "Limpido" to FluidGlassButtonStyle.Clear,
    "Evidenza" to FluidGlassButtonStyle.Prominent,
  )
  Column(verticalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
    Text(size.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
      horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GroupSpacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      styles.forEach { (name, style) ->
        FluidGlassIconButton(
          onClick = { onTap("$name ${size.name}") },
          style = style,
          modifier = Modifier.testTag("buttons-icon-${size.name}-$name"),
        ) { FluidIcon(FluidIcons.Check, "$name ${size.name}") }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
      styles.forEach { (name, style) ->
        FluidGlassButton(
          text = name,
          onClick = { onTap("$name ${size.name}") },
          modifier = Modifier.weight(1f).testTag("buttons-label-${size.name}-$name"),
          style = style,
          sizing = FluidButtonSizing.Flexible,
          maxLines = 1,
        )
      }
    }
  }
}

/** Il contenuto del foglio: tasti senza taglia, stile ne' larghezza scritti, che prendono i valori di serie. */
@Composable
private fun SheetContent(onTap: (String) -> Unit, onClose: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      "Qui i tasti sono ${LocalFluidGlassSize.current.name}, ${LocalFluidGlassButtonStyle.current} e " +
        "${LocalFluidButtonSizing.current.name}: in iOS 27 un foglio non eredita lo stile della barra che lo apre.",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.testTag("buttons-sheet-note"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
      FluidGlassIconButton(onClick = { onTap("Condividi (foglio)") }, modifier = Modifier.testTag("buttons-sheet-icon")) {
        FluidIcon(FluidIcons.Share, "Condividi")
      }
      FluidGlassIconButton(onClick = { onTap("Modifica (foglio)") }) { FluidIcon(FluidIcons.Edit, "Modifica") }
    }
    FluidGlassButton("Chiudi", onClose, Modifier.testTag("buttons-sheet-close"))
  }
}

private const val ExtraSheet = "buttons.sheet"

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
