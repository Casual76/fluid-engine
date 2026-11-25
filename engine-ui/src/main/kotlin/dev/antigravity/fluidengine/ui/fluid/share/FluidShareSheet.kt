package dev.antigravity.fluidengine.ui.fluid.share

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FileIcon
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSheetPages
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidFileExtension
import dev.antigravity.fluidengine.ui.fluid.fluidFormatFileSize
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.Link
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Print
import dev.antigravity.fluidengine.ui.fluid.icons.QrCode
import dev.antigravity.fluidengine.ui.fluid.icons.Save
import dev.antigravity.fluidengine.ui.fluid.icons.Warning
import dev.antigravity.fluidengine.ui.fluid.output.FluidPrint
import dev.antigravity.fluidengine.ui.fluid.output.fluidPdfDocument
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Un'azione dell'app nel foglio, sopra quelle dell'engine (come «Aggiungi a Nota rapida» sopra «Copia» in iOS).
 * [dismisses] falso lascia il foglio aperto dopo il tocco.
 */
@Immutable
class FluidShareAction(
  val id: String,
  val title: String,
  val icon: ImageVector,
  val dismisses: Boolean = true,
  val onClick: (FluidShareItem) -> Unit,
)

/** Le azioni dell'engine da mostrare; tutte accese di serie. Ognuna si nasconde da sola dove non ha senso. */
@Immutable
data class FluidShareOptions(
  val copy: Boolean = true,
  val saveToFiles: Boolean = true,
  /** «Mostra come QR»: solo per un testo o un link, e solo con un [FluidQrEncoder] nella composizione (`engine-qr`). */
  val qr: Boolean = true,
  /** «Stampa»: per un'immagine, un PDF, un testo o un oggetto con [FluidShareItem.print]. */
  val print: Boolean = true,
  /** La fila delle app fidate in cima. */
  val apps: Boolean = true,
  /**
   * Le azioni dell'engine in lista, come in iOS 17 e 18. Di serie (falso) sono una fila di tasti tondi sotto le app,
   * come in iOS 26, e in lista restano le azioni dell'app e «Altre app…».
   */
  val actionsAsList: Boolean = false,
  /**
   * Il tetto della copia di un file dell'app fuori dalla cartella dell'engine ([FluidShareItem.localFiles]): oltre, il
   * foglio dice quanto pesa e quanto si puo' al posto delle azioni.
   */
  val copyLimitBytes: Long = FluidShareFiles.DefaultCopyLimitBytes,
)

/** Cosa e' successo nel foglio, per l'app che vuole saperlo (un contatore, un'analisi, un «Annulla»). */
sealed class FluidShareOutcome {
  class SentTo(val target: FluidShareTarget) : FluidShareOutcome()
  object Chooser : FluidShareOutcome()
  object Copied : FluidShareOutcome()
  class Saved(val uris: List<Uri>) : FluidShareOutcome()
  object Printed : FluidShareOutcome()
  object QrShown : FluidShareOutcome()
  class Action(val id: String) : FluidShareOutcome()
}

/** Le parole del foglio, di serie in italiano; [english] per l'inglese. */
@Immutable
data class FluidShareLabels(
  val copy: String = "Copia",
  val copied: String = "Copiato",
  val saveToFiles: String = "Salva in File",
  val saved: String = "Salvato",
  val saveFailed: String = "Non si è salvato",
  val showQr: String = "Mostra come QR",
  val print: String = "Stampa",
  val moreApps: String = "Altre app…",
  val more: String = "Altro",
  val close: String = "Chiudi",
  val done: String = "Fine",
  val qrHint: String = "Inquadralo con la fotocamera di un altro telefono.",
  val qrDescription: String = "Codice QR di %s",
  val previewOf: String = "Anteprima di %s",
  val text: String = "Testo",
  val image: String = "Immagine",
  val pdf: String = "Documento PDF",
  val video: String = "Video",
  val audio: String = "Audio",
  val file: String = "File",
  val photos: String = "%d foto",
  val items: String = "%d elementi",
  val chooserTitle: String = "Condividi",
  val options: String = "Opzioni",
  val preparingFile: String = "Preparo il file…",
  val fileMissing: String = "Il file non c'è più: forse è stato spostato o cancellato.",
  val fileUnreadable: String = "Il file non si legge, quindi non si può condividere.",
  /** Il peso e il tetto. */
  val fileTooLarge: String = "Il file è troppo grande da condividere da qui: %1\$s, al massimo %2\$s.",
  /** Il peso da copiare. */
  val fileNoSpace: String = "Non c'è abbastanza spazio libero per preparare il file (%1\$s).",
) {
  companion object {
    fun english(): FluidShareLabels = FluidShareLabels(
      copy = "Copy", copied = "Copied", saveToFiles = "Save to Files", saved = "Saved", saveFailed = "Not saved",
      showQr = "Show as QR Code", print = "Print", moreApps = "More Apps…", more = "More", close = "Close", done = "Done",
      qrHint = "Scan it with another phone's camera.", qrDescription = "QR code for %s", previewOf = "Preview of %s",
      text = "Text", image = "Image", pdf = "PDF Document", video = "Video", audio = "Audio", file = "File",
      photos = "%d photos", items = "%d items", chooserTitle = "Share", options = "Options",
      preparingFile = "Preparing the file…", fileMissing = "The file is no longer there: it may have been moved or deleted.",
      fileUnreadable = "The file can't be read, so it can't be shared.",
      fileTooLarge = "The file is too large to share from here: %1\$s, at most %2\$s.",
      fileNoSpace = "There isn't enough free space to prepare the file (%1\$s).",
    )
  }
}

