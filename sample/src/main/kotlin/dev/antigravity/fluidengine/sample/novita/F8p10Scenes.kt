package dev.antigravity.fluidengine.sample.novita

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.qr.ProvideFluidQr
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sharein.FluidShareInContent
import dev.antigravity.fluidengine.sharein.FluidShareInDestination
import dev.antigravity.fluidengine.sharein.FluidShareInPolicy
import dev.antigravity.fluidengine.sharein.FluidShareInSheet
import dev.antigravity.fluidengine.sharein.FluidShareInShortcuts
import dev.antigravity.fluidengine.sharein.FluidShareInWindow
import dev.antigravity.fluidengine.sharein.rememberFluidShareInState
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.icons.Code
import dev.antigravity.fluidengine.ui.fluid.icons.Event
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.School
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.output.FluidPdfDocument
import dev.antigravity.fluidengine.ui.fluid.output.FluidShareImageFormat
import dev.antigravity.fluidengine.ui.fluid.output.FluidShareImageFrame
import dev.antigravity.fluidengine.ui.fluid.output.fluidPdfDocument
import dev.antigravity.fluidengine.ui.fluid.output.rememberFluidPdfWriter
import dev.antigravity.fluidengine.ui.fluid.output.rememberFluidShareImageRenderer
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyCheck
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyChoice
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyFormat
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyOption
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyTools
import dev.antigravity.fluidengine.ui.fluid.share.FluidPrepareCopySheet
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareCopyOptions
import dev.antigravity.fluidengine.ui.fluid.share.FluidPreparedCopy
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareAction
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareOutcome
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheet
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareTrust
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// F8.10 · Condivisione, nei casi delle app di Alessio (le schede `foglio-condividi`, `prepara-la-copia`,
// `uscite-stampabili`, `arrivi-da-altre-app`): il voto e l'impegno di ClasseViva come immagine «senza fare lo
// screenshot» (la nota di Alessio), la foto di MyCloud Gallery senza posizione, la pagella in PDF e in stampa, il link di
// Pampa Store anche come QR, e gli arrivi in Pampa Notes da un'altra app.

/** Le scene di F8.10, nell'ordine delle idee. */
internal val F8p10Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "condividi-voto", "Un voto come immagine",
    "ClasseViva · «Condividi» nella barra: l'immagine del voto, il foglio di vetro, le app Pampa e le azioni",
  ) { GradeShareScene(it) },
  NewElementScene(
    "immagini-tre-formati", "Voto e impegno in tre formati",
    "L'immagine da condividere in 1:1, 4:5 e 9:16, col fondo dell'accento e il materiale «stampa»",
  ) { FormatsScene(it) },
  NewElementScene(
    "prepara-foto", "Una foto senza posizione",
    "MyCloud Gallery · le opzioni prima del foglio: l'app fa la copia, l'engine la verifica e blocca se non torna",
  ) { PrepareScene(it) },
  NewElementScene(
    "pdf-pagella", "La pagella in PDF e in stampa",
    "ClasseViva · piu' pagine con testo vero, intestazione e numeri; la stampa con l'anteprima di Android",
  ) { PdfScene(it) },
  NewElementScene(
    "condividi-link", "Un link, anche come QR",
    "Pampa Store · il link di un'app nel foglio, con «Mostra come QR» e un'azione dell'app",
  ) { LinkScene(it) },
  NewElementScene(
    "arrivi", "Arrivi da altre app",
    "Pampa Notes · testo, link, foto, un file troppo grande: il foglio sopra l'app di partenza",
  ) { ArrivalsScene(it) },
)

/** Le voci da provare a mano di F8.10, in coda a quelle di «Da provare». */
internal val F8p10TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-10-foglio", TryArea.ContentData, "Il foglio di condivisione",
    "Apri «Un voto come immagine» e tocca Condividi nella barra: giudica se il foglio nasce dal tasto, se la miniatura " +
      "entra sfocata e se la fila delle app scorre nitida; poi prova Copia, Salva in File, Stampa e «Altre app…».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Un voto come immagine"),
  ),
  TryItem(
    "f8-10-formati", TryArea.ContentData, "Immagini in tre formati",
    "Apri «Voto e impegno in tre formati» in chiaro e in scuro: giudica se il voto e l'impegno si leggono nei tre " +
      "formati e se il pannello «stampa» sembra il vetro dell'app senza esserlo.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Voto e impegno in tre formati"),
  ),
  TryItem(
    "f8-10-copia", TryArea.ContentData, "Prepara la copia",
    "Apri «Una foto senza posizione», tocca Condividi e spegni «Posizione»: giudica l'attesa e il riepilogo; poi accendi " +
      "«Esportatore difettoso» e verifica che «Condividi» resti spento.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Una foto senza posizione"),
  ),
  TryItem(
    "f8-10-pdf", TryArea.ContentData, "PDF e stampa",
    "Apri «La pagella in PDF e in stampa», tocca «Salva PDF» e poi «Stampa»: giudica l'anteprima di Android, i numeri " +
      "di pagina e che nessuna riga sia tagliata fra due pagine; seleziona una parola nel PDF aperto da un'altra app.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La pagella in PDF e in stampa"),
  ),
  TryItem(
    "f8-10-arrivi", TryArea.ContentData, "Arrivi da altre app",
    "Da Chrome o da Galleria tocca Condividi e scegli Fluid Glass: giudica il foglio sopra l'altra app, la copia con " +
      "l'avanzamento e «Salva»; la seconda volta la destinazione usata compare in alto nel foglio di Android.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Arrivi da altre app"),
  ),
)

