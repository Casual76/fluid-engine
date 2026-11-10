package dev.antigravity.fluidengine.sample.measure

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidFormPresets
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.runtimeShaderEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Tutto ciò che una scena riceve dalla pagina. */
@Stable
internal class MeasureSession(
  val config: MeasureConfig,
  val clock: MeasureClock,
  /** Nullo se la pagina non ha una finestra da cui leggere i tempi (un'anteprima, una prova sul computer). */
  val meter: FrameMeter?,
  val report: MeasureReport,
  val sources: MeasureSources,
  /** Le sorgenti delle sonde; nulle sotto Android 13, dove AGSL non esiste. */
  val shaders: ProtoSources?,
  /** Lo spazio che la barra delle schede della galleria copre in fondo. */
  val bottomInset: Dp,
)

/** La scena chiesta da [MeasureSession.config], e la sua misura. */
@Composable
internal fun MeasureSceneContent(session: MeasureSession) {
  val shaders = session.shaders
  when (session.config.scene) {
    MeasureScene.Base -> BaseScene(session)
    MeasureScene.Compare, MeasureScene.Uniforms, MeasureScene.Pieces -> GlassScene(session)
    MeasureScene.Edge -> EdgeScene(session)
    MeasureScene.Probe -> ProbeScene(session)
    MeasureScene.Compile ->
      if (shaders != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        CompileScene(session, shaders)
      } else {
        UnsupportedScene(session)
      }
    MeasureScene.Physics -> PhysicsScene(session)
  }
}

/** Il palco e basta: ciò che costa un fotogramma senza vetro, da sottrarre a tutto il resto. */
@Composable
private fun BaseScene(session: MeasureSession) {
  MeasureStage(session.config, session.clock, session.sources) {}
  LaunchedEffect(Unit) { runMeasureWindows(session.config, session.meter, session.report) }
}

private const val ProtoUnavailable =
  "il prototipo non si puo' montare: AGSL esiste da Android 13, oppure una sorgente e' stata rifiutata (righe ERRORE)"

/** Una scena che chiede il prototipo dove non si può montare: lo dice, e chiude la misura. */
@Composable
private fun UnsupportedScene(session: MeasureSession) {
  MeasureStage(session.config, session.clock, session.sources) {}
  LaunchedEffect(Unit) {
    session.report.line("FINE ${session.config.key} | $ProtoUnavailable")
  }
}

// --- (b) (c) (d): gli elementi di vetro ------------------------------------------------------------

/**
 * Le tre scene degli elementi (confronto con oggi, soli uniform, pezzi fusi) sono lo stesso palco con
 * default diversi: ciò che cambia fra una misura e l'altra sta tutto in [MeasureConfig].
 */
@Composable
private fun GlassScene(session: MeasureSession) {
  val config = session.config
  val drive = remember(config.animation, session.clock) { driveFor(config.animation, session.clock) }
  val showToday = config.glass == MeasureGlass.Today || config.glass == MeasureGlass.Both
  val showProto = config.glass == MeasureGlass.Proto || config.glass == MeasureGlass.Both
  val shaders = session.shaders
  val protoAvailable = shaders != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  MeasureStage(config, session.clock, session.sources) {
    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(bottom = session.bottomInset),
      verticalArrangement = Arrangement.spacedBy(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (showToday) GlassRow(session, drive, shaders = null)
      if (showProto && protoAvailable) GlassRow(session, drive, shaders = shaders)
    }
  }
  LaunchedEffect(Unit) {
    if (showProto && !protoAvailable) {
      session.report.line("FINE ${config.key} | $ProtoUnavailable")
    } else {
      runMeasureWindows(config, session.meter, session.report)
    }
  }
}

