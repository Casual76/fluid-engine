package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.UpdateChannel
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.SystemUpdateAlt
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

// L'aggiornamento in-app, il lato che disegna (3.0, fase 12c). Il modello, le parole e le funzioni pure
// stanno in `FluidUpdateModel.kt`: qui ci sono solo le due superfici che le app si riscrivevano.

/**
 * Il contenuto dell'avviso di aggiornamento: la versione, le note, il peso, l'avanzamento, i tasti.
 *
 * E' **contenuto**, non una finestra: sta dentro un [FluidGlassModalPortal] (o un foglio, o un avviso)
 * che lo possiede il chiamante — oppure si usa [FluidUpdatePromptSheet], che e' questo dentro un foglio
 * di vetro. Una sola cosa cambia secondo lo [state], e i tasti seguono:
 *
 *  * **c'e' una novita'** ([FluidUpdateUiState.Available]): «Aggiorna», «Più tardi» e, se passi [onSkip],
 *    «Salta questa versione» — tre risposte, e nessuna e' una trappola: si fa, si rimanda (torna al
 *    prossimo avvio), o si salta quella versione per sempre;
 *  * **sta scaricando o installando**: il tasto principale non c'e' piu', c'e' una riga che dice cosa
 *    succede, e resta «Più tardi». **L'avviso non diventa una gabbia**: fermare uno scaricamento e' una
 *    scelta legittima, e il suo significato (annullare o lasciar finire) e' dell'app, che riceve [onLater];
 *  * **e' fallita**: la frase che il sistema ha dato, «Riprova» e «Chiudi». Il tentativo successivo e'
 *    [onInstall]: per l'app e' lo stesso gesto della prima volta, non un secondo percorso;
 *  * **serve il permesso** di installare da questa app: lo dice prima, perche' Android lo chiede una
 *    volta e una finestra di sistema che arriva senza spiegazione sembra un errore. «Apri impostazioni» e'
 *    ancora [onInstall]: l'app sa cosa aprire;
 *  * **e' installata**: la conferma, e «Chiudi».
 *
 * **La barra dice la verita'**: determinata solo se si sa quanto pesa il file ([FluidUpdateUiState.Downloading.progress]
 * non nullo), senza fine per la verifica e l'installazione, **assente** mentre Android aspetta una
 * risposta (nulla si muove, e una barra che gira mentirebbe). Le note si leggono intere nel corpo del
 * foglio, che scorre da solo: i tasti restano fermi sotto, anche con una lista di novita' di quaranta righe.
 *
 * Suona `Success` quando l'installazione finisce e `Error` quando si ferma: un aggiornamento termina mentre
 * si guarda altrove, e l'esito si sente.
 *
 * Senza una versione nello stato (`Idle`, `Checking`, `UpToDate`, `CheckFailed`) non disegna niente.
 *
 * @param onSkip «Salta questa versione»: la tiene l'app (non tornera' piu' finche' non ne esce un'altra).
 *   Null: il tasto non c'e'.
 * @param insetBottom lascia sotto lo spazio della barra di navigazione: vero in un foglio, falso in un
 *   pop-up a mezz'aria (vedi [FluidSheetScaffold]).
 */
