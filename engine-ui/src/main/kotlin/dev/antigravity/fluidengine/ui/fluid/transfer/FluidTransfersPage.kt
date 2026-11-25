package dev.antigravity.fluidengine.ui.fluid.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidConditionPhrases
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferControls
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidProgressBar
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutcome
import dev.antigravity.fluidengine.ui.fluid.activity.LocalFluidActivityLabels
import dev.antigravity.fluidengine.ui.fluid.icons.CloudDownload
import dev.antigravity.fluidengine.ui.fluid.icons.CloudUpload
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.theme.FluidEmptyPreset
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

// ================================================================================================================
// La pagina «Trasferimenti» (F8.20, `centro-trasferimenti`)
//
// Gli elenchi di download di Safari e dell'App Store: una riga per trasferimento, col nome, la barra, «12,4 MB di 48 MB ·
// 2,1 MB/s · ancora 17 s», e il tasto che serve adesso (Pausa, Riprendi, Riprova, Fallo comunque). Legge il modello di
// foundation (`FluidTransferItem`): la coda e' `engine-transfer`, o una coda dell'app che implementa
// `FluidTransferControls`. Fuori dalla pagina il lavoro si vede nella pillola ([FluidTransferActivity]).
// ================================================================================================================

/** Le parole dei trasferimenti, di serie in italiano ([english] per l'inglese). Si cambiano con [LocalFluidTransferLabels]. */
@Immutable
data class FluidTransferLabels(
  val title: String = "Trasferimenti",
  val inProgress: String = "In corso",
  val failedSection: String = "Non riusciti",
  val finishedSection: String = "Completati",
  val queued: String = "In coda",
  val waiting: String = "In attesa",
  val paused: String = "In pausa",
  val failed: String = "Non riuscito",
  val completed: String = "Completato",
  val cancelled: String = "Annullato",
  val pause: String = "Pausa",
  val resume: String = "Riprendi",
  val retry: String = "Riprova",
  val force: String = "Fallo comunque",
  val cancel: String = "Annulla",
  val clearFinished: String = "Togli i completati",
  val of: String = "di",
  val perSecond: (String) -> String = { "$it/s" },
  val remaining: (String) -> String = { "ancora $it" },
  /** Un tentativo automatico dopo un errore che passa: «Nuovo tentativo fra poco (2 di 3)». */
  val retrying: (attempt: Int, of: Int) -> String = { a, n -> "Nuovo tentativo fra poco ($a di $n)" },
  /** Il server non riprende: dopo una pausa il trasferimento ricomincia. */
  val restarts: String = "Ricomincia da capo dopo una pausa",
  val failureNetwork: String = "La connessione si è interrotta",
  val failureServer: (Int?) -> String = { code -> if (code != null) "Il server non risponde ($code)" else "Il server non risponde" },
  val failureNotFound: String = "Il file non c'è più",
  val failureUnauthorized: String = "Accesso negato",
  val failureStorage: String = "Spazio o file non disponibile",
  val failureIntegrity: String = "Il file arrivato non è quello atteso",
  val failureUnknown: String = "Qualcosa non è andato",
  val empty: String = "Nessun trasferimento",
  val emptyDetail: String = "Gli scaricamenti e i caricamenti lunghi compaiono qui, anche con l'app chiusa.",
  /** Il riassunto della pillola con piu' trasferimenti: «3 trasferimenti». */
  val count: (Int) -> String = { n -> if (n == 1) "Un trasferimento" else "$n trasferimenti" },
  val conditions: FluidConditionPhrases = FluidConditionPhrases(),
  /** L'azione di TalkBack che apre la riga. */
  val open: String = "Apri",
) {
  /** Il motivo di [failure], in parole. */
  fun failure(failure: FluidTransferFailure?): String = when (failure?.kind) {
    null -> failed
    FluidTransferFailure.Network -> failureNetwork
    FluidTransferFailure.Server -> failureServer(failure.code)
    FluidTransferFailure.NotFound -> failureNotFound
    FluidTransferFailure.Unauthorized -> failureUnauthorized
    FluidTransferFailure.Storage -> failureStorage
    FluidTransferFailure.Integrity -> failureIntegrity
    else -> failureUnknown
  }

  companion object {
    fun english(): FluidTransferLabels = FluidTransferLabels(
      title = "Transfers",
      inProgress = "In progress",
      failedSection = "Failed",
      finishedSection = "Completed",
      queued = "Queued",
      waiting = "Waiting",
      paused = "Paused",
      failed = "Failed",
      completed = "Completed",
      cancelled = "Cancelled",
      pause = "Pause",
      resume = "Resume",
      retry = "Retry",
      force = "Do it anyway",
      cancel = "Cancel",
      clearFinished = "Clear completed",
      of = "of",
      perSecond = { "$it/s" },
      remaining = { "$it left" },
      retrying = { a, n -> "Retrying soon ($a of $n)" },
      restarts = "Starts over after a pause",
      failureNetwork = "The connection dropped",
      failureServer = { code -> if (code != null) "The server isn't responding ($code)" else "The server isn't responding" },
      failureNotFound = "The file is gone",
      failureUnauthorized = "Access denied",
      failureStorage = "Storage or file unavailable",
      failureIntegrity = "The file isn't the one expected",
      failureUnknown = "Something went wrong",
      empty = "No transfers",
      emptyDetail = "Long downloads and uploads show up here, even with the app closed.",
      count = { n -> if (n == 1) "1 transfer" else "$n transfers" },
      conditions = FluidConditionPhrases.english(),
      open = "Open",
    )
  }
}

