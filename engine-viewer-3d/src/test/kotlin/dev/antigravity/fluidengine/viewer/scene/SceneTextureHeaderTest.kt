package dev.antigravity.fluidengine.viewer.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** R2 · Le intestazioni delle texture che si misurano prima di Filament: JPEG, KTX2 e i tre WebP. */
class SceneTextureHeaderTest {
  private fun size(bytes: ByteArray): Pair<Long, Long>? = textureSize(TextureBytes(bytes, 0, bytes.size))?.let { it.width to it.height }

  @Test
  fun jpegSizeComesFromTheFirstFrameHeaderAfterOtherSegments() {
    val jpeg = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN).apply {
      put(0xFF.toByte()).put(0xD8.toByte())
      // APP0 di 16 byte, poi DHT (C4, non è un SOF), poi SOF2 progressivo.
      put(0xFF.toByte()).put(0xE0.toByte()).putShort(16).put(ByteArray(14))
      put(0xFF.toByte()).put(0xC4.toByte()).putShort(4).putShort(0)
      put(0xFF.toByte()).put(0xC2.toByte()).putShort(11).put(8).putShort(3000).putShort(4000).put(3)
    }.array()
    assertEquals(4000L to 3000L, size(jpeg))
    // Fino all'immagine senza un SOF: non si sa.
    assertNull(size(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 2)))
  }

  @Test
  fun ktx2CountsLayersAndFaces() {
    val ktx = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
      put(byteArrayOf(0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, 0xBB.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
      putInt(0).putInt(1).putInt(2048).putInt(1024).putInt(0).putInt(2).putInt(6).putInt(1)
    }.array()
    val size = textureSize(TextureBytes(ktx, 0, ktx.size))!!
    assertEquals(2048L, size.width)
    assertEquals(1024L, size.height)
    assertEquals(12L, size.count)
  }

  @Test
  fun webpLossyLosslessAndExtended() {
    fun riff(fourcc: String, body: ByteBuffer.() -> Unit) = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray()).putInt(32).put("WEBP".toByteArray()).put(fourcc.toByteArray()).putInt(20)
      body()
    }.array()
    val lossy = riff("VP8 ") { put(ByteArray(3)).put(0x9D.toByte()).put(0x01).put(0x2A).putShort(1920).putShort(1080) }
    assertEquals(1920L to 1080L, size(lossy))
    val lossless = riff("VP8L") { put(0x2F).putInt((1023) or (511 shl 14)) }
    assertEquals(1024L to 512L, size(lossless))
    val extended = riff("VP8X") {
      put(ByteArray(4))
      val w = 16383; val h = 8191
      put((w and 0xFF).toByte()).put((w shr 8 and 0xFF).toByte()).put((w shr 16).toByte())
      put((h and 0xFF).toByte()).put((h shr 8 and 0xFF).toByte()).put((h shr 16).toByte())
    }
    assertEquals(16384L to 8192L, size(extended))
  }

  @Test
  fun anythingElseIsUnknown() {
    assertNull(size("BM".toByteArray() + ByteArray(60)))
    assertNull(size(ByteArray(0)))
  }
}
