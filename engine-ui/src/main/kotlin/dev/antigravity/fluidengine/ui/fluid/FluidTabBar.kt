package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop

@Immutable
data class FluidTabItem(
  val route: String,
  val label: String,
  val icon: ImageVector,
)

/**
 * How much rim and shadow a tab indicator keeps when nobody is holding it.
 *
 * The vendored bar's own numbers — half a rim, a third of a shadow — kept because they are the
 * answer to a question that was asked against a real screen: below this the indicator stops having
 * an edge of its own and starts borrowing its shape from whatever the bar happens to be over.
 */
internal const val FluidTabIndicatorEdgeFloor = 0.5f

/**
 * Le misure della barra di prima e del rail.
 *
 * [Height] e [RailWidth] sono ancora quelle di [FluidTabRail]: 64 dp per scheda, 84 dp di larghezza.
 * Per la barra in basso i numeri giusti sono quelli di [FluidFloatingTabBarDefaults], e
 * [ContentInset] di qui ne riserva 4 dp in più di quanti la barra nuova ne occupi.
 */
object FluidTabBarDefaults {
  val Height = 64.dp
  val HorizontalMargin = 14.dp
  /** Above the system's gesture handle, not against it. See [FluidFoldingTabBarDefaults]. */
  val BottomMargin = 16.dp
  val RailWidth = 84.dp

  /** Vertical space a screen must leave free so its content clears the floating bar. */
  val ContentInset = Height + BottomMargin

  /** Gap between the bar's edge and the indicator inside it. */
  internal val IndicatorInset = 4.dp
}

@Immutable
internal data class TabIndicatorTiming(
  val leadingDurationMillis: Int,
  val trailingDurationMillis: Int,
)

internal fun tabIndicatorTiming(reducedMotion: Boolean): TabIndicatorTiming =
  if (reducedMotion) {
    TabIndicatorTiming(leadingDurationMillis = 0, trailingDurationMillis = 0)
  } else {
    // Kept for the rail and for reduced motion, where the indicator is a position rather than an
    // object with mass. The horizontal bar's own indicator is spring-driven and does not read this.
    TabIndicatorTiming(leadingDurationMillis = 190, trailingDurationMillis = 250)
  }

/**
 * La barra di prima, sotto il nome che le app già chiamano.
 *
 * Adesso è [FluidFloatingTabBar] nella sua forma a elenco: chi la chiamava prende il vetro vero senza
 * toccare una riga — la rifrazione sotto la pillola, la riga tinta nascosta che la pillola campiona
 * attraverso se stessa, l'allungamento che segue il dito trascinato. Qui dentro non si decide più
 * niente su come appare la barra: è il motivo per cui un'app dipende da un engine invece di
 * copiarne uno, e un engine che migliora un componente lasciandone indietro il nome non ha
 * migliorato niente per chi lo usava.
 *
 * Deprecata e non cancellata, perché cancellarla farebbe pagare a ogni app un miglioramento che non
 * ha chiesto, con una scadenza che non ha scelto (la rimozione è prevista per la 3.0). Nel
 * frattempo funziona, *è* la barra nuova, e la deprecazione dice dove andare quando c'è un motivo
 * per andarci.
 *
 * **Cosa si vede cambiare**, letto da come le app la posano:
 *
 *  - **La larghezza.** Quella di prima riempiva la larghezza che le davi. La nuova è una capsula
 *    grande quanto le sue schede (fino a [FluidFloatingTabBarDefaults.TabWidth] ciascuna, di meno
 *    se non ci stanno) e sta al centro della larghezza offerta: con tre schede su un telefono è
 *    più stretta di prima, non una barra da lato a lato.
 *  - **L'altezza.** [FluidFloatingTabBarDefaults.OpenHeight] (60 dp) invece di 64. È un pavimento:
 *    col carattere ingrandito la barra cresce invece di tagliare l'etichetta. Chi riserva lo spazio
 *    con [FluidTabBarDefaults.ContentInset] ne lascia 4 dp in più del necessario;
 *    [FluidFloatingTabBarDefaults.ContentInset] è il numero giusto.
 *  - **I margini restano di chi la posa.** Né la barra di prima né la nuova portano dentro di sé il
 *    margine laterale, quello in fondo o lo spazio della barra di sistema: `navigationBarsPadding()`
 *    e [FluidFloatingTabBarDefaults.HorizontalMargin] / [FluidFloatingTabBarDefaults.BottomMargin]
 *    li mette il contenitore, e restano gli stessi. Un'app che la posava senza margini la vedrà
 *    ancora senza margini, ma capsula e centrata invece che a filo.
 *  - **Non si ripiega.** La barra nuova sa ridursi a una pillola mentre la pagina scorre; qui
 *    nessuno la guida e resta aperta, come prima. Per farla ripiegare serve [FluidFloatingTabBar]
 *    chiamata direttamente, con una [rememberFluidFloatingTabBarScrollConnection] attaccata alla
 *    pagina con `nestedScroll` — che è anche ciò che le permette di tenere fermo il vetro mentre la
 *    pagina scorre.
 *  - **Cosa dice a chi non la vede.** Ogni scheda dice ancora se è quella scelta (`selected`). Non
 *    portano più il ruolo `Tab` né il tag di test `top_level_<rotta>`: sono proprietà del nodo
 *    della scheda, che la barra nuova costruisce da sé, e una proprietà messa su un discendente non
 *    ci sale. Un test che cercava il tag cerca la scheda dalla sua etichetta (`hasText`); [FluidTabRail]
 *    li tiene entrambi.
 *
 * Il rail per gli schermi larghi, [FluidTabRail], non ha un equivalente e resta.
 */
