package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.live.FluidLiveAction
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOrigin
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLivePoint
import dev.antigravity.fluidengine.foundation.live.FluidLiveStage
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.notify.FluidNotify
import dev.antigravity.fluidengine.notify.FluidNotifyChannel
import dev.antigravity.fluidengine.notify.FluidNotifyChannelGroup
import dev.antigravity.fluidengine.notify.FluidNotifyConfig
import dev.antigravity.fluidengine.notify.FluidNotifyEvent
import dev.antigravity.fluidengine.notify.FluidNotifyImportance
import dev.antigravity.fluidengine.notify.FluidNotifyMessage
import dev.antigravity.fluidengine.notify.FluidNotifyReply
import dev.antigravity.fluidengine.notify.FluidReminderContent
import dev.antigravity.fluidengine.notify.FluidReminderTime
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHealthPage
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationSettingsPage
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidUpcomingRemindersPage
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutcome
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutside
import dev.antigravity.fluidengine.ui.fluid.activity.rememberFluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F8.19 · Notifiche, promemoria e attività fuori dall'app. Le scene usano engine-notify vero: le notifiche, i Live Update
// e i promemoria sono quelli di Android, e si guardano uscendo dall'app (la tendina, la barra di stato, la schermata di
// blocco). La galleria installa engine-notify solo quando si apre una di queste scene: il primo fotogramma dell'app non
// cambia. I permessi (notifiche, allarmi esatti) li chiede solo la build di debug (`src/debug/AndroidManifest.xml`).

internal val F8p19Scenes = listOf(
  NewElementScene(
    "attivita-fuori", "Un'attività lunga, dentro e fuori",
    "Una conversione: la pillola nell'app, il Live Update quando esci, di nuovo la pillola al rientro",
  ) { LongActivityScene(it) },
  NewElementScene(
    "viaggio-live", "Il viaggio di Transit",
    "Un Live Update con le tappe e le fermate, anche come lo vedrebbe la mamma con «Avvisa quando arrivo»",
  ) { TripScene(it) },
  NewElementScene(
    "notifiche-raccolte", "Tre voti in raffica",
    "«3 nuovi voti» invece di tre sveglie, e la risposta rapida a «Alessio ti ha condiviso una lista»",
  ) { GroupedScene(it) },
  NewElementScene(
    "promemoria-minuto", "Un promemoria fra un minuto",
    "L'allarme giusto, «Cosa succederà» e il registro di quando arriva davvero",
  ) { ReminderScene(it) },
  NewElementScene(
    "impostazioni-notifiche", "Le impostazioni delle notifiche",
    "I canali con parole comuni, il riepilogo programmato (spento di serie) e i Live Update",
  ) { SettingsScene(it) },
  NewElementScene(
    "notifiche-non-arrivano", "Le notifiche non arrivano?",
    "I controlli: permesso, One UI che sospende l'app, canali spenti, allarmi esatti, Non disturbare",
  ) { HealthScene(it) },
)

internal val F8p19Section = NewElementSection(
  pack = "F8.19",
  title = "Notifiche, promemoria e attività fuori dall'app",
  scenes = F8p19Scenes,
  detail = "engine-notify: il Live Update di Android 16, le notifiche raccolte, i promemoria che arrivano davvero",
)

internal val F8p19TryItems = listOf(
  TryItem(
    "f8-19-fuori", TryArea.Pages, "Esci durante la conversione",
    "Avvia «Converti il video» ed esci con il gesto Home: guarda la barra di stato e la tendina, tocca «Pausa» nella notifica e rientra; la pillola deve riprendere il posto della notifica.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Un'attività lunga, dentro e fuori"),
  ),
  TryItem(
    "f8-19-viaggio", TryArea.Pages, "Il viaggio nella barra di stato",
    "Avvia il viaggio ed esci dall'app: guarda il gettone con l'ora d'arrivo, la barra a tappe e la schermata di blocco; poi prova «Come la mamma» e «Smetti di seguire».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il viaggio di Transit"),
  ),
  TryItem(
    "f8-19-voti", TryArea.Pages, "Tre voti, una sveglia",
    "Tocca «Arrivano tre voti»: deve suonare una volta sola e la tendina deve dire «3 nuovi voti»; poi rispondi ad Alessio dalla notifica.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Tre voti in raffica"),
  ),
  TryItem(
    "f8-19-promemoria", TryArea.Pages, "Un promemoria fra un minuto",
    "Tocca «Ricordamelo fra un minuto», blocca il telefono e aspetta: poi apri «Cosa succederà» e guarda il ritardo vero.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Un promemoria fra un minuto"),
  ),
  TryItem(
    "f8-19-non-arrivano", TryArea.Pages, "Le notifiche non arrivano?",
    "Spegni le notifiche della galleria nelle impostazioni e torna: la pagina deve dirlo e portarti nel punto giusto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Le notifiche non arrivano?"),
  ),
)

