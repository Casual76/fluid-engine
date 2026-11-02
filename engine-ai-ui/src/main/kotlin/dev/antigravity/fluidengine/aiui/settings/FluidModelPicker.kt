package dev.antigravity.fluidengine.aiui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/** Il nome di un modello per una riga delle impostazioni: dal catalogo se c'e', l'id altrimenti. */
fun ModelCatalogue?.fluidSummary(id: String?, strings: FluidAiSettingsStrings): String {
  if (id == null) return strings.modelAsChat
  val model = this?.chat?.firstOrNull { it.id == id } ?: this?.stt?.firstOrNull { it.id == id } ?: return id
  return if (model.free) "${model.displayName} · ${strings.metaFree}" else model.displayName
}

/** Un modello in una riga: nome, id, contesto, prezzo se esiste, cosa sa fare. */
@Composable
private fun ModelRow(
  model: ModelInfo,
  selected: Boolean,
  extra: String?,
  strings: FluidAiSettingsStrings,
  onClick: () -> Unit,
  badge: (@Composable () -> Unit)? = null,
) {
  val meta = buildList {
    FluidModelText.contextLabel(model.contextWindow)?.let { add(fluidFormat(strings.metaContext, it)) }
    if (model.pricePromptPerM != null) add(fluidFormat(strings.metaPrice, FluidModelText.priceLabel(model.pricePromptPerM), FluidModelText.priceLabel(model.priceCompletionPerM)))
    if (model.free) add(strings.metaFree)
    if (model.supportsReasoning) add(strings.metaReasons)
    if (model.supportsVision) add(strings.metaSees)
    if (model.supportsDocuments) add(strings.metaPdf)
    extra?.let { add(it) }
  }.joinToString(" · ")
  FluidListRow(
    title = model.displayName,
    subtitle = if (model.id == model.displayName) meta else "${model.id}${if (meta.isNotEmpty()) " · $meta" else ""}",
    onClick = onClick,
    leading = if (selected) {
      { Icon(Icons.Rounded.Check, contentDescription = strings.chosen, tint = MaterialTheme.colorScheme.primary) }
    } else {
      null
    },
    badge = badge,
  )
}

/**
 * Il picker dei modelli, **nel vetro dell'engine** (`FluidGlassModalPortal`, un foglio): per Groq e
 * Gemini un elenco col preferito in testa; per OpenRouter ricerca, filtri (gli strumenti sono
 * obbligatori), sezioni Consigliati / Gratuiti / Tutti, e fino a due riserve per la chat.
 *
 * E' il picker di ClasseViva e PampAI (identici); FluidWeather aveva lo stesso in un suo foglio nero
 * (`BlackSheet`) e senza il filtro "Vede", che qui c'e'. Dichiaralo **sempre**, mai dentro un `let`:
 * smontarlo alla chiusura toglie l'animazione di uscita, e il portale con `item` e' fatto per questo
 * (le lambda composable sono contenitori mutabili: alla chiusura le catture sono gia' null).
 *
 * @param request cosa si sta scegliendo; null lo chiude.
 * @param selectedModel il modello oggi scelto per quella richiesta.
 * @param fallbacks le riserve di OpenRouter, e [onFallbacks] per cambiarle.
 */
