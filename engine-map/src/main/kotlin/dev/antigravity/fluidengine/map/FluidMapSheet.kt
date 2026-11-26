/*
 * engine-map (4.0, F8.26): il foglio a piu' altezze sopra la mappa e la scheda del luogo (Indicazioni, Condividi, Copia
 * coordinate), come Mappe di iOS 26. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.map.icons.Directions
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import kotlinx.coroutines.delay

/** I numeri del foglio sopra la mappa. */
object FluidMapSheetDefaults {
  /**
   * L'altezza del foglio basso, col campo di ricerca. Mappe di iOS 26: una capsula alta 66–67 pt a 22–23 pt dal fondo,
   * cioe' circa 89 pt dal suo bordo alto al fondo dello schermo (la guida dell'iPhone, 1,11 px/pt, 26/11/2026; fiducia
   * media, un'immagine sola). Qui 96 dp: il foglio dell'engine porta dentro la maniglia e lo spazio della barra dei gesti.
   */
  val Peek: Dp = 96.dp

  /** Le tre altezze di Mappe: basso, a meta' (la mappa resta viva fin qui) e in cima. */
  val Detents: Set<FluidSheetDetent> = setOf(FluidSheetDetent.Height(Peek), FluidSheetDetent.Medium, FluidSheetDetent.Large)

  /** Quanto tempo resta «Coordinate copiate» al posto delle coordinate. A occhio, 26/11/2026. */
  const val CopiedMillis: Long = 1_600
}

/**
 * Il foglio sopra la mappa, come quello di Mappe in iOS 26: sempre aperto, con tre altezze ([FluidMapSheetDefaults.Detents]:
 * basso, a meta', in cima), di vetro finche' non e' in cima, e **la mappa resta viva** fino a meta' (si trascina, si tocca:
 * `undimmedUpTo = Medium`). Trascinato giu' sotto il detent piu' basso torna su: questo foglio non si chiude.
 *
 * E' [FluidGlassSheet] dell'engine (servono l'host dei modali alla radice), che in piu' dice alla mappa quanto la copre
 * ([FluidMapState.bottomObscured]): l'attribuzione resta sopra il foglio, e i comandi centrano i punti nella parte che si
 * vede.
 *
 * @param mapState lo stato della mappa sotto.
 * @param selectedDetent il detent da tenere, se l'app lo vuole decidere (un luogo scelto porta il foglio a meta').
 */
@Composable
fun FluidMapSheet(
  mapState: FluidMapState,
  detents: Set<FluidSheetDetent> = FluidMapSheetDefaults.Detents,
  selectedDetent: FluidSheetDetent? = null,
  onSelectedDetentChange: (FluidSheetDetent) -> Unit = {},
  paneTitle: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val ordered = remember(detents) { detents.toList() }
  var rested by remember { mutableStateOf(selectedDetent ?: lowestOf(ordered)) }
  val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
  val obscured = obscuredBy(rested, windowHeight)
  SideEffect { mapState.bottomObscured = obscured }
  LaunchedEffect(selectedDetent) { selectedDetent?.let { rested = it } }
  FluidGlassSheet(
    visible = true,
    // Il foglio della mappa non si chiude: il foglio di vetro dell'engine torna al detent piu' basso.
    onDismissRequest = {},
    detents = detents,
    selectedDetent = selectedDetent,
    onSelectedDetentChange = {
      rested = it
      onSelectedDetentChange(it)
    },
    paneTitle = paneTitle,
    undimmedUpTo = FluidSheetDetent.Medium,
    content = content,
  )
}

/**
 * L'host dei modali **sopra la mappa**: il foglio ([FluidMapSheet]) e i pannelli dei comandi (livelli, crediti) si
 * disegnano qui e rifrangono [backdrop], la registrazione della mappa ([FluidMap] con lo stesso `backdrop`), invece della
 * pagina sotto. Un'app che ha gia' il suo host alla radice lo mette attorno alla pagina della mappa.
 *
 * ```kotlin
 * val backdrop = rememberGlassBackdrop()
 * FluidMapGlassHost(backdrop) {
 *   FluidMap(state, backdrop = backdrop, markers = luoghi)
 *   FluidMapSheet(state) { FluidMapPlaceCard(luogo) }
 * }
 * ```
 */
@Composable
fun FluidMapGlassHost(backdrop: GlassBackdropState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  val host = rememberFluidGlassModalHostState()
  CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
    Box(modifier.fillMaxSize()) {
      content()
      FluidGlassModalHost(state = host, backdrop = backdrop)
    }
  }
}

/** Il detent piu' basso di un elenco, per altezza (per il primo fotogramma, prima che il foglio dica dove sta). */
private fun lowestOf(detents: List<FluidSheetDetent>): FluidSheetDetent =
  detents.minByOrNull { d ->
    when (d) {
      is FluidSheetDetent.Height -> d.height.value / 10_000f
      is FluidSheetDetent.Fraction -> d.fraction
      FluidSheetDetent.Medium -> 0.52f
      FluidSheetDetent.Large -> 1f
    }
  } ?: FluidSheetDetent.Large

