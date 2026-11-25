package dev.antigravity.fluidengine.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonLeading
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonList
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonTile
import dev.antigravity.fluidengine.ui.fluid.fluidFormatFileSize
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CheckCircle
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.FormatListBulleted
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.History
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.toolbar.selection
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoRow
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoSection
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerResumeProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerThumbnail
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidFileViewerRoute
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidViewerThumbnail
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.viewer.FluidFileThumbnailImage
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.FluidViewerType
import dev.antigravity.fluidengine.viewer.fileActions
import dev.antigravity.fluidengine.viewer.rememberFluidFileThumbnail
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import dev.antigravity.fluidengine.viewer.rememberFluidViewerResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * Le parole dello sfoglia-file (F8.18), in italiano di serie: un'app in un'altra lingua passa le sue. I numeri sono già
 * formattati da chi chiama.
 */
@Immutable
data class FluidFileBrowserStrings(
  val browse: String = "Sfoglia",
  val recents: String = "Recenti",
  val recentCount: (Int) -> String = { if (it == 1) "1 file" else "$it file" },
  val locations: String = "Posizioni",
  val addLocation: String = "Aggiungi una posizione…",
  val addLocationDetail: String = "Una cartella del telefono o di un'app, come Drive",
  val removeLocation: String = "Togli dalle posizioni",
  val locationsFootnote: String = "Le cartelle che aggiungi restano qui. Tieni premuto su una cartella per toglierla: " +
    "i file restano dove sono.",
  val phone: String = "Sul telefono",
  val downloads: String = "Download",
  val media: String = "Foto e video",
  val folder: String = "Cartella",
  val more: String = "Altro",
  val select: String = "Seleziona",
  val icons: String = "Icone",
  val list: String = "Elenco",
  val name: String = "Nome",
  val kind: String = "Tipo",
  val date: String = "Data",
  val size: String = "Dimensione",
  val ascendingName: String = "Dalla A alla Z",
  val descendingName: String = "Dalla Z alla A",
  val newestFirst: String = "Prima i più recenti",
  val oldestFirst: String = "Prima i meno recenti",
  val largestFirst: String = "Prima i più grandi",
  val smallestFirst: String = "Prima i più piccoli",
  val searchIn: (String) -> String = { "Cerca in $it" },
  val items: (Int) -> String = { if (it == 1) "1 elemento" else "$it elementi" },
  val loading: String = "Caricamento",
  val failedTitle: String = "Non riesco a leggere la cartella",
  val failedDetail: String = "La posizione non risponde o il permesso non c'è più.",
  val retry: String = "Riprova",
  val emptyTitle: String = "Cartella vuota",
  val emptyDetail: String = "Qui non c'è ancora niente.",
  val noResults: (String) -> String = { "Nessun risultato per «$it»" },
  val noResultsDetail: String = "Prova con un'altra parola.",
  val noRecentsTitle: String = "Ancora niente",
  val noRecentsDetail: String = "I file che apri compaiono qui, dal più recente.",
  val open: String = "Apri",
  val share: String = "Condividi",
  val scrubber: String = "Scorri la cartella",
  val infoFile: String = "File",
  val infoKind: String = "Tipo",
  val infoSize: String = "Dimensione",
  val infoModified: String = "Modificato",
  val infoWhere: String = "Posizione",
  val infoDetails: String = "Dettagli",
  /** Il nome di un tipo per TalkBack e per il pannello info. */
  val kindName: (FluidFileKind) -> String = { kind ->
    when (kind) {
      FluidFileKind.Image -> "Immagine"
      FluidFileKind.Video -> "Video"
      FluidFileKind.Audio -> "Audio"
      FluidFileKind.Pdf -> "PDF"
      FluidFileKind.Document -> "Documento"
      FluidFileKind.Spreadsheet -> "Foglio di calcolo"
      FluidFileKind.Presentation -> "Presentazione"
      FluidFileKind.Archive -> "Archivio"
      FluidFileKind.Text -> "Testo"
      FluidFileKind.Executable -> "Eseguibile"
      else -> "File"
    }
  },
)

/**
 * Lo stato dello sfoglia-file: come si mostrano le cartelle ([layout]) e in che ordine ([sort]), uguali per tutte come
 * in File, e le cartelle aperte, tenute (al più [MaxFolders]) perché tornando indietro siano ancora lì, allo stesso
 * punto. [layout] e [sort] sopravvivono alla ricreazione; le cartelle si rileggono.
 */
