package dev.antigravity.fluidengine.ui.fluid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

// F8.11 · `sfondi-vivi`, rivista da Alessio: «più che per pagine da vedere tutti i giorni, ci può stare per gli onboarding,
// o pagine più giocose». Tre sfondi lentissimi e sobri, coi colori del tema, da mettere dietro il benvenuto, la pagina vuota
// dell'assistente, un traguardo. Si fermano da soli con «Riduci movimento», col risparmio energetico o la batteria scarica,
// e quando la pagina non si vede. Riferimento: gli sfondi animati della schermata di blocco di iOS (Astronomia, Meteo).

/**
 * Uno sfondo vivo: [Aurora] (aloni di colore che vagano piano in alto), [Waves] (onde morbide in basso) o [Stars] (puntini che
 * respirano). Una classe con le sue costanti e non un enum, così uno sfondo nuovo si aggiunge senza rompere chi li usa.
 */
@Immutable
class FluidLiveBackground private constructor(internal val kind: Int, private val label: String) {
  override fun toString(): String = label

  companion object {
    /** L'aurora: tre aloni larghi coi colori del tema che vagano piano nella metà alta, e sotto la pagina pulita. */
    val Aurora: FluidLiveBackground = FluidLiveBackground(0, "Aurora")

    /** Le onde: tre onde morbide nella metà bassa, che scorrono di lato in decine di secondi. */
    val Waves: FluidLiveBackground = FluidLiveBackground(1, "Waves")

    /** Le stelle: puntini radi che respirano, ognuno col suo ritmo; sul tema chiaro diventano granelli di colore. */
    val Stars: FluidLiveBackground = FluidLiveBackground(2, "Stars")
  }
}

/** I numeri degli sfondi vivi. */
object FluidLiveBackgroundDefaults {
  /**
   * Il ritmo dello sfondo: **30 Hz**. Lo sfondo è lentissimo (le onde più svelte fanno un giro in una quarantina di
   * secondi), e a 30 Hz il passo di un fotogramma è sotto il pixel: non si vede, e il vetro sopra ricampiona la metà
   * delle volte che a 60 Hz e un quarto che a 120. Scelto il 24 novembre 2026, misura nel rapporto di F8.11.
   */
  const val FrameRateHz: Int = 30

  /**
   * L'istante disegnato quando lo sfondo è fermo (movimento ridotto, risparmio energetico, batteria scarica): **12 s**,
   * una posa in cui gli aloni e le onde sono ben distribuiti. Fermo vuol dire un fotogramma solo, leggibile.
   */
  const val RestSeconds: Float = 12f

  /**
   * Ogni quanto il tempo ricomincia: **200π s** (10 min e 28 s). Tiene i seni precisi in `float`, e il ritorno a zero non
   * si vede: tutte le frequenze del disegno sono multipli di 0,01 rad/s, quindi in 200π secondi ogni onda fa un numero
   * intero di giri (R2: con 30 minuti gli aloni saltavano fino a un decimo della pagina). Una frequenza nuova deve
   * restare un multiplo di 0,01 rad/s (`F8p11LiveBackgroundWrapTest`).
   */
  const val WrapSeconds: Float = 628.31854f
}

/**
 * Lo stato di uno sfondo vivo: se sta andando ([isRunning]) e quanti fotogrammi nuovi ha disegnato ([framesDrawn]), per le
 * prove e per le misure. Non serve tenerlo: [FluidLiveBackgroundCanvas] ne crea uno suo.
 */
@Stable
class FluidLiveBackgroundState internal constructor() {
  /** Lo sfondo si muove: falso col movimento ridotto, col risparmio energetico, con la batteria scarica o a pagina nascosta. */
  var isRunning: Boolean by mutableStateOf(false)
    internal set

  /** Quanti istanti nuovi lo sfondo ha chiesto da quando c'è (non è uno stato osservato: leggerlo non ricompone). */
  var framesDrawn: Long = 0L
    internal set

