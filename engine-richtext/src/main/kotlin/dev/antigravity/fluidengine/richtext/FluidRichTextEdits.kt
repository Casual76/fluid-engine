package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

/**
 * Cosa e' acceso dove sta il cursore (o su tutta la selezione): quello che la barra di formattazione
 * mostra e TalkBack annuncia.
 *
 * Con il cursore fermo e' il formato con cui uscira' la prossima lettera (compreso un grassetto appena
 * acceso col tasto, che il testo non ha ancora); con una selezione uno stile e' acceso solo se lo e' su
 * **tutta** la selezione, come in ogni editor: toccarlo allora lo toglie, altrimenti lo mette a tutto.
 *
 * @param link l'indirizzo del link che il cursore tocca, o che copre tutta la selezione.
 * @param list l'elenco dei paragrafi toccati, se e' lo stesso per tutti.
 * @param quote vero se tutti i paragrafi toccati sono citazione.
 */
@Immutable
data class FluidRichTextFormat(
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val link: String? = null,
  val list: FluidRichTextListKind? = null,
  val quote: Boolean = false,
) {
  /** Vero se [style] e' acceso. */
  fun has(style: FluidRichTextInlineStyle): Boolean = when (style) {
    FluidRichTextInlineStyle.Bold -> bold
    FluidRichTextInlineStyle.Italic -> italic
    FluidRichTextInlineStyle.Underline -> underline
  }
}

/** Il formato che uscira' scrivendo in [offset], scelto col tasto prima di scrivere. */
@Immutable
internal data class PendingFormat(val offset: Int, val format: FluidRichTextInlineFormat)

/** Una modifica dell'utente in coordinate del testo **di prima**: `[start, end)` diventa [inserted]. */
internal data class UserChange(val start: Int, val end: Int, val inserted: String)

/** Cosa fare di una modifica dell'utente. */
internal sealed interface UserEditOutcome {
  /** Il testo cambia come ha chiesto la tastiera; [pending] e' il formato del cursore dopo (un a capo lo porta). */
  data class Applied(val document: FluidRichTextDocument, val pending: PendingFormat?) : UserEditOutcome

  /**
   * La tastiera ha chiesto un cambio di testo che l'editor trasforma in un cambio di stile: un a capo
   * su una voce vuota esce dall'elenco, un «cancella» in testa a una voce toglie la voce. Il testo resta
   * com'era.
   */
  data class Intercepted(val document: FluidRichTextDocument) : UserEditOutcome
}

/**
 * Applica al documento le modifiche che la tastiera ha fatto al testo, decidendo il formato dei
 * caratteri nuovi e lo stile dei paragrafi nuovi. Puro: e' il cuore dell'editor, e si prova sulla JVM.
 *
 * - **Scrivere** eredita il formato del carattere prima (vedi `typingFormatAt`), o quello scelto col
 *   tasto se il cursore non si e' mosso; **sostituire** (la correzione automatica, un suggerimento, la
 *   composizione che si allunga) tiene il formato del primo carattere sostituito: correggere una parola
 *   in grassetto la lascia in grassetto.
 * - **A capo** in mezzo a una voce crea una voce nuova dello stesso elenco; su una voce **vuota** esce
 *   dall'elenco (e su un paragrafo di citazione vuoto esce dalla citazione): il «doppio a capo».
 * - **Cancella** con il cursore in testa a una voce toglie la voce (poi la citazione) invece di unire il
 *   paragrafo a quello sopra, come fanno tutti gli editor.
 */
