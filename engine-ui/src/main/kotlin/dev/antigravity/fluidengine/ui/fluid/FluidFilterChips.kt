package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.ExpandMore
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.History
import dev.antigravity.fluidengine.ui.fluid.icons.LocationOn
import dev.antigravity.fluidengine.ui.fluid.icons.Schedule
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPriority
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/*
 * F6B.8 · Filtri e suggerimenti a pillole (la fila; i gettoni nel campo arrivano dopo F6B.7).
 *
 * Una fila di pillole sotto il campo di ricerca (nello slot `suggestions` di `FluidSearchSpec`), nel pannello
 * dei suggerimenti, o fuori dalla ricerca. Il suo comportamento di serie, scelto da Alessio il 21 novembre 2026: **quando
 * le pillole non stanno nella larghezza, testo grande compreso, la fila si raccoglie in un tasto di vetro «Filtri · 2»**,
 * con la stessa regola del trabocco della barra (priorita' e larghezza misurata, `resolveFluidToolbar`). Il tasto apre
 * il menu dal proprio vetro, con le stesse voci a interruttore, che lasciano il menu aperto (`keepsMenuOpen`, F5A.3).
 * In Transit le etichette a 1,3 e a 2,0 si leggevano «Tutt», «Urba» (`ChipLayoutTest.kt`): qui un'etichetta non si
 * tronca mai, la pillola che non ci sta va nel menu.
 *
 * Apple non ha la fila che diventa menu (F6B.0, §10: cercata nelle HIG Search fields, Typography e Menus): si giustifica
 * con la regola HIG sul testo dinamico. Il viaggio del menu e' quello di F5A, dalla sorgente: nessun numero di movimento
 * nuovo in questo file.
 */

/** Come si sceglie nella fila: una sola pillola alla volta (gli ambiti, esclusivi) o quante se ne vuole. */
enum class FluidFilterMode { Single, Multiple }

/** Un valore di una pillola a piu' valori (il periodo: «7 giorni», «30 giorni», «Altro»). */
@Immutable
data class FluidFilterValue(val id: String, val label: String) {
  init { require(id.isNotEmpty()) { "FluidFilterValue richiede un id non vuoto" } }
}

/**
 * Una pillola della fila.
 *
 * @property id stabile e unico nella fila: la selezione parla di id, mai di posizioni.
 * @property label il nome della pillola, intero: non si tronca mai (se non ci sta, la pillola va nel menu).
 * @property icon un simbolo facoltativo in testa, anche nella voce del menu.
 * @property priority chi lascia la fila per primo quando non c'e' posto: come nella barra, prima `Low`, poi `Automatic`,
 *   poi `High`; a parita' esce prima l'ultima della fila. Il tasto «Filtri» non esce mai.
 * @property values con almeno un valore la pillola e' **a piu' valori**: al tocco si apre in un menu dal suo vetro e
 *   mostra il valore scelto. Nel menu della fila raccolta diventa una voce con il sottomenu dei valori.
 * @property enabled spenta, non si sceglie (ne' nella fila ne' nel menu).
 */
@Immutable
data class FluidFilter(
  val id: String,
  val label: String,
  val icon: ImageVector? = null,
  val priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
  val values: List<FluidFilterValue> = emptyList(),
  val enabled: Boolean = true,
) {
  init { require(id.isNotEmpty()) { "FluidFilter richiede un id non vuoto" } }

  /** Vero se la pillola si apre in un menu di valori. */
  val hasValues: Boolean get() = values.isNotEmpty()
}

/**
 * La selezione di una fila, posseduta dall'app: quali pillole sono accese e, per quelle a piu' valori, quale valore.
 * E' **la stessa** per la fila e per il menu: una sola funzione ([toggle], [choose]) la cambia da tutti e due i posti.
 */
@Immutable
data class FluidFilterSelection(
  val selected: Set<String> = emptySet(),
  val values: Map<String, String> = emptyMap(),
) {
  fun isSelected(id: String): Boolean = id in selected

  /** Il valore scelto della pillola [id], o null se la pillola e' spenta o non ha valori. */
  fun valueOf(id: String): String? = values[id]?.takeIf { id in selected }

  /** Quante pillole sono accese. */
  val count: Int get() = selected.size

  /**
   * Il tocco su una pillola semplice. [FluidFilterMode.Multiple]: accende o spegne. [FluidFilterMode.Single]: la sceglie
   * al posto delle altre; toccata quando e' gia' la scelta resta com'e' (gli ambiti, esclusivi, ne hanno sempre una).
   */
  fun toggle(id: String, mode: FluidFilterMode): FluidFilterSelection = when (mode) {
    FluidFilterMode.Multiple -> if (id in selected) FluidFilterSelection(selected - id, values - id) else copy(selected = selected + id)
    FluidFilterMode.Single -> if (id in selected) this else FluidFilterSelection(setOf(id), values.filterKeys { it == id })
  }

  /** La scelta di un valore della pillola [id] ([value] null: «Qualsiasi», la pillola si spegne). */
  fun choose(id: String, value: String?, mode: FluidFilterMode): FluidFilterSelection = when {
    value == null -> FluidFilterSelection(selected - id, values - id)
    mode == FluidFilterMode.Multiple -> FluidFilterSelection(selected + id, values + (id to value))
    else -> FluidFilterSelection(setOf(id), mapOf(id to value))
  }

  companion object {
    val None: FluidFilterSelection = FluidFilterSelection()

    /** Una selezione con le pillole [ids] accese. */
    fun of(vararg ids: String): FluidFilterSelection = FluidFilterSelection(ids.toSet())

    /** Per `rememberSaveable(stateSaver = FluidFilterSelection.Saver)`: sopravvive alla morte del processo. */
    val Saver: Saver<FluidFilterSelection, Any> = listSaver(
      save = { s -> listOf(s.selected.size) + s.selected.toList() + s.values.flatMap { listOf(it.key, it.value) } },
      restore = { saved ->
        val n = saved[0] as Int
        val ids = saved.subList(1, 1 + n).map { it as String }.toSet()
        val pairs = saved.drop(1 + n).chunked(2).associate { (it[0] as String) to (it[1] as String) }
        FluidFilterSelection(ids, pairs)
      },
    )
  }
}

