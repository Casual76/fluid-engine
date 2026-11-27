<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/notify.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-notify

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-notify/notify/FluidLivePlan.kt`

- `class FluidNotifyGeneration private constructor(val name: String, val firstSdk: Int)` — Le tre generazioni di Android per le notifiche di un'attività, come le chiama il piano. Non è un `enum` (regola 3.11).
  - `companion object`
    - `val Classic: FluidNotifyGeneration` — Android 8–12.
    - `val Permission: FluidNotifyGeneration` — Android 13–15.
    - `val Live: FluidNotifyGeneration` — Android 16 e oltre.
    - `fun of(sdk: Int): FluidNotifyGeneration` — La generazione di [sdk].

## `engine-notify/notify/FluidLiveUpdates.kt`

- `class FluidLiveUpdates internal constructor(private val notify: FluidNotify) : FluidLiveUpdateSink` — Le attività vive dell'app fuori dall'app: il pubblicatore delle notifiche vive (Live Update su Android 16, notifica con l'avanzamento prima). Si prende con `FluidNotify.get(context).liveUpdates`.
  - `val active: StateFlow<List<FluidLiveUpdate>>` — Le attività in corso fuori dall'app (dirette e specchiate), nell'ordine d'arrivo.
  - `val whenOutside: FluidLiveUpdateSink` — Lo specchio della pillola: le attività pubblicate qui si vedono come notifica **solo quando l'app non è in primo piano**. Si passa a `FluidActivityHostState.mirrorOutside` di engine-ui.
  - `fun foreground(update: FluidLiveUpdate, notificationId: Int): Notification` — La notifica di [update] per un **servizio in primo piano**: da passare a `startForeground(notificationId, …)`. Da qui in poi [publish], [finish] e [remove] con lo stesso `id` aggiornano quella notifica (la stessa coppia `null`, [notificationId]). Alla fine chiama [finish] e poi `stopForeground(STOP_FOREGROUND_REMOVE)`: l'esito sta in una notifica a parte, che resta.
  - `fun canPromote(): Boolean` — Vero se Android 16 lascia promuovere le notifiche dell'app (l'utente non ha spento i Live Update).
  - `fun build(update: FluidLiveUpdate, outcome: FluidLiveOutcome? = null): Notification?` — La notifica che [update] avrebbe adesso, senza pubblicarla: per un'app che la vuole mostrare a modo suo. Null per un'attività finita in silenzio.

## `engine-notify/notify/FluidNotify.kt`

- `class FluidNotifyResult private constructor(val name: String)` — Come è andata una pubblicazione. Non è un `enum` (regola 3.11).
  - `val posted: Boolean get()` — Vero se la notifica è (o sarà, al turno della porta del ritmo) a schermo.
  - `companion object`
    - `val Posted: FluidNotifyResult` — Pubblicata, o in fila per il ritmo (al più quattro al secondo per l'app intera). La fila sta in memoria: se il processo muore prima del suo turno (di solito meno di un secondo; con molte notifiche insieme qualche secondo), la notifica non parte.
    - `val InDigest: FluidNotifyResult` — Messa da parte per il prossimo riepilogo, come ha scelto l'utente.
    - `val NotificationsOff: FluidNotifyResult` — Non mostrata: le notifiche dell'app sono spente (il permesso negato, o spente dall'utente).
    - `val ChannelOff: FluidNotifyResult` — Non mostrata: il canale è spento nelle impostazioni di Android, oppure non è dichiarato in `FluidNotifyConfig.channels` (per esempio un canale tolto in un aggiornamento dell'app, arrivato da una notifica push): l'app non cade.
- `class FluidNotifyConfig(val channels: List<FluidNotifyChannel> = emptyList(), val smallIcon: Int = 0, val color: Int? = null, val labels: FluidNotifyLabels = FluidNotifyLabels(), val onEvent: (FluidNotifyEvent) -> Unit = {}, val launchActivity: Class<out Activity>? = null)` — Come l'app usa engine-notify: i suoi canali, l'icona, le parole e il gestore dei tocchi. Si passa a [FluidNotify.install] dall'`Application.onCreate`, così un tocco che fa ripartire l'app trova il gestore.
- `class FluidNotify private constructor(context: Context, config: FluidNotifyConfig)` — Le notifiche dell'app: il punto d'ingresso di engine-notify. Uno per processo: [install] dall'`Application`, poi [get].
  - `val context: Context` — Il contesto dell'applicazione.
  - `var config: FluidNotifyConfig` — La configurazione di adesso.
  - `val liveUpdates: FluidLiveUpdates` — Le attività vive fuori dall'app.
  - `val reminders: FluidReminders` — I promemoria.
  - `val center: FluidNotifyCenter get()` — Il centro delle notifiche per le pagine di engine-ui (impostazioni, «Cosa succederà», «Le notifiche non arrivano?»).
  - `val channels: List<FluidNotifyChannel>` — Tutti i canali: quelli dell'app, poi i tre dell'engine (attività, promemoria, riepilogo).
  - `fun channel(id: String): FluidNotifyChannel?` — Il canale [id], o null.
  - `fun ensureChannels()` — Crea (o aggiorna nome e descrizione di) tutti i canali e i loro gruppi. Android usa l'importanza solo alla prima creazione: dopo decide l'utente, e l'engine non la cambia mai.
  - `fun notificationsAllowed(): Boolean` — Vero se le notifiche dell'app sono accese (da Android 13: il permesso è concesso).
  - `fun channelAllowed(channelId: String): Boolean` — Vero se il canale [channelId] è acceso. Un canale dichiarato che in Android non c'è (cancellato, o mai creato) si ricrea: senza, Android scarterebbe la notifica in silenzio (R2). Un canale che non c'è e non è dichiarato: falso.
  - `fun canPost(channelId: String): Boolean` — Vero se una notifica sul canale [channelId] si può mostrare adesso.
  - `fun post(message: FluidNotifyMessage): FluidNotifyResult` — Pubblica [message], o la mette nel riepilogo se l'utente l'ha chiesto per il suo canale (e non è urgente). Le notifiche dello stesso filo si raccolgono; quelle che arrivano in raffica dopo la prima arrivano in silenzio.
  - `fun cancel(messageId: String)` — Toglie la notifica [messageId] (e ricalcola il riassunto del suo filo).
  - `fun cancelThread(thread: String)` — Toglie tutte le notifiche del filo [thread], anche quelle che aspettano il riepilogo.
  - `fun badgeCount(): Int` — Quante notifiche dell'app contano nel pallino dell'icona: quelle a schermo dei canali col pallino, messaggi e promemoria (i riassunti e le attività in corso no).
  - `fun handleIntent(intent: Intent?): Boolean` — Un'attività dell'app aperta da una notifica (un tasto che apre l'app, `FluidNotifyAction.opensApp`) porta l'evento nell'`Intent`: l'attività lo passa qui da `onCreate` e `onNewIntent`. Vero se c'era un evento.
  - `companion object`
    - `fun install(context: Context, config: FluidNotifyConfig): FluidNotify` — Installa engine-notify con [config] (dall'`Application.onCreate`): crea i canali, rimette promemoria e riepilogo, e comincia a seguire l'app dentro e fuori. Una seconda chiamata cambia la configurazione.
    - `fun get(context: Context): FluidNotify` — engine-notify dell'app; se l'app non l'ha installato, con la configurazione di serie. Un tocco arrivato prima dell'[install] (un processo fatto ripartire da una notifica, con l'install in un'attività) aspetta l'install: il suo evento arriva allora al gestore dell'app, invece di perdersi.

## `engine-notify/notify/FluidNotifyChannel.kt`

- `class FluidNotifyImportance private constructor(val name: String, val androidImportance: Int, internal val priority: Int)` — Quanto una notifica interrompe. Non è un `enum` (regola 3.11 del piano).
  - `companion object`
    - `val Urgent: FluidNotifyImportance` — Compare in alto e suona: un voto nuovo, un promemoria, una persona che scrive.
    - `val Normal: FluidNotifyImportance` — Suona, senza comparire in alto.
    - `val Quiet: FluidNotifyImportance` — In silenzio, con l'icona nella barra di stato: le attività in corso, gli aggiornamenti.
    - `val Silent: FluidNotifyImportance` — In silenzio e senza icona nella barra di stato: solo nel centro notifiche. Mai per un Live Update.
- `class FluidNotifyChannelGroup(val id: String, val name: String)` — Un gruppo di canali: una sezione delle impostazioni («Scuola», «Persone»).
- `class FluidNotifyChannel(val id: String, val name: String, val description: String, val importance: FluidNotifyImportance = FluidNotifyImportance.Normal, val group: FluidNotifyChannelGroup? = null, val summary: (count: Int) -> String = { count -> "$count notifiche" }, val digestible: Boolean = importance != FluidNotifyImportance.Urgent, val badge: Boolean = true, val category: String? = null)` — Un canale delle notifiche dell'app, con parole comuni.
  - `companion object`
    - `const val LiveId: String` — L'id del canale dell'engine per le attività in corso (Live Update e notifiche con l'avanzamento).
    - `const val RemindersId: String` — L'id del canale dell'engine per i promemoria.
    - `const val DigestId: String` — L'id del canale dell'engine per il riepilogo.

## `engine-notify/notify/FluidNotifyLabels.kt`

- `data class FluidNotifyLabels(val liveChannel: String = "Attività in corso", val liveChannelDescription: String = "Download, conversioni, timer e viaggi mentre sei fuori dall'app", val remindersChannel: String = "Promemoria", val remindersChannelDescription: String = "I promemoria che hai chiesto, all'ora giusta", val digestChannel: String = "Riepilogo", val digestChannelDescription: String = "Le notifiche raccolte agli orari che hai scelt…, val done: String = "Fatto", val failed: String = "Non riuscito", val paused: String = "In pausa", val meName: String = "Tu", val unfollow: String = "Smetti di seguire", val digestTitle: (count: Int) -> String = { count -> if (count == 1) "Riepilogo · 1 notif…, val percent: (Int) -> String = { "$it%" }, val lateDeviceOff: String = "Il telefono era spento o appena riavviato", val lateInexact: String = "Senza l'allarme esatto Android può spostarlo d…, val lateSystem: String = "Il sistema l'ha ritardato per risparmiare batt…, val checkPermission: String = "Notifiche dell'app", val checkPermissionOk: String = "Accese", val checkPermissionOff: String = "Spente: l'app non può mostrare niente", val checkChannelOff: String = "Spento nelle impostazioni di Android", val checkExact: String = "Promemoria al minuto", val checkExactOk: String = "Permesso concesso: arrivano all'ora giusta", val checkExactOff: String = "Senza il permesso «Sveglie e promemoria» posso…, val checkSuspended: String = "App in sospensione", val checkSuspendedOk: String = "L'app può lavorare in sottofondo", val checkSuspendedSamsung: String = "One UI ha messo l'app a dormire: notifiche e p…, val checkSuspendedOther: String = "Android limita l'app in sottofondo: notifiche…, val checkFocus: String = "Modalità di concentrazione", val checkFocusOk: String = "Nessuna attiva", val checkFocusOn: String = "Non disturbare è attivo: le notifiche arrivano…, val checkLive: String = "Live Update", val checkLiveOk: String = "Accesi: le attività in corso stanno nella barr…, val checkLiveOff: String = "Spenti: le attività in corso restano notifiche…, val fixOpen: String = "Apri le impostazioni", val fixBattery: String = "Apri «Batteria»", val fixAllow: String = "Consenti", val notShownNotificationsOff: String = "Le notifiche erano spente", val latePastWhenScheduled: String = "Era già passato quando l'app l'ha messo in pro…, val checkStorage: String = "Memoria dei promemoria", val checkStorageWriteFailed: String = "Non riesco a salvare promemoria e riepilogo: l…, un riavvio li perde.", val checkStorageUnreadable: String = "Una parte della memoria dei promemoria era rov…)` — Le parole di engine-notify, di serie in italiano ([english] per l'inglese): i nomi dei canali dell'engine, le frasi della fine di un'attività, il riepilogo, i promemoria e i controlli di «Le notifiche non arrivano?». Le parole delle pagine stanno in engine-ui (`FluidNotifyPageLabels`).
  - `companion object`
    - `fun english(): FluidNotifyLabels` — Le parole inglesi.

