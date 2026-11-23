package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.ui.fluid.FluidFilter
import dev.antigravity.fluidengine.ui.fluid.FluidFilterChips
import dev.antigravity.fluidengine.ui.fluid.FluidFilterMode
import dev.antigravity.fluidengine.ui.fluid.FluidFilterPresets
import dev.antigravity.fluidengine.ui.fluid.FluidFilterSelection
import dev.antigravity.fluidengine.ui.fluid.FluidFilterValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSuggestion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSuggestions
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToken
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPriority
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// F6B.8: i filtri e i suggerimenti a pillole. I casi delle app di Alessio rifatti col solo engine: nessuna API di
// movimento qui (la fila che si raccoglie in «Filtri · N», il menu che nasce dal vetro come dal gruppo della barra, la
// pillola a più valori sono dell'engine).
//
// - `filtri`: Transit, «Cerca una fermata». Sotto il campo la fila dei filtri (a scelta multipla, con la pillola
//   «Periodo» a più valori) e i suggerimenti, coi recenti che si tolgono dalla ✕; nella pagina la fila delle linee, a
//   scelta singola. A testo grande (o in una finestra stretta) le pillole che non ci stanno vanno in «Filtri · N».
// - `gettoni`: Mail, «da: persona» (F6B.8b). Una pillola scelta (una persona) vola nel campo della ricerca e diventa
//   gettone, e il suo posto nella fila si richiude; il gettone tolto torna pillola. Sono `tokens` e `filters` di
//   `FluidSearchSpec`, col preset `FluidFilterPresets.tokens`.

/** I tag dei test e delle riprese. */
internal object FilterSceneTags {
  const val Stops = "filtri:fermate"
  const val Mail = "gettoni:posta"
}

private val TransitFilters = listOf(
  FluidFilter("aperti", "Aperti ora", priority = FluidToolbarPriority.High),
  FluidFilter("accessibile", "Accessibile"),
  FluidFilter("metro", "Metro", priority = FluidToolbarPriority.Low),
  FluidFilterPresets.lastDays(),
)

private val TransitLines = FluidFilterPresets.scopes(
  listOf(
    FluidFilterValue("tutte", "Tutte"),
    FluidFilterValue("bus", "Bus"),
    FluidFilterValue("treni", "Treni"),
    FluidFilterValue("notte", "Notte"),
  ),
  label = "Linee",
)

private data class TransitStop(val name: String, val lines: String, val kind: String, val open: Boolean, val accessible: Boolean)

private val TransitStops = listOf(
  TransitStop("Piazza Duomo", "1, 6, 17", "bus", open = true, accessible = true),
  TransitStop("Stazione Centrale", "M1, R, 23", "treni", open = true, accessible = true),
  TransitStop("Porta Romana", "11, 36", "bus", open = false, accessible = false),
  TransitStop("Aeroporto", "Navetta, R", "treni", open = true, accessible = true),
  TransitStop("Campo di Marte", "3, 10", "bus", open = true, accessible = false),
  TransitStop("Sesto", "R", "treni", open = false, accessible = true),
  TransitStop("Piazza della Libertà", "N1", "notte", open = true, accessible = true),
  TransitStop("Careggi", "14, 40", "bus", open = true, accessible = true),
)

/**
 * Transit: la ricerca delle fermate con i filtri e i suggerimenti sotto il campo (`suggestions`). Prima del primo
 * carattere ci sono i recenti, mai «Nessun risultato»; scrivendo, i risultati per pertinenza.
 */