/** I tag delle prove. */
internal object F8p10Tags {
  const val Format = "f8p10:formato"
  const val Outcome = "f8p10:esito"
  const val Gallery = "f8p10:galleria"
  const val Broken = "f8p10:difettoso"
  const val SavePdf = "f8p10:salva-pdf"
  const val PrintPdf = "f8p10:stampa"
  const val PdfCheck = "f8p10:verifica-pdf"
  const val LongPdf = "f8p10:pdf-lungo"
  const val Inbox = "f8p10:arrivi"
  const val OptionsFirst = "f8p10:opzioni-prima"
  const val ShareButton = "condividi"
  fun simulate(id: String): String = "f8p10:simula:$id"
}

/**
 * Le app della famiglia Pampa per la fila del foglio. La galleria di debug e' firmata con la chiave di debug, quindi
 * «stessa firma» non troverebbe le app vere di Alessio: si fida anche dell'impronta del certificato di Pampa, se
 * l'app ne ha una (il manifest dello store la porta), e della propria.
 */
private val SampleTrust: FluidShareTrust = FluidShareTrust.certificates(setOf(PampaCertificateSha256, DebugCertificateSha256))

/** L'impronta SHA-256 del certificato di Pampa (CN=Pampa, O=PampaStore), letta da pampa-notes-1.0.1.apk: le app vere di Alessio. */
internal const val PampaCertificateSha256: String = "825738500ce9605429ffba3c4019228f992f2c37d1b70a2230a7ce2c25637e43"

/** L'impronta della chiave di debug di questo PC: le build di prova delle app Pampa si vedono fra loro. */
internal const val DebugCertificateSha256: String = "d7a409a3686ee645449d08741e8015af3f2fef436058167b0b86b6003e12497e"

private fun describe(outcome: FluidShareOutcome): String = when (outcome) {
  is FluidShareOutcome.SentTo -> "Mandato a ${outcome.target.label}"
  FluidShareOutcome.Chooser -> "Aperto il foglio di Android"
  FluidShareOutcome.Copied -> "Copiato"
  is FluidShareOutcome.Saved -> "Salvato in ${outcome.uris.size} file"
  FluidShareOutcome.Printed -> "Aperta l'anteprima di stampa"
  FluidShareOutcome.QrShown -> "Mostrato il QR"
  is FluidShareOutcome.Action -> "Azione «${outcome.id}»"
}

// --- I dati finti delle app -----------------------------------------------------------------------------------------

private class SampleGrade(val subject: String, val short: String, val value: String, val kind: String, val date: String, val teacher: String, val note: String)

private val Grade = SampleGrade("Matematica", "MAT", "8½", "Scritto", "24 novembre 2026", "Prof.ssa Bianchi", "Equazioni di secondo grado")

private class SampleEvent(val title: String, val whenText: String, val detail: String)

private val Event = SampleEvent("Verifica di storia", "Giovedì 26 novembre · 2ª ora", "Capitoli 4–6: la Rivoluzione francese")

