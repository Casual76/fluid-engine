package dev.antigravity.fluidengine.ui.fluid

import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Un'attività che ricorda ogni cambio delle istantanee delle recenti. */
class R2F5aRecentsActivity : Activity() {
  val recents = mutableListOf<Boolean>()

  override fun setRecentsScreenshotEnabled(enabled: Boolean) {
    recents += enabled
    super.setRecentsScreenshotEnabled(enabled)
  }
}

/**
 * R2-F5a · in Robolectric: il segno della finestra protetta non riaccende le recenti che l'app aveva spento da sé
 * (difetto 30), e il paragrafo chiuso del testo che si espande non spezza un emoji né stacca un accento (minore).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class R2F5aWindowAndTextTest {

  @Test fun `un valore scoperto e ricoperto non riaccende le recenti spente dall'app`() {
    val activity = Robolectric.buildActivity(R2F5aRecentsActivity::class.java).setup().get()
    // L'app tiene fuori dalle recenti tutta l'attivita'.
    activity.setRecentsScreenshotEnabled(false)
    FluidSecureWindow.acquire(activity, blockScreenshots = false)
    FluidSecureWindow.release(activity, blockScreenshots = false)
    assertEquals(0, FluidSecureWindow.holdsOf(activity.window))
    assertFalse("le recenti dell'app sono state riaccese: ${activity.recents}", activity.recents.last())
  }

  @Test fun `il segno degli screenshot torna com'era`() {
    val activity = Robolectric.buildActivity(R2F5aRecentsActivity::class.java).setup().get()
    FluidSecureWindow.acquire(activity, blockScreenshots = true)
    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    FluidSecureWindow.release(activity, blockScreenshots = true)
    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
    // Un'app che aveva gia' FLAG_SECURE lo tiene.
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    FluidSecureWindow.acquire(activity, blockScreenshots = true)
    FluidSecureWindow.release(activity, blockScreenshots = true)
    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
  }

  @Test fun `il paragrafo chiuso si taglia fra due caratteri interi`() {
    val context = RuntimeEnvironment.getApplication()
    val measurer = TextMeasurer(createFontFamilyResolver(context), Density(2f), LayoutDirection.Ltr)
    val style = TextStyle(fontSize = 17.sp)
    var truncated = 0
    for (sample in listOf("😀".repeat(160), "é".repeat(240), "Caffè 😀 ".repeat(40))) {
      val text = AnnotatedString(sample)
      for (width in 120..720 step 7) {
        val plan = FluidExpandablePlan.of(measurer, text, style, width, 2, "altro", Color.Blue)
        if (!plan.truncated) continue
        truncated++
        val cut = plan.cut
        if (cut <= 0 || cut >= sample.length) continue
        assertFalse("meta' di un emoji a $width px: «${sample.substring(0, cut).takeLast(4)}»", Character.isLowSurrogate(sample[cut]))
        assertNotEquals(
          "accento staccato a $width px",
          Character.NON_SPACING_MARK.toInt(),
          Character.getType(sample[cut]),
        )
      }
    }
    assertTrue("nessun paragrafo tagliato: la prova non prova niente", truncated > 30)
  }
}
