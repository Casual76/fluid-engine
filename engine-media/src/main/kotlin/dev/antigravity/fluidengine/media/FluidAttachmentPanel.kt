package dev.antigravity.fluidengine.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAsyncImage
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidPresentationMode
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMarkDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.AttachFile
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.LocationOn
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoCamera
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** I numeri del pannello degli allegati (F8.14, `pannello-allegati`). */
object FluidAttachmentPanelDefaults {
  /**
   * L'altezza del pannello quando la tastiera non si e' ancora vista: 300 dp sopra la barra di sistema, quanto una
   * tastiera di Android in verticale (Gboard, ~280–310 dp). Appena la tastiera si vede, il pannello prende la sua
   * altezza (la quota della tastiera, come in Messaggi di iOS, dove il pannello sta al posto della tastiera).
   */
  val Height: Dp = 300.dp

  /**
   * Il lato delle foto recenti della striscia: 84 dp, come il selettore compatto di iOS 17 (`.photosPickerStyle(.compact)`,
   * una riga sola che scorre di lato: celle da ≈ 84 pt con passo 86, WWDC23, misurato il 25 novembre 2026; fiducia media).
   * Il pannello «+» di Messaggi non ha una striscia di recenti: e' la nota della scheda `pannello-allegati`.
   */
  val RecentSize: Dp = 84.dp

  /** Lo spazio fra le foto recenti: 2 dp, come le celle del selettore di iOS. */
  val RecentSpacing: Dp = 2.dp

  /** Quante foto recenti: 30 (la striscia scorre; per le altre c'e' «Tutte le foto»). */
  const val RecentCount: Int = 30

  /**
   * La molla dell'arrivo e dell'uscita quando la tastiera non c'e': quella dei fogli (smorzamento 0,86, rigidezza 380),
   * a occhio sul pannello di iOS che sale al posto della tastiera.
   */
  const val SpringDamping: Float = 0.86f
  const val SpringStiffness: Float = 380f
}

/**
 * Una fonte del pannello: la fotocamera, le foto, i file, la posizione, o una dell'app (una nota, un documento
 * dell'archivio). L'elenco e' aperto: l'app lo riempie nell'ordine che vuole. [tone] e' il colore della piastrella
 * dell'icona (mai della riga).
 */
@Immutable
class FluidAttachmentSource(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val tone: FluidTone = FluidTone.Neutral,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
) {
  companion object {
    /** La fotocamera: l'app apre la sua cattura (con un `FileProvider` suo per `ACTION_IMAGE_CAPTURE`). */
    fun camera(onClick: () -> Unit, label: String = FluidMediaLabels().camera): FluidAttachmentSource =
      FluidAttachmentSource("camera", label, FluidIcons.PhotoCamera, FluidTone.Neutral, onClick = onClick)

    /** Le foto: di solito il selettore intero ([FluidMediaPicker]) o il Photo Picker di Android. */
    fun photos(onClick: () -> Unit, label: String = FluidMediaLabels().photos): FluidAttachmentSource =
      FluidAttachmentSource("photos", label, FluidIcons.PhotoLibrary, FluidTone.Primary, onClick = onClick)

    /** I file: il selettore di documenti di Android (`OpenMultipleDocuments`) o il visore dei file dell'app. */
    fun files(onClick: () -> Unit, label: String = FluidMediaLabels().files): FluidAttachmentSource =
      FluidAttachmentSource("files", label, FluidIcons.Folder, FluidTone.Info, onClick = onClick)

    /** La posizione: l'app la chiede (col permesso dal tasto, F8.11) e la allega come vuole. */
    fun location(onClick: () -> Unit, label: String = FluidMediaLabels().location): FluidAttachmentSource =
      FluidAttachmentSource("location", label, FluidIcons.LocationOn, FluidTone.Success, onClick = onClick)

    /** Un messaggio vocale o un audio. */
    fun audio(onClick: () -> Unit, label: String = FluidMediaLabels().audio): FluidAttachmentSource =
      FluidAttachmentSource("audio", label, FluidIcons.Mic, FluidTone.Danger, onClick = onClick)

    /** Una fonte di un'altra app o dell'app stessa, con l'icona degli allegati. */
    fun other(id: String, label: String, onClick: () -> Unit, icon: ImageVector = FluidIcons.AttachFile, tone: FluidTone = FluidTone.Neutral) =
      FluidAttachmentSource(id, label, icon, tone, onClick = onClick)
  }
}

