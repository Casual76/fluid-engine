package dev.antigravity.fluidengine.sample.catalog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.LocalGalleryMatrix
import dev.antigravity.fluidengine.sample.MatrixData
import dev.antigravity.fluidengine.sample.MatrixTextScale
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.sample.rememberMatrixFlag
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidExperimentalRowGlass
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.FluidGroupedMaterial
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidJumpPill
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonList
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeAction
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGroupedItems
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeActions
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSwipeState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.ProvideFluidHapticTrace
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cosa mostra la lista della demo: gli stati che ogni lista lunga deve saper dire. */
internal enum class LongListsState(val label: String) {
  Content("Elenco"),
  Empty("Vuoto"),
  Loading("Caricamento"),
  Error("Errore"),
}

/** Quante righe: poche per guardare angoli e separatori, 50 000 per il banco di misura. */
internal enum class LongListsSize(val label: String, val rows: Int) {
  Few("12 righe", 12),
  Huge("50 000 righe", 50_000),
}

/** Il fondo delle righe, per il confronto del banco di misura. */
internal enum class LongListsMaterial(val label: String) {
  Opaque("Opaco"),
  Tinted("Tinto"),
  Glass("Vetro per riga"),
}

/**
 * Liste lunghe e righe (Fase 1 di Pampa Mail, F1.2a): `fluidGroupedItems`, `fluidSwipeActions`, `FluidJumpPill`,
 * le azioni TalkBack della riga, lo stato vuoto con un'azione e la pillola di stato a colore libero, in una pagina.
 *
 *  * **La lista**: un item per riga con la chiave dell'elemento, separatori e angoli che si richiudono quando
 *    una riga entra o esce, l'ingresso e l'uscita animati (spenti col movimento ridotto);
 *  * **Lo scorrimento di lato**: verso destra «letta/da leggere» (la riga torna), verso sinistra «Archivia» (la riga
 *    esce e la lista richiude il posto; «Annulla l'ultima» la rimette, con la stessa chiave). Le stesse azioni sono
 *    fra quelle di TalkBack della riga, insieme a «Fissa» (E05). Tenuta premuta, la riga apre il suo menu;
 *  * **«Arriva una riga in cima»**: con la lista a meta', quello che si sta leggendo resta fermo e compare la
 *    pillola «N nuove ↑»; in cima, la riga nuova entra in vista;
 *  * **Stati**: vuoto (con l'azione, E06), caricamento (scheletri), errore (con «Riprova»), chiaro/scuro, testo al
 *    200%, movimento ridotto;
 *  * **Banco di misura** (solo nelle build di debug): 50 000 righe e i tre fondi (opaco, tinto, vetro per riga, che la
 *    regola vieta e qui si misura), con il conto dei fotogrammi lenti (FrameMetrics della finestra, l'equivalente di
 *    JankStats senza una dipendenza in piu') e delle catture del vetro (`FluidGlassDiagnostics.totalCaptures`, che a
 *    schermo fermo deve restare fermo). La prova si fa sul telefono: scorrere a lungo e leggere la percentuale.
 *
 * Non registrata nella galleria: la registra F1.6.
 */
@OptIn(FluidExperimentalRowGlass::class)
@Composable
fun LongListsDemo(bottomInset: Dp = 0.dp, onBack: (() -> Unit)? = null) {
  // Nella galleria gli interruttori partono dalla matrice degli stati (e restano liberi); fuori, da spenti.
  var dark by rememberMatrixFlag { it.theme.isDark }
  var largeText by rememberMatrixFlag { it.textScale == MatrixTextScale.Largest }
  var reducedMotion by rememberMatrixFlag { it.reducedMotion }

  val settings = matrixDemoSettings(dark)
  FluidTheme(settings = settings, systemBars = false) {
    ProvideFluidHapticTrace {
      val density = LocalDensity.current
      CompositionLocalProvider(
        LocalDensity provides Density(density.density, if (largeText) 2f else density.fontScale),
        LocalFluidMotionPolicy provides if (reducedMotion) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current,
      ) {
        LongListsPage(
          bottomInset = bottomInset,
          onBack = onBack,
          dark = dark,
          onDark = { dark = it },
          largeText = largeText,
          onLargeText = { largeText = it },
          reducedMotion = reducedMotion,
          onReducedMotion = { reducedMotion = it },
        )
      }
    }
  }
}

