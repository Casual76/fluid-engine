/*
 * engine-map (4.0, F8.26): i comandi di vetro sopra la mappa (livelli, posizione, bussola) e l'attribuzione. Codice del
 * Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.map.icons.NearMe
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** La colonna dei comandi: la capsula coi livelli e la posizione, e sotto la bussola. */
@Composable
internal fun FluidMapControlsColumn(
  state: FluidMapState,
  controls: FluidMapControls,
  styles: List<FluidMapStyle>,
  style: FluidMapStyle,
  backdrop: GlassBackdropState,
  labels: FluidMapLabels,
  onLocationRequest: (() -> Unit)?,
  compassFirst: Boolean,
  modifier: Modifier,
) {
  val scope = rememberCoroutineScope()
  val showLocation = controls.location && (state.userLocation != null || onLocationRequest != null)
  val showLayers = controls.layers && styles.size > 1
  Column(modifier.testTag(FluidMapTags.Controls), horizontalAlignment = Alignment.End) {
    // Sopra il foglio (Mappe di iOS 26) la bussola sta sopra la capsula; in alto, sotto.
    if (controls.compass && compassFirst) {
      FluidMapCompass(state, backdrop, labels)
      Spacer(Modifier.height(FluidMapControlsDefaults.Gap))
    }
    if (showLayers || showLocation) {
      // Una capsula sola coi due tasti, come Mappe di iOS 26: il vetro e' uno, i tasti ci stanno dentro.
      Column(
        Modifier
          .width(FluidMapControlsDefaults.Button)
          .fluidGlass(FluidGlass.Regular, FluidCapsuleShape, backdrop),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (showLayers) FluidMapLayersButton(state, styles, style, labels)
        if (showLayers && showLocation) {
          Box(
            Modifier
              .width(FluidMapControlsDefaults.Button * 0.55f)
              .height(0.75.dp)
              .graphicsLayer { alpha = 0.22f }
              .clip(FluidCapsuleShape)
              .background(MaterialTheme.colorScheme.onSurface),
          )
        }
        if (showLocation) {
          val following = state.tracking == FluidMapTracking.Follow
          FluidCapsuleAction(
            icon = if (following) FluidIcons.Filled.NearMe else FluidIcons.NearMe,
            // Come il tasto di Mappe: «Posizione» anche prima che l'app la sappia (il tocco la chiede). Fino alla F8.26b
            // diceva «Posizione non disponibile», che e' falso: il tasto e' li' proprio per averla.
            contentDescription = if (following) labels.locationFollowing else labels.location,
            onClick = {
              if (state.userLocation == null) onLocationRequest?.invoke()
              else if (following) state.tracking = FluidMapTracking.Off
              else scope.launch { state.centerOnUser() }
            },
            modifier = Modifier.testTag(FluidMapTags.Location),
            size = FluidMapControlsDefaults.Button,
            selected = following,
          )
        }
      }
    }
    if (controls.compass && !compassFirst) {
      Spacer(Modifier.height(FluidMapControlsDefaults.Gap))
      FluidMapCompass(state, backdrop, labels)
    }
  }
}


/**
 * La bussola (`MapCompass`): compare quando il nord non e' in alto, gira con la mappa, e toccata riporta il nord su. Il
 * nord e' la punta rossa con la «N». La rotazione si legge nel disegno: la mappa che gira non la ricompone.
 */
