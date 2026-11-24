package dev.antigravity.fluidengine.ui.fluid

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.VisibilityOff
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * La **modalità discreta** (F8.3, `valore-riservato`): vera, vela insieme tutti i valori riservati segnati
 * `discreetOnly` (un voto in classe, un saldo sui mezzi). L'interruttore è dell'app; l'engine la legge da qui.
 */
val LocalFluidDiscreetMode: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * Chi decide se un valore si può scoprire: di serie nessuno (basta il tocco). L'app, o il blocco dell'engine quando ci
 * sarà (`blocco-biometrico`), ne dà uno che chiede l'impronta o il PIN e risponde vero o falso.
 */
fun interface FluidPrivateGate {
  /** Vero se il valore si può mostrare. Si chiama dopo il tocco, sul thread dell'interfaccia. */
  suspend fun authorize(): Boolean
}

/** Il cancello in uso per i valori riservati di questo albero; null: il tocco basta. */
val LocalFluidPrivateGate: ProvidableCompositionLocal<FluidPrivateGate?> = staticCompositionLocalOf { null }

/** Lo stato di un valore riservato: coperto o scoperto. Non si salva: dopo una rotazione o il ritorno nell'app è coperto. */
@Stable
class FluidPrivateState {
  /** Vero se il valore si vede. */
  var isRevealed: Boolean by mutableStateOf(false)
    private set

  /** Mostra il valore, senza passare dal cancello (per un tasto «Mostra» dell'app che ha già chiesto l'impronta). */
  fun reveal() {
    isRevealed = true
  }

  /** Copre il valore. */
  fun cover() {
    isRevealed = false
  }
}

/** Uno stato coperto, ricordato nella composizione. */
@Composable
fun rememberFluidPrivateState(): FluidPrivateState = remember { FluidPrivateState() }

/** Come si vela un valore riservato: un segnaposto al posto del testo, o la sfocatura per un'immagine. */
@Stable
class FluidPrivateVeil private constructor(internal val blur: Boolean) {
  override fun toString(): String = if (blur) "FluidPrivateVeil.Blur" else "FluidPrivateVeil.Placeholder"

  companion object {
    /**
     * Il segnaposto di `redacted(reason: .privacy)` di SwiftUI: barre arrotondate al posto delle righe, col segno
     * dell'occhio barrato. Il valore **non si disegna** (scheda dell'idea: un testo corto sfocato si indovina).
     */
    val Placeholder: FluidPrivateVeil = FluidPrivateVeil(blur = false)

    /**
     * Il velo dei contenuti sensibili di Messaggi: l'immagine molto sfocata con l'occhio e «Tocca per vedere». Per le
     * immagini: una foto sfocata non si legge, e la sagoma aiuta a riconoscerla. Sotto Android 12 (niente sfocatura)
     * diventa il segnaposto.
     */
    val Blur: FluidPrivateVeil = FluidPrivateVeil(blur = true)
  }
}

/**
 * I numeri del valore riservato. **A occhio**, 23 novembre 2026, salvo dove detto.
 */
object FluidPrivateDefaults {
  /**
   * Dopo quanto un valore scoperto si ricopre da solo: 30 s. Apple non ricopre a tempo un `privacySensitive` (lo fa il
   * sistema col blocco dello schermo); 30 s è il tempo dei gestori di password per un campo mostrato. A occhio.
   */
  val RecoverAfter: Duration = 30.seconds

  /** Lo scambio fra velo e valore: 300 ms, il T dello scambio sfocato di R7 (F3.4), col colmo della sfocatura a metà. */
  internal const val SwapMs: Int = 300

  /** La sfocatura al colmo dello scambio: 8 dp, quella di [GlassContentDefaults.BlurReference] per il contenuto. */
  internal val SwapBlur: Dp = 8.dp

  /** La sfocatura del velo [FluidPrivateVeil.Blur]: 28 dp, abbastanza perché un volto o una cifra non si leggano. */
  internal val VeilBlur: Dp = 28.dp

  /** Il segno dell'occhio barrato: 16 dp, se il valore è largo almeno 64 dp. */
  internal val Glyph: Dp = 16.dp
  internal val GlyphMinWidth: Dp = 64.dp
}

