package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.derivedStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * I tempi e le misure del movimento dell'editor. Le fonti: le riprese di Foto in iOS 26 non sono pubbliche a 120 Hz, quindi
 * i tempi sono **a occhio** (F8.15, 25 novembre 2026) sui video della guida dell'iPhone e sugli eventi WWDC; le misure
 * della cornice vengono dalle schermate di «Ritaglia» di iOS 26 e 27 (`sessioni/f8-2/riferimenti`, 1,244 px per punto).
 */
internal object EditorMotion {
  /** Dopo una maniglia lasciata, la cornice aspetta poi torna a riempire lo spazio (Foto: ~0,5 s). A occhio. */
  const val SettleDelayMillis = 450L
  /** La molla del ritorno della cornice: smorzata, ~0,5 s (la risposta «smooth» dell'engine). */
  val Settle = spring<Float>(dampingRatio = 1f, stiffness = 160f)
  /** Il passaggio fra «Regola» e «Ritaglia»: i margini della cornice crescono con la molla standard. */
  val Mode = spring<Float>(dampingRatio = 1f, stiffness = 260f)
  /** La rotazione di 90° e lo specchio: un quarto di giro in ~0,35 s, senza rimbalzo. A occhio. */
  val Turn = spring<Float>(dampingRatio = 1f, stiffness = 320f)
  /** Tenere premuta la foto mostra l'originale dopo 100 ms (un tocco veloce non lampeggia). A occhio. */
  const val HoldToCompareMillis = 100L
  /** La griglia dei terzi compare quando il dito muove la cornice e svanisce quando si ferma. */
  val Grid = spring<Float>(dampingRatio = 1f, stiffness = 400f)
}

/** Le misure della cornice: iOS 26, «Ritaglia» (riferimento a 1,244 px per punto). */
internal object EditorCropLook {
  /** I margini della cornice dentro lo spazio della foto: 24 dp ai lati (30 px su 489 della schermata). */
  val MarginX = 24.dp
  val MarginY = 24.dp
  /** Gli angoli a «L»: spessi 3 dp, lunghi 20 dp, appena fuori dalla cornice. */
  val CornerThickness = 3.dp
  val CornerLength = 20.dp
  /** I segni a meta' dei lati. */
  val EdgeMark = 18.dp
  /** La riga della cornice e quelle della griglia. */
  val FrameLine = 1.dp
  val GridLine = 0.6.dp
  /** Il velo sulla parte della foto fuori dal ritaglio, del colore della pagina. */
  const val Veil = 0.72f
  /** Il dito prende una maniglia entro 26 dp (piu' della meta' dei 44 dp del bersaglio). */
  val Touch = 26.dp
  /** La cornice non scende sotto 64 dp sullo schermo. */
  val MinFrame = 64.dp
}

/** Cio' che il disegno ricorda fra un fotogramma e l'altro, senza ricomporre (nessuna allocazione nel disegno). */
private class PhotoHolder {
  val values = FloatArray(9)
  val matrix = Matrix()
  val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  val veil = android.graphics.Path()
  val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  var orientKey = -1
  var orient = FluidAffine.Identity
  var outKey: FluidCropGeometry? = null
  var toOut = FluidAffine.Identity
  var radius = 1f
  var area = ScreenRect(0f, 0f, 1f, 1f)
  var marginX = 0f
  var marginY = 0f
  var lastView: CropView? = null
  var frame = ScreenRect(0f, 0f, 0f, 0f)
  var keyGeometry: FluidCropGeometry? = null
  var keyCrop = -1f
  var keySettle = -1f
  var keyFrozen: CropView? = null
  var keyArea: ScreenRect? = null
  var settleJob: Job? = null
  /** F7.24: lo strato a meta' risoluzione dell'anteprima mentre il dito muove il righello (da Android 13). */
  var light: android.graphics.RenderNode? = null
}

/**
 * La foto dell'editor, con la cornice in «Ritaglia» e il confronto in «Regola». Si disegna tutta nel disegno: la ricetta
 * si legge li', cosi' un righello che muove l'esposizione ridisegna solo questo, senza ricomporre.
 */
