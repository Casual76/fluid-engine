/*
 * engine-map (4.0, F8.26): la mappa, come `Map` di MapKit per SwiftUI e Mappe di iOS 26. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** I tag delle prove. */
object FluidMapTags {
  const val Map: String = "fluid-map"
  const val Controls: String = "fluid-map:comandi"
  const val Location: String = "fluid-map:posizione"
  const val Compass: String = "fluid-map:bussola"
  const val Layers: String = "fluid-map:livelli"
  const val Attribution: String = "fluid-map:attribuzione"
  const val UserDot: String = "fluid-map:tu"
  const val Postcard: String = "fluid-map:cartolina"
  fun marker(id: String): String = "fluid-map:segnaposto:$id"
  fun style(id: String): String = "fluid-map:stile:$id"
}

/**
 * Dove stanno i comandi di vetro.
 *
 * - [AboveSheet]: in basso a destra, **appoggiati sopra il foglio** e con lui quando si posa a un'altra altezza, la bussola
 *   sopra la capsula. E' Mappe di iOS 26 (la guida dell'iPhone e Newsroom, 26/11/2026).
 * - [TopEnd]: in alto a destra, sotto le barre: la mappa di «Luoghi» in Foto di iOS 26, e Mappe fino a iOS 18.
 */
enum class FluidMapControlsPlacement { AboveSheet, TopEnd }

/** Quali comandi di vetro mostra la mappa, e dove. Tutti di serie, sopra il foglio, come Mappe di iOS 26. */
@Immutable
data class FluidMapControls(
  val location: Boolean = true,
  val compass: Boolean = true,
  val layers: Boolean = true,
  val placement: FluidMapControlsPlacement = FluidMapControlsPlacement.AboveSheet,
) {
  companion object {
    val All: FluidMapControls = FluidMapControls()
    val None: FluidMapControls = FluidMapControls(location = false, compass = false, layers = false)
  }
}

/**
 * La mappa: `Map` di MapKit per SwiftUI con i comandi di Mappe di iOS 26.
 *
 * - **I comandi di vetro** (`MapUserLocationButton`, `MapCompass`, il tasto dei livelli di Mappe): una capsula col tasto
 *   dei livelli e quello della posizione, e la bussola, che compare solo quando il nord non e' in alto e toccata lo
 *   riporta su. Di serie stanno **in basso a destra, appoggiati sopra il foglio**, con la bussola sopra la capsula, come
 *   Mappe di iOS 26; con [FluidMapControlsPlacement.TopEnd] in alto a destra, come la mappa di «Luoghi» in Foto. La posizione la da' l'app ([FluidMapState.userLocation]); senza, il tasto chiama
 *   [onLocationRequest] (l'app chiede il permesso). Toccato, porta la mappa sulla posizione e la fa seguire; il dito che
 *   sposta la mappa smette di seguirla.
 * - **I segnaposto a grappolo** (`Marker` e `MKClusterAnnotation`): quelli vicini sullo schermo diventano un cerchio col
 *   numero, che toccato avvicina fino a dividerli. Quando lo zoom cambia livello i segnaposto escono dal grappolo da cui
 *   nascono (e ci rientrano), in [FluidMapMotion.ClusterMillis]. Le posizioni seguono la camera nel disegno: la mappa che
 *   scorre non ricompone niente.
 * - **L'attribuzione**, sempre in vista in basso a sinistra (sopra il foglio: [FluidMapState.bottomObscured]), che
 *   toccata mostra i crediti con i collegamenti.
 *
 * **Il vetro.** I comandi rifrangono la mappa quando il motore la disegna dentro Compose
 * ([FluidMapEngine.glassSamplesMap]: MapLibre in `TextureView`, il disegno). Il foglio sopra la mappa e' [FluidMapSheet].
 * Niente di cio' che muove il dito si sfoca («dito nitido»): ne' la mappa ne' i segnaposto.
 *
 * **TalkBack.** La mappa dice quanti luoghi ha in vista e ha le azioni «Ingrandisci», «Riduci» e «Mostra tutti i
 * luoghi»; ogni segnaposto e' un tasto col suo nome. **Riduci movimento**: i voli diventano salti e i grappoli cambiano
 * senza viaggio.
 *
 * @param markers i segnaposto. I grappoli si calcolano fuori dal thread principale quando l'elenco cambia.
 * @param selectedMarkerId il segnaposto scelto (si ingrandisce, come in Mappe); null nessuno.
 * @param onMarkerClick il tocco su un segnaposto solo.
 * @param onClusterClick il tocco su un grappolo; null (di serie) avvicina fino a dividerlo.
 * @param onMapClick il tocco sulla mappa fuori dai segnaposto (per togliere la scelta).
 * @param onLocationRequest il tasto della posizione quando la posizione non c'e'; null: il tasto non c'e' finche' l'app
 *   non da' una posizione.
 * @param styles gli stili del pannello dei livelli; quello scelto e' [FluidMapState.styleId].
 * @param contentPadding lo spazio delle barre attorno (i comandi stanno dentro).
 * @param backdrop la registrazione della mappa che il vetro rifrange: la stessa da dare all'host dei modali sopra la mappa
 *   ([FluidMapGlassHost]), perche' il foglio e i pannelli rifrangano la mappa e non la pagina sotto.
 * @param markerContent un segnaposto disegnato dall'app al posto del pallino (una miniatura, in «Luoghi» di Foto):
 *   riceve il grappolo e se e' scelto; il suo centro sta sul punto.
 */
