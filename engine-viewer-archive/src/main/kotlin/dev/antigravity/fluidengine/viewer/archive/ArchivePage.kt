package dev.antigravity.fluidengine.viewer.archive

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidReduceTransparency
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.fluidFormatFileSize
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoAction
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.page.FluidPageMove
import dev.antigravity.fluidengine.ui.page.LocalFluidPageTransition
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.text.Collator

/** The D Mix archive wash, chosen by eye on 22 November 2026. */
private object ArchiveMixProfile {
  const val Tint = .20f
  /** The last 12% is the viewer backdrop window chosen in F6C.1. */
  const val BackdropStart = .88f
}

internal fun archiveBackdropAlpha(move: FluidPageMove?, progress: Float): Float = when (move) {
  null, FluidPageMove.None -> 1f
  FluidPageMove.Push -> {
    val fraction = ((progress - ArchiveMixProfile.BackdropStart) / (1f - ArchiveMixProfile.BackdropStart)).coerceIn(0f, 1f)
    fraction * fraction * (3f - 2f * fraction)
  }
  else -> 0f
}

private enum class ArchiveSort(val label: String) { Name("nome"), Size("dimensione"), Date("data") }
private enum class ArchiveFilter(val label: String) { All("tutto"), Folders("cartelle"), Files("file"), Protected("voci cifrate") }
private enum class ArchiveMenu(val title: String) { Sort("Ordina per"), Filter("Mostra"), Encoding("Codifica dei nomi") }

