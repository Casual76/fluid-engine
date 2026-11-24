package dev.antigravity.fluidengine.sample.measure

import android.content.ContentProvider
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassStartupLayerTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun mergedStartupProviderPreparesBudgetBeforeFirstStandaloneBackdrop() {
    val context = compose.activity.application
    val provider = context.packageManager.resolveContentProvider(
      context.packageName + ".androidx-startup", PackageManager.GET_META_DATA)!!
    assertNotNull(provider.metaData.getString("dev.antigravity.fluidengine.ui.glass.quality.android.GlassStartup"))
    // Si avvia il provider del manifest unito, come farebbe Android prima dell'Activity.
    Robolectric.buildContentProvider(Class.forName(provider.name).asSubclass(ContentProvider::class.java)).create(provider)
    val early = ReflectionHelpers.getStaticField<FluidGlassResources>(FluidGlassResources::class.java, "process")
    assertNotNull(early)
    var layerCreated = false
    compose.setContent {
      assertSame(early.budget, FluidGlassResources.get(context).budget)
      rememberGlassBackdrop()
      SideEffect { layerCreated = true }
    }
    compose.runOnIdle { assertTrue(layerCreated) }
  }
}
