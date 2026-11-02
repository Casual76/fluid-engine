package dev.antigravity.fluidengine.aiui.markdown

import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownOptions

/**
 * Come si legge il testo di una risposta dell'assistente: i marcatori `[[chip]]` che il modello
 * scrive per i chip sotto la risposta spariscono dal testo (a fine risposta diventano chip, e
 * mostrarli mentre arrivano non dice niente).
 *
 * Il Markdown e' quello di engine-ui (`FluidMarkdown`, `FluidMarkdownParser`), che di serie non li
 * tocca: un testo qualunque puo' usare le parentesi doppie per se'. Qui sta la parte che sa cos'e' un
 * assistente. Le bolle, la card e il pannello lo passano da soli; chi disegna una risposta per conto
 * suo (una cronologia, un'anteprima) lo passa come `options`.
 */
val FluidAssistantMarkdownOptions = FluidMarkdownOptions(hideChipMarkers = true)
