package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.editor.icons.AspectRatio
import dev.antigravity.fluidengine.editor.icons.Compare
import dev.antigravity.fluidengine.editor.icons.Crop
import dev.antigravity.fluidengine.editor.icons.CropLandscape
import dev.antigravity.fluidengine.editor.icons.CropPortrait
import dev.antigravity.fluidengine.editor.icons.Draw
import dev.antigravity.fluidengine.editor.icons.Flip
import dev.antigravity.fluidengine.editor.icons.Rotate90DegreesCcw
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfter
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfterLabels
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Redo
import dev.antigravity.fluidengine.ui.fluid.icons.Tune
import dev.antigravity.fluidengine.ui.fluid.icons.Undo
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

/**
 * Le misure della testata e della barra in basso, dalle schermate di Foto in iOS 26 e 27 («Ritaglia» e «Regola»,
 * `sessioni/f8-2/riferimenti`, 1,132 px per punto): «Annulla» e «Fine» in alto ai lati; sotto, le capsule di vetro da
 * 44 pt (a sinistra annulla e ripristina, o specchio e rotazione; a destra le proporzioni e «•••») col titolo della
 * scheda in grigio al centro; in basso le schede in una capsula di vetro, con un triangolo sopra quella scelta.
 */
internal object EditorChromeLook {
  val Side = 16.dp
  val Row = 44.dp
  val RowGap = 8.dp
  val CapsuleIcon = 22.dp
  val RulerHeight = 48.dp
  val ControlsGap = 10.dp
  val ModeBarHeight = 58.dp
  val ModeItemWidth = 76.dp
  val ModeMarker = 5.dp
  val LabelMargin = 12.dp
  /** L'etichetta sopra la foto («ESPOSIZIONE»): grigia traslucida, come in Foto. */
  const val LabelScrim = 0.42f
  /** Il titolo della scheda in alto («REGOLA») e le scritte spente. */
  const val TitleInk = 0.5f
  /** Nel visore dei file: il posto della capsula in basso del visore (28 dp dal fondo, alta 48, piu' 8 di respiro). */
  val ViewerBottomRoom = 84.dp
}

/**
 * L'**editor delle foto**, come «Modifica» di Foto in iOS 26 (idea `ritaglia-e-regola`, F8.15): una pagina intera con
 * - in alto «Annulla» e «Fine»; sotto, annulla e ripristina (o, in «Ritaglia», specchio e rotazione di 90°), il titolo
 *   della scheda, le proporzioni, «Annota» se l'app la offre ([onMarkup]) e «•••» (Ripristina originale, il confronto
 *   a tendina);
 * - la foto: in «Regola» il ritaglio, e **tenendola premuta l'originale**; in «Ritaglia» la cornice con le maniglie (le
 *   proporzioni si rispettano), la foto che si sposta e si pizzica sotto, la rotazione a due dita;
 * - in «Regola» la **ruota delle regolazioni** (prima «Auto») e il righello col neutro; in «Ritaglia» il righello di
 *   «Raddrizza» (±45°) o la fila delle proporzioni;
 * - in basso le schede «Regola» e «Ritaglia».
 *
 * **Il dito resta nitido**: la foto si ricalcola a ogni fotogramma mentre il righello gira (lo shader AGSL da Android 13,
 * altrimenti il calcolo ridotto sulla CPU, e quello pieno quando il dito si ferma); niente si sfoca sotto il dito. Si
 * sfoca solo cio' che cambia da se': l'etichetta sopra la foto quando cambia regolazione, le icone delle capsule quando
 * cambia scheda.
 *
 * **L'originale resta**: l'editor non scrive niente. [onDone] da' la ricetta ([FluidImageEdit]); l'app salva un file
 * nuovo con [FluidImageEditorState.export] e, se vuole riaprire la modifica, tiene la ricetta ([FluidImageEdit.encode]).
 * [onCancel] arriva dopo la conferma «Annulla modifiche» se c'era qualcosa da perdere.
 *
 * @param aspects le proporzioni offerte in «Ritaglia» (di serie quelle di Foto e il tondo).
 * @param onMarkup «Annota»: l'app salva la foto in PNG nella sua cache ([FluidImageEditorState.export] con
 *   [FluidImageExport.Format.Png]) e apre il disegno (`engine-ink`) su quell'URI: l'accordo con F8.16, un file PNG nella
 *   cache nei due versi. Null, niente tasto.
 */
