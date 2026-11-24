package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidPasswordCharset
import dev.antigravity.fluidengine.foundation.FluidPasswordCheck
import dev.antigravity.fluidengine.foundation.FluidPasswordRequirement
import dev.antigravity.fluidengine.foundation.FluidPasswordRules
import dev.antigravity.fluidengine.foundation.FluidPasswordStrength
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Key

/** Le misure del campo password. A occhio, 23 novembre 2026, nella famiglia del campo di testo. */
object FluidPasswordFieldDefaults {
  /** I segmenti della forza: quattro capsule alte 4 dp, a 4 dp l'una dall'altra. */
  val StrengthHeight: Dp = 4.dp
  val StrengthGap: Dp = 4.dp

  /** Il segno di una regola: 16 dp, il cerchio vuoto o la spunta. */
  val RuleMark: Dp = 16.dp
}

/** Il nome di una regola nelle parole del campo: «Almeno 8 caratteri», «Una lettera maiuscola o un numero». Pura. */
internal fun fluidPasswordRuleText(requirement: FluidPasswordRequirement, labels: FluidSpecialFieldLabels): String =
  when (requirement) {
    is FluidPasswordRequirement.MinLength -> labels.ruleMinLength(requirement.count)
    is FluidPasswordRequirement.MaxLength -> labels.ruleMaxLength(requirement.count)
    is FluidPasswordRequirement.MaxConsecutive -> labels.ruleMaxConsecutive(requirement.count)
    is FluidPasswordRequirement.OnlyAllowed -> labels.ruleOnlyAllowed
    is FluidPasswordRequirement.Contains -> requirement.any.mapIndexed { index, set ->
      val text = fluidPasswordCharsetText(set, labels)
      if (index == 0) text else text.replaceFirstChar { it.lowercase() }
    }.joinToString(labels.ruleOr)
  }

private fun fluidPasswordCharsetText(set: FluidPasswordCharset, labels: FluidSpecialFieldLabels): String = when (set) {
  FluidPasswordCharset.Lower -> labels.ruleLower
  FluidPasswordCharset.Upper -> labels.ruleUpper
  FluidPasswordCharset.Digit -> labels.ruleDigit
  FluidPasswordCharset.Special -> labels.ruleSpecial
  FluidPasswordCharset.AsciiPrintable, FluidPasswordCharset.Unicode -> labels.ruleLetter
  else -> labels.ruleCustom(set.id.removePrefix("[").removeSuffix("]").toList().joinToString(" "))
}

/**
 * Il campo password guidato (F8.3, idea `campo-password`): [FluidTextField] segreto, con l'occhio che mostra e nasconde,
 * e sotto **le regole che si spuntano mentre si scrive**, **la forza stimata** e, a campo vuoto, **la password forte
 * proposta**, come le schermate di registrazione di iOS con le Password Rules di iCloud Keychain.
 *
 * - **Le regole** sono dati dell'app ([rules], `FluidPasswordRules`, anche dalla riga di Apple con
 *   `FluidPasswordRules.parse`). Ognuna ha la sua riga e si spunta quando la password la rispetta; per TalkBack «Almeno 8
 *   caratteri, fatto».
 * - **La forza** è una stima semplice e dichiarata ([FluidPasswordStrength], niente zxcvbn): quattro segmenti e la parola
 *   («Buona · Stima indicativa»). Non promette sicurezza: è un aiuto visivo.
 * - **La password forte.** A campo vuoto e col fuoco compare la proposta (`FluidPasswordRules.generate`: il formato di
 *   Apple, «dixtuf-nezKun-zigvy6», quando le regole lo ammettono; sempre dentro le regole): «Usa password forte» la scrive
 *   nel campo e la mostra in chiaro, come iOS. Nasce sul dispositivo, da `SecureRandom`.
 * - **L'autocompilazione** del sistema: il campo è una password nuova ([ContentType.NewPassword]) se [newPassword], quella
 *   di un accesso ([ContentType.Password]) se no; un gestore di password propone di salvarla o la compila.
 *
 * Con [rules] null è il campo di un accesso: niente regole, niente forza, niente proposta.
 */
