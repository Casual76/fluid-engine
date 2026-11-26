/*
 * engine-map (4.0, F8.26): il disegno dei segnaposto e dei grappoli. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.LocationOn
import kotlin.math.log2

/**
 * Le misure del segnaposto: il pallino di `Marker` di MapKit (iOS 17 e 26), misurato a occhio sulle schermate di Mappe
 * il 26/11/2026 (fiducia media): 30 dp col simbolo da 16, la punta di 7 dp, 46 dp quando e' scelto; il nome sotto, in
 * 11,5 sp seminero, su due righe al piu'.
 */
object FluidMapPinDefaults {
  val Balloon = 30.dp
  val SelectedBalloon = 46.dp
  val Tail = 7.dp
  val Glyph = 16.dp

  /** Meta' dell'altezza del segnaposto: la punta sta al centro, sopra il pallino, sotto il nome. */
  val Half = 56.dp
  val LabelWidth = 120.dp

  /** Il grappolo: un cerchio da 34 a 56 dp, che cresce col logaritmo del numero. */
  val ClusterMin = 34.dp
  val ClusterMax = 56.dp
}

/** Il pallino di un segnaposto solo, con la punta sul punto e il nome sotto. Toccato, lo sceglie. */
@Composable
internal fun FluidMapPin(
  marker: FluidMapMarker,
  selected: Boolean,
  labels: FluidMapLabels,
  reduced: Boolean,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val tint = if (marker.tint == Color.Unspecified) scheme.primary else marker.tint
  val scale by animateFloatAsState(
    targetValue = if (selected) FluidMapPinDefaults.SelectedBalloon / FluidMapPinDefaults.Balloon else 1f,
    animationSpec = if (reduced) snap() else FluidMotion.flick(),
    label = "fluid-map-pin",
  )
  val interaction = remember { MutableInteractionSource() }
  val description = listOfNotNull(marker.title, marker.subtitle).joinToString(", ")
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    // La meta' alta: il pallino con la punta in fondo, cioe' sul punto.
    Box(Modifier.size(FluidMapPinDefaults.LabelWidth, FluidMapPinDefaults.Half), contentAlignment = Alignment.BottomCenter) {
      Box(
        Modifier
          .size(FluidMapPinDefaults.Balloon + 14.dp, FluidMapPinDefaults.Balloon + FluidMapPinDefaults.Tail + 7.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 1f)
          }
          .testTag(FluidMapTags.marker(marker.id))
          .semantics {
            contentDescription = description
            role = Role.Button
            this.selected = selected
            if (selected) stateDescription = labels.selected
          }
          .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
      ) {
        FluidMapBalloon(marker)
      }
    }
    // La meta' bassa: il nome, col contorno del colore della pagina perche' si legga su ogni mappa.
    Box(Modifier.size(FluidMapPinDefaults.LabelWidth, FluidMapPinDefaults.Half), contentAlignment = Alignment.TopCenter) {
      BasicText(
        text = marker.title,
        modifier = Modifier.widthIn(max = FluidMapPinDefaults.LabelWidth),
        style = TextStyle(
          color = scheme.onSurface,
          fontSize = 11.5.sp,
          lineHeight = 13.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          shadow = Shadow(scheme.surface, Offset.Zero, 6f),
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Il pallino col simbolo e la punta, senza nome e senza tocco: quello del segnaposto e della cartolina. */
@Composable
internal fun FluidMapBalloon(marker: FluidMapMarker) {
  val tint = if (marker.tint == Color.Unspecified) MaterialTheme.colorScheme.primary else marker.tint
  Box(contentAlignment = Alignment.TopCenter) {
    Canvas(Modifier.size(FluidMapPinDefaults.Balloon, FluidMapPinDefaults.Balloon + FluidMapPinDefaults.Tail)) {
      val d = FluidMapPinDefaults.Balloon.toPx()
      val tail = FluidMapPinDefaults.Tail.toPx()
      val r = d / 2f
      val c = Offset(r, r)
      val path = Path().apply {
        moveTo(r - tail * 0.8f, d - tail * 0.55f)
        lineTo(r, d + tail)
        lineTo(r + tail * 0.8f, d - tail * 0.55f)
        close()
      }
      drawPath(path, tint)
      drawCircle(tint, r, c)
      drawCircle(Color.White.copy(alpha = 0.9f), r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
    }
    Box(
      Modifier.size(FluidMapPinDefaults.Balloon, FluidMapPinDefaults.Balloon + FluidMapPinDefaults.Tail),
      contentAlignment = Alignment.TopCenter,
    ) {
      Box(Modifier.size(FluidMapPinDefaults.Balloon), contentAlignment = Alignment.Center) {
        FluidIcon(marker.glyph ?: FluidIcons.LocationOn, contentDescription = null, tint = Color.White, size = FluidMapPinDefaults.Glyph)
      }
    }
  }
}

/** Il cerchio di un grappolo, col numero di cose che contiene. Toccato, avvicina. */
@Composable
internal fun FluidMapClusterBubble(cluster: FluidMapCluster, labels: FluidMapLabels, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val first = cluster.first.tint
  val tint = if (first == Color.Unspecified) scheme.primary else first
  val diameter = clusterDiameter(cluster.count)
  val interaction = remember { MutableInteractionSource() }
  Box(
    Modifier
      .size(diameter.coerceAtLeast(44.dp))
      .testTag(FluidMapTags.marker(cluster.id))
      .semantics {
        contentDescription = labels.cluster(cluster.count)
        role = Role.Button
        onClick(label = labels.clusterAction) { onClick(); true }
      }
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(diameter)) {
      drawCircle(tint, size.minDimension / 2f)
      drawCircle(Color.White, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
    BasicText(
      text = clusterCountText(cluster.count),
      style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
      maxLines = 1,
    )
  }
}

/** Il segnaposto disegnato dall'app: tasto col nome per TalkBack, tocco che sceglie o avvicina. */
internal fun Modifier.markerSemantics(
  cluster: FluidMapCluster,
  selected: Boolean,
  labels: FluidMapLabels,
  onMarkerClick: (FluidMapMarker) -> Unit,
  onClusterClick: (FluidMapCluster) -> Unit,
): Modifier {
  val click = { if (cluster.isSingle) onMarkerClick(cluster.first) else onClusterClick(cluster) }
  return this
    .testTag(FluidMapTags.marker(cluster.id))
    .semantics(mergeDescendants = true) {
      contentDescription = if (cluster.isSingle) cluster.first.title else labels.cluster(cluster.count)
      role = Role.Button
      this.selected = selected
    }
    .clickable(onClick = click)
}

/** Il diametro del cerchio di un grappolo di [count] cose. */
internal fun clusterDiameter(count: Int) =
  (FluidMapPinDefaults.ClusterMin + (6f * log2(count.coerceAtLeast(2).toFloat())).dp - 6.dp)
    .coerceIn(FluidMapPinDefaults.ClusterMin, FluidMapPinDefaults.ClusterMax)

/** Il numero del grappolo: intero fino a 999, poi «999+». */
internal fun clusterCountText(count: Int): String = if (count > 999) "999+" else count.toString()
