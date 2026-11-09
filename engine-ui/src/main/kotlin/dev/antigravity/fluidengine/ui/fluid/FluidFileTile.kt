package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import java.util.Locale

/** Stato deciso dall'app: nessun download, accesso URI o caricamento immagini viene avviato dall'engine. */
@Stable
sealed interface FluidFileStatus {
  data object Remote : FluidFileStatus
  data object Ready : FluidFileStatus
  /** Lettura differita: passare { state.value }, senza leggere il progresso nella riga chiamante. Null = indeterminato. */
  class Transferring(val progress: () -> Float? = { null }) : FluidFileStatus
  data object Failed : FluidFileStatus
}

/**
 * File in un gruppo di contenuto. Nome fino a due righe, estensione finale conservata e nome intero per TalkBack.
 * [thumbnail] e [trailingMenu] sono dell'app: il menu deve usare gli host Fluid, la miniatura non deve sostituire
 * l'avviso sugli eseguibili. [onClick] scarica Remote o apre Ready; durante trasferimento/errore e disabilitato.
 * Estensioni patologiche piu larghe di due righe richiedono al chiamante spazio sufficiente per essere leggibili.
 */
@Composable
fun FluidFileTile(
  name: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  mimeType: String? = null,
  sizeBytes: Long? = null,
  status: FluidFileStatus = FluidFileStatus.Ready,
  onCancel: (() -> Unit)? = null,
  onRetry: (() -> Unit)? = null,
  onRemove: (() -> Unit)? = null,
  thumbnail: (@Composable () -> Unit)? = null,
  trailingMenu: (@Composable () -> Unit)? = null,
  locale: Locale = LocalConfiguration.current.locales[0],
) {
  val labels = LocalFluidLabels.current
  val kind = remember(name, mimeType) { FluidFileKind.from(mimeType, name) }
  val size = remember(sizeBytes, locale) { sizeBytes?.let { fluidFormatFileSize(it, locale) } }
  val stateLabel = when (status) {
    FluidFileStatus.Remote -> labels.fileRemote
    FluidFileStatus.Ready -> labels.fileReady
    FluidFileStatus.Failed -> labels.fileFailed
    is FluidFileStatus.Transferring -> labels.fileTransferring
  }
  val description = listOfNotNull(name, labels.fileKind(kind), size, stateLabel).joinToString(", ")
  Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
          .fluidRowPressable(
            onClick = if (status == FluidFileStatus.Ready || status == FluidFileStatus.Remote) onClick else null,
            role = Role.Button,
            animateFeedback = LocalFluidMotionPolicy.current.allowDecorativeMotion,
          )
          .semantics(mergeDescendants = true) { contentDescription = description }
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(Modifier.clearAndSetSemantics {}) {
          if (thumbnail != null && kind != FluidFileKind.Executable) Box(Modifier.size(40.dp).clip(ContinuousCornerShape(FluidRadius.Small))) { thumbnail() }
          else FileIcon(kind, 40)
        }
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
          FileName(name, 2)
          Text(listOfNotNull(size, stateLabel).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Column {
        if (status is FluidFileStatus.Transferring && onCancel != null) FileAction(labels.fileCancel, Icons.Outlined.Close, onCancel)
        if (status == FluidFileStatus.Failed && onRetry != null) FileAction(labels.fileRetry, Icons.Outlined.Refresh, onRetry)
        if (onRemove != null) FileAction(labels.fileRemove, Icons.Outlined.DeleteOutline, onRemove)
        trailingMenu?.invoke()
      }
    }
    if (status is FluidFileStatus.Transferring) FileProgress(status)
  }
}

/**
 * File compatto per righe dense: superficie piu bassa del bersaglio tattile Compose da 48 dp, espanso fuori
 * dal layout tramite ViewConfiguration. Lasciare spazio fra chip per evitare bersagli sovrapposti.
 * Il gesto cede agli scroll degli antenati; l'estensione non viene sostituita da quella dichiarata dal MIME.
 */