## `engine-notify/notify/FluidNotifyMessage.kt`

- `class FluidNotifyAction(val label: String, val key: String, val opensApp: Boolean = false)` — Un tasto di una notifica: [label] è la scritta e il nome per TalkBack, [key] arriva all'app nell'evento.
- `class FluidNotifyReply(val label: String = "Rispondi", val hint: String = "Scrivi una risposta", val choices: List<String> = emptyList())` — La risposta rapida (`RemoteInput`): si scrive dalla notifica, senza aprire l'app. Il testo arriva all'app in [FluidNotifyEvent.Reply]; poi la notifica se ne va, come su iOS dopo una risposta dal banner.
- `data class FluidNotifyMessage(val id: String, val channel: String, val title: String, val text: String? = null, val thread: String? = null, val open: String? = null, val actions: List<FluidNotifyAction> = emptyList(), val reply: FluidNotifyReply? = null, val person: String? = null, val timeMillis: Long = System.currentTimeMillis(), val urgent: Boolean? = null, val sensitive: Boolean = false)` — Una notifica dell'app.
- `abstract class FluidNotifyEvent internal constructor()` — Cosa ha fatto l'utente con una notifica dell'engine. Arriva al gestore `FluidNotifyConfig.onEvent`, sul thread principale, anche quando il tocco fa ripartire l'app (il gestore si dichiara nell'`Application`).
  - `class Action(val messageId: String, val key: String) : FluidNotifyEvent()` — Un tasto [key] della notifica [messageId].
  - `class Reply(val messageId: String, val text: String) : FluidNotifyEvent()` — La risposta rapida [text] alla notifica [messageId].
  - `class ReminderDelivered(val reminderId: String, val scheduledMillis: Long, val deliveredMillis: Long) : FluidNotifyEvent()` — Il promemoria [reminderId] è arrivato ed è stato mostrato.
  - `class ReminderAction(val reminderId: String, val key: String) : FluidNotifyEvent()` — Il tasto [key] del promemoria [reminderId].
  - `class Unfollowed(val liveId: String) : FluidNotifyEvent()` — L'utente ha smesso di seguire l'attività [liveId] di un'altra persona («Smetti di seguire»).
  - `class LiveDismissed(val liveId: String) : FluidNotifyEvent()` — L'utente ha tolto la notifica dell'attività [liveId]: l'engine non la ripropone finché l'attività dura.

