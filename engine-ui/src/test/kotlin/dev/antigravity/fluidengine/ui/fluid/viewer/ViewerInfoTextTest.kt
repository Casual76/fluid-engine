package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.antigravity.fluidengine.ui.fluid.fluidContrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerInfoTextTest {
  @Test fun titleAndFactsMeetAAEvenWhenTheGlassAndPageHaveOppositePolarity() {
    val surfaces = listOf(Color(0xff18191c), Color.White, Color(0xff385671), Color(0xffd9c1a0))
    val backdrops = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue)
    for (start in surfaces) for (end in surfaces) for (step in 0..255) {
      val fill = end.copy(alpha = step / 255f).compositeOver(start)
      for (preferred in listOf(Color(0xff151618), Color(0xffeeeeef))) {
        val text = viewerInfoText(fill, preferred)
        for (back in backdrops) {
          val background = text.support.compositeOver(back)
          val ratio = fluidContrastRatio(text.ink, background)
          assertTrue("start=$start end=$end step=$step back=$back ratio=$ratio", ratio >= 4.5f)
        }
      }
    }
  }
}
