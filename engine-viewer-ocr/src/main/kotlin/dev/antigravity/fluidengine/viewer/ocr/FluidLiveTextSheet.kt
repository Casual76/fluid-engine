/*
 * engine-viewer-ocr (4.0, F8.17d): «scansiona, poi leggi». In una foto storta di un foglio Tesseract spezza e mescola
 * le righe (21,7% di caratteri sbagliati sulla foto di prova, sul tablet); la stessa foto passata dallo scanner si legge
 * quasi perfetta (2,1%). Qui la lettura trova il foglio coi bordi dello scanner (engine-scan-core), legge la pagina
 * raddrizzata e riporta le righe sulla foto con la stessa prospettiva: per chi guarda non cambia niente, la selezione
 * resta sulla foto vera. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.scan.FluidScanDetection
import dev.antigravity.fluidengine.scan.FluidScanEdges
import dev.antigravity.fluidengine.scan.FluidScanImages
import dev.antigravity.fluidengine.scan.FluidScanPageMap
import dev.antigravity.fluidengine.scan.FluidScanQuad
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * La lettura di una foto passando dal foglio, se c'è e se serve. Il foglio si raddrizza solo quando la foto lo vede
 * **storto** (girato o in prospettiva) e grande: una schermata, una pagina già dritta o una foto senza foglio si leggono
 * come prima, tutte intere.
 */
internal object SheetReading {
  /**
   * La fiducia minima nei bordi: 0,8. A occhio: sulle foto di prova dello scanner i fogli veri stanno fra 0,91 e 0,99, e
   * sotto 0,8 un foglio sbagliato toglierebbe alla lettura il resto della foto.
   */
  const val MinConfidence = 0.8f

  /** La parte di foto che il foglio deve coprire: un quinto. Un foglio più piccolo è un dettaglio della foto. */
  const val MinArea = 0.2f

  /**
   * Quanto deve essere storto il foglio perché valga la pena raddrizzarlo: un lato girato di più di 1,5° dall'asse, o due
   * lati opposti che differiscono di più del 2% (la prospettiva). A occhio: Tesseract raddrizza da sé le righe girate di
   * un grado o due, e i riquadri dritti di una schermata (le bolle, le card) non arrivano mai a queste soglie.
   */
  const val MinTiltDegrees = 1.5f
  const val MinPerspective = 0.02f

  /** Il foglio da raddrizzare in [detection] (una foto [width] × [height]), o `null` se la foto si legge intera. */
  fun sheetToStraighten(detection: FluidScanDetection?, width: Int, height: Int): FluidScanQuad? {
    if (detection == null || detection.confidence < MinConfidence) return null
    val quad = detection.quad
    val aspect = width.toFloat() / height
    if (quad.area(aspect) / aspect < MinArea || !quad.isConvex(aspect)) return null
    return quad.takeIf { tilt(it, width, height) > MinTiltDegrees || perspective(it, width, height) > MinPerspective }
  }

  /** L'angolo più grande (in gradi) fra un lato del foglio e l'asse più vicino, orizzontale o verticale. */
  internal fun tilt(quad: FluidScanQuad, width: Int, height: Int): Float {
    var worst = 0f
    for (i in 0 until 4) {
      val a = quad[i]
      val b = quad[i + 1]
      val deg = (atan2(((b.y - a.y) * height).toDouble(), ((b.x - a.x) * width).toDouble()) * 180.0 / PI).toFloat()
      val fromAxis = abs(((deg % 90f) + 90f) % 90f).let { min(it, 90f - it) }
      worst = max(worst, fromAxis)
    }
    return worst
  }

  /** Quanto differiscono i lati opposti (0 per un rettangolo visto di fronte). */
  internal fun perspective(quad: FluidScanQuad, width: Int, height: Int): Float {
    fun side(i: Int): Float = hypot((quad[i + 1].x - quad[i].x) * width, (quad[i + 1].y - quad[i].y) * height)
    fun diff(a: Float, b: Float) = abs(a - b) / max(max(a, b), 1f)
    return max(diff(side(0), side(2)), diff(side(1), side(3)))
  }

  /**
   * Legge [photo] con [recognizer]: se c'è un foglio storto, la pagina raddrizzata e poi le righe riportate sulla foto;
   * se no la foto intera. [photo] resta di chi chiama.
   */
  suspend fun recognize(recognizer: FluidTextRecognizer, photo: Bitmap): FluidRecognizedText {
    val sheet = sheetToStraighten(FluidScanEdges.detect(photo), photo.width, photo.height)
    val map = sheet?.inset(FluidScanImages.EdgeInset)?.pageMap() ?: return recognizer.recognize(photo)
    val page = FluidScanImages.straighten(photo, sheet)
    try {
      return recognizer.recognize(page).onPhoto(map, photo.width, photo.height)
    } finally {
      page.recycle()
    }
  }
}

/**
 * Lo stesso testo con ogni riquadro portato dalla pagina raddrizzata alla foto [width] × [height] attraverso [map]: i
 * rettangoli dritti della pagina diventano i quadrilateri storti delle righe sulla foto.
 */
internal fun FluidRecognizedText.onPhoto(map: FluidScanPageMap, width: Int, height: Int): FluidRecognizedText {
  fun point(p: Offset): Offset = map.photoPoint(p.x, p.y).let { Offset(it.x, it.y) }
  fun quad(q: FluidTextQuad) = FluidTextQuad(point(q.topLeft), point(q.topRight), point(q.bottomRight), point(q.bottomLeft))
  return FluidRecognizedText(
    blocks.map { b ->
      FluidRecognizedBlock(b.lines.map { l ->
        FluidRecognizedLine(l.words.map { w -> w.copy(quad = quad(w.quad)) }, quad(l.quad))
      }, quad(b.quad))
    },
    width, height, language,
  )
}