@Composable
fun FluidImageEditor(
  state: FluidImageEditorState,
  onCancel: () -> Unit,
  onDone: (FluidImageEdit) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidImageEditorStrings = FluidImageEditorStrings(),
  aspects: List<FluidCropAspect> = FluidCropAspect.Presets,
  onMarkup: (() -> Unit)? = null,
) = EditorScaffold(state, onCancel, onDone, modifier, strings, aspects, onMarkup, embedded = false)

/**
 * Il corpo dell'editor. [embedded]: dentro il visore dei file, che ha gia' indietro, il titolo e «Fine» (la riga di
 * «Annulla» e «Fine» non c'e', e in basso resta il posto della capsula del visore).
 */
@Composable
internal fun EditorScaffold(
  state: FluidImageEditorState,
  onCancel: () -> Unit,
  onDone: (FluidImageEdit) -> Unit,
  modifier: Modifier,
  strings: FluidImageEditorStrings,
  aspects: List<FluidCropAspect>,
  onMarkup: (() -> Unit)?,
  embedded: Boolean,
) {
  val scheme = MaterialTheme.colorScheme
  val backdrop = rememberGlassBackdrop()
  val menu = rememberFluidMorphMenuState()
  val ns = rememberFluidGlassNamespace()
  var confirmDiscard by remember { mutableStateOf(false) }
  val cancel = {
    if (state.hasChanges) confirmDiscard = true else onCancel()
  }
  BackHandler(enabled = !embedded || state.aspectPanel || state.splitCompare) {
    when {
      state.aspectPanel -> state.aspectPanel = false
      state.splitCompare -> state.splitCompare = false
      else -> cancel()
    }
  }
  Box(modifier.fillMaxSize()) {
    // Il fondo e' la sola cosa che il vetro della testata rifrange: la foto che cambia a ogni fotogramma non e' una
    // sorgente del vetro (F7: niente sorgenti a schermo intero sempre registrate). F7.24: e ha uno strato suo. La sorgente
    // si riregistra (e i cinque vetri ricatturano) ogni volta che si riregistra lo strato in cui sta: senza, la foto che
    // cambia a ogni valore del righello la trascinava con se' a ogni fotogramma.
    Box(Modifier.fillMaxSize().graphicsLayer().background(scheme.background).glassBackdropSource(backdrop))
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
      Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (embedded) {
          Spacer(Modifier.height(EditorChromeLook.Row + EditorChromeLook.RowGap))
        } else {
          EditorTopBar(state, strings, onCancel = cancel, onDone = { onDone(state.edit) }, cancelSource = ns)
          Spacer(Modifier.height(EditorChromeLook.RowGap))
        }
        EditorSecondBar(state, strings, menu, backdrop, onMarkup)
        Box(Modifier.weight(1f).fillMaxWidth().testTag(FluidImageEditorTags.Photo)) {
          when {
            state.loadError != null -> Text(
              strings.loadFailed, Modifier.align(Alignment.Center),
              color = scheme.onBackground.copy(alpha = EditorChromeLook.TitleInk),
            )
            state.image == null -> Text(
              strings.loading, Modifier.align(Alignment.Center),
              color = scheme.onBackground.copy(alpha = EditorChromeLook.TitleInk),
            )
            state.splitCompare && state.mode == FluidEditorMode.Adjust -> EditorSplit(state, strings)
            else -> EditorPhoto(state, strings)
          }
          if (state.image != null && state.mode == FluidEditorMode.Adjust && !state.splitCompare) {
            EditorPhotoLabel(state, strings, Modifier.align(Alignment.BottomCenter).padding(bottom = EditorChromeLook.LabelMargin))
          }
        }
        Spacer(Modifier.height(EditorChromeLook.ControlsGap))
        Box(Modifier.fillMaxWidth().height(EditorStripLook.Item + EditorChromeLook.RulerHeight + EditorChromeLook.ControlsGap)) {
          if (state.mode == FluidEditorMode.Adjust) {
            Column {
              EditorAdjustStrip(state, strings)
              Spacer(Modifier.height(EditorChromeLook.ControlsGap))
              EditorAdjustRuler(state, strings)
            }
          } else if (state.aspectPanel) {
            EditorAspectPanel(state, strings, aspects, Modifier.align(Alignment.BottomCenter))
          } else {
            EditorStraighten(state, strings, Modifier.align(Alignment.BottomCenter))
          }
        }
        Spacer(Modifier.height(EditorChromeLook.ControlsGap))
        EditorModeBar(state, strings, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(if (embedded) EditorChromeLook.ViewerBottomRoom else EditorChromeLook.ControlsGap))
      }
    }
    FluidMorphMenuHost(state = menu, backdrop = backdrop)
    FluidConfirmationDialog(
      visible = confirmDiscard,
      onDismissRequest = { confirmDiscard = false },
      source = FluidTransitionSource(FluidImageEditorTags.Cancel, ns),
      title = strings.discardTitle,
      actions = listOf(FluidAlertAction(strings.discard, onCancel, FluidAlertAction.Emphasis.Destructive)),
      cancelLabel = strings.keepEditing,
    )
  }
}

