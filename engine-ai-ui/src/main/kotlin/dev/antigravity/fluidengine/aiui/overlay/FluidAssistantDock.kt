package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.aiui.markdown.FluidAssistantMarkdownOptions
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidHairline
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownStyle
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassEdge
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.icons.Stop

/**
 * L'assistente come **pannello ancorato** in basso, invece di aureola + barra + card: e' il
 * layout di Fluid-transit, dove sotto c'e' una mappa viva e il momento in cui uno vuole guardarla e'
 * proprio quello in cui l'assistente sta cercando una linea. Non e' una schermata e non e' un
 * dialogo: un pannello di vetro appoggiato in basso, che lascia viva la mappa sopra.
 *
 * Dall'alto: la testata (il pallino che segue il microfono, la riga di stato, il tasto per chiudere),
 * il corpo con la risposta, i suggerimenti toccabili a riposo, i chip, la conferma di un'azione che
 * scrive, e la riga di ingresso (campo, e un tasto che e' microfono, stop o invio secondo cosa c'e'
 * scritto e cosa sta facendo l'assistente).
 *
 * Quello che il file di Fluid-transit aveva imparato a sue spese e che qui resta:
 *  - **il corpo prende quello che avanza** (`weight(1f, fill = false)` dentro una colonna con
 *    `imePadding`): con la tastiera aperta e una risposta lunga a carattere grande il tetto di 280 dp
 *    veniva misurato per primo e la riga per scrivere restava con gli avanzi, campo e tasto tagliati
 *    a meta'. Adesso testata, chip, conferma e riga di ingresso si misurano prima, e la risposta
 *    scorre nello spazio che resta;
 *  - **la testata cresce col testo**: una frase d'errore a carattere grande va su quattro righe, e
 *    46 dp fissi ne mostravano una e mezza, tagliata senza puntini proprio dove dice cosa fare;
 *  - **a riposo, esempi** ("Per esempio…"): "Chiedimi qualcosa" a vuoto non dice cosa l'assistente
 *    sa fare. Li scrive l'app ([suggestions]), perche' sono dominio;
 *  - **chi usa un lettore di schermo** sente il riquadro che si apre (`paneTitle`), lo stato che cambia
 *    (`liveRegion`) e la risposta, una volta, finita.
 *
 * @param suggestions gli esempi toccabili quando non c'e' niente in corso e il campo e' vuoto.
 * @param maxChips quanti chip della risposta (da `Done.chips`); gli altri non si mostrano.
 * @param maxBodyHeight il tetto del corpo, entro quello che la tastiera lascia.
 * @param announceAnswer legge la risposta a chi usa TalkBack, quando e' finita (i primi 600 caratteri).
 */
