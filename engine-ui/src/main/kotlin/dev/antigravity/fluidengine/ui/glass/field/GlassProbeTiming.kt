/*
 * F7.25 · QUANDO LEGGE LA SONDA SE IL RENDERTHREAD È INDIETRO.
 *
 * La lettura della sonda (`GlassProbeWindowReader.send`) è una `syncAndDraw` del suo `HardwareRenderer`: il thread
 * principale aspetta che il RenderThread (uno per processo) prenda la sincronizzazione, cioè che finisca quello che sta
 * facendo. F7.22 la chiedeva al commit della finestra, col RenderThread libero (0,3 ms); ma quando il RenderThread è in
 * ritardo, e sempre mentre la maniglia del PDF di 3000 pagine trascina, il commit arriva quando la finestra ha già
 * cominciato il fotogramma dopo, e dopo tre rinvii la sonda leggeva «come prima», subito dopo il disegno della finestra:
 * 4,5–5,7 ms di principale fermo, perché in quel momento il RenderThread ha davanti tutto il fotogramma nuovo
 * (`dequeueBuffer` bloccato 2–3,5 ms sui tre buffer pieni, più 4–5 ms di disegno).
 *
 * **Che cosa dicono le tracce** (Tab S9, le sei corse di F7.22, `artifacts/liquid-glass/sessioni/f7-25/misure/pc/`):
 * con i tre buffer pieni il RenderThread finisce il fotogramma F 0,2–2 ms dopo l'inizio del fotogramma F+1 del
 * principale, e resta libero fino alla sincronizzazione di F+1, 1,5–3 ms dopo. Al pre-disegno di F+1 (prima che la
 * finestra registri e sincronizzi) è libero nel 35–94% dei fotogrammi; quando non lo è, gli manca la coda di F, non un
 * fotogramma intero.
 *
 * **La regola** ([GlassProbeReadGate]): si legge solo col RenderThread libero, e lo dice un *ping*
 * ([GlassProbeRenderThreadPing]), una `syncAndDraw` vuota su un filo suo, accodata dietro il fotogramma della finestra:
 * quando torna, il RenderThread ha finito.
 *
 * - **Il ping torna e la finestra non ha disegnato altro**: si legge subito, fra due fotogrammi (come il commit di F7.22).
 * - **Al pre-disegno di un fotogramma, ping tornato**: si legge lì, prima che la finestra registri; la radice si
 *   invalida, così il disegno della finestra in questo stesso fotogramma ha il danno intero e niente di ciò che la
 *   lettura ha sincronizzato per prima si perde (il rischio di S1-bis).
 * - **Ping non ancora tornato**: si salta, e il vetro tiene la lettura di prima. Per [GlassProbeFreeSkipFrames]
 *   fotogrammi senza nessuna attesa; poi, al pre-disegno, si aspetta il ping al più [GlassProbeBriefWaitNanos], una volta
 *   sola per lettura (F7.25c); dopo [GlassProbeCeilingNanos] (250 ms) si legge comunque, al pre-disegno, pagando la coda
 *   del RenderThread.
 *
 * **Il prezzo** è una lettura che arriva qualche fotogramma dopo. Sul Tab S9: 0–2 fotogrammi di mediana, 2–15 al p90.
 * Il tetto era 15 fotogrammi (125 ms, come il ritardo con cui il vetro di Apple comincia a girare: R4 B6, WWDC25 219,
 * 6:52); dalla F7.25d è un tempo, 250 ms, la scelta di Alessio (vedi [GlassProbeCeilingNanos]). Il rapporto di F7.25 ha
 * i numeri.
 */
package dev.antigravity.fluidengine.ui.glass.field

import android.graphics.HardwareRenderer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * F7.25 · Per quanti fotogrammi una lettura salta senza nessuna attesa, se il RenderThread è occupato: 4, cioè 33 ms, un
 * fotogramma del video di Apple (29,97 fps). Scelto sulle tracce di F7.22 (26 novembre 2026): con 4 la lettura arriva
 * con 1 fotogramma di ritardo di mediana e 4–5 al p90 nella maniglia, e le attese brevi servono al 6–10% delle letture.
 */
internal const val GlassProbeFreeSkipFrames: Int = 4

/**
 * F7.25 · Quanto il principale aspetta il ping al pre-disegno, dopo i fotogrammi liberi: 1 ms. Con i tre buffer pieni
 * al pre-disegno al RenderThread mancano 0,2–2 ms; oltre il millisecondo si salta di nuovo (tracce di F7.22).
 */
internal const val GlassProbeBriefWaitNanos: Long = 1_000_000L