/** I tag dei test e delle riprese. */
object FluidShareSheetTags {
  const val Sheet = "fluid-share:foglio"
  const val Header = "fluid-share:testata"
  const val Thumbnail = "fluid-share:miniatura"
  const val Close = "fluid-share:chiudi"
  const val Apps = "fluid-share:app"
  const val Copy = "fluid-share:copia"
  const val Save = "fluid-share:salva"
  const val Qr = "fluid-share:qr"
  const val QrCode = "fluid-share:codice"
  /** «Fine» sotto il QR. */
  const val QrDone = "fluid-share:qr-fine"
  const val Print = "fluid-share:stampa"
  const val MoreApps = "fluid-share:altre-app"
  const val Options = "fluid-share:opzioni"
  /** L'attesa della copia di un file dell'app. */
  const val Preparing = "fluid-share:preparo"
  /** Il messaggio di un file che non parte. */
  const val Problem = "fluid-share:problema"

  fun app(packageName: String): String = "fluid-share:app:$packageName"
  fun action(id: String): String = "fluid-share:azione:$id"
}

/**
 * Il foglio di condivisione (4.0, F8.10, `foglio-condividi`): il foglio di vetro di iOS 26 (`ShareLink`,
 * `UIActivityViewController`) con la consegna di Android.
 *
 * - **In cima l'anteprima**: la miniatura (pronta in [FluidShareItem.preview] o preparata dal foglio dal primo Uri di
 *   un'immagine, che entra sfocata quando arriva: la cambia l'app, non il dito), il nome, il tipo e il peso, e la «x».
 * - **La fila delle app Pampa** installate e fidate ([FluidShareTargets], [trust]): le app che ricevono questo tipo e
 *   hanno la firma attesa, le ultime scelte in cima; in fondo «Altro» apre il foglio di Android.
 * - **Le azioni in lista**: quelle dell'app ([actions]), poi Copia, Salva in File, Mostra come QR, Stampa ([options]).
 * - **In fondo «Altre app…»**: il foglio di Android (`Intent.createChooser`, con [chooserActions] da Android 14).
 *
 * Il foglio e' un [FluidGlassSheet] a meta' e in cima, e con [source] nasce dal tasto Condividi e ci rientra (il viaggio
 * dei fogli). La fila delle app scorre col dito ed e' sempre nitida. Ogni file parte col permesso di lettura
 * (`FLAG_GRANT_READ_URI_PERMISSION`); la propria app non compare mai fra le destinazioni.
 *
 * Un file dell'app fuori dalla cartella dell'engine ([FluidShareItem.localFiles], da [FluidShareItem.file] con un `File`)
 * si copia li' quando il foglio si apre ([FluidShareFiles.stage]), fuori dal thread principale: la miniatura e il corpo
 * mostrano l'attesa, e un file che non c'e', non si legge o pesa oltre [FluidShareOptions.copyLimitBytes] lo dice al posto
 * delle azioni, con «Chiudi». Nessun tocco lancia un'eccezione.
 *
 * Con [copyOptions] sotto il nome c'e' «Opzioni ›», che apre la pagina delle opzioni **dentro il foglio**
 * ([FluidSheetPages]): arriva da destra, si torna con indietro in alto, e confermata la copia nuova prende il posto
 * dell'oggetto (anteprima, riga sotto il nome, azioni). Il foglio non si chiude e non si riapre.
 *
 * @param item l'oggetto da condividere; null chiude il foglio.
 * @param copyOptions le opzioni della copia ([FluidShareCopyOptions]); null per nessuna «Opzioni ›».
 * @param onShared cosa e' successo, dopo che e' successo.
 * @param people F10.6 · la fila delle persone in cima, sopra le app («I tuoi dispositivi» per primi, scelta Q1): la
 *   disegna `FluidPeopleRow` di `engine-account-ui` coi dati di Pampa Store. Riceve l'oggetto e il modo di chiudere il
 *   foglio dopo l'invio. Null: niente fila, come prima e come senza account (P1).
 */
