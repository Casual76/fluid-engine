package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFilterPresets
import dev.antigravity.fluidengine.ui.fluid.FluidFilterValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToken
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// I gettoni negli altri campi (F6B.15, scelta di Alessio del 23 novembre 2026, pomeriggio):
// - `gettoni-titolo`: Mail, la ricerca sotto il titolo grande (`FluidSearchPlacement.Principal`), con le persone;
// - `gettoni-pagina`: Notes, `FluidSearchField` dentro la pagina, con le etichette.
// Lo stesso modello e lo stesso viaggio del campo della barra (la scena `gettoni`): la pillola scelta vola nel campo e
// diventa gettone, il gettone tolto torna pillola al suo posto.

/** I tag dei test. */
internal object TokenFieldSceneTags {
  const val Mail = "gettoni-titolo:posta"
  const val Notes = "gettoni-pagina:note"
}

/** I gettoni sopravvivono alla morte del processo come il testo del campo. */
private val SceneTokensSaver = listSaver<List<FluidToken>, String>(
  save = { tokens -> tokens.flatMap { listOf(it.id, it.label) } },
  restore = { saved -> saved.chunked(2).map { FluidToken(it[0], it[1]) } },
)

private val UnderTitleMail = listOf(
  "Anna" to "Riunione di domani",
  "Luca" to "Le foto delle vacanze",
  "Marta" to "Fattura di novembre",
  "Giorgio" to "Re: il progetto",
  "Anna" to "Il calendario del trasloco",
  "Luca" to "Partita di giovedì",
  "Marta" to "Biglietti del treno",
  "Giorgio" to "Le slide di oggi",
)

/**
 * Mail, la ricerca sotto il titolo grande (F6B.15): `FluidSearchPlacement.Principal` coi gettoni e le persone di
 * [MailPeople]. Aperto il campo, le persone sono una fila di pillole sotto di lui; quella scelta vola nel campo.
 */
@Composable
internal fun TokensUnderTitleScene(onBack: () -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  var active by rememberSaveable { mutableStateOf(false) }
  var tokens by rememberSaveable(stateSaver = SceneTokensSaver) { mutableStateOf(emptyList<FluidToken>()) }
  val chosen = tokens.map { it.label }.toSet()
  val shown = UnderTitleMail.filter { (from, subject) ->
    (chosen.isEmpty() || from in chosen) &&
      (query.isBlank() || subject.contains(query, ignoreCase = true) || from.contains(query, ignoreCase = true))
  }
  FluidScreen(
    title = "Posta",
    onBack = onBack,
    search = FluidSearchSpec.search(
      query, { query = it }, prompt = "Cerca nella posta", placement = FluidSearchPlacement.Principal,
      active = active, onActiveChange = { active = it },
      tokens = tokens, onTokensChange = { tokens = it }, filters = MailPeople,
    ),
  ) {
    item(key = "posta-titolo") {
      FluidSectionHeader(title = if (chosen.isEmpty()) "Tutta la posta" else "Da: ${chosen.joinToString()}")
    }
    item(key = "posta") {
      FluidListGroup(Modifier.testTag(TokenFieldSceneTags.Mail)) {
        shown.forEachIndexed { index, (from, subject) ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = from, subtitle = subject)
        }
        if (shown.isEmpty()) FluidListRow(title = "Nessuna mail", subtitle = "Togli un gettone")
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Il campo sta sotto il titolo. Toccalo: sotto ci sono le persone. Quella scelta vola nel campo e diventa un " +
          "gettone; il gettone toccato, o tolto col tasto cancella a testo vuoto, torna pillola.",
      )
    }
  }
}

/** Le etichette delle note: i gettoni suggeriti del campo nella pagina. */
internal val NoteTags = FluidFilterPresets.tokens(
  listOf(
    FluidFilterValue("lavoro", "Lavoro"),
    FluidFilterValue("casa", "Casa"),
    FluidFilterValue("idee", "Idee"),
    FluidFilterValue("viaggi", "Viaggi"),
  ),
  label = "Etichetta",
)

private val TaggedNotes = listOf(
  Triple("Riunione di lunedì", "Lavoro", "Ordine del giorno e chi porta cosa"),
  Triple("Lista della spesa", "Casa", "Latte, pane, il detersivo"),
  Triple("Un'app per le piante", "Idee", "Ricorda quando annaffiarle"),
  Triple("Lisbona a marzo", "Viaggi", "Il tram 28 e il miradouro"),
  Triple("Il preventivo", "Lavoro", "Da mandare entro venerdì"),
  Triple("La caldaia", "Casa", "Chiamare il tecnico"),
)

/**
 * Notes, `FluidSearchField` dentro la pagina (F6B.15), sempre aperto, coi gettoni e le etichette di [NoteTags]: sotto il
 * campo la fila delle etichette; quella scelta vola nel campo e le note si restringono.
 */
@Composable
internal fun TokensInPageScene(onBack: () -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  var tokens by rememberSaveable(stateSaver = SceneTokensSaver) { mutableStateOf(emptyList<FluidToken>()) }
  val chosen = tokens.map { it.label }.toSet()
  val shown = TaggedNotes.filter { (title, tag, body) ->
    (chosen.isEmpty() || tag in chosen) &&
      (query.isBlank() || title.contains(query, ignoreCase = true) || body.contains(query, ignoreCase = true))
  }
  FluidScreen(title = "Note", onBack = onBack) {
    item(key = "campo") {
      FluidSearchField(
        query = query,
        onQueryChange = { query = it },
        placeholder = "Cerca nelle note",
        autoFocus = false,
        tokens = tokens,
        onTokensChange = { tokens = it },
        filters = NoteTags,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }
    item(key = "note-titolo") {
      FluidSectionHeader(title = if (chosen.isEmpty()) "Tutte le note" else chosen.joinToString())
    }
    item(key = "note") {
      FluidListGroup(Modifier.testTag(TokenFieldSceneTags.Notes)) {
        shown.forEachIndexed { index, (title, tag, body) ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = title, subtitle = "$tag · $body")
        }
        if (shown.isEmpty()) FluidListRow(title = "Nessuna nota", subtitle = "Togli un gettone")
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Il campo sta nella pagina, sopra le note. Sotto di lui le etichette: quella scelta vola nel campo e diventa un " +
          "gettone, e le note si restringono; il gettone toccato torna pillola.",
      )
    }
  }
}
