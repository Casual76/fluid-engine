package dev.antigravity.fluidengine.ui.fluid.toolbar

import kotlin.math.abs
import kotlin.math.max

/** Posizioni semantiche: la pagina dichiara l'intento e la barra sceglie il lato, anche nel trabocco. */
enum class FluidToolbarPlacement {
  Automatic, Navigation, Leading, Principal, Trailing, PrimaryAction, SecondaryAction,
  ConfirmationAction, CancellationAction, DestructiveAction, BottomBar, PinnedTrailing,
}

/** Il posto si libera dal meno importante; a parità resta prima il tasto più esterno (R5 B11). */
enum class FluidToolbarPriority { Low, Automatic, High }

/** Il fisso separa due vetri, il flessibile distribuisce i comandi agli estremi (R1 B2). */
enum class FluidToolbarSpacing { Fixed, Flexible }

/** Il ruolo mantiene riconoscibili conferma, uscita e azioni distruttive senza imporre icone all'app. */
enum class FluidButtonRole { Confirm, Close, Cancel, Destructive }

internal enum class FluidToolbarRole {
  Action, Back, Close, Confirm, Primary, Text, Search, FixedSpacer, FlexibleSpacer, Group, Overflow,
}

internal enum class FluidToolbarSide { Leading, Center, Trailing }
internal enum class FluidToolbarBar { Top, Bottom }
internal enum class FluidToolbarTransition { Automatic, Materialize, Identity }
internal enum class FluidToolbarMotion { Full, Reduced, Identity }
internal enum class FluidToolbarTrigger {
  State, Push, Pop, TabChange, PredictiveBackPreview, PredictiveBackCancelled, PredictiveBackCommitted,
}

/** Valori di contenuto, senza Color, ImageVector o stato Compose: li traduce la DSL, non il risolutore. */
internal data class FluidToolbarBadge(val text: String? = null)

/**
 * Un id esplicito diverso vieta l'euristica: [matchId] è null solo per chiavi locali generate dalla DSL.
 * [symbol] identifica il simbolo, non la sua istanza grafica; [widthDp] è la misura fornita dal chiamante
 * per testo/campi/contenuto libero. Tinta ARGB, badge e selezione appartengono al singolo elemento.
 * [children] serve solo a Group; il modello ne prende una copia, comprese le liste annidate.
 */
internal data class FluidToolbarItem(
  val id: Any,
  val role: FluidToolbarRole = FluidToolbarRole.Action,
  val placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
  val symbol: Any? = null,
  val label: String? = null,
  val priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
  val overflow: Boolean = false,
  val sharedBackground: Boolean = true,
  val badge: FluidToolbarBadge? = null,
  val tintArgb: Long? = null,
  val selected: Boolean = false,
  val enabled: Boolean = true,
  val prominent: Boolean = false,
  val widthDp: Float? = null,
  val children: List<FluidToolbarItem> = emptyList(),
  val matchId: Any? = id,
  val bornFrom: Any? = null,
  val transition: FluidToolbarTransition = FluidToolbarTransition.Automatic,
  val contentKey: Any? = null,
) {
  init {
    require(widthDp == null || widthDp.isFinite() && widthDp >= 0f)
    require(role == FluidToolbarRole.Group || children.isEmpty())
    require(tintArgb == null || tintArgb in 0L..0xffffffffL)
  }

  val isSpacer: Boolean get() = role == FluidToolbarRole.FixedSpacer || role == FluidToolbarRole.FlexibleSpacer
  val isPrimary: Boolean get() = prominent || role == FluidToolbarRole.Primary || role == FluidToolbarRole.Confirm ||
    placement == FluidToolbarPlacement.PrimaryAction || placement == FluidToolbarPlacement.ConfirmationAction
  val isExit: Boolean get() = role == FluidToolbarRole.Back || role == FluidToolbarRole.Close
  val content: Any? get() = contentKey ?: ToolbarContentKey(symbol != null, symbol ?: label)
}

private data class ToolbarContentKey(val isSymbol: Boolean, val value: Any?)

internal data class FluidToolbarTitles(val inline: String? = null, val large: String? = null, val subtitle: String? = null)
internal enum class FluidToolbarPresentationKind { Menu, Sheet }
internal data class FluidToolbarPresentation(val sourceId: Any, val kind: FluidToolbarPresentationKind)

