package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Visibility
import dev.antigravity.fluidengine.ui.fluid.icons.VisibilityOff

/**
 * A text field.
 *
 * Material's outlined field carries a floating label that animates into the border, an indicator
 * line, and a focus colour change — three moving parts announcing a box you can type in. iOS gives
 * it a quiet filled rect and lets the caret do the talking. The one piece of interaction it does add
 * is the clear button, which appears only while there is something to clear.
 *
 * Questa e' la forma a [String]: comoda per un campo che vuole solo il testo. Chi ha bisogno del
 * cursore — un editor con i pulsanti di formattazione, che devono scrivere dove si sta scrivendo e
 * non in fondo — usa l'overload a [TextFieldValue], di cui questo e' un guscio: la selezione vive
 * qui dentro e il chiamante continua a vedere una stringa.
 *
 * @param secret un campo che contiene un segreto (una password, una chiave, un token): il testo e'
 *   puntini, e un occhio a destra lo mostra e lo nasconde. Dalla 3.0. MyCloud si era scritto il
 *   suo "Mostra"/"Nascondi" sopra il campo perche' lo slot `trailing` era di 18 dp e un bersaglio
 *   cosi' piccolo non si colpiva; adesso lo slot e' di 24 dp, che e' l'altezza del contenuto di un
 *   campo da 44 (il campo non cresce), e il tocco ha comunque i 48 dp che Compose da' a ogni
 *   bersaglio piccolo. Un campo segreto parte nascosto a ogni apertura, non ricorda di essere stato
 *   mostrato, e non ha il tasto che svuota: l'occhio e' l'unica cosa a destra che si tocca. Di serie
 *   la tastiera e' quella delle password, senza correzione automatica ([keyboardOptions] la
 *   sostituisce se la passi). Per chi legge lo schermo il campo e' una password finche' ci sono i
 *   puntini, e lo resta anche dopo l'occhio: e' la libreria di testo a fissare la semantica quando
 *   il campo nasce nascosto, e un segreto mostrato resta un segreto. Se `trailing` c'e' sta prima
 *   dell'occhio.
 *
 * **Le misure di Apple (4.0, F6.6).** Il campo pieno di iOS 27 nel contenuto (quello della ricerca
 * sotto un titolo grande, sessione 278 di WWDC26, 11:15, misurato al pixel in 4K a 3,67 px/pt) e'
 * una **capsula alta 40 pt**, grigia di 18 livelli sotto la pagina bianca (il `tertiarySystemFill`
 * di UIKit: 118, 118, 128 al 12%), col testo da 17 pt, l'icona davanti a 13 pt dal bordo e il testo
 * a 37. Il campo a una riga ora e' quello: capsula, 40 dp ([FluidTextFieldDefaults.MinHeight]),
 * riempimento [fluidQuietFillAlpha], margini di 8 dp sopra e sotto e 12 ai lati (icona a 12, testo
 * a 38). Prima era un rettangolo di 44 dp col raggio di 12 e un velo del 6%. Su piu' righe la
 * capsula diventa un rettangolo arrotondato col raggio della capsula a una riga
 * ([FluidTextFieldDefaults.MultilineRadius], 20 dp), cosi' i due campi di un modulo sono della stessa
 * famiglia.
 */
@Composable
fun FluidTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  label: String? = null,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  singleLine: Boolean = true,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  isError: Boolean = false,
  supportingText: String? = null,
  minHeight: androidx.compose.ui.unit.Dp = FluidTextFieldDefaults.MinHeight,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  showClearButton: Boolean = singleLine,
  secret: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  // Lo stesso accorgimento di BasicTextField(String): la selezione e la composizione IME stanno in
  // uno stato locale, il testo e' sempre quello del chiamante. Se il chiamante cambia il testo da
  // fuori, la selezione locale si tiene (e il campo la riporta nei limiti da solo).
  var fieldValue by remember { mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length))) }
  val current = fieldValue.copy(text = value)
  SideEffect {
    if (current.selection != fieldValue.selection || current.composition != fieldValue.composition) {
      fieldValue = current
    }
  }
  var lastText by remember(value) { mutableStateOf(value) }

  FluidTextField(
    value = current,
    onValueChange = { new ->
      fieldValue = new
      // Il chiamante sente solo i cambi di testo: muovere il cursore non e' un valore nuovo.
      val changed = lastText != new.text
      lastText = new.text
      if (changed) onValueChange(new.text)
    },
    modifier = modifier,
    placeholder = placeholder,
    label = label,
    enabled = enabled,
    readOnly = readOnly,
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    isError = isError,
    supportingText = supportingText,
    minHeight = minHeight,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    visualTransformation = visualTransformation,
    showClearButton = showClearButton,
    secret = secret,
    leading = leading,
    trailing = trailing,
  )
}

