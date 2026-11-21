package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceKey
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import java.util.IdentityHashMap

// Un elemento che diventa pagina (piano «iOS in Android», F6B.5c): i preset. Il movimento è tutto di F6B.5a (il dito
// che apre e la chiusura in giù), di F6B.5b (l'elemento condiviso, i tre tratti, «Cresce») e dello zoom di F5B.3. Qui
// non c'è un numero di movimento: un preset sceglie quali di quei pezzi usare, e li scrive al posto dell'app.

/**
 * Come un elemento diventa pagina e ci torna: lo zoom di SwiftUI (`navigationTransition(.zoom(sourceID:in:))`) nei
 * quattro casi delle app. Si dichiara sulla rotta (`composable(route, zoom = FluidZoomStyle.Rise, …)`, oppure
 * `FluidPageHost(zoom = { key -> … })`) e, dalla parte della sorgente, con [fluidZoomSource] o [fluidZoomOpen].
 *
 * - [Zoom]: una card o una miniatura diventa la pagina. Se la miniatura e la pagina marcano la stessa immagine con
 *   [fluidSharedElement], l'immagine viaggia sul proprio riquadro (la griglia e il visore di Gallery).
 * - [Rise]: un accessorio in basso diventa la pagina intera, e si apre anche tirandolo su col dito (il mini lettore che
 *   diventa «In riproduzione»). La sorgente è il contenuto dell'accessorio, che l'app marca da sé: la tab bar non si tocca.
 * - [Grow]: una pillola di vetro ([FluidGrowingPagePill]) diventa pagina o pannello; il vetro resta vetro per tutta la
 *   corsa e diventa opaco solo all'arrivo (la pillola della località di Weather). Con tinta e ottiche proprie: [grow].
 * - [FromToolbar] e [fromToolbar]: un tasto della barra dichiarata diventa la pagina («Scrivi» → compositore). Una lastra
 *   di vetro nasce da sotto il tasto e cresce come in «Cresce»; il tasto resta della barra, che non entra nello zoom e si
 *   trasforma da sé nella barra della pagina d'arrivo.
 *
 * Tutti chiudono anche trascinando in giù ([dismiss]); la rotta può dire altro con il suo `dismiss`.
 *
 * **Non è un enum di proposito** (regola 3.11 del piano: un valore nuovo in un enum pubblico rompe i `when` esaustivi
 * delle app). Si confronta con `==`, e un `when` su un preset ha sempre il suo `else`: un preset nuovo domani non rompe
 * niente.
 *
 * Con le animazioni ridotte, o se la sorgente non si trova (uscita dallo scorrimento, pagina ripristinata dopo la morte
 * del processo senza la sua sorgente), vale il ripiego dell'`Expand` di sempre, senza salti.
 */
