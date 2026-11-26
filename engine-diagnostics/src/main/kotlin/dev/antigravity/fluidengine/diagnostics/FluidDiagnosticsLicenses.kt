package dev.antigravity.fluidengine.diagnostics

import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/** Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-diagnostics. */
val FluidDiagnosticsLicenses: List<FluidLicense> = listOf(
  FluidLicense(
    name = "Jetpack Metrics (JankStats)",
    author = "The Android Open Source Project",
    licence = "Apache License 2.0",
    url = "https://developer.android.com/jetpack/androidx/releases/metrics",
    role = "Il conto dei fotogrammi lenti di ogni pagina, per la diagnostica.",
  ),
)
