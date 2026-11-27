package dev.antigravity.fluidengine.diagnostics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.fluidGroupedItems
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Insights
import dev.antigravity.fluidengine.ui.fluid.icons.PhoneAndroid
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.icons.Shield
import dev.antigravity.fluidengine.ui.fluid.icons.Speed
import dev.antigravity.fluidengine.ui.fluid.icons.Warning
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// F8.24 · L'interfaccia della diagnostica: il foglio del consenso (chiesto una volta, come «Condividi con gli sviluppatori
// di app» che iOS chiede alla configurazione), il gruppo delle impostazioni e la pagina «Dati di diagnostica», la
// «Privacy in pratica» della scheda: cosa si raccoglie in parole semplici, e cosa c'è sul telefono adesso, riga per riga,
// col JSON vero che partirebbe. Righe di impostazioni senza vetro; il foglio è il foglio di vetro dell'engine.

/** Le parole della diagnostica. In italiano di serie; [english] è il punto di partenza per un'app tradotta. */
@Immutable
data class FluidDiagnosticsLabels(
  val consentTitle: (appName: String) -> String = { "Aiuta a migliorare $it" },
  val consentMessage: (appName: String) -> String = {
    "Se $it si chiude da sola o va a scatti, può mandare allo sviluppatore un rapporto, senza i tuoi dati. " +
      "Puoi cambiare idea quando vuoi, nelle impostazioni."
  },
  val consentSends: String = "Cosa parte",
  val sendsCrashes: String = "Quando l'app si chiude da sola o si blocca, e in che punto del codice",
  val sendsFrames: String = "Quali pagine vanno a scatti",
  val sendsDevice: String = "Il modello del telefono e le versioni di Android e dell'app",
  val consentNever: String = "Cosa non parte mai",
  val neverDetail: String = "Quello che scrivi, le foto e i file, il tuo nome e il tuo account, la posizione, l'indirizzo di rete",
  val share: String = "Condividi con lo sviluppatore",
  val dontShare: String = "Non condividere",
  val settingTitle: String = "Condividi con lo sviluppatore",
  val settingSubtitle: String = "I crash e i rallentamenti, senza i tuoi dati",
  val dataTitle: String = "Dati di diagnostica",
  val dataCount: (Int) -> String = { n -> if (n == 1) "1 rapporto sul telefono" else "$n rapporti sul telefono" },
  val dataNone: String = "Nessun rapporto sul telefono",
  val settingFootnote: String = "I rapporti restano sul telefono finché non dici di sì. Partono al massimo una volta al giorno.",
  val whatHeader: String = "Cosa si raccoglie",
  val onPhoneHeader: String = "Sul telefono",
  val emptyTitle: String = "Niente da mandare",
  val emptyDetail: String = "Quando l'app si chiude da sola o va a scatti, il rapporto compare qui prima di partire.",
  val sendNow: String = "Invia ora",
  val sent: (Int) -> String = { n -> if (n == 1) "Partito 1 rapporto" else "Partiti $n rapporti" },
  val sendFailed: String = "Non è partito: riprova più tardi",
  val nothingToSend: String = "Non c'è niente da mandare",
  val clear: String = "Cancella i dati",
  val clearConfirmTitle: String = "Cancellare i rapporti sul telefono?",
  val clearConfirm: String = "Cancella",
  val cancel: String = "Annulla",
  val close: String = "Chiudi",
  val kindCrash: String = "L'app si è chiusa",
  val kindExit: (reason: String) -> String = { reason ->
    when (reason) {
      "anr" -> "L'app si è bloccata"
      "low_memory" -> "Chiusa dal sistema: memoria finita"
      "crash_native" -> "L'app si è chiusa (codice nativo)"
      "excessive_resource" -> "Chiusa dal sistema: troppe risorse"
      "initialization_failure" -> "L'app non è partita"
      "freezer" -> "Chiusa dal sistema mentre era ferma"
      else -> "Chiusa dal sistema"
    }
  },
  val kindFrames: (page: String) -> String = { "Fotogrammi di «$it»" },
  val framesDetail: (frames: Long, slow: Long, hangs: Long) -> String = { frames, slow, hangs ->
    "$frames fotogrammi, $slow lenti" + if (hangs > 0) ", $hangs blocchi" else ""
  },
  val notSharedFootnote: String = "Non condividi: niente si raccoglie e niente parte.",
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidDiagnosticsLabels = FluidDiagnosticsLabels(
      consentTitle = { "Help improve $it" },
      consentMessage = {
        "If $it closes by itself or stutters, it can send the developer a report, without your data. " +
          "You can change your mind any time in settings."
      },
      consentSends = "What is sent",
      sendsCrashes = "When the app closes by itself or hangs, and where in the code",
      sendsFrames = "Which pages stutter",
      sendsDevice = "Your phone model and the Android and app versions",
      consentNever = "What is never sent",
      neverDetail = "What you write, photos and files, your name and account, your location, your network address",
      share = "Share with the developer",
      dontShare = "Don't share",
      settingTitle = "Share with the developer",
      settingSubtitle = "Crashes and slowdowns, without your data",
      dataTitle = "Diagnostic data",
      dataCount = { n -> if (n == 1) "1 report on this phone" else "$n reports on this phone" },
      dataNone = "No reports on this phone",
      settingFootnote = "Reports stay on your phone until you say yes. They are sent at most once a day.",
      whatHeader = "What is collected",
      onPhoneHeader = "On this phone",
      emptyTitle = "Nothing to send",
      emptyDetail = "When the app closes by itself or stutters, the report shows up here before it is sent.",
      sendNow = "Send now",
      sent = { n -> if (n == 1) "1 report sent" else "$n reports sent" },
      sendFailed = "Not sent: try again later",
      nothingToSend = "Nothing to send",
      clear = "Delete data",
      clearConfirmTitle = "Delete the reports on this phone?",
      clearConfirm = "Delete",
      cancel = "Cancel",
      close = "Close",
      kindCrash = "The app closed",
      kindExit = { reason ->
        when (reason) {
          "anr" -> "The app hung"
          "low_memory" -> "Closed by the system: out of memory"
          "crash_native" -> "The app closed (native code)"
          "excessive_resource" -> "Closed by the system: too many resources"
          "initialization_failure" -> "The app did not start"
          "freezer" -> "Closed by the system while frozen"
          else -> "Closed by the system"
        }
      },
      kindFrames = { "Frames of “$it”" },
      framesDetail = { frames, slow, hangs -> "$frames frames, $slow slow" + if (hangs > 0) ", $hangs hangs" else "" },
      notSharedFootnote = "Not sharing: nothing is collected and nothing is sent.",
    )
  }
}

