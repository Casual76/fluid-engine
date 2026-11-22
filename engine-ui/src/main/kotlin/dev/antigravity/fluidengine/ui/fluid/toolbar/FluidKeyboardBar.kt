package dev.antigravity.fluidengine.ui.fluid.toolbar

import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets as AndroidWindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsulePlacement
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchLabels
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidTokenSuggestion
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.SearchCounter
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidSearchCounterSpeech
import dev.antigravity.fluidengine.ui.fluid.icons.AttachFile
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowDown
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowUp
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.Redo
import dev.antigravity.fluidengine.ui.fluid.icons.Undo
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

internal data class FluidKeyboardDeclaration(
  val context: Any,
  val alwaysVisible: Boolean,
  val fieldFocused: Boolean?,
  val bar: FluidToolbarDeclaration,
)

internal class FluidKeyboardDock(val visible: Boolean, val insets: WindowInsets)

internal val LocalFluidKeyboardDock = staticCompositionLocalOf<FluidKeyboardDock?> { null }

private val KeyboardGap = FluidFloatingTabBarDefaults.Spacing
internal val FluidKeyboardBarHeight = FluidFloatingCapsuleDefaults.ActionSize + KeyboardGap
internal val FluidKeyboardSearchClearance = FluidKeyboardBarHeight + KeyboardGap

/**
 * Posa una barra contestuale al fondo del contenitore, sopra l'IME. Usare un [Box] che riempie la finestra,
 * accanto al contenuto. Gli inset già consumati da un genitore (per esempio con `imePadding`) sono rispettati.
 * I comandi possono stare direttamente in [toolbar] oppure in `keyboard(context) { … }`. Con la tastiera fisica,
 * [fieldFocused] può seguire il campo dell'app; quando è null la barra usa la sessione di input della finestra.
 */
@Composable
fun FluidKeyboardBar(
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  context: Any = Unit,
  alwaysVisible: Boolean = false,
  fieldFocused: Boolean? = null,
  toolbar: FluidToolbarScope.() -> Unit,
) {
  val declaration = buildFluidToolbar(null, toolbar)
  val keyboard = declaration.keyboard ?: FluidKeyboardDeclaration(context, alwaysVisible, fieldFocused, declaration)
  if (keyboard.bar.buttons.isEmpty()) return
  val dock = rememberFluidKeyboardDock(keyboard, ownerFocused = null)
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    FluidKeyboardBarContent(keyboard, dock, backdrop)
  }
}

/**
 * Preset a capsula per una fila di controlli composabili. Conserva la posizione, gli ingressi e il bordo dello
 * scorrimento delle capsule esistenti, usando la stessa quota IME della barra dichiarata. Allineare il modificatore
 * in basso nel contenitore. [visible] è controllato dall'editor, per esempio dalla selezione o da un tasto «Aa».
 */
@Composable
fun FluidKeyboardBar(
  visible: Boolean,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.ReplacingTabBar,
  avoidKeyboard: Boolean = true,
  label: String? = null,
  content: @Composable RowScope.() -> Unit,
) {
  val keyboardInsets = if (avoidKeyboard) Modifier.windowInsetsPadding(rememberKeyboardImeInsets()) else Modifier
  FluidFloatingCapsule(
    modifier = modifier.then(keyboardInsets),
    backdrop = backdrop,
    visible = visible,
    placement = placement,
    fillWidth = true,
    label = label,
    content = content,
  )
}

@Composable
internal fun FluidKeyboardBarContent(
  keyboard: FluidKeyboardDeclaration,
  dock: FluidKeyboardDock,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
) {
  if (!dock.visible) return
  CompositionLocalProvider(
    LocalGlassBackdrop provides backdrop,
    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    LocalFluidCanvasBackdrop provides null,
    LocalFluidGlassQuality provides null,
    LocalFluidToolbarMinimize provides null,
  ) {
    FluidToolbarBar(
      declaration = keyboard.bar,
      modifier = modifier.navigationBarsPadding().windowInsetsPadding(dock.insets).padding(bottom = KeyboardGap),
      bar = FluidToolbarBar.Keyboard,
      keyboardContext = keyboard.context,
    )
  }
}

