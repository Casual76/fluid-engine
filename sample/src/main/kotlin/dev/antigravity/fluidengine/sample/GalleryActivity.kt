package dev.antigravity.fluidengine.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidTabBarPadding
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.foundation.EngineSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope

/**
 * The gallery.
 *
 * Every surface here is deliberately standing over something loud. Glass over a flat grey page has
 * nothing to refract and looks identical whether or not the lens works at all — which is precisely
 * how a design system ends up shipping a material nobody can see.
 */
class GalleryActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Gallery()
      }
    }
  }
}

private val Tabs = listOf(
  FluidTabItem(route = "material", label = "Materiale", icon = Icons.Rounded.Home),
  FluidTabItem(route = "controls", label = "Controlli", icon = Icons.Rounded.GridView),
  FluidTabItem(route = "chrome", label = "Chrome", icon = Icons.Rounded.AutoAwesome),
  FluidTabItem(route = "settings", label = "Impostazioni", icon = Icons.Rounded.Settings),
)

@Composable
private fun Gallery() {
  var route by remember { mutableStateOf(Tabs.first().route) }
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Box(modifier = Modifier.fillMaxSize()) {
    FluidScreen(
      title = when (route) {
        "controls" -> "Controlli"
        "chrome" -> "Chrome"
        "settings" -> "Impostazioni"
        else -> "Fluid Glass"
      },
      subtitle = "Ogni superficie qui sopra sta su qualcosa di rumoroso, apposta.",
      extraBottomPadding = FluidTabBarDefaults.ContentInset + bottomInset,
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
      overlay = { backdrop ->
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomInset)
            .padding(fluidTabBarPadding()),
        ) {
          FluidTabBar(
            items = Tabs,
            selectedRoute = route,
            onSelect = { route = it.route },
            backdrop = backdrop,
          )
        }
        GalleryFloatingControls(
          backdrop = backdrop,
          bottomInset = bottomInset,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      },
    ) {
      item(key = "intro") { FluidSectionHeader(title = "Sotto il vetro") }
      items(12) { index ->
        LoudCard(index)
      }
      // I crediti stanno qui e non in una pagina a parte apposta: sono la stessa sezione che ogni
      // app deve mettere nella propria pagina "informazioni", e vale la pena guardarla girare.
      fluidLicensesSection()
    }
  }
}

/**
 * Controls parked over the page rather than inside it.
 *
 * A glass button that lives in a list has an opaque row behind it and cannot show what it does; the
 * only honest place to look at one is floating over moving content, which is also the only place a
 * real app should use one.
 */
@Composable
private fun GalleryFloatingControls(
  backdrop: GlassBackdropState,
  bottomInset: androidx.compose.ui.unit.Dp,
  modifier: Modifier = Modifier,
) {
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Row(
      modifier = modifier
        .padding(bottom = bottomInset + FluidTabBarDefaults.ContentInset + 18.dp)
        .padding(horizontal = 18.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidGlassButton(text = "Azione", onClick = {}, backdrop = backdrop)
      FluidGlassButton(text = "Attiva", onClick = {}, backdrop = backdrop, selected = true)
      FluidGlassIconButton(onClick = {}, backdrop = backdrop) {
        Icon(
          imageVector = Icons.Rounded.AutoAwesome,
          contentDescription = "Effetto",
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

/**
 * Something with hard edges and saturated colour, so the lens has an image with structure in it.
 *
 * A gradient alone hides a broken refraction: bending a smooth ramp produces another smooth ramp.
 * The stripes are here so displacement is visible as displacement.
 */
@Composable
private fun LoudCard(index: Int) {
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