@Composable
fun FluidShareSheet(
  item: FluidShareItem?,
  onDismissRequest: () -> Unit,
  source: FluidTransitionSource? = null,
  actions: List<FluidShareAction> = emptyList(),
  options: FluidShareOptions = FluidShareOptions(),
  trust: FluidShareTrust = FluidShareTrust.SameSigner,
  chooserActions: List<FluidShareChooserAction> = emptyList(),
  labels: FluidShareLabels = FluidShareLabels(),
  copyOptions: FluidShareCopyOptions? = null,
  onShared: (FluidShareOutcome) -> Unit = {},
  people: (@Composable (item: FluidShareItem, dismiss: () -> Unit) -> Unit)? = null,
) {
  // Letto qui, dove il foglio e' dichiarato: il corpo lo disegna l'host dei modali, alla radice, che non vede i
  // CompositionLocal della pagina (un ProvideFluidQr intorno alla sola pagina deve bastare).
  val encoder = LocalFluidQrEncoder.current
  FluidGlassSheet(
    item = item,
    onDismissRequest = onDismissRequest,
    detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    paneTitle = labels.chooserTitle,
    source = source,
  ) { shown ->
    // L'oggetto del foglio: quello dell'app, o la copia confermata nelle opzioni finche' l'app non ne passa un altro.
    var current by remember(shown) { mutableStateOf(shown) }
    var choice by remember(shown) { mutableStateOf<FluidCopyChoice?>(null) }
    var prepared by remember(shown) { mutableStateOf<FluidPreparedCopy?>(null) }
    var pages by remember(shown) { mutableStateOf(listOf(SharePage.Share)) }
    // Un file dell'app fuori dalla cartella dell'engine si copia li' adesso, fuori dal thread principale e col tetto
    // delle opzioni; l'esito vale solo per l'oggetto da cui e' partito.
    val context = LocalContext.current
    val staging by produceState<Pair<FluidShareItem, FluidShareStaged>?>(null, current, options.copyLimitBytes) {
      if (current.needsCopy) value = current to FluidShareFiles.stage(context, current, options.copyLimitBytes)
    }
    val result = staging?.takeIf { it.first === current }?.second
    val ready = if (!current.needsCopy) current else (result as? FluidShareStaged.Ready)?.item
    FluidSheetPages(
      pages = pages,
      onBack = { pages = pages.dropLast(1).ifEmpty { listOf(SharePage.Share) } },
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) { page ->
      when (page) {
        SharePage.Share -> Column(Modifier.fillMaxSize()) {
          ShareSheetBody(
            ready ?: current, onDismissRequest, actions, options, trust, chooserActions, labels, onShared, encoder,
            header = current,
            onOptions = if (copyOptions != null) ({ pages = listOf(SharePage.Share, SharePage.Options) }) else null,
            copying = ready == null && result == null,
            failure = result as? FluidShareStaged.Failed,
            people = people,
          )
        }
        SharePage.Options -> if (copyOptions != null) FluidShareOptionsPage(
          copyOptions = copyOptions,
          start = choice,
          startCopy = prepared,
          onBack = { pages = listOf(SharePage.Share) },
          onConfirm = { confirmed, copy ->
            choice = confirmed
            prepared = copy
            current = copy.item
            pages = listOf(SharePage.Share)
          },
        )
      }
    }
  }
}

/** Le pagine del foglio: la condivisione e, sopra, le opzioni della copia. */
private enum class SharePage { Share, Options }

/** Cosa mostra il corpo: le azioni, il QR al loro posto, l'attesa della copia o il perche' non parte. */
private enum class SharePane { Actions, Qr, Preparing, Problem }

