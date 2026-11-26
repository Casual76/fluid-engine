package dev.antigravity.fluidengine.ink

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidColorSwatches
import dev.antigravity.fluidengine.ui.fluid.FluidColorWell
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlin.math.roundToInt

/** I tag delle prove e delle riprese della tavolozza. */
object FluidInkPaletteTags {
  const val Palette = "fluid-ink:tavolozza"
  const val Minimized = "fluid-ink:tavolozza:ridotta"
  const val Ruler = "fluid-ink:tavolozza:righello"
  const val Color = "fluid-ink:tavolozza:colore"
  const val More = "fluid-ink:tavolozza:altro"
  const val Options = "fluid-ink:tavolozza:opzioni"

  fun tool(tool: FluidInkTool) = "fluid-ink:tavolozza:${tool.id}"
  fun width(index: Int) = "fluid-ink:tavolozza:spessore:$index"
}

/**
 * Le misure della tavolozza, prese dalla schermata ufficiale di Markup di iOS 26 sull'iPhone (la guida dell'iPhone, 1,24 px
 * per punto, `sessioni/f8-16/riferimenti/`, fiducia media): la barra di vetro a capsula alta 68 pt e larga quasi quanto
 * lo schermo (20 pt per lato), gli strumenti a passo di 37 pt col corpo largo 16, quello scelto 10 pt piu' alto, il
 * pozzetto del colore e il tondo grigio a destra. Qui il passo e' 36 dp: sette strumenti, il colore e «•••» stanno in
 * 360 dp (un telefono stretto col margine di 12 per lato ne ha 336: la fila si stringe col peso). A occhio (25/11/2026) sulle riprese della tavolozza di Markup in iOS 26 sull'iPhone
 * (fiducia media: le proporzioni si vedono, Apple non le pubblica).
 */
object FluidInkPaletteDefaults {
  val Height: Dp = 68.dp
  val ToolWidth: Dp = 36.dp
  /** Su un iPhone la barra e' larga quanto lo schermo meno 20 pt per lato; sugli schermi larghi si ferma qui. */
  val MaxWidth: Dp = 560.dp
  val ToolRaise: Dp = 10.dp
  val MinimizedSize: Dp = 52.dp
  val SideMargin: Dp = 12.dp
  val BottomMargin: Dp = 12.dp
}

/**
 * La tavolozza di vetro di Markup (`PKToolPicker` di iOS 26): gli strumenti in fila come penne vere, nell'ordine di Apple
 * (penna, monolinea, evidenziatore, gomma, lazo, righello, matita), la punta col colore scelto, quello scelto piu' alto;
 * il pozzetto del colore; «•••» nel tondo grigio. In cima la maniglia (iOS 27); su uno schermo largo anche i colori veloci
 * in fila.
 *
 * - Un tocco su uno strumento lo sceglie; un tocco su quello gia' scelto apre le sue opzioni, che **nascono
 *   dallo strumento**: i cinque spessori, l'opacita' e i colori veloci (per la gomma: a oggetti o a pixel).
 * - Il pozzetto apre il selettore di colore dell'engine (F8.7).
 * - «•••»: disegnare solo con la penna, ridurre da sola la tavolozza quando si disegna, le forme perfette, spostarla in
 *   alto o in basso, cancellare tutto.
 * - **Si sposta e si riduce.** Tenuta premuta (dalla maniglia o da un punto qualsiasi) e trascinata va in alto o in basso; ridotta e' un tondo col solo strumento
 *   scelto, che si riapre toccandolo. Il vetro passa da una forma all'altra col piano flottante, senza animazioni scritte.
 *
 * Mentre si disegna resta ferma e nitida (il vetro non rilegge il foglio). Il tratto a bassa latenza non le passa sopra:
 * la tavolozza dice al foglio dove sta. Per TalkBack ogni strumento e' un tasto di scelta col suo nome, il righello un
 * interruttore.
 */
