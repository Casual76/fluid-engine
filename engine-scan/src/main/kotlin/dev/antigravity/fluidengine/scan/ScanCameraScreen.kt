/*
 * engine-scan (4.0, F8.17): la fotocamera di vetro dello scanner. Il foglio trovato si disegna nel layer (letto nel
 * disegno: il riconoscimento a 15 fotogrammi al secondo non ricompone niente), la scritta cambia per sostituzione
 * sfocata (R7), la pagina scattata vola nella miniatura. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.DocumentScanner
import dev.antigravity.fluidengine.ui.fluid.icons.FlashlightOff
import dev.antigravity.fluidengine.ui.fluid.icons.FlashlightOn
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Tune
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** La pagina che vola dal foglio alla miniatura: da dove parte (radice), cosa mostra. */
private class ScanFlight(val from: Rect, val image: ImageBitmap)

@Composable
internal fun ScanCameraScreen(
  state: FluidScanState,
  camera: FluidScanCamera,
  backdrop: GlassBackdropState,
  strings: FluidScanStrings,
  auto: Boolean,
  onAutoChange: (Boolean) -> Unit,
  filter: FluidScanFilter,
  onFilterChange: (FluidScanFilter) -> Unit,
  retaking: Boolean,
  saving: Boolean,
  onCaptured: suspend (Bitmap, FluidScanQuad?) -> Unit,
  onOpenPages: () -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
) {
  val edges = remember { FluidScanEdges.create() }
  val tracker = remember { FluidScanTracker() }
  var track by remember { mutableStateOf(FluidScanTrack(null, camera.aspect, 0f, false, FluidScanHint.Find)) }
  var capturing by remember { mutableStateOf(false) }
  var flight by remember { mutableStateOf<ScanFlight?>(null) }
  var announce by remember { mutableStateOf("") }
  val flightProgress = remember { Animatable(0f) }
  val flash = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val motion = LocalFluidMotionPolicy.current
  val autoNow by rememberUpdatedState(auto && !retaking)
  val capturingNow by rememberUpdatedState(capturing)
  // Dove stanno sullo schermo l'anteprima e la miniatura, in coordinate della radice: letti solo dal volo e dal disegno.
  val previewBounds = remember { mutableStateOf(Rect.Zero) }
  val thumbBounds = remember { mutableStateOf(Rect.Zero) }
  val rootBounds = remember { mutableStateOf(Rect.Zero) }
  val captured by rememberUpdatedState(onCaptured)

  fun capture(manual: Boolean) {
    if (capturing || !camera.ready) return
    capturing = true
    if (manual) tracker.capturedManually()
    val live = track.quad
    val aspect = track.aspect
    scope.launch {
      try {
        if (!motion.reducedMotion) launch {
          flash.snapTo(0.55f)
          flash.animateTo(0f, tween(ScanCameraDefaults.FlashMillis, easing = LinearEasing))
        }
        val photo = camera.capture()
        // Come VisionKit: i bordi si ricercano sulla foto vera; quelli dal vivo valgono se la foto non li conferma.
        val found = withContext(Dispatchers.Default) { FluidScanEdges.detect(photo) }?.quad
        val quad = found?.takeIf { live == null || it.distanceTo(live, aspect) < ScanCameraDefaults.ConfirmDistance } ?: live ?: found
        if (!motion.reducedMotion) {
          val small = withContext(Dispatchers.Default) { FluidScanImages.straighten(photo, quad ?: FluidScanQuad.Full, 0, 480) }
          val from = quadBounds(quad ?: FluidScanQuad.Full, photo.width.toFloat() / photo.height, previewBounds.value)
          flight = ScanFlight(from, small.asImageBitmap())
          flightProgress.snapTo(0f)
          launch {
            flightProgress.animateTo(1f, FluidMotion.smooth())
            flight = null
          }
        }
        captured(photo, quad)
        announce = strings.pageAdded(state.pages.size)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // Uno scatto fallito (la fotocamera che si chiude a metà) non chiude lo scanner: si riprova.
      } finally {
        capturing = false
      }
    }
  }

  LaunchedEffect(camera, retaking) {
    tracker.reset()
    val main = Handler(Looper.getMainLooper())
    camera.analyze { frame ->
      val found = edges.detect(frame)
      main.post {
        val shake = camera.shake(frame.timestampMillis)
        val moved = found?.copy(quad = found.quad.shifted(shake))
        val next = tracker.update(moved, frame.timestampMillis, autoNow && !capturingNow)
        track = next
        if (next.shouldCapture) capture(manual = false)
      }
    }
  }

  val hint by remember { derivedStateOf { track.hint } }
  val hasSheet by remember { derivedStateOf { track.quad != null } }
  val accent = MaterialTheme.colorScheme.primary
  val sheetLabel = if (hasSheet) strings.documentFound else strings.hintFind
  BoxWithConstraints(Modifier.fillMaxSize().testTag(FluidScanTags.Camera).onGloballyPositioned { rootBounds.value = it.boundsInRoot() }) {
    // L'anteprima e il foglio trovato: la sorgente del vetro dei comandi.
    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)
      .onGloballyPositioned { previewBounds.value = it.boundsInRoot() }
      .drawWithContent {
        drawContent()
        val t = track
        val quad = t.quad
        if (quad != null) {
          val shown = quad
          val path = Path()
          shown.points.forEachIndexed { i, p ->
            val o = scenePoint(p, t.aspect, size)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
          }
          path.close()
          // Il foglio di VisionKit: un velo dell'accento e il contorno pieno, più deciso mentre si avvicina lo scatto.
          drawPath(path, accent.copy(alpha = ScanCameraDefaults.QuadFillAlpha + 0.12f * t.steadiness))
          drawPath(path, accent.copy(alpha = 0.95f), style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
        }
        val f = flash.value
        if (f > 0f) drawRect(Color.White.copy(alpha = f))
      }
      .semantics { contentDescription = sheetLabel }) {
      camera.Preview(Modifier.fillMaxSize())
    }

    val problem = camera.problem
    if (problem != null) {
      ScanCameraProblem(problem, camera, strings, Modifier.align(Alignment.Center))
    } else {
      // La scritta che guida, come «Ready for next scan.» di Note in iOS 26: una capsula di vetro sotto i tasti in alto;
      // il testo cambia per sostituzione sfocata (R7).
      val text = when (hint) {
        FluidScanHint.Find -> strings.hintFind
        FluidScanHint.Closer -> strings.hintCloser
        FluidScanHint.Steady -> if (auto && !retaking) strings.hintSteady else null
        FluidScanHint.Capturing -> strings.hintCapturing
        else -> null
      }
      if (text != null && camera.ready) {
        Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = ScanCameraDefaults.HintTop)
          .fluidGlass(shape = FluidCapsuleShape, backdrop = backdrop)
          .padding(horizontal = 18.dp, vertical = 9.dp).testTag(FluidScanTags.Hint)
          .semantics { liveRegion = LiveRegionMode.Polite }) {
          FluidGlassContentTransition(targetState = text) { label ->
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
          }
        }
      }
    }

    // In alto, come Note di iOS 26 (articolo 108963): la «x» a sinistra, la spunta piena che salva a destra.
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
      FluidGlassIconButton(onClick = onCancel, backdrop = backdrop, modifier = Modifier.testTag(FluidScanTags.Cancel)) {
        FluidIcon(FluidIcons.Close, strings.cancel)
      }
      Spacer(Modifier.weight(1f))
      if (!retaking) {
        val count = state.pages.size
        FluidGlassIconButton(onClick = onSave, backdrop = backdrop, enabled = count > 0 && !saving,
          style = FluidGlassButtonStyle.Prominent, modifier = Modifier.testTag(FluidScanTags.Save)) {
          FluidIcon(FluidIcons.Check, if (count > 0) strings.saveCount(count) else strings.save)
        }
      }
    }

    // In basso: i tre comandi tondi con l'etichetta (Flash, Filtri, Otturatore), poi la miniatura e lo scatto.
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
      .padding(start = 20.dp, end = 20.dp, bottom = ScanCameraDefaults.BottomInset),
      horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(ScanCameraDefaults.ControlGap), verticalAlignment = Alignment.Top) {
        if (camera.hasTorch) {
          ScanControl(strings.torch) {
            FluidGlassIconButton(onClick = { camera.torch = !camera.torch }, backdrop = backdrop, selected = camera.torch) {
              FluidIcon(if (camera.torch) FluidIcons.FlashlightOn else FluidIcons.FlashlightOff, if (camera.torch) strings.torchOff else strings.torchOn)
            }
          }
        }
        ScanControl(strings.filter) {
          FluidMorphMenuButton(
            actions = { FluidScanFilter.All.map { f -> FluidContextAction(strings.filterNames(f), selected = f == filter) { onFilterChange(f) } } },
            onClick = {}, icon = FluidIcons.Tune, text = null, backdrop = backdrop, opensOnTap = true,
            modifier = Modifier.testTag(FluidScanTags.Filter).semantics { contentDescription = strings.filter; stateDescription = strings.filterNames(filter) },
          )
        }
        if (!retaking) {
          ScanControl(if (auto) strings.auto else strings.manual) {
            FluidGlassIconButton(onClick = { onAutoChange(!auto) }, backdrop = backdrop, selected = auto,
              modifier = Modifier.testTag(FluidScanTags.Auto).semantics {
                contentDescription = strings.autoDescription
                stateDescription = if (auto) strings.auto else strings.manual
                role = Role.Switch
              }) {
              FluidIcon(FluidIcons.DocumentScanner, null)
            }
          }
        }
      }
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (!retaking) ScanThumbnailStack(state, strings, landed = flight == null, onOpenPages,
          Modifier.align(Alignment.CenterStart).onGloballyPositioned { thumbBounds.value = it.boundsInRoot() })
        ScanShutter(enabled = camera.ready && !capturing && !saving, strings) { capture(manual = true) }
      }
    }

    // La pagina che vola: nasce dal foglio e finisce nella miniatura (il viaggio d'oro di F1B).
    flight?.let { f ->
      val density = LocalDensity.current
      val target = thumbBounds.value
      Image(f.image, contentDescription = null, contentScale = ContentScale.FillBounds,
        modifier = Modifier.graphicsLayer {
          val p = flightProgress.value
          val to = if (target == Rect.Zero) f.from else target
          val origin = rootBounds.value.topLeft
          val left = lerp(f.from.left, to.left, p) - origin.x
          val top = lerp(f.from.top, to.top, p) - origin.y
          val width = lerp(f.from.width, to.width, p)
          val height = lerp(f.from.height, to.height, p)
          translationX = left
          translationY = top
          // La misura del nodo è quella d'arrivo: il volo scala dal suo angolo in alto a sinistra.
          transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
          scaleX = width / max(1f, size.width)
          scaleY = height / max(1f, size.height)
          shadowElevation = 8f * density.density
          clip = true
          shape = ContinuousCornerShape(6.dp)
        }.size(with(density) { max(1f, f.from.width).toDp() }, with(density) { max(1f, f.from.height).toDp() }))
    }

    // Per TalkBack: «Pagina 3 scansionata», detto una volta.
    if (announce.isNotEmpty()) Text(announce, Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = Color.Transparent)
  }
}

