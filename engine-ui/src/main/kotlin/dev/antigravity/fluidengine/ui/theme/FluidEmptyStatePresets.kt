package dev.antigravity.fluidengine.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.VisibilityThreshold
import android.os.SystemClock
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidContentStateStrings
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonList
import dev.antigravity.fluidengine.ui.fluid.FluidTextMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionWords
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CloudOff
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh
import dev.antigravity.fluidengine.ui.fluid.icons.SearchOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * I casi pronti di una pagina senza contenuto (F8.1, `stati-pronti`), come `ContentUnavailableView` di SwiftUI (iOS 17) e
 * il suo preset `.search(text:)`: ognuno ha l'icona, le parole e l'azione di serie, e l'app dice solo in che stato è e
 * cosa fa «Riprova». Una classe chiusa e non un enum: un caso nuovo si aggiunge senza rompere chi usa questi.
 */
@Immutable
sealed class FluidEmptyPreset {
  /** Una ricerca senza risultati: «Nessun risultato per «cena»», «Controlla come l'hai scritto…». */
  data class NoResults(val query: String? = null) : FluidEmptyPreset()

  /** Senza rete: la pagina riprova da sola quando torna (`online` di [FluidEmptyState]). */
  data object Offline : FluidEmptyPreset()

  /** Un errore, con «Riprova» come tasto con esito. [detail] è la frase dell'app, se ne ha una. */
  data class Error(val detail: String? = null) : FluidEmptyPreset()

  /** Un permesso spento, con «Apri le impostazioni». [detail] dice quale e perché. */
  data class Permission(val detail: String? = null) : FluidEmptyPreset()

  /** Non c'è ancora niente («Ancora niente qui»): una casella vuota, un album senza foto. */
  data object Empty : FluidEmptyPreset()
}

/**
 * Uno stato vuoto **già pronto** (F8.1): [FluidEmptyState] con l'icona, il titolo e la frase del caso ([preset]), nelle
 * parole di `FluidLabels.contentStates` (in italiano di serie). Le parole dell'app vincono ([title], [detail]).
 *
 * - **«Riprova» è un tasto con esito** ([onRetry], `FluidActionButton`): mostra il lavoro, la spunta o l'errore, e mentre
 *   lavora non riparte. [onRetry] riesce se finisce e non riesce se lancia.
 * - **Offline**: con [online] la pagina riprova da sola quando la rete torna (il passaggio da falso a vero), al più una
 *   volta ogni [FluidEmptyStateDefaults.AutoRetryGapMillis]: il riprova automatico non parte a raffica. Lo stato della
 *   rete lo dà l'app (`EngineNetwork.states` di `engine-net` guarda `NET_CAPABILITY_VALIDATED`: una rete senza internet
 *   non è «online»). **Dalla F8.20**, se l'app non lo passa e le condizioni del telefono sono lette
 *   (`EngineConditions.install` di `engine-net`, il posto solo `FluidDeviceConditions`), la pagina le legge da lì: riprova
 *   da sola senza una riga in più, con la tenuta contro le micro-cadute.
 * - **Permesso**: [onAction] è «Apri le impostazioni» ([actionLabel] per un'altra parola).
 *
 * L'icona è grande e quieta, come il simbolo di `ContentUnavailableView`, e TalkBack non la legge: il titolo dice tutto.
 */
