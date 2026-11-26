package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.scan.FluidDocumentScanner
import dev.antigravity.fluidengine.scan.FluidScanResult
import dev.antigravity.fluidengine.scan.FluidStillScanCamera
import dev.antigravity.fluidengine.scan.rememberFluidScanState
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// F8.17 · Documenti: lo scanner (engine-scan) e il testo nelle immagini (engine-viewer-ocr). La galleria non chiede la
// fotocamera (Fluid Glass è un'app pubblicata, e checkArViewerManifests lo controlla): lo scanner qui usa la fotocamera
// finta con le foto di fogli in assets/scanner/ (tools/scan-samples/genera.py). La fotocamera vera si prova nell'app di
// prova del modulo, engine-scan/prova.

internal val F8p17Section = NewElementSection(
  pack = "F8.17",
  title = "Documenti",
  scenes = listOf(
    NewElementScene(
      "scanner", "Scansiona un documento",
      "Pampa Notes e Universal Converter · Il foglio trovato dal vivo, lo scatto quando è fermo, la pagina raddrizzata e " +
        "pulita, «Salva» e il PDF che si apre nel visore",
    ) { onBack -> F8p17ScannerScene(onBack, review = false) },
    NewElementScene(
      "scanner-fila", "La fila delle pagine",
      "Tre pagine già scansionate: tieni premuta una miniatura per spostarla, ritaglia un angolo con la lente, cambia il " +
        "filtro, ruota",
    ) { onBack -> F8p17ScannerScene(onBack, review = true) },
    NewElementScene(
      "leggi-testo", "Leggi testo nelle foto",
      "Una schermata e la foto di una lettera: il tasto di Testo attivo, tieni premuto su una parola, copia, cerca",
    ) { onBack -> F8p17ViewerScene(onBack, listOf("schermata.png", "foto-lettera.jpg")) },
    NewElementScene(
      "leggi-testo-pdf", "Leggi testo in un PDF scansito",
      "Due pagine senza testo vero: «Leggi testo» le legge sul telefono, e «Trova» cerca nelle parole trovate",
    ) { onBack -> F8p17ViewerScene(onBack, listOf("scansione.pdf")) },
  ),
  detail = "Lo scanner di documenti come Note di iOS, e «Leggi testo» nei visori come Testo attivo",
)

internal val F8p17TryItems = listOf(
  TryItem(
    "f8-17-scanner", TryArea.Pages, "Scansiona un documento",
    "Apri lo scanner e aspetta: il foglio si colora, la scritta dice «Tieni fermo», la pagina scatta da sola e vola " +
      "nella miniatura e dopo il tavolo vuoto arriva il foglio dopo; prova anche «Manuale» col tasto bianco, poi la " +
      "spunta in alto: il PDF si apre nel visore e ha «Leggi testo».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Scansiona un documento"),
  ),
  TryItem(
    "f8-17-fila", TryArea.Pages, "La fila delle pagine",
    "Tieni premuta la prima miniatura in basso e portala in fondo; poi «Ritaglia» e trascina un angolo (la lente " +
      "ingrandisce sotto il dito), «Filtro» con le quattro anteprime, «Ruota» ed «Elimina» sull'ultima.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La fila delle pagine"),
  ),
  TryItem(
    "f8-17-leggi", TryArea.Pages, "Leggi testo",
    "Sulla schermata aspetta il tasto in basso a destra e toccalo (la foto si vela, le righe restano chiare), tieni " +
      "premuto sul codice della prenotazione, allarga con le maniglie e «Copia»; ingrandisci col doppio tocco e riprova, " +
      "e «Testo» apre l'elenco delle righe con la ricerca.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Leggi testo nelle foto"),
  ),
  TryItem(
    "f8-17-pdf", TryArea.Pages, "Leggi testo in un PDF scansito",
    "Apri il PDF e fermati sulla prima pagina finché compare il tasto, poi «Trova» in basso e cerca «Galileo»: le " +
      "parole si illuminano anche se il PDF non ha testo vero.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Leggi testo in un PDF scansito"),
  ),
)

