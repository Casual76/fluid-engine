package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.media.FluidAttachmentMenu
import dev.antigravity.fluidengine.media.FluidAttachmentPanel
import dev.antigravity.fluidengine.media.FluidAttachmentPanelHost
import dev.antigravity.fluidengine.media.FluidAttachmentSource
import dev.antigravity.fluidengine.media.FluidDeviceMediaSource
import dev.antigravity.fluidengine.media.FluidFakeMediaSource
import dev.antigravity.fluidengine.media.FluidMediaAccess
import dev.antigravity.fluidengine.media.FluidMediaItem
import dev.antigravity.fluidengine.media.FluidMediaKind
import dev.antigravity.fluidengine.media.FluidMediaPicker
import dev.antigravity.fluidengine.media.FluidMediaRequest
import dev.antigravity.fluidengine.media.FluidMediaSource
import dev.antigravity.fluidengine.media.FluidPickedMedia
import dev.antigravity.fluidengine.media.rememberFluidAttachmentPanelState
import dev.antigravity.fluidengine.media.rememberFluidMediaPickerState
import dev.antigravity.fluidengine.media.rememberFluidMediaThumbnail
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAsyncImage
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidComposerSlots
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTrailing
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// F8.14 · Foto e allegati (`scelta-foto-e-video`, `pannello-allegati`), le scene della pagina «Elementi nuovi». Le foto
// sono **solo immagini di prova**: le sorgenti delle scene sono finte (`FluidFakeMediaSource`), anche quella che fa la
// parte del telefono, cosi' la galleria non legge mai la libreria vera del dispositivo. Il Photo Picker di Android non si
// offre (mostrerebbe le foto vere).

/** La sezione della pagina. */
internal val F8p14Section: NewElementSection
  get() = NewElementSection(
    pack = "F8.14",
    title = "Foto e allegati",
    scenes = F8p14Scenes,
    detail = "Il selettore di foto e video con MyCloud Gallery e la ricerca per significato, e il «+» degli allegati",
  )

internal val F8p14Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "selettore-foto", "Allega foto",
    "Pampa Mail · scelta in ordine col numero, album, ricerca per significato in MyCloud Gallery, anteprima che nasce dalla foto",
  ) { onBack -> F8p14PickerScene(onBack, limit = 10) },
  NewElementScene(
    "foto-profilo", "La foto del profilo",
    "Account Pampa · una foto sola: un tocco sceglie e torna, come in iOS",
  ) { onBack -> F8p14PickerScene(onBack, limit = 1) },
  NewElementScene(
    "accesso-foto", "Accesso alle foto",
    "Tutte, «solo alcune», nessun permesso, MyCloud Gallery bloccata, il NAS che non risponde",
  ) { onBack -> F8p14AccessScene(onBack) },
  NewElementScene(
    "allegati-piu", "Il «+» di Messaggi",
    "Pampa Mail · la scheda di vetro che nasce dal «+»: le recenti in cima, poi le fonti",
  ) { onBack -> F8p14MenuScene(onBack) },
  NewElementScene(
    "allegati-tastiera", "Allegati al posto della tastiera",
    "PampAI · il pannello prende il posto della tastiera, alla sua altezza, senza salti del campo",
  ) { onBack -> F8p14KeyboardPanelScene(onBack) },
)

/** I tag delle prove e delle riprese. */
internal object F8p14Tags {
  const val Result = "f8p14:risultato"
  const val Open = "f8p14:apri"
  const val Plus = "f8p14:piu"
  const val Ground = "f8p14:fondo"
  const val Regime = "f8p14:regime"
  const val Tray = "f8p14:vassoio"

  fun tray(index: Int) = "f8p14:vassoio:$index"
}

/** Il fondo della conversazione nelle scene del pannello. */
internal enum class F8p14Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

/**
 * Le immagini di prova della galleria (le cinque dei visori), ridotte una volta: le sorgenti finte le usano come miniature
 * per un elemento su tre; gli altri sono sfumature.
 */
