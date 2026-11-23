package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFindDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchExpansion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFindState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import kotlin.math.roundToInt

// F6B.6: il campo coi preset d'uso, e «trova nella pagina». I casi delle app di Alessio rifatti col solo engine: nessuna
// API di movimento qui (il viaggio della lente che diventa campo, i vicini che rientrano, la salita sopra la tastiera sono
// dell'engine).
//
// - `campo`: Mail. La ricerca in alto che **resta in cima** inghiotte «Scrivi» e l'account e li restituisce; il titolo è
//   il selettore degli account (F6B.11). Una fila sceglie il preset del campo: Ricerca, Messaggio, Nota, Assistente.
// - `trova`: Notes, una trascrizione. «Trova» nasce sopra la tastiera, dice «3/12», l'invio passa al successivo, e la
//   barra in alto resta com'è.

/** I tag dei test e delle riprese. */
internal object FieldSceneTags {
  const val Outcome = "campo:esito"
  const val Transcript = "trova:testo"
  const val Count = "trova:conteggio"
  fun preset(preset: FieldPreset) = "campo:preset:${preset.key}"
}

/** I preset d'uso del campo (F6B.6), nella fila della scena `campo`. */
internal enum class FieldPreset(val key: String, val label: String, val detail: String) {
  Search("ricerca", "Ricerca", "In alto, resta in cima e inghiotte i tasti accanto"),
  Message("messaggio", "Messaggio", "In basso, cresce fino a cinque righe e resta aperto dopo l'invio"),
  Note("nota", "Nota", "In basso, va a capo e si chiude con «Fine»"),
  Assistant("assistente", "Assistente", "In basso, resta aperto dopo la domanda"),
}

private val FieldAccounts = listOf("Posta in arrivo", "Lavoro", "Personale")

/**
 * Mail: «Posta in arrivo ▾». Con il preset Ricerca il campo sta in alto e resta in cima (`StayAtTop`): aperto, i vicini
 * («Scrivi» e l'account) si sfocano e rientrano nel suo vetro, e tornano alla chiusura. Gli altri preset mettono il campo in
 * basso, sopra la tastiera: «Messaggio» resta aperto dopo l'invio, «Nota» si chiude, «Assistente» resta aperto.
 */
@Composable
internal fun FieldPresetsScene(onBack: () -> Unit) {
  var preset by rememberSaveable { mutableStateOf(FieldPreset.Search) }
  var text by rememberSaveable { mutableStateOf("") }
  var active by rememberSaveable { mutableStateOf(false) }
  var outcome by rememberSaveable { mutableStateOf("Nessun invio") }
  var account by rememberSaveable { mutableStateOf(FieldAccounts.first()) }
  val open: (Boolean) -> Unit = { active = it }
  val spec = when (preset) {
    FieldPreset.Search -> FluidSearchSpec.search(
      text, { text = it }, prompt = "Cerca nella posta", onSearch = { outcome = "Cercato «$text»" },
      placement = FluidSearchPlacement.TopBar, behavior = FluidSearchBehavior.CollapseToButton,
      expansion = FluidSearchExpansion.StayAtTop, active = active, onActiveChange = open,
    )
    FieldPreset.Message -> FluidSearchSpec.message(
      text, { text = it }, onSend = { outcome = "Inviato «$text»"; text = "" }, active = active, onActiveChange = open,
    )
    FieldPreset.Note -> FluidSearchSpec.note(
      text, { text = it }, onDone = { outcome = "Nota salvata" }, placement = FluidSearchPlacement.BottomBar,
      active = active, onActiveChange = open,
    )
    FieldPreset.Assistant -> FluidSearchSpec.assistant(
      text, { text = it }, onAsk = { outcome = "Chiesto «$text»"; text = "" }, active = active, onActiveChange = open,
    )
  }
  FluidScreen(
    title = account,
    onBack = onBack,
    search = spec,
    toolbar = {
      title(
        FluidToolbarTitleMode.Large,
        menu = {
          FieldAccounts.map { name ->
            FluidContextAction(name, icon = if (name == FieldAccounts.first()) FluidIcons.Inbox else null,
              selected = name == account, onClick = { account = name })
          } + FluidContextAction("Aggiungi account", icon = FluidIcons.Add, startsSection = true, onClick = {})
        },
        selectorHint = "Cambia casella",
      )
      button("scrivi", FluidIcons.Edit, "Scrivi", onClick = { outcome = "Nuova mail" })
      item("account", label = "Account di Alessio", placement = FluidToolbarPlacement.PinnedTrailing, onClick = {}) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
          FluidToneTile(FluidTone.Primary) { Text("A", style = MaterialTheme.typography.labelLarge) }
        }
      }
    },
  ) {
    item(key = "preset-titolo") { FluidSectionHeader(title = "Il preset del campo") }
    item(key = "preset") {
      FluidListGroup {
        FieldPreset.entries.forEachIndexed { index, choice ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = choice.label,
            subtitle = choice.detail,
            selected = choice == preset,
            disclosure = false,
            modifier = Modifier.testTag(FieldSceneTags.preset(choice)),
            onClick = {
              if (choice != preset) {
                preset = choice
                active = false
                text = ""
              }
            },
          )
        }
      }
    }
    item(key = "esito") {
      FluidListGroup {
        FluidListRow(title = outcome, subtitle = "Tocca la lente, scrivi, invia", modifier = Modifier.testTag(FieldSceneTags.Outcome))
      }
    }
    item(key = "posta-titolo") { FluidSectionHeader(title = "Oggi") }
    item(key = "posta") {
      FluidListGroup {
        FieldMail.forEachIndexed { index, (from, subject) ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = from, subtitle = "$subject · $account")
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "In alto il campo resta in cima: i tasti accanto si sfocano e rientrano nel suo vetro, e tornano quando si chiude. " +
          "Gli altri preset stanno in basso e salgono con la tastiera.",
      )
    }
  }
}

