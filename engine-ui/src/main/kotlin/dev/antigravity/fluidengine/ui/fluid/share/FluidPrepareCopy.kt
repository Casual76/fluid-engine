/*
 * Prepara la copia (4.0, F8.10, `prepara-la-copia`): prima del foglio di condivisione, un foglio per scegliere cosa
 * mandare (il formato, i dati da includere), con l'attesa, l'esito e il riepilogo di cio' che l'app ha davvero preparato.
 * Come le «Opzioni» del foglio di Foto su iPhone (la posizione di una foto). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid.share

import android.content.ContentResolver
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSheetPageBar
import dev.antigravity.fluidengine.ui.fluid.FluidSheetPageBarButton
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CheckCircle
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File

/** Un formato fra cui scegliere («Originale», «JPEG piu' leggero», «PDF»). */
@Immutable
class FluidCopyFormat(val key: String, val title: String)

/**
 * Un dato che si puo' includere o lasciare a casa («Posizione», «Registrazione audio», «Allegati»). [included] e' la
 * scelta di partenza. Un dato lasciato a casa chiede all'app la prova che nella copia non c'e' ([FluidCopyCheck]).
 */
@Immutable
class FluidCopyOption(val key: String, val title: String, val subtitle: String = "", val included: Boolean = true)

/** La scelta: il formato e i dati inclusi (per chiave). */
@Immutable
data class FluidCopyChoice(val format: String?, val included: Set<String>) {
  fun includes(key: String): Boolean = key in included
}

/**
 * Una verifica fatta dall'app **sulla copia vera**, non sulle opzioni: «Posizione — tolta», «Peso — 1,2 MB». [option] e'
 * la chiave del dato a cui si riferisce, se c'e'; [passed] falso blocca l'invio.
 */
@Immutable
class FluidCopyCheck(val label: String, val value: String, val passed: Boolean = true, val option: String? = null)

/** La copia preparata: l'oggetto da passare al foglio di condivisione e le verifiche fatte su di lui. */
@Immutable
class FluidPreparedCopy(val item: FluidShareItem, val checks: List<FluidCopyCheck>)

/** Le parole del foglio, di serie in italiano. */
@Immutable
data class FluidPrepareCopyLabels(
  val title: String = "Opzioni",
  val message: String? = "Scegli cosa parte. Il riepilogo dice cosa c'è davvero nella copia.",
  val format: String = "Formato",
  val include: String = "Includi",
  val preparing: String = "Preparo la copia…",
  val ready: String = "Pronta da condividere",
  val blocked: String = "La copia non corrisponde alle opzioni: non parte.",
  val notVerified: String = "Non verificato: %s",
  val failed: String = "La copia non si è potuta preparare.",
  val share: String = "Condividi",
  val retry: String = "Rifai la copia",
  val cancel: String = "Annulla",
  /** La conferma della pagina «Opzioni» dentro il foglio di condivisione ([FluidShareCopyOptions]). */
  val done: String = "Fine",
  /** Il tasto indietro della stessa pagina. */
  val back: String = "Indietro",
) {
  companion object {
    fun english(): FluidPrepareCopyLabels = FluidPrepareCopyLabels(
      title = "Options", message = "Choose what goes out. The summary says what the copy really contains.",
      format = "Format", include = "Include", preparing = "Preparing the copy…", ready = "Ready to share",
      blocked = "The copy doesn't match the options: it won't be sent.", notVerified = "Not verified: %s",
      failed = "The copy couldn't be prepared.", share = "Share", retry = "Prepare Again", cancel = "Cancel",
      done = "Done", back = "Back",
    )
  }
}

/** I tag dei test e delle riprese. */
object FluidPrepareCopyTags {
  const val Sheet = "fluid-copy:foglio"
  const val Status = "fluid-copy:esito"
  const val Share = "fluid-copy:condividi"
  const val Retry = "fluid-copy:rifai"
  /** La pagina «Opzioni» dentro il foglio di condivisione. */
  const val Page = "fluid-copy:pagina"
  /** La sua conferma. */
  const val Done = "fluid-copy:fine"
  fun option(key: String): String = "fluid-copy:opzione:$key"
  fun check(index: Int): String = "fluid-copy:verifica:$index"
}