@Composable
fun FluidUpdatePrompt(
  state: FluidUpdateUiState,
  onInstall: () -> Unit,
  onLater: () -> Unit,
  modifier: Modifier = Modifier,
  onSkip: (() -> Unit)? = null,
  strings: FluidUpdateStrings = FluidUpdateStrings(),
  insetBottom: Boolean = true,
) {
  val release = state.release ?: return
  val haptics = rememberFluidHaptics()
  // Per tipo di stato e non per stato: ogni percentuale e' uno stato nuovo, e un colpo per ognuna sarebbe
  // la grana di un download. Il colpo e' per i due momenti che contano.
  LaunchedEffect(state::class) {
    when (state) {
      is FluidUpdateUiState.Installed -> haptics.play(FluidHapticEvent.Success)
      is FluidUpdateUiState.Failed -> haptics.play(FluidHapticEvent.Error)
      else -> Unit
    }
  }

  val failed = state is FluidUpdateUiState.Failed
  val notes = FluidUpdate.plainNotes(release.notes)
  val message = if (state is FluidUpdateUiState.Available) {
    strings.availableMessage(FluidUpdate.sizeLabel(release.sizeBytes))
  } else {
    FluidUpdate.progressText(state, strings)
  }

  FluidSheetScaffold(
    title = if (failed) strings.failedTitle else strings.promptTitle(release.version),
    modifier = modifier,
    message = message,
    icon = {
      FluidIcon(
        imageVector = if (failed) FluidIcons.Error else FluidIcons.SystemUpdateAlt,
        contentDescription = null,
      )
    },
    tone = if (failed) FluidTone.Danger else FluidTone.Primary,
    insetBottom = insetBottom,
    actions = {
      when {
        state is FluidUpdateUiState.Installed -> {
          FluidButton(
            text = strings.close,
            onClick = onLater,
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Large,
            fillWidth = true,
          )
        }
        state.isBusy -> {
          FluidButton(
            text = strings.updating,
            onClick = {},
            size = FluidButtonSize.Large,
            enabled = false,
            loading = true,
            fillWidth = true,
          )
          FluidButton(
            text = strings.later,
            onClick = onLater,
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Large,
            fillWidth = true,
          )
        }
        else -> {
          FluidButton(
            text = when (state) {
              is FluidUpdateUiState.Failed -> strings.retry
              is FluidUpdateUiState.NeedsPermission -> strings.openSettings
              else -> strings.install
            },
            onClick = onInstall,
            size = FluidButtonSize.Large,
            fillWidth = true,
          )
          FluidButton(
            text = if (state is FluidUpdateUiState.Available) strings.later else strings.close,
            onClick = onLater,
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Large,
            fillWidth = true,
          )
          if (state is FluidUpdateUiState.Available && onSkip != null) {
            FluidButton(
              text = strings.skip,
              onClick = onSkip,
              style = FluidButtonStyle.Plain,
              size = FluidButtonSize.Large,
              fillWidth = true,
            )
          }
        }
      }
    },
  ) {
    FluidUpdateProgress(state)
    if (notes.isNotBlank()) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = strings.whatsNew,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = notes,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/**
 * La barra dell'avanzamento: determinata se si sa a che punto e', senza fine se si sa solo che sta
 * lavorando, nulla se non si muove niente. Vedi [FluidUpdatePrompt].
 */
@Composable
private fun FluidUpdateProgress(state: FluidUpdateUiState, modifier: Modifier = Modifier) {
  when (state) {
    is FluidUpdateUiState.Downloading -> {
      val progress = state.progress
      if (progress != null) {
        FluidProgressBar(progress = { progress }, modifier = modifier.fillMaxWidth())
      } else {
        FluidIndeterminateBar(modifier = modifier.fillMaxWidth())
      }
    }
    is FluidUpdateUiState.Working -> when (state.stage) {
      FluidUpdateStage.Verifying, FluidUpdateStage.Installing ->
        FluidIndeterminateBar(modifier = modifier.fillMaxWidth())
      FluidUpdateStage.AwaitingUser -> Unit
    }
    else -> Unit
  }
}

/**
 * [FluidUpdatePrompt] dentro un foglio di vetro che sale dal basso: una chiamata sola per l'avviso
 * all'avvio.
 *
 * [state] **e' anche l'interruttore**: il foglio e' aperto finche' lo stato ha una versione
 * ([FluidUpdateUiState.release] non nullo) e si chiude quando l'app lo porta a `null`, `Idle` o
 * `UpToDate`. Resta intero mentre esce — il portale a `item` tiene l'ultimo stato — cosi' un avviso che si
 * chiude non diventa un titolo vuoto per i duecento millisecondi dell'uscita.
 *
 * [onLater] e' sia il tasto sia il congedo (indietro, un tocco fuori, trascinare giu'): per l'app sono la
 * stessa risposta. Senza un `FluidGlassModalHost` in scope non si apre e non va in crash (e' la regola del
 * portale).
 */
@Composable
fun FluidUpdatePromptSheet(
  state: FluidUpdateUiState?,
  onInstall: () -> Unit,
  onLater: () -> Unit,
  modifier: Modifier = Modifier,
  onSkip: (() -> Unit)? = null,
  strings: FluidUpdateStrings = FluidUpdateStrings(),
) {
  FluidGlassModalPortal(
    item = state?.takeIf { it.release != null },
    onDismissRequest = onLater,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = strings.sectionTitle,
  ) { shown ->
    FluidUpdatePrompt(
      state = shown,
      onInstall = onInstall,
      onLater = onLater,
      modifier = modifier,
      onSkip = onSkip,
      strings = strings,
    )
  }
}

/** Come si sceglie il canale: due righe di scelta, o un solo interruttore per le beta. */
enum class FluidUpdateChannelStyle {
  /** «Stabile» e «Beta», con la spunta a destra: si vede cosa si riceve e cosa no. */
  Choice,

  /** «Versioni di prova», un interruttore: per chi ha solo il canale stabile e quello beta come un di piu'. */
  BetaToggle,
}

/**
 * Le righe degli aggiornamenti per una pagina di impostazioni: il canale, il controllo con
 * l'installazione, "controllato N minuti fa", e lo scarico automatico (estensione di `LazyListScope`).
 *
 * Una estensione e non una schermata, per la stessa ragione di `fluidAppearanceSection`: ogni app ha gia'
 * la sua pagina «Impostazioni», con la sua testata e il suo ordine, e otto app avevano riscritto questa
 * sezione — chi con un `Dialog`, chi con una scheda, chi con tre tasti in fila — con la stessa differenza
 * fra una versione e l'altra: quasi tutta nei bug (l'`Idle` che non c'era, un errore che finiva nel ramo
 * «in corso» senza un modo di riprovare).
 *
 * **Il controllo e l'installazione sono la stessa riga** per chi legge: «c'e' una versione nuova, tocca e
 * arriva». Per questo l'azione cambia con lo [state] — «Controlla ora» quando non c'e' niente da
 * installare, «Installa la 1.2.0» (col peso) quando c'e', «Riprova» dopo un errore — e un'installazione in
 * corso mostra la sua barra dentro il gruppo, non in una scheda a parte.
 *
 * Ogni controllo di sola impostazione (canale, scarico automatico) compare solo se passi **insieme** il
 * valore e la sua callback: la sezione non inventa uno stato che l'app non ha. Il canale e' un
 * [UpdateChannel] di foundation; la callback riceve quello nuovo e **non viene chiamata** se tocchi quello
 * gia' scelto.
 *
 * @param currentVersion la versione installata: una riga in cima. Null: la riga non c'e'.
 * @param lastCheckedAtMillis quando si e' controllato l'ultima volta (epoca, in millisecondi): diventa
 *   «Controllato 5 minuti fa» sotto «Controlla ora». Null: «Mai controllato».
 * @param nowMillis l'orologio: di serie il telefono, e un test ne passa uno suo.
 * @param channelStyle vedi [FluidUpdateChannelStyle].
 * @param onSkip «Salta questa versione», una riga semplice sotto l'azione quando c'e' una novita'. Null:
 *   non c'e'.
 * @param supported falso per una build che non si aggiorna da sola (debug, store): una riga che lo dice e
 *   niente altro. E' meglio di una sezione fatta di tasti che non fanno niente.
 * @param glass i gruppi in vetro, se la schermata ha un canvas ambientale (come `FluidListGroup`).
 * @param footnote una nota sotto la sezione.
 * @param keyPrefix il prefisso delle chiavi degli item, se la pagina ha due sezioni di questo tipo.
 * @param autoCheck il controllo all'avvio (3.0.1), **distinto** da [autoDownload]: cercare una versione nuova
 *   a ogni apertura non vuol dire scaricarla senza chiedere. Compare, con [onAutoCheckChange], nello stesso
 *   gruppo dello scarico automatico e sopra di lui. Chi lo onora e' l'app (o [rememberFluidUpdateController]
 *   con `checkOnStart`).
 *
 * La riga di stato di una versione nuova riassume le note in **tre righe al piu'** (3.0.1): le note intere
 * stanno nell'avviso ([FluidUpdatePromptSheet]), e un primo paragrafo di dieci righe spingeva l'azione
 * fuori dallo schermo.
 */
fun LazyListScope.fluidUpdateSection(
  state: FluidUpdateUiState,
  onCheck: () -> Unit,
  onInstall: () -> Unit,
  currentVersion: String? = null,
  lastCheckedAtMillis: Long? = null,
  nowMillis: () -> Long = { System.currentTimeMillis() },
  channel: UpdateChannel? = null,
  onChannelChange: ((UpdateChannel) -> Unit)? = null,
  channelStyle: FluidUpdateChannelStyle = FluidUpdateChannelStyle.Choice,
  autoDownload: Boolean? = null,
  onAutoDownloadChange: ((Boolean) -> Unit)? = null,
  onSkip: (() -> Unit)? = null,
  supported: Boolean = true,
  strings: FluidUpdateStrings = FluidUpdateStrings(),
  glass: Boolean = false,
  footnote: String? = null,
  keyPrefix: String = "fluid-update",
  autoCheck: Boolean? = null,
  onAutoCheckChange: ((Boolean) -> Unit)? = null,
) {
  item(key = "$keyPrefix-header") { FluidSectionHeader(title = strings.sectionTitle) }

  if (!supported) {
    item(key = "$keyPrefix-unsupported") {
      FluidListGroup(glass = glass) {
        FluidListRow(title = strings.unsupported, subtitle = strings.unsupportedDetail)
      }
    }
    return
  }

  item(key = "$keyPrefix-status") {
    val release = state.release
    val busy = state.isBusy
    val rows = buildList<@Composable () -> Unit> {
      if (currentVersion != null) {
        add { FluidListRow(title = strings.currentVersion, subtitle = currentVersion) }
      }
      statusRow(state, strings)?.let { row -> add { FluidUpdateStatusRow(row) } }
      if (busy && state !is FluidUpdateUiState.Checking) {
        add { Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { FluidUpdateProgress(state) } }
      }
      add {
        if (state.canInstall && release != null) {
          FluidListRow(
            title = when (state) {
              is FluidUpdateUiState.Failed -> strings.retry
              is FluidUpdateUiState.NeedsPermission -> strings.openSettings
              else -> strings.installVersion(release.version)
            },
            subtitle = if (state is FluidUpdateUiState.Available) {
              FluidUpdate.sizeLabel(release.sizeBytes).orEmpty()
            } else {
              ""
            },
            onClick = onInstall,
          )
        } else {
          FluidListRow(
            title = strings.checkNow,
            subtitle = if (state is FluidUpdateUiState.Checking) {
              strings.checking
            } else {
              FluidUpdate.lastCheckLabel(lastCheckedAtMillis, nowMillis(), strings)
            },
            // Mentre controlla o installa un secondo tocco non rilancia niente: la riga resta, senza
            // risposta, e dice cosa sta facendo.
            onClick = if (busy) null else onCheck,
            badge = if (state is FluidUpdateUiState.Checking) {
              { FluidSpinner() }
            } else {
              null
            },
          )
        }
      }
      if (state is FluidUpdateUiState.Available && onSkip != null) {
        add { FluidListRow(title = strings.skip, subtitle = "", onClick = onSkip) }
      }
    }
    FluidListGroup(glass = glass) {
      rows.forEachIndexed { index, row ->
        if (index > 0) FluidListDivider()
        row()
      }
    }
  }

  if (channel != null && onChannelChange != null) {
    item(key = "$keyPrefix-channel") {
      val emit: (UpdateChannel) -> Unit = { next -> if (next != channel) onChannelChange(next) }
      when (channelStyle) {
        FluidUpdateChannelStyle.Choice -> FluidListGroup(modifier = Modifier.selectableGroup(), glass = glass) {
          UpdateChannel.entries.forEachIndexed { index, candidate ->
            if (index > 0) FluidListDivider()
            val stable = candidate == UpdateChannel.STABLE
            FluidChoiceRow(
              title = if (stable) strings.channelStable else strings.channelBeta,
              subtitle = if (stable) strings.channelStableDetail else strings.channelBetaDetail,
              selected = channel == candidate,
              onSelect = { emit(candidate) },
            )
          }
        }
        FluidUpdateChannelStyle.BetaToggle -> FluidListGroup(glass = glass) {
          FluidToggleRow(
            title = strings.betaToggle,
            subtitle = strings.betaToggleDetail,
            checked = channel == UpdateChannel.BETA,
            onCheckedChange = { on -> emit(if (on) UpdateChannel.BETA else UpdateChannel.STABLE) },
          )
        }
      }
    }
  }

  val showsAutoCheck = autoCheck != null && onAutoCheckChange != null
  val showsAutoDownload = autoDownload != null && onAutoDownloadChange != null
  if (showsAutoCheck || showsAutoDownload) {
    item(key = "$keyPrefix-auto") {
      FluidListGroup(glass = glass) {
        if (autoCheck != null && onAutoCheckChange != null) {
          FluidToggleRow(
            title = strings.autoCheck,
            subtitle = strings.autoCheckDetail,
            checked = autoCheck,
            onCheckedChange = onAutoCheckChange,
          )
        }
        if (autoDownload != null && onAutoDownloadChange != null) {
          if (showsAutoCheck) FluidListDivider()
          FluidToggleRow(
            title = strings.autoDownload,
            subtitle = strings.autoDownloadDetail,
            checked = autoDownload,
            onCheckedChange = onAutoDownloadChange,
          )
        }
      }
    }
  }

  if (footnote != null) {
    item(key = "$keyPrefix-footnote") { FluidSectionFootnote(text = footnote) }
  }
}

/** Quante righe di note al piu' nella riga di una versione nuova: le note intere sono nell'avviso. */
internal const val FluidUpdateNotesSummaryLines = 3

/** La riga che dice a che punto e' l'aggiornamento: titolo, dettaglio, ed eventualmente il segno «Nuova». */
private class FluidUpdateRowSpec(
  val title: String,
  val subtitle: String,
  val meta: String? = null,
  val isNew: Boolean = false,
  val newLabel: String = "",
  val subtitleMaxLines: Int = Int.MAX_VALUE,
)

/**
 * Cosa dice la riga di stato per [state], o null se non c'e' niente da dire (non si e' ancora
 * controllato, o si sta controllando: ne parla la riga dell'azione).
 */
private fun statusRow(state: FluidUpdateUiState, strings: FluidUpdateStrings): FluidUpdateRowSpec? = when (state) {
  FluidUpdateUiState.Idle, FluidUpdateUiState.Checking -> null
  FluidUpdateUiState.UpToDate -> FluidUpdateRowSpec(strings.upToDate, strings.upToDateDetail)
  is FluidUpdateUiState.CheckFailed -> FluidUpdateRowSpec(
    strings.checkFailed,
    state.message?.takeIf { it.isNotBlank() } ?: strings.checkFailedDetail,
  )
  is FluidUpdateUiState.Available -> FluidUpdateRowSpec(
    title = strings.available(state.release.version),
    subtitle = FluidUpdate.notesSummary(state.release.notes)
      .ifBlank { strings.availableMessage(FluidUpdate.sizeLabel(state.release.sizeBytes)) },
    isNew = true,
    newLabel = strings.newBadge,
    subtitleMaxLines = FluidUpdateNotesSummaryLines,
  )
  is FluidUpdateUiState.Downloading, is FluidUpdateUiState.Working ->
    FluidUpdateRowSpec(strings.updating, FluidUpdate.progressText(state, strings).orEmpty())
  is FluidUpdateUiState.Installed -> FluidUpdateRowSpec(strings.installed, "")
  is FluidUpdateUiState.Failed -> FluidUpdateRowSpec(strings.failedTitle, FluidUpdate.progressText(state, strings).orEmpty())
  is FluidUpdateUiState.NeedsPermission ->
    FluidUpdateRowSpec(strings.available(state.release.version), FluidUpdate.progressText(state, strings).orEmpty())
}

@Composable
private fun FluidUpdateStatusRow(spec: FluidUpdateRowSpec) {
  FluidListRow(
    title = spec.title,
    subtitle = spec.subtitle,
    meta = spec.meta,
    subtitleMaxLines = spec.subtitleMaxLines,
    badge = if (spec.isNew) {
      { FluidStatusBadge(label = spec.newLabel, tone = FluidTone.Primary) }
    } else {
      null
    },
  )
}