@Deprecated(
  message = "È FluidFloatingTabBar con la lista di schede, e ne ha il vetro: usa quella. Cambia la " +
    "forma (una capsula grande quanto le schede, al centro, non una barra da lato a lato) e non si " +
    "ripiega finché non le dai una scroll connection; i margini restano di chi la posa.",
  replaceWith = ReplaceWith(
    "FluidFloatingTabBar(items = items, selectedRoute = selectedRoute, onSelect = onSelect, " +
      "scrollConnection = rememberFluidFloatingTabBarScrollConnection(), backdrop = backdrop, " +
      "modifier = modifier, onReselect = onReselect)",
    "dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar",
    "dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection",
  ),
)
@Composable
fun FluidTabBar(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  // Una connessione che nessuna pagina porta con sé: la barra non ha niente da cui lasciarsi
  // ripiegare, e `Never` lo dice invece di lasciarlo dedurre.
  val scroll = rememberFluidFloatingTabBarScrollConnection(
    inlineBehavior = FluidFloatingTabBarInlineBehavior.Never,
  )
  FluidFloatingTabBar(
    items = items,
    selectedRoute = selectedRoute,
    onSelect = onSelect,
    scrollConnection = scroll,
    backdrop = backdrop,
    modifier = modifier,
    onReselect = onReselect,
  )
}

/**
 * Dice a chi non vede la barra se questa scheda è quella scelta.
 *
 * Per la barra a elenco di [FluidFloatingTabBar]: nasce da un DSL di composable e non ha il concetto
 * di selezione, quindi chi la riempie da una lista di [FluidTabItem] lo rimette. Va su un
 * discendente della scheda e non sulla scheda, che la barra costruisce al suo interno ed è un
 * `clickable`: unisce la semantica dei figli alla propria.
 *
 * Solo `selected`, e non per scelta: provato su un albero vero, `selected` sale dal figlio al nodo
 * unito, mentre `Role` e `testTag` no. Il ruolo `Tab` e il tag `top_level_<rotta>` restano quindi
 * delle schede dove la scheda è il nodo stesso, cioè quelle del rail ([FluidTabItemContent]).
 */
internal fun Modifier.fluidTabSelected(selected: Boolean): Modifier = this.semantics {
  this.selected = selected
}

/**
 * Where the lens sits, in the padded space inside the bar.
 *
 * Right to left is not a mirrored layout here but a mirrored *coordinate*: the indicator is placed
 * by a graphics-layer translation rather than by the layout system, so nothing flips it for us and
 * the arithmetic has to. Extracted because an RTL error in a draw-time expression is invisible until
 * someone actually runs the app in Arabic or Hebrew.
 */
internal fun tabIndicatorTranslation(
  value: Float,
  tabWidth: Float,
  barOffset: Float,
  containerWidth: Float,
  isLtr: Boolean,
): Float {
  val travel = value * tabWidth + barOffset
  return if (isLtr) travel else containerWidth - travel - tabWidth
}

/** Applied by the invisible accent copy so a tab can drop its own colour animation there. */
private val LocalFluidTabTint = compositionLocalOf<Color?> { null }

