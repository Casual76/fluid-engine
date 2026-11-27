package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCenter
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyChannelInfo
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyDigestInfo
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyHealth
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyLiveState
import dev.antigravity.fluidengine.foundation.notify.FluidNotifySettings
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import dev.antigravity.fluidengine.foundation.notify.FluidReminderInfo
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationSettingsPage
import dev.antigravity.fluidengine.ui.fluid.FluidNotifyPageTags
import dev.antigravity.fluidengine.ui.fluid.FluidUpcomingRemindersPage
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R2 · La pagina delle impostazioni delle notifiche (F8.19) davanti ai difetti della review R2: gli orari del riepilogo
 * fedeli a quelli salvati (minuti e terzo orario compresi) e un tocco che non riesce ad aprire le impostazioni di Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p19NotifyR2UiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun themed(content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
  }

  private class Center(times: List<Int>, private val opens: Boolean = true) : FluidNotifyCenter {
    override val settings = MutableStateFlow(
      FluidNotifySettings(
        notificationsAllowed = true,
        channels = listOf(FluidNotifyChannelInfo("circolari", "Circolari", "Le comunicazioni della scuola", inDigest = true)),
        digest = FluidNotifyDigestInfo(enabled = true, times = times),
        live = FluidNotifyLiveState.Allowed,
      ),
    )
    override val reminders = MutableStateFlow(emptyList<FluidReminderInfo>())
    override val deliveries = MutableStateFlow(emptyList<FluidReminderDelivery>())
    override val health = MutableStateFlow(FluidNotifyHealth())
    val saved = ArrayList<List<Int>>()

    override fun refresh() = Unit
    override fun setChannelInDigest(channelId: String, inDigest: Boolean) = Unit
    override fun setDigest(enabled: Boolean, times: List<Int>) {
      saved += times
      settings.value = settings.value.copy(digest = settings.value.digest.copy(enabled = enabled, times = times))
    }
    override fun cancelReminder(id: String) = Unit
    override fun open(fix: FluidNotifyFix): Boolean = opens
  }

  private fun stateIs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

  @Test
  fun r2m_ilRiepilogoAlleOttoEMezza_restaAlleOttoEMezza() {
    val center = Center(listOf(8 * 60 + 30, 18 * 60))
    themed { FluidNotificationSettingsPage(center = center, onBack = {}) }
    advance(400)
    assertEquals("il primo orario si legge com'è salvato", 1, compose.onAllNodes(stateIs("8:30")).fetchSemanticsNodes().size)
  }

  @Test
  fun r2m_cambiareUnOrario_nonTogliIlTerzo() {
    val center = Center(listOf(8 * 60, 12 * 60, 18 * 60))
    themed { FluidNotificationSettingsPage(center = center, onBack = {}) }
    advance(400)
    compose.onNode(stateIs("12:00")).performClick()
    advance(800)
    compose.onAllNodesWithText("13:00")[0].performClick()
    advance(800)
    assertEquals(listOf(8 * 60, 13 * 60, 18 * 60), center.saved.last())
  }

  @Test
  fun r2q5_unPromemoriaNonMostrato_siLeggeCosì() {
    val center = Center(listOf(8 * 60, 18 * 60))
    val at = java.time.ZonedDateTime.now().withHour(12).withMinute(0).toInstant().toEpochMilli()
    center.deliveries.value = listOf(FluidReminderDelivery("pasta", "Scola la pasta", at, at, "Le notifiche erano spente", shown = false))
    themed { FluidUpcomingRemindersPage(center = center, onBack = {}, locale = java.util.Locale.ITALY, now = { at }) }
    advance(600)
    assertTrue(compose.onAllNodesWithText("Non mostrato", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertTrue(
      "non «in orario»: non è arrivato",
      compose.onAllNodesWithText("In orario", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
    )
    assertTrue(compose.onAllNodesWithText("Le notifiche erano spente", substring = true).fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun r2m_leImpostazioniNonSiAprono_laPaginaLoDice() {
    val center = Center(listOf(8 * 60, 18 * 60), opens = false)
    themed { FluidNotificationSettingsPage(center = center, onBack = {}) }
    advance(400)
    compose.onNodeWithTag(FluidNotifyPageTags.channel("circolari")).performClick()
    advance(600)
    assertTrue(
      "un messaggio spiega che le impostazioni non si sono aperte",
      compose.onAllNodesWithText("Non riesco ad aprire le impostazioni", substring = true).fetchSemanticsNodes().isNotEmpty(),
    )
  }
}
