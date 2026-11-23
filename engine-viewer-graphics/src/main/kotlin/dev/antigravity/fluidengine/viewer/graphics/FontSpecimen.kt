package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object SpecimenLayout {
  // Colonna di D · Mix e misure del provino.
  val ColumnWidth = 552.dp
  val Hero = 96.sp
  val Glyph = 24.sp
  val Sizes = listOf(18, 28, 42)
}

@Composable
internal fun FontSpecimen(
  file: File,
  faces: List<FontFace>,
  initial: List<Typeface>,
  format: String,
  padding: PaddingValues,
) {
  var index by remember(file) { mutableIntStateOf(0) }
  val spacing = FluidScreenDefaults.HorizontalPadding
  val backdrop = rememberGlassBackdrop()
  Box(Modifier.fillMaxSize()) {
    Spacer(Modifier.matchParentSize().glassBackdropSource(backdrop).background(MaterialTheme.colorScheme.surface))
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(padding).padding(horizontal = spacing, vertical = spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        key(index) {
          val face = faces[index]
          val values = remember(face) { face.axes.map { mutableFloatStateOf(it.normal) } }
          val settings = face.axes.zip(values).joinToString(",") { (axis, value) -> "'${axis.tag}' ${value.floatValue}" }
          val typeface by produceState(initial[index], file, index, settings) {
            value = if (face.axes.indices.all { face.axes[it].normal == values[it].floatValue }) initial[index]
            else withContext(Dispatchers.Default) {
              Typeface.Builder(file).setTtcIndex(index).setFontVariationSettings(settings).build() ?: initial[index]
            }
          }
          val custom = remember(typeface) { FontFamily(typeface) }
          Column(
            Modifier.widthIn(max = SpecimenLayout.ColumnWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
          ) {
            SpecimenHero(face, custom, index, faces.size) { index = (index + 1) % faces.size }
            SpecimenFacts(face, format)
            if (face.axes.isNotEmpty()) SpecimenSection("Assi variabili") {
              Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = spacing, vertical = spacing / 2)) {
                  face.axes.forEachIndexed { i, axis ->
                    if (i != 0) HorizontalDivider()
                    AxisControl(axis, values[i].floatValue) { values[i].floatValue = it }
                  }
                }
              }
            }
            if (face.preview.sentence.isNotEmpty()) SpecimenSentences(face.preview.sentence, custom)
            if (face.preview.groups.isNotEmpty()) SpecimenSection("Glifi") {
              Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                face.preview.groups.forEach { SpecimenGlyphs(it, custom) }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SpecimenHero(face: FontFace, custom: FontFamily, index: Int, count: Int, next: () -> Unit) {
  val spacing = FluidScreenDefaults.HorizontalPadding
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      Modifier.padding(horizontal = spacing, vertical = spacing * 1.5f),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(FluidScreenDefaults.ItemSpacing / 2),
    ) {
      if (face.preview.hero.isNotEmpty()) {
        Text(
          face.preview.hero,
          Modifier.clearAndSetSemantics {},
          style = MaterialTheme.typography.displayLarge.specimen(custom).copy(
            fontSize = SpecimenLayout.Hero, lineHeight = SpecimenLayout.Hero * 1.08f, textAlign = TextAlign.Center,
          ),
        )
      }
      Text(
        face.family,
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Text(face.style, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      if (count > 1) FluidButton("Carattere ${index + 1} di $count", next, style = FluidButtonStyle.Tinted)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecimenFacts(face: FontFace, format: String) {
  val gap = FluidScreenDefaults.ItemSpacing
  FlowRow(
    Modifier.fillMaxWidth().semantics { isTraversalGroup = true },
    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(gap / 2),
  ) {
    face.facts(format).forEach { fact ->
      Text(
        "${fact.label} · ${fact.value}",
        Modifier.fluidGlass().padding(horizontal = gap, vertical = gap / 2),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

@Composable
private fun AxisControl(axis: FontAxis, value: Float, onChange: (Float) -> Unit) {
  val gap = FluidScreenDefaults.ItemSpacing
  val formatted = value.axisValue()
  Column(Modifier.fillMaxWidth().padding(top = gap)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
      verticalAlignment = Alignment.CenterVertically) {
      Text(axis.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
      Text(formatted, Modifier.clearAndSetSemantics {}, style = FluidTextStyles.numeric)
    }
    if (axis.min < axis.max) {
      FluidSlider(value, onChange, Modifier.fillMaxWidth(),
        valueRange = axis.min..axis.max, snapValues = listOf(axis.normal),
        contentDescription = axis.name, stateDescription = formatted)
    } else {
      Text("Valore fisso · $formatted", Modifier.padding(bottom = gap),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun SpecimenSection(title: String, body: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(FluidScreenDefaults.ItemSpacing / 2)) {
    FluidSectionHeader(title)
    body()
  }
}

@Composable
private fun SpecimenSentences(sentence: String, custom: FontFamily) {
  val gap = FluidScreenDefaults.ItemSpacing
  SpecimenSection("Frase di prova") {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
      Column(Modifier.fillMaxWidth().padding(horizontal = FluidScreenDefaults.HorizontalPadding)) {
        SpecimenLayout.Sizes.forEachIndexed { i, size ->
          if (i != 0) HorizontalDivider()
          Column(Modifier.padding(vertical = gap), verticalArrangement = Arrangement.spacedBy(gap / 2)) {
            Text("$size sp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sentence, style = MaterialTheme.typography.bodyLarge.specimen(custom).copy(
              fontSize = size.sp, lineHeight = (size * 1.2f).sp,
            ))
          }
        }
      }
    }
  }
}

@Composable
private fun SpecimenGlyphs(group: FontGlyphGroup, custom: FontFamily) {
  val gap = FluidScreenDefaults.ItemSpacing / 2
  val em = with(LocalDensity.current) { SpecimenLayout.Glyph.toDp() }
  Column(verticalArrangement = Arrangement.spacedBy(gap)) {
    Text(group.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val minimum = maxOf(38.dp, em * 1.5f)
      val columns = ((maxWidth + gap) / (minimum + gap)).toInt().coerceIn(1, 10)
      val cell = (maxWidth - gap * (columns - 1)) / columns
      Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        group.characters.chunked(columns).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (glyph in row) {
              Box(
                Modifier.width(cell).heightIn(min = maxOf(40.dp, em * 1.5f))
                  .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
                  .clearAndSetSemantics { contentDescription = glyph.toString() },
                contentAlignment = Alignment.Center,
              ) {
                Text(glyph.toString(), style = MaterialTheme.typography.titleLarge.specimen(custom).copy(
                  fontSize = SpecimenLayout.Glyph, lineHeight = SpecimenLayout.Glyph * 1.18f,
                ), maxLines = 1, softWrap = false)
              }
            }
          }
        }
      }
    }
  }
}

private fun TextStyle.specimen(family: FontFamily): TextStyle = copy(
  fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, letterSpacing = 0.sp,
)
