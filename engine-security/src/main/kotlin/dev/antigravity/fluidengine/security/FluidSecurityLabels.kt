package dev.antigravity.fluidengine.security

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration

/**
 * Le parole del blocco: la schermata di vetro, il dialogo, il PIN, la riga delle impostazioni, il contenuto protetto.
 * Italiano di serie, [English] per un'app in inglese. `%1$s` è il nome dell'app o della cosa protetta.
 *
 * Le frasi seguono iOS: «Richiedi Face ID», «Visualizza album», «Visualizza nota» (support.apple.com, «Lock or hide an
 * app on iPhone», «Lock your notes on iPhone», «Hide photos and videos», iOS 27, lette il 24/11/2026), dette con le
 * parole di Android (impronta, volto, codice del telefono).
 */
data class FluidSecurityLabels(
  /** Il titolo della schermata di blocco: «Fluid Glass è bloccata». */
  val lockedTitle: String = "%1\$s è bloccata",
  /** Sotto il titolo, con impronta o volto. */
  val lockedSubtitleBiometric: String = "Usa l'impronta, il volto o il codice del telefono per aprirla.",
  /** Sotto il titolo, col solo codice del telefono. */
  val lockedSubtitleCredential: String = "Usa il codice del telefono per aprirla.",
  /** Sotto il titolo, col solo PIN dell'app. */
  val lockedSubtitlePin: String = "Scrivi il PIN dell'app per aprirla.",
  /** Il tasto che apre il dialogo di sistema. */
  val unlock: String = "Sblocca",
  /** Il tasto della riserva. */
  val usePin: String = "Usa il PIN dell'app",
  /** Il titolo del dialogo di sistema: «Sblocca Fluid Glass». */
  val promptTitle: String = "Sblocca %1\$s",
  /** Il titolo del PIN. */
  val pinTitle: String = "Scrivi il PIN dell'app",
  /** Il PIN sbagliato. */
  val pinWrong: String = "PIN sbagliato. Riprova.",
  /** Troppi tentativi: «Riprova fra 5 minuti». */
  val pinWait: String = "Troppi tentativi. Riprova fra %1\$s.",
  /** Le attese di [pinWait]: un minuto, più minuti, un'ora, più ore. */
  val waitOneMinute: String = "1 minuto",
  val waitMinutes: String = "%1\$d minuti",
  val waitOneHour: String = "1 ora",
  val waitHours: String = "%1\$d ore",
  /** Torna dal PIN al tasto. */
  val back: String = "Indietro",
  /** Annulla (il tasto in basso del dialogo, quando non c'è riserva). */
  val cancel: String = "Annulla",
  /** Un'autenticazione non riuscita senza frase del sistema. */
  val authenticationFailed: String = "Non è stato possibile verificare che sei tu. Riprova.",
  /** Nessun modo di sbloccare: né impronta, né codice del telefono, né PIN. */
  val nothingToUnlockWith: String = "Imposta un blocco schermo nelle impostazioni del telefono, o un PIN dell'app.",
  /** Il nome della schermata di blocco per TalkBack. */
  val lockScreenDescription: String = "Schermata di blocco",

  // --- La riga delle impostazioni ---------------------------------------------------------------------------------
  /** L'interruttore: «Richiedi l'impronta». */
  val settingTitle: String = "Richiedi l'impronta",
  /** Sotto l'interruttore. */
  val settingSubtitle: String = "Quando torni in %1\$s chiede l'impronta, il volto o il codice del telefono.",
  /** Sotto l'interruttore, quando il blocco non si può accendere. */
  val settingUnavailable: String = "Per usarlo imposta un blocco schermo nelle impostazioni del telefono, o un PIN dell'app.",
  /** La riga del tempo: «Blocca». */
  val settingTimeout: String = "Blocca",
  /** Il tempo zero. */
  val timeoutImmediately: String = "Subito",
  /** «Dopo 5 minuti». */
  val timeoutAfterMinutes: String = "Dopo %1\$d min",
  /** «Dopo 1 ora». */
  val timeoutAfterHours: String = "Dopo %1\$d h",
  /** La riga del PIN dell'app. */
  val settingPin: String = "PIN dell'app",
  /** Sotto la riga del PIN. */
  val settingPinSubtitle: String = "La riserva quando l'impronta e il codice del telefono non ci sono.",
  /** Il valore della riga del PIN: c'è. */
  val pinOn: String = "Attivo",
  /** Il valore della riga del PIN: non c'è. */
  val pinOff: String = "Spento",
  /** Il titolo della scelta del PIN. */
  val pinChooseTitle: String = "Scegli un PIN dell'app",
  /** Il secondo passo. */
  val pinConfirmTitle: String = "Scrivilo di nuovo",
  /** I due PIN diversi. */
  val pinMismatch: String = "I due PIN non sono uguali. Ricomincia.",
  /** Togli il PIN. */
  val pinRemove: String = "Togli il PIN",

  // --- Il contenuto protetto --------------------------------------------------------------------------------------
  /** Il titolo del contenuto chiuso: «Note bloccate è bloccato». */
  val contentLockedTitle: String = "%1\$s è bloccato",
  /** Sotto il titolo. */
  val contentLockedSubtitle: String = "Usa l'impronta per vederlo.",
  /** Il tasto che lo apre: «Visualizza» (iOS: «Visualizza album», «Visualizza nota»). */
  val contentView: String = "Visualizza",
  /** Il tasto che lo richiude. */
  val contentLock: String = "Blocca",
) {
  /** Il nome di un tempo di blocco: «Subito», «Dopo 5 min», «Dopo 1 h». */
  fun timeout(duration: Duration): String = when {
    duration == Duration.ZERO -> timeoutImmediately
    duration.inWholeMinutes < 60 -> timeoutAfterMinutes.format(duration.inWholeMinutes.toInt().coerceAtLeast(1))
    else -> timeoutAfterHours.format(duration.inWholeHours.toInt())
  }

  /** «Riprova fra 5 minuti», per un'attesa di [millis]. */
  fun pinWaitText(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
    val amount = when {
      minutes >= 60 -> ((minutes + 59) / 60).let { if (it == 1L) waitOneHour else waitHours.format(it.toInt()) }
      minutes == 1L -> waitOneMinute
      else -> waitMinutes.format(minutes.toInt())
    }
    return pinWait.format(amount)
  }

  companion object {
    /** Le parole in inglese. */
    val English: FluidSecurityLabels = FluidSecurityLabels(
      lockedTitle = "%1\$s is locked",
      lockedSubtitleBiometric = "Use your fingerprint, face or phone passcode to open it.",
      lockedSubtitleCredential = "Use your phone passcode to open it.",
      lockedSubtitlePin = "Enter the app PIN to open it.",
      unlock = "Unlock",
      usePin = "Use app PIN",
      promptTitle = "Unlock %1\$s",
      pinTitle = "Enter app PIN",
      pinWrong = "Wrong PIN. Try again.",
      pinWait = "Too many attempts. Try again in %1\$s.",
      waitOneMinute = "1 minute",
      waitMinutes = "%1\$d minutes",
      waitOneHour = "1 hour",
      waitHours = "%1\$d hours",
      back = "Back",
      cancel = "Cancel",
      authenticationFailed = "We couldn't confirm it's you. Try again.",
      nothingToUnlockWith = "Set a screen lock in your phone's settings, or an app PIN.",
      lockScreenDescription = "Lock screen",
      settingTitle = "Require fingerprint",
      settingSubtitle = "When you return to %1\$s, it asks for your fingerprint, face or phone passcode.",
      settingUnavailable = "To use it, set a screen lock in your phone's settings, or an app PIN.",
      settingTimeout = "Lock",
      timeoutImmediately = "Immediately",
      timeoutAfterMinutes = "After %1\$d min",
      timeoutAfterHours = "After %1\$d h",
      settingPin = "App PIN",
      settingPinSubtitle = "The backup when fingerprint and phone passcode aren't available.",
      pinOn = "On",
      pinOff = "Off",
      pinChooseTitle = "Choose an app PIN",
      pinConfirmTitle = "Enter it again",
      pinMismatch = "The two PINs don't match. Start over.",
      pinRemove = "Turn off PIN",
      contentLockedTitle = "%1\$s is locked",
      contentLockedSubtitle = "Use your fingerprint to see it.",
      contentView = "View",
      contentLock = "Lock",
    )
  }
}

/** Le parole del blocco per questo albero. */
val LocalFluidSecurityLabels: ProvidableCompositionLocal<FluidSecurityLabels> = staticCompositionLocalOf { FluidSecurityLabels() }
