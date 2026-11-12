/*
 * QUANDO LEGGERE lo sfondo sotto un vetro (piano «iOS in Android», 3.9 e F2.1).
 *
 * La sonda (`GlassProbe.kt`) misura ciò che il vetro ha dietro per decidere lato, inchiostro e ombra. Leggere costa
 * poco ma non zero (S1-bis: 2–2,7 ms sul principale quando la richiesta cade dopo il disegno della finestra, in tempo
 * morto, 0,2–0,4 ms sul filo di lavoro), e soprattutto una lettura che nessun evento giustifica è uno spreco che si
 * paga per ogni vetro radice di ogni schermata. Quindi la regola è: **niente eventi, niente letture**. Gli eventi sono
 * tre, e nessuno chiede una cattura in più:
 *
 * 1. una **revisione** di una sorgente: il layer che il vetro campiona si è registrato di nuovo. È l'unico modo di
 *    sapere che sotto un vetro fermo lo sfondo è cambiato, perché la cattura del vetro tiene la sorgente per
 *    riferimento e non si rifà (`DrawBackdropModifier.kt`, invariante 3);
 * 2. un cambio di **geometria**: il vetro si è spostato o ridimensionato rispetto alle sorgenti (o loro rispetto a
 *    lui). Solo qui la miniatura va registrata di nuovo; dopo una revisione basta rileggerla;
 * 3. sorgenti che **non avvisano** (un `CanvasBackdrop`, il materiale pubblicato da un pannello): per loro, e solo
 *    per loro, una cadenza fissa e bassa. Il ritardo massimo del giro, lì, è quella cadenza.
 *
 * Più due freni: una **cadenza massima** (al più una lettura ogni [GlassProbeMinIntervalMillis]: gli eventi che
 * arrivano nel frattempo si sommano in una lettura sola) e il **silenzio durante un movimento veloce** (il vetro che
 * vola, la pagina lanciata: leggere lì misura uno sfondo che fra un fotogramma non c'è più, e il giro che ne nasce
 * sfarfalla). Il movimento finito, la lettura che era in attesa parte.
 *
 * Puro: gli istanti glieli dà chi chiama, in millisecondi di un orologio monotono. Lo prova `GlassProbeSchedulerTest`.
 */
package dev.antigravity.fluidengine.ui.glass.field

import kotlin.math.min

/**
 * La cadenza massima della sonda: al più una lettura ogni tanti millisecondi.
 *
 * Il piano chiede 100–150 ms (3.9). S1-bis ha misurato quattro sonde ogni 100 ms senza fotogrammi persi sul Tab S9, e
 * anche ogni 33 ms; 120 sta nel mezzo dell'intervallo del piano e a 120 Hz sono 14–15 fotogrammi. È un'ipotesi da
 * tarare in S3 (sottocategoria F2-f, costo), insieme alla tenuta su una striscia sottile.
 */
internal const val GlassProbeMinIntervalMillis: Long = 120L

/**
 * La cadenza fissa per le sorgenti che non avvisano quando cambiano. Bassa apposta: quelle sorgenti sono rare sotto un
 * vetro radice, e per loro il ritardo del giro è questo numero. Ipotesi da tarare in S3.
 */
internal const val GlassProbeCadenceMillis: Long = 500L

/**
 * Sopra questa velocità del vetro rispetto alle sorgenti (in dp al secondo) la sonda tace: è un vetro che vola o una
 * pagina che scivola via, non un vetro che si sposta piano. Scelta per coerenza con `FluidGlassQuality`
 * (`FullQualityVelocity` = 24 px per fotogramma, circa 480 dp/s a 60 Hz e densità 3). Ipotesi da tarare in S3.
 */
internal const val GlassProbeFastMotionDpPerSecond: Float = 500f

/** Quanto silenzio dopo l'ultimo spostamento veloce prima di considerare finito il movimento. */
internal const val GlassProbeMotionQuietMillis: Long = 100L

/**
 * Su quanto tempo, al più, si spalma uno spostamento per farne una velocità. Il vetro avvisa a ogni fotogramma in cui
 * si muove; il primo spostamento dopo un riposo, diviso per tutto il riposo, sembrerebbe lento (40 px in mezzo
 * secondo), e la sonda leggerebbe proprio mentre il vetro parte. Due fotogrammi a 60 Hz.
 */
internal const val GlassProbeMotionWindowMillis: Long = 32L

/**
 * Il tetto dell'attesa dopo letture fallite di fila: la cadenza massima raddoppia a ogni fallimento fino a qui. Un
 * lettore che rifiuta (superficie persa, contesto fermo) non deve diventare un giro a vuoto ogni 120 ms.
 */
internal const val GlassProbeMaxBackoffMillis: Long = 2_000L