@Stable
class FluidFileBrowserState internal constructor(
  private val scope: CoroutineScope,
  layout: FluidFileLayout = FluidFileLayout.Grid,
  sort: FluidFileSort = FluidFileSort(),
) {
  var layout: FluidFileLayout by mutableStateOf(layout)
  var sort: FluidFileSort by mutableStateOf(sort)
    private set

  private val folders = LinkedHashMap<String, FluidFileFolderState>(16, 0.75f, true)
  internal val known = HashMap<String, FluidFileSource>()

  /** Cambia l'ordine di tutte le cartelle aperte (i Recenti hanno il loro, dall'ultimo aperto, come in File). */
  fun sortBy(sort: FluidFileSort) {
    this.sort = sort
    folders.values.filter { it.source.id != FluidFileRecents.SourceId }.forEach { it.sortBy(sort) }
  }

  /** La cartella [folder] (null: la radice) di [source], aperta o ritrovata. */
  fun folder(source: FluidFileSource, folder: String?, name: String): FluidFileFolderState {
    known[source.id] = source
    val key = "${source.id}/${folder.orEmpty()}"
    val recents = source.id == FluidFileRecents.SourceId
    val state = folders.getOrPut(key) {
      FluidFileFolderState(source, folder, name, scope, if (recents) FluidFileSort.of(FluidFileSortKey.Date) else sort)
    }
    while (folders.size > MaxFolders) folders.remove(folders.keys.first())
    if (!recents) state.sortBy(sort)
    return state
  }

  /** La cartella già aperta, se c'è. */
  internal fun opened(sourceId: String, folder: String?): FluidFileFolderState? = folders["$sourceId/${folder.orEmpty()}"]

  companion object {
    /** Le cartelle tenute aperte: 12, a occhio (25 novembre 2026). */
    const val MaxFolders: Int = 12

    internal fun saver(scope: CoroutineScope) = listSaver<FluidFileBrowserState, Any>(
      save = { listOf(it.layout.toString(), it.sort.key.toString(), it.sort.descending) },
      restore = { saved ->
        val layout = if (saved[0] == FluidFileLayout.List.toString()) FluidFileLayout.List else FluidFileLayout.Grid
        val key = FluidFileSortKey.all.firstOrNull { it.toString() == saved[1] } ?: FluidFileSortKey.Name
        FluidFileBrowserState(scope, layout, FluidFileSort(key, saved[2] as Boolean))
      },
    )
  }
}

/** Uno stato dello sfoglia-file che sopravvive alla ricreazione (vista e ordine). */
@Composable
fun rememberFluidFileBrowserState(
  layout: FluidFileLayout = FluidFileLayout.Grid,
  sort: FluidFileSort = FluidFileSort(),
): FluidFileBrowserState {
  val scope = rememberCoroutineScope()
  return rememberSaveable(saver = FluidFileBrowserState.saver(scope)) { FluidFileBrowserState(scope, layout, sort) }
}

/** I tag delle prove (e delle riprese). */
object FluidFileBrowserTags {
  const val Browse = "fluid-files:sfoglia"
  const val Recents = "fluid-files:recenti"
  const val AddLocation = "fluid-files:aggiungi"
  const val Folder = "fluid-files:cartella"
  fun location(id: String) = "fluid-files:posizione:$id"
  fun item(id: String) = "fluid-files:elemento:$id"
}

/**
 * **Lo sfoglia-file** (F8.18, `sfoglia-file`): la pagina di vetro per sfogliare, cercare e scegliere i file, come File di
 * iOS 26 (`UIDocumentBrowserViewController`; per scegliere, `fileImporter` di SwiftUI). Una pagina intera con le sue
 * rotte, da mettere dove l'app vuole (una scheda, una rotta, un foglio): tutto si muove con le pagine di `FluidNavHost`.
 *
 * - **Sfoglia**: «Recenti» in cima, poi le **posizioni**: le sorgenti dell'app ([sources]: la sua cartella, un cloud,
 *   gli allegati) e le cartelle concesse dall'utente ([locations], col selettore di sistema: il telefono e le app con un
 *   `DocumentsProvider`), con «Aggiungi una posizione…» in fondo. Come la barra laterale di File sull'iPad.
 * - **Una cartella**: il nome in alto (il titolo grande che si ritira), **Icone** (la griglia con le miniature vere di
 *   `engine-viewer`) o **Elenco**, l'ordine (nome, tipo, data, dimensione, il secondo tocco inverte) e «Seleziona» nel
 *   menu «•••», «Cerca in …» sotto il titolo, lo scorrimento rapido per le cartelle lunghe. Le cartelle stanno prima dei
 *   file. Migliaia di file arrivano a pagine ([FluidFileFolderState]); mentre si aspetta, le tessere vuote; se la
 *   posizione non risponde, «Riprova».
 * - **Aprire**: un file si apre nei visori dell'engine **dalla sua miniatura e ci torna** (il viaggio d'oro di F6C.10),
 *   sfogliando gli altri file della cartella (quelli che si vedono, anche filtrati dalla ricerca), col pannello info,
 *   «Condividi» e «Apri con…»; e **riparte dal punto in cui era stato lasciato** (`rememberFluidViewerResume`). Il
 *   tipo decide il preset; con i moduli di famiglia (`engine-viewer-all`) si aprono anche Office, archivi e il resto.
 * - **Scegliere** ([onPick]): come il selettore di documenti di iOS, un tocco su un file lo sceglie; per sceglierne
 *   più d'uno, «Seleziona» e poi «Apri».
 *
 * **Il confine** (la scheda dell'idea): l'engine sfoglia, ordina, cerca, sceglie e apre. Copiare, spostare,
 * rinominare, cancellare sono dell'app: li dà come azioni della selezione ([selectionActions]).
 *
 * @param sources le sorgenti dell'app, in cima alle posizioni.
 * @param locations le cartelle concesse ([rememberFluidFileLocations]); null: niente «Aggiungi una posizione…».
 * @param recents i recenti ([rememberFluidFileRecents]); null: niente «Recenti».
 * @param onBack il tasto indietro della pagina «Sfoglia»; null se è la radice dell'app.
 * @param onPick la scelta dei file (il modo `fileImporter`): un tocco su un file lo consegna, la selezione ne consegna
 *   più d'uno; null, lo sfoglia-file apre i file nel visore.
 * @param selectionActions le azioni dell'app sui file scelti (Sposta, Elimina…), nella barra della selezione.
 * @param openWith «Apri con…» di un file nel visore; [share] «Condividi» di un file o dei file scelti. Di serie si
 *   condividono gli indirizzi `content://` col foglio di sistema; un file dell'app (`file://`) ha bisogno del
 *   `FileProvider` dell'app, quindi di [share].
 * @param media il contenuto di un video o di un audio col player dell'app (come in `rememberFluidViewerFiles`).
 * @param addLocationHint dove apre il selettore di «Aggiungi una posizione…» (di serie la memoria interna); null, dove
 *   vuole il sistema.
 */
