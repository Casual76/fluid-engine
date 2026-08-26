package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.launch

/** Tab three: everything that appears *over* a page, and the one thing that used to be impossible. */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ChromeTab(bottomInset: Dp) {
  var cardOpen by remember { mutableStateOf(false) }
  var sheetOpen by remember { mutableStateOf(false) }
  var legacySheet by remember { mutableStateOf(false) }
  var alertOpen by remember { mutableStateOf(false) }
  var cardOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
  var log by remember { mutableStateOf("Niente ancora.") }
  var notificationCount by remember { mutableIntStateOf(0) }

  val notificationHost = LocalFluidNotificationHostState.current
  val scope = rememberCoroutineScope()

  // Declared here, next to the state it reads, and drawn at the root above the tab bar. This is the
  // pattern every feature uses: nothing is emitted at this call site.
  FluidGlassModalPortal(
    visible = cardOpen,
    onDismissRequest = { cardOpen = false },
    origin = { cardOrigin },
    paneTitle = "Dettaglio",
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Un pop-up in vetro",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Nasce dalla riga che hai toccato e LIEVITA fino a qui: la sagoma viaggia con la " +
          "rifrazione addosso, il contenuto le sta attaccato, e la pagina dietro resta a colori " +
          "— la separa il bordo del vetro, non un velo nero. Per chiudere tocca fuori.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  FluidGlassModalPortal(
    visible = sheetOpen,
    onDismissRequest = { sheetOpen = false },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Foglio",
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 34.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Ancorato al bordo",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      repeat(4) { index ->
        Text(
          text = "Riga ${index + 1} di contenuto, sopra la pagina che si vede ancora.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  FluidScreen(
    title = "Chrome",
    subtitle = "Quello che appare sopra una pagina, e la pagina resta visibile.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.TertiaryToPrimary, motif = FluidHeroMotif.Ripples)
    },
  ) {
    item(key = "modal-header") { FluidSectionHeader(title = "Modale in-root") }
    item(key = "modal") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Card che si espande",
          subtitle = "Cresce dai limiti di questa riga",
          modifier = Modifier.fluidExpandOrigin { cardOrigin = it },
          onClick = { cardOpen = true },
        )
        FluidListDivider()
        FluidListRow(
          title = "Foglio ancorato in basso",
          subtitle = "Stessa meccanica, altra geometria",
          onClick = { sheetOpen = true },
        )
      }
    }

    item(key = "context-header") { FluidSectionHeader(title = "Menù contestuale") }
    item(key = "context") {
      FluidListGroup(glass = true) {
        listOf("Circolare di settembre", "Uscita anticipata", "Consiglio di classe")
          .forEachIndexed { index, title ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = title,
              subtitle = "Tieni premuto per le azioni",
              tone = if (index == 1) FluidTone.Warning else FluidTone.Neutral,
              onClick = { log = "Aperta: $title" },
              contextActions = {
                listOf(
                  FluidContextAction("Segna come letta", Icons.Rounded.Star) {
                    log = "Letta: $title"
                  },
                  FluidContextAction("Condividi", Icons.Rounded.Share) {
                    log = "Condivisa: $title"
                  },
                  FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) {
                    log = "Eliminata: $title"
                  },
                )
              },
            )
          }
      }
    }

    item(key = "log") {
      FluidCard(glass = true) {
        Text(
          text = log,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "legacy-header") { FluidSectionHeader(title = "Finestre di piattaforma") }
    item(key = "legacy") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Notifica in-app",
          subtitle = "Overlay in-root, vetro vero",
          onClick = {
            val id = ++notificationCount
            scope.launch {
              notificationHost?.show(
                FluidNotification(
                  id = "gallery-$id",
                  title = "Notifica $id",
                  message = "Anche questa rifrange la pagina invece di coprirla.",
                  tone = FluidNotificationTone.Success,
                ),
              )
            }
          },
        )
        FluidListDivider()
        FluidListRow(
          title = "FluidSheet (deprecato)",
          subtitle = "Finestra separata: gradienti dipinti, non vetro",
          onClick = { legacySheet = true },
        )
        FluidListDivider()
        FluidListRow(
          title = "FluidAlert",
          subtitle = "Dialog di sistema, stesso confine",
          onClick = { alertOpen = true },
        )
      }
    }
  }

  if (legacySheet) {
    FluidSheet(onDismissRequest = { legacySheet = false }, title = "Foglio di piattaforma") {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Questo vive in una finestra sua, quindi non può leggere il GraphicsLayer " +
            "dell'app. Guarda il fondo: è dipinto, non rifratto. È il confine di sistema per " +
            "cui esiste FluidGlassModalPortal.",
          style = MaterialTheme.typography.bodyMedium,
        )
        FluidButton(text = "Chiudi", onClick = { legacySheet = false }, fillWidth = true)
      }
    }
  }

  if (alertOpen) {
    FluidAlert(
      onDismissRequest = { alertOpen = false },
      title = "Eliminare?",
      message = "Anche l'alert sta in una finestra separata.",
      actions = listOf(
        FluidAlertAction("Annulla", onClick = { alertOpen = false }),
        FluidAlertAction(
          "Elimina",
          onClick = { alertOpen = false },
          emphasis = FluidAlertAction.Emphasis.Destructive,
        ),
      ),
    )
  }
}