/** Snapshot strutturale: ricrearlo non cambia l'identità e modificare la lista dell'app non riscrive il passato. */
internal class FluidToolbarModel(
  items: List<FluidToolbarItem> = emptyList(),
  val titles: FluidToolbarTitles = FluidToolbarTitles(),
  val presentation: FluidToolbarPresentation? = null,
) {
  val items: List<FluidToolbarItem> = items.map { it.snapshot() }

  init {
    val all = this.items.flatMap { it.tree() }
    require(all.map { it.id }.distinct().size == all.size) { "id duplicati nella barra" }
    require(all.none { it.id == FluidToolbarOverflowId }) { "id del trabocco riservato" }
    require(all.count { it.role != FluidToolbarRole.Group && it.isPrimary } <= 1) { "una sola azione principale" }
    val explicit = all.filter { it.role != FluidToolbarRole.Group && !it.isSpacer }.mapNotNull { it.matchId }
    require(explicit.distinct().size == explicit.size) { "identità di accoppiamento duplicate" }
    require(presentation == null || all.any { it.id == presentation.sourceId && !it.isSpacer && it.role != FluidToolbarRole.Group })
  }

  override fun equals(other: Any?): Boolean = other is FluidToolbarModel &&
    items == other.items && titles == other.titles && presentation == other.presentation
  override fun hashCode(): Int = 31 * (31 * items.hashCode() + titles.hashCode()) + (presentation?.hashCode() ?: 0)
}

private fun FluidToolbarItem.snapshot(): FluidToolbarItem = copy(children = children.map { it.snapshot() })
private fun FluidToolbarItem.tree(): List<FluidToolbarItem> = listOf(this) + children.flatMap { it.tree() }

/**
 * L'adattatore F4A.2 passa GroupItemPitch, GroupSpacing, ScreenEdgeMargin e GlassButtonLens di FluidMetrics.
 * Il modello non importa neppure Dp: è compilabile con la sola libreria standard Kotlin.
 */
internal data class FluidToolbarMetrics(
  val itemPitchDp: Float,
  val groupSpacingDp: Float,
  val edgeMarginDp: Float,
  val buttonWidthDp: Float,
) {
  init {
    require(listOf(itemPitchDp, buttonWidthDp).all { it.isFinite() && it > 0f })
    require(listOf(groupSpacingDp, edgeMarginDp).all { it.isFinite() && it >= 0f })
  }
}

internal object FluidToolbarOverflowId
internal data class FluidToolbarGroupId(val members: Set<Any>)
internal data class FluidToolbarGroup(
  val id: FluidToolbarGroupId,
  val side: FluidToolbarSide,
  val items: List<FluidToolbarItem>,
  val leftDp: Float,
  val widthDp: Float,
  val sharedBackground: Boolean,
)

internal data class FluidToolbarElement(
  val item: FluidToolbarItem,
  val side: FluidToolbarSide,
  val groupId: FluidToolbarGroupId?,
  val leftDp: Float,
  val widthDp: Float,
  val inOverflow: Boolean = false,
) {
  val centerDp: Float get() = leftDp + widthDp / 2f
}

internal data class FluidToolbarResolved(
  val groups: List<FluidToolbarGroup>,
  val elements: List<FluidToolbarElement>,
  val overflow: List<FluidToolbarItem>,
  val widthDp: Float,
  val requiredWidthDp: Float,
  val fits: Boolean,
  val metrics: FluidToolbarMetrics,
  val titles: FluidToolbarTitles,
  val presentation: FluidToolbarPresentation?,
) {
  val overflowElement: FluidToolbarElement? get() = elements.firstOrNull { it.item.id == FluidToolbarOverflowId }
  fun element(id: Any): FluidToolbarElement? = elements.firstOrNull { it.item.id == id }
}

private data class ToolbarToken(val item: FluidToolbarItem, val side: FluidToolbarSide, val groupPath: List<Any>)
private data class ToolbarDraft(val side: FluidToolbarSide, val path: List<Any>, val items: MutableList<FluidToolbarItem>, val gap: Float)

private fun FluidToolbarItem.side(): FluidToolbarSide = when {
  isPrimary -> FluidToolbarSide.Trailing
  placement == FluidToolbarPlacement.Principal -> FluidToolbarSide.Center
  placement in listOf(FluidToolbarPlacement.Navigation, FluidToolbarPlacement.Leading, FluidToolbarPlacement.CancellationAction) ->
    FluidToolbarSide.Leading
  placement == FluidToolbarPlacement.PinnedTrailing -> FluidToolbarSide.Trailing
  isExit -> FluidToolbarSide.Leading
  else -> FluidToolbarSide.Trailing
}