/** Una riga di elementi: del prototipo se ci sono [shaders], altrimenti del vetro di oggi. */
@Composable
private fun GlassRow(session: MeasureSession, drive: ProtoDrive, shaders: ProtoSources?) {
  val config = session.config
  val proto = shaders != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  if (config.scene == MeasureScene.Pieces) {
    if (shaders != null && proto) ProtoPieces(session, shaders, drive) else TodayPieces(session, drive)
  } else {
    val elements = if (config.element == MeasureElement.All) {
      listOf(MeasureElement.Round, MeasureElement.Capsule, MeasureElement.Panel)
    } else {
      listOf(config.element)
    }
    BoxWithConstraints {
      val content: @Composable () -> Unit = {
        elements.forEach { element ->
          if (shaders != null && proto) {
            ProtoElement(session, shaders, element, drive)
          } else {
            TodayElement(session, element, drive)
          }
        }
      }
      // In formato telefono i tre elementi del banco non stanno in una riga: vanno in colonna.
      if (maxWidth < 440.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          content()
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
          content()
        }
      }
    }
  }
}

/** Taglia e raggio di un elemento, in dp: quelli della scena `oggi-foto` del banco. `radius` nullo è una capsula. */
private class ElementMetrics(val width: Dp, val height: Dp, val radius: Dp?)

private fun metricsOf(element: MeasureElement): ElementMetrics = when (element) {
  MeasureElement.Round -> ElementMetrics(44.dp, 44.dp, null)
  MeasureElement.Capsule -> ElementMetrics(156.dp, 56.dp, null)
  MeasureElement.Large -> ElementMetrics(360.dp, 480.dp, 28.dp)
  MeasureElement.Panel, MeasureElement.All -> ElementMetrics(170.dp, 192.dp, 28.dp)
}

/** Quanto il nodo sporge attorno alla sagoma quando il ritaglio hardware è spento, in dp. */
private const val ProtoMarginDp = 8f

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ProtoElement(session: MeasureSession, shaders: ProtoSources, element: MeasureElement, drive: ProtoDrive) {
  val config = session.config
  val metrics = metricsOf(element)
  val margin = if (config.clip) 0f else ProtoMarginDp
  val dark = GlassDefaults.isDarkSurface()
  val spec = remember(config.blurDp, config.dispersion, dark) { ProtoSpec.of(config.blurDp, config.dispersion, dark) }
  val body = remember(shaders) { shaders.body() }
  val light = remember(shaders, config.light) { if (config.light) shaders.light() else null }
  val radiusDp = metrics.radius?.value
  val shape: Shape = remember(config.clip, radiusDp) {
    when {
      !config.clip -> RectangleShape
      radiusDp == null -> CircleShape
      else -> RoundedCornerShape(radiusDp.dp)
    }
  }
  val layout: ProtoLayout = remember(margin, radiusDp) {
    { width, height, density ->
      val inset = margin * density
      val halfWidth = width / 2f - inset
      val halfHeight = height / 2f - inset
      add(width / 2f, height / 2f, halfWidth, halfHeight, radiusDp?.times(density) ?: min(halfWidth, halfHeight))
    }
  }
  Box(
    modifier = Modifier
      .size(metrics.width + (margin * 2f).dp, metrics.height + (margin * 2f).dp)
      .protoGlass(
        source = session.sources.layer,
        body = body,
        light = light,
        spec = spec,
        mount = config.mount,
        shape = shape,
        marginDp = margin,
        scale = config.scale,
        // L'ombra di oggi segue la sagoma del nodo: senza ritaglio il nodo è un rettangolo, e sarebbe un'ombra sbagliata.
        shadow = config.shadow && config.clip,
        drive = drive,
        layout = layout,
      ),
  )
}

