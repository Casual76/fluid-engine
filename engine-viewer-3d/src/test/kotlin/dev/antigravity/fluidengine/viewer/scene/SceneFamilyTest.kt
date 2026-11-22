package dev.antigravity.fluidengine.viewer.scene

import android.os.ParcelFileDescriptor
import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SceneFamilyTest {
  @Test
  fun glbKeepsItsAnimationAndMaterials() {
    val stats = stats("anelli.glb")
    assertEquals((56 * 16 * 2 + 44 * 14 * 2).toLong(), stats.triangles)
    assertEquals(2, stats.materials)
    assertEquals(1, stats.animations)
    assertTrue(stats.width > stats.height)
  }

  @Test
  fun binaryAndAsciiStlAreConvertedWithTheSameBounds() {
    val binary = stats("piramide-binaria.stl")
    val ascii = stats("piramide-ascii.stl")
    assertEquals(6L, binary.triangles)
    assertEquals(18L, binary.vertices)
    assertEquals(binary, ascii)
    assertEquals(1.4, binary.height, 0.001)
  }

  @Test
  fun objLoadsMtlAndTextureFromItsFolder() {
    val stats = stats("piramide.obj")
    assertEquals(6L, stats.triangles)
    assertEquals(2, stats.materials)
    assertEquals(1, stats.textures)
  }

  @Test
  fun gltfRequestsOnlyDeclaredSidecars() = runBlocking {
    val requested = mutableListOf<String>()
    val stats = openFluidSceneModel(source("tessuto.gltf", sidecar = {
      requested += it
      resource(it)
    })).stats
    assertEquals(setOf("tessuto.bin", "tessuto.png"), requested.toSet())
    assertEquals(1, stats.textures)
    assertEquals(768L, stats.triangles)
  }

  @Test
  fun gltfTraversalIsRejectedBeforeProvider() = runBlocking {
    val json = checkNotNull(resource("tessuto.gltf")).use { it.readBytes().decodeToString() }
      .replace("tessuto.bin", "%2e%2e/tessuto.bin")
    var called = false
    val failure = runCatching {
      openFluidSceneModel(source("tessuto.gltf", json.toByteArray()) { called = true; resource(it) })
    }.exceptionOrNull() as FluidViewerException
    assertEquals(FluidViewerFailure.Unsupported, failure.failure)
    assertFalse(called)
  }

  @Test
  fun gltfCountsRepeatedInstancesInItsLimit() = runBlocking {
    val input = """
      {"asset":{"version":"2.0"},"scenes":[{"nodes":[0,1]}],"nodes":[{"mesh":0},{"mesh":0}],
      "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
      "accessors":[{"count":1000000},{"count":2000000}]}
    """.trimIndent()
    val failure = runCatching { openFluidSceneModel(source("large.gltf", input.toByteArray())) }
      .exceptionOrNull() as FluidViewerException
    assertEquals(FluidViewerFailure.TooLarge, failure.failure)
  }

  private fun stats(name: String) = runBlocking { openFluidSceneModel(source(name)).stats }

  private fun source(name: String, input: ByteArray? = null, sidecar: (String) -> InputStream? = ::resource) =
    object : FluidSceneSource {
      override val name = name
      override suspend fun open(): InputStream = input?.inputStream() ?: checkNotNull(resource(name))
      override suspend fun openRelative(path: String): InputStream? = sidecar(path)
      override suspend fun seekable(): ParcelFileDescriptor = error("Non serve un descrittore")
    }

  private fun resource(name: String): InputStream? = javaClass.getResourceAsStream("/corpus/$name")
}