/** I tag delle prove e delle riprese del pannello. */
object FluidAttachmentPanelTags {
  const val Panel: String = "fluid-attach:pannello"
  const val Recents: String = "fluid-attach:recenti"
  const val Sources: String = "fluid-attach:fonti"
  fun recent(key: String): String = "fluid-attach:recente:$key"
  fun source(id: String): String = "fluid-attach:fonte:$id"
}

/**
 * Lo stato del pannello degli allegati: aperto o chiuso, e l'altezza della tastiera vista per ultima (il pannello prende
 * la sua). Si crea con [rememberFluidAttachmentPanelState]; il «+» del composer chiama [toggle].
 */
@Stable
class FluidAttachmentPanelState internal constructor(initiallyOpen: Boolean) {
  /** Il pannello e' aperto (o si sta aprendo). */
  var isOpen: Boolean by mutableStateOf(initiallyOpen)
    private set

  /** L'ultima altezza della tastiera, in px (0: mai vista). */
  internal var keyboardPx by mutableIntStateOf(0)

  /** Quanta parte del pannello e' riservata in fondo, in px (letta solo in misura: non ricompone). */
  internal var reservedPx by mutableFloatStateOf(0f)

  /** Apre il pannello al posto della tastiera (che si chiude). */
  fun open() {
    isOpen = true
  }

  /** Chiude il pannello. */
  fun close() {
    isOpen = false
  }

  /** Apre o chiude. */
  fun toggle() {
    isOpen = !isOpen
  }
}

/** Lo stato di un pannello degli allegati, chiuso di serie. Resta alla rotazione. */
@Composable
fun rememberFluidAttachmentPanelState(initiallyOpen: Boolean = false): FluidAttachmentPanelState =
  rememberSaveable(saver = Saver(save = { it.isOpen }, restore = { FluidAttachmentPanelState(it) })) {
    FluidAttachmentPanelState(initiallyOpen)
  }

/**
 * Il posto del pannello degli allegati: [content] (la schermata col composer) sta sopra, il [panel] in fondo, **al posto
 * della tastiera e alla sua altezza** (come il «+» di Messaggi di iOS e i pannelli di input al posto della tastiera).
 *
 * Il passaggio fra tastiera e pannello e' senza salto: il composer resta fermo.
 * - **Aprendo con la tastiera su**, il pannello prende subito il suo posto sotto la tastiera, che scende e lo scopre;
 *   per la schermata la tastiera e' gia' stata tolta (gli inset sono consumati per l'altezza del pannello).
 * - **Toccando il campo col pannello aperto**, la tastiera sale sopra il pannello e, quando e' arrivata, il pannello
 *   lascia il posto.
 * - **Senza tastiera** il pannello sale dal fondo con la molla dei fogli ([FluidAttachmentPanelDefaults]) e il composer con
 *   lui; chiudendo scende. Col movimento ridotto arriva e va via in un fotogramma.
 *
 * La quota della tastiera e la parte riservata si leggono in misura e in disegno: la tastiera che si muove non ricompone
 * la schermata.
 */
