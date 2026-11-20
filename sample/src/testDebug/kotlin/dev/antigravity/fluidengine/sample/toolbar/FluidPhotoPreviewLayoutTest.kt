@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.toolbar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidCustomLiftedPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h700dp-mdpi")
class FluidPhotoPreviewLayoutTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Un ingresso già a metà corsa deve usare la taglia del figlio nella prima misura.
   * Il suo contenuto largo sporge: boundsInRoot attraversa il ritaglio Compose effettivo. */
  @Test fun measuredCustomContentIsClippedToTheMovingBodyAlsoOnReturn() {
    val progress = mutableFloatStateOf(0.5f)
    compose.setContent {
      Box(Modifier.fillMaxSize().testTag("host"), contentAlignment = AbsoluteAlignment.TopLeft) {
        FluidCustomLiftedPreview(
          source = Rect(22f, 200f, 122f, 300f), recording = null,
          custom = { Box(Modifier.size(280.dp, 180.dp).testTag("foto")) }, shape = null,
          viewport = Rect(0f, 0f, 400f, 600f), margins = Rect(16f, 16f, 384f, 584f),
          progress = { progress.floatValue }, anchorScale = { 1f }, presence = { 1f },
          paneTint = null, backdrop = null, onMeasured = {},
        )
      }
    }
    fun check(expected: Rect) {
      val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
      val actual = compose.onNodeWithTag("foto").fetchSemanticsNode().boundsInRoot.translate(-host.topLeft)
      assertEquals(expected.left, actual.left, 1f)
      assertEquals(expected.top, actual.top, 1f)
      assertEquals(expected.right, actual.right, 1f)
      assertEquals(expected.bottom, actual.bottom, 1f)
    }
    check(Rect(19f, 180f, 209f, 320f))
    compose.runOnIdle { progress.floatValue = 1f }
    check(Rect(16f, 160f, 296f, 340f))
    compose.runOnIdle { progress.floatValue = 0.5f }
    check(Rect(19f, 180f, 209f, 320f))
    compose.runOnIdle { progress.floatValue = 0f }
    check(Rect(22f, 200f, 122f, 300f))
  }
}