@Composable
internal fun EditorPhoto(state: FluidImageEditorState, strings: FluidImageEditorStrings, modifier: Modifier = Modifier) {
  val image = state.image
  val holder = remember { PhotoHolder() }
  var areaSize by remember { mutableStateOf(IntSize.Zero) }
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val slop = LocalViewConfiguration.current.touchSlop
  val scope = rememberCoroutineScope()

  val shader = image?.let { rememberAdjustShader(it) }
  // L'anteprima della CPU, dove non c'e' lo shader: ridotta mentre il dito muove, piena quando si ferma.
  val cpuState = if (image != null && shader == null) rememberCpuPreview(state, image) else null

  val cropProgress = remember { Animatable(if (state.mode == FluidEditorMode.Crop) 1f else 0f) }
  LaunchedEffect(state, reduced) {
    snapshotFlow { state.mode }.collect { m ->
      val target = if (m == FluidEditorMode.Crop) 1f else 0f
      if (reduced) cropProgress.snapTo(target) else cropProgress.animateTo(target, EditorMotion.Mode)
    }
  }
  var frozen by remember { mutableStateOf<CropView?>(null) }
  val settle = remember { Animatable(0f) }
  val grid = remember { Animatable(0f) }
  var fineGrid by remember { mutableStateOf(false) }
  LaunchedEffect(state) {
    snapshotFlow { state.interacting && state.mode == FluidEditorMode.Crop }.collect { on ->
      if (reduced) grid.snapTo(if (on) 1f else 0f) else grid.animateTo(if (on) 1f else 0f, EditorMotion.Grid)
    }
  }
  val touch = with(density) { EditorCropLook.Touch.toPx() }
  val minFrame = with(density) { EditorCropLook.MinFrame.toPx() }
  val marginXpx = with(density) { EditorCropLook.MarginX.toPx() }
  val marginYpx = with(density) { EditorCropLook.MarginY.toPx() }
  val veilColor = scheme.background
  val handleColor = scheme.onBackground
  val cornerT = with(density) { EditorCropLook.CornerThickness.toPx() }
  val cornerL = with(density) { EditorCropLook.CornerLength.toPx() }
  val edgeMark = with(density) { EditorCropLook.EdgeMark.toPx() }
  val frameLine = with(density) { EditorCropLook.FrameLine.toPx() }
  val gridLine = with(density) { EditorCropLook.GridLine.toPx() }

  /** La vista adatta di adesso, dalla misura dello spazio (scritta al layout) e dai margini della scheda. */
  fun fitNow(g: FluidCropGeometry, ow: Int, oh: Int): CropView =
    CropMath.fit(holder.area, marginXpx * cropProgress.value, marginYpx * cropProgress.value, g, ow, oh)

  val turnAngle = remember { Animatable(0f) }
  val turnScale = remember { Animatable(1f) }
  val flipScale = remember { Animatable(1f) }
  LaunchedEffect(state) {
    var seen = state.turnEvents
    snapshotFlow { state.turnEvents }.collect { n ->
      if (n == seen) return@collect
      seen = n
      val img = state.image ?: return@collect
      if (reduced) return@collect
      if (state.lastTurnFlip) {
        flipScale.snapTo(-1f)
        launch { flipScale.animateTo(1f, EditorMotion.Turn) }
      } else {
        val before = holder.lastView
        val g = state.edit.geometry
        val (ow, oh) = g.orientedSize(img.width, img.height)
        val after = fitNow(g, ow, oh)
        turnAngle.snapTo(90f)
        turnScale.snapTo(if (before != null) before.zoom / after.zoom else 1f)
        launch { turnAngle.animateTo(0f, EditorMotion.Turn) }
        launch { turnScale.animateTo(1f, EditorMotion.Turn) }
      }
    }
  }

  /** La vista di adesso (quella che il disegno usa), dalle misure dello spazio e dalla ricetta. */
  fun currentView(img: EditorImage, g: FluidCropGeometry): CropView {
    val (ow, oh) = g.orientedSize(img.width, img.height)
    val fit = fitNow(g, ow, oh)
    return frozen?.lerp(fit, settle.value) ?: fit
  }

  fun startSettle() {
    holder.settleJob?.cancel()
    holder.settleJob = scope.launch {
      if (!reduced) delay(EditorMotion.SettleDelayMillis)
      if (reduced) settle.snapTo(1f) else settle.animateTo(1f, EditorMotion.Settle)
      frozen = null
      settle.snapTo(0f)
    }
  }

  val gestures = Modifier.pointerInput(state, image) {
    val img = image ?: return@pointerInput
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      if (state.mode != FluidEditorMode.Crop) {
        if (state.splitCompare) return@awaitEachGesture
        // «Regola»: tenendo premuto si vede l'originale, come in Foto.
        val up = withTimeoutOrNull(EditorMotion.HoldToCompareMillis) { waitForUpOrCancellation() }
        if (up != null) return@awaitEachGesture
        state.showingOriginal = true
        try {
          waitForUpOrCancellation()
        } finally {
          state.showingOriginal = false
        }
        return@awaitEachGesture
      }
      // «Ritaglia». Se la cornice sta ancora tornando al suo posto, si riparte da dove si trova.
      holder.settleJob?.cancel()
      if (frozen != null) {
        frozen = null
        scope.launch { settle.snapTo(0f) }
      }
      val g0 = state.edit.geometry
      val view0 = fitNow(g0, g0.orientedSize(img.width, img.height).first, g0.orientedSize(img.width, img.height).second)
      val (ow, oh) = g0.orientedSize(img.width, img.height)
      val frame0 = CropMath.frame(view0, g0, ow, oh)
      val handle = CropMath.handleAt(frame0, down.position.x, down.position.y, touch)
      var mode = 0 // 0 in attesa, 1 maniglia, 2 sposta, 3 due dita
      var total = Offset.Zero
      val ratio = g0.aspect.ratio(ow.toFloat() / oh)
      while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.count { it.pressed }
        if (pressed == 0) break
        if (pressed >= 2 && mode != 1) {
          if (mode != 3) {
            mode = 3
            state.beginGesture()
            fineGrid = false
          }
          val g = state.edit.geometry
          val v = fitNow(g, ow, oh)
          val centroid = event.calculateCentroid(useCurrent = true)
          var next = CropMath.pinched(g, v, event.calculateZoom(), centroid.x, centroid.y, ow, oh,
            minPixels = max(24f, min(ow, oh) / 20f))
          val turn = event.calculateRotation()
          if (turn != 0f) {
            val angle = (next.angle + turn).coerceIn(-45f, 45f)
            if (next.angle * angle < 0f || (angle == 0f) != (next.angle == 0f)) haptics.play(FluidHapticEvent.Threshold)
            val (sw, sh) = CropMath.sourceDims(next, ow, oh)
            next = next.copy(angle = angle).fitted(sw, sh)
            fineGrid = true
          }
          val pan = event.calculatePan()
          next = CropMath.panned(next, fitNow(next, ow, oh), pan.x, pan.y, ow, oh)
          state.setGeometryLive(next)
          event.changes.forEach { it.consume() }
          continue
        }
        val change = event.changes.firstOrNull { it.id == down.id && it.pressed } ?: event.changes.first { it.pressed }
        val delta = change.positionChange()
        total += delta
        if (mode == 0 && total.getDistance() > slop) {
          mode = if (handle != null) 1 else 2
          state.beginGesture()
          fineGrid = false
          if (mode == 1) frozen = view0
        }
        when (mode) {
          1 -> state.setGeometryLive(CropMath.resized(g0, view0, handle!!, total.x, total.y, ow, oh, ratio, minFrame))
          2 -> {
            val g = state.edit.geometry
            state.setGeometryLive(CropMath.panned(g, fitNow(g, ow, oh), delta.x, delta.y, ow, oh))
          }
        }
        if (mode != 0) change.consume()
      }
      if (mode != 0) {
        state.endGesture()
        if (frozen != null) startSettle()
      }
    }
  }

  // La cornice a riposo, per escludere i suoi angoli dai gesti di sistema; null mentre il dito muove.
  val exclusion by remember(state, image) {
    derivedStateOf {
      val img = image ?: return@derivedStateOf null
      if (state.interacting || areaSize == IntSize.Zero) return@derivedStateOf null
      val g = state.edit.geometry
      val (ow, oh) = g.orientedSize(img.width, img.height)
      val area = ScreenRect(0f, 0f, areaSize.width.toFloat(), areaSize.height.toFloat())
      val cp = cropProgress.value
      CropMath.frame(CropMath.fit(area, marginXpx * cp, marginYpx * cp, g, ow, oh), g, ow, oh)
    }
  }
  val photoLabel = strings.photo
  Box(
    modifier
      .fillMaxSize()
      // F7.24: la foto ha uno strato suo. Mentre il righello gira si ridisegna a ogni valore, e senza strato faceva
      // riregistrare tutta la pagina dell'editor a ogni fotogramma.
      .graphicsLayer()
      .clipToBounds()
      .onSizeChanged {
        holder.area = ScreenRect(0f, 0f, it.width.toFloat(), it.height.toFloat())
        areaSize = it
      }
      .then(gestures)
      .semantics {
        contentDescription = photoLabel
        if (state.mode == FluidEditorMode.Crop) {
          stateDescription = strings.cropFrame
          customActions = listOf(
            CustomAccessibilityAction(strings.rotate) { state.rotateLeft(); true },
            CustomAccessibilityAction(strings.flip) { state.flip(); true },
            CustomAccessibilityAction(strings.zoomIn) { zoomBy(state, 1.25f); true },
            CustomAccessibilityAction(strings.zoomOut) { zoomBy(state, 0.8f); true },
            CustomAccessibilityAction(strings.reset) { state.resetGeometry(); true },
          )
        } else {
          stateDescription = if (state.showingOriginal) strings.original else strings.edited
          customActions = listOf(
            CustomAccessibilityAction(if (state.showingOriginal) strings.edited else strings.original) {
              state.showingOriginal = !state.showingOriginal
              true
            },
          )
        }
      }
      .drawBehind {
        val img = image ?: return@drawBehind
        val edit = state.edit
        val g = edit.geometry
        val crop = cropProgress.value
        holder.marginX = marginXpx * crop
        holder.marginY = marginYpx * crop
        if (state.showingOriginal && state.mode != FluidEditorMode.Crop) {
          drawOriginal(img, holder)
          return@drawBehind
        }
        // La vista, la matrice e la cornice si rifanno solo quando cambia qualcosa: mentre il righello gira cambia solo
        // la ricetta delle regolazioni, e il disegno non alloca niente.
        val settleNow = settle.value
        val frozenNow = frozen
        if (g !== holder.keyGeometry || crop != holder.keyCrop || settleNow != holder.keySettle ||
          frozenNow !== holder.keyFrozen || holder.area !== holder.keyArea) {
          val ow = if (g.quarterTurns % 2 == 0) img.width else img.height
          val oh = if (g.quarterTurns % 2 == 0) img.height else img.width
          val view = currentView(img, g)
          holder.lastView = view
          val key = (if (g.flipped) 4 else 0) + g.quarterTurns
          if (key != holder.orientKey) {
            holder.orient = g.sourceToOriented(img.width, img.height)
            holder.orientKey = key
          }
          CropMath.matrix(view, g, holder.orient, holder.values)
          holder.matrix.setValues(holder.values)
          holder.frame = CropMath.frame(view, g, ow, oh)
          holder.keyGeometry = g
          holder.keyCrop = crop
          holder.keySettle = settleNow
          holder.keyFrozen = frozenNow
          holder.keyArea = holder.area
        }
        val frame = holder.frame
        drawIntoCanvas { canvas ->
          val nc = canvas.nativeCanvas
          // F7.24: mentre il dito muove una regolazione, la foto dello shader si disegna a meta' risoluzione (un quarto
          // dei pixel) in uno strato suo, poi si allarga; al rilascio torna piena e nitida. «Dito nitido» vale per il
          // controllo, non per l'anteprima.
          val light = shader != null && state.interacting && state.mode == FluidEditorMode.Adjust &&
            FluidImageEditorDiagnostics.lightPreview && nc.isHardwareAccelerated &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
          if (shader != null) {
            if (holder.outKey !== g) {
              holder.toOut = img.toOutputFraction(g)
              holder.radius = img.sharpRadius(g)
              holder.outKey = g
            }
            shader.update(edit.adjustments, holder.toOut, holder.radius)
          }
          if (light) {
            val node = holder.light ?: android.graphics.RenderNode("FluidEditorLightPreview").also { holder.light = it }
            val w = kotlin.math.ceil(size.width * EditorRendering.LightScale).toInt().coerceAtLeast(1)
            val h = kotlin.math.ceil(size.height * EditorRendering.LightScale).toInt().coerceAtLeast(1)
            node.setPosition(0, 0, w, h)
            node.setUseCompositingLayer(true, null)
            val rc = node.beginRecording(w, h)
            try {
              rc.scale(EditorRendering.LightScale, EditorRendering.LightScale)
              drawPhotoTransforms(rc, frame, turnAngle.value, turnScale.value, flipScale.value)
              rc.concat(holder.matrix)
              shader!!.draw(rc)
            } finally {
              node.endRecording()
            }
            nc.save()
            nc.scale(1f / EditorRendering.LightScale, 1f / EditorRendering.LightScale)
            nc.drawRenderNode(node)
            nc.restore()
          }
          nc.save()
          drawPhotoTransforms(nc, frame, turnAngle.value, turnScale.value, flipScale.value)
          nc.save()
          nc.concat(holder.matrix)
          if (light) {
            // Gia' disegnata sopra, a meta' risoluzione.
          } else if (shader != null) {
            shader.draw(nc)
          } else {
            val bitmap = cpuState?.value
            if (bitmap != null && !edit.adjustments.isNeutral) {
              nc.save()
              nc.scale(img.width.toFloat() / bitmap.width, img.height.toFloat() / bitmap.height)
              nc.drawBitmap(bitmap, 0f, 0f, holder.bitmapPaint)
              nc.restore()
            } else {
              nc.drawBitmap(img.preview, 0f, 0f, holder.bitmapPaint)
            }
          }
          nc.restore()
          // Il velo fuori dal ritaglio: pieno in «Regola» (si vede solo il ritaglio), velato in «Ritaglia».
          val veilAlpha = 1f - (1f - EditorCropLook.Veil) * crop
          val path = holder.veil
          path.reset()
          path.fillType = android.graphics.Path.FillType.EVEN_ODD
          path.addRect(-size.width, -size.height, size.width * 2, size.height * 2, android.graphics.Path.Direction.CW)
          if (g.aspect.round) {
            path.addOval(frame.left, frame.top, frame.right, frame.bottom, android.graphics.Path.Direction.CW)
          } else {
            path.addRect(frame.left, frame.top, frame.right, frame.bottom, android.graphics.Path.Direction.CW)
          }
          holder.veilPaint.color = veilColor.copy(alpha = veilAlpha).toArgb()
          nc.drawPath(path, holder.veilPaint)
          nc.restore()
        }
        if (crop > 0f) drawCropOverlay(frame, crop, grid.value, fineGrid, g.aspect.round, handleColor, cornerT, cornerL,
          edgeMark, frameLine, gridLine)
      },
  ) {
    // F8.15b · Gli angoli della cornice stanno a 24 dp dal bordo dello schermo: dentro la fascia da cui Android 10 e
    // oltre fa partire «indietro» (da tutti e due i lati). Sul tablet, nel formato del telefono, tirare un angolo chiudeva
    // l'editor. Gli angoli si escludono dai gesti di sistema (4 riquadri da 52 dp: 104 dp per lato, sotto i 200 che il
    // sistema concede). Solo in «Ritaglia» e a riposo: durante il gesto il dito e' gia' dell'editor, e i riquadri non
    // cambiano a ogni fotogramma.
    if (image != null && state.mode == FluidEditorMode.Crop && exclusion != null) {
      val box = EditorCropLook.Touch * 2
      for (corner in 0 until 4) {
        Box(
          Modifier
            .offset {
              val f = exclusion ?: return@offset IntOffset.Zero
              val x = if (corner % 2 == 0) f.left else f.right
              val y = if (corner < 2) f.top else f.bottom
              IntOffset((x - touch).roundToInt(), (y - touch).roundToInt())
            }
            .size(box)
            .systemGestureExclusion(),
        )
      }
    }
  }
}

