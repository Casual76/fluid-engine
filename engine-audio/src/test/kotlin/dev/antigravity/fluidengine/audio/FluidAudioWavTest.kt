package dev.antigravity.fluidengine.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * R3 (review del 27/11/2026, difetti 1 e 9) · I WAV che non ha scritto l'engine: 24 e 32 bit, più canali, pezzi extra
 * prima dei dati (`LIST`, `fact`, `bext`, `JUNK`), e intestazioni rovinate. I file nascono qui, byte per byte, come li
 * scrivono le altre app: nessun file preso da fuori.
 */
class FluidAudioWavTest {

  private lateinit var folder: File

  @Before
  fun setUp() {
    folder = Files.createTempDirectory("fluid-wav").toFile()
  }

  @After
  fun tearDown() {
    folder.deleteRecursively()
  }

  // --- Come si scrive un WAV ------------------------------------------------------------------------------------------

  /** Un pezzo RIFF: nome, misura, corpo e il byte di riempimento se la misura e' dispari. */
  private fun chunk(id: String, body: ByteArray, declared: Long = body.size.toLong()): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(id.toByteArray(Charsets.US_ASCII))
    out.write(le32(declared))
    out.write(body)
    if (body.size % 2 == 1) out.write(0)
    return out.toByteArray()
  }

  private fun wav(vararg chunks: ByteArray): ByteArray {
    val inner = ByteArrayOutputStream()
    inner.write("WAVE".toByteArray(Charsets.US_ASCII))
    chunks.forEach { inner.write(it) }
    val out = ByteArrayOutputStream()
    out.write("RIFF".toByteArray(Charsets.US_ASCII))
    out.write(le32(inner.size().toLong()))
    out.write(inner.toByteArray())
    return out.toByteArray()
  }

  /** Il corpo di `fmt `: [tag] 1 = PCM, 3 = virgola mobile; con [extensible] il formato esteso da 40 byte. */
  private fun fmt(tag: Int, channels: Int, rate: Int, bits: Int, extensible: Int? = null): ByteArray {
    val block = channels * bits / 8
    val b = ByteBuffer.allocate(if (extensible != null) 40 else 16).order(ByteOrder.LITTLE_ENDIAN)
      .putShort((if (extensible != null) 0xFFFE else tag).toShort())
      .putShort(channels.toShort())
      .putInt(rate)
      .putInt(rate * block)
      .putShort(block.toShort())
      .putShort(bits.toShort())
    if (extensible != null) {
      b.putShort(22).putShort(bits.toShort()).putInt(if (channels == 2) 3 else 4)
      // Il sottoformato: i primi due byte sono il tipo (1 PCM, 3 virgola mobile), poi la coda fissa del GUID.
      b.putShort(extensible.toShort()).put(byteArrayOf(0, 0, 0, 0, 0x10, 0, 0x80.toByte(), 0, 0, 0xAA.toByte(), 0, 0x38, 0x9B.toByte(), 0x71))
    }
    return b.array()
  }

  /** Campioni riconoscibili: il fotogramma i, canale c, vale i * 7 + c (nei bit che ci sono). */
  private fun samples(frames: Int, channels: Int, bits: Int): ByteArray {
    val bytes = bits / 8
    val out = ByteArray(frames * channels * bytes)
    var at = 0
    for (i in 0 until frames) for (c in 0 until channels) {
      val v = i * 7 + c
      for (k in 0 until bytes) out[at++] = (v ushr (8 * k)).toByte()
    }
    return out
  }

  private fun list(): ByteArray {
    val info = ByteArrayOutputStream()
    info.write("INFO".toByteArray(Charsets.US_ASCII))
    info.write(chunk("INAM", "Lezione di storia\u0000".toByteArray(Charsets.US_ASCII)))
    info.write(chunk("IART", "Registratore di un'altra app\u0000".toByteArray(Charsets.US_ASCII)))
    return info.toByteArray()
  }

  private fun le32(v: Long): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()

  private fun file(name: String, bytes: ByteArray): File = File(folder, name).also { it.writeBytes(bytes) }

  /** I pezzi di un WAV: nome -> corpo, nell'ordine. */
  private fun chunks(bytes: ByteArray): List<Pair<String, ByteArray>> {
    assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
    assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
    assertEquals("la misura RIFF e' il file meno 8 byte", bytes.size - 8L, le(bytes, 4))
    val out = ArrayList<Pair<String, ByteArray>>()
    var at = 12
    while (at + 8 <= bytes.size) {
      val size = le(bytes, at + 4).toInt()
      out += String(bytes, at, 4, Charsets.US_ASCII) to bytes.copyOfRange(at + 8, at + 8 + size)
      at += 8 + size + (size and 1)
    }
    assertEquals("nessun byte in piu' in fondo", bytes.size, at)
    return out
  }

  private fun le(bytes: ByteArray, at: Int): Long = ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

  // --- Difetto 1: il taglio ---------------------------------------------------------------------------------------------

  @Test
  fun aWavFromAnotherAppAt24BitWithAListBeforeTheDataIsCutKeepingItsFormat() {
    // 24 bit, stereo, 8 kHz, un secondo, coi titoli in `LIST` fra formato e dati (come li scrive un registratore qualunque).
    val format = fmt(1, channels = 2, rate = 8_000, bits = 24)
    val data = samples(frames = 8_000, channels = 2, bits = 24)
    val source = file("lezione.wav", wav(chunk("fmt ", format), chunk("LIST", list()), chunk("data", data)))

    assertTrue("il taglio riesce", FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 250L, 500L))

    val cut = chunks(source.readBytes())
    // R3-M1b (Alessio, risposta b): anche i titoli passano.
    assertEquals(listOf("fmt ", "LIST", "data"), cut.map { it.first })
    assertArrayEquals("il formato resta quello vero (24 bit, due canali)", format, cut[0].second)
    assertArrayEquals("i titoli restano", list(), cut[1].second)
    // Fotogrammi da 6 byte: da 2000 a 4000, i campioni giusti.
    assertArrayEquals("i dati sono quelli del tratto", data.copyOfRange(2_000 * 6, 4_000 * 6), cut[2].second)
    assertFalse("il temporaneo non resta", File(source.path + ".taglio").exists())
    // E il file tagliato si rilegge: 250 ms.
    val envelope = FluidAudioEnvelopes.fromWav(ByteArrayInputStream(source.readBytes()), bucketMillis = 50)
    assertEquals(5, envelope.size)
  }

  @Test
  fun aWavAt32BitWithBextFactAndJunkIsCutAndAnOddCutIsPadded() {
    // 32 bit mono, i pezzi della radio (`bext`) prima del formato, `fact` e un `JUNK` dispari fra formato e dati.
    val format = fmt(1, channels = 1, rate = 1_000, bits = 32)
    val data = samples(frames = 1_000, channels = 1, bits = 32)
    val source = file(
      "radio.wav",
      wav(chunk("bext", ByteArray(602)), chunk("fmt ", format), chunk("fact", le32(1_000)), chunk("JUNK", ByteArray(7)), chunk("data", data)),
    )
    assertTrue(FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 100L, 103L))
    val cut = chunks(source.readBytes())
    // R3-M1b: `bext` resta, con l'ora d'inizio spostata di 100 campioni; `fact` (la durata) e `JUNK` no.
    assertEquals(listOf("fmt ", "bext", "data"), cut.map { it.first })
    assertArrayEquals(format, cut[0].second)
    assertArrayEquals(ByteArray(602).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(338, 100L) }, cut[1].second)
    assertArrayEquals(data.copyOfRange(100 * 4, 103 * 4), cut[2].second)

    // 8 bit mono, tre fotogrammi: un pezzo dispari, col suo byte di riempimento.
    val small = fmt(1, channels = 1, rate = 1_000, bits = 8)
    val bytes = samples(frames = 500, channels = 1, bits = 8)
    val odd = file("dispari.wav", wav(chunk("fmt ", small), chunk("data", bytes)))
    assertTrue(FluidAudioTrim.trim(odd, FluidRecordingFormat.Wav, 10L, 13L))
    val oddCut = chunks(odd.readBytes())
    assertArrayEquals(bytes.copyOfRange(10, 13), oddCut[1].second)
    assertEquals("il riempimento c'e'", 12 + 8 + 16 + 8 + 3 + 1, odd.length().toInt())
  }

  @Test
  fun theExtensibleFormatWithPcmInsideIsCutToo() {
    val format = fmt(1, channels = 2, rate = 48_000, bits = 24, extensible = 1)
    val data = samples(frames = 4_800, channels = 2, bits = 24)
    val source = file("esteso.wav", wav(chunk("fmt ", format), chunk("data", data)))
    assertTrue(FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 0L, 50L))
    val cut = chunks(source.readBytes())
    assertArrayEquals(format, cut[0].second)
    assertArrayEquals(data.copyOfRange(0, 2_400 * 6), cut[1].second)
  }

  @Test
  fun aWavThatCannotBeCutIsLeftExactlyAsItWas() {
    val cases = mapOf(
      // In virgola mobile: non e' PCM intero.
      "float.wav" to wav(chunk("fmt ", fmt(3, channels = 1, rate = 8_000, bits = 32)), chunk("data", samples(800, 1, 32))),
      // Esteso, ma con la virgola mobile dentro.
      "float-esteso.wav" to wav(chunk("fmt ", fmt(1, channels = 2, rate = 8_000, bits = 32, extensible = 3)), chunk("data", samples(800, 2, 32))),
      // ADPCM IMA: compresso, quattro bit.
      "adpcm.wav" to wav(chunk("fmt ", ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putShort(0x11).putShort(1).putInt(8_000).putInt(4_055).putShort(256).putShort(4).putShort(2).putShort(505).array()), chunk("data", ByteArray(2_560))),
      // I dati prima del formato.
      "senza-formato.wav" to wav(chunk("data", samples(800, 1, 16)), chunk("fmt ", fmt(1, channels = 1, rate = 8_000, bits = 16))),
      // Niente dati.
      "senza-dati.wav" to wav(chunk("fmt ", fmt(1, channels = 1, rate = 8_000, bits = 16)), chunk("LIST", list())),
      // Non e' un WAV.
      "testo.wav" to "Questo non e' un file audio, ma e' lungo abbastanza da sembrarlo.".toByteArray(),
    )
    for ((name, bytes) in cases) {
      val source = file(name, bytes)
      assertFalse("$name: non si taglia", FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 10L, 60L))
      assertArrayEquals("$name: il file resta com'era", bytes, source.readBytes())
      assertFalse("$name: il temporaneo non resta", File(source.path + ".taglio").exists())
    }
  }

  @Test
  fun aCutBeyondTheEndOrADataSizeLongerThanTheFileStaysInsideTheBytes() {
    // La misura dei dati dice 1 MB, ma il file (un'app morta registrando) ne ha 16 000: si taglia cio' che c'e'.
    val format = fmt(1, channels = 1, rate = 8_000, bits = 16)
    val data = samples(frames = 8_000, channels = 1, bits = 16)
    val source = file("troncato.wav", wav(chunk("fmt ", format), chunk("data", data, declared = 1_000_000L)))
    assertTrue(FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 900L, Long.MAX_VALUE))
    val cut = chunks(source.readBytes())
    assertArrayEquals(data.copyOfRange(7_200 * 2, 8_000 * 2), cut[1].second)
    // Tutto oltre la fine: niente da tenere, falso.
    val before = source.readBytes()
    assertFalse(FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 5_000L, 6_000L))
    assertArrayEquals(before, source.readBytes())
  }

  // --- R3-M1b: i titoli e il blocco della radio passano (Alessio, risposta b) ----------------------------------------

  /** Un `LIST` di tipo [type] con i pezzi [items] (nome -> testo, chiuso dallo zero). */
  private fun infoList(type: String, vararg items: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(type.toByteArray(Charsets.US_ASCII))
    for ((id, text) in items) out.write(chunk(id, (text + "\u0000").toByteArray(Charsets.ISO_8859_1)))
    return out.toByteArray()
  }

  /** Il blocco `bext` della radio (EBU Tech 3285): descrizione, ora d'inizio in campioni, versione, loudness, storia. */
  private fun bext(description: String, timeReference: Long, version: Int, loudness: ShortArray? = null, history: String = ""): ByteArray {
    val h = history.toByteArray(Charsets.US_ASCII)
    val b = ByteBuffer.allocate(602 + h.size).order(ByteOrder.LITTLE_ENDIAN)
    b.put(description.toByteArray(Charsets.US_ASCII))
    b.position(256)
    b.put("Pampa Notes".toByteArray(Charsets.US_ASCII))
    b.position(320)
    b.put("2026-11-27".toByteArray(Charsets.US_ASCII)).put("06:30:00".toByteArray(Charsets.US_ASCII))
    b.putLong(338, timeReference)
    b.putShort(346, version.toShort())
    for (i in 0 until 64) b.put(348 + i, (i + 1).toByte())
    loudness?.forEachIndexed { i, v -> b.putShort(412 + 2 * i, v) }
    b.position(602)
    b.put(h)
    return b.array()
  }

  /** Cosa legge del file il lettore WAV del JDK (`javax.sound.sampled`): un lettore qualunque, non quello dell'engine. */
  private class JdkWav(val channels: Int, val bits: Int, val encoding: String, val frames: Long, val bytes: ByteArray)

  /** Per riflessione: le prove compilano contro `android.jar`, che `javax.sound` non lo ha; la JVM delle prove sì. */
  private fun jdkRead(file: File): JdkWav {
    val system = Class.forName("javax.sound.sampled.AudioSystem")
    val stream = system.getMethod("getAudioInputStream", File::class.java).invoke(null, file) as java.io.InputStream
    stream.use {
      val format = stream.javaClass.getMethod("getFormat").invoke(stream)
      val type = format.javaClass
      return JdkWav(
        channels = type.getMethod("getChannels").invoke(format) as Int,
        bits = type.getMethod("getSampleSizeInBits").invoke(format) as Int,
        encoding = type.getMethod("getEncoding").invoke(format).toString(),
        frames = stream.javaClass.getMethod("getFrameLength").invoke(stream) as Long,
        bytes = stream.readBytes(),
      )
    }
  }

  @Test
  fun theTitlesAndTheTextTagsBeforeAndAfterTheDataStayWhenCutting() {
    // 16 bit stereo a 44,1 kHz, un secondo. Prima dei dati i titoli e le note (`LIST`/`INFO`); dopo, altri titoli, i
    // segnaposto (`cue ` e le loro etichette in `LIST`/`adtl`), l'XML di produzione (`iXML`) e i tag `id3 `.
    val format = fmt(1, channels = 2, rate = 44_100, bits = 16)
    val data = samples(frames = 44_100, channels = 2, bits = 16)
    val before = infoList("INFO", "INAM" to "Lezione di storia", "ICMT" to "Le note del prof: ripassare il Medioevo")
    val after = infoList("INFO", "IART" to "Registratore di un'altra app", "ICRD" to "2026-11-27")
    val cue = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(1).putInt(22_050)
      .put("data".toByteArray(Charsets.US_ASCII)).putInt(0).putInt(0).putInt(22_050).array()
    val adtl = "adtl".toByteArray(Charsets.US_ASCII) +
      chunk("labl", ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array() + "Metà\u0000".toByteArray(Charsets.ISO_8859_1))
    val ixml = "<BWFXML><SPEED><TIMESTAMP_SAMPLES_SINCE_MIDNIGHT_LO>0</TIMESTAMP_SAMPLES_SINCE_MIDNIGHT_LO></SPEED></BWFXML>".toByteArray(Charsets.US_ASCII)
    val id3 = "ID3".toByteArray(Charsets.US_ASCII) + byteArrayOf(4, 0, 0, 0, 0, 0, 7) + "TIT2abc".toByteArray(Charsets.US_ASCII)
    val source = file(
      "lezione-con-titoli.wav",
      wav(
        chunk("fmt ", format), chunk("LIST", before), chunk("data", data),
        chunk("LIST", after), chunk("cue ", cue), chunk("LIST", adtl), chunk("iXML", ixml), chunk("id3 ", id3),
      ),
    )

    assertTrue("il taglio riesce", FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 250L, 500L))

    val cut = chunks(source.readBytes())
    assertEquals(
      "i titoli prima e dopo i dati e i tag restano, al loro posto; segnaposto, etichette e XML (parlano di tempi) no",
      listOf("fmt ", "LIST", "data", "LIST", "id3 "),
      cut.map { it.first },
    )
    assertArrayEquals(format, cut[0].second)
    assertArrayEquals("i titoli e le note di prima", before, cut[1].second)
    assertArrayEquals("i dati del tratto", data.copyOfRange(11_025 * 4, 22_050 * 4), cut[2].second)
    assertArrayEquals("i titoli di dopo", after, cut[3].second)
    assertArrayEquals("i tag", id3, cut[4].second)

    // Si rilegge con l'engine: 250 ms...
    assertEquals(5, FluidAudioEnvelopes.fromWav(ByteArrayInputStream(source.readBytes()), bucketMillis = 50).size)
    // ...e con un lettore qualunque: formato e campioni giusti.
    val jdk = jdkRead(source)
    assertEquals(2, jdk.channels)
    assertEquals(16, jdk.bits)
    assertEquals("PCM_SIGNED", jdk.encoding)
    assertEquals(11_025L, jdk.frames)
    assertArrayEquals(data.copyOfRange(11_025 * 4, 22_050 * 4), jdk.bytes)
  }

  @Test
  fun aCutAt24BitWithTitlesReadsBackWithAnyReader() {
    val format = fmt(1, channels = 2, rate = 8_000, bits = 24)
    val data = samples(frames = 8_000, channels = 2, bits = 24)
    val source = file("titoli-24.wav", wav(chunk("fmt ", format), chunk("LIST", list()), chunk("data", data), chunk("LIST", list())))
    assertTrue(FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 100L, 600L))
    assertEquals(listOf("fmt ", "LIST", "data", "LIST"), chunks(source.readBytes()).map { it.first })
    val jdk = jdkRead(source)
    assertEquals(24, jdk.bits)
    assertEquals(4_000L, jdk.frames)
    assertArrayEquals(data.copyOfRange(800 * 6, 4_800 * 6), jdk.bytes)
  }

  @Test
  fun theBroadcastBlockFollowsTheCutOrLeaves() {
    // 48 kHz mono; il primo campione e' alle 01:00:00 (172 800 000 campioni dalla mezzanotte). Si taglia da 500 ms.
    val format = fmt(1, channels = 1, rate = 48_000, bits = 16)
    val data = samples(frames = 48_000, channels = 1, bits = 16)
    val hour = 3_600L * 48_000L
    fun cutWith(name: String, block: ByteArray): List<Pair<String, ByteArray>> {
      val source = file(name, wav(chunk("bext", block), chunk("fmt ", format), chunk("data", data)))
      assertTrue("$name: il taglio riesce", FluidAudioTrim.trim(source, FluidRecordingFormat.Wav, 500L, 750L))
      return chunks(source.readBytes())
    }
    fun timeOf(block: ByteArray) = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).getLong(338)

    // Versione 1: resta com'era, con l'ora d'inizio del primo campione tenuto (24 000 campioni dopo).
    val v1 = bext("Intervista al sindaco", hour, version = 1)
    val keptV1 = cutWith("bext-v1.wav", v1)
    assertEquals(listOf("fmt ", "bext", "data"), keptV1.map { it.first })
    assertEquals("l'ora d'inizio segue il taglio", hour + 24_000L, timeOf(keptV1[1].second))
    assertArrayEquals("il resto e' identico", v1.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(338, hour + 24_000L) }, keptV1[1].second)

    // Versione 2 con la loudness misurata sul file intero: dopo il taglio non vale piu'. Il blocco torna alla versione 1
    // (dove quei byte sono riservati, a zero); descrizione, UMID e storia (dispari, col riempimento) restano.
    val v2 = bext("Intervista al sindaco", hour, version = 2, loudness = shortArrayOf(-2300, 700, -100, -1500, -1800), history = "A=PCM,F=48000,W=16,M=mono\r\n")
    val keptV2 = cutWith("bext-v2.wav", v2)
    val expected = v2.copyOf().also {
      val b = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
      b.putLong(338, hour + 24_000L)
      b.putShort(346, 1)
      for (i in 412 until 422) b.put(i, 0)
    }
    assertArrayEquals(expected, keptV2[1].second)

    // Un blocco troppo corto per avere i campi non e' coerente con niente: non passa, ma il taglio si fa.
    val short = cutWith("bext-corto.wav", ByteArray(100) { 7 })
    assertEquals(listOf("fmt ", "data"), short.map { it.first })
    assertArrayEquals(data.copyOfRange(24_000 * 2, 36_000 * 2), short[1].second)
  }

  // --- Difetto 9: le misure dell'intestazione ---------------------------------------------------------------------------

  /** [fromWav] deve dare un'IOException, non un'altra eccezione ne' un errore di memoria. */
  private fun assertRefused(what: String, bytes: ByteArray) {
    try {
      FluidAudioEnvelopes.fromWav(ByteArrayInputStream(bytes))
      fail("$what: letto come se fosse buono")
    } catch (expected: IOException) {
      // Giusto.
    } catch (other: Throwable) {
      fail("$what: ${other.javaClass.simpleName} invece di un'IOException")
    }
  }

  @Test
  fun absurdSizesInTheHeaderAreAnIOExceptionBeforeAnyAllocation() {
    val data = chunk("data", samples(800, 1, 16))
    // Un pezzo `fmt ` di 2 GB (la misura a 31 bit) e uno che come intero con segno e' negativo.
    assertRefused("fmt da 2 GB", wav(chunk("fmt ", fmt(1, 1, 8_000, 16), declared = 0x7FFF_FFF0L), data))
    assertRefused("fmt negativo", wav(chunk("fmt ", fmt(1, 1, 8_000, 16), declared = 0x8000_0010L), data))
    // Un pezzo `fmt ` troppo corto per avere i campi.
    assertRefused("fmt di 6 byte", wav(chunk("fmt ", ByteArray(6) { 1 }), data))
    // 30 000 canali: il buffer di lettura da solo sarebbe di centinaia di MB.
    assertRefused("30 000 canali", wav(chunk("fmt ", fmt(1, 30_000, 8_000, 8)), chunk("data", ByteArray(60_000))))
    // Una frequenza di 1 Hz farebbe un punto dell'onda per ogni campione.
    assertRefused("1 Hz", wav(chunk("fmt ", fmt(1, 1, 1, 8)), chunk("data", ByteArray(4_000))))
  }

  @Test
  fun aBrokenWavLoadsAsNullAndAGoodOneStillLoads() = runBlocking {
    val broken = file("rotto.wav", wav(chunk("fmt ", fmt(1, 1, 8_000, 16), declared = 0x8000_0010L), chunk("data", ByteArray(1_600))))
    assertNull(FluidAudioEnvelopes.load(broken))
    // Il formato esteso col PCM dentro e 6 canali (il 5.1) si legge.
    val surround = file("surround.wav", wav(chunk("fmt ", fmt(1, 6, 48_000, 16, extensible = 1)), chunk("data", samples(4_800, 6, 16))))
    assertEquals(5, FluidAudioEnvelopes.load(surround, bucketMillis = 20)!!.size)
  }
}
