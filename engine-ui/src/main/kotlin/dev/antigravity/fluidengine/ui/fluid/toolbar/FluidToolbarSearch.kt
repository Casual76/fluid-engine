package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec

/**
 * I posti in cui una schermata offre la ricerca alla barra. [FluidScreen][dev.antigravity.fluidengine.ui.fluid.FluidScreen]
 * li chiama **tutti**, sempre; è [FluidToolbarSearchSlot] a decidere in quale il campo si posa e come. Così F4B.2 può
 * spostare il campo (nella barra in basso, da tasto a campo, sopra la tastiera) riscrivendo **questo file**, senza
 * riaprire `FluidScreen.kt` né la DSL (piano, F4A, «Ricerca: chi possiede cosa»).
 */
internal enum class FluidToolbarSearchSite {
  /** Il primo elemento della lista, sotto il titolo grande: dove le app lo mettono oggi. */
  UnderTitle,

  /** La riga dei tasti in alto. */
  TopBar,

  /** Sopra il contenuto, in basso (la capsula della ricerca di F4B). */
  Bottom,
}

/** Se [spec] si posa in [site]. In F4A il campo sta solo sotto il titolo, qualunque posizione la pagina chieda. */
internal fun fluidToolbarSearchSits(spec: FluidSearchSpec, site: FluidToolbarSearchSite): Boolean =
  site == FluidToolbarSearchSite.UnderTitle

/** La chiave dell'elemento della lista che porta il campo. */
internal const val FluidToolbarSearchItemKey = "fluid:toolbar-search"

/**
 * Il punto unico della ricerca di una barra (F4A.2): il parametro `search` di `FluidScreen` e `search(spec)` della DSL
 * arrivano qui, e solo qui. In F4A posa il [FluidSearchField] di oggi **così com'è**: sempre aperto, oppure richiudibile
 * in un tondo con la lente se la pagina chiede [FluidSearchBehavior.CollapseToButton] e possiede l'apertura
 * ([FluidSearchSpec.onActiveChange]); il fuoco automatico è spento, perché un campo che sta in pagina e alza la tastiera
 * da solo coprirebbe i risultati. Posizioni, passaggio da tasto a campo, vicini e tastiera sono di F4B.2, che riscrive
 * questo file.
 */
@Composable
internal fun FluidToolbarSearchSlot(
  spec: FluidSearchSpec,
  site: FluidToolbarSearchSite,
  modifier: Modifier = Modifier,
) {
  if (!fluidToolbarSearchSits(spec, site)) return
  val collapsible = spec.behavior == FluidSearchBehavior.CollapseToButton && spec.onActiveChange != null
  FluidSearchField(
    query = spec.query,
    onQueryChange = spec.onQueryChange,
    modifier = modifier.fillMaxWidth(),
    placeholder = spec.prompt,
    onSearch = spec.onSearch,
    expanded = if (collapsible) spec.active else true,
    onExpandedChange = if (collapsible) spec.onActiveChange else null,
    autoFocus = false,
  )
}

/**
 * Il campo come elemento della lista di una schermata, sotto il titolo grande: la forma di [FluidToolbarSearchSlot] per
 * [FluidToolbarSearchSite.UnderTitle]. Niente se [spec] è null o se il campo sta altrove.
 */
internal fun LazyListScope.fluidToolbarSearchItem(spec: FluidSearchSpec?) {
  if (spec == null || !fluidToolbarSearchSits(spec, FluidToolbarSearchSite.UnderTitle)) return
  item(key = FluidToolbarSearchItemKey, contentType = FluidToolbarSearchItemKey) {
    FluidToolbarSearchSlot(spec, FluidToolbarSearchSite.UnderTitle)
  }
}
