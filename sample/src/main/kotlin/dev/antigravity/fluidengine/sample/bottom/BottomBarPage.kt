package dev.antigravity.fluidengine.sample.bottom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.material.LocalGalleryPageOpener
import dev.antigravity.fluidengine.sample.toolbar.ToolbarDemoPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * «Barre in basso, ricerca e schede»: tutto F4B in una pagina, in quattro capitoli.
 *
 * 1. **Barra in basso e capsula.** Le azioni della pagina stanno in basso, sul piano dell'host: fra «Raccolta» e
 *    «Foto» il più resta fermo e gli altri entrano ed escono; il più si stacca dal gruppo e ci torna; la capsula
 *    libera cambia larghezza nella pagina.
 * 2. **Ricerca.** Una sola dichiarazione che cambia sede ([BottomSearchMode]): in basso con gli ambiti, automatica,
 *    minimizzata, in alto, in alto fino alla tastiera, e il campo di inserimento.
 * 3. **Tab bar** ([TabBarStage]): la piega col tasto e allo scorrimento, il tondo della ricerca, il lettore e la
 *    scheda prominente.
 * 4. **Tab bar in alto e barra laterale**: apre «Navigazione adattiva», che sta da sola perché vuole la finestra
 *    intera ([TabletNavigationPage]).
 *
 * Il vetro in basso e le sue coordinate appartengono all'host. Le righe del primo capitolo e quella della ricerca in
 * basso hanno le frasi che le riprese di F4B.1 e F4B.2 toccano: non vanno cambiate senza cambiare quelle.
 */
@Composable
internal fun BottomBarPage(onClose: () -> Unit) {
  var photo by rememberSaveable { mutableStateOf(false) }
  var separate by rememberSaveable { mutableStateOf(false) }
  var favourite by rememberSaveable { mutableStateOf(false) }
  var shared by rememberSaveable { mutableStateOf(false) }
  var expanded by rememberSaveable { mutableStateOf(false) }
  var fewer by rememberSaveable { mutableStateOf(false) }
  var searchMode by rememberSaveable { mutableStateOf<BottomSearchMode?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var favouritesOnly by rememberSaveable { mutableStateOf(false) }
  val openPage = LocalGalleryPageOpener.current
  val back = { if (photo) photo = false else onClose() }
  ToolbarDemoPolicy {
    FluidPageHost(stack = if (photo) listOf("raccolta", "foto") else listOf("raccolta"),
      onBack = back, modifier = Modifier.fillMaxSize()) { page ->
      val detail = page == "foto"
      FluidScreen(title = if (detail) "Foto" else "Raccolta", onBack = back, toolbar = {
        val mode = searchMode
        if (mode != null && !detail) search(mode.spec(query, { query = it },
          scopes = { Text(if (favouritesOnly) "Preferite" else "Tutte", Modifier.clickable { favouritesOnly = !favouritesOnly }) },
          suggestions = { if (query.isNotBlank()) Text("Cerca «$query» nella raccolta") }))
        group("comandi", placement = FluidToolbarPlacement.BottomBar) {
          button("elenco", if (detail) FluidIcons.Search else FluidIcons.FormatListBulleted,
            if (detail) "Torna alla raccolta" else "Mostra foto", onClick = { photo = !detail })
          flexibleSpacer()
          if (!detail && !fewer) {
            button("cuore", FluidIcons.Favorite, "Preferito", onClick = { favourite = !favourite }, selected = favourite)
            button("condividi", FluidIcons.IosShare, "Condividi", onClick = { shared = !shared }, selected = shared)
            if (separate) spacer()
          }
          button("aggiungi", FluidIcons.Add, "Cambia gruppo", onClick = { separate = !separate; photo = false })
        }
      }) {
        if (!detail) item(key = "capitolo-barra") {
          Chapter("Barra in basso e capsula", "Le azioni della pagina in basso: cambiano con la pagina, si staccano e si riuniscono.")
        }
        item(key = "foto") {
          FluidListRow(title = if (detail) "Torna alla raccolta" else "Apri la foto", subtitle = "Cambia pagina", onClick = { photo = !detail })
        }
        if (!detail) item(key = "separa") {
          FluidListRow(title = if (separate) "Riunisci i comandi" else "Separa il più", subtitle = "Azioni della barra in basso", onClick = { separate = !separate })
        }
        item(key = "capsula-riga") {
          FluidListRow(title = if (expanded) "Riduci la capsula" else "Espandi la capsula", subtitle = "Azioni nella pagina",
            onClick = { expanded = !expanded })
        }
        item(key = "capsula") {
          FluidFloatingCapsule {
            FluidCapsuleAction(if (expanded) FluidIcons.Check else FluidIcons.Edit, "Modifica la capsula",
              onClick = { expanded = !expanded })
            if (expanded) FluidCapsuleAction(FluidIcons.Favorite, "Contrassegna", onClick = { favourite = !favourite },
              selected = favourite)
          }
        }
        if (detail) return@FluidScreen
        item(key = "capitolo-ricerca") {
          Chapter("Ricerca", "Il tasto diventa il campo allargandosi e i vicini gli fanno posto. Una sede alla volta.")
        }
        BottomSearchMode.entries.forEach { mode ->
          item(key = "ricerca-${mode.name}") {
            val active = searchMode == mode
            FluidListRow(title = if (active) mode.activeTitle else mode.title,
              subtitle = if (active && mode != BottomSearchMode.Bottom) "In uso · ${mode.detail}" else mode.detail,
              onClick = { searchMode = if (active) null else mode })
          }
        }
        item(key = "vicini") {
          FluidListRow(title = if (fewer) "Rimetti cuore e condividi" else "Lascia due comandi",
            subtitle = "Con due vicini la ricerca automatica parte già campo", onClick = { fewer = !fewer })
        }
        item(key = "capitolo-tabbar") {
          Chapter("Tab bar", "La piega col tasto o scorrendo, il tondo della ricerca, il lettore e la scheda prominente.")
        }
        item(key = "tabbar") { TabBarStage() }
        item(key = "capitolo-tablet") {
          Chapter("Tab bar in alto e barra laterale",
            "Sotto 600 dp la tab bar, fino a 999 dp il rail, da 1000 dp la tab bar in alto col tasto che la apre in barra laterale.")
        }
        item(key = "tablet") {
          FluidListRow(title = "Apri la navigazione adattiva", subtitle = "«Atlante cromatico», a finestra intera",
            onClick = { openPage?.invoke(GalleryPage.TabletNavigation) })
        }
      }
    }
  }
}

/** Il titolo di un capitolo, come nelle gallerie dei menu e delle presentazioni. */
@Composable
private fun Chapter(title: String, detail: String) {
  Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
