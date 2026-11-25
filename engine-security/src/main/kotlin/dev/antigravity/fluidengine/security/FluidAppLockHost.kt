package dev.antigravity.fluidengine.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCodeField
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSecureWindowHold
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.Fingerprint
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCodeFieldState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.vault.VaultAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chi chiede l'impronta o il codice per aprire l'app: di serie il dialogo di sistema ([FluidSystemAuthenticator]); un
 * test ne passa uno finto.
 */
fun interface FluidLockAuthenticator {
  /** Mostra il dialogo e aspetta l'esito. [fallback]: il tasto in basso della riserva («Usa il PIN dell'app»). */
  suspend fun authenticateUser(title: String, subtitle: String?, fallback: String?): VaultAuthResult
}

/** Il blocco dell'app di questo albero, per i lanciatori che non devono bloccare ([rememberFluidLockSafeLauncher]). */
val LocalFluidAppLock: ProvidableCompositionLocal<FluidAppLock?> = staticCompositionLocalOf { null }

/** I tag dei test. */
object FluidAppLockTags {
  const val Screen = "fluid-lock:schermata"
  const val Unlock = "fluid-lock:sblocca"
  const val UsePin = "fluid-lock:pin"
  const val PinField = "fluid-lock:campo-pin"
  const val Message = "fluid-lock:messaggio"
  const val Content = "fluid-lock:contenuto"
}

/**
 * I numeri di movimento della schermata di blocco. **A occhio**, 24/11/2026: Apple non pubblica come si apre un'app
 * bloccata; questo è quello che si vede in iOS 18–27, la schermata che si dissolve e l'app che entra a fuoco.
 */
object FluidAppLockMotion {
  /** Quanto dura l'uscita della schermata di blocco: 320 ms. */
  const val RevealMs: Int = 320

  /** La sfocatura dell'app che entra, al primo fotogramma: 18 dp, a zero alla fine. */
  const val RevealBlurDp: Float = 18f

  /** Il dialogo automatico parte quando la schermata è già lì: 250 ms dopo. */
  const val AutoPromptDelayMs: Long = 250L
}

/** Il dialogo di sistema dell'attività di questo albero, o null se non c'è un'attività. */
@Composable
fun rememberFluidSystemAuthenticator(labels: FluidSecurityLabels = LocalFluidSecurityLabels.current): FluidSystemAuthenticator? {
  val activity = LocalContext.current.findActivity() ?: return null
  return remember(activity, labels) { FluidSystemAuthenticator(activity, labels) }
}

/** Il dialogo di sistema come [FluidLockAuthenticator]. */
fun FluidSystemAuthenticator.asLockAuthenticator(): FluidLockAuthenticator =
  FluidLockAuthenticator { title, subtitle, fallback -> authenticateUser(title, subtitle, fallback) }

/**
 * **L'app con il blocco** (F8.23): [content] è l'app, e quando [lock] è bloccato sopra c'è la schermata di blocco di
 * vetro, col nome e l'icona dell'app, il tasto «Sblocca» (il dialogo di sistema parte anche da solo, come Face ID) e
 * «Usa il PIN dell'app» se c'è il PIN.
 *
 * - **Il contenuto bloccato resta composto** (la navigazione e lo stato restano), ma non si disegna, non prende il
 *   dito e TalkBack non lo legge; il fuoco e la tastiera si chiudono.
 * - **L'anteprima nelle recenti è oscurata** finché il blocco è acceso (`setRecentsScreenshotEnabled(false)` da Android
 *   13, `FLAG_SECURE` prima), con [hidePreview].
 * - **All'apertura** la schermata si dissolve e l'app entra dalla sfocatura ([FluidAppLockMotion]); con «Riduci
 *   movimento» si apre e basta.
 * - **Ferma, non ridisegna**: lo sfondo è un'immagine ferma e il vetro la campiona una volta.
 *
 * @param appName il nome nella schermata e nel dialogo; di serie quello dell'app.
 * @param authenticator il dialogo; di serie quello di sistema dell'attività.
 * @param backdrop lo sfondo dietro il vetro; di serie una sfumatura ferma nei colori del tema.
 */