/** Il voto come lo disegna l'app: la materia, il voto nel cerchio, la data e la nota. */
@Composable
private fun GradeCard(grade: SampleGrade) {
  val good = MaterialTheme.colorScheme.primary
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(grade.subject, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("${grade.kind} · ${grade.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Box(
        Modifier.size(72.dp).clip(FluidCapsuleShape).background(good.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Text(grade.value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = good)
      }
    }
    Spacer(Modifier.height(14.dp))
    Text(grade.note, style = MaterialTheme.typography.bodyLarge)
    Text(grade.teacher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** L'impegno dell'agenda come lo disegna l'app. */
@Composable
private fun EventCard(event: SampleEvent) {
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(44.dp).clip(ContinuousCornerShape(FluidRadius.Control)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) { FluidIcon(FluidIcons.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(event.whenText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
      }
    }
    Spacer(Modifier.height(12.dp))
    Text(event.detail, style = MaterialTheme.typography.bodyLarge)
  }
}

/** Il voto nella sua cornice da condividere. */
@Composable
internal fun GradeImage() = FluidShareImageFrame(signature = "ClasseViva") { GradeCard(Grade) }

/** L'impegno nella sua cornice da condividere. */
@Composable
internal fun EventImage() = FluidShareImageFrame(signature = "ClasseViva · Agenda") { EventCard(Event) }

/** Lo schermo di una scena: il titolo, la sigla, «Condividi» nella barra (se c'e'). */
@Composable
private fun ShareScreen(title: String, onBack: () -> Unit, onShare: (() -> Unit)?, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  FluidScreen(
    title = title,
    subtitle = "F8.10 · Condivisione",
    onBack = onBack,
    toolbar = {
      title(FluidToolbarTitleMode.Inline)
      if (onShare != null) button(F8p10Tags.ShareButton, FluidIcons.IosShare, "Condividi", onClick = onShare)
    },
    content = content,
  )
}

// --- Il voto come immagine ------------------------------------------------------------------------------------------

@Composable
private fun GradeShareScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val renderer = rememberFluidShareImageRenderer()
  val scope = rememberCoroutineScope()
  var format by rememberSaveable { mutableIntStateOf(1) }
  var item by remember { mutableStateOf<FluidShareItem?>(null) }
  var outcome by remember { mutableStateOf("Nessuna condivisione") }
  val source = rememberFluidToolbarTransitionSource(F8p10Tags.ShareButton)
  ProvideFluidQr {
    ShareScreen("Un voto come immagine", onBack, onShare = {
      scope.launch {
        val chosen = FluidShareImageFormat.all[format]
        runCatching {
          val file = renderer.renderToFile(chosen, "Voto di matematica ${chosen.name.replace(':', 'x')}.png") { GradeImage() }
          item = FluidShareItem.file(context, file, "image/png", title = "Voto di matematica")
        }.onFailure { outcome = "L'immagine non si è fatta: ${it.message}" }
      }
    }) {
      item(key = "formato") {
        FluidSegmentedControl(
          options = FluidShareImageFormat.all,
          selected = FluidShareImageFormat.all[format],
          onSelect = { format = FluidShareImageFormat.all.indexOf(it) },
          modifier = Modifier.fillMaxWidth().testTag(F8p10Tags.Format),
          label = { it.name },
        )
      }
      item(key = "voti") {
        Column {
          FluidSectionHeader(title = "Primo quadrimestre", detail = "Tocca Condividi: parte l'immagine del voto, non uno screenshot")
          FluidListGroup {
            FluidListRow(title = "Matematica · ${Grade.value}", subtitle = "${Grade.kind} · ${Grade.date} · ${Grade.note}", leading = {
              FluidIcon(FluidIcons.School, contentDescription = null)
            })
            FluidListDivider()
            FluidListRow(title = "Italiano · 7", subtitle = "Orale · 20 novembre 2026", disclosure = false)
            FluidListDivider()
            FluidListRow(title = "Inglese · 9", subtitle = "Scritto · 18 novembre 2026", disclosure = false)
          }
        }
      }
      item(key = "esito") { FluidSectionFootnote(text = "Ultimo esito: $outcome", modifier = Modifier.testTag(F8p10Tags.Outcome)) }
    }
    FluidShareSheet(
      item = item,
      onDismissRequest = { item = null },
      source = source,
      trust = SampleTrust,
      onShared = { outcome = describe(it) },
    )
  }
}

// --- Le immagini nei tre formati ------------------------------------------------------------------------------------

private class RenderedImage(val label: String, val file: File, val preview: ImageBitmap)

@Composable
private fun FormatsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val renderer = rememberFluidShareImageRenderer()
  var images by remember { mutableStateOf<List<RenderedImage>>(emptyList()) }
  var item by remember { mutableStateOf<FluidShareItem?>(null) }
  val dark = MaterialTheme.colorScheme.surface.red < 0.5f
  LaunchedEffect(dark) {
    val out = ArrayList<RenderedImage>()
    for ((name, content) in listOf<Pair<String, @Composable () -> Unit>>("Voto" to { GradeImage() }, "Impegno" to { EventImage() })) {
      for (format in FluidShareImageFormat.all) {
        // Una tela che non entra nella memoria non ferma la pagina: quella immagine manca.
        val file = runCatching {
          renderer.renderToFile(format, "$name ${format.name.replace(':', 'x')}${if (dark) " scuro" else ""}.png", content)
        }.getOrNull() ?: continue
        val preview = withContext(Dispatchers.IO) {
          BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
        } ?: continue
        out += RenderedImage("$name · ${format.name}", file, preview)
        images = out.toList()
      }
    }
  }
  ShareScreen("Voto e impegno in tre formati", onBack, onShare = null) {
    item(key = "galleria") {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(F8p10Tags.Gallery),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        images.forEach { image ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
              image.preview,
              contentDescription = image.label,
              contentScale = ContentScale.Fit,
              modifier = Modifier.height(260.dp).aspectRatio(image.preview.width.toFloat() / image.preview.height)
                .clip(ContinuousCornerShape(FluidRadius.Control)),
            )
            Spacer(Modifier.height(6.dp))
            FluidButton(image.label, onClick = {
              item = FluidShareItem.file(context, image.file, "image/png", title = image.label)
            }, style = FluidButtonStyle.Tinted)
          }
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Le immagini sono fatte fuori dallo schermo da una vista Compose, a 1080 px di larghezza: il testo e' " +
          "testo, il vetro diventa il materiale piatto «stampa». In scuro escono scure.",
      )
    }
  }
  FluidShareSheet(item = item, onDismissRequest = { item = null }, trust = SampleTrust)
}

