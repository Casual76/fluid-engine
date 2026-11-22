package dev.antigravity.fluidengine.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * GIF e WebP: misura, animazione, fotogrammi e giri dai primi byte. I file si costruiscono qui byte per byte, come
 * li scrive un encoder vero (GIF89a con l'estensione NETSCAPE, WebP con `VP8X`/`ANIM`/`ANMF`, `VP8 ` e `VP8L`).
 */
class FluidImageHeaderTest {

  private fun ByteArrayOutputStream.u8(vararg values: Int) = values.forEach { write(it and 0xFF) }
  private fun ByteArrayOutputStream.le16(value: Int) = u8(value, value shr 8)
  private fun ByteArrayOutputStream.le24(value: Int) = u8(value, value shr 8, value shr 16)
  private fun ByteArrayOutputStream.le32(value: Int) = u8(value, value shr 8, value shr 16, value shr 24)
  private fun ByteArrayOutputStream.ascii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

  /** Una GIF di [frames] fotogrammi 1×1 su una tela [width]×[height]; [loops] `null` senza NETSCAPE. */
  private fun gif(width: Int, height: Int, frames: Int, loops: Int? = 0, trailer: Boolean = true, localTable: Boolean = false): ByteArray {
    val out = ByteArrayOutputStream()
    out.ascii("GIF89a")
    out.le16(width); out.le16(height)
    out.u8(0x80 or 0x00, 0, 0) // tavolozza globale di 2 colori
    out.u8(0, 0, 0, 255, 255, 255)
    if (loops != null) {
      out.u8(0x21, 0xFF, 11); out.ascii("NETSCAPE2.0"); out.u8(3, 1); out.le16(loops); out.u8(0)
    }
    repeat(frames) {
      out.u8(0x21, 0xF9, 4, 0x04, 10, 0, 0, 0) // controllo grafico: 100 ms
      out.u8(0x2C); out.le16(0); out.le16(0); out.le16(1); out.le16(1)
      if (localTable) { out.u8(0x80); out.u8(0, 0, 0, 255, 0, 0) } else out.u8(0)
      out.u8(2, 2, 0x44, 0x01, 0) // LZW: un pixel
      out.u8(0x21, 0xFE, 3); out.ascii("ciao".take(3)); out.u8(0) // un commento fra i fotogrammi
    }
    if (trailer) out.u8(0x3B)
    return out.toByteArray()
  }

  private fun chunk(out: ByteArrayOutputStream, fourcc: String, payload: ByteArray) {
    out.ascii(fourcc); out.le32(payload.size); out.write(payload)
    if (payload.size % 2 == 1) out.u8(0)
  }

  private fun riff(body: ByteArrayOutputStream.() -> Unit): ByteArray {
    val inner = ByteArrayOutputStream().apply { ascii("WEBP"); body() }.toByteArray()
    return ByteArrayOutputStream().apply { ascii("RIFF"); le32(inner.size); write(inner) }.toByteArray()
  }

  private fun animatedWebp(width: Int, height: Int, frames: Int, loops: Int = 0) = riff {
    chunk(this, "VP8X", ByteArrayOutputStream().apply { u8(0x02 or 0x10, 0, 0, 0); le24(width - 1); le24(height - 1) }.toByteArray())
    chunk(this, "ANIM", ByteArrayOutputStream().apply { le32(0); le16(loops) }.toByteArray())
    repeat(frames) { chunk(this, "ANMF", ByteArray(17) { 0 }) }
  }

  private fun lossyWebp(width: Int, height: Int) = riff {
    chunk(this, "VP8 ", ByteArrayOutputStream().apply { u8(0x10, 0x02, 0x00, 0x9D, 0x01, 0x2A); le16(width); le16(height); u8(0, 0, 0) }.toByteArray())
  }

  private fun losslessWebp(width: Int, height: Int) = riff {
    val bits = (width - 1) or ((height - 1) shl 14)
    chunk(this, "VP8L", ByteArrayOutputStream().apply { u8(0x2F); le32(bits) }.toByteArray())
  }

  @Test
  fun `an animated gif with the netscape loop`() {
    val header = FluidImageHeader.read(gif(320, 240, frames = 3, loops = 0))!!
    assertEquals("gif", header.format)
    assertEquals(320, header.width)
    assertEquals(240, header.height)
    assertTrue(header.animated)
    assertEquals(3, header.frameCount)
    assertEquals(0, header.loopCount)
    assertEquals(320f / 240, header.aspectRatio!!, 0.0001f)
  }

  @Test
  fun `a gif with one frame is still`() {
    val header = FluidImageHeader.read(gif(16, 16, frames = 1, loops = null, localTable = true))!!
    assertFalse(header.animated)
    assertEquals(1, header.frameCount)
    assertNull(header.loopCount)
  }

  @Test
  fun `a truncated gif knows it is animated after two frames but not how many`() {
    val full = gif(64, 48, frames = 5, loops = 2)
    val cut = full.copyOf(full.size - 30)
    val header = FluidImageHeader.read(cut)!!
    assertTrue(header.animated)
    assertNull(header.frameCount)
    assertEquals(2, header.loopCount)
    // Senza la fine e con un fotogramma solo: ferma, conteggio ignoto.
    val single = gif(64, 48, frames = 1, trailer = false)
    assertFalse(FluidImageHeader.read(single)!!.animated)
    assertNull(FluidImageHeader.read(single)!!.frameCount)
  }

  @Test
  fun `an animated webp counts its frames and loops`() {
    val header = FluidImageHeader.read(animatedWebp(400, 300, frames = 4, loops = 3))!!
    assertEquals("webp", header.format)
    assertEquals(400, header.width)
    assertEquals(300, header.height)
    assertTrue(header.animated)
    assertEquals(4, header.frameCount)
    assertEquals(3, header.loopCount)
  }

  @Test
  fun `a webp with the animation flag but one frame is still`() {
    val header = FluidImageHeader.read(animatedWebp(10, 10, frames = 1))!!
    assertFalse(header.animated)
    assertEquals(1, header.frameCount)
  }

  @Test
  fun `a truncated animated webp is animated with an unknown count`() {
    val full = animatedWebp(100, 50, frames = 6)
    val header = FluidImageHeader.read(full.copyOf(60))!!
    assertTrue(header.animated)
    assertNull(header.frameCount)
    assertEquals(100, header.width)
  }

  @Test
  fun `still webp lossy and lossless give their size`() {
    val lossy = FluidImageHeader.read(lossyWebp(1920, 1080))!!
    assertEquals(1920 to 1080, lossy.width to lossy.height)
    assertFalse(lossy.animated)
    assertEquals(1, lossy.frameCount)
    val lossless = FluidImageHeader.read(losslessWebp(512, 384))!!
    assertEquals(512 to 384, lossless.width to lossless.height)
    assertFalse(lossless.animated)
  }

  @Test
  fun `other bytes are not a gif or a webp`() {
    assertNull(FluidImageHeader.read(ByteArray(0)))
    assertNull(FluidImageHeader.read("GIF8".toByteArray()))
    assertNull(FluidImageHeader.read(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0)))
    assertNull(FluidImageHeader.read("RIFF\u0010\u0000\u0000\u0000WAVEfmt ".toByteArray(Charsets.ISO_8859_1)))
  }

  /** Tre fotogrammi 64×48 scritti da Pillow 13 (libwebp, il codificatore GIF di Pillow): file veri, non costruiti qui. */
  private fun resource(name: String): ByteArray =
    requireNotNull(javaClass.getResourceAsStream("/viewer/$name")) { "manca $name" }.use { it.readBytes() }

  @Test
  fun `files written by a real encoder`() {
    val gif = FluidImageHeader.read(resource("anim.gif"))!!
    assertEquals(listOf<Any?>("gif", 64, 48, true, 3, 0), listOf(gif.format, gif.width, gif.height, gif.animated, gif.frameCount, gif.loopCount))
    val webp = FluidImageHeader.read(resource("anim.webp"))!!
    assertEquals(listOf<Any?>("webp", 64, 48, true, 3, 0), listOf(webp.format, webp.width, webp.height, webp.animated, webp.frameCount, webp.loopCount))
    val still = FluidImageHeader.read(resource("ferma.webp"))!!
    assertEquals(listOf<Any?>("webp", 64, 48, false, 1, null), listOf(still.format, still.width, still.height, still.animated, still.frameCount, still.loopCount))
  }

  @Test
  fun `gif and webp resolve to the image preset from bytes alone`() {
    assertEquals(FluidViewerPreset.Image, FluidViewerType.of(null, "senza-nome", gif(2, 2, frames = 2)).preset)
    assertEquals(FluidViewerPreset.Image, FluidViewerType.of("application/octet-stream", "x", animatedWebp(2, 2, 2)).preset)
  }
}