/** I tag dei test della diagnostica. */
object FluidDiagnosticsTags {
  const val Consent = "fluid-diagnostics:consenso"
  const val Share = "fluid-diagnostics:condividi"
  const val DontShare = "fluid-diagnostics:non-condividere"
  const val Toggle = "fluid-diagnostics:interruttore"
  const val DataRow = "fluid-diagnostics:dati"
  const val DataPage = "fluid-diagnostics:pagina"
  const val SendNow = "fluid-diagnostics:invia"
  const val Clear = "fluid-diagnostics:cancella"
  const val Empty = "fluid-diagnostics:vuoto"
  const val Json = "fluid-diagnostics:json"

  /** La riga del rapporto [id]. */
  fun record(id: String): String = "fluid-diagnostics:rapporto:$id"
}

/**
 * Il foglio del consenso (F8.24): si apre da sé finché la scelta non c'è, **una volta**. «Condividi con lo
 * sviluppatore» è il sì; «Non condividere», e anche chiudere il foglio, è il no: senza un sì esplicito non parte niente
 * (il GDPR chiede un consenso dato, non presunto). Si cambia idea in [FluidDiagnosticsSettingsGroup].
 *
 * L'app lo mette dove vuole che compaia (dopo il benvenuto, non sopra il primo fotogramma) e lo spegne con [enabled]
 * finché non è il momento.
 */