@Composable
fun FluidModelPickerPortal(
  request: FluidModelPickRequest?,
  catalogues: Map<ProviderId, ModelCatalogue>,
  selectedModel: (FluidModelPickRequest) -> String?,
  fallbacks: List<String>,
  onSelect: (FluidModelPickRequest, String) -> Unit,
  onFallbacks: (List<String>) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
) {
  FluidGlassModalPortal(
    item = request,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = request?.let { pickerTitle(it, strings) },
  ) { current ->
    val catalogue = catalogues[current.provider] ?: ModelCatalogue(emptyList(), emptyList())
    val models = if (current.stt) catalogue.stt else catalogue.chat
    val selected = selectedModel(current)
    Column(modifier) {
      Text(
        text = pickerTitle(current, strings),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
      )
      current.tier?.let {
        Text(strings.tierHint(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
      }
      Spacer(Modifier.height(8.dp))
      if (current.provider == ProviderId.OPENROUTER && !current.stt) {
        FluidOpenRouterList(
          catalogue = catalogue,
          primary = selected,
          fallbacks = if (current.tier == ModelTier.CHAT || current.tier == null) fallbacks else emptyList(),
          withFallbacks = current.tier == ModelTier.CHAT || current.tier == null,
          strings = strings,
          onPrimary = { id ->
            onSelect(current, id)
            // Sceglierlo come primario lo toglie dalle riserve: non puo' essere riserva di se stesso.
            if (id in fallbacks) onFallbacks(FluidModelPicker.afterChoosingPrimary(fallbacks, id))
          },
          onFallbacks = onFallbacks,
        )
      } else {
        FluidSimpleModelList(models = models, selected = selected, strings = strings, onSelect = { onSelect(current, it); onDismiss() })
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

private fun pickerTitle(request: FluidModelPickRequest, strings: FluidAiSettingsStrings): String =
  "${request.provider.label} · ${if (request.stt) strings.modelTranscription else request.tier?.let { strings.tierLabel(it) } ?: strings.tierChat}"

/** L'elenco semplice di Groq e Gemini: il preferito in testa, un tocco sceglie. */
@Composable
fun FluidSimpleModelList(
  models: List<ModelInfo>,
  selected: String?,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
) {
  if (models.isEmpty()) {
    Text(strings.pickerNone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(8.dp))
    return
  }
  LazyColumn(modifier = modifier.fillMaxWidth().heightIn(max = 460.dp)) {
    item {
      FluidListGroup {
        models.forEachIndexed { index, model ->
          if (index > 0) FluidListDivider()
          ModelRow(model = model, selected = model.id == selected, extra = null, strings = strings, onClick = { onSelect(model.id) })
        }
      }
    }
  }
}

/**
 * Il catalogo di OpenRouter: ricerca, filtri (i tool sono obbligatori e non si tolgono), sezioni
 * Consigliati / Gratuiti / Tutti, il primario e fino a due riserve. Le regole sono di
 * [FluidModelPicker] (provate); qui c'e' solo il disegno.
 */
@Composable
fun FluidOpenRouterList(
  catalogue: ModelCatalogue,
  primary: String?,
  fallbacks: List<String>,
  withFallbacks: Boolean,
  onPrimary: (String) -> Unit,
  onFallbacks: (List<String>) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
) {
  var filter by remember { mutableStateOf(FluidModelFilter()) }
  val sections = remember(catalogue, filter) { FluidModelPicker.sections(catalogue, filter) }
  Column(modifier.fillMaxWidth()) {
    FluidTextField(
      value = filter.query,
      onValueChange = { filter = filter.copy(query = it) },
      placeholder = strings.pickerSearch,
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
      FluidChip(label = strings.filterTools, selected = true, onClick = {}, enabled = false)
      FluidChip(label = strings.filterReasoning, selected = filter.onlyReasoning, onClick = { filter = filter.copy(onlyReasoning = !filter.onlyReasoning) })
      FluidChip(label = strings.filterFree, selected = filter.onlyFree, onClick = { filter = filter.copy(onlyFree = !filter.onlyFree) })
      FluidChip(label = strings.filterVision, selected = filter.onlyVision, onClick = { filter = filter.copy(onlyVision = !filter.onlyVision) })
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
      sections.forEach { (section, models) ->
        val title = when (section) {
          FluidModelSection.Recommended -> strings.sectionRecommended
          FluidModelSection.Free -> strings.sectionFree
          FluidModelSection.All -> strings.sectionAll
        }
        item(key = "h-$section") { FluidSectionHeader(title = title) }
        item(key = "g-$section") {
          FluidListGroup {
            models.forEachIndexed { index, model ->
              if (index > 0) FluidListDivider()
              val isPrimary = model.id == primary
              val isFallback = model.id in fallbacks
              ModelRow(
                model = model,
                selected = isPrimary,
                extra = if (isFallback) strings.fallbackTag else null,
                strings = strings,
                onClick = { onPrimary(model.id) },
                badge = if (!withFallbacks || isPrimary) {
                  null
                } else {
                  {
                    FluidButton(
                      text = if (isFallback) strings.fallbackRemove else strings.fallbackAdd,
                      style = FluidButtonStyle.Plain,
                      size = FluidButtonSize.Small,
                      enabled = FluidModelPicker.canToggleFallback(fallbacks, model.id),
                      onClick = { onFallbacks(FluidModelPicker.toggleFallback(fallbacks, model.id)) },
                    )
                  }
                },
              )
            }
          }
          if (withFallbacks && section == FluidModelSection.Recommended && fallbacks.size >= FluidModelPicker.MaxFallbacks) {
            Text(strings.fallbacksFull, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
          }
          Spacer(Modifier.height(8.dp))
        }
      }
      if (sections.isEmpty()) {
        item {
          Text(strings.pickerNoMatch, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
      }
    }
  }
}
