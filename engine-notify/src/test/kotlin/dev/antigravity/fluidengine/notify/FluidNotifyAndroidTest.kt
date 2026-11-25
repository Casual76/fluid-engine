package dev.antigravity.fluidengine.notify

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.live.FluidLiveAction
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.notify.testing.FluidNotifyTesting
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * F8.19 · engine-notify con le notifiche e gli allarmi veri di Android (Robolectric): prima del 13 (sdk 30) e dal 13 al
 * 15 (sdk 34). Android 16 è in `FluidNotifyAndroid16Test` (vuole Java 21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FluidNotifyAndroidTest {
  private lateinit var app: Application
  private lateinit var notify: FluidNotify
  private val events = ArrayList<FluidNotifyEvent>()

  private val voti = FluidNotifyChannel(
    "voti", "Voti nuovi", "Quando la scuola pubblica un voto",
    importance = FluidNotifyImportance.Urgent, summary = { n -> "$n nuovi voti" },
  )
  private val circolari = FluidNotifyChannel("circolari", "Circolari", "Le comunicazioni della scuola")
  private val rome = ZoneId.of("Europe/Rome")

  @Before
  fun setUp() {
    FluidNotifyTesting.reset()
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    app = ApplicationProvider.getApplicationContext()
    notify = FluidNotify.install(app, FluidNotifyConfig(channels = listOf(voti, circolari), onEvent = { events += it }))
    notify.zone = { rome }
  }

  @After
  fun tearDown() = FluidNotifyTesting.reset()

  private fun manager() = app.getSystemService(NotificationManager::class.java)
  private fun shadowNm() = shadowOf(manager())
  private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
  private fun shown(tag: String): Notification? = shadowNm().getNotification(tag, idFor(tag))
  private fun idFor(tag: String): Int = when {
    tag.startsWith(FluidLiveUpdates.LiveTagPrefix) -> FluidLiveUpdates.LiveNotificationId
    tag.startsWith(FluidNotify.SummaryTagPrefix) -> FluidNotify.SummaryNotificationId
    tag.startsWith(FluidNotify.ReminderTagPrefix) -> FluidNotify.ReminderNotificationId
    tag == FluidNotify.DigestTag -> FluidNotify.SummaryNotificationId
    else -> FluidNotify.MessageNotificationId
  }
  private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
  private fun millis(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), rome).toInstant().toEpochMilli()

  private val transfer = FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", text = "3 di 10 file", progress = 0.4f)

  // --- i canali --------------------------------------------------------------------------------------------------------

  @Test
  fun iCanali_conParoleComuni_eQuelliDellEngine() {
    val channels = manager().notificationChannels.associateBy { it.id }
    assertEquals("Voti nuovi", channels["voti"]!!.name)
    assertEquals("Quando la scuola pubblica un voto", channels["voti"]!!.description)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channels["voti"]!!.importance)
    assertEquals("Attività in corso", channels[FluidNotifyChannel.LiveId]!!.name)
    assertEquals(NotificationManager.IMPORTANCE_LOW, channels[FluidNotifyChannel.LiveId]!!.importance)
    assertFalse("un'attività non mette il pallino sull'icona", channels[FluidNotifyChannel.LiveId]!!.canShowBadge())
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channels[FluidNotifyChannel.RemindersId]!!.importance)
    assertTrue(channels.containsKey(FluidNotifyChannel.DigestId))
  }

  // --- le generazioni ----------------------------------------------------------------------------------------------------

  @Test
  @Config(sdk = [30])
  fun primaDelTredici_laNotificaConLAvanzamento_senzaPermesso() {
    notify.liveUpdates.publish(transfer)
    idle()
    val n = shown("fluid.live/video")!!
    assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    assertEquals(40, n.extras.getInt(Notification.EXTRA_PROGRESS))
    assertEquals(100, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
    assertEquals("Converto il video", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals(FluidNotifyChannel.LiveId, n.channelId)
    assertFalse(n.extras.getBoolean("android.requestPromotedOngoing"))
    assertFalse(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
  }

  @Test
  fun dalTrediciAlQuindici_laStessaNotifica_colPermesso() {
    notify.liveUpdates.publish(transfer)
    idle()
    val n = shown("fluid.live/video")!!
    assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    assertEquals(40, n.extras.getInt(Notification.EXTRA_PROGRESS))
    assertFalse("niente promozione prima di Android 16", n.extras.getBoolean("android.requestPromotedOngoing"))
    assertEquals(NotificationCompat.CATEGORY_PROGRESS, n.category)
    assertNotNull("chi toglie la notifica avvisa l'engine", n.deleteIntent)
  }

  @Test
  fun permessoNegato_nienteNotifiche_eLaPaginaLoDice() {
    shadowNm().setNotificationsEnabled(false)
    val result = notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8"))
    assertEquals(FluidNotifyResult.NotificationsOff, result)
    notify.liveUpdates.publish(transfer)
    idle()
    assertEquals(0, shadowNm().allNotifications.size)
    notify.center.refresh()
    val first = notify.center.health.value.checks.first()
    assertEquals("permission", first.id)
    assertFalse(first.ok)
    assertEquals(FluidNotifyFix.AppNotifications, first.fix)
    assertFalse(notify.center.settings.value.notificationsAllowed)
    // Concesso: la stessa attività si vede alla pubblicazione dopo.
    shadowNm().setNotificationsEnabled(true)
    notify.liveUpdates.publish(transfer.copy(progress = 0.5f))
    idle()
    assertEquals(50, shown("fluid.live/video")!!.extras.getInt(Notification.EXTRA_PROGRESS))
  }

  @Test
  fun unCanaleSpento_nienteNotifica() {
    // L'utente spegne il canale nelle impostazioni di Android.
    manager().deleteNotificationChannel("circolari")
    manager().createNotificationChannel(
      android.app.NotificationChannel("circolari", "Circolari", NotificationManager.IMPORTANCE_NONE),
    )
    assertEquals(FluidNotifyResult.ChannelOff, notify.post(FluidNotifyMessage("c1", "circolari", "Circolare 42")))
  }

  // --- il raggruppamento ------------------------------------------------------------------------------------------------

  @Test
  fun treVotiInRaffica_unaSvegliaSola_eIlRiassunto() {
    notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8", thread = "voti"))
    notify.post(FluidNotifyMessage("v2", "voti", "Storia: 7", thread = "voti"))
    notify.post(FluidNotifyMessage("v3", "voti", "Inglese: 9", thread = "voti"))
    idle(3_000)
    val children = listOf("v1", "v2", "v3").map { shown("fluid.msg/$it")!! }
    assertTrue(children.all { it.group == "fluid.thread/voti" })
    assertEquals("la prima suona", NotificationCompat.GROUP_ALERT_ALL, children[0].groupAlertBehavior)
    assertEquals("le altre tacciono", NotificationCompat.GROUP_ALERT_SUMMARY, children[1].groupAlertBehavior)
    assertEquals(NotificationCompat.GROUP_ALERT_SUMMARY, children[2].groupAlertBehavior)
    val summary = shown("fluid.sum/fluid.thread/voti")!!
    assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
    assertEquals("3 nuovi voti", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals("il riassunto non suona mai", NotificationCompat.GROUP_ALERT_CHILDREN, summary.groupAlertBehavior)
    assertEquals(3, notify.badgeCount())
    // Uno tolto: «2 nuovi voti»; poi uno solo: niente riassunto.
    notify.cancel("v3")
    idle(2_000)
    assertEquals("2 nuovi voti", shown("fluid.sum/fluid.thread/voti")!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    notify.cancel("v2")
    idle(2_000)
    assertNull(shown("fluid.sum/fluid.thread/voti"))
  }

  @Test
  fun dopoLaFinestra_ilFiloSuonaDiNuovo() {
    notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8", thread = "voti"))
    idle(FluidNotifyProfile.BurstWindowMs + 1_000)
    notify.post(FluidNotifyMessage("v2", "voti", "Storia: 7", thread = "voti"))
    idle(2_000)
    assertEquals(NotificationCompat.GROUP_ALERT_ALL, shown("fluid.msg/v2")!!.groupAlertBehavior)
  }

  // --- le risposte e i tasti ------------------------------------------------------------------------------------------

  @Test
  fun laRispostaRapida_arrivaAllApp_eLaNotificaSeNeVa() {
    notify.post(
      FluidNotifyMessage(
        "lista", "voti", "Alessio ti ha condiviso una lista", "Spesa di sabato", person = "Alessio",
        reply = FluidNotifyReply(hint = "Scrivi ad Alessio"),
      ),
    )
    idle()
    val n = shown("fluid.msg/lista")!!
    val reply = n.actions.first()
    assertEquals("Rispondi", reply.title.toString())
    val input = reply.remoteInputs.single()
    assertEquals(FluidNotify.ReplyKey, input.resultKey)
    assertEquals("Scrivi ad Alessio", input.label.toString())
    assertEquals(NotificationCompat.CATEGORY_MESSAGE, n.category)
    // Android scrive il testo nell'intent e lo manda al ricevitore.
    val intent = shadowOf(reply.actionIntent).savedIntent
    RemoteInput.addResultsToIntent(
      arrayOf(RemoteInput.Builder(FluidNotify.ReplyKey).build()), intent,
      Bundle().apply { putCharSequence(FluidNotify.ReplyKey, "Grazie, la guardo") },
    )
    FluidNotifyReceiver().onReceive(app, intent)
    idle()
    val event = events.filterIsInstance<FluidNotifyEvent.Reply>().single()
    assertEquals("lista", event.messageId)
    assertEquals("Grazie, la guardo", event.text)
    assertNull(shown("fluid.msg/lista"))
  }

  @Test
  fun unTastoDellaNotifica_arrivaAllApp() {
    notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8", actions = listOf(FluidNotifyAction("Segna come visto", "visto"))))
    idle()
    val action = shown("fluid.msg/v1")!!.actions.single()
    FluidNotifyReceiver().onReceive(app, shadowOf(action.actionIntent).savedIntent)
    idle()
    val event = events.filterIsInstance<FluidNotifyEvent.Action>().single()
    assertEquals("v1", event.messageId)
    assertEquals("visto", event.key)
  }

  // --- il riepilogo ---------------------------------------------------------------------------------------------------

  @Test
  fun ilRiepilogo_speltoDiSerie_poiRaccoglie_maLUrgenteArrivaSubito() {
    assertFalse(notify.center.settings.value.digest.enabled)
    assertEquals(FluidNotifyResult.Posted, notify.post(FluidNotifyMessage("c0", "circolari", "Circolare 41")))
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    assertEquals(FluidNotifyResult.InDigest, notify.post(FluidNotifyMessage("c1", "circolari", "Circolare 42")))
    assertEquals(FluidNotifyResult.InDigest, notify.post(FluidNotifyMessage("c2", "circolari", "Circolare 43")))
    assertEquals(
      "urgente per sé: subito", FluidNotifyResult.Posted,
      notify.post(FluidNotifyMessage("c3", "circolari", "Scuola chiusa domani", urgent = true)),
    )
    assertEquals("un canale urgente: subito", FluidNotifyResult.Posted, notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8")))
    idle(2_000)
    assertNull(shown("fluid.msg/c1"))
    assertEquals(2, notify.center.settings.value.digest.waiting)
    val alarm = alarms().single { it.operation != null && shadowOf(it.operation).savedIntent.action == FluidNotifyReceiver.ActionDigest }
    assertEquals(FluidNotifyProfile.InexactWindowMs, alarm.windowLengthMs)
    // Suona il riepilogo.
    FluidNotifyReceiver().onReceive(app, shadowOf(alarm.operation).savedIntent)
    idle(3_000)
    assertNotNull(shown("fluid.msg/c1"))
    assertNotNull(shown("fluid.msg/c2"))
    val summary = shown(FluidNotify.DigestTag)!!
    assertEquals("Riepilogo · 2 notifiche", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals(0, notify.center.settings.value.digest.waiting)
  }

  @Test
  fun spegnereIlRiepilogo_consegnaSubitoCiòCheAspettava() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    notify.post(FluidNotifyMessage("c1", "circolari", "Circolare 42"))
    notify.center.setDigest(false, listOf(8 * 60, 18 * 60))
    idle(3_000)
    assertNotNull(shown("fluid.msg/c1"))
  }

  // --- il ritmo -------------------------------------------------------------------------------------------------------

  @Test
  fun centoAggiornamentiInUnSecondo_dueNotifiche_eLUltimoValore() {
    val before = FluidNotifyTesting.postCount(notify)
    for (i in 1..100) {
      notify.liveUpdates.publish(transfer.copy(progress = i / 100f))
      idle(10)
    }
    idle(2_000)
    val posts = FluidNotifyTesting.postCount(notify) - before
    assertTrue("pubblicazioni: $posts", posts <= 3)
    assertEquals(100, shown("fluid.live/video")!!.extras.getInt(Notification.EXTRA_PROGRESS))
  }

  @Test
  fun dieciSecondiDiAvanzamento_alPiuUnoAlSecondo() {
    val before = FluidNotifyTesting.postCount(notify)
    for (i in 1..200) {
      notify.liveUpdates.publish(transfer.copy(progress = i / 200f))
      idle(50)
    }
    idle(2_000)
    val posts = FluidNotifyTesting.postCount(notify) - before
    assertTrue("pubblicazioni: $posts", posts in 9..12)
    assertEquals(100, shown("fluid.live/video")!!.extras.getInt(Notification.EXTRA_PROGRESS))
  }

  @Test
  fun unCambioDiStato_nonAspettaIlSecondo() {
    notify.liveUpdates.publish(transfer)
    idle(100)
    notify.liveUpdates.publish(transfer.copy(text = "4 di 10 file"))
    idle(100)
    assertEquals("4 di 10 file", shown("fluid.live/video")!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  // --- dentro e fuori (lo specchio della pillola) ---------------------------------------------------------------------

  @Test
  fun loSpecchio_soloFuoriDallApp_eAlRientroTornaPillola() {
    val mirror = notify.liveUpdates.whenOutside
    FluidNotifyTesting.setOutside(notify, false)
    mirror.publish(transfer)
    idle()
    assertNull("dentro c'è la pillola", shown("fluid.live/video"))
    FluidNotifyTesting.setOutside(notify, true)
    idle()
    assertNotNull("fuori la notifica", shown("fluid.live/video"))
    FluidNotifyTesting.setOutside(notify, false)
    idle()
    assertNull("al rientro torna pillola", shown("fluid.live/video"))
    // Finita fuori: resta l'esito.
    FluidNotifyTesting.setOutside(notify, true)
    idle(2_000)
    mirror.finish("video", FluidLiveOutcome.succeeded("Video pronto"))
    idle(2_000)
    val done = shown("fluid.live/video")!!
    assertEquals(0, done.flags and Notification.FLAG_ONGOING_EVENT)
    assertEquals("Video pronto", done.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    assertEquals(FluidNotifyProfile.EndedLingerMs, done.timeoutAfter)
    FluidNotifyTesting.setOutside(notify, false)
    idle()
    assertNotNull("l'esito resta, come una Live Activity finita", shown("fluid.live/video"))
  }

  @Test
  fun finitaDentro_nienteNotifica() {
    val mirror = notify.liveUpdates.whenOutside
    FluidNotifyTesting.setOutside(notify, false)
    mirror.publish(transfer)
    mirror.finish("video", FluidLiveOutcome.succeeded())
    FluidNotifyTesting.setOutside(notify, true)
    idle(2_000)
    assertNull(shown("fluid.live/video"))
  }

  @Test
  fun tolta_dallUtente_nonTorna_salvoUnErrore() {
    notify.liveUpdates.publish(transfer)
    idle()
    FluidNotifyReceiver().onReceive(app, shadowOf(shown("fluid.live/video")!!.deleteIntent).savedIntent)
    manager().cancel("fluid.live/video", FluidLiveUpdates.LiveNotificationId)
    notify.liveUpdates.publish(transfer.copy(progress = 0.9f))
    idle(2_000)
    assertNull("non si ripropone", shown("fluid.live/video"))
    assertTrue(events.any { it is FluidNotifyEvent.LiveDismissed })
    notify.liveUpdates.finish("video", FluidLiveOutcome.failed("Rete assente"))
    idle(2_000)
    assertEquals("Rete assente", shown("fluid.live/video")!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  @Test
  fun smettiDiSeguire_nonTorna_nemmenoSeLAppContinuaAPubblicare() {
    val trip = FluidLiveUpdate(
      "mamma", FluidLiveKind.Navigation, "Alessio è sul 12", text = "Arriva alle 18:42",
      origin = dev.antigravity.fluidengine.foundation.live.FluidLiveOrigin.Followed,
    )
    notify.liveUpdates.publish(trip)
    idle()
    val unfollow = shown("fluid.live/mamma")!!.actions.last()
    assertEquals("Smetti di seguire", unfollow.title.toString())
    FluidNotifyReceiver().onReceive(app, shadowOf(unfollow.actionIntent).savedIntent)
    idle()
    assertNull(shown("fluid.live/mamma"))
    assertTrue(events.any { it is FluidNotifyEvent.Unfollowed })
    // L'app non ha ancora fermato il viaggio: le fermate dopo non riportano la notifica, nemmeno l'esito.
    notify.liveUpdates.publish(trip.copy(text = "Arriva alle 18:44"))
    idle(2_000)
    assertNull(shown("fluid.live/mamma"))
    notify.liveUpdates.finish("mamma", FluidLiveOutcome.failed("Viaggio interrotto"))
    idle(2_000)
    assertNull(shown("fluid.live/mamma"))
    // Un viaggio nuovo, dopo la fine, si vede di nuovo.
    notify.liveUpdates.publish(trip)
    idle()
    assertNotNull(shown("fluid.live/mamma"))
  }

  @Test
  fun unTastoDellAttivita_chiamaLaStessaFunzioneDellaPillola() {
    var paused = 0
    notify.liveUpdates.publish(transfer.copy(actions = listOf(FluidLiveAction("Pausa", { paused++ }))))
    idle()
    val action = shown("fluid.live/video")!!.actions.single()
    FluidNotifyReceiver().onReceive(app, shadowOf(action.actionIntent).savedIntent)
    assertEquals(1, paused)
    // Un tasto di un processo morto: la notifica era vecchia, se ne va.
    val stale = Intent(shadowOf(action.actionIntent).savedIntent).putExtra(FluidNotifyReceiver.ExtraSession, notify.session + 1)
    FluidNotifyReceiver().onReceive(app, stale)
    assertEquals(1, paused)
    assertNull(shown("fluid.live/video"))
  }

  @Test
  fun ilServizioInPrimoPiano_aggiornaLaSuaNotifica_eLEsitoStaAParte() {
    val n = notify.liveUpdates.foreground(transfer, notificationId = 100)
    manager().notify(null, 100, n) // startForeground
    notify.liveUpdates.publish(transfer.copy(text = "5 di 10 file"))
    idle()
    assertEquals("5 di 10 file", shadowNm().getNotification(null, 100).extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    notify.liveUpdates.finish("video", FluidLiveOutcome.succeeded())
    idle(2_000)
    assertEquals("Fatto", shown("fluid.live/video")!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  // --- i promemoria ---------------------------------------------------------------------------------------------------

  private fun reminderAlarm(id: String) = alarms().single {
    shadowOf(it.operation).savedIntent.getStringExtra(FluidNotifyReceiver.ExtraId) == id
  }

  @Test
  fun unPromemoriaFraUnMinuto_esatto_arriva_eSiRegistra() {
    val now = millis("2026-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    val alarm = reminderAlarm("pasta")
    assertEquals(now + 60_000, alarm.triggerAtMs)
    assertTrue(alarm.isAllowWhileIdle)
    assertEquals(0L, alarm.windowLengthMs)
    assertTrue(notify.center.reminders.value.single().exact)
    assertEquals(
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
      app.packageManager.getComponentEnabledSetting(ComponentName(app, FluidNotifyRestoreReceiver::class.java)),
    )
    notify.wallNow = { now + 61_000 }
    FluidNotifyReceiver().onReceive(app, shadowOf(alarm.operation).savedIntent)
    idle()
    val n = shown("fluid.rem/pasta")!!
    assertEquals("Scola la pasta", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals(FluidNotifyChannel.RemindersId, n.channelId)
    val delivery = notify.center.deliveries.value.single()
    assertEquals(now + 60_000, delivery.scheduledMillis)
    assertEquals(now + 61_000, delivery.deliveredMillis)
    assertNull("un secondo non è un ritardo", delivery.reason)
    assertTrue("finito, esce dal programma", notify.center.reminders.value.isEmpty())
    assertTrue(events.any { it is FluidNotifyEvent.ReminderDelivered })
    assertEquals(
      "l'ultimo promemoria spegne il ricevitore del ripristino",
      PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      app.packageManager.getComponentEnabledSetting(ComponentName(app, FluidNotifyRestoreReceiver::class.java)),
    )
  }

  @Test
  fun senzaAllarmeEsatto_unaFinestraDiDieciMinuti_eLaPaginaLoDice() {
    ShadowAlarmManager.setCanScheduleExactAlarms(false)
    val now = millis("2026-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    assertEquals(FluidNotifyProfile.InexactWindowMs, reminderAlarm("pasta").windowLengthMs)
    assertFalse(notify.center.reminders.value.single().exact)
    val exact = notify.center.health.value.checks.first { it.id == "exact" }
    assertFalse(exact.ok)
    assertEquals(FluidNotifyFix.ExactAlarms, exact.fix)
    assertTrue(notify.center.open(FluidNotifyFix.ExactAlarms))
    assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, shadowOf(app).nextStartedActivity.action)
  }

  @Test
  fun dopoUnRiavvio_ilPromemoriaPerso_arrivaSubito_segnatoInRitardo() {
    // Una data lontana: la FluidNotify nuova si avvia con l'orologio vero (wallNow si cambia solo dopo install), e
    // con una data già passata consegnerebbe il promemoria all'avvio come «ritardato dal sistema», prima del riavvio.
    // Con 2026-11-25T10:00 la prova è caduta il 25/11/2026 dopo le 11 (unione di F10.8).
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("verifica", FluidReminderTime.at(now + 3_600_000), FluidReminderContent("Verifica di storia"))
    notify.reminders.schedule("dopo", FluidReminderTime.at(now + 10 * 3_600_000), FluidReminderContent("Più tardi"))
    // Lo spegnimento cancella gli allarmi; il telefono riparte due ore dopo, con un processo nuovo.
    val am = app.getSystemService(AlarmManager::class.java)
    am.cancel(notify.reminders.pendingIntent("verifica"))
    am.cancel(notify.reminders.pendingIntent("dopo"))
    assertTrue(alarms().isEmpty())
    FluidNotifyTesting.reset()
    notify = FluidNotify.install(app, FluidNotifyConfig(channels = listOf(voti, circolari), onEvent = { events += it }))
    notify.zone = { rome }
    notify.wallNow = { now + 2 * 3_600_000 }
    FluidNotifyRestoreReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
    idle()
    assertNotNull(shown("fluid.rem/verifica"))
    val delivery = notify.center.deliveries.value.first()
    assertEquals("verifica", delivery.id)
    assertEquals("Il telefono era spento o appena riavviato", delivery.reason)
    assertEquals(3_600_000L, delivery.delayMillis)
    assertEquals("l'altro torna in programma", now + 10 * 3_600_000, reminderAlarm("dopo").triggerAtMs)
  }

  @Test
  fun unCambioDiFuso_ilPromemoriaAlleOttoResta_alleOtto() {
    notify.wallNow = { millis("2026-11-25T10:00") }
    notify.reminders.schedule("sveglia", FluidReminderTime.local(LocalDateTime.parse("2026-11-26T08:00")), FluidReminderContent("Buongiorno"))
    assertEquals(millis("2026-11-26T08:00"), reminderAlarm("sveglia").triggerAtMs)
    val london = ZoneId.of("Europe/London")
    notify.zone = { london }
    FluidNotifyRestoreReceiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))
    assertEquals(
      ZonedDateTime.of(LocalDateTime.parse("2026-11-26T08:00"), london).toInstant().toEpochMilli(),
      reminderAlarm("sveglia").triggerAtMs,
    )
  }

  @Test
  fun unPromemoriaOgniGiorno_dopoLaConsegna_armaIlGiornoDopo() {
    val now = millis("2026-11-25T07:00")
    notify.wallNow = { now }
    notify.reminders.schedule(
      "medicina", FluidReminderTime.local(LocalDateTime.parse("2026-11-25T08:00")), FluidReminderContent("Medicina"),
      repeat = dev.antigravity.fluidengine.foundation.FluidRecurrenceRule(dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency.Daily),
    )
    assertNotNull(notify.center.reminders.value.single().repeats)
    notify.wallNow = { millis("2026-11-25T08:00") }
    FluidNotifyReceiver().onReceive(app, shadowOf(reminderAlarm("medicina").operation).savedIntent)
    assertEquals(millis("2026-11-26T08:00"), reminderAlarm("medicina").triggerAtMs)
    assertEquals(millis("2026-11-26T08:00"), notify.center.reminders.value.single().atMillis)
  }

  @Test
  fun unaSveglia_usaLAllarmeDelSistema() {
    val now = millis("2026-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("sveglia", FluidReminderTime.at(now + 60_000), FluidReminderContent("Sveglia"), alarm = true)
    assertNotNull(reminderAlarm("sveglia").alarmClockInfo)
  }

  @Test
  fun toltoDalProgramma() {
    val now = millis("2026-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    notify.center.cancelReminder("pasta")
    assertTrue(alarms().isEmpty())
    assertTrue(notify.center.reminders.value.isEmpty())
  }

  // --- «Le notifiche non arrivano?» -----------------------------------------------------------------------------------

  @Test
  fun laPagina_portaNelPuntoGiustoDelleImpostazioni() {
    assertTrue(notify.center.open(FluidNotifyFix.channel("voti")))
    val channel = shadowOf(app).nextStartedActivity
    assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel.action)
    assertEquals("voti", channel.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    assertTrue(notify.center.open(FluidNotifyFix.Battery))
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(app).nextStartedActivity.action)
    assertTrue(notify.center.open(FluidNotifyFix.AppNotifications))
    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(app).nextStartedActivity.action)
  }

  @Test
  fun lAppSospesa_siVedeDalSecchio() {
    shadowOf(app.getSystemService(android.app.usage.UsageStatsManager::class.java))
      .setCurrentAppStandbyBucket(android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED)
    notify.center.refresh()
    val health = notify.center.health.value
    assertTrue(health.suspended)
    assertEquals("suspended", health.checks.first().id)
    assertEquals(FluidNotifyFix.Battery, health.checks.first().fix)
  }
}
