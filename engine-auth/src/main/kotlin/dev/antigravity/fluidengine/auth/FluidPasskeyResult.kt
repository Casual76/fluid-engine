package dev.antigravity.fluidengine.auth

/**
 * Una passkey appena creata o appena usata, già divisa fra quello che va al server e quello che resta sul telefono.
 *
 * - [responseJson] è la risposta WebAuthn **da spedire**: è quella di Credential Manager con i risultati del PRF tolti
 *   (`chiavi.md` § 3.2 e K6). Il server dell'Account Pampa rifiuta una risposta che li contiene ancora
 *   (`src/guards.ts`, `assertNoPrfOutput`), e i test di questo modulo lo provano coi vettori fatti dal server.
 * - [prfOutput] è l'uscita del PRF, **solo per il telefono**: 32 byte che non si scrivono nei log, non si spediscono e
 *   non si mostrano. [toString] non li stampa.
 * - [prfEnabled] dice, alla creazione, se il gestore dichiara il PRF (`clientExtensionResults.prf.enabled`). Alcuni
 *   gestori lo dichiarano ma danno il valore solo all'accesso (Samsung Pass, secondo un test di terzi): per questo lo
 *   Store fa un accesso subito dopo la creazione.
 * - [authenticatorData] porta i flag UP, UV, BE e BS, e alla creazione l'AAGUID del gestore.
 */
class FluidPasskeyResult internal constructor(
  /** L'id della credenziale, in base64url. */
  val credentialId: String,
  /** La risposta WebAuthn da spedire al server, senza i risultati del PRF. */
  val responseJson: String,
  /** I dati dell'autenticatore: flag, contatore, AAGUID alla creazione. */
  val authenticatorData: FluidAuthenticatorData,
  /** Alla creazione: il gestore dichiara il PRF. `null` se non lo dice (e sempre all'accesso, dove non c'è). */
  val prfEnabled: Boolean?,
  private val prfFirst: ByteArray?,
  /** I trasporti dichiarati alla creazione (`internal`, `hybrid`…); vuoto all'accesso. */
  val transports: List<String>,
  /** All'accesso: l'id dell'utente scritto nella passkey (`user.id` della creazione), in base64url. */
  val userHandle: String?,
  /** L'origine scritta in `clientDataJSON`: per un'app, `android:apk-key-hash:` e l'impronta della firma. */
  val origin: String?,
) {
  /** C'è l'uscita del PRF. */
  val hasPrfOutput: Boolean get() = prfFirst != null

  /** L'uscita del PRF per l'ingresso dato (una copia), o `null`. Resta sul telefono. */
  fun prfOutput(): ByteArray? = prfFirst?.copyOf()

  override fun toString(): String =
    "FluidPasskeyResult(credentialId=$credentialId, prfEnabled=$prfEnabled, " +
      "prfOutput=${if (prfFirst != null) "${prfFirst.size} byte, nascosti" else "assente"}, $authenticatorData)"
}
