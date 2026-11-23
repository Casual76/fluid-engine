package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Warning
import java.util.Locale
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

// F6C.2 · Il preset del file sconosciuto: mai un vicolo cieco. L'icona del tipo (FluidFileKind), il nome con
// l'estensione vera, il tipo e la dimensione se nota, «Apri con…» e «Condividi» dell'app, l'avviso per un eseguibile.
// Il dump esadecimale (quello di Universal Converter, PreviewLoader.kt) si può chiedere passando i primi byte, ma non è
// di serie.

/** Le parole del preset sconosciuto, in italiano di serie. */
@Immutable
data class FluidUnknownPresetStrings(
  val openWith: String = "Apri con…",
  val share: String = "Condividi",
  val unknownSize: String = "Dimensione sconosciuta",
  val emptyFile: String = "File vuoto",
  val noPreview: String = "Nessuna anteprima per questo tipo di file",
  val executableWarning: String = "È un'app o un programma: aprilo solo se ti fidi di chi te l'ha mandato.",
  val openFailed: String = "Nessuna app può aprire questo file",
  val shareFailed: String = "Il file non si può condividere",
  val showBytes: String = "Mostra i primi byte",
  val hideBytes: String = "Nascondi i byte",
  val bytesFailed: String = "I byte non si possono leggere",
  val androidPackage: String = "Pacchetto Android",
  val executableType: String = "File eseguibile",
  val apkWarning: String = "È un'app da installare. Aprila solo se ti fidi di chi te l'ha mandata: può leggere i tuoi dati.",
)

/** I numeri del preset sconosciuto. */
object FluidUnknownPresetDefaults {
  /** Quanti byte mostra il dump: il primo KB, come Universal Converter (F6C.0 §6). */
  const val HexDumpBytes: Int = 1024

  /** Byte per riga del dump: otto stanno in monospazio su un telefono da 360 dp. A occhio, 22/11/2026. */
  const val HexRowBytes: Int = 8
}

/**
 * La riga sotto il nome: il tipo (le parole di [FluidLabels] per [FluidFileKind]) e la dimensione, «File vuoto» a
 * zero, [FluidUnknownPresetStrings.unknownSize] se non si sa.
 */
internal fun fluidUnknownDetails(
  info: FluidFileInfo,
  kind: String,
  strings: FluidUnknownPresetStrings,
  locale: Locale = Locale.getDefault(),
  today: LocalDate? = null,
): String {
  val size = when (val bytes = info.sizeBytes) {
    null -> strings.unknownSize
    0L -> strings.emptyFile
    else -> if (bytes < 0) strings.unknownSize else fluidFormatFileSize(bytes, locale)
  }
  val date = info.modifiedAtMillis?.let {
    fluidFormatFileDate(java.time.Instant.ofEpochMilli(it), locale = locale, today = today ?: LocalDate.now(),
      includeTime = true)
  }
  return listOfNotNull(kind, size, date).joinToString(" · ")
}

internal fun viewerIsApk(file: FluidFileInfo): Boolean =
  file.name.endsWith(".apk", ignoreCase = true) ||
    file.mimeType?.substringBefore(';')?.trim()?.equals("application/vnd.android.package-archive", ignoreCase = true) == true

/** Il dump di [bytes]: indirizzo, byte in esadecimale e caratteri stampabili (gli altri sono un punto), [perRow] per riga. */
internal fun fluidHexDump(
  bytes: ByteArray,
  limit: Int = FluidUnknownPresetDefaults.HexDumpBytes,
  perRow: Int = FluidUnknownPresetDefaults.HexRowBytes,
): List<String> {
  val size = minOf(bytes.size, limit.coerceAtLeast(0))
  val row = perRow.coerceAtLeast(1)
  val out = ArrayList<String>((size + row - 1) / row)
  var at = 0
  while (at < size) {
    val end = minOf(at + row, size)
    val line = StringBuilder()
    line.append(String.format(Locale.ROOT, "%08X", at)).append("  ")
    for (i in at until at + row) {
      if (i < end) line.append(String.format(Locale.ROOT, "%02X", bytes[i].toInt() and 0xFF)) else line.append("  ")
      line.append(' ')
    }
    line.append(' ')
    for (i in at until end) {
      val code = bytes[i].toInt() and 0xFF
      line.append(if (code in 0x20..0x7E) code.toChar() else '.')
    }
    out += line.toString()
    at = end
  }
  return out
}