/** I tag dei test. */
internal object F8p19Tags {
  const val Convert = "f8p19:converti"
  const val ConvertFast = "f8p19:converti-svelto"
  const val Pause = "f8p19:pausa"
  const val Fail = "f8p19:fallisce"
  const val Cancel = "f8p19:annulla"
  const val Permission = "f8p19:permesso"
  const val TripStart = "f8p19:viaggio"
  const val TripFollowed = "f8p19:viaggio-mamma"
  const val TripState = "f8p19:viaggio-stato"
  const val Grades = "f8p19:voti"
  const val Share = "f8p19:lista"
  const val Clear = "f8p19:togli"
  const val Events = "f8p19:eventi"
  const val Remind = "f8p19:promemoria"
  const val RemindDaily = "f8p19:promemoria-ogni-giorno"
  const val Upcoming = "f8p19:cosa-succedera"
}

// --- l'engine della galleria ------------------------------------------------------------------------------------------

private val Scuola = FluidNotifyChannelGroup("galleria.scuola", "Scuola")
private val Persone = FluidNotifyChannelGroup("galleria.persone", "Persone")

/** I canali della galleria: quelli di ClasseViva e dell'account, con parole comuni. */
internal object F8p19Channels {
  val Voti = FluidNotifyChannel(
    "galleria.voti", "Voti nuovi", "Quando la scuola pubblica un voto",
    importance = FluidNotifyImportance.Urgent, group = Scuola,
    summary = { n -> if (n == 1) "1 nuovo voto" else "$n nuovi voti" },
  )
  val Circolari = FluidNotifyChannel(
    "galleria.circolari", "Circolari", "Le comunicazioni della scuola: possono aspettare il riepilogo",
    importance = FluidNotifyImportance.Normal, group = Scuola,
    summary = { n -> if (n == 1) "1 circolare" else "$n circolari" },
  )
  val DaPersone = FluidNotifyChannel(
    "galleria.persone", "Dalle persone", "Quando qualcuno ti manda una lista, un invito o un voto",
    importance = FluidNotifyImportance.Urgent, group = Persone, category = NotificationCompat.CATEGORY_MESSAGE,
    summary = { n -> "$n cose dalle persone" },
  )
  val All = listOf(Voti, Circolari, DaPersone)
}

/** L'ultimo evento delle notifiche della galleria (un tasto, una risposta), per mostrarlo nella scena. */
internal object F8p19Events {
  val last = mutableStateOf<String?>(null)

  /** Quante volte l'utente ha smesso di seguire il viaggio: la scena del viaggio lo ferma (come farebbe Transit). */
  val unfollowed = androidx.compose.runtime.mutableIntStateOf(0)

  fun record(event: FluidNotifyEvent) {
    last.value = when (event) {
      is FluidNotifyEvent.Reply -> "Hai risposto: «${event.text}»"
      is FluidNotifyEvent.Action -> "Tasto «${event.key}» di ${event.messageId}"
      is FluidNotifyEvent.ReminderDelivered -> "Promemoria arrivato"
      is FluidNotifyEvent.Unfollowed -> {
        unfollowed.intValue++
        "Hai smesso di seguire il viaggio"
      }
      is FluidNotifyEvent.LiveDismissed -> "Hai tolto la notifica: non torna"
      else -> event.toString()
    }
  }
}