private fun toolbarTokens(items: List<FluidToolbarItem>, bar: FluidToolbarBar): List<ToolbarToken> {
  val flat = ArrayList<ToolbarToken>()
  fun add(item: FluidToolbarItem, path: List<Any>, parent: FluidToolbarItem?) {
    val inherited = if (parent == null) item else item.copy(
      placement = if (item.placement == FluidToolbarPlacement.Automatic) parent.placement else item.placement,
      priority = if (item.priority == FluidToolbarPriority.Automatic) parent.priority else item.priority,
      overflow = item.overflow || parent.overflow,
      sharedBackground = item.sharedBackground && parent.sharedBackground,
    )
    if (inherited.role == FluidToolbarRole.Group) {
      inherited.children.forEach { add(it, path + inherited.id, inherited) }
    } else if ((inherited.placement == FluidToolbarPlacement.BottomBar) == (bar == FluidToolbarBar.Bottom)) {
      flat += ToolbarToken(inherited, inherited.side(), path)
    }
  }
  items.forEach { add(it, emptyList(), null) }
  // Prima/dopo uno spazio flessibile sono estremi diversi, non due vetri sovrapposti sullo stesso lato.
  val totals = flat.filter { it.item.role == FluidToolbarRole.FlexibleSpacer }.groupingBy { it.side }.eachCount()
  val seen = mutableMapOf<FluidToolbarSide, Int>()
  return flat.map { token ->
    val total = totals[token.side] ?: 0
    val count = seen[token.side] ?: 0
    if (token.item.role == FluidToolbarRole.FlexibleSpacer) seen[token.side] = count + 1
    val side = when {
      total == 0 || token.item.isPrimary || token.item.isExit || token.item.placement == FluidToolbarPlacement.PinnedTrailing -> token.side
      count == 0 -> FluidToolbarSide.Leading
      count == total -> FluidToolbarSide.Trailing
      else -> FluidToolbarSide.Center
    }
    token.copy(side = side)
  }
}

private fun FluidToolbarItem.canShare(): Boolean = sharedBackground && !isPrimary && !isExit &&
  placement != FluidToolbarPlacement.PinnedTrailing &&
  role in listOf(FluidToolbarRole.Action, FluidToolbarRole.Search)

private fun toolbarDrafts(tokens: List<ToolbarToken>, hidden: Set<Any>, more: Boolean, metrics: FluidToolbarMetrics): List<ToolbarDraft> {
  val result = ArrayList<ToolbarDraft>()
  for (side in FluidToolbarSide.entries) {
    var current: ToolbarDraft? = null
    var gap = metrics.groupSpacingDp
    for (token in tokens.filter { it.side == side }) {
      val item = token.item
      if (item.isSpacer) {
        current = null
        gap = max(gap, item.widthDp ?: metrics.groupSpacingDp)
        continue
      }
      if (item.id in hidden) continue
      if (current != null && current.path == token.groupPath && current.items.last().canShare() && item.canShare()) {
        current.items += item
      } else {
        current = ToolbarDraft(side, token.groupPath, arrayListOf(item), gap)
        result += current
        gap = metrics.groupSpacingDp
      }
    }
  }
  if (more) result += ToolbarDraft(FluidToolbarSide.Trailing, emptyList(), arrayListOf(
    FluidToolbarItem(FluidToolbarOverflowId, FluidToolbarRole.Overflow, symbol = FluidToolbarOverflowId),
  ), metrics.groupSpacingDp)
  // L'azione principale è esterna; PinnedTrailing è il bordo destro anche se è dichiarato prima.
  return result.sortedWith(compareBy<ToolbarDraft>({ it.side.ordinal }, {
    if (it.side != FluidToolbarSide.Trailing) 0 else when {
      it.items.any { item -> item.placement == FluidToolbarPlacement.PinnedTrailing } -> 3
      it.items.any { item -> item.isPrimary } -> 2
      it.items.any { item -> item.role == FluidToolbarRole.Overflow } -> 1
      else -> 0
    }
  }))
}

private fun FluidToolbarItem.width(metrics: FluidToolbarMetrics): Float = max(metrics.buttonWidthDp, widthDp ?: metrics.buttonWidthDp)
private fun ToolbarDraft.width(metrics: FluidToolbarMetrics): Float =
  items.sumOf { it.width(metrics).toDouble() }.toFloat() + (items.size - 1) * (metrics.itemPitchDp - metrics.buttonWidthDp)

private fun List<ToolbarDraft>.sideWidth(side: FluidToolbarSide, metrics: FluidToolbarMetrics): Float {
  val row = filter { it.side == side }
  return row.sumOf { it.width(metrics).toDouble() }.toFloat() + row.drop(1).sumOf { it.gap.toDouble() }.toFloat()
}

