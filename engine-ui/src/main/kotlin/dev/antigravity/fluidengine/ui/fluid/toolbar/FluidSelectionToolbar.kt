package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMarkDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMode
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.Checklist
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.DriveFileMove
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FolderZip
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MarkEmailRead
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoAlbum
import dev.antigravity.fluidengine.ui.fluid.icons.VisibilityOff
import kotlin.math.max
import kotlin.math.roundToInt

// ================================================================================================================
// La barra delle azioni di selezione (piano «iOS in Android», F6B.1)
//
// La pagina scrive lo stato con un `if`, come per ogni altra barra dichiarata: «sfoglia» con il tasto «Seleziona»,
// «seleziona» con `selection { }`. La barra fa il resto con il suo modello: «Seleziona» e «Fine» sono lo stesso tasto
// (lo stesso `id`, lo stesso vetro che cambia larghezza e scambia la scritta sfocandola), il conteggio prende il posto
// del titolo agganciato, «Seleziona tutto» nasce a sinistra al posto dell'indietro, le azioni scendono nella barra in
// basso (o restano in alto). È `EditMode` + `List(selection:)` + `ToolbarItemGroup(placement: .bottomBar)` di SwiftUI.
// ================================================================================================================

/**
 * Dove stanno le azioni di una selezione dichiarata con [selection].
 *
 * Non è un `enum` di proposito (regola 3.11 del piano): le posizioni nuove si aggiungono come valori nuovi di questa
 * classe senza rompere i `when` delle app.
 *
 * - [ReplacingTabBar]: in basso, **al posto della tab bar**. La schermata pubblica la sua barra in basso alla shell con il
 *   segno «sostituisce la tab bar» ([dev.antigravity.fluidengine.ui.fluid.FluidChromeController]); finché la tab bar
 *   non la prende (F6B.2) e dove una tab bar non c'è (una pagina senza schede, il pannello della lista su un tablet con
 *   la barra laterale) la disegna la schermata, nella sua barra in basso. È il caso di Mail, Note e File.
 * - [AboveTabBar]: in basso, sopra la tab bar, che resta.
 * - [Top]: nella barra in alto, accanto a «Fine»: per le pagine senza una barra in basso.
 * - [Floating]: in basso, una capsula che galleggia sul contenuto e sopra la tab bar, che resta; la pagina non le
 *   riserva spazio in fondo alla lista (la griglia di Gallery le passa sotto).
 */
@JvmInline
value class FluidSelectionBarPlacement private constructor(private val value: Int) {

  /** Le azioni stanno nella barra in basso (tutte le posizioni tranne [Top]). */
  internal val atBottom: Boolean get() = value != TopValue

  /** La barra in basso chiede di prendere il posto della tab bar. */
  internal val replacesTabBar: Boolean get() = value == ReplacingValue

  /** La lista lascia in fondo lo spazio della barra delle azioni. */
  internal val reservesSpace: Boolean get() = value != FloatingValue

  override fun toString(): String = when (value) {
    ReplacingValue -> "ReplacingTabBar"
    AboveValue -> "AboveTabBar"
    TopValue -> "Top"
    else -> "Floating"
  }

  companion object {
    private const val ReplacingValue = 0
    private const val AboveValue = 1
    private const val TopValue = 2
    private const val FloatingValue = 3

    /** In basso, al posto della tab bar (la posizione di serie). */
    val ReplacingTabBar: FluidSelectionBarPlacement = FluidSelectionBarPlacement(ReplacingValue)

    /** In basso, sopra la tab bar. */
    val AboveTabBar: FluidSelectionBarPlacement = FluidSelectionBarPlacement(AboveValue)

    /** Nella barra in alto. */
    val Top: FluidSelectionBarPlacement = FluidSelectionBarPlacement(TopValue)

    /** In basso, in una capsula che galleggia sul contenuto. */
    val Floating: FluidSelectionBarPlacement = FluidSelectionBarPlacement(FloatingValue)
  }
}

/**
 * Le identità che la selezione dà ai suoi tasti. Contano fra due stati della barra: **lo stesso id è lo stesso vetro**.
 * «Seleziona» ([selectButton]) e «Fine» hanno di serie [Toggle], così il vetro che diceva «Seleziona» si stringe e dice
 * «Fine» (lo scambio sfocato di R7 dentro un vetro che cambia larghezza, R4 B2) invece di sparire e rinascere.
 */
object FluidSelectionToolbarIds {
  /** «Seleziona» e «Fine»: lo stesso tasto che cambia scritta. */
  val Toggle: Any = FluidToolbarRoleId("selection")

  /** «Seleziona tutto» e «Deseleziona». */
  val SelectAll: Any = FluidToolbarRoleId("selection-all")

  /** Il conteggio al centro della barra, al posto del titolo agganciato. */
  val Count: Any = FluidToolbarRoleId("selection-count")

