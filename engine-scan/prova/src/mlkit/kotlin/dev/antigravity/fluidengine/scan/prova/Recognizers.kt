package dev.antigravity.fluidengine.scan.prova

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedBlock
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedLine
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedText
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedWord
import dev.antigravity.fluidengine.viewer.ocr.FluidTesseractRecognizer
import dev.antigravity.fluidengine.viewer.ocr.FluidTextQuad
import dev.antigravity.fluidengine.viewer.ocr.FluidTextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** La variante «mlkit»: Tesseract e ML Kit Text Recognition v2, sulle stesse immagini. */
internal fun recognizers(context: Context): List<Pair<String, FluidTextRecognizer>> =
  listOf("tesseract" to FluidTesseractRecognizer(context), "mlkit" to MlKitRecognizer())

/** ML Kit dietro il contratto dell'engine: com'è un riconoscitore che un'app potrebbe dare a FluidLiveText. */
private class MlKitRecognizer : FluidTextRecognizer {
  private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  override val available: Boolean = true

  override suspend fun recognize(image: Bitmap): FluidRecognizedText {
    val result = suspendCancellableCoroutine { c ->
      client.process(InputImage.fromBitmap(image, 0))
        .addOnSuccessListener { c.resume(it) }
        .addOnFailureListener { c.resumeWithException(it) }
    }
    val w = image.width.toFloat()
    val h = image.height.toFloat()
    fun quad(r: android.graphics.Rect?) = FluidTextQuad.of(r?.let { Rect(it.left / w, it.top / h, it.right / w, it.bottom / h) } ?: Rect.Zero)
    val blocks = result.textBlocks.map { b ->
      FluidRecognizedBlock(b.lines.map { l ->
        FluidRecognizedLine(l.elements.map { e -> FluidRecognizedWord(e.text, quad(e.boundingBox), e.confidence) }, quad(l.boundingBox))
      }, quad(b.boundingBox))
    }
    return FluidRecognizedText(blocks, image.width, image.height, "lat")
  }
}