@Composable
private fun FluidMapCompass(state: FluidMapState, backdrop: GlassBackdropState, labels: FluidMapLabels) {
  val scope = rememberCoroutineScope()
  val rotated by remember(state) { derivedStateOf { state.camera.isRotated } }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val shown by animateFloatAsState(
    targetValue = if (rotated) 1f else 0f,
    animationSpec = if (reduced) snap() else androidx.compose.animation.core.tween(180),
    label = "fluid-map-compass",
  )
  if (!rotated && shown == 0f) return
  val north = MaterialTheme.colorScheme.error
  val south = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
  val ink = MaterialTheme.colorScheme.onSurface
  FluidGlassIconButton(
    onClick = { scope.launch { state.resetNorth() } },
    modifier = Modifier
      .graphicsLayer {
        alpha = shown
        scaleX = 0.8f + 0.2f * shown
        scaleY = 0.8f + 0.2f * shown
      }
      .testTag(FluidMapTags.Compass)
      // I gradi si leggono nella semantica, non nella composizione: la mappa che gira non ricompone la bussola.
      .semantics { contentDescription = labels.compass(state.camera.normalizedBearing.roundToInt() % 360) },
    backdrop = backdrop,
    enabled = rotated,
  ) {
    Canvas(Modifier.size(30.dp)) {
      val bearing = state.camera.bearing.toFloat()
      rotate(-bearing) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val len = size.minDimension * 0.30f
        val wid = size.minDimension * 0.11f
        val up = Path().apply {
          moveTo(c.x, c.y - len); lineTo(c.x + wid, c.y); lineTo(c.x - wid, c.y); close()
        }
        val down = Path().apply {
          moveTo(c.x, c.y + len); lineTo(c.x + wid, c.y); lineTo(c.x - wid, c.y); close()
        }
        drawPath(up, north)
        drawPath(down, south)
        // La «N» in cima, piccola, dentro il vetro: due tratti e la diagonale.
        val h = size.minDimension * 0.13f
        val w = h * 0.75f
        val top = c.y - len - h * 1.55f
        val stroke = 1.6.dp.toPx()
        drawLine(ink, Offset(c.x - w / 2, top + h), Offset(c.x - w / 2, top), stroke)
        drawLine(ink, Offset(c.x - w / 2, top), Offset(c.x + w / 2, top + h), stroke)
        drawLine(ink, Offset(c.x + w / 2, top + h), Offset(c.x + w / 2, top), stroke)
      }
    }
  }
}

