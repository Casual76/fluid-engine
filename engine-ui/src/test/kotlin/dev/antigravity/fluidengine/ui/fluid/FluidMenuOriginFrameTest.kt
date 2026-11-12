package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il segnaposto di partenza del menu che nasce da un tasto (F1B.3, `fluidMenuOriginFrame`): la taglia del tasto a
 * riposo, al centro del tasto premuto. Il motivo è il velo: sul lato scuro la superficie che viaggia parte dal velo
 * della forma di partenza, e un tasto da 44 dp gonfiato dal dito a ~65 cade nella fascia del velo dei grandi.
 */
class FluidMenuOriginFrameTest {

  private val rest = Rect(Offset(100f, 200f), androidx.compose.ui.geometry.Size(132f, 132f))

  @Test
  fun theOriginHasTheRestingSizeAndThePressedCentre() {
    // Il tasto premuto: gonfiato del 48% e spinto dal dito di 9 px a destra e 6 in basso.
    val pressed = Rect(rest.center.x + 9f - 98f, rest.center.y + 6f - 98f, rest.center.x + 9f + 98f, rest.center.y + 6f + 98f)
    val origin = fluidMenuOriginFrame(rest, pressed)
    assertEquals(rest.width, origin.width, 1e-4f)
    assertEquals(rest.height, origin.height, 1e-4f)
    assertEquals(pressed.center.x, origin.center.x, 1e-4f)
    assertEquals(pressed.center.y, origin.center.y, 1e-4f)
  }

  @Test
  fun aKeyThatIsNotPressedStartsWhereItIs() {
    assertEquals(rest, fluidMenuOriginFrame(rest, rest))
  }

  @Test
  fun aKeyWithoutAnAreaKeepsThePressedFrame() {
    val pressed = Rect(10f, 10f, 40f, 40f)
    assertEquals(pressed, fluidMenuOriginFrame(Rect.Zero, pressed))
  }

  @Test
  fun theDarkVeilStartsFromTheKeysOwnVeilNotFromTheInflatedOne() {
    // 44 dp a densità 2,125 (il tablet), gonfiato dal dito a 65 dp: il caso visto sul tablet.
    val density = 2.125f
    val key = Rect(0f, 0f, 44f * density, 44f * density)
    val inflated = Rect(key.center.x - 32.5f * density, key.center.y - 32.5f * density, key.center.x + 32.5f * density, key.center.y + 32.5f * density)
    val before = glassLiquidLargeVeilGain(inflated.minDimension / density)
    val after = glassLiquidLargeVeilGain(fluidMenuOriginFrame(key, inflated).minDimension / density)
    assertTrue("il segnaposto gonfiato era nella fascia dei grandi: $before", before > 0.25f)
    assertEquals(glassLiquidLargeVeilGain(44f), after, 0f)
    assertEquals(0f, after, 0f)
  }
}