@Composable
fun FluidFileChip(
  name: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  mimeType: String? = null,
) {
  val kind = remember(name, mimeType) { FluidFileKind.from(mimeType, name) }
  val labels = LocalFluidLabels.current
  val viewConfiguration = LocalViewConfiguration.current
  val touchConfiguration = remember(viewConfiguration) {
    object : ViewConfiguration by viewConfiguration {
      override val minimumTouchTargetSize = DpSize(
        maxOf(48.dp, viewConfiguration.minimumTouchTargetSize.width),
        maxOf(48.dp, viewConfiguration.minimumTouchTargetSize.height),
      )
    }
  }
  CompositionLocalProvider(LocalViewConfiguration provides touchConfiguration) {
    Row(
      modifier.widthIn(min = 48.dp, max = 240.dp)
        .fluidPressable(onClick = onClick, role = Role.Button, feedback = FluidPressFeedback.Expand, grip = FluidPressGrip.YieldsToScroll)
        .semantics { contentDescription = "$name, ${labels.fileKind(kind)}" }
        .clip(ContinuousCornerShape(FluidRadius.Control))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(Modifier.clearAndSetSemantics {}) { FileIcon(kind, 24) }
      Box(Modifier.weight(1f, fill = false).clearAndSetSemantics {}) { FileName(name, 1) }
    }
  }
}

@Composable
private fun FileName(name: String, lines: Int) {
  val style = MaterialTheme.typography.bodyMedium
  val measurer = rememberTextMeasurer()
  BoxWithConstraints {
    val width = constraints.maxWidth
    val display = remember(name, width, style, lines, measurer) {
      fun fits(value: String) = !measurer.measure(value, style, maxLines = lines, constraints = Constraints(maxWidth = width)).hasVisualOverflow
      if (fits(name)) name else {
        var low = 0
        var high = name.codePointCount(0, name.length)
        while (low < high) {
          val mid = (low + high + 1) / 2
          if (fits(fluidTruncateFileName(name, mid))) low = mid else high = mid - 1
        }
        fluidTruncateFileName(name, low)
      }
    }
    Text(display, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = lines)
  }
}

@Composable
private fun FileProgress(status: FluidFileStatus.Transferring) {
  // Unico scope che legge il valore: la riga e il nome non vengono ricomposti a ogni percentuale.
  val progress = status.progress()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
  val semantics = Modifier.semantics {
    progressBarRangeInfo = progress?.let { ProgressBarRangeInfo(it, 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate
  }
  if (progress != null) FluidProgressBar(progress = { progress }, modifier = semantics)
  else if (LocalFluidMotionPolicy.current.reducedMotion) FluidProgressBar(progress = { 0.35f }, modifier = semantics)
  else FluidIndeterminateBar(modifier = semantics)
}

@Composable
private fun FileAction(label: String, icon: ImageVector, action: () -> Unit) {
  Box(Modifier.size(48.dp).fluidPressable(onClick = action, role = Role.Button, grip = FluidPressGrip.YieldsToScroll)
    .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun FileIcon(kind: FluidFileKind, size: Int) {
  val icon = when (kind) {
    FluidFileKind.Image -> Icons.Outlined.Image
    FluidFileKind.Pdf -> Icons.Outlined.PictureAsPdf
    FluidFileKind.Document -> Icons.Outlined.Description
    FluidFileKind.Spreadsheet -> Icons.Outlined.TableChart
    FluidFileKind.Presentation -> Icons.Outlined.Slideshow
    FluidFileKind.Archive -> Icons.Outlined.FolderZip
    FluidFileKind.Audio -> Icons.Outlined.AudioFile
    FluidFileKind.Video -> Icons.Outlined.VideoFile
    FluidFileKind.Text -> Icons.Outlined.Code
    FluidFileKind.Executable -> Icons.Outlined.WarningAmber
    FluidFileKind.Other -> Icons.AutoMirrored.Outlined.InsertDriveFile
  }
  FluidToneTile(if (kind == FluidFileKind.Executable) FluidTone.Warning else FluidTone.Primary, size = size.dp) {
    Icon(icon, contentDescription = null)
  }
}
