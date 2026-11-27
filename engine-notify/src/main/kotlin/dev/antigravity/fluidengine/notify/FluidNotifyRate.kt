package dev.antigravity.fluidengine.notify

// F8.19 · Il ritmo: aggiornare senza inondare, con una frequenza che il sistema non taglia.
//
// Android stima il ritmo con cui ogni app manda notifiche (una media mobile degli intervalli) e, oltre **5 al secondo**,
// scarta gli aggiornamenti di una notifica che c'è già, tranne quello che chiude un avanzamento («Package enqueue rate is
// … Shedding events»): AOSP, `NotificationManagerService.DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE = 5f` e `RateEstimator`,
// fiducia media-alta (codice letto a memoria, non riletto il 25/11/2026). Un aggiornamento scartato non torna: la barra
// resterebbe ferma su un valore vecchio. Per questo tutto ciò che l'engine pubblica passa da una porta sola
// ([FluidNotifyGate]) che non supera mai [FluidNotifyProfile.MaxPostsPerSecond] al secondo, e un aggiornamento che
// aspetta il suo turno viene **sostituito** dal successivo invece di accodarsi: arriva sempre l'ultimo valore.

/**
 * I numeri di engine-notify, con la fonte.
 */
internal object FluidNotifyProfile {
  /**
   * Quante notifiche al secondo l'engine pubblica al massimo, fra tutte quelle dell'app: una meno del limite di Android
   * (5, vedi sopra), così la stima del sistema resta sotto anche con un po' di scarto. A occhio sopra la fonte, 25/11/2026.
   */
  const val MaxPostsPerSecond: Int = 4

  /** La finestra del ritmo, in millisecondi. */
  const val RateWindowMs: Long = 1_000L

  /**
   * Il passo minimo fra due aggiornamenti della stessa attività quando cambia solo l'avanzamento. Apple chiede di
   * aggiornare «only when new content is available» (HIG, Live Activities, fiducia alta) e non dà un numero; Android non ne
   * dà nessuno. Un secondo: la barra della notifica è larga ~300 dp, un aggiornamento al secondo è già fluido per un lavoro
   * di minuti. A occhio, 25/11/2026.
   */
  const val ProgressGapMs: Long = 1_000L

  /**
   * La raffica: le notifiche dello stesso filo che arrivano entro questo tempo dall'ultima che ha suonato arrivano in
   * silenzio, raccolte («3 nuovi voti» invece di tre sveglie). Android 15 abbassa da solo il volume delle notifiche
   * ravvicinate della stessa app («notification cooldown»); questa è la stessa idea, per filo. A occhio, 25/11/2026.
   */
  const val BurstWindowMs: Long = 120_000L

  /**
   * Quanto resta la notifica dell'esito dopo la fine: quattro ore, come una Live Activity finita sulla schermata di blocco
   * («it remains for up to four hours», HIG Live Activities e ActivityKit `ActivityUIDismissalPolicy`, fiducia alta).
   */
  const val EndedLingerMs: Long = 4L * 60 * 60 * 1000

  /**
   * Quanto aspetta l'engine prima di dire che l'app è uscita dal primo piano: un'attività che si ricrea (una rotazione)
   * si ferma e riparte, e non deve far lampeggiare le notifiche. È il `TIMEOUT_MS` di `ProcessLifecycleOwner` di
   * androidx (700 ms, fiducia alta).
   */
  const val OutsideDelayMs: Long = 700L

  /**
   * La finestra di un promemoria senza l'allarme esatto: Android 12 e successivi allargano a dieci minuti le finestre più
   * strette (developer.android.com, «Schedule alarms», `setWindow`, fiducia alta).
   */
  const val InexactWindowMs: Long = 10L * 60 * 1000

  /** Oltre questo ritardo un promemoria conta come arrivato tardi, nel registro. A occhio, 25/11/2026. */
  const val LateThresholdMs: Long = 60_000L

