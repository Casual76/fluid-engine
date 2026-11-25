package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.files.FluidFileBrowser
import dev.antigravity.fluidengine.files.FluidFileLocations
import dev.antigravity.fluidengine.files.FluidFileSources
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.sample.viewer.VisoreTipo
import dev.antigravity.fluidengine.sample.viewer.VisoriFamiglia
import dev.antigravity.fluidengine.sample.viewer.VisoriFiles
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.PhoneAndroid
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPosition
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerResumeProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerThumbnail
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidFileViewerRoute
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidViewerThumbnail
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.viewer.FluidFileThumbnailImage
import dev.antigravity.fluidengine.viewer.FluidMedia3Session
import dev.antigravity.fluidengine.viewer.FluidPdfPageSource
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerResumeStore
import dev.antigravity.fluidengine.viewer.FluidViewerType
import dev.antigravity.fluidengine.viewer.fileActions
import dev.antigravity.fluidengine.viewer.fluidMedia3Content
import dev.antigravity.fluidengine.viewer.rememberFluidFileThumbnail
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import dev.antigravity.fluidengine.viewer.rememberFluidViewerResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// F8.18 · File: lo sfoglia-file (engine-files), il bilancio dei documenti grandi (automatico e invisibile, nota di
// Alessio) e la ripresa dallo stesso punto. Tre scene; i file di prova li crea la galleria nella sua cartella, la prima
// volta che servono: il PDF di 3000 pagine (PdfDocument della piattaforma), una lezione registrata di due minuti (WAV) e
// degli appunti lunghi (testo).

/** Le scene di F8.18. */
internal val F8p18Section = NewElementSection(
  pack = "F8.18",
  title = "File",
  scenes = listOf(
    NewElementScene(
      "sfoglia-file", "Sfoglia i file",
      "Universal Converter · Recenti e posizioni, cartelle in griglia o in elenco, ordine, ricerca, e ogni file che si " +
        "apre dalla sua miniatura e ci torna",
    ) { onBack -> F8p18BrowserScene(onBack) },
    NewElementScene(
      "pdf-gigante", "Un PDF di 3000 pagine",
      "Il bilancio automatico: la barra di scorrimento è di tutto il documento, si disegnano solo le pagine in vista",
    ) { onBack -> F8p18GiantScene(onBack) },
    NewElementScene(
      "riprendi", "Riprendi dallo stesso punto",
      "Un PDF, una lezione e degli appunti si riaprono dove li avevi lasciati; se il file cambia, la posizione si offre",
    ) { onBack -> F8p18ResumeScene(onBack) },
  ),
  detail = "Lo sfoglia-file, i documenti enormi senza scatti e la ripresa dal punto lasciato",
)

/** Le voci da provare a mano. */
internal val F8p18TryItems = listOf(
  TryItem(
    "f8-18-sfoglia", TryArea.Pages, "Sfoglia i file",
    "Apri «In Fluid Glass» e poi «Documenti» (le miniature sono il contenuto vero), da «•••» passa a «Elenco» e ordina " +
      "per «Data» (un secondo tocco inverte), cerca «quaderno» e apri il PDF: nasce dalla sua miniatura e, chiudendolo " +
      "col dito, ci torna.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Sfoglia i file"),
  ),
  TryItem(
    "f8-18-posizioni", TryArea.Pages, "Aggiungi una posizione",
    "In «Sfoglia» tocca «Aggiungi una posizione…» e scegli una cartella del telefono o di Drive: resta fra le posizioni " +
      "anche chiudendo l'app, la togli tenendola premuta, e un file aperto da lì compare in «Recenti».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Sfoglia i file"),
  ),
  TryItem(
    "f8-18-gigante", TryArea.Pages, "Il PDF di 3000 pagine",
    "Apri il PDF e lancia la lista veloce (niente scatti, le pagine arrivano nitide quando ti fermi), poi tieni il dito " +
      "sulla maniglia a destra e portala in fondo fino a «Pagina 3000 di 3000», senza domande né regolazioni.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Un PDF di 3000 pagine"),
  ),
  TryItem(
    "f8-18-riprendi", TryArea.Pages, "Riprendi dallo stesso punto",
    "Apri il PDF, scendi a pagina 40 e ingrandisci, chiudi e riapri (torna lì); fai lo stesso con la lezione fermata a " +
      "un minuto e con gli appunti, poi «Cambia gli appunti» e riaprili: in basso c'è «Riprendi dalla riga…».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Riprendi dallo stesso punto"),
  ),
)

