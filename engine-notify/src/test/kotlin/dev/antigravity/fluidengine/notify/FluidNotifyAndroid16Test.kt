package dev.antigravity.fluidengine.notify

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOrigin
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLivePoint
import dev.antigravity.fluidengine.foundation.live.FluidLiveStage
import dev.antigravity.fluidengine.foundation.live.FluidLiveTime
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.notify.testing.FluidNotifyTesting
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F8.19 · Android 16 vero (Robolectric con sdk 36, che vuole Java 21: le prove di engine-notify girano sul JDK di Android
 * Studio, vedi `engine-notify/build.gradle`). La prova che conta è `Notification.hasPromotableCharacteristics()`: il
 * codice di Android 16 dice se la notifica ha tutto ciò che serve per diventare un Live Update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FluidNotifyAndroid16Test {
  private lateinit var app: Application
  private lateinit var notify: FluidNotify

  @Before
  fun setUp() {
    FluidNotifyTesting.reset()
    app = ApplicationProvider.getApplicationContext()
    notify = FluidNotify.install(app, FluidNotifyConfig())
  }

  @After
  fun tearDown() = FluidNotifyTesting.reset()

  private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
  private fun shown(id: String): Notification =
    shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification("fluid.live/$id", FluidLiveUpdates.LiveNotificationId)

  @Test
  fun unTrasferimento_eUnLiveUpdatePromuovibile() {
    notify.liveUpdates.publish(FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", text = "3 di 10 file", progress = 0.4f))
    idle()
    val n = shown("video")
    assertTrue("la promozione è chiesta", n.extras.getBoolean("android.requestPromotedOngoing"))
    assertTrue("Android 16 la trova promuovibile", n.hasPromotableCharacteristics())
    // Questa immagine è la prima uscita di Android 16 (36.0): lì serve il colore chiesto, e l'engine lo ha aggiunto da solo.
    assertTrue(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
    assertEquals(FluidLiveUpdates.EarlyPromotionColor, n.color)
    assertEquals("40%", n.shortCriticalText)
    val style = Notification.Builder.recoverBuilder(app, n).style
    assertTrue("la barra di Android 16: ${style?.javaClass}", style is Notification.ProgressStyle)
    assertEquals(40, (style as Notification.ProgressStyle).progress)
  }

  @Test
  fun unViaggio_leTappeEleFermate() {
    notify.liveUpdates.publish(
      FluidLiveUpdate(
        "viaggio", FluidLiveKind.Navigation, "Bus 12 verso casa", text = "Scendi a Piazza Dante · arrivo 18:42", progress = 0.5f,
        stages = listOf(FluidLiveStage(120, 0xFF8E8E93.toInt()), FluidLiveStage(600, 0xFF0A84FF.toInt()), FluidLiveStage(80, 0xFF8E8E93.toInt())),
        points = listOf(FluidLivePoint(120), FluidLivePoint(720)),
        chip = "18:42",
      ),
    )
    idle()
    val n = shown("viaggio")
    assertTrue(n.hasPromotableCharacteristics())
    assertEquals(Notification.CATEGORY_NAVIGATION, n.category)
    assertEquals("18:42", n.shortCriticalText)
    val style = Notification.Builder.recoverBuilder(app, n).style as Notification.ProgressStyle
    assertEquals(listOf(120, 600, 80), style.progressSegments.map { it.length })
    assertEquals(listOf(120, 720), style.progressPoints.map { it.position })
    assertEquals(400, style.progress)
    assertEquals(800, style.progressMax)
  }

  @Test
  fun unTimer_ilCronometroNelGettone() {
    val now = android.os.SystemClock.elapsedRealtime()
    notify.liveUpdates.publish(FluidLiveUpdate("timer", FluidLiveKind.Timer, "Pasta", time = FluidLiveTime.until(now + 300_000)))
    idle()
    val n = shown("timer")
    assertTrue(n.hasPromotableCharacteristics())
    assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    assertNull(n.shortCriticalText)
    assertEquals("stopwatch", n.category)
  }

  @Test
  fun unaSincronizzazione_nonChiedeLaPromozione() {
    notify.liveUpdates.publish(FluidLiveUpdate("sync", FluidLiveKind.Sync, "Sincronizzo"))
    idle()
    val n = shown("sync")
    assertFalse(n.extras.getBoolean("android.requestPromotedOngoing"))
    assertFalse("niente colore pieno se non si chiede la promozione", n.extras.getBoolean(Notification.EXTRA_COLORIZED))
  }

  @Test
  fun avviataDaAltri_nienteLiveUpdate_seguitaPerScelta_conIlTastoPerSmettere() {
    notify.liveUpdates.publish(
      FluidLiveUpdate("mamma", FluidLiveKind.Navigation, "Alessio è sul 12", text = "Arriva alle 18:42", origin = FluidLiveOrigin.Followed),
    )
    notify.liveUpdates.publish(FluidLiveUpdate("altri", FluidLiveKind.Navigation, "Un viaggio", origin = FluidLiveOrigin.Others))
    idle()
    val followed = shown("mamma")
    assertTrue(followed.extras.getBoolean("android.requestPromotedOngoing"))
    assertEquals("Smetti di seguire", followed.actions.last().title.toString())
    assertFalse(shown("altri").extras.getBoolean("android.requestPromotedOngoing"))
  }

  @Test
  fun allaFine_nonPiuPromossa() {
    notify.liveUpdates.publish(FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", progress = 0.9f))
    idle()
    notify.liveUpdates.finish("video", FluidLiveOutcome.succeeded("Video pronto"))
    idle(2_000)
    val n = shown("video")
    assertFalse(n.extras.getBoolean("android.requestPromotedOngoing"))
    assertFalse("un esito non è più un'attività in corso", n.hasPromotableCharacteristics())
    assertEquals("Video pronto", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  @Test
  fun laPagina_ilLiveUpdateCe() {
    notify.center.refresh()
    assertTrue(notify.center.settings.value.live.supported)
  }
}
