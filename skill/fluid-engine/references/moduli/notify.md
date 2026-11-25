# engine-notify

Notifiche, promemoria e attività fuori dall'app (F8.19, idee `attivita-fuori-dall-app`, `notifiche-di-sistema`,
`promemoria-affidabili`). Pacchetto `dev.antigravity.fluidengine.notify`. **Opt-in**, senza interfaccia Compose, sopra
`engine-foundation` e `androidx.core` (niente WorkManager: riepilogo e promemoria passano da `AlarmManager`). Le pagine
(impostazioni delle notifiche, «Cosa succederà», «Le notifiche non arrivano?») e il ponte con la pillola di attività stanno
in `engine-ui`; i due moduli si parlano coi tipi puri di `engine-foundation` (`foundation.live`, `foundation.notify`).

Le notifiche **dentro** l'app (la card che scende) sono un'altra cosa: `FluidNotificationHost`, scheda
[notifiche](../componenti/notifiche.md). Qui ci sono le notifiche **vere** di Android.

Riferimenti Apple: ActivityKit (Live Activity e Dynamic Island), il livello d'interruzione di `UNNotificationContent`, il
Riepilogo programmato e le Full immersion, `UNCalendarNotificationTrigger`.

Catalogo completo delle firme: [`../api/notify.md`](../api/notify.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| installare il modulo (canali, gestore dei tocchi) | `FluidNotify.install(this, FluidNotifyConfig(channels = …, onEvent = …))` nell'`Application.onCreate` |
| un canale con parole comuni | `FluidNotifyChannel("voti", "Voti nuovi", "Quando la scuola pubblica un voto", FluidNotifyImportance.Urgent, summary = { "$it nuovi voti" })` |
| una notifica | `FluidNotify.get(context).post(FluidNotifyMessage(id, canale, titolo, testo))` |
| raccogliere più notifiche («3 nuovi voti» invece di tre sveglie) | lo stesso `thread`: suona la prima, le altre della raffica arrivano in silenzio sotto il riassunto |
| la risposta rapida | `reply = FluidNotifyReply(hint = "Scrivi ad Alessio")`; il testo arriva in `FluidNotifyEvent.Reply` |
| una notifica da una persona («Alessio ti ha condiviso una lista») | `person = "Alessio"` (diventa una conversazione, `MessagingStyle`) sul canale delle persone (`category = CATEGORY_MESSAGE`) |
| il riepilogo a orari scelti | lo accende l'utente nella pagina delle impostazioni: **di serie tutto arriva subito** |
| un'attività lunga che continua fuori dall'app (la pillola) | `activities.mirrorOutside(FluidNotify.get(this).liveUpdates.whenOutside, MainScope())` di engine-ui, una volta; poi solo la pillola |
| il Live Update di un servizio in primo piano (Transit) | `startForeground(id, live.foreground(update, id))`, poi `live.publish(update)` e `live.finish(id, esito)` |
| un promemoria | `FluidReminders.get(context).schedule(id, FluidReminderTime.local(…), FluidReminderContent(…))` |
| un promemoria ogni giorno | `repeat = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)` (la regola di F8.8) |
| la pagina delle impostazioni, «Cosa succederà», «Le notifiche non arrivano?» | `FluidNotificationSettingsPage`, `FluidUpcomingRemindersPage`, `FluidNotificationHealthPage` di engine-ui con `FluidNotify.get(context).center` |
| chiedere il permesso delle notifiche | dal tasto che ne ha bisogno: `rememberFluidPermission(FluidPermission.Notifications, …)` (F8.11); le pagine lo fanno da sole |
| provare | `testing.FluidNotifyTesting` (`reset`, `setOutside`, `postCount`) |

## Installare e i permessi

```kotlin
class App : Application() {
  val activities = FluidActivityHostState()

  override fun onCreate() {
    super.onCreate()
    FluidNotify.install(this, FluidNotifyConfig(channels = listOf(Voti, Circolari, DaPersone), smallIcon = R.drawable.ic_stat, onEvent = ::onNotify))
    activities.mirrorOutside(FluidNotify.get(this).liveUpdates.whenOutside, MainScope())
  }
}
```

`install` va fatto nell'`Application`: un tocco su un tasto o un promemoria che scade possono far ripartire l'app, e il
gestore deve esserci. Crea i canali, rimette promemoria e riepilogo, e comincia a seguire l'app dentro e fuori.

Il manifest del modulo porta solo permessi **normali**: `RECEIVE_BOOT_COMPLETED` (il ricevitore del ripristino è spento
finché non c'è un promemoria) e `POST_PROMOTED_NOTIFICATIONS` (i Live Update; l'utente li spegne nelle impostazioni). L'app
dichiara quelli che l'utente concede, quando li usa:

| permesso | quando | se manca |
|---|---|---|
| `POST_NOTIFICATIONS` | da Android 13, si chiede dal tasto (`rememberFluidPermission`) | niente notifiche; `post` dice `NotificationsOff`; la pagina lo dice |
| `SCHEDULE_EXACT_ALARM` | i promemoria al minuto, da Android 12 (da Android 13 non è concesso di serie) | il promemoria arriva entro 10 minuti; «Cosa succederà» lo dice; «Le notifiche non arrivano?» porta a «Sveglie e promemoria» |
| `USE_EXACT_ALARM` | solo per sveglie e calendari (politica di Google Play) | — |

## Canali e notifiche

**I canali** hanno parole comuni: il nome dice cosa arriva («Voti nuovi»), la descrizione quando. L'importanza
(`FluidNotifyImportance`) vale alla prima creazione; dopo decide l'utente, e l'engine non la cambia mai:

| importanza | Android | come iOS |
|---|---|---|
| `Urgent` | `IMPORTANCE_HIGH`: in alto e suona | time-sensitive |
| `Normal` | `IMPORTANCE_DEFAULT`: suona | active |
| `Quiet` | `IMPORTANCE_LOW`: in silenzio, icona nella barra | passive |
| `Silent` | `IMPORTANCE_MIN`: solo nel centro notifiche (mai per un Live Update) | — |

L'engine aggiunge tre canali suoi: «Attività in corso» (`fluid.live`, in silenzio, senza pallino), «Promemoria»
(`fluid.reminders`, urgente) e «Riepilogo» (`fluid.digest`).

**Raccolte per filo.** Le notifiche con lo stesso `thread` stanno sotto un riassunto («3 nuovi voti», la frase
`summary` del canale) da due in su. Suona la prima; quelle che arrivano entro **2 minuti** dall'ultima che ha suonato
tacciono (`GROUP_ALERT_SUMMARY`) e il riassunto non suona mai (`GROUP_ALERT_CHILDREN`). È l'idea del «notification
cooldown» di Android 15, per filo.

**Il pallino** conta solo le notifiche da leggere dei canali col pallino: le attività in corso no (`badgeCount()`).

**Le modalità di concentrazione** non si scavalcano mai: nessuna richiesta di passare sopra a Non disturbare. Le persone
usano `CATEGORY_MESSAGE` e `MessagingStyle`, così le conversazioni prioritarie dell'utente passano da sole.

**I tasti e la risposta** portano una chiave, non una funzione (una notifica sopravvive al processo): il tocco arriva al
gestore `onEvent` come `FluidNotifyEvent.Action`, `Reply`, `ReminderAction`, `Unfollowed`, `LiveDismissed`, sul thread
principale. Un tasto che apre l'app (`opensApp = true`) porta l'evento nell'`Intent`: l'attività chiama
`FluidNotify.get(this).handleIntent(intent)` da `onCreate` e `onNewIntent`. Dopo una risposta rapida la notifica se ne va.

**Il riepilogo** (`center.setDigest`, `center.setChannelInDigest`) è **spento di serie**. Acceso, le notifiche non urgenti
dei canali scelti aspettano gli orari (di serie 8:00 e 18:00, come il Riepilogo programmato di iOS) e arrivano raccolte,
con un riassunto che suona una volta. Un canale `Urgent`, o una notifica con `urgent = true`, arriva sempre subito.
Spegnerlo consegna subito ciò che aspettava.

## Le attività fuori dall'app (Live Update)

`FluidLiveUpdate` (in `engine-foundation`, `foundation.live`) è la stessa descrizione dentro e fuori: titolo, riga,
avanzamento, tappe e punti, tempo, gettone, pausa, comandi, indirizzo, origine. **Si pubblica una volta.**

**Le tre generazioni** (`FluidNotifyGeneration`):

| Android | cosa diventa |
|---|---|
| 8–12 (`Classic`) | la notifica con la barra (`setProgress`), in corso, senza permesso da chiedere |
| 13–15 (`Permission`) | la stessa, col permesso `POST_NOTIFICATIONS`; da Android 14 l'utente può toglierla anche se è in corso |
| 16 (`Live`) | il Live Update promosso: `Notification.ProgressStyle` (le tappe e le fermate), il gettone nella barra di stato (`setShortCriticalText`, intero sotto i 7 caratteri; o il cronometro), la schermata di blocco |

**Le regole di Android** (developer.android.com, «Live Updates», fiducia alta), rispettate per costruzione: in corso, col
titolo, senza viste su misura, mai il riassunto di un gruppo, uno stile fra standard e `ProgressStyle`, un canale sopra
`IMPORTANCE_MIN`, la promozione chiesta (`setRequestPromotedOngoing`). **Solo ciò che l'utente ha avviato**:
`FluidLiveKind.Sync` non è mai promosso; `FluidLiveOrigin.Others` (avviato da altri) nemmeno; `FluidLiveOrigin.Followed`
(un'attività di un'altra persona che l'utente ha scelto di seguire, «Avvisa quando arrivo») sì, e l'engine aggiunge
«Smetti di seguire». **Una notifica tolta dall'utente non torna** finché l'attività dura (`deleteIntent`), salvo un esito
non riuscito.

**Android 16 ha due regole.** La prima uscita (API 36.0) promuove solo una notifica col colore chiesto
(`hasPromotableCharacteristics`, letto nel codice dell'immagine di Robolectric 16); la QPR1 (36.1) e la documentazione di
oggi vogliono la richiesta esplicita e **niente** colore. L'engine scrive la notifica come vuole la documentazione e chiede
al telefono (`hasPromotableCharacteristics()`); se dice di no, riprova con la regola della prima uscita. Decide il telefono.

**Visto sul Tab S9** (One UI 8.5, Android 16 QPR, 25 novembre 2026). La conversione e il viaggio sono promossi
(`PROMOTED_ONGOING`) senza colore. One UI li mette in cima alla tendina, con la barra a tappe, la freccia che avanza e i
tasti. Il gettone nella barra di stato **non** c'è: One UI mostra solo l'icona dell'attività. Un gruppo («3 nuovi voti»)
One UI lo mostra chiuso con l'ultima notifica e il numero, non col titolo del riassunto.

**Il ritmo.** Android scarta gli aggiornamenti di un'app che ne manda più di 5 al secondo (AOSP,
`DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE`): la barra resterebbe ferma su un valore vecchio. Tutto ciò che l'engine pubblica
passa da una porta sola, **al più 4 al secondo** per l'app intera, e un aggiornamento che aspetta il turno viene sostituito
dal successivo: arriva sempre l'ultimo valore. Un cambio del solo avanzamento aspetta almeno **1 s** dal precedente; un
cambio di stato (la riga, la pausa, le tappe) passa subito. Meno dell'1% non cambia la notifica.

**La fine** (`finish`): la notifica dell'esito («Video pronto», «Non riuscito: rete assente») non è più in corso né
promossa, si chiude al tocco e resta **4 ore**, come una Live Activity finita sulla schermata di blocco. `FluidLiveOutcome.Silent`
la toglie.

**Dentro e fuori.** `liveUpdates` è il modo **diretto** (la notifica si vede sempre: un servizio in primo piano).
`liveUpdates.whenOutside` è lo **specchio della pillola**: la notifica si vede solo quando l'app non è in primo piano
(l'uscita si dichiara dopo 700 ms, come `ProcessLifecycleOwner`, così una rotazione non la fa lampeggiare); al rientro se
ne va e la pillola riprende il posto; l'esito di un'attività finita fuori resta. Il ponte è in engine-ui:
`FluidActivityHostState.mirrorOutside(sink, scope)` (per un host nell'`Application`) o `FluidActivityOutside(state, sink)`
(dentro la composizione). L'avanzamento, che fuori nessuno disegna, lo rilegge lo specchio una volta al secondo **solo
mentre l'app è fuori**.

**I comandi** della notifica (Pausa, Annulla, Termina) chiamano la stessa funzione della pillola finché il processo è
vivo; un tasto di un processo morto toglie la notifica vecchia.

**Un servizio in primo piano** (Fluid Transit):

```kotlin
val live = FluidNotify.get(this).liveUpdates
startForeground(NOTIFICATION_ID, live.foreground(trip(state), NOTIFICATION_ID))
// a ogni fermata, o quando l'arrivo cambia di 2 minuti:
live.publish(trip(state))
// all'arrivo:
live.finish("viaggio", FluidLiveOutcome.succeeded("Sei arrivato"))
stopForeground(STOP_FOREGROUND_REMOVE)

fun trip(s: NavState) = FluidLiveUpdate(
  id = "viaggio", kind = FluidLiveKind.Navigation, title = s.headline, text = "${s.detail} · arrivo ${hm(s.etaEpoch)}",
  progress = s.journeyDone / s.journeyStops.toFloat(),
  stages = s.legs.map { FluidLiveStage(it.stops, it.color) }, points = s.stops.map { FluidLivePoint(it.index) },
  chip = hm(s.etaEpoch), actions = listOf(FluidLiveAction("Termina", ::stop)), open = Deeplink.nav(),
)
```

## I promemoria

`FluidReminders.schedule(id, quando, contenuto)` salva il promemoria (`noBackupFilesDir/fluid_notify.json`), sceglie
l'allarme e lo rimette dopo riavvio, aggiornamento dell'app, cambio d'ora e di fuso e cambio del permesso degli allarmi
esatti (il ricevitore `FluidNotifyRestoreReceiver`, acceso solo finché c'è un promemoria).

| quando | cosa usa |
|---|---|
| allarme esatto concesso (sempre prima di Android 12) | `setExactAndAllowWhileIdle`: al minuto anche a telefono fermo |
| senza | `setWindow` di 10 minuti (Android 12 e oltre allargano a 10 minuti le finestre più strette) |
| `alarm = true` e allarme esatto | `setAlarmClock`: la sveglia che il sistema non sposta mai, con l'icona nella barra |

**L'ora.** `FluidReminderTime.local(…)` è un'ora dell'orologio: resta alle 8:00 anche cambiando fuso o con l'ora legale
(nell'ora che manca va avanti, come `ZonedDateTime.of`), come `UNCalendarNotificationTrigger`. `FluidReminderTime.at(…)`
è un istante. Una ripetizione conta sempre i giorni sull'orologio.

**Il registro.** Ogni promemoria arrivato si segna con l'ora prevista e quella vera, e il perché del ritardo oltre un
minuto: «Il telefono era spento o appena riavviato», «Senza l'allarme esatto Android può spostarlo di qualche minuto», «Il
sistema l'ha ritardato per risparmiare batteria». Gli ultimi 50 sono in `center.deliveries`. Un promemoria scaduto mentre
il telefono era spento arriva subito, segnato in ritardo; di una ripetizione persa per giorni arriva solo la più recente.

## «Le notifiche non arrivano?»

`center.health` controlla, prima ciò che non va: le notifiche dell'app, l'app **messa a dormire** (One UI «App in
sospensione»: il secchio di standby «raro» o «limitato», o le restrizioni in sottofondo), ogni canale spento, il permesso
degli allarmi esatti (se ci sono promemoria), Non disturbare, i Live Update. Ogni riga porta nel punto giusto
(`center.open(fix)`): la pagina del canale, «Sveglie e promemoria», la pagina dell'app con «Batteria» (su One UI «Senza
restrizioni»), i Live Update dell'app, Non disturbare; se una pagina non c'è, quella delle notifiche dell'app o la pagina
dell'app.

## I numeri

| numero | valore | fonte |
|---|---|---|
| pubblicazioni al secondo | 4 | AOSP, `DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE = 5f` meno uno (fiducia media-alta, letto a memoria) |
| passo dell'avanzamento | 1 s | a occhio, 25/11/2026; Apple: «Update a Live Activity only when new content is available» (HIG) |
| raffica di un filo | 2 min | a occhio, 25/11/2026 |
| l'esito resta | 4 h | HIG Live Activities e `ActivityUIDismissalPolicy` (fiducia alta) |
| uscita dall'app | 700 ms | `ProcessLifecycleOwner.TIMEOUT_MS` (fiducia alta) |
| finestra senza allarme esatto | 10 min | developer.android.com, «Schedule alarms» (fiducia alta) |
| ritardo che si segna | 1 min | a occhio, 25/11/2026 |
| orari del riepilogo | 8:00 e 18:00 | iOS, Riepilogo programmato (fiducia alta) |
| gettone intero | < 7 caratteri | developer.android.com, «Live Updates» (fiducia alta) |

## Cosa è verificato dai test

- **JVM** (`FluidLivePlanTest`, `FluidNotifyPureTest`): il piano della notifica per le tre generazioni (barra, tappe,
  punti, gettone, cronometro, pausa, categoria, origini, tre tasti, esito, quattro ore), la porta del ritmo (mai più di 4
  al secondo, l'ultimo valore arriva sempre, «non prima di»), i promemoria nel tempo (ora locale e fuso, istante, ora
  legale, ripetizioni, giorni persi, fine), il riepilogo, la memoria su disco, i controlli.
- **Robolectric, Android 11 e 14** (`FluidNotifyAndroidTest`, `ShadowNotificationManager` e `ShadowAlarmManager`): i
  canali, le due generazioni, il permesso negato, il canale spento, tre voti in raffica con una sveglia sola e il riassunto,
  la risposta rapida, i tasti, il riepilogo, cento aggiornamenti in un secondo (al più tre pubblicazioni, l'ultimo valore),
  dieci secondi di avanzamento, lo specchio dentro e fuori, la notifica tolta, «Smetti di seguire» che non torna anche se
  l'app continua a pubblicare, il tasto della pillola e quello vecchio, il
  servizio in primo piano, il promemoria al minuto, senza allarme esatto, dopo un riavvio, dopo un cambio di fuso, ogni
  giorno, la sveglia, il ripristino acceso e spento, i punti giusti delle impostazioni, l'app sospesa.
- **Robolectric, Android 16** (`FluidNotifyAndroid16Test`, sdk 36 su Java 21): `hasPromotableCharacteristics()` vero per
  trasferimento, viaggio e timer, `ProgressStyle` con tappe e punti, il gettone, il cronometro, niente promozione per la
  sincronizzazione e per chi non ha scelto, «Smetti di seguire», l'esito non più promosso.
- **Galleria** (`F8p19NotifyUiTest`): pillola → notifica → pillola con la pillola vera, le pagine con TalkBack e «Riduci
  movimento», tre voti dalla scena.