/** engine-notify con i canali della galleria (installato alla prima scena che lo usa). */
internal fun galleryNotify(context: Context): FluidNotify {
  val current = FluidNotify.get(context)
  if (current.config.channels.map { it.id } == F8p19Channels.All.map { it.id }) return current
  return FluidNotify.install(context, FluidNotifyConfig(channels = F8p19Channels.All, onEvent = F8p19Events::record))
}

@Composable
private fun rememberGalleryNotify(): FluidNotify {
  val context = LocalContext.current
  return remember(context) { galleryNotify(context) }
}

/** Una riga che fa una cosa. */
@Composable
private fun CommandRow(title: String, subtitle: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
  FluidListRow(
    title = title, subtitle = subtitle, modifier = Modifier.testTag(tag),
    onClick = if (enabled) onClick else null, disclosure = false,
  )
}

/** La riga del permesso delle notifiche: dal tasto, con la spiegazione (F8.11). */
@Composable
private fun PermissionRow(notify: FluidNotify) {
  val settings by notify.center.settings.collectAsStateWithLifecycle()
  val permission = rememberFluidPermission(
    FluidPermission.Notifications,
    reason = "Per vedere l'attività, i voti e i promemoria anche fuori dalla galleria.",
  ) { notify.center.refresh() }
  if (!settings.notificationsAllowed) {
    FluidListRow(
      title = "Attiva le notifiche",
      subtitle = "Senza, fuori dall'app non si vede niente",
      modifier = Modifier.fluidPermissionSource(permission).testTag(F8p19Tags.Permission),
      tone = FluidTone.Warning,
      onClick = { permission.request { notify.center.refresh() } },
    )
  }
}

// --- un'attività lunga, dentro e fuori --------------------------------------------------------------------------------

/**
 * Universal Converter converte un video: la pillola al posto del titolo. Uscendo dall'app la stessa attività diventa il
 * Live Update (Android 16) o la notifica con l'avanzamento (prima); «Pausa» e «Annulla» della notifica chiamano le stesse
 * funzioni della pillola; rientrando la notifica se ne va. Finita fuori, resta «Video pronto».
 */
@Composable
private fun LongActivityScene(onBack: () -> Unit) {
  val notify = rememberGalleryNotify()
  val activities = rememberFluidActivityHostState()
  val scope = rememberCoroutineScope()
  val progress = remember { mutableFloatStateOf(0f) }
  var paused by remember { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }
  var seconds by rememberSaveable { mutableStateOf(120) }

  fun publish() {
    activities.publish(
      FluidActivity.transfer(
        id = "video",
        progress = { progress.floatValue },
        title = "Converto il video",
        detail = "Gita.mov → MP4 · ${(progress.floatValue * 100).toInt()}%",
        paused = paused,
        onPause = { paused = true; publish() },
        onResume = { paused = false; publish() },
        onCancel = {
          job?.cancel()
          activities.remove("video")
        },
      ),
    )
  }

  fun start(totalSeconds: Int) {
    job?.cancel()
    progress.floatValue = 0f
    paused = false
    publish()
    job = scope.launch {
      val stepMs = 100L
      val steps = totalSeconds * 1000L / stepMs
      var done = 0L
      var lastDetail = -1
      while (done < steps) {
        delay(stepMs)
        if (paused) continue
        done++
        progress.floatValue = done.toFloat() / steps
        // La riga cambia ogni 10%: un cambio di stato, non un aggiornamento al secondo.
        val tenth = (progress.floatValue * 10).toInt()
        if (tenth != lastDetail) {
          lastDetail = tenth
          publish()
        }
      }
      activities.finish("video", FluidActivityOutcome.succeeded("Video pronto"))
    }
  }

  FluidActivityHost(activities) {
    FluidActivityOutside(activities, notify.liveUpdates.whenOutside)
    FluidScreen(title = "Converter", onBack = onBack, toolbar = { title(FluidToolbarTitleMode.Inline) }) {
      item(key = "permesso") { PermissionRow(notify) }
      item(key = "durata") {
        Column {
          FluidSectionHeader("Quanto dura")
          FluidSegmentedControl(
            options = listOf(30, 120, 300),
            selected = seconds,
            onSelect = { seconds = it },
            label = { if (it < 60) "$it s" else "${it / 60} min" },
          )
        }
      }
      item(key = "comandi") {
        Column {
          FluidSectionHeader("La conversione")
          FluidListGroup {
            CommandRow("Converti il video", "La pillola prende il posto di «Converter»; poi esci dall'app", F8p19Tags.Convert) { start(seconds) }
            FluidListDivider()
            CommandRow("Pausa o riprendi", "Come il tasto della notifica", F8p19Tags.Pause) {
              if (activities.activities.any { it.id == "video" }) {
                paused = !paused
                publish()
              }
            }
            FluidListDivider()
            CommandRow("Non riesce", "Finisce con «Spazio esaurito»: fuori resta l'errore", F8p19Tags.Fail) {
              job?.cancel()
              activities.finish("video", FluidActivityOutcome.failed("Spazio esaurito"))
            }
            FluidListDivider()
            CommandRow("Annulla", "Finisce senza esito: la notifica se ne va", F8p19Tags.Cancel) {
              job?.cancel()
              activities.remove("video")
            }
          }
        }
      }
      item(key = "nota") {
        FluidSectionFootnote(
          "Si pubblica una volta sola, nella pillola: fuori dall'app la stessa attività diventa la notifica. Su Android 16 è un " +
            "Live Update, col gettone della percentuale nella barra di stato; prima è una notifica con la barra. La notifica " +
            "si aggiorna al più una volta al secondo, e a ogni cambio di stato.",
        )
      }
    }
  }
}

