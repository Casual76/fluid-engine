package dev.antigravity.fluidengine.viewer.scene

import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.antigravity.fluidengine.viewer.FluidFileThumbnails
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneThumbnailTest {
  @Test fun uvTextureMakesBothClothColorsVisibleInTheThumbnail() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    org.robolectric.Shadows.shadowOf(context.getSystemService(android.app.ActivityManager::class.java)).setMemoryClass(512)
    dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources.get(context).budget
      .updateLimits(dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits.Full)
    val file = FluidViewerSource.of("tessuto.gltf", context.cacheDir, "model/gltf+json",
      read = { requireNotNull(javaClass.getResourceAsStream("/corpus/tessuto.gltf")) })
    val source = object : FluidSceneSource, FluidViewerSource by file {
      override suspend fun openRelative(path: String) = javaClass.getResourceAsStream("/corpus/$path")
    }
    source.use {
      val thumbnail = FluidFileThumbnails.read(context, source, 300)
      assertNotNull(thumbnail)
      val bitmap = requireNotNull(thumbnail).image.asAndroidBitmap()
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      assertTrue("warm thread", pixels.count { Color.red(it) > Color.blue(it) * 1.45f && Color.red(it) > 70 } > 250)
      assertTrue("blue fabric", pixels.count { Color.blue(it) > Color.red(it) * 1.35f && Color.blue(it) > 35 } > 250)
    }
  }
}
