package dev.antigravity.fluidengine.foundation.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * L'**inviluppo** di un audio (F8.13): quanto è forte, un valore ogni [bucketMillis] millisecondi, da 0 (silenzio) a 1.
 * È quello che disegnano l'onda da scorrere (`FluidAudioWaveform`) e l'onda della registrazione (`FluidAudioTimeline`) di
 * `engine-ui`, e quello che scrive il registratore di `engine-audio` mentre registra.
 *
 * **I valori sono già pronti da disegnare**: la scala non lineare di [FluidAudioLevel] (in decibel, col ginocchio che
 * non satura) si applica una volta, quando il valore nasce, e non a ogni fotogramma. Un'app che ha le sue ampiezze le
 * passa a [of] già normalizzate fra 0 e 1, come chiede la scheda dell'idea `onda-audio`.
 *
 * Due forme, con la stessa lettura:
 *  - **ferma** ([of], [read]): un file già registrato. Le domande «qual è il picco fra questi due tempi» ([peak]) vanno
 *    su una piramide di massimi costruita alla prima domanda, quindi un'onda di due ore mostrata intera in 90 barre non
 *    scorre 360 000 valori a ogni fotogramma;
 *  - **che cresce** ([FluidGrowingAudioEnvelope]): la registrazione in corso. Un solo scrittore (il thread della
 *    cattura) e lettori su altri thread (il disegno): i valori stanno in blocchi che non si spostano mai, e [size] è
 *    l'ultima cosa che lo scrittore pubblica.
 *
 * [bucketMillis] di serie è [DefaultBucketMillis], 20 ms: l'onda della registrazione di Memo Vocali di iOS 26 ha una
 * barra ogni 40 ms a 100 punti al secondo (misurata sulla guida dell'iPhone, vedi `FluidAudioTimelineDefaults`), e con
 * due valori per barra lo zoom fino a 200 punti al secondo ha ancora un valore per barra.
 */
abstract class FluidAudioEnvelope internal constructor(
  /** Quanti millisecondi copre un valore. Sempre positivo. */
  val bucketMillis: Int,
) {
  init {
    require(bucketMillis > 0) { "bucketMillis deve essere positivo: $bucketMillis" }
  }

  /** Quanti valori ci sono adesso. Per un inviluppo che cresce, è l'ultima cosa pubblicata dallo scrittore. */
  abstract val size: Int

  /** Il valore [index], fra 0 e 1. Fuori da `0 until size` è 0. */
  abstract operator fun get(index: Int): Float

  /** Quanto dura l'audio descritto: [size] valori di [bucketMillis] ms. */
  val durationMillis: Long get() = size.toLong() * bucketMillis

  /**
   * Il picco fra [fromMillis] (compreso) e [toMillis] (escluso): il massimo dei valori che toccano l'intervallo. Fuori
   * dall'audio è 0. È la domanda di una barra dell'onda, che copre di solito più valori.
   */
  open fun peak(fromMillis: Long, toMillis: Long): Float {
    val count = size
    if (count == 0 || toMillis <= fromMillis) return 0f
    val first = firstIndex(fromMillis, count)
    val last = lastIndex(toMillis, count)
    var best = 0f
    for (i in first..last) best = max(best, get(i))
    return best
  }

  /**
   * Il primo indice toccato da un intervallo che comincia a [fromMillis]. Con [lastIndex]: se il primo è oltre l'ultimo
   * l'intervallo è fuori dall'audio. Interi e non un `IntRange`: il disegno fa questa domanda per ogni barra di ogni
   * fotogramma, e non deve allocare.
   */
  protected fun firstIndex(fromMillis: Long, count: Int): Int =
    (fromMillis.coerceAtLeast(0L) / bucketMillis).coerceAtMost(count.toLong()).toInt()

  /** L'ultimo indice toccato: un intervallo che finisce esattamente su un confine non tocca il valore dopo. */
  protected fun lastIndex(toMillis: Long, count: Int): Int =
    ((toMillis - 1L).coerceAtLeast(0L) / bucketMillis).coerceAtMost(count - 1L).toInt()

  /** Una copia dei valori di adesso. */
  fun toFloatArray(): FloatArray {
    val count = size
    return FloatArray(count) { get(it) }
  }

  /**
   * Scrive l'inviluppo in [output], nel formato che [read] rilegge: quattro byte di firma, il passo, il numero dei
   * valori e un byte per valore (256 livelli: più di quanti pixel abbia una barra). Due ore di registrazione sono
   * 360 KB: il registratore lo salva accanto al file, e l'onda si mostra subito senza decodificare l'audio.
   */
  @Throws(IOException::class)
  fun write(output: OutputStream) {
    val count = size
    val data = DataOutputStream(output)
    data.writeInt(Magic)
    data.writeInt(bucketMillis)
    data.writeInt(count)
    val bytes = ByteArray(count) { (get(it).coerceIn(0f, 1f) * 255f).roundToInt().toByte() }
    data.write(bytes)
    data.flush()
  }

  companion object {
    /** Il passo di serie: 20 ms. Vedi l'intestazione. */
    const val DefaultBucketMillis: Int = 20

    /** «FAE1»: la firma del formato di [write]. */
    private const val Magic: Int = 0x46414531

    /** L'inviluppo vuoto. */
    val Empty: FluidAudioEnvelope = of(FloatArray(0))

    /**
     * Un inviluppo fermo con i [levels] dati, già fra 0 e 1 (quelli fuori si tengono dentro, un NaN è 0). L'array si
     * copia: l'app può riusare il suo.
     */
    fun of(levels: FloatArray, bucketMillis: Int = DefaultBucketMillis): FluidAudioEnvelope =
      StaticAudioEnvelope(FloatArray(levels.size) { i -> levels[i].let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) } }, bucketMillis)

    /** Rilegge un inviluppo scritto con [write]. Un file d'altro tipo o troncato è un'[IOException]. */
    @Throws(IOException::class)
    fun read(input: InputStream): FluidAudioEnvelope {
      val data = DataInputStream(input)
      if (data.readInt() != Magic) throw IOException("non è un inviluppo di FluidAudioEnvelope")
      val bucket = data.readInt()
      val count = data.readInt()
      if (bucket <= 0 || count < 0) throw IOException("inviluppo rovinato: passo $bucket, valori $count")
      val bytes = ByteArray(count)
      data.readFully(bytes)
      return StaticAudioEnvelope(FloatArray(count) { (bytes[it].toInt() and 0xFF) / 255f }, bucket)
    }
  }
}

