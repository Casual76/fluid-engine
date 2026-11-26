package dev.antigravity.fluidengine.editor

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.editor.icons.AutoFixHigh
import dev.antigravity.fluidengine.editor.icons.Brightness1
import dev.antigravity.fluidengine.editor.icons.Brightness6
import dev.antigravity.fluidengine.editor.icons.ChangeHistory
import dev.antigravity.fluidengine.editor.icons.Contrast
import dev.antigravity.fluidengine.editor.icons.DarkMode
import dev.antigravity.fluidengine.editor.icons.Details
import dev.antigravity.fluidengine.editor.icons.Exposure
import dev.antigravity.fluidengine.editor.icons.Flare
import dev.antigravity.fluidengine.editor.icons.Gradient
import dev.antigravity.fluidengine.editor.icons.InvertColors
import dev.antigravity.fluidengine.editor.icons.LightMode
import dev.antigravity.fluidengine.editor.icons.Thermostat
import dev.antigravity.fluidengine.editor.icons.Vignette
import dev.antigravity.fluidengine.editor.icons.WaterDrop
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Le misure della ruota delle regolazioni, dalla schermata di «Regola» di Foto in iOS 27 (la guida dell'iPhone,
 * `sessioni/f8-2/riferimenti/apple-photos-adjust-ruler-iphone-ios27.png`, 1,132 px per punto): tasti tondi da 50 pt con
 * un passo di 69 pt, la regolazione scelta al centro; l'anello sottile, e il valore scritto dentro con l'arco che si
 * riempie. Fiducia alta per le misure, media per lo spessore dell'anello (2 px sulla schermata).
 */
internal object EditorStripLook {
  val Item = 50.dp
  val Gap = 19.dp
  val Ring = 1.5.dp
  val ValueArc = 2.5.dp
  val Icon = 24.dp
  /** L'anello a riposo, sull'inchiostro della pagina. */
  const val RingInk = 0.28f
  /** «Auto» acceso: il tondo pieno, come in Foto. */
  const val AutoFill = 0.14f
}

/** L'icona di una regolazione: le sagome della fila di Foto, nel set di Material Symbols allo stesso peso. */
internal fun adjustmentIcon(a: FluidAdjustment): ImageVector = when (a) {
  FluidAdjustment.Exposure -> FluidIcons.Exposure
  FluidAdjustment.Brilliance -> FluidIcons.Flare
  FluidAdjustment.Highlights -> FluidIcons.LightMode
  FluidAdjustment.Shadows -> FluidIcons.DarkMode
  FluidAdjustment.Contrast -> FluidIcons.Contrast
  FluidAdjustment.Brightness -> FluidIcons.Brightness6
  FluidAdjustment.BlackPoint -> FluidIcons.Brightness1
  FluidAdjustment.Saturation -> FluidIcons.Gradient
  FluidAdjustment.Vibrance -> FluidIcons.InvertColors
  FluidAdjustment.Warmth -> FluidIcons.Thermostat
  FluidAdjustment.Tint -> FluidIcons.WaterDrop
  FluidAdjustment.Sharpness -> FluidIcons.ChangeHistory
  FluidAdjustment.Definition -> FluidIcons.Details
  FluidAdjustment.Vignette -> FluidIcons.Vignette
  else -> FluidIcons.Exposure
}

/** I tag delle prove e delle riprese. */
object FluidImageEditorTags {
  const val Photo = "fluid-editor:foto"
  const val Strip = "fluid-editor:ruota"
  const val Ruler = "fluid-editor:righello"
  const val Straighten = "fluid-editor:raddrizza"
  const val Cancel = "fluid-editor:annulla"
  const val Done = "fluid-editor:fine"
  const val Undo = "fluid-editor:indietro"
  const val Redo = "fluid-editor:avanti"
  const val ModeAdjust = "fluid-editor:regola"
  const val ModeCrop = "fluid-editor:ritaglia"
  const val Rotate = "fluid-editor:ruota-sinistra"
  const val Flip = "fluid-editor:specchia"
  const val Aspect = "fluid-editor:proporzioni"
  const val Reset = "fluid-editor:reimposta"
  const val More = "fluid-editor:altro"
  const val Label = "fluid-editor:etichetta"
  const val Split = "fluid-editor:tendina"

  fun item(id: String) = "fluid-editor:voce:$id"
  fun aspect(id: String) = "fluid-editor:rapporto:$id"
}

/**
 * La **ruota delle regolazioni**: la fila di tasti tondi di «Regola» che scorre di lato e si ferma con una regolazione al
 * centro (quella sotto il righello). Il dito la muove (nitida, anche nell'inerzia); un tocco su un tasto lo porta al
 * centro; un tic per ogni regolazione che passa al centro. La prima voce e' «Auto».
 */