@Immutable
class FluidZoomStyle private constructor(
  private val kind: Int,
  internal val glass: GrowingPageGlass?,
  internal val toolbarItem: Any?,
) {
  /**
   * La chiusura di serie del preset, quella che la rotta usa se non ne dichiara una: per tutti e quattro
   * [FluidPageDismiss.Down], come lo zoom di UIKit, il lettore di Musica e i visori, che si chiudono tirandoli giù.
   */
  val dismiss: FluidPageDismiss get() = FluidPageDismiss.Down

  /**
   * Se la sorgente del preset si apre anche tirandola su col dito (F6B.5a): vero per [Rise] e [Grow]. È il valore di
   * serie di `openByDragging` in [fluidZoomSource] e [fluidZoomOpen].
   */
  val opensByDragging: Boolean get() = kind == KindRise || kind == KindGrow

  /** Vero per i preset che partono da un tasto della barra dichiarata. */
  internal val fromBar: Boolean get() = kind == KindToolbar

  override fun equals(other: Any?): Boolean =
    other is FluidZoomStyle && other.kind == kind && other.glass == glass && other.toolbarItem == toolbarItem

  override fun hashCode(): Int = (kind * 31 + (glass?.hashCode() ?: 0)) * 31 + (toolbarItem?.hashCode() ?: 0)

  override fun toString(): String = when (kind) {
    KindZoom -> "FluidZoomStyle.Zoom"
    KindRise -> "FluidZoomStyle.Rise"
    KindGrow -> if (this == Grow) "FluidZoomStyle.Grow" else "FluidZoomStyle.grow(${glass?.tint}, ${glass?.optics})"
    else -> if (toolbarItem == null) "FluidZoomStyle.FromToolbar" else "FluidZoomStyle.fromToolbar($toolbarItem)"
  }

  companion object {
    private const val KindZoom = 0
    private const val KindRise = 1
    private const val KindGrow = 2
    private const val KindToolbar = 3

    /** Una card o una miniatura diventa la pagina, con l'elemento condiviso se c'è. */
    val Zoom: FluidZoomStyle = FluidZoomStyle(KindZoom, null, null)

    /** Un accessorio in basso diventa la pagina intera, guidato dal dito. */
    val Rise: FluidZoomStyle = FluidZoomStyle(KindRise, null, null)

    /**
     * Una pillola di vetro diventa pagina o pannello, con la tinta dei controlli e l'ottica dei pulsanti: le stesse di
     * serie di [FluidGrowingPagePill].
     */
    val Grow: FluidZoomStyle = FluidZoomStyle(KindGrow, GrowingPageGlass(null, GlassDefaults.buttonOptics()), null)

    /**
     * Un tasto della barra diventa la pagina, con la sorgente data dalla rotta: `zoomSource` restituisce quella di
     * `rememberFluidToolbarTransitionSource(id)`, presa nella pagina che dichiara la barra. Se la rotta non sa
     * raggiungerla, [fromToolbar] la trova da sé.
     */
    val FromToolbar: FluidZoomStyle = FluidZoomStyle(KindToolbar, GrowingPageGlass(null, GlassDefaults.buttonOptics()), null)

    /**
     * [Grow] con tinta e ottiche proprie. Passare le stesse a [FluidGrowingPagePill]: la pillola e la lastra che cresce
     * devono essere lo stesso vetro. [tint] null: quella dei controlli ([GlassDefaults.controlTint]).
     */
    fun grow(tint: GlassTint? = null, optics: GlassOptics = GlassDefaults.buttonOptics()): FluidZoomStyle =
      FluidZoomStyle(KindGrow, GrowingPageGlass(tint, optics), null)

    /**
     * Il tasto [itemId] della barra dichiarata dalla **pagina sotto** diventa la pagina: è la sorgente che la pagina sotto
     * otterrebbe con `rememberFluidToolbarTransitionSource(itemId)`, trovata dall'host. Nessuna `zoomSource` da scrivere:
     * `composable("scrivi", zoom = FluidZoomStyle.fromToolbar("scrivi")) { … }`. Se la rotta dà anche una `zoomSource`,
     * vale quella.
     */
    fun fromToolbar(itemId: Any): FluidZoomStyle =
      FluidZoomStyle(KindToolbar, GrowingPageGlass(null, GlassDefaults.buttonOptics()), itemId)
  }
}

/**
 * La sorgente di un preset, in una riga: marca l'elemento come sorgente dello zoom ([fluidTransitionSource] con [id],
 * [namespace] e [shape]), lo apre al tocco con la pressione dell'engine e, se [openByDragging], anche tirandolo su col
 * dito (`fluidOpenGesture`, F6B.5a). [onOpen] è la navigazione dell'app, chiamata una volta per apertura.
 *
 * La rotta dichiara lo stesso preset e la stessa sorgente: `composable(route, zoomSource = { FluidTransitionSource(id,
 * namespace) }, zoom = style)`. Il modificatore va **in testa** alla catena, prima di forma, fondo e contenuto: mentre la
 * pagina è aperta la sorgente non disegna ciò che le sta dopo. Per [FluidZoomStyle.Grow] la sorgente è
 * [FluidGrowingPagePill], che si marca da sé: lì si usa [fluidZoomOpen].
 *
 * [state] serve solo a un accessorio che sta fuori dall'host delle pagine (una shell con la tab bar): è lo stato
 * passato all'host. Dentro una pagina dell'host non serve.
 */
@Composable
fun Modifier.fluidZoomSource(
  id: Any,
  namespace: FluidGlassNamespace,
  style: FluidZoomStyle = FluidZoomStyle.Zoom,
  shape: Shape = RectangleShape,
  state: FluidPageHostState? = null,
  openByDragging: Boolean = style.opensByDragging,
  onOpen: () -> Unit,
): Modifier = fluidZoomOpen(style, state, openByDragging, onOpen).fluidTransitionSource(id, namespace, shape)

/**
 * L'apertura di un preset senza marcare la sorgente: il tocco, con la pressione dell'engine e il ruolo di pulsante, e,
 * se [openByDragging], il dito che tira su. Per una sorgente che si marca da sé, come [FluidGrowingPagePill]:
 *
 * ```kotlin
 * FluidGrowingPagePill("luogo", ns, backdrop, Modifier.fluidZoomOpen(FluidZoomStyle.Grow) { nav.navigate("luoghi") }) {
 *   Text("Bologna")
 * }
 * ```
 */
@Composable
fun Modifier.fluidZoomOpen(
  style: FluidZoomStyle,
  state: FluidPageHostState? = null,
  openByDragging: Boolean = style.opensByDragging,
  onOpen: () -> Unit,
): Modifier {
  val finger = if (openByDragging) fluidOpenGesture(FluidOpenDirection.Up, state, onOpen) else this
  return finger.fluidPressable(onClick = onOpen, role = Role.Button)
}

