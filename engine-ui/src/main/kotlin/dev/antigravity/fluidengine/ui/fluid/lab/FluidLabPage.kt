package dev.antigravity.fluidengine.ui.fluid.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.foundation.lab.FluidExperiment
import dev.antigravity.fluidengine.foundation.lab.FluidExperimentApplies
import dev.antigravity.fluidengine.foundation.lab.FluidLab
import dev.antigravity.fluidengine.foundation.lab.FluidLabState
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Forum
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh
import dev.antigravity.fluidengine.ui.fluid.icons.Science
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

// F8.24 · La pagina «Laboratorio»: le funzioni sperimentali dell'app, da accendere e spegnere, come «Feature Flags» di
// Safari su iPhone (Impostazioni › App › Safari › Avanzate › Feature Flags): un elenco di interruttori raggruppati e
// «Ripristina tutto» in fondo. Righe di impostazioni senza vetro, come le pagine delle notifiche: la pagina è un posto
// dove si legge e si sceglie. Gli interruttori li muove il dito, quindi restano nitidi; l'unica cosa che cambia da sola
// è la riga sotto il nome («Al prossimo avvio»), che è testo semplice. Nessun numero di movimento qui.

/**
 * Le parole del Laboratorio. In italiano di serie; [english] è il punto di partenza per un'app tradotta.
 *
 * @param footnote la nota sotto l'elenco: cosa vuol dire «sperimentale», in parole dell'utente.
 */
@Immutable
data class FluidLabLabels(
  val title: String = "Laboratorio",
  val intro: String = "Funzioni nuove ancora in prova. Accendile per provarle prima degli altri: possono cambiare, " +
    "funzionare a metà o sparire. Le spegni quando vuoi.",
  val emptyTitle: String = "Niente in prova",
  val emptyDetail: String = "Per ora non ci sono funzioni da provare. Torna dopo un aggiornamento.",
  val nextLaunch: String = "Al prossimo avvio",
  val resetAll: String = "Ripristina tutto",
  val resetAllDetail: String = "Rimette ogni funzione com'è di serie",
  val resetConfirmTitle: String = "Ripristinare tutte le funzioni in prova?",
  val resetConfirm: String = "Ripristina",
  val cancel: String = "Annulla",
  val report: String = "Segnala un problema",
  val reportDetail: String = "Qualcosa non va con una funzione in prova? Dillo allo sviluppatore",
  val footnote: String = "Le funzioni in prova restano sul telefono: la tua scelta non va da nessuna parte.",
  val rowTitle: String = "Laboratorio",
  val rowNone: String = "Prova le funzioni nuove",
  val rowSome: (Int) -> String = { n -> if (n == 1) "1 funzione accesa" else "$n funzioni accese" },
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidLabLabels = FluidLabLabels(
      title = "Lab",
      intro = "New features still being tested. Turn them on to try them early: they may change, half-work or go away. " +
        "Turn them off whenever you like.",
      emptyTitle = "Nothing to try",
      emptyDetail = "There are no features to try right now. Check back after an update.",
      nextLaunch = "On next launch",
      resetAll = "Reset all",
      resetAllDetail = "Puts every feature back to its default",
      resetConfirmTitle = "Reset all features being tested?",
      resetConfirm = "Reset",
      cancel = "Cancel",
      report = "Report a problem",
      reportDetail = "Something wrong with a feature being tested? Tell the developer",
      footnote = "Features being tested stay on your phone: your choice goes nowhere.",
      rowTitle = "Lab",
      rowNone = "Try new features",
      rowSome = { n -> if (n == 1) "1 feature on" else "$n features on" },
    )
  }
}

/** I tag dei test del Laboratorio. */
object FluidLabTags {
  const val Page = "fluid-lab:pagina"
  const val Row = "fluid-lab:riga"
  const val Reset = "fluid-lab:ripristina"
  const val Report = "fluid-lab:segnala"
  const val Empty = "fluid-lab:vuoto"

  /** La riga della funzione [key]. */
  fun experiment(key: String): String = "fluid-lab:funzione:$key"
}

/**
 * La pagina «Laboratorio» (F8.24): le funzioni di [lab] raggruppate per [FluidExperiment.group], ognuna con il suo
 * interruttore, la nota «Al prossimo avvio» dove serve, «Ripristina tutto» con la conferma, e [onReportProblem] come
 * ultima riga (di solito il flusso «Segnala un problema» di `engine-feedback`).
 *
 * Una pagina intera ([FluidScreen]), da mettere dove l'app tiene le sue pagine delle impostazioni; per aprirla c'è
 * [FluidLabRow]. Senza funzioni dice che non c'è niente in prova.
 */