// --- il viaggio di Transit ------------------------------------------------------------------------------------------

/** Le tappe del viaggio: due minuti a piedi, il 12 per otto fermate, un minuto a piedi. */
private const val WalkStart = 2
private const val BusStops = 8
private const val WalkEnd = 1

/**
 * Il viaggio verso casa come lo pubblicherebbe Fluid Transit dal suo servizio: un Live Update diretto (si vede anche
 * dentro l'app), con le tre tappe (a piedi, il bus, a piedi), le fermate come punti, il gettone con l'ora d'arrivo e
 * «Termina». «Come la mamma» mostra lo stesso viaggio seguito da un'altra persona: il titolo cambia, e c'è «Smetti di
 * seguire».
 */
@Composable
private fun TripScene(onBack: () -> Unit) {
  val notify = rememberGalleryNotify()
  val scope = rememberCoroutineScope()
  var step by remember { mutableStateOf(-1) }
  var followed by rememberSaveable { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }
  val events by F8p19Events.last
  val total = WalkStart + BusStops + WalkEnd
  val arrival = remember { LocalTime.now().plusMinutes(18).withSecond(0).withNano(0) }

  fun update(at: Int): FluidLiveUpdate {
    val arriveText = "%d:%02d".format(arrival.hour, arrival.minute)
    val stop = (at - WalkStart).coerceIn(0, BusStops)
    val phase = when {
      at < WalkStart -> "A piedi verso la fermata"
      at < WalkStart + BusStops -> "Sul 12 · fermata $stop di $BusStops"
      else -> "A piedi verso casa"
    }
    return FluidLiveUpdate(
      id = "viaggio",
      kind = FluidLiveKind.Navigation,
      title = if (followed) "Alessio è sul 12" else "Verso casa",
      text = if (followed) "Arriva alle $arriveText" else "$phase · arrivo $arriveText",
      progress = at.toFloat() / total,
      stages = listOf(
        FluidLiveStage(WalkStart, 0xFF8E8E93.toInt()),
        FluidLiveStage(BusStops, 0xFF0A84FF.toInt()),
        FluidLiveStage(WalkEnd, 0xFF8E8E93.toInt()),
      ),
      points = (0..BusStops).map { FluidLivePoint(WalkStart + it) },
      chip = arriveText,
      actions = if (followed) emptyList() else listOf(FluidLiveAction("Termina", { job?.cancel(); notify.liveUpdates.remove("viaggio"); step = -1 })),
      origin = if (followed) FluidLiveOrigin.Followed else FluidLiveOrigin.User,
    )
  }

  fun start() {
    job?.cancel()
    step = 0
    notify.liveUpdates.publish(update(0))
    job = scope.launch {
      while (step < total) {
        delay(4_000L)
        step++
        if (step < total) notify.liveUpdates.publish(update(step))
      }
      notify.liveUpdates.finish("viaggio", FluidLiveOutcome.succeeded(if (followed) "È arrivato" else "Sei arrivato"))
      step = -1
    }
  }

  LaunchedEffect(Unit) { F8p19Events.last.value = null }
  // «Smetti di seguire» dalla notifica: chi segue non vuole più il viaggio, e la scena lo ferma come farebbe Transit.
  val unfollowed = F8p19Events.unfollowed.intValue
  val firstUnfollowed = remember { unfollowed }
  LaunchedEffect(unfollowed) {
    if (unfollowed != firstUnfollowed) {
      job?.cancel()
      notify.liveUpdates.remove("viaggio")
      step = -1
    }
  }
  FluidScreen(title = "Viaggio", onBack = onBack, toolbar = { title(FluidToolbarTitleMode.Inline) }) {
    item(key = "permesso") { PermissionRow(notify) }
    item(key = "comandi") {
      Column {
        FluidSectionHeader("Il viaggio")
        FluidListGroup {
          CommandRow("Avvia il viaggio", "Una fermata ogni 4 secondi; il Live Update si vede anche qui dentro", F8p19Tags.TripStart) { start() }
          FluidListDivider()
          CommandRow(
            if (followed) "Come Alessio" else "Come la mamma",
            if (followed) "Torna al viaggio di chi lo fa" else "Lo stesso viaggio seguito con «Avvisa quando arrivo»",
            F8p19Tags.TripFollowed,
          ) {
            followed = !followed
            if (step >= 0) notify.liveUpdates.publish(update(step))
          }
        }
      }
    }
    item(key = "stato") {
      FluidListGroup {
        FluidListRow(
          title = if (step < 0) "Fermo" else "Tappa ${step + 1} di $total",
          subtitle = events ?: "Su Android 16 la barra di stato mostra il gettone con l'ora d'arrivo",
          modifier = Modifier.testTag(F8p19Tags.TripState),
          disclosure = false,
        )
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Le regole di Android: un Live Update solo per ciò che l'utente ha avviato o ha scelto di seguire. Per questo la " +
          "mamma lo vede solo se ha scelto di seguire Alessio, e ha sempre «Smetti di seguire».",
      )
    }
  }
}

