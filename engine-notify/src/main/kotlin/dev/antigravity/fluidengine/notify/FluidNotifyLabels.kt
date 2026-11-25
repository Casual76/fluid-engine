package dev.antigravity.fluidengine.notify

/**
 * Le parole di engine-notify, di serie in italiano ([english] per l'inglese): i nomi dei canali dell'engine, le frasi
 * della fine di un'attività, il riepilogo, i promemoria e i controlli di «Le notifiche non arrivano?». Le parole delle
 * pagine stanno in engine-ui (`FluidNotifyPageLabels`).
 */
data class FluidNotifyLabels(
  val liveChannel: String = "Attività in corso",
  val liveChannelDescription: String = "Download, conversioni, timer e viaggi mentre sei fuori dall'app",
  val remindersChannel: String = "Promemoria",
  val remindersChannelDescription: String = "I promemoria che hai chiesto, all'ora giusta",
  val digestChannel: String = "Riepilogo",
  val digestChannelDescription: String = "Le notifiche raccolte agli orari che hai scelto",
  val done: String = "Fatto",
  val failed: String = "Non riuscito",
  val paused: String = "In pausa",
  /** Il nome dell'utente in una conversazione (`MessagingStyle`). */
  val meName: String = "Tu",
  /** Il tasto che toglie un'attività seguita di un'altra persona (le linee guida di Android chiedono «Unpin»). */
  val unfollow: String = "Smetti di seguire",
  /** Il titolo del riepilogo: «Riepilogo · 5 notifiche». */
  val digestTitle: (count: Int) -> String = { count -> if (count == 1) "Riepilogo · 1 notifica" else "Riepilogo · $count notifiche" },
  val percent: (Int) -> String = { "$it%" },
  /** Perché un promemoria è arrivato tardi. */
  val lateDeviceOff: String = "Il telefono era spento o appena riavviato",
  val lateInexact: String = "Senza l'allarme esatto Android può spostarlo di qualche minuto",
  val lateSystem: String = "Il sistema l'ha ritardato per risparmiare batteria",
  // I controlli di «Le notifiche non arrivano?»
  val checkPermission: String = "Notifiche dell'app",
  val checkPermissionOk: String = "Accese",
  val checkPermissionOff: String = "Spente: l'app non può mostrare niente",
  val checkChannelOff: String = "Spento nelle impostazioni di Android",
  val checkExact: String = "Promemoria al minuto",
  val checkExactOk: String = "Permesso concesso: arrivano all'ora giusta",
  val checkExactOff: String = "Senza il permesso «Sveglie e promemoria» possono arrivare fino a 10 minuti dopo",
  val checkSuspended: String = "App in sospensione",
  val checkSuspendedOk: String = "L'app può lavorare in sottofondo",
  val checkSuspendedSamsung: String =
    "One UI ha messo l'app a dormire: notifiche e promemoria possono arrivare tardi o non arrivare. Apri «Batteria» e scegli «Senza restrizioni».",
  val checkSuspendedOther: String =
    "Android limita l'app in sottofondo: notifiche e promemoria possono arrivare tardi. Apri «Batteria» e scegli «Senza restrizioni».",
  val checkFocus: String = "Modalità di concentrazione",
  val checkFocusOk: String = "Nessuna attiva",
  val checkFocusOn: String = "Non disturbare è attivo: le notifiche arrivano in silenzio",
  val checkLive: String = "Live Update",
  val checkLiveOk: String = "Accesi: le attività in corso stanno nella barra di stato",
  val checkLiveOff: String = "Spenti: le attività in corso restano notifiche normali",
  val fixOpen: String = "Apri le impostazioni",
  val fixBattery: String = "Apri «Batteria»",
  val fixAllow: String = "Consenti",
) {
  companion object {
    /** Le parole inglesi. */
    fun english(): FluidNotifyLabels = FluidNotifyLabels(
      liveChannel = "Ongoing activities",
      liveChannelDescription = "Downloads, conversions, timers and trips while you are out of the app",
      remindersChannel = "Reminders",
      remindersChannelDescription = "The reminders you asked for, on time",
      digestChannel = "Summary",
      digestChannelDescription = "Notifications collected at the times you chose",
      done = "Done", failed = "Failed", paused = "Paused", meName = "You", unfollow = "Stop following",
      digestTitle = { count -> if (count == 1) "Summary · 1 notification" else "Summary · $count notifications" },
      lateDeviceOff = "The phone was off or had just restarted",
      lateInexact = "Without exact alarms Android may move it by a few minutes",
      lateSystem = "The system delayed it to save battery",
      checkPermission = "App notifications", checkPermissionOk = "On",
      checkPermissionOff = "Off: the app can't show anything",
      checkChannelOff = "Turned off in Android settings",
      checkExact = "Reminders on the minute", checkExactOk = "Allowed: they arrive on time",
      checkExactOff = "Without «Alarms & reminders» they can arrive up to 10 minutes late",
      checkSuspended = "Sleeping app", checkSuspendedOk = "The app can work in the background",
      checkSuspendedSamsung = "One UI put the app to sleep: notifications and reminders may be late or missing. Open Battery and choose «Unrestricted».",
      checkSuspendedOther = "Android restricts the app in the background: notifications and reminders may be late. Open Battery and choose «Unrestricted».",
      checkFocus = "Focus modes", checkFocusOk = "None active",
      checkFocusOn = "Do Not Disturb is on: notifications arrive silently",
      checkLive = "Live Updates", checkLiveOk = "On: ongoing activities stay in the status bar",
      checkLiveOff = "Off: ongoing activities stay ordinary notifications",
      fixOpen = "Open settings", fixBattery = "Open Battery", fixAllow = "Allow",
    )
  }
}
