package dev.antigravity.fluidengine.ui.glass.field

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.FluidWork
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import dev.antigravity.fluidengine.ui.fluid.rememberFluidMotionPolicy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Consente al riflesso del vetro Liquid di seguire l'inclinazione. Di serie è spento, così il tema
 * non apre i servizi dei sensori. L'app può fornirlo attorno a `FluidTheme` e conservare la scelta.
 */
val LocalFluidGlassMotionLight: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

internal val LocalGlassLightEnvironment = compositionLocalOf<GlassLightEnvironment?> { null }

@Composable
internal fun rememberGlassLightEnvironment(): GlassLightEnvironment? {
  if (!LocalFluidGlassMotionLight.current || Build.VERSION.SDK_INT < 33) return null
  if (LocalFluidMotionPolicy.current.reducedMotion || rememberFluidMotionPolicy().reducedMotion) return null
  LocalGlassLightEnvironment.current?.let { return it }
  val view = LocalView.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val environment = remember(view) {
    val context = view.context.applicationContext
    GlassLightEnvironment(AndroidGlassLightSensor(context) { view.display?.rotation ?: Surface.ROTATION_0 })
  }
  DisposableEffect(environment, lifecycle) {
    val context = view.context.applicationContext
    val power = context.getSystemService(PowerManager::class.java)
    fun update() {
      environment.setAvailable(
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && power?.isPowerSaveMode != true &&
          // F8.20: il posto solo delle condizioni (risparmio energetico, telefono caldo, e la loro simulazione).
          FluidDeviceConditions.allows(FluidWork.MotionEffects).allowed,
      )
    }
    val powerChanges = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) = update()
    }
    val visibility = LifecycleEventObserver { _, _ -> update() }
    ContextCompat.registerReceiver(
      context, powerChanges, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    lifecycle.addObserver(visibility)
    update()
    // Le condizioni del telefono cambiano anche senza un annuncio di Android (il calore, la simulazione): si rilegge.
    val conditions = CoroutineScope(Dispatchers.Main.immediate).launch {
      FluidDeviceConditions.state.drop(1).collect { update() }
    }
    onDispose {
      conditions.cancel()
      lifecycle.removeObserver(visibility)
      context.unregisterReceiver(powerChanges)
      environment.setAvailable(false)
    }
  }
  return environment
}

/** Una lambda del solo strato di luce può ricevere l'angolo senza cambiare il contratto di drawBackdrop. */
internal class GlassLightDraw(private val draw: DrawScope.(Float) -> Unit) : (DrawScope) -> Unit {
  override fun invoke(scope: DrawScope) = draw(scope, 0f)

  operator fun invoke(scope: DrawScope, angle: Float) = draw(scope, angle)
}

internal interface GlassLightSensor {
  fun start(onSample: (x: Float, y: Float, rotation: Int, timestampNs: Long) -> Unit): Boolean
  fun stop()
}

/** Un solo campione pubblicato per il tema in finestra. Solo il disegno osserva [angle]. */
internal class GlassLightEnvironment(private val sensor: GlassLightSensor) {
  private var available = false
  private var surfaces = 0
  private var listening = false
  private val filter = GlassLightFilter()

  var angle by mutableFloatStateOf(0f)
    private set

  fun setAvailable(value: Boolean) {
    if (available == value) return
    available = value
    updateListener()
  }

  fun addSurface() {
    if (++surfaces == 1) updateListener()
  }

  fun removeSurface() {
    if (--surfaces == 0) updateListener()
  }

  private fun updateListener() {
    val wanted = available && surfaces > 0
    if (wanted == listening) return
    if (wanted) {
      listening = sensor.start { x, y, rotation, timestampNs ->
        if (listening) filter.sample(x, y, rotation, timestampNs)?.let { angle = it }
      }
    } else {
      listening = false
      sensor.stop()
      filter.reset()
      angle = 0f
    }
  }
}

/**
 * Prende la prima posa come neutra. Il passa-basso segue il tempo del sensore anche se la cadenza varia.
 * La soglia è rispetto all'ultimo valore pubblicato, così una sequenza di piccoli spostamenti si somma.
 */
internal class GlassLightFilter {
  private var rotation = -1
  private var lastNs = 0L
  private var neutral = 0f
  private var filtered = 0f
  private var published = 0f

  fun reset() {
    rotation = -1
    published = 0f
  }

  fun sample(x: Float, y: Float, rotation: Int, timestampNs: Long): Float? {
    if (!x.isFinite() || !y.isFinite()) return null
    // Gli assi del sensore sono quelli del dispositivo nella sua orientazione naturale.
    val tilt = when (rotation) {
      Surface.ROTATION_90 -> y - 0.6f * x
      Surface.ROTATION_180 -> -x - 0.6f * y
      Surface.ROTATION_270 -> -y + 0.6f * x
      else -> x + 0.6f * y
    }
    if (this.rotation != rotation) {
      this.rotation = rotation
      lastNs = timestampNs
      neutral = tilt
      filtered = 0f
      if (published == 0f) return null
      published = 0f
      return published
    }
    if (timestampNs <= lastNs) return null
    val elapsed = (timestampNs - lastNs).coerceAtMost(80_000_000L)
    lastNs = timestampNs
    val target = ((tilt - neutral) * RadiansPerAcceleration).coerceIn(-MaxAngle, MaxAngle)
    filtered += (target - filtered) * (1f - exp(-elapsed / FilterTimeNs))
    val settled = target == 0f && abs(filtered) <= RedrawThreshold
    if (settled) filtered = 0f
    if (filtered == published || (!settled && abs(filtered - published) < RedrawThreshold)) return null
    published = filtered
    return published
  }
}

// Radianti: circa 22° al massimo e 0,5° di soglia. Il guadagno raggiunge il tetto a circa 0,65 g.
private const val MaxAngle = 0.38f
private const val RadiansPerAcceleration = 0.060f
private const val RedrawThreshold = 0.009f
private const val FilterTimeNs = 90_000_000f

/** Lo stesso asse serve al nodo Android e alla traccia del banco. Il filo e il bagliore del dito hanno altri uniform. */
internal fun glassReflexDirection(x: Float, y: Float, angle: Float): Offset {
  val cosine = cos(angle)
  val sine = sin(angle)
  return Offset(x * cosine - y * sine, x * sine + y * cosine)
}

private class AndroidGlassLightSensor(context: Context, private val rotation: () -> Int) :
  GlassLightSensor, SensorEventListener {
  private val manager = context.getSystemService(SensorManager::class.java)
  private var onSample: ((Float, Float, Int, Long) -> Unit)? = null

  override fun start(onSample: (Float, Float, Int, Long) -> Unit): Boolean {
    val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
    if (!manager.registerListener(this, accelerometer, 25_000)) return false
    this.onSample = onSample
    return true
  }

  override fun stop() {
    onSample = null
    manager?.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    onSample?.invoke(event.values[0], event.values[1], rotation(), event.timestamp)
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
