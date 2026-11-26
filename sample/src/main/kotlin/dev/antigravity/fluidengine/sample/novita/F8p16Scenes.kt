package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ink.FluidInkCanvas
import dev.antigravity.fluidengine.ink.FluidInkCodec
import dev.antigravity.fluidengine.ink.FluidInkDrawingPolicy
import dev.antigravity.fluidengine.ink.FluidInkEditor
import dev.antigravity.fluidengine.ink.FluidInkExport
import dev.antigravity.fluidengine.ink.FluidInkSheet
import dev.antigravity.fluidengine.ink.FluidInkSheetLoad
import dev.antigravity.fluidengine.ink.FluidInkState
import dev.antigravity.fluidengine.ink.FluidInkTool
import dev.antigravity.fluidengine.ink.rememberFluidInkPdfSheet
import dev.antigravity.fluidengine.ink.ready
import dev.antigravity.fluidengine.media.FluidMediaPicker
import dev.antigravity.fluidengine.media.FluidMediaRequest
import dev.antigravity.fluidengine.media.rememberFluidMediaPickerState
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// F8.16 · Disegno e annotazioni (`disegno-e-annotazioni`, engine-ink), le scene della pagina «Elementi nuovi»: lo schizzo
// su un foglio bianco (Pampa Notes), la foto annotata (MyCloud Gallery, anche dal selettore di F8.14), il PDF firmato
// (Universal Converter) e il riquadro della firma (Pampa Mail). Le foto e il PDF sono quelli di prova della galleria; i
// file consegnati vanno nella cache della galleria, mai nella libreria del telefono.

/** La sezione della pagina. */
internal val F8p16Section: NewElementSection
  get() = NewElementSection(
    pack = "F8.16",
    title = "Disegno e annotazioni",
    scenes = F8p16Scenes,
    detail = "Il foglio e la tavolozza di vetro di Markup: penna, evidenziatore, matita, gomma, righello, lazo, forme; la foto e il PDF annotati",
  )

internal val F8p16Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "disegno-foglio", "Uno schizzo",
    "Pampa Notes · il foglio bianco, la tavolozza di vetro, la penna dello stilo, le forme tenendo fermo, annulla e ripristina",
  ) { onBack -> F8p16SketchScene(onBack) },
  NewElementScene(
    "annota-foto", "Scrivi su una foto",
    "MyCloud Gallery · una foto chiara, una scura o dal selettore: la copia annotata a piena risoluzione",
  ) { onBack -> F8p16PhotoScene(onBack) },
  NewElementScene(
    "annota-pdf", "Firma un PDF",
    "Universal Converter · le pagine in fila, il dito o la penna, un PDF nuovo coi tratti come vettori",
  ) { onBack -> F8p16PdfScene(onBack) },
  NewElementScene(
    "firma", "La firma",
    "Pampa Mail · il riquadro della firma nella pagina: un PNG trasparente ritagliato attorno ai tratti",
  ) { onBack -> F8p16SignatureScene(onBack) },
)

/** I tag delle prove e delle riprese. */
internal object F8p16Tags {
  const val Result = "f8p16:risultato"
  const val ResultImage = "f8p16:risultato:immagine"
  const val Again = "f8p16:di-nuovo"
  const val PhotoLight = "f8p16:foto-chiara"
  const val PhotoDark = "f8p16:foto-scura"
  const val PhotoPicker = "f8p16:foto-selettore"
  const val SignatureBox = "f8p16:firma:riquadro"
  const val SignatureClear = "f8p16:firma:cancella"
  const val SignatureDone = "f8p16:firma:fatto"
  const val SignatureImage = "f8p16:firma:immagine"
}

/** Un file della cache della galleria per quello che consegnano le scene. */
private fun f8p16File(context: Context, name: String): File = File(context.cacheDir, "f8p16").apply { mkdirs() }.let { File(it, name) }

/** Quello che la scena ha ricevuto dalla consegna: l'immagine (o la prima pagina) e una riga che dice cos'e'. */
private class F8p16Delivered(val image: ImageBitmap?, val summary: String)

// --- Lo schizzo ---------------------------------------------------------------------------------------------------------