  /** Il gruppo delle azioni. */
  val Actions: Any = FluidToolbarRoleId("selection-actions")
}

/**
 * Le parole della selezione, di serie in italiano. Un'app in un'altra lingua, o che vuole «3 note» invece di
 * «3 selezionati», passa le sue.
 *
 * @property count il titolo con N elementi scelti (N ≥ 1).
 * @property none il titolo a zero: la selezione è aperta ma niente è scelto ancora.
 * @property all il titolo di «tutto» quando il numero non si conosce (una lista paginata senza `count`).
 * @property allBut il titolo di «tutto tranne N» quando il numero non si conosce.
 */
@Immutable
data class FluidSelectionLabels(
  val select: String = "Seleziona",
  val done: String = "Fine",
  val selectAll: String = "Seleziona tutto",
  val deselectAll: String = "Deseleziona",
  val none: String = "0 selezionati",
  val count: (Int) -> String = { if (it == 1) "1 selezionato" else "$it selezionati" },
  val all: String = "Tutti selezionati",
  val allBut: (Int) -> String = { "Tutti tranne $it" },
)

/**
 * Il tasto che apre la selezione: una scritta, di serie «Seleziona». Ha di serie lo stesso id di «Fine»
 * ([FluidSelectionToolbarIds.Toggle]): quando la pagina passa a [selection] è lo stesso vetro che cambia scritta.
 */
fun FluidToolbarScope.selectButton(
  onClick: () -> Unit,
  label: String = FluidSelectionDefaultLabels.select,
  id: Any = FluidSelectionToolbarIds.Toggle,
  placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
  enabled: Boolean = true,
) {
  text(id, label, onClick, placement = placement, enabled = enabled)
}

/**
 * La barra in selezione, in una riga (F6B.1). Si scrive al posto della barra di «sfoglia», con un `if`:
 *
 * ```
 * FluidScreen(title = "Posta in arrivo", toolbar = {
 *   if (selecting) {
 *     selection(state, count = state.countIn(total), onSelectAll = { state.selectAll(snapshot) },
 *       onDone = { selecting = false },
 *       actions = FluidSelectionActions.mail(onMove = move, onArchive = archive, onDelete = delete))
 *   } else {
 *     selectButton(onClick = { selecting = true })
 *   }
 * }) { … }
 * ```
 *
 * Che cosa dichiara:
 * - **il conteggio** al centro della barra, al posto del titolo agganciato («3 selezionati», [FluidSelectionLabels]):
 *   quando cambia, la scritta si scambia sfocandosi. Per TalkBack è un'area viva: ogni cambio si annuncia;
 * - **«Seleziona tutto»** a sinistra, se c'è [onSelectAll]: un tondo col simbolo dell'elenco spuntato, che con tutto
 *   scelto (`AllExcept` senza esclusioni) si accende e diventa **«Deseleziona»** (svuota la selezione e la lascia
 *   aperta). Un tondo e non una scritta perché la barra ha tre gruppi e centra il conteggio: «Seleziona tutto» per
 *   esteso spingerebbe il conteggio in «Altro» su un telefono (R5 B11). Le parole sono il suo nome per TalkBack e la sua
 *   voce nel menu. Su una lista paginata «Seleziona tutto» resta la forma `AllExcept` dello stato
 *   ([FluidSelectionState.selectAll]): l'engine non conta e non carica righe;
 * - **«Fine»** a destra, che esce dalla selezione: svuota [state] e chiama [onDone];
 * - **le azioni** ([actions]: tasti, gruppi, spaziatori, priorità e trabocco della barra; i preset in
 *   [FluidSelectionActions]) nella posizione [placement]. Le azioni dei preset si spengono da sole a zero elementi
 *   ([selectionHasItems]);
 * - **il back della selezione**: con una selezione dichiarata il back (il tasto, il gesto del sistema, il dito dal
 *   bordo) esce dalla selezione e non dalla pagina, come «Fine». Niente cambia finché il gesto indietro non è
 *   confermato (D1): un gesto annullato lascia la selezione com'era. Il tasto indietro della pagina non c'è: a sinistra
 *   sta «Seleziona tutto».
 *
 * Una selezione sola per barra. Le azioni in basso non possono contenere un'azione principale (`primary`, `confirm`):
 * il modello ne vuole una sola, e la barra la riserva alle pagine. Un'azione con `role = FluidButtonRole.Destructive`
 * dentro una selezione in basso passa `placement = FluidToolbarPlacement.BottomBar`: il ruolo distruttivo, da solo, la
 * metterebbe in alto (è il posto di `DestructiveAction`). I preset lo fanno già.
 *
 * Funziona in `FluidScreen(toolbar = …)` (anche dentro un host di pagine) e in `FluidPageChrome(toolbar = …)`.
 *
 * @param state lo stato della selezione: «Fine», il back e «Deseleziona» lo svuotano.
 * @param count quante righe sono scelte, se l'app lo sa ([FluidSelectionState.countIn] col totale della query). Null:
 *   lo conta lo stato quando può (forma esplicita), altrimenti il titolo dice «Tutti selezionati».
 * @param onSelectAll «Seleziona tutto»; null, il tasto non c'è.
 * @param onDone l'uscita dalla selezione (dopo che [state] è stato svuotato): la pagina torna a «sfoglia».
 * @param placement dove stanno le azioni ([FluidSelectionBarPlacement]).
 * @param labels le parole.
 * @param doneId l'identità di «Fine»: di serie quella di [selectButton].
 */
