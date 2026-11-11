package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidContainerScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarInlineBehavior
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidSyncAction
import dev.antigravity.fluidengine.ui.tutorial.FluidCoachMark
import dev.antigravity.fluidengine.ui.tutorial.FluidCoachMarkPlacement
import dev.antigravity.fluidengine.ui.tutorial.FluidGestureHint
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorial
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorialLabels

/**
 * Tutto in vetro vero (3.0, fase 9): quello che era opaco o dipinto accanto al vetro, ora vetro; e
 * quello che si afferra e scorre, ora la goccia di Kyant — piena a riposo, lente sotto il dito.
 *
 * Ogni sezione sta su un riquadro di colori e di testo nitido, registrato come fondale (una pagina
 * piatta rifrange una pagina piatta, e il vetro non si vedrebbe). Dove c'e' un confronto, la riga
 * "senza fondale" sta su una pagina piatta: li' il componente e' quello opaco di sempre — e **deve
 * restare corretto**, e' la meta' della prova.
 *
 *  - **Chip**: un tasto di vetro, scelto con l'accento nel materiale; premuto cresce e segue il dito.
 *  - **Segmentato**: pista di vetro (lastra), pillola a goccia. Bianca a riposo (grigia sullo scuro),
 *    lente quando la prendi, un tick per segmento mentre la trascini.
 *  - **Rail**: la goccia viaggia fra le schede come la pillola della barra quando si tocca una scheda.
 *  - **Indice di sezione**: tieni premuto la barretta: la lente e' una goccia, la bolla e' un pannello.
 *  - **Barra flottante**: la banda sopra le schede e' il vetro della barra; il cerchio e la scheda
 *    ripiegata rispondono con la luce del dito e un `Tap`, senza l'increspatura di Material.
 *  - **Suggerimento**: niente punta dipinta; "Ok" e' un tasto di vetro, l'ingresso scivola e sfuma.
 *  - **Sincronizzazione nella barra**: l'indicatore e' una capsula di vetro, col tono dentro il materiale.
 *  - **Fogli e pagine**: il vecchio `FluidSheet` apre il foglio di vetro dell'host; la pagina intera ha
 *    la chiusura di vetro; il contenitore ha la lastra di contenuto sul fondale ambientale.
 *
 * Una colonna sola, cosi' va ovunque: la monta chi prepara la galleria. Le sezioni che aprono fogli
 * usano l'host dei modali della radice (`LocalFluidGlassModalHostState`); senza, lo dicono.
 */
@Composable
fun AllGlassDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("all-glass-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = "Provali con le dita, sul chiaro e sullo scuro: bianco o grigio a riposo, lente sotto il dito.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp),
    )
    ChipsSection()
    SegmentedSection()
    RailSection()
    IndexSection()
    TabBarSection()
    CoachSection()
    SyncSection()
    SheetsSection()
  }
}

// --- Chip ----------------------------------------------------------------------------------------

@Composable
private fun ChipsSection() {
  FluidSectionHeader(title = "Chip", detail = "Un tasto di vetro. Il secondo giro sta su una pagina piatta.")
  var picked by remember { mutableStateOf(setOf("Tutti")) }
  val toggle: (String) -> Unit = { label ->
    picked = if (label in picked) picked - label else picked + label
  }
  Stage(height = 130.dp) { backdrop ->
    Row(
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      CompositionLocalProvider(LocalFluidCanvasBackdrop provides backdrop) {
        ChipRow(picked, toggle)
      }
    }
  }
  Row(
    modifier = Modifier.padding(horizontal = 4.dp).testTag("all-glass-chips-flat"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ChipRow(picked, toggle)
  }
}

@Composable
private fun ChipRow(picked: Set<String>, toggle: (String) -> Unit) {
  listOf("Tutti", "Preferiti", "Recenti").forEachIndexed { index, label ->
    FluidChip(
      label = label,
      selected = label in picked,
      onClick = { toggle(label) },
      leading = if (index == 1) {
        { FluidIcon(FluidIcons.Filled.Favorite, contentDescription = null) }
      } else {
        null
      },
    )
  }
}

// --- Segmentato -----------------------------------------------------------------------------------

@Composable
private fun SegmentedSection() {
  FluidSectionHeader(
    title = "Segmentato",
    detail = "Prendi la pillola e trascinala: un tick per segmento. Il secondo e' su una pagina piatta.",
  )
  var scope by remember { mutableStateOf("Settimana") }
  val options = listOf("Giorno", "Settimana", "Mese", "Anno")
  Stage(height = 110.dp) { backdrop ->
    CompositionLocalProvider(LocalFluidCanvasBackdrop provides backdrop) {
      FluidSegmentedControl(
        options = options,
        selected = scope,
        onSelect = { scope = it },
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp).testTag("all-glass-segmented"),
        label = { it },
      )
    }
  }
  FluidSegmentedControl(
    options = options,
    selected = scope,
    onSelect = { scope = it },
    modifier = Modifier.padding(horizontal = 4.dp).testTag("all-glass-segmented-flat"),
    label = { it },
  )
}