## `engine-notify/notify/FluidNotifyReceivers.kt`

- `class FluidNotifyReceiver : BroadcastReceiver()` — I tocchi sulle notifiche, i promemoria che scadono e il riepilogo.
- `class FluidNotifyRestoreReceiver : BroadcastReceiver()` — Il ripristino: dopo un riavvio (gli allarmi di Android si cancellano allo spegnimento), un aggiornamento dell'app, un cambio d'ora o di fuso e un cambio del permesso degli allarmi esatti, rimette promemoria e riepilogo. Un promemoria scaduto mentre il telefono era spento arriva subito, segnato «in ritardo» nel registro.

## `engine-notify/notify/FluidReminders.kt`

- `class FluidReminderTime private constructor(internal val epochMillis: Long?, internal val wall: LocalDateTime?)` — Quando suona un promemoria. Non è un `enum` né una classe `sealed` (regola 3.11).
  - `fun instantMillis(zone: ZoneId): Long` — L'istante in cui cade, nella [zone] di adesso.
  - `companion object`
    - `fun at(epochMillis: Long): FluidReminderTime` — Un istante, in millisecondi dall'epoca.
    - `fun at(instant: Instant): FluidReminderTime` — Un istante.
    - `fun local(dateTime: LocalDateTime): FluidReminderTime` — Un'ora sull'orologio, nel fuso del telefono quando arriva.
