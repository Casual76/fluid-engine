package dev.antigravity.fluidengine.sample.measure

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.catalog.GroupMenuStage
import dev.antigravity.fluidengine.sample.catalog.GroupMenuStageActionId
import dev.antigravity.fluidengine.sample.material.MaterialTarget
import dev.antigravity.fluidengine.sample.material.trackBounds
import dev.antigravity.fluidengine.sample.playground.PlaygroundStage
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidComposerSlots
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionLeading
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidTokenSuggestion
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionActions
import dev.antigravity.fluidengine.ui.fluid.toolbar.history
import dev.antigravity.fluidengine.ui.fluid.toolbar.selectButton
import dev.antigravity.fluidengine.ui.fluid.toolbar.selection
import dev.antigravity.fluidengine.ui.fluid.toolbar.suggestions
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.MoreVert
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidButtonRole
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.LocalFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.delay

/**
 * La scena di uno scenario: componenti veri dell'engine, nella pagina «Misure» dentro la galleria (con la tab bar della
 * galleria in fondo e l'host dei modali alla radice, come in un'app). Ogni scena scrive in [actions] le azioni dei suoi
 * tratti e dice quando è pronta.
 *
 * Le scene non riusano le demo della galleria, di proposito: una demo si riordina da una fase all'altra (F5A ha
 * riscritto i menu in quattro capitoli), e uno scenario che cambia sotto i piedi non si confronta più con sé stesso.
 * Contenuto, testi e tempi stanno qui e non cambiano senza che cambi il nome dello scenario.
 */
@Composable
internal fun ScenarioScene(scenario: MeasureScenario, actions: ScenarioActions, bottomInset: Dp) {
  when (scenario) {
    MeasureScenario.ScrollPhotos -> ScrollScene(photos = true, actions, bottomInset)
    MeasureScenario.ScrollText -> ScrollScene(photos = false, actions, bottomInset)
    MeasureScenario.MenuButton -> MenuButtonScene(actions)
    MeasureScenario.MenuGroup -> MenuGroupScene(actions, bottomInset)
    MeasureScenario.MenuGroupBar -> MenuGroupBarScene(actions, bottomInset)
    MeasureScenario.ContextMenu -> ContextScene(actions, bottomInset, holdFloorMillis = 600L)
    MeasureScenario.BarContext -> BarContextScene(actions, bottomInset)
    MeasureScenario.CapsulePress -> CapsuleScene(actions, bottomInset)
    MeasureScenario.SheetRise -> SheetScene(actions, bottomInset, dragged = false)
    MeasureScenario.SheetDrag -> SheetScene(actions, bottomInset, dragged = true)
    MeasureScenario.EightSlabs -> SlabsScene(actions, bottomInset)
    MeasureScenario.PushPop -> PushPopScene(actions, bottomInset)
    MeasureScenario.ZoomCard -> ZoomScene(actions, bottomInset)
    MeasureScenario.F1bGroupMenu -> F1bStageScene(actions, bottomInset)
    // La cadenza di F1B: la riga tenuta 800 ms come `swipe 235 1906 235 1906 800` di `lotto-gfx-cfh.sh`.
    MeasureScenario.F1bContext -> ContextScene(actions, bottomInset, holdFloorMillis = 800L, holdExact = true)
    MeasureScenario.F1bCard -> F1bCardScene(actions, bottomInset)
    MeasureScenario.Selection -> SelectionScene(actions, bottomInset)
    MeasureScenario.ComposerField -> ComposerFieldScene(actions, bottomInset)
    MeasureScenario.KeyboardBar -> KeyboardBarScene(actions, bottomInset)
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// Contenuto comune
// ---------------------------------------------------------------------------------------------------------------------

/** La «foto» delle scene: generata in codice (quella delle misure di F0.6), uguale a ogni lancio. */
@Composable
private fun rememberScenarioPhoto(): ImageBitmap = remember { generateMeasurePhoto() }

/** Le voci dei menu: le stesse quattro per tutti gli scenari (una disattivata, una distruttiva in una sezione sua). */
private fun scenarioMenu(): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = FluidIcons.Share) {},
  FluidContextAction(label = "Aggiungi ai preferiti", icon = FluidIcons.Filled.Star) {},
  FluidContextAction(label = "Modifica", icon = FluidIcons.Edit, enabled = false) {},
  FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {},
)

private val BlockShape = RoundedCornerShape(20.dp)

