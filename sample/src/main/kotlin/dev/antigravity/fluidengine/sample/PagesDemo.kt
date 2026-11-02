package dev.antigravity.fluidengine.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
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
 * che scorre di lato e un cursore: dal suo inizio la riga passa la mano al back, il cursore mai.
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

/** Le righe che dicono dove sta ogni pagina (uniche: il titolo c'e' due volte, nella pagina e nella barra). */
internal const val PagesMarkA = "Apri B"
internal const val PagesMarkB = "Apri C sopra"
internal const val PagesMarkC = "Torna alla radice"
internal const val PagesChipsTag = "pages:chips"
internal const val PagesSliderTag = "pages:slider"

/** Il grafo della demo: "a" radice (1 azione), "b" Slide (2), "c" ShrinkSlide (3). Anche quello dei test. */
@Composable
internal fun PagesDemo(
  navController: NavHostController = rememberFluidNavController(),
  state: FluidPageHostState = rememberFluidPageHostState(),
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
        subtitle = "Apri una pagina e torna indietro col dito: da qualunque punto, o dal bordo.",
        actions = {
          FluidBarAction(icon = Icons.Rounded.Search, contentDescription = "Cerca", onClick = {})
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
          }
        }
        fillerRows("a")
      }
    }
    composable(PagesRouteB) {
      FluidScreen(
        title = "Pagina B",
        subtitle = "Trascina verso destra da un punto qualunque. Lenta oltre un terzo, o con una spinta.",
        onBack = { navController.popBackStack() },
        actions = {
          FluidBarAction(icon = Icons.Rounded.Share, contentDescription = "Condividi", onClick = {})
          FluidBarAction(icon = Icons.Rounded.Star, contentDescription = "Preferito", onClick = {})
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
    composable(PagesRouteC, transition = FluidPageTransition.ShrinkSlide) {
      var level by remember { mutableFloatStateOf(0.4f) }
      FluidScreen(
        title = "Pagina C",
        subtitle = "Questa prima rimpicciolisce sul posto, poi scivola via.",
        onBack = { navController.popBackStack() },
        actions = {
          FluidBarAction(icon = Icons.Rounded.Search, contentDescription = "Cerca", onClick = {})
          FluidBarAction(icon = Icons.Rounded.Share, contentDescription = "Condividi", onClick = {})
          FluidBarAction(icon = Icons.Rounded.Delete, contentDescription = "Elimina", onClick = {})
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
