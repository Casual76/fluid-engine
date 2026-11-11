package dev.antigravity.fluidengine.aiui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.DragHandle
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowDown
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowUp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * L'ordine dei provider, con le frecce su e giu' **e** il trascinamento. ClasseViva e PampAI avevano le
 * frecce, FluidWeather le frecce piu' il trascinamento (tenendo premuto, come la griglia della sua
 * home): le frecce restano perche' sono l'unica strada per chi non puo' trascinare (tastiera, TalkBack),
 * il trascinamento perche' e' quella che la mano si aspetta. [draggable] falso toglie la maniglia.
 *
 * Solo i provider con una chiave verificata compaiono; gli altri restano in coda all'ordine salvato, e
 * ci tornano quando avranno una chiave ([FluidProviderOrder.move]).
 *
 * **Una vibrazione per momento**: `GestureStart` quando la riga si prende, un `Tick` a ogni posto che
 * passa, `GestureEnd` quando si lascia.
 */
@Composable
fun FluidProviderOrderList(
  order: List<ProviderId>,
  available: Set<ProviderId>,
  onReorder: (List<ProviderId>) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
  glass: Boolean = false,
  draggable: Boolean = true,
) {
  val visible = FluidProviderOrder.visible(order, available)
  val haptics = rememberFluidHaptics()
  val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }
  var dragging by remember { mutableIntStateOf(-1) }
  var dragOffset by remember { mutableFloatStateOf(0f) }

  fun move(from: Int, to: Int) {
    val next = FluidProviderOrder.move(order, available, from, to)
    if (next != order) onReorder(next)
  }

  FluidListGroup(modifier = modifier, glass = glass) {
    visible.forEachIndexed { index, provider ->
      if (index > 0) FluidListDivider()
      val isDragging = dragging == index
      FluidListRow(
        title = provider.label,
        subtitle = if (index == 0) strings.orderFirst else strings.orderBackup,
        modifier = if (draggable && visible.size > 1) {
          Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
            .pointerInput(visible) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  dragging = index
                  dragOffset = 0f
                  haptics.play(FluidHapticEvent.GestureStart)
                },
                onDrag = { change, delta ->
                  change.consume()
                  dragOffset += delta.y
                  val target = FluidProviderOrder.dragTarget(index, dragOffset, rowHeightPx, visible.size)
                  if (target != index) {
                    move(index, target)
                    haptics.play(FluidHapticEvent.Tick)
                    dragging = -1
                    dragOffset = 0f
                  }
                },
                onDragEnd = {
                  if (dragging == index) haptics.play(FluidHapticEvent.GestureEnd)
                  dragging = -1
                  dragOffset = 0f
                },
                onDragCancel = {
                  dragging = -1
                  dragOffset = 0f
                },
              )
            }
        } else {
          Modifier
        },
        leading = if (draggable && visible.size > 1) {
          { FluidIcon(FluidIcons.DragHandle, contentDescription = strings.orderDrag, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
          null
        },
        badge = {
          Row {
            ArrowButton(FluidIcons.KeyboardArrowUp, strings.orderMoveUp, enabled = index > 0) { move(index, index - 1) }
            ArrowButton(FluidIcons.KeyboardArrowDown, strings.orderMoveDown, enabled = index < visible.lastIndex) { move(index, index + 1) }
          }
        },
      )
    }
  }
}

/** L'altezza di una riga, per sapere quando il dito ne ha attraversata una intera. */
private val RowHeight = 64.dp

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(36.dp)
      .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.8f else 0.25f),
      modifier = Modifier.size(20.dp),
    )
  }
}
