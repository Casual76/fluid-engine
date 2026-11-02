package dev.antigravity.fluidengine.aiui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.keys.VerifyResult
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * La scheda di una chiave: stato, guida a quattro passi con il tasto che apre la console, campo (la
 * chiave salvata non si ri-mostra mai: vuoto = "lascia quella salvata"), Verifica con l'esito inline,
 * Rimuovi.
 *
 * E' la scheda di ClasseViva, di PampAI (che l'aveva portata da CV, parola per parola) e di FluidWeather
 * (colori in parametro, stringhe nelle risorse), e la chiave di Fluid-transit, che invece stava in un
 * `AlertDialog` Material. **Union**:
 *  - [onSurface] e [onSurfaceVariant]: l'onboarding di FluidWeather sta sopra il cielo, e il testo non e'
 *    il colore di una superficie;
 *  - **un avviso prima del giro di rete** ([FluidKeyShape]): la chiave di Gemini incollata nel campo di
 *    Groq e' l'errore piu' comune, e "il servizio l'ha rifiutata" non dice perche';
 *  - la **spiegazione di cosa parte** verso il servizio ([FluidAiSettingsStrings.keyDisclosure]), che
 *    FT teneva a parte dalla chiave: la garanzia sulla chiave si leggeva come "non esce niente", e non
 *    e' vero. La scrive l'app, perche' dice cosa (voti, posizione, mappa).
 *  - il campo e' `FluidTextField(secret = true)`: una chiave e' una password, non si mostra e la
 *    tastiera non la impara; l'occhio la mostra.
 *
 * @param saveAndVerify salva (se la chiave non e' null) e verifica. Un'app con `AiKeyStore` e
 *   `AiKeyVerifier` scrive `{ p, k -> if (k != null) keys.set(p, k); verifier.verify(p) }`.
 * @param onVerified la chiave e' buona: FT accende l'assistente se l'utente non aveva mai scelto.
 */
@Composable
fun FluidAiKeySetup(
  provider: ProviderId,
  state: KeyState,
  saveAndVerify: suspend (ProviderId, String?) -> VerifyResult,
  onRemove: (ProviderId) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
  onVerified: (VerifyResult.Ok) -> Unit = {},
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
  onSurface: Color = MaterialTheme.colorScheme.onSurface,
  onSurfaceVariant: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val uriHandler = LocalUriHandler.current
  val scope = rememberCoroutineScope()
  val haptics = rememberFluidHaptics()
  val guide = strings.guide(provider)
  var expanded by remember { mutableStateOf(initiallyExpanded) }
  var input by remember { mutableStateOf("") }
  var verifying by remember { mutableStateOf(false) }
  var outcome by remember { mutableStateOf<String?>(null) }
  var outcomeError by remember { mutableStateOf(false) }
  val check = FluidKeyShape.check(provider, input)

  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(provider.label, style = MaterialTheme.typography.titleSmall, color = onSurface)
        Text(
          text = when {
            state.verified -> fluidFormat(strings.keyVerifiedOn, DateFormat.getDateInstance(DateFormat.SHORT).format(Date(state.verifiedAtMillis!!)))
            state.present -> strings.keySavedUnverified
            else -> guide.tagline
          },
          style = MaterialTheme.typography.bodySmall,
          color = onSurfaceVariant,
        )
      }
      FluidButton(
        text = if (expanded) strings.keyClose else strings.keyHowTo,
        style = FluidButtonStyle.Plain,
        size = FluidButtonSize.Small,
        onClick = { expanded = !expanded },
      )
    }
    AnimatedVisibility(visible = expanded) {
      Column(Modifier.padding(top = 8.dp)) {
        guide.steps.forEachIndexed { index, step ->
          Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
            Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant, modifier = Modifier.width(20.dp))
            Text(step, style = MaterialTheme.typography.bodySmall, color = onSurface)
          }
        }
        strings.keyDisclosure(provider)?.let { disclosure ->
          Spacer(Modifier.height(6.dp))
          Text(disclosure, style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row {
          FluidButton(
            text = fluidFormat(strings.keyOpenConsole, guide.consoleHost),
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            onClick = { runCatching { uriHandler.openUri(guide.consoleUrl) } },
          )
          if (guide.extraUrl != null && guide.extraLabel != null) {
            Spacer(Modifier.width(8.dp))
            FluidButton(
              text = guide.extraLabel,
              style = FluidButtonStyle.Plain,
              size = FluidButtonSize.Small,
              onClick = { runCatching { uriHandler.openUri(guide.extraUrl) } },
            )
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      FluidTextField(
        value = input,
        onValueChange = { input = FluidKeyShape.clean(it); outcome = null },
        placeholder = if (state.present) strings.keyPlaceholderKeep else fluidFormat(strings.keyPlaceholderNew, guide.placeholder),
        secret = true,
        isError = outcomeError,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      if (input.isNotBlank() || state.present) {
        FluidButton(
          text = if (verifying) strings.keyVerifying else strings.keyVerify,
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          loading = verifying,
          enabled = !verifying,
          onClick = {
            verifying = true
            outcome = null
            scope.launch {
              // Tutto dentro un solo try: qui si tocca il Keystore, il disco e la rete, e un errore
              // qualsiasi deve dire cos'e' andato storto, non far cadere l'app.
              try {
                when (val result = saveAndVerify(provider, input.takeIf { it.isNotBlank() })) {
                  is VerifyResult.Ok -> {
                    outcome = fluidFormat(strings.keyValid, result.catalogue.chat.size, result.catalogue.stt.size) +
                      (result.chosenDefault?.let { chosen -> fluidFormat(if (result.chosenIsFree) strings.keyChosenFree else strings.keyChosenPaid, chosen) } ?: "")
                    outcomeError = false
                    input = ""
                    haptics.play(FluidHapticEvent.Confirm)
                    onVerified(result)
                  }
                  VerifyResult.Invalid -> {
                    outcome = strings.keyInvalid
                    outcomeError = true
                    haptics.play(FluidHapticEvent.Error)
                  }
                  is VerifyResult.Failed -> {
                    outcome = fluidFormat(strings.keyFailed, result.error?.message ?: strings.keyNoAnswer)
                    outcomeError = true
                    haptics.play(FluidHapticEvent.Error)
                  }
                }
              } catch (e: CancellationException) {
                throw e
              } catch (e: Throwable) {
                outcome = fluidFormat(strings.keyFailed, e.message ?: e::class.java.simpleName)
                outcomeError = true
                haptics.play(FluidHapticEvent.Error)
              }
              verifying = false
            }
          },
        )
      }
      if (state.present && input.isBlank()) {
        Spacer(Modifier.width(4.dp))
        FluidButton(
          text = strings.keyRemove,
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Small,
          onClick = { onRemove(provider); outcome = null },
        )
      }
    }
    // L'avviso di forma sta sotto il campo finche' non c'e' un esito vero: dopo, parla il servizio.
    val hint = if (outcome == null) keyHint(check, provider, strings) else null
    (outcome ?: hint)?.let { message ->
      Spacer(Modifier.height(4.dp))
      Text(message, style = MaterialTheme.typography.bodySmall, color = if (outcomeError) MaterialTheme.colorScheme.error else onSurfaceVariant)
    }
  }
}

/** L'avviso per una chiave che non ha la forma di quelle del servizio; null quando va bene o non c'e' niente. */
internal fun keyHint(check: FluidKeyCheck, provider: ProviderId, strings: FluidAiSettingsStrings): String? = when (check) {
  is FluidKeyCheck.WrongProvider -> fluidFormat(strings.keyWrongProvider, check.actual.label, provider.label)
  is FluidKeyCheck.UnexpectedShape -> fluidFormat(strings.keyUnexpectedShape, provider.label, check.expectedPrefix)
  FluidKeyCheck.TooShort -> strings.keyTooShort
  FluidKeyCheck.Empty, FluidKeyCheck.Looks -> null
}