@Composable
fun FluidFileBrowser(
  modifier: Modifier = Modifier,
  state: FluidFileBrowserState = rememberFluidFileBrowserState(),
  sources: List<FluidFileSource> = emptyList(),
  locations: FluidFileLocations? = rememberFluidFileLocations(),
  recents: FluidFileRecents? = rememberFluidFileRecents(),
  onBack: (() -> Unit)? = null,
  onPick: ((List<FluidFileItem>) -> Unit)? = null,
  selectionActions: (FluidToolbarScope.(List<FluidFileItem>) -> Unit)? = null,
  openWith: ((FluidViewerFile) -> Unit)? = null,
  share: ((List<FluidFileItem>) -> Unit)? = null,
  media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null,
  strings: FluidFileBrowserStrings = FluidFileBrowserStrings(),
  addLocationHint: Uri? = FluidFileLocations.PhoneHint,
) {
  val context = LocalContext.current
  val nav = rememberFluidNavController()
  val namespace = rememberFluidGlassNamespace()
  locations?.words = strings
  recents?.words = strings
  val currentSources by rememberUpdatedState(sources)
  val currentLocations by rememberUpdatedState(locations)
  val currentRecents by rememberUpdatedState(recents)
  val currentPick by rememberUpdatedState(onPick)
  val currentActions by rememberUpdatedState(selectionActions)
  val currentShare by rememberUpdatedState(share)
  val currentOpenWith by rememberUpdatedState(openWith)
  val currentMedia by rememberUpdatedState(media)
  val currentStrings by rememberUpdatedState(strings)
  val currentBack by rememberUpdatedState(onBack)
  // Una volta al giorno (alla prima apertura del processo): fuori i recenti dei file che non ci sono più.
  LaunchedEffect(recents) { recents?.let { if (it.entries.isNotEmpty()) it.prune(context) } }

  fun sourceById(id: String): FluidFileSource? =
    currentSources.firstOrNull { it.id == id } ?: currentLocations?.sources?.firstOrNull { it.id == id }
      ?: currentRecents?.source?.takeIf { it.id == id } ?: state.known[id]

  fun folderOf(entry: NavBackStackEntry): FluidFileFolderState? {
    val sourceId = entry.arguments?.getString(ArgSource)?.let(Uri::decode) ?: return null
    val folder = entry.arguments?.getString(ArgFolder)?.let(Uri::decode)?.takeUnless { it == RootFolder }
    state.opened(sourceId, folder)?.let { return it }
    val source = sourceById(sourceId) ?: return null
    val name = entry.arguments?.getString(ArgName)?.let(Uri::decode) ?: source.title
    return state.folder(source, folder, name)
  }

  val open: (FluidFileFolderState, FluidFileItem) -> Unit = { folder, item ->
    val pick = currentPick
    if (item.isFolder) {
      nav.navigate(folderRoute(folder.source.id, item.id, item.name))
    } else if (pick != null) {
      // Come il selettore di documenti di iOS: un tocco sceglie il file. Più file: «Seleziona».
      pick(listOf(item))
    } else {
      val index = folder.files.indexOfFirst { it.id == item.id }
      if (index >= 0) {
        val origin = if (folder.source.id == FluidFileRecents.SourceId) currentRecents?.sourceOf(item) ?: folder.source.id
          else folder.source.id
        currentRecents?.record(origin, item)
        nav.navigate(viewerRoute(folder.source.id, folder.folder, index))
      }
    }
  }

  FluidNavHost(nav, BrowseRoute, modifier.fillMaxSize()) {
    composable(BrowseRoute) {
      BrowsePage(
        sources = currentSources,
        locations = currentLocations,
        recents = currentRecents,
        strings = currentStrings,
        onBack = currentBack,
        hint = addLocationHint,
        onOpen = { source -> nav.navigate(folderRoute(source.id, null, source.title)) },
      )
    }
    composable(FolderRoutePattern, arguments = listOf(
      navArgument(ArgSource) { type = NavType.StringType },
      navArgument(ArgFolder) { type = NavType.StringType },
      navArgument(ArgName) { type = NavType.StringType },
    )) { entry ->
      val folder = remember(entry) { folderOf(entry) }
      if (folder == null) {
        FluidScreen(title = currentStrings.folder, onBack = { nav.popBackStack() }) {
          item { FluidEmptyState(currentStrings.failedTitle, currentStrings.failedDetail) }
        }
      } else {
        FolderPage(folder, state, namespace, currentStrings, onBack = { nav.popBackStack() },
          onOpen = { open(folder, it) }, onPick = currentPick, selectionActions = currentActions,
          share = { items -> currentShare?.invoke(items) ?: shareItems(context, items) },
          canShare = { items -> currentShare != null || items.all { it.uri?.scheme == "content" } })
      }
    }
    fluidFileViewerRoute(ViewerRoutePattern, arguments = listOf(
      navArgument(ArgSource) { type = NavType.StringType },
      navArgument(ArgFolder) { type = NavType.StringType },
      navArgument(ArgIndex) { type = NavType.IntType },
    ), initial = { it.arguments?.getInt(ArgIndex) ?: 0 }, thumbnail = { entry, index ->
      val folder = folderOf(entry)
      folder?.files?.getOrNull(index)?.let { FluidViewerThumbnail(thumbnailKey(folder, it), namespace) }
    }) { entry ->
      val folder = remember(entry) { folderOf(entry) }
      if (folder != null) {
        FileViewerPage(folder, currentStrings, onBack = { nav.popBackStack() },
          openWith = currentOpenWith, media = currentMedia,
          share = currentShare?.let { callback -> { file: FluidViewerFile -> callback(folder.files.filter { it.uri == file.uri }) } },
          onShown = { item ->
            val origin = if (folder.source.id == FluidFileRecents.SourceId) currentRecents?.sourceOf(item) ?: folder.source.id
              else folder.source.id
            currentRecents?.record(origin, item)
          },
          onUnreadable = { uri -> currentRecents?.forget(uri) })
      }
    }
  }
}

