package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il movimento scritto a mano diventato token (3.0, fase 10) ha **gli stessi valori di prima**.
 *
 * Il punto di questa fase e' che la matrice si chiuda senza che niente si muova: la pillola, il puck,
 * lo slider e l'interruttore devono uscire identici (R1). Ogni test qui sotto rifa l'espressione
 * *com'era scritta nel file del componente* e la confronta con il token, non per uguaglianza di oggetto
 * (la molla di serie di Compose ha una soglia nulla, il token una esplicita, e le due si comportano
 * allo stesso modo) ma per **traiettoria**: lo stesso valore a ogni passo di 25 ms per tre secondi, e la
 * stessa durata. Un numero spostato di una cifra cambia la traiettoria.
 */
class FluidMotionTokensTest {

  private val stepNanos = 25_000_000L
  private val steps = 120

  private fun trajectory(spec: AnimationSpec<Float>, from: Float, to: Float): List<Float> {
    val vectorised = spec.vectorize(Float.VectorConverter)
    return (0..steps).map { step ->
      vectorised.getValueFromNanos(
        step * stepNanos,
        AnimationVector1D(from),
        AnimationVector1D(to),
        AnimationVector1D(0f),
      ).value
    }
  }

  private fun durationNanos(spec: AnimationSpec<Float>, from: Float, to: Float): Long =
    spec.vectorize(Float.VectorConverter).getDurationNanos(
      AnimationVector1D(from),
      AnimationVector1D(to),
      AnimationVector1D(0f),
    )

  private fun assertSameMotion(
    label: String,
    before: AnimationSpec<Float>,
    after: AnimationSpec<Float>,
    from: Float = 0f,
    to: Float = 1f,
  ) {
    assertEquals("$label: la traiettoria", trajectory(before, from, to), trajectory(after, from, to))
    assertEquals("$label: la durata", durationNanos(before, from, to), durationNanos(after, from, to))
  }

  // --- La barra a pillola ------------------------------------------------------------------------

