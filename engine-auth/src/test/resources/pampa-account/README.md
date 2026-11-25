# Il contratto del server dell'Account Pampa, per le prove di engine-auth

- `contract/v1/` è la copia, senza modifiche, di `C:\VibeCoded Projects\Pampa-account\contract\v1\` al commit
  `f4f23b2` (F10.1, 25 novembre 2026). Quando il server cambia il contratto, si ricopia da lì e si rilanciano le prove.
- `webauthn-vectors.json` lo scrive `engine-auth/tools/webauthn-vectors.mjs` con le funzioni vere del server
  (`src/webauthn.ts`, `@simplewebauthn/server` 14.1.0) e il suo autenticatore finto (`test/fake/authenticator.ts`), allo
  stesso commit, tutto in locale:

  ```
  git -C "C:\VibeCoded Projects\Pampa-account" archive f4f23b2 src test contract | tar -x -C <cartella vuota>
  node engine-auth/tools/webauthn-vectors.mjs <cartella vuota> "C:\VibeCoded Projects\Pampa-account" > engine-auth/src/test/resources/pampa-account/webauthn-vectors.json
  ```

  Le chiavi delle passkey finte sono nuove a ogni giro: i valori cambiano, la forma no. Le prove non dipendono dai
  valori.

Le prove che li usano: `FluidWebAuthnContractTest` e `FluidPasskeysErrorsTest`.