@Composable
private fun BrowsePage(
  sources: List<FluidFileSource>,
  locations: FluidFileLocations?,
  recents: FluidFileRecents?,
  strings: FluidFileBrowserStrings,
  onBack: (() -> Unit)?,
  hint: Uri?,
  onOpen: (FluidFileSource) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val add = if (locations != null) rememberFluidAddLocation(locations, hint, onAdded = onOpen) else null
  val granted = locations?.sources.orEmpty()
  FluidScreen(title = strings.browse, onBack = onBack, modifier = Modifier.testTag(FluidFileBrowserTags.Browse)) {
    if (recents != null) item(key = "recenti") {
      FluidListGroup {
        val count = recents.entries.size
        FluidListRow(strings.recents, strings.recentCount(count), modifier = Modifier.testTag(FluidFileBrowserTags.Recents),
          leading = { FluidIcon(FluidIcons.History, null, tint = MaterialTheme.colorScheme.primary) }, leadingTile = false,
          onClick = { onOpen(recents.source) })
      }
    }
    item(key = "posizioni") {
      Column {
        FluidSectionHeader(strings.locations)
        FluidListGroup {
          var first = true
          for (source in sources + granted) {
            if (!first) FluidListDivider()
            first = false
            val removable = locations?.owns(source) == true
            FluidListRow(source.title, source.subtitle ?: "", modifier = Modifier.testTag(FluidFileBrowserTags.location(source.id)),
              // Come Sfoglia di File: l'icona nel colore dell'accento, senza tessera.
              leading = { FluidIcon(source.icon ?: FluidIcons.Folder, null, tint = MaterialTheme.colorScheme.primary) },
              leadingTile = false, onClick = { onOpen(source) },
              contextActions = if (removable) ({
                listOf(FluidContextAction(strings.removeLocation, destructive = true) {
                  scope.launch { locations?.remove(source) }
                })
              }) else null)
          }
          if (add != null) {
            if (!first) FluidListDivider()
            FluidListRow(strings.addLocation, strings.addLocationDetail, modifier = Modifier.testTag(FluidFileBrowserTags.AddLocation),
              leading = { FluidIcon(FluidIcons.Add, null, tint = MaterialTheme.colorScheme.primary) }, leadingTile = false,
              disclosure = false, onClick = add)
          }
        }
        if (add != null) FluidSectionFootnote(strings.locationsFootnote)
      }
    }
  }
}