@Composable
private fun ColumnScope.ShareSheetBody(
  item: FluidShareItem,
  onDismissRequest: () -> Unit,
  actions: List<FluidShareAction>,
  options: FluidShareOptions,
  trust: FluidShareTrust,
  chooserActions: List<FluidShareChooserAction>,
  labels: FluidShareLabels,
  onShared: (FluidShareOutcome) -> Unit,
  encoder: FluidQrEncoder?,
  onOptions: (() -> Unit)?,
  copying: Boolean = false,
  failure: FluidShareStaged.Failed? = null,
  header: FluidShareItem = item,
  people: (@Composable (item: FluidShareItem, dismiss: () -> Unit) -> Unit)? = null,
) {
  val context = LocalContext.current
  val haptics = rememberFluidHaptics()
  val scope = rememberCoroutineScope()
  val dismiss by rememberUpdatedState(onDismissRequest)
  val report by rememberUpdatedState(onShared)
  var pane by remember(item) { mutableStateOf(SharePane.Actions) }
  var copied by remember(item) { mutableStateOf(false) }
  var saved by remember(item) { mutableStateOf<Boolean?>(null) }
  val targets by rememberFluidShareTargets(if (options.apps && !copying && failure == null) item else null, trust)
  LaunchedEffect(failure) { if (failure != null) haptics.play(FluidHapticEvent.Warning) }

  fun finish(outcome: FluidShareOutcome, after: Long = 0L) {
    report(outcome)
    if (after <= 0L) dismiss() else scope.launch { delay(after); dismiss() }
  }

  val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(fluidSaveMime(item))) { uri ->
    if (uri != null) scope.launch {
      val ok = withContext(Dispatchers.IO) { runCatching { fluidSaveInto(context.contentResolver, item, uri) }.isSuccess }
      saved = ok
      haptics.play(if (ok) FluidHapticEvent.Success else FluidHapticEvent.Error)
      if (ok) finish(FluidShareOutcome.Saved(listOf(uri)), FluidShareDoneMillis)
    }
  }
  val saveMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
    if (tree != null) scope.launch {
      val written = withContext(Dispatchers.IO) { runCatching { fluidSaveAllInto(context, item, tree) }.getOrDefault(emptyList()) }
      saved = written.size == item.uris.size
      haptics.play(if (saved == true) FluidHapticEvent.Success else FluidHapticEvent.Error)
      if (saved == true) finish(FluidShareOutcome.Saved(written), FluidShareDoneMillis)
    }
  }

  val qrText = item.text?.takeIf { !item.hasFiles && it.length <= FluidShareQrMaxChars }
  val qr = remember(qrText, encoder) { if (qrText != null && encoder != null && options.qr) encoder.encode(qrText) else null }

  Column(Modifier.testTag(FluidShareSheetTags.Sheet).fillMaxWidth().weight(1f, fill = false)) {
    // La testata legge l'oggetto com'era prima della copia: la miniatura viene dal file al suo posto e non si rilegge
    // dall'Uri della copia quando la copia e' pronta (e' la stessa immagine).
    ShareHeader(header, labels, onClose = onDismissRequest, onOptions = onOptions, busy = copying)
    Spacer(Modifier.height(14.dp))
    // Il corpo cambia identita' (azioni o QR) per un tocco che chiede un'altra cosa: entra sfocato, come le ruote del
    // calendario (F8.8); la fila delle app dentro scorre col dito ed e' nitida.
    // L'attesa della copia e il perche' non parte sono cambi dell'app: entrano sfocati anche loro.
    val shownPane = when {
      failure != null -> SharePane.Problem
      copying -> SharePane.Preparing
      else -> pane
    }
    FluidGlassContentTransition(
      targetState = shownPane,
      modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
      blurReference = 40.dp,
      contentAlignment = Alignment.TopCenter,
    ) { current ->
      when (current) {
        SharePane.Preparing -> SharePreparing(labels)
        SharePane.Problem -> if (failure != null) ShareProblem(failure, labels, onClose = onDismissRequest)
        SharePane.Qr -> if (qr != null) ShareQrPanel(qr, qrText.orEmpty(), labels) { finish(FluidShareOutcome.QrShown) }
        SharePane.Actions -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
          if (people != null) {
            people(item) { dismiss() }
            Spacer(Modifier.height(12.dp))
          }
          val apps = targets
          if (options.apps && !apps.isNullOrEmpty()) {
            ShareAppsRow(
              targets = apps,
              labels = labels,
              onPick = { target ->
                val sent = runCatching { context.startActivity(FluidShareIntents.sendTo(item, target)) }.isSuccess
                if (sent) {
                  FluidShareTargets.remember(context, target)
                  finish(FluidShareOutcome.SentTo(target))
                }
              },
              onMore = { openChooser(context, item, labels, chooserActions) { finish(FluidShareOutcome.Chooser) } },
            )
            Spacer(Modifier.height(16.dp))
          }
          // Le azioni dell'engine: in iOS 26 sono una fila di tasti tondi sotto le app (Copia, Aggiungi all'album…), e
          // solo le altre stanno in lista; con `actionsAsList` tornano tutte in lista, come in iOS 17 e 18.
          val commands = buildList {
            if (options.copy && (item.hasFiles || item.text != null)) add(
              ShareCommand(FluidShareSheetTags.Copy, if (copied) labels.copied else labels.copy,
                if (copied) FluidIcons.Check else FluidIcons.ContentCopy, changed = copied) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(FluidShareIntents.clip(context, item))
                copied = true
                haptics.play(FluidHapticEvent.Success)
                finish(FluidShareOutcome.Copied, FluidShareDoneMillis)
              },
            )
            if (options.saveToFiles && (item.hasFiles || item.text != null)) add(
              ShareCommand(
                FluidShareSheetTags.Save,
                when (saved) { true -> labels.saved; false -> labels.saveFailed; null -> labels.saveToFiles },
                when (saved) { true -> FluidIcons.Check; false -> FluidIcons.Warning; null -> FluidIcons.Save },
                changed = saved != null,
              ) { runCatching { if (item.uris.size > 1) saveMany.launch(null) else saveOne.launch(fluidSaveName(item)) } },
            )
            if (qr != null) add(ShareCommand(FluidShareSheetTags.Qr, labels.showQr, FluidIcons.QrCode) {
              haptics.play(FluidHapticEvent.Open)
              pane = SharePane.Qr
            })
            if (options.print && fluidCanPrint(item)) add(ShareCommand(FluidShareSheetTags.Print, labels.print, FluidIcons.Print) {
              if (fluidPrintItem(context, item)) finish(FluidShareOutcome.Printed)
            })
          }
          val appCommands = actions.map { action ->
            ShareCommand(FluidShareSheetTags.action(action.id), action.title, action.icon) {
              action.onClick(item)
              if (action.dismisses) finish(FluidShareOutcome.Action(action.id)) else report(FluidShareOutcome.Action(action.id))
            }
          }
          val more = ShareCommand(FluidShareSheetTags.MoreApps, labels.moreApps, FluidIcons.MoreHoriz) {
            openChooser(context, item, labels, chooserActions) { finish(FluidShareOutcome.Chooser) }
          }
          if (options.actionsAsList) {
            ShareCommandList(appCommands + commands)
            Spacer(Modifier.height(12.dp))
            ShareCommandList(listOf(more))
          } else {
            if (commands.isNotEmpty()) {
              FluidListDivider(Modifier.padding(horizontal = 16.dp))
              Spacer(Modifier.height(12.dp))
              Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
              ) { commands.forEach { ShareRoundAction(it) } }
              Spacer(Modifier.height(14.dp))
            }
            ShareCommandList(appCommands + more)
          }
          Spacer(Modifier.height(24.dp))
        }
      }
    }
  }
}