/** L'originale: la foto com'era, intera, adatta allo spazio. */
private fun DrawScope.drawOriginal(img: EditorImage, holder: PhotoHolder) {
  val z = min(size.width / img.width, size.height / img.height)
  val dx = (size.width - img.width * z) / 2f
  val dy = (size.height - img.height * z) / 2f
  drawIntoCanvas { canvas ->
    val nc = canvas.nativeCanvas
    nc.save()
    nc.translate(dx, dy)
    nc.scale(z, z)
    nc.drawBitmap(img.preview, 0f, 0f, holder.bitmapPaint)
    nc.restore()
  }
}

/** La cornice di «Ritaglia»: la riga, gli angoli a «L» appena fuori, i segni a meta' dei lati, la griglia dei terzi. */
private fun DrawScope.drawCropOverlay(
  f: ScreenRect, alpha: Float, gridAlpha: Float, fine: Boolean, round: Boolean, ink: Color,
  cornerT: Float, cornerL: Float, edgeMark: Float, frameLine: Float, gridLine: Float,
) {
  val white = Color.White
  if (gridAlpha > 0f) {
    val lines = if (fine) 9 else 3
    for (i in 1 until lines) {
      val x = f.left + f.width * i / lines
      val y = f.top + f.height * i / lines
      drawLine(white, Offset(x, f.top), Offset(x, f.bottom), gridLine, alpha = 0.55f * gridAlpha * alpha)
      drawLine(white, Offset(f.left, y), Offset(f.right, y), gridLine, alpha = 0.55f * gridAlpha * alpha)
    }
  }
  if (round) {
    drawOval(white, Offset(f.left, f.top), androidx.compose.ui.geometry.Size(f.width, f.height), alpha = 0.9f * alpha,
      style = Stroke(frameLine))
  }
  drawRect(white, Offset(f.left, f.top), androidx.compose.ui.geometry.Size(f.width, f.height),
    alpha = (if (round) 0.35f else 0.9f) * alpha, style = Stroke(frameLine))
  val o = cornerT / 2f
  val l = f.left - o
  val t = f.top - o
  val r = f.right + o
  val b = f.bottom + o
  val len = min(cornerL, min(f.width, f.height) / 3f)
  fun corner(x: Float, y: Float, sx: Float, sy: Float) {
    drawLine(ink, Offset(x - sx * o, y), Offset(x + sx * len, y), cornerT, alpha = alpha)
    drawLine(ink, Offset(x, y - sy * o), Offset(x, y + sy * len), cornerT, alpha = alpha)
  }
  corner(l, t, 1f, 1f)
  corner(r, t, -1f, 1f)
  corner(l, b, 1f, -1f)
  corner(r, b, -1f, -1f)
  if (f.width > edgeMark * 3) {
    drawLine(ink, Offset(f.centerX - edgeMark / 2, t), Offset(f.centerX + edgeMark / 2, t), cornerT, alpha = alpha)
    drawLine(ink, Offset(f.centerX - edgeMark / 2, b), Offset(f.centerX + edgeMark / 2, b), cornerT, alpha = alpha)
  }
  if (f.height > edgeMark * 3) {
    drawLine(ink, Offset(l, f.centerY - edgeMark / 2), Offset(l, f.centerY + edgeMark / 2), cornerT, alpha = alpha)
    drawLine(ink, Offset(r, f.centerY - edgeMark / 2), Offset(r, f.centerY + edgeMark / 2), cornerT, alpha = alpha)
  }
}