/**
 * F7.25d · Dopo quanto tempo una lettura che aspetta il RenderThread libero si fa comunque: **250 ms**. Un tempo e non
 * un numero di fotogrammi, così vale uguale a 60 Hz (15 fotogrammi) e a 120 Hz (30).
 *
 * È la scelta di Alessio del 26 novembre 2026 (la domanda del rapporto di F7.25, risposta «B»). Il tetto di F7.25 era
 * 15 fotogrammi, 125 ms. Sul Tab S9, nello scorrimento più pesante del PDF di 3000 pagine (RenderThread oltre 8,3 ms in
 * 334–459 fotogrammi su 8 s), il 27–37% delle letture ci arrivava e lì aspettava la coda del RenderThread: 4–7 ms di
 * filo principale, fino a 13, quanto la sonda di prima. Il tetto più lungo serve a leggerci più di rado. In cambio il
 * vetro può girare chiaro/scuro fino a un quarto di secondo dopo il contenuto, più tardi dei 100–133 ms di Apple.
 */
internal const val GlassProbeCeilingNanos: Long = 250_000_000L

/**
 * F7.25d · L'orologio del tetto: `SystemClock.uptimeMillis()`, in nanosecondi. Lo stesso per l'inizio dell'attesa
 * (fuori da un fotogramma) e per i fotogrammi, e in Robolectric è l'orologio simulato con cui girano i fotogrammi.
 * Il millisecondo basta per un tetto di 250 ms.
 */
internal fun glassProbeNowNanos(): Long = SystemClock.uptimeMillis() * 1_000_000L

/** Che cosa fare di una lettura che aspetta, adesso. */
internal enum class GlassProbeGateVerdict {
  /** Il RenderThread è libero: si legge. */
  ReadNow,

  /** Occupato, e siamo ancora nei fotogrammi liberi: si salta, senza nessuna attesa. */
  Skip,

  /** Occupato, dopo i fotogrammi liberi: si aspetta il ping per poco, e si legge solo se torna. Una volta per lettura. */
  WaitBriefly,

  /** Al tetto: si legge comunque. */
  ReadAnyway,
}

/**
 * F7.25 · La regola dei fotogrammi saltati (vedi l'intestazione del file). Pura: i fotogrammi e lo stato del
 * RenderThread glieli dice il lettore della finestra. Dal thread principale. La prova è `GlassProbeReadGateTest`.
 */
internal class GlassProbeReadGate(
  private val freeFrames: Int = GlassProbeFreeSkipFrames,
  private val ceilingNanos: Long = GlassProbeCeilingNanos,
) {
  init {
    require(freeFrames >= 0) { "i fotogrammi liberi ($freeFrames) non possono essere meno di zero" }
    require(ceilingNanos > 0L) { "il tetto ($ceilingNanos ns) deve essere più di zero" }
  }

  /** I fotogrammi passati da quando la lettura aspetta. */
  var frames: Int = 0
    private set

  /** F7.25d · Da quanto la lettura aspetta, al suo ultimo fotogramma. */
  var waitedNanos: Long = 0L
    private set

  private var startNanos = 0L

  /** F7.25c · Se questa lettura ha già avuto la sua attesa breve. */
  private var waited = false

  /** Una lettura comincia ad aspettare, adesso ([nowNanos], l'orologio di [glassProbeNowNanos]). */
  fun begin(nowNanos: Long) {
    frames = 0
    waitedNanos = 0L
    startNanos = nowNanos
    waited = false
  }

  /** Un fotogramma nuovo mentre la lettura aspetta (la fase delle animazioni), adesso ([nowNanos]). */
  fun onFrame(nowNanos: Long) {
    frames++
    waitedNanos = nowNanos - startNanos
  }

  /**
   * Che cosa fare adesso, con il RenderThread [free] o no. [GlassProbeGateVerdict.WaitBriefly] arriva **una volta sola per
   * lettura** (F7.25c), al primo fotogramma dopo quelli liberi, e chi lo riceve aspetta il ping: dopo si salta fino al
   * ping o al tetto. Sul Tab S9 (finestra 1 di F7.25, 26 novembre 2026), col RenderThread oltre 8,3 ms per fotogrammi di
   * fila, l'attesa ripetuta a ogni fotogramma scadeva 5 volte su 6: 230 attese e 237 ms di principale in 10 s di lanci,
   * più delle letture «come prima» che sostituiva. Il ping tornato dopo l'attesa si legge comunque al pre-disegno dopo,
   * o fra due fotogrammi: costa al più un fotogramma di ritardo, solo in quei momenti.
   */
  fun verdict(free: Boolean): GlassProbeGateVerdict = when {
    free -> GlassProbeGateVerdict.ReadNow
    waitedNanos >= ceilingNanos -> GlassProbeGateVerdict.ReadAnyway
    frames > freeFrames && !waited -> {
      waited = true
      GlassProbeGateVerdict.WaitBriefly
    }
    else -> GlassProbeGateVerdict.Skip
  }
}

