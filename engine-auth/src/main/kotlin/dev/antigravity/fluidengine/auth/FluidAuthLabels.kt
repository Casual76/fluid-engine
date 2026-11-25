package dev.antigravity.fluidengine.auth

/**
 * Le frasi di [AuthResult]: quelle che vede l'utente quando una passkey o una password non va.
 *
 * **In italiano di serie**, come `FluidLabels` di `engine-ui` (che questo modulo non vede: non porta Compose). Un'app
 * in una lingua sola non scrive niente; una tradotta le passa a [FluidPasskeys], [FluidPasswords] e a
 * [AuthResult.phrase], tutte: una sola lasciata di serie è una frase italiana in un'app inglese. [english] è pronta.
 *
 * Le frasi dicono cosa fare, non cosa è successo dentro: nessun nome di eccezione, nessun codice. Sono generiche
 * di proposito (il modulo non sa per quale servizio entri): un'app che vuole il suo nome lo mette con `copy`, per
 * esempio `noCredential = "Su questo telefono non trovo una passkey per Pampa."` (scheda `passkey`, B § 9.2).
 */
data class FluidAuthLabels(
  /** [AuthResult.Cancelled]. Di solito non si mostra: l'utente sa di aver chiuso il foglio. */
  val cancelled: String = "Operazione annullata.",
  /** [AuthResult.NoCredential] per una passkey. */
  val noCredential: String = "Su questo telefono non trovo una passkey da usare qui.",
  /** [AuthResult.NoCredential] per una password o un codice salvato ([FluidPasswords.get]). */
  val noPassword: String = "Nel gestore delle password non trovo niente da usare qui.",
  /** [AuthResult.Why.OldAndroid]. */
  val oldAndroid: String = "Le passkey funzionano da Android 9 in poi. Su questo telefono puoi entrare in un altro modo.",
  /** [AuthResult.Why.NoProvider]. */
  val noProvider: String = "Non trovo un gestore di passkey e password. Puoi sceglierne uno nelle impostazioni del telefono.",
  /** [AuthResult.Why.NoScreenLock]. */
  val noScreenLock: String = "Per usare una passkey serve un blocco schermo. Impostalo nelle impostazioni del telefono, poi riprova.",
  /**
   * Il gestore non riconosce l'app per quel dominio (`SecurityError`): di solito `assetlinks.json` non c'è ancora,
   * non è raggiungibile o non ha l'impronta della firma di quest'app.
   */
  val wrongDomain: String = "Il servizio non riconosce ancora quest'app. Riprova più tardi.",
  /** Alla creazione c'è già una passkey per lo stesso account (`InvalidStateError`, `excludeCredentials`). */
  val alreadyExists: String = "Su questo telefono c'è già una passkey per questo account.",
  /** Il foglio si è interrotto (un'altra app, lo schermo spento): [AuthResult.Failed.retry] è vero. */
  val interrupted: String = "Si è interrotto tutto a metà. Riprova.",
  /** La richiesta non è valida: un JSON che non è WebAuthn, o una risposta del gestore che non si legge. */
  val badRequest: String = "La richiesta non è valida. Riprova più tardi.",
  /** Tutto il resto. */
  val generic: String = "Qualcosa non è andato. Riprova.",
) {
  companion object {
    /** Le stesse frasi in inglese. Un'app in un'altra lingua parte da questa con `copy(...)` o le scrive tutte. */
    fun english(): FluidAuthLabels = FluidAuthLabels(
      cancelled = "Cancelled.",
      noCredential = "There's no passkey on this phone to use here.",
      noPassword = "There's nothing in your password manager to use here.",
      oldAndroid = "Passkeys need Android 9 or later. You can sign in another way on this phone.",
      noProvider = "There's no passkey and password manager. You can choose one in your phone's settings.",
      noScreenLock = "Passkeys need a screen lock. Set one in your phone's settings, then try again.",
      wrongDomain = "The service doesn't recognise this app yet. Try again later.",
      alreadyExists = "There's already a passkey for this account on this phone.",
      interrupted = "It stopped halfway. Try again.",
      badRequest = "The request isn't valid. Try again later.",
      generic = "Something went wrong. Try again.",
    )
  }
}