- `data class FluidReminderContent(val title: String, val text: String? = null, val channel: String? = null, val open: String? = null, val actions: List<FluidNotifyAction> = emptyList(), val thread: String? = null)` — Cosa mostra un promemoria.
- `class FluidReminders internal constructor(private val notify: FluidNotify)` — I promemoria dell'app: [schedule], [cancel], [upcoming], e il registro di quelli arrivati ([deliveries]). Si prende con [FluidReminders.get] (o `FluidNotify.get(context).reminders`).
  - `fun schedule(id: String, at: FluidReminderTime, content: FluidReminderContent, repeat: FluidRecurrenceRule? = null, exact: Boolean = true, alarm: Boolean = false)` — Mette in programma il promemoria [id] a [at] con [content]. Lo stesso `id` sostituisce quello di prima.
  - `fun cancel(id: String)` — Toglie il promemoria [id] dal programma (e la sua notifica, se è a schermo).
  - `fun upcoming(): List<FluidReminderInfo>` — I promemoria in programma, dal più vicino.
  - `fun deliveries(): List<FluidReminderDelivery>` — I promemoria arrivati, dal più recente.
  - `fun canScheduleExact(): Boolean` — Vero se l'allarme esatto è concesso (sempre prima di Android 12).
  - `fun restore(afterBoot: Boolean = false)` — Rimette tutti gli allarmi: dopo un riavvio, un aggiornamento dell'app, un cambio d'ora o di fuso, un cambio del permesso degli allarmi esatti (lo fa il ricevitore dell'engine), e all'avvio dell'app. Un promemoria scaduto nel frattempo arriva subito, segnato in ritardo.
  - `companion object`
    - `fun get(context: Context): FluidReminders` — I promemoria dell'app.

## `engine-notify/notify/testing/FluidNotifyTesting.kt`

- `object FluidNotifyTesting` — Per le prove delle app (e della galleria) con engine-notify: un processo nuovo a ogni prova, l'app spostata dentro e fuori dal primo piano a mano, il conto delle pubblicazioni. Non serve in un'app vera.
  - `fun reset()` — Dimentica l'istanza installata: la prova dopo parte come un processo nuovo.
  - `fun setOutside(notify: FluidNotify, outside: Boolean)` — Sposta l'app fuori ([outside] vero) o dentro il primo piano, senza aspettare le attività.
  - `fun postCount(notify: FluidNotify): Int` — Quante notifiche ha pubblicato l'engine dall'installazione (aggiornamenti compresi).