/** I tag delle prove. */
internal object F8p17Tags {
  const val Preparing = "f8p17:preparo"
  const val Result = "f8p17:risultato"
}

/** Le foto dei fogli della fotocamera finta, negli asset della galleria. */
internal object F8p17Assets {
  const val Folder = "scanner"
  val Sheets = listOf("buona-luce.jpg", "prospettiva.jpg", "scontrino.jpg")
  const val Table = "tavolo-vuoto.jpg"

  fun bitmap(context: Context, name: String): Bitmap =
    context.assets.open("$Folder/$name").use { BitmapFactory.decodeStream(it) } ?: error("Foto mancante: $name")

  /** Il file degli asset copiato nella cache, per i visori (che leggono un indirizzo). */
  suspend fun file(context: Context, name: String): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "f8p17").apply { mkdirs() }
    File(dir, name).also { out ->
      if (!out.exists()) context.assets.open("$Folder/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
    }
  }
}

/**
 * Lo scanner con la fotocamera finta. Con [review] le tre pagine sono già nella fila e si apre la revisione. «Salva»
 * consegna il PDF, che si apre nel visore (dove «Leggi testo» lo legge).
 */
@Composable
private fun F8p17ScannerScene(onBack: () -> Unit, review: Boolean) {
  val context = LocalContext.current
  var result by remember { mutableStateOf<FluidScanResult?>(null) }
  val done = result
  if (done?.pdfFile != null) {
    F8p17Viewer(listOf(viewerFile(done.pdfFile!!, "application/pdf")), onBack = { result = null })
    return
  }
  val state = rememberFluidScanState()
  var ready by rememberSaveable { mutableStateOf(!review) }
  LaunchedEffect(review) {
    if (review && state.pages.isEmpty()) {
      for (name in F8p17Assets.Sheets) {
        val photo = withContext(Dispatchers.IO) { F8p17Assets.bitmap(context, name) }
        state.add(photo, null)
      }
      ready = true
    } else ready = true
  }
  if (!ready) {
    Box(Modifier.fillMaxSize().testTag(F8p17Tags.Preparing), contentAlignment = Alignment.Center) { FluidSpinner() }
    return
  }
  val camera = remember {
    FluidStillScanCamera(
      sheets = F8p17Assets.Sheets.map { name -> suspend { withContext(Dispatchers.IO) { F8p17Assets.bitmap(context, name) } } },
      table = { withContext(Dispatchers.IO) { F8p17Assets.bitmap(context, F8p17Assets.Table) } },
    )
  }
  FluidDocumentScanner(
    state = state,
    camera = camera,
    onDone = { result = it },
    onCancel = onBack,
    openReview = review,
  )
}

/** I file degli asset nel visore, con «Leggi testo» (l'estensione si scopre da sola). */
@Composable
private fun F8p17ViewerScene(onBack: () -> Unit, names: List<String>) {
  val context = LocalContext.current
  val files by produceState<List<FluidViewerFile>?>(null, names) {
    value = names.map { name ->
      val file = F8p17Assets.file(context, name)
      viewerFile(file, when (file.extension) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "image/jpeg" })
    }
  }
  val list = files
  if (list == null) {
    Box(Modifier.fillMaxSize().testTag(F8p17Tags.Preparing), contentAlignment = Alignment.Center) { FluidSpinner() }
    return
  }
  F8p17Viewer(list, onBack)
}

@Composable
private fun F8p17Viewer(list: List<FluidViewerFile>, onBack: () -> Unit) {
  val opened = rememberFluidViewerFiles(list)
  FluidViewerInfoProvider(info = { i -> list.getOrNull(i)?.let { f -> FluidViewerInfo(file = FluidFileInfo(f.name, f.mimeType, f.sizeBytes)) } }) {
    FluidFileViewer(count = opened.count, content = opened::content, onBack = onBack, modifier = Modifier.testTag(F8p17Tags.Result))
  }
}

private fun viewerFile(file: File, mime: String) =
  FluidViewerFile(Uri.fromFile(file), file.name, mime, file.length(), file.lastModified().takeIf { it > 0 })