private fun List<ToolbarDraft>.requiredWidth(metrics: FluidToolbarMetrics): Float {
  val leading = sideWidth(FluidToolbarSide.Leading, metrics)
  val center = sideWidth(FluidToolbarSide.Center, metrics)
  val trailing = sideWidth(FluidToolbarSide.Trailing, metrics)
  val inside = if (center > 0f) center + 2f * max(
    if (leading > 0f) leading + metrics.groupSpacingDp else 0f,
    if (trailing > 0f) trailing + metrics.groupSpacingDp else 0f,
  ) else leading + trailing + if (leading > 0f && trailing > 0f) metrics.groupSpacingDp else 0f
  return if (inside == 0f) 0f else metrics.edgeMarginDp * 2f + inside
}

/**
 * R1 B1–B10, R5 B11: icone insieme, testo/uscita/principale isolati, tre gruppi e menu compreso nel conto.
 * I pareggi espellono dall'interno verso l'esterno; l'ordine nel menu resta quello dichiarato.
 * Con vincoli impossibili (es. finestra più stretta di un fissato) [FluidToolbarResolved.fits] è false:
 * non si perde in silenzio un comando protetto. Nessuna misura di testo viene indovinata dal modello.
 */
internal fun resolveFluidToolbar(
  model: FluidToolbarModel,
  widthDp: Float,
  searchActive: Boolean = false,
  metrics: FluidToolbarMetrics,
  bar: FluidToolbarBar = FluidToolbarBar.Top,
): FluidToolbarResolved {
  require(widthDp.isFinite() && widthDp >= 0f)
  val tokens = toolbarTokens(model.items, bar)
  val actions = tokens.filter { !it.item.isSpacer }
  require(actions.count { it.item.isPrimary } <= 1) { "un gruppo non può dichiarare più azioni principali" }
  val hidden = actions.filter { it.item.overflow || searchActive && it.item.placement == FluidToolbarPlacement.PinnedTrailing }
    .mapTo(linkedSetOf()) { it.item.id }
  val candidates = actions.filter { !it.item.isExit && !it.item.isPrimary && it.item.placement != FluidToolbarPlacement.PinnedTrailing }
    .sortedWith(compareBy<ToolbarToken>({ it.item.priority.ordinal }, {
      val index = actions.indexOf(it)
      if (it.side == FluidToolbarSide.Leading) -index else index
    }))
  var drafts = toolbarDrafts(tokens, hidden, hidden.isNotEmpty(), metrics)
  for (candidate in candidates) {
    if (drafts.size <= 3 && drafts.requiredWidth(metrics) <= widthDp) break
    if (hidden.add(candidate.item.id)) drafts = toolbarDrafts(tokens, hidden, true, metrics)
  }
  val groups = ArrayList<FluidToolbarGroup>()
  val elements = ArrayList<FluidToolbarElement>()
  for (side in FluidToolbarSide.entries) {
    val row = drafts.filter { it.side == side }
    val rowWidth = drafts.sideWidth(side, metrics)
    var left = when (side) {
      FluidToolbarSide.Leading -> metrics.edgeMarginDp
      FluidToolbarSide.Center -> (widthDp - rowWidth) / 2f
      FluidToolbarSide.Trailing -> widthDp - metrics.edgeMarginDp - rowWidth
    }
    row.forEachIndexed { index, draft ->
      if (index > 0) left += draft.gap
      val groupWidth = draft.width(metrics)
      val groupId = FluidToolbarGroupId(draft.items.mapTo(linkedSetOf()) { it.id })
      groups += FluidToolbarGroup(groupId, side, draft.items.toList(), left, groupWidth, draft.items.first().sharedBackground)
      var itemLeft = left
      for (item in draft.items) {
        elements += FluidToolbarElement(item, side, groupId, itemLeft, item.width(metrics))
        itemLeft += item.width(metrics) + metrics.itemPitchDp - metrics.buttonWidthDp
      }
      left += groupWidth
    }
  }
  val overflow = actions.filter { it.item.id in hidden }.map { it.item }
  elements += actions.filter { it.item.id in hidden }.map {
    FluidToolbarElement(it.item, it.side, null, 0f, it.item.width(metrics), inOverflow = true)
  }
  val required = drafts.requiredWidth(metrics)
  return FluidToolbarResolved(groups, elements, overflow, widthDp, required,
    groups.size <= 3 && required <= widthDp, metrics, model.titles, model.presentation)
}