// --- Prepara la copia -----------------------------------------------------------------------------------------------

/** La foto di prova con la posizione (Firenze) nei dati EXIF, preparata una volta nella cartella dell'app. */
private fun samplePhotoWithLocation(context: Context): File {
  val file = File(File(context.filesDir, "f8-10").apply { mkdirs() }, "Fiori con posizione.jpg")
  if (file.exists() && file.length() > 0) return file
  val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.viewer_flower)
  file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
  bitmap.recycle()
  ExifInterface(file.path).apply {
    setAttribute(ExifInterface.TAG_GPS_LATITUDE, "43/1,46/1,1200/100")
    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "11/1,15/1,600/100")
    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
    setAttribute(ExifInterface.TAG_MAKE, "Pampa")
    saveAttributes()
  }
  return file
}

@Composable
private fun PrepareScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var options by remember { mutableStateOf(false) }
  var item by remember { mutableStateOf<FluidShareItem?>(null) }
  var broken by rememberSaveable { mutableStateOf(false) }
  var slow by rememberSaveable { mutableStateOf(true) }
  val source = rememberFluidToolbarTransitionSource(F8p10Tags.ShareButton)
  val photo by androidx.compose.runtime.produceState<File?>(null) { value = withContext(Dispatchers.IO) { samplePhotoWithLocation(context) } }
  val formats = remember { listOf(FluidCopyFormat("originale", "Originale"), FluidCopyFormat("leggera", "Più leggera")) }
  val copyFields = remember {
    listOf(
      FluidCopyOption("posizione", "Posizione", "Dove è stata scattata la foto"),
      FluidCopyOption("fotocamera", "Dati della fotocamera", "Marca e impostazioni"),
    )
  }
  val prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy = { choice ->
    preparePhoto(context, photo ?: samplePhotoWithLocation(context), choice, broken, slow)
  }
  val copyOptions = remember(broken, slow, photo) { FluidShareCopyOptions(prepare, formats, copyFields) }
  // Come Foto in iOS 26: «Condividi» apre il foglio con l'originale; «Opzioni ›» nella testata apre le opzioni dentro
  // il foglio, e confermate il foglio mostra la copia verificata.
  ShareScreen("Una foto senza posizione", onBack, onShare = {
    photo?.let { item = FluidShareItem.file(context, it, "image/jpeg", title = "1 foto", ).copy(detail = "Posizione inclusa") }
  }) {
    item(key = "foto") {
      Image(
        painterResource(R.drawable.viewer_flower),
        contentDescription = "Fiori al vento",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(ContinuousCornerShape(FluidRadius.Group)),
      )
    }
    item(key = "info") {
      FluidListGroup {
        FluidListRow(title = "Posizione dell'originale", subtitle = "43° 46′ N, 11° 15′ E · Firenze", disclosure = false)
        FluidListDivider()
        FluidListRow(title = "File", subtitle = photo?.let { "${it.name} · ${it.length() / 1024} KB" } ?: "…", disclosure = false)
      }
    }
    item(key = "prove") {
      FluidListGroup {
        FluidToggleRow(
          title = "Esportatore difettoso",
          subtitle = "Una prova: l'app dimentica di togliere la posizione. L'engine se ne accorge e blocca l'invio.",
          checked = broken,
          onCheckedChange = { broken = it },
          modifier = Modifier.testTag(F8p10Tags.Broken),
        )
        FluidListDivider()
        FluidToggleRow(title = "App lenta", subtitle = "La copia aspetta un secondo, per vedere l'attesa.", checked = slow, onCheckedChange = { slow = it })
      }
    }
    item(key = "prima") {
      // Il foglio delle opzioni a se', per l'app che vuole chiedere prima di aprire il foglio di condivisione.
      FluidButton(
        "Le opzioni prima del foglio",
        onClick = { options = true },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
        modifier = Modifier.testTag(F8p10Tags.OptionsFirst),
      )
    }
  }
  FluidPrepareCopySheet(
    visible = options,
    onDismissRequest = { options = false },
    onReady = { ready ->
      options = false
      item = ready
    },
    prepare = prepare,
    formats = formats,
    options = copyFields,
  )
  FluidShareSheet(
    item = item,
    onDismissRequest = { item = null },
    source = source,
    trust = SampleTrust,
    copyOptions = copyOptions,
  )
}