/** Lo stesso elemento col vetro di oggi: `glassSurface`, col ruolo e la tinta che un'app gli darebbe. */
@Composable
private fun TodayElement(session: MeasureSession, element: MeasureElement, drive: ProtoDrive) {
  val metrics = metricsOf(element)
  val radius = metrics.radius
  val shape: Shape = remember(radius) { if (radius == null) CircleShape else ContinuousCornerShape(radius) }
  val role = when (element) {
    MeasureElement.Round -> GlassRole.Interactive
    MeasureElement.Capsule -> GlassRole.Floating
    else -> GlassRole.Modal
  }
  val optics = when (element) {
    MeasureElement.Round -> GlassDefaults.buttonOptics()
    MeasureElement.Capsule -> GlassDefaults.optics(GlassRole.Floating)
    MeasureElement.Large -> GlassDefaults.panelOptics()
    else -> GlassDefaults.panelOptics(compact = true)
  }
  val tint = when (element) {
    MeasureElement.Round -> GlassDefaults.controlTint()
    MeasureElement.Capsule -> GlassDefaults.floatingTint()
    else -> GlassDefaults.panelTint()
  }
  Box(
    modifier = Modifier
      .size(metrics.width, metrics.height)
      .glassSurface(
        state = session.sources.glass,
        tint = tint,
        shape = shape,
        intensity = drive.materialize,
        role = role,
        optics = optics,
        pressed = drive.press,
        nestInContainer = false,
      ),
  )
}

private const val PiecesMarginDp = 16f
private const val PiecesGapDp = 8f
private val PiecesHeight = 56.dp
private val PiecesMaxWidth = 656.dp

/**
 * Un campo solo con N pezzi fusi. La superficie ha sempre la stessa taglia e i pezzi si dividono la
 * stessa larghezza, così fra 1 e 8 pezzi cambia il lavoro per pixel e non quanti pixel sono coperti.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ProtoPieces(session: MeasureSession, shaders: ProtoSources, drive: ProtoDrive) {
  val config = session.config
  val pieces = config.pieces
  val variant = if (config.fitVariant) ProtoSources.fittingVariant(pieces) else MeasureConfig.MaxPieces
  val dark = GlassDefaults.isDarkSurface()
  val spec = remember(config.blurDp, config.dispersion, dark) { ProtoSpec.of(config.blurDp, config.dispersion, dark) }
  val body = remember(shaders, variant) { shaders.body(variant) }
  val light = remember(shaders, variant, config.light) { if (config.light) shaders.light(variant) else null }
  val layout: ProtoLayout = remember(pieces) {
    { width, height, density ->
      val margin = PiecesMarginDp * density
      val gap = PiecesGapDp * density
      val pieceWidth = (width - 2f * margin - gap * (pieces - 1)) / pieces
      val pieceHeight = height - 2f * margin
      for (index in 0 until pieces) {
        add(
          centerX = margin + pieceWidth * (index + 0.5f) + gap * index,
          centerY = height / 2f,
          halfWidth = pieceWidth / 2f,
          halfHeight = pieceHeight / 2f,
          radius = min(pieceWidth, pieceHeight) / 2f,
        )
      }
    }
  }
  Box(
    modifier = Modifier
      .widthIn(max = PiecesMaxWidth)
      .fillMaxWidth()
      .padding(horizontal = 8.dp)
      .height(PiecesHeight + (PiecesMarginDp * 2f).dp)
      .protoGlass(
        source = session.sources.layer,
        body = body,
        light = light,
        spec = spec,
        mount = config.mount,
        shape = RectangleShape,
        marginDp = PiecesMarginDp,
        scale = config.scale,
        shadow = false,
        drive = drive,
        layout = layout,
      ),
  )
}

/** Il termine di paragone: oggi N pezzi sono N lastre, ognuna con la sua cattura e la sua catena. */
@Composable
private fun TodayPieces(session: MeasureSession, drive: ProtoDrive) {
  val tint = GlassDefaults.floatingTint()
  Row(
    modifier = Modifier
      .widthIn(max = PiecesMaxWidth)
      .fillMaxWidth()
      .padding(horizontal = (8f + PiecesMarginDp).dp)
      .height(PiecesHeight),
    horizontalArrangement = Arrangement.spacedBy(PiecesGapDp.dp),
  ) {
    repeat(session.config.pieces) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .glassSurface(
            state = session.sources.glass,
            tint = tint,
            shape = CircleShape,
            intensity = drive.materialize,
            role = GlassRole.Floating,
            pressed = drive.press,
            nestInContainer = false,
          ),
      )
    }
  }
}

private val TwoPi = (2.0 * PI).toFloat()

