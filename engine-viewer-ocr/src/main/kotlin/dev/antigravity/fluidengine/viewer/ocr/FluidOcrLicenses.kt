/*
 * engine-viewer-ocr (4.0, F8.17): le licenze del riconoscimento del testo, per la pagina «Licenze e crediti».
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/**
 * Le librerie e i dati di terzi di `engine-viewer-ocr`: Tesseract e il modello dell'italiano, Leptonica e le sue
 * librerie d'immagine, JavaCPP. Tutte libere e compatibili con la GPL-3.0 dell'engine.
 *
 * ```kotlin
 * fluidLicensesSection(licences = FluidEngineLicenses + FluidOcrLicenses)
 * ```
 */
val FluidOcrLicenses: List<FluidLicense> = listOf(
  FluidLicense("Tesseract OCR", "Google e i collaboratori di Tesseract", "Apache-2.0",
    "https://github.com/tesseract-ocr/tesseract", "Il riconoscimento del testo di «Leggi testo», sul telefono"),
  FluidLicense("tessdata_fast (ita)", "Google e i collaboratori di Tesseract", "Apache-2.0",
    "https://github.com/tesseract-ocr/tessdata_fast", "Il modello dell'italiano"),
  FluidLicense("Leptonica", "Dan Bloomberg", "BSD-2-Clause",
    "http://www.leptonica.org", "Le immagini per Tesseract"),
  FluidLicense("libpng, libjpeg-turbo, libtiff, libwebp, OpenJPEG, giflib, zlib", "i rispettivi autori",
    "libpng, IJG/BSD-3-Clause, libtiff, BSD-3-Clause, BSD-2-Clause, MIT, zlib",
    "https://github.com/bytedeco/javacpp-presets/tree/master/leptonica", "I formati d'immagine di Leptonica"),
  FluidLicense("JavaCPP e JavaCPP Presets", "Samuel Audet (bytedeco)", "Apache-2.0",
    "https://github.com/bytedeco/javacpp-presets", "Il collegamento fra Kotlin e Tesseract"),
)
