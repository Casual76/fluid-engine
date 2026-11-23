package dev.antigravity.fluidengine.sample.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.viewer.*
import java.util.Locale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// F6C.5 · Il visore della galleria: il telaio di engine-ui, i file aperti da engine-viewer, il player dell'app.
//
// Cosa fa l'engine e cosa l'app (piano F6C, «il telaio mostra, il modulo apre, l'app possiede»):
// - il telaio: rotta, pager, zoom, chiusura, barre al tocco, pannello info, modo strumento, i sei preset;
// - engine-viewer: il risolutore, PdfRenderer, ImageDecoder, il testo e i colori, l'adattatore di Media3;
// - la galleria: i file e la loro cartella, i due ExoPlayer, quando il video parte e riprende, «Apri con…», lo
//   strumento «Ritaglia» (finto: non scrive niente) e il contenuto del pannello.

/**
 * Ciò che la galleria prende da fuori. Di serie un ExoPlayer per file; le prove su JVM passano un player finto e,
 * dove Robolectric non ha il decodificatore della piattaforma (PdfRenderer, AnimatedImageDrawable), il contenuto già
 * pronto di quel file.
 */
@Immutable
internal class VisoriDeps(
  val player: (Context, Uri) -> Player = ::exoPlayer,
  val content: (VisoreFile) -> FluidViewerContent? = { null },
) {
  companion object {
    val Default = VisoriDeps()
  }
}

private fun exoPlayer(context: Context, uri: Uri): Player = ExoPlayer.Builder(context).build().apply {
  setMediaItem(MediaItem.fromUri(uri))
  repeatMode = Player.REPEAT_MODE_ONE
}

/**
 * Il player di un file e la regola della galleria: un video parte la prima volta che la sua pagina si ferma; quando
 * lo si sfoglia via (`currentFluidViewerPageActive()` falso) o l'app va in secondo piano si ferma; al ritorno riparte
 * solo se suonava e se [resume] è vero. È una scelta dell'app, non dell'engine: un'app che vuole il video sempre
 * fermo al ritorno mette [resume] a falso.
 */
@Stable
internal class VisoriMedia(val uri: Uri, val session: FluidMedia3Session, private val autoplay: Boolean) {
  var resume by mutableStateOf(true)
  private var started = false
  private var wasPlaying = false

  /** Quante volte si è fermato perché la pagina è stata lasciata. */
  var stops by mutableIntStateOf(0)
    private set

  fun shown() {
    if (!started) {
      started = true
      if (autoplay) session.play()
    } else if (resume && wasPlaying) {
      session.play()
    }
  }

  fun hidden() {
    wasPlaying = session.player.playWhenReady
    if (wasPlaying) {
      session.pause()
      stops++
    }
  }
}

/** Dentro la pagina del file: segue la pagina ferma e il ciclo di vita dell'attività. */
@Composable
private fun FollowPage(media: VisoriMedia) {
  val active = currentFluidViewerPageActive()
  LifecycleResumeEffect(active, media) {
    if (active) media.shown()
    onPauseOrDispose { if (active) media.hidden() }
  }
}

@Composable
private fun rememberVisoriMedia(uri: Uri?, deps: VisoriDeps, autoplay: Boolean): VisoriMedia? {
  if (uri == null) return null
  val context = LocalContext.current.applicationContext
  val player = remember(uri, deps) { deps.player(context, uri) }
  DisposableEffect(player) { onDispose { player.release() } }
  val session = rememberFluidMedia3Session(player)
  return remember(session) { VisoriMedia(uri, session, autoplay) }
}

private fun VisoriMedia.videoContent(): FluidViewerContent.Media {
  val base = fluidMedia3Content(session, video = true)
  return FluidViewerContent.Media(session, base.sampleVideo, videoSurface = {
    FollowPage(this)
    base.videoSurface?.invoke()
  })
}

private fun VisoriMedia.audioContent() = FluidViewerContent.Media(session)

