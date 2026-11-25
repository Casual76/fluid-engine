package dev.antigravity.fluidengine.notify

import android.app.NotificationManager
import androidx.core.app.NotificationCompat

// F8.19 · `notifiche-di-sistema`: i canali con parole comuni. Un canale è ciò che l'utente accende e spegne nelle
// impostazioni di Android, quindi il nome dice *cosa arriva* («Voti nuovi»), non come lo chiama il codice. Il riferimento
// Apple non ha canali: ha le categorie e il livello d'interruzione di `UNNotificationContent` (passive, active,
// time-sensitive, critical), che qui diventano [FluidNotifyImportance].

/**
 * Quanto una notifica interrompe. Non è un `enum` (regola 3.11 del piano).
 *
 * Il paragone con iOS è il livello d'interruzione di `UNNotificationInterruptionLevel` (fiducia alta): [Urgent] è
 * «time-sensitive» (compare in alto e suona), [Normal] «active», [Quiet] «passive» (arriva in silenzio nel centro
 * notifiche). Nessun livello scavalca le modalità di concentrazione: l'engine non chiede mai di passare sopra a Non
 * disturbare.
 */
class FluidNotifyImportance private constructor(
  /** Il nome, l'identità. */
  val name: String,
  /** Il livello del canale di Android (`NotificationManager.IMPORTANCE_*`). */
  val androidImportance: Int,
  /** La priorità per Android 7 e prima di lui (`NotificationCompat.PRIORITY_*`): la stessa scala. */
  internal val priority: Int,
) {
  override fun equals(other: Any?): Boolean = other is FluidNotifyImportance && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidNotifyImportance($name)"

  companion object {
    /** Compare in alto e suona: un voto nuovo, un promemoria, una persona che scrive. */
    val Urgent: FluidNotifyImportance =
      FluidNotifyImportance("urgent", NotificationManager.IMPORTANCE_HIGH, NotificationCompat.PRIORITY_HIGH)

    /** Suona, senza comparire in alto. */
    val Normal: FluidNotifyImportance =
      FluidNotifyImportance("normal", NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.PRIORITY_DEFAULT)

    /** In silenzio, con l'icona nella barra di stato: le attività in corso, gli aggiornamenti. */
    val Quiet: FluidNotifyImportance =
      FluidNotifyImportance("quiet", NotificationManager.IMPORTANCE_LOW, NotificationCompat.PRIORITY_LOW)

    /** In silenzio e senza icona nella barra di stato: solo nel centro notifiche. Mai per un Live Update. */
    val Silent: FluidNotifyImportance =
      FluidNotifyImportance("silent", NotificationManager.IMPORTANCE_MIN, NotificationCompat.PRIORITY_MIN)
  }
}

/** Un gruppo di canali: una sezione delle impostazioni («Scuola», «Persone»). */
class FluidNotifyChannelGroup(val id: String, val name: String) {
  override fun equals(other: Any?): Boolean = other is FluidNotifyChannelGroup && other.id == id && other.name == name
  override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode()
  override fun toString(): String = "FluidNotifyChannelGroup($id)"
}

/**
 * Un canale delle notifiche dell'app, con parole comuni.
 *
 * ```kotlin
 * val Voti = FluidNotifyChannel(
 *   id = "voti", name = "Voti nuovi", description = "Quando la scuola pubblica un voto",
 *   importance = FluidNotifyImportance.Urgent, summary = { n -> "$n nuovi voti" },
 * )
 * ```
 *
 * @param id l'identità del canale per Android: **non si cambia più** (Android ricorda le scelte dell'utente per id).
 * @param name il nome che l'utente legge nelle impostazioni: cosa arriva («Voti nuovi»), non «Canale 1».
 * @param description quando arriva («Quando la scuola pubblica un voto»).
 * @param importance quanto interrompe; Android la usa solo alla prima creazione, poi decide l'utente.
 * @param group la sezione delle impostazioni, o null.
 * @param summary la frase che raccoglie più notifiche dello stesso filo («3 nuovi voti»): di serie «N notifiche».
 * @param digestible falso per un canale che arriva **sempre subito**, anche col riepilogo acceso (una persona che scrive,
 *   un'allerta). Di serie vero per [FluidNotifyImportance.Normal] e [FluidNotifyImportance.Quiet], falso per
 *   [FluidNotifyImportance.Urgent].
 * @param badge falso per non contare le sue notifiche nel pallino dell'icona: le attività in corso e i promemoria già
 *   letti non sono «cose da leggere».
 * @param category la categoria di Android per le sue notifiche (`NotificationCompat.CATEGORY_*`): per esempio
 *   `CATEGORY_MESSAGE` per le persone, così le conversazioni prioritarie e le modalità di concentrazione le riconoscono.
 */
class FluidNotifyChannel(
  val id: String,
  val name: String,
  val description: String,
  val importance: FluidNotifyImportance = FluidNotifyImportance.Normal,
  val group: FluidNotifyChannelGroup? = null,
  val summary: (count: Int) -> String = { count -> "$count notifiche" },
  val digestible: Boolean = importance != FluidNotifyImportance.Urgent,
  val badge: Boolean = true,
  val category: String? = null,
) {
  init {
    require(id.isNotBlank()) { "un canale ha un id" }
    require(name.isNotBlank()) { "un canale ha un nome che l'utente capisce" }
  }

  override fun equals(other: Any?): Boolean = other is FluidNotifyChannel && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "FluidNotifyChannel($id)"

  companion object {
    /** L'id del canale dell'engine per le attività in corso (Live Update e notifiche con l'avanzamento). */
    const val LiveId: String = "fluid.live"

    /** L'id del canale dell'engine per i promemoria. */
    const val RemindersId: String = "fluid.reminders"

    /** L'id del canale dell'engine per il riepilogo. */
    const val DigestId: String = "fluid.digest"
  }
}
