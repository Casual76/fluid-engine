package dev.antigravity.fluidengine.editor

import androidx.compose.runtime.Immutable

/**
 * Le parole dell'editor, in italiano di serie: quelle di Foto in iOS 26 in italiano dove ci sono («Annulla», «Fine»,
 * «Regola», «Ritaglia», «Ripristina originale»), le altre scritte allo stesso modo. Un'app in un'altra lingua le passa
 * tutte.
 *
 * In Foto «Annulla» e' sia il tasto in alto (lascia la modifica) sia la freccia (toglie l'ultimo passo); qui la freccia
 * dice a TalkBack «Annulla l'ultima modifica», per non confondere le due.
 */
@Immutable
data class FluidImageEditorStrings(
  val cancel: String = "Annulla",
  val done: String = "Fine",
  val adjust: String = "Regola",
  val crop: String = "Ritaglia",
  val undo: String = "Annulla l'ultima modifica",
  val redo: String = "Ripristina",
  val more: String = "Altro",
  val revert: String = "Ripristina originale",
  val splitCompare: String = "Confronto a tendina",
  val splitCompareOff: String = "Chiudi il confronto",
  val markup: String = "Annota",
  val auto: String = "Auto",
  val autoOn: String = "Auto acceso",
  val autoOff: String = "Auto spento",
  val original: String = "Originale",
  val edited: String = "Modificata",
  val reset: String = "Reimposta",
  val rotate: String = "Ruota a sinistra",
  val flip: String = "Specchia",
  val aspect: String = "Proporzioni",
  val straighten: String = "Raddrizza",
  val portrait: String = "Verticale",
  val landscape: String = "Orizzontale",
  val loading: String = "Apertura della foto…",
  val loadFailed: String = "Impossibile aprire la foto",
  val discardTitle: String = "Annullare le modifiche?",
  val discard: String = "Annulla modifiche",
  val keepEditing: String = "Continua a modificare",
  val cropFrame: String = "Ritaglio",
  val zoomIn: String = "Stringi il ritaglio",
  val zoomOut: String = "Allarga il ritaglio",
  val photo: String = "Foto",
  val holdToCompare: String = "Tieni premuto per vedere l'originale",
  /** Il nome di una regolazione, sulla ruota e sopra la foto. */
  val adjustmentName: (FluidAdjustment) -> String = ::italianName,
  /** Il nome delle proporzioni nella fila. */
  val aspectName: (FluidCropAspect) -> String = ::italianAspect,
  /** I gradi del raddrizzamento sul righello. */
  val degrees: (Float) -> String = { v -> "${if (v < 0) "−" else ""}${kotlin.math.abs(v).toInt()}°" },
  /** Il tasto sotto «Impossibile aprire la foto» (R1): riapre la foto senza uscire dall'editor. */
  val retry: String = "Riprova",
) {
  private companion object {
    fun italianName(a: FluidAdjustment): String = when (a) {
      FluidAdjustment.Exposure -> "Esposizione"
      FluidAdjustment.Brilliance -> "Brillantezza"
      FluidAdjustment.Highlights -> "Luci"
      FluidAdjustment.Shadows -> "Ombre"
      FluidAdjustment.Contrast -> "Contrasto"
      FluidAdjustment.Brightness -> "Luminosità"
      FluidAdjustment.BlackPoint -> "Punto di nero"
      FluidAdjustment.Saturation -> "Saturazione"
      FluidAdjustment.Vibrance -> "Vividezza"
      FluidAdjustment.Warmth -> "Calore"
      FluidAdjustment.Tint -> "Tinta"
      FluidAdjustment.Sharpness -> "Nitidezza"
      FluidAdjustment.Definition -> "Definizione"
      FluidAdjustment.Vignette -> "Vignettatura"
      else -> a.id
    }

    fun italianAspect(a: FluidCropAspect): String = when (a) {
      FluidCropAspect.Freeform -> "Libero"
      FluidCropAspect.Original -> "Originale"
      FluidCropAspect.Square -> "Quadrato"
      FluidCropAspect.Circle -> "Tondo"
      else -> "${a.width}:${a.height}"
    }
  }
}