/** A che punto e' la copia. */
sealed class FluidCopyState {
  object Preparing : FluidCopyState()
  class Ready(val copy: FluidPreparedCopy) : FluidCopyState()
  class Blocked(val copy: FluidPreparedCopy, val problems: List<String>) : FluidCopyState()
  class Failed(val message: String?) : FluidCopyState()
}

/**
 * Cosa non va in [copy] rispetto a [choice]: una verifica fallita, o un dato lasciato a casa senza una verifica passata
 * che lo dica. Vuoto vuol dire che la copia corrisponde alle opzioni. E' la regola della scheda: una spunta da sola non
 * toglie un dato dal file, quindi senza la prova l'invio e' bloccato.
 */
fun fluidCopyProblems(
  options: List<FluidCopyOption>,
  choice: FluidCopyChoice,
  copy: FluidPreparedCopy,
  labels: FluidPrepareCopyLabels = FluidPrepareCopyLabels(),
): List<String> {
  val failed = copy.checks.filter { !it.passed }.map { "${it.label}: ${it.value}" }
  // Una verifica fallita e' gia' nell'elenco: «non verificato» e' solo il dato lasciato a casa senza nessuna verifica.
  val unverified = options.filter { !choice.includes(it.key) }
    .filter { option -> copy.checks.none { it.option == option.key } }
    .map { labels.notVerified.format(it.title) }
  return failed + unverified
}

/**
 * Il foglio «Opzioni» prima della condivisione (4.0, F8.10, `prepara-la-copia`).
 *
 * In cima il formato ([formats], se piu' d'uno) e i dati da includere ([options]); sotto, l'esito. **La copia la prepara
 * l'app** ([prepare]), di nuovo a ogni cambio di scelta (l'ultima vince, quella prima si annulla): l'engine mostra
 * l'attesa, poi il riepilogo delle verifiche fatte sulla copia vera ([FluidCopyCheck]). Se la copia non corrisponde alle
 * opzioni ([fluidCopyProblems]) «Condividi» resta spento e c'e' «Rifai la copia». L'attesa e l'esito sono cambi
 * dell'app ed entrano sfocati; gli interruttori e il segmentato li muove il dito e restano nitidi.
 *
 * [onReady] riceve l'oggetto da condividere (di solito l'app apre subito [FluidShareSheet] con lui).
 */
@Composable
fun FluidPrepareCopySheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  onReady: (FluidShareItem) -> Unit,
  prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy,
  formats: List<FluidCopyFormat> = emptyList(),
  options: List<FluidCopyOption> = emptyList(),
  source: FluidTransitionSource? = null,
  labels: FluidPrepareCopyLabels = FluidPrepareCopyLabels(),
) {
  // R2 · La scelta si salva qui, dove il foglio e' dichiarato, e non nel contenuto: quello lo compone l'host dei modali
  // alla radice, e alla rotazione (o col processo ripristinato) ripartirebbe dalle scelte di serie, rimettendo in
  // silenzio la posizione che la persona aveva tolto. Un'apertura nuova riparte da capo.
  val draft = rememberSaveable(saver = FluidCopyDraft.Saver) { FluidCopyDraft() }
  SideEffect { draft.follow(visible) }
  FluidGlassSheet(
    visible = visible,
    onDismissRequest = onDismissRequest,
    detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    paneTitle = labels.title,
    source = source,
  ) {
    PrepareCopyBody(onDismissRequest, onReady, prepare, formats, options, labels, start = draft.choice, onChoice = { draft.choice = it })
  }
}

/** La scelta del foglio delle opzioni a se', salvata col foglio: sopravvive alla rotazione e al processo ucciso (R2). */
@Stable
internal class FluidCopyDraft {
  var open by mutableStateOf(false)
  var choice by mutableStateOf<FluidCopyChoice?>(null)

