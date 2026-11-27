package dev.antigravity.fluidengine.feedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.diagnostics.FluidDiagnostics
import dev.antigravity.fluidengine.foundation.lab.FluidLab
import dev.antigravity.fluidengine.ink.FluidInkEditor
import dev.antigravity.fluidengine.ink.FluidInkExport
import dev.antigravity.fluidengine.ink.FluidInkSheet
import dev.antigravity.fluidengine.ink.FluidInkSheetLoad
import dev.antigravity.fluidengine.ink.FluidInkTool
import dev.antigravity.fluidengine.ink.rememberFluidInkState
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Forum
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.FolderZip
import dev.antigravity.fluidengine.ui.fluid.icons.Public
import dev.antigravity.fluidengine.ui.fluid.icons.Screenshot
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareAction
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareIntents
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheet
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// F8.24 · «Segnala un problema», l'interfaccia. Non disturba: si apre solo quando l'utente lo chiede (la riga di
// «Informazioni», il Laboratorio), mai dopo uno screenshot (nota di Alessio). Il giro, come il feedback di TestFlight:
//  1. il foglio di vetro con «Cosa non va?», lo screenshot e cosa parte, tutto in vista;
//  2. «Cattura una pagina»: il foglio esce e resta una capsula di vetro in basso («Vai alla pagina col problema, poi
//     tocca qui»); l'utente va dove vuole e tocca «Cattura», la capsula sparisce in quel fotogramma e la finestra si
//     copia (PixelCopy). Oppure «Scegli dalle foto», col selettore di Android;
//  3. lo screenshot si segna col dito, col Markup dell'engine (engine-ink); torna nel foglio, ed entra sfocato perché
//     l'ha cambiato l'app;
//  4. due strade: lo ZIP col foglio Condividi (WhatsApp in cima; scelta di Alessio, F8.24b), o la issue su GitHub nel
//     browser, già scritta.
// La capsula è vetro dell'engine, e la sua sorgente si registra solo mentre c'è (niente sorgenti a schermo intero sempre
// accese, F7). I campi e gli interruttori li muove il dito e restano nitidi.

/** I tag dei test di «Segnala un problema». */
object FluidFeedbackTags {
  const val Sheet = "fluid-feedback:foglio"
  const val Row = "fluid-feedback:riga"
  const val Description = "fluid-feedback:frase"
  const val Capture = "fluid-feedback:cattura"
  const val Pick = "fluid-feedback:foto"
  const val Thumbnail = "fluid-feedback:miniatura"
  const val Mark = "fluid-feedback:segna"
  const val Remove = "fluid-feedback:togli"
  const val Pill = "fluid-feedback:capsula"
  const val PillCapture = "fluid-feedback:capsula:cattura"
  const val PillCancel = "fluid-feedback:capsula:annulla"
  const val Markup = "fluid-feedback:segnatura"
  const val SendFile = "fluid-feedback:file"
  const val OpenIssue = "fluid-feedback:issue"
  const val Preview = "fluid-feedback:anteprima"
  const val PreviewToggle = "fluid-feedback:mostra"
  const val Credit = "fluid-feedback:crediti"

  /** L'interruttore di una parte («screenshot», «telefono», «prove», «arresti», «eventi»). */
  fun include(part: String): String = "fluid-feedback:parte:$part"
}

/** Dove sta il flusso. */
internal sealed interface FeedbackPhase {
  data object Closed : FeedbackPhase
  data object Composing : FeedbackPhase
  data object Capturing : FeedbackPhase
  data object Shooting : FeedbackPhase
  class Marking(val image: ImageBitmap, val serial: Int) : FeedbackPhase
}