@Composable
private fun FolderPage(
  folder: FluidFileFolderState,
  state: FluidFileBrowserState,
  namespace: dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace,
  strings: FluidFileBrowserStrings,
  onBack: () -> Unit,
  onOpen: (FluidFileItem) -> Unit,
  onPick: ((List<FluidFileItem>) -> Unit)?,
  selectionActions: (FluidToolbarScope.(List<FluidFileItem>) -> Unit)?,
  share: (List<FluidFileItem>) -> Unit,
  canShare: (List<FluidFileItem>) -> Boolean,
) {
  LaunchedEffect(folder) { folder.start() }
  LaunchedEffect(folder) { folder.source.changes(folder.folder).collect { folder.refresh() } }
  val selection = rememberFluidSelectionState<String>()
  var selecting by rememberSaveable { mutableStateOf(false) }
  val items = folder.items
  val chosen = { items.filter { selection.isSelected(it.id) && !it.isFolder } }
  val locale = LocalConfiguration.current.locales[0]
  val grid = state.layout == FluidFileLayout.Grid
  val recents = folder.source.id == FluidFileRecents.SourceId
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = ((maxWidth - 32.dp) / GridCellWidth).toInt().coerceIn(3, 8)
    val cellPx = with(LocalDensity.current) { ((maxWidth - 32.dp) / columns).roundToPx() }
    FluidScreen(
      title = folder.name,
      onBack = onBack,
      listState = folder.listState,
      // In elenco le righe stanno in un gruppo solo (i pezzi del gruppo si toccano); in griglia lo spazio fra le file.
      itemSpacing = if (grid) GridSpacing else 0.dp,
      modifier = Modifier.testTag(FluidFileBrowserTags.Folder),
      toolbar = {
        if (selecting) {
          selection(selection, count = items.count { selection.isSelected(it.id) }, onSelectAll = {
            items.filter { !it.isFolder }.forEach { selection.select(it.id) }
          }, onDone = { selecting = false }) {
            val picked = chosen()
            if (onPick != null) button("fluid-files.open", FluidIcons.Check, strings.open, { onPick(picked) },
              placement = FluidToolbarPlacement.BottomBar, enabled = picked.isNotEmpty())
            if (canShare(picked)) button("fluid-files.share", FluidIcons.Share, strings.share, { share(picked) },
              placement = FluidToolbarPlacement.BottomBar, enabled = picked.isNotEmpty())
            selectionActions?.invoke(this, picked)
          }
        } else {
          action("fluid-files.more", FluidIcons.MoreHoriz, strings.more) {
            folderMenu(state, folder, strings, onSelect = { selecting = true })
          }
          search(FluidSearchSpec.search(folder.query, folder::search, prompt = strings.searchIn(folder.name)))
        }
      },
      overlay = { backdrop ->
        if (items.size >= FastScrollFrom) FluidFastScroller(folder.listState, label = { index ->
          scrubLabel(folder.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key, items, columns,
            grid, folder.sort, locale)
        }, backdrop = backdrop, contentDescription = strings.scrubber)
      },
    ) {
      when {
        items.isEmpty() && folder.failure != null -> item(key = "errore") {
          FluidEmptyState(strings.failedTitle, strings.failedDetail, strings.retry, folder::retry)
        }
        items.isEmpty() && !folder.complete -> item(key = "attesa") {
          if (grid) GridSkeleton(columns) else FluidSkeletonList(rows = 8, leading = FluidSkeletonLeading.Tile, label = strings.loading)
        }
        items.isEmpty() -> item(key = "vuota") {
          when {
            folder.query.isNotBlank() -> FluidEmptyState(strings.noResults(folder.query), strings.noResultsDetail)
            recents -> FluidEmptyState(strings.noRecentsTitle, strings.noRecentsDetail)
            else -> FluidEmptyState(strings.emptyTitle, strings.emptyDetail)
          }
        }
        grid -> {
          val rows = (items.size + columns - 1) / columns
          items(rows, key = { row -> "r:$row:$columns" }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
              for (column in 0 until columns) {
                val item = items.getOrNull(row * columns + column)
                if (item == null) {
                  Spacer(Modifier.weight(1f))
                } else {
                  FileTile(item, folder, namespace, cellPx, strings, locale, selecting, selection.isSelected(item.id),
                    Modifier.weight(1f)) {
                    if (selecting) { if (!item.isFolder) selection.toggle(item.id) } else onOpen(item)
                  }
                }
              }
            }
          }
        }
        else -> {
          items(items.size, key = { "i:" + items[it].id }) { index ->
            val item = items[index]
            val segment = when {
              items.size == 1 -> FluidGroupSegment.Whole
              index == 0 -> FluidGroupSegment.First
              index == items.size - 1 -> FluidGroupSegment.Last
              else -> FluidGroupSegment.Middle
            }
            FluidListGroup(segment = segment) {
              if (index > 0) FluidListDivider()
              FileRow(item, folder, namespace, cellPx, strings, locale, selecting, selection.isSelected(item.id)) {
                if (selecting) { if (!item.isFolder) selection.toggle(item.id) } else onOpen(item)
              }
            }
          }
        }
      }
      if (items.isNotEmpty() && !folder.complete) item(key = "altre") {
        // La pagina dopo: questa riga entra in composizione poco prima di arrivare in vista (la lista pigra la prepara).
        LaunchedEffect(folder.loadedCount, folder.failure) { if (folder.failure == null) folder.loadMore() }
        if (folder.failure != null) {
          FluidEmptyState(strings.failedTitle, strings.failedDetail, strings.retry, folder::retry)
        } else {
          Text(strings.loading, Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

private fun folderMenu(state: FluidFileBrowserState, folder: FluidFileFolderState, strings: FluidFileBrowserStrings,
  onSelect: () -> Unit) = buildList {
  // I Recenti hanno il loro ordine; le cartelle quello di tutte, come in File.
  val own = folder.source.id == FluidFileRecents.SourceId
  val sort = if (own) folder.sort else state.sort
  val sortBy: (FluidFileSort) -> Unit = { if (own) folder.sortBy(it) else state.sortBy(it) }
  add(FluidContextAction(strings.select, icon = FluidIcons.CheckCircle) { onSelect() })
  add(FluidContextAction(strings.icons, icon = FluidIcons.GridView, startsSection = true,
    selected = state.layout == FluidFileLayout.Grid) { state.layout = FluidFileLayout.Grid })
  add(FluidContextAction(strings.list, icon = FluidIcons.FormatListBulleted,
    selected = state.layout == FluidFileLayout.List) { state.layout = FluidFileLayout.List })
  FluidFileSortKey.all.forEachIndexed { i, key ->
    val chosen = sort.key == key
    val label = when (key) {
      FluidFileSortKey.Kind -> strings.kind
      FluidFileSortKey.Date -> strings.date
      FluidFileSortKey.Size -> strings.size
      else -> strings.name
    }
    add(FluidContextAction(label, startsSection = i == 0, selected = chosen,
      subtitle = if (chosen) sortDirection(sort, strings) else null) {
      sortBy(if (chosen) sort.reversed() else FluidFileSort.of(key))
    })
  }
}

private fun sortDirection(sort: FluidFileSort, strings: FluidFileBrowserStrings): String = when (sort.key) {
  FluidFileSortKey.Date -> if (sort.descending) strings.newestFirst else strings.oldestFirst
  FluidFileSortKey.Size -> if (sort.descending) strings.largestFirst else strings.smallestFirst
  else -> if (sort.descending) strings.descendingName else strings.ascendingName
}

/** L'etichetta della bolla dello scorrimento rapido: l'iniziale, il mese, la dimensione o l'estensione. */
private fun scrubLabel(key: Any?, items: List<FluidFileItem>, columns: Int, grid: Boolean, sort: FluidFileSort,
  locale: Locale): String? {
  val text = key as? String ?: return null
  val item = when {
    text.startsWith("r:") -> text.removePrefix("r:").substringBefore(':').toIntOrNull()?.let { items.getOrNull(it * columns) }
    text.startsWith("i:") -> text.removePrefix("i:").let { id -> items.firstOrNull { it.id == id } }
    else -> null
  } ?: return null
  if (!grid && !text.startsWith("i:")) return null
  return when (sort.key) {
    FluidFileSortKey.Date -> item.modifiedAtMillis?.let {
      java.text.SimpleDateFormat("LLLL yyyy", locale).format(java.util.Date(it))
    }
    FluidFileSortKey.Size -> item.sizeBytes?.let { fluidFormatFileSize(it, locale) }
    FluidFileSortKey.Kind -> item.name.substringAfterLast('.', "").uppercase(locale).ifEmpty { null }
    else -> item.name.firstOrNull()?.uppercase(locale)
  }
}

@Composable
private fun GridSkeleton(columns: Int) {
  Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
    repeat(3) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
        repeat(columns) { FluidSkeletonTile(Modifier.weight(1f)) }
      }
    }
  }
}

/** Il nome per TalkBack di un elemento: nome, tipo, data e dimensione (o quanti elementi ha una cartella). */
private fun describe(item: FluidFileItem, strings: FluidFileBrowserStrings, locale: Locale): String = listOfNotNull(
  item.name,
  if (item.isFolder) strings.folder else strings.kindName(item.kind),
  item.modifiedAtMillis?.let { FluidFormat.date(Instant.ofEpochMilli(it), locale = locale) },
  if (item.isFolder) item.childCount?.let(strings.items) else item.sizeBytes?.let { fluidFormatFileSize(it, locale) },
).joinToString(", ")

/** La chiave del viaggio d'oro di un file: sorgente, cartella, file. */
private fun thumbnailKey(folder: FluidFileFolderState, item: FluidFileItem): String =
  "fluid-files:${folder.source.id}/${folder.folder.orEmpty()}/${item.id}"

/** La miniatura di un elemento: il contenuto vero del file, o la cartella. Riempie la sua scatola. */
@Composable
private fun ItemThumbnail(item: FluidFileItem, sizePx: Int, modifier: Modifier) {
  if (item.isFolder) {
    Box(modifier, contentAlignment = Alignment.Center) { FolderGlyph(Modifier.fillMaxSize(0.82f)) }
  } else {
    val thumbnail = rememberFluidFileThumbnail(item.viewerFile(), sizePx)
    Box(modifier, contentAlignment = Alignment.Center) {
      if (thumbnail != null) FluidFileThumbnailImage(thumbnail, Modifier.fillMaxSize())
    }
  }
}

/**
 * La cartella piena, come le cartelle di File (il dorso con la linguetta e il davanti, nel colore dell'accento; il dorso
 * più chiaro). Disegnata qui perché il set di icone dell'engine ha solo la cartella al tratto. Le proporzioni sono a
 * occhio dalle schermate di iOS 26 (25 novembre 2026): larga 1, alta 0,78, linguetta al 40 %.
 */
@Composable
private fun FolderGlyph(modifier: Modifier) {
  val front = MaterialTheme.colorScheme.primary
  val back = androidx.compose.ui.graphics.lerp(front, MaterialTheme.colorScheme.surface, 0.35f)
  androidx.compose.foundation.Canvas(modifier) {
    val w = size.width
    val h = minOf(size.height, w * 0.78f)
    val top = (size.height - h) / 2f
    val radius = androidx.compose.ui.geometry.CornerRadius(w * 0.07f)
    drawRoundRect(back, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(w * 0.42f, h * 0.3f), radius)
    drawRoundRect(back, androidx.compose.ui.geometry.Offset(0f, top + h * 0.1f), androidx.compose.ui.geometry.Size(w, h * 0.9f), radius)
    drawRoundRect(front, androidx.compose.ui.geometry.Offset(0f, top + h * 0.22f), androidx.compose.ui.geometry.Size(w, h * 0.78f), radius)
  }
}

@Composable
private fun FileTile(
  item: FluidFileItem,
  folder: FluidFileFolderState,
  namespace: dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace,
  sizePx: Int,
  strings: FluidFileBrowserStrings,
  locale: Locale,
  selecting: Boolean,
  selected: Boolean,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Small)
  val description = describe(item, strings, locale)
  Column(
    modifier.testTag(FluidFileBrowserTags.item(item.id))
      .fluidPressable(onClick = onClick, role = Role.Button)
      .semantics(mergeDescendants = true) {
        contentDescription = description
        if (selecting && !item.isFolder) this.selected = selected
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
      val thumb = if (item.isFolder) Modifier.fillMaxSize()
        else Modifier.fillMaxSize(0.92f).fluidViewerThumbnail(FluidViewerThumbnail(thumbnailKey(folder, item), namespace), shape)
          .shadow(1.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
      ItemThumbnail(item, sizePx, thumb)
      if (selecting && !item.isFolder) FluidSelectionMark(selected, Modifier.align(Alignment.BottomEnd).padding(4.dp),
        contentDescription = null)
    }
    Spacer(Modifier.height(6.dp))
    Text(item.name, Modifier.clearAndSetSemantics {}, maxLines = 2, overflow = TextOverflow.MiddleEllipsis,
      textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onSurface)
    val date = item.modifiedAtMillis?.let { FluidFormat.date(Instant.ofEpochMilli(it), locale = locale) }
    val size = if (item.isFolder) item.childCount?.let(strings.items) else item.sizeBytes?.let { fluidFormatFileSize(it, locale) }
    for (line in listOfNotNull(date, size)) {
      Text(line, Modifier.clearAndSetSemantics {}, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
  }
}

@Composable
private fun FileRow(
  item: FluidFileItem,
  folder: FluidFileFolderState,
  namespace: dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace,
  sizePx: Int,
  strings: FluidFileBrowserStrings,
  locale: Locale,
  selecting: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Small)
  val date = item.modifiedAtMillis?.let { FluidFormat.date(Instant.ofEpochMilli(it), locale = locale) }
  val size = if (item.isFolder) item.childCount?.let(strings.items) else item.sizeBytes?.let { fluidFormatFileSize(it, locale) }
  val description = describe(item, strings, locale)
  FluidListRow(
    title = item.name,
    subtitle = listOfNotNull(date, size).joinToString(" · "),
    modifier = Modifier.testTag(FluidFileBrowserTags.item(item.id)).semantics {
      contentDescription = description
      if (selecting && !item.isFolder) this.selected = selected
    },
    titleMaxLines = 2,
    leadingTile = false,
    leading = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (selecting) {
          if (!item.isFolder) FluidSelectionMark(selected, contentDescription = null) else Spacer(Modifier.size(24.dp))
          Spacer(Modifier.size(12.dp))
        }
        val thumb = if (item.isFolder) Modifier.size(RowThumbnail)
          else Modifier.size(RowThumbnail).fluidViewerThumbnail(FluidViewerThumbnail(thumbnailKey(folder, item), namespace), shape)
            .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ItemThumbnail(item, sizePx / 2, thumb)
      }
    },
    disclosure = item.isFolder && !selecting,
    onClick = onClick,
  )
}

