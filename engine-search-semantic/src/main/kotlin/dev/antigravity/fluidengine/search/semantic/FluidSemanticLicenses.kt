package dev.antigravity.fluidengine.search.semantic

import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/**
 * Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include
 * engine-search-semantic (e il modello). Il perche' e le versioni: `LICENSES/LiteRT-LM.md`.
 */
val FluidSemanticSearchLicenses: List<FluidLicense> = listOf(
  FluidLicense(
    name = "LiteRT-LM",
    author = "Google LLC",
    licence = "Apache License 2.0",
    url = "https://github.com/google-ai-edge/LiteRT-LM",
    role = "Il motore che fa girare sul telefono il modello del significato della ricerca.",
  ),
  FluidLicense(
    name = "EmbeddingGemma 2",
    author = "Google LLC",
    licence = "Apache License 2.0",
    url = "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm",
    role = "Il modello che capisce il significato di una ricerca («la mail del dentista»), tutto sul telefono.",
  ),
)
