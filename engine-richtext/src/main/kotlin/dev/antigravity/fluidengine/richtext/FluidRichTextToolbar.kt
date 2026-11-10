package dev.antigravity.fluidengine.richtext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsulePlacement
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidPressGrip
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop

/**
 * La barra di formattazione sopra la tastiera: «Aa», grassetto, corsivo, sottolineato, elenco puntato,
 * elenco numerato, citazione, riduci e aumenta rientro, link, e annulla/ripeti.
 *
 * **Compare quando serve e non occupa spazio altrimenti.** E' una [FluidFloatingCapsule] con
 * `avoidKeyboard`: galleggia sopra la pagina (che non le lascia uno spazio suo) e sale con la tastiera.
 * E' in scena solo con l'editor che ha il fuoco e del testo selezionato, o dopo un tocco su «Aa"
 * ([FluidRichTextState.isFormattingBarVisible]); entra e esce col movimento della capsula, che con il
 * movimento ridotto e' una dissolvenza.
 *
 * **Non copre la riga che si scrive.** La pagina non le lascia uno spazio suo, quindi la barra sta sopra
 * il fondo della pagina, dove il campo porta il cursore. Finche' e' in scena dice allo stato dove sta, e
 * [FluidRichTextEditor] porta la riga del cursore (o il capo della selezione che si muove) **sopra di
 * lei**: scrivendo, andando a capo, spostando il cursore, quando la barra compare e quando la tastiera sale.
 * Non c'e' niente da collegare: basta lo stesso [state]. Serve che la pagina scorra e che finisca sopra la
 * tastiera (vedi [FluidRichTextEditor]).
 *
 * **I tasti dicono cosa e' acceso al cursore.** Ogni stile e' un interruttore (`FluidCapsuleAction(selected
 * = …)`: ruolo `Switch` per TalkBack, disco dell'accento quando e' acceso, non solo un colore), letto da
 * [FluidRichTextState.format]; riduci/aumenta rientro, annulla e ripeti sono tasti semplici, spenti
 * quando non c'e' niente da fare (il rientro si cambia solo su voci e citazioni:
 * [FluidRichTextFormat.canIncreaseIndent], [FluidRichTextFormat.canDecreaseIndent]). Toccare un tasto
 * non toglie il fuoco all'editor: la tastiera resta e la selezione pure.
 *
 * **Il link** apre un foglio con il testo e l'indirizzo (sicuro: `javascript:` e simili vengono
 * rifiutati), e dentro un link esistente permette di cambiarlo o toglierlo. Il foglio e' un portale: serve
 * un `FluidGlassModalHost` alla radice, come per ogni foglio dell'engine.
 *
 * Su un telefono stretto, o col testo al 200%, i tasti scorrono di lato dentro la capsula: sono sempre
 * da 48 dp. I tasti cedono al gesto di scorrimento (`FluidPressGrip.YieldsToScroll`): un trascinamento
 * che parte sopra un tasto fa scorrere la fila e non lo preme, un tocco semplice lo preme.
 *
 * **Dove si posa.** Nell'overlay della pagina (`FluidScreen.overlay`, o un `Box` sopra il contenuto),
 * allineata in basso: `Modifier.align(Alignment.BottomCenter)`. Di serie prende i margini della barra
 * delle schede ([FluidFloatingCapsulePlacement.ReplacingTabBar]): con la tastiera aperta la barra delle
 * schede non si vede, e la capsula sta nel suo posto.
 *
 * @param showHistory mostra annulla e ripeti in coda.
 * @param avoidKeyboard sale con la tastiera (di serie). Falso solo se la barra e' posata dentro un
 *   riquadro che non arriva al fondo dello schermo (un'anteprima, una card): li' il margine della
 *   tastiera la spingerebbe fuori.
 */