internal enum class FluidToolbarMatch { Id, Role, Symbol, OutsideOrder }
internal enum class FluidToolbarElementChange {
  Matched, BornFromNeighbour, MergeIntoNeighbour, Materialize, Dematerialize, IntoOverflow, OutOfOverflow, InOverflow, Identity,
}
internal enum class FluidToolbarContentChange { None, BlurReplace, BlurAppear, BlurDisappear, Crossfade, Identity }
internal data class FluidToolbarElementDiff(
  val from: FluidToolbarElement?,
  val to: FluidToolbarElement?,
  val change: FluidToolbarElementChange,
  val content: FluidToolbarContentChange,
  val matchedBy: FluidToolbarMatch? = null,
  val neighbourId: Any? = null,
  val insideGroup: Boolean = false,
) {
  val moved: Boolean get() = from != null && to != null && !from.inOverflow && !to.inOverflow && from.leftDp != to.leftDp
  val appearanceChanged: Boolean get() = from != null && to != null &&
    (from.item.tintArgb != to.item.tintArgb || from.item.selected != to.item.selected || from.item.badge != to.item.badge ||
      from.item.isPrimary != to.item.isPrimary)
}

internal enum class FluidToolbarGroupChange { Matched, Split, Merge, Reconfigured, BornFromNeighbour, MergeIntoNeighbour, Materialize, Dematerialize }
internal data class FluidToolbarGroupDiff(
  val from: List<FluidToolbarGroup>,
  val to: List<FluidToolbarGroup>,
  val change: FluidToolbarGroupChange,
  val neighbourId: FluidToolbarGroupId? = null,
  val animateShape: Boolean = true,
)

internal enum class FluidToolbarTitleKind { Inline, Large, Subtitle }
internal enum class FluidToolbarTitleChange { BlurReplace, BlurAppear, BlurDisappear, PageContent }
internal data class FluidToolbarTitleDiff(val kind: FluidToolbarTitleKind, val change: FluidToolbarTitleChange, val from: String?, val to: String?)
internal data class FluidToolbarPresentationDiff(
  val request: FluidToolbarPresentation,
  val sourceGroupId: FluidToolbarGroupId,
  val opening: Boolean,
)

internal data class FluidToolbarDiff(
  val elements: List<FluidToolbarElementDiff>,
  val groups: List<FluidToolbarGroupDiff>,
  val titles: List<FluidToolbarTitleDiff>,
  val presentations: List<FluidToolbarPresentationDiff>,
  val startsNow: Boolean,
  val motion: FluidToolbarMotion,
)

private data class ToolbarPair(val from: FluidToolbarElement, val to: FluidToolbarElement, val by: FluidToolbarMatch)

private fun FluidToolbarElement.semanticRole(): Any? = when {
  item.isExit -> "exit"
  item.isPrimary -> "primary"
  item.role == FluidToolbarRole.Overflow -> "overflow"
  item.role == FluidToolbarRole.Search -> "search"
  item.role == FluidToolbarRole.Text -> "text"
  else -> null
}

private fun toolbarPairs(from: FluidToolbarResolved, to: FluidToolbarResolved): List<ToolbarPair> {
  val sources = from.elements.toMutableList()
  val targets = to.elements.toMutableList()
  val pairs = ArrayList<ToolbarPair>()
  fun accept(a: FluidToolbarElement, b: FluidToolbarElement, by: FluidToolbarMatch) {
    pairs += ToolbarPair(a, b, by)
    sources.remove(a)
    targets.remove(b)
  }
  for (b in targets.toList()) {
    val a = sources.firstOrNull { b.item.matchId != null && it.item.matchId == b.item.matchId && it.side == b.side }
    if (a != null) accept(a, b, FluidToolbarMatch.Id)
  }
  for (side in FluidToolbarSide.entries) {
    fun outside(element: FluidToolbarElement): Float = if (side == FluidToolbarSide.Trailing) -element.centerDp else element.centerDp
    for (by in listOf(FluidToolbarMatch.Role, FluidToolbarMatch.Symbol, FluidToolbarMatch.OutsideOrder)) {
      for (b in targets.filter { it.side == side && it.item.matchId == null && !it.inOverflow }.sortedBy { outside(it) }) {
        val eligible = sources.filter { it.side == side && it.item.matchId == null && !it.inOverflow }
        val key = if (by == FluidToolbarMatch.Role) b.semanticRole() else b.item.symbol
        val a = when (by) {
          FluidToolbarMatch.Role, FluidToolbarMatch.Symbol -> if (key == null) null else eligible.singleOrNull {
            (if (by == FluidToolbarMatch.Role) it.semanticRole() else it.item.symbol) == key
          }?.takeIf { targets.count { target -> target.side == side && target.item.matchId == null && !target.inOverflow &&
            (if (by == FluidToolbarMatch.Role) target.semanticRole() else target.item.symbol) == key } == 1 }
          else -> eligible.sortedBy { outside(it) }.firstOrNull()
        }
        if (a != null) accept(a, b, by)
      }
    }
  }
  return pairs
}