/** Velo sul lato dell'IME, che vive in un'altra finestra. Opacità scelta a occhio il 21 novembre 2026. */
internal fun Modifier.fluidKeyboardVeil(surface: Color): Modifier = background(
  Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.38f))),
  FluidCapsuleShape,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberFluidKeyboardDock(
  keyboard: FluidKeyboardDeclaration,
  ownerFocused: Boolean?,
): FluidKeyboardDock {
  val ime = rememberKeyboardImeInsets()
  val density = LocalDensity.current
  val configured = LocalConfiguration.current
  val hardware = configured.keyboard == Configuration.KEYBOARD_QWERTY &&
    configured.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
  val softwareVisible = WindowInsets.isImeVisible || ime.hasDockedKeyboard(density)
  val editorHasInput = if (keyboard.fieldFocused == null && hardware && !softwareVisible) {
    rememberKeyboardInputActive()
  } else softwareVisible
  val fieldFocused = keyboard.fieldFocused ?: (ownerFocused != false && editorHasInput)
  val visible = fluidKeyboardBarVisible(
    keyboard.alwaysVisible, LocalWindowInfo.current.isWindowFocused, fieldFocused, softwareVisible, hardware,
  )
  val detached = if (softwareVisible) rememberKeyboardDetached() else false
  return remember(visible, ime, detached) {
    FluidKeyboardDock(visible, if (detached) WindowInsets(0, 0, 0, 0) else ime)
  }
}

/** Il back dell'IME aggiorna gli inset: la barra esce con loro e il back della pagina resta disponibile. */
internal fun fluidKeyboardBarVisible(
  alwaysVisible: Boolean, windowFocused: Boolean, fieldFocused: Boolean, softwareVisible: Boolean, hardware: Boolean,
): Boolean = alwaysVisible || windowFocused && fieldFocused && (softwareVisible || hardware)

/** Con quota zero l'ancoraggio è già al fondo. Dai rettangoli pubblicati da API 35 si riconosce anche un IME diviso. */
@Composable
private fun rememberKeyboardDetached(): Boolean {
  if (Build.VERSION.SDK_INT < 35) return false
  val view = LocalView.current
  var detached by remember(view) { mutableStateOf(false) }
  DisposableEffect(view) {
    fun update() { detached = keyboardDoesNotSpanWindow(view) }
    update()
    val observer = view.viewTreeObserver
    val listener = ViewTreeObserver.OnGlobalLayoutListener { update() }
    observer.addOnGlobalLayoutListener(listener)
    onDispose { if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener) }
  }
  return detached
}

@androidx.annotation.RequiresApi(35)
private fun keyboardDoesNotSpanWindow(view: View): Boolean {
  val insets = view.rootWindowInsets ?: return false
  val ime = insets.getBoundingRects(AndroidWindowInsets.Type.ime())
  if (ime.isEmpty()) return false
  val navigation = insets.getInsets(AndroidWindowInsets.Type.navigationBars())
  val right = insets.frame.width - navigation.right
  return ime.none { it.left <= navigation.left && it.right >= right }
}

@Composable
private fun rememberKeyboardInputActive(): Boolean {
  val view = LocalView.current
  var active by remember(view) { mutableStateOf(false) }
  DisposableEffect(view) {
    val input = view.context.getSystemService(InputMethodManager::class.java)
    fun update() { active = input?.isActive(view) == true }
    update()
    val observer = view.viewTreeObserver
    val listener = ViewTreeObserver.OnPreDrawListener { update(); true }
    observer.addOnPreDrawListener(listener)
    onDispose { if (observer.isAlive) observer.removeOnPreDrawListener(listener) }
  }
  return active
}

internal data class KeyboardImeSample(val bottom: Int, val source: Int, val target: Int) {
  val hasPlatformMotion: Boolean get() = source != target
  val hasIntermediateHeight: Boolean get() = hasPlatformMotion && bottom != source && bottom != target

