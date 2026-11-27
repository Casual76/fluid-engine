package dev.antigravity.fluidengine.foundation.lab

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// F8.24 · Il Laboratorio (`flag-graduali`, rivista da Alessio): le funzioni sperimentali che l'utente accende da sé, da una
// pagina «Laboratorio» nelle impostazioni, come la pagina «Feature Flags» di Safari (Impostazioni › App › Safari ›
// Avanzate › Feature Flags). Niente rete e niente flag remoti nuovi: «il flag remoto non mi interessa» (Alessio, nota
// sulla scheda). Il kill switch e i flag che PampAI usa per gli incidenti restano in `engine-config`, dove sono.
//
// Qui la parte senza interfaccia: l'elenco delle funzioni, la scelta dell'utente e la sua memoria. La pagina e la riga
// delle impostazioni stanno in `engine-ui` (`ui.fluid.lab`). Sta in `engine-foundation` perché la legge anche il codice
// che non disegna (un repository che sceglie il motore nuovo, un lavoro in sottofondo).

/**
 * Una funzione sperimentale: una cosa nuova dell'app che l'utente può provare prima che sia finita (F8.24).
 *
 * @param key il nome stabile della funzione, quello che resta nella memoria: lettere minuscole, cifre, `-`, `_` e `.`, da
 *   1 a 48 caratteri. Cambiarlo spegne la funzione a chi l'aveva accesa.
 * @param title il nome nella pagina («Nuovo editor delle note»).
 * @param summary una o due frasi su cosa cambia, nelle parole dell'utente.
 * @param group il gruppo della pagina («Interfaccia», «Prestazioni»); nulla per il gruppo senza titolo, in cima.
 * @param defaultEnabled com'è senza una scelta dell'utente. Di serie spenta: una funzione in prova si accende per scelta.
 * @param applies quando la scelta vale: subito, o al prossimo avvio per ciò che si decide una volta sola (il motore di un
 *   database, il materiale del vetro prima del primo fotogramma).
 * @param expires l'ultimo giorno della prova. Dopo, la funzione sparisce dalla pagina e torna com'è di serie: o è finita
 *   nell'app, o è stata tolta. Come le voci di Safari, che spariscono quando la funzione esce.
 */
class FluidExperiment(
  val key: String,
  val title: String,
  val summary: String,
  val group: String? = null,
  val defaultEnabled: Boolean = false,
  val applies: FluidExperimentApplies = FluidExperimentApplies.Immediately,
  val expires: LocalDate? = null,
) {
  init {
    require(FluidLab.isValidKey(key)) { "Nome di funzione non valido: «$key» (a-z, 0-9, -, _, ., da 1 a 48)" }
  }

  /** Vero se la prova è finita il giorno [today] (il giorno di [expires] vale ancora). */
  fun isExpired(today: LocalDate): Boolean = expires != null && today.isAfter(expires)

  override fun equals(other: Any?): Boolean = other is FluidExperiment && other.key == key
  override fun hashCode(): Int = key.hashCode()
  override fun toString(): String = "FluidExperiment($key)"
}

/** Quando vale la scelta di una [FluidExperiment]. */
enum class FluidExperimentApplies {
  /** Subito: chi legge [FluidLab.isEnabled] dopo la scelta vede il valore nuovo. */
  Immediately,

  /**
   * Al prossimo avvio: [FluidLab.isEnabled] resta quello di quando il processo è partito, e la pagina dice «Al prossimo
   * avvio». Per ciò che l'app decide una volta sola: cambiarlo a metà lascerebbe l'app in uno stato misto.
   */
  NextLaunch,
}

/**
 * Lo stato del Laboratorio in un colpo solo, immutabile: la scelta di ogni funzione e quelle che aspettano il prossimo
 * avvio. Si sostituisce intero a ogni cambio (la regola di F7.21: uno stato letto da più fili è un valore intero).
 *
 * @param chosen il valore scelto di ogni funzione della pagina (anche quelle che valgono al prossimo avvio).
 * @param pendingRestart le funzioni la cui scelta è diversa dal valore con cui il processo è partito.
 */
class FluidLabState internal constructor(
  val chosen: Map<String, Boolean>,
  val pendingRestart: Set<String>,
) {
  /** Il valore scelto della funzione [key] (falso per una chiave che non c'è). */
  fun isChosen(key: String): Boolean = chosen[key] == true

  /** Quante funzioni sono accese, contando la scelta e non il valore in uso. */
  val enabledCount: Int get() = chosen.values.count { it }
}

