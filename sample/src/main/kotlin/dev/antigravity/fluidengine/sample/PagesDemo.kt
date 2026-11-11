package dev.antigravity.fluidengine.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidBackSwipe
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * Le pagine della 3.0 (fase 8), da sole: tre `FluidScreen` in un FluidNavHost, con la barra ancorata.
 *
 * Un'attivita' a parte e non una scheda della galleria: qui il back di sistema deve arrivare all'host
 * delle pagine e non chiudere l'app. Le tre pagine hanno un numero diverso di azioni nella barra (una,
 * due, tre) per guardare i tasti di vetro che diventano quelli della pagina accanto, e "c" ha una riga
 * che scorre di lato e un cursore: dal suo inizio la riga passa la mano al back, il cursore mai. La
 * "bozza" ha un `BackHandler` che chiede prima di uscire: il back a dito passa da lui come quello di
 * sistema, e la pagina torna sotto il dito mentre l'avviso decide.
 */
class PagesDemoActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        PagesDemo()
      }
    }
  }
}

internal const val PagesRouteA = "a"
internal const val PagesRouteB = "b"
internal const val PagesRouteC = "c"
internal const val PagesRouteDraft = "bozza"

/** Le righe che dicono dove sta ogni pagina (uniche: il titolo c'e' due volte, nella pagina e nella barra). */
internal const val PagesMarkA = "Apri B"
internal const val PagesMarkB = "Apri C sopra"
internal const val PagesMarkC = "Torna alla radice"
internal const val PagesMarkDraft = "Apri una bozza"
internal const val PagesDraftDirty = "Modifiche non salvate"
internal const val PagesChipsTag = "pages:chips"
internal const val PagesSliderTag = "pages:slider"
internal const val PagesSegmentedTag = "pages:segmented"
internal val PagesPeriods = listOf("Giorno", "Settimana", "Mese")

/**
 * Il grafo della demo: "a" radice (1 azione), "b" Slide (2), "c" ShrinkSlide (3). Anche quello dei test.
 *
 * [backSwipe] e' quello di B e C: di serie il bordo, come il gesto di sistema. Solo i test dei gesti
 * annidati e del «da qualunque punto» lo chiedono diverso.
 */
