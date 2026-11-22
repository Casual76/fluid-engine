package dev.antigravity.fluidengine.sample.dynamic

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAdaptiveNavigation
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidButtonRole
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidListDetailScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdown
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Description
import dev.antigravity.fluidengine.ui.fluid.icons.DriveFileMove
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.FolderZip
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.MusicNote
import dev.antigravity.fluidengine.ui.fluid.icons.PictureAsPdf
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidUndoCountdownState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionActions
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionLeading
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.selectButton
import dev.antigravity.fluidengine.ui.fluid.toolbar.selection
import dev.antigravity.fluidengine.ui.fluid.toolbar.selectionHasItems
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile

// La barra delle azioni di selezione (F6B.1, F6B.2) nei quattro casi delle app (F6B.0, §12). Ogni scena scrive lo stato
// con un `if` nella sua barra, `selectButton` fuori selezione e `selection { }` dentro: il resto (il conteggio, «Fine»,
// la tab bar che diventa le azioni, il back della selezione con D1, la riga che non salta) è dell'engine.

/** I tag dei test. */
internal object SelectionSceneTags {
  const val MailList = "selezione:posta:lista"
  const val MailNavigation = "selezione:posta:navigazione"
  const val PhotosGrid = "selezione:foto:griglia"

  fun message(id: Int): String = "selezione:posta:$id"
  fun note(id: Int): String = "selezione:note:$id"
  fun photo(id: Int): String = "selezione:foto:$id"
  fun file(id: Int): String = "selezione:file:$id"
}

/** Un insieme di numeri che sopravvive alla ricreazione. */
private val IntSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })

// --- Mail: la tab bar diventa le azioni; sul tablet le azioni stanno nel pannello della lista ----------------------

internal data class SelectionMessage(val id: Int, val from: String, val subject: String, val preview: String)

internal val SelectionMessages = listOf(
  SelectionMessage(0, "Giulia Rossi", "Cena di sabato", "Ci vediamo alle otto da noi? Porto il dolce."),
  SelectionMessage(1, "Banca", "Estratto conto di novembre", "Il tuo estratto conto è disponibile nell'area riservata."),
  SelectionMessage(2, "Marco", "Foto della gita", "Ti mando le foto del lago, sono venute benissimo."),
  SelectionMessage(3, "Biblioteca", "Prestito in scadenza", "Il libro va restituito entro venerdì."),
  SelectionMessage(4, "Sara", "Re: Progetto", "Ho guardato la bozza, due note veloci."),
  SelectionMessage(5, "Palestra", "Nuovi orari", "Da lunedì apriamo alle sette."),
  SelectionMessage(6, "Elena", "Appunti della riunione", "Il riepilogo di ieri, con le date dei prossimi passi."),
  SelectionMessage(7, "Corriere", "Il pacco è in consegna", "Arriva oggi fra le 14 e le 18."),
  SelectionMessage(8, "Luca", "Partita di giovedì", "Siamo in otto, manca un portiere."),
  SelectionMessage(9, "Comune", "Raccolta differenziata", "Il calendario del nuovo anno è online."),
  SelectionMessage(10, "Martina", "Riunione di domani", "Confermo l'orario delle dieci."),
  SelectionMessage(11, "Agenzia viaggi", "Il tuo itinerario", "In allegato i biglietti del treno."),
)

private val MailTabs = listOf(
  FluidTabItem("posta", "Posta", FluidIcons.Inbox),
  FluidTabItem("cartelle", "Cartelle", FluidIcons.Folder),
  FluidTabItem("cerca", "Cerca", FluidIcons.Search),
)

/** Quanto dura l'«Annulla» dopo un'azione di massa: otto secondi, come «Invia» (F6B.4). */
private const val BatchUndoMillis = 8_000L

