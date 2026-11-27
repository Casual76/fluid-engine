package dev.antigravity.fluidengine.foundation.notify

import kotlinx.coroutines.flow.StateFlow

// F8.19 · Il centro delle notifiche visto dalle pagine: i dati puri che le pagine di engine-ui disegnano (le impostazioni
// delle notifiche, «Cosa succederà», «Le notifiche non arrivano?») e i comandi che mandano. Lo implementa engine-notify
// (`FluidNotify.center`); sta qui perché engine-ui e engine-notify non si conoscono, come `SyncStatus` per engine-sync.

/**
 * Un canale delle notifiche come lo vede l'utente: parole comuni, non nomi tecnici.
 *
 * @property id l'identità del canale di Android.
 * @property name il nome («Voti nuovi»).
 * @property description la frase che dice quando arriva («Quando la scuola pubblica un voto»).
 * @property enabled falso se l'utente l'ha spento nelle impostazioni di Android: l'app non può riaccenderlo, solo portare
 *   lì.
 * @property urgent vero per un canale che arriva sempre subito (non va mai nel riepilogo).
 * @property inDigest vero se l'utente ha scelto di ricevere questo canale nel riepilogo.
 * @property group il nome del gruppo di canali (la sezione della pagina), o null.
 */
data class FluidNotifyChannelInfo(
  val id: String,
  val name: String,
  val description: String,
  val enabled: Boolean = true,
  val urgent: Boolean = false,
  val inDigest: Boolean = false,
  val group: String? = null,
)

/**
 * Il riepilogo a orari scelti (il «Riepilogo programmato» di iOS): **spento di serie**, tutto arriva subito. Lo accende
 * l'utente, e allora i canali che ha scelto arrivano raccolti agli orari [times].
 *
 * @property times i minuti dal mezzanotte, in ordine (di serie 8:00 e 18:00, come il riepilogo di iOS: «By default, the
 *   summary is delivered at 8 AM and 6 PM», support.apple.com, fiducia alta).
 * @property waiting quante notifiche aspettano il prossimo riepilogo.
 */
data class FluidNotifyDigestInfo(
  val enabled: Boolean = false,
  val times: List<Int> = listOf(8 * 60, 18 * 60),
  val waiting: Int = 0,
)

/**
 * Lo stato dei Live Update dell'app (Android 16). Non è un `enum` (regola 3.11).
 */
class FluidNotifyLiveState private constructor(internal val code: Int) {
  /** Vero se il telefono li ha (Android 16). */
  val supported: Boolean get() = code != NotSupportedCode

  /** Vero se l'utente li lascia accesi per l'app. */
  val allowed: Boolean get() = code == AllowedCode

  override fun equals(other: Any?): Boolean = other is FluidNotifyLiveState && other.code == code
  override fun hashCode(): Int = code
  override fun toString(): String = "FluidNotifyLiveState(${listOf("unsupported", "allowed", "off")[code]})"

  companion object {
    private const val NotSupportedCode = 0
    private const val AllowedCode = 1
    private const val OffCode = 2

    /** Prima di Android 16: le attività fuori dall'app sono notifiche con l'avanzamento. */
    val NotSupported: FluidNotifyLiveState = FluidNotifyLiveState(NotSupportedCode)

    /** Accesi. */
    val Allowed: FluidNotifyLiveState = FluidNotifyLiveState(AllowedCode)

    /** Spenti dall'utente nelle impostazioni dell'app. */
    val Off: FluidNotifyLiveState = FluidNotifyLiveState(OffCode)
  }
}

/**
 * Le impostazioni delle notifiche come le disegna la pagina.
 *
 * @property notificationsAllowed falso se le notifiche dell'app sono spente (il permesso negato da Android 13, o spente
 *   dall'utente).
 * @property channels i canali, nell'ordine dell'app.
 * @property digest il riepilogo.
 * @property live i Live Update.
 * @property focusActive vero se è attiva una modalità di concentrazione (Non disturbare): le notifiche arrivano lo stesso,
 *   in silenzio, e l'engine non la scavalca mai.
 */
data class FluidNotifySettings(
  val notificationsAllowed: Boolean = true,
  val channels: List<FluidNotifyChannelInfo> = emptyList(),
  val digest: FluidNotifyDigestInfo = FluidNotifyDigestInfo(),
  val live: FluidNotifyLiveState = FluidNotifyLiveState.NotSupported,
  val focusActive: Boolean = false,
)

/**
 * Un promemoria in programma, per la pagina «Cosa succederà».
 *
 * @property atMillis quando suonerà (l'istante, sull'orologio di parete).
 * @property exact vero se suonerà al minuto (l'allarme esatto è concesso), falso se Android può spostarlo di qualche
 *   minuto.
 * @property repeats la regola di ripetizione in parole, o null.
 */
data class FluidReminderInfo(
  val id: String,
  val title: String,
  val text: String? = null,
  val atMillis: Long,
  val exact: Boolean,
  val repeats: String? = null,
)