/** I tag delle prove. */
internal object F8p18Tags {
  const val Giant = "f8p18:gigante"
  const val Hardware = "f8p18:memoria-grafica"
  const val Preparing = "f8p18:preparo"
  const val Resume = "f8p18:riprendi"
  const val Change = "f8p18:cambia"
  const val Forget = "f8p18:dimentica"
  const val Saved = "f8p18:posizioni"
  const val Clean = "f8p18:togli"
  fun tile(name: String) = "f8p18:file:$name"
}

/**
 * Le dipendenze che le prove su JVM sostituiscono: il player (Robolectric non ha ExoPlayer) e il documento a pagine
 * (`PdfRenderer` e `PdfDocument` non girano sulla JVM). Di serie, quelli veri.
 */
internal class F8p18Deps(
  val player: (Context, Uri) -> Player = { context, uri ->
    ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)) }
  },
  /** Il PDF gigante già pronto (le prove: un sorgente finto); null, la galleria lo crea col PdfDocument. */
  val giant: FluidPageSource? = null,
  val giantPages: Int = F8p18Files.GiantPages,
) {
  companion object {
    val Default = F8p18Deps()
  }
}

internal val LocalF8p18Deps = staticCompositionLocalOf { F8p18Deps.Default }

// --- I file di prova ---------------------------------------------------------------------------------------------

internal object F8p18Files {
  /** Le pagine del PDF gigante. */
  const val GiantPages: Int = 3000
  const val GiantName = "Manuale completo.pdf"
  const val LessonName = "Lezione registrata.wav"
  const val NotesName = "Appunti lunghi.txt"
  /** Le note della cartella lunga: abbastanza per le pagine (200 alla volta) e lo scorrimento rapido. */
  const val ManyNotes: Int = 1200

  /**
   * La radice dello sfoglia-file, dentro la cartella dei file della galleria Visori (così le copie si fanno con
   * `VisoriFiles.prepare`, una volta sola): `filesDir/visori-vN/f8p18`.
   */
  fun root(context: Context): File = VisoriFiles.folder(context.applicationContext, LibraryScene)

  /** Dove stanno il PDF gigante, la lezione e gli appunti (le stesse copie per le tre scene). */
  fun documents(context: Context): File = File(root(context), "Documenti")
  fun music(context: Context): File = File(root(context), "Musica e video")
  fun code(context: Context): File = File(root(context), "Codice e dati")

  private const val LibraryScene = "f8p18"