/** Un'onda fra 0 e 1 che parte da 0: lo stesso valore allo stesso istante, a ogni lancio. */
private fun wave(seconds: Float, period: Float): Float = 0.5f - 0.5f * cos(TwoPi * seconds / period)

/** Che cosa cambia a ogni fotogramma, in funzione del tempo della scena. */
private fun driveFor(animation: MeasureAnimation, clock: MeasureClock): ProtoDrive = when (animation) {
  MeasureAnimation.None -> ProtoDrive()
  MeasureAnimation.Press -> ProtoDrive(press = { wave(clock.seconds, 0.8f) })
  MeasureAnimation.Appear -> ProtoDrive(
    materialize = { wave(clock.seconds, 1.2f) },
    sharpWeight = { 1f - wave(clock.seconds, 1.2f) },
  )
  MeasureAnimation.Glow -> ProtoDrive(
    glow = { 0.6f },
    glowAt = { 0.5f + 0.35f * sin(TwoPi * clock.seconds / 1.5f) },
  )
}

// --- (e): il bordo dello scorrimento -----------------------------------------------------------------

@Composable
private fun EdgeScene(session: MeasureSession) {
  val config = session.config
  val shaders = session.shaders
  val twoPass = config.strip == MeasureStrip.TwoPass
  val available = shaders != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  MeasureStage(config, session.clock, session.sources) {
    when (config.strip) {
      MeasureStrip.TwoPass -> {
        if (shaders != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (config.edges != MeasureEdges.Bottom) EdgeStrip(session, shaders, fromBottom = false)
          if (config.edges != MeasureEdges.Top) EdgeStrip(session, shaders, fromBottom = true)
        }
      }
      // La fascia di oggi esiste solo in alto: sfuma verso il basso, e non ha una gemella rovesciata.
      MeasureStrip.Today -> Box(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .height(config.stripHeightDp.dp)
          .glassSurface(
            state = session.sources.glass,
            tint = GlassDefaults.barTint(),
            falloff = GlassFalloff.FadeDown,
            nestInContainer = false,
          ),
      )
      MeasureStrip.None -> Unit
    }
  }
  LaunchedEffect(Unit) {
    if (twoPass && !available) {
      session.report.line("FINE ${config.key} | $ProtoUnavailable")
    } else {
      runMeasureWindows(config, session.meter, session.report)
    }
  }
}

private val RectangleShapeBlock: () -> Shape = { RectangleShape }

/**
 * La striscia del bordo (piano 3.10): nessuna sagoma, nessuna lente. Una cattura semplice
 * (`drawPlainBackdrop`) alla risoluzione chiesta, e due nodi runtime in catena, orizzontale poi
 * verticale, con il raggio che cresce verso il bordo dello schermo.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun BoxScope.EdgeStrip(session: MeasureSession, shaders: ProtoSources, fromBottom: Boolean) {
  val config = session.config
  val palette = measurePalette()
  val source = shaders.edge
  val effects: BackdropEffectScope.() -> Unit = remember(config.blurDp, fromBottom, palette, source) {
    {
      if (size.isSpecified) {
        measureSection("Misure.bordo") {
          val radiusPx = config.blurDp.dp.toPx()
          val width = size.width
          val height = size.height
          runtimeShaderEffect("misure-bordo-h", source, "content") {
            edgeUniforms(width, height, horizontal = true, radiusPx, fromBottom, Color.Transparent)
          }
          // Il velo verso il colore del fondo sta nella seconda passata, come nel piano.
          runtimeShaderEffect("misure-bordo-v", source, "content") {
            edgeUniforms(width, height, horizontal = false, radiusPx, fromBottom, palette.page.copy(alpha = 0.35f))
          }
        }
      }
    }
  }
  Box(
    modifier = Modifier
      .align(if (fromBottom) Alignment.BottomCenter else Alignment.TopCenter)
      .fillMaxWidth()
      .height(config.stripHeightDp.dp)
      .drawPlainBackdrop(
        backdrop = session.sources.layer,
        shape = RectangleShapeBlock,
        effects = effects,
        backdropScale = config.scale,
        nestInContainer = false,
      ),
  )
}

private fun RuntimeShader.edgeUniforms(
  width: Float,
  height: Float,
  horizontal: Boolean,
  radiusPx: Float,
  fromBottom: Boolean,
  veil: Color,
) {
  setFloatUniform("size", width, height)
  setFloatUniform("direction", if (horizontal) 1f else 0f, if (horizontal) 0f else 1f)
  setFloatUniform("maxRadius", radiusPx)
  setFloatUniform("fromBottom", if (fromBottom) 1f else 0f)
  setColorUniform("veilColor", veil)
}

// --- (f): la lettura della miniatura ------------------------------------------------------------------

/**
 * Ciò che farà `GlassProbe` (piano 3.9), ridotto all'osso: una miniatura del fondale registrata in un
 * layer piccolo, letta con `toImageBitmap()` ogni [MeasureConfig.periodMillis], e i suoi pixel copiati
 * per farne la luminanza media. Si misurano i due tempi, e intanto i fotogrammi dello schermo.
 */
