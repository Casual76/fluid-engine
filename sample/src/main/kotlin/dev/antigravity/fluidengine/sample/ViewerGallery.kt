package dev.antigravity.fluidengine.sample

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.antigravity.fluidengine.sample.viewer.*
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import dev.antigravity.fluidengine.viewer.FluidViewerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Il collegamento del visore della galleria: `fluidglass://visori/file/{indice}` (F6C.5). */
internal const val VisoriDeepLink = "fluidglass://visori/file/{index}"

internal fun visoriLink(index: Int): Uri = Uri.parse(VisoriDeepLink.replace("{index}", index.toString()))

/**
 * La galleria «Visori» (F6C.5, rifatta in F6C.15): un file per tipo di ogni famiglia, ognuno aperto dalla sua
 * miniatura nel telaio di serie, con il pannello info della sua famiglia; la scena «Senza moduli», la pagina «Pesi» e
 * la prova in sola lettura con i tipi di Universal Converter. La rotta di serie ha il collegamento e il ripristino.
 * [deps] cambia solo nelle prove su JVM (il player e i decodificatori che Robolectric non ha).
 */
@Composable
internal fun ViewerGallery(deps: VisoriDeps = VisoriDeps.Default, onBack: () -> Unit) {
  val nav = rememberFluidNavController()
  val namespace = rememberFluidGlassNamespace()
  val context = LocalContext.current.applicationContext
  val families = VisoriFamiglia.entries
  val prepared = families.associateWith { family ->
    key(family) {
      produceState<List<FluidViewerFile>?>(null, context) {
        value = VisoriFiles.prepare(context, family.files, family.scene)
      }.value
    }
  }
  val readers = families.associateWith { family ->
    key(family) { rememberVisoriDocuments(family.files, prepared[family], family.scene) }
  }
  val converter = VisoriFiles.converter
  val converterFiles by produceState<List<FluidViewerFile>?>(null, context) {
    value = VisoriFiles.prepare(context, converter, true)
  }
  val senza = VisoriSenzaModuliFiles
  val senzaFiles = families.drop(1).map { prepared[it] }.takeIf { lists -> lists.all { it != null } }
    ?.flatMap { it.orEmpty() }
  val scope = rememberCoroutineScope()
  val readingWidth = (LocalWindowInfo.current.containerSize.width - with(LocalDensity.current) {
    (FluidScreenDefaults.HorizontalPadding * 2).roundToPx()
  }).coerceAtLeast(1)
  val currentReadingWidth by rememberUpdatedState(readingWidth)
  var preparing by remember { mutableStateOf(false) }
  var warmPage by remember { mutableStateOf<VisoriWarmPage?>(null) }
  fun destination(family: VisoriFamiglia, index: Int) =
    if (family == VisoriFamiglia.Serie) "viewer/$index" else "famiglia/${family.scene}/$index"
  fun openTile(family: VisoriFamiglia, index: Int, file: VisoreFile) {
    if (preparing) return
    val reader = readers.getValue(family)
    val destination = destination(family, index)
    warmPage = null
    if (deps !== VisoriDeps.Default || !file.tipo.preparesDocument || index !in 0 until reader.count) {
      nav.navigate(destination)
      return
    }
    preparing = true
    scope.launch {
      // The work is a sibling, so timing out the click does not join a decoder still finishing.
      val preparation = scope.async { prepareVisoriFirstContent(reader, index, currentReadingWidth) }
      try {
        warmPage = withTimeoutOrNull(VisoriPreparationMillis) { preparation.await() }
        if (nav.currentDestination?.route == "collection") nav.navigate(destination)
      } finally {
        preparation.cancel()
        preparing = false
      }
    }
  }
  var resumeVideo by rememberSaveable { mutableStateOf(true) }
  fun thumbnail(file: VisoreFile) = FluidViewerThumbnail(file.id, namespace)
  FluidNavHost(nav, "collection") {
    composable("collection") {
      FluidScreen(title = "Visori", subtitle = "Anteprime dei file", onBack = onBack) {
        item { FluidSectionHeader("File di esempio", detail = "Tocca un file: si apre dalla miniatura e torna lì.") }
        for (family in families) {
          val files = family.files
          item(key = "famiglia-${family.scene}") {
            FluidSectionHeader("${family.title} · ${family.module}", Modifier.testTag("visori-famiglia-${family.scene}"),
              detail = family.sentence)
          }
          items((files.size + 1) / 2, key = { "riga-${family.scene}-$it" }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              repeat(2) { column ->
                val index = row * 2 + column
                val file = files.getOrNull(index)
                if (file == null) Spacer(Modifier.weight(1f))
                else Tile(file, prepared[family]?.getOrNull(index), thumbnail(file),
                  Modifier.weight(1f).testTag("visori-${file.id}")) { openTile(family, index, file) }
              }
            }
          }
        }
        item { FluidSectionHeader("Moduli e pesi", detail = "Che cosa vede un'app senza moduli, e quanto costa ognuno.") }
        item {
          FluidListGroup {
            FluidListRow("Senza moduli", "Gli stessi file con il solo engine-viewer: i tipi che non conosce restano " +
              "sconosciuti, con «Apri con…»", modifier = Modifier.testTag("visori-senza"),
              leading = { Icon(FluidIcons.Draft) }, onClick = { nav.navigate("senza") })
            FluidListDivider()
            FluidListRow("Pesi", "Il peso di ogni modulo per architettura, con le licenze, da families.json",
              modifier = Modifier.testTag("visori-pesi"), leading = { Icon(FluidIcons.Storage) },
              onClick = { nav.navigate("pesi") })
          }
        }
        item { FluidSectionHeader("Il video") }
        item {
          FluidListGroup {
            FluidToggleRow("Riprendi al ritorno", resumeVideo, { resumeVideo = it },
              subtitle = "Il video si ferma quando lo sfogli via. Acceso, riparte se suonava; spento, resta in pausa.")
          }
        }
        item { FluidSectionHeader("La rotta") }
        item {
          FluidListGroup {
            val catalog = VisoriFiles.gallery
            val code = catalog.indexOfFirst { it.tipo == VisoreTipo.Codice }
            FluidListRow("Apri dal collegamento", "fluidglass://visori/file/$code: si apre «${catalog[code].title}», " +
              "senza miniatura", modifier = Modifier.testTag("visori-collegamento"), leading = { Icon(FluidIcons.Link) },
              onClick = { nav.navigate(visoriLink(code)) })
            FluidListDivider()
            FluidListRow("Ripristino", "Sfoglia, poi lascia che il sistema chiuda l'app: riaprendola il visore torna " +
              "sullo stesso file", leading = { Icon(FluidIcons.History) }, disclosure = false)
          }
        }
        item { FluidSectionHeader("Prova in sola lettura") }
        item {
          FluidListGroup {
            FluidListRow("Universal Converter", "I tipi delle sue prove, ognuno col suo preset, con engine-viewer-all " +
              "al posto del dump esadecimale", modifier = Modifier.testTag("visori-converter"),
              leading = { Icon(FluidIcons.SwapHoriz) }, onClick = { nav.navigate("converter") })
          }
        }
      }
    }
    fluidFileViewerRoute("viewer/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      deepLinks = listOf(navDeepLink { uriPattern = VisoriDeepLink }),
      initial = { it.arguments!!.getInt("index") },
      thumbnail = { _, index -> VisoriFiles.gallery.getOrNull(index)?.let(::thumbnail) }) {
      val family = VisoriFamiglia.Serie
      VisoriViewer(family.files, prepared[family], resumeVideo, onBack = { nav.popBackStack() }, deps = deps,
        documentReader = readers[family], warmPage = warmPage)
    }
    for (family in families.drop(1)) {
      fluidFileViewerRoute("famiglia/${family.scene}/{index}",
        arguments = listOf(navArgument("index") { type = NavType.IntType }),
        initial = { it.arguments!!.getInt("index") },
        thumbnail = { _, index -> family.files.getOrNull(index)?.let(::thumbnail) }) {
        VisoriViewer(family.files, prepared[family], resumeVideo, onBack = { nav.popBackStack() }, deps = deps,
          documentReader = readers[family], warmPage = warmPage)
      }
    }
    composable("senza") {
      HoldWithoutFamilies()
      VisoriSenzaModuliPage(senza, senzaFiles, namespace, onBack = { nav.popBackStack() },
        onOpen = { nav.navigate("senza/$it") })
    }
    fluidFileViewerRoute("senza/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      initial = { it.arguments!!.getInt("index") },
      thumbnail = { _, index -> senza.getOrNull(index)?.let { FluidViewerThumbnail("senza-${it.id}", namespace) } }) {
      HoldWithoutFamilies()
      VisoriViewer(senza, senzaFiles, resumeVideo, onBack = { nav.popBackStack() }, deps = deps, withoutFamilies = true)
    }
    composable("pesi") { VisoriPesiPage(onBack = { nav.popBackStack() }) }
    composable("converter") {
      FluidScreen(title = "Universal Converter", subtitle = "Prova in sola lettura", onBack = { nav.popBackStack() }) {
        item {
          Text("Oggi Universal Converter prova a leggere ogni file come immagine, poi come testo, e altrimenti mostra " +
            "un dump esadecimale del primo KB (PreviewLoader.kt). Qui gli stessi tipi, presi dalle sue prove, passano " +
            "dal registro di engine-viewer-all al preset giusto. L'app non si tocca: è una scena della galleria, che " +
            "include engine-viewer-all senza engine-viewer-ar (la fotocamera non serve per guardare un file).",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
          FluidListGroup {
            converter.forEachIndexed { index, file ->
              if (index > 0) FluidListDivider()
              val shown = converterFiles?.getOrNull(index)
              val opened = shown?.let { visoriOutcome(it.type) }
              FluidListRow(file.name, "Oggi: ${file.today}" + (opened?.let { ". Qui: $it" } ?: ""),
                modifier = Modifier.testTag("converter-${file.id}"),
                meta = shown?.sizeBytes?.let(::formatBytes) ?: if (file.tipo == VisoreTipo.Sparito) "—" else null,
                leading = { Icon(iconOf(file)) }, onClick = { nav.navigate("converter/$index") })
            }
          }
        }
      }
    }
    fluidFileViewerRoute("converter/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      initial = { it.arguments!!.getInt("index") }) {
      VisoriViewer(converter, converterFiles, resumeVideo, onBack = { nav.popBackStack() }, deps = deps,
        converter = true)
    }
  }
}