/** La copia vera della foto, come la farebbe MyCloud Gallery, con le verifiche fatte sul file uscito. */
private suspend fun preparePhoto(context: Context, original: File, choice: FluidCopyChoice, broken: Boolean, slow: Boolean): FluidPreparedCopy =
  withContext(Dispatchers.IO) {
    if (slow) delay(1000)
    val light = choice.format == "leggera"
    val target = FluidShareFiles.newFile(context, if (light) "Fiori (leggera).jpg" else "Fiori.jpg")
    val source = Uri.fromFile(original)
    if (light) {
      val bitmap = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = 2 })
      target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
      bitmap.recycle()
      // Una ricompressione perde tutto l'EXIF: si rimette cio' che l'utente ha chiesto di tenere.
      val exif = ExifInterface(target.path)
      val from = ExifInterface(original.path)
      if (choice.includes("posizione") || broken) FluidCopyTools.LocationTags.forEach { tag -> from.getAttribute(tag)?.let { exif.setAttribute(tag, it) } }
      if (choice.includes("fotocamera")) from.getAttribute(ExifInterface.TAG_MAKE)?.let { exif.setAttribute(ExifInterface.TAG_MAKE, it) }
      exif.saveAttributes()
    } else if (choice.includes("posizione") || broken) {
      FluidCopyTools.copy(context.contentResolver, source, target)
    } else {
      FluidCopyTools.copyWithoutLocation(context.contentResolver, source, target)
    }
    if (!choice.includes("fotocamera") && !light) {
      ExifInterface(target.path).apply { setAttribute(ExifInterface.TAG_MAKE, null); saveAttributes() }
    }
    val hasLocation = FluidCopyTools.hasLocation(target)
    val hasCamera = ExifInterface(target.path).getAttribute(ExifInterface.TAG_MAKE) != null
    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(target.path, it) }
    FluidPreparedCopy(
      item = FluidShareItem.file(context, target, "image/jpeg", title = "1 foto")
        .copy(detail = if (hasLocation) "Posizione inclusa" else "Senza posizione"),
      checks = listOf(
        FluidCopyCheck("Posizione", if (hasLocation) "presente" else "tolta", passed = choice.includes("posizione") || !hasLocation, option = "posizione"),
        FluidCopyCheck("Dati della fotocamera", if (hasCamera) "presenti" else "tolti", passed = choice.includes("fotocamera") || !hasCamera, option = "fotocamera"),
        FluidCopyCheck("Misura", "${size.outWidth} × ${size.outHeight}"),
        FluidCopyCheck("Peso", "${target.length() / 1024} KB"),
      ),
    )
  }

// --- La pagella in PDF ----------------------------------------------------------------------------------------------

private val Subjects = listOf(
  "Italiano" to listOf("7" to "Orale", "7½" to "Scritto", "8" to "Tema argomentativo"),
  "Latino" to listOf("6½" to "Versione", "7" to "Orale"),
  "Storia" to listOf("8" to "Orale", "8½" to "Verifica"),
  "Filosofia" to listOf("7" to "Orale", "7" to "Scritto"),
  "Inglese" to listOf("9" to "Scritto", "8½" to "Orale", "9" to "Listening"),
  "Matematica" to listOf("8½" to "Scritto", "7" to "Orale", "8" to "Scritto"),
  "Fisica" to listOf("7½" to "Scritto", "8" to "Laboratorio"),
  "Scienze" to listOf("8" to "Orale", "7½" to "Verifica"),
  "Storia dell'arte" to listOf("9" to "Orale"),
  "Scienze motorie" to listOf("9" to "Pratico", "8½" to "Pratico"),
  "Educazione civica" to listOf("8" to "Progetto"),
  "Religione" to listOf("Ottimo" to "Giudizio"),
)

private const val TeacherNote = "Lo studente partecipa con interesse e continuità, interviene in modo pertinente e rispetta " +
  "le consegne. Nel secondo periodo si consiglia di curare l'esposizione orale e di approfondire gli argomenti con " +
  "letture personali, che renderanno più solide le competenze già raggiunte. Il lavoro a casa è regolare e ordinato."

