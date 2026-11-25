package dev.antigravity.fluidengine.files

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Un file o una cartella di una [FluidFileSource]. [id] è stabile dentro la sorgente (l'id del documento, il percorso
 * relativo): con l'id della sorgente identifica l'elemento nei recenti, nelle chiavi delle liste e nel viaggio d'oro.
 * [uri] è l'indirizzo da cui il visore legge il file (null per una cartella di una sorgente virtuale); una cartella
 * dice, se lo sa, quanti elementi contiene ([childCount]).
 */
@Immutable
data class FluidFileItem(
  val id: String,
  val name: String,
  val isFolder: Boolean = false,
  val mimeType: String? = null,
  val sizeBytes: Long? = null,
  val modifiedAtMillis: Long? = null,
  val uri: Uri? = null,
  val childCount: Int? = null,
) {
  /** La famiglia del file, per l'icona e per TalkBack. */
  val kind: FluidFileKind get() = FluidFileKind.from(mimeType, name)

  /** Il file per i visori dell'engine; null per una cartella o un file senza indirizzo. */
  fun viewerFile(): FluidViewerFile? =
    uri?.takeIf { !isFolder }?.let { FluidViewerFile(it, name, mimeType, sizeBytes, modifiedAtMillis) }
}

/**
 * Per che cosa si ordina una cartella. Non è un `enum` di proposito (regola 3.11): un ordine nuovo sarà una costante in
 * più. Le quattro sono quelle del menu di File di iOS 26 («Nome», «Tipo», «Data», «Dimensione»; «Tag» non c'è).
 */
@Immutable
@JvmInline
value class FluidFileSortKey private constructor(private val id: Int) {
  override fun toString(): String = when (id) {
    DateId -> "Date"
    SizeId -> "Size"
    KindId -> "Kind"
    else -> "Name"
  }

  companion object {
    private const val NameId = 0
    private const val DateId = 1
    private const val SizeId = 2
    private const val KindId = 3

    val Name: FluidFileSortKey = FluidFileSortKey(NameId)
    val Date: FluidFileSortKey = FluidFileSortKey(DateId)
    val Size: FluidFileSortKey = FluidFileSortKey(SizeId)
    val Kind: FluidFileSortKey = FluidFileSortKey(KindId)

    /** Nell'ordine del menu di File: nome, tipo, data, dimensione. */
    val all: List<FluidFileSortKey> = listOf(Name, Kind, Date, Size)
  }
}

/**
 * L'ordine di una cartella: la chiave e il verso. Come File, la data e la dimensione partono **dalla più recente e
 * dalla più grande** ([of]); un secondo tocco sulla voce scelta inverte il verso. Le cartelle stanno sempre prima dei
 * file; a parità decide il nome, con le regole della lingua (`Collator`: «é» accanto a «e», maiuscole e minuscole
 * insieme, «file2» prima di «file10» non c'è: è l'ordine alfabetico, come Android e File).
 */
@Immutable
data class FluidFileSort(val key: FluidFileSortKey = FluidFileSortKey.Name, val descending: Boolean = false) {
  /** Lo stesso ordine col verso opposto. */
  fun reversed(): FluidFileSort = copy(descending = !descending)

  /** Il confronto: le cartelle prima, poi la chiave nel verso scelto, poi il nome. */
  fun comparator(locale: Locale = Locale.getDefault()): Comparator<FluidFileItem> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    val byName = Comparator<FluidFileItem> { a, b -> collator.compare(a.name, b.name) }
    val byKey: Comparator<FluidFileItem> = when (key) {
      FluidFileSortKey.Date -> compareBy<FluidFileItem> { it.modifiedAtMillis ?: Long.MIN_VALUE }
      FluidFileSortKey.Size -> compareBy<FluidFileItem> { if (it.isFolder) (it.childCount ?: 0).toLong() else it.sizeBytes ?: -1L }
      FluidFileSortKey.Kind -> Comparator { a, b ->
        collator.compare(a.name.substringAfterLast('.', "").lowercase(locale), b.name.substringAfterLast('.', "").lowercase(locale))
      }
      else -> byName
    }
    val directed = if (descending) byKey.reversed() else byKey
    return compareBy<FluidFileItem> { !it.isFolder }.then(directed).then(byName)
  }

  companion object {
    /** L'ordine di serie di una chiave, come File: dalla A alla Z, dal più recente, dal più grande. */
    fun of(key: FluidFileSortKey): FluidFileSort =
      FluidFileSort(key, descending = key == FluidFileSortKey.Date || key == FluidFileSortKey.Size)
  }
}