/** Il nome più lungo che sta in [lines] righe larghe [width], tagliato in mezzo con l'estensione vera intera. */
internal fun fluidFitFileName(name: String, measurer: TextMeasurer, style: TextStyle, width: Int, lines: Int): String {
  fun fits(value: String) =
    !measurer.measure(value, style, maxLines = lines, constraints = Constraints(maxWidth = width.coerceAtLeast(1))).hasVisualOverflow
  if (fits(name)) return name
  var low = 0
  var high = name.codePointCount(0, name.length)
  while (low < high) {
    val mid = (low + high + 1) / 2
    if (fits(fluidTruncateFileName(name, mid))) low = mid else high = mid - 1
  }
  return fluidTruncateFileName(name, low)
}

/** Esegue l'azione dell'app; un'eccezione (nessuna app, un URI scaduto) diventa [failed] invece di un crash. */
internal inline fun fluidRunFileAction(action: () -> Unit, failed: () -> Unit) {
  try {
    action()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (error: RuntimeException) {
    failed()
  }
}

/**
 * Il preset del file che il visore non sa mostrare: una scheda, mai un vicolo cieco.
 *
 * - L'icona del tipo ([FluidFileKind], la stessa di [FluidFileTile]), il nome con la sua estensione vera (tagliato in
 *   mezzo se non sta in due righe), il tipo e la dimensione se nota.
 * - «Apri con…» e «Condividi» se l'app li dà ([FluidViewerContent.Unknown.openWith], [FluidViewerContent.Unknown.share]).
 *   Un'apertura che fallisce con un'eccezione lo dice nella scheda; indietro e Chiudi restano sempre nella barra.
 * - Un **eseguibile** (anche con un nome innocuo: la regola di [FluidFileKind.from]) ha l'icona di avviso e una riga che
 *   chiede di aprirlo solo se ci si fida.
 * - **Il dump esadecimale non è di serie**: con [firstBytes] compare «Mostra i primi byte», e il dump del primo KB.
 */
@Composable
fun FluidUnknownPreset(
  file: FluidViewerContent.Unknown,
  modifier: Modifier = Modifier,
  strings: FluidUnknownPresetStrings = FluidUnknownPresetStrings(),
  firstBytes: (suspend () -> ByteArray)? = null,
) {
  val info = file.info
  val labels = LocalFluidLabels.current
  var failure by remember(file) { mutableStateOf<String?>(null) }
  var showBytes by rememberSaveable { mutableStateOf(false) }
  val gap = 10.dp
  val details = LocalViewerInfo.current
  val colors = MaterialTheme.colorScheme
  val image = file.icon ?: details?.thumbnail
  val accent = viewerAccent(details?.copy(thumbnail = image) ?: FluidViewerInfo(info, thumbnail = image), colors.primary)
  val dark = GlassDefaults.isDarkSurface()
  val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
  val displayFile = details?.file ?: info
  val today = if (displayFile.modifiedAtMillis != null) rememberCurrentDate() else null
  val apk = viewerIsApk(info)
  val kind = when {
    apk -> strings.androidPackage
    info.kind == FluidFileKind.Executable -> strings.executableType
    else -> labels.fileKind(info.kind)
  }
  val backdrop = rememberGlassBackdrop()
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(Modifier.matchParentSize().glassBackdropSource(backdrop).viewerWash(accent,
      if (info.kind == FluidFileKind.Executable) ViewerMixProfile.Amber else colors.secondary, colors.primary))
    val top = FluidScreenDefaults.topBarHeight()
    val iconTop = viewerBoardTop(134.dp)
    ViewerMixColumn(Modifier.fillMaxSize().padding(top = top,
      bottom = FluidScreenDefaults.bottomContentPadding())) {
      val iconSize = ViewerMixProfile.FileIcon
      val iconShape = ContinuousCornerShape(ViewerMixProfile.FileIconRadius)
      Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = iconTop - top, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(Modifier.size(iconSize).viewerHalo(accent, ViewerMixProfile.FileIconRadius),
          contentAlignment = Alignment.Center) {
          val icon = image
          if (icon != null) Image(icon, null, Modifier.fillMaxSize().clip(iconShape),
            contentScale = ContentScale.Fit)
          else Box(Modifier.fillMaxSize().background(colors.primary, iconShape), contentAlignment = Alignment.Center) {
            Icon(fluidFileGlyph(info.kind), null, Modifier.size(58.dp), tint = colors.onPrimary)
          }
        }
        val nameStyle = MaterialTheme.typography.headlineSmall.viewerSize(24f, FontWeight.Bold).copy(textAlign = TextAlign.Center)
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 26.dp)) {
          val width = constraints.maxWidth
          val shown = remember(info.name, width, nameStyle, measurer) { fluidFitFileName(info.name, measurer, nameStyle, width, 2) }
          Text(shown, Modifier.fillMaxWidth().semantics { heading() }, style = nameStyle, maxLines = 2)
        }
        Text(details?.subtitle ?: fluidUnknownDetails(displayFile, kind, strings, locale, today), Modifier.padding(top = 5.dp),
          style = MaterialTheme.typography.bodyLarge.viewerSize(15f),
          color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        val facts = details?.facts?.takeIf { it.isNotEmpty() } ?: listOfNotNull(
          info.name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase(), info.mimeType)
        ViewerFacts(facts, backdrop, Modifier.padding(top = 12.dp), height = ViewerMixProfile.FileFactsHeight)
        if (info.kind == FluidFileKind.Executable) {
          Spacer(Modifier.height(44.dp))
          Row(Modifier.fillMaxWidth().heightIn(min = 90.dp)
            .background(if (dark) ViewerMixProfile.WarningDark else ViewerMixProfile.WarningLight,
              ContinuousCornerShape(ViewerMixProfile.WarningRadius))
            .padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(ViewerMixProfile.Amber, ContinuousCornerShape(11.dp)),
              contentAlignment = Alignment.Center) {
              Icon(FluidIcons.Warning, contentDescription = null, modifier = Modifier.size(21.dp), tint = Color.White)
            }
            val warning = if (apk) strings.apkWarning else strings.executableWarning
            val first = warning.indexOfFirst { it == '.' || it == ':' }
            val text = buildAnnotatedString {
              val end = if (first == -1) warning.length else first + 1
              withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(warning.substring(0, end)) }
              append(warning.substring(end))
            }
            Text(text, Modifier.weight(1f), color = if (dark) ViewerMixProfile.WarningInkDark else ViewerMixProfile.WarningInkLight,
              style = MaterialTheme.typography.bodyLarge.viewerSize(14.5f, FontWeight.Medium, 20f))
          }
        }
        Spacer(Modifier.height(if (info.kind == FluidFileKind.Executable) 20.dp else 26.dp))
        val openWith = file.openWith
        val share = file.share
        if (openWith == null && share == null) {
          Text(strings.noPreview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        }
        val button = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        if (openWith != null) ViewerBoardButton(strings.openWith, {
          failure = null
          fluidRunFileAction(openWith) { failure = strings.openFailed }
        }, button)
        if (openWith != null && share != null) Spacer(Modifier.height(gap))
        if (share != null) ViewerBoardButton(strings.share, {
          failure = null
          fluidRunFileAction(share) { failure = strings.shareFailed }
        }, button, style = FluidButtonStyle.Tinted)
        failure?.let {
          Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (firstBytes != null) {
          Spacer(Modifier.height(gap))
          ViewerBoardButton(if (showBytes) strings.hideBytes else strings.showBytes, { showBytes = !showBytes },
            button, style = FluidButtonStyle.Plain, height = 36.dp, textSize = 14.5f)
          if (showBytes) HexDump(firstBytes, strings)
        }
      }
    }
  }
}

@Composable
private fun HexDump(firstBytes: suspend () -> ByteArray, strings: FluidUnknownPresetStrings) {
  var rows by remember(firstBytes) { mutableStateOf<List<String>?>(null) }
  var failed by remember(firstBytes) { mutableStateOf(false) }
  LaunchedEffect(firstBytes) {
    try {
      rows = fluidHexDump(firstBytes())
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      failed = true
    }
  }
  val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
  when {
    failed -> Text(strings.bytesFailed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    rows == null -> FluidSpinner()
    else -> Column(Modifier.fillMaxWidth()) {
      for (line in rows.orEmpty()) Text(line, style = style, maxLines = 1, softWrap = false)
    }
  }
}