/** Un riquadro di foto, ritagliato ogni volta in un punto diverso perché la lista non sia un motivo che si ripete. */
@Composable
private fun PhotoBlock(index: Int, photo: ImageBitmap) {
  val alignments = listOf(Alignment.TopStart, Alignment.Center, Alignment.BottomEnd, Alignment.TopEnd, Alignment.BottomStart)
  Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(220.dp).clip(BlockShape)) {
    Image(
      bitmap = photo,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = alignments[index % alignments.size],
      modifier = Modifier.fillMaxSize(),
    )
    Text(
      text = "Foto ${index + 1}",
      style = MaterialTheme.typography.titleMedium,
      color = androidx.compose.ui.graphics.Color.White,
      modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
    )
  }
}

private const val Paragraph =
  "Signs are made using neon lights. Light is used to define color, tone, and shape. They are irreplaceable, and " +
    "depend on the people who bend them all over the world."

/** Un riquadro di solo testo: un titolo e cinque righe di corpo. */
@Composable
private fun TextBlock(index: Int) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
    Text("Paragrafo ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    repeat(3) { Text(Paragraph, style = MaterialTheme.typography.bodyLarge) }
  }
}

/** Righe di una lista vera, in un gruppo di vetro: il fondo delle scene di menu, fogli e pagine. */
@Composable
private fun PlainRows(prefix: String, count: Int) {
  FluidListGroup(glass = true) {
    repeat(count) { index ->
      if (index > 0) FluidListDivider()
      FluidListRow(title = "$prefix ${index + 1}", subtitle = "Una riga della lista, sopra la pagina", onClick = {})
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// Scorrimento
// ---------------------------------------------------------------------------------------------------------------------

/** Quanto dura il trascinamento di una lista: il dito fa poco meno di metà schermo e lascia con lo slancio. */
private const val ListDragMillis = 320L

/**
 * Una `FluidScreen` vera con la barra dichiarata in alto e la tab bar della galleria in basso, trascinata dal dito
 * finto. Un trascinamento e non `animateScrollBy`: solo il dito passa dallo scorrimento annidato, che ripiega la tab
 * bar, fa assottigliare il vetro mentre si scorre e muove il bordo dello scorrimento, cioè il costo vero.
 */
@Composable
private fun ScrollScene(photos: Boolean, actions: ScenarioActions, bottomInset: Dp) {
  val finger = rememberScenarioFinger()
  val page = remember { MaterialTarget() }
  val photo = rememberScenarioPhoto()
  FluidScreen(
    title = if (photos) "Foto" else "Testo",
    subtitle = "F7.1 · scorrimento ${if (photos) "sulle foto" else "sul testo"}",
    listState = rememberLazyListState(),
    extraBottomPadding = bottomInset,
    toolbar = {
      button("cerca", FluidIcons.Search, "Cerca", onClick = {})
      button("altro", FluidIcons.MoreHoriz, "Altro", onClick = {})
    },
    modifier = Modifier.trackBounds(page),
  ) {
    items(count = 40, key = { it }) { index -> if (photos) PhotoBlock(index, photo) else TextBlock(index) }
  }
  actions.on("su") { page.bounds?.let { finger.drag(it.at(0.5f, 0.72f), it.at(0.5f, 0.34f), ListDragMillis) } }
  actions.on("giu") { page.bounds?.let { finger.drag(it.at(0.5f, 0.34f), it.at(0.5f, 0.72f), ListDragMillis) } }
  LaunchedEffect(actions) { actions.readyWhenPlaced(page) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Menu
// ---------------------------------------------------------------------------------------------------------------------

/** «Ordina», il tasto che diventa il suo menu, su un palco di foto a tutta pagina (come il palco della demo dei menu). */
@Composable
private fun MenuButtonScene(actions: ScenarioActions) {
  val finger = rememberScenarioFinger()
  val stage = rememberGlassBackdrop()
  val menu = rememberFluidMorphMenuState()
  val button = remember { MaterialTarget() }
  val photo = rememberScenarioPhoto()
  Box(modifier = Modifier.fillMaxSize()) {
    Image(
      bitmap = photo,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize().glassBackdropSource(stage),
    )
    CompositionLocalProvider(LocalFluidMorphMenuState provides menu) {
      FluidMorphMenuButton(
        actions = ::scenarioMenu,
        onClick = {},
        text = "Ordina",
        backdrop = stage,
        opensOnTap = true,
        modifier = Modifier.align(Alignment.Center).trackBounds(button),
      )
      FluidMorphMenuHost(state = menu, backdrop = stage)
    }
  }
  actions.on("apre") { button.bounds?.let { finger.tap(it.center) } }
  actions.on("chiude") { menu.dismiss() }
  LaunchedEffect(actions) { actions.readyWhenPlaced(button) }
}

private const val GroupMenuId = "menu-gruppo"

/**
 * Il gruppo della barra che diventa menu (F5A): tre tasti con lo sfondo in comune, l'ultimo apre il menu. Il dito
 * tocca il tasto dove la barra dice che sta (la sua sorgente di transizione); la chiusura passa dall'host dei modali.
 */
@Composable
private fun MenuGroupScene(actions: ScenarioActions, bottomInset: Dp) {
  val finger = rememberScenarioFinger()
  val photo = rememberScenarioPhoto()
  val source = rememberFluidToolbarTransitionSource(GroupMenuId)
  val host = LocalFluidGlassModalHostState.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  FluidScreen(
    title = "Album",
    subtitle = "F7.1 · menu dal gruppo",
    extraBottomPadding = bottomInset,
    toolbar = {
      group(id = "strumenti") {
        button("condividi", FluidIcons.Share, "Condividi", onClick = {})
        button("preferito", FluidIcons.Filled.Star, "Preferito", onClick = {})
        action(GroupMenuId, FluidIcons.MoreVert, "Menu del gruppo") { scenarioMenu() }
      }
    },
  ) {
    items(count = 12, key = { it }) { index -> PhotoBlock(index, photo) }
  }
  actions.on("apre") {
    // Con lo sfondo in comune la sorgente è la capsula di tutto il gruppo, e il suo centro è «Preferito»: il tasto del
    // menu è l'ultimo della capsula, un quadrato alto quanto lei all'estremità destra (a sinistra se si legge da destra).
    val bounds = source.resolve()?.bounds
    if (bounds == null) {
      actions.failure = "tasto del gruppo non trovato"
    } else {
      val half = bounds.height / 2f
      val x = if (rtl) bounds.left + half else bounds.right - half
      actions.note = "tocco=${x.toInt()},${bounds.center.y.toInt()}"
      finger.tap(Offset(x, bounds.center.y))
    }
  }
  actions.on("chiude") {
    if (host == null) {
      actions.failure = "manca l'host dei modali"
    } else {
      host.dismissTop()
    }
  }
  LaunchedEffect(actions) {
    while (source.resolve() == null) delay(50)
    actions.ready = true
  }
}

/**
 * F7.27: il palco «Il gruppo che diventa menu» della galleria ([GroupMenuStage], la schermata annidata con la barra in
 * basso e due gruppi) dentro una pagina con righe di vetro sopra e sotto, come nella pagina «Menù liquidi». Il dito tocca
 * «Menu del gruppo», l'ultimo tasto della capsula del primo gruppo, dove la barra dice che sta; la chiusura passa
 * dall'host dei modali. La sorgente si cerca dentro la pagina, dove la barra annidata la registra.
 */
@Composable
private fun MenuGroupBarScene(actions: ScenarioActions, bottomInset: Dp) {
  val finger = rememberScenarioFinger()
  val host = LocalFluidGlassModalHostState.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val sources = remember { arrayOfNulls<FluidTransitionSource>(1) }
  FluidScreen(
    title = "Menù",
    subtitle = "F7.27 · il gruppo in basso",
    extraBottomPadding = bottomInset,
  ) {
    item(key = "rows") { PlainRows("Riga", 2) }
    item(key = "stage") {
      sources[0] = rememberFluidToolbarTransitionSource(GroupMenuStageActionId)
      GroupMenuStage(pick = {})
    }
    item(key = "rows-after") { PlainRows("Voce", 6) }
  }
  actions.on("apre") {
    val bounds = sources[0]?.resolve()?.bounds
    if (bounds == null) {
      actions.failure = "tasto del gruppo non trovato"
    } else {
      val half = bounds.height / 2f
      val x = if (rtl) bounds.left + half else bounds.right - half
      actions.note = "tocco=${x.toInt()},${bounds.center.y.toInt()}"
      finger.tap(Offset(x, bounds.center.y))
    }
  }
  actions.on("chiude") {
    if (host == null) actions.failure = "manca l'host dei modali" else host.dismissTop()
  }
  LaunchedEffect(actions) {
    while (sources[0]?.resolve() == null) delay(50)
    actions.ready = true
  }
}

/**
 * Il menu contestuale di una riga vera: il dito la tiene premuta (si solleva, poi il menu), la lascia senza muoversi
 * (il menu resta aperto), e la chiusura passa dall'host dei modali. [holdExact]: la durata è quella e basta (la
 * cadenza di F1B), altrimenti è la più corta che apre sempre il menu, mai sotto [holdFloorMillis].
 */
@Composable
private fun ContextScene(actions: ScenarioActions, bottomInset: Dp, holdFloorMillis: Long, holdExact: Boolean = false) {
  val finger = rememberScenarioFinger()
  val row = remember { MaterialTarget() }
  val host = LocalFluidGlassModalHostState.current
  FluidScreen(title = "Note", subtitle = "F7.1 · menu contestuale", extraBottomPadding = bottomInset) {
    item(key = "rows") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Nota della riunione",
          subtitle = "Tieni premuto per le azioni",
          modifier = Modifier.trackBounds(row),
          onClick = {},
          contextActions = ::scenarioMenu,
        )
        repeat(5) { index ->
          FluidListDivider()
          FluidListRow(title = "Nota ${index + 2}", subtitle = "Una riga della lista", onClick = {})
        }
      }
    }
    items(count = 8, key = { "text-$it" }) { index -> TextBlock(index) }
  }
  val hold = if (holdExact) holdFloorMillis else longPressMillis(holdFloorMillis)
  actions.on("apre") { row.bounds?.let { finger.hold(it.center, hold) } }
  actions.on("chiude") {
    if (host == null) {
      actions.failure = "manca l'host dei modali"
    } else {
      host.dismissTop()
    }
  }
  LaunchedEffect(actions) { actions.readyWhenPlaced(row) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Barra
// ---------------------------------------------------------------------------------------------------------------------

/** Sfoglia → seleziona (F4A.2): «Seleziona» e ⋯ diventano condividi, elimina e la spunta, cambiando solo lo stato. */
@Composable
private fun BarContextScene(actions: ScenarioActions, bottomInset: Dp) {
  var selecting by remember { mutableStateOf(false) }
  FluidScreen(
    title = "Documenti",
    subtitle = "F7.1 · cambio di contesto della barra",
    extraBottomPadding = bottomInset,
    toolbar = {
      if (selecting) {
        button("condividi", FluidIcons.IosShare, "Condividi", onClick = {})
        button("elimina", FluidIcons.Delete, "Elimina", onClick = {}, role = FluidButtonRole.Destructive)
        confirm(onClick = { selecting = false })
      } else {
        text("seleziona", "Seleziona", onClick = { selecting = true })
        button("opzioni", FluidIcons.MoreHoriz, "Opzioni", onClick = {})
      }
    },
  ) {
    item(key = "rows") { PlainRows("Documento", 6) }
    items(count = 8, key = { "text-$it" }) { index -> TextBlock(index) }
  }
  actions.on("cambia") { selecting = true }
  actions.on("torna") { selecting = false }
  LaunchedEffect(actions) { actions.ready = true }
}

// ---------------------------------------------------------------------------------------------------------------------
// Capsula
// ---------------------------------------------------------------------------------------------------------------------

private const val CapsuleHoldMillis = 1_600L

/** Una capsula grande sopra una foto, tenuta premuta dal dito che scivola: lente premuta e bagliore che lo segue. */
@Composable
private fun CapsuleScene(actions: ScenarioActions, bottomInset: Dp) {
  val finger = rememberScenarioFinger()
  val capsule = remember { MaterialTarget() }
  val photo = rememberScenarioPhoto()
  FluidScreen(title = "Capsula", subtitle = "F7.1 · pressione continua", extraBottomPadding = bottomInset) {
    item(key = "stage") {
      Box(modifier = Modifier.fillMaxWidth().height(280.dp).clip(BlockShape), contentAlignment = Alignment.Center) {
        Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        FluidGlassButton(
          text = "Tieni premuto",
          onClick = {},
          size = FluidGlassSize.Large,
          modifier = Modifier.trackBounds(capsule),
        )
      }
    }
    items(count = 6, key = { "text-$it" }) { index -> TextBlock(index) }
  }
  actions.on("tieni") { capsule.bounds?.let { finger.slideHold(it, CapsuleHoldMillis) } }
  LaunchedEffect(actions) { actions.readyWhenPlaced(capsule) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Fogli
// ---------------------------------------------------------------------------------------------------------------------

private const val SheetDragMillis = 300L

/**
 * Il foglio di vetro con due altezze sopra una pagina vera. [dragged] falso: sale alla media e scende, dallo stato.
 * Vero: parte aperto a metà, e il dito lo porta in alto e lo riporta giù (il foglio si trascina da ogni suo punto).
 * Il dito parte da frazioni fisse della pagina, che a metà e in alto cadono dentro il foglio su tablet e telefono.
 */
@Composable
private fun SheetScene(actions: ScenarioActions, bottomInset: Dp, dragged: Boolean) {
  val finger = rememberScenarioFinger()
  val page = remember { MaterialTarget() }
  var visible by remember { mutableStateOf(dragged) }
  val detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) }
  FluidScreen(
    title = "Luoghi",
    subtitle = "F7.1 · ${if (dragged) "foglio trascinato" else "foglio che sale"}",
    extraBottomPadding = bottomInset,
    modifier = Modifier.trackBounds(page),
  ) {
    item(key = "rows") { PlainRows("Luogo", 6) }
    items(count = 8, key = { "text-$it" }) { index -> TextBlock(index) }
  }
  FluidGlassSheet(
    visible = visible,
    onDismissRequest = { visible = false },
    detents = detents,
    paneTitle = "Luoghi vicini",
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
      Text("Luoghi vicini", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      repeat(8) { index ->
        Text(
          "Luogo ${index + 1} · a ${index + 2} minuti",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(vertical = 10.dp),
        )
      }
    }
  }
  if (dragged) {
    actions.on("su") {
      if (!visible) {
        // Il foglio si è chiuso nel tratto prima (un lancio troppo forte): si riapre, e la riga FINE lo dice.
        visible = true
        actions.note = "riaperto"
        delay(SheetReopenMillis)
      }
      page.bounds?.let { finger.drag(it.at(0.5f, 0.6f), it.at(0.5f, 0.3f), SheetDragMillis) }
    }
    actions.on("giu") { page.bounds?.let { finger.drag(it.at(0.5f, 0.22f), it.at(0.5f, 0.52f), SheetDragMillis) } }
  } else {
    actions.on("sale") { visible = true }
    actions.on("scende") { visible = false }
  }
  LaunchedEffect(actions) { actions.readyWhenPlaced(page) }
}

private const val SheetReopenMillis = 900L

// ---------------------------------------------------------------------------------------------------------------------
// Otto lastre
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Otto pannelli di vetro fermi (ruolo `Modal`, ottica del pannello: la ricetta del foglio dell'host) sopra una lista
 * di foto registrata come sorgente e trascinata dal dito: ogni pannello ricattura a ogni fotogramma dello scorrimento.
 * I pannelli non prendono il tocco, che arriva alla lista sotto.
 */
@Composable
private fun SlabsScene(actions: ScenarioActions, bottomInset: Dp) {
  val finger = rememberScenarioFinger()
  val page = remember { MaterialTarget() }
  val stage = rememberGlassBackdrop()
  val photo = rememberScenarioPhoto()
  Box(modifier = Modifier.fillMaxSize().trackBounds(page)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().glassBackdropSource(stage),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = bottomInset + 96.dp),
    ) {
      items(count = 40, key = { it }) { index -> PhotoBlock(index, photo) }
    }
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      repeat(4) {
        // Due colonne a metà larghezza con 16 dp in mezzo: sul tablet e sul telefono, e il dito (al centro) passa fra le due.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          repeat(2) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(SlabHeight)
                .glassSurface(
                  state = stage,
                  tint = GlassDefaults.panelTint(),
                  shape = ContinuousCornerShape(FluidRadius.Sheet),
                  role = GlassRole.Modal,
                  optics = GlassDefaults.panelOptics(),
                ),
            )
          }
        }
      }
    }
  }
  actions.on("su") { page.bounds?.let { finger.drag(it.at(0.5f, 0.72f), it.at(0.5f, 0.34f), ListDragMillis) } }
  actions.on("giu") { page.bounds?.let { finger.drag(it.at(0.5f, 0.34f), it.at(0.5f, 0.72f), ListDragMillis) } }
  LaunchedEffect(actions) { actions.readyWhenPlaced(page) }
}

