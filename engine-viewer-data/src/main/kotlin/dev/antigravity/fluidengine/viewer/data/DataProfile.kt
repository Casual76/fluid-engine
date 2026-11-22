package dev.antigravity.fluidengine.viewer.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * F6C.13 · I numeri della famiglia «data»: tetti di lettura, misure delle righe, colori dei tipi. Nessuno è un numero di
 * movimento. Dove non c'è una fonte sono scelti a occhio il 22 novembre 2026, con la tavola D davanti
 * (`artifacts/liquid-glass/ricerca/visori-2/tavole/d-mix-*.png`, pagina «Dati · JSON»).
 */
internal object DataProfile {
  /** Il tetto dei nodi di un albero (JSON, XML, plist): oltre si smette di leggere e la pagina lo dice. A occhio. */
  const val MaxTreeNodes: Int = 150_000

  /** Quanti figli mostra un nodo aperto, e quanti ne aggiunge «Mostra altri». La ricerca dice 100 (§1.3). */
  const val TreePage: Int = 100

  /** Il tetto dell'annidamento: oltre, il file è «annidato troppo» (un JSON di 100.000 «[» non fa cadere lo stack). */
  const val MaxTreeDepth: Int = 512

  /** Sotto questo numero di nodi si aprono da soli anche i figli della radice. A occhio. */
  const val ExpandSmallTree: Int = 600

  /** I byte letti al più da un testo intero (Markdown, HTML, mail, calendario, contatti, plist binario): 16 MiB. */
  const val MaxTextBytes: Int = 16 * 1024 * 1024

  /** Il testo mostrato in «Testo» o «Sorgente»: il tetto del preset testo di F6C.4, 4 MiB. */
  const val MaxSourceBytes: Int = 4 * 1024 * 1024

  /** Le righe al più di un CSV: oltre si smette di contare e la pagina lo dice. 100.000 è la prova; dieci volte tanto il tetto. */
  const val MaxCsvRows: Int = 1_000_000

  /** Le colonne al più di un CSV: quelle di un foglio di Excel. */
  const val MaxCsvColumns: Int = 16_384

  /** Ogni quante righe contate la griglia riceve il nuovo conto mentre il file si legge. A occhio. */
  const val CsvPublishRows: Int = 4_096

  /** Il testo al più di una cella: un CSV con un campo di un megabyte non impagina un megabyte. A occhio. */
  const val MaxCellChars: Int = 1_000

  /** La larghezza di una colonna di CSV: dal testo più lungo delle prime righe, fra questi due estremi (dp). A occhio. */
  const val CsvMinColumn: Float = 56f
  const val CsvMaxColumn: Float = 280f
  const val CsvCharWidth: Float = 7.2f

  /** Una voce dentro un EPUB o un allegato più grande di così non si estrae (zip bomb): 64 MiB. A occhio. */
  const val MaxEntryBytes: Long = 64L * 1024 * 1024

  /** Il rapporto di compressione oltre il quale una voce è sospetta (una zip bomb comprime oltre 1000:1). */
  const val MaxCompressionRatio: Long = 200

  /** La foto di un contatto e la copertina di un libro si decodificano al più a questa misura (px). */
  const val MaxPictureSide: Int = 1024

  /** L'altezza minima di una riga dell'albero (dp): la tavola D ne ha 33, con il dito ne servono almeno 36. */
  val TreeRowHeight = 36.dp

  /** Il rientro di ogni livello dell'albero (dp), come nella tavola. */
  val TreeIndent = 18.dp

  /**
   * La tinta leggera delle schede di calendario e contatti (D · Mix, §5.6): la stessa del preset sconosciuto di F6C.9
   * (`ViewerMixProfile.FileTint`, 20%), a occhio il 22 novembre 2026. Sparisce con «Riduci trasparenza» e il contrasto alto.
   */
  const val CardTint: Float = 0.20f

  /**
   * La colonna di lettura di un libro sul tablet (dp): quella centrale di F6C.9, che l'orchestratore ha indicato nel
   * secondo giro di F6C.13 (22 novembre 2026). Sul telefono la colonna è lo schermo meno i margini della pagina.
   */
  const val BookColumn: Int = 552

  /** Il corpo del testo di un libro (sp, attraverso lo zoom del testo di WebView): fra 18 e 20, a occhio, 22 novembre 2026. */
  const val BookFontSize: Int = 19

  /** L'interlinea di un libro: comoda, come Libri. A occhio, 22 novembre 2026. */
  const val BookLineHeight: Float = 1.6f

  /** L'ambra dell'avviso: la stessa dell'avviso dell'eseguibile di F6C.9 (`ViewerMixProfile.Amber`, il giallo-arancio di sistema di Apple). */
  val Amber = Color(0xFFFF9500)

  /** La colonna della pagina di un libro protetto: quella dello sconosciuto di F6C.9 (`ViewerMixProfile.PanelWidth`). */
  val NoticeColumn = 444.dp

  /** L'icona grande di un libro senza copertina: come l'icona dello sconosciuto di F6C.9. */
  val NoticeIcon = 136.dp
}

/**
 * I colori dei tipi dell'albero, dalla tavola D (che usa quelli di Xcode): rosso per le stringhe, blu per i numeri
 * (giallo in scuro), viola per i vero/falso (rosa in scuro), grigio per null e per i contenitori. Il punto colorato
 * davanti alla chiave è il segno del tipo (§5.1, «i punti colorati»). A occhio, 22 novembre 2026.
 */
internal class DataTypeColors(
  val string: Color,
  val number: Color,
  val bool: Color,
  val nil: Color,
  val container: Color,
  val date: Color,
  val data: Color,
  val element: Color,
  val attribute: Color,
  val comment: Color,
  val error: Color,
) {
  companion object {
    val Light = DataTypeColors(
      string = Color(0xFFC41A16), number = Color(0xFF1C00CF), bool = Color(0xFF9B2393), nil = Color(0xFFAEAEB2),
      container = Color(0xFF8E8E93), date = Color(0xFF0B4F79), data = Color(0xFF643820), element = Color(0xFF0F68A0),
      attribute = Color(0xFF815F03), comment = Color(0xFF5D6C79), error = Color(0xFFD70015),
    )
    val Dark = DataTypeColors(
      string = Color(0xFFFC6A5D), number = Color(0xFFD0BF69), bool = Color(0xFFFC5FA3), nil = Color(0xFF6C7986),
      container = Color(0xFF98989D), date = Color(0xFF5DD8FF), data = Color(0xFFFD8F3F), element = Color(0xFF41A1C0),
      attribute = Color(0xFFE5CFA3), comment = Color(0xFF7F8C98), error = Color(0xFFFF6961),
    )
  }
}
