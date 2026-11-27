package dev.antigravity.fluidengine.notify

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.notify.testing.FluidNotifyTesting
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlarmManager

/**
 * R2 · I difetti di F8.19 trovati dalla review R2 (`muse-f8-prime.md`, «Maggiori — notifiche, promemoria, Live Update»,
 * i minori e i sospetti della stessa famiglia), uno per prova, con le notifiche e gli allarmi veri di Android
 * (Robolectric, sdk 34). Ogni prova è caduta sul codice di prima (`bce39db`) e passa con la correzione.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FluidNotifyR2Test {
  private lateinit var app: Application
  private lateinit var notify: FluidNotify
  private val events = ArrayList<FluidNotifyEvent>()

  private val voti = FluidNotifyChannel(
    "voti", "Voti nuovi", "Quando la scuola pubblica un voto",
    importance = FluidNotifyImportance.Urgent, summary = { n -> "$n nuovi voti" },
  )
  private val circolari = FluidNotifyChannel("circolari", "Circolari", "Le comunicazioni della scuola")
  private val rome = ZoneId.of("Europe/Rome")

  private fun config() = FluidNotifyConfig(channels = listOf(voti, circolari), onEvent = { events += it })

  @Before
  fun setUp() {
    FluidNotifyTesting.reset()
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    app = ApplicationProvider.getApplicationContext()
    notify = FluidNotify.install(app, config())
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
  private fun reminderAlarm(id: String) = alarms().single {
    shadowOf(it.operation).savedIntent.getStringExtra(FluidNotifyReceiver.ExtraId) == id
  }
  private fun fireReminder(id: String) = FluidNotifyReceiver().onReceive(app, shadowOf(reminderAlarm(id).operation).savedIntent)
  private fun fireDigest() {
    val alarm = alarms().single { it.operation != null && shadowOf(it.operation).savedIntent.action == FluidNotifyReceiver.ActionDigest }
    FluidNotifyReceiver().onReceive(app, shadowOf(alarm.operation).savedIntent)
  }
  private fun text(n: Notification, key: String): String = n.extras.getCharSequence(key)?.toString().orEmpty()
  private fun lines(n: Notification): List<String> =
    n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()

  /** Le scritture della memoria vanno su disco in sottofondo (dopo R2): la prova aspetta che finiscano. */
  private fun flushStore() {
    runCatching { notify.store.javaClass.getMethod("flush").invoke(notify.store) }
    idle()
  }

  /** Un processo nuovo: la FluidNotify di prima muore (la porta del ritmo perde ciò che aspettava), poi l'app reinstalla. */
  private fun newProcess() {
    FluidNotifyTesting.reset()
    notify = FluidNotify.install(app, config())
    notify.zone = { rome }
  }

  private fun channelOff(id: String) {
    manager().deleteNotificationChannel(id)
    manager().createNotificationChannel(NotificationChannel(id, id, NotificationManager.IMPORTANCE_NONE))
  }

  private val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)

  // --- n. 7: un promemoria scaduto con le notifiche spente ------------------------------------------------------------

  // La scelta di Alessio (domanda 5, B): un promemoria scaduto con le notifiche spente si perde, il registro lo segna
  // «non mostrato», e alla riaccensione delle notifiche non arriva niente di vecchio.

  @Test
  fun r2n7_promemoriaConLeNotificheSpente_siPerde_eIlRegistroDiceNonMostrato() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    shadowNm().setNotificationsEnabled(false)
    notify.wallNow = { now + 61_000 }
    fireReminder("pasta")
    idle()
    assertNull(shown("fluid.rem/pasta"))
    val delivery = notify.center.deliveries.value.singleOrNull()
    assertNotNull("il registro segna il promemoria, non mostrato", delivery)
    assertTrue("segnato non mostrato: $delivery", delivery.toString().contains("shown=false"))
    assertEquals(now + 60_000, delivery!!.scheduledMillis)
    assertEquals("Le notifiche erano spente", delivery.reason)
    assertFalse("niente ReminderDelivered per un promemoria non mostrato", events.any { it is FluidNotifyEvent.ReminderDelivered })
    assertTrue("non resta in programma", notify.center.reminders.value.isEmpty())
    // Le notifiche tornano (la pagina si rilegge, l'app torna in primo piano, un processo nuovo): niente di vecchio.
    shadowNm().setNotificationsEnabled(true)
    notify.wallNow = { now + 5 * 60_000 }
    notify.center.refresh()
    FluidNotifyTesting.setOutside(notify, true)
    FluidNotifyTesting.setOutside(notify, false)
    idle()
    newProcess()
    notify.wallNow = { now + 6 * 60_000 }
    notify.reminders.restore()
    idle()
    assertNull(shown("fluid.rem/pasta"))
    assertEquals(1, notify.center.deliveries.value.size)
  }

  @Test
  fun r2n7_ilCanaleSpento_unPromemoriaOgniGiorno_siPerdeOggi_eSuonaDomani() {
    val now = millis("2099-11-25T07:00")
    notify.wallNow = { now }
    notify.reminders.schedule("medicina", FluidReminderTime.local(LocalDateTime.parse("2099-11-25T08:00")), FluidReminderContent("Medicina"), repeat = daily)
    channelOff(FluidNotifyChannel.RemindersId)
    notify.wallNow = { millis("2099-11-25T08:00") }
    fireReminder("medicina")
    idle()
    assertNull(shown("fluid.rem/medicina"))
    val delivery = notify.center.deliveries.value.singleOrNull()
    assertNotNull(delivery)
    assertTrue(delivery.toString().contains("shown=false"))
    assertEquals("domani alle 8:00 di nuovo", millis("2099-11-26T08:00"), reminderAlarm("medicina").triggerAtMs)
  }

  // --- n. 9: i riassunti e i messaggi sensibili -----------------------------------------------------------------------

  @Test
  fun r2n9_ilRiassuntoDelFilo_nonPortaIlTestoSensibile() {
    notify.post(FluidNotifyMessage("m1", "voti", "Matematica: 4", "Da recuperare entro venerdì", thread = "voti", sensitive = true))
    notify.post(FluidNotifyMessage("m2", "voti", "Storia: 7", thread = "voti"))
    idle(3_000)
    val summary = shown("fluid.sum/fluid.thread/voti")!!
    assertNotEquals(Notification.VISIBILITY_PUBLIC, summary.visibility)
    val public = summary.publicVersion
    assertNotNull("il riassunto di un filo con un messaggio sensibile ha la sua versione pubblica", public)
    val publicText = text(public!!, Notification.EXTRA_TITLE) + text(public, Notification.EXTRA_TEXT) + lines(public)
    assertFalse(publicText, "Matematica" in publicText || "recuperare" in publicText)
    val all = lines(summary) + text(summary, Notification.EXTRA_TEXT) + text(summary, Notification.EXTRA_TITLE)
    assertFalse("le righe del riassunto non portano il messaggio sensibile: $all", all.any { "Matematica" in it || "recuperare" in it })
    assertTrue("il messaggio normale resta: $all", all.any { "Storia: 7" in it })
  }

  @Test
  fun r2n9_ilRiepilogo_nonPortaIlTestoSensibile() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    notify.post(FluidNotifyMessage("c1", "circolari", "Colloquio con la prof", "Tuo figlio ha due insufficienze", sensitive = true))
    notify.post(FluidNotifyMessage("c2", "circolari", "Uscita anticipata"))
    fireDigest()
    idle(3_000)
    val summary = shown(FluidNotify.DigestTag)!!
    assertNotNull(summary.publicVersion)
    val all = lines(summary) + text(summary, Notification.EXTRA_TEXT) + text(summary, Notification.EXTRA_TITLE)
    assertFalse("il riepilogo non porta il messaggio sensibile: $all", all.any { "Colloquio" in it || "insufficienze" in it })
  }

  // --- n. 10: rimettere i promemoria a ogni avvio ---------------------------------------------------------------------

  @Test
  fun r2n10_rimettereOgniGiornoDopoLaConsegna_nonSuonaDueVolte() {
    notify.wallNow = { millis("2099-11-25T07:00") }
    val at = FluidReminderTime.local(LocalDateTime.parse("2099-11-25T08:00"))
    notify.reminders.schedule("medicina", at, FluidReminderContent("Medicina"), repeat = daily)
    notify.wallNow = { millis("2099-11-25T08:00") }
    fireReminder("medicina")
    idle()
    manager().cancelAll()
    // L'app riparte e rimette i suoi promemoria, come a ogni avvio.
    notify.wallNow = { millis("2099-11-25T08:05") }
    notify.reminders.schedule("medicina", at, FluidReminderContent("Medicina"), repeat = daily)
    idle()
    assertNull("l'occorrenza delle 8:00 è già arrivata", shown("fluid.rem/medicina"))
    assertEquals(1, notify.center.deliveries.value.size)
    assertEquals(millis("2099-11-26T08:00"), reminderAlarm("medicina").triggerAtMs)
  }

  @Test
  fun r2n10_rimettereUnPromemoriaGiaArrivato_nonSuonaDueVolte() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    notify.wallNow = { now + 61_000 }
    fireReminder("pasta")
    idle()
    manager().cancelAll()
    notify.wallNow = { now + 120_000 }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    idle()
    assertNull(shown("fluid.rem/pasta"))
    assertEquals(1, notify.center.deliveries.value.size)
    // Un'ora nuova con lo stesso id è un promemoria nuovo.
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 600_000), FluidReminderContent("Scola la pasta"))
    assertEquals(now + 600_000, reminderAlarm("pasta").triggerAtMs)
  }

  // --- n. 11: una scrittura fallita della memoria ---------------------------------------------------------------------

  @Test
  fun r2n11_unaScritturaFallita_siVedeNeiControlli_ePoiSiRiscriveTutto() {
    val file = File(app.noBackupFilesDir, "fluid_notify.json")
    file.delete()
    // Il posto del file è occupato da una cartella piena: ogni scrittura fallisce, come col disco pieno.
    file.mkdirs()
    File(file, "occupato").writeText("x")
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("verifica", FluidReminderTime.at(now + 3_600_000), FluidReminderContent("Verifica di storia"))
    flushStore()
    notify.center.refresh()
    val storage = notify.center.health.value.checks.firstOrNull { it.id == "storage" }
    assertNotNull("i controlli dicono che la memoria non si salva: ${notify.center.health.value.checks.map { it.id }}", storage)
    assertFalse(storage!!.ok)
    assertEquals("storage", notify.center.health.value.checks.first().id)
    // Lo spazio torna: la scrittura dopo salva tutto, anche il promemoria di prima, e il controllo se ne va.
    File(file, "occupato").delete()
    file.delete()
    notify.reminders.schedule("dopo", FluidReminderTime.at(now + 7_200_000), FluidReminderContent("Più tardi"))
    flushStore()
    notify.center.refresh()
    assertNull(notify.center.health.value.checks.firstOrNull { it.id == "storage" })
    newProcess()
    assertEquals(setOf("verifica", "dopo"), notify.store.read().reminders.map { it.id }.toSet())
  }

  // --- n. 12: il riepilogo svuotato prima di pubblicare ---------------------------------------------------------------

  @Test
  fun r2n12_ilRiepilogoConLeNotificheSpente_nonPerdeLeAttese() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    assertEquals(FluidNotifyResult.InDigest, notify.post(FluidNotifyMessage("c1", "circolari", "Circolare 42")))
    shadowNm().setNotificationsEnabled(false)
    fireDigest()
    idle(3_000)
    assertEquals("l'attesa resta per il riepilogo dopo", 1, notify.center.settings.value.digest.waiting)
    shadowNm().setNotificationsEnabled(true)
    fireDigest()
    idle(3_000)
    assertNotNull(shown("fluid.msg/c1"))
    assertEquals(0, notify.center.settings.value.digest.waiting)
  }

  @Test
  fun r2n12_ilProcessoMuoreAMetàRiepilogo_leAtteseNonPubblicateRestano() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    for (i in 1..6) notify.post(FluidNotifyMessage("c$i", "circolari", "Circolare $i"))
    fireDigest()
    // La porta ne pubblica quattro al secondo: le altre aspettano il turno quando il processo muore.
    flushStore()
    newProcess()
    assertEquals(listOf("c5", "c6"), notify.store.read().digestQueue.map { it.id })
  }

  // --- n. 13: schedule da un filo di sottofondo -----------------------------------------------------------------------

  @Test
  fun r2n13_unPromemoriaScadutoMessoDaUnFiloDiSottofondo_siPubblicaSulPrincipale() {
    val onMain = ArrayList<Boolean>()
    val real = notify.poster
    notify.poster = object : FluidNotifyPoster {
      override fun notify(tag: String?, id: Int, notification: Notification) {
        onMain += Looper.myLooper() == Looper.getMainLooper()
        real.notify(tag, id, notification)
      }
      override fun cancel(tag: String?, id: Int) = real.cancel(tag, id)
      override fun active(): List<StatusBarNotification> = real.active()
    }
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    val worker = Thread {
      notify.reminders.schedule("pasta", FluidReminderTime.at(now - 60_000), FluidReminderContent("Scola la pasta"))
    }
    worker.start()
    worker.join()
    idle()
    assertTrue("pubblicato", onMain.isNotEmpty())
    assertTrue("la porta del ritmo lavora solo sul principale: $onMain", onMain.all { it })
    assertNotNull(shown("fluid.rem/pasta"))
  }

  // --- minori: notifiche e promemoria ---------------------------------------------------------------------------------

  @Test
  fun r2m_leAttivitàVive_aTelefonoBloccato_soloLaVersioneSenzaDettagli() {
    notify.liveUpdates.publish(FluidLiveUpdate("video", FluidLiveKind.Transfer, "Referto_ospedale.pdf", text = "3 di 10 MB", progress = 0.3f))
    idle()
    val n = shown("fluid.live/video")!!
    assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
    val public = n.publicVersion!!
    assertFalse("Referto" in text(public, Notification.EXTRA_TITLE) + text(public, Notification.EXTRA_TEXT))
    assertEquals("la barra resta anche nascosta", 30, public.extras.getInt(Notification.EXTRA_PROGRESS))
  }

  @Test
  fun r2m_ilPallino_contaAncheIPromemoria() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now - 1_000), FluidReminderContent("Scola la pasta"))
    idle()
    assertNotNull(shown("fluid.rem/pasta"))
    assertEquals(1, notify.badgeCount())
  }

  @Test
  fun r2m_unPromemoriaGiàPassato_nonDàUnMotivoFalso() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("ieri", FluidReminderTime.at(now - 30 * 60_000), FluidReminderContent("Era per mezz'ora fa"))
    val delivery = notify.center.deliveries.value.single()
    assertEquals("Era già passato quando l'app l'ha messo in programma", delivery.reason)
  }

  @Test
  fun r2m_unRiavvio_vistoAncheDaUnAvvioNormaleDellApp() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("verifica", FluidReminderTime.at(now + 3_600_000), FluidReminderContent("Verifica di storia"))
    app.getSystemService(AlarmManager::class.java).cancel(notify.reminders.pendingIntent("verifica"))
    // Il telefono è ripartito cinque minuti fa, due ore dopo; l'app si apre prima che arrivi BOOT_COMPLETED.
    newProcess()
    notify.wallNow = { now + 2 * 3_600_000 }
    notify.elapsedNow = { 5 * 60_000L }
    notify.reminders.restore()
    assertEquals("Il telefono era spento o appena riavviato", notify.center.deliveries.value.single().reason)
  }

  @Test
  fun r2m_unCanaleSparito_siRicrea_eLaNotificaArriva() {
    manager().deleteNotificationChannel("voti")
    assertEquals(FluidNotifyResult.Posted, notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8")))
    assertNotNull("il canale torna", manager().getNotificationChannel("voti"))
    idle()
    assertNotNull(shown("fluid.msg/v1"))
  }

  @Test
  fun r2m_unCanaleNonDichiarato_nonFaCadereLApp() {
    assertEquals(FluidNotifyResult.ChannelOff, notify.post(FluidNotifyMessage("p1", "promozioni", "Sconto del 20%")))
  }

  @Test
  @Config(sdk = [34], shadows = [ExactRevokedAlarmManager::class])
  fun r2m_permessoEsattoToltoInCorsa_unaFinestraDiDieciMinuti() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule("pasta", FluidReminderTime.at(now + 60_000), FluidReminderContent("Scola la pasta"))
    assertEquals(FluidNotifyProfile.InexactWindowMs, reminderAlarm("pasta").windowLengthMs)
    assertFalse(notify.center.reminders.value.single().exact)
  }

  @Test
  fun r2m_unaMemoriaRotta_siCopia_eSiVedeNeiControlli() {
    FluidNotifyTesting.reset()
    val file = File(app.noBackupFilesDir, "fluid_notify.json")
    val broken = """{"v":1,"reminders":[{"id":"rotto"},{"id":"buono","at":4102444800000,"content":{"title":"Buono","actions":[]},"exact":true,"alarm":false}]}"""
    file.parentFile!!.mkdirs()
    file.writeText(broken)
    notify = FluidNotify.install(app, config())
    assertEquals(listOf("buono"), notify.store.read().reminders.map { it.id })
    notify.center.refresh()
    val storage = notify.center.health.value.checks.firstOrNull { it.id == "storage" }
    assertNotNull(storage)
    assertFalse(storage!!.ok)
    assertEquals("la copia del file com'era", broken, File(app.noBackupFilesDir, "fluid_notify.json.bak").readText())
  }

  @Test
  fun r2m_laCodaDelRiepilogo_haUnTetto() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    for (i in 1..60) notify.post(FluidNotifyMessage("c$i", "circolari", "Circolare $i"))
    val queue = notify.store.read().digestQueue
    assertEquals(50, queue.size)
    assertEquals("restano le più recenti", "c60", queue.last().id)
  }

  @Test
  fun r2m_togliereUnFilo_toglieAncheLeAttese() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    notify.center.setChannelInDigest("circolari", true)
    notify.post(FluidNotifyMessage("c1", "circolari", "Circolare 42", thread = "circ"))
    notify.post(FluidNotifyMessage("c2", "circolari", "Circolare 43"))
    notify.cancelThread("circ")
    idle()
    assertEquals(listOf("c2"), notify.store.read().digestQueue.map { it.id })
  }

  @Test
  fun r2m_unFiloScartato_ilMessaggioDopoSuona() {
    notify.post(FluidNotifyMessage("v1", "voti", "Matematica: 8", thread = "voti"))
    idle(1_000)
    manager().cancelAll() // l'utente scarta il filo
    idle(30_000)
    notify.post(FluidNotifyMessage("v2", "voti", "Storia: 7", thread = "voti"))
    idle(1_000)
    assertEquals(NotificationCompat.GROUP_ALERT_ALL, shown("fluid.msg/v2")!!.groupAlertBehavior)
  }

  @Test
  fun r2m_iControlli_guardanoAncheAttivitàInCorsoERiepilogo() {
    notify.center.setDigest(true, listOf(8 * 60, 18 * 60))
    channelOff(FluidNotifyChannel.LiveId)
    channelOff(FluidNotifyChannel.DigestId)
    notify.center.refresh()
    val failing = notify.center.health.value.checks.filter { !it.ok }.map { it.id }
    assertTrue(failing.toString(), "channel:${FluidNotifyChannel.LiveId}" in failing)
    assertTrue(failing.toString(), "channel:${FluidNotifyChannel.DigestId}" in failing)
  }

  @Test
  fun r2m_ilPermessoConcessoDopo_lAttivitàRicompareDaSola() {
    shadowNm().setNotificationsEnabled(false)
    notify.liveUpdates.publish(FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", progress = 0.4f))
    idle()
    assertNull(shown("fluid.live/video"))
    shadowNm().setNotificationsEnabled(true)
    notify.center.refresh() // la pagina del permesso, o il ritorno nell'app
    idle()
    assertNotNull("ricompare senza aspettare che l'app ripubblichi", shown("fluid.live/video"))
  }

  @Test
  fun r2m_unTastoCheApreLApp_toglieIlPromemoria() {
    val now = millis("2099-11-25T10:00")
    notify.wallNow = { now }
    notify.reminders.schedule(
      "pasta", FluidReminderTime.at(now - 1_000),
      FluidReminderContent("Scola la pasta", actions = listOf(FluidNotifyAction("Apri la ricetta", "ricetta", opensApp = true))),
    )
    idle()
    val action = shown("fluid.rem/pasta")!!.actions.single()
    val intent = shadowOf(action.actionIntent).savedIntent
    assertTrue(notify.handleIntent(intent))
    idle()
    assertTrue(events.any { it is FluidNotifyEvent.ReminderAction })
    assertNull("eseguito: la notifica se ne va", shown("fluid.rem/pasta"))
  }

  @Test
  fun r2m_unIndirizzoEsterno_siApreFuoriDallApp() {
    notify.post(FluidNotifyMessage("n1", "circolari", "Il giornalino", open = "https://example.com/giornalino"))
    idle()
    val intent = shadowOf(shown("fluid.msg/n1")!!.contentIntent).savedIntent
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("https://example.com/giornalino", intent.dataString)
    assertNull("nessuna attività dell'app lo apre: niente pacchetto, lo apre il browser", intent.`package`)
  }

  @Test
  fun r2m_unaRispostaPrimaDellInstall_nonSiPerde() {
    notify.post(FluidNotifyMessage("lista", "voti", "Alessio ti ha scritto", "Ci sei?", person = "Alessio", reply = FluidNotifyReply()))
    idle()
    val intent = shadowOf(shown("fluid.msg/lista")!!.actions.first().actionIntent).savedIntent
    RemoteInput.addResultsToIntent(
      arrayOf(RemoteInput.Builder(FluidNotify.ReplyKey).build()), intent,
      Bundle().apply { putCharSequence(FluidNotify.ReplyKey, "Sì, arrivo") },
    )
    // Processo nuovo avviato dalla risposta; l'app installa engine-notify solo più tardi (in un'attività).
    FluidNotifyTesting.reset()
    events.clear()
    FluidNotifyReceiver().onReceive(app, intent)
    idle()
    notify = FluidNotify.install(app, config())
    idle()
    val reply = events.filterIsInstance<FluidNotifyEvent.Reply>().singleOrNull()
    assertNotNull("la risposta arriva all'app appena installa: $events", reply)
    assertEquals("Sì, arrivo", reply!!.text)
  }

  @Test
  fun r2m_installTardivo_conUnAttivitàGiàInVista_restaDentro() {
    FluidNotifyTesting.reset()
    val processes = ActivityManager.RunningAppProcessInfo().apply {
      pid = Process.myPid()
      importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
    shadowOf(app.getSystemService(ActivityManager::class.java)).setProcesses(listOf(processes))
    val first = Robolectric.buildActivity(Activity::class.java).setup()
    // L'app installa engine-notify tardi, con la prima attività già avviata.
    notify = FluidNotify.install(app, config())
    assertFalse(notify.foreground.outside.value)
    val second = Robolectric.buildActivity(Activity::class.java).setup()
    first.pause().stop()
    idle(FluidNotifyProfile.OutsideDelayMs + 100)
    assertFalse("la seconda attività è in vista: l'app è dentro", notify.foreground.outside.value)
    second.pause().stop()
    idle(FluidNotifyProfile.OutsideDelayMs + 100)
    assertTrue(notify.foreground.outside.value)
  }

  // --- n. 8: le conversazioni (la scelta di Alessio, domanda 2: B) ----------------------------------------------------

  private fun shortcuts(): List<ShortcutInfo> = app.getSystemService(ShortcutManager::class.java).dynamicShortcuts

  private fun fromPerson(id: String, person: String) =
    FluidNotifyMessage(id, "voti", "$person ti ha scritto", "Ci sei?", person = person, reply = FluidNotifyReply())

  @Test
  fun r2n8_unaPersona_diventaUnaConversazione() {
    notify.post(fromPerson("m1", "Alessio"))
    idle()
    val n = shown("fluid.msg/m1")!!
    assertNotNull("la notifica è legata alla scorciatoia della conversazione", n.shortcutId)
    val shortcut = shortcuts().singleOrNull { it.id == n.shortcutId }
    assertNotNull("la scorciatoia dinamica c'è: ${shortcuts().map { it.id }}", shortcut)
    assertEquals("Alessio", shortcut!!.shortLabel.toString())
    // `isLongLived` è nascosta nell'SDK pubblico: in Robolectric la classe è quella vera di Android, si legge per riflessione.
    val longLived = ShortcutInfo::class.java.getMethod("isLongLived").invoke(shortcut) as Boolean
    assertTrue("di lunga vita, come vuole Android per le conversazioni", longLived)
    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)!!
    assertEquals("la persona ha la chiave della conversazione", n.shortcutId, style.messages.single().person!!.key)
  }

  @Test
  fun r2n8_ilTetto_toglieLaConversazionePiùVecchia_maiLeScorciatoieDellApp() {
    shadowOf(app.getSystemService(ShortcutManager::class.java)).setMaxShortcutCountPerActivity(3)
    val own = ShortcutInfo.Builder(app, "nuova").setShortLabel("Nuova nota")
      .setIntent(Intent(Intent.ACTION_VIEW).setPackage(app.packageName)).build()
    app.getSystemService(ShortcutManager::class.java).addDynamicShortcuts(listOf(own))
    for ((i, person) in listOf("Anna", "Bruno", "Carla").withIndex()) {
      notify.post(fromPerson("m$i", person))
      idle(1_000)
    }
    val ids = shortcuts().map { it.id }.toSet()
    assertEquals("tre in tutto: ${shortcuts().map { it.shortLabel }}", 3, ids.size)
    assertTrue("la scorciatoia dell'app resta", "nuova" in ids)
    assertEquals(setOf("Bruno", "Carla"), shortcuts().filter { it.id != "nuova" }.map { it.shortLabel.toString() }.toSet())
  }

  @Test
  fun r2n8_unaConversazioneSenzaNotifiche_esceDalMenu() {
    notify.post(fromPerson("m1", "Alessio"))
    notify.post(fromPerson("m2", "Bea"))
    idle(1_000)
    assertEquals(2, shortcuts().size)
    // L'app toglie la notifica di Alessio (letta): la sua conversazione esce dal menu dell'icona.
    notify.cancel("m1")
    idle()
    assertEquals(listOf("Bea"), shortcuts().map { it.shortLabel.toString() })
    // L'utente toglie quella di Bea: anche lei esce.
    val dismissed = shown("fluid.msg/m2")!!
    manager().cancel("fluid.msg/m2", FluidNotify.MessageNotificationId)
    assertNotNull("chi toglie una conversazione avvisa l'engine", dismissed.deleteIntent)
    FluidNotifyReceiver().onReceive(app, shadowOf(dismissed.deleteIntent).savedIntent)
    idle()
    assertTrue(shortcuts().isEmpty())
  }

  // --- sospetto 4: il deleteIntent dell'esito -------------------------------------------------------------------------

  @Test
  fun r2s4_ilDeleteDellEsitoVecchio_nonTogliLAttivitàNuova() {
    val transfer = FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", progress = 0.4f)
    notify.liveUpdates.publish(transfer)
    idle()
    notify.liveUpdates.finish("video", FluidLiveOutcome.succeeded("Video pronto"))
    idle(2_000)
    val ended = shown("fluid.live/video")!!
    // Un'attività nuova con lo stesso id, mentre l'esito di prima è ancora a schermo.
    notify.liveUpdates.publish(transfer.copy(progress = 0.1f))
    idle(2_000)
    // Android manda il deleteIntent dell'esito vecchio (scaduto dopo quattro ore, o sostituito).
    ended.deleteIntent?.let { FluidNotifyReceiver().onReceive(app, shadowOf(it).savedIntent) }
    notify.liveUpdates.publish(transfer.copy(progress = 0.5f))
    idle(2_000)
    assertFalse(events.any { it is FluidNotifyEvent.LiveDismissed })
    assertEquals(50, shown("fluid.live/video")!!.extras.getInt(Notification.EXTRA_PROGRESS))
  }

  @Test
  fun r2s4_unTastoDellAttivitàFinita_nonComandaQuellaNuova() {
    var cancelled = 0
    val first = FluidLiveUpdate(
      "video", FluidLiveKind.Transfer, "Converto il video", progress = 0.4f,
      actions = listOf(dev.antigravity.fluidengine.foundation.live.FluidLiveAction("Annulla", { cancelled++ })),
    )
    notify.liveUpdates.publish(first)
    idle()
    val oldButton = shadowOf(shown("fluid.live/video")!!.actions.single().actionIntent).savedIntent
    notify.liveUpdates.finish("video", FluidLiveOutcome.Silent)
    notify.liveUpdates.publish(first.copy(title = "Converto l'altro video"))
    idle()
    FluidNotifyReceiver().onReceive(app, oldButton)
    assertEquals("il tasto della notifica vecchia non annulla l'attività nuova", 0, cancelled)
  }
}

/** Il permesso degli allarmi esatti tolto fra il controllo e la richiesta: Android lancia `SecurityException`. */
@Implements(AlarmManager::class)
class ExactRevokedAlarmManager : ShadowAlarmManager() {
  @Implementation
  override fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
    throw SecurityException("Caller needs to hold android.permission.SCHEDULE_EXACT_ALARM")
  }
}
