package dev.antigravity.fluidengine.viewer.graphics

import java.util.Locale

internal data class FontGlyphGroup(val label: String, val characters: String)

internal data class FontPreview(
  val hero: String,
  val sentence: String,
  val groups: List<FontGlyphGroup>,
  val scripts: List<String>,
  val languages: List<String>,
)

private const val Uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val Lowercase = "abcdefghijklmnopqrstuvwxyz"
private const val Greek = "αβγδεζηθικλμνξοπρστυφχψω"
private const val Cyrillic = "абвгдежзийклмнопрстуфхцчшщъыьэюя"
private const val Hebrew = "אבגדהוזחטיךכלםמןנסעףפץצקרשת"
private const val Arabic = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"

private val scriptSamples = listOf(
  FontGlyphGroup("Greco", Greek),
  FontGlyphGroup("Cirillico", Cyrillic),
  FontGlyphGroup("Ebraico", Hebrew),
  FontGlyphGroup("Arabo", Arabic),
  FontGlyphGroup("Devanagari", "अआइईउऊएकगचजटडतदनपबभमयरलवशसह"),
  FontGlyphGroup("Kana", "あいうえおかきくけこさしすせそアイウエオカキクケコ"),
  FontGlyphGroup("Han", "一二三十山水日月人文天地中大小"),
  FontGlyphGroup("Hangul", "가나다라마바사아자차카타파하한글"),
)

private val previewSentences = listOf(
  "Quel vitreo balzo fra squarci d'ombra",
  "Τα γράμματα ζωντανεύουν",
  "Съешь ещё этих мягких булок",
  "אותיות יוצרות צורות",
  "الحروف ترسم كلمات",
  "अक्षर मिलकर शब्द बनाते हैं",
  "文字を組み合わせる",
  "文字描绘世界",
  "글자로 그림을 그려요",
)

/** Base letters checked in the Unicode map. The probes do not infer shaping or full language support. */
private val languageAlphabets = listOf(
  "Inglese" to Lowercase,
  "Italiano" to Lowercase + "àèéìòóù",
  "Francese" to Lowercase + "àâæçéèêëîïôœùûüÿ",
  "Spagnolo" to Lowercase + "áéíñóúü",
  "Tedesco" to Lowercase + "äöüß",
  "Portoghese" to Lowercase + "áàâãçéêíóôõú",
  "Polacco" to Lowercase + "ąćęłńóśźż",
  "Greco" to Greek + "ςάέήίόύώϊϋ",
  "Russo" to Cyrillic + "ё",
  "Ucraino" to Cyrillic.filterNot { it in "ъыэ" } + "ґєії",
)

internal fun fontPreview(cmap: FontCmap?): FontPreview {
  fun available(group: FontGlyphGroup) = group.copy(characters = group.characters.filter { cmap != null && it.code in cmap })
  fun covers(text: String) = cmap != null && text.all { it.isWhitespace() || it.code in cmap }

  val latin = listOf(FontGlyphGroup("Maiuscole", Uppercase), FontGlyphGroup("Minuscole", Lowercase)).map(::available)
  val scripts = scriptSamples.map(::available).filter { it.characters.isNotEmpty() }
  val letters = latin + scripts
  val groups = (letters + listOf(
    available(FontGlyphGroup("Cifre", "0123456789")),
    available(FontGlyphGroup("Segni", ".,:;!?-+()&@#%='/€")),
  )).filter { it.characters.isNotEmpty() }
  val hero = if (covers("Aa")) "Aa" else groups.firstOrNull()?.characters.orEmpty().take(2)
  val sentence = previewSentences.firstOrNull(::covers)
    ?: (letters.firstOrNull { it.characters.isNotEmpty() } ?: groups.firstOrNull())?.characters.orEmpty()
      .take(24).chunked(5).joinToString(" ")
  val detectedScripts = buildList {
    if (latin.any { it.characters.isNotEmpty() }) add("Latino")
    addAll(scripts.map { it.label })
  }
  val languages = languageAlphabets.filter { (_, letters) ->
    covers(letters + letters.uppercase(Locale.ROOT))
  }.map { it.first }
  return FontPreview(hero, sentence, groups, detectedScripts, languages)
}