@Composable
private fun FluidTabItemContent(
  item: FluidTabItem,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // The accent copy is tinted wholesale by a colour filter, so its own colour animation would only
  // fight it. Everything there resolves to one flat colour and the filter does the rest.
  val tinted = LocalFluidTabTint.current != null
  val contentColor by animateColorAsState(
    targetValue = when {
      tinted -> MaterialTheme.colorScheme.onSurface
      selected -> MaterialTheme.colorScheme.onSurface
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    },
    animationSpec = if (reducedMotion) snap() else FluidMotion.color(200),
    label = "tab tint",
  )

  val density = LocalDensity.current
  val selectedLiftPx = with(density) { -1.5.dp.toPx() }
  val settle by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.fluid(),
    label = "tab settle",
  )

  Box(
    modifier = modifier
      .fillMaxHeight()
      .testTag("top_level_${item.route}")
      .semantics {
        this.role = Role.Tab
        this.selected = selected
      }
      .fluidPressable(onClick = onClick, pressedScale = 0.96f),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Icon(
        imageVector = item.icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier
          .size(24.dp)
          .graphicsLayer {
            val scale = 0.94f + 0.06f * settle
            scaleX = scale
            scaleY = scale
            translationY = selectedLiftPx * settle
          },
      )
      Text(
        text = item.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Wide-screen variant: the same material and the same lens, stood on its side. */
@Composable
fun FluidTabRail(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  if (items.isEmpty()) return

  val tint = GlassDefaults.floatingTint()
  val accent = MaterialTheme.colorScheme.primary
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val timing = tabIndicatorTiming(reducedMotion)
  // Senza una scheda che corrisponda alla rotta, la lente non si mostra: il rail resta in scena
  // anche su una nota, e una lente parcheggiata sulla prima scheda direbbe una cosa falsa.
  val hasSelection = items.any { it.route == selectedRoute }
  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
  val lensAlpha by animateFloatAsState(if (hasSelection) 1f else 0f, label = "fluidTabRailLens")

  val railGlass = rememberGlassBackdrop()
  val tabsGlass = rememberGlassBackdrop()
  val indicatorBackdrop = rememberCombinedGlassBackdrop(railGlass, tabsGlass)

  val itemHeight = FluidTabBarDefaults.Height
  val pillTop = remember { Animatable(0f) }

  LaunchedEffect(selectedIndex, itemHeight, reducedMotion) {
    val target = with(density) { (selectedIndex * itemHeight.toPx()) }
    if (reducedMotion) {
      pillTop.snapTo(target)
    } else {
      pillTop.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 420f))
    }
  }

  val tabs: @Composable () -> Unit = {
    items.forEach { item ->
      Box(modifier = Modifier.fillMaxWidth().height(itemHeight)) {
        FluidTabItemContent(
          item = item,
          selected = item.route == selectedRoute,
          modifier = Modifier.fillMaxSize(),
          onClick = {
            if (item.route == selectedRoute) onReselect(item) else onSelect(item)
          },
        )
      }
    }
  }

  Box(modifier = modifier.width(FluidTabBarDefaults.RailWidth)) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .glassSurface(
          state = backdrop,
          tint = tint,
          shape = ContinuousCornerShape(28.dp),
          role = GlassRole.Floating,
          exports = railGlass,
        ),
      horizontalAlignment = Alignment.CenterHorizontally,
      content = { tabs() },
    )

    CompositionLocalProvider(LocalFluidTabTint provides accent) {
      Column(
        modifier = Modifier
          .clearAndSetSemantics {}
          .alpha(0f)
          .layerBackdrop(tabsGlass.layerBackdrop!!)
          .fillMaxSize()
          .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { tabs() },
      )
    }

    Box(
      modifier = Modifier
        .graphicsLayer {
          translationY = pillTop.value
          alpha = lensAlpha
        }
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .glassSurface(
          state = indicatorBackdrop,
          tint = GlassDefaults.selectionTint(),
          shape = ContinuousCornerShape(22.dp),
          role = GlassRole.Interactive,
          // The lens the vendored bar this design comes from had, and the engine did not.
        //
        // Three things, and they are one thing: the indicator has to read as a *piece of glass*
        // sitting on the bar even when nobody is touching it. The bend is rightly a press —
        // holding it is what asks for it — but the rim and the shadow were multiplied down by the
        // same number, so at rest there was neither, and the shape came entirely from its own
        // wash. Over a bar standing on a bright cover that wash is nearly the cover's colour and
        // the selection simply disappeared. The floor is the vendored bar's own (half a rim,
        // a third of a shadow), and the press takes both the rest of the way.
        //
        // The inner shadow is the other half: it is the thickness of the pane, seen from inside,
        // and it is what makes the press read as pressing *into* something.
        optics = remember {
          GlassDefaults.optics(GlassRole.Interactive).copy(
            blurScale = 0f,
            edgeFloor = FluidTabIndicatorEdgeFloor,
            innerShadowRadius = 8.dp,
            innerShadowAlpha = 1f,
          )
        },
          opticalDepth = { 0f },
        )
        .fillMaxWidth()
        .height(itemHeight),
    )
  }
}

/** Padding a floating tab bar needs around itself. */
@Composable
fun fluidTabBarPadding(): PaddingValues = PaddingValues(
  horizontal = FluidTabBarDefaults.HorizontalMargin,
  vertical = FluidTabBarDefaults.BottomMargin,
)
