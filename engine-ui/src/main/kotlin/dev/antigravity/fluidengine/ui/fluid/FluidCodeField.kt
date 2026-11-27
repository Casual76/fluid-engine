package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Lo stato di un [FluidCodeField]: il codice scritto, e l'esito che l'app gli dà. È dell'app (ricordato con
 * [rememberFluidCodeFieldState]): l'app legge [code], e a codice sbagliato chiama [fail].
 *
 * Non si salva nello stato salvato dell'attività: un codice monouso o un PIN non devono finire su disco quando il
 * sistema chiude l'app in secondo piano.
 */
@Stable
class FluidCodeFieldState(
  /** Quante caselle: da 4 a 8 (verifica in due passaggi, PIN, codice di abbinamento). */
  val length: Int,
) {
  init {
    require(length in 1..12) { "lunghezza del codice non valida: $length" }
  }

  /** Il codice scritto finora, mai più lungo di [length]. */
  var code: String by mutableStateOf("")
    private set

  /** Vero quando tutte le caselle sono piene. */
  val isComplete: Boolean get() = code.length == length

  /** Vero dal [fail] finché non si riscrive: le caselle sono nella tinta d'errore. */
  var isError: Boolean by mutableStateOf(false)
    private set

  internal var failures by mutableIntStateOf(0)
    private set

  /** Scrive [value] (tagliato a [length]): lo usano l'incolla e l'autocompilazione. Toglie l'errore. */
  fun set(value: String) {
    val next = value.take(length)
    if (next != code) isError = false
    code = next
  }

  /** Svuota le caselle. */
  fun clear() {
    code = ""
  }

  /**
   * Il codice non è giusto: le caselle tremano con l'aptica d'errore, prendono la tinta d'errore e si svuotano (come
   * il codice di sblocco di iOS e i campi del codice dell'Account Apple).
   */
  fun fail() {
    isError = true
    failures++
  }
}

/** Uno stato del campo a codice di [length] caselle, ricordato nella composizione. */
@Composable
fun rememberFluidCodeFieldState(length: Int = 6): FluidCodeFieldState = remember(length) { FluidCodeFieldState(length) }

/**
 * Le misure del campo a codice. **A occhio** (23 novembre 2026), dai campi del codice dell'Account Apple e di
 * Impostazioni su iOS 26 (fiducia media: nessuna misura al pixel), nella famiglia dei campi dell'engine.
 */
object FluidCodeFieldDefaults {
  /** L'altezza di una casella: 52 dp, quella del campo di vetro dell'engine ([FluidSearchFieldDefaults.Height], F6B.15). */
  val CellHeight: Dp = 52.dp

  /**
   * La larghezza minima di una casella: 48 dp, il bersaglio minimo; col testo grande la casella cresce con la cifra e le
   * caselle vanno a capo a gruppi, mai sotto i 48 dp (scheda dell'idea, «rischi»).
   */
  val CellMinWidth: Dp = 48.dp

  /** Lo spazio fra due caselle: 8 dp. */
  val CellSpacing: Dp = 8.dp

  /** Lo spazio fra due gruppi (3 + 3 per sei cifre, 4 + 4 per otto): 20 dp, come lo stacco dei codici di Apple. */
  val GroupSpacing: Dp = 20.dp

  /** Il raggio di una casella: quello dei controlli ([FluidRadius.Control], 12 dp), con l'angolo continuo. */
  val CellRadius: Dp = FluidRadius.Control

  /** Il bordo della casella attiva e di quella in errore: 2 dp, nell'accento o nella tinta d'errore. */
  val FocusBorder: Dp = 2.dp
}

/**
 * I numeri di movimento del campo a codice. **A occhio**, 23 novembre 2026: Apple non pubblica la scossa del codice
 * sbagliato; questa è quella del codice di sblocco di iOS letta a vista (tre scosse che si smorzano in mezzo secondo).
 */
internal object FluidCodeFieldMotion {
  /** L'ampiezza della scossa: 10 dp al primo colmo. */
  const val ShakeAmplitudeDp: Float = 10f

  /** La durata della scossa: 480 ms, sei mezzi periodi smorzati. */
  const val ShakeMs: Int = 480
  const val ShakeHalfPeriods: Int = 6

  /** Dopo la scossa le caselle si svuotano: 120 ms più tardi, perché l'occhio veda ancora il codice sbagliato. */
  const val ClearDelayMs: Long = 120L

  /** Il cursore lampeggia come quello di ogni campo di testo: 500 ms acceso, 500 spento. */
  const val CaretBlinkMs: Long = 500L
}

