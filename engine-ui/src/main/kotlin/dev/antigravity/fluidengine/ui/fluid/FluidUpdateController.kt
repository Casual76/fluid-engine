package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.foundation.UpdateChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * La colla fra un [AppUpdater] e la vista dell'aggiornamento (3.0.1): lo stato che `fluidUpdateSection` e
 * [FluidUpdatePromptSheet] disegnano, il controllo, l'installazione con gli errori presi.
 *
 * Tre app (Pampa sounds, KeyVoice, Codex) avevano scritto la stessa classe di settanta righe, con le stesse
 * scelte: un controllo che non trova niente e' [FluidUpdateUiState.UpToDate] e non un errore, uno che non
 * riesce e' [FluidUpdateUiState.CheckFailed] e non «nessun aggiornamento», un secondo tocco mentre lavora non
 * rilancia niente, un'eccezione dell'installatore diventa [FluidUpdateUiState.Failed] (la cancellazione no:
 * quella si propaga) e lo stesso tocco riprova.
 *
 * **Dove sta, e perche'.** In `engine-ui`, non in `engine-update`: il controllore parla l'[AppUpdater] di
 * `engine-foundation` (che `EngineAppUpdater` implementa, e che un'app con un updater suo puo' implementare)
 * e produce il [FluidUpdateUiState] di qui. In `engine-update` avrebbe voluto Compose e `engine-ui`, due
 * dipendenze che quel modulo non ha e che un'app senza interfaccia non deve tirarsi dentro.
 *
 * **Lo scope decide quanto vive un download.** Con quello di una schermata un'installazione si ferma quando
 * si cambia pagina; con quello dell'app (un `CoroutineScope` del processo, o l'`applicationScope` che l'app
 * ha gia') continua, e tornando nelle impostazioni la sezione dice ancora dov'e' arrivata.
 *
 * @property state dove sta l'aggiornamento: da passare a `fluidUpdateSection(state = …)`.
 * @property lastCheckedAtMillis quando e' finito l'ultimo controllo, o null: «Controllato 5 minuti fa».
 * @property available la versione trovata dall'ultimo controllo, o null: quella che [install] installa.
 * @property channel il canale su cui [check] cerca; lo aggiorna [rememberFluidUpdateController].
 * @property ignoredVersion la versione saltata («Salta questa versione»), che [check] non offre.
 */
@Stable
class FluidUpdateController(
  private val updater: AppUpdater,
  private val scope: CoroutineScope,
  /** La versione installata, **senza** il suffisso delle build di debug: il manifest conosce solo le release. */
  private val currentVersion: String,
  private val strings: FluidUpdateStrings = FluidUpdateStrings(),
  private val now: () -> Long = System::currentTimeMillis,
) {
  var state: FluidUpdateUiState by mutableStateOf(FluidUpdateUiState.Idle)
    private set

  var lastCheckedAtMillis: Long? by mutableStateOf(null)
    private set

  var available: AvailableAppUpdate? by mutableStateOf(null)
    private set

  var channel: UpdateChannel by mutableStateOf(UpdateChannel.STABLE)

  var ignoredVersion: String by mutableStateOf("")

  /** Chiede al manifest se c'e' una versione piu' nuova sul [channel]. Mentre lavora un secondo tocco non fa niente. */
  fun check() {
    if (state.isBusy) return
    state = FluidUpdateUiState.Checking
    scope.launch {
      val result = try {
        updater.check(currentVersionName = currentVersion, channel = channel, ignoredVersion = ignoredVersion)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        Result.failure(failure)
      }
      result.fold(
        onSuccess = { found ->
          available = found
          state = if (found == null) {
            FluidUpdateUiState.UpToDate
          } else {
            FluidUpdateUiState.Available(found.toFluidUpdateRelease())
          }
        },
        onFailure = { failure ->
          available = null
          state = FluidUpdateUiState.CheckFailed(failure.message)
        },
      )
      lastCheckedAtMillis = now()
    }
  }

  /**
   * Scarica e installa la versione trovata dall'ultimo controllo. Dopo un errore, o col permesso
   * d'installare ancora da dare ([FluidUpdateUiState.NeedsPermission]: l'installatore dell'engine apre le
   * impostazioni da se'), e' lo stesso tocco che riprova. Senza una versione in mano non fa niente.
   */
  fun install() {
    val update = available ?: return
    if (state.isBusy) return
    val release = update.toFluidUpdateRelease()
    state = FluidUpdateUiState.Downloading(release = release, progress = null)
    scope.launch {
      try {
        updater.install(update).collect { step -> state = step.toFluidUpdateState(release) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        state = FluidUpdateUiState.Failed(release, failure.message ?: strings.unknownError)
      }
    }
  }

  /** Dimentica la versione trovata e torna a «mai controllato» (un cambio di canale, per esempio). */
  fun reset() {
    if (state.isBusy) return
    available = null
    state = FluidUpdateUiState.Idle
  }
}

/**
 * Un [FluidUpdateController] ricordato nella composizione (3.0.1).
 *
 * ```kotlin
 * val update = rememberFluidUpdateController(updater, appScope, BuildConfig.VERSION_NAME.substringBefore("-"))
 * fluidUpdateSection(
 *   state = update.state,
 *   onCheck = update::check,
 *   onInstall = update::install,
 *   lastCheckedAtMillis = update.lastCheckedAtMillis,
 * )
 * ```
 *
 * @param scope di serie quello della composizione: un download si ferma quando il punto in cui e' chiamata
 *   esce di scena. Per un'installazione che sopravvive alla navigazione si passa uno scope dell'app, e la si
 *   chiama alla radice.
 * @param channel e [ignoredVersion] valgono per il prossimo [FluidUpdateController.check].
 * @param checkOnStart un controllo appena il controllore nasce: il «Controlla all'avvio» di
 *   `fluidUpdateSection(autoCheck = …)`. Uno solo per controllore, non a ogni ricomposizione.
 */
@Composable
fun rememberFluidUpdateController(
  updater: AppUpdater,
  scope: CoroutineScope = rememberCoroutineScope(),
  currentVersion: String,
  channel: UpdateChannel = UpdateChannel.STABLE,
  ignoredVersion: String = "",
  checkOnStart: Boolean = false,
  strings: FluidUpdateStrings = FluidUpdateStrings(),
): FluidUpdateController {
  val controller = remember(updater, scope, currentVersion) {
    FluidUpdateController(updater = updater, scope = scope, currentVersion = currentVersion, strings = strings)
  }
  SideEffect {
    controller.channel = channel
    controller.ignoredVersion = ignoredVersion
  }
  LaunchedEffect(controller) {
    if (checkOnStart) {
      controller.channel = channel
      controller.ignoredVersion = ignoredVersion
      controller.check()
    }
  }
  return controller
}