@Composable
private fun FileViewerPage(
  folder: FluidFileFolderState,
  strings: FluidFileBrowserStrings,
  onBack: () -> Unit,
  openWith: ((FluidViewerFile) -> Unit)?,
  media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)?,
  share: ((FluidViewerFile) -> Unit)?,
  onShown: (FluidFileItem) -> Unit,
  onUnreadable: (Uri) -> Unit,
) {
  LaunchedEffect(folder) { folder.start() }
  val items = folder.files
  val files = remember(items) { items.mapNotNull { it.viewerFile() } }
  val opened = rememberFluidViewerFiles(files, openWith = openWith, share = share, media = media)
  val resume = rememberFluidViewerResume(opened)
  val locale = LocalConfiguration.current.locales[0]
  // Un file che il visore non riesce più a leggere esce dai recenti (la ripresa toglie da sé la sua posizione).
  val currentUnreadable by rememberUpdatedState(onUnreadable)
  LaunchedEffect(opened) {
    snapshotFlow {
      (0 until opened.count).mapNotNull { index ->
        opened.document(index)?.takeIf { it.failure?.failure == FluidViewerFailure.Unreadable }?.file?.uri
      }
    }.collect { gone -> gone.forEach { currentUnreadable(it) } }
  }
  FluidViewerResumeProvider(resume = resume) {
    FluidViewerInfoProvider(info = { index ->
      items.getOrNull(index)?.let { item -> fileInfo(item, opened.document(index), folder, strings, locale) }
    }) {
      FluidFileViewer(
        count = opened.count,
        content = opened::content,
        onBack = onBack,
        onPageChange = { index -> items.getOrNull(index)?.let(onShown) },
        fileActions = { index -> opened.fileActions(index) },
      )
    }
  }
}

