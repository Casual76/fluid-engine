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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassMenuButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidBarFold
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.launch

/**
 * The gallery.
 *
 * Four tabs, and each one exists because there is a *different* thing that can only be judged by
 * looking at it:
 *
 *  | Materiale    | the four `GlassRole`s over deliberately loud content                       |
 *  | Controlli    | switch, segmented, chips, buttons and fields, on an ambient canvas         |
 *  | Chrome       | the in-root modal, the context menu, notifications, sheet and alert        |
 *  | Impostazioni | ambient canvas plus content glass — what a real settings page looks like   |
 *
 * Every surface here is deliberately standing over something loud. Glass over a flat grey page has
 * nothing to refract and looks identical whether or not the lens works at all — which is precisely
 * how a design system ends up shipping a material nobody can see. This is where each new component
 * gets looked at *before* it goes into an app.
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

private const val RouteMaterial = "material"
private const val RouteControls = "controls"
private const val RouteChrome = "chrome"
private const val RouteSettings = "settings"

private val Tabs = listOf(
  FluidTabItem(route = RouteMaterial, label = "Materiale", icon = Icons.Rounded.Home),
  FluidTabItem(route = RouteControls, label = "Controlli", icon = Icons.Rounded.GridView),
  FluidTabItem(route = RouteChrome, label = "Chrome", icon = Icons.Rounded.AutoAwesome),
  FluidTabItem(route = RouteSettings, label = "Impostazioni", icon = Icons.Rounded.Settings),
)

/**
 * The shell, in the shape a real app has one.
 *
 * The tab bar, the notification host and the modal host are all siblings of the content and all read
 * `chromeController.activeBackdrop` — which is exactly how ClasseViva and Pampa Store are wired. A
 * gallery that special-cased its own chrome would be testing an arrangement no app uses.
 */
