package dev.antigravity.fluidengine.aiui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.aiui.markdown.FluidAssistantMarkdownOptions
import dev.antigravity.fluidengine.aiui.overlay.FluidConfirmationRow
import dev.antigravity.fluidengine.aiui.overlay.fluidDefaultChipLabel
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.aiui.strings.FluidChatStrings
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownStyle
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.theme.FluidCard

/**
 * Come si disegna un messaggio: **Bubble** (PampAI) — la domanda e' una bolla colorata a destra, la
 * risposta e' testo a tutta larghezza sulla pagina, senza card — oppure **Card** (ClasseViva) — una
 * carta per messaggio, con "Tu" e "Assistente" sopra. In una conversazione le due voci si distinguono
 * per forma prima che per etichetta, e per questo la bolla e' di serie: una domanda che occupa la riga
 * intera quanto la risposta toglie proprio quel segnale. La carta resta per chi ha una pagina fatta di
 * carte (un registro, un elenco) e non vuole una chat in mezzo.
 */
enum class FluidMessageStyle { Bubble, Card }

/** Un'azione su un messaggio: voce del menu di pressione lunga **e** azione personalizzata per TalkBack. */
@Immutable
data class FluidMessageAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null, val onClick: () -> Unit)

/**
 * La domanda: una bolla colorata a destra, come in una chat. Non e' una card di vetro a tutta
 * larghezza: in una conversazione le due voci si distinguono per forma prima che per etichetta.
 *
 * Il dominio entra dagli slot, e li scrive l'app: [leading] (la matita "modifica" di PampAI, fuori a
 * sinistra e solo sull'ultima domanda), [attachments] (le miniature vere, con coil, che l'engine non
 * conosce), [below] (le frecce "‹ 2/3 ›" delle versioni). [actions] sono le voci del menu di pressione
 * lunga sulla bolla (Copia, Modifica e rinvia…): le stesse le legge TalkBack fra le azioni.
 *
 * @param extraAccessibilityActions altre azioni solo per TalkBack (le versioni, in PampAI).
 * @param maxWidthFraction quanto della riga puo' prendere la bolla.
 */
@Composable
fun FluidUserBubble(
  text: String,
  modifier: Modifier = Modifier,
  style: FluidMessageStyle = FluidMessageStyle.Bubble,
  actions: List<FluidMessageAction> = emptyList(),
  extraAccessibilityActions: List<CustomAccessibilityAction> = emptyList(),
  leading: (@Composable () -> Unit)? = null,
  attachments: (@Composable ColumnScope.() -> Unit)? = null,
  below: (@Composable () -> Unit)? = null,
  strings: FluidChatStrings = FluidChatStrings(),
  glass: Boolean = false,
  maxWidthFraction: Float = 0.86f,
) {
  if (style == FluidMessageStyle.Card) {
    FluidCard(modifier = modifier, glass = glass) {
      Text(strings.userLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(4.dp))
      Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
      attachments?.let {
        Spacer(Modifier.height(8.dp))
        it()
      }
    }
    return
  }
  val menu = rememberFluidContextMenu(actions = { actions.map { FluidContextAction(it.label, it.icon, onClick = it.onClick) } })
  val talkBack = actions.map { action -> CustomAccessibilityAction(action.label) { action.onClick(); true } } + extraAccessibilityActions
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val maxBubble = maxWidth * maxWidthFraction
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Column(
          modifier = Modifier
            .widthIn(max = maxBubble)
            .then(if (actions.isNotEmpty()) Modifier.fluidContextMenuAnchor(menu).fluidPressable(onLongClick = { menu.open() }, pressedScale = 1f, haptic = null) else Modifier)
            .semantics { customActions = talkBack }
            .background(MaterialTheme.colorScheme.primaryContainer, ContinuousCornerShape(FluidRadius.Card))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
          Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
          attachments?.let {
            Spacer(Modifier.height(8.dp))
            it()
          }
        }
      }
    }
    below?.invoke()
  }
}

/**
 * Una domanda scritta mentre l'assistente lavorava, che aspetta il suo turno: la bolla della
 * domanda, piu' tenue, con sotto "In coda" e la X che la riporta nel campo. Parte da sola quando
 * l'assistente finisce. Il concetto di coda e' dell'app (un runtime che la tiene): qui c'e' solo come si vede.
 */