private fun openChooser(
  context: Context,
  item: FluidShareItem,
  labels: FluidShareLabels,
  chooserActions: List<FluidShareChooserAction>,
  then: () -> Unit,
) {
  try {
    context.startActivity(FluidShareIntents.chooser(context, item, labels.chooserTitle, chooserActions))
    then()
  } catch (_: ActivityNotFoundException) {
    // Nessun foglio di sistema (un dispositivo senza): il foglio resta aperto.
  }
}

/** Quanto resta visibile la spunta di «Copiato» o «Salvato» prima che il foglio se ne vada. A occhio, 24 novembre 2026. */
internal const val FluidShareDoneMillis: Long = 450L

/** Un QR oltre questa lunghezza e' troppo fitto per una fotocamera a un braccio di distanza. */
internal const val FluidShareQrMaxChars: Int = 1200

@Composable
private fun ShareHeader(
  item: FluidShareItem,
  labels: FluidShareLabels,
  onClose: () -> Unit,
  onOptions: (() -> Unit)?,
  busy: Boolean = false,
) {
  val locale = LocalConfiguration.current.locales[0]
  Row(
    Modifier.testTag(FluidShareSheetTags.Header).fillMaxWidth().padding(start = 16.dp, end = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ShareThumbnail(item, labels, busy)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        item.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      // La riga cambia quando torna la copia nuova dalle opzioni: la cambia l'app, entra sfocata.
      FluidGlassContentTransition(
        targetState = fluidShareSubtitle(item, labels, locale),
        blurReference = 16.dp,
        contentAlignment = Alignment.CenterStart,
      ) { subtitle ->
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      // «Opzioni ›» sotto il nome, come nel foglio di Foto di iOS 26: apre le opzioni della copia dentro il foglio.
      if (onOptions != null) {
        Text(
          "${labels.options} ›",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag(FluidShareSheetTags.Options)
            .fluidPressable(onClick = onOptions, role = Role.Button).heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically),
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    FluidGlassIconButton(
      onClick = onClose,
      size = 36.dp,
      modifier = Modifier.testTag(FluidShareSheetTags.Close).semantics { contentDescription = labels.close },
      haptic = FluidHapticEvent.Close,
    ) {
      FluidIcon(FluidIcons.Close, contentDescription = null, size = 18.dp)
    }
  }
}

@Composable
private fun ShareThumbnail(item: FluidShareItem, labels: FluidShareLabels, busy: Boolean = false) {
  val context = LocalContext.current
  val px = with(LocalDensity.current) { (FluidShareThumbnailSize * 2).roundToPx() }
  // Un file dell'app ancora da copiare si legge dal suo posto: la miniatura c'e' gia' mentre la copia lavora.
  val first = item.uris.firstOrNull() ?: item.localFiles.firstOrNull()?.let { Uri.fromFile(it) }
  // L'immagine di prima resta finche' non arriva la nuova: la copia confermata nelle opzioni e' la stessa foto, e la
  // miniatura non deve passare dall'icona del tipo.
  var image by remember { mutableStateOf(item.preview) }
  LaunchedEffect(item.preview, first, item.mimeType) {
    image = item.preview ?: if (first != null && item.mimeType.startsWith("image/")) {
      withContext(Dispatchers.IO) { runCatching { fluidLoadThumbnail(context.contentResolver, first, px) }.getOrNull() }
    } else {
      null
    }
  }
  val shape = ContinuousCornerShape(FluidRadius.Control)
  Box(
    Modifier
      .testTag(FluidShareSheetTags.Thumbnail)
      .size(FluidShareThumbnailSize)
      .clip(shape)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
      .clearAndSetSemantics { contentDescription = labels.previewOf.format(item.title) },
    contentAlignment = Alignment.Center,
  ) {
    // La miniatura che arriva e' un cambio dell'app: entra sfocata al posto dell'icona del tipo.
    FluidGlassContentTransition(
      targetState = image,
      key = { if (it == null) "tipo" else "anteprima" },
      blurReference = FluidShareThumbnailSize,
    ) { bitmap ->
      if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(FluidShareThumbnailSize))
      } else when {
        item.isLink -> FluidIcon(FluidIcons.Link, contentDescription = null, size = 26.dp, tint = MaterialTheme.colorScheme.primary)
        !item.hasFiles -> FluidIcon(FluidIcons.IosShare, contentDescription = null, size = 26.dp, tint = MaterialTheme.colorScheme.primary)
        else -> FileIcon(FluidFileKind.from(item.mimeType, item.saveName), 28)
      }
    }
    // L'attesa della copia sopra la miniatura, dopo un attimo: un file piccolo si copia prima e non lampeggia niente.
    if (busy) {
      val visible = rememberFluidShareGrace()
      Box(
        Modifier.matchParentSize().alpha(if (visible) 1f else 0f).background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
      ) { FluidSpinner(size = 22.dp, color = Color.White) }
    }
  }
}

/** La miniatura del foglio: 56 dp, la misura dell'anteprima del foglio di iOS 26 (a occhio dalle schermate, 24 novembre 2026). */
internal val FluidShareThumbnailSize = 56.dp

/** L'icona di un'app nella fila: 60 dp, quanto le icone del foglio di iOS. */
internal val FluidShareAppIconSize = 60.dp

@Composable
private fun ShareAppsRow(
  targets: List<FluidShareTarget>,
  labels: FluidShareLabels,
  onPick: (FluidShareTarget) -> Unit,
  onMore: () -> Unit,
) {
  Row(
    Modifier.testTag(FluidShareSheetTags.Apps).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    targets.forEach { target ->
      ShareAppBubble(target.label, Modifier.testTag(FluidShareSheetTags.app(target.packageName)), onClick = { onPick(target) }) {
        val icon = target.icon
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(FluidShareAppIconSize))
        else FluidIcon(FluidIcons.IosShare, contentDescription = null, size = 28.dp)
      }
    }
    ShareAppBubble(labels.more, Modifier, onClick = onMore) {
      Box(
        Modifier.size(FluidShareAppIconSize).clip(FluidCapsuleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
      ) { FluidIcon(FluidIcons.MoreHoriz, contentDescription = null, size = 28.dp) }
    }
  }
}

@Composable
private fun ShareAppBubble(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
  Column(
    modifier
      .width(80.dp)
      .fluidPressable(onClick = onClick, role = Role.Button)
      .semantics(mergeDescendants = true) { contentDescription = label }
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(FluidShareAppIconSize), contentAlignment = Alignment.Center) { icon() }
    Spacer(Modifier.height(6.dp))
    Text(
      label,
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 76.dp),
    )
  }
}

