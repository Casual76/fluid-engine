package dev.antigravity.fluidengine.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
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
 * Le pagine della 3.0 (fase 8), da sole: tre destinazioni in un FluidNavHost, niente barra.
 *
 * Un'attivita' a parte e non una scheda della galleria: la galleria non e' (ancora) un grafo di
 * navigazione, e qui il back di sistema deve arrivare all'host delle pagine e non chiudere l'app.
 * La barra ancorata e' il passo 5: queste pagine hanno un titolo e un tasto "Indietro" semplici, per
 * guardare il movimento e basta.
 */
class PagesDemoActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        // Gli stessi strumenti della galleria (registro aptico, grana): un indietro partito da un
        // tasto si legge nel registro che si e' acceso da Controlli.
        ProvideGalleryTools(SharedGalleryTools) {
          Box(modifier = Modifier.fillMaxSize()) {
            PagesDemo()
            GalleryToolsOverlay(SharedGalleryTools)
          }
        }
      }
    }
  }
}

internal const val PagesRouteA = "a"
internal const val PagesRouteB = "b"
internal const val PagesRouteC = "c"

/** Il grafo della demo: "a" radice, "b" Slide, "c" ShrinkSlide. Anche quello dei test. */
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
      DemoPage(
        title = "Pagine",
        subtitle = "Apri una pagina e torna indietro col dito: da qualunque punto, o dal bordo.",
        onBack = null,
      ) {
        FluidListRow(
          title = "Slide",
          subtitle = "Entra da destra; indietro come iOS, sotto il dito",
          onClick = { navController.navigate(PagesRouteB) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Rimpicciolisce e scivola",
          subtitle = "Prima si fa piccola, poi esce",
          onClick = { navController.navigate(PagesRouteC) },
        )
      }
    }
    composable(PagesRouteB) {
      DemoPage(
        title = "Pagina B",
        subtitle = "Trascina verso destra da un punto qualunque. Lenta oltre un terzo, o con una spinta.",
        onBack = { navController.popBackStack() },
      ) {
        FluidListRow(
          title = "Apri C sopra",
          subtitle = "Due pagine in pila",
          onClick = { navController.navigate(PagesRouteC) },
        )
        FluidListDivider()
        FluidListRow(title = "Una riga", subtitle = "Niente da aprire", disclosure = false)
      }
    }
    composable(PagesRouteC, transition = FluidPageTransition.ShrinkSlide) {
      DemoPage(
        title = "Pagina C",
        subtitle = "Questa prima rimpicciolisce sul posto, poi scivola via.",
        onBack = { navController.popBackStack() },
      ) {
        FluidListRow(
          title = "Torna alla radice",
          subtitle = "Un salto di piu' pagine",
          onClick = { navController.popBackStack(PagesRouteA, inclusive = false) },
        )
        FluidListDivider()
        FluidListRow(title = "Una riga", subtitle = "Niente da aprire", disclosure = false)
      }
    }
  }
}

@Composable
private fun DemoPage(
  title: String,
  subtitle: String,
  onBack: (() -> Unit)?,
  rows: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    if (onBack != null) FluidButton(text = "Indietro", onClick = onBack)
    Text(
      text = title,
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FluidSectionHeader(title = "Righe")
    FluidListGroup(modifier = Modifier.fillMaxWidth()) { rows() }
  }
}
