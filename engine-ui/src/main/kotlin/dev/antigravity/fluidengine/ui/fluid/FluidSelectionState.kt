package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Cosa e' scelto, in una delle due forme che una selezione multipla puo' avere.
 *
 * - [Explicit]: sono scelte **queste** chiavi. E' la forma di una persona che tocca tre righe.
 * - [AllExcept]: e' scelto **tutto, tranne** queste chiavi. E' la forma di «Seleziona tutto» su una
 *   lista paginata: l'insieme scelto e' «l'intero risultato della vista» e le sue chiavi **non sono
 *   in memoria** (50.000 righe in un database, tre schermate caricate). Rappresentarlo come un
 *   insieme di chiavi vorrebbe dire o scegliere in silenzio solo le righe caricate, o caricarle tutte per
 *   scriverne la lista.
 */
sealed interface FluidSelectionMode<out K : Any> {

  /** Sono scelte queste chiavi, nell'ordine in cui sono state scelte. */
  @Immutable
  data class Explicit<out K : Any>(val keys: Set<K>) : FluidSelectionMode<K>

  /**
   * E' scelto tutto tranne [excluded].
   *
   * @property snapshot il testimone che l'app lega al momento del tocco («query + istante»): vedi
   *   [FluidSelectionState.selectAll]. L'engine non lo interpreta, lo conserva e lo restituisce.
   */
  @Immutable
  data class AllExcept<out K : Any>(val excluded: Set<K>, val snapshot: String? = null) : FluidSelectionMode<K>
}

/**
 * Lo stato della selezione multipla di una lista: cosa e' scelto, e come si entra e si esce.
 *
 * **Perche' esiste.** Ogni app con una lista selezionabile aveva uno stato suo (`Set<Id>` dentro un
 * ViewModel, `mutableStateListOf` nel composable) e tre pezzi sbagliati a modo loro: «Seleziona tutto»
 * che sceglieva solo le righe caricate (e l'azione poi riguardava il quinto di quello che la persona
 * credeva), il back che usciva dalla pagina invece che dalla selezione, e una rotazione che azzerava
 * tutto. Qui lo stato e' uno, e gli ultimi due sono risolti dal [rememberFluidSelectionState] e dal
 * [FluidSelectionBackHandler].
 *
 * **«Seleziona tutto» e le liste paginate.** [selectAll] non conta niente e non cerca niente: passa
 * a [FluidSelectionMode.AllExcept] con un insieme di esclusioni vuoto. Da li' ogni riga, caricata o no,
 * e' scelta finche' qualcuno non la [toggle] fuori; il **numero** lo fornisce chi conosce il totale
 * (la query del database): [countIn]. L'engine non puo' saperlo e non deve: «quante righe ha il
 * risultato» e' una domanda al repository, e un conteggio tenuto qui dentro invecchierebbe alla prima
 * riga arrivata.
 *
 * **Il risultato «al momento del tocco».** «Seleziona tutto» non vuol dire «e tutto quello che arrivera'
 * dopo»: la persona ha scelto quel risultato, non uno che cambia sotto le sue dita. L'engine non
 * conosce la query, quindi la sua parte e' offrire il posto per il testimone ([selectAll] riceve
 * `snapshot`, [snapshot] lo restituisce) e una domanda per le righe nuove ([isSelected] con
 * `belongsToSnapshot = false`): l'app dice quali righe erano gia' li' (per esempio quelle con data di
 * arrivo non successiva al taglio) e quelle arrivate dopo risultano **non** scelte. Il conteggio e
 * l'azione di massa li risolve l'app sul database con lo stesso testimone: nessun elenco di 50.000
 * identificatori passa da questo oggetto.
 *
 * **Cosa NON fa.** Non sa nulla di account, query o permessi; non decide se un'azione e' ammessa su
 * una selezione `AllExcept`; non conta le righe. Il cambio di account o di filtro e' un evento
 * dell'app e lo comunica: [clear], o [clearIfSnapshotDiffers] se il testimone e' cambiato.
 *
 * **Uscire.** La selezione e' «attiva» finche' scelta ([isActive]): toccando via l'ultima riga si
 * esce da sola, come in tutte le app. Con `AllExcept` il motore non sa quando le esclusioni hanno
 * eguagliato il totale: lo dice chi ha il totale con [exitIfEmpty]. [clear] e il back
 * ([FluidSelectionBackHandler]) escono sempre.
 *
 * **Righe e ricomposizione.** [isSelected] legge lo stato intero: a ogni scelta ricompongono le righe
 * che lo leggono (quelle visibili: decine, non migliaia). Una riga costosa legge il suo valore con
 * `remember(key) { derivedStateOf { state.isSelected(key) } }` e ricompone solo quando cambia il suo.
 *
 * **Thread.** Come ogni stato di Compose: si modifica dal thread principale.
 *
 * Una persona sola non puo' scegliere due righe uguali: le chiavi sono insiemi (`K` deve avere
 * `equals`/`hashCode` veri: un id, non un oggetto mutabile) e **stabili fra aggiornamenti dei dati**
 * (un id di database, non l'indice nella lista: una riga arrivata in cima sposterebbe la scelta sulla riga
 * sbagliata).
 */
