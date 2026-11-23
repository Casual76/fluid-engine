package dev.antigravity.fluidengine.viewer.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.scene.FluidSceneViewerExtension
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.util.ServiceLoader

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w400dp-h800dp-mdpi")
class ArViewerRobolectricTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val activity = Robolectric.buildActivity(ComponentActivity::class.java)
  private var permissionRequests = 0
  private var installRequests = 0
  private var result = false
  private val permissions = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
      override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
      ) {
        assertEquals(Manifest.permission.CAMERA, input)
        permissionRequests++
        dispatchResult(requestCode, result)
      }
    }
  }

  @After fun close() {
    activity.pause().stop().destroy()
  }

  @Test fun hidesArOnUnsupportedHardware() {
    show(ArAvailability.Unsupported)
    compose.onNodeWithText("AR", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithText("3D preview").assertExists()
    assertEquals(0, permissionRequests)
  }

  @Test fun offersInstallationWithoutRequestingTheCamera() {
    show(ArAvailability.Installable)
    compose.onNodeWithText("AR", useUnmergedTree = true).performClick()
    compose.onNodeWithText("Continue to Google Play", useUnmergedTree = true).assertExists()
    assertEquals(0, installRequests)
    assertEquals(0, permissionRequests)
  }

  @Test fun showsExplanationAndReturnsToTheModelWhenPermissionIsDenied() {
    show(ArAvailability.Ready)
    compose.onNodeWithText("AR", useUnmergedTree = true).performClick()
    compose.onNodeWithText("Use the camera in AR", useUnmergedTree = true).assertExists()
    assertEquals(0, permissionRequests)
    compose.onNodeWithText("Continue", useUnmergedTree = true).performClick()
    compose.onNodeWithText("Camera access was denied.", substring = true, useUnmergedTree = true).assertExists()
    compose.onNodeWithText("OK", useUnmergedTree = true).performClick()
    compose.onNodeWithText("3D preview").assertExists()
    assertEquals(1, permissionRequests)
  }

  @Test fun systemBackFromTheArDialogReturnsToTheModel() {
    result = true
    show(ArAvailability.Ready)
    compose.onNodeWithText("AR", useUnmergedTree = true).performClick()
    compose.onNodeWithText("Continue", useUnmergedTree = true).performClick()
    compose.onNodeWithText("AR room").assertExists()
    compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithText("AR room").assertDoesNotExist()
    compose.onNodeWithText("3D preview").assertExists()
  }

  @Test fun providerIsRegistered() {
    activity.setup()
    val extension = FluidSceneViewerExtension::class.java
    assertTrue(ServiceLoader.load(extension, extension.classLoader).any { it is FluidArSceneExtension })
  }

  private fun show(availability: ArAvailability) {
    val fake = object : ArDevice {
      override suspend fun availability(context: Context) = availability
      override fun prepare(activity: Activity, userRequested: Boolean): Boolean {
        installRequests++
        return true
      }
    }
    val controls = ArSceneControls(fake) { _, _ -> Text("AR room") }
    val content = FluidViewerContent.Custom(actions = { controls.actions(this) }) {
      Text("3D preview")
      controls.Overlay(active)
    }
    activity.setup().get().setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides permissions) {
        FluidTheme(EngineSettings()) {
          FluidFileViewer(count = 1, content = { content })
        }
      }
    }
    compose.waitForIdle()
  }
}
