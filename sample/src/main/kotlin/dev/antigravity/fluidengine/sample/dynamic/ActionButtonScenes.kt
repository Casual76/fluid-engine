package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionWords
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionWork
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CloudOff
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import kotlinx.coroutines.delay

// I tasti con stato di lavoro ed esito (F6B.3a, F6B.3b): i sei preset e uno su misura, nei casi di Mail («Invia» nella
// barra del compositore), Notes («Salva»), Fluidify (lo scaricamento con pausa e ripresa) e Gallery (il preferito).
// Il lavoro è finto e lo decide la scena (riesce, non riesce, breve); stati, tempi, glifi, parole e aptica sono
// dell'engine. Nessun numero di movimento qui: le durate sotto sono quanto dura il lavoro simulato, non l'animazione.

/** Come va a finire la prossima azione: la scena lo sceglie per provare i tre casi. */
internal enum class ActionOutcome(val label: String) {
  Success("Riesce"),
  Failure("Non riesce"),
  Short("Lavoro breve"),
}

/** Quanto dura il lavoro finto: lungo abbastanza da mostrare l'anello, oppure sotto la soglia dei 300 ms. */
private const val SimulatedWorkMs = 1_600L
private const val SimulatedShortWorkMs = 150L
private const val SimulatedSteps = 40

/** I tag dei test. */
internal object ActionSceneTags {
  const val Outcomes = "tasti:esito"
  const val Grid = "tasti:preset"
  const val Pause = "tasti:pausa"
  const val Stop = "tasti:ferma"

  fun outcome(outcome: ActionOutcome): String = "tasti:esito:${outcome.name}"
}

/** Un tasto su misura: «Sincronizza», con le sue icone e le sue parole e i tempi e i modi dei preset. */
internal val SyncActionPreset: FluidActionPreset = FluidActionPreset.custom(
  idle = FluidIcons.Sync,
  success = FluidIcons.Check,
  failure = FluidIcons.CloudOff,
  words = FluidActionWords(
    idle = "Sincronizza",
    working = "Sincronizzazione in corso…",
    succeeded = "Sincronizzato",
    failed = "Sincronizzazione non riuscita",
    cancelled = "Sincronizzazione annullata",
  ),
)

/** Il lavoro finto: aspetta, avanza se è un trasferimento, e non riesce se la scena lo chiede. */
private suspend fun FluidActionWork.simulate(outcome: ActionOutcome, transfer: Boolean = false, paused: () -> Boolean = { false }) {
  if (outcome == ActionOutcome.Short) {
    delay(SimulatedShortWorkMs)
    return
  }
  if (!transfer) {
    delay(SimulatedWorkMs)
    if (outcome == ActionOutcome.Failure) fail()
    return
  }
  var step = 0
  while (step < SimulatedSteps) {
    delay(SimulatedWorkMs / SimulatedSteps)
    if (paused()) continue
    step++
    progress = step.toFloat() / SimulatedSteps
    // Un trasferimento che non riesce si ferma a metà strada, come una rete che cade.
    if (outcome == ActionOutcome.Failure && step >= SimulatedSteps * 3 / 5) fail()
  }
}

/**
 * La scena dei tasti con esito. In alto, la barra del compositore di Mail con «Invia» (`primary(…, action = Send, state
 * = …)`); sotto, i sei preset da soli, uno su misura e lo scaricamento di Fluidify con pausa e ripresa. Lo stato è
 * dell'app: gli esecutori ([rememberFluidActionRunner]) trasformano il lavoro negli stati, e la pausa la aggiunge la
 * scena sopra lo stato dell'esecutore, come farebbe un'app che sa fermare il suo scaricamento.
 */
