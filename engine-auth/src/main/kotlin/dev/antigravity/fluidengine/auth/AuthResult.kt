package dev.antigravity.fluidengine.auth

/**
 * L'esito di una chiamata a Credential Manager, con l'errore già tradotto in una frase che si può mostrare.
 *
 * È la scheda `passkey` dell'architettura dell'Account Pampa (B § 9.2): l'app non deve conoscere le quindici
 * eccezioni di `androidx.credentials` né le loro varianti DOM. Ne riceve cinque casi, e per ognuno una frase pronta
 * ([phrase], con le parole di [FluidAuthLabels]). Decidere se la prova basta spetta al servizio, non al modulo.
 *
 * - [Ok]: è andata, con il valore.
 * - [Cancelled]: l'utente ha chiuso il foglio, oppure il gestore ha risposto `NotAllowedError`, che in WebAuthn è anche
 *   «tempo scaduto» e di proposito non dice di più. Di solito non si mostra niente.
 * - [NoCredential]: sul telefono non c'è una passkey (o una password) per questa richiesta.
 * - [Unsupported]: il telefono non può farlo, e il motivo è in [Why]. Si sa spesso prima di chiamare:
 *   [FluidPasskeys.unsupportedReason].
 * - [Failed]: tutto il resto, con la frase già scelta ([Failed.message]) e la causa per i log. [Failed.retry] dice se
 *   ha senso riprovare subito.
 *
 * Il testo di [Failed.message] non contiene mai la causa: è per l'utente. La causa resta in [Failed.cause].
 */
sealed interface AuthResult<out T> {

  /** È andata. */
  data class Ok<T>(val value: T) : AuthResult<T>

  /** L'utente ha chiuso il foglio di sistema (o il gestore ha risposto `NotAllowedError`). */
  data object Cancelled : AuthResult<Nothing>

  /** Nessuna credenziale da usare per questa richiesta, su questo telefono. */
  data object NoCredential : AuthResult<Nothing>

  /** Il telefono non può farlo: versione di Android, nessun gestore, nessun blocco schermo. */
  data class Unsupported(val why: Why) : AuthResult<Nothing>

  /**
   * Tutto il resto. [message] è la frase per l'utente, già scelta da [FluidAuthLabels]; [cause] è l'eccezione di
   * Credential Manager (o del JSON), per i log; [retry] è vero quando riprovare subito ha senso (un'interruzione).
   */
  data class Failed(val message: String, val cause: Throwable?, val retry: Boolean = false) : AuthResult<Nothing>

  /**
   * Perché il telefono non può. I tre casi della scheda `passkey`. Un caso nuovo non entrerà qui come valore: un
   * motivo che non è uno di questi arriva come [Failed].
   */
  enum class Why {
    /** Android più vecchio di 9 (API 28): Credential Manager non gestisce le passkey. Pampa Store parte da Android 8. */
    OldAndroid,

    /** Nessun gestore di passkey o password attivo (o manca Play services da Android 9 a 13). */
    NoProvider,

    /** Il telefono non ha un blocco schermo: le passkey non si possono creare né usare. */
    NoScreenLock,
  }
}

/**
 * La frase da mostrare per questo esito, o `null` se è andata ([AuthResult.Ok]). Per [AuthResult.Failed] è il suo
 * messaggio, già scelto quando è nato; per gli altri casi viene da [labels]. [password] è vero per gli esiti di
 * [FluidPasswords]: cambia solo la frase di [AuthResult.NoCredential] («nel gestore delle password…»).
 */
fun AuthResult<*>.phrase(labels: FluidAuthLabels = FluidAuthLabels(), password: Boolean = false): String? = when (this) {
  is AuthResult.Ok -> null
  AuthResult.Cancelled -> labels.cancelled
  AuthResult.NoCredential -> if (password) labels.noPassword else labels.noCredential
  is AuthResult.Unsupported -> when (why) {
    AuthResult.Why.OldAndroid -> labels.oldAndroid
    AuthResult.Why.NoProvider -> labels.noProvider
    AuthResult.Why.NoScreenLock -> labels.noScreenLock
  }
  is AuthResult.Failed -> message
}

/** Il valore se è andata, altrimenti `null`. */
fun <T> AuthResult<T>.valueOrNull(): T? = (this as? AuthResult.Ok<T>)?.value