@Composable
fun FluidPasswordField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  rules: FluidPasswordRules? = FluidPasswordRules.Default,
  newPassword: Boolean = rules != null,
  showStrength: Boolean = newPassword,
  suggestStrong: Boolean = newPassword && rules != null,
  label: String? = null,
  placeholder: String? = null,
  enabled: Boolean = true,
  isError: Boolean = false,
  supportingText: String? = null,
  imeAction: ImeAction = if (newPassword) ImeAction.Next else ImeAction.Done,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current,
) {
  val revealed = remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }
  var dismissed by remember { mutableStateOf(false) }
  val suggestion = remember(rules) { rules?.takeIf { suggestStrong }?.let { runCatching { it.generate() }.getOrNull() } }
  val checks = remember(rules, value) { rules?.check(value).orEmpty() }
  val strength = remember(value) { FluidPasswordStrength.estimate(value) }
  val offerSuggestion = suggestion != null && enabled && value.isEmpty() && focused && !dismissed

  // Il testo col cursore: un testo nuovo da fuori (la password proposta) mette il cursore in fondo, come iOS; i cambi che
  // vengono dalla tastiera tengono selezione e composizione.
  var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
  val current = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
  FluidTextFieldCore(
    value = current,
    onValueChange = { next ->
      // Chi riscrive a mano torna ai puntini: la password in chiaro era solo quella proposta.
      if (next.text.length < value.length && revealed.value && value == suggestion) revealed.value = false
      field = next
      if (next.text != value) onValueChange(next.text)
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
    supportingText = supportingText,
    minHeight = FluidTextFieldDefaults.MinHeight,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
    keyboardActions = keyboardActions,
    visualTransformation = VisualTransformation.None,
    showClearButton = false,
    secret = true,
    leading = null,
    trailing = null,
    extras = FluidTextFieldExtras(
      contentType = if (newPassword) ContentType.NewPassword else ContentType.Password,
      revealed = revealed,
      fieldModifier = Modifier.onFocusChanged { focused = it.isFocused },
      supporting = {
        AnimatedVisibility(
          visible = offerSuggestion,
          enter = fadeIn(FluidMotion.fadeIn(180)) + expandVertically(FluidMotion.standard()),
          exit = fadeOut(FluidMotion.fadeOut(140)) + shrinkVertically(FluidMotion.standard()),
        ) {
          if (suggestion != null) PasswordSuggestion(
            password = suggestion,
            labels = labels,
            onUse = {
              revealed.value = true
              onValueChange(suggestion)
            },
            onDismiss = { dismissed = true },
          )
        }
        if (showStrength && rules != null) PasswordStrengthMeter(strength, labels)
        if (checks.isNotEmpty()) PasswordRules(checks, labels)
      },
    ),
  )
}

/** I quattro segmenti della forza e la parola, con «Stima indicativa». */
@Composable
private fun PasswordStrengthMeter(strength: FluidPasswordStrength, labels: FluidSpecialFieldLabels) {
  val scheme = MaterialTheme.colorScheme
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val level = strength.level.coerceIn(0, 4)
  val fill by animateFloatAsState(level.toFloat(), if (reduced) snap() else FluidMotion.standard(), label = "forza")
  val color = when (level) {
    0 -> scheme.outlineVariant
    1 -> scheme.error
    2 -> scheme.tertiary
    else -> scheme.primary
  }
  val track = scheme.onSurface.copy(alpha = fluidQuietFillAlpha(GlassDefaults.isDarkSurface()))
  val word = if (level == 0) null else labels.strengthLevels.getOrNull(level - 1)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp)
      .clearAndSetSemantics { if (word != null) contentDescription = labels.strengthSpoken(word) },
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(FluidPasswordFieldDefaults.StrengthHeight)
        .drawBehind {
          // Si legge nel disegno: la forza che cresce ridisegna, non ricompone.
          val gap = FluidPasswordFieldDefaults.StrengthGap.toPx()
          val w = (size.width - gap * 3) / 4f
          val r = CornerRadius(size.height / 2f)
          for (i in 0 until 4) {
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            val part = (fill - i).coerceIn(0f, 1f)
            if (part > 0f) drawRoundRect(color, Offset(x, 0f), Size(w * part, size.height), r)
          }
        },
    )
    if (word != null) {
      Text(
        text = "$word · ${labels.strengthNote}",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
      )
    }
  }
}

/** Le regole, una per riga, con la spunta che entra sfocata quando la regola è rispettata. */
@Composable
private fun PasswordRules(checks: List<FluidPasswordCheck>, labels: FluidSpecialFieldLabels) {
  val scheme = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    checks.forEach { check ->
      val text = fluidPasswordRuleText(check.requirement, labels)
      Row(
        modifier = Modifier.clearAndSetSemantics {
          contentDescription = "$text, ${if (check.satisfied) labels.ruleMet else labels.ruleMissing}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FluidGlassContentTransition(check.satisfied, Modifier.size(FluidPasswordFieldDefaults.RuleMark)) { met ->
          if (met) {
            FluidIcon(FluidIcons.Check, contentDescription = null, tint = scheme.primary,
              modifier = Modifier.size(FluidPasswordFieldDefaults.RuleMark))
          } else {
            Box(
              Modifier
                .size(FluidPasswordFieldDefaults.RuleMark - 4.dp)
                .border(1.5.dp, scheme.outline, FluidCapsuleShape),
            )
          }
        }
        Text(
          text = text,
          style = MaterialTheme.typography.bodySmall,
          color = if (check.satisfied) scheme.onSurface else scheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** La proposta della password forte: la password, dove nasce, e i due tasti di Apple. */
@Composable
private fun PasswordSuggestion(
  password: String,
  labels: FluidSpecialFieldLabels,
  onUse: () -> Unit,
  onDismiss: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ContinuousCornerShape(FluidTextFieldDefaults.MultilineRadius))
      .background(fluidTextFieldWellColor(isError = false))
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FluidIcon(FluidIcons.Key, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
      Text(labels.suggestedTitle, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
    }
    Text(
      text = password,
      style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
      color = scheme.onSurface,
      modifier = Modifier.semantics { contentDescription = password.toList().joinToString(" ") },
    )
    Text(labels.suggestedNote, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidGlassButton(
        text = labels.useSuggested,
        onClick = onUse,
        size = FluidGlassSize.Compact,
        maxLines = 1,
        style = FluidGlassButtonStyle.Prominent,
      )
      FluidGlassButton(
        text = labels.ownPassword,
        onClick = onDismiss,
        size = FluidGlassSize.Compact,
        maxLines = 1,
      )
    }
  }
}