internal object F8p14Images {
  private val ids = listOf(
    R.drawable.viewer_flower, R.drawable.viewer_astronaut, R.drawable.viewer_rocket,
    R.drawable.viewer_campanelle_cover, R.drawable.viewer_notes_cover,
  )
  @Volatile private var decoded: List<Bitmap>? = null

  fun all(context: Context): List<Bitmap> = decoded ?: synchronized(this) {
    decoded ?: ids.mapNotNull { id ->
      val options = BitmapFactory.Options().apply { inSampleSize = 2 }
      BitmapFactory.decodeResource(context.resources, id, options)
    }.also { decoded = it }
  }

  /** La miniatura di prova di [item] col lato [sizePx], o null per lasciare la sfumatura. */
  fun thumbnail(context: Context, item: FluidMediaItem, sizePx: Int): Bitmap? {
    val n = item.id.filter { it.isDigit() }.toIntOrNull() ?: return null
    if (n % 3 != 0) return null
    val photos = all(context)
    if (photos.isEmpty()) return null
    val photo = photos[(n / 3) % photos.size]
    val side = sizePx.coerceIn(32, 1600)
    val scale = side.toFloat() / maxOf(photo.width, photo.height)
    if (scale >= 1f) return photo
    return Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt().coerceAtLeast(1), (photo.height * scale).toInt().coerceAtLeast(1), true)
  }
}

/** Le due sorgenti di prova: «il telefono» (finto) e MyCloud Gallery (finta, con la ricerca e il backup). */
internal class F8p14Sources(context: Context) {
  private val app = context.applicationContext

  val phone: FluidFakeMediaSource = FluidFakeMediaSource.sample(
    id = FluidDeviceMediaSource.SourceId, label = "Su questo telefono", count = 48,
  ).let { base ->
    FluidFakeMediaSource(
      id = base.id, label = base.label,
      entries = base.entries.map { e -> FluidFakeMediaSource.Entry(e.item.copy(backupOnly = false), e.tags, e.albums, e.colors) },
      albumList = listOf(FluidFakeMediaSource.Album("vacanze", "Fotocamera"), FluidFakeMediaSource.Album("famiglia", "WhatsApp")),
      capabilities = dev.antigravity.fluidengine.media.FluidMediaSourceCapabilities(search = true, semanticSearch = false, albums = true),
      thumbnails = { item, size -> F8p14Images.thumbnail(app, item, size) },
    )
  }

  val cloud: FluidFakeMediaSource = FluidFakeMediaSource.sample(id = "mycloud", label = "MyCloud Gallery", count = 90).let { base ->
    FluidFakeMediaSource(
      id = base.id, label = base.label, entries = base.entries,
      albumList = listOf(
        FluidFakeMediaSource.Album("vacanze", "Vacanze"),
        FluidFakeMediaSource.Album("famiglia", "Famiglia"),
        FluidFakeMediaSource.Album("pippo", "Pippo"),
      ),
      thumbnails = { item, size -> F8p14Images.thumbnail(app, item, size) },
    )
  }

  val all: List<FluidMediaSource> = listOf(phone, cloud)
}

@Composable
private fun rememberF8p14Sources(): F8p14Sources {
  val context = LocalContext.current
  return remember(context) { F8p14Sources(context) }
}

// --- Il selettore ----------------------------------------------------------------------------------------------------

@Composable
private fun F8p14PickerScene(onBack: () -> Unit, limit: Int) {
  val sources = rememberF8p14Sources()
  val request = remember(limit) {
    if (limit == 1) FluidMediaRequest(kinds = setOf(FluidMediaKind.Photo), limit = 1) else FluidMediaRequest.attachments(limit)
  }
  var open by rememberSaveable { mutableStateOf(true) }
  val picked = remember { mutableStateListOf<FluidPickedMedia>() }
  if (open) {
    val state = rememberFluidMediaPickerState(sources.all, request)
    FluidMediaPicker(
      state = state,
      onResult = { media ->
        picked.clear()
        picked += media
        open = false
      },
      onCancel = { if (picked.isEmpty()) onBack() else open = false },
      offerSystemPicker = false,
    )
  } else {
    BackHandler { onBack() }
    F8p14Result(
      title = if (limit == 1) "La foto del profilo" else "Allega foto",
      picked = picked,
      sources = sources,
      onAgain = { open = true },
      onBack = onBack,
    )
  }
}

