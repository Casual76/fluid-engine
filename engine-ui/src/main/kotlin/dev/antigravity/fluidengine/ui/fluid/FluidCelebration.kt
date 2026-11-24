package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

// F8.5 · La festa: i coriandoli di un successo (la scheda `coriandoli`), come l'effetto «Coriandoli» di iMessage.
//
// Costa un solo strato, disegnato in un passaggio: niente un composable per pezzo. Le particelle stanno in vettori
// preparati una volta (struttura di vettori, non oggetti), il passo e il disegno non allocano. Finita la festa lo strato
// esce dalla composizione: zero ridisegni, il RenderNode rilasciato.

/** I numeri della festa, con la loro scheda. */
internal object FluidCelebrationProfile {
  /**
   * Quanti coriandoli: **120** sul telefono di riferimento. La scheda chiede di restare sotto i 60 sui dispositivi deboli:
   * [particlesFor] da' la scala di F7.10 (Pieno, Ridotto, Protetto), che arrivera' collegata con F7.11. Per ora il numero
   * e' un parametro interno dello stato ([FluidCelebrationState.particleCount]). A occhio, 24 novembre 2026, sul confronto
   * con iMessage (`sessioni/f8-5/riferimenti`).
   */
  const val ParticleCount: Int = 120
  const val Capacity: Int = 160

  /** La scala di F7.10: meno coriandoli dove la GPU o la memoria sono strette. */
  fun particlesFor(optics: GlassOptics): Int = when (optics) {
    GlassOptics.Full -> ParticleCount
    GlassOptics.Reduced -> 72
    GlassOptics.Protected -> 48
  }

  /** Gravita' in dp/s²: con la resistenza dell'aria da' una caduta a regime di ~320 dp/s, la pioggia lenta di iMessage. */
  const val GravityDp: Float = 900f

  /** La resistenza dell'aria per pezzo (1/s): leggeri, si fermano in fretta e poi cadono adagio. */
  const val DragMin: Float = 2.2f
  const val DragMax: Float = 3.4f

  /**
   * Quanto la faccia conta nella resistenza: un pezzo di piatto frena quasi il doppio di uno di taglio, cosi' scende a
   * strappi mentre gira, come la carta. `k = drag · (EdgeDrag + FaceDrag · |cos giro|)`.
   */
  const val EdgeDrag: Float = 0.45f
  const val FaceDrag: Float = 0.9f

  /** Il giro 3D (rad/s) e il giro nel piano (°/s). */
  const val FlipMin: Float = 5f
  const val FlipMax: Float = 11f
  const val SpinMax: Float = 260f

  /** L'ondeggiare di lato: accelerazione in dp/s² e pulsazione in rad/s. */
  const val SwayDp: Float = 260f
  const val SwayMin: Float = 2.5f
  const val SwayMax: Float = 4.5f

  /** La pioggia (iMessage): partono sopra il bordo, fino a un terzo di schermo piu' in alto, cosi' arrivano scaglionati. */
  const val RainSpread: Float = 0.35f
  const val RainSpeedMinDp: Float = 140f
  const val RainSpeedMaxDp: Float = 300f

  /** Lo scoppio da un punto (il tasto «Installa»): un ventaglio verso l'alto di ±55°, 700–1500 dp/s. */
  const val BurstHalfAngle: Float = 55f
  const val BurstSpeedMinDp: Float = 700f
  const val BurstSpeedMaxDp: Float = 1500f

  /** La vita massima e la dissolvenza finale di chi non e' ancora uscito dal fondo. */
  const val LifeSeconds: Float = 4.2f
  const val FadeSeconds: Float = 0.5f

  /** Le misure dei pezzi in dp: strisce, quadratini e coriandoli tondi. */
  const val StripWidthDp: Float = 5f
  const val StripLengthDp: Float = 12f
  const val SquareDp: Float = 8f
  const val DotDp: Float = 7f

  /**
   * «Riduci movimento»: niente pioggia. Un segno fermo e breve, i coriandoli sparsi gia' posati, per **900 ms**, poi
   * svaniscono in **250 ms** (una dissolvenza, non un movimento: la regola di iOS). L'aptica resta.
   */
  const val StillHoldMs: Long = 900L
  const val StillFadeMs: Long = 250L
}