@Composable
fun FluidMap(
  state: FluidMapState,
  modifier: Modifier = Modifier,
  markers: List<FluidMapMarker> = emptyList(),
  selectedMarkerId: String? = null,
  onMarkerClick: (FluidMapMarker) -> Unit = {},
  onClusterClick: ((FluidMapCluster) -> Unit)? = null,
  onMapClick: ((FluidGeoPoint) -> Unit)? = null,
  onLocationRequest: (() -> Unit)? = null,
  styles: List<FluidMapStyle> = FluidMapStyles.OpenFreeMap,
  engine: FluidMapEngine = rememberFluidMapEngine(),
  controls: FluidMapControls = FluidMapControls.All,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  labels: FluidMapLabels = FluidMapLabels(),
  backdrop: GlassBackdropState = rememberGlassBackdrop(),
  markerContent: (@Composable (cluster: FluidMapCluster, selected: Boolean) -> Unit)? = null,
) {
  val density = LocalDensity.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  SideEffect { state.reducedMotion = reduced }
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val style = styles.firstOrNull { it.id == state.styleId } ?: styles.firstOrNull() ?: FluidMapStyles.Explore
  val scope = rememberCoroutineScope()

  // I grappoli: fuori dal thread principale, scritti qui.
  var index by remember { mutableStateOf(FluidMapClusterIndex.Empty) }
  LaunchedEffect(markers) {
    index = if (markers.isEmpty()) FluidMapClusterIndex.Empty else withContext(Dispatchers.Default) { FluidMapClusterIndex.build(markers) }
  }

  val currentMapClick by rememberUpdatedState(onMapClick)
  DisposableEffect(state) {
    state.onTap = { point -> currentMapClick?.invoke(point) }
    // Il vetro adattivo sopra rilegge lo sfondo quando la mappa vera ridisegna (le tessere che arrivano a camera ferma).
    state.onRendered = { backdrop.notifyChanged() }
    onDispose {
      state.onTap = null
      state.onRendered = null
    }
  }
  // La mappa che segue la posizione: ogni posizione **nuova** la riporta li', senza toccare lo zoom. Solo la posizione:
  // l'accensione del tasto vola da se' (centerOnUser), e un secondo volo qui lo fermerebbe a meta' strada.
  LaunchedEffect(state) {
    snapshotFlow { state.userLocation }.collect { here ->
      if (here != null && state.tracking == FluidMapTracking.Follow && !state.isGestureActive) {
        state.animateTo(state.shiftedForSheet(state.camera.copy(center = here.point)))
      }
    }
  }

  // La finestra dei grappoli cambia di rado (il livello, o mezzo schermo di strada): il numero letto da TalkBack si conta
  // solo quando cambia lei, non a ogni fotogramma della mappa che scorre.
  val window by remember { derivedStateOf { clusterWindow(state, index) } }
  val visibleCount = remember(window, index) { Snapshot.withoutReadObservation { visibleClusterCount(state, index) } }
  val currentMarkers by rememberUpdatedState(markers)
  val mapActions = remember(labels, markers.isEmpty()) {
    buildList {
      add(CustomAccessibilityAction(labels.zoomIn) { scope.launch { state.zoomBy(1.0) }; true })
      add(CustomAccessibilityAction(labels.zoomOut) { scope.launch { state.zoomBy(-1.0) }; true })
      if (markers.isNotEmpty()) add(CustomAccessibilityAction(labels.showAll) { scope.launch { state.show(currentMarkers.map { it.point }) }; true })
    }
  }

  Box(
    modifier
      .clipToBounds()
      .onSizeChanged {
        state.density = density.density
        state.sizePx = it
      }
      .testTag(FluidMapTags.Map),
  ) {
    // Una mappa che il vetro non vede (MapLibre in `SurfaceView`) sta **fuori** dalla registrazione del vetro: la sorgente,
    // quando qualcuno la campiona, si compone in un layer fuori schermo, e il buco che la `SurfaceView` apre nella finestra
    // restava chiuso li' dentro. La finestra sopra la mappa era piena e la mappa, disegnata sotto, non si vedeva: nera sul
    // Tab S9 (F8.26, 26/11/2026). Dentro la sorgente restano solo la mappa che il vetro rifrange e TalkBack.
    val glassSees = engine.glassSamplesMap
    if (!glassSees) engine.Surface(state, style, dark, Modifier.matchParentSize())
    Box(
      Modifier
        .matchParentSize()
        .glassBackdropSource(backdrop)
        .semantics {
          contentDescription = labels.visiblePlaces(visibleCount)
          customActions = mapActions
        },
    ) {
      if (glassSees) engine.Surface(state, style, dark, Modifier.fillMaxSize())
    }
    FluidMapUserDot(state, labels)
    FluidMapMarkerLayer(
      state = state,
      index = index,
      window = window,
      selectedMarkerId = selectedMarkerId,
      reduced = reduced,
      labels = labels,
      onMarkerClick = onMarkerClick,
      onClusterClick = { cluster ->
        if (onClusterClick != null) onClusterClick(cluster)
        else scope.launch {
          val zoom = index.expansionZoom(cluster).toDouble() + 0.5
          state.animateTo(state.camera.copy(center = cluster.point, zoom = zoom.coerceAtLeast(state.camera.zoom + 1.0)))
        }
      },
      markerContent = markerContent,
    )
    val layoutDirection = LocalLayoutDirection.current
    // Il foglio sopra la mappa: i comandi (in Mappe di iOS 26) e l'attribuzione ci stanno appoggiati sopra, e lo seguono
    // quando si posa a un'altra altezza (con la molla dei comandi; col movimento ridotto subito).
    val lift = animateDpAsState(
      targetValue = state.bottomObscured,
      animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "fluid-map-lift",
    )
    val aboveSheet = controls.placement == FluidMapControlsPlacement.AboveSheet
    FluidMapControlsColumn(
      state = state,
      controls = controls,
      styles = styles,
      style = style,
      backdrop = backdrop,
      labels = labels,
      onLocationRequest = onLocationRequest,
      compassFirst = aboveSheet,
      modifier = if (aboveSheet) {
        Modifier
          .align(Alignment.BottomEnd)
          .padding(bottom = contentPadding.calculateBottomPadding(), end = contentPadding.calculateEndPadding(layoutDirection))
          .padding(end = FluidMapControlsDefaults.EdgeMargin, bottom = FluidMapControlsDefaults.SheetGap)
          .offset { IntOffset(0, -lift.value.roundToPx()) }
          // Col foglio in cima i comandi non servono (il foglio copre la mappa): spariscono, e non si toccano.
          .graphicsLayer { alpha = if (lift.value.toPx() > state.sizePx.height * 0.6f) 0f else 1f }
      } else {
        Modifier
          .align(Alignment.TopEnd)
          .padding(top = contentPadding.calculateTopPadding(), end = contentPadding.calculateEndPadding(layoutDirection))
          .padding(top = FluidMapControlsDefaults.TopMargin, end = FluidMapControlsDefaults.EdgeMargin)
      },
    )
    FluidMapAttributionChip(
      attribution = style.attribution,
      backdrop = backdrop,
      labels = labels,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(
          start = contentPadding.calculateStartPadding(layoutDirection),
          bottom = contentPadding.calculateBottomPadding(),
        )
        .padding(start = 8.dp, bottom = 4.dp)
        // Accanto ai comandi in basso, mai sotto: su un telefono stretto la scritta va a capo prima della capsula.
        .padding(end = if (aboveSheet) FluidMapControlsDefaults.EdgeMargin + FluidMapControlsDefaults.Button + 8.dp else 8.dp)
        .offset { IntOffset(0, -lift.value.roundToPx()) },
    )
  }
}

