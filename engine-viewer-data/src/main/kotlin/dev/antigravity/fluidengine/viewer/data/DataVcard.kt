package dev.antigravity.fluidengine.viewer.data

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un valore con la sua etichetta: «cellulare» → «+39 333 1234567». */
internal class DataLabeled(val label: String, val value: String)

/** Un contatto, pronto per la scheda. */
internal class DataContact(
  val version: String?,
  val name: String,
  val structuredName: String?,
  val nickname: String?,
  val organization: String?,
  val title: String?,
  val phones: List<DataLabeled>,
  val emails: List<DataLabeled>,
  val addresses: List<DataLabeled>,
  val urls: List<DataLabeled>,
  val birthday: String?,
  val anniversary: String?,
  val note: String?,
  val photo: ByteArray?,
  val photoRemote: Boolean,
  val categories: List<String>,
  val others: List<DataLabeled>,
)

/**
 * F6C.13 · Il lettore vCard 2.1, 3.0 e 4.0 (§1.3), in Kotlin puro: più contatti in un file; le righe piegate di 3.0 e
 * 4.0 e le righe spezzate dal quoted-printable di 2.1 (con il loro `CHARSET`); i tipi senza nome di 2.1
 * (`TEL;CELL;VOICE:`); i gruppi di Apple (`item1.TEL` con la sua `item1.X-ABLabel`); la foto in base64 (anche come
 * `data:` di 4.0). Una foto che è un indirizzo web non si scarica: il contatto ha le iniziali, e il pannello lo dice.
 */
internal object DataVcard {

  /** Le righe logiche: la piegatura di 3.0/4.0, e in 2.1 il «=» in fondo a una riga quoted-printable che la continua. */
  fun unfold(text: String): List<String> {
    val out = ArrayList<String>()
    var qpOpen = false
    for (raw in text.split('\n')) {
      val line = raw.removeSuffix("\r")
      when {
        qpOpen && out.isNotEmpty() -> out[out.lastIndex] = out.last().removeSuffix("=") + line
        line.isEmpty() -> continue
        (line[0] == ' ' || line[0] == '\t') && out.isNotEmpty() -> out[out.lastIndex] = out.last() + line.substring(1)
        else -> out += line
      }
      val last = out.lastOrNull().orEmpty()
      qpOpen = last.endsWith("=") && last.substringBefore(':').uppercase(Locale.ROOT).contains("QUOTED-PRINTABLE")
    }
    return out
  }

  fun parse(text: String): List<DataContact> {
    val contacts = ArrayList<DataContact>()
    var current: ArrayList<IcsProperty>? = null
    // Le etichette di Apple: «item1.X-ABLabel:Ufficio» vale per «item1.TEL».
    var groups = HashMap<String, String>()
    val groupOf = HashMap<IcsProperty, String>()
    for (line in unfold(text)) {
      val p = DataIcs.property(line) ?: continue
      val group = line.substringBefore(':').substringBefore(';').let { head -> if ('.' in head) head.substringBefore('.') else null }
      when {
        p.name == "BEGIN" && p.value.trim().equals("VCARD", ignoreCase = true) -> {
          current = ArrayList()
          groups = HashMap()
        }
        p.name == "END" && p.value.trim().equals("VCARD", ignoreCase = true) -> {
          current?.let { contacts += contact(it, groups, groupOf) }
          current = null
        }
        current != null -> {
          if (p.name == "X-ABLABEL" && group != null) groups[group] = p.value.trim().removePrefix("_\$!<").removeSuffix(">!\$_")
          else {
            current.add(p)
            if (group != null) groupOf[p] = group
          }
        }
      }
    }
    return contacts
  }

  /** Il valore di testo: il quoted-printable di 2.1 nel suo charset, poi gli escape. */
  private fun text(p: IcsProperty): String {
    val encoding = p.param("ENCODING")?.uppercase(Locale.ROOT)
    val raw = if (encoding == "QUOTED-PRINTABLE") {
      val bytes = DataMime.quotedPrintable(p.value.toByteArray(Charsets.ISO_8859_1))
      String(bytes, DataText.charsetOf(p.param("CHARSET")) ?: Charsets.UTF_8)
    } else {
      p.value
    }
    return DataIcs.unescape(raw).trim()
  }

  private val TypeNames = mapOf(
    "CELL" to "cellulare", "MOBILE" to "cellulare", "HOME" to "casa", "WORK" to "lavoro", "FAX" to "fax", "PAGER" to "cercapersone",
    "MAIN" to "principale", "OTHER" to "altro", "IPHONE" to "iPhone", "INTERNET" to "", "VOICE" to "", "PREF" to "", "TEXT" to "",
    "X-MOBILE" to "cellulare", "CAR" to "auto", "VIDEO" to "video", "MSG" to "messaggi", "PARCEL" to "", "POSTAL" to "", "DOM" to "",
    "INTL" to "", "X400" to "",
  )

  private fun label(p: IcsProperty, groups: Map<String, String>, groupOf: Map<IcsProperty, String>, fallback: String): String {
    groupOf[p]?.let { g -> groups[g]?.let { return it.lowercase(Locale.ITALIAN) } }
    val types = p.param("TYPE")?.split(',')?.map { it.trim().uppercase(Locale.ROOT) }.orEmpty()
    val words = types.mapNotNull { t -> TypeNames[t] ?: t.lowercase(Locale.ITALIAN).takeIf { !it.startsWith("x-") } }.filter { it.isNotEmpty() }.distinct()
    return words.joinToString(" · ").ifEmpty { fallback }
  }