@Composable
private fun F8p16SketchScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { FluidInkState() }
  val sheet = remember { FluidInkSheet.blank() }
  var editing by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var delivered by remember { mutableStateOf<F8p16Delivered?>(null) }
  if (editing) {
    FluidInkEditor(
      state = state,
      sheet = sheet.ready(),
      onCancel = onBack,
      onDone = {
        busy = true
        scope.launch {
          // Due consegne, come farebbe Pampa Notes: i tratti per riaprire lo schizzo, e l'immagine per la nota.
          val saved = state.encode()
          val file = f8p16File(context, "schizzo.png")
          val result = withContext(Dispatchers.IO) { FileOutputStream(file).use { FluidInkExport.drawing(state.strokes, it, size = sheet.size, scale = 2f) } }
          val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }
          val reopened = FluidInkCodec.decode(saved).size
          delivered = F8p16Delivered(
            bitmap?.asImageBitmap(),
            "PNG ${result.width} × ${result.height} · ${state.strokes.size} tratti salvati in ${saved.size / 1024 + 1} kB (riaperti: $reopened)",
          )
          busy = false
          editing = false
        }
      },
      title = "Schizzo",
      busy = busy,
    )
  } else {
    F8p16Result("Uno schizzo", delivered, onAgain = { editing = true }, onBack = onBack)
  }
}

// --- La foto ------------------------------------------------------------------------------------------------------------

private enum class F8p16Photo(val label: String, val resource: Int) {
  Light("Foto chiara", R.drawable.viewer_flower),
  Dark("Foto scura", R.drawable.viewer_astronaut),
}

@Composable
private fun F8p16PhotoScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var photo by remember { mutableStateOf<Bitmap?>(null) }
  var picking by remember { mutableStateOf(false) }
  var delivered by remember { mutableStateOf<F8p16Delivered?>(null) }
  var busy by remember { mutableStateOf(false) }
  val current = photo
  when {
    picking -> {
      // Il selettore di F8.14 con le sorgenti finte della galleria: la foto scelta arriva come la riceverebbe l'app.
      val sources = remember(context) { F8p14Sources(context) }
      val state = rememberFluidMediaPickerState(sources.all, FluidMediaRequest.SinglePhoto)
      FluidMediaPicker(
        state = state,
        onResult = { media ->
          val item = media.firstOrNull()?.item
          photo = item?.let { F8p14Images.thumbnail(context, it, 2400) } ?: decodePhoto(context, F8p16Photo.Light.resource)
          picking = false
        },
        onCancel = { picking = false },
        offerSystemPicker = false,
      )
    }
    current != null -> {
      val inkState = remember(current) { FluidInkState() }
      val sheet = remember(current) { FluidInkSheet.image(current.asImageBitmap(), IntSize(current.width, current.height)) }
      BackHandler { photo = null }
      FluidInkEditor(
        state = inkState,
        sheet = sheet.ready(),
        onCancel = { photo = null },
        onDone = {
          busy = true
          scope.launch {
            val file = f8p16File(context, "foto-annotata.jpg")
            val result = withContext(Dispatchers.IO) { FileOutputStream(file).use { FluidInkExport.image(context, sheet, inkState.strokes, it) } }
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }
            delivered = F8p16Delivered(bitmap?.asImageBitmap(), "JPEG ${result.width} × ${result.height} · ${file.length() / 1024} kB · un file nuovo")
            busy = false
            photo = null
          }
        },
        title = "Annota la foto",
        busy = busy,
      )
    }
    delivered != null -> F8p16Result("Scrivi su una foto", delivered, onAgain = { delivered = null }, onBack = onBack)
    else -> FluidScreen(title = "Scrivi su una foto", subtitle = "F8.16 · scegli la foto da annotare", onBack = onBack) {
      item(key = "foto") {
        FluidListGroup {
          F8p16Photo.entries.forEachIndexed { index, p ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = p.label,
              subtitle = if (p == F8p16Photo.Light) "Il fiore: i tratti scuri sopra" else "L'astronauta: i tratti chiari sopra",
              onClick = { photo = decodePhoto(context, p.resource) },
              modifier = Modifier.testTag(if (p == F8p16Photo.Light) F8p16Tags.PhotoLight else F8p16Tags.PhotoDark),
            )
          }
          FluidListDivider()
          FluidListRow(
            title = "Dal selettore",
            subtitle = "Il selettore di foto di F8.14, con MyCloud Gallery finta",
            onClick = { picking = true },
            modifier = Modifier.testTag(F8p16Tags.PhotoPicker),
          )
        }
      }
      item(key = "nota") {
        FluidSectionFootnote("La copia annotata va nella cache della galleria: la foto di partenza non si tocca.")
      }
    }
  }
}