@Stable
class FluidSelectionState<K : Any>(initial: FluidSelectionMode<K> = FluidSelectionMode.Explicit(emptySet())) {

  private var current: FluidSelectionMode<K> by mutableStateOf(initial)

  /** Cosa e' scelto, come valore immutabile: salvabile, confrontabile, passabile a un'azione di massa. */
  val mode: FluidSelectionMode<K> get() = current

  /**
   * C'e' una selezione in corso: almeno una chiave scelta, o «tutto tranne» qualcuno. E' cio' che
   * fa comparire la capsula di azioni e il titolo «N selezionate», e che abilita il back.
   */
  val isActive: Boolean
    get() = when (val m = current) {
      is FluidSelectionMode.Explicit -> m.keys.isNotEmpty()
      is FluidSelectionMode.AllExcept -> true
    }

  /** E' la forma «tutto tranne»: il numero scelto dipende dal totale ([countIn]). */
  val isAllExcept: Boolean get() = current is FluidSelectionMode.AllExcept

  /** Il testimone dell'ultimo [selectAll]; null in forma esplicita o se l'app non ne ha passato uno. */
  val snapshot: String?
    get() = (current as? FluidSelectionMode.AllExcept)?.snapshot

  /**
   * Quante righe sono scelte, dato il [total] delle righe del risultato che l'app conosce.
   *
   * In forma esplicita il totale non serve (sono le chiavi scelte: se fra queste ci sono righe sparite
   * dai dati, e' [retainOnly] o [forget] che le tolgono). In forma «tutto tranne» e' `total − esclusioni`,
   * mai sotto zero. **Il totale lo calcola l'app con lo stesso testimone della selezione**
   * (stessa query, stesso taglio): se lo calcola due volte con due query diverse il numero sara'
   * quello giusto per la seconda e sbagliato per quello che la persona ha scelto.
   */
  fun countIn(total: Int): Int = when (val m = current) {
    is FluidSelectionMode.Explicit -> m.keys.size
    is FluidSelectionMode.AllExcept -> (total - m.excluded.size).coerceAtLeast(0)
  }

  /**
   * [key] e' scelta?
   *
   * @param belongsToSnapshot per la forma «tutto tranne»: false per una riga **arrivata dopo** il
   *   tocco su «Seleziona tutto» (non e' parte del risultato scelto, quindi non e' scelta). In forma
   *   esplicita non conta: una chiave e' scelta perche' la persona l'ha toccata.
   */
  fun isSelected(key: K, belongsToSnapshot: Boolean = true): Boolean = when (val m = current) {
    is FluidSelectionMode.Explicit -> key in m.keys
    is FluidSelectionMode.AllExcept -> belongsToSnapshot && key !in m.excluded
  }

  /** Scelta se non lo e', non scelta se lo e'. Il tocco su un avatar o su una riga in selezione. */
  fun toggle(key: K) {
    setSelected(key, !isSelected(key))
  }

  /** Sceglie [key] se non lo era. */
  fun select(key: K) {
    setSelected(key, true)
  }

  /** Toglie [key] dalla selezione se c'era. */
  fun deselect(key: K) {
    setSelected(key, false)
  }

  /**
   * Porta [key] allo stato [selected]. In forma esplicita aggiunge o toglie la chiave; in forma
   * «tutto tranne» toglie o aggiunge **l'esclusione**.
   */
  fun setSelected(key: K, selected: Boolean) {
    current = when (val m = current) {
      is FluidSelectionMode.Explicit ->
        if (selected) {
          if (key in m.keys) m else FluidSelectionMode.Explicit(m.keys + key)
        } else {
          if (key in m.keys) FluidSelectionMode.Explicit(m.keys - key) else m
        }

      is FluidSelectionMode.AllExcept ->
        if (selected) {
          if (key in m.excluded) m.copy(excluded = m.excluded - key) else m
        } else {
          if (key in m.excluded) m else m.copy(excluded = m.excluded + key)
        }
    }
  }