/**
 * The text field, with the caret.
 *
 * E' la forma da usare quando qualcosa fuori dal campo deve sapere dov'e' il cursore o cambiare la
 * selezione: i pulsanti di un editor Markdown, un «inserisci qui». Le operazioni tipiche stanno in
 * [FluidTextEdit], pure, cosi' il chiamante non ricalcola gli indici a mano.
 */
@Composable
fun FluidTextField(
  value: TextFieldValue,
  onValueChange: (TextFieldValue) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  label: String? = null,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  singleLine: Boolean = true,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  isError: Boolean = false,
  supportingText: String? = null,
  minHeight: androidx.compose.ui.unit.Dp = FluidTextFieldDefaults.MinHeight,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  showClearButton: Boolean = singleLine,
  secret: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val interactionSource = remember { MutableInteractionSource() }
  // Chi chiede almeno due righe vuole un campo su piu' righe, anche se non ha tolto `singleLine`
  // (che di default e' vero): con `singleLine` il campo di testo ignora `minLines`, e i campi
  // «descrizione» e «dettagli» uscivano alti una riga sola. Lo stesso per il tasto che svuota, che
  // sta bene su una riga di ricerca e male in un paragrafo.
  val oneLine = singleLine && minLines <= 1
  val lineCap = if (oneLine) 1 else maxLines.takeIf { it > 1 && it >= minLines } ?: Int.MAX_VALUE
  // Un campo segreto non ha il tasto che svuota: a destra c'e' l'occhio, e due tasti piccoli
  // accanto sono il modo piu' rapido per scoprire quale dei due si e' colpito. Un segreto si riscrive.
  val clearButton = showClearButton && oneLine && !secret
  // Parte nascosto a ogni composizione: ricordarsi di essere stato mostrato vorrebbe dire che una
  // rotazione dello schermo, o il ritorno da un'altra app, rimette in chiaro una password.
  var revealed by remember { mutableStateOf(false) }
  val masked = secret && !revealed
  val transformation = if (masked) PasswordVisualTransformation() else visualTransformation
  val options = if (secret && keyboardOptions == KeyboardOptions.Default) {
    FluidSecretKeyboardOptions
  } else {
    keyboardOptions
  }

  // Il pozzo del campo, e perche' sopra il vetro non puo' essere lo stesso.
  //
  // Su una pagina opaca una velatura del 6 per cento basta: sotto c'e' una superficie piatta, e il
  // sei per cento si legge come un incavo. Dentro un pannello di vetro sotto NON c'e' una superficie
  // piatta — c'e' la pagina dell'app, trasmessa a circa meta'. La stessa velatura si modula con lei,
  // e un campo vuoto smette di essere un campo: diventa una finestra sulla lista che sta dietro.
  // Misurato su un modulo dentro un pannello: in una casella che dovrebbe essere una costante la
  // luminanza andava da 175 a 215, cioe' il contenuto della pagina, riconoscibile, dentro lo spazio
  // in cui si deve scrivere.
  //
  // Sopra il vetro il campo si dipinge quindi un fondo **opaco**, del colore del pannello scurito.
  // Opaco perche' e' l'unica cosa che ferma la pagina; del colore del pannello perche' un grigio
  // qualunque diventerebbe un rettangolo appiccicato sopra il vetro invece di un incavo scavato
  // dentro. E' la stessa regola dell'impilamento: sopra il vetro tutto sale, non scende.
  //
  // E di quanto si scurisce si misura, non si sceglie. Il film e' un colore quasi bianco che il
  // pannello rende a circa meta', quindi un pannello che a schermo sta a 230 di luminanza nasce da
  // un film a 250. Un decimo verso onSurface lasciava il campo a 224: piatto, finalmente, ma sei
  // punti sotto il pannello, cioe' MENO stacco della velatura che aveva prima. Piatto e
  // indistinguibile non e' un incavo, e' una toppa. Diciotto centesimi lo portano a circa 210: i
  // venti punti che l'incavo aveva sulla pagina opaca, questa volta senza la pagina dentro.
  val onGlass = LocalFluidCanvasIsGlass.current
  val film = GlassDefaults.glassFilm()
  val wellColor = when {
    onGlass && isError -> lerp(film, scheme.error, 0.22f)
    onGlass -> lerp(film, scheme.onSurface, 0.18f)
    isError -> scheme.error.copy(alpha = 0.10f)
    // Il grigio di riempimento di Apple (F6.6): 18 livelli sotto la pagina chiara, ~28 sopra la scura.
    else -> scheme.onSurface.copy(alpha = fluidQuietFillAlpha(GlassDefaults.isDarkSurface()))
  }
  // Capsula a una riga, rettangolo arrotondato col raggio della capsula su piu' righe (F6.6).
  val wellShape = if (oneLine) FluidCapsuleShape else FluidTextFieldMultilineShape

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (label != null) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
      )
    }

    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth(),
      enabled = enabled,
      readOnly = readOnly,
      singleLine = oneLine,
      minLines = minLines,
      maxLines = lineCap,
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
      cursorBrush = SolidColor(scheme.primary),
      keyboardOptions = options,
      keyboardActions = keyboardActions,
      visualTransformation = transformation,
      interactionSource = interactionSource,
      decorationBox = { innerTextField ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(wellShape)
            .background(wellColor)
            .heightIn(min = minHeight)
            .padding(horizontal = FluidTextFieldHorizontalPadding, vertical = FluidTextFieldVerticalPadding),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
              Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() }
            }
          }
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty() && placeholder != null) {
              Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                // Un campo a una riga non cresce per il segnaposto: una frase lunga si tronca, non va a capo (3.0.4).
                maxLines = if (oneLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
              )
            }
            innerTextField()
          }
          if (clearButton) {
            AnimatedVisibility(
              visible = value.text.isNotEmpty() && enabled,
              enter = fadeIn(FluidMotion.fadeIn(140)) + scaleIn(FluidMotion.snappy(), initialScale = 0.6f),
              exit = fadeOut(FluidMotion.fadeOut(120)) + scaleOut(FluidMotion.fadeOut(120), targetScale = 0.6f),
            ) {
              FluidClearButton(onClick = { onValueChange(TextFieldValue("")) })
            }
          }
          // 24 dp, non 18: e' l'altezza che il contenuto di un campo da 40 ha comunque (40 meno gli 8 dp
          // di margine sopra e sotto), quindi il campo non cresce, e un'icona di 24 dp sta intera.
          if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
              Box(modifier = Modifier.size(FluidFieldTrailingSize), contentAlignment = Alignment.Center) { trailing() }
            }
          }
          if (secret) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
              FluidRevealButton(revealed = revealed, enabled = enabled, onToggle = { revealed = !revealed })
            }
          }
        }
      },
    )

    supportingText?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) scheme.error else scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
      )
    }
  }
}

