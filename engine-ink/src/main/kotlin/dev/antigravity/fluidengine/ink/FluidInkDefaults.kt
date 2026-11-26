package dev.antigravity.fluidengine.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * I numeri del disegno. Dove c'e' Apple, la fonte e la fiducia; dove non c'e', «a occhio» con la data. Si cambiano qui,
 * non nei componenti.
 */
object FluidInkDefaults {
  /**
   * I cinque spessori di ogni strumento, in dp sullo schermo quando comincia il tratto (lo spessore che si vede: chi
   * ingrandisce scrive piu' fine, come in Note di iOS). Cinque perche' cinque sono i pallini della tavolozza di Markup
   * (iOS 26, fiducia alta sul numero); le misure sono a occhio (25/11/2026) sulle riprese di Markup, fiducia bassa: Apple
   * pubblica solo i limiti di `PKInkingTool.InkType.validWidthRange`.
   */
  val PenWidths: List<Dp> = listOf(1.dp, 2.dp, 3.5.dp, 6.dp, 10.dp)
  val HighlighterWidths: List<Dp> = listOf(8.dp, 12.dp, 18.dp, 26.dp, 36.dp)
  val PencilWidths: List<Dp> = listOf(1.dp, 2.dp, 3.dp, 5.dp, 8.dp)

  /** I diametri della gomma a pixel (la gomma a oggetti tocca con il dito: [ObjectEraserWidth]). */
  val EraserWidths: List<Dp> = listOf(8.dp, 16.dp, 28.dp, 44.dp, 64.dp)

  /** Il diametro della gomma a oggetti: un dito (a occhio, 25/11/2026). */
  val ObjectEraserWidth: Dp = 14.dp

  /** Lo spessore di partenza di ogni strumento: il terzo pallino per la penna, il terzo per l'evidenziatore, il secondo per la matita. */
  fun widths(tool: FluidInkTool): List<Dp> = when (tool) {
    FluidInkTool.Highlighter -> HighlighterWidths
    FluidInkTool.Pencil -> PencilWidths
    FluidInkTool.Eraser -> EraserWidths
    else -> PenWidths
  }

  fun defaultWidthIndex(tool: FluidInkTool): Int = when (tool) {
    FluidInkTool.Pencil -> 1
    else -> 2
  }

  /**
   * I colori veloci, quelli della fila del popover dello strumento: nero, blu, verde, giallo e rosso di sistema di iOS
   * (`UIColor.systemBlue` #007AFF, `systemGreen` #34C759, `systemYellow` #FFCC00, `systemRed` #FF3B30: fiducia alta, i
   * valori chiari della Human Interface Guidelines). Il nero e' quello della penna di Markup.
   */
  val QuickColors: List<Color> = listOf(
    Color(0xFF000000), Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFFFF3B30),
  )

  /** I colori di partenza: penna e matita nere, evidenziatore giallo, come in Markup (fiducia alta). */
  fun defaultColor(tool: FluidInkTool): Color = when (tool) {
    FluidInkTool.Highlighter -> Color(0xFFFFCC00)
    else -> Color(0xFF000000)
  }

  /**
   * L'opacita' dell'evidenziatore: il colore scelto, a meta' (a occhio sulle riprese di Markup, 25/11/2026). Il tratto
   * non si somma con se stesso (`SelfOverlap.DISCARD`): ripassare sullo stesso tratto non lo scurisce, come il marker.
   */
  const val HighlighterAlpha: Float = 0.5f

  /**
   * Quanto resta fermo il dito alla fine di un tratto perche' diventi una forma: mezzo secondo (a occhio sulle riprese
   * di Note, iOS 26, 25/11/2026; Apple dice solo «tieni premuto alla fine», fiducia media).
   */
  const val ShapeHoldMillis: Long = 500L

  /** Quanto puo' tremare il dito fermo: 4 dp (a occhio, 25/11/2026). */
  val ShapeHoldSlop: Dp = 4.dp

  /** La forma piu' piccola che si riconosce: 24 dp di diagonale (a occhio, 25/11/2026). */
  val ShapeMinSize: Dp = 24.dp

  /** Il viaggio dal tratto a mano alla forma perfetta: 180 ms (a occhio sulle riprese di Note, 25/11/2026). */
  const val ShapeMorphMillis: Int = 180

  /**
   * Il righello: una riga di 88 dp di altezza (le proporzioni del righello di PencilKit sull'iPhone, a occhio sulle
   * riprese, 25/11/2026) e un tratto che comincia entro 24 dp dal bordo vi si appoggia e corre dritto.
   */
  val RulerHeight: Dp = 88.dp
  val RulerSnap: Dp = 24.dp

  /** Gli angoli dove il righello si ferma mentre gira (con un tic): ogni 45° entro 2°, come PencilKit (fiducia media). */
  const val RulerDetentDegrees: Float = 45f
  const val RulerDetentSlack: Float = 2f

  /** Lo zoom: da 1 (il foglio intero) a 5 volte (a occhio, 25/11/2026: Note arriva piu' o meno li'). */
  const val MaxZoom: Float = 5f

  /**
   * Il secondo dito che arriva sul primo che disegna: entro 250 ms, o prima che il tratto passi 24 dp, il tratto si
   * annulla e i due dita spostano e ingrandiscono la pagina, come nel foglio di PencilKit con «qualsiasi input» (a
   * occhio, 25/11/2026). Dopo, il secondo dito e' il palmo, e si ignora.
   */
  const val TwoFingerGraceMillis: Long = 250L
  val TwoFingerGraceDistance: Dp = 24.dp

  /** Quanti passi tiene la cronologia di annulla e ripristina (PencilKit usa `UndoManager`, senza un tetto dichiarato). */
  const val HistoryLimit: Int = 200

  /** Il colore della selezione del lazo (il tratteggio), e quello del contorno della gomma: l'accento lo da' il tema. */
  val LassoDash: Dp = 6.dp
}