  /** Un foglio che si apre riparte dalle scelte di serie; chiuso, la scelta resta per l'uscita. */
  fun follow(visible: Boolean) {
    if (!visible) {
      open = false
    } else if (!open) {
      open = true
      choice = null
    }
  }

  companion object {
    val Saver: Saver<FluidCopyDraft, Any> = Saver(
      save = { draft -> arrayListOf(draft.open, draft.choice?.takeIf { draft.open }?.let(::fluidSaveChoice)) },
      restore = { saved ->
        val list = saved as List<*>
        FluidCopyDraft().apply {
          open = list[0] as Boolean
          choice = fluidRestoreChoice(list[1])
        }
      },
    )
  }
}

/** Una scelta nel `Bundle`: il formato e le chiavi incluse, solo stringhe. */
internal fun fluidSaveChoice(choice: FluidCopyChoice): ArrayList<Any?> = arrayListOf(choice.format, ArrayList(choice.included.sorted()))

/** Il contrario di [fluidSaveChoice]; null per niente di salvato. */
internal fun fluidRestoreChoice(saved: Any?): FluidCopyChoice? {
  val list = saved as? List<*> ?: return null
  return FluidCopyChoice(list[0] as String?, (list[1] as List<*>).map { it as String }.toSet())
}

/**
 * Le «Opzioni ›» del foglio di condivisione (4.0, F8.10): con questo, [FluidShareSheet] mostra «Opzioni ›» sotto il nome
 * e le apre come una pagina **dentro lo stesso foglio** ([FluidSheetPages]), come il foglio di Foto di iOS: arriva da
 * destra, indietro in alto a sinistra, la spunta in alto a destra. La pagina e' quella di [FluidPrepareCopySheet] (il
 * formato, i dati da includere, l'attesa, il riepilogo sulla copia vera); confermata, si torna alla pagina del foglio
 * con la copia nuova al posto dell'oggetto di prima. Il foglio non si chiude e non si riapre.
 *
 * L'oggetto passato al foglio e' la copia della scelta di partenza ([FluidCopyOption.included], il primo formato): la
 * pagina riparte dall'ultima scelta confermata e, se la scelta non cambia, non rifa' la copia. Se la scelta di partenza
 * lascia a casa qualcosa (un'opzione con `included` falso), il foglio non si fida dell'oggetto: all'apertura prepara e
 * verifica lui la copia di partenza, con l'attesa al posto delle azioni, e una copia che non corrisponde non parte (R2).
 *
 * La scelta confermata e la sua copia si salvano col foglio: dopo una rotazione, o col processo ripristinato e l'app che
 * ripassa lo stesso oggetto, il foglio riparte dalla copia, mai dall'originale (R2).
 */
@Immutable
class FluidShareCopyOptions(
  val prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy,
  val formats: List<FluidCopyFormat> = emptyList(),
  val options: List<FluidCopyOption> = emptyList(),
  val labels: FluidPrepareCopyLabels = FluidPrepareCopyLabels(),
) {
  /** La scelta di partenza: il primo formato e le [options] con `included`. */
  internal val startChoice: FluidCopyChoice
    get() = FluidCopyChoice(formats.firstOrNull()?.key, options.filter { it.included }.map { it.key }.toSet())

  /**
   * La scelta di partenza se lascia a casa qualcosa (un'opzione spenta di serie), altrimenti null. Con lei il foglio non
   * si fida dell'oggetto passato: prepara e verifica la copia di partenza prima di mostrare le azioni (R2).
   */
  internal val strictStart: FluidCopyChoice? get() = startChoice.takeIf { options.any { !it.included } }
}

/** La scelta, il tentativo e l'esito di una preparazione: li usano il foglio a se' e la pagina dentro il foglio. */
@Stable
private class PrepareCopyModel(format: String?, included: Set<String>) {
  var format by mutableStateOf(format)
  var included by mutableStateOf(included)
  var attempt by mutableIntStateOf(0)
  var state by mutableStateOf<FluidCopyState>(FluidCopyState.Preparing)
  /** La scelta per cui e' fatto [state]: la copia pronta vale solo per lei (R2). */
  var stateChoice by mutableStateOf<FluidCopyChoice?>(null)
  val choice: FluidCopyChoice get() = FluidCopyChoice(format, included)