@Composable
private fun ProbeScene(session: MeasureSession) {
  val config = session.config
  val lumas = remember { FloatArray(4) { Float.NaN } }
  MeasureStage(config, session.clock, session.sources) {
    Row(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(bottom = session.bottomInset),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      repeat(config.probes) { index -> ProbeBox(session, index, lumas) }
    }
  }
  LaunchedEffect(Unit) {
    runMeasureWindows(config, session.meter, session.report) { window ->
      val snapshot = window.samples["snapshot"]
      val read = window.samples["read"]
      val failed = window.samples["failed"]?.count ?: 0
      val luma = lumas.take(config.probes).joinToString(",") { if (it.isNaN()) "-" else "%.3f".format(java.util.Locale.ROOT, it) }
      "snapshot_ms ${snapshot?.millis() ?: "-"} n=${snapshot?.count ?: 0}" +
        " | read_ms ${read?.millis() ?: "-"} | failed=$failed luma=$luma"
    }
  }
}

/** Ciò che il disegno lascia alla lettura: dove sta il riquadro, e se la miniatura è stata registrata. */
private class ProbeHolder {
  var coordinates: LayoutCoordinates? = null
  var recorded = false
  var warned = false
}

@Composable
private fun ProbeBox(session: MeasureSession, index: Int, lumas: FloatArray) {
  val config = session.config
  val source: LayerBackdrop = session.sources.layer
  val layer = rememberGraphicsLayer()
  val holder = remember { ProbeHolder() }
  val outline = measurePalette().accent
  val side = config.probeSide
  Box(
    modifier = Modifier
      .size(120.dp)
      .onGloballyPositioned { holder.coordinates = it }
      .drawWithContent {
        // La miniatura si registra quando il riquadro si disegna, cioè quando cambia la sua geometria:
        // tiene il fondale per riferimento, quindi dopo basta rileggerla.
        val coordinates = holder.coordinates
        val drawable = size.width > 0f && size.height > 0f
        if (drawable && coordinates != null && coordinates.isAttached && source.isReadyToSample()) {
          val scaleX = side / size.width
          val scaleY = side / size.height
          val asDensity = this
          layer.record(IntSize(side, side)) {
            scale(scaleX, scaleY, pivot = Offset.Zero) {
              with(source) { drawBackdrop(asDensity, coordinates, null) }
            }
          }
          holder.recorded = true
        }
        drawContent()
        drawRect(color = outline, style = Stroke(width = 1.dp.toPx()))
      },
  )
  LaunchedEffect(layer, config) {
    // Più sonde non leggono tutte nello stesso fotogramma: è così che le schedulerà l'engine.
    delay(config.periodMillis * index / config.probes)
    while (true) {
      delay(config.periodMillis)
      if (!holder.recorded) continue
      try {
        val started = System.nanoTime()
        val image = measureAsyncSection("Misure.miniatura", index) {
          if (config.thread == MeasureThread.Background) {
            withContext(Dispatchers.Default) { layer.toImageBitmap() }
          } else {
            layer.toImageBitmap()
          }
        }
        val snapped = System.nanoTime()
        val luma = if (config.thread == MeasureThread.Main) {
          meanLuma(image)
        } else {
          withContext(Dispatchers.Default) { meanLuma(image) }
        }
        val read = System.nanoTime()
        session.meter?.sample("snapshot", snapped - started)
        session.meter?.sample("read", read - snapped)
        lumas[index] = luma
      } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
      } catch (error: Exception) {
        session.meter?.sample("failed", 0L)
        if (!holder.warned) {
          holder.warned = true
          Log.w(MeasureLogTag, "Lettura della miniatura fallita (${config.thread.key}): $error")
        }
      }
    }
  }
}