@Composable
fun FluidAppLockHost(
  lock: FluidAppLock,
  modifier: Modifier = Modifier,
  appName: String = rememberAppName(),
  authenticator: FluidLockAuthenticator? = rememberFluidSystemAuthenticator()?.asLockAuthenticator(),
  capability: FluidAuthCapability = rememberFluidAuthCapability(lock),
  hidePreview: Boolean = true,
  labels: FluidSecurityLabels = LocalFluidSecurityLabels.current,
  backdrop: (@Composable BoxScope.() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val state by lock.state.collectAsState()
  val settings by lock.settings.collectAsState()
  // Bloccata, o ancora da leggere: il contenuto non si vede. Solo «bloccata» mostra la schermata (all'avvio, finché le
  // impostazioni non sono lette, un attimo di fondo e basta: un'app col blocco spento non deve vederla mai).
  val locked = state != FluidAppLockState.Unlocked
  val showScreen = state == FluidAppLockState.Locked
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  FluidSecureWindowHold(active = hidePreview && settings.enabled, blockScreenshots = false)

  // 0: bloccata; 1: aperta. Va verso 1 con la dissolvenza solo dopo una schermata di blocco vera, e torna a 0 di colpo.
  val reveal = remember { Animatable(if (locked) 0f else 1f) }
  // Vero da quando la schermata si è vista fino alla fine della sua uscita. Un campo semplice, non uno stato: lo legge
  // la dissolvenza, e scriverlo non deve ricomporre.
  val screenSeen = remember { booleanArrayOf(false) }
  if (showScreen) screenSeen[0] = true
  LaunchedEffect(state, reduced) {
    when {
      locked -> reveal.snapTo(0f)
      reduced || !screenSeen[0] -> reveal.snapTo(1f)
      else -> reveal.animateTo(1f, tween(FluidAppLockMotion.RevealMs))
    }
    if (!locked) screenSeen[0] = false
  }
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  LaunchedEffect(locked) {
    if (locked) {
      focus.clearFocusSafely()
      keyboard?.hide()
    }
  }

  CompositionLocalProvider(LocalFluidAppLock provides lock) {
    Box(modifier.fillMaxSize()) {
      // Vero solo durante l'uscita della schermata: si ricompone quando cambia, non a ogni fotogramma.
      val revealing by remember(locked) { derivedStateOf { !locked && screenSeen[0] && reveal.value < 1f } }
      Box(
        Modifier
          .fillMaxSize()
          .testTag(FluidAppLockTags.Content)
          .then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)
          .graphicsLayer {
            alpha = if (locked) 0f else 1f
            // L'app entra dalla sfocatura: la schermata la fa cambiare da sola, non il dito (F8, «dito nitido»).
            val sigma = FluidAppLockMotion.RevealBlurDp.dp.toPx() * (1f - reveal.value)
            renderEffect = if (!locked && screenSeen[0] && Build.VERSION.SDK_INT >= 31 && sigma > 0.25f) BlurEffect(sigma, sigma, TileMode.Clamp) else null
          },
      ) {
        content()
      }
      if (showScreen || revealing) {
        FluidAppLockScreen(
          lock = lock,
          appName = appName,
          authenticator = authenticator,
          capability = capability,
          labels = labels,
          backdrop = backdrop,
          modifier = Modifier.graphicsLayer {
            val p = reveal.value
            alpha = 1f - p
            val s = 1f + 0.06f * p
            scaleX = s
            scaleY = s
          },
        )
      }
    }
  }
}

/** Le capacità del telefono, rilette a ogni blocco (un'impronta registrata nel frattempo conta). */
@Composable
fun rememberFluidAuthCapability(lock: FluidAppLock): FluidAuthCapability {
  val context = LocalContext.current
  val count = lock.lockCount
  return remember(context, count) { FluidBiometrics.capability(context) }
}

/** Il nome dell'app, dal suo manifest. */
@Composable
fun rememberAppName(): String {
  val context = LocalContext.current
  return remember(context) {
    context.applicationInfo.loadLabel(context.packageManager).toString()
  }
}