  /** La copia pronta, se e' quella della scelta di adesso: un dito che ha appena cambiato scelta non conferma la vecchia. */
  val ready: FluidCopyState.Ready? get() = (state as? FluidCopyState.Ready)?.takeIf { stateChoice == choice }
}

/**
 * Prepara [choice] con [prepare] e la verifica ([fluidCopyState]). Una preparazione annullata non scrive mai: chi prepara
 * puo' non guardare l'annullamento (un callback avvolto in `suspendCoroutine`, una copia bloccante) e finire dopo che la
 * persona ha cambiato scelta; la sua copia, magari con la posizione, non deve diventare quella pronta (R2).
 */
internal suspend fun fluidPrepareChecked(
  prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy,
  options: List<FluidCopyOption>,
  choice: FluidCopyChoice,
  labels: FluidPrepareCopyLabels,
): FluidCopyState {
  val state = try {
    fluidCopyState(options, choice, prepare(choice), labels)
  } catch (c: CancellationException) {
    throw c
  } catch (t: Throwable) {
    FluidCopyState.Failed(t.message)
  }
  currentCoroutineContext().ensureActive()
  return state
}

/**
 * Prepara la copia a ogni cambio di scelta (l'ultima vince). Con [start] e [startCopy] parte da una copia gia' fatta:
 * finche' la scelta e' quella, la copia resta e non si rifa'.
 */
@Composable
private fun rememberPrepareCopyModel(
  prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy,
  formats: List<FluidCopyFormat>,
  options: List<FluidCopyOption>,
  labels: FluidPrepareCopyLabels,
  start: FluidCopyChoice? = null,
  startCopy: FluidPreparedCopy? = null,
  onChoice: ((FluidCopyChoice) -> Unit)? = null,
): PrepareCopyModel {
  val haptics = rememberFluidHaptics()
  val preparer by rememberUpdatedState(prepare)
  val report by rememberUpdatedState(onChoice)
  val model = remember {
    PrepareCopyModel(
      start?.format ?: formats.firstOrNull()?.key,
      start?.included ?: options.filter { it.included }.map { it.key }.toSet(),
    )
  }
  val choice = model.choice
  LaunchedEffect(choice) { report?.invoke(choice) }
  LaunchedEffect(choice, model.attempt) {
    val kept = startCopy?.takeIf { model.attempt == 0 && choice == start }
    if (kept != null) {
      model.stateChoice = choice
      model.state = fluidCopyState(options, choice, kept, labels)
      return@LaunchedEffect
    }
    model.state = FluidCopyState.Preparing
    model.stateChoice = choice
    // Un dito che spegne due interruttori di fila fa una copia sola. A occhio, 24 novembre 2026.
    delay(FluidPrepareDebounceMillis)
    model.state = fluidPrepareChecked(preparer, options, choice, labels)
    haptics.play(if (model.state is FluidCopyState.Ready) FluidHapticEvent.Success else FluidHapticEvent.Warning)
  }
  return model
}

/** Pronta se corrisponde alle opzioni ([fluidCopyProblems]), bloccata altrimenti. */
internal fun fluidCopyState(
  options: List<FluidCopyOption>,
  choice: FluidCopyChoice,
  copy: FluidPreparedCopy,
  labels: FluidPrepareCopyLabels,
): FluidCopyState {
  val problems = fluidCopyProblems(options, choice, copy, labels)
  return if (problems.isEmpty()) FluidCopyState.Ready(copy) else FluidCopyState.Blocked(copy, problems)
}