/**
 * La luminanza media (0..1) di una miniatura. `toImageBitmap()` dà un'immagine che vive sulla GPU: per
 * leggerne i pixel va copiata in memoria, ed è quella copia la parte che può costare.
 */
private fun meanLuma(image: ImageBitmap): Float {
  val bitmap = image.asAndroidBitmap()
  val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
  if (readable == null || readable.width <= 0 || readable.height <= 0) return Float.NaN
  val pixels = IntArray(readable.width * readable.height)
  readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
  var sum = 0f
  for (pixel in pixels) {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    sum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
  }
  if (readable !== bitmap) readable.recycle()
  return sum / pixels.size
}

// --- (g): la prima compilazione ------------------------------------------------------------------------

private class CompileStep(val name: String, val source: String)

/** Quanto resta in scena una variante prima che arrivi la successiva: abbastanza fotogrammi per la mediana «a regime». */
private const val CompileStepMillis = 700L
private val CompileVariants = intArrayOf(1, 2, 4, 8)
private val CompileWidth = 156.dp
private val CompileHeight = 56.dp

/**
 * Ogni variante di shader entra in scena da sola, a distanza di [CompileStepMillis]: prima il corpo a
 * 1, 2, 4, 8 pezzi e la sua luce, poi la striscia del bordo. Per ognuna si prendono il tempo di
 * costruzione del programma (`RuntimeShader(sorgente)`, sul filo principale) e il fotogramma in cui si
 * disegna per la prima volta, accanto alla mediana dei fotogrammi che seguono.
 *
 * La prima compilazione vera è una sola per installazione: il sistema tiene i programmi compilati su
 * disco. Perché il numero sia quello a freddo, prima del lancio va svuotata la cache dell'app
 * (`pm clear`: vedi LEGGIMI.md).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun CompileScene(session: MeasureSession, shaders: ProtoSources) {
  val config = session.config
  val steps = remember(shaders) {
    buildList {
      for (pieces in CompileVariants) {
        add(CompileStep("body$pieces", shaders.body(pieces).source))
        add(CompileStep("light$pieces", shaders.light(pieces).source))
      }
      add(CompileStep("edge", shaders.edge))
    }
  }
  var shown by remember { mutableIntStateOf(0) }
  val drive = remember { ProtoDrive() }

  MeasureStage(config, session.clock, session.sources) {
    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(bottom = session.bottomInset),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      CompileVariants.forEachIndexed { index, pieces ->
        val bodyStep = index * 2
        if (shown > bodyStep) {
          CompileElement(session, shaders, pieces, withLight = shown > bodyStep + 1, drive = drive)
        } else {
          Spacer(Modifier.size(CompileWidth, CompileHeight))
        }
      }
    }
    if (shown > steps.lastIndex) EdgeStrip(session, shaders, fromBottom = false)
  }

  LaunchedEffect(Unit) {
    val report = session.report
    val meter = session.meter
    report.line("AVVIO ${config.key} | steps=${steps.size} step_ms=$CompileStepMillis warmup_s=${config.warmupSeconds}")
    if (meter == null) {
      report.line("FINE ${config.key} | senza finestra dell'attivita': nessun tempo di fotogramma")
      return@LaunchedEffect
    }
    delay((config.warmupSeconds * 1000f).toLong())

    // Le sorgenti dell'engine: quanto costa provarle tutte (solo quelle che nessuno ha ancora usato).
    val engineStarted = System.nanoTime()
    val verdicts = FluidGlassDiagnostics.compileAllShaders()
    val engineNanos = System.nanoTime() - engineStarted
    val refused = verdicts.filterValues { it != null }.keys
    report.line(
      "COMPILA scene=compile variant=engine-all build_ms=${MeasureSummary.millis(engineNanos)}" +
        " sources=${verdicts.size} refused=${if (refused.isEmpty()) "none" else refused.joinToString(",")}",
    )

    val intro = LongArray(steps.size)
    val build = LongArray(steps.size)
    var refusedAt = -1
    val seconds = (steps.size * CompileStepMillis + 1500L) / 1000f
    val window = meter.collect("Misure compile", measureCapacity(seconds)) {
      delay(600L)
      for (index in steps.indices) {
        val step = steps[index]
        val started = System.nanoTime()
        val error = measureSection("Misure.compila ${step.name}") { protoCompileError(step.source) }
        build[index] = System.nanoTime() - started
        if (error != null) {
          // Una variante che il driver rifiuta non si può disegnare: la sequenza si ferma qui, e lo dice.
          Log.e(MeasureLogTag, "Variante ${step.name} rifiutata dal compilatore del dispositivo: $error")
          refusedAt = index
          break
        }
        intro[index] = withFrameNanos { now ->
          shown = index + 1
          now
        }
        delay(CompileStepMillis)
      }
      delay(400L)
    }

    val frames = window.frames
    val interval = (1_000_000_000.0 / meter.refreshHz).toLong()
    val measured = if (refusedAt >= 0) refusedAt else steps.size
    if (refusedAt >= 0) {
      report.line("COMPILA scene=compile variant=${steps[refusedAt].name} REFUSED: sequenza interrotta, vedi logcat")
    }
    for (index in 0 until measured) {
      // Il fotogramma del primo disegno è quello del vsync in cui la variante è entrata; la finestra è
      // larga qualche fotogramma perché i due orologi possono non coincidere al nanosecondo.
      val from = intro[index] - interval
      val to = intro[index] + 5L * interval
      val steadyFrom = intro[index] + 8L * interval
      val steadyTo = intro[index] + CompileStepMillis * 1_000_000L - 2L * interval
      report.line(
        "COMPILA scene=compile variant=${steps[index].name}" +
          " build_ms=${MeasureSummary.millis(build[index])}" +
          " first_total_ms=${orMissing(frames.maxBetween(frames.total, from, to))}" +
          " first_ui_ms=${orMissing(frames.maxBetween(frames.ui, from, to))}" +
          " first_render_ms=${orMissing(frames.maxBetween(frames.render, from, to))}" +
          " first_gpu_ms=${orMissing(frames.maxBetween(frames.gpu, from, to))}" +
          " steady_total_ms=${orMissing(frames.medianBetween(frames.total, steadyFrom, steadyTo))}",
      )
    }
    report.line("FINE ${config.key} | frames=${frames.count} dropped=${frames.dropped}")
  }
}

private fun orMissing(nanos: Long): String = if (nanos < 0L) "n/d" else MeasureSummary.millis(nanos)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun CompileElement(
  session: MeasureSession,
  shaders: ProtoSources,
  pieces: Int,
  withLight: Boolean,
  drive: ProtoDrive,
) {
  val config = session.config
  val dark = GlassDefaults.isDarkSurface()
  val spec = remember(config.blurDp, config.dispersion, dark) { ProtoSpec.of(config.blurDp, config.dispersion, dark) }
  val body = remember(shaders, pieces) { shaders.body(pieces) }
  val light = remember(shaders, pieces, withLight) { if (withLight) shaders.light(pieces) else null }
  val layout: ProtoLayout = remember {
    { width, height, _ -> add(width / 2f, height / 2f, width / 2f, height / 2f, min(width, height) / 2f) }
  }
  Box(
    modifier = Modifier
      .size(CompileWidth, CompileHeight)
      .protoGlass(
        source = session.sources.layer,
        body = body,
        light = light,
        spec = spec,
        mount = MeasureMount.One,
        shape = CircleShape,
        marginDp = 0f,
        scale = 1f,
        shadow = false,
        drive = drive,
        layout = layout,
      ),
  )
}

// --- (h): Fluid-physics, Full e Balanced ------------------------------------------------------------------

/**
 * Una superficie di Fluid-physics che viaggia in ciclo fra due forme, con il livello chiesto da
 * [MeasureConfig.tier]. Il livello si può forzare solo qui, dove la superficie la crea la galleria: i
 * componenti dell'engine (menu, barra) chiamano `fluidPhysicsSurface` da sé e non lo espongono.
 */