/**
 * Un promemoria arrivato: quando doveva e quando è arrivato davvero (il registro dei ritardi). Anche uno scaduto che
 * non si è potuto mostrare ([shown] falso: le notifiche o il canale erano spenti) sta nel registro: è perso, e non
 * arriva più quando le notifiche tornano (la scelta di Alessio del 26/11/2026, R2).
 *
 * @property reason perché è arrivato tardi, o perché non è stato mostrato, se lo si sa («Il telefono era spento»), o null.
 * @property shown falso se all'ora del promemoria non si è potuto mostrare.
 */
data class FluidReminderDelivery(
  val id: String,
  val title: String,
  val scheduledMillis: Long,
  val deliveredMillis: Long,
  val reason: String? = null,
  val shown: Boolean = true,
) {
  /** Il ritardo in millisecondi, mai meno di zero. */
  val delayMillis: Long get() = (deliveredMillis - scheduledMillis).coerceAtLeast(0L)
}

/**
 * Dove porta una correzione della pagina «Le notifiche non arrivano?»: il punto giusto delle impostazioni. Non è un `enum`
 * (regola 3.11).
 */
class FluidNotifyFix private constructor(val name: String, val channelId: String? = null) {
  override fun equals(other: Any?): Boolean = other is FluidNotifyFix && other.name == name && other.channelId == channelId
  override fun hashCode(): Int = 31 * name.hashCode() + (channelId?.hashCode() ?: 0)
  override fun toString(): String = "FluidNotifyFix($name${channelId?.let { ", $it" } ?: ""})"

  companion object {
    /** Le notifiche dell'app (il permesso, o l'interruttore generale). */
    val AppNotifications: FluidNotifyFix = FluidNotifyFix("app-notifications")

    /** Il canale [id]. */
    fun channel(id: String): FluidNotifyFix = FluidNotifyFix("channel", id)

    /** Sveglie e promemoria (l'allarme esatto, da Android 12). */
    val ExactAlarms: FluidNotifyFix = FluidNotifyFix("exact-alarms")

    /** La batteria dell'app: «Senza restrizioni», così One UI non la sospende. */
    val Battery: FluidNotifyFix = FluidNotifyFix("battery")

    /** I Live Update dell'app (Android 16). */
    val LiveUpdates: FluidNotifyFix = FluidNotifyFix("live-updates")

    /** Le modalità di concentrazione (Non disturbare). */
    val Focus: FluidNotifyFix = FluidNotifyFix("focus")
  }
}

/**
 * Un controllo della pagina «Le notifiche non arrivano?»: una riga con l'esito e, se qualcosa non va, la correzione.
 *
 * @property id l'identità del controllo («permission», «channel:voti», «exact», «suspended», …).
 * @property ok vero se va bene.
 * @property fix dove porta il tasto della correzione, o null.
 * @property fixLabel la scritta del tasto («Apri le impostazioni»).
 */
data class FluidNotifyCheck(
  val id: String,
  val title: String,
  val detail: String,
  val ok: Boolean,
  val fix: FluidNotifyFix? = null,
  val fixLabel: String? = null,
)

/**
 * L'esito dei controlli: [checks] in ordine d'importanza, [suspended] vero se il sistema ha messo l'app a dormire (One UI
 * «App in sospensione», o il secchio «limitato» di Android).
 */
data class FluidNotifyHealth(
  val checks: List<FluidNotifyCheck> = emptyList(),
  val suspended: Boolean = false,
) {
  /** Vero se tutti i controlli vanno bene. */
  val allGood: Boolean get() = checks.all { it.ok }
}

/**
 * Il centro delle notifiche dell'app, come lo leggono e lo comandano le pagine di engine-ui. Lo dà `engine-notify`
 * (`FluidNotify.center`); per le prove e le anteprime basta un'implementazione con dati fissi.
 */
interface FluidNotifyCenter {
  /** Le impostazioni. */
  val settings: StateFlow<FluidNotifySettings>

  /** I promemoria in programma, dal più vicino. */
  val reminders: StateFlow<List<FluidReminderInfo>>

  /** I promemoria arrivati, dal più recente (gli ultimi 50). */
  val deliveries: StateFlow<List<FluidReminderDelivery>>

  /** I controlli di «Le notifiche non arrivano?». */
  val health: StateFlow<FluidNotifyHealth>

  /** Rilegge tutto da Android: le pagine lo chiamano alla ripresa (al ritorno dalle impostazioni). */
  fun refresh()

  /** Il canale [channelId] nel riepilogo ([inDigest]) o subito. */
  fun setChannelInDigest(channelId: String, inDigest: Boolean)

  /** Accende o spegne il riepilogo, con i suoi orari (minuti dalla mezzanotte). */
  fun setDigest(enabled: Boolean, times: List<Int>)

  /** Toglie il promemoria [id] dal programma. */
  fun cancelReminder(id: String)

  /** Apre il punto giusto delle impostazioni di Android per [fix]. Vero se è riuscito ad aprirlo. */
  fun open(fix: FluidNotifyFix): Boolean
}
