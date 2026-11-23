package dev.antigravity.fluidengine.viewer.data

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonText
import dev.antigravity.fluidengine.ui.fluid.FluidWebContent
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.ui.fluid.FluidWebSizing
import dev.antigravity.fluidengine.ui.fluid.icons.AttachFile
import dev.antigravity.fluidengine.ui.fluid.icons.AudioFile
import dev.antigravity.fluidengine.ui.fluid.icons.Description
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FolderZip
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.icons.PictureAsPdf
import dev.antigravity.fluidengine.ui.fluid.icons.TableChart
import dev.antigravity.fluidengine.ui.fluid.icons.VideoFile
import dev.antigravity.fluidengine.ui.fluid.rememberFluidWebContentState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerTypeActions
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Una mail aperta: il messaggio letto, le regole delle risorse (le parti `cid:` dal messaggio, mai la rete) e gli
 * allegati aperti dal risolutore, che il modello chiude con sé.
 */
internal class DataMailModel(val name: String, val mail: DataMail, private val request: FluidViewerRequest, private val settings: FluidDataSettings) : Closeable {
  val remote: Int = mail.html?.let(DataWeb::remoteReferences) ?: 0
  val policy = DataWeb.Policy(
    local = { path -> mail.part(path)?.let { DataWeb.serveBytes(it.type, it.body) } },
    part = { id -> mail.part(id)?.let { DataWeb.serveBytes(it.type, it.body) } },
  )

  /** Il corpo HTML pronto: il foglio minimo, e la larghezza dello schermo se la mail non è impaginata a misura fissa. */
  val html: String? = mail.html?.let { DataWeb.inject(it, DataWeb.MailCss, viewport = !DataWeb.fixedWidth(it)) }

  private val opened = ArrayList<FluidViewerDocument>()

  /** Apre l'allegato [part] col risolutore: il suo preset, o lo sconosciuto con «Apri con…» se l'app lo dà. */
  suspend fun open(part: MimePart, index: Int): FluidViewerDocument = withContext(Dispatchers.IO) {
    val name = attachmentName(part, index)
    val dir = File(request.context.cacheDir, "fluid-viewer-data").apply { mkdirs() }
    val file = File.createTempFile("allegato-", "-" + name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60), dir)
    file.writeBytes(part.body)
    val openFile = settings.openFile
    val openWith: (() -> Unit)? = if (openFile == null) null else ({
      val copy = File(dir, "uscita-${System.nanoTime()}-" + name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60))
      copy.writeBytes(part.body)
      openFile(name, part.type, copy)
    })
    val document = FluidViewerRegistry.open(
      request.context, FluidViewerSource.of(file, name, part.type.takeIf { it != "application/octet-stream" }, deleteOnClose = true),
      request.options, openWith = openWith, share = null, media = request.media,
    )
    synchronized(opened) { opened += document }
    document
  }

  fun release(document: FluidViewerDocument) {
    synchronized(opened) { opened.remove(document) }
    document.close()
  }

  override fun close() {
    val all = synchronized(opened) { opened.toList().also { opened.clear() } }
    all.forEach { it.close() }
  }

  companion object {
    fun attachmentName(part: MimePart, index: Int): String = part.filename ?: when {
      part.type == "message/rfc822" -> "messaggio-${index + 1}.eml"
      part.type == "text/calendar" -> "invito-${index + 1}.ics"
      part.type == "text/vcard" || part.type == "text/x-vcard" -> "contatto-${index + 1}.vcf"
      else -> "allegato-${index + 1}"
    }
  }
}

/**
 * F6C.13 · Una mail come in Mail: la scheda delle intestazioni (oggetto, mittente con l'avatar, destinatari, data), il
 * corpo HTML in `FluidWebContent` (le immagini `cid:` dalle parti, le remote bloccate) o il testo, e gli allegati come
 * righe di un gruppo. Un allegato si apre col risolutore, col suo preset, dentro questa pagina: il back torna al messaggio.
 */