/** La pagella: un titolo, una sezione per materia con i voti e il giudizio, e un grafico delle medie fatto in Compose. */
internal fun sampleReportCard(): FluidPdfDocument = fluidPdfDocument("Pagella · primo quadrimestre", locale = Locale.ITALY) {
  title("Pagella del primo quadrimestre", "Classe 4ª · anno scolastico 2026/27 · documento di prova di Fluid Glass")
  compose { AveragesChart() }
  Subjects.forEach { (subject, grades) ->
    heading(subject)
    grades.forEach { (value, kind) -> row(kind, value, "Novembre 2026") }
    paragraph(TeacherNote)
    divider()
  }
  heading("Note del consiglio di classe")
  repeat(3) { paragraph(TeacherNote) }
}

/** Il registro lungo: circa 300 pagine di solo testo, per misurare il tempo e la memoria del PDF a pezzi. */
internal fun sampleLongRegister(sections: Int = 420): FluidPdfDocument = fluidPdfDocument("Registro dell'anno", locale = Locale.ITALY) {
  title("Registro dell'anno", "Documento di prova lungo: $sections lezioni")
  repeat(sections) { i ->
    heading("Lezione ${i + 1}")
    row("Argomento", Subjects[i % Subjects.size].first, "Ora ${(i % 6) + 1}")
    paragraph(TeacherNote)
  }
}

/** Il grafico delle medie, nello stile dell'app: una vista Compose dentro il PDF, con testo vero. */
@Composable
private fun AveragesChart() {
  val averages = listOf("ITA" to 7.5f, "LAT" to 6.75f, "STO" to 8.25f, "ING" to 8.8f, "MAT" to 7.8f, "FIS" to 7.75f, "ART" to 9f)
  Column(
    Modifier.fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Card)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(16.dp),
  ) {
    Text("Medie per materia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
      averages.forEach { (label, value) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(String.format(Locale.ITALY, "%.1f", value), style = MaterialTheme.typography.labelSmall)
          Box(Modifier.width(22.dp).height((value * 9).dp).clip(ContinuousCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
          Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
      }
    }
  }
}

/** Cosa dice il PDF uscito, riletto da Android: le pagine e, da Android 15, il testo della prima pagina. */
private fun readBack(file: File): String = runCatching {
  ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
    PdfRenderer(fd).use { renderer ->
      val pages = renderer.pageCount
      val text = if (Build.VERSION.SDK_INT >= 35) {
        renderer.openPage(0).use { page -> page.textContents.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").take(90) }
      } else null
      "Pagine: $pages · ${file.length() / 1024} KB" + (text?.let { " · testo letto dal PDF: «$it…»" } ?: "")
    }
  }
}.getOrElse { "Non si rilegge: ${it.message}" }

@Composable
private fun PdfScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val writer = rememberFluidPdfWriter()
  val scope = rememberCoroutineScope()
  var item by remember { mutableStateOf<FluidShareItem?>(null) }
  var check by remember { mutableStateOf("Non ancora scritto") }
  val document = remember { sampleReportCard() }
  ShareScreen("La pagella in PDF e in stampa", onBack, onShare = null) {
    item(key = "intro") {
      FluidListGroup {
        FluidListRow(title = "Pagella del primo quadrimestre", subtitle = "12 materie, voti, giudizi e un grafico delle medie", disclosure = false)
      }
    }
    item(key = "tasti") {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidButton("Salva PDF", onClick = {
          scope.launch {
            val file = writer.writeToFile(document, "Pagella primo quadrimestre")
            check = withContext(Dispatchers.IO) { readBack(file) }
            item = FluidShareItem.file(context, file, "application/pdf", title = "Pagella primo quadrimestre")
          }
        }, modifier = Modifier.weight(1f).testTag(F8p10Tags.SavePdf))
        FluidButton("Stampa", onClick = { writer.print(document) }, style = FluidButtonStyle.Tinted,
          modifier = Modifier.weight(1f).testTag(F8p10Tags.PrintPdf))
      }
    }
    item(key = "lungo") {
      FluidButton("PDF lungo (circa 300 pagine)", onClick = {
        scope.launch {
          val started = android.os.SystemClock.elapsedRealtime()
          val result = runCatching {
            dev.antigravity.fluidengine.ui.fluid.output.FluidPdf.writeToFile(context, sampleLongRegister(), "Registro lungo")
          }
          val ms = android.os.SystemClock.elapsedRealtime() - started
          check = result.fold(
            onSuccess = { file -> "PDF lungo in $ms ms. " + withContext(Dispatchers.IO) { readBack(file) } },
            onFailure = { "PDF lungo fermato dopo $ms ms: ${it.message}" },
          )
        }
      }, style = FluidButtonStyle.Plain, modifier = Modifier.fillMaxWidth().testTag(F8p10Tags.LongPdf))
    }
    item(key = "verifica") { FluidSectionFootnote(text = check, modifier = Modifier.testTag(F8p10Tags.PdfCheck)) }
  }
  FluidShareSheet(item = item, onDismissRequest = { item = null }, trust = SampleTrust)
}