/**
 * Quante letture fallite di fila la sonda ritenta da sola, a tempo (F2.3). Oltre, si comporta come dopo una lettura
 * saltata: aspetta il prossimo evento (una revisione, uno spostamento). Prima di F2.3 ritentava per sempre, ogni 2 s col
 * lettore rifatto a ogni tentativo: una pagina ferma con un lettore che non risponde (Robolectric, una piattaforma che
 * non consegna l'immagine) non arrivava mai a riposo. Tre tentativi coprono un rifiuto passeggero (superficie persa,
 * contesto fermo) in 0,24 + 0,48 + 0,96 s.
 */
internal const val GlassProbeMaxRetries: Int = 3

/** Che cosa fare adesso. */
internal sealed interface GlassProbeDecision {
  /** Leggere ora. [rerecord]: la geometria è cambiata (o non c'è ancora una miniatura) e va registrata di nuovo. */
  data class Read(val rerecord: Boolean) : GlassProbeDecision

  /** C'è qualcosa da fare, ma non prima di [atMillis]: richiamare [GlassProbeScheduler.next] allora. */
  data class WaitUntil(val atMillis: Long) : GlassProbeDecision

  /** Niente da fare finché non arriva un evento (o finché la lettura in volo non torna). */
  data object Idle : GlassProbeDecision
}

/** Com'è finita una lettura chiesta. */
internal enum class GlassProbeOutcome {
  /** I numeri sono arrivati. */
  Read,

  /**
   * Il lettore ha rifiutato o non ha risposto: si riprova, con un'attesa che cresce, al più [GlassProbeMaxRetries]
   * volte di fila; poi si aspetta il prossimo evento.
   */
  Failed,

  /**
   * Non c'era niente da leggere adesso (la finestra è in secondo piano, il vetro non ha ancora coordinate, la
   * sorgente non è pronta). Non è un errore: si aspetta il prossimo evento, che arriva da solo quando la cosa
   * cambia (la finestra che torna ridisegna, e le sorgenti avvisano).
   */
  Skipped,
}

/**
 * La regola del quando, per una sonda. Vedi l'intestazione del file.
 *
 * @param minIntervalMillis la cadenza massima.
 * @param cadenceMillis la cadenza fissa per le sorgenti che non avvisano.
 * @param fastPxPerSecond la velocità sopra cui la sonda tace, in pixel al secondo: chi chiama moltiplica
 *   [GlassProbeFastMotionDpPerSecond] per la densità (il valore di serie vale a densità 1, per i test).
 * @param motionQuietMillis quanto silenzio dopo l'ultimo spostamento veloce.
 * @param maxBackoffMillis il tetto dell'attesa dopo letture fallite.
 * @param maxRetries quante letture fallite di fila si ritentano da sole ([GlassProbeMaxRetries]).
 */
