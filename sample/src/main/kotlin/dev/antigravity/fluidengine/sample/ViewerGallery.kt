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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Il collegamento del visore della galleria: `fluidglass://visori/file/{indice}` (F6C.5). */
internal const val VisoriDeepLink = "fluidglass://visori/file/{index}"

internal fun visoriLink(index: Int): Uri = Uri.parse(VisoriDeepLink.replace("{index}", index.toString()))

/**
 * La galleria «Visori» (F6C.5): i sei tipi della prima versione nel telaio di serie, una rotta vera con collegamento e
 * ripristino, lo strumento dell'app, il pannello info e la prova in sola lettura con i tipi di Universal Converter.
 * [deps] cambia solo nelle prove su JVM (il player e i decodificatori che Robolectric non ha).
 */
@Composable
internal fun ViewerGallery(deps: VisoriDeps = VisoriDeps.Default, onBack: () -> Unit) {
  val nav = rememberFluidNavController()
  val namespace = rememberFluidGlassNamespace()
  val context = LocalContext.current.applicationContext
  val catalog = VisoriFiles.gallery
  val converter = VisoriFiles.converter
  val files by produceState<List<FluidViewerFile>?>(null, context) { value = VisoriFiles.prepare(context, catalog, false) }
  val converterFiles by produceState<List<FluidViewerFile>?>(null, context) {
    value = VisoriFiles.prepare(context, converter, true)
  }
  var resumeVideo by rememberSaveable { mutableStateOf(true) }
  fun thumbnail(index: Int) = catalog.getOrNull(index)?.let { FluidViewerThumbnail(it.id, namespace) }
  FluidNavHost(nav, "collection") {
    composable("collection") {
      FluidScreen(title = "Visori", subtitle = "Il telaio, i sei preset ed engine-viewer", onBack = onBack) {
        item { FluidSectionHeader("I sei tipi", detail = "Tocca un file: si apre dalla miniatura e torna lì.") }
        items((catalog.size + 1) / 2) { row ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { column ->
              val index = row * 2 + column
              val file = catalog.getOrNull(index)
              if (file == null) Spacer(Modifier.weight(1f))
              else Tile(file, requireNotNull(thumbnail(index)), Modifier.weight(1f).testTag("visori-${file.id}")) {
                nav.navigate("viewer/$index")
              }
            }
          }
        }
        item { FluidSectionHeader("Il video", detail = "L'app decide che cosa succede quando torni sul video.") }
        item {
          FluidListGroup {
            FluidToggleRow("Riprendi al ritorno", resumeVideo, { resumeVideo = it },
              subtitle = "Il video si ferma quando lo sfogli via. Acceso, riparte se suonava; spento, resta in pausa.")
          }
        }
        item { FluidSectionHeader("La rotta") }
        item {
          FluidListGroup {
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
            FluidListRow("Universal Converter", "I tipi che oggi apre col dump esadecimale, nel telaio e col " +
              "risolutore di engine-viewer", modifier = Modifier.testTag("visori-converter"),
              leading = { Icon(FluidIcons.SwapHoriz) }, onClick = { nav.navigate("converter") })
          }
        }
      }
    }
    fluidFileViewerRoute("viewer/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      deepLinks = listOf(navDeepLink { uriPattern = VisoriDeepLink }),
      initial = { it.arguments!!.getInt("index") }, thumbnail = { _, index -> thumbnail(index) }) {
      VisoriViewer(catalog, files, resumeVideo, onBack = { nav.popBackStack() }, deps = deps)
    }
    composable("converter") {
      FluidScreen(title = "Universal Converter", subtitle = "Prova in sola lettura", onBack = { nav.popBackStack() }) {
        item {
          Text("Oggi Universal Converter prova a leggere ogni file come immagine, poi come testo, e altrimenti mostra " +
            "un dump esadecimale del primo KB (PreviewLoader.kt). Qui gli stessi tipi passano dal risolutore di " +
            "engine-viewer al preset giusto. L'app non si tocca: è una scena della galleria.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
          FluidListGroup {
            converter.forEachIndexed { index, file ->
              if (index > 0) FluidListDivider()
              val shown = converterFiles?.getOrNull(index)
              val preset = shown?.type?.preset?.let(::presetName)
              FluidListRow(file.name, "Oggi: ${file.today}" + (preset?.let { ". Qui: $it" } ?: ""),
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
      VisoriViewer(converter, converterFiles, resumeVideo, onBack = { nav.popBackStack() }, deps = deps, converter = true)
    }
  }
}

@Composable
private fun Icon(vector: ImageVector) {
  FluidToneTile(FluidTone.Primary) { FluidIcon(vector, contentDescription = null) }
}

private fun iconOf(file: VisoreFile): ImageVector = when (file.tipo) {
  VisoreTipo.Foto, VisoreTipo.Animata -> FluidIcons.Image
  VisoreTipo.Video -> FluidIcons.VideoFile
  VisoreTipo.Audio -> FluidIcons.AudioFile
  VisoreTipo.Pagine -> FluidIcons.PictureAsPdf
  VisoreTipo.Testo, VisoreTipo.Vuoto -> FluidIcons.Description
  VisoreTipo.Codice -> FluidIcons.Code
  VisoreTipo.Eseguibile -> FluidIcons.Warning
  VisoreTipo.Sparito -> FluidIcons.CloudOff
  VisoreTipo.Sconosciuto -> FluidIcons.Draft
}

/** La miniatura: la foto, il primo fotogramma della WebP, o l'icona del tipo con l'estensione. */
@Composable
private fun Tile(file: VisoreFile, thumbnail: FluidViewerThumbnail, modifier: Modifier, onClick: () -> Unit) {
  val context = LocalContext.current
  val still by produceState<ImageBitmap?>(null, file) {
    if (file.tipo == VisoreTipo.Animata && file.resource != null) value = withContext(Dispatchers.IO) {
      context.resources.openRawResource(file.resource).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }
  }
  Column(modifier.clickable(onClick = onClick)) {
    val shape = ContinuousCornerShape(FluidRadius.Card)
    Box(Modifier.fillMaxWidth().aspectRatio(1.5f).fluidViewerThumbnail(thumbnail, shape).clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
      val picture = still
      when {
        file.cover != null -> Image(painterResource(file.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        picture != null -> Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FluidIcon(iconOf(file), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(file.name.substringAfterLast('.', "").uppercase(), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Text(file.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1,
      overflow = TextOverflow.Ellipsis)
    Text(file.tipo.etichetta, Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