/** Quanti grappoli si vedono adesso (per TalkBack): letto in un `derivedStateOf`, cambia solo col numero. */
private fun visibleClusterCount(state: FluidMapState, index: FluidMapClusterIndex): Int {
  val projector = state.projector() ?: return 0
  if (index.size == 0) return 0
  val level = clusterLevel(state.camera.zoom, index.minZoom, index.maxZoom)
  val w = projector.widthPx
  val h = projector.heightPx
  return index.around(level, projector.centerX, projector.centerY, projector.screenRadiusNormalized()).count { c ->
    val p = projector.toScreenNormalized(c.x, c.y)
    p.x in 0f..w && p.y in 0f..h
  }
}

/** La finestra dei grappoli da comporre: il livello e la cella del centro, che cambiano di rado mentre la mappa scorre. */
@Immutable
private data class ClusterWindow(val level: Int, val cellX: Long, val cellY: Long, val radius: Double) {
  val centerX: Double get() = (cellX + 0.5) * radius
  val centerY: Double get() = (cellY + 0.5) * radius
}

private fun clusterWindow(state: FluidMapState, index: FluidMapClusterIndex): ClusterWindow? {
  val size = state.sizePx
  if (size.width <= 0 || size.height <= 0) return null
  val level = clusterLevel(state.camera.zoom, index.minZoom, index.maxZoom)
  // Il raggio dello schermo al livello intero (il piu' grande dentro il livello): fisso finche' il livello non cambia.
  val half = sqrt(size.width * size.width / 4.0 + size.height * size.height / 4.0)
  val radius = half / (FluidMapProjection.TileSize * 2.0.pow(level) * state.density)
  val cx = FluidMapProjection.x(state.camera.center.longitude)
  val cy = FluidMapProjection.y(state.camera.center.latitude)
  return ClusterWindow(level, floor(cx / radius).toLong(), floor(cy / radius).toLong(), radius)
}