  internal var seconds by mutableFloatStateOf(FluidLiveBackgroundDefaults.RestSeconds)
}

/** Lo stato di uno sfondo vivo, da passare a [FluidLiveBackgroundCanvas] quando lo si vuole leggere. */
@Composable
fun rememberFluidLiveBackgroundState(): FluidLiveBackgroundState = remember { FluidLiveBackgroundState() }

/**
 * Uno sfondo vivo a tutta la misura del suo contenitore, opaco, coi colori del tema ([tone], di serie il primario; vedi
 * [FluidHeroTone]). Va sotto il contenuto di una pagina speciale, **non** sotto le pagine di tutti i giorni: un benvenuto,
 * la pagina vuota dell'assistente, un traguardo. In una [FluidScreen] si passa con `ambient = FluidAmbient(…, live = …)`,
 * così la barra e il vetro della pagina lo rifrangono.
 *
 * - **Si ferma da solo** su un fotogramma fermo e leggibile: col movimento ridotto ([LocalFluidMotionPolicy]), col
 *   risparmio energetico, con la batteria scarica (il segnale di Android, circa 15%) e quando la pagina non è in primo
 *   piano (ciclo di vita sotto RESUMED: una pagina coperta di `FluidNavHost`, l'app in sottofondo).
 * - **Costa poco**: il Canvas della piattaforma (gradienti e tracciati costruiti una volta per misura, niente AGSL),
 *   nessuna allocazione per fotogramma, nessuna ricomposizione (il tempo si legge nel disegno), e il tempo avanza a
 *   [frameRateHz] (30 di serie), non a ogni fotogramma dello schermo. Sotto il vetro è una sorgente che cambia: il costo
 *   misurato è nel rapporto di F8.11.
 * - È decorativo: TalkBack non lo vede.
 *
 * @param intensity quanto colore: 1 pieno, 0 il fondo piatto (il fotogramma resta opaco).
 */
@Composable
fun FluidLiveBackgroundCanvas(
  background: FluidLiveBackground,
  modifier: Modifier = Modifier,
  tone: FluidHeroTone = FluidHeroTone.Primary,
  intensity: Float = 1f,
  state: FluidLiveBackgroundState = rememberFluidLiveBackgroundState(),
  frameRateHz: Int = FluidLiveBackgroundDefaults.FrameRateHz,
) {
  val scheme = MaterialTheme.colorScheme
  val colors = remember(scheme, tone, intensity) { liveColors(scheme, tone, intensity) }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val power = rememberPowerConditions()
  val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
  val running = !reduced && !power.saving && !power.batteryLow && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
  LaunchedEffect(running, frameRateHz, state) {
    state.isRunning = running
    if (!running) {
      // Fermo: col movimento ridotto la posa di riposo; negli altri casi resta dov'era, e ripartirà da lì.
      if (reduced) state.seconds = FluidLiveBackgroundDefaults.RestSeconds
      return@LaunchedEffect
    }
    val periodNanos = 1_000_000_000L / frameRateHz.coerceIn(1, 120)
    var origin = Long.MIN_VALUE
    val resumeAt = state.seconds
    while (true) {
      withFrameNanos { now ->
        if (origin == Long.MIN_VALUE) origin = now - (resumeAt * 1e9f).toLong()
        state.seconds = (((now - origin) / 1e9) % FluidLiveBackgroundDefaults.WrapSeconds).toFloat()
        state.framesDrawn++
      }
      // Non si chiede un fotogramma a ogni vsync: si dorme quasi tutto il periodo e si prende il vsync dopo. A 120 Hz il
      // passo cade fra 27 e 35 ms, a 60 e a 30 Hz su 33,3.
      delay(periodNanos * 8 / 10 / 1_000_000)
    }
  }
  val draw = Modifier.drawWithCache {
    val painter = CanvasLive(background, size.width, size.height, colors)
    onDrawBehind { painter.draw(this, state.seconds) }
  }
  Box(modifier.then(draw).semantics { hideFromAccessibility() })
}