/** Gli item della pagina prima delle righe: il titolo di `FluidScreen` piu' quelli della demo. */
private const val ItemsBeforeRows = 1 + 5

@OptIn(FluidExperimentalRowGlass::class, ExperimentalLayoutApi::class)
@Composable
private fun LongListsPage(
  bottomInset: Dp,
  onBack: (() -> Unit)?,
  dark: Boolean,
  onDark: (Boolean) -> Unit,
  largeText: Boolean,
  onLargeText: (Boolean) -> Unit,
  reducedMotion: Boolean,
  onReducedMotion: (Boolean) -> Unit,
) {
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val debuggable = LocalContext.current.isDebuggable()

  // Lo stato dei dati parte dalla matrice (vuoto, caricamento, errore) e resta libero come il segmentato.
  val matrixData = LocalGalleryMatrix.current?.data
  var state by remember(matrixData) {
    mutableStateOf(
      when (matrixData) {
        MatrixData.Empty -> LongListsState.Empty
        MatrixData.Loading -> LongListsState.Loading
        MatrixData.Error -> LongListsState.Error
        else -> LongListsState.Content
      },
    )
  }
  var size by remember { mutableStateOf(LongListsSize.Few) }
  var material by remember { mutableStateOf(LongListsMaterial.Opaque) }
  var bench by remember { mutableStateOf(false) }

  // I dati: identificativi, dal piu' nuovo. Il contenuto di una riga si ricava dall'identificativo.
  var ids by remember(size) { mutableStateOf(List(size.rows) { (size.rows - 1 - it).toLong() }) }
  var nextId by remember(size) { mutableStateOf(size.rows.toLong()) }
  val read = remember(size) { mutableStateMapOf<Long, Boolean>() }
  val pinned = remember(size) { mutableStateMapOf<Long, Boolean>() }
  var lastArchived by remember(size) { mutableStateOf<Pair<Int, Long>?>(null) }
  var newAbove by remember(size) { mutableIntStateOf(0) }
  var opened by remember { mutableStateOf<Long?>(null) }

  // Le righe nuove arrivate sopra non contano piu' quando l'utente torna in cima da solo.
  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex <= ItemsBeforeRows }.collect { atTop -> if (atTop) newAbove = 0 }
  }

  fun arrive(howMany: Int) {
    val atTop = listState.firstVisibleItemIndex <= ItemsBeforeRows
    val fresh = List(howMany) { nextId + howMany - 1 - it }
    nextId += howMany
    ids = fresh + ids
    if (!atTop) newAbove += howMany
  }

  fun archive(id: Long) {
    val index = ids.indexOf(id)
    if (index < 0) return
    lastArchived = index to id
    ids = ids.toMutableList().also { it.removeAt(index) }
  }

  val groupedMaterial = when (material) {
    LongListsMaterial.Opaque -> FluidGroupedMaterial.Opaque
    LongListsMaterial.Tinted -> FluidGroupedMaterial.Tinted
    LongListsMaterial.Glass -> FluidGroupedMaterial.GlassSegments
  }

  FluidScreen(
    title = "Liste lunghe",
    subtitle = "Righe una per una, scorrimento di lato, novità in cima.",
    onBack = onBack,
    listState = listState,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.PrimaryToSecondary, motif = FluidHeroMotif.Cards) },
    overlay = { backdrop ->
      FluidJumpPill(
        visible = newAbove > 0 && state == LongListsState.Content,
        label = if (newAbove == 1) "1 nuova in alto" else "$newAbove nuove in alto",
        onClick = {
          newAbove = 0
          scope.launch { listState.animateScrollToItem(0) }
        },
        backdrop = backdrop,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = 64.dp)
          .testTag("demo-jump-pill"),
      )
      if (state == LongListsState.Content) {
        FluidFastScroller(
          state = listState,
          label = { index ->
            val row = index - ItemsBeforeRows
            ids.getOrNull(row)?.let { "Settimana ${it / 500 + 1}" }
          },
          backdrop = backdrop,
          modifier = Modifier.align(Alignment.CenterEnd),
        )
      }
    },
  ) {
    // 1 — i comandi della demo.
    item(key = "controls") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidSegmentedControl(
          options = LongListsState.entries,
          selected = state,
          onSelect = { state = it },
          label = { it.label },
          modifier = Modifier.testTag("demo-state"),
        )
        FluidSegmentedControl(
          options = LongListsSize.entries,
          selected = size,
          onSelect = { size = it },
          label = { it.label },
        )
        FluidListGroup {
          FluidToggleRow(title = "Tema scuro", checked = dark, onCheckedChange = onDark)
          FluidToggleRow(title = "Testo al 200%", checked = largeText, onCheckedChange = onLargeText)
          FluidToggleRow(title = "Movimento ridotto", checked = reducedMotion, onCheckedChange = onReducedMotion)
        }
      }
    }
    // 2 — gli arrivi e l'annullo.
    item(key = "actions") {
      // Un flusso e non una riga: al 200% due tasti non stanno affiancati, e in una `Row` il secondo veniva
      // schiacciato fino a una colonna di lettere. Cosi' va a capo, e ogni tasto ha la larghezza intera.
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        FluidButton(
          text = "Arriva una riga in cima",
          onClick = { arrive(1) },
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.testTag("demo-arrive"),
        )
        FluidButton(
          text = "Annulla l'ultima",
          onClick = {
            lastArchived?.let { (index, id) ->
              ids = ids.toMutableList().also { it.add(index.coerceAtMost(it.size), id) }
              lastArchived = null
            }
          },
          enabled = lastArchived != null,
          style = FluidButtonStyle.Tinted,
        )
      }
    }
    // 3 — il banco di misura, solo in debug.
    item(key = "bench") {
      if (debuggable) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidListGroup {
            FluidToggleRow(
              title = "Banco di misura",
              subtitle = "Fotogrammi lenti e catture del vetro mentre si scorre.",
              checked = bench,
              onCheckedChange = { bench = it },
            )
          }
          if (bench) {
            FluidSegmentedControl(
              options = LongListsMaterial.entries,
              selected = material,
              onSelect = { material = it },
              label = { it.label },
            )
            LongListsBench()
          }
        }
      }
    }
    // 4 — la nota.
    item(key = "note") {
      FluidSectionFootnote(
        text = opened?.let { "Aperto: elemento $it." }
          ?: "Scorri una riga a destra per «letta», a sinistra per archiviarla. Tienila premuta per il menu.",
      )
    }
    // 5 — l'intestazione della sezione.
    item(key = "rows-header") {
      FluidSectionHeader(title = "Elementi", detail = if (state == LongListsState.Content) "${ids.size}" else null)
    }

    when (state) {
      LongListsState.Empty -> item(key = "empty") {
        FluidEmptyState(
          title = "Nessun elemento",
          detail = "Quando arriva qualcosa compare qui, in cima.",
          actionLabel = "Aggiungi tre elementi",
          onAction = {
            state = LongListsState.Content
            arrive(3)
          },
          illustration = { Icon(Icons.Rounded.Inbox, contentDescription = null) },
          modifier = Modifier.testTag("demo-empty"),
        )
      }
      LongListsState.Loading -> item(key = "loading") { FluidSkeletonList(rows = 6) }
      LongListsState.Error -> item(key = "error") {
        FluidEmptyState(
          title = "Non è stato possibile caricare",
          detail = "Controlla la connessione e riprova.",
          actionLabel = "Riprova",
          onAction = { state = LongListsState.Content },
        )
      }
      LongListsState.Content -> fluidGroupedItems(
        count = ids.size,
        key = { index -> ids[index] },
        material = groupedMaterial,
      ) { index ->
        val id = ids[index]
        val isRead = read[id] == true
        val swipe = rememberFluidSwipeState()
        val scheme = MaterialTheme.colorScheme
        FluidListRow(
          title = "Elemento ${id + 1}",
          subtitle = if (isRead) "Letto · settimana ${id / 500 + 1}" else "Da leggere · settimana ${id / 500 + 1}",
          tone = if (isRead) FluidTone.Neutral else FluidTone.Primary,
          leading = { Icon(if (isRead) Icons.Rounded.MarkEmailRead else Icons.Rounded.MarkEmailUnread, null) },
          labels = if (pinned[id] == true || id % 7L == 0L) {
            {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pinned[id] == true) FluidStatusBadge("Fissato", tone = FluidTone.Warning)
                if (id % 7L == 0L) FluidStatusBadge("Lavoro", color = DemoLabelColors[(id % DemoLabelColors.size).toInt()])
              }
            }
          } else {
            null
          },
          onClick = { opened = id },
          contextActions = {
            listOf(
              FluidContextAction(label = if (isRead) "Segna da leggere" else "Segna come letto") { read[id] = !isRead },
              FluidContextAction(label = "Archivia", destructive = true) { archive(id) },
            )
          },
          customActions = listOf(
            CustomAccessibilityAction(if (pinned[id] == true) "Togli dai fissati" else "Fissa") {
              pinned[id] = pinned[id] != true
              true
            },
          ),
          modifier = Modifier
            .fluidSwipeActions(
              state = swipe,
              startAction = FluidSwipeAction(
                label = if (isRead) "Segna da leggere" else "Segna come letto",
                icon = if (isRead) Icons.Rounded.MarkEmailUnread else Icons.Rounded.MarkEmailRead,
                color = scheme.primary,
              ) { read[id] = !isRead },
              endAction = FluidSwipeAction(
                label = "Archivia",
                icon = Icons.Rounded.Archive,
                color = scheme.tertiary,
                removesItem = true,
              ) { archive(id) },
              shape = segmentShape,
              fill = rowFill,
            )
            .testTag("demo-row-$id"),
        )
      }
    }
  }
}