  /** La cartella dello sfoglia-file: i file della galleria Visori per famiglia, il PDF gigante, molte note. */
  suspend fun library(context: Context, progress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
    val root = root(context).apply { mkdirs() }
    val catalog = VisoriFamiglia.entries.flatMap { it.files }
      .filter { it.tipo != VisoreTipo.Sparito && it.tipo != VisoreTipo.Vuoto && it.tipo != VisoreTipo.Eseguibile }
      .distinctBy { it.name }
    val groups = catalog.groupBy { file ->
      when (file.tipo) {
        VisoreTipo.Foto, VisoreTipo.Animata, VisoreTipo.Grafica -> "Immagini"
        VisoreTipo.Video, VisoreTipo.Audio -> "Musica e video"
        VisoreTipo.Archivio -> "Archivi"
        VisoreTipo.Pagine, VisoreTipo.Office, VisoreTipo.Foglio, VisoreTipo.Presentazione, VisoreTipo.Libro -> "Documenti"
        else -> "Codice e dati"
      }
    }
    for ((folder, files) in groups) VisoriFiles.prepare(context, files, "$LibraryScene/$folder")
    progress(0.2f)
    giantPdf(context, GiantPages) { progress(0.2f + it * 0.7f) }
    lesson(music(context))
    notes(code(context))
    val many = File(root, "Note del corso").apply { mkdirs() }
    if ((many.list()?.size ?: 0) < ManyNotes) {
      for (i in 1..ManyNotes) {
        val note = File(many, "Nota %04d.txt".format(i))
        if (!note.exists()) note.writeText("Nota $i del corso.\n\n${Filler[i % Filler.size]}\n")
      }
    }
    File(root, "Leggimi.txt").takeIf { !it.exists() }?.writeText(
      "La cartella di prova dello sfoglia-file (F8.18).\nOgni file si apre dalla sua miniatura.\n")
    progress(1f)
    root
  }

  /** Il PDF gigante: [pages] pagine A4 (una ogni 250 orizzontale), testo vero e un grafico ogni dieci. */
  suspend fun giantPdf(context: Context, pages: Int = GiantPages,
    progress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
    val folder = documents(context).apply { mkdirs() }
    val file = File(folder, GiantName)
    val done = File(folder, ".manuale-$pages")
    if (file.exists() && done.exists()) return@withContext file
    val document = PdfDocument()
    try {
      val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(28, 28, 30); textSize = 10.5f }
      val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val band = Paint()
      val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(110, 110, 115); textSize = 9f }
      for (i in 0 until pages) {
        val landscape = i % 250 == 249
        val width = if (landscape) 842 else 595
        val height = if (landscape) 595 else 842
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, i + 1).create())
        val canvas = page.canvas
        val chapter = i / 30 + 1
        band.color = AndroidColor.HSVToColor(floatArrayOf((chapter * 37f) % 360f, 0.55f, 0.72f))
        canvas.drawRect(0f, 0f, width.toFloat(), 64f, band)
        canvas.drawText("Capitolo $chapter", 40f, 42f, title)
        var y = 100f
        for (line in 0 until (if (landscape) 22 else 40)) {
          canvas.drawText(Filler[(i + line) % Filler.size], 40f, y, ink)
          y += 17f
        }
        if (i % 10 == 0) {
          for (bar in 0 until 8) {
            val h = 30f + ((i + bar * 13) % 70)
            canvas.drawRect(60f + bar * 38f, y + 110f - h, 88f + bar * 38f, y + 110f, band)
          }
        }
        canvas.drawText("Pagina ${i + 1} di $pages", width - 140f, height - 28f, muted)
        document.finishPage(page)
        if (i % 50 == 0) progress(i / pages.toFloat())
      }
      val partial = File(folder, "$GiantName.parte")
      FileOutputStream(partial).use { document.writeTo(it) }
      if (!partial.renameTo(file)) throw IOException("Non rinomino $partial")
      done.createNewFile()
    } finally {
      document.close()
    }
    progress(1f)
    file
  }

  /** La lezione: due minuti di note (WAV a 22,05 kHz, mono), per la ripresa da un minuto. */
  fun lesson(folder: File): File {
    folder.mkdirs()
    val file = File(folder, LessonName)
    val rate = 22050
    val seconds = 120
    val samples = rate * seconds
    if (file.exists() && file.length() == 44L + samples * 2) return file
    val notes = floatArrayOf(261.6f, 293.7f, 329.6f, 349.2f, 392f, 440f, 493.9f, 523.3f)
    DataOutputStream(FileOutputStream(file).buffered()).use { out ->
      fun intLe(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff); out.write(v shr 16 and 0xff); out.write(v shr 24 and 0xff) }
      fun shortLe(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff) }
      out.writeBytes("RIFF"); intLe(36 + samples * 2); out.writeBytes("WAVE")
      out.writeBytes("fmt "); intLe(16); shortLe(1); shortLe(1); intLe(rate); intLe(rate * 2); shortLe(2); shortLe(16)
      out.writeBytes("data"); intLe(samples * 2)
      for (n in 0 until samples) {
        val second = n / rate
        val within = (n % (rate * 2)) / (rate * 2f)
        val envelope = (1f - within) * 0.35f
        val value = sin(2 * PI * notes[(second / 2) % notes.size] * n / rate) * envelope
        shortLe((value * Short.MAX_VALUE).roundToInt())
      }
    }
    return file
  }

  /** Gli appunti: 3000 righe di testo, per la ripresa dalla riga. */
  fun notes(folder: File): File {
    folder.mkdirs()
    val file = File(folder, NotesName)
    if (!file.exists()) file.writeText(buildString {
      for (i in 1..3000) append("Riga ").append(i).append(" · ").append(Filler[i % Filler.size]).append('\n')
    })
    return file
  }

  /** «Togli i file di prova»: la cartella della scena e le posizioni dei suoi file. */
  fun clean(context: Context) {
    val root = root(context)
    val store = FluidViewerResumeStore.get(context)
    store.all.filter { it.uri.startsWith(Uri.fromFile(root).toString()) }.forEach { store.forget(it.key) }
    store.flush()
    root.deleteRecursively()
  }

  /** «Cambia gli appunti»: una riga in più, così cambiano peso e data (un'altra revisione). */
  fun changeNotes(folder: File) {
    val file = notes(folder)
    file.appendText("Aggiunta del ${System.currentTimeMillis()}\n")
  }

  fun viewerFile(file: File, mime: String?): FluidViewerFile =
    FluidViewerFile(Uri.fromFile(file), file.name, mime, file.length(), file.lastModified().takeIf { it > 0 })

  private val Filler = listOf(
    "Il bilancio è automatico e invisibile: la barra di scorrimento è per tutto il documento.",
    "Solo le pagine in vista sono disegnate; le altre si caricano quando la lista si ferma.",
    "Un telefono debole riceve pagine un po' meno fitte, mai una domanda.",
    "La pagina arriva nella memoria grafica già pronta: il fotogramma non aspetta la texture.",
    "Riaprendo il file si torna alla pagina, al punto della pagina e allo zoom di prima.",
    "Le note del corso sono tante per provare le pagine dello sfoglia-file e lo scorrimento rapido.",
    "Ogni file si apre dalla sua miniatura e ci torna, anche chiudendolo col dito.",
    "La ricerca non bada a maiuscole e accenti: «citta» trova «Città».",
  )
}