/**
 * La simulazione dei coriandoli, in vettori: posizione, velocita', i due giri, l'ondeggiare, la misura, il colore. Pura
 * (niente Android): i test la provano sulla JVM. [step] non alloca.
 */
internal class FluidConfetti(val capacity: Int) {
  val x = FloatArray(capacity)
  val y = FloatArray(capacity)
  val vx = FloatArray(capacity)
  val vy = FloatArray(capacity)
  val spin = FloatArray(capacity)
  val spinSpeed = FloatArray(capacity)
  val flip = FloatArray(capacity)
  val flipSpeed = FloatArray(capacity)
  val sway = FloatArray(capacity)
  val swaySpeed = FloatArray(capacity)
  val width = FloatArray(capacity)
  val length = FloatArray(capacity)
  val drag = FloatArray(capacity)
  val color = IntArray(capacity)
  val shape = IntArray(capacity)
  val alive = BooleanArray(capacity)

  /** Quanti pezzi sono in gioco, e da quanto (s). */
  var count = 0
    private set
  var age = 0f
    private set
  private var gravity = 0f
  private var swayAccel = 0f
  private var bottom = 0f
  private var margin = 0f

  val anyAlive: Boolean
    get() {
      for (i in 0 until count) if (alive[i]) return true
      return false
    }

  private fun common(i: Int, random: Random, density: Float, colors: Int) {
    val p = FluidCelebrationProfile
    val kind = random.nextInt(10)
    // Sette strisce, due quadratini, un tondo su dieci.
    shape[i] = when {
      kind < 7 -> Strip
      kind < 9 -> Square
      else -> Dot
    }
    val k = 0.8f + 0.4f * random.nextFloat()
    when (shape[i]) {
      Strip -> { width[i] = p.StripWidthDp * k * density; length[i] = p.StripLengthDp * k * density }
      Square -> { width[i] = p.SquareDp * k * density; length[i] = width[i] }
      else -> { width[i] = p.DotDp * k * density; length[i] = width[i] }
    }
    color[i] = random.nextInt(colors.coerceAtLeast(1))
    drag[i] = p.DragMin + (p.DragMax - p.DragMin) * random.nextFloat()
    spin[i] = 360f * random.nextFloat()
    spinSpeed[i] = (random.nextFloat() * 2f - 1f) * p.SpinMax
    flip[i] = 2f * PI.toFloat() * random.nextFloat()
    flipSpeed[i] = (p.FlipMin + (p.FlipMax - p.FlipMin) * random.nextFloat()) * if (random.nextBoolean()) 1f else -1f
    sway[i] = 2f * PI.toFloat() * random.nextFloat()
    swaySpeed[i] = p.SwayMin + (p.SwayMax - p.SwayMin) * random.nextFloat()
    alive[i] = true
  }

  private fun begin(n: Int, heightPx: Float, density: Float) {
    count = n.coerceIn(0, capacity)
    age = 0f
    gravity = FluidCelebrationProfile.GravityDp * density
    swayAccel = FluidCelebrationProfile.SwayDp * density
    bottom = heightPx
    margin = 24f * density
    for (i in count until capacity) alive[i] = false
  }

  /** La pioggia dall'alto, come iMessage: [n] pezzi su tutta la larghezza, scaglionati sopra il bordo. */
  fun seedRain(n: Int, widthPx: Float, heightPx: Float, density: Float, colors: Int, random: Random) {
    begin(n, heightPx, density)
    val p = FluidCelebrationProfile
    for (i in 0 until count) {
      common(i, random, density, colors)
      x[i] = widthPx * random.nextFloat()
      y[i] = -length[i] - heightPx * p.RainSpread * random.nextFloat()
      vx[i] = (random.nextFloat() - 0.5f) * 60f * density
      vy[i] = (p.RainSpeedMinDp + (p.RainSpeedMaxDp - p.RainSpeedMinDp) * random.nextFloat()) * density
    }
  }