/** La scossa alla fase [u] (0..1), in frazione dell'ampiezza: un seno smorzato che parte e arriva a zero. Pura. */
internal fun fluidCodeShake(u: Float): Float {
  val t = u.coerceIn(0f, 1f)
  return sin(PI.toFloat() * FluidCodeFieldMotion.ShakeHalfPeriods * t) * exp(-3f * t)
}

/**
 * Quanto stringere le caselle di un codice di [length] cifre in [groups] gruppi perché stiano in una riga larga
 * [available] (F8.23, `singleRow`): 1 se stanno già, altrimenti la frazione che le fa entrare, caselle e spazi insieme,
 * mai sotto 0,4. Pura.
 */
internal fun fluidCodeSingleRowScale(length: Int, groups: Int, available: Dp): Float {
  val natural = FluidCodeFieldDefaults.CellMinWidth * length +
    FluidCodeFieldDefaults.CellSpacing * (length - groups) +
    FluidCodeFieldDefaults.GroupSpacing * (groups - 1).coerceAtLeast(0)
  if (available == Dp.Infinity || available >= natural) return 1f
  return (available / natural).coerceIn(0.4f, 1f)
}

/** I gruppi delle caselle: 3 + 3 per sei, 4 + 4 per otto, 3 + 4 per sette, uno solo fino a cinque. Pura. */
internal fun fluidCodeGroups(length: Int): List<Int> = when {
  length <= 5 -> listOf(length)
  length % 2 == 0 -> listOf(length / 2, length / 2)
  else -> listOf(length / 2, length - length / 2)
}

/**
 * Il testo nuovo del campo a codice dopo un cambio della tastiera: un carattere scritto si aggiunge, un incolla o
 * un'autocompilazione (più caratteri insieme) si legge come un codice intero («Il tuo codice è 482913», «482-913»).
 * Sempre solo caratteri validi, mai più di [length]; un incolla con più caratteri validi del codice e nessun codice
 * della lunghezza giusta (un numero di telefono) non entra: resta [previous]. Pura.
 */
internal fun fluidCodeInput(previous: String, raw: String, length: Int, digitsOnly: Boolean): String {
  fun valid(c: Char) = if (digitsOnly) c in '0'..'9' else c.isLetterOrDigit() && c.code < 128
  val inserted = raw.length - previous.length
  if (inserted > 1 || (inserted >= 1 && !raw.startsWith(previous))) {
    // Più di un carattere arrivato insieme: un codice incollato o proposto. Vince la sequenza lunga quanto il codice.
    fluidCodeFromText(raw, length..length, digitsOnly, strict = false)?.let { return it }
    // R2: un numero piu' lungo del codice (un telefono incollato) non si taglia alle prime cifre: l'incolla non entra.
    if (raw.count(::valid) > length) return previous
  }
  return raw.filter(::valid).take(length).let { if (digitsOnly) it else it.uppercase() }
}