@Composable
fun FluidInkPalette(
  state: FluidInkState,
  modifier: Modifier = Modifier,
  labels: FluidInkLabels = FluidInkLabels(),
  tools: List<FluidInkTool> = FluidInkTool.all,
  showsRuler: Boolean = true,
) {
  val ns = rememberFluidGlassNamespace()
  val haptics = rememberFluidHaptics()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  var options by remember { mutableStateOf<FluidInkTool?>(null) }
  var more by remember { mutableStateOf(false) }
  var dragY by remember { mutableFloatStateOf(0f) }
  val maskKey = remember { Any() }
  DisposableEffect(state, maskKey) { onDispose { state.inkMasks.remove(maskKey) } }
  val screen = LocalConfiguration.current.screenWidthDp
  val wide = screen >= 600
  // Il passo degli strumenti: 36 dp, o meno su un telefono stretto, perche' la fila stia tutta (al minimo 28).
  val slots = tools.size + if (showsRuler) 1 else 0
  val room = minOf(screen.toFloat() - 2 * FluidInkPaletteDefaults.SideMargin.value, FluidInkPaletteDefaults.MaxWidth.value)
  val pitch = ((room - 36f - 44f - 8f - 34f - 6f - (if (wide) 260f else 0f)) / slots.coerceAtLeast(1))
    .coerceIn(28f, FluidInkPaletteDefaults.ToolWidth.value).dp
  val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
  val slop = LocalViewConfiguration.current.touchSlop

  FluidGlassContainer(
    modifier
      .offset { IntOffset(0, dragY.roundToInt()) }
      .onGloballyPositioned { state.inkMasks[maskKey] = it.boundsInRoot() }
      // Tenuta premuta e trascinata, la tavolozza si sposta; lasciata, va in alto o in basso (il passaggio Initial: i tasti
      // sotto il dito si annullano quando il trascinamento comincia).
      .pointerInput(state) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          // Null se il dito e' rimasto fermo per il click lungo; falso se si e' alzato o mosso prima.
          val early: Boolean? = withTimeoutOrNull(longPress) {
            while (true) {
              val e = awaitPointerEvent(PointerEventPass.Initial)
              val c = e.changes.firstOrNull { it.id == down.id } ?: break
              if (!c.pressed || (c.position - down.position).getDistance() > slop) break
            }
            false
          }
          if (early != null) return@awaitEachGesture
          haptics.play(FluidHapticEvent.GestureStart)
          var total = 0f
          while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            e.changes.forEach { it.consume() }
            if (!c.pressed) break
            total += c.position.y - c.previousPosition.y
            dragY = total
          }
          val travel = with(density) { 160.dp.toPx() }
          if (!state.paletteAtTop && total < -travel) state.paletteAtTop = true
          else if (state.paletteAtTop && total > travel) state.paletteAtTop = false
          dragY = 0f
          haptics.play(FluidHapticEvent.GestureEnd)
        }
      },
  ) {
    if (state.paletteMinimized) {
      val tool = state.tool
      Box(
        Modifier
          .fluidGlassId("fluid-ink-palette", ns)
          .size(FluidInkPaletteDefaults.MinimizedSize)
          .fluidGlass(FluidGlass.Regular, FluidCapsuleShape)
          .clip(FluidCapsuleShape)
          .testTag(FluidInkPaletteTags.Minimized)
          .fluidPressable(onClick = { state.paletteMinimized = false }, role = Role.Button, haptic = FluidHapticEvent.Open)
          .semantics { contentDescription = labels.expand + ", " + labels.of(tool) },
        contentAlignment = Alignment.BottomCenter,
      ) {
        ToolGlyph(tool, state.styleOf(if (tool.isInking) tool else FluidInkTool.Pen).color, Modifier.size(24.dp, 44.dp))
      }
    } else {
      val ink = MaterialTheme.colorScheme.onSurface
      Box(
        Modifier
          .fluidGlassId("fluid-ink-palette", ns)
          .widthIn(max = FluidInkPaletteDefaults.MaxWidth)
          .height(FluidInkPaletteDefaults.Height)
          .fluidGlass(FluidGlass.Regular, FluidCapsuleShape)
          .clip(FluidCapsuleShape)
          .testTag(FluidInkPaletteTags.Palette),
      ) {
      // La maniglia (iOS 27): la tavolozza si tiene e si sposta.
      Box(
        Modifier
          .align(Alignment.TopCenter)
          .padding(top = 5.dp)
          .size(36.dp, 4.dp)
          .clip(FluidCapsuleShape)
          .background(ink.copy(alpha = 0.18f)),
      )
      Row(
        Modifier.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        for (tool in tools) {
          // Il righello sta fra il lazo e la matita, come in Markup.
          if (tool == FluidInkTool.Pencil && showsRuler) RulerToggle(state, labels, pitch)
          val selected = state.tool == tool
          val raise by animateDpAsState(
            if (selected) 0.dp else FluidInkPaletteDefaults.ToolRaise,
            if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 600f),
            label = "strumento",
          )
          Box(
            Modifier
              .width(pitch)
              .height(FluidInkPaletteDefaults.Height)
              .fluidTransitionSource(tool.id, ns)
              .testTag(FluidInkPaletteTags.tool(tool))
              .fluidPressable(
                onClick = {
                  if (selected && tool != FluidInkTool.Lasso) options = tool else state.selectTool(tool)
                },
                role = Role.RadioButton,
                haptic = if (selected) FluidHapticEvent.Open else FluidHapticEvent.Tap,
              )
              .semantics {
                contentDescription = labels.of(tool)
                this.selected = selected
                if (tool.isInking || tool == FluidInkTool.Eraser) stateDescription = labels.width(state.styleOf(tool).widthIndex)
              },
            contentAlignment = Alignment.BottomCenter,
          ) {
            ToolGlyph(
              tool, state.styleOf(if (tool.isInking) tool else FluidInkTool.Pen).color,
              Modifier.offset { IntOffset(0, raise.roundToPx()) }.size(18.dp, 58.dp),
            )
          }
        }
        if (showsRuler && FluidInkTool.Pencil !in tools) RulerToggle(state, labels, pitch)
        Spacer(Modifier.weight(1f).widthIn(min = 6.dp))
        val colorTool = if (state.tool.isInking) state.tool else FluidInkTool.Pen
        val style = state.styleOf(colorTool)
        // Sugli schermi larghi i colori veloci stanno nella fila, come nella tavolozza di iOS 27 sull'iPad.
        if (wide) {
          Box(Modifier.height(FluidInkPaletteDefaults.Height), contentAlignment = Alignment.Center) {
            FluidColorSwatches(
              colors = FluidInkDefaults.QuickColors,
              selected = FluidInkDefaults.QuickColors.firstOrNull { it == style.color.copy(alpha = 1f) },
              onSelect = { state.setStyle(colorTool, style.copy(color = it.copy(alpha = style.color.alpha))) },
              customLabel = null,
            )
          }
        }
        Box(Modifier.height(FluidInkPaletteDefaults.Height), contentAlignment = Alignment.Center) {
          FluidColorWell(
            color = style.color,
            onColorChange = { state.setStyle(colorTool, style.copy(color = it.copy(alpha = 1f))) },
            label = labels.color,
            supportsOpacity = false,
            modifier = Modifier.testTag(FluidInkPaletteTags.Color),
          )
        }
        Spacer(Modifier.width(8.dp))
        // «•••» nel tondo grigio del «+» di Markup.
        Box(Modifier.height(FluidInkPaletteDefaults.Height), contentAlignment = Alignment.Center) {
          Box(
            Modifier
              .size(34.dp)
              .fluidTransitionSource("altro", ns)
              .clip(FluidCapsuleShape)
              .background(ink.copy(alpha = 0.08f))
              .testTag(FluidInkPaletteTags.More)
              .fluidPressable(onClick = { more = true }, role = Role.Button, haptic = FluidHapticEvent.Open)
              .semantics { contentDescription = labels.more },
            contentAlignment = Alignment.Center,
          ) { FluidIcon(FluidIcons.MoreHoriz, null, size = 20.dp) }
        }
      }
      }
    }
  }

  val open = options
  FluidGlassModalPortal(
    visible = open != null,
    onDismissRequest = { options = null },
    paneTitle = open?.let { labels.of(it) },
    source = open?.let { FluidTransitionSource(it.id, ns) },
    compactAdaptation = FluidCompactAdaptation.Popover,
  ) {
    if (open != null) ToolOptions(state, open, labels)
  }
  FluidGlassModalPortal(
    visible = more,
    onDismissRequest = { more = false },
    paneTitle = labels.more,
    source = FluidTransitionSource("altro", ns),
    compactAdaptation = FluidCompactAdaptation.Popover,
  ) {
    MoreOptions(state, labels, onDone = { more = false })
  }
}

