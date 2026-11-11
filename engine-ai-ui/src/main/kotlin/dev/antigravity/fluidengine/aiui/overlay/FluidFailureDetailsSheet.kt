package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.chat.fluidCopyToClipboard
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * Il foglio "Dettagli" di una risposta fallita: vetro, dal padrone di casa dei modali alla radice
 * (lo stesso del selettore del modello), con in fondo "Copia". Va nella pagina e non nell'item di una
 * lista: un portale dentro un item che esce di vista si chiuderebbe da solo.
 *
 * @param report cosa e' successo; null chiude il foglio (con la sua animazione di uscita).
 */
@Composable
fun FluidFailureDetailsSheet(
  report: FluidFailureReport?,
  onDismiss: () -> Unit,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
) {
  val context = LocalContext.current
  FluidGlassModalPortal(
    item = report,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = strings.failureDetailsTitle,
    footer = { current ->
      // Il foglio dell'engine non mette margini ne' scavalca la barra dei gesti: li mette chi lo usa.
      // Senza, "Copia" andava da bordo a bordo e sotto la barra di sistema.
      Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = SheetMargin, end = SheetMargin, bottom = 12.dp)) {
        FluidButton(
          text = strings.failureCopy,
          onClick = { fluidCopyToClipboard(context, current.asText(strings), label = strings.failureDetailsTitle) },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Medium,
          fillWidth = true,
          leading = { FluidIcon(FluidIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
      }
    },
  ) { current -> FluidFailureDetailsContent(current, strings) }
}

/** Il contenuto del foglio, a se' per le anteprime. */
@Composable
fun FluidFailureDetailsContent(report: FluidFailureReport, strings: FluidAssistantStrings = FluidAssistantStrings()) {
  val rows = buildList {
    // Senza servizio (nessuna chiave, un errore prima di chiamarne uno) la riga non c'e'.
    report.provider?.let { add(DetailRow(strings.failureService, it.label, report.model.orEmpty())) }
    report.kind?.let { add(DetailRow(strings.failureReason, FluidAssistantTexts.failureTitle(it, strings), report.reasonLine(strings).orEmpty())) }
    if (report.httpCode != null || !report.providerMessage.isNullOrBlank()) {
      add(
        DetailRow(
          strings.failureServiceAnswer,
          report.httpCode?.let { fluidFormat(strings.failureCode, it) } ?: strings.failureNoCode,
          report.providerMessage.orEmpty(),
        ),
      )
    }
    report.chain?.let { add(DetailRow(strings.failureTried, it, report.chainReasons(strings).orEmpty())) }
  }
  Column(Modifier.fillMaxWidth().padding(horizontal = SheetMargin)) {
    Text(strings.failureWhatHappened, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    Text(report.summary(strings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    if (rows.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      FluidListGroup {
        rows.forEachIndexed { index, row ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = row.value, subtitle = row.detail, eyebrow = row.label)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

/** Il margine ai lati del foglio, lo stesso del foglio d'esempio dell'engine. */
private val SheetMargin = 20.dp

private data class DetailRow(val label: String, val value: String, val detail: String)
