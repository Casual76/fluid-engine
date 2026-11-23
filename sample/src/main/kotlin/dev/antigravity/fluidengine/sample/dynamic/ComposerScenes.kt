package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.chat.FluidAssistantBubble
import dev.antigravity.fluidengine.aiui.chat.FluidComposerChip
import dev.antigravity.fluidengine.aiui.chat.FluidComposerPill
import dev.antigravity.fluidengine.aiui.chat.FluidComposerVoiceLine
import dev.antigravity.fluidengine.aiui.chat.FluidUserBubble
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantField
import dev.antigravity.fluidengine.aiui.chat.fluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.rememberFluidComposerState
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidComposerSlots
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTrailing
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutcome
import dev.antigravity.fluidengine.ui.fluid.activity.rememberFluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.icons.Movie
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F6B.7: il composer dentro il campo. I casi delle app di Alessio rifatti col solo engine (e engine-ai-ui): nessuna API
// di movimento qui. La crescita di riga in riga, il vassoio, il tasto a stati, la riga della voce e l'alone sono del campo.
//
// - `messaggio`: Messaggi e Mail, una chat con Anna. Il campo «Messaggio» in basso cresce fino a cinque righe col bordo
//   alto che sale; il «+» apre il vassoio degli allegati sopra il testo; il tasto a destra è il microfono, poi «Invia»
//   (con l'anello mentre l'invio lavora), poi lo stop mentre si detta. Dopo l'invio il campo resta aperto. La lista
//   lascia il posto al campo cresciuto, con e senza tastiera (la correzione di F6B.13).
// - `assistente`: PampAI, il composer nuovo dell'assistente (`fluidAssistantField`) con l'alone attorno al campo, i chip
//   del modello, le pillole, la voce. Mentre l'assistente risponde la sua attività prende il posto del titolo (F6B.10).

/** I tag dei test e delle riprese. */
internal object ComposerSceneTags {
  const val Chat = "composer:chat"
  const val Status = "composer:stato"
  fun message(index: Int) = "composer:messaggio:$index"
}

/** Un messaggio della chat: di chi lo manda e cosa dice. */
private data class ChatLine(val mine: Boolean, val text: String)

private val ChatOpening = listOf(
  ChatLine(false, "Ciao! Domani ci vediamo per il progetto della sede?"),
  ChatLine(true, "Sì, alle dieci in sala riunioni."),
  ChatLine(false, "Perfetto. Porti tu le piante del secondo piano?"),
  ChatLine(true, "Le porto, e anche il calendario del trasloco."),
  ChatLine(false, "Marta dice che il magazzino va spostato prima degli uffici."),
  ChatLine(true, "Ha ragione: due settimane per il magazzino, una per gli uffici."),
  ChatLine(false, "E i fornitori dei mobili? Li segue Giorgio?"),
  ChatLine(true, "Sì, Giorgio. La rete e i telefoni invece Sara."),
  ChatLine(false, "Ok. Il preventivo della rete va firmato entro venerdì."),
  ChatLine(true, "Lo firmo giovedì, così Sara ordina subito."),
  ChatLine(false, "Grande. Ultima cosa: la festa di inaugurazione."),
  ChatLine(true, "Pensavo a un lunedì sera di marzo, con le famiglie."),
)

/** Una riga della chat: le mie a destra (la bolla dell'engine), quelle di Anna a sinistra in una card. */
@Composable
private fun ChatRow(line: ChatLine, modifier: Modifier = Modifier) {
  if (line.mine) {
    FluidUserBubble(line.text, modifier = modifier)
  } else {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
      FluidCard(Modifier.fillMaxWidth(0.82f)) { Text(line.text, style = MaterialTheme.typography.bodyLarge) }
    }
  }
}

/**
 * Messaggi e Mail: il campo «Messaggio» col composer dentro (`FluidSearchSpec.message(…, composer = FluidComposerSlots(…))`).
 * L'invio passa da un esecutore dei tasti con esito: mentre lavora l'anello gira attorno alla freccia, poi la spunta.
 */