fun FluidToolbarScope.selection(
  state: FluidSelectionState<*>,
  count: Int?,
  onSelectAll: (() -> Unit)?,
  onDone: () -> Unit,
  placement: FluidSelectionBarPlacement = FluidSelectionBarPlacement.ReplacingTabBar,
  labels: FluidSelectionLabels = FluidSelectionDefaultLabels,
  doneId: Any = FluidSelectionToolbarIds.Toggle,
  actions: FluidToolbarScope.() -> Unit,
) {
  require(entries.none { it.selectionCarrier() != null }) { "una sola selezione per barra" }
  val shown = fluidSelectionCount(state.mode, count)
  val allSelected = fluidSelectionAllSelected(state.mode)
  val title = fluidSelectionTitle(state.mode, count, labels)
  val exit: () -> Unit = {
    state.clear()
    onDone()
  }
  val declaration = FluidSelectionDeclaration(placement, title, shown, allSelected, exit)
  // Il portatore: un gruppo vuoto, inerte per il modello (niente vetri, niente spazio, niente tasti), che fa viaggiare
  // la selezione con la dichiarazione fino alla schermata, all'host delle pagine e alla shell.
  entries += FluidToolbarGroupEntry(
    id = FluidSelectionCarrier(declaration),
    placement = FluidToolbarPlacement.Automatic,
    priority = FluidToolbarPriority.Automatic,
    overflow = false,
    sharedBackground = true,
    children = emptyList(),
  )
  if (onSelectAll != null) {
    // Un tondo e non una scritta: la barra ha tre gruppi e centra il conteggio (R5 B11), e «Seleziona tutto» scritto
    // per esteso lo spingerebbe in «Altro» su ogni telefono. Con tutto scelto lo stesso tasto è acceso (la pellicola
    // della selezione) e dice «Deseleziona». Le parole restano il suo nome e la voce del menu se ci finisce.
    button(
      id = FluidSelectionToolbarIds.SelectAll,
      icon = FluidIcons.Checklist,
      label = if (allSelected) labels.deselectAll else labels.selectAll,
      onClick = if (allSelected) ({ state.clear() }) else onSelectAll,
      placement = FluidToolbarPlacement.Leading,
      priority = FluidToolbarPriority.High,
      selected = allSelected,
    )
  }
  val previous = CurrentSelection.get()
  CurrentSelection.set(declaration)
  try {
    group(
      id = FluidSelectionToolbarIds.Actions,
      placement = if (placement.atBottom) FluidToolbarPlacement.BottomBar else FluidToolbarPlacement.Automatic,
      content = actions,
    )
  } finally {
    CurrentSelection.set(previous)
  }
  // Il conteggio al centro, senza vetro: prende il posto del titolo agganciato (il titolo cede il posto a ciò che sta al
  // centro). Il nome per TalkBack lo dà l'area viva della schermata, non questa faccia: due letture sarebbero una di troppo.
  // Dopo le azioni e prima di «Fine» di proposito: quando il posto non basta la barra fa uscire prima le azioni, poi
  // «Seleziona tutto», poi il conteggio, e «Fine» per ultimo (a parità di priorità, fra centro e destra esce prima chi
  // viene prima; a sinistra il più interno). Come in iOS, i tasti contano più del titolo.
  entries += FluidToolbarButtonEntry(
    id = FluidSelectionToolbarIds.Count,
    role = FluidToolbarRole.Text,
    placement = FluidToolbarPlacement.Principal,
    priority = FluidToolbarPriority.High,
    text = title,
    label = null,
    onClick = null,
    sharedBackground = false,
  )
  text(id = doneId, text = labels.done, onClick = exit, priority = FluidToolbarPriority.High)
}

/**
 * Se c'è almeno un elemento scelto nella selezione che si sta dichiarando: vero fuori da [selection]. Le azioni dei
 * preset lo leggono per spegnersi a zero; un'azione su misura fa lo stesso con `enabled = selectionHasItems`.
 */
val FluidToolbarScope.selectionHasItems: Boolean
  get() = CurrentSelection.get()?.hasItems ?: true