/** Un grappolo da disporre: dove parte e dove arriva (coordinate normalizzate), e se esce. */
private class PlacedCluster(val cluster: FluidMapCluster, val fromX: Double, val fromY: Double, val exiting: Boolean)

@Composable
private fun FluidMapMarkerLayer(
  state: FluidMapState,
  index: FluidMapClusterIndex,
  window: ClusterWindow?,
  selectedMarkerId: String?,
  reduced: Boolean,
  labels: FluidMapLabels,
  onMarkerClick: (FluidMapMarker) -> Unit,
  onClusterClick: (FluidMapCluster) -> Unit,
  markerContent: (@Composable (FluidMapCluster, Boolean) -> Unit)?,
) {
  if (index.size == 0) return
  val current = window ?: return
  // Il cambio di livello: i grappoli nuovi partono dal grappolo da cui nascono (avvicinando) e i vecchi rientrano in
  // quello che li contiene (allontanando).
  var shownLevel by remember(index) { mutableIntStateOf(current.level) }
  var fromLevel by remember(index) { mutableIntStateOf(current.level) }
  // Il viaggio si legge nella disposizione; la composizione sa solo se c'e' (due volte per cambio di livello).
  var transitioning by remember(index) { mutableStateOf(false) }
  val progress = remember(index) { Animatable(1f) }
  LaunchedEffect(index, current.level) {
    if (current.level == shownLevel) return@LaunchedEffect
    fromLevel = shownLevel
    shownLevel = current.level
    if (reduced) {
      progress.snapTo(1f)
      transitioning = false
    } else {
      progress.snapTo(0f)
      transitioning = true
      try {
        progress.animateTo(1f, tween(FluidMapMotion.ClusterMillis))
      } finally {
        transitioning = false
      }
    }
  }
  val r = current.radius * 2.2
  val visible = remember(index, current, shownLevel) { index.around(shownLevel, current.centerX, current.centerY, r) }
  val placed: List<PlacedCluster> = remember(index, visible, fromLevel, shownLevel, transitioning) {
    buildList {
      if (transitioning && shownLevel > fromLevel) {
        // Avvicinando: ognuno nasce dal suo antenato.
        for (c in visible) {
          val a = index.ancestor(c.id, shownLevel, fromLevel)
          add(PlacedCluster(c, a?.x ?: c.x, a?.y ?: c.y, exiting = false))
        }
      } else if (transitioning) {
        // Allontanando: i vecchi rientrano nell'antenato nuovo, i nuovi compaiono sul posto.
        for (old in index.around(fromLevel, current.centerX, current.centerY, r)) {
          if (index.cluster(shownLevel, old.id) != null) continue
          val a = index.ancestor(old.id, fromLevel, shownLevel) ?: continue
          add(PlacedCluster(old, a.x, a.y, exiting = true))
        }
        for (c in visible) add(PlacedCluster(c, c.x, c.y, exiting = false))
      } else {
        for (c in visible) add(PlacedCluster(c, c.x, c.y, exiting = false))
      }
    }
  }
  Layout(
    modifier = Modifier.fillMaxSize(),
    content = {
      for (item in placed) {
        key(item.cluster.id, item.exiting) {
          val cluster = item.cluster
          val selected = cluster.isSingle && cluster.first.id == selectedMarkerId
          Box(
            Modifier.fadeWhile(item.exiting, progress),
            contentAlignment = Alignment.Center,
          ) {
            if (markerContent != null) {
              Box(Modifier.markerSemantics(cluster, selected, labels, onMarkerClick, onClusterClick)) { markerContent(cluster, selected) }
            } else if (cluster.isSingle) {
              FluidMapPin(cluster.first, selected, labels, reduced, onClick = { onMarkerClick(cluster.first) })
            } else {
              FluidMapClusterBubble(cluster, labels, onClick = { onClusterClick(cluster) })
            }
          }
        }
      }
    },
  ) { measurables, constraints ->
    val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
    val placeables = measurables.map { it.measure(loose) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      // La camera si legge qui, nella disposizione: la mappa che scorre sposta i segnaposto senza ricomporli. La misura e'
      // quella di questo nodo (la mappa intera), non quella scritta da onSizeChanged, che arriva un fotogramma dopo.
      val projector = FluidMapProjector(state.camera, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density)
      val t = progress.value.toDouble()
      placeables.forEachIndexed { i, placeable ->
        val item = placed.getOrNull(i) ?: return@forEachIndexed
        val c = item.cluster
        val x: Double
        val y: Double
        if (item.exiting) {
          x = c.x + wrapDelta(c.x, item.fromX) * t
          y = c.y + (item.fromY - c.y) * t
        } else {
          x = item.fromX + wrapDelta(item.fromX, c.x) * t
          y = item.fromY + (c.y - item.fromY) * t
        }
        val at = projector.toScreenNormalized(x, y)
        placeable.placeRelative((at.x - placeable.width / 2f).toInt(), (at.y - placeable.height / 2f).toInt())
      }
    }
  }
}