@Composable
fun FluidRichTextToolbar(
  state: FluidRichTextState,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.ReplacingTabBar,
  showHistory: Boolean = true,
  avoidKeyboard: Boolean = true,
) {
  val labels = LocalFluidRichTextLabels.current
  // Dove sta la barra lo sa solo lei: lo dice allo stato, e l'editor porta la riga del cursore sopra di
  // lei. Si misura il contenitore dell'animazione (il suo bordo alto e' quello della capsula a riposo,
  // anche mentre la capsula sta ancora salendo), non la capsula che si muove.
  DisposableEffect(state) {
    onDispose { state.formattingBarTop = Float.NaN }
  }
  FluidFloatingCapsule(
    modifier = modifier.onGloballyPositioned { state.formattingBarTop = it.positionInRoot().y },
    backdrop = backdrop,
    visible = state.isFormattingBarVisible,
    placement = placement,
    fillWidth = true,
    avoidKeyboard = avoidKeyboard,
    label = labels.formattingBar,
  ) {
    Row(
      modifier = Modifier
        .weight(1f)
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(FluidFloatingCapsuleDefaults.Spacing),
    ) {
      val format = state.format
      FluidCapsuleAction(
        icon = Icons.Rounded.TextFormat,
        contentDescription = labels.formatting,
        onClick = { state.isFormattingRequested = !state.isFormattingRequested },
        selected = state.isFormattingRequested,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.Rounded.FormatBold,
        contentDescription = labels.bold,
        onClick = { state.toggle(FluidRichTextInlineStyle.Bold) },
        selected = format.bold,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.Rounded.FormatItalic,
        contentDescription = labels.italic,
        onClick = { state.toggle(FluidRichTextInlineStyle.Italic) },
        selected = format.italic,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.Rounded.FormatUnderlined,
        contentDescription = labels.underline,
        onClick = { state.toggle(FluidRichTextInlineStyle.Underline) },
        selected = format.underline,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
        contentDescription = labels.bulletList,
        onClick = { state.toggleList(FluidRichTextListKind.Bullet) },
        selected = format.list == FluidRichTextListKind.Bullet,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.Rounded.FormatListNumbered,
        contentDescription = labels.numberedList,
        onClick = { state.toggleList(FluidRichTextListKind.Numbered) },
        selected = format.list == FluidRichTextListKind.Numbered,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.Rounded.FormatQuote,
        contentDescription = labels.quote,
        onClick = { state.toggleQuote() },
        selected = format.quote,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.AutoMirrored.Rounded.FormatIndentDecrease,
        contentDescription = labels.decreaseIndent,
        onClick = { state.decreaseIndent() },
        enabled = format.canDecreaseIndent,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.AutoMirrored.Rounded.FormatIndentIncrease,
        contentDescription = labels.increaseIndent,
        onClick = { state.increaseIndent() },
        enabled = format.canIncreaseIndent,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidCapsuleAction(
        icon = Icons.Rounded.Link,
        contentDescription = labels.link,
        onClick = { state.openLinkEditor() },
        selected = format.link != null,
        grip = FluidPressGrip.YieldsToScroll,
      )
      if (showHistory) {
        FluidCapsuleAction(
          icon = Icons.AutoMirrored.Rounded.Undo,
          contentDescription = labels.undo,
          onClick = { state.undo() },
          enabled = state.canUndo,
          grip = FluidPressGrip.YieldsToScroll,
        )
        FluidCapsuleAction(
          icon = Icons.AutoMirrored.Rounded.Redo,
          contentDescription = labels.redo,
          onClick = { state.redo() },
          enabled = state.canRedo,
          grip = FluidPressGrip.YieldsToScroll,
        )
      }
    }
  }
  FluidRichTextLinkSheet(state)
}

/**
 * Il tasto «Aa» che chiede la barra di formattazione anche senza selezione: un interruttore (acceso
 * quando la barra e' chiesta), piatto come le azioni della capsula, con il bersaglio da 48 dp. Se
 * l'editor non ha il fuoco glielo da', cosi' compaiono insieme la tastiera e la barra.
 *
 * L'app lo mette dove ha senso per lei: nella barra della pagina, accanto agli allegati, in fondo al
 * campo.
 */
@Composable
fun FluidRichTextFormatButton(
  state: FluidRichTextState,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val labels = LocalFluidRichTextLabels.current
  FluidCapsuleAction(
    icon = Icons.Rounded.TextFormat,
    contentDescription = labels.formatting,
    onClick = {
      val request = !state.isFormattingRequested
      state.isFormattingRequested = request
      if (request && !state.isFocused) state.requestFocus()
    },
    modifier = modifier,
    enabled = enabled,
    selected = state.isFormattingRequested,
  )
}

/** Il foglio del link: testo e indirizzo, «Fatto», «Togli il link» se ce n'e' gia' uno. */
@Composable
private fun FluidRichTextLinkSheet(state: FluidRichTextState) {
  val request = state.linkRequest
  val labels = LocalFluidRichTextLabels.current
  val dismiss = {
    state.linkRequest = null
    state.requestFocus()
  }
  FluidGlassModalPortal(
    visible = request != null,
    onDismissRequest = dismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = labels.linkTitle,
  ) {
    if (request == null) return@FluidGlassModalPortal
    var text by remember(request) { mutableStateOf(request.text) }
    // Un indirizzo di posta o un numero si mostrano come li si scrive; un indirizzo web intero.
    var address by remember(request) {
      mutableStateOf(request.url.let { if (it.startsWith("http")) it else FluidRichTextLinks.displayText(it) })
    }
    val safe = FluidRichTextLinks.fromUserInput(address)
    val showError = address.isNotBlank() && safe == null
    val focus = remember { FocusRequester() }
    LaunchedEffect(request) { runCatching { focus.requestFocus() } }
    val confirm = {
      if (safe != null) {
        state.applyLink(request.range, safe, if (request.canEditText) text else null)
        state.linkRequest = null
        state.requestFocus()
      }
    }
    FluidSheetScaffold(
      title = labels.linkTitle,
      actions = {
        FluidButton(
          text = labels.linkConfirm,
          onClick = confirm,
          size = FluidButtonSize.Large,
          enabled = safe != null,
          fillWidth = true,
        )
        if (request.url.isNotEmpty()) {
          FluidButton(
            text = labels.linkRemove,
            onClick = {
              state.select(request.range)
              state.removeLink()
              state.linkRequest = null
              state.requestFocus()
            },
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Large,
            fillWidth = true,
          )
        }
        FluidButton(
          text = labels.linkCancel,
          onClick = dismiss,
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Large,
          fillWidth = true,
        )
      },
    ) {
      if (request.canEditText) {
        FluidTextField(
          value = text,
          onValueChange = { text = it },
          label = labels.linkText,
          modifier = Modifier.padding(bottom = 12.dp),
        )
      }
      FluidTextField(
        value = address,
        onValueChange = { address = it },
        label = labels.linkAddress,
        placeholder = labels.linkAddressPlaceholder,
        isError = showError,
        supportingText = if (showError) labels.linkInvalid else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { confirm() }),
        modifier = Modifier.focusRequester(focus),
      )
    }
  }
}
