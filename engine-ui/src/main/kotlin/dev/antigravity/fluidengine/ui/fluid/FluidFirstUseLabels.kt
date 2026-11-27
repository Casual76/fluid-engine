package dev.antigravity.fluidengine.ui.fluid

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Le parole della prima volta (F8.11): il foglio di benvenuto e delle novità, la spiegazione di un permesso e il suo
 * «Apri Impostazioni». Di serie in italiano; [english] è la versione inglese completa. Un'app in un'altra lingua ne dà
 * una sua con [LocalFluidFirstUseLabels].
 *
 * Sono a parte da [FluidLabels] perché i pacchetti di F8 corrono insieme: ognuno porta le sue parole senza toccare la
 * classe di tutti (come `FluidSpecialFieldLabels` di F8.3).
 */
@Immutable
data class FluidFirstUseLabels(
  /** Il titolo del primo avvio: «Benvenuto in Note». */
  val welcomeTitle: (appName: String) -> String = { "Benvenuto in $it" },
  /** Il titolo dopo un aggiornamento: «Novità di Note». */
  val whatsNewTitle: (appName: String) -> String = { "Novità di $it" },
  /** Il tasto fermo in basso. */
  val continueLabel: String = "Continua",
  /** Il collegamento sotto la nota sulla privacy, quando l'app ne dà uno. */
  val privacyLink: String = "Scopri come vengono gestiti i tuoi dati…",
  /** TalkBack, sui pallini dei passi: «Passo 2 di 3». */
  val stepPosition: (page: Int, count: Int) -> String = { page, count -> "Passo $page di $count" },
  /** Il tasto che dalla spiegazione porta al dialogo di Android. */
  val permissionContinue: String = "Continua",
  /** Il tasto che chiude la spiegazione senza chiedere. */
  val permissionNotNow: String = "Non ora",
  /** Il tasto che porta alle impostazioni dell'app quando Android non chiede più. */
  val permissionOpenSettings: String = "Apri Impostazioni",
  /** Il titolo della spiegazione quando Android non chiede più: «Accesso al microfono non consentito». */
  val permissionOffTitle: (FluidPermission) -> String = { "Accesso ${it.name} non consentito" },
  /** La frase aggiunta al perché quando si va nelle impostazioni. */
  val permissionOffHint: String = "Puoi consentirlo nelle Impostazioni dell'app.",
  /** Il titolo della spiegazione di serie: «Consentire l'accesso al microfono?». */
  val permissionTitle: (FluidPermission) -> String = { "Consentire l'accesso ${it.name}?" },
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidFirstUseLabels = FluidFirstUseLabels(
      welcomeTitle = { "Welcome to $it" },
      whatsNewTitle = { "What's New in $it" },
      continueLabel = "Continue",
      privacyLink = "See how your data is managed…",
      stepPosition = { page, count -> "Step $page of $count" },
      permissionContinue = "Continue",
      permissionNotNow = "Not Now",
      permissionOpenSettings = "Open Settings",
      permissionOffTitle = { "Access to ${englishName(it)} is off" },
      permissionOffHint = "You can turn it on in the app's Settings.",
      permissionTitle = { "Allow access to ${englishName(it)}?" },
    )
  }
}

private fun englishName(permission: FluidPermission): String = when (permission.id) {
  "microphone" -> "the microphone"
  "camera" -> "the camera"
  "notifications" -> "notifications"
  "photos" -> "your photos"
  "photos-videos" -> "your photos and videos"
  "location-coarse" -> "your location"
  else -> permission.id
}

/** Le parole della prima volta per il sottoalbero. */
val LocalFluidFirstUseLabels = staticCompositionLocalOf { FluidFirstUseLabels() }

/**
 * La memoria della prima volta per il sottoalbero: cosa ha già visto il benvenuto, quali permessi sono già stati chiesti.
 * L'app la fornisce alla radice con `EngineFirstUseStore(context)` di `engine-storage`; di serie è una memoria che dura
 * quanto il processo (il benvenuto tornerebbe a ogni avvio a freddo, e un permesso negato per sempre sembrerebbe mai
 * chiesto): va bene per le prove, non per un'app.
 */
val LocalFluidFirstUseMemory = staticCompositionLocalOf<FluidFirstUseMemory> { ProcessFirstUseMemory }

private val ProcessFirstUseMemory: FluidFirstUseMemory = FluidFirstUseMemory.inMemory()

/**
 * Legge [key] senza far cadere l'app (R2): una memoria che non risponde (il file del DataStore corrotto, un errore di IO,
 * una memoria dell'app che lancia) dà un fallimento, e chi chiama decide come se non sapesse. Non ripete l'errore nel log
 * con la chiave o il valore: solo il tipo.
 */
internal suspend fun FluidFirstUseMemory.readSafely(key: String): Result<String?> =
  try {
    Result.success(read(key))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(FirstUseLogTag, "memoria della prima volta illeggibile: ${e.javaClass.simpleName}")
    Result.failure(e)
  }

/** Scrive [key] senza far cadere l'app (R2). Falso se la memoria non l'ha preso. */
internal suspend fun FluidFirstUseMemory.writeSafely(key: String, value: String?): Boolean =
  try {
    write(key, value)
    true
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(FirstUseLogTag, "memoria della prima volta non scritta: ${e.javaClass.simpleName}")
    false
  }

private const val FirstUseLogTag = "FluidFirstUse"