private val SlabHeight = 140.dp

// ---------------------------------------------------------------------------------------------------------------------
// Pagine e zoom
// ---------------------------------------------------------------------------------------------------------------------

private const val PageList = "cartelle"
private const val PageDetail = "note"

/** Push e pop nell'host delle pagine: due `FluidScreen` con la barra dichiarata, la pila cambiata dallo stato. */
@Composable
private fun PushPopScene(actions: ScenarioActions, bottomInset: Dp) {
  var stack by remember { mutableStateOf(listOf(PageList)) }
  FluidPageHost(stack = stack, onBack = { if (stack.size > 1) stack = stack.dropLast(1) }) { page ->
    if (page == PageList) {
      FluidScreen(
        title = "Cartelle",
        subtitle = "F7.1 · push e pop",
        extraBottomPadding = bottomInset,
        toolbar = { button("nuova", FluidIcons.Add, "Nuova cartella", onClick = {}) },
      ) {
        item(key = "rows") { PlainRows("Cartella", 8) }
      }
    } else {
      FluidScreen(
        title = "Note",
        onBack = { stack = listOf(PageList) },
        extraBottomPadding = bottomInset,
        toolbar = {
          button("condividi", FluidIcons.IosShare, "Condividi", onClick = {})
          button("altro", FluidIcons.MoreHoriz, "Altro", onClick = {})
        },
      ) {
        items(count = 8, key = { it }) { index -> TextBlock(index) }
      }
    }
  }
  actions.on("push") { stack = listOf(PageList, PageDetail) }
  actions.on("pop") { stack = listOf(PageList) }
  LaunchedEffect(actions) { actions.ready = true }
}

