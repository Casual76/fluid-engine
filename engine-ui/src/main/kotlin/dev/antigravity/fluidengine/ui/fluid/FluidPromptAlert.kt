package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Cosa pensa un avviso con un campo di quello che e' stato scritto.
 *
 * Tre stati e non un booleano, perche' «non va bene» ne copre due che si mostrano in modo opposto:
 * un campo **vuoto** non ha sbagliato niente — non ha ancora scritto — e dirgli «Il nome non puo'
 * essere vuoto» mentre l'avviso si apre e' rimproverare chi non ha ancora cominciato; un valore
 * **fuori misura** ha sbagliato, e va detto dove (`Invalid`). In tutti e due il tasto di conferma e'
 * spento, ma solo uno dei due ha una frase.
 */
@Immutable
sealed interface FluidPromptValidation {
  /** Si puo' confermare. */
  data object Valid : FluidPromptValidation

  /** Non ancora: il tasto resta spento e il campo tace. Il caso del campo vuoto. */
  data object Incomplete : FluidPromptValidation

  /** Scritto, ma sbagliato: il tasto resta spento e il campo mostra [message] sotto di se', in errore. */
  data class Invalid(val message: String) : FluidPromptValidation
}

/**
 * La validazione di serie di [FluidPromptAlert]: basta che ci sia qualcosa che non siano spazi.
 *
 * E' quella di una rinomina, e quella a cui un'app torna quando non ha regole sue: un nome fatto di soli
 * spazi non e' un nome, ma nemmeno un errore da spiegare.
 */
fun fluidPromptNotBlank(text: String): FluidPromptValidation =
  if (text.isBlank()) FluidPromptValidation.Incomplete else FluidPromptValidation.Valid

/**
 * Il testo come lo vedono la validazione e la conferma: ritagliato dagli spazi ai bordi se [trim].
 *
 * Una sola funzione per tutti e due perche' devono vedere lo **stesso** testo: se la validazione
 * guardasse il campo grezzo e la conferma quello ritagliato, "  " sarebbe valido e confermerebbe una
 * stringa vuota.
 */
internal fun fluidPromptText(raw: String, trim: Boolean): String = if (trim) raw.trim() else raw

/** La selezione con cui si apre il campo: tutto il testo (si riscrive) o il cursore in fondo (si prosegue). */
internal fun fluidPromptInitialSelection(text: String, selectAll: Boolean): TextRange =
  if (selectAll) TextRange(0, text.length) else TextRange(text.length)

/**
 * Un avviso che chiede una riga di testo: rinominare qualcosa, scrivere un valore di taratura.
 *
 * E' [FluidAlert] con un [FluidTextField] nel suo slot, e tutto il resto e' quello che ogni app si
 * riscriveva intorno (la rinomina di Codex, la taratura di Pampa Sounds, il nome di una playlist): il
 * tasto «Salva» che si accende solo quando ha senso, il testo che si ritaglia, la tastiera che conferma.
 *
 * **Il tasto e' la validazione.** [validate] vede il testo (ritagliato, se [trim]) a ogni modifica e
 * risponde con un [FluidPromptValidation]: finche' non e' `Valid` il tasto di conferma e' spento, e se e'
 * `Invalid` il campo mostra la frase sotto di se' e si colora. Una validazione che girava solo al tocco
 * di «Salva» voleva dire un tasto sempre acceso che non fa niente, che e' peggio di uno spento.
 *
 * **La tastiera conferma.** L'azione del tasto invio — `Done` di serie, `Go` o `Send` se la dai a
 * [keyboardOptions] — fa quello che il tasto di conferma fa, e solo se la validazione lo permette. Un
 * avviso in cui ci si ferma a cercare il tasto sopra la tastiera e' un avviso che costa un tocco in piu'.
 *
 * **Si apre pronto a scrivere.** Il campo prende il fuoco da solo e, con [selectAll], con il testo
 * iniziale tutto selezionato: rinominare e' sostituire, e chi vuole solo ritoccare tocca e sposta il
 * cursore. Senza [initialValue] non c'e' niente da selezionare.
 *
 * Come ogni [FluidAlert] e' una finestra di piattaforma, quindi dipinge il suo vetro e non rifrange la
 * pagina: e' il prezzo di una tastiera che deve salire sopra tutto. Le parole di serie sono in
 * italiano; un'app tradotta le passa tutte (vedi `fluidLicensesSection`).
 *
 * @param onConfirm riceve il testo gia' ritagliato (se [trim]) e **non chiude** l'avviso: lo possiede
 *   chi lo mostra, come per ogni portale.
 * @param validate vedi [FluidPromptValidation]; di serie [fluidPromptNotBlank].
 * @param trim toglie gli spazi ai bordi prima di validare e di confermare.
 * @param selectAll apre con [initialValue] selezionato.
 */
@Composable
fun FluidPromptAlert(
  title: String,
  onDismissRequest: () -> Unit,
  onConfirm: (String) -> Unit,
  modifier: Modifier = Modifier,
  message: String? = null,
  initialValue: String = "",
  placeholder: String? = null,
  confirmLabel: String = "Salva",
  cancelLabel: String = "Annulla",
  validate: (String) -> FluidPromptValidation = ::fluidPromptNotBlank,
  keyboardOptions: KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Done,
  ),
  trim: Boolean = true,
  selectAll: Boolean = true,
  singleLine: Boolean = true,
) {
  var field by remember(initialValue) {
    mutableStateOf(
      TextFieldValue(
        text = initialValue,
        selection = fluidPromptInitialSelection(initialValue, selectAll),
      ),
    )
  }
  val text = fluidPromptText(field.text, trim)
  val validation = validate(text)
  val canConfirm = validation is FluidPromptValidation.Valid
  val confirm: () -> Unit = { if (canConfirm) onConfirm(text) }

  val focus = remember { FocusRequester() }
  // Un avviso che non prende il fuoco non deve far cadere l'app: se la finestra non e' ancora pronta
  // il campo resta com'e', e si tocca. Il fuoco e' una cortesia, non una condizione.
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  FluidAlert(
    onDismissRequest = onDismissRequest,
    title = title,
    modifier = modifier,
    message = message,
    actions = listOf(
      FluidAlertAction(label = cancelLabel, onClick = onDismissRequest),
      FluidAlertAction(
        label = confirmLabel,
        onClick = confirm,
        emphasis = FluidAlertAction.Emphasis.Preferred,
        enabled = canConfirm,
      ),
    ),
  ) {
    FluidTextField(
      value = field,
      onValueChange = { field = it },
      modifier = Modifier.padding(top = 8.dp).focusRequester(focus),
      placeholder = placeholder,
      singleLine = singleLine,
      isError = validation is FluidPromptValidation.Invalid,
      supportingText = (validation as? FluidPromptValidation.Invalid)?.message,
      keyboardOptions = keyboardOptions,
      keyboardActions = KeyboardActions(onAny = { confirm() }),
    )
  }
}