/** Le parole delle azioni dei preset, di serie in italiano. */
@Immutable
data class FluidSelectionActionLabels(
  val share: String = "Condividi",
  val delete: String = "Elimina",
  val move: String = "Sposta",
  val archive: String = "Archivia",
  val markRead: String = "Segna come letti",
  val favorite: String = "Preferiti",
  val addToAlbum: String = "Aggiungi all'album",
  val hide: String = "Nascondi",
  val duplicate: String = "Duplica",
  val compress: String = "Comprimi",
)

/**
 * I preset delle azioni di selezione (F6B.1): funzioni che ricevono le callback e scrivono azioni, priorità e trabocco
 * per [selection]. Un'azione con la callback null non c'è. Si spengono da sole a zero elementi scelti.
 *
 * **In basso** le azioni si dividono ai due estremi della barra con uno spazio flessibile, in tre gruppi al più, «Altro»
 * compreso (il tetto della barra, R5 B11): quando il posto non basta escono prima le azioni a priorità bassa. **In alto**
 * ([FluidSelectionBarPlacement.Top]) la barra ha già il conteggio e «Fine», e ancora tre gruppi in tutto: le azioni
 * finiscono in «Altro», con «Seleziona tutto» se non c'è posto, e restano raggiungibili da lì e da TalkBack.
 *
 * Nessuna azione dei preset ha priorità alta: in alto i posti della selezione («Seleziona tutto», il conteggio, «Fine»)
 * restano finché c'è un'azione da far uscire.
 *
 * Le icone e l'ordine vengono dalle barre di selezione di iOS 26 (Mail: segna, sposta, archivia, elimina; Foto:
 * condividi, preferiti, album, elimina; File: condividi, duplica, sposta, elimina, con «Altro»), viste nei fermi della
 * ricerca F6B.0 (`artifacts/liquid-glass/sessioni/f6b-0/ricerca-f6b.md`, §1): fiducia alta sull'anatomia; i gruppi e le
 * priorità sono una scelta dell'engine.
 */
object FluidSelectionActions {

  /** Mail: «Segna come letti» a sinistra; «Sposta» e «Archivia» insieme; «Elimina» col suo vetro, a destra. */
  fun mail(
    onDelete: () -> Unit,
    onArchive: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onMarkRead: (() -> Unit)? = null,
    labels: FluidSelectionActionLabels = FluidSelectionActionLabels(),
  ): FluidToolbarScope.() -> Unit = {
    val enabled = selectionHasItems
    val at = selectionActionPlacement()
    val bottom = at == FluidToolbarPlacement.BottomBar
    onMarkRead?.let {
      button("mark", FluidIcons.MarkEmailRead, labels.markRead, it, placement = at,
        priority = FluidToolbarPriority.Low, enabled = enabled)
      if (bottom) flexibleSpacer()
    }
    onMove?.let { button("move", FluidIcons.DriveFileMove, labels.move, it, placement = at, enabled = enabled) }
    onArchive?.let { button("archive", FluidIcons.Archive, labels.archive, it, placement = at, enabled = enabled) }
    if (bottom && (onMove != null || onArchive != null)) spacer()
    button("delete", FluidIcons.Delete, labels.delete, onDelete, placement = at, role = FluidButtonRole.Destructive,
      enabled = enabled)
  }

  /**
   * Gallery (Foto): «Condividi» a sinistra; «Preferiti», «Aggiungi all'album» ed «Elimina» insieme a destra;
   * «Nascondi» e «Duplica» sempre in «Altro».
   */
  fun gallery(
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onAddToAlbum: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    labels: FluidSelectionActionLabels = FluidSelectionActionLabels(),
  ): FluidToolbarScope.() -> Unit = {
    val enabled = selectionHasItems
    val at = selectionActionPlacement()
    button("share", FluidIcons.IosShare, labels.share, onShare, placement = at, enabled = enabled)
    if (at == FluidToolbarPlacement.BottomBar) flexibleSpacer()
    onFavorite?.let { button("favorite", FluidIcons.Favorite, labels.favorite, it, placement = at, enabled = enabled) }
    onAddToAlbum?.let {
      button("album", FluidIcons.PhotoAlbum, labels.addToAlbum, it, placement = at,
        priority = FluidToolbarPriority.Low, enabled = enabled)
    }
    button("delete", FluidIcons.Delete, labels.delete, onDelete, placement = at, role = FluidButtonRole.Destructive,
      enabled = enabled)
    if (onHide != null || onDuplicate != null) overflow {
      onHide?.let { button("hide", FluidIcons.VisibilityOff, labels.hide, it, placement = at, enabled = enabled) }
      onDuplicate?.let { button("duplicate", FluidIcons.ContentCopy, labels.duplicate, it, placement = at, enabled = enabled) }
    }
  }