/** Quale card si apre: la terza, nella prima schermata su tablet e telefono. */
private const val ZoomCard = 2
private const val ZoomGrid = -1

/** Una griglia di card; una si apre a pagina intera con lo zoom dalla sorgente (F5B.3) e ci torna al pop. */
@Composable
private fun ZoomScene(actions: ScenarioActions, bottomInset: Dp) {
  val namespace = rememberFluidGlassNamespace()
  val photo = rememberScenarioPhoto()
  var stack by remember { mutableStateOf(listOf(ZoomGrid)) }
  val cardShape = ContinuousCornerShape(FluidRadius.Group)
  FluidPageHost(
    stack = stack,
    onBack = { if (stack.size > 1) stack = stack.dropLast(1) },
    transition = { if (it == ZoomGrid) FluidPageTransition.Slide else FluidPageTransition.Expand },
    zoomSource = { if (it == ZoomGrid) null else FluidTransitionSource(it, namespace) },
  ) { key ->
    if (key == ZoomGrid) {
      FluidScreen(title = "Raccolte", subtitle = "F7.1 · zoom dalla card", extraBottomPadding = bottomInset) {
        items(count = 5, key = { it }) { row ->
          Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { column ->
              val id = row * 2 + column
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(180.dp)
                  .fluidTransitionSource(id, namespace, cardShape)
                  .clip(cardShape),
              ) {
                PhotoBlockImage(id, photo)
              }
            }
          }
        }
      }
    } else {
      FluidScreen(title = "Raccolta ${key + 1}", onBack = { stack = listOf(ZoomGrid) }, extraBottomPadding = bottomInset) {
        item(key = "photo") {
          Box(modifier = Modifier.fillMaxWidth().height(320.dp).clip(BlockShape)) { PhotoBlockImage(key, photo) }
        }
        items(count = 4, key = { "text-$it" }) { index -> TextBlock(index) }
      }
    }
  }
  actions.on("apre") { stack = listOf(ZoomGrid, ZoomCard) }
  actions.on("chiude") { stack = listOf(ZoomGrid) }
  LaunchedEffect(actions) { actions.ready = true }
}

