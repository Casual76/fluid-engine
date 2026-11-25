package dev.antigravity.fluidengine.auth

/**
 * I dati dell'autenticatore di una risposta WebAuthn: l'hash dell'RP ID, i flag, il contatore e, alla creazione,
 * l'AAGUID del gestore ([WebAuthn § 6.1](https://www.w3.org/TR/webauthn-3/#sctn-authenticator-data), fiducia alta).
 *
 * I flag che contano per l'Account Pampa (architettura § 1.2, punto 5):
 * - [userPresent] (UP) e [userVerified] (UV): il server li vuole accesi tutti e due;
 * - [backupEligible] (BE) e [backedUp] (BS): la passkey può essere sincronizzata, ed è sincronizzata adesso. Con BS una
 *   passkey sopravvive alla perdita del telefono, ed è quello che fa verde «Il tuo accesso è al sicuro?».
 *
 * Il server li rilegge e li verifica da sé: qui servono al telefono per sapere subito che passkey ha davanti (la prova
 * del PRF li scrive a schermo), non come prova di niente.
 */
class FluidAuthenticatorData private constructor(
  private val rpIdHashBytes: ByteArray,
  /** Il byte dei flag, com'è. */
  val flags: Int,
  /** Il contatore delle firme. Le passkey sincronizzate lo tengono a zero. */
  val signCount: Long,
  private val aaguidBytes: ByteArray?,
  private val credentialIdBytes: ByteArray?,
) {
  /** UP: l'utente era presente. */
  val userPresent: Boolean get() = flags and UP != 0

  /** UV: l'utente si è fatto riconoscere (impronta, volto, PIN). */
  val userVerified: Boolean get() = flags and UV != 0

  /** BE: la passkey può essere sincronizzata fra dispositivi. Non cambia mai per la stessa passkey. */
  val backupEligible: Boolean get() = flags and BE != 0

  /** BS: la passkey è sincronizzata adesso. Può cambiare nel tempo. */
  val backedUp: Boolean get() = flags and BS != 0

  /** AT: ci sono i dati della credenziale (solo alla creazione). */
  val hasAttestedCredential: Boolean get() = flags and AT != 0

  /** ED: ci sono estensioni firmate dall'autenticatore. */
  val hasExtensions: Boolean get() = flags and ED != 0

  /**
   * L'AAGUID del gestore, nella forma `ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4`, solo alla creazione. Con
   * `attestation: "none"` un gestore può darlo tutto a zero: allora [aaguidIsZero] è vero.
   */
  val aaguid: String? get() = aaguidBytes?.let { formatUuid(it) }

  /** L'AAGUID è tutto a zero (il gestore non lo dichiara). */
  val aaguidIsZero: Boolean get() = aaguidBytes?.all { it == 0.toByte() } == true

  /** L'id della credenziale scritto nei dati della creazione, in base64url. */
  val credentialId: String? get() = credentialIdBytes?.let { Base64Url.encode(it) }

  /** L'hash SHA-256 dell'RP ID (una copia). */
  fun rpIdHash(): ByteArray = rpIdHashBytes.copyOf()

  /** Vero se questi dati sono per l'RP ID dato. */
  fun isFor(rpId: String): Boolean = sha256(rpId.toByteArray(Charsets.UTF_8)).contentEquals(rpIdHashBytes)

  override fun toString(): String =
    "FluidAuthenticatorData(UP=$userPresent, UV=$userVerified, BE=$backupEligible, BS=$backedUp, " +
      "signCount=$signCount, aaguid=$aaguid)"

  companion object {
    private const val UP = 0x01
    private const val UV = 0x04
    private const val BE = 0x08
    private const val BS = 0x10
    private const val AT = 0x40
    private const val ED = 0x80

    /** Legge i byte dei dati dell'autenticatore. Lancia [FluidWebAuthnJsonException] se non sono validi. */
    fun parse(bytes: ByteArray): FluidAuthenticatorData {
      if (bytes.size < 37) throw FluidWebAuthnJsonException("authenticatorData troppo corti")
      val flags = bytes[32].toInt() and 0xff
      val counter = (33 until 37).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xff) }
      var aaguid: ByteArray? = null
      var credentialId: ByteArray? = null
      if (flags and AT != 0) {
        if (bytes.size < 55) throw FluidWebAuthnJsonException("authenticatorData: dati della credenziale troncati")
        aaguid = bytes.copyOfRange(37, 53)
        val idLength = ((bytes[53].toInt() and 0xff) shl 8) or (bytes[54].toInt() and 0xff)
        if (bytes.size < 55 + idLength) throw FluidWebAuthnJsonException("authenticatorData: id troncato")
        credentialId = bytes.copyOfRange(55, 55 + idLength)
      }
      return FluidAuthenticatorData(bytes.copyOfRange(0, 32), flags, counter, aaguid, credentialId)
    }

    /** Legge i dati dell'autenticatore dentro un `attestationObject` (CBOR, campo `authData`). */
    fun fromAttestationObject(bytes: ByteArray): FluidAuthenticatorData {
      val map = CborReader(bytes).readItem() as? Map<*, *>
        ?: throw FluidWebAuthnJsonException("attestationObject non è una mappa CBOR")
      val authData = map["authData"] as? ByteArray
        ?: throw FluidWebAuthnJsonException("attestationObject senza authData")
      return parse(authData)
    }

    private fun formatUuid(b: ByteArray): String {
      val hex = b.toHex()
      return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
  }
}
