package dev.antigravity.fluidengine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.foundation.audio.FluidAudioLevel
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * L'**onda di un file** (F8.13): l'inviluppo che disegnano `FluidAudioWaveform` e `FluidAudioTimeline`, per un audio che
 * non è stato registrato adesso.
 *
 * - **Il WAV** si legge da sé, senza decodificatori ([fromWav]): PCM 8, 16, 24 o 32 bit, mono o stereo.
 * - **Gli altri** (M4A, MP3, OGG, FLAC…) si decodificano con `MediaExtractor` e `MediaCodec` di Android ([decode]), fuori
 *   dal thread principale. Un'ora di audio si decodifica in pochi secondi; per non rifarlo l'onda si **salva accanto al
 *   file** ([save], [sidecar]) e si rilegge subito ([load]). Il registratore la salva da solo.
 */
object FluidAudioEnvelopes {

  /** Il file dell'onda salvata accanto a [audio]: lo stesso nome con `.fluidwave` in coda. */
  fun sidecar(audio: File): File = File(audio.path + ".fluidwave")

  /** Salva [envelope] accanto ad [audio]. */
  @Throws(IOException::class)
  fun save(audio: File, envelope: FluidAudioEnvelope) {
    sidecar(audio).outputStream().buffered().use { envelope.write(it) }
  }