@Composable
private fun PhotoBlockImage(index: Int, photo: ImageBitmap) {
  val alignments = listOf(Alignment.TopStart, Alignment.Center, Alignment.BottomEnd, Alignment.TopEnd, Alignment.BottomStart)
  Image(
    bitmap = photo,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    alignment = alignments[index % alignments.size],
    modifier = Modifier.fillMaxSize(),
  )
}

// ---------------------------------------------------------------------------------------------------------------------
// Le prove di F1B
// ---------------------------------------------------------------------------------------------------------------------

/** Le tre forme del palco «Tasti → Menù → Pop-up» del Playground. */
internal enum class F1bStagePhase { Buttons, Menu, Popup }

/**
 * Le geometrie del palco del Playground, in pixel del palco: la copia di `demoFormFor` di `playground/PracticalDemos.kt`
 * com'era nelle prove di F1B. Copiata e non chiamata perché là è privata, e perché la prova non deve cambiare se la
 * demo cambia: se cambiano là, questa resta la (c) del piano.
 */
internal fun f1bStageForm(phase: F1bStagePhase, stage: IntSize, groupRadiusPx: Float, sheetRadiusPx: Float): FluidForm {
  val w = stage.width.toFloat()
  val h = stage.height.toFloat()
  return when (phase) {
    F1bStagePhase.Buttons -> {
      val buttonWidth = w * 0.34f
      val buttonHeight = h * 0.17f
      val gap = w * 0.07f
      val top = h * 0.5f - buttonHeight / 2f
      FluidForm.Group(
        pieces = listOf(
          FluidForm.Slab(
            Rect(w / 2f - gap / 2f - buttonWidth, top, w / 2f - gap / 2f, top + buttonHeight),
            FluidCornerRadii.all(buttonHeight / 2f),
          ),
          FluidForm.Slab(
            Rect(w / 2f + gap / 2f, top, w / 2f + gap / 2f + buttonWidth, top + buttonHeight),
            FluidCornerRadii.all(buttonHeight / 2f),
          ),
        ),
        blendRadius = minOf(w, h) * 0.11f,
      )
    }
    F1bStagePhase.Menu -> {
      val menuWidth = w * 0.58f
      val menuHeight = h * 0.62f
      FluidForm.Slab(
        frame = Rect(w / 2f - menuWidth / 2f, h * 0.48f - menuHeight / 2f, w / 2f + menuWidth / 2f, h * 0.48f + menuHeight / 2f),
        cornerRadii = FluidCornerRadii.all(groupRadiusPx),
      )
    }
    F1bStagePhase.Popup -> {
      val popupWidth = w * 0.84f
      val popupHeight = h * 0.72f
      FluidForm.Slab(
        frame = Rect(w / 2f - popupWidth / 2f, h * 0.5f - popupHeight / 2f, w / 2f + popupWidth / 2f, h * 0.5f + popupHeight / 2f),
        cornerRadii = FluidCornerRadii.all(sheetRadiusPx),
      )
    }
  }
}