@Composable
private fun PrepareCopyBody(
  onDismissRequest: () -> Unit,
  onReady: (FluidShareItem) -> Unit,
  prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy,
  formats: List<FluidCopyFormat>,
  options: List<FluidCopyOption>,
  labels: FluidPrepareCopyLabels,
  start: FluidCopyChoice? = null,
  onChoice: ((FluidCopyChoice) -> Unit)? = null,
) {
  val model = rememberPrepareCopyModel(prepare, formats, options, labels, start, onChoice = onChoice)
  FluidSheetScaffold(
    title = labels.title,
    message = labels.message,
    modifier = Modifier.testTag(FluidPrepareCopyTags.Sheet),
    actions = {
      val ready = model.ready
      FluidButton(
        labels.share,
        onClick = { ready?.let { onReady(it.copy.item) } },
        enabled = ready != null,
        fillWidth = true,
        size = FluidButtonSize.Large,
        modifier = Modifier.testTag(FluidPrepareCopyTags.Share),
      )
      if (model.state is FluidCopyState.Blocked || model.state is FluidCopyState.Failed) {
        FluidButton(
          labels.retry,
          onClick = { model.attempt++ },
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
          size = FluidButtonSize.Large,
          modifier = Modifier.testTag(FluidPrepareCopyTags.Retry),
        )
      }
      FluidButton(labels.cancel, onDismissRequest, style = FluidButtonStyle.Plain, fillWidth = true, size = FluidButtonSize.Large)
    },
  ) {
    PrepareCopyFields(model, formats, options, labels, onRetry = null)
  }
}

/**
 * La pagina «Opzioni» dentro il foglio di condivisione ([FluidShareCopyOptions]): la barra con indietro e la spunta, poi
 * i campi di [FluidPrepareCopySheet]. La spunta e' accesa solo con la copia pronta; «Rifai la copia» sta nell'esito.
 */
@Composable
internal fun FluidShareOptionsPage(
  copyOptions: FluidShareCopyOptions,
  start: FluidCopyChoice?,
  startCopy: FluidPreparedCopy?,
  onBack: () -> Unit,
  onConfirm: (FluidCopyChoice, FluidPreparedCopy) -> Unit,
) {
  val labels = copyOptions.labels
  val model = rememberPrepareCopyModel(copyOptions.prepare, copyOptions.formats, copyOptions.options, labels, start, startCopy)
  val ready = model.ready
  Column(Modifier.fillMaxSize().testTag(FluidPrepareCopyTags.Page)) {
    FluidSheetPageBar(title = labels.title, onBack = onBack, backLabel = labels.back) {
      FluidGlassIconButton(
        onClick = { ready?.let { onConfirm(model.choice, it.copy) } },
        size = FluidSheetPageBarButton,
        enabled = ready != null,
        style = FluidGlassButtonStyle.Prominent,
        haptic = FluidHapticEvent.Success,
        modifier = Modifier.testTag(FluidPrepareCopyTags.Done),
      ) {
        FluidIcon(FluidIcons.Check, contentDescription = labels.done, size = 22.dp)
      }
    }
    Column(
      Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      labels.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      PrepareCopyFields(model, copyOptions.formats, copyOptions.options, labels, onRetry = { model.attempt++ })
    }
  }
}

