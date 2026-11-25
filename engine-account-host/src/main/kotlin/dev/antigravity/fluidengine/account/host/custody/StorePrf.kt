package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.auth.FluidPasskeyResult
import dev.antigravity.fluidengine.auth.FluidPrfInput
import dev.antigravity.fluidengine.auth.FluidWebAuthnJson

/**
 * Il PRF della passkey lo decide lo Store, non il server (`b\chiavi.md` § 3.2; critica S9). Il lavoro lo fa `engine-auth`
 * (F10.2), con l'ingresso fisso dell'Account Pampa ([FluidPrfInput.PampaV1], `SHA-256("pampa/v1/prf")`, lo stesso di
 * `PampaLabels.prfInput()`):
 *
 * - [assertionRequest] e [creationRequest]: dalle opzioni WebAuthn che arrivano dal server tolgono ogni estensione `prf`
 *   (anche `evalByCredential`) e mettono l'ingresso dello Store. Un server ostile non può cambiare l'ingresso e rendere
 *   inutili le porte.
 * - La risposta della passkey si legge con `FluidWebAuthnJson.readAssertion`/`readCreation`: `responseJson` è già senza
 *   i risultati del PRF (K6), e l'uscita resta in `prfOutput()`, da cui nasce la porta ([door]).
 * - [leaksPrfOutput]: la seconda cintura. `AccountServerClient` la usa su ogni corpo JSON prima di spedirlo.
 */
object StorePrf {
  /** L'ingresso del PRF dello Store, all'accesso e alla creazione. */
  val input: FluidPrfInput get() = FluidPrfInput.PampaV1

  /** Le opzioni di accesso per Credential Manager, col PRF dello Store al posto di quello del server. */
  fun assertionRequest(optionsJson: String): String = FluidWebAuthnJson.assertionRequest(optionsJson, input)

  /** Le opzioni di creazione per Credential Manager, col PRF dello Store (chiesto già alla creazione). */
  fun creationRequest(optionsJson: String): String = FluidWebAuthnJson.creationRequest(optionsJson, input)

  /** La porta della passkey appena usata: vedi [DoorSecret.fromPasskey]. */
  fun door(result: FluidPasskeyResult): DoorSecret = DoorSecret.fromPasskey(result)

  /** Vero se [json] porta ancora un'uscita del PRF, in un punto qualunque: una richiesta così non parte. */
  fun leaksPrfOutput(json: String): Boolean = FluidWebAuthnJson.hasPrfOutput(json)
}
