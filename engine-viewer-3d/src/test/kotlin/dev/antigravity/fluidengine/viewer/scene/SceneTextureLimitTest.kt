package dev.antigravity.fluidengine.viewer.scene

import android.os.ParcelFileDescriptor
import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * R2 · Le texture di un modello si misurano prima di darle a Filament: una PNG di pochi byte che dichiara 16384×16384
 * (un GB di pixel alla decodifica) è «troppo grande», a fianco di un glTF, incorporata in un data URI o dentro il GLB
 * costruito da un OBJ; e lo è anche la somma di tante texture grandi. Le texture vere del corpo di prova si aprono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SceneTextureLimitTest {
  @Test
  fun aSidecarTextureDeclaringAGigapixelIsTooLarge() = runBlocking {
    val failure = failure(source("tessuto.gltf") { path -> if (path == "tessuto.png") png(16_384, 16_384).inputStream() else resource(path) })
    assertEquals(FluidViewerFailure.TooLarge, failure?.failure)
  }

  @Test
  fun anEmbeddedDataUriTextureIsMeasuredToo() = runBlocking {
    val json = text("tessuto.gltf").replace("\"tessuto.png\"", "\"data:image/png;base64,${base64(png(16_384, 16_384))}\"")
    assertEquals(FluidViewerFailure.TooLarge, failure(source("tessuto.gltf", json.toByteArray()))?.failure)
  }

  @Test
  fun anObjTextureInsideTheConvertedGlbIsMeasuredToo() = runBlocking {
    val failure = failure(source("piramide.obj") { path -> if (path.endsWith(".png")) png(16_384, 16_384).inputStream() else resource(path) })
    assertEquals(FluidViewerFailure.TooLarge, failure?.failure)
  }

  @Test
  fun manyLargeTexturesTogetherAreTooLarge() = runBlocking {
    // Cinque 4096×4096: ognuna va bene, insieme sono 84 milioni di pixel.
    val images = (0 until 5).joinToString(",") { "{\"uri\":\"data:image/png;base64,${base64(png(4096, 4096))}\"}" }
    val json = text("tessuto.gltf").replace(Regex("\"images\"\\s*:\\s*\\[[^\\]]*\\]"), "\"images\":[$images]")
    assertEquals(FluidViewerFailure.TooLarge, failure(source("tessuto.gltf", json.toByteArray()))?.failure)
  }

  @Test
  fun theCorpusTexturesStillOpen() = runBlocking {
    assertEquals(1, openFluidSceneModel(source("tessuto.gltf")).stats.textures)
    assertEquals(1, openFluidSceneModel(source("piramide.obj")).stats.textures)
    // Una sola texture grande ma legale (4096×4096) si apre.
    val json = text("tessuto.gltf").replace("\"tessuto.png\"", "\"data:image/png;base64,${base64(png(4096, 4096))}\"")
    assertTrue(openFluidSceneModel(source("tessuto.gltf", json.toByteArray())).stats.triangles > 0)
  }

  private suspend fun failure(source: FluidSceneSource): FluidViewerException? =
    runCatching { openFluidSceneModel(source) }.exceptionOrNull() as FluidViewerException?

  /** Una PNG minima: la firma e l'intestazione IHDR con le misure dichiarate, senza pixel. */
  private fun png(width: Int, height: Int): ByteArray = ByteBuffer.allocate(8 + 25 + 12).order(ByteOrder.BIG_ENDIAN).apply {
    put(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
    putInt(13).put("IHDR".toByteArray()).putInt(width).putInt(height)
    put(8).put(6).put(0).put(0).put(0).putInt(0)
    putInt(0).put("IEND".toByteArray()).putInt(0)
  }.array()

  private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

  private fun text(name: String) = checkNotNull(resource(name)).use { it.readBytes().decodeToString() }

  private fun source(name: String, input: ByteArray? = null, sidecar: (String) -> InputStream? = ::resource) =
    object : FluidSceneSource {
      override val name = name
      override suspend fun open(): InputStream = input?.inputStream() ?: checkNotNull(resource(name))
      override suspend fun openRelative(path: String): InputStream? = sidecar(path)
      override suspend fun seekable(): ParcelFileDescriptor = error("Non serve un descrittore")
    }

  private fun resource(name: String): InputStream? = javaClass.getResourceAsStream("/corpus/$name")
}
