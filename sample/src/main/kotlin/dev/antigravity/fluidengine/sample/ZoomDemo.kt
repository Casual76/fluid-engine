package dev.antigravity.fluidengine.sample

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidBackSwipe
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState

// Lo zoom dalla sorgente (4.0, piano «iOS in Android», F5B.3), da solo: una griglia di card che si aprono in pagina e
// tornano col dito, e un tasto della barra che apre un foglio. Sta nell'attività delle pagine ([PagesDemoActivity], con
// l'extra [ZoomDemoScene]): il back di sistema deve arrivare all'host delle pagine e non chiudere l'app.

/** L'extra che fa aprire a [PagesDemoActivity] questa scena invece delle pagine di sempre. */
internal const val ZoomDemoScene = "pages.scene.zoom"

internal const val ZoomRouteGrid = "zoom"
internal const val ZoomRouteDetail = "zoom/{card}"
internal const val ZoomArgCard = "card"
internal const val ZoomCardCount = 8

/** I tag dei test: una card della griglia, il tasto della barra, la prima riga del foglio. */
internal fun zoomCardTag(card: Int): String = "zoom:card:$card"
internal const val ZoomSheetButton = "sposta"
internal const val ZoomSheetFirstRow = "Posta in arrivo"
internal const val ZoomDetailMark = "Torna col dito"

/** L'intent che apre la scena dello zoom col vetro [model] che la galleria chiede. */
internal fun zoomDemoIntent(context: Context, model: FluidGlassModel): Intent =
  pagesDemoIntent(context, model).putExtra(ZoomDemoScene, true)

/** Le card: un nome e due colori, quelli della foto finta (la stessa in cima alla pagina d'arrivo). */
private data class ZoomCard(val title: String, val place: String, val from: Color, val to: Color)

private val ZoomCards = listOf(
  ZoomCard("Faro", "Capo Spartivento", Color(0xFF3A7BD5), Color(0xFF8FB8DE)),
  ZoomCard("Bosco", "Foresta Umbra", Color(0xFF2F5D34), Color(0xFF7FB069)),
  ZoomCard("Tramonto", "Scala dei Turchi", Color(0xFFE9A23B), Color(0xFFC0392B)),
  ZoomCard("Ghiacciaio", "Forni", Color(0xFF6DB3D6), Color(0xFFE8F4FA)),
  ZoomCard("Vigna", "Langhe", Color(0xFF6B3E75), Color(0xFFB66D9C)),
  ZoomCard("Duna", "Piscinas", Color(0xFFF2994A), Color(0xFFF7D9A8)),
  ZoomCard("Lago", "Braies", Color(0xFF16697A), Color(0xFF82C0CC)),
  ZoomCard("Borgo", "Civita", Color(0xFF8D6E63), Color(0xFFD7CCC8)),
)

/**
 * La scena: un `FluidNavHost` con la griglia e la pagina di una card (`Expand` con la sorgente: lo zoom), dentro una chrome
 * e un host dei modali come nella galleria. Anche quella dei test ([navController], [state]).
 */
@Composable
internal fun ZoomDemo(
  navController: NavHostController = rememberFluidNavController(),
  state: FluidPageHostState = rememberFluidPageHostState(),
) {
  val namespace = rememberFluidGlassNamespace()
  val chrome = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chrome.activeBackdrop.value ?: fallbackBackdrop
  CompositionLocalProvider(LocalFluidGlassModalHostState provides modalHost) {
    Box(Modifier.fillMaxSize()) {
      ProvideFluidChrome(controller = chrome, bottomInset = 0.dp, scrollToTop = scrollToTop) {
        Box(Modifier.fillMaxSize().fluidChromePages().fluidGlassModalObscured()) {
          FluidNavHost(
            navController = navController,
            startDestination = ZoomRouteGrid,
            modifier = Modifier.fillMaxSize(),
            state = state,
          ) {
            composable(ZoomRouteGrid) {
              ZoomGridPage(namespace = namespace, onOpen = { card -> navController.navigate("zoom/$card") })
            }
            composable(
              ZoomRouteDetail,
              arguments = listOf(navArgument(ZoomArgCard) { type = NavType.IntType }),
              transition = FluidPageTransition.Expand,
              // Da qualunque punto: la pagina segue il dito e si rimpicciolisce verso la sua card.
              backSwipe = FluidBackSwipe.Anywhere,
              zoomSource = { entry -> entry.arguments?.getInt(ZoomArgCard)?.let { FluidTransitionSource(it, namespace) } },
            ) { entry ->
              val card = entry.arguments?.getInt(ZoomArgCard) ?: 0
              ZoomDetailPage(card = card, onBack = { navController.popBackStack() })
            }
          }
        }
      }
      FluidGlassModalHost(state = modalHost, backdrop = backdrop)
    }
  }
}

