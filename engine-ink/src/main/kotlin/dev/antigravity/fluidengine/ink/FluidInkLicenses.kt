package dev.antigravity.fluidengine.ink

import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/** Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-ink. */
val FluidInkLicenses: List<FluidLicense> = listOf(
  FluidLicense(
    name = "Jetpack Ink (androidx.ink)",
    author = "The Android Open Source Project",
    licence = "Apache License 2.0",
    url = "https://developer.android.com/jetpack/androidx/releases/ink",
    role = "Il tratto a bassa latenza, i pennelli e la resa dei disegni e delle annotazioni.",
  ),
  FluidLicense(
    name = "Jetpack Motion Prediction (androidx.input)",
    author = "The Android Open Source Project",
    licence = "Apache License 2.0",
    url = "https://developer.android.com/jetpack/androidx/releases/input",
    role = "La predizione del tratto di un fotogramma, per la latenza piu' bassa.",
  ),
)