  /**
   * «Seleziona tutto»: passa alla forma «tutto tranne» senza esclusioni, **senza materializzare nessuna
   * chiave**. Vale per l'intero risultato, anche le righe non caricate; vedi la documentazione della classe.
   *
   * Le scelte fatte prima si perdono: in forma «tutto tranne» sono tutte incluse.
   *
   * @param snapshot il testimone che l'app lega a questo risultato («query + istante del tocco»), che
   *   [snapshot] restituira' e [clearIfSnapshotDiffers] confronta. Una stringa, perche' deve sopravvivere
   *   alla morte del processo; l'app ne decide il formato.
   */
  fun selectAll(snapshot: String? = null) {
    current = FluidSelectionMode.AllExcept(emptySet(), snapshot)
  }

  /** Esce dalla selezione: niente e' scelto. */
  fun clear() {
    if (current != EmptyExplicit) current = FluidSelectionMode.Explicit(emptySet())
  }

  /**
   * Esce se la selezione e' «tutto tranne» e il [total] non lascia niente di scelto (le esclusioni hanno
   * raggiunto il totale: la persona ha tolto riga per riga tutto cio' che aveva scelto). Da chiamare
   * quando cambiano il conteggio o le esclusioni, perche' l'engine non conosce il totale.
   * In forma esplicita non fa niente: li' una selezione vuota e' gia' non attiva.
   */
  fun exitIfEmpty(total: Int) {
    if (isAllExcept && countIn(total) == 0) clear()
  }

  /**
   * Toglie le chiavi **che non esistono piu'** nei dati, dopo un aggiornamento: l'azione e' stata fatta,
   * la riga e' stata spostata, l'ha cancellata un'altra app.
   *
   * Per una lista **tutta in memoria**: passa le chiavi che i dati hanno ancora, e tutto il resto
   * esce dalla selezione (anche dalle esclusioni: una riga che non c'e' piu' non e' piu' «tranne»,
   * e lasciarla sottrarrebbe una riga che il totale non conta piu').
   * Per una lista **paginata** non va usata con le sole righe caricate (toglierebbe le scelte sulle
   * righe non caricate): vedi [forget].
   */
  fun retainOnly(valid: Set<K>) {
    current = when (val m = current) {
      is FluidSelectionMode.Explicit ->
        if (m.keys.all { it in valid }) m else FluidSelectionMode.Explicit(m.keys.filterTo(LinkedHashSet()) { it in valid })

      is FluidSelectionMode.AllExcept ->
        if (m.excluded.all { it in valid }) m else m.copy(excluded = m.excluded.filterTo(LinkedHashSet()) { it in valid })
    }
  }

  /**
   * Toglie le chiavi che l'app **sa** non esistere piu' (le righe che ha appena archiviato, eliminato,
   * spostato), senza guardare le altre: e' la forma per le liste paginate, dove l'elenco di cio'
   * che c'e' ancora non esiste. Vale per le due forme, per la stessa ragione di [retainOnly].
   */
  fun forget(keys: Collection<K>) {
    if (keys.isEmpty()) return
    current = when (val m = current) {
      is FluidSelectionMode.Explicit -> {
        val remaining = m.keys - keys.toSet()
        if (remaining.size == m.keys.size) m else FluidSelectionMode.Explicit(remaining)
      }

      is FluidSelectionMode.AllExcept -> {
        val remaining = m.excluded - keys.toSet()
        if (remaining.size == m.excluded.size) m else m.copy(excluded = remaining)
      }
    }
  }

  /**
   * Esce dalla selezione «tutto tranne» se il testimone con cui fu fatta **non e' piu'** [currentSnapshot]: il
   * risultato che la persona aveva scelto non e' piu' quello che vede (ha cambiato filtro, account, ricerca),
   * e un'azione di massa lo applicherebbe a righe che non ha guardato. Non tocca la forma esplicita:
   * le chiavi sono righe precise, valide in qualunque vista.
   */
  fun clearIfSnapshotDiffers(currentSnapshot: String?) {
    val m = current
    if (m is FluidSelectionMode.AllExcept && m.snapshot != currentSnapshot) clear()
  }