@Composable
private fun EditorTopBar(
  state: FluidImageEditorState, strings: FluidImageEditorStrings, onCancel: () -> Unit, onDone: () -> Unit,
  cancelSource: dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = EditorChromeLook.Side).height(EditorChromeLook.Row),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidGlassButton(
      strings.cancel, onCancel,
      Modifier.testTag(FluidImageEditorTags.Cancel).fluidTransitionSource(FluidImageEditorTags.Cancel, cancelSource),
    )
    Spacer(Modifier.weight(1f))
    FluidGlassButton(
      strings.done, onDone, Modifier.testTag(FluidImageEditorTags.Done),
      enabled = state.isReady, style = FluidGlassButtonStyle.Prominent,
    )
  }
}

/** La seconda riga: le capsule di vetro e il titolo della scheda. Le icone che cambiano con la scheda si sfocano. */
@Composable
private fun EditorSecondBar(
  state: FluidImageEditorState, strings: FluidImageEditorStrings,
  menu: dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState,
  backdrop: GlassBackdropState, onMarkup: (() -> Unit)?,
) {
  val scheme = MaterialTheme.colorScheme
  val mode = state.mode
  Box(Modifier.fillMaxWidth().padding(horizontal = EditorChromeLook.Side).height(EditorChromeLook.Row)) {
    Row(Modifier.align(Alignment.CenterStart).fluidGlass(FluidGlass.Regular, FluidCapsuleShape)) {
      FluidGlassContentTransition(mode, key = { it.id }) { m ->
        Row {
          if (m == FluidEditorMode.Crop) {
            CapsuleIcon(FluidIcons.Flip, strings.flip, FluidImageEditorTags.Flip, enabled = state.isReady) { state.flip() }
            CapsuleIcon(FluidIcons.Rotate90DegreesCcw, strings.rotate, FluidImageEditorTags.Rotate, enabled = state.isReady) {
              state.rotateLeft()
            }
          } else {
            CapsuleIcon(FluidIcons.Undo, strings.undo, FluidImageEditorTags.Undo, enabled = state.canUndo) { state.undo() }
            CapsuleIcon(FluidIcons.Redo, strings.redo, FluidImageEditorTags.Redo, enabled = state.canRedo) { state.redo() }
          }
        }
      }
    }
    Box(Modifier.align(Alignment.Center)) {
      if (mode == FluidEditorMode.Crop) {
        // Si legge solo «e' cambiata?»: la riga non si ricompone a ogni fotogramma mentre il dito sposta la foto (F8.15b).
        val changed by remember(state) { derivedStateOf { !state.edit.geometry.isIdentity } }
        Text(
          strings.reset.uppercase(),
          color = if (changed) scheme.primary else Color.Transparent,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .testTag(FluidImageEditorTags.Reset)
            .fluidPressable(onClick = { state.resetGeometry() }, enabled = changed, role = Role.Button)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        )
      } else {
        Text(
          strings.adjust.uppercase(),
          color = scheme.onBackground.copy(alpha = EditorChromeLook.TitleInk),
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.semantics { heading() },
        )
      }
    }
    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
      if (onMarkup != null || mode == FluidEditorMode.Crop) {
        Row(Modifier.fluidGlass(FluidGlass.Regular, FluidCapsuleShape)) {
          if (mode == FluidEditorMode.Crop) {
            CapsuleIcon(FluidIcons.AspectRatio, strings.aspect, FluidImageEditorTags.Aspect, enabled = state.isReady,
              selected = state.aspectPanel) { state.aspectPanel = !state.aspectPanel }
          }
          if (onMarkup != null) CapsuleIcon(FluidIcons.Draw, strings.markup, "fluid-editor:annota", state.isReady) { onMarkup() }
        }
        Spacer(Modifier.width(8.dp))
      }
      FluidMorphMenuButton(
        state = menu,
        actions = {
          buildList {
            if (state.mode == FluidEditorMode.Adjust) {
              add(FluidContextAction(if (state.splitCompare) strings.splitCompareOff else strings.splitCompare, FluidIcons.Compare) {
                state.splitCompare = !state.splitCompare
              })
            }
            add(FluidContextAction(strings.revert, destructive = true, enabled = !state.edit.isOriginal, startsSection = true) {
              state.revertToOriginal()
            })
          }
        },
        onClick = {},
        icon = FluidIcons.MoreHoriz,
        backdrop = backdrop,
        opensOnTap = true,
        modifier = Modifier.testTag(FluidImageEditorTags.More),
      )
    }
  }
}