/**
 * Lo stato di «Segnala un problema» di un'app (F8.24): la frase, lo screenshot e cosa allegare restano anche se il
 * foglio si chiude, finché la segnalazione non parte. Si ottiene con [rememberFluidFeedbackState] e si passa a
 * [FluidFeedbackHost]; [open] apre il foglio da qualunque punto (la riga [FluidFeedbackRow], il Laboratorio).
 *
 * Tutti i cambi avvengono sul thread principale; il lavoro (leggere la coda, scrivere i file) va in sottofondo.
 */
@Stable
class FluidFeedbackState internal constructor(
  val config: FluidFeedbackConfig,
  internal val lab: FluidLab?,
  internal val diagnostics: FluidDiagnostics?,
) {
  internal var phase by mutableStateOf<FeedbackPhase>(FeedbackPhase.Closed)

  /** Quello che l'utente sta scrivendo. */
  var draft by mutableStateOf(FluidFeedbackDraft())
    internal set

  /** Le informazioni raccolte all'apertura del foglio, o nulla finché non sono pronte. */
  var info by mutableStateOf<FluidFeedbackInfo?>(null)
    internal set

  internal var shareItem by mutableStateOf<FluidShareItem?>(null)
  internal var busy by mutableStateOf(false)
  internal var message by mutableStateOf<String?>(null)
  private var serial = 0

  /** Vero se il foglio, la capsula o la segnatura sono aperti. */
  val isOpen: Boolean get() = phase != FeedbackPhase.Closed || shareItem != null

  /** Apre il foglio (dalla riga di «Informazioni», dal Laboratorio, da un tasto dell'app). */
  fun open() {
    message = null
    phase = FeedbackPhase.Composing
  }

  /** Chiude tutto, tenendo la bozza per la prossima volta. */
  fun close() {
    phase = FeedbackPhase.Closed
  }

  /** Cambia la bozza (la frase, cosa allegare). */
  fun update(transform: (FluidFeedbackDraft) -> FluidFeedbackDraft) {
    draft = transform(draft)
  }

  /**
   * Dimentica la bozza: dopo l'invio, o quando l'utente la butta. R3 · Con lei se ne va lo screenshot di lavoro, se è
   * della cartella di lavoro ([FluidFeedbackFile.workDir]; un file dell'app messo nella bozza da lei resta): lo ZIP e
   * la copia in Download, se c'erano, sono già fatti.
   */
  fun discard() {
    draft.screenshot?.let(FluidFeedbackFile::deleteWorkFile)
    draft = FluidFeedbackDraft()
    info = null
  }

  internal fun startCapture() {
    phase = FeedbackPhase.Capturing
  }

  internal fun mark(image: ImageBitmap) {
    phase = FeedbackPhase.Marking(image, ++serial)
  }
}

/** La segnalazione della pagina, se l'app ha un [FluidFeedbackHost] sopra: `LocalFluidFeedback.current?.open()`. */
val LocalFluidFeedback = staticCompositionLocalOf<FluidFeedbackState?> { null }

/**
 * Lo stato di «Segnala un problema» ([FluidFeedbackState]). [lab] aggiunge le funzioni in prova accese; [diagnostics]
 * gli ultimi arresti della coda (di serie quella installata nel processo).
 */
@Composable
fun rememberFluidFeedbackState(
  config: FluidFeedbackConfig,
  lab: FluidLab? = null,
  diagnostics: FluidDiagnostics? = FluidDiagnostics.get(),
): FluidFeedbackState = remember(config, lab, diagnostics) { FluidFeedbackState(config, lab, diagnostics) }

/**
 * L'ospite di «Segnala un problema» (F8.24): si mette **una volta**, attorno al contenuto dell'app (dentro `FluidTheme`,
 * attorno al `FluidNavHost`), perché la capsula della cattura deve restare sopra mentre l'utente cambia pagina. Porta
 * il foglio, la capsula, la segnatura dello screenshot e il foglio Condividi; fornisce [LocalFluidFeedback].
 */