/**
 * Il campo a codice (F8.3, idea `campo-codice`): le caselle per un codice di 4–8 cifre (verifica in due passaggi, PIN,
 * codice di abbinamento). È **un campo solo** per la tastiera, con le caselle disegnate sopra: il fuoco avanza e torna
 * indietro da solo, «cancella» toglie l'ultima cifra, e a codice completo chiama [onComplete].
 *
 * - **Il codice proposto.** Il campo dice al sistema che aspetta un codice monouso (il tipo di contenuto
 *   [ContentType.SmsOtpCode] dell'autocompilazione di Compose, come `textContentType(.oneTimeCode)` di Apple): il
 *   servizio di autocompilazione del telefono (Google, Samsung Pass) può proporre il codice arrivato per SMS sopra la
 *   tastiera, e il codice scelto riempie tutte le caselle. Nessuna libreria di Google Play services.
 * - **L'incolla di tutto il codice.** Un codice incollato col dito lungo, o dalla tastiera, riempie le caselle in un
 *   colpo, anche dentro una frase. Con [pasteButton] sotto le caselle compare il tasto incolla ([FluidPasteButton]) se
 *   negli appunti c'è un codice (solo allora: la nota di Alessio).
 * - **L'errore.** L'app verifica e, se il codice è sbagliato, chiama [FluidCodeFieldState.fail]: le caselle tremano con
 *   l'aptica d'errore, prendono la tinta d'errore e si svuotano. Con «Riduci movimento» non tremano.
 * - **La verifica.** Con [verifying] il campo non si scrive e le cifre si velano; il tasto con esito (`FluidActionButton`)
 *   accanto dice il lavoro e l'esito.
 * - **Il PIN.** Con [secret] le cifre sono pallini, e il campo è una password per l'accessibilità.
 * - **TalkBack** legge il nome del campo e «cifra 3 di 6», poi «codice completo».
 *
 * L'aspetto è quello dei campi dell'engine: ogni casella è il pozzo di [FluidTextField] (lo stesso colore, sulla pagina e
 * sul vetro), alta quanto il campo di vetro di F6B.15, col raggio dei controlli ([FluidCodeFieldDefaults]).
 *
 * @param digitsOnly vero: solo cifre, col tastierino numerico. Falso: lettere e cifre (in maiuscolo), per i codici di
 *   abbinamento.
 * @param label il nome del campo per TalkBack; di serie [FluidSpecialFieldLabels.code].
 * @param singleRow le caselle restano **in una riga sola**, come il codice di sblocco di iOS: quando lo spazio non basta
 *   (un telefono stretto, un pannello, il testo grande) si stringono in proporzione, caselle e spazi, invece di andare a
 *   capo a gruppi. Per un PIN (F8.23, la schermata di blocco); di serie falso, il comportamento di F8.3.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluidCodeField(
  state: FluidCodeFieldState,
  onComplete: (String) -> Unit,
  modifier: Modifier = Modifier,
  digitsOnly: Boolean = true,
  secret: Boolean = false,
  verifying: Boolean = false,
  enabled: Boolean = true,
  autoFocus: Boolean = false,
  pasteButton: Boolean = true,
  label: String? = null,
  labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current,
  singleRow: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  val haptics = LocalFluidHaptics.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val complete by rememberUpdatedState(onComplete)
  val focus = remember { FocusRequester() }
  var focused by remember { mutableStateOf(false) }
  val shake = remember { Animatable(0f) }

  // Il codice completo si annuncia all'app una volta per codice, da qualunque strada arrivi (tastiera, incolla, proposta).
  LaunchedEffect(state) {
    snapshotFlow { state.code }.collect { if (it.length == state.length) complete(it) }
  }
  // L'errore: la scossa, l'aptica, poi le caselle vuote.
  LaunchedEffect(state) {
    snapshotFlow { state.failures }.collect { failures ->
      if (failures == 0) return@collect
      haptics.play(FluidHapticEvent.Error)
      if (!reduced) {
        shake.snapTo(0f)
        shake.animateTo(1f, tween(FluidCodeFieldMotion.ShakeMs, easing = LinearEasing))
      }
      delay(FluidCodeFieldMotion.ClearDelayMs)
      // R2 (difetto 28): si svuota solo il codice sbagliato. Chi ha gia' riscritto durante la scossa (ogni cifra nuova
      // toglie l'errore, [FluidCodeFieldState.set]) tiene le cifre nuove.
      if (state.isError) state.clear()
      shake.snapTo(0f)
    }
  }
  LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }

  // Il cursore che lampeggia nella casella attiva: si legge solo nel disegno (nessuna ricomposizione), due volte al secondo.
  val caret = remember { mutableFloatStateOf(1f) }
  LaunchedEffect(focused, reduced, state.code.length) {
    caret.floatValue = 1f
    if (!focused || reduced) return@LaunchedEffect
    while (true) {
      delay(FluidCodeFieldMotion.CaretBlinkMs)
      caret.floatValue = if (caret.floatValue > 0f) 0f else 1f
    }
  }

  val name = label ?: labels.code
  val spokenState = when {
    verifying -> labels.codeVerifying
    state.isComplete -> labels.codeComplete
    else -> labels.codePosition(state.code.length + 1, state.length)
  }
  val text = state.code
  // Il valore del campo tiene la composizione della tastiera (le lettere di un codice di abbinamento si compongono), e
  // si riscrive solo quando il codice cambia da fuori (incolla dal tasto, svuotamento dopo l'errore).
  var field by remember { mutableStateOf(TextFieldValue()) }
  val value = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))

  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    // Il campo vero è trasparente e sta sotto le caselle: la tastiera, l'incolla col dito lungo e l'autocompilazione
    // parlano con lui; le caselle sono solo il suo disegno.
    CompositionLocalProvider(LocalTextSelectionColors provides InvisibleSelection) {
      BasicTextField(
        value = value,
        onValueChange = { raw ->
          if (verifying) return@BasicTextField
          val next = fluidCodeInput(text, raw.text, state.length, digitsOnly)
          state.set(next)
          field = if (next == raw.text) raw else TextFieldValue(next, TextRange(next.length))
        },
        enabled = enabled,
        readOnly = verifying,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
          keyboardType = if (digitsOnly) KeyboardType.NumberPassword else KeyboardType.Ascii,
          capitalization = if (digitsOnly) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
          autoCorrectEnabled = false,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions.Default,
        modifier = Modifier
          .focusRequester(focus)
          .onFocusChanged { focused = it.isFocused }
          .semantics {
            // Il codice monouso si fa proporre dall'autocompilazione; un PIN no: un codice SMS non va proposto (ne'
            // salvato) nel campo del PIN della schermata di blocco (R2).
            if (!secret) contentType = ContentType.SmsOtpCode
            contentDescription = name
            stateDescription = spokenState
            if (state.isError) error(labels.codeWrong)
          },
        decorationBox = { inner ->
          Box(contentAlignment = Alignment.Center) {
            val shakeModifier = Modifier.graphicsLayer {
              translationX = fluidCodeShake(shake.value) * FluidCodeFieldMotion.ShakeAmplitudeDp * density.density
            }
            // Le caselle di un gruppo; [cellWidth] nullo: la larghezza di serie, che cresce col testo grande.
            val group: @Composable (Int, Int, Dp, Dp?) -> Unit = { from, size, cellGap, cellWidth ->
              Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                for (index in from until from + size) {
                  // La casella attiva è la prossima da scrivere; a codice completo, l'ultima.
                  val activeIndex = if (state.isComplete) state.length - 1 else text.length
                  CodeCell(
                    char = text.getOrNull(index),
                    active = focused && enabled && !verifying && index == activeIndex,
                    secret = secret,
                    error = state.isError,
                    dimmed = verifying || !enabled,
                    caret = { caret.floatValue },
                    width = cellWidth,
                  )
                }
              }
            }
            val groups = fluidCodeGroups(state.length)
            if (singleRow) {
              // Una riga sola, come iOS: se non c'è posto le caselle si stringono, mai a capo.
              BoxWithConstraints(contentAlignment = Alignment.Center) {
                val scale = fluidCodeSingleRowScale(state.length, groups.size, maxWidth)
                Row(
                  modifier = shakeModifier,
                  horizontalArrangement = Arrangement.spacedBy(FluidCodeFieldDefaults.GroupSpacing * scale),
                ) {
                  var start = 0
                  groups.forEach { size ->
                    group(start, size, FluidCodeFieldDefaults.CellSpacing * scale, FluidCodeFieldDefaults.CellMinWidth * scale)
                    start += size
                  }
                }
              }
            } else {
              FlowRow(
                modifier = shakeModifier,
                horizontalArrangement = Arrangement.spacedBy(FluidCodeFieldDefaults.GroupSpacing, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(FluidCodeFieldDefaults.CellSpacing),
              ) {
                var start = 0
                groups.forEach { size ->
                  group(start, size, FluidCodeFieldDefaults.CellSpacing, null)
                  start += size
                }
              }
            }
            // Il testo vero, invisibile, sopra le caselle: il dito lungo ci trova l'incolla del sistema.
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) { inner() }
          }
        },
      )
    }
    if (pasteButton && enabled && !verifying) {
      FluidPasteButton(
        kind = FluidPasteKind.code(state.length..state.length, digitsOnly),
        onPaste = { pasted -> if (pasted is FluidPasted.Code) state.set(pasted.code) },
        labels = labels,
      )
    }
  }
}

private val InvisibleSelection = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)

private val CodeCellShape = ContinuousCornerShape(FluidCodeFieldDefaults.CellRadius)

/** Una casella: il pozzo del campo, la cifra (o il pallino), il bordo della casella attiva e il cursore. */
@Composable
private fun CodeCell(
  char: Char?,
  active: Boolean,
  secret: Boolean,
  error: Boolean,
  dimmed: Boolean,
  caret: () -> Float,
  width: Dp? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val well = fluidTextFieldWellColor(error)
  val border = when {
    error -> scheme.error
    active -> scheme.primary
    else -> Color.Transparent
  }
  val digitStyle = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
  Box(
    modifier = Modifier
      .heightIn(min = FluidCodeFieldDefaults.CellHeight)
      .then(if (width != null) Modifier.width(width) else Modifier.widthIn(min = FluidCodeFieldDefaults.CellMinWidth))
      .clip(CodeCellShape)
      .background(well)
      .border(FluidCodeFieldDefaults.FocusBorder, border, CodeCellShape)
      .then(if (active && char == null) Modifier.drawBehind {
        // Il cursore: 2 dp per 24, al centro, nell'accento.
        val alpha = caret()
        if (alpha > 0f) {
          val w = 2.dp.toPx()
          val h = 24.dp.toPx().coerceAtMost(size.height * 0.6f)
          drawRoundRect(scheme.primary.copy(alpha = alpha), Offset((size.width - w) / 2f, (size.height - h) / 2f),
            Size(w, h), CornerRadius(w / 2f))
        }
      } else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    when {
      char == null -> Spacer(Modifier.width(1.dp).height(1.dp))
      secret -> Box(Modifier.size(12.dp).clip(FluidCapsuleShape).background(if (dimmed) scheme.onSurfaceVariant else scheme.onSurface))
      else -> Text(
        text = char.toString(),
        style = digitStyle,
        color = if (dimmed) scheme.onSurfaceVariant else scheme.onSurface,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}
