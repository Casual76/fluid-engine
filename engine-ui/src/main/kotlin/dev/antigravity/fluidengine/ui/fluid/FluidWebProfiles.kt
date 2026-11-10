package dev.antigravity.fluidengine.ui.fluid

// I profili dei documenti di FluidWebContent: la macchina a stati, senza una riga di Android.
// Kotlin puro di proposito: ogni transizione e' provata sul JVM (FluidWebProfilesTest). Le chiamate vere al
// provider stanno in FluidWebPlatform.kt, dietro [ProfilePlatform].

/** Il provider non sa dare a una vista un profilo suo (manca MULTI_PROFILE): il contenitore fallisce chiuso. */
internal class IsolationUnavailable : RuntimeException()

/** Dove [RendererClosing] riconsegna il profilo di una vista chiusa. Lo realizza [ClosedProfilePool]. */
internal interface ProfileReturn {
  /** La vista e' distrutta e il provider ha confermato la morte del renderer chiesta dal contenitore. */
  fun recycle(name: String)

  /** Ogni altro caso: il profilo non serve piu' nessun documento in questo processo. */
  fun retire(name: String)
}

/** Cio' che [ClosedProfilePool] chiede alla piattaforma. [V] e' la vista: il pool la passa soltanto.
 *
 * Una sola realizzazione vera (`AndroidProfiles`, su AndroidX WebKit) e una finta nelle prove JVM. Ogni metodo
 * viene chiamato sul thread principale, e [clearData] e [schedule] devono richiamare li'.
 */
internal interface ProfilePlatform<V> {
  /** Lancia se il chiamante non e' sul thread principale. */
  fun requireMainThread()

  /** Il provider sa dare profili separati (MULTI_PROFILE). Senza, nessuna vista nasce. */
  val multiProfile: Boolean

  /** Il provider sa cancellare i dati di un profilo (DELETE_BROWSING_DATA). Senza, nessun profilo si riusa. */
  val deleteBrowsingData: Boolean

  /** La parte casuale del nome di un profilo nuovo, mai ripetuta. */
  fun newId(): String

  /** I nomi di tutti i profili che il provider conosce, anche quelli di altre WebView dell'app. */
  fun existingNames(): Collection<String>

  /** Da' a [view] il profilo [name] (creandolo se manca) e ne spegne i cookie. Puo' lanciare. */
  fun assign(view: V, name: String)

  /** Cancella cache, cookie e storage di [name]; a cose fatte chiama [done], una volta, mai prima di tornare.
   * Risponde false, senza cancellare nulla e senza chiamare [done], se il profilo non c'e'. Puo' lanciare.
   */
  fun clearData(name: String, done: () -> Unit): Boolean

  /** Elimina il profilo. Lancia [IllegalStateException] se e' caricato in questo processo. */
  fun delete(name: String)

  /** Esegue [action] fra [delayMillis] millisecondi. */
  fun schedule(delayMillis: Long, action: () -> Unit)
}

/** I profili dei documenti: uno per vista alla volta, riusato solo dopo una pulizia confermata.
 *
 * Un profilo caricato non si puo' cancellare nello stesso processo (lo vieta AndroidX), e resta caricato
 * anche a renderer morto: sull'emulatore di prova ogni profilo costa al processo dell'app circa 24
 * descrittori di file, 2 thread e 0,7 MB, piu' un centinaio di kilobyte su disco. Un profilo nuovo per ogni
 * documento farebbe quindi crescere l'app a ogni mail aperta. Per questo i profili sono pochi: un profilo
 * torna disponibile quando, e solo quando,
 * 1. la sua vista e' stata distrutta;
 * 2. il suo renderer e' stato terminato dal contenitore e il provider ne ha confermato la morte
 *    ([RendererClosing]): nessuna memoria del renderer passa al documento successivo;
 * 3. `deleteBrowsingData` ha risposto: cache di rete, cookie e ogni storage del profilo sono cancellati.
 * In ogni altro caso (renderer morto da se', conferma mai arrivata, funzione mancante nel provider) il
 * profilo viene ritirato: nessuna vista lo riusa in questo processo.
 *
 * La regola e' scritta negli stati ([Stage]), e queste sono tutte le transizioni:
 *
 * | da | a | quando |
 * |---|---|---|
 * | (nuovo) o `Clean` | `Retired` | [isolate] sceglie il nome; resta cosi' se [ProfilePlatform.assign] lancia |
 * | `Retired` | `InUse` | [isolate]: la vista ha il profilo, con i cookie spenti |
 * | `InUse` | `Cleaning` | [recycle]: la pulizia e' partita |
 * | `InUse` | `Retired` | [recycle] senza `deleteBrowsingData`, senza il profilo o con un errore; [retire] |
 * | `Cleaning` | `Clean` | la pulizia ha risposto: il nome entra in coda fra i disponibili |
 * | qualunque | `Retired` | [retire] |
 *
 * Un nome entra fra i disponibili solo da `Cleaning`, dove arriva solo da `InUse`, e ne esce appena una vista
 * lo prende. Quindi due viste vive non condividono mai un profilo, e un profilo ritirato non torna: da
 * `Retired` si esce solo dentro [isolate], per il nome appena scelto. Una pulizia che non risponde lascia il
 * profilo in `Cleaning` per sempre (nessuno lo riusa); una risposta tardiva, doppia o per un profilo nel
 * frattempo ritirato non cambia nulla. I disponibili si danno dal piu' vecchio: nessuno resta indietro.
 *
 * RESIDUO NOTO: il provider non offre un modo di cancellare l'elenco degli indirizzi visitati di un profilo.
 * Dopo la pulizia vi restano gli indirizzi dei documenti precedenti, casuali e nuovi per ogni vista (un
 * documento non li puo' conoscere), e cio' che una pagina con script vi avesse aggiunto con pushState.
 *
 * Chi possiede cosa: il pool possiede solo nomi e stati, mai una vista o un'Activity; la vista e' di
 * `ClosedWebView`, il renderer di [RendererClosing]. Ne esiste uno per processo (`ClosedProfiles`).
 * Tutto sul thread principale, e ogni ingresso lo controlla, comprese le risposte della piattaforma.
 */
