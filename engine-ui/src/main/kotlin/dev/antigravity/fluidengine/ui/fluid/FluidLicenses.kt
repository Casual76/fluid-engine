package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineBuild
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * A third-party work the engine ships inside the app binary.
 *
 * Attribution is a licence condition for most of what is listed here, not a courtesy: Apache-2.0
 * asks for the notice to travel with the distribution, and the SIL OFL asks for the copyright line.
 * Putting the list in the engine rather than in each app is what stops it from being right in one
 * app and three versions stale in the other four.
 */
@Immutable
data class FluidLicense(
  val name: String,
  val author: String,
  val licence: String,
  val url: String,
  /** One line on what it actually does in the app, so the list is readable and not just legal. */
  val role: String,
)

/**
 * Everything `engine-ui` carries that somebody else wrote.
 *
 * Keep it in step with the files in `LICENSES/`: a notice that exists only in a Markdown file in a
 * repository has not accompanied the distribution, which is the thing the licence asked for.
 */
val FluidEngineLicenses: List<FluidLicense> = listOf(
  FluidLicense(
    name = "AndroidLiquidGlass (backdrop)",
    author = "Kyant — Copyright 2025",
    licence = "Apache License 2.0",
    url = "https://github.com/Kyant0/AndroidLiquidGlass",
    role = "Il vetro: cattura dello sfondo, lente rifrattiva, bordo speculare, spessore.",
  ),
  FluidLicense(
    name = "Inter",
    author = "Rasmus Andersson",
    licence = "SIL Open Font License 1.1",
    url = "https://github.com/rsms/inter",
    role = "La famiglia tipografica di tutta l'interfaccia.",
  ),
  FluidLicense(
    name = "Square",
    author = "Lelonio",
    licence = "GPL-3.0 — nessun codice ripreso",
    url = "https://github.com/Lelonio/Square",
    role = "Da qui abbiamo imparato come si porta davvero il vetro su Android.",
  ),
)

/**
 * The credits, as rows a settings page can drop into its own list.
 *
 * A `LazyListScope` extension rather than a screen, because every app already has an "informazioni"
 * page with its own header, its own version row and its own order, and a second screen would be one
 * more place to navigate to and forget about.
 */
fun LazyListScope.fluidLicensesSection(
  title: String = "Licenze e crediti",
  footnote: String? = "Il Fluid Engine ${EngineBuild.VERSION} include queste opere di terze parti. " +
    "Le licenze complete sono nel repository dell'engine, in LICENSES/.",
  licences: List<FluidLicense> = FluidEngineLicenses,
) {
  item(key = "fluid-licences-header") {
    FluidSectionHeader(title = title)
  }
  item(key = "fluid-licences-body") {
    FluidLicenseGroup(licences = licences)
  }
  if (footnote != null) {
    item(key = "fluid-licences-footnote") {
      FluidSectionFootnote(text = footnote)
    }
  }
}

/** The same list outside a lazy layout, for a page built out of a `Column`. */
@Composable
fun FluidLicenseGroup(
  licences: List<FluidLicense> = FluidEngineLicenses,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  FluidListGroup(modifier = modifier) {
    licences.forEachIndexed { index, licence ->
      if (index > 0) FluidListDivider()
      FluidListRow(
        title = licence.name,
        subtitle = "${licence.author} · ${licence.licence}",
        // Nessun "Apri" scritto: la riga ha gia' il chevron, e una riga di lista che dice due volte
        // la stessa cosa e' la cosa piu' facile da aggiungere e la piu' difficile da giustificare.
        onClick = { uriHandler.openUri(licence.url) },
      )
    }
  }
}

/**
 * The long form: what each work does, not only who wrote it.
 *
 * For an app that wants a real credits page rather than a compliance list.
 */
@Composable
fun FluidLicenseDetails(
  licences: List<FluidLicense> = FluidEngineLicenses,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    licences.forEach { licence ->
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = licence.name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = licence.role,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "${licence.author} · ${licence.licence}",
          modifier = Modifier.padding(top = 2.dp),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