/** Un tasto dentro una capsula di vetro: 44 dp, l'icona a 22. */
@Composable
private fun CapsuleIcon(
  icon: ImageVector, label: String, tag: String, enabled: Boolean, selected: Boolean = false, onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Box(
    Modifier
      .size(EditorChromeLook.Row)
      .testTag(tag)
      .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button)
      .semantics {
        contentDescription = label
        this.selected = selected
      },
    contentAlignment = Alignment.Center,
  ) {
    FluidIcon(
      icon, null, size = EditorChromeLook.CapsuleIcon,
      tint = when {
        !enabled -> scheme.onBackground.copy(alpha = 0.3f)
        selected -> scheme.primary
        else -> scheme.onBackground
      },
    )
  }
}

/**
 * L'etichetta sopra la foto: il nome della regolazione al centro della ruota, o «ORIGINALE» mentre si tiene premuto.
 * Cambia da se' (la decide la ruota, non il dito sull'etichetta): entra sfocata.
 */
@Composable
private fun EditorPhotoLabel(state: FluidImageEditorState, strings: FluidImageEditorStrings, modifier: Modifier) {
  val text = when {
    state.showingOriginal -> strings.original
    state.selected == null -> strings.auto
    else -> strings.adjustmentName(state.selected!!)
  }.uppercase()
  Box(
    modifier
      .testTag(FluidImageEditorTags.Label)
      .background(Color.Black.copy(alpha = EditorChromeLook.LabelScrim), FluidCapsuleShape)
      .padding(horizontal = 10.dp, vertical = 4.dp),
  ) {
    FluidGlassContentTransition(text) { t ->
      Text(t, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
  }
}

/** Il righello di «Raddrizza»: ±45°, il neutro attira, la cornice resta dentro la foto. */
@Composable
private fun EditorStraighten(state: FluidImageEditorState, strings: FluidImageEditorStrings, modifier: Modifier) {
  // L'angolo come lambda (F8.15c): ne' spostare la foto ne' girare il righello ricompongono questa parte.
  FluidRulerDial(
    value = { state.edit.geometry.angle },
    onValueChange = { v ->
      if (!state.interacting) state.beginGesture()
      state.setStraighten(v)
    },
    valueRange = -45f..45f,
    step = 1f,
    majorEvery = 10,
    neutralValue = 0f,
    onValueChangeFinished = { state.endGesture() },
    valueText = { strings.degrees(it) },
    contentDescription = strings.straighten,
    modifier = modifier.testTag(FluidImageEditorTags.Straighten),
  )
}

/** La fila delle proporzioni, come in Foto: i due tasti verticale e orizzontale, poi i nomi; quello scelto in grigio. */
@Composable
private fun EditorAspectPanel(
  state: FluidImageEditorState, strings: FluidImageEditorStrings, aspects: List<FluidCropAspect>, modifier: Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  // Solo le proporzioni e le rotazioni di 90°: il resto della ricetta cambia a ogni fotogramma dei gesti (F8.15b).
  val g by remember(state) { derivedStateOf { state.edit.geometry.let { FluidCropGeometry(it.quarterTurns, aspect = it.aspect) } } }
  val size = state.sourceSize
  val ratio = size?.let { (w, h) -> g.orientedSize(w, h).let { (ow, oh) -> ow.toFloat() / oh } } ?: 1f
  val canTurn = !g.aspect.isFreeform && g.aspect.width != g.aspect.height
  val portrait = g.aspect.isPortrait(ratio)
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      CapsuleIcon(FluidIcons.CropPortrait, strings.portrait, FluidImageEditorTags.aspect("verticale"), canTurn, portrait) {
        state.setAspectPortrait(true)
      }
      CapsuleIcon(FluidIcons.CropLandscape, strings.landscape, FluidImageEditorTags.aspect("orizzontale"), canTurn, !portrait) {
        state.setAspectPortrait(false)
      }
    }
    Spacer(Modifier.height(8.dp))
    val list = rememberLazyListState()
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(6.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = EditorChromeLook.Side)) {
      items(aspects, key = { it.id }) { a ->
        val chosen = a.width == g.aspect.width && a.height == g.aspect.height && a.round == g.aspect.round ||
          (a.width > 0 && a.width == g.aspect.height && a.height == g.aspect.width && a.round == g.aspect.round)
        Text(
          strings.aspectName(a).uppercase(),
          color = if (chosen) scheme.onBackground else scheme.onBackground.copy(alpha = 0.55f),
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .testTag(FluidImageEditorTags.aspect(a.id))
            .background(if (chosen) scheme.onBackground.copy(alpha = 0.12f) else Color.Transparent, FluidCapsuleShape)
            .fluidPressable(onClick = { state.setAspect(if (!portrait == (a.width >= a.height) || a.width <= 0) a else a.rotated()) },
              role = Role.Button)
            .semantics { selected = chosen }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }
}

/** Le schede in basso: «Regola» e «Ritaglia», in una capsula di vetro; un triangolo nell'accento sopra quella scelta. */
@Composable
private fun EditorModeBar(state: FluidImageEditorState, strings: FluidImageEditorStrings, modifier: Modifier) {
  Row(
    modifier.height(EditorChromeLook.ModeBarHeight).fluidGlass(FluidGlass.Regular, FluidCapsuleShape)
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ModeItem(state, FluidEditorMode.Adjust, FluidIcons.Tune, strings.adjust, FluidImageEditorTags.ModeAdjust)
    ModeItem(state, FluidEditorMode.Crop, FluidIcons.Crop, strings.crop, FluidImageEditorTags.ModeCrop)
  }
}

@Composable
private fun ModeItem(state: FluidImageEditorState, mode: FluidEditorMode, icon: ImageVector, label: String, tag: String) {
  val scheme = MaterialTheme.colorScheme
  val chosen = state.mode == mode
  val ink = if (chosen) scheme.onBackground else scheme.onBackground.copy(alpha = 0.55f)
  val accent = scheme.primary
  Column(
    Modifier
      .width(EditorChromeLook.ModeItemWidth)
      .testTag(tag)
      .fluidPressable(onClick = {
        state.mode = mode
        state.aspectPanel = false
        if (mode == FluidEditorMode.Crop) state.splitCompare = false
      }, role = Role.Tab)
      .semantics(mergeDescendants = true) { selected = chosen }
      .drawBehind {
        if (chosen) {
          val s = EditorChromeLook.ModeMarker.toPx()
          val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width / 2 - s, 0f)
            lineTo(size.width / 2 + s, 0f)
            lineTo(size.width / 2, s)
            close()
          }
          drawPath(path, accent)
        }
      }
      .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    FluidIcon(icon, null, tint = ink, size = 22.dp)
    Text(label, color = ink, fontSize = 11.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
  }
}

/**
 * Il confronto a tendina dentro l'editor: la stessa cornice, a sinistra senza regolazioni e a destra con. La foto la
 * disegna [EditorFlatPhoto]; la tendina e' [FluidBeforeAfter] di engine-ui.
 */
@Composable
private fun EditorSplit(state: FluidImageEditorState, strings: FluidImageEditorStrings) {
  val img = state.image ?: return
  BoxWithConstraints(Modifier.fillMaxSize().testTag(FluidImageEditorTags.Split)) {
    val density = LocalDensity.current
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    // La geometria sola: il righello delle regolazioni non ricompone la tendina (F8.15b).
    val g by remember(state) { derivedStateOf { state.edit.geometry } }
    val (ow, oh) = g.orientedSize(img.width, img.height)
    val view = CropMath.fit(ScreenRect(0f, 0f, w, h), 0f, 0f, g, ow, oh)
    val frame = CropMath.frame(view, g, ow, oh)
    Box(
      Modifier
        .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
        .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() }),
    ) {
      FluidBeforeAfter(
        before = { EditorFlatPhoto(state, img, view, frame, adjusted = false) },
        after = { EditorFlatPhoto(state, img, view, frame, adjusted = true) },
        labels = FluidBeforeAfterLabels(before = strings.original, after = strings.edited),
      )
    }
  }
}