/** Un comando tondo di vetro con l'etichetta sotto, come Flash, Filtri e Otturatore di Note in iOS 26. */
@Composable
private fun ScanControl(label: String, button: @Composable () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    button()
    Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clearAndSetSemantics {})
  }
}

/** La pila delle pagine fatte, in basso a sinistra: l'ultima miniatura e il conto. Tocca per rivederle. */
@Composable
private fun ScanThumbnailStack(state: FluidScanState, strings: FluidScanStrings, landed: Boolean, onOpen: () -> Unit, modifier: Modifier) {
  val pages = state.pages
  val last = pages.lastOrNull()
  val thumb = last?.let { state.thumbnail(it.id) }
  Box(modifier.size(ScanCameraDefaults.ThumbnailSize).testTag(FluidScanTags.Thumbnail)
    .then(if (last != null) Modifier.fluidPressable(onClick = onOpen, role = Role.Button) else Modifier)
    .semantics(mergeDescendants = true) {
      if (last != null) {
        contentDescription = strings.openPages
        stateDescription = strings.pages(pages.size)
      }
    }) {
    if (last != null) {
      Box(Modifier.fillMaxSize().clip(ContinuousCornerShape(8.dp)).background(Color.White.copy(alpha = 0.92f))
        .border(1.5.dp, Color.White, ContinuousCornerShape(8.dp))) {
        if (thumb != null && landed) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      }
      if (pages.size > 1) {
        Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
          contentAlignment = Alignment.Center) {
          Text("${pages.size}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics {})
        }
      }
    }
  }
}

