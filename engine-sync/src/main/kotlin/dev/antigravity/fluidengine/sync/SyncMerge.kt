package dev.antigravity.fluidengine.sync

import dev.antigravity.fluidengine.foundation.text.FluidTextDiff

/**
 * L'unione di due versioni di un documento cambiate a partire da una stessa base: quella del dispositivo (le modifiche non
 * ancora mandate) e quella del relay (le modifiche degli altri). E' una funzione pura, senza rete e senza registro.
 *
 * Per ogni campo, confrontando con la base:
 * - cambiato da una parte sola: si prende quello;
 * - cambiato da tutte e due allo stesso modo: uno solo;
 * - cambiato da tutte e due in modo diverso:
 *   - un valore da **ultimo che vince** prende lo stamp piu' grande ([SyncHlc]), su tutti i dispositivi lo stesso;
 *   - un [SyncValue.LongText] si **fonde** a tre vie, prima per righe e poi, dentro un passaggio conteso, per parole;
 *     due aggiunte nello stesso punto restano tutte e due (prima la piu' vecchia). Se le due parti hanno cambiato lo
 *     stesso passaggio in modo diverso, il campo tiene la versione con lo stamp piu' grande e mette l'altra fra le
 *     [SyncField.alternatives]: niente si perde, e l'app chiede la scelta.
 *
 * Una pietra tombale vince su tutto: un documento cancellato da una parte e' cancellato.
 *
 * **Idempotente.** Fondere una versione con se stessa, o con una che la contiene gia', non cambia niente: un invio arrivato
 * due volte non duplica una riga di testo.
 */
object SyncMerge {
  /**
   * L'unione di [local] e [remote] rispetto a [base] (null per un elemento nato senza base comune). [now] da' lo stamp di un
   * testo fuso, che e' una scrittura nuova; [maxEdits] e' il tetto di lavoro del confronto ([FluidTextDiff]).
   */
  fun merge(
    base: SyncDocument?,
    local: SyncDocument,
    remote: SyncDocument,
    now: () -> SyncHlc,
    maxEdits: Int = FluidTextDiff.DefaultMaxEdits,
  ): SyncDocument {
    if (local == remote) return local
    if (local.deleted != null || remote.deleted != null) {
      val stamps = listOfNotNull(local.deleted, remote.deleted)
      return SyncDocument.tombstone(stamps.min())
    }
    if (base != null && !base.isDeleted) {
      if (local == base) return remote
      if (remote == base) return local
    }
    val names = sortedSetOf<String>().apply {
      addAll(local.fields.keys)
      addAll(remote.fields.keys)
    }
    val out = LinkedHashMap<String, SyncField>(names.size)
    for (name in names) {
      val o = base?.fields?.get(name)
      val l = local.fields[name]
      val r = remote.fields[name]
      val merged = mergeField(o, l, r, now, maxEdits) ?: continue
      out[name] = merged
    }
    return SyncDocument(out)
  }

  internal fun mergeField(o: SyncField?, l: SyncField?, r: SyncField?, now: () -> SyncHlc, maxEdits: Int): SyncField? {
    if (l == null) return r
    if (r == null) return l
    if (l == r) return l
    if (l == o) return r
    if (r == o) return l
    val alternatives = mergeAlternatives(o?.alternatives.orEmpty(), l.alternatives, r.alternatives)
    // Tutte e due le parti hanno cambiato il campo.
    if (l.stamp == r.stamp && l.value == r.value) {
      return SyncField(l.value, l.stamp, alternatives.without(l.stamp))
    }
    val (winner, loser) = if (l.stamp >= r.stamp) l to r else r to l
    val lv = l.value
    val rv = r.value
    if (lv is SyncValue.LongText && rv is SyncValue.LongText && l.stamp != r.stamp) {
      val baseText = (o?.value as? SyncValue.LongText)?.text
      val olderFirst = l.stamp < r.stamp
      val fused = baseText?.let { SyncTextMerge.merge(it, lv.text, rv.text, aFirst = olderFirst, maxEdits = maxEdits) }
      if (fused != null) {
        // Un testo fuso e' una scrittura nuova: il suo stamp segue tutti e due.
        return SyncField(SyncValue.LongText(fused), now(), alternatives.without(l.stamp).without(r.stamp))
      }
      val kept = (alternatives + SyncAlternative(loser.stamp, loser.value))
        .distinctBy { it.stamp }
        .without(winner.stamp)
      return SyncField(winner.value, winner.stamp, kept.sortedBy { it.stamp })
    }
    return SyncField(winner.value, winner.stamp, alternatives.without(winner.stamp).without(loser.stamp))
  }