@Composable
internal fun PagesDemo(
  navController: NavHostController = rememberFluidNavController(),
  state: FluidPageHostState = rememberFluidPageHostState(),
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
) {
  FluidNavHost(
    navController = navController,
    startDestination = PagesRouteA,
    modifier = Modifier.fillMaxSize(),
    state = state,
  ) {
    composable(PagesRouteA) {
      FluidScreen(
        title = "Pagine",
        subtitle = "Apri una pagina e torna indietro col dito dal bordo, come col gesto di sistema.",
        actions = {
          FluidBarAction(icon = FluidIcons.Search, contentDescription = "Cerca", onClick = {})
        },
      ) {
        item(key = "rows") {
          FluidListGroup {
            FluidListRow(
              title = PagesMarkA,
              subtitle = "Entra da destra; indietro come iOS, sotto il dito",
              onClick = { navController.navigate(PagesRouteB) },
            )
            FluidListDivider()
            FluidListRow(
              title = "Apri C",
              subtitle = "Prima si fa piccola, poi esce",
              onClick = { navController.navigate(PagesRouteC) },
            )
            FluidListDivider()
            FluidListRow(
              title = PagesMarkDraft,
              subtitle = "Chiede prima di uscire, anche al back col dito",
              onClick = { navController.navigate(PagesRouteDraft) },
            )
          }
        }
        fillerRows("a")
      }
    }
    composable(PagesRouteB, backSwipe = backSwipe) {
      FluidScreen(
        title = "Pagina B",
        subtitle = "Trascina verso destra dal bordo sinistro. Lenta oltre un terzo, o con una spinta.",
        onBack = { navController.popBackStack() },
        actions = {
          FluidBarAction(icon = FluidIcons.Share, contentDescription = "Condividi", onClick = {})
          FluidBarAction(icon = FluidIcons.Filled.Star, contentDescription = "Preferito", onClick = {})
        },
      ) {
        item(key = "rows") {
          FluidListGroup {
            FluidListRow(
              title = PagesMarkB,
              subtitle = "Due pagine in pila, tre tasti che cambiano",
              onClick = { navController.navigate(PagesRouteC) },
            )
            FluidListDivider()
            FluidListRow(title = "Una riga", subtitle = "Niente da aprire", disclosure = false)
          }
        }
        fillerRows("b")
      }
    }
    composable(PagesRouteC, transition = FluidPageTransition.ShrinkSlide, backSwipe = backSwipe) {
      var level by remember { mutableFloatStateOf(0.4f) }
      var period by remember { mutableStateOf(PagesPeriods.first()) }
      FluidScreen(
        title = "Pagina C",
        subtitle = "Questa prima rimpicciolisce sul posto, poi scivola via.",
        onBack = { navController.popBackStack() },
        actions = {
          FluidBarAction(icon = FluidIcons.Search, contentDescription = "Cerca", onClick = {})
          FluidBarAction(icon = FluidIcons.Share, contentDescription = "Condividi", onClick = {})
          FluidBarAction(icon = FluidIcons.Delete, contentDescription = "Elimina", onClick = {})
        },
      ) {
        item(key = "chips-header") { FluidSectionHeader(title = "Scorre di lato") }
        item(key = "chips") {
          LazyRow(
            modifier = Modifier.fillMaxWidth().testTag(PagesChipsTag),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp),
          ) {
            items(12) { index ->
              Box(
                modifier = Modifier
                  .size(width = 96.dp, height = 56.dp)
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh, ContinuousCornerShape(FluidRadius.Card)),
                contentAlignment = Alignment.Center,
              ) {
                Text(text = "Voce ${index + 1}", style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
        item(key = "slider") {
          FluidSlider(
            value = level,
            onValueChange = { level = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(PagesSliderTag),
            contentDescription = "Livello",
          )
        }
        // Come il cursore, il segmentato non porta mai indietro: un trascinamento di lato che parte su di
        // lui, anche sul segmento scelto (la goccia), e' suo (3.0.1).
        item(key = "segmented") {
          FluidSegmentedControl(
            options = PagesPeriods,
            selected = period,
            onSelect = { period = it },
            modifier = Modifier.padding(vertical = 8.dp).testTag(PagesSegmentedTag),
            label = { it },
          )
        }
        item(key = "rows") {
          FluidListGroup {
            FluidListRow(
              title = PagesMarkC,
              subtitle = "Un salto di piu' pagine",
              onClick = { navController.popBackStack(PagesRouteA, inclusive = false) },
            )
            FluidListDivider()
            FluidListRow(title = "Una riga", subtitle = "Niente da aprire", disclosure = false)
          }
        }
        fillerRows("c")
      }
    }
    composable(PagesRouteDraft) {
      var dirty by remember { mutableStateOf(true) }
      var asking by remember { mutableStateOf(false) }
      // Il gestore della pagina: abilitato solo con modifiche da perdere. Il back a dito oltre la soglia
      // arriva qui come quello di sistema; senza modifiche la pagina esce come le altre.
      BackHandler(enabled = dirty) { asking = true }
      FluidScreen(
        title = "Bozza",
        subtitle = "Torna indietro col dito: con le modifiche accese la pagina torna sotto il dito e chiede.",
        onBack = { if (dirty) asking = true else navController.popBackStack() },
      ) {
        item(key = "dirty") {
          FluidListGroup {
            FluidToggleRow(
              title = PagesDraftDirty,
              subtitle = "Spento, il back non chiede niente",
              checked = dirty,
              onCheckedChange = { dirty = it },
            )
          }
        }
        fillerRows("bozza")
      }
      if (asking) {
        FluidAlert(
          onDismissRequest = { asking = false },
          title = "Uscire senza salvare?",
          message = "Le modifiche alla bozza andranno perse.",
          actions = listOf(
            FluidAlertAction("Resta", onClick = { asking = false }, emphasis = FluidAlertAction.Emphasis.Preferred),
            FluidAlertAction(
              "Esci",
              onClick = {
                asking = false
                navController.popBackStack()
              },
              emphasis = FluidAlertAction.Emphasis.Destructive,
            ),
          ),
        )
      }
    }
  }
}

/** Abbastanza righe da far scorrere la pagina e agganciare il titolo alla barra. */
private fun androidx.compose.foundation.lazy.LazyListScope.fillerRows(page: String) {
  item(key = "filler-header") { FluidSectionHeader(title = "Righe") }
  item(key = "filler") {
    FluidListGroup {
      repeat(14) { index ->
        if (index > 0) FluidListDivider()
        FluidListRow(
          title = "Riga ${index + 1} di $page",
          subtitle = "Per scorrere e far agganciare il titolo",
          disclosure = false,
        )
      }
    }
  }
}