/**
 * Una richiesta a una sorgente: la [folder] (null è la radice), l'ordine, il segno dove riprendere ([cursor], quello
 * dato dalla pagina prima; null all'inizio), quanti elementi al più ([limit]) e, per una sorgente che cerca da sé
 * ([FluidFileSource.searches]), le parole da cercare ([search]) in tutta la cartella e sotto.
 */
@Immutable
data class FluidFileQuery(
  val folder: String?,
  val sort: FluidFileSort = FluidFileSort(),
  val cursor: String? = null,
  val limit: Int = DefaultLimit,
  val search: String? = null,
) {
  companion object {
    /** Gli elementi per pagina: 200, a occhio (25 novembre 2026). Una cartella di migliaia di file arriva a pezzi. */
    const val DefaultLimit: Int = 200
  }
}

/** Una pagina di una cartella: gli elementi e il segno per la prossima ([next]); null se è l'ultima. */
@Immutable
data class FluidFilePage(val items: List<FluidFileItem>, val next: String? = null)

/**
 * **Il contratto di una sorgente di file** (la scheda dell'idea: «paginata, lenta, che può fallire»). Le due di serie
 * sono la cartella dell'app ([FluidFileSources.appFolder]) e una cartella concessa dall'utente con lo Storage Access
 * Framework ([FluidFileSources.documentTree], anche di un'app che ha un `DocumentsProvider`: Drive, MyCloud Gallery).
 * L'app scrive le sue: un cloud, gli allegati di una casella, i file di una conversazione.
 *
 * - [list] è sospendibile e può essere lenta: il browser mostra le righe vuote mentre aspetta e «Riprova» se lancia.
 *   Si chiama dal thread principale, e la sorgente sposta da sé il lavoro (`withContext(Dispatchers.IO)`), come i
 *   sorgenti dei visori.
 * - **Le pagine**: al più [FluidFileQuery.limit] elementi e il segno per la prossima. Il browser chiede la prossima
 *   quando la lista si avvicina alla fine.
 * - [sorts]: vero se la sorgente rende già gli elementi nell'ordine chiesto (le due di serie: leggono la cartella intera
 *   e la tagliano a pezzi). Falso: il browser ordina in memoria ciò che ha, e le pagine nuove si inseriscono al loro
 *   posto.
 * - [searches]: vero se la sorgente cerca da sé ([FluidFileQuery.search]); falso: il browser legge tutta la cartella e
 *   filtra i nomi in memoria (senza badare a maiuscole e accenti).
 * - [changes] avvisa quando la cartella cambia (un provider che finisce di caricare, un file nuovo): il browser rilegge.
 */
interface FluidFileSource {
  /** L'id stabile della sorgente: entra nei recenti, nelle rotte e nelle chiavi. Senza «/». */
  val id: String
  /** Il nome nella lista delle posizioni e il titolo della radice: «Su questo telefono», «Drive». */
  val title: String
  /** La seconda riga nella lista delle posizioni: l'app che la dà, lo spazio. */
  val subtitle: String? get() = null
  /** L'icona nella lista delle posizioni; null: la cartella. */
  val icon: ImageVector? get() = null
  val sorts: Boolean get() = false
  val searches: Boolean get() = false

  suspend fun list(query: FluidFileQuery): FluidFilePage

  fun changes(folder: String?): Flow<Unit> = emptyFlow()
}

/** Il nome per la ricerca: minuscolo, senza accenti («Città» trova «citta»). */
internal fun fluidFileSearchKey(text: String, locale: Locale = Locale.getDefault()): String =
  Normalizer.normalize(text.lowercase(locale), Normalizer.Form.NFD).replace(Diacritics, "")

private val Diacritics = Regex("\\p{Mn}+")

/** Vero se [item] risponde alle [words] (tutte, in qualunque punto del nome). */
internal fun fluidFileMatches(item: FluidFileItem, words: List<String>, locale: Locale = Locale.getDefault()): Boolean {
  if (words.isEmpty()) return true
  val name = fluidFileSearchKey(item.name, locale)
  return words.all { it in name }
}

/** Le parole di una ricerca, già nella forma di [fluidFileSearchKey]. */
internal fun fluidFileSearchWords(query: String, locale: Locale = Locale.getDefault()): List<String> =
  fluidFileSearchKey(query, locale).split(' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }
