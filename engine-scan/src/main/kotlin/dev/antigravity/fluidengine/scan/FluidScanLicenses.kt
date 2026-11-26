/*
 * engine-scan (4.0, F8.17): le licenze delle librerie del modulo, da aggiungere alla pagina «Licenze e crediti».
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/**
 * Le librerie di terzi di `engine-scan`: solo CameraX. I bordi, la prospettiva, i filtri e il PDF sono codice
 * dell'engine (niente OpenCV).
 *
 * ```kotlin
 * fluidLicensesSection(licences = FluidEngineLicenses + FluidScanLicenses)
 * ```
 */
val FluidScanLicenses: List<FluidLicense> = listOf(
  FluidLicense("AndroidX CameraX", "Android Open Source Project", "Apache-2.0",
    "https://developer.android.com/jetpack/androidx/releases/camera", "La fotocamera dello scanner di documenti"),
)