private fun decodePhoto(context: Context, resource: Int): Bitmap =
  BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inScaled = false })

// --- Il PDF -------------------------------------------------------------------------------------------------------------

@Composable
private fun F8p16PdfScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val source = remember(context) {
    f8p16File(context, "note.pdf").also { file ->
      if (!file.exists()) context.resources.openRawResource(R.raw.viewer_notes).use { input -> FileOutputStream(file).use { input.copyTo(it) } }
    }
  }
  var editing by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var delivered by remember { mutableStateOf<F8p16Delivered?>(null) }
  val state = remember { FluidInkState() }
  if (editing) {
    val load = rememberFluidInkPdfSheet(Uri.fromFile(source))
    FluidInkEditor(
      state = state,
      sheet = load,
      onCancel = onBack,
      onDone = {
        val sheet = (load as? FluidInkSheetLoad.Ready)?.sheet ?: return@FluidInkEditor
        busy = true
        scope.launch {
          val file = f8p16File(context, "note-firmate.pdf")
          val result = FileOutputStream(file).use { FluidInkExport.pdf(sheet, state.strokes, it) }
          val first = withContext(Dispatchers.IO) { firstPage(file) }
          delivered = F8p16Delivered(first?.asImageBitmap(), "PDF di ${result.pages} pagine · ${file.length() / 1024} kB · i tratti sono vettori")
          busy = false
          editing = false
        }
      },
      title = "Note.pdf",
      busy = busy,
    )
  } else {
    F8p16Result("Firma un PDF", delivered, onAgain = { editing = true }, onBack = onBack)
  }
}

/** La prima pagina del PDF consegnato, per vederlo (sul computer non c'e' `PdfRenderer`: null). */
private fun firstPage(file: File): Bitmap? = try {
  ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
    PdfRenderer(fd).use { renderer ->
      renderer.openPage(0).use { page ->
        val w = 900
        val bitmap = Bitmap.createBitmap(w, (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
      }
    }
  }
} catch (e: Throwable) {
  null
}

// --- La firma -----------------------------------------------------------------------------------------------------------

@Composable
private fun F8p16SignatureScene(onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  // Per la firma disegna anche il dito, sempre: e' un riquadro piccolo, non una pagina da scorrere.
  val state = remember { FluidInkState(tool = FluidInkTool.Pen, drawingPolicy = FluidInkDrawingPolicy.AnyInput).also { it.shapeRecognition = false } }
  val sheet = remember { FluidInkSheet.blank(paper = Color.Transparent) }
  var signature by remember { mutableStateOf<ImageBitmap?>(null) }
  FluidScreen(title = "La firma", subtitle = "F8.16 · Pampa Mail: firmare un allegato e rispondere", onBack = onBack) {
    item(key = "documento") {
      Column(
        Modifier
          .fillMaxWidth()
          .clip(ContinuousCornerShape(FluidRadius.Card))
          .background(Color.White)
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Modulo di consenso", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Text(
          "Il sottoscritto autorizza la gita del 12 dicembre al Museo della Scienza, con partenza alle 8:15 e rientro alle 16:30.",
          style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A3A3C),
        )
        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.BottomStart) {
          val image = signature
          if (image != null) {
            Image(image, "La firma", Modifier.height(80.dp).testTag(F8p16Tags.SignatureImage), contentScale = ContentScale.Fit)
          }
          Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFC7C7CC)))
        }
        Text("Firma del genitore", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6C6C70))
      }
    }
    item(key = "riquadro") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FluidSectionHeader(title = "Firma qui", detail = "Col dito o con la penna")
        FluidInkCanvas(
          state = state,
          sheet = sheet,
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .clip(ContinuousCornerShape(FluidRadius.Card))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ContinuousCornerShape(FluidRadius.Card))
            .testTag(F8p16Tags.SignatureBox),
          selectionActions = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FluidGlassButton("Cancella", onClick = { state.clear(); signature = null }, enabled = !state.isEmpty, modifier = Modifier.testTag(F8p16Tags.SignatureClear))
          FluidGlassButton(
            "Firma",
            onClick = {
              scope.launch {
                val bytes = java.io.ByteArrayOutputStream()
                FluidInkExport.drawing(state.strokes, bytes, scale = 3f, background = null, cropToContent = true)
                signature = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())?.asImageBitmap()
              }
            },
            enabled = !state.isEmpty,
            modifier = Modifier.testTag(F8p16Tags.SignatureDone),
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote("La firma esce trasparente e ritagliata attorno ai tratti: si posa su un documento di qualsiasi colore.")
    }
  }
}

