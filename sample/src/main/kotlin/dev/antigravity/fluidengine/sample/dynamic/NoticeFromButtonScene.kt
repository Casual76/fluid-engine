package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationResult
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MarkEmailUnread
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidButtonRole
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.launch

// L'avviso che nasce dal tasto (F6B.4). **Non è di serie**: dopo i video del 21 novembre Alessio ha scelto, per «Invia» e
// «Annulla», il tasto che diventa il contatore (la scena «Invia» di questa galleria). L'avviso resta nell'engine per chi
// vuole un avviso con testo: qui lo pubblica un'app che lo sceglie, con la sorgente del tasto della sua barra. La goccia
// si raccoglie sul tasto, va al punto di posa vicino a lui (sopra la barra in basso, sotto quella in alto) e la card si
// apre attorno a lei; all'uscita torna nel tasto se c'è ancora, altrimenti scivola via come le notifiche di sempre.

/** Gli `id` dei tasti, che sono anche le loro sorgenti: `rememberFluidToolbarTransitionSource(id)`. */
internal object NoticeSceneIds {
  const val Archive = "archivia"
  const val Unread = "non-letta"
  const val Delete = "elimina"
}

/** I tag dei test. */
internal object NoticeSceneTags {
  const val Status = "avviso:stato"
}

/** Quanto resta «Annulla» nell'avviso: l'anello che si svuota attorno a lui (scelta di Alessio). */
private const val NoticeUndoSeconds = 5

/**
 * Mail: una conversazione aperta. «Archivia», in basso, pubblica `FluidNotification.undo(…, source = …)`: l'avviso con
 * un solo «Annulla» in linea e l'anello che si svuota attorno a lui. «Segna come non letta», in alto, e «Elimina», in
 * basso, pubblicano `FluidNotification.result(…)`: un esito positivo e uno negativo, ognuno dal suo tasto.
 */
@Composable
internal fun NoticeFromButtonScene(onBack: () -> Unit) {
  val host = rememberFluidNotificationHostState()
  val scope = rememberCoroutineScope()
  val backdrop = rememberGlassBackdrop()
  var archived by rememberSaveable { mutableStateOf(false) }
  var status by rememberSaveable { mutableStateOf("Nessun avviso ancora.") }
  var serial by remember { mutableIntStateOf(0) }
  // Le sorgenti si leggono quando si pubblica: il riquadro e la forma del tasto in quel momento (F6B.4).
  val archiveSource = rememberFluidToolbarTransitionSource(NoticeSceneIds.Archive)
  val unreadSource = rememberFluidToolbarTransitionSource(NoticeSceneIds.Unread)
  val deleteSource = rememberFluidToolbarTransitionSource(NoticeSceneIds.Delete)
  fun publish(notification: FluidNotification, describe: (FluidNotificationResult) -> String) {
    scope.launch { status = describe(host.showForResult(notification)) }
  }
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
      FluidScreen(
        title = "Cena di sabato",
        onBack = onBack,
        toolbar = {
          button(NoticeSceneIds.Unread, FluidIcons.MarkEmailUnread, "Segna come non letta", onClick = {
            publish(FluidNotification.result(true, "Segnata come non letta", source = unreadSource, id = "non-letta-${++serial}")) {
              "Segna come non letta: ${it.spoken()}"
            }
          })
          group("comandi", placement = FluidToolbarPlacement.BottomBar) {
            button(NoticeSceneIds.Archive, FluidIcons.Archive, "Archivia", enabled = !archived, onClick = {
              archived = true
              val notification = FluidNotification.undo(
                title = "Conversazione archiviata",
                seconds = NoticeUndoSeconds,
                onUndo = { archived = false },
                source = archiveSource,
                group = "posta",
                id = "archivio-${++serial}",
              )
              publish(notification) { "Archivia: ${it.spoken()}" }
            })
            flexibleSpacer()
            button(NoticeSceneIds.Delete, FluidIcons.Delete, "Elimina", role = FluidButtonRole.Destructive,
              placement = FluidToolbarPlacement.BottomBar, onClick = {
                publish(FluidNotification.result(false, "Eliminazione non riuscita",
                  "La conversazione è condivisa: riprova più tardi.", source = deleteSource, id = "elimina-${++serial}")) {
                  "Elimina: ${it.spoken()}"
                }
              })
          }
        },
      ) {
        item(key = "messaggio") {
          FluidListGroup {
            FluidListRow(title = "Da", subtitle = "Giulia Rossi", disclosure = false, leadingTile = false)
            FluidListDivider()
            FluidListRow(
              title = "Cartella",
              subtitle = if (archived) "Archivio" else "Posta in arrivo",
              disclosure = false,
              leadingTile = false,
            )
          }
        }
        item(key = "testo") {
          Text(
            "Ci vediamo alle otto da noi? Porto il dolce.\n\nGiulia",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 12.dp),
          )
        }
        item(key = "stato-intestazione") { FluidSectionHeader(title = "Come è finita") }
        item(key = "stato") {
          Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(NoticeSceneTags.Status))
        }
        item(key = "nota") {
          FluidSectionFootnote(
            text = "Ogni avviso nasce dal vetro del suo tasto e si posa vicino a lui. «Annulla» ha attorno un anello che si " +
              "svuota: la scadenza vera resta dell'app. Questo avviso non è di serie: per «Invia» l'engine usa il contatore " +
              "nel tasto.",
          )
        }
      }
    }
    FluidNotificationHost(host, Modifier.align(Alignment.TopCenter), backdrop = backdrop)
  }
}

/** L'esito in parole, per la riga «Come è finita». */
private fun FluidNotificationResult.spoken(): String = when (this) {
  is FluidNotificationResult.ActionPerformed -> "toccato «$label»"
  FluidNotificationResult.Dismissed -> "chiuso"
  FluidNotificationResult.TimedOut -> "scaduto"
  FluidNotificationResult.Replaced -> "sostituito da uno più nuovo"
  FluidNotificationResult.Rejected -> "rifiutato"
}