internal class GlassProbeScheduler(
  private val minIntervalMillis: Long = GlassProbeMinIntervalMillis,
  private val cadenceMillis: Long = GlassProbeCadenceMillis,
  private val fastPxPerSecond: Float = GlassProbeFastMotionDpPerSecond,
  private val motionQuietMillis: Long = GlassProbeMotionQuietMillis,
  private val maxBackoffMillis: Long = GlassProbeMaxBackoffMillis,
  private val maxRetries: Int = GlassProbeMaxRetries,
) {
  /** Un evento è arrivato dopo l'inizio dell'ultima lettura: ne serve un'altra. */
  private var pendingRead = false

  /** La geometria è cambiata dopo l'ultima registrazione della miniatura. */
  private var pendingRerecord = false

  /**
   * L'ultima lettura è fallita e va ritentata a tempo (F2.3). Non è un evento: dopo [maxRetries] fallimenti di fila non
   * fa più partire niente da sola, ma resta, e la lettura che parte col prossimo evento rifà la miniatura se serviva.
   */
  private var retry = false
  private var retryRerecord = false

  /** Una lettura è stata chiesta e non è ancora tornata. */
  var inFlight: Boolean = false
    private set

  private var sentRerecord = false
  private var lastSentAt = Long.MIN_VALUE
  private var previousSentAt = Long.MIN_VALUE
  private var lastGeometryAt = Long.MIN_VALUE
  private var fastUntil = Long.MIN_VALUE
  private var failures = 0
  private var unannounced = false
  private var suspended = false

  /** Quante letture sono partite. Per i test e per la diagnostica. */
  var reads: Int = 0
    private set

  /** Una sorgente si è registrata di nuovo: lo sfondo può essere cambiato anche se niente si è mosso. */
  fun onRevision(nowMillis: Long) {
    pendingRead = true
  }

  /**
   * Il vetro si è spostato di [movedPx] pixel rispetto alle sorgenti (o è cambiata la taglia: chi chiama passa lo
   * spostamento più grande dei quattro lati). La prima chiamata è la nascita della sonda: niente velocità.
   */
  fun onGeometry(nowMillis: Long, movedPx: Float) {
    pendingRead = true
    pendingRerecord = true
    val previous = lastGeometryAt
    lastGeometryAt = nowMillis
    if (previous == Long.MIN_VALUE || !movedPx.isFinite() || movedPx <= 0f) return
    // Due spostamenti nello stesso millisecondo non danno una velocità: non si decide niente su un'unità di tempo nulla.
    if (nowMillis - previous <= 0L) return
    val elapsed = min(nowMillis - previous, GlassProbeMotionWindowMillis)
    val speed = movedPx * 1000f / elapsed
    if (speed > fastPxPerSecond) fastUntil = nowMillis + motionQuietMillis
  }

  /** Se fra le sorgenti ce n'è almeno una che non avvisa quando cambia: per quelle vale la cadenza fissa. */
  fun setUnannounced(value: Boolean) {
    unannounced = value
  }

  /**
   * La sonda tace finché [value] è vero: la qualità del vetro è sotto la soglia (un lancio veloce) o chi la possiede
   * l'ha fermata. Gli eventi intanto si accumulano, e al ritorno parte una lettura sola.
   */
  fun setSuspended(value: Boolean) {
    suspended = value
  }

  /** Il movimento veloce è ancora in corso a [nowMillis]. */
  fun isMovingFast(nowMillis: Long): Boolean = nowMillis < fastUntil

  /** Che cosa fare a [nowMillis]. Non cambia niente: solo [onReadSent] e [onReadDone] lo fanno. */
  fun next(nowMillis: Long): GlassProbeDecision {
    if (inFlight || suspended) return GlassProbeDecision.Idle
    val cadenceDue = unannounced && (lastSentAt == Long.MIN_VALUE || nowMillis - lastSentAt >= cadenceMillis)
    val due = pendingRead || pendingRerecord || cadenceDue || retryDue
    if (!due) {
      return if (unannounced) GlassProbeDecision.WaitUntil(lastSentAt + cadenceMillis) else GlassProbeDecision.Idle
    }
    if (nowMillis < fastUntil) return GlassProbeDecision.WaitUntil(fastUntil)
    if (lastSentAt != Long.MIN_VALUE) {
      val earliest = lastSentAt + interval()
      if (nowMillis < earliest) return GlassProbeDecision.WaitUntil(earliest)
    }
    return GlassProbeDecision.Read(rerecord = pendingRerecord || retryRerecord || reads == 0)
  }

  /** Un ritentativo a tempo è ancora permesso: l'ultima lettura è fallita, e non più di [maxRetries] di fila. */
  private val retryDue: Boolean get() = retry && failures <= maxRetries

  /**
   * La lettura decisa da [next] è partita a [nowMillis] (con o senza una nuova miniatura, [rerecord]). Gli eventi
   * arrivati fin qui sono coperti da lei; quelli che arrivano da adesso ne chiedono un'altra.
   */
  fun onReadSent(nowMillis: Long, rerecord: Boolean) {
    inFlight = true
    sentRerecord = rerecord
    pendingRead = false
    retry = false
    if (rerecord) {
      pendingRerecord = false
      retryRerecord = false
    }
    previousSentAt = lastSentAt
    lastSentAt = nowMillis
    reads++
  }

  /** La lettura partita è finita così. */
  fun onReadDone(nowMillis: Long, outcome: GlassProbeOutcome) {
    if (!inFlight) return
    inFlight = false
    when (outcome) {
      GlassProbeOutcome.Read -> failures = 0
      GlassProbeOutcome.Failed -> {
        // Non un evento: un ritentativo, a tempo finché ce ne sono (vedi [retryDue]), poi col prossimo evento.
        failures++
        retry = true
        if (sentRerecord) retryRerecord = true
      }
      GlassProbeOutcome.Skipped -> {
        // Niente attesa che cresce e niente nuovo tentativo a tempo: si riparte dal prossimo evento. Ma la lettura
        // non c'è stata, quindi ciò che era in attesa resta in attesa.
        pendingRead = true
        if (sentRerecord) pendingRerecord = true
        // Lo spazio fra due letture conta da quelle vere: una lettura saltata non deve ritardare la prossima.
        lastSentAt = previousSentAt
        reads--
      }
    }
  }

  /**
   * Se c'è un evento che aspetta una lettura. Dopo una lettura saltata ([GlassProbeOutcome.Skipped]) resta vero, ma
   * chi possiede la sonda non richiama [next] a tempo: aspetta il prossimo evento, perché un tentativo subito
   * salterebbe di nuovo.
   */
  val hasPending: Boolean get() = pendingRead || pendingRerecord || retryDue

  /** Lo spazio minimo fra due letture adesso: la cadenza massima, raddoppiata a ogni fallimento di fila. */
  fun interval(): Long {
    if (failures <= 0) return minIntervalMillis
    val shift = min(failures, 16)
    val grown = minIntervalMillis shl shift
    return if (grown <= 0L) maxBackoffMillis else min(grown, maxBackoffMillis)
  }
}
