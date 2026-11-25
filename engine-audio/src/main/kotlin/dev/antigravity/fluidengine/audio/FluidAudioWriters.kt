package dev.antigravity.fluidengine.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.antigravity.fluidengine.foundation.audio.FluidRecordingQuality
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** In che file finisce una registrazione. Nuovo nella 4.0 (F8.13). */
enum class FluidRecordingFormat(
  /** L'estensione del file, senza punto. */
  val extension: String,
  /** Il tipo MIME del file. */
  val mimeType: String,
) {
  /**
   * AAC-LC in un M4A, col codificatore di Android (`MediaCodec` e `MediaMuxer`): è la qualità «Compressa» di Memo Vocali
   * (AAC). Un'ora a 96 kbit/s mono, la qualità di serie, è circa 44 MB.
   */
  Aac("m4a", "audio/mp4"),

  /** PCM 16 bit in un WAV: la qualità «Senza perdita». Un'ora a 48 kHz mono è 346 MB. */
  Wav("wav", "audio/wav"),
  ;

  companion object {
    /** Il formato di una preimpostazione: WAV per quella senza perdita, AAC per le altre. */
    fun of(quality: FluidRecordingQuality): FluidRecordingFormat = if (quality.lossless) Wav else Aac
  }
}

/**
 * Chi scrive i campioni nel file, dal thread della cattura. [write] riceve i campioni in ordine e senza buchi: la pausa
 * non scrive niente, così il file è uno solo e continuo, come in Memo Vocali.
 */
internal interface FluidAudioWriter {
  @Throws(IOException::class)
  fun write(samples: ShortArray, count: Int)

  /** Chiude il file. Dopo, il file è completo e leggibile. */
  @Throws(IOException::class)
  fun finish()

  /** Butta tutto (una registrazione scartata): il file si cancella. */
  fun abandon()
}

/**
 * Il WAV scritto mentre si registra: l'intestazione di 44 byte con le misure a zero, i campioni in coda, e le due misure
 * scritte a [finish]. Un buffer di byte riusato: nessuna allocazione per blocco.
 */
internal class FluidWavWriter(private val file: File, private val sampleRate: Int) : FluidAudioWriter {
  private val out = RandomAccessFile(file, "rw")
  private var bytes = ByteArray(0)
  private var dataBytes = 0L

  init {
    out.setLength(0L)
    out.write(wavHeader(0, sampleRate))
  }

  override fun write(samples: ShortArray, count: Int) {
    if (count <= 0) return
    if (bytes.size < count * 2) bytes = ByteArray(count * 2)
    for (i in 0 until count) {
      val s = samples[i].toInt()
      bytes[2 * i] = (s and 0xFF).toByte()
      bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
    }
    out.write(bytes, 0, count * 2)
    dataBytes += count * 2
  }

  override fun finish() {
    val data = dataBytes.coerceAtMost(Int.MAX_VALUE.toLong() - 36).toInt()
    out.seek(0L)
    out.write(wavHeader(data, sampleRate))
    out.close()
  }

  override fun abandon() {
    runCatching { out.close() }
    file.delete()
  }
}

/** L'intestazione di un WAV PCM 16 bit mono, little-endian (mai `DataOutputStream`, che è big-endian). */
internal fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int = 1): ByteArray =
  ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    .put("RIFF".toByteArray(Charsets.US_ASCII))
    .putInt(36 + dataSize)
    .put("WAVE".toByteArray(Charsets.US_ASCII))
    .put("fmt ".toByteArray(Charsets.US_ASCII))
    .putInt(16)
    .putShort(1)
    .putShort(channels.toShort())
    .putInt(sampleRate)
    .putInt(sampleRate * channels * 2)
    .putShort((channels * 2).toShort())
    .putShort(16)
    .put("data".toByteArray(Charsets.US_ASCII))
    .putInt(dataSize)
    .array()

/**
 * L'AAC in M4A scritto mentre si registra: il codificatore di Android in modo sincrono sul thread della cattura, e il
 * contenitore MP4 di `MediaMuxer`. I tempi dei pacchetti vengono dal conto dei campioni scritti, quindi una pausa non
 * lascia buchi nel file.
 */
internal class FluidAacWriter(
  private val file: File,
  private val sampleRate: Int,
  bitRate: Int,
) : FluidAudioWriter {
  private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
  private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
  private val info = MediaCodec.BufferInfo()
  private var track = -1
  private var muxing = false
  private var samples = 0L

  init {
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, InputBytes)
    }
    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    codec.start()
  }

  override fun write(samples: ShortArray, count: Int) {
    var offset = 0
    while (offset < count) {
      val index = codec.dequeueInputBuffer(TimeoutUs)
      if (index < 0) {
        drain(endOfStream = false)
        continue
      }
      val buffer = codec.getInputBuffer(index) ?: continue
      buffer.clear()
      buffer.order(ByteOrder.LITTLE_ENDIAN)
      val room = minOf(count - offset, buffer.remaining() / 2)
      buffer.asShortBuffer().put(samples, offset, room)
      val time = this.samples * 1_000_000L / sampleRate
      codec.queueInputBuffer(index, 0, room * 2, time, 0)
      this.samples += room
      offset += room
      drain(endOfStream = false)
    }
  }

  override fun finish() {
    // Il segnale di fine, poi tutto quello che il codificatore ha ancora dentro.
    var index = codec.dequeueInputBuffer(TimeoutUs)
    var tries = 0
    while (index < 0 && tries++ < 50) {
      drain(endOfStream = false)
      index = codec.dequeueInputBuffer(TimeoutUs)
    }
    if (index >= 0) codec.queueInputBuffer(index, 0, 0, samples * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    drain(endOfStream = true)
    runCatching { codec.stop() }
    codec.release()
    if (muxing) {
      muxer.stop()
    }
    muxer.release()
    if (!muxing) throw IOException("il codificatore AAC non ha prodotto niente")
  }

  override fun abandon() {
    runCatching { codec.stop() }
    runCatching { codec.release() }
    runCatching { if (muxing) muxer.stop() }
    runCatching { muxer.release() }
    file.delete()
  }

  private fun drain(endOfStream: Boolean) {
    var idle = 0
    while (true) {
      val index = codec.dequeueOutputBuffer(info, if (endOfStream) TimeoutUs else 0L)
      when {
        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
          if (!endOfStream || ++idle > 100) return
        }
        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          track = muxer.addTrack(codec.outputFormat)
          muxer.start()
          muxing = true
        }
        index >= 0 -> {
          val data = codec.getOutputBuffer(index)
          val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
          if (data != null && info.size > 0 && !config && muxing) {
            data.position(info.offset)
            data.limit(info.offset + info.size)
            muxer.writeSampleData(track, data, info)
          }
          codec.releaseOutputBuffer(index, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
      }
    }
  }

  private companion object {
    const val TimeoutUs = 10_000L
    const val InputBytes = 16 * 1024
  }
}