/** Cosa e' tornato all'app: la fila degli elementi nell'ordine della scelta, con nome, sorgente e peso. */
@Composable
private fun F8p14Result(
  title: String,
  picked: List<FluidPickedMedia>,
  sources: F8p14Sources,
  onAgain: () -> Unit,
  onBack: () -> Unit,
) {
  FluidScreen(title = title, subtitle = "F8.14 · cosa riceve l'app", onBack = onBack, modifier = Modifier.testTag(F8p14Tags.Result)) {
    item(key = "fila") {
      F8p14Tray(picked, sources, onRemove = null)
    }
    item(key = "elenco") {
      FluidListGroup {
        picked.forEachIndexed { index, media ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = "${index + 1}. ${media.info.name}",
            subtitle = listOfNotNull(
              sources.all.firstOrNull { it.id == media.source }?.label,
              media.info.mimeType,
              if (media.item?.backupOnly == true) "solo nel backup" else null,
            ).joinToString(" · "),
            disclosure = false,
          )
        }
      }
    }
    item(key = "di-nuovo") {
      FluidListGroup { FluidListRow(title = "Scegli di nuovo", subtitle = "Riapre il selettore", onClick = onAgain, modifier = Modifier.testTag(F8p14Tags.Open)) }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Le foto di queste scene sono immagini di prova della galleria: le sorgenti sono finte, anche quella del telefono.",
      )
    }
  }
}

/** La fila degli allegati (il vassoio del composer): le miniature in ordine, con la «x» per toglierle. */
@Composable
private fun F8p14Tray(picked: List<FluidPickedMedia>, sources: F8p14Sources, onRemove: ((FluidPickedMedia) -> Unit)?) {
  val px = with(LocalDensity.current) { 64.dp.roundToPx() }
  val shape = ContinuousCornerShape(FluidRadius.Control)
  LazyRow(Modifier.fillMaxWidth().testTag(F8p14Tags.Tray), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    items(picked.size, key = { picked[it].uri.toString() }) { index ->
      val media = picked[index]
      val item = media.item
      val source = sources.all.firstOrNull { it.id == media.source }
      Box(Modifier.size(64.dp).testTag(F8p14Tags.tray(index))) {
        Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
          if (item != null && source != null) {
            FluidAsyncImage(rememberFluidMediaThumbnail(source, item, px), contentDescription = media.info.name, modifier = Modifier.fillMaxSize())
          }
          if (media.kind == FluidMediaKind.Video) {
            FluidIcon(FluidIcons.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, size = 16.dp, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
          }
        }
        if (onRemove != null) {
          FluidGlassIconButton(
            onClick = { onRemove(media) },
            size = 26.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
          ) { FluidIcon(FluidIcons.Close, "Togli ${media.info.name}", size = 14.dp) }
        }
      }
    }
  }
}

// --- I regimi dell'accesso -------------------------------------------------------------------------------------------

private enum class F8p14Regime(val label: String) {
  // Etichette corte: nel formato telefono cinque segmenti stanno in ~78 dp l'uno (sul tablet «Solo alcune» si tagliava).
  Full("Tutte"), Limited("Alcune"), Denied("Negate"), Locked("Bloccata"), Unreachable("Senza NAS")
}