/**
 * Una fila dichiarata: le pillole, il modo e il nome del tasto in cui si raccoglie. E' cio' che restituiscono i preset
 * ([FluidFilterPresets]) e cio' che `FluidSearchSpec(filters = …)` porta come gettoni suggeriti (F6B.8b).
 *
 * @property label il nome del tasto della fila raccolta («Filtri»); null: quello di [FluidFilterLabels].
 */
@Immutable
data class FluidFilterSet(
  val filters: List<FluidFilter>,
  val mode: FluidFilterMode = FluidFilterMode.Multiple,
  val label: String? = null,
)

/**
 * Le parole che la fila e i suggerimenti dicono da se'. **In italiano di serie**, come [FluidLabels]: un'app tradotta le
 * passa una volta alla radice con [LocalFluidFilterLabels].
 */
@Immutable
data class FluidFilterLabels(
  /** Il nome del tasto della fila raccolta, in [FluidFilterMode.Multiple]. */
  val filters: String = "Filtri",
  /** Il nome del tasto della fila raccolta in [FluidFilterMode.Single], quando la scelta e' rimasta nella fila. */
  val more: String = "Altro",
  /** Il tasto con le pillole accese che contiene: `%1$s` il nome, `%2$d` quante («Filtri · 2»). */
  val activeCount: String = "%1\$s · %2\$d",
  /** Lo stato del tasto per TalkBack: `%1$d` quante pillole accese contiene («attivi: 2», giusto anche per uno). */
  val activeSpoken: String = "attivi: %1\$d",
  /** La prima voce del menu di una pillola a piu' valori: nessun valore, la pillola spenta. */
  val anyValue: String = "Qualsiasi",
  /** La ✕ di un recente, e l'azione di TalkBack sulla riga. */
  val removeRecent: String = "Rimuovi dai recenti",
  /** Lo stato vuoto, solo dopo il primo carattere. */
  val noResults: String = "Nessun risultato",
)

/** Le parole della fila e dei suggerimenti per tutto l'albero. */
val LocalFluidFilterLabels: ProvidableCompositionLocal<FluidFilterLabels> = staticCompositionLocalOf { FluidFilterLabels() }

/** Le misure della fila. */
object FluidFilterChipsDefaults {
  /** Lo spazio fra due pillole: come fra due tasti di una fila di controlli. */
  val Spacing: Dp = 8.dp
}

// --- La regola del trabocco -------------------------------------------------------------------------------------

/**
 * Come si dispone una fila: gli indici (nell'ordine dichiarato) delle pillole che restano nella fila e di quelle che
 * vanno nel menu del tasto. [collapsed] vuota: la fila intera, senza tasto.
 */
@Immutable
internal data class FluidFilterRowLayout(val visible: List<Int>, val collapsed: List<Int>) {
  val collapses: Boolean get() = collapsed.isNotEmpty()
}

/**
 * **La stessa regola del trabocco della barra** (`resolveFluidToolbar`, R5 B11) per una fila di pillole: priorita' e
 * larghezza misurata, nessuna misura indovinata.
 *
 * Se tutte le pillole stanno in [availableWidth] (con [spacing] fra loro) la fila resta intera. Altrimenti escono una
 * alla volta, prima le `Low`, poi le `Automatic`, poi le `High`, e a parita' **dall'interno verso l'esterno** come nella
 * barra (per una fila che parte dal bordo d'inizio, l'interno e' la coda: esce prima l'ultima), finche' le pillole
 * rimaste piu' il tasto ([collapsedWidth]) ci stanno. Al limite resta il tasto solo. L'ordine nel menu resta quello
 * dichiarato.
 *
 * [widths] sono le larghezze **naturali** delle pillole, al testo di adesso (la scala del carattere e' gia' dentro):
 * cosi' la fila diventa menu a testo grande come a una finestra stretta, con la stessa regola. La larghezza di una
 * pillola a piu' valori e del tasto e' quella della loro etichetta piu' larga: un valore scelto o un filtro acceso nel
 * menu non cambiano la disposizione.
 */
internal fun resolveFluidFilterRow(
  widths: List<Float>,
  priorities: List<FluidToolbarPriority>,
  availableWidth: Float,
  collapsedWidth: Float,
  spacing: Float,
): FluidFilterRowLayout {
  require(widths.size == priorities.size) { "una priorita' per pillola" }
  val all = widths.indices.toList()
  fun rowWidth(indices: Collection<Int>, withKey: Boolean): Float {
    val count = indices.size + if (withKey) 1 else 0
    if (count == 0) return 0f
    return indices.sumOf { widths[it].toDouble() }.toFloat() + (if (withKey) collapsedWidth else 0f) + spacing * (count - 1)
  }
  if (rowWidth(all, withKey = false) <= availableWidth + 0.5f) return FluidFilterRowLayout(all, emptyList())
  val leaving = all.sortedWith(compareBy<Int>({ priorities[it].ordinal }, { -it }))
  val kept = all.toMutableSet()
  for (index in leaving) {
    kept -= index
    if (rowWidth(kept, withKey = true) <= availableWidth + 0.5f) break
  }
  return FluidFilterRowLayout(all.filter { it in kept }, all.filter { it !in kept })
}

/** Il nome del tasto della fila raccolta, come si vede: «Filtri», «Filtri · 2», o in Single la scelta che contiene. */
internal fun fluidFilterKeyLabel(
  collapsed: List<FluidFilter>,
  selection: FluidFilterSelection,
  mode: FluidFilterMode,
  name: String?,
  labels: FluidFilterLabels,
): String {
  val active = collapsed.count { selection.isSelected(it.id) }
  return when (mode) {
    FluidFilterMode.Multiple -> {
      val base = name ?: labels.filters
      if (active > 0) labels.activeCount.format(base, active) else base
    }
    FluidFilterMode.Single -> collapsed.firstOrNull { selection.isSelected(it.id) }?.let { fluidFilterPillLabel(it, selection) }
      ?: name ?: labels.more
  }
}

