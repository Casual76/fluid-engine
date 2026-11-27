package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidQuantity
import dev.antigravity.fluidengine.foundation.FluidUnit
import dev.antigravity.fluidengine.foundation.FluidUnitDimension
import dev.antigravity.fluidengine.foundation.FluidUnitFormat
import dev.antigravity.fluidengine.foundation.FluidUnits
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.UnfoldMore
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// ================================================================================================================
// La scrittura dal vivo, pura: il testo grezzo (cifre, un decimale della lingua, il meno) e le migliaia a vista
// ================================================================================================================

/** Quante cifre intere si scrivono al più: quindici, oltre è un errore di battitura (e un conto che non serve). */
internal const val FluidUnitFieldMaxIntegerDigits = 15

/**
 * Il testo grezzo di [value] per il campo: cifre, il decimale della lingua, il meno; niente migliaia (quelle sono solo a
 * vista). [minFraction] cifre dopo la virgola almeno (l'euro: «12,50»), [maxFraction] al più. Null: campo vuoto. Pura.
 */
internal fun fluidUnitRaw(value: BigDecimal?, separators: FluidUnitFormat.Separators, maxFraction: Int, minFraction: Int = 0): String {
  if (value == null) return ""
  return FluidUnitFormat.number(value, Locale.ROOT, maxFraction, minFraction, grouping = false)
    .replace('.', separators.decimal)
}

/** Il numero del testo grezzo [raw] (col decimale [decimal]); null se vuoto o se è solo «-» o «,». Pura. */
internal fun fluidUnitRawValue(raw: String, decimal: Char): BigDecimal? {
  val plain = raw.replace(decimal, '.')
  if (plain.isEmpty() || plain == "-" || plain == "." || plain == "-.") return null
  return plain.removeSuffix(".").toBigDecimalOrNull()
}

/**
 * Il testo grezzo dopo un cambio della tastiera, dal testo di prima [previous] e da quello arrivato [incoming] (grezzo
 * anche lui: le migliaia sono a vista). È la parte «notoriamente insidiosa» della scheda, e la regola è quella di
 * Calcolatrice:
 *  - **un tasto**: le cifre entrano; `.` e `,` sono **il** decimale della lingua, se non c'è ancora e se l'unità ha
 *    decimali (sulla tastiera italiana il punto diventa virgola); un decimale all'inizio diventa «0,»; il meno solo in
 *    testa e solo se [allowNegative]; gli zeri in testa vanno via («05» → «5»);
 *  - **oltre le cifre** dell'unità o le quindici intere il tasto non entra (il testo resta quello di prima);
 *  - **più caratteri insieme** (incolla, autocompilazione): il testo si legge con [FluidUnitFormat.parse] nella lingua, che
 *    accetta l'altra convenzione e il simbolo attaccato («1,234.56», «12,5 km»), e si arrotonda alle cifre dell'unità.
 * Pura.
 */
internal fun fluidUnitEdit(
  previous: String,
  incoming: String,
  separators: FluidUnitFormat.Separators,
  locale: Locale,
  maxFraction: Int,
  allowNegative: Boolean,
): String {
  if (incoming == previous) return previous
  val pasted = incoming.length - previous.length > 1
  if (pasted) {
    FluidUnitFormat.parse(incoming, locale)?.let { parsed ->
      val value = if (!allowNegative && parsed.signum() < 0) parsed.negate() else parsed
      val rounded = value.setScale(maxFraction.coerceAtLeast(0), RoundingMode.HALF_EVEN)
      return fluidUnitRaw(rounded, separators, maxFraction)
    }
  }
  val builder = StringBuilder()
  var decimalSeen = false
  var fractionDigits = 0
  var integerDigits = 0
  for ((index, c) in incoming.withIndex()) {
    when {
      c in '0'..'9' -> {
        if (decimalSeen) fractionDigits++ else integerDigits++
        builder.append(c)
      }
      (c == '.' || c == ',' || c == separators.decimal) && !decimalSeen && maxFraction > 0 -> {
        decimalSeen = true
        if (builder.isEmpty() || builder.toString() == "-") builder.append('0')
        builder.append(separators.decimal)
      }
      (c == '-' || c == '−') && index == 0 && allowNegative -> builder.append('-')
      else -> Unit
    }
  }
  if (fractionDigits > maxFraction || integerDigits > FluidUnitFieldMaxIntegerDigits) return previous
  // Gli zeri in testa: «05» → «5», «00» → «0»; «0,5» resta.
  val text = builder.toString()
  val negative = text.startsWith('-')
  val body = text.removePrefix("-")
  val decimalAt = body.indexOf(separators.decimal)
  val integer = if (decimalAt < 0) body else body.substring(0, decimalAt)
  val rest = if (decimalAt < 0) "" else body.substring(decimalAt)
  val trimmed = integer.trimStart('0').ifEmpty { if (integer.isNotEmpty()) "0" else "" }
  return (if (negative) "-" else "") + trimmed + rest
}