  /**
   * Le versioni in conflitto dopo l'unione, a tre vie come i campi: resta una versione che c'e' da tutte e due le parti o
   * che una parte ha aggiunto; se ne va una che una parte ha scelto (tolto) e l'altra non ha toccato.
   */
  internal fun mergeAlternatives(
    base: List<SyncAlternative>,
    local: List<SyncAlternative>,
    remote: List<SyncAlternative>,
  ): List<SyncAlternative> {
    if (local.isEmpty() && remote.isEmpty()) return emptyList()
    val inBase = base.mapTo(HashSet()) { it.stamp }
    val inLocal = local.associateBy { it.stamp }
    val inRemote = remote.associateBy { it.stamp }
    val out = ArrayList<SyncAlternative>()
    for ((stamp, alt) in inLocal) {
      if (stamp in inRemote || stamp !in inBase) out += alt
    }
    for ((stamp, alt) in inRemote) {
      if (stamp !in inLocal && stamp !in inBase) out += alt
    }
    return out.sortedBy { it.stamp }
  }

  private fun List<SyncAlternative>.without(stamp: SyncHlc): List<SyncAlternative> =
    if (none { it.stamp == stamp }) this else filter { it.stamp != stamp }
}

/**
 * L'unione a tre vie di un testo (diff3: S. Khanna, K. Kunal e B. C. Pierce, «A Formal Investigation of Diff3», 2007): si
 * allineano base e ciascuna versione, e fra due punti uguali in tutte e tre si prende il tratto cambiato da una parte sola.
 * Prima per righe; un tratto conteso si riprova per parole, cosi' due modifiche a parole diverse della stessa riga si
 * fondono. Risponde `null` quando un tratto e' stato cambiato da tutte e due le parti in modo diverso.
 */
internal object SyncTextMerge {
  fun merge(base: String, a: String, b: String, aFirst: Boolean, maxEdits: Int = FluidTextDiff.DefaultMaxEdits): String? {
    if (a == b) return a
    if (a == base) return b
    if (b == base) return a
    // Due testi diversi nati senza una base comune: non c'e' niente da allineare, si sceglie.
    if (base.isEmpty()) return null
    return merge3(
      FluidTextDiff.lines(base), FluidTextDiff.lines(a), FluidTextDiff.lines(b), aFirst, maxEdits,
    ) { o, x, y ->
      merge3(
        FluidTextDiff.words(o.joinToString("")),
        FluidTextDiff.words(x.joinToString("")),
        FluidTextDiff.words(y.joinToString("")),
        aFirst,
        maxEdits,
        refine = null,
      )
    }
  }

  private fun merge3(
    o: List<String>,
    a: List<String>,
    b: List<String>,
    aFirst: Boolean,
    maxEdits: Int,
    refine: ((List<String>, List<String>, List<String>) -> String?)?,
  ): String? {
    val da = FluidTextDiff.diff(o, a, maxEdits) ?: return null
    val db = FluidTextDiff.diff(o, b, maxEdits) ?: return null
    val out = StringBuilder()
    var i = 0
    var ja = 0
    var jb = 0
    while (true) {
      var k = i
      while (k < o.size && (da.matchInB(k) < 0 || db.matchInB(k) < 0)) k++
      val aEnd = if (k < o.size) da.matchInB(k) else a.size
      val bEnd = if (k < o.size) db.matchInB(k) else b.size
      val oc = o.subList(i, k)
      val ac = a.subList(ja, aEnd)
      val bc = b.subList(jb, bEnd)
      when {
        ac == oc -> bc.forEach(out::append)
        bc == oc -> ac.forEach(out::append)
        ac == bc -> ac.forEach(out::append)
        // Due aggiunte nello stesso punto: restano tutte e due, prima la piu' vecchia, e quello che hanno in comune una
        // volta sola (la stessa riga aggiunta da tutte e due, o un'aggiunta che contiene l'altra).
        oc.isEmpty() -> union(ac, bc, aFirst, maxEdits, out)
        else -> out.append(refine?.invoke(oc, ac, bc) ?: return null)
      }
      if (k >= o.size) break
      out.append(o[k])
      i = k + 1
      ja = aEnd + 1
      jb = bEnd + 1
    }
    return out.toString()
  }

  /** L'unione di due aggiunte: si allineano, i pezzi comuni una volta, gli altri nel loro ordine (prima quelli di chi viene prima). */
  private fun union(a: List<String>, b: List<String>, aFirst: Boolean, maxEdits: Int, out: StringBuilder) {
    val d = FluidTextDiff.diff(a, b, maxEdits)
    if (d == null) {
      if (aFirst) { a.forEach(out::append); b.forEach(out::append) } else { b.forEach(out::append); a.forEach(out::append) }
      return
    }
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
      if (i < a.size && j < b.size && d.matchInB(i) == j) {
        out.append(a[i])
        i++
        j++
        continue
      }
      val ai = i
      while (i < a.size && d.matchInB(i) < 0) i++
      val bj = j
      while (j < b.size && d.matchInA(j) < 0) j++
      val ra = a.subList(ai, i)
      val rb = b.subList(bj, j)
      if (aFirst) { ra.forEach(out::append); rb.forEach(out::append) } else { rb.forEach(out::append); ra.forEach(out::append) }
    }
  }
}