/** Il formato, i dati da includere e l'esito. [onRetry] mette «Rifai la copia» nell'esito, quando serve. */
@Composable
private fun PrepareCopyFields(
  model: PrepareCopyModel,
  formats: List<FluidCopyFormat>,
  options: List<FluidCopyOption>,
  labels: FluidPrepareCopyLabels,
  onRetry: (() -> Unit)?,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (formats.size > 1) {
      Text(labels.format, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      FluidSegmentedControl(
        options = formats,
        selected = formats.firstOrNull { it.key == model.format } ?: formats.first(),
        onSelect = { model.format = it.key },
        modifier = Modifier.fillMaxWidth(),
        label = { it.title },
      )
    }
    if (options.isNotEmpty()) {
      Text(labels.include, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      FluidListGroup {
        options.forEachIndexed { index, option ->
          if (index > 0) FluidListDivider()
          FluidToggleRow(
            title = option.title,
            subtitle = option.subtitle,
            checked = option.key in model.included,
            onCheckedChange = { on -> model.included = if (on) model.included + option.key else model.included - option.key },
            modifier = Modifier.testTag(FluidPrepareCopyTags.option(option.key)),
          )
        }
      }
    }
    // L'esito: lo cambia l'app (la copia pronta, bloccata, fallita), quindi entra sfocato.
    FluidGlassContentTransition(
      targetState = model.state,
      key = { it.javaClass },
      modifier = Modifier.fillMaxWidth().testTag(FluidPrepareCopyTags.Status)
        .semantics { liveRegion = LiveRegionMode.Polite },
      blurReference = 40.dp,
      contentAlignment = Alignment.TopStart,
    ) { current -> CopyStatus(current, labels, onRetry) }
  }
}

/** La pausa prima di preparare, perche' due cambi di fila facciano una copia sola. */
internal const val FluidPrepareDebounceMillis: Long = 180L

@Composable
private fun CopyStatus(state: FluidCopyState, labels: FluidPrepareCopyLabels, onRetry: (() -> Unit)?) {
  when (state) {
    FluidCopyState.Preparing -> Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidSpinner()
      Spacer(Modifier.width(10.dp))
      Text(labels.preparing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    is FluidCopyState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusLine(labels.ready, ok = true)
      Checks(state.copy.checks)
    }
    is FluidCopyState.Blocked -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusLine(labels.blocked, ok = false)
      state.problems.forEach {
        Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
      }
      Checks(state.copy.checks)
      RetryButton(labels, onRetry)
    }
    is FluidCopyState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      StatusLine(labels.failed, ok = false)
      state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      RetryButton(labels, onRetry)
    }
  }
}

@Composable
private fun RetryButton(labels: FluidPrepareCopyLabels, onRetry: (() -> Unit)?) {
  if (onRetry == null) return
  FluidButton(
    labels.retry,
    onClick = onRetry,
    style = FluidButtonStyle.Tinted,
    fillWidth = true,
    size = FluidButtonSize.Large,
    modifier = Modifier.padding(top = 8.dp).testTag(FluidPrepareCopyTags.Retry),
  )
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    FluidIcon(
      if (ok) FluidIcons.CheckCircle else FluidIcons.Error,
      contentDescription = null,
      size = 20.dp,
      tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun Checks(checks: List<FluidCopyCheck>) {
  if (checks.isEmpty()) return
  FluidListGroup {
    checks.forEachIndexed { index, check ->
      if (index > 0) FluidListDivider()
      Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp).testTag(FluidPrepareCopyTags.check(index))
          .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(check.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
          check.value,
          style = MaterialTheme.typography.bodyMedium,
          color = if (check.passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        FluidIcon(
          if (check.passed) FluidIcons.CheckCircle else FluidIcons.Error,
          contentDescription = null,
          size = 18.dp,
          tint = if (check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

/**
 * Gli attrezzi per le copie delle foto (4.0, F8.10): togliere la posizione da un JPEG e verificare che non c'e' piu',
 * leggendo il file e non le opzioni. Usano `android.media.ExifInterface`; chiamare fuori dal thread principale.
 */
object FluidCopyTools {
  /** I campi EXIF che dicono dove e quando e' stata fatta una foto col GPS. */
  val LocationTags: List<String> = listOf(
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
    ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE,
    ExifInterface.TAG_GPS_DEST_LATITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_TRACK,
  )

  /** Vero se [file] porta ancora un dato di posizione. */
  fun hasLocation(file: File): Boolean {
    val exif = ExifInterface(file.path)
    return LocationTags.any { exif.getAttribute(it) != null }
  }

  /** Copia l'immagine di [source] in [target]. */
  fun copy(resolver: ContentResolver, source: Uri, target: File) {
    requireNotNull(resolver.openInputStream(source)) { "Il file non si legge" }.use { input ->
      target.outputStream().use { input.copyTo(it) }
    }
  }

  /** Copia un JPEG di [source] in [target] senza i dati di posizione; il resto dei metadati resta. */
  fun copyWithoutLocation(resolver: ContentResolver, source: Uri, target: File) {
    copy(resolver, source, target)
    val exif = ExifInterface(target.path)
    LocationTags.forEach { exif.setAttribute(it, null) }
    exif.saveAttributes()
  }
}
