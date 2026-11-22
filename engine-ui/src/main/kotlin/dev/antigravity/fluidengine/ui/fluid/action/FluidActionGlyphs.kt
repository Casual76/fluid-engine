package dev.antigravity.fluidengine.ui.fluid.action

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurEffect
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSmoothstep
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowDownward
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowUpward
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import kotlin.math.PI
import kotlin.math.sin

/** I tempi si riferiscono al glifo. La soglia, la tenuta e la scossa del contenuto sono in [FluidActionProfile]. */
internal object FluidActionGlyphProfile {
  /** F6B.0 §6, UIKit 243 f586–596. Alta sui fotogrammi, media per Salva. */
  const val FillMs = 67
  // Salva: chiusura del tratto nei primi 48 ms, poi scomparsa prima del Draw (scelta visiva).
  const val SaveCloseMs = 48
  const val SaveDrawStartMs = 67
  fun saveClosure(ms: Float) = glassContentSmoothstep(0f, SaveCloseMs.toFloat(), ms)
  fun saveRingAlpha(ms: Float) = 1f - glassContentSmoothstep(SaveCloseMs.toFloat(), SaveDrawStartMs.toFloat(), ms)
  fun saveReveal(ms: Float) = checkReveal(ms - SaveDrawStartMs)

  /**
   * L'icona di Salva che esce mentre la spunta si disegna: le curve di chi esce di R7 sul loro T (300 ms,
   * [GlassContentDefaults.DurationMs]), sparita a 0,66 T ≈ 198 ms. Così la spunta, che comincia a [SaveDrawStartMs],
   * entra mentre l'icona è ancora lì, sfocata, e non resta un attimo di vetro vuoto (scelta di Alessio del 22 novembre
   * 2026, sulle riprese di F6B.12: prima l'icona usciva tutta nei primi 67 ms). R7, alta, come ogni scambio d'icona.
   */
  val SaveBaseExitMs: Float = GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd
  fun saveBaseAlpha(ms: Float) = GlassContentCurves.exitAlpha.at((ms / GlassContentDefaults.DurationMs).coerceAtLeast(0f))
  fun saveBaseBlur(ms: Float) = GlassContentCurves.blurRise((ms / GlassContentDefaults.DurationMs).coerceAtLeast(0f))
  const val DrawMs = 267
  const val CheckWidth = .46f
  /** Caduta della freccia prima della spunta: a occhio, 21 novembre 2026. */
  const val DropMs = 144
  const val DropDistance = .48f
  /** A occhio, 21 novembre 2026: le cifre lasciano spazio alla freccia nel primo 42% della caduta. */
  const val DropClearDigits = .42f
  /** F6B.0 §6, Wiggle 315°, spostamento per asse 16/308. Alta sulla misura, bassa per il lavoro. */
  const val ChargeAxis = 16f / 308f
  /** Anticipazione trattenuta, corsa di uscita e rientro: a occhio, 21 novembre 2026. */
  const val ChargeMs = 180
  const val LaunchMs = 190
  const val ReturnMs = 160
  const val LaunchAxis = .65f
  /** Il cuore cresce dell'11% e torna alla taglia di riposo: a occhio, 21 novembre 2026. */
  const val HeartMs = 236
  const val HeartGrowth = .11f
  /** Copia mostra subito la nuova sagoma e la mette a fuoco. A occhio, 21 novembre 2026. */
  const val CopyFocusMs = 112

  fun heartScale(t: Float): Float = 1f + HeartGrowth * sin(PI * t.coerceIn(0f, 1f)).toFloat()
  fun fill(timeMs: Float, transfer: Boolean): Float {
    val start = if (transfer) DropMs.toFloat() else 0f
    return glassContentSmoothstep(start, start + FillMs, timeMs)
  }

  fun reveal(timeMs: Float, transfer: Boolean): Float {
    val start = FillMs + if (transfer) DropMs else 0
    return checkReveal(timeMs - start)
  }