/** La forma d'onda è dell'app (il preset audio la ospita): barre fisse, colorate fin dove è arrivato il player. */
@Composable
internal fun Waveform(session: FluidMediaSession) {
  val played = MaterialTheme.colorScheme.primary
  val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
  val duration = session.durationMillis?.takeIf { it > 0 }
  Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(40.dp).testTag("visori-onda")) {
    val bars = 48
    val step = size.width / bars
    val fraction = duration?.let { session.positionMillis.toFloat() / it } ?: 0f
    for (i in 0 until bars) {
      val level = 0.25f + 0.75f * ((kotlin.math.sin(i * 0.55f) * kotlin.math.sin(i * 0.17f + 1f) + 1f) / 2f)
      val h = size.height * level
      drawRoundRect(if (i < bars * fraction) played else rest, Offset(i * step + step * 0.2f, (size.height - h) / 2),
        Size(step * 0.6f, h), androidx.compose.ui.geometry.CornerRadius(step * 0.3f))
    }
  }
}

/**
 * Un visore sui file [catalog], aperti con engine-viewer da [files] (null finché la galleria li copia). [resumeVideo]
 * è la scelta dell'app per il video sfogliato via. [converter] è la prova con Universal Converter: lì lo sconosciuto
 * offre anche il dump esadecimale dei primi byte, a richiesta.
 */
@Composable
internal fun VisoriViewer(
  catalog: List<VisoreFile>,
  files: List<FluidViewerFile>?,
  resumeVideo: Boolean,
  onBack: () -> Unit,
  deps: VisoriDeps = VisoriDeps.Default,
  converter: Boolean = false,
  documentReader: FluidViewerFiles? = null,
  warmPage: VisoriWarmPage? = null,
) {
  val context = LocalContext.current
  val list = files.orEmpty()
  fun indexOf(type: VisoreTipo) = catalog.indexOfFirst { it.tipo == type }
  val video = rememberVisoriMedia(list.getOrNull(indexOf(VisoreTipo.Video))?.uri, deps, autoplay = true)
  val audio = rememberVisoriMedia(list.getOrNull(indexOf(VisoreTipo.Audio))?.uri, deps, autoplay = false)
  SideEffect {
    video?.resume = resumeVideo
    audio?.resume = resumeVideo
  }
  fun sample(file: FluidViewerFile): VisoreFile = catalog[list.indexOf(file)]
  val opened = rememberFluidViewerFiles(
    list,
    openWith = { file -> VisoriFiles.openWith(context, sample(file)) },
    share = { file -> VisoriFiles.share(context, sample(file)) },
    media = { file, type ->
      when {
        type.preset == FluidViewerPreset.Video && file.uri == video?.uri -> video.videoContent()
        type.preset == FluidViewerPreset.Audio && file.uri == audio?.uri -> audio.audioContent()
        else -> null
      }
    },
  )
  fun reader(i: Int) = documentReader?.takeIf { catalog[i].tipo.preparesDocument } ?: opened
  var current by rememberSaveable { mutableIntStateOf(-1) }
  val crop = remember { CropState() }
  val count = if (files == null) 0 else catalog.size
  // The original converter demo shows its plain Markdown source and offers a hex dump of DOCX.
  // Its examples keep that behavior while the gallery exercises the installed families.
  val markdown = list.getOrNull(catalog.indexOfFirst { converter && it.id == "uc-markdown" })
  val converterSource by produceState<FluidViewerContent.Text?>(null, markdown) {
    value = markdown?.let { file ->
      withContext(Dispatchers.IO) {
        File(requireNotNull(file.uri.path)).inputStream().use {
          FluidTextDecoder.read(it, file.name, file.mimeType, file.sizeBytes)?.let { source -> FluidViewerContent.Text(source) }
        }
      }
    }
  }
  FluidViewerInfoProvider(info = { i ->
    val sample = catalog[i]
    visoriDetails(sample, list[i], reader(i).document(i), video, audio, resumeVideo)
  }) {
    FluidFileViewer(
      count = count,
      content = { i ->
        val sample = catalog[i]
        deps.content(sample) ?: when {
          converter && sample.id == "uc-relazione" -> FluidViewerContent.Unknown(list[i].info,
            openWith = { VisoriFiles.openWith(context, sample) }, share = { VisoriFiles.share(context, sample) })
          converter && sample.id == "uc-markdown" && converterSource != null -> requireNotNull(converterSource)
          else -> {
            val content = reader(i).content(i)
            if (warmPage != null && content is FluidViewerContent.Pages && content.source === warmPage.source)
              warmPage.content else content
          }
        }
      },
      onBack = onBack,
      onPageChange = { current = it },
      toolbar = { index ->
        val sample = catalog[index]
        if (sample.resource != null && sample.tipo !in setOf(VisoreTipo.Sconosciuto, VisoreTipo.Eseguibile)) {
          button("share", FluidIcons.IosShare, "Condividi", { VisoriFiles.share(context, sample) },
            placement = FluidToolbarPlacement.BottomBar)
        }
      },
      tools = {
        // «Ritaglia» è uno strumento dell'app: c'è sulle foto, e la barra in basso diventa la sua finché non si tocca Fine.
        val photo = catalog.getOrNull(current)
        if (photo?.tipo == VisoreTipo.Foto && photo.cover != null) {
          tool("ritaglia", FluidIcons.Edit, "Ritaglia", actions = { _ ->
            button("ruota", FluidIcons.Refresh, "Ruota", { crop.rotate() })
            button("formato", FluidIcons.GridView, "Formato ${crop.format.label}", { crop.next() })
          }) { i -> FakeCrop(catalog[i], crop) }
        }
      },
      renderContent = { file, visible ->
        if (file is FluidViewerContent.Media && file.videoSurface == null && audio != null) FollowPage(audio)
        VisoriBody(file, visible, catalog, list, converter)
      },
    )
  }
  LaunchedEffect(current) { crop.reset() }
}