// --- I player della scena ----------------------------------------------------------------------------------------

/** Un player per ogni file che suona, creato la prima volta che il visore lo apre e rilasciato con la scena. */
@Stable
private class F8p18Players(private val context: Context, private val scope: CoroutineScope,
  private val make: (Context, Uri) -> Player) {
  private val sessions = HashMap<Uri, FluidMedia3Session>()

  fun content(file: FluidViewerFile, type: FluidViewerType): FluidViewerContent? {
    val video = type.preset == FluidViewerPreset.Video
    if (!video && type.preset != FluidViewerPreset.Audio) return null
    val session = sessions.getOrPut(file.uri) {
      FluidMedia3Session(make(context, file.uri)).also { session ->
        session.attach()
        scope.launch { session.followPosition() }
      }
    }
    return fluidMedia3Content(session, video = video)
  }

  fun release() {
    sessions.values.forEach {
      it.close()
      it.player.release()
    }
    sessions.clear()
  }
}

@Composable
private fun rememberF8p18Players(): F8p18Players {
  val context = LocalContext.current.applicationContext
  val scope = rememberCoroutineScope()
  val deps = LocalF8p18Deps.current
  val players = remember(context, scope, deps) { F8p18Players(context, scope, deps.player) }
  DisposableEffect(players) { onDispose { players.release() } }
  return players
}

