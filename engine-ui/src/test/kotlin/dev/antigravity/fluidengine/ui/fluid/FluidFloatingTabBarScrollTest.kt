package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quando la barra flottante si ripiega (3.0.1).
 *
 * Il difetto era nel posto in cui si decideva: `onPreScroll` riceve tutto il dito, anche su una pagina
 * che non scorre, e la barra si ripiegava su una pagina corta a ogni trascinamento verso l'alto. Ora si
 * decide in `onPostScroll`, su quello che il contenuto ha fatto scorrere davvero. E un cambio di pagina
 * la riapre e chiude il fermo dello scorrimento, senza che l'app debba chiamare niente.
 */
class FluidFloatingTabBarScrollTest {

  private val user = NestedScrollSource.UserInput

  private fun connection() = FluidFloatingTabBarScrollConnection(
    scrollThresholdPx = 100f,
    expandThresholdPx = 16f,
  )

  /** Un trascinamento come lo vede la barra: il dito in pre-scroll, poi quanto e' scorso e quanto avanza. */
  private fun FluidFloatingTabBarScrollConnection.drag(finger: Float, scrolled: Float) {
    onPreScroll(Offset(0f, finger), user)
    onPostScroll(consumed = Offset(0f, scrolled), available = Offset(0f, finger - scrolled), source = user)
  }

  @Test
  fun `su una pagina che non scorre il dito non ripiega la barra`() {
    val bar = connection()
    repeat(20) { bar.drag(finger = -30f, scrolled = 0f) }
    assertFalse(bar.isInline)
  }

  @Test
  fun `una pagina che scorre davvero la ripiega oltre la soglia`() {
    val bar = connection()
    repeat(3) { bar.drag(finger = -30f, scrolled = -30f) }
    assertFalse("90 px non bastano", bar.isInline)
    bar.drag(finger = -30f, scrolled = -30f)
    assertTrue(bar.isInline)
  }

  @Test
  fun `solo la parte che scorre conta`() {
    val bar = connection()
    // In fondo alla lista: il dito corre, la lista ne usa un pezzo.
    repeat(9) { bar.drag(finger = -40f, scrolled = -10f) }
    assertFalse("90 px scorsi su 360 di dito", bar.isInline)
    bar.drag(finger = -40f, scrolled = -10f)
    assertTrue(bar.isInline)
  }

  @Test
  fun `tirata giu' in cima si riapre, anche se l'elastico del bordo consuma il resto`() {
    val bar = connection()
    bar.inline()
    // La `FluidScreen` stira il bordo in alto: il resto lo consuma lei, `available` arriva a zero.
    bar.drag(finger = 20f, scrolled = 20f)
    assertFalse(bar.isInline)
  }

  @Test
  fun `bloccata non si muove`() {
    val bar = connection()
    bar.locked = true
    repeat(10) { bar.drag(finger = -50f, scrolled = -50f) }
    assertFalse(bar.isInline)
  }

  @Test
  fun `un cambio di pagina la riapre e chiude lo scorrimento`() = runTest {
    val bar = connection()
    repeat(5) { bar.drag(finger = -30f, scrolled = -30f) }
    assertTrue(bar.isInline)
    assertTrue("il dito non si e' ancora alzato", bar.scrolling)

    bar.routeChanged()

    assertFalse(bar.isInline)
    assertFalse(bar.scrolling)
    // E il conto riparte da zero: un altro mezzo scorrimento non la ripiega subito.
    bar.drag(finger = -60f, scrolled = -60f)
    assertFalse(bar.isInline)
    bar.onPreFling(Velocity.Zero)
    assertFalse(bar.scrolling)
  }
}