@Composable
fun FluidDiagnosticsConsentPrompt(
  diagnostics: FluidDiagnostics,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  labels: FluidDiagnosticsLabels = FluidDiagnosticsLabels(),
) {
  val consent by diagnostics.consent.collectAsStateWithLifecycle()
  val appName = diagnostics.config.appName
  FluidGlassSheet(
    visible = enabled && consent == FluidDiagnosticsConsent.NotAsked,
    onDismissRequest = { diagnostics.setConsent(false) },
    detents = remember { setOf(FluidSheetDetent.Large) },
    paneTitle = labels.consentTitle(appName),
  ) {
    FluidSheetScaffold(
      title = labels.consentTitle(appName),
      modifier = modifier.testTag(FluidDiagnosticsTags.Consent),
      message = labels.consentMessage(appName),
      icon = { FluidIcon(FluidIcons.Insights, contentDescription = null) },
      tone = FluidTone.Info,
      actions = {
        FluidButton(
          text = labels.share,
          onClick = { diagnostics.setConsent(true) },
          modifier = Modifier.testTag(FluidDiagnosticsTags.Share),
          size = FluidButtonSize.Large,
          fillWidth = true,
        )
        FluidButton(
          text = labels.dontShare,
          onClick = { diagnostics.setConsent(false) },
          modifier = Modifier.testTag(FluidDiagnosticsTags.DontShare),
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Large,
          fillWidth = true,
        )
      },
    ) {
      FluidDiagnosticsWhat(labels)
    }
  }
}

/** Cosa parte e cosa no, in tre righe e una: lo stesso nel foglio del consenso e nella pagina dei dati. */
@Composable
private fun FluidDiagnosticsWhat(labels: FluidDiagnosticsLabels) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidSectionHeader(labels.consentSends)
    FluidListGroup {
      FluidListRow(
        title = labels.sendsCrashes, subtitle = "", disclosure = false, tone = FluidTone.Warning,
        leading = { FluidIcon(FluidIcons.Warning, contentDescription = null) },
      )
      FluidListDivider()
      FluidListRow(
        title = labels.sendsFrames, subtitle = "", disclosure = false, tone = FluidTone.Info,
        leading = { FluidIcon(FluidIcons.Speed, contentDescription = null) },
      )
      FluidListDivider()
      FluidListRow(
        title = labels.sendsDevice, subtitle = "", disclosure = false, tone = FluidTone.Neutral,
        leading = { FluidIcon(FluidIcons.PhoneAndroid, contentDescription = null) },
      )
    }
    FluidSectionHeader(labels.consentNever)
    FluidListGroup {
      FluidListRow(
        title = labels.neverDetail, subtitle = "", disclosure = false, tone = FluidTone.Success,
        leading = { FluidIcon(FluidIcons.Shield, contentDescription = null) },
      )
    }
  }
}

/**
 * Le righe della diagnostica per una pagina delle impostazioni (di solito «Privacy»): l'interruttore «Condividi con lo
 * sviluppatore» e «Dati di diagnostica», che apre [onOpenData] (la [FluidDiagnosticsDataPage]). Con la nota sotto.
 */