/** TalkBack, «Stringi il ritaglio» e «Allarga il ritaglio»: il pizzico attorno al centro, un passo. */
private fun zoomBy(state: FluidImageEditorState, scale: Float) {
  val img = state.image ?: return
  val g = state.edit.geometry
  val (ow, oh) = g.orientedSize(img.width, img.height)
  val v = CropView(0f, 0f, 1f, g.centerX * ow, g.centerY * oh)
  state.setGeometryLive(CropMath.pinched(g, v, scale, 0f, 0f, ow, oh, max(24f, min(ow, oh) / 20f)))
  state.commit()
}

/** Le scelte del disegno. */
/** Il giro (a due dita o di 90°) e lo specchio della foto attorno al centro della cornice: uguali piena o leggera. */
private fun drawPhotoTransforms(c: android.graphics.Canvas, frame: ScreenRect, turnAngle: Float, turnScale: Float, flip: Float) {
  if (turnAngle != 0f || turnScale != 1f) {
    c.rotate(turnAngle, frame.centerX, frame.centerY)
    c.scale(turnScale, turnScale, frame.centerX, frame.centerY)
  }
  if (flip != 1f) c.scale(flip, 1f, frame.centerX, frame.centerY)
}

internal object EditorRendering {
  /** L'anteprima della CPU mentre il dito muove: a meta' risoluzione (un quarto dei pixel). */
  const val CpuMovingScale = 0.5f

  /** F7.24: l'anteprima dello shader mentre il dito muove una regolazione: meta' risoluzione, come quella della CPU. */
  const val LightScale = 0.5f

  /** Falso nelle prove che vogliono la strada della CPU anche su Android 13 e oltre. */
  @Volatile
  var gpuAllowed: Boolean = true
}