// --- Sfoglia i file --------------------------------------------------------------------------------------------------

@Composable
private fun F8p18BrowserScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var progress by remember { mutableFloatStateOf(0f) }
  val root by produceState<File?>(null, context) { value = F8p18Files.library(context) { progress = it } }
  val players = rememberF8p18Players()
  val folder = root
  if (folder == null) {
    F8p18Preparing("Preparo i file di prova", progress, onBack)
    return
  }
  val source = remember(folder) {
    FluidFileSources.appFolder(folder, id = "fluid-glass", title = "In Fluid Glass",
      subtitle = "La cartella dell'app", icon = FluidIcons.PhoneAndroid)
  }
  // Sul tablet di Alessio (solo in debug): il selettore delle cartelle apre già nella cartella di prova, così le sue
  // cartelle non passano sullo schermo.
  val debuggable = remember(context) { context.isDebuggableApp() }
  val hint = remember(context) {
    context.activity()?.intent?.takeIf { debuggable }?.getStringExtra(BrowserHintExtra)?.let(Uri::parse)
      ?: FluidFileLocations.PhoneHint
  }
  FluidFileBrowser(
    sources = listOf(source),
    onBack = onBack,
    media = players::content,
    addLocationHint = hint,
  )
}

@Composable
private fun F8p18Preparing(title: String, progress: Float, onBack: () -> Unit) {
  FluidScreen(title = title, onBack = onBack, modifier = Modifier.testTag(F8p18Tags.Preparing)) {
    item {
      FluidListGroup {
        FluidListRow("${(progress * 100).roundToInt()}%", "La prima volta la galleria crea un PDF di " +
          "${F8p18Files.GiantPages} pagine, una lezione di due minuti e ${F8p18Files.ManyNotes} note.", disclosure = false)
      }
    }
  }
}

// --- Il PDF di 3000 pagine -------------------------------------------------------------------------------------------

@Composable
private fun F8p18GiantScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val deps = LocalF8p18Deps.current
  var progress by remember { mutableFloatStateOf(0f) }
  val pdf by produceState<File?>(null, context, deps) {
    value = if (deps.giant != null) null else F8p18Files.giantPdf(context, pages = deps.giantPages) { progress = it }
  }
  if (deps.giant == null && pdf == null) {
    F8p18Preparing("Preparo il PDF", progress, onBack)
    return
  }
  val debuggable = remember(context) { context.isDebuggableApp() }
  val scope = rememberCoroutineScope()
  // Il confronto sul dispositivo: spento, le pagine si caricano nella texture al primo disegno. L'extra vale anche nella
  // bench (le misure si fanno lì); l'interruttore si vede solo in debug.
  var hardware by remember {
    val extra = context.activity()?.intent?.takeIf { it.hasExtra(GiantHardwareExtra) }
      ?.getBooleanExtra(GiantHardwareExtra, true)
    mutableStateOf(extra ?: FluidPdfPageSource.hardwareDisplay)
  }
  DisposableEffect(hardware) {
    FluidPdfPageSource.hardwareDisplay = hardware
    onDispose { FluidPdfPageSource.hardwareDisplay = true }
  }
  val files = remember(pdf) { pdf?.let { listOf(F8p18Files.viewerFile(it, "application/pdf")) }.orEmpty() }
  F8p18Documents(
    title = "Un PDF di ${deps.giantPages} pagine",
    subtitle = "Il bilancio dei documenti grandi",
    files = files,
    fake = deps.giant?.let { listOf(it) },
    onBack = onBack,
    modifier = Modifier.testTag(F8p18Tags.Giant),
  ) {
    item {
      FluidSectionFootnote("Nessuna regolazione: la barra a destra porta a qualunque pagina, si disegnano solo le " +
        "pagine in vista, e sui telefoni deboli le pagine sono un po' meno fitte. La lettura riparte dal punto lasciato.")
    }
    if (debuggable) item {
      FluidListGroup {
        FluidToggleRow("Pagine nella memoria grafica", hardware, { hardware = it },
          modifier = Modifier.testTag(F8p18Tags.Hardware),
          subtitle = "Solo per il confronto: spento, ogni pagina si carica nella texture dentro il fotogramma, come prima")
      }
    }
    item {
      FluidListGroup {
        FluidListRow("Togli i file di prova", "Il PDF, la lezione, gli appunti e le note della scena, con le loro posizioni",
          modifier = Modifier.testTag(F8p18Tags.Clean), leading = { FluidIcon(FluidIcons.Delete, null) },
          tone = FluidTone.Danger, disclosure = false, onClick = {
            scope.launch {
              withContext(Dispatchers.IO) { F8p18Files.clean(context) }
              onBack()
            }
          })
      }
    }
  }
}