/** La foto ritagliata, con o senza regolazioni, nel riquadro della cornice (per la tendina). */
@Composable
private fun EditorFlatPhoto(state: FluidImageEditorState, img: EditorImage, view: CropView, frame: ScreenRect, adjusted: Boolean) {
  val shader = if (adjusted) rememberAdjustShader(img) else null
  val cpu = if (adjusted && shader == null) rememberCpuPreview(state, img) else null
  val holder = remember { FlatHolder() }
  Box(
    Modifier.fillMaxSize().drawBehind {
      val g = state.edit.geometry
      CropMath.matrix(view, g, g.sourceToOriented(img.width, img.height), holder.values)
      holder.matrix.setValues(holder.values)
      drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save()
        nc.translate(-frame.left, -frame.top)
        nc.concat(holder.matrix)
        if (shader != null) {
          shader.update(state.edit.adjustments, img.toOutputFraction(g), img.sharpRadius(g))
          shader.draw(nc)
        } else {
          val bitmap = cpu?.value
          if (bitmap != null && adjusted) {
            nc.scale(img.width.toFloat() / bitmap.width, img.height.toFloat() / bitmap.height)
            nc.drawBitmap(bitmap, 0f, 0f, holder.paint)
          } else {
            nc.drawBitmap(img.preview, 0f, 0f, holder.paint)
          }
        }
        nc.restore()
      }
    },
  )
}

