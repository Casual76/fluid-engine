package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.aiui.chat.FluidFollowAnswer
import dev.antigravity.fluidengine.aiui.markdown.FluidAssistantMarkdownOptions
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownStyle
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent

/** L'etichetta di un chip quando l'app non ne dice una: il suo valore, o il suo id. */
fun fluidDefaultChipLabel(chip: AnswerChip): String = chip.value ?: chip.id

/**
 * La card sotto l'aureola: domanda in piccolo, riga di stato col pallino che respira, risposta in
 * streaming con il markdown, chip sotto, tasto stop discreto mentre lavora. E' vetro sopra la
 * pagina, non un dialogo: quello che c'e' dietro resta leggibile.
 *
 * E' la card di ClasseViva e di FluidWeather (stessa card, due fork) piu' due cose che aveva solo
 * PampAI: la riga che dice **chi ha risposto** quando il servizio e' cambiato ("Ha risposto Gemini:
 * Groq era al limite" — la riserva e' accesa di default, e un modello che cambia in silenzio non e'
 * onesto) e il tasto **"Dettagli"** su un fallimento. Il corpo segue la risposta che si forma
 * (`FluidFollowAnswer`): in CV la card si allungava fino all'altezza massima e le parole nuove
 * finivano sotto il bordo, fuori vista.
 *
 * Il dominio entra da tre parti, e le mette l'app: le frasi dei gruppi di strumenti in
 * [strings] (`statusFor`), le etichette dei chip in [chipLabel], e gli slot [header] (la traccia dei
 * passi di PampAI, la sua maniglia "Apri in...") e [footer].
 *
 * **Il vetro** (4.0, F3.2e): un pannello `Modal` del piano flottante. Da sola sulla pagina sta in un
 * `FluidGlassContainer` con un pezzo solo e **scurisce in continuo** con ciò che ha dietro, come i grandi dell'engine (il
 * foglio, la notifica), col testo nell'inchiostro e nello schema di colori del suo lato. Sul lato del tema è il vetro di
 * prima: lo stesso corpo, filo, ombra e colori. Lo stop e i chip che porta (e i vetri degli slot) sono vetro annidato
 * della card: ne campionano il materiale e ne prendono il lato. Il `modifier` sta attorno al contenitore, che passa
 * alla card i vincoli minimi.
 *
 * @param onTapBody un tocco sul corpo: di solito zittisce la voce che sta leggendo.
 * @param tapHaptic cosa si sente a quel tocco. ClasseViva: niente (zittire non e' un momento);
 *   FluidWeather: il `Tap` di serie. Qui di serie niente.
 * @param onDetails con un callback, un fallimento ha il tasto "Dettagli" che porta il resoconto.
 * @param markdownStyle colori e misure del Markdown del corpo. Null, di serie: quello del **lato del vetro**
 *   ([FluidMarkdownDefaults.style] letto dentro la card), così il testo segue la card quando scurisce (F3.8a: prima era
 *   un valore di serie nella firma, letto fuori dal contenitore, e in tema chiaro su un fondo scuro la risposta restava
 *   nera su una card scurita). Uno stile dell'app resta quello dell'app.
 * @param body cambia la resa del corpo (di serie [FluidMarkdown]); riceve il testo e se sta arrivando.
 */
