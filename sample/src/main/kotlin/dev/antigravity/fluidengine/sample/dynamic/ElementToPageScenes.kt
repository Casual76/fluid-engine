package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.AttachFile
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowDown
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowUp
import dev.antigravity.fluidengine.ui.fluid.icons.LocationOn
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.icons.SkipNext
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.page.FluidGrowingPagePill
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.page.FluidZoomStyle
import dev.antigravity.fluidengine.ui.page.fluidGrowingPageChrome
import dev.antigravity.fluidengine.ui.page.fluidSharedElement
import dev.antigravity.fluidengine.ui.page.fluidZoomOpen
import dev.antigravity.fluidengine.ui.page.fluidZoomSource
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// Le quattro scene di «un elemento che diventa pagina» (F6B.5c). Ognuna è una sorgente marcata con un preset e una
// destinazione che lo dichiara: nessun codice di movimento. I colori delle copertine e delle foto finte sono pieni e
// diversi l'uno dall'altro: li cercano anche i test sui pixel (`FluidElementToPageUiTest`).

/** La copertina finta del brano: la stessa nel mini lettore e in «In riproduzione». */
internal val ElementToPageCoverColor = Color(0xFFE6A379)

/** Il colore pieno della foto finta [index], diverso per ogni foto e lontano dal fondo del tema. */
internal fun elementToPagePhotoColor(index: Int): Color {
  val hue = (index * 47f) % 360f
  return Color.hsv(hue, 0.62f, 0.78f)
}

// --- Fluidify: il mini lettore sopra la tab bar diventa «In riproduzione» (Sale) -------------------------------------

@Composable
internal fun LibraryPage(ns: FluidGlassNamespace, playing: Boolean, onPlay: () -> Unit, onBack: () -> Unit, onOpen: () -> Unit) {
  val sections = listOf("Ascolti recenti", "Playlist", "Album", "Artisti", "Brani", "Scaricati", "Generi", "Compilation")
  FluidScreen(
    title = "Libreria",
    onBack = onBack,
    extraBottomPadding = 88.dp,
    overlay = { backdrop ->
      // L'accessorio è dell'app (come quello sopra la tab bar): la sorgente è il suo contenuto, marcato con il preset.
      FluidFloatingCapsule(
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        backdrop = backdrop,
        fillWidth = true,
      ) {
        Row(
          Modifier.weight(1f).height(48.dp).testTag(ElementToPageTags.MiniPlayer)
            .fluidZoomSource(ElementToPageIds.MiniPlayer, ns, FluidZoomStyle.Rise, FluidCapsuleShape, onOpen = onOpen),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            Modifier.padding(start = 4.dp).size(36.dp).fluidSharedElement(ElementToPageIds.Cover, ns)
              .clip(ContinuousCornerShape(FluidRadius.Small)).background(ElementToPageCoverColor),
          )
          Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Il brano", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("L'artista", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          PlayButton(playing, onPlay)
          FluidIcon(FluidIcons.SkipNext, contentDescription = "Brano successivo", modifier = Modifier.padding(end = 8.dp))
        }
      }
    },
  ) {
    item(key = "intestazione") { FluidSectionHeader(title = "La tua musica") }
    item(key = "sezioni") {
      FluidListGroup {
        sections.forEachIndexed { index, section ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = section, subtitle = "${12 + index * 7} elementi", onClick = {})
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Tira su il mini lettore, fermati, torna indietro e riprendi: la pagina segue il dito. Al tocco si apre da " +
          "solo. «In riproduzione» si chiude tirandola giù o col tasto in alto.",
      )
    }
  }
}

@Composable
private fun PlayButton(playing: Boolean, onPlay: () -> Unit, big: Boolean = false) {
  FluidIcon(
    imageVector = if (playing) FluidIcons.Pause else FluidIcons.PlayArrow,
    contentDescription = if (playing) "Pausa" else "Riproduci",
    size = if (big) 44.dp else 26.dp,
    modifier = Modifier.padding(horizontal = 6.dp).fluidPressable(onClick = onPlay, role = Role.Button),
  )
}