@Composable
fun FluidFeedbackHost(
  state: FluidFeedbackState,
  modifier: Modifier = Modifier,
  labels: FluidFeedbackLabels = FluidFeedbackLabels(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val backdrop = rememberGlassBackdrop()
  val phase = state.phase
  val capturing = phase == FeedbackPhase.Capturing || phase == FeedbackPhase.Shooting
  CompositionLocalProvider(LocalFluidFeedback provides state) {
    Box(modifier.fillMaxSize()) {
      // La sorgente del vetro della capsula si registra solo mentre la capsula c'è.
      Box(Modifier.fillMaxSize().then(if (capturing) Modifier.glassBackdropSource(backdrop) else Modifier)) { content() }
      FluidFloatingCapsule(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 16.dp)
          // Nel fotogramma della copia la capsula non si disegna: lo screenshot è la pagina sola.
          .graphicsLayer { alpha = if (state.phase == FeedbackPhase.Shooting) 0f else 1f }
          .testTag(FluidFeedbackTags.Pill),
        backdrop = backdrop,
        visible = capturing,
        label = labels.captureHint,
        leading = {
          FluidCapsuleAction(
            icon = FluidIcons.Close,
            contentDescription = labels.captureCancel,
            onClick = { state.open() },
            modifier = Modifier.testTag(FluidFeedbackTags.PillCancel),
          )
        },
        trailing = {
          FluidGlassButton(
            text = labels.captureAction,
            onClick = {
              if (state.phase != FeedbackPhase.Capturing) return@FluidGlassButton
              state.phase = FeedbackPhase.Shooting
              scope.launch {
                // Due fotogrammi: il primo disegna la pagina senza la capsula, il secondo è già sullo schermo.
                withFrameNanos { }
                withFrameNanos { }
                val activity = context.findActivity()
                val shot = runCatching { activity?.let { FluidFeedbackCapture.window(it) } }.getOrNull()
                if (shot != null) state.mark(shot.asImageBitmap()) else state.open()
              }
            },
            backdrop = backdrop,
            modifier = Modifier.testTag(FluidFeedbackTags.PillCapture),
          )
        },
      ) {
        Text(
          text = labels.captureHint,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 8.dp).width(180.dp),
          maxLines = 2,
        )
      }
      if (phase is FeedbackPhase.Marking) {
        FeedbackMarkup(state, phase, labels)
      }
    }
    FeedbackSheet(state, labels)
    val item = state.shareItem
    FluidShareSheet(
      item = item,
      onDismissRequest = {
        state.shareItem = null
        if (state.phase == FeedbackPhase.Closed && state.draft.canSend) state.open()
      },
      actions = remember(context) { whatsappActions(context) },
      onShared = {
        // Partito: la bozza si dimentica e il foglio non torna.
        state.discard()
        state.phase = FeedbackPhase.Closed
      },
    )
  }
}

/** La segnatura dello screenshot col Markup dell'engine, sopra tutto. Al ✓ il PNG segnato prende il posto della bozza. */
@Composable
private fun BoxScope.FeedbackMarkup(state: FluidFeedbackState, phase: FeedbackPhase.Marking, labels: FluidFeedbackLabels) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ink = rememberFluidInkState(tool = FluidInkTool.Pen)
  val sheet = remember(phase.serial) { FluidInkSheet.image(phase.image) }
  var saving by remember(phase.serial) { mutableStateOf(false) }
  FluidInkEditor(
    state = ink,
    sheet = FluidInkSheetLoad.Ready(sheet),
    onCancel = { state.open() },
    onDone = {
      saving = true
      scope.launch {
        val file = File(FluidFeedbackFile.workDir(context), "screenshot-${System.currentTimeMillis()}.png")
        val ok = runCatching {
          withContext(Dispatchers.IO) {
            file.outputStream().buffered().use { out ->
              FluidInkExport.image(context, sheet, ink.strokes, out, format = Bitmap.CompressFormat.PNG, quality = 100)
            }
          }
        }.isSuccess
        if (ok) {
          state.draft.screenshot?.takeIf { it != file }?.delete()
          state.update { it.copy(screenshot = file, includeScreenshot = true) }
        }
        saving = false
        state.open()
      }
    },
    modifier = Modifier.fillMaxSize().testTag(FluidFeedbackTags.Markup),
    title = labels.markTitle,
    busy = saving,
  )
}