@Composable
private fun PhysicsScene(session: MeasureSession) {
  val config = session.config
  val density = LocalDensity.current
  MeasureStage(config, session.clock, session.sources) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val dp = density.density
        val forms = remember(width, height, dp, config.journey) { physicsForms(config.journey, width, height, dp) }
        val state = rememberFluidPhysicsState(forms.first)
        LaunchedEffect(forms) {
          state.snapTo(forms.first)
          while (true) {
            state.morphTo(forms.second)
            delay(260L)
            state.morphTo(forms.first)
            delay(260L)
          }
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .fluidPhysicsSurface(
              state = state,
              backdrop = session.sources.glass,
              tint = GlassDefaults.floatingTint(),
              role = GlassRole.Floating,
              tier = config.tier,
            ),
        )
      }
    }
  }
  LaunchedEffect(Unit) {
    // Dopo l'attesa la superficie ha già chiesto i suoi verdetti: si scrive che cosa il dispositivo le
    // ha concesso, perché «Full chiesto» e «Full ottenuto» non sono la stessa cosa.
    delay(1000L)
    val verdicts = FluidGlassDiagnostics.shaderVerdicts
      .filterKeys { it.startsWith("SlabGroup") || it.startsWith("PolyMorph") }
      .entries.joinToString(",") { (name, error) -> "$name=${if (error == null) "ok" else "RIFIUTATO"}" }
    session.report.line(
      "FISICA tier_requested=${config.tier?.name?.lowercase() ?: "auto"} sdk=${Build.VERSION.SDK_INT}" +
        " verdicts=${verdicts.ifEmpty { "none" }}",
    )
    runMeasureWindows(config, session.meter, session.report)
  }
}