/**
 * Le migliaia a vista mentre si scrive (`1234567,5` si legge «1.234.567,5»), col cursore che resta dove la persona
 * l'ha messo: il testo grezzo non cambia, cambia solo come si vede.
 */
internal class FluidGroupingTransformation(private val separators: FluidUnitFormat.Separators) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val raw = text.text
    val sign = if (raw.startsWith('-')) 1 else 0
    val decimalAt = raw.indexOf(separators.decimal).let { if (it < 0) raw.length else it }
    val integer = raw.substring(sign, decimalAt)
    if (integer.length < 4) return TransformedText(text, OffsetMapping.Identity)
    val grouped = FluidUnitFormat.group(integer, separators.grouping)
    val out = raw.substring(0, sign) + grouped + raw.substring(decimalAt)
    // Le due mappe, costruite una volta per testo (non per fotogramma).
    val toOut = IntArray(raw.length + 1)
    val toRaw = IntArray(out.length + 1)
    var o = 0
    for (r in 0..raw.length) {
      while (o < out.length && r < raw.length && out[o] != raw[r]) o++
      toOut[r] = o
      if (r < raw.length) o++
    }
    var r2 = 0
    for (i in 0..out.length) {
      while (r2 < raw.length && toOut[r2] < i) r2++
      toRaw[i] = r2
    }
    return TransformedText(AnnotatedString(out), object : OffsetMapping {
      override fun originalToTransformed(offset: Int): Int = toOut[offset.coerceIn(0, raw.length)]
      override fun transformedToOriginal(offset: Int): Int = toRaw[offset.coerceIn(0, out.length)]
    })
  }

  override fun equals(other: Any?): Boolean = other is FluidGroupingTransformation && other.separators == separators
  override fun hashCode(): Int = separators.hashCode()
}

// ================================================================================================================
// Il campo
// ================================================================================================================

/** Le misure della capsula dell'unità. A occhio, 23 novembre 2026: alta quanto il contenuto del campo da 40 dp. */
object FluidUnitFieldDefaults {
  /** L'altezza della capsula dell'unità: 28 dp, nel campo da 40 con 8 dp sopra e sotto meno 2. */
  val CapsuleHeight: Dp = 28.dp

  /** Il margine ai lati del simbolo nella capsula. */
  val CapsulePadding: Dp = 10.dp
}

/**
 * Il campo con unità (F8.3, idea `campo-con-unita`): un numero con significato — un importo, una misura, una percentuale
 * — con la sua unità accanto, che si legge, si scrive e si converte. **Restituisce sempre il numero** ([onValueChange],
 * un [BigDecimal] nell'unità [unit], o null a campo vuoto), mai la stringa; il valore nell'unità base è
 * `FluidQuantity(valore, unit).inBase()`.
 *
 * Apple non ha un controllo unico: compone `TextField` con le `FormatStyle` di Measurement e Currency, come la Calcolatrice
 * di iOS 18 con le conversioni. Qui:
 * - **la lingua**: il separatore decimale è quello di [locale] («1,25» in italiano, «1.25» in inglese), e «1,25» resta
 *   scrivibile così mentre si scrive; le migliaia si vedono mentre si scrive, senza spostare il cursore;
 * - **la tastiera** dei numeri decimali; un incolla da un'altra app si legge in più formati («1,234.56», «12,5 km»);
 * - **le cifre** dell'unità: l'euro ne accetta due e a fine scrittura mostra «12,50»;
 * - **la capsula dell'unità** sta dove la lingua mette il simbolo (dopo in italiano, prima per la valuta in inglese); con
 *   più [units] si tocca e apre il menu dell'engine, e scegliendo un'altra unità il valore si converte (come
 *   Calcolatrice), se [convertOnUnitChange];
 * - **la conversione** sotto il campo: con [convertTo] una riga «≈ 7,77 mi» che segue quello che si scrive.
 *
 * L'aspetto è quello di [FluidTextField]: la stessa capsula da 40 dp, lo stesso pozzo sulla pagina e sul vetro.
 *
 * @param units le unità fra cui scegliere (della stessa dimensione di [unit]); vuota: la capsula non apre niente.
 * @param onUnitChange riceve l'unità scelta; senza, la capsula non apre niente.
 * @param allowNegative vero per le temperature, un saldo; falso di serie.
 */