/** Il lato dello slot a destra di un campo: l'altezza del contenuto di un campo da 40 dp. */
internal val FluidFieldTrailingSize = 24.dp

/** I valori di serie di [FluidTextField]. */
object FluidTextFieldDefaults {
  /**
   * L'altezza minima del campo: 40 dp, i 40,0 pt del campo pieno di iOS 27 (147 px a 3,67 px/pt, sessione 278 a 11:15;
   * fiducia alta). Era 44. Col contenuto di 24 dp e 8 dp sopra e sotto il campo non cresce per l'icona a destra.
   */
  val MinHeight: Dp = 40.dp

  /** Il raggio di un campo su piu' righe: 20 dp, quello della capsula a una riga. */
  val MultilineRadius: Dp = 20.dp
}

/** Il margine ai lati del testo: 12 dp (Apple: l'icona a 13 pt dal bordo, il testo a 37). */
internal val FluidTextFieldHorizontalPadding = 12.dp

/** Il margine sopra e sotto: 8 dp, cosi' 40 dp lasciano al contenuto i 24 dello slot a destra. */
internal val FluidTextFieldVerticalPadding = 8.dp

private val FluidTextFieldMultilineShape = ContinuousCornerShape(FluidTextFieldDefaults.MultilineRadius)

/**
 * Il grigio di riempimento dei controlli quieti nel contenuto (campi, stepper opaco), come alfa di `onSurface`: l'8% sul
 * chiaro e il 12% sullo scuro. E' il `tertiarySystemFill` di Apple (118, 118, 128 al 12% sul chiaro, al 24% sullo
 * scuro, UIKit) portato sull'inchiostro dell'engine: sul bianco l'8% di `onSurface` toglie 18 livelli, quelli che si
 * misurano sotto il campo di ricerca di iOS 27 (pagina 253, campo 235); sullo scuro il 12% ne aggiunge ~28. Prima 6%.
 */