@Composable
fun FluidLabPage(
  lab: FluidLab,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  onReportProblem: (() -> Unit)? = null,
  labels: FluidLabLabels = FluidLabLabels(),
) {
  val state by lab.state.collectAsStateWithLifecycle()
  var confirmReset by remember { mutableStateOf(false) }
  val groups = remember(lab) { fluidLabGroups(lab.experiments) }
  FluidScreen(title = labels.title, onBack = onBack, modifier = modifier.testTag(FluidLabTags.Page)) {
    item(key = "intro") { FluidSectionFootnote(labels.intro) }
    if (groups.isEmpty()) {
      item(key = "empty") {
        FluidEmptyState(labels.emptyTitle, labels.emptyDetail, Modifier.testTag(FluidLabTags.Empty)) {
          FluidIcon(FluidIcons.Science, contentDescription = null)
        }
      }
    }
    groups.forEach { group ->
      item(key = "group:${group.title.orEmpty()}") {
        Column {
          group.title?.let { FluidSectionHeader(it) }
          FluidListGroup {
            group.experiments.forEachIndexed { index, experiment ->
              if (index > 0) FluidListDivider()
              FluidToggleRow(
                title = experiment.title,
                subtitle = fluidLabSubtitle(experiment, state, labels),
                checked = state.isChosen(experiment.key),
                onCheckedChange = { lab.setEnabled(experiment, it) },
                modifier = Modifier.testTag(FluidLabTags.experiment(experiment.key)),
              )
            }
          }
        }
      }
    }
    item(key = "actions") {
      Column {
        FluidListGroup {
          FluidListRow(
            title = labels.resetAll,
            subtitle = labels.resetAllDetail,
            modifier = Modifier.testTag(FluidLabTags.Reset),
            leading = { FluidIcon(FluidIcons.Refresh, contentDescription = null) },
            tone = FluidTone.Neutral,
            disclosure = false,
            onClick = { confirmReset = true },
          )
          if (onReportProblem != null) {
            FluidListDivider()
            FluidListRow(
              title = labels.report,
              subtitle = labels.reportDetail,
              modifier = Modifier.testTag(FluidLabTags.Report),
              leading = { FluidIcon(FluidIcons.Forum, contentDescription = null) },
              tone = FluidTone.Primary,
              onClick = onReportProblem,
            )
          }
        }
        FluidSectionFootnote(labels.footnote)
      }
    }
  }
  FluidConfirmationDialog(
    visible = confirmReset,
    onDismissRequest = { confirmReset = false },
    title = labels.resetConfirmTitle,
    actions = listOf(FluidAlertAction(labels.resetConfirm, { lab.resetAll() }, FluidAlertAction.Emphasis.Destructive)),
    cancelLabel = labels.cancel,
  )
}

/**
 * La riga che apre il Laboratorio, da mettere in un `FluidListGroup` delle impostazioni: «Laboratorio», e sotto quante
 * funzioni sono accese. Con [hideWhenEmpty] (di serie) non c'è se l'app non ha niente in prova.
 */
@Composable
fun FluidLabRow(
  lab: FluidLab,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  hideWhenEmpty: Boolean = true,
  labels: FluidLabLabels = FluidLabLabels(),
) {
  if (hideWhenEmpty && lab.experiments.isEmpty()) return
  val state by lab.state.collectAsStateWithLifecycle()
  val count = state.enabledCount
  FluidListRow(
    title = labels.rowTitle,
    subtitle = if (count == 0) labels.rowNone else labels.rowSome(count),
    modifier = modifier.testTag(FluidLabTags.Row),
    leading = { FluidIcon(FluidIcons.Science, contentDescription = null) },
    tone = FluidTone.Info,
    onClick = onClick,
  )
}

/** Un gruppo della pagina: il titolo (nullo per il primo, senza testata) e le sue funzioni, nell'ordine dato. */
internal class FluidLabGroup(val title: String?, val experiments: List<FluidExperiment>)

/** Le funzioni in gruppi, nell'ordine in cui il primo di ogni gruppo compare; il gruppo senza nome sta in cima. */
internal fun fluidLabGroups(experiments: List<FluidExperiment>): List<FluidLabGroup> {
  val byGroup = LinkedHashMap<String?, MutableList<FluidExperiment>>()
  experiments.filter { it.group == null }.takeIf { it.isNotEmpty() }?.let { byGroup[null] = it.toMutableList() }
  experiments.filter { it.group != null }.forEach { byGroup.getOrPut(it.group) { mutableListOf() } += it }
  return byGroup.map { (title, list) -> FluidLabGroup(title, list) }
}

/** La riga sotto il nome: il riassunto, e «Al prossimo avvio» quando la scelta aspetta un riavvio. */
internal fun fluidLabSubtitle(experiment: FluidExperiment, state: FluidLabState, labels: FluidLabLabels): String =
  if (experiment.applies == FluidExperimentApplies.NextLaunch && experiment.key in state.pendingRestart) {
    experiment.summary + "\n" + labels.nextLaunch
  } else {
    experiment.summary
  }