private const val GiantHardwareExtra = "f8p18.hw"
private const val BrowserHintExtra = "f8p18.hint"

private fun Context.activity(): Activity? {
  var current: Context? = this
  while (current is android.content.ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}

// --- Riprendi dallo stesso punto -------------------------------------------------------------------------------------

@Composable
private fun F8p18ResumeScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val deps = LocalF8p18Deps.current
  var progress by remember { mutableFloatStateOf(0f) }
  var revision by remember { mutableIntStateOf(0) }
  val files by produceState<List<FluidViewerFile>?>(null, context, revision) {
    value = withContext(Dispatchers.IO) {
      buildList {
        if (deps.giant == null) add(F8p18Files.viewerFile(F8p18Files.giantPdf(context, pages = deps.giantPages) { progress = it },
          "application/pdf"))
        add(F8p18Files.viewerFile(F8p18Files.lesson(F8p18Files.music(context)), "audio/wav"))
        add(F8p18Files.viewerFile(F8p18Files.notes(F8p18Files.code(context)), "text/plain"))
      }
    }
  }
  val list = files
  if (list == null) {
    F8p18Preparing("Preparo i file", progress, onBack)
    return
  }
  val store = remember(context) { FluidViewerResumeStore.get(context) }
  var saved by remember { mutableStateOf(store.all) }
  DisposableEffect(store) {
    val watch = store.observe { saved = store.all }
    onDispose { watch.close() }
  }
  val scope = rememberCoroutineScope()
  F8p18Documents(
    title = "Riprendi dallo stesso punto",
    subtitle = "Pagina, zoom, secondo e riga",
    files = list,
    fake = deps.giant?.let { listOf(it) },
    onBack = onBack,
    onReturn = { saved = store.all },
    modifier = Modifier.testTag(F8p18Tags.Resume),
  ) {
    item { FluidSectionHeader("Posizioni salvate", detail = "Salvate a riposo, tutte insieme; tolte quando il file non c'è più.") }
    item {
      FluidListGroup(Modifier.testTag(F8p18Tags.Saved)) {
        val mine = saved.filter { entry -> list.any { it.uri.toString() == entry.uri } }
        if (mine.isEmpty()) FluidListRow("Nessuna", "Apri un file e spostati: la posizione compare qui.", disclosure = false)
        mine.forEachIndexed { i, entry ->
          if (i > 0) FluidListDivider()
          FluidListRow(Uri.parse(entry.uri).lastPathSegment.orEmpty(), describePosition(entry.position), disclosure = false)
        }
      }
    }
    item {
      FluidListGroup {
        FluidListRow("Cambia gli appunti", "Una riga in più: riaprendoli, la riga di prima si offre in basso",
          modifier = Modifier.testTag(F8p18Tags.Change), leading = { FluidIcon(FluidIcons.Edit, null) },
          tone = FluidTone.Primary, disclosure = false, onClick = {
            scope.launch {
              withContext(Dispatchers.IO) { F8p18Files.changeNotes(F8p18Files.code(context)) }
              revision++
            }
          })
        FluidListDivider()
        FluidListRow("Dimentica le posizioni", "I tre file ripartono dall'inizio",
          modifier = Modifier.testTag(F8p18Tags.Forget), leading = { FluidIcon(FluidIcons.Delete, null) },
          tone = FluidTone.Danger, disclosure = false, onClick = {
            list.forEach { store.forget(FluidViewerResumeStore.keyOf(it)) }
            saved = store.all
          })
      }
    }
  }
}