@Composable
fun FluidEmptyState(
  preset: FluidEmptyPreset,
  modifier: Modifier = Modifier,
  title: String? = null,
  detail: String? = null,
  onRetry: (suspend () -> Unit)? = null,
  online: Boolean? = null,
  onAction: (() -> Unit)? = null,
  actionLabel: String? = null,
) {
  val words = LocalFluidLabels.current.contentStates
  val shownTitle = title ?: preset.title(words)
  val shownDetail = detail ?: preset.detail(words)
  val icon = preset.icon()
  val illustration: @Composable () -> Unit = {
    FluidIcon(imageVector = icon, contentDescription = null, size = FluidEmptyStateDefaults.IconSize)
  }
  val retries = preset is FluidEmptyPreset.Error || preset is FluidEmptyPreset.Offline
  // F8.20: senza `online` dall'app, quello del posto solo delle condizioni (se qualcuno le legge).
  val measured = if (preset is FluidEmptyPreset.Offline && online == null) {
    val conditions by FluidDeviceConditions.state.collectAsState()
    conditions.takeIf { it.known }?.online
  } else {
    null
  }
  val shownOnline = online ?: measured
  val action: (@Composable () -> Unit)? = when {
    retries && onRetry != null -> {
      { FluidRetryButton(words, onRetry, online = if (preset is FluidEmptyPreset.Offline) shownOnline else null) }
    }
    onAction != null -> {
      {
        FluidButton(
          text = actionLabel ?: if (preset is FluidEmptyPreset.Permission) words.openSettings else words.retry,
          onClick = onAction,
          style = dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle.Tinted,
        )
      }
    }
    else -> null
  }
  FluidEmptyStateWithAction(shownTitle, shownDetail, modifier, illustration, action)
}

/** Le misure e i tempi degli stati pronti. */
object FluidEmptyStateDefaults {
  /** L'icona: 48 dp, il simbolo grande di `ContentUnavailableView` (`.largeTitle` ingrandito), a occhio sul confronto. */
  val IconSize = 48.dp

  /**
   * Il tempo minimo fra due tentativi automatici quando torna la rete: **5 s**, a occhio il 23 novembre 2026. Una rete
   * che va e viene (un treno in galleria) non deve far partire un tentativo a ogni passaggio.
   */
  const val AutoRetryGapMillis: Long = 5_000L
}

private fun FluidEmptyPreset.title(words: FluidContentStateStrings): String = when (this) {
  is FluidEmptyPreset.NoResults -> query?.takeIf { it.isNotBlank() }?.let { String.format(words.noResultsFor, it) } ?: words.noResults
  FluidEmptyPreset.Offline -> words.offline
  is FluidEmptyPreset.Error -> words.error
  is FluidEmptyPreset.Permission -> words.permission
  FluidEmptyPreset.Empty -> words.empty
}

private fun FluidEmptyPreset.detail(words: FluidContentStateStrings): String = when (this) {
  is FluidEmptyPreset.NoResults -> words.noResultsDetail
  FluidEmptyPreset.Offline -> words.offlineDetail
  is FluidEmptyPreset.Error -> detail ?: words.errorDetail
  is FluidEmptyPreset.Permission -> detail ?: words.permissionDetail
  FluidEmptyPreset.Empty -> words.emptyDetail
}

private fun FluidEmptyPreset.icon(): ImageVector = when (this) {
  is FluidEmptyPreset.NoResults -> FluidIcons.SearchOff
  FluidEmptyPreset.Offline -> FluidIcons.CloudOff
  is FluidEmptyPreset.Error -> FluidIcons.Error
  is FluidEmptyPreset.Permission -> FluidIcons.Lock
  FluidEmptyPreset.Empty -> FluidIcons.Inbox
}

/** «Riprova» come tasto con esito, e il riprova automatico al ritorno della rete. */
@Composable
private fun FluidRetryButton(words: FluidContentStateStrings, onRetry: suspend () -> Unit, online: Boolean?) {
  val preset = remember(words) {
    FluidActionPreset.custom(
      idle = FluidIcons.Refresh,
      success = FluidIcons.Check,
      failure = FluidIcons.Error,
      words = FluidActionWords(
        idle = words.retry,
        working = words.retrying,
        succeeded = words.retried,
        failed = words.retryFailed,
        cancelled = words.retry,
      ),
    )
  }
  val runner = rememberFluidActionRunner(preset)
  val retry by rememberUpdatedState(onRetry)
  var lastAuto by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
  var wasOffline by remember { mutableStateOf(online == false) }
  LaunchedEffect(online) {
    if (online == false) wasOffline = true
    if (online == true && wasOffline) {
      wasOffline = false
      val now = SystemClock.elapsedRealtime()
      if (now - lastAuto >= FluidEmptyStateDefaults.AutoRetryGapMillis && !runner.isRunning) {
        lastAuto = now
        runner.run { retry() }
      }
    }
  }
  FluidActionButton(
    preset = preset,
    state = runner.state,
    onClick = { runner.run { retry() } },
    label = words.retry,
  )
}

