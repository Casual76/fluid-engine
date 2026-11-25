package dev.antigravity.fluidengine.security

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** I numeri del blocco dell'app (F8.23). */
object FluidAppLockDefaults {
  /**
   * Le scelte di «Blocca»: subito, dopo 1, 5 o 15 minuti, dopo un'ora. «Subito» è il comportamento di «Richiedi
   * Face ID» di iOS 18+ (l'app «si blocca di nuovo da sola quando la chiudi», support.apple.com, «Lock or hide an app
   * on iPhone», iOS 27, letto il 24/11/2026, fiducia alta); gli altri sono quelli di «Richiedi la password» di macOS e
   * del codice di iPhone senza Face ID (a memoria, fiducia media).
   */
  val Timeouts: List<Duration> = listOf(Duration.ZERO, 1.minutes, 5.minutes, 15.minutes, 1.hours)

  /** Le cifre del PIN dell'app: 6, il codice di serie di iPhone dal 2015 (iOS 9). */
  const val PinLength: Int = 6

  /** Le lunghezze del PIN che si possono scegliere: 4 e 6, come «Opzioni codice» di iOS. */
  val PinLengths: List<Int> = listOf(4, 6)

  /**
   * Per quanto vale il permesso di uscire senza blocco ([FluidAppLock.allowLeaving]): 10 minuti, il tempo di scegliere
   * un file o una persona a cui mandare qualcosa. **A occhio**, 24/11/2026.
   */
  val LeaveAllowance: Duration = 10.minutes

  /** I giri di PBKDF2-HMAC-SHA256 del PIN dell'app: come la cassaforte (`VaultDefaults.SecretIterations`). */
  const val PinIterations: Int = 210_000

  /**
   * Quanto aspettare dopo [failures] PIN sbagliati di fila: niente fino a 4, poi 1 minuto, 5, 15 (al settimo e
   * all'ottavo) e un'ora. È la tabella dei ritardi del codice di iPhone (Apple Platform Security, «Passcodes and
   * passwords», a memoria, fiducia media).
   */
  fun pinDelay(failures: Int): Duration = when {
    failures < 5 -> Duration.ZERO
    failures == 5 -> 1.minutes
    failures == 6 -> 5.minutes
    failures <= 8 -> 15.minutes
    else -> 1.hours
  }
}

/** Le regole pure del blocco: quando si blocca al rientro. */
internal object FluidAppLockRules {
  /**
   * Vero se al rientro, dopo [awayMillis] fuori, l'app va bloccata. Mai se il blocco è spento, mai durante un'uscita
   * permessa (una condivisione, una scelta di file: [allowedLeave]).
   */
  fun shouldLockOnReturn(enabled: Boolean, awayMillis: Long, timeout: Duration, allowedLeave: Boolean): Boolean =
    enabled && !allowedLeave && awayMillis >= timeout.inWholeMilliseconds

  /** Fino a quando aspettare dopo [failures] errori, l'ultimo a [lastFailureAt]; null: si può riprovare. */
  fun pinWaitUntil(failures: Int, lastFailureAt: Long?, now: Long): Long? {
    val delay = FluidAppLockDefaults.pinDelay(failures)
    if (delay == Duration.ZERO || lastFailureAt == null) return null
    val until = lastFailureAt + delay.inWholeMilliseconds
    return until.takeIf { it > now }
  }
}