/** Le due forme di un viaggio, in pixel del palco. */
private fun physicsForms(journey: MeasureJourney, width: Float, height: Float, density: Float): Pair<FluidForm, FluidForm> {
  val centerX = width / 2f
  val centerY = height * 0.42f
  val shortSide = min(width, height)
  val panelRadius = 28f * density
  return when (journey) {
    MeasureJourney.Square -> {
      val half = shortSide * 0.25f
      FluidForm.circle(Offset(centerX, centerY), shortSide * 0.15f) to
        FluidFormPresets.square(Rect(centerX - half, centerY - half, centerX + half, centerY + half), panelRadius)
    }
    MeasureJourney.Panel -> {
      val top = centerY - 160f * density
      FluidFormPresets.capsule(Rect(centerX - 78f * density, top, centerX + 78f * density, top + 56f * density)) to
        FluidFormPresets.rectangle(
          Rect(centerX - 120f * density, top, centerX + 120f * density, top + 320f * density),
          panelRadius,
        )
    }
    MeasureJourney.Pieces -> {
      val radius = 26f * density
      val apart = 64f * density
      FluidForm.Group(
        pieces = listOf(
          FluidForm.circle(Offset(centerX - apart, centerY), radius),
          FluidForm.circle(Offset(centerX + apart, centerY), radius),
        ),
        blendRadius = 24f * density,
      ) to FluidFormPresets.rectangle(
        Rect(centerX - 120f * density, centerY - 90f * density, centerX + 120f * density, centerY + 90f * density),
        panelRadius,
      )
    }
  }
}