/** Con che cosa si apre un tipo: la famiglia e il suo tipo, o il preset di serie. */
internal fun visoriOutcome(type: FluidViewerType): String {
  val family = type.family
  return if (family != null) "${moduleOf(family)} · ${type.label ?: presetName(type.preset)}"
  else presetName(type.preset) + if (type.preset == FluidViewerPreset.Unknown) ", «Apri con…»" else ""
}

/**
 * La scena «Senza moduli» (F6C.15): gli stessi file delle famiglie come li apre un'app che ha solo engine-viewer. Ogni
 * riga dice il modulo che servirebbe e che cosa succede qui; nessun file è un vicolo cieco.
 */
@Composable
private fun VisoriSenzaModuliPage(files: List<VisoreFile>, prepared: List<FluidViewerFile>?,
  namespace: FluidGlassNamespace, onBack: () -> Unit, onOpen: (Int) -> Unit) {
  FluidScreen(title = "Senza moduli", subtitle = "Solo engine-viewer", onBack = onBack) {
    item {
      Text("Un'app che include solo engine-viewer non paga il peso delle famiglie. I tipi che il modulo di serie " +
        "conosce (il testo, il Markdown come codice, l'HEIC con il decoder di Android) si aprono lo stesso; gli altri " +
        "restano sconosciuti, con il nome, la dimensione, «Apri con…» e «Condividi».",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var start = 0
    for (family in VisoriFamiglia.entries.drop(1)) {
      val offset = start
      val count = family.files.size
      start += count
      item(key = "senza-${family.scene}") {
        FluidSectionHeader(family.title, detail = "Con ${family.module} si aprono col loro preset.")
      }
      item(key = "senza-${family.scene}-righe") {
        FluidListGroup {
          for (i in 0 until count) {
            val index = offset + i
            val file = files[index]
            if (i > 0) FluidListDivider()
            val type = prepared?.getOrNull(index)?.type
            FluidListRow(file.name, "Qui: " + (type?.let(::visoriOutcome) ?: "…"),
              modifier = Modifier.testTag("senza-${file.id}"),
              meta = prepared?.getOrNull(index)?.sizeBytes?.let(::formatBytes),
              leading = {
                Box(Modifier.fluidViewerThumbnail(FluidViewerThumbnail("senza-${file.id}", namespace),
                  ContinuousCornerShape(FluidRadius.Card))) { Icon(iconOf(file)) }
              },
              onClick = { if (prepared != null) onOpen(index) })
          }
        }
      }
    }
  }
}

internal fun moduleOf(family: String): String =
  VisoriFamiglia.entries.firstOrNull { it.id == family }?.module ?: "engine-viewer-$family"

@Composable
private fun Icon(vector: ImageVector) {
  FluidToneTile(FluidTone.Primary) { FluidIcon(vector, contentDescription = null) }
}

internal fun iconOf(file: VisoreFile): ImageVector = when (file.tipo) {
  VisoreTipo.Foto, VisoreTipo.Animata, VisoreTipo.Grafica -> FluidIcons.Image
  VisoreTipo.Video -> FluidIcons.VideoFile
  VisoreTipo.Audio -> FluidIcons.AudioFile
  VisoreTipo.Pagine -> FluidIcons.PictureAsPdf
  VisoreTipo.Testo, VisoreTipo.Vuoto, VisoreTipo.Office -> FluidIcons.Description
  VisoreTipo.Libro -> FluidIcons.MenuBook
  VisoreTipo.Foglio -> FluidIcons.TableChart
  VisoreTipo.Presentazione -> FluidIcons.Slideshow
  VisoreTipo.Codice, VisoreTipo.Dati -> FluidIcons.Code
  VisoreTipo.Archivio -> FluidIcons.FolderZip
  VisoreTipo.Carattere -> FluidIcons.TextFormat
  VisoreTipo.Modello -> FluidIcons.Layers
  VisoreTipo.Eseguibile -> FluidIcons.Warning
  VisoreTipo.Sparito -> FluidIcons.CloudOff
  VisoreTipo.Sconosciuto -> FluidIcons.Draft
}

/**
 * La miniatura: la foto, il primo fotogramma della WebP, la prima pagina del PDF, quella che dà la famiglia (la
 * miniatura salvata nel documento Office, la copertina dell'EPUB, l'immagine speciale) o l'icona del tipo.
 */
@Composable
private fun Tile(file: VisoreFile, prepared: FluidViewerFile?, thumbnail: FluidViewerThumbnail, modifier: Modifier,
  onClick: () -> Unit) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val still by produceState<ImageBitmap?>(null, file, prepared) {
    value = withContext(Dispatchers.IO) {
      try {
        when {
          file.tipo == VisoreTipo.Animata && file.resource != null ->
            context.resources.openRawResource(file.resource).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
          file.family != VisoriFamiglia.Serie && prepared != null -> {
            val path = prepared.uri.path?.let(::File)?.takeIf { it.isFile }
            path?.let { disk ->
              FluidViewerSource.of(disk, prepared.name, prepared.mimeType).use { source ->
                FluidViewerRegistry.thumbnail(context, source, with(density) { 240.dp.roundToPx() })
              }
            }
          }
          else -> null
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        // A thumbnail is a nicety: a native decoder missing on this device leaves the type's icon.
        null
      }
    }
  }
  Column(modifier.clickable(onClick = onClick)) {
    val shape = ContinuousCornerShape(FluidRadius.Card)
    Box(Modifier.fillMaxWidth().aspectRatio(1.5f).fluidViewerThumbnail(thumbnail, shape).clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
      val picture = still
      when {
        file.cover != null -> Image(painterResource(file.cover), null, Modifier.fillMaxSize(),
          contentScale = if (file.tipo == VisoreTipo.Pagine) ContentScale.Fit else ContentScale.Crop)
        picture != null -> Image(picture, null, Modifier.fillMaxSize(),
          contentScale = if (file.tipo.preparesDocument && file.tipo != VisoreTipo.Grafica) ContentScale.Fit
          else ContentScale.Crop)
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FluidIcon(iconOf(file), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(file.name.substringAfterLast('.', "").uppercase(), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Text(file.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1,
      overflow = TextOverflow.Ellipsis)
    Text(file.tipo.etichetta + " · " + file.name.substringAfterLast('.', "").uppercase(),
      Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
