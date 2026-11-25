<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/auth.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-auth ed engine-auth-google

**Entrare con una passkey o con Google, dal telefono, senza rete.** Due moduli **opt-in** della 4.0 (F10.2), nati per
l'Account Pampa: `engine-auth` porta le passkey e il gestore delle password con Credential Manager; `engine-auth-google`
porta «Continua con Google» e le concessioni Google. Pacchetti `dev.antigravity.fluidengine.auth` e
`dev.antigravity.fluidengine.auth.google`.

Modello mentale: **il foglio di sistema e le sue frasi, non un client del server.** Il modulo prende il JSON WebAuthn che
manda il server, apre il foglio di Android e restituisce la risposta già divisa fra quello che va al server e quello che
resta sul telefono. Le richieste al server le fa l'app: nessuno dei due moduli ha il permesso `INTERNET` (regola R9
dell'Account Pampa). Decidere se la prova basta spetta al servizio, non al modulo.

Il progetto: `artifacts\ecosistema\account\b\architettura.md` § 1 e § 9.2–9.3, `b\chiavi.md` § 3.2. Catalogo:
[`../api/auth.md`](../api/auth.md), [`../api/auth-google.md`](../api/auth-google.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| creare una passkey col JSON del server | `FluidPasskeys.create(activity, optionsJson)` → `AuthResult<FluidPasskeyResult>` |
| entrare con una passkey | `FluidPasskeys.get(activity, optionsJson)`; con `onlyOnThisPhone = true` non mostra niente (`NoCredential`) se qui non c'è una passkey |
| sapere prima se il telefono può (Android 9, blocco schermo) | `FluidPasskeys.unsupportedReason(context)` / `isSupported` |
| mandare la risposta al server **senza** il PRF | `result.responseJson` (il modulo l'ha già tolto) |
| l'uscita del PRF, solo per il telefono | `result.prfOutput()` (32 byte, `null` se il gestore non l'ha data) |
| i flag BE e BS, l'AAGUID del gestore | `result.authenticatorData` (`backupEligible`, `backedUp`, `aaguid`) |
| salvare il codice di recupero nel gestore, o rileggerlo | `FluidPasswords.save(activity, id, secret)`, `FluidPasswords.get(activity, ids)` |
| la frase da mostrare per un errore | `result.phrase()`, con le parole di `FluidAuthLabels` (italiano di serie, `english()` pronta) |
| tradurre un'eccezione di Credential Manager chiamato da me | `FluidAuthErrors.fromCreate(e, labels)` / `fromGet(e, labels)` |
| le conversioni JSON senza Android (test, banco) | `FluidWebAuthnJson.creationRequest`, `assertionRequest`, `readCreation`, `readAssertion`, `hasPrfOutput` |
| «Continua con Google» col nonce del server | `FluidGoogleSignIn.idToken(activity, webClientId, nonce, returning)` (`engine-auth-google`) |
| un token Google per un ambito (Calendario…) | `FluidGoogleAccess.authorize(…)`: **spento** finché `FluidGoogleAccess.isEnabled` è falso |

## Installare

`-Modules …,engine-auth` (porta `engine-foundation` e `androidx.credentials` 1.6.0, Apache-2.0) oppure
`-Modules …,engine-auth-google` (porta anche `engine-auth`). Sono opt-in: entrano solo se chiesti per nome.

**Android 9–13.** Lì le passkey passano da Google Play services, e serve
`androidx.credentials:credentials-play-services-auth`. Lo porta `engine-auth-google`; un'app che non vuole Google aggiunge
solo quella riga. Da Android 14 Credential Manager passa dal sistema e basta `engine-auth`.

**La licenza.** `engine-auth-google` è l'unico modulo dell'engine con librerie Google non libere (`play-services-auth`,
`googleid`). Per collegarle l'engine ha il permesso aggiuntivo della GPL-3.0 § 7, col testo della FSF:
[`LICENSES/Permesso-librerie-Google.md`](../../../LICENSES/Permesso-librerie-Google.md). Il permesso vale per il
codice di Alessio, non per quello di LibrePods in `engine-ui`: il file spiega cosa vuol dire per un'app che li unisce.

## `FluidPasskeys`

**Il PRF lo scrive il modulo, mai il server** (critica S9). Le opzioni che arrivano dal server passano com'erano, nello
stesso ordine, tranne `extensions.prf`: qualunque cosa ci sia (un ingresso diverso, un `evalByCredential`) si toglie, e al
suo posto va `FluidPrfInput.PampaV1`, cioè `SHA-256("pampa/v1/prf")` (`chiavi.md` § 3.2). Un'altra app passa il suo
(`FluidPrfInput.fromLabel(…)`) o `prf = null`, che non chiede il PRF ma toglie comunque quello del server.

```kotlin
when (val r = FluidPasskeys.create(activity, optionsJson)) {        // l'oggetto «options» del server
  is AuthResult.Ok -> {
    server.registerVerify(r.value.responseJson)                     // senza prf.results
    r.value.prfOutput()?.let { keepOnPhone(it) }                    // il PRF resta qui
  }
  AuthResult.Cancelled -> Unit                                       // l'utente ha chiuso: niente da dire
  else -> showError(r.phrase())
}
```

- **Alla creazione** si chiede già il valore (`evaluatePrfAtCreation = true`): alcuni gestori lo danno. Chi non lo dà
  risponde solo `prf.enabled` (`result.prfEnabled`), e lo Store fa subito un accesso. Con `false` si chiede solo se c'è
  (`prf: {}`), come in `chiavi.md`.
- **Al ritorno** il modulo toglie `clientExtensionResults.prf.results` (e il vecchio `hmacGetSecret`), lascia
  `prf.enabled`, e legge flag e AAGUID dall'`attestationObject` (CBOR) o da `authenticatorData`. Il server dell'Account
  Pampa rifiuta una risposta che contiene ancora il PRF (`src/guards.ts`).
- **Prima di aprire il foglio** controlla Android 9 e il blocco schermo (`KeyguardManager.isDeviceSecure`): senza,
  risponde `AuthResult.Unsupported` e non chiama niente. Il gestore che manca si sa solo chiamando.
- **`toString` non stampa segreti**: né l'uscita del PRF (`FluidPasskeyResult`), né la password (`FluidSavedPassword`),
  né il token di Google (`FluidGoogleIdToken`, `GoogleAccess.Token`).

## `AuthResult` e le frasi

| Esito | Quando | Frase (`FluidAuthLabels`) |
|---|---|---|
| `Ok(value)` | è andata | nessuna |
| `Cancelled` | foglio chiuso; DOM `NotAllowedError` o `AbortError` | `cancelled` (di solito non si mostra) |
| `NoCredential` | nessuna passkey (o password) per la richiesta | `noCredential`, o `noPassword` con `phrase(password = true)` |
| `Unsupported(OldAndroid)` | Android 8 | `oldAndroid` |
| `Unsupported(NoProvider)` | nessun gestore, `…ProviderConfiguration…`, `…Unsupported…`, `NoCreateOption`, DOM `NotSupportedError` | `noProvider` |
| `Unsupported(NoScreenLock)` | nessun blocco schermo | `noScreenLock` |
| `Failed(message, cause, retry)` | DOM `SecurityError` (di solito `assetlinks.json`) | `wrongDomain` |
| | DOM `InvalidStateError` alla creazione (c'è già) | `alreadyExists` |
| | interruzione, DOM `TimeoutError` (`retry = true`) | `interrupted` |
| | JSON che non è WebAuthn | `badRequest` |
| | tutto il resto | `generic` |

`NotAllowedError` è anche «tempo scaduto»: WebAuthn lo tiene ambiguo di proposito, e il modulo fa come un browser.
`Failed.message` non contiene mai la causa: la causa resta in `Failed.cause`, per i log.

## `engine-auth-google`

- **`FluidGoogleSignIn.idToken(activity, webClientId, nonce, returning)`.** Con `returning = true` il foglio dal basso
  coi soli account già entrati e la scelta automatica; senza account risponde `NoCredential` e non mostra niente. Con
  `false` il foglio del tasto «Continua con Google». Il `nonce` lo dà il server; `webClientId` è il client **web** del
  progetto Google. Il server legge solo il `sub`: email, nome e foto sono per lo schermo del telefono.
- **`FluidGoogleAccess` è scritto ma spento** (scelta N6 di Alessio): finché `isEnabled` è falso, `authorize` risponde
  `GoogleAccess.Off`, `revokeAll` risponde `Ok` e `clearToken` non fa niente, senza toccare Google. Il token di
  `GoogleAccess.Token` vale circa un'ora e sta solo in memoria. Entrare e autorizzare restano due flussi separati.

## Le prove

- `FluidWebAuthnContractTest` (JVM): le conversioni contro il contratto del server (`contract/v1`, commit `f4f23b2`) e
  contro risposte che il server ha verificato davvero (`src/test/resources/pampa-account/webauthn-vectors.json`, fatte
  da `engine-auth/tools/webauthn-vectors.mjs` con `@simplewebauthn/server` e l'autenticatore finto del server). La
  risposta pulita è uguale, campo per campo, a quella che il server ha accettato; quella col PRF il server la rifiuta.
- `FluidPasskeysErrorsTest` (Robolectric): ogni riga della tabella sopra, con le eccezioni vere di
  `androidx.credentials` e un gestore finto; Android 8 con `@Config(sdk = [27])`; il blocco schermo con
  `ShadowKeyguardManager`.
- `FluidGoogleSignInTest` (Robolectric): le due opzioni di Google, il token, gli errori, le concessioni spente.
- Il PRF vero, con il Gestore password di Google e con Samsung Pass, lo misura l'app di prova `tools/prf-probe`
  («Pampa · prova passkey») su `prova.pampa-apps.com`: [`tools/prf-probe/README.md`](../../../tools/prf-probe/README.md).

## Trappole

- **Le opzioni vanno passate senza involucro**: l'oggetto `options` della risposta del server, non la risposta intera.
  Senza `challenge` in cima il modulo risponde `badRequest` senza aprire il foglio.
- **`SecurityError` non è un difetto del modulo**: il gestore non trova l'app in `https://<rpId>/.well-known/assetlinks.json`
  (file assente, non JSON, dietro un redirect, o senza l'impronta della firma di quest'app).
- **Un'app firmata con un'altra chiave è un'altra origine** (`android:apk-key-hash:` + SHA-256 del certificato): una
  build di debug con la chiave di Android Studio non entra nelle passkey di `pampa-apps.com`.
- **`FluidGoogleAccess.isEnabled` è globale**: accenderlo è una scelta di prodotto (un client Google con ambiti da far
  verificare), non una riga da mettere «per provare».

## Dove guardare

`engine-auth/src/main/kotlin/dev/antigravity/fluidengine/auth/`: `FluidPasskeys.kt`, `FluidWebAuthnJson.kt` (il PRF),
`FluidAuthenticatorData.kt` (flag e CBOR), `FluidAuthErrors.kt` (la tabella), `FluidAuthLabels.kt`, `FluidPasswords.kt`.
`engine-auth-google/src/main/kotlin/dev/antigravity/fluidengine/auth/google/`: `FluidGoogleSignIn.kt`,
`FluidGoogleAccess.kt`.