@Composable
private fun FluidAppLockScreen(
  lock: FluidAppLock,
  appName: String,
  authenticator: FluidLockAuthenticator?,
  capability: FluidAuthCapability,
  labels: FluidSecurityLabels,
  backdrop: (@Composable BoxScope.() -> Unit)?,
  modifier: Modifier,
) {
  val settings by lock.settings.collectAsState()
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  // La schermata ha la tastiera del PIN: quando se ne va, la porta con sé (backstop dell'apertura qui sotto).
  DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
  val lockCount = lock.lockCount
  val canPrompt = authenticator != null && (capability.anyBiometrics || capability.deviceCredential)
  var pinMode by remember(lockCount) { mutableStateOf(!canPrompt && settings.hasPin) }
  var message by remember(lockCount) { mutableStateOf<String?>(null) }
  var waitUntil by remember(lockCount) { mutableStateOf<Long?>(null) }
  val codeState = rememberFluidCodeFieldState(settings.pinLength)
  val title = labels.lockedTitle.format(appName)
  val subtitle = when {
    pinMode -> labels.lockedSubtitlePin
    capability.anyBiometrics -> labels.lockedSubtitleBiometric
    capability.deviceCredential -> labels.lockedSubtitleCredential
    settings.hasPin -> labels.lockedSubtitlePin
    else -> labels.nothingToUnlockWith
  }

  val prompt: suspend () -> Unit = prompt@{
    val auth = authenticator ?: return@prompt
    when (val result = auth.authenticateUser(labels.promptTitle.format(appName), null, if (settings.hasPin) labels.usePin else null)) {
      VaultAuthResult.Success -> lock.unlock()
      VaultAuthResult.Fallback -> pinMode = true
      VaultAuthResult.Cancelled -> Unit
      is VaultAuthResult.Failed -> message = result.message
      is VaultAuthResult.Unavailable -> if (settings.hasPin) pinMode = true else message = labels.nothingToUnlockWith
    }
  }
  val currentPrompt by rememberUpdatedState(prompt)

  // Il dialogo parte da solo, una volta per blocco, quando l'attività è davanti (come Face ID all'apertura).
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(lockCount, canPrompt) {
    if (!canPrompt) return@LaunchedEffect
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    delay(FluidAppLockMotion.AutoPromptDelayMs)
    currentPrompt()
  }
  // Fine dell'attesa dopo troppi PIN sbagliati: un solo risveglio, nessun conto alla rovescia che ridisegna.
  LaunchedEffect(waitUntil) {
    val until = waitUntil ?: return@LaunchedEffect
    delay((until - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
    waitUntil = null
    message = null
  }

  val glass = rememberGlassBackdrop()
  Box(
    modifier
      .fillMaxSize()
      .testTag(FluidAppLockTags.Screen)
      .semantics { paneTitle = labels.lockScreenDescription }
      // Il dito non passa all'app sotto: fra due fratelli Compose consegna il tocco solo a quello in cima che lo
      // ascolta. Niente consumo: consumare qui annullerebbe i tasti della schermata (che controllano il passaggio finale).
      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
  ) {
    Box(Modifier.fillMaxSize().glassBackdropSource(glass)) {
      if (backdrop != null) backdrop() else FluidLockBackdrop()
    }
    CompositionLocalProvider(LocalGlassBackdrop provides glass) {
      Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
          Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .glassSurface(
              state = glass,
              tint = GlassDefaults.panelTint(),
              shape = ContinuousCornerShape(FluidRadius.Sheet),
              role = GlassRole.Modal,
              optics = GlassDefaults.panelOptics(),
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AppIcon()
          Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
          )
          Text(
            message ?: subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(FluidAppLockTags.Message).semantics { liveRegion = LiveRegionMode.Polite },
          )
          if (pinMode) {
            FluidCodeField(
              state = codeState,
              secret = true,
              autoFocus = true,
              enabled = waitUntil == null,
              label = labels.pinTitle,
              pasteButton = false,
              // Come il codice di sblocco di iOS: una riga sola, che si stringe se il posto non basta.
              singleRow = true,
              modifier = Modifier.testTag(FluidAppLockTags.PinField),
              onComplete = { code ->
                scope.launch {
                  val pin = code.toCharArray()
                  val result = try {
                    lock.checkPin(pin)
                  } catch (e: Exception) {
                    // La cassaforte non risponde: lo si dice, l'app non si chiude e resta bloccata.
                    codeState.fail()
                    message = labels.authenticationFailed
                    return@launch
                  } finally {
                    pin.fill('0')
                  }
                  when (result) {
                    FluidPinCheck.Correct -> {
                      // Prima che la schermata se ne vada: senza, la tastiera numerica passava un attimo alla QWERTY
                      // (il fuoco scivolava fuori dal campo del PIN) e poi si chiudeva (visto sul Tab S9, 25/11/2026).
                      // Sul thread principale: la vista non si tocca da altri, e dopo la cassaforte (che lavora su IO)
                      // non è detto di esserci (nei test di Compose non ci si torna). Già lì, `immediate` non aspetta.
                      withContext(Dispatchers.Main.immediate) {
                        keyboard?.hide()
                        focusManager.clearFocusSafely()
                      }
                      codeState.clear()
                    }
                    is FluidPinCheck.Wrong -> {
                      codeState.fail()
                      message = result.waitUntil?.let { labels.pinWaitText(it - android.os.SystemClock.elapsedRealtime()) }
                        ?: labels.pinWrong
                      waitUntil = result.waitUntil
                    }
                    is FluidPinCheck.Wait -> {
                      codeState.fail()
                      message = labels.pinWaitText(result.until - android.os.SystemClock.elapsedRealtime())
                      waitUntil = result.until
                    }
                    FluidPinCheck.NoPin -> pinMode = false
                  }
                }
              },
            )
            if (canPrompt) {
              FluidButton(
                text = labels.back,
                onClick = { pinMode = false; message = null },
                style = FluidButtonStyle.Plain,
              )
            }
          } else {
            if (canPrompt) {
              FluidButton(
                text = labels.unlock,
                onClick = { scope.launch { currentPrompt() } },
                style = FluidButtonStyle.Filled,
                fillWidth = true,
                modifier = Modifier.testTag(FluidAppLockTags.Unlock),
                leading = { FluidIcon(if (capability.anyBiometrics) FluidIcons.Fingerprint else FluidIcons.Lock, contentDescription = null) },
              )
            }
            if (settings.hasPin) {
              FluidButton(
                text = labels.usePin,
                onClick = { pinMode = true; message = null },
                style = if (canPrompt) FluidButtonStyle.Plain else FluidButtonStyle.Filled,
                fillWidth = !canPrompt,
                modifier = Modifier.testTag(FluidAppLockTags.UsePin),
              )
            }
          }
        }
      }
    }
  }
}

