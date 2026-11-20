package dev.antigravity.fluidengine.ui.fluid

import android.os.Build

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronRight
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.GlassContentLensEffect
import dev.antigravity.fluidengine.ui.glass.field.glassMenuLensOverflow
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropFaces
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropMotion
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropRecipe
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Il pannello dei menu (3.0, fase 7): UNO per tutti — il menu che si trasforma, il menu contestuale,
 * le azioni della barra, il vecchio tasto col menu — con le voci, i separatori e UNA goccia di vetro
 * che segue il dito sulle voci come la pillola della tab bar.
 *
 * Il pannello non e' il vetro: il vetro e' la superficie di Fluid-physics che l'host fa viaggiare
 * dall'ancora al pannello, e che pubblica il proprio materiale (`exports`). Qui c'e' quello che sta
 * SOPRA quel vetro, in due fratelli:
 *
 *  1. la goccia, che campiona la pagina con dentro il materiale del pannello (`glassContainer`, vetro
 *     su vetro) e ha TUTTE le sue trasformazioni nel proprio `layerBlock` — posto, scala del viaggio,
 *     schiacciamento — che il renderer inverte quando campiona. Mai dentro il layer delle voci: un
 *     vetro scalato da un antenato rifrange quello che non c'e' piu' (la prima regola del vetro);
 *  2. la colonna delle voci, con l'opacita' e la mappa del viaggio nel suo `graphicsLayer` (testo,
 *     non vetro: li' scalare e' lecito) e i separatori disegnati ai confini.
 *
 * Il dito lo decide [FluidMenuSelection]: il pannello le scrive la geometria e le passa i gesti che
 * cadono su di lui; il dito che ha APERTO il menu arriva invece dall'ancora (`FluidMenuHandOff`).
 */

/**
 * I limiti di un nodo in pixel della radice, NON ritagliati dagli antenati: un tasto mezzo nascosto
 * dal bordo di una lista resta il tasto intero, e il menu deve partire dalla sua sagoma vera, non da
 * quella che il ritaglio lascia vedere (`boundsInRoot` la taglia).
 */
internal fun LayoutCoordinates.unclippedBoundsInRoot(): Rect {
  val topLeft = localToRoot(Offset.Zero)
  val bottomRight = localToRoot(Offset(size.width.toFloat(), size.height.toFloat()))
  return Rect(topLeft, bottomRight)
}

/** Dove sta il pannello, per convertire i tocchi in pixel della radice senza allocare per evento. */
internal class FluidMenuCoordinates {
  var value: LayoutCoordinates? = null

  fun toRoot(local: Offset): Offset {
    val c = value
    return if (c != null && c.isAttached) c.localToRoot(local) else local
  }
}

/**
 * I gesti che cadono SUL pannello: un tocco su una voce, o un trascinamento che parte qui.
 *
 * Il down si prende nell'Initial e si consuma (lo schema di `fluidButtonGesture`): il `clickable`
 * delle righe, che c'e' per TalkBack e tastiera, lo trova consumato e non clicca — il click dal dito
 * lo decide la selezione, una volta sola. Un tocco FUORI dal pannello non arriva qui: cade
 * sull'acchiappa-tocco dell'host, o sullo scrim, e congeda.
 */
internal fun Modifier.fluidMenuPointerArea(
  selection: FluidMenuSelection,
  coordinates: FluidMenuCoordinates,
): Modifier = this.pointerInput(selection, coordinates) {
  selection.touchSlop = viewConfiguration.touchSlop
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    down.consume()
    selection.panelDown(coordinates.toRoot(down.position))
    var ended = false
    try {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
        val moved = change.position != change.previousPosition
        change.consume()
        if (!change.pressed) {
          ended = true
          selection.panelUp(coordinates.toRoot(change.position))
          break
        }
        if (moved) selection.panelMove(coordinates.toRoot(change.position))
      }
    } finally {
      // Un gesto finito senza sollevamento (il pannello smontato, un annullo): nessuna scelta.
      if (!ended) selection.panelCancel()
    }
  }
}

/**
 * Lo stato della goccia di UN pannello: la molla di [GlassDragAnimation] (valore fra le celle,
 * pressione, scale, velocita') e la sua presenza. Si legge solo in disegno.
 */
@Stable
internal class FluidMenuDropState(drag: GlassDragAnimation, count: Int) {
  val drag: GlassDragAnimation = drag
  val presence = Animatable(0f, 0.001f)

  /** Le celle fra la prima e l'ultima: converte la velocita' della molla in celle al secondo. */
  val span: Float = max(1, count - 1).toFloat()

  /**
   * Quanto la goccia sta su [index]: 1 sopra, 0 a una cella di distanza, per la pressione e la
   * presenza. Scala il testo della voce e spegne i suoi separatori, e lo fa SCIVOLANDO: con la
   * goccia a meta' fra due voci, meta' ciascuna, mai un salto.
   */
  fun weight(index: Int): Float {
    val near = (1f - abs(drag.value - index)).coerceIn(0f, 1f)
    return near * drag.pressProgress.coerceIn(0f, 1f) * presence.value
  }
}