/**
 * Dove si ricorda la scelta dell'utente. Di serie le `SharedPreferences` dell'app in un file suo ([FluidLab.create]);
 * [inMemory] per le prove. La lettura è sincrona di proposito: una funzione può decidere il primo fotogramma.
 */
interface FluidLabStore {
  /** Le scelte salvate: chiave → acceso. Una chiave assente vuol dire «nessuna scelta». */
  fun readAll(): Map<String, Boolean>

  /** Salva la scelta di [key]; nulla la cancella (torna al valore di serie). */
  fun write(key: String, enabled: Boolean?)

  /** Cancella tutte le scelte. */
  fun clear()

  companion object {
    /** Una memoria che dura quanto l'oggetto: per le prove. */
    fun inMemory(initial: Map<String, Boolean> = emptyMap()): FluidLabStore = InMemoryLabStore(initial)

    /**
     * Le `SharedPreferences` [preferences], anche quelle dell'app: si scrive con `apply()`, quindi senza aspettare il
     * disco. Le scelte stanno sotto le chiavi `fluid-lab.<nome>` e il Laboratorio non legge, non toglie e
     * non svuota le altre (R3: prima, nelle preferenze dell'app, la nascita del Laboratorio toglieva ogni booleano che non
     * conosceva e «Ripristina tutto» svuotava il file intero). Il file tutto suo di [FluidLab.create] resta senza prefisso.
     */
    fun preferences(preferences: SharedPreferences): FluidLabStore = PreferencesLabStore(preferences, LabKeyPrefix)
  }
}

/** Il prefisso delle chiavi del Laboratorio in preferenze che non sono solo sue ([FluidLabStore.preferences]). */
private const val LabKeyPrefix: String = "fluid-lab."

/**
 * Il Laboratorio di un'app: le [experiments] che l'utente può accendere, con la sua scelta ricordata (F8.24).
 *
 * Se ne fa **uno per processo** (nell'`Application`, o pigro in un oggetto dell'app) e lo si passa alla pagina
 * `FluidLabPage` di `engine-ui` e a chi legge. Le funzioni scadute ([FluidExperiment.expires]) non ci sono più: né nella
 * pagina né in [isEnabled], che per loro dà il valore di serie. La scadenza si guarda a ogni lettura col giorno di
 * [today] (di serie quello del telefono): una prova che finisce mentre l'app resta aperta torna di serie subito, non al
 * prossimo avvio (R3). La pagina la toglie quando si riapre.
 *
 * Il codice dell'app chiede `lab.isEnabled(NuovoEditor)`; la lettura è un accesso a una mappa, senza disco (il giorno si
 * chiede solo per le funzioni che hanno una scadenza).
 *
 * @param today il giorno di adesso, chiesto a ogni lettura: per le prove un giorno che si sposta.
 */
