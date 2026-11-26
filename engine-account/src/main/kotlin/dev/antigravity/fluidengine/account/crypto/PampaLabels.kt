package dev.antigravity.fluidengine.account.crypto

/**
 * Le etichette della cifratura dell'account: gli `info` di HKDF e di HPKE e i dati associati. Vengono dal contratto
 * del server, `Pampa-account\contract\v1\e2e.json` (F10.3), che le fissa per lo Store e per il telefono finto del
 * banco; una prova di questo modulo confronta ognuna con la copia del contratto, così un cambio da una parte sola
 * non passa.
 *
 * `<…>` nel modello si sostituisce col valore ([fill]). Il separatore è sempre «|».
 */
object PampaLabels {
  const val RECOVERY_AUTH = "pampa/v1/recovery-auth"
  const val DOOR_SEED = "pampa/v1/door"
  const val DOOR_SEAL = "pampa/v1/door-seal|<doorId>|<epoch>"
  const val DOOR_TAG = "pampa/v1/doors"
  const val DOOR_CHECK = "pampa/v1/door-check"
  const val EPOCH_LINK = "pampa/v1/epoch"
  const val APP_KEY = "pampa/v1/app/<package>"
  const val BACKUP_KEY = "pampa/v1/backup/<package>"
  const val SHARED_KEY = "pampa/v1/shared/<name>"
  const val TRANSFER_SEAL = "pampa/v1/transfer|<transferId>"
  const val TRANSFER_MAC = "pampa/v1/transfer-mac"

  /** L'ingresso fisso del PRF della passkey: lo scrive lo Store, mai il server (critica S9). */
  const val PRF_INPUT_TEXT = "pampa/v1/prf"

  /** I nomi delle chiavi comuni (`b\chiavi.md` § 3): i luoghi, la rubrica, le chiavi private della persona. */
  const val SHARED_PLACES = "places"
  const val SHARED_RUBRICA = "rubrica"
  const val SHARED_PERSONA = "persona"

  /** `SHA-256("pampa/v1/prf")`: i 32 byte che lo Store mette in `prf.eval.first`. */
  fun prfInput(): ByteArray = PampaCrypto.sha256(PRF_INPUT_TEXT.toByteArray(Charsets.UTF_8))

  /** Sostituisce i segnaposto di [template] coi valori di [values]; un segnaposto senza valore è un errore. */
  fun fill(template: String, vararg values: Pair<String, Any>): String {
    val map = values.toMap()
    return PLACEHOLDER.replace(template) { m ->
      val key = m.groupValues[1]
      requireNotNull(map[key]) { "manca il valore di <$key> in $template" }.toString()
    }
  }

  /**
   * I dati associati di una riga sincronizzata: `accountId|pacchetto|raccolta|idRiga|versione` (K4).
   *
   * R2 · Raccolta e id li sceglie l'app, e il separatore non ha lunghezze: con un «|» dentro, `a|b` + `c` darebbe gli
   * stessi dati associati di `a` + `b|c`, e il server potrebbe scambiare le due righe. Per questo un «|» lì è
   * [PampaCryptoException], subito, dove l'id nasce.
   */
  fun recordAad(accountId: String, packageName: String, collection: String, recordId: String, version: Long): ByteArray {
    if ('|' in collection || '|' in recordId) throw PampaCryptoException("«|» non può stare nella raccolta né nell'id di una riga")
    return "$accountId|$packageName|$collection|$recordId|$version".toByteArray(Charsets.UTF_8)
  }

  /** I dati associati di un pacchetto di backup: `accountId|pacchetto|backup|n|epoca` (`b\chiavi.md` § 7). */
  fun backupAad(accountId: String, packageName: String, n: Long, epoch: Int): ByteArray =
    "$accountId|$packageName|backup|$n|$epoch".toByteArray(Charsets.UTF_8)

  private val PLACEHOLDER = Regex("<(\\w+)>")
}