/** Una goccia per pannello, che segue [FluidMenuSelection.hovered]. */
@Composable
internal fun rememberFluidMenuDropState(selection: FluidMenuSelection, count: Int): FluidMenuDropState {
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val state = remember(scope, count) {
    FluidMenuDropState(
      drag = GlassDragAnimation(
        animationScope = scope,
        initialValue = 0f,
        valueRange = 0f..max(1, count - 1).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = FluidMenuDropPressedScale,
        // Criticamente smorzata: lo schiacciamento segue il dito e si ferma quando si ferma, non
        // ondeggia a ogni voce attraversata.
        velocityDampingRatio = 1f,
      ),
      count = count,
    )
  }
  LaunchedEffect(selection, state, reducedMotion) {
    val effect = this
    var engaged = false
    var fade: Job? = null
    snapshotFlow { selection.hovered }.collect { index ->
      if (index >= 0) {
        if (!engaged) {
          // La prima entrata: la goccia nasce SOTTO il dito, non ci scivola da un'altra voce.
          state.drag.snapToValue(index.toFloat())
          state.drag.press()
          engaged = true
        } else {
          state.drag.updateValue(index.toFloat())
        }
        fade?.cancel()
        fade = effect.launch {
          if (reducedMotion) state.presence.snapTo(1f) else state.presence.animateTo(1f, FluidMotion.fadeIn(110))
        }
      } else if (engaged) {
        engaged = false
        state.drag.release()
        fade?.cancel()
        fade = effect.launch {
          // La goccia si posa bianca prima di andarsene: e' la conferma della scelta. Si aspetta
          // che la lente sia quasi tornata goccia, ma mai piu' di un attimo.
          withTimeoutOrNull(FluidMenuDropConfirmMillis) {
            snapshotFlow { state.drag.pressProgress }.first { it < 0.5f }
          }
          if (reducedMotion) state.presence.snapTo(0f) else state.presence.animateTo(0f, FluidMotion.fadeOut(160))
        }
      }
    }
  }
  return state
}

/**
 * Il pannello di un menu. Il chiamante lo posa alla posa FINALE (offset e taglia del pannello): il
 * viaggio lo fanno [contentAlpha] e [contentMap], letti in disegno, e la sagoma che l'host disegna
 * sotto.
 *
 * @param layout le celle, dalla stessa aritmetica con cui l'host ha risolto il pannello.
 * @param panelMaterial il materiale che la superficie dell'host pubblica (`exports`): la goccia lo
 *   trova da sola grazie a `glassContainer`. Null senza vetro.
 * @param pageBackdrop cio' che la goccia campiona sotto il pannello; null: niente goccia (nessun
 *   fondale da piegare, API < 31), restano la scala della voce e i separatori che si spengono.
 * @param onPick una scelta da TalkBack o dalla tastiera (il `clickable` delle righe). Quella del dito
 *   passa da [FluidMenuSelection.onPick].
 * @param travelClip la sagoma del corpo di vetro in questo istante, nelle coordinate del pannello, o
 *   null a riposo (3.0): voci e goccia non escono mai dal vetro mentre si forma o si ritira. Letta in
 *   disegno; un rettangolo arrotondato, quindi il ritaglio hardware del nodo, niente maschera.
 * @param structure le celle come le dispone l'host dei modali (4.0, F5A.3): fila in cima, righe col
 *   sottotitolo, sottomenu, ordine capovolto. [onPick] riceve allora l'indice di una cella, non di una voce.
 *   Null: una riga per voce di [actions] alle quote di [layout], come prima (il menu di `FluidMorphMenuHost`).
 * @param tileLabels le tessere della fila mostrano l'etichetta sotto il simbolo ([FluidMenuElementSize.Medium]).
 * @param levelBlurDp la sfocatura del cambio di livello (aprire o chiudere un sottomenu), letta in disegno.
 * @param outgoing il livello che se ne va durante un cambio di livello, disegnato sotto quello nuovo.
 * @param interactive il pannello prende il dito e i clic delle voci. Falso mentre il menu si chiude (S4i): un menu che si posa
 *   sul suo tasto (`FluidMenuPlacement.OverAnchor`) lo copriva fino alla fine dell'uscita, e il tocco sul tasto per riaprirlo
 *   finiva sul pannello che se ne andava.
 */
