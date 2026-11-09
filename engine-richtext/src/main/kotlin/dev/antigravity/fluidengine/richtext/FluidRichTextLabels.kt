package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le parole che l'editor e la sua barra dicono da se': i nomi dei tasti per TalkBack, lo stato al
 * cursore, il foglio del link.
 *
 * Stanno nel modulo e non in `FluidLabels` di `engine-ui` perche' il modulo e' opt-in: un'app che non
 * lo include non deve vedersi crescere le parole dell'engine. Come `FluidLabels`, **in italiano di
 * serie**, con [english] pronto; un'app tradotta le passa una volta alla radice:
 *
 * ```kotlin
 * CompositionLocalProvider(LocalFluidRichTextLabels provides FluidRichTextLabels(bold = stringResource(R.string.bold), …)) { … }
 * ```
 */
@Immutable
data class FluidRichTextLabels(
  /** Il tasto «Aa» che mostra e nasconde la barra. */
  val formatting: String = "Formattazione",
  /** Il titolo della barra per TalkBack, quando compare. */
  val formattingBar: String = "Barra di formattazione",
  val bold: String = "Grassetto",
  val italic: String = "Corsivo",
  val underline: String = "Sottolineato",
  val bulletList: String = "Elenco puntato",
  val numberedList: String = "Elenco numerato",
  val quote: String = "Citazione",
  /** Il tasto del link e lo stato «link» al cursore. */
  val link: String = "Link",
  val undo: String = "Annulla",
  val redo: String = "Ripeti",
  /** Il titolo del foglio del link. */
  val linkTitle: String = "Link",
  /** Il campo del testo del link. */
  val linkText: String = "Testo",
  /** Il campo dell'indirizzo del link. */
  val linkAddress: String = "Indirizzo",
  /** Il segnaposto dell'indirizzo. */
  val linkAddressPlaceholder: String = "esempio.it",
  /** Sotto l'indirizzo, quando non si puo' usare (vuoto, `javascript:`, uno schema sconosciuto). */
  val linkInvalid: String = "Questo indirizzo non si può usare",
  val linkConfirm: String = "Fatto",
  val linkCancel: String = "Annulla",
  val linkRemove: String = "Togli il link",
  /**
   * Lo stato al cursore per TalkBack quando il cursore e' su un link, con `%1$s` per l'indirizzo: «link a
   * esempio.it». Una stringa con un segnaposto, cosi' un'app la traduce come le altre risorse.
   */
  val linkTo: String = "link a %1\$s",
  /** Fra due stili nello stato al cursore: «Grassetto, corsivo». */
  val separator: String = ", ",
  /** Il tasto che porta le voci (o le citazioni) toccate un livello piu' dentro. */
  val increaseIndent: String = "Aumenta rientro",
  /** Il tasto che le porta un livello piu' fuori (e alla fine fuori dall'elenco o dalla citazione). */
  val decreaseIndent: String = "Riduci rientro",
  /**
   * Il livello di un elenco o di una citazione annidati nello stato al cursore, con `%1$d` per il
   * numero: «Elenco puntato, livello 2». Si dice solo dal secondo livello in su.
   */
  val level: String = "livello %1\$d",
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidRichTextLabels = FluidRichTextLabels(
      formatting = "Formatting",
      formattingBar = "Formatting bar",
      bold = "Bold",
      italic = "Italic",
      underline = "Underline",
      bulletList = "Bulleted list",
      numberedList = "Numbered list",
      quote = "Quote",
      link = "Link",
      undo = "Undo",
      redo = "Redo",
      linkTitle = "Link",
      linkText = "Text",
      linkAddress = "Address",
      linkAddressPlaceholder = "example.com",
      linkInvalid = "This address can't be used",
      linkConfirm = "Done",
      linkCancel = "Cancel",
      linkRemove = "Remove link",
      linkTo = "link to %1\$s",
      separator = ", ",
      increaseIndent = "Increase indent",
      decreaseIndent = "Decrease indent",
      level = "level %1\$d",
    )
  }
}

/** Le parole dell'editor per l'albero sotto. Di serie l'italiano. */
val LocalFluidRichTextLabels: ProvidableCompositionLocal<FluidRichTextLabels> = staticCompositionLocalOf { FluidRichTextLabels() }

/**
 * Cosa TalkBack dice dello stato al cursore: gli stili accesi, l'elenco (col livello da 2 in su:
 * «Elenco puntato, livello 2»), la citazione (idem), il link. Vuoto per il testo semplice.
 */
internal fun FluidRichTextFormat.describe(labels: FluidRichTextLabels): String = buildList {
  if (bold) add(labels.bold)
  if (italic) add(labels.italic)
  if (underline) add(labels.underline)
  when (list) {
    FluidRichTextListKind.Bullet -> add(labels.bulletList)
    FluidRichTextListKind.Numbered -> add(labels.numberedList)
    null -> Unit
  }
  if (list != null && listLevel > 1) add(String.format(labels.level, listLevel))
  if (quote) add(labels.quote)
  if (quote && quoteLevel > 1) add(String.format(labels.level, quoteLevel))
  link?.let { add(String.format(labels.linkTo, FluidRichTextLinks.displayText(it))) }
}.joinToString(labels.separator)