@Composable
internal fun FiltersScene(onBack: () -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  var active by rememberSaveable { mutableStateOf(false) }
  var filters by rememberSaveable(stateSaver = FluidFilterSelection.Saver) { mutableStateOf(FluidFilterSelection.of("aperti")) }
  var lines by rememberSaveable(stateSaver = FluidFilterSelection.Saver) { mutableStateOf(FluidFilterSelection.of("tutte")) }
  var recents by rememberSaveable { mutableStateOf(listOf("Piazza Duomo", "Aeroporto", "Careggi")) }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  val suggestions: List<FluidSearchSuggestion> = if (query.isBlank()) {
    recents.mapIndexed { index, name -> FluidFilterPresets.recent("recente-$name", name, relevance = -index.toFloat()) }
  } else {
    TransitStops.filter { it.name.contains(query, ignoreCase = true) }.map { stop ->
      FluidSearchSuggestion("fermata-${stop.name}", stop.name, "Linee ${stop.lines}",
        relevance = if (stop.name.startsWith(query, ignoreCase = true)) 1f else 0f)
    }
  }
  val shown = TransitStops.filter { stop ->
    (!filters.isSelected("aperti") || stop.open) &&
      (!filters.isSelected("accessibile") || stop.accessible) &&
      (!filters.isSelected("metro") || stop.lines.contains("M")) &&
      (lines.isSelected("tutte") || lines.selected.isEmpty() || lines.isSelected(stop.kind))
  }
  FluidScreen(
    title = "Fermate",
    onBack = onBack,
    toolbar = { title(FluidToolbarTitleMode.Large) },
    search = FluidSearchSpec.search(
      query, { query = it }, prompt = "Cerca una fermata", active = active, onActiveChange = { active = it },
      suggestions = {
        Column {
          FluidFilterChips(TransitFilters, filters, { filters = it }, mode = FluidFilterMode.Multiple, label = "Filtri")
          FluidSearchSuggestions(
            query = query,
            suggestions = suggestions,
            onSuggestionClick = { suggestion ->
              picked = suggestion.title
              recents = (listOf(suggestion.title) + recents.filter { it != suggestion.title }).take(4)
              active = false
            },
            onRemoveRecent = { suggestion -> recents = recents.filter { it != suggestion.title } },
          )
        }
      },
    ),
  ) {
    item(key = "linee-titolo") { FluidSectionHeader(title = "Linee") }
    item(key = "linee") { FluidFilterChips(TransitLines, lines, { lines = it }) }
    item(key = "fermate-titolo") { FluidSectionHeader(title = picked?.let { "Scelta: $it" } ?: "Fermate vicine") }
    item(key = "fermate") {
      FluidListGroup(Modifier.testTag(FilterSceneTags.Stops)) {
        shown.forEachIndexed { index, stop ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = stop.name, subtitle = "Linee ${stop.lines}${if (stop.open) "" else " · chiusa adesso"}")
        }
        if (shown.isEmpty()) FluidListRow(title = "Nessuna fermata con questi filtri", subtitle = "Togli un filtro")
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "A testo grande le pillole che non ci stanno vanno in «Filtri · N», che apre il menu dal suo vetro con le " +
          "stesse voci. «Periodo» si apre nei suoi valori.",
      )
    }
  }
}

/** Le persone della posta: i gettoni suggeriti del campo (il preset «Gettoni», «da: persona»). */
internal val MailPeople = FluidFilterPresets.tokens(
  listOf(
    FluidFilterValue("anna", "Anna"),
    FluidFilterValue("luca", "Luca"),
    FluidFilterValue("marta", "Marta"),
    FluidFilterValue("giorgio", "Giorgio"),
  ),
  label = "Da",
)

private val TokenMail = listOf(
  "Anna" to "Riunione di domani",
  "Luca" to "Le foto delle vacanze",
  "Marta" to "Fattura di novembre",
  "Giorgio" to "Re: il progetto",
  "Anna" to "Il calendario del trasloco",
  "Luca" to "Partita di giovedì",
)

/** I gettoni sopravvivono alla morte del processo come il testo del campo. */
private val TokensSaver = listSaver<List<FluidToken>, String>(
  save = { tokens -> tokens.flatMap { listOf(it.id, it.label) } },
  restore = { saved -> saved.chunked(2).map { FluidToken(it[0], it[1]) } },
)

/**
 * Mail, «da: persona» (F6B.8b): la ricerca a gettoni di SwiftUI (`searchable(text:tokens:suggestedTokens:)`) col solo
 * engine. Aperto il campo, le persone sono pillole di vetro accanto a lui; quella scelta vola nel campo e diventa un
 * gettone, e il suo posto nella fila si richiude. Il gettone toccato, o tolto col «cancella» a testo vuoto (il primo
 * colpo lo sceglie), torna pillola. La posta mostra solo quella delle persone scelte, e delle parole scritte.
 */
@Composable
internal fun TokensScene(onBack: () -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  var active by rememberSaveable { mutableStateOf(false) }
  var tokens by rememberSaveable(stateSaver = TokensSaver) { mutableStateOf(emptyList<FluidToken>()) }
  val chosen = tokens.map { it.label }.toSet()
  val shown = TokenMail.filter { (from, subject) ->
    (chosen.isEmpty() || from in chosen) &&
      (query.isBlank() || subject.contains(query, ignoreCase = true) || from.contains(query, ignoreCase = true))
  }
  FluidScreen(
    title = "Posta",
    onBack = onBack,
    toolbar = { title(FluidToolbarTitleMode.Inline) },
    search = FluidSearchSpec.search(
      query, { query = it }, prompt = "Cerca nella posta", active = active, onActiveChange = { active = it },
      tokens = tokens, onTokensChange = { tokens = it }, filters = MailPeople,
    ),
  ) {
    item(key = "posta-titolo") {
      FluidSectionHeader(title = if (chosen.isEmpty()) "Tutta la posta" else "Da: ${chosen.joinToString()}")
    }
    item(key = "posta") {
      FluidListGroup(Modifier.testTag(FilterSceneTags.Mail)) {
        shown.forEachIndexed { index, (from, subject) ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = from, subtitle = subject)
        }
        if (shown.isEmpty()) FluidListRow(title = "Nessuna mail", subtitle = "Togli un gettone")
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Tocca il campo: accanto ci sono le persone. Quella scelta vola nel campo e diventa un gettone, e il suo posto " +
          "nella fila si richiude. Il gettone toccato, o tolto col tasto cancella a testo vuoto, torna pillola.",
      )
    }
  }
}