/** L'etichetta piu' larga che il tasto puo' mostrare: quella con cui la fila si misura (la disposizione non salta). */
internal fun fluidFilterKeyProbeLabel(
  filters: List<FluidFilter>,
  mode: FluidFilterMode,
  name: String?,
  labels: FluidFilterLabels,
): List<String> = when (mode) {
  FluidFilterMode.Multiple -> listOf(labels.activeCount.format(name ?: labels.filters, filters.size.coerceAtLeast(1)))
  FluidFilterMode.Single -> filters.flatMap { f -> if (f.hasValues) f.values.map { it.label } else listOf(f.label) } +
    (name ?: labels.more)
}

/** Che cosa mostra una pillola: il valore scelto, se ne ha uno, altrimenti il suo nome. */
internal fun fluidFilterPillLabel(filter: FluidFilter, selection: FluidFilterSelection): String =
  selection.valueOf(filter.id)?.let { v -> filter.values.firstOrNull { it.id == v }?.label } ?: filter.label

/** Le etichette che una pillola puo' mostrare: si misura con la piu' larga. */
internal fun fluidFilterPillProbeLabels(filter: FluidFilter): List<String> = listOf(filter.label) + filter.values.map { it.label }

// --- Le voci del menu: la stessa selezione della fila --------------------------------------------------------

/**
 * Le voci del menu di una pillola a piu' valori: «Qualsiasi» e poi i valori, con la spunta su quello scelto. Una scelta
 * chiude il menu (e' una scelta fra alternative, come «Ordina per»).
 */
internal fun fluidFilterValueItems(
  filter: FluidFilter,
  selection: () -> FluidFilterSelection,
  mode: FluidFilterMode,
  labels: FluidFilterLabels,
  onChange: (FluidFilterSelection) -> Unit,
): List<FluidContextAction> {
  val now = selection().valueOf(filter.id)
  val any = FluidContextAction(label = labels.anyValue, selected = now == null) {
    onChange(selection().choose(filter.id, null, mode))
  }
  return listOf(any) + filter.values.mapIndexed { index, value ->
    FluidContextAction(label = value.label, selected = now == value.id, startsSection = index == 0) {
      onChange(selection().choose(filter.id, value.id, mode))
    }
  }
}

/**
 * Le voci del menu della fila raccolta, nell'ordine dichiarato: **le stesse pillole**, con la stessa selezione. In
 * [FluidFilterMode.Multiple] ogni voce e' un interruttore che lascia il menu aperto (`keepsMenuOpen`); in
 * [FluidFilterMode.Single] una scelta, che chiude. Una pillola a piu' valori e' una voce col sottomenu dei valori, nello
 * stesso vetro, col valore scelto come sottotitolo. Ogni scelta passa da [FluidFilterSelection.toggle] o
 * [FluidFilterSelection.choose], come il tocco nella fila, sulla selezione **di quel momento** ([selection]).
 */
internal fun fluidFilterMenuItems(
  filters: List<FluidFilter>,
  selection: () -> FluidFilterSelection,
  mode: FluidFilterMode,
  labels: FluidFilterLabels,
  onChange: (FluidFilterSelection) -> Unit,
): List<FluidContextAction> {
  val now = selection()
  return filters.map { filter ->
    if (filter.hasValues) {
      val value = now.valueOf(filter.id)?.let { v -> filter.values.firstOrNull { it.id == v } }
      FluidContextAction(
        label = filter.label,
        icon = filter.icon,
        enabled = filter.enabled,
        selected = value != null,
        subtitle = value?.label,
        children = fluidFilterValueItems(filter, selection, mode, labels, onChange),
      ) {}
    } else {
      FluidContextAction(
        label = filter.label,
        icon = filter.icon,
        enabled = filter.enabled,
        selected = now.isSelected(filter.id),
        keepsMenuOpen = mode == FluidFilterMode.Multiple,
      ) { onChange(selection().toggle(filter.id, mode)) }
    }
  }
}

// --- Il menu dal vetro di una pillola: come il gruppo della barra -----------------------------------------------

/**
 * Il tasto premuto di un chip che apre un menu: un quadrato del lato corto del chip, al suo centro. E' li' che la capsula
 * del chip si raccoglie nella goccia, e sopra di lui si posa il pannello (`solveFluidMenuPanel` legge
 * [FluidMenuAnchor.restBounds]).
 */
internal fun fluidChipMenuTrigger(chip: Rect): Rect {
  val side = chip.minDimension
  val c = chip.center
  return Rect(c.x - side / 2f, c.y - side / 2f, c.x + side / 2f, c.y + side / 2f)
}

/**
 * L'aggancio di F6B.8: un [FluidChip] che apre un menu **dal proprio vetro come il gruppo di una barra** (correzione di
 * Alessio dopo i video, 22 novembre 2026: «come il menu dal gruppo», F5A.2, R4 B1 b). Per il livello dei menu il chip e'
 * un'ancora di gruppo: la capsula ([FluidMenuAnchor.groupBounds]) e' il chip, il tasto premuto ([FluidMenuAnchor.restBounds])
 * la goccia al suo centro ([fluidChipMenuTrigger]). Il viaggio e' quindi quello del gruppo, senza un numero nuovo: la
 * capsula si stringe nella goccia (al 63% in 33 ms, la goccia a 67 ms), la goccia sale con la lente, le voci entrano nella
 * lente e il corpo si posa nel pannello con un superamento solo; al congedo il vetro torna capsula e la faccia ci rientra.
 *
 * La parte del chip, come la barra fa con le icone del gruppo ([FluidMenuAnchor.groupFaces]):
 * - registra la sua faccia in [faceLayer] mentre si disegna ([fluidChipFaceRecord]); l'host la disegna sopra il vetro
 *   al ritorno, con l'opacita' della fase;
 * - mentre il menu e' in scena non disegna vetro ne' faccia ([FluidMenuAnchor.hidden]); nella nascita disegna solo la
 *   faccia registrata con la curva d'uscita della barra, ritagliata dal vetro che si stringe ([fluidChipMenuGate]);
 * - scrive dove sta ([place]) e, a menu aperto, tiene capsula e goccia sul chip: se il chip cambia larghezza (il
 *   conteggio del tasto «Filtri · N»), il vetro torna dove il chip e' adesso.
 */