@Composable
internal fun MessageComposerScene(onBack: () -> Unit) {
  var text by rememberSaveable { mutableStateOf("") }
  var active by rememberSaveable { mutableStateOf(false) }
  var listening by remember { mutableStateOf(false) }
  val lines = remember { mutableStateListOf<ChatLine>().apply { addAll(ChatOpening) } }
  val attachments = remember { mutableStateListOf<String>() }
  val send = rememberFluidActionRunner(FluidActionPreset.Send)
  val trailing = if (listening) {
    FluidSearchTrailing.stop(onStop = { listening = false; text = "Ci vediamo domani alle dieci" }, label = "Smetti di dettare")
  } else {
    FluidSearchTrailing.voice(onVoice = { listening = true }, sending = send.state)
  }
  val composer = FluidComposerSlots(
    leading = {
      FluidCapsuleAction(FluidIcons.Add, "Aggiungi un allegato", onClick = {
        attachments += if (attachments.size % 2 == 0) "Foto ${attachments.size + 1}" else "Video ${attachments.size + 1}"
      }, size = FluidSearchFieldDefaults.ActionSize)
    },
    attachments = if (attachments.isEmpty()) null else {
      {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          attachments.forEach { name ->
            FluidComposerPill(
              icon = if (name.startsWith("Foto")) FluidIcons.Image else FluidIcons.Movie,
              label = name,
              onClick = { attachments.remove(name) },
              removeDescription = "Togli $name",
            )
          }
        }
      }
    },
    trailing = trailing,
    voiceLine = if (listening) {
      { FluidComposerVoiceLine(partial = "Ci vediamo domani alle", transcribing = false, amplitude = { 0.45f }, onTap = { listening = false }) }
    } else null,
  )
  FluidScreen(
    title = "Anna",
    onBack = onBack,
    toolbar = { title(FluidToolbarTitleMode.Inline) },
    search = FluidSearchSpec.message(
      text, { text = it },
      onSend = {
        val sent = text
        val files = attachments.toList()
        text = ""
        attachments.clear()
        send.run {
          delay(700L)
          lines += ChatLine(true, if (files.isEmpty()) sent else "$sent (${files.joinToString()})")
        }
      },
      active = active, onActiveChange = { active = it }, composer = composer,
    ),
  ) {
    items(lines.size, key = { it }) { index ->
      ChatRow(lines[index], Modifier.padding(vertical = 4.dp).testTag(ComposerSceneTags.message(index)))
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Scrivi più righe: il campo cresce fino a cinque col bordo alto, poi il testo scorre dentro. Il «+» aggiunge un " +
          "allegato sopra il testo; toccato, se ne va. A fine lista l'ultimo messaggio resta sopra il campo, anche con la tastiera.",
      )
    }
  }
}

/** Un turno della chat con l'assistente. */
private data class AssistantTurn(val user: Boolean, val text: String)

private val AssistantOpening = listOf(
  AssistantTurn(true, "Riassumi la riunione del lunedì in tre punti."),
  AssistantTurn(false, "1. Il progetto della nuova sede è approvato, restano le sale del secondo piano.\n" +
    "2. Il trasloco comincia lunedì 3 febbraio: prima il magazzino, poi gli uffici.\n" +
    "3. Il preventivo della rete va firmato entro venerdì."),
)

/**
 * PampAI: il composer dell'assistente con l'alone (`fluidAssistantField`, F6B.7). Mentre risponde, l'assistente pubblica la
 * sua attività (`FluidActivity.assistant`) e la pillola prende il posto del titolo; finita la risposta, la pillola passa il
 * testimone alla chat e se ne va.
 */
@Composable
internal fun AssistantComposerScene(onBack: () -> Unit) {
  val composer = rememberFluidComposerState()
  val activities = rememberFluidActivityHostState()
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var listening by remember { mutableStateOf(false) }
  var attached by remember { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }
  val turns = remember { mutableStateListOf<AssistantTurn>().apply { addAll(AssistantOpening) } }
  val action = fluidComposerAction(composer.text.isNotBlank(), busy = busy, listening = listening, speaking = false)
  fun ask(question: String) {
    turns += AssistantTurn(true, question)
    busy = true
    activities.publish(FluidActivity.assistant(detail = question, onStop = { job?.cancel() }))
    job = scope.launch {
      try {
        delay(2_400L)
        turns += AssistantTurn(false, "Ecco: ${question.lowercase().removeSuffix("?")}. Ne parlo con Marta e ti aggiorno.")
      } finally {
        busy = false
        activities.finish("assistant", FluidActivityOutcome.HandedOff)
      }
    }
  }
  FluidActivityHost(activities) {
    FluidScreen(
      title = "Aria",
      onBack = onBack,
      toolbar = {
        title(FluidToolbarTitleMode.Inline)
        button("nuova", FluidIcons.Edit, "Nuova chat", onClick = { turns.clear() })
      },
      search = fluidAssistantField(
        state = composer,
        action = action,
        onSend = {
          val question = composer.text.trim()
          if (question.isNotEmpty() && !busy) {
            composer.sent()
            attached = false
            ask(question)
          }
        },
        onStop = { job?.cancel() },
        onVoice = { listening = true },
        onStopListening = {
          listening = false
          composer.fill("Che cosa manca per il trasloco?")
        },
        onAdd = { attached = true },
        chips = { FluidComposerChip("Modello · Auto", onClick = {}) },
        pills = if (attached) {
          { FluidComposerPill(FluidIcons.Image, "Pianta del secondo piano", onClick = { attached = false }) }
        } else null,
        voiceLine = { FluidComposerVoiceLine(partial = null, transcribing = false, amplitude = { 0.5f }, onTap = { listening = false }) },
        requestMicPermission = false,
      ),
    ) {
      items(turns.size, key = { it }) { index ->
        val turn = turns[index]
        val modifier = Modifier.padding(vertical = 4.dp).testTag(ComposerSceneTags.message(index))
        if (turn.user) FluidUserBubble(turn.text, modifier = modifier) else FluidAssistantBubble(turn.text, modifier = modifier)
      }
      item(key = "stato") {
        Text(
          if (busy) "Aria sta rispondendo…" else "Chiedi qualcosa ad Aria",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).testTag(ComposerSceneTags.Status),
        )
      }
      item(key = "nota") {
        FluidSectionFootnote(
          "L'alone è fermo a riposo e si anima a campo aperto, con la voce e mentre Aria risponde. Mentre risponde, la " +
            "pillola di attività sta al posto del titolo.",
        )
      }
    }
  }
}
