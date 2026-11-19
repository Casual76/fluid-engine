package dev.antigravity.fluidengine.sample.presentations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.zoomDemoIntent
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSheetTransition
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// Le presentazioni di F5B (piano «iOS in Android»), nella pagina «Righe e fogli» della galleria: tutto quello che nasce
// da una sorgente o sale dal basso, nell'ordine in cui un'app lo incontra. Il popover dal tasto della barra; la conferma
// sopra «Elimina»; l'avviso con e senza sorgente; il foglio di vetro coi detent, la maniglia, la soglia di oscuramento e
// la dissolvenza; lo zoom dalla card e il foglio dal tasto (una scena a sé, perché lo zoom fra pagine vuole un
// `FluidNavHost` e il back di sistema). La pagina dichiara la barra e il suo tasto ([PresentationsPopoverButton]); il
// resto sta qui.

/** L'id del tasto della barra che apre il popover: la pagina lo dichiara, la sorgente lo cerca. */
internal const val PresentationsPopoverButton = "presentations-popover-button"

/** Quale presentazione è aperta: una sola alla volta, come in una schermata vera. */
internal enum class Presentation {
  Popover, InlineConfirmation, AnchoredAlert, CenteredAlert, GlassSheet, SmallSheet, FadeSheet,
}

/** Lo stato del capitolo: la presentazione aperta e le sorgenti delle righe, nello spazio dei nomi della pagina. */
@Stable
internal class PresentationsDemoState(val namespace: FluidGlassNamespace) {
  var open by mutableStateOf<Presentation?>(null)
  val deleteSource = FluidTransitionSource("delete", namespace)
  val alertSource = FluidTransitionSource("alert", namespace)

  fun close() {
    open = null
  }

  /** Un foglio di vetro è aperto: la pagina si disegna fuori schermo perché il foglio la campioni intera. */
  val sheetOpen: Boolean get() = open in setOf(Presentation.GlassSheet, Presentation.SmallSheet, Presentation.FadeSheet)
}

@Composable
internal fun rememberPresentationsDemoState(): PresentationsDemoState {
  val namespace = rememberFluidGlassNamespace()
  return remember(namespace) { PresentationsDemoState(namespace) }
}

/**
 * Le righe del capitolo, in tre sezioni sotto un titolo. Le sorgenti stanno **sulle righe** (`fluidTransitionSource`,
 * col raggio dei controlli): la conferma si posa sopra «Elimina», l'avviso parte dalla sua riga e ci rientra.
 */
internal fun LazyListScope.presentationsChapter(state: PresentationsDemoState, glass: Boolean) {
  item(key = "presentations-chapter") {
    ChapterTitle(
      title = "Presentazioni",
      detail = "Tutto nasce dalla sua sorgente e ci torna: il popover dal tasto, la conferma dal comando, l'avviso " +
        "dalla riga, il foglio dal basso o dal tasto, la pagina dalla card.",
    )
  }
  item(key = "presentations-header") { FluidSectionHeader(title = "Dal tasto e dalla riga") }
  item(key = "presentations") {
    val shape = ContinuousCornerShape(FluidRadius.Control)
    FluidListGroup(glass = glass) {
      FluidListRow(
        title = "Popover dal tasto",
        subtitle = "Il tasto ··· della barra diventa il pannello e ci rientra; la pagina resta attiva.",
        onClick = { state.open = Presentation.Popover },
      )
      FluidListDivider()
      FluidListRow(
        title = "Elimina",
        subtitle = "",
        modifier = Modifier.fluidTransitionSource("delete", state.namespace, shape),
        onClick = { state.open = Presentation.InlineConfirmation },
      )
      FluidListDivider()
      FluidListRow(
        title = "Avviso dal tasto",
        subtitle = "",
        modifier = Modifier.fluidTransitionSource("alert", state.namespace, shape),
        onClick = { state.open = Presentation.AnchoredAlert },
      )
      FluidListDivider()
      FluidListRow(title = "Avviso al centro", subtitle = "", onClick = { state.open = Presentation.CenteredAlert })
    }
  }
  item(key = "glass-sheets-header") { FluidSectionHeader(title = "Foglio di vetro") }
  item(key = "glass-sheets") {
    FluidListGroup(glass = glass) {
      FluidListRow(
        title = "Foglio con i detent",
        subtitle = "A meta' altezza e' vetro e la pagina resta attiva; in cima e' una pagina e quella sotto si scurisce. " +
          "Trascinalo, o tocca la maniglia.",
        onClick = { state.open = Presentation.GlassSheet },
        modifier = Modifier.testTag("demo-glass-sheet"),
      )
      FluidListDivider()
      FluidListRow(
        title = "Foglio piccolo",
        subtitle = "Tre altezze: 200 dp, meta', tutta.",
        onClick = { state.open = Presentation.SmallSheet },
      )
      FluidListDivider()
      FluidListRow(
        title = "Foglio in dissolvenza",
        subtitle = "Compare al suo posto invece di salire (iOS 27).",
        onClick = { state.open = Presentation.FadeSheet },
      )
    }
  }
  item(key = "zoom-header") { FluidSectionHeader(title = "Zoom dalla sorgente") }
  item(key = "zoom") {
    val context = LocalContext.current
    // La scena sta nell'attività delle pagine: il vetro chiesto alla galleria va con lei.
    val model = LocalFluidGlassModel.current
    FluidListGroup(glass = glass) {
      FluidListRow(
        title = "Zoom dalla card e foglio dal tasto",
        subtitle = "Card che diventano pagine e tornano col dito; «Sposta» apre un foglio che nasce dal suo tasto.",
        onClick = { context.startActivity(zoomDemoIntent(context, model)) },
      )
    }
  }
}

