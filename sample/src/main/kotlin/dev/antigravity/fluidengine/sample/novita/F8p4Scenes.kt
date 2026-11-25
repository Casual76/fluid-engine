package dev.antigravity.fluidengine.sample.novita

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.viewer.VisoreTipo
import dev.antigravity.fluidengine.sample.viewer.VisoriFiles
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidDisclosureGroup
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGrid
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridHeader
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridSection
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionBackHandler
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidDragSelection
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBack
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.rememberFluidDisclosureState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPreviewGridState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.page.FluidSegmentedPager
import dev.antigravity.fluidengine.ui.page.rememberFluidSegmentedPagerState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.viewer.FluidFileThumbnailImage
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.rememberFluidFileThumbnail
import java.time.LocalDate
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// F8.4 · Liste e pagine: le sezioni richiudibili col vetro che si separa, la selezione trascinando, la griglia di
// anteprime (foto e file) e il pager col segmentato che e' la pillola della tab bar, nei casi delle app di Alessio (le
// schede `sezioni-richiudibili`, `selezione-trascinando`, `griglia-media`, `pager-segmentato`).

/** Le scene di F8.4, nell'ordine delle idee. */
internal val F8p4Section = NewElementSection(
  pack = "F8.4",
  title = "Liste e pagine",
  scenes = listOf(
    NewElementScene(
      "sezioni", "Note di versione e dettagli",
      "Pampa Store, Pampa Mail, ClasseViva · il contenuto nasce dal vetro del titolo e ci torna; «3 allegati» da chiusa",
    ) { onBack -> DisclosureScene(onBack) },
    NewElementScene(
      "selezione-righe", "Seleziona le mail trascinando",
      "Pampa Mail · due dita o la pressione lunga, il primo elemento decide, la lista scorre da sola ai bordi",
    ) { onBack -> RowsSelectionScene(onBack) },
    NewElementScene(
      "griglia-foto", "La libreria di foto",
      "MyCloud Gallery · pizzico fra 3, 5 e 9 colonne sul punto, mesi in alto, scorrimento con la data, selezione",
    ) { onBack -> PhotoGridScene(onBack) },
    NewElementScene(
      "griglia-file", "Una griglia di file",
      "Universal Converter · miniature vere di engine-viewer, celle col nome, 2, 3 o 4 colonne",
    ) { onBack -> FileGridScene(onBack) },
    NewElementScene(
      "pager-segmentato", "Voti, note e assenze",
      "ClasseViva · il segmentato e' la pillola della tab bar: segue il dito che trascina le pagine",
    ) { onBack -> PagerScene(onBack) },
  ),
)

/** I tag delle prove. */
internal object F8p4Tags {
  const val Background = "f8p4:fondo"
  const val Readout = "f8p4:valore"
  const val List = "f8p4:lista"
  const val Grid = "f8p4:griglia"
  const val Pager = "f8p4:pager"
  const val Select = "f8p4:seleziona"

  fun section(id: String) = "f8p4:sezione:$id"

  fun row(index: Int) = "f8p4:riga:$index"
}

/** Il fondo di una scena. */
internal enum class F8p4Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

// --- Le sezioni richiudibili -----------------------------------------------------------------------------------------

@Composable
private fun DisclosureScene(onBack: () -> Unit) {
  var ground by rememberSaveable { mutableStateOf(F8p4Ground.LightPhoto) }
  FluidScreen(title = "Note di versione e dettagli", subtitle = "F8.4 · sezioni", onBack = onBack) {
    item(key = "fondo") {
      FluidSegmentedControl(
        options = F8p4Ground.entries,
        selected = ground,
        onSelect = { ground = it },
        modifier = Modifier.fillMaxWidth().testTag(F8p4Tags.Background),
        label = { it.label },
      )
    }
    item(key = "scena") { DisclosureStage(ground) }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Aprendo, il contenuto nasce dal vetro del titolo, se ne stacca con un collo e scende al suo posto; " +
          "chiudendo ci torna dentro. Il titolo resta il nodo di TalkBack, con «Espanso» o «Compresso».",
      )
    }
  }
}