  /** File: «Condividi» e «Duplica» a sinistra; «Sposta» ed «Elimina» a destra; «Comprimi» sempre in «Altro». */
  fun files(
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    labels: FluidSelectionActionLabels = FluidSelectionActionLabels(),
  ): FluidToolbarScope.() -> Unit = {
    val enabled = selectionHasItems
    val at = selectionActionPlacement()
    button("share", FluidIcons.IosShare, labels.share, onShare, placement = at, enabled = enabled)
    onDuplicate?.let {
      button("duplicate", FluidIcons.ContentCopy, labels.duplicate, it, placement = at,
        priority = FluidToolbarPriority.Low, enabled = enabled)
    }
    if (at == FluidToolbarPlacement.BottomBar) flexibleSpacer()
    button("move", FluidIcons.DriveFileMove, labels.move, onMove, placement = at, enabled = enabled)
    button("delete", FluidIcons.Delete, labels.delete, onDelete, placement = at, role = FluidButtonRole.Destructive,
      enabled = enabled)
    onCompress?.let { compress ->
      overflow { button("compress", FluidIcons.FolderZip, labels.compress, compress, placement = at, enabled = enabled) }
    }
  }

  /** Solo «Elimina», a destra (una lista di cronologia, di bozze). */
  fun deleteOnly(
    onDelete: () -> Unit,
    label: String = FluidSelectionActionLabels().delete,
  ): FluidToolbarScope.() -> Unit = {
    button("delete", FluidIcons.Delete, label, onDelete, placement = selectionActionPlacement(),
      role = FluidButtonRole.Destructive, enabled = selectionHasItems)
  }
}

/** Le parole di serie, una volta sola. */
internal val FluidSelectionDefaultLabels = FluidSelectionLabels()

/**
 * La posizione esplicita di un'azione dei preset: la barra in basso per le selezioni in basso (un ruolo distruttivo,
 * da solo, la metterebbe in alto), quella del gruppo in alto.
 */
private fun selectionActionPlacement(): FluidToolbarPlacement =
  if (CurrentSelection.get()?.placement?.atBottom == false) FluidToolbarPlacement.Automatic else FluidToolbarPlacement.BottomBar

/**
 * La selezione che si sta dichiarando, per i preset e per [selectionHasItems]. Le azioni girano dentro [selection], in
 * modo sincrono: un `ThreadLocal` perché due composizioni su due thread non si leggano a vicenda.
 */
private val CurrentSelection = ThreadLocal<FluidSelectionDeclaration?>()

/**
 * Ciò che la barra sa di una selezione dichiarata: dove stanno le azioni, il titolo, il numero, se è scelto tutto e
 * come si esce. Viaggia con la [FluidToolbarDeclaration] ([selection]).
 */
@Stable
internal class FluidSelectionDeclaration(
  val placement: FluidSelectionBarPlacement,
  /** Il titolo che la barra mostra al centro e che TalkBack annuncia. */
  val title: String,
  /** Il numero scelto, se si conosce. */
  val count: Int?,
  val allSelected: Boolean,
  /** Svuota lo stato e chiama `onDone`: «Fine» e il back. */
  val exit: () -> Unit,
) {
  /** C'è almeno un elemento scelto, o non si sa (tutto tranne qualcuno, senza il totale). */
  val hasItems: Boolean get() = count == null || count > 0
}

/**
 * La chiave del gruppo che porta la selezione. Uguale a ogni altra della sua classe: due composizioni della stessa
 * barra danno lo stesso modello anche se le callback dentro sono nuove, e il modello non vede la selezione cambiare
 * quando cambiano solo le callback.
 */
private class FluidSelectionCarrier(val selection: FluidSelectionDeclaration) {
  override fun equals(other: Any?): Boolean = other is FluidSelectionCarrier
  override fun hashCode(): Int = 0x5E1EC7
  override fun toString(): String = "fluid:selection"
}

private fun FluidToolbarEntry.selectionCarrier(): FluidSelectionCarrier? =
  (this as? FluidToolbarGroupEntry)?.id as? FluidSelectionCarrier

/** La selezione dichiarata in questa barra, o null. */
internal val FluidToolbarDeclaration.selection: FluidSelectionDeclaration?
  get() = entries.firstNotNullOfOrNull { it.selectionCarrier()?.selection }

/** La barra in basso di questa dichiarazione chiede il posto della tab bar. */
internal val FluidToolbarDeclaration.bottomReplacesTabBar: Boolean
  get() = hasBottomBar && selection?.placement?.replacesTabBar == true

/** La lista deve lasciare in fondo lo spazio della barra in basso di questa dichiarazione. */
internal val FluidToolbarDeclaration.bottomReservesSpace: Boolean
  get() = hasBottomBar && selection?.placement?.reservesSpace != false