/**
 * Notes, una trascrizione: «Trova nella pagina» (F6B.6). A riposo il campo non c'è; il tasto della barra lo apre in fondo,
 * sopra la tastiera, e la barra in alto non cambia. Le occorrenze le trova `rememberFluidFindState`; la scena porta in
 * vista quella corrente.
 */
@Composable
internal fun FindInPageScene(onBack: () -> Unit) {
  val find = rememberFluidFindState(FindTranscript)
  var finding by rememberSaveable { mutableStateOf(false) }
  val list = rememberLazyListState()
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val lead = with(LocalDensity.current) { FindLead.toPx() }
  FluidScreen(
    title = "Riunione del lunedì",
    onBack = onBack,
    listState = list,
    search = FluidSearchSpec.findInPage(find.query, find::onQueryChange, find.navigation, finding, { finding = it }),
    toolbar = {
      title(FluidToolbarTitleMode.Inline)
      button("trova", FluidIcons.Search, "Trova", onClick = { finding = true })
      button("condividi", FluidIcons.IosShare, "Condividi", onClick = {})
    },
  ) {
    item(key = "intestazione") { FluidSectionHeader(title = "Trascrizione · 12 minuti") }
    item(key = "conteggio") {
      FluidListGroup {
        FluidListRow(
          title = if (find.query.isEmpty()) "Cerca «progetto» o «lunedì»" else "«${find.query}»: ${find.total} occorrenze",
          subtitle = "Il tasto con la lente apre «Trova» sopra la tastiera",
          modifier = Modifier.testTag(FieldSceneTags.Count),
        )
      }
    }
    // L'ultima voce della lista: la scena la porta in vista dal suo indice.
    item(key = "testo") {
      Text(
        find.highlight(FluidFindDefaults.highlights()),
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layout = it },
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).testTag(FieldSceneTags.Transcript),
      )
    }
  }
  // L'occorrenza corrente in vista, un po' sotto la barra: il dato lo dà lo stato, lo scorrimento è della scena.
  LaunchedEffect(find.scrollRequest) {
    val request = find.scrollRequest ?: return@LaunchedEffect
    val text = layout ?: return@LaunchedEffect
    val index = list.layoutInfo.totalItemsCount - 1
    if (index >= 0) list.scrollToItem(index, (request.boundsIn(text).top - lead).roundToInt().coerceAtLeast(0))
  }
}

/** Quanto testo resta sopra l'occorrenza portata in vista: la barra in alto non la copre. */
private val FindLead = 160.dp

private val FieldMail = listOf(
  "Anna Bellini" to "Riunione di domani",
  "Luca" to "Le foto delle vacanze",
  "Marta R." to "Fattura di novembre",
  "Giorgio" to "Re: il progetto",
  "Sara" to "Cena venerdì?",
  "Paolo" to "Il preventivo",
  "Elena" to "Biglietti del treno",
  "Davide" to "Foto della festa",
)

internal val FindTranscript = """
  Buongiorno a tutti, cominciamo dal progetto della nuova sede. Lunedì scorso abbiamo visto le piante con l'architetto e
  il progetto è stato approvato quasi tutto: restano da decidere le sale riunioni del secondo piano.

  Marta ha preparato il calendario: il trasloco comincia lunedì 3 febbraio e finisce entro la fine del mese. Il progetto
  prevede due settimane per il magazzino e una per gli uffici. Se qualcosa slitta, si sposta tutto di una settimana.

  Luca chiede chi segue i fornitori. Per il progetto dei mobili se ne occupa Giorgio; per la rete e i telefoni Sara, che
  lunedì manda a tutti l'elenco delle prese. Anna ricorda che il preventivo della rete va firmato entro venerdì.

  Ultimo punto: la festa di inaugurazione. Si pensa a un lunedì sera di marzo, dopo la fine del progetto, con i clienti e
  le famiglie. Davide prepara una proposta per la prossima riunione, sempre di lunedì alle nove.
""".trimIndent()