@Composable
fun FluidUnitField(
  value: BigDecimal?,
  onValueChange: (BigDecimal?) -> Unit,
  unit: FluidUnit,
  modifier: Modifier = Modifier,
  units: List<FluidUnit> = emptyList(),
  onUnitChange: ((FluidUnit) -> Unit)? = null,
  convertOnUnitChange: Boolean = true,
  convertTo: FluidUnit? = null,
  label: String? = null,
  placeholder: String? = null,
  supportingText: String? = null,
  isError: Boolean = false,
  enabled: Boolean = true,
  allowNegative: Boolean = unit.dimension == FluidUnitDimension.Temperature,
  locale: Locale = fluidCurrentLocale(),
  imeAction: ImeAction = ImeAction.Done,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current,
) {
  val separators = remember(locale) { FluidUnitFormat.separators(locale) }
  val maxFraction = unit.fractionDigits
  var focused by remember { mutableStateOf(false) }
  // Il testo grezzo vive qui: «1,» resta «1,» mentre si scrive, anche se il numero è 1.
  var field by remember { mutableStateOf(TextFieldValue(fluidUnitRaw(value, separators, maxFraction, unit.minFractionDigits))) }
  var sent by remember { mutableStateOf(value) }
  var sentUnit by remember { mutableStateOf(unit) }
  // La lingua fa parte del testo grezzo (il decimale): cambiandola il testo si riscrive (visto sul tablet: «767,,08»).
  var sentSeparators by remember { mutableStateOf(separators) }
  val changed by rememberUpdatedState(onValueChange)
  // Un valore cambiato da fuori (o un'unità nuova) riscrive il testo; i cambi che vengono da qui no.
  val external = (value?.let { v -> sent?.let { it.compareTo(v) != 0 } ?: true } ?: (sent != null)) || unit != sentUnit ||
    separators != sentSeparators
  val current = if (external) {
    val raw = fluidUnitRaw(value, separators, maxFraction, if (focused) 0 else unit.minFractionDigits)
    TextFieldValue(raw, TextRange(raw.length))
  } else field
  if (external) SideEffect {
    field = current
    sent = value
    sentUnit = unit
    sentSeparators = separators
  }

  val choose: (FluidUnit) -> Unit = { next ->
    if (next != unit) {
      onUnitChange?.invoke(next)
      if (convertOnUnitChange && value != null) {
        val converted = FluidUnits.convert(value, unit, next, next.fractionDigits)
        if (converted != null) changed(converted.stripTrailingZerosSafe())
      }
    }
  }
  // Con un valore da convertire si offrono solo le unita' in cui si converte davvero (R2): una valuta senza cambio
  // dichiarato cambiava l'unita' lasciando il numero, e 12 € diventavano «12 $» in silenzio. A campo vuoto, tutte quelle
  // della stessa grandezza.
  val mustConvert = convertOnUnitChange && value != null
  val choices = units.filter { it == unit || it.canConvertTo(unit) || (!mustConvert && it.dimension == unit.dimension) }
  val before = FluidUnitFormat.symbolBefore(unit, locale)
  val capsule: @Composable () -> Unit = {
    UnitCapsule(unit, locale, choices.takeIf { onUnitChange != null && it.size > 1 }, choose, enabled, labels)
  }
  val conversion = convertTo?.takeIf { it != unit && value != null }?.let { target ->
    FluidQuantity(value!!, unit).to(target)?.let { labels.converted(FluidUnitFormat.format(it, locale)) }
  }

  FluidTextFieldCore(
    value = current,
    onValueChange = { next ->
      val raw = fluidUnitEdit(current.text, next.text, separators, locale, maxFraction, allowNegative)
      val selection = if (raw == next.text) next.selection else TextRange(raw.length)
      field = TextFieldValue(raw, selection, if (raw == next.text) next.composition else null)
      val number = fluidUnitRawValue(raw, separators.decimal)
      val before0 = fluidUnitRawValue(current.text, separators.decimal)
      if ((number == null) != (before0 == null) || (number != null && before0 != null && number.compareTo(before0) != 0)) {
        sent = number
        sentUnit = unit
        changed(number)
      }
    },
    modifier = modifier,
    placeholder = placeholder,
    label = label,
    enabled = enabled,
    readOnly = false,
    singleLine = true,
    minLines = 1,
    maxLines = 1,
    isError = isError,
    supportingText = conversion?.let { c -> supportingText?.let { "$c · $it" } ?: c } ?: supportingText,
    minHeight = FluidTextFieldDefaults.MinHeight,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false, imeAction = imeAction),
    keyboardActions = keyboardActions,
    visualTransformation = FluidGroupingTransformation(separators),
    showClearButton = false,
    secret = false,
    leading = null,
    trailing = null,
    extras = FluidTextFieldExtras(
      fieldModifier = Modifier.onFocusChanged { state ->
        val was = focused
        focused = state.isFocused
        // A fine scrittura il numero si scrive per bene: «12,5» diventa «12,50» per l'euro, «1,» diventa «1».
        if (was && !state.isFocused) {
          val number = fluidUnitRawValue(field.text, separators.decimal)
          val raw = fluidUnitRaw(number, separators, maxFraction, unit.minFractionDigits)
          field = TextFieldValue(raw, TextRange(raw.length))
        }
      },
      leadingWide = if (before) capsule else null,
      trailingWide = if (before) null else capsule,
    ),
  )
}