  /** Quanti promemoria arrivati tiene il registro. */
  const val DeliveryLogSize: Int = 50

  /**
   * Quante notifiche aspettano al più il riepilogo (le più recenti; R2): Android non tiene più di 50 notifiche a schermo
   * per un'app e scarta le altre (AOSP, `NotificationManagerService.MAX_PACKAGE_NOTIFICATIONS = 50`, fiducia media-alta,
   * letto a memoria il 26/11/2026). Una coda senza tetto faceva anche riscrivere un file sempre più grande a ogni `post`.
   */
  const val DigestQueueSize: Int = 50
}

/**
 * La porta di tutto ciò che l'engine pubblica: al massimo [maxPerWindow] pubblicazioni in [windowMs], e un'azione che
 * aspetta il suo turno viene sostituita da una nuova con la stessa chiave (vince l'ultima, al posto della prima nella
 * fila). Pura, con l'orologio e il timer dati da fuori: le prove la guidano a mano.
 *
 * Va usata da un thread solo (quello del timer, il principale): chi arriva da un altro filo passa da `FluidNotify.onMain`.
 */
internal class FluidNotifyGate(
  private val clock: () -> Long,
  private val schedule: (delayMs: Long, block: () -> Unit) -> Unit,
  private val maxPerWindow: Int = FluidNotifyProfile.MaxPostsPerSecond,
  private val windowMs: Long = FluidNotifyProfile.RateWindowMs,
) {
  private class Pending(var notBefore: Long, var action: () -> Unit)

  private val pending = LinkedHashMap<String, Pending>()
  private val stamps = ArrayDeque<Long>()
  private var wakeAt: Long = Long.MAX_VALUE

  /** Quante pubblicazioni ha fatto, in tutto (per le prove e per il registro). */
  var posted: Int = 0
    private set

  /** Quante azioni sono state sostituite prima di partire (aggiornamenti intermedi risparmiati). */
  var coalesced: Int = 0
    private set

  /** Le chiavi in attesa, nell'ordine della fila. */
  val waiting: List<String> get() = pending.keys.toList()

  /**
   * Esegue [action] appena il ritmo lo permette, e non prima di [notBefore]. Se un'azione con la stessa [key] aspetta già,
   * la sostituisce e tiene il suo posto; il [notBefore] resta il più vicino dei due.
   */
  fun submit(key: String, notBefore: Long = clock(), action: () -> Unit) {
    val old = pending[key]
    if (old != null) {
      old.action = action
      old.notBefore = minOf(old.notBefore, notBefore)
      coalesced++
    } else {
      pending[key] = Pending(notBefore, action)
    }
    drain()
  }

  /** Toglie l'azione [key] dalla fila, se aspetta. */
  fun cancel(key: String) {
    pending.remove(key)
  }

  /** Vero se l'azione [key] aspetta il suo turno. */
  fun isWaiting(key: String): Boolean = key in pending

  /** Esegue ciò che può adesso, e chiede il timer per il resto. */
  fun drain() {
    val now = clock()
    while (stamps.isNotEmpty() && now - stamps.first() >= windowMs) stamps.removeFirst()
    // Una alla volta, rileggendo la fila: un'azione può pubblicarne un'altra (il riepilogo di un filo dopo il messaggio).
    while (stamps.size < maxPerWindow) {
      val entry = pending.entries.firstOrNull { it.value.notBefore <= now } ?: break
      pending.remove(entry.key)
      stamps.addLast(now)
      posted++
      entry.value.action()
    }
    if (pending.isEmpty()) return
    val nextSlot = if (stamps.size < maxPerWindow) now else stamps.first() + windowMs
    val nextDue = pending.values.minOf { it.notBefore }
    val next = maxOf(nextSlot, nextDue)
    if (next < wakeAt || wakeAt <= now) {
      wakeAt = next
      schedule((next - now).coerceAtLeast(0L)) {
        wakeAt = Long.MAX_VALUE
        drain()
      }
    }
  }
}
