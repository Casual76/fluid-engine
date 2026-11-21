package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidZoomStyle
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// «Un elemento che diventa pagina» (piano «iOS in Android», F6B.5c): i quattro preset nei casi delle app, rifatti con il
// solo codice dell'engine. Nessuna riga di movimento qui: ogni scena dichiara il suo preset sulla rotta e marca la sua
// sorgente, il resto è dell'host. Le app non migrano (scelta di Alessio del 21 novembre): sono scene della galleria.
//
// Ogni caso è un grafo annidato la cui partenza è la pagina della sorgente: un collegamento che apre la destinazione
// ricostruisce sotto di lei la pagina giusta (la griglia sotto il visore), e il ritorno ci trova la sorgente.

/** Le rotte delle scene: un grafo per caso, la partenza è la pagina della sorgente. */
internal object ElementToPageRoutes {
  const val Index = "elementi"
  const val MusicGraph = "elementi/musica-grafo"
  const val Library = "elementi/musica"
  const val Player = "elementi/musica/lettore"
  const val PhotosGraph = "elementi/foto-grafo"
  const val Grid = "elementi/foto"
  const val ArgPhoto = "foto"
  const val Viewer = "elementi/foto/{$ArgPhoto}"
  const val WeatherGraph = "elementi/meteo-grafo"
  const val Weather = "elementi/meteo"
  const val Places = "elementi/meteo/luoghi"
  const val MailGraph = "elementi/posta-grafo"
  const val Inbox = "elementi/posta"
  const val Compose = "elementi/posta/scrivi"

  fun viewer(photo: Int): String = "elementi/foto/$photo"
}

/** I collegamenti che aprono le destinazioni (`adb shell am start -d …` nella galleria; nei test `navigate(uri)`). */
internal object ElementToPageLinks {
  const val Base = "fluidengine://elementi"
  const val Player = "$Base/lettore"
  const val Viewer = "$Base/foto/{${ElementToPageRoutes.ArgPhoto}}"
  const val Places = "$Base/luoghi"
  const val Compose = "$Base/scrivi"

  fun viewer(photo: Int): String = "$Base/foto/$photo"
}

/** Le identità delle sorgenti e degli elementi condivisi, le stesse nelle due pagine. */
internal object ElementToPageIds {
  const val MiniPlayer = "mini-lettore"
  const val Cover = "copertina"
  const val Pill = "localita"
  const val ComposeButton = "scrivi"

  fun photo(index: Int): String = "foto-$index"
}

/** I tag dei test. */
internal object ElementToPageTags {
  const val MiniPlayer = "elementi:mini"
  const val PlayerCover = "elementi:lettore:copertina"
  const val Pill = "elementi:pillola"
  const val Viewer = "elementi:visore"

  fun thumbnail(index: Int): String = "elementi:foto:$index"
}

/**
 * La pagina della galleria: un `FluidNavHost` con l'indice dei quattro casi e i loro grafi. Le scene si aprono anche dai
 * collegamenti di [ElementToPageLinks]. [navController], [state] e [photos] servono ai test (`FluidElementToPageUiTest`):
 * la galleria li lascia di serie.
 */
