package dev.antigravity.fluidengine.ink

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

/** F8.16 · Il salvataggio dei tratti: si rilegge uguale, e un file sbagliato lo dice. */
class FluidInkCodecTest {
  @Test fun strokesComeBackTheSame() {
    val stylus = FluidInkPointsBuilder().apply {
      for (i in 0 until 50) add(i * 2f, i * 1.5f, i * 4f, pressure = i / 50f, tilt = 0.3f, orientation = 1.2f)
    }.build()
    val strokes = listOf(
      FluidInkStroke(3L, FluidInkTool.Pen, Color(0xFF007AFF), 2.5f, stylus, FluidInkInput.Stylus),
      FluidInkStroke(9L, FluidInkTool.Highlighter, Color(0xFFFFCC00).copy(alpha = 0.5f), 18f, FluidInkPoints.of(0f, 0f, 100f, 0f), FluidInkInput.Touch),
      FluidInkStroke(10L, FluidInkTool.Pencil, Color.Black, 2f, FluidInkPoints.of(0f, 0f, 50f, 50f, 0f, 0f), FluidInkInput.Synthetic, FluidInkShapeKind.Triangle),
    )
    val back = FluidInkCodec.decode(FluidInkCodec.encode(strokes))
    assertEquals(strokes.size, back.size)
    for ((a, b) in strokes.zip(back)) {
      assertEquals(a.id, b.id)
      assertEquals(a.tool, b.tool)
      assertEquals(a.size, b.size, 0f)
      assertEquals(a.input, b.input)
      assertEquals(a.shape, b.shape)
      assertEquals(a.points.size, b.points.size)
      assertEquals(a.points.hasPressure, b.points.hasPressure)
      assertEquals(a.points.hasTilt, b.points.hasTilt)
      assertArrayEquals(a.points.data.copyOf(a.points.size * 6), b.points.data.copyOf(b.points.size * 6), 0f)
      assertEquals(a.color.value, b.color.value)
    }
  }

  @Test fun aStateRoundTripsThroughEncode() {
    val s = FluidInkState()
    s.add(FluidInkStroke(s.newStrokeId(), FluidInkTool.Pen, Color.Red, 3f, FluidInkPoints.of(1f, 2f, 3f, 4f)))
    val other = FluidInkState()
    other.load(FluidInkCodec.decode(s.encode()))
    assertEquals(1, other.strokes.size)
    assertEquals(Color.Red.value, other.strokes.single().color.value)
  }

  @Test fun aFileThatIsNotADrawingFails() {
    var failed = false
    try {
      FluidInkCodec.decode("ciao, non sono un disegno".toByteArray())
    } catch (e: IOException) {
      failed = true
    }
    assertTrue(failed)
  }

  @Test fun aNewerVersionSaysSo() {
    val bytes = ByteArrayOutputStream().also { out ->
      DataOutputStream(out).apply { writeInt(0x46494E4B); writeShort(FluidInkCodec.Version + 1) }
    }.toByteArray()
    val message = try {
      FluidInkCodec.decode(bytes)
      null
    } catch (e: IOException) {
      e.message
    }
    assertTrue(message!!.contains("nuova"))
  }

  @Test fun aHundredStrokesStaySmall() {
    val strokes = List(100) { n ->
      val b = FluidInkPointsBuilder()
      for (i in 0 until 60) b.add(i * 3f, n * 10f + (i % 7), i * 8f)
      FluidInkStroke(n.toLong() + 1, FluidInkTool.Pen, Color.Black, 2f, b.build(), FluidInkInput.Touch)
    }
    val size = FluidInkCodec.encode(strokes).size
    // 6000 punti × 3 float = 72 kB grezzi: compressi stanno sotto i 60 kB.
    assertTrue("pesa $size byte", size < 60_000)
  }
}
