package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdge
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdgeSide
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A6 (F4A, filo): la striscia del bordo superiore, da sola su una tinta unita, non disegna nessuna riga.
 *
 * Il difetto: a intensità 1 una sola riga di pixel usciva a 255,255,255 sotto i tasti, larga quanto lo schermo, e solo
 * se il confine del ramo `s > 0.0001` dello shader cadeva fra una riga pari e la dispari seguente (le GPU eseguono i pixel
 * a gruppi di 2×2). Per questo la scena gira a **quattro altezze**, mezzo dp l'una dall'altra: a densità 2 il confine
 * si sposta di un pixel a ogni passo e tocca righe pari e dispari. Su una tinta unita la striscia può solo schiarire
 * verso il velo (bianco) e sempre meno scendendo, quindi ogni riga deve essere al più quella sopra, più [Tolerance]
 * livelli (la grana e i due arrotondamenti a 8 bit della dissolvenza finale, osservati: fino a 2); il filo ne aveva 15.
 * Sotto la striscia c'è il fondo, tale e quale.
 */
@RunWith(AndroidJUnit4::class)
class FluidScrollEdgeLineInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private companion object {
    const val Tolerance = 3
  }

  @Test fun topEdgeOnAUniformSourceDrawsNoLine() {
    val background = Color(0xfff0f1f5)
    val heights = listOf(68f, 68.5f, 69f, 69.5f)
    val index = mutableIntStateOf(0)
    compose.setContent {
      val source = rememberGlassBackdrop()
      key(index.intValue) {
        Box(Modifier.size(240.dp, 160.dp).testTag("striscia")) {
          Box(Modifier.fillMaxSize().glassBackdropSource(source).background(background))
          FluidScrollEdge(source, FluidScrollEdgeSide.Top, heights[index.intValue].dp, color = Color.White)
        }
      }
    }
    val failures = ArrayList<String>()
    for (n in heights.indices) {
      compose.runOnIdle { index.intValue = n }
      compose.waitForIdle()
      val image = compose.onNodeWithTag("striscia").captureToImage().toPixelMap()
      // La striscia è alta `height + 1,4 · 44 dp`; sotto di lei, a densità 2, il fondo.
      val stripPx = ((heights[n] + 1.4f * 44f) * 2f).toInt()
      for (x in listOf(image.width / 4, image.width / 2, image.width * 3 / 4)) {
        for (y in 1 until image.height) {
          val above = image[x, y - 1].toArgb()
          val here = image[x, y].toArgb()
          for (shift in intArrayOf(16, 8, 0)) {
            val a = (above shr shift) and 0xff
            val h = (here shr shift) and 0xff
            if (h > a + Tolerance) failures += "altezza ${heights[n]} dp, x=$x y=$y: %08x sopra %08x".format(here, above)
          }
          if (y > stripPx + 1 && here != background.toArgb()) {
            failures += "altezza ${heights[n]} dp, x=$x y=$y sotto la striscia (${stripPx} px): %08x".format(here)
          }
        }
      }
    }
    assertTrue("righe fuori dal profilo della striscia:\n" + failures.take(12).joinToString("\n"), failures.isEmpty())
  }
}
