package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import java.util.Collections

/**
 * F7.20 · Le righe del pannello info che cambiano dopo l'apertura: il conto delle righe di un CSV che finisce in
 * sottofondo, le pagine di un documento Office contate mentre si impagina. È una lista di sola lettura che è anche uno
 * stato: chi la legge in composizione (`fluidDataViewerInfo`, `officeInfo`) si aggiorna da solo quando cambia.
 *
 * **Si cambia da qualunque filo**, con [replace], [add] o [update]. Ogni cambio mette una lista intera e immutabile al
 * posto della vecchia, in un passo solo; due cambi da fili diversi si mettono in fila e nessuno si perde.
 *
 * **Si legge senza istantanee.** [current] dà la lista di adesso, ferma: una lettura sola, sempre una lista intera
 * (quella di prima o quella nuova, mai una a metà). Anche `for`, `filter`, `any` e gli altri giri sulla lista leggono
 * lo stato una volta sola. Per una copia da tenere, usa [current], non `toList()`.
 *
 * Perché non una `SnapshotStateList`, come prima: Compose 1.10 legge l'id di un record due volte quando cerca il valore
 * da dare (`readable()`), e se nel mezzo un altro filo ricicla proprio quel record, il lettore prende il record che
 * l'altro filo sta riempiendo. Una `SnapshotStateList` si legge un elemento alla volta, con molte letture per giro:
 * copiata in un'istantanea (`Snapshot.takeSnapshot`) mentre il conto la sostituiva, dava
 * `ConcurrentModificationException`, liste vuote a metà e indici fuori misura (`DataDetailsReplaceTest`). Qui ogni
 * lettura prende un valore solo, e ogni valore che un record può contenere è una lista intera.
 *
 * ```kotlin
 * val details = FluidViewerLiveDetails(righe(conto = null))                       // «righe in conteggio»
 * val grid = MiaGriglia(file, onCounted = { n -> details.replace(righe(n)) })     // dal filo del conto
 * return FluidViewerOpened(FluidViewerContent.Grid(grid), details)
 * ```
 */
class FluidViewerLiveDetails(initial: List<FluidViewerDetail> = emptyList()) : AbstractList<FluidViewerDetail>() {
  private val lock = Any()

  /** L'ultima lista scritta: la base dei cambi, fuori dagli stati di Compose (vedi il perché nella classe). */
  @Volatile private var latest: List<FluidViewerDetail> = frozen(initial)

  private val state = mutableStateOf(latest, referentialEqualityPolicy())

  /** Le righe di adesso, intere e immutabili. In composizione la lettura si iscrive ai cambi. */
  val current: List<FluidViewerDetail> get() = state.value

  /** Mette [items] al posto delle righe di adesso, in un passo solo. Righe uguali a quelle di adesso non cambiano niente. */
  fun replace(items: List<FluidViewerDetail>) = update { items }

  /** Aggiunge [detail] in fondo. */
  fun add(detail: FluidViewerDetail) = update { it + detail }

  /**
   * Cambia le righe con [transform], che riceve quelle di adesso e dà le nuove. I cambi di fili diversi passano uno alla
   * volta, quindi [transform] vede sempre l'ultimo cambio: «aggiungi se manca» o «riempi se è vuota» sono sicuri.
   */
  fun update(transform: (List<FluidViewerDetail>) -> List<FluidViewerDetail>) {
    synchronized(lock) {
      val next = frozen(transform(latest))
      if (next == latest) return
      latest = next
      // Un'istantanea propria: chi osserva gli stati (la composizione) lo sa subito, anche da un filo in sottofondo.
      Snapshot.withMutableSnapshot { state.value = next }
    }
  }

  override val size: Int get() = current.size

  override fun get(index: Int): FluidViewerDetail = current[index]

  // I giri sulla lista leggono lo stato una volta sola: una sostituzione a metà giro non si vede.
  override fun iterator(): Iterator<FluidViewerDetail> = current.iterator()

  override fun listIterator(): ListIterator<FluidViewerDetail> = current.listIterator()

  override fun listIterator(index: Int): ListIterator<FluidViewerDetail> = current.listIterator(index)

  private companion object {
    /**
     * Una copia che nessuno può cambiare. L'involucro di `Collections` tiene la lista in un campo `final`: anche un
     * filo che la trova senza sincronizzarsi (un record di Compose letto in corsa) la vede intera.
     */
    fun frozen(items: List<FluidViewerDetail>): List<FluidViewerDetail> =
      if (items.isEmpty()) emptyList() else Collections.unmodifiableList(ArrayList(items))
  }
}