/**
 * Un valore riservato (F8.3, idea `valore-riservato`): il dato — una chiave, un voto, un saldo, un'anteprima — coperto da
 * un velo finché la persona non lo tocca, come `redacted(reason: .privacy)` e `privacySensitive()` di SwiftUI e il velo
 * dei contenuti sensibili di Messaggi.
 *
 * - **Si scopre col tocco** (o con l'azione «Mostra» di TalkBack). Se c'è un [gate] (il blocco dell'engine, l'impronta
 *   dell'app) il tocco passa da lui.
 * - **Si ricopre da solo** dopo [recoverAfter], quando la pagina va in secondo piano, e quando il valore esce dalla
 *   composizione. Lo stato non si salva: dopo una rotazione è coperto.
 * - **Mai nelle schermate recenti.** Con [protectWindow], mentre il valore è scoperto la finestra non lascia la sua
 *   immagine alle app recenti (`Activity.setRecentsScreenshotEnabled(false)` da Android 13, `FLAG_SECURE` prima, che
 *   è l'unico modo): l'istantanea delle recenti si prende quando l'app esce, cioè proprio quando il valore è ancora in
 *   vista. Con [blockScreenshots] anche gli screenshot sono neri (`FLAG_SECURE`, per tutta la finestra: Android non sa
 *   fare di meno). Il segno si toglie quando l'ultimo valore scoperto della finestra si ricopre, a pagina in primo piano.
 * - **Coperto, il valore non c'è**: non si disegna (il velo è un segnaposto o la sua sfocatura) e TalkBack non lo legge
 *   («Valore nascosto», con l'azione «Mostra»).
 * - **Lo scambio** entra sfocato (R7): il velo esce e il valore entra a fuoco in 300 ms. Con «Riduci movimento» cambia e
 *   basta.
 *
 * @param discreetOnly falso (di serie): coperto finché non lo scopri (una chiave). Vero: coperto solo con la modalità
 *   discreta ([LocalFluidDiscreetMode]) accesa (un voto).
 * @param recoverAfter null: non si ricopre a tempo (solo in secondo piano).
 */
@Composable
fun Modifier.fluidPrivate(
  state: FluidPrivateState,
  discreetOnly: Boolean = false,
  veil: FluidPrivateVeil = FluidPrivateVeil.Placeholder,
  recoverAfter: Duration? = FluidPrivateDefaults.RecoverAfter,
  protectWindow: Boolean = true,
  blockScreenshots: Boolean = false,
  gate: FluidPrivateGate? = LocalFluidPrivateGate.current,
  labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current,
): Modifier {
  val discreet = LocalFluidDiscreetMode.current
  val covered = if (discreetOnly) discreet && !state.isRevealed else !state.isRevealed
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val scope = rememberCoroutineScope()
  val currentGate by rememberUpdatedState(gate)
  val density = LocalDensity.current
  val scheme = MaterialTheme.colorScheme
  val bar = scheme.onSurface.copy(alpha = fluidQuietFillAlpha(GlassDefaults.isDarkSurface()) + 0.04f)
  val ink = scheme.onSurfaceVariant
  val eye = rememberVectorPainter(FluidIcons.VisibilityOff)
  val body = MaterialTheme.typography.bodyLarge
  val lineHeightPx = with(density) {
    if (body.lineHeight.isSpecified) body.lineHeight.toPx() else if (body.fontSize.isSpecified) body.fontSize.toPx() * 1.35f else 22.dp.toPx()
  }
  val layerPaint = remember { Paint() }

  // 0: coperto, 1: scoperto. Si legge solo nel disegno.
  val shown = remember { Animatable(if (covered) 0f else 1f) }
  LaunchedEffect(covered, reduced) {
    val target = if (covered) 0f else 1f
    if (reduced) shown.snapTo(target) else shown.animateTo(target, tween(FluidPrivateDefaults.SwapMs))
  }

  // Si ricopre da solo.
  LaunchedEffect(state.isRevealed, recoverAfter) {
    if (state.isRevealed && recoverAfter != null) {
      delay(recoverAfter)
      state.cover()
    }
  }
  // In secondo piano si ricopre; il segno della finestra resta finché la pagina non torna in primo piano.
  var background by remember { mutableStateOf(false) }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, state) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> if (state.isRevealed) {
          background = true
          state.cover()
        }
        Lifecycle.Event.ON_START -> background = false
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      state.cover()
    }
  }
  // Si protegge la finestra solo mentre il valore è scoperto **ed è riservato** adesso (un voto a modalità discreta spenta
  // non è un segreto), e dopo il secondo piano finché la pagina non torna: l'istantanea delle recenti si prende uscendo.
  val revealedPrivately = state.isRevealed && (!discreetOnly || discreet)
  val protecting = protectWindow && (revealedPrivately || background)
  FluidSecureWindowHold(active = protecting, blockScreenshots = blockScreenshots)

  val requestReveal: () -> Unit = {
    scope.launch {
      val allowed = currentGate?.authorize() ?: true
      if (allowed) state.reveal()
    }
  }
  val semantics = if (covered) {
    Modifier.clearAndSetSemantics {
      contentDescription = "${labels.hidden}, ${labels.tapToReveal}"
      onClick(label = labels.reveal) { requestReveal(); true }
    }
  } else if (!discreetOnly || discreet) {
    Modifier.semantics {
      customActions = listOf(CustomAccessibilityAction(labels.hide) { state.cover(); true })
    }
  } else Modifier
  val blurVeil = veil.blur && Build.VERSION.SDK_INT >= 31
  return this
    .then(semantics)
    .then(if (covered) Modifier.pointerInput(Unit) { detectTapGestures { requestReveal() } } else Modifier)
    .graphicsLayer {
      // La sfocatura dello scambio: zero ai due capi, al colmo a metà. Il velo sfocato tiene la sua sfocatura da coperto.
      val p = shown.value
      val swap = 4f * p * (1f - p) * FluidPrivateDefaults.SwapBlur.toPx()
      val rest = if (blurVeil) (1f - p) * FluidPrivateDefaults.VeilBlur.toPx() else 0f
      val radius = swap + rest
      renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(radius, radius, TileMode.Decal) else null
    }
    .drawWithContent {
      val p = shown.value
      // Il valore si disegna solo mentre entra o quando è scoperto; col velo sfocato anche sotto il velo (sfocato).
      if (p > 0f || blurVeil) {
        if (p >= 1f || blurVeil) drawContent() else drawIntoCanvas { canvas ->
          layerPaint.alpha = p
          canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), layerPaint)
          drawContent()
          canvas.restore()
        }
      }
      if (p < 1f) {
        val veilAlpha = 1f - p
        if (blurVeil) {
          drawRect(scheme.surface.copy(alpha = 0.35f * veilAlpha))
        } else {
          // Le barre del segnaposto, una per riga, l'ultima più corta.
          val lines = (size.height / lineHeightPx).toInt().coerceAtLeast(1)
          val barHeight = (lineHeightPx * 0.62f).coerceAtMost(size.height)
          val step = size.height / lines
          for (i in 0 until lines) {
            val w = if (lines > 1 && i == lines - 1) size.width * 0.6f else size.width
            val y = step * i + (step - barHeight) / 2f
            drawRoundRect(bar.copy(alpha = bar.alpha * veilAlpha), Offset(0f, y), Size(w, barHeight), CornerRadius(barHeight / 2f))
          }
        }
        val glyph = FluidPrivateDefaults.Glyph.toPx()
        if (size.width >= FluidPrivateDefaults.GlyphMinWidth.toPx() && size.height >= glyph) {
          val x = if (blurVeil) (size.width - glyph) / 2f else size.width - glyph - (lineHeightPx * 0.62f - glyph).coerceAtLeast(4f) / 2f
          val y = (size.height - glyph) / 2f
          translate(x, y) {
            with(eye) { draw(Size(glyph, glyph), alpha = veilAlpha, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink)) }
          }
        }
      }
    }
}

