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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationAction
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import kotlinx.coroutines.launch
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
  // Vetro su vetro (2.13.0): i pannelli che si aprono sopra questa pagina rumorosa, ognuno con un
  // tasto e un tasto vuoto dentro. Ricevono la registrazione della pagina, `backdrop = page`, come
  // la passano le app: e' il caso in cui prima il tasto veniva piu' nitido del pannello.
  var popoverOpen by remember { mutableStateOf(false) }
  var expandOpen by remember { mutableStateOf(false) }
  var sheetOpen by remember { mutableStateOf(false) }
  var expandOrigin by remember { mutableStateOf<Rect?>(null) }
  var notificationCount by remember { mutableIntStateOf(0) }
  val notificationHost = LocalFluidNotificationHostState.current
  val scope = rememberCoroutineScope()
  val page = LocalFluidChromeController.current?.activeBackdrop?.value ?: currentGlassBackdrop()

  FluidGlassModalPortal(
    visible = popoverOpen,
    onDismissRequest = { popoverOpen = false },
    paneTitle = "Pop-up con tasti",
  ) {
    GlassOnGlassPaneContent("Pop-up centrato", page)
  }
  FluidGlassModalPortal(
    visible = expandOpen,
    onDismissRequest = { expandOpen = false },
    origin = { expandOrigin },
    paneTitle = "Card con tasti",
  ) {
    GlassOnGlassPaneContent("Card che si espande", page)
  }
  FluidGlassModalPortal(
    visible = sheetOpen,
    onDismissRequest = { sheetOpen = false },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Foglio con tasti",
  ) {
    GlassOnGlassPaneContent("Foglio", page, tall = true)
  }

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
    item(key = "nested-header") { FluidSectionHeader(title = "Vetro su vetro") }
    // I comandi stanno sopra il palco, e corti: cosi' ogni pannello si apre sopra le righe del
    // palco (la card cresce dal suo tasto verso il basso, il pop-up e il foglio stanno al centro)
    // e niente di quello che serve misurare finisce sotto i tasti flottanti o la barra.
    item(key = "nested-triggers") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = "Card",
          onClick = { expandOpen = true },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          modifier = Modifier.weight(1f).fluidExpandOrigin { expandOrigin = it },
        )
        FluidButton(
          text = "Pop-up",
          onClick = { popoverOpen = true },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          modifier = Modifier.weight(1f),
        )
        FluidButton(
          text = "Foglio",
          onClick = { sheetOpen = true },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          modifier = Modifier.weight(1f),
        )
        FluidButton(
          text = "Notifica",
          onClick = {
            val id = ++notificationCount
            scope.launch {
              notificationHost?.show(
                FluidNotification(
                  id = "nested-$id",
                  title = "Vetro su vetro",
                  message = "Il tasto di chiusura e l'azione stanno sul vetro della card, e la " +
                    "devono sfocare. Tirala giu' per aprirla e vedere l'azione.",
                  tone = FluidNotificationTone.Info,
                  durationMillis = 60_000L,
                  actions = listOf(FluidNotificationAction("Apri") {}),
                ),
              )
            }
          },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          modifier = Modifier.weight(1f),
        )
      }
    }
    item(key = "nested-stage") { GlassOnGlassStage() }
    item(key = "nested-foreign") { ForeignPaneStage() }
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

/**
 * Il contenuto dei pannelli di prova: un titolo e, subito sotto, un tasto e un tasto vuoto sul vetro
 * del pannello — in alto, perche' e' li' che ciascun pannello sta sopra il palco a righe. [tall]
 * allunga il foglio fin sopra il palco.
 */
@Composable
private fun GlassOnGlassPaneContent(
  title: String,
  page: GlassBackdropState,
  tall: Boolean = false,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidGlassButton(text = "Conferma", onClick = {}, backdrop = page)
      BlankGlassPill(page)
    }
    Text(
      text = "I due tasti qui sopra ricevono la pagina, come li scrivono le app: devono sfocare " +
        "il vetro su cui stanno, non bucarlo.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (tall) Spacer(Modifier.height(300.dp))
  }
}