/** L'interruttore del righello nella fila degli strumenti. */
@Composable
private fun RulerToggle(state: FluidInkState, labels: FluidInkLabels, pitch: Dp) {
  val on = state.rulerVisible
  Box(
    Modifier
      .width(pitch)
      .height(FluidInkPaletteDefaults.Height)
      .testTag(FluidInkPaletteTags.Ruler)
      .fluidPressable(onClick = { state.rulerVisible = !on }, role = Role.Switch, haptic = if (on) FluidHapticEvent.ToggleOff else FluidHapticEvent.ToggleOn)
      .semantics {
        contentDescription = labels.ruler
        toggleableState = ToggleableState(on)
      },
    contentAlignment = Alignment.BottomCenter,
  ) {
    val reduced = LocalFluidMotionPolicy.current.reducedMotion
    val raise by animateDpAsState(
      if (on) 0.dp else FluidInkPaletteDefaults.ToolRaise,
      if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 600f),
      label = "righello",
    )
    RulerGlyph(on, Modifier.offset { IntOffset(0, raise.roundToPx()) }.size(18.dp, 58.dp))
  }
}

/** Le opzioni di uno strumento: i cinque spessori, l'opacita' e i colori veloci; per la gomma, il tipo. */
@Composable
private fun ToolOptions(state: FluidInkState, tool: FluidInkTool, labels: FluidInkLabels) {
  val style = state.styleOf(tool)
  Column(
    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).width(264.dp).testTag(FluidInkPaletteTags.Options),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (tool == FluidInkTool.Eraser) {
      FluidSegmentedControl(
        options = listOf(FluidInkEraserMode.Area, FluidInkEraserMode.Object),
        selected = state.eraserMode,
        onSelect = { state.eraserMode = it },
        label = { if (it == FluidInkEraserMode.Area) labels.areaEraser else labels.objectEraser },
      )
    }
    if (tool != FluidInkTool.Eraser || state.eraserMode == FluidInkEraserMode.Area) {
      val widths = FluidInkDefaults.widths(tool)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        widths.forEachIndexed { index, width ->
          val chosen = style.widthIndex == index
          val accent = MaterialTheme.colorScheme.primary
          val ink = MaterialTheme.colorScheme.onSurface
          Box(
            Modifier
              .size(44.dp)
              .testTag(FluidInkPaletteTags.width(index))
              .fluidPressable(onClick = { state.setStyle(tool, style.copy(widthIndex = index)) }, role = Role.RadioButton)
              .semantics {
                contentDescription = labels.width(index)
                selected = chosen
              },
            contentAlignment = Alignment.Center,
          ) {
            Canvas(Modifier.size(36.dp)) {
              val shown = (width.toPx() * if (tool == FluidInkTool.Highlighter || tool == FluidInkTool.Eraser) 0.5f else 1.4f)
                .coerceIn(3.dp.toPx(), 26.dp.toPx())
              drawCircle(if (tool == FluidInkTool.Eraser) ink.copy(alpha = 0.5f) else style.color.copy(alpha = 1f), shown / 2f)
              if (chosen) drawCircle(accent, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
          }
        }
      }
    }
    if (tool.isInking) {
      Text(labels.opacity, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      FluidSlider(
        value = style.color.alpha,
        onValueChange = { state.setStyle(tool, style.copy(color = style.color.copy(alpha = it.coerceIn(0.1f, 1f)))) },
        valueRange = 0.1f..1f,
        contentDescription = labels.opacity,
        stateDescription = "${(style.color.alpha * 100).roundToInt()} %",
      )
      FluidColorSwatches(
        colors = FluidInkDefaults.QuickColors,
        selected = FluidInkDefaults.QuickColors.firstOrNull { it == style.color.copy(alpha = 1f) },
        onSelect = { state.setStyle(tool, style.copy(color = it.copy(alpha = style.color.alpha))) },
      )
    }
  }
}

/** «•••»: chi disegna, la tavolozza che si riduce, le forme, dove sta, cancella tutto. */
@Composable
private fun MoreOptions(state: FluidInkState, labels: FluidInkLabels, onDone: () -> Unit) {
  Column(Modifier.padding(vertical = 8.dp).width(300.dp)) {
    FluidToggleRow(
      title = labels.stylusOnly,
      checked = state.drawingPolicy == FluidInkDrawingPolicy.StylusOnly || (state.drawingPolicy == FluidInkDrawingPolicy.Auto && state.stylusSeen),
      onCheckedChange = { state.drawingPolicy = if (it) FluidInkDrawingPolicy.StylusOnly else FluidInkDrawingPolicy.AnyInput },
    )
    FluidToggleRow(title = labels.autoMinimize, checked = state.autoMinimize, onCheckedChange = { state.autoMinimize = it })
    FluidToggleRow(title = labels.shapes, checked = state.shapeRecognition, onCheckedChange = { state.shapeRecognition = it })
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FluidGlassButton(
        if (state.paletteAtTop) labels.moveToBottom else labels.moveToTop,
        onClick = { state.paletteAtTop = !state.paletteAtTop; onDone() },
      )
      FluidGlassButton(labels.clearAll, onClick = { state.clear(); onDone() }, enabled = !state.isEmpty)
    }
  }
}