/** Il tasto dei livelli e il suo pannello di vetro, che nasce dal tasto: gli stili, col campione e la spunta. */
@Composable
private fun FluidMapLayersButton(state: FluidMapState, styles: List<FluidMapStyle>, style: FluidMapStyle, labels: FluidMapLabels) {
  var open by remember { mutableStateOf(false) }
  var origin by remember { mutableStateOf<Rect?>(null) }
  val dark = MaterialTheme.colorScheme.surface.let { (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f }
  FluidCapsuleAction(
    icon = FluidIcons.Map,
    contentDescription = "${labels.layers}, ${style.label}",
    onClick = { open = true },
    modifier = Modifier
      .onGloballyPositioned { origin = it.boundsInRoot() }
      .testTag(FluidMapTags.Layers),
    size = FluidMapControlsDefaults.Button,
  )
  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = { open = false },
    origin = { origin },
    presentation = FluidGlassModalPresentation.Popover,
    paneTitle = labels.layersTitle,
  ) {
    Column(Modifier.widthIn(min = 220.dp).padding(vertical = 6.dp)) {
      Text(
        labels.layersTitle,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )
      for (s in styles) {
        val chosen = s.id == style.id
        Row(
          Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(FluidMapTags.style(s.id))
            .semantics {
              role = Role.RadioButton
              selected = chosen
            }
            .clickable {
              state.styleId = s.id
              open = false
            }
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Canvas(Modifier.size(28.dp).clip(ContinuousCornerShape(FluidRadius.Small))) {
            drawRect(if (dark) s.darkSwatch else s.swatch)
            drawLine(if (dark) Color(0x55FFFFFF) else Color(0x33000000), Offset(0f, size.height * 0.7f), Offset(size.width, size.height * 0.3f), 2.dp.toPx())
          }
          Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          if (chosen) FluidIcon(FluidIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}

/**
 * L'attribuzione: la scritta corta sempre in vista sulla mappa, su un vetro piccolo, che toccata apre i crediti con i
 * collegamenti (ODbL: «© OpenStreetMap» e openstreetmap.org/copyright a un tocco). Il bersaglio del dito e' di 44 dp
 * anche se il vetro e' basso. Su uno schermo stretto la scritta va a capo (al piu' due righe) invece di finire sotto i
 * comandi; se nemmeno in due righe ci sta (il carattere grande di sistema, R3) resta quella compatta
 * ([FluidMapAttribution.compact], «© OpenStreetMap»), come sulla cartolina piccola.
 *
 * TalkBack la legge una volta: «Crediti della mappa: OpenFreeMap © OpenMapTiles © OpenStreetMap», un tasto. Fino alla
 * F8.26b la scritta sul vetro si univa alla descrizione e i crediti si sentivano due volte.
 */
@Composable
internal fun FluidMapAttributionChip(
  attribution: FluidMapAttribution,
  backdrop: GlassBackdropState,
  labels: FluidMapLabels,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  var origin by remember { mutableStateOf<Rect?>(null) }
  val textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  BoxWithConstraints(
    modifier
      .heightIn(min = 44.dp)
      .onGloballyPositioned { origin = it.boundsInRoot() }
      .testTag(FluidMapTags.Attribution)
      // La descrizione e' tutta qui: la scritta sul vetro non si aggiunge (TalkBack la diceva due volte).
      .clearAndSetSemantics {
        contentDescription = "${labels.credits}: ${attribution.short}"
        role = Role.Button
        onClick { open = true; true }
      }
      .clickable { open = true },
    contentAlignment = Alignment.BottomStart,
  ) {
    val textWidth = if (constraints.hasBoundedWidth) constraints.maxWidth - with(density) { (AttributionPadding * 2).roundToPx() } else 0
    val text = remember(attribution, textWidth, textStyle, density) {
      fluidMapAttributionText(attribution, textWidth, textStyle, measurer, density)
    }
    Box(Modifier.fluidGlass(FluidGlass.Regular, FluidCapsuleShape, backdrop).padding(horizontal = AttributionPadding, vertical = 3.dp)) {
      BasicText(text, style = textStyle, maxLines = AttributionMaxLines)
    }
  }
  FluidMapCreditsPopover(open, onDismiss = { open = false }, origin = { origin }, attribution = attribution, labels = labels)
}

private val AttributionPadding = 8.dp
private const val AttributionMaxLines = 2

/**
 * La scritta dei crediti sulla mappa larga [maxWidthPx]: quella corta se sta in due righe, se no quella compatta. Col
 * carattere di sistema a 2× su un telefono da 360 dp la corta ne vuole tre, e la terza, quella di «© OpenStreetMap», si
 * tagliava (R3). Con una larghezza che non si sa ancora resta la corta.
 */
internal fun fluidMapAttributionText(
  attribution: FluidMapAttribution,
  maxWidthPx: Int,
  style: TextStyle,
  measurer: TextMeasurer,
  density: Density,
): String {
  if (maxWidthPx <= 0) return attribution.short
  val layout = measurer.measure(
    attribution.short, style, maxLines = AttributionMaxLines, constraints = Constraints(maxWidth = maxWidthPx), density = density,
  )
  return if (layout.didOverflowHeight) attribution.compact else attribution.short
}

/**
 * I crediti interi, coi collegamenti, nel popover che nasce da chi li apre: il vetro dei crediti sulla mappa e i crediti
 * della cartolina. Senza host dei modali non compare.
 */
@Composable
internal fun FluidMapCreditsPopover(
  open: Boolean,
  onDismiss: () -> Unit,
  origin: () -> Rect?,
  attribution: FluidMapAttribution,
  labels: FluidMapLabels,
) {
  val uri = LocalUriHandler.current
  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = onDismiss,
    origin = origin,
    presentation = FluidGlassModalPresentation.Popover,
    paneTitle = labels.credits,
  ) {
    Column(Modifier.widthIn(min = 240.dp, max = 320.dp).padding(vertical = 6.dp)) {
      Text(labels.credits, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      for (credit in attribution.credits) {
        Box(
          Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { runCatching { uri.openUri(credit.url) } }
            .padding(horizontal = 16.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          Text(credit.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}