/**
 * Le sorgenti dei preset di un host: decide quale sorgente dà una pagina, data la sua dichiarazione, e la ricorda per il
 * ritorno. Una per host, rinfrescata a ogni sua composizione prima del diff della pila.
 *
 * - Senza preset la sorgente è quella dichiarata, com'era.
 * - [FluidZoomStyle.Grow] porta il vetro che cresce (`growingGlass`, F6B.5b).
 * - [FluidZoomStyle.fromToolbar] trova il tasto nel posto della barra della pagina sotto: la stessa identità di
 *   `rememberFluidToolbarTransitionSource` chiamata in quella pagina.
 * - **Un tasto della barra non si nasconde.** La barra dell'host non entra nello zoom: resta ferma e si trasforma nella
 *   barra della pagina d'arrivo con lo scambio sfocato di sempre (F4A, F3.4), e il tasto le appartiene. Nascondere la sua
 *   faccia mentre la pagina è aperta la farebbe sparire in un fotogramma alla partenza e ricomparire in un fotogramma
 *   all'arrivo, perché il vetro della barra copre la lastra che nasce sotto di lui (lo mostravano i fotogrammi del banco,
 *   `elemento-dalla-barra`). L'host riceve allora una sorgente di passaggio, con un'identità sua: ne legge la geometria
 *   dal tasto vero, la «consuma» senza togliere niente alla barra, e la lastra cresce da sotto il tasto.
 * - Un tasto della barra può non essere ancora al suo posto quando il ritorno comincia: la barra dell'host si sta
 *   ritrasformando in quella della pagina sotto. Allora vale l'ultimo riquadro in cui la pagina l'aveva trovato, come
 *   per una sorgente sparita a metà ritorno (F5B.3: la sagoma atterra dove l'aveva trovata). Se la pagina non l'ha mai
 *   trovato (ripristinata dopo la morte del processo), vale il ripiego.
 */
internal class FluidZoomSources {
  private val below = HashMap<Any, Any>()
  private val toolbarPages = IdentityHashMap<FluidTransitionSource, Any>()
  private val toolbarButtons = IdentityHashMap<FluidTransitionSource, FluidTransitionSource>()
  private val toolbarSeen = HashMap<Any, FluidTransitionSourceGeometry>()

  /** La pila nuova: chi sta sotto chi. Le pagine che non ci sono più si dimenticano, salvo quelle ancora in scena. */
  fun track(stack: List<Any>, onStage: (Any) -> Boolean) {
    for (i in 1 until stack.size) below[stack[i]] = stack[i - 1]
    val alive = stack.toHashSet()
    fun gone(page: Any) = page !in alive && !onStage(page)
    below.keys.removeAll(::gone)
    toolbarSeen.keys.removeAll(::gone)
    val forgotten = toolbarPages.filterValues(::gone).keys
    toolbarPages.keys.removeAll(forgotten)
    toolbarButtons.keys.removeAll(forgotten)
  }

  /** La pagina sotto [page] nell'ultima pila che l'aveva, o null. */
  fun below(page: Any): Any? = below[page]

  /**
   * La sorgente di [page] col suo preset [style]. [declared] è quella della rotta; [barOwner] dà il proprietario delle
   * sorgenti della barra di una pagina (il suo posto nell'host), per [FluidZoomStyle.fromToolbar].
   */
  fun sourceOf(
    page: Any,
    style: FluidZoomStyle?,
    declared: FluidTransitionSource?,
    barOwner: (Any) -> Any?,
  ): FluidTransitionSource? {
    if (style == null) return declared
    val glass = style.glass
    if (!style.fromBar) {
      val raw = declared ?: return null
      return if (glass == null) raw else raw.growingGlass(glass.tint, glass.optics)
    }
    val button = declared ?: style.toolbarItem?.let { id ->
      below[page]?.let(barOwner)?.let { owner -> FluidTransitionSource(FluidTransitionSourceKey(owner, id)) }
    } ?: return null
    val passage = FluidTransitionSource(FluidTransitionSourceKey(BarPassage(page), style.toolbarItem ?: button))
      .let { if (glass == null) it else it.growingGlass(glass.tint, glass.optics) }
    toolbarPages[passage] = page
    toolbarButtons[passage] = button
    return passage
  }

  /** Dove si legge una sorgente: com'era; per i tasti della barra dal tasto vero, o dall'ultimo riquadro in cui c'era. */
  fun resolve(source: FluidTransitionSource): FluidTransitionSourceGeometry? {
    val page = toolbarPages[source] ?: return source.resolve(includeCoveredPage = true)
    val found = toolbarButtons[source]?.resolve(includeCoveredPage = true)
    if (found != null) {
      toolbarSeen[page] = found
      return found
    }
    return toolbarSeen[page]
  }

  /** Il proprietario delle sorgenti di passaggio: nessuna vista lo usa, quindi consumarle non nasconde niente. */
  private class BarPassage(val page: Any)
}