@Composable
internal fun FluidGlassMenuPanel(
  actions: List<FluidContextAction>,
  selection: FluidMenuSelection,
  layout: FluidMenuPanelLayout?,
  panelMaterial: GlassBackdropState?,
  pageBackdrop: GlassBackdropState?,
  contentAlpha: () -> Float,
  contentMap: () -> FluidMenuContentMap,
  onPick: (Int) -> Unit,
  modifier: Modifier = Modifier,
  paneTitle: String? = null,
  travelClip: (() -> RoundRect?)? = null,
  lens: FluidMenuLens? = null,
  structure: FluidMenuStructure? = null,
  tileLabels: Boolean = false,
  levelBlurDp: (() -> Float)? = null,
  outgoing: FluidMenuOutgoingLevel? = null,
  interactive: Boolean = true,
) {
  val density = LocalDensity.current
  val coordinates = remember { FluidMenuCoordinates() }
  // Senza struttura le celle sono le righe di sempre, una per voce, alle quote che il chiamante ha risolto: la
  // stessa aritmetica, quindi le stesse celle (e la stessa goccia) di prima.
  val cells = structure ?: remember(actions, layout) { fluidMenuStructureOfLayout(actions, requireNotNull(layout)) }
  val plain = structure == null
  val enabled = remember(cells) { cells.entries.map { it.enabled } }
  val drop = rememberFluidMenuDropState(selection, cells.size)
  val uniform = remember(cells) { fluidMenuCellsUniform(cells) }
  val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  val band = MaterialTheme.colorScheme.onSurface.copy(alpha = if (GlassDefaults.isDarkSurface()) 0.08f else 0.05f)
  val insetPx = with(density) { FluidMenuSeparatorInset.toPx() }
  val tileInsetPx = with(density) { FluidMenuTileSeparatorInset.toPx() }
  val hairlinePx = with(density) { 0.5.dp.toPx() }
  val anchored = lens?.journey?.anchor != null
  val lensEffect = remember(lens != null) {
    if (lens != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      GlassContentLensEffect()
    } else {
      null
    }
  }
  // Lo strato che porta le voci alla lente, dentro la finestra (S4g-bis, b): un layer esplicito, l'effetto che prima stava sul nodo.
  val inkLens = rememberGraphicsLayer()

  Box(
    modifier = modifier
      .then(if (panelMaterial != null) Modifier.glassContainer(panelMaterial) else Modifier)
      .onGloballyPositioned { c ->
        // Nessuna trasformazione di layer su questo nodo: la sua posizione e' la posa finale, ed e'
        // quella che il dito vede quando il pannello e' fermo (il hit-test parla di quella).
        coordinates.value = c
        val topLeft = c.localToRoot(Offset.Zero)
        val panel = Rect(topLeft, Size(c.size.width.toFloat(), c.size.height.toFloat()))
        val current = selection.geometry
        if (current == null || current.panel != panel || current.enabled != enabled || current.cells.size != cells.size) {
          selection.geometry = cells.geometry(panel)
        }
      }
      // In chiusura il dito passa sotto, alla pagina e al tasto che l'ha aperto (S4i).
      .then(if (interactive) Modifier.fluidMenuPointerArea(selection, coordinates) else Modifier)
      .semantics {
        paneTitle?.let { this.paneTitle = it }
        isTraversalGroup = true
      }
      // Col vetro dell'ancora la sagoma ritaglia ogni figlio per conto suo, e le voci nello shader della lente (S4g, NO 1).
      .then(if (anchored || travelClip == null) Modifier else Modifier.fluidMenuTravelClip(travelClip)),
  ) {
    if (pageBackdrop != null && cells.size > 0) {
      Box(if (anchored) Modifier.matchParentSize().drawWithContent { drawInMenuSilhouette(lens!!, travelClip) } else Modifier.matchParentSize()) {
        FluidMenuDrop(
          state = drop,
          selection = selection,
          backdrop = pageBackdrop,
          // Una fila di tessere o righe di altezze diverse: la goccia prende la misura della cella su cui sta,
          // scivolando fra le due quando passa dall'una all'altra.
          cellSize = { panel: Size ->
            if (uniform) Size(panel.width, cells.heights[0]) else fluidMenuDropCellSize(cells, panel.width, drop.drag.value)
          },
          vertical = true,
          contentAlpha = contentAlpha,
          contentMap = contentMap,
        )
      }
    }

    // Il livello che se ne va durante un cambio di livello (F5A.3): posato dov'era, sotto quello che arriva, senza
    // goccia e senza semantica (non si sceglie piu'), con la sua opacita', la sua scala e la sua sfocatura. Il ritaglio
    // del pannello vale anche per lui: le voci restano dentro il vetro che cambia forma.
    if (outgoing != null) Box(
      if (anchored) Modifier.matchParentSize().drawWithContent { drawInMenuSilhouette(lens!!, travelClip) } else Modifier.matchParentSize(),
    ) {
      Box(
        modifier = Modifier
          .fluidMenuOutgoingPlacement(outgoing.offset, outgoing.size)
          .clearAndSetSemantics { }
          .graphicsLayer {
            alpha = outgoing.alpha().coerceIn(0f, 1f)
            val s = outgoing.scale()
            scaleX = s
            scaleY = s
            renderEffect = fluidMenuLevelBlurEffect(outgoing.blurDp, this.density)
          }
          .drawBehind {
            drawMenuSeparators(outgoing.structure, { 0f }, separator, band, insetPx, tileInsetPx, hairlinePx)
          },
      ) {
        FluidMenuCells(outgoing.structure, plain = false, tileLabels = outgoing.tileLabels, weight = { 0f }, onPick = {}, interactive = interactive)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        // Le voci le ritaglia lo shader della lente (`clipInk`), non un ritaglio sopra il loro strato: sotto un ritaglio Skia
        // da' all'effetto solo lo strato dentro il ritaglio, e la lente che prende le voci da fuori il vetro trovava il bordo
        // dello strato ripetuto (le strisce di «Condividi») o tagliato netto («Elimina» a meta'), solo sul dispositivo (S4g, NO 1).
        .then(if (anchored) Modifier.drawWithContent {
          val plan = lens!!.physics.ensurePlan()
          val inShader = lensEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensEffect.clips(plan)
          drawInMenuSilhouette(lens, travelClip, inShader)
        } else Modifier)
        // La mappa del viaggio sul nodo: e' quella del dito (il `clickable` delle righe) e della semantica.
        .graphicsLayer {
          val map = contentMap()
          translationX = map.translation.x
          translationY = map.translation.y
          scaleX = map.scale
          scaleY = map.scale
        }
        // L'opacita' e la lente su uno strato a parte (S4g-bis, b). Ingrandite e portate verso il tasto, le voci di «Altro»
        // uscivano dalla finestra: sotto un `RenderEffect` Skia rende nello strato solo la parte che si vede, e la lente, che
        // prende le icone a destra da li', le trovava tagliate al bordo dello schermo (chiaro f0010–f0012, scuro f0085–f0086).
        // Lo strato si sposta dentro la finestra e lo shader lo riporta al suo posto (`inkShift`); il dito resta dov'era.
        .drawWithContent {
          val map = contentMap()
          var shift = Offset.Zero
          var bounds: Rect? = null
          val effect = if (lens != null && lensEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val focus = lens.journey.focus.value
            val blur = fluidMenuLensBlurDp(focus)
            val strength = fluidMenuLensStrength(focus)
            val clipInk = lens.journey.anchor != null &&
              (lens.physics.ensurePlan().dropRest != null || travelClip?.invoke() != null)
            if (blur > 0f || strength > 0f || clipInk) {
              val padding = Offset(size.width, size.height) * ((1f - map.scale) / 2f)
              val plan = lens.physics.ensurePlan()
              val origin = lens.panelTopLeft + map.translation + padding
              val blurDp = max(blur, levelBlurDp?.invoke() ?: 0f)
              if (lensEffect.shades(plan, strength, clipInk)) {
                fluidMenuInkFrame(coordinates.value, size, map, fluidMenuInkHaloPx(blurDp * this.density, map.scale))?.let {
                  shift = it.shift
                  bounds = it.bounds
                }
              }
              lensEffect.effect(
                plan = plan,
                origin = origin,
                scale = map.scale,
                density = this.density,
                blurDp = blurDp,
                strength = strength,
                clipInk = clipInk,
                // Le voci che la lente ingrandisce oltre il vetro entrano schiacciate nel bordo, mai tagliate (S4f, NO 2).
                overflow = if (clipInk) glassMenuLensOverflow(plan.bounds, origin, size, map.scale) else null,
                inkShift = shift,
              )
            } else {
              fluidMenuLevelBlurEffect(levelBlurDp, this.density)
            }
          } else {
            fluidMenuLevelBlurEffect(levelBlurDp, this.density)
          }
          inkLens.alpha = contentAlpha().coerceIn(0f, 1f)
          inkLens.renderEffect = effect
          inkLens.translationX = shift.x
          inkLens.translationY = shift.y
          // Le celle hanno il loro layer: qui si registra solo quello, e le righe non si ridisegnano a ogni fotogramma.
          inkLens.record { this@drawWithContent.drawContent() }
          // Il limite della lente (S4g-ter): senza, lo shader della lente, che puo' colorare anche il trasparente, faceva allocare
          // a Skia un intermedio grande quanto la finestra divisa per la scala delle voci (2048×3072 a 0,19), e sul Tab S9 il
          // driver ci metteva fino a ~1 s alla prima apertura del gruppo. Il limite e' lo strato delle voci, intero e spostato
          // nella finestra, piu' la sfocatura: la lente riceve tutte le voci e l'intermedio resta della misura del pannello.
          val limit = bounds
          if (limit != null) {
            FluidMenuInkProbe.onBounds?.invoke(FluidMenuInkReport(limit, size, map.scale))
            clipRect(limit.left, limit.top, limit.right, limit.bottom) { drawLayer(inkLens) }
          } else {
            drawLayer(inkLens)
          }
        },
    ) {
      // Le celle in un nodo col suo layer: il nodo di sopra si ridisegna a ogni fotogramma del viaggio, loro solo quando
      // cambiano (la riga premuta, la goccia), come prima.
      Box(
        Modifier
          .matchParentSize()
          .graphicsLayer { }
          .drawBehind {
            drawMenuSeparators(cells, { drop.weight(it) }, separator, band, insetPx, tileInsetPx, hairlinePx)
          },
      ) {
        FluidMenuCells(cells, plain = plain, tileLabels = tileLabels, weight = { drop.weight(it) }, onPick = onPick, interactive = interactive)
      }
    }
  }
}

/**
 * Di quanto spostare le voci, in pixel della finestra, perche' il rettangolo [ink] in cui si disegnano stia tutto nella finestra
 * [window]: zero se ci sta gia', il minimo verso l'interno se esce da un lato, al centro se e' piu' grande di lei.
 *
 * S4g-bis sul Tab S9 (b): nella nascita di «Altro» la lente ingrandisce le voci fino a 1,42× e le porta verso il tasto, vicino
 * al bordo destro dello schermo. Lo strato delle voci usciva dalla finestra fino a ~300 px, e sotto un `RenderEffect` Skia rende
 * nello strato del filtro solo la parte dentro lo schermo (lo shader della lente non dichiara quanto lontano legge): la lente,
 * che porta nel bordo del vetro le voci di fuori (il tiro dei lati di S4f), trovava le icone di condivisione e della stella
 * tagliate al bordo dello schermo (chiaro f0010–f0012, scuro f0085–f0086). In RTL lo stesso menu sta a sinistra, e lo
 * spostamento dell'inchiostro verso destra lo teneva nella finestra: le icone entravano intere. Al banco lo strato e' sempre
 * intero, ed era verde.
 */
internal fun fluidMenuInkOnScreen(ink: Rect, window: Rect): Offset {
  fun axis(lo: Float, hi: Float, min: Float, max: Float): Float = when {
    hi - lo > max - min -> (min + max - lo - hi) / 2f
    hi > max -> max - hi
    lo < min -> min - lo
    else -> 0f
  }
  return Offset(axis(ink.left, ink.right, window.left, window.right), axis(ink.top, ink.bottom, window.top, window.bottom))
}

/**
 * Lo strato della lente delle voci in questo fotogramma, nelle unita' del nodo che porta la mappa del viaggio (prima della sua
 * scala): di quanto si sposta per stare nella finestra ([shift], [fluidMenuInkOnScreen]) e il rettangolo in cui si disegna
 * ([bounds], [fluidMenuInkLimit]).
 */
internal class FluidMenuInkFrame(val shift: Offset, val bounds: Rect)

/**
 * Lo spostamento e il limite dello strato delle voci per il nodo grande [size], dentro il pannello [panel], con la mappa [map]
 * e il margine [haloPx] (pixel della finestra, [fluidMenuInkHaloPx]). Null senza coordinate (prima della prima misura).
 */
internal fun fluidMenuInkFrame(panel: LayoutCoordinates?, size: Size, map: FluidMenuContentMap, haloPx: Float): FluidMenuInkFrame? {
  if (panel == null || !panel.isAttached || !(map.scale > 0.0001f)) return null
  val s = map.scale
  val center = Offset(size.width / 2f, size.height / 2f)
  val ink = Rect(panel.localToRoot(center + map.translation - center * s), panel.localToRoot(center + map.translation + center * s))
  val window = Rect(Offset.Zero, panel.findRootCoordinates().size.toSize())
  val shift = fluidMenuInkOnScreen(ink, window)
  val limit = fluidMenuInkLimit(ink, window, shift, haloPx)
  // Dalla finestra al nodo: il centro del nodo cade dove la mappa porta il centro del pannello.
  val at = panel.localToRoot(center + map.translation)
  fun local(p: Offset) = center + (p - at) / s
  return FluidMenuInkFrame(Offset(shift.x / s, shift.y / s), Rect(local(limit.topLeft), local(limit.bottomRight)))
}

/**
 * Il limite dello strato della lente delle voci, in pixel della finestra (S4g-ter): lo strato [ink] spostato di [shift] dentro
 * la finestra [window], allargato di [haloPx] e tagliato alla finestra.
 *
 * Lo shader della lente puo' colorare anche il trasparente, e senza un limite Skia alloca per il filtro un intermedio grande
 * quanto la finestra, nello spazio dello strato: la finestra divisa per la scala delle voci (2048×3072 a 0,19 sul banco). Sul
 * Tab S9 l'allocazione dentro il driver Adreno ha portato la prima apertura del gruppo a 862 ms in chiaro e 1.061 in scuro
 * (S4g contro S4f, bisezione F7.3k). Prima di S4g il limite era il ritaglio della sagoma, che pero' toglieva alla lente le voci
 * di fuori dal vetro (il NO 1 di S4g). Questo le tiene tutte: la lente tira le voci verso l'esterno, quindi quello che disegna
 * sta dentro lo strato delle voci, e il vetro piu' grande delle voci (il seme) fuori da loro e' trasparente. L'intermedio e'
 * quello del pannello piu' il margine, a ogni scala.
 */
internal fun fluidMenuInkLimit(ink: Rect, window: Rect, shift: Offset, haloPx: Float): Rect =
  ink.translate(shift).inflate(haloPx).intersect(window)

/**
 * Il margine del limite delle voci in pixel della finestra: la sfocatura prima della lente ([blurPx], in pixel dello strato,
 * fino a tre volte il suo raggio) alla scala [scale], e qualche pixel per l'antialias e l'arrotondamento dei limiti.
 */
internal fun fluidMenuInkHaloPx(blurPx: Float, scale: Float): Float = (3f * blurPx + 2f) * scale + 4f

/** Solo nei test (S4g-ter): il limite dello strato della lente delle voci a ogni fotogramma in cui c'e'. Null altrove. */
internal object FluidMenuInkProbe {
  @Volatile var onBounds: ((FluidMenuInkReport) -> Unit)? = null
}

/** Il limite [bounds] (unita' del nodo delle voci, grande [node]) alla scala [scale] delle voci. */
internal class FluidMenuInkReport(val bounds: Rect, val node: Size, val scale: Float)

/**
 * Disegna dentro la sagoma del vetro dell'ancora mentre si forma o torna (la goccia, o il ritaglio del viaggio), in coordinate
 * del pannello; a riposo, o quando la ritaglia gia' lo shader ([inShader]), senza ritaglio.
 */
private fun ContentDrawScope.drawInMenuSilhouette(lens: FluidMenuLens, travelClip: (() -> RoundRect?)?, inShader: Boolean = false) {
  val plan = lens.physics.ensurePlan()
  if (inShader || (plan.dropRest == null && travelClip?.invoke() == null)) {
    drawContent()
  } else {
    val origin = lens.panelTopLeft
    translate(-origin.x, -origin.y) {
      clipPath(plan.silhouette) {
        translate(origin.x, origin.y) { this@drawInMenuSilhouette.drawContent() }
      }
    }
  }
}

/** Le celle di un livello: le righe alle loro quote, le tessere nella loro parte di fila. */
@Composable
private fun FluidMenuCells(
  cells: FluidMenuStructure,
  plain: Boolean,
  tileLabels: Boolean,
  weight: (Int) -> Float,
  onPick: (Int) -> Unit,
  interactive: Boolean = true,
) {
  val density = LocalDensity.current
  for (index in 0 until cells.size) {
    val entry = cells.entries[index]
    val height = with(density) { cells.heights[index].toDp() }
    if (entry.kind == FluidMenuCellKind.Tile) {
      FluidGlassMenuTile(
        entry = entry,
        labeled = tileLabels,
        weight = { weight(index) },
        onClick = { onPick(index) },
        modifier = Modifier.fluidMenuTileCell(cells, index),
        interactive = interactive,
      )
    } else {
      FluidGlassMenuRow(
        action = entry.action,
        weight = { weight(index) },
        onClick = { onPick(index) },
        modifier = Modifier
          .offset { IntOffset(0, cells.tops[index].roundToInt()) }
          .fillMaxWidth()
          .height(height),
        // L'intestazione di un sottomenu e' il suo titolo e basta: il sottotitolo diceva la scelta, che ora si vede sotto.
        subtitle = if (plain || entry.kind == FluidMenuCellKind.Header) null else entry.action.subtitle,
        submenu = !plain && entry.opensSubmenu,
        header = entry.kind == FluidMenuCellKind.Header,
        interactive = interactive,
      )
    }
  }
}

/**
 * Il livello che se ne va durante un cambio di livello (F5A.3): le sue celle, dove stava il suo pannello rispetto a
 * quello nuovo ([offset], pixel, gia' posati all'intero come i pannelli), quanto era grande, e le tre grandezze del
 * cambio di contenuto, lette in disegno.
 */
internal class FluidMenuOutgoingLevel(
  val structure: FluidMenuStructure,
  val offset: IntOffset,
  val size: IntSize,
  val tileLabels: Boolean,
  val alpha: () -> Float,
  val scale: () -> Float,
  val blurDp: () -> Float,
)

/** Posa il livello che se ne va alla sua taglia e al suo posto, anche fuori dal pannello nuovo (che e' piu' piccolo). */
private fun Modifier.fluidMenuOutgoingPlacement(offset: IntOffset, size: IntSize): Modifier =
  this.layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(size.width.coerceAtLeast(0), size.height.coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeable.place(offset.x, offset.y)
    }
  }

