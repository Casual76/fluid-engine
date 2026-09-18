package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront

/** Il ruolo di un pannello: chi elenca, chi mostra, chi sta di lato. */
enum class FluidPaneRole { Side, List, Detail }

/** Dentro quale pannello si sta componendo, o null fuori da un [FluidPaneScaffold]. */
val LocalFluidPaneRole: ProvidableCompositionLocal<FluidPaneRole?> = compositionLocalOf { null }

object FluidPaneDefaults {
  /** Da qui in su c'e' posto per il rail al fianco di una pagina. */
  val MediumBreakpoint: Dp = 600.dp

  /** Da qui in su c'e' posto per due pannelli affiancati, e per la barra laterale. */
  val ExpandedBreakpoint: Dp = 1000.dp
}

/** Le misure dei pannelli: quelle fisse, e il minimo sotto cui il dettaglio non si comprime. */
@Immutable
data class FluidPaneSizes(
  val side: Dp = 280.dp,
  val list: Dp = 380.dp,
  /** Sotto questa larghezza il dettaglio non e' piu' un dettaglio: si sfila prima la barra, poi la lista. */
  val detailMin: Dp = 420.dp,
  val rail: Dp = FluidTabBarDefaults.RailWidth + FluidTabBarDefaults.HorizontalMargin * 2,
)

/**
 * Cosa sta in scena e quanto e' largo. Solo i pannelli elencati in [panes] vengono emessi: un
 * pannello nascosto non e' un pannello largo zero, e' un pannello che non c'e', cosi' la sua
 * `FluidScreen` non registra un fondale che nessuno vede.
 */
@Immutable
data class FluidPaneLayout(
  val panes: List<FluidPaneRole>,
  val showRail: Boolean,
  val railWidth: Dp,
  val sideWidth: Dp,
  val listWidth: Dp,
  val detailWidth: Dp,
) {
  val showSide: Boolean get() = FluidPaneRole.Side in panes
  val showList: Boolean get() = FluidPaneRole.List in panes
  val showDetail: Boolean get() = FluidPaneRole.Detail in panes

  /** Lista e dettaglio insieme: la navigazione ha due padroni di casa. */
  val twoPane: Boolean get() = showList && showDetail
}

/**
 * Quali pannelli stanno in [available] dp, e quanto larghi.
 *
 * Tre regimi. Sotto [FluidPaneDefaults.MediumBreakpoint] un pannello solo, e la barra in basso
 * e' affare dell'app. Fino a [FluidPaneDefaults.ExpandedBreakpoint] il rail di fianco a un
 * pannello. Oltre, lista e dettaglio affiancati con la barra laterale al posto del rail — e se il
 * dettaglio scenderebbe sotto [FluidPaneSizes.detailMin], si sfila prima la barra laterale (torna
 * il rail), poi la lista. Il dettaglio non si comprime mai: e' quello che si legge.
 *
 * [showDetail] conta solo quando c'e' posto per un pannello: dice quale dei due.
 */
fun fluidPaneLayout(
  available: Dp,
  sizes: FluidPaneSizes = FluidPaneSizes(),
  hasSide: Boolean = false,
  hasRail: Boolean = false,
  showDetail: Boolean = false,
): FluidPaneLayout {
  val single = listOf(if (showDetail) FluidPaneRole.Detail else FluidPaneRole.List)
  if (!available.isFinite || available < FluidPaneDefaults.MediumBreakpoint) {
    return FluidPaneLayout(single, showRail = false, 0.dp, 0.dp, available, available)
  }
  val railWidth = if (hasRail) sizes.rail else 0.dp
  if (available >= FluidPaneDefaults.ExpandedBreakpoint) {
    if (hasSide && available - sizes.side - sizes.list >= sizes.detailMin) {
      return FluidPaneLayout(
        panes = listOf(FluidPaneRole.Side, FluidPaneRole.List, FluidPaneRole.Detail),
        showRail = false,
        railWidth = 0.dp,
        sideWidth = sizes.side,
        listWidth = sizes.list,
        detailWidth = available - sizes.side - sizes.list,
      )
    }
    if (available - railWidth - sizes.list >= sizes.detailMin) {
      return FluidPaneLayout(
        panes = listOf(FluidPaneRole.List, FluidPaneRole.Detail),
        showRail = hasRail,
        railWidth = railWidth,
        sideWidth = 0.dp,
        listWidth = sizes.list,
        detailWidth = available - railWidth - sizes.list,
      )
    }
  }
  val pane = available - railWidth
  return FluidPaneLayout(single, showRail = hasRail, railWidth, 0.dp, pane, pane)
}

/**
 * I pannelli di una pagina larga, affiancati.
 *
 * Emette **solo** quello che [layout] mette in scena, e per ogni pannello dichiara il suo ruolo
 * ([LocalFluidPaneRole]) e che e' davanti ([LocalFluidRouteFront]): due pannelli sullo schermo sono
 * davanti entrambi, e senza dirlo il registro della chrome eleggerebbe l'ultimo registrato in un
 * momento qualsiasi. Cosi' l'ultimo registrato fra i davanti e' sempre il dettaglio, che e' quello
 * che la chrome deve rifrangere.
 *
 * I controller di navigazione stanno fuori, nella shell: un `NavHost` che sparisce dalla
 * composizione perche' il suo pannello non c'e' conserva lo stack, e quando il pannello torna
 * riprende da dov'era.
 */
@Composable
fun FluidPaneScaffold(
  layout: FluidPaneLayout,
  list: @Composable () -> Unit,
  detail: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  side: (@Composable () -> Unit)? = null,
  rail: (@Composable () -> Unit)? = null,
) {
  Row(modifier = modifier.fillMaxSize()) {
    if (layout.showRail && rail != null) {
      Box(modifier = Modifier.width(layout.railWidth).fillMaxHeight()) { rail() }
    }
    if (layout.showSide && side != null) {
      FluidPane(role = FluidPaneRole.Side, modifier = Modifier.width(layout.sideWidth), content = side)
    }
    if (layout.showList) {
      FluidPane(
        role = FluidPaneRole.List,
        modifier = if (layout.showDetail) Modifier.width(layout.listWidth) else Modifier.weight(1f),
        content = list,
      )
    }
    if (layout.showDetail) {
      FluidPane(role = FluidPaneRole.Detail, modifier = Modifier.weight(1f), content = detail)
    }
  }
}

@Composable
private fun RowScope.FluidPane(
  role: FluidPaneRole,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  val front = remember { mutableStateOf(true) }
  Box(modifier = modifier.fillMaxHeight()) {
    CompositionLocalProvider(
      LocalFluidPaneRole provides role,
      LocalFluidRouteFront provides front,
    ) {
      content()
    }
  }
}