/** Il pannello info di un file: tipo, dimensione, data, posizione, e i dettagli letti dal visore o dalla famiglia. */
private fun fileInfo(
  item: FluidFileItem,
  document: FluidViewerDocument?,
  folder: FluidFileFolderState,
  strings: FluidFileBrowserStrings,
  locale: Locale,
): FluidViewerInfo {
  val kind = strings.kindName(item.kind)
  val size = item.sizeBytes?.let { fluidFormatFileSize(it, locale) }
  val date = item.modifiedAtMillis?.let { FluidFormat.date(Instant.ofEpochMilli(it), locale = locale) }
  val where = if (folder.folder == null) folder.source.title else "${folder.source.title} › ${folder.name}"
  val file = FluidViewerInfoSection(strings.infoFile, listOfNotNull(
    FluidViewerInfoRow(strings.infoKind, kind),
    size?.let { FluidViewerInfoRow(strings.infoSize, it, item.sizeBytes.toString()) },
    date?.let { FluidViewerInfoRow(strings.infoModified, it) },
    FluidViewerInfoRow(strings.infoWhere, where),
  ))
  val details = document?.details.orEmpty().groupBy { it.section ?: strings.infoDetails }.map { (title, rows) ->
    FluidViewerInfoSection(title, rows.map { FluidViewerInfoRow(it.label, it.value) })
  }
  return FluidViewerInfo(
    file = FluidFileInfo(item.name, item.mimeType, item.sizeBytes, modifiedAtMillis = item.modifiedAtMillis),
    subtitle = where,
    facts = listOfNotNull(kind, size, date),
    sections = listOf(file) + details,
  )
}

