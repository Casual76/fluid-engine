package dev.antigravity.fluidengine.ai.mcp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Le parole della pagina «Connettori» (4.0, F9.2), in italiano di serie: un'app in un'altra lingua le passa
 * tutte. Le frasi spiegano cosa succede con parole di tutti i giorni (niente "MCP", niente "OAuth").
 */
@Immutable
data class FluidConnectorsLabels(
  val title: String = "Connettori",
  val intro: String = "I connettori collegano l'assistente ai servizi che usi (il calendario, le attivita', la casa). " +
    "I loro strumenti si usano solo quando servono, e prima di fare qualcosa l'assistente te lo chiede.",
  val yours: String = "I tuoi connettori",
  val emptyTitle: String = "Nessun connettore",
  val emptyDetail: String = "Aggiungine uno con l'indirizzo del servizio o con il suo QR.",
  val add: String = "Aggiungi",
  val addHeader: String = "Aggiungi un connettore",
  val addressLabel: String = "Indirizzo del servizio",
  val addressPlaceholder: String = "https://…",
  val addressInvalid: String = "Serve un indirizzo https",
  val scanQr: String = "Scansiona un QR",
  val scanQrDetail: String = "Il QR del servizio porta il suo indirizzo",
  val statusNew: String = "Da collegare",
  val statusReady: String = "Collegato",
  val statusSignIn: String = "Serve l'accesso",
  val statusError: String = "Non raggiungibile",
  val tools: (Int) -> String = { n -> if (n == 1) "1 strumento" else "$n strumenti" },
  val signIn: String = "Entra",
  val signOut: String = "Esci",
  val refresh: String = "Aggiorna",
  val remove: String = "Togli connettore",
  val removeConfirm: String = "Togliere il connettore? L'assistente non potra' piu' usarlo.",
  val removeYes: String = "Togli",
  val cancel: String = "Annulla",
  val working: String = "In corso…",
  val toolsHeader: String = "Strumenti",
  val instructionsHeader: String = "Note del servizio",
  val instructionsDetail: String = "Le scrive il servizio: le leggi tu, all'assistente non arrivano.",
  val policyAsk: String = "Chiedi",
  val policyAlways: String = "Sempre",
  val policyOff: String = "Spento",
  val policyLabel: String = "Prima di usarlo",
  val warning: String = "Descrizione sospetta: all'assistente arriva solo il nome",
  val heldNew: String = "Nuovo: l'assistente non lo usa finche' non lo approvi",
  val heldChanged: String = "Cambiato dal servizio: rivedilo e approvalo",
  val approve: String = "Approva",
  val readOnly: String = "legge e basta, secondo il servizio",
  val writes: String = "puo' cambiare dati, secondo il servizio",
)

/**
 * La pagina «Connettori» (4.0, F9.2): l'elenco dei connettori, l'aggiunta da indirizzo o da QR, l'accesso
 * con l'account, gli strumenti di ognuno com'e' scritto dal servizio (con gli avvisi delle difese) e la
 * regola di ognuno (chiedi, sempre, spento), il tasto per toglierlo. Sul vetro dell'engine: la pagina e'
 * una [FluidScreen] con i gruppi di righe di sempre.
 *
 * - [onOpenUrl] apre l'indirizzo dell'accesso nel browser o in una Custom Tab (mai in una WebView
 *   dell'app: la specifica OAuth lo chiede). Al ritorno l'app chiama [McpConnectors.completeSignIn] con
 *   l'indirizzo con cui e' stata riaperta.
 * - [onScanQr], se c'e', apre lo scanner dell'app e torna il testo letto; la pagina lo legge con
 *   [McpConnectorLink.parse]. Senza, la riga del QR non c'e'.
 *
 * Il dettaglio di un connettore si apre nella stessa pagina e si chiude col back. Con «Riduci movimento»
 * il segno di lavoro e' una parola ferma al posto della rotella.
 */