/** Il tasto di scatto: il cerchio bianco nell'anello scuro, come in Note di iOS 26; 72 dp. */
@Composable
private fun ScanShutter(enabled: Boolean, strings: FluidScanStrings, onClick: () -> Unit) {
  Box(Modifier.size(ScanCameraDefaults.ShutterSize).testTag(FluidScanTags.Shutter)
    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
    .padding(6.dp)
    .clip(CircleShape)
    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.45f))
    .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button)
    .semantics { contentDescription = strings.shutter })
}

@Composable
private fun ScanCameraProblem(problem: FluidScanCameraProblem, camera: FluidScanCamera, strings: FluidScanStrings, modifier: Modifier) {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera.permissionChanged() }
  Column(modifier.padding(32.dp).widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(if (problem == FluidScanCameraProblem.Permission) strings.permissionTitle else strings.unavailable,
      color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    if (problem == FluidScanCameraProblem.Permission) {
      FluidGlassButton(strings.permissionAction, { launcher.launch(android.Manifest.permission.CAMERA) },
        style = FluidGlassButtonStyle.Prominent)
    }
  }
}

/** Il punto [p] della scena (normalizzato, proporzioni [aspect]) sullo schermo di misura [size], riempito tagliando. */
internal fun scenePoint(p: FluidScanPoint, aspect: Float, size: Size): Offset {
  val viewAspect = size.width / size.height
  val (sw, sh) = if (viewAspect > aspect) size.width to size.width / aspect else size.height * aspect to size.height
  return Offset((size.width - sw) / 2f + p.x * sw, (size.height - sh) / 2f + p.y * sh)
}