internal fun applyUserChanges(
  document: FluidRichTextDocument,
  changes: List<UserChange>,
  originalSelection: TextRange,
  pending: PendingFormat?,
): UserEditOutcome {
  if (changes.size == 1) {
    val change = changes.single()
    if (change.start == change.end && change.inserted == "\n") {
      val paragraph = document.paragraphIndexAt(change.start)
      val style = document.paragraphStyles[paragraph]
      val empty = document.paragraphStart(paragraph) == document.paragraphEnd(paragraph)
      if (empty && (style.list != null || style.quote)) {
        return UserEditOutcome.Intercepted(document.mapParagraphs(paragraph, paragraph) { it.unwrapOneLevel() })
      }
    }
    if (change.inserted.isEmpty() && change.end == change.start + 1 && document.text[change.start] == '\n' &&
      originalSelection.collapsed && originalSelection.start == change.end
    ) {
      val paragraph = document.paragraphIndexAt(change.end)
      val style = document.paragraphStyles[paragraph]
      if (style.list != null || style.quote) {
        return UserEditOutcome.Intercepted(document.mapParagraphs(paragraph, paragraph) { it.unwrapOneLevel() })
      }
    }
  }

  var result = document
  var nextPending: PendingFormat? = null
  // Dall'ultima alla prima: le coordinate di prima restano valide per quelle che vengono prima.
  for (change in changes.sortedByDescending { it.start }) {
    val format = when {
      change.start == change.end ->
        if (pending != null && pending.offset == change.start && originalSelection.collapsed && originalSelection.start == change.start) {
          pending.format
        } else {
          result.typingFormatAt(change.start)
        }
      else -> replacedFormat(result, change.start, change.end)
    }
    result = result.replaceText(change.start, change.end, change.inserted, format)
    if (changes.size == 1 && change.inserted.endsWith('\n') && !format.copy(link = null).isPlain) {
      // Dopo un a capo il paragrafo nuovo e' vuoto: non ha un carattere da cui ereditare. Il grassetto
      // in cui si stava scrivendo continua (il link no).
      nextPending = PendingFormat(change.start + change.inserted.length, format.copy(link = null))
    }
  }
  return UserEditOutcome.Applied(result, nextPending)
}

/** Il formato del primo carattere vero di `[start, end)`; se sono solo a capo, quello di chi scrive li'. */
private fun replacedFormat(document: FluidRichTextDocument, start: Int, end: Int): FluidRichTextInlineFormat {
  for (index in start until end) {
    if (document.text[index] != '\n') return document.formatAt(index)
  }
  return document.typingFormatAt(start)
}

/** Toglie un livello: prima l'elenco, poi la citazione. */
internal fun FluidRichTextParagraphStyle.unwrapOneLevel(): FluidRichTextParagraphStyle =
  if (list != null) copy(list = null) else copy(quote = false)

/** Il formato attivo per [selection]: vedi [FluidRichTextFormat]. */
internal fun FluidRichTextDocument.formatFor(selection: TextRange, pending: PendingFormat?): FluidRichTextFormat {
  val start = selection.min.coerceIn(0, text.length)
  val end = selection.max.coerceIn(0, text.length)
  if (start == end) {
    val inline = if (pending != null && pending.offset == start) pending.format else typingFormatAt(start)
    val linkRange = linkRangeAt(start)
    val style = paragraphStyles[paragraphIndexAt(start)]
    return FluidRichTextFormat(
      bold = inline.bold,
      italic = inline.italic,
      underline = inline.underline,
      link = linkRange?.let { formatAt(it.start).link },
      list = style.list,
      quote = style.quote,
    )
  }
  val first = paragraphIndexAt(start)
  val last = paragraphIndexAt(end)
  val styles = paragraphStyles.subList(first, last + 1)
  val firstLink = (start until end).firstOrNull { text[it] != '\n' }?.let { formatAt(it).link }
  return FluidRichTextFormat(
    bold = allInline(start, end) { it.bold },
    italic = allInline(start, end) { it.italic },
    underline = allInline(start, end) { it.underline },
    link = firstLink?.takeIf { url -> allInline(start, end) { it.link == url } },
    list = styles.first().list?.takeIf { kind -> styles.all { it.list == kind } },
    quote = styles.all { it.quote },
  )
}

/**
 * Porta [document] ad avere il testo [newText] cambiando il meno possibile (prefisso e suffisso in
 * comune): la rete di sicurezza per quando il testo e' cambiato senza passare dall'editor.
 */
internal fun FluidRichTextDocument.reconcile(newText: String): FluidRichTextDocument {
  if (text == newText) return this
  var prefix = 0
  val max = minOf(text.length, newText.length)
  while (prefix < max && text[prefix] == newText[prefix]) prefix++
  var suffix = 0
  while (suffix < max - prefix && text[text.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
  val start = prefix
  val end = text.length - suffix
  val format = if (start < end) replacedFormat(this, start, end) else typingFormatAt(start)
  return replaceText(start, end, newText.substring(prefix, newText.length - suffix), format)
}

/** Il pezzo di testo diverso fra [old] e [new]: `(inizio, fine in old, fine in new)`. */
internal fun minimalDiff(old: CharSequence, new: CharSequence): Triple<Int, Int, Int> {
  var prefix = 0
  val max = minOf(old.length, new.length)
  while (prefix < max && old[prefix] == new[prefix]) prefix++
  var suffix = 0
  while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
  return Triple(prefix, old.length - suffix, new.length - suffix)
}
