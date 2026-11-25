package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto

/**
 * Le etichette delle persone, dal contratto del server (`Pampa-account\contract\v1\e2e.json` → `people.labels`, F10.5).
 * Una prova le confronta con la copia del contratto, così un cambio da una parte sola non passa. `<…>` si sostituisce
 * col valore ([fill]).
 *
 * Due etichette sono nuove di F10.6 e non sono ancora nel contratto ([WORDS_COMMIT], [WORDS_SALT]): servono
 * all'impegno delle quattro parole contro il «compleanno» (rapporto F10.6). Il server non le usa mai.
 */
object PeopleLabels {
  const val CODE = "pampa/v1/codice|"
  const val MAILBOX = "pampa/v1/casella|"
  const val CARD = "pampa/v1/scheda|"
  const val WORK_KEYS = "pampa/v1/chiavi-di-lavoro|<epoch>|"
  const val DELIVERY_PRINT = "pampa/v1/gettone|"
  const val INVITE_PRINT = "pampa/v1/invito|"
  const val WRITE_PRINT = "pampa/v1/scrittura|"
  const val BLOCKED_PRINT = "pampa/v1/bloccato|"
  const val CDXB_INFO = "pampa/v1/cdxb|<from>|<to>"
  const val CDXB_SIG = "pampa/v1/cdxb-firma|<from>|<to>|"
  const val PMX1_KEY = "pampa/v1/pmx1"
  const val PMX1_AAD = "|<from>|<to>|<thread>"
  const val PAIR = "pampa/v1/coppia"
  const val GEM = "pampa/v1/gemma"
  const val WORDS = "pampa/v1/parole"
  const val SPACE_AAD = "pampa/v1/spazio|<spaceId>|<epoch>|<recordId>|<version>"
  const val SPACE_SIG = "pampa/v1/spazio-firma|<spaceId>|<epoch>|<recordId>|<version>|"

  /** F10.6 · L'impegno di chi comincia lo scambio delle parole: `SHA-256(etichetta ‖ nonce ‖ radice ‖ enc ‖ enc dell'altro)`. */
  const val WORDS_COMMIT = "pampa/v1/parole-impegno|"

  /** F10.6 · Il sale delle parole e della gemma: `SHA-256(etichetta ‖ nonce di chi comincia ‖ nonce di chi risponde)`. */
  const val WORDS_SALT = "pampa/v1/parole-sale|"

  /** F10.6 · Il saluto della vicinanza e la runa (`NearbyHandshake` di Codex, con l'etichetta di Pampa). */
  const val NEARBY_NAME = "pampa/v1/vicino-nome|<day>"
  const val NEARBY_AUTH = "pampa/v1/vicino-firma"
  const val NEARBY_RUNE = "pampa/v1/vicino-runa"

  private val PLACEHOLDER = Regex("<(\\w+)>")

  /** Sostituisce i segnaposto di [template]; un segnaposto senza valore è un errore. */
  fun fill(template: String, vararg values: Pair<String, Any>): String {
    val map = values.toMap()
    return PLACEHOLDER.replace(template) { m -> requireNotNull(map[m.groupValues[1]]) { "manca <${m.groupValues[1]}> in $template" }.toString() }
  }

  internal fun utf8(template: String, vararg values: Pair<String, Any>): ByteArray = fill(template, *values).toByteArray(Charsets.UTF_8)
}

/**
 * Il codice Pampa e la casella, che nascono dalla radice Ed25519 (contratto `people.formulas.code` e `mailbox`): il
 * telefono li sa prima di firmare la scheda, e il server non li sceglie. È il `CodexId` dell'alpha col prefisso `PMP-`
 * (scelta D10, Q10: niente `CDX-`).
 *
 * Il codice ha 40 bit: rende **costoso**, non impossibile, che un server ostile dia una scheda falsa con il tuo codice
 * (critica S5). Per questo un contatto aggiunto col codice resta «da verificare».
 */
object PersonCode {
  const val PREFIX = "PMP"
  private val PATTERN = Regex("^PMP-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")

  /** `PMP-XXXX-XXXX` della radice [rootPub]: i primi 40 bit di `SHA-256("pampa/v1/codice|" ‖ radice)`. */
  fun of(rootPub: ByteArray): String {
    require(rootPub.size == 32) { "radice non valida" }
    val h = PampaCrypto.sha256(PeopleLabels.CODE.toByteArray(Charsets.UTF_8), rootPub)
    var v = 0L
    for (i in 0 until 5) v = (v shl 8) or (h[i].toLong() and 0xFF)
    val s = buildString { for (i in 7 downTo 0) append(Crockford32.ALPHABET[((v ushr (i * 5)) and 31).toInt()]) }
    return "$PREFIX-${s.substring(0, 4)}-${s.substring(4)}"
  }

  /** L'id della casella: i primi 16 byte di `SHA-256("pampa/v1/casella|" ‖ radice)`, in base64url (22 caratteri). */
  fun mailboxOf(rootPub: ByteArray): String =
    PampaBase64.encode(PampaCrypto.sha256(PeopleLabels.MAILBOX.toByteArray(Charsets.UTF_8), rootPub).copyOf(16))

  /**
   * Un codice scritto a mano, come lo legge il server (`normalizeCode`): maiuscole, O→0, I e L→1. Accetta anche gli
   * spazi al posto dei trattini e il codice senza `PMP-`; `null` se non è un codice.
   */
  fun normalize(input: String): String? {
    val s = input.trim().uppercase().replace('O', '0').replace('I', '1').replace('L', '1')
    if (PATTERN.matches(s)) return s
    val body = s.filter { it.isLetterOrDigit() }.removePrefix(PREFIX)
    if (body.length != 8) return null
    return "$PREFIX-${body.take(4)}-${body.drop(4)}".takeIf { PATTERN.matches(it) }
  }

  fun isValid(input: String): Boolean = normalize(input) != null
}

/** Le impronte dei gettoni, come le tiene la casella (contratto `people.formulas.print`). */
object PeoplePrints {
  fun delivery(token: ByteArray): String = print(PeopleLabels.DELIVERY_PRINT, token)
  fun invite(token: ByteArray): String = print(PeopleLabels.INVITE_PRINT, token)
  fun write(token: ByteArray): String = print(PeopleLabels.WRITE_PRINT, token)

  /** L'impronta di una casella bloccata: `SHA-256(utf8("pampa/v1/bloccato|" + casella))`. Il server la sa ricalcolare. */
  fun blocked(mailbox: String): String = PampaBase64.encode(PampaCrypto.sha256((PeopleLabels.BLOCKED_PRINT + mailbox).toByteArray(Charsets.UTF_8)))

  private fun print(label: String, token: ByteArray): String {
    require(token.size == 32) { "un gettone è di 32 byte" }
    return PampaBase64.encode(PampaCrypto.sha256(label.toByteArray(Charsets.UTF_8), token))
  }

  /** Un gettone nuovo: 32 byte casuali in base64url (43 caratteri). */
  fun newToken(): String = PampaBase64.encode(PampaCrypto.random(32))
}