private class FlatHolder {
  val values = FloatArray(9)
  val matrix = Matrix()
  val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
}

/** Lo shader dell'anteprima per [img], da Android 13 (null sotto, o se lo shader non si compila). */
@Composable
internal fun rememberAdjustShader(img: EditorImage): AdjustShader? = remember(img) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && EditorRendering.gpuAllowed) {
    runCatching { AdjustShader(img.preview, img.definitionBitmap) }.getOrNull()
  } else null
}

/**
 * L'anteprima della CPU per [img]: ridotta mentre il dito muove ([EditorRendering.CpuMovingScale]), piena quando si
 * ferma. Il calcolo e' fuori dal thread principale; un valore nuovo scarta quello vecchio a meta'.
 */
@Composable
internal fun rememberCpuPreview(state: FluidImageEditorState, img: EditorImage): androidx.compose.runtime.State<Bitmap?> {
  val out = remember(img) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(img) {
    snapshotFlow {
      val e = state.edit
      val needsGeometry = e.adjustments[FluidAdjustment.Vignette] != 0f || e.adjustments[FluidAdjustment.Sharpness] != 0f
      Triple(e.adjustments, if (needsGeometry) e.geometry else null, state.interacting)
    }.collectLatest { (adjustments, geometry, moving) ->
      out.value = if (adjustments.isNeutral) null
      else EditorCompute.renderPreview(img, adjustments, geometry ?: state.edit.geometry,
        if (moving) EditorRendering.CpuMovingScale else 1f)
    }
  }
  return out
}