/**
 * F1B (c): il palco del Playground (lo stesso `PlaygroundStage`, stesso fondale e stessa ottica) che passa da due tasti
 * al menu e al pop-up. Nella prova di allora le tre forme si sceglievano dal controllo segmentato sotto il palco;
 * qui le sceglie lo stato, con lo stesso `morphTo`. Mancano gli strati di testo della demo (privati là).
 */
@Composable
private fun F1bStageScene(actions: ScenarioActions, bottomInset: Dp) {
  val density = LocalDensity.current
  val groupRadiusPx = with(density) { FluidRadius.Group.toPx() }
  val sheetRadiusPx = with(density) { FluidRadius.Sheet.toPx() }
  val state = rememberFluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
  var stage by remember { mutableStateOf(IntSize.Zero) }
  fun form(phase: F1bStagePhase) = f1bStageForm(phase, stage, groupRadiusPx, sheetRadiusPx)
  LaunchedEffect(stage) {
    if (stage != IntSize.Zero) {
      state.snapTo(form(F1bStagePhase.Buttons))
      actions.ready = true
    }
  }
  FluidScreen(title = "Fisica", subtitle = "F7.1 · F1B (c), tasti → menù → pop-up", extraBottomPadding = bottomInset) {
    item(key = "stage") {
      PlaygroundStage(state = state, tier = null, drawing = false, aspectRatio = 1.25f, onSized = { stage = it })
    }
    items(count = 4, key = { "text-$it" }) { index -> TextBlock(index) }
  }
  actions.on("menu") { state.morphTo(form(F1bStagePhase.Menu)) }
  actions.on("popup") { state.morphTo(form(F1bStagePhase.Popup)) }
  actions.on("menu-2") { state.morphTo(form(F1bStagePhase.Menu)) }
  actions.on("tasti") { state.morphTo(form(F1bStagePhase.Buttons)) }
}