@Composable
fun FluidDiagnosticsSettingsGroup(
  diagnostics: FluidDiagnostics,
  onOpenData: () -> Unit,
  modifier: Modifier = Modifier,
  labels: FluidDiagnosticsLabels = FluidDiagnosticsLabels(),
) {
  val consent by diagnostics.consent.collectAsStateWithLifecycle()
  val count by diagnostics.queue.count.collectAsStateWithLifecycle()
  Column(modifier) {
    FluidListGroup {
      FluidToggleRow(
        title = labels.settingTitle,
        subtitle = labels.settingSubtitle,
        checked = consent == FluidDiagnosticsConsent.Granted,
        onCheckedChange = { diagnostics.setConsent(it) },
        modifier = Modifier.testTag(FluidDiagnosticsTags.Toggle),
        leading = { FluidIcon(FluidIcons.Insights, contentDescription = null) },
        tone = FluidTone.Info,
      )
      FluidListDivider()
      FluidListRow(
        title = labels.dataTitle,
        subtitle = if (count == 0) labels.dataNone else labels.dataCount(count),
        modifier = Modifier.testTag(FluidDiagnosticsTags.DataRow),
        onClick = onOpenData,
      )
    }
    FluidSectionFootnote(if (consent == FluidDiagnosticsConsent.Denied) labels.notSharedFootnote else labels.settingFootnote)
  }
}

/**
 * La pagina «Dati di diagnostica» (F8.24): cosa si raccoglie, in parole semplici, e i rapporti che sono sul telefono
 * adesso, ognuno col JSON che partirebbe (un tocco sulla riga). «Invia ora» col sì e un indirizzo; «Cancella i dati»
 * con la conferma.
 *
 * Mentre la coda si legge (sul filo dell'IO) la pagina non dice «Niente da mandare» e i tasti aspettano: lo dice solo
 * quando sa che la coda e' vuota. I rapporti sono una riga della lista l'uno, e si compongono quelli che si vedono (R3:
 * prima la pagina partiva «vuota» anche con i rapporti, e li componeva tutti, fino a 200, in un elemento solo).
 */
@Composable
fun FluidDiagnosticsDataPage(
  diagnostics: FluidDiagnostics,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  labels: FluidDiagnosticsLabels = FluidDiagnosticsLabels(),
) {
  val consent by diagnostics.consent.collectAsStateWithLifecycle()
  val count by diagnostics.queue.count.collectAsStateWithLifecycle()
  // Null finche' la coda non e' letta: «sto leggendo» non e' «vuota».
  val read by produceState<List<FluidDiagnosticsRecord>?>(initialValue = null, diagnostics, count) {
    value = withContext(Dispatchers.IO) {
      diagnostics.queue.refreshCount()
      diagnostics.queue.records().asReversed()
    }
  }
  val records = read.orEmpty()
  val scope = rememberCoroutineScope()
  var shown by remember { mutableStateOf<FluidDiagnosticsRecord?>(null) }
  var outcome by remember { mutableStateOf<String?>(null) }
  var confirmClear by remember { mutableStateOf(false) }
  var sending by remember { mutableStateOf(false) }
  FluidScreen(title = labels.dataTitle, onBack = onBack, modifier = modifier.testTag(FluidDiagnosticsTags.DataPage)) {
    item(key = "what") {
      Column {
        FluidSectionHeader(labels.whatHeader)
        FluidDiagnosticsWhat(labels)
      }
    }
    item(key = "phone") {
      Column {
        FluidSectionHeader(labels.onPhoneHeader)
        if (read?.isEmpty() == true) {
          FluidEmptyState(labels.emptyTitle, labels.emptyDetail, Modifier.testTag(FluidDiagnosticsTags.Empty))
        }
      }
    }
    // La chiave porta anche il posto: gli id vengono dal file della coda, e una riga doppia non deve far cadere la lista.
    fluidGroupedItems(count = records.size, key = { i -> "rapporto:$i:${records[i].id}" }, animateItems = false) { i ->
      val record = records[i]
      FluidListRow(
        title = fluidDiagnosticsTitle(record, labels),
        subtitle = fluidDiagnosticsSubtitle(record, labels),
        modifier = Modifier.testTag(FluidDiagnosticsTags.record(record.id)),
        leading = { FluidIcon(fluidDiagnosticsIcon(record), contentDescription = null) },
        tone = when (record.kind) {
          FluidDiagnosticsKind.Crash -> FluidTone.Danger
          FluidDiagnosticsKind.Exit -> FluidTone.Warning
          FluidDiagnosticsKind.Frames -> FluidTone.Info
        },
        onClick = { shown = record },
      )
    }
    item(key = "actions") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        if (consent == FluidDiagnosticsConsent.Granted && diagnostics.config.endpoint != null) {
          FluidButton(
            text = labels.sendNow,
            onClick = {
              sending = true
              scope.launch {
                val result = diagnostics.send()
                outcome = when (result) {
                  is FluidDiagnosticsSendResult.Sent -> labels.sent(result.count)
                  is FluidDiagnosticsSendResult.Failed -> labels.sendFailed
                  else -> labels.nothingToSend
                }
                sending = false
              }
            },
            modifier = Modifier.testTag(FluidDiagnosticsTags.SendNow),
            loading = sending,
            enabled = !sending && records.isNotEmpty(),
            fillWidth = true,
            leading = { FluidIcon(FluidIcons.Send, contentDescription = null) },
          )
        }
        outcome?.let { FluidSectionFootnote(it) }
        FluidButton(
          text = labels.clear,
          onClick = { confirmClear = true },
          modifier = Modifier.testTag(FluidDiagnosticsTags.Clear),
          style = FluidButtonStyle.Destructive,
          enabled = records.isNotEmpty(),
          fillWidth = true,
          leading = { FluidIcon(FluidIcons.Delete, contentDescription = null) },
        )
      }
    }
  }
  FluidGlassSheet(
    item = shown,
    onDismissRequest = { shown = null },
    detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    paneTitle = labels.dataTitle,
  ) { record ->
    FluidSheetScaffold(
      title = fluidDiagnosticsTitle(record, labels),
      message = fluidDiagnosticsSubtitle(record, labels),
      actions = {
        FluidButton(text = labels.close, onClick = { shown = null }, style = FluidButtonStyle.Tinted, fillWidth = true)
      },
    ) {
      Text(
        text = PrettyJson.encodeToString(JsonObject.serializer(), record.toJson()),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(FluidDiagnosticsTags.Json),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        softWrap = false,
      )
    }
  }
  FluidConfirmationDialog(
    visible = confirmClear,
    onDismissRequest = { confirmClear = false },
    title = labels.clearConfirmTitle,
    actions = listOf(FluidAlertAction(labels.clearConfirm, { diagnostics.clear() }, FluidAlertAction.Emphasis.Destructive)),
    cancelLabel = labels.cancel,
  )
}

