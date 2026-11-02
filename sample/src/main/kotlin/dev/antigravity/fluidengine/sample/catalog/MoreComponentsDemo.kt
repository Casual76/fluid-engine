package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.UpdateChannel
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidImageViewer
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidUpdatePrompt
import dev.antigravity.fluidengine.ui.fluid.FluidUpdatePromptSheet
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateRelease
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateStage
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidMoved
import dev.antigravity.fluidengine.ui.fluid.fluidReorderable
import dev.antigravity.fluidengine.ui.fluid.fluidUpdateSection
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidReorderState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * I pezzi generici che piu' app si erano riscritte da sole, della fase 12c: l'aggiornamento in-app (l'avviso e
 * le righe delle impostazioni), il riordino per trascinamento (una colonna e una griglia), lo scorrimento
 * rapido con la bolla e il visualizzatore di immagini.
 *
 * Non e' ancora nella galleria: la monta chi la prepara. Sta in una colonna sola e **si porta il suo host dei
 * modali** (un palco per il visualizzatore e per il foglio dell'aggiornamento), cosi' funziona dove la si mette.
 * L'orologio ha la sua demo nell'engine-wear: la lista con i quattro stati (`FluidWearList`) non e' qui perche'
 * la galleria e' un'app da telefono.
 *
 * Il vetro vuole qualcosa di nitido dietro: il riordino e lo scorrimento stanno su un palco di colori, che
 * e' anche il canvas che la goccia dell'elemento sollevato rifrange.
 */
@Composable
fun MoreComponentsDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("more-components-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    UpdateDemoSection()
    ReorderColumnSection()
    ReorderGridSection()
    FastScrollSection()
    ViewerSection()
  }
}

// --- Palco -----------------------------------------------------------------------------------------

/** Il palco: un riquadro con un fondale colorato registrato come il canvas del contenuto. */
@Composable
private fun Stage(
  height: androidx.compose.ui.unit.Dp,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  val wash = remember {
    Brush.linearGradient(
      colors = listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
      start = Offset.Zero,
      end = Offset(900f, 700f),
    )
  }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .drawBehind { drawRect(wash) },
  ) {
    // Il fondale dietro, **registrato da solo**: e' quello che la goccia dell'elemento sollevato rifrange.
    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop))
    CompositionLocalProvider(LocalFluidCanvasBackdrop provides backdrop) {
      content(backdrop)
    }
  }
}

// --- Aggiornamento ---------------------------------------------------------------------------------

private val DemoRelease = FluidUpdateRelease(
  version = "2.1.0",
  notes = "**Novità**\n- il vetro dei pannelli ora è uno solo\n- il riordino per trascinamento\n- correzioni varie\n\n" +
    "Le beta arrivano prima: nelle impostazioni si sceglie il canale.",
  sizeBytes = 48L * 1024 * 1024 + 300L * 1024,
)

private enum class UpdateScenario(val label: String) {
  Idle("Mai controllato"),
  Checking("Controllo"),
  UpToDate("Aggiornata"),
  CheckFailed("Controllo fallito"),
  Available("Disponibile"),
  Downloading("Scarico"),
  UnknownSize("Peso ignoto"),
  Verifying("Verifica"),
  AwaitingUser("Conferma"),
  Installed("Installata"),
  Failed("Fallita"),
  NeedsPermission("Permesso"),
}

private fun UpdateScenario.state(): FluidUpdateUiState = when (this) {
  UpdateScenario.Idle -> FluidUpdateUiState.Idle
  UpdateScenario.Checking -> FluidUpdateUiState.Checking
  UpdateScenario.UpToDate -> FluidUpdateUiState.UpToDate
  UpdateScenario.CheckFailed -> FluidUpdateUiState.CheckFailed()
  UpdateScenario.Available -> FluidUpdateUiState.Available(DemoRelease)
  UpdateScenario.Downloading -> FluidUpdateUiState.Downloading(
    DemoRelease, progress = 0.42f, downloadedBytes = 21L * 1024 * 1024, totalBytes = DemoRelease.sizeBytes,
  )
  UpdateScenario.UnknownSize -> FluidUpdateUiState.Downloading(DemoRelease, progress = null)
  UpdateScenario.Verifying -> FluidUpdateUiState.Working(DemoRelease, FluidUpdateStage.Verifying)
  UpdateScenario.AwaitingUser -> FluidUpdateUiState.Working(DemoRelease, FluidUpdateStage.AwaitingUser)
  UpdateScenario.Installed -> FluidUpdateUiState.Installed(DemoRelease)
  UpdateScenario.Failed -> FluidUpdateUiState.Failed(DemoRelease, "Non c'è abbastanza spazio sul telefono.")
  UpdateScenario.NeedsPermission -> FluidUpdateUiState.NeedsPermission(DemoRelease)
}