/** Il foglio della segnalazione. */
@Composable
private fun FeedbackSheet(state: FluidFeedbackState, labels: FluidFeedbackLabels) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val visible = state.phase == FeedbackPhase.Composing && state.shareItem == null
  // Le informazioni si raccolgono all'apertura (la coda si legge in sottofondo) e restano per la bozza.
  if (visible && state.info == null) {
    androidx.compose.runtime.LaunchedEffect(state) {
      state.info = withContext(Dispatchers.IO) {
        // R3 · Gli screenshot di lavoro rimasti da un processo ucciso dopo averli scritti se ne vanno alla riapertura.
        FluidFeedbackFile.deleteOrphanWorkFiles(context)
        FluidFeedbackInfo.collect(context, state.config, state.lab, state.diagnostics)
      }
    }
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      scope.launch {
        val image = withContext(Dispatchers.IO) { runCatching { decodePicked(context, uri) }.getOrNull() }
        if (image != null) state.mark(image)
      }
    }
  }
  // Il foglio con le altezze fisse: il contenuto e' piu' alto di uno schermo di telefono e scorre dentro.
  FluidGlassSheet(
    visible = visible,
    onDismissRequest = { state.close() },
    detents = remember { setOf(FluidSheetDetent.Large) },
    paneTitle = labels.title,
  ) {
    val draft = state.draft
    val info = state.info
    FluidSheetScaffold(
      title = labels.title,
      modifier = Modifier.testTag(FluidFeedbackTags.Sheet),
      actions = {
        FluidButton(
          text = labels.sendFile,
          onClick = {
            val ready = info ?: return@FluidButton
            state.busy = true
            state.message = null
            scope.launch {
              val result = runCatching {
                // Lo ZIP (F8.24b): LEGGIMI.txt, il JSON, lo screenshot segnato e il registro pulito, nella cartella del
                // foglio Condividi, che lo manda come documento.
                val file = FluidFeedbackFile.writeToFile(context, draft, ready, labels)
                FluidShareItem.file(context, file, FluidFeedbackFile.MimeType)
              }
              state.busy = false
              result.onSuccess { state.shareItem = it }.onFailure { state.message = labels.failed }
            }
          },
          modifier = Modifier.testTag(FluidFeedbackTags.SendFile),
          size = FluidButtonSize.Large,
          enabled = draft.canSend && info != null && !state.busy,
          loading = state.busy,
          fillWidth = true,
          leading = { FluidIcon(FluidIcons.FolderZip, contentDescription = null) },
        )
        val github = state.config.github
        if (github != null) {
          FluidButton(
            text = labels.openIssue,
            onClick = {
              val ready = info ?: return@FluidButton
              scope.launch {
                val name = FluidFeedbackText.fileName(ready, "png")
                val saved = draft.attachedScreenshot?.let { FluidFeedbackFile.saveToDownloads(context, it, name) }
                val url = FluidFeedbackText.issueUrl(draft, ready, github, labels, screenshotName = if (saved != null) name else null)
                val opened = runCatching {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (opened) {
                  state.discard()
                  state.close()
                } else {
                  state.message = labels.failed
                }
              }
            },
            modifier = Modifier.testTag(FluidFeedbackTags.OpenIssue),
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Large,
            enabled = draft.canSend && info != null && !state.busy,
            fillWidth = true,
            leading = { FluidIcon(FluidIcons.Public, contentDescription = null) },
          )
          FluidSectionFootnote(labels.issueFootnote)
        }
        state.message?.let { FluidSectionFootnote(it) }
      },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidTextField(
          value = draft.description,
          onValueChange = { text -> state.update { it.copy(description = text) } },
          modifier = Modifier.fillMaxWidth().testTag(FluidFeedbackTags.Description),
          label = labels.whatsWrong,
          placeholder = labels.whatsWrongPlaceholder,
          singleLine = false,
          minLines = 3,
          maxLines = 8,
        )
        FeedbackScreenshot(state, labels, onPick = {
          picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        FeedbackParts(state, labels)
        FluidTextField(
          value = draft.credit,
          onValueChange = { text -> state.update { it.copy(credit = text.take(60)) } },
          modifier = Modifier.fillMaxWidth().testTag(FluidFeedbackTags.Credit),
          label = labels.credit,
          placeholder = labels.creditPlaceholder,
        )
      }
    }
  }
}

/** Lo screenshot: i due tasti per averlo, o la miniatura con «Segna» e «Togli» e l'avviso sui dati. */
@Composable
private fun FeedbackScreenshot(state: FluidFeedbackState, labels: FluidFeedbackLabels, onPick: () -> Unit) {
  val file = state.draft.screenshot
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidSectionHeader(labels.screenshot)
    if (file == null) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = labels.capturePage,
          onClick = { state.startCapture() },
          modifier = Modifier.weight(1f).testTag(FluidFeedbackTags.Capture),
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
          leading = { FluidIcon(FluidIcons.Screenshot, contentDescription = null) },
        )
        FluidButton(
          text = labels.pickPhoto,
          onClick = onPick,
          modifier = Modifier.weight(1f).testTag(FluidFeedbackTags.Pick),
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
          leading = { FluidIcon(FluidIcons.PhotoLibrary, contentDescription = null) },
        )
      }
    } else {
      val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { FluidFeedbackFile.loadScreenshot(file, maxSide = 640) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // La miniatura la cambia l'app (dopo la segnatura): entra sfocata.
        FluidGlassContentTransition(
          targetState = image,
          key = { it?.let { file.path } },
          modifier = Modifier
            .height(132.dp)
            .width(72.dp)
            .clip(ContinuousCornerShape(FluidRadius.Small))
            .semantics { contentDescription = labels.screenshotDescription }
            .testTag(FluidFeedbackTags.Thumbnail),
        ) { shown ->
          if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
          }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(labels.screenshotWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidButton(
              text = labels.markAgain,
              onClick = { image?.let(state::mark) },
              modifier = Modifier.testTag(FluidFeedbackTags.Mark),
              style = FluidButtonStyle.Tinted,
              size = FluidButtonSize.Medium,
              enabled = image != null,
              leading = { FluidIcon(FluidIcons.Edit, contentDescription = null) },
            )
            FluidButton(
              text = labels.removeScreenshot,
              onClick = {
                file.delete()
                state.update { it.copy(screenshot = null) }
              },
              modifier = Modifier.testTag(FluidFeedbackTags.Remove),
              style = FluidButtonStyle.Plain,
              size = FluidButtonSize.Medium,
            )
          }
        }
      }
    }
  }
}