/** Posa una tessera della fila nella sua cella: larga quanto la sua parte di pannello, dalla larghezza di adesso. */
private fun Modifier.fluidMenuTileCell(cells: FluidMenuStructure, index: Int): Modifier =
  this.layout { measurable, constraints ->
    val cell = cells.cell(index, constraints.maxWidth.toFloat())
    val w = cell.width.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(w, cell.height.roundToInt().coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeable.place(cell.left.roundToInt(), cell.top.roundToInt())
    }
  }

/** Le celle di sempre per una lista e le sue quote: una riga piena per voce (il menu senza struttura). */
internal fun fluidMenuStructureOfLayout(actions: List<FluidContextAction>, layout: FluidMenuPanelLayout): FluidMenuStructure {
  val n = minOf(actions.size, layout.tops.size)
  return FluidMenuStructure(
    entries = List(n) { i -> FluidMenuEntry(actions[i], FluidMenuCellKind.Row, actions[i].startsSection, i) },
    tops = layout.tops.copyOf(n),
    heights = FloatArray(n) { layout.rowHeightPx },
    columns = IntArray(n),
    columnCounts = IntArray(n) { 1 },
    height = layout.height,
    sectionBands = layout.sectionBands,
    sectionGapPx = layout.sectionGapPx,
  )
}