// --- tre voti in raffica ----------------------------------------------------------------------------------------------

@Composable
private fun GroupedScene(onBack: () -> Unit) {
  val notify = rememberGalleryNotify()
  val events by F8p19Events.last
  var counter by rememberSaveable { mutableStateOf(0) }
  val subjects = listOf("Matematica" to "8", "Storia" to "7", "Inglese" to "9", "Fisica" to "6½", "Latino" to "7½")
  LaunchedEffect(Unit) { F8p19Events.last.value = null }
  FluidScreen(title = "Notifiche", onBack = onBack, toolbar = { title(FluidToolbarTitleMode.Inline) }) {
    item(key = "permesso") { PermissionRow(notify) }
    item(key = "comandi") {
      Column {
        FluidSectionHeader("ClasseViva e l'account")
        FluidListGroup {
          CommandRow("Arrivano tre voti", "Una sveglia sola, poi «3 nuovi voti»", F8p19Tags.Grades) {
            repeat(3) {
              val (subject, grade) = subjects[(counter + it) % subjects.size]
              notify.post(
                FluidNotifyMessage("voto-${counter + it}", F8p19Channels.Voti.id, "$subject: $grade", "Verifica scritta", thread = "voti"),
              )
            }
            counter += 3
          }
          FluidListDivider()
          CommandRow("Alessio ti condivide una lista", "Con «Rispondi» dalla notifica", F8p19Tags.Share) {
            notify.post(
              FluidNotifyMessage(
                "lista", F8p19Channels.DaPersone.id, "Alessio ti ha condiviso una lista", "Spesa per sabato · 6 cose",
                person = "Alessio", reply = FluidNotifyReply(hint = "Scrivi ad Alessio", choices = listOf("Grazie!", "Ci penso io")),
              ),
            )
          }
          FluidListDivider()
          CommandRow("Una circolare", "Se l'hai messa nel riepilogo, aspetta l'orario", "f8p19:circolare") {
            counter++
            notify.post(FluidNotifyMessage("circ-$counter", F8p19Channels.Circolari.id, "Circolare ${40 + counter}", "Uscita anticipata venerdì", thread = "circolari"))
          }
          FluidListDivider()
          CommandRow("Togli tutte", "Le notifiche della scena", F8p19Tags.Clear) {
            notify.cancelThread("voti")
            notify.cancelThread("circolari")
            notify.cancel("lista")
          }
        }
      }
    }
    item(key = "eventi") {
      FluidListGroup {
        FluidListRow(
          title = "Dalla notifica",
          subtitle = events ?: "Ancora niente: rispondi ad Alessio o tocca un tasto",
          modifier = Modifier.testTag(F8p19Tags.Events),
          disclosure = false,
        )
      }
    }
  }
}