// ================================================================================================================
// La finestra fuori dalle recenti
// ================================================================================================================

/**
 * Il segno «finestra protetta» di una finestra, contato: lo chiedono i valori riservati scoperti, e si toglie quando
 * l'ultimo lo lascia. Ricorda com'era la finestra prima (un'app che aveva già `FLAG_SECURE` lo tiene).
 */
internal object FluidSecureWindow {
  private class Hold(val window: Window, val hadSecure: Boolean) {
    var recents = 0
    var secure = 0
  }

  private val holds = HashMap<Window, Hold>()

  /** Quante richieste ha [window] (per i test). */
  internal fun holdsOf(window: Window): Int = holds[window]?.let { it.recents + it.secure } ?: 0

  fun acquire(activity: Activity, blockScreenshots: Boolean) {
    val window = activity.window ?: return
    val hold = holds.getOrPut(window) {
      Hold(window, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
    val useSecure = blockScreenshots || Build.VERSION.SDK_INT < 33
    if (useSecure) {
      if (hold.secure++ == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      if (hold.recents++ == 0 && Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false)
    }
  }

  fun release(activity: Activity, blockScreenshots: Boolean) {
    val window = activity.window ?: return
    val hold = holds[window] ?: return
    val useSecure = blockScreenshots || Build.VERSION.SDK_INT < 33
    if (useSecure && hold.secure > 0) {
      if (--hold.secure == 0 && !hold.hadSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else if (!useSecure && hold.recents > 0) {
      if (--hold.recents == 0 && Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(true)
    }
    if (hold.recents == 0 && hold.secure == 0) holds.remove(window)
  }
}

/** Tiene il segno della finestra finché [active]. */
@Composable
internal fun FluidSecureWindowHold(active: Boolean, blockScreenshots: Boolean) {
  val activity = LocalContext.current.fluidActivity() ?: return
  DisposableEffect(activity, active, blockScreenshots) {
    if (active) FluidSecureWindow.acquire(activity, blockScreenshots)
    onDispose { if (active) FluidSecureWindow.release(activity, blockScreenshots) }
  }
}

private tailrec fun Context.fluidActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.fluidActivity()
  else -> null
}