/** La griglia: due colonne di card, ognuna sorgente della sua pagina; il tasto «Sposta» della barra apre un foglio. */
@Composable
private fun ZoomGridPage(namespace: FluidGlassNamespace, onOpen: (Int) -> Unit) {
  var sheet by remember { mutableStateOf(false) }
  var moved by remember { mutableStateOf("Nessuno spostamento") }
  // Il tasto della barra come sorgente: la sua capsula (il gruppo, se ne ha uno) è dove il foglio nasce e rientra.
  val sheetSource = rememberFluidToolbarTransitionSource(ZoomSheetButton)
  FluidScreen(
    title = "Zoom",
    subtitle = "Tocca una card: la pagina nasce dalla card e ci torna col dito. «Sposta» apre un foglio dal tasto.",
    toolbar = {
      group("azioni") {
        button("archivia", FluidIcons.Archive, "Archivia", onClick = { moved = "Archiviato" })
        button(ZoomSheetButton, FluidIcons.Folder, "Sposta", onClick = { sheet = true })
        button("elimina", FluidIcons.Delete, "Elimina", onClick = { moved = "Eliminato" })
      }
    },
  ) {
    item(key = "grid-header") { FluidSectionHeader(title = "Luoghi") }
    for (row in 0 until ZoomCardCount / 2) {
      item(key = "grid-$row") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          for (column in 0 until 2) {
            val index = row * 2 + column
            ZoomCardTile(
              card = index,
              namespace = namespace,
              onOpen = onOpen,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
    item(key = "moved") { FluidSectionFootnote(text = "Ultimo esito: $moved") }
  }
  FluidGlassSheet(
    visible = sheet,
    onDismissRequest = { sheet = false },
    paneTitle = "Sposta",
    source = sheetSource,
  ) {
    ZoomMailboxes(onPick = { box ->
      moved = "Spostato in $box"
      sheet = false
    })
  }
}

/** Una card: la sorgente prima di tutto (consumata, sparisce intera), poi forma, foto finta e scritte. */
@Composable
private fun ZoomCardTile(card: Int, namespace: FluidGlassNamespace, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
  val data = ZoomCards[card % ZoomCards.size]
  val shape = ContinuousCornerShape(FluidRadius.Card)
  Column(
    modifier = modifier
      .testTag(zoomCardTag(card))
      .fluidPressable(onClick = { onOpen(card) }, role = Role.Button)
      .fluidTransitionSource(card, namespace, shape)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .aspectRatio(1.25f)
        .background(Brush.linearGradient(listOf(data.from, data.to))),
    )
    Text(
      text = data.title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
    )
    Text(
      text = data.place,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    )
  }
}

/** La pagina di una card: la stessa foto in cima (è ciò che si vede nella card all'inizio dello zoom), poi il testo. */
@Composable
private fun ZoomDetailPage(card: Int, onBack: () -> Unit) {
  val data = ZoomCards[card % ZoomCards.size]
  FluidScreen(title = data.title, subtitle = data.place, onBack = onBack) {
    item(key = "photo") {
      Box(
        Modifier
          .fillMaxWidth()
          .height(240.dp)
          .clip(ContinuousCornerShape(FluidRadius.Card))
          .background(Brush.linearGradient(listOf(data.from, data.to))),
        contentAlignment = Alignment.BottomStart,
      ) {
        Text(
          text = data.place,
          color = Color.White,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }
    }
    item(key = "rows") {
      FluidListGroup {
        FluidListRow(
          title = ZoomDetailMark,
          subtitle = "Trascina verso destra da qualunque punto: la pagina rimpicciolisce sotto il dito verso la card.",
          disclosure = false,
        )
        FluidListDivider()
        FluidListRow(title = "Lascia a metà", subtitle = "Sotto la soglia torna intera; riprendila mentre torna.", disclosure = false)
        FluidListDivider()
        FluidListRow(title = "Tasto indietro", subtitle = "Rientra nella card con la molla delle pagine.", disclosure = false)
      }
    }
    item(key = "text") {
      FluidSectionFootnote(
        text = "La card resta nascosta finché la pagina è aperta e rinasce quando la pagina ci rientra: le due non si " +
          "vedono mai insieme.",
      )
    }
  }
}

/** Il contenuto del foglio: le cartelle, come il foglio «Sposta» di Mail (WWDC25 284, 14:07). */
@Composable
private fun ZoomMailboxes(onPick: (String) -> Unit) {
  val boxes = listOf(
    Triple(ZoomSheetFirstRow, "51 messaggi", FluidIcons.Inbox),
    Triple("Archivio", "Tutto quello che hai tenuto", FluidIcons.Archive),
    Triple("Cestino", "Si svuota dopo 30 giorni", FluidIcons.Delete),
    Triple("Viaggi", "Prenotazioni e biglietti", FluidIcons.Folder),
    Triple("Ricevute", "Acquisti e fatture", FluidIcons.Folder),
  )
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
      text = "Sposta il messaggio in una cartella",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(bottom = 12.dp),
    )
    FluidListGroup {
      boxes.forEachIndexed { index, (name, detail, icon) ->
        if (index > 0) FluidListDivider()
        FluidListRow(
          title = name,
          subtitle = detail,
          leading = { dev.antigravity.fluidengine.ui.fluid.FluidIcon(icon, contentDescription = null) },
          onClick = { onPick(name) },
        )
      }
    }
  }
}
