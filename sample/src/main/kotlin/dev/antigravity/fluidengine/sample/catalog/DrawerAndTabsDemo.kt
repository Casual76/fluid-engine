package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidDrawer
import dev.antigravity.fluidengine.ui.fluid.FluidDrawerState
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionBackHandler
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarRow
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarSection
import dev.antigravity.fluidengine.ui.fluid.FluidTabBadge
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidDrawerObscured
import dev.antigravity.fluidengine.ui.fluid.fluidIdentityColor
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Drafts
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Forum
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.Label
import dev.antigravity.fluidengine.ui.fluid.icons.Menu
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidDrawerState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * Il pannello laterale e le estensioni della barra (F1.3 parte B, pm-f1/ge), tutti insieme.
 *
 * Sta nella galleria come pagina «Pannello laterale e schede» (con la matrice degli stati). Le sezioni:
 *
 * 1. **La pillola a tre zone** (Posta, Cerca, Chat) col cerchio «Scrivi» accanto: il pallino sulla Chat si
 *    accende e si spegne, un numero al posto del pallino, la barra si ripiega (il segno sparisce, il
 *    cerchio resta) e si riapre. «Scrivi» e' un pulsante: non cambia la zona.
 * 2. **Il rail con «Scrivi» in testa**, per le finestre da 600 dp, col segno sulle zone.
 * 3. **Il pannello delle cartelle**: si apre dal ☰ (non dal bordo), sezioni per identita' (avatar,
 *    indirizzo, colore), righe annidate a 48 dp con i numeri, piede fermo. Si chiude toccando fuori, col
 *    back, trascinandolo a sinistra o con l'azione di TalkBack. Con la «selezione attiva» accesa il back
 *    chiude prima il pannello e poi la selezione.
 * 4. **Varianti**: testo al 200%, tema scuro, movimento ridotto (la piega e il pannello arrivano in un
 *    fotogramma; il dito segue lo stesso).
 *
 * Ogni palco consuma gli inset della finestra, cosi' il pannello si comporta come se il palco fosse lo
 * schermo. Il vuoto e l'errore non hanno senso qui (sono componenti di navigazione); lo stato «carico» e'
 * la lista lunga del pannello, che scorre.
 */
@Composable
fun DrawerAndTabsDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("drawer-and-tabs-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    PillSection()
    RailSection()
    DrawerSection()
    VariantsSection()
  }
}

// --- Il palco ------------------------------------------------------------------------------------------

@Composable
private fun Stage(
  height: Dp,
  modifier: Modifier = Modifier,
  page: @Composable BoxScope.() -> Unit = { StagePage() },
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .consumeWindowInsets(WindowInsets.safeDrawing),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(backdrop)
        .drawBehind {
          drawRect(
            Brush.linearGradient(
              colors = listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
              start = Offset.Zero,
              end = Offset(900f, 900f),
            ),
          )
        },
      content = page,
    )
    content(backdrop)
  }
}

/**
 * Le righe fittizie dietro il vetro. [topInset] e' lo spazio che la scena si prende in alto (i tasti e
 * le etichette di stato): le righe cominciano sotto, cosi' nessuna scritta della scena ne copre una.
 */
