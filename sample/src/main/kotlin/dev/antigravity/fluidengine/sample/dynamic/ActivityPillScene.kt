package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutcome
import dev.antigravity.fluidengine.ui.fluid.activity.rememberFluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MarkEmailUnread
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// La pillola di attività (F6B.10): la scena di prova per le riprese sul tablet e sul telefono. Una posta col titolo in
// linea e la barra che si ritira scorrendo; in cima alla lista i comandi che pubblicano, finiscono e tolgono le attività.
// Accanto alla pillola, per confrontare i tempi della card, un popover dalla sorgente («Filtri», F5B.1) e il menu «Altro»
// della barra (F5A).

/** I tag dei test e delle riprese. */
internal object ActivitySceneTags {
  const val Start = "attivita:sincronizza"
  const val Succeed = "attivita:riuscita"
  const val Fail = "attivita:non-riuscita"
  const val HandOff = "attivita:testimone"
  const val Second = "attivita:seconda"
  const val Clear = "attivita:togli"
}

/** Quanto dura la sincronizzazione della scena, se nessuno la finisce prima: l'anello si riempie in questo tempo. */
private const val ActivitySceneSyncMs = 9_000L

/**
 * Mail che sincronizza: «Sincronizza» pubblica `FluidActivity.sync` con un avanzamento che cresce; la pillola prende il
 * posto di «Posta». «Finisce bene» e «Non riesce» la chiudono con l'esito, «Passa il testimone» con
 * `FluidActivityOutcome.HandedOff` (la pillola esce subito). «Anche un caricamento» ne aggiunge una seconda: il contatore.
 * Scorrendo la lista la barra si ritira e della pillola resta l'anellino.
 */
@Composable
internal fun ActivityPillScene(onBack: () -> Unit) {
  val activities = rememberFluidActivityHostState()
  val scope = rememberCoroutineScope()
  val progress = remember { mutableFloatStateOf(0f) }
  val upload = remember { mutableFloatStateOf(0f) }
  var running by remember { mutableStateOf<Job?>(null) }
  var filters by remember { mutableStateOf(false) }
  FluidActivityHost(activities) {
    val filtersSource = rememberFluidToolbarTransitionSource(ActivitySceneIds.Filters)
    fun sync() {
      running?.cancel()
      progress.floatValue = 0f
      activities.publish(FluidActivity.sync(progress = { progress.floatValue }, detail = "Posta in arrivo"))
      running = scope.launch {
        val steps = (ActivitySceneSyncMs / 50L).toInt()
        repeat(steps) {
          delay(50L)
          progress.floatValue = (it + 1).toFloat() / steps
        }
        activities.finish("sync")
      }
    }
    fun end(outcome: FluidActivityOutcome) {
      running?.cancel()
      activities.finish("sync", outcome)
    }
    FluidScreen(
      title = "Posta",
      onBack = onBack,
      barMinimize = FluidBarMinimizeBehavior.OnScrollDown,
      toolbar = {
        title(FluidToolbarTitleMode.Inline)
        button(ActivitySceneIds.Filters, FluidIcons.FilterList, "Filtri", onClick = { filters = true })
        overflow {
          button("letta", FluidIcons.MarkEmailUnread, "Segna tutte come lette", onClick = {})
          button("archivia", FluidIcons.Archive, "Archivia tutte", onClick = {})
        }
      },
    ) {
      item(key = "comandi-titolo") { FluidSectionHeader(title = "La pillola") }
      item(key = "comandi") {
        FluidListGroup {
          ActivityCommand("Sincronizza", "La pillola prende il posto di «Posta»", ActivitySceneTags.Start) { sync() }
          FluidListDivider()
          ActivityCommand("Finisce bene", "La spunta, un battito, poi la pillola esce", ActivitySceneTags.Succeed) {
            end(FluidActivityOutcome.succeeded())
          }
          FluidListDivider()
          ActivityCommand("Non riesce", "«Rete assente», più a lungo", ActivitySceneTags.Fail) {
            end(FluidActivityOutcome.failed("Rete assente"))
          }
          FluidListDivider()
          ActivityCommand("Passa il testimone", "La pillola esce subito, senza esito", ActivitySceneTags.HandOff) {
            end(FluidActivityOutcome.HandedOff)
          }
          FluidListDivider()
          ActivityCommand("Anche un caricamento", "Una seconda attività: il contatore", ActivitySceneTags.Second) {
            upload.floatValue = 0.3f
            activities.publish(FluidActivity.transfer("upload", progress = { upload.floatValue }, upload = true))
          }
          FluidListDivider()
          ActivityCommand("Togli tutto", "Le attività finiscono senza esito", ActivitySceneTags.Clear) {
            running?.cancel()
            activities.remove("sync")
            activities.remove("upload")
          }
        }
      }
      item(key = "comandi-nota") {
        FluidSectionFootnote(
          "Tocca la pillola per la card. «Filtri» apre un popover e «Altro» il menu della barra: hanno gli stessi tempi.",
        )
      }
      item(key = "posta-titolo") { FluidSectionHeader(title = "In arrivo") }
      item(key = "posta") {
        FluidListGroup {
          ActivitySceneMail.forEachIndexed { index, (from, subject) ->
            if (index > 0) FluidListDivider()
            FluidListRow(title = from, subtitle = subject)
          }
        }
      }
    }
    FluidGlassModalPortal(
      item = Unit.takeIf { filters },
      onDismissRequest = { filters = false },
      paneTitle = "Filtri",
      source = filtersSource,
      compactAdaptation = FluidCompactAdaptation.Popover,
    ) {
      Column(Modifier.widthIn(min = 240.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Filtri", style = MaterialTheme.typography.titleMedium)
        Text("Non lette", style = MaterialTheme.typography.bodyMedium)
        Text("Con allegati", style = MaterialTheme.typography.bodyMedium)
        Text("Contrassegnate", style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun ActivityCommand(title: String, detail: String, tag: String, onClick: () -> Unit) {
  FluidListRow(title = title, subtitle = detail, modifier = Modifier.testTag(tag), onClick = onClick)
}

/** Gli `id` della barra della scena. */
internal object ActivitySceneIds {
  const val Filters = "filtri"
}

private val ActivitySceneMail = listOf(
  "Anna Bellini" to "Riunione di domani",
  "Luca" to "Le foto delle vacanze",
  "Marta R." to "Fattura di novembre",
  "Giorgio" to "Re: il progetto",
  "Sara" to "Cena venerdì?",
  "Paolo" to "Il preventivo",
  "Elena" to "Biglietti del treno",
  "Davide" to "Foto della festa",
  "Chiara" to "Appunti della lezione",
  "Banca" to "Estratto conto",
  "Giulia" to "Il libro che ti dicevo",
  "Marco" to "Partita di giovedì",
  "Teatro" to "I tuoi biglietti",
  "Francesca" to "Bozza della relazione",
  "Corriere" to "Il pacco è in consegna",
  "Roberto" to "Le slide di oggi",
  "Silvia" to "Auguri!",
  "Comune" to "Promemoria della scadenza",
)