/** «Cosa parte»: un interruttore per parte, e tutto il testo in vista con «Mostra tutto quello che parte». */
@Composable
private fun FeedbackParts(state: FluidFeedbackState, labels: FluidFeedbackLabels) {
  val draft = state.draft
  val info = state.info
  var preview by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidSectionHeader(labels.sends)
    val parts = buildList {
      if (draft.screenshot != null) {
        add(FeedbackPart("screenshot", labels.sendsScreenshot, "", draft.includeScreenshot) { on -> state.update { it.copy(includeScreenshot = on) } })
      }
      add(
        FeedbackPart(
          "telefono", labels.sendsDevice,
          info?.let { "${it.device} · Android ${it.android} · ${it.appName} ${it.appVersion}" }.orEmpty(), draft.includeDevice,
        ) { on -> state.update { it.copy(includeDevice = on) } },
      )
      if (info != null && info.experiments.isNotEmpty()) {
        add(
          FeedbackPart("prove", labels.sendsExperiments(info.experiments.size), info.experiments.joinToString(", "), draft.includeExperiments) { on ->
            state.update { it.copy(includeExperiments = on) }
          },
        )
      }
      if (info != null && info.crashes.isNotEmpty()) {
        add(FeedbackPart("arresti", labels.sendsCrashes(info.crashes.size), "", draft.includeCrashes) { on -> state.update { it.copy(includeCrashes = on) } })
      }
      if (info != null && info.log.isNotEmpty()) {
        add(FeedbackPart("eventi", labels.sendsLog(info.log.size), "", draft.includeLog) { on -> state.update { it.copy(includeLog = on) } })
      }
    }
    FluidListGroup {
      parts.forEach { part ->
        FluidToggleRow(
          title = part.title,
          subtitle = part.subtitle,
          checked = part.checked,
          onCheckedChange = part.onChange,
          modifier = Modifier.testTag(FluidFeedbackTags.include(part.key)),
        )
        FluidListDivider()
      }
      FluidListRow(
        title = if (preview) labels.hideWhatSends else labels.showWhatSends,
        subtitle = "",
        modifier = Modifier.testTag(FluidFeedbackTags.PreviewToggle),
        tone = FluidTone.Primary,
        disclosure = false,
        onClick = { preview = !preview },
      )
    }
    if (preview && info != null) {
      Text(
        text = FluidFeedbackText.issueBody(draft, info, labels, screenshotName = draft.attachedScreenshot?.name),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(FluidFeedbackTags.Preview),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        softWrap = false,
      )
    }
  }
}