@Composable
internal fun DataMailPage(scope: FluidViewerCustomScope, model: DataMailModel, settings: FluidDataSettings) {
  val strings = settings.strings
  val mail = model.mail
  var nested by remember { mutableStateOf<FluidViewerDocument?>(null) }
  var generation by remember { mutableIntStateOf(0) }
  var remoteAllowed by rememberSaveable { mutableStateOf(false) }
  // F6C.20: le intestazioni tutte, come «Mostra tutte le intestazioni» di Mail, e il foglio degli allegati.
  var allHeaders by rememberSaveable { mutableStateOf(false) }
  var attachmentsOpen by rememberSaveable { mutableStateOf(false) }
  val coroutines = rememberCoroutineScope()
  val state = rememberFluidWebContentState()
  val open = rememberDataLinkOpener(settings)
  val dark = dataDark()
  val current = nested
  if (current != null) {
    DataNested(scope, current, strings.backToMessage) {
      nested = null
      generation++
      model.release(current)
    }
    return
  }
  // Un allegato si apre col risolutore, col suo preset, dentro questa pagina: dalla riga del messaggio o dal foglio.
  val openAttachment: (MimePart, Int) -> Unit = { part, i ->
    coroutines.launch {
      nested = model.open(part, i)
    }
  }
  DataInner(
    scope,
    generation = generation,
    actions = {
      if (model.remote > 0 && settings.remoteImages != null && !remoteAllowed) {
        text("data.mail.images", strings.loadImages, {
          model.policy.remote = settings.remoteImages
          remoteAllowed = true
          state.retry()
        }, placement = FluidToolbarPlacement.BottomBar)
      }
      // Le azioni del tipo (F6C.20): tutte le intestazioni del file, e gli allegati in un foglio se ce ne sono.
      viewerTypeActions(strings.more) {
        listOfNotNull(
          FluidContextAction(if (allHeaders) strings.fewerHeaders else strings.allHeaders) { allHeaders = !allHeaders },
          if (mail.attachments.isEmpty()) null else FluidContextAction(strings.attachments) { attachmentsOpen = true },
        )
      }
    },
  ) {
    val scheme = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
    ) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            mail.subject ?: strings.noSubject, Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
          )
          val from = mail.from.firstOrNull()
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Le iniziali dal nome senza simboli: «Marta Rossi 🌉» fa «MR».
            val initials = from?.display?.filter { it.isLetterOrDigit() || it.isWhitespace() }?.trim()?.ifEmpty { null } ?: "?"
            FluidAvatar(initials, key = from?.address ?: initials)
            Column(Modifier.weight(1f)) {
              Text(from?.display ?: "—", style = MaterialTheme.typography.titleSmall)
              if (from != null && from.address != null && from.address != from.display) {
                Text(from.address, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
              }
            }
            mail.date?.withZoneSameInstant(zone)?.let {
              Text(it.format(DateTimeFormatter.ofPattern("d MMM yyyy\nHH:mm", Locale.ITALIAN)), style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant)
            }
          }
          if (mail.to.isNotEmpty()) MailLine(strings.to, mail.to.joinToString(", ") { it.display })
          if (mail.cc.isNotEmpty()) MailLine(strings.cc, mail.cc.joinToString(", ") { it.display })
          mail.date?.withZoneSameInstant(zone)?.let {
            MailLine(strings.date, it.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)))
          }
          if (allHeaders) MailHeaders(mail.headers)
          if (model.remote > 0 && !remoteAllowed) {
            Text(strings.remoteBlocked(model.remote), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
          }
        }
        FluidListDivider()
      }
      item(key = "body") {
        val body = model.html
        val text = mail.text
        // Nel tema scuro un corpo senza colori suoi prende quelli del tema; una newsletter con i suoi colori resta com'è.
        val themed = remember(body, dark, scheme) { body?.let { DataWeb.forTheme(it, dark, scheme.surface, scheme.onSurface, scheme.primary) } }
        when {
          themed != null -> FluidWebContent(
            html = themed.first,
            onLinkClick = { open(it.url) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            state = state,
            onRequest = model.policy::handle,
            sizing = FluidWebSizing.WrapContent(),
            darkening = themed.second,
            placeholder = { FluidSkeletonText(Modifier.padding(FluidScreenDefaults.HorizontalPadding), lines = 8) },
            failure = { failure ->
              if (text != null) MailText(text) else DataNotice(strings.webFailed, failure.toString(), Modifier.padding(FluidScreenDefaults.HorizontalPadding))
            },
          )
          text != null -> MailText(text)
          else -> Text("—", Modifier.padding(FluidScreenDefaults.HorizontalPadding), color = scheme.onSurfaceVariant)
        }
      }
      if (mail.attachments.isNotEmpty()) item(key = "attachments") {
        Column(Modifier.fillMaxWidth().padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 12.dp)) {
          DataSectionTitle(strings.attachments)
          MailAttachments(mail, openAttachment)
        }
      }
    }
    // «Allegati» di «•••» (F6C.20): le stesse righe in un foglio, che si chiude quando l'allegato si apre.
    FluidGlassSheet(
      visible = attachmentsOpen,
      onDismissRequest = { attachmentsOpen = false },
      detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
      paneTitle = strings.attachments,
    ) {
      Text(
        strings.attachments,
        Modifier.padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = DataProfile.SheetTitlePadding).semantics { heading() },
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      )
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FluidScreenDefaults.HorizontalPadding)) {
        MailAttachments(mail) { part, i ->
          attachmentsOpen = false
          openAttachment(part, i)
        }
      }
    }
  }
}