internal class ClosedProfilePool<V>(private val platform: ProfilePlatform<V>) : ProfileReturn {
  /** Dove si trova un profilo creato in questo processo. */
  enum class Stage { InUse, Cleaning, Clean, Retired }

  /** Ogni profilo creato in questo processo, dal primo momento. Quelli del processo precedente non ci sono mai. */
  private val stages = HashMap<String, Stage>()
  private val clean = ArrayDeque<String>()
  private val leftovers = ArrayDeque<String>()
  private var pruned = false

  /** Lo stato di [name]; nullo se questo processo non lo ha mai creato. Per le prove e per chi indaga. */
  fun stage(name: String): Stage? = stages[name]

  /** Da' a [view] un profilo suo e senza cookie; restituisce il nome. Senza MULTI_PROFILE fallisce chiuso. */
  fun isolate(view: V): String {
    platform.requireMainThread()
    if (!platform.multiProfile) throw IsolationUnavailable()
    pruneAfterProcessRecreation()
    val name = takeClean() ?: (Prefix + platform.newId())
    // Ritirato finche' la vista non lo ha davvero: se la chiamata qui sotto fallisce, non torna disponibile.
    stages[name] = Stage.Retired
    platform.assign(view, name)
    stages[name] = Stage.InUse
    return name
  }

  private fun takeClean(): String? {
    while (true) {
      val name = clean.removeFirstOrNull() ?: return null
      if (stages[name] == Stage.Clean) return name
    }
  }

  /** Il profilo non ha piu' vista ne' renderer: lo pulisce, e solo a pulizia finita lo rende disponibile. */
  override fun recycle(name: String) {
    platform.requireMainThread()
    if (stages[name] != Stage.InUse) return
    // Ritirato finche' la pulizia non e' partita davvero, e di nuovo se non parte.
    stages[name] = Stage.Retired
    if (!platform.multiProfile || !platform.deleteBrowsingData) return
    try {
      stages[name] = Stage.Cleaning
      if (!platform.clearData(name) { cleaned(name) }) stages[name] = Stage.Retired
    } catch (_: RuntimeException) {
      stages[name] = Stage.Retired // Senza una pulizia confermata il profilo resta ritirato.
    }
  }

  private fun cleaned(name: String) {
    platform.requireMainThread()
    if (stages[name] != Stage.Cleaning) return
    stages[name] = Stage.Clean
    clean.addLast(name)
  }

  /** Il profilo non verra' piu' usato in questo processo; [pruneAfterProcessRecreation] lo elimina al prossimo. */
  override fun retire(name: String) {
    platform.requireMainThread()
    stages[name] = Stage.Retired
    if (!platform.multiProfile) return
    try {
      platform.delete(name)
    } catch (_: IllegalStateException) {
      // Chromium tiene caricato il profilo anche dopo destroy(), e AndroidX vieta di cancellarlo in
      // questo processo: non e' un errore dell'host e non deve far cadere l'Activity.
    }
  }

  /** Al primo host del processo: elenca i profili lasciati dal processo precedente, per eliminarli con calma.
   *
   * Non puo' toccare un profilo di questo processo (in uso, in pulizia, disponibile o ritirato), per tre
   * motivi che non dipendono l'uno dall'altro: l'elenco si prende prima di creare il primo profilo, e non si
   * riprende piu'; [pruneOne] salta ogni nome che questo processo ha creato; e il provider rifiuta di
   * cancellare un profilo caricato, cioe' ogni profilo usato qui. Recupera anche i profili lasciati da una
   * terminazione senza onDispose, e non tocca quelli di altre WebView dell'app (non hanno il prefisso).
   *
   * Cancellarli costa qualche millisecondo l'uno, sul thread principale perche' l'API lo richiede: si comincia
   * qualche secondo dopo il primo documento e se ne elimina uno per volta. Se il processo finisce prima, i
   * rimasti si ritrovano nell'elenco del processo successivo.
   */
  private fun pruneAfterProcessRecreation() {
    if (pruned) return
    pruned = true
    for (name in platform.existingNames()) {
      if (name.startsWith(Prefix)) leftovers.addLast(name)
    }
    if (leftovers.isNotEmpty()) platform.schedule(PruneStartMillis, ::pruneOne)
  }

  private fun pruneOne() {
    platform.requireMainThread()
    if (!platform.multiProfile) return
    val name = leftovers.removeFirstOrNull() ?: return
    if (name !in stages) {
      try {
        platform.delete(name)
      } catch (_: IllegalStateException) {
        // Caricato da qualcun altro in questo processo: non e' nostro da cancellare adesso.
      }
    }
    if (leftovers.isNotEmpty()) platform.schedule(PrunePauseMillis, ::pruneOne)
  }

  internal companion object {
    const val Prefix = "fluid-content-"
    /** Attesa prima di cominciare a eliminare i profili del processo precedente: il primo documento non li ha fra i piedi. */
    const val PruneStartMillis = 4_000L
    /** Pausa fra un profilo eliminato e il successivo: sul thread principale ce n'e' al piu' uno ogni tanto. */
    const val PrunePauseMillis = 50L
  }
}