// --- Il risultato -------------------------------------------------------------------------------------------------------

@Composable
private fun F8p16Result(title: String, delivered: F8p16Delivered?, onAgain: () -> Unit, onBack: () -> Unit) {
  BackHandler { onBack() }
  FluidScreen(title = title, subtitle = "F8.16 · cosa riceve l'app", onBack = onBack, modifier = Modifier.testTag(F8p16Tags.Result)) {
    val image = delivered?.image
    if (image != null) {
      item(key = "immagine") {
        Image(
          image, "Il file consegnato",
          Modifier
            .fillMaxWidth()
            .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
            .clip(ContinuousCornerShape(FluidRadius.Card))
            .testTag(F8p16Tags.ResultImage),
          contentScale = ContentScale.Fit,
        )
      }
    }
    item(key = "riga") {
      FluidListGroup {
        FluidListRow(title = delivered?.summary ?: "Niente da consegnare", subtitle = "Il file nuovo, nella cache della galleria", disclosure = false)
        FluidListDivider()
        FluidListRow(title = "Continua a disegnare", subtitle = "Riapre l'editor coi tratti di prima", onClick = onAgain, modifier = Modifier.testTag(F8p16Tags.Again))
      }
    }
  }
}

/** Le voci da provare a mano (in coda a «Da provare»). */
internal val F8p16TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-16-tratto", TryArea.ContentData, "Il tratto che segue il dito",
    "Apri «Uno schizzo» e scrivi veloce una parola col dito: giudica se il tratto sta sotto il dito senza ritardo, se " +
      "finito non lampeggia e se la penna dello stilo, appena tocca, disegna lei e il dito fa scorrere la pagina.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Uno schizzo"),
  ),
  TryItem(
    "f8-16-forme", TryArea.ContentData, "Le forme tenendo fermo",
    "Disegna un cerchio, un rettangolo, un triangolo e una linea e alla fine tieni fermo il dito: giudica se diventano la " +
      "forma giusta con un tic, e se la linea si allunga e gira prima di alzare il dito.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Uno schizzo"),
  ),
  TryItem(
    "f8-16-tavolozza", TryArea.ContentData, "La tavolozza di vetro",
    "Tocca la penna gia' scelta, cambia spessore e opacita', prova monolinea, gomma (a pixel, poi per oggetti), lazo " +
      "(sposta, duplica) e righello (gira con due dita): giudica il vetro accanto a Markup, se le opzioni nascono dallo " +
      "strumento e se la tavolozza tenuta per la maniglia va in alto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Uno schizzo"),
  ),
  TryItem(
    "f8-16-consegna", TryArea.ContentData, "La foto e il PDF annotati",
    "Apri «Scrivi su una foto» (chiara e scura) e «Firma un PDF», disegna e tocca ✓: giudica se la copia ha i tratti dove " +
      "li avevi messi, nitidi, e se la «x» con dei tratti chiede di eliminare le modifiche.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Scrivi su una foto"),
  ),
)