/** Un'azione del foglio, che si disegna come tasto tondo o come riga. */
private class ShareCommand(
  val tag: String,
  val title: String,
  val icon: ImageVector,
  val changed: Boolean = false,
  val onClick: () -> Unit,
)

/** Le azioni in un gruppo di righe. */
@Composable
private fun ShareCommandList(commands: List<ShareCommand>) {
  if (commands.isEmpty()) return
  FluidListGroup(Modifier.padding(horizontal = 16.dp)) {
    commands.forEachIndexed { index, command ->
      if (index > 0) FluidListDivider()
      ShareActionRow(command.title, command.icon, Modifier.testTag(command.tag), command.changed, command.onClick)
    }
  }
}

/**
 * Un'azione come tasto tondo, come la fila di iOS 26 (Copia, Aggiungi all'album): il disco da 60 dp col simbolo e il
 * nome sotto, su due righe al piu'. Il cambio dopo il tocco («Copiato», la spunta) lo fa l'app: entra sfocato.
 */
@Composable
private fun ShareRoundAction(command: ShareCommand) {
  Column(
    Modifier
      .testTag(command.tag)
      .width(84.dp)
      .fluidPressable(onClick = command.onClick, role = Role.Button)
      .semantics(mergeDescendants = true) { role = Role.Button }
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier.size(FluidShareActionDiscSize).clip(FluidCapsuleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
      contentAlignment = Alignment.Center,
    ) {
      FluidGlassContentTransition(targetState = command.icon, key = { if (command.changed) "fatto" else it }, blurReference = 26.dp) { glyph ->
        FluidIcon(glyph, contentDescription = null, size = 26.dp)
      }
    }
    Spacer(Modifier.height(6.dp))
    FluidGlassContentTransition(targetState = command.title, blurReference = 16.dp) { text ->
      Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 80.dp),
      )
    }
  }
}