// --- Il link come QR ------------------------------------------------------------------------------------------------

@Composable
private fun LinkScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var item by remember { mutableStateOf<FluidShareItem?>(null) }
  var outcome by remember { mutableStateOf("Nessuna condivisione") }
  val source = rememberFluidToolbarTransitionSource(F8p10Tags.ShareButton)
  val url = "https://pampa.example.org/app/dev.antigravity.fluidglass"
  ProvideFluidQr {
    ShareScreen("Un link, anche come QR", onBack, onShare = { item = FluidShareItem.link(url, "Fluid Glass su Pampa Store") }) {
      item(key = "app") {
        FluidListGroup {
          FluidListRow(title = "Fluid Glass", subtitle = "Il vetro liquido dell'engine, da toccare · gratis", disclosure = false)
        }
      }
      item(key = "esito") { FluidSectionFootnote(text = "Ultimo esito: $outcome", modifier = Modifier.testTag(F8p10Tags.Outcome)) }
    }
    FluidShareSheet(
      item = item,
      onDismissRequest = { item = null },
      source = source,
      trust = SampleTrust,
      actions = remember {
        listOf(FluidShareAction("markdown", "Copia come Markdown", FluidIcons.Code) { shared ->
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
          clipboard.setPrimaryClip(android.content.ClipData.newPlainText(shared.title, "[${shared.title}](${shared.text})"))
        })
      },
      onShared = { outcome = describe(it) },
    )
  }
}

// --- Gli arrivi -----------------------------------------------------------------------------------------------------

/** Le destinazioni di Pampa Notes nella galleria. */
internal val ArrivalDestinations: List<FluidShareInDestination> = listOf(
  FluidShareInDestination("appunti", "Appunti", "Il quaderno di tutti i giorni"),
  FluidShareInDestination("scuola", "Scuola", "Compiti e verifiche"),
  FluidShareInDestination("viaggi", "Viaggi", "Prenotazioni e mappe"),
)

/** La categoria delle scorciatoie di condivisione, la stessa di `res/xml/fluid_glass_shortcuts.xml`. */
internal const val ArrivalShareCategory = "dev.antigravity.fluidengine.sample.CONDIVISIONE"

/** Il tetto della demo: 5 MB per file, per provare il file troppo grande senza scriverne uno da 100. */
internal fun arrivalPolicy(context: Context) = FluidShareInPolicy(
  maxFileBytes = 5L * 1024 * 1024,
  maxTotalBytes = 20L * 1024 * 1024,
  trustedAuthorities = setOf(FluidShareFiles.authority(context)),
)

/** Gli arrivi salvati, come li salverebbe Pampa Notes: una cartella per destinazione. */
internal object ArrivalsStore {
  fun root(context: Context): File = File(context.filesDir, "arrivi")

  fun save(context: Context, destination: FluidShareInDestination, content: FluidShareInContent) {
    val dir = File(root(context), destination.id).apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    content.text?.let { File(dir, "$stamp${if (content.selection) " selezione" else ""}.txt").writeText(it) }
    content.link?.let { File(dir, "$stamp.url").writeText(listOfNotNull(content.subject, it).joinToString("\n")) }
    content.files.forEach { file ->
      val target = File(dir, file.name)
      if (!file.renameTo(target)) {
        file.copyTo(target, overwrite = true)
        file.delete()
      }
    }
  }

  fun list(context: Context): List<Pair<String, File>> =
    root(context).listFiles()?.flatMap { dir -> dir.listFiles()?.map { dir.name to it }.orEmpty() }.orEmpty()
      .sortedByDescending { it.second.lastModified() }
}

/** I file finti della demo, esposti dal FileProvider dell'engine (autorita' fidata nella politica della demo). */
private fun demoFile(context: Context, name: String, write: (File) -> Unit): Uri {
  val dir = File(FluidShareFiles.root(context), "demo-arrivi").apply { mkdirs() }
  val file = File(dir, name)
  if (!file.exists() || file.length() == 0L) write(file)
  return FluidShareFiles.uriFor(context, file)
}

private fun demoPhoto(context: Context, name: String, drawable: Int): Uri = demoFile(context, name) { file ->
  val bitmap = BitmapFactory.decodeResource(context.resources, drawable)
  file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
  bitmap.recycle()
}