@Composable
fun FluidQueuedBubble(
  text: String,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  attachmentCount: Int = 0,
  strings: FluidChatStrings = FluidChatStrings(),
) {
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
      Column(
        Modifier
          .widthIn(max = maxWidth * 0.86f)
          .alpha(QueuedAlpha)
          .background(MaterialTheme.colorScheme.primaryContainer, ContinuousCornerShape(FluidRadius.Card))
          .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (attachmentCount > 0) {
          Text(
            fluidFormat(strings.queuedAttachments, attachmentCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(6.dp))
      Text(strings.queued, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      FluidSmallAction(Icons.Rounded.Close, strings.queuedRemove, onCancel)
    }
  }
}

/** Quanto e' tenue una domanda in coda: si legge, ma si vede che non e' ancora partita. */
private const val QueuedAlpha = 0.6f

/**
 * Come finisce una risposta senza (o oltre) il suo testo. La stessa tabella di decisioni che
 * PampAI aveva scritta nel mezzo di `AssistantMessage`: **pura**, in [fluidOutcomeLines], perche' e' la
 * parte che si dimentica un caso (una risposta fermata a meta' che sembra tronca per un guasto).
 */
sealed interface FluidBubbleOutcome {
  /** Niente da dire: la risposta e' quella che si legge, o sta ancora arrivando. */
  data object None : FluidBubbleOutcome

  /** E' fallita; [message] e' la frase (di [FluidAssistantTexts.failure]). */
  data class Failed(val message: String) : FluidBubbleOutcome

  /** L'utente l'ha fermata. */
  data object Cancelled : FluidBubbleOutcome

  /** Il servizio ha chiuso senza scrivere niente. */
  data object Empty : FluidBubbleOutcome

  /** L'app si e' chiusa prima della fine e nessuno la sta piu' scrivendo. */
  data object Interrupted : FluidBubbleOutcome
}

/** Una riga di esito: il testo, se e' un errore, se e' la riga piccola sotto il testo. */
@Immutable
data class FluidOutcomeLine(val text: String, val error: Boolean)

/** Le due righe di esito: al posto del testo (se non c'e') e in coda ad esso (se c'e'). */
@Immutable
data class FluidOutcomeLines(val instead: FluidOutcomeLine?, val after: FluidOutcomeLine?)

/**
 * Cosa dire di un esito.
 *  - Senza testo: la frase dell'esito al suo posto (un errore in rosso, il resto in grigio).
 *  - Con testo e fallita: il testo resta e sotto, piccola e rossa, la frase dell'errore.
 *  - Con testo e fermata, a lavoro finito ([settled]): sotto, "Fermata qui" — una risposta fermata a
 *    meta' finisce a meta' frase, e senza la riga sembra tronca per un guasto.
 *  - Mentre lavora ([busy]) niente: lo dice la riga di stato.
 */
fun fluidOutcomeLines(outcome: FluidBubbleOutcome, hasText: Boolean, busy: Boolean, settled: Boolean, strings: FluidChatStrings): FluidOutcomeLines {
  if (hasText) {
    return when (outcome) {
      is FluidBubbleOutcome.Failed -> FluidOutcomeLines(null, FluidOutcomeLine(outcome.message, error = true))
      FluidBubbleOutcome.Cancelled -> FluidOutcomeLines(null, if (settled) FluidOutcomeLine(strings.cancelledHere, error = false) else null)
      else -> FluidOutcomeLines(null, null)
    }
  }
  return when (outcome) {
    is FluidBubbleOutcome.Failed -> FluidOutcomeLines(FluidOutcomeLine(outcome.message, error = true), null)
    FluidBubbleOutcome.Cancelled -> FluidOutcomeLines(FluidOutcomeLine(strings.cancelledBeforeAnswer, error = false), null)
    FluidBubbleOutcome.Empty -> if (busy) FluidOutcomeLines(null, null) else FluidOutcomeLines(FluidOutcomeLine(strings.noAnswer, error = false), null)
    FluidBubbleOutcome.Interrupted -> if (busy) FluidOutcomeLines(null, null) else FluidOutcomeLines(FluidOutcomeLine(strings.interrupted, error = false), null)
    FluidBubbleOutcome.None -> FluidOutcomeLines(null, null)
  }
}

/**
 * La risposta: testo a tutta larghezza, senza card. Una risposta chiusa in un riquadro si legge
 * come una scheda; una conversazione vuole che le parole stiano sulla pagina. Sopra, quando c'e'
 * stato del lavoro, la riga di stato ([status]) e l'eventuale traccia ([header]); sotto stanno i chip,
 * la riga del servizio che ha risposto ([switchLine]) e le azioni ([actions]), che sfumano dentro
 * a risposta finita.
 *
 * **La fine della risposta e' un movimento, non cinque.** Prima, al "fatto", la riga di stato
 * spariva di colpo, la traccia compariva, i paragrafi diventavano Markdown, la riga delle azioni
 * spuntava e la misura animata si spegneva a meta'. Qui il testo e' lo stesso nodo ([FluidMarkdown]
 * con `streaming` che torna falso), le azioni e i chip sfumano dentro, e la colonna anima la sua
 * misura finche' la risposta e' viva ([animateSize]).
 *
 * Quello che resta all'app e' il dominio: la **traccia dei passi** ([header]), le **azioni** sotto
 * ([actions]: Copia, Condividi, Rigenera con…, versioni, telemetria; `FluidSmallAction` e
 * `FluidPillAction` sono pronte), cosa e' un chip. Il testo, lo stato, l'esito li passa chi chiama
 * (da una riga su disco, da `AssistantState`: nessuno dei due e' dell'engine).
 *
 * @param streaming il testo sta arrivando: le parole nuove sfumano dentro.
 * @param contentKey l'identita' del messaggio (il suo id): "e' nato adesso?" si decide al primo
 *   passaggio per messaggio, e una versione vecchia ritrovata con le frecce e' gia' letta, non si rivela.
 * @param settled il lavoro su questa risposta e' finito (azioni e chip si mostrano).
 * @param confirmation un'azione in attesa del si'.
 */
@Composable
fun FluidAssistantBubble(
  text: String,
  modifier: Modifier = Modifier,
  style: FluidMessageStyle = FluidMessageStyle.Bubble,
  streaming: Boolean = false,
  settled: Boolean = !streaming,
  contentKey: Any? = null,
  status: String? = null,
  statusError: Boolean = false,
  header: (@Composable ColumnScope.() -> Unit)? = null,
  confirmation: PendingConfirmation? = null,
  onConfirm: (Long, Boolean) -> Unit = { _, _ -> },
  outcome: FluidBubbleOutcome = FluidBubbleOutcome.None,
  busy: Boolean = streaming,
  chips: List<AnswerChip> = emptyList(),
  chipLabel: (AnswerChip) -> String = ::fluidDefaultChipLabel,
  onChip: (AnswerChip) -> Unit = {},
  switchLine: String? = null,
  actions: (@Composable RowScope.() -> Unit)? = null,
  animateSize: Boolean = streaming,
  strings: FluidChatStrings = FluidChatStrings(),
  assistantStrings: FluidAssistantStrings = FluidAssistantStrings(),
  markdownStyle: FluidMarkdownStyle = FluidMarkdownDefaults.style(),
  glass: Boolean = false,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // Una per messaggio: se al primo passaggio il testo c'era gia' (chat riaperta) e' gia' letto.
  val freshStart = remember(contentKey) { text.isBlank() }
  val sizing = if (animateSize && !reducedMotion) Modifier.animateContentSize(FluidMotion.intSize(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy)) else Modifier
  val lines = fluidOutcomeLines(outcome, hasText = text.isNotBlank(), busy = busy, settled = settled, strings = strings)

  @Composable
  fun Content() {
    Column(Modifier.fillMaxWidth().then(sizing)) {
      header?.invoke(this)
      if (!status.isNullOrBlank()) {
        Text(
          text = status,
          style = MaterialTheme.typography.bodyMedium,
          color = if (statusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(bottom = 6.dp),
        )
      }
      if (confirmation != null) {
        FluidConfirmationRow(confirmation, onConfirm = { onConfirm(confirmation.id, it) }, strings = assistantStrings, modifier = Modifier.padding(bottom = 8.dp))
      }
      if (text.isNotBlank()) {
        FluidMarkdown(text, streaming = streaming, animateFirst = freshStart, style = markdownStyle, options = FluidAssistantMarkdownOptions)
      }
      lines.instead?.let { OutcomeText(it) }
      lines.after?.let {
        Spacer(Modifier.height(6.dp))
        OutcomeText(it, small = true)
      }
      // I chip arrivano con il messaggio finale: sfumano dentro invece di comparire di colpo.
      AnimatedVisibility(
        visible = chips.isNotEmpty(),
        enter = if (reducedMotion) EnterTransition.None else fadeIn(FluidMotion.fadeIn()),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(FluidMotion.fadeOut()),
      ) {
        Column {
          Spacer(Modifier.height(10.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip -> FluidChip(label = chipLabel(chip), selected = false, onClick = { onChip(chip) }) }
          }
        }
      }
      // La riserva automatica e' accesa di default: un servizio diverso da quello scelto che risponde
      // senza dirlo e' un modello che cambia in silenzio.
      if (!switchLine.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(switchLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (actions != null) {
        AnimatedVisibility(
          visible = settled,
          enter = if (reducedMotion) EnterTransition.None else fadeIn(FluidMotion.fadeIn()),
          exit = if (reducedMotion) ExitTransition.None else fadeOut(FluidMotion.fadeOut()),
        ) {
          Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
      }
    }
  }

  if (style == FluidMessageStyle.Card) {
    FluidCard(modifier = modifier, highlighted = outcome is FluidBubbleOutcome.Failed, glass = glass) {
      Text(strings.assistantLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(4.dp))
      Content()
    }
  } else {
    Box(modifier.fillMaxWidth()) { Content() }
  }
}

@Composable
private fun OutcomeText(line: FluidOutcomeLine, small: Boolean = false) {
  Text(
    text = line.text,
    style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
    color = if (line.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * La risposta in corso quando la conversazione e' nuova e non c'e' ancora un messaggio salvato: la
 * domanda (dallo stato, finche' la riga non arriva) e sotto la riga di stato e il testo. Mostra anche
 * il testo di `Done`, `Failed` e `Cancelled`: fra la fine della risposta e il primo messaggio salvato
 * passa un fotogramma, e in quel fotogramma il testo non deve sparire.
 *
 * Tutto dallo [AssistantState]: l'app che non ha una riga su disco (ClasseViva a conversazione
 * nuova, PampAI alla prima domanda) lo mette in lista cosi'. La bolla della domanda e' la stessa che
 * [FluidUserBubble] disegnera' un attimo dopo, nello stesso posto: il cambio non si vede.
 */
@Composable
fun FluidLiveBubble(
  state: AssistantState,
  pending: PendingConfirmation?,
  onConfirm: (Long, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  style: FluidMessageStyle = FluidMessageStyle.Bubble,
  strings: FluidChatStrings = FluidChatStrings(),
  assistantStrings: FluidAssistantStrings = FluidAssistantStrings(),
  chipLabel: (AnswerChip) -> String = ::fluidDefaultChipLabel,
  onChip: (AnswerChip) -> Unit = {},
  markdownStyle: FluidMarkdownStyle = FluidMarkdownDefaults.style(),
) {
  val text = FluidAssistantTexts.bodyText(state).orEmpty()
  val question = FluidAssistantTexts.question(state)
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    if (!question.isNullOrBlank()) FluidUserBubble(text = question, style = style, strings = strings)
    FluidAssistantBubble(
      text = text,
      style = style,
      streaming = state is AssistantState.Answering,
      settled = !state.isBusy,
      contentKey = "live",
      status = FluidAssistantTexts.statusLine(state, assistantStrings),
      statusError = state is AssistantState.Failed,
      confirmation = pending,
      onConfirm = onConfirm,
      busy = state.isBusy,
      chips = (state as? AssistantState.Done)?.chips.orEmpty(),
      chipLabel = chipLabel,
      onChip = onChip,
      switchLine = (state as? AssistantState.Done)?.let { FluidAssistantTexts.switchLine(it.switches, assistantStrings) },
      animateSize = true,
      strings = strings,
      assistantStrings = assistantStrings,
      markdownStyle = markdownStyle,
    )
  }
}

/**
 * La pagina vuota di una chat: un segno, il nome al centro e una frase sotto, e dei suggerimenti
 * toccabili. La frase e i suggerimenti li sceglie l'app (PampAI li ricava dall'ora del giorno): sono
 * dominio, e si scelgono **una volta per apertura**, non a ogni ricomposizione, che e' esattamente il
 * contrario di un saluto. In una lista passa `Modifier.fillParentMaxHeight()`.
 */
@Composable
fun FluidEmptyGreeting(
  title: String,
  greeting: String,
  suggestions: List<String>,
  onSuggestion: (String) -> Unit,
  modifier: Modifier = Modifier,
  mark: (@Composable () -> Unit)? = null,
  bottomInset: androidx.compose.ui.unit.Dp = 96.dp,
) {
  Box(
    modifier
      .fillMaxWidth()
      .padding(bottom = bottomInset),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      mark?.let {
        it()
        Spacer(Modifier.height(6.dp))
      }
      Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
      Spacer(Modifier.height(8.dp))
      Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(horizontal = 8.dp),
        ) {
          suggestions.forEach { suggestion -> FluidChip(label = suggestion, selected = false, onClick = { onSuggestion(suggestion) }) }
        }
      }
    }
  }
}
