package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroBand
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidVividCard
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors
import dev.antigravity.fluidengine.ui.fluid.FluidVividEffect

/**
 * Tab one: the material itself, over content with hard edges in it.
 *
 * No ambient canvas here on purpose — this tab is about the *chrome* roles, and the loud cards are a
 * harsher test than any canvas would be.
 */
@Composable
internal fun MaterialTab(bottomInset: Dp) {
  FluidScreen(
    title = "Fluid Glass",
    subtitle = "Ogni superficie qui sopra sta su qualcosa di rumoroso, apposta.",
    extraBottomPadding = bottomInset,
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Notifications,
        contentDescription = "Notifiche",
        onClick = {},
      )
      FluidBarAction(
        icon = Icons.Rounded.Settings,
        contentDescription = "Impostazioni",
        onClick = {},
      )
    },
  ) {
    item(key = "band-header") { FluidSectionHeader(title = "La fascia satura") }
    item(key = "band") {
      FluidHeroBand(
        tone = FluidHeroTone.Tertiary,
        motif = FluidHeroMotif.Bars,
        eyebrow = "Fascia di sezione",
        value = "7",
        label = "toni sull'anello",
        icon = Icons.Rounded.Notifications,
      )
    }
    item(key = "vivid-header") { FluidSectionHeader(title = "La card vivida") }
    item(key = "vivid-sheen") {
      FluidVividCard(
        colors = FluidVividColors.from(Color(0xFF00A86B), Color(0xFF00C7BE)),
        effect = FluidVividEffect.Sheen,
      ) {
        Text(
          text = "8,5",
          style = FluidTextStyles.largeNumeric,
        )
        Text(
          text = "Con lo sheen: la banda di luce passa solo quando la card e' a schermo.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    item(key = "vivid-plain") {
      FluidVividCard(colors = FluidVividColors.from(Color(0xFFFF9500), Color(0xFFFF6B00))) {
        Text(
          text = "5,5",
          style = FluidTextStyles.largeNumeric,
        )
        Text(
          text = "Senza effetto, e con il contenuto scelto dal contrasto: qui e' scuro.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    item(key = "intro") { FluidSectionHeader(title = "Sotto il vetro") }
    items(12) { index -> LoudCard(index) }
  }
}

/**
 * Something with hard edges and saturated colour, so the lens has an image with structure in it.
 *
 * A gradient alone hides a broken refraction: bending a smooth ramp produces another smooth ramp.
 * The stripes are here so displacement is visible as displacement.
 */
@Composable
internal fun LoudCard(index: Int) {
  val palette = listOf(
    Color(0xFFFF3B30) to Color(0xFFFF9500),
    Color(0xFF34C759) to Color(0xFF00C7BE),
    Color(0xFF007AFF) to Color(0xFF5856D6),
    Color(0xFFFF2D55) to Color(0xFFAF52DE),
  )
  val (start, end) = palette[index % palette.size]

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1.9f)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .background(Brush.linearGradient(listOf(start, end)))
      .background(
        Brush.linearGradient(
          colors = List(14) { i -> if (i % 2 == 0) Color.White.copy(alpha = 0.28f) else Color.Transparent },
          start = Offset.Zero,
          end = Offset(220f, 220f),
        ),
      ),
    contentAlignment = Alignment.CenterStart,
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Text(
        text = "Riquadro ${index + 1}",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Text(
        text = "Scorri sotto la barra e guarda il bordo.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.9f),
      )
    }
  }
}