/**
 * Mail: la posta in arrivo con le sue schede. «Seleziona» trasforma la tab bar nelle azioni (Segna come letti,
 * Sposta, Archivia, Elimina), il titolo diventa il conteggio, «Fine» o il back tornano com'era. Archiviare in blocco
 * chiude la selezione e lascia **un solo «Annulla»** per tutto il gruppo, nella barra in alto, che si riempie col tempo
 * (il contatore di F6B.4, che Alessio ha scelto al posto dell'avviso grande).
 *
 * Sul tablet la stessa dichiarazione delle schede diventa rail (da 600 dp) o tab bar in alto con la barra laterale (da
 * 1000 dp), e la lista sta in un pannello accanto al dettaglio: rail e barra laterale non si trasformano, la barra delle
 * azioni sta nel pannello della lista (scelta di Alessio, come in Notes).
 */
@Composable
internal fun SelectionMailScene(onBack: () -> Unit) {
  var tab by rememberSaveable { mutableStateOf(MailTabs.first().route) }
  val selection = rememberFluidSelectionState<Int>()
  var selecting by rememberSaveable { mutableStateOf(false) }
  var archived by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  var read by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  var batch by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  var undoStartedAt by rememberSaveable { mutableLongStateOf(-1L) }
  var open by rememberSaveable { mutableIntStateOf(-1) }
  val visible = SelectionMessages.filter { it.id !in archived }
  val chosen = { visible.filter { selection.isSelected(it.id) }.map { it.id }.toSet() }
  val exit = { selection.clear(); selecting = false }
  // L'operazione e la sua finestra sono dell'app; l'engine conosce il tasto e il suo tempo.
  val putAway: () -> Unit = {
    batch = chosen()
    archived = archived + batch
    undoStartedAt = SystemClock.elapsedRealtime()
    exit()
  }
  val timing = remember(undoStartedAt) {
    if (undoStartedAt < 0L) null else FluidNotificationCountdown(BatchUndoMillis, undoStartedAt)
  }
  val countdown = timing?.let { rememberFluidUndoCountdownState(it) }
  LaunchedEffect(countdown?.isActive, timing) {
    if (timing != null && countdown?.isActive == false) undoStartedAt = -1L
  }
  val undo = countdown?.let {
    FluidUndoCountdown(it, onUndo = {
      archived = archived - batch
      undoStartedAt = -1L
    }, accessibilityLabel = "Annulla l'archiviazione")
  }

  FluidAdaptiveNavigation(
    items = MailTabs,
    selectedRoute = tab,
    onSelect = { tab = it.route },
    modifier = Modifier.navigationBarsPadding().testTag(SelectionSceneTags.MailNavigation),
  ) { _ ->
    FluidListDetailScaffold(
      ambient = null,
      list = { _ ->
        FluidScreen(
          title = "Posta in arrivo",
          onBack = onBack,
          modifier = Modifier.testTag(SelectionSceneTags.MailList),
          toolbar = {
            if (selecting) {
              selection(
                state = selection,
                count = selection.countIn(visible.size),
                onSelectAll = { selection.selectAll(snapshot = "posta-${visible.size}") },
                onDone = { selecting = false },
                actions = FluidSelectionActions.mail(
                  onDelete = putAway,
                  onArchive = putAway,
                  onMove = putAway,
                  onMarkRead = { read = read + chosen(); exit() },
                ),
              )
            } else {
              selectButton(onClick = { selecting = true })
              if (undo != null) undoCountdown("annulla-archivio", undo)
            }
          },
        ) {
          item(key = "messaggi") {
            FluidListGroup {
              visible.forEachIndexed { index, message ->
                if (index > 0) FluidListDivider()
                val picked = selecting && selection.isSelected(message.id)
                FluidListRow(
                  title = message.from,
                  subtitle = "${message.subject} · ${message.preview}",
                  subtitleMaxLines = 2,
                  meta = if (message.id in read) null else "Nuova",
                  leadingTile = false,
                  disclosure = false,
                  selected = picked,
                  modifier = Modifier.testTag(SelectionSceneTags.message(message.id)),
                  leading = {
                    FluidSelectionLeading(selecting = selecting, selected = picked) {
                      FluidToneTile(FluidTone.Primary) { Text(message.from.take(1), fontWeight = FontWeight.SemiBold) }
                    }
                  },
                  onClick = { if (selecting) selection.toggle(message.id) else open = message.id },
                  onLongClick = if (selecting) null else ({ selecting = true; selection.select(message.id) }),
                )
              }
            }
          }
          item(key = "nota") {
            FluidSectionFootnote(
              text = "«Seleziona», poi tocca tre righe: la tab bar diventa le azioni. Archivia: la selezione si chiude e " +
                "resta un solo «Annulla» per tutte e tre. Prova anche il gesto indietro, prima annullato e poi confermato.",
            )
          }
        }
      },
      detail = { MailDetail(SelectionMessages.firstOrNull { it.id == open }) },
    )
  }
}