/** Una parte della segnalazione, col suo interruttore. */
private class FeedbackPart(val key: String, val title: String, val subtitle: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * La riga «Segnala un problema» per la pagina «Informazioni» (in un `FluidListGroup`). Apre il foglio di [state], o di
 * [LocalFluidFeedback] se non lo si passa.
 */
@Composable
fun FluidFeedbackRow(
  modifier: Modifier = Modifier,
  state: FluidFeedbackState? = LocalFluidFeedback.current,
  labels: FluidFeedbackLabels = FluidFeedbackLabels(),
) {
  if (state == null) return
  FluidListRow(
    title = labels.title,
    subtitle = labels.rowSubtitle,
    modifier = modifier.testTag(FluidFeedbackTags.Row),
    leading = { FluidIcon(FluidIcons.Forum, contentDescription = null) },
    tone = FluidTone.Primary,
    onClick = { state.open() },
  )
}

/** I pacchetti di WhatsApp (e WhatsApp Business), nell'ordine in cui si cercano. */
internal val WhatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

/** L'azione «WhatsApp» del foglio Condividi, se WhatsApp c'è: lo stesso invio, diretto a lui. */
private fun whatsappActions(context: Context): List<FluidShareAction> {
  val pm = context.packageManager
  val pkg = WhatsappPackages.firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess } ?: return emptyList()
  return listOf(
    FluidShareAction(id = "whatsapp", title = FluidFeedbackLabels().whatsapp, icon = FluidIcons.Forum) { item ->
      runCatching {
        context.startActivity(FluidShareIntents.send(item).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    },
  )
}

private fun decodePicked(context: Context, uri: Uri, maxSide: Int = 2560): ImageBitmap {
  val bitmap = if (Build.VERSION.SDK_INT >= 28) {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
      val side = max(info.size.width, info.size.height)
      if (side > maxSide) {
        val f = maxSide.toDouble() / side
        decoder.setTargetSize(max(1, (info.size.width * f).toInt()), max(1, (info.size.height * f).toInt()))
      }
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
  } else {
    @Suppress("DEPRECATION")
    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
  }
  return bitmap.asImageBitmap()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