internal class FluidChipMenuSource(val anchor: FluidMenuAnchor) {
  /** Il chip a riposo, in pixel della radice. */
  var bounds: Rect = Rect.Zero
    private set

  /** La faccia del chip, registrata mentre si disegna. La scrive il chip. */
  var faceLayer: GraphicsLayer? = null

  internal var opener: (withFinger: Boolean) -> Boolean = { false }

  /** Apre il menu; [withFinger]: il dito passa al menu (il click lungo). Vero se si e' aperto davvero. */
  fun open(withFinger: Boolean): Boolean = opener(withFinger)

  /** La posa del chip: a menu in scena capsula e goccia lo seguono. */
  fun place(chip: Rect) {
    bounds = chip
    if (anchor.groupBounds != null) aim(chip)
  }

  /** L'ancora di gruppo sul chip [chip]: capsula, tasto premuto e facce. */
  fun aim(chip: Rect) {
    anchor.groupBounds = chip
    anchor.restBounds = fluidChipMenuTrigger(chip)
    anchor.pressedBounds = anchor.restBounds
    anchor.groupFaces = faceLayer
  }
}

/**
 * Registra la faccia del chip (il contenuto dentro i margini) in [faces], nelle coordinate del chip ([GraphicsLayer.topLeft]
 * e' il margine), e la disegna da li': cio' che si vede e' lo strato. E' lo strato che l'host disegna al ritorno.
 */
internal fun Modifier.fluidChipFaceRecord(faces: GraphicsLayer, horizontal: Dp, vertical: Dp): Modifier = drawWithContent {
  val offset = IntOffset(horizontal.roundToPx(), vertical.roundToPx())
  faces.topLeft = offset
  faces.record { this@drawWithContent.drawContent() }
  translate(-offset.x.toFloat(), -offset.y.toFloat()) { drawLayer(faces) }
}

/**
 * Il cancello del chip sorgente. A riposo il chip si disegna com'e'. Mentre il menu e' in scena il vetro e' dell'host (un
 * cambio di proprietario allo stesso pixel); nella nascita, finche' la fase del gruppo ([FluidMenuAnchor.groupProgress]) non
 * l'ha spenta, il chip disegna la sua faccia registrata con le curve d'uscita della barra (sfocata, rimpicciolita, poi via,
 * come le icone del gruppo: sfocate al massimo a ~41 ms, via a ~54 ms), ritagliata dal vetro che si raccoglie nella goccia
 * ([FluidMenuAnchor.groupClip]). Al ritorno la faccia la disegna l'host sopra il suo vetro ([FluidMenuAnchor.groupFacesAbove]).
 */