/** Quanto della finestra copre il foglio fermo a [detent] (le misure del foglio dell'engine: 52% a meta'). */
internal fun obscuredBy(detent: FluidSheetDetent, windowHeight: Dp): Dp = when (detent) {
  is FluidSheetDetent.Height -> detent.height
  is FluidSheetDetent.Fraction -> windowHeight * detent.fraction
  FluidSheetDetent.Medium -> windowHeight * 0.52f
  FluidSheetDetent.Large -> windowHeight
}

/** Un luogo per la scheda: il nome, il punto, una riga sotto (l'indirizzo, «214 foto») e un dettaglio facoltativo. */
@Immutable
data class FluidMapPlace(
  val title: String,
  val point: FluidGeoPoint,
  val subtitle: String? = null,
  val detail: String? = null,
)

/** I tag delle prove della scheda. */
object FluidMapPlaceTags {
  const val Card: String = "fluid-map:luogo"
  const val Directions: String = "fluid-map:luogo:indicazioni"
  const val Share: String = "fluid-map:luogo:condividi"
  const val Copy: String = "fluid-map:luogo:copia"
  const val Coordinates: String = "fluid-map:luogo:coordinate"
}

/**
 * La scheda del luogo, da mettere nel [FluidMapSheet]: il nome, la riga sotto, i tre tasti di Mappe e le coordinate.
 *
 * - **Indicazioni** (il tasto pieno, come Mappe): apre l'app di mappe del telefono sul punto (`geo:`), se c'e'.
 * - **Condividi**: il nome e il collegamento a OpenStreetMap, col foglio di Android.
 * - **Copia** (per TalkBack «Copia coordinate»): «43.77310, 11.25560» negli appunti, e «Coordinate copiate» per un momento al posto delle
 *   coordinate (detto anche da TalkBack).
 *
 * Ogni tasto si puo' sostituire (l'app ha il suo navigatore o il suo foglio di condivisione).
 */
@Composable
fun FluidMapPlaceCard(
  place: FluidMapPlace,
  modifier: Modifier = Modifier,
  labels: FluidMapLabels = FluidMapLabels(),
  onDirections: (() -> Unit)? = null,
  onShare: (() -> Unit)? = null,
  onCopy: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  var copied by remember(place) { mutableStateOf(false) }
  LaunchedEffect(copied) {
    if (copied) {
      delay(FluidMapSheetDefaults.CopiedMillis)
      copied = false
    }
  }
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(FluidMapPlaceTags.Card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(place.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    place.subtitle?.let {
      Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FluidButton(
        text = labels.directions,
        onClick = { onDirections?.invoke() ?: FluidMapActions.directions(context, place) },
        modifier = Modifier.weight(1f).testTag(FluidMapPlaceTags.Directions),
        style = FluidButtonStyle.Filled,
        fillWidth = true,
        leading = { FluidIcon(FluidIcons.Directions, contentDescription = null) },
      )
      FluidButton(
        text = labels.share,
        onClick = { onShare?.invoke() ?: FluidMapActions.share(context, place, labels) },
        modifier = Modifier.weight(1f).testTag(FluidMapPlaceTags.Share),
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
        leading = { FluidIcon(FluidIcons.IosShare, contentDescription = null) },
      )
      FluidButton(
        text = labels.copyShort,
        onClick = {
          if (onCopy != null) onCopy() else FluidMapActions.copyCoordinates(context, place)
          copied = true
        },
        modifier = Modifier
          .weight(1f)
          .testTag(FluidMapPlaceTags.Copy)
          .semantics { contentDescription = labels.copyCoordinates },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
        leading = { FluidIcon(FluidIcons.ContentCopy, contentDescription = null) },
      )
    }
    Text(
      if (copied) labels.copied else FluidGeoFormat.display(place.point),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(top = 10.dp)
        .testTag(FluidMapPlaceTags.Coordinates)
        .semantics { liveRegion = LiveRegionMode.Polite },
    )
    place.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
  }
}

/** Le azioni di serie della scheda del luogo, anche da usare a mano. */
object FluidMapActions {
  /** Apre l'app di mappe del telefono sul punto (`geo:` con il nome). Falso se non ce n'e' nessuna. */
  fun directions(context: Context, place: FluidMapPlace): Boolean = try {
    context.startActivity(
      Intent(Intent.ACTION_VIEW, Uri.parse(FluidGeoFormat.geoUri(place.point, place.title))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
  } catch (_: ActivityNotFoundException) {
    false
  }

  /** Il nome e il collegamento a OpenStreetMap del luogo, col foglio di condivisione di Android. */
  fun share(context: Context, place: FluidMapPlace, labels: FluidMapLabels = FluidMapLabels()) {
    val text = "${place.title}\n${FluidGeoFormat.openStreetMapUrl(place.point)}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, place.title)
    context.startActivity(Intent.createChooser(send, labels.share).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  /** Copia le coordinate («43.77310, 11.25560») negli appunti. */
  fun copyCoordinates(context: Context, place: FluidMapPlace) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(place.title, FluidGeoFormat.copy(place.point)))
  }
}
