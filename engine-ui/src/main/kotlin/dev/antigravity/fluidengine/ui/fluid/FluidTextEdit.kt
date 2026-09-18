package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Le operazioni di un editor di testo semplice, sul valore e non sul componente.
 *
 * Esistono perche' un pulsante «grassetto» che appende `**` in fondo al testo e' peggio di nessun
 * pulsante: chi scrive ha il cursore in un punto preciso, e ogni operazione qui lavora da li'.
 * Pure — [TextFieldValue] dentro, [TextFieldValue] fuori — cosi' si provano in JVM, e cosi' un
 * editor le compone senza toccare il campo.
 */
object FluidTextEdit {
  /** Mette [snippet] al posto della selezione, o al cursore, e lascia il cursore dopo di esso. */
  fun insert(value: TextFieldValue, snippet: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val text = value.text.replaceRange(start, end, snippet)
    return TextFieldValue(text, TextRange(start + snippet.length))
  }

  /**
   * Avvolge la selezione fra [prefix] e [suffix] e la tiene selezionata, cosi' un secondo tocco
   * puo' avvolgerla ancora. Senza selezione mette la coppia e lascia il cursore in mezzo, pronto a
   * scrivere dentro.
   */
  fun wrap(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val text = value.text.replaceRange(start, end, prefix + selected + suffix)
    val innerStart = start + prefix.length
    val selection = if (selected.isEmpty()) TextRange(innerStart) else TextRange(innerStart, innerStart + selected.length)
    return TextFieldValue(text, selection)
  }

  /**
   * Mette [prefix] all'inizio della riga del cursore (`# `, `- `, `> `), o lo toglie se c'e' gia'.
   *
   * Un segno di riga vale per la riga, in qualunque punto di essa stia il cursore: e' la differenza
   * fra un titolo e un cancelletto a meta' frase. La selezione si sposta di quanto si e' aggiunto o
   * tolto, e non finisce mai prima dell'inizio della riga.
   */
  fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val lineStart = value.text.lastIndexOf('\n', value.selection.min - 1) + 1
    val hasPrefix = value.text.startsWith(prefix, lineStart)
    val text = if (hasPrefix) {
      value.text.removeRange(lineStart, lineStart + prefix.length)
    } else {
      value.text.replaceRange(lineStart, lineStart, prefix)
    }
    val delta = if (hasPrefix) -prefix.length else prefix.length
    fun shift(index: Int): Int = if (index >= lineStart) (index + delta).coerceAtLeast(lineStart) else index
    return TextFieldValue(text, TextRange(shift(value.selection.start), shift(value.selection.end)))
  }
}