/** Tutte righe piene della stessa altezza: la goccia ha una taglia sola, come prima della 4.0. */
internal fun fluidMenuCellsUniform(cells: FluidMenuStructure): Boolean {
  if (cells.size == 0) return true
  val h = cells.heights[0]
  for (i in 0 until cells.size) if (cells.columnCounts[i] > 1 || cells.heights[i] != h) return false
  return true
}

/** La sfocatura del cambio di livello come effetto del layer delle voci, o niente (API < 31, o a fuoco). */
private fun fluidMenuLevelBlurEffect(blurDp: (() -> Float)?, density: Float): androidx.compose.ui.graphics.RenderEffect? {
  if (blurDp == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
  val px = blurDp() * density
  return if (px > 0.05f) androidx.compose.ui.graphics.BlurEffect(px, px, androidx.compose.ui.graphics.TileMode.Decal) else null
}

/** La texture delle voci parla le coordinate della stessa finestra da cui legge il campo. */
internal class FluidMenuLens(
  val journey: FluidMenuJourney,
  val physics: FluidPhysicsState,
  private val panel: Rect,
) {
  val panelTopLeft: Offset get() = panel.topLeft

  fun contentMap(): FluidMenuContentMap {
    val clip = physics.travelClipIn(panel, journey.progress.value)
    val visible = clip?.let { Rect(it.left, it.top, it.right, it.bottom).translate(panel.topLeft) } ?: panel
    return fluidMenuLensMap(
      panel, visible, journey.magnifiesInk, journey.progress.value, journey.focus.value, returning = journey.bornReturn,
    )
  }
}

/**
 * Il ritaglio del contenuto sul corpo di vetro in viaggio (3.0): un layer col clip acceso solo mentre
 * [clip] da' una sagoma. Nessuna trasformazione: le coordinate del pannello restano quelle del dito.
 */
private fun Modifier.fluidMenuTravelClip(clip: () -> RoundRect?): Modifier = graphicsLayer {
  val r = clip()
  if (r != null) {
    shape = FluidMenuRoundRectShape(r)
    this.clip = true
  } else {
    this.clip = false
  }
}

/** Un rettangolo arrotondato gia' posato: l'outline e' lui, qualunque sia la taglia del nodo. */
internal class FluidMenuRoundRectShape(private val round: RoundRect) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Rounded(round)

  override fun equals(other: Any?): Boolean = other is FluidMenuRoundRectShape && other.round == round

  override fun hashCode(): Int = round.hashCode()
}

