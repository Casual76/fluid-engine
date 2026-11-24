package dev.antigravity.fluidengine.sample.novita

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p12AddressActivityTest {
  @get:Rule val compose = createEmptyComposeRule()
  private lateinit var host: ActivityController<AddressDemoActivity>
  private val ctx: Context get() = ApplicationProvider.getApplicationContext()
  private fun view(path: String) = Intent(Intent.ACTION_VIEW, Uri.parse("fluidglass://$path"), ctx, AddressDemoActivity::class.java)

  private fun launch(path: String, saved: Bundle? = null) {
    compose.runOnUiThread {
      host = Robolectric.buildActivity(AddressDemoActivity::class.java, view(path))
      if (saved == null) host.setup() else host.setup(saved)
      host.windowFocusChanged(true)
    }
    compose.waitForIdle()
  }

  @After fun close() {
    if (::host.isInitialized) compose.runOnUiThread { host.pause().stop().destroy() }
  }

  private fun page(path: String) = compose.onNodeWithTag("indirizzo:fluidglass://$path").assertIsDisplayed()
  private fun back() = compose.runOnIdle { host.get().onBackPressedDispatcher.onBackPressed() }
  private fun send(path: String) = compose.runOnIdle { host.newIntent(view(path)) }

  private fun predictive(cancel: Boolean) {
    val dispatcher = host.get().onBackPressedDispatcher
    compose.runOnIdle {
      dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 360f, 0f, BackEventCompat.EDGE_LEFT))
      dispatcher.dispatchOnBackProgressed(BackEventCompat(440f, 360f, .68f, BackEventCompat.EDGE_LEFT))
    }
    compose.mainClock.advanceTimeByFrame()
    compose.runOnUiThread {
      if (cancel) dispatcher.dispatchOnBackCancelled() else dispatcher.onBackPressed()
    }
    compose.waitForIdle()
  }

  @Test fun coldViewCopiesCanonicalLinkAndBackLeavesAtRoot() {
    launch("nota/123")
    page("nota/123")
    compose.onNodeWithText("Copia link").performClick()
    val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
    assertEquals("fluidglass://nota/123", clip?.getItemAt(0)?.text?.toString())
    predictive(cancel = true)
    page("nota/123")
    predictive(cancel = false)
    page("quaderno/2")
    back()
    page("home")
    back()
    assertTrue(host.get().isFinishing)
  }

  @Test fun warmViewReplacesAnUnrelatedStackAndMissingKeepsTheParent() {
    launch("nota/7")
    compose.onNodeWithText("Entra nella demo").assertIsDisplayed()
    send("nota/123")
    page("nota/123")
    send("sconosciuta")
    page("nota/123")
    send("nota/404")
    compose.onNodeWithText("Torna al quaderno").performClick()
    page("quaderno/2")
    predictive(cancel = false)
    page("home")
    back()
    assertTrue(host.get().isFinishing)
  }

  private fun restartWithOriginalIntent(path: String) {
    val parcel = Parcel.obtain()
    val saved = try {
      compose.runOnIdle {
        val bundle = Bundle()
        host.pause().saveInstanceState(bundle).stop().destroy()
        parcel.writeBundle(bundle)
        parcel.setDataPosition(0)
        requireNotNull(parcel.readBundle(AddressDemoActivity::class.java.classLoader))
      }
    } finally { parcel.recycle() }
    launch(path, saved)
  }

  @Test fun newActivityAndViewModelStoreRestoreTheParentInsteadOfReplayingTheInitialIntent() {
    launch("nota/123")
    back()
    page("quaderno/2")
    restartWithOriginalIntent("nota/123")
    page("quaderno/2")
    back()
    page("home")
  }

  @Test fun pendingSignInSurvivesProcessStateAndResumesTheExactNote() {
    launch("home")
    send("nota/7")
    compose.onNodeWithText("Entra nella demo").assertIsDisplayed()
    restartWithOriginalIntent("home")
    compose.onNodeWithText("Entra nella demo").performClick()
    page("nota/7")
    back()
    page("quaderno/2")
  }
}
