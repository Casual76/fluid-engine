package dev.antigravity.fluidengine.sample.novita

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCenter
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyChannelInfo
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCheck
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyHealth
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyLiveState
import dev.antigravity.fluidengine.foundation.notify.FluidNotifySettings
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import dev.antigravity.fluidengine.foundation.notify.FluidReminderInfo
import dev.antigravity.fluidengine.notify.FluidNotify
import dev.antigravity.fluidengine.notify.FluidNotifyConfig
import dev.antigravity.fluidengine.notify.testing.FluidNotifyTesting
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHealthPage
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationSettingsPage
import dev.antigravity.fluidengine.ui.fluid.FluidNotifyPageTags
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidUpcomingRemindersPage
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutcome
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutside
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F8.19 · Il passaggio pillola → notifica → pillola con engine-notify vero e la pillola vera (formato telefono), le pagine
 * delle notifiche con TalkBack (nomi, stati, azioni, bersagli da 44 dp) e con «Riduci movimento», e le scene della
 * galleria. Android 16 vero è nelle prove di engine-notify (sdk 36 vuole Java 21; qui il JDK è il 17).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p19NotifyUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var app: Application

  @Before fun start() {
    FluidNotifyTesting.reset()
    app = ApplicationProvider.getApplicationContext()
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidNotifyTesting.reset()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun shown(tag: String): Notification? =
    shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(tag, 0x5F1D)

  private fun themed(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
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

  // --- pillola → notifica → pillola -------------------------------------------------------------------------------------

  @Test
  fun pillola_notifica_pillola() {
    val notify = FluidNotify.install(app, FluidNotifyConfig())
    FluidNotifyTesting.setOutside(notify, false)
    val activities = FluidActivityHostState()
    val progress = mutableFloatStateOf(0.2f)
    themed {
      FluidActivityHost(activities) {
        FluidActivityOutside(activities, notify.liveUpdates.whenOutside)
        FluidScreen(title = "Converter", onBack = {}, toolbar = { title(FluidToolbarTitleMode.Inline) }) {
          item { androidx.compose.material3.Text("Contenuto") }
        }
      }
    }
    advance(100)
    activities.publish(FluidActivity.transfer("video", progress = { progress.floatValue }, title = "Converto il video"))
    advance(500)
    assertEquals("dentro c'è la pillola", "video", activities.current?.id)
    assertNull("dentro, niente notifica", shown("fluid.live/video"))

    // Fuori: la stessa attività diventa la notifica.
    FluidNotifyTesting.setOutside(notify, true)
    advance(200)
    val outside = shown("fluid.live/video")!!
    assertEquals("Converto il video", outside.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals(20, outside.extras.getInt(Notification.EXTRA_PROGRESS))

    // L'avanzamento, che fuori nessuno disegna, lo rilegge lo specchio: al più una volta al secondo.
    progress.floatValue = 0.6f
    advance(2_500)
    assertEquals(60, shown("fluid.live/video")!!.extras.getInt(Notification.EXTRA_PROGRESS))

    // Rientro: la notifica se ne va, la pillola resta.
    FluidNotifyTesting.setOutside(notify, false)
    advance(200)
    assertNull(shown("fluid.live/video"))
    assertEquals("video", activities.current?.id)

    // Finita fuori: resta l'esito, come una Live Activity finita.
    FluidNotifyTesting.setOutside(notify, true)
    advance(1_500)
    activities.finish("video", FluidActivityOutcome.succeeded("Video pronto"))
    advance(1_500)
    val done = shown("fluid.live/video")!!
    assertEquals("Video pronto", done.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    assertEquals(0, done.flags and Notification.FLAG_ONGOING_EVENT)
  }

  @Test
  fun annullataNellaPillola_laNotificaSeNeVa() {
    val notify = FluidNotify.install(app, FluidNotifyConfig())
    val activities = FluidActivityHostState()
    themed { FluidActivityHost(activities) { FluidActivityOutside(activities, notify.liveUpdates.whenOutside) } }
    advance(50)
    FluidNotifyTesting.setOutside(notify, true)
    activities.publish(FluidActivity.sync(progress = { 0.3f }))
    advance(500)
    assertNotNull(shown("fluid.live/sync"))
    activities.remove("sync")
    advance(500)
    assertNull(shown("fluid.live/sync"))
  }

  // --- le pagine --------------------------------------------------------------------------------------------------------

  private class FakeCenter : FluidNotifyCenter {
    override val settings = MutableStateFlow(
      FluidNotifySettings(
        notificationsAllowed = true,
        channels = listOf(
          FluidNotifyChannelInfo("voti", "Voti nuovi", "Quando la scuola pubblica un voto", urgent = true),
          FluidNotifyChannelInfo("circolari", "Circolari", "Le comunicazioni della scuola"),
        ),
        live = FluidNotifyLiveState.Allowed,
      ),
    )
    override val reminders = MutableStateFlow(
      listOf(
        FluidReminderInfo("te", "Il tè è pronto", "Togli la bustina", atMillis = noon + 3_600_000, exact = true),
        FluidReminderInfo("zaino", "Controlla lo zaino", null, atMillis = noon + 86_400_000, exact = false, repeats = "Ogni giorno"),
      ),
    )
    override val deliveries = MutableStateFlow(
      listOf(FluidReminderDelivery("verifica", "Verifica di storia", noon - 7_200_000, noon - 7_200_000 + 600_000, "Il telefono era spento")),
    )
    override val health = MutableStateFlow(
      FluidNotifyHealth(
        checks = listOf(
          FluidNotifyCheck("permission", "Notifiche dell'app", "Accese", ok = true),
          FluidNotifyCheck("suspended", "App in sospensione", "L'app può lavorare in sottofondo", ok = true),
        ),
      ),
    )
    val opened = ArrayList<FluidNotifyFix>()
    var refreshes = 0

    override fun refresh() {
      refreshes++
    }

    override fun setChannelInDigest(channelId: String, inDigest: Boolean) {
      settings.value = settings.value.copy(
        channels = settings.value.channels.map { if (it.id == channelId) it.copy(inDigest = inDigest) else it },
      )
    }

    override fun setDigest(enabled: Boolean, times: List<Int>) {
      settings.value = settings.value.copy(digest = settings.value.digest.copy(enabled = enabled, times = times))
    }

    override fun cancelReminder(id: String) {
      reminders.value = reminders.value.filterNot { it.id == id }
    }

    override fun open(fix: FluidNotifyFix): Boolean = opened.add(fix)

    companion object {
      val noon: Long = ZonedDateTime.now(ZoneId.systemDefault()).withHour(12).withMinute(0).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()
    }
  }

  private fun minTargetPx() = 44.dp.value * compose.activity.resources.displayMetrics.density

  @Test
  fun leImpostazioni_conTalkBack_eIlRiepilogoSpentoDiSerie() {
    val center = FakeCenter()
    themed { FluidNotificationSettingsPage(center = center, onBack = {}, onOpenUpcoming = {}, onOpenHealth = {}) }
    advance(300)
    assertTrue("la pagina rilegge lo stato quando si apre", center.refreshes >= 1)
    compose.onNodeWithText("Voti nuovi", substring = true).assertExists()
    compose.onNodeWithText("Sempre subito", substring = true).assertExists()
    val toggle = compose.onNodeWithTag(FluidNotifyPageTags.DigestToggle).fetchSemanticsNode()
    assertEquals(
      "il riepilogo è un interruttore, spento di serie",
      androidx.compose.ui.state.ToggleableState.Off, toggle.config.getOrNull(SemanticsProperties.ToggleableState),
    )
    assertTrue(toggle.boundsInRoot.height >= minTargetPx())
    // Acceso: compaiono gli orari e la scelta dei canali; il canale urgente non c'è.
    compose.onNodeWithTag(FluidNotifyPageTags.DigestToggle).performClick()
    advance(500)
    assertTrue(center.settings.value.digest.enabled)
    compose.onNodeWithTag(FluidNotifyPageTags.digestChannel("circolari")).assertExists()
    compose.onNodeWithTag(FluidNotifyPageTags.digestChannel("voti")).assertDoesNotExist()
    compose.onNodeWithTag(FluidNotifyPageTags.digestChannel("circolari")).performClick()
    advance(300)
    assertTrue(center.settings.value.channels.first { it.id == "circolari" }.inDigest)
    // Il canale porta nella sua pagina di Android.
    compose.onNodeWithTag(FluidNotifyPageTags.channel("voti")).performClick()
    advance(200)
    assertEquals(FluidNotifyFix.channel("voti"), center.opened.last())
  }

  @Test
  fun cosaSuccedera_conTalkBack_eTogli() {
    val center = FakeCenter()
    themed { FluidUpcomingRemindersPage(center = center, onBack = {}, zone = ZoneId.systemDefault(), locale = Locale.ITALY, now = { FakeCenter.noon }) }
    advance(300)
    assertTrue(compose.onAllNodesWithText("oggi alle", substring = true).fetchSemanticsNodes().isNotEmpty())
    compose.onNodeWithText("al minuto", substring = true).assertExists()
    compose.onNodeWithText("entro 10 minuti", substring = true).assertExists()
    compose.onNodeWithText("10 minuti di ritardo", substring = true).assertExists()
    compose.onNodeWithText("Il telefono era spento", substring = true).assertExists()
    val row = compose.onNodeWithTag(FluidNotifyPageTags.reminder("te")).fetchSemanticsNode()
    assertTrue(row.boundsInRoot.height >= minTargetPx())
    val remove = row.config.getOrNull(SemanticsActions.CustomActions)!!.first { it.label == "Togli" }
    compose.runOnIdle { remove.action() }
    advance(300)
    assertEquals(listOf("zaino"), center.reminders.value.map { it.id })
    compose.onNodeWithTag(FluidNotifyPageTags.reminder("te")).assertDoesNotExist()
  }

  @Test
  fun nonArrivano_laCorrezioneEntraSfocata() = health(reduced = false)

  @Test
  fun nonArrivano_conRiduciMovimento() = health(reduced = true)

  private fun health(reduced: Boolean) {
    run {
      val center = FakeCenter()
      themed(reduced) { FluidNotificationHealthPage(center = center, onBack = {}) }
      advance(300)
      compose.onNodeWithTag(FluidNotifyPageTags.check("suspended")).assertTextContains("A posto", substring = true)
      // Torna dalle impostazioni: l'app si è addormentata. Lo cambia l'app, non il dito: il segno cambia sfocato.
      center.health.value = FluidNotifyHealth(
        checks = listOf(
          FluidNotifyCheck(
            "suspended", "App in sospensione", "One UI ha messo l'app a dormire", ok = false,
            fix = FluidNotifyFix.Battery, fixLabel = "Apri Batteria",
          ),
          FluidNotifyCheck("permission", "Notifiche dell'app", "Accese", ok = true),
        ),
        suspended = true,
      )
      advance(48)
      val during = compose.onAllNodesWithText("Apri Batteria", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
      val oldDuring = compose.onAllNodesWithText("A posto", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
      // A metà del cambio: col movimento il segno vecchio può esserci ancora (i due strati sfocati); con «Riduci
      // movimento» resta solo quello della riga del permesso, che non cambia.
      println("F8p19 nonArrivano reduced=$reduced: a 48 ms «Apri Batteria» $during, «A posto» $oldDuring")
      if (reduced) {
        assertEquals("con «Riduci movimento» il segno cambia di colpo", 1, oldDuring)
      } else {
        assertEquals("col movimento i due segni sono insieme, sfocati: il vecchio esce, il nuovo entra", 2, oldDuring)
      }
      advance(1_000)
      assertEquals(1, compose.onAllNodesWithText("A posto", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
      compose.onNodeWithText("Apri Batteria", substring = true, useUnmergedTree = true).assertExists()
      compose.onNodeWithTag(FluidNotifyPageTags.check("suspended")).performClick()
      advance(200)
      assertEquals(FluidNotifyFix.Battery, center.opened.last())
      assertTrue("il segno nuovo c'è già durante il cambio ($reduced)", during >= 1)
    }
  }

  // --- le scene ---------------------------------------------------------------------------------------------------------

  @Test
  fun treVotiInRaffica_dallaScena() {
    themed { NewElementsPageAt(onBack = {}, initial = "notifiche-raccolte") }
    advance(500)
    compose.onNodeWithTag(F8p19Tags.Grades).performClick()
    advance(3_000)
    val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
    val summary = nm.getNotification("fluid.sum/fluid.thread/voti", 0x5F1F)
    assertNotNull(summary)
    assertEquals("3 nuovi voti", summary!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
  }

  @Test
  fun leSceneSiAprono() {
    val current = androidx.compose.runtime.mutableStateOf(F8p19Scenes.first().id)
    themed { androidx.compose.runtime.key(current.value) { NewElementsPageAt(onBack = {}, initial = current.value) } }
    for (scene in F8p19Scenes) {
      current.value = scene.id
      advance(400)
      compose.onNodeWithText(
        when (scene.id) {
          "attivita-fuori" -> "Converti il video"
          "viaggio-live" -> "Avvia il viaggio"
          "notifiche-raccolte" -> "Arrivano tre voti"
          "promemoria-minuto" -> "Ricordamelo fra un minuto"
          "impostazioni-notifiche" -> "Riepilogo programmato"
          else -> "App in sospensione"
        },
        substring = true,
      ).assertExists()
    }
    assertNotNull(FluidNotify.get(app).channel(F8p19Channels.Voti.id))
  }
}