private fun toolbarGroupDiffs(from: FluidToolbarResolved, to: FluidToolbarResolved, pairs: List<ToolbarPair>): List<FluidToolbarGroupDiff> {
  val edges = linkedSetOf<Pair<FluidToolbarGroupId, FluidToolbarGroupId>>()
  for (pair in pairs) {
    val old = if (pair.from.inOverflow && !pair.to.inOverflow) from.overflowElement?.groupId else pair.from.groupId
    val new = if (pair.to.inOverflow && !pair.from.inOverflow) to.overflowElement?.groupId else pair.to.groupId
    if (old != null && new != null) edges += old to new
  }
  val remainingOld = from.groups.toMutableList()
  val remainingNew = to.groups.toMutableList()
  val result = ArrayList<FluidToolbarGroupDiff>()
  while (true) {
    val edge = edges.firstOrNull { e -> remainingOld.any { it.id == e.first } && remainingNew.any { it.id == e.second } } ?: break
    val oldIds = linkedSetOf(edge.first)
    val newIds = linkedSetOf(edge.second)
    do {
      val before = oldIds.size + newIds.size
      edges.forEach { (a, b) -> if (a in oldIds || b in newIds) { oldIds += a; newIds += b } }
    } while (before != oldIds.size + newIds.size)
    val old = remainingOld.filter { it.id in oldIds }
    val new = remainingNew.filter { it.id in newIds }
    result += FluidToolbarGroupDiff(old, new, when {
      old.size == 1 && new.size == 1 -> FluidToolbarGroupChange.Matched
      old.size == 1 -> FluidToolbarGroupChange.Split
      new.size == 1 -> FluidToolbarGroupChange.Merge
      else -> FluidToolbarGroupChange.Reconfigured
    })
    remainingOld.removeAll(old.toSet())
    remainingNew.removeAll(new.toSet())
  }
  for (group in remainingNew) {
    val forced = group.items.any { it.transition != FluidToolbarTransition.Automatic || it.isExit }
    val explicit = group.items.firstNotNullOfOrNull { item -> item.bornFrom?.let { from.element(it)?.groupId } }
    val donor = from.groups.filter { it.side == group.side && it.sharedBackground && it.id != group.id }
      .minByOrNull { abs(it.leftDp + it.widthDp / 2f - group.leftDp - group.widthDp / 2f) }
    val neighbour = if (forced || !group.sharedBackground) null else explicit?.let { id -> from.groups.firstOrNull { it.id == id && it.side == group.side } } ?: donor
    result += FluidToolbarGroupDiff(emptyList(), listOf(group), if (neighbour == null) FluidToolbarGroupChange.Materialize else
      FluidToolbarGroupChange.BornFromNeighbour, neighbour?.id)
  }
  for (group in remainingOld) {
    val forced = group.items.any { it.transition != FluidToolbarTransition.Automatic || it.isExit }
    val neighbour = if (forced || !group.sharedBackground) null else to.groups.filter { it.side == group.side && it.sharedBackground && it.id != group.id }
      .minByOrNull { abs(it.leftDp + it.widthDp / 2f - group.leftDp - group.widthDp / 2f) }
    result += FluidToolbarGroupDiff(listOf(group), emptyList(), if (neighbour == null) FluidToolbarGroupChange.Dematerialize else
      FluidToolbarGroupChange.MergeIntoNeighbour, neighbour?.id)
  }
  return result
}

/**
 * Corrispondenze, non fotogrammi: il piano F3 possiede molle, colli, distanze dal vicino e catture.
 * D1 tiene ferma la barra durante il gesto, D2 conserva il contenuto dei gruppi che si dividono/uniscono,
 * D3 dichiara la parentela, D4 tratta il cambio scheda come uno stato senza verso.
 * Un grafo molti-a-molti non fa attraversare i gruppi: i membri trasferiti rinascono (M10).
 */