private fun BigDecimal.stripTrailingZerosSafe(): BigDecimal =
  if (signum() == 0) BigDecimal.ZERO else stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

/** La lingua in uso, quella della configurazione (cambia col sistema senza riavviare la pagina). */
@Composable
internal fun fluidCurrentLocale(): Locale {
  val configuration = LocalConfiguration.current
  return if (android.os.Build.VERSION.SDK_INT >= 24) configuration.locales[0] ?: Locale.getDefault() else Locale.getDefault()
}

/** La capsula dell'unità: il simbolo e, con delle scelte, il segno su-giù dei pop-up di iOS; toccata apre il menu. */
@Composable
private fun UnitCapsule(
  unit: FluidUnit,
  locale: Locale,
  choices: List<FluidUnit>?,
  onChoose: (FluidUnit) -> Unit,
  enabled: Boolean,
  labels: FluidSpecialFieldLabels,
) {
  val scheme = MaterialTheme.colorScheme
  val symbol = FluidUnitFormat.symbol(unit, locale)
  val menu = if (choices != null) rememberFluidContextMenu(actions = {
    choices.map { option ->
      FluidContextAction(
        label = FluidUnitFormat.symbol(option, locale),
        subtitle = FluidUnitFormat.name(option, locale)?.takeIf { it != FluidUnitFormat.symbol(option, locale) },
        selected = option == unit,
        onClick = { onChoose(option) },
      )
    }
  }) else null
  val shape = FluidCapsuleShape
  val base = Modifier
    .heightIn(min = FluidUnitFieldDefaults.CapsuleHeight)
    .clip(shape)
    .background(scheme.onSurface.copy(alpha = fluidQuietFillAlpha(GlassDefaults.isDarkSurface())))
  val interactive = if (menu != null && enabled) {
    base
      // La faccia si registra al sollevamento, come i menu della barra: il menu si apre anche da TalkBack, senza un
      // fotogramma disegnato col dito giù.
      .contextMenuAnchor(menu, listRow = false, group = null, captureOnLift = true)
      .fluidPressable(onClick = { menu.open() }, role = Role.Button, feedback = FluidPressFeedback.Expand)
      .clearAndSetSemantics {
        role = Role.Button
        contentDescription = labels.unitPicker
        stateDescription = symbol
        onClick { menu.open(); true }
      }
  } else {
    base.clearAndSetSemantics { contentDescription = symbol }
  }
  Row(
    modifier = interactive.padding(horizontal = FluidUnitFieldDefaults.CapsulePadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(symbol, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
    if (menu != null) {
      FluidIcon(FluidIcons.UnfoldMore, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
  }
}