/**
 * Lo stato del contenuto di una pagina, per [FluidContentState]: carica, è pronto col suo valore, o non c'è (con il caso
 * pronto da mostrare).
 */
@Immutable
sealed interface FluidLoadState<out T> {
  /** Sta caricando: lo scheletro. */
  data object Loading : FluidLoadState<Nothing>

  /** Il contenuto. */
  data class Ready<T>(val value: T) : FluidLoadState<T>

  /** Niente da mostrare: uno degli stati pronti. */
  data class Unavailable(val preset: FluidEmptyPreset) : FluidLoadState<Nothing>
}

/**
 * Il contenuto di una pagina che passa **fra caricamento, vuoto, errore e contenuto senza salti** (F8.1, `stati-pronti`):
 * lo scheletro ([loading], di serie [FluidSkeletonList]), lo stato pronto ([unavailable], di serie [FluidEmptyState] col
 * preset) o il contenuto. Il nuovo entra **sfocato e si mette a fuoco** mentre il vecchio svanisce (la grammatica del
 * contenuto che cambia identità, R7, T = [FluidTextMotion.ImageArrivalMillis]), e l'altezza segue con una molla: la pagina
 * non salta da uno scheletro alto a un errore basso.
 *
 * Fra due contenuti pronti (un valore nuovo) non c'è passaggio: il contenuto si aggiorna e basta. Col movimento ridotto
 * è una dissolvenza breve, senza sfocatura.
 */
@Composable
fun <T> FluidContentState(
  state: FluidLoadState<T>,
  modifier: Modifier = Modifier,
  loading: @Composable () -> Unit = { FluidSkeletonList() },
  unavailable: @Composable (FluidEmptyPreset) -> Unit = { FluidEmptyState(it) },
  content: @Composable (T) -> Unit,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val words = LocalFluidLabels.current.contentStates
  val duration = FluidTextMotion.ImageArrivalMillis
  AnimatedContent(
    targetState = state,
    modifier = modifier,
    contentKey = { it.kindKey() },
    transitionSpec = {
      if (reduced) {
        fadeIn(tween(ReducedFadeMillis)) togetherWith fadeOut(tween(ReducedFadeMillis)) using SizeTransform(clip = false)
      } else {
        // R7: il vecchio è sparito a 0,66 T, il nuovo comincia a 0,36 T e ha l'opacità piena a 1,15 T.
        fadeIn(tween((duration * 0.79f).toInt(), delayMillis = (duration * 0.36f).toInt(), easing = FluidMotion.EaseOut)) togetherWith
          fadeOut(tween((duration * 0.66f).toInt(), easing = FluidMotion.EaseOut)) using
          SizeTransform(clip = false) { _, _ ->
            spring(FluidMotion.DampingChrome, FluidMotion.ResponseSmooth, IntSize.VisibilityThreshold)
          }
      }
    },
    label = "contenuto",
  ) { shown ->
    val blur = transition.animateFloat(
      transitionSpec = { tween(duration, easing = FluidMotion.EaseOut) },
      label = "sfocatura",
    ) { phase -> if (phase == EnterExitState.Visible || reduced) 0f else FluidTextMotion.ImageArrivalBlurDp }
    val layer = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        val sigma = blur.value.dp.toPx()
        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sigma > 0.25f) {
          BlurEffect(sigma, sigma, TileMode.Decal)
        } else {
          null
        }
      }
    when (shown) {
      FluidLoadState.Loading -> androidx.compose.foundation.layout.Box(layer.semantics { contentDescription = words.loading }) { loading() }
      is FluidLoadState.Unavailable -> androidx.compose.foundation.layout.Box(layer) { unavailable(shown.preset) }
      is FluidLoadState.Ready -> androidx.compose.foundation.layout.Box(layer) { content(shown.value) }
    }
  }
}

private const val ReducedFadeMillis = 150

/** La chiave del passaggio: lo stesso tipo di stato non passa (un contenuto nuovo si aggiorna e basta). */
private fun FluidLoadState<*>.kindKey(): Any = when (this) {
  FluidLoadState.Loading -> 0
  is FluidLoadState.Ready -> 1
  is FluidLoadState.Unavailable -> preset::class
}