private fun wrapDelta(from: Double, to: Double): Double {
  var d = to - from
  if (d > 0.5) d -= 1.0 else if (d < -0.5) d += 1.0
  return d
}

/** Il segnaposto che esce sfuma con il viaggio; quello che resta e' pieno (l'opacita' si legge nel disegno). */
private fun Modifier.fadeWhile(exiting: Boolean, progress: Animatable<Float, *>): Modifier =
  if (!exiting) this else this.graphicsLayer { alpha = 1f - progress.value }

/** Il punto di chi guarda: il pallino coi bordi bianchi, l'alone dell'incertezza e il cono della direzione. */
@Composable
private fun BoxScope.FluidMapUserDot(state: FluidMapState, labels: FluidMapLabels) {
  val here = state.userLocation ?: return
  val color = MaterialTheme.colorScheme.primary
  val ring = Color.White
  Canvas(
    Modifier
      .matchParentSize()
      .testTag(FluidMapTags.UserDot)
      .semantics { contentDescription = labels.userLocation },
  ) {
    val projector = FluidMapProjector(state.camera, size.width, size.height, density)
    val at = projector.toScreen(here.point)
    val accuracy = here.accuracyMeters
    if (accuracy != null && accuracy > 0f) {
      // I metri in pixel alla latitudine del punto: il mondo e' lungo 40 075 km all'equatore.
      val metersPerPx = 40_075_016.686 * kotlin.math.cos(Math.toRadians(here.point.latitude)) / projector.worldPx
      val r = (accuracy / metersPerPx).toFloat()
      if (r > FluidMapControlsDefaults.UserDot.toPx()) drawCircle(color.copy(alpha = 0.14f), r, at)
    }
    here.headingDegrees?.let { heading ->
      val angle = (heading - projector.camera.bearing).toFloat()
      drawArc(
        color = color.copy(alpha = 0.28f),
        startAngle = angle - 90f - 25f, sweepAngle = 50f, useCenter = true,
        topLeft = Offset(at.x - 34.dp.toPx(), at.y - 34.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(68.dp.toPx(), 68.dp.toPx()),
      )
    }
    drawCircle(ring, FluidMapControlsDefaults.UserDot.toPx() / 2f + FluidMapControlsDefaults.UserRing.toPx(), at)
    drawCircle(color, FluidMapControlsDefaults.UserDot.toPx() / 2f, at)
  }
}

/** I numeri dei comandi e dei segni sopra la mappa. */
object FluidMapControlsDefaults {
  /**
   * Il margine dei comandi dal bordo destro: 16 dp. Mappe di iOS 26: 16 pt nella guida dell'iPhone (1,11 px/pt) e nelle
   * immagini di Newsroom (1,746 px/pt), misurate il 26/11/2026 (`sessioni/f8-26/riferimenti/indice.md`). Fiducia alta.
   */
  val EdgeMargin = 16.dp

  /** Lo spazio fra la capsula e il bordo alto del foglio: 10 dp (Newsroom 10 pt, la guida 12,5–13,5). Fiducia media. */
  val SheetGap = 10.dp

  /** Il margine dall'alto quando i comandi stanno in alto (`TopEnd`, la mappa di «Luoghi» in Foto). A occhio, 26/11/2026. */
  val TopMargin = 8.dp

  /** Il lato di un tasto della capsula e della bussola: 48 dp (Mappe di iOS 26: 47,5–49 pt; la capsula 48 × 96). Fiducia alta. */
  val Button = 48.dp

  /** Lo spazio fra la bussola e la capsula: 10 dp (Mappe di iOS 26: 10,3 pt fra gli elementi della colonna). Fiducia alta. */
  val Gap = 10.dp

  /** Il pallino della posizione (18 dp) e il bordo bianco (3 dp), come il punto blu di Mappe. A occhio, 26/11/2026. */
  val UserDot = 18.dp
  val UserRing = 3.dp
}