/** Il dettaglio del messaggio, nel pannello a destra del tablet. */
@Composable
private fun MailDetail(message: SelectionMessage?) {
  FluidScreen(title = message?.subject ?: "Nessun messaggio") {
    item(key = "testo") {
      Text(
        message?.let { "${it.from}\n\n${it.preview}" } ?: "Scegli un messaggio dalla lista.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 8.dp),
      )
    }
  }
}

// --- Notes: «Seleziona tutto» su una cartella che si carica a pagine, le azioni in alto --------------------------------

/** Quante note ha la cartella (il totale della query, che l'app conosce) e quante se ne caricano per volta. */
internal const val SelectionNotesTotal = 120
internal const val SelectionNotesPage = 30
private const val NotesMoreKey = "altre"

/**
 * Notes: una cartella di 120 note caricate trenta alla volta. «Seleziona tutto» sceglie **anche le note non ancora
 * caricate** (la forma `AllExcept` dello stato: l'engine non carica e non conta righe, il numero viene dall'app con
 * `countIn`), e togliere una nota la esclude. La pagina non ha una barra in basso: le azioni stanno in alto
 * ([FluidSelectionBarPlacement.Top]), in «Altro» se non c'è posto.
 */
@Composable
internal fun SelectionNotesScene(onBack: () -> Unit) {
  val selection = rememberFluidSelectionState<Int>()
  var selecting by rememberSaveable { mutableStateOf(false) }
  var removed by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  var loaded by rememberSaveable { mutableIntStateOf(SelectionNotesPage) }
  val list = rememberLazyListState()
  val notes = (0 until SelectionNotesTotal).filter { it !in removed }
  val shown = notes.take(loaded)
  // La paginazione finta: quando la riga «Carico altre note» entra nello schermo arriva la pagina dopo.
  LaunchedEffect(list) {
    snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == NotesMoreKey } }.collect { atEnd ->
      if (atEnd && loaded < SelectionNotesTotal) loaded = (loaded + SelectionNotesPage).coerceAtMost(SelectionNotesTotal)
    }
  }
  val exit = { selection.clear(); selecting = false }
  val delete = {
    removed = removed + notes.filter { selection.isSelected(it) }
    exit()
  }
  FluidScreen(
    title = "Lavoro",
    onBack = onBack,
    listState = list,
    toolbar = {
      if (selecting) {
        selection(
          state = selection,
          count = selection.countIn(notes.size),
          onSelectAll = { selection.selectAll(snapshot = "lavoro-${notes.size}") },
          onDone = { selecting = false },
          placement = FluidSelectionBarPlacement.Top,
        ) {
          // Le azioni su misura: si spengono da sole a zero con `selectionHasItems`, come quelle dei preset.
          button("sposta", FluidIcons.DriveFileMove, "Sposta", onClick = exit, enabled = selectionHasItems)
          button("elimina", FluidIcons.Delete, "Elimina", onClick = delete, role = FluidButtonRole.Destructive,
            enabled = selectionHasItems)
        }
      } else {
        selectButton(onClick = { selecting = true })
      }
    },
  ) {
    item(key = "intestazione") { FluidSectionHeader(title = "Note", detail = "Caricate ${shown.size} di ${notes.size}") }
    item(key = "note") {
      FluidListGroup {
        shown.forEachIndexed { index, note ->
          if (index > 0) FluidListDivider()
          val picked = selecting && selection.isSelected(note)
          FluidListRow(
            title = "Nota ${note + 1}",
            subtitle = if (note % 3 == 0) "Con un elenco di cose da fare" else "Appunti della settimana",
            leadingTile = false,
            disclosure = false,
            selected = picked,
            modifier = Modifier.testTag(SelectionSceneTags.note(note)),
            // Senza piastrella: lo spazio si apre per il segno, sempre della stessa misura, e la riga non salta.
            leading = { FluidSelectionLeading(selecting = selecting, selected = picked) },
            onClick = { if (selecting) selection.toggle(note) },
          )
        }
      }
    }
    item(key = NotesMoreKey) {
      if (shown.size < notes.size) FluidSectionFootnote(text = "Carico altre note…")
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "«Seleziona», poi «Seleziona tutto» (in «Altro» solo se la barra non ha posto): il conteggio dice tutte " +
          "le note della cartella, anche quelle non ancora caricate; togline una e il conteggio scende di uno.",
      )
    }
  }
}