@Composable
fun FluidAttachmentPanelHost(
  state: FluidAttachmentPanelState,
  panel: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  val ime = WindowInsets.ime
  val nav = WindowInsets.navigationBars
  val keyboard = LocalSoftwareKeyboardController.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val defaultPx = with(density) { FluidAttachmentPanelDefaults.Height.roundToPx() } + nav.getBottom(density)
  val heightPx = if (state.keyboardPx > 0) state.keyboardPx else defaultPx
  val reserve = remember { Animatable(if (state.isOpen) heightPx.toFloat() else 0f) }
  val currentHeight by rememberUpdatedState(heightPx)
  // La quota della tastiera: l'ultima altezza ferma. E il campo toccato col pannello aperto: quando la tastiera e'
  // arrivata, il pannello lascia il posto (senza salto, perche' fino a li' la tastiera lo copriva).
  LaunchedEffect(state, ime, density) {
    var previous = -1
    snapshotFlow { ime.getBottom(density) }.collect { now ->
      if (now > 0 && now == previous) state.keyboardPx = now
      if (state.isOpen && now > 0 && now > previous && previous >= 0 && now >= currentHeight * 0.98f) {
        state.close()
        reserve.snapTo(0f)
      }
      previous = now
    }
  }
  LaunchedEffect(state.isOpen) {
    if (state.isOpen) {
      val imeNow = ime.getBottom(density)
      if (imeNow > 0) {
        // La tastiera c'e': il pannello prende subito il posto sotto di lei, e lei lo scopre scendendo.
        reserve.snapTo(max(currentHeight, imeNow).toFloat())
        keyboard?.hide()
      } else if (reduced) {
        reserve.snapTo(currentHeight.toFloat())
      } else {
        reserve.animateTo(currentHeight.toFloat(), spring(FluidAttachmentPanelDefaults.SpringDamping, FluidAttachmentPanelDefaults.SpringStiffness))
      }
    } else if (ime.getBottom(density) == 0 && reserve.value > 0f) {
      if (reduced) reserve.snapTo(0f)
      else reserve.animateTo(0f, spring(FluidAttachmentPanelDefaults.SpringDamping, FluidAttachmentPanelDefaults.SpringStiffness))
    } else {
      reserve.snapTo(0f)
    }
  }
  val panelInsets = remember(reserve) { ReservedInsets { reserve.value.roundToInt() } }
  // Si legge come si' o no: la molla che corre non ricompone, cambia solo la misura.
  val showing by remember(reserve) { derivedStateOf { reserve.value > 0f } }
  val backdrop = rememberGlassBackdrop()
  Layout(
    modifier = modifier,
    content = {
      Box(Modifier.glassBackdropSource(backdrop).consumeWindowInsets(panelInsets)) { content() }
      Box(Modifier.clipToBounds()) {
        if (state.isOpen || showing) {
          Box(
            Modifier.fillMaxSize()
              .glassSurface(backdrop, GlassDefaults.panelTint(), ContinuousCornerShape(topStart = FluidRadius.Group, topEnd = FluidRadius.Group), role = GlassRole.Floating, optics = GlassDefaults.panelOptics())
              .testTag(FluidAttachmentPanelTags.Panel),
          ) { panel() }
        }
      }
    },
  ) { measurables, constraints ->
    val reserved = reserve.value.roundToInt().coerceIn(0, constraints.maxHeight)
    state.reservedPx = reserved.toFloat()
    val full = constraints.maxHeight
    val contentPlaceable = measurables[0].measure(Constraints.fixed(constraints.maxWidth, (full - reserved).coerceAtLeast(0)))
    val panelHeight = max(currentHeight, reserved)
    val panelPlaceable = measurables[1].measure(Constraints.fixed(constraints.maxWidth, panelHeight))
    layout(constraints.maxWidth, full) {
      contentPlaceable.place(0, 0)
      panelPlaceable.place(0, full - reserved)
    }
  }
}

/** Gli inset che il pannello toglie alla schermata: tanto in fondo quanto il pannello riserva (letti in misura). */
private class ReservedInsets(private val bottom: () -> Int) : WindowInsets {
  override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0
  override fun getTop(density: Density): Int = 0
  override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0
  override fun getBottom(density: Density): Int = bottom()
}

/**
 * Il pannello degli allegati di vetro (F8.14, `pannello-allegati`), come il «+» di Messaggi di iOS 26: **le foto recenti
 * in cima**, in una striscia da toccare, **poi le fonti** ([sources]: la fotocamera, le foto, i file, la posizione, quelle
 * dell'app), un elenco aperto che l'app riempie. Sta in un [FluidAttachmentPanelHost].
 *
 * Una foto toccata si allega subito ([onAttach], gia' consegnata dalla sorgente, pronta da leggere) e porta il suo numero
 * finche' e' fra gli allegati dell'app ([attached], le chiavi nell'ordine del vassoio); toccata di nuovo si toglie
 * ([onRemove]). A [maxAttachments] le altre si spengono. Mentre una sorgente lenta (il NAS) la consegna, la foto porta la
 * rotella, un secondo tocco non la consegna di nuovo e conta gia' per il tetto; una consegna rifiutata lo dice con
 * l'avviso del selettore, e una sorgente chiusa chiede lo sblocco (R3).
 *
 * @param recents la sorgente della striscia (di solito il telefono, [FluidDeviceMediaSource]); null: niente striscia.
 *   Senza accesso alle foto la striscia lascia il posto al tasto del permesso (F8.11).
 * @param onAllPhotos «Tutte le foto» in fondo alla striscia: di solito apre il [FluidMediaPicker]; null: non c'e'.
 */