/**
 * F1B (h): la riga «Card che si espande» della scheda Chrome, che si apre nel pop-up di vetro dai suoi limiti. Il dito
 * la tocca (come `tap 250 497`); la chiusura è lo stato, come faceva l'«Indietro» di sistema di allora.
 */
@Composable
private fun F1bCardScene(actions: ScenarioActions, bottomInset: Dp) {
  val finger = rememberScenarioFinger()
  val row = remember { MaterialTarget() }
  var open by remember { mutableStateOf(false) }
  var origin by remember { mutableStateOf<Rect?>(null) }
  FluidGlassModalPortal(visible = open, onDismissRequest = { open = false }, origin = { origin }, paneTitle = "Dettaglio") {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(text = "Un pop-up in vetro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(
        text = "Nasce dalla riga che hai toccato e lievita fino a qui: la sagoma viaggia con la rifrazione addosso, il " +
          "contenuto le sta attaccato, e la pagina dietro resta a colori.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  FluidScreen(title = "Chrome", subtitle = "F7.1 · F1B (h), la card che diventa foglio", extraBottomPadding = bottomInset) {
    item(key = "modal-header") { FluidSectionHeader(title = "Modale in-root") }
    item(key = "modal") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Card che si espande",
          subtitle = "Cresce dai limiti di questa riga",
          modifier = Modifier.fluidExpandOrigin { origin = it }.trackBounds(row),
          onClick = { open = true },
        )
        FluidListDivider()
        FluidListRow(title = "Foglio ancorato in basso", subtitle = "Stessa meccanica, altra geometria", onClick = {})
      }
    }
    item(key = "context-header") { FluidSectionHeader(title = "Menù contestuale") }
    item(key = "context") { PlainRows("Circolare", 3) }
  }
  actions.on("apre") { row.bounds?.let { finger.tap(it.center) } }
  actions.on("chiude") { open = false }
  LaunchedEffect(actions) { actions.readyWhenPlaced(row) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Elementi dinamici (F6B.13)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * La selezione della posta (F6B.1, F6B.2): «Seleziona» e tre righe scelte. Con la tab bar della galleria in fondo la
 * pillola delle schede diventa la capsula delle azioni nello stesso vetro; «Fine» la riporta com'era.
 */
@Composable
private fun SelectionScene(actions: ScenarioActions, bottomInset: Dp) {
  val state = remember { FluidSelectionState<Int>() }
  var selecting by remember { mutableStateOf(false) }
  FluidScreen(
    title = "Posta",
    subtitle = "F7.1 · selezione",
    extraBottomPadding = bottomInset,
    toolbar = {
      if (selecting) {
        selection(
          state = state,
          count = state.countIn(SelectionRows),
          onSelectAll = { state.selectAll() },
          onDone = { selecting = false },
          actions = FluidSelectionActions.mail(onDelete = {}, onArchive = {}, onMove = {}, onMarkRead = {}),
        )
      } else {
        selectButton(onClick = { selecting = true })
      }
    },
  ) {
    item(key = "rows") {
      FluidListGroup(glass = true) {
        repeat(SelectionRows) { index ->
          if (index > 0) FluidListDivider()
          val picked = selecting && state.isSelected(index)
          FluidListRow(
            title = "Messaggio ${index + 1}",
            subtitle = "Una riga della posta",
            leadingTile = false,
            selected = picked,
            leading = { FluidSelectionLeading(selecting, picked) {} },
            onClick = { if (selecting) state.toggle(index) },
          )
        }
      }
    }
  }
  actions.on("entra") {
    selecting = true
    listOf(0, 2, 3).forEach(state::select)
  }
  actions.on("esce") {
    selecting = false
    state.clear()
  }
  LaunchedEffect(actions) { actions.ready = true }
}

private const val SelectionRows = 12

/** Le cinque righe del campo che cresce: la stessa bozza a ogni lancio. */
private val ComposerLines = listOf("Prima riga", "Seconda riga", "Terza riga", "Quarta riga", "Quinta riga")

/** L'a capo fra le righe della bozza. */
private const val LineBreak = "\n"

/** Ogni quanto arriva una riga nel tratto «cresce»: cinque righe in 800 ms, poi la molla della crescita si posa. */
private const val ComposerLineMillis = 200L

/**
 * Il campo «Messaggio» col composer (F6B.7), chiuso (senza tastiera: a riposo tiene le sue righe), sopra una chat: cresce
 * da una a cinque righe, apre il vassoio di un allegato, torna a una riga. La crescita segue senza viaggio del piano; la
 * lista rimisura il suo margine in fondo (F6B.13).
 */
@Composable
private fun ComposerFieldScene(actions: ScenarioActions, bottomInset: Dp) {
  var text by remember { mutableStateOf(ComposerLines.first()) }
  var attached by remember { mutableStateOf(false) }
  val composer = FluidComposerSlots(
    leading = { FluidCapsuleAction(FluidIcons.Add, "Aggiungi un allegato", onClick = {}, size = 40.dp) },
    attachments = if (attached) {
      { Text("Foto della sede", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp)) }
    } else null,
  )
  FluidScreen(
    title = "Anna",
    subtitle = "F7.1 · campo col composer",
    extraBottomPadding = bottomInset,
    search = FluidSearchSpec.message(text, { text = it }, onSend = {}, composer = composer),
  ) {
    items(count = 24, key = { it }) { index -> TextBlock(index) }
  }
  actions.on("cresce") {
    for (lines in 2..ComposerLines.size) {
      delay(ComposerLineMillis)
      text = ComposerLines.take(lines).joinToString(separator = LineBreak)
    }
  }
  actions.on("allegato") { attached = true }
  actions.on("torna") {
    attached = false
    text = ComposerLines.first()
  }
  LaunchedEffect(actions) { actions.ready = true }
}