/** I colori dello sfondo, già pesati da [intensity] e dal lato del tema. */
@Immutable
internal data class LiveColors(val base: Color, val a: Color, val b: Color, val c: Color, val dark: Float)

internal fun liveColors(scheme: androidx.compose.material3.ColorScheme, tone: FluidHeroTone, intensity: Float): LiveColors {
  val amount = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 1f
  val hero = fluidHeroColors(scheme, tone, urgent = false)
  val base = scheme.background
  val dark = if (base.luminance() < 0.5f) 1f else 0f
  // Tre tinte vicine e non uguali: l'accento del tono e due rotazioni della sua tinta (+40° e −50°, a occhio il 24
  // novembre 2026). I ruoli del tema (secondario, terziario) nascono dall'accento e restano quasi dello stesso colore: tre
  // veli tutti verdi si fondevano in una macchia sola.
  return LiveColors(
    base = base,
    a = lerp(base, hero.accent, amount),
    b = lerp(base, hero.accent.rotateHue(40f), amount),
    c = lerp(base, hero.accent.rotateHue(-50f), amount),
    dark = dark,
  )
}

private fun Color.rotateHue(degrees: Float): Color {
  val hsv = FloatArray(3)
  android.graphics.Color.colorToHSV(toArgb(), hsv)
  hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
  return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Risparmio energetico e batteria scarica, letti all'inizio e seguiti coi segnali di Android. */
@Stable
private class PowerConditions {
  var saving by mutableStateOf(false)
  var batteryLow by mutableStateOf(false)
}

@Composable
private fun rememberPowerConditions(): PowerConditions {
  val context = LocalContext.current
  val conditions = remember { PowerConditions() }
  DisposableEffect(context) {
    val app = context.applicationContext
    val power = app.getSystemService(PowerManager::class.java)
    fun read(sticky: Intent?) {
      conditions.saving = power?.isPowerSaveMode == true
      if (sticky != null) conditions.batteryLow = sticky.batteryLow()
    }
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
          Intent.ACTION_BATTERY_LOW -> conditions.batteryLow = true
          Intent.ACTION_BATTERY_OKAY -> conditions.batteryLow = false
          else -> read(null)
        }
      }
    }
    val filter = IntentFilter().apply {
      addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
      addAction(Intent.ACTION_BATTERY_LOW)
      addAction(Intent.ACTION_BATTERY_OKAY)
    }
    ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    read(ContextCompat.registerReceiver(app, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
    onDispose { runCatching { app.unregisterReceiver(receiver) } }
  }
  return conditions
}

/** La batteria scarica dal segnale fisso di Android: il segnale suo da Android 9, prima il 15% senza carica. */
private fun Intent.batteryLow(): Boolean {
  if (Build.VERSION.SDK_INT >= 28 && hasExtra(BatteryManager.EXTRA_BATTERY_LOW)) {
    return getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
  }
  val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
  val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
  val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
  return level >= 0 && scale > 0 && !plugged && level * 100 / scale <= 15
}

/**
 * Il disegno, col Canvas della piattaforma: gradienti e tracciati costruiti una volta per misura e spostati nel disegno,
 * nessuna allocazione per fotogramma. Niente AGSL apposta: uno sfondo non deve passare dal compilatore degli shader (e dai
 * suoi verdetti) per disegnarsi, e non c'è un dispositivo su cui non si veda.
 *
 * - **Aurora**: tre aloni larghi e schiacciati coi colori del tono, che vagano piano nella metà alta (il più svelto fa un
 *   giro in circa 45 s), e sotto un velo del fondo che riporta la pagina pulita dove sta il testo.
 * - **Onde**: tre creste piene nella metà bassa, che scorrono di lato (la più svelta in circa 40 s).
 * - **Stelle**: 90 puntini in posti fissi (un generatore con un seme fisso) che respirano ognuno col suo ritmo (3–10 s),
 *   sotto un alone del colore del tono in alto.
 */