@Composable
internal fun ActionButtonsScene(onBack: () -> Unit) {
  var outcome by rememberSaveable { mutableStateOf(ActionOutcome.Success) }
  var favorite by rememberSaveable { mutableStateOf(false) }
  var paused by rememberSaveable { mutableStateOf(false) }
  val send = rememberFluidActionRunner(FluidActionPreset.Send)
  val save = rememberFluidActionRunner(FluidActionPreset.Save)
  val copy = rememberFluidActionRunner(FluidActionPreset.Copy)
  val download = rememberFluidActionRunner(FluidActionPreset.Download)
  val upload = rememberFluidActionRunner(FluidActionPreset.Upload)
  val heart = FluidActionPreset.favorite(favorite)
  val like = rememberFluidActionRunner(heart)
  val sync = rememberFluidActionRunner(SyncActionPreset)
  // La pausa è dell'app: sopra lo stato dell'esecutore, la stessa lettura dell'avanzamento con `paused = true`.
  val downloadState = remember(download.state, paused) {
    val now = download.state
    if (paused && now is FluidActionState.Working) FluidActionState.Working(now.progress, paused = true) else now
  }
  val startSend = { send.run { simulate(outcome) }; Unit }

  FluidScreen(
    title = "Tasti con esito",
    subtitle = "Invia, Salva, Copia, Scarica, Carica, Preferito e uno su misura",
    onBack = onBack,
    toolbar = {
      // Mail: «Invia» nella barra del compositore. Si carica mentre lavora, a invio riuscito parte.
      primary("invia", FluidIcons.Send, "Invia", onClick = startSend, action = FluidActionPreset.Send, state = send.state)
    },
  ) {
    item(key = "esito-intestazione") {
      FluidSectionHeader(title = "La prossima azione", detail = "Scegli come finisce il lavoro, poi tocca un tasto.")
    }
    item(key = "esito") {
      Row(Modifier.testTag(ActionSceneTags.Outcomes), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionOutcome.entries.forEach { choice ->
          FluidChip(
            label = choice.label,
            selected = outcome == choice,
            onClick = { outcome = choice },
            modifier = Modifier.testTag(ActionSceneTags.outcome(choice)),
          )
        }
      }
    }
    item(key = "preset-intestazione") {
      FluidSectionHeader(
        title = "I sei preset",
        detail = "Il lavoro è un anello sottile dentro il vetro; l'esito si capisce dall'icona, anche quello negativo.",
      )
    }
    item(key = "preset") {
      Column(Modifier.testTag(ActionSceneTags.Grid), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          ActionCell("Invia · Mail") {
            FluidActionButton(FluidActionPreset.Send, send.state, onClick = startSend)
          }
          ActionCell("Salva · Notes") {
            FluidActionButton(FluidActionPreset.Save, save.state, onClick = { save.run { simulate(outcome) } })
          }
          ActionCell("Copia · un link") {
            // Copia è istantanea: niente lavoro, solo la spunta per un secondo. Un fallimento resta possibile.
            FluidActionButton(FluidActionPreset.Copy, copy.state, onClick = {
              copy.run { if (outcome == ActionOutcome.Failure) fail() }
            })
          }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          ActionCell("Scarica · Fluidify") {
            FluidActionButton(FluidActionPreset.Download, downloadState, onClick = {
              paused = false
              download.run { simulate(outcome, transfer = true, paused = { paused }) }
            })
          }
          ActionCell("Carica · Gallery") {
            FluidActionButton(FluidActionPreset.Upload, upload.state, onClick = {
              upload.run { simulate(outcome, transfer = true) }
            })
          }
          ActionCell("Preferito · Gallery") {
            // Ottimistico: il cuore si riempie al tocco; se il lavoro non riesce torna com'era con la scossa.
            FluidActionButton(heart, like.state, onClick = {
              like.run {
                simulate(if (outcome == ActionOutcome.Failure) outcome else ActionOutcome.Short)
                favorite = !favorite
              }
            })
          }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          ActionCell("Su misura · Sincronizza") {
            FluidActionButton(SyncActionPreset, sync.state, onClick = { sync.run { simulate(outcome) } })
          }
        }
      }
    }
    item(key = "pausa-intestazione") {
      FluidSectionHeader(
        title = "Lo scaricamento, con pausa e ripresa",
        detail = "Fluidify: la percentuale resta ferma in pausa e riparte da lì. Annulla ferma Scarica e Carica.",
      )
    }
    item(key = "pausa") {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidGlassButton(
          text = if (paused) "Riprendi" else "Pausa",
          onClick = { paused = !paused },
          enabled = download.isRunning,
          modifier = Modifier.testTag(ActionSceneTags.Pause),
        )
        FluidGlassButton(
          text = "Annulla",
          onClick = { download.cancel(); upload.cancel(); paused = false },
          enabled = download.isRunning || upload.isRunning,
          modifier = Modifier.testTag(ActionSceneTags.Stop),
        )
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Con «Lavoro breve» il lavoro dura meno di 300 ms: non si vede l'anello, solo l'esito. Mentre lavora il " +
          "tasto non risponde, e TalkBack dice «in corso», l'esito o «non riuscito».",
      )
    }
  }
}

/** Un tasto con la sua didascalia: la didascalia è per chi guarda, TalkBack legge il nome del tasto. */
@Composable
private fun ActionCell(caption: String, button: @Composable () -> Unit) {
  Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    button()
    Text(
      caption,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {},
    )
  }
}