@Composable
private fun F8p14AccessScene(onBack: () -> Unit) {
  val sources = rememberF8p14Sources()
  var regime by rememberSaveable { mutableStateOf(F8p14Regime.Limited) }
  var open by rememberSaveable { mutableStateOf(false) }
  val picked = remember { mutableStateListOf<FluidPickedMedia>() }
  // Il regime scelto si applica alle due sorgenti finte prima di aprire il selettore (dopo la composizione).
  SideEffect {
    sources.phone.access = when (regime) {
      F8p14Regime.Limited -> FluidMediaAccess.Limited
      F8p14Regime.Denied -> FluidMediaAccess.NotAllowed()
      else -> FluidMediaAccess.Full
    }
    sources.cloud.access = if (regime == F8p14Regime.Locked) FluidMediaAccess.Locked(null) else FluidMediaAccess.Full
    sources.cloud.backupReachable = regime != F8p14Regime.Unreachable
  }
  if (open) {
    val state = rememberFluidMediaPickerState(sources.all, FluidMediaRequest.attachments(6))
    FluidMediaPicker(
      state = state,
      onResult = { picked.clear(); picked += it; open = false },
      onCancel = { open = false },
      offerSystemPicker = false,
    )
    return
  }
  FluidScreen(title = "Accesso alle foto", subtitle = "F8.14 · i regimi", onBack = onBack) {
    item(key = "regime") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FluidSectionHeader(title = "Il telefono e MyCloud Gallery", detail = regimeDetail(regime))
        FluidSegmentedControl(
          options = F8p14Regime.entries,
          selected = regime,
          onSelect = { regime = it },
          modifier = Modifier.fillMaxWidth().testTag(F8p14Tags.Regime),
          label = { it.label },
        )
      }
    }
    item(key = "apri") {
      FluidListGroup {
        FluidListRow(title = "Apri il selettore", subtitle = "Sei elementi al massimo", onClick = { open = true }, modifier = Modifier.testTag(F8p14Tags.Open))
      }
    }
    if (picked.isNotEmpty()) item(key = "fila") { F8p14Tray(picked, sources, onRemove = null) }
  }
}

private fun regimeDetail(regime: F8p14Regime): String = when (regime) {
  F8p14Regime.Full -> "Tutta la libreria del telefono, e MyCloud Gallery aperta."
  F8p14Regime.Limited -> "Android 14 «solo alcune foto»: la fascia lo dice e «Gestisci» ne fa scegliere altre."
  F8p14Regime.Denied -> "Nessun permesso: il tasto lo chiede (F8.11). Qui la galleria non dichiara il permesso, apposta."
  F8p14Regime.Locked -> "MyCloud Gallery col blocco dell'app: niente si elenca finche' non si sblocca."
  F8p14Regime.Unreachable -> "Il NAS non risponde: scegliendo una foto solo nel backup, l'avviso dice perche' non esce niente."
}

// --- Il «+» di Messaggi: la scheda che nasce dal tasto ----------------------------------------------------------------