/** Una installazione finta: scarica, verifica, chiede la conferma e finisce. Quello che farebbe `AppUpdater.install`. */
private fun CoroutineScope.simulateInstall(set: (FluidUpdateUiState) -> Unit) = launch {
  for (step in 0..20) {
    set(
      FluidUpdateUiState.Downloading(
        DemoRelease, step / 20f, (DemoRelease.sizeBytes * step) / 20, DemoRelease.sizeBytes,
      ),
    )
    delay(120)
  }
  set(FluidUpdateUiState.Working(DemoRelease, FluidUpdateStage.Verifying))
  delay(900)
  set(FluidUpdateUiState.Working(DemoRelease, FluidUpdateStage.Installing))
  delay(900)
  set(FluidUpdateUiState.Working(DemoRelease, FluidUpdateStage.AwaitingUser))
  delay(1_400)
  set(FluidUpdateUiState.Installed(DemoRelease))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateDemoSection() {
  val scope = rememberCoroutineScope()
  val host = rememberFluidGlassModalHostState()
  var scenario by remember { mutableStateOf(UpdateScenario.Available) }
  var state by remember { mutableStateOf(UpdateScenario.Available.state()) }
  var channel by remember { mutableStateOf(UpdateChannel.STABLE) }
  var autoDownload by remember { mutableStateOf(false) }
  var sheetOpen by remember { mutableStateOf(false) }
  var skipped by remember { mutableStateOf(false) }

  fun choose(next: UpdateScenario) {
    scenario = next
    state = next.state()
  }

  FluidSectionHeader(
    title = "Aggiornamento in-app",
    detail = "Lo stesso modello per l'avviso e per le righe delle impostazioni: si sceglie lo stato e si guarda.",
  )
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    UpdateScenario.entries.forEach { candidate ->
      FluidChip(label = candidate.label, selected = candidate == scenario, onClick = { choose(candidate) })
    }
  }

  Text(
    text = "Le righe delle impostazioni",
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  LazyColumn(
    modifier = Modifier.fillMaxWidth().height(420.dp).testTag("update-section"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    fluidUpdateSection(
      state = state,
      onCheck = {
        state = FluidUpdateUiState.Checking
        scope.launch {
          delay(900)
          state = FluidUpdateUiState.Available(DemoRelease)
          scenario = UpdateScenario.Available
        }
      },
      onInstall = { scope.simulateInstall { state = it } },
      currentVersion = "2.0.0-beta.1",
      lastCheckedAtMillis = System.currentTimeMillis() - 7 * 60_000L,
      channel = channel,
      onChannelChange = { channel = it },
      autoDownload = autoDownload,
      onAutoDownloadChange = { autoDownload = it },
      onSkip = { skipped = true; state = FluidUpdateUiState.UpToDate },
      footnote = if (skipped) "La 2.1.0 è saltata: non torna finché non ne esce un'altra." else null,
    )
  }

  Text(
    text = "L'avviso (il contenuto di un foglio)",
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ContinuousCornerShape(FluidRadius.Sheet))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    FluidUpdatePrompt(
      state = state,
      onInstall = { scope.simulateInstall { state = it } },
      onLater = { choose(UpdateScenario.Idle) },
      onSkip = { skipped = true; choose(UpdateScenario.UpToDate) },
      insetBottom = false,
    )
  }
  FluidButton(
    text = "Apri come foglio",
    onClick = { sheetOpen = true },
    style = FluidButtonStyle.Tinted,
    enabled = state is FluidUpdateUiState.Available,
  )
  // Il foglio ha il suo host: un palco piccolo, dove sale dal basso.
  Stage(height = 360.dp) { backdrop ->
    CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
      Text(
        text = "Il palco del foglio",
        modifier = Modifier.align(Alignment.Center),
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      FluidUpdatePromptSheet(
        state = if (sheetOpen) state else null,
        onInstall = { scope.simulateInstall { state = it } },
        onLater = { sheetOpen = false },
        onSkip = { sheetOpen = false; skipped = true },
      )
      FluidGlassModalHost(state = host, backdrop = backdrop)
    }
  }
}

// --- Riordino --------------------------------------------------------------------------------------

private val Elements = listOf("Terra", "Aria", "Fuoco", "Acqua", "Etere", "Luce", "Ombra", "Tempo")

@Composable
private fun ReorderColumnSection() {
  var rows by remember { mutableStateOf(Elements) }
  var ended by remember { mutableStateOf(0) }
  val listState = rememberLazyListState()
  val reorder = rememberFluidReorderState(
    listState = listState,
    onMove = { from, to -> rows = rows.fluidMoved(from, to) },
    onDragEnd = { ended++ },
  )

  FluidSectionHeader(
    title = "Riordino: una colonna",
    detail = "Tieni premuto una riga e trascina: i vicini scalano, un colpo per ogni posto, la lista scorre ai bordi. " +
      "Ordine salvato $ended volte: ${rows.joinToString(" ")}",
  )
  Stage(height = 400.dp) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().testTag("reorder-column"),
      contentPadding = PaddingValues(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(rows, key = { _, name -> name }) { _, name ->
        Row(
          modifier = fluidReorderable(reorder, key = name)
            .fillMaxWidth()
            .clip(ContinuousCornerShape(FluidRadius.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(Icons.Rounded.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
      }
    }
  }
}

@Composable
private fun ReorderGridSection() {
  var tiles by remember { mutableStateOf((1..12).map { "Città $it" }) }
  val gridState = rememberLazyGridState()
  val reorder = rememberFluidReorderState(
    gridState = gridState,
    onMove = { from, to -> tiles = tiles.fluidMoved(from, to) },
  )
  val palette = remember {
    listOf(Color(0xFF5B8DEF), Color(0xFFEF6F5B), Color(0xFF58B07B), Color(0xFFE0A93B), Color(0xFF9B6BDE), Color(0xFF3FB6C6))
  }

  FluidSectionHeader(
    title = "Riordino: una griglia",
    detail = "Le tessere cambiano posto sui due assi. Senza vetro dietro, la tessera sollevata cresce e proietta un'ombra.",
  )
  Box(Modifier.fillMaxWidth().height(380.dp).clip(ContinuousCornerShape(FluidRadius.Group))) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      state = gridState,
      modifier = Modifier.fillMaxSize().testTag("reorder-grid"),
      contentPadding = PaddingValues(14.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      itemsIndexed(tiles, key = { _, name -> name }) { _, name ->
        val color = palette[name.filter { it.isDigit() }.toInt() % palette.size]
        Box(
          modifier = fluidReorderable(reorder, key = name)
            .aspectRatio(1f)
            .clip(ContinuousCornerShape(FluidRadius.Card))
            .background(color),
          contentAlignment = Alignment.Center,
        ) {
          Text(name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
      }
    }
  }
}

// --- Scorrimento rapido ----------------------------------------------------------------------------

private val Months = listOf(
  "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
  "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
)

/** Un mese ogni otto righe, dal gennaio 2019: l'etichetta che la bolla dice. */
private fun monthLabel(index: Int): String {
  val month = (index / 8) % 12
  val year = 2019 + (index / 8) / 12
  return "${Months[month]} $year"
}

@Composable
private fun FastScrollSection() {
  val listState = rememberLazyListState()
  val backdrop = rememberGlassBackdrop()

  FluidSectionHeader(
    title = "Scorrimento rapido",
    detail = "Scorri, e compare la maniglia: trascinala e la lista va dove va il dito, con la bolla del mese. Un colpo per mese.",
  )
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(420.dp)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .testTag("fast-scroll-stage"),
  ) {
    // La lista e' registrata come fondale; la maniglia sta fuori dalla registrazione (se stesse dentro
    // campionerebbe se stessa).
    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(MaterialTheme.colorScheme.surface)) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("fast-scroll-list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        items(count = 480, key = { it }) { index ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(36.dp).clip(ContinuousCornerShape(FluidRadius.Small)).background(MaterialTheme.colorScheme.primaryContainer))
            Column {
              Text("Foto ${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
              Text(monthLabel(index), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
    FluidFastScroller(
      state = listState,
      label = ::monthLabel,
      backdrop = backdrop,
      modifier = Modifier.padding(vertical = 8.dp),
    )
  }
}

// --- Visualizzatore --------------------------------------------------------------------------------

private val Pictures = listOf(
  listOf(Color(0xFF283E51), Color(0xFF4B79A1)),
  listOf(Color(0xFFDD5E89), Color(0xFFF7BB97)),
  listOf(Color(0xFF134E5E), Color(0xFF71B280)),
)

@Composable
private fun ViewerSection() {
  val host = rememberFluidGlassModalHostState()
  var open by remember { mutableStateOf(false) }
  var page by remember { mutableStateOf(0) }

  FluidSectionHeader(
    title = "Visualizzatore di immagini",
    detail = "Dentro l'host dei modali: pizzica o tocca due volte per ingrandire, sposta (l'immagine non esce dal suo " +
      "bordo), scorri di lato per cambiare, trascina in giù per chiudere. Pagina: ${page + 1}.",
  )
  FluidButton(text = "Apri il visualizzatore", onClick = { open = true }, style = FluidButtonStyle.Tinted)
  Stage(height = 520.dp) { backdrop ->
    CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
      Text(
        text = "Il palco del visualizzatore",
        modifier = Modifier.align(Alignment.Center),
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      FluidImageViewer(
        visible = open,
        onDismissRequest = { open = false },
        pageCount = Pictures.size,
        onPageChange = { page = it },
        // Un'immagine larga 3:2: i limiti dello spostamento seguono l'immagine, non il riquadro.
        contentAspectRatio = { 1.5f },
      ) { index ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .drawBehind {
              drawRect(Brush.linearGradient(Pictures[index], start = Offset.Zero, end = Offset(size.width, size.height)))
            },
          contentAlignment = Alignment.Center,
        ) {
          Text("Immagine ${index + 1}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
      }
      FluidGlassModalHost(state = host, backdrop = backdrop)
    }
  }
}