/**
 * I pannelli del capitolo. Si dichiarano qui e si disegnano nell'host della galleria: fuori da un host il popover, la
 * conferma e i fogli non si aprono (e non vanno in crash), l'avviso ripiega sulla finestra di piattaforma.
 *
 * Il popover cambia l'ordine della pagina ([sortLabels], [sortSelected], [onSort]): un compito in parallelo, la pagina
 * resta attiva. [itemName] è il nome che la conferma chiede di eliminare; [onResult] scrive l'esito sotto la pagina.
 */
@Composable
internal fun PresentationsOverlays(
  state: PresentationsDemoState,
  popoverSource: FluidTransitionSource,
  glass: Boolean,
  itemName: String,
  sortLabels: List<String>,
  sortSelected: Int,
  onSort: (Int) -> Unit,
  onResult: (String) -> Unit,
) {
  val open = state.open
  FluidGlassModalPortal(
    visible = open == Presentation.Popover,
    onDismissRequest = state::close,
    source = popoverSource,
    paneTitle = "Opzioni della galleria",
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Ordina per", style = MaterialTheme.typography.titleMedium)
      sortLabels.forEachIndexed { index, label ->
        FluidChoiceRow(title = label, selected = sortSelected == index, onSelect = {
          onSort(index)
          state.close()
        })
      }
    }
  }
  FluidConfirmationDialog(
    item = itemName.takeIf { open == Presentation.InlineConfirmation },
    onDismissRequest = state::close,
    source = state.deleteSource,
    title = { "Eliminare «$it»?" },
    actions = { shown ->
      listOf(FluidAlertAction("Elimina", { onResult("Eliminata «$shown»") }, FluidAlertAction.Emphasis.Destructive))
    },
  )
  FluidAlert(
    item = open?.takeIf { it == Presentation.AnchoredAlert || it == Presentation.CenteredAlert },
    onDismissRequest = state::close,
    title = { "Modifiche non salvate" },
    message = { "Vuoi continuare a modificare questa voce?" },
    source = state.alertSource.takeIf { open == Presentation.AnchoredAlert },
    actions = { listOf(FluidAlertAction("Continua", state::close, FluidAlertAction.Emphasis.Preferred)) },
  )

  val sheetClosed = {
    state.close()
    onResult("Foglio chiuso")
  }
  val sheetRested: (FluidSheetDetent) -> Unit = { onResult("Foglio fermo: ${detentLabel(it)}") }
  // Il foglio della soglia: a meta' altezza un compito che accompagna la pagina (niente velo, la pagina si tocca), sopra
  // un compito che la interrompe.
  FluidGlassSheet(
    visible = open == Presentation.GlassSheet,
    onDismissRequest = sheetClosed,
    detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    undimmedUpTo = FluidSheetDetent.Medium,
    onSelectedDetentChange = sheetRested,
    paneTitle = "Luoghi",
  ) {
    PlacesSheet(glass = glass)
  }
  FluidGlassSheet(
    visible = open == Presentation.SmallSheet,
    onDismissRequest = sheetClosed,
    detents = remember { setOf(FluidSheetDetent.Height(200.dp), FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    onSelectedDetentChange = sheetRested,
    paneTitle = "Luoghi",
  ) {
    PlacesSheet(glass = glass)
  }
  FluidGlassSheet(
    visible = open == Presentation.FadeSheet,
    onDismissRequest = sheetClosed,
    detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    onSelectedDetentChange = sheetRested,
    transition = FluidSheetTransition.CrossFade,
    paneTitle = "Luoghi",
  ) {
    PlacesSheet(glass = glass)
  }
}

/** Il titolo di un capitolo della pagina, come nella galleria dei menu. */
@Composable
internal fun ChapterTitle(title: String, detail: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Il nome di un detent, per l'esito della demo. */
private fun detentLabel(detent: FluidSheetDetent): String = when (detent) {
  FluidSheetDetent.Medium -> "a meta'"
  FluidSheetDetent.Large -> "in cima"
  is FluidSheetDetent.Fraction -> "al ${(detent.fraction * 100).toInt()}%"
  is FluidSheetDetent.Height -> "a ${detent.height.value.toInt()} dp"
}

/**
 * Il contenuto dei fogli di vetro: un titolo e una lista di luoghi che scorre, come il foglio di Apple della sessione 323
 * («Select landmarks»). La lista scorre sotto il titolo; finché il foglio non è in cima, lo scorrimento verso l'alto alza
 * il foglio.
 */
@Composable
private fun ColumnScope.PlacesSheet(glass: Boolean) {
  Text(
    text = "Scegli i luoghi",
    style = MaterialTheme.typography.titleMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).align(Alignment.CenterHorizontally),
  )
  Column(
    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Places.forEach { (region, places) ->
      FluidSectionHeader(title = region)
      FluidListGroup(glass = glass) {
        Column(Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
          places.forEachIndexed { index, place ->
            if (index > 0) FluidListDivider()
            FluidListRow(title = place, subtitle = region, disclosure = false)
          }
        }
      }
    }
  }
}

private val Places: List<Pair<String, List<String>>> = listOf(
  "Asia" to listOf("Everest", "Fuji", "Wulingyuan"),
  "Africa" to listOf("Deadvlei", "Sahara", "Serengeti"),
  "Antartide" to listOf("Isole Shetland Meridionali"),
  "Oceania" to listOf("Grande barriera corallina", "Uluru"),
  "Europa" to listOf("Dolomiti", "Cinque Terre", "Etna"),
)
