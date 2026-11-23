package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSplitButton
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.rememberFluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Drafts
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.LocationOn
import dev.antigravity.fluidengine.ui.fluid.icons.PictureAsPdf
import dev.antigravity.fluidengine.ui.fluid.icons.Schedule
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F6B.11: il tasto diviso e il titolo che diventa selettore, coi casi delle app di Alessio rifatti col solo engine
// (nessuna API di movimento qui: il viaggio dei menu e dei pannelli è dell'engine).
//
// - `tasto-diviso`: Mail «Scrivi», «Invia ▾» con «Programma invio» (UX-CMP-09), nella barra; e da solo, «Esporta ▾».
// - `titolo-menu`: Mail, il titolo «Posta in arrivo» col disco apre gli account; «Sincronizza» pubblica un'attività,
//   e la pillola sta minima accanto al titolo (il titolo che è un selettore non cede il posto).
// - `titolo-pannello`: Weather, il titolo della località apre un pannello con le località (popover dal titolo; sul
//   telefono un foglio dal titolo).

/** I tag dei test e delle riprese. */
internal object SplitTitleTags {
  const val Outcome = "f6b11:esito"
  const val Sync = "f6b11:sincronizza"
  const val Standalone = "f6b11:esporta"
  fun location(name: String) = "f6b11:localita:$name"
}

/** Mail «Scrivi»: «Invia ▾» nella barra (la freccia apre «Programma invio» dallo stesso vetro) e «Esporta ▾» da solo. */
@Composable
internal fun SplitButtonScene(onBack: () -> Unit) {
  var outcome by remember { mutableStateOf("Nessuna azione") }
  FluidScreen(
    title = "Nuova mail",
    onBack = onBack,
    toolbar = {
      title(FluidToolbarTitleMode.Inline)
      // R1 N5 senza freccia visibile: il tocco allega una foto, la pressione lunga apre le altre scelte dallo stesso vetro.
      action(
        "allega", FluidIcons.Image, "Allega",
        onClick = { outcome = "Allegata una foto" },
        menu = {
          listOf(
            FluidContextAction("Foto", icon = FluidIcons.Image, onClick = { outcome = "Allegata una foto" }),
            FluidContextAction("Documento", icon = FluidIcons.PictureAsPdf, onClick = { outcome = "Allegato un documento" }),
          )
        },
      )
      split(
        "invia", "Invia",
        onClick = { outcome = "Inviata ora" },
        menuLabel = "Opzioni di invio",
        menu = {
          listOf(
            FluidContextAction("Programma invio", icon = FluidIcons.Schedule, onClick = { outcome = "Programmata per domani alle 8" }),
            FluidContextAction("Invia come bozza", icon = FluidIcons.Drafts, onClick = { outcome = "Salvata nelle bozze" }),
          )
        },
      )
    },
  ) {
    item(key = "esito-titolo") { FluidSectionHeader(title = "Ultima azione") }
    item(key = "esito") {
      FluidListGroup {
        FluidListRow(title = outcome, subtitle = "Tocca «Invia», o la freccia per le altre opzioni",
          modifier = Modifier.testTag(SplitTitleTags.Outcome))
      }
    }
    item(key = "bozza-titolo") { FluidSectionHeader(title = "Bozza") }
    item(key = "bozza") {
      FluidListGroup {
        FluidListRow(title = "A", subtitle = "Anna Bellini")
        FluidListDivider()
        FluidListRow(title = "Oggetto", subtitle = "Riunione di domani")
        FluidListDivider()
        FluidListRow(title = "Ci vediamo alle dieci in sala riunioni. Porto io le slide.", subtitle = "")
      }
    }
    item(key = "solo-titolo") { FluidSectionHeader(title = "Da solo") }
    item(key = "solo") {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidSplitButton(
          label = "Esporta",
          onClick = { outcome = "Esportata come PDF" },
          menuLabel = "Formati di esportazione",
          modifier = Modifier.testTag(SplitTitleTags.Standalone),
          menu = {
            listOf(
              FluidContextAction("Come PDF", icon = FluidIcons.PictureAsPdf, onClick = { outcome = "Esportata come PDF" }),
              FluidContextAction("Come immagine", icon = FluidIcons.Image, onClick = { outcome = "Esportata come immagine" }),
            )
          },
        )
        FluidSplitButton(
          label = "Condividi",
          icon = FluidIcons.IosShare,
          onClick = { outcome = "Condivisa" },
          menu = { listOf(FluidContextAction("Copia il collegamento", onClick = { outcome = "Collegamento copiato" })) },
        )
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Un vetro solo con un filo fra azione e freccia. Il tocco sull'azione la fa; la freccia, o la pressione lunga " +
          "sull'azione, apre il menu dallo stesso vetro.",
      )
    }
  }
}