  private fun checkReveal(elapsed: Float): Float {
    val t = (elapsed / DrawMs).coerceIn(0f, 1f)
    // Primo braccio breve, poi salita che si posa: 243, due bracci già visibili a +133 ms (fiducia media).
    return 1f - (1f - t) * (1f - t)
  }
}

internal object FluidActionGlyphs {
  fun of(kind: FluidActionKind): FluidActionGlyph? = when (kind) {
    FluidActionKind.Send -> SendGlyph
    FluidActionKind.Copy -> CopyGlyph
    FluidActionKind.Favorite -> FavoriteGlyph
    FluidActionKind.Save -> SaveGlyph
    FluidActionKind.Download, FluidActionKind.Upload -> DiscGlyph
    FluidActionKind.Custom -> null
  }
}

private val FluidActionFace.working get() = ring != FluidActionRing.None
private val FluidActionFace.checked get() = icon == FluidIcons.Check && ink == FluidActionInk.Content
private val FluidActionFace.transfer get() = kind == FluidActionKind.Download || kind == FluidActionKind.Upload

@Composable
private fun Ring(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook) {
  if (face.working) FluidActionFaceDrawing(face.copy(icon = null, digits = false), progress, look)
}

private object SendGlyph : FluidActionGlyph {
  @Composable
  override fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(modifier.requiredSize(look.iconSize * FluidActionProfile.RingDiameter).clipToBounds(), contentAlignment = Alignment.Center) {
      Ring(face, progress, look)
      FluidGlassContentTransition(targetState = face, key = { it.icon }, blurReference = look.iconSize) { shown ->
        val symbol = shown.icon ?: return@FluidGlassContentTransition
        if (symbol == FluidIcons.Send) {
          val target = when {
            look.reducedMotion -> 0f
            face.checked -> FluidActionGlyphProfile.LaunchAxis
            face.working -> -FluidActionGlyphProfile.ChargeAxis
            else -> 0f
          }
          val time = when {
            face.checked -> FluidActionGlyphProfile.LaunchMs
            face.working -> FluidActionGlyphProfile.ChargeMs
            else -> FluidActionGlyphProfile.ReturnMs
          }
          val axis by animateFloatAsState(target, tween(time), label = "send diagonal")
          val targetScale = if (face.working) FluidActionProfile.WorkingIconScale else 1f
          val scale by animateFloatAsState(targetScale, tween(time), label = "send inside ring")
          FluidIcon(symbol, null, Modifier.graphicsLayer {
            rotationZ = -45f * direction
            scaleX = if (look.reducedMotion) targetScale else scale
            scaleY = scaleX
            val offset = if (look.reducedMotion) 0f else axis * look.iconSize.toPx()
            translationX = direction * offset
            translationY = -offset
          }, tint = look.ink, size = look.iconSize)
        } else {
          FluidIcon(symbol, null, tint = if (shown.ink == FluidActionInk.Error) look.error else look.ink, size = look.iconSize)
        }
      }
    }
  }
}

/** Copia è istantaneo: l'esecutore governa il secondo di tenuta. */
private object CopyGlyph : FluidActionGlyph {
  @Composable
  override fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier) {
    val previous = remember { arrayOf(face) }
    val focus = remember(face, look.reducedMotion) {
      val change = previous[0] != face && !look.reducedMotion
      previous[0] = face
      Animatable(if (change) 0f else 1f)
    }
    LaunchedEffect(focus) {
      if (focus.value < 1f) focus.animateTo(1f, tween(FluidActionGlyphProfile.CopyFocusMs, easing = LinearEasing))
    }
    Box(modifier.graphicsLayer {
      if (Build.VERSION.SDK_INT >= 31 && !look.reducedMotion) {
        val sigma = GlassContentDefaults.PeakBlur * (1f - focus.value)
        renderEffect = glassContentBlurEffect(glassContentSigmaPx(sigma, look.iconSize.toPx()))
      }
    }) { FluidActionFaceDrawing(face, progress, look) }
  }
}