internal fun Modifier.fluidChipMenuGate(source: FluidChipMenuSource, exit: GraphicsLayer, referencePx: Float): Modifier =
  this
    .fluidMenuPointerRelay(source.anchor.handOff)
    .onGloballyPositioned { source.place(it.unclippedBoundsInRoot()) }
    .drawWithContent {
      val anchor = source.anchor
      if (!anchor.hidden) {
        drawContent()
        return@drawWithContent
      }
      val faces = source.faceLayer ?: return@drawWithContent
      if (anchor.groupBounds == null || anchor.groupFacesAbove() || faces.isReleased) return@drawWithContent
      val phase = anchor.groupProgress()
      if (phase >= FluidMotion.MenuGroupFaceProgressSpan) return@drawWithContent
      val u = (phase / FluidMotion.MenuGroupFaceProgressSpan).coerceAtLeast(0f)
      val blurAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
      val alpha = GlassContentCurves.alpha(GlassContentRole.Exit, u, blurAvailable)
      if (alpha <= 0.004f) return@drawWithContent
      val clip = anchor.groupClip()
      exit.record {
        if (clip == null) drawLayer(faces) else clipPath(Path().apply { addRoundRect(clip) }) { drawLayer(faces) }
      }
      val scale = GlassContentCurves.scale(GlassContentRole.Exit, u)
      exit.alpha = alpha
      exit.scaleX = scale
      exit.scaleY = scale
      val radius = if (blurAvailable) {
        glassContentBlurRadiusPx(glassContentSigmaPx(GlassContentCurves.blur(GlassContentRole.Exit, u) * GlassContentDefaults.PeakBlur, referencePx))
      } else {
        0f
      }
      exit.renderEffect = if (radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
      drawLayer(exit)
    }

/**
 * Il menu che nasce dal vetro di un chip ([FluidChipMenuSource]): una voce `Expand` dell'host dei modali con un'ancora di
 * gruppo, il percorso del menu dal gruppo della barra (`FluidToolbarMenu`): stessa nascita, stessa lente, stesso congedo.
 * Null senza host: la fila allora non si raccoglie (scorre) e la pillola a piu' valori passa al valore dopo.
 *
 * [actions] si legge **all'apertura** e, se e' una [FluidMenuActions] con un contenuto vivo, a ogni ridisegno: le voci a
 * interruttore mostrano lo stato nuovo senza chiudere il menu.
 */
@Composable
internal fun rememberFluidChipMenu(title: String, actions: () -> List<FluidContextAction>): FluidChipMenuSource? {
  val host = fluidChipMenuHost() ?: return null
  val entry = remember(host) { FluidGlassModalEntry() }
  val source = remember(host) { FluidChipMenuSource(FluidMenuAnchor()) }
  val anchor = source.anchor
  val currentActions by rememberUpdatedState(actions)
  SideEffect {
    entry.presentation = FluidGlassModalPresentation.Expand
    entry.paneTitle = title
    entry.onDismissRequest = { entry.visible = false }
    entry.menuAnchor = anchor
    entry.menuHandOff = anchor.handOff
    entry.origin = { source.bounds }
  }
  DisposableEffect(host, entry) {
    host.register(entry)
    onDispose {
      entry.visible = false
      anchor.consume(false)
      anchor.handOff.sink = null
      host.unregister(entry)
    }
  }
  return remember(host, entry, source) {
    source.opener = open@{ withFinger ->
      // L'apertura e' sincrona, nel gesto, come quella del gruppo della barra (`FluidToolbarMenu.open`): voci, ancora,
      // sequenza e visibilita' nello stesso istante, e il chip cede il vetro nello stesso fotogramma.
      if (entry.visible || anchor.hidden || source.bounds.isEmpty) return@open false
      val list = currentActions()
      if (list.isEmpty()) return@open false
      source.aim(source.bounds)
      entry.previewSize = source.bounds
      entry.actions = list
      entry.openedAt = host.nextSequence()
      entry.selection.cancel()
      entry.visible = true
      entry.lifted = true
      anchor.consume(true)
      if (withFinger) anchor.handOff.attach(entry.selection)
      true
    }
    source
  }
}

/**
 * L'host in cui un chip puo' aprire il suo menu, o null. Null anche dentro un piano flottante che disegna lui i vetri dei
 * pezzi (`FluidGlassContainer`, come la riga del campo di ricerca): li' il chip non puo' cedere il suo vetro al menu senza
 * il passaggio del piano, e due vetri allo stesso posto sono proprio cio' che il viaggio esclude. La fila allora scorre.
 */
@Composable
internal fun fluidChipMenuHost(): FluidGlassModalHostState? {
  val host = LocalFluidGlassModalHostState.current ?: return null
  return host.takeIf { currentFluidGlassContainer(FluidCapsuleShape) == null }
}

// --- La fila --------------------------------------------------------------------------------------------------

/**
 * Una fila di filtri a pillole. Si usa sotto il campo di ricerca (lo slot `suggestions` di `FluidSearchSpec`, sopra
 * [FluidSearchSuggestions]), nel pannello dei suggerimenti, e anche fuori dalla ricerca.
 *
 * ```kotlin
 * var selection by rememberSaveable(stateSaver = FluidFilterSelection.Saver) { mutableStateOf(FluidFilterSelection.None) }
 * FluidFilterChips(
 *   filters = listOf(
 *     FluidFilter("aperti", "Aperti ora"),
 *     FluidFilter("parcheggio", "Con parcheggio"),
 *     FluidFilterPresets.lastDays(),
 *   ),
 *   selection = selection,
 *   onChange = { selection = it },
 * )
 * ```
 *
 * **Il comportamento di serie** (scelta di Alessio del 21 novembre 2026): quando le pillole non stanno nella larghezza,
 * testo grande compreso, quelle che non ci stanno si raccolgono in **un** tasto di vetro «Filtri · 2» (il numero dice
 * quante pillole accese contiene), con la regola del trabocco della barra ([resolveFluidFilterRow]: priorita', poi
 * dall'interno verso l'esterno). Il tasto apre il menu **dal proprio vetro** con le stesse voci: in
 * [FluidFilterMode.Multiple] interruttori che lasciano il menu aperto. La selezione e' la stessa nella fila e nel menu.
 * Senza `FluidGlassModalHost` la fila non si raccoglie: scorre in orizzontale. Lo stesso dentro un piano flottante che
 * disegna lui i vetri (la riga del campo, lo slot `scopes`, che le da' anche una larghezza senza limite): il posto della
 * fila che si raccoglie e' sotto il campo, nello slot `suggestions`, sopra [FluidSearchSuggestions].
 *
 * **Una pillola a piu' valori** ([FluidFilter.values], il preset [FluidFilterPresets.lastDays]) si apre in menu dal suo
 * vetro, con «Qualsiasi» e i valori; scelto un valore, la pillola lo mostra e si accende.
 *
 * **TalkBack**: ogni pillola e' un pulsante con lo stato «selezionato»; il tasto dice quante pillole accese contiene
 * («Filtri · 2, attivi: 2»); una pillola a piu' valori dice nome e valore («Periodo, 7 giorni»). In
 * [FluidFilterMode.Single] la fila e' un gruppo di scelte. Ogni bersaglio e' almeno 48 × 48 dp.
 *
 * @param filters le pillole, in ordine; gli id devono essere unici.
 * @param selection la selezione, posseduta dall'app.
 * @param onChange la selezione nuova, dalla fila o dal menu.
 * @param mode una sola alla volta (gli ambiti) o quante se ne vuole (di serie).
 * @param label il nome del tasto della fila raccolta; null: «Filtri» in Multiple, «Altro» in Single (o la scelta, se e'
 *   nel menu).
 */
@Composable
fun FluidFilterChips(
  filters: List<FluidFilter>,
  selection: FluidFilterSelection,
  onChange: (FluidFilterSelection) -> Unit,
  mode: FluidFilterMode = FluidFilterMode.Multiple,
  modifier: Modifier = Modifier,
  label: String? = null,
  labels: FluidFilterLabels = LocalFluidFilterLabels.current,
) {
  require(filters.map { it.id }.toSet().size == filters.size) { "FluidFilterChips: gli id delle pillole devono essere unici" }
  val host = fluidChipMenuHost()
  val group = if (mode == FluidFilterMode.Single) Modifier.selectableGroup() else Modifier
  if (host == null) {
    // Senza l'host dei modali (o dentro un piano che disegna lui i vetri) non c'e' menu: la fila resta intera e scorre,
    // e nessuna etichetta si tronca.
    Row(
      modifier = modifier.then(group).horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(FluidFilterChipsDefaults.Spacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (filter in filters) key(filter.id) { FilterPill(filter, selection, onChange, mode, labels) }
    }
    return
  }
  val keyProbes = fluidFilterKeyProbeLabel(filters, mode, label, labels)
  SubcomposeLayout(modifier.then(group)) { constraints ->
    val gap = FluidFilterChipsDefaults.Spacing.roundToPx()
    // Le larghezze naturali, al testo di adesso: pillole di misura senza vetro, misurate e mai posate.
    val probes = subcompose(ProbeSlot) {
      for (filter in filters) {
        FluidChipProbe(fluidFilterPillProbeLabels(filter), leading = filter.icon != null, trailing = filter.hasValues)
      }
      FluidChipProbe(keyProbes, leading = false, trailing = true)
    }
    val loose = Constraints(maxHeight = constraints.maxHeight)
    val measuredProbes = probes.map { it.measure(loose) }
    val widths = measuredProbes.dropLast(1).map { it.width.toFloat() }
    val keyWidth = measuredProbes.last().width.toFloat()
    val available = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
    val row = resolveFluidFilterRow(widths, filters.map { it.priority }, available, keyWidth, gap.toFloat())
    val childConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
    val placeables = ArrayList<androidx.compose.ui.layout.Placeable>()
    for (index in row.visible) {
      val filter = filters[index]
      placeables += subcompose(PillSlot(filter.id)) { FilterPill(filter, selection, onChange, mode, labels) }
        .map { it.measure(childConstraints) }
    }
    if (row.collapses) {
      val collapsed = row.collapsed.map { filters[it] }
      placeables += subcompose(KeySlot) { CollapsedKey(collapsed, selection, onChange, mode, label, labels) }
        .map { it.measure(childConstraints) }
    }
    val height = placeables.maxOfOrNull { it.height } ?: 0
    val width = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
    layout(
      width.coerceIn(constraints.minWidth, constraints.maxWidth),
      height.coerceIn(constraints.minHeight, constraints.maxHeight),
    ) {
      var x = 0
      for (p in placeables) {
        p.placeRelative(x, (height - p.height) / 2)
        x += p.width + gap
      }
    }
  }
}

/** Una fila dichiarata ([FluidFilterSet], per esempio da [FluidFilterPresets]). */
@Composable
fun FluidFilterChips(
  set: FluidFilterSet,
  selection: FluidFilterSelection,
  onChange: (FluidFilterSelection) -> Unit,
  modifier: Modifier = Modifier,
  labels: FluidFilterLabels = LocalFluidFilterLabels.current,
) {
  FluidFilterChips(set.filters, selection, onChange, set.mode, modifier, set.label, labels)
}

private object ProbeSlot
private object KeySlot
private data class PillSlot(val id: String)

/**
 * Una pillola di misura: lo stesso contenuto di [FluidChipCore] (margini, simbolo, etichetta, freccia, bersaglio minimo)
 * senza vetro, senza tocco e senza semantica. Con piu' etichette e' larga quanto la piu' larga: le etichette stanno una
 * sopra l'altra in un `Box`, e la piu' larga decide.
 */
@Composable
private fun FluidChipProbe(labels: List<String>, leading: Boolean, trailing: Boolean) {
  val style = fluidChipLabelStyle()
  Box(
    modifier = Modifier
      .clearAndSetSemantics { }
      .defaultMinSize(minWidth = FluidChipMinTarget, minHeight = FluidChipMinTarget)
      .padding(horizontal = FluidChipHorizontalPadding, vertical = FluidChipVerticalPadding),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(FluidChipContentGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leading) Spacer(Modifier.width(FluidChipGlyphSize))
      Box {
        for (text in labels) Text(text = text, style = style, maxLines = 1, softWrap = false)
      }
      if (trailing) Spacer(Modifier.width(fluidChipTrailingGlyphSize()))
    }
  }
}

@Composable
private fun FilterGlyph(icon: ImageVector, size: Dp = FluidChipGlyphSize) {
  FluidIcon(imageVector = icon, contentDescription = null, size = size)
}

/** Una pillola della fila: semplice (si accende al tocco) o a piu' valori (si apre in menu dal suo vetro). */
@Composable
private fun FilterPill(
  filter: FluidFilter,
  selection: FluidFilterSelection,
  onChange: (FluidFilterSelection) -> Unit,
  mode: FluidFilterMode,
  labels: FluidFilterLabels,
) {
  val leading: (@Composable () -> Unit)? = filter.icon?.let { icon -> { FilterGlyph(icon) } }
  if (!filter.hasValues) {
    FluidChipCore(
      label = filter.label,
      selected = selection.isSelected(filter.id),
      onClick = { onChange(selection.toggle(filter.id, mode)) },
      modifier = Modifier.defaultMinSize(minWidth = FluidChipMinTarget),
      enabled = filter.enabled,
      leading = leading,
    )
    return
  }
  val currentSelection by rememberUpdatedState(selection)
  val currentOnChange by rememberUpdatedState(onChange)
  val menu = rememberFluidChipMenu(title = filter.label) {
    FluidMenuActions { fluidFilterValueItems(filter, { currentSelection }, mode, labels) { currentOnChange(it) } }
  }
  val value = selection.valueOf(filter.id)?.let { v -> filter.values.firstOrNull { it.id == v } }
  FluidChipCore(
    label = fluidFilterPillLabel(filter, selection),
    selected = value != null,
    // Senza host (nessun menu) il tocco passa al valore dopo, e dall'ultimo a «Qualsiasi».
    onClick = { onChange(selection.choose(filter.id, fluidFilterNextValue(filter, selection), mode)) },
    modifier = Modifier
      .defaultMinSize(minWidth = FluidChipMinTarget)
      .semantics { contentDescription = if (value != null) "${filter.label}, ${value.label}" else filter.label },
    enabled = filter.enabled,
    leading = leading,
    trailing = { FilterGlyph(FluidIcons.ExpandMore, fluidChipTrailingGlyphSize()) },
    menu = menu,
  )
}

/** Il valore dopo quello scelto, per la pillola a piu' valori senza menu: dall'ultimo si torna a nessuno. */
internal fun fluidFilterNextValue(filter: FluidFilter, selection: FluidFilterSelection): String? {
  val now = selection.valueOf(filter.id)
  val index = filter.values.indexOfFirst { it.id == now }
  return filter.values.getOrNull(index + 1)?.id
}

/** Il tasto in cui la fila si raccoglie: «Filtri · 2», e il menu con le pillole che contiene. */
@Composable
private fun CollapsedKey(
  collapsed: List<FluidFilter>,
  selection: FluidFilterSelection,
  onChange: (FluidFilterSelection) -> Unit,
  mode: FluidFilterMode,
  name: String?,
  labels: FluidFilterLabels,
) {
  val currentSelection by rememberUpdatedState(selection)
  val currentOnChange by rememberUpdatedState(onChange)
  val currentCollapsed by rememberUpdatedState(collapsed)
  val title = name ?: if (mode == FluidFilterMode.Multiple) labels.filters else labels.more
  val menu = rememberFluidChipMenu(title = title) {
    FluidMenuActions { fluidFilterMenuItems(currentCollapsed, { currentSelection }, mode, labels) { currentOnChange(it) } }
  }
  val active = collapsed.count { selection.isSelected(it.id) }
  FluidChipCore(
    label = fluidFilterKeyLabel(collapsed, selection, mode, name, labels),
    selected = active > 0,
    onClick = {},
    modifier = Modifier
      .defaultMinSize(minWidth = FluidChipMinTarget)
      .semantics { if (mode == FluidFilterMode.Multiple && active > 0) stateDescription = labels.activeSpoken.format(active) },
    trailing = { FilterGlyph(FluidIcons.ExpandMore, fluidChipTrailingGlyphSize()) },
    menu = menu,
  )
}

// --- I suggerimenti -------------------------------------------------------------------------------------------

/** Che cosa e' un suggerimento: decide il simbolo di serie e, per i recenti, la ✕. */
enum class FluidSuggestionKind { Result, Recent, Nearby, Popular }

/**
 * Un suggerimento della lista sotto il campo.
 *
 * @property relevance la pertinenza, **decisa dall'app**: la lista e' una sola, dalla piu' pertinente; a parita' resta
 *   l'ordine in cui l'app la passa.
 * @property icon il simbolo in testa; null: quello del [kind] (lente, orologio, segnaposto, stella).
 */
@Immutable
data class FluidSearchSuggestion(
  val id: String,
  val title: String,
  val subtitle: String? = null,
  val kind: FluidSuggestionKind = FluidSuggestionKind.Result,
  val relevance: Float = 0f,
  val icon: ImageVector? = null,
) {
  init { require(id.isNotEmpty()) { "FluidSearchSuggestion richiede un id non vuoto" } }
}

/** Che cosa mostra la lista dei suggerimenti: niente, le righe, o lo stato vuoto. */
internal enum class FluidSuggestionsShow { Nothing, Rows, NoResults }

/**
 * La regola dello stato vuoto (Transit): **mai un «Nessun risultato» prima del primo carattere**, e nemmeno mentre i
 * risultati stanno arrivando. Con una parola, nessun risultato e niente in arrivo: lo stato vuoto.
 */
internal fun fluidSuggestionsShow(query: String, count: Int, loading: Boolean): FluidSuggestionsShow = when {
  count > 0 -> FluidSuggestionsShow.Rows
  query.isBlank() || loading -> FluidSuggestionsShow.Nothing
  else -> FluidSuggestionsShow.NoResults
}

/** Una lista sola, dalla piu' pertinente; a parita' l'ordine dell'app (l'ordinamento e' stabile). */
internal fun fluidSuggestionsOrdered(suggestions: List<FluidSearchSuggestion>): List<FluidSearchSuggestion> =
  suggestions.sortedByDescending { it.relevance }

private fun FluidSuggestionKind.defaultIcon(): ImageVector = when (this) {
  FluidSuggestionKind.Result -> FluidIcons.Search
  FluidSuggestionKind.Recent -> FluidIcons.History
  FluidSuggestionKind.Nearby -> FluidIcons.LocationOn
  FluidSuggestionKind.Popular -> FluidIcons.Star
}

/**
 * La lista dei suggerimenti sotto il campo (lo slot `suggestions` di `FluidSearchSpec`, o dove serve).
 *
 * - **Una lista sola**, ordinata per pertinenza ([FluidSearchSuggestion.relevance]): la pertinenza la decide l'app, la
 *   lista non la indovina e non divide in sezioni.
 * - **I recenti** ([FluidSuggestionKind.Recent]) hanno la ✕ per riga, un bersaglio da 48 dp, e la stessa azione per
 *   TalkBack sulla riga («Rimuovi dai recenti»). Senza [onRemoveRecent] la ✕ non c'e'.
 * - **Lo stato vuoto** solo dopo il primo carattere ([query] non vuota), e mai mentre [loading]: prima del primo
 *   carattere una lista vuota non mostra niente.
 *
 * Righe e gruppo sono quelli delle liste dell'engine ([FluidListGroup], [FluidListRow], [FluidListDivider]).
 */
@Composable
fun FluidSearchSuggestions(
  query: String,
  suggestions: List<FluidSearchSuggestion>,
  onSuggestionClick: (FluidSearchSuggestion) -> Unit,
  modifier: Modifier = Modifier,
  onRemoveRecent: ((FluidSearchSuggestion) -> Unit)? = null,
  loading: Boolean = false,
  labels: FluidFilterLabels = LocalFluidFilterLabels.current,
) {
  val ordered = remember(suggestions) { fluidSuggestionsOrdered(suggestions) }
  when (fluidSuggestionsShow(query, ordered.size, loading)) {
    FluidSuggestionsShow.Nothing -> Unit
    FluidSuggestionsShow.NoResults -> Text(
      text = labels.noResults,
      modifier = modifier
        .fillMaxWidth()
        .padding(vertical = 24.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    FluidSuggestionsShow.Rows -> FluidListGroup(modifier = modifier) {
      ordered.forEachIndexed { index, suggestion ->
        key(suggestion.id) {
          if (index > 0) FluidListDivider()
          SuggestionRow(suggestion, onSuggestionClick, onRemoveRecent, labels)
        }
      }
    }
  }
}

@Composable
private fun SuggestionRow(
  suggestion: FluidSearchSuggestion,
  onClick: (FluidSearchSuggestion) -> Unit,
  onRemoveRecent: ((FluidSearchSuggestion) -> Unit)?,
  labels: FluidFilterLabels,
) {
  val removable = suggestion.kind == FluidSuggestionKind.Recent && onRemoveRecent != null
  val remove = { onRemoveRecent?.invoke(suggestion); Unit }
  FluidListRow(
    title = suggestion.title,
    subtitle = suggestion.subtitle.orEmpty(),
    leading = {
      FluidIcon(
        imageVector = suggestion.icon ?: suggestion.kind.defaultIcon(),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        size = 20.dp,
      )
    },
    leadingTile = false,
    disclosure = false,
    onClick = { onClick(suggestion) },
    badge = if (removable) {
      {
        Box(
          modifier = Modifier
            .size(FluidChipMinTarget)
            .semantics { contentDescription = "${labels.removeRecent}: ${suggestion.title}" }
            .fluidPressable(onClick = remove, role = androidx.compose.ui.semantics.Role.Button,
              feedback = FluidPressFeedback.Expand, grip = FluidPressGrip.YieldsToScroll),
          contentAlignment = Alignment.Center,
        ) {
          FluidIcon(
            imageVector = FluidIcons.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 18.dp,
          )
        }
      }
    } else {
      null
    },
    customActions = if (removable) listOf(CustomAccessibilityAction(labels.removeRecent) { remove(); true }) else emptyList(),
  )
}

// --- I preset -------------------------------------------------------------------------------------------------

/**
 * I preset della fila e dei suggerimenti (F6B.8): **Ambiti** (esclusivi, in linea), **Filtri a scelta multipla**,
 * **Intervalli** (una pillola a piu' valori), **Gettoni** («da: persona», F6B.8b), **Recenti**, **Vicini e popolari**.
 */
object FluidFilterPresets {
  /**
   * **Ambiti**: scelte esclusive in linea sotto il campo, come gli ambiti di iOS 27 («Tutte le caselle», «Casella
   * attuale», WWDC26 292). Una sola alla volta: l'app parte con `FluidFilterSelection.of(<il primo id>)`.
   */
  fun scopes(options: List<FluidFilterValue>, label: String? = null): FluidFilterSet =
    FluidFilterSet(options.map { FluidFilter(it.id, it.label) }, FluidFilterMode.Single, label)

  /** **Filtri a scelta multipla**: interruttori, accesi insieme; raccolti, il menu resta aperto a ogni scelta. */
  fun choices(filters: List<FluidFilter>, label: String? = null): FluidFilterSet =
    FluidFilterSet(filters, FluidFilterMode.Multiple, label)

  /** **Intervalli**: una pillola a piu' valori, che si apre in menu dal suo vetro. */
  fun range(
    id: String,
    label: String,
    values: List<FluidFilterValue>,
    icon: ImageVector? = FluidIcons.Schedule,
    priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
  ): FluidFilter = FluidFilter(id = id, label = label, icon = icon, priority = priority, values = values)

  /**
   * L'intervallo piu' comune, il periodo: «7 giorni», «30 giorni» e «Altro» (che l'app tratta come vuole, per esempio
   * con la scelta delle date). Gli id dei valori sono `"7"`, `"30"` e `"altro"`.
   */
  fun lastDays(
    id: String = "periodo",
    label: String = "Periodo",
    days: List<Int> = listOf(7, 30),
    dayLabel: (Int) -> String = { "$it giorni" },
    other: String? = "Altro",
  ): FluidFilter = range(
    id = id,
    label = label,
    values = days.map { FluidFilterValue(it.toString(), dayLabel(it)) } + listOfNotNull(other?.let { FluidFilterValue("altro", it) }),
  )

  /**
   * **Gettoni** («da: persona», F6B.8b): i gettoni suggeriti di una ricerca, per `FluidSearchSpec(filters = …)`. Ogni
   * valore e' una pillola che, scelta, diventa gettone nel campo (SwiftUI `suggestedTokens`), con l'id e l'etichetta del
   * valore; [icon] sta in testa alla pillola e al gettone (di serie nessuno: un nome corto e niente simbolo fanno stare tre
   * gettoni e il testo nel campo di un telefono da 360 dp; [FluidIcons.Person] per la persona). [mode]
   * [FluidFilterMode.Single]: un gettone solo di questa fila alla volta, che prende il posto dell'altro.
   */
  fun tokens(
    values: List<FluidFilterValue>,
    label: String? = null,
    icon: ImageVector? = null,
    mode: FluidFilterMode = FluidFilterMode.Multiple,
  ): FluidFilterSet = FluidFilterSet(values.map { FluidFilter(it.id, it.label, icon = icon) }, mode, label)

  /** **Recenti**: una ricerca fatta prima, con la ✕ nella lista. */
  fun recent(id: String, title: String, subtitle: String? = null, relevance: Float = 0f): FluidSearchSuggestion =
    FluidSearchSuggestion(id, title, subtitle, FluidSuggestionKind.Recent, relevance)

  /** Un luogo vicino. */
  fun nearby(id: String, title: String, subtitle: String? = null, relevance: Float = 0f): FluidSearchSuggestion =
    FluidSearchSuggestion(id, title, subtitle, FluidSuggestionKind.Nearby, relevance)

  /** Una ricerca popolare. */
  fun popular(id: String, title: String, subtitle: String? = null, relevance: Float = 0f): FluidSearchSuggestion =
    FluidSearchSuggestion(id, title, subtitle, FluidSuggestionKind.Popular, relevance)

  /**
   * **Vicini e popolari**: le due liste in una sola, per pertinenza (quella dell'app); a parita' prima i vicini. E' la
   * lista prima del primo carattere, al posto di uno stato vuoto.
   */
  fun nearbyAndPopular(nearby: List<FluidSearchSuggestion>, popular: List<FluidSearchSuggestion>): List<FluidSearchSuggestion> =
    fluidSuggestionsOrdered(
      nearby.map { it.copy(kind = FluidSuggestionKind.Nearby) } + popular.map { it.copy(kind = FluidSuggestionKind.Popular) },
    )
}
