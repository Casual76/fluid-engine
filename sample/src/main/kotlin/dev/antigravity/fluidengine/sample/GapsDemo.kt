package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidIndeterminateBar
import dev.antigravity.fluidengine.ui.fluid.FluidProgressBar
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.fluidGlassGroupsIndexed
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.fluidHapticsFor
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * I buchi che le app dichiaravano (fase 12a): cose che l'engine non aveva e che ogni app si e'
 * riscritta a mano, dicendo perche' nel proprio KDoc. Una sezione per buco, sul canvas ambientale
 * della scheda Controlli, cosi' si guardano nel posto dove si giudicano.
 */
internal fun LazyListScope.gapsDemoItems() {
  // 1 e 2. Le taglie dei tasti di vetro, e il pulsante grande per i fogli.
  item(key = "gaps-sizes-header") { FluidSectionHeader(title = "Tasti di vetro: tre taglie") }
  item(key = "gaps-sizes") {
    FluidCard(glass = true) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidGlassSize.entries.forEach { size ->
          FluidGlassIconButton(onClick = {}, size = size) {
            Icon(Icons.Rounded.Add, contentDescription = "Aggiungi (${size.name})", modifier = Modifier.size(size.iconSize))
          }
        }
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidGlassSize.entries.forEach { size ->
          FluidGlassButton(text = size.name, onClick = {}, size = size)
        }
      }
      Text(
        text = "40, 44 e 54 dp di vetro; il riquadro che si tocca non scende mai sotto i 48. " +
          "La pressione e' la stessa in proporzione: tienili premuti uno dopo l'altro.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  item(key = "gaps-large-button") {
    FluidCard(glass = true) {
      FluidButton(text = "Pulsante grande, 50 dp", onClick = {}, size = FluidButtonSize.Large, fillWidth = true)
      FluidButton(
        text = "Annulla",
        onClick = {},
        style = FluidButtonStyle.Tinted,
        size = FluidButtonSize.Large,
        fillWidth = true,
      )
      Text(
        text = "FluidButtonSize.Large: l'altezza di un pulsante in fondo a un foglio.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  // 3. Il messaggio che si tocca.
  item(key = "gaps-message-header") { FluidSectionHeader(title = "Messaggio toccabile") }
  item(key = "gaps-message") {
    var taps by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      FluidInlineMessage(
        title = "Sei offline",
        message = "Mostro quello che c'era l'ultima volta. Tocca per riprovare adesso.",
        tone = FluidTone.Warning,
        leading = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
        action = "Riprova (toccato $taps)",
        onClick = { taps++ },
        glass = true,
      )
      FluidInlineMessage(
        title = "Nuova versione",
        message = "Si apre il foglio con le novita'. Si puo' anche nascondere.",
        tone = FluidTone.Info,
        onClick = { taps++ },
        onDismiss = {},
        glass = true,
      )
      FluidInlineMessage(
        title = "Solo da leggere",
        message = "Senza onClick e' il riquadro di sempre: niente freccia. Questo resta opaco (glass = false).",
        tone = FluidTone.Neutral,
      )
      // Una card spenta (3.0.1): non risponde, e il contenuto sfuma senza che sfumi il vetro.
      FluidCard(glass = true, enabled = false, onClick = { taps++ }) {
        Text("Card spenta", style = MaterialTheme.typography.titleMedium)
        Text(
          "Toccala: non succede niente. Sfuma il testo, non il vetro.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  // 4. Il campo segreto e lo slot a 24 dp.
  item(key = "gaps-field-header") { FluidSectionHeader(title = "Campo segreto") }
  item(key = "gaps-field") {
    var secret by remember { mutableStateOf("pampa-1234") }
    var plain by remember { mutableStateOf("cartella") }
    FluidCard(glass = true) {
      FluidTextField(
        value = secret,
        onValueChange = { secret = it },
        label = "Chiave di accesso",
        placeholder = "Incolla la chiave",
        secret = true,
      )
      FluidTextField(
        value = plain,
        onValueChange = { plain = it },
        label = "Slot a destra da 24 dp",
        trailing = { Icon(Icons.Rounded.Folder, contentDescription = null) },
      )
    }
  }

  // 5. Il riempimento che si legge.
  item(key = "gaps-progress-header") { FluidSectionHeader(title = "Barre: il riempimento si legge") }
  item(key = "gaps-progress") {
    FluidCard(glass = true) {
      listOf(
        "Giallo pallido" to Color(0xFFFFF176),
        "Grigio chiaro" to Color(0xFFBDBDBD),
        "Verde di mezzo" to Color(0xFF8BC34A),
        "Accento del tema" to MaterialTheme.colorScheme.primary,
      ).forEach { (name, color) ->
        Text(text = name, style = MaterialTheme.typography.labelMedium)
        FluidProgressBar(progress = { 0.6f }, color = color)
      }
      FluidIndeterminateBar(color = Color(0xFFFFF176))
      Text(
        text = "Almeno 3:1 sulla pista, qualunque colore: se non basta, si scurisce (o schiarisce) del minimo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  // 6. La piastrella pubblica.
  item(key = "gaps-tiles-header") { FluidSectionHeader(title = "Piastrella") }
  item(key = "gaps-tiles") {
    FluidCard(glass = true) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidToneTile(tone = FluidTone.Success) { Icon(Icons.Rounded.Folder, contentDescription = null) }
        FluidToneTile(tone = FluidTone.Danger) { Icon(Icons.Rounded.Settings, contentDescription = null) }
        FluidToneTile(color = Color(0xFFE91E63)) { Icon(Icons.Rounded.Mic, contentDescription = null) }
        FluidToneTile(
          color = Color(0xFF3949AB),
          veil = Color(0xFF3949AB).copy(alpha = 0.30f),
          size = 48.dp,
        ) { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) }
      }
      Text(
        text = "Un tono dell'engine, un colore qualunque, un velo scelto a parte e una taglia: " +
          "il colore sta sulla piastrella, mai sullo sfondo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  // 7. Lo stato vuoto con l'illustrazione.
  item(key = "gaps-empty-header") { FluidSectionHeader(title = "Stato vuoto con illustrazione") }
  item(key = "gaps-empty") {
    FluidCard(glass = true) {
      FluidEmptyState(
        title = "Nessuna foto",
        detail = "Quando ne aggiungi qualcuna, la trovi qui.",
        illustration = {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp))
        },
      )
    }
  }

  // 8. Una lista lunga in pannelli di vetro dal taglio invisibile.
  item(key = "gaps-groups-header") { FluidSectionHeader(title = "Lista lunga in pannelli di vetro") }
  fluidGlassGroupsIndexed(items = (1..20).toList(), key = "gaps-groups") { index, number ->
    FluidListRow(
      title = "Riga $number (indice $index)",
      subtitle = if (number % 8 == 0 || number % 8 == 1) "Qui cade il taglio fra due pannelli: non si deve vedere" else "",
      onClick = {},
    )
  }

  // 9. L'aptica per il codice a viste.
  item(key = "gaps-haptics-header") { FluidSectionHeader(title = "Aptica senza Compose") }
  item(key = "gaps-haptics") {
    val context = LocalContext.current
    val haptics = remember(context) { fluidHapticsFor(context) }
    FluidCard(glass = true) {
      // Il tema sotto i tre tasti e' zittito: si deve sentire solo quello che suona `haptics`, cioe'
      // l'aptica dell'engine presa da un Context, non i tre colpi del tasto che cresce.
      CompositionLocalProvider(LocalFluidHaptics provides NoOpFluidHaptics) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          listOf(
            "Tocco" to FluidHapticEvent.Tap,
            "Apre" to FluidHapticEvent.Open,
            "Errore" to FluidHapticEvent.Error,
          ).forEach { (label, event) ->
            FluidButton(
              text = label,
              onClick = { haptics.play(event) },
              style = FluidButtonStyle.Tinted,
              size = FluidButtonSize.Small,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
      Text(
        text = "fluidHapticsFor(context): lo stesso motore del tema, per una tastiera o una vista. " +
          "Questi tre suonano da li', non dal tema.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
