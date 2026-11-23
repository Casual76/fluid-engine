package dev.antigravity.fluidengine.foundation.memory

import java.io.Closeable

/** Lettura puntuale del registro. Il picco comprende insieme lavoro in volo e risorse residenti. */
class FluidMemoryUsage internal constructor(
  val limitBytes: Long,
  val usedBytes: Long,
  val inFlightBytes: Long,
  val peakBytes: Long,
  val refusals: Long,
) {
  val residentBytes: Long get() = usedBytes - inFlightBytes
  val excessBytes: Long get() = (usedBytes - limitBytes).coerceAtLeast(0)
}

/**
 * Un registro per processo, fornito dal chiamante a tutti i produttori. Il medesimo lock copre entrambi
 * i limiti e ogni passaggio di proprietà. Il registro contabilizza byte dichiarati e lascia al chiamante
 * allocazione, pubblicazione, espulsione e distruzione. Nessuna callback entra nella sezione critica.
 */
class FluidMemoryBudget(initialLimits: FluidMemoryLimits) {
  private val lock = Any()
  private var limits = initialLimits
  private val process = Counter()
  private val categories = Array(FluidMemoryCategory.entries.size) { Counter() }

  private class Counter {
    var used = 0L
    var inFlight = 0L
    var peak = 0L
    var refusals = 0L

    fun change(delta: Long, inFlightDelta: Long) {
      used += delta
      inFlight += inFlightDelta
      peak = maxOf(used, peak)
    }

    fun refused() {
      if (refusals < Long.MAX_VALUE) refusals++
    }

    fun snapshot(limit: Long) = FluidMemoryUsage(limit, used, inFlight, peak, refusals)
  }

  /** Prenota una stima conservativa positiva prima di avviare il produttore. Null permette espulsione o ripiego. */
  fun tryReserve(category: FluidMemoryCategory, bytes: Long): Reservation? = synchronized(lock) {
    require(bytes > 0)
    if (!admit(category, bytes)) return null
    val reservation = Reservation(category, bytes)
    change(category, bytes, bytes)
    reservation
  }

  /** Sostituisce i limiti conservando ogni spesa viva. Ritorna il minimo numero di byte da recuperare. */
  fun updateLimits(newLimits: FluidMemoryLimits): Long = synchronized(lock) {
    limits = newLimits
    var categoryExcess = 0L
    for (category in FluidMemoryCategory.entries) {
      categoryExcess += (categories[category.ordinal].used - limits[category]).coerceAtLeast(0)
    }
    maxOf(process.used - limits.processBytes, categoryExcess)
  }

  /** Diagnostica su richiesta, per il processo (senza argomento) o una categoria. */
  fun usage(category: FluidMemoryCategory? = null): FluidMemoryUsage = synchronized(lock) {
    if (category == null) process.snapshot(limits.processBytes)
    else categories[category.ordinal].snapshot(limits[category])
  }

  // Chiamati sotto lock. Il margine può essere negativo dopo un trim o una sottostima.
  private fun admit(category: FluidMemoryCategory, growth: Long): Boolean {
    val counter = categories[category.ordinal]
    if (growth <= limits[category] - counter.used && growth <= limits.processBytes - process.used) return true
    counter.refused()
    process.refused()
    return false
  }

  private fun change(category: FluidMemoryCategory, delta: Long, inFlightDelta: Long) {
    categories[category.ordinal].change(delta, inFlightDelta)
    process.change(delta, inFlightDelta)
  }

  /**
   * Il produttore possiede la prenotazione fino all'uscita effettiva del lavoro (anche nativo).
   * Usare use/finally nel produttore stesso. Il semplice annullo di chi aspetta non restituisce i byte.
   */
  inner class Reservation internal constructor(private val category: FluidMemoryCategory, private val estimateBytes: Long) : Closeable {
    private var charged = estimateBytes
    private var attempted = false

    /**
     * Converte la spesa in un primo proprietario residente. Chiamare una sola volta, dopo aver liberato
     * gli intermedi. Senza misura resta la stima. Un costo minore restituisce subito il margine.
     * Se il costo maggiore non entra, torna null: il costo reale resta in volo fino allo scarto
     * della risorsa e a [close]. Una somma oltre Long.MAX_VALUE è una misura invalida e lascia intatta la riserva.
     */
    fun confirm(actualBytes: Long = estimateBytes): Resident? = synchronized(lock) {
      check(charged > 0 && !attempted) { "Prenotazione già chiusa o confermata" }
      require(actualBytes > 0)
      val delta = actualBytes - charged
      require(delta <= Long.MAX_VALUE - process.used) { "Il totale misurato non è rappresentabile in byte" }
      if (delta > 0 && !admit(category, delta)) {
        change(category, delta, delta)
        charged = actualBytes
        attempted = true
        return null
      }

      val owner = Resident(Shared(category, actualBytes))
      change(category, delta, -charged)
      charged = 0
      attempted = true
      owner
    }

    /** Restituisce una prenotazione ancora in volo. Dopo la consegna al residente o una seconda close è innocua. */
    override fun close() = synchronized(lock) {
      if (charged != 0L) {
        change(category, -charged, -charged)
        charged = 0
      }
    }
  }

  internal class Shared(val category: FluidMemoryCategory, val bytes: Long, var owners: Long = 1)

  /** Un proprietario della risorsa fisica. Cache, UI e altri lettori hanno ciascuno il proprio handle. */
  inner class Resident internal constructor(private val shared: Shared) : Closeable {
    private var open = true

    /** Acquisisce un altro proprietario prima di consegnare la stessa risorsa a un altro utilizzatore. */
    fun retain(): Resident = synchronized(lock) {
      check(open) { "Proprietario già chiuso" }
      check(shared.owners < Long.MAX_VALUE)
      val owner = Resident(shared)
      shared.owners++
      owner
    }

    /** L'ultimo proprietario restituisce i byte. Chiamare quando questo utilizzatore ha davvero finito. */
    override fun close() = synchronized(lock) {
      if (open) {
        open = false
        shared.owners--
        if (shared.owners == 0L) change(shared.category, -shared.bytes, 0)
      }
    }
  }
}
