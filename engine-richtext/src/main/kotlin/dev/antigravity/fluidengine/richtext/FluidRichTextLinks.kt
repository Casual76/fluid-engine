package dev.antigravity.fluidengine.richtext

/**
 * Quali indirizzi possono diventare un link, e come si ripulisce quello che scrive una persona.
 *
 * Un link in un documento formattato finisce in un HTML che qualcun altro aprira' (una mail inviata, una
 * nota condivisa): un `javascript:` o un `data:` li' dentro e' codice che gira nel programma di chi
 * legge. Per questo l'editor tiene solo gli schemi che portano da qualche parte senza eseguire niente —
 * `http`, `https`, `mailto`, `tel` — e scarta il resto, **compresi gli indirizzi relativi** (in un HTML
 * incollato non hanno una base, e in una mail non ne avrebbero una). Il testo del link resta sempre:
 * si perde solo l'indirizzo.
 */
object FluidRichTextLinks {
  /** Gli schemi ammessi, in minuscolo. */
  val AllowedSchemes: Set<String> = setOf("http", "https", "mailto", "tel")

  /** Oltre questa lunghezza un indirizzo e' scartato: nessun link vero e' cosi' lungo, un attacco si'. */
  const val MaxLength: Int = 2048

  /**
   * L'indirizzo pronto per un link, o null se non e' sicuro.
   *
   * Toglie spazi e caratteri di controllo **ovunque** (i browser li ignorano dentro lo schema, ed e'
   * cosi' che `java&#10;script:` passa i filtri ingenui), poi vuole uno schema esplicito fra quelli
   * ammessi; per `http` e `https` anche un host. Lo schema esce in minuscolo.
   */
  fun sanitize(url: String): String? {
    if (url.length > MaxLength * 2) return null
    val compact = buildString(url.length) {
      for (char in url) {
        if (char > ' ' && char != '\u007F' && !char.isWhitespace() && char != '​' && char != '﻿') append(char)
      }
    }
    if (compact.isEmpty() || compact.length > MaxLength) return null
    val colon = compact.indexOf(':')
    if (colon <= 0) return null
    val scheme = compact.substring(0, colon)
    if (!scheme[0].isAsciiLetter() || scheme.any { !(it.isAsciiLetter() || it.isDigit() || it == '+' || it == '-' || it == '.') }) {
      return null
    }
    val lower = scheme.lowercase()
    if (lower !in AllowedSchemes) return null
    val rest = compact.substring(colon + 1)
    when (lower) {
      "http", "https" -> {
        if (!rest.startsWith("//")) return null
        val host = rest.substring(2).takeWhile { it != '/' && it != '?' && it != '#' }
        if (host.isEmpty() || host.startsWith('@')) return null
      }
      else -> if (rest.isEmpty()) return null
    }
    // Le virgolette e le parentesi angolari in un indirizzo vero sono codificate: lasciarle passare e'
    // solo un modo in piu' di uscire da un attributo in un lettore scritto male.
    if (rest.any { it == '"' || it == '<' || it == '>' || it == '\'' || it == '`' }) return null
    return lower + ":" + rest
  }

  /**
   * L'indirizzo che una persona scrive nel campo di un link, completato: `esempio.it` diventa
   * `https://esempio.it`, `nome@esempio.it` diventa `mailto:nome@esempio.it`, `+39 055 123` diventa
   * `tel:+39055123`. Null se anche cosi' non e' un indirizzo sicuro.
   */
  fun fromUserInput(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    sanitize(trimmed)?.let { return it }
    val hasScheme = trimmed.indexOf(':').let { it > 0 && trimmed.substring(0, it).all { c -> c.isAsciiLetter() } } &&
      !trimmed.substringAfter(':').all { it.isDigit() }
    if (hasScheme) return null
    if (trimmed.contains('@') && !trimmed.contains('/') && !trimmed.contains(' ')) {
      val (local, domain) = trimmed.substringBefore('@') to trimmed.substringAfter('@')
      if (local.isNotEmpty() && domain.contains('.')) return sanitize("mailto:$trimmed")
    }
    val phone = trimmed.filterNot { it == ' ' || it == '-' || it == '.' || it == '(' || it == ')' }
    if (phone.length >= 3 && (phone[0] == '+' || phone[0].isDigit()) && phone.drop(1).all { it.isDigit() }) {
      return sanitize("tel:$phone")
    }
    if (trimmed.contains('.') && !trimmed.contains(' ')) return sanitize("https://$trimmed")
    return null
  }

  /** Come si legge un indirizzo a una persona: senza `mailto:`/`tel:` e senza la barra finale. */
  fun displayText(url: String): String = when {
    url.startsWith("mailto:") -> url.removePrefix("mailto:")
    url.startsWith("tel:") -> url.removePrefix("tel:")
    else -> url.removeSuffix("/")
  }

  private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}

/**
 * Il documento come testo semplice: la parte «solo testo» di una mail, un'anteprima, una notifica.
 *
 * Ogni paragrafo una riga; le voci di elenco con `• ` o `1. `, le citazioni con `> ` (come le risposte
 * citate di sempre); un link il cui testo non e' gia' l'indirizzo e' seguito dall'indirizzo fra
 * parentesi, perche' in un testo semplice altrimenti si perderebbe. Grassetto, corsivo e sottolineato
 * spariscono.
 */
object FluidRichTextPlainText {
  /** Il testo semplice di [document]. */
  fun write(document: FluidRichTextDocument): String = buildString {
    for (index in 0 until document.paragraphCount) {
      if (index > 0) append('\n')
      val style = document.paragraphStyles[index]
      if (style.quote) append("> ")
      when (style.list) {
        FluidRichTextListKind.Bullet -> append("• ")
        FluidRichTextListKind.Numbered -> append(document.listNumber(index)).append(". ")
        null -> Unit
      }
      appendParagraph(document, index)
    }
  }

  private fun StringBuilder.appendParagraph(document: FluidRichTextDocument, index: Int) {
    val start = document.paragraphStart(index)
    val end = document.paragraphEnd(index)
    var openLink: String? = null
    val linkText = StringBuilder()
    fun closeLink() {
      val url = openLink ?: return
      val shown = linkText.toString()
      if (shown.trim() != FluidRichTextLinks.displayText(url) && shown.trim() != url) append(" (").append(url).append(')')
      openLink = null
      linkText.setLength(0)
    }
    document.forEachRun { runStart, runEnd, format ->
      val a = maxOf(runStart, start)
      val b = minOf(runEnd, end)
      if (a < b) {
        if (format.link != openLink) closeLink()
        val piece = document.text.substring(a, b)
        if (format.link != null) {
          openLink = format.link
          linkText.append(piece)
        }
        append(piece)
      }
    }
    closeLink()
  }
}