@Composable
internal fun PlayerPage(ns: FluidGlassNamespace, playing: Boolean, onPlay: () -> Unit, onClose: () -> Unit) {
  Column(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).statusBarsPadding()
      .navigationBarsPadding().padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    FluidIcon(
      FluidIcons.KeyboardArrowDown, contentDescription = "Chiudi «In riproduzione»",
      modifier = Modifier.padding(vertical = 12.dp).fluidPressable(onClick = onClose, role = Role.Button),
    )
    Box(
      Modifier.fillMaxWidth().aspectRatio(1f).testTag(ElementToPageTags.PlayerCover)
        .fluidSharedElement(ElementToPageIds.Cover, ns)
        .clip(ContinuousCornerShape(FluidRadius.Card)).background(ElementToPageCoverColor),
    )
    Spacer(Modifier.height(28.dp))
    Text("In riproduzione", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Il brano", style = MaterialTheme.typography.headlineSmall)
    Text("L'artista", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Box(
      Modifier.fillMaxWidth().height(4.dp).clip(FluidCapsuleShape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    ) {
      Box(Modifier.fillMaxWidth(0.38f).height(4.dp).clip(FluidCapsuleShape).background(MaterialTheme.colorScheme.onSurface))
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
      PlayButton(playing, onPlay, big = true)
      FluidIcon(FluidIcons.SkipNext, contentDescription = "Brano successivo", size = 36.dp)
    }
  }
}

// --- Gallery: la griglia e il visore (Zoom) ----------------------------------------------------------------------------

@Composable
internal fun PhotoGridPage(ns: FluidGlassNamespace, list: LazyListState, onBack: () -> Unit, onOpen: (Int) -> Unit) {
  val rows = (ElementToPagePhotoCount + ElementToPageGridColumns - 1) / ElementToPageGridColumns
  FluidScreen(title = "Foto", subtitle = "Tocca una foto; nel visore trascina in giù per tornare.", onBack = onBack, listState = list) {
    for (row in 0 until rows) {
      item(key = "riga-$row") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ElementToPageGridSpacing)) {
          for (column in 0 until ElementToPageGridColumns) {
            val index = row * ElementToPageGridColumns + column
            if (index >= ElementToPagePhotoCount) {
              Spacer(Modifier.weight(1f))
              continue
            }
            val shape = ContinuousCornerShape(FluidRadius.Small)
            // Il preset in testa (la sorgente consumata sparisce intera), poi l'immagine condivisa, poi forma e colore.
            Box(
              Modifier.weight(1f).aspectRatio(1f).testTag(ElementToPageTags.thumbnail(index))
                .fluidZoomSource(index, ns, FluidZoomStyle.Zoom, shape) { onOpen(index) }
                .fluidSharedElement(ElementToPageIds.photo(index), ns)
                .clip(shape).background(elementToPagePhotoColor(index)),
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun ViewerPage(ns: FluidGlassNamespace, photo: Int, onClose: () -> Unit) {
  // Il visore ha il fondo del tema sotto la foto, come Foto; la barra dell'host ha il titolo e l'indietro.
  FluidPageChrome(title = "Foto ${photo + 1} di $ElementToPagePhotoCount", onBack = onClose)
  Box(Modifier.fillMaxSize().testTag(ElementToPageTags.Viewer), contentAlignment = Alignment.Center) {
    Box(
      Modifier.fillMaxWidth().aspectRatio(1f).fluidSharedElement(ElementToPageIds.photo(photo), ns)
        .background(elementToPagePhotoColor(photo)),
    )
  }
}

// --- Weather: la pillola della località cresce (Cresce) ----------------------------------------------------------------

/** Le località finte: nome, gradi, cielo. */
internal data class ElementToPagePlace(val name: String, val degrees: Int, val sky: String)

internal val ElementToPagePlaces = listOf(
  ElementToPagePlace("Bologna", 18, "Parzialmente nuvoloso"),
  ElementToPagePlace("Milano", 15, "Pioggia leggera"),
  ElementToPagePlace("Napoli", 22, "Sereno"),
  ElementToPagePlace("Trento", 9, "Nebbia"),
  ElementToPagePlace("Cagliari", 21, "Vento"),
)

@Composable
internal fun WeatherPage(ns: FluidGlassNamespace, place: String, onBack: () -> Unit, onOpen: () -> Unit) {
  val now = ElementToPagePlaces.firstOrNull { it.name == place } ?: ElementToPagePlaces.first()
  val days = listOf("Oggi", "Domani", "Sabato", "Domenica", "Lunedì", "Martedì", "Mercoledì")
  FluidScreen(
    title = "Meteo",
    onBack = onBack,
    extraBottomPadding = 88.dp,
    overlay = { backdrop ->
      FluidGrowingPagePill(
        ElementToPageIds.Pill, ns, backdrop,
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp).height(52.dp)
          .testTag(ElementToPageTags.Pill).fluidZoomOpen(FluidZoomStyle.Grow, onOpen = onOpen),
      ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
          FluidIcon(FluidIcons.LocationOn, contentDescription = null, size = 20.dp)
          Text(now.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
          FluidIcon(FluidIcons.KeyboardArrowUp, contentDescription = null, size = 20.dp)
        }
      }
    },
  ) {
    item(key = "adesso") {
      Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(now.name, style = MaterialTheme.typography.titleLarge)
        Text("${now.degrees}°", style = MaterialTheme.typography.displayLarge)
        Text(now.sky, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    item(key = "giorni") {
      FluidListGroup {
        days.forEachIndexed { index, day ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = day, subtitle = now.sky, meta = "${now.degrees - 2 + index % 4}°", disclosure = false)
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(text = "Tocca la pillola o tirala su: il suo vetro diventa la pagina delle località e si fa opaco all'arrivo.")
    }
  }
}

@Composable
internal fun PlacesPage(selected: String, onPick: (String) -> Unit) {
  // I comandi stanno su un contenitore trasparente: si vedono già sul vetro, dopo il 70% della corsa.
  Box(Modifier.fillMaxSize()) {
    Column(
      Modifier.fillMaxWidth().fluidGrowingPageChrome().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
      Text("Le mie località", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
      FluidListGroup {
        ElementToPagePlaces.forEachIndexed { index, place ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = place.name, subtitle = place.sky, meta = "${place.degrees}°", selected = place.name == selected,
            disclosure = false, onClick = { onPick(place.name) },
          )
        }
      }
    }
  }
}

// --- Mail: «Scrivi» della barra in basso diventa il compositore (Dalla barra) -----------------------------------------

private val ElementToPageMessages = listOf(
  Triple("Giulia Rossi", "Cena di sabato", "Ci vediamo alle otto da noi? Porto il dolce."),
  Triple("Banca", "Estratto conto di novembre", "Il tuo estratto conto è disponibile nell'area riservata."),
  Triple("Marco", "Foto della gita", "Ti mando le foto del lago, sono venute benissimo."),
  Triple("Biblioteca", "Prestito in scadenza", "Il libro va restituito entro venerdì."),
  Triple("Sara", "Re: Progetto", "Ho guardato la bozza, due note veloci."),
  Triple("Palestra", "Nuovi orari", "Da lunedì apriamo alle sette."),
)

@Composable
internal fun InboxPage(onBack: () -> Unit, onCompose: () -> Unit) {
  FluidScreen(
    title = "Posta in arrivo",
    onBack = onBack,
    toolbar = {
      group("comandi", placement = FluidToolbarPlacement.BottomBar) {
        button("filtra", FluidIcons.FilterList, "Filtra", onClick = {})
        flexibleSpacer()
        button(ElementToPageIds.ComposeButton, FluidIcons.Edit, "Scrivi", onClick = onCompose)
      }
    },
  ) {
    item(key = "messaggi") {
      FluidListGroup {
        ElementToPageMessages.forEachIndexed { index, (from, subject, preview) ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = from, subtitle = "$subject · $preview", subtitleMaxLines = 2, onClick = {})
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(text = "«Scrivi», in basso a destra, diventa il compositore e ci torna quando lo chiudi.")
    }
  }
}

@Composable
internal fun ComposerPage(onClose: () -> Unit) {
  FluidScreen(
    title = "Nuovo messaggio",
    toolbar = {
      close(onClick = onClose)
      group("comandi", placement = FluidToolbarPlacement.BottomBar) {
        button("allega", FluidIcons.AttachFile, "Allega", onClick = {})
        flexibleSpacer()
        button("invia", FluidIcons.Send, "Invia", onClick = onClose)
      }
    },
  ) {
    item(key = "campi") {
      // I campi si vedono già sul vetro che cresce dal tasto, dopo il 70% della corsa; il fondo arriva alla posa.
      Column(Modifier.fluidGrowingPageChrome()) {
        FluidListGroup {
          FluidListRow(title = "A:", subtitle = "Giulia Rossi", disclosure = false)
          FluidListDivider()
          FluidListRow(title = "Oggetto:", subtitle = "Cena di sabato", disclosure = false)
        }
        Text(
          "Ciao Giulia,\nsabato va benissimo. Porto io il vino.\n\nA presto",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
        )
      }
    }
  }
}
