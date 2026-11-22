package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun FontSpecimen(file: File, font: FontFile, initial: List<Typeface>, padding: PaddingValues) {
  var index by remember(file) { mutableIntStateOf(0) }
  val face = font.faces[index]
  val axisValues = remember(face) { face.axes.map { mutableFloatStateOf(it.normal) } }
  val axes = face.axes.zip(axisValues).joinToString(",") { (axis, value) -> "'${axis.tag}' ${value.floatValue}" }
  val typeface by produceState(initial[index], file, index, axes) {
    value = if (axes.isEmpty()) initial[index] else withContext(Dispatchers.Default) {
      Typeface.Builder(file).setTtcIndex(index).setFontVariationSettings(axes).build() ?: initial[index]
    }
  }
  val custom = remember(typeface) { FontFamily(typeface) }
  val direction = LocalLayoutDirection.current
  val spacing = FluidScreenDefaults.HorizontalPadding
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
      start = padding.calculateLeftPadding(direction) + spacing,
      top = padding.calculateTopPadding(),
      end = padding.calculateRightPadding(direction) + spacing,
      bottom = padding.calculateBottomPadding() + spacing,
    ),
    verticalArrangement = Arrangement.spacedBy(spacing),
  ) {
    Text(face.family, style = MaterialTheme.typography.displaySmall, fontFamily = custom)
    Text(face.style, style = MaterialTheme.typography.bodyLarge)
    if (font.faces.size > 1) FluidButton(
      onClick = { index = (index + 1) % font.faces.size },
      text = "Carattere ${index + 1} di ${font.faces.size}",
    )
    for ((axis, value) in face.axes.zip(axisValues)) {
      Column {
        Text("${axis.name} · ${value.floatValue.axisValue()}", style = MaterialTheme.typography.titleMedium)
        if (axis.min < axis.max) FluidSlider(value.floatValue, { value.floatValue = it }, Modifier.fillMaxWidth(),
          valueRange = axis.min..axis.max, snapValues = listOf(axis.normal), contentDescription = axis.name,
          stateDescription = value.floatValue.axisValue())
      }
    }
    Text("ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789", style = MaterialTheme.typography.headlineSmall,
      fontFamily = custom)
    for (size in listOf(18, 28, 42)) {
      Text("$size sp", style = MaterialTheme.typography.labelMedium)
      Text("Quel vitreo balzo fra squarci d'ombra", style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = custom, fontSize = size.sp, lineHeight = (size * 1.2).sp,
      ))
    }
  }
}