  private fun contact(props: List<IcsProperty>, groups: Map<String, String>, groupOf: Map<IcsProperty, String>): DataContact {
    fun first(name: String) = props.firstOrNull { it.name == name }
    val n = first("N")?.let { text(it) }?.split(';')
    val structured = n?.let { parts ->
      listOf(parts.getOrNull(3), parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(0), parts.getOrNull(4))
        .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }.joinToString(" ").takeIf { it.isNotEmpty() }
    }
    val org = first("ORG")?.let { text(it) }?.split(';')?.filter { it.isNotBlank() }?.joinToString(" · ")?.takeIf { it.isNotEmpty() }
    val fn = first("FN")?.let { text(it) }?.takeIf { it.isNotEmpty() }
    val emailFirst = props.firstOrNull { it.name == "EMAIL" }?.let { text(it) }
    val name = fn ?: structured ?: org ?: emailFirst ?: "Senza nome"
    val photoProperty = first("PHOTO")
    var photoRemote = false
    val photo = photoProperty?.let { p ->
      val v = p.value.trim()
      val encoding = p.param("ENCODING")?.uppercase(Locale.ROOT)
      when {
        v.startsWith("data:", ignoreCase = true) -> DataMime.base64(v.substringAfter(',').toByteArray(Charsets.ISO_8859_1))
        encoding == "B" || encoding == "BASE64" -> DataMime.base64(v.toByteArray(Charsets.ISO_8859_1))
        v.startsWith("http", ignoreCase = true) -> { photoRemote = true; null }
        p.param("VALUE")?.equals("URI", ignoreCase = true) == true -> { photoRemote = true; null }
        v.length > 64 && v.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' } -> DataMime.base64(v.toByteArray(Charsets.ISO_8859_1))
        else -> null
      }
    }?.takeIf { it.isNotEmpty() }
    val address = props.filter { it.name == "ADR" }.map { p ->
      val parts = text(p).split(';').map { it.trim() }
      // pobox; esteso; via; città; regione; CAP; paese
      val street = listOfNotNull(parts.getOrNull(0), parts.getOrNull(1), parts.getOrNull(2)).filter { it.isNotEmpty() }.joinToString(", ")
      val city = listOfNotNull(parts.getOrNull(5), parts.getOrNull(3), parts.getOrNull(4)?.let { if (it.isNotEmpty()) "($it)" else it })
        .filter { it.isNotEmpty() }.joinToString(" ")
      val lines = listOf(street, city, parts.getOrNull(6).orEmpty()).filter { it.isNotEmpty() }.joinToString("\n")
      DataLabeled(label(p, groups, groupOf, "indirizzo"), lines)
    }.filter { it.value.isNotEmpty() }
    val known = setOf("BEGIN", "END", "VERSION", "N", "FN", "ORG", "TITLE", "TEL", "EMAIL", "ADR", "URL", "BDAY", "ANNIVERSARY",
      "NOTE", "PHOTO", "CATEGORIES", "NICKNAME", "PRODID", "REV", "UID", "LABEL", "KIND", "SOURCE", "SORT-STRING", "X-ABUID")
    val others = props.filter { it.name !in known && !it.name.startsWith("X-AB") && !it.name.startsWith("X-ANDROID") }.mapNotNull { p ->
      val value = text(p).takeIf { it.isNotEmpty() && it.length < 300 } ?: return@mapNotNull null
      val title = when (p.name) {
        "IMPP" -> "messaggi"
        "ROLE" -> "ruolo"
        "GEO" -> "posizione"
        "TZ" -> "fuso orario"
        "GENDER" -> "genere"
        "LANG" -> "lingua"
        "X-SOCIALPROFILE" -> "profilo"
        else -> p.name.lowercase(Locale.ITALIAN).removePrefix("x-")
      }
      DataLabeled(title, value)
    }
    return DataContact(
      version = first("VERSION")?.value?.trim(),
      name = name,
      structuredName = structured,
      nickname = first("NICKNAME")?.let { text(it) }?.takeIf { it.isNotEmpty() },
      organization = org,
      title = first("TITLE")?.let { text(it) }?.takeIf { it.isNotEmpty() },
      phones = props.filter { it.name == "TEL" }.map { DataLabeled(label(it, groups, groupOf, "telefono"), text(it).removePrefix("tel:")) }.filter { it.value.isNotEmpty() },
      emails = props.filter { it.name == "EMAIL" }.map { DataLabeled(label(it, groups, groupOf, "e-mail"), text(it)) }.filter { it.value.isNotEmpty() },
      addresses = address,
      urls = props.filter { it.name == "URL" }.map { DataLabeled(label(it, groups, groupOf, "sito"), text(it)) }.filter { it.value.isNotEmpty() },
      birthday = first("BDAY")?.let { date(text(it)) },
      anniversary = first("ANNIVERSARY")?.let { date(text(it)) },
      note = first("NOTE")?.let { text(it) }?.takeIf { it.isNotEmpty() },
      photo = photo,
      photoRemote = photoRemote,
      categories = props.filter { it.name == "CATEGORIES" }.flatMap { text(it).split(',') }.map { it.trim() }.filter { it.isNotEmpty() },
      others = others,
    )
  }

  /** «19900315», «1990-03-15», «--0315», «--03-15» → «15 marzo 1990», «15 marzo». */
  fun date(value: String): String {
    val v = value.trim().substringBefore('T')
    val italian = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
    return try {
      when {
        Regex("""^\d{8}$""").matches(v) -> LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).format(italian)
        Regex("""^\d{4}-\d{2}-\d{2}$""").matches(v) -> LocalDate.parse(v).format(italian)
        Regex("""^--\d{2}-?\d{2}$""").matches(v) -> {
          val digits = v.filter { it.isDigit() }
          MonthDay.of(digits.take(2).toInt(), digits.drop(2).toInt()).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))
        }
        else -> value.trim()
      }
    } catch (_: Exception) {
      value.trim()
    }
  }
}
