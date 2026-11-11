package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropRecipe
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropSquash
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
 */
@Composable
internal fun FluidGlassMenuPanel(
  actions: List<FluidContextAction>,
  selection: FluidMenuSelection,
  layout: FluidMenuPanelLayout,
  panelMaterial: GlassBackdropState?,
  pageBackdrop: GlassBackdropState?,
  contentAlpha: () -> Float,
  contentMap: () -> FluidMenuContentMap,
  onPick: (Int) -> Unit,
  modifier: Modifier = Modifier,
  paneTitle: String? = null,
  travelClip: (() -> RoundRect?)? = null,
) {
  val density = LocalDensity.current
  val coordinates = remember { FluidMenuCoordinates() }
  val enabled = remember(actions) { actions.map { it.enabled } }
  val drop = rememberFluidMenuDropState(selection, actions.size)
  val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  val band = MaterialTheme.colorScheme.onSurface.copy(alpha = if (GlassDefaults.isDarkSurface()) 0.08f else 0.05f)
  val insetPx = with(density) { FluidMenuSeparatorInset.toPx() }
  val hairlinePx = with(density) { 0.5.dp.toPx() }

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
        if (current == null || current.panel != panel || current.enabled != enabled) {
          selection.geometry = fluidMenuGeometryOf(layout, enabled, panel)
        }
      }
      .fluidMenuPointerArea(selection, coordinates)
      .semantics {
        paneTitle?.let { this.paneTitle = it }
        isTraversalGroup = true
      }
      .then(if (travelClip != null) Modifier.fluidMenuTravelClip(travelClip) else Modifier),
  ) {
    if (pageBackdrop != null && actions.isNotEmpty()) {
      FluidMenuDrop(
        state = drop,
        selection = selection,
        backdrop = pageBackdrop,
        cellSize = { Size(it.width, layout.rowHeightPx) },
        vertical = true,
        contentAlpha = contentAlpha,
        contentMap = contentMap,
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          alpha = contentAlpha().coerceIn(0f, 1f)
          val map = contentMap()
          translationX = map.translation.x
          translationY = map.translation.y
          scaleX = map.scale
          scaleY = map.scale
        }
        .drawBehind {
          drawMenuSeparators(layout, actions, drop, separator, band, insetPx, hairlinePx)
        },
    ) {
      val rowHeight = with(density) { layout.rowHeightPx.toDp() }
      actions.forEachIndexed { index, action ->
        FluidGlassMenuRow(
          action = action,
          weight = { drop.weight(index) },
          onClick = { onPick(index) },
          modifier = Modifier
            .offset { IntOffset(0, layout.tops[index].roundToInt()) }
            .fillMaxWidth()
            .height(rowHeight),
        )
      }
    }
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
private class FluidMenuRoundRectShape(private val round: RoundRect) : Shape {
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
  layout: FluidMenuPanelLayout,
  actions: List<FluidContextAction>,
  drop: FluidMenuDropState,
  separator: Color,
  band: Color,
  insetPx: Float,
  hairlinePx: Float,
) {
  for (y in layout.sectionBands) {
    drawRect(band, topLeft = Offset(0f, y), size = Size(size.width, layout.sectionGapPx))
  }
  for (i in 1 until actions.size) {
    if (actions[i].startsSection) continue
    val fade = 1f - max(drop.weight(i - 1), drop.weight(i))
    if (fade <= 0.01f) continue
    val y = layout.tops[i] - hairlinePx / 2f
    drawRect(
      color = separator.copy(alpha = separator.alpha * fade),
      topLeft = Offset(insetPx, y),
      size = Size(size.width - insetPx, hairlinePx),
    )
  }
}

@Composable
private fun FluidGlassMenuRow(
  action: FluidContextAction,
  weight: () -> Float,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val base = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  val tone = if (action.enabled) base else base.copy(alpha = base.alpha * 0.38f)
  Row(
    modifier = modifier
      // Per TalkBack e tastiera: il ruolo, l'azione, lo stato spento. Dal dito non scatta mai — il
      // pannello consuma il down prima di lui — e la scelta la fa la selezione.
      .clickable(
        interactionSource = null,
        indication = null,
        enabled = action.enabled,
        role = Role.Button,
        onClick = onClick,
      )
      .semantics { if (action.selected) selected = true }
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
    Text(
      text = action.label,
      style = MaterialTheme.typography.bodyLarge,
      color = tone,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    // L'icona della voce a 22 dp dalla 4.0 (erano 20): quella della barra divisa per poco piu' di 1,2, come in
    // Apple (R6). Il colore e' quello della voce, che e' gia' l'inchiostro del testo (o `error`).
    action.icon?.let {
      FluidIcon(imageVector = it, contentDescription = null, tint = tone, size = FluidMenuRowGlyph)
    }
    // La spunta della voce scelta (3.0.1), a destra come nei menu di scelta di iOS, nel colore della voce:
    // e' un segno di stato, non un'azione, e l'accento lo hanno gia' la goccia e il tasto.
    if (action.selected) {
      FluidIcon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = tone, size = FluidMenuRowGlyph)
    }
  }
}

/**
 * La goccia del menu: la ricetta di `glassDrop` (Kyant), con i numeri del pomello dello slider —
 * bianca a riposo, lente 10/14 dp sotto il dito, `Ambient` ÷ 1,5, ombra interna solo premuta, ombra
 * fissa 4 dp al 5% — e una capsula rientrata 4 × 2 dp dalla cella.
 *
 * Tutto nel `layerBlock`: il posto ([fluidMenuDropCenter] fra le celle, portato sulla sagoma di
 * adesso da [contentMap]), la scala (quella del viaggio per quella della molla) e lo schiacciamento
 * sull'asse del moto (verticale per un menu; orizzontale fra i due pulsanti affiancati di un avviso).
 * Il renderer inverte quel blocco quando campiona, quindi la lente ha in mano quello che c'e' davvero
 * sotto, ovunque sia.
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
  val lens = GlassDropLens.Slider

  val layerBlock: GraphicsLayerScope.() -> Unit = remember(state, selection, vertical, reducedMotion) {
    {
      val g = selection.geometry
      val map = contentMap()
      // La capsula sta posata al centro del pannello (vedi `layoutCell`): la si porta sul centro
      // della cella sotto il dito, e da li' dove la sagoma di adesso porta il contenuto — la stessa
      // mappa delle voci, attorno allo stesso centro.
      val fromCenter = if (g != null && g.cells.isNotEmpty()) {
        val local = fluidMenuDropCenter(g, state.drag.value) - g.panel.topLeft
        (local - Offset(g.panel.width / 2f, g.panel.height / 2f)) * map.scale
      } else {
        Offset.Zero
      }
      translationX = fromCenter.x + map.translation.x
      translationY = fromCenter.y + map.translation.y
      val squash = glassDropSquash(
        velocity = state.drag.velocity * state.span,
        divisor = GlassDropRecipe.SquashDivisor,
        signed = false,
        reducedMotion = reducedMotion,
      )
      val sx = map.scale * state.drag.scaleX
      val sy = map.scale * state.drag.scaleY
      if (vertical) {
        scaleY = sy / squash.xDivisor
        scaleX = sx * squash.yFactor
      } else {
        scaleX = sx / squash.xDivisor
        scaleY = sy * squash.yFactor
      }
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
      .drawBackdrop(
        backdrop = backdrop.backdrop,
        shape = { FluidCapsuleShape },
        effects = {
          val p = state.drag.pressProgress.coerceIn(0f, 1f)
          blur(GlassDropRecipe.RestBlur.toPx() * (1f - p))
          lens(lens.height.toPx() * p, lens.amount.toPx() * p, chromaticAberration = p > 0.01f)
        },
        highlight = {
          Highlight.Ambient.copy(
            width = Highlight.Ambient.width / GlassDropRecipe.HighlightDivisor,
            blurRadius = Highlight.Ambient.blurRadius / GlassDropRecipe.HighlightDivisor,
            alpha = state.drag.pressProgress.coerceIn(0f, 1f),
          )
        },
        shadow = {
          Shadow(
            radius = GlassDropRecipe.RestShadowRadius,
            color = Color.Black.copy(alpha = GlassDropRecipe.RestShadowAlpha),
          )
        },
        innerShadow = {
          val p = state.drag.pressProgress.coerceIn(0f, 1f)
          InnerShadow(radius = GlassDropRecipe.PressedInnerShadowRadius * p, alpha = p)
        },
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(Color.White.copy(alpha = (1f - state.drag.pressProgress).coerceIn(0f, 1f))) },
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