@Composable
fun FluidConnectorsPage(
  connectors: McpConnectors,
  onOpenUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  onScanQr: (suspend () -> String?)? = null,
  labels: FluidConnectorsLabels = FluidConnectorsLabels(),
) {
  val views by remember(connectors) { connectors.views }.collectAsState(initial = emptyList())
  val scope = rememberCoroutineScope()
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf<String?>(null) }
  var address by rememberSaveable { mutableStateOf("") }
  var addressError by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var confirmRemove by remember { mutableStateOf(false) }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion

  fun run(tag: String, block: suspend () -> Unit) {
    if (busy != null) return
    busy = tag
    message = null
    scope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        message = e.message ?: labels.statusError
      } finally {
        busy = null
      }
    }
  }

  val current = views.firstOrNull { it.connector.id == selected }
  BackHandler(enabled = current != null) {
    selected = null
    confirmRemove = false
  }

  FluidScreen(
    title = current?.connector?.name ?: labels.title,
    modifier = modifier,
    subtitle = current?.let { statusLine(it, labels) },
    onBack = if (current != null) ({ selected = null; confirmRemove = false }) else onBack,
  ) {
    if (current == null) {
      item(key = "intro") {
        Text(labels.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
      }
      item(key = "yours") { FluidSectionHeader(labels.yours) }
      item(key = "list") {
        if (views.isEmpty()) {
          FluidEmptyState(title = labels.emptyTitle, detail = labels.emptyDetail)
        } else {
          FluidListGroup(glass = true) {
            views.forEachIndexed { index, view ->
              if (index > 0) FluidListDivider()
              FluidListRow(
                title = view.connector.name,
                subtitle = statusLine(view, labels),
                meta = hostOf(view.connector.url),
                tone = toneOf(view.status),
                onClick = { selected = view.connector.id },
              )
            }
          }
        }
      }
      item(key = "add-header") { FluidSectionHeader(labels.addHeader) }
      item(key = "add") {
        FluidListGroup(glass = true) {
          Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FluidTextField(
              value = address,
              onValueChange = { address = it; addressError = false },
              label = labels.addressLabel,
              placeholder = labels.addressPlaceholder,
              isError = addressError,
              supportingText = if (addressError) labels.addressInvalid else null,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = {
                val link = McpConnectorLink.parse(address)
                if (link == null) addressError = true else run("add") { selected = connectors.add(link); address = "" }
              }),
            )
            FluidButton(
              text = labels.add,
              onClick = {
                val link = McpConnectorLink.parse(address)
                if (link == null) addressError = true else run("add") { selected = connectors.add(link); address = "" }
              },
              enabled = address.isNotBlank() && busy == null,
              loading = busy == "add" && !reduced,
              fillWidth = true,
            )
            WorkingLine(busy, message, labels)
          }
          if (onScanQr != null) {
            FluidListDivider()
            FluidListRow(
              title = labels.scanQr,
              subtitle = labels.scanQrDetail,
              onClick = {
                run("qr") {
                  val text = onScanQr() ?: return@run
                  val link = McpConnectorLink.parse(text)
                  if (link == null) {
                    address = text
                    addressError = true
                  } else {
                    selected = connectors.add(link)
                  }
                }
              },
            )
          }
        }
      }
    } else {
      current.connector.instructions?.takeIf { it.isNotBlank() }?.let { instructions ->
        item(key = "instructions-header") { FluidSectionHeader(labels.instructionsHeader, detail = labels.instructionsDetail) }
        item(key = "instructions") {
          FluidListGroup(glass = true) {
            Text(instructions, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(14.dp))
          }
        }
      }
      // Le azioni del dettaglio, che hanno bisogno di [connectors] e di [onOpenUrl].
      item(key = "actions") {
        DetailActions(current, connectors, labels, busy, confirmRemove, onConfirmRemove = { confirmRemove = it }, onOpenUrl = onOpenUrl, onRemoved = { selected = null }) { tag, block -> run(tag, block) }
        WorkingLine(busy, message, labels)
      }
      item(key = "tools-header") { FluidSectionHeader(labels.toolsHeader, detail = labels.tools(current.tools.size)) }
      item(key = "tools") { ToolsGroup(current, connectors, labels) { tag, block -> run(tag, block) } }
    }
  }
}

/** Lo stato di un connettore in una riga: "Collegato · 5 strumenti". */
private fun statusLine(view: McpConnectorView, labels: FluidConnectorsLabels): String {
  val status = when (view.status) {
    McpConnectorStatus.NEW -> labels.statusNew
    McpConnectorStatus.READY -> labels.statusReady
    McpConnectorStatus.NEEDS_SIGN_IN -> labels.statusSignIn
    McpConnectorStatus.ERROR -> labels.statusError
  }
  return if (view.status == McpConnectorStatus.READY) "$status · ${labels.tools(view.tools.size)}" else status
}

private fun toneOf(status: McpConnectorStatus): FluidTone = when (status) {
  McpConnectorStatus.READY -> FluidTone.Success
  McpConnectorStatus.NEEDS_SIGN_IN -> FluidTone.Warning
  McpConnectorStatus.ERROR -> FluidTone.Danger
  McpConnectorStatus.NEW -> FluidTone.Neutral
}