@Composable
internal fun ArchivePage(session: ArchiveSession, viewer: FluidViewerCustomScope) {
  val index = session.index
  val states = remember(session) { mutableMapOf<String, LazyListState>() }
  var folder by rememberSaveable { mutableStateOf("") }
  var sort by rememberSaveable { mutableStateOf(ArchiveSort.Name) }
  var filter by rememberSaveable { mutableStateOf(ArchiveFilter.All) }
  var menu by remember { mutableStateOf<ArchiveMenu?>(null) }
  LaunchedEffect(index) {
    if (folder.isNotEmpty() && index.tree.children(folder.substringBeforeLast('/', "")).none { it.path == folder }) folder = ""
  }
  BackHandler(enabled = viewer.active && folder.isNotEmpty() && session.preview == null && session.pending == null && menu == null) {
    folder = folder.substringBeforeLast('/', "")
  }

  val collator = remember { Collator.getInstance() }
  val (folders, files) = remember(index, folder, sort, filter, collator) {
    val shown = index.tree.children(folder).filter {
      when (filter) {
        ArchiveFilter.All -> true
        ArchiveFilter.Folders -> it.kind == ArchiveKind.Folder
        ArchiveFilter.Files -> it.kind == ArchiveKind.File
        ArchiveFilter.Protected -> it.encrypted || it.kind == ArchiveKind.Folder
      }
    }.sortedWith(compareBy<ArchiveItem> {
      when (sort) {
        ArchiveSort.Name -> 0L
        ArchiveSort.Size -> -(it.bytes ?: -1L)
        ArchiveSort.Date -> -(it.modifiedSeconds ?: 0L)
      }
    }.thenComparator { first, second -> collator.compare(first.name, second.name) })
    shown.partition { it.kind == ArchiveKind.Folder }
  }

  val flat = archiveSolidMaterial()
  val scheme = MaterialTheme.colorScheme
  val transition = LocalFluidPageTransition.current
  val backdrop = rememberGlassBackdrop()
  val wash = Brush.verticalGradient(listOf(scheme.primary.copy(alpha = ArchiveMixProfile.Tint),
    scheme.surface, scheme.tertiary.copy(alpha = ArchiveMixProfile.Tint)))
  val top = viewer.contentPadding.calculateTopPadding()
  val bottom = viewer.contentPadding.calculateBottomPadding()
  val fontScale = LocalDensity.current.fontScale
  val position = states.getOrPut(folder) { LazyListState() }
  BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Box(Modifier.matchParentSize().glassBackdropSource(backdrop).drawBehind {
      val alpha = archiveBackdropAlpha(transition?.kind, transition?.progress ?: 1f)
      if (alpha > 0f) {
        if (flat) drawRect(scheme.surface, alpha = alpha) else drawRect(wash, alpha = alpha)
      }
    })
    val columns = (maxWidth.value / (138f * fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 3)
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxSize(),
      state = position,
      contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top, bottom = bottom),
    ) {
      item("archive-heading") {
        Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(FluidIcons.FolderZip, null, Modifier.size(40.dp), tint = scheme.primary)
            Column(Modifier.weight(1f)) {
              Text(session.request.source.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
              Text("${index.format} · ${index.items.size} voci", style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant)
            }
          }
          ArchiveBreadcrumbs(folder, { folder = it })
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidChip("Ordina per ${sort.label}", false, { menu = ArchiveMenu.Sort })
            FluidChip("Mostra ${filter.label}", filter != ArchiveFilter.All, { menu = ArchiveMenu.Filter })
            if (session.format == ArchiveFormat.Zip) FluidChip(session.encoding.label, false,
              { menu = ArchiveMenu.Encoding }, enabled = !session.busy)
          }
        }
      }
      item("archive-status") { ArchiveStatus(session) }
      items(folders.chunked(columns), key = { "folder:${it.first().path}" }) { tiles ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (tile in tiles) {
            ArchiveFolderTile(tile, index.tree.children(tile.path).size, flat, backdrop, Modifier.weight(1f)) {
              folder = tile.path
            }
          }
          repeat(columns - tiles.size) { Spacer(Modifier.weight(1f)) }
        }
      }
      items(files.size, key = { "entry:${files[it].ordinal}" }) { i ->
        val item = files[i]
        val subtitle = when {
          item.kind == ArchiveKind.Link -> "Collegamento"
          item.kind == ArchiveKind.Special -> "Voce speciale"
          item.encrypted -> "Protetto da password"
          else -> item.bytes?.let(::fluidFormatFileSize).orEmpty()
        }
        val open: (() -> Unit)? = if (!session.busy && session.canOpen(item)) ({ session.requestOpen(item) }) else null
        // The segments share one silhouette while the list composes only the visible rows.
        FluidListGroup(segment = when {
          files.size == 1 -> FluidGroupSegment.Whole
          i == 0 -> FluidGroupSegment.First
          i == files.lastIndex -> FluidGroupSegment.Last
          else -> FluidGroupSegment.Middle
        }) {
          if (i != 0) FluidListDivider()
          FluidListRow(title = item.name, subtitle = subtitle, disclosure = false,
            leading = { Icon(fileIcon(item), null, tint = scheme.primary) }, onClick = open)
        }
      }
      if (files.isEmpty() && folders.isEmpty() && index.problem == null && index.protection != ArchiveProtection.Rar &&
        index.protection != ArchiveProtection.SevenZipHeaders) item("empty") {
        Text(if (filter == ArchiveFilter.All) "Cartella vuota" else "Nessuna voce con questo filtro",
          Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
  menu?.let { current ->
    val close = { menu = null }
    when (current) {
      ArchiveMenu.Sort -> ArchiveChoices(current.title, ArchiveSort.entries, sort, { it.label }, close) {
        sort = it
      }
      ArchiveMenu.Filter -> ArchiveChoices(current.title, ArchiveFilter.entries, filter, { it.label }, close) {
        filter = it
      }
      ArchiveMenu.Encoding -> ArchiveChoices(current.title, ArchiveEncoding.entries, session.encoding, { it.label }, close) {
        session.changeEncoding(it)
      }
    }
  }
  if (session.pending != null) ArchivePassword(session)
  if (viewer.active) session.preview?.let { document ->
    Dialog(session::dismissPreview, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      CompositionLocalProvider(LocalFluidGlassModalHostState provides null) {
        FluidViewerInfoProvider(info = {
          document.fluidArchiveViewerInfo() ?: FluidViewerInfo(document.file.info, actions = listOfNotNull(
            (document.content as? FluidViewerContent.Unknown)?.openWith
              ?.let { FluidViewerInfoAction("Apri con…", it) },
          ))
        }) {
          FluidFileViewer(count = 1, initial = 0, content = { document.content }, onBack = session::dismissPreview,
            onDismissRequest = session::dismissPreview, modifier = Modifier.fillMaxSize())
        }
      }
    }
  }
}

@Composable
private fun ArchiveBreadcrumbs(folder: String, go: (String) -> Unit) {
  val parts = folder.split('/').filter(String::isNotEmpty)
  val scroll = rememberScrollState()
  LaunchedEffect(folder, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
  Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    FluidChip("Archivio", parts.isEmpty(), { go("") })
    for ((i, part) in parts.withIndex()) {
      Icon(FluidIcons.ChevronRight, null, Modifier.size(16.dp))
      FluidChip(part, i == parts.lastIndex, { go(parts.take(i + 1).joinToString("/")) })
    }
  }
}

@Composable
private fun ArchiveFolderTile(item: ArchiveItem, count: Int, flat: Boolean, backdrop: GlassBackdropState,
  modifier: Modifier, open: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val material = if (flat) Modifier.background(scheme.surfaceContainerHigh, shape)
  else Modifier.fluidGlass(FluidGlass.Regular, shape, backdrop)
  Column(modifier.fluidPressable(onClick = open, role = Role.Button).then(material).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(FluidIcons.Folder, null, Modifier.size(24.dp), tint = scheme.primary)
    Text(item.name, style = MaterialTheme.typography.titleSmall)
    Text(if (count == 1) "1 voce" else "$count voci", style = MaterialTheme.typography.bodySmall,
      color = scheme.onSurfaceVariant)
  }
}

@Composable
private fun ArchiveStatus(session: ArchiveSession) {
  val archive = session.index
  val incomplete = archive.problem
  val locked = archive.protection == ArchiveProtection.Rar ||
    (archive.sevenZipFallback && !SevenZipAes.available)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (locked || incomplete != null || archive.hiddenPaths > 0) {
      FluidListGroup(Modifier.padding(bottom = 8.dp)) {
        val label = if (locked) "Archivio protetto" else incomplete?.label ?: "Percorsi ignorati"
        val note = when {
          locked -> "Puoi provare ad aprirlo con un’altra app."
          incomplete == ArchiveProblem.Limit -> "Limite di 128 MiB per voce, 512 MiB in totale e rapporto 200:1 sopra 1 MiB."
          incomplete != null -> "Sono visibili le voci lette prima dell’errore."
          else -> "${archive.hiddenPaths} voci con nomi o percorsi non validi."
        }
        FluidListRow(label, note, leading = { Icon(FluidIcons.Warning, null) }, disclosure = false)
      }
    }
    if (locked || incomplete == ArchiveProblem.Limit) FluidButton("Apri con…", session::requestOpenOriginal,
      modifier = Modifier.padding(bottom = 8.dp), style = FluidButtonStyle.Tinted, enabled = !session.busy)
    if (session.canUnlockHeaders) FluidButton("Inserisci password", session::requestUnlock, enabled = !session.busy)
    val progress = session.progress
    if (progress != null) {
      LinearProgressIndicator(progress = { progress.completedFiles.toFloat() / progress.totalFiles.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth())
      Text("${progress.completedFiles} di ${progress.totalFiles} file · ${fluidFormatFileSize(progress.writtenBytes)}",
        style = MaterialTheme.typography.bodyMedium)
    } else if (session.busy) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Lettura in corso", style = MaterialTheme.typography.bodyMedium)
      }
    }
    if (session.pending == null && session.preview == null) session.problem?.let {
      Text(it.label, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun ArchivePassword(session: ArchiveSession) {
  var value by remember { mutableStateOf("") }
  val submit = {
    if (value.isNotEmpty()) {
      session.submitPassword(value.toCharArray())
      value = ""
    }
  }
  FluidAlert(title = "Password dell’archivio", onDismissRequest = session::dismissPassword,
    message = session.problem?.label, actions = listOf(
      FluidAlertAction(label = "Annulla", onClick = session::dismissPassword),
      FluidAlertAction(label = "Apri", enabled = value.isNotEmpty(), onClick = submit),
    )) {
    FluidTextField(value = value, onValueChange = { if (it.length <= 256 && '\u0000' !in it) value = it },
      label = "Password", secret = true, showClearButton = false,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { submit() }))
  }
}

@Composable
private fun <T> ArchiveChoices(title: String, values: List<T>, selected: T, label: (T) -> String,
  close: () -> Unit, choose: (T) -> Unit) {
  FluidAlert(title = title, onDismissRequest = close, actions = listOf(FluidAlertAction(label = "Annulla", onClick = close))) {
    FluidListGroup {
      for (value in values) FluidListRow(title = label(value), subtitle = "", selected = value == selected,
        disclosure = false, onClick = { choose(value); close() })
    }
  }
}

private fun fileIcon(item: ArchiveItem): ImageVector = when {
  item.kind == ArchiveKind.Link -> FluidIcons.Link
  item.encrypted -> FluidIcons.Lock
  else -> when (FluidFileKind.from(null, item.name)) {
    FluidFileKind.Pdf -> FluidIcons.PictureAsPdf
    FluidFileKind.Image -> FluidIcons.Image
    FluidFileKind.Archive -> FluidIcons.FolderZip
    else -> FluidIcons.Description
  }
}

@Composable
private fun archiveSolidMaterial(): Boolean {
  val resolver = LocalContext.current.contentResolver
  val keys = remember { buildList {
    if (Build.VERSION.SDK_INT >= 31) add("disable_window_blurs")
    if (Build.MANUFACTURER.equals("samsung", true)) add("accessibility_reduce_transparency")
  } }
  fun reduced() = keys.any { key -> runCatching { Settings.Global.getInt(resolver, key, 0) == 1 }.getOrDefault(false) }
  var system by remember(resolver) { mutableStateOf(reduced()) }
  DisposableEffect(resolver, keys) {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) { system = reduced() }
    }
    for (key in keys) runCatching { resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer) }
    onDispose { if (keys.isNotEmpty()) resolver.unregisterContentObserver(observer) }
  }
  val focused = LocalWindowInfo.current.isWindowFocused
  LaunchedEffect(focused) { if (focused) system = reduced() }
  return system || LocalFluidReduceTransparency.current || LocalFluidSystemContrast.current >= FluidSystemContrast.High
}