  /** I campioni veri vanno direttamente alla misura. Il ripiego vede i salti senza una corsa di piattaforma. */
  fun layoutBottom(followingPlatform: Boolean, reduced: Boolean, springBottom: Int): Int = when {
    reduced || followingPlatform || hasIntermediateHeight -> bottom
    hasPlatformMotion && bottom == source -> bottom
    else -> springBottom.coerceAtLeast(0)
  }
}

private class KeyboardImeInsets(
  private val current: WindowInsets,
  private val source: WindowInsets,
  private val target: WindowInsets,
  private val reduced: Boolean,
  density: Density,
) : WindowInsets by current {
  var followingPlatform by mutableStateOf(false)
  val fallback = Animatable(current.getBottom(density).toFloat())

  fun sample(density: Density) = KeyboardImeSample(
    current.getBottom(density), source.getBottom(density), target.getBottom(density),
  )

  fun hasDockedKeyboard(density: Density): Boolean =
    current.getBottom(density) > 0 || target.getBottom(density) > 0

  override fun getBottom(density: Density): Int =
    sample(density).layoutBottom(followingPlatform, reduced, fallback.value.roundToInt())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberKeyboardImeInsets(): KeyboardImeInsets {
  val density = LocalDensity.current
  val current = WindowInsets.ime
  val source = WindowInsets.imeAnimationSource
  val target = WindowInsets.imeAnimationTarget
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val insets = remember(current, source, target, density, reduced) {
    KeyboardImeInsets(current, source, target, reduced, density)
  }
  // Il fallback usa la molla standard di F4B.2. Nessun passaggio per la molla durante una corsa vera dell'IME.
  LaunchedEffect(insets, density) {
    snapshotFlow { insets.sample(density) }.collectLatest { sample ->
      when {
        sample.hasIntermediateHeight -> insets.followingPlatform = true
        sample.hasPlatformMotion && insets.followingPlatform -> Unit
        insets.followingPlatform || reduced -> {
          insets.fallback.snapTo(sample.bottom.toFloat())
          insets.followingPlatform = false
        }
        else -> insets.fallback.animateTo(sample.bottom.toFloat(), FluidMotion.standard())
      }
    }
  }
  return insets
}

private enum class KeyboardPresetId {
  Previous, Next, Done, Attachment, Photo, Dictation, Undo, Redo, FindCounter, FindPrevious, FindNext, WritingTools,
}
private data class KeyboardSuggestionId(val id: String)

/** Un passo fra i campi; Fatto toglie il focus e chiude l'IME. Le etichette sono sostituibili dall'app. */
fun FluidToolbarScope.fieldNavigation(
  focusManager: FocusManager,
  keyboardController: SoftwareKeyboardController? = null,
  hasPrevious: Boolean = true,
  hasNext: Boolean = true,
  previousLabel: String = "Campo precedente",
  nextLabel: String = "Campo successivo",
  doneLabel: String = "Fatto",
) {
  button(KeyboardPresetId.Previous, FluidIcons.KeyboardArrowUp, previousLabel,
    { focusManager.moveFocus(FocusDirection.Previous) }, enabled = hasPrevious)
  button(KeyboardPresetId.Next, FluidIcons.KeyboardArrowDown, nextLabel,
    { focusManager.moveFocus(FocusDirection.Next) }, enabled = hasNext)
  flexibleSpacer()
  button(KeyboardPresetId.Done, FluidIcons.Check, doneLabel, {
    focusManager.clearFocus()
    keyboardController?.hide()
  }, priority = FluidToolbarPriority.High)
}

/** Azioni offerte dal campo; un callback null lascia assente il rispettivo comando. */
fun FluidToolbarScope.quickActions(
  onAttach: (() -> Unit)? = null,
  onPhoto: (() -> Unit)? = null,
  onDictate: (() -> Unit)? = null,
  attachmentLabel: String = "Allega",
  photoLabel: String = "Foto",
  dictationLabel: String = "Dettatura",
  onWritingTools: (() -> Unit)? = null,
  writingToolsLabel: String = "Strumenti di scrittura",
) {
  if (onAttach != null) button(KeyboardPresetId.Attachment, FluidIcons.AttachFile, attachmentLabel, onAttach)
  if (onPhoto != null) button(KeyboardPresetId.Photo, FluidIcons.PhotoLibrary, photoLabel, onPhoto)
  if (onDictate != null) button(KeyboardPresetId.Dictation, FluidIcons.Mic, dictationLabel, onDictate)
  if (onWritingTools != null) button(KeyboardPresetId.WritingTools, FluidIcons.AutoAwesome, writingToolsLabel, onWritingTools)
}

/** Suggerimenti di parole o destinatari. Ogni scelta richiama l'app senza spostare il focus del campo. */
fun FluidToolbarScope.suggestions(
  suggestions: List<FluidTokenSuggestion>,
  onPick: (FluidTokenSuggestion) -> Unit,
) {
  for (suggestion in suggestions) {
    entries += FluidToolbarButtonEntry(
      id = KeyboardSuggestionId(suggestion.id),
      role = FluidToolbarRole.Action,
      placement = FluidToolbarPlacement.Automatic,
      priority = FluidToolbarPriority.Low,
      text = suggestion.title,
      label = listOfNotNull(suggestion.title, suggestion.subtitle).joinToString(", "),
      onClick = { onPick(suggestion) },
    )
  }
}

/** Cronologia dell'editor. I due comandi restano accessibili anche quando sono spenti. */
fun FluidToolbarScope.history(
  canUndo: Boolean,
  canRedo: Boolean,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  undoLabel: String = "Annulla",
  redoLabel: String = "Ripeti",
) {
  button(KeyboardPresetId.Undo, FluidIcons.Undo, undoLabel, onUndo, enabled = canUndo)
  button(KeyboardPresetId.Redo, FluidIcons.Redo, redoLabel, onRedo, enabled = canRedo)
}

/** «Trova» accanto alle frecce, con lo stesso contatore e le parole di F6B.6. */
fun FluidToolbarScope.findInText(
  query: String,
  navigation: FluidSearchNavigation,
  labels: FluidSearchLabels = FluidSearchLabels(),
) {
  val speech = fluidSearchCounterSpeech(query, navigation, labels)
  if (speech != null) {
    entries += FluidToolbarButtonEntry(
      id = KeyboardPresetId.FindCounter,
      role = FluidToolbarRole.Text,
      placement = FluidToolbarPlacement.Automatic,
      priority = FluidToolbarPriority.High,
      text = speech.text,
      label = speech.spoken,
      onClick = null,
      type = KeyboardFindCounter(query, navigation, labels),
    )
  }
  button(KeyboardPresetId.FindPrevious, FluidIcons.KeyboardArrowUp, labels.previous, navigation.onPrevious,
    priority = FluidToolbarPriority.High, enabled = navigation.total > 0)
  button(KeyboardPresetId.FindNext, FluidIcons.KeyboardArrowDown, labels.next, navigation.onNext,
    priority = FluidToolbarPriority.High, enabled = navigation.total > 0)
}

private class KeyboardFindCounter(
  private val query: String,
  private val navigation: FluidSearchNavigation,
  private val labels: FluidSearchLabels,
) : FluidToolbarElementType() {
  private data class Kind(override val state: String?) : FluidToolbarKind {
    override val type = FluidToolbarKinds.KeyboardFindCounter
    override val identity = KeyboardPresetId.FindCounter
  }

  private val speech = fluidSearchCounterSpeech(query, navigation, labels)
  override val kind: FluidToolbarKind = Kind(speech?.text)
  override val acceptsTap: Boolean get() = false

  @Composable
  override fun rememberSlot(live: Boolean): FluidToolbarTypeSlot = object : FluidToolbarTypeSlot {
    @Composable
    override fun Face(color: Color, iconSize: Dp, onTint: Boolean) {
      SearchCounter(query, navigation, labels, color)
    }

    override fun describe(receiver: SemanticsPropertyReceiver, name: String?) {
      val words = speech ?: return
      receiver.contentDescription = words.spoken
      receiver.liveRegion = words.liveRegion
    }
  }
}