/**
 * Uno strumento disegnato come in Markup: il corpo chiaro che sale dal fondo della tavolozza, e la punta (la penna a
 * cono con l'anello del colore, l'evidenziatore a scalpello trasparente, la matita di legno con la grafite colorata, la
 * gomma rosa, il lazo col cerchio tratteggiato).
 */
@Composable
internal fun ToolGlyph(tool: FluidInkTool, color: Color, modifier: Modifier = Modifier) {
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val body = if (dark) Color(0xFFE6E6EB) else Color(0xFFFFFFFF)
  val edge = if (dark) Color(0x33000000) else Color(0x22000000)
  val ink = MaterialTheme.colorScheme.onSurface
  Canvas(modifier) { drawTool(tool, color, body, edge, ink) }
}

private fun DrawScope.drawTool(tool: FluidInkTool, color: Color, body: Color, edge: Color, ink: Color) {
  val w = size.width
  val h = size.height
  val bodyW = w * 0.72f
  val left = (w - bodyW) / 2f
  val tipTop = h * 0.04f
  val shoulder = h * 0.32f
  val shade = Brush.horizontalGradient(listOf(body.copy(alpha = 1f), body, Color(0xFFD9D9DE).copy(alpha = body.alpha)), startX = left, endX = left + bodyW)
  // Il corpo.
  drawRoundRect(shade, Offset(left, shoulder), Size(bodyW, h - shoulder + 6f), CornerRadius(3.dp.toPx()))
  drawRoundRect(edge, Offset(left, shoulder), Size(bodyW, h - shoulder + 6f), CornerRadius(3.dp.toPx()), style = Stroke(0.7.dp.toPx()))
  val opaque = color.copy(alpha = 1f)
  when (tool) {
    FluidInkTool.Pen -> {
      val cone = Path().apply {
        moveTo(left, shoulder); lineTo(left + bodyW, shoulder); lineTo(w / 2f + bodyW * 0.12f, tipTop + h * 0.08f)
        lineTo(w / 2f, tipTop); lineTo(w / 2f - bodyW * 0.12f, tipTop + h * 0.08f); close()
      }
      // Come la penna di Markup: il cono chiaro, la punta e l'anello del colore scelto.
      drawPath(cone, Color(0xFFE5E5EA))
      val tip = Path().apply {
        moveTo(w / 2f - bodyW * 0.12f, tipTop + h * 0.08f); lineTo(w / 2f, tipTop); lineTo(w / 2f + bodyW * 0.12f, tipTop + h * 0.08f); close()
      }
      drawPath(tip, opaque)
      drawRect(opaque, Offset(left, shoulder + h * 0.05f), Size(bodyW, h * 0.07f))
    }
    FluidInkTool.Monoline -> {
      val cone = Path().apply {
        moveTo(left, shoulder); lineTo(left + bodyW, shoulder); lineTo(w / 2f + bodyW * 0.2f, tipTop + h * 0.07f)
        lineTo(w / 2f - bodyW * 0.2f, tipTop + h * 0.07f); close()
      }
      drawPath(cone, Color(0xFF3A3A3C))
      drawRoundRect(opaque, Offset(w / 2f - bodyW * 0.2f, tipTop), Size(bodyW * 0.4f, h * 0.09f), CornerRadius(bodyW * 0.2f))
      drawRect(opaque, Offset(left, shoulder + h * 0.05f), Size(bodyW, h * 0.04f))
    }
    FluidInkTool.Highlighter -> {
      val chisel = Path().apply {
        moveTo(left + bodyW * 0.12f, shoulder); lineTo(left + bodyW * 0.88f, shoulder)
        lineTo(left + bodyW * 0.88f, tipTop + h * 0.10f); lineTo(left + bodyW * 0.12f, tipTop + h * 0.18f); close()
      }
      drawPath(chisel, opaque.copy(alpha = 0.85f))
      drawRect(opaque.copy(alpha = 0.55f), Offset(left, shoulder + h * 0.04f), Size(bodyW, h * 0.10f))
    }
    FluidInkTool.Pencil -> {
      val wood = Path().apply {
        moveTo(left, shoulder); lineTo(left + bodyW, shoulder); lineTo(w / 2f, tipTop); close()
      }
      drawPath(wood, Color(0xFFE8C9A0))
      val lead = Path().apply {
        moveTo(w / 2f - bodyW * 0.16f, tipTop + h * 0.10f); lineTo(w / 2f + bodyW * 0.16f, tipTop + h * 0.10f); lineTo(w / 2f, tipTop); close()
      }
      drawPath(lead, opaque)
      drawRect(opaque, Offset(left, shoulder + h * 0.05f), Size(bodyW, h * 0.05f))
    }
    FluidInkTool.Eraser -> {
      drawRoundRect(Color(0xFFF2A7B5), Offset(left, tipTop + h * 0.08f), Size(bodyW, shoulder - tipTop - h * 0.06f), CornerRadius(4.dp.toPx()))
      drawRect(Color(0xFFBDBDC2), Offset(left, shoulder), Size(bodyW, h * 0.05f))
    }
    FluidInkTool.Lasso -> {
      val cone = Path().apply {
        moveTo(left, shoulder); lineTo(left + bodyW, shoulder); lineTo(w / 2f + 1.5f, tipTop + h * 0.16f); lineTo(w / 2f - 1.5f, tipTop + h * 0.16f); close()
      }
      drawPath(cone, Color(0xFFBDBDC2))
      drawCircle(ink.copy(alpha = 0.8f), bodyW * 0.42f, Offset(w / 2f, tipTop + h * 0.10f),
        style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f))))
    }
  }
}