@Composable
private fun Gallery() {
  var route by remember { mutableStateOf(RouteMaterial) }
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val chromeController = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val notificationHost = rememberFluidNotificationHostState()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop

  val barFold = rememberFluidBarFold()

  CompositionLocalProvider(
    LocalFluidGlassModalHostState provides modalHost,
    LocalFluidNotificationHostState provides notificationHost,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      ProvideFluidChrome(
        controller = chromeController,
        bottomInset = FluidFoldingTabBarDefaults.ContentInset,
        scrollToTop = scrollToTop,
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(barFold.connection)
            .fluidGlassModalObscured(),
        ) {
          when (route) {
            RouteControls -> ControlsTab(bottomInset)
            RouteChrome -> ChromeTab(bottomInset)
            RouteSettings -> SettingsTab(bottomInset)
            else -> MaterialTab(bottomInset)
          }
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(
            horizontal = FluidFoldingTabBarDefaults.HorizontalMargin,
            vertical = FluidFoldingTabBarDefaults.BottomMargin,
          ),
      ) {
        FluidFoldingTabBar(
          items = Tabs,
          selectedRoute = route,
          onSelect = { route = it.route },
          onReselect = { scrollToTop.request() },
          onExpandRequest = barFold::unfold,
          backdrop = backdrop,
          fold = { barFold.progress.value },
          trailing = {
            FluidGlassIconButton(
              onClick = {},
              backdrop = backdrop,
              modifier = Modifier.fillMaxSize(),
            ) {
              Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Cerca",
                modifier = Modifier.size(22.dp),
              )
            }
          },
        )
      }

      if (route == RouteMaterial) {
        GalleryFloatingControls(
          backdrop = backdrop,
          bottomInset = bottomInset,
          fold = { barFold.progress.value },
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }

      // Above the tab bar, and that placement is the whole reason the modal is in-root: a pop-up
      // that a floating navigation capsule can sit on top of is not a modal.
      FluidGlassModalHost(state = modalHost, backdrop = backdrop)
      FluidNotificationHost(
        state = notificationHost,
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

/**
 * Tab one: the material itself, over content with hard edges in it.
 *
 * No ambient canvas here on purpose — this tab is about the *chrome* roles, and the loud cards are a
 * harsher test than any canvas would be.
 */
@Composable
private fun MaterialTab(bottomInset: androidx.compose.ui.unit.Dp) {
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
    item(key = "intro") { FluidSectionHeader(title = "Sotto il vetro") }
    items(12) { index -> LoudCard(index) }
  }
}

/** Tab two: every control the engine has, standing on a real canvas instead of on grey. */
@Composable
private fun ControlsTab(bottomInset: androidx.compose.ui.unit.Dp) {
  var switchOne by remember { mutableStateOf(true) }
  var switchTwo by remember { mutableStateOf(false) }
  var segment by remember { mutableStateOf("Mese") }
  var chip by remember { mutableStateOf("Tutti") }
  var query by remember { mutableStateOf("") }

  FluidScreen(
    title = "Controlli",
    subtitle = "Interruttore, segmenti, pillole, pulsanti e campi, sul canvas ambientale.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Dots)
    },
  ) {
    item(key = "switches") {
      FluidListGroup(glass = true) {
        SwitchRow("Interruttore acceso", switchOne) { switchOne = it }
        FluidListDivider()
        SwitchRow("Interruttore spento", switchTwo) { switchTwo = it }
        FluidListDivider()
        SwitchRow("Disabilitato", true, enabled = false) {}
      }
    }

    item(key = "segments-header") { FluidSectionHeader(title = "Segmenti") }
    item(key = "segments") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf("Giorno", "Settimana", "Mese"),
          selected = segment,
          onSelect = { segment = it },
          label = { it },
        )
        Text(
          text = "Trascina la pastiglia fra i segmenti: la lente rifrange la copia " +
            "accentata delle etichette, non un riempimento.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "chips-header") { FluidSectionHeader(title = "Pillole e pulsanti") }
    item(key = "chips") {
      FluidCard(glass = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("Tutti", "Letti", "Da leggere").forEach { option ->
            FluidChip(label = option, selected = chip == option, onClick = { chip = option })
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FluidButton(text = "Principale", onClick = {}, size = FluidButtonSize.Medium)
          FluidButton(
            text = "Tinta",
            onClick = {},
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Medium,
          )
          FluidButton(
            text = "Elimina",
            onClick = {},
            style = FluidButtonStyle.Destructive,
            size = FluidButtonSize.Medium,
          )
        }
      }
    }

    item(key = "field-header") { FluidSectionHeader(title = "Campo di testo") }
    item(key = "field") {
      FluidCard(glass = true) {
        FluidTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = "Cerca",
        )
      }
    }

    item(key = "tiles-header") { FluidSectionHeader(title = "Piastrelle") }
    item(key = "tiles") {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidMetricTile(
          label = "Media",
          value = "7,4",
          detail = "in salita",
          tone = FluidTone.Success,
          glass = true,
          modifier = Modifier.weight(1f),
        )
        FluidMetricTile(
          label = "Assenze",
          value = "3",
          detail = "da giustificare",
          tone = FluidTone.Warning,
          glass = true,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun SwitchRow(
  label: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
    )
    FluidSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
  }
}

/** Tab three: everything that appears *over* a page, and the one thing that used to be impossible. */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChromeTab(bottomInset: androidx.compose.ui.unit.Dp) {
  var cardOpen by remember { mutableStateOf(false) }
  var sheetOpen by remember { mutableStateOf(false) }
  var legacySheet by remember { mutableStateOf(false) }
  var alertOpen by remember { mutableStateOf(false) }
  var cardOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
  var log by remember { mutableStateOf("Niente ancora.") }
  var notificationCount by remember { mutableIntStateOf(0) }

  val notificationHost = LocalFluidNotificationHostState.current
  val scope = rememberCoroutineScope()

  // Declared here, next to the state it reads, and drawn at the root above the tab bar. This is the
  // pattern every feature uses: nothing is emitted at this call site.
  FluidGlassModalPortal(
    visible = cardOpen,
    onDismissRequest = { cardOpen = false },
    origin = { cardOrigin },
    paneTitle = "Dettaglio",
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Un pop-up in vetro",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Si apre esattamente sulla riga che hai toccato, con la misura del proprio " +
          "contenuto, e cresce dall'angolo più vicino con le stesse due molle della pastiglia " +
          "della tab bar. La pagina dietro resta a colori: la separa il bordo del vetro, non un " +
          "velo nero.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FluidButton(text = "Chiudi", onClick = { cardOpen = false }, fillWidth = true)
    }
  }

  FluidGlassModalPortal(
    visible = sheetOpen,
    onDismissRequest = { sheetOpen = false },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Foglio",
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 34.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Ancorato al bordo",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      repeat(4) { index ->
        Text(
          text = "Riga ${index + 1} di contenuto, sopra la pagina che si vede ancora.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FluidButton(text = "Chiudi", onClick = { sheetOpen = false }, fillWidth = true)
    }
  }

  FluidScreen(
    title = "Chrome",
    subtitle = "Quello che appare sopra una pagina, e la pagina resta visibile.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.TertiaryToPrimary, motif = FluidHeroMotif.Ripples)
    },
  ) {
    item(key = "modal-header") { FluidSectionHeader(title = "Modale in-root") }
    item(key = "modal") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Card che si espande",
          subtitle = "Cresce dai limiti di questa riga",
          modifier = Modifier.fluidExpandOrigin { cardOrigin = it },
          onClick = { cardOpen = true },
        )
        FluidListDivider()
        FluidListRow(
          title = "Foglio ancorato in basso",
          subtitle = "Stessa meccanica, altra geometria",
          onClick = { sheetOpen = true },
        )
      }
    }

    item(key = "context-header") { FluidSectionHeader(title = "Menù contestuale") }
    item(key = "context") {
      FluidListGroup(glass = true) {
        listOf("Circolare di settembre", "Uscita anticipata", "Consiglio di classe")
          .forEachIndexed { index, title ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = title,
              subtitle = "Tieni premuto per le azioni",
              tone = if (index == 1) FluidTone.Warning else FluidTone.Neutral,
              onClick = { log = "Aperta: $title" },
              contextActions = {
                listOf(
                  FluidContextAction("Segna come letta", Icons.Rounded.Star) {
                    log = "Letta: $title"
                  },
                  FluidContextAction("Condividi", Icons.Rounded.Share) {
                    log = "Condivisa: $title"
                  },
                  FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) {
                    log = "Eliminata: $title"
                  },
                )
              },
            )
          }
      }
    }

    item(key = "log") {
      FluidCard(glass = true) {
        Text(
          text = log,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "legacy-header") { FluidSectionHeader(title = "Finestre di piattaforma") }
    item(key = "legacy") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Notifica in-app",
          subtitle = "Overlay in-root, vetro vero",
          onClick = {
            val id = ++notificationCount
            scope.launch {
              notificationHost?.show(
                FluidNotification(
                  id = "gallery-$id",
                  title = "Notifica $id",
                  message = "Anche questa rifrange la pagina invece di coprirla.",
                  tone = FluidNotificationTone.Success,
                ),
              )
            }
          },
        )
        FluidListDivider()
        FluidListRow(
          title = "FluidSheet (deprecato)",
          subtitle = "Finestra separata: gradienti dipinti, non vetro",
          onClick = { legacySheet = true },
        )
        FluidListDivider()
        FluidListRow(
          title = "FluidAlert",
          subtitle = "Dialog di sistema, stesso confine",
          onClick = { alertOpen = true },
        )
      }
    }
  }

  if (legacySheet) {
    FluidSheet(onDismissRequest = { legacySheet = false }, title = "Foglio di piattaforma") {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Questo vive in una finestra sua, quindi non può leggere il GraphicsLayer " +
            "dell'app. Guarda il fondo: è dipinto, non rifratto. È il confine di sistema per " +
            "cui esiste FluidGlassModalPortal.",
          style = MaterialTheme.typography.bodyMedium,
        )
        FluidButton(text = "Chiudi", onClick = { legacySheet = false }, fillWidth = true)
      }
    }
  }

  if (alertOpen) {
    FluidAlert(
      onDismissRequest = { alertOpen = false },
      title = "Eliminare?",
      message = "Anche l'alert sta in una finestra separata.",
      actions = listOf(
        FluidAlertAction("Annulla", onClick = { alertOpen = false }),
        FluidAlertAction(
          "Elimina",
          onClick = { alertOpen = false },
          emphasis = FluidAlertAction.Emphasis.Destructive,
        ),
      ),
    )
  }
}