// --- Rail ---------------------------------------------------------------------------------------

@Composable
private fun RailSection() {
  FluidSectionHeader(title = "Rail", detail = "Tocca una scheda: la goccia diventa lente mentre viaggia.")
  var selected by remember { mutableStateOf("home") }
  val items = remember {
    listOf(
      FluidTabItem("home", "Home", FluidIcons.Home, filledIcon = FluidIcons.Filled.Home),
      FluidTabItem("star", "Preferiti", FluidIcons.Filled.Star),
      FluidTabItem("settings", "Opzioni", FluidIcons.Settings, filledIcon = FluidIcons.Filled.Settings),
    )
  }
  Stage(height = 220.dp) { backdrop ->
    FluidTabRail(
      items = items,
      selectedRoute = selected,
      onSelect = { selected = it.route },
      backdrop = backdrop,
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 16.dp)
        .height(192.dp)
        .testTag("all-glass-rail"),
    )
  }
}

// --- Indice di sezione -------------------------------------------------------------------------------

@Composable
private fun IndexSection() {
  FluidSectionHeader(
    title = "Indice di sezione",
    detail = "Tieni premuta la barretta a destra e trascina: la lente e' una goccia, la bolla un pannello.",
  )
  val sections = remember {
    listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago").mapIndexed { index, label ->
      FluidSectionAnchor(key = label, label = label, itemIndex = index)
    }
  }
  var active by remember { mutableStateOf("Gen") }
  Stage(height = 320.dp) { backdrop ->
    Text(
      text = "Sezione: $active",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White,
      modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
    )
    FluidSectionIndex(
      sections = sections,
      activeSectionKey = active,
      onSelectSection = { anchor, _ -> active = anchor.key },
      modifier = Modifier.align(Alignment.CenterEnd),
      backdrop = backdrop,
    )
  }
}

// --- Barra flottante -----------------------------------------------------------------------------------

@Composable
private fun TabBarSection() {
  FluidSectionHeader(
    title = "Barra flottante con banda",
    detail = "La banda sopra le schede e' il vetro della barra. Ripiegala e tocca: luce del dito e un colpo.",
  )
  var selected by remember { mutableStateOf("home") }
  val scroll = rememberFluidFloatingTabBarScrollConnection(
    inlineBehavior = FluidFloatingTabBarInlineBehavior.Never,
  )
  Stage(height = 320.dp) { backdrop ->
    FluidFloatingTabBar(
      selectedTabKey = selected,
      scrollConnection = scroll,
      modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).testTag("all-glass-tabbar"),
      backdrop = backdrop,
      inlineAccessory = { band, _ -> NowPlaying(band) },
      expandedAccessory = { band, _ -> NowPlaying(band) },
    ) {
      tab(
        key = "home",
        title = { Text("Home") },
        icon = { FluidIcon(FluidIcons.Home, contentDescription = null) },
        onClick = { selected = "home" },
      )
      tab(
        key = "star",
        title = { Text("Preferiti") },
        icon = { FluidIcon(FluidIcons.Filled.Star, contentDescription = null) },
        onClick = { selected = "star" },
      )
      tab(
        key = "settings",
        title = { Text("Opzioni") },
        icon = { FluidIcon(FluidIcons.Settings, contentDescription = null) },
        onClick = { selected = "settings" },
      )
      standaloneTab(
        key = "search",
        icon = { FluidIcon(FluidIcons.Search, contentDescription = "Cerca") },
        onClick = {},
      )
    }
  }
  FluidButton(
    text = if (scroll.isInline) "Apri la barra" else "Ripiega la barra",
    onClick = { if (scroll.isInline) scroll.expand() else scroll.inline() },
    style = FluidButtonStyle.Tinted,
    modifier = Modifier.padding(horizontal = 4.dp),
  )
}