/**
 * Il righello della tavolozza, come in Markup: un righello dritto col corpo chiaro e le tacche, che sale quando e' acceso
 * (come uno strumento scelto) e prende l'accento sul bordo.
 */
@Composable
private fun RulerGlyph(on: Boolean, modifier: Modifier = Modifier) {
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val body = if (dark) Color(0xFFE6E6EB) else Color(0xFFFFFFFF)
  val accent = MaterialTheme.colorScheme.primary
  Canvas(modifier) {
    val w = size.width * 0.62f
    val left = (size.width - w) / 2f
    val top = size.height * 0.04f
    drawRoundRect(body, Offset(left, top), Size(w, size.height - top + 6f), CornerRadius(2.dp.toPx()))
    drawRoundRect(if (on) accent else Color(0x33000000), Offset(left, top), Size(w, size.height - top + 6f), CornerRadius(2.dp.toPx()),
      style = Stroke(if (on) 1.5.dp.toPx() else 0.7.dp.toPx()))
    val step = size.height / 14f
    for (i in 1 until 14) {
      val y = top + i * step
      val tick = if (i % 2 == 0) w * 0.55f else w * 0.3f
      drawLine(Color(0xFF8E8E93), Offset(left, y), Offset(left + tick, y), 0.8.dp.toPx())
    }
  }
}