/** Colori d'etichetta scelti «dall'utente»: la pillola sceglie da se' il testo che si legge. */
private val DemoLabelColors = listOf(
  androidx.compose.ui.graphics.Color(0xFFFFD54F),
  androidx.compose.ui.graphics.Color(0xFF1A237E),
  androidx.compose.ui.graphics.Color(0xFF66BB6A),
  androidx.compose.ui.graphics.Color(0xFFE57373),
  androidx.compose.ui.graphics.Color(0xFF9E9E9E),
)

/**
 * Il contatore del banco: i fotogrammi della finestra (FrameMetrics, dal sistema, sul thread suo) e quelli oltre la
 * scadenza di un refresh, piu' le catture del vetro dall'apertura del banco. Si azzera riaprendolo.
 */
@Composable
private fun LongListsBench() {
  val context = LocalContext.current
  val window = remember(context) { context.findActivity()?.window }
  val frames = remember { AtomicLong() }
  val slow = remember { AtomicLong() }
  val capturesAtStart = remember { FluidGlassDiagnostics.totalCaptures }
  var shown by remember { mutableStateOf("In attesa di fotogrammi…") }

  if (window != null) {
    DisposableEffect(window) {
      val thread = HandlerThread("fluid-bench").apply { start() }
      @Suppress("DEPRECATION") // `Context.display` e' dall'API 30, la galleria parte dalla 26.
      val refreshHz = window.windowManager.defaultDisplay.refreshRate.takeIf { it > 0f } ?: 60f
      val deadlineNanos = (1_000_000_000.0 / refreshHz).toLong()
      val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        frames.incrementAndGet()
        if (metrics.getMetric(FrameMetrics.TOTAL_DURATION) > deadlineNanos) slow.incrementAndGet()
      }
      val attached = runCatching { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }.isSuccess
      onDispose {
        if (attached) runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
        thread.quitSafely()
      }
    }
  }
  LaunchedEffect(Unit) {
    while (true) {
      val total = frames.get()
      val late = slow.get()
      val percent = if (total == 0L) 0.0 else late * 100.0 / total
      val captures = FluidGlassDiagnostics.totalCaptures - capturesAtStart
      shown = "Fotogrammi $total · lenti $late (${"%.2f".format(percent)}%) · catture del vetro $captures"
      delay(500)
    }
  }
  Text(
    text = shown,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp).testTag("demo-bench"),
  )
}

private fun Context.isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