/** Le parole dei trasferimenti, di serie in italiano. */
val LocalFluidTransferLabels = staticCompositionLocalOf { FluidTransferLabels() }

/** I tag per le prove. */
object FluidTransferTags {
  fun row(id: String): String = "fluid:transfer:$id"
  fun action(id: String): String = "fluid:transfer:$id:action"
  fun cancel(id: String): String = "fluid:transfer:$id:cancel"
  const val ClearFinished: String = "fluid:transfer:clear"
}

/**
 * Le frasi di una riga, pure (le prove le leggono): [amount] «12,4 MB di 48 MB», [pace] «2,1 MB/s · ancora 17 s» o il
 * perche' dell'attesa, [status] lo stato in parole («In attesa · Aspetto il Wi-Fi»).
 */
@Immutable
data class FluidTransferPhrases(
  val amount: String,
  val pace: String?,
  val status: String,
  /**
   * La riga sotto la barra quando il trasferimento **non** corre (F8.20b): una frase sola, che si tronca invece di
   * accavallarsi. «In pausa · 12,7 MB di 48 MB», «Aspetto il Wi-Fi · Rete a consumo», «Non riuscito · Il server non
   * risponde (503)», «Completato · 48 MB». Mentre corre la riga sono [amount] e [pace], coi numeri che scorrono.
   */
  val line: String = listOfNotNull(status, pace).joinToString(" · "),
)

/** Le frasi della riga di [item]. Pura. */
fun fluidTransferPhrases(item: FluidTransferItem, labels: FluidTransferLabels, maxRetries: Int = 3): FluidTransferPhrases {
  val amount = if (item.bytesTotal > 0L) {
    "${FluidFormat.bytes(item.bytesDone)} ${labels.of} ${FluidFormat.bytes(item.bytesTotal)}"
  } else {
    FluidFormat.bytes(item.bytesDone.coerceAtLeast(0L))
  }
  val state = item.state
  val pace = when {
    state == FluidTransferState.Running -> {
      val speed = item.bytesPerSecond.takeIf { it > 0L }?.let { labels.perSecond(FluidFormat.bytes(it)) }
      val left = item.remainingMillis?.let { labels.remaining(FluidFormat.duration(Duration.ofMillis(it.coerceAtLeast(1_000L)), maxUnits = 1)) }
      listOfNotNull(speed, left).joinToString(" · ").ifEmpty { null }
    }
    state == FluidTransferState.Waiting -> labels.conditions.waitingFor(item.waitingFor)
    state == FluidTransferState.Failed -> labels.failure(item.failure)
    state == FluidTransferState.Queued && item.failure != null -> labels.retrying(item.attempts, maxRetries)
    state == FluidTransferState.Paused && !item.resumable -> labels.restarts
    else -> null
  }
  val status = when (state) {
    FluidTransferState.Queued -> labels.queued
    FluidTransferState.Running -> item.progress?.let { FluidFormat.percent(it.toDouble()) } ?: labels.inProgress
    FluidTransferState.Waiting -> listOfNotNull(labels.waiting, item.waitingFor.firstOrNull()?.let(labels.conditions::reason)).joinToString(" · ")
    FluidTransferState.Paused -> labels.paused
    FluidTransferState.Failed -> labels.failed
    FluidTransferState.Completed -> labels.completed
    FluidTransferState.Cancelled -> labels.cancelled
    else -> state.name
  }
  val total = item.bytesTotal.takeIf { it > 0L }?.let(FluidFormat::bytes)
  val line = when (state) {
    FluidTransferState.Paused -> listOfNotNull(labels.paused, amount.takeIf { item.bytesDone > 0L }, pace)
    // Il perche' prima, lo stato dopo: «Aspetto il Wi-Fi · Rete a consumo» (il «In attesa» lo dice gia' la frase).
    FluidTransferState.Waiting -> listOfNotNull(pace, item.waitingFor.firstOrNull()?.let(labels.conditions::reason))
      .ifEmpty { listOf(labels.waiting) }
    FluidTransferState.Completed -> listOfNotNull(labels.completed, total ?: amount)
    else -> listOfNotNull(status, pace)
  }.distinct().joinToString(" · ")
  return FluidTransferPhrases(amount, pace, status, line)
}