/**
 * La barra di una schermata (`FluidScreen`, `FluidPageChrome`). Come [buildFluidToolbar], tranne una cosa: con una
 * [selection] dichiarata la barra **non** aggiunge l'indietro della schermata. In selezione il back esce dalla selezione
 * ([FluidSelectionBackEffect]) e a sinistra sta «Seleziona tutto»: un chevron che togliesse la pagina direbbe il
 * contrario del back. Senza selezione il risultato è quello di [buildFluidToolbar], voce per voce.
 */
internal fun buildFluidScreenToolbar(onBack: (() -> Unit)?, block: FluidToolbarScope.() -> Unit): FluidToolbarDeclaration {
  val declared = buildFluidToolbar(null, block)
  if (onBack == null || declared.selection != null) return declared
  return buildFluidToolbar(onBack) {
    entries += declared.entries
    titleMode = declared.titleMode
    subtitle = declared.subtitle
    search = declared.search
    overflowLabel = declared.overflowLabel
  }
}

/** Il numero scelto: quello dell'app, o quello che lo stato sa contare da solo (la forma esplicita). */
internal fun fluidSelectionCount(mode: FluidSelectionMode<*>, count: Int?): Int? = count?.coerceAtLeast(0) ?: when (mode) {
  is FluidSelectionMode.Explicit -> mode.keys.size
  is FluidSelectionMode.AllExcept -> null
}

/** È scelto tutto: «Seleziona tutto» senza esclusioni. L'engine non conosce il totale, quindi la forma esplicita no. */
internal fun fluidSelectionAllSelected(mode: FluidSelectionMode<*>): Boolean =
  mode is FluidSelectionMode.AllExcept && mode.excluded.isEmpty()

/** Il titolo della barra in selezione. */
internal fun fluidSelectionTitle(mode: FluidSelectionMode<*>, count: Int?, labels: FluidSelectionLabels): String {
  val shown = fluidSelectionCount(mode, count)
  return when {
    shown == null && mode is FluidSelectionMode.AllExcept ->
      if (mode.excluded.isEmpty()) labels.all else labels.allBut(mode.excluded.size)
    shown == null || shown == 0 -> labels.none
    else -> labels.count(shown)
  }
}

// ================================================================================================================
// Il back della selezione (D1)
// ================================================================================================================

internal object FluidSelectionBackInfo : NavigationEventInfo()

/**
 * Il back che esce dalla selezione: un gestore del dispatcher di navigationevent, lo stesso su cui stanno l'host delle
 * pagine e i `BackHandler` dell'app.
 *
 * **D1: niente cambia finché il gesto non è confermato.** Inizio, avanzamento e annullo del back predittivo non fanno
 * niente: la selezione, il titolo e la barra restano come sono mentre il dito decide. Solo il completamento esce. È
 * anche quello che fa un `BackHandler`, che però non si può provare senza Compose: qui la regola è scritta e un test la
 * prova col dispatcher vero ([dev.antigravity.fluidengine.ui.fluid.toolbar], `FluidSelectionToolbarTest`).
 *
 * Il dito dentro l'app (il bordo di un host di pagine) segue la stessa strada: l'host porta la pagina sotto il dito e,
 * al rilascio oltre la soglia, manda al dispatcher lo stesso «indietro completato»; risponde questo gestore, la pagina
 * torna al suo posto con la molla dell'annullo, e la selezione si chiude.
 */
internal class FluidSelectionBackGesture(enabled: Boolean = true, onExit: () -> Unit) :
  NavigationEventHandler<FluidSelectionBackInfo>(FluidSelectionBackInfo, enabled) {

  var onExit: () -> Unit = onExit

  override fun onBackStarted(event: NavigationEvent) = Unit

  override fun onBackProgressed(event: NavigationEvent) = Unit

  override fun onBackCancelled() = Unit

  override fun onBackCompleted() {
    onExit()
  }
}

/**
 * Compone il back della selezione: [onExit] al back confermato, finché [enabled].
 *
 * Si registra come un `BackHandler` (sul dispatcher di navigationevent della composizione, quando il ciclo di vita parte
 * e fino a quando si ferma), quindi rispetta l'ordine del back dell'engine: menu → pannello → selezione → pagina. Va
 * composto quando la selezione si apre, non prima: entra allora nella pila, sopra la pagina e l'host, sotto i menu che
 * si apriranno dopo. Senza un dispatcher di navigationevent ripiega sul `BackHandler`.
 */
@Composable
internal fun FluidSelectionBackEffect(enabled: Boolean, onExit: () -> Unit) {
  val current by rememberUpdatedState(onExit)
  val gesture = remember { FluidSelectionBackGesture(enabled) { current() } }
  SideEffect { gesture.isBackEnabled = enabled }
  val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
  if (dispatcher == null) {
    BackHandler(enabled = enabled) { current() }
    return
  }
  LifecycleStartEffect(gesture, dispatcher) {
    dispatcher.addHandler(gesture)
    onStopOrDispose { gesture.remove() }
  }
}