private class CanvasLive(
  private val background: FluidLiveBackground,
  private val width: Float,
  private val height: Float,
  private val colors: LiveColors,
) {
  private val glowRadius = maxOf(width, height) * 0.42f
  private val glows = listOf(colors.a, colors.b, colors.c).map { color ->
    Brush.radialGradient(listOf(color.copy(alpha = 0.40f + 0.22f * colors.dark), color.copy(alpha = 0f)), Offset.Zero, glowRadius)
  }
  private val keep = Brush.verticalGradient(
    0.38f to colors.base.copy(alpha = 0f),
    0.86f to colors.base,
    startY = 0f,
    endY = height,
  )
  private val sky = Brush.radialGradient(
    listOf(colors.a.copy(alpha = 0.10f + 0.08f * colors.dark), colors.a.copy(alpha = 0f)),
    Offset(width * 0.5f, 0f),
    maxOf(width, height) * 0.7f,
  )
  private val path = Path()
  private val stars: FloatArray = FloatArray(StarCount * 4).also { array ->
    var seed = 0x2F6E2B1
    fun next(): Float {
      seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
      return (seed ushr 8) / 16_777_216f
    }
    for (i in 0 until StarCount) {
      array[i * 4] = next() * width
      array[i * 4 + 1] = next() * height
      array[i * 4 + 2] = 0.8f + next() * 1.6f
      array[i * 4 + 3] = next() * 2f * PI.toFloat()
    }
  }

  fun draw(scope: DrawScope, seconds: Float) = with(scope) {
    drawRect(colors.base)
    when (background.kind) {
      0 -> {
        for (i in glows.indices) {
          val x = width * (0.15f + 0.37f * i + 0.14f * sin(seconds * (0.14f + 0.03f * i) + i * 1.7f))
          val y = height * (0.06f + 0.08f * (i % 2) + 0.035f * sin(seconds * (0.09f + 0.02f * i) + i))
          // Gli aloni sono schiacciati: larghi il doppio di quanto sono alti, come veli e non come lune.
          translate(x, y) { scale(1.6f, 0.62f, Offset.Zero) { drawCircle(glows[i], radius = glowRadius, center = Offset.Zero) } }
        }
        drawRect(keep)
      }
      1 -> for (i in 0 until 3) {
        path.rewind()
        path.moveTo(0f, height)
        val steps = 32
        for (s in 0..steps) {
          val u = s / steps.toFloat()
          val h = 0.60f + 0.10f * i +
            0.035f * sin(u * (3f + i) * PI.toFloat() + seconds * (0.16f + 0.04f * i) + i * 2.1f) +
            0.012f * sin(u * 9f - seconds * 0.11f + i)
          path.lineTo(u * width, h * height)
        }
        path.lineTo(width, height)
        path.close()
        val color = when (i) { 0 -> colors.c; 1 -> colors.b; else -> colors.a }
        drawPath(path, color.copy(alpha = (0.20f + 0.09f * i) * (0.80f + 0.25f * colors.dark)))
      }
      else -> {
        drawRect(sky)
        val star = lerp(colors.b, Color.White, colors.dark * 0.65f)
        for (i in 0 until StarCount) {
          val twinkle = 0.55f + 0.45f * sin(seconds * (0.6f + (i % 7) * 0.2f) + stars[i * 4 + 3])
          drawCircle(
            star.copy(alpha = twinkle * (0.45f + 0.45f * colors.dark)),
            radius = stars[i * 4 + 2] * density,
            center = Offset(stars[i * 4], stars[i * 4 + 1]),
          )
        }
      }
    }
  }

  private companion object {
    const val StarCount = 90
  }
}