internal fun fluidQuietFillAlpha(dark: Boolean): Float = if (dark) 0.12f else 0.08f

/**
 * La tastiera di un campo segreto quando il chiamante non ne ha scelta una: quella delle password
 * (niente suggerimenti che finiscono nel dizionario dell'utente) e senza correzione automatica.
 */
internal val FluidSecretKeyboardOptions = KeyboardOptions(
  keyboardType = KeyboardType.Password,
  autoCorrectEnabled = false,
)

/**
 * L'occhio che mostra e nasconde un campo segreto. Cresce sotto il dito come ogni pulsante, e per chi
 * legge lo schermo dice cosa fara' toccandolo, non cosa e' adesso.
 */
@Composable
private fun FluidRevealButton(revealed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
  // Le parole per TalkBack vengono dall'app (3.0.1): erano scritte qui in italiano.
  val labels = LocalFluidLabels.current
  val description = if (revealed) labels.hideSecret else labels.showSecret
  Box(
    modifier = Modifier
      .size(FluidFieldTrailingSize)
      .semantics { contentDescription = description }
      .fluidPressable(
        onClick = onToggle,
        enabled = enabled,
        role = Role.Button,
        feedback = FluidPressFeedback.Expand,
      ),
    contentAlignment = Alignment.Center,
  ) {
    FluidIcon(
      imageVector = if (revealed) FluidIcons.VisibilityOff else FluidIcons.Visibility,
      contentDescription = null,
      modifier = Modifier.size(FluidFieldTrailingSize),
    )
  }
}

/**
 * The filled circle with a cross through it. Drawn rather than shipped as an icon asset.
 *
 * Il disegno resta di 28 dp. Il bersaglio del dito e' di 48 dp, la misura minima che Material chiede a un
 * tasto: sta sopra il disegno senza occupare spazio, quindi il campo non cresce e il testo non si restringe.
 * Per TalkBack e' un pulsante con il nome di [FluidLabels.clear].
 */
@Composable
private fun FluidClearButton(onClick: () -> Unit) {
  val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
  val glyph = MaterialTheme.colorScheme.surface
  val labels = LocalFluidLabels.current
  Box(
    modifier = Modifier.size(28.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .requiredSize(48.dp)
        .semantics { contentDescription = labels.clear }
        .fluidPressable(onClick = onClick, role = Role.Button, feedback = FluidPressFeedback.Expand),
      contentAlignment = Alignment.Center,
    ) {
      androidx.compose.foundation.Canvas(modifier = Modifier.size(17.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(color = color, radius = radius)
        val arm = radius * 0.44f
        val stroke = radius * 0.2f
        listOf(
          androidx.compose.ui.geometry.Offset(-arm, -arm) to androidx.compose.ui.geometry.Offset(arm, arm),
          androidx.compose.ui.geometry.Offset(-arm, arm) to androidx.compose.ui.geometry.Offset(arm, -arm),
        ).forEach { (from, to) ->
          drawLine(
            color = glyph,
            start = center + from,
            end = center + to,
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
          )
        }
      }
    }
  }
}