// ================================================================================================================
// Il titolo che cede il posto al conteggio, l'annuncio, il segno nelle righe
// ================================================================================================================

/**
 * Quanto il titolo agganciato ha lasciato il posto a ciò che sta al centro della barra (il conteggio della selezione):
 * 0 il titolo c'è, 1 è uscito. Corre sul tempo dello scambio del contenuto (R7: [GlassContentDefaults.DurationMs]),
 * lineare: la forma la dà la curva di chi esce ([fluidTitleHandoffLayer]). Al primo stato non si anima niente: una
 * pagina che nasce col centro occupato non ha un titolo da far uscire. Con il movimento ridotto cambia di colpo.
 */
@Composable
internal fun rememberFluidTitleHandoff(hidden: Boolean): State<Float> {
  val policy = LocalFluidMotionPolicy.current
  val progress = remember { Animatable(if (hidden) 1f else 0f) }
  LaunchedEffect(hidden, policy.reducedMotion) {
    val target = if (hidden) 1f else 0f
    if (policy.reducedMotion) {
      progress.snapTo(target)
    } else {
      val millis = (GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd * policy.durationScale).roundToInt()
      progress.animateTo(target, tween(millis.coerceAtLeast(0), easing = LinearEasing))
    }
  }
  return progress.asState()
}

/**
 * Il titolo che esce sfocandosi (R7: chi esce si sfoca, rimpicciolisce appena e svanisce) mentre il conteggio entra al
 * centro con lo scambio della barra. [progress] va da 0 a 1 su [GlassContentDefaults.ExitEnd] di T, la parte della
 * curva in cui chi esce c'è ancora; al ritorno la stessa curva all'indietro. [referencePx] è l'altezza della riga del
 * titolo, su cui si misura la sfocatura.
 */
internal fun GraphicsLayerScope.fluidTitleHandoffLayer(progress: Float, referencePx: Float) {
  if (progress <= 0f) return
  val u = progress.coerceIn(0f, 1f) * GlassContentDefaults.ExitEnd
  val blurAvailable = android.os.Build.VERSION.SDK_INT >= 31
  alpha *= GlassContentCurves.alpha(GlassContentRole.Exit, u, blurAvailable)
  val scale = GlassContentCurves.exitScale.at(u)
  scaleX *= scale
  scaleY *= scale
  if (blurAvailable) {
    val radius = glassContentBlurRadiusPx(
      glassContentSigmaPx(GlassContentCurves.blur(GlassContentRole.Exit, u) * GlassContentDefaults.PeakBlur, referencePx),
    )
    renderEffect = if (radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
  }
}

/**
 * L'annuncio del conteggio per TalkBack: un'area viva grande quanto il centro della barra, con il titolo della
 * selezione come nome. Ogni cambio del titolo si annuncia, con garbo (`Polite`), senza spostare il fuoco.
 */
@Composable
internal fun FluidSelectionAnnouncement(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier.semantics {
      liveRegion = LiveRegionMode.Polite
      contentDescription = text
    },
  )
}

/** Le misure del [FluidSelectionLeading]. */
object FluidSelectionLeadingDefaults {
  /** Il segno: quello della selezione multipla, quanto l'icona di una riga. */
  val MarkSize: Dp = FluidSelectionMarkDefaults.Size

  /**
   * Quanto spazio prende il segno in una riga che non ha niente nello spazio `leading`: il segno più 8 dp prima del
   * testo. **A occhio** (F6B.1, 21 novembre 2026): Mail di iOS sposta il testo della riga di circa un tondo e mezzo.
   */
  val InsetWithoutContent: Dp = 32.dp
}

/**
 * Lo spazio `leading` di una riga in una lista selezionabile (F6B.1): fuori selezione mostra [content] (la piastrella,
 * l'avatar); in selezione il segno ([FluidSelectionMark]) prende **il suo posto, nella stessa misura**, così la riga
 * non cambia altezza e la lista non salta. Il passaggio è lo scambio sfocato del contenuto (R7: chi esce si sfoca e
 * svanisce, chi entra arriva sfocato e si fa nitido), sul tempo della barra che cambia insieme.
 *
 * Senza [content] la riga non ha niente a sinistra fuori selezione: allora lo spazio si apre (il testo si sposta di
 * [FluidSelectionLeadingDefaults.InsetWithoutContent]) con lo stesso tempo, e il segno nasce dentro.
 *
 * In una `FluidListRow` va con `leadingTile = false` (la piastrella, se serve, la mette [content]):
 * ```
 * FluidListRow(title, subtitle, leadingTile = false, onClick = { if (selecting) state.toggle(id) else open(id) },
 *   leading = { FluidSelectionLeading(selecting, state.isSelected(id)) { FluidToneTile(tone) { Icon(…) } } })
 * ```
 */
