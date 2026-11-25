<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/auth-google.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-auth-google

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-auth-google/auth/google/FluidGoogleAccess.kt`

- `sealed interface GoogleAccess` — L'esito di [FluidGoogleAccess.authorize].
  - `class Token(val accessToken: String, val grantedScopes: Set<String>, val expiresAtElapsed: Long) : GoogleAccess`
  - `data class NeedsConsent(val intent: PendingIntent) : GoogleAccess`
  - `data object NoPlayServices : GoogleAccess`
  - `data class Failed(val message: String) : GoogleAccess`
  - `data object Off : GoogleAccess`
- `object FluidGoogleAccess` — Le concessioni Google (architettura § 4): un token d'accesso per un ambito, come il Calendario, chiesto **solo sul telefono** con l'API di autorizzazione di Play services ([authorization](https://developer.android.com/identity/authorization), fiducia alta). Il server non tiene niente di Google (regola R5).
  - `var isEnabled: Boolean` — Spento di serie (N6).
  - `const val TOKEN_LIFETIME_MS: Long` — Quanto vale un token d'accesso di Google: un'ora. L'API non lo dice nel risultato; si conta un po' meno, 55 minuti, così l'app lo rinnova prima che scada (a occhio, 25/11/2026, sulla durata di serie dei token OAuth di Google).
  - `suspend fun authorize(context: Context, scopes: Set<String>, account: Account? = null, labels: FluidAuthLabels = FluidAuthLabels()): GoogleAccess` — Chiede un token per [scopes] (gli indirizzi degli ambiti, per esempio `https://www.googleapis.com/auth/calendar.events`).
  - `fun fromConsent(context: Context, data: Intent?, labels: FluidAuthLabels = FluidAuthLabels()): GoogleAccess` — Dopo il consenso: il risultato dell'intent di [GoogleAccess.NeedsConsent].
  - `suspend fun revokeAll(context: Context, account: Account?, scopes: Set<String> = emptySet(), labels: FluidAuthLabels = FluidAuthLabels()): AuthResult<Unit>` — Toglie tutte le concessioni di questo progetto per [account] (all'eliminazione dell'account, architettura § 1.6). [scopes] sono quelli concessi; vuoto vuol dire «quelli che Google conosce».
  - `suspend fun clearToken(context: Context, token: String)` — Dimentica un token d'accesso (per esempio dopo un 401): la prossima [authorize] ne chiede uno nuovo.

## `engine-auth-google/auth/google/FluidGoogleSignIn.kt`

- `class FluidGoogleIdToken internal constructor(private val token: String, val email: String, val displayName: String?, val pictureUri: String?)` — L'ID token di Google, da spedire al server, e i dati dell'account da mostrare sul telefono.
  - `fun idToken(): String` — Il token, da spedire al server (`POST /v1/google/login`).
- `object FluidGoogleSignIn` — «Continua con Google» con Credential Manager (architettura § 1.3, [Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), fiducia alta). Chiede solo `openid`, `email` e `profile`: entrare e autorizzare sono due cose separate, e le concessioni stanno in [FluidGoogleAccess].
  - `suspend fun idToken(activity: Activity, webClientId: String, nonce: String, returning: Boolean, labels: FluidAuthLabels = FluidAuthLabels()): AuthResult<FluidGoogleIdToken>`
