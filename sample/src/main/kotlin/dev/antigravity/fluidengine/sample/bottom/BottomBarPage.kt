package dev.antigravity.fluidengine.sample.bottom

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.sample.toolbar.ToolbarDemoPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.page.FluidPageHost

/** La pagina cambia i comandi. Il vetro in basso e le sue coordinate appartengono all'host. */
@Composable
internal fun BottomBarPage(onClose: () -> Unit) {
  var photo by rememberSaveable { mutableStateOf(false) }
  var separate by rememberSaveable { mutableStateOf(false) }
  var favourite by rememberSaveable { mutableStateOf(false) }
  var shared by rememberSaveable { mutableStateOf(false) }
  var expanded by rememberSaveable { mutableStateOf(false) }
  val back = { if (photo) photo = false else onClose() }
  ToolbarDemoPolicy {
    FluidPageHost(stack = if (photo) listOf("raccolta", "foto") else listOf("raccolta"),
      onBack = back, modifier = Modifier.fillMaxSize()) { page ->
      val detail = page == "foto"
      FluidScreen(title = if (detail) "Foto" else "Raccolta", onBack = back, toolbar = {
        group("comandi", placement = FluidToolbarPlacement.BottomBar) {
          button("elenco", if (detail) FluidIcons.Search else FluidIcons.FormatListBulleted,
            if (detail) "Torna alla raccolta" else "Mostra foto", onClick = { photo = !detail })
          flexibleSpacer()
          if (!detail) {
            button("cuore", FluidIcons.Favorite, "Preferito", onClick = { favourite = !favourite }, selected = favourite)
            button("condividi", FluidIcons.IosShare, "Condividi", onClick = { shared = !shared }, selected = shared)
            if (separate) spacer()
          }
          button("aggiungi", FluidIcons.Add, "Cambia gruppo", onClick = { separate = !separate; photo = false })
        }
      }) {
        item { FluidListRow(title = if (detail) "Torna alla raccolta" else "Apri la foto", subtitle = "Cambia pagina", onClick = { photo = !detail }) }
        if (!detail) item {
          FluidListRow(title = if (separate) "Riunisci i comandi" else "Separa il più", subtitle = "Azioni della barra in basso", onClick = { separate = !separate })
        }
        item {
          FluidListRow(title = if (expanded) "Riduci la capsula" else "Espandi la capsula", subtitle = "Azioni nella pagina",
            onClick = { expanded = !expanded })
        }
        item {
          FluidFloatingCapsule {
            FluidCapsuleAction(if (expanded) FluidIcons.Check else FluidIcons.Edit, "Modifica la capsula",
              onClick = { expanded = !expanded })
            if (expanded) FluidCapsuleAction(FluidIcons.Favorite, "Contrassegna", onClick = { favourite = !favourite },
              selected = favourite)
          }
        }
      }
    }
  }
}