@Composable
fun FluidAssistantCard(
  state: AssistantState,
  pending: PendingConfirmation?,
  backdrop: GlassBackdropState,
  onStop: () -> Unit,
  onChip: (AnswerChip) -> Unit,
  onConfirm: (Long, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  onTapBody: () -> Unit = {},
  strings: FluidAssistantStrings = FluidAssistantStrings(),
  chipLabel: (AnswerChip) -> String = ::fluidDefaultChipLabel,
  maxBodyHeight: Dp = 380.dp,
  markdownStyle: FluidMarkdownStyle? = null,
  tapHaptic: FluidHapticEvent? = null,
  onDetails: ((FluidFailureReport) -> Unit)? = null,
  showSwitchLine: Boolean = true,
  header: (@Composable ColumnScope.() -> Unit)? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  body: (@Composable (text: String, streaming: Boolean) -> Unit)? = null,
) {
  val question = FluidAssistantTexts.question(state)
  // La pellicola si legge qui, fuori dal contenitore: è quella del lato del tema, come prima. Il lato lo fa il contenitore.
  val tint = GlassDefaults.modalTint()
  FluidAssistantPanelGlass(modifier) { outer ->
    Column(
      modifier = outer
        .fillMaxWidth()
        .fluidGlassPanel(
          backdrop = backdrop,
          tint = tint,
          shape = CardShape,
          role = FluidAssistantSurface.Card.role,
          large = FluidAssistantSurface.Card.large,
        )
        .fluidPressable(onClick = onTapBody, pressedScale = 1f, role = null, haptic = tapHaptic)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      // Lo stop, i chip e i vetri degli slot sono vetro annidato della card, non pezzi del suo contenitore.
      FluidAssistantPanelContent {
        // Lo stile di serie si legge qui dentro, dove lo schema di colori è quello del lato della card.
        val bodyStyle = fluidAssistantMarkdownStyle(markdownStyle)
        header?.invoke(this)
        Row(verticalAlignment = Alignment.Top) {
          Column(Modifier.weight(1f)) {
            if (!question.isNullOrBlank()) {
              Text(
                text = question,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.height(6.dp))
            }
            FluidStatusLine(state, strings)
          }
          if (state.isBusy && state !is AssistantState.AwaitingConfirmation) {
            Spacer(Modifier.width(8.dp))
            FluidStopPill(backdrop, onStop, contentDescription = strings.stop)
          }
        }
        val text = FluidAssistantTexts.bodyText(state)
        AnimatedContent(
          targetState = text != null,
          transitionSpec = { fadeIn(FluidMotion.fadeIn(180)).togetherWith(fadeOut(FluidMotion.fadeOut(120))) },
          label = "assistantBody",
        ) { hasBody ->
          if (hasBody && text != null) {
            val scroll = rememberScrollState()
            FluidFollowAnswer(scroll, following = state is AssistantState.Answering)
            Column(
              Modifier
                .padding(top = 10.dp)
                .heightIn(max = maxBodyHeight)
                .verticalScroll(scroll),
            ) {
              val streaming = state is AssistantState.Answering
              if (body != null) body(text, streaming) else FluidMarkdown(text, streaming = streaming, animateFirst = false, style = bodyStyle, options = FluidAssistantMarkdownOptions)
            }
          }
        }
        if (pending != null && (state.isBusy || state is AssistantState.AwaitingConfirmation)) {
          Spacer(Modifier.height(12.dp))
          FluidConfirmationRow(pending, onConfirm = { onConfirm(pending.id, it) }, strings = strings)
        }
        if (state is AssistantState.Done) {
          if (state.chips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              state.chips.forEach { chip ->
                FluidChipPill(label = chipLabel(chip), backdrop = backdrop, onClick = { onChip(chip) })
              }
            }
          }
          val switchLine = if (showSwitchLine) FluidAssistantTexts.switchLine(state.switches, strings) else null
          if (switchLine != null) {
            Spacer(Modifier.height(10.dp))
            Text(switchLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (state is AssistantState.Failed && onDetails != null) {
          Spacer(Modifier.height(10.dp))
          FluidButton(
            text = strings.details,
            onClick = { onDetails(FluidFailureReport.of(state)) },
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
          )
        }
        footer?.invoke(this)
      }
    }
  }
}

/** La sagoma della card: il raggio dei fogli. Una sola istanza, perché l'aspetto del pezzo non cambi a ogni composizione. */
private val CardShape = ContinuousCornerShape(FluidRadius.Sheet)

/**
 * La riga di stato: una frase che sale e prende il posto della precedente ("trascrivo" → "guardo i
 * voti" → "rispondo"), invece di cambiare di colpo come un cartello che si ribalta, col pallino che
 * respira finche' l'assistente lavora. Rossa per un fallimento.
 */
@Composable
fun FluidStatusLine(state: AssistantState, strings: FluidAssistantStrings, modifier: Modifier = Modifier) {
  val text = FluidAssistantTexts.statusLine(state, strings) ?: return
  val error = state is AssistantState.Failed
  AnimatedContent(
    targetState = text,
    modifier = modifier,
    transitionSpec = {
      (slideInVertically(FluidMotion.intOffset(FluidMotion.DampingStandard, FluidMotion.ResponseSnappy)) { it / 2 } + fadeIn(FluidMotion.fadeIn(140)))
        .togetherWith(slideOutVertically(FluidMotion.intOffset(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy)) { -it / 2 } + fadeOut(FluidMotion.fadeOut(100)))
    },
    label = "assistantStatus",
  ) { shown ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (state.isBusy) {
        FluidBreathingDot()
        Spacer(Modifier.width(8.dp))
      }
      Text(
        text = shown,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}