@Composable
internal fun EditorAdjustStrip(state: FluidImageEditorState, strings: FluidImageEditorStrings, modifier: Modifier = Modifier) {
  val items = remember { listOf<FluidAdjustment?>(null) + FluidAdjustment.All }
  val start = items.indexOf(state.selected).coerceAtLeast(0)
  val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
  val scope = rememberCoroutineScope()
  val haptics = LocalFluidHaptics.current
  // La voce al centro diventa la regolazione scelta. Si legge lo scorrimento, non si ricompone la fila.
  LaunchedEffect(list) {
    var first = true
    snapshotFlow {
      val info = list.layoutInfo
      val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
      info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
    }.distinctUntilChanged().collect { index ->
      if (index == null) return@collect
      if (!first && list.isScrollInProgress) haptics.play(FluidHapticEvent.Tick)
      first = false
      if (list.isScrollInProgress) state.selected = items[index]
    }
  }
  // Una regolazione scelta da fuori (un tocco, la ricreazione) va al centro.
  LaunchedEffect(list) {
    snapshotFlow { state.selected }.collect { a ->
      val i = items.indexOf(a)
      if (i >= 0 && !list.isScrollInProgress) list.animateScrollToItem(i)
    }
  }
  BoxWithConstraints(modifier.fillMaxWidth().testTag(FluidImageEditorTags.Strip)) {
    val side = (maxWidth - EditorStripLook.Item) / 2
    LazyRow(
      state = list,
      contentPadding = PaddingValues(horizontal = side),
      horizontalArrangement = Arrangement.spacedBy(EditorStripLook.Gap),
      flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
      modifier = Modifier.fillMaxWidth(),
    ) {
      itemsIndexed(items, key = { _, a -> a?.id ?: FluidImageEditorState.AutoId }) { index, a ->
        StripItem(state, a, strings) {
          state.selected = a
          scope.launch { list.animateScrollToItem(index) }
          if (a == null) state.toggleAuto()
        }
      }
    }
  }
}

@Composable
private fun StripItem(state: FluidImageEditorState, a: FluidAdjustment?, strings: FluidImageEditorStrings, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val ink = scheme.onBackground
  val accent = scheme.primary
  val selected = state.selected == a
  val auto = a == null
  val autoOn = auto && state.edit.isAuto
  val name = if (a == null) strings.auto else strings.adjustmentName(a)
  Box(
    Modifier
      .size(EditorStripLook.Item)
      .testTag(FluidImageEditorTags.item(a?.id ?: FluidImageEditorState.AutoId))
      .fluidPressable(onClick = onClick, role = Role.Button)
      .semantics {
        contentDescription = name
        this.selected = selected
        role = Role.Button
        stateDescription = if (a == null) {
          if (autoOn) strings.autoOn else strings.autoOff
        } else {
          state.edit.adjustments[a].roundToInt().toString()
        }
      }
      .drawBehind {
        val stroke = EditorStripLook.Ring.toPx()
        val r = size.minDimension / 2f - stroke
        if (autoOn) drawCircle(ink, r + stroke / 2, alpha = EditorStripLook.AutoFill)
        drawCircle(ink, r, style = Stroke(stroke), alpha = EditorStripLook.RingInk)
        if (a != null) {
          // L'arco del valore: dall'alto, in senso orario per i positivi e antiorario per i negativi.
          val v = state.edit.adjustments[a] / 100f
          if (v != 0f) {
            val arc = EditorStripLook.ValueArc.toPx()
            drawArc(
              if (selected) accent else ink, startAngle = -90f, sweepAngle = 360f * v, useCenter = false,
              topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
              style = Stroke(arc, cap = StrokeCap.Round), alpha = if (selected) 1f else 0.7f,
            )
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    if (a != null && selected) {
      SelectedValue(state, a, accent, ink)
    } else {
      FluidIcon(
        if (auto) (if (autoOn) FluidIcons.Filled.AutoFixHigh else FluidIcons.AutoFixHigh) else adjustmentIcon(a!!),
        contentDescription = null,
        tint = if (selected) accent else ink.copy(alpha = 0.85f),
        size = EditorStripLook.Icon,
      )
    }
  }
}

/** Il valore dentro il tasto scelto: si ricompone solo quando cambia il numero, non a ogni fotogramma. */
@Composable
private fun SelectedValue(state: FluidImageEditorState, a: FluidAdjustment, accent: androidx.compose.ui.graphics.Color,
  ink: androidx.compose.ui.graphics.Color) {
  val value = state.edit.adjustments[a].roundToInt()
  if (value == 0) {
    FluidIcon(adjustmentIcon(a), contentDescription = null, tint = accent, size = EditorStripLook.Icon)
  } else {
    Text(
      text = if (value < 0) "−${-value}" else value.toString(),
      color = accent,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
    )
  }
}

/** Il righello sotto la ruota: la regolazione scelta, da −100 a 100 (0…100 per nitidezza e definizione). */
@Composable
internal fun EditorAdjustRuler(state: FluidImageEditorState, strings: FluidImageEditorStrings, modifier: Modifier = Modifier) {
  val a = state.selected
  Box(modifier.fillMaxWidth().height(EditorChromeLook.RulerHeight), contentAlignment = Alignment.BottomCenter) {
    if (a == null) return@Box
    val value = state.edit.adjustments[a]
    FluidRulerDial(
      value = value,
      onValueChange = { v ->
        if (!state.interacting) state.beginGesture()
        state.setAdjustment(a, v)
      },
      valueRange = a.range,
      step = 1f,
      majorEvery = 10,
      neutralValue = 0f,
      onValueChangeFinished = { state.endGesture() },
      valueText = null,
      contentDescription = strings.adjustmentName(a),
      modifier = Modifier.testTag(FluidImageEditorTags.Ruler).semantics {
        stateDescription = value.roundToInt().toString()
      },
    )
  }
}
