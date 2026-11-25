package dev.antigravity.fluidengine.auth

/**
 * L'ingresso dell'estensione PRF di WebAuthn: il «sale» per cui la passkey restituisce 32 byte segreti e sempre uguali
 * ([WebAuthn, prf](https://www.w3.org/TR/webauthn-3/#prf-extension), fiducia alta).
 *
 * **Lo scrive sempre il modulo, mai il server** (critica S9, `chiavi.md` § 3.2). Un server ostile che mandasse un
 * ingresso diverso, o un `evalByCredential`, non ruberebbe niente (l'uscita non gli arriva), ma farebbe uscire un valore
 * diverso: le porte dei dati cifrati non si aprirebbero più, e l'utente verrebbe spinto verso il codice di recupero. Per
 * questo l'ingresso è un tipo, non un pezzo di JSON: [FluidWebAuthnJson] toglie qualunque `prf` arrivi nelle opzioni e
 * mette questo.
 *
 * L'ingresso è **fisso e uguale per tutti** ([PampaV1]): all'accesso il server non sa ancora quale passkey userà
 * l'utente. L'uscita resta segreta e diversa per ogni passkey, perché la decide il segreto interno della passkey. La
 * piattaforma non usa l'ingresso così com'è: ne fa lo SHA-256 con il prefisso «WebAuthn PRF» prima di passarlo al
 * gestore (stessa fonte), quindi due servizi con lo stesso ingresso su due RP ID diversi hanno comunque uscite diverse.
 */
class FluidPrfInput private constructor(private val value: ByteArray) {

  /** I byte dell'ingresso (una copia). */
  fun bytes(): ByteArray = value.copyOf()

  /** L'ingresso in base64url senza «=», come va nel JSON (`extensions.prf.eval.first`). */
  val base64Url: String get() = Base64Url.encode(value)

  override fun equals(other: Any?): Boolean = other is FluidPrfInput && other.value.contentEquals(value)

  override fun hashCode(): Int = value.contentHashCode()

  override fun toString(): String = "FluidPrfInput($base64Url)"

  companion object {
    /** La costante dell'Account Pampa, `chiavi.md` § 3.2. */
    const val PAMPA_V1_LABEL: String = "pampa/v1/prf"

    /** `SHA-256("pampa/v1/prf")`: l'ingresso di Pampa Store, all'accesso e alla creazione. */
    val PampaV1: FluidPrfInput = fromLabel(PAMPA_V1_LABEL)

    /** L'ingresso `SHA-256(label)`, per un servizio che non è l'Account Pampa (una copia dell'engine, regola R8). */
    fun fromLabel(label: String): FluidPrfInput {
      require(label.isNotEmpty()) { "L'etichetta del PRF non può essere vuota" }
      return FluidPrfInput(sha256(label.toByteArray(Charsets.UTF_8)))
    }

    /** Un ingresso già fatto: da 1 a 64 byte. */
    fun ofBytes(bytes: ByteArray): FluidPrfInput {
      require(bytes.size in 1..64) { "L'ingresso del PRF va da 1 a 64 byte, non ${bytes.size}" }
      return FluidPrfInput(bytes.copyOf())
    }
  }
}
