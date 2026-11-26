package dev.antigravity.fluidengine.search

import java.nio.ByteBuffer
import java.util.Base64

/**
 * Il modello del significato delle prove: i vettori **veri** di EmbeddingGemma 2 Text 270M (LiteRT-LM 0.18.1, CPU),
 * calcolati una volta sul PC per i pezzi dei documenti di prova e per le dieci domande, e salvati a 8 bit nelle risorse
 * (`fluid-search/meaning-embeddinggemma2-270m.tsv`). Un testo che non c'e' e' un errore: la prova non inventa vettori.
 */
class FixtureEmbedder : FluidTextEmbedder {
  override val space: FluidEmbeddingSpace = FluidEmbeddingSpace.EmbeddingGemma2

  /** I testi chiesti, in ordine (le prove guardano che cosa arriva al modello). */
  val asked = ArrayList<String>()

  private val vectors: Map<String, FloatArray> by lazy {
    val stream = FixtureEmbedder::class.java.classLoader!!.getResourceAsStream("fluid-search/meaning-embeddinggemma2-270m.tsv")
      ?: error("manca la risorsa dei vettori")
    stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
      lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
        val tab = line.lastIndexOf('\t')
        val text = line.substring(0, tab).replace("\\n", "\n")
        text to decode(line.substring(tab + 1))
      }
    }
  }

  /** Vero se il testo ha un vettore. */
  fun knows(text: String): Boolean = text in vectors

  override suspend fun embed(texts: List<String>): List<FloatArray> {
    asked += texts
    return texts.map { vectors[it] ?: error("nessun vettore per: $it") }
  }

  private fun decode(base64: String): FloatArray {
    val bytes = Base64.getDecoder().decode(base64)
    val buffer = ByteBuffer.wrap(bytes)
    val scale = buffer.float
    val v = FloatArray(bytes.size - 4) { bytes[4 + it] * scale }
    var s = 0.0
    for (x in v) s += x * x
    val inv = (1.0 / kotlin.math.sqrt(s)).toFloat()
    return FloatArray(v.size) { v[it] * inv }
  }
}