  /** Lo scoppio da ([originX], [originY]): un ventaglio verso l'alto, che frena e poi ricade. */
  fun seedBurst(n: Int, originX: Float, originY: Float, heightPx: Float, density: Float, colors: Int, random: Random) {
    begin(n, heightPx, density)
    val p = FluidCelebrationProfile
    for (i in 0 until count) {
      common(i, random, density, colors)
      x[i] = originX
      y[i] = originY
      val angle = (-90f + (random.nextFloat() * 2f - 1f) * p.BurstHalfAngle) * PI.toFloat() / 180f
      val speed = (p.BurstSpeedMinDp + (p.BurstSpeedMaxDp - p.BurstSpeedMinDp) * random.nextFloat()) * density
      vx[i] = cos(angle) * speed
      vy[i] = sin(angle) * speed
    }
  }

  /**
   * I coriandoli gia' posati, fermi, per «Riduci movimento»: sparsi in una fascia attorno a ([originX], [originY]) (o in
   * alto, per la pioggia), con i giri a caso. Nessun passo dopo.
   */
  fun seedStill(n: Int, widthPx: Float, heightPx: Float, originX: Float, originY: Float, density: Float, colors: Int, random: Random) {
    begin(n, heightPx, density)
    for (i in 0 until count) {
      common(i, random, density, colors)
      val spread = 0.28f * widthPx
      x[i] = (originX + (random.nextFloat() * 2f - 1f) * spread).coerceIn(0f, widthPx)
      y[i] = (originY + (random.nextFloat() * 2f - 1f) * 0.6f * spread).coerceIn(0f, heightPx)
      vx[i] = 0f
      vy[i] = 0f
    }
  }

  /** Un passo di [dt] secondi: gravita', resistenza secondo la faccia, ondeggiare, giri. Vero se qualcuno e' ancora vivo. */
  fun step(dt: Float): Boolean {
    age += dt
    var any = false
    val p = FluidCelebrationProfile
    for (i in 0 until count) {
      if (!alive[i]) continue
      flip[i] += flipSpeed[i] * dt
      spin[i] += spinSpeed[i] * dt
      sway[i] += swaySpeed[i] * dt
      val face = abs(cos(flip[i]))
      val k = drag[i] * (p.EdgeDrag + p.FaceDrag * face)
      vx[i] += (swayAccel * sin(sway[i]) - k * vx[i]) * dt
      vy[i] += (gravity - k * vy[i]) * dt
      x[i] += vx[i] * dt
      y[i] += vy[i] * dt
      if (y[i] > bottom + margin || age > p.LifeSeconds) {
        alive[i] = false
      } else {
        any = true
      }
    }
    return any
  }

  /** L'opacita' della festa adesso: piena, poi la dissolvenza degli ultimi [FluidCelebrationProfile.FadeSeconds]. */
  val alpha: Float
    get() {
      val p = FluidCelebrationProfile
      val left = p.LifeSeconds - age
      return (left / p.FadeSeconds).coerceIn(0f, 1f)
    }

  companion object {
    const val Strip = 0
    const val Square = 1
    const val Dot = 2
  }
}

/**
 * Disegna i coriandoli vivi in un passaggio sul canvas nativo, con un pennello solo: il giro 3D e' lo schiacciamento lungo
 * il pezzo (`cos` del giro) e la luce (la faccia di piatto e' piu' chiara, il retro un po' piu' scuro). Non alloca.
 */
