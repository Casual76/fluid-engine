@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButtonCore
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.gelPose
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * F7.23 · Premi e trascina un tasto tondo di vetro, col dito vero, sul tasto vero.
 *
 * Il difetto (Alessio, 25/11, Tab S9, il Play da 62 dp della scena `riproduzione-sistema`): il tasto trascinato stava in un
 * riquadro (lo spostamento si fermava dopo un lato di strada, asse per asse) e l'ellisse saltava d'orientamento da un
 * fotogramma all'altro. La causa era la posa del gel di F6.1 (`fluidGelPose`): deriva `0,25 · tanh(1,6 · strada)` asse per
 * asse e flessione dalla velocità di un solo evento.
 *
 * Qui un `FluidGlassIconButtonCore` da 62 dp col materiale Liquid (verdetti degli shader accettati: col vetro di ripiego
 * il gel non c'è) e il gesto da `performTouchInput`, un evento ogni 4 ms come il digitalizzatore del Tab S9. A ogni
 * fotogramma (16 ms) la posa che il layer del vetro applica davvero (`gelPose`, la stessa di `gelTranslation`).
 *
 * In mdpi un dp è un pixel: il lato del tasto, `D`, è 62 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h900dp-mdpi")
class F7p23GlassDragTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var press: FluidPressState

  private val lens = 62f

  @Before fun liquid() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
  }

  @After fun reset() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun show() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFF15171A)))
          val state = rememberFluidPressState(FluidPressFeedback.ExpandRound)
          SideEffect { press = state }
          Box(Modifier.offset(80.dp, 300.dp)) {
            FluidGlassIconButtonCore(
              onClick = {},
              modifier = Modifier.testTag("play"),
              backdrop = page,
              enabled = true,
              selected = false,
              shape = FluidCapsuleShape,
              onLongClick = null,
              size = FluidGlassSize.Regular,
              haptic = null,
              longClickHaptic = null,
              press = state,
              lens = lens.dp,
            ) {}
          }
        }
      }
    }
    frames(30)
  }

  private fun frames(count: Int) = repeat(count) {
    compose.mainClock.advanceTimeByFrame()
    Snapshot.sendApplyNotifications()
  }

  /** La posa che il layer del vetro applica adesso. */
  private fun pose() = press.gelPose(lens, lens, 1f)

  /**
   * Un gesto: giù al centro del tasto, poi un evento ogni 4 ms verso i punti di [path] (relativi al contatto), un
   * fotogramma ogni quattro eventi, e [onFrame] a ogni fotogramma con la strada vera del dito. Su, e il ritorno finito.
   */
  private fun gesture(path: List<Offset>, onFrame: (finger: Offset) -> Unit) {
    val node = compose.onNodeWithTag("play")
    node.performTouchInput { down(center) }
    frames(2)
    var at = Offset.Zero
    path.chunked(4).forEach { events ->
      node.performTouchInput {
        for (to in events) {
          moveBy(to - at, delayMillis = 4L)
          at = to
        }
      }
      frames(1)
      onFrame(at)
    }
    node.performTouchInput { up() }
    frames(90)
    assertEquals("il ritorno finisce", Offset.Zero, press.lean)
  }

  /** La deriva a [distance] lati di strada, interpolata fra i due fotogrammi che la comprendono. */
  private fun at(curve: List<Pair<Float, Float>>, distance: Float): Float {
    val after = curve.indexOfFirst { it.first >= distance }
    assertTrue("la strada arriva a $distance lati", after > 0)
    val (x0, y0) = curve[after - 1]
    val (x1, y1) = curve[after]
    return y0 + (y1 - y0) * (distance - x0) / (x1 - x0)
  }

  private fun straight(direction: Offset, length: Float, stepPx: Float): List<Offset> {
    val unit = direction / direction.getDistance()
    return (1..(length / stepPx).toInt()).map { unit * (it * stepPx) }
  }

  @Test fun driftGrowsWithoutARigidCapAndIsTheSameInEveryDirection() {
    show()
    // In orizzontale fino a sei lati di strada e un poco oltre, a 500 dp/s: la deriva a ogni fotogramma.
    val along = mutableListOf<Pair<Float, Float>>()
    gesture(straight(Offset(1f, 0f), 6.2f * lens, 2f)) { finger ->
      along += finger.getDistance() / lens to pose().drift.getDistance() / lens
    }
    // In diagonale fino a quattro lati e un poco oltre: stessa distanza, stessa deriva (prima, asse per asse, 1,41 volte
    // tanto), e nel verso del dito.
    val across = mutableListOf<Pair<Float, Float>>()
    var diagonalAngle = 0f
    gesture(straight(Offset(1f, 1f), 4.2f * lens, 2f)) { finger ->
      val drift = pose().drift
      across += finger.getDistance() / lens to drift.getDistance() / lens
      if (drift != Offset.Zero) {
        diagonalAngle = max(diagonalAngle, abs(atan2(drift.y, drift.x) - atan2(finger.y, finger.x)) * 180f / PI.toFloat())
      }
    }
    val axis = FloatArray(7) { k -> if (k == 0) 0f else at(along, k.toFloat()) }
    val diagonal = at(across, 4f)
    val report = "deriva in lati a 1..6 lati di strada: ${axis.drop(1).joinToString { "%.3f".format(it) }}; " +
      "in diagonale a 4: ${"%.3f".format(diagonal)}"
    println("F7.23 $report")
    for (k in 2..6) assertTrue("cresce sempre ($report)", axis[k] > axis[k - 1] + 0.01f)
    assertTrue("nessun tetto rigido: da 3 a 6 lati di strada il vetro va avanti ancora ($report)",
      axis[6] - axis[3] >= 0.06f)
    for (k in 2..5) {
      assertTrue("cresce sempre meno ($report)", axis[k + 1] - axis[k] < axis[k] - axis[k - 1])
    }
    assertTrue("niente riquadro: in diagonale come in orizzontale ($report)", abs(diagonal / axis[4] - 1f) < 0.05f)
    assertTrue("la deriva va verso il dito: ${diagonalAngle}°", diagonalAngle < 2f)
  }

  @Test fun deformationIsContinuousUnderANoisyFinger() {
    show()
    // Il dito vero: un giro e mezzo attorno al contatto a 1,2 lati, in 2 s, con un rumore di ±1,5 px su ogni evento.
    var seed = 0x2311L
    fun noise(): Float {
      seed = (seed * 6364136223846793005L + 1442695040888963407L)
      return ((seed ushr 33) % 3001L).toFloat() / 1000f - 1.5f
    }
    val radius = 1.2f * lens
    val path = mutableListOf<Offset>()
    for (i in 1..36) path += Offset(radius * i / 36f, 0f)
    for (i in 1..500) {
      val angle = 1.5f * 2f * PI.toFloat() * i / 500f
      path += Offset(radius * cos(angle) + noise(), radius * sin(angle) + noise())
    }
    var previous: FloatArray? = null
    var worstTurn = 0f
    var worstStep = 0f
    var ratioSum = 0f
    var ratioFrames = 0
    var frame = 0
    gesture(path) {
      val pose = pose()
      val a = pose.invXX
      val b = pose.invXY
      val c = pose.invYY
      val half = sqrt(((a - c) / 2f) * ((a - c) / 2f) + b * b)
      val ratio = ((a + c) / 2f + half) / ((a + c) / 2f - half)
      val axis = 0.5f * atan2(2f * b, a - c)
      val now = floatArrayOf(a, b, c, ratio, axis)
      frame++
      // Si giudica il giro, non l'uscita dal centro (144 ms) e la svolta vera all'inizio del giro: dai 400 ms in poi.
      if (frame > 25) {
        ratioSum += ratio
        ratioFrames++
        previous?.let { last ->
          worstStep = max(worstStep, max(abs(a - last[0]), max(abs(b - last[1]), abs(c - last[2]))))
          if (ratio > 1.04f && last[3] > 1.04f) {
            var turn = axis - last[4]
            while (turn > PI / 2) turn -= PI.toFloat()
            while (turn < -PI / 2) turn += PI.toFloat()
            worstTurn = max(worstTurn, abs(turn) * 180f / PI.toFloat())
          }
        }
      }
      previous = now
    }
    val meanRatio = ratioSum / ratioFrames
    val report = "salto d'asse peggiore ${"%.1f".format(worstTurn)}° per fotogramma, passo della matrice " +
      "${"%.3f".format(worstStep)}, ellisse media ${"%.3f".format(meanRatio)}:1"
    println("F7.23 $report")
    // Un giro e mezzo in 2 s: l'asse vero ruota di 4,3° ogni 16 ms, e la matrice di un'ellisse 1,33:1 che ruota così
    // cambia di circa 0,03 a fotogramma. Col codice di F6.1 (la velocità di un solo evento): 90° e 0,29.
    assertTrue("l'ellisse non salta d'orientamento ($report)", worstTurn <= 10f)
    assertTrue("la deformazione è continua ($report)", worstStep <= 0.045f)
    assertTrue("il gel tirato e in moto resta un'ellisse ($report)", meanRatio >= 1.06f)
  }
}