/**
 * F7.25 · Chi dice quando il RenderThread ha finito: [ping] accoda un segnale dietro il lavoro che il RenderThread ha
 * adesso; [returned] è l'ultimo tornato. Il vero è [GlassProbeRenderThreadPing]; le prove ne usano uno finto
 * (`GlassProbeWindowReader.fenceForTesting`).
 */
internal interface GlassProbeFence {
  /** L'ultimo ping tornato (0 = nessuno). Si legge da qualunque filo. */
  val returned: Int

  /** Falso se il ping non si può fare (il renderer non è nato): il lettore torna a chiedere come prima. */
  val healthy: Boolean

  /** Un ping col numero [seq] (crescente). Dal principale. Quando torna, chi l'ha creato lo sa sul principale. */
  fun ping(seq: Int)

  /** Aspetta che il ping [seq] torni, al più [timeoutNanos]. Vero se è tornato. Dal principale. */
  fun await(seq: Int, timeoutNanos: Long): Boolean

  /** Chiude tutto. Dal principale. */
  fun release()
}

/**
 * F7.25 · Il ping del RenderThread: un `HardwareRenderer` **senza superficie e senza nodi**, usato solo da un filo suo
 * (`FluidGlass.sonda.rt`). La sua `syncAndDraw` è un compito in coda al RenderThread come gli altri: torna quando il
 * RenderThread ha finito il fotogramma della finestra che aveva davanti, e poi non disegna niente (niente superficie,
 * `SYNC_LOST_SURFACE_REWARD_IF_FOUND`). Non tocca nessun nodo della finestra, quindi non ha il problema di S1 (una
 * lettura da un altro filo che sincronizza i nodi condivisi mentre il principale li cambia): il suo albero è vuoto.
 *
 * I ping si raccolgono: se ne arriva uno nuovo mentre il filo aspetta il precedente, parte solo l'ultimo, e il suo numero
 * vale anche per quelli saltati. [onReturn] arriva sul principale col numero tornato.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class GlassProbeRenderThreadPing(private val onReturn: (Int) -> Unit) : GlassProbeFence {
  private val main = Handler(Looper.getMainLooper())
  private val thread = HandlerThread("FluidGlass.sonda.rt").apply { start() }
  private val handler = Handler(thread.looper)
  private val lock = Object()
  private val latest = AtomicInteger()

  // Del filo del ping soltanto.
  private var renderer: HardwareRenderer? = null

  @Volatile
  override var returned: Int = 0
    private set

  @Volatile
  override var healthy: Boolean = true
    private set

  @Volatile
  private var released = false

  private val run = Runnable { pingOnce() }

  override fun ping(seq: Int) {
    if (released) return
    latest.set(seq)
    handler.post(run)
  }

  private fun pingOnce() {
    val seq = latest.get()
    if (released || seq <= returned) return
    val target = renderer ?: try {
      HardwareRenderer().also {
        it.setName("FluidGlass.sonda.rt")
        renderer = it
      }
    } catch (error: RuntimeException) {
      Log.w(PingLogTag, "Sonda dello sfondo: il ping del RenderThread non è nato ($error), si legge come prima")
      healthy = false
      null
    }
    if (target != null) {
      Trace.beginSection(SectionPing)
      try {
        target.createRenderRequest().setVsyncTime(System.nanoTime()).syncAndDraw()
      } catch (error: RuntimeException) {
        Log.w(PingLogTag, "Sonda dello sfondo: il ping del RenderThread ha lanciato ($error), si legge come prima")
        healthy = false
      } finally {
        Trace.endSection()
      }
    }
    synchronized(lock) {
      returned = seq
      lock.notifyAll()
    }
    main.post { onReturn(seq) }
  }

  override fun await(seq: Int, timeoutNanos: Long): Boolean {
    if (returned >= seq) return true
    val deadline = System.nanoTime() + timeoutNanos
    Trace.beginSection(SectionAwait)
    try {
      synchronized(lock) {
        while (returned < seq) {
          val left = deadline - System.nanoTime()
          if (left <= 0L) return false
          lock.wait(left / 1_000_000L, (left % 1_000_000L).toInt())
        }
      }
      return true
    } finally {
      Trace.endSection()
    }
  }

  override fun release() {
    if (released) return
    released = true
    handler.post {
      renderer?.destroy()
      renderer = null
    }
    thread.quitSafely()
  }

  private companion object {
    /** Lo stesso nome di registro della sonda (`GlassProbe.kt`). */
    const val PingLogTag = "FluidGlass"
    const val SectionPing = "Glass.sonda ping"
    const val SectionAwait = "Glass.sonda aspetta il ping"
  }
}
