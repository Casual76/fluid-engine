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
   * L'onda di un WAV letto da [input]: PCM intero a 8, 16, 24 o 32 bit, uno o più canali (mescolati). Un WAV d'altro
   * tipo (in virgola mobile, compresso) è un'[IOException]: lo si passa a [decode].
   */
  @Throws(IOException::class)
  fun fromWav(input: InputStream, bucketMillis: Int = FluidAudioEnvelope.DefaultBucketMillis): FluidAudioEnvelope {
    val head = ByteArray(12)
    readFully(input, head)
    if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") {
      throw IOException("non è un WAV")
    }
    var channels = 0
    var rate = 0
    var bits = 0
    val chunk = ByteArray(8)
    while (true) {
      readFully(input, chunk)
      val id = String(chunk, 0, 4, Charsets.US_ASCII)
      val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
      if (id == "fmt ") {
        val fmt = ByteArray(size.toInt())
        readFully(input, fmt)
        val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
        val tag = b.short.toInt() and 0xFFFF
        channels = b.short.toInt()
        rate = b.int
        b.int
        b.short
        bits = b.short.toInt()
        // 1 = PCM; 0xFFFE = esteso, che per i nostri casi porta PCM intero.
        if (tag != 1 && tag != 0xFFFE) throw IOException("WAV non PCM (formato $tag)")
        if (size % 2 == 1L) input.skip(1)
      } else if (id == "data") {
        if (channels <= 0 || rate <= 0 || bits !in intArrayOf(8, 16, 24, 32)) throw IOException("WAV senza formato valido")
        return readPcm(input, size, channels, rate, bits / 8, bucketMillis)
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
      val header = ByteArray(44)
      input.readFully(header)
      val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
      val channels = b.getShort(22).toInt()
      val rate = b.getInt(24)
      val bits = b.getShort(34).toInt()
      val dataSize = b.getInt(40).toLong() and 0xFFFFFFFFL
      val frame = channels * bits / 8
      if (frame <= 0 || rate <= 0) return false
      val first = (fromMillis * rate / 1000L) * frame
      val last = minOf((toMillis * rate / 1000L) * frame, dataSize)
      if (last <= first) return false
      RandomAccessFile(target, "rw").use { out ->
        out.setLength(0L)
        out.write(wavHeader((last - first).toInt(), rate, channels))
        input.seek(44L + first)
        val buffer = ByteArray(64 * 1024)
        var left = last - first
        while (left > 0) {
          val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
          if (n < 0) break
          out.write(buffer, 0, n)
          left -= n
        }
      }
    }
    return true
  }

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