/** Il contenuto della banda: lo disegna l'app, sopra il vetro che la barra le mette. */
@Composable
private fun NowPlaying(modifier: Modifier) {
  Row(
    modifier = modifier.height(56.dp).padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    FluidIcon(FluidIcons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
      text = "In riproduzione",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

// --- Suggerimento ----------------------------------------------------------------------------------------

@Composable
private fun CoachSection() {
  FluidSectionHeader(
    title = "Suggerimento",
    detail = "Senza punta dipinta: \"Ok\" e' un tasto di vetro. Chiudilo e rimostralo per vedere l'ingresso.",
  )
  var shown by remember { mutableStateOf(true) }
  val density = LocalDensity.current
  val tutorial = remember {
    FluidTutorial(
      id = "demo",
      priority = 0,
      title = "Tieni premuto",
      text = "Premi a lungo una riga per aprire il menu delle azioni.",
      hint = FluidGestureHint.LongPress,
    )
  }
  Stage(height = 260.dp) { backdrop ->
    if (shown) {
      FluidCoachMark(
        tutorial = tutorial,
        labels = FluidTutorialLabels(dismiss = "Ok", next = "Avanti", optOut = "Non mostrare piu'"),
        placement = with(density) {
          FluidCoachMarkPlacement(x = 24.dp.toPx(), y = 36.dp.toPx(), below = true, pointerX = 60.dp.toPx())
        },
        backdrop = backdrop,
        onMeasured = {},
        onDismiss = { shown = false },
        onOptOut = { shown = false },
      )
    } else {
      FluidButton(
        text = "Mostra di nuovo",
        onClick = { shown = true },
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

// --- Sincronizzazione nella barra ------------------------------------------------------------------------------

@Composable
private fun SyncSection() {
  FluidSectionHeader(
    title = "Sincronizzazione nella barra",
    detail = "Quattro stati, una capsula di vetro con il tono dentro il materiale. Tocca per il dettaglio.",
  )
  val statuses = remember {
    listOf(
      SyncStatus(state = SyncState.IDLE, lastSuccessfulSyncEpochMillis = 1_700_000_000_000L),
      SyncStatus(state = SyncState.SYNCING),
      SyncStatus(state = SyncState.PARTIAL, message = "Una sezione non risponde.", failedSections = listOf("Voti")),
      SyncStatus(state = SyncState.ERROR, message = "Sessione scaduta."),
    )
  }
  Stage(height = 110.dp) { backdrop ->
    // Come nella barra superiore: il materiale della barra e' in scope, e i controlli lo rifrangono.
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
      Row(
        modifier = Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        statuses.forEach { status -> FluidSyncAction(status = status, onRetry = {}) }
      }
    }
  }
}

// --- Fogli e pagine ------------------------------------------------------------------------------------------------

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetsSection() {
  FluidSectionHeader(
    title = "Fogli e pagine",
    detail = "Il vecchio foglio apre quello di vetro dell'host; la pagina intera ha la chiusura di vetro.",
  )
  val hasHost = LocalFluidGlassModalHostState.current != null
  var sheet by remember { mutableStateOf(false) }
  var page by remember { mutableStateOf(false) }
  var container by remember { mutableStateOf(false) }

  if (!hasHost) {
    Text(
      text = "Questa sezione ha bisogno dell'host dei modali alla radice dell'app: senza, il foglio e' quello di piattaforma.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp),
    )
  }
  Row(
    modifier = Modifier.padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    FluidButton(text = "Foglio", onClick = { sheet = true }, style = FluidButtonStyle.Tinted, modifier = Modifier.testTag("all-glass-sheet"))
    FluidButton(text = "Pagina intera", onClick = { page = true }, style = FluidButtonStyle.Tinted, modifier = Modifier.testTag("all-glass-page"))
    FluidButton(text = "Contenitore", onClick = { container = true }, style = FluidButtonStyle.Tinted, modifier = Modifier.testTag("all-glass-container"))
  }

  if (sheet) {
    FluidSheet(onDismissRequest = { sheet = false }, title = "Foglio") {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "FluidSheet, con un host: il foglio e' di vetro, dentro la composizione. Trascinalo in basso per chiuderlo.",
          style = MaterialTheme.typography.bodyMedium,
        )
        FluidButton(text = "Chiudi", onClick = { sheet = false }, fillWidth = true)
      }
    }
  }
  FluidGlassModalPortal(
    visible = page,
    onDismissRequest = { page = false },
    presentation = FluidGlassModalPresentation.FullScreen,
    paneTitle = "Pagina intera",
  ) {
    Text(
      text = "Una pagina opaca, con la chiusura di vetro in alto a destra.",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(20.dp),
    )
  }
  if (container) {
    Dialog(
      onDismissRequest = { container = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Box(Modifier.fillMaxSize()) {
        FluidContainerScaffold(
          title = "Contenitore",
          onBack = { container = false },
          ambient = FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow),
          hero = {
            Text(
              text = "Sul fondale ambientale il gruppo e' una lastra di contenuto, come una lista di vetro.",
              style = MaterialTheme.typography.bodyLarge,
            )
          },
          secondary = {
            Text(
              text = "Senza fondale resterebbe il contenitore pieno di sempre.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
        )
      }
    }
  }
}

// --- Il palco ----------------------------------------------------------------------------------------------------------

/**
 * Un riquadro di colori e di testo nitido, registrato come fondale del vetro: una pagina piatta
 * rifrange una pagina piatta, e il vetro non si vedrebbe.
 */
@Composable
private fun Stage(
  height: Dp,
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  val wash = remember {
    Brush.linearGradient(
      colors = listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
      start = Offset.Zero,
      end = Offset(900f, 900f),
    )
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(backdrop)
        .drawBehind { drawRect(wash) },
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(16) { index ->
          Text(
            text = "Testo nitido dietro il vetro, riga ${index + 1} — ABCDEFG 0123456789",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (index % 2 == 0) Color.Black else Color.White,
          )
        }
      }
    }
    content(backdrop)
  }
}