/**
 * **La pagina «Trasferimenti»** (F8.20): i trasferimenti in corso (anche in coda, in attesa e in pausa), quelli non
 * riusciti e quelli completati, ognuno con la sua barra, il suo stato e il tasto che serve adesso.
 *
 * ```
 * val items by transfers.items.collectAsState()                          // engine-transfer
 * FluidTransfersPage(items, controls = transfers, onBack = { nav.popBackStack() })
 * ```
 *
 * - **I numeri** (byte, velocita', tempo che manca) cambiano da soli, quindi scorrono sfocati ([FluidNumericText]) e al
 *   piu' una volta al secondo, come in Safari; la barra segue ogni avanzamento.
 * - **Un trasferimento che aspetta** dice il perche' («Aspetto il Wi-Fi · Rete a consumo») e offre «Fallo comunque»
 *   quando si puo' forzare; uno non riuscito dice il motivo e offre «Riprova».
 * - **TalkBack**: una riga e' un nodo solo (nome, stato, avanzamento) con le azioni come azioni di accessibilita'.
 *
 * @param onOpen il tocco su un trasferimento completato (aprire il file); null: la riga non si tocca.
 */
@Composable
fun FluidTransfersPage(
  items: List<FluidTransferItem>,
  controls: FluidTransferControls,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  title: String? = null,
  onOpen: ((FluidTransferItem) -> Unit)? = null,
) {
  val labels = LocalFluidTransferLabels.current
  val running = items.filter { it.state.active || it.state == FluidTransferState.Paused }
  val failed = items.filter { it.state == FluidTransferState.Failed }
  val finished = items.filter { it.state.finished }.sortedByDescending { it.finishedAtMillis ?: 0L }
  FluidScreen(title = title ?: labels.title, modifier = modifier, onBack = onBack) {
    if (items.isEmpty()) {
      item(key = "vuoto") {
        FluidEmptyState(FluidEmptyPreset.Empty, title = labels.empty, detail = labels.emptyDetail)
      }
    }
    if (running.isNotEmpty()) {
      item(key = "in-corso") {
        FluidTransferSection(labels.inProgress, running, controls, onOpen)
      }
    }
    if (failed.isNotEmpty()) {
      item(key = "non-riusciti") {
        FluidTransferSection(labels.failedSection, failed, controls, onOpen)
      }
    }
    if (finished.isNotEmpty()) {
      item(key = "completati") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidTransferSection(labels.finishedSection, finished, controls, onOpen)
          FluidButton(
            text = labels.clearFinished,
            onClick = controls::clearFinished,
            style = FluidButtonStyle.Plain,
            size = FluidButtonSize.Small,
            modifier = Modifier.testTag(FluidTransferTags.ClearFinished),
          )
        }
      }
    }
  }
}

@Composable
private fun FluidTransferSection(
  title: String,
  items: List<FluidTransferItem>,
  controls: FluidTransferControls,
  onOpen: ((FluidTransferItem) -> Unit)?,
) {
  Column {
    FluidSectionHeader(title = title)
    FluidListGroup {
      items.forEachIndexed { index, item ->
        if (index > 0) FluidListDivider()
        androidx.compose.runtime.key(item.id) {
          val open = onOpen?.takeIf { item.state == FluidTransferState.Completed }
          FluidTransferRow(item, controls, onOpen = open?.let { { it(item) } })
        }
      }
    }
  }
}

/**
 * Una riga di trasferimento, per una lista dell'app: l'icona del verso, il nome, la barra, i numeri e il tasto che serve
 * adesso. La stessa di [FluidTransfersPage].
 */