/** Le forme in cui Android consegna, una per tasto, verso l'attivita' degli arrivi. */
internal fun demoArrival(context: Context, kind: String): Intent {
  val target = ComponentName(context, ShareInDemoActivity::class.java)
  return when (kind) {
    "testo" -> Intent(Intent.ACTION_SEND).setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, "Ricordati di portare il libro di storia e la calcolatrice per giovedì.")
    "link" -> Intent(Intent.ACTION_SEND).setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, "Mappa del museo https://example.org/museo/mappa").putExtra(Intent.EXTRA_SUBJECT, "Mappa del museo")
    "selezione" -> Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT, "La Rivoluzione francese comincia nel 1789 con la convocazione degli Stati generali.")
      .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    "foto" -> {
      val uris = arrayListOf(
        demoPhoto(context, "Fiori.jpg", R.drawable.viewer_flower),
        demoPhoto(context, "Razzo.jpg", R.drawable.viewer_rocket),
        demoPhoto(context, "Astronauta.jpg", R.drawable.viewer_astronaut),
      )
      Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .also { intent ->
          intent.clipData = android.content.ClipData.newRawUri("foto", uris[0]).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        }
    }
    "enorme" -> {
      val uri = demoFile(context, "Video del saggio.mp4") { file ->
        file.outputStream().use { out -> val block = ByteArray(1024 * 1024); repeat(12) { out.write(block) } }
      }
      Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
    }
    "apri" -> Intent(Intent.ACTION_VIEW).setDataAndType(demoFile(context, "Circolare.txt") { it.writeText("Circolare n. 381: uscita didattica di venerdì.") }, "text/plain")
    else -> Intent(Intent.ACTION_SEND).setType("image/jpeg")
      .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://${FluidShareFiles.authority(context)}/fluid_share/manca/nessuno.jpg"))
  }.setComponent(target).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

@Composable
private fun ArrivalsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var entries by remember { mutableStateOf(ArrivalsStore.list(context)) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { entries = ArrivalsStore.list(context) }
  val simulations = listOf(
    "testo" to "Testo", "link" to "Link", "selezione" to "Testo selezionato", "foto" to "Tre foto",
    "enorme" to "File da 12 MB", "apri" to "Apri con", "illeggibile" to "Uri illeggibile",
  )
  ShareScreen("Arrivi da altre app", onBack, onShare = null) {
    item(key = "simula") {
      Column {
        FluidSectionHeader(title = "Simula un arrivo", detail = "Oppure da un'altra app: Condividi → Fluid Glass")
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          simulations.forEach { (id, label) ->
            FluidButton(label, onClick = { context.startActivity(demoArrival(context, id)) }, style = FluidButtonStyle.Tinted,
              modifier = Modifier.testTag(F8p10Tags.simulate(id)))
          }
        }
      }
    }
    item(key = "arrivati") {
      Column(Modifier.testTag(F8p10Tags.Inbox)) {
        FluidSectionHeader(title = "Arrivati", detail = if (entries.isEmpty()) "Ancora niente" else "${entries.size} elementi")
        if (entries.isNotEmpty()) FluidListGroup {
          entries.take(30).forEachIndexed { index, (destination, file) ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = file.name,
              subtitle = "${ArrivalDestinations.firstOrNull { it.id == destination }?.title ?: destination} · ${file.length() / 1024} KB",
              disclosure = false,
            )
          }
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Tetto della demo: 5 MB per file. La destinazione usata diventa una scorciatoia di condivisione: la " +
          "prossima volta compare in alto nel foglio di Android.",
      )
    }
  }
}

/**
 * L'attivita' che riceve le condivisioni (F8.10): trasparente sopra l'app di partenza, col foglio degli arrivi
 * dell'engine. Salva in [ArrivalsStore] e pubblica la destinazione come scorciatoia di condivisione.
 */
class ShareInDemoActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FluidShareInWindow.install(this)
    val shortcut = FluidShareInShortcuts.destinationOf(intent)
    setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val policy = remember { arrivalPolicy(this) }
        val state = rememberFluidShareInState(intent, policy)
        FluidShareInSheet(
          state = state,
          destinations = ArrivalDestinations,
          onSave = { destination, content ->
            withContext(Dispatchers.IO) { ArrivalsStore.save(this@ShareInDemoActivity, destination, content) }
            FluidShareInShortcuts.publish(this@ShareInDemoActivity, destination, ShareInDemoActivity::class.java, ArrivalShareCategory)
          },
          onClose = { finish() },
          title = "Fluid Glass",
          initialDestination = shortcut,
        )
      }
    }
  }

  override fun finish() {
    super.finish()
    if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
  }
}
