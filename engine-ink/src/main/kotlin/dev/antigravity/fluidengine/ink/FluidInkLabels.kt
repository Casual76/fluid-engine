package dev.antigravity.fluidengine.ink

import androidx.compose.runtime.Immutable

/**
 * Le parole del disegno, in italiano di serie (le app le traducono passando le loro). Sono quelle di Markup in italiano
 * dove ci sono («Penna», «Evidenziatore», «Matita», «Gomma», «Lazo», «Righello»), e le dice anche TalkBack.
 */
@Immutable
data class FluidInkLabels(
  val pen: String = "Penna",
  val highlighter: String = "Evidenziatore",
  val pencil: String = "Matita",
  val monoline: String = "Monolinea",
  val eraser: String = "Gomma",
  val lasso: String = "Lazo",
  val ruler: String = "Righello",
  val color: String = "Colore",
  val undo: String = "Annulla",
  val redo: String = "Ripristina",
  val done: String = "Fine",
  val cancel: String = "Chiudi",
  val more: String = "Altre opzioni",
  val width: (Int) -> String = { "Spessore ${it + 1} di 5" },
  val opacity: String = "Opacità",
  val objectEraser: String = "Gomma per oggetti",
  val areaEraser: String = "Gomma a pixel",
  val delete: String = "Elimina",
  val duplicate: String = "Duplica",
  val recolor: String = "Colora",
  val clearAll: String = "Cancella tutto",
  val stylusOnly: String = "Disegna solo con la penna",
  val autoMinimize: String = "Riduci automaticamente",
  val shapes: String = "Forme perfette",
  val moveToTop: String = "Sposta in alto",
  val moveToBottom: String = "Sposta in basso",
  val expand: String = "Mostra gli strumenti",
  val discardTitle: String = "Eliminare le modifiche?",
  val discard: String = "Elimina le modifiche",
  val keepEditing: String = "Continua",
  /** La descrizione del foglio per TalkBack: quanti tratti ci sono. */
  val canvas: (Int) -> String = { n ->
    when (n) {
      0 -> "Foglio da disegno, vuoto"
      1 -> "Foglio da disegno, un tratto"
      else -> "Foglio da disegno, $n tratti"
    }
  },
  /** I tratti scelti col lazo, detti da TalkBack. */
  val selected: (Int) -> String = { n -> if (n == 1) "Un tratto selezionato" else "$n tratti selezionati" },
  val rulerAngle: (Int) -> String = { "$it°" },
  val loading: String = "Apertura…",
) {
  /** Il nome di uno strumento. */
  fun of(tool: FluidInkTool): String = when (tool) {
    FluidInkTool.Pen -> pen
    FluidInkTool.Highlighter -> highlighter
    FluidInkTool.Pencil -> pencil
    FluidInkTool.Monoline -> monoline
    FluidInkTool.Eraser -> eraser
    FluidInkTool.Lasso -> lasso
    else -> tool.toString()
  }
}