  @Test
  fun `the tug return, the fold, the puck entrance and the fold fade are the springs and tweens they were`() {
    assertSameMotion("ritorno del tiro", spring(1f, 300f, 0.5f), FluidMotion.TabTugReturn, to = 24f)
    assertSameMotion("piegatura della barra", spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow), FluidMotion.TabBarFold)
    assertSameMotion("puck che entra", tween(durationMillis = 300, easing = FastOutSlowInEasing), FluidMotion.PuckEnter)
    // La dissolvenza di serie di `fadeIn()`/`fadeOut()`, che `AnimatedContent` usava senza dirlo.
    assertSameMotion("dissolvenza della piegatura", spring(stiffness = Spring.StiffnessMediumLow), FluidMotion.FoldCrossFade)
  }

  @Test
  fun `the gooey pulse is the keyframes it was, a peak at half the fold`() {
    val before = keyframes<Float> {
      durationMillis = 300
      0f at 0
      1f atFraction 0.5f using FastOutSlowInEasing
      0f at 300
    }
    assertSameMotion("picco gommoso", before, FluidMotion.GooeyPulse, from = 0f, to = 0f)
    assertEquals(300, FluidMotion.GooeyMillis)
    // E il puck che lo accompagna dura quanto lui: e' la sola cosa che li tiene insieme.
    assertEquals(durationNanos(FluidMotion.GooeyPulse, 0f, 0f), durationNanos(FluidMotion.PuckEnter, 0f, 1f))
  }

  @Test
  fun `the tab that enters or leaves the bar keeps its keyframes`() {
    val enterAlpha = keyframes<Float> {
      durationMillis = 150
      0f atFraction 0.5f using FastOutSlowInEasing
      1f atFraction 0.8f
    }
    val leaveAlpha = keyframes<Float> { durationMillis = 150 }
    assertSameMotion("alfa in entrata", enterAlpha, FluidMotion.tabEnterAlpha(entering = true), from = 0f, to = 1f)
    assertSameMotion("alfa in uscita", leaveAlpha, FluidMotion.tabEnterAlpha(entering = false), from = 1f, to = 0f)

    val blur = 50f * 3f
    val enterBlur = keyframes<Float> {
      durationMillis = 150
      blur atFraction 0.5f using FastOutSlowInEasing
      0f atFraction 0.8f
    }
    assertSameMotion("sfocatura in entrata", enterBlur, FluidMotion.tabEnterBlur(entering = true, blurPx = blur), from = blur, to = 0f)
    assertSameMotion("sfocatura in uscita", leaveAlpha, FluidMotion.tabEnterBlur(entering = false, blurPx = blur), from = 0f, to = blur)
  }

  @Test
  fun `the lead the puck has before the page changes is still 160 ms`() {
    assertEquals(160L, FluidMotion.TabPuckLeadMillis)
  }

  // --- Foglio e pop-up ---------------------------------------------------------------------------

  @Test
  fun `the sheet settle is the spring it was`() {
    assertSameMotion("foglio", spring(0.9f, 500f, 0.5f), FluidMotion.SheetSettle, to = 400f)
  }

  @Test
  fun `the popover timings and springs are the numbers the modal wrote`() {
    assertEquals(110, FluidMotion.PopoverFadeInMillis)
    assertEquals(240, FluidMotion.PopoverFadeOutMillis)
    assertEquals(280, FluidMotion.PopoverWindowFadeOutMillis)
    assertEquals(0.92f, FluidMotion.PopoverWindowRunUpEnd, 0f)
    assertSameMotion(
      "rincorsa",
      tween(130, easing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.55f)),
      FluidMotion.PopoverWindowRunUp,
      to = FluidMotion.PopoverWindowRunUpEnd,
    )
    assertSameMotion("assestamento X", spring(0.55f, 300f, 0.001f), FluidMotion.PopoverWindowSettleX)
    assertSameMotion("assestamento Y", spring(0.62f, 300f, 0.001f), FluidMotion.PopoverWindowSettleY)
    assertSameMotion("apertura X", spring(0.62f, 260f, 0.001f), FluidMotion.PopoverOpenX)
    assertSameMotion("apertura Y", spring(0.74f, 260f, 0.001f), FluidMotion.PopoverOpenY)
    assertSameMotion("uscita della finestra", spring(1f, 620f, 0.001f), FluidMotion.PopoverWindowExit)
    assertSameMotion("uscita classica", spring(1f, 420f, 0.001f), FluidMotion.PopoverExit)
  }

  @Test
  fun `the two popover exits keep their order, the window lands before its fade ends`() {
    // L'uscita della finestra e' piu' rigida di quella classica: e' cosi' che atterra prima della dissolvenza.
    assertTrue(
      durationNanos(FluidMotion.PopoverWindowExit, 1f, 0f) < durationNanos(FluidMotion.PopoverExit, 1f, 0f),
    )
  }

  // --- La goccia di Kyant ------------------------------------------------------------------------

  @Test
  fun `the drop springs are Kyant's, the five of them`() {
    val threshold = 0.001f
    assertSameMotion("valore", spring(1f, 1000f, threshold), FluidMotion.dropValue(threshold), to = 3f)
    assertSameMotion("velocita' di Kyant", spring(0.5f, 300f, threshold * 10f), FluidMotion.dropVelocity(0.5f, threshold * 10f), to = 2f)
    assertSameMotion("velocita' critica del rail", spring(1f, 300f, threshold * 10f), FluidMotion.dropVelocity(1f, threshold * 10f), to = 2f)
    assertSameMotion("pressione", spring(1f, 1000f, 0.001f), FluidMotion.DropPress)
    assertSameMotion("scala X", spring(0.6f, 250f, 0.001f), FluidMotion.DropScaleX, to = 1.4f)
    assertSameMotion("scala Y", spring(0.7f, 250f, 0.001f), FluidMotion.DropScaleY, to = 1.4f)
  }

  @Test
  fun `the glow of the finger follows and fades on the same springs as before`() {
    assertEquals(spring(0.5f, 300f, Offset.VisibilityThreshold), FluidMotion.GlowPosition)
    assertEquals(spring(0.5f, 300f, 0.001f), FluidMotion.GlowPress)
  }

  // --- Piccoli pezzi -----------------------------------------------------------------------------

  @Test
  fun `a spring over pixels has the half pixel threshold, and the old call sites say the same`() {
    assertEquals(0.5f, FluidMotion.PixelThreshold, 0f)
    // Lo scatto di scorrimento, il rimbalzo, il ritorno di una card: tutti scritti con 0,5.
    assertSameMotion(
      "scatto",
      spring(dampingRatio = FluidMotion.DampingChrome, stiffness = FluidMotion.ResponseSnappy, visibilityThreshold = 0.5f),
      FluidMotion.pixels(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy),
      to = 120f,
    )
    assertSameMotion(
      "rimbalzo",
      spring(dampingRatio = FluidMotion.DampingChrome, stiffness = FluidMotion.ResponseStandard, visibilityThreshold = 0.5f),
      FluidMotion.pixels(FluidMotion.DampingChrome, FluidMotion.ResponseStandard),
      to = 120f,
    )
    assertSameMotion(
      "ritorno della card",
      spring(dampingRatio = FluidMotion.DampingStandard, stiffness = FluidMotion.ResponseSnappy, visibilityThreshold = 0.5f),
      notificationSpringBack(),
      to = 120f,
    )
  }

  @Test
  fun `the flick is the spring Compose used when nobody gave one`() {
    // `animateFloatAsState`, `animateColorAsState` e `Transition.animateFloat` senza spec: critica, 1500.
    assertSameMotion("scatto", spring(visibilityThreshold = 0.01f), FluidMotion.flick())
    assertSameMotion("scatto di serie", spring(), FluidMotion.flick())
  }

  @Test
  fun `a tuned spring is exactly the spring of its two numbers`() {
    assertSameMotion(
      "molla data",
      spring(FluidMotion.DampingFluid, FluidMotion.ResponseStandard),
      FluidMotion.tuned(FluidMotion.DampingFluid, FluidMotion.ResponseStandard),
    )
  }

  // --- Il respiro e i giri -----------------------------------------------------------------------

  @Test
  fun `the pulse is the infinite tween it was, in phase with whoever asks`() {
    for (offset in listOf(0, 250, 899, 1_700)) {
      val before = infiniteRepeatable<Float>(
        animation = tween(durationMillis = 900, easing = FluidMotion.EaseInOut),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = StartOffset(offset, StartOffsetType.FastForward),
      )
      assertSameMotion("respiro, fase $offset", before, FluidMotion.pulse(FluidMotion.PulseMillis, offset))
    }
    assertEquals(900, FluidMotion.PulseMillis)
  }

  @Test
  fun `the pulse lows are the ones the dot and the step wrote`() {
    assertEquals(0.35f, FluidMotion.PulseLowAlpha, 0f)
    assertEquals(0.55f, FluidMotion.PulseFillLowAlpha, 0f)
    // Un riempimento non scende quanto un pallino: a 0,35 il passo in corso sembrerebbe quello da fare.
    assertTrue(FluidMotion.PulseFillLowAlpha > FluidMotion.PulseLowAlpha)
  }

  @Test
  fun `the loops are the restarting tweens the spinner, the bar, the sheen and the hint wrote`() {
    assertSameMotion(
      "spinner",
      infiniteRepeatable<Float>(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
      FluidMotion.loop(FluidMotion.SpinnerMillis),
      to = 8f,
    )
    assertSameMotion(
      "barra indeterminata",
      infiniteRepeatable<Float>(tween(durationMillis = 1100, easing = FluidMotion.EaseInOut), RepeatMode.Restart),
      FluidMotion.loop(FluidMotion.IndeterminateMillis, FluidMotion.EaseInOut),
    )
    assertSameMotion(
      "riflesso",
      infiniteRepeatable<Float>(
        tween(durationMillis = 2600, delayMillis = 1900, easing = FluidMotion.EaseInOut),
        RepeatMode.Restart,
      ),
      FluidMotion.loop(2600, FluidMotion.EaseInOut, restMillis = 1900),
    )
  }
}