private object FavoriteGlyph : FluidActionGlyph {
  @Composable
  override fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier) {
    val symbol = face.icon ?: return
    val bounce = remember { Animatable(1f) }
    val previous = remember { arrayOf(symbol) }
    LaunchedEffect(symbol, face.ink, look.reducedMotion) {
      val changed = previous[0] != symbol
      previous[0] = symbol
      if (changed && face.ink == FluidActionInk.Content && !look.reducedMotion) {
        bounce.snapTo(0f)
        bounce.animateTo(1f, tween(FluidActionGlyphProfile.HeartMs, easing = LinearEasing))
      } else bounce.snapTo(1f)
    }
    Box(modifier.requiredSize(look.iconSize * FluidActionProfile.RingDiameter), contentAlignment = Alignment.Center) {
      FluidIcon(symbol, null, Modifier.graphicsLayer {
        val scale = if (look.reducedMotion) 1f else FluidActionGlyphProfile.heartScale(bounce.value)
        scaleX = scale
        scaleY = scale
      }, tint = if (face.ink == FluidActionInk.Error) look.error else look.ink, size = look.iconSize)
    }
  }
}

/** Entrando nel successo manteniamo lo stesso anello. Uscendone, R7 scambia la spunta con la nuova icona. */
private object SaveGlyph : FluidActionGlyph {
  @Composable
  override fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier) {
    if (look.reducedMotion) {
      Box(modifier) { FluidActionFaceDrawing(face, progress, look) }
      return
    }
    val sequence = remember { arrayOf(Any()) }
    val lastRest = remember { arrayOf(face) }
    val group = remember(face.checked) {
      // Con l'anello la spunta si disegna dove l'anello si chiude (anello → spunta, UIKit 243). Senza anello (un lavoro
      // sotto la soglia dei 300 ms) non c'è niente da chiudere: la spunta entra con lo scambio sfocato di R7 mentre
      // l'icona esce, sovrapposte, invece di aspettare la fine dell'icona (scelta di Alessio del 22 novembre 2026).
      if (!face.checked || !lastRest[0].working) sequence[0] = Any()
      sequence[0]
    }
    if (!face.checked) lastRest[0] = face
    FluidGlassContentTransition(group to face, modifier, key = { it.first }, blurReference = look.iconSize) { (_, shown) ->
      SaveSequence(shown, progress, look)
    }
  }
}

@Composable
private fun SaveSequence(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook) {
  val profile = FluidActionGlyphProfile
  val total = profile.SaveDrawStartMs + profile.DrawMs
  val time by animateFloatAsState(if (face.checked) total.toFloat() else 0f, tween(total, easing = LinearEasing), label = "save check")
  val lastBase = remember { arrayOf(face) }
  if (!face.checked) lastBase[0] = face
  val base = lastBase[0]
  val showBase by remember { derivedStateOf { time < profile.SaveBaseExitMs } }
  Box(Modifier.requiredSize(look.iconSize * FluidActionProfile.RingDiameter), contentAlignment = Alignment.Center) {
    if (showBase && !base.checked) {
      if (base.working) Box(Modifier.graphicsLayer { alpha = profile.saveRingAlpha(time) }) {
        FluidActionFaceDrawing(base.copy(icon = null, digits = false), progress, look, ringClosure = { profile.saveClosure(time) })
      }
      FluidGlassContentTransition(base, key = { it.copy(paused = false) }, blurReference = look.iconSize) { shown ->
        Box(Modifier.graphicsLayer {
          // L'icona esce con le curve di R7 e si sovrappone alla spunta che comincia a disegnarsi (saveBaseAlpha).
          alpha = profile.saveBaseAlpha(time)
          if (Build.VERSION.SDK_INT >= 31) renderEffect = glassContentBlurEffect(
            glassContentSigmaPx(GlassContentDefaults.PeakBlur * profile.saveBaseBlur(time), look.iconSize.toPx()))
        }) { FluidActionFaceDrawing(shown.copy(ring = FluidActionRing.None), progress, look) }
      }
    }
    if (face.checked) {
      val check = remember { MaskedCheck() }
      Canvas(Modifier.requiredSize(look.iconSize)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
          check.draw(this, profile.saveReveal(time), look.ink, BlendMode.SrcOver)
        }
      }
    }
  }
}