/** L'altezza della foto dietro le sezioni: piu' di tutte e tre aperte, cosi' non si riscala mai. */
private val DisclosurePhotoHeight = 1100.dp

@Composable
private fun DisclosureStage(ground: F8p4Ground) {
  val backdrop = rememberGlassBackdrop()
  Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    if (ground == F8p4Ground.Page) {
      Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background).glassBackdropSource(backdrop))
    } else {
      // La foto ha un'altezza sua, fissa, e il riquadro la ritaglia: quando una sezione si apre il riquadro si allunga e
      // ne scopre di piu', invece di riscalarla a ogni fotogramma (F8.4b).
      Box(Modifier.matchParentSize().clipToBounds().glassBackdropSource(backdrop)) {
        Image(
          painter = painterResource(if (ground == F8p4Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .requiredHeight(DisclosurePhotoHeight),
        )
      }
    }
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Pampa Store: le note di versione sotto un aggiornamento.
      FluidDisclosureGroup(
        title = "Pampa Notes 3.2",
        summary = "Note di versione",
        backdrop = backdrop,
        state = rememberFluidDisclosureState(initiallyExpanded = false),
        modifier = Modifier.testTag(F8p4Tags.section("store")),
      ) {
        Text(
          text = "• Le note si sincronizzano anche offline.\n• La ricerca trova le parole nelle foto.\n" +
            "• Corretto il cursore che saltava in fondo.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(16.dp),
        )
      }
      // Pampa Mail: i dettagli di consegna, con il riepilogo da chiusa.
      FluidDisclosureGroup(
        title = "Allegati",
        summary = "3 allegati",
        backdrop = backdrop,
        modifier = Modifier.testTag(F8p4Tags.section("allegati")),
      ) {
        val files = listOf("Preventivo.pdf" to "210 kB", "Planimetria.png" to "1,4 MB", "Contratto.docx" to "88 kB")
        files.forEachIndexed { i, (name, size) ->
          if (i > 0) FluidListDivider()
          FluidListRow(title = name, subtitle = size, disclosure = false)
        }
      }
      // ClasseViva: le note sotto un'assenza.
      FluidDisclosureGroup(
        title = "Assenza del 18 novembre",
        summary = "Giustificata",
        backdrop = backdrop,
        modifier = Modifier.testTag(F8p4Tags.section("assenza")),
      ) {
        FluidListRow(title = "Motivo", subtitle = "Visita medica", disclosure = false)
        FluidListDivider()
        FluidListRow(title = "Giustificata da", subtitle = "Genitore, 19 novembre", disclosure = false)
      }
    }
  }
}

// --- La selezione trascinando sulle righe ----------------------------------------------------------------------------

private val Senders = listOf("Giulia", "Marco", "Banca", "Scuola", "Luca", "Corriere", "Anna", "Biblioteca")