class FluidLab(
  experiments: List<FluidExperiment>,
  private val store: FluidLabStore,
  private val today: () -> LocalDate = LocalDate::now,
) {
  /** Il Laboratorio di un giorno fisso [today]: la scadenza si guarda sempre su quel giorno (le prove di prima). */
  constructor(experiments: List<FluidExperiment>, store: FluidLabStore, today: LocalDate) : this(experiments, store, { today })

  // Le funzioni vive alla nascita: quelle gia' scadute non ci sono proprio, come prima.
  private val started: List<FluidExperiment> = today().let { day -> experiments.filterNot { it.isExpired(day) }.distinctBy { it.key } }

  private val anyExpiry: Boolean = started.any { it.expires != null }

  /** Le funzioni della pagina, nell'ordine dato, senza quelle scadute oggi. */
  val experiments: List<FluidExperiment> get() = if (anyExpiry) started.filter(::isAlive) else started

  private val all: Map<String, FluidExperiment> = started.associateBy { it.key }

  /** Vero se la prova [experiment] non e' finita oggi. */
  private fun isAlive(experiment: FluidExperiment): Boolean = experiment.expires == null || !experiment.isExpired(today())

  /** La funzione di nome [key], se c'e' e non e' scaduta oggi. */
  private fun live(key: String): FluidExperiment? = all[key]?.takeIf(::isAlive)

  // Il valore con cui il processo è partito: quello che leggono le funzioni «al prossimo avvio».
  private val atLaunch: Map<String, Boolean>

  private val mutableState: MutableStateFlow<FluidLabState>

  init {
    val saved = store.readAll()
    // Le scelte di funzioni che non ci sono più si dimenticano: un nome tolto e poi riusato non deve trovarsi acceso.
    saved.keys.filter { it !in all }.forEach { store.write(it, null) }
    val chosen = started.associate { it.key to (saved[it.key] ?: it.defaultEnabled) }
    atLaunch = chosen
    mutableState = MutableStateFlow(FluidLabState(chosen, emptySet()))
  }

  /** Lo stato da osservare (la pagina, la riga delle impostazioni). */
  val state: StateFlow<FluidLabState> = mutableState.asStateFlow()

  /** La funzione [key], o nulla se non c'è (o è scaduta). */
  fun experiment(key: String): FluidExperiment? = live(key)

  /**
   * Vero se la funzione è accesa **ora**. Per una funzione [FluidExperimentApplies.NextLaunch] è il valore di quando il
   * processo è partito. Una funzione che il Laboratorio non conosce (o scaduta) dà il suo valore di serie.
   */
  fun isEnabled(experiment: FluidExperiment): Boolean {
    val known = live(experiment.key) ?: return experiment.defaultEnabled
    return if (known.applies == FluidExperimentApplies.NextLaunch) {
      atLaunch[known.key] ?: known.defaultEnabled
    } else {
      mutableState.value.chosen[known.key] ?: known.defaultEnabled
    }
  }

  /** [isEnabled] per nome: falso per un nome che non c'è. */
  fun isEnabled(key: String): Boolean = live(key)?.let(::isEnabled) ?: false

  /** Accende o spegne la funzione [experiment]. Una scelta uguale al valore di serie non resta scritta. */
  fun setEnabled(experiment: FluidExperiment, enabled: Boolean) {
    val known = live(experiment.key) ?: return
    val current = mutableState.value
    if (current.chosen[known.key] == enabled) return
    store.write(known.key, if (enabled == known.defaultEnabled) null else enabled)
    publish(current.chosen + (known.key to enabled))
  }

  /** Riporta tutte le funzioni al valore di serie, come «Ripristina tutto» di Safari. */
  fun resetAll() {
    store.clear()
    publish(started.associate { it.key to it.defaultEnabled })
  }

  /** Le funzioni accese ora (per una segnalazione: «Funzioni in prova accese: …»). */
  fun enabledExperiments(): List<FluidExperiment> = experiments.filter(::isEnabled)

  private fun publish(chosen: Map<String, Boolean>) {
    val pending = experiments
      .filter { it.applies == FluidExperimentApplies.NextLaunch && chosen[it.key] != atLaunch[it.key] }
      .map { it.key }
      .toSet()
    mutableState.value = FluidLabState(chosen, pending)
  }

  companion object {
    /** Il file delle `SharedPreferences` di [create], a parte da quelli dell'app. */
    const val DefaultFileName: String = "fluid-lab"

    private val KeyPattern = Regex("^[a-z0-9][a-z0-9._-]{0,47}$")

    /** Vero se [key] è un nome di funzione valido. */
    fun isValidKey(key: String): Boolean = KeyPattern.matches(key)

    /**
     * Il Laboratorio con la memoria nelle `SharedPreferences` dell'app (il file [fileName], escluso dal backup solo se
     * l'app lo esclude). La lettura del file avviene qui, una volta: crealo nell'`Application` o al primo uso.
     */
    fun create(context: Context, experiments: List<FluidExperiment>, fileName: String = DefaultFileName): FluidLab =
      FluidLab(experiments, PreferencesLabStore(context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE), prefix = ""))
  }
}

private class InMemoryLabStore(initial: Map<String, Boolean>) : FluidLabStore {
  private val values = java.util.concurrent.ConcurrentHashMap(initial)
  override fun readAll(): Map<String, Boolean> = HashMap(values)
  override fun write(key: String, enabled: Boolean?) {
    if (enabled == null) values.remove(key) else values[key] = enabled
  }
  override fun clear() = values.clear()
}

/**
 * Le scelte in `SharedPreferences`, sotto le chiavi che cominciano con [prefix]: vuoto solo per il file tutto del
 * Laboratorio ([FluidLab.create]), dove ogni booleano e' una scelta.
 */
private class PreferencesLabStore(private val preferences: SharedPreferences, private val prefix: String) : FluidLabStore {
  override fun readAll(): Map<String, Boolean> =
    preferences.all.mapNotNull { (key, value) ->
      if (!key.startsWith(prefix)) null else (value as? Boolean)?.let { key.substring(prefix.length) to it }
    }.toMap()

  override fun write(key: String, enabled: Boolean?) {
    preferences.edit().apply { if (enabled == null) remove(prefix + key) else putBoolean(prefix + key, enabled) }.apply()
  }

  override fun clear() {
    if (prefix.isEmpty()) {
      preferences.edit().clear().apply()
    } else {
      val ours = preferences.all.keys.filter { it.startsWith(prefix) }
      preferences.edit().apply { ours.forEach { remove(it) } }.apply()
    }
  }
}