/**
 * L'inviluppo fermo, con la piramide dei massimi per [peak]: il livello `k` ha un valore ogni `2^k` del livello zero.
 * Si costruisce alla prima domanda lunga (meno della metà della memoria dei valori, una volta).
 */
private class StaticAudioEnvelope(private val levels: FloatArray, bucketMillis: Int) : FluidAudioEnvelope(bucketMillis) {

  @Volatile private var pyramid: Array<FloatArray>? = null

  override val size: Int get() = levels.size

  override fun get(index: Int): Float = if (index in levels.indices) levels[index] else 0f

  override fun peak(fromMillis: Long, toMillis: Long): Float {
    val count = levels.size
    if (count == 0 || toMillis <= fromMillis) return 0f
    val firstIndex = firstIndex(fromMillis, count)
    val lastIndex = lastIndex(toMillis, count)
    if (firstIndex > lastIndex) return 0f
    val span = lastIndex - firstIndex + 1
    if (span <= DirectScan) {
      var best = 0f
      for (i in firstIndex..lastIndex) best = max(best, levels[i])
      return best
    }
    val floors = pyramid ?: buildPyramid().also { pyramid = it }
    // Il livello in cui l'intervallo copre ancora una manciata di valori: i bordi si leggono a quel livello, quindi la
    // barra può allargarsi di mezzo valore del livello per lato. Per un'onda è invisibile; il centro è esatto.
    var level = 0
    while (level < floors.size && (span shr (level + 1)) >= DirectScan / 2) level++
    if (level == 0) {
      var best = 0f
      for (i in firstIndex..lastIndex) best = max(best, levels[i])
      return best
    }
    val row = floors[level - 1]
    val first = firstIndex shr level
    val last = min(lastIndex shr level, row.lastIndex)
    var best = 0f
    for (i in first..last) best = max(best, row[i])
    return best
  }