internal fun drawFluidConfetti(
  canvas: android.graphics.Canvas,
  paint: android.graphics.Paint,
  confetti: FluidConfetti,
  palette: IntArray,
  alpha: Float,
) {
  if (palette.isEmpty()) return
  for (i in 0 until confetti.count) {
    if (!confetti.alive[i]) continue
    val c = cos(confetti.flip[i])
    val light = (0.72f + 0.28f * abs(c)) * if (c < 0f) 0.88f else 1f
    val argb = palette[confetti.color[i] % palette.size]
    val r = (((argb shr 16) and 0xFF) * light).toInt().coerceIn(0, 255)
    val g = (((argb shr 8) and 0xFF) * light).toInt().coerceIn(0, 255)
    val b = ((argb and 0xFF) * light).toInt().coerceIn(0, 255)
    val a = (((argb ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
    paint.color = (a shl 24) or (r shl 16) or (g shl 8) or b
    val halfW = confetti.width[i] / 2f
    val halfL = confetti.length[i] / 2f
    canvas.save()
    canvas.translate(confetti.x[i], confetti.y[i])
    canvas.rotate(confetti.spin[i])
    canvas.scale(1f, if (abs(c) < 0.06f) 0.06f else abs(c))
    if (confetti.shape[i] == FluidConfetti.Dot) {
      canvas.drawCircle(0f, 0f, halfW, paint)
    } else {
      canvas.drawRect(-halfW, -halfL, halfW, halfL, paint)
    }
    canvas.restore()
  }
}

/**
 * Lo stato della festa: [launch] la fa partire, [isCelebrating] dice se e' in corso. Si crea con
 * [rememberFluidCelebrationState] e si passa a [FluidCelebration].
 */
@Stable
class FluidCelebrationState internal constructor() {
  /** Vero dalla partenza finche' l'ultimo coriandolo non e' uscito (o il segno fermo non e' svanito). */
  var isCelebrating: Boolean by mutableStateOf(false)
    private set

  /**
   * Quanti coriandoli: un parametro **interno**, per la scala dei dispositivi deboli di F7.10 quando sara' collegata
   * ([FluidCelebrationProfile.particlesFor]). Un'app non lo regola: Alessio non vuole manopole esposte.
   */
  internal var particleCount: Int = FluidCelebrationProfile.ParticleCount

  internal val confetti = FluidConfetti(FluidCelebrationProfile.Capacity)
  internal val generation = mutableIntStateOf(0)
  internal var origin: Offset? = null

  /** Il riquadro dello strato nella radice (px), scritto al piazzamento. */
  internal var layerX = 0f
  internal var layerY = 0f
  internal var layerWidth = 0f
  internal var layerHeight = 0f
  internal var density = 1f
  internal var colors = 1

  /** Cambia a ogni fotogramma: lo legge solo il disegno. */
  internal val tick = mutableLongStateOf(0L)

  /** L'opacita' dello strato: la dissolvenza del segno fermo. Letta solo nel livello. */
  internal val layerAlpha = mutableFloatStateOf(1f)

  /** Per le prove: i fotogrammi del passo e i disegni dello strato. */
  internal var frames = 0L
  internal var draws = 0L

  private var seeded = false
  private var lastNanos = 0L
  private var running = false

  /** Il passo di un fotogramma: un oggetto solo, fatto una volta. */
  internal val onFrame: (Long) -> Unit = { nanos ->
    if (!seeded) {
      if (layerWidth > 0f && layerHeight > 0f) {
        seed(Random(nanos))
        seeded = true
        lastNanos = nanos
      }
    } else {
      // Un buco lungo (la finestra in pausa) non fa saltare i coriandoli: al piu' 1/30 di secondo per passo.
      val dt = ((nanos - lastNanos) / 1e9f).coerceIn(0f, 1f / 30f)
      lastNanos = nanos
      running = confetti.step(dt)
    }
    frames++
    tick.longValue = nanos
  }

  private fun seed(random: Random) {
    val o = origin
    if (o == null) {
      confetti.seedRain(particleCount, layerWidth, layerHeight, density, colors, random)
    } else {
      confetti.seedBurst(particleCount, o.x - layerX, o.y - layerY, layerHeight, density, colors, random)
    }
    running = true
  }

  /**
   * Fa partire la festa. Senza [originInRoot] e' la pioggia dall'alto su tutto lo strato (iMessage); con un punto nella
   * radice (il centro del tasto che ha avuto successo, da `boundsInRoot()`) e' uno scoppio che sale da li' e ricade.
   * Una festa in corso riparte da capo.
   */
  fun launch(originInRoot: Offset? = null) {
    origin = originInRoot
    generation.intValue++
    isCelebrating = true
  }

  internal suspend fun run(reduced: Boolean) {
    seeded = false
    running = true
    layerAlpha.floatValue = 1f
    if (reduced) {
      // Il segno fermo: un fotogramma per sapere dove sta lo strato, poi un disegno solo.
      while (layerWidth <= 0f) withFrameNanos { }
      val o = origin
      val ox = o?.x?.minus(layerX) ?: (layerWidth / 2f)
      val oy = o?.y?.minus(layerY) ?: (layerHeight * 0.22f)
      confetti.seedStill(particleCount / 2, layerWidth, layerHeight, ox, oy, density, colors, Random(generation.intValue))
      tick.longValue++
      delay(FluidCelebrationProfile.StillHoldMs)
      val start = withFrameNanos { it }
      var t = 0f
      while (t < 1f) {
        t = withFrameNanos { (it - start) / 1e6f / FluidCelebrationProfile.StillFadeMs }
        layerAlpha.floatValue = (1f - t).coerceIn(0f, 1f)
      }
    } else {
      while (running) withFrameNanos(onFrame)
    }
    isCelebrating = false
  }
}

/** Lo stato di una festa, ricordato. */
@Composable
fun rememberFluidCelebrationState(): FluidCelebrationState = remember { FluidCelebrationState() }

/** I colori dei coriandoli. */
object FluidCelebrationDefaults {
  /**
   * I colori di sistema di Apple (HIG «Color», iOS 17–18): rosso, arancio, giallo, verde, azzurro, blu, viola, rosa, nella
   * variante chiara o scura del tema. Fiducia **alta** sui valori; che siano quelli dei coriandoli di iMessage e' a occhio
   * (24 novembre 2026, sul confronto in `sessioni/f8-5`).
   */
  @Composable
  @ReadOnlyComposable
  fun colors(): List<Color> = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Dark else Light

  internal val Light = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF32ADE6), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55),
  )
  internal val Dark = listOf(
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF375F),
  )
}