private val PrettyJson = Json { prettyPrint = true }

/** Il titolo di un rapporto nella pagina dei dati. */
internal fun fluidDiagnosticsTitle(record: FluidDiagnosticsRecord, labels: FluidDiagnosticsLabels): String = when (record.kind) {
  FluidDiagnosticsKind.Crash -> labels.kindCrash
  FluidDiagnosticsKind.Exit -> labels.kindExit(record.exit?.reason.orEmpty())
  FluidDiagnosticsKind.Frames -> labels.kindFrames(record.frames?.page.orEmpty())
}

/** La riga sotto: per i fotogrammi i conti, per gli altri il tipo; poi l'ora e la versione. */
internal fun fluidDiagnosticsSubtitle(record: FluidDiagnosticsRecord, labels: FluidDiagnosticsLabels, locale: Locale = Locale.getDefault()): String {
  val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(record.hourMillis))
  val what = when (record.kind) {
    FluidDiagnosticsKind.Crash -> record.crash?.type?.substringAfterLast('.').orEmpty()
    FluidDiagnosticsKind.Exit -> record.exit?.importance.orEmpty()
    FluidDiagnosticsKind.Frames -> record.frames?.let { labels.framesDetail(it.frames, it.slow, it.hangs) }.orEmpty()
  }
  return listOf(what, whenText, record.source.appVersion).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun fluidDiagnosticsIcon(record: FluidDiagnosticsRecord) = when (record.kind) {
  FluidDiagnosticsKind.Crash -> FluidIcons.Error
  FluidDiagnosticsKind.Exit -> FluidIcons.Warning
  FluidDiagnosticsKind.Frames -> FluidIcons.Speed
}