private fun describePosition(position: FluidViewerPosition): String = when {
  position.mediaMillis != null -> {
    val seconds = (position.mediaMillis ?: 0L) / 1000
    "al minuto %d:%02d".format(seconds / 60, seconds % 60)
  }
  else -> buildString {
    append("pagina o riga ").append(position.page + 1)
    if (position.offset > 0f) append(", ").append((position.offset * 100).roundToInt()).append("% più giù")
    if (position.zoom != 1f) append(", zoom ").append((position.zoom * 100).roundToInt()).append("%")
  }
}

// --- La pagina con le tessere e il visore ----------------------------------------------------------------------------

/**
 * Una pagina di tessere che si aprono nel visore dalla loro miniatura, con la ripresa accesa. [fake] sono i documenti
 * già pronti delle prove (al posto dei file a pagine, nello stesso ordine).
 */
@Composable
private fun F8p18Documents(
  title: String,
  subtitle: String,
  files: List<FluidViewerFile>,
  fake: List<FluidPageSource>?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onReturn: () -> Unit = {},
  extra: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {},
) {
  val nav = rememberFluidNavController()
  val namespace = rememberFluidGlassNamespace()
  val players = rememberF8p18Players()
  val fakes = fake.orEmpty()
  val count = fakes.size + files.size
  fun thumbnail(index: Int) = FluidViewerThumbnail("f8p18:$title:$index", namespace)
  FluidNavHost(nav, "pagina", modifier) {
    composable("pagina") {
      LaunchedEffect(Unit) { onReturn() }
      FluidScreen(title = title, subtitle = subtitle, onBack = onBack) {
        item {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (index in 0 until 3) {
              if (index >= count) {
                Spacer(Modifier.weight(1f))
              } else {
                val file = files.getOrNull(index - fakes.size)
                val name = file?.name ?: "Documento di prova"
                F8p18Tile(name, file, thumbnail(index), Modifier.weight(1f).testTag(F8p18Tags.tile(name))) {
                  nav.navigate("file/$index")
                }
              }
            }
          }
        }
        extra()
      }
    }
    fluidFileViewerRoute("file/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      initial = { it.arguments?.getInt("index") ?: 0 }, thumbnail = { _, index -> thumbnail(index) }) {
      val opened = rememberFluidViewerFiles(files, media = players::content)
      val resume = rememberFluidViewerResume(opened)
      FluidViewerResumeProvider(resume = { index -> if (index < fakes.size) null else resume(index - fakes.size) }) {
        FluidFileViewer(
          count = count,
          content = { index ->
            if (index < fakes.size) FluidViewerContent.Pages(fakes[index]) else opened.content(index - fakes.size)
          },
          onBack = { nav.popBackStack() },
          fileActions = { index -> if (index < fakes.size) null else opened.fileActions(index - fakes.size) },
        )
      }
    }
  }
}

@Composable
private fun F8p18Tile(name: String, file: FluidViewerFile?, thumbnail: FluidViewerThumbnail, modifier: Modifier,
  onClick: () -> Unit) {
  val shape = ContinuousCornerShape(FluidRadius.Small)
  val sizePx = with(LocalDensity.current) { 140.dp.roundToPx() }
  Column(modifier.fluidPressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.78f).fluidViewerThumbnail(thumbnail, shape).shadow(1.dp, shape).clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
      val image = rememberFluidFileThumbnail(file, sizePx)
      if (image != null) FluidFileThumbnailImage(image, Modifier.fillMaxSize())
    }
    Spacer(Modifier.height(6.dp))
    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(horizontal = 2.dp))
  }
}