/** Il rettangolo che contiene [quad] sullo schermo [bounds] (in coordinate della radice). */
private fun quadBounds(quad: FluidScanQuad, aspect: Float, bounds: Rect): Rect {
  if (bounds == Rect.Zero) return Rect.Zero
  val pts = quad.points.map { scenePoint(it, aspect, bounds.size) }
  return Rect(bounds.left + pts.minOf { it.x }, bounds.top + pts.minOf { it.y }, bounds.left + pts.maxOf { it.x }, bounds.top + pts.maxOf { it.y })
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Lo stesso foglio spostato di [d] (la mano finta della sorgente di prova). */
internal fun FluidScanQuad.shifted(d: FluidScanPoint): FluidScanQuad =
  if (d.x == 0f && d.y == 0f) this else FluidScanQuad(topLeft + d, topRight + d, bottomRight + d, bottomLeft + d)

/** Le misure della fotocamera dello scanner. */
internal object ScanCameraDefaults {
  /** Lo scatto: 72 dp, come quello della fotocamera di iOS (fiducia alta: 72 pt sull'iPhone). */
  val ShutterSize = 72.dp
  /** La miniatura delle pagine: 56 dp. A occhio, sulle riprese di Note di iOS 26. */
  val ThumbnailSize = 56.dp
  /** Il margine in basso dello scatto sopra la barra di sistema: 20 dp. A occhio, sull'immagine dell'articolo 108963. */
  val BottomInset = 20.dp
  /** La scritta che guida: 64 dp sotto la barra di stato, sotto i tasti in alto. A occhio, come in Note di iOS 26. */
  val HintTop = 64.dp
  /** Lo spazio fra i tre comandi tondi: 22 dp. A occhio, sull'immagine dell'articolo 108963 (circa 92 pt fra i centri). */
  val ControlGap = 22.dp
  /** Il velo del foglio trovato: l'accento al 28%. A occhio, sulle riprese di VisionKit (il blu di sistema velato). */
  const val QuadFillAlpha = 0.28f
  /** Il lampo dello scatto: 180 ms di bianco che si spegne. A occhio. */
  const val FlashMillis = 180
  /** I bordi trovati sulla foto vera valgono se distano da quelli dal vivo meno dell'8% della diagonale. A occhio. */
  const val ConfirmDistance = 0.08f
}