@Composable
fun FluidAttachmentPanel(
  recents: FluidMediaSource?,
  sources: List<FluidAttachmentSource>,
  attached: List<String>,
  onAttach: (FluidPickedMedia) -> Unit,
  onRemove: (key: String) -> Unit,
  modifier: Modifier = Modifier,
  request: FluidMediaRequest = FluidMediaRequest(),
  maxAttachments: Int = Int.MAX_VALUE,
  onAllPhotos: (() -> Unit)? = null,
  labels: FluidMediaLabels = LocalFluidMediaLabels.current,
) {
  Column(
    modifier
      .fillMaxSize()
      .navigationBarsPadding()
      .verticalScroll(rememberScrollState())
      .padding(top = 14.dp, bottom = 8.dp)
      .semantics { paneTitle = labels.panelTitle },
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (recents != null) {
      AttachmentRecents(recents, request, attached, onAttach, onRemove, maxAttachments, onAllPhotos, labels)
    }
    if (sources.isNotEmpty()) {
      Box(Modifier.padding(horizontal = 16.dp).testTag(FluidAttachmentPanelTags.Sources)) {
        FluidListGroup {
          sources.forEachIndexed { index, source ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = source.label,
              subtitle = "",
              tone = source.tone,
              leading = { FluidIcon(source.icon, null) },
              disclosure = false,
              onClick = if (source.enabled) source.onClick else null,
              modifier = Modifier.testTag(FluidAttachmentPanelTags.source(source.id)),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AttachmentRecents(
  source: FluidMediaSource,
  request: FluidMediaRequest,
  attached: List<String>,
  onAttach: (FluidPickedMedia) -> Unit,
  onRemove: (String) -> Unit,
  maxAttachments: Int,
  onAllPhotos: (() -> Unit)?,
  labels: FluidMediaLabels,
) {
  val scope = rememberCoroutineScope()
  val permission = rememberFluidPermission(
    permission = if (FluidMediaKind.Video in request.kinds) FluidPermission.PhotosAndVideos else FluidPermission.Photos,
    reason = labels.permissionReason,
    onResult = { scope.launch { source.refresh() } },
  )
  LaunchedEffect(permission.status) { source.refresh() }
  // R3: le foto che si stanno consegnando (una sorgente lenta, il NAS) e l'avviso dell'ultima consegna non riuscita. Una
  // foto in consegna non si consegna una seconda volta e conta per il tetto finché non arriva; un rifiuto si dice con la
  // frase del selettore, e una sorgente chiusa chiede lo sblocco come nel selettore.
  var pending by remember(source) { mutableStateOf(emptySet<String>()) }
  var notice by remember(source) { mutableStateOf<FluidMediaPickerNotice?>(null) }
  val unlocker = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
    scope.launch { source.refresh() }
  }
  notice?.let { MediaNoticeAlert(it, labels, request.limit) { notice = null } }
  val access = source.access
  if (!access.allowsListing()) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
      if (source.id == FluidDeviceMediaSource.SourceId && access is FluidMediaAccess.NotAllowed) {
        FluidButton(
          text = labels.allowAccess,
          onClick = { permission.request { scope.launch { source.refresh() } } },
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.fluidPermissionSource(permission),
        )
      }
    }
    return
  }
  var items by remember(source, access) { mutableStateOf<List<FluidMediaItem>?>(null) }
  LaunchedEffect(source, access) {
    items = try {
      source.page(request.query(), null, FluidAttachmentPanelDefaults.RecentCount).items.filter { request.accepts(it) }
    } catch (_: Exception) {
      emptyList()
    }
  }
  val list = items ?: return
  val cellPx = with(LocalDensity.current) { FluidAttachmentPanelDefaults.RecentSize.roundToPx() }
  val shape = ContinuousCornerShape(FluidRadius.Control)
  val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
  val atLimit = attached.size + pending.count { it !in attached } >= maxAttachments
  LazyRow(
    Modifier.fillMaxWidth().testTag(FluidAttachmentPanelTags.Recents),
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(FluidAttachmentPanelDefaults.RecentSpacing),
  ) {
    items(list, key = { it.key }) { item ->
      val index = attached.indexOf(item.key)
      val chosen = index >= 0
      val delivering = item.key in pending
      val description = remember(item.key, locale) { pickerDescription(item, labels, locale) }
      Box(
        Modifier
          .size(FluidAttachmentPanelDefaults.RecentSize)
          .clip(shape)
          .testTag(FluidAttachmentPanelTags.recent(item.key))
          .semantics {
            contentDescription = description
            selected = chosen
          }
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = chosen || (!atLimit && !delivering),
          ) {
            if (chosen) {
              onRemove(item.key)
            } else if (item.key !in pending) {
              pending = pending + item.key
              scope.launch {
                try {
                  when (val result = source.deliver(listOf(item), request)) {
                    is FluidMediaDelivery.Ready -> {
                      val media = result.media.firstOrNull()
                      if (media != null) onAttach(media) else notice = FluidMediaPickerNotice(FluidMediaRefusal.NothingAvailable, result.skipped)
                    }
                    is FluidMediaDelivery.Refused -> notice = FluidMediaPickerNotice(result.reason, skipped = 0)
                    is FluidMediaDelivery.NeedsUnlock -> {
                      val intent = result.unlock
                      if (intent != null) {
                        runCatching { unlocker.launch(IntentSenderRequest.Builder(intent.intentSender).build()) }
                      } else {
                        source.refresh()
                      }
                    }
                  }
                } catch (e: CancellationException) {
                  throw e
                } catch (_: Exception) {
                  notice = FluidMediaPickerNotice(FluidMediaRefusal.Failed, skipped = 0)
                } finally {
                  pending = pending - item.key
                }
              }
            }
          },
      ) {
        val phase = rememberFluidMediaThumbnail(source, item, cellPx)
        FluidAsyncImage(phase, contentDescription = null, modifier = Modifier.fillMaxSize())
        if (delivering) FluidSpinner(Modifier.align(Alignment.Center), size = 20.dp)
        if (item.kind == FluidMediaKind.Video) {
          Text(
            fluidMediaDurationText(item.durationMillis ?: 0L),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
          )
        }
        if (atLimit && !chosen) {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = FluidPreviewGridDefaults.LimitVeilAlpha)))
        }
        FluidSelectionMark(
          selected = chosen,
          onPhoto = true,
          number = if (chosen) index + 1 else null,
          contentDescription = null,
          modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
        )
      }
    }
    if (onAllPhotos != null) {
      item(key = "tutte") {
        Box(
          Modifier.size(FluidAttachmentPanelDefaults.RecentSize).clip(shape)
            .clickable(onClick = onAllPhotos)
            .semantics { contentDescription = labels.allPhotos },
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FluidIcon(FluidIcons.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            Text(labels.allPhotos, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
  }
}

/** I numeri della scheda degli allegati che nasce dal «+» ([FluidAttachmentMenu]), misurati su Messaggi di iOS 26. */
object FluidAttachmentMenuDefaults {
  /**
   * La scheda: larga 320 dp e alta al piu' 456 dp, come la scheda di Messaggi di iOS 26 e 27 (≈ 320 × 456 pt, guida
   * dell'iPhone e articolo 104969, misurate il 25 novembre 2026 a 1,135 e 1,458 px per punto; fiducia alta). Il raggio
   * degli angoli (≈ 35 pt in iOS) e' quello del popover dell'engine.
   */
  val Width: Dp = 320.dp
  val MaxHeight: Dp = 456.dp

  /** Il passo delle righe: 66 dp (66 pt in iOS 17–27, fiducia alta). */
  val RowHeight: Dp = 66.dp

  /** Il cerchio dell'icona: 38 dp (38–39 pt), col centro a 53 dp dal bordo sinistro della scheda (bordo a 34 dp). */
  val IconSize: Dp = 38.dp
  val IconStart: Dp = 34.dp

  /** Il nome comincia a 100 dp dal bordo della scheda (≈ 27 pt dopo l'icona). */
  val LabelGap: Dp = 28.dp

  /** Sopra la prima riga: il centro della prima icona sta a 56 dp dal bordo alto, quindi 23 dp di margine. */
  val TopPadding: Dp = 23.dp
}

/**
 * Il pannello degli allegati come in **Messaggi di iOS 26** (F8.14): il «+» diventa una **scheda di vetro** che cresce
 * dal tasto e ci rientra (il viaggio d'oro dei pannelli che nascono da un tasto), con le fonti in righe dalle icone tonde
 * ([FluidAttachmentMenuDefaults]: 320 × 456 dp, righe da 66 dp, icone da 38 dp, le misure di iOS 26). In cima, se
 * l'app le vuole, le **foto recenti** da toccare (la nota della scheda `pannello-allegati`; Messaggi non le ha). Toccare
 * una fonte la apre e chiude la scheda; una foto recente si allega e la scheda resta, per sceglierne altre.
 *
 * Il «+» porta `Modifier.fluidTransitionSource(id, namespace, FluidCapsuleShape)` in testa ai suoi modificatori, e
 * [source] e' `FluidTransitionSource(id, namespace)`. Serve un `FluidGlassModalHost` in scena (come per ogni pannello
 * dell'engine). L'altra presentazione, al posto della tastiera, e' [FluidAttachmentPanelHost] con [FluidAttachmentPanel].
 */
@Composable
fun FluidAttachmentMenu(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  source: FluidTransitionSource?,
  recents: FluidMediaSource?,
  sources: List<FluidAttachmentSource>,
  attached: List<String>,
  onAttach: (FluidPickedMedia) -> Unit,
  onRemove: (key: String) -> Unit,
  request: FluidMediaRequest = FluidMediaRequest(),
  maxAttachments: Int = Int.MAX_VALUE,
  onAllPhotos: (() -> Unit)? = null,
  labels: FluidMediaLabels = LocalFluidMediaLabels.current,
) {
  val dismiss by rememberUpdatedState(onDismissRequest)
  FluidGlassModalPortal(
    visible = visible,
    onDismissRequest = onDismissRequest,
    presentation = FluidGlassModalPresentation.Popover,
    paneTitle = labels.panelTitle,
    source = source,
    // Anche sul telefono resta la scheda che nasce dal tasto, come in Messaggi (non un foglio).
    compactAdaptation = FluidCompactAdaptation.Popover,
    mode = FluidPresentationMode.Modal,
  ) {
    Column(
      Modifier
        .width(FluidAttachmentMenuDefaults.Width)
        .heightIn(max = FluidAttachmentMenuDefaults.MaxHeight)
        .verticalScroll(rememberScrollState())
        .padding(top = if (recents != null) 16.dp else FluidAttachmentMenuDefaults.TopPadding - 14.dp, bottom = 8.dp)
        .testTag(FluidAttachmentPanelTags.Panel),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (recents != null) {
        AttachmentRecents(recents, request, attached, onAttach, onRemove, maxAttachments, onAllPhotos, labels)
        Spacer(Modifier.height(4.dp))
      }
      Column(Modifier.testTag(FluidAttachmentPanelTags.Sources)) {
        for (item in sources) {
          AttachmentMenuRow(item) {
            dismiss()
            item.onClick()
          }
        }
      }
    }
  }
}

@Composable
private fun AttachmentMenuRow(source: FluidAttachmentSource, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .height(FluidAttachmentMenuDefaults.RowHeight)
      .clickable(enabled = source.enabled, onClick = onClick)
      .padding(start = FluidAttachmentMenuDefaults.IconStart, end = 16.dp)
      .testTag(FluidAttachmentPanelTags.source(source.id)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidToneTile(source.tone, modifier = Modifier.clip(FluidCapsuleShape), size = FluidAttachmentMenuDefaults.IconSize) {
      FluidIcon(source.icon, null)
    }
    Spacer(Modifier.width(FluidAttachmentMenuDefaults.LabelGap))
    Text(source.label, style = MaterialTheme.typography.titleLarge)
  }
}