@Composable
fun FluidSelectionLeading(
  selecting: Boolean,
  selected: Boolean,
  modifier: Modifier = Modifier,
  markSize: Dp = FluidSelectionLeadingDefaults.MarkSize,
  content: (@Composable () -> Unit)? = null,
) {
  val policy = LocalFluidMotionPolicy.current
  val progress = remember { Animatable(if (selecting) 1f else 0f) }
  LaunchedEffect(selecting, policy.reducedMotion) {
    val target = if (selecting) 1f else 0f
    if (policy.reducedMotion) {
      progress.snapTo(target)
    } else {
      val millis = (GlassContentDefaults.DurationMs * GlassContentDefaults.EnterEnd * policy.durationScale).roundToInt()
      progress.animateTo(target, tween(millis.coerceAtLeast(0), easing = LinearEasing))
    }
  }
  val inset = FluidSelectionLeadingDefaults.InsetWithoutContent
  // Il segno c'è da quando la selezione si apre a quando ha finito di uscire: letto così, la riga ricompone due volte
  // per passaggio, non a ogni fotogramma (una lista ne ha decine).
  val markShown by remember { derivedStateOf { progress.value > 0f } }
  Layout(
    modifier = modifier,
    content = {
      if (content != null) {
        Box(Modifier.graphicsLayer {
          // Chi esce quando la selezione si apre: lo stesso tempo, all'indietro quando si chiude.
          leadingSwapLayer(GlassContentRole.Exit, progress.value, markSize.toPx())
        }) { content() }
      }
      Box(Modifier.graphicsLayer {
        leadingSwapLayer(if (content != null) GlassContentRole.Enter else GlassContentRole.Born, progress.value, markSize.toPx())
      }) {
        if (selecting || markShown) FluidSelectionMark(selected = selected, size = markSize)
      }
    },
  ) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(loose) }
    val mark = placeables.last()
    val body = if (content != null) placeables.first() else null
    val width: Int
    val height: Int
    if (body != null) {
      // La misura è la più grande delle due, sempre: lo scambio non sposta il testo della riga.
      width = max(body.width, markSize.roundToPx())
      height = max(body.height, markSize.roundToPx())
    } else {
      // Lo spazio si apre col progresso: niente salti, e fuori selezione la riga è quella di prima, al pixel.
      width = (inset.roundToPx() * leadingOpening(progress.value)).roundToInt()
      height = markSize.roundToPx()
    }
    val w = constraints.constrainWidth(width)
    val h = constraints.constrainHeight(height)
    layout(w, h) {
      body?.place((w - body.width) / 2, (h - body.height) / 2)
      val markX = if (body != null) (w - mark.width) / 2 else 0
      mark.place(markX, (h - mark.height) / 2)
    }
  }
}

/** L'apertura dello spazio di una riga senza `leading`: una smoothstep sul tempo dello scambio, piena a T. */
internal fun leadingOpening(progress: Float): Float {
  val u = (progress * GlassContentDefaults.EnterEnd).coerceIn(0f, 1f)
  return u * u * (3f - 2f * u)
}

/**
 * Uno strato dello scambio nel segno della riga: [progress] 0 → 1 copre [GlassContentDefaults.EnterEnd] di T (chi
 * entra è nitido e pieno solo lì). Chi esce ([GlassContentRole.Exit]) è pieno a 0, chi entra a 1.
 */
private fun GraphicsLayerScope.leadingSwapLayer(role: GlassContentRole, progress: Float, referencePx: Float) {
  val u = progress.coerceIn(0f, 1f) * GlassContentDefaults.EnterEnd
  val blurAvailable = android.os.Build.VERSION.SDK_INT >= 31
  val shown = when (role) {
    GlassContentRole.Exit -> u <= GlassContentDefaults.ExitEnd
    else -> u >= GlassContentDefaults.EnterAt
  }
  if (!shown) {
    alpha = 0f
    return
  }
  // A riposo (tutto fuori o tutto dentro) niente strati: lo stato fermo è quello di sempre, al pixel.
  val resting = (role == GlassContentRole.Exit && u <= 0f) || (role != GlassContentRole.Exit && u >= GlassContentDefaults.EnterEnd)
  if (resting) return
  alpha = GlassContentCurves.alpha(role, u, blurAvailable)
  val scale = when (role) {
    GlassContentRole.Exit -> GlassContentCurves.exitScale.at(u)
    GlassContentRole.Enter -> GlassContentCurves.enterScale.at(u)
    else -> GlassContentCurves.bornScale.at(u)
  }
  scaleX = scale
  scaleY = scale
  if (blurAvailable) {
    val radius = glassContentBlurRadiusPx(
      glassContentSigmaPx(GlassContentCurves.blur(role, u) * GlassContentDefaults.PeakBlur, referencePx),
    )
    renderEffect = if (radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
  }
}