/** I preset di serie, con due ritocchi dell'app sullo sconosciuto: il file sparito lo dice, e il dump a richiesta. */
@Composable
private fun VisoriBody(file: FluidViewerContent, visible: Boolean, catalog: List<VisoreFile>,
  list: List<FluidViewerFile>, converter: Boolean) {
  when (file) {
    is FluidViewerContent.Image -> FluidImagePreset(file)
    is FluidViewerContent.Media -> if (file.videoSurface == null) FluidMediaPreset(file, visible,
      strings = FluidMediaPresetStrings(skipBack = "Indietro di 15 secondi", skipForward = "Avanti di 15 secondi", skipSeconds = 15))
      else FluidMediaPreset(file, visible)
    is FluidViewerContent.Pages -> FluidPagesPreset(file, visible)
    is FluidViewerContent.Text -> FluidTextPreset(file)
    is FluidViewerContent.Grid -> FluidGridPreset(file, visible)
    is FluidViewerContent.Custom -> FluidCustomPreset(file, visible)
    is FluidViewerContent.Unknown -> {
      val index = list.indexOfFirst { it.name == file.info.name }
      val sample = catalog.getOrNull(index)
      if (sample?.tipo == VisoreTipo.Sparito) {
        FluidUnknownPreset(FluidViewerContent.Unknown(file.info), strings = FluidUnknownPresetStrings(
          noPreview = "Il file non si legge: è stato spostato o cancellato dopo la conversione."))
      } else {
        val bytes: (suspend () -> ByteArray)? = remember(index, converter) {
          if (index >= 0 && (converter || sample?.tipo == VisoreTipo.Eseguibile || sample?.tipo == VisoreTipo.Sconosciuto)) ({ VisoriFiles.firstBytes(list[index], FluidUnknownPresetDefaults.HexDumpBytes) }) else null
        }
        FluidUnknownPreset(file, firstBytes = bytes)
      }
    }
  }
}

// --- Lo strumento finto «Ritaglia» ---------------------------------------------------------------------------------

internal enum class CropFormat(val label: String, val ratio: Float?) {
  Free("libero", null),
  Square("1:1", 1f),
  Classic("4:3", 4f / 3f),
}

@Stable
internal class CropState {
  var rotation by mutableIntStateOf(0)
    private set
  var format by mutableStateOf(CropFormat.Free)
    private set
  var offset by mutableStateOf(Offset.Zero)

  fun rotate() {
    rotation = (rotation + 90) % 360
  }

  fun next() {
    format = CropFormat.entries[(format.ordinal + 1) % CropFormat.entries.size]
  }

  fun reset() {
    rotation = 0
    format = CropFormat.Free
    offset = Offset.Zero
  }
}

/**
 * Il corpo dello strumento: la foto con una cornice da spostare, i terzi e il fuori scurito. Non ritaglia niente: fa
 * vedere che il contenuto è del compositore dell'app e la barra in basso è sua (Ruota, Formato, Fine).
 */