@Composable
private fun StagePage(topInset: Dp = 0.dp) {
  Column(
    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = maxOf(16.dp, topInset + 8.dp)),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    repeat(12) { i ->
      Text(
        text = "Messaggio ${i + 1} — un'anteprima di testo nitido dietro il vetro",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

// --- 1. La pillola ------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillSection() {
  FluidSectionHeader(
    title = "Pillola a tre zone",
    detail = "Il pallino della Chat solo a barra aperta; «Scrivi» e' un pulsante, non una zona.",
  )
  var dot by remember { mutableStateOf(true) }
  var count by remember { mutableStateOf(false) }
  var folded by remember { mutableStateOf(false) }
  var zone by remember { mutableStateOf("mail") }
  var writes by remember { mutableIntStateOf(0) }
  // Un flusso: al 200% i tre tasti non stanno affiancati, e in una `Row` «Numero» si spezzava e «Ripiega» spariva.
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidButton(text = if (dot) "Spegni il segno" else "Accendi il segno", onClick = { dot = !dot }, style = FluidButtonStyle.Tinted)
    FluidButton(text = if (count) "Pallino" else "Numero", onClick = { count = !count }, style = FluidButtonStyle.Tinted)
    FluidButton(text = if (folded) "Riapri" else "Ripiega", onClick = { folded = !folded }, style = FluidButtonStyle.Tinted)
  }
  Stage(height = 220.dp, modifier = Modifier.testTag("pill-stage")) { backdrop ->
    PillBar(
      backdrop = backdrop,
      badge = when {
        !dot -> null
        count -> FluidTabBadge(count = 12)
        else -> FluidTabBadge.Dot
      },
      folded = folded,
      zone = zone,
      onZone = { zone = it },
      onWrite = { writes++ },
    )
  }
  // Fuori dal palco: dentro, al 200% il testo di stato si sovrapponeva ai messaggi del fondale.
  Text(text = "Zona: $zone · «Scrivi» toccato $writes volte", style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun BoxScope.PillBar(
  backdrop: GlassBackdropState,
  badge: FluidTabBadge?,
  folded: Boolean,
  zone: String,
  onZone: (String) -> Unit,
  onWrite: () -> Unit,
) {
  val scroll = rememberFluidFloatingTabBarScrollConnection()
  androidx.compose.runtime.LaunchedEffect(folded) { if (folded) scroll.inline() else scroll.expand() }
  FluidFloatingTabBar(
    selectedTabKey = zone,
    scrollConnection = scroll,
    backdrop = backdrop,
    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 16.dp),
  ) {
    tab(key = "mail", title = { Text("Posta") }, icon = { FluidIcon(FluidIcons.Inbox, null) }, onClick = { onZone("mail") })
    tab(key = "search", title = { Text("Cerca") }, icon = { FluidIcon(FluidIcons.Search, null) }, onClick = { onZone("search") })
    tab(
      key = "chat",
      title = { Text("Chat") },
      icon = { FluidIcon(FluidIcons.Forum, null) },
      onClick = { onZone("chat") },
      badge = badge,
    )
    standaloneTab(key = "write", icon = { FluidIcon(FluidIcons.Edit, contentDescription = "Scrivi") }, onClick = onWrite)
  }
}

// --- 2. Il rail ----------------------------------------------------------------------------------------

@Composable
private fun RailSection() {
  FluidSectionHeader(
    title = "Rail con «Scrivi» in testa",
    detail = "Da 600 dp la pillola diventa il rail: l'azione primaria ha ancora il suo posto fisso.",
  )
  var route by remember { mutableStateOf("mail") }
  var writes by remember { mutableIntStateOf(0) }
  val items = listOf(
    FluidTabItem(route = "mail", label = "Posta", icon = FluidIcons.Inbox, selectedIcon = FluidIcons.Filled.Inbox, badge = FluidTabBadge(count = 3)),
    FluidTabItem(route = "search", label = "Cerca", icon = FluidIcons.Search, selectedIcon = FluidIcons.Filled.Search),
    FluidTabItem(route = "chat", label = "Chat", icon = FluidIcons.Forum, selectedIcon = FluidIcons.Filled.Forum, badge = FluidTabBadge.Dot),
  )
  Stage(height = 340.dp, modifier = Modifier.testTag("rail-stage")) { backdrop ->
    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      FluidTabRail(
        items = items,
        selectedRoute = route,
        onSelect = { route = it.route },
        backdrop = backdrop,
        modifier = Modifier.fillMaxHeight(),
        header = {
          FluidGlassIconButton(onClick = { writes++ }, backdrop = backdrop) {
            FluidIcon(FluidIcons.Edit, contentDescription = "Scrivi")
          }
        },
      )
    }
  }
  // Fuori dal palco, per la stessa ragione della pillola: al 200% si sovrapponeva al fondale.
  Text(text = "Zona: $route · «Scrivi»: $writes", style = MaterialTheme.typography.labelLarge)
}

// --- 3. Il pannello ------------------------------------------------------------------------------------

@Composable
private fun DrawerSection() {
  FluidSectionHeader(
    title = "Pannello delle cartelle",
    detail = "Dal ☰ e mai dal bordo. Si chiude toccando fuori, col back, trascinandolo a sinistra.",
  )
  var selecting by remember { mutableStateOf(false) }
  FluidListGroup {
    FluidToggleRow(
      title = "Selezione attiva sotto il pannello",
      subtitle = "Il back chiude prima il pannello, poi la selezione",
      checked = selecting,
      onCheckedChange = { selecting = it },
    )
  }
  // L'altezza della riga dei tasti e dello stato, misurata dalla scena: a testo grande l'etichetta va a
  // capo e la riga cresce, e le righe fittizie scendono con lei invece di finirle sotto.
  var headerPx by remember { mutableIntStateOf(0) }
  val headerHeight = with(LocalDensity.current) { headerPx.toDp() }
  Stage(
    height = 560.dp,
    modifier = Modifier.testTag("drawer-stage"),
    page = { StagePage(topInset = headerHeight) },
  ) { backdrop ->
    DrawerScene(
      backdrop = backdrop,
      selecting = selecting,
      onSelectingChange = { selecting = it },
      onHeaderSized = { headerPx = it },
    )
  }
}

@Composable
private fun BoxScope.DrawerScene(
  backdrop: GlassBackdropState,
  selecting: Boolean,
  onSelectingChange: (Boolean) -> Unit,
  onHeaderSized: (heightPx: Int) -> Unit = {},
  state: FluidDrawerState = rememberFluidDrawerState(),
) {
  var folder by remember { mutableStateOf("inbox") }
  val selection = rememberFluidSelectionState<String>()
  androidx.compose.runtime.LaunchedEffect(selecting) {
    if (selecting) selection.select("m1") else selection.clear()
  }
  // Il back della selezione: composto prima del pannello, che entra nella pila quando si apre.
  FluidSelectionBackHandler(selection, onExit = { onSelectingChange(false) })
  val opener = remember { FocusRequester() }

  Box(Modifier.fillMaxSize().fluidDrawerObscured(state)) {
    // Una riga sola per il tasto e lo stato: lo stato ha il suo spazio (prende il resto della larghezza e,
    // a testo grande, va a capo) e le righe fittizie del palco cominciano sotto la riga.
    Row(
      modifier = Modifier
        .align(Alignment.TopStart)
        .fillMaxWidth()
        .onSizeChanged { onHeaderSized(it.height) }
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      FluidGlassIconButton(
        onClick = { state.open() },
        modifier = Modifier.focusRequester(opener),
        backdrop = backdrop,
      ) { FluidIcon(FluidIcons.Menu, contentDescription = "Cartelle") }
      Text(
        text = if (selection.isActive) "1 selezionata · cartella: $folder" else "Cartella: $folder",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
      )
    }
  }
  FluidDrawer(
    state = state,
    backdrop = backdrop,
    paneTitle = "Cartelle",
    returnFocusTo = opener,
    header = {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidAvatar(name = "Anna Rossi")
        Column {
          Text("Anna Rossi", style = MaterialTheme.typography.titleMedium)
          Text("2 account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
    footer = {
      FluidSidebarRow(
        label = "Impostazioni",
        selected = false,
        onClick = { state.close() },
        icon = FluidIcons.Settings,
        minHeight = FluidSidebarDefaults.ComfortableRowHeight,
      )
    },
  ) {
    drawerFolders(current = folder, onPick = { folder = it; state.close() })
  }
}

private fun LazyListScope.drawerFolders(current: String, onPick: (String) -> Unit) {
  item { FluidSidebarSection(title = "Tutte le caselle") }
  folderRow("inbox", "Posta in arrivo", FluidIcons.Inbox, current, onPick, count = 12)
  folderRow("starred", "Speciali", FluidIcons.Filled.Star, current, onPick)
  folderRow("drafts", "Bozze", FluidIcons.Drafts, current, onPick, count = 2)
  folderRow("sent", "Inviata", FluidIcons.Send, current, onPick)
  folderRow("trash", "Cestino", FluidIcons.Delete, current, onPick)
  for ((name, address) in listOf("Anna Rossi" to "anna@esempio.it", "Lavoro" to "anna.rossi@studio.it")) {
    item(key = "section-$address") {
      FluidSidebarSection(
        title = address,
        uppercase = false,
        leading = { FluidAvatar(name = name, key = address, size = 24.dp) },
        detail = name,
      )
    }
    folderRow("$address/inbox", "Posta in arrivo", FluidIcons.Inbox, current, onPick, count = 150, identity = address)
    folderRow("$address/archive", "Archivio", FluidIcons.Archive, current, onPick, identity = address)
    folderRow("$address/projects", "Progetti", FluidIcons.Folder, current, onPick, identity = address)
    folderRow("$address/projects/2026", "2026", FluidIcons.Folder, current, onPick, level = 1, count = 4, identity = address)
    folderRow("$address/projects/2026/q4", "Quarto trimestre", FluidIcons.Folder, current, onPick, level = 2, identity = address)
    folderRow("$address/label", "Fatture", FluidIcons.Label, current, onPick, identity = address)
  }
}

private fun LazyListScope.folderRow(
  key: String,
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  current: String,
  onPick: (String) -> Unit,
  count: Int = 0,
  level: Int = 0,
  identity: String? = null,
) {
  item(key = key) {
    FluidSidebarRow(
      label = label,
      selected = key == current,
      onClick = { onPick(key) },
      icon = icon,
      // Il colore dell'account sulla piastrella, mai sulla riga.
      tint = identity?.let { fluidIdentityColor(it) },
      level = level,
      minHeight = FluidSidebarDefaults.ComfortableRowHeight,
      trailing = if (count > 0) {
        { FluidBadge(count = count) }
      } else {
        null
      },
    )
  }
}

// --- 4. Varianti ---------------------------------------------------------------------------------------

@Composable
private fun VariantsSection() {
  FluidSectionHeader(
    title = "Varianti",
    detail = "Testo al 200% (la barra cresce, il pannello scorre), tema scuro, movimento ridotto.",
  )
  val density = LocalDensity.current
  Text("Testo al 200%", style = MaterialTheme.typography.labelLarge)
  CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
    VariantStage(tag = "variant-large-text")
  }
  Text("Tema scuro", style = MaterialTheme.typography.labelLarge)
  FluidTheme(settings = EngineSettings(themeMode = ThemeMode.DARK), systemBars = false) {
    Box(
      Modifier
        .fillMaxWidth()
        .clip(ContinuousCornerShape(FluidRadius.Group))
        .background(MaterialTheme.colorScheme.surface)
        .padding(8.dp),
    ) { VariantStage(tag = "variant-dark") }
  }
  Text("Movimento ridotto", style = MaterialTheme.typography.labelLarge)
  CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
    VariantStage(tag = "variant-reduced-motion")
  }
}

/** La pillola col segno e il pannello, in un palco solo: quello che si rompe col testo grande, al buio, senza animazioni. */
@Composable
private fun VariantStage(tag: String) {
  var folded by remember { mutableStateOf(false) }
  // Come nel palco del pannello: la riga dei tasti (☰ e «Ripiega») ha il suo spazio, misurato dalla scena, e le
  // righe fittizie cominciano sotto di lei. A testo grande la riga cresce e le righe scendono con lei.
  var headerPx by remember { mutableIntStateOf(0) }
  val headerHeight = with(LocalDensity.current) { headerPx.toDp() }
  Stage(
    height = 480.dp,
    modifier = Modifier.testTag(tag),
    page = { StagePage(topInset = headerHeight) },
  ) { backdrop ->
    val drawer = rememberFluidDrawerState()
    var folder by remember { mutableStateOf("inbox") }
    Box(Modifier.fillMaxSize().fluidDrawerObscured(drawer)) {
      Row(
        modifier = Modifier
          .align(Alignment.TopStart)
          .onSizeChanged { headerPx = it.height }
          .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidGlassIconButton(onClick = { drawer.open() }, backdrop = backdrop) {
          FluidIcon(FluidIcons.Menu, contentDescription = "Cartelle")
        }
        FluidButton(text = if (folded) "Riapri" else "Ripiega", onClick = { folded = !folded }, style = FluidButtonStyle.Tinted)
      }
      var zone by remember { mutableStateOf("mail") }
      PillBar(backdrop = backdrop, badge = FluidTabBadge.Dot, folded = folded, zone = zone, onZone = { zone = it }, onWrite = {})
    }
    FluidDrawer(state = drawer, backdrop = backdrop, paneTitle = "Cartelle") {
      drawerFolders(current = folder, onPick = { folder = it; drawer.close() })
    }
  }
}
