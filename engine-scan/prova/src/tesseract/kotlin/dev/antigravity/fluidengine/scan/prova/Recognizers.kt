package dev.antigravity.fluidengine.scan.prova

import android.content.Context
import dev.antigravity.fluidengine.viewer.ocr.FluidTesseractRecognizer
import dev.antigravity.fluidengine.viewer.ocr.FluidTextRecognizer

/** La variante «tesseract»: solo il riconoscimento dell'engine. */
internal fun recognizers(context: Context): List<Pair<String, FluidTextRecognizer>> =
  listOf("tesseract" to FluidTesseractRecognizer(context))