@Composable
private fun F8p14MenuScene(onBack: () -> Unit) {
  val sources = rememberF8p14Sources()
  val ns = rememberFluidGlassNamespace()
  var ground by rememberSaveable { mutableStateOf(F8p14Ground.LightPhoto) }
  var text by rememberSaveable { mutableStateOf("") }
  var menu by rememberSaveable { mutableStateOf(false) }
  var picker by rememberSaveable { mutableStateOf(false) }
  val attached = remember { mutableStateListOf<FluidPickedMedia>() }
  if (picker) {
    val state = rememberFluidMediaPickerState(sources.all, FluidMediaRequest.attachments(10))
    FluidMediaPicker(
      state = state,
      onResult = { media -> media.forEach { m -> if (attached.none { it.item?.key == m.item?.key }) attached += m }; picker = false },
      onCancel = { picker = false },
      offerSystemPicker = false,
    )
    return
  }
  val composer = FluidComposerSlots(
    leading = {
      FluidCapsuleAction(
        FluidIcons.Add, "Allegati", onClick = { menu = true },
        size = FluidSearchFieldDefaults.ActionSize,
        // Il «+» e' la sorgente della scheda: in testa, cosi' mentre la scheda e' aperta il tasto non si disegna.
        modifier = Modifier.fluidTransitionSource(F8p14PlusId, ns, FluidCapsuleShape).testTag(F8p14Tags.Plus),
      )
    },
    attachments = if (attached.isEmpty()) null else {
      { F8p14Tray(attached, sources, onRemove = { attached.remove(it) }) }
    },
    trailing = FluidSearchTrailing.voice(onVoice = {}),
  )
  Box(Modifier.fillMaxSize()) {
    FluidScreen(
      title = "Giulia",
      onBack = onBack,
      toolbar = { title(FluidToolbarTitleMode.Inline) },
      search = FluidSearchSpec.message(text, { text = it }, onSend = { text = ""; attached.clear() }, composer = composer),
    ) {
      item(key = "fondo") {
        FluidSegmentedControl(
          options = F8p14Ground.entries, selected = ground, onSelect = { ground = it },
          modifier = Modifier.fillMaxWidth().testTag(F8p14Tags.Ground), label = { it.label },
        )
      }
      item(key = "foto") { F8p14ChatPhoto(ground) }
      item(key = "nota") {
        FluidSectionFootnote(
          "Tocca il «+»: la scheda di vetro nasce dal tasto, come in Messaggi di iOS 26 (320 × 456, righe da 66, icone da 38). " +
            "In cima le foto recenti: un tocco allega, un altro toglie. «Foto» apre il selettore intero.",
        )
      }
    }
    FluidAttachmentMenu(
      visible = menu,
      onDismissRequest = { menu = false },
      source = FluidTransitionSource(F8p14PlusId, ns),
      recents = sources.phone,
      sources = listOf(
        FluidAttachmentSource.camera(onClick = {}),
        FluidAttachmentSource.photos(onClick = { picker = true }),
        FluidAttachmentSource.files(onClick = {}),
        FluidAttachmentSource.location(onClick = {}),
        FluidAttachmentSource.other("mycloud", "MyCloud Gallery", onClick = { picker = true }),
      ),
      attached = attached.mapNotNull { it.item?.key },
      onAttach = { m -> attached += m },
      onRemove = { key -> attached.removeAll { it.item?.key == key } },
      onAllPhotos = { menu = false; picker = true },
    )
  }
}

private const val F8p14PlusId = "f8p14-piu"

/**
 * Il fondo della conversazione: con «Foto chiara» o «Foto scura» una foto grande arrivata nella chat (una delle immagini
 * di prova), cosi' la scheda del «+» e il pannello stanno sopra una foto; con «Pagina» niente.
 */
