package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * La luce della goccia a metà pressione (F1B.3, giro di correzione): ogni rettangolo della luce arriva al nodo con la sua
 * miscela e l'alfa per la quantità di luce. Lo scope del nodo è un registratore (un `Proxy` di `DrawScope`): sul JVM
 * non c'è niente da disegnare, si guarda solo cosa gli viene chiesto.
 */
class GlassDropFadedLightTest {

  private class Rect(val alpha: Float, val blend: Int)

  private fun recorder(calls: MutableList<Rect>): DrawScope =
    Proxy.newProxyInstance(DrawScope::class.java.classLoader, arrayOf(DrawScope::class.java)) { _, method, args ->
      // drawRect(brush o colore, topLeft, size, alpha, style, colorFilter, blendMode)
      if (method.name.startsWith("drawRect")) calls += Rect(args[3] as Float, args[6] as Int)
      when (method.returnType) {
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 1f
        java.lang.Integer.TYPE -> 0
        java.lang.Boolean.TYPE -> false
        else -> null
      }
    } as DrawScope

  @Test
  fun `each rectangle of the light keeps its blend and is as strong as the pressure`() {
    val calls = ArrayList<Rect>()
    GlassDropFadedLight().draw(recorder(calls), 0.25f) {
      // Come `drawGlassLight`: il riflesso sommato, il filo sopra.
      drawRect(brush = SolidColor(Color.White), blendMode = BlendMode.Plus)
      drawRect(color = Color.Black, alpha = 0.5f)
    }
    assertEquals(2, calls.size)
    assertEquals(0.25f, calls[0].alpha, 1e-6f)
    assertEquals("il riflesso resta una somma sulla goccia", BlendMode.Plus.hashCode(), calls[0].blend)
    assertEquals(0.125f, calls[1].alpha, 1e-6f)
    assertEquals(BlendMode.SrcOver.hashCode(), calls[1].blend)
  }

  @Test
  fun `the same node scope is wrapped once, a new one again`() {
    val light = GlassDropFadedLight()
    val base = recorder(ArrayList())
    var first: DrawScope? = null
    var second: DrawScope? = null
    light.draw(base, 0.3f) { first = this }
    light.draw(base, 0.6f) { second = this }
    assertSame("nessuna allocazione da un fotogramma all'altro", first, second)
    var other: DrawScope? = null
    light.draw(recorder(ArrayList()), 0.6f) { other = this }
    assertNotSame(first, other)
  }
}