@Composable
internal fun ElementToPagePage(
  onClose: (() -> Unit)? = null,
  navController: NavHostController = rememberFluidNavController(),
  state: FluidPageHostState = rememberFluidPageHostState(),
  photos: LazyListState = rememberLazyListState(),
) {
  val ns = rememberFluidGlassNamespace()
  var place by rememberSaveable { mutableStateOf(ElementToPagePlaces.first().name) }
  var playing by rememberSaveable { mutableStateOf(true) }
  val back: () -> Unit = { navController.popBackStack() }
  FluidNavHost(navController, startDestination = ElementToPageRoutes.Index, modifier = Modifier.fillMaxSize(), state = state) {
    composable(ElementToPageRoutes.Index) {
      ElementToPageIndex(onClose = onClose, onOpen = { route -> navController.navigate(route) })
    }

    // Fluidify: il mini lettore sale fino a «In riproduzione», col dito e al tocco; si chiude tirandolo giù.
    navigation(startDestination = ElementToPageRoutes.Library, route = ElementToPageRoutes.MusicGraph) {
      composable(ElementToPageRoutes.Library) {
        LibraryPage(ns, playing, onPlay = { playing = !playing }, onBack = back,
          onOpen = { navController.navigate(ElementToPageRoutes.Player) })
      }
      composable(
        ElementToPageRoutes.Player,
        deepLinks = listOf(navDeepLink { uriPattern = ElementToPageLinks.Player }),
        zoomSource = { FluidTransitionSource(ElementToPageIds.MiniPlayer, ns) },
        zoom = FluidZoomStyle.Rise,
      ) {
        PlayerPage(ns, playing, onPlay = { playing = !playing }, onClose = back)
      }
    }

    // Gallery: una miniatura della griglia si apre nel visore e si richiude trascinando in giù.
    navigation(startDestination = ElementToPageRoutes.Grid, route = ElementToPageRoutes.PhotosGraph) {
      composable(ElementToPageRoutes.Grid) {
        PhotoGridPage(ns, photos, onBack = back, onOpen = { photo -> navController.navigate(ElementToPageRoutes.viewer(photo)) })
      }
      composable(
        ElementToPageRoutes.Viewer,
        arguments = listOf(navArgument(ElementToPageRoutes.ArgPhoto) { type = NavType.IntType }),
        deepLinks = listOf(navDeepLink { uriPattern = ElementToPageLinks.Viewer }),
        zoomSource = { entry -> entry.arguments?.getInt(ElementToPageRoutes.ArgPhoto)?.let { FluidTransitionSource(it, ns) } },
        zoom = FluidZoomStyle.Zoom,
      ) { entry ->
        ViewerPage(ns, entry.arguments?.getInt(ElementToPageRoutes.ArgPhoto) ?: 0, onClose = back)
      }
    }

    // Weather: la pillola della località cresce nella pagina delle località, di vetro fino all'arrivo.
    navigation(startDestination = ElementToPageRoutes.Weather, route = ElementToPageRoutes.WeatherGraph) {
      composable(ElementToPageRoutes.Weather) {
        WeatherPage(ns, place, onBack = back, onOpen = { navController.navigate(ElementToPageRoutes.Places) })
      }
      composable(
        ElementToPageRoutes.Places,
        deepLinks = listOf(navDeepLink { uriPattern = ElementToPageLinks.Places }),
        zoomSource = { FluidTransitionSource(ElementToPageIds.Pill, ns) },
        zoom = FluidZoomStyle.Grow,
      ) {
        PlacesPage(selected = place, onPick = { picked -> place = picked; back() })
      }
    }

    // Mail: «Scrivi» della barra in basso diventa il compositore. La sorgente la trova l'host, dal tasto della pagina sotto.
    navigation(startDestination = ElementToPageRoutes.Inbox, route = ElementToPageRoutes.MailGraph) {
      composable(ElementToPageRoutes.Inbox) {
        InboxPage(onBack = back, onCompose = { navController.navigate(ElementToPageRoutes.Compose) })
      }
      composable(
        ElementToPageRoutes.Compose,
        deepLinks = listOf(navDeepLink { uriPattern = ElementToPageLinks.Compose }),
        zoom = FluidZoomStyle.fromToolbar(ElementToPageIds.ComposeButton),
      ) {
        ComposerPage(onClose = back)
      }
    }
  }
}

/** L'indice: i quattro casi, con cosa provare in ognuno. */
@Composable
private fun ElementToPageIndex(onClose: (() -> Unit)?, onOpen: (String) -> Unit) {
  val cases = listOf(
    Triple("Il mini lettore", "Fluidify · Sale: tiralo su o toccalo, poi tira giù «In riproduzione».", ElementToPageRoutes.MusicGraph),
    Triple("Dalla griglia al visore", "Gallery · Zoom: la foto viaggia nel visore; si chiude trascinando in giù.", ElementToPageRoutes.PhotosGraph),
    Triple("La pillola della località", "Weather · Cresce: il vetro della pillola diventa la pagina.", ElementToPageRoutes.WeatherGraph),
    Triple("«Scrivi» dalla barra", "Mail · Dalla barra: il tasto diventa il compositore.", ElementToPageRoutes.MailGraph),
  )
  FluidScreen(
    title = "Un elemento che diventa pagina",
    subtitle = "Quattro preset, una riga per l'app. Le pagine sono rotte vere: indietro, collegamenti, ripristino.",
    onBack = onClose,
  ) {
    item(key = "intestazione") { FluidSectionHeader(title = "Casi delle app") }
    item(key = "casi") {
      FluidListGroup {
        cases.forEachIndexed { index, (title, detail, route) ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = title, subtitle = detail, onClick = { onOpen(route) })
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "La sorgente non si vede mentre la pagina è aperta e rinasce quando la pagina ci rientra. Se la sorgente " +
          "non c'è più (una foto uscita dallo scorrimento), la pagina torna col ripiego, senza salti.",
      )
    }
  }
}

/** La misura di una riga di miniature nella griglia: tre per riga. */
internal const val ElementToPageGridColumns = 3

/** Quante foto finte ha la griglia: abbastanza da far uscire una miniatura dallo schermo. */
internal const val ElementToPagePhotoCount = 45

internal val ElementToPageGridSpacing = 4.dp