/** Il disco di un'azione nella fila: 60 dp, quanto le icone delle app sopra (a occhio dalla guida di iOS 26). */
internal val FluidShareActionDiscSize = 60.dp

/** Una riga d'azione come nel foglio di iOS: il nome a sinistra, il simbolo a destra. 52 dp, sopra i 44 delle regole. */
@Composable
private fun ShareActionRow(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  changed: Boolean = false,
  onClick: () -> Unit,
) {
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .fluidPressable(onClick = onClick, role = Role.Button)
      .semantics(mergeDescendants = true) { role = Role.Button }
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Il cambio dopo il tocco («Copiato», la spunta) lo fa l'app: entra sfocato.
    FluidGlassContentTransition(
      targetState = title,
      modifier = Modifier.weight(1f),
      blurReference = 22.dp,
      contentAlignment = Alignment.CenterStart,
    ) { text -> Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth()) }
    FluidGlassContentTransition(targetState = icon, key = { if (changed) "fatto" else it }, blurReference = 22.dp) { glyph ->
      FluidIcon(glyph, contentDescription = null, size = 22.dp)
    }
  }
}

@Composable
private fun ShareQrPanel(matrix: FluidQrMatrix, text: String, labels: FluidShareLabels, onDone: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Il codice prende lo spazio che resta (al massimo 280 dp) e resta quadrato: a meta' altezza si stringe, e il
    // testo e «Fine» sotto restano sempre dentro il foglio, a ogni detent.
    FluidQrCode(
      matrix = matrix,
      contentDescription = labels.qrDescription.format(text.take(80)),
      modifier = Modifier.testTag(FluidShareSheetTags.QrCode)
        .weight(1f, fill = false)
        .widthIn(max = 280.dp)
        .aspectRatio(1f, matchHeightConstraintsFirst = true)
        .clip(ContinuousCornerShape(FluidRadius.Card)),
    )
    Spacer(Modifier.height(14.dp))
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
    Text(
      labels.qrHint,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(18.dp))
    FluidButton(labels.done, onDone, fillWidth = true, size = FluidButtonSize.Large, modifier = Modifier.testTag(FluidShareSheetTags.QrDone))
    Spacer(Modifier.height(16.dp))
  }
}

/** Quanto aspetta l'attesa della copia prima di farsi vedere: 150 ms. A occhio (24 novembre 2026): una foto si copia prima. */
internal const val FluidShareStageGraceMillis: Long = 150L

/** Vero dopo [FluidShareStageGraceMillis] dalla prima composizione. */
@Composable
private fun rememberFluidShareGrace(): Boolean {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(FluidShareStageGraceMillis)
    visible = true
  }
  return visible
}