/**
 * I separatori: una riga sottile al confine fra due voci della stessa sezione (rientrata come quelle
 * delle liste raggruppate), una banda fra due sezioni. Disegnati, non scatole da mezzo dp: una scatola
 * aggiunge altezza che l'aritmetica delle celle non conta, e il dito sceglierebbe la riga sbagliata.
 * I due accanto alla voce sotto la goccia si spengono: la goccia e' la voce, e una riga che la taglia
 * la fa sembrare due.
 */
private fun DrawScope.drawMenuSeparators(
  cells: FluidMenuStructure,
  weight: (Int) -> Float,
  separator: Color,
  band: Color,
  insetPx: Float,
  tileInsetPx: Float,
  hairlinePx: Float,
) {
  for (y in cells.sectionBands) {
    drawRect(band, topLeft = Offset(0f, y), size = Size(size.width, cells.sectionGapPx))
  }
  for (i in 1 until cells.size) {
    if (cells.entries[i].startsSection) continue
    val fade = 1f - max(weight(i - 1), weight(i))
    if (fade <= 0.01f) continue
    val color = separator.copy(alpha = separator.alpha * fade)
    if (cells.sameLine(i - 1, i)) {
      // Fra due tessere della fila: un filo verticale, staccato dai bordi alto e basso della fila.
      val x = cells.cell(i, size.width).left - hairlinePx / 2f
      drawRect(
        color = color,
        topLeft = Offset(x, cells.tops[i] + tileInsetPx),
        size = Size(hairlinePx, cells.heights[i] - tileInsetPx * 2f),
      )
      continue
    }
    val y = cells.tops[i] - hairlinePx / 2f
    drawRect(
      color = color,
      topLeft = Offset(insetPx, y),
      size = Size(size.width - insetPx, hairlinePx),
    )
  }
}