private val SplitTitleAccounts = listOf("Posta in arrivo", "Lavoro", "Personale")

/** Mail: il titolo col disco apre gli account; «Sincronizza» fa vedere la pillola accanto al titolo che è un selettore. */
@Composable
internal fun TitleMenuScene(onBack: () -> Unit) {
  val activities = rememberFluidActivityHostState()
  val scope = rememberCoroutineScope()
  val progress = remember { mutableFloatStateOf(0f) }
  var running by remember { mutableStateOf<Job?>(null) }
  var account by remember { mutableStateOf(SplitTitleAccounts.first()) }
  FluidActivityHost(activities) {
    FluidScreen(
      title = account,
      onBack = onBack,
      toolbar = {
        title(
          FluidToolbarTitleMode.Large,
          menu = {
            SplitTitleAccounts.map { name ->
              FluidContextAction(name, icon = if (name == SplitTitleAccounts.first()) FluidIcons.Inbox else null,
                selected = name == account, onClick = { account = name })
            } + FluidContextAction("Aggiungi account", icon = FluidIcons.Add, startsSection = true, onClick = {})
          },
          selectorHint = "Cambia casella",
        )
        button("cerca", FluidIcons.Search, "Cerca", onClick = {})
      },
    ) {
      item(key = "comandi-titolo") { FluidSectionHeader(title = "Il titolo") }
      item(key = "comandi") {
        FluidListGroup {
          FluidListRow(
            title = "Sincronizza",
            subtitle = "La pillola sta minima accanto al titolo, che resta il selettore",
            modifier = Modifier.testTag(SplitTitleTags.Sync),
            onClick = {
              running?.cancel()
              progress.floatValue = 0f
              activities.publish(FluidActivity.sync(progress = { progress.floatValue }, detail = account))
              running = scope.launch {
                repeat(120) {
                  delay(50L)
                  progress.floatValue = (it + 1) / 120f
                }
                activities.finish("sync")
              }
            },
          )
        }
      }
      item(key = "nota") {
        FluidSectionFootnote("Tocca il titolo, grande o agganciato alla barra: gli account nascono da lui e ci rientrano.")
      }
      item(key = "posta-titolo") { FluidSectionHeader(title = "Oggi") }
      item(key = "posta") {
        FluidListGroup {
          SplitTitleMail.forEachIndexed { index, (from, subject) ->
            if (index > 0) FluidListDivider()
            FluidListRow(title = from, subtitle = "$subject · $account")
          }
        }
      }
    }
  }
}

private val SplitTitleLocations = listOf("Firenze", "Milano", "Roma", "Bologna", "Napoli")

/** Weather: il titolo della località apre un pannello con le località (popover; sul telefono un foglio dal titolo). */
@Composable
internal fun TitlePanelScene(onBack: () -> Unit) {
  var place by remember { mutableStateOf(SplitTitleLocations.first()) }
  FluidScreen(
    title = place,
    onBack = onBack,
    toolbar = {
      title(
        FluidToolbarTitleMode.Inline,
        panel = {
          // Lo scope del pannello è una colonna: dentro il gruppo la sua chiusura si chiama da qui.
          val close: () -> Unit = { dismiss() }
          FluidSectionHeader(title = "Le tue località")
          FluidListGroup(Modifier.padding(bottom = 8.dp)) {
            SplitTitleLocations.forEachIndexed { index, name ->
              if (index > 0) FluidListDivider()
              FluidListRow(
                title = name,
                subtitle = if (name == place) "Qui adesso" else "",
                modifier = Modifier.testTag(SplitTitleTags.location(name)),
                onClick = {
                  place = name
                  close()
                },
              )
            }
          }
        },
        selectorHint = "Cambia località",
      )
      button("aggiorna", FluidIcons.Sync, "Aggiorna", onClick = {})
      button("posizione", FluidIcons.LocationOn, "La mia posizione", onClick = {})
    },
  ) {
    item(key = "oggi-titolo") { FluidSectionHeader(title = "Oggi a $place") }
    item(key = "oggi") {
      FluidListGroup {
        FluidListRow(title = "18°", subtitle = "Sereno, vento debole")
        FluidListDivider()
        FluidListRow(title = "Domani", subtitle = "Nuvoloso, 15°")
        FluidListDivider()
        FluidListRow(title = "Dopodomani", subtitle = "Pioggia, 12°")
      }
    }
    item(key = "nota") {
      FluidSectionFootnote("Tocca il titolo: il pannello delle località nasce da lui. Sul telefono è un foglio dal titolo.")
    }
  }
}

private val SplitTitleMail = listOf(
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
)
