package dev.antigravity.fluidengine.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * F8.16c · I tratti finiti di androidx.ink si disegnano col canvas portato nelle coordinate del tratto
 * ([drawInStrokeSpace]): `CanvasStrokeRenderer.draw` non applica la matrice che riceve. Sul Tab S9 (26/11/2026) i tratti
 * uscivano piccoli di 2,625 volte (la densita') e spostati verso l'angolo. Qui un «tratto» disegnato nelle sue coordinate
 * (un punto del foglio) deve cadere nel pixel dello schermo che la vista gli assegna, e non nel pixel con lo stesso numero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidInkRenderTransformTest {
  @Test fun aPointOfTheSheetLandsWhereTheViewPutsIt() {
    // La vista di un foglio bianco a densita' 2,625 (un telefono a 420 dpi): scala 2,625, angolo in (31,5, 220,5).
    val view = InkView(scale = 2.625f, ox = 31.5f, oy = 220.5f)
    val matrix = view.matrix(Matrix())
    val bitmap = Bitmap.createBitmap(1080, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
    val canvas = Canvas(bitmap)
    val world = floatArrayOf(150f, 250f)
    val paint = Paint().apply { color = Color.BLACK }
    // Come fa androidx.ink: disegna nelle coordinate del tratto, e la matrice la deve portare il canvas.
    drawInStrokeSpace(canvas, matrix) { it.drawCircle(world[0], world[1], 2f, paint) }
    val screen = view.toScreen(androidx.compose.ui.geometry.Offset(world[0], world[1]))
    assertEquals(Color.BLACK, bitmap.getPixel(screen.x.roundToInt(), screen.y.roundToInt()))
    // Il difetto: lo stesso punto preso per pixel (150, 250) resta bianco.
    assertEquals(Color.WHITE, bitmap.getPixel(150, 250))
    // E il canvas torna com'era: un disegno dopo non eredita la matrice.
    assertTrue(canvas.getMatrix().isIdentity)
  }
}