/**
 * La festa per un successo: i coriandoli che piovono dall'alto, come l'effetto «Coriandoli» di iMessage, o che
 * scoppiano da un punto (l'installazione riuscita in Pampa Store, un bel voto in ClasseViva). Si mette **sopra** il
 * contenuto, ultimo in un `Box` grande quanto lo schermo, e parte con [FluidCelebrationState.launch]:
 *
 * ```
 * val festa = rememberFluidCelebrationState()
 * Box(Modifier.fillMaxSize()) {
 *   Contenuto(onVoto = { if (it >= 9) festa.launch() })
 *   FluidCelebration(festa)
 * }
 * ```
 *
 * - Pezzi leggeri con il giro 3D (si schiacciano e si accendono girando), la gravita' e la resistenza dell'aria che
 *   cambia con la faccia, come la carta; il doppio colpetto aptico dell'engine ([FluidHapticEvent.Success]) alla partenza.
 * - **Costo.** Uno strato solo, disegnato in un passaggio, sopra tutto e con il suo livello (il contenuto sotto non si
 *   ridisegna); i pezzi sono preparati una volta e il passo non alloca. Finita la festa lo strato esce dalla
 *   composizione: zero ridisegni e il livello rilasciato. Non prende i tocchi.
 * - **«Riduci movimento»**: niente pioggia, un segno fermo e breve (i coriandoli gia' posati, 0,9 s, poi svaniscono) e
 *   l'aptica.
 *
 * @param state lo stato, da [rememberFluidCelebrationState].
 * @param colors i colori dei pezzi; di serie quelli di sistema di Apple ([FluidCelebrationDefaults.colors]).
 * @param haptic falso per tacere l'aptica (se l'app ne suona gia' una sua nello stesso momento).
 */
@Composable
fun FluidCelebration(
  state: FluidCelebrationState,
  modifier: Modifier = Modifier,
  colors: List<Color> = FluidCelebrationDefaults.colors(),
  haptic: Boolean = true,
) {
  val haptics = LocalFluidHaptics.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val generation = state.generation.intValue
  LaunchedEffect(generation) {
    if (generation > 0 && haptic) haptics.play(FluidHapticEvent.Success)
  }
  if (!state.isCelebrating) return
  state.density = LocalDensity.current.density
  val palette = remember(colors) { IntArray(colors.size) { colors[it].toArgb() } }
  state.colors = palette.size
  val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
  LaunchedEffect(generation, reduced) { state.run(reduced) }
  Spacer(
    modifier
      .fillMaxSize()
      .onPlaced { coordinates ->
        val p = coordinates.positionInRoot()
        state.layerX = p.x
        state.layerY = p.y
        state.layerWidth = coordinates.size.width.toFloat()
        state.layerHeight = coordinates.size.height.toFloat()
      }
      .graphicsLayer { alpha = state.layerAlpha.floatValue }
      .drawBehind {
        state.tick.longValue
        state.draws++
        val confetti = state.confetti
        drawIntoCanvas { drawFluidConfetti(it.nativeCanvas, paint, confetti, palette, confetti.alpha) }
      },
  )
}