/** L'attesa della copia di un file dell'app, al posto delle azioni. */
@Composable
private fun SharePreparing(labels: FluidShareLabels) {
  val visible = rememberFluidShareGrace()
  Row(
    Modifier
      .testTag(FluidShareSheetTags.Preparing)
      .fillMaxWidth()
      .heightIn(min = 96.dp)
      .padding(horizontal = 24.dp)
      .alpha(if (visible) 1f else 0f)
      .semantics { liveRegion = LiveRegionMode.Polite },
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidSpinner()
    Spacer(Modifier.width(10.dp))
    Text(labels.preparingFile, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Perche' il file non parte, con parole sue, al posto delle azioni; «Chiudi» chiude il foglio. */
@Composable
private fun ShareProblem(failure: FluidShareStaged.Failed, labels: FluidShareLabels, onClose: () -> Unit) {
  val locale = LocalConfiguration.current.locales[0]
  Column(
    Modifier
      .testTag(FluidShareSheetTags.Problem)
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
      .semantics { liveRegion = LiveRegionMode.Polite },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    FluidIcon(FluidIcons.Warning, contentDescription = null, size = 30.dp, tint = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(10.dp))
    Text(fluidShareProblemText(failure, labels, locale), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(18.dp))
    FluidButton(labels.close, onClose, fillWidth = true, size = FluidButtonSize.Large)
    Spacer(Modifier.height(16.dp))
  }
}

/** Il messaggio di un file che non parte: il motivo, e il peso e il tetto quando contano. */
internal fun fluidShareProblemText(failure: FluidShareStaged.Failed, labels: FluidShareLabels, locale: Locale): String =
  when (failure.problem) {
    FluidShareFileProblem.Missing -> labels.fileMissing
    FluidShareFileProblem.Unreadable -> labels.fileUnreadable
    FluidShareFileProblem.TooLarge -> labels.fileTooLarge.format(
      fluidFormatFileSize(failure.sizeBytes ?: 0L, locale),
      fluidFormatFileSize(failure.limitBytes, locale),
    )
    FluidShareFileProblem.NoSpace -> labels.fileNoSpace.format(fluidFormatFileSize(failure.sizeBytes ?: 0L, locale))
  }

/** La riga sotto il nome: «Immagine PNG · 245 KB», «Testo», «example.org», «3 foto · 4,2 MB». */
internal fun fluidShareSubtitle(item: FluidShareItem, labels: FluidShareLabels, locale: Locale): String {
  val size = item.sizeBytes?.let { fluidFormatFileSize(it, locale) }
  val kind = when {
    item.isLink -> fluidLinkHost(item.text.orEmpty()) ?: item.text.orEmpty()
    !item.hasFiles -> labels.text
    item.fileCount > 1 -> (if (item.mimeType.startsWith("image/")) labels.photos else labels.items).format(item.fileCount)
    else -> {
      val ext = fluidFileExtension(item.saveName).removePrefix(".").uppercase(Locale.ROOT)
      when (FluidFileKind.from(item.mimeType, item.saveName)) {
        FluidFileKind.Image -> if (ext.isNotEmpty()) "${labels.image} $ext" else labels.image
        FluidFileKind.Pdf -> labels.pdf
        FluidFileKind.Video -> labels.video
        FluidFileKind.Audio -> labels.audio
        else -> if (ext.isNotEmpty()) "${labels.file} $ext" else labels.file
      }
    }
  }
  val base = if (size != null && item.hasFiles) "$kind · $size" else kind
  return item.detail?.let { "$base · $it" } ?: base
}

/** Il tipo di «Salva in File» per un oggetto solo. */
internal fun fluidSaveMime(item: FluidShareItem): String = if (item.hasFiles) item.mimeType.replace("*", "octet-stream").let {
  if (it.endsWith("/octet-stream") && !it.startsWith("application")) "application/octet-stream" else it
} else "text/plain"

/** Vero se «Stampa» sa stampare [item]. */
internal fun fluidCanPrint(item: FluidShareItem): Boolean = when {
  item.print != null -> true
  item.uris.size == 1 && item.mimeType.startsWith("image/") -> true
  item.uris.size == 1 && item.mimeType == "application/pdf" -> true
  !item.hasFiles && item.text != null -> true
  else -> false
}

/** Apre l'anteprima di stampa di Android per [item]; falso se non c'e' niente da stampare. */
internal fun fluidPrintItem(context: Context, item: FluidShareItem): Boolean {
  val document = item.print
  return when {
    document != null && !document.needsCompose -> FluidPrint.print(context, document, item.title) != null
    item.uris.size == 1 && item.mimeType.startsWith("image/") -> FluidPrint.printImage(context, item.uris.first(), item.title) != null
    item.uris.size == 1 && item.mimeType == "application/pdf" -> FluidPrint.printPdf(context, item.uris.first(), item.title) != null
    !item.hasFiles && item.text != null -> {
      val text = item.text
      FluidPrint.print(context, fluidPdfDocument(item.title) { paragraph(text) }, item.title) != null
    }
    else -> false
  }
}

/** Una miniatura di lato almeno [px] da un'immagine, letta a campioni (mai l'immagine intera in memoria): [FluidImageSample]. */
internal fun fluidLoadThumbnail(resolver: ContentResolver, uri: Uri, px: Int): ImageBitmap? =
  FluidImageSample.atLeast(resolver, uri, px)?.asImageBitmap()

/** «Salva in File» di un oggetto solo: copia i byte (o il testo) nel documento scelto. */
internal fun fluidSaveInto(resolver: ContentResolver, item: FluidShareItem, target: Uri) {
  resolver.openOutputStream(target, "wt").use { out ->
    requireNotNull(out) { "Il documento scelto non si apre in scrittura" }
    val source = item.uris.firstOrNull()
    if (source != null) {
      requireNotNull(resolver.openInputStream(source)) { "Il file da salvare non si legge" }.use { it.copyTo(out) }
    } else {
      out.write(item.text.orEmpty().toByteArray(Charsets.UTF_8))
    }
  }
}

/** «Salva in File» di piu' file: un documento per file nella cartella scelta. Ritorna quelli scritti. */
internal fun fluidSaveAllInto(context: Context, item: FluidShareItem, tree: Uri): List<Uri> {
  val resolver = context.contentResolver
  val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
  return item.uris.mapIndexedNotNull { index, uri ->
    val name = fluidDisplayName(resolver, uri) ?: "${fluidSafeFileName(item.title)} ${index + 1}"
    val mime = resolver.getType(uri) ?: item.mimeType
    runCatching {
      val created = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return@runCatching null
      resolver.openOutputStream(created, "wt")!!.use { out -> resolver.openInputStream(uri)!!.use { it.copyTo(out) } }
      created
    }.getOrNull()
  }
}

/** Il nome che il provider da' a un Uri (`OpenableColumns.DISPLAY_NAME`). */
internal fun fluidDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
  resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
    if (c.moveToFirst()) c.getString(0) else null
  }
}.getOrNull()