private fun hostOf(url: String): String = runCatching { java.net.URL(url).host }.getOrDefault(url)

@Composable
private fun DetailActions(
  view: McpConnectorView,
  connectors: McpConnectors,
  labels: FluidConnectorsLabels,
  busy: String?,
  confirmRemove: Boolean,
  onConfirmRemove: (Boolean) -> Unit,
  onOpenUrl: (String) -> Unit,
  onRemoved: () -> Unit,
  run: (String, suspend () -> Unit) -> Unit,
) {
  val id = view.connector.id
  FluidListGroup(glass = true) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (view.signedIn) {
          FluidButton(labels.signOut, onClick = { run("signout") { connectors.signOut(id) } }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Medium, enabled = busy == null)
        } else if (view.status == McpConnectorStatus.NEEDS_SIGN_IN) {
          FluidButton(labels.signIn, onClick = { run("signin") { onOpenUrl(connectors.beginSignIn(id)) } }, enabled = busy == null)
        }
        FluidButton(labels.refresh, onClick = { run("refresh") { connectors.refresh(id) } }, style = FluidButtonStyle.Plain, size = FluidButtonSize.Medium, enabled = busy == null)
      }
      if (!confirmRemove) {
        FluidButton(labels.remove, onClick = { onConfirmRemove(true) }, style = FluidButtonStyle.Destructive, fillWidth = true, enabled = busy == null)
      } else {
        Text(labels.removeConfirm, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FluidButton(labels.cancel, onClick = { onConfirmRemove(false) }, style = FluidButtonStyle.Plain)
          FluidButton(labels.removeYes, onClick = {
            run("remove") {
              connectors.remove(id)
              onConfirmRemove(false)
              onRemoved()
            }
          }, style = FluidButtonStyle.Destructive)
        }
      }
    }
  }
}

@Composable
private fun ToolsGroup(view: McpConnectorView, connectors: McpConnectors, labels: FluidConnectorsLabels, run: (String, suspend () -> Unit) -> Unit) {
  if (view.tools.isEmpty()) return
  val id = view.connector.id
  FluidListGroup(glass = true) {
    view.tools.forEachIndexed { index, tool ->
      if (index > 0) FluidListDivider()
      Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val title = McpGuard.clean(tool.info.title ?: tool.info.annotations.title ?: tool.info.name)
        val hint = if (tool.info.annotations.readOnlyHint == true) labels.readOnly else labels.writes
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (tool.review.userDescription.isNotBlank()) {
          Text(tool.review.userDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6)
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (tool.review.replaced) {
          Text(
            labels.warning,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { contentDescription = "${labels.warning}: $title" },
          )
        }
        when (tool.held) {
          McpHeld.NEW, McpHeld.CHANGED -> {
            Text(if (tool.held == McpHeld.NEW) labels.heldNew else labels.heldChanged, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            FluidButton(labels.approve, onClick = { run("approve") { connectors.approve(id, tool.info.name) } }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
          }
          else -> {
            // «Sempre» solo per chi legge e basta, secondo il servizio: una scrittura chiede sempre.
            val options = if (tool.info.annotations.readOnlyHint()) listOf(McpToolPolicy.ASK, McpToolPolicy.ALWAYS, McpToolPolicy.OFF) else listOf(McpToolPolicy.ASK, McpToolPolicy.OFF)
            val label: (McpToolPolicy) -> String = { p -> when (p) { McpToolPolicy.ASK -> labels.policyAsk; McpToolPolicy.ALWAYS -> labels.policyAlways; McpToolPolicy.OFF -> labels.policyOff } }
            FluidSegmentedControl(
              options = options,
              selected = tool.policy,
              onSelect = { p -> run("policy") { connectors.setPolicy(id, tool.info.name, p) } },
              modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "${labels.policyLabel}: $title"
                stateDescription = label(tool.policy)
              },
              label = label,
            )
          }
        }
      }
    }
  }
}

/** Il segno di lavoro e l'ultimo errore. Con «Riduci movimento» una parola ferma al posto della rotella. */
@Composable
private fun WorkingLine(busy: String?, message: String?, labels: FluidConnectorsLabels) {
  if (busy == null && message == null) return
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  Row(
    Modifier.fillMaxWidth().padding(8.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (busy != null) {
      if (!reduced) FluidSpinner(modifier = Modifier.semantics { contentDescription = labels.working })
      Text(labels.working, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (message != null) {
      Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
  }
}