/** Condivide gli indirizzi `content://` col foglio di sistema (un file dell'app ha bisogno del `FileProvider`). */
internal fun shareItems(context: Context, items: List<FluidFileItem>) {
  val uris = items.mapNotNull { it.uri?.takeIf { uri -> uri.scheme == "content" } }
  if (uris.isEmpty()) return
  val intent = if (uris.size == 1) {
    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first()).setType(items.first().mimeType ?: "*/*")
  } else {
    Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)).setType("*/*")
  }
  intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
  intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  try {
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: ActivityNotFoundException) {
  }
}

private const val BrowseRoute = "fluid-files/sfoglia"
private const val ArgSource = "source"
private const val ArgFolder = "folder"
private const val ArgName = "name"
private const val ArgIndex = "index"
private const val RootFolder = "~"
private const val FolderRoutePattern = "fluid-files/cartella/{$ArgSource}/{$ArgFolder}/{$ArgName}"
private const val ViewerRoutePattern = "fluid-files/file/{$ArgSource}/{$ArgFolder}/{$ArgIndex}"

private fun folderRoute(source: String, folder: String?, name: String) =
  "fluid-files/cartella/${Uri.encode(source)}/${Uri.encode(folder ?: RootFolder)}/${Uri.encode(name)}"

private fun viewerRoute(source: String, folder: String?, index: Int) =
  "fluid-files/file/${Uri.encode(source)}/${Uri.encode(folder ?: RootFolder)}/$index"

/**
 * La tessera della griglia: almeno 112 dp, come le icone di File sull'iPhone (tre colonne a 390 pt di larghezza, viste
 * nelle schermate di iOS 26; a occhio, 25 novembre 2026). Sul tablet le colonne crescono fino a otto.
 */
private val GridCellWidth: Dp = 112.dp
private val GridSpacing: Dp = 12.dp
/** La miniatura di una riga dell'elenco: 44 dp, il bersaglio di Apple. A occhio, 25 novembre 2026. */
private val RowThumbnail: Dp = 44.dp
/** Da quanti elementi compare lo scorrimento rapido. */
private const val FastScrollFrom = 60