  private fun buildPyramid(): Array<FloatArray> {
    val rows = ArrayList<FloatArray>()
    var below = levels
    while (below.size > 1) {
      val row = FloatArray((below.size + 1) / 2) { i ->
        val a = below[2 * i]
        val b = if (2 * i + 1 < below.size) below[2 * i + 1] else 0f
        max(a, b)
      }
      rows += row
      below = row
    }
    return rows.toTypedArray()
  }

  private companion object {
    /** Fino a quanti valori si scorre direttamente: sopra si sale nella piramide. */
    const val DirectScan = 64
  }
}

/**
 * L'inviluppo che cresce mentre si registra: [append] dal thread della cattura, [get], [size] e [peak] da qualunque
 * thread. I valori stanno in blocchi da [ChunkSize] che non si spostano mai (niente array che si rialloca sotto un
 * lettore), e un secondo livello tiene il massimo di ogni [CoarseSpan] valori, così il picco di un tratto lungo non
 * scorre tutto. Un blocco nuovo nasce ogni 82 secondi di registrazione, sul thread della cattura: il disegno non alloca.
 *
 * Il tetto è [MaxChunks] blocchi: 46 ore a 20 ms. Oltre, [append] non scrive più (l'audio continua, l'onda no).
 */
class FluidGrowingAudioEnvelope(bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis) : FluidAudioEnvelope(bucketMillis) {

  private val chunks = arrayOfNulls<FloatArray>(MaxChunks)
  private val coarse = arrayOfNulls<FloatArray>(MaxChunks)

  @Volatile private var count: Int = 0

  override val size: Int get() = count

  override fun get(index: Int): Float {
    if (index < 0 || index >= count) return 0f
    return chunks[index ushr ChunkShift]?.get(index and ChunkMask) ?: 0f
  }

  /** Aggiunge un valore, tenuto fra 0 e 1 (un NaN è 0). Un solo scrittore alla volta. */
  fun append(level: Float) {
    val index = count
    val chunk = index ushr ChunkShift
    if (chunk >= MaxChunks) return
    val values = chunks[chunk] ?: FloatArray(ChunkSize).also { chunks[chunk] = it }
    val peaks = coarse[chunk] ?: FloatArray(ChunkSize / CoarseSpan).also { coarse[chunk] = it }
    val clean = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
    values[index and ChunkMask] = clean
    val slot = (index and ChunkMask) / CoarseSpan
    peaks[slot] = if ((index and ChunkMask) % CoarseSpan == 0) clean else max(peaks[slot], clean)
    // La pubblicazione: un lettore che vede il conto nuovo vede anche il valore (e il blocco) scritti prima.
    count = index + 1
  }

  /** Riparte da zero (una registrazione scartata). I blocchi restano allocati per la prossima. */
  fun clear() {
    count = 0
  }

  /** Una copia ferma di adesso, per salvarla o per mostrarla quando la registrazione è finita. */
  fun snapshot(): FluidAudioEnvelope = FluidAudioEnvelope.of(toFloatArray(), bucketMillis)

  override fun peak(fromMillis: Long, toMillis: Long): Float {
    val count = size
    if (count == 0 || toMillis <= fromMillis) return 0f
    val last = lastIndex(toMillis, count)
    var best = 0f
    var i = firstIndex(fromMillis, count)
    while (i <= last) {
      // Dove un blocco grosso sta tutto dentro, si legge il suo massimo.
      if (i % CoarseSpan == 0 && i + CoarseSpan - 1 <= last) {
        best = max(best, coarse[i ushr ChunkShift]?.get((i and ChunkMask) / CoarseSpan) ?: 0f)
        i += CoarseSpan
      } else {
        best = max(best, get(i))
        i++
      }
    }
    return best
  }

  companion object {
    /** Valori per blocco: 4096, cioè 82 secondi a 20 ms. */
    const val ChunkSize: Int = 1 shl 12
    private const val ChunkShift = 12
    private const val ChunkMask = ChunkSize - 1

    /** Quanti valori riassume un massimo del secondo livello. */
    const val CoarseSpan: Int = 64

    /** Il tetto dei blocchi: 2048, cioè più di 46 ore a 20 ms. */
    const val MaxChunks: Int = 2048
  }
}