// --- Gallery: la capsula delle azioni galleggia sulla griglia ----------------------------------------------------------

internal const val SelectionPhotoCount = 60
private const val SelectionPhotoColumns = 3

private fun selectionPhotoColor(index: Int): Color = Color.hsv((index * 37f) % 360f, 0.55f, 0.80f)

/**
 * Gallery: la libreria a griglia. In selezione le azioni (Condividi, Preferiti, Aggiungi all'album, Nascondi, Elimina)
 * stanno in una **capsula che galleggia** sopra la griglia ([FluidSelectionBarPlacement.Floating]): la griglia non le
 * lascia spazio e le passa sotto, come in Foto. Ogni foto scelta ha il segno sopra di sé.
 */
@Composable
internal fun SelectionPhotosScene(onBack: () -> Unit) {
  val selection = rememberFluidSelectionState<Int>()
  var selecting by rememberSaveable { mutableStateOf(false) }
  var hidden by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  var favorites by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  val photos = (0 until SelectionPhotoCount).filter { it !in hidden }
  val chosen = { photos.filter { selection.isSelected(it) }.toSet() }
  val exit = { selection.clear(); selecting = false }
  val remove = { hidden = hidden + chosen(); exit() }
  FluidScreen(
    title = "Libreria",
    onBack = onBack,
    toolbar = {
      if (selecting) {
        selection(
          state = selection,
          count = selection.countIn(photos.size),
          onSelectAll = { selection.selectAll() },
          onDone = { selecting = false },
          placement = FluidSelectionBarPlacement.Floating,
          actions = FluidSelectionActions.gallery(
            onShare = exit,
            onDelete = remove,
            onFavorite = { favorites = favorites + chosen(); exit() },
            onAddToAlbum = exit,
            onHide = remove,
          ),
        )
      } else {
        selectButton(onClick = { selecting = true })
      }
    },
  ) {
    item(key = "griglia") {
      Column(Modifier.testTag(SelectionSceneTags.PhotosGrid), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        photos.chunked(SelectionPhotoColumns).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            row.forEach { photo -> SelectionPhoto(photo, selecting, selection.isSelected(photo), photo in favorites) {
              if (selecting) selection.toggle(photo) else { selecting = true; selection.select(photo) }
            } }
            repeat(SelectionPhotoColumns - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "«Seleziona» e tocca qualche foto: la capsula delle azioni galleggia sopra la griglia, che continua a " +
          "scorrerle sotto. La tab bar dell'app, se c'è, resta al suo posto.",
      )
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SelectionPhoto(
  index: Int,
  selecting: Boolean,
  selected: Boolean,
  favorite: Boolean,
  onClick: () -> Unit,
) {
  Box(
    Modifier.weight(1f).aspectRatio(1f).testTag(SelectionSceneTags.photo(index))
      .clip(ContinuousCornerShape(FluidRadius.Small)).background(selectionPhotoColor(index))
      .semantics {
        contentDescription = "Foto ${index + 1}${if (favorite) ", preferita" else ""}"
        if (selecting) this.selected = selected
      }
      .fluidPressable(onClick = onClick, role = Role.Button),
  ) {
    if (selecting) {
      FluidSelectionMark(
        selected = selected,
        onPhoto = true,
        contentDescription = null,
        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
      )
    }
  }
}

// --- File: la barra in basso della pagina, senza tab bar, cambia azioni --------------------------------------------------

internal data class SelectionFile(val id: Int, val name: String, val detail: String, val kind: Int)

internal val SelectionFiles = listOf(
  SelectionFile(0, "Preventivo cucina.pdf", "2,4 MB · ieri", 0),
  SelectionFile(1, "Vacanze", "48 elementi", 1),
  SelectionFile(2, "Foto del lago.jpg", "3,1 MB · lunedì", 2),
  SelectionFile(3, "Contratto affitto.pdf", "820 kB · 12 nov", 0),
  SelectionFile(4, "Playlist corsa", "22 brani", 3),
  SelectionFile(5, "Appunti.txt", "4 kB · oggi", 4),
  SelectionFile(6, "Archivio 2025.zip", "1,2 GB · 3 gen", 5),
  SelectionFile(7, "Scansione documento.pdf", "1,1 MB · 2 nov", 0),
  SelectionFile(8, "Progetti", "12 elementi", 1),
  SelectionFile(9, "Ricevuta.jpg", "640 kB · 28 ott", 2),
)

/**
 * File: una pagina senza tab bar. Fuori selezione la sua barra in basso ha «Filtra» e «Nuova cartella»; in selezione
 * **la stessa barra** cambia azioni (Condividi, Duplica, Sposta, Elimina, Comprimi in «Altro») nello stesso vetro, con
 * lo scambio sfocato e la larghezza che scorre. È la traccia `selezione-senza-tabbar` del banco.
 */
@Composable
internal fun SelectionFilesScene(onBack: () -> Unit) {
  val selection = rememberFluidSelectionState<Int>()
  var selecting by rememberSaveable { mutableStateOf(false) }
  var removed by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
  val files = SelectionFiles.filter { it.id !in removed }
  val exit = { selection.clear(); selecting = false }
  FluidScreen(
    title = "Documenti",
    onBack = onBack,
    toolbar = {
      if (selecting) {
        selection(
          state = selection,
          count = selection.countIn(files.size),
          onSelectAll = { selection.selectAll() },
          onDone = { selecting = false },
          actions = FluidSelectionActions.files(
            onShare = exit,
            onMove = exit,
            onDelete = { removed = removed + files.filter { selection.isSelected(it.id) }.map { it.id }; exit() },
            onDuplicate = exit,
            onCompress = exit,
          ),
        )
      } else {
        selectButton(onClick = { selecting = true })
        group("comandi", placement = FluidToolbarPlacement.BottomBar) {
          button("filtra", FluidIcons.FilterList, "Filtra", onClick = {})
          flexibleSpacer()
          button("nuova-cartella", FluidIcons.Add, "Nuova cartella", onClick = {})
        }
      }
    },
  ) {
    item(key = "file") {
      FluidListGroup {
        files.forEachIndexed { index, file ->
          if (index > 0) FluidListDivider()
          val picked = selecting && selection.isSelected(file.id)
          FluidListRow(
            title = file.name,
            subtitle = file.detail,
            leadingTile = false,
            disclosure = false,
            selected = picked,
            modifier = Modifier.testTag(SelectionSceneTags.file(file.id)),
            leading = {
              FluidSelectionLeading(selecting = selecting, selected = picked) {
                FluidToneTile(if (file.kind == 1) FluidTone.Info else FluidTone.Neutral) {
                  FluidIcon(fileIcon(file.kind), contentDescription = null)
                }
              }
            },
            onClick = { if (selecting) selection.toggle(file.id) },
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Qui non c'è una tab bar: la barra in basso della pagina diventa quella delle azioni nello stesso " +
          "vetro, e «Fine» la riporta a «Filtra» e «Nuova cartella».",
      )
    }
  }
}

private fun fileIcon(kind: Int) = when (kind) {
  0 -> FluidIcons.PictureAsPdf
  1 -> FluidIcons.Folder
  2 -> FluidIcons.Image
  3 -> FluidIcons.MusicNote
  5 -> FluidIcons.FolderZip
  else -> FluidIcons.Description
}