@Composable
fun FluidAssistantDock(
  state: AssistantState,
  micLevel: MicLevel,
  pending: PendingConfirmation?,
  backdrop: GlassBackdropState,
  actions: FluidAssistantActions,
  modifier: Modifier = Modifier,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
  chipLabel: (AnswerChip) -> String = ::fluidDefaultChipLabel,
  suggestions: List<String> = emptyList(),
  maxChips: Int = 3,
  maxBodyHeight: Dp = 280.dp,
  announceAnswer: Boolean = true,
  markdownStyle: FluidMarkdownStyle = FluidMarkdownDefaults.style(),
  onClose: () -> Unit = {},
) {
  var draft by remember { mutableStateOf("") }
  val body = FluidAssistantTexts.bodyText(state).orEmpty()

  // Per chi usa un lettore di schermo: la risposta, una volta, finita.
  val view = LocalView.current
  LaunchedEffect(state is AssistantState.Done, body) {
    if (announceAnswer && state is AssistantState.Done && body.isNotBlank()) {
      @Suppress("DEPRECATION")
      view.announceForAccessibility(body.take(AnnounceChars))
    }
  }

  fun send(text: String) {
    if (text.isBlank() || state is AssistantState.Listening) return
    actions.onAskText(text)
    draft = ""
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .imePadding()
      .semantics { paneTitle = strings.title }
      .glassSurface(
        state = backdrop,
        tint = GlassDefaults.floatingTint(),
        shape = ContinuousCornerShape(FluidRadius.Sheet),
        edge = GlassEdge.None,
        role = GlassRole.Floating,
      ),
  ) {
    // --- testata: cosa sta facendo, e come si chiude ----------------------------------------------
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 8.dp)
        .heightIn(min = 46.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidMicDot(level = micLevel.level, speaking = micLevel.speaking, busy = state.isBusy)
      Spacer(Modifier.width(10.dp))
      Text(
        text = if (pending != null) strings.needsConfirmation else dockStatus(state, strings),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 6.dp)
          .semantics { liveRegion = LiveRegionMode.Polite },
      )
      FluidIcon(
        imageVector = FluidIcons.Close,
        contentDescription = strings.close,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .size(38.dp)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = {
              actions.onCancel()
              actions.onDismiss()
              onClose()
            },
          )
          .padding(9.dp),
      )
    }

    if (body.isNotBlank()) {
      FluidHairline(modifier = Modifier.padding(horizontal = 20.dp))
      // Il corpo prende quello che resta, non uno spazio fisso: vedi la nota sopra.
      val scroll = rememberScrollState()
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .heightIn(max = maxBodyHeight)
          .verticalScroll(scroll)
          .padding(horizontal = 20.dp, vertical = 12.dp),
      ) {
        FluidMarkdown(body, streaming = state is AssistantState.Answering, animateFirst = false, style = markdownStyle, options = FluidAssistantMarkdownOptions)
      }
    }

    // --- a riposo: gli esempi da cui cominciare ---------------------------------------------------
    if (state is AssistantState.Idle && draft.isBlank() && suggestions.isNotEmpty()) {
      FluidHairline(modifier = Modifier.padding(horizontal = 20.dp))
      // Come il corpo della risposta, prende quello che avanza e scorre: a riposo la tastiera e'
      // aperta proprio perche' si e' appena toccato il campo, e con intestazione e tre righe da
      // 48 dp fisse un telefono piccolo o in orizzontale col carattere grande tagliava il campo.
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      ) {
        Text(
          text = strings.examples,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .padding(bottom = 2.dp)
            .semantics { heading() },
        )
        for (suggestion in suggestions) {
          Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { actions.onAskText(suggestion) },
              )
              .padding(vertical = 6.dp),
          )
        }
      }
    }

    // --- i chip della risposta (i posti che ha nominato), da aprire con un tocco -----------------
    val chips = (state as? AssistantState.Done)?.chips.orEmpty().take(maxChips)
    if (chips.isNotEmpty()) {
      // A capo invece che tutti su una riga: coi nomi veri delle fermate il primo si prendeva quasi
      // tutta la larghezza e gli altri ricevevano quaranta dp, con il testo a capo ogni due lettere.
      FlowRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (chip in chips) {
          FluidChipPill(label = chipLabel(chip), backdrop = backdrop, maxLines = 2, onClick = { actions.onChip(chip) })
        }
      }
    }

    // --- la conferma di un'azione che scrive ------------------------------------------------------
    // Si guarda l'azione IN ATTESA, non lo stato: lo stato lo scrivono in sei punti diversi
    // l'orchestratore e la sessione, e basta che uno arrivi dopo perche' il tasto non compaia mai.
    if (pending != null) {
      FluidConfirmationRow(
        pending = pending,
        onConfirm = { actions.onConfirm(pending.id, it) },
        strings = strings,
        fillButtons = true,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
      )
    }

    // --- la riga in fondo: parlare o scrivere -----------------------------------------------------
    FluidHairline(modifier = Modifier.padding(horizontal = 20.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      FluidTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = strings.askPlaceholder,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        // Il tasto Invio della tastiera diceva "Invia" e non faceva niente: si doveva cercare il
        // pulsante accanto al campo.
        keyboardActions = KeyboardActions(onSend = { send(draft) }),
        modifier = Modifier.weight(1f),
      )
      when {
        state is AssistantState.Listening -> FluidGlassIconButton(onClick = actions.onStopListening, backdrop = backdrop) {
          FluidIcon(FluidIcons.Filled.Stop, contentDescription = strings.finishedSpeaking, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        draft.isBlank() -> FluidGlassIconButton(onClick = actions.onAskVoice, backdrop = backdrop) {
          FluidIcon(FluidIcons.Mic, contentDescription = strings.voice, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        else -> FluidGlassIconButton(onClick = { send(draft) }, backdrop = backdrop) {
          FluidIcon(FluidIcons.Send, contentDescription = strings.send, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
      }
    }
  }
}

/**
 * Il pallino della testata: un cerchio che respira col microfono mentre ascolta e pulsa piano
 * mentre lavora. Non e' decorazione — e' l'unico segno che dice se il telefono ti sta davvero
 * sentendo, che e' la prima domanda di chi parla a una macchina.
 */
@Composable
fun FluidMicDot(level: Float, speaking: Boolean, busy: Boolean, modifier: Modifier = Modifier) {
  val target = micDotScale(level, speaking, busy)
  val scale by animateFloatAsState(targetValue = target, animationSpec = FluidMotion.snappy(), label = "micDot")
  Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size((12 * scale).dp)
        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
    )
  }
}

/** La scala del pallino: parlando segue la voce (fino a una volta e mezza), al lavoro e' un po' piu' grande, a riposo uno. */
internal fun micDotScale(level: Float, speaking: Boolean, busy: Boolean): Float = when {
  speaking -> 1f + level.coerceIn(0f, 1f) * 0.5f
  busy -> 1.15f
  else -> 1f
}

/** La frase della testata: a riposo "chiedimi qualcosa", altrimenti lo stato; a risposta finita "Fatto". */
internal fun dockStatus(state: AssistantState, strings: FluidAssistantStrings): String = when (state) {
  AssistantState.Idle -> strings.idleStatus
  is AssistantState.Done -> strings.dockDone
  else -> FluidAssistantTexts.statusLine(state, strings) ?: strings.title
}

private const val AnnounceChars = 600