@Composable
fun FluidTransferRow(
  item: FluidTransferItem,
  controls: FluidTransferControls,
  modifier: Modifier = Modifier,
  onOpen: (() -> Unit)? = null,
) {
  val labels = LocalFluidTransferLabels.current
  val scheme = MaterialTheme.colorScheme
  // I numeri al piu' una volta al secondo (Safari); lo stato subito.
  val latest by rememberUpdatedState(item)
  var shown by remember { mutableStateOf(item) }
  LaunchedEffect(Unit) {
    snapshotFlow { latest }.conflate().collect { next ->
      val steady = next.state == shown.state
      shown = next
      if (steady) delay(TextIntervalMillis)
    }
  }
  val phrases = fluidTransferPhrases(shown.copy(state = item.state, failure = item.failure, waitingFor = item.waitingFor), labels)
  val action = fluidTransferAction(item, labels)
  val spoken = listOfNotNull(item.title, phrases.status, phrases.amount, phrases.pace).joinToString(", ")
  val accessibility = buildList {
    if (action != null) add(CustomAccessibilityAction(action.label) { action.run(controls, item.id); true })
    if (!item.state.finished && item.state != FluidTransferState.Failed) {
      add(CustomAccessibilityAction(labels.cancel) { controls.cancel(item.id); true })
    }
    if (onOpen != null) add(CustomAccessibilityAction(labels.open) { onOpen(); true })
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .testTag(FluidTransferTags.row(item.id))
      .then(if (onOpen != null) Modifier.fluidPressable(onClick = onOpen, pressedScale = 1f) else Modifier)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidIcon(
      if (item.direction == FluidTransferDirection.Upload) FluidIcons.CloudUpload else FluidIcons.CloudDownload,
      null,
      size = 22.dp,
      tint = if (item.state == FluidTransferState.Failed) scheme.error else scheme.primary,
    )
    Spacer(Modifier.width(12.dp))
    Column(
      modifier = Modifier
        .weight(1f)
        .clearAndSetSemantics {
          contentDescription = spoken
          stateDescription = phrases.status
          customActions = accessibility
        },
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      item.subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (!item.state.finished && item.state != FluidTransferState.Failed) {
        val fraction = item.progress
        FluidProgressBar(
          progress = { fraction ?: 0f },
          modifier = Modifier
            .padding(vertical = 2.dp)
            .alpha(if (item.state == FluidTransferState.Running) 1f else DimmedAlpha),
        )
      }
      if (item.state == FluidTransferState.Running) {
        // Mentre corre: i byte e il passo scorrono sfocati. Il passo cede lo spazio (si taglia al bordo) invece di
        // passare sopra ai byte quando la riga e' stretta (il tasto a destra, una lingua lunga).
        Row(verticalAlignment = Alignment.CenterVertically) {
          FluidNumericText(
            text = phrases.amount,
            value = shown.bytesDone.toDouble(),
            style = FluidTextStyles.numeric.merge(MaterialTheme.typography.bodySmall),
            color = scheme.onSurfaceVariant,
          )
          phrases.pace?.let { pace ->
            Text(
              " · ",
              style = MaterialTheme.typography.bodySmall,
              color = scheme.onSurfaceVariant,
              maxLines = 1,
            )
            FluidNumericText(
              text = pace,
              value = shown.bytesPerSecond.toDouble(),
              style = FluidTextStyles.numeric.merge(MaterialTheme.typography.bodySmall),
              color = scheme.onSurfaceVariant,
              modifier = Modifier.weight(1f, fill = false).clipToBounds(),
            )
          }
        }
      } else {
        // Fermo: una frase sola, che si tronca. Prima erano due testi affiancati che, stretti, si accavallavano
        // («In attesa · Rete a consumoAspetto il Wi-Fi», Tab S9 25/11) e la pausa non diceva «In pausa».
        Text(
          phrases.line,
          style = MaterialTheme.typography.bodySmall,
          color = if (item.state == FluidTransferState.Failed) scheme.error else scheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (action != null) {
      Spacer(Modifier.width(8.dp))
      FluidButton(
        text = action.label,
        onClick = { action.run(controls, item.id) },
        style = if (action.primary) FluidButtonStyle.Tinted else FluidButtonStyle.Plain,
        size = FluidButtonSize.Small,
        modifier = Modifier.widthIn(min = 44.dp).testTag(FluidTransferTags.action(item.id)),
      )
    }
  }
}

/** Il tasto che serve adesso a [item]. */
@Immutable
private class FluidTransferAction(val label: String, val primary: Boolean, val run: (FluidTransferControls, String) -> Unit)

private fun fluidTransferAction(item: FluidTransferItem, labels: FluidTransferLabels): FluidTransferAction? = when (item.state) {
  FluidTransferState.Running, FluidTransferState.Queued -> FluidTransferAction(labels.pause, false) { c, id -> c.pause(id) }
  FluidTransferState.Paused -> FluidTransferAction(labels.resume, true) { c, id -> c.resume(id) }
  FluidTransferState.Waiting -> if (item.canForce) FluidTransferAction(labels.force, true) { c, id -> c.force(id) }
  else FluidTransferAction(labels.pause, false) { c, id -> c.pause(id) }
  FluidTransferState.Failed -> FluidTransferAction(labels.retry, true) { c, id -> c.retry(id) }
  else -> null
}

/**
 * **La pillola dei trasferimenti**: pubblica in [activities] l'attivita' dei trasferimenti in giro ([overview]) e la
 * tiene aggiornata; quando non ce ne sono piu' la chiude con l'esito (la spunta, o il motivo dell'errore). Si mette alla
 * radice, accanto all'host della pillola: dentro l'app si vede nella barra, fuori la porta `engine-notify` (F8.19).
 *
 * L'avanzamento la pillola lo legge da uno stato (niente ripubblicazioni a ogni byte); l'attivita' si ripubblica solo
 * quando cambia cosa dice (il titolo, la pausa, l'attesa).
 *
 * @param onOpen «Apri» nella card: di solito la pagina «Trasferimenti».
 */
@Composable
fun FluidTransferActivity(
  activities: FluidActivityHostState,
  overview: FluidTransferOverview,
  controls: FluidTransferControls,
  onOpen: (() -> Unit)? = null,
  id: String = FluidTransferActivityId,
) {
  val labels = LocalFluidTransferLabels.current
  val activityLabels = LocalFluidActivityLabels.current
  val progress = remember { mutableFloatStateOf(0f) }
  val lead = overview.running ?: overview.active.firstOrNull() ?: overview.paused.firstOrNull()
  val fraction = overview.progress ?: lead?.progress ?: 0f
  // L'avanzamento lo legge la pillola mentre disegna: si scrive dopo la composizione, non durante.
  SideEffect { progress.floatValue = fraction }
  val face = lead?.let { item ->
    FluidTransferFace(
      leadId = item.id,
      title = if (overview.active.size + overview.paused.size > 1) labels.count(overview.active.size + overview.paused.size) else item.title,
      upload = item.direction == FluidTransferDirection.Upload,
      paused = overview.active.isEmpty(),
      detail = when (item.state) {
        FluidTransferState.Waiting -> labels.conditions.waitingFor(item.waitingFor)
        FluidTransferState.Paused -> labels.paused
        else -> item.title.takeIf { overview.active.size > 1 }
      },
    )
  }
  val last = remember { mutableStateOf<FluidTransferItem?>(null) }
  // L'ultimo trasferimento visto, per l'esito quando la pillola si chiude.
  val seen = lead ?: overview.items.maxByOrNull { it.finishedAtMillis ?: 0L }
  val currentControls by rememberUpdatedState(controls)
  val currentOpen by rememberUpdatedState(onOpen)
  LaunchedEffect(face) {
    // L'esito si decide sull'ultimo visto: quello di questa composizione, anche se l'effetto parte prima del SideEffect.
    if (face == null && seen != null) last.value = seen
    if (face != null) {
      activities.publish(
        FluidActivity.transfer(
          id = id,
          progress = { progress.floatValue },
          title = face.title,
          upload = face.upload,
          detail = face.detail,
          paused = face.paused,
          onPause = { currentControls.pause(face.leadId) },
          onResume = { currentControls.resume(face.leadId) },
          onCancel = { currentControls.cancel(face.leadId) },
          onOpen = currentOpen?.let { open -> { open() } },
          labels = activityLabels,
        ),
      )
    } else {
      val ended = last.value
      when {
        ended == null -> activities.remove(id)
        ended.state == FluidTransferState.Failed -> activities.finish(id, FluidActivityOutcome.failed(labels.failure(ended.failure)))
        ended.state == FluidTransferState.Cancelled -> activities.remove(id)
        else -> activities.finish(id, FluidActivityOutcome.succeeded())
      }
    }
  }
  SideEffect { if (seen != null) last.value = seen }
  DisposableEffect(activities, id) { onDispose { activities.remove(id) } }
}

/** Cosa dice la pillola: si ripubblica solo quando cambia. */
@Immutable
private data class FluidTransferFace(
  val leadId: String,
  val title: String,
  val upload: Boolean,
  val paused: Boolean,
  val detail: String?,
)

/** L'id di serie dell'attivita' dei trasferimenti nella pillola. */
const val FluidTransferActivityId: String = "transfers"

/** I numeri della riga cambiano al piu' una volta al secondo, come nell'elenco dei download di Safari (a occhio). */
private const val TextIntervalMillis = 1_000L

/** La barra di un trasferimento fermo (in coda, in attesa, in pausa) si attenua. */
private const val DimmedAlpha = 0.45f
