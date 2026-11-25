package dev.antigravity.fluidengine.vault

/**
 * Le frasi pronte della cassaforte: i dialoghi e i casi che la persona deve capire senza sapere cos'è un Keystore.
 * Italiano di serie, [English] per un'app in inglese; un'app le cambia con `copy`.
 *
 * Il tono è quello di Apple quando una cosa si chiude per sicurezza: si dice cos'è successo e cosa fare, senza colpe e
 * senza parole tecniche («Hai aggiunto un'impronta», non «KeyPermanentlyInvalidatedException»).
 */
data class VaultLabels(
  /** Il titolo di serie del dialogo. */
  val unlockTitle: String = "Sblocca",
  /** Il tasto in basso del dialogo dell'impronta, quando la riserva è il codice del telefono. */
  val useDeviceCredential: String = "Usa il codice del telefono",
  /** Il tasto in basso del dialogo dell'impronta, quando la riserva è il segreto dell'app. */
  val useAppSecret: String = "Usa il PIN dell'app",
  /** Un'impronta o un volto aggiunti, con il codice del telefono come riserva. */
  val biometricsChanged: String = "Hai cambiato le impronte o il volto del telefono: per sicurezza questa sezione si è " +
    "chiusa. Aprila con il codice del telefono, poi basterà di nuovo l'impronta.",
  /** Un'impronta o un volto aggiunti, con il PIN dell'app come riserva. */
  val biometricsChangedSecret: String = "Hai cambiato le impronte o il volto del telefono: per sicurezza questa sezione " +
    "si è chiusa. Aprila con il PIN dell'app, poi basterà di nuovo l'impronta.",
  /** Un'impronta o un volto aggiunti, senza riserva. */
  val biometricsChangedLost: String = "Hai cambiato le impronte o il volto del telefono: per sicurezza i dati di questa " +
    "sezione non si possono più aprire. Si ricomincia da una sezione vuota.",
  /** Il blocco schermo tolto. */
  val screenLockRemoved: String = "Il blocco schermo è stato tolto, e con lui le chiavi di questa sezione: i suoi dati " +
    "non si possono più aprire. Si ricomincia da una sezione vuota.",
  /** Il blocco schermo tolto, con il PIN dell'app come riserva. */
  val screenLockRemovedSecret: String = "Il blocco schermo è stato tolto: apri questa sezione con il PIN dell'app.",
  /** La chiave sparita (Keystore azzerato, dati da un altro telefono). */
  val keyLost: String = "La chiave di questa sezione non c'è più su questo telefono: i suoi dati non si possono aprire. " +
    "Si ricomincia da una sezione vuota.",
  /** Nessun blocco schermo, per una sezione all'impronta. */
  val noScreenLock: String = "Per proteggere questa sezione imposta un blocco schermo (PIN, sequenza o password) nelle " +
    "impostazioni del telefono.",
  /** Nessuna impronta registrata, senza il codice del telefono come riserva. */
  val noBiometrics: String = "Per usare l'impronta registrane una nelle impostazioni del telefono.",
  /** Il Keystore non risponde. */
  val keystoreError: String = "Il telefono non riesce a usare le sue chiavi in questo momento. Riprova fra poco.",
  /** Il segreto dell'app sbagliato. */
  val wrongSecret: String = "PIN sbagliato.",
  /** Nessun segreto dell'app impostato. */
  val noSecret: String = "Questa sezione non ha un PIN dell'app.",
  /** L'autenticazione non è andata. */
  val authenticationFailed: String = "Non è stato possibile verificare che sei tu. Riprova.",
  /** La sezione è chiusa. */
  val locked: String = "Questa sezione è chiusa.",
  /** Un dato che non torna. */
  val corrupt: String = "Questo dato è danneggiato e non si può aprire.",
) {
  /** La frase di un'invalidazione [reason], secondo la riserva della sezione. */
  fun reason(reason: VaultInvalidation, reserve: VaultReserve): String = when (reason) {
    VaultInvalidation.BiometricsChanged -> when (reserve) {
      VaultReserve.DeviceCredential -> biometricsChanged
      VaultReserve.AppSecret -> biometricsChangedSecret
      VaultReserve.None -> biometricsChangedLost
    }
    VaultInvalidation.ScreenLockRemoved -> if (reserve == VaultReserve.AppSecret) screenLockRemovedSecret else screenLockRemoved
    VaultInvalidation.KeyLost -> keyLost
  }

  /** La frase di un motivo per cui la protezione non c'è. */
  fun unavailable(reason: VaultUnavailability): String = when (reason) {
    VaultUnavailability.NoScreenLock -> noScreenLock
    VaultUnavailability.NoBiometrics -> noBiometrics
    VaultUnavailability.KeystoreError -> keystoreError
  }

  /**
   * La frase da mostrare per l'esito [unlock] di una sezione con la riserva [reserve]; null quando non c'è niente da
   * dire (aperta, annullata).
   */
  fun message(unlock: VaultUnlock, reserve: VaultReserve): String? = when (unlock) {
    is VaultUnlock.Opened -> null
    VaultUnlock.Cancelled -> null
    is VaultUnlock.NeedsSecret -> unlock.invalidation?.let { reason(it, VaultReserve.AppSecret) }
    VaultUnlock.WrongSecret -> wrongSecret
    is VaultUnlock.Lost -> reason(unlock.reason, if (unlock.reason == VaultInvalidation.BiometricsChanged) VaultReserve.None else reserve)
    is VaultUnlock.Unavailable -> unavailable(unlock.reason)
    is VaultUnlock.Failed -> unlock.message
  }

  companion object {
    /** Le frasi in inglese. */
    val English: VaultLabels = VaultLabels(
      unlockTitle = "Unlock",
      useDeviceCredential = "Use phone passcode",
      useAppSecret = "Use app PIN",
      biometricsChanged = "Your fingerprints or face changed, so this section locked for your safety. Open it with your " +
        "phone passcode; after that your fingerprint works again.",
      biometricsChangedSecret = "Your fingerprints or face changed, so this section locked for your safety. Open it with " +
        "the app PIN; after that your fingerprint works again.",
      biometricsChangedLost = "Your fingerprints or face changed, so the data in this section can no longer be opened. " +
        "It starts over empty.",
      screenLockRemoved = "The screen lock was turned off, and its keys with it: the data in this section can no longer " +
        "be opened. It starts over empty.",
      screenLockRemovedSecret = "The screen lock was turned off: open this section with the app PIN.",
      keyLost = "The key of this section is no longer on this phone, so its data can't be opened. It starts over empty.",
      noScreenLock = "To protect this section, set a screen lock (PIN, pattern or password) in your phone's settings.",
      noBiometrics = "To use your fingerprint, add one in your phone's settings.",
      keystoreError = "Your phone can't use its keys right now. Try again in a moment.",
      wrongSecret = "Wrong PIN.",
      noSecret = "This section has no app PIN.",
      authenticationFailed = "We couldn't confirm it's you. Try again.",
      locked = "This section is locked.",
      corrupt = "This item is damaged and can't be opened.",
    )
  }
}