/**
 * La barra sopra la tastiera (F6B.9) in vista anche senza tastiera (`alwaysVisible`, come con la tastiera fisica): passa
 * dai suggerimenti alla formattazione e torna. Il cambio di contesto è un cambio di stato della barra dichiarata.
 */
@Composable
private fun KeyboardBarScene(actions: ScenarioActions, bottomInset: Dp) {
  var formatting by remember { mutableStateOf(false) }
  FluidScreen(
    title = "Nuova mail",
    subtitle = "F7.1 · barra sopra la tastiera",
    extraBottomPadding = bottomInset,
    toolbar = {
      keyboard(context = if (formatting) "formattazione" else "destinatari", alwaysVisible = true) {
        if (formatting) {
          button("grassetto", FluidIcons.FormatBold, "Grassetto", onClick = {})
          button("corsivo", FluidIcons.FormatItalic, "Corsivo", onClick = {})
          button("sottolineato", FluidIcons.FormatUnderlined, "Sottolineato", onClick = {})
          history(canUndo = true, canRedo = false, onUndo = {}, onRedo = {})
        } else {
          suggestions(listOf(FluidTokenSuggestion("anna", "Anna"), FluidTokenSuggestion("luca", "Luca"))) {}
        }
      }
    },
  ) {
    item(key = "rows") { PlainRows("Campo", 4) }
    items(count = 8, key = { "text-$it" }) { index -> TextBlock(index) }
  }
  actions.on("cambia") { formatting = true }
  actions.on("torna") { formatting = false }
  LaunchedEffect(actions) { actions.ready = true }
}
