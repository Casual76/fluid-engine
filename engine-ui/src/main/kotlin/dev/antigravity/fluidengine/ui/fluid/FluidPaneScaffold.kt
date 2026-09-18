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
  /**
   * La finestra tiene l'elenco e la cosa aperta come due posti distinti.
   *
   * E' una **capacita'**, non cio' che si vede: dice all'app che aprire una nota non deve impilarla
   * sopra l'elenco ma metterla nell'altro posto, anche quando in quel momento l'elenco e' fuori
   * scena. Senza questa distinzione, un pannello nascosto diventerebbe una rotta persa.
   */
  val splits: Boolean = false,
) {
  val showSide: Boolean get() = FluidPaneRole.Side in panes
  val showList: Boolean get() = FluidPaneRole.List in panes
  val showDetail: Boolean get() = FluidPaneRole.Detail in panes
}

/**
 * Quali pannelli stanno in [available] dp, e quanto larghi.
 *
 * **Due pannelli al massimo, e la barra laterale e' uno dei due.** Tre cose aperte insieme — le
 * materie, l'elenco e la nota — si leggono come tre pagine appiccicate, e con le impostazioni
 * diventano tre livelli della stessa gerarchia visibili tutti in una volta: l'indice, la sezione, e
 * un menu che con quella sezione non c'entra niente. Quindi la barra laterale resta sempre, perche'
 * e' da li' che si cambia materia, e accanto sta **o** l'elenco **o** quello che si e' aperto.
 *
 * Tre regimi. Sotto [FluidPaneDefaults.MediumBreakpoint] un pannello solo, e la barra in basso e'
 * affare dell'app. Fino a [FluidPaneDefaults.ExpandedBreakpoint] il rail di fianco a un pannello.
 * Oltre, la barra laterale piu' uno — e se quel pannello scenderebbe sotto [FluidPaneSizes.detailMin],
 * la barra laterale si sfila e torna il rail: quello che si legge non si comprime mai.
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
    val splits = available - sizes.list >= sizes.detailMin
    if (hasSide && available - sizes.side >= sizes.detailMin) {
      val pane = available - sizes.side
      return FluidPaneLayout(
        panes = listOf(FluidPaneRole.Side) + single,
        showRail = false,
        railWidth = 0.dp,
        sideWidth = sizes.side,
        listWidth = pane,
        detailWidth = pane,
        splits = splits,
      )
    }
    val pane = available - railWidth
    return FluidPaneLayout(single, showRail = hasRail, railWidth, 0.dp, pane, pane, splits = splits)
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
    // Fuori scena non vuol dire smontato. Quando la finestra tiene i due posti distinti
    // ([FluidPaneLayout.splits]) il pannello che non si vede resta **composto**, largo zero: il suo
    // navigation host conserva il grafo e lo stack, e mostrarlo di nuovo e' un cambio di misura.
    // Smontarlo significherebbe un controller senza grafo, e una rotta verso di lui che esplode.
    if (layout.showList || layout.splits) {
      FluidPane(
        role = FluidPaneRole.List,
        modifier = if (layout.showList) Modifier.weight(1f) else Modifier.width(0.dp),
        front = layout.showList,
        content = list,
      )
    }
    if (layout.showDetail || layout.splits) {
      FluidPane(
        role = FluidPaneRole.Detail,
        modifier = if (layout.showDetail) Modifier.weight(1f) else Modifier.width(0.dp),
        front = layout.showDetail,
        content = detail,
      )
    }
  }
}

@Composable
private fun RowScope.FluidPane(
  role: FluidPaneRole,
  modifier: Modifier,
  content: @Composable () -> Unit,
  /**
   * Se questo pannello e' in scena.
   *
   * Un pannello composto ma largo zero non e' davanti a niente, e dirlo conta: e' cosi' che la
   * chrome sa quale fondale rifrangere, e che una schermata fuori scena non si prende il vetro.
   */
  front: Boolean = true,
) {
  val isFront = remember { mutableStateOf(front) }
  isFront.value = front
  Box(modifier = modifier.fillMaxHeight()) {
    CompositionLocalProvider(
      LocalFluidPaneRole provides role,
      LocalFluidRouteFront provides isFront,
    ) {
      content()
    }
  }
}