/** Lo stesso disco conserva la misura dell'anello. L'orologio si può invertire anche a metà della rivelazione. */
private object DiscGlyph : FluidActionGlyph {
  @Composable
  override fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier) {
    key(face.kind, look.reducedMotion) {
      val transfer = face.transfer
      val total = FluidActionGlyphProfile.FillMs + FluidActionGlyphProfile.DrawMs + if (transfer) FluidActionGlyphProfile.DropMs else 0
      val target = if (face.checked) 1f else 0f
      val phase = if (look.reducedMotion) null else animateFloatAsState(target,
        tween(if (face.checked) total else FluidActionGlyphProfile.ReturnMs, easing = LinearEasing), label = "action check")
      val time = { (phase?.value ?: target) * total }
      // Da uno stato già riuscito (rotazione) si entra in posa. Durante la caduta conserviamo la freccia che c'era.
      val lastBase = remember { arrayOf(face) }
      if (!face.checked) lastBase[0] = face
      val base = lastBase[0]
      val showBase by remember(face.checked, look.reducedMotion) { derivedStateOf { !face.checked || time() < total } }
      Box(modifier.requiredSize(look.iconSize * FluidActionProfile.RingDiameter), contentAlignment = Alignment.Center) {
        if (showBase && !base.checked) {
          Ring(base, progress, look)
          FluidGlassContentTransition(base, key = {
            if (it.transfer && it.ink != FluidActionInk.Error) it.kind else it.copy(paused = false)
          }, blurReference = look.iconSize) { shown ->
            if (shown.transfer && shown.ink != FluidActionInk.Error) TransferSymbol(shown, progress, look, time)
            else Box(Modifier.graphicsLayer {
              val u = (time() / FluidActionGlyphProfile.FillMs).coerceIn(0f, 1f)
              alpha = 1f - glassContentSmoothstep(0f, 1f, u)
              if (Build.VERSION.SDK_INT >= 31) renderEffect = glassContentBlurEffect(
                glassContentSigmaPx(GlassContentDefaults.PeakBlur * GlassContentCurves.blurRise(u), look.iconSize.toPx()))
            }) { FluidActionFaceDrawing(shown.copy(ring = FluidActionRing.None), progress, look) }
          }
        }
        val showDisc by remember(face.checked, look.reducedMotion) { derivedStateOf { face.checked || time() > 0f } }
        if (showDisc) CheckDisc(look, transfer, time)
      }
    }
  }
}

@Composable
private fun TransferSymbol(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, time: () -> Float) {
  val arrow = if (face.kind == FluidActionKind.Upload) FluidIcons.ArrowUpward else FluidIcons.ArrowDownward
  val box = look.iconSize * FluidActionProfile.RingDiameter
  Box(Modifier.requiredSize(box), contentAlignment = Alignment.Center) {
    FluidIcon(arrow, null, Modifier.graphicsLayer {
      val u = (time() / FluidActionGlyphProfile.DropMs).coerceIn(0f, 1f)
      val fading = glassContentSmoothstep(FluidActionGlyphProfile.DropClearDigits, 1f, u)
      // La stessa freccia conserva posto e taglia dall'inizio dell'arco fino all'esito.
      translationY = -box.toPx() * .07f + u * look.iconSize.toPx() * FluidActionGlyphProfile.DropDistance
      alpha = 1f - fading
      if (Build.VERSION.SDK_INT >= 31) renderEffect = glassContentBlurEffect(
        glassContentSigmaPx(GlassContentDefaults.PeakBlur * fading, look.iconSize.toPx()))
    }, tint = look.ink, size = look.iconSize * .80f)
    if (face.digits) {
      val measure = rememberTextMeasurer()
      val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
        fontSize = 10.sp, letterSpacing = 0.sp)
      val widest = remember(measure, style) { measure.measure("100%", style).size }
      Canvas(Modifier.requiredSize(box).graphicsLayer {
        alpha = 1f - glassContentSmoothstep(0f, FluidActionGlyphProfile.DropClearDigits, time() / FluidActionGlyphProfile.DropMs)
      }) {
        val text = measure.measure("${fluidActionPercent(progress.read())}%", style)
        val scale = minOf(size.width * .65f / widest.width, size.height * .28f / widest.height)
        withTransform({
          translate(center.x, size.height * .76f)
          scale(scale, scale, Offset.Zero)
        }) { drawText(text, look.ink, Offset(-text.size.width / 2f, -text.size.height / 2f)) }
      }
    }
  }
}