@Composable
private fun F8p14ChatPhoto(ground: F8p14Ground) {
  if (ground == F8p14Ground.Page) return
  Image(
    painter = painterResource(if (ground == F8p14Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
    contentDescription = "Foto ricevuta",
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(ContinuousCornerShape(FluidRadius.Card)),
  )
}

// --- Il pannello al posto della tastiera -----------------------------------------------------------------------------

@Composable
private fun F8p14KeyboardPanelScene(onBack: () -> Unit) {
  val sources = rememberF8p14Sources()
  val panel = rememberFluidAttachmentPanelState()
  var text by rememberSaveable { mutableStateOf("") }
  var ground by rememberSaveable { mutableStateOf(F8p14Ground.Page) }
  val attached = remember { mutableStateListOf<FluidPickedMedia>() }
  BackHandler(enabled = panel.isOpen) { panel.close() }
  val composer = FluidComposerSlots(
    leading = {
      FluidCapsuleAction(
        FluidIcons.Add, if (panel.isOpen) "Chiudi gli allegati" else "Allegati",
        onClick = { panel.toggle() }, size = FluidSearchFieldDefaults.ActionSize, selected = panel.isOpen,
        modifier = Modifier.testTag(F8p14Tags.Plus),
      )
    },
    attachments = if (attached.isEmpty()) null else {
      { F8p14Tray(attached, sources, onRemove = { attached.remove(it) }) }
    },
    trailing = FluidSearchTrailing.voice(onVoice = {}),
  )
  FluidAttachmentPanelHost(
    state = panel,
    panel = {
      FluidAttachmentPanel(
        recents = sources.phone,
        sources = listOf(
          FluidAttachmentSource.camera(onClick = {}),
          FluidAttachmentSource.photos(onClick = {}),
          FluidAttachmentSource.files(onClick = {}),
          FluidAttachmentSource.audio(onClick = {}),
        ),
        attached = attached.mapNotNull { it.item?.key },
        onAttach = { m -> attached += m },
        onRemove = { key -> attached.removeAll { it.item?.key == key } },
      )
    },
  ) {
    Box(Modifier.fillMaxSize()) {
      FluidScreen(
        title = "PampAI",
        onBack = onBack,
        toolbar = { title(FluidToolbarTitleMode.Inline) },
        search = FluidSearchSpec.message(text, { text = it }, onSend = { text = ""; attached.clear() }, composer = composer),
      ) {
        item(key = "fondo") {
          FluidSegmentedControl(
            options = F8p14Ground.entries, selected = ground, onSelect = { ground = it },
            modifier = Modifier.fillMaxWidth().testTag(F8p14Tags.Ground), label = { it.label },
          )
        }
        item(key = "foto") { F8p14ChatPhoto(ground) }
        item(key = "nota") {
          FluidSectionFootnote(
            "Tocca il campo per la tastiera, poi il «+»: il pannello prende il posto della tastiera alla sua altezza e il " +
              "campo resta fermo. Tocca il campo per tornare alla tastiera.",
          )
        }
      }
    }
  }
}

/** Le voci da provare a mano (in coda a «Da provare»). */
internal val F8p14TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-14-ordine", TryArea.ContentData, "La scelta in ordine",
    "Apri «Allega foto», tocca quattro foto, togli la seconda e passa il dito di lato su una fila: giudica se i numeri " +
      "scalano sfocati, se la fila si sceglie in ordine e se a dieci le altre si spengono.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Allega foto"),
  ),
  TryItem(
    "f8-14-anteprima", TryArea.ContentData, "L'anteprima che nasce dalla foto",
    "Nel selettore tieni premuta una foto: giudica se cresce dal suo riquadro fino a tutto schermo, se sfogliando e " +
      "trascinando in giu' rientra nella foto giusta, e se da li' si sceglie.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Allega foto"),
  ),
  TryItem(
    "f8-14-ricerca", TryArea.ContentData, "Cerca in MyCloud Gallery",
    "Nel selettore tocca la lente e scrivi «mare d'estate»: giudica se sotto il campo compare cosa ha capito e se le foto " +
      "con la nuvola (solo nel backup) si distinguono.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Allega foto"),
  ),
  TryItem(
    "f8-14-accesso", TryArea.ContentData, "Solo alcune foto",
    "Apri «Accesso alle foto», prova «Alcune», «Bloccata» e «Senza NAS»: giudica se la fascia, lo sblocco e " +
      "l'avviso dicono la cosa giusta con parole semplici.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Accesso alle foto"),
  ),
  TryItem(
    "f8-14-piu", TryArea.ContentData, "Il «+» di Messaggi",
    "Apri «Il «+» di Messaggi» e tocca il «+»: giudica se la scheda nasce dal tasto e ci rientra, se le recenti si " +
      "allegano col numero e se l'allegato arriva nel campo.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il «+» di Messaggi"),
  ),
  TryItem(
    "f8-14-tastiera", TryArea.ContentData, "Il pannello al posto della tastiera",
    "Apri «Allegati al posto della tastiera», apri la tastiera e tocca il «+»: giudica se il campo resta fermo mentre la " +
      "tastiera lascia il posto al pannello, e tornando alla tastiera.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Allegati al posto della tastiera"),
  ),
)
