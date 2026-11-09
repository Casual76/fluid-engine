package dev.antigravity.fluidengine.sample

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationAction
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

/**
 * Tab three: everything that appears *over* a page, and the one thing that used to be impossible.
 *
 * Dalla beta della 3.0 e' anche il catalogo: le demo che non stanno in nessuna scheda (le righe e i
 * fogli, le capsule, i buchi delle app) si aprono da qui con [onOpen], a un tocco dalla scheda.
 */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ChromeTab(bottomInset: Dp, onOpen: (GalleryPage) -> Unit = {}) {
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

    item(key = "pages-header") { FluidSectionHeader(title = "Navigazione") }
    item(key = "pages") {
      val context = LocalContext.current
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Pagine",
          subtitle = "Apertura da destra, indietro sotto il dito (3.0)",
          onClick = { context.startActivity(Intent(context, PagesDemoActivity::class.java)) },
        )
      }
    }

    item(key = "catalog-header") { FluidSectionHeader(title = "Catalogo della 3.0") }
    item(key = "catalog") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Da provare",
          subtitle = "Cosa giudicare in mano in questa beta, con il collegamento a ogni demo",
          modifier = Modifier.testTag("chrome-open-to-try"),
          onClick = { onOpen(GalleryPage.ToTry) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Righe e fogli",
          subtitle = "Interruttori, scelte, aspetto, conferme e avvisi con un campo",
          modifier = Modifier.testTag("chrome-open-rows"),
          onClick = { onOpen(GalleryPage.RowsAndSheets) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Capsule e pezzi piccoli",
          subtitle = "Capsula, ricerca, pallino, scheletri, selezione, pager, passi",
          modifier = Modifier.testTag("chrome-open-capsules"),
          onClick = { onOpen(GalleryPage.CapsulesAndParts) },
        )
        FluidListDivider()
        FluidListRow(
          title = "I buchi delle app",
          subtitle = "Taglie, messaggio, campo segreto, barre, piastrella, vuoto, gruppi",
          modifier = Modifier.testTag("chrome-open-gaps"),
          onClick = { onOpen(GalleryPage.Gaps) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Menù liquidi",
          subtitle = "Si scelgono col dito che li ha aperti: tasto, barra, riga, card e avviso",
          modifier = Modifier.testTag("chrome-open-menus"),
          onClick = { onOpen(GalleryPage.Menus) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Tutto in vetro",
          subtitle = "Chip, segmentato, rail, indice, banda della barra, suggerimento, capsula, fogli",
          modifier = Modifier.testTag("chrome-open-all-glass"),
          onClick = { onOpen(GalleryPage.AllGlass) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Riordino, aggiornamento e visore",
          subtitle = "Aggiornamento in-app, riordino, scorrimento rapido con la bolla, visualizzatore",
          modifier = Modifier.testTag("chrome-open-more"),
          onClick = { onOpen(GalleryPage.MoreComponents) },
        )
      }
    }

    // I componenti nuovi della fase 1: una pagina per demo, raggruppate, tutte con la matrice degli stati.
    item(key = "new-components-header") {
      FluidSectionHeader(
        title = "Componenti nuovi",
        detail = "Ogni pagina ha il tasto della matrice degli stati: tema, testo, movimento, lingua.",
      )
    }
    GalleryGroup.entries.forEach { group ->
      item(key = "new-components-${group.name}-header") {
        FluidSectionHeader(title = group.title, detail = group.detail)
      }
      item(key = "new-components-${group.name}") {
        FluidListGroup(glass = true) {
          GalleryPage.newComponents.filter { it.group == group }.forEachIndexed { index, page ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = page.title,
              subtitle = page.catalogSubtitle,
              modifier = Modifier.testTag("chrome-open-${page.name}"),
              onClick = { onOpen(page) },
            )
          }
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
          title = "Notifica da aprire",
          // L'unico suggerimento del gesto: la card non ha una maniglia, e senza saperlo non si trova.
          subtitle = "Tirala giù per aprirla, su per scartarla",
          onClick = {
            val id = ++notificationCount
            scope.launch {
              notificationHost?.show(
                FluidNotification(
                  id = "gallery-open-$id",
                  title = "Sincronizzazione completata",
                  message = "Sono arrivati quattordici nuovi voti e due comunicazioni della segreteria. " +
                    "Chiusa, la notifica ne mostra due righe; tirata giù si apre e mostra il testo " +
                    "per intero, con le azioni, e resta finché non la chiudi tu. Col dito sopra, " +
                    "il tempo si ferma.",
                  tone = FluidNotificationTone.Info,
                  actions = listOf(
                    FluidNotificationAction("Apri") { log = "Azione della notifica $id: Apri" },
                  ),
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