@Composable
private fun FakeCrop(file: VisoreFile, state: CropState) {
  val cover = file.cover ?: return
  Box(Modifier.fillMaxSize().background(Color.Black).testTag("visori-ritaglio")
    .semantics { stateDescription = "Ritaglio ${state.format.label}, ruotata di ${state.rotation} gradi" },
    contentAlignment = Alignment.Center) {
    Image(painterResource(cover), file.title, Modifier.fillMaxSize(0.92f).rotate(state.rotation.toFloat()),
      contentScale = ContentScale.Fit)
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
      detectDragGestures { change, drag ->
        change.consume()
        state.offset += drag
      }
    }) {
      val maxW = size.width * 0.78f
      val maxH = size.height * 0.5f
      val ratio = state.format.ratio
      val (w, h) = when {
        ratio == null -> maxW to maxH
        maxW / ratio <= maxH -> maxW to maxW / ratio
        else -> maxH * ratio to maxH
      }
      val limitX = (size.width - w) / 2
      val limitY = (size.height - h) / 2
      val center = Offset(size.width / 2 + state.offset.x.coerceIn(-limitX, limitX),
        size.height / 2 + state.offset.y.coerceIn(-limitY, limitY))
      val frame = Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
      val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRect(frame)
      }
      drawPath(outside, Color.Black.copy(alpha = 0.55f))
      drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
      for (k in 1..2) {
        val x = frame.left + frame.width * k / 3
        val y = frame.top + frame.height * k / 3
        drawLine(Color.White.copy(alpha = 0.5f), Offset(x, frame.top), Offset(x, frame.bottom), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.5f), Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
      }
      val arm = 18.dp.toPx()
      val thick = 4.dp.toPx()
      for (corner in listOf(frame.topLeft, frame.topRight, frame.bottomLeft, frame.bottomRight)) {
        val dx = if (corner.x == frame.left) arm else -arm
        val dy = if (corner.y == frame.top) arm else -arm
        drawLine(Color.White, corner, corner + Offset(dx, 0f), thick)
        drawLine(Color.White, corner, corner + Offset(0f, dy), thick)
      }
    }
  }
}

// --- Il pannello info ----------------------------------------------------------------------------------------------

internal fun kindName(kind: FluidFileKind): String = when (kind) {
  FluidFileKind.Image -> "immagine"
  FluidFileKind.Pdf -> "PDF"
  FluidFileKind.Document -> "documento"
  FluidFileKind.Spreadsheet -> "foglio di calcolo"
  FluidFileKind.Presentation -> "presentazione"
  FluidFileKind.Archive -> "archivio"
  FluidFileKind.Audio -> "audio"
  FluidFileKind.Video -> "video"
  FluidFileKind.Text -> "testo"
  FluidFileKind.Executable -> "eseguibile"
  FluidFileKind.Other -> "altro"
}

internal fun presetName(preset: FluidViewerPreset): String = when (preset) {
  FluidViewerPreset.Image -> "Immagine"
  FluidViewerPreset.Video -> "Video"
  FluidViewerPreset.Audio -> "Audio"
  FluidViewerPreset.Pages -> "Documento a pagine"
  FluidViewerPreset.Text -> "Testo e codice"
  else -> "Sconosciuto"
}

internal fun decodedBy(document: FluidViewerDocument?): String? {
  val content = document?.content ?: return null
  return when (content) {
    is FluidViewerContent.Text -> (content.source as? FluidTextDocument)?.let { text ->
      buildString {
        append("engine-viewer · ").append(text.charset.charset.name())
        append(" · ").append(text.lineCount).append(if (text.lineCount == 1) " riga" else " righe")
        text.language?.let { append(" · ").append(it) }
        if (text.truncated) append(" · troncato")
      }
    } ?: "engine-viewer · testo"
    is FluidViewerContent.Pages -> "PdfRenderer della piattaforma · ${content.source.count} pagine"
    is FluidViewerContent.Image -> "ImageDecoder della piattaforma"
    is FluidViewerContent.Media -> "Media3, il player dell'app"
    is FluidViewerContent.Unknown -> "Nessun decodificatore: «Apri con…»"
    else -> null
  }
}

internal fun formatBytes(bytes: Long): String = when {
  bytes == 0L -> "Vuoto"
  bytes < 1024 -> "$bytes byte"
  bytes < 1024 * 1024 -> String.format(Locale.ITALIAN, "%.1f KB", bytes / 1024.0)
  else -> String.format(Locale.ITALIAN, "%.1f MB", bytes / (1024.0 * 1024.0))
}