  companion object {
    private val EmptyExplicit: FluidSelectionMode<Nothing> = FluidSelectionMode.Explicit(emptySet())

    /**
     * Il [Saver] di uno stato: la forma, il testimone e le chiavi, come una lista che il `Bundle` sa
     * tenere. Sopravvive alla rotazione e alla morte del processo.
     *
     * **Tetto.** Un `Bundle` ha circa un mega per tutta l'app; una selezione esplicita di diecimila
     * chiavi, o di diecimila esclusioni, lo farebbe esplodere a tempo di salvataggio
     * (`TransactionTooLargeException`) — e il crash ci sarebbe quando l'app va in secondo piano, non
     * quando la persona sceglie. Oltre [maxSavedKeys] chiavi lo stato si salva **vuoto**: la selezione e'
     * transitoria, e perderla e' un fastidio mentre il crash e' un guasto.
     *
     * @param keySaver come salvare una chiave. Null: la chiave e' gia' salvabile (`String`, `Int`,
     *   `Long`, o un `Serializable`) e si salva com'e'. Una chiave che non lo e' (un oggetto) richiede il
     *   suo [Saver]; **non** mettere nel `Bundle` l'oggetto di dominio, solo il suo identificatore.
     */
    fun <K : Any> saver(
      keySaver: Saver<K, out Any>? = null,
      maxSavedKeys: Int = DefaultMaxSavedKeys,
    ): Saver<FluidSelectionState<K>, Any> {
      @Suppress("UNCHECKED_CAST")
      val keys = keySaver as Saver<K, Any>?
      return Saver(
        save = { state ->
          val (kind, set, snapshot) = when (val m = state.mode) {
            is FluidSelectionMode.Explicit -> Triple(KindExplicit, m.keys, null)
            is FluidSelectionMode.AllExcept -> Triple(KindAllExcept, m.excluded, m.snapshot)
          }
          val saved = ArrayList<Any>(set.size + 3)
          if (set.size > maxSavedKeys) {
            saved += KindExplicit
            saved += ""
          } else {
            saved += kind
            saved += snapshot.orEmpty()
            for (key in set) {
              saved += if (keys == null) key else with(keys) { save(key) } ?: continue
            }
          }
          saved
        },
        restore = { raw ->
          @Suppress("UNCHECKED_CAST")
          val list = raw as List<Any>
          val kind = list[0] as Int
          val snapshot = (list[1] as String).takeIf { it.isNotEmpty() }
          val restored = LinkedHashSet<K>(maxOf(0, list.size - 2))
          for (i in 2 until list.size) {
            @Suppress("UNCHECKED_CAST")
            val key = if (keys == null) list[i] as K else keys.restore(list[i])
            if (key != null) restored += key
          }
          FluidSelectionState(
            if (kind == KindAllExcept) {
              FluidSelectionMode.AllExcept(restored, snapshot)
            } else {
              FluidSelectionMode.Explicit(restored)
            },
          )
        },
      )
    }

    private const val KindExplicit = 0
    private const val KindAllExcept = 1

    /** Quante chiavi al massimo si salvano di serie: mille sono piu' di quante ne tocchi a mano una persona, e restano ben sotto il limite di un `Bundle`. */
    const val DefaultMaxSavedKeys: Int = 1_000
  }
}

/**
 * Lo stato di una selezione che resta com'e' alla rotazione e alla morte del processo
 * ([FluidSelectionState.saver]).
 *
 * @param keySaver come salvare le chiavi se non sono gia' salvabili: vedi [FluidSelectionState.saver].
 */
@Composable
fun <K : Any> rememberFluidSelectionState(keySaver: Saver<K, out Any>? = null): FluidSelectionState<K> =
  rememberSaveable(saver = FluidSelectionState.saver(keySaver)) { FluidSelectionState() }

/**
 * Il back esce dalla selezione, e non da altro.
 *
 * Quando [state] e' attivo registra un `BackHandler` che la svuota; quando non lo e', non registra
 * niente e il back fa quello che faceva (chiudere la pagina).
 *
 * **L'ordine di composizione e' l'ordine del back.** Il `BackHandler` registrato **per ultimo** vince,
 * quindi questo va composto **dopo** il contenuto che ha i suoi (la pagina, il pannello laterale, l'host
 * di navigazione) e **prima** di chi deve avere la meglio su di lui: i menu e i fogli dell'engine
 * registrano il loro quando si aprono, dopo, e quindi un menu aperto sulla selezione si chiude per
 * primo (menu, poi selezione, poi pagina). Posato prima della pagina, il back chiude la pagina con la
 * selezione ancora accesa.
 *
 * @param enabled falso lo spegne senza toglierlo dall'albero (una pagina che non e' quella in primo piano).
 * @param onExit chiamato dopo aver svuotato la selezione (per riportare il titolo, nascondere la capsula).
 */
@Composable
fun FluidSelectionBackHandler(
  state: FluidSelectionState<*>,
  enabled: Boolean = true,
  onExit: (() -> Unit)? = null,
) {
  BackHandler(enabled = enabled && state.isActive) {
    state.clear()
    onExit?.invoke()
  }
}