/** L'icona dell'app, 64 dp con l'angolo continuo; il lucchetto se l'icona non si legge. */
@Composable
private fun AppIcon() {
  val context = LocalContext.current
  val bitmap: ImageBitmap? = remember(context) {
    try {
      val drawable: Drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
      drawable.toBitmap(192, 192).asImageBitmap()
    } catch (e: Exception) {
      null
    }
  }
  if (bitmap != null) {
    Image(bitmap, contentDescription = null, modifier = Modifier.size(64.dp).clip(ContinuousCornerShape(16.dp)))
  } else {
    FluidIcon(FluidIcons.Lock, contentDescription = null, size = 44.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/**
 * Lo sfondo di serie della schermata di blocco: una sfumatura ferma nei colori del tema, con due macchie di luce che il
 * vetro rifrange. Si disegna una volta: niente che si muova da solo.
 */
@Composable
fun FluidLockBackdrop(modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Canvas(modifier.fillMaxSize().background(scheme.surface)) {
    drawRect(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surface, scheme.tertiaryContainer), Offset.Zero, Offset(size.width, size.height)))
    drawCircle(
      Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.55f), Color.Transparent), Offset(size.width * 0.25f, size.height * 0.3f), size.minDimension * 0.55f),
      radius = size.minDimension * 0.55f,
      center = Offset(size.width * 0.25f, size.height * 0.3f),
    )
    drawCircle(
      Brush.radialGradient(listOf(scheme.tertiary.copy(alpha = 0.5f), Color.Transparent), Offset(size.width * 0.8f, size.height * 0.72f), size.minDimension * 0.6f),
      radius = size.minDimension * 0.6f,
      center = Offset(size.width * 0.8f, size.height * 0.72f),
    )
  }
}

// ================================================================================================================
// Uscire senza bloccare: condividere, scegliere un file
// ================================================================================================================

/** Un lanciatore che, prima di aprire l'altra app, dice al blocco di non bloccare al ritorno. */
class FluidLockSafeLauncher<I> internal constructor(
  private val launcher: ManagedActivityResultLauncher<I, *>,
  private val lock: () -> FluidAppLock?,
) {
  /** Apre l'altra app (la scelta di file, la fotocamera): il ritorno non blocca. */
  fun launch(input: I) {
    lock()?.allowLeaving()
    launcher.launch(input)
  }
}

/**
 * `rememberLauncherForActivityResult` che non fa bloccare l'app al ritorno: per la scelta di un file, di una foto, la
 * fotocamera di sistema. Il blocco è quello di [LocalFluidAppLock] (lo dà [FluidAppLockHost]).
 */
@Composable
fun <I, O> rememberFluidLockSafeLauncher(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): FluidLockSafeLauncher<I> {
  val lock by rememberUpdatedState(LocalFluidAppLock.current)
  val launcher = rememberLauncherForActivityResult(contract, onResult)
  return remember(launcher) { FluidLockSafeLauncher(launcher) { lock } }
}

/**
 * Apre [intent] (una condivisione: `Intent.createChooser`) senza far bloccare l'app al ritorno.
 */
fun FluidAppLock?.startExternal(context: Context, intent: Intent) {
  this?.allowLeaving()
  context.startActivity(intent)
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

private fun FocusManager.clearFocusSafely() {
  try {
    clearFocus(force = true)
  } catch (_: IllegalStateException) {
    // Nessun fuoco da togliere.
  }
}