/** Tab four: what a settings page looks like once the content is made of the same material. */
@Composable
private fun SettingsTab(bottomInset: androidx.compose.ui.unit.Dp) {
  var amoled by remember { mutableStateOf(false) }
  var notifications by remember { mutableStateOf(true) }
  var theme by remember { mutableStateOf("Sistema") }

  FluidScreen(
    title = "Impostazioni",
    subtitle = "Canvas ambientale, contenuto in vetro, e i crediti.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards)
    },
  ) {
    item(key = "appearance-header") { FluidSectionHeader(title = "Aspetto") }
    item(key = "appearance") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf("Chiaro", "Scuro", "Sistema"),
          selected = theme,
          onSelect = { theme = it },
          label = { it },
        )
      }
    }
    item(key = "toggles") {
      FluidListGroup(glass = true) {
        SwitchRow("Nero assoluto", amoled) { amoled = it }
        FluidListDivider()
        SwitchRow("Notifiche", notifications) { notifications = it }
      }
    }
    item(key = "about-header") { FluidSectionHeader(title = "Informazioni") }
    item(key = "about") {
      FluidListGroup(glass = true) {
        FluidListRow(title = "Versione", subtitle = "Galleria del Fluid Engine")
        FluidListDivider()
        FluidListRow(title = "Sorgente", subtitle = "fluid-engine", onClick = {})
      }
    }
    // I crediti stanno qui apposta: sono la stessa sezione che ogni app deve mettere nella propria
    // pagina "informazioni", e vale la pena guardarla girare sopra il canvas.
    fluidLicensesSection()
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
  fold: () -> Float,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val openPx = with(density) {
    (FluidFoldingTabBarDefaults.OpenHeight + FluidFoldingTabBarDefaults.BottomMargin + 18.dp).toPx()
  }
  val foldedPx = with(density) {
    (FluidFoldingTabBarDefaults.FoldedHeight + FluidFoldingTabBarDefaults.BottomMargin + 18.dp)
      .toPx()
  }
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Row(
      modifier = modifier
        // Riding the fold rather than parked at a fixed height. Left alone, these sat exactly where
        // the open bar used to be — so folding the bar left them hanging in the middle of nothing,
        // which is worse than either state on its own.
        .graphicsLayer {
          translationY = -androidx.compose.ui.util.lerp(openPx, foldedPx, fold().coerceIn(0f, 1f))
        }
        .padding(bottom = bottomInset)
        .padding(horizontal = 18.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidGlassButton(text = "Attiva", onClick = {}, backdrop = backdrop, selected = true)
      // Tap for the action, hold and it turns into its own menu. The one control here that is not
      // simply made of glass but *changes shape* out of it.
      FluidGlassMenuButton(
        text = "Azione",
        onClick = {},
        backdrop = backdrop,
        actions = {
          listOf(
            FluidContextAction("Condividi", Icons.Rounded.Share) {},
            FluidContextAction("Aggiungi ai preferiti", Icons.Rounded.Star) {},
            FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) {},
          )
        },
      )
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