@Composable
private fun RowsSelectionScene(onBack: () -> Unit) {
  val selection = rememberFluidSelectionState<Int>()
  val listState = rememberLazyListState()
  val rows = remember { (0 until 80).toList() }
  FluidSelectionBackHandler(selection)
  BackHandler(enabled = !selection.isActive) { onBack() }
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
    SceneTopBar(
      title = if (selection.isActive) "${selection.countIn(rows.size)} selezionate" else "In arrivo",
      onBack = onBack,
      action = if (selection.isActive) "Fine" else "Seleziona",
      onAction = { if (selection.isActive) selection.clear() else selection.select(rows.first()) },
    )
    Text(
      text = "Due dita su una riga e giu', o una pressione lunga e giu': il primo elemento decide.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag(F8p4Tags.Readout),
    )
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag(F8p4Tags.List)
        .fluidDragSelection(selection, listState, keyAt = { rows.getOrNull(it) }),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
      itemsIndexed(rows, key = { _, id -> id }) { index, id ->
        val selected = selection.isSelected(id)
        Row(
          Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag(F8p4Tags.row(index))
            .padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (selection.isActive) {
            FluidSelectionMark(selected = selected, modifier = Modifier.padding(end = 12.dp))
          }
          Column(Modifier.weight(1f)) {
            Text(Senders[id % Senders.size], style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
              "Messaggio ${id + 1} · ${(id * 7) % 12 + 8}:${((id * 13) % 60).toString().padStart(2, '0')}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}


@Composable
private fun SceneTopBar(title: String, onBack: () -> Unit, action: String?, onAction: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidGlassIconButton(onClick = onBack) { FluidIcon(FluidIcons.ArrowBack, "Indietro") }
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
    )
    if (action != null) {
      FluidGlassButton(text = action, onClick = onAction, modifier = Modifier.testTag(F8p4Tags.Select))
    }
  }
}

// --- La griglia di foto ----------------------------------------------------------------------------------------------

/** Una foto finta della libreria: la data e come si disegna (una delle quattro immagini, o un colore). */
internal class F8p4Photo(val id: Int, val date: LocalDate, val image: Int, val bias: BiasAlignment, val color: Color)

/** La libreria: 2400 foto in 12 mesi, dalla piu' recente. Costruita una volta, fuori dal disegno. */
internal fun f8p4Library(count: Int = 2400): List<FluidPreviewGridSection<F8p4Photo>> {
  val start = LocalDate.of(2026, 11, 24)
  val photos = (0 until count).map { i ->
    val day = start.minusDays((i * 365L) / count)
    val h = (i * 47) % 360
    F8p4Photo(
      id = i,
      date = day,
      // Sparse con un passo primo, non `i % 5`: a 5 colonne ogni colonna ripeteva la stessa immagine (F8.4b).
      image = Math.floorMod(i * 7919 + (i / 5) * 104729 + (i / 3) * 31, 9),
      bias = BiasAlignment(((i * 37) % 21 - 10) / 10f, ((i * 53) % 21 - 10) / 10f),
      color = Color.hsv(h.toFloat(), 0.35f + (i % 7) * 0.05f, 0.55f + (i % 5) * 0.08f),
    )
  }
  return photos.groupBy { it.date.withDayOfMonth(1) }.map { (month, items) ->
    FluidPreviewGridSection(
      key = month,
      title = "${month.month.getDisplayName(TextStyle.FULL, Locale.ITALY).replaceFirstChar { it.titlecase() }} ${month.year}",
      items = items,
    )
  }
}

@Composable
private fun PhotoGridScene(onBack: () -> Unit) {
  val sections = remember { f8p4Library() }
  val selection = rememberFluidSelectionState<Int>()
  val grid = rememberFluidPreviewGridState()
  val context = LocalContext.current
  // Le cinque immagini, decodificate una volta per la scena: nessuna cella decodifica niente. Le voci da 5 a 8 sono
  // colori.
  val images = remember(context) {
    listOf(
      R.drawable.viewer_flower, R.drawable.viewer_astronaut, R.drawable.viewer_rocket, R.drawable.viewer_campanelle_cover,
      R.drawable.viewer_notes_cover,
    ).map { BitmapPainter(ImageBitmap.imageResource(context.resources, it)) }
  }
  val total = remember(sections) { sections.sumOf { it.items.size } }
  // La lingua dell'app, per la data media (come Foto).
  val locale = LocalConfiguration.current.locales[0]
  var selecting by rememberSaveable { mutableStateOf(false) }
  val mode = selecting || selection.isActive
  FluidSelectionBackHandler(selection, onExit = { selecting = false })
  BackHandler(enabled = selecting && !selection.isActive) { selecting = false }
  BackHandler(enabled = !mode) { onBack() }
  // Come Foto di iOS 26 (F8.4b, scelta di Alessio): il titolo e la data sopra le foto, i tasti di vetro che galleggiano,
  // le foto fin sotto la barra di stato. La testata la fa la griglia.
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    FluidPreviewGrid(
      sections = sections,
      itemKey = { it.id },
      state = grid,
      selection = selection,
      inSelectionMode = mode,
      contentPadding = WindowInsets.navigationBars.asPaddingValues(),
      title = if (mode) "${selection.countIn(total)} selezionate" else "Libreria",
      dateLabel = { photo -> F8p4Dates.medium(photo.date, locale) },
      fastScrollLabel = { photo -> F8p4Dates.medium(photo.date, locale) },
      navigation = { FluidGlassIconButton(onClick = onBack) { FluidIcon(FluidIcons.ArrowBack, "Indietro") } },
      actions = {
        FluidGlassButton(
          text = if (mode) "Fine" else "Seleziona",
          onClick = {
            if (mode) {
              selection.clear()
              selecting = false
            } else {
              selecting = true
            }
          },
          modifier = Modifier.testTag(F8p4Tags.Select),
        )
      },
      modifier = Modifier.fillMaxSize().testTag(F8p4Tags.Grid),
    ) { photo, selected ->
      F8p4PhotoCell(photo, images, selected)
    }
  }
}

/**
 * Le date della libreria, come Foto: la data **media della lingua**, «13 lug 2025» in italiano e «Jul 13, 2025» in
 * inglese, col mese abbreviato come lo scrive la lingua. Stessa data in testata e nello scorrimento rapido. La fa
 * `FluidFormat.date` dell'engine, dalle ICU di Android (F8.4c: con `java.time` il tablet scriveva «24 Nov 2026»).
 */
internal object F8p4Dates {
  fun medium(date: LocalDate, locale: Locale): String = FluidFormat.date(date, FormatStyle.MEDIUM, locale)
}

/** Una cella di foto: niente alloca disegnando (la tinta della scelta e' un velo letto dal layer). */
@Composable
private fun F8p4PhotoCell(photo: F8p4Photo, images: List<BitmapPainter>, selected: Boolean) {
  if (photo.image < images.size) {
    Image(
      painter = images[photo.image],
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = photo.bias,
      modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (selected) 0.72f else 1f },
    )
  } else {
    Box(Modifier.fillMaxSize().background(photo.color).graphicsLayer { alpha = if (selected) 0.72f else 1f })
  }
}

// --- La griglia di file ----------------------------------------------------------------------------------------------

@Composable
private fun FileGridScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val all = remember { (VisoriFiles.gallery + VisoriFiles.converter).filter { it.tipo != VisoreTipo.Sparito } }
  val prepared by produceState<List<FluidViewerFile>?>(null, context) {
    value = VisoriFiles.prepare(context, all, "f8p4")
  }
  val selection = rememberFluidSelectionState<String>()
  val grid = rememberFluidPreviewGridState(initialDensity = 1)
  var selecting by rememberSaveable { mutableStateOf(false) }
  val mode = selecting || selection.isActive
  FluidSelectionBackHandler(selection, onExit = { selecting = false })
  BackHandler(enabled = selecting && !selection.isActive) { selecting = false }
  BackHandler(enabled = !mode) { onBack() }
  val entries = remember(prepared) {
    val files = prepared ?: return@remember emptyList()
    all.zip(files)
  }
  val sections = remember(entries) {
    entries.groupBy {
      when (it.first.tipo) {
        VisoreTipo.Foto, VisoreTipo.Animata, VisoreTipo.Video, VisoreTipo.Grafica -> "Immagini e video"
        VisoreTipo.Pagine, VisoreTipo.Office, VisoreTipo.Testo, VisoreTipo.Codice, VisoreTipo.Dati -> "Documenti"
        else -> "Altro"
      }
    }.map { (title, items) -> FluidPreviewGridSection(key = title, title = title, items = items) }
  }
  val cellPx = with(LocalDensity.current) { 160.dp.roundToPx() }
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + FileGridTitleRoom
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    run {
      FluidPreviewGrid(
        sections = sections,
        itemKey = { it.first.id },
        state = grid,
        columns = listOf(2, 3, 4),
        cellAspectRatio = 0.78f,
        spacing = 12.dp,
        // La prima riga parte sotto la testata, e poi ci scorre sotto.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
        selection = selection,
        inSelectionMode = mode,
        header = { section -> FluidPreviewGridHeader(section.title ?: "") },
        // Sotto la testata ci sono nomi di file su una pagina chiara, non foto: inchiostro del tema e velo del fondo.
        titleOnPhotos = false,
        title = if (mode) "${selection.countIn(entries.size)} selezionati" else "File",
        navigation = { FluidGlassIconButton(onClick = onBack) { FluidIcon(FluidIcons.ArrowBack, "Indietro") } },
        actions = {
          FluidGlassButton(
            text = if (mode) "Fine" else "Seleziona",
            onClick = {
              if (mode) {
                selection.clear()
                selecting = false
              } else {
                selecting = true
              }
            },
            modifier = Modifier.testTag(F8p4Tags.Select),
          )
        },
        modifier = Modifier.fillMaxSize().testTag(F8p4Tags.Grid),
      ) { (file, viewer), selected ->
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = if (selected) 0.72f else 1f }) {
          Box(
            Modifier
              .fillMaxWidth()
              .weight(1f)
              .clip(ContinuousCornerShape(FluidRadius.Card))
              .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
          ) {
            val thumbnail = rememberFluidFileThumbnail(viewer, cellPx)
            if (thumbnail != null) FluidFileThumbnailImage(thumbnail, Modifier.fillMaxSize())
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Lo spazio della testata sopra la prima riga dei file: la riga dei tasti, il titolo grande e la data. */
private val FileGridTitleRoom = 150.dp

// --- Il pager col segmentato -----------------------------------------------------------------------------------------

private val Subjects = listOf("Matematica" to "8½", "Italiano" to "7", "Inglese" to "9", "Fisica" to "6½", "Storia" to "8",
  "Scienze" to "7½", "Arte" to "10", "Latino" to "6", "Filosofia" to "8")

@Composable
private fun PagerScene(onBack: () -> Unit) {
  val segments = listOf("Voti", "Note", "Assenze")
  val pager = rememberFluidSegmentedPagerState(pageCount = { segments.size })
  BackHandler { onBack() }
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(F8p4Tags.Pager)) {
    FluidSegmentedPager(segments = segments, state = pager) { index, padding ->
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (index) {
          0 -> {
            item { PagerPhoto(R.drawable.viewer_flower) }
            item { PagerCaption(index, segments.size) }
            item {
              FluidListGroup {
                Subjects.forEachIndexed { i, (subject, mark) ->
                  if (i > 0) FluidListDivider()
                  FluidListRow(title = subject, subtitle = "Ultimo voto: $mark", disclosure = false)
                }
              }
            }
          }
          1 -> {
            item { PagerPhoto(R.drawable.viewer_astronaut) }
            item { PagerCaption(index, segments.size) }
            item {
              FluidListGroup {
                listOf("Compiti non svolti" to "12 nov", "Ottima partecipazione" to "6 nov", "Ritardo" to "28 ott")
                  .forEachIndexed { i, (title, date) ->
                    if (i > 0) FluidListDivider()
                    FluidListRow(title = title, subtitle = date, disclosure = false)
                  }
              }
            }
          }
          else -> {
            item { PagerPhoto(R.drawable.viewer_rocket) }
            item { PagerCaption(index, segments.size) }
            item {
              FluidListGroup {
                listOf("18 novembre" to "Giustificata", "3 novembre" to "Da giustificare", "21 ottobre" to "Giustificata")
                  .forEachIndexed { i, (title, state) ->
                    if (i > 0) FluidListDivider()
                    FluidListRow(title = title, subtitle = state, disclosure = false)
                  }
              }
            }
          }
        }
      }
    }
  }
}

/** «Pagina 2 di 3», sotto la foto della pagina: scorre con lei e non copre niente (F8.4b). */
@Composable
private fun PagerCaption(index: Int, count: Int) {
  Text(
    "Pagina ${index + 1} di $count",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp).testTag(F8p4Tags.Readout),
  )
}

@Composable
private fun PagerPhoto(res: Int) {
  Image(
    painter = painterResource(res),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(ContinuousCornerShape(FluidRadius.Group)),
  )
}