// --- un promemoria fra un minuto --------------------------------------------------------------------------------------

@Composable
private fun ReminderScene(onBack: () -> Unit) {
  val notify = rememberGalleryNotify()
  var sub by rememberSaveable { mutableStateOf(false) }
  BackHandler(enabled = sub) { sub = false }
  if (sub) {
    FluidUpcomingRemindersPage(center = notify.center, onBack = { sub = false })
    return
  }
  val health by notify.center.health.collectAsStateWithLifecycle()
  val exact = health.checks.firstOrNull { it.id == "exact" }
  FluidScreen(title = "Promemoria", onBack = onBack, toolbar = { title(FluidToolbarTitleMode.Inline) }) {
    item(key = "permesso") { PermissionRow(notify) }
    item(key = "comandi") {
      Column {
        FluidSectionHeader("Un promemoria")
        FluidListGroup {
          CommandRow("Ricordamelo fra un minuto", "«Il tè è pronto»: blocca il telefono e aspetta", F8p19Tags.Remind) {
            notify.reminders.schedule(
              "galleria.te", FluidReminderTime.at(System.currentTimeMillis() + 60_000L),
              FluidReminderContent("Il tè è pronto", "Togli la bustina"),
            )
          }
          FluidListDivider()
          CommandRow("Ogni mattina alle 8:00", "Resta alle 8:00 anche cambiando fuso o con l'ora legale", F8p19Tags.RemindDaily) {
            notify.reminders.schedule(
              "galleria.mattina", FluidReminderTime.local(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 0))),
              FluidReminderContent("Buongiorno", "Controlla lo zaino"),
              repeat = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily),
            )
          }
          FluidListDivider()
          FluidListRow(
            title = "Cosa succederà", subtitle = "I promemoria in programma e quelli arrivati, col ritardo vero",
            modifier = Modifier.testTag(F8p19Tags.Upcoming), onClick = { sub = true },
          )
        }
      }
    }
    if (exact != null && !exact.ok) {
      item(key = "esatto") {
        FluidListGroup {
          FluidListRow(
            title = exact.title, subtitle = exact.detail, tone = FluidTone.Warning,
            onClick = { notify.center.open(FluidNotifyFix.ExactAlarms) },
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Il promemoria si salva e sopravvive a riavvio, aggiornamento e cambio d'ora. Con il permesso «Sveglie e promemoria» " +
          "arriva al minuto; senza, entro dieci minuti.",
      )
    }
  }
}

// --- le pagine --------------------------------------------------------------------------------------------------------

@Composable
private fun SettingsScene(onBack: () -> Unit) {
  val notify = rememberGalleryNotify()
  var sub by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = sub != null) { sub = null }
  when (sub) {
    "programma" -> FluidUpcomingRemindersPage(center = notify.center, onBack = { sub = null })
    "controlli" -> FluidNotificationHealthPage(center = notify.center, onBack = { sub = null })
    else -> FluidNotificationSettingsPage(
      center = notify.center, onBack = onBack,
      onOpenUpcoming = { sub = "programma" }, onOpenHealth = { sub = "controlli" },
    )
  }
}

@Composable
private fun HealthScene(onBack: () -> Unit) {
  val notify = rememberGalleryNotify()
  FluidNotificationHealthPage(center = notify.center, onBack = onBack)
}