  /**
   * L'onda di [audio]: quella salvata accanto se c'è e non è più vecchia del file, se no decodificata (e salvata, se si
   * può scrivere nella cartella). Null se il file non si legge.
   */
  suspend fun load(audio: File, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope? =
    withContext(Dispatchers.IO) {
      val side = sidecar(audio)
      if (side.isFile && side.lastModified() >= audio.lastModified()) {
        runCatching { side.inputStream().buffered().use { FluidAudioEnvelope.read(it) } }.getOrNull()?.let { return@withContext it }
      }
      val decoded = if (audio.extension.equals("wav", ignoreCase = true)) {
        runCatching { audio.inputStream().buffered().use { fromWav(it, bucketMillis) } }.getOrNull()
      } else {
        decodeWith(bucketMillis) { setDataSource(audio.path) }
      }
      decoded?.also { runCatching { save(audio, it) } }
    }

  /** L'onda di un audio qualunque per [uri] (`content://`, `file://`, `android.resource://`), decodificato. */
  suspend fun decode(context: Context, uri: Uri, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope? =
    withContext(Dispatchers.IO) { decodeWith(bucketMillis) { setDataSource(context, uri, null) } }

  /**
   * L'onda di un WAV letto da [input]: PCM intero a 8, 16, 24 o 32 bit, da 1 a 8 canali (mescolati). Un WAV d'altro tipo
   * (in virgola mobile, compresso) è un'[IOException]: lo si passa a [decode].
   *
   * Le misure scritte nell'intestazione non si prendono sulla parola (R3): un pezzo `fmt ` fuori da 16–64 byte (il PCM ne
   * usa 16, il formato esteso 40), più di 8 canali (il 7.1) o una frequenza fuori da 1–768 kHz sono un'[IOException]
   * prima di allocare qualcosa, anche per un file rovinato o costruito apposta.
   */
  @Throws(IOException::class)
  fun fromWav(input: InputStream, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope {
    val head = ByteArray(12)
    readFully(input, head)
    if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") {
      throw IOException("non è un WAV")
    }
    var format: WavFormat? = null
    val chunk = ByteArray(8)
    while (true) {
      readFully(input, chunk)
      val id = String(chunk, 0, 4, Charsets.US_ASCII)
      val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
      if (id == "fmt ") {
        if (size < 16L || size > MaxWavFormatBytes) throw IOException("WAV con un formato di $size byte")
        val fmt = ByteArray(size.toInt())
        readFully(input, fmt)
        format = wavFormat(fmt)
        if (size % 2 == 1L) skipFully(input, 1L)
      } else if (id == "data") {
        val f = format ?: throw IOException("WAV senza formato valido")
        return readPcm(input, size, f.channels, f.rate, f.bits / 8, bucketMillis)
      } else {
        skipFully(input, size + (size and 1L))
      }
    }
  }

  /** Il tratto [fromMillis]..[toMillis] di [envelope], ripartendo da zero: l'onda di un file tagliato. */
  fun slice(envelope: FluidAudioEnvelope, fromMillis: Long, toMillis: Long): FluidAudioEnvelope {
    val bucket = envelope.bucketMillis
    val first = (fromMillis / bucket).toInt().coerceIn(0, envelope.size)
    val last = ((toMillis + bucket - 1) / bucket).toInt().coerceIn(first, envelope.size)
    return FluidAudioEnvelope.of(FloatArray(last - first) { envelope[first + it] }, bucket)
  }

  private fun readPcm(input: InputStream, size: Long, channels: Int, rate: Int, bytesPerSample: Int, bucketMillis: Int): FluidAudioEnvelope {
    val frameBytes = channels * bytesPerSample
    val bucketFrames = (rate.toLong() * bucketMillis / 1000L).toInt().coerceAtLeast(1)
    val levels = FloatArrayBuilder()
    val buffer = ByteArray(frameBytes * 4096)
    var remaining = if (size == 0xFFFFFFFFL || size == 0L) Long.MAX_VALUE else size
    var sum = 0.0
    var count = 0
    while (remaining > 0) {
      val want = minOf(buffer.size.toLong(), remaining).toInt() / frameBytes * frameBytes
      if (want <= 0) break
      val read = readUpTo(input, buffer, want)
      if (read <= 0) break
      remaining -= read
      var offset = 0
      while (offset + frameBytes <= read) {
        var mix = 0.0
        for (c in 0 until channels) mix += sample(buffer, offset + c * bytesPerSample, bytesPerSample)
        mix /= channels
        sum += mix * mix
        count++
        offset += frameBytes
        if (count == bucketFrames) {
          levels.add(FluidAudioLevel.fromRms(sqrt(sum / count).toFloat()))
          sum = 0.0
          count = 0
        }
      }
    }
    if (count > 0) levels.add(FluidAudioLevel.fromRms(sqrt(sum / count).toFloat()))
    return FluidAudioEnvelope.of(levels.toArray(), bucketMillis)
  }

  /** Un campione in frazione del fondo scala (-1..1). */
  private fun sample(buffer: ByteArray, at: Int, bytes: Int): Double = when (bytes) {
    1 -> ((buffer[at].toInt() and 0xFF) - 128) / 128.0
    2 -> ((buffer[at].toInt() and 0xFF) or (buffer[at + 1].toInt() shl 8)).toShort() / 32768.0
    3 -> (((buffer[at].toInt() and 0xFF) or ((buffer[at + 1].toInt() and 0xFF) shl 8) or (buffer[at + 2].toInt() shl 16))) / 8388608.0
    else -> ((buffer[at].toInt() and 0xFF) or ((buffer[at + 1].toInt() and 0xFF) shl 8) or
      ((buffer[at + 2].toInt() and 0xFF) shl 16) or (buffer[at + 3].toInt() shl 24)) / 2147483648.0
  }

  private suspend fun decodeWith(bucketMillis: Int, open: MediaExtractor.() -> Unit): FluidAudioEnvelope? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      extractor.open()
      val track = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: return null
      extractor.selectTrack(track)
      val format = extractor.getTrackFormat(track)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
      val decoder = MediaCodec.createDecoderByType(mime)
      codec = decoder
      decoder.configure(format, null, null, 0)
      decoder.start()
      var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      var bucketFrames = (rate.toLong() * bucketMillis / 1000L).toInt().coerceAtLeast(1)
      val levels = FloatArrayBuilder()
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var sum = 0.0
      var count = 0
      while (true) {
        kotlin.coroutines.coroutineContext.ensureActive()
        if (!inputDone) {
          val index = decoder.dequeueInputBuffer(10_000L)
          if (index >= 0) {
            val buffer = decoder.getInputBuffer(index)!!
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
              decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val out = decoder.dequeueOutputBuffer(info, 10_000L)
        if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          val actual = decoder.outputFormat
          rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          bucketFrames = (rate.toLong() * bucketMillis / 1000L).toInt().coerceAtLeast(1)
        } else if (out >= 0) {
          val buffer = decoder.getOutputBuffer(out)
          if (buffer != null && info.size > 0) {
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            val pcm = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            while (pcm.remaining() >= channels) {
              var mix = 0.0
              for (c in 0 until channels) mix += pcm.get() / 32768.0
              mix /= channels
              sum += mix * mix
              count++
              if (count == bucketFrames) {
                levels.add(FluidAudioLevel.fromRms(sqrt(sum / count).toFloat()))
                sum = 0.0
                count = 0
              }
            }
          }
          decoder.releaseOutputBuffer(out, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
      }
      if (count > 0) levels.add(FluidAudioLevel.fromRms(sqrt(sum / count).toFloat()))
      return FluidAudioEnvelope.of(levels.toArray(), bucketMillis)
    } catch (error: Exception) {
      if (error is kotlinx.coroutines.CancellationException) throw error
      return null
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      extractor.release()
    }
  }

  private fun readFully(input: InputStream, into: ByteArray) {
    var at = 0
    while (at < into.size) {
      val n = input.read(into, at, into.size - at)
      if (n < 0) throw IOException("file troncato")
      at += n
    }
  }

  private fun readUpTo(input: InputStream, into: ByteArray, want: Int): Int {
    var at = 0
    while (at < want) {
      val n = input.read(into, at, want - at)
      if (n < 0) break
      at += n
    }
    return at
  }

  private fun skipFully(input: InputStream, count: Long) {
    var left = count
    while (left > 0) {
      val n = input.skip(left)
      if (n <= 0) {
        if (input.read() < 0) throw IOException("file troncato")
        left--
      } else {
        left -= n
      }
    }
  }
}

/** Quanti canali al più ha un WAV che si legge o si taglia: 8 (il 7.1). Oltre è un'[IOException] (R3). */
internal const val MaxWavChannels: Int = 8

/**
 * Quanto è lungo al più il pezzo `fmt ` di un WAV: 64 byte. Il PCM ne usa 16, il formato esteso (`WAVE_FORMAT_EXTENSIBLE`)
 * 40; un pezzo più lungo non è un WAV che sappiamo leggere (R3).
 */
internal const val MaxWavFormatBytes: Int = 64

/** La frequenza più bassa e la più alta di un WAV che si legge o si taglia: 1 kHz e 768 kHz (R3). */
internal const val MinWavRate: Int = 1_000
internal const val MaxWavRate: Int = 768_000

/** Il formato di un WAV che l'engine sa leggere e tagliare: PCM intero, coi suoi canali, frequenza e bit. */
internal class WavFormat(val channels: Int, val rate: Int, val bits: Int, val blockAlign: Int)

/**
 * Il formato dal corpo del pezzo `fmt ` di un WAV ([fmt], almeno 16 byte): PCM intero (formato 1, o esteso con PCM
 * dentro), 8/16/24/32 bit, da 1 a [MaxWavChannels] canali, a una frequenza fra [MinWavRate] e [MaxWavRate]. Ogni altra
 * cosa è un'[IOException] (R3).
 */
internal fun wavFormat(fmt: ByteArray): WavFormat {
  if (fmt.size < 16) throw IOException("WAV con il formato troncato")
  val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
  val tag = b.short.toInt() and 0xFFFF
  val channels = b.short.toInt() and 0xFFFF
  val rate = b.int
  b.int
  val blockAlign = b.short.toInt() and 0xFFFF
  val bits = b.short.toInt() and 0xFFFF
  // 1 = PCM; 0xFFFE = esteso: il tipo vero sono i primi due byte del sottoformato (al byte 24), 1 per il PCM intero.
  val pcm = tag == 1 || (tag == 0xFFFE && fmt.size >= 26 && (b.getShort(24).toInt() and 0xFFFF) == 1)
  if (!pcm) throw IOException("WAV non PCM (formato $tag)")
  if (channels !in 1..MaxWavChannels || rate !in MinWavRate..MaxWavRate || bits !in intArrayOf(8, 16, 24, 32)) {
    throw IOException("WAV senza formato valido ($channels canali, $rate Hz, $bits bit)")
  }
  return WavFormat(channels, rate, bits, blockAlign)
}

/** Un array di float che cresce, senza scatole. */
private class FloatArrayBuilder {
  private var values = FloatArray(1024)
  private var size = 0

  fun add(value: Float) {
    if (size == values.size) values = values.copyOf(values.size * 2)
    values[size++] = value
  }

  fun toArray(): FloatArray = values.copyOf(size)
}

/**
 * Il **taglio** di un file registrato fra due tempi (F8.13): quello che il registratore fa ai silenzi in testa e in coda.
 * Il WAV si taglia copiando i byte; l'AAC in M4A copiando i pacchetti con `MediaExtractor` e `MediaMuxer`, senza
 * ricodificare (un pacchetto AAC è 21 ms a 48 kHz: il taglio cade al pacchetto più vicino). Il file si sostituisce solo a
 * taglio riuscito.
 *
 * **Il WAV di un'altra app** (R3) si taglia se è uno di quelli che l'engine sa leggere ([FluidAudioEnvelopes.fromWav]):
 * PCM intero a 8, 16, 24 o 32 bit, da 1 a 8 canali, con i pezzi in qualsiasi ordine (`LIST`, `fact`, `bext`, `JUNK`
 * prima o dopo i dati). Il file tagliato tiene il formato com'era (lo stesso pezzo `fmt `, per primo), i dati del tratto e
 * i pezzi di testo (R3-M1b, scelta di Alessio): i titoli e le note (`LIST`/`INFO`) e i tag `id3 `, prima o dopo i dati
 * come nel file di partenza. Il blocco della radio `bext` resta se è coerente col taglio: la sua ora d'inizio si sposta al
 * primo campione tenuto, e la loudness della versione 2, che misurava il file intero, si toglie. Non passano i pezzi che
 * parlano di tempi del file intero: `fact` (la durata), `cue ` e le sue etichette (`LIST`/`adtl`), `smpl`, `iXML`, `_PMX`.
 * Un WAV d'altro tipo (in virgola mobile, compresso) o rovinato non si taglia: falso, e il file resta com'era.
 */
object FluidAudioTrim {

  /** Taglia [file] a [fromMillis]..[toMillis]. Vero se il file ora è tagliato; falso se non si è potuto (resta com'era). */
  fun trim(file: File, format: FluidRecordingFormat, fromMillis: Long, toMillis: Long): Boolean {
    if (toMillis <= fromMillis) return false
    val temporary = File(file.path + ".taglio")
    val done = runCatching {
      when (format) {
        FluidRecordingFormat.Wav -> trimWav(file, temporary, fromMillis, toMillis)
        FluidRecordingFormat.Aac -> trimMp4(file, temporary, fromMillis, toMillis)
      }
    }.getOrDefault(false)
    if (!done) {
      temporary.delete()
      return false
    }
    if (!file.delete() || !temporary.renameTo(file)) {
      temporary.delete()
      return false
    }
    return true
  }

  private fun trimWav(source: File, target: File, fromMillis: Long, toMillis: Long): Boolean {
    RandomAccessFile(source, "r").use { input ->
      val length = input.length()
      if (length < 12L) return false
      val head = ByteArray(12)
      input.readFully(head)
      if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return false
      // R3: i pezzi si cercano camminando, come in `fromWav` (un `LIST` o un `bext` prima dei dati sono normali fuori
      // dall'engine), invece di leggere formato e misure a posti fissi. R3-M1b: si cammina anche dopo i dati, dove molti
      // programmi scrivono i titoli, e si tengono i pezzi di testo ([kept]).
      var fmt: ByteArray? = null
      var format: WavFormat? = null
      var dataStart = -1L
      var dataSize = 0L
      val before = ArrayList<WavPiece>()
      val after = ArrayList<WavPiece>()
      var bext: Long = -1L
      var bextSize = 0L
      var at = 12L
      val chunk = ByteArray(8)
      while (at + 8L <= length) {
        input.seek(at)
        input.readFully(chunk)
        val id = String(chunk, 0, 4, Charsets.US_ASCII)
        val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val body = at + 8L
        if (dataStart < 0L) {
          if (id == "fmt ") {
            if (size < 16L || size > MaxWavFormatBytes || body + size > length) return false
            val bytes = ByteArray(size.toInt())
            input.readFully(bytes)
            format = runCatching { wavFormat(bytes) }.getOrNull() ?: return false
            fmt = bytes
          } else if (id == "data") {
            if (format == null) return false
            dataStart = body
            // Una misura assente (0, o tutti 1 di chi scrive in diretta) o più lunga del file: i byte che ci sono davvero,
            // e dopo non c'è altro.
            val available = length - body
            if (size == 0L || size == 0xFFFFFFFFL || size >= available) {
              dataSize = available
              break
            }
            dataSize = size
          } else if (body + size <= length) {
            if (id == "bext") {
              // Uno solo: un secondo blocco della radio non è coerente con niente.
              if (bext < 0L) {
                bext = body
                bextSize = size
                before += WavPiece(id, body, size)
              }
            } else if (kept(input, id, body, size)) {
              before += WavPiece(id, body, size)
            }
          }
        } else {
          // Dopo i dati: un pezzo che dice di essere più lungo del file è la coda rovinata, e si lascia.
          if (body + size > length) break
          if (kept(input, id, body, size)) after += WavPiece(id, body, size)
        }
        at = body + size + (size and 1L)
      }
      val f = format ?: return false
      val formatBytes = fmt ?: return false
      if (dataStart < 0L) return false
      val frame = f.channels.toLong() * (f.bits / 8)
      if (f.blockAlign.toLong() != frame) return false
      val frames = dataSize / frame
      val first = framesAt(fromMillis, f.rate).coerceAtMost(frames)
      val last = framesAt(toMillis, f.rate).coerceAtMost(frames)
      if (last <= first) return false
      val bytes = (last - first) * frame
      // `bext` passa solo se resta coerente col taglio ([trimmedBext]); se no si lascia, e il taglio si fa lo stesso.
      val bextBytes = if (bext >= 0L) trimmedBext(input, bext, bextSize, first) else null
      val keptBefore = before.filter { it.id != "bext" || bextBytes != null }
      val formatPad = formatBytes.size and 1
      var riff = 4L + 8L + formatBytes.size + formatPad + 8L + bytes + (bytes and 1L)
      for (piece in keptBefore + after) riff += 8L + piece.size + (piece.size and 1L)
      if (riff > 0xFFFFFFFFL) return false
      RandomAccessFile(target, "rw").use { out ->
        out.setLength(0L)
        val header = ByteBuffer.allocate(12 + 8 + formatBytes.size + formatPad).order(ByteOrder.LITTLE_ENDIAN)
          .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(riff.toInt())
          .put("WAVE".toByteArray(Charsets.US_ASCII))
          .put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(formatBytes.size).put(formatBytes)
        if (formatPad == 1) header.put(0.toByte())
        out.write(header.array())
        // Il formato per primo (i lettori vecchi lo vogliono lì), poi i pezzi tenuti nell'ordine del file: quelli di prima
        // dei dati, i dati del tratto, quelli di dopo.
        val buffer = ByteArray(64 * 1024)
        for (piece in keptBefore) {
          if (piece.id == "bext") writePiece(out, piece.id, bextBytes!!) else if (!copyPiece(input, out, piece, buffer)) return false
        }
        out.write(pieceHeader("data", bytes))
        if (!copyRange(input, out, dataStart + first * frame, bytes, buffer)) return false
        // Un pezzo di lunghezza dispari ha un byte di riempimento (RIFF).
        if (bytes and 1L == 1L) out.write(0)
        for (piece in after) if (!copyPiece(input, out, piece, buffer)) return false
      }
    }
    return true
  }

  /** Un pezzo del WAV di partenza: il nome, dove comincia il corpo, quanto è lungo. */
  private class WavPiece(val id: String, val body: Long, val size: Long)

  /**
   * I pezzi di **testo** che passano nel file tagliato (R3-M1b, scelta di Alessio): i titoli e le note (`LIST` di tipo
   * `INFO`: titolo, autore, commento, data…) e i tag `id3 ` (titolo, artista, album; un `CHAP` o un `SYLT` dentro, rari,
   * restano coi tempi del file intero). Gli altri no, perché parlano di tempi del file intero: `fact` (la durata), `cue `
   * e il loro `LIST` di tipo `adtl` (segnaposto ed etichette), `smpl`, `iXML` e `_PMX` (ore e durate di produzione), e i
   * pezzi che non conosciamo. `bext` ha la sua regola ([trimmedBext]).
   */
  private fun kept(input: RandomAccessFile, id: String, body: Long, size: Long): Boolean = when (id) {
    "LIST" -> size >= 4L && ByteArray(4).also { input.seek(body); input.readFully(it) }.contentEquals(InfoType)
    "id3 ", "ID3 " -> true
    else -> false
  }

  private val InfoType = "INFO".toByteArray(Charsets.US_ASCII)

  /**
   * Il blocco della radio (`bext`, EBU Tech 3285) per il file tagliato, o null se non resta coerente. È coerente se ha
   * tutti i campi (602 byte, al più 1 MiB con la storia): l'ora d'inizio (`TimeReference`, in campioni dalla mezzanotte) si
   * sposta di [firstFrame], perché ora il primo campione è quello; descrizione, autore, data, UMID e storia restano. Nella
   * versione 2 la loudness misurata (cinque valori) parlava del file intero: se c'è, il blocco torna alla versione 1, dove
   * quei byte sono riservati e a zero. Un blocco più corto o troppo lungo non passa.
   */
  private fun trimmedBext(input: RandomAccessFile, body: Long, size: Long, firstFrame: Long): ByteArray? {
    if (size < BextFixedBytes || size > MaxBextBytes) return null
    val block = ByteArray(size.toInt())
    input.seek(body)
    input.readFully(block)
    val b = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
    val start = b.getLong(BextTimeReference)
    val moved = start + firstFrame
    if (java.lang.Long.compareUnsigned(moved, start) < 0) return null
    b.putLong(BextTimeReference, moved)
    val version = b.getShort(BextVersion).toInt() and 0xFFFF
    if (version >= 2 && (BextLoudness until BextLoudness + 10).any { block[it] != 0.toByte() }) {
      b.putShort(BextVersion, 1)
      for (i in BextLoudness until BextLoudness + 10) block[i] = 0
    }
    return block
  }

  private const val BextFixedBytes = 602L
  private const val MaxBextBytes = 1L shl 20
  private const val BextTimeReference = 338
  private const val BextVersion = 346
  private const val BextLoudness = 412

  private fun pieceHeader(id: String, size: Long): ByteArray =
    ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put(id.toByteArray(Charsets.US_ASCII)).putInt(size.toInt()).array()

  private fun writePiece(out: RandomAccessFile, id: String, body: ByteArray) {
    out.write(pieceHeader(id, body.size.toLong()))
    out.write(body)
    if (body.size and 1 == 1) out.write(0)
  }

  private fun copyPiece(input: RandomAccessFile, out: RandomAccessFile, piece: WavPiece, buffer: ByteArray): Boolean {
    out.write(pieceHeader(piece.id, piece.size))
    if (!copyRange(input, out, piece.body, piece.size, buffer)) return false
    if (piece.size and 1L == 1L) out.write(0)
    return true
  }

  /** Copia [count] byte da [from]; falso se il file finisce prima. */
  private fun copyRange(input: RandomAccessFile, out: RandomAccessFile, from: Long, count: Long, buffer: ByteArray): Boolean {
    input.seek(from)
    var left = count
    while (left > 0) {
      val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
      if (n <= 0) return false
      out.write(buffer, 0, n)
      left -= n
    }
    return true
  }

  /** Il fotogramma del millisecondo [millis] a [rate] Hz, per difetto, senza traboccare (anche con `Long.MAX_VALUE`). */
  private fun framesAt(millis: Long, rate: Int): Long = millis.coerceIn(0L, Long.MAX_VALUE / rate) * rate / 1000L

  private fun trimMp4(source: File, target: File, fromMillis: Long, toMillis: Long): Boolean {
    val extractor = MediaExtractor()
    val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var started = false
    try {
      extractor.setDataSource(source.path)
      val track = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: return false
      extractor.selectTrack(track)
      val out = muxer.addTrack(extractor.getTrackFormat(track))
      muxer.start()
      started = true
      extractor.seekTo(fromMillis * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
      val buffer = ByteBuffer.allocate(256 * 1024)
      val info = MediaCodec.BufferInfo()
      val startUs = fromMillis * 1000L
      val endUs = toMillis * 1000L
      var wrote = false
      while (true) {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        val time = extractor.sampleTime
        if (time > endUs) break
        if (time >= startUs) {
          info.set(0, size, time - startUs, extractor.sampleFlags)
          muxer.writeSampleData(out, buffer, info)
          wrote = true
        }
        extractor.advance()
      }
      return wrote
    } finally {
      runCatching { if (started) muxer.stop() }
      runCatching { muxer.release() }
      extractor.release()
    }
  }
}
