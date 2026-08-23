package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A text field.
 *
 * Material's outlined field carries a floating label that animates into the border, an indicator
 * line, and a focus colour change — three moving parts announcing a box you can type in. iOS gives
 * it a quiet filled rect and lets the caret do the talking. The one piece of interaction it does add
 * is the clear button, which appears only while there is something to clear.
 */
@Composable
fun FluidTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  label: String? = null,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  singleLine: Boolean = true,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  isError: Boolean = false,
  supportingText: String? = null,
  minHeight: androidx.compose.ui.unit.Dp = 44.dp,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  showClearButton: Boolean = singleLine,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val interactionSource = remember { MutableInteractionSource() }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (label != null) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
      )
    }

    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth(),
      enabled = enabled,
      readOnly = readOnly,
      singleLine = singleLine,
      minLines = minLines,
      maxLines = maxLines,
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
      cursorBrush = SolidColor(scheme.primary),
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      visualTransformation = visualTransformation,
      interactionSource = interactionSource,
      decorationBox = { innerTextField ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(FluidRadius.Control))
            .background(
              if (isError) scheme.error.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.06f),
            )
            .heightIn(min = minHeight)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
              Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() }
            }
          }
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder != null) {
              Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
              )
            }
            innerTextField()
          }
          if (showClearButton) {
            AnimatedVisibility(
              visible = value.isNotEmpty() && enabled,
              enter = fadeIn(FluidMotion.fadeIn(140)) + scaleIn(FluidMotion.snappy(), initialScale = 0.6f),
              exit = fadeOut(FluidMotion.fadeOut(120)) + scaleOut(FluidMotion.fadeOut(120), targetScale = 0.6f),
            ) {
              FluidClearButton(onClick = { onValueChange("") })
            }
          }
          if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
              Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { trailing() }
            }
          }
        }
      },
    )

    supportingText?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) scheme.error else scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
      )
    }
  }
}

/** The filled circle with a cross through it. Drawn rather than shipped as an icon asset. */
@Composable
private fun FluidClearButton(onClick: () -> Unit) {
  val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
  val glyph = MaterialTheme.colorScheme.surface
  Box(
    modifier = Modifier
      .size(28.dp)
      .fluidPressable(onClick = onClick, pressedScale = 0.86f),
    contentAlignment = Alignment.Center,
  ) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(17.dp)) {
      val radius = size.minDimension / 2f
      drawCircle(color = color, radius = radius)
      val arm = radius * 0.44f
      val stroke = radius * 0.2f
      listOf(
        androidx.compose.ui.geometry.Offset(-arm, -arm) to androidx.compose.ui.geometry.Offset(arm, arm),
        androidx.compose.ui.geometry.Offset(-arm, arm) to androidx.compose.ui.geometry.Offset(arm, -arm),
      ).forEach { (from, to) ->
        drawLine(
          color = glyph,
          start = center + from,
          end = center + to,
          strokeWidth = stroke,
          cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
      }
    }
  }
}