/** Gli allegati come righe di un gruppo: il nome, la misura e il tipo, l'icona del tipo; un tocco apre l'allegato. */
@Composable
private fun MailAttachments(mail: DataMail, onOpen: (MimePart, Int) -> Unit) {
  FluidListGroup {
    mail.attachments.forEachIndexed { i, part ->
      val name = DataMailModel.attachmentName(part, i)
      FluidListRow(
        title = name,
        subtitle = "${DataText.size(part.body.size.toLong())} · ${part.type}",
        leading = { FluidIcon(iconFor(name, part.type), contentDescription = null) },
        onClick = { onOpen(part, i) },
      )
      if (i < mail.attachments.lastIndex) FluidListDivider()
    }
  }
}

/**
 * F6C.20 · Tutte le intestazioni del messaggio, nell'ordine e coi nomi del file (le parole codificate RFC 2047 lette),
 * in monospazio e da selezionare, come «Mostra tutte le intestazioni» di Mail.
 */
@Composable
private fun MailHeaders(headers: MimeHeaders) {
  val scheme = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
  SelectionContainer {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DataProfile.MailHeaderGap)) {
      headers.list.forEach { (name, value) ->
        Text(
          buildAnnotatedString {
            withStyle(SpanStyle(color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)) { append(name) }
            append(": ")
            append(DataMime.decodeWords(value))
          },
          style = style,
        )
      }
    }
  }
}

@Composable
private fun MailLine(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun MailText(text: String) {
  SelectionContainer {
    Text(text.trimEnd(), Modifier.fillMaxWidth().padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 12.dp),
      style = MaterialTheme.typography.bodyLarge)
  }
}

private fun iconFor(name: String, type: String) = when (FluidFileKind.from(type, name)) {
  FluidFileKind.Image -> FluidIcons.Image
  FluidFileKind.Pdf -> FluidIcons.PictureAsPdf
  FluidFileKind.Spreadsheet -> FluidIcons.TableChart
  FluidFileKind.Archive -> FluidIcons.FolderZip
  FluidFileKind.Audio -> FluidIcons.AudioFile
  FluidFileKind.Video -> FluidIcons.VideoFile
  FluidFileKind.Document, FluidFileKind.Text -> FluidIcons.Description
  else -> FluidIcons.AttachFile
}

/** Per le prove: l'indirizzo che il contenitore darebbe a una parte `cid:`. */
internal fun cidUri(id: String): Uri = Uri.parse("cid:$id")

/** Per le prove: la risposta del gestore della mail a [uri]. */
internal fun DataMailModel.serve(uri: Uri): FluidWebResponse = policy.handle(dev.antigravity.fluidengine.ui.fluid.FluidWebRequest(uri, "GET", false))