internal fun diffFluidToolbar(
  from: FluidToolbarResolved,
  to: FluidToolbarResolved,
  trigger: FluidToolbarTrigger = FluidToolbarTrigger.State,
  motion: FluidToolbarMotion = FluidToolbarMotion.Full,
): FluidToolbarDiff {
  if (trigger == FluidToolbarTrigger.PredictiveBackPreview || trigger == FluidToolbarTrigger.PredictiveBackCancelled) {
    return FluidToolbarDiff(emptyList(), emptyList(), emptyList(), emptyList(), false, motion)
  }
  val pairs = toolbarPairs(from, to)
  val rawGroups = toolbarGroupDiffs(from, to, pairs)
  val shapedSides = mutableSetOf<FluidToolbarSide>()
  val groups = rawGroups.map { group ->
    val side = (group.to.firstOrNull() ?: group.from.first()).side
    val shapeEvent = group.change !in listOf(FluidToolbarGroupChange.Matched, FluidToolbarGroupChange.Materialize, FluidToolbarGroupChange.Dematerialize)
    val animate = motion == FluidToolbarMotion.Full && group.from.plus(group.to).flatMap { it.items }
      .none { it.transition == FluidToolbarTransition.Identity } && (!shapeEvent || shapedSides.add(side))
    group.copy(animateShape = animate)
  }
  val continuations = mutableMapOf<FluidToolbarGroupId, FluidToolbarGroupId>()
  for (graph in groups.filter { it.change == FluidToolbarGroupChange.Reconfigured }) {
    val candidates = pairs.filter { pair -> graph.from.any { it.id == pair.from.groupId } && graph.to.any { it.id == pair.to.groupId } }
      .groupingBy { it.from.groupId!! to it.to.groupId!! }.eachCount().entries
      .sortedByDescending { it.value }
    for ((edge, _) in candidates) {
      if (edge.first !in continuations && edge.second !in continuations.values) continuations[edge.first] = edge.second
    }
  }
  val result = ArrayList<FluidToolbarElementDiff>()
  fun content(change: FluidToolbarContentChange, a: FluidToolbarElement?, b: FluidToolbarElement?): FluidToolbarContentChange = when {
    motion == FluidToolbarMotion.Identity || a?.item?.transition == FluidToolbarTransition.Identity || b?.item?.transition == FluidToolbarTransition.Identity -> FluidToolbarContentChange.Identity
    change == FluidToolbarContentChange.None -> change
    motion == FluidToolbarMotion.Reduced -> FluidToolbarContentChange.Crossfade
    else -> change
  }
  fun unmatched(element: FluidToolbarElement, born: Boolean, forceLens: Boolean = false) {
    val state = if (born) from else to
    val ownState = if (born) to else from
    val groupDiff = groups.firstOrNull { g -> (if (born) g.to else g.from).any { it.id == element.groupId } }
    val inside = groupDiff != null && groupDiff.from.isNotEmpty() && groupDiff.to.isNotEmpty()
    val sameGlass = if (born) groupDiff?.from else groupDiff?.to
    val explicit = element.item.bornFrom?.let { state.element(it) }?.takeIf { !it.inOverflow && it.side == element.side }
    val neighbours = state.elements.filter { !it.inOverflow && it.side == element.side && it.item.id != element.item.id }
    val nearby = neighbours.filter { it.groupId in sameGlass.orEmpty().map { group -> group.id } }
      .ifEmpty { neighbours }.minByOrNull { abs(it.centerDp - element.centerDp) }
    val shapeAllowed = groupDiff?.animateShape == true
    val forced = forceLens || element.item.transition != FluidToolbarTransition.Automatic || element.item.isExit ||
      !element.item.sharedBackground || (!inside && !shapeAllowed) ||
      state.elements.any { it.item.matchId != null && it.item.matchId == element.item.matchId && it.side != element.side }
    val neighbour = if (forced) null else explicit ?: nearby
    val change = when {
      element.inOverflow -> FluidToolbarElementChange.InOverflow
      motion == FluidToolbarMotion.Identity || element.item.transition == FluidToolbarTransition.Identity -> FluidToolbarElementChange.Identity
      neighbour != null -> if (born) FluidToolbarElementChange.BornFromNeighbour else FluidToolbarElementChange.MergeIntoNeighbour
      else -> if (born) FluidToolbarElementChange.Materialize else FluidToolbarElementChange.Dematerialize
    }
    val a = if (born) null else element
    val b = if (born) element else null
    result += FluidToolbarElementDiff(a, b, change,
      content(if (element.inOverflow) FluidToolbarContentChange.None else if (born) FluidToolbarContentChange.BlurAppear else FluidToolbarContentChange.BlurDisappear, a, b),
      neighbourId = neighbour?.item?.id, insideGroup = inside && ownState.groups.any { it.id == element.groupId })
  }
  for (pair in pairs) {
    val a = pair.from
    val b = pair.to
    val graph = groups.firstOrNull { it.from.any { group -> group.id == a.groupId } && it.to.any { group -> group.id == b.groupId } }
    val crossing = pairs.any { other -> other !== pair && !a.inOverflow && !b.inOverflow && !other.from.inOverflow && !other.to.inOverflow &&
      a.side == other.from.side && b.side == other.to.side &&
      (a.centerDp - other.from.centerDp) * (b.centerDp - other.to.centerDp) < 0f }
    val transfer = graph?.change == FluidToolbarGroupChange.Reconfigured && continuations[a.groupId] != b.groupId
    // La larghezza può cambiare molto (ricerca/testo) restando ancorata allo stesso bordo: non è un viaggio lungo.
    fun anchor(element: FluidToolbarElement): Float = when (element.side) {
      FluidToolbarSide.Leading -> element.leftDp
      FluidToolbarSide.Center -> element.centerDp
      FluidToolbarSide.Trailing -> element.leftDp + element.widthDp
    }
    val tooFar = !a.inOverflow && !b.inOverflow && abs(anchor(a) - anchor(b)) > from.metrics.buttonWidthDp * 1.5f
    if (!a.inOverflow && !b.inOverflow && (crossing || transfer || tooFar ||
        a.item.transition == FluidToolbarTransition.Materialize || b.item.transition == FluidToolbarTransition.Materialize)) {
      unmatched(a, born = false, forceLens = true)
      unmatched(b, born = true, forceLens = true)
      continue
    }
    val identity = motion == FluidToolbarMotion.Identity || a.item.transition == FluidToolbarTransition.Identity || b.item.transition == FluidToolbarTransition.Identity
    val change = when {
      identity -> FluidToolbarElementChange.Identity
      a.inOverflow && b.inOverflow -> FluidToolbarElementChange.InOverflow
      a.inOverflow -> FluidToolbarElementChange.OutOfOverflow
      b.inOverflow -> FluidToolbarElementChange.IntoOverflow
      else -> FluidToolbarElementChange.Matched
    }
    val changed = a.item.content != b.item.content || a.item.tintArgb != b.item.tintArgb || a.item.selected != b.item.selected ||
      a.item.isPrimary != b.item.isPrimary
    result += FluidToolbarElementDiff(a, b, change, content(when {
      a.inOverflow && b.inOverflow -> FluidToolbarContentChange.None
      a.inOverflow -> FluidToolbarContentChange.BlurAppear
      b.inOverflow -> FluidToolbarContentChange.BlurDisappear
      changed -> FluidToolbarContentChange.BlurReplace
      else -> FluidToolbarContentChange.None
    }, a, b), pair.by, when (change) {
      FluidToolbarElementChange.IntoOverflow -> to.overflowElement?.item?.id
      FluidToolbarElementChange.OutOfOverflow -> from.overflowElement?.item?.id
      else -> null
    })
  }
  from.elements.filter { a -> pairs.none { it.from == a } }.forEach { unmatched(it, born = false) }
  to.elements.filter { b -> pairs.none { it.to == b } }.forEach { unmatched(it, born = true) }
  val titles = ArrayList<FluidToolbarTitleDiff>()
  fun title(kind: FluidToolbarTitleKind, old: String?, new: String?) {
    if (old != new) titles += FluidToolbarTitleDiff(kind, when {
      kind == FluidToolbarTitleKind.Large -> FluidToolbarTitleChange.PageContent
      old == null -> FluidToolbarTitleChange.BlurAppear
      new == null -> FluidToolbarTitleChange.BlurDisappear
      else -> FluidToolbarTitleChange.BlurReplace
    }, old, new)
  }
  title(FluidToolbarTitleKind.Inline, from.titles.inline, to.titles.inline)
  title(FluidToolbarTitleKind.Large, from.titles.large, to.titles.large)
  title(FluidToolbarTitleKind.Subtitle, from.titles.subtitle, to.titles.subtitle)
  val presentations = ArrayList<FluidToolbarPresentationDiff>()
  if (from.presentation != to.presentation) {
    fun presentation(state: FluidToolbarResolved, opening: Boolean) {
      val request = state.presentation ?: return
      val group = state.element(request.sourceId)?.groupId ?: return
      presentations += FluidToolbarPresentationDiff(request, group, opening)
    }
    presentation(from, false)
    presentation(to, true)
  }
  return FluidToolbarDiff(result, groups, titles, presentations, true, motion)
}