/** Maschera nel viewport 24 della spunta dell'engine. Il pennello segue la mezzeria, il contorno pieno non viene tagliato. */
internal object FluidCheckMask {
  val start = Offset(5.29f, 12.29f)
  val elbow = Offset(9.55f, 16.55f)
  val end = Offset(18.72f, 7.39f)
  val elbowAt = (elbow - start).getDistance() / ((elbow - start).getDistance() + (end - elbow).getDistance())

  fun tip(progress: Float): Offset {
    val p = progress.coerceIn(0f, 1f)
    return if (p <= elbowAt) start + (elbow - start) * (p / elbowAt)
    else elbow + (end - elbow) * ((p - elbowAt) / (1f - elbowAt))
  }
}

/** La stessa maschera segue la mezzeria del vettore in entrambi i disegni. */
private class MaskedCheck {
  private val outline = (FluidIcons.Check.root[0] as VectorPath).pathData.toPath()
  private val spine = android.graphics.Path()
  private val mask = android.graphics.Path()
  private val clip = mask.asComposePath()
  private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = 2.7f
  }

  fun draw(scope: DrawScope, p: Float, color: Color, blendMode: BlendMode): Unit = with(scope) {
    if (p >= 1f) drawPath(outline, color, blendMode = blendMode)
    else if (p > 0f) {
      val tip = FluidCheckMask.tip(p)
      spine.rewind()
      spine.moveTo(FluidCheckMask.start.x, FluidCheckMask.start.y)
      if (p > FluidCheckMask.elbowAt) spine.lineTo(FluidCheckMask.elbow.x, FluidCheckMask.elbow.y)
      spine.lineTo(tip.x, tip.y)
      brush.getFillPath(spine, mask)
      clipPath(clip) { drawPath(outline, color, blendMode = blendMode) }
    }
  }
}

@Composable
private fun CheckDisc(look: FluidActionLook, transfer: Boolean, time: () -> Float) {
  val check = remember { MaskedCheck() }
  Canvas(Modifier.requiredSize(look.iconSize * FluidActionProfile.RingDiameter)
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
    val t = time()
    val fill = FluidActionGlyphProfile.fill(t, transfer)
    val drop = if (transfer) (t / FluidActionGlyphProfile.DropMs).coerceIn(0f, 1f) else fill
    val radius = size.minDimension / 2f
    val stroke = maxOf(FluidActionProfile.RingMinStrokeDp.dp.toPx(), look.iconSize.toPx() * FluidActionProfile.RingStroke)
    drawCircle(look.ink, radius - stroke / 2f, alpha = drop, style = Stroke(stroke))
    drawCircle(look.ink, radius, alpha = fill)
    val p = FluidActionGlyphProfile.reveal(t, transfer)
    if (p <= 0f) return@Canvas
    // Le dimensioni del segno pieno sono 15,73 × 11,49 nel viewport. In UIKit il segno è quasi quadrato (160 × 158).
    val width = size.minDimension * FluidActionGlyphProfile.CheckWidth
    withTransform({
      translate(center.x, center.y)
      scale(width / 15.73f, width * (158f / 160f) / 11.49f, Offset.Zero)
      translate(-12f, -12f)
    }) {
      check.draw(this, p, Color.Black, BlendMode.Clear)
    }
  }
}
