package dev.antigravity.fluidengine.notify

// F8.19 · `notifiche-di-sistema`: una notifica vera di Android, descritta con dati e non con un builder. Le azioni e le
// risposte non portano funzioni (una notifica sopravvive al processo dell'app): portano una chiave, e il tocco arriva
// all'app come [FluidNotifyEvent], al gestore dato in `FluidNotifyConfig.onEvent`.

/**
 * Un tasto di una notifica: [label] è la scritta e il nome per TalkBack, [key] arriva all'app nell'evento.
 *
 * @param opensApp vero se il tasto porta nell'app («Apri», «Rispondi» con l'app), falso se lavora in sottofondo
 *   («Segna come letto»).
 */
class FluidNotifyAction(val label: String, val key: String, val opensApp: Boolean = false) {
  override fun equals(other: Any?): Boolean =
    other is FluidNotifyAction && other.label == label && other.key == key && other.opensApp == opensApp
  override fun hashCode(): Int = 31 * label.hashCode() + key.hashCode()
  override fun toString(): String = "FluidNotifyAction($key)"
}

/**
 * La risposta rapida (`RemoteInput`): si scrive dalla notifica, senza aprire l'app. Il testo arriva all'app in
 * [FluidNotifyEvent.Reply]; poi la notifica se ne va, come su iOS dopo una risposta dal banner.
 *
 * @param label la scritta del tasto («Rispondi»).
 * @param hint il segnaposto del campo («Scrivi ad Alessio»).
 * @param choices le risposte pronte, o vuoto.
 */
class FluidNotifyReply(
  val label: String = "Rispondi",
  val hint: String = "Scrivi una risposta",
  val choices: List<String> = emptyList(),
) {
  override fun equals(other: Any?): Boolean =
    other is FluidNotifyReply && other.label == label && other.hint == hint && other.choices == choices
  override fun hashCode(): Int = 31 * (31 * label.hashCode() + hint.hashCode()) + choices.hashCode()
}

/**
 * Una notifica dell'app.
 *
 * @param id l'identità: la stessa `id` aggiorna la notifica invece di aggiungerne una.
 * @param channel l'id del canale (dichiarato in `FluidNotifyConfig.channels`).
 * @param title il titolo («Matematica: 8»).
 * @param text il testo, o null.
 * @param thread il filo: le notifiche dello stesso filo si raccolgono sotto una sola («3 nuovi voti», con la frase del
 *   canale), e le seguenti di una raffica arrivano in silenzio. Null: da sola.
 * @param open l'indirizzo da aprire al tocco (un indirizzo di pagina di F8.12, `https://…` o lo schema dell'app); di
 *   serie l'app.
 * @param actions i tasti (Android ne mostra tre, la risposta compresa).
 * @param reply la risposta rapida, o null.
 * @param person chi la manda («Alessio»): diventa una conversazione (`MessagingStyle`), e le modalità di concentrazione
 *   possono lasciarla passare se l'utente ha scelto quella persona.
 * @param timeMillis l'ora dell'evento (sull'orologio di parete).
 * @param urgent vero per farla arrivare subito anche col riepilogo acceso; null lo lascia al canale.
 * @param sensitive vero per nasconderne il testo a telefono bloccato.
 */
data class FluidNotifyMessage(
  val id: String,
  val channel: String,
  val title: String,
  val text: String? = null,
  val thread: String? = null,
  val open: String? = null,
  val actions: List<FluidNotifyAction> = emptyList(),
  val reply: FluidNotifyReply? = null,
  val person: String? = null,
  val timeMillis: Long = System.currentTimeMillis(),
  val urgent: Boolean? = null,
  val sensitive: Boolean = false,
) {
  init {
    require(id.isNotBlank()) { "una notifica ha un id" }
    require(title.isNotBlank()) { "una notifica ha un titolo" }
  }
}

/**
 * Cosa ha fatto l'utente con una notifica dell'engine. Arriva al gestore `FluidNotifyConfig.onEvent`, sul thread
 * principale, anche quando il tocco fa ripartire l'app (il gestore si dichiara nell'`Application`).
 *
 * Non è una classe `sealed` di proposito (regola 3.11): gli eventi nuovi non romperanno i `when` delle app, che finiscono
 * con un `else`.
 */
abstract class FluidNotifyEvent internal constructor() {
  /** Un tasto [key] della notifica [messageId]. */
  class Action(val messageId: String, val key: String) : FluidNotifyEvent() {
    override fun toString(): String = "Action($messageId, $key)"
  }

  /** La risposta rapida [text] alla notifica [messageId]. */
  class Reply(val messageId: String, val text: String) : FluidNotifyEvent() {
    override fun toString(): String = "Reply($messageId, ${text.length} caratteri)"
  }

  /** Il promemoria [reminderId] è arrivato ed è stato mostrato. */
  class ReminderDelivered(val reminderId: String, val scheduledMillis: Long, val deliveredMillis: Long) : FluidNotifyEvent() {
    override fun toString(): String = "ReminderDelivered($reminderId)"
  }

  /** Il tasto [key] del promemoria [reminderId]. */
  class ReminderAction(val reminderId: String, val key: String) : FluidNotifyEvent() {
    override fun toString(): String = "ReminderAction($reminderId, $key)"
  }

  /** L'utente ha smesso di seguire l'attività [liveId] di un'altra persona («Smetti di seguire»). */
  class Unfollowed(val liveId: String) : FluidNotifyEvent() {
    override fun toString(): String = "Unfollowed($liveId)"
  }

  /** L'utente ha tolto la notifica dell'attività [liveId]: l'engine non la ripropone finché l'attività dura. */
  class LiveDismissed(val liveId: String) : FluidNotifyEvent() {
    override fun toString(): String = "LiveDismissed($liveId)"
  }
}