/**
 * Una riga del menu. [subtitle] e' la seconda riga di testo (4.0); [submenu] mette il gallone di una voce che apre un
 * sottomenu; [header] e' la voce madre in cima al sottomenu aperto: in grassetto, col gallone girato verso il basso
 * (il menu e' «aperto su di lei»), e sceglierla torna indietro.
 */
@Composable
private fun FluidGlassMenuRow(
  action: FluidContextAction,
  weight: () -> Float,
  onClick: () -> Unit,
  modifier: Modifier,
  subtitle: String? = null,
  submenu: Boolean = false,
  header: Boolean = false,
  interactive: Boolean = true,
) {
  val base = if (action.destructive && !header) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  val tone = if (action.enabled || header) base else base.copy(alpha = base.alpha * 0.38f)
  Row(
    modifier = modifier
      // Per TalkBack e tastiera: il ruolo, l'azione, lo stato spento. Dal dito non scatta mai — il
      // pannello consuma il down prima di lui — e la scelta la fa la selezione. In chiusura niente: il dito passa sotto (S4i).
      .then(if (!interactive) Modifier else Modifier.clickable(
        interactionSource = null,
        indication = null,
        enabled = action.enabled || header,
        role = Role.Button,
        onClick = onClick,
      ))
      .semantics { if (action.selected && !header) selected = true }
      .drawWithContent {
        // La voce sotto la goccia si fa avanti di un 5%, dal bordo d'attacco: e' la goccia che la
        // solleva. Solo in disegno — niente layer, niente ricomposizione mentre il dito scorre. Non
        // 1,12 come un tasto: una riga larga uscirebbe dal pannello.
        val w = weight()
        if (w > 0.001f) {
          val s = 1f + FluidMenuRowLift * w
          scale(s, s, pivot = Offset(0f, size.height / 2f)) { this@drawWithContent.drawContent() }
        } else {
          drawContent()
        }
      }
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val labelStyle = if (header) {
      MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    } else {
      MaterialTheme.typography.bodyLarge
    }
    if (subtitle == null) {
      Text(
        text = action.label,
        style = labelStyle,
        color = tone,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    } else {
      // Il sottotitolo e' un'informazione in piu', non una seconda voce: piu' piccolo e meno contrastato.
      Column(modifier = Modifier.weight(1f)) {
        Text(text = action.label, style = labelStyle, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = tone.copy(alpha = tone.alpha * FluidMenuSubtitleAlpha),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    // L'icona della voce a 22 dp dalla 4.0 (erano 20): quella della barra divisa per poco piu' di 1,2, come in
    // Apple (R6). Il colore e' quello della voce, che e' gia' l'inchiostro del testo (o `error`).
    action.icon?.let {
      FluidIcon(imageVector = it, contentDescription = null, tint = tone, size = FluidMenuRowGlyph)
    }
    // La spunta della voce scelta (3.0.1), a destra come nei menu di scelta di iOS, nel colore della voce:
    // e' un segno di stato, non un'azione, e l'accento lo hanno gia' la goccia e il tasto.
    if (action.selected && !header) {
      FluidIcon(imageVector = FluidIcons.Check, contentDescription = null, tint = tone, size = FluidMenuRowGlyph)
    }
    // Il gallone del sottomenu (4.0): nella colonna della spunta, un filo piu' tenue della voce. Girato in giu'
    // sull'intestazione del sottomenu aperto, come il gallone di un gruppo che si e' aperto.
    if (submenu || header) {
      FluidIcon(
        imageVector = FluidIcons.ChevronRight,
        contentDescription = null,
        tint = tone.copy(alpha = tone.alpha * FluidMenuSubtitleAlpha),
        size = FluidMenuRowGlyph,
        modifier = if (header) Modifier.rotate(90f) else Modifier,
      )
    }
  }
}

/**
 * Una tessera della fila in cima (4.0, F5A.3): il simbolo al centro e, se [labeled], l'etichetta corta sotto. Senza
 * simbolo, l'etichetta. Per TalkBack e' un pulsante con l'etichetta della voce anche quando non la si vede.
 */
@Composable
private fun FluidGlassMenuTile(
  entry: FluidMenuEntry,
  labeled: Boolean,
  weight: () -> Float,
  onClick: () -> Unit,
  modifier: Modifier,
  interactive: Boolean = true,
) {
  val action = entry.action
  val base = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  val tone = if (action.enabled) base else base.copy(alpha = base.alpha * 0.38f)
  Column(
    modifier = modifier
      .then(if (!interactive) Modifier else Modifier.clickable(
        interactionSource = null,
        indication = null,
        enabled = action.enabled,
        role = Role.Button,
        onClick = onClick,
      ))
      .semantics {
        // L'etichetta che non si vede si legge lo stesso; quella che si vede la legge gia' il suo testo.
        if (!labeled && action.icon != null) contentDescription = action.label
        if (action.selected) selected = true
      }
      .drawWithContent {
        // La tessera sotto la goccia si fa avanti dal centro, come un pulsante dell'avviso.
        val w = weight()
        if (w > 0.001f) {
          val s = 1f + FluidMenuRowLift * w
          scale(s, s) { this@drawWithContent.drawContent() }
        } else {
          drawContent()
        }
      }
      .padding(horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    val icon = action.icon
    if (icon != null) {
      FluidIcon(imageVector = icon, contentDescription = null, tint = tone, size = FluidMenuRowGlyph)
    }
    if (labeled || icon == null) {
      Text(
        text = action.label,
        style = MaterialTheme.typography.bodySmall,
        color = tone,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = if (icon != null) Modifier.padding(top = FluidMenuTileGap) else Modifier,
      )
    }
  }
}

/**
 * La goccia del menu: **la** goccia, `glassDrop` (`glass/interaction/GlassDrop.kt`), con la lente del
 * pomello dello slider e la faccia bianca dei pomelli, in una capsula rientrata 4 × 2 dp dalla cella.
 * Fino a F1B.3 era una copia in linea della ricetta di Kyant; ora e' la stessa funzione dei controlli, e
 * col materiale nuovo ha la loro stessa luce (e la loro ombra, che la regola del vetro annidato riduce:
 * la goccia sta nel pannello).
 *
 * Ciò che e' del menu e non della goccia sta nel suo movimento ([GlassDropMotion]: la scala del viaggio
 * per quella della molla, la velocita' in voci al secondo) e nel posto ([fluidMenuDropCenter] fra le
 * celle, portato sulla sagoma di adesso da [contentMap], e la presenza), che `glassDrop` mette nel suo
 * `layerBlock` prima della scala. Lo schiacciamento sta sull'asse del moto (verticale per un menu;
 * orizzontale fra i due pulsanti affiancati di un avviso). Il renderer inverte quel blocco quando
 * campiona, quindi la lente ha in mano quello che c'e' davvero sotto, ovunque sia.
 *
 * Il cancello `drawWithContent` fuori dal layer e' la seconda trappola del vetro in movimento: a
 * presenza zero non si registra niente.
 *
 * @param cellSize la taglia di una cella, dalla taglia del pannello (le celle sono larghe uguali).
 */
@Composable
internal fun FluidMenuDrop(
  state: FluidMenuDropState,
  selection: FluidMenuSelection,
  backdrop: GlassBackdropState,
  cellSize: (panel: Size) -> Size,
  vertical: Boolean,
  contentAlpha: () -> Float,
  contentMap: () -> FluidMenuContentMap,
) {
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val insetX = with(density) { FluidMenuDropInsetX.toPx() }
  val insetY = with(density) { FluidMenuDropInsetY.toPx() }

  // Il movimento della goccia del menu: la pressione della molla; la scala, quella della molla per quella
  // del viaggio (la goccia cresce con la sagoma che la porta); la velocita', in celle al secondo.
  val motion = remember(state) {
    GlassDropMotion(
      progress = { state.drag.pressProgress },
      scaleX = { contentMap().scale * state.drag.scaleX },
      scaleY = { contentMap().scale * state.drag.scaleY },
      velocity = { state.drag.velocity * state.span },
    )
  }
  // Il posto: la capsula sta posata al centro del pannello (vedi `layoutCell`); la si porta sul centro della
  // cella sotto il dito, e da li' dove la sagoma di adesso porta il contenuto — la stessa mappa delle voci,
  // attorno allo stesso centro. E la presenza, per l'opacita' del contenuto.
  val place: GraphicsLayerScope.() -> Unit = remember(state, selection) {
    {
      val g = selection.geometry
      val map = contentMap()
      val fromCenter = if (g != null && g.cells.isNotEmpty()) {
        val local = fluidMenuDropCenter(g, state.drag.value) - g.panel.topLeft
        (local - Offset(g.panel.width / 2f, g.panel.height / 2f)) * map.scale
      } else {
        Offset.Zero
      }
      translationX = fromCenter.x + map.translation.x
      translationY = fromCenter.y + map.translation.y
      alpha = (state.presence.value * contentAlpha()).coerceIn(0f, 1f)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawWithContent {
        if (state.presence.value * contentAlpha() > 0.004f) drawContent()
      }
      .layoutCell(cellSize, insetX, insetY)
      .glassDrop(
        backdrop = backdrop.backdrop,
        motion = motion,
        lensSize = GlassDropLens.Slider,
        face = GlassDropFaces.Light,
        squashDivisor = GlassDropRecipe.SquashDivisor,
        squashSigned = false,
        reducedMotion = { reducedMotion },
        shape = FluidCapsuleShape,
        squashVertical = vertical,
        place = place,
      ),
  )
}

/**
 * Porta il nodo della goccia dalla taglia del pannello a quella di una cella rientrata, centrata: la
 * traslazione del `layerBlock` parte cosi' dal centro del pannello.
 */
private fun Modifier.layoutCell(cellSize: (Size) -> Size, insetX: Float, insetY: Float): Modifier =
  this.layout { measurable, constraints ->
    val panel = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    val cell = cellSize(panel)
    val w = (cell.width - insetX * 2f).roundToInt().coerceAtLeast(1)
    val h = (cell.height - insetY * 2f).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeable.place((constraints.maxWidth - w) / 2, (constraints.maxHeight - h) / 2)
    }
  }

/** Quanto si solleva la voce sotto la goccia, a pressione piena. */
private const val FluidMenuRowLift = 0.05f

/** La goccia premuta cresce di un 4%: la pillola della barra, non un tasto. */
internal const val FluidMenuDropPressedScale = 1.04f

/** Al piu' quanto la goccia aspetta di tornare bianca prima di sparire, dopo una scelta. */
private const val FluidMenuDropConfirmMillis = 140L

private val FluidMenuDropInsetX = 4.dp
private val FluidMenuDropInsetY = 2.dp
private val FluidMenuSeparatorInset = 16.dp

/** Quanto il filo fra due tessere della fila resta lontano dai bordi alto e basso della fila. */
private val FluidMenuTileSeparatorInset = 10.dp

/** Fra il simbolo e l'etichetta di una tessera [FluidMenuElementSize.Medium]. */
internal val FluidMenuTileGap = 4.dp

/** Quanto e' tenue il sottotitolo (e il gallone) rispetto all'inchiostro della voce. */
private const val FluidMenuSubtitleAlpha = 0.6f
