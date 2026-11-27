package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.26h · **Gli angoli in pixel alla scala della cattura.** Il corpo di una lastra si costruisce alla taglia e alla
 * densità della cattura ([GlassDrawInputs.setBody]): un angolo in dp scende con lei, uno in percentuale anche. Un angolo in
 * pixel no: `CornerSize(px).toPx` non guarda né la taglia né la densità. Il foglio di vetro ([FluidGlassModalSheet]) ha
 * gli angoli in pixel (`FluidRadius.Sheet` già convertito, e il raggio concentrico di sotto), e al gradino più basso del
 * Tab S9 la cattura è a un ottavo: il raggio di 80,75 px arrivava a mezzo lato corto (82 px), e il corpo era una capsula,
 * un ovale tinto dentro il rettangolo ritagliato a piena risoluzione (la finestra del 27 novembre, «Foglio trascinato»).
 *
 * Il metro è la stessa sagoma con gli angoli in dp: alla stessa scala deve dare gli stessi raggi. A scala piena non cambia
 * niente, al bit.
 */
class GlassCaptureCornersTest {
  /** Il Tab S9: 340 dpi, 2,125 pixel per dp. */
  private val screen = Density(2.125f)
  private val node = Size(1318f, 2489f)
  private val sheetPx = 38f * 2.125f

  private fun sheet(top: CornerSize, bottom: CornerSize): Shape =
    ContinuousCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)

  private fun body(shape: Shape, scale: Float): GlassSlabGeometry {
    val size = Size(node.width * scale, node.height * scale)
    return glassSlabGeometry(shape, size, Density(screen.density * scale), LayoutDirection.Ltr, node.maxDimension)
  }

  @Test fun `un angolo in pixel scende con la cattura come lo stesso angolo in dp`() {
    val inPixels = sheet(CornerSize(sheetPx), CornerSize(sheetPx))
    val inDp = sheet(CornerSize(38.dp), CornerSize(38.dp))
    for (scale in floatArrayOf(0.5f, 0.25f, 0.125f)) {
      val px = body(inPixels, scale)
      val dp = body(inDp, scale)
      println("F726H | scala $scale | in pixel ${px.topLeft} ${px.bottomLeft} | in dp ${dp.topLeft} ${dp.bottomLeft}")
      assertEquals("scala $scale, in alto", dp.topLeft, px.topLeft, 0.05f)
      assertEquals("scala $scale, in basso", dp.bottomRight, px.bottomRight, 0.05f)
      assertEquals("scala $scale, il raccordo", dp.smoothing, px.smoothing, 0f)
    }
  }

  @Test fun `a un ottavo il foglio del Tab S9 resta un rettangolo arrotondato e non una capsula`() {
    val scale = 0.125f
    val g = body(sheet(CornerSize(sheetPx), CornerSize(60f)), scale)
    val half = node.width * scale / 2f
    println("F726H | foglio a un ottavo | raggi ${g.topLeft} ${g.bottomLeft} | mezzo lato corto $half")
    assertEquals("il raggio in alto, a un ottavo", sheetPx * scale, g.topLeft, 0.05f)
    assertEquals("il raggio in basso, a un ottavo", 60f * scale, g.bottomLeft, 0.05f)
    assertTrue("il raggio in alto ${g.topLeft} non deve arrivare a mezzo lato ($half)", g.topLeft < half / 4f)
  }

  @Test fun `a scala piena gli angoli restano quelli di prima al bit`() {
    val shapes = listOf(
      sheet(CornerSize(sheetPx), CornerSize(60f)),
      sheet(CornerSize(38.dp), CornerSize(20.dp)),
      FluidCapsuleShape,
      ContinuousCornerShape(24.dp),
    )
    for (shape in shapes) {
      val g = glassSlabGeometry(shape, node, screen, LayoutDirection.Ltr, node.maxDimension)
      val corners = shape as androidx.compose.foundation.shape.CornerBasedShape
      // Il conto di prima: i raggi letti alla taglia e alla densità date, senza altro.
      val before = glassSlabGeometry(
        width = node.width,
        height = node.height,
        topLeft = corners.topStart.toPx(node, screen),
        topRight = corners.topEnd.toPx(node, screen),
        bottomRight = corners.bottomEnd.toPx(node, screen),
        bottomLeft = corners.bottomStart.toPx(node, screen),
        smoothing = (shape as? ContinuousCornerShape)?.smoothing ?: 0f,
        continuous = shape is ContinuousCornerShape,
        small = glassSlabIsSmall(node.maxDimension),
      )
      assertEquals("$shape, in alto a sinistra", before.topLeft.toRawBits(), g.topLeft.toRawBits())
      assertEquals("$shape, in basso a destra", before.bottomRight.toRawBits(), g.bottomRight.toRawBits())
      assertEquals("$shape, il ritaglio", before.clipScale.toRawBits(), g.clipScale.toRawBits())
    }
  }

  @Test fun `una percentuale e un dp alla scala della cattura restano quelli di prima`() {
    val scale = 0.125f
    val size = Size(node.width * scale, node.height * scale)
    val density = Density(screen.density * scale)
    for (shape in listOf(FluidCapsuleShape, ContinuousCornerShape(24.dp))) {
      val g = glassSlabGeometry(shape, size, density, LayoutDirection.Ltr, node.maxDimension)
      val corners = shape as androidx.compose.foundation.shape.CornerBasedShape
      val before = glassSlabGeometry(
        width = size.width,
        height = size.height,
        topLeft = corners.topStart.toPx(size, density),
        topRight = corners.topEnd.toPx(size, density),
        bottomRight = corners.bottomEnd.toPx(size, density),
        bottomLeft = corners.bottomStart.toPx(size, density),
        smoothing = (shape as? ContinuousCornerShape)?.smoothing ?: 0f,
        continuous = true,
        small = glassSlabIsSmall(node.maxDimension),
      )
      assertEquals("$shape a un ottavo", before.topLeft.toRawBits(), g.topLeft.toRawBits())
    }
  }
}
