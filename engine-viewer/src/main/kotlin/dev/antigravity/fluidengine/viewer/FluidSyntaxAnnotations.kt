package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Il tag delle annotazioni di sintassi: lo stesso che legge il preset testo di engine-ui (F6C.2,
 * `FluidTextPresetDefaults.SyntaxAnnotation`). Il valore di ogni annotazione è il ruolo in minuscolo
 * ([FluidSyntaxRole.annotation]: `keyword`, `type`, `string`, `number`, `comment`, `tag`, `attribute`, `heading`,
 * `emphasis`, `link`, `code`). Il preset li colora col tema mentre disegna: il passaggio chiaro/scuro non lascia
 * colori vecchi nelle righe già lette, e il modulo non sceglie colori.
 */
const val FluidSyntaxAnnotation: String = "fluid.syntax"

/** Il valore dell'annotazione di questo ruolo: il nome in minuscolo. */
val FluidSyntaxRole.annotation: String get() = toString().lowercase()

/** [line] con un'annotazione [FluidSyntaxAnnotation] per ogni pezzo. Un pezzo fuori dalla riga si ignora. */
fun fluidSyntaxAnnotated(line: String, spans: List<FluidSyntaxSpan>): AnnotatedString = buildAnnotatedString {
  append(line)
  for (span in spans) {
    val start = span.start.coerceIn(0, line.length)
    val end = span.end.coerceIn(start, line.length)
    if (end > start) addStringAnnotation(FluidSyntaxAnnotation, span.role.annotation, start, end)
  }
}
